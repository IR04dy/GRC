package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.MaDueDiligence;
import com.tss.grc.module.socops.repository.MaDueDiligenceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaDueDiligenceService {

    private final MaDueDiligenceRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public Page<MaDueDiligence> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByCreatedAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public MaDueDiligence get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("MaDueDiligence " + id));
    }

    @Transactional
    public MaDueDiligence save(MaDueDiligence in) {
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        MaDueDiligence saved = repo.save(in);
        audit.log(isNew ? "MA_DD_CREATE" : "MA_DD_UPDATE",
            "MaDueDiligence", saved.getId(),
            saved.getEngagementCode() + " · " + saved.getTargetEntityName()
                + " · " + saved.getPhase());
        return saved;
    }

    @Transactional
    public MaDueDiligence transitionPhase(Long id, String newPhase) {
        MaDueDiligence dd = get(id);
        String old = dd.getPhase();
        dd.setPhase(newPhase);
        MaDueDiligence saved = repo.save(dd);
        audit.log("MA_DD_TRANSITION", "MaDueDiligence", id,
            "Phase " + old + " → " + newPhase);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        MaDueDiligence dd = get(id);
        dd.softDelete(null);
        repo.save(dd);
        audit.log("MA_DD_DELETE", "MaDueDiligence", id, "Soft-deleted");
    }
}
