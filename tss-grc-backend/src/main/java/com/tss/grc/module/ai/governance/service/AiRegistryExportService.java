package com.tss.grc.module.ai.governance.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.repository.MlModelAuditRepository;
import com.tss.grc.module.ai.governance.repository.MlModelRepository;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.reporting.service.PdfFonts;
import com.tss.grc.module.reporting.service.PdfTheme;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regulator-ready export of the AI/ML Model Registry — the board / examiner
 * pack. Two outputs from one read:
 *
 * <ul>
 *   <li><b>XLSX</b> — a "Posture" sheet (governance KPIs + gap counts) and a
 *       "Models" sheet (one row per model, full tri-regulator attributes).</li>
 *   <li><b>PDF</b> — branded cover + KPI strip + the models table, using the
 *       shared {@link PdfTheme}/{@link PdfFonts} so Arabic renders and the
 *       pack matches the BCP plan / board pack house style.</li>
 * </ul>
 *
 * <p>"Signed-off AIA" is resolved in one batch query (model ids with a passed
 * AIA audit); the rest derives from the model row, so the export is a single
 * cheap read regardless of registry size.
 */
@Service
@RequiredArgsConstructor
public class AiRegistryExportService {

    private static final Set<String> ELEVATED = Set.of("CRITICAL", "HIGH");
    private static final Set<String> PROD_OR_APPROVED = Set.of("APPROVED_FOR_PROD", "IN_PRODUCTION", "MONITORING");
    private static final Set<String> PROD_STAGES = Set.of("IN_PRODUCTION", "MONITORING");
    private static final Set<String> CROSS_BORDER = Set.of("GCC", "GLOBAL");

    private final MlModelRepository repo;
    private final MlModelAuditRepository auditRepo;
    private final OrganisationRepository orgs;

    /** Posture rollup, mirroring the registry KPI tiles. */
    public record Posture(int total, int inProduction, int highOrCritical,
                          int missingFairnessAudit, int missingHumanOversight,
                          int crossBorderNoBasis, int missingSignedOffAia,
                          int euAiActHighRisk, int sdaiaCompliant, int samaCompliant) {}

    /** Optional export filter — mirrors the registry filters + gap drill-downs. */
    public record ExportFilter(String stage, String tier, String domain, String gap, Boolean euHighRisk) {
        public static final ExportFilter NONE = new ExportFilter(null, null, null, null, null);
        boolean isEmpty() {
            return blank(stage) && blank(tier) && blank(domain) && blank(gap) && euHighRisk == null;
        }
        private static boolean blank(String s) { return s == null || s.isBlank(); }
    }

    @Transactional(readOnly = true)
    public byte[] export(String format) {
        return export(format, ExportFilter.NONE);
    }

    @Transactional(readOnly = true)
    public byte[] export(String format, ExportFilter filter) {
        Long orgId = TenantContext.getCurrentTenant();
        List<MlModel> all = repo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId);
        Set<Long> aiaSignedOff = new HashSet<>(auditRepo.findModelIdsWithSignedOffAia(orgId));
        List<MlModel> models = applyFilter(all, filter, aiaSignedOff);
        Posture p = posture(models, aiaSignedOff);
        Organisation org = orgId == null ? null : orgs.findById(orgId).orElse(null);
        return "PDF".equalsIgnoreCase(format)
                ? toPdf(models, aiaSignedOff, p, org)
                : toXlsx(models, aiaSignedOff, p, org);
    }

    private List<MlModel> applyFilter(List<MlModel> all, ExportFilter f, Set<Long> aia) {
        if (f == null || f.isEmpty()) return all;
        List<MlModel> out = new ArrayList<>();
        for (MlModel m : all) {
            if (notBlank(f.stage()) && !f.stage().equalsIgnoreCase(m.getLifecycleStage())) continue;
            if (notBlank(f.tier()) && !f.tier().equalsIgnoreCase(m.getRiskTier())) continue;
            if (notBlank(f.domain()) && !f.domain().equalsIgnoreCase(m.getBusinessDomain())) continue;
            if (Boolean.TRUE.equals(f.euHighRisk()) && !Boolean.TRUE.equals(m.getIsHighRiskEuAiAct())) continue;
            if (notBlank(f.gap()) && !matchesGap(m, f.gap(), aia)) continue;
            out.add(m);
        }
        return out;
    }

    private boolean matchesGap(MlModel m, String gap, Set<Long> aia) {
        boolean elevated = ELEVATED.contains(m.getRiskTier());
        boolean prodish = PROD_OR_APPROVED.contains(m.getLifecycleStage());
        return switch (gap.toUpperCase()) {
            case "OVERSIGHT" -> elevated && prodish && (m.getHumanOversight() == null
                    || m.getHumanOversight().isBlank() || "NONE".equals(m.getHumanOversight()));
            case "CROSS_BORDER" -> m.getDataResidency() != null && CROSS_BORDER.contains(m.getDataResidency())
                    && (m.getCrossBorderTransferBasis() == null || m.getCrossBorderTransferBasis().isBlank()
                        || "NOT_ASSESSED".equals(m.getCrossBorderTransferBasis()));
            case "AIA" -> elevated && prodish && !aia.contains(m.getId());
            case "FAIRNESS" -> elevated && prodish && !Boolean.TRUE.equals(m.getHasFairnessAudit());
            default -> true;
        };
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private Posture posture(List<MlModel> models, Set<Long> aiaSignedOff) {
        int total = models.size(), inProd = 0, hiCrit = 0, noFair = 0, noOvr = 0, xb = 0, noAia = 0, eu = 0, sdaia = 0, sama = 0;
        for (MlModel m : models) {
            boolean elevated = ELEVATED.contains(m.getRiskTier());
            boolean prodish = PROD_OR_APPROVED.contains(m.getLifecycleStage());
            if (PROD_STAGES.contains(m.getLifecycleStage())) inProd++;
            if (elevated) hiCrit++;
            if (elevated && prodish && !Boolean.TRUE.equals(m.getHasFairnessAudit())) noFair++;
            if (elevated && prodish && (m.getHumanOversight() == null || m.getHumanOversight().isBlank()
                    || "NONE".equals(m.getHumanOversight()))) noOvr++;
            if (m.getDataResidency() != null && CROSS_BORDER.contains(m.getDataResidency())
                    && (m.getCrossBorderTransferBasis() == null || m.getCrossBorderTransferBasis().isBlank()
                        || "NOT_ASSESSED".equals(m.getCrossBorderTransferBasis()))) xb++;
            if (elevated && prodish && !aiaSignedOff.contains(m.getId())) noAia++;
            if (Boolean.TRUE.equals(m.getIsHighRiskEuAiAct())) eu++;
            if ("COMPLIANT".equals(m.getSdaiaAiEthicsCompliance())) sdaia++;
            if ("COMPLIANT".equals(m.getSamaAiPrinciplesCompliance())) sama++;
        }
        return new Posture(total, inProd, hiCrit, noFair, noOvr, xb, noAia, eu, sdaia, sama);
    }

    // ─── XLSX ──────────────────────────────────────────────────────────

    private static final String[] COLS = {
            "Model code", "Title (EN)", "Domain", "Risk tier", "Lifecycle stage",
            "SDAIA", "SAMA", "EU AI Act high-risk", "Human oversight", "Data residency",
            "Cross-border transfer basis", "Fairness audit", "Signed-off AIA",
            "Version", "Hosting", "Production date"
    };

    private byte[] toXlsx(List<MlModel> models, Set<Long> aia, Posture p, Organisation org) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle header = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font hf = wb.createFont();
            hf.setBold(true);
            header.setFont(hf);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Posture sheet
            Sheet ps = wb.createSheet("Posture");
            int r = 0;
            r = kv(ps, r, header, "AI/ML Model Registry — Governance Posture", org == null ? "" : org.getNameEn());
            r = kv(ps, r, header, "Generated at", LocalDateTime.now().toString());
            r++;
            r = kv(ps, r, header, "Total models", String.valueOf(p.total()));
            r = kv(ps, r, header, "In production", String.valueOf(p.inProduction()));
            r = kv(ps, r, header, "Critical / High", String.valueOf(p.highOrCritical()));
            r = kv(ps, r, header, "EU AI Act high-risk", String.valueOf(p.euAiActHighRisk()));
            r = kv(ps, r, header, "SDAIA compliant", String.valueOf(p.sdaiaCompliant()));
            r = kv(ps, r, header, "SAMA compliant", String.valueOf(p.samaCompliant()));
            r++;
            r = kv(ps, r, header, "GAP — No fairness audit (high+)", String.valueOf(p.missingFairnessAudit()));
            r = kv(ps, r, header, "GAP — No human oversight (high+)", String.valueOf(p.missingHumanOversight()));
            r = kv(ps, r, header, "GAP — Cross-border, no PDPL basis", String.valueOf(p.crossBorderNoBasis()));
            r = kv(ps, r, header, "GAP — No signed-off AIA (high+)", String.valueOf(p.missingSignedOffAia()));
            ps.autoSizeColumn(0);
            ps.autoSizeColumn(1);

            // Models sheet
            Sheet ms = wb.createSheet("Models");
            Row h = ms.createRow(0);
            for (int i = 0; i < COLS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(COLS[i]);
                c.setCellStyle(header);
            }
            int row = 1;
            for (MlModel m : models) {
                Row dr = ms.createRow(row++);
                int i = 0;
                dr.createCell(i++).setCellValue(s(m.getModelCode()));
                dr.createCell(i++).setCellValue(s(m.getTitleEn()));
                dr.createCell(i++).setCellValue(s(m.getBusinessDomain()));
                dr.createCell(i++).setCellValue(s(m.getRiskTier()));
                dr.createCell(i++).setCellValue(s(m.getLifecycleStage()));
                dr.createCell(i++).setCellValue(s(m.getSdaiaAiEthicsCompliance()));
                dr.createCell(i++).setCellValue(s(m.getSamaAiPrinciplesCompliance()));
                dr.createCell(i++).setCellValue(Boolean.TRUE.equals(m.getIsHighRiskEuAiAct()) ? "Yes" : "No");
                dr.createCell(i++).setCellValue(s(m.getHumanOversight()));
                dr.createCell(i++).setCellValue(s(m.getDataResidency()));
                dr.createCell(i++).setCellValue(s(m.getCrossBorderTransferBasis()));
                dr.createCell(i++).setCellValue(Boolean.TRUE.equals(m.getHasFairnessAudit()) ? "Yes" : "No");
                dr.createCell(i++).setCellValue(aia.contains(m.getId()) ? "Yes" : "No");
                dr.createCell(i++).setCellValue(s(m.getVersionLabel()));
                dr.createCell(i++).setCellValue(s(m.getHosting()));
                dr.createCell(i).setCellValue(m.getProductionDate() == null ? "" : m.getProductionDate().toString());
            }
            for (int i = 0; i < COLS.length; i++) ms.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ExportException("XLSX", "AI registry XLSX export failed", ex);
        }
    }

    private static int kv(Sheet s, int rowIdx, CellStyle header, String k, String v) {
        Row r = s.createRow(rowIdx);
        Cell c0 = r.createCell(0);
        c0.setCellValue(k);
        c0.setCellStyle(header);
        r.createCell(1).setCellValue(v == null ? "" : v);
        return rowIdx + 1;
    }

    // ─── PDF ───────────────────────────────────────────────────────────

    private byte[] toPdf(List<MlModel> models, Set<Long> aia, Posture p, Organisation org) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 56, 56);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(PdfTheme.professionalFooter("AI/ML Model Registry", "Confidential"));
            doc.open();

            PdfTheme.Cover cv = new PdfTheme.Cover();
            cv.eyebrow = "AI Governance Pack";
            cv.eyebrowAr = "حزمة حوكمة الذكاء الاصطناعي";
            cv.titleEn = "AI/ML Model Registry";
            cv.subtitle = "SDAIA · SAMA · PDPL · EU AI Act";
            cv.orgNameEn = org == null ? null : org.getNameEn();
            cv.orgNameAr = org == null ? null : org.getNameAr();
            cv.classification = "Confidential";
            cv.generatedAt = LocalDateTime.now();
            PdfTheme.coverHero(doc, cv);

            List<PdfTheme.Kpi> kpis = new ArrayList<>();
            kpis.add(new PdfTheme.Kpi("Total models", String.valueOf(p.total()), ""));
            kpis.add(new PdfTheme.Kpi("Critical / High", String.valueOf(p.highOrCritical()), ""));
            kpis.add(new PdfTheme.Kpi("EU high-risk", String.valueOf(p.euAiActHighRisk()), ""));
            kpis.add(new PdfTheme.Kpi("No signed-off AIA", String.valueOf(p.missingSignedOffAia()), ""));
            PdfTheme.kpiStrip(doc, kpis);

            List<PdfTheme.Kpi> gaps = new ArrayList<>();
            gaps.add(new PdfTheme.Kpi("No fairness audit", String.valueOf(p.missingFairnessAudit()), ""));
            gaps.add(new PdfTheme.Kpi("No human oversight", String.valueOf(p.missingHumanOversight()), ""));
            gaps.add(new PdfTheme.Kpi("Cross-border, no basis", String.valueOf(p.crossBorderNoBasis()), ""));
            gaps.add(new PdfTheme.Kpi("SDAIA compliant", String.valueOf(p.sdaiaCompliant()), ""));
            PdfTheme.kpiStrip(doc, gaps);

            PdfTheme.sectionDivider(doc, "Models (" + models.size() + ")", "النماذج");

            String[] pdfCols = {"Code", "Domain", "Tier", "Stage", "SDAIA", "SAMA",
                    "EU", "Oversight", "Residency", "Fairness", "AIA"};
            PdfPTable t = new PdfPTable(pdfCols.length);
            t.setWidthPercentage(100);
            for (String c : pdfCols) headerCell(t, c);
            for (MlModel m : models) {
                cell(t, s(m.getModelCode()));
                cell(t, s(m.getBusinessDomain()));
                cell(t, s(m.getRiskTier()));
                cell(t, s(m.getLifecycleStage()));
                cell(t, s(m.getSdaiaAiEthicsCompliance()));
                cell(t, s(m.getSamaAiPrinciplesCompliance()));
                cell(t, Boolean.TRUE.equals(m.getIsHighRiskEuAiAct()) ? "High" : "—");
                cell(t, s(m.getHumanOversight()));
                cell(t, s(m.getDataResidency()));
                cell(t, Boolean.TRUE.equals(m.getHasFairnessAudit()) ? "Yes" : "—");
                cell(t, aia.contains(m.getId()) ? "Yes" : "—");
            }
            doc.add(t);

            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ExportException("PDF", "AI registry PDF export failed", ex);
        }
    }

    private static void headerCell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.header(8)));
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setBackgroundColor(PdfFonts.BRAND_PRIMARY);
        c.setBorderColor(PdfFonts.HAIRLINE);
        c.setPaddingTop(5); c.setPaddingBottom(5); c.setPaddingLeft(5); c.setPaddingRight(5);
        t.addCell(c);
    }

    private static void cell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text == null || text.isEmpty() ? "—" : text, PdfFonts.body(8)));
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setPaddingTop(4); c.setPaddingBottom(4); c.setPaddingLeft(5); c.setPaddingRight(5);
        t.addCell(c);
    }

    private static String s(String v) { return v == null ? "" : v; }
}
