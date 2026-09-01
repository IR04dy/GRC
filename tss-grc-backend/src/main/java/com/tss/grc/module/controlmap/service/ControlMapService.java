package com.tss.grc.module.controlmap.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.controlmap.dto.ControlLinkResponse;
import com.tss.grc.module.controlmap.dto.EntityControlEvidenceResponse;
import com.tss.grc.module.controlmap.entity.ControlLink;
import com.tss.grc.module.controlmap.repository.ControlLinkRepository;
import com.tss.grc.module.evidence.entity.EvidenceControl;
import com.tss.grc.module.evidence.entity.EvidenceFramework;
import com.tss.grc.module.evidence.entity.EvidenceSubControl;
import com.tss.grc.module.evidence.repository.EvidenceControlRepository;
import com.tss.grc.module.evidence.repository.EvidenceFrameworkRepository;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.evidence.repository.EvidenceSubControlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ControlMapService {

    private static final int EXPIRY_HORIZON_DAYS = 60;

    private final ControlLinkRepository repo;
    private final EvidenceSubControlRepository subControlRepository;
    private final EvidenceControlRepository controlRepository;
    private final EvidenceFrameworkRepository frameworkRepository;
    private final EvidenceItemRepository itemRepository;

    /** Allowed satisfaction levels; anything else falls back to FULL. */
    private static final Set<String> SATISFACTIONS = Set.of("FULL", "PARTIAL", "PLANNED");

    private static String normalizeSatisfaction(String s) {
        if (s == null) return "FULL";
        String up = s.trim().toUpperCase(Locale.ROOT);
        return SATISFACTIONS.contains(up) ? up : "FULL";
    }

    @Transactional(readOnly = true)
    public List<ControlLinkResponse> getMappings(String entityType, Long entityId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndEntityTypeAndEntityId(orgId, entityType, entityId)
            .stream().map(l -> resolve(l.getSubControlId(), l.getSatisfaction())).toList();
    }

    /** Replace-set: the entity's mappings become exactly the supplied sub-control ids. */
    @Transactional
    public List<ControlLinkResponse> setMappings(String entityType, Long entityId,
                                                 List<Long> subControlIds, Map<Long, String> satisfaction,
                                                 Long userId) {
        Long orgId = TenantContext.getCurrentTenant();
        repo.deleteForEntity(orgId, entityType, entityId);
        Map<Long, String> sat = satisfaction == null ? Map.of() : satisfaction;
        List<Long> distinct = subControlIds == null ? List.of()
            : subControlIds.stream().filter(Objects::nonNull).distinct().toList();
        for (Long scId : distinct) {
            ControlLink l = new ControlLink();
            l.setOrganisationId(orgId);
            l.setEntityType(entityType);
            l.setEntityId(entityId);
            l.setSubControlId(scId);
            l.setSatisfaction(normalizeSatisfaction(sat.get(scId)));
            l.setCreatedBy(userId);
            repo.save(l);
        }
        return distinct.stream()
            .map(scId -> resolve(scId, normalizeSatisfaction(sat.get(scId)))).toList();
    }

    /**
     * The entity's mapped sub-controls enriched with recommended evidence and
     * the org's live evidence-collection coverage — mirrors the GRC Expert
     * coverage rollup (freshness-aware) but scoped to one entity's control map.
     */
    @Transactional(readOnly = true)
    public List<EntityControlEvidenceResponse> getEvidenceReadiness(String entityType, Long entityId) {
        Long orgId = TenantContext.getCurrentTenant();
        List<ControlLink> links = repo.findByOrganisationIdAndEntityTypeAndEntityId(orgId, entityType, entityId);
        if (links.isEmpty()) return List.of();

        // Per-sub-control evidence rollups (org-scoped), filtered in-memory to the mapped set.
        Map<Long, Map<String, Integer>> statusBySc = new HashMap<>();
        Map<Long, int[]> expiryBySc = new HashMap<>();         // [expired, expiringSoon]
        Map<Long, Integer> freshApprovedBySc = new HashMap<>(); // APPROVED & not past valid_to
        if (orgId != null) {
            for (Object[] r : itemRepository.aggregateStatusBySubControl(orgId)) {
                Long scId = ((Number) r[0]).longValue();
                String st = r[1] == null ? "UNKNOWN" : r[1].toString();
                int cnt = ((Number) r[2]).intValue();
                statusBySc.computeIfAbsent(scId, k -> new HashMap<>()).merge(st, cnt, Integer::sum);
            }
            LocalDate today = LocalDate.now();
            LocalDate horizon = today.plusDays(EXPIRY_HORIZON_DAYS);
            for (Object[] r : itemRepository.aggregateExpiryBySubControl(orgId, today, horizon)) {
                expiryBySc.put(((Number) r[0]).longValue(),
                        new int[]{((Number) r[1]).intValue(), ((Number) r[2]).intValue()});
            }
            for (Object[] r : itemRepository.aggregateFreshApprovedBySubControl(orgId, today)) {
                freshApprovedBySc.put(((Number) r[0]).longValue(), ((Number) r[1]).intValue());
            }
        }

        // PERF-NQ-005: batch-resolve the static sub-control -> control -> framework
        // catalog with three findAllById calls + in-memory maps, instead of three
        // findById queries per mapped link. (These are global catalog tables, so
        // findAllById has the same scoping as the prior per-row findById.)
        List<Long> scIds = links.stream().map(ControlLink::getSubControlId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, EvidenceSubControl> scById = new HashMap<>();
        subControlRepository.findAllById(scIds).forEach(sc -> scById.put(sc.getId(), sc));
        List<Long> ctrlIds = scById.values().stream().map(EvidenceSubControl::getControlId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, EvidenceControl> ctrlById = new HashMap<>();
        controlRepository.findAllById(ctrlIds).forEach(c -> ctrlById.put(c.getId(), c));
        List<Long> fwIds = ctrlById.values().stream().map(EvidenceControl::getFrameworkId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, EvidenceFramework> fwById = new HashMap<>();
        frameworkRepository.findAllById(fwIds).forEach(f -> fwById.put(f.getId(), f));

        List<EntityControlEvidenceResponse> out = new ArrayList<>();
        for (ControlLink l : links) {
            Long scId = l.getSubControlId();
            EvidenceSubControl sc = scId == null ? null : scById.get(scId);
            String controlCode = null, frameworkCode = null, guidanceEn = null, guidanceAr = null;
            String code = null, nameEn = null, nameAr = null;
            if (sc != null) {
                code = sc.getSubControlCode(); nameEn = sc.getNameEn(); nameAr = sc.getNameAr();
                guidanceEn = sc.getEvidenceGuidance(); guidanceAr = sc.getEvidenceGuidanceAr();
                EvidenceControl ctrl = sc.getControlId() == null ? null : ctrlById.get(sc.getControlId());
                if (ctrl != null) {
                    controlCode = ctrl.getControlCode();
                    EvidenceFramework fw = ctrl.getFrameworkId() == null ? null : fwById.get(ctrl.getFrameworkId());
                    if (fw != null) frameworkCode = fw.getCode();
                }
            }
            Map<String, Integer> sm = statusBySc.getOrDefault(scId, Map.of());
            int approved = sm.getOrDefault("APPROVED", 0);
            int pending  = sm.getOrDefault("PENDING_REVIEW", 0);
            int draft    = sm.getOrDefault("DRAFT", 0);
            int rejected = sm.getOrDefault("REJECTED", 0);
            int total    = sm.values().stream().mapToInt(Integer::intValue).sum();
            int[] exp = expiryBySc.getOrDefault(scId, new int[]{0, 0});
            int freshApproved = freshApprovedBySc.getOrDefault(scId, 0);
            String coverage = freshApproved > 0 ? "COVERED"
                    : (approved > 0 ? "EXPIRED" : (total > 0 ? "PARTIAL" : "GAP"));

            out.add(new EntityControlEvidenceResponse(
                    scId, code, nameEn, nameAr, controlCode, frameworkCode,
                    normalizeSatisfaction(l.getSatisfaction()),
                    guidanceEn, guidanceAr,
                    total, approved, pending, draft, rejected, exp[1], exp[0], coverage));
        }
        // Stable order: GAPs first (most actionable), then by framework + code.
        out.sort((a, b) -> {
            int ra = coverageRank(a.coverage()), rb = coverageRank(b.coverage());
            if (ra != rb) return Integer.compare(ra, rb);
            return Objects.compare(
                    (a.frameworkCode() == null ? "" : a.frameworkCode()) + (a.subControlCode() == null ? "" : a.subControlCode()),
                    (b.frameworkCode() == null ? "" : b.frameworkCode()) + (b.subControlCode() == null ? "" : b.subControlCode()),
                    String::compareTo);
        });
        return out;
    }

    private static int coverageRank(String coverage) {
        return switch (coverage == null ? "" : coverage) {
            case "GAP" -> 0;
            case "PARTIAL" -> 1;
            case "EXPIRED" -> 2;
            default -> 3; // COVERED
        };
    }

    private ControlLinkResponse resolve(Long scId, String satisfaction) {
        String sat = normalizeSatisfaction(satisfaction);
        EvidenceSubControl sc = subControlRepository.findById(scId).orElse(null);
        if (sc == null) {
            return new ControlLinkResponse(scId, null, null, null, null, null, sat);
        }
        String controlCode = null, frameworkCode = null;
        EvidenceControl ctrl = controlRepository.findById(sc.getControlId()).orElse(null);
        if (ctrl != null) {
            controlCode = ctrl.getControlCode();
            EvidenceFramework fw = frameworkRepository.findById(ctrl.getFrameworkId()).orElse(null);
            if (fw != null) frameworkCode = fw.getCode();
        }
        return new ControlLinkResponse(scId, sc.getSubControlCode(), sc.getNameEn(), sc.getNameAr(),
            controlCode, frameworkCode, sat);
    }
}
