package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.DlpEvent;
import com.tss.grc.module.socops.repository.DlpEventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DlpEventService {

    private final DlpEventRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    @Transactional(readOnly = true)
    public Page<DlpEvent> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByEventTimeDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public DlpEvent get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("DlpEvent " + id));
    }

    @Transactional
    public DlpEvent ingest(DlpEvent in) {
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        return repo.findByOrganisationIdAndSourceSystemAndExternalId(
                org, in.getSourceSystem(), in.getExternalId())
            .map(existing -> {
                audit.log("DLP_REFRESH", "DlpEvent", existing.getId(), "Re-ingest");
                return existing;
            })
            .orElseGet(() -> {
                // SEC-CYB-002 — force id=null so a forged id on the ingest body
                // can't overwrite an existing (cross-tenant) row via merge.
                in.setId(null);
                // F2-386 — the review status is the analyst's act, written only by review().
                // ingest binds the whole DlpEvent entity, so a caller (a DLP connector, or any
                // DLP_INGEST/CISO holder) could POST {severity:CRITICAL, reviewed:true} and have
                // the event born "reviewed", silencing the AR-136 DLP_CRITICAL_UNREVIEWED SOC
                // escalation below. A freshly-ingested event has not been reviewed — scrub the
                // review fields on create, the same server-owned-field discipline as the id scrub.
                in.setReviewed(false);
                in.setReviewOutcome(null);
                in.setReviewNotes(null);
                DlpEvent saved = repo.save(in);
                audit.log("DLP_INGEST", "DlpEvent", saved.getId(),
                    saved.getSourceSystem() + " · " + saved.getActionTaken()
                        + " · " + (saved.getDataClassification() == null ? "—" : saved.getDataClassification()));

                // F8 — AR-136 (CRITICAL unreviewed) and AR-145 (NDC TOP_SECRET
                // blocked → CISO instant page). Two distinct triggers because
                // a CRITICAL of CONFIDENTIAL goes to a different inbox than a
                // TOP_SECRET BLOCK that escalates straight to NDC's owner.
                Map<String, Object> payload = Map.of(
                    "sourceSystem",       saved.getSourceSystem()       == null ? "" : saved.getSourceSystem(),
                    "actionTaken",        saved.getActionTaken()        == null ? "" : saved.getActionTaken(),
                    "severity",           saved.getSeverity()           == null ? "" : saved.getSeverity(),
                    "dataClassification", saved.getDataClassification() == null ? "" : saved.getDataClassification());
                if ("CRITICAL".equalsIgnoreCase(saved.getSeverity()) && !Boolean.TRUE.equals(saved.getReviewed())) {
                    publisher.publishEvent(DomainEvent.of(
                        org, "DLP_CRITICAL_UNREVIEWED", "DlpEvent", saved.getId(), payload));
                }
                if ("TOP_SECRET".equalsIgnoreCase(saved.getDataClassification())
                        && "BLOCK".equalsIgnoreCase(saved.getActionTaken())) {
                    publisher.publishEvent(DomainEvent.of(
                        org, "DLP_TOP_SECRET_BLOCKED", "DlpEvent", saved.getId(), payload));
                }
                return saved;
            });
    }

    @Transactional
    public DlpEvent review(Long id, String outcome, String notes) {
        DlpEvent e = get(id);
        e.setReviewed(true);
        e.setReviewOutcome(outcome);
        e.setReviewNotes(notes);
        DlpEvent saved = repo.save(e);
        audit.log("DLP_REVIEW", "DlpEvent", id, "Outcome: " + outcome);
        return saved;
    }
}
