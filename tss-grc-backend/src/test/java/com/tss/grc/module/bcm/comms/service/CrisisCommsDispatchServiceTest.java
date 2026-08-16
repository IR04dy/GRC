package com.tss.grc.module.bcm.comms.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.comms.channel.ChannelSender;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.DispatchRequest;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.DispatchResult;
import com.tss.grc.module.bcm.comms.entity.CrisisCommsCascade;
import com.tss.grc.module.bcm.comms.entity.CrisisCommsTemplateV2;
import com.tss.grc.module.bcm.comms.repository.CrisisCommsCascadeRepository;
import com.tss.grc.module.bcm.comms.repository.CrisisCommsTemplateV2Repository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CrisisCommsDispatchService} (Slice 9-E).
 *
 * <p>Covers:
 * <ul>
 *   <li>Dispatch happy path — three channels, three cascade rows, F8 emitted</li>
 *   <li>Pre-approval guard — refusing to dispatch DRAFT templates</li>
 *   <li>Channel failure recorded as PARTIAL / FAILED status</li>
 *   <li>Placeholder substitution — {@code {{key}}} → value</li>
 *   <li>Audit row written + F8 dispatch + roll-up events emitted</li>
 *   <li>Unknown channel type recorded as FAILED rather than thrown</li>
 *   <li>Tenant isolation — list filters by org</li>
 * </ul>
 */
class CrisisCommsDispatchServiceTest {

    private static final Long ORG_ID = 9L;

    private CrisisCommsTemplateV2Repository templateRepo;
    private CrisisCommsCascadeRepository cascadeRepo;
    private AuditLogService audit;
    private ApplicationEventPublisher publisher;
    private CrisisCommsDispatchService service;

    private ChannelSender emailSender;
    private ChannelSender smsSender;
    private ChannelSender whatsappSender;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);

        templateRepo = mock(CrisisCommsTemplateV2Repository.class);
        cascadeRepo  = mock(CrisisCommsCascadeRepository.class);
        audit        = mock(AuditLogService.class);
        publisher    = mock(ApplicationEventPublisher.class);

        emailSender    = mock(ChannelSender.class);
        smsSender      = mock(ChannelSender.class);
        whatsappSender = mock(ChannelSender.class);
        when(emailSender.channelType()).thenReturn("EMAIL");
        when(smsSender.channelType()).thenReturn("SMS");
        when(whatsappSender.channelType()).thenReturn("WHATSAPP");

        service = new CrisisCommsDispatchService(
            templateRepo, cascadeRepo, audit, publisher,
            List.of(emailSender, smsSender, whatsappSender));

        // Echo cascade repo.save back with an id (simulating DB).
        when(cascadeRepo.save(any(CrisisCommsCascade.class))).thenAnswer(inv -> {
            CrisisCommsCascade c = inv.getArgument(0);
            if (c.getId() == null) c.setId(System.nanoTime() % 100_000);
            return c;
        });
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    private static CrisisCommsTemplateV2 approvedTemplate() {
        CrisisCommsTemplateV2 t = new CrisisCommsTemplateV2();
        t.setId(501L);
        t.setOrganisationId(ORG_ID);
        t.setTemplateCode("CCT-CUS-OUTAGE-008");
        t.setAudience("CUSTOMERS");
        t.setScenarioCategory("OUTAGE");
        t.setSeverityBand("HIGH");
        t.setApprovalStatus("APPROVED_BY_CISO");
        t.setSubjectEn("Service update — {{service_name}}");
        t.setSubjectAr("تحديث بخصوص خدمة {{service_name}}");
        t.setBodyEn("Service {{service_name}} is unavailable until {{eta_restored_at}}.");
        t.setBodyAr("الخدمة {{service_name}} غير متاحة حتى {{eta_restored_at}}.");
        t.setSmsBodyEn("{{service_name}} unavailable. ETA {{eta_restored_at}}.");
        t.setSmsBodyAr("الخدمة {{service_name}} غير متاحة. الوقت المتوقع {{eta_restored_at}}.");
        return t;
    }

    private static DispatchRequest dispatchReq(List<String> channels, List<String> recipients,
                                                Map<String, String> placeholders) {
        return new DispatchRequest(
            777L,            // crisis_event_id
            501L,            // template_id
            channels,
            "CUSTOMERS",
            recipients,
            placeholders);
    }

    // ─── Happy path ──────────────────────────────────────────────────

    @Test void dispatch_threeChannels_threeCascadeRows() {
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(approvedTemplate()));
        when(emailSender.send(any(), any(), any(), any()))
            .thenReturn(ChannelSender.SendResult.ok(3, "email ok"));
        when(smsSender.send(any(), any(), any(), any()))
            .thenReturn(ChannelSender.SendResult.ok(3, "sms ok"));
        when(whatsappSender.send(any(), any(), any(), any()))
            .thenReturn(ChannelSender.SendResult.ok(3, "wa ok"));

        Map<String, String> placeholders = Map.of(
            "service_name", "mada online",
            "eta_restored_at", "2026-05-23 14:00 KSA");

        DispatchResult out = service.dispatch(dispatchReq(
            List.of("EMAIL", "SMS", "WHATSAPP"),
            List.of("a@b.com", "b@b.com", "c@b.com"),
            placeholders));

        assertThat(out.cascades()).hasSize(3);
        assertThat(out.totalRecipients()).isEqualTo(9);  // 3 channels × 3 recipients
        assertThat(out.totalSuccess()).isEqualTo(9);
        assertThat(out.totalFailure()).isEqualTo(0);
        assertThat(out.cascades())
            .extracting("status")
            .containsExactlyInAnyOrder("COMPLETED", "COMPLETED", "COMPLETED");
        // SMS / WhatsApp picked the short body (placeholder applied to short).
        assertThat(out.cascades()).anyMatch(c ->
            "SMS".equals(c.channel()) && c.renderedSubject().contains("mada online"));
    }

    @Test void dispatch_blocksUnapprovedTemplate() {
        CrisisCommsTemplateV2 draft = approvedTemplate();
        draft.setApprovalStatus("DRAFT");
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.dispatch(dispatchReq(
                List.of("EMAIL"), List.of("a@b.com"), Map.of())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APPROVED_BY_CISO");
        verify(cascadeRepo, never()).save(any());
    }

    @Test void dispatch_recordsChannelFailure_asPartial() {
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(approvedTemplate()));
        when(emailSender.send(any(), any(), any(), any()))
            .thenReturn(ChannelSender.SendResult.partial(2, 1, "1 bounced"));

        DispatchResult out = service.dispatch(dispatchReq(
            List.of("EMAIL"),
            List.of("a@b.com", "b@b.com", "c@b.com"),
            Map.of("service_name", "mada", "eta_restored_at", "soon")));

        assertThat(out.cascades()).hasSize(1);
        assertThat(out.cascades().get(0).status()).isEqualTo("PARTIAL");
        assertThat(out.cascades().get(0).successCount()).isEqualTo(2);
        assertThat(out.cascades().get(0).failureCount()).isEqualTo(1);
    }

    @Test void dispatch_recordsChannelException_asFailed() {
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(approvedTemplate()));
        when(emailSender.send(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("SMTP server unreachable"));

        DispatchResult out = service.dispatch(dispatchReq(
            List.of("EMAIL"),
            List.of("a@b.com", "b@b.com"),
            Map.of("service_name", "mada", "eta_restored_at", "soon")));

        assertThat(out.cascades()).hasSize(1);
        assertThat(out.cascades().get(0).status()).isEqualTo("FAILED");
        assertThat(out.cascades().get(0).failureCount()).isEqualTo(2);
        assertThat(out.cascades().get(0).responsePayload()).contains("SMTP server unreachable");
    }

    @Test void dispatch_unknownChannelType_recordedAsFailed() {
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(approvedTemplate()));

        DispatchResult out = service.dispatch(dispatchReq(
            List.of("VOICE_BLAST"),   // not registered as a sender bean
            List.of("+966500000000"),
            Map.of("service_name", "mada", "eta_restored_at", "soon")));

        assertThat(out.cascades()).hasSize(1);
        assertThat(out.cascades().get(0).status()).isEqualTo("FAILED");
        assertThat(out.cascades().get(0).responsePayload()).contains("no registered ChannelSender");
    }

    @Test void dispatch_emitsDispatchAndCompletedEvents() {
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(approvedTemplate()));
        when(emailSender.send(any(), any(), any(), any()))
            .thenReturn(ChannelSender.SendResult.ok(1, "ok"));

        service.dispatch(dispatchReq(
            List.of("EMAIL"),
            List.of("a@b.com"),
            Map.of("service_name", "mada", "eta_restored_at", "soon")));

        // Two events: per-channel DISPATCHED + roll-up COMPLETED.
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(2)).publishEvent(captor.capture());
        List<DomainEvent> events = captor.getAllValues();
        assertThat(events).extracting(DomainEvent::triggerEvent)
            .containsExactly("CRISIS_COMMS_CASCADE_DISPATCHED",
                             "CRISIS_COMMS_CASCADE_COMPLETED");
    }

    @Test void dispatch_writesAuditRow() {
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(approvedTemplate()));
        when(emailSender.send(any(), any(), any(), any()))
            .thenReturn(ChannelSender.SendResult.ok(1, "ok"));

        service.dispatch(dispatchReq(
            List.of("EMAIL"),
            List.of("a@b.com"),
            Map.of("service_name", "mada", "eta_restored_at", "soon")));

        verify(audit, times(1))
            .log(any(), any(), any(), any());
    }

    // ─── F2-147 — simulated dispatch must reach the row and the roll-up ──

    @Test void dispatch_carriesSimulatedFlagOntoTheCascadeRowAndTheRollup() {
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(approvedTemplate()));
        // EMAIL really sent; SMS and WHATSAPP only logged.
        when(emailSender.send(any(), any(), any(), any()))
            .thenReturn(ChannelSender.SendResult.ok(2, "email: 2 ok, 0 failed"));
        when(smsSender.send(any(), any(), any(), any()))
            .thenReturn(ChannelSender.SendResult.simulated(2, "stub-sms: 2 recipients logged"));
        when(whatsappSender.send(any(), any(), any(), any()))
            .thenReturn(ChannelSender.SendResult.simulated(2, "stub-whatsapp: 2 recipients logged"));

        DispatchResult out = service.dispatch(dispatchReq(
            List.of("EMAIL", "SMS", "WHATSAPP"),
            List.of("a@b.com", "b@b.com"),
            Map.of("service_name", "mada", "eta_restored_at", "soon")));

        // The counters are unchanged — a simulated cascade is still a complete
        // audit row. What must not happen is the counters standing alone.
        assertThat(out.totalSuccess()).isEqualTo(6);
        assertThat(out.simulatedChannels()).containsExactly("SMS", "WHATSAPP");

        assertThat(out.cascades())
            .filteredOn(c -> "EMAIL".equals(c.channel()))
            .allMatch(c -> Boolean.FALSE.equals(c.simulated()));
        assertThat(out.cascades())
            .filteredOn(c -> !"EMAIL".equals(c.channel()))
            .allMatch(c -> Boolean.TRUE.equals(c.simulated()));

        // And the row that goes to the database carries it, not just the DTO —
        // the cascade row is what an auditor reads months later.
        ArgumentCaptor<CrisisCommsCascade> rows = ArgumentCaptor.forClass(CrisisCommsCascade.class);
        verify(cascadeRepo, times(3)).save(rows.capture());
        assertThat(rows.getAllValues())
            .filteredOn(r -> "SMS".equals(r.getChannel()))
            .allMatch(r -> Boolean.TRUE.equals(r.getSimulated()));
    }

    @Test void dispatch_allLiveChannels_namesNoSimulatedChannel() {
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(approvedTemplate()));
        when(emailSender.send(any(), any(), any(), any()))
            .thenReturn(ChannelSender.SendResult.ok(1, "email ok"));

        DispatchResult out = service.dispatch(dispatchReq(
            List.of("EMAIL"), List.of("a@b.com"),
            Map.of("service_name", "mada", "eta_restored_at", "soon")));

        assertThat(out.simulatedChannels()).isEmpty();
        assertThat(out.cascades().get(0).simulated()).isFalse();
    }

    @Test void dispatch_simulatedFlagReachesTheF8Payload() {
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(approvedTemplate()));
        when(smsSender.send(any(), any(), any(), any()))
            .thenReturn(ChannelSender.SendResult.simulated(1, "stub-sms: 1 recipient logged"));

        service.dispatch(dispatchReq(
            List.of("SMS"), List.of("+966500000000"),
            Map.of("service_name", "mada", "eta_restored_at", "soon")));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues().get(0).payload()).containsEntry("simulated", "true");
        assertThat(captor.getAllValues().get(1).payload())
            .containsEntry("simulatedChannels", "SMS");
    }

    // ─── F2-147 — readiness, so the operator learns before dispatching ──

    @Test void channelReadiness_reportsEveryDispatchableChannel() {
        when(emailSender.isHealthy()).thenReturn(true);
        when(emailSender.deliveryMode()).thenReturn(ChannelSender.DeliveryMode.LIVE);
        when(smsSender.isHealthy()).thenReturn(true);
        when(smsSender.deliveryMode()).thenReturn(ChannelSender.DeliveryMode.SIMULATED);
        when(whatsappSender.isHealthy()).thenReturn(true);
        when(whatsappSender.deliveryMode()).thenReturn(ChannelSender.DeliveryMode.SIMULATED);

        var readiness = service.channelReadiness();

        // Every channel the request vocabulary accepts, including the two with
        // no bean at all — "nothing would happen" is the answer that matters most.
        assertThat(readiness).extracting("channelType")
            .containsExactly("EMAIL", "SMS", "WHATSAPP", "TEAMS", "VOICE_BLAST");
        assertThat(readiness).filteredOn(r -> r.channelType().equals("EMAIL"))
            .allMatch(r -> r.registered() && r.healthy() && r.deliveryMode().equals("LIVE"));
        assertThat(readiness).filteredOn(r -> r.channelType().equals("SMS"))
            .allMatch(r -> r.registered() && r.deliveryMode().equals("SIMULATED"));
        assertThat(readiness).filteredOn(r -> r.channelType().equals("TEAMS"))
            .allMatch(r -> !r.registered() && !r.healthy()
                && r.deliveryMode().equals("UNREGISTERED"));
    }

    @Test void channelReadiness_aSenderThatWontAnswerIsUnknownNotLive() {
        // deliveryMode() unstubbed → null. The reassuring reading of a missing
        // answer is "LIVE"; that is precisely what must not happen.
        when(emailSender.isHealthy()).thenReturn(true);

        assertThat(service.channelReadiness())
            .filteredOn(r -> r.channelType().equals("EMAIL"))
            .allMatch(r -> r.deliveryMode().equals("UNKNOWN"));
    }

    // ─── Placeholder substitution ────────────────────────────────────

    @Test void applyPlaceholders_replacesDoubleBraceKeys() {
        Map<String, String> p = new HashMap<>();
        p.put("incident_code", "INC-0001");
        p.put("eta_restored_at", "2026-05-23 15:00");
        String out = CrisisCommsDispatchService.applyPlaceholders(
            "Subject {{incident_code}} ETA {{eta_restored_at}} keep {{unknown}}", p);
        assertThat(out).isEqualTo("Subject INC-0001 ETA 2026-05-23 15:00 keep {{unknown}}");
    }

    @Test void applyPlaceholders_nullSafe() {
        assertThat(CrisisCommsDispatchService.applyPlaceholders(null, Map.of("x", "y"))).isNull();
        assertThat(CrisisCommsDispatchService.applyPlaceholders("hi {{x}}", null))
            .isEqualTo("hi {{x}}");
    }

    // ─── Validation ──────────────────────────────────────────────────

    @Test void dispatch_emptyChannelList_rejected() {
        assertThatThrownBy(() -> service.dispatch(dispatchReq(
            List.of(), List.of("a@b.com"), Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one channel");
    }

    // ─── Tenant isolation ────────────────────────────────────────────

    @Test void listForCrisisEvent_filtersByOrg() {
        when(cascadeRepo.findByOrganisationIdAndCrisisEventIdAndIsDeletedFalseOrderByDispatchedAtDesc(
                ORG_ID, 777L))
            .thenReturn(List.of());
        service.listForCrisisEvent(777L);
        verify(cascadeRepo, times(1))
            .findByOrganisationIdAndCrisisEventIdAndIsDeletedFalseOrderByDispatchedAtDesc(ORG_ID, 777L);
    }
}
