package com.tss.grc.module.privacy_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.privacy_lc.dto.DsrCaseDto;
import com.tss.grc.module.privacy_lc.entity.DsrCase;
import com.tss.grc.module.privacy_lc.repository.DsrCaseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks the privacy/PDPL DSR state machine (V17 / PDPL Article 4):
 *   INTAKE              → VERIFYING_IDENTITY | REJECTED | EXTENDED
 *   VERIFYING_IDENTITY  → COLLECTING | REJECTED | EXTENDED
 *   COLLECTING          → REDACTING | REJECTED | EXTENDED
 *   REDACTING           → DELIVERED | EXTENDED
 *   EXTENDED            → VERIFYING_IDENTITY | COLLECTING | REDACTING | DELIVERED | REJECTED
 *
 * Article 4 SLA: 30 days default; extending grants another 30 (60 total max).
 */
@ExtendWith(MockitoExtension.class)
class DsrCaseServiceTest {

    private static final Long ORG_ID = 1L;
    private static final String EXT_ID = "DSR-2026-001";

    @Mock private DsrCaseRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private DsrCaseService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static DsrCase dsr(String status) {
        DsrCase d = new DsrCase();
        d.setExternalId(EXT_ID);
        d.setOrganisationId(ORG_ID);
        d.setStatus(status);
        d.setSlaDeadline(LocalDate.of(2026, 6, 6));
        return d;
    }

    private static DsrCaseDto.TransitionRequest req(String status) {
        DsrCaseDto.TransitionRequest r = new DsrCaseDto.TransitionRequest();
        r.setStatus(status);
        return r;
    }

    @Test
    void transition_INTAKE_to_VERIFYING_IDENTITY_succeeds() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(dsr("INTAKE")));
        when(repo.save(any(DsrCase.class))).thenAnswer(inv -> inv.getArgument(0));

        DsrCaseDto.TransitionRequest r = req("VERIFYING_IDENTITY");
        r.setVerificationMethod("NAFATH");
        DsrCaseDto result = service.transition(EXT_ID, r);

        assertThat(result.getStatus()).isEqualTo("VERIFYING_IDENTITY");
        assertThat(result.getVerificationMethod()).isEqualTo("NAFATH");
        assertThat(result.getIdentityVerifiedAt()).isNotNull();
    }

    @Test
    void transition_REDACTING_to_DELIVERED_records_delivery() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(dsr("REDACTING")));
        when(repo.save(any(DsrCase.class))).thenAnswer(inv -> inv.getArgument(0));

        DsrCaseDto.TransitionRequest r = req("DELIVERED");
        r.setDeliveryMethod("SECURE_PORTAL");
        DsrCaseDto result = service.transition(EXT_ID, r);

        assertThat(result.getStatus()).isEqualTo("DELIVERED");
        assertThat(result.getDeliveryMethod()).isEqualTo("SECURE_PORTAL");
        assertThat(result.getDeliveredAt()).isNotNull();
    }

    /** Article 4 60-day cap: extending pushes the SLA deadline by 30 days. */
    @Test
    void transition_to_EXTENDED_pushes_sla_by_30_days() {
        DsrCase original = dsr("COLLECTING");
        original.setSlaDeadline(LocalDate.of(2026, 6, 1));
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(original));
        when(repo.save(any(DsrCase.class))).thenAnswer(inv -> inv.getArgument(0));

        DsrCaseDto result = service.transition(EXT_ID, req("EXTENDED"));

        assertThat(result.getStatus()).isEqualTo("EXTENDED");
        assertThat(result.getExtended()).isTrue();
        assertThat(result.getSlaDeadline()).isEqualTo(LocalDate.of(2026, 7, 1));   // +30 days
    }

    @Test
    void transition_INTAKE_to_DELIVERED_skips_collection_and_rejects() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(dsr("INTAKE")));

        assertThatThrownBy(() -> service.transition(EXT_ID, req("DELIVERED")))
            .isInstanceOf(IllegalTransitionException.class)
            .extracting("from", "to")
            .containsExactly("INTAKE", "DELIVERED");
    }

    @Test
    void transition_DELIVERED_is_terminal() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(dsr("DELIVERED")));

        assertThatThrownBy(() -> service.transition(EXT_ID, req("REDACTING")))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void transition_unknownExternalId_throwsEntityNotFoundException() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, "DSR-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition("DSR-X", req("VERIFYING_IDENTITY")))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ── F7 / F8 emission contract ─────────────────────────────────────────────

    @Test
    void create_emits_DSR_RECEIVED_and_writes_audit_log() {
        when(repo.save(any(DsrCase.class))).thenAnswer(inv -> {
            DsrCase d = inv.getArgument(0);
            d.setId(42L);
            return d;
        });
        DsrCaseDto req = new DsrCaseDto();
        req.setType("ACCESS");
        req.setSubjectName("Test Subject");
        req.setContactEmail("subject@example.com");

        service.create(req);

        verify(audit).log(eq("CREATE"), eq("DsrCase"), eq(42L), any(String.class));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("DSR_RECEIVED");
        assertThat(emitted.entityType()).isEqualTo("DsrCase");
        assertThat(emitted.payload()).containsEntry("type", "ACCESS");
        assertThat(emitted.payload()).containsEntry("status", "INTAKE");
        assertThat(emitted.payload()).containsEntry("previousStatus", "");
    }

    @Test
    void transition_to_DELIVERED_emits_DSR_DELIVERED_with_previousStatus() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(dsr("REDACTING")));
        when(repo.save(any(DsrCase.class))).thenAnswer(inv -> inv.getArgument(0));

        DsrCaseDto.TransitionRequest r = req("DELIVERED");
        r.setDeliveryMethod("SECURE_PORTAL");
        service.transition(EXT_ID, r);

        verify(audit).log(eq("TRANSITION"), eq("DsrCase"), any(), any(String.class));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("DSR_DELIVERED");
        assertThat(emitted.payload()).containsEntry("status", "DELIVERED");
        assertThat(emitted.payload()).containsEntry("previousStatus", "REDACTING");
    }

    @Test
    void transition_to_EXTENDED_emits_DSR_EXTENDED() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(dsr("COLLECTING")));
        when(repo.save(any(DsrCase.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transition(EXT_ID, req("EXTENDED"));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("DSR_EXTENDED");
        assertThat(emitted.payload()).containsEntry("status", "EXTENDED");
        assertThat(emitted.payload()).containsEntry("extended", "true");
    }

    @Test
    void transition_to_REJECTED_emits_DSR_REJECTED() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(dsr("VERIFYING_IDENTITY")));
        when(repo.save(any(DsrCase.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transition(EXT_ID, req("REJECTED"));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("DSR_REJECTED");
        assertThat(emitted.payload()).containsEntry("status", "REJECTED");
        assertThat(emitted.payload()).containsEntry("previousStatus", "VERIFYING_IDENTITY");
    }
}
