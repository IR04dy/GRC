package com.tss.grc.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
     * <p>Raising this number means adding an endpoint that authorises by identity in a platform
     * whose authorisation model is permissions, and whose role names have a 54% miss rate. Convert
     * instead. Where a role genuinely is the right unit — {@code PLATFORM_ADMIN}-only platform
     * administration is the honest case — say so in the guard's javadoc, because the next reader
     * cannot tell that apart from an unconverted one.
     */
    private static final int BASELINE_PURE_ROLE_SITES = 561;

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

    // ── extraction ────────────────────────────────────────────────────────────────────────────

    private record Census(int controllers, int roleSites, int pureSites, int pureUnderModule,
                          int rawAuthoritySites, Set<String> modulePackages, Set<String> roleNames,
                          Set<String> rawAuthorities, List<String> lines) { }

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
