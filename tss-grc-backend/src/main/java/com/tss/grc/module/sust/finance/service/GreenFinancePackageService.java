package com.tss.grc.module.sust.finance.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import com.tss.grc.module.platform.export.ExportNaming;
import com.tss.grc.module.platform.export.PdfFonts;
import com.tss.grc.module.platform.export.PdfTheme;
import com.tss.grc.module.platform.export.XlsxStyler;
import com.tss.grc.module.sust.finance.entity.AllocationReport;
import com.tss.grc.module.sust.finance.entity.GreenInstrument;
import com.tss.grc.module.sust.finance.entity.GreenProject;
import com.tss.grc.module.sust.finance.entity.InstrumentAllocation;
import com.tss.grc.module.sust.finance.repository.AllocationReportRepository;
import com.tss.grc.module.sust.finance.repository.GreenInstrumentRepository;
import com.tss.grc.module.sust.finance.repository.GreenProjectRepository;
import com.tss.grc.module.sust.finance.repository.InstrumentAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Green-Finance uplift G2 (plan §6.7 for green finance / REG-7) — the investor/regulator
 * evidence ZIP for the green-sukuk register: branded {@code README.pdf} (PdfTheme +
 * tenant BrandTheme) + {@code Instruments.xlsx} + {@code Use_of_Proceeds.xlsx} +
 * {@code Impact_Reports.xlsx} + {@code manifest.json} with SHA-256 of every entry.
 * Mirrors RegulatorPackageService; the download is audit-logged by the export interceptor.
 */
@Service
@RequiredArgsConstructor
public class GreenFinancePackageService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GreenInstrumentRepository instruments;
    private final GreenProjectRepository projects;
    private final InstrumentAllocationRepository allocations;
    private final AllocationReportRepository reports;
    private final com.tss.grc.module.sust.finance.repository.IfrsRequirementRepository ifrs;
    private final BrandThemeService brandThemeService;

    public record Package(byte[] bytes, String filename) {}

    @Transactional(readOnly = true)
    public Package generate(String generatedBy) {
        Long org = TenantContext.requireOrganisationId();
        BrandThemeService.ResolvedBrand brand = brandThemeService.resolve();
        Color brandColor = brand.primaryColor();
        LocalDateTime now = LocalDateTime.now();

        List<GreenInstrument> instr = instruments.findByOrganisationIdAndIsDeletedFalseOrderByInstrumentCodeAsc(org);
        List<GreenProject> projs = projects.findByOrganisationIdAndIsDeletedFalseOrderByProjectCodeAsc(org);
        Map<Long, String> projCode = new java.util.HashMap<>();
        projs.forEach(p -> projCode.put(p.getId(), p.getProjectCode()));
        Map<Long, String> instrCode = new java.util.HashMap<>();
        instr.forEach(i -> instrCode.put(i.getId(), i.getInstrumentCode()));

        byte[] instrumentsXlsx = writeXlsx(brandColor, "Instruments",
            new String[]{"Code", "Type", "Name (EN)", "Name (AR)", "Amount (SAR mn)", "Issuance Costs",
                "Shariah Structure", "Shariah Ref", "SPO Reviewer", "Status"},
            instr, i -> new String[]{
                i.getInstrumentCode(), i.getInstrumentType(), nz(i.getNameEn()), nz(i.getNameAr()),
                str(i.getAmountSarMn()), str(i.getIssuanceCostsSarMn()),
                nz(i.getShariahStructure()), nz(i.getShariahApprovalRef()), nz(i.getExternalReviewer()),
                i.getStatus()});

        List<InstrumentAllocation> allAllocs = instr.stream()
            .flatMap(i -> allocations
                .findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByAllocationDateAsc(org, i.getId())
                .stream())
            .toList();
        byte[] uopXlsx = writeXlsx(brandColor, "Use of Proceeds",
            new String[]{"Instrument", "Project", "Amount (SAR mn)", "Date", "Financing Type", "Status"},
            allAllocs, a -> new String[]{
                instrCode.getOrDefault(a.getInstrumentId(), String.valueOf(a.getInstrumentId())),
                projCode.getOrDefault(a.getProjectId(), String.valueOf(a.getProjectId())),
                str(a.getAmountSarMn()),
                a.getAllocationDate() == null ? "" : a.getAllocationDate().toString(),
                a.getFinancingType(), a.getStatus()});

        List<AllocationReport> reps = reports.findByOrganisationIdAndIsDeletedFalseOrderByPeriodYearDesc(org);
        byte[] impactXlsx = writeXlsx(brandColor, "Impact Reports",
            new String[]{"Instrument", "Period", "Type", "Allocated", "Unallocated", "Allocation %",
                "Refinancing %", "tCO2e Avoided/yr", "MWh Renewable/yr", "Water m3/yr",
                "Intensity tCO2e/SARmn", "Status"},
            reps, r -> new String[]{
                instrCode.getOrDefault(r.getInstrumentId(), String.valueOf(r.getInstrumentId())),
                String.valueOf(r.getPeriodYear()), r.getReportType(),
                str(r.getAllocatedSarMn()), str(r.getUnallocatedSarMn()), str(r.getAllocationRatioPct()),
                str(r.getRefinancingSharePct()), str(r.getTco2eAvoidedYr()), str(r.getMwhRenewableYr()),
                str(r.getWaterM3SavedYr()), str(r.getIntensityTco2ePerSarMn()), r.getStatus()});

        // V293 (plan §5.4) — the S1/S2 gap sheet is part of the SOCPA-readiness pack.
        byte[] ifrsXlsx = writeXlsx(brandColor, "IFRS S1-S2 Readiness",
            new String[]{"Code", "Standard", "Pillar", "Requirement (EN)", "Status", "Coverage Ref", "Owner"},
            ifrs.findByOrganisationIdAndIsDeletedFalseOrderByReqCodeAsc(org),
            r -> new String[]{r.getReqCode(), r.getStandard(), r.getPillar(), nz(r.getTitleEn()),
                r.getStatus(), nz(r.getCoverageRef()), nz(r.getOwnerName())});

        byte[] readme = readmePdf(brand, brandColor, now, generatedBy, instr.size(), allAllocs.size(), reps.size());

        String[] names = {"README.pdf", "Instruments.xlsx", "Use_of_Proceeds.xlsx", "Impact_Reports.xlsx",
            "IFRS_S1_S2_Readiness.xlsx"};
        byte[][] blobs = {readme, instrumentsXlsx, uopXlsx, impactXlsx, ifrsXlsx};

        StringBuilder manifest = new StringBuilder();
        manifest.append("{\n  \"package\": \"GreenFinanceEvidence\",\n")
            .append("  \"organisationId\": ").append(org).append(",\n")
            .append("  \"generatedAt\": \"").append(now.format(TS)).append("\",\n")
            .append("  \"generatedBy\": \"").append(generatedBy == null ? "system" : generatedBy).append("\",\n")
            .append("  \"files\": [\n");
        for (int i = 0; i < names.length; i++) {
            manifest.append("    { \"name\": \"").append(names[i]).append("\", \"bytes\": ")
                .append(blobs[i].length).append(", \"sha256\": \"").append(sha256(blobs[i])).append("\" }")
                .append(i < names.length - 1 ? "," : "").append('\n');
        }
        manifest.append("  ]\n}\n");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < names.length; i++) addEntry(zip, names[i], blobs[i]);
            addEntry(zip, "manifest.json", manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new ExportException("ZIP", "Green-finance package generation failed", e);
        }
        return new Package(out.toByteArray(), ExportNaming.build("GreenFinance", "EvidencePackage", "en", "zip"));
    }

    // ── builders ────────────────────────────────────────────────────────

    private byte[] readmePdf(BrandThemeService.ResolvedBrand brand, Color brandColor, LocalDateTime now,
                             String generatedBy, int nInstr, int nAlloc, int nReports) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(PdfTheme.professionalFooter("Green Finance Evidence Package", "Confidential"));
            doc.open();

            PdfTheme.Cover cv = new PdfTheme.Cover();
            cv.eyebrow = "Green Finance Evidence Package";
            cv.eyebrowAr = "حزمة أدلة التمويل الأخضر";
            cv.titleEn = "Green Sukuk — Allocation & Impact";
            cv.titleAr = "الصكوك الخضراء — التخصيص والأثر";
            cv.orgNameEn = brand.orgNameEn();
            cv.orgNameAr = brand.orgNameAr();
            cv.brand = brandColor;
            cv.classification = "Confidential";
            cv.generatedAt = now;
            PdfTheme.coverHero(doc, cv);

            PdfTheme.kpiStrip(doc, List.of(
                new PdfTheme.Kpi("Instruments", String.valueOf(nInstr), "Green register"),
                new PdfTheme.Kpi("Allocations", String.valueOf(nAlloc), "Use of proceeds"),
                new PdfTheme.Kpi("Reports", String.valueOf(nReports), "Allocation + impact")
            ));

            var meta = PdfTheme.metadataGrid();
            PdfTheme.addMetaCell(meta, "Generated by", generatedBy == null ? "system" : generatedBy);
            PdfTheme.addMetaCell(meta, "Generated (UTC)", now.format(TS));
            PdfTheme.addMetaCell(meta, "Standard", "ICMA GBP / Green Sukuk practice");
            PdfTheme.addMetaCell(meta, "Integrity", "manifest.json (SHA-256 per file)");
            doc.add(meta);

            PdfTheme.sectionDivider(doc, "Package contents", "محتويات الحزمة", brandColor);
            doc.add(new Paragraph("Instruments.xlsx — the green/sustainable instrument register incl. Shariah structure and SPO.", PdfFonts.body(11)));
            doc.add(new Paragraph("Use_of_Proceeds.xlsx — the allocation ledger (instrument -> project, maker-checker states).", PdfFonts.body(11)));
            doc.add(new Paragraph("Impact_Reports.xlsx — frozen annual allocation/impact snapshots (plan §4.2 figures).", PdfFonts.body(11)));
            doc.add(new Paragraph("manifest.json — SHA-256 checksum of every file above.", PdfFonts.body(11)));

            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new ExportException("PDF", "Green-finance README failed", e);
        }
        return baos.toByteArray();
    }

    private <T> byte[] writeXlsx(Color brand, String sheetName, String[] headers,
                                 List<T> rows, Function<T, String[]> mapper) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle hdr = XlsxStyler.headerStyle(wb, brand);
            Row h = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(hdr);
            }
            int r = 1;
            for (T item : rows) {
                String[] vals = mapper.apply(item);
                Row row = sheet.createRow(r++);
                for (int c = 0; c < vals.length; c++) row.createCell(c).setCellValue(vals[c] == null ? "" : vals[c]);
            }
            XlsxStyler.freezeAndFilter(sheet, headers.length, Math.max(1, r - 1));
            for (int c = 0; c < headers.length; c++) {
                if (XlsxStyler.isStatusHeader(headers[c])) XlsxStyler.statusColors(sheet, c, 1, Math.max(1, r - 1));
            }
            XlsxStyler.autoSize(sheet, headers.length);
            wb.write(baos);
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            throw new ExportException("XLSX", "Green-finance sheet failed: " + sheetName, e);
        }
    }

    private static void addEntry(ZipOutputStream zip, String name, byte[] bytes) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new ExportException("HASH", "SHA-256 unavailable", e);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String str(Object o) { return o == null ? "" : o.toString(); }
}
