package com.tss.grc.module.aml;

import com.tss.grc.module.aml.sanctions.controller.SanctionsScreeningController;
import org.junit.jupiter.api.DisplayName;
import com.tss.grc.support.GuardExpressions;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-145(e) slice 5 — {@code aml} converted from {@code hasAnyRole} to {@code hasPermission}.
 *
 * <h3>This slice inverts the previous three</h3>
 * V367/V368 found a grant that was <em>wrong</em> and revoked it. V369 found an action that was
 * <em>missing</em> and created it. Here V362's catalogue is neither: three of the six distinct name-sets in
 * this controller map onto {@code AML_READ}, {@code AML_WRITE} and {@code AML_APPROVE} <b>exactly</b> — the
 * first time the classifier's output has survived measurement unaltered. What was absent is a
 * <b>gradient</b>, and it is in the READ tier, which is not where this slice was queued to look.
 *
 * <pre>
 *   A  lists + checks        7 roles  -&gt; AML:READ           (exact match, ghost aside)
 *   B  a list's entries      6 roles  -&gt; AML:READ_ENTRIES   &lt;-- created by V370
 *   C  hit queue + dashboard 5 roles  -&gt; AML:READ_HITS      &lt;-- created by V370
 *   D  create/amend/import   2 roles  -&gt; AML:MANAGE_LISTS   &lt;-- created by V370
 *   E  refresh from upstream 1 role   -&gt; AML:REFRESH_LISTS  &lt;-- created by V370
 *   F  perform a screening   4 roles  -&gt; AML:WRITE          (exact match)
 *   D  review/escalate a hit 2 roles  -&gt; AML:APPROVE        (exact match)
 * </pre>
 *
 * <h3>⚠ Why the gradient may not be re-derived from a population diff</h3>
 * Sets A, B and C differ only by which front-line roles they admit ({@code ONBOARDING_AGENT}, then
 * {@code KYC_OFFICER}). Measured in dev, {@code A\B}, {@code B\C} and {@code A\C} are all <b>empty</b> —
 * every tier resolves to the same three users — because {@code AML_OFFICER}, {@code KYC_OFFICER} and
 * {@code ONBOARDING_AGENT} each have <b>0 active users</b>. <b>A zero population diff measured in a tenant
 * that never provisioned the distinguishing roles is not evidence that there is no distinction</b>; it is
 * evidence that the instrument cannot see one. Collapsing the three onto one {@code AML:READ} would have
 * measured as population-preserving, verified live, and handed the hit queue — which customers matched a
 * sanctions list — to every onboarding agent, which is the FATF R.21 / SAMA tipping-off exposure.
 *
 * <p>{@link #theReadGradientIsThreeDistinctActions()} exists because that collapse is a tidy-looking
 * refactor that no population measurement in this database can refute.
 *
 * <h3>What this does NOT claim</h3>
 * All seven read guards recited {@code AUDIT_COMMITTEE_MEMBER}, which exists in no {@code iam.roles} row.
 * Conversion deletes the name, so its intent — "the audit committee can read the sanctions surface" — is
 * <b>unmet</b>, not satisfied: committee membership is modelled as a seat, which is F2-26's territory.
 * Separately, {@code AML:REFRESH_LISTS} is granted to {@code AML_OFFICER} alone, which has 0 active users,
 * so it is administrator-only — <b>already true</b> of the guard it replaces, so the conversion reveals an
 * attrition narrowness rather than creating one (F2-52).
 *
 * <h3>⚠ Do not delete AML_OFFICER, KYC_OFFICER or ONBOARDING_AGENT as unused roles</h3>
 * After this conversion those three names occur <b>0</b> times in the whole of {@code src/main} — this
 * controller was their only site — and none of them has an active user, so both of the obvious checks say
 * "unused". They are not: they hold the AML grants (7, 3 and 2 actions), and removing them would silently
 * revoke every tier above. The conversion moved their authority out of a string comparison and into
 * {@code iam.role_permissions}, which is the point, and is also what makes them invisible to {@code grep}.
 */
class SanctionsScreeningControllerGuardTest {

    private static final String READ          = "hasPermission('AML','READ')";
    private static final String READ_ENTRIES  = "hasPermission('AML','READ_ENTRIES')";
    private static final String READ_HITS     = "hasPermission('AML','READ_HITS')";
    private static final String WRITE         = "hasPermission('AML','WRITE')";
    private static final String MANAGE_LISTS  = "hasPermission('AML','MANAGE_LISTS')";
    private static final String REFRESH_LISTS = "hasPermission('AML','REFRESH_LISTS')";
    private static final String APPROVE       = "hasPermission('AML','APPROVE')";

    private static final Path SOURCE = Path.of(
            "src/main/java/com/tss/grc/module/aml/sanctions/controller/SanctionsScreeningController.java");

    private static final Path V370 = Path.of("src/main/resources/db/migration/sqlserver/"
            + "V370__aml_the_read_gradient_the_catalogue_could_not_express.sql");

    /** Exhaustive: an unlisted endpoint is an unreviewed guard and fails by name. */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>() {{
        // Reads, in ascending sensitivity. The list exists / the names inside it / who matched.
        put("listLists",         READ);
        put("getList",           READ);
        put("listChecks",        READ);
        put("getCheck",          READ);
        put("listEntries",       READ_ENTRIES);
        put("listHits",          READ_HITS);
        put("dashboard",         READ_HITS);
        // Running a screening is the front-line action and is deliberately the widest write.
        put("performScreening",  WRITE);
        // Curating the list. Separate from APPROVE despite an identical population today, so a tenant can
        // grant list curation without also granting disposal of the hits that curation produces.
        put("createList",        MANAGE_LISTS);
        put("updateList",        MANAGE_LISTS);
        put("bulkImportEntries", MANAGE_LISTS);
        // Pulling entries from the upstream source — the narrowest guard in the controller.
        put("refreshList",       REFRESH_LISTS);
        // Disposing of a hit.
        put("reviewHit",         APPROVE);
        put("escalateHit",       APPROVE);
    }};

    @Test
    @DisplayName("every SanctionsScreeningController endpoint is guarded, and by the tier it was reviewed at")
    void everyEndpointIsPinnedToATier() {
        Map<String, String> actual = new TreeMap<>();
        for (Method m : SanctionsScreeningController.class.getDeclaredMethods()) {
            if (!m.getDeclaringClass().equals(SanctionsScreeningController.class) || m.isSynthetic()) continue;
            PreAuthorize guard = m.getAnnotation(PreAuthorize.class);
            if (guard != null) actual.put(m.getName(), guard.value());
        }
        // Anti-vacuity: a reflection read that resolves nothing satisfies every containsExactly below.
        assertThat(actual)
                .as("SanctionsScreeningController must expose the 14 guarded endpoints this test reviewed; "
                    + "a reflection read that finds none would pass the tier assertions vacuously")
                .hasSize(EXPECTED.size());
        assertThat(actual)
                .as("each endpoint must keep the tier it was reviewed at — six distinct guard name-sets were "
                    + "measured against iam.role_permissions before the conversion, see this class's javadoc")
                .containsExactlyInAnyOrderEntriesOf(new TreeMap<>(EXPECTED));
    }

    /**
     * The collapse this guards against is not a hypothetical: three read actions on one controller reads
     * like duplication, the endpoints all return sanctions data, and <b>no population measurement in this
     * database can distinguish the three tiers</b> because the roles that separate them are unstaffed. So
     * the structure has to be asserted directly, with the regulatory reason attached to the failure.
     */
    @Test
    @DisplayName("the AML read gradient is three distinct actions, narrowing as the data gets more sensitive")
    void theReadGradientIsThreeDistinctActions() {
        assertThat(new TreeSet<>(java.util.List.of(READ, READ_ENTRIES, READ_HITS)))
                .as("the three read tiers must be three DIFFERENT actions; identical strings here would "
                    + "make the gradient decorative")
                .hasSize(3);

        assertThat(EXPECTED.get("listEntries"))
                .as("listEntries returns the sanctioned NAMES inside a list. It must not fall back to "
                    + "AML:READ — that tier includes ONBOARDING_AGENT, which the guard excluded")
                .isEqualTo(READ_ENTRIES)
                .isNotEqualTo(READ);

        for (String hitEndpoint : java.util.List.of("listHits", "dashboard")) {
            assertThat(EXPECTED.get(hitEndpoint))
                    .as("%s exposes WHICH CUSTOMERS matched a sanctions list. Widening it to AML:READ or "
                        + "AML:READ_ENTRIES would admit KYC_OFFICER and/or ONBOARDING_AGENT — disclosure of "
                        + "a flagged customer to front-line staff is the FATF R.21 / SAMA tipping-off "
                        + "offence, and it is the reason these are separate actions", hitEndpoint)
                    .isEqualTo(READ_HITS)
                    .isNotEqualTo(READ)
                    .isNotEqualTo(READ_ENTRIES);
        }

        assertThat(MANAGE_LISTS)
                .as("curating the list that produces a hit and disposing of that hit are the two halves of "
                    + "a maker/checker pair; identical populations today do not make them one action")
                .isNotEqualTo(APPROVE);
    }

    @Test
    @DisplayName("no guard in SanctionsScreeningController authorises by role name any more")
    void noGuardRecitesARoleName() throws IOException {
        String raw = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String code = stripComments(raw);

        assertThat(code)
                .as("the conversion is the point of F2-145(e); a role-name guard here is a regression")
                .doesNotContain("hasAnyRole")
                .doesNotContain("hasRole(");

        // The stripper must not be the reason it passes — the raw file DOES contain the string, in the
        // comment block that records what the guards were converted from.
        assertThat(raw)
                .as("if the controller no longer documents what it was converted FROM, this test's "
                    + "comment-stripping has become untested; re-point it or drop it")
                .contains("hasAnyRole");
    }

    @Test
    @DisplayName("the guards name only AML, and only actions the catalogue holds after V370")
    void theGuardsNameOnlyCataloguedPermissions() throws IOException {
        String code = stripComments(Files.readString(SOURCE, StandardCharsets.UTF_8));
        // F2-263 — shared extractor. This copy's own spelling had no whitespace tolerance after
        // the opening paren or before the closing one, so a formatter run would have shrunk the
        // population and left the assertion below passing over less than it names.
        var pairs = new TreeSet<>(GuardExpressions.permissionPairsIn(code));

        assertThat(pairs)
                .as("a guard citing a module or action outside the catalogue can never be satisfied by a "
                    + "grant — it is a ghost with different spelling (F2-143). Four of these seven exist "
                    + "only because V370 created them")
                .containsExactly("AML:APPROVE", "AML:MANAGE_LISTS", "AML:READ", "AML:READ_ENTRIES",
                                 "AML:READ_HITS", "AML:REFRESH_LISTS", "AML:WRITE");
    }

    /**
     * V370 must stay in the tree. Four of the seven actions above exist in <b>no</b> earlier migration, so
     * on a fresh install without this file those guards cite actions no grant can satisfy and the endpoints
     * become administrator-only — the {@code hasAuthority('KB_WRITE')} failure of F2-53, four tiers at once.
     * Nothing else in the hermetic suite can reach a database to notice.
     */
    @Test
    @DisplayName("V370 — the migration that creates the four new AML actions is present and still creates them")
    void theNewActionsStillHaveTheirMigration() throws IOException {
        assertThat(Files.exists(V370))
                .as("V370 catalogues READ_ENTRIES, READ_HITS, MANAGE_LISTS and REFRESH_LISTS; without it "
                    + "four of the seven guards above cite actions no grant can satisfy")
                .isTrue();
        String sql = Files.readString(V370, StandardCharsets.UTF_8).replaceAll("(?m)^--[^\\n]*", " ");
        assertThat(sql)
                .as("V370 must still INSERT all four permissions, not merely describe doing so")
                .contains("INSERT INTO iam.permissions")
                .contains("AML_READ_ENTRIES")
                .contains("AML_READ_HITS")
                .contains("AML_MANAGE_LISTS")
                .contains("AML_REFRESH_LISTS")
                .contains("INSERT INTO iam.role_permissions");
        assertThat(sql)
                .as("and must still assert the gradient is NESTED and STRICT — three separate grant counts "
                    + "can each be right while the sets overlap wrongly, and equal sets satisfy containment "
                    + "while expressing nothing")
                .contains("READ_ENTRIES without READ")
                .contains("strictly decreasing");
    }

    /** Block then line, so a {@code //} inside a block comment cannot truncate the block. */
    private static String stripComments(String java) {
        return java.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
    }
}
