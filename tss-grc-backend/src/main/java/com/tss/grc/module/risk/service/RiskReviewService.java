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
                ? dto.decision().trim().toUpperCase()
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

    private int flag(List<Risk> overdue) {
        LocalDate today = LocalDate.now();
        int flagged = 0;
        for (Risk r : overdue) {
            if ("OVERDUE".equals(r.getReviewStatus())) continue;  // already flagged — idempotent
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
        return id == null ? "RISK-0000" : "RISK-" + String.format("%04d", id);
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
