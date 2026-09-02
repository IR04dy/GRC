package com.tss.grc.module.integrations.servicenow.client;

import com.tss.grc.module.integrations.servicenow.config.ServiceNowProperties;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentRequest;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentResponse;
import com.tss.grc.module.integrations.servicenow.dto.StubInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory ServiceNow simulator — the default in dev, demo, and unit tests.
 *
 * <p>Generates deterministic synthetic incident numbers (INC0010001,
 * INC0010002, …) starting at 10001 so tests can assert exact strings.
 * Each call is recorded in {@link #getRecordedCalls()} for inspection.
 *
 * <p>State transitions follow the ServiceNow incident state machine:
 * 1 New → 2 In Progress → 6 Resolved → 7 Closed. {@link #closeIncident}
 * moves to state 7 with the provided work_notes appended.
 */
@Component
@ConditionalOnProperty(
    name = "grc.integrations.servicenow.mode",
    havingValue = "STUB",
    matchIfMissing = true
)
public class StubServiceNowClient implements ServiceNowClient {

    private static final Logger log = LoggerFactory.getLogger(StubServiceNowClient.class);

    /** First synthetic INC number — chosen high enough to avoid collision with real fixture data. */
    private static final long INC_SEQ_START = 10_000L;

    private final ServiceNowProperties props;
    private final AtomicLong sequence = new AtomicLong(INC_SEQ_START);
    private final Map<String, ServiceNowIncidentResponse> byNumber = new LinkedHashMap<>();
    private final List<StubInvocation> recordedCalls = new ArrayList<>();

    public StubServiceNowClient(ServiceNowProperties props) {
        this.props = props;
    }

    @Override
    public synchronized ServiceNowIncidentResponse createIncident(ServiceNowIncidentRequest req) {
        long seq = sequence.incrementAndGet();
        String number = String.format(Locale.ROOT, "INC%07d", seq);
        String sysId = UUID.nameUUIDFromBytes(("snow-stub-" + number)
                .getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        String url = props.getBaseUrl() + "/nav_to.do?uri=incident.do?sys_id=" + sysId;
        String createdAt = Instant.now().toString();

        ServiceNowIncidentResponse resp = new ServiceNowIncidentResponse(
            sysId, number, "New", createdAt, url
        );
        byNumber.put(number, resp);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("grcSourceModule", req.grcSourceModule());
        payload.put("grcSourceId", req.grcSourceId());
        payload.put("shortDescription", req.shortDescription());
        payload.put("severity", req.severity());
        payload.put("category", req.category());
        recordedCalls.add(new StubInvocation(Instant.now(), "CREATE", number, payload));

        log.info("[ServiceNow-STUB] create -> {} ({}) for {}#{}",
            number, sysId, req.grcSourceModule(), req.grcSourceId());
        return resp;
    }

    @Override
    public synchronized ServiceNowIncidentResponse getIncident(String snowNumber) {
        recordedCalls.add(new StubInvocation(Instant.now(), "GET", snowNumber, Map.of()));
        return byNumber.get(snowNumber);
    }

    @Override
    public synchronized ServiceNowIncidentResponse updateIncident(String snowNumber, Map<String, Object> patch) {
        recordedCalls.add(new StubInvocation(Instant.now(), "UPDATE", snowNumber, new HashMap<>(patch)));
        ServiceNowIncidentResponse existing = byNumber.get(snowNumber);
        if (existing == null) return null;
        String newState = patch.containsKey("state")
            ? String.valueOf(patch.get("state"))
            : existing.state();
        ServiceNowIncidentResponse updated = new ServiceNowIncidentResponse(
            existing.snowSysId(), existing.snowNumber(), newState, existing.createdAt(), existing.url()
        );
        byNumber.put(snowNumber, updated);
        log.info("[ServiceNow-STUB] update {} -> state={}", snowNumber, newState);
        return updated;
    }

    @Override
    public synchronized boolean closeIncident(String snowNumber, String closureNotes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("state", "Closed");
        payload.put("close_notes", closureNotes);
        recordedCalls.add(new StubInvocation(Instant.now(), "CLOSE", snowNumber, payload));
        ServiceNowIncidentResponse existing = byNumber.get(snowNumber);
        if (existing == null) return false;
        byNumber.put(snowNumber, new ServiceNowIncidentResponse(
            existing.snowSysId(), existing.snowNumber(), "Closed",
            existing.createdAt(), existing.url()
        ));
        log.info("[ServiceNow-STUB] close {} notes={} chars", snowNumber,
            closureNotes == null ? 0 : closureNotes.length());
        return true;
    }

    @Override
    public boolean isHealthy() {
        recordedCalls.add(new StubInvocation(Instant.now(), "HEALTH", null, Map.of()));
        return true;
    }

    /** Snapshot of recorded calls — immutable copy. */
    public synchronized List<StubInvocation> getRecordedCalls() {
        return List.copyOf(recordedCalls);
    }

    /** Test-only — wipe state. */
    public synchronized void reset() {
        sequence.set(INC_SEQ_START);
        byNumber.clear();
        recordedCalls.clear();
    }
}
