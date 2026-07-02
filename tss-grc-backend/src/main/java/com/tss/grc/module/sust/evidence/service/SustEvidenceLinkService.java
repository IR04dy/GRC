package com.tss.grc.module.sust.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.entity.EvidenceLink;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.evidence.repository.EvidenceLinkRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository;
import com.tss.grc.module.sust.cma.repository.CmaDisclosureRepository;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
import com.tss.grc.module.sust.metrics.repository.EsgMetricRepository;
import com.tss.grc.module.sust.sgi.repository.SgiCommitmentRepository;
import com.tss.grc.module.sust.tcfd.repository.TcfdItemRepository;
import com.tss.grc.module.sust.vendor.repository.VendorEsgSurveyRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * S-C — sustainability ↔ generic-evidence bridge.
 *
 * <p>The sustainability module no longer keeps its own evidence silo for the
 * heavy lifting: the full request → upload (real file, SHA-256) → review →
 * reject → resubmit → approve → version → expire lifecycle is owned by
 * {@code module/evidence} ({@code /api/v1/evidence/*}). This bridge attaches
 * an already-collected {@link EvidenceItem} to a sustainability domain entity
 * — it sets the entity's {@code evidence_item_id} FK <em>and</em> records a
 * generic {@link EvidenceLink} so the evidence-package endpoint can bundle
 * every linked file + hash for an assurer.
 */
@Service
@RequiredArgsConstructor
public class SustEvidenceLinkService {

    public static final String MODULE = "SUSTAINABILITY";

    /** The sustainability entity types that carry an {@code evidence_item_id} FK (V250, +V256 vendor survey). */
    private static final Set<String> ENTITY_TYPES = Set.of(
        "GhgEmissionRecord", "CmaDisclosure", "TcfdItem",
        "SgiCommitment", "ClimateRiskOpportunity", "EsgMetric", "VendorEsgSurvey");

    private final EvidenceItemRepository evidenceItemRepo;
    private final EvidenceLinkRepository evidenceLinkRepo;
    private final AuditLogService audit;

    private final GhgEmissionRecordRepository       ghgRepo;
    private final CmaDisclosureRepository           cmaRepo;
    private final TcfdItemRepository                tcfdRepo;
    private final SgiCommitmentRepository           sgiRepo;
    private final ClimateRiskOpportunityRepository  climateRepo;
    private final EsgMetricRepository               metricRepo;
    private final VendorEsgSurveyRepository          vendorSurveyRepo;

    private Long org() { return TenantContext.getCurrentTenant(); }

    /**
     * Link a real, uploaded + hashed evidence item to a sustainability entity.
     * Sets the entity's {@code evidence_item_id} FK and records a generic
     * {@link EvidenceLink}. Idempotent per (entity, evidence item).
     */
    @Transactional
    public Map<String, Object> link(String entityType, Long entityId, Long evidenceItemId, String notes) {
        Long orgId = org();
        if (!ENTITY_TYPES.contains(entityType))
            throw new IllegalArgumentException("entityType must be one of " + ENTITY_TYPES);

        // 1) the evidence item must exist and belong to this tenant.
        EvidenceItem item = evidenceItemRepo.findById(evidenceItemId)
            .filter(e -> orgId.equals(e.getOrganisationId()))
            .orElseThrow(() -> new EntityNotFoundException("Evidence item " + evidenceItemId));

        // 2) set the FK on the (tenant-scoped) sustainability entity.
        setEntityFk(entityType, entityId, evidenceItemId, orgId);

        // 3) record the generic link — idempotent per (entity, item).
        boolean exists = evidenceLinkRepo
            .findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(orgId, MODULE, entityId)
            .stream()
            .anyMatch(l -> evidenceItemId.equals(l.getEvidenceId()) && entityType.equals(l.getLinkedEntityType()));
        if (!exists) {
            EvidenceLink link = new EvidenceLink();
            link.setOrganisationId(orgId);
            link.setEvidenceId(evidenceItemId);
            link.setLinkedModule(MODULE);
            link.setLinkedEntityType(entityType);
            link.setLinkedEntityId(entityId);
            link.setNotes(notes);
            evidenceLinkRepo.save(link);
        }

        audit.log("LINK_EVIDENCE", entityType, entityId,
            "Linked evidence item " + evidenceItemId + " to " + entityType + " " + entityId);
        return manifestEntry(item, entityType, entityId);
    }

    private void setEntityFk(String entityType, Long entityId, Long evidenceItemId, Long orgId) {
        switch (entityType) {
            case "GhgEmissionRecord" -> {
                var e = ghgRepo.findByIdAndOrganisationId(entityId, orgId).orElseThrow(() -> nf(entityType, entityId));
                e.setEvidenceItemId(evidenceItemId); ghgRepo.save(e);
            }
            case "CmaDisclosure" -> {
                var e = cmaRepo.findByIdAndOrganisationId(entityId, orgId).orElseThrow(() -> nf(entityType, entityId));
                e.setEvidenceItemId(evidenceItemId); cmaRepo.save(e);
            }
            case "TcfdItem" -> {
                var e = tcfdRepo.findByIdAndOrganisationId(entityId, orgId).orElseThrow(() -> nf(entityType, entityId));
                e.setEvidenceItemId(evidenceItemId); tcfdRepo.save(e);
            }
            case "SgiCommitment" -> {
                var e = sgiRepo.findByIdAndOrganisationId(entityId, orgId).orElseThrow(() -> nf(entityType, entityId));
                e.setEvidenceItemId(evidenceItemId); sgiRepo.save(e);
            }
            case "ClimateRiskOpportunity" -> {
                var e = climateRepo.findByIdAndOrganisationId(entityId, orgId).orElseThrow(() -> nf(entityType, entityId));
                e.setEvidenceItemId(evidenceItemId); climateRepo.save(e);
            }
            case "EsgMetric" -> {
                var e = metricRepo.findByIdAndOrganisationId(entityId, orgId).orElseThrow(() -> nf(entityType, entityId));
                e.setEvidenceItemId(evidenceItemId); metricRepo.save(e);
            }
            case "VendorEsgSurvey" -> {
                var e = vendorSurveyRepo.findByIdAndOrganisationId(entityId, orgId).orElseThrow(() -> nf(entityType, entityId));
                e.setEvidenceItemId(evidenceItemId); vendorSurveyRepo.save(e);
            }
            default -> throw new IllegalArgumentException("entityType must be one of " + ENTITY_TYPES);
        }
    }

    private static EntityNotFoundException nf(String type, Long id) {
        return new EntityNotFoundException(type + " " + id);
    }

    /** Every real evidence item linked to a sustainability entity. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForEntity(String entityType, Long entityId) {
        Long orgId = org();
        return evidenceLinkRepo
            .findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(orgId, MODULE, entityId)
            .stream()
            .filter(l -> entityType == null || entityType.equals(l.getLinkedEntityType()))
            .map(l -> evidenceItemRepo.findById(l.getEvidenceId()).orElse(null))
            .filter(Objects::nonNull)
            .filter(i -> orgId.equals(i.getOrganisationId()))
            .map(i -> manifestEntry(i, entityType, entityId))
            .toList();
    }

    /**
     * Evidence package — bundles every sustainability-linked evidence item with
     * its file name + SHA-256 hash + status + validity into a manifest an
     * assurer can trace. Optionally filtered to one entity type.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildPackage(String entityType) {
        Long orgId = org();
        List<Map<String, Object>> items = new ArrayList<>();
        int withFile = 0, withHash = 0;
        for (EvidenceLink l : evidenceLinkRepo.findByOrganisationIdAndLinkedModuleAndIsDeletedFalse(orgId, MODULE)) {
            if (entityType != null && !entityType.equals(l.getLinkedEntityType())) continue;
            EvidenceItem i = evidenceItemRepo.findById(l.getEvidenceId())
                .filter(x -> orgId.equals(x.getOrganisationId())).orElse(null);
            if (i == null) continue;
            items.add(manifestEntry(i, l.getLinkedEntityType(), l.getLinkedEntityId()));
            if (i.getFileName() != null)     withFile++;
            if (i.getEvidenceHash() != null) withHash++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("module", MODULE);
        out.put("entityTypeFilter", entityType);
        out.put("itemCount", items.size());
        out.put("filesPresent", withFile);
        out.put("hashesPresent", withHash);
        out.put("items", items);
        return out;
    }

    private Map<String, Object> manifestEntry(EvidenceItem i, String entityType, Long entityId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evidenceItemId", i.getId());
        m.put("linkedEntityType", entityType);
        m.put("linkedEntityId", entityId);
        m.put("titleEn", i.getTitleEn());
        m.put("evidenceType", i.getEvidenceType());
        m.put("status", i.getStatus());
        m.put("fileName", i.getFileName());
        m.put("fileSize", i.getFileSize());
        m.put("sha256", i.getEvidenceHash());
        m.put("validFrom", i.getValidFrom());
        m.put("validTo", i.getValidTo());
        m.put("version", i.getVersion());
        return m;
    }
}
