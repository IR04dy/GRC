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
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        CyberInsurance saved = repo.save(in);
        audit.log(isNew ? "CYBER_INS_CREATE" : "CYBER_INS_UPDATE",
            "CyberInsurance", saved.getId(),
            saved.getPolicyNumber() + " · " + saved.getCarrierName());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        CyberInsurance p = get(id);
        p.softDelete(null);
        repo.save(p);
        audit.log("CYBER_INS_DELETE", "CyberInsurance", id, "Soft-deleted");
    }
}
