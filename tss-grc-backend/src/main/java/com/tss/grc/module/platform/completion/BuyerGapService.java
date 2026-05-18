package com.tss.grc.module.platform.completion;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.privacy.entity.ProcessingActivity;
import com.tss.grc.module.privacy.repository.ProcessingActivityRepository;
import com.tss.grc.module.risk.entity.RiskControl;
import com.tss.grc.module.risk.repository.RiskControlRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Buyer-gap completion service — combined backend for the three module
 * gaps the May-2026 buyer assessment flagged:
 *
 * <ul>
 *   <li><b>§5.1 ERM</b> — KRI band classification + transition events,
 *       risk → control linkage management, appetite-vs-exposure variance.</li>
 *   <li><b>§5.9 Evidence</b> — collection-freshness sweep that flips items
 *       past {@code reviewDueDate} into {@code STALE} status and emits an
 *       F8 event for the engine.</li>
 *   <li><b>§5.4 Privacy</b> — cross-border-transfer risk listing,
 *       special-category-data listing.</li>
 * </ul>
 *
 * <p>Bundled into one service because each piece is a thin orchestrator
 * over an existing repo + a small persistence helper. Splitting into three
 * services would spread the audit-trail wiring across near-empty classes.
 */
@Service
@RequiredArgsConstructor
public class BuyerGapService {

    private static final Logger log = LoggerFactory.getLogger(BuyerGapService.class);

    private final RiskControlRepository         riskControls;
    private final EvidenceItemRepository        evidence;
    private final ProcessingActivityRepository  ropa;
    private final AuditLogService               audit;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager em;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ══════════════════════════════════════════════════════════════
    // §5.1 ERM — Risk → Control linkage + appetite variance
    //
    // §5.1 #5 KRI band engine is ALREADY DONE by the legacy KriEngineService +
    // KriAppetiteController: band classification, automatic breach-event
    // creation, ack + resolve workflow. Buyer assessment was stale; this
    // service stays focused on the two genuinely missing pieces.
    // ══════════════════════════════════════════════════════════════

    // ── §5.1 #2 — Risk → Control linkage management ───────────────

    @Transactional(readOnly = true)
    public List<RiskControl> listRiskControls(Long riskId) {
        return riskControls.findByOrganisationIdAndRiskIdOrderByIdAsc(org(), riskId);
    }

    @Transactional
    public RiskControl linkRiskControl(Long riskId, Long controlId,
                                       String effectiveness, BigDecimal contributionPct,
                                       String notes) {
        Long o = org();
        RiskControl row = riskControls.findByOrganisationIdAndRiskIdAndControlId(o, riskId, controlId)
            .orElseGet(RiskControl::new);
        row.setOrganisationId(o);
        row.setRiskId(riskId);
        row.setControlId(controlId);
        if (effectiveness != null && !effectiveness.isBlank()) row.setEffectiveness(effectiveness);
        row.setContributionPct(contributionPct);
        row.setNotes(notes);
        row.setUpdatedAt(LocalDateTime.now());
        RiskControl saved = riskControls.save(row);
        audit.log("LINK", "RiskControl", saved.getId(),
            "Risk #" + riskId + " ↔ Control #" + controlId + " effectiveness=" + saved.getEffectiveness());
        return saved;
    }

    // ── §5.1 #5 — Appetite-vs-exposure variance ─────────────────────

    public record AppetiteVariance(
        long   totalRisks,
        long   risksAboveAppetite,
        long   risksWithinAppetite,
        Map<String, CategoryBreakdown> byCategory
    ) {}
    public record CategoryBreakdown(
        long    riskCount,
        long    aboveAppetite,
        Integer appetiteScore,
        Integer maxResidualScore,
        Double  avgResidualScore
    ) {}

    /**
     * Computes per-category exposure vs appetite. Returns:
     *   - per-category: appetite_score, max residual, average residual, breach count
     *   - rollup: total risks, breach count, within-appetite count
     *
     * <p>Uses a single tenant-scoped native query so we don't have to wire
     * a JPA join across two schemas (risk.risks + risk.risk_appetites).
     */
    @Transactional(readOnly = true)
    public AppetiteVariance appetiteVariance() {
        Long o = org();
        // Per-category aggregation. Joining to risk_appetite_statements is
        // optional — risks without a matching appetite fall into the
        // UNCATEGORISED bucket. Treats tolerance_upper as the appetite
        // ceiling: a risk is "above appetite" when its residual_score
        // exceeds the category's tolerance_upper for any approved appetite.
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "select coalesce(cast(r.category_id as nvarchar(50)), 'UNCATEGORISED') as category,"
          + "       count(*)                                                       as risk_count,"
          + "       coalesce(max(r.residual_score), 0)                             as max_residual,"
          + "       coalesce(avg(cast(r.residual_score as float)), 0.0)            as avg_residual,"
          + "       max(a.tolerance_upper)                                         as appetite_ceiling"
          + "  from risk.risks r"
          + "  left join risk.risk_appetite_statements a"
          + "    on a.organisation_id = r.organisation_id"
          + "   and a.category_id     = r.category_id"
          + "   and a.is_deleted      = 0"
          + " where r.organisation_id = :org and r.is_deleted = 0"
          + " group by r.category_id")
            .setParameter("org", o)
            .getResultList();

        Map<String, CategoryBreakdown> byCat = new LinkedHashMap<>();
        long total = 0, above = 0, within = 0;
        for (Object[] r : rows) {
            String cat        = (String) r[0];
            long   count      = ((Number) r[1]).longValue();
            int    maxRes     = ((Number) r[2]).intValue();
            double avgRes     = ((Number) r[3]).doubleValue();
            Integer appetite  = r[4] == null ? null : ((Number) r[4]).intValue();

            long catAbove = 0;
            if (appetite != null) {
                catAbove = (maxRes > appetite) ? count : 0;
            }
            byCat.put(cat, new CategoryBreakdown(count, catAbove, appetite, maxRes,
                Math.round(avgRes * 10.0) / 10.0));
            total += count;
            above += catAbove;
            within += (count - catAbove);
        }
        return new AppetiteVariance(total, above, within, byCat);
    }

    // ══════════════════════════════════════════════════════════════
    // §5.9 Evidence — collection-freshness sweep
    // ══════════════════════════════════════════════════════════════

    public record FreshnessSweepResult(
        long itemsScanned,
        long flippedToStale,
        long stillCurrent
    ) {}

    /**
     * Walks tenant evidence and flips items past their {@code reviewDueDate}
     * into {@code STALE} status. Emits a {@code EVIDENCE_REVIEW_OVERDUE}
     * domain event per flipped item so F8 rules can chase the owner.
     */
    @Transactional
    public FreshnessSweepResult evidenceFreshnessSweep() {
        Long o = org();
        LocalDate today = LocalDate.now();

        @SuppressWarnings("unchecked")
        List<EvidenceItem> due = em.createQuery(
            "select e from EvidenceItem e "
          + " where e.organisationId = :org "
          + "   and e.reviewDueDate is not null "
          + "   and e.reviewDueDate < :today "
          + "   and e.status not in ('STALE','EXPIRED','ARCHIVED','REJECTED')")
            .setParameter("org", o)
            .setParameter("today", today)
            .getResultList();

        long flipped = 0;
        for (EvidenceItem e : due) {
            e.setStatus("STALE");
            evidence.save(e);
            flipped++;
            audit.log("STALE", "EvidenceItem", e.getId(),
                "Evidence " + e.getTitleEn() + " went stale (review_due_date "
                + e.getReviewDueDate() + ")");
            eventPublisher.publishEvent(new DomainEvent(
                o, "EVIDENCE_REVIEW_OVERDUE", "EvidenceItem", e.getId(), null,
                Map.of("titleEn", String.valueOf(e.getTitleEn()),
                       "reviewDueDate", String.valueOf(e.getReviewDueDate()))));
        }

        // count of currently-current rows (cheap separate query)
        long current = ((Number) em.createQuery(
            "select count(e) from EvidenceItem e "
          + " where e.organisationId = :org "
          + "   and e.status = 'CURRENT'")
            .setParameter("org", o)
            .getSingleResult()).longValue();

        return new FreshnessSweepResult(due.size() + current, flipped, current);
    }

    // ══════════════════════════════════════════════════════════════
    // §5.4 Privacy — cross-border + special-category listings
    // ══════════════════════════════════════════════════════════════

    public record AtRiskTransfer(
        Long   id,
        String activityName,
        String receivingCountry,
        String adequacyDecision,
        String transferMechanism,
        String dataOfficeReference,
        String riskNote
    ) {}

    /**
     * Lists processing activities flagged as cross-border that look risky:
     * either no adequacy decision recorded, or NONE/PARTIAL with no
     * transfer mechanism documented. The "riskNote" surfaces the reason.
     */
    @Transactional(readOnly = true)
    public List<AtRiskTransfer> atRiskTransfers() {
        Long o = org();
        List<ProcessingActivity> all = ropa.findByOrganisationIdAndCrossBorderTransferTrue(o);
        List<AtRiskTransfer> out = new ArrayList<>();
        for (ProcessingActivity p : all) {
            String adequacy   = p.getAdequacyDecision();
            String mechanism  = p.getTransferMechanism();
            String reason = null;
            if (adequacy == null)                                              reason = "Adequacy decision missing";
            else if ("NONE".equals(adequacy) && (mechanism == null || mechanism.isBlank()))
                                                                              reason = "Non-adequate destination with no transfer mechanism";
            else if ("PARTIAL".equals(adequacy) && (mechanism == null || mechanism.isBlank()))
                                                                              reason = "Partial-adequacy destination needs an explicit mechanism";
            if (reason == null) continue;
            out.add(new AtRiskTransfer(
                p.getId(), p.getActivityNameEn(), p.getReceivingCountry(),
                adequacy, mechanism, p.getDataOfficeReference(), reason));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ProcessingActivity> specialCategoryActivities() {
        return ropa.findByOrganisationIdAndSpecialCategoryFlagTrue(org());
    }
}
