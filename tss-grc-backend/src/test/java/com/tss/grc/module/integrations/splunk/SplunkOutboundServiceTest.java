package com.tss.grc.module.integrations.splunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.servicenow.entity.IntegrationExternalLink;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.integrations.splunk.client.SplunkClient;
import com.tss.grc.module.integrations.splunk.config.SplunkProperties;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventRequest;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventResponse;
import com.tss.grc.module.integrations.splunk.entity.SplunkHecOutboundLog;
import com.tss.grc.module.integrations.splunk.repository.SplunkHecOutboundLogRepository;
import com.tss.grc.module.integrations.splunk.service.SplunkOutboundService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks in:
 *   - disabled-mode no-op short-circuits before client + repo writes
 *   - successful push writes audit row + external_link row + F7/F8
 *   - retry-exhaustion path still writes an audit row (with error_message)
 *     and re-throws to the caller
 *   - pushSecurityEvent pulls the entity by id+org and builds the DTO
 */
@ExtendWith(MockitoExtension.class)
class SplunkOutboundServiceTest {

    @Mock private SplunkClient client;
    @Mock private SplunkHecOutboundLogRepository logRepo;
    @Mock private IntegrationExternalLinkRepository linkRepo;
    @Mock private SecurityEventRepository securityEventRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private SplunkProperties props;
    private SplunkOutboundService service;

    @BeforeEach
    void setUp() {
        props = new SplunkProperties();
        props.setEnabled(true);
        props.setDefaultIndex("tss_grc");
        props.setDefaultSourcetype("grc:event:json");
        service = new SplunkOutboundService(
            client, props, logRepo, linkRepo, securityEventRepo, audit, publisher, new ObjectMapper()
        );
        TenantContext.setCurrentTenant(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SplunkHecEventRequest sampleRequest() {
        return new SplunkHecEventRequest(
            "SECURITY_EVENT", "EXT-1",
            "edge-vpn-01", "tss-grc-platform",
            "grc:event:json", "tss_grc",
            1716440040L,
            Map.of("severity", "HIGH")
        );
    }

    private SplunkHecEventResponse fakeOk(String eventId) {
        return new SplunkHecEventResponse(eventId, 0, "Success", 200, "tss_grc", 1);
    }

    // ── disabled mode ───────────────────────────────────────────────────

    @Test
    void push_whenDisabled_isNoOp_doesNotCallClient_andDoesNotPersistAudit() {
        props.setEnabled(false);
        SplunkHecEventResponse resp = service.push(sampleRequest());
        assertThat(resp.isDisabled()).isTrue();
        verify(client, never()).pushEvent(any());
        verify(logRepo, never()).save(any());
        verify(linkRepo, never()).save(any());
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ── happy path ──────────────────────────────────────────────────────

    @Test
    void push_happyPath_savesAuditRow_externalLink_andEmitsAuditAndDomainEvent() {
        when(client.pushEvent(any())).thenReturn(fakeOk("SPLK-EVT-1-1"));
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        SplunkHecEventResponse resp = service.push(sampleRequest());

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.splunkEventId()).isEqualTo("SPLK-EVT-1-1");

        // Audit row persisted with httpStatus=200
        ArgumentCaptor<SplunkHecOutboundLog> logCap = ArgumentCaptor.forClass(SplunkHecOutboundLog.class);
        verify(logRepo).save(logCap.capture());
        SplunkHecOutboundLog logRow = logCap.getValue();
        assertThat(logRow.getHttpStatus()).isEqualTo(200);
        assertThat(logRow.getSourceModule()).isEqualTo("SECURITY_EVENT");
        assertThat(logRow.getSourceId()).isEqualTo("EXT-1");
        assertThat(logRow.getHecIndex()).isEqualTo("tss_grc");
        assertThat(logRow.getErrorMessage()).isNull();
        assertThat(logRow.getAttemptCount()).isEqualTo(1);

        // external_link row written (reusing the ServiceNow V163 generic table with integration='SPLUNK')
        ArgumentCaptor<IntegrationExternalLink> linkCap = ArgumentCaptor.forClass(IntegrationExternalLink.class);
        verify(linkRepo).save(linkCap.capture());
        IntegrationExternalLink link = linkCap.getValue();
        assertThat(link.getIntegration()).isEqualTo("SPLUNK");
        assertThat(link.getGrcModule()).isEqualTo("SECURITY_EVENT");
        assertThat(link.getGrcRecordId()).isEqualTo("EXT-1");
        assertThat(link.getExternalId()).isEqualTo("SPLK-EVT-1-1");

        // F7 + F8
        verify(audit, times(1)).log(eq("SPLUNK_EVENT_PUSHED"), eq("SECURITY_EVENT"), any(), anyString());
        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evCap.capture());
        DomainEvent ev = evCap.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("SPLUNK_EVENT_PUSHED");
        assertThat(ev.payload()).containsEntry("splunkEventId", "SPLK-EVT-1-1");
    }

    // ── transient failure / retry exhaustion ────────────────────────────

    @Test
    void push_whenClientThrows_persistsAuditWithErrorMessage_andRethrows() {
        when(client.pushEvent(any())).thenThrow(new RuntimeException("HEC 503 after 3 attempts"));

        assertThatThrownBy(() -> service.push(sampleRequest()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("503");

        ArgumentCaptor<SplunkHecOutboundLog> logCap = ArgumentCaptor.forClass(SplunkHecOutboundLog.class);
        verify(logRepo).save(logCap.capture());
        SplunkHecOutboundLog row = logCap.getValue();
        assertThat(row.getHttpStatus()).isNull();
        assertThat(row.getErrorMessage()).contains("HEC 503");

        // No external_link write, no F7/F8 on a failed push.
        verify(linkRepo, never()).save(any());
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ── pushSecurityEvent — entity hydration ────────────────────────────

    @Test
    void pushSecurityEvent_pullsEntityByIdAndOrg_andBuildsRequest() {
        SecurityEvent ev = new SecurityEvent();
        ev.setOrganisationId(1L);
        ev.setExternalId("SIEM-001");
        ev.setSourceSystem("SPLUNK_ES");
        ev.setSeverity("HIGH");
        ev.setCategory("AUTH");
        ev.setRuleName("Brute force on edge VPN");
        ev.setSourceHost("edge-vpn-01");
        ev.setSourceIp("10.40.2.18");
        ev.setStatus("NEW");
        ev.setEventTime(LocalDateTime.of(2026, 5, 23, 8, 14, 0));

        when(securityEventRepo.findByIdAndOrganisationId(42L, 1L)).thenReturn(Optional.of(ev));
        when(client.pushEvent(any())).thenReturn(fakeOk("SPLK-EVT-1-1"));
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        SplunkHecEventResponse resp = service.pushSecurityEvent(42L);
        assertThat(resp.isSuccess()).isTrue();

        ArgumentCaptor<SplunkHecEventRequest> reqCap = ArgumentCaptor.forClass(SplunkHecEventRequest.class);
        verify(client).pushEvent(reqCap.capture());
        SplunkHecEventRequest sent = reqCap.getValue();
        assertThat(sent.sourceModule()).isEqualTo("SECURITY_EVENT");
        assertThat(sent.sourceId()).isEqualTo("SIEM-001");
        assertThat(sent.host()).isEqualTo("edge-vpn-01");
        assertThat(sent.source()).isEqualTo("SPLUNK_ES");
        assertThat(sent.event()).containsEntry("severity", "HIGH")
                                .containsEntry("rule_name", "Brute force on edge VPN")
                                .containsEntry("source_host", "edge-vpn-01")
                                .containsEntry("source_ip", "10.40.2.18");
    }

    @Test
    void pushSecurityEvent_unknownId_throws() {
        when(securityEventRepo.findByIdAndOrganisationId(999L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.pushSecurityEvent(999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("999");
    }

    @Test
    void pushGenericEvent_routesThroughCommonPipeline() {
        when(client.pushEvent(any())).thenReturn(fakeOk("SPLK-EVT-1-1"));
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Missing access review");
        payload.put("severity", "HIGH");

        SplunkHecEventResponse resp = service.pushGenericEvent("FINDING", "FND-9", payload, "tss_grc_findings");
        assertThat(resp.isSuccess()).isTrue();

        ArgumentCaptor<SplunkHecEventRequest> reqCap = ArgumentCaptor.forClass(SplunkHecEventRequest.class);
        verify(client).pushEvent(reqCap.capture());
        SplunkHecEventRequest sent = reqCap.getValue();
        assertThat(sent.sourceModule()).isEqualTo("FINDING");
        assertThat(sent.sourceId()).isEqualTo("FND-9");
        assertThat(sent.index()).isEqualTo("tss_grc_findings");
    }

    // Light shim around Mockito's `eq` to keep import noise low.
    private static String eq(String s) { return org.mockito.ArgumentMatchers.eq(s); }
}
