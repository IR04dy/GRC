package com.tss.grc.module.openbanking;

import com.tss.grc.module.openbanking.controller.ObController;
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
 * F2-145(e) slice 2 — {@code openbanking} converted from {@code hasAnyRole} to {@code hasPermission}.
 *
 * <p>All 18 endpoints authorised by role name, so the {@code OPENBANKING} permissions V362 catalogued
 * decided nothing: {@code hasAnyRole} compares authority strings and never reaches
 * {@code ModulePermissionEvaluator}. {@code CRO} — recited on the APPROVE tier — is seated in no row of
 * {@code iam.roles}, so that leg was permanently false and the tier was narrower than it read.
 *
 * <h3>The APPROVE tier was NOT population-preserving, and that is why a migration shipped first</h3>
 * Measured per action against {@code iam.role_permissions} before the edit:
 *
 * <pre>
 *   READ     guard named 6 (0 ghosts)   LOST none  GAINED none
 *   WRITE    guard named 3 (0 ghosts)   LOST none  GAINED none
 *   APPROVE  guard named 3 (1 ghost: CRO)  LOST none  GAINED OPEN_BANKING_OFFICER   <-- blocker
 * </pre>
 *
 * The catalogue granted {@code OPENBANKING:APPROVE} to {@code OPEN_BANKING_OFFICER}, which no APPROVE
 * guard in this controller has ever named. Converting as-is would have handed the module's operator
 * {@code createTpp}, {@code transitionTpp} (KYB approval / suspend / revoke) and {@code notifySama} —
 * i.e. the officer who grants customer consents to a TPP would also admit that TPP. V368 revokes the
 * grant, so the two populations agree and the conversion changes no request's outcome.
 *
 * <p>The provenance matters more than the single grant: V362's own header says its grants are
 * <em>derived</em> — action from the HTTP verb, "or APPROVE when the method is
 * approve/reject/transition/decide/sign-shaped". That classifier reads the method and never reads the
 * tier the guard assigns, and this controller puts two transition-shaped methods
 * ({@code transitionIncident}, {@code revokeConsent}) on WRITE. So the whole WRITE population was
 * promoted to APPROVE. The same shape produced {@code SHARIA_AUDITOR}'s APPROVE (V367), {@code CFO}'s
 * {@code CPTY:WRITE} and {@code KYC_OFFICER}/{@code ONBOARDING_AGENT}'s {@code AML:WRITE} — four of the
 * six V362 modules. "Derived from the guards" is not the same as "faithful to the guards".
 *
 * <h3>Why this pins tiers rather than the absence of a shape</h3>
 * "No guard here calls {@code hasAnyRole}" passes the moment the names are swapped, and keeps passing if
 * {@code notifySama} is later re-pointed at the write constant — the failure mode
 * {@code FraudControllerGuardTest} and {@code HelpControllerGuardTest} were both rewritten to close. The
 * map below is exhaustive, so a new endpoint that is not listed fails by name.
 *
 * <p>⚠ The tiers are <b>not uniform by HTTP verb</b>, and a tidying sweep would break the module's only
 * separation of duties. {@code createTpp} is a POST on APPROVE because admitting a Third-Party Provider
 * is a control gate under SAMA's Open Banking Framework, not the operator's clerical act; while
 * {@code transitionIncident} is a POST on WRITE because incident handling <em>is</em> the operator's day
 * job. {@code transitionTpp} and {@code transitionIncident} therefore sit on different tiers despite
 * sharing a verb and a name shape — which is precisely the distinction V362's classifier could not see.
 */
class ObControllerGuardTest {

    private static final String READ    = "hasPermission('OPENBANKING','READ')";
    private static final String WRITE   = "hasPermission('OPENBANKING','WRITE')";
    private static final String APPROVE = "hasPermission('OPENBANKING','APPROVE')";

    private static final Path SOURCE = Path.of(
            "src/main/java/com/tss/grc/module/openbanking/controller/ObController.java");

    /**
     * Every endpoint and the tier it belongs to. Exhaustive on purpose — an unlisted endpoint is an
     * unreviewed guard, and {@link #everyEndpointIsPinnedToATier()} fails for it.
     */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>() {{
        // TPPs. Admission and de-admission are the control gate; maintaining the record is not.
        put("createTpp",           APPROVE);
        put("transitionTpp",       APPROVE);
        put("updateTpp",           WRITE);
        put("listTpps",            READ);
        put("getTpp",              READ);
        put("listConsentsForTpp",  READ);
        put("listTppEvents",       READ);
        // Consents — granting and revoking a customer consent is the operator's day job.
        put("grantConsent",        WRITE);
        put("revokeConsent",       WRITE);
        put("listConsents",        READ);
        // Append-only API call audit.
        put("recordApiCall",       WRITE);
        put("listApiCalls",        READ);
        // Incidents. Raising and advancing them is WRITE; telling SAMA is the approval.
        put("raiseIncident",       WRITE);
        put("transitionIncident",  WRITE);
        put("notifySama",          APPROVE);
        put("listIncidents",       READ);
        put("getIncident",         READ);
        put("dashboard",           READ);
    }};

    @Test
    @DisplayName("every ObController endpoint is guarded, and by the tier it was reviewed at")
    void everyEndpointIsPinnedToATier() {
        Map<String, String> actual = new TreeMap<>();
        for (Method m : ObController.class.getDeclaredMethods()) {
            if (!m.getDeclaringClass().equals(ObController.class) || m.isSynthetic()) continue;
            PreAuthorize guard = m.getAnnotation(PreAuthorize.class);
            if (guard != null) actual.put(m.getName(), guard.value());
        }
        // Anti-vacuity: a reflection read that resolves nothing satisfies every containsExactly below.
        assertThat(actual)
                .as("ObController must expose the 18 guarded endpoints this test reviewed; a reflection "
                    + "read that finds none would pass the tier assertions vacuously")
                .hasSize(EXPECTED.size());
        assertThat(actual)
                .as("each endpoint must keep the tier it was reviewed at — the tiers are not uniform by "
                    + "verb, see this class's javadoc")
                .containsExactlyInAnyOrderEntriesOf(new TreeMap<>(EXPECTED));
    }

    @Test
    @DisplayName("no guard in ObController authorises by role name any more")
    void noGuardRecitesARoleName() throws IOException {
        String raw = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String code = stripComments(raw);

        // Comment-stripping is load-bearing here: the controller's own comment block explains what the
        // guards were converted FROM, so it names hasAnyRole in prose. An assertion over the raw text
        // would fail on the documentation of the fix.
        assertThat(code)
                .as("the conversion is the point of F2-145(e); a role-name guard here is a regression")
                .doesNotContain("hasAnyRole")
                .doesNotContain("hasRole(");

        // And the stripper must not be the reason it passes — the raw file DOES contain the string.
        assertThat(raw)
                .as("if the controller no longer documents what it was converted FROM, this test's "
                    + "comment-stripping has become untested; re-point it or drop it")
                .contains("hasAnyRole");
    }

    @Test
    @DisplayName("the guards name only OPENBANKING, and only actions V362 catalogued")
    void theGuardsNameOnlyCataloguedPermissions() throws IOException {
        String code = stripComments(Files.readString(SOURCE, StandardCharsets.UTF_8));
        Matcher m = Pattern.compile("hasPermission\\('([A-Z_]+)'\\s*,\\s*'([A-Z_]+)'\\)").matcher(code);
        var pairs = new TreeSet<String>();
        while (m.find()) pairs.add(m.group(1) + ":" + m.group(2));

        assertThat(pairs)
                .as("a guard citing a module or action outside the catalogue can never be satisfied by a "
                    + "grant — it is a ghost with different spelling (F2-143)")
                .containsExactly("OPENBANKING:APPROVE", "OPENBANKING:READ", "OPENBANKING:WRITE");
    }

    /**
     * V368 must stay in the tree, because the APPROVE tier's correctness depends on it and nothing else
     * in the unit suite can reach a database to notice its absence. If the file is renamed or dropped,
     * a fresh install catalogues {@code OPEN_BANKING_OFFICER} back onto APPROVE and this controller
     * silently widens — the failure would only ever show up as a live probe nobody re-ran.
     */
    @Test
    @DisplayName("V368 — the migration the APPROVE tier depends on is still present and still revokes")
    void theApproveTierStillHasItsMigration() throws IOException {
        Path v368 = Path.of("src/main/resources/db/migration/sqlserver/"
                            + "V368__open_banking_officer_does_not_approve_its_own_tpps.sql");
        assertThat(Files.exists(v368))
                .as("V368 revokes OPENBANKING:APPROVE from OPEN_BANKING_OFFICER; without it the "
                    + "APPROVE guards above admit the operator that grants the consents")
                .isTrue();
        String sql = Files.readString(v368, StandardCharsets.UTF_8);
        assertThat(sql.replaceAll("(?m)^--[^\\n]*", " "))
                .as("V368 must still contain the DELETE, not merely a header describing one")
                .contains("DELETE rp")
                .contains("OPEN_BANKING_OFFICER");
    }

    /** Block then line, so a {@code //} inside a block comment cannot truncate the block. */
    private static String stripComments(String java) {
        return java.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
    }
}
