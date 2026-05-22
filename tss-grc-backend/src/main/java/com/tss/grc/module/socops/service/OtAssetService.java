package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.OtAsset;
import com.tss.grc.module.socops.repository.OtAssetRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OtAssetService {

    private final OtAssetRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

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
        // Snapshot prior remote-access state before write — only fire AR-131
        // (OT remote access activated) on the transition OFF→ON. Without
        // the prior value the rule would refire on every subsequent save.
        Boolean priorRemote = null;
        if (in.getId() != null) {
            OtAsset prior = repo.findByIdAndOrganisationId(in.getId(), TenantContext.getCurrentTenant()).orElse(null);
            if (prior != null) priorRemote = prior.getRemoteAccessEnabled();
        }
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        OtAsset saved = repo.save(in);
        audit.log(isNew ? "OT_ASSET_CREATE" : "OT_ASSET_UPDATE",
            "OtAsset", saved.getId(),
            saved.getAssetCode() + " · L" + saved.getPurdueLevel() + " · " + saved.getCriticality());

        // F8 — AR-131. NCA OTCC + IEC 62443 treat remote OT access as a
        // privileged-by-default change; this fires the moment the flag is
        // set so the CISO sees a fresh approval workflow start.
        boolean nowRemote   = Boolean.TRUE.equals(saved.getRemoteAccessEnabled());
        boolean wasRemote   = Boolean.TRUE.equals(priorRemote);
        if (nowRemote && (isNew || !wasRemote)) {
            publisher.publishEvent(DomainEvent.of(
                saved.getOrganisationId(), "OT_REMOTE_ACCESS_ENABLED",
                "OtAsset", saved.getId(),
                Map.of("assetCode",    saved.getAssetCode()    == null ? "" : saved.getAssetCode(),
                       "purdueLevel",  saved.getPurdueLevel()  == null ? 0  : saved.getPurdueLevel(),
                       "criticality",  saved.getCriticality()  == null ? "" : saved.getCriticality(),
                       "siteName",     saved.getSiteName()     == null ? "" : saved.getSiteName())));
        }
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
