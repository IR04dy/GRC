package com.tss.grc.module.tprm;

import com.tss.grc.module.tprm.bcp_attestation.controller.VendorBcpAttestationController;
import com.tss.grc.module.tprm.bcp_attestation.controller.VendorBcpCampaignController;
import com.tss.grc.module.tprm.cyber.controller.ThirdPartyCyberAssessmentController;
import com.tss.grc.module.tprm.outsourcing.controller.OutsourcingController;
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
 * F2-05 slice 2 — the TPRM module authorises on the {@code TPRM} permission, not on role names.
 *
 * <h3>What was wrong</h3>
 * <p>All <b>39</b> pure role guards in this module named {@code TPRM_LEAD}, which does not exist in
 * {@code iam.roles}. Six other ghosts appeared beside it: {@code PROCUREMENT} (the real role is
 * {@code PROCUREMENT_OWNER}), {@code OPS_MANAGER}, {@code INCIDENT_COMMANDER},
 * {@code VENDOR_CONTACT}, {@code BOARD_MEMBER}, {@code AUDIT_COMMITTEE_MEMBER}. A role guard never
 * reaches {@code ModulePermissionEvaluator}, so it gets no PLATFORM_ADMIN bypass either — the
 * population that passed was exactly the surviving real names, and where {@code TPRM_LEAD} was the
 * only non-admin leg that left <b>PLATFORM_ADMIN alone on 8 endpoints</b>: the whole vendor-BCP
 * campaign lifecycle (open / close / cancel / send-reminders), outsourcing delete, and monitoring
 * event record/resolve.
 *
 * <p>Live before the change, {@code rmanager} (RISK_MANAGER) got <b>403</b> opening a campaign it had
 * just been allowed to create.
 *
 * <h3>The two judgement calls, pinned here because they are not obvious</h3>
 * <ol>
 *   <li><b>{@code #approve}/{@code #retire} became {@code TPRM:APPROVE}, a permission V327 adds to
 *       the catalogue.</b> Those two guards named CISO, RISK_MANAGER and PLATFORM_ADMIN — all real,
 *       so they were <em>not</em> defective. Folding them into {@code TPRM:WRITE} would have widened
 *       an approval gate to all 8 writers including SUPPLIER_MANAGER. Guarding on
 *       {@code TPRM:APPROVE} <em>without</em> cataloguing it would have been F2-23: a permanently
 *       closed endpoint. V327 grants it to exactly CISO and RISK_MANAGER, and asserts that set is
 *       exact — so this conversion is behaviour-identical for the approval tier.</li>
 *   <li><b>{@code #submit} is the internal manual submit, not the vendor's path.</b> The
 *       {@code VENDOR_CONTACT} ghost made it look as though suppliers had been locked out of their
 *       own attestation. They had not: {@code POST /bcp-attestations/by-token/{token}/submit} is
 *       {@code permitAll()} with an opaque per-attestation UUID and is deliberately excluded from
 *       this conversion — asserted below, because a future sweep that "consistently" moved it onto
 *       {@code TPRM:WRITE} would break every vendor portal link in production.</li>
 * </ol>
 *
 * @see com.tss.grc.security.RoleGuardConversionRatchetTest the census and the platform-wide ratchet
 */
class TprmPermissionConversionTest {

    private static final String READ = "hasPermission('TPRM','READ')";
    private static final String WRITE = "hasPermission('TPRM','WRITE')";
    private static final String DELETE = "hasPermission('TPRM','DELETE')";
    private static final String APPROVE = "hasPermission('TPRM','APPROVE')";
    private static final String PERMIT_ALL = "permitAll()";

    private static final List<Class<?>> CONTROLLERS = List.of(
        OutsourcingController.class,
        VendorBcpAttestationController.class,
        VendorBcpCampaignController.class,
        ThirdPartyCyberAssessmentController.class);

    /** {@code Controller#method} → expected guard, for every mapped endpoint in the four classes. */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        // ── OutsourcingController — the SAMA outsourcing register ───────────────────────────────
        for (String m : List.of("list", "get", "materialityAssessment", "samaPending",
                                "overdueReviews", "dashboard", "dueDiligenceFor", "eventsFor",
                                "openEvents", "exitPlanFor", "overdueExitTests")) {
            EXPECTED.put("OutsourcingController#" + m, READ);
        }
        for (String m : List.of("create", "update", "transition", "recordReview",
                                "recordDueDiligence", "recordEvent", "resolveEvent",
                                "createExitPlan", "updateExitPlan", "recordExitTest")) {
            EXPECTED.put("OutsourcingController#" + m, WRITE);
        }
        EXPECTED.put("OutsourcingController#delete", DELETE);

        // ── VendorBcpAttestationController ──────────────────────────────────────────────────────
        EXPECTED.put("VendorBcpAttestationController#list", READ);
        EXPECTED.put("VendorBcpAttestationController#get", READ);
        EXPECTED.put("VendorBcpAttestationController#submit", WRITE);
        EXPECTED.put("VendorBcpAttestationController#accept", WRITE);
        EXPECTED.put("VendorBcpAttestationController#reject", WRITE);
        EXPECTED.put("VendorBcpAttestationController#markOutOfScope", WRITE);
        // the vendor's own path — token-authenticated, deliberately NOT converted
        EXPECTED.put("VendorBcpAttestationController#getByToken", PERMIT_ALL);
        EXPECTED.put("VendorBcpAttestationController#submitByToken", PERMIT_ALL);

        // ── VendorBcpCampaignController ─────────────────────────────────────────────────────────
        EXPECTED.put("VendorBcpCampaignController#list", READ);
        EXPECTED.put("VendorBcpCampaignController#get", READ);
        EXPECTED.put("VendorBcpCampaignController#stats", READ);
        for (String m : List.of("create", "open", "sendReminders", "escalate", "close", "cancel")) {
            EXPECTED.put("VendorBcpCampaignController#" + m, WRITE);
        }

        // ── ThirdPartyCyberAssessmentController — the module's pre-existing shape, plus the tier ─
        //    Five of these seven already used hasPermission before F2-05 touched anything; they are
        //    pinned because the inventory assertion demanded it, and that is the point of having one:
        //    a controller half-converted years ago is exactly where a "consistency" edit lands.
        EXPECTED.put("ThirdPartyCyberAssessmentController#list", READ);
        EXPECTED.put("ThirdPartyCyberAssessmentController#get", READ);
        EXPECTED.put("ThirdPartyCyberAssessmentController#create", WRITE);
        EXPECTED.put("ThirdPartyCyberAssessmentController#recordPosture", WRITE);
        EXPECTED.put("ThirdPartyCyberAssessmentController#submit", WRITE);
        EXPECTED.put("ThirdPartyCyberAssessmentController#approve", APPROVE);
        EXPECTED.put("ThirdPartyCyberAssessmentController#retire", APPROVE);
    }

    private static Map<String, String> actualGuards() {
        Map<String, String> found = new TreeMap<>();
        for (Class<?> c : CONTROLLERS) {
            for (Method m : c.getDeclaredMethods()) {
                boolean mapped = m.isAnnotationPresent(GetMapping.class)
                    || m.isAnnotationPresent(PostMapping.class)
                    || m.isAnnotationPresent(PutMapping.class)
                    || m.isAnnotationPresent(PatchMapping.class)
                    || m.isAnnotationPresent(DeleteMapping.class);
                if (!mapped) continue;
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                found.put(c.getSimpleName() + "#" + m.getName(),
                          pa == null ? "<NO GUARD>" : pa.value());
            }
        }
        return found;
    }

    @Test
    void everyEndpointCarriesTheExpectedTprmPermission() {
        Map<String, String> actual = actualGuards();
        EXPECTED.forEach((site, guard) ->
            assertThat(actual)
                .as("%s must be guarded by %s", site, guard)
                .containsEntry(site, guard));
    }

    @Test
    void noEndpointAuthorisesByRoleName() {
        actualGuards().forEach((site, guard) ->
            assertThat(guard)
                .as("%s must not authorise by role name — all 39 guards in this module named "
                  + "TPRM_LEAD, which does not exist in iam.roles, and a role guard gets no "
                  + "PLATFORM_ADMIN bypass, so 8 endpoints were administrator-only by attrition",
                    site)
                .doesNotContain("hasAnyRole")
                .doesNotContain("hasRole")
                .doesNotContain("hasAuthority"));
    }

    /**
     * A write landing on {@code TPRM:READ} would hand the SAMA outsourcing register to all 17
     * readers, VIEWER and SUPPLIER_USER included. This is not hypothetical: the first pass of the
     * conversion script attributed the HTTP verb from the wrong annotation and put
     * {@code #create}, {@code #createExitPlan}, {@code #submit} and the campaign {@code #create} on
     * READ. Caught by reading the output; asserted here so it cannot come back quietly.
     */
    @Test
    void noWriteApproveOrDeleteRunsOnTheReadPermission() {
        Map<String, String> actual = actualGuards();
        EXPECTED.forEach((site, expected) -> {
            if (READ.equals(expected) || PERMIT_ALL.equals(expected)) return;
            assertThat(actual.get(site))
                .as("%s changes state; TPRM:READ is held by 17 roles including VIEWER and "
                  + "SUPPLIER_USER", site)
                .isNotNull()
                .isNotEqualTo(READ);
        });
    }

    /**
     * The approval tier must stay narrower than the write tier. {@code TPRM:APPROVE} is held by
     * exactly CISO and RISK_MANAGER (asserted in V327); {@code TPRM:WRITE} by 8 roles including
     * SUPPLIER_MANAGER. Collapsing the two is the single most likely "tidy-up" regression here,
     * because every other state-changing endpoint in the module is WRITE.
     */
    @Test
    void theAssessmentApprovalTierIsNotTheWriteTier() {
        Map<String, String> actual = actualGuards();
        assertThat(actual.get("ThirdPartyCyberAssessmentController#approve")).isEqualTo(APPROVE);
        assertThat(actual.get("ThirdPartyCyberAssessmentController#retire")).isEqualTo(APPROVE);
        assertThat(APPROVE).isNotEqualTo(WRITE);
    }

    /**
     * The vendor portal authenticates by opaque per-attestation UUID, not by role or permission.
     * Moving these two onto a permission "for consistency" would break every live vendor link, and
     * they are the only {@code permitAll()} endpoints in the module — so they are pinned explicitly
     * rather than left to the inventory.
     */
    @Test
    void theTokenAuthenticatedVendorPathIsNotConverted() {
        Map<String, String> actual = actualGuards();
        assertThat(actual.get("VendorBcpAttestationController#getByToken")).isEqualTo(PERMIT_ALL);
        assertThat(actual.get("VendorBcpAttestationController#submitByToken")).isEqualTo(PERMIT_ALL);
    }

    @Test
    void noEndpointEscapesTheInventory() {
        Map<String, String> actual = actualGuards();
        assertThat(actual.keySet())
            .as("TPRM endpoints not pinned here — add them to EXPECTED with a deliberate "
              + "permission. TPRM has READ/WRITE/APPROVE/DELETE/EXPORT catalogued; APPROVE was "
              + "added by V327 and is the only one narrower than WRITE")
            .containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
        // Anti-vacuity: 46 mapped endpoints across the four controllers — 39 converted here, 2
        // token-authenticated vendor paths left alone, and 5 that already used hasPermission.
        assertThat(actual).hasSize(46);
        assertThat(actual.values()).doesNotContain("<NO GUARD>");
    }
}
