package com.tss.grc.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-05 — the role-guard census, and the ratchet that drains it.
 *
 * <h3>What a role guard costs</h3>
 * <p>{@code @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO')")} authorises by <em>who someone
 * is</em>. {@code hasPermission('CYBER','WRITE')} authorises by <em>what they may do</em>. The two
 * look interchangeable and are not, in three ways this codebase has already paid for:
 *
 * <ol>
 *   <li><b>A role name is a string nobody validates.</b> A guard naming a role that does not exist
 *       in {@code iam.roles} is not a compile error, not a startup warning and not a 500 — it is a
 *       leg of an OR that can never be true. F2-27 found two ({@code MODEL_OWNER},
 *       {@code BOARD_MEMBER}); this test's companion script re-derives the full list from the live
 *       role table, because the set of typos is not knowable from the code alone.</li>
 *   <li><b>A role list cannot be re-granted.</b> Giving a new customer persona access to a
 *       role-guarded endpoint means editing Java and shipping a jar. The same change on a
 *       permission-guarded endpoint is a row in {@code iam.role_permissions} — which is the entire
 *       point of having a 94-permission catalogue and a role editor.</li>
 *   <li><b>Role guards are invisible to every tightness measurement.</b>
 *       {@link PermissionCatalogueUsageTest} emits the {@code MODULE:ACTION} pairs the code guards
 *       on, and {@code Appendix/GRANT_TIGHTNESS.md} joins them against the grant table. An endpoint
 *       guarded only by role names appears in neither side of that join: not as a guarded
 *       permission, not as an ungranted one. It is authorised, and unmeasured.</li>
 * </ol>
 *
 * <h3>Why reflection, and why a dump file</h3>
 * <p>Same reason as its sibling, arrived at the hard way three times: a source regex has
 * under-counted a security surface in this repository repeatedly (F2-27 missed 8 constant-valued
 * guards; F2-30 missed 54 field pairs; F2-13a's own {@code hasAnyRole}-leg count was 3 by grep and
 * 73 by reflection, because a guard that spans two source lines is invisible to a line-oriented
 * match). The annotation value is assembled by the compiler, so reflection sees the whole
 * expression however it was formatted.
 *
 * <p>The role-existence half needs {@code iam.roles} and the suite has no database, so this test
 * writes {@code target/role-guard-sites.txt} and
 * {@code Appendix/scripts/role_guard_census.py} joins it against the live role table — the same
 * split as {@code permission-usage.txt} → {@code grant_tightness.py}.
 *
 * <h3>What the assertions are for</h3>
 * <p>The ratchet: pure role guards under {@code com.tss.grc.module.**} may only decrease. F2-05
 * converts them module by module, each conversion granting the permission to exactly the roles the
 * guard previously named, so the population that can call the endpoint is unchanged on day one.
 * The target is 0; the assertion is "no worse than the last measurement", so the backlog cannot
 * grow while it is being worked off.
 */
class RoleGuardConversionRatchetTest {

    /** {@code hasAnyRole('A','B')} / {@code hasRole('A')} — any whitespace, any arity. */
    private static final Pattern ROLE_CALL =
        Pattern.compile("has(?:AnyRole|Role)\\(([^)]*)\\)");

    /**
     * {@code hasAuthority('X')} / {@code hasAnyAuthority(...)} — two different things, depending on
     * the string.
     *
     * <p>{@code hasAuthority('ROLE_X')} is {@code hasRole('X')} written longhand, and is counted
     * with the role calls: splitting them would let the ratchet be satisfied by respelling a guard
     * rather than converting it.
     *
     * <p>{@code hasAuthority('KB_WRITE')} is something else — a <em>permission</em> spelled as the
     * raw granted authority, which is what {@code ModulePermissionEvaluator} emits. It reads like
     * {@code hasPermission('KB','WRITE')} and is not the same expression: the evaluator is never
     * called, so everything it does is skipped, starting with the hardcoded PLATFORM_ADMIN bypass
     * that the other 1,400 guards rely on. Classified as {@code RAW_AUTHORITY} and reported
     * separately, because the conversion is a different one-line change and its failure mode is the
     * opposite direction — too tight rather than too loose.
     */
    private static final Pattern AUTHORITY_CALL =
        Pattern.compile("has(?:AnyAuthority|Authority)\\(([^)]*)\\)");

    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");
    private static final Pattern HAS_PERMISSION = Pattern.compile("hasPermission\\(");

    /** {@code PACKAGE CLASS#member KIND ROLE|ROLE...} per line, for the census script. */
    private static final Path OUT = Path.of("target", "role-guard-sites.txt");

    /**
     * Pure role guards (a role leg and <em>no</em> {@code hasPermission} leg) under
     * {@code com.tss.grc.module.**}.
     *
     * <p>Sites, not files: one number per guarded endpoint. It may only go down.
     *
     * <p><b>647</b> measured 2026-07-29 when F2-05 opened, across 38 module packages, reciting 74
     * distinct role names of which <b>40 do not exist</b> in {@code iam.roles}. 361 of the 647 lose
     * at least one leg to a name that is not there, and 40 are left admitting PLATFORM_ADMIN only —
     * not by intent but by attrition. (A further 31 name PLATFORM_ADMIN and nothing else, which is
     * an honest choice and not part of that 40.)
     *
     * <p><b>621</b> after the first module: {@code audit_lc/ia_plan}'s 26 endpoints moved to
     * {@code AUDIT:READ/WRITE/DELETE}, the permission its two sibling controllers already used.
     *
     * <p><b>582</b> after the second: {@code tprm}'s 39 endpoints, every one of which named
     * {@code TPRM_LEAD} — a role that does not exist — moved to {@code TPRM:READ/WRITE/DELETE} plus
     * a newly catalogued {@code TPRM:APPROVE} granted to exactly the two real roles its approval
     * guard named (V327).
     *
     * <p><b>572</b> after the third: {@code fraud}'s 10 endpoints moved to {@code FRAUD:READ/WRITE},
     * a permission that <em>did not exist</em> until V328 catalogued it — the first slice with no
     * target to inherit. Its two guards were constants naming {@code FRAUD_OFFICER} and
     * {@code FRAUD_INVESTIGATOR}, and on the write constant those ghosts were the only non-admin
     * legs, so the entire case lifecycle was PLATFORM_ADMIN-only. The same two names also sat in
     * {@code AttachmentAccessPolicy}'s {@code FRAUD_CASE} entry, faithfully mirrored there from this
     * guard — a reminder that converting a controller is not finished until every route that serves
     * the record agrees.
     *
     * <p><b>568</b> after the fourth: the four inbound-integration endpoints in {@code integrations}
     * whose guards named {@code SOC_AUTOMATION} and {@code INTEGRATION_SERVICE} — neither in
     * {@code iam.roles} — moved to {@code CYBER:WRITE}, which the eight seeded {@code *_INGEST}
     * machine roles already hold, so <b>no migration</b>. Only 4 of that package's 24 sites moved on
     * purpose: the other 20 name roles that all exist, and no catalogued permission matches
     * {@code {PLATFORM_ADMIN, CISO\}} — converting them would either widen integration config to
     * VIEWER/REGULATOR_VIEWER via {@code CYBER:READ} or drop CISO via {@code PLATFORM:WRITE}. Naming
     * the owner of integration administration is a product decision, not a conversion.
     *
     * <p><b>537</b> after {@code integrations} came back: <b>15 of those 20 moved</b> once the product
     * decision was taken (ADR-017 D-8, V343 catalogues {@code INTEGRATIONS:READ}/{@code WRITE}). The
     * paragraph above was right that no <em>existing</em> permission fit and wrong to leave it there —
     * <b>"no catalogued permission matches this population" is an argument for cataloguing one, and
     * the module sat administrator-only for as long as that sentence stood.</b> The measured effect is
     * not a narrowing: the 20 guards recited nine role names of which <b>six do not exist</b>, so 15
     * endpoints were reachable by {@code PLATFORM_ADMIN} and {@code CISO} alone — <em>by attrition,
     * not by design</em> — and {@code splunk/test-push} by PLATFORM_ADMIN alone. This is therefore a
     * <b>re-grant</b>, probed per endpoint as one, with a single access loss recorded in the guard that
     * takes it ({@code TenableController#status} loses {@code RISK_MANAGER}).
     *
     * <p>The 5 sites deliberately left as role guards are the module's <em>outbound action</em>
     * endpoints — {@code contain}, {@code lift}, {@code recast}, {@code acceptRisk},
     * {@code removeRecast} — and they had their <b>ghost legs stripped without being converted</b>,
     * which is provably access-preserving because a name absent from {@code iam.roles} can never
     * satisfy {@code hasAnyRole}. Converting them was measured and rejected in both directions:
     * {@code INTEGRATIONS:WRITE} is {@code {ORG_ADMIN, CISO\}} and would <em>revoke</em> severity
     * recast and vulnerability risk-acceptance from {@code RISK_MANAGER}, the role whose function that
     * is, while {@code CYBER:WRITE} includes eight machine feeds. <b>A ratchet that only ever counts
     * down invites conversion for its own sake; five sites here are the honest floor, and each says so
     * in its own javadoc</b> — {@link com.tss.grc.security.IntegrationsAuthorizationTest} pins them,
     * along with the invariant that the only role names this package may recite are three that exist.
     *
     * <p><b>564</b> after the fifth: {@code board_pack}'s 4 read/write sites moved to
     * {@code ADMIN:READ}/{@code ADMIN:WRITE} — already granted, so <b>no migration</b>. This one
     * inverts the pattern. Five of the read guard's six role names were ghosts (CRO, BOARD_MEMBER,
     * AUDIT_COMMITTEE_CHAIR, AUDIT_COMMITTEE_MEMBER, COMPANY_SECRETARY), but the guard ended
     * {@code or hasAuthority('REPORTING_READ')} — so instead of leaving the endpoint admin-only, the
     * dead names made a <em>wide</em> guard look narrow: REPORTING:READ is 20 roles including
     * VIEWER, which the guard's own comment excludes by name. {@code viewer} read all 16 board packs,
     * 200. <b>A ghost role is not always a lockout; sometimes it is camouflage.</b> The 5th site,
     * {@code delete}, stays {@code hasRole('PLATFORM_ADMIN')} on purpose — the ADMIN module
     * catalogues only APPROVE/EXPORT/READ/WRITE, so there is no ADMIN:DELETE to convert to.
     *
     * <p><b>561</b> after {@code knowledge}: {@code KbArticleController}'s three write guards were
     * {@code hasRole('PLATFORM_ADMIN') or hasAuthority('KB_WRITE')}, and {@code KB} was absent from
     * {@code iam.permissions} entirely — a ghost <em>permission</em> rather than a ghost role, failing
     * identically and just as silently, so authoring every knowledge-base article was the platform
     * administrator's job alone. Moved to {@code hasPermission('KB','WRITE')} + V329. ⚠ <b>The two
     * READ guards were deliberately left as {@code isAuthenticated()}</b> and are pinned that way: the
     * corpus is staff guidance including the whistleblowing and Nazaha conflict-of-interest
     * procedures, and a grant list can only ever wrongly exclude an employee from finding out how to
     * report. Not every role guard should become a permission — this is the counter-example.
     *
     * <p>⚠ <b>564 and 565 are both real, and neither is the merged number.</b> {@code board_pack} and
     * {@code knowledge} were measured on branches that each forked from 568, so each reported only its
     * own subtraction — 568−4 and 568−3. Merged together the count is <b>561</b>, which was
     * <em>measured</em> after the merge rather than arithmetic'd: a per-branch baseline is only ever
     * valid against that branch's fork point, so a ratchet like this must be re-derived whenever two
     * conversions land together, never resolved by taking one side of the conflict.
     *
     * <p><b>552</b> after {@code bcm/comms}: the nine crisis-comms v2 endpoints, whose guards named
     * {@code CRISIS_COMMANDER} and {@code COMMS_LEAD} — neither of which exists in {@code iam.roles}.
     * Moved to {@code BCM:READ} (4 sites), {@code BCM:WRITE} (2), {@code BCM:APPROVE} (2 — dispatch
     * and the template approval transition) and {@code BCM:DELETE} (archive), all already catalogued
     * and granted, so no migration was needed for the guards. ⚠ <b>This one was found from the
     * OPPOSITE direction to every conversion before it.</b> The others came from reading guards; this
     * came from {@code schema_drift.py} noticing that the two tables these endpoints query exist in
     * neither database — so the ghost roles were the second defect on a surface that could not have
     * worked anyway (F2-64, V336). <b>A ghost role and a missing table hide each other: the 403 you
     * would have got masks the 503 you would have got.</b>
     *
     * <p><b>511</b> after {@code sharia} (F2-145(e) slice 1): 21 sites to {@code SHARIA}
     * READ/WRITE/APPROVE. That slice's value was not the count — it was that V362's catalogue and V367's
     * revocation had both been <b>inert</b> until a guard consulted them. Catalogue, revoke, convert:
     * three artefacts in order, and only the third is observable.
     *
     * <p><b>493</b> after {@code openbanking} (slice 2): 18 sites in {@code ObController} to
     * {@code OPENBANKING} READ/WRITE/APPROVE, and the first conversion in this programme that was
     * <b>not population-preserving until a migration made it so</b>. The catalogue granted
     * {@code OPENBANKING:APPROVE} to {@code OPEN_BANKING_OFFICER}, a role no APPROVE guard there has ever
     * named, so converting as-is would have let the officer who grants customer consents to a TPP also
     * admit that TPP ({@code createTpp}, {@code transitionTpp}, {@code notifySama}). V368 revokes it.
     * ⚠ The provenance generalises and is the finding worth keeping: V362 states its grants are
     * <em>derived</em> — action from the HTTP verb, "or APPROVE when the method is
     * approve/reject/transition/decide/sign-shaped" — and that classifier reads the <em>method</em> while
     * a guard's tier is a <em>design decision</em> it never read. Every controller with a
     * transition-shaped method on its WRITE tier therefore had that whole tier promoted to APPROVE.
     * Measured across all six V362 modules: {@code SHARIA_AUDITOR}'s APPROVE (caught, V367),
     * {@code OPEN_BANKING_OFFICER}'s APPROVE (V368), {@code CFO}'s {@code CPTY:WRITE} and
     * {@code KYC_OFFICER}/{@code ONBOARDING_AGENT}'s {@code AML:WRITE} (both filed — each needs a
     * catalogue decision, not a revoke). Four of six. <b>"Derived from the guards" is not "faithful to
     * the guards", and the word <em>derived</em> is what made it feel already-checked.</b> This slice also
     * retired {@code OPEN_BANKING_OFFICER} from the guard vocabulary entirely (50 distinct names → 49):
     * it was recited in this controller and nowhere else, so its authority now flows only through the
     * grant — which is the end state, not a loss.
     *
     * <p><b>484</b> after {@code consumer} (slice 3): 9 sites in {@code ComplaintController}, the module
     * F2-145 called the severe case because {@code PLATFORM_ADMIN} and {@code COMPLIANCE_OFFICER} were
     * the entire population of all nine guards. 🔑 <b>It was a FLAT guard</b> — the identical
     * three-name literal on every authenticated endpoint, GET and POST alike — so the module had no tier
     * distinction to preserve, and the catalogue grants {@code CONSUMER} READ, WRITE <em>and</em> APPROVE
     * to exactly the guard's two surviving names. Any tiering is therefore population-preserving, which
     * made the tier assignment a genuine design choice rather than a transcription: reads READ, the four
     * operational writes WRITE, and {@code escalateToSama} alone APPROVE — the analogue of
     * {@code ObController#notifySama}, since telling the regulator is an outbound act with legal
     * consequence. <b>Tiering a flat guard changes no request today and is still worth doing</b>: it gives
     * {@code CONSUMER:APPROVE} — catalogued by V362 and consulted by nothing anywhere — its first reader,
     * and makes the split grantable later without a code change. Retired
     * {@code CONSUMER_PROTECTION_OFFICER} from the guard vocabulary entirely (49 names → 48), the second
     * such retirement in two slices. ⚠ <b>This is also the first conversion with a FRONT-END gate to
     * match</b>: {@code ComplaintDetailPage} computed {@code canAct} from the same three role names, and
     * leaving it would have left a client gate reciting names the server no longer consults — invisible
     * precisely because the populations are identical.
     *
     * <p><b>470</b> after {@code cpty} (slice 4): 14 sites, and the first conversion blocked by a
     * <b>missing action</b> rather than a wrong grant. {@code CPTY} was catalogued with READ and WRITE
     * only, because V362's classifier assigns APPROVE by method shape and this controller's two approval
     * endpoints are {@code assignRating} and {@code upsertLimit} — named like neither an approval nor a
     * transition. So both were read as writes and the APPROVE tier's population was folded into WRITE,
     * which is how {@code CFO} came to hold {@code CPTY:WRITE} while appearing on no WRITE guard.
     * 🔑 <b>A missing catalogue entry is not a gap that makes a conversion impossible; it is a gap that
     * makes the WRONG conversion easy.</b> With no {@code CPTY:APPROVE}, the obvious move is to point
     * {@code APPROVE_GUARD} at the {@code CPTY:WRITE} that exists — which would take the approval
     * population from {@code {PLATFORM_ADMIN, CFO}} to include {@code CREDIT_OFFICER} and
     * {@code RISK_MANAGER}, letting a credit officer assign the internal credit rating on their own
     * counterparty and set their own large-exposure limit. Under SAMA's large-exposure rules that
     * separation is the control. <b>V369</b> catalogues the action, grants it to {@code CFO}, and revokes
     * {@code CFO}'s mis-derived WRITE. ⚠ This is the <em>mirror image</em> of V368's defect: the same
     * classifier promoted {@code openbanking}'s WRITE tier to APPROVE and demoted {@code cpty}'s APPROVE
     * tier to WRITE — one cause, both directions, and which one you get depends on nothing but whether a
     * method happens to be named like a transition. ⚠ {@code CFO} is held by no active user, so APPROVE is
     * reachable only through the administrator's bypass; that was already true of the guard, so the
     * conversion <b>reveals</b> that narrowness rather than causing it (F2-52's attrition shape, and a
     * provisioning act to fix).
     *
     * <p><b>456</b> after {@code aml} (slice 5): 14 sites over <b>six</b> distinct guard name-sets, the most
     * of any module in the queue, onto <b>seven</b> actions. This one inverts the previous three. V367/V368
     * found a grant that was <em>wrong</em>; V369 found an action that was <em>missing</em>; here three of
     * the six sets map onto V362's {@code AML_READ}/{@code AML_WRITE}/{@code AML_APPROVE} <b>exactly</b> —
     * the first time the classifier's output has survived measurement unaltered. What was absent is a
     * <b>gradient</b>, and it sits in the READ tier, which is not where the plan queued this slice to look.
     * Sets A/B/C differ only by which front-line roles they admit: lists and checks include
     * {@code ONBOARDING_AGENT} and {@code KYC_OFFICER}; a list's <em>entries</em> drop the former; the
     * <em>hit queue</em> and dashboard drop both. <b>V370</b> catalogues {@code READ_ENTRIES},
     * {@code READ_HITS}, {@code MANAGE_LISTS} and {@code REFRESH_LISTS} — purely additive, nothing revoked.
     *
     * <p>🔑 ⚠ <b>The measurement discipline this programme runs on would have destroyed this control, and
     * been right about the numbers.</b> The standing rule is "measure the per-action population diff before
     * converting". Measured in dev, {@code A\B}, {@code B\C} and {@code A\C} are all <b>empty</b>: every read
     * tier resolves to the same three users, because {@code AML_OFFICER}, {@code KYC_OFFICER} and
     * {@code ONBOARDING_AGENT} — the only roles that differentiate them — each have <b>0 active users</b>.
     * Collapsing 14 guards onto 3 actions would have measured population-preserving, probed 200/403 exactly
     * as predicted, and handed the sanctions <em>hit queue</em> — which customers matched — to every
     * onboarding agent a tenant ever hires. Disclosing a flagged customer to front-line staff is the FATF
     * R.21 / SAMA tipping-off offence. <b>A zero population diff measured in a tenant that never provisioned
     * the distinguishing roles is not evidence that there is no distinction; it is evidence that the
     * instrument cannot see one.</b> When the population is unstaffed, the guard's own shape is the only
     * surviving record of intent, and it has to outrank the count.
     *
     * <p>➕ Two by-products worth knowing when picking slice order. Names recited fell <b>47 → 44</b>:
     * {@code AML_OFFICER}, {@code KYC_OFFICER} and {@code ONBOARDING_AGENT} now occur <b>0</b> times in the
     * whole of {@code src/main} — this controller was their only site — and {@code AUDIT_COMMITTEE_MEMBER}
     * fell from 16 occurrences to 3. ⚠ <b>Those three roles are not dead, and a future reader grepping the
     * source will conclude they are.</b> They hold the AML grants (7, 3 and 2 actions respectively), so
     * deleting them as unused would silently revoke the tiers this slice just built: the conversion moved
     * their authority out of a string comparison and into {@code iam.role_permissions}, which is the whole
     * point, and it is also what makes them invisible to {@code grep}.
     *
     * <p><b>430</b> after {@code ethics} (slice 6), the last module F2-145(e) queued: 26 sites over four
     * distinct name-sets, onto six actions. Two things here that no earlier slice showed.
     *
     * <p>➕ <b>The catalogue was inert, so its grants were not the population to preserve.</b>
     * {@code ETHICS:READ/WRITE/APPROVE} carried grants for months while {@code hasPermission('ETHICS'}
     * occurred <b>zero</b> times in {@code src/main} — catalogued <em>for</em> these controllers by their own
     * descriptions and never wired to them. For every earlier slice the grant table was the fact to keep;
     * here it conferred nothing, so the <b>guard</b> was the only ground truth and <b>V371</b> could both add
     * and revoke ({@code INTERNAL_AUDITOR}'s {@code ETHICS:READ}) without changing one person's real access.
     * ⚠ Worth generalising: <em>a catalogue entry's grants are evidence of intent only once something reads
     * them.</em>
     *
     * <p>🔑 ⚠ <b>And the slice-5 trap recurred one module later, this time hiding an independence control
     * rather than a disclosure one.</b> Measured as <em>users</em>, three of the four name-sets are the same
     * two people ({@code admin}, {@code compliance}); as <em>roles</em> they are three different authorities,
     * separated entirely by {@code ETHICS_OFFICER} and {@code BOARD_SECRETARY} — <b>0 active users</b> each.
     * Mapping the hotline's triage tier and the retaliation tier onto {@code ETHICS:WRITE}, which the
     * catalogue's description invited, would have measured population-preserving and let <em>the ethics
     * officer who ran a whistleblower investigation rule on the retaliation complaint about it</em> — the
     * identical self-review defect V367 removed from {@code sharia} in slice (b), re-introduced in another
     * module inside the same programme. Separately it would have put a {@code BOARD_SECRETARY} inside hotline
     * case files that may name or concern the board. V371 catalogues {@code READ_CASES}, {@code TRIAGE} and
     * {@code PROTECT} and asserts both absences by name, because <b>an absence is what a population diff is
     * worst at noticing</b>.
     *
     * <p>➕ Names recited fell <b>44 → 43</b> ({@code ETHICS_OFFICER} was named nowhere else) and two
     * non-guard surfaces had to move with the guards: {@code CoiService#declarantFor}, which recited the same
     * three role names to decide who may file a declaration on an employee's behalf, and
     * {@code AttachmentAccessPolicy}'s {@code COI_CAMPAIGN} / {@code WHISTLEBLOWER_CASE} entries. The second
     * needed a new expression — the registry could say {@code MODULE_READ} or a role name, and ethics is the
     * first module whose read tier is a <em>named</em> action, so {@code perm("ETHICS")} would have been wrong
     * in both directions at once.
     *
     * <p>Raising this number means adding an endpoint that authorises by identity in a platform
     * whose authorisation model is permissions, and whose role names have a 54% miss rate. Convert
     * instead. Where a role genuinely is the right unit — {@code PLATFORM_ADMIN}-only platform
     * administration is the honest case — say so in the guard's javadoc, because the next reader
     * cannot tell that apart from an unconverted one.
     */
    private static final int BASELINE_PURE_ROLE_SITES = 430;

    /**
     * Anti-vacuity floor. A scan that resolves no controllers passes every "may only decrease"
     * assertion trivially — the failure mode that made {@link PermissionCatalogueUsageTest} carry a
     * floor too. 300+ {@code @RestController} classes exist; 200 is a wide margin.
     */
    private static final int MIN_CONTROLLERS_SCANNED = 200;

    @Test
    void roleGuardsUnderModuleMayOnlyDecrease() throws Exception {
        Census census = scan();

        Files.createDirectories(OUT.getParent());
        Files.write(OUT, census.lines);

        System.out.println("[F2-05] controllers scanned          = " + census.controllers);
        System.out.println("[F2-05] guard sites with a role leg  = " + census.roleSites);
        System.out.println("[F2-05]   of those, PURE role guards = " + census.pureSites);
        System.out.println("[F2-05]   of those, composed w/ perm = " + (census.roleSites - census.pureSites));
        System.out.println("[F2-05] pure role sites under module/ = " + census.pureUnderModule);
        System.out.println("[F2-05] module packages affected     = " + census.modulePackages.size()
            + " " + census.modulePackages);
        System.out.println("[F2-05] distinct role names named    = " + census.roleNames.size());
        System.out.println("[F2-05] " + census.roleNames);
        System.out.println("[F2-05] raw-authority sites (perm spelled longhand, evaluator skipped) = "
            + census.rawAuthoritySites + " " + census.rawAuthorities);
        System.out.println("[F2-05] wrote " + OUT.toAbsolutePath());

        assertThat(census.controllers)
            .as("only %d @RestController classes resolved — the scan is broken, and every "
                + "'may only decrease' assertion below would pass vacuously", census.controllers)
            .isGreaterThanOrEqualTo(MIN_CONTROLLERS_SCANNED);

        assertThat(census.pureUnderModule)
            .as("F2-05 ratchet: %d endpoints under com.tss.grc.module.** authorise by role name "
                + "with no permission leg; %d was the measured baseline. A role list cannot be "
                + "re-granted without a jar, names no catalogued permission, and is invisible to "
                + "the grant-tightness join. Convert to hasPermission('MODULE','ACTION') and grant "
                + "it to exactly the roles the guard named.",
                census.pureUnderModule, BASELINE_PURE_ROLE_SITES)
            .isLessThanOrEqualTo(BASELINE_PURE_ROLE_SITES);
    }

    /**
     * A role name the evaluator can never match is a permanently false OR leg. {@code hasAnyRole}
     * compares against the authority {@code ROLE_ + name}, and every seeded role in
     * {@code iam.roles} is UPPER_SNAKE, so anything else is dead on arrival — the identical
     * argument {@link PermissionCatalogueUsageTest} makes for malformed permission names, which is
     * enforced there and was unenforced here.
     *
     * <p><b>⚠ F2-52 (noted 2026-08-06): this checks SHAPE, and the half that matters is EXISTENCE.</b>
     * A name can be perfectly UPPER_SNAKE and still match no row in {@code iam.roles}, in which case
     * the OR leg is exactly as dead as a malformed one — and the measured miss rate against the
     * catalogue is around <b>50% of the 74 names recited</b> across 537 pure role sites. That half is
     * delegated, in the sentence below, to {@code Appendix/scripts/role_guard_census.py}.
     *
     * <p><b>A script is not a gate.</b> It answers the question only when someone remembers to run it,
     * which is the same structural weakness this programme has recorded repeatedly: F2-77's chain
     * proof found defects that had sat for a year because nothing executed the folder; V272's header
     * claimed a complete catalogue and quietly stopped being true for a month. <b>This test runs on
     * every build and deliberately does not ask the question it identifies as the important one.</b>
     *
     * <p>It <i>can</i> be gated without a database, which is the constraint that pushed it to a
     * script: pin the catalogue's role names as a constant sourced from {@code iam.roles}, assert that
     * every recited name is in it, and ratchet the ghost count downward from its current value rather
     * than demanding zero. That keeps the unit suite hermetic — the property {@code CLAUDE.md}
     * requires — while making the 50% miss rate fail the build when it grows. Scoped, not done; doing
     * it needs the catalogue read once, and it is the concrete next step on F2-52.
     */
    @Test
    void noRoleGuardNamesAnUnmatchableRole() throws Exception {
        Census census = scan();
        List<String> malformed = new ArrayList<>();
        for (String name : census.roleNames) {
            if (!name.matches("[A-Z][A-Z0-9_]*")) {
                malformed.add(name);
            }
        }
        System.out.println("[F2-05] unmatchable role names = " + malformed);
        assertThat(malformed)
            .as("hasAnyRole('x') tests for the authority ROLE_x; every role in iam.roles is "
                + "UPPER_SNAKE, so these legs can never be true and the guard is tighter than it "
                + "reads. Whether a well-formed name EXISTS is a database question — "
                + "Appendix/scripts/role_guard_census.py answers that half.")
            .isEmpty();
    }

    /**
     * F2-52 — a role name that matches no row in {@code iam.roles} is a permanently false OR leg.
     *
     * <p>This is the half {@link #noRoleGuardNamesAnUnmatchableRole()} explicitly delegated to a
     * script. A script answers only when someone runs it; this runs on every build. The catalogue is
     * pinned as a constant precisely so the check stays <b>hermetic</b> — {@code CLAUDE.md} requires
     * {@code mvn -o clean package} to need no database, and that constraint is what pushed this
     * question out of the suite in the first place.
     *
     * <p><b>A ratchet, not zero.</b> Ghost names already exist; demanding zero would block unrelated
     * work on a pre-existing sweep. The count may only fall.
     *
     * <p><b>⚠ The pinned catalogue is a copy, and copies drift.</b> {@link #ROLE_CATALOGUE} was read
     * from {@code iam.roles} on 2026-08-06 (58 active rows). If a genuinely new role is added, this
     * test fails until the name is pinned — which is the correct outcome, not friction: a role the
     * guards rely on should be declared somewhere the build can see. It is <b>not</b> the authority on
     * what roles exist; {@code Appendix/scripts/role_guard_census.py} still owns that. This asserts
     * only that no guard names something absent from both.
     */
    @Test
    void noRoleGuardNamesARoleThatDoesNotExist() throws Exception {
        Census census = scan();

        List<String> ghosts = new ArrayList<>();
        for (String name : census.roleNames) {
            if (!ROLE_CATALOGUE.contains(name)) {
                ghosts.add(name);
            }
        }
        java.util.Collections.sort(ghosts);
        System.out.println("[F2-52] role names recited by guards = " + census.roleNames.size()
            + " ; not in the catalogue = " + ghosts.size() + " -> " + ghosts);

        // The instrument must be able to speak: a scan that finds no names would report zero ghosts.
        assertThat(census.roleNames.size())
            .as("the guard scan found %d role names; the codebase recites dozens across 537 pure role "
                + "sites. A collapsed scan reports zero ghosts, which is the reassuring direction.",
                census.roleNames.size())
            .isGreaterThanOrEqualTo(40);

        assertThat(ghosts.size())
            .as("%d role names recited by @PreAuthorize match no row in iam.roles, against a baseline "
                + "of %d. Each is a permanently false OR leg: hasAnyRole('X') tests for the authority "
                + "ROLE_X, so a guard reciting a non-existent role is strictly tighter than it reads, "
                + "and a reader auditing authorisation is told a role can reach an endpoint when "
                + "nothing can. Either correct the name or drop the leg — do not raise the baseline. "
                + "Ghosts: %s", ghosts.size(), BASELINE_GHOST_ROLE_NAMES, ghosts)
            .isLessThanOrEqualTo(BASELINE_GHOST_ROLE_NAMES);
    }

    /**
     * Active role names in {@code iam.roles}, read 2026-08-06 (58 rows).
     *
     * <p>Pinned rather than queried so the unit suite stays database-free. See
     * {@link #noRoleGuardNamesARoleThatDoesNotExist()} for why a copy is acceptable here and what it
     * is deliberately not claiming.
     */
    private static final Set<String> ROLE_CATALOGUE = Set.of(
        "APPSEC_INGEST", "APPSEC_LEAD", "ASSET_OWNER", "AUDITOR",
        "BCM_MANAGER", "BIA_OWNER", "BOARD_SECRETARY", "BRAND_INGEST",
        "BRAND_MONITORING", "CFO", "CHANGE_MANAGER", "CISO",
        "COMPLIANCE_OFFICER", "CONTROL_OWNER", "CRISIS_MANAGER", "CYBER_MANAGER",
        "DATA_OWNER", "DLP_INGEST", "DLP_REVIEWER", "DPO",
        "DR_OWNER", "EMERGENCY_RESPONDER", "EVIDENCE_OWNER", "EXECUTIVE_REVIEWER",
        "EXTERNAL_AUDITOR", "FINANCE_LEAD", "FORENSICS_LEAD", "FRAUD_INVESTIGATOR",
        "GRC_ADMINISTRATOR", "HR_LEAD", "IAM_LEAD", "INTERNAL_AUDITOR",
        "IR_LEAD", "JML_INGEST", "LEGAL_COUNSEL", "LEGAL_REVIEWER",
        "MA_LEAD", "NCA_COMPLIANCE", "ORG_ADMIN", "OT_LEAD",
        "PLATFORM_ADMIN", "PRIVACY_MANAGER", "PROCUREMENT_OWNER", "REGULATOR_VIEWER",
        "RISK_ANALYST", "RISK_MANAGER", "RISK_OWNER", "SBOM_INGEST",
        "SCANNER_INGEST", "SECURITY_ANALYST", "SERVICE_OWNER", "SIEM_INGEST",
        "SUPPLIER_MANAGER", "SUPPLIER_USER", "SUSTAINABILITY_OFFICER", "TI_INGEST",
        "VIEWER", "VULN_MANAGER",
        // ── seated by V362 (F2-145 / ADR-017 D-14), 58 -> 67 ────────────────────────────────────
        // ⚠ This copy MUST be updated in the same commit as the migration that seats a role, and
        // that is the cost of pinning it. A stale catalogue does not fail: it reports nine roles
        // that now exist as ghosts, i.e. it INVENTS defects — the reassuring direction is the other
        // one, so this is the direction worth stating. The nine are what F2-145 measured as domain
        // roles living only in guard text across six Saudi/financial modules.
        "SHARIA_OFFICER", "SHARIA_AUDITOR", "ETHICS_OFFICER", "OPEN_BANKING_OFFICER",
        "AML_OFFICER", "KYC_OFFICER", "ONBOARDING_AGENT", "CONSUMER_PROTECTION_OFFICER",
        "CREDIT_OFFICER");

    /**
     * Ghost names measured 2026-08-06: <b>28</b>. <b>MUST only ever decrease.</b>
     *
     * <p>The full set, so the work is scoped rather than merely counted: {@code AML_OFFICER},
     * {@code AUDIT_COMMITTEE_CHAIR}, {@code AUDIT_COMMITTEE_MEMBER}, {@code AUDIT_DIRECTOR},
     * {@code BOARD_MEMBER}, {@code CIO}, {@code CISO_GOVERNANCE}, {@code COMPANY_SECRETARY},
     * {@code CONSUMER_PROTECTION_OFFICER}, {@code CONTENT_EDITOR}, {@code CREDIT_OFFICER},
     * {@code CRISIS_COMMANDER}, {@code CRO}, {@code ETHICS_OFFICER}, {@code FACILITATOR},
     * {@code HR_DIRECTOR}, {@code INCIDENT_COMMANDER}, {@code KYC_OFFICER}, {@code MODEL_OWNER},
     * {@code ONBOARDING_AGENT}, {@code OPEN_BANKING_OFFICER}, {@code PRIVACY_OFFICER},
     * {@code SHARIA_AUDITOR}, {@code SHARIA_BOARD_CHAIR}, {@code SHARIA_OFFICER}, {@code SOC_LEAD},
     * {@code TPRM_LEAD}, {@code USER}.
     *
     * <p><b>⚠ This count conflates two different defects.</b> {@code COMPLIANCE_READ},
     * {@code CYBER_READ}, {@code RISK_READ}, {@code INCIDENT_READ} and {@code PRIVACY_READ} are not
     * role names anybody mistyped — they are <b>permissions spelled as raw authorities</b>, the
     * {@code RAW_AUTHORITY} case this class's own header describes: {@code hasAuthority('RISK_READ')}
     * reads like {@code hasPermission('RISK','READ')} and is not the same expression, because
     * {@code ModulePermissionEvaluator} is never called and everything it does is skipped —
     * <b>including the hardcoded PLATFORM_ADMIN bypass every other guard relies on</b>. They are
     * absent from {@code iam.roles} for the same reason {@code KB_WRITE} was in F2-53: they were never
     * meant to be roles.
     *
     * <p>"Absent from the catalogue" is still the right test; "ghost role name" is the wrong label for
     * those five. The remedy differs too — a typo is dropped or corrected, whereas these need
     * rewriting as {@code hasPermission(...)}, and per F2-53 that direction fails <b>too tight rather
     * than too loose</b>. Each of the five also has <b>no real non-admin role left beside it</b>, so
     * they are candidates for being reachable by nobody at all. Not verified here, and deliberately
     * not swept with the typos.
     *
     * <p><b>⚠ Correction, same day: these guards work.</b> I first recorded them as sites with no real
     * non-admin role beside them, possibly reachable by nobody. {@code ModulePermissionEvaluator}
     * documents granted authorities as {@code MODULE_ACTION} and matches that exact string, so
     * {@code RISK_READ} <i>is</i> a granted authority and {@code hasAnyAuthority('RISK_READ')} admits
     * every user holding the permission. What is lost is only the {@code ROLE_PLATFORM_ADMIN} bypass
     * the evaluator applies first — <b>too tight for admins, not open to nobody</b>. Worth rewriting as
     * {@code hasPermission(...)} to regain the bypass and read consistently; not broken authorisation.
     * Their presence in this list is a labelling artefact of asking {@code iam.roles} about a string
     * that was never a role.
     *
     * <p><b>Sized on one name, because the shape generalises.</b> {@code TPRM_LEAD} is <b>11
     * occurrences across 5 files</b>, and they are not all guards: {@code MtpdInheritanceController}
     * (3) and {@code ProcessVendorDependencyController} (5) recite it in {@code @PreAuthorize}, while
     * {@code AttachmentAccessPolicy} and {@code IntakeService} carry it in a Java role {@code Set} that
     * no {@code @PreAuthorize} scan sees. This test's own note on the {@code fraud} conversion already
     * records why that matters — <i>"converting a controller is not finished until every route that
     * serves the record agrees"</i> — and it means the ghost count falling is <b>not</b> evidence the
     * name is gone from the authorisation surface.
     *
     * <p>⚠ It also means the sweep cannot be a rename pass. Dropping a dead leg is
     * behaviour-preserving (it never matched), but <i>correcting</i> one widens access, and F2-144
     * showed a case where the dead leg was the only thing preventing an unbound write. <b>Each site
     * needs its intent recovered before its name is touched</b>, and where the intent is unmet the
     * honest move is to drop the leg and say so rather than substitute a role that happens to exist.
     *
     * <p>Several are near-misses for a role that <i>does</i> exist and read as typos rather than
     * policy: {@code PRIVACY_OFFICER} beside the real {@code PRIVACY_MANAGER}, {@code SOC_LEAD} beside
     * {@code SECURITY_ANALYST}/{@code IR_LEAD}, {@code TPRM_LEAD} beside {@code SUPPLIER_MANAGER}.
     * Others are governance identities the platform models as committee <i>seats</i> rather than roles
     * ({@code BOARD_MEMBER}, {@code AUDIT_COMMITTEE_CHAIR}, {@code COMPANY_SECRETARY}) — for those the
     * guard is asking the wrong question, not misspelling the answer, which is F2-26's territory.
     * {@code CONTENT_EDITOR} is already named in F2-53's row as a ghost.
     *
     * <p>⚠ The plan records "40 of the 74 recited names do not exist". This measures <b>28</b> against
     * the 58 active rows. The figures are not comparable without knowing what the earlier one counted
     * — it may have included the malformed names the sibling test now bans, or run against a smaller
     * catalogue. <b>28 is what this instrument measures today; it does not refute the 40 and should not
     * be read as doing so.</b>
     *
     * <p><b>24 → 23 (F2-144b, 2026-08-06): {@code USER} is gone.</b> It occurred exactly once in the
     * whole of {@code src/main/java} — {@code CoiController.SUBMIT_GUARD} — which is now
     * {@code isAuthenticated()}, so the name has left the source entirely rather than moved. ⚠ Note what
     * did <b>not</b> happen: {@code ETHICS_OFFICER}, the other ghost in that same expression, is still
     * counted, because it also sits in {@code MANAGE_GUARD} and dropping it there would silently ratify
     * compliance-officer ownership of ethics oversight — F2-145's reasoning, applied to a second module.
     * A one-line change removing two dead names therefore moves this baseline by one, which is the
     * clearest illustration available of why this number is a <em>name</em> count and not a defect count.
     *
     * <p><b>23 → 14 (F2-145 / ADR-017 D-14, V362): the biggest single fall, and by the opposite
     * mechanism to every previous one.</b> Every earlier drop removed a name from a <em>guard</em>. This
     * one changes nothing in {@code module/**}: {@code V362} seats the nine roles — {@code SHARIA_OFFICER},
     * {@code SHARIA_AUDITOR}, {@code ETHICS_OFFICER}, {@code OPEN_BANKING_OFFICER}, {@code AML_OFFICER},
     * {@code KYC_OFFICER}, {@code ONBOARDING_AGENT}, {@code CONSUMER_PROTECTION_OFFICER},
     * {@code CREDIT_OFFICER} — so the guards that always named them start matching. {@code hasAnyRole}
     * compares names, so this required no annotation edit at all; live-proven 403 → 200 on
     * {@code /api/v1/sharia/scholars} with {@code ShariaController} untouched.
     *
     * <p>⚠ <b>A falling count here is now ambiguous and the javadoc has to say so.</b> Two very different
     * events reduce it: a dead leg is <em>deleted</em> from a guard (access unchanged, the record of intent
     * lost) or the named role is <em>seated</em> (access granted, intent met). This drop is entirely the
     * second kind. Reading the number alone, they are indistinguishable — which is exactly why F2-52's
     * sweep is a per-site review rather than a rename pass.
     *
     * <p>⚠ The remaining {@code MODEL_OWNER}-style names were <b>deliberately not seated</b>: D-14
     * resolves them by binding to data the platform already holds ({@code ml_model.model_owner_user_id},
     * populated on 25 of 25 rows). Seating them would have been <em>looser</em> than the column — the
     * owner of model A could act on model B — so this baseline is not simply "make it fall".
     *
     * <p>{@code SHARIA_BOARD_CHAIR} left this population by a third route, and it is the one F2-145(e)
     * generalises: <b>converting the guard to a permission deletes every name it recited</b>, ghosts
     * included. It was 4 sites in one controller, all {@code SHARIA} APPROVE; {@code ShariaController}
     * now asks {@code hasPermission('SHARIA','APPROVE')} and the name appears nowhere in {@code src/main}.
     * Seating it was never needed — {@link com.tss.grc.module.sharia.ShariaControllerGuardTest} pins the
     * result. That is cheaper than either of the other two routes and is why 102 sites across the six
     * Saudi/financial modules are queued behind it rather than being individually adjudicated.
     */
    private static final int BASELINE_GHOST_ROLE_NAMES = 8;

    // ── extraction ────────────────────────────────────────────────────────────────────────────

    private record Census(int controllers, int roleSites, int pureSites, int pureUnderModule,
                          int rawAuthoritySites, Set<String> modulePackages, Set<String> roleNames,
                          Set<String> rawAuthorities, List<String> lines) { }

    // ── the committed census must agree with what the build measures ──────────

    /**
     * ⚠ <b>Why this gate exists.</b> {@code Appendix/ROLE_GUARD_CENSUS.md} opens with <i>"Do not
     * hand-edit: the next run overwrites it"</i> — but nothing <em>runs</em> it. Measured 2026-08-06,
     * the committed copy still said <b>32 ghosts, 66 names recited, 561 pure sites</b> while this
     * test enforced <b>13 / 56 / 532</b>: nineteen ghosts of drift, accumulated across several slices
     * that each fixed guards and left the document describing the platform before them. A generated
     * artefact nobody regenerates is a stale assertion with a "generated" byline — which reads as
     * <em>more</em> authoritative than prose, not less. Third register in this programme to drift
     * while the build was right.
     *
     * <p>Only the figures this test computes <b>hermetically</b> are gated. The census also joins
     * {@code iam.roles} and {@code iam.role_permissions}, which a database-free suite cannot check,
     * so the script remains the authority for the per-role tables.
     */
    private static final Pattern CENSUS_ROLE_SITES = Pattern.compile(
        "(?m)^\\|\\s*Guard sites with a role leg\\s*\\|\\s*\\**(\\d+)\\**\\s*\\|");
    private static final Pattern CENSUS_PURE = Pattern.compile(
        "(?m)^\\|\\s*…\\s*\\*\\*pure\\*\\*[^|]*\\|\\s*\\**(\\d+)\\**\\s*\\|");
    private static final Pattern CENSUS_NAMES = Pattern.compile(
        "(?m)^\\|\\s*Distinct role names recited by any role guard\\s*\\|\\s*\\**(\\d+)\\**\\s*\\|");
    private static final Pattern CENSUS_GHOSTS = Pattern.compile(
        "(?m)^\\|\\s*…\\s*\\*\\*that do not exist[^|]*\\|\\s*\\**(\\d+)\\**\\s*\\|");

    @Test
    void theCommittedCensusStatesWhatTheBuildMeasures() throws Exception {
        Path census = Paths.get("..", "TSS-GRC-Platform-Bible", "Appendix",
                                "ROLE_GUARD_CENSUS.md").toAbsolutePath().normalize();
        assertThat(Files.exists(census)).as(
            "The committed role-guard census is missing at %s. Regenerate it with "
            + "`PYTHONIOENCODING=utf-8 py TSS-GRC-Platform-Bible/Appendix/scripts/"
            + "role_guard_census.py`.", census)
            .isTrue();
        String md = Files.readString(census);

        Census scan = scan();
        int ghosts = (int) scan.roleNames.stream().filter(n -> !ROLE_CATALOGUE.contains(n)).count();

        Map<String, int[]> claims = new LinkedHashMap<>();
        claims.put("guard sites with a role leg",
            new int[]{censusFigure(md, CENSUS_ROLE_SITES), scan.roleSites});
        claims.put("pure role sites",
            new int[]{censusFigure(md, CENSUS_PURE), scan.pureSites});
        claims.put("distinct role names recited",
            new int[]{censusFigure(md, CENSUS_NAMES), scan.roleNames.size()});
        claims.put("names absent from iam.roles",
            new int[]{censusFigure(md, CENSUS_GHOSTS), ghosts});

        List<String> drift = new ArrayList<>();
        for (var e : claims.entrySet()) {
            if (e.getValue()[0] != e.getValue()[1]) {
                drift.add("%s: census says %d, the build measures %d"
                    .formatted(e.getKey(), e.getValue()[0], e.getValue()[1]));
            }
        }

        assertThat(drift).as("""
            The committed role-guard census disagrees with this build. It is a GENERATED document \
            and the generator has not been run since the guards changed, so it publishes figures for \
            a platform that no longer exists.

            Fix by regenerating, never by editing the numbers:
              PYTHONIOENCODING=utf-8 py TSS-GRC-Platform-Bible/Appendix/scripts/role_guard_census.py
            (it needs the live database and target/role-guard-sites.txt, which this test has just \
            rewritten)

            %s""".formatted(String.join("\n            ", drift)))
            .isEmpty();
    }

    /** One figure from the census summary table; -1 when the row is missing, never 0. */
    private static int censusFigure(String md, Pattern p) {
        Matcher m = p.matcher(md);
        // -1 rather than 0 so a renamed row fails loudly instead of coincidentally matching a
        // genuine zero — the day the ghost count really reaches 0 is the day that would matter.
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static Census scan() {
        int controllers = 0, roleSites = 0, pureSites = 0, pureUnderModule = 0, rawAuthoritySites = 0;
        Set<String> modulePackages = new TreeSet<>();
        Set<String> roleNames = new TreeSet<>();
        Set<String> rawAuthorities = new TreeSet<>();
        List<String> lines = new ArrayList<>();

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            controllers++;
            List<String[]> found = new ArrayList<>();
            classify(c.getAnnotation(PreAuthorize.class), "<class>", found);
            for (Method m : c.getDeclaredMethods()) {
                classify(m.getAnnotation(PreAuthorize.class), m.getName(), found);
            }
            if (found.isEmpty()) {
                continue;
            }
            String pkg = c.getPackageName();
            // com.tss.grc.module.cyber.controller -> module.cyber ; anything else stays as-is
            String bucket = pkg.startsWith("com.tss.grc.module.")
                ? "module." + pkg.substring("com.tss.grc.module.".length()).split("\\.")[0]
                : pkg.replace("com.tss.grc.", "");
            for (String[] f : found) {
                String member = f[0], kind = f[1], named = f[2];
                if ("RAW_AUTHORITY".equals(kind)) {
                    rawAuthoritySites++;
                    for (String a : named.split("\\|")) {
                        if (!a.isBlank()) {
                            rawAuthorities.add(a);
                        }
                    }
                } else {
                    roleSites++;
                    if ("PURE".equals(kind)) {
                        pureSites++;
                        if (pkg.startsWith("com.tss.grc.module.")) {
                            pureUnderModule++;
                            modulePackages.add(bucket);
                        }
                    }
                    for (String r : named.split("\\|")) {
                        if (!r.isBlank()) {
                            roleNames.add(r);
                        }
                    }
                }
                lines.add(bucket + " " + c.getSimpleName() + "#" + member + " " + kind + " " + named);
            }
        }
        lines.sort(String::compareTo);
        return new Census(controllers, roleSites, pureSites, pureUnderModule, rawAuthoritySites,
                          modulePackages, roleNames, rawAuthorities, lines);
    }

    /**
     * Records {@code {member, KIND, NAME|NAME...}} for any guard that authorises by identity rather
     * than by {@code hasPermission}. Kinds: {@code PURE} (role legs only), {@code COMPOSED} (role
     * leg ANDed onto a permission — already tight, listed for completeness) and
     * {@code RAW_AUTHORITY} (a permission spelled as its granted-authority string, which bypasses
     * the evaluator). A guard may produce two entries if it mixes forms; each is counted once in
     * its own bucket, which is why the census reports them separately rather than as one total.
     */
    private static void classify(PreAuthorize pa, String member, List<String[]> out) {
        if (pa == null) {
            return;
        }
        String expr = pa.value();
        boolean composed = HAS_PERMISSION.matcher(expr).find();

        Set<String> roles = new LinkedHashSet<>();
        Set<String> rawAuthorities = new LinkedHashSet<>();
        collectNames(ROLE_CALL.matcher(expr), roles);
        // hasAuthority splits by prefix: ROLE_* is a role said longhand, anything else is a
        // permission said longhand. Same call, two different findings.
        Set<String> authorities = new LinkedHashSet<>();
        collectNames(AUTHORITY_CALL.matcher(expr), authorities);
        for (String a : authorities) {
            if (a.startsWith("ROLE_")) {
                roles.add(a.substring("ROLE_".length()));
            } else {
                rawAuthorities.add(a);
            }
        }

        if (!roles.isEmpty()) {
            out.add(new String[] { member, composed ? "COMPOSED" : "PURE", String.join("|", roles) });
        }
        if (!rawAuthorities.isEmpty()) {
            out.add(new String[] { member, "RAW_AUTHORITY", String.join("|", rawAuthorities) });
        }
    }

    private static void collectNames(Matcher call, Set<String> into) {
        while (call.find()) {
            Matcher q = QUOTED.matcher(call.group(1));
            while (q.find()) {
                into.add(q.group(1));
            }
        }
    }
}
