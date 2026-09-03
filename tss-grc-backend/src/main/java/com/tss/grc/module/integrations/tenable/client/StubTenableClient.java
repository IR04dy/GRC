package com.tss.grc.module.integrations.tenable.client;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.tenable.config.TenableProperties;
import com.tss.grc.module.integrations.tenable.dto.RecastResponse;
import com.tss.grc.module.integrations.tenable.dto.StubInvocation;
import com.tss.grc.module.integrations.tenable.dto.TenableFindingDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory Tenable simulator — the default in dev, demo, and unit tests.
 *
 * <p>Finding ids are composite: {@code asset_uuid = "fake-asset-{n}"} and
 * {@code plugin_id = "TEN-FND-{seq}"} where {@code n} cycles through three
 * synthetic assets and {@code seq} is a per-org monotonic counter. Tests can
 * assert exact strings.
 *
 * <p>Severity distribution by sequence (mod 5) — CRITICAL / HIGH / MEDIUM /
 * LOW / INFO — so the inbound service's severity-mapping test gets one of
 * each NCA bucket by polling five times.
 *
 * <p>Each call is recorded in {@link #getRecordedCalls()} for inspection.
 */
@Component
@ConditionalOnProperty(
    name = "grc.integrations.tenable.mode",
    havingValue = "STUB",
    matchIfMissing = true
)
public class StubTenableClient implements TenableClient {

    private static final Logger log = LoggerFactory.getLogger(StubTenableClient.class);

    /** Three rotating synthetic assets so the test fixtures have variety. */
    private static final String[] FAKE_ASSETS = {"fake-asset-1", "fake-asset-2", "fake-asset-3"};

    /** Cycle by sequence so a 5-element poll produces one of each severity. */
    private static final String[] FAKE_SEVERITIES = {"CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"};

    /** CVSS scores aligned with the severity cycle so the mapping test is deterministic. */
    private static final BigDecimal[] FAKE_CVSS = {
        new BigDecimal("9.8"),
        new BigDecimal("7.5"),
        new BigDecimal("5.4"),
        new BigDecimal("3.1"),
        new BigDecimal("0.0")
    };

    private static final String[] FAKE_CVES = {
        "CVE-2024-12345", "CVE-2024-23456", "CVE-2023-34567", "CVE-2023-45678", "CVE-2022-56789"
    };

    private static final String[] FAKE_PLUGINS = {
        "Microsoft Windows SMBv1 Remote Code Execution",
        "OpenSSL Heartbleed Information Disclosure",
        "Apache Log4j Remote Code Execution",
        "TLS Version 1.0 Protocol Deprecated",
        "Banner Information Disclosure"
    };

    private final TenableProperties props;
    private final Map<Long, AtomicLong> sequencesByOrg = new ConcurrentHashMap<>();
    private final List<StubInvocation> recordedCalls = new ArrayList<>();

    /** Composite (asset_uuid, plugin_id) keys already returned — second poll won't re-emit. */
    private final Map<Long, Set<String>> emittedByOrg = new ConcurrentHashMap<>();

    public StubTenableClient(TenableProperties props) {
        this.props = props;
    }

    @Override
    public synchronized List<TenableFindingDto> pollFindings() {
        Long orgId = TenantContext.getCurrentTenant();
        long orgKey = orgId == null ? 0L : orgId;
        AtomicLong seq = sequencesByOrg.computeIfAbsent(orgKey, k -> new AtomicLong(0L));
        Set<String> emitted = emittedByOrg.computeIfAbsent(orgKey, k -> new LinkedHashSet<>());

        // Emit a 5-item batch — one finding per severity bucket so the
        // upstream service's severity-mapping tests are deterministic.
        List<TenableFindingDto> batch = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            long s = seq.incrementAndGet();
            int idx = (int) ((s - 1) % FAKE_SEVERITIES.length);
            String assetUuid = FAKE_ASSETS[(int) ((s - 1) % FAKE_ASSETS.length)];
            String pluginId = String.format(Locale.ROOT, "TEN-FND-%d", s);
            String key = assetUuid + "|" + pluginId;
            if (emitted.contains(key)) {
                continue; // stub-side dedupe — never re-emit
            }
            emitted.add(key);

            TenableFindingDto dto = new TenableFindingDto(
                assetUuid,
                pluginId,
                FAKE_PLUGINS[idx],
                "host-" + (((s - 1) % FAKE_ASSETS.length) + 1) + ".tss.local",
                FAKE_SEVERITIES[idx],
                FAKE_CVSS[idx],
                FAKE_CVES[idx],
                Instant.now().toString(),
                "Synthetic Tenable finding #" + s + " (" + FAKE_SEVERITIES[idx] + ")"
            );
            batch.add(dto);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchSize", batch.size());
        payload.put("orgId", orgKey);
        payload.put("pluginIds", batch.stream().map(TenableFindingDto::pluginId).toList());
        recordedCalls.add(new StubInvocation(Instant.now(), "POLL", null, payload));

        log.info("[Tenable-STUB] poll -> {} finding(s) for org {}", batch.size(), orgKey);
        return batch;
    }

    @Override
    public synchronized RecastResponse recastSeverity(String pluginId, String assetUuid, String newSeverity, String justification) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pluginId", pluginId);
        if (assetUuid != null) payload.put("assetUuid", assetUuid);
        payload.put("newSeverity", newSeverity);
        if (justification != null) payload.put("justification", justification);
        recordedCalls.add(new StubInvocation(Instant.now(), "RECAST", pluginId, payload));

        log.info("[Tenable-STUB] RECAST plugin={} asset={} severity={}", pluginId, assetUuid, newSeverity);
        return new RecastResponse(
            "RECAST_SEVERITY", pluginId, assetUuid, true, 200, 1,
            "stub-recast-" + safe(pluginId).toLowerCase(Locale.ROOT)
        );
    }

    @Override
    public synchronized RecastResponse acceptRisk(String pluginId, String assetUuid, String justification, LocalDateTime expiresAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pluginId", pluginId);
        if (assetUuid != null) payload.put("assetUuid", assetUuid);
        if (justification != null) payload.put("justification", justification);
        if (expiresAt != null) payload.put("expiresAt", expiresAt.toString());
        recordedCalls.add(new StubInvocation(Instant.now(), "ACCEPT_RISK", pluginId, payload));

        log.info("[Tenable-STUB] ACCEPT_RISK plugin={} asset={} expires={}", pluginId, assetUuid, expiresAt);
        return new RecastResponse(
            "ACCEPT_RISK", pluginId, assetUuid, true, 200, 1,
            "stub-accept-" + safe(pluginId).toLowerCase(Locale.ROOT)
        );
    }

    @Override
    public synchronized RecastResponse removeRecast(String pluginId, String assetUuid) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pluginId", pluginId);
        if (assetUuid != null) payload.put("assetUuid", assetUuid);
        recordedCalls.add(new StubInvocation(Instant.now(), "REMOVE_RECAST", pluginId, payload));

        log.info("[Tenable-STUB] REMOVE_RECAST plugin={} asset={}", pluginId, assetUuid);
        return new RecastResponse(
            "REMOVE_RECAST", pluginId, assetUuid, true, 200, 1,
            "stub-remove-" + safe(pluginId).toLowerCase(Locale.ROOT)
        );
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

    private static String safe(String s) {
        if (s == null) return "null";
        return s.replaceAll("[^A-Za-z0-9_-]", "-");
    }
}
