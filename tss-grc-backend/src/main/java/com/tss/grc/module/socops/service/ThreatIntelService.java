package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.ThreatIntel;
import com.tss.grc.module.socops.repository.ThreatIntelRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ThreatIntelService {

    private final ThreatIntelRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public Page<ThreatIntel> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByPublishedAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public ThreatIntel get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("ThreatIntel " + id));
    }

    @Transactional
    public ThreatIntel save(ThreatIntel in) {
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        // Dedup by (source, externalId) — the same advisory may arrive via
        // multiple feeds; we update the existing row rather than creating
        // a duplicate.
        if (in.getId() == null) {
            return repo.findByOrganisationIdAndSourceAndExternalId(org, in.getSource(), in.getExternalId())
                .map(existing -> {
                    existing.setTitleEn(in.getTitleEn());
                    existing.setTitleAr(in.getTitleAr());
                    existing.setSummary(in.getSummary());
                    existing.setSeverity(in.getSeverity());
                    existing.setIocType(in.getIocType());
                    existing.setIocValue(in.getIocValue());
                    existing.setExpiresAt(in.getExpiresAt());
                    ThreatIntel saved = repo.save(existing);
                    audit.log("TI_UPDATE", "ThreatIntel", saved.getId(),
                        saved.getSource() + " · " + saved.getExternalId());
                    return saved;
                })
                .orElseGet(() -> {
                    ThreatIntel saved = repo.save(in);
                    audit.log("TI_CREATE", "ThreatIntel", saved.getId(),
                        saved.getSource() + " · " + saved.getExternalId());
                    return saved;
                });
        }
        ThreatIntel saved = repo.save(in);
        audit.log("TI_UPDATE", "ThreatIntel", saved.getId(),
            saved.getSource() + " · " + saved.getExternalId());
        return saved;
    }

    @Transactional
    public ThreatIntel markActioned(Long id, Long userId, String notes) {
        ThreatIntel ti = get(id);
        ti.setActioned(true);
        ti.setActionedAt(LocalDateTime.now());
        ti.setActionedByUserId(userId);
        ti.setActionNotes(notes);
        ThreatIntel saved = repo.save(ti);
        audit.log("TI_ACTIONED", "ThreatIntel", id, "Actioned: " + (notes == null ? "—" : notes));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        ThreatIntel ti = get(id);
        ti.softDelete(null);
        repo.save(ti);
        audit.log("TI_DELETE", "ThreatIntel", id, "Soft-deleted");
    }
}
