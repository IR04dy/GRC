package com.tss.grc.module.platform.completion;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.entity.EvidenceConnector;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.repository.EvidenceConnectorRepository;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * §5.9 Evidence slice of the buyer-assessment completion bundle —
 * collection-freshness sweep and the evidence connector registry.
 *
 * <p>Extracted from the former {@code BuyerGapService} god-class
 * (QUAL-BCMP-003). Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class EvidenceGapService {

    private final EvidenceItemRepository      evidence;
    private final EvidenceConnectorRepository connectors;
    private final AuditLogService             audit;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager em;

    private Long org() { return TenantContext.getCurrentTenant(); }

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
