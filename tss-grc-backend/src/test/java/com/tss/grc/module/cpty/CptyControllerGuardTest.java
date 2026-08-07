package com.tss.grc.module.cpty;

import com.tss.grc.module.cpty.controller.CptyController;
import org.junit.jupiter.api.DisplayName;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-145(e) slice 4 — {@code cpty} converted from {@code hasAnyRole} to {@code hasPermission}.
 *
 * <p>Also the module that carried {@code cpty}'s <b>zero-test</b> status: it is one of the eight packages
 * {@code MEASURED_BASELINE} lists with no test file at all, so this class is its first.
 *
 * <h3>The blocker here was a MISSING action, not a wrong grant, and that made the easy conversion the
 * dangerous one</h3>
 * V362 catalogued {@code CPTY} with READ and WRITE only. Its classifier derived each action from the
 * endpoint's HTTP verb, "or APPROVE when the method is approve/reject/transition/decide/sign-shaped" —
 * and this controller's two approval endpoints are {@code assignRating} and {@code upsertLimit}, named
 * like neither. So both were read as plain writes and the APPROVE tier's population was folded into
 * WRITE, which is how {@code CFO} came to hold {@code CPTY:WRITE} while appearing on no WRITE guard.
 *
 * <pre>
 *   READ     guard named 7 (1 ghost: CRO)   LOST none  GAINED none
 *   WRITE    guard named 4 (1 ghost: CRO)   LOST none  GAINED CFO      &lt;-- fixed by V369
 *   APPROVE  guard named 3 (1 ghost: CRO)   no CPTY:APPROVE existed    &lt;-- created by V369
 * </pre>
 *
 * <p>⚠ <b>The shortcut would have collapsed a SAMA control.</b> With no {@code CPTY:APPROVE}, the obvious
 * move is to point {@code APPROVE_GUARD} at the {@code CPTY:WRITE} that does exist — taking the approval
 * population from {@code {PLATFORM_ADMIN, CFO}} to include {@code CREDIT_OFFICER} and
 * {@code RISK_MANAGER}, so a credit officer could assign the internal credit rating on their own
 * counterparty and set their own large-exposure limit. <b>A missing catalogue entry is not a gap that
 * makes a conversion impossible; it is a gap that makes the wrong conversion easy.</b>
 *
 * <p>This is the <em>mirror image</em> of V368's defect in {@code openbanking}, where the same classifier
 * promoted a WRITE tier to APPROVE. One cause, both directions, and which direction you get depends on
 * nothing but whether a method happens to be named like a transition.
 *
 * <h3>What this does NOT claim</h3>
 * {@code CFO} is held by no active user in dev, so {@code CPTY:APPROVE} is reachable only through the
 * platform administrator's in-code bypass. That was already true — {@code APPROVE_GUARD} named
 * {@code PLATFORM_ADMIN}, a ghost, and a role nobody holds — so the conversion <b>reveals</b> a narrowness
 * rather than creating one. Distinguishing those two is why the population was measured before the edit.
 */
class CptyControllerGuardTest {

    private static final String READ    = "hasPermission('CPTY','READ')";
    private static final String WRITE   = "hasPermission('CPTY','WRITE')";
    private static final String APPROVE = "hasPermission('CPTY','APPROVE')";

    private static final Path SOURCE = Path.of(
            "src/main/java/com/tss/grc/module/cpty/controller/CptyController.java");

    /** Exhaustive: an unlisted endpoint is an unreviewed guard and fails by name. */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>() {{
        // Counterparties — creating and maintaining the record is the analyst's.
        put("create",                   WRITE);
        put("update",                   WRITE);
        put("list",                     READ);
        put("get",                      READ);
        // Ratings. Recording what an action WAS is WRITE; deciding the rating is APPROVE.
        put("assignRating",             APPROVE);
        put("recordAction",             WRITE);
        put("listRatings",              READ);
        put("listActions",              READ);
        // Limits. Setting or raising the large-exposure limit is the credit authority's decision;
        // recording utilisation against an existing limit is bookkeeping.
        put("upsertLimit",              APPROVE);
        put("recordUtilisation",        WRITE);
        put("listLimits",               READ);
        put("listLimitsForCounterparty", READ);
        put("listEvents",               READ);
        put("dashboard",                READ);
    }};

    @Test
    @DisplayName("every CptyController endpoint is guarded, and by the tier it was reviewed at")
    void everyEndpointIsPinnedToATier() {
        Map<String, String> actual = new TreeMap<>();
        for (Method m : CptyController.class.getDeclaredMethods()) {
            if (!m.getDeclaringClass().equals(CptyController.class) || m.isSynthetic()) continue;
            PreAuthorize guard = m.getAnnotation(PreAuthorize.class);
            if (guard != null) actual.put(m.getName(), guard.value());
        }
        // Anti-vacuity: a reflection read that resolves nothing satisfies every containsExactly below.
        assertThat(actual)
                .as("CptyController must expose the 14 guarded endpoints this test reviewed; a reflection "
                    + "read that finds none would pass the tier assertions vacuously")
                .hasSize(EXPECTED.size());
        assertThat(actual)
                .as("each endpoint must keep the tier it was reviewed at — recordAction/recordUtilisation "
                    + "are WRITE while assignRating/upsertLimit are APPROVE, see this class's javadoc")
                .containsExactlyInAnyOrderEntriesOf(new TreeMap<>(EXPECTED));
    }

    @Test
    @DisplayName("no guard in CptyController authorises by role name any more")
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
    @DisplayName("the guards name only CPTY, and only actions the catalogue holds after V369")
    void theGuardsNameOnlyCataloguedPermissions() throws IOException {
        String code = stripComments(Files.readString(SOURCE, StandardCharsets.UTF_8));
        Matcher m = Pattern.compile("hasPermission\\('([A-Z_]+)'\\s*,\\s*'([A-Z_]+)'\\)").matcher(code);
        var pairs = new TreeSet<String>();
        while (m.find()) pairs.add(m.group(1) + ":" + m.group(2));

        assertThat(pairs)
                .as("a guard citing a module or action outside the catalogue can never be satisfied by a "
                    + "grant — it is a ghost with different spelling (F2-143). CPTY:APPROVE exists only "
                    + "because V369 created it")
                .containsExactly("CPTY:APPROVE", "CPTY:READ", "CPTY:WRITE");
    }

    /**
     * V369 must stay in the tree. The APPROVE tier depends on an action that exists in <b>no</b> earlier
     * migration, so on a fresh install without this file {@code CPTY:APPROVE} is uncatalogued and the two
     * approval guards can be satisfied by nobody but the platform administrator — the
     * {@code hasAuthority('KB_WRITE')} failure of F2-53, one module over. Nothing else in the hermetic
     * suite can reach a database to notice.
     */
    @Test
    @DisplayName("V369 — the migration that creates CPTY:APPROVE is still present and still creates it")
    void theApproveTierStillHasItsMigration() throws IOException {
        Path v369 = Path.of("src/main/resources/db/migration/sqlserver/"
                            + "V369__cpty_approve_the_action_the_classifier_never_created.sql");
        assertThat(Files.exists(v369))
                .as("V369 catalogues CPTY:APPROVE and grants it to CFO; without it the APPROVE guards "
                    + "above cite an action no grant can satisfy")
                .isTrue();
        String sql = Files.readString(v369, StandardCharsets.UTF_8).replaceAll("(?m)^--[^\\n]*", " ");
        assertThat(sql)
                .as("V369 must still INSERT the permission and grant it, not merely describe doing so")
                .contains("INSERT INTO iam.permissions")
                .contains("CPTY_APPROVE")
                .contains("INSERT INTO iam.role_permissions");
        assertThat(sql)
                .as("and must still revoke CFO's mis-derived CPTY:WRITE, or the write tier stays wide")
                .contains("DELETE rp");
    }

    /** Block then line, so a {@code //} inside a block comment cannot truncate the block. */
    private static String stripComments(String java) {
        return java.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
    }
}
