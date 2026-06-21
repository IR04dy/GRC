package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.ZtMaturity;
import com.tss.grc.module.socops.repository.ZtMaturityRepository;
import jakarta.persistence.EntityNotFoundException;
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
        // SEC-CYB-002 — on create force id=null so a client-supplied id can't
        // overwrite an existing row via merge; on update load the row scoped
        // to the caller's tenant and copy only mutable fields.
        Long org = TenantContext.getCurrentTenant();
        boolean isNew = in.getId() == null;
        ZtMaturity target;
        if (isNew) {
            in.setId(null);
            in.setOrganisationId(org);
            target = in;
        } else {
            ZtMaturity existing = repo.findByIdAndOrganisationId(in.getId(), org)
                .orElseThrow(() -> new EntityNotFoundException("ZtMaturity " + in.getId()));
            copyMutable(in, existing);
            target = existing;
        }
        ZtMaturity saved = repo.save(target);
        audit.log(isNew ? "ZT_MATURITY_CREATE" : "ZT_MATURITY_UPDATE",
            "ZtMaturity", saved.getId(),
            saved.getAssessmentCode() + " · " + saved.getPillar());
        return saved;
    }

    /** Copy client-mutable fields onto the tenant-owned managed entity. id,
     *  organisationId and audit columns are deliberately never copied. */
    private void copyMutable(ZtMaturity in, ZtMaturity target) {
        target.setAssessmentCode(in.getAssessmentCode());
        target.setPillar(in.getPillar());
        target.setCurrentStage(in.getCurrentStage());
        target.setTargetStage(in.getTargetStage());
        target.setScore(in.getScore());
        target.setEvidenceSummary(in.getEvidenceSummary());
        target.setGapSummary(in.getGapSummary());
        target.setActionPlan(in.getActionPlan());
        target.setAssessedAt(in.getAssessedAt());
        target.setAssessedBy(in.getAssessedBy());
    }
}
