package com.tss.grc.module.integrations;

import com.tss.grc.module.integrations.crowdstrike.controller.CrowdStrikeController;
import com.tss.grc.module.integrations.servicenow.webhook.ServiceNowWebhookController;
import com.tss.grc.module.integrations.splunk.webhook.SplunkWebhookController;
import com.tss.grc.module.integrations.tenable.controller.TenableController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-05 slice 4 — the inbound integration path admits the machine accounts the platform seeds for it.
 *
 * <h3>What was wrong</h3>
 * <p>Four endpoints in {@code module/integrations} named {@code SOC_AUTOMATION} and
 * {@code INTEGRATION_SERVICE}. <b>Neither exists in {@code iam.roles}</b>, and a role guard never
 * reaches {@code ModulePermissionEvaluator} so it gets no PLATFORM_ADMIN bypass either — the
 * population that passed was exactly the surviving real names, which on all four was
 * {@code PLATFORM_ADMIN} alone:
 * <ul>
 *   <li>{@code SplunkWebhookController#alertReceived} — a Splunk saved search posting an alert</li>
 *   <li>{@code ServiceNowWebhookController#incidentUpdated} — SNow pushing an incident state change</li>
 *   <li>{@code CrowdStrikeController#manualPoll} — pull Falcon detections now</li>
 *   <li>{@code TenableController#manualPoll} — pull Tenable findings now</li>
 * </ul>
 *
 * <p><b>The sharp end is not the 403 — it is what the documentation told the customer to do about it.</b>
 * {@code docs/integrations/SPLUNK.md} and {@code SERVICENOW.md} instructed the operator to
 * "provision a dedicated integration service account and grant it one of those roles". With two of
 * the three unable to exist, the only grantable name left was {@code PLATFORM_ADMIN} — so following
 * the shipped instructions meant <b>handing an external SIEM or ITSM a platform-administrator
 * token</b>. Both pages are corrected in the same commit as this test.
 *
 * <p>Meanwhile the platform seeds <b>eight {@code *_INGEST} machine roles</b> — {@code SIEM_INGEST},
 * {@code SCANNER_INGEST}, {@code TI_INGEST}, {@code APPSEC_INGEST}, {@code DLP_INGEST},
 * {@code JML_INGEST}, {@code SBOM_INGEST}, {@code BRAND_INGEST} — every one of which holds
 * {@code CYBER:WRITE}, and nine {@code socops} endpoints already guard ingestion that way
 * ({@code SocOpsController#ingestSecurityEvent} is {@code hasAnyRole('SIEM_INGEST',…)}). So
 * {@code SIEM_INGEST}, the account provisioned for SIEM ingestion, was refused by the SIEM webhook
 * while a sibling endpoint writing <em>the same {@code socops.security_event} table</em> accepted it.
 * <b>An inconsistency, not a missing control</b> — the same shape as F2-13b and CM-C2.
 *
 * <h3>Why {@code CYBER:WRITE}, and why no migration</h3>
 * <p>Measured: 18 holders — the 8 machine roles, the cyber humans (CISO, CYBER_MANAGER,
 * SECURITY_ANALYST, IR_LEAD, VULN_MANAGER, APPSEC_LEAD, OT_LEAD, EMERGENCY_RESPONDER), ORG_ADMIN and
 * PLATFORM_ADMIN — and <b>no</b> VIEWER, REGULATOR_VIEWER, EXTERNAL_AUDITOR or SUPPLIER_USER. The
 * grant is already correct, so unlike V326/V327/V328 this slice needs no migration at all.
 *
 * <h3>⚠ Why only four of this package's twenty-four role guards moved — SUPERSEDED 2026-08-01</h3>
 * <p>This section used to end "naming the owner of integration administration is a product decision,
 * recorded in the plan, not guessed here", and it was right about the four but wrong to stop. That
 * decision was taken as <b>ADR-017 D-8</b>, {@code INTEGRATIONS:READ}/{@code WRITE} were catalogued by
 * <b>V343</b>, and <b>15 of the remaining 20 sites</b> moved onto them. The claim it rested on —
 * "the other twenty are <em>not defective</em>: they name PLATFORM_ADMIN, CISO and RISK_MANAGER, which
 * all exist" — <b>was itself the F2-27 shape</b>: those three do exist, and <em>six other names in the
 * same twenty guards do not</em> ({@code SOC_ANALYST}, {@code SOC_TIER_2}, {@code SOC_AUTOMATION},
 * {@code INTEGRATION_SERVICE}, {@code INCIDENT_COMMANDER}, {@code BOARD_MEMBER}), which is precisely
 * why the effective population had collapsed to two personas. Checking the names a guard needs is not
 * the same as checking the names a guard has.
 *
 * <p>What survives from the original reasoning, and is now pinned rather than argued: the five
 * <em>outbound action</em> endpoints stay role-guarded, because {@code CYBER:WRITE} includes eight
 * machine feeds and {@code INTEGRATIONS:WRITE} ({@code ORG_ADMIN}, {@code CISO}) would revoke
 * severity recast and risk acceptance from {@code RISK_MANAGER}, whose function that is. Their ghost
 * legs were stripped without converting them — provably access-preserving, since a name absent from
 * {@code iam.roles} can never satisfy {@code hasAnyRole}.
 *
 * @see com.tss.grc.security.RoleGuardConversionRatchetTest the census and the platform-wide ratchet
 * @see com.tss.grc.security.IntegrationsAuthorizationTest the A17-D8 partition and the ghost ratchet
 */
class IntegrationsIngestGuardTest {

    private static final String CYBER_WRITE = "hasPermission('CYBER','WRITE')";

    /** The four sites this slice converts. */
    private static final Map<String, String> CONVERTED = Map.of(
        "SplunkWebhookController#alertReceived", CYBER_WRITE,
        "ServiceNowWebhookController#incidentUpdated", CYBER_WRITE,
        "CrowdStrikeController#manualPoll", CYBER_WRITE,
        "TenableController#manualPoll", CYBER_WRITE);

    private static final List<Class<?>> CONTROLLERS = List.of(
        SplunkWebhookController.class,
        ServiceNowWebhookController.class,
        CrowdStrikeController.class,
        TenableController.class);

    private static Map<String, String> guards() {
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
    void theFourIngestEndpointsAuthoriseOnCyberWrite() {
        Map<String, String> actual = guards();
        CONVERTED.forEach((site, guard) ->
            assertThat(actual)
                .as("%s must be guarded by %s — it was PLATFORM_ADMIN-only by attrition", site, guard)
                .containsEntry(site, guard));
    }

    /**
     * The whole point of the slice: on the four endpoints it converts, the two names that cannot be
     * granted must not come back. A "restore the previous guard" revert would reintroduce a
     * documented instruction to give an external system admin credentials.
     *
     * <p>⚠ Scoped to the converted four on purpose. The first version of this test asserted it
     * package-wide and <b>failed</b> — {@code CrowdStrikeController#status},
     * {@code TenableController#status} and {@code SplunkController#pushSecurityEvent} still recite
     * {@code SOC_AUTOMATION} among legs that include the real {@code CISO}, so they are read
     * endpoints that work, not admin-only ones. Those dead legs are tracked in
     * {@code ROLE_GUARD_CENSUS.md} §A and cannot be removed here without either widening the
     * endpoint or making a product decision this slice is not taking. Asserting the broad claim
     * would have been a test asserting work that was not done.
     */
    @Test
    void theConvertedEndpointsNameNoUngrantableServiceAccountRole() {
        Map<String, String> actual = guards();
        CONVERTED.keySet().forEach(site -> {
            assertThat(actual.get(site))
                .as("%s names SOC_AUTOMATION, which is absent from iam.roles — it cannot be granted, "
                  + "so naming it silently reduces the guard to its other legs", site)
                .doesNotContain("SOC_AUTOMATION");
            assertThat(actual.get(site))
                .as("%s names INTEGRATION_SERVICE, which is absent from iam.roles", site)
                .doesNotContain("INTEGRATION_SERVICE");
            assertThat(actual.get(site))
                .as("%s must authorise on a permission, not an identity", site)
                .doesNotContain("hasAnyRole").doesNotContain("hasRole");
        });
    }

    /**
     * A machine account must not gain an active-response or risk-acceptance power by being swept up in
     * a "make the module consistent" edit. {@code CYBER:WRITE} includes eight ingest accounts; host
     * containment and vulnerability risk acceptance are deliberately NOT on it.
     */
    @Test
    void activeResponseAndRiskAcceptanceAreNotOnTheIngestTier() {
        Map<String, String> actual = guards();
        for (String site : List.of("CrowdStrikeController#contain", "CrowdStrikeController#lift",
                                   "TenableController#acceptRisk", "TenableController#recast",
                                   "TenableController#removeRecast")) {
            assertThat(actual.get(site))
                .as("%s must stay narrower than the ingest tier — CYBER:WRITE is held by 8 machine "
                  + "accounts, and a SIEM feed must not be able to quarantine a host or accept a "
                  + "vulnerability risk", site)
                .isNotNull()
                .isNotEqualTo(CYBER_WRITE);
        }
    }

    @Test
    void everyEndpointInTheseControllersStillCarriesAGuard() {
        Map<String, String> actual = guards();
        assertThat(actual.values())
            .as("an unguarded endpoint here would be reachable by any authenticated caller")
            .doesNotContain("<NO GUARD>");
        // Anti-vacuity: a scan resolving nothing passes every assertion above. 19 mapped endpoints
        // across the four classes (the package has 24 role-guard sites in total; the other 5 live in
        // SplunkController and ServiceNowController, which this slice does not touch).
        assertThat(actual).hasSizeGreaterThanOrEqualTo(15);
        assertThat(actual).containsKeys("CrowdStrikeController#contain", "TenableController#acceptRisk");
    }
}
