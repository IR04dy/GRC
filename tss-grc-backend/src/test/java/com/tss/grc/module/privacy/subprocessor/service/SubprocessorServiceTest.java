package com.tss.grc.module.privacy.subprocessor.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.LifecycleTransitionRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.MaterialChangeRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorResponse;
import com.tss.grc.module.privacy.subprocessor.entity.Subprocessor;
import com.tss.grc.module.privacy.subprocessor.entity.SubprocessorEvent;
import com.tss.grc.module.privacy.subprocessor.repository.SubprocessorEventRepository;
import com.tss.grc.module.privacy.subprocessor.repository.SubprocessorRepository;
import jakarta.persistence.EntityNotFoundException;
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
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SubprocessorService} — Sprint 11 PDPL.
 *
 * <p>Locks:
 * <ul>
 *   <li>tenant stamping on CREATE + lifecycle defaults to PROPOSED</li>
 *   <li>all four {@code dpoApprove} gates (DPA, signed-date, purpose,
 *       cross-border safeguard if non-SA)</li>
 *   <li>{@code recordReview} math — bumps last_reviewed_at, computes
 *       next_review_due_at = now + 12 months</li>
 *   <li>{@code materialChange} forces lifecycle back to
 *       {@code UNDER_DPO_REVIEW}</li>
 *   <li>F8 event emission for the canonical triggers</li>
 *   <li>tenant isolation: cross-tenant get returns
 *       {@code EntityNotFoundException}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SubprocessorServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long SUB_ID = 42L;
    private static final UUID SUB_EX = UUID.randomUUID();

    @Mock private SubprocessorRepository repo;
    @Mock private SubprocessorEventRepository eventRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    private SubprocessorService service;

    @BeforeEach
    void setUp() {
        service = new SubprocessorService(repo, eventRepo, audit, eventPublisher);
        TenantContext.setCurrentTenant(ORG_ID);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ─────────────────────────────────────────────────

    private static Subprocessor sub(String stage, String country) {
        Subprocessor s = new Subprocessor();
        s.setId(SUB_ID);
        s.setExternalId(SUB_EX);
        s.setOrganisationId(ORG_ID);
        s.setSubprocessorCode("SUBP-2026-001");
        s.setSubProcessorLegalName("Microsoft Saudi Arabia");
        s.setSubProcessorLegalNameAr("مايكروسوفت العربية السعودية");
        s.setCountryOfProcessing(country);
        s.setProcessingPurposeEn("Cloud hosting for the core banking workload");
        s.setProcessingPurposeAr("استضافة سحابية لأعباء الأنظمة المصرفية");
        s.setLawfulBasis("CONTRACT");
        s.setAuthorisationType("GENERAL");
        s.setDataVolumeBand("HIGH");
        s.setHasDpaInPlace(true);
        s.setDpaSignedDate(LocalDate.now().minusDays(30));
        s.setLifecycleStage(stage);
        if ("SA".equals(country)) {
            s.setCrossBorderSafeguardType("KSA_RESIDENT");
        } else {
            s.setCrossBorderSafeguardType("SCC_PLUS_TIA");
        }
        return s;
    }

    private static SubprocessorRequest req() {
        return new SubprocessorRequest(
                null, null,
                "Microsoft Saudi Arabia",
                "مايكروسوفت العربية السعودية",
                null,
                "SA", "Riyadh",
                null, null,
                "Cloud hosting for the core banking workload",
                "استضافة سحابية لأعباء الأنظمة المصرفية",
                "CONTRACT", "GENERAL", "DPA-MS-2024-AZURE",
                "KSA_RESIDENT", null, null, null, null,
                "HIGH",
                true,
                LocalDate.now().minusDays(30),
                LocalDate.now().plusYears(3)
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> org.mockito.stubbing.Answer<T> assignId(AtomicLong seq) {
        return inv -> {
            Object arg = inv.getArgument(0);
            try {
                var getId = arg.getClass().getMethod("getId");
                if (getId.invoke(arg) == null) {
                    var setId = arg.getClass().getMethod("setId", Long.class);
                    setId.invoke(arg, seq.incrementAndGet());
                }
            } catch (Exception ignored) {}
            return (T) arg;
        };
    }

    // ─── Create ────────────────────────────────────────────────────

    @Test
    void create_stampsTenantAndDefaultsToProposed() {
        AtomicLong seq = new AtomicLong(100);
        when(repo.save(any(Subprocessor.class))).thenAnswer(assignId(seq));
        when(repo.countByOrganisationIdAndSubprocessorCode(eq(ORG_ID), anyString())).thenReturn(0L);
        when(eventRepo.save(any(SubprocessorEvent.class))).thenAnswer(assignId(new AtomicLong(200)));

        SubprocessorResponse out = service.create(req());

        assertThat(out.lifecycleStage()).isEqualTo("PROPOSED");
        assertThat(out.subprocessorCode()).startsWith("SUBP-");
        assertThat(out.countryOfProcessing()).isEqualTo("SA");

        ArgumentCaptor<Subprocessor> cap = ArgumentCaptor.forClass(Subprocessor.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getOrganisationId()).isEqualTo(ORG_ID);
        verify(audit).log(eq("CREATE"), eq("Subprocessor"), anyLong(), anyString());
    }

    @Test
    void create_emitsSubprocessorAddedEvent() {
        AtomicLong seq = new AtomicLong(100);
        when(repo.save(any(Subprocessor.class))).thenAnswer(assignId(seq));
        when(repo.countByOrganisationIdAndSubprocessorCode(eq(ORG_ID), anyString())).thenReturn(0L);
        when(eventRepo.save(any(SubprocessorEvent.class))).thenAnswer(assignId(new AtomicLong(200)));

        service.create(req());

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        DomainEvent ev = cap.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("SUBPROCESSOR_ADDED");
        assertThat(ev.entityType()).isEqualTo("Subprocessor");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.payload()).containsEntry("countryOfProcessing", "SA");
        assertThat(ev.payload()).containsEntry("lifecycleStage", "PROPOSED");
    }

    @Test
    void create_seedsAddedEventOnLedger() {
        AtomicLong seq = new AtomicLong(100);
        when(repo.save(any(Subprocessor.class))).thenAnswer(assignId(seq));
        when(repo.countByOrganisationIdAndSubprocessorCode(eq(ORG_ID), anyString())).thenReturn(0L);
        when(eventRepo.save(any(SubprocessorEvent.class))).thenAnswer(assignId(new AtomicLong(200)));

        service.create(req());

        ArgumentCaptor<SubprocessorEvent> cap = ArgumentCaptor.forClass(SubprocessorEvent.class);
        verify(eventRepo).save(cap.capture());
        assertThat(cap.getValue().getEventType()).isEqualTo("ADDED");
        assertThat(cap.getValue().getOrganisationId()).isEqualTo(ORG_ID);
    }

    // ─── DPO approval gates ──────────────────────────────────────

    @Test
    void dpoApprove_missingDpaFlag_throwsWithMissingKey() {
        Subprocessor s = sub("UNDER_DPO_REVIEW", "SA");
        s.setHasDpaInPlace(false);
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.dpoApprove(SUB_EX, 99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hasDpaInPlace");
    }

    @Test
    void dpoApprove_missingDpaSignedDate_throwsWithMissingKey() {
        Subprocessor s = sub("UNDER_DPO_REVIEW", "SA");
        s.setDpaSignedDate(null);
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.dpoApprove(SUB_EX, 99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dpaSignedDate");
    }

    @Test
    void dpoApprove_missingPurpose_throwsWithMissingKey() {
        Subprocessor s = sub("UNDER_DPO_REVIEW", "SA");
        s.setProcessingPurposeEn("");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.dpoApprove(SUB_EX, 99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("processingPurposeEn");
    }

    @Test
    void dpoApprove_nonKsaWithoutSafeguard_throwsWithMissingKey() {
        Subprocessor s = sub("UNDER_DPO_REVIEW", "US");
        s.setCrossBorderSafeguardType(null);
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.dpoApprove(SUB_EX, 99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("crossBorderSafeguardType");
    }

    @Test
    void dpoApprove_listsAllMissingKeys() {
        // Subprocessor with multiple gaps — verify the message lists all of them.
        Subprocessor s = sub("UNDER_DPO_REVIEW", "US");
        s.setHasDpaInPlace(false);
        s.setDpaSignedDate(null);
        s.setCrossBorderSafeguardType(null);
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.dpoApprove(SUB_EX, 99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hasDpaInPlace")
                .hasMessageContaining("dpaSignedDate")
                .hasMessageContaining("crossBorderSafeguardType");
    }

    @Test
    void dpoApprove_allGatesPassed_setsApprovedAndSeedsReviewWindow() {
        Subprocessor s = sub("UNDER_DPO_REVIEW", "SA");
        s.setLastReviewedAt(null);
        s.setNextReviewDueAt(null);
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));
        when(repo.save(any(Subprocessor.class))).thenAnswer(inv -> inv.getArgument(0));

        SubprocessorResponse out = service.dpoApprove(SUB_EX, 99L);

        assertThat(out.lifecycleStage()).isEqualTo("APPROVED");
        assertThat(out.dpoApprovedByUserId()).isEqualTo(99L);
        assertThat(out.dpoApprovedAt()).isNotNull();
        assertThat(out.lastReviewedAt()).isNotNull();
        // 12-month review cadence — next due ≈ now + 12 months.
        long monthsBetween = ChronoUnit.MONTHS.between(out.lastReviewedAt(), out.nextReviewDueAt());
        assertThat(monthsBetween).isEqualTo(12);

        verify(audit).log(eq("DPO_APPROVE"), eq("Subprocessor"), anyLong(), anyString());
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("SUBPROCESSOR_LIFECYCLE_TRANSITION");
    }

    @Test
    void dpoApprove_ksaResidentRowDoesNotRequireSafeguardType() {
        // SA country, no cross_border_safeguard_type → should still approve cleanly.
        Subprocessor s = sub("UNDER_DPO_REVIEW", "SA");
        s.setCrossBorderSafeguardType(null);
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));
        when(repo.save(any(Subprocessor.class))).thenAnswer(inv -> inv.getArgument(0));

        SubprocessorResponse out = service.dpoApprove(SUB_EX, 99L);
        assertThat(out.lifecycleStage()).isEqualTo("APPROVED");
    }

    // ─── Review math ──────────────────────────────────────────────

    @Test
    void recordReview_bumpsLastReviewAndPlus12MonthsNextDue() {
        Subprocessor s = sub("APPROVED", "SA");
        LocalDateTime stale = LocalDateTime.now().minusYears(1).minusDays(5);
        s.setLastReviewedAt(stale);
        s.setNextReviewDueAt(stale.plusMonths(12));
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));
        when(repo.save(any(Subprocessor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventRepo.save(any(SubprocessorEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        SubprocessorResponse out = service.recordReview(SUB_EX, 77L);

        assertThat(out.lastReviewedAt()).isAfter(stale);
        assertThat(ChronoUnit.MONTHS.between(out.lastReviewedAt(), out.nextReviewDueAt()))
                .isEqualTo(12);

        verify(audit).log(eq("REVIEW_COMPLETED"), eq("Subprocessor"), anyLong(), anyString());
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("SUBPROCESSOR_REVIEWED");
    }

    // ─── Material change ──────────────────────────────────────────

    @Test
    void materialChange_forcesBackToUnderDpoReview() {
        Subprocessor s = sub("APPROVED", "SA");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));
        when(repo.save(any(Subprocessor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventRepo.save(any(SubprocessorEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        SubprocessorResponse out = service.materialChange(SUB_EX,
                new MaterialChangeRequest("Vendor changed sub-region to Frankfurt", "تغيير المعالج الفرعي للمنطقة لفرانكفورت"));

        assertThat(out.lifecycleStage()).isEqualTo("UNDER_DPO_REVIEW");
        verify(audit).log(eq("MATERIAL_CHANGE"), eq("Subprocessor"), anyLong(), anyString());

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("SUBPROCESSOR_MATERIAL_CHANGE");
        assertThat(cap.getValue().payload()).containsEntry("previousLifecycleStage", "APPROVED");
    }

    @Test
    void materialChange_terminalRetiredRowDoesNotResurrect() {
        Subprocessor s = sub("RETIRED", "SA");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));
        when(repo.save(any(Subprocessor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventRepo.save(any(SubprocessorEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        SubprocessorResponse out = service.materialChange(SUB_EX,
                new MaterialChangeRequest("Historic correction", null));
        assertThat(out.lifecycleStage()).isEqualTo("RETIRED");
    }

    // ─── Lifecycle transitions ────────────────────────────────────

    @Test
    void transition_approvedToSuspended_ok() {
        Subprocessor s = sub("APPROVED", "SA");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));
        when(repo.save(any(Subprocessor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventRepo.save(any(SubprocessorEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        SubprocessorResponse out = service.transitionLifecycle(SUB_EX,
                new LifecycleTransitionRequest("SUSPENDED", "Vendor breach in another tenant"));

        assertThat(out.lifecycleStage()).isEqualTo("SUSPENDED");
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("SUBPROCESSOR_LIFECYCLE_TRANSITION");
    }

    @Test
    void transition_retiredToApproved_rejected() {
        Subprocessor s = sub("RETIRED", "SA");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.transitionLifecycle(SUB_EX,
                new LifecycleTransitionRequest("APPROVED", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETIRED → APPROVED");
    }

    // ─── Update cross-border safeguard change ────────────────────

    @Test
    void update_safeguardChange_emitsCrossBorderChangedEvent() {
        Subprocessor s = sub("APPROVED", "US");
        s.setCrossBorderSafeguardType("SCC_PLUS_TIA");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));
        when(repo.save(any(Subprocessor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventRepo.save(any(SubprocessorEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        SubprocessorRequest r = new SubprocessorRequest(
                null, null,
                s.getSubProcessorLegalName(), s.getSubProcessorLegalNameAr(),
                null, "US", "Texas",
                null, null,
                s.getProcessingPurposeEn(), s.getProcessingPurposeAr(),
                s.getLawfulBasis(),
                s.getAuthorisationType(), null,
                "ADEQUACY_DECISION", null, null, null,
                "SDAIA-AD-2024-007",
                s.getDataVolumeBand(),
                s.getHasDpaInPlace(), s.getDpaSignedDate(), null
        );
        service.update(SUB_EX, r);

        // Two events fire: UPDATE (no F8 from it) and CROSS_BORDER_CHANGED (F8).
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues())
                .extracting(DomainEvent::triggerEvent)
                .contains("SUBPROCESSOR_CROSS_BORDER_CHANGED");
        verify(audit).log(eq("CROSS_BORDER_CHANGE"), eq("Subprocessor"), anyLong(), anyString());
    }

    @Test
    void update_noSafeguardChange_doesNotEmitCrossBorderEvent() {
        Subprocessor s = sub("APPROVED", "US");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(s));
        when(repo.save(any(Subprocessor.class))).thenAnswer(inv -> inv.getArgument(0));

        SubprocessorRequest r = new SubprocessorRequest(
                null, null,
                s.getSubProcessorLegalName(), s.getSubProcessorLegalNameAr(),
                null, "US", "California",  // region change only
                null, null,
                s.getProcessingPurposeEn(), s.getProcessingPurposeAr(),
                s.getLawfulBasis(),
                s.getAuthorisationType(), null,
                s.getCrossBorderSafeguardType(), null, null, null, null,
                s.getDataVolumeBand(),
                s.getHasDpaInPlace(), s.getDpaSignedDate(), null
        );
        service.update(SUB_EX, r);

        verify(audit, never()).log(eq("CROSS_BORDER_CHANGE"), eq("Subprocessor"), anyLong(), anyString());
    }

    // ─── Tenant isolation ────────────────────────────────────────

    @Test
    void get_otherTenant_returnsNotFound() {
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(SUB_EX))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
