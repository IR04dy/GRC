package com.tss.grc.module.sharia;

import com.tss.grc.module.sharia.controller.ShariaController;
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
 * F2-145(e) / F2-05 — the first module converted from {@code hasAnyRole} to {@code hasPermission}.
 *
 * <p>All 21 endpoints authorised by role name. Two of the names did not exist in {@code iam.roles}:
 * {@code CRO} on read and approve, {@code SHARIA_BOARD_CHAIR} on approve — permanently false legs, so
 * the module was narrower than it read (F2-52's decoration shape). {@code hasAnyRole} also never
 * reaches {@code ModulePermissionEvaluator}, which means it never reaches the PLATFORM_ADMIN bypass
 * either, so every name had to be recited including the administrator's.
 *
 * <h3>The conversion was population-preserving, and that was measured before it was made</h3>
 * V362 catalogued the {@code SHARIA} permissions <em>from these very guards</em>, so per action the
 * granted roles and the guard's surviving names were already the same set. Measured against dev:
 *
 * <pre>
 *   READ     guard named 7 (1 ghost: CRO)                    LOST none  GAINED none
 *   WRITE    guard named 4 (0 ghosts)                        LOST none  GAINED none
 *   APPROVE  guard named 5 (2 ghosts: CRO, SHARIA_BOARD_CHAIR) LOST none  GAINED none
 * </pre>
 *
 * ADR-010's amendment asks that a conversion grant the <b>intended</b> population rather than the
 * incidental one; here the two coincide, which is the only reason this could ship as one slice.
 *
 * <h3>What the conversion switched ON</h3>
 * V367 revoked {@code SHARIA:APPROVE} from {@code SHARIA_AUDITOR} so the audit function cannot approve
 * what it audits. That migration was <b>inert</b> while these guards recited role names — its own header
 * says so. From the converting commit it decides requests: {@code SHARIA_AUDITOR} holds WRITE and not
 * APPROVE, so {@code transitionProduct} and {@code purifyNcEvent} now 403 for it while
 * {@code raiseFinding} does not. Live-probed with two purpose-seated users, since the role is held by
 * nobody in dev.
 *
 * <h3>Why this pins tiers rather than the absence of a shape</h3>
 * "No guard in this class calls {@code hasAnyRole}" passes the moment the names are swapped and keeps
 * passing if {@code purifyNcEvent} is later re-pointed at the write constant — the failure mode
 * {@code FraudControllerGuardTest} and {@code HelpControllerGuardTest} were both rewritten to close.
 * The tier map below is exhaustive, so a new endpoint that is not listed fails by name.
 *
 * <p>⚠ The three tiers are <b>not uniform by HTTP verb</b> and a later tidying sweep would break them:
 * {@code createScholar} is APPROVE because appointing a scholar to the Sharia board is a board act,
 * while {@code updateScholar} is WRITE; {@code transitionFinding} is WRITE because an auditor advances
 * its own finding, while the other three transitions are APPROVE. Those asymmetries predate the
 * conversion — it preserved each site's tier exactly — and they are asserted here so the sweep has to
 * argue with a failing test.
 */
class ShariaControllerGuardTest {

    private static final String READ    = "hasPermission('SHARIA','READ')";
    private static final String WRITE   = "hasPermission('SHARIA','WRITE')";
    private static final String APPROVE = "hasPermission('SHARIA','APPROVE')";

    private static final Path SOURCE = Path.of(
            "src/main/java/com/tss/grc/module/sharia/controller/ShariaController.java");

    /**
     * Every endpoint and the tier it belongs to. Exhaustive on purpose — an unlisted endpoint is an
     * unreviewed guard, and {@link #everyEndpointIsPinnedToATier()} fails for it.
     */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>() {{
        // Scholars. Appointing one is a board act; editing the record is not.
        put("createScholar",        APPROVE);
        put("listScholars",         READ);
        put("getScholar",           READ);
        put("updateScholar",        WRITE);
        put("declareCoi",           WRITE);
        // Resolutions — issuing a Sharia resolution is the board's, drafting is the officer's.
        put("createResolution",     WRITE);
        put("listResolutions",      READ);
        put("getResolution",        READ);
        put("transitionResolution", APPROVE);
        put("listResolutionEvents", READ);
        // Audit findings. The auditor raises AND advances its own finding: WRITE, deliberately.
        put("raiseFinding",         WRITE);
        put("listFindings",         READ);
        put("transitionFinding",    WRITE);
        // Non-compliance events. Purification disposes of tainted income — an approval.
        put("detectNcEvent",        WRITE);
        put("listNcEvents",         READ);
        put("transitionNcEvent",    WRITE);
        put("purifyNcEvent",        APPROVE);
        // Products. Certifying a product as Sharia-compliant is an approval.
        put("submitProduct",        WRITE);
        put("listProducts",         READ);
        put("transitionProduct",    APPROVE);
        put("dashboard",            READ);
    }};

    @Test
    @DisplayName("every ShariaController endpoint is guarded, and by the tier it was reviewed at")
    void everyEndpointIsPinnedToATier() {
        Map<String, String> actual = new TreeMap<>();
        for (Method m : ShariaController.class.getDeclaredMethods()) {
            if (!m.getDeclaringClass().equals(ShariaController.class) || m.isSynthetic()) continue;
            PreAuthorize guard = m.getAnnotation(PreAuthorize.class);
            if (guard != null) actual.put(m.getName(), guard.value());
        }
        // Anti-vacuity: a reflection read that resolves nothing satisfies every containsExactly below.
        assertThat(actual)
                .as("ShariaController must expose the 21 guarded endpoints this test reviewed; a "
                    + "reflection read that finds none would pass the tier assertions vacuously")
                .hasSize(EXPECTED.size());
        assertThat(actual)
                .as("each endpoint must keep the tier it was reviewed at — the tiers are not uniform "
                    + "by verb, see this class's javadoc")
                .containsExactlyInAnyOrderEntriesOf(new TreeMap<>(EXPECTED));
    }

    @Test
    @DisplayName("no guard in ShariaController authorises by role name any more")
    void noGuardRecitesARoleName() throws IOException {
        String code = stripComments(Files.readString(SOURCE, StandardCharsets.UTF_8));

        // ⚠ Comment-stripping is load-bearing HERE and provably so, unlike the length-preserving
        // variant I once claimed was: this class's own javadoc and the controller's explain what the
        // guards used to do, so both name hasAnyRole in prose. An assertion over the raw text would
        // fail on the documentation of the fix.
        assertThat(code)
                .as("the conversion is the point of F2-145(e); a role-name guard here is a regression")
                .doesNotContain("hasAnyRole")
                .doesNotContain("hasRole(");

        // And the stripper must not be the reason it passes — the raw file DOES contain the string.
        String raw = Files.readString(SOURCE, StandardCharsets.UTF_8);
        assertThat(raw)
                .as("if the controller's javadoc no longer explains what it was converted FROM, this "
                    + "test's comment-stripping has become untested; re-point it or drop it")
                .contains("hasAnyRole");
    }

    @Test
    @DisplayName("the guards name only SHARIA, and only actions V362 catalogued")
    void theGuardsNameOnlyCataloguedPermissions() throws IOException {
        String code = stripComments(Files.readString(SOURCE, StandardCharsets.UTF_8));
        Matcher m = Pattern.compile("hasPermission\\('([A-Z_]+)'\\s*,\\s*'([A-Z_]+)'\\)").matcher(code);
        var pairs = new TreeSet<String>();
        while (m.find()) pairs.add(m.group(1) + ":" + m.group(2));

        assertThat(pairs)
                .as("a guard citing a module or action outside the catalogue can never be satisfied by "
                    + "a grant — it is a ghost with different spelling (F2-143)")
                .containsExactly("SHARIA:APPROVE", "SHARIA:READ", "SHARIA:WRITE");
    }

    /** Block then line, so a {@code //} inside a block comment cannot truncate the block. */
    private static String stripComments(String java) {
        return java.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
    }
}
