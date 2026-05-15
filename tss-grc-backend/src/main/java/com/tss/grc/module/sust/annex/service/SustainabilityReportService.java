package com.tss.grc.module.sust.annex.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
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

    private final ReportingYearRepository           yearRepo;
    private final SustainabilityReportRepository    reportRepo;
    private final AnnexGeneratorService             generator;

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
        return yearRepo.save(req);
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
        return yearRepo.save(cur);
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
     * Generate an annual annex report for the given reporting-year. Always
     * creates a fresh DRAFT row — the caller can submit/approve later.
     * Re-running generation produces a new draft row at version v(N+1).
     */
    @Transactional
    public SustainabilityReport generateAnnex(Long ryId) {
        ReportingYear ry = getYear(ryId);

        var content = generator.generate(
            ry.getYear(),
            ry.getReportingEntityEn(),
            ry.getReportingEntityAr());

        // Version = v{N+1} of the existing rows for this year + type.
        List<SustainabilityReport> existing = reportRepo
            .findByOrganisationIdAndReportingYearIdOrderByGeneratedAtDescIdDesc(org(), ryId);
        long annualCount = existing.stream().filter(r -> "ANNUAL_ANNEX".equals(r.getReportType())).count();
        String version = "v" + (annualCount + 1) + ".0";

        SustainabilityReport r = new SustainabilityReport();
        r.setOrganisationId(org());
        r.setReportingYearId(ryId);
        r.setReportType("ANNUAL_ANNEX");
        r.setTitleEn(String.format("Sustainability Annex %d — %s", ry.getYear(), ry.getReportingEntityEn()));
        r.setTitleAr(ry.getReportingEntityAr() == null ? null
            : String.format("ملحق الاستدامة %d — %s", ry.getYear(), ry.getReportingEntityAr()));
        r.setVersion(version);
        r.setStatus("DRAFT");
        r.setReportCode(String.format("ANN-%d-%s", ry.getYear(), version));
        r.setContentMarkdown(content.markdown());
        r.setContentHtml(content.html());
        r.setContentJson(content.json());
        return reportRepo.save(r);
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
        return reportRepo.save(cur);
    }
}
