package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.dto.RiskRequest;
import com.tss.grc.module.risk.dto.RiskResponse;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskCategory;
import com.tss.grc.module.risk.entity.RiskAppetiteStatement;
import com.tss.grc.module.risk.repository.RiskAcceptanceRepository;
import com.tss.grc.module.risk.repository.RiskAppetiteStatementRepository;
import com.tss.grc.module.risk.repository.RiskCategoryRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.risk_lc.repository.TreatmentPlanRepository;
import jakarta.persistence.EntityNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RiskService — Sprint 4 §4.1 test backlog.
 *
 * Locks the ISO 31000 / COSO ERM lifecycle state machine and tenant-scope rules:
 *
 *   OPEN ─────► UNDER_REVIEW ─────► MITIGATING ─────► CLOSED
 *     │              │
 *     └──────────────┴────► ACCEPTED ─────► CLOSED
 *                                              │
 *                                              ▼
 *                                            OPEN  (reopen on recurrence)
 *
 * Plus the incident-feedback loop: bumpLikelihoodFromIncident must
 * increment residualLikelihood by 1 capped at 5.
 */
@ExtendWith(MockitoExtension.class)
class RiskServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long RISK_ID   = 17L;

    @Mock private RiskRepository riskRepository;
    @Mock private RiskCategoryRepository riskCategoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TreatmentPlanRepository treatmentPlanRepository;
    @Mock private RiskAppetiteStatementRepository appetiteRepository;
    /** F2-164 — the close gate's exemption now reads this, not `status`. */
    @Mock private RiskAcceptanceRepository acceptanceRepository;
    @InjectMocks private RiskService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Risk risk(String status, Long orgId) {
        Risk r = new Risk();
        r.setId(RISK_ID);
        r.setOrganisationId(orgId);
        r.setTitleEn("Sample risk");
        r.setStatus(status);
        return r;
    }

    private static Risk risk(String status) { return risk(status, ORG_ID); }

    private static RiskRequest req() {
        return new RiskRequest(
            "Title EN", "العنوان", "desc", "الوصف", null, null,
            3, 4, 2, 3, null, null
        );
    }

    private static RiskCategory cat(long id, String nameEn) {
        RiskCategory c = new RiskCategory();
        c.setId(id); c.setNameEn(nameEn); c.setNameAr(nameEn + " (ar)");
        return c;
    }

    private static User owner(long id, String fullName, String username) {
        User u = new User();
        u.setId(id); u.setFullNameEn(fullName); u.setUsername(username);
        return u;
    }

    // ─── PERF-NQ-001: list() batch-resolves FK display fields ─────────

    @Test
    void list_batchResolvesCategoryAndOwner_noPerRowFindById() {
        Risk r1 = risk("OPEN"); r1.setId(1L); r1.setCategoryId(10L); r1.setOwnerId(100L);
        Risk r2 = risk("OPEN"); r2.setId(2L); r2.setCategoryId(11L); r2.setOwnerId(100L);
        Risk r3 = risk("OPEN"); r3.setId(3L); r3.setCategoryId(10L); r3.setOwnerId(101L);
        PageRequest pageable = PageRequest.of(0, 50);
        when(riskRepository.findByOrganisationId(ORG_ID, pageable))
            .thenReturn(new PageImpl<>(List.of(r1, r2, r3)));
        when(riskCategoryRepository.findAllById(anySet()))
            .thenReturn(List.of(cat(10L, "Operational"), cat(11L, "Cyber")));
        when(userRepository.findAllById(anySet()))
            .thenReturn(List.of(owner(100L, "Alice", "alice"), owner(101L, "Bob", "bob")));

        Page<RiskResponse> out = service.list(null, pageable);

        assertThat(out.getContent()).hasSize(3);
        assertThat(out.getContent().get(0).categoryNameEn()).isEqualTo("Operational");
        assertThat(out.getContent().get(1).categoryNameEn()).isEqualTo("Cyber");
        assertThat(out.getContent().get(2).ownerUsername()).isEqualTo("bob");
        // The fix: exactly ONE batch query per FK type, never per-row findById.
        verify(riskCategoryRepository, times(1)).findAllById(anySet());
        verify(userRepository, times(1)).findAllById(anySet());
        verify(riskCategoryRepository, never()).findById(any());
        verify(userRepository, never()).findById(any());
    }

    // ─── lifecycle: happy path ───────────────────────────────────────

    @Test
    void submitForReview_OPEN_to_UNDER_REVIEW() {
        Risk r = risk("OPEN");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        RiskResponse resp = service.submitForReview(RISK_ID);
        assertThat(resp.status()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void approve_UNDER_REVIEW_to_MITIGATING() {
        Risk r = risk("UNDER_REVIEW");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.approve(RISK_ID).status()).isEqualTo("MITIGATING");
    }

    @Test
    void close_from_MITIGATING_or_ACCEPTED() {
        // Allowed from MITIGATING
        Risk a = risk("MITIGATING");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(a));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.close(RISK_ID).status()).isEqualTo("CLOSED");

        // Allowed from ACCEPTED
        Risk b = risk("ACCEPTED");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(b));
        assertThat(service.close(RISK_ID).status()).isEqualTo("CLOSED");
    }

    // ─── E-J archive / reopen lifecycle ──────────────────────────────

    @Test
    void archive_CLOSED_to_ARCHIVED() {
        Risk r = risk("CLOSED");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.archive(RISK_ID).status()).isEqualTo("ARCHIVED");
    }

    @Test
    void archive_fromOpen_isRejected() {
        Risk r = risk("OPEN");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.archive(RISK_ID))
            .isInstanceOf(IllegalArgumentException.class);
        verify(riskRepository, never()).save(any());
    }

    @Test
    void reopen_from_ARCHIVED_to_OPEN() {
        Risk r = risk("ARCHIVED");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.reopen(RISK_ID).status()).isEqualTo("OPEN");
    }

    // ─── E-D mandatory-treatment gate ────────────────────────────────

    @Test
    void close_highRisk_withoutTreatmentPlan_isBlocked() {
        Risk r = risk("MITIGATING"); r.setRiskLevel("HIGH");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(treatmentPlanRepository.existsByOrganisationIdAndRiskIdAndStatusNot(ORG_ID, RISK_ID, "REJECTED"))
            .thenReturn(false);

        assertThatThrownBy(() -> service.close(RISK_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("treatment plan");
        verify(riskRepository, never()).save(any());   // transition must not happen
    }

    @Test
    void close_highRisk_withTreatmentPlan_succeeds() {
        Risk r = risk("MITIGATING"); r.setRiskLevel("CRITICAL");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(treatmentPlanRepository.existsByOrganisationIdAndRiskIdAndStatusNot(ORG_ID, RISK_ID, "REJECTED"))
            .thenReturn(true);
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.close(RISK_ID).status()).isEqualTo("CLOSED");
    }

    // ─── F2-164: the exemption is the RECORD, never the status string ─────
    //
    // These four tests replace a single one — `close_highRisk_whenAccepted_
    // isExempt_fromTreatmentGate` — which stubbed nothing but `status="ACCEPTED"`
    // and asserted the gate was skipped. It described the code accurately and
    // that is exactly what made it hard to see: it locked in a one-click,
    // permanent bypass of ERM's headline control at RISK:WRITE. Same shape as
    // F2-159's decoy. A control test has to name the artefact, not the claim.

    @Test
    void close_highRisk_withLiveFormalAcceptance_isExempt_fromTreatmentGate() {
        // The exemption survives, and it is earned: an APPROVED acceptance record
        // inside its time-box carries justification, compensating controls, CRO
        // sign-off and (above appetite) Board sign-off.
        Risk r = risk("ACCEPTED"); r.setRiskLevel("HIGH");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(acceptanceRepository.existsByOrganisationIdAndRiskIdAndStatusAndExpiresAtGreaterThanEqual(
                eq(ORG_ID), eq(RISK_ID), eq("APPROVED"), any(LocalDate.class))).thenReturn(true);
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.close(RISK_ID).status()).isEqualTo("CLOSED");
        verify(treatmentPlanRepository, never())
            .existsByOrganisationIdAndRiskIdAndStatusNot(anyLong(), anyLong(), anyString());
    }

    @Test
    void close_highRisk_ACCEPTED_withNoAcceptanceRecord_isStillGated() {
        // THE regression this slice exists to prevent. A HIGH risk sitting in
        // ACCEPTED with no acceptance record behind it gets no exemption — the
        // status is a projection, and an unbacked projection proves nothing.
        Risk r = risk("ACCEPTED"); r.setRiskLevel("HIGH");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(acceptanceRepository.existsByOrganisationIdAndRiskIdAndStatusAndExpiresAtGreaterThanEqual(
                eq(ORG_ID), eq(RISK_ID), eq("APPROVED"), any(LocalDate.class))).thenReturn(false);
        when(treatmentPlanRepository.existsByOrganisationIdAndRiskIdAndStatusNot(ORG_ID, RISK_ID, "REJECTED"))
            .thenReturn(false);

        assertThatThrownBy(() -> service.close(RISK_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("treatment plan");
        verify(riskRepository, never()).save(any());
    }

    @Test
    void close_highRisk_ACCEPTED_withLapsedAcceptance_isStillGated() {
        // The repository query filters `expires_at >= today`, so a lapsed
        // acceptance answers false here. The gate must not wait for expireSweep
        // to catch up — an exemption that outlives its time-box is the permanence
        // half of F2-164.
        Risk r = risk("ACCEPTED"); r.setRiskLevel("CRITICAL");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(acceptanceRepository.existsByOrganisationIdAndRiskIdAndStatusAndExpiresAtGreaterThanEqual(
                eq(ORG_ID), eq(RISK_ID), eq("APPROVED"), any(LocalDate.class))).thenReturn(false);
        when(treatmentPlanRepository.existsByOrganisationIdAndRiskIdAndStatusNot(ORG_ID, RISK_ID, "REJECTED"))
            .thenReturn(true);   // a plan is the OTHER way through — and it works
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.close(RISK_ID).status()).isEqualTo("CLOSED");
    }

    @Test
    void close_refusal_namesTheStoreTheGateActuallyReads() {
        // (c) — there are two treatment tables. `risk.risk_treatments` is the
        // simple list on the risk's Treatments tab; `risk_lc.treatment_plans` is
        // the canonical plan the gate reads. The old message said "draft a
        // treatment plan", which a user who had just added three treatments read
        // as the system contradicting itself. Measured in dev: risks 21/22/23/24/
        // 32 are HIGH+ with a legacy treatment each and zero canonical plans.
        Risk r = risk("MITIGATING"); r.setRiskLevel("HIGH");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(acceptanceRepository.existsByOrganisationIdAndRiskIdAndStatusAndExpiresAtGreaterThanEqual(
                eq(ORG_ID), eq(RISK_ID), eq("APPROVED"), any(LocalDate.class))).thenReturn(false);
        when(treatmentPlanRepository.existsByOrganisationIdAndRiskIdAndStatusNot(ORG_ID, RISK_ID, "REJECTED"))
            .thenReturn(false);

        assertThatThrownBy(() -> service.close(RISK_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Treatments tab are not the same record");
    }

    @Test
    void close_lowRisk_hasNoTreatmentGate() {
        Risk r = risk("MITIGATING"); r.setRiskLevel("LOW");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.close(RISK_ID).status()).isEqualTo("CLOSED");
        verify(treatmentPlanRepository, never())
            .existsByOrganisationIdAndRiskIdAndStatusNot(anyLong(), anyLong(), anyString());
    }

    @Test
    void close_overAppetiteRisk_withoutPlan_isBlocked() {
        // E-H — not HIGH by level, but residual (10) breaches its category's
        // approved appetite ceiling (8) → still gated.
        Risk r = risk("MITIGATING"); r.setRiskLevel("MEDIUM"); r.setCategoryId(5L); r.setResidualScore(10);
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        RiskAppetiteStatement a = new RiskAppetiteStatement();
        a.setOrganisationId(ORG_ID); a.setCategoryId(5L); a.setStatus("APPROVED");
        a.setToleranceUpper(BigDecimal.valueOf(8));
        when(appetiteRepository.findByOrganisationIdAndStatus(ORG_ID, "APPROVED")).thenReturn(List.of(a));
        when(treatmentPlanRepository.existsByOrganisationIdAndRiskIdAndStatusNot(ORG_ID, RISK_ID, "REJECTED"))
            .thenReturn(false);

        assertThatThrownBy(() -> service.close(RISK_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("appetite");
        verify(riskRepository, never()).save(any());
    }

    @Test
    void reopen_CLOSED_to_OPEN() {
        Risk r = risk("CLOSED");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.reopen(RISK_ID).status()).isEqualTo("OPEN");
    }

    @Test
    void acceptOnApprovedAcceptance_allowed_from_OPEN_and_UNDER_REVIEW() {
        // F2-164 — renamed from `accept`, and no longer reachable from a
        // controller. `RiskAcceptanceService.approve` is the only caller, so the
        // state guard is now the second check rather than the only one.
        Risk a = risk("OPEN");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(a));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.acceptOnApprovedAcceptance(RISK_ID).status()).isEqualTo("ACCEPTED");

        Risk b = risk("UNDER_REVIEW");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(b));
        assertThat(service.acceptOnApprovedAcceptance(RISK_ID).status()).isEqualTo("ACCEPTED");
    }

    // ─── lifecycle: illegal transitions ──────────────────────────────

    @Test
    void submitForReview_from_CLOSED_throws() {
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("CLOSED")));

        assertThatThrownBy(() -> service.submitForReview(RISK_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OPEN");
    }

    @Test
    void approve_skipping_review_throws() {
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("OPEN")));

        assertThatThrownBy(() -> service.approve(RISK_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UNDER_REVIEW");
    }

    @Test
    void acceptOnApprovedAcceptance_from_a_terminal_status_throws() {
        // ⚠ Was `_from_MITIGATING_throws`, and MITIGATING is now a legitimate source —
        // stopping treatment to retain the risk is a real decision, and `request()` has
        // always allowed raising an acceptance there. Refusing it made a raised acceptance
        // un-approvable, which the live probe caught as a 400. The statuses that must still
        // be refused are the terminal ones: retaining a CLOSED or ARCHIVED risk is a reopen
        // decision (E-J), not an acceptance.
        for (String terminal : new String[]{"CLOSED", "ARCHIVED"}) {
            when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk(terminal)));
            assertThatThrownBy(() -> service.acceptOnApprovedAcceptance(RISK_ID))
                .as("a %s risk must not be flipped to ACCEPTED", terminal)
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(RiskService.RETAINABLE_STATUSES).contains("MITIGATING");
    }

    @Test
    void reopen_from_OPEN_throws() {
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("OPEN")));

        assertThatThrownBy(() -> service.reopen(RISK_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CLOSED");
    }

    // ─── incident feedback loop ──────────────────────────────────────

    @Test
    void bumpLikelihoodFromIncident_increments_by_1() {
        Risk r = risk("OPEN");
        r.setResidualLikelihood(2);
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        service.bumpLikelihoodFromIncident(RISK_ID);
        assertThat(r.getResidualLikelihood()).isEqualTo(3);
    }

    @Test
    void bumpLikelihoodFromIncident_caps_at_5() {
        Risk r = risk("OPEN");
        r.setResidualLikelihood(5);
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        service.bumpLikelihoodFromIncident(RISK_ID);
        assertThat(r.getResidualLikelihood()).isEqualTo(5);  // ceiling, not 6
    }

    @Test
    void bumpLikelihoodFromIncident_null_starts_at_2() {
        // Defensive: a risk with no residual likelihood yet should get 1+1=2
        Risk r = risk("OPEN");
        r.setResidualLikelihood(null);
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        service.bumpLikelihoodFromIncident(RISK_ID);
        assertThat(r.getResidualLikelihood()).isEqualTo(2);
    }

    // ─── tenant scope ────────────────────────────────────────────────

    @Test
    void get_other_tenant_throws_EntityNotFound() {
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("OPEN", OTHER_ORG)));

        assertThatThrownBy(() -> service.get(RISK_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Risk not found");
    }

    @Test
    void approve_other_tenant_throws_EntityNotFound() {
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("UNDER_REVIEW", OTHER_ORG)));

        assertThatThrownBy(() -> service.approve(RISK_ID))
            .isInstanceOf(EntityNotFoundException.class);

        verify(riskRepository, never()).save(any());
    }

    // ─── soft delete ─────────────────────────────────────────────────

    @Test
    void delete_soft_deletes_via_save() {
        Risk r = risk("OPEN");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(RISK_ID);

        assertThat(r.getIsDeleted()).isTrue();
        verify(riskRepository).save(r);
        verify(riskRepository, never()).deleteById(any());
    }

    // ─── create stamps tenant + identifiedDate ───────────────────────

    @Test
    void create_stamps_organisation_and_identifiedDate() {
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> {
            Risk saved = inv.getArgument(0);
            saved.setId(RISK_ID);
            return saved;
        });

        service.create(req());

        org.mockito.ArgumentCaptor<Risk> captor = org.mockito.ArgumentCaptor.forClass(Risk.class);
        verify(riskRepository).save(captor.capture());
        Risk saved = captor.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getIdentifiedDate()).isNotNull();
    }

    // ══════════════════════════════════════════════════════════════════
    // F8 automation events
    //
    // RiskService publishes:
    //   RISK_CREATED      — on create() with riskLevel, residualScore, owner
    //   RISK_TRANSITIONED — on every state-machine step with previousStatus
    //
    // Mirrors the FindingService / CrisisAarService pattern (PRs #50, #53).
    // All five transition methods route through doTransition(), so we only
    // need to test one (approve) to lock the event payload + one to confirm
    // the centralised emit covers a different start state (accept).
    // ══════════════════════════════════════════════════════════════════

    @Test
    void create_publishesRiskCreatedEvent_withCanonicalPayload() {
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> {
            Risk saved = inv.getArgument(0);
            saved.setId(RISK_ID);
            return saved;
        });

        service.create(req());

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("RISK_CREATED");
        assertThat(ev.entityType()).isEqualTo("Risk");
        assertThat(ev.entityId()).isEqualTo(RISK_ID);
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        // correlationId threads downstream rule_runs to the human-readable
        // RISK-NNNN code rather than the surrogate id — uniform with the
        // FindingService externalId / CrisisAarService reportCode pattern.
        assertThat(ev.correlationId()).isEqualTo("RISK-0017");
        assertThat(ev.payload()).containsEntry("riskCode", "RISK-0017");
        assertThat(ev.payload()).containsEntry("status", "OPEN");
        assertThat(ev.payload()).containsEntry("titleEn", "Title EN");
    }

    @Test
    void transition_publishesRiskTransitionedEvent_withPreviousStatusInPayload() {
        Risk r = risk("UNDER_REVIEW");
        r.setTitleEn("PDPL personal data exposure risk");
        r.setComplianceFrameworkClause("PDPL-2.3.1");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        service.approve(RISK_ID);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("RISK_TRANSITIONED");
        assertThat(ev.entityId()).isEqualTo(RISK_ID);
        // Single TRANSITIONED event — rules target the step via
        // condition_json {"status":"MITIGATING"} rather than us emitting
        // five distinct per-target event names. Matches FINDING_TRANSITIONED.
        assertThat(ev.payload()).containsEntry("status", "MITIGATING");
        assertThat(ev.payload()).containsEntry("previousStatus", "UNDER_REVIEW");
        // complianceFrameworkClause enables regulator-anchored routing
        // (e.g. PDPL-tagged risks auto-route to the DPO).
        assertThat(ev.payload()).containsEntry("complianceFrameworkClause", "PDPL-2.3.1");
    }

    @Test
    void acceptOnApprovedAcceptance_from_OPEN_carriesPreviousStatus_OPEN() {
        // The 'OPEN → ACCEPTED' path skips formal review — SAMA §10.4
        // flags this as a governance concern. A seed rule will match
        // ONLY on previousStatus=OPEN + status=ACCEPTED (and skip the
        // legitimate UNDER_REVIEW → ACCEPTED path). Confirm the payload
        // carries the field that makes that distinction possible.
        Risk r = risk("OPEN");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        service.acceptOnApprovedAcceptance(RISK_ID);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.payload()).containsEntry("status", "ACCEPTED");
        assertThat(ev.payload()).containsEntry("previousStatus", "OPEN");
    }

    @Test
    void transition_illegalState_doesNotEmitEvent() {
        // require() throws BEFORE audit.log + publishEvent — bad-state
        // guard must NOT fire an F8 event (otherwise a rule would react
        // to a state change that never happened — info leak + spurious
        // side-effects). Mirrors CrisisAarService PR #53 pattern.
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("CLOSED")));

        assertThatThrownBy(() -> service.submitForReview(RISK_ID))
            .isInstanceOf(IllegalArgumentException.class);

        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void approve_otherTenant_doesNotEmitEvent() {
        // Cross-tenant 404 must NOT fire an F8 event under the requesting
        // tenant (info leak — a rule reacting to an entity the requester
        // never had access to). Standardised across all audit-logged
        // services (FindingService PR #50).
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("UNDER_REVIEW", OTHER_ORG)));

        assertThatThrownBy(() -> service.approve(RISK_ID))
            .isInstanceOf(EntityNotFoundException.class);

        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    @Test
    void update_auditsButDoesNotEmit() {
        // No F8 on UPDATE — same policy as FindingService / CapaTaskService.
        // Status moves go through the dedicated transition methods which
        // DO emit. Field edits don't change rule-routing-relevant state,
        // so an UPDATE event would just spam the audit log with no
        // matching rules.
        Risk r = risk("OPEN");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(RISK_ID, req());

        verify(audit).log(eq("UPDATE"), eq("Risk"), eq(RISK_ID), anyString());
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    @Test
    void delete_auditsButDoesNotEmit() {
        // No F8 on DELETE — admin-only policy matches FindingService.
        Risk r = risk("OPEN");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(RISK_ID);

        verify(audit).log(eq("DELETE"), eq("Risk"), eq(RISK_ID), anyString());
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }
}
