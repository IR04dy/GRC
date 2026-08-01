package com.tss.grc.security;

import com.tss.grc.module.integrations.crowdstrike.controller.CrowdStrikeController;
import com.tss.grc.module.integrations.servicenow.controller.ServiceNowController;
import com.tss.grc.module.integrations.servicenow.webhook.ServiceNowWebhookController;
import com.tss.grc.module.integrations.splunk.controller.SplunkController;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-017 <b>D-8</b> (read as <b>A17-D8</b>; §6's D-8 is the unrelated CAB-voting decision) —
 * {@code INTEGRATIONS} becomes a permission module, and the partition is pinned so it cannot drift
 * back.
 *
 * <h3>What was wrong, measured before anything was written</h3>
 * <p>The 20 role-name guards in {@code module/integrations} recited nine role names. <b>Six of them do
 * not exist in {@code iam.roles}</b> — {@code SOC_AUTOMATION}, {@code INTEGRATION_SERVICE},
 * {@code SOC_ANALYST}, {@code SOC_TIER_2}, {@code INCIDENT_COMMANDER}, {@code BOARD_MEMBER} — verified
 * absent on <em>both</em> databases (dev {@code TSS_GRC}, prod {@code TSS_GRC_Deployment}) on
 * 2026-08-01. {@code hasAnyRole} never reaches {@link ModulePermissionEvaluator}, so a role guard also
 * gets <b>no PLATFORM_ADMIN bypass</b>: a leg naming a role that is not there is permanently false, and
 * a guard made only of such legs admits nobody.
 *
 * <p>Live, as six users, before the conversion: every one of the 20 endpoints returned 403 to
 * {@code GRC_ADMINISTRATOR}, {@code CYBER_MANAGER} and {@code VIEWER}, and 200 only to
 * {@code PLATFORM_ADMIN} and {@code CISO} — except {@code splunk/test-push}, which was
 * {@code hasRole('PLATFORM_ADMIN')} and admitted the administrator alone, and the three Tenable
 * endpoints that also named the real {@code RISK_MANAGER}. <b>Accidental administrator-only-ness is not
 * a security model.</b> So this change is a <em>re-grant</em>, which by the rule ADR-017 D-10 adopted
 * must be probed and justified as one — see V343 for the per-grantee need-to-know, including the one
 * grant that is not an administrator ({@code CYBER_MANAGER}).
 *
 * <h3>Why the partition has three buckets and not one</h3>
 * <ul>
 *   <li><b>{@code INTEGRATIONS:READ}</b> (8) — adapter health and bound configuration with secrets
 *       masked. Granted to {@code ORG_ADMIN}, {@code GRC_ADMINISTRATOR}, {@code CISO},
 *       {@code CYBER_MANAGER}.</li>
 *   <li><b>{@code INTEGRATIONS:WRITE}</b> (7) — transmitting to a third party, plus the four
 *       {@code /stub/recorded-calls} diagnostics. Those are GETs on the WRITE tier <em>on purpose</em>:
 *       {@code StubInvocation} carries the frozen inbound <b>payload</b>, so they are the only
 *       read-shaped endpoints here that can return what actually left the platform, and they must not
 *       widen to the two assurance personas READ adds. Granted to {@code ORG_ADMIN}, {@code CISO}.</li>
 *   <li><b>{@code CYBER:WRITE}</b> (4) — the ingest path, unchanged from F2-05 slice 4; the eight
 *       seeded {@code *_INGEST} machine roles hold it.</li>
 *   <li><b>Role guards, deliberately</b> (5) — the outbound <em>action</em> endpoints. See
 *       {@link #theFiveOutboundActionsStayRoleGuardedAndTheReasonIsRecorded()}.</li>
 * </ul>
 *
 * <h3>Why reflection rather than a source grep</h3>
 * <p>The same reason this repository arrived at three times over: a line-oriented regex has
 * under-counted a security surface here repeatedly (F2-13a's own {@code hasAnyRole}-leg count was 3 by
 * grep and 73 by reflection, because a guard spanning two source lines is invisible to it). The
 * annotation value is assembled by the compiler, so reflection sees the whole expression however it
 * was formatted — and several guards in these six classes now carry trailing {@code //} comments on the
 * same line, which is exactly the shape that defeats a naive match.
 *
 * @see RoleGuardConversionRatchetTest the platform-wide census this slice drains by 15
 * @see com.tss.grc.module.integrations.IntegrationsIngestGuardTest F2-05 slice 4, the ingest half
 */
class IntegrationsAuthorizationTest {

    private static final String READ  = "hasPermission('INTEGRATIONS','READ')";
    private static final String WRITE = "hasPermission('INTEGRATIONS','WRITE')";
    private static final String CYBER_WRITE = "hasPermission('CYBER','WRITE')";

    private static final List<Class<?>> CONTROLLERS = List.of(
        SplunkController.class, SplunkWebhookController.class,
        ServiceNowController.class, ServiceNowWebhookController.class,
        CrowdStrikeController.class, TenableController.class);

    /** The observe tier: adapter health and masked configuration. */
    private static final Set<String> ON_READ = new TreeSet<>(List.of(
        "SplunkController#status", "SplunkController#config",
        "ServiceNowController#health", "ServiceNowController#config",
        "CrowdStrikeController#status", "CrowdStrikeController#config",
        "TenableController#status", "TenableController#config"));

    /** The administer tier: anything that transmits, plus anything that returns a frozen payload. */
    private static final Set<String> ON_WRITE = new TreeSet<>(List.of(
        "SplunkController#testPush", "SplunkController#pushSecurityEvent",
        "SplunkController#recordedCalls",
        "ServiceNowController#testPush", "ServiceNowController#recordedCalls",
        "CrowdStrikeController#recordedCalls", "TenableController#recordedCalls"));

    /** F2-05 slice 4, unchanged: ingest, held by the eight {@code *_INGEST} machine roles. */
    private static final Set<String> ON_CYBER_WRITE = new TreeSet<>(List.of(
        "SplunkWebhookController#alertReceived", "ServiceNowWebhookController#incidentUpdated",
        "CrowdStrikeController#manualPoll", "TenableController#manualPoll"));

    /** Outbound actions that stay role-guarded. Shrinking this set needs an ADR, not a refactor. */
    private static final Set<String> ROLE_GUARDED = new TreeSet<>(List.of(
        "CrowdStrikeController#contain", "CrowdStrikeController#lift",
        "TenableController#recast", "TenableController#acceptRisk",
        "TenableController#removeRecast"));

    /**
     * The <b>only</b> role names any guard in this package may recite, and all three exist in
     * {@code iam.roles} on both databases. This is the ghost ratchet: adding a fourth name fails here,
     * so the next person to reach for {@code hasAnyRole('SOC_LEAD',…)} has to establish that the role
     * exists before the suite will go green. That check is what nobody did nine times.
     */
    private static final Set<String> PERMITTED_ROLE_NAMES =
        new TreeSet<>(List.of("CISO", "PLATFORM_ADMIN", "RISK_MANAGER"));

    /** Named individually so a failure message says which ghost came back, not just "a ghost". */
    private static final List<String> RETIRED_GHOSTS = List.of(
        "SOC_AUTOMATION", "INTEGRATION_SERVICE", "SOC_ANALYST",
        "SOC_TIER_2", "INCIDENT_COMMANDER", "BOARD_MEMBER");

    private static final Pattern ROLE_CALL = Pattern.compile("has(?:AnyRole|Role)\\(([^)]*)\\)");
    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    // ── the partition ─────────────────────────────────────────────────────────────────────────────

    @Test
    void everyIntegrationEndpointFallsInExactlyOneOfTheFourBuckets() {
        Map<String, String> guards = guards();

        Set<String> onRead = new TreeSet<>(), onWrite = new TreeSet<>(),
                    onCyberWrite = new TreeSet<>(), roleGuarded = new TreeSet<>(),
                    unclassified = new TreeSet<>();
        guards.forEach((site, expr) -> {
            if (expr.startsWith(READ)) onRead.add(site);
            else if (expr.startsWith(WRITE)) onWrite.add(site);
            else if (expr.startsWith(CYBER_WRITE)) onCyberWrite.add(site);
            else if (ROLE_CALL.matcher(expr).find()) roleGuarded.add(site);
            else unclassified.add(site);
        });

        System.out.println("[A17-D8] INTEGRATIONS:READ  = " + onRead.size() + " " + onRead);
        System.out.println("[A17-D8] INTEGRATIONS:WRITE = " + onWrite.size() + " " + onWrite);
        System.out.println("[A17-D8] CYBER:WRITE        = " + onCyberWrite.size() + " " + onCyberWrite);
        System.out.println("[A17-D8] role-guarded       = " + roleGuarded.size() + " " + roleGuarded);

        assertThat(unclassified)
            .as("an integration endpoint authorises on something this test does not know about — "
              + "either it lost its guard, or a fifth bucket was invented without recording why")
            .isEmpty();
        assertThat(onRead)
            .as("INTEGRATIONS:READ is the observe tier: adapter health and masked configuration, "
              + "nothing that transmits and nothing that returns a payload")
            .containsExactlyInAnyOrderElementsOf(ON_READ);
        assertThat(onWrite)
            .as("INTEGRATIONS:WRITE is the administer tier: transmitting to a third party, and the "
              + "four /stub/recorded-calls endpoints, which return frozen request payloads")
            .containsExactlyInAnyOrderElementsOf(ON_WRITE);
        assertThat(onCyberWrite)
            .as("the ingest path stays on CYBER:WRITE — the eight *_INGEST machine roles hold it, "
              + "and moving it to INTEGRATIONS:WRITE would lock every machine feed out (F2-05 slice 4)")
            .containsExactlyInAnyOrderElementsOf(ON_CYBER_WRITE);
        assertThat(roleGuarded)
            .as("exactly five outbound-action endpoints stay role-guarded; both shrinking and growing "
              + "this set is a decision, not a tidy-up")
            .containsExactlyInAnyOrderElementsOf(ROLE_GUARDED);
    }

    /**
     * The ghost ratchet, and the assertion this class exists for. Every role name recited anywhere in
     * the package must be one of three that exist.
     *
     * <p>A guard cannot be reviewed by reading it: {@code hasAnyRole('SOC_TIER_2','CISO')} and
     * {@code hasAnyRole('BCM_MANAGER','CISO')} are the same expression to a reader and different
     * populations to the evaluator, and nothing in the build ever knew the difference. This test is
     * the cheapest place to know it.
     */
    @Test
    void noGuardHereRecitesARoleThatDoesNotExist() {
        Set<String> named = new TreeSet<>();
        for (String expr : guards().values()) {
            Matcher call = ROLE_CALL.matcher(expr);
            while (call.find()) {
                Matcher q = QUOTED.matcher(call.group(1));
                while (q.find()) named.add(q.group(1));
            }
        }
        System.out.println("[A17-D8] role names recited in module/integrations = " + named);

        for (String ghost : RETIRED_GHOSTS) {
            assertThat(named)
                .as("%s is absent from iam.roles on BOTH databases (verified 2026-08-01). A guard "
                  + "naming it has a permanently false leg, and if that is the only non-admin leg the "
                  + "endpoint is administrator-only by attrition — which is the whole of A17-D8. "
                  + "Seeding the role instead would make an absent control look present (§7).", ghost)
                .doesNotContain(ghost);
        }
        assertThat(named)
            .as("a guard in module/integrations names a role outside %s. Every name added here must "
              + "first be shown to exist in iam.roles — six of the nine that used to be here did not.",
                PERMITTED_ROLE_NAMES)
            .isSubsetOf(PERMITTED_ROLE_NAMES);
        // Anti-vacuity in the other direction: if the extraction silently found nothing, every
        // doesNotContain above passes. Five role guards remain and they name three roles between them.
        assertThat(named)
            .as("no role names were extracted at all — the five surviving role guards were either "
              + "converted without updating this test, or the matcher stopped matching")
            .isNotEmpty()
            .contains("CISO", "PLATFORM_ADMIN", "RISK_MANAGER");
    }

    /**
     * The five that stay, and why converting them would be worse than leaving them. Recorded as an
     * assertion because "we thought about it" does not survive the next sweep: the previous pass over
     * this package left the reasoning in a javadoc, and that javadoc went stale the moment the product
     * decision it was waiting for arrived.
     *
     * <p>{@code INTEGRATIONS:WRITE} is {@code {ORG_ADMIN, CISO\}}. Converting {@code recast} /
     * {@code acceptRisk} onto it would <b>revoke severity override and vulnerability risk-acceptance
     * from {@code RISK_MANAGER}</b> — the one role whose function that is — and hand host quarantine to
     * a tenant administrator with no security-operations claim. {@code CYBER:WRITE} is rejected by D-8
     * itself: it includes eight machine feeds, and a SIEM must not be able to isolate a host.
     * Vulnerability risk-acceptance is a two-population decision (cyber <em>and</em> risk) that no
     * catalogued permission expresses, and inventing one was out of scope.
     */
    @Test
    void theFiveOutboundActionsStayRoleGuardedAndTheReasonIsRecorded() {
        Map<String, String> guards = guards();
        for (String site : ROLE_GUARDED) {
            assertThat(guards.get(site))
                .as("%s must stay narrower than the ingest tier — CYBER:WRITE is held by eight "
                  + "machine accounts", site)
                .isNotNull()
                .isNotEqualTo(CYBER_WRITE)
                .doesNotContain("INTEGRATIONS");
        }
        // contain and lift must agree exactly: a lift narrower than the containment it reverses
        // strands the host, and a lift wider than it lets someone undo a decision they could not make.
        assertThat(guards.get("CrowdStrikeController#lift"))
            .as("lift and contain must name the same population — whoever may isolate a host must be "
              + "able to restore it, and only they")
            .isEqualTo(guards.get("CrowdStrikeController#contain"));
        // RISK_MANAGER is why these two were not converted; if it ever leaves them, the reason is gone.
        assertThat(guards.get("TenableController#recast")).contains("RISK_MANAGER");
        assertThat(guards.get("TenableController#acceptRisk")).contains("RISK_MANAGER");
    }

    @Test
    void everyMappedEndpointInThePackageStillCarriesAGuard() {
        Map<String, String> guards = guards();
        assertThat(guards.values())
            .as("an unguarded endpoint here is reachable by any authenticated caller, and this package "
              + "holds credential configuration and the record of what left the platform")
            .doesNotContain("<NO GUARD>");
        // Anti-vacuity floor: a reflection scan that resolves nothing passes every assertion above.
        // 24 mapped endpoints across the six classes as of 2026-08-01.
        assertThat(guards)
            .as("the reflection scan resolved %d endpoints — too few for six controllers, so the "
              + "assertions above are passing on an empty map", guards.size())
            .hasSizeGreaterThanOrEqualTo(20);
        assertThat(guards).containsKeys(
            "SplunkController#status", "TenableController#acceptRisk", "CrowdStrikeController#contain");
    }

    // ── extraction ────────────────────────────────────────────────────────────────────────────────

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
}
