package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.DlpEvent;
import com.tss.grc.module.socops.repository.DlpEventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DlpEventService {

    private final DlpEventRepository repo;
    private final AuditLogService audit;

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
                DlpEvent saved = repo.save(in);
                audit.log("DLP_INGEST", "DlpEvent", saved.getId(),
                    saved.getSourceSystem() + " · " + saved.getActionTaken()
                        + " · " + (saved.getDataClassification() == null ? "—" : saved.getDataClassification()));
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
