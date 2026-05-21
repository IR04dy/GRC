package com.tss.grc.module.platform.completion;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import com.tss.grc.module.bcm.cycle.repository.CrisisEventRepository;
import com.tss.grc.module.evidence.entity.EvidenceConnector;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.repository.EvidenceConnectorRepository;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.privacy.entity.ProcessingActivity;
import com.tss.grc.module.privacy.repository.ProcessingActivityRepository;
import com.tss.grc.module.privacy_lc.entity.DsrCase;
import com.tss.grc.module.privacy_lc.repository.DsrCaseRepository;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskAttestation;
import com.tss.grc.module.risk.entity.RiskControl;
import com.tss.grc.module.risk.repository.RiskAttestationRepository;
import com.tss.grc.module.risk.repository.RiskControlRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
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
    private final RiskRepository                risks;
    private final RiskAttestationRepository     riskAttestations;
    private final EvidenceItemRepository        evidence;
    private final EvidenceConnectorRepository   connectors;
    private final ProcessingActivityRepository  ropa;
    private final DsrCaseRepository             dsrCases;
    private final CrisisEventRepository         crises;
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

    /** Page size for the freshness sweep. Chosen to keep peak memory low
     *  even on tenants with tens of thousands of stale items. */
    private static final int SWEEP_PAGE_SIZE = 500;

    /**
     * Walks tenant evidence and flips items past their {@code reviewDueDate}
     * into {@code STALE} status. Emits a {@code EVIDENCE_REVIEW_OVERDUE}
     * domain event per flipped item so F8 rules can chase the owner.
     *
     * <p>PERFORMANCE: paginated in {@link #SWEEP_PAGE_SIZE}-row windows so
     * a tenant with 100k stale items doesn't OOM the JVM. The query
     * predicate ORDER BY id ASC + skipping previously-flipped rows (status
     * filter) guarantees forward progress between pages even though we
     * mutate the rows we read.
     */
    @Transactional
    public FreshnessSweepResult evidenceFreshnessSweep() {
        Long o = org();
        LocalDate today = LocalDate.now();
        long flipped = 0;

        while (true) {
            @SuppressWarnings("unchecked")
            List<EvidenceItem> due = em.createQuery(
                "select e from EvidenceItem e "
              + " where e.organisationId = :org "
              + "   and e.reviewDueDate is not null "
              + "   and e.reviewDueDate < :today "
              + "   and e.status not in ('STALE','EXPIRED','ARCHIVED','REJECTED') "
              + " order by e.id asc")
                .setParameter("org", o)
                .setParameter("today", today)
                .setMaxResults(SWEEP_PAGE_SIZE)
                .getResultList();

            if (due.isEmpty()) break;

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
            // Flush + clear the persistence context so the next page starts
            // with no managed entities — keeps the JVM heap flat across
            // very long sweeps.
            em.flush();
            em.clear();
            // If this page was a short read, we're done.
            if (due.size() < SWEEP_PAGE_SIZE) break;
        }

        // count of currently-current rows (cheap separate query)
        long current = ((Number) em.createQuery(
            "select count(e) from EvidenceItem e "
          + " where e.organisationId = :org "
          + "   and e.status = 'CURRENT'")
            .setParameter("org", o)
            .getSingleResult()).longValue();

        return new FreshnessSweepResult(flipped + current, flipped, current);
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
     * transfer mechanism documented. The {@code riskNote} surfaces the
     * reason for each row.
     *
     * <p>PERFORMANCE: the at-risk predicate is pushed into JPQL via
     * {@link ProcessingActivityRepository#findAtRiskCrossBorder} so we
     * only hydrate the rows that will actually be returned to the client.
     * The previous version pulled every cross-border RoPA into memory
     * and filtered in Java, which made the response O(all-cross-border-rows)
     * even if the result was a single row.
     */
    @Transactional(readOnly = true)
    public List<AtRiskTransfer> atRiskTransfers() {
        Long o = org();
        List<ProcessingActivity> hits = ropa.findAtRiskCrossBorder(o);
        List<AtRiskTransfer> out = new ArrayList<>(hits.size());
        for (ProcessingActivity p : hits) {
            String adequacy  = p.getAdequacyDecision();
            String mechanism = p.getTransferMechanism();
            String reason;
            if (adequacy == null) {
                reason = "Adequacy decision missing";
            } else if ("NONE".equals(adequacy)) {
                reason = "Non-adequate destination with no transfer mechanism";
            } else { // PARTIAL is the only remaining case the SQL filter returns
                reason = "Partial-adequacy destination needs an explicit mechanism";
            }
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

    // ══════════════════════════════════════════════════════════════
    // §5.1 #6 — Risk owner attestation cycle (V115)
    //
    // SAMA expectation + NCA ECC §1-3: the risk owner re-confirms each
    // active risk on a regular cadence (default 90 days = quarterly).
    // Each attestation snapshots the residual posture for an audit-defensible
    // trail even if the risk drifts later.
    // ══════════════════════════════════════════════════════════════

    public record AttestBody(String decision, String statementEn,
                             String statementAr, String attestedByName) {}

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
        att.setAttestedByName(body.attestedByName());
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

    // ══════════════════════════════════════════════════════════════
    // §5.4 #1 — DSR fulfilment SLA sweep (PDPL Article 4 — 30 days)
    // ══════════════════════════════════════════════════════════════

    public record DsrSlaSweepResult(long openOverdue, long firedEvents) {}

    @Transactional
    public DsrSlaSweepResult dsrSlaSweep() {
        Long o = org();
        List<String> closedStatuses = List.of("DELIVERED","REJECTED","EXTENDED");
        List<DsrCase> overdue = dsrCases.findByOrganisationIdAndStatusNotInAndSlaDeadlineBefore(
            o, closedStatuses, LocalDate.now());

        long fired = 0;
        LocalDateTime now = LocalDateTime.now();
        for (DsrCase c : overdue) {
            boolean stampedNow = false;
            if (c.getSlaBreachedAt() == null) {
                c.setSlaBreachedAt(now);
                stampedNow = true;
            }
            // CORRECTNESS: dedup against `!Boolean.TRUE.equals(...)` not
            // `Boolean.FALSE.equals(...)` — the latter returns false when
            // the flag is null, which is the OPPOSITE of what we want
            // (a null flag means "never notified" and SHOULD fire).
            boolean shouldFire = !Boolean.TRUE.equals(c.getSlaBreachNotified());
            if (shouldFire) {
                eventPublisher.publishEvent(new DomainEvent(
                    o, "PDPL_DSR_SLA_BREACH", "DsrCase", c.getId(), null,
                    Map.of("externalId", String.valueOf(c.getExternalId()),
                           "type", String.valueOf(c.getType()),
                           "slaDeadline", String.valueOf(c.getSlaDeadline()),
                           "status", String.valueOf(c.getStatus()))));
                c.setSlaBreachNotified(true);
                fired++;
                // Only write the audit row on the first detection. Prior
                // code logged on every sweep run → tens of duplicate rows
                // per overdue case once a daily sweep was wired.
                audit.log("SLA_BREACH", "DsrCase", c.getId(),
                    "DSR " + c.getExternalId() + " missed PDPL 30-day SLA (deadline "
                    + c.getSlaDeadline() + ")");
            }
            // Only save when something actually changed — avoids
            // gratuitous UPDATEs + audit-interceptor noise.
            if (stampedNow || shouldFire) {
                dsrCases.save(c);
            }
        }
        return new DsrSlaSweepResult(overdue.size(), fired);
    }

    // ══════════════════════════════════════════════════════════════
    // §5.4 #2 — PDPL Article 23 72-hour breach deadline maintenance
    // ══════════════════════════════════════════════════════════════

    public record Pdpl72hSweepResult(
        long personalDataCrises, long deadlinesSet, long newlyBreached) {}

    /**
     * For every crisis with personal_data_affected=true:
     *   - if pdpl_72h_deadline_at is null and detection_time is set,
     *     compute it = detection_time + 72h
     *   - if the deadline has passed and notification hasn't been drafted,
     *     flip pdpl_72h_breached=true and emit PDPL_72H_BREACH
     */
    @Transactional
    public Pdpl72hSweepResult pdpl72hSweep() {
        Long o = org();
        // Use a permissive page to walk all rows; in practice these counts are tiny.
        List<CrisisEvent> all = crises.findByOrganisationIdOrderByIdDesc(o,
            org.springframework.data.domain.PageRequest.of(0, 500)).getContent();

        long pd = 0, set = 0, breached = 0;
        LocalDateTime now = LocalDateTime.now();
        for (CrisisEvent ce : all) {
            if (!Boolean.TRUE.equals(ce.getPersonalDataAffected())) continue;
            pd++;
            boolean changed = false;
            if (ce.getPdpl72hDeadlineAt() == null && ce.getDetectionTime() != null) {
                ce.setPdpl72hDeadlineAt(ce.getDetectionTime().plusHours(72));
                set++;
                changed = true;
            }
            if (ce.getPdpl72hDeadlineAt() != null
                && ce.getPdplNotificationDraftedAt() == null
                && ce.getPdpl72hDeadlineAt().isBefore(now)
                && !Boolean.TRUE.equals(ce.getPdpl72hBreached())) {
                ce.setPdpl72hBreached(true);
                breached++;
                changed = true;
                eventPublisher.publishEvent(new DomainEvent(
                    o, "PDPL_72H_BREACH", "CrisisEvent", ce.getId(), null,
                    Map.of("eventCode", String.valueOf(ce.getEventCode()),
                           "title", String.valueOf(ce.getTitleEn()),
                           "deadline", String.valueOf(ce.getPdpl72hDeadlineAt()))));
                audit.log("PDPL_72H_BREACH", "CrisisEvent", ce.getId(),
                    "Crisis " + ce.getEventCode() + " missed PDPL 72-hour window");
            }
            // PERFORMANCE: only persist when we actually mutated the row.
            // The prior unconditional save() fired Hibernate UPDATEs +
            // F7 audit-interceptor rows for every event scanned, even
            // ones that weren't touched.
            if (changed) {
                crises.save(ce);
            }
        }
        return new Pdpl72hSweepResult(pd, set, breached);
    }

    // ══════════════════════════════════════════════════════════════
    // §5.9 #2 — Evidence connector registry
    // ══════════════════════════════════════════════════════════════

    public record ConnectorPatch(String status, String configJson) {}

    @Transactional(readOnly = true)
    public List<EvidenceConnector> listConnectors() {
        return connectors.findByOrganisationIdOrderByCategoryAscNameEnAsc(org());
    }

    @Transactional
    public EvidenceConnector updateConnectorStatus(String code, ConnectorPatch patch) {
        Long o = org();
        EvidenceConnector c = connectors.findByOrganisationIdAndCode(o, code)
            .orElseThrow(() -> new EntityNotFoundException(
                "Connector " + code + " not found for tenant " + o));
        if (patch.status() != null && !patch.status().isBlank()) c.setStatus(patch.status());
        if (patch.configJson() != null)                          c.setConfigJson(patch.configJson());
        c.setUpdatedAt(LocalDateTime.now());
        EvidenceConnector saved = connectors.save(c);
        audit.log("UPDATE", "EvidenceConnector", saved.getId(),
            "Connector " + code + " → status=" + saved.getStatus());
        return saved;
    }
}
