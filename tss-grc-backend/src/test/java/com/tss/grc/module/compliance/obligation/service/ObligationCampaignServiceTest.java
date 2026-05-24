package com.tss.grc.module.compliance.obligation.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.CampaignCloseRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.CampaignCreateRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.CampaignResponse;
import com.tss.grc.module.compliance.obligation.entity.Obligation;
import com.tss.grc.module.compliance.obligation.entity.ObligationAttestation;
import com.tss.grc.module.compliance.obligation.entity.ObligationAttestationCampaign;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationCampaignRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 11 — {@link ObligationCampaignService} tests.
 *
 * <p>Covers the campaign workflow (mirrors the BCP-attestation slice
 * pattern at @see VendorBcpCampaignServiceTest):
 * <ul>
 *   <li>Create → open materialises the correct subset (regulator /
 *       frequency filter)</li>
 *   <li>Reminders skip rows already attested</li>
 *   <li>Escalation creates F8 trigger + flips DUE / IN_PROGRESS past
 *       due_date to OVERDUE</li>
 *   <li>Close recomputes rollups</li>
 *   <li>State-machine guard enforces legal transitions</li>
 *   <li>F8 event emitted at every transition</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ObligationCampaignServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private ObligationAttestationCampaignRepository campaignRepo;
    @Mock private ObligationAttestationRepository attestationRepo;
    @Mock private ObligationRepository obligationRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private ObligationCampaignService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new ObligationCampaignService(campaignRepo, attestationRepo, obligationRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    private static CampaignCreateRequest validReq(String regulator) {
        return new CampaignCreateRequest(
            "OBLCAM-2026-Q2-SAMA",
            "Q2 2026 SAMA obligation attestation cycle",
            "دورة الإقرارات الربع سنوية لالتزامات مؤسسة النقد — الربع الثاني 2026",
            null, null,
            2026, "Q2",
            regulator, null,
            LocalDateTime.now().plusDays(45),
            7
        );
    }

    private static ObligationAttestationCampaign existing(String status, int invited) {
        ObligationAttestationCampaign c = new ObligationAttestationCampaign();
        c.setId(201L);
        c.setOrganisationId(ORG_ID);
        c.setExternalId(UUID.randomUUID());
        c.setCampaignCode("OBLCAM-2026-Q2-SAMA");
        c.setTitleEn("Q2 2026 SAMA obligation attestation cycle");
        c.setTitleAr("دورة الإقرارات الربع سنوية لالتزامات مؤسسة النقد — الربع الثاني 2026");
        c.setCycleYear(2026);
        c.setCycleQuarter("Q2");
        c.setRegulatorFilter("SAMA");
        c.setStatus(status);
        c.setClosesAt(LocalDateTime.now().plusDays(30));
        c.setReminderCadenceDays(7);
        c.setTotalInvited(invited);
        c.setTotalInProgress(0);
        c.setTotalAttested(0);
        c.setTotalAttestedWithExcs(0);
        c.setTotalOverdue(0);
        c.setTotalNa(0);
        return c;
    }

    private static Obligation obligation(Long id, String code, String regulator) {
        Obligation o = new Obligation();
        o.setId(id);
        o.setOrganisationId(ORG_ID);
        o.setObligationCode(code);
        o.setRegulator(regulator);
        o.setObligationType("REPORTING");
        o.setFrequency("MONTHLY");
        o.setSeverityIfMissed("HIGH");
        o.setLifecycleStage("ACTIVE");
        o.setNextDueDate(LocalDate.now().plusDays(30));
        o.setOwnerUserId(99L);
        o.setLeadTimeDays(7);
        return o;
    }

    // ─── Create ──────────────────────────────────────────────────────

    @Test void createCampaign_persistsDraftWithGivenCode() {
        when(campaignRepo.existsByOrganisationIdAndCampaignCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(false);
        when(campaignRepo.save(any(ObligationAttestationCampaign.class))).thenAnswer(inv -> {
            ObligationAttestationCampaign c = inv.getArgument(0);
            c.setId(201L);
            return c;
        });

        CampaignResponse out = service.createCampaign(validReq("SAMA"));

        assertThat(out.campaignCode()).isEqualTo("OBLCAM-2026-Q2-SAMA");
        assertThat(out.status()).isEqualTo("DRAFT");
        assertThat(out.regulatorFilter()).isEqualTo("SAMA");
        assertThat(out.titleAr()).contains("مؤسسة النقد");
    }

    @Test void createCampaign_duplicateCodeRejected() {
        when(campaignRepo.existsByOrganisationIdAndCampaignCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(true);
        assertThatThrownBy(() -> service.createCampaign(validReq("SAMA")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test void createCampaign_blankRegulatorFilterTreatedAsNull() {
        when(campaignRepo.existsByOrganisationIdAndCampaignCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(false);
        when(campaignRepo.save(any(ObligationAttestationCampaign.class))).thenAnswer(inv -> {
            ObligationAttestationCampaign c = inv.getArgument(0);
            c.setId(201L);
            return c;
        });

        CampaignResponse out = service.createCampaign(validReq(""));
        assertThat(out.regulatorFilter()).isNull();
    }

    // ─── Open ────────────────────────────────────────────────────────

    @Test void openCampaign_materialisesOneAttestationPerMatchingObligation() {
        ObligationAttestationCampaign c = existing("DRAFT", 0);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 201L)).thenReturn(Optional.of(c));
        when(obligationRepo.findByOrganisationIdAndLifecycleStageAndRegulatorAndIsDeletedFalse(
                ORG_ID, "ACTIVE", "SAMA"))
            .thenReturn(List.of(
                obligation(1001L, "OBL-SAMA-2026-001", "SAMA"),
                obligation(1002L, "OBL-SAMA-2026-002", "SAMA"),
                obligation(1003L, "OBL-SAMA-2026-003", "SAMA")));
        when(attestationRepo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.save(any(ObligationAttestationCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse out = service.openCampaign(201L);

        assertThat(out.status()).isEqualTo("OPEN");
        assertThat(out.totalInvited()).isEqualTo(3);
        verify(attestationRepo, times(3)).save(any(ObligationAttestation.class));
    }

    @Test void openCampaign_emptyTargetSetStillAdvancesStatus() {
        ObligationAttestationCampaign c = existing("DRAFT", 0);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 201L)).thenReturn(Optional.of(c));
        when(obligationRepo.findByOrganisationIdAndLifecycleStageAndRegulatorAndIsDeletedFalse(
                ORG_ID, "ACTIVE", "SAMA")).thenReturn(List.of());
        when(campaignRepo.save(any(ObligationAttestationCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse out = service.openCampaign(201L);

        assertThat(out.status()).isEqualTo("OPEN");
        assertThat(out.totalInvited()).isEqualTo(0);
        verify(attestationRepo, never()).save(any(ObligationAttestation.class));
    }

    @Test void openCampaign_blockedFromClosedState() {
        ObligationAttestationCampaign c = existing("CLOSED", 0);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 201L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.openCampaign(201L))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void openCampaign_emitsCampaignOpenedEvent() {
        ObligationAttestationCampaign c = existing("DRAFT", 0);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 201L)).thenReturn(Optional.of(c));
        when(obligationRepo.findByOrganisationIdAndLifecycleStageAndRegulatorAndIsDeletedFalse(
                ORG_ID, "ACTIVE", "SAMA"))
            .thenReturn(List.of(obligation(1001L, "OBL-SAMA-2026-001", "SAMA")));
        when(attestationRepo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.save(any(ObligationAttestationCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        service.openCampaign(201L);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("OBLIGATION_CAMPAIGN_OPENED");
        assertThat(ev.payload()).containsEntry("status", "OPEN");
        assertThat(ev.payload()).containsEntry("previousStatus", "DRAFT");
    }

    // ─── Reminders ───────────────────────────────────────────────────

    @Test void sendReminders_skipsAlreadyAttested() {
        ObligationAttestationCampaign c = existing("OPEN", 5);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 201L)).thenReturn(Optional.of(c));
        // Three rows in mixed state — only DUE / IN_PROGRESS should
        // count toward the reminder set. We rely on the repository's IN
        // filter to do that, so we mock it returning DUE + IN_PROGRESS
        // only; the service should not save any per-row mutation in v1.
        ObligationAttestation due = new ObligationAttestation();
        due.setId(501L); due.setOrganisationId(ORG_ID); due.setCampaignId(201L);
        due.setObligationId(1001L); due.setAttestationStatus("DUE");
        due.setDueDate(LocalDate.now().plusDays(10));
        ObligationAttestation inProg = new ObligationAttestation();
        inProg.setId(502L); inProg.setOrganisationId(ORG_ID); inProg.setCampaignId(201L);
        inProg.setObligationId(1002L); inProg.setAttestationStatus("IN_PROGRESS");
        inProg.setDueDate(LocalDate.now().plusDays(10));
        when(attestationRepo.findByOrganisationIdAndCampaignIdAndAttestationStatusInAndIsDeletedFalse(
                eq(ORG_ID), eq(201L), eq(List.of("DUE", "IN_PROGRESS"))))
            .thenReturn(List.of(due, inProg));
        when(campaignRepo.save(any(ObligationAttestationCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse out = service.sendReminders(201L);

        assertThat(out.status()).isEqualTo("IN_PROGRESS");
        // No per-row save in the reminder pass — service emits a single
        // F8 event with the count for the rule engine to use.
        verify(attestationRepo, never()).save(any(ObligationAttestation.class));
    }

    @Test void sendReminders_emitsRemindersEventWithCount() {
        ObligationAttestationCampaign c = existing("IN_PROGRESS", 5);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 201L)).thenReturn(Optional.of(c));
        when(attestationRepo.findByOrganisationIdAndCampaignIdAndAttestationStatusInAndIsDeletedFalse(
                anyLong(), anyLong(), anyList())).thenReturn(List.of());
        when(campaignRepo.save(any(ObligationAttestationCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sendReminders(201L);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().triggerEvent()).isEqualTo("OBLIGATION_CAMPAIGN_REMINDERS_SENT");
        assertThat(captor.getValue().payload()).containsKey("reminded");
    }

    @Test void sendReminders_rejectedFromTerminalCampaign() {
        ObligationAttestationCampaign c = existing("CLOSED", 3);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 201L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.sendReminders(201L))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── Escalation ──────────────────────────────────────────────────

    @Test void escalateOverdue_flipsOverdueRowsAndAdvancesCampaign() {
        ObligationAttestationCampaign c = existing("IN_PROGRESS", 4);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 201L)).thenReturn(Optional.of(c));
        List<ObligationAttestation> stale = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ObligationAttestation a = new ObligationAttestation();
            a.setId((long) i + 10);
            a.setOrganisationId(ORG_ID);
            a.setCampaignId(201L);
            a.setObligationId((long) i + 1001);
            a.setAttestationStatus("DUE");
            a.setDueDate(LocalDate.now().minusDays(5));
            stale.add(a);
        }
        when(attestationRepo.findByOrganisationIdAndCampaignIdAndAttestationStatusInAndDueDateBeforeAndIsDeletedFalse(
                eq(ORG_ID), eq(201L), eq(List.of("DUE", "IN_PROGRESS")), any()))
            .thenReturn(stale);
        when(attestationRepo.save(any(ObligationAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.save(any(ObligationAttestationCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse out = service.escalateOverdue(201L);

        assertThat(out.status()).isEqualTo("ESCALATED");
        assertThat(out.totalOverdue()).isEqualTo(2);
    }

    @Test void escalateOverdue_emitsAuditCommitteeTrigger() {
        ObligationAttestationCampaign c = existing("IN_PROGRESS", 3);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 201L)).thenReturn(Optional.of(c));
        when(attestationRepo.findByOrganisationIdAndCampaignIdAndAttestationStatusInAndDueDateBeforeAndIsDeletedFalse(
                anyLong(), anyLong(), anyList(), any())).thenReturn(List.of());
        when(campaignRepo.save(any(ObligationAttestationCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        service.escalateOverdue(201L);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().triggerEvent()).isEqualTo("OBLIGATION_CAMPAIGN_ESCALATED");
        assertThat(captor.getValue().payload()).containsKey("overdueCount");
    }

    // ─── Close ───────────────────────────────────────────────────────

    @Test void closeCampaign_recomputesRollupsFromAttestationCounts() {
        ObligationAttestationCampaign c = existing("IN_PROGRESS", 5);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 201L)).thenReturn(Optional.of(c));
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
                eq(ORG_ID), eq(201L), eq("IN_PROGRESS"))).thenReturn(0L);
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
                eq(ORG_ID), eq(201L), eq("ATTESTED"))).thenReturn(3L);
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
                eq(ORG_ID), eq(201L), eq("ATTESTED_WITH_EXCEPTIONS"))).thenReturn(1L);
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
                eq(ORG_ID), eq(201L), eq("OVERDUE"))).thenReturn(1L);
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
                eq(ORG_ID), eq(201L), eq("NOT_APPLICABLE"))).thenReturn(0L);
        when(campaignRepo.save(any(ObligationAttestationCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignCloseRequest req = new CampaignCloseRequest(
            "Q2 2026 closed; 4 of 5 attested, 1 overdue escalated to AC.",
            "تم إغلاق الربع الثاني 2026؛ أُقِرَّت 4 من 5، وصُعِّدت متأخرة واحدة إلى لجنة المراجعة.");
        CampaignResponse out = service.closeCampaign(201L, req);

        assertThat(out.status()).isEqualTo("CLOSED");
        assertThat(out.totalAttested()).isEqualTo(3);
        assertThat(out.totalAttestedWithExcs()).isEqualTo(1);
        assertThat(out.totalOverdue()).isEqualTo(1);
        assertThat(out.closingSummaryAr()).contains("تم إغلاق");
    }

    @Test void closeCampaign_blockedFromDraft() {
        ObligationAttestationCampaign c = existing("DRAFT", 0);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 201L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.closeCampaign(201L, null))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── Tenant isolation ────────────────────────────────────────────

    @Test void list_alwaysFiltersByOrganisation() {
        when(campaignRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID)).thenReturn(List.of());
        service.list();
        verify(campaignRepo, times(1)).findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID);
    }

    // ─── State machine guard ─────────────────────────────────────────

    @Test void guardTransition_drives() {
        ObligationCampaignService.guardTransition("DRAFT", "OPEN");
        ObligationCampaignService.guardTransition("DRAFT", "CANCELLED");
        assertThatThrownBy(() -> ObligationCampaignService.guardTransition("DRAFT", "CLOSED"))
            .isInstanceOf(IllegalTransitionException.class);
        ObligationCampaignService.guardTransition("OPEN", "IN_PROGRESS");
        ObligationCampaignService.guardTransition("IN_PROGRESS", "ESCALATED");
        assertThatThrownBy(() -> ObligationCampaignService.guardTransition("CLOSED", "OPEN"))
            .isInstanceOf(IllegalTransitionException.class);
    }
}
