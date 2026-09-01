package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.dto.RiskReviewDtos.ReassessDto;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskReview;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.risk.repository.RiskReviewRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ERM epic E-J — scheduled risk review / reassessment.
 *
 * <p>A review is a structured re-evaluation of a risk on a cadence (default
 * annual), distinct from the lighter periodic owner <em>attestation</em>
 * (§5.1 #6). {@link #reassess} may re-score the residual likelihood/impact,
 * resets the review clock, and writes a {@link RiskReview} audit row capturing
 * the before/after. A daily stale sweep flags risks past their review date as
 * {@code OVERDUE} and emits an F8 {@code RISK_REVIEW_OVERDUE} event so a rule
 * can escalate to the risk owner / CRO.
 */
@Service
@RequiredArgsConstructor
public class RiskReviewService {

    private final RiskRepository riskRepository;
    private final RiskReviewRepository reviewRepository;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;

    /** Re-reads DB-computed columns after a re-score — null in unit tests. */
    @PersistenceContext
    private EntityManager entityManager;

    // ── Reassessment ──────────────────────────────────────────────────────────

    @Transactional
    public RiskReview reassess(Long riskId, ReassessDto dto, Long userId, String username) {
        Risk r = mustGet(riskId);

        Integer lBefore = r.getResidualLikelihood();
        Integer iBefore = r.getResidualImpact();
        Integer scoreBefore = r.getResidualScore();

        boolean rescored = dto != null && dto.residualLikelihood() != null && dto.residualImpact() != null;
        if (rescored) {
            r.setResidualLikelihood(clamp(dto.residualLikelihood()));
            r.setResidualImpact(clamp(dto.residualImpact()));
        }
        if (dto != null && dto.cadenceDays() != null && dto.cadenceDays() > 0) {
            r.setReviewCadenceDays(dto.cadenceDays());
        }

        int cadence = r.getReviewCadenceDays() == null ? 365 : r.getReviewCadenceDays();
        LocalDate today = LocalDate.now();
        r.setLastReviewedAt(LocalDateTime.now());
        r.setReviewDate(today.plusDays(cadence));
        r.setReviewStatus("CURRENT");
        r = riskRepository.save(r);
        refreshComputed(r);  // pull DB-computed residual_score / risk_level after a re-score

        String decision = (dto != null && dto.decision() != null && !dto.decision().isBlank())
                ? dto.decision().trim().toUpperCase(Locale.ROOT)
                : (rescored ? "RESCORED" : "CONFIRMED");

        RiskReview rv = new RiskReview();
        rv.setOrganisationId(r.getOrganisationId());
        rv.setRiskId(r.getId());
        rv.setReviewedAt(LocalDateTime.now());
        rv.setReviewedBy(userId);
        rv.setReviewedByName(username);
        rv.setDecision(decision);
        rv.setResidualLikelihoodBefore(lBefore);
        rv.setResidualImpactBefore(iBefore);
        rv.setResidualLikelihoodAfter(r.getResidualLikelihood());
        rv.setResidualImpactAfter(r.getResidualImpact());
        rv.setResidualScoreSnapshot(r.getResidualScore());
        rv.setRiskLevelSnapshot(r.getRiskLevel());
        rv.setNextReviewDate(r.getReviewDate());
        rv.setNoteEn(dto == null ? null : dto.noteEn());
        rv.setNoteAr(dto == null ? null : dto.noteAr());
        rv = reviewRepository.save(rv);

        audit.log("RISK_REASSESSED", "Risk", r.getId(),
                "Risk " + riskCode(r.getId()) + " reviewed (" + decision + ")"
                        + (rescored ? " · residual " + scoreBefore + " → " + r.getResidualScore() : "")
                        + " · next review " + r.getReviewDate());
        events.publishEvent(new DomainEvent(
                r.getOrganisationId(), "RISK_REASSESSED",
                "Risk", r.getId(), riskCode(r.getId()),
                reassessPayload(r, decision)));
        return rv;
    }

    @Transactional(readOnly = true)
    public List<RiskReview> listReviews(Long riskId) {
        Long orgId = TenantContext.getCurrentTenant();
        return reviewRepository.findByOrganisationIdAndRiskIdOrderByReviewedAtDesc(orgId, riskId);
    }

    // ── Stale sweep ─────────────────────────────────────────────────────────────

    /** Tenant-scoped overdue risks (active + past their review date). */
    @Transactional(readOnly = true)
    public List<Risk> listOverdue() {
        return riskRepository.findReviewOverdue(TenantContext.getCurrentTenant(), LocalDate.now());
    }

    /** Flag the current tenant's overdue risks; emit F8 for each newly-flagged. Idempotent. */
    @Transactional
    public int runOverdueSweepForTenant() {
        return flag(riskRepository.findReviewOverdue(TenantContext.getCurrentTenant(), LocalDate.now()));
    }

    /** System-wide sweep across all tenants — invoked by the scheduler. */
    @Transactional
    public int runOverdueSweepAllTenants() {
        return flag(riskRepository.findReviewOverdueAllOrgs(LocalDate.now()));
    }

    /**
     * ⚠⚠ F2-317 — the tenant is set <b>per row</b>, because this method has two callers and only
     * one of them arrives with an ambient tenant.
     *
     * <p>{@link #runOverdueSweepForTenant()} is called from {@code RiskReviewController}, so
     * {@code TenantContext} holds the caller's organisation. {@link #runOverdueSweepAllTenants()} is
     * called from {@code RiskReviewScheduler}, where nothing sets it — and
     * {@code AuditLogService.recordEvent} does {@code org != null ? org : 1L}, falling back to
     * <b>tenant 1</b> for "system events". So every {@code RISK_REVIEW_OVERDUE} row the scheduler
     * wrote would be stamped organisation 1 whatever tenant's risk it described: tenant 1's audit log
     * gains entries naming other tenants' risk codes, and those tenants get no record that their
     * risks were flagged at all.
     *
     * <p>🔑 The {@code DomainEvent} two lines down already passes {@code r.getOrganisationId()}
     * explicitly. <b>Within one loop body the event was org-explicit and the audit row beside it was
     * not</b> — that asymmetry is the tell.
     *
     * <p>Latent when found: the scheduler is off by default
     * ({@code grc.risk.review-scheduler-enabled:false}, set in no profile) so it had never run, and
     * all seven existing rows came from the per-tenant path. Measured, <b>24 risks are past their
     * review date and unflagged — 4 in org 1, 10 in org 2, 10 in org 3</b>, so enabling one flag
     * would have mis-stamped 20 of them. What made it harmless was the configuration, not the code
     * ([[F2-182]]).
     *
     * <p>⚠ The ambient tenant is <b>restored</b>, never cleared: the controller path continues
     * inside the same request after this returns, and clearing would strip its tenant mid-request.
     * {@code KriSchedulerService} clears in its {@code finally} and is right to — it has no ambient
     * tenant to lose.
     *
     * <p>⚠ Deliberately <b>no per-row try/catch</b>, which is what this slice set out to add.
     * {@code audit.log} is {@code @Transactional} and joins this transaction, so catching its failure
     * still leaves the transaction rollback-only and the whole batch fails at commit: the catch would
     * create the <em>appearance</em> of resilience while changing nothing. Doing it properly needs a
     * per-row {@code REQUIRES_NEW} boundary — the recorded engine-wiring rule — and is filed.
     */
    private int flag(List<Risk> overdue) {
        LocalDate today = LocalDate.now();
        int flagged = 0;
        Long ambient = TenantContext.getCurrentTenant();
        try {
            for (Risk r : overdue) {
                if ("OVERDUE".equals(r.getReviewStatus())) continue;  // already flagged — idempotent
                TenantContext.setCurrentTenant(r.getOrganisationId());
                r.setReviewStatus("OVERDUE");
                riskRepository.save(r);
                flagged++;
                long days = r.getReviewDate() == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(r.getReviewDate(), today));
                audit.log("RISK_REVIEW_OVERDUE", "Risk", r.getId(),
                        "Risk " + riskCode(r.getId()) + " review overdue by " + days + " day(s) (due " + r.getReviewDate() + ")");
                events.publishEvent(new DomainEvent(
                        r.getOrganisationId(), "RISK_REVIEW_OVERDUE",
                        "Risk", r.getId(), riskCode(r.getId()),
                        overduePayload(r, days)));
            }
        } finally {
            TenantContext.setCurrentTenant(ambient);
        }
        return flagged;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Risk mustGet(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return riskRepository.findById(id)
                .filter(r -> r.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + id));
    }

    private void refreshComputed(Risk r) {
        if (entityManager == null || r.getId() == null) return;
        entityManager.flush();
        entityManager.refresh(r);
    }

    private static Integer clamp(Integer v) {
        if (v == null) return null;
        return Math.max(1, Math.min(5, v));
    }

    private static String riskCode(Long id) {
        return id == null ? "RISK-0000" : "RISK-" + String.format(Locale.ROOT, "%04d", id);
    }

    private static Map<String, Object> reassessPayload(Risk r, String decision) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("riskCode", riskCode(r.getId()));
        m.put("decision", decision);
        m.put("riskLevel", r.getRiskLevel() == null ? "" : r.getRiskLevel());
        m.put("residualScore", r.getResidualScore() == null ? "" : String.valueOf(r.getResidualScore()));
        m.put("nextReviewDate", r.getReviewDate() == null ? "" : r.getReviewDate().toString());
        return m;
    }

    private static Map<String, Object> overduePayload(Risk r, long daysOverdue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("riskCode", riskCode(r.getId()));
        m.put("riskLevel", r.getRiskLevel() == null ? "" : r.getRiskLevel());
        m.put("residualScore", r.getResidualScore() == null ? "" : String.valueOf(r.getResidualScore()));
        m.put("reviewDate", r.getReviewDate() == null ? "" : r.getReviewDate().toString());
        m.put("daysOverdue", String.valueOf(daysOverdue));
        return m;
    }
}
