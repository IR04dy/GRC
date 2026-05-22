package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.OtAsset;
import com.tss.grc.module.socops.repository.OtAssetRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OtAssetService {

    private final OtAssetRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public Page<OtAsset> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByPurdueLevelAscAssetCodeAsc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public OtAsset get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("OtAsset " + id));
    }

    @Transactional
    public OtAsset save(OtAsset in) {
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        OtAsset saved = repo.save(in);
        audit.log(isNew ? "OT_ASSET_CREATE" : "OT_ASSET_UPDATE",
            "OtAsset", saved.getId(),
            saved.getAssetCode() + " · L" + saved.getPurdueLevel() + " · " + saved.getCriticality());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        OtAsset a = get(id);
        a.softDelete(null);
        repo.save(a);
        audit.log("OT_ASSET_DELETE", "OtAsset", id, "Soft-deleted");
    }
}
