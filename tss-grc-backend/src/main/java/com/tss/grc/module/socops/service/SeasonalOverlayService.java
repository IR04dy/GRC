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
        // SEC-CYB-002 — on create force id=null so a client-supplied id can't
        // overwrite an existing row via merge; on update load the row scoped
        // to the caller's tenant and copy only mutable fields.
        Long org = TenantContext.getCurrentTenant();
        boolean isNew = in.getId() == null;
        SeasonalOverlay target;
        if (isNew) {
            in.setId(null);
            in.setOrganisationId(org);
            target = in;
        } else {
            SeasonalOverlay existing = repo.findByIdAndOrganisationId(in.getId(), org)
                .orElseThrow(() -> new EntityNotFoundException("SeasonalOverlay " + in.getId()));
            copyMutable(in, existing);
            target = existing;
        }
        SeasonalOverlay saved = repo.save(target);
        audit.log(isNew ? "SEASONAL_OVERLAY_CREATE" : "SEASONAL_OVERLAY_UPDATE",
            "SeasonalOverlay", saved.getId(), saved.getOverlayCode());
        return saved;
    }

    /** Copy client-mutable fields onto the tenant-owned managed entity. id,
     *  organisationId and audit columns are deliberately never copied. */
    private void copyMutable(SeasonalOverlay in, SeasonalOverlay target) {
        target.setOverlayCode(in.getOverlayCode());
        target.setTitleEn(in.getTitleEn());
        target.setTitleAr(in.getTitleAr());
        target.setStartsAt(in.getStartsAt());
        target.setEndsAt(in.getEndsAt());
        target.setHijriLabel(in.getHijriLabel());
        target.setThreatProfile(in.getThreatProfile());
        target.setAutoTightenKris(in.getAutoTightenKris());
        target.setAutoTightenSlas(in.getAutoTightenSlas());
        target.setNotifyRecipients(in.getNotifyRecipients());
        target.setIsActive(in.getIsActive());
    }
}
