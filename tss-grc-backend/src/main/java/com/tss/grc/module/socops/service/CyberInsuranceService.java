package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.CyberInsurance;
import com.tss.grc.module.socops.repository.CyberInsuranceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CyberInsuranceService {

    private final CyberInsuranceRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public List<CyberInsurance> list() {
        return repo.findByOrganisationIdOrderByEffectiveFromDesc(TenantContext.getCurrentTenant());
    }

    @Transactional(readOnly = true)
    public CyberInsurance get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("CyberInsurance " + id));
    }

    @Transactional(readOnly = true)
    public List<CyberInsurance> renewalsDueWithinDays(int days) {
        LocalDate today = LocalDate.now();
        return repo.findRenewalsDueBetween(
            TenantContext.getCurrentTenant(), today, today.plusDays(days));
    }

    @Transactional
    public CyberInsurance save(CyberInsurance in) {
        // SEC-CYB-002 — on create force id=null so a client-supplied id can't
        // overwrite an existing row via merge; on update load the row scoped
        // to the caller's tenant and copy only mutable fields.
        Long org = TenantContext.getCurrentTenant();
        boolean isNew = in.getId() == null;
        CyberInsurance target;
        if (isNew) {
            in.setId(null);
            in.setOrganisationId(org);
            target = in;
        } else {
            CyberInsurance existing = repo.findByIdAndOrganisationId(in.getId(), org)
                .orElseThrow(() -> new EntityNotFoundException("CyberInsurance " + in.getId()));
            copyMutable(in, existing);
            target = existing;
        }
        CyberInsurance saved = repo.save(target);
        audit.log(isNew ? "CYBER_INS_CREATE" : "CYBER_INS_UPDATE",
            "CyberInsurance", saved.getId(),
            saved.getPolicyNumber() + " · " + saved.getCarrierName());
        return saved;
    }

    /** Copy client-mutable fields onto the tenant-owned managed entity. id,
     *  organisationId and audit columns are deliberately never copied. */
    private void copyMutable(CyberInsurance in, CyberInsurance target) {
        target.setPolicyNumber(in.getPolicyNumber());
        target.setCarrierName(in.getCarrierName());
        target.setBrokerName(in.getBrokerName());
        target.setPolicyType(in.getPolicyType());
        target.setCoverageAreas(in.getCoverageAreas());
        target.setAggregateLimitSar(in.getAggregateLimitSar());
        target.setPerEventLimitSar(in.getPerEventLimitSar());
        target.setRetentionSar(in.getRetentionSar());
        target.setEffectiveFrom(in.getEffectiveFrom());
        target.setEffectiveTo(in.getEffectiveTo());
        target.setRenewalDueAt(in.getRenewalDueAt());
        target.setStatus(in.getStatus());
        target.setUnderwritingQuestionnaireId(in.getUnderwritingQuestionnaireId());
        target.setLastRenewalResponsesAt(in.getLastRenewalResponsesAt());
        target.setClaimCount(in.getClaimCount());
        target.setLastClaimAt(in.getLastClaimAt());
        target.setLastClaimPaidSar(in.getLastClaimPaidSar());
        target.setAggregateClaimsPaidSar(in.getAggregateClaimsPaidSar());
        target.setNotes(in.getNotes());
    }

    @Transactional
    public void delete(Long id) {
        CyberInsurance p = get(id);
        p.softDelete(null);
        repo.save(p);
        audit.log("CYBER_INS_DELETE", "CyberInsurance", id, "Soft-deleted");
    }
}
