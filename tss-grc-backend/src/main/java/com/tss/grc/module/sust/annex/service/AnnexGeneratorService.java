package com.tss.grc.module.sust.annex.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
import com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository;
import com.tss.grc.module.sust.cma.entity.CmaDisclosure;
import com.tss.grc.module.sust.cma.repository.CmaDisclosureRepository;
import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
import com.tss.grc.module.sust.ghg.service.GhgEmissionRecordService;
import com.tss.grc.module.sust.sgi.entity.SgiCommitment;
import com.tss.grc.module.sust.sgi.repository.SgiCommitmentRepository;
import com.tss.grc.module.sust.tcfd.entity.TcfdItem;
import com.tss.grc.module.sust.tcfd.repository.TcfdItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Composes the bilingual Sustainability Annex content from the data
 * already living in the platform — GHG inventory (V56), Climate Risks
 * (V55), plus the static CMA / SGI / TCFD seeds that live in the
 * frontend lib today (these will be promoted to real entities in
 * follow-up slices).
 *
 * <p>Three artefacts produced per generation:
 * <ul>
 *   <li>{@code markdown} — the human-reviewable annex (CMA-quote-style)</li>
 *   <li>{@code html}     — same content wrapped in minimal print-ready HTML</li>
 *   <li>{@code json}     — machine-readable export for downstream systems</li>
 * </ul>
 *
 * <p>All section headings appear in English then Arabic on the same
 * line ({@code G / SUSTAINABILITY ANNEX — ANNUAL REPORT — ملحق الاستدامة …})
 * to match the spec's sample annex format. Body paragraphs include
 * AR/EN counterparts where the underlying data has them (e.g. climate
 * risks have {@code titleEn} + {@code titleAr}).
 */
@Service
@RequiredArgsConstructor
public class AnnexGeneratorService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final GhgEmissionRecordRepository      ghgRepo;
    private final GhgEmissionRecordService         ghgService;     // for aggregate + Rule-1 flag
    private final ClimateRiskOpportunityRepository climateRepo;
    // S-I — SGI / CMA / TCFD sections now read live registers (were hardcoded).
    private final SgiCommitmentRepository          sgiRepo;
    private final CmaDisclosureRepository          cmaRepo;
    private final TcfdItemRepository               tcfdRepo;

    /** Result bundle returned by {@link #generate(short, String, String)}. */
    public record AnnexContent(String markdown, String html, String json) {}

    public AnnexContent generate(short year, String orgEn, String orgAr) {
        Long org = TenantContext.getCurrentTenant();

        // Pull data the generator owns directly.
        var ghgAgg   = ghgService.aggregate(year);
        var ghgYoy   = ghgService.yoy(year);
        var ghgRows  = ghgRepo.findByOrganisationIdAndReportingYear(org, year);
        var climates = climateRepo.findByOrganisationIdOrderByRiskScoreDescIdDesc(org,
            PageRequest.of(0, 50)).getContent();
        // S-I — live SGI / CMA / TCFD registers (replace the hardcoded arrays).
        var sgi  = sgiRepo.findByOrganisationIdOrderByTargetYearAscCommitmentCodeAsc(org, PageRequest.of(0, 100)).getContent();
        var cma  = cmaRepo.findByOrganisationIdAndReportingYear(org, year);
        var tcfd = tcfdRepo.findByOrganisationIdAndReportingYear(org, year);

        String md = buildMarkdown(year, orgEn, orgAr, ghgAgg, ghgYoy, ghgRows, climates, sgi, cma, tcfd);
        String html = wrapHtml(md, orgEn, orgAr, year);
        String json = buildJson(year, orgEn, orgAr, ghgAgg, ghgYoy, ghgRows, climates, sgi, cma, tcfd);

        return new AnnexContent(md, html, json);
    }

    // ── Markdown body ────────────────────────────────────────────────
    private String buildMarkdown(short year, String orgEn, String orgAr,
                                  Map<String, Object> ghgAgg, Map<String, Object> ghgYoy,
                                  List<GhgEmissionRecord> ghgRows,
                                  List<ClimateRiskOpportunity> climates,
                                  List<SgiCommitment> sgi, List<CmaDisclosure> cma, List<TcfdItem> tcfd) {
        StringBuilder b = new StringBuilder();
        // Header — matches the spec's sample annex shape exactly.
        b.append("G / SUSTAINABILITY ANNEX — ANNUAL REPORT\n");
        b.append("ملحق الاستدامة والحوكمة البيئية — التقرير السنوي\n\n");
        b.append(String.format("Reporting entity: %s%n", orgEn));
        b.append(String.format("الجهة المُعِدّة  : %s%n", orgAr == null ? orgEn : orgAr));
        b.append(String.format("Reporting year: %d%n", year));
        b.append(String.format("Generated     : %sT00:00:00Z%n%n", LocalDate.now().format(ISO)));

        // ─── 1. GHG ───────────────────────────────────────────────
        b.append("1. GHG EMISSIONS · انبعاثات الغازات الدفيئة\n");
        BigDecimal s1 = bd(ghgAgg.get("scope1Total"));
        BigDecimal s2 = bd(ghgAgg.get("scope2Total"));
        BigDecimal s3 = bd(ghgAgg.get("scope3Total"));
        BigDecimal tot = bd(ghgAgg.get("grandTotal"));
        BigDecimal cov = bd(ghgAgg.get("scope3Coverage"));
        boolean s3Verified = ghgRows.stream().filter(r -> r.getScope() == 3)
            .allMatch(GhgEmissionRecord::getVerified);
        boolean s1Verified = ghgRows.stream().filter(r -> r.getScope() == 1)
            .allMatch(GhgEmissionRecord::getVerified);
        boolean s2Verified = ghgRows.stream().filter(r -> r.getScope() == 2)
            .allMatch(GhgEmissionRecord::getVerified);
        b.append(String.format("   Scope 1: %s tCO2e (verified: %s)%n",
            fmt(s1), s1Verified ? "YES" : "no"));
        b.append(String.format("   Scope 2: %s tCO2e (verified: %s)%n",
            fmt(s2), s2Verified ? "YES" : "no"));
        b.append(String.format("   Scope 3: %s tCO2e (coverage %s%%, verified: %s)%n",
            fmt(s3), cov.setScale(0, java.math.RoundingMode.HALF_UP), s3Verified ? "YES" : "no"));
        b.append(String.format("   Total  : %s tCO2e%n", fmt(tot)));
        b.append(String.format("   YoY    : Scope 1 %s, Scope 2 %s, Scope 3 %s%n%n",
            yoyChip(ghgYoy.get("scope1YoY")),
            yoyChip(ghgYoy.get("scope2YoY")),
            yoyChip(ghgYoy.get("scope3YoY"))));

        // ─── 2. SGI — live sgi_commitment register (V59).
        b.append("2. SAUDI GREEN INITIATIVE COMMITMENTS · التزامات مبادرة السعودية الخضراء\n");
        appendSgi(b, sgi);
        b.append('\n');

        // ─── 3. CMA — live cma_disclosure register for the year (V60).
        b.append("3. CMA ARTICLES 91-94 DISCLOSURE · إفصاح المواد 91-94 من لائحة الحوكمة\n");
        appendCma(b, cma);
        b.append('\n');

        // ─── 4. TCFD — live tcfd_item register for the year (V61).
        b.append("4. TCFD ALIGNMENT · المحاذاة مع TCFD\n");
        appendTcfd(b, tcfd);
        b.append('\n');

        // ─── 5. CLIMATE RISKS — pulled from the V55 entity (live data)
        b.append("5. CLIMATE RISKS & OPPORTUNITIES · المخاطر والفرص المناخية\n");
        if (climates.isEmpty()) {
            b.append("   (No climate risks registered for this period.)\n");
        } else {
            for (ClimateRiskOpportunity c : climates) {
                b.append(String.format("   [%-12s] %-6s score=%2d  %s%n",
                    c.getCategory(), c.getKind(), c.getRiskScore(), c.getTitleEn()));
                if (c.getTitleAr() != null && !c.getTitleAr().isBlank()) {
                    b.append(String.format("                                    %s%n", c.getTitleAr()));
                }
                b.append(String.format("                                    Status: %s · Horizon: %s · Owner: %s%n",
                    c.getStatus(), c.getTimeHorizon(),
                    c.getOwnerName() == null ? "—" : c.getOwnerName()));
            }
        }
        b.append('\n');

        // Footer — methodology note + signature lines
        b.append("Methodology: GHG Protocol Corporate Standard (Scope 1/2) + Scope 3 Standard.\n");
        b.append("CMA Article references: Corporate Governance Regulations issued by Capital Market Authority.\n");
        b.append("TCFD recommendations: Final Report — Recommendations of the TCFD (June 2017).\n");
        return b.toString();
    }

    private void appendSgi(StringBuilder b, List<SgiCommitment> sgi) {
        // S-I — live sgi_commitment register (V59), no longer a hardcoded array.
        if (sgi.isEmpty()) { b.append("   (No SGI commitments registered.)\n"); return; }
        for (SgiCommitment c : sgi) {
            b.append(String.format("   [%-9s] %s%n", safe(c.getSector()), safe(c.getTitleEn())));
            if (c.getTitleAr() != null && !c.getTitleAr().isBlank())
                b.append(String.format("               %s%n", c.getTitleAr()));
            b.append(String.format("               Progress: %s / %s %s — %s (target %s)%n",
                num(c.getCurrentValue()), num(c.getTargetValue()), safe(c.getUnit()),
                safe(c.getStatus()), c.getTargetYear() == null ? "—" : c.getTargetYear()));
        }
    }

    private void appendCma(StringBuilder b, List<CmaDisclosure> cma) {
        // S-I — live cma_disclosure register for the year (V60).
        if (cma.isEmpty()) { b.append("   (No CMA disclosures registered for this year.)\n"); return; }
        for (CmaDisclosure d : cma) {
            b.append(String.format("   Art. %-8s [%-16s] %s / %s%n",
                d.getDisplayCode(), safe(d.getDisclosureStatus()),
                safe(d.getTitleEn()), d.getTitleAr() == null ? "" : d.getTitleAr()));
            String ev = d.getEvidenceRef() != null ? d.getEvidenceRef()
                      : (d.getReportSectionReference() != null ? d.getReportSectionReference() : "—");
            b.append(String.format("     evidence: %s%n", ev));
        }
    }

    private void appendTcfd(StringBuilder b, List<TcfdItem> tcfd) {
        // S-I — live tcfd_item register for the year (V61).
        if (tcfd.isEmpty()) { b.append("   (No TCFD items registered for this year.)\n"); return; }
        for (TcfdItem t : tcfd) {
            b.append(String.format("   [%-15s] %-3s [%-16s] %s%n",
                safe(t.getPillar()), safe(t.getRecommendationCode()),
                safe(t.getDisclosureStatus()), safe(t.getTitleEn())));
        }
    }

    // ── JSON body ───────────────────────────────────────────────────
    private String buildJson(short year, String orgEn, String orgAr,
                              Map<String, Object> ghgAgg, Map<String, Object> ghgYoy,
                              List<GhgEmissionRecord> ghgRows,
                              List<ClimateRiskOpportunity> climates,
                              List<SgiCommitment> sgi, List<CmaDisclosure> cma, List<TcfdItem> tcfd) {
        // Hand-built minimal JSON — avoids dragging in Jackson serialisation
        // configuration; the produced string is valid JSON for downstream
        // consumers that just want to ingest the figures.
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append(String.format("  \"reportingEntityEn\": %s,%n", j(orgEn)));
        b.append(String.format("  \"reportingEntityAr\": %s,%n", j(orgAr == null ? orgEn : orgAr)));
        b.append(String.format("  \"reportingYear\": %d,%n", year));
        b.append(String.format("  \"generatedAt\": %s,%n", j(LocalDate.now().toString() + "T00:00:00Z")));
        b.append("  \"ghg\": {\n");
        b.append(String.format("    \"scope1\": %s,%n", fmt(bd(ghgAgg.get("scope1Total")))));
        b.append(String.format("    \"scope2\": %s,%n", fmt(bd(ghgAgg.get("scope2Total")))));
        b.append(String.format("    \"scope3\": %s,%n", fmt(bd(ghgAgg.get("scope3Total")))));
        b.append(String.format("    \"total\": %s,%n",  fmt(bd(ghgAgg.get("grandTotal")))));
        b.append(String.format("    \"scope3Coverage\": %s,%n", bd(ghgAgg.get("scope3Coverage"))));
        b.append(String.format("    \"scope3PartialFlag\": %s,%n", ghgAgg.get("scope3PartialFlag")));
        b.append(String.format("    \"recordCount\": %d,%n", ghgRows.size()));
        b.append(String.format("    \"yoy\": { \"scope1\": %s, \"scope2\": %s, \"scope3\": %s, \"total\": %s }%n",
            bd(ghgYoy.get("scope1YoY")), bd(ghgYoy.get("scope2YoY")),
            bd(ghgYoy.get("scope3YoY")), bd(ghgYoy.get("totalYoY"))));
        b.append("  },\n");
        b.append("  \"climateRiskCount\": ").append(climates.size()).append(",\n");
        b.append("  \"cmaArticleCount\": ").append(cma.size()).append(",\n");
        b.append("  \"tcfdItemCount\": ").append(tcfd.size()).append(",\n");
        b.append("  \"sgiCommitmentCount\": ").append(sgi.size()).append("\n");
        b.append("}\n");
        return b.toString();
    }

    // ── HTML wrapper ────────────────────────────────────────────────
    private String wrapHtml(String md, String orgEn, String orgAr, short year) {
        // Minimal print-ready HTML. <pre> preserves the markdown's
        // monospace alignment so the EN/AR pairs stay column-aligned.
        // No <link>s or external assets — the file is fully self-contained
        // for download / e-mail.
        String safe = md.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<!doctype html>\n<html lang=\"ar\" dir=\"rtl\"><head>\n"
            + "<meta charset=\"utf-8\">\n"
            + "<title>Sustainability Annex " + year + " — " + orgEn + "</title>\n"
            + "<style>body{font:13px/1.55 -apple-system,Segoe UI,Tahoma,Arial,sans-serif;"
            + "margin:24px;color:#15201D;background:#FAF8F4} "
            + "pre{font:12px/1.55 ui-monospace,Consolas,monospace;white-space:pre-wrap;"
            + "color:#15201D;background:#fff;border:1px solid #E5DFD2;padding:24px;border-radius:8px}</style>\n"
            + "</head><body><pre>" + safe + "</pre></body></html>\n";
    }

    // ── tiny helpers ────────────────────────────────────────────────
    private static BigDecimal bd(Object o) {
        return o instanceof BigDecimal v ? v : BigDecimal.ZERO;
    }
    private static String fmt(BigDecimal v) {
        return v.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
    private static String safe(String s) { return s == null || s.isBlank() ? "—" : s; }
    private static String num(BigDecimal v) { return v == null ? "—" : v.stripTrailingZeros().toPlainString(); }
    /** Format a YoY value as a signed percentage, "+/-N.N%". */
    private static String yoyChip(Object v) {
        if (!(v instanceof BigDecimal d) || d.signum() == 0) return "0%";
        return (d.signum() > 0 ? "+" : "") + d.setScale(1, java.math.RoundingMode.HALF_UP) + "%";
    }
    /** Minimal JSON-string encoder — quotes + escapes the few chars we need. */
    private static String j(String s) {
        if (s == null) return "null";
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }
}
