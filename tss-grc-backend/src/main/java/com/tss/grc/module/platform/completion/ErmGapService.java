package com.tss.grc.module.platform.completion;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskAttestation;
import com.tss.grc.module.risk.entity.RiskControl;
import com.tss.grc.module.risk.repository.RiskAttestationRepository;
import com.tss.grc.module.risk.repository.RiskControlRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.risk.service.RiskExposureService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * §5.1 ERM slice of the buyer-assessment completion bundle — risk → control
 * linkage, appetite-vs-exposure variance, and the risk-owner attestation cycle.
 *
 * <p>Extracted from the former {@code BuyerGapService} god-class (QUAL-BCMP-003)
 * which bundled three unrelated domains (ERM / Evidence / Privacy) into one
 * class touching five repo families. Each domain is now its own service so a
 * change to one no longer pulls the others' repos into scope. Behaviour is
 * unchanged.
 *
 * <p>§5.1 #5 KRI band engine is ALREADY DONE by the canonical KRI engine
 * (module.risk.kri — KriController at /api/v1/risk/kri/*); this service stays
 * focused on the genuinely missing pieces.
 */
@Service
@RequiredArgsConstructor
public class ErmGapService {

    private final RiskControlRepository     riskControls;
    private final RiskRepository            risks;
    private final RiskAttestationRepository riskAttestations;
    private final AuditLogService           audit;
    private final RiskExposureService       exposure;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager em;

    private Long org() { return TenantContext.getCurrentTenant(); }

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
        // SECURITY: verify the risk belongs to the caller's tenant BEFORE
        // creating the bridge row. Without this check a caller from org A
        // could pass a riskId belonging to org B and we'd happily create a
        // risk_controls row stamping `organisation_id = A` but
        // `risk_id = (a row in B)` — a cross-tenant FK leak.
        risks.findById(riskId)
            .filter(r -> Objects.equals(r.getOrganisationId(), o))
            .orElseThrow(() -> new EntityNotFoundException(
                "Risk " + riskId + " not found for tenant " + o));

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
        // E-B: a control link / effectiveness change re-derives the risk's residual
        // from the aggregate control posture (manual re-assessments still override).
        exposure.recomputeResidual(riskId);
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
    // §5.1 #6 — Risk owner attestation cycle (V115)
    //
    // SAMA expectation + NCA ECC §1-3: the risk owner re-confirms each
    // active risk on a regular cadence (default 90 days = quarterly).
    // Each attestation snapshots the residual posture for an audit-defensible
    // trail even if the risk drifts later.
    // ══════════════════════════════════════════════════════════════

    /**
     * F2-224 — {@code attestedByName} is gone. The attester of a risk is the authenticated
     * principal; see {@link #attestRisk} for why the clock proves it.
     */
    public record AttestBody(String decision, String statementEn,
                             String statementAr) {}

    @Transactional
    public RiskAttestation attestRisk(Long riskId, AttestBody body) {
        Long o = org();
        Risk risk = risks.findById(riskId)
            .filter(r -> Objects.equals(r.getOrganisationId(), o))
            .orElseThrow(() -> new EntityNotFoundException(
                "Risk " + riskId + " not found for tenant " + o));

        String decision = (body.decision() == null || body.decision().isBlank())
            ? "CONFIRMED" : body.decision();

        int cadence = risk.getAttestationCadenceDays() != null
            ? risk.getAttestationCadenceDays() : 90;
        LocalDateTime now = LocalDateTime.now();
        LocalDate nextDue = LocalDate.now().plusDays(cadence);

        RiskAttestation att = new RiskAttestation();
        att.setOrganisationId(o);
        att.setRiskId(riskId);
        att.setAttestedAt(now);
        // F2-224 — the attester is the authenticated principal, never a body field. This method
        // stamps attestedAt from the server's own clock one line above, which is the server
        // declaring that it is the recorder of this act; taking WHO from the request splits one
        // attribution across two owners. A risk attestation is the artefact saying a named person
        // confirmed the residual score below is still current, so a caller-chosen name is the
        // whole value of the record.
        att.setAttestedByName(CurrentActor.username());
        att.setDecision(decision);
        att.setStatementEn(body.statementEn());
        att.setStatementAr(body.statementAr());
        att.setResidualScoreSnapshot(risk.getResidualScore());
        att.setRiskLevelSnapshot(risk.getRiskLevel());
        att.setNextDueDate(nextDue);
        RiskAttestation saved = riskAttestations.save(att);

        risk.setLastAttestedAt(now);
        risk.setNextAttestationDate(nextDue);
        risks.save(risk);

        audit.log("ATTEST", "Risk", riskId,
            "Risk attested with decision=" + decision + ", next due " + nextDue);
        eventPublisher.publishEvent(new DomainEvent(
            o, "RISK_ATTESTED", "Risk", riskId, null,
            Map.of("decision", decision, "nextDueDate", String.valueOf(nextDue))));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<RiskAttestation> listRiskAttestations(Long riskId) {
        return riskAttestations.findByOrganisationIdAndRiskIdOrderByAttestedAtDesc(org(), riskId);
    }

    public record AttestationSweepResult(long overdue, long firedEvents) {}

    /**
     * Walks tenant risks past their attestation due date and emits a
     * RISK_ATTESTATION_OVERDUE event per risk so F8 rules can notify the
     * owner. Idempotency: this sweep is safe to re-run; it does not change
     * any state on the risk itself — the F8 rule chain is expected to
     * suppress duplicate notifications within its own dedup window.
     */
    @Transactional
    public AttestationSweepResult riskAttestationSweep() {
        Long o = org();
        List<Risk> overdue = risks.findAttestationOverdue(o, LocalDate.now());
        long fired = 0;
        for (Risk r : overdue) {
            eventPublisher.publishEvent(new DomainEvent(
                o, "RISK_ATTESTATION_OVERDUE", "Risk", r.getId(), null,
                Map.of("title", String.valueOf(r.getTitleEn()),
                       "nextAttestationDate", String.valueOf(r.getNextAttestationDate()),
                       "ownerId", String.valueOf(r.getOwnerId()))));
            fired++;
        }
        if (fired > 0) {
            audit.log("SWEEP", "RiskAttestation", null,
                "Found " + fired + " risk(s) past attestation due date");
        }
        return new AttestationSweepResult(overdue.size(), fired);
    }
}
