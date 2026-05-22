package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.IamJmlEvent;
import com.tss.grc.module.socops.repository.IamJmlEventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IamJmlEventService {

    private final IamJmlEventRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public Page<IamJmlEvent> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByOccurredAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public IamJmlEvent get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("IamJmlEvent " + id));
    }

    @Transactional(readOnly = true)
    public List<IamJmlEvent> privilegedReviewsDueWithinDays(int days) {
        LocalDateTime now = LocalDateTime.now();
        return repo.findPrivilegedReviewsDueBetween(
            TenantContext.getCurrentTenant(), now, now.plusDays(days));
    }

    @Transactional
    public IamJmlEvent save(IamJmlEvent in) {
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        IamJmlEvent saved = repo.save(in);
        audit.log(isNew ? "JML_CREATE" : "JML_UPDATE",
            "IamJmlEvent", saved.getId(),
            saved.getEventType() + " · " + saved.getSubjectUsername()
                + (Boolean.TRUE.equals(saved.getPrivileged()) ? " · PRIVILEGED" : ""));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        IamJmlEvent e = get(id);
        e.softDelete(null);
        repo.save(e);
        audit.log("JML_DELETE", "IamJmlEvent", id, "Soft-deleted");
    }
}
