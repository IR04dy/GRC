package com.tss.grc.module.socops.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.repository.CyberAssetRepository;
import com.tss.grc.module.cyber.repository.VulnerabilityRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.ThreatIntel;
import com.tss.grc.module.socops.entity.ThreatIntelAction;
import com.tss.grc.module.socops.repository.DetectionRuleRepository;
import com.tss.grc.module.socops.repository.ThreatIntelActionRepository;
import com.tss.grc.module.socops.repository.ThreatIntelRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ThreatIntelService {

    private final ThreatIntelRepository        repo;
    private final AuditLogService              audit;
    private final ApplicationEventPublisher    publisher;
    // SOC-E — actioning an IOC now links it to a concrete platform target.
    private final ThreatIntelActionRepository  actions;
    private final CyberAssetRepository         cyberAssets;
    private final VulnerabilityRepository      vulnerabilities;
    private final DetectionRuleRepository      detections;

    static final Set<String> ACTION_TYPES = Set.of(
        "BLOCK", "SCAN", "HUNT", "MONITOR", "PATCH", "BRIEF", "LINK_DETECTION", "REVIEWED", "ESCALATE");
    static final Set<String> TARGET_TYPES = Set.of("ASSET", "VULNERABILITY", "DETECTION");

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

    /**
     * SOC-E — action an IOC. Records WHAT was done ({@code actionType}) and, optionally, WHICH
     * concrete platform target it links to ({@code ASSET} / {@code VULNERABILITY} / {@code DETECTION}
     * — validated to exist in this tenant). Appends an immutable {@link ThreatIntelAction} row and
     * flips the parent's denormalised {@code actioned} summary flags (last action wins). A stray
     * {@code targetId} without a target type is ignored; an unknown target id is a 400, not a 500.
     */
    @Transactional
    public ThreatIntel actionIoc(Long id, String actionType, String targetType, Long targetId, String notes) {
        Long org = TenantContext.getCurrentTenant();
        ThreatIntel ti = get(id);                       // validates existence + tenant scope

        String type = (actionType == null || actionType.isBlank())
            ? "REVIEWED" : actionType.trim().toUpperCase();
        if (!ACTION_TYPES.contains(type))
            throw new IllegalArgumentException("actionType must be one of " + ACTION_TYPES);

        String tType = null;
        String tLabel = null;
        if (targetType != null && !targetType.isBlank() && !"NONE".equalsIgnoreCase(targetType)) {
            tType = targetType.trim().toUpperCase();
            if (!TARGET_TYPES.contains(tType))
                throw new IllegalArgumentException("targetType must be one of " + TARGET_TYPES + " or NONE");
            if (targetId == null)
                throw new IllegalArgumentException("targetId is required when targetType is " + tType);
            tLabel = resolveTargetLabel(org, tType, targetId);   // validates the target exists in this org
        } else {
            targetId = null;                            // no/NONE target → drop any stray id
        }

        ThreatIntelAction a = new ThreatIntelAction();
        a.setOrganisationId(org);
        a.setThreatIntelId(ti.getId());
        a.setActionType(type);
        a.setTargetType(tType);
        a.setTargetId(targetId);
        a.setTargetLabel(tLabel);
        a.setNotes(notes);
        a.setActionedByUserId(CurrentActor.userId());
        a.setActionedBy(CurrentActor.username());
        a.setActionedAt(LocalDateTime.now());
        actions.save(a);

        // Denormalised summary on the parent — keeps the "actioned/pending" queue + existing
        // list UI working; the full history lives in threat_intel_action.
        ti.setActioned(true);
        ti.setActionedAt(a.getActionedAt());
        ti.setActionedByUserId(a.getActionedByUserId());
        ti.setActionNotes(notes);
        ThreatIntel saved = repo.save(ti);

        audit.log("TI_ACTIONED", "ThreatIntel", id,
            type + (tType != null ? " → " + tType + " #" + targetId + " (" + tLabel + ")" : "")
                + (notes == null || notes.isBlank() ? "" : " · " + notes));
        return saved;
    }

    /** SOC-E — the full action history for one IOC (newest first), tenant-scoped. */
    @Transactional(readOnly = true)
    public List<ThreatIntelAction> listActions(Long id) {
        ThreatIntel ti = get(id);                       // validates existence + tenant scope
        return actions.findByOrganisationIdAndThreatIntelIdOrderByActionedAtDesc(
            ti.getOrganisationId(), ti.getId());
    }

    /** Resolve a human label for a link target, validating it exists in the caller's tenant. */
    private String resolveTargetLabel(Long org, String targetType, Long targetId) {
        return switch (targetType) {
            case "ASSET" -> cyberAssets.findByIdAndOrganisationId(targetId, org)
                .map(a -> a.getAssetName())
                .orElseThrow(() -> new IllegalArgumentException("target ASSET " + targetId + " not found"));
            case "VULNERABILITY" -> vulnerabilities.findByIdAndOrganisationId(targetId, org)
                .map(v -> v.getCveId() != null && !v.getCveId().isBlank() ? v.getCveId() : v.getTitleEn())
                .orElseThrow(() -> new IllegalArgumentException("target VULNERABILITY " + targetId + " not found"));
            case "DETECTION" -> detections.findByIdAndOrganisationId(targetId, org)
                .map(d -> d.getRuleCode() + " · " + d.getTitleEn())
                .orElseThrow(() -> new IllegalArgumentException("target DETECTION " + targetId + " not found"));
            default -> throw new IllegalArgumentException("Unsupported targetType " + targetType);
        };
    }

    /** Legacy shim — the old "mark done" path (no concrete target) now records a REVIEWED action. */
    @Transactional
    public ThreatIntel markActioned(Long id, Long userId, String notes) {
        return actionIoc(id, "REVIEWED", null, null, notes);
    }

    @Transactional
    public void delete(Long id) {
        ThreatIntel ti = get(id);
        ti.softDelete(null);
        repo.save(ti);
        audit.log("TI_DELETE", "ThreatIntel", id, "Soft-deleted");
    }
}
