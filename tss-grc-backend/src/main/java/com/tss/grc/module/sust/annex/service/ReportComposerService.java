package com.tss.grc.module.sust.annex.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.annex.service.AnnexGeneratorService.AnnexContent;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.repository.CorrectiveActionRepository;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
import com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository;
import com.tss.grc.module.sust.cma.entity.CmaDisclosure;
import com.tss.grc.module.sust.cma.repository.CmaDisclosureRepository;
import com.tss.grc.module.sust.cma.service.CmaDisclosureService;
import com.tss.grc.module.sust.dashboard.service.SustainabilityDashboardService;
import com.tss.grc.module.sust.evidence.entity.SustainabilityEvidence;
import com.tss.grc.module.sust.evidence.repository.SustainabilityEvidenceRepository;
import com.tss.grc.module.sust.evidence.service.SustainabilityEvidenceService;
import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
import com.tss.grc.module.sust.ghg.service.GhgEmissionRecordService;
import com.tss.grc.module.sust.sgi.entity.SgiCommitment;
import com.tss.grc.module.sust.sgi.repository.SgiCommitmentRepository;
import com.tss.grc.module.sust.tcfd.entity.TcfdItem;
import com.tss.grc.module.sust.tcfd.repository.TcfdItemRepository;
import com.tss.grc.module.sust.tcfd.service.TcfdItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Multi-type Sustainability report composer (slice 12).
 *
 * <p>Dispatches per {@code reportType} to a thin composer per type
 * that builds a Markdown + HTML + JSON triple from the same underlying
 * slice data. Designed so the {@code SustainabilityReport.report_type}
 * enum is fully covered:
 * <pre>
 *   ANNUAL_ANNEX   delegates to AnnexGeneratorService (untouched)
 *   BOARD_ESG      executive 1-pager: ESG score + top gaps + YoY
 *   CMA_MATRIX     the 11-article disclosure status grid
 *   TCFD_REPORT    4-pillar disclosure matrix
 *   GHG_REPORT     GHG inventory deep with verification status
 *   SGI_PROGRESS   SGI commitments per-sector progress register
 *   CLIMATE_RISK   climate risk + opportunity register by score
 *   CAPA_STATUS    corrective actions by status + overdue list
 *   EVIDENCE_PKG   evidence repository snapshot by module + expiry
 *   DASH_EXPORT    machine-readable dashboard JSON snapshot
 * </pre>
 *
 * <p>Every composer keeps a consistent shape — title heading, summary
 * paragraph, sections — so the export bundle is uniform regardless of
 * which kind of report a user asks for.
 */
@Service
@RequiredArgsConstructor
public class ReportComposerService {

    private final ObjectMapper                     objectMapper;

    private final AnnexGeneratorService            annexGenerator;
    private final SustainabilityDashboardService   dashboardService;

    private final GhgEmissionRecordRepository      ghgRepo;
    private final GhgEmissionRecordService         ghgService;
    private final SgiCommitmentRepository          sgiRepo;
    private final CmaDisclosureRepository          cmaRepo;
    private final CmaDisclosureService             cmaService;
    private final TcfdItemRepository               tcfdRepo;
    private final TcfdItemService                  tcfdService;
    private final ClimateRiskOpportunityRepository climateRepo;
    private final CorrectiveActionRepository       capaRepo;
    private final CorrectiveActionService          capaService;
    private final SustainabilityEvidenceRepository evidenceRepo;
    private final SustainabilityEvidenceService    evidenceService;

    /**
     * Dispatch per report type. Returns the canonical AnnexContent
     * triple regardless of which generator runs.
     */
    public AnnexContent compose(String reportType, short year, String orgEn, String orgAr) {
        return switch (reportType) {
            case "ANNUAL_ANNEX"  -> annexGenerator.generate(year, orgEn, orgAr);
            case "BOARD_ESG"     -> boardEsg(year, orgEn, orgAr);
            case "CMA_MATRIX"    -> cmaMatrix(year, orgEn, orgAr);
            case "TCFD_REPORT"   -> tcfdReport(year, orgEn, orgAr);
            case "GHG_REPORT"    -> ghgReport(year, orgEn, orgAr);
            case "SGI_PROGRESS"  -> sgiProgress(year, orgEn, orgAr);
            case "CLIMATE_RISK"  -> climateRisk(year, orgEn, orgAr);
            case "CAPA_STATUS"   -> capaStatus(year, orgEn, orgAr);
            case "EVIDENCE_PKG"  -> evidencePkg(year, orgEn, orgAr);
            case "DASH_EXPORT"   -> dashExport(year, orgEn, orgAr);
            default -> throw new IllegalArgumentException("Unknown report type: " + reportType);
        };
    }

    // ─── BOARD_ESG ──────────────────────────────────────────────────

    private AnnexContent boardEsg(short year, String orgEn, String orgAr) {
        Map<String, Object> dash = dashboardService.dashboard(year);
        @SuppressWarnings("unchecked")
        Map<String, Object> overall = (Map<String, Object>) dash.get("overall");
        @SuppressWarnings("unchecked")
        Map<String, Object> ghgYoy = (Map<String, Object>) dash.get("ghgYoy");
        @SuppressWarnings("unchecked")
        List<String> topGaps = (List<String>) dash.get("topGaps");

        StringBuilder md = new StringBuilder();
        md.append("# Board ESG Report — ").append(year).append('\n');
        md.append("# تقرير الاستدامة لمجلس الإدارة — ").append(year).append("\n\n");
        appendOrgLine(md, orgEn, orgAr);

        md.append("\n## 1. ESG Overall Score — مؤشّر الاستدامة الشامل\n\n");
        md.append("**Score:** ").append(overall.get("score")).append("/100 — ")
          .append(overall.get("band")).append("\n\n");

        @SuppressWarnings("unchecked")
        Map<String, Object> breakdown = (Map<String, Object>) overall.get("breakdown");
        md.append("| Component | Score (%) |\n|---|---|\n");
        for (var e : breakdown.entrySet()) {
            md.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        }

        md.append("\n## 2. Year-over-Year Emissions — التغيّر السنوي للانبعاثات\n\n");
        md.append("| Scope | YoY (%) |\n|---|---|\n");
        md.append("| Scope 1 | ").append(ghgYoy.get("scope1YoY")).append(" |\n");
        md.append("| Scope 2 | ").append(ghgYoy.get("scope2YoY")).append(" |\n");
        md.append("| Scope 3 | ").append(ghgYoy.get("scope3YoY")).append(" |\n");
        md.append("| Total   | ").append(ghgYoy.get("totalYoY")).append(" |\n");

        md.append("\n## 3. Top ESG Gaps — أبرز الثغرات\n\n");
        if (topGaps.isEmpty()) {
            md.append("_No executive-level gaps detected for this cycle._\n");
        } else {
            for (String g : topGaps) md.append("- ").append(g).append('\n');
        }

        return wrap(md.toString(), Map.of(
            "reportType", "BOARD_ESG", "year", year,
            "overall", overall, "ghgYoy", ghgYoy, "topGaps", topGaps));
    }

    // ─── CMA_MATRIX ─────────────────────────────────────────────────

    private AnnexContent cmaMatrix(short year, String orgEn, String orgAr) {
        Long org = TenantContext.getCurrentTenant();
        List<CmaDisclosure> rows = cmaRepo.findByOrganisationIdAndReportingYear(org, year);
        rows.sort(Comparator.comparing(CmaDisclosure::getArticle)
            .thenComparing(d -> d.getClause() == null ? "" : d.getClause()));
        var readiness = cmaService.readiness(year);

        StringBuilder md = new StringBuilder();
        md.append("# CMA Disclosure Matrix — مصفوفة إفصاحات CMA — ").append(year).append("\n\n");
        appendOrgLine(md, orgEn, orgAr);

        md.append("\n**Readiness:** ").append(readiness.get("readinessPct")).append("% — ")
          .append(readiness.get("byStatus")).append("\n\n");

        md.append("| Article | Title | Status | Evidence | Gap |\n");
        md.append("|---|---|---|---|---|\n");
        for (var d : rows) {
            md.append("| Art. ").append(d.getArticle())
              .append(d.getClause() == null ? "" : d.getClause()).append(" | ")
              .append(safe(d.getTitleEn())).append(" | ")
              .append(d.getDisclosureStatus()).append(" | ")
              .append(safe(d.getEvidenceRef())).append(" | ")
              .append(d.getGapDescriptionEn() != null ? safe(d.getGapDescriptionEn()) : "—")
              .append(" |\n");
        }
        return wrap(md.toString(), Map.of(
            "reportType", "CMA_MATRIX", "year", year,
            "readiness", readiness, "disclosures", rows.stream().map(this::cmaDto).toList()));
    }

    // ─── TCFD_REPORT ────────────────────────────────────────────────

    private AnnexContent tcfdReport(short year, String orgEn, String orgAr) {
        Long org = TenantContext.getCurrentTenant();
        List<TcfdItem> rows = tcfdRepo.findByOrganisationIdAndReportingYear(org, year);
        rows.sort(Comparator.comparing(TcfdItem::getPillar).thenComparing(TcfdItem::getRecommendationCode));
        var readiness = tcfdService.readiness(year);

        StringBuilder md = new StringBuilder();
        md.append("# TCFD Alignment Report — تقرير محاذاة TCFD — ").append(year).append("\n\n");
        appendOrgLine(md, orgEn, orgAr);
        md.append("\n**Alignment:** ").append(readiness.get("alignmentPct")).append("%\n");
        if (Boolean.TRUE.equals(readiness.get("strategyResilienceGap"))) {
            md.append("\n> ⚠ Rule 7: Strategy resilience scenario not fully disclosed.\n");
        }

        Map<String, List<TcfdItem>> byPillar = new LinkedHashMap<>();
        for (var t : rows) byPillar.computeIfAbsent(t.getPillar(), k -> new ArrayList<>()).add(t);

        for (var pe : byPillar.entrySet()) {
            md.append("\n## ").append(pe.getKey()).append('\n');
            md.append("| Rec. | Title | Status | Evidence |\n|---|---|---|---|\n");
            for (var t : pe.getValue()) {
                md.append("| (").append(t.getRecommendationCode()).append(") | ")
                  .append(safe(t.getTitleEn())).append(" | ")
                  .append(t.getDisclosureStatus()).append(" | ")
                  .append(safe(t.getEvidenceRef())).append(" |\n");
            }
        }
        return wrap(md.toString(), Map.of(
            "reportType", "TCFD_REPORT", "year", year,
            "readiness", readiness, "items", rows.stream().map(this::tcfdDto).toList()));
    }

    // ─── GHG_REPORT ─────────────────────────────────────────────────

    private AnnexContent ghgReport(short year, String orgEn, String orgAr) {
        Long org = TenantContext.getCurrentTenant();
        List<GhgEmissionRecord> rows = ghgRepo.findByOrganisationIdAndReportingYear(org, year);
        rows.sort(Comparator.comparing(GhgEmissionRecord::getScope)
            .thenComparing(GhgEmissionRecord::getRecordCode, Comparator.nullsLast(String::compareTo)));
        var agg = ghgService.aggregate(year);
        var yoy = ghgService.yoy(year);

        StringBuilder md = new StringBuilder();
        md.append("# GHG Inventory Report — تقرير سجل الانبعاثات — ").append(year).append("\n\n");
        appendOrgLine(md, orgEn, orgAr);

        md.append("\n## 1. Totals\n\n");
        md.append("| Scope | tCO2e | YoY (%) |\n|---|---|---|\n");
        md.append("| Scope 1 | ").append(agg.get("scope1Total")).append(" | ").append(yoy.get("scope1YoY")).append(" |\n");
        md.append("| Scope 2 | ").append(agg.get("scope2Total")).append(" | ").append(yoy.get("scope2YoY")).append(" |\n");
        md.append("| Scope 3 | ").append(agg.get("scope3Total")).append(" | ").append(yoy.get("scope3YoY")).append(" |\n");
        md.append("| **Total** | **").append(agg.get("grandTotal")).append("** | ").append(yoy.get("totalYoY")).append(" |\n\n");
        md.append("Scope 3 coverage: **").append(agg.get("scope3Coverage")).append("%**");
        if (Boolean.TRUE.equals(agg.get("scope3PartialFlag"))) {
            md.append(" — ⚠ Partial (Rule 1)");
        }

        md.append("\n\n## 2. Per-Record Detail\n\n");
        md.append("| Code | Scope | Source | tCO2e | Verified | Provider |\n");
        md.append("|---|---|---|---|---|---|\n");
        for (var r : rows) {
            md.append("| ").append(safe(r.getRecordCode())).append(" | ")
              .append(r.getScope()).append(" | ")
              .append(safe(r.getSourceNameEn())).append(" | ")
              .append(r.getEmissionValueTco2e()).append(" | ")
              .append(Boolean.TRUE.equals(r.getVerified()) ? "✓" : "—").append(" | ")
              .append(safe(r.getVerificationProvider())).append(" |\n");
        }
        return wrap(md.toString(), Map.of(
            "reportType", "GHG_REPORT", "year", year,
            "aggregate", agg, "yoy", yoy, "records", rows.stream().map(this::ghgDto).toList()));
    }

    // ─── SGI_PROGRESS ───────────────────────────────────────────────

    private AnnexContent sgiProgress(short year, String orgEn, String orgAr) {
        Long org = TenantContext.getCurrentTenant();
        List<SgiCommitment> rows = sgiRepo.findByOrganisationIdAndStatus(org, "ACTIVE");
        rows.sort(Comparator.comparing(SgiCommitment::getTargetYear)
            .thenComparing(SgiCommitment::getCommitmentCode));

        StringBuilder md = new StringBuilder();
        md.append("# Saudi Green Initiative Progress — تقدّم التزامات SGI — ").append(year).append("\n\n");
        appendOrgLine(md, orgEn, orgAr);

        md.append("\n| Code | Sector | Commitment | Baseline | Current | Target | Year |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (var c : rows) {
            md.append("| ").append(safe(c.getCommitmentCode())).append(" | ")
              .append(c.getSector()).append(" | ")
              .append(safe(c.getTitleEn())).append(" | ")
              .append(c.getBaselineValue()).append(' ').append(safe(c.getUnit())).append(" | ")
              .append(c.getCurrentValue()).append(' ').append(safe(c.getUnit())).append(" | ")
              .append(c.getTargetValue()).append(' ').append(safe(c.getUnit())).append(" | ")
              .append(c.getTargetYear()).append(" |\n");
        }
        return wrap(md.toString(), Map.of(
            "reportType", "SGI_PROGRESS", "year", year,
            "commitments", rows.stream().map(this::sgiDto).toList()));
    }

    // ─── CLIMATE_RISK ───────────────────────────────────────────────

    private AnnexContent climateRisk(short year, String orgEn, String orgAr) {
        Long org = TenantContext.getCurrentTenant();
        List<ClimateRiskOpportunity> rows = climateRepo
            .findByOrganisationIdOrderByRiskScoreDescIdDesc(org, PageRequest.of(0, 100))
            .getContent();

        StringBuilder md = new StringBuilder();
        md.append("# Climate Risk & Opportunity Register — سجلّ المخاطر والفرص المناخية — ").append(year).append("\n\n");
        appendOrgLine(md, orgEn, orgAr);

        md.append("\n| Code | Kind | Category | Horizon | Likelihood × Impact | Score | Status |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (var c : rows) {
            md.append("| ").append(safe(c.getItemCode())).append(" | ")
              .append(c.getKind()).append(" | ")
              .append(c.getCategory()).append(" | ")
              .append(c.getTimeHorizon()).append(" | ")
              .append(c.getLikelihood()).append(" × ").append(c.getImpact()).append(" | ")
              .append(c.getRiskScore()).append(" | ")
              .append(c.getStatus()).append(" |\n");
        }
        return wrap(md.toString(), Map.of(
            "reportType", "CLIMATE_RISK", "year", year,
            "items", rows.stream().map(this::climateDto).toList()));
    }

    // ─── CAPA_STATUS ────────────────────────────────────────────────

    private AnnexContent capaStatus(short year, String orgEn, String orgAr) {
        Long org = TenantContext.getCurrentTenant();
        List<CorrectiveAction> rows = capaRepo.findByOrganisationIdAndStatusIn(
            org, List.of("OPEN", "IN_PROGRESS", "COMPLETED", "VERIFIED"));
        var agg = capaService.aggregate();

        StringBuilder md = new StringBuilder();
        md.append("# Corrective Actions Status — حالة الإجراءات التصحيحية — ").append(year).append("\n\n");
        appendOrgLine(md, orgEn, orgAr);

        md.append("\n**Active total:** ").append(agg.get("activeTotal"))
          .append(" · Open: ").append(agg.get("open"))
          .append(" · In progress: ").append(agg.get("inProgress"))
          .append(" · Awaiting verification: ").append(agg.get("completed"))
          .append(" · Verified: ").append(agg.get("verified"))
          .append("\n\n**Overdue:** ").append(agg.get("overdue"))
          .append(" · **Due in 30 days:** ").append(agg.get("dueSoon")).append("\n\n");

        md.append("| Code | Title | Priority | Status | Source | Due |\n");
        md.append("|---|---|---|---|---|---|\n");
        for (var a : rows) {
            md.append("| ").append(safe(a.getActionCode())).append(" | ")
              .append(safe(a.getTitleEn())).append(" | ")
              .append(a.getPriority()).append(" | ")
              .append(a.getStatus()).append(" | ")
              .append(a.getSourceType()).append(":").append(safe(a.getSourceRef())).append(" | ")
              .append(a.getTargetDate() == null ? "—" : a.getTargetDate().toString()).append(" |\n");
        }
        return wrap(md.toString(), Map.of(
            "reportType", "CAPA_STATUS", "year", year,
            "aggregate", agg, "actions", rows.stream().map(this::capaDto).toList()));
    }

    // ─── EVIDENCE_PKG ───────────────────────────────────────────────

    private AnnexContent evidencePkg(short year, String orgEn, String orgAr) {
        Long org = TenantContext.getCurrentTenant();
        // Pull all evidence regardless of year for the package.
        List<SustainabilityEvidence> rows = evidenceRepo
            .findByOrganisationIdOrderByCreatedAtDescIdDesc(org, PageRequest.of(0, 1000))
            .getContent();
        var agg = evidenceService.aggregate();

        StringBuilder md = new StringBuilder();
        md.append("# Evidence Package — حزمة الأدلة — ").append(year).append("\n\n");
        appendOrgLine(md, orgEn, orgAr);
        md.append("\n**Total:** ").append(agg.get("total"))
          .append(" · **Expiring in 30 days:** ").append(agg.get("expiringSoon30d"))
          .append("\n\n");

        md.append("| Code | Title | Module | Type | Version | Valid To | Status |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (var e : rows) {
            md.append("| ").append(safe(e.getEvidenceCode())).append(" | ")
              .append(safe(e.getTitleEn())).append(" | ")
              .append(safe(e.getModuleArea())).append(" | ")
              .append(safe(e.getEvidenceType())).append(" | ")
              .append(safe(e.getVersion())).append(" | ")
              .append(e.getValidTo() == null ? "—" : e.getValidTo().toString()).append(" | ")
              .append(e.getApprovalStatus()).append(" |\n");
        }
        return wrap(md.toString(), Map.of(
            "reportType", "EVIDENCE_PKG", "year", year,
            "aggregate", agg, "items", rows.stream().map(this::evidenceDto).toList()));
    }

    // ─── DASH_EXPORT ────────────────────────────────────────────────

    private AnnexContent dashExport(short year, String orgEn, String orgAr) {
        Map<String, Object> dash = dashboardService.dashboard(year);
        StringBuilder md = new StringBuilder();
        md.append("# Executive Dashboard Snapshot — صورة لوحة الإدارة — ").append(year).append("\n\n");
        appendOrgLine(md, orgEn, orgAr);
        md.append("\nMachine-readable dashboard JSON below. See dashboard tab for visualisation.\n");

        // Body is mostly the JSON itself — match the spec's expected
        // "DASH_EXPORT" purpose of being a downloadable data dump.
        return wrap(md.toString(), dash);
    }

    // ─── Common helpers ─────────────────────────────────────────────

    private static void appendOrgLine(StringBuilder md, String orgEn, String orgAr) {
        md.append("**Reporting entity:** ").append(orgEn == null ? "" : orgEn);
        if (orgAr != null && !orgAr.isBlank()) md.append(" / ").append(orgAr);
        md.append('\n');
    }

    private AnnexContent wrap(String markdown, Map<String, Object> json) {
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><style>"
            + "body{font-family:sans-serif;max-width:840px;margin:24px auto;padding:0 16px;line-height:1.55}"
            + "table{border-collapse:collapse;width:100%;margin:12px 0;font-size:13px}"
            + "th,td{border:1px solid #e5e7eb;padding:6px 10px;text-align:start}"
            + "th{background:#f9fafb;font-weight:700}"
            + "h1,h2{margin-top:24px}h1{font-size:22px}h2{font-size:17px}"
            + "blockquote{border-inline-start:3px solid #fbbf24;padding-inline-start:12px;color:#92400e;background:#fef3c7;margin:12px 0}"
            + "</style></head><body>"
            + markdownToHtml(markdown)
            + "</body></html>";
        return new AnnexContent(markdown, html, toJson(json));
    }

    /** Minimal Markdown→HTML conversion — enough for headings, tables, lists, bold.
     *  F2-391: package-private so ReportComposerServiceTest can drive the table
     *  renderer directly (the public annex builders need heavy collaborators). */
    static String markdownToHtml(String md) {
        StringBuilder out = new StringBuilder(md.length() * 2);
        boolean inTable = false;
        boolean firstRowOfTable = false;
        boolean inList = false;
        for (String line : md.split("\n", -1)) {
            String t = line.trim();
            // Tables — lines starting with "|".
            if (t.startsWith("|")) {
                if (t.matches("\\|\\s*-.*")) continue;  // separator row
                if (!inTable) { out.append("<table>"); inTable = true; firstRowOfTable = true; }
                // F2-391: split with limit -1 to KEEP the trailing empty cell of a
                // pipe-terminated row ("| a | b | c |" -> ["", " a ", " b ", " c ", ""]).
                // The default limit 0 discards trailing empties, so the array was one
                // shorter than the loop bound below assumes — the bound already subtracts
                // one for a trailing "|", which only balances if that empty is present.
                // Without -1 the last data cell of every pipe-terminated row was dropped
                // (and a single-cell row rendered empty). Matches the md.split("\n", -1)
                // idiom this method already uses on line 418.
                String[] cells = t.split("\\|", -1);
                out.append("<tr>");
                for (int i = 1; i < cells.length - (t.endsWith("|") ? 1 : 0); i++) {
                    String c = cells[i].trim();
                    // First cell of the first row of a table is treated as <th>.
                    boolean isHeader = firstRowOfTable;
                    firstRowOfTable = false;
                    out.append(isHeader ? "<th>" : "<td>")
                       .append(escape(stripBold(c)))
                       .append(isHeader ? "</th>" : "</td>");
                }
                out.append("</tr>");
                continue;
            }
            if (inTable) { out.append("</table>"); inTable = false; }
            // Lists
            if (t.startsWith("- ")) {
                if (!inList) { out.append("<ul>"); inList = true; }
                out.append("<li>").append(escape(stripBold(t.substring(2)))).append("</li>");
                continue;
            }
            if (inList) { out.append("</ul>"); inList = false; }
            // Headings
            if (t.startsWith("# "))   { out.append("<h1>").append(escape(stripBold(t.substring(2)))).append("</h1>"); continue; }
            if (t.startsWith("## "))  { out.append("<h2>").append(escape(stripBold(t.substring(3)))).append("</h2>"); continue; }
            if (t.startsWith("> "))   { out.append("<blockquote>").append(escape(stripBold(t.substring(2)))).append("</blockquote>"); continue; }
            if (t.isEmpty())          { out.append("<p></p>"); continue; }
            out.append("<p>").append(escape(stripBold(t))).append("</p>");
        }
        if (inTable) out.append("</table>");
        if (inList) out.append("</ul>");
        return out.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
    /** Drop **bold** markers since we don't render them in this minimal pass. */
    private static String stripBold(String s) { return s.replace("**", ""); }
    private static String safe(String s) { return s == null ? "—" : s; }

    /** JSON serializer — delegates to the shared Jackson ObjectMapper.
     *  Falls back to the minimal hand-rolled serializer on the rare
     *  JsonProcessingException so the export never fails outright. */
    private String toJson(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException ex) {
            return toJsonFallback(v);
        }
    }

    /** Minimal hand-rolled serializer retained as a fallback for the
     *  shapes we emit when Jackson serialization fails. */
    @SuppressWarnings("unchecked")
    private static String toJsonFallback(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean || v instanceof Number) return v.toString();
        if (v instanceof String s) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        if (v instanceof Map<?, ?> m) {
            StringBuilder b = new StringBuilder("{");
            boolean first = true;
            for (var e : ((Map<String, Object>) m).entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append('"').append(e.getKey()).append("\":").append(toJsonFallback(e.getValue()));
            }
            return b.append('}').toString();
        }
        if (v instanceof Iterable<?> it) {
            StringBuilder b = new StringBuilder("[");
            boolean first = true;
            for (var item : it) {
                if (!first) b.append(',');
                first = false;
                b.append(toJsonFallback(item));
            }
            return b.append(']').toString();
        }
        return "\"" + v.toString().replace("\"", "\\\"") + "\"";
    }

    // ─── Lightweight per-entity DTO builders for the JSON output ───
    // Kept inside the service so the JSON shape lives next to the
    // markdown shape — easier to keep them in sync over time.

    private Map<String, Object> cmaDto(CmaDisclosure d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("article", d.getArticle()); m.put("clause", d.getClause());
        m.put("titleEn", d.getTitleEn()); m.put("titleAr", d.getTitleAr());
        m.put("status", d.getDisclosureStatus()); m.put("evidenceRef", d.getEvidenceRef());
        m.put("gapDescriptionEn", d.getGapDescriptionEn());
        return m;
    }
    private Map<String, Object> tcfdDto(TcfdItem t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pillar", t.getPillar()); m.put("recommendationCode", t.getRecommendationCode());
        m.put("titleEn", t.getTitleEn()); m.put("titleAr", t.getTitleAr());
        m.put("status", t.getDisclosureStatus()); m.put("evidenceRef", t.getEvidenceRef());
        return m;
    }
    private Map<String, Object> ghgDto(GhgEmissionRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recordCode", r.getRecordCode()); m.put("scope", r.getScope());
        m.put("category", r.getCategory());
        m.put("sourceNameEn", r.getSourceNameEn()); m.put("sourceNameAr", r.getSourceNameAr());
        m.put("emissionValueTco2e", r.getEmissionValueTco2e());
        m.put("verified", r.getVerified()); m.put("verificationProvider", r.getVerificationProvider());
        return m;
    }
    private Map<String, Object> sgiDto(SgiCommitment c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("commitmentCode", c.getCommitmentCode()); m.put("sector", c.getSector());
        m.put("titleEn", c.getTitleEn()); m.put("titleAr", c.getTitleAr());
        m.put("baselineValue", c.getBaselineValue()); m.put("currentValue", c.getCurrentValue());
        m.put("targetValue", c.getTargetValue()); m.put("targetYear", c.getTargetYear());
        m.put("unit", c.getUnit()); m.put("status", c.getStatus());
        return m;
    }
    private Map<String, Object> climateDto(ClimateRiskOpportunity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemCode", c.getItemCode()); m.put("kind", c.getKind());
        m.put("category", c.getCategory()); m.put("timeHorizon", c.getTimeHorizon());
        m.put("likelihood", c.getLikelihood()); m.put("impact", c.getImpact());
        m.put("riskScore", c.getRiskScore()); m.put("status", c.getStatus());
        return m;
    }
    private Map<String, Object> capaDto(CorrectiveAction a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("actionCode", a.getActionCode()); m.put("titleEn", a.getTitleEn());
        m.put("priority", a.getPriority()); m.put("status", a.getStatus());
        m.put("sourceType", a.getSourceType()); m.put("sourceRef", a.getSourceRef());
        m.put("targetDate", a.getTargetDate() == null ? null : a.getTargetDate().toString());
        return m;
    }
    private Map<String, Object> evidenceDto(SustainabilityEvidence e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evidenceCode", e.getEvidenceCode()); m.put("titleEn", e.getTitleEn());
        m.put("moduleArea", e.getModuleArea()); m.put("evidenceType", e.getEvidenceType());
        m.put("validTo", e.getValidTo() == null ? null : e.getValidTo().toString());
        m.put("approvalStatus", e.getApprovalStatus());
        return m;
    }
}
