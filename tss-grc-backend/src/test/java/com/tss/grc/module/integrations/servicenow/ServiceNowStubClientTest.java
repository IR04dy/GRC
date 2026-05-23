package com.tss.grc.module.integrations.servicenow;

import com.tss.grc.module.integrations.servicenow.client.StubServiceNowClient;
import com.tss.grc.module.integrations.servicenow.config.ServiceNowProperties;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentRequest;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentResponse;
import com.tss.grc.module.integrations.servicenow.dto.StubInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the stub client's deterministic behaviour — INC numbers run
 * from INC0010001 upward and every call is recorded.
 */
class ServiceNowStubClientTest {

    private StubServiceNowClient stub;

    @BeforeEach
    void setUp() {
        ServiceNowProperties props = new ServiceNowProperties();
        props.setBaseUrl("https://acme-dev.service-now.com");
        stub = new StubServiceNowClient(props);
    }

    private ServiceNowIncidentRequest sample(String id, String severity) {
        return new ServiceNowIncidentRequest(
            "CYBER_INCIDENT",
            id,
            "Suspicious lateral movement",
            "Detected SMB scan from 10.40.2.18",
            severity,
            "Security",
            "SOC Tier 2",
            "soc@tss.local",
            Map.of()
        );
    }

    @Test
    void createIncident_assignsMonotonicNumbers_andRecordsCall() {
        ServiceNowIncidentResponse first = stub.createIncident(sample("CYB-001", "HIGH"));
        ServiceNowIncidentResponse second = stub.createIncident(sample("CYB-002", "CRITICAL"));

        assertThat(first.snowNumber()).isEqualTo("INC0010001");
        assertThat(second.snowNumber()).isEqualTo("INC0010002");
        assertThat(first.state()).isEqualTo("New");
        assertThat(first.snowSysId()).isNotBlank();
        assertThat(first.url()).startsWith("https://acme-dev.service-now.com/nav_to.do");

        List<StubInvocation> calls = stub.getRecordedCalls();
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).method()).isEqualTo("CREATE");
        assertThat(calls.get(0).payload()).containsEntry("grcSourceId", "CYB-001");
    }

    @Test
    void getIncident_returnsCreatedRecord_andNullForUnknown() {
        ServiceNowIncidentResponse created = stub.createIncident(sample("CYB-100", "MEDIUM"));
        ServiceNowIncidentResponse fetched = stub.getIncident(created.snowNumber());

        assertThat(fetched).isNotNull();
        assertThat(fetched.snowSysId()).isEqualTo(created.snowSysId());

        assertThat(stub.getIncident("INC9999999")).isNull();
    }

    @Test
    void updateIncident_transitionsState_andRecordsPatch() {
        ServiceNowIncidentResponse created = stub.createIncident(sample("CYB-200", "LOW"));
        ServiceNowIncidentResponse updated = stub.updateIncident(
            created.snowNumber(),
            Map.of("state", "In Progress", "work_notes", "SOC investigating")
        );

        assertThat(updated.state()).isEqualTo("In Progress");
        StubInvocation last = stub.getRecordedCalls().get(stub.getRecordedCalls().size() - 1);
        assertThat(last.method()).isEqualTo("UPDATE");
        assertThat(last.payload()).containsKeys("state", "work_notes");
    }

    @Test
    void closeIncident_flipsStateToClosed() {
        ServiceNowIncidentResponse created = stub.createIncident(sample("CYB-300", "HIGH"));
        boolean ok = stub.closeIncident(created.snowNumber(), "Contained, no impact");
        assertThat(ok).isTrue();

        ServiceNowIncidentResponse after = stub.getIncident(created.snowNumber());
        assertThat(after.state()).isEqualTo("Closed");
    }

    @Test
    void closeIncident_returnsFalseForUnknownNumber() {
        assertThat(stub.closeIncident("INC0099999", "n/a")).isFalse();
    }

    @Test
    void isHealthy_alwaysTrue_andRecorded() {
        assertThat(stub.isHealthy()).isTrue();
        StubInvocation last = stub.getRecordedCalls().get(stub.getRecordedCalls().size() - 1);
        assertThat(last.method()).isEqualTo("HEALTH");
    }
}
