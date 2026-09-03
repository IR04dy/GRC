package com.tss.grc.module.integrations.splunk.client;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.splunk.config.SplunkProperties;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventRequest;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventResponse;
import com.tss.grc.module.integrations.splunk.dto.StubInvocation;
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
 * In-memory Splunk HEC simulator — the default in dev, demo, and unit tests.
 *
 * <p>Generates deterministic synthetic event ids of the form
 * {@code SPLK-EVT-{org}-{seq}} starting at sequence 1 per organisation, so
 * tests can assert exact strings. Each call is recorded in
 * {@link #getRecordedCalls()} for inspection.
 *
 * <p>HEC code {@code 0} ("Success") is always returned — failure-mode
 * simulation is left to {@code LiveSplunkClient} integration tests.
 */
@Component
@ConditionalOnProperty(
    name = "grc.integrations.splunk.mode",
    havingValue = "STUB",
    matchIfMissing = true
)
public class StubSplunkClient implements SplunkClient {

    private static final Logger log = LoggerFactory.getLogger(StubSplunkClient.class);

    private final SplunkProperties props;
    private final Map<Long, AtomicLong> sequencesByOrg = new ConcurrentHashMap<>();
    private final List<StubInvocation> recordedCalls = new ArrayList<>();

    public StubSplunkClient(SplunkProperties props) {
        this.props = props;
    }

    @Override
    public synchronized SplunkHecEventResponse pushEvent(SplunkHecEventRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        long orgKey = orgId == null ? 0L : orgId;
        long seq = sequencesByOrg.computeIfAbsent(orgKey, k -> new AtomicLong(0L)).incrementAndGet();
        String eventId = String.format(Locale.ROOT, "SPLK-EVT-%d-%d", orgKey, seq);
        String idx = req.index() != null ? req.index() : props.getDefaultIndex();

        SplunkHecEventResponse resp = new SplunkHecEventResponse(
            eventId, 0, "Success", 200, idx, 1
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceModule", req.sourceModule());
        payload.put("sourceId", req.sourceId());
        payload.put("host", req.host());
        payload.put("source", req.source());
        payload.put("sourcetype", req.sourcetype() != null ? req.sourcetype() : props.getDefaultSourcetype());
        payload.put("index", idx);
        payload.put("eventTimeEpochSec", req.eventTimeEpochSec());
        payload.put("event", req.event());
        recordedCalls.add(new StubInvocation(Instant.now(), "PUSH", eventId, payload));

        log.info("[Splunk-STUB] push -> {} (index={}) for {}#{}",
            eventId, idx, req.sourceModule(), req.sourceId());
        return resp;
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
        recordedCalls.clear();
    }
}
