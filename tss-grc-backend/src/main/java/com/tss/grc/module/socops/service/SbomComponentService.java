package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.SbomComponent;
import com.tss.grc.module.socops.repository.SbomComponentRepository;
import jakarta.persistence.EntityNotFoundException;
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
        // SEC-CYB-002 — on create force id=null so a client-supplied id can't
        // overwrite an existing row via merge; on update load the row scoped
        // to the caller's tenant and copy only mutable fields.
        Long org = TenantContext.getCurrentTenant();
        boolean isNew = in.getId() == null;
        SbomComponent target;
        if (isNew) {
            in.setId(null);
            in.setOrganisationId(org);
            target = in;
        } else {
            SbomComponent existing = repo.findByIdAndOrganisationId(in.getId(), org)
                .orElseThrow(() -> new EntityNotFoundException("SbomComponent " + in.getId()));
            copyMutable(in, existing);
            target = existing;
        }
        SbomComponent saved = repo.save(target);
        audit.log(isNew ? "SBOM_CREATE" : "SBOM_UPDATE",
            "SbomComponent", saved.getId(),
            saved.getApplicationName() + " · " + saved.getComponentName());
        return saved;
    }

    /** Copy client-mutable fields onto the tenant-owned managed entity. id,
     *  organisationId and audit columns are deliberately never copied. */
    private void copyMutable(SbomComponent in, SbomComponent target) {
        target.setApplicationId(in.getApplicationId());
        target.setApplicationName(in.getApplicationName());
        target.setComponentName(in.getComponentName());
        target.setComponentVersion(in.getComponentVersion());
        target.setPackageEcosystem(in.getPackageEcosystem());
        target.setPurl(in.getPurl());
        target.setLicense(in.getLicense());
        target.setSupplier(in.getSupplier());
        target.setHasKnownCves(in.getHasKnownCves());
        target.setHighestCveSeverity(in.getHighestCveSeverity());
        target.setUpgradeAvailable(in.getUpgradeAvailable());
        target.setLastScannedAt(in.getLastScannedAt());
    }
}
