package com.tss.grc.module.integrations.tenable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.Vulnerability;
import com.tss.grc.module.cyber.repository.VulnerabilityRepository;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.integrations.tenable.client.StubTenableClient;
import com.tss.grc.module.integrations.tenable.config.TenableProperties;
import com.tss.grc.module.integrations.tenable.controller.TenableController;
import com.tss.grc.module.integrations.tenable.dto.AcceptRiskRequest;
import com.tss.grc.module.integrations.tenable.dto.RecastRequest;
import com.tss.grc.module.integrations.tenable.dto.RecastResponse;
import com.tss.grc.module.integrations.tenable.dto.StatusDto;
import com.tss.grc.module.integrations.tenable.entity.TenableFindingLog;
import com.tss.grc.module.integrations.tenable.entity.TenableRecastLog;
import com.tss.grc.module.integrations.tenable.repository.TenableFindingLogRepository;
import com.tss.grc.module.integrations.tenable.repository.TenableRecastLogRepository;
import com.tss.grc.module.integrations.tenable.service.TenableInboundService;
import com.tss.grc.module.integrations.tenable.service.TenableOutboundService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Slice-style controller test — invokes the controller methods directly with
 * the stub client + real outbound/inbound wiring. Mirrors how the CrowdStrike
 * and Splunk controller tests work in this repo (no MockMvc, no
 * @{@code WebMvcTest}). The {@code @PreAuthorize} guards aren't exercised
 * here because none of the other module controller tests do — those guards
 * are verified by integration smoke tests in a separate harness.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenableControllerTest {

    private static final String GOOD_JUSTIFICATION =
        "Recasted because the affected asset sits in an isolated test VLAN — see CHANGE-2026-887";

    @Mock private TenableFindingLogRepository findingLogRepo;
    @Mock private TenableRecastLogRepository recastLogRepo;
    @Mock private IntegrationExternalLinkRepository linkRepo;
    @Mock private VulnerabilityRepository vulnerabilities;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private StubTenableClient stub;
    private TenableProperties props;
    private TenableInboundService inbound;
    private TenableOutboundService outbound;
    private TenableController controller;

    @BeforeEach
    void setUp() {
        props = new TenableProperties();
        props.setEnabled(true);
        props.setMode(TenableProperties.Mode.STUB);
        props.setPlatform(TenableProperties.Platform.IO);
        props.setBaseUrl("https://cloud.tenable.com");
        props.setAccessKey("ak-abc-1234");
        props.setSecretKey("sk-shhh-do-not-log");
        stub = new StubTenableClient(props);

        // Mock-out the side-effect repos so the wiring exercises end-to-end without a database.
        when(findingLogRepo.findByOrganisationIdAndTenableAssetUuidAndTenablePluginId(
                anyLong(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(vulnerabilities.save(any(Vulnerability.class))).thenAnswer(inv -> {
            Vulnerability v = inv.getArgument(0);
            v.setId(8888L);
            return v;
        });

        inbound = new TenableInboundService(
            stub, props, findingLogRepo, linkRepo, vulnerabilities, audit, publisher
        );
        outbound = new TenableOutboundService(
            stub, props, recastLogRepo, audit, publisher, new ObjectMapper()
        );

        @SuppressWarnings("unchecked")
        ObjectProvider<StubTenableClient> stubProvider =
            (ObjectProvider<StubTenableClient>) Mockito.mock(ObjectProvider.class);
        when(stubProvider.getIfAvailable()).thenReturn(stub);

        controller = new TenableController(
            stub, props, inbound, outbound, findingLogRepo, recastLogRepo, stubProvider
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
        TenableFindingLog lastDet = new TenableFindingLog();
        lastDet.setCreatedAt(LocalDateTime.of(2026, 5, 23, 10, 0));
        when(findingLogRepo.findFirstByOrganisationIdOrderByCreatedAtDesc(1L)).thenReturn(lastDet);

        TenableRecastLog lastAct = new TenableRecastLog();
        lastAct.setCreatedAt(LocalDateTime.of(2026, 5, 23, 11, 0));
        when(recastLogRepo.findFirstByOrganisationIdOrderByCreatedAtDesc(1L)).thenReturn(lastAct);

        StatusDto status = controller.status();
        assertThat(status.enabled()).isTrue();
        assertThat(status.pollingEnabled()).isFalse(); // default OFF
        assertThat(status.mode()).isEqualTo(TenableProperties.Mode.STUB);
        assertThat(status.platform()).isEqualTo(TenableProperties.Platform.IO);
        assertThat(status.healthy()).isTrue();
        assertThat(status.lastSuccessfulPollAt()).isEqualTo(LocalDateTime.of(2026, 5, 23, 10, 0));
        assertThat(status.lastRecastAt()).isEqualTo(LocalDateTime.of(2026, 5, 23, 11, 0));
    }

    // ── /config — secrets masking ───────────────────────────────────────

    @Test
    void config_masksAccessKeyAndSecretKey() {
        Map<String, Object> body = controller.config();
        assertThat(String.valueOf(body.get("accessKey"))).contains("****");
        assertThat(String.valueOf(body.get("secretKey"))).contains("****");
        assertThat(String.valueOf(body.get("secretKey"))).doesNotContain("sk-shhh-do-not-log");
        assertThat(body).containsEntry("enabled", true);
        assertThat(body).containsEntry("platform", TenableProperties.Platform.IO);
        assertThat(body).containsKey("retry");
    }

    // ── /poll — manual trigger ──────────────────────────────────────────

    @Test
    void manualPoll_runsInboundAndReportsCount() {
        Map<String, Object> body = controller.manualPoll();
        // Stub returns 5 findings per poll — all ingested on first run.
        assertThat(body).containsEntry("ingested", 5);
        assertThat(body).containsKey("at");
        // The stub recorded a POLL invocation.
        assertThat(stub.getRecordedCalls()).anyMatch(c -> c.method().equals("POLL"));
    }

    // ── /recast/{pluginId} ──────────────────────────────────────────────

    @Test
    void recast_invokesOutboundAndReturnsAcceptedResponse() {
        RecastResponse resp = controller.recast(
            "TEN-FND-5",
            new RecastRequest("LOW", GOOD_JUSTIFICATION, "fake-asset-1")
        );
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.actionType()).isEqualTo("RECAST_SEVERITY");
        assertThat(resp.targetPluginId()).isEqualTo("TEN-FND-5");
        // Stub recorded a RECAST call carrying the analyst justification.
        assertThat(stub.getRecordedCalls()).anyMatch(c ->
            c.method().equals("RECAST")
            && "TEN-FND-5".equals(c.targetId())
            && "LOW".equals(c.payload().get("newSeverity"))
        );
    }

    // ── /accept-risk/{pluginId} ─────────────────────────────────────────

    @Test
    void acceptRisk_invokesOutbound_andRecordsAcceptCall() {
        LocalDateTime exp = LocalDateTime.of(2026, 12, 31, 0, 0);
        RecastResponse resp = controller.acceptRisk(
            "TEN-FND-9",
            new AcceptRiskRequest(GOOD_JUSTIFICATION, exp, null)
        );
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.actionType()).isEqualTo("ACCEPT_RISK");
        assertThat(stub.getRecordedCalls()).anyMatch(c ->
            c.method().equals("ACCEPT_RISK")
            && "TEN-FND-9".equals(c.targetId())
            && exp.toString().equals(c.payload().get("expiresAt"))
        );
    }

    // ── /accept-risk validates justification at service layer ───────────

    @Test
    void acceptRisk_shortJustification_throwsAtServiceLayer() {
        // The DTO Bean Validation runs at the MVC layer; the service-level
        // double-check is exercised here.
        assertThatThrownBy(() -> controller.acceptRisk(
            "TEN-FND-X",
            new AcceptRiskRequest("too short", null, null)
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("30 characters");
    }

    // ── /remove-recast/{pluginId} ───────────────────────────────────────

    @Test
    void removeRecast_invokesOutbound_andRecordsRemoveCall() {
        RecastResponse resp = controller.removeRecast(
            "TEN-FND-3",
            new TenableController.RemoveRecastBody("fake-asset-2")
        );
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.actionType()).isEqualTo("REMOVE_RECAST");
        assertThat(stub.getRecordedCalls()).anyMatch(c ->
            c.method().equals("REMOVE_RECAST") && "TEN-FND-3".equals(c.targetId())
        );
    }

    // ── /remove-recast with null body works ─────────────────────────────

    @Test
    void removeRecast_withNullBody_treatsAsGlobal() {
        RecastResponse resp = controller.removeRecast("TEN-FND-11", null);
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.targetAssetUuid()).isNull();
    }

    // ── /stub/recorded-calls ────────────────────────────────────────────

    @Test
    void recordedCalls_returnsStubLogWhenAvailable() {
        controller.manualPoll();
        assertThat(controller.recordedCalls().getBody()).isNotEmpty();
    }

    // ── manualPoll routes through inbound, ingest reflected ────────────

    @Test
    void manualPoll_persistsFindingLogAndExternalLink() {
        controller.manualPoll();
        // Inbound writes a finding_log row + an external_link per finding.
        // 5 findings → 5 saves on each repo.
        Mockito.verify(findingLogRepo, Mockito.times(5)).save(any(TenableFindingLog.class));
        Mockito.verify(linkRepo, Mockito.times(5)).save(any());
    }
}
