package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.AwarenessCampaign;
import com.tss.grc.module.socops.repository.AwarenessCampaignRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AwarenessCampaignService {

    private final AwarenessCampaignRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public Page<AwarenessCampaign> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByStartedAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public AwarenessCampaign get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("AwarenessCampaign " + id));
    }

    @Transactional
    public AwarenessCampaign save(AwarenessCampaign in) {
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        AwarenessCampaign saved = repo.save(in);
        audit.log(isNew ? "AWARENESS_CREATE" : "AWARENESS_UPDATE",
            "AwarenessCampaign", saved.getId(), saved.getCampaignCode());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        AwarenessCampaign c = get(id);
        c.softDelete(null);
        repo.save(c);
        audit.log("AWARENESS_DELETE", "AwarenessCampaign", id, "Soft-deleted");
    }
}
