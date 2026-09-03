package com.tss.grc.module.integrations.crowdstrike.client;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties;
import com.tss.grc.module.integrations.crowdstrike.dto.ContainmentResponse;
import com.tss.grc.module.integrations.crowdstrike.dto.FalconDetectionDto;
import com.tss.grc.module.integrations.crowdstrike.dto.StubInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory Falcon simulator — the default in dev, demo, and unit tests.
 *
 * <p>Detection ids follow {@code ldt:fake-host-{n}:{seq}} where {@code n}
 * cycles through three synthetic hosts and {@code seq} is a per-org
 * monotonic counter. Tests can assert exact strings.
 *
 * <p>Severity distribution by sequence (mod 4) — CRITICAL / HIGH / MEDIUM /
 * LOW — so the inbound service's severity-mapping test gets one of each by
 * polling four times.
 *
 * <p>Each call is recorded in {@link #getRecordedCalls()} for inspection.
 */
@Component
@ConditionalOnProperty(
    name = "grc.integrations.crowdstrike.mode",
    havingValue = "STUB",
    matchIfMissing = true
)
public class StubCrowdStrikeClient implements CrowdStrikeClient {

    private static final Logger log = LoggerFactory.getLogger(StubCrowdStrikeClient.class);

    /** Three rotating synthetic hosts so the test fixtures have variety. */
    private static final String[] FAKE_HOSTS = {"fake-host-1", "fake-host-2", "fake-host-3"};

    /** Cycle by sequence so a 4-element poll produces one of each severity. */
    private static final int[] FAKE_SEVERITIES = {95, 75, 55, 25};

    private static final String[] FAKE_TACTICS = {
        "Initial Access", "Defense Evasion", "Credential Access", "Lateral Movement"
    };

    private static final String[] FAKE_TECHNIQUES = {
        "Valid Accounts", "Obfuscated Files", "Brute Force", "Remote Services"
    };

    private static final String[] FAKE_MITRE_IDS = {
        "T1078", "T1027", "T1110", "T1021"
    };

    private final CrowdStrikeProperties props;
    private final Map<Long, AtomicLong> sequencesByOrg = new ConcurrentHashMap<>();
    private final List<StubInvocation> recordedCalls = new ArrayList<>();

    /** Detection ids already returned — second poll won't re-emit them. */
    private final Map<Long, java.util.Set<String>> emittedByOrg = new ConcurrentHashMap<>();

    public StubCrowdStrikeClient(CrowdStrikeProperties props) {
        this.props = props;
    }

    @Override
    public synchronized List<FalconDetectionDto> pollDetections() {
        Long orgId = TenantContext.getCurrentTenant();
        long orgKey = orgId == null ? 0L : orgId;
        AtomicLong seq = sequencesByOrg.computeIfAbsent(orgKey, k -> new AtomicLong(0L));
        java.util.Set<String> emitted = emittedByOrg.computeIfAbsent(orgKey, k -> new java.util.LinkedHashSet<>());

        // Emit a 4-item batch — one detection per severity bucket so the
        // upstream service's severity-mapping tests are deterministic.
        List<FalconDetectionDto> batch = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            long s = seq.incrementAndGet();
            String hostId = "aid-" + FAKE_HOSTS[(int) ((s - 1) % FAKE_HOSTS.length)];
            int sev = FAKE_SEVERITIES[(int) ((s - 1) % FAKE_SEVERITIES.length)];
            String detectionId = String.format(Locale.ROOT, "ldt:%s:%d", hostId, s);
            if (emitted.contains(detectionId)) {
                continue; // stub-side dedupe — never re-emit
            }
            emitted.add(detectionId);

            FalconDetectionDto dto = new FalconDetectionDto(
                detectionId,
                hostId,
                "host-" + (((s - 1) % FAKE_HOSTS.length) + 1) + ".tss.local",
                sev,
                FAKE_TACTICS[(int) ((s - 1) % FAKE_TACTICS.length)],
                FAKE_TECHNIQUES[(int) ((s - 1) % FAKE_TECHNIQUES.length)],
                FAKE_MITRE_IDS[(int) ((s - 1) % FAKE_MITRE_IDS.length)],
                Instant.now().toString(),
                "Synthetic Falcon detection #" + s + " (severity " + sev + ")"
            );
            batch.add(dto);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchSize", batch.size());
        payload.put("orgId", orgKey);
        payload.put("detectionIds", batch.stream().map(FalconDetectionDto::detectionId).toList());
        recordedCalls.add(new StubInvocation(Instant.now(), "POLL", null, payload));

        log.info("[CrowdStrike-STUB] poll -> {} detection(s) for org {}", batch.size(), orgKey);
        return batch;
    }

    @Override
    public synchronized ContainmentResponse containHost(String falconHostId, String reasonText) {
        return recordAction("CONTAIN", falconHostId, reasonText);
    }

    @Override
    public synchronized ContainmentResponse liftContainment(String falconHostId) {
        return recordAction("LIFT", falconHostId, null);
    }

    @Override
    public synchronized ContainmentResponse hideHost(String falconHostId) {
        return recordAction("HIDE", falconHostId, null);
    }

    private ContainmentResponse recordAction(String method, String hostId, String reasonText) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hostId", hostId);
        if (reasonText != null) payload.put("reason", reasonText);
        recordedCalls.add(new StubInvocation(Instant.now(), method, hostId, payload));

        log.info("[CrowdStrike-STUB] {} host {}", method, hostId);
        String resourceId = String.format("falcon-action-%s-%s", method.toLowerCase(java.util.Locale.ROOT), hostId);
        String actionType = switch (method) {
            case "CONTAIN" -> "CONTAIN_HOST";
            case "LIFT" -> "LIFT_CONTAINMENT";
            case "HIDE" -> "HIDE_HOST";
            default -> method;
        };
        return new ContainmentResponse(actionType, hostId, true, 202, 1, resourceId);
    }

    @Override
    /**
     * F2-452 — {@code synchronized} because this method WRITES to {@code recordedCalls}, the
     * same shared {@code ArrayList} every other writer here guards with the monitor. It was
     * the only toucher without the modifier, and it is reachable in every profile: the
     * {@code /status} (or {@code /health}) route calls the client directly, guarded by
     * {@code INTEGRATIONS:READ} and NOT by {@code grc.integrations.*.enabled}, and this stub
     * is the default bean ({@code matchIfMissing = true}). Two concurrent status probes were
     * therefore enough for two unsynchronized {@code ArrayList.add} calls to lose an element,
     * corrupt {@code size}, or throw {@code ArrayIndexOutOfBoundsException}.
     *
     * <p>A health probe reads as a read, which is why three synchronized writers beside it
     * made it look covered (F2-215's two-of-N) — it records the probe, so it is a writer.
     */
    public synchronized boolean isHealthy() {
        recordedCalls.add(new StubInvocation(Instant.now(), "HEALTH", null, Map.of()));
        return true;
    }

    /** Snapshot of recorded calls — immutable copy. */
    public synchronized List<StubInvocation> getRecordedCalls() {
        return List.copyOf(recordedCalls);
    }

    /** Test-only — wipe state. */
    public synchronized void reset() {
        sequencesByOrg.clear();
        emittedByOrg.clear();
        recordedCalls.clear();
    }
}
