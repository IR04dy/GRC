package com.tss.grc.module.integrations.splunk;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.splunk.client.StubSplunkClient;
import com.tss.grc.module.integrations.splunk.config.SplunkProperties;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventRequest;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventResponse;
import com.tss.grc.module.integrations.splunk.dto.StubInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the stub Splunk client's deterministic behaviour — event ids
 * follow {@code SPLK-EVT-{org}-{seq}}, every call is recorded, and the
 * default index/sourcetype is applied when the request omits them.
 */
class SplunkStubClientTest {

    private StubSplunkClient stub;

    @BeforeEach
    void setUp() {
        SplunkProperties props = new SplunkProperties();
        props.setEnabled(true);
        props.setDefaultIndex("tss_grc");
        props.setDefaultSourcetype("grc:event:json");
        stub = new StubSplunkClient(props);
        TenantContext.setCurrentTenant(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SplunkHecEventRequest sample(String externalId, String severity) {
        return new SplunkHecEventRequest(
            "SECURITY_EVENT",
            externalId,
            "edge-vpn-01",
            "tss-grc-platform",
            "grc:event:json",
            "tss_grc",
            1716440040L,
            Map.of("severity", severity, "rule_name", "Edge VPN brute-force")
        );
    }

    @Test
    void pushEvent_assignsDeterministicEventIds_perOrg() {
        SplunkHecEventResponse first = stub.pushEvent(sample("EXT-1", "HIGH"));
        SplunkHecEventResponse second = stub.pushEvent(sample("EXT-2", "CRITICAL"));

        assertThat(first.splunkEventId()).isEqualTo("SPLK-EVT-7-1");
        assertThat(second.splunkEventId()).isEqualTo("SPLK-EVT-7-2");
        assertThat(first.hecCode()).isZero();
        assertThat(first.httpStatus()).isEqualTo(200);
        assertThat(first.hecText()).isEqualTo("Success");
        assertThat(first.hecIndex()).isEqualTo("tss_grc");
        assertThat(first.isSuccess()).isTrue();
        assertThat(first.attemptCount()).isEqualTo(1);
    }

    @Test
    void pushEvent_perOrgSequences_areIndependent() {
        TenantContext.setCurrentTenant(1L);
        SplunkHecEventResponse a = stub.pushEvent(sample("A", "LOW"));
        TenantContext.setCurrentTenant(2L);
        SplunkHecEventResponse b = stub.pushEvent(sample("B", "LOW"));
        assertThat(a.splunkEventId()).isEqualTo("SPLK-EVT-1-1");
        assertThat(b.splunkEventId()).isEqualTo("SPLK-EVT-2-1");
    }

    @Test
    void pushEvent_recordsCallPayload() {
        stub.pushEvent(sample("EXT-9", "MEDIUM"));
        List<StubInvocation> calls = stub.getRecordedCalls();
        assertThat(calls).hasSize(1);
        StubInvocation only = calls.get(0);
        assertThat(only.method()).isEqualTo("PUSH");
        assertThat(only.splunkEventId()).isEqualTo("SPLK-EVT-7-1");
        assertThat(only.payload()).containsEntry("sourceModule", "SECURITY_EVENT")
                                  .containsEntry("sourceId", "EXT-9")
                                  .containsEntry("host", "edge-vpn-01")
                                  .containsEntry("sourcetype", "grc:event:json")
                                  .containsEntry("index", "tss_grc");
    }

    @Test
    void pushEvent_defaultsAppliedWhenRequestOmitsIndex() {
        SplunkHecEventRequest noIndex = new SplunkHecEventRequest(
            "FINDING", "F-1", null, null, null, null, null, Map.of("title", "MFA missing")
        );
        SplunkHecEventResponse resp = stub.pushEvent(noIndex);
        assertThat(resp.hecIndex()).isEqualTo("tss_grc");
        // recorded payload shows the default sourcetype was applied
        StubInvocation only = stub.getRecordedCalls().get(0);
        assertThat(only.payload()).containsEntry("sourcetype", "grc:event:json")
                                  .containsEntry("index", "tss_grc");
    }

    @Test
    void isHealthy_alwaysTrue_andRecorded() {
        assertThat(stub.isHealthy()).isTrue();
        StubInvocation last = stub.getRecordedCalls().get(stub.getRecordedCalls().size() - 1);
        assertThat(last.method()).isEqualTo("HEALTH");
    }

    @Test
    void reset_clearsSequencesAndCalls() {
        stub.pushEvent(sample("X", "LOW"));
        stub.reset();
        assertThat(stub.getRecordedCalls()).isEmpty();
        SplunkHecEventResponse after = stub.pushEvent(sample("X", "LOW"));
        assertThat(after.splunkEventId()).isEqualTo("SPLK-EVT-7-1");
    }
}
