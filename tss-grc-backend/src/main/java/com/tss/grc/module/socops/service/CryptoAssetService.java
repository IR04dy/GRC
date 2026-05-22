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
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        CryptoAsset saved = repo.save(in);
        audit.log(isNew ? "CRYPTO_ASSET_CREATE" : "CRYPTO_ASSET_UPDATE",
            "CryptoAsset", saved.getId(), saved.getAssetCode());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        CryptoAsset c = get(id);
        c.softDelete(null);
        repo.save(c);
        audit.log("CRYPTO_ASSET_DELETE", "CryptoAsset", id, "Soft-deleted");
    }
}
