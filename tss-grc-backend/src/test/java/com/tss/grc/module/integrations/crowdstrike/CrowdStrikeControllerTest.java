package com.tss.grc.module.integrations.crowdstrike;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.crowdstrike.client.StubCrowdStrikeClient;
import com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties;
import com.tss.grc.module.integrations.crowdstrike.controller.CrowdStrikeController;
import com.tss.grc.module.integrations.crowdstrike.dto.ContainmentRequest;
import com.tss.grc.module.integrations.crowdstrike.dto.ContainmentResponse;
import com.tss.grc.module.integrations.crowdstrike.dto.FalconDetectionDto;
import com.tss.grc.module.integrations.crowdstrike.dto.StatusDto;
import com.tss.grc.module.integrations.crowdstrike.entity.CrowdStrikeActionLog;
import com.tss.grc.module.integrations.crowdstrike.entity.CrowdStrikeDetectionLog;
import com.tss.grc.module.integrations.crowdstrike.repository.CrowdStrikeActionLogRepository;
import com.tss.grc.module.integrations.crowdstrike.repository.CrowdStrikeDetectionLogRepository;
import com.tss.grc.module.integrations.crowdstrike.service.CrowdStrikeInboundService;
import com.tss.grc.module.integrations.crowdstrike.service.CrowdStrikeOutboundService;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Slice-style controller test — invokes the controller methods directly with
 * the stub client + real outbound/inbound wiring. Mirrors how Splunk and
 * ServiceNow controller tests work in this repo (no MockMvc, no
 * @{@code WebMvcTest}). The {@code @PreAuthorize} guards aren't exercised
 * here because none of the other module controller tests do — those guards
 * are verified by integration smoke tests in a separate harness.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrowdStrikeControllerTest {

    @Mock private CrowdStrikeDetectionLogRepository detectionLogRepo;
    @Mock private CrowdStrikeActionLogRepository actionLogRepo;
    @Mock private IntegrationExternalLinkRepository linkRepo;
    @Mock private SecurityEventRepository securityEvents;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private StubCrowdStrikeClient stub;
    private CrowdStrikeProperties props;
    private CrowdStrikeInboundService inbound;
    private CrowdStrikeOutboundService outbound;
    private CrowdStrikeController controller;

    @BeforeEach
    void setUp() {
        props = new CrowdStrikeProperties();
        props.setEnabled(true);
        props.setMode(CrowdStrikeProperties.Mode.STUB);
        props.setBaseUrl("https://api.eu-1.crowdstrike.com");
        props.setClientId("client-abc-1234");
        props.setClientSecret("topsecret-shhh-do-not-log");
        stub = new StubCrowdStrikeClient(props);

        // Mock-out the side-effect repos so the wiring exercises end-to-end
        // without a database.
        when(detectionLogRepo.findByOrganisationIdAndFalconDetectionId(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(securityEvents.save(any(SecurityEvent.class))).thenAnswer(inv -> {
            SecurityEvent ev = inv.getArgument(0);
            ev.setId(7777L);
            return ev;
        });

        inbound = new CrowdStrikeInboundService(
            stub, props, detectionLogRepo, linkRepo, securityEvents, audit, publisher, new ObjectMapper()
        );
        outbound = new CrowdStrikeOutboundService(
            stub, props, actionLogRepo, audit, publisher, new ObjectMapper()
        );

        @SuppressWarnings("unchecked")
        ObjectProvider<StubCrowdStrikeClient> stubProvider =
            (ObjectProvider<StubCrowdStrikeClient>) Mockito.mock(ObjectProvider.class);
        when(stubProvider.getIfAvailable()).thenReturn(stub);

        controller = new CrowdStrikeController(
            stub, props, inbound, outbound, detectionLogRepo, actionLogRepo, stubProvider
        );

        // Set tenant + auth context.
        TenantContext.setCurrentTenant(1L);
        GrcUserDetails principal = new GrcUserDetails(
            555L, 1L, "ciso", "x", true, true,
            List.of(new SimpleGrantedAuthority("ROLE_CISO"))
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        stub.reset();
    }

    // ── /status ─────────────────────────────────────────────────────────

    @Test
    void status_reflectsKillSwitchesAndStubHealth() {
        CrowdStrikeDetectionLog lastDet = new CrowdStrikeDetectionLog();
        lastDet.setCreatedAt(LocalDateTime.of(2026, 5, 23, 10, 0));
        when(detectionLogRepo.findFirstByOrganisationIdOrderByCreatedAtDesc(1L)).thenReturn(lastDet);

        CrowdStrikeActionLog lastAct = new CrowdStrikeActionLog();
        lastAct.setCreatedAt(LocalDateTime.of(2026, 5, 23, 11, 0));
        when(actionLogRepo.findFirstByOrganisationIdOrderByCreatedAtDesc(1L)).thenReturn(lastAct);

        StatusDto status = controller.status();
        assertThat(status.enabled()).isTrue();
        assertThat(status.pollingEnabled()).isFalse(); // default OFF
        assertThat(status.mode()).isEqualTo(CrowdStrikeProperties.Mode.STUB);
        assertThat(status.healthy()).isTrue();
        assertThat(status.lastSuccessfulPollAt()).isEqualTo(LocalDateTime.of(2026, 5, 23, 10, 0));
        assertThat(status.lastActionAt()).isEqualTo(LocalDateTime.of(2026, 5, 23, 11, 0));
    }

    // ── /config — secrets masking ───────────────────────────────────────

    @Test
    void config_masksClientIdAndSecret() {
        Map<String, Object> body = controller.config();
        assertThat(String.valueOf(body.get("clientId"))).contains("****");
        assertThat(String.valueOf(body.get("clientSecret"))).contains("****");
        assertThat(String.valueOf(body.get("clientSecret"))).doesNotContain("topsecret-shhh-do-not-log");
        assertThat(body).containsEntry("enabled", true);
        assertThat(body).containsKey("retry");
    }

    // ── /poll — manual trigger ──────────────────────────────────────────

    @Test
    void manualPoll_runsInboundAndReportsCount() {
        Map<String, Object> body = controller.manualPoll();
        // Stub returns 4 detections per poll — all ingested on first run.
        assertThat(body).containsEntry("ingested", 4);
        assertThat(body).containsKey("at");
        // The stub recorded a POLL invocation.
        assertThat(stub.getRecordedCalls()).anyMatch(c -> c.method().equals("POLL"));
    }

    // ── /contain ────────────────────────────────────────────────────────

    @Test
    void contain_invokesOutboundAndReturnsAcceptedResponse() {
        ContainmentResponse resp = controller.contain(
            "aid-host-5",
            new ContainmentRequest("ransomware seen on PCI server")
        );
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.actionType()).isEqualTo("CONTAIN_HOST");
        assertThat(resp.targetHostId()).isEqualTo("aid-host-5");
        assertThat(resp.falconResourceId()).contains("aid-host-5");
        // Stub recorded a CONTAIN call carrying the analyst reason.
        assertThat(stub.getRecordedCalls()).anyMatch(c ->
            c.method().equals("CONTAIN")
            && "aid-host-5".equals(c.targetId())
            && "ransomware seen on PCI server".equals(c.payload().get("reason"))
        );
    }

    // ── /lift ───────────────────────────────────────────────────────────

    @Test
    void lift_invokesOutboundLift_andRecordsLiftCall() {
        ContainmentResponse resp = controller.lift("aid-host-5");
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.actionType()).isEqualTo("LIFT_CONTAINMENT");
        assertThat(stub.getRecordedCalls()).anyMatch(c ->
            c.method().equals("LIFT") && "aid-host-5".equals(c.targetId())
        );
    }

    // ── /stub/recorded-calls ────────────────────────────────────────────

    @Test
    void recordedCalls_returnsStubLogWhenAvailable() {
        controller.manualPoll();
        assertThat(controller.recordedCalls().getBody()).isNotEmpty();
    }

    // ── manualPoll routes through inbound, ingest reflected ────────────

    @Test
    void manualPoll_persistsDetectionLogAndExternalLink() {
        controller.manualPoll();
        // Inbound writes a detection_log row + an external_link per detection.
        // 4 detections → 4 saves on each repo.
        Mockito.verify(detectionLogRepo, Mockito.times(4)).save(any(CrowdStrikeDetectionLog.class));
        Mockito.verify(linkRepo, Mockito.times(4)).save(any());
    }

    @SuppressWarnings("unused")
    private static FalconDetectionDto dto() {
        return new FalconDetectionDto("x", "y", "z", 50, "a", "b", "T1", "2026-01-01T00:00:00Z", "d");
    }
}
