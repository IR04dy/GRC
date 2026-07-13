package com.tss.grc.module.sust.finance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.CreateInstrumentRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.CreateProjectRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.CreateReportRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.InstrumentContribution;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.InstrumentSummaryResponse;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.PortfolioAllocation;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.PortfolioImpactResponse;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.ProjectAttribution;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.ProposeAllocationRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.TransitionInstrumentRequest;
import com.tss.grc.module.sust.finance.entity.AllocationReport;
import com.tss.grc.module.sust.finance.entity.GreenInstrument;
import com.tss.grc.module.sust.finance.entity.GreenProject;
import com.tss.grc.module.sust.finance.entity.InstrumentAllocation;
import com.tss.grc.module.sust.finance.repository.AllocationReportRepository;
import com.tss.grc.module.sust.finance.repository.GreenInstrumentRepository;
import com.tss.grc.module.sust.finance.repository.GreenProjectRepository;
import com.tss.grc.module.sust.finance.repository.InstrumentAllocationRepository;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Green-Finance uplift G1 (plan §4) — the Green Sukuk / sustainable-finance register:
 * instruments, eligible projects, and the maker-checker'd use-of-proceeds ledger with
 * the §4.2 calculation rules. Hard rules enforced here:
 * <ul>
 *   <li>Over-allocation blocked: Σ(APPROVED + pending PROPOSED) never exceeds net proceeds.</li>
 *   <li>Proposer ≠ approver on every allocation (runtime SoD, §4.3) — 409 on self-approval.</li>
 *   <li>Sukuk-type instruments cannot pass SHARIAH_REVIEW without a Shariah approval reference.</li>
 *   <li>Allocations only against instruments in APPROVED/ISSUED/REPORTING state.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GreenFinanceService {

    private static final Set<String> ALLOCATABLE = Set.of("APPROVED", "ISSUED", "REPORTING");
    private static final List<String> LIFECYCLE =
        List.of("DRAFT", "SHARIAH_REVIEW", "APPROVED", "ISSUED", "REPORTING", "MATURED");
    private static final Set<String> SUKUK_TYPES = Set.of("GREEN_SUKUK", "SUSTAINABILITY_SUKUK");

    private final GreenInstrumentRepository instruments;
    private final GreenProjectRepository projects;
    private final InstrumentAllocationRepository allocations;
    private final AllocationReportRepository reports;
    private final AuditLogService audit;

    // ── instruments ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GreenInstrument> listInstruments() {
        return instruments.findByOrganisationIdAndIsDeletedFalseOrderByInstrumentCodeAsc(org());
    }

    @Transactional
    public GreenInstrument createInstrument(CreateInstrumentRequest r) {
        Long org = org();
        if (instruments.existsByOrganisationIdAndInstrumentCodeAndIsDeletedFalse(org, r.instrumentCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Instrument code already exists: " + r.instrumentCode());
        }
        GreenInstrument e = new GreenInstrument();
        e.setOrganisationId(org);
        e.setInstrumentCode(r.instrumentCode());
        e.setInstrumentType(r.instrumentType());
        e.setNameEn(r.nameEn());
        e.setNameAr(r.nameAr());
        e.setIsin(r.isin());
        e.setListing(r.listing());
        e.setProspectusRef(r.prospectusRef());
        e.setIssueDate(r.issueDate());
        e.setMaturityDate(r.maturityDate());
        e.setAmountSarMn(r.amountSarMn());
        e.setIssuanceCostsSarMn(r.issuanceCostsSarMn() == null ? BigDecimal.ZERO : r.issuanceCostsSarMn());
        e.setFrameworkReference(r.frameworkReference());
        e.setShariahStructure(r.shariahStructure() == null ? "NA" : r.shariahStructure());
        e.setShariahApprovalRef(r.shariahApprovalRef());
        e.setExternalReviewer(r.externalReviewer());
        e.setSpoRating(r.spoRating());
        e.setIcmaCategories(r.icmaCategories());
        e.setProfitRatePct(r.profitRatePct());
        if (r.lookbackMonths() != null) e.setLookbackMonths(r.lookbackMonths());
        GreenInstrument saved = instruments.save(e);
        audit.log("CREATE", "GreenInstrument", saved.getId(),
            "Green instrument created: " + saved.getInstrumentCode() + " (" + saved.getInstrumentType() + ")");
        return saved;
    }

    @Transactional
    public GreenInstrument transitionInstrument(Long id, TransitionInstrumentRequest r) {
        GreenInstrument e = mustInstrument(id);
        String from = e.getStatus(), to = r.status();
        int fromIdx = LIFECYCLE.indexOf(from), toIdx = LIFECYCLE.indexOf(to);
        if (toIdx < 0 || Math.abs(toIdx - fromIdx) != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Illegal transition " + from + " -> " + to + " (one lifecycle step at a time)");
        }
        // Shariah gate: sukuk types cannot pass SHARIAH_REVIEW without an approval reference.
        if ("SHARIAH_REVIEW".equals(from) && "APPROVED".equals(to) && SUKUK_TYPES.contains(e.getInstrumentType())) {
            String ref = r.shariahApprovalRef() != null ? r.shariahApprovalRef() : e.getShariahApprovalRef();
            if (ref == null || ref.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sukuk instruments require a Shariah-board approval reference before APPROVED");
            }
            e.setShariahApprovalRef(ref);
            e.setShariahApproved(true);
        }
        e.setStatus(to);
        GreenInstrument saved = instruments.save(e);
        audit.log("TRANSITION", "GreenInstrument", saved.getId(),
            "Green instrument " + saved.getInstrumentCode() + ": " + from + " -> " + to);
        return saved;
    }

    // ── projects ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GreenProject> listProjects() {
        return projects.findByOrganisationIdAndIsDeletedFalseOrderByProjectCodeAsc(org());
    }

    @Transactional
    public GreenProject createProject(CreateProjectRequest r) {
        Long org = org();
        if (projects.existsByOrganisationIdAndProjectCodeAndIsDeletedFalse(org, r.projectCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project code already exists: " + r.projectCode());
        }
        GreenProject e = new GreenProject();
        e.setOrganisationId(org);
        e.setProjectCode(r.projectCode());
        e.setNameEn(r.nameEn());
        e.setNameAr(r.nameAr());
        e.setCategory(r.category());
        e.setDescriptionEn(r.descriptionEn());
        e.setDescriptionAr(r.descriptionAr());
        e.setTotalCostSarMn(r.totalCostSarMn());
        e.setExpectedTco2eAvoidedYr(r.expectedTco2eAvoidedYr());
        e.setActualTco2eAvoidedYr(r.actualTco2eAvoidedYr());
        e.setExpectedMwhRenewableYr(r.expectedMwhRenewableYr());
        e.setActualMwhRenewableYr(r.actualMwhRenewableYr());
        e.setExpectedWaterM3SavedYr(r.expectedWaterM3SavedYr());
        e.setActualWaterM3SavedYr(r.actualWaterM3SavedYr());
        e.setActualImpactBasis(r.actualImpactBasis() == null ? "ACTUAL" : r.actualImpactBasis());
        GreenProject saved = projects.save(e);
        audit.log("CREATE", "GreenProject", saved.getId(),
            "Green project created: " + saved.getProjectCode() + " [" + saved.getCategory() + "]");
        return saved;
    }

    // ── use-of-proceeds ledger (maker-checker) ──────────────────────────

    @Transactional(readOnly = true)
    public List<InstrumentAllocation> listAllocations(Long instrumentId) {
        mustInstrument(instrumentId);
        return allocations.findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByAllocationDateAsc(org(), instrumentId);
    }

    @Transactional
    public InstrumentAllocation proposeAllocation(ProposeAllocationRequest r) {
        Long org = org();
        GreenInstrument instr = mustInstrument(r.instrumentId());
        projects.findByOrganisationIdAndIdAndIsDeletedFalse(org, r.projectId())
            .orElseThrow(() -> new EntityNotFoundException("Green project not found: " + r.projectId()));
        if (!ALLOCATABLE.contains(instr.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Instrument must be APPROVED/ISSUED/REPORTING to allocate (is " + instr.getStatus() + ")");
        }
        // Over-allocation hard block (§4.2) — counts APPROVED and still-pending PROPOSED.
        BigDecimal net = GreenFinanceMath.netProceeds(instr.getAmountSarMn(), instr.getIssuanceCostsSarMn());
        BigDecimal committed = sumAllocations(org, instr.getId(), Set.of("APPROVED", "PROPOSED"), null);
        if (GreenFinanceMath.wouldOverAllocate(committed, r.amountSarMn(), net)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Over-allocation blocked: committed " + committed + " + " + r.amountSarMn()
                    + " exceeds net proceeds " + net + " SAR mn");
        }
        InstrumentAllocation a = new InstrumentAllocation();
        a.setOrganisationId(org);
        a.setInstrumentId(r.instrumentId());
        a.setProjectId(r.projectId());
        a.setAmountSarMn(r.amountSarMn());
        a.setAllocationDate(r.allocationDate());
        a.setFinancingType(r.financingType() == null ? "NEW_FINANCING" : r.financingType());
        a.setProposedBy(currentUserId());
        InstrumentAllocation saved = allocations.save(a);
        audit.log("CREATE", "InstrumentAllocation", saved.getId(),
            "Allocation proposed: " + r.amountSarMn() + " SAR mn, instrument " + instr.getInstrumentCode());
        return saved;
    }

    @Transactional
    public InstrumentAllocation approveAllocation(Long id) {
        InstrumentAllocation a = mustAllocation(id);
        requirePending(a);
        Long approver = currentUserId();
        if (approver != null && approver.equals(a.getProposedBy())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Self-approval blocked: allocation proposer and approver must differ (SoD)");
        }
        a.setStatus("APPROVED");
        a.setApprovedBy(approver);
        a.setDecidedAt(LocalDateTime.now());
        InstrumentAllocation saved = allocations.save(a);
        audit.log("APPROVE", "InstrumentAllocation", saved.getId(),
            "Allocation approved: " + saved.getAmountSarMn() + " SAR mn");
        return saved;
    }

    @Transactional
    public InstrumentAllocation rejectAllocation(Long id, String reason) {
        InstrumentAllocation a = mustAllocation(id);
        requirePending(a);
        a.setStatus("REJECTED");
        a.setApprovedBy(currentUserId());
        a.setDecidedAt(LocalDateTime.now());
        a.setRejectionReason(reason);
        InstrumentAllocation saved = allocations.save(a);
        audit.log("REJECT", "InstrumentAllocation", saved.getId(), "Allocation rejected: " + reason);
        return saved;
    }

    // ── §4.2 summary ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InstrumentSummaryResponse summary(Long instrumentId) {
        Long org = org();
        GreenInstrument instr = mustInstrument(instrumentId);
        List<InstrumentAllocation> all =
            allocations.findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByAllocationDateAsc(org, instrumentId);

        BigDecimal net = GreenFinanceMath.netProceeds(instr.getAmountSarMn(), instr.getIssuanceCostsSarMn());
        BigDecimal allocated = BigDecimal.ZERO, refinanced = BigDecimal.ZERO;
        BigDecimal tco2e = BigDecimal.ZERO, mwh = BigDecimal.ZERO, water = BigDecimal.ZERO;
        int approved = 0, pending = 0;
        boolean estimateBasis = false;   // V293 — any contributing project on ESTIMATE figures

        // Cache projects once for attribution.
        Map<Long, GreenProject> projById = new java.util.HashMap<>();
        for (InstrumentAllocation a : all) {
            if ("PROPOSED".equals(a.getStatus())) { pending++; continue; }
            if (!"APPROVED".equals(a.getStatus())) continue;
            approved++;
            allocated = allocated.add(a.getAmountSarMn());
            if ("REFINANCING".equals(a.getFinancingType())) refinanced = refinanced.add(a.getAmountSarMn());
            GreenProject p = projById.computeIfAbsent(a.getProjectId(),
                pid -> projects.findByOrganisationIdAndIdAndIsDeletedFalse(org, pid).orElse(null));
            if (p == null) continue;
            if ("ESTIMATE".equals(p.getActualImpactBasis())) estimateBasis = true;
            BigDecimal share = GreenFinanceMath.projectShare(a.getAmountSarMn(), p.getTotalCostSarMn());
            tco2e = tco2e.add(GreenFinanceMath.attributedImpact(share, p.getActualTco2eAvoidedYr()));
            mwh   = mwh.add(GreenFinanceMath.attributedImpact(share, p.getActualMwhRenewableYr()));
            water = water.add(GreenFinanceMath.attributedImpact(share, p.getActualWaterM3SavedYr()));
        }

        return new InstrumentSummaryResponse(
            instr.getId(), instr.getInstrumentCode(), instr.getStatus(),
            net,
            allocated.setScale(4, java.math.RoundingMode.HALF_UP),
            GreenFinanceMath.unallocated(net, allocated),
            GreenFinanceMath.allocationRatioPct(allocated, net),
            GreenFinanceMath.refinancingSharePct(refinanced, allocated),
            tco2e, mwh, water,
            GreenFinanceMath.intensityPerSarMn(tco2e, allocated),
            approved, pending, estimateBasis);
    }

    /**
     * GF-1 (plan §4.2 last formula) — org-level attributed impact across ALL
     * instruments with double-count protection: per project, the summed
     * instrument shares are capped at 1 ({@link GreenFinanceMath#dedupFactor}),
     * and each instrument's contribution is scaled pro-rata — a project financed
     * by two sukuk delivers its impact once, split between them. APPROVED
     * allocations only (the same population as the per-instrument summary).
     */
    @Transactional(readOnly = true)
    public PortfolioImpactResponse portfolioImpact() {
        Long org = org();
        List<GreenInstrument> instrs =
            instruments.findByOrganisationIdAndIsDeletedFalseOrderByInstrumentCodeAsc(org);

        // project id -> (instrument id -> summed raw share), insertion-ordered.
        Map<Long, Map<Long, BigDecimal>> sharesByProject = new java.util.LinkedHashMap<>();
        Map<Long, GreenProject> projById = new java.util.HashMap<>();
        Map<Long, GreenInstrument> instrById = new java.util.LinkedHashMap<>();
        for (GreenInstrument gi : instrs) {
            instrById.put(gi.getId(), gi);
            for (InstrumentAllocation a : allocations
                    .findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByAllocationDateAsc(org, gi.getId())) {
                if (!"APPROVED".equals(a.getStatus())) continue;
                GreenProject p = projById.computeIfAbsent(a.getProjectId(),
                    pid -> projects.findByOrganisationIdAndIdAndIsDeletedFalse(org, pid).orElse(null));
                if (p == null) continue;
                BigDecimal share = GreenFinanceMath.projectShare(a.getAmountSarMn(), p.getTotalCostSarMn());
                sharesByProject.computeIfAbsent(p.getId(), k -> new java.util.LinkedHashMap<>())
                    .merge(gi.getId(), share, BigDecimal::add);
            }
        }

        Map<Long, BigDecimal[]> contrib = new java.util.LinkedHashMap<>();   // instr -> {tco2e, mwh, water}
        List<ProjectAttribution> byProject = new java.util.ArrayList<>();
        boolean estimateBasis = false;
        int capped = 0;

        for (Map.Entry<Long, Map<Long, BigDecimal>> e : sharesByProject.entrySet()) {
            GreenProject p = projById.get(e.getKey());
            BigDecimal rawSum = e.getValue().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal factor = GreenFinanceMath.dedupFactor(rawSum);
            boolean dedup = factor.compareTo(BigDecimal.ONE) < 0;
            if (dedup) capped++;
            if ("ESTIMATE".equals(p.getActualImpactBasis())) estimateBasis = true;
            byProject.add(new ProjectAttribution(p.getId(), p.getProjectCode(),
                rawSum.setScale(4, java.math.RoundingMode.HALF_UP),
                (dedup ? BigDecimal.ONE : rawSum).setScale(4, java.math.RoundingMode.HALF_UP),
                dedup));
            for (Map.Entry<Long, BigDecimal> se : e.getValue().entrySet()) {
                BigDecimal effectiveShare = se.getValue().multiply(factor);
                BigDecimal[] c = contrib.computeIfAbsent(se.getKey(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                c[0] = c[0].add(GreenFinanceMath.attributedImpact(effectiveShare, p.getActualTco2eAvoidedYr()));
                c[1] = c[1].add(GreenFinanceMath.attributedImpact(effectiveShare, p.getActualMwhRenewableYr()));
                c[2] = c[2].add(GreenFinanceMath.attributedImpact(effectiveShare, p.getActualWaterM3SavedYr()));
            }
        }

        // Totals are the sum of the per-instrument slices, so the breakdown
        // always reconciles with the headline figures.
        BigDecimal tco2e = BigDecimal.ZERO, mwh = BigDecimal.ZERO, water = BigDecimal.ZERO;
        List<InstrumentContribution> byInstrument = new java.util.ArrayList<>();
        for (Map.Entry<Long, BigDecimal[]> ce : contrib.entrySet()) {
            GreenInstrument gi = instrById.get(ce.getKey());
            BigDecimal[] c = ce.getValue();
            tco2e = tco2e.add(c[0]); mwh = mwh.add(c[1]); water = water.add(c[2]);
            byInstrument.add(new InstrumentContribution(
                gi.getId(), gi.getInstrumentCode(), c[0], c[1], c[2]));
        }

        return new PortfolioImpactResponse(tco2e, mwh, water, estimateBasis,
            sharesByProject.size(), capped, byInstrument, byProject,
            portfolioAllocation());
    }

    /** Post-issuance lifecycle states — the population of the deployment KPI. */
    private static final Set<String> POST_ISSUANCE = Set.of("ISSUED", "REPORTING", "MATURED");

    /**
     * GF-3 — portfolio-level allocation ratio: Σ APPROVED allocations over
     * Σ net proceeds across post-issuance instruments. DRAFT/SHARIAH_REVIEW/
     * APPROVED instruments haven't raised money yet, so they would only
     * dilute the deployment figure.
     */
    @Transactional(readOnly = true)
    public PortfolioAllocation portfolioAllocation() {
        Long org = org();
        BigDecimal net = BigDecimal.ZERO, allocated = BigDecimal.ZERO;
        int count = 0;
        for (GreenInstrument gi : instruments.findByOrganisationIdAndIsDeletedFalseOrderByInstrumentCodeAsc(org)) {
            if (!POST_ISSUANCE.contains(gi.getStatus())) continue;
            count++;
            net = net.add(GreenFinanceMath.netProceeds(gi.getAmountSarMn(), gi.getIssuanceCostsSarMn()));
            for (InstrumentAllocation a : allocations
                    .findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByAllocationDateAsc(org, gi.getId())) {
                if ("APPROVED".equals(a.getStatus())) allocated = allocated.add(a.getAmountSarMn());
            }
        }
        return new PortfolioAllocation(
            net.setScale(4, java.math.RoundingMode.HALF_UP),
            allocated.setScale(4, java.math.RoundingMode.HALF_UP),
            GreenFinanceMath.allocationRatioPct(allocated, net),
            count);
    }

    // ── post-issuance reports (G2, plan §4.1 / REG-7) ────────────────────

    @Transactional(readOnly = true)
    public List<AllocationReport> listReports(Long instrumentId) {
        mustInstrument(instrumentId);
        return reports.findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByPeriodYearDesc(org(), instrumentId);
    }

    /** Freeze a DRAFT snapshot of the current §4.2 figures for the reporting period. */
    @Transactional
    public AllocationReport createReport(CreateReportRequest r) {
        Long org = org();
        GreenInstrument instr = mustInstrument(r.instrumentId());
        String type = r.reportType() == null ? "COMBINED" : r.reportType();
        if (reports.existsByOrganisationIdAndInstrumentIdAndPeriodYearAndReportTypeAndIsDeletedFalse(
                org, instr.getId(), r.periodYear(), type)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Report already exists for " + instr.getInstrumentCode() + " / " + r.periodYear() + " / " + type);
        }
        InstrumentSummaryResponse s = summary(instr.getId());
        AllocationReport rep = new AllocationReport();
        rep.setOrganisationId(org);
        rep.setInstrumentId(instr.getId());
        rep.setPeriodYear(r.periodYear());
        rep.setReportType(type);
        rep.setAllocatedSarMn(s.allocatedSarMn());
        rep.setUnallocatedSarMn(s.unallocatedSarMn());
        rep.setAllocationRatioPct(s.allocationRatioPct());
        rep.setRefinancingSharePct(s.refinancingSharePct());
        rep.setTco2eAvoidedYr(s.tco2eAvoidedYr());
        rep.setMwhRenewableYr(s.mwhRenewableYr());
        rep.setWaterM3SavedYr(s.waterM3SavedYr());
        rep.setIntensityTco2ePerSarMn(s.intensityTco2ePerSarMn());
        rep.setNarrativeEn(r.narrativeEn());
        rep.setNarrativeAr(r.narrativeAr());
        AllocationReport saved = reports.save(rep);
        audit.log("CREATE", "AllocationReport", saved.getId(),
            "Allocation/impact report drafted: " + instr.getInstrumentCode() + " " + r.periodYear());
        return saved;
    }

    @Transactional
    public AllocationReport approveReport(Long id) {
        AllocationReport rep = mustReport(id);
        if (!"DRAFT".equals(rep.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report is not DRAFT (" + rep.getStatus() + ")");
        }
        Long approver = currentUserId();
        if (approver != null && approver.equals(rep.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Self-approval blocked: report creator and approver must differ (SoD)");
        }
        rep.setStatus("APPROVED");
        rep.setApprovedBy(approver);
        AllocationReport saved = reports.save(rep);
        audit.log("APPROVE", "AllocationReport", saved.getId(), "Allocation/impact report approved");
        return saved;
    }

    /**
     * V295 (roadmap closure) — standalone per-report export payload for the shared
     * tabular engine (packet.xlsx/.docx): the report's frozen §4.2 figures + the
     * issuing instrument's identity, as one Field/Value sheet.
     */
    @Transactional(readOnly = true)
    public com.tss.grc.module.reporting.dto.TabularExportRequest reportExportPayload(Long reportId, String lang) {
        AllocationReport rep = mustReport(reportId);
        GreenInstrument instr = mustInstrument(rep.getInstrumentId());
        boolean ar = "ar".equalsIgnoreCase(lang);
        java.util.List<java.util.List<String>> rows = java.util.List.of(
            java.util.List.of(ar ? "الأداة" : "Instrument", instr.getInstrumentCode() + " — "
                + (ar && instr.getNameAr() != null ? instr.getNameAr() : instr.getNameEn())),
            java.util.List.of(ar ? "النوع" : "Type", instr.getInstrumentType()),
            java.util.List.of(ar ? "مرجع نشرة الإصدار" : "Prospectus ref", str(instr.getProspectusRef())),
            java.util.List.of(ar ? "فترة التقرير" : "Reporting period", String.valueOf(rep.getPeriodYear())),
            java.util.List.of(ar ? "نوع التقرير" : "Report type", rep.getReportType()),
            java.util.List.of(ar ? "المخصص (م ر.س)" : "Allocated (SAR mn)", str(rep.getAllocatedSarMn())),
            java.util.List.of(ar ? "غير المخصص (م ر.س)" : "Unallocated (SAR mn)", str(rep.getUnallocatedSarMn())),
            java.util.List.of(ar ? "نسبة التخصيص %" : "Allocation ratio %", str(rep.getAllocationRatioPct())),
            java.util.List.of(ar ? "حصة إعادة التمويل %" : "Refinancing share %", str(rep.getRefinancingSharePct())),
            java.util.List.of(ar ? "انبعاثات مُتجنَّبة (طن/سنة)" : "tCO2e avoided / yr", str(rep.getTco2eAvoidedYr())),
            java.util.List.of(ar ? "طاقة متجددة (م.و.س/سنة)" : "MWh renewable / yr", str(rep.getMwhRenewableYr())),
            java.util.List.of(ar ? "مياه موفَّرة (م3/سنة)" : "Water m3 saved / yr", str(rep.getWaterM3SavedYr())),
            java.util.List.of(ar ? "الكثافة (طن/م ر.س)" : "Intensity tCO2e / SAR mn", str(rep.getIntensityTco2ePerSarMn())),
            java.util.List.of(ar ? "الحالة" : "Status", rep.getStatus()));
        return new com.tss.grc.module.reporting.dto.TabularExportRequest(
            "GreenFinance", "AllocationReport" + rep.getPeriodYear(), lang,
            java.util.List.of(new com.tss.grc.module.reporting.dto.TabularExportRequest.Sheet(
                (ar ? "تقرير التخصيص والأثر " : "Allocation & Impact ") + rep.getPeriodYear(),
                java.util.List.of(ar ? "البند" : "Field", ar ? "القيمة" : "Value"), rows)));
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    @Transactional
    public AllocationReport publishReport(Long id) {
        AllocationReport rep = mustReport(id);
        if (!"APPROVED".equals(rep.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Only APPROVED reports can be published (" + rep.getStatus() + ")");
        }
        rep.setStatus("PUBLISHED");
        rep.setPublishedAt(LocalDateTime.now());
        AllocationReport saved = reports.save(rep);
        audit.log("TRANSITION", "AllocationReport", saved.getId(), "Allocation/impact report published");
        return saved;
    }

    private AllocationReport mustReport(Long id) {
        return reports.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Report not found: " + id));
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private BigDecimal sumAllocations(Long org, Long instrumentId, Set<String> statuses, Long excludeId) {
        return allocations.findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByAllocationDateAsc(org, instrumentId)
            .stream()
            .filter(a -> statuses.contains(a.getStatus()))
            .filter(a -> excludeId == null || !excludeId.equals(a.getId()))
            .map(InstrumentAllocation::getAmountSarMn)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private GreenInstrument mustInstrument(Long id) {
        return instruments.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Green instrument not found: " + id));
    }

    private InstrumentAllocation mustAllocation(Long id) {
        return allocations.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Allocation not found: " + id));
    }

    private static void requirePending(InstrumentAllocation a) {
        if (!"PROPOSED".equals(a.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Allocation already decided (" + a.getStatus() + ")");
        }
    }

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }

    static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }
}
