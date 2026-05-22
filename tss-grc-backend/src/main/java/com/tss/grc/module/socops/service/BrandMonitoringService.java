package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.BrandMonitoring;
import com.tss.grc.module.socops.repository.BrandMonitoringRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BrandMonitoringService {

    private final BrandMonitoringRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public Page<BrandMonitoring> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByDetectedAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public BrandMonitoring get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("BrandMonitoring " + id));
    }

    @Transactional
    public BrandMonitoring ingest(BrandMonitoring in) {
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        return repo.findByOrganisationIdAndSourceSystemAndExternalId(
                org, in.getSourceSystem(), in.getExternalId())
            .map(existing -> existing) // dedup — keep existing record
            .orElseGet(() -> {
                BrandMonitoring saved = repo.save(in);
                audit.log("BRAND_INGEST", "BrandMonitoring", saved.getId(),
                    saved.getSourceSystem() + " · " + saved.getAlertType()
                        + (saved.getAffectedAsset() == null ? "" : " · " + saved.getAffectedAsset()));
                return saved;
            });
    }

    @Transactional
    public BrandMonitoring requestTakedown(Long id) {
        BrandMonitoring a = get(id);
        a.setStatus("TAKEDOWN_REQUESTED");
        a.setTakedownRequestedAt(LocalDateTime.now());
        BrandMonitoring saved = repo.save(a);
        audit.log("BRAND_TAKEDOWN_REQUEST", "BrandMonitoring", id, "Takedown requested");
        return saved;
    }

    @Transactional
    public BrandMonitoring completeTakedown(Long id, String notes) {
        BrandMonitoring a = get(id);
        a.setStatus("TAKEDOWN_COMPLETE");
        a.setTakedownCompletedAt(LocalDateTime.now());
        a.setActionNotes(notes);
        BrandMonitoring saved = repo.save(a);
        audit.log("BRAND_TAKEDOWN_COMPLETE", "BrandMonitoring", id, "Takedown complete: " + notes);
        return saved;
    }
}
