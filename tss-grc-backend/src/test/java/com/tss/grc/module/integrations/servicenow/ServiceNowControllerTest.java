package com.tss.grc.module.integrations.servicenow;

import com.tss.grc.module.integrations.servicenow.client.StubServiceNowClient;
import com.tss.grc.module.integrations.servicenow.config.ServiceNowProperties;
import com.tss.grc.module.integrations.servicenow.controller.ServiceNowController;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentRequest;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentResponse;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.integrations.servicenow.service.ServiceNowOutboundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Slice-style controller test — invokes the controller methods directly
 * with the stub client wired in. Doesn't spin up MockMvc since none of
 * the other module tests in this repo do.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceNowControllerTest {

    @Mock private IntegrationExternalLinkRepository linkRepo;

    private StubServiceNowClient stub;
    private ServiceNowProperties props;
    private ServiceNowOutboundService outbound;
    private ServiceNowController controller;

    @BeforeEach
    void setUp() {
        props = new ServiceNowProperties();
        props.setEnabled(true);
        props.setMode(ServiceNowProperties.Mode.STUB);
        props.setBaseUrl("https://acme-dev.service-now.com");
        props.getAuth().setClientId("client-abc");
        props.getAuth().setClientSecret("topsecret-shhh");

        stub = new StubServiceNowClient(props);

        // Avoid real DB writes — outbound stores external_link rows via this mock.
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        outbound = new ServiceNowOutboundService(stub, props, linkRepo);

        @SuppressWarnings("unchecked")
        ObjectProvider<StubServiceNowClient> provider = (ObjectProvider<StubServiceNowClient>)
            org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(stub);

        controller = new ServiceNowController(stub, props, outbound, provider);

        // Set tenant for the outbound external_link write.
        com.tss.grc.common.tenant.TenantContext.setCurrentTenant(1L);
    }

    @org.junit.jupiter.api.AfterEach
    void cleanUp() {
        com.tss.grc.common.tenant.TenantContext.clear();
    }

    @Test
    void health_reflectsStubHealthy() {
        Map<String, Object> body = controller.health();
        assertThat(body).containsEntry("enabled", true)
                        .containsEntry("mode", ServiceNowProperties.Mode.STUB)
                        .containsEntry("healthy", true);
    }

    @Test
    void config_masksSecrets() {
        Map<String, Object> body = controller.config();
        assertThat(body).containsKey("auth");
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) body.get("auth");
        assertThat(String.valueOf(auth.get("clientId"))).contains("****");
        assertThat(String.valueOf(auth.get("clientSecret"))).contains("****");
        assertThat(String.valueOf(auth.get("clientSecret"))).doesNotContain("topsecret-shhh");
    }

    @Test
    void testPush_invokesStubAndReturnsIncResponse() {
        ServiceNowIncidentRequest req = new ServiceNowIncidentRequest(
            "CYBER_INCIDENT", "CYB-42", "Brute-force on edge VPN", "Repeated 401s",
            "CRITICAL", "Security", "SOC Tier 2", "soc@tss.local", Map.of()
        );
        ServiceNowIncidentResponse resp = controller.testPush(req);

        assertThat(resp.snowNumber()).startsWith("INC");
        assertThat(resp.state()).isEqualTo("New");
        assertThat(stub.getRecordedCalls()).anyMatch(c -> c.method().equals("CREATE"));
    }

    @Test
    void recordedCalls_returnsStubLogWhenAvailable() {
        controller.testPush(new ServiceNowIncidentRequest(
            "AUDIT_FINDING", "FND-1", "Finding test", "", "MEDIUM",
            null, null, null, Map.of()
        ));
        assertThat(controller.recordedCalls().getBody()).isNotEmpty();
    }
}
