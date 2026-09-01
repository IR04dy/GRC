package com.tss.grc.module.risk.bowtie.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.ApproveRequest;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieAnalysisRequest;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieAnalysisResponse;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieConsequenceDto;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieFullResponse;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTiePreventiveControlDto;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieThreatDto;
import com.tss.grc.module.risk.bowtie.entity.BowTieAnalysis;
import com.tss.grc.module.risk.bowtie.entity.BowTieConsequence;
import com.tss.grc.module.risk.bowtie.entity.BowTieMitigatingControl;
import com.tss.grc.module.risk.bowtie.entity.BowTiePreventiveControl;
import com.tss.grc.module.risk.bowtie.entity.BowTieThreat;
import com.tss.grc.module.risk.bowtie.repository.BowTieAnalysisRepository;
import com.tss.grc.module.risk.bowtie.repository.BowTieConsequenceRepository;
import com.tss.grc.module.risk.bowtie.repository.BowTieMitigatingControlRepository;
import com.tss.grc.module.risk.bowtie.repository.BowTiePreventiveControlRepository;
import com.tss.grc.module.risk.bowtie.repository.BowTieThreatRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BowTieAnalysisService — Sprint 11 P1 GRC test backlog.
 *
 * Locks the ISO 31010 bow-tie state machine, computation, tenant scope,
 * and F8 event emission contract:
 *
 *   DRAFT ──submit()──▶ IN_REVIEW ──approve()──▶ APPROVED
 *                                  └─archive()──▶ ARCHIVED (soft-delete)
 *   (any state) ──archive()──▶ ARCHIVED
 */
@ExtendWith(MockitoExtension.class)
class BowTieAnalysisServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final UUID BOWTIE_EX = UUID.randomUUID();
    private static final Long BOWTIE_ID = 42L;

    @Mock private BowTieAnalysisRepository bowtieRepo;
    @Mock private BowTieThreatRepository threatRepo;
    @Mock private BowTieConsequenceRepository consequenceRepo;
    @Mock private BowTiePreventiveControlRepository preventiveRepo;
    @Mock private BowTieMitigatingControlRepository mitigatingRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private BowTieAnalysisService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Helpers ──────────────────────────────────────────────────

    private static BowTieAnalysis bowtie(String status) { return bowtie(status, ORG_ID); }

    private static BowTieAnalysis bowtie(String status, Long orgId) {
        BowTieAnalysis b = new BowTieAnalysis();
        b.setId(BOWTIE_ID);
        b.setExternalId(BOWTIE_EX);
        b.setOrganisationId(orgId);
        b.setBowtieCode("BOWTIE-2026-001");
        b.setTopEventTitleEn("Material customer data exfiltration");
        b.setAssessmentDate(LocalDate.now());
        b.setStatus(status);
        return b;
    }

    private static BowTieAnalysisRequest req() {
        return new BowTieAnalysisRequest(
                null, null,
                "Top event", "أعلى حدث",
                null, null,
                "CYBER",
                LocalDate.now(),
                "CISO",
                "ISO_31010",
                null,
                // F2-413: preventiveControls/mitigatingControls are gone from the request — no code
                // read them and the frontend never sent them. Controls go through their own routes.
                null, null
        );
    }

    private static BowTieAnalysisRequest reqWithChildren() {
        return new BowTieAnalysisRequest(
                null, null,
                "Top event", "أعلى حدث",
                null, null,
                "CYBER",
                LocalDate.now(),
                "CISO",
                "ISO_31010",
                null,
                List.of(threatDto("Insider", 1), threatDto("Ransomware", 2)),
                List.of(consequenceDto("SDAIA fine", 1), consequenceDto("Customer churn", 2))
        );
    }

    private static BowTieThreatDto threatDto(String title, int order) {
        return new BowTieThreatDto(null, null, null, order,
                title, null, null, null,
                "LIKELY", "UNLIKELY", "External actor");
    }

    private static BowTieConsequenceDto consequenceDto(String title, int order) {
        return new BowTieConsequenceDto(null, null, null, order,
                title, null, null, null,
                "REGULATORY", "MAJOR", "MODERATE", null);
    }

    private static BowTieThreat threatEntity(Long id, Long bowtieId, String residualLikelihood) {
        BowTieThreat t = new BowTieThreat();
        t.setId(id);
        t.setExternalId(UUID.randomUUID());
        t.setOrganisationId(ORG_ID);
        t.setBowtieId(bowtieId);
        t.setTitleEn("Threat " + id);
        t.setResidualLikelihood(residualLikelihood);
        return t;
    }

    private static BowTieConsequence consequenceEntity(Long id, Long bowtieId, String residualSeverity) {
        BowTieConsequence c = new BowTieConsequence();
        c.setId(id);
        c.setExternalId(UUID.randomUUID());
        c.setOrganisationId(ORG_ID);
        c.setBowtieId(bowtieId);
        c.setTitleEn("Consequence " + id);
        c.setResidualSeverity(residualSeverity);
        return c;
    }

    private static BowTiePreventiveControl preventiveEntity(Long id, Long bowtieId, Long threatId, String effectiveness) {
        BowTiePreventiveControl c = new BowTiePreventiveControl();
        c.setId(id);
        c.setExternalId(UUID.randomUUID());
        c.setOrganisationId(ORG_ID);
        c.setBowtieId(bowtieId);
        c.setThreatId(threatId);
        c.setTitleEn("Preventive " + id);
        c.setEffectiveness(effectiveness);
        return c;
    }

    private static BowTieMitigatingControl mitigatingEntity(Long id, Long bowtieId, Long consId, String effectiveness) {
        BowTieMitigatingControl c = new BowTieMitigatingControl();
        c.setId(id);
        c.setExternalId(UUID.randomUUID());
        c.setOrganisationId(ORG_ID);
        c.setBowtieId(bowtieId);
        c.setConsequenceId(consId);
        c.setTitleEn("Mitigating " + id);
        c.setEffectiveness(effectiveness);
        return c;
    }

    // Mutates input as save() does — assigns id + returns same instance.
    private static <T> org.mockito.stubbing.Answer<T> assignId(AtomicLong seq) {
        return inv -> {
            Object arg = inv.getArgument(0);
            try {
                var m = arg.getClass().getMethod("getId");
                if (m.invoke(arg) == null) {
                    var setter = arg.getClass().getMethod("setId", Long.class);
                    setter.invoke(arg, seq.incrementAndGet());
                }
            } catch (Exception ignored) {}
            @SuppressWarnings("unchecked") T t = (T) arg;
            return t;
        };
    }

    // ─── Create / update / cascade ────────────────────────────────

    @Test
    void create_stampsOrganisationAndDefaults() {
        AtomicLong seq = new AtomicLong(100);
        when(bowtieRepo.save(any(BowTieAnalysis.class))).thenAnswer(assignId(seq));

        BowTieAnalysisResponse out = service.create(req());

        assertThat(out.status()).isEqualTo("DRAFT");
        assertThat(out.bowtieCode()).startsWith("BOWTIE-");

        ArgumentCaptor<BowTieAnalysis> cap = ArgumentCaptor.forClass(BowTieAnalysis.class);
        verify(bowtieRepo).save(cap.capture());
        BowTieAnalysis saved = cap.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getMethodology()).isEqualTo("ISO_31010");
        assertThat(saved.getAssessmentDate()).isNotNull();
        verify(audit).log(eq("CREATE"), eq("BowTieAnalysis"), anyLong(), anyString());
    }

    @Test
    void create_publishesBowtieCreatedEvent() {
        AtomicLong seq = new AtomicLong(100);
        when(bowtieRepo.save(any(BowTieAnalysis.class))).thenAnswer(assignId(seq));

        service.create(req());

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        DomainEvent ev = cap.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("BOWTIE_CREATED");
        assertThat(ev.entityType()).isEqualTo("BowTieAnalysis");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.payload()).containsKey("bowtieCode");
        assertThat(ev.payload()).containsEntry("status", "DRAFT");
        assertThat(ev.payload()).containsEntry("riskCategory", "CYBER");
    }

    @Test
    void create_cascadesChildren() {
        AtomicLong seq = new AtomicLong(100);
        when(bowtieRepo.save(any(BowTieAnalysis.class))).thenAnswer(assignId(seq));
        when(threatRepo.save(any(BowTieThreat.class))).thenAnswer(assignId(seq));
        when(consequenceRepo.save(any(BowTieConsequence.class))).thenAnswer(assignId(seq));

        service.create(reqWithChildren());

        // 2 threats + 2 consequences should have been persisted.
        verify(threatRepo, org.mockito.Mockito.times(2)).save(any(BowTieThreat.class));
        verify(consequenceRepo, org.mockito.Mockito.times(2)).save(any(BowTieConsequence.class));
    }

    @Test
    void update_replacesHeaderFieldsAndPublishesUpdated() {
        BowTieAnalysis existing = bowtie("DRAFT");
        when(bowtieRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BOWTIE_EX))
                .thenReturn(Optional.of(existing));
        when(bowtieRepo.save(any(BowTieAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        BowTieAnalysisRequest changed = new BowTieAnalysisRequest(
                null, 17L,
                "Updated title", null,
                "Updated description", null,
                "OPERATIONAL",
                null, null, null, null,
                null, null
        );

        BowTieAnalysisResponse out = service.update(BOWTIE_EX, changed);

        assertThat(out.topEventTitleEn()).isEqualTo("Updated title");
        assertThat(out.linkedRiskId()).isEqualTo(17L);
        assertThat(out.riskCategory()).isEqualTo("OPERATIONAL");

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("BOWTIE_UPDATED");
    }

    @Test
    void update_rejectsOnApprovedStatus() {
        BowTieAnalysis approved = bowtie("APPROVED");
        when(bowtieRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BOWTIE_EX))
                .thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.update(BOWTIE_EX, req()))
                .isInstanceOf(IllegalTransitionException.class);

        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ─── Full detail loads children ───────────────────────────────

    @Test
    void getFull_loadsHeaderAndAllChildren() {
        BowTieAnalysis b = bowtie("APPROVED");
        BowTieThreat t1 = threatEntity(11L, BOWTIE_ID, "POSSIBLE");
        BowTieConsequence c1 = consequenceEntity(21L, BOWTIE_ID, "MAJOR");
        BowTiePreventiveControl p1 = preventiveEntity(31L, BOWTIE_ID, t1.getId(), "HIGH");
        BowTieMitigatingControl m1 = mitigatingEntity(41L, BOWTIE_ID, c1.getId(), "MEDIUM");

        when(bowtieRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BOWTIE_EX))
                .thenReturn(Optional.of(b));
        when(threatRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByThreatOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(t1));
        when(consequenceRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByConsequenceOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(c1));
        when(preventiveRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(p1));
        when(mitigatingRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(m1));

        BowTieFullResponse out = service.getFull(BOWTIE_EX);

        assertThat(out.header().bowtieCode()).isEqualTo("BOWTIE-2026-001");
        assertThat(out.threats()).hasSize(1);
        assertThat(out.consequences()).hasSize(1);
        assertThat(out.preventiveControls()).hasSize(1);
        // Preventive control DTO must carry the threat's external UUID so
        // the SVG renderer can key the control to its parent without
        // round-tripping ids.
        assertThat(out.preventiveControls().get(0).threatExternalId()).isEqualTo(t1.getExternalId());
        assertThat(out.mitigatingControls()).hasSize(1);
        assertThat(out.mitigatingControls().get(0).consequenceExternalId()).isEqualTo(c1.getExternalId());
    }

    // ─── Effectiveness computation ────────────────────────────────

    @Test
    void recomputeOverallEffectiveness_blendsPreventiveAndMitigating() {
        BowTieAnalysis b = bowtie("APPROVED");
        // Preventive avg: (90 + 30) / 2 = 60
        // Mitigating avg: 70
        // Blended = (60 + 70) / 2 = 65
        when(preventiveRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(
                        preventiveEntity(1L, BOWTIE_ID, 11L, "VERY_HIGH"),
                        preventiveEntity(2L, BOWTIE_ID, 11L, "LOW")));
        when(mitigatingRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(mitigatingEntity(3L, BOWTIE_ID, 21L, "HIGH")));

        service.recomputeOverallEffectiveness(b);

        assertThat(b.getOverallControlEffectiveness()).isNotNull();
        assertThat(b.getOverallControlEffectiveness().doubleValue()).isEqualTo(65.00);
    }

    @Test
    void recomputeOverallEffectiveness_handlesOneEmptySide() {
        BowTieAnalysis b = bowtie("APPROVED");
        // Only preventive controls; mitigating side empty — should not be
        // dragged to zero by the missing side.
        when(preventiveRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(preventiveEntity(1L, BOWTIE_ID, 11L, "HIGH")));
        when(mitigatingRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(new ArrayList<>());

        service.recomputeOverallEffectiveness(b);

        assertThat(b.getOverallControlEffectiveness().doubleValue()).isEqualTo(70.00);
    }

    @Test
    void recomputeOverallEffectiveness_emptyBowtieScoresZero() {
        BowTieAnalysis b = bowtie("APPROVED");
        when(preventiveRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(new ArrayList<>());
        when(mitigatingRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(new ArrayList<>());

        service.recomputeOverallEffectiveness(b);

        assertThat(b.getOverallControlEffectiveness().doubleValue()).isEqualTo(0.00);
    }

    // ─── Residual rating inference ────────────────────────────────

    @Test
    void inferResidualRating_worstCaseGivesCRITICAL() {
        BowTieAnalysis b = bowtie("APPROVED");
        when(threatRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByThreatOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(threatEntity(1L, BOWTIE_ID, "ALMOST_CERTAIN"))); // 5
        when(consequenceRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByConsequenceOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(consequenceEntity(2L, BOWTIE_ID, "CATASTROPHIC"))); // 5 → 25

        assertThat(service.inferResidualRating(b)).isEqualTo("CRITICAL");
    }

    @Test
    void inferResidualRating_bestCaseGivesLOW() {
        BowTieAnalysis b = bowtie("APPROVED");
        when(threatRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByThreatOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(threatEntity(1L, BOWTIE_ID, "RARE")));         // 1
        when(consequenceRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByConsequenceOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(consequenceEntity(2L, BOWTIE_ID, "MINOR")));   // 2 → 2

        assertThat(service.inferResidualRating(b)).isEqualTo("LOW");
    }

    @Test
    void inferResidualRating_picksWorstAcrossMultipleEntries() {
        BowTieAnalysis b = bowtie("APPROVED");
        when(threatRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByThreatOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(
                        threatEntity(1L, BOWTIE_ID, "RARE"),
                        threatEntity(2L, BOWTIE_ID, "LIKELY")));     // worst = 4
        when(consequenceRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByConsequenceOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(
                        consequenceEntity(3L, BOWTIE_ID, "MINOR"),
                        consequenceEntity(4L, BOWTIE_ID, "MAJOR"))); // worst = 4 → 16

        assertThat(service.inferResidualRating(b)).isEqualTo("CRITICAL");
    }

    @Test
    void inferResidualRating_returnsNullWhenNoRatingsPresent() {
        BowTieAnalysis b = bowtie("APPROVED");
        when(threatRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByThreatOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(threatEntity(1L, BOWTIE_ID, null)));
        when(consequenceRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByConsequenceOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(consequenceEntity(2L, BOWTIE_ID, null)));

        assertThat(service.inferResidualRating(b)).isNull();
    }

    // ─── Lifecycle transitions ───────────────────────────────────

    @Test
    void submitForReview_DRAFT_to_IN_REVIEW_emitsSubmittedEvent() {
        BowTieAnalysis b = bowtie("DRAFT");
        when(bowtieRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BOWTIE_EX))
                .thenReturn(Optional.of(b));
        when(bowtieRepo.save(any(BowTieAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        BowTieAnalysisResponse out = service.submitForReview(BOWTIE_EX);

        assertThat(out.status()).isEqualTo("IN_REVIEW");
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("BOWTIE_SUBMITTED");
        assertThat(cap.getValue().payload()).containsEntry("previousStatus", "DRAFT");
    }

    @Test
    void cannotApproveFromDRAFT() {
        BowTieAnalysis b = bowtie("DRAFT");
        when(bowtieRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BOWTIE_EX))
                .thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.approve(BOWTIE_EX, new ApproveRequest()))
                .isInstanceOf(IllegalTransitionException.class);

        // Bad-state guard must not fire an F8 event or write audit (mirrors
        // the centralised guard pattern in RiskService PR #50).
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
        verify(bowtieRepo, never()).save(any(BowTieAnalysis.class));
    }

    @Test
    void approve_IN_REVIEW_to_APPROVED_recomputesEffectivenessAndResidual() {
        BowTieAnalysis b = bowtie("IN_REVIEW");
        when(bowtieRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BOWTIE_EX))
                .thenReturn(Optional.of(b));
        when(bowtieRepo.save(any(BowTieAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));
        // Provide one preventive HIGH (70) and one mitigating HIGH (70) → blended 70.
        when(preventiveRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(preventiveEntity(1L, BOWTIE_ID, 11L, "HIGH")));
        when(mitigatingRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(mitigatingEntity(2L, BOWTIE_ID, 21L, "HIGH")));
        // Provide threats / consequences for residual derivation.
        when(threatRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByThreatOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(threatEntity(11L, BOWTIE_ID, "POSSIBLE")));     // 3
        when(consequenceRepo.findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByConsequenceOrderAsc(ORG_ID, BOWTIE_ID))
                .thenReturn(List.of(consequenceEntity(21L, BOWTIE_ID, "MODERATE"))); // 3 → 9 = MEDIUM

        f223AuthAs("Group CRO", 3L);
        BowTieAnalysisResponse out = service.approve(BOWTIE_EX, new ApproveRequest());

        assertThat(out.status()).isEqualTo("APPROVED");
        assertThat(out.approvedAt()).isNotNull();
        assertThat(out.approvedByName()).isEqualTo("Group CRO");
        assertThat(out.overallControlEffectiveness().doubleValue()).isEqualTo(70.00);
        assertThat(out.overallResidualRating()).isEqualTo("MEDIUM");

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("BOWTIE_APPROVED");
    }

    @Test
    void archive_softDeletesAndEmitsArchivedEvent() {
        BowTieAnalysis b = bowtie("APPROVED");
        when(bowtieRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BOWTIE_EX))
                .thenReturn(Optional.of(b));
        when(bowtieRepo.save(any(BowTieAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        service.archive(BOWTIE_EX);

        assertThat(b.getIsDeleted()).isTrue();
        assertThat(b.getStatus()).isEqualTo("ARCHIVED");
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("BOWTIE_ARCHIVED");
    }

    // ─── Tenant isolation ────────────────────────────────────────

    @Test
    void getFull_otherTenantThrowsEntityNotFound() {
        // mustGet() uses the repository finder that filters on organisation_id —
        // a tenant-mismatched row simply isn't returned, so the path yields a
        // 404 / EntityNotFound rather than leaking the row across tenants.
        when(bowtieRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BOWTIE_EX))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFull(BOWTIE_EX))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void approve_otherTenantDoesNotEmitEvent() {
        when(bowtieRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BOWTIE_EX))
                .thenReturn(Optional.empty());   // simulating no row visible to ORG_ID

        assertThatThrownBy(() -> service.approve(BOWTIE_EX, new ApproveRequest()))
                .isInstanceOf(EntityNotFoundException.class);

        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ─── Soft-delete behaviour ───────────────────────────────────

    @Test
    void archive_marksHeaderSoftDeleted() {
        BowTieAnalysis b = bowtie("DRAFT");
        when(bowtieRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BOWTIE_EX))
                .thenReturn(Optional.of(b));
        when(bowtieRepo.save(any(BowTieAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        service.archive(BOWTIE_EX);

        assertThat(b.getIsDeleted()).isTrue();
        assertThat(b.getDeletedAt()).isNotNull();
        verify(bowtieRepo).save(b);
    }

    /**
     * F2-223 — a real principal in the SecurityContext, because the service now takes the actor
     * from {@code CurrentActor} rather than from the request. {@code CurrentActor} requires the
     * principal to be a {@link com.tss.grc.security.GrcUserDetails}, so a bare username token
     * would resolve to null and the assertion would be about nothing.
     */
    private static void f223AuthAs(String username, Long userId) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(
                    new com.tss.grc.security.GrcUserDetails(
                        userId, 1L, username, "x", true, true, java.util.List.of()),
                    "x", java.util.List.of()));
    }
}
