package com.tss.grc.module.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.dto.ExpertControlResponse;
import com.tss.grc.module.evidence.dto.ExpertFrameworkResponse;
import com.tss.grc.module.evidence.dto.ExpertSubControlResponse;
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
import java.util.stream.Collectors;

/**
 * Builds the GRC Expert / SME catalog: the active framework → control →
 * sub-control tree (NCA / NDI / PDPL / BCM frameworks) enriched per sub-control
 * with the recommended evidence and a live rollup of the current org's
 * evidence-collection status (so SME guidance is wired to the evidence
 * lifecycle).
 */
@Service
@RequiredArgsConstructor
public class GrcExpertService {

    private static final int EXPIRY_HORIZON_DAYS = 60;

    private final EvidenceFrameworkRepository frameworkRepository;
    private final EvidenceControlRepository controlRepository;
    private final EvidenceSubControlRepository subControlRepository;
    private final EvidenceItemRepository itemRepository;

    @Transactional(readOnly = true)
    public List<ExpertFrameworkResponse> listExpertCatalog() {
        Long orgId = TenantContext.getCurrentTenant();

        List<EvidenceFramework> frameworks = frameworkRepository.findByIsActiveTrue();
        List<Long> fwIds = frameworks.stream().map(EvidenceFramework::getId).toList();
        List<EvidenceControl> controls = fwIds.isEmpty() ? List.of()
                : controlRepository.findByFrameworkIdInAndIsActiveTrue(fwIds);
        List<Long> ctrlIds = controls.stream().map(EvidenceControl::getId).toList();
        List<EvidenceSubControl> subs = ctrlIds.isEmpty() ? List.of()
                : subControlRepository.findByControlIdInAndIsActiveTrue(ctrlIds);

        // ── Per-sub-control evidence rollups (org-scoped) ─────────────────────
        Map<Long, Map<String, Integer>> statusBySc = new HashMap<>();
        Map<Long, int[]> expiryBySc = new HashMap<>();        // [expired, expiringSoon]
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
                Long scId = ((Number) r[0]).longValue();
                int expired = ((Number) r[1]).intValue();
                int soon = ((Number) r[2]).intValue();
                expiryBySc.put(scId, new int[]{expired, soon});
            }
            for (Object[] r : itemRepository.aggregateFreshApprovedBySubControl(orgId, today)) {
                freshApprovedBySc.put(((Number) r[0]).longValue(), ((Number) r[1]).intValue());
            }
        }

        Map<Long, List<EvidenceSubControl>> subsByCtrl = subs.stream()
                .collect(Collectors.groupingBy(EvidenceSubControl::getControlId));
        Map<Long, List<EvidenceControl>> ctrlsByFw = controls.stream()
                .collect(Collectors.groupingBy(EvidenceControl::getFrameworkId));

        List<ExpertFrameworkResponse> out = new ArrayList<>();
        for (EvidenceFramework f : frameworks) {
            List<EvidenceControl> fwCtrls = ctrlsByFw.getOrDefault(f.getId(), List.of());
            List<ExpertControlResponse> ctrlResponses = new ArrayList<>();
            int fwSub = 0, fwCovered = 0, fwPartial = 0, fwGap = 0;

            for (EvidenceControl c : fwCtrls) {
                List<EvidenceSubControl> cSubs = subsByCtrl.getOrDefault(c.getId(), List.of());
                List<ExpertSubControlResponse> scResponses = new ArrayList<>();
                int cCovered = 0, cPartial = 0, cGap = 0;

                for (EvidenceSubControl sc : cSubs) {
                    Map<String, Integer> sm = statusBySc.getOrDefault(sc.getId(), Map.of());
                    int approved = sm.getOrDefault("APPROVED", 0);
                    int pending  = sm.getOrDefault("PENDING_REVIEW", 0);
                    int draft    = sm.getOrDefault("DRAFT", 0);
                    int rejected = sm.getOrDefault("REJECTED", 0);
                    int total    = sm.values().stream().mapToInt(Integer::intValue).sum();
                    int[] exp = expiryBySc.getOrDefault(sc.getId(), new int[]{0, 0});
                    int freshApproved = freshApprovedBySc.getOrDefault(sc.getId(), 0);

                    // Freshness-aware coverage: COVERED only when at least one approved item is
                    // still valid. If approved evidence exists but ALL of it has expired, the
                    // sub-control is EXPIRED (not COVERED) so an officer is not shown green over
                    // stale evidence. EXPIRED rolls up with PARTIAL at the framework level so
                    // covered + partial + gap == subControlCount holds.
                    String coverage = freshApproved > 0 ? "COVERED"
                            : (approved > 0 ? "EXPIRED" : (total > 0 ? "PARTIAL" : "GAP"));
                    switch (coverage) {
                        case "COVERED"          -> cCovered++;
                        case "EXPIRED", "PARTIAL" -> cPartial++;
                        default                 -> cGap++;
                    }

                    scResponses.add(new ExpertSubControlResponse(
                            sc.getId(), sc.getSubControlCode(), sc.getNameEn(), sc.getNameAr(),
                            sc.getEvidenceGuidance(), sc.getEvidenceGuidanceAr(), total, approved, pending, draft, rejected,
                            exp[1], exp[0], coverage));
                }

                fwSub += cSubs.size();
                fwCovered += cCovered;
                fwPartial += cPartial;
                fwGap += cGap;
                ctrlResponses.add(new ExpertControlResponse(
                        c.getId(), c.getControlCode(), c.getNameEn(), c.getNameAr(),
                        cSubs.size(), cCovered, cPartial, cGap, scResponses));
            }

            out.add(new ExpertFrameworkResponse(
                    f.getId(), f.getCode(), classifyFamily(f.getCode(), f.getIssuer()),
                    f.getNameEn(), f.getNameAr(), f.getVersion(), f.getIssuer(), f.getDescriptionEn(),
                    fwCtrls.size(), fwSub, fwCovered, fwPartial, fwGap, ctrlResponses));
        }
        return out;
    }

    /** Map a framework to one of the SME families (else OTHER) from its code / issuer.
     *  ESG and ERM are checked before the NCA issuer-fallback so ERM/RISK catalogs
     *  (whose issuer may mention NCA) classify as ERM, not NCA. */
    static String classifyFamily(String code, String issuer) {
        String c = code == null ? "" : code.toUpperCase(Locale.ROOT);
        if (c.startsWith("PDPL")) return "PDPL";
        if (c.startsWith("NDMO") || c.startsWith("NDI")) return "NDI";
        if (c.startsWith("BCM")) return "BCM";
        if (c.startsWith("ESG") || c.startsWith("GRI") || c.startsWith("TCFD")
                || c.startsWith("ISSB") || c.startsWith("SUST")) return "ESG";
        if (c.startsWith("AI") || c.startsWith("ISO42001") || c.startsWith("NIST")) return "AI";
        if (c.startsWith("ERM") || c.startsWith("RISK") || c.startsWith("COSO")
                || c.startsWith("ISO31000")) return "ERM";
        if (c.startsWith("ECC") || c.startsWith("CCC") || c.startsWith("DCC")
                || c.startsWith("CSCC") || c.startsWith("OTCC") || c.startsWith("TCC")
                || (issuer != null && issuer.toUpperCase(Locale.ROOT).contains("NCA"))) return "NCA";
        return "OTHER";
    }
}
