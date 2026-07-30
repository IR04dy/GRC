package com.tss.grc.module.fraud.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-05 slice 3 — the fraud case lifecycle authorises on {@code FRAUD}, not on two roles nobody has.
 *
 * <h3>What was wrong</h3>
 * <p>{@code FraudController}'s ten guards lived in two constants:
 * <pre>
 *   READ_GUARD  = hasAnyRole('PLATFORM_ADMIN','FRAUD_OFFICER','FRAUD_INVESTIGATOR',
 *                            'COMPLIANCE_OFFICER','INTERNAL_AUDITOR','RISK_MANAGER')
 *   WRITE_GUARD = hasAnyRole('PLATFORM_ADMIN','FRAUD_OFFICER','FRAUD_INVESTIGATOR')
 * </pre>
 * <p>{@code FRAUD_OFFICER} and {@code FRAUD_INVESTIGATOR} are absent from {@code iam.roles}, and a
 * role guard never reaches {@code ModulePermissionEvaluator} so it gets no PLATFORM_ADMIN bypass
 * either — the population that passed was exactly the surviving real names. On the write guard those
 * two ghosts were the <b>only</b> non-admin legs, so opening, triaging, updating, resolving and
 * closing a fraud case was <b>PLATFORM_ADMIN-only</b>. Live before the change, {@code compliance}
 * (COMPLIANCE_OFFICER + GRC_ADMINISTRATOR, 86 of 96 permissions) got <b>403</b> opening a case.
 *
 * <h3>Why this module needed a test the other two slices did not</h3>
 * <p>The guards are <b>constants</b>, not ten literals. That cuts both ways:
 * <ul>
 *   <li>It is why reflection found this at all — F2-27 recorded a source regex missing 8
 *       constant-valued guards, and these were two more of them.</li>
 *   <li>It makes the cheap assertion worthless. "The constant equals
 *       {@code hasPermission('FRAUD','READ')}" would still pass if someone re-pointed
 *       {@code #close} at {@code READ_GUARD}, because the constant is untouched and there is no
 *       per-endpoint literal to notice. So the inventory below pins each endpoint to its tier by
 *       name, and {@link #noStateChangingEndpointRunsOnTheReadGuard()} pins the direction.</li>
 * </ul>
 *
 * <h3>What is deliberately NOT here</h3>
 * <p>No test asserts that any particular persona investigates fraud. V328 catalogues
 * {@code FRAUD:READ}/{@code FRAUD:WRITE} and grants write to the two tenant administrators only;
 * {@code FRAUD_OFFICER} and {@code FRAUD_INVESTIGATOR} are not created and no existing role is
 * anointed. That question is open for the customer, and it is now a checkbox in the role editor
 * rather than a Java edit and a jar.
 *
 * @see com.tss.grc.security.RoleGuardConversionRatchetTest the census and the platform-wide ratchet
 * @see com.tss.grc.module.attachment.service.AttachmentAccessPolicy FRAUD_CASE moved with it
 */
class FraudControllerGuardTest {

    private static final String READ = "hasPermission('FRAUD','READ')";
    private static final String WRITE = "hasPermission('FRAUD','WRITE')";

    /** {@code method} → expected guard, for every mapped endpoint on the controller. */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        for (String m : List.of("listTypologies", "listCases", "getCase", "listUpdates",
                                "dashboard")) {
            EXPECTED.put(m, READ);
        }
        // The whole case lifecycle — every one of these was PLATFORM_ADMIN-only before the fix.
        for (String m : List.of("createCase", "triage", "recordUpdate", "setOutcome", "close")) {
            EXPECTED.put(m, WRITE);
        }
    }

    private static Map<String, String> actualGuards() {
        Map<String, String> found = new TreeMap<>();
        for (Method m : FraudController.class.getDeclaredMethods()) {
            boolean mapped = m.isAnnotationPresent(GetMapping.class)
                || m.isAnnotationPresent(PostMapping.class)
                || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(PatchMapping.class)
                || m.isAnnotationPresent(DeleteMapping.class);
            if (!mapped) continue;
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            found.put(m.getName(), pa == null ? "<NO GUARD>" : pa.value());
        }
        return found;
    }

    @Test
    void everyEndpointCarriesTheExpectedFraudPermission() {
        Map<String, String> actual = actualGuards();
        EXPECTED.forEach((site, guard) ->
            assertThat(actual)
                .as("FraudController#%s must be guarded by %s", site, guard)
                .containsEntry(site, guard));
    }

    @Test
    void noEndpointAuthorisesByRoleName() {
        actualGuards().forEach((site, guard) ->
            assertThat(guard)
                .as("FraudController#%s must not authorise by role name — the previous guards named "
                  + "FRAUD_OFFICER and FRAUD_INVESTIGATOR, neither of which exists in iam.roles, "
                  + "and a role guard gets no PLATFORM_ADMIN bypass, so the write tier was "
                  + "administrator-only by attrition", site)
                .doesNotContain("hasAnyRole")
                .doesNotContain("hasRole")
                .doesNotContain("hasAuthority"));
    }

    /**
     * The regression this module is most exposed to. Because both guards are constants, swapping
     * which constant an endpoint references is a one-word edit that no "the constant is a permission"
     * assertion would catch — and {@code FRAUD:READ} is held by 5 roles against WRITE's 2, so a
     * write landing on READ would hand case creation and closure to every reader.
     */
    @Test
    void noStateChangingEndpointRunsOnTheReadGuard() {
        Map<String, String> actual = actualGuards();
        EXPECTED.forEach((site, expected) -> {
            if (READ.equals(expected)) return;
            assertThat(actual.get(site))
                .as("FraudController#%s changes case state; FRAUD:READ is held by 5 roles "
                  + "including INTERNAL_AUDITOR, which must not be able to close a case", site)
                .isNotNull()
                .isNotEqualTo(READ);
        });
    }

    @Test
    void noEndpointEscapesTheInventory() {
        Map<String, String> actual = actualGuards();
        assertThat(actual.keySet())
            .as("fraud endpoints not pinned here — add them to EXPECTED with a deliberate tier. "
              + "FRAUD has only READ and WRITE catalogued (V328), so a guard naming any other "
              + "action would be permanently closed (F2-23)")
            .containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
        // Anti-vacuity: 10 mapped endpoints, 5 reads and 5 writes. If this controller grows and the
        // count is bumped without adding an EXPECTED entry, the assertion above fails first.
        assertThat(actual).hasSize(10);
        assertThat(actual.values()).doesNotContain("<NO GUARD>");
    }
}
