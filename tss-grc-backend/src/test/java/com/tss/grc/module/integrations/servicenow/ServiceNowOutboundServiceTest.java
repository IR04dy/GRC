package com.tss.grc.module.integrations.servicenow;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.servicenow.client.ServiceNowClient;
import com.tss.grc.module.integrations.servicenow.config.ServiceNowProperties;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentRequest;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentResponse;
import com.tss.grc.module.integrations.servicenow.entity.IntegrationExternalLink;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.integrations.servicenow.service.ServiceNowOutboundService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks in:
 *   - severity → impact/urgency mapping
 *   - no-op when integration disabled
 *   - external_link row written on successful push
 */
@ExtendWith(MockitoExtension.class)
class ServiceNowOutboundServiceTest {

    @Mock private ServiceNowClient client;
    @Mock private IntegrationExternalLinkRepository linkRepo;

    private ServiceNowProperties props;
    private ServiceNowOutboundService service;

    @BeforeEach
    void setUp() {
        props = new ServiceNowProperties();
        props.setEnabled(true);
        props.setBaseUrl("https://acme.service-now.com");
        service = new ServiceNowOutboundService(client, props, linkRepo);
        TenantContext.setCurrentTenant(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── severity mapping ────────────────────────────────────────────────

    @Test
    void severityMapping_critical() {
        int[] iu = ServiceNowOutboundService.mapSeverityToImpactUrgency("CRITICAL");
        assertThat(iu).containsExactly(1, 1);
    }

    @Test
    void severityMapping_high() {
        int[] iu = ServiceNowOutboundService.mapSeverityToImpactUrgency("HIGH");
        assertThat(iu).containsExactly(2, 2);
    }

    @Test
    void severityMapping_medium() {
        int[] iu = ServiceNowOutboundService.mapSeverityToImpactUrgency("MEDIUM");
        assertThat(iu).containsExactly(3, 2);
    }

    @Test
    void severityMapping_low() {
        int[] iu = ServiceNowOutboundService.mapSeverityToImpactUrgency("LOW");
        assertThat(iu).containsExactly(3, 3);
    }

    @Test
    void severityMapping_unknownDefaultsToLow() {
        assertThat(ServiceNowOutboundService.mapSeverityToImpactUrgency("???"))
            .containsExactly(3, 3);
        assertThat(ServiceNowOutboundService.mapSeverityToImpactUrgency(null))
            .containsExactly(3, 3);
    }

    @Test
    void severityMapping_caseInsensitive() {
        assertThat(ServiceNowOutboundService.mapSeverityToImpactUrgency("high"))
            .containsExactly(2, 2);
    }

    // ── disabled-mode no-op ─────────────────────────────────────────────

    @Test
    void push_whenDisabled_isNoOpAndReturnsDisabled() {
        props.setEnabled(false);

        ServiceNowIncidentRequest req = new ServiceNowIncidentRequest(
            "CYBER_INCIDENT", "CYB-1", "test", "desc", "HIGH",
            "Security", null, null, Map.of()
        );
        ServiceNowIncidentResponse resp = service.push(req);

        assertThat(resp.isDisabled()).isTrue();
        verify(client, never()).createIncident(any());
        verify(linkRepo, never()).save(any());
    }

    @Test
    void pushGeneric_whenDisabled_isNoOp() {
        props.setEnabled(false);
        ServiceNowIncidentResponse resp = service.pushGeneric("AUDIT_FINDING",
            Map.of("id", "F-1", "title", "Missing MFA"));
        assertThat(resp.isDisabled()).isTrue();
        verify(client, never()).createIncident(any());
    }

    // ── enabled path ────────────────────────────────────────────────────

    @Test
    void push_enriches_severityIntoCustomFields_andSavesExternalLink() {
        ServiceNowIncidentResponse fake = new ServiceNowIncidentResponse(
            "abc123sysid", "INC0010001", "New", "2026-05-23T10:00:00Z",
            "https://acme.service-now.com/nav_to.do?uri=incident.do?sys_id=abc123sysid"
        );
        when(client.createIncident(any())).thenReturn(fake);
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        ServiceNowIncidentRequest req = new ServiceNowIncidentRequest(
            "CYBER_INCIDENT", "CYB-1", "Lateral movement", "long desc",
            "CRITICAL", null, "SOC Tier 2", "soc@tss.local", Map.of()
        );
        ServiceNowIncidentResponse resp = service.push(req);
        assertThat(resp.snowNumber()).isEqualTo("INC0010001");

        ArgumentCaptor<ServiceNowIncidentRequest> cap =
            ArgumentCaptor.forClass(ServiceNowIncidentRequest.class);
        verify(client).createIncident(cap.capture());
        ServiceNowIncidentRequest sent = cap.getValue();

        // category defaulted from props
        assertThat(sent.category()).isEqualTo("Security");
        // CRITICAL → impact=1 urgency=1 in customFields
        assertThat(sent.customFields()).containsEntry("impact", "1")
                                       .containsEntry("urgency", "1");

        // external_link row saved
        ArgumentCaptor<IntegrationExternalLink> linkCap =
            ArgumentCaptor.forClass(IntegrationExternalLink.class);
        verify(linkRepo).save(linkCap.capture());
        IntegrationExternalLink saved = linkCap.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(1L);
        assertThat(saved.getIntegration()).isEqualTo("SERVICENOW");
        assertThat(saved.getGrcModule()).isEqualTo("CYBER_INCIDENT");
        assertThat(saved.getGrcRecordId()).isEqualTo("CYB-1");
        assertThat(saved.getExternalId()).isEqualTo("abc123sysid");
        assertThat(saved.getExternalNumber()).isEqualTo("INC0010001");
        assertThat(saved.getLastKnownState()).isEqualTo("New");
    }

    @Test
    void pushFinding_routesThroughGenericMapping() {
        ServiceNowIncidentResponse fake = new ServiceNowIncidentResponse(
            "f1sysid", "INC0010002", "New", "2026-05-23T10:00:00Z", "url"
        );
        when(client.createIncident(any())).thenReturn(fake);
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        ServiceNowIncidentResponse resp = service.pushFinding(Map.of(
            "id", "FND-9",
            "title", "Missing access review",
            "severity", "HIGH",
            "description", "No reviewer assigned to ERP role pack."
        ));

        assertThat(resp.snowNumber()).isEqualTo("INC0010002");
        ArgumentCaptor<ServiceNowIncidentRequest> cap =
            ArgumentCaptor.forClass(ServiceNowIncidentRequest.class);
        verify(client).createIncident(cap.capture());
        ServiceNowIncidentRequest sent = cap.getValue();
        assertThat(sent.grcSourceModule()).isEqualTo("AUDIT_FINDING");
        assertThat(sent.grcSourceId()).isEqualTo("FND-9");
        assertThat(sent.severity()).isEqualTo("HIGH");
        assertThat(sent.customFields()).containsEntry("impact", "2")
                                       .containsEntry("urgency", "2");
    }
}
