package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.ThreatIntel;
import com.tss.grc.module.socops.repository.ThreatIntelRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ThreatIntelService {

    private final ThreatIntelRepository      repo;
    private final AuditLogService            audit;
    private final ApplicationEventPublisher  publisher;

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
                    // SEC-CYB-002 — force id=null so a forged id on the body
                    // can't overwrite an existing (cross-tenant) row via merge.
                    in.setId(null);
                    ThreatIntel saved = repo.save(in);
                    audit.log("TI_CREATE", "ThreatIntel", saved.getId(),
                        saved.getSource() + " · " + saved.getExternalId());
                    // F8 — AR-130 fans CRITICAL/HIGH TI rows out to SOC + IR.
                    // The "actionable" tier is the cutoff Saudi-CERT, NCA and
                    // MISP advisories use; LOW/MEDIUM stays in the queue.
                    if ("CRITICAL".equalsIgnoreCase(saved.getSeverity())
                            || "HIGH".equalsIgnoreCase(saved.getSeverity())) {
                        publisher.publishEvent(DomainEvent.of(
                            org, "TI_ACTIONABLE", "ThreatIntel", saved.getId(),
                            Map.of("source",   saved.getSource()   == null ? "" : saved.getSource(),
                                   "severity", saved.getSeverity() == null ? "" : saved.getSeverity(),
                                   "iocType",  saved.getIocType()  == null ? "" : saved.getIocType())));
                    }
                    return saved;
                });
        }
        // SEC-CYB-002 — explicit update path: load the row scoped to the
        // caller's tenant and copy only mutable fields onto the managed
        // entity. A detached, client-built entity is never merged.
        ThreatIntel existing = repo.findByIdAndOrganisationId(in.getId(), org)
            .orElseThrow(() -> new EntityNotFoundException("ThreatIntel " + in.getId()));
        copyMutable(in, existing);
        ThreatIntel saved = repo.save(existing);
        audit.log("TI_UPDATE", "ThreatIntel", saved.getId(),
            saved.getSource() + " · " + saved.getExternalId());
        return saved;
    }

    /** Copy client-mutable fields onto the tenant-owned managed entity. id,
     *  organisationId and audit columns are deliberately never copied. */
    private void copyMutable(ThreatIntel in, ThreatIntel target) {
        target.setExternalId(in.getExternalId());
        target.setSource(in.getSource());
        target.setPublishedAt(in.getPublishedAt());
        target.setExpiresAt(in.getExpiresAt());
        target.setTitleEn(in.getTitleEn());
        target.setTitleAr(in.getTitleAr());
        target.setSummary(in.getSummary());
        target.setIocType(in.getIocType());
        target.setIocValue(in.getIocValue());
        target.setSeverity(in.getSeverity());
        target.setConfidence(in.getConfidence());
        target.setTlp(in.getTlp());
        target.setMitreTactics(in.getMitreTactics());
        target.setMitreTechniques(in.getMitreTechniques());
        target.setAffectedSectors(in.getAffectedSectors());
        target.setActioned(in.getActioned());
        target.setActionedAt(in.getActionedAt());
        target.setActionedByUserId(in.getActionedByUserId());
        target.setActionNotes(in.getActionNotes());
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
