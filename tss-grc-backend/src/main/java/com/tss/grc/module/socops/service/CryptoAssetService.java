package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.CryptoAsset;
import com.tss.grc.module.socops.repository.CryptoAssetRepository;
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
public class CryptoAssetService {

    private final CryptoAssetRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public Page<CryptoAsset> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByAssetCodeAsc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public CryptoAsset get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("CryptoAsset " + id));
    }

    @Transactional(readOnly = true)
    public List<CryptoAsset> expiringWithinDays(int days) {
        LocalDateTime now = LocalDateTime.now();
        return repo.findExpiringBetween(
            TenantContext.getCurrentTenant(), now, now.plusDays(days));
    }

    @Transactional
    public CryptoAsset save(CryptoAsset in) {
        // SEC-CYB-002 — on create force id=null so a client-supplied id can't
        // overwrite an existing row via merge; on update load the row scoped
        // to the caller's tenant and copy only mutable fields.
        Long org = TenantContext.getCurrentTenant();
        boolean isNew = in.getId() == null;
        CryptoAsset target;
        if (isNew) {
            in.setId(null);
            in.setOrganisationId(org);
            target = in;
        } else {
            CryptoAsset existing = repo.findByIdAndOrganisationId(in.getId(), org)
                .orElseThrow(() -> new EntityNotFoundException("CryptoAsset " + in.getId()));
            copyMutable(in, existing);
            target = existing;
        }
        CryptoAsset saved = repo.save(target);
        audit.log(isNew ? "CRYPTO_ASSET_CREATE" : "CRYPTO_ASSET_UPDATE",
            "CryptoAsset", saved.getId(), saved.getAssetCode());
        return saved;
    }

    /** Copy client-mutable fields onto the tenant-owned managed entity. id,
     *  organisationId and audit columns are deliberately never copied. */
    private void copyMutable(CryptoAsset in, CryptoAsset target) {
        target.setAssetCode(in.getAssetCode());
        target.setAssetType(in.getAssetType());
        target.setTitleEn(in.getTitleEn());
        target.setTitleAr(in.getTitleAr());
        target.setIssuedBy(in.getIssuedBy());
        target.setSubjectDn(in.getSubjectDn());
        target.setFingerprint(in.getFingerprint());
        target.setAlgorithm(in.getAlgorithm());
        target.setKeyLength(in.getKeyLength());
        target.setQuantumSafe(in.getQuantumSafe());
        target.setValidFrom(in.getValidFrom());
        target.setValidUntil(in.getValidUntil());
        target.setDeployedOn(in.getDeployedOn());
        target.setOwner(in.getOwner());
        target.setRotationPeriodDays(in.getRotationPeriodDays());
        target.setLastRotatedAt(in.getLastRotatedAt());
        target.setClassification(in.getClassification());
        target.setStatus(in.getStatus());
    }

    @Transactional
    public void delete(Long id) {
        CryptoAsset c = get(id);
        c.softDelete(null);
        repo.save(c);
        audit.log("CRYPTO_ASSET_DELETE", "CryptoAsset", id, "Soft-deleted");
    }
}
