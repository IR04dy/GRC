package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.ZtMaturity;
import com.tss.grc.module.socops.repository.ZtMaturityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZtMaturityService {

    private final ZtMaturityRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public List<ZtMaturity> list() {
        return repo.findByOrganisationIdOrderByAssessmentCodeDescPillarAsc(
            TenantContext.getCurrentTenant());
    }

    @Transactional(readOnly = true)
    public List<ZtMaturity> byAssessment(String code) {
        return repo.findByOrganisationIdAndAssessmentCode(
            TenantContext.getCurrentTenant(), code);
    }

    @Transactional
    public ZtMaturity save(ZtMaturity in) {
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        ZtMaturity saved = repo.save(in);
        audit.log(isNew ? "ZT_MATURITY_CREATE" : "ZT_MATURITY_UPDATE",
            "ZtMaturity", saved.getId(),
            saved.getAssessmentCode() + " · " + saved.getPillar());
        return saved;
    }
}
