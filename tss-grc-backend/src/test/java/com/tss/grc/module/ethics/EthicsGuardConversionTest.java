package com.tss.grc.module.ethics;

import com.tss.grc.module.ethics.coi.controller.CoiController;
import com.tss.grc.module.ethics.whistleblower.controller.WhistleblowerCaseController;
import com.tss.grc.module.ethics.whistleblower.controller.WhistleblowerRetaliationController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-145(e) slice 6 — {@code ethics} converted from {@code hasAnyRole} to {@code hasPermission}. The last
 * module in the queue, and the one where the programme's own measurement discipline was most dangerous.
 *
 * <h3>An inert catalogue is not evidence of anything</h3>
 * {@code ETHICS:READ}, {@code ETHICS:WRITE} and {@code ETHICS:APPROVE} existed with grants attached, and
 * before this slice {@code hasPermission('ETHICS'} occurred <b>zero</b> times in {@code src/main}. Their
 * descriptions named COI declarations, whistleblower cases and recusals — they were catalogued <em>for</em>
 * these three controllers and never wired to them. For aml/cpty/sharia/openbanking the catalogue's grants
 * were the population to preserve; here they conferred nothing, so the <b>guard</b> was the only ground
 * truth and V371 made the catalogue faithful to it, adding and revoking without changing one person's real
 * access.
 *
 * <pre>
 *   A  COI register, 11 sites   3 roles  -&gt; ETHICS:READ / WRITE / APPROVE  (exact match)
 *   B  hotline read,   6 sites  3 roles  -&gt; ETHICS:READ_CASES   &lt;-- created by V371
 *   C  hotline triage, 5 sites  2 roles  -&gt; ETHICS:TRIAGE       &lt;-- created by V371
 *   D  retaliation,    4 sites  1 role   -&gt; ETHICS:PROTECT      &lt;-- created by V371
 * </pre>
 *
 * <h3>⚠ Why none of B, C or D may be re-derived from a population diff</h3>
 * Measured as <em>users</em> in dev, A, C and D are the <b>same two people</b> ({@code admin},
 * {@code compliance}) and B adds one ({@code auditor}). As <em>roles</em> they are four different
 * authorities. The difference is carried entirely by {@code ETHICS_OFFICER} and {@code BOARD_SECRETARY},
 * which have <b>0 active users</b> each. Mapping C and D onto {@code ETHICS:WRITE} — which the catalogue's
 * own description invited — would have been reported as population-preserving by any headcount, and would
 * have:
 *
 * <ul>
 *   <li>let the ethics officer who triaged and investigated a hotline case rule on the <b>retaliation
 *       complaint about that investigation</b> — the identical self-review defect V367 removed from
 *       {@code sharia} three slices earlier, re-introduced in another module inside the same programme;</li>
 *   <li>put a {@code BOARD_SECRETARY} inside hotline case files that may name, or be about, the board.</li>
 * </ul>
 *
 * <p>{@link #theHotlineIsNotTheRegisterAndDispositionIsNotTriage()} exists because both collapses look like
 * tidying, and <b>no population measurement in this database can refute either</b>.
 *
 * <h3>What this does NOT claim</h3>
 * <ul>
 *   <li>{@code AUDIT_COMMITTEE_MEMBER}, the fifth name in both hotline read guards, has no
 *       {@code iam.roles} row. Conversion drops it, so its intent — the audit committee reading the
 *       hotline — is <b>unmet</b>, not satisfied: committee membership is a seat (F2-26). Same disposition
 *       slice 5 reached for the same ghost.</li>
 *   <li>The retaliation javadoc promised an "HR Director" protector. No {@code HR_DIRECTOR} row exists
 *       ({@code HR_LEAD} does) and the guard never named either, so {@code ETHICS:PROTECT} is granted to
 *       {@code COMPLIANCE_OFFICER} alone — faithful to the guard, and now a grant a tenant can widen
 *       rather than a jar somebody has to ship.</li>
 *   <li>{@code INTERNAL_AUDITOR} loses {@code ETHICS:READ} (V371) and cannot read the COI register. That
 *       was already true of the role list this replaces; whether it <em>should</em> be is an owner's
 *       decision, filed rather than smuggled in.</li>
 * </ul>
 *
 * <h3>Two surfaces that had to move with the guards</h3>
 * {@code CoiService#declarantFor} recited the same three role names to decide who may file a declaration on
 * an employee's behalf, and {@code AttachmentAccessPolicy} carried the parents' read lists for
 * {@code COI_CAMPAIGN} and {@code WHISTLEBLOWER_CASE}. Both now read the permission. Leaving either behind
 * would have split one authority in two — a tenant granting {@code ETHICS:WRITE} to a fourth role would get
 * campaign management but not filing-on-behalf, and a role holding {@code ETHICS:READ_CASES} would open a
 * hotline case and then 403 on its attachments.
 */
class EthicsGuardConversionTest {

    private static final String READ        = "hasPermission('ETHICS','READ')";
    private static final String WRITE       = "hasPermission('ETHICS','WRITE')";
    private static final String APPROVE     = "hasPermission('ETHICS','APPROVE')";
    private static final String READ_CASES  = "hasPermission('ETHICS','READ_CASES')";
    private static final String TRIAGE      = "hasPermission('ETHICS','TRIAGE')";
    private static final String PROTECT     = "hasPermission('ETHICS','PROTECT')";
    private static final String AUTHED      = "isAuthenticated()";
    private static final String PUBLIC      = "permitAll()";

    private static final Path COI_SRC = Path.of(
            "src/main/java/com/tss/grc/module/ethics/coi/controller/CoiController.java");
    private static final Path CASE_SRC = Path.of("src/main/java/com/tss/grc/module/ethics/whistleblower/"
            + "controller/WhistleblowerCaseController.java");
    private static final Path RETAL_SRC = Path.of("src/main/java/com/tss/grc/module/ethics/whistleblower/"
            + "controller/WhistleblowerRetaliationController.java");
    private static final List<Path> SOURCES = List.of(COI_SRC, CASE_SRC, RETAL_SRC);

    private static final Path V371 = Path.of("src/main/resources/db/migration/sqlserver/"
            + "V371__ethics_the_inert_catalogue_and_the_independence_the_user_counts_hid.sql");

    /** {@code Class#method} → guard. Exhaustive: an unlisted endpoint is an unreviewed guard. */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>() {{
        // ── the COI register: one role list, split by what the endpoint does ──────────────
        put("CoiController#listCampaigns",               READ);
        put("CoiController#getCampaign",                 READ);
        put("CoiController#listDeclarationsByCampaign",  READ);
        put("CoiController#getDeclaration",              READ);
        put("CoiController#recusalsByUser",              READ);
        put("CoiController#createCampaign",              WRITE);
        put("CoiController#openCampaign",                WRITE);
        put("CoiController#closeCampaign",               WRITE);
        put("CoiController#archiveCampaign",             WRITE);
        put("CoiController#recordRecusal",               WRITE);
        put("CoiController#reviewDeclaration",           APPROVE);
        // F2-144(b): every employee is under the obligation this discharges. Not part of the conversion,
        // pinned by CoiSubmitGuardTest and listed in ControllerAuthorizationRatchetTest.SELF_SCOPED.
        put("CoiController#submitDeclaration",           AUTHED);

        // ── the hotline: anonymous intake, then a confidential case file ──────────────────
        put("WhistleblowerCaseController#publicSubmit",       PUBLIC);
        put("WhistleblowerCaseController#publicStatus",       PUBLIC);
        put("WhistleblowerCaseController#publicReply",        PUBLIC);
        put("WhistleblowerCaseController#list",               READ_CASES);
        put("WhistleblowerCaseController#stats",              READ_CASES);
        put("WhistleblowerCaseController#get",                READ_CASES);
        put("WhistleblowerCaseController#updates",            READ_CASES);
        put("WhistleblowerCaseController#triage",             TRIAGE);
        put("WhistleblowerCaseController#startInvestigation", TRIAGE);
        put("WhistleblowerCaseController#investigationUpdate", TRIAGE);
        put("WhistleblowerCaseController#close",              TRIAGE);
        put("WhistleblowerCaseController#linkCapa",           TRIAGE);

        // ── retaliation: read with the hotline, decide apart from it ──────────────────────
        put("WhistleblowerRetaliationController#list",     READ_CASES);
        put("WhistleblowerRetaliationController#get",      READ_CASES);
        put("WhistleblowerRetaliationController#claim",    PROTECT);
        put("WhistleblowerRetaliationController#validate", PROTECT);
        put("WhistleblowerRetaliationController#uphold",   PROTECT);
        put("WhistleblowerRetaliationController#dismiss",  PROTECT);
    }};

    @Test
    @DisplayName("every ethics endpoint is guarded, and by the tier it was reviewed at")
    void everyEndpointIsPinnedToATier() {
        Map<String, String> actual = new TreeMap<>();
        for (Class<?> c : List.of(CoiController.class, WhistleblowerCaseController.class,
                                  WhistleblowerRetaliationController.class)) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getDeclaringClass().equals(c) || m.isSynthetic()) continue;
                PreAuthorize guard = m.getAnnotation(PreAuthorize.class);
                if (guard != null) actual.put(c.getSimpleName() + "#" + m.getName(), guard.value());
            }
        }
        // Anti-vacuity: a reflection read that resolves nothing satisfies the entries assertion below.
        assertThat(actual)
                .as("the three ethics controllers must expose the %d guarded endpoints this test reviewed; "
                    + "a reflection read that finds none would pass the tier assertion vacuously",
                    EXPECTED.size())
                .hasSize(EXPECTED.size());
        assertThat(actual)
                .as("each endpoint must keep the tier it was reviewed at — four distinct guard name-sets "
                    + "were measured against iam.role_permissions before the conversion, and three of them "
                    + "resolve to the same USERS today; see this class's javadoc")
                .containsExactlyInAnyOrderEntriesOf(new TreeMap<>(EXPECTED));
    }

    /**
     * The two collapses this guards against are the tidy-looking kind. Six actions on one module reads like
     * duplication; the endpoints all return ethics data; and <b>no population measurement in this database
     * can distinguish the register tier, the triage tier and the retaliation tier</b>, because the roles
     * that separate them are unstaffed. So the structure is asserted directly, with the reason attached to
     * the failure message rather than left in a commit note.
     */
    @Test
    @DisplayName("the hotline is not the COI register, and retaliation disposition is not triage")
    void theHotlineIsNotTheRegisterAndDispositionIsNotTriage() {
        assertThat(new TreeSet<>(List.of(READ, WRITE, APPROVE, READ_CASES, TRIAGE, PROTECT)))
                .as("six distinct actions; identical strings here would make the tiers decorative")
                .hasSize(6);

        for (String hotlineRead : List.of("WhistleblowerCaseController#list",
                                          "WhistleblowerCaseController#stats",
                                          "WhistleblowerCaseController#get",
                                          "WhistleblowerCaseController#updates",
                                          "WhistleblowerRetaliationController#list",
                                          "WhistleblowerRetaliationController#get")) {
            assertThat(EXPECTED.get(hotlineRead))
                    .as("%s serves a confidential hotline case file. Widening it to ETHICS:READ would admit "
                        + "BOARD_SECRETARY — who curates the COI register and whom this controller's role "
                        + "list deliberately excluded, because a case may name or be about the board. Both "
                        + "roles are unstaffed in dev, so no user-population diff can object", hotlineRead)
                    .isEqualTo(READ_CASES)
                    .isNotEqualTo(READ);
        }

        for (String disposition : List.of("WhistleblowerRetaliationController#validate",
                                          "WhistleblowerRetaliationController#uphold",
                                          "WhistleblowerRetaliationController#dismiss",
                                          "WhistleblowerRetaliationController#claim")) {
            assertThat(EXPECTED.get(disposition))
                    .as("%s disposes of a retaliation complaint, which is usually ABOUT the handling of the "
                        + "underlying case. Merging it into ETHICS:TRIAGE or ETHICS:WRITE lets the ethics "
                        + "officer who ran the investigation rule on the complaint against it — the "
                        + "self-review defect V367 removed from sharia. ETHICS:PROTECT is granted to "
                        + "COMPLIANCE_OFFICER alone and V371 asserts nobody else holds it", disposition)
                    .isEqualTo(PROTECT)
                    .isNotEqualTo(TRIAGE)
                    .isNotEqualTo(WRITE);
        }

        assertThat(EXPECTED.get("CoiController#reviewDeclaration"))
                .as("deciding a declaration's outcome is an approval, not a write; the catalogue said so "
                    + "before any guard consulted it")
                .isEqualTo(APPROVE)
                .isNotEqualTo(WRITE);

        assertThat(EXPECTED.get("CoiController#submitDeclaration"))
                .as("F2-144(b) is not undone by this conversion: declaring a conflict of interest is an "
                    + "obligation every employee is under, and a grant list can only wrongly exclude a "
                    + "declarant")
                .isEqualTo(AUTHED);
    }

    @Test
    @DisplayName("no guard in the three ethics controllers authorises by role name any more")
    void noGuardRecitesARoleName() throws IOException {
        for (Path source : SOURCES) {
            String raw = Files.readString(source, StandardCharsets.UTF_8);
            assertThat(stripComments(raw))
                    .as("%s: the conversion is the point of F2-145(e); a role-name guard here is a "
                        + "regression", source.getFileName())
                    .doesNotContain("hasAnyRole")
                    .doesNotContain("hasRole(");
            // The stripper must not be the reason it passes — each file still documents, in a comment,
            // what its guards were converted FROM.
            assertThat(raw)
                    .as("%s no longer records what it was converted from, so this test's comment-stripping "
                        + "has become untested; re-point it or drop it", source.getFileName())
                    .contains("hasAnyRole");
        }
    }

    @Test
    @DisplayName("the guards name only ETHICS, and only actions the catalogue holds after V371")
    void theGuardsNameOnlyCataloguedPermissions() throws IOException {
        var pairs = new TreeSet<String>();
        for (Path source : SOURCES) {
            String code = stripComments(Files.readString(source, StandardCharsets.UTF_8));
            Matcher m = Pattern.compile("hasPermission\\('([A-Z_]+)'\\s*,\\s*'([A-Z_]+)'\\)").matcher(code);
            while (m.find()) pairs.add(m.group(1) + ":" + m.group(2));
        }
        assertThat(pairs)
                .as("a guard citing a module or action outside the catalogue can never be satisfied by a "
                    + "grant — it is a ghost with different spelling (F2-143). Three of these six exist "
                    + "only because V371 created them")
                .containsExactly("ETHICS:APPROVE", "ETHICS:PROTECT", "ETHICS:READ", "ETHICS:READ_CASES",
                                 "ETHICS:TRIAGE", "ETHICS:WRITE");
    }

    /**
     * V371 must stay in the tree. Three of the six actions above exist in <b>no</b> earlier migration, so on
     * a fresh install without this file 15 of the 26 converted endpoints cite actions no grant can satisfy
     * and become administrator-only — the {@code hasAuthority('KB_WRITE')} failure of F2-53. Nothing else in
     * the hermetic suite can reach a database to notice.
     */
    @Test
    @DisplayName("V371 — the migration behind the three new ETHICS actions is present and still does the work")
    void theNewActionsStillHaveTheirMigration() throws IOException {
        assertThat(Files.exists(V371))
                .as("V371 catalogues READ_CASES, TRIAGE and PROTECT; without it 15 of the 26 guards above "
                    + "cite actions no grant can satisfy")
                .isTrue();
        String sql = Files.readString(V371, StandardCharsets.UTF_8).replaceAll("(?m)^--[^\\n]*", " ");
        assertThat(sql)
                .as("V371 must still INSERT all three permissions and grant them, not merely describe it")
                .contains("INSERT INTO iam.permissions")
                .contains("ETHICS_READ_CASES")
                .contains("ETHICS_TRIAGE")
                .contains("ETHICS_PROTECT")
                .contains("INSERT INTO iam.role_permissions");
        assertThat(sql)
                .as("and must still revoke INTERNAL_AUDITOR's ETHICS:READ — the COI register's role list "
                    + "never named internal audit, and ETHICS:READ is now that list")
                .contains("DELETE rp");
        assertThat(sql)
                .as("and must still assert the two controls the user counts could not see: nobody but the "
                    + "compliance officer holds PROTECT, and no board secretary holds a hotline action")
                .contains("independent protector")
                .contains("BOARD_SECRETARY holds a hotline action")
                .contains("is not strict");
    }

    /** Block then line, so a {@code //} inside a block comment cannot truncate the block. */
    private static String stripComments(String java) {
        return java.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
    }
}
