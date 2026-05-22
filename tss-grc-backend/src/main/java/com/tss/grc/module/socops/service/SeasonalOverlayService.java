package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.SeasonalOverlay;
import com.tss.grc.module.socops.repository.SeasonalOverlayRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeasonalOverlayService {

    private final SeasonalOverlayRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public List<SeasonalOverlay> list() {
        return repo.findByOrganisationIdOrderByStartsAtAsc(TenantContext.getCurrentTenant());
    }

    @Transactional(readOnly = true)
    public SeasonalOverlay get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("SeasonalOverlay " + id));
    }

    /** Returns the overlays active at {@code now} — used by the F8 engine. */
    @Transactional(readOnly = true)
    public List<SeasonalOverlay> activeNow() {
        return repo.findActiveAt(TenantContext.getCurrentTenant(), LocalDateTime.now());
    }

    @Transactional
    public SeasonalOverlay save(SeasonalOverlay in) {
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        SeasonalOverlay saved = repo.save(in);
        audit.log(isNew ? "SEASONAL_OVERLAY_CREATE" : "SEASONAL_OVERLAY_UPDATE",
            "SeasonalOverlay", saved.getId(), saved.getOverlayCode());
        return saved;
    }
}
