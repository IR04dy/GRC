package com.tss.grc.module.sust.annex.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.annex.entity.ReportingYear;
import com.tss.grc.module.sust.annex.entity.SustainabilityReport;
import com.tss.grc.module.sust.annex.repository.ReportingYearRepository;
import com.tss.grc.module.sust.annex.repository.SustainabilityReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Reporting-year + Sustainability-report lifecycle owner.
 *
 * <p>Generation works as: pick a {@link ReportingYear}, ask
 * {@link AnnexGeneratorService} to compose the three content
 * snapshots, persist a {@link SustainabilityReport} row in DRAFT.
 *
 * <p>Status machines:
 * <ul>
 *   <li>ReportingYear:   ACTIVE → DATA_COLLECTION → REVIEW → APPROVED → PUBLISHED → ARCHIVED</li>
 *   <li>Report:          DRAFT → SUBMITTED → APPROVED → PUBLISHED (SUPERSEDED is set
 *                        automatically when a newer version is approved for the same type+year)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SustainabilityReportService {

    private static final Set<String> REPORT_STATUSES = Set.of("DRAFT","SUBMITTED","APPROVED","PUBLISHED","SUPERSEDED");
    private static final Set<String> YEAR_STATUSES   = Set.of("ACTIVE","DATA_COLLECTION","REVIEW","APPROVED","PUBLISHED","ARCHIVED");

    /** Report types supported by ReportComposerService (slice 12). */
    private static final Set<String> REPORT_TYPES = Set.of(
        "ANNUAL_ANNEX", "BOARD_ESG", "CMA_MATRIX", "TCFD_REPORT",
        "GHG_REPORT",   "SGI_PROGRESS", "CLIMATE_RISK", "CAPA_STATUS",
        "EVIDENCE_PKG", "DASH_EXPORT");

    private final ReportingYearRepository           yearRepo;
    private final SustainabilityReportRepository    reportRepo;
    private final AnnexGeneratorService             generator;
    private final ReportComposerService             composer;
    private final AuditLogService                   audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Reporting year ───────────────────────────────────────────────
    public List<ReportingYear> listYears() {
        return yearRepo.findByOrganisationIdOrderByYearDesc(org());
    }

    public ReportingYear getYear(Long id) {
        return yearRepo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("ReportingYear " + id));
    }

    @Transactional
    public ReportingYear createYear(ReportingYear req) {
        req.setId(null);
        req.setOrganisationId(org());
        if (req.getStatus() == null) req.setStatus("ACTIVE");
        ReportingYear saved = yearRepo.save(req);
        audit.log("CREATE", "ReportingYear", saved.getId(), "Reporting year created: " + saved.getYear());
        return saved;
    }

    @Transactional
    public ReportingYear transitionYear(Long id, String to) {
        ReportingYear cur = getYear(id);
        if (!YEAR_STATUSES.contains(to))
            throw new IllegalArgumentException("Unknown year status: " + to);
        // Linear forward + ARCHIVED any-time.
        String from = cur.getStatus();
        boolean ok =
            ("ACTIVE".equals(from)          && "DATA_COLLECTION".equals(to))
         || ("DATA_COLLECTION".equals(from) && "REVIEW".equals(to))
         || ("REVIEW".equals(from)          && "APPROVED".equals(to))
         || ("APPROVED".equals(from)        && "PUBLISHED".equals(to))
         || "ARCHIVED".equals(to);
        if (!ok) throw new IllegalTransitionException(from, to, Set.of("DATA_COLLECTION","REVIEW","APPROVED","PUBLISHED","ARCHIVED"));
        cur.setStatus(to);
        LocalDateTime now = LocalDateTime.now();
        if ("APPROVED".equals(to))  cur.setApprovedAt(now);
        if ("PUBLISHED".equals(to)) cur.setPublishedAt(now);
        ReportingYear saved = yearRepo.save(cur);
        audit.log("TRANSITION", "ReportingYear", saved.getId(), "Reporting year -> " + to);
        return saved;
    }

    // ── Reports ──────────────────────────────────────────────────────
    public Page<SustainabilityReport> listReports(int page, int size) {
        return reportRepo.findByOrganisationIdOrderByGeneratedAtDescIdDesc(org(), PageRequest.of(page, size));
    }

    public List<SustainabilityReport> listReportsForYear(Long ryId) {
        // Validate the year belongs to us before listing.
        getYear(ryId);
        return reportRepo.findByOrganisationIdAndReportingYearIdOrderByGeneratedAtDescIdDesc(org(), ryId);
    }

    public SustainabilityReport getReport(Long id) {
        return reportRepo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("SustainabilityReport " + id));
    }

    /**
     * Generate an annual annex report — kept for backwards compatibility
     * with the existing /generate-annex endpoint. Delegates to the
     * generic {@link #generateReport(Long, String)}.
     */
    @Transactional
    public SustainabilityReport generateAnnex(Long ryId) {
        return generateReport(ryId, "ANNUAL_ANNEX");
    }

    /**
     * Generic report generator (slice 12). Dispatches per reportType
     * through {@link ReportComposerService}. Always creates a fresh
     * DRAFT row — re-running generates v(N+1) at the same type.
     */
    @Transactional
    public SustainabilityReport generateReport(Long ryId, String reportType) {
        if (!REPORT_TYPES.contains(reportType))
            throw new IllegalArgumentException("Unknown reportType: " + reportType);
        ReportingYear ry = getYear(ryId);

        var content = composer.compose(reportType,
            ry.getYear(),
            ry.getReportingEntityEn(),
            ry.getReportingEntityAr());

        // Version = v{N+1} of existing rows for this year + type.
        List<SustainabilityReport> existing = reportRepo
            .findByOrganisationIdAndReportingYearIdOrderByGeneratedAtDescIdDesc(org(), ryId);
        long sameTypeCount = existing.stream().filter(r -> reportType.equals(r.getReportType())).count();
        String version = "v" + (sameTypeCount + 1) + ".0";

        SustainabilityReport r = new SustainabilityReport();
        r.setOrganisationId(org());
        r.setReportingYearId(ryId);
        r.setReportType(reportType);
        r.setTitleEn(reportTitleEn(reportType, ry.getYear(), ry.getReportingEntityEn()));
        r.setTitleAr(ry.getReportingEntityAr() == null ? null
            : reportTitleAr(reportType, ry.getYear(), ry.getReportingEntityAr()));
        r.setVersion(version);
        r.setStatus("DRAFT");
        r.setReportCode(String.format("%s-%d-%s", reportCodePrefix(reportType), ry.getYear(), version));
        r.setContentMarkdown(content.markdown());
        r.setContentHtml(content.html());
        r.setContentJson(content.json());
        SustainabilityReport saved = reportRepo.save(r);
        audit.log("CREATE", "SustainabilityReport", saved.getId(),
            "Generated " + reportType + " report " + saved.getReportCode());
        return saved;
    }

    private static String reportTitleEn(String type, short year, String org) {
        String name = switch (type) {
            case "ANNUAL_ANNEX"  -> "Sustainability Annex";
            case "BOARD_ESG"     -> "Board ESG Report";
            case "CMA_MATRIX"    -> "CMA Disclosure Matrix";
            case "TCFD_REPORT"   -> "TCFD Alignment Report";
            case "GHG_REPORT"    -> "GHG Inventory Report";
            case "SGI_PROGRESS"  -> "SGI Progress Report";
            case "CLIMATE_RISK"  -> "Climate Risk & Opportunity Register";
            case "CAPA_STATUS"   -> "Corrective Actions Status Report";
            case "EVIDENCE_PKG"  -> "Evidence Package";
            case "DASH_EXPORT"   -> "Executive Dashboard Export";
            default -> "Sustainability Report";
        };
        return String.format("%s %d — %s", name, year, org == null ? "" : org);
    }

    private static String reportTitleAr(String type, short year, String orgAr) {
        String name = switch (type) {
            case "ANNUAL_ANNEX"  -> "ملحق الاستدامة";
            case "BOARD_ESG"     -> "تقرير الاستدامة لمجلس الإدارة";
            case "CMA_MATRIX"    -> "مصفوفة إفصاحات هيئة السوق المالية";
            case "TCFD_REPORT"   -> "تقرير محاذاة TCFD";
            case "GHG_REPORT"    -> "تقرير سجل الانبعاثات";
            case "SGI_PROGRESS"  -> "تقرير تقدّم التزامات SGI";
            case "CLIMATE_RISK"  -> "سجلّ المخاطر والفرص المناخية";
            case "CAPA_STATUS"   -> "تقرير حالة الإجراءات التصحيحية";
            case "EVIDENCE_PKG"  -> "حزمة الأدلة";
            case "DASH_EXPORT"   -> "تصدير لوحة الإدارة";
            default -> "تقرير استدامة";
        };
        return String.format("%s %d — %s", name, year, orgAr);
    }

    private static String reportCodePrefix(String type) {
        return switch (type) {
            case "ANNUAL_ANNEX"  -> "ANN";
            case "BOARD_ESG"     -> "BRD";
            case "CMA_MATRIX"    -> "CMA";
            case "TCFD_REPORT"   -> "TCFD";
            case "GHG_REPORT"    -> "GHG";
            case "SGI_PROGRESS"  -> "SGI";
            case "CLIMATE_RISK"  -> "CLI";
            case "CAPA_STATUS"   -> "CAPA";
            case "EVIDENCE_PKG"  -> "EVP";
            case "DASH_EXPORT"   -> "DASH";
            default -> "RPT";
        };
    }

    @Transactional
    public SustainabilityReport transitionReport(Long id, String to) {
        SustainabilityReport cur = getReport(id);
        if (!REPORT_STATUSES.contains(to))
            throw new IllegalArgumentException("Unknown report status: " + to);
        String from = cur.getStatus();
        boolean ok =
            ("DRAFT".equals(from)     && "SUBMITTED".equals(to))
         || ("SUBMITTED".equals(from) && "APPROVED".equals(to))
         || ("APPROVED".equals(from)  && "PUBLISHED".equals(to));
        if (!ok) throw new IllegalTransitionException(from, to, Set.of("SUBMITTED","APPROVED","PUBLISHED"));
        cur.setStatus(to);
        LocalDateTime now = LocalDateTime.now();
        if ("APPROVED".equals(to))  cur.setApprovedAt(now);
        if ("PUBLISHED".equals(to)) {
            cur.setPublishedAt(now);
            // Supersede earlier PUBLISHED rows of the same type/year.
            for (SustainabilityReport sib : reportRepo
                    .findByOrganisationIdAndReportingYearIdOrderByGeneratedAtDescIdDesc(
                        org(), cur.getReportingYearId())) {
                if (sib.getId().equals(cur.getId())) continue;
                if (!cur.getReportType().equals(sib.getReportType())) continue;
                if ("PUBLISHED".equals(sib.getStatus())) {
                    sib.setStatus("SUPERSEDED");
                    reportRepo.save(sib);
                }
            }
        }
        SustainabilityReport saved = reportRepo.save(cur);
        audit.log("TRANSITION", "SustainabilityReport", saved.getId(), "Report -> " + to);
        return saved;
    }
}
