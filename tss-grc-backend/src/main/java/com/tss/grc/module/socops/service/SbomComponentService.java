package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.SbomComponent;
import com.tss.grc.module.socops.repository.SbomComponentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SbomComponentService {

    private final SbomComponentRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public Page<SbomComponent> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByApplicationNameAscComponentNameAsc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional
    public SbomComponent save(SbomComponent in) {
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        SbomComponent saved = repo.save(in);
        audit.log(isNew ? "SBOM_CREATE" : "SBOM_UPDATE",
            "SbomComponent", saved.getId(),
            saved.getApplicationName() + " · " + saved.getComponentName());
        return saved;
    }
}
