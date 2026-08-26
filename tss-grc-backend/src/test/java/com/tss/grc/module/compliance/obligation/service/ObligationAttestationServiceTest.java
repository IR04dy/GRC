package com.tss.grc.module.compliance.obligation.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.AttestationRecordRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.AttestationResponse;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.AttestationReviewRequest;
import com.tss.grc.module.compliance.obligation.entity.Obligation;
import com.tss.grc.module.compliance.obligation.entity.ObligationAttestation;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationRepository;
import com.tss.grc.module.compliance.obligation.repository.ObligationRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 11 — {@link ObligationAttestationService} tests.
 *
 * <p>Covers:
 * <ul>
 *   <li>Happy-path record (DUE → ATTESTED)</li>
 *   <li>ATTESTED_WITH_EXCEPTIONS preserves exception text;
 *       ATTESTED clears prior exceptions cleanly</li>
 *   <li>Reviewer ACCEPTED / REJECTED / REQUEST_CHANGES paths</li>
 *   <li>REQUEST_CHANGES flips the row back to IN_PROGRESS</li>
 *   <li>Roll-forward on obligation invoked only for genuine attestations</li>
 *   <li>F8 events emitted (OBLIGATION_ATTESTED, OBLIGATION_ATTESTATION_REVIEWED)</li>
 *   <li>Bulk markOverdueForCampaign</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ObligationAttestationServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private ObligationAttestationRepository repo;
    @Mock private ObligationRepository obligationRepo;
    @Mock private ObligationService obligationService;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private ObligationCampaignRollups rollups;

    private ObligationAttestationService service;

    @BeforeEach void setUp() {
        // F2-224 — the service resolves the actor from CurrentActor now.
        authAs("compliance.officer", 31L);
        TenantContext.setCurrentTenant(ORG_ID);
        service = new ObligationAttestationService(repo, obligationRepo, obligationService, audit, publisher, rollups);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static Obligation oblig() {
        Obligation o = new Obligation();
        o.setId(1001L);
        o.setOrganisationId(ORG_ID);
        o.setObligationCode("OBL-SAMA-2026-001");
        o.setRegulator("SAMA");
        o.setFrequency("MONTHLY");
        o.setLifecycleStage("ACTIVE");
        o.setNextDueDate(LocalDate.of(2026, 6, 15));
        return o;
    }

    private static ObligationAttestation existing(String status) {
        ObligationAttestation a = new ObligationAttestation();
        a.setId(501L);
        a.setOrganisationId(ORG_ID);
        a.setExternalId(UUID.randomUUID());
        a.setObligationId(1001L);
        a.setCampaignId(201L);
        a.setPeriodLabel("Q2-2026");
        a.setPeriodStart(LocalDate.of(2026, 4, 1));
        a.setPeriodEnd(LocalDate.of(2026, 6, 30));
        a.setDueDate(LocalDate.of(2026, 7, 15));
        a.setAttestationStatus(status);
        return a;
    }

    private static AttestationRecordRequest recordReq(String status, String exEn, String exAr) {
        return new AttestationRecordRequest(
            1001L, 201L,
            "Q2-2026",
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 15),
            status,
            "I confirm the Q2 2026 SAMA monthly CAR returns were filed on time.",
            "أُقِر بأن تقارير كفاية رأس المال الشهرية للربع الثاني 2026 قُدِّمت في موعدها.",
            "[evidence-pack-1]", exEn, exAr
        );
    }

    // ─── Record happy path ───────────────────────────────────────────

    @Test void recordAttestation_happyPath_DueToAttested() {
        when(obligationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 1001L))
            .thenReturn(Optional.of(oblig()));
        when(repo.findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByDueDateAsc(ORG_ID, 201L))
            .thenReturn(List.of(existing("DUE")));
        when(repo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));

        AttestationResponse out = service.recordAttestation(recordReq("ATTESTED", null, null));

        assertThat(out.attestationStatus()).isEqualTo("ATTESTED");
        // F2-224 — was isEqualTo(99L), the id the CALLER passed. An obligation attestation
        // names the person who asserted compliance; that is the principal.
        assertThat(out.attestedByUserId()).isEqualTo(31L);
        assertThat(out.attestedAt()).isNotNull();
        // F2-224 — the roll-forward reads the attester off the SAVED ROW, not the request.
        verify(obligationService).rollForwardAfterAttestation(eq(1001L), eq(31L), any());
    }

    // ─── F2-385 — campaign rollups recompute on every child status change ─────

    @Test void recordAttestation_recomputesCampaignRollups() {
        when(obligationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 1001L))
            .thenReturn(Optional.of(oblig()));
        when(repo.findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByDueDateAsc(ORG_ID, 201L))
            .thenReturn(List.of(existing("DUE")));
        when(repo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordAttestation(recordReq("ATTESTED", null, null));

        // Without this the campaign's stored counts froze at their post-open values, so stats()
        // reported 0% completion until the campaign was closed. The recompute runs for the campaign
        // the attestation belongs to (201), in the same transaction as the child save.
        verify(rollups).recompute(ORG_ID, 201L);
    }

    @Test void reviewAttestation_recomputesCampaignRollups() {
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(existing("ATTESTED")));
        when(repo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));

        // REQUEST_CHANGES moves the row back to IN_PROGRESS, changing the campaign's counts.
        service.reviewAttestation(501L, new AttestationReviewRequest("REQUEST_CHANGES", "redo", "أعد"));

        verify(rollups).recompute(ORG_ID, 201L);
    }

    @Test void recordAttestation_withExceptions_preservesExceptionText() {
        when(obligationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 1001L))
            .thenReturn(Optional.of(oblig()));
        when(repo.findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByDueDateAsc(ORG_ID, 201L))
            .thenReturn(List.of(existing("DUE")));
        when(repo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));

        AttestationResponse out = service.recordAttestation(recordReq(
            "ATTESTED_WITH_EXCEPTIONS",
            "Day 12 filing for April was 2 days late; remediation underway.",
            "تأخر إيداع إبريل عن الموعد بيومين، وقيد المعالجة."));

        assertThat(out.attestationStatus()).isEqualTo("ATTESTED_WITH_EXCEPTIONS");
        assertThat(out.exceptionsNotedEn()).contains("2 days late");
        assertThat(out.exceptionsNotedAr()).contains("تأخر");
    }

    @Test void recordAttestation_plainAttested_clearsAnyPriorExceptions() {
        ObligationAttestation prior = existing("DUE");
        prior.setExceptionsNotedEn("stale text");
        prior.setExceptionsNotedAr("نص قديم");
        when(obligationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 1001L))
            .thenReturn(Optional.of(oblig()));
        when(repo.findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByDueDateAsc(ORG_ID, 201L))
            .thenReturn(List.of(prior));
        when(repo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));

        AttestationResponse out = service.recordAttestation(recordReq("ATTESTED", "ignored", "تجاهل"));

        assertThat(out.exceptionsNotedEn()).isNull();
        assertThat(out.exceptionsNotedAr()).isNull();
    }

    @Test void recordAttestation_notApplicable_doesNotRollForward() {
        when(obligationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 1001L))
            .thenReturn(Optional.of(oblig()));
        when(repo.findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByDueDateAsc(ORG_ID, 201L))
            .thenReturn(List.of(existing("DUE")));
        when(repo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordAttestation(recordReq("NOT_APPLICABLE", null, null));

        verify(obligationService, never()).rollForwardAfterAttestation(anyLong(), anyLong(), any());
    }

    @Test void recordAttestation_obligationNotFoundRejected() {
        when(obligationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 1001L))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordAttestation(recordReq("ATTESTED", null, null)))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
            .hasMessageContaining("Obligation not found");
    }

    // ─── Reviewer outcomes ───────────────────────────────────────────

    @Test void reviewAttestation_acceptedKeepsAttestedStatus() {
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(existing("ATTESTED")));
        when(repo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));

        AttestationResponse out = service.reviewAttestation(501L,
            new AttestationReviewRequest("ACCEPTED", "Looks good", "جيد"));

        assertThat(out.reviewerDecision()).isEqualTo("ACCEPTED");
        assertThat(out.attestationStatus()).isEqualTo("ATTESTED");
        assertThat(out.reviewedAt()).isNotNull();
    }

    @Test void reviewAttestation_rejectedKeepsLifecycleStatus() {
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(existing("ATTESTED_WITH_EXCEPTIONS")));
        when(repo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));

        AttestationResponse out = service.reviewAttestation(501L,
            new AttestationReviewRequest("REJECTED", "Insufficient evidence", "أدلة غير كافية"));

        assertThat(out.reviewerDecision()).isEqualTo("REJECTED");
        assertThat(out.attestationStatus()).isEqualTo("ATTESTED_WITH_EXCEPTIONS");
    }

    @Test void reviewAttestation_requestChanges_flipsToInProgress() {
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(existing("ATTESTED")));
        when(repo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));

        AttestationResponse out = service.reviewAttestation(501L,
            new AttestationReviewRequest("REQUEST_CHANGES",
                "Please attach the actual filing receipt.",
                "يرجى إرفاق إيصال التقديم الفعلي."));

        assertThat(out.attestationStatus()).isEqualTo("IN_PROGRESS");
        assertThat(out.reviewerDecision()).isEqualTo("REQUEST_CHANGES");
    }

    @Test void reviewAttestation_rejectsUnreviewableStatus() {
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(existing("DUE")));
        assertThatThrownBy(() -> service.reviewAttestation(501L,
            new AttestationReviewRequest("ACCEPTED", "ok", "تمام")))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void reviewAttestation_invalidDecisionRejected() {
        assertThatThrownBy(() -> service.reviewAttestation(501L,
            new AttestationReviewRequest("MAYBE", "x", "س")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reviewerDecision");
    }

    @Test void reviewAttestation_emitsF8Event() {
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(existing("ATTESTED")));
        when(repo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reviewAttestation(501L, new AttestationReviewRequest("ACCEPTED", "ok", "تمام"));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().triggerEvent()).isEqualTo("OBLIGATION_ATTESTATION_REVIEWED");
        assertThat(captor.getValue().payload()).containsEntry("reviewerDecision", "ACCEPTED");
    }

    // ─── Bulk overdue ───────────────────────────────────────────────

    @Test void markOverdueForCampaign_flipsAllStaleRows() {
        ObligationAttestation a1 = existing("DUE");
        ObligationAttestation a2 = existing("IN_PROGRESS");
        a2.setId(502L);
        when(repo.findByOrganisationIdAndCampaignIdAndAttestationStatusInAndDueDateBeforeAndIsDeletedFalse(
                eq(ORG_ID), eq(201L), any(), any())).thenReturn(List.of(a1, a2));
        when(repo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));

        int flipped = service.markOverdueForCampaign(201L, LocalDate.now());

        assertThat(flipped).isEqualTo(2);
    }

    // ─── C-3 org-wide overdue sweep (compliance-deadline scheduler) ───

    @Test void sweepOverdueForOrg_flipsAllStaleRowsAndEmits() {
        ObligationAttestation a1 = existing("DUE");
        ObligationAttestation a2 = existing("IN_PROGRESS");
        a2.setId(502L);
        when(repo.findByOrganisationIdAndAttestationStatusInAndDueDateBeforeAndIsDeletedFalse(
                eq(ORG_ID), any(), any())).thenReturn(List.of(a1, a2));
        when(repo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));

        int flipped = service.sweepOverdueForOrg(LocalDate.now());

        assertThat(flipped).isEqualTo(2);
        assertThat(a1.getAttestationStatus()).isEqualTo("OVERDUE");
        assertThat(a2.getAttestationStatus()).isEqualTo("OVERDUE");
        // One OBLIGATION_OVERDUE F8 event per flipped row.
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).allMatch(e -> "OBLIGATION_OVERDUE".equals(e.triggerEvent()));
    }

    /**
     * F2-224 — a real principal in the SecurityContext, because the service now takes the actor
     * from {@code CurrentActor} rather than from the request. {@code CurrentActor} requires the
     * principal to be a {@link com.tss.grc.security.GrcUserDetails}, so a bare username token
     * would resolve to null and the assertion would be about nothing.
     */
    private static void authAs(String username, Long userId) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(
                    new com.tss.grc.security.GrcUserDetails(
                        userId, 1L, username, "x", true, true, java.util.List.of()),
                    "x", java.util.List.of()));
    }

}
