package com.tss.grc.module.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.entity.ConnectorDefinition;
import com.tss.grc.module.evidence.entity.EvidenceControl;
import com.tss.grc.module.evidence.entity.EvidenceFramework;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.entity.EvidenceSubControl;
import com.tss.grc.module.evidence.entity.EvidenceVersion;
import com.tss.grc.module.evidence.repository.ConnectorDefinitionRepository;
import com.tss.grc.module.evidence.repository.EvidenceControlRepository;
import com.tss.grc.module.evidence.repository.EvidenceFrameworkRepository;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.evidence.repository.EvidenceSubControlRepository;
import com.tss.grc.module.evidence.repository.EvidenceVersionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the buyer-assessment §5.9 "evidence-as-a-matrix" view: every
 * framework gets a tab, every framework has its controls and sub-controls, and
 * each control row carries its evidence count, freshness and last-collection
 * metadata. Single evidence may satisfy multiple controls — the matrix shows
 * each linkage explicitly so audit teams can see reuse at a glance.
 */
@Service
@RequiredArgsConstructor
public class EvidenceMatrixService {

    private final EvidenceFrameworkRepository  frameworkRepo;
    private final EvidenceControlRepository    controlRepo;
    private final EvidenceSubControlRepository subControlRepo;
    private final EvidenceItemRepository       itemRepo;
    private final EvidenceVersionRepository    versionRepo;
    private final ConnectorDefinitionRepository connectorRepo;

    /**
     * Returns the full evidence matrix grouped by framework. Shape:
     *   [
     *     { framework: {…}, controls: [
     *         { control: {…}, subControls: [
     *             { subControl: {…}, evidenceCount, freshestExpiry, freshness, methodMix }
     *         ]}
     *     ]}
     *   ]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> matrix() {
        Long org = TenantContext.requireOrganisationId();
        // INTENTIONAL: EvidenceFramework is a PLATFORM-SHARED catalog (NCA ECC,
        // ISO 27001, SAMA CSF, PCI-DSS, etc.) — same for every tenant. The entity
        // has no organisation_id column. The tenant scoping happens at the
        // EvidenceItem level (the org-scoped item query below).
        // Do not refactor to findByOrganisationId — that finder doesn't exist.
        List<EvidenceFramework> frameworks = frameworkRepo.findAll().stream()
            .filter(fw -> !Boolean.FALSE.equals(fw.getIsActive()))
            .toList();
        LocalDate today = LocalDate.now();

        // PERF-NQ-003: batch-load the active control / sub-control / evidence
        // graph in a FIXED 4 queries (frameworks, controls, sub-controls, items)
        // and group in memory — replacing the old 1 + N_fw + N_ctrl + N_sub N+1.
        List<Long> fwIds = frameworks.stream().map(EvidenceFramework::getId).toList();
        List<EvidenceControl> controls = fwIds.isEmpty() ? List.of()
            : controlRepo.findByFrameworkIdInAndIsActiveTrue(fwIds);
        List<Long> ctrlIds = controls.stream().map(EvidenceControl::getId).toList();
        List<EvidenceSubControl> subs = ctrlIds.isEmpty() ? List.of()
            : subControlRepo.findByControlIdInAndIsActiveTrue(ctrlIds);
        List<Long> subIds = subs.stream().map(EvidenceSubControl::getId).toList();
        List<EvidenceItem> items = subIds.isEmpty() ? List.of()
            : itemRepo.findByOrganisationIdAndSubControlIdIn(org, subIds);

        Map<Long, List<EvidenceControl>> controlsByFw = controls.stream()
            .collect(java.util.stream.Collectors.groupingBy(EvidenceControl::getFrameworkId));
        Map<Long, List<EvidenceSubControl>> subsByCtrl = subs.stream()
            .collect(java.util.stream.Collectors.groupingBy(EvidenceSubControl::getControlId));
        Map<Long, List<EvidenceItem>> itemsBySub = items.stream()
            .collect(java.util.stream.Collectors.groupingBy(EvidenceItem::getSubControlId));

        List<Map<String, Object>> out = new ArrayList<>();
        for (EvidenceFramework fw : frameworks) {
            List<EvidenceControl> fwControls = controlsByFw.getOrDefault(fw.getId(), List.of());
            List<Map<String, Object>> controlBlocks = new ArrayList<>();

            for (EvidenceControl c : fwControls) {
                List<EvidenceSubControl> ctrlSubs = subsByCtrl.getOrDefault(c.getId(), List.of());
                List<Map<String, Object>> subBlocks = new ArrayList<>();

                int controlEvidenceCount = 0;
                LocalDate controlFreshestExpiry = null;

                for (EvidenceSubControl s : ctrlSubs) {
                    List<EvidenceItem> subItems = itemsBySub.getOrDefault(s.getId(), List.of());
                    int count = subItems.size();
                    controlEvidenceCount += count;

                    LocalDate freshestExpiry = subItems.stream()
                        .map(EvidenceItem::getValidTo)
                        .filter(java.util.Objects::nonNull)
                        .max(java.util.Comparator.naturalOrder())
                        .orElse(null);
                    if (freshestExpiry != null && (controlFreshestExpiry == null || freshestExpiry.isAfter(controlFreshestExpiry))) {
                        controlFreshestExpiry = freshestExpiry;
                    }

                    Map<String, Long> methodMix = new HashMap<>();
                    for (EvidenceItem it : subItems) {
                        String m = it.getCollectionMethod() != null ? it.getCollectionMethod() : "MANUAL";
                        methodMix.merge(m, 1L, Long::sum);
                    }

                    Map<String, Object> sb = new HashMap<>();
                    sb.put("subControlId",     s.getId());
                    sb.put("subControlCode",   s.getSubControlCode());
                    sb.put("nameEn",           s.getNameEn());
                    sb.put("nameAr",           s.getNameAr());
                    sb.put("evidenceGuidance", s.getEvidenceGuidance());
                    sb.put("evidenceCount",    count);
                    sb.put("freshestExpiry",   freshestExpiry);
                    sb.put("freshness",        freshnessBand(freshestExpiry, today));
                    sb.put("methodMix",        methodMix);
                    subBlocks.add(sb);
                }

                Map<String, Object> cb = new HashMap<>();
                cb.put("controlId",          c.getId());
                cb.put("controlCode",        c.getControlCode());
                cb.put("nameEn",             c.getNameEn());
                cb.put("nameAr",             c.getNameAr());
                cb.put("descriptionEn",      c.getDescriptionEn());
                cb.put("subControls",        subBlocks);
                cb.put("evidenceCount",      controlEvidenceCount);
                cb.put("freshestExpiry",     controlFreshestExpiry);
                cb.put("freshness",          freshnessBand(controlFreshestExpiry, today));
                controlBlocks.add(cb);
            }

            Map<String, Object> fb = new HashMap<>();
            fb.put("frameworkId",   fw.getId());
            fb.put("code",          fw.getCode());
            fb.put("nameEn",        fw.getNameEn());
            fb.put("nameAr",        fw.getNameAr());
            fb.put("version",       fw.getVersion());
            fb.put("issuer",        fw.getIssuer());
            fb.put("controls",      controlBlocks);
            fb.put("controlCount",  controlBlocks.size());
            fb.put("evidenceCount", controlBlocks.stream().mapToInt(b -> (int) b.get("evidenceCount")).sum());
            out.add(fb);
        }
        return out;
    }

    /** Freshness band: GREEN if expiry > 30 days away, AMBER ≤ 30 days, RED if past, GREY if unknown. */
    private String freshnessBand(LocalDate expiry, LocalDate today) {
        if (expiry == null) return "GREY";
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, expiry);
        if (days < 0) return "RED";
        if (days <= 30) return "AMBER";
        return "GREEN";
    }

    /** Version history for a single evidence item (assessment §5.9 #1). */
    @Transactional(readOnly = true)
    public List<EvidenceVersion> versions(Long evidenceId) {
        EvidenceItem item = itemRepo.findById(evidenceId)
            .filter(e -> e.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Evidence not found: " + evidenceId));
        return versionRepo.findByEvidenceIdOrderByVersionNoDesc(item.getId());
    }

    /**
     * Snapshot the current state of an evidence item into a new version row, then
     * apply changes from the request. Used by "replace evidence file" flows so
     * the previous version is retained and the WORM hash chain is extended.
     */
    @Transactional
    public EvidenceVersion snapshotVersion(Long evidenceId, Map<String, Object> body) {
        EvidenceItem item = itemRepo.findById(evidenceId)
            .filter(e -> e.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Evidence not found: " + evidenceId));
        EvidenceVersion v = new EvidenceVersion();
        v.setEvidenceId(item.getId());
        int next = (item.getCurrentVersionNo() == null ? 1 : item.getCurrentVersionNo()) + 1;
        v.setVersionNo(next);
        v.setTitleEn(item.getTitleEn());
        v.setTitleAr(item.getTitleAr());
        v.setDescriptionEn(item.getDescriptionEn());
        v.setEvidenceHash(item.getEvidenceHash());
        v.setPreviousHash(item.getPreviousHash());
        v.setFileName(item.getFileName());
        v.setFilePath(item.getFilePath());
        v.setFileSize(item.getFileSize());
        Object summ = body.get("changeSummary");
        if (summ instanceof String s) v.setChangeSummary(s);
        v.setSnapshottedAt(LocalDateTime.now());
        EvidenceVersion saved = versionRepo.save(v);

        // Extend WORM hash chain on the live item.
        item.setPreviousHash(item.getEvidenceHash());
        Object newHash = body.get("evidenceHash");
        if (newHash instanceof String s) item.setEvidenceHash(s);
        item.setCurrentVersionNo(next);
        itemRepo.save(item);
        return saved;
    }

    // ── Connectors ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ConnectorDefinition> listConnectors() {
        return connectorRepo.findByOrganisationIdOrderByCode(TenantContext.getCurrentTenant());
    }

    @Transactional
    public ConnectorDefinition setConnectorStatus(Long id, String status) {
        ConnectorDefinition c = connectorRepo.findById(id)
            .filter(x -> x.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Connector not found: " + id));
        if (!java.util.Set.of("AVAILABLE", "CONFIGURED", "RUNNING", "DISABLED").contains(status)) {
            throw new IllegalArgumentException("status must be AVAILABLE | CONFIGURED | RUNNING | DISABLED");
        }
        c.setStatus(status);
        c.setUpdatedAt(LocalDateTime.now());
        return connectorRepo.save(c);
    }

    /** Stubbed connector run — records a synthetic run summary so the UI can show "last run" data. */
    @Transactional
    public ConnectorDefinition runConnector(Long id) {
        ConnectorDefinition c = connectorRepo.findById(id)
            .filter(x -> x.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Connector not found: " + id));
        c.setLastRunAt(LocalDateTime.now());
        c.setLastRunStatus("SIMULATED");
        c.setLastRunSummary(
            "Simulated run completed. In production this connector would invoke its source-system API "
            + "and create one or more evidence items per the collection_plan_en. The MVP implements the "
            + "definition + scheduler hook only — actual API integrations are deferred to follow-up sessions.");
        c.setEvidenceCollectedCount(c.getEvidenceCollectedCount() == null ? 1 : c.getEvidenceCollectedCount() + 1);
        c.setUpdatedAt(LocalDateTime.now());
        return connectorRepo.save(c);
    }
}
