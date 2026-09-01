package com.tss.grc.module.reporting.service;

import com.tss.grc.module.platform.export.ExportNaming;
import com.tss.grc.module.platform.export.PdfFonts;
import com.tss.grc.module.platform.export.PdfTheme;
import com.tss.grc.module.platform.export.XlsxStyler;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.Control;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.evidence.service.EvidenceStorageService;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Export P2/P3 — regulator / audit evidence package builder.
 *
 * <p>Generalises the BCM-cycle evidence ZIP ({@code BcmEvidencePackageService}) to a
 * platform-wide, framework-agnostic pack an auditor or regulator can be handed as a
 * single self-describing archive:
 * <ul>
 *   <li>{@code README.pdf}              — branded cover (tenant BrandTheme) with scope,
 *       generation stamp (Hijri + Gregorian), row counts and a contents list.</li>
 *   <li>{@code Requirement_Mapping.xlsx}— every control / requirement × framework × status.</li>
 *   <li>{@code Evidence_Index.xlsx}     — the evidence register (title, type, status,
 *       file, validity window, integrity hash).</li>
 *   <li>{@code Audit_Trail.xlsx}        — recent platform audit-log entries (who/what/when),
 *       including EXPORT events from the export audit trail.</li>
 *   <li>{@code manifest.json}           — SHA-256 of every file so tampering is detectable,
 *       plus organisation / scope / generation metadata.</li>
 * </ul>
 *
 * <p>Workbooks use the shared brand-aware {@link XlsxStyler}; the README uses {@link PdfTheme}.
 * All reads are org-scoped via {@link TenantContext}.
 */
@Service
@RequiredArgsConstructor
public class RegulatorPackageService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RegulatorPackageService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_ROWS   = 5000;   // per data sheet
    private static final int MAX_AUDIT  = 1000;   // audit-trail entries
    /** Total embedded evidence-binary budget — guards against OOM buffering the ZIP in memory. */
    private static final long MAX_EVIDENCE_TOTAL_BYTES = 200L * 1024 * 1024;

    private final ControlRepository controlRepo;
    private final EvidenceItemRepository evidenceRepo;
    private final AuditLogService auditLogService;
    private final OrganisationRepository orgRepo;
    private final BrandThemeService brandThemeService;
    private final EvidenceStorageService evidenceStorage;

    public record Package(byte[] bytes, String filename) {}

    @Transactional(readOnly = true)
    public Package generate(String generatedByName) {
        Long org = TenantContext.requireOrganisationId();
        Organisation o = orgRepo.findById(org).orElse(null);
        BrandThemeService.ResolvedBrand brand = brandThemeService.resolve();
        Color brandColor = brand.primaryColor();
        String orgName = brand.orgNameEn() != null ? brand.orgNameEn()
                       : (o == null ? "Organisation" : o.getNameEn());
        LocalDateTime now = LocalDateTime.now();

        // ── gather data (org-scoped) ────────────────────────────────────────
        List<Control> controls = controlRepo.findByOrganisationId(org, PageRequest.of(0, MAX_ROWS)).getContent();
        List<EvidenceItem> evidence = evidenceRepo.findByOrganisationId(org, PageRequest.of(0, MAX_ROWS)).getContent();
        List<AuditLogEntry> audit = auditLogService.list(null, null, null, 0, MAX_AUDIT).getContent();

        // ── build files ─────────────────────────────────────────────────────
        byte[] mapping = writeXlsx(brandColor, "Requirement Mapping",
            new String[]{"Code", "Name (EN)", "Name (AR)", "Framework", "Domain", "Maturity", "Status", "Owner"},
            controls, c -> new String[]{
                nz(c.getCode()), nz(c.getNameEn()), nz(c.getNameAr()), nz(c.getFramework()),
                nz(c.getDomain()), c.getMaturityLevel() == null ? "" : String.valueOf(c.getMaturityLevel()),
                nz(c.getStatus()), nz(c.getOwner())
            });

        byte[] evidenceIndex = writeXlsx(brandColor, "Evidence Index",
            new String[]{"Title (EN)", "Type", "Source Module", "Status", "File Name",
                "Size (bytes)", "Version", "Valid From", "Valid To", "Integrity Hash"},
            evidence, e -> new String[]{
                nz(e.getTitleEn()), nz(e.getEvidenceType()), nz(e.getSourceModule()), nz(e.getStatus()),
                nz(e.getFileName()), e.getFileSize() == null ? "" : String.valueOf(e.getFileSize()),
                nz(e.getVersion()), e.getValidFrom() == null ? "" : e.getValidFrom().toString(),
                e.getValidTo() == null ? "" : e.getValidTo().toString(), nz(e.getEvidenceHash())
            });

        // F2-326: carry the ROLE the actor held at the time, next to the actor. The unified viewer
        // (AuditLogPage) renders "<user> · <role>" and the BCM evidence package (F2-325) carries both;
        // user_role is co-populated exactly with user_username (0 rows carry one without the other, both
        // databases), so it adds no blank, and it is the field an auditor reads for a segregation-of-duties
        // check (F2-54). A row with no principal blanks the actor rather than fabricating one (F2-213).
        byte[] auditTrail = writeXlsx(brandColor, "Audit Trail",
            new String[]{"Time (UTC)", "Action", "Module", "Entity", "Code", "User", "Role", "Severity", "Message"},
            audit, a -> new String[]{
                a.getCreatedAt() == null ? "" : a.getCreatedAt().format(TS), nz(a.getActionType()),
                nz(a.getSourceModule()), nz(a.getEntityType()), nz(a.getEntityCode()),
                nz(a.getUserUsername()), nz(a.getUserRole()), nz(a.getSeverity()), nz(a.getMessage())
            });

        byte[] readme = readmePdf(orgName, o == null ? null : o.getNameAr(), brand, brandColor, now,
            generatedByName, controls.size(), evidence.size(), audit.size());

        // ── collect core files + the actual evidence-file binaries ──────────
        List<String> names = new ArrayList<>(List.of(
            "README.pdf", "Requirement_Mapping.xlsx", "Evidence_Index.xlsx", "Audit_Trail.xlsx"));
        List<byte[]> blobs = new ArrayList<>(List.of(readme, mapping, evidenceIndex, auditTrail));
        embedEvidenceFiles(evidence, names, blobs);

        // ── assemble ZIP (manifest last — it checksums every other entry) ───
        String manifest = manifestJson(org, orgName, now, generatedByName,
            controls.size(), evidence.size(), audit.size(),
            names.toArray(new String[0]), blobs.toArray(new byte[0][]));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < names.size(); i++) addEntry(zip, names.get(i), blobs.get(i));
            addEntry(zip, "manifest.json", manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new ExportException("ZIP", "Regulator package generation failed", e);
        }

        String filename = ExportNaming.build("Regulator", "EvidencePackage", "en", "zip");
        return new Package(out.toByteArray(), filename);
    }

    // ── XLSX ─────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface RowMapper<T> { String[] map(T row); }

    private static <T> byte[] writeXlsx(Color brand, String sheetName, String[] headers,
                                        List<T> rows, RowMapper<T> mapper) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle hdr = XlsxStyler.headerStyle(wb, brand);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hdr);
            }
            int r = 1;
            for (T row : rows) {
                String[] vals = mapper.map(row);
                Row dataRow = sheet.createRow(r++);
                for (int i = 0; i < headers.length; i++) {
                    dataRow.createCell(i).setCellValue(i < vals.length ? nz(vals[i]) : "");
                }
            }
            XlsxStyler.freezeAndFilter(sheet, headers.length, r - 1);
            for (int i = 0; i < headers.length; i++) {
                if (XlsxStyler.isStatusHeader(headers[i])) XlsxStyler.statusColors(sheet, i, 1, r - 1);
            }
            XlsxStyler.autoSize(sheet, headers.length);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            throw new ExportException("XLSX", "Regulator package sheet failed", e);
        }
    }

    // ── README PDF ─────────────────────────────────────────────────────────

    private byte[] readmePdf(String orgEn, String orgAr, BrandThemeService.ResolvedBrand brand,
                             Color brandColor, LocalDateTime now, String generatedBy,
                             int controls, int evidence, int auditEntries) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(PdfTheme.professionalFooter("Regulator Evidence Package", "Confidential"));
            doc.open();

            PdfTheme.Cover cv = new PdfTheme.Cover();
            cv.eyebrow = "Regulator / Audit Evidence Package";
            cv.eyebrowAr = "حزمة أدلة الجهة التنظيمية / التدقيق";
            cv.titleEn = "Evidence Package";
            cv.titleAr = "حزمة الأدلة";
            cv.subtitle = brand.taglineEn();
            cv.subtitleAr = brand.taglineAr();
            cv.orgNameEn = orgEn;
            cv.orgNameAr = brand.orgNameAr() != null ? brand.orgNameAr() : orgAr;
            cv.brand = brandColor;
            cv.classification = "Confidential";
            cv.generatedAt = now;
            PdfTheme.coverHero(doc, cv);

            PdfTheme.kpiStrip(doc, List.of(
                new PdfTheme.Kpi("Controls", String.valueOf(controls), "Requirement mapping"),
                new PdfTheme.Kpi("Evidence", String.valueOf(evidence), "Indexed artifacts"),
                new PdfTheme.Kpi("Audit", String.valueOf(auditEntries), "Trail entries")
            ));

            var meta = PdfTheme.metadataGrid();
            PdfTheme.addMetaCell(meta, "Organisation", orgEn);
            PdfTheme.addMetaCell(meta, "Generated by", generatedBy == null ? "—" : generatedBy);
            PdfTheme.addMetaCell(meta, "Generated (UTC)", now.format(TS));
            PdfTheme.addMetaCell(meta, "Hijri", PdfTheme.formatHijri(now.toLocalDate()));
            doc.add(meta);

            PdfTheme.sectionDivider(doc, "Package contents", "محتويات الحزمة", brandColor);
            doc.add(new Paragraph("README.pdf — this cover and index.", PdfFonts.body(11)));
            doc.add(new Paragraph("Requirement_Mapping.xlsx — controls / requirements × framework × status.", PdfFonts.body(11)));
            doc.add(new Paragraph("Evidence_Index.xlsx — evidence register with integrity hashes.", PdfFonts.body(11)));
            doc.add(new Paragraph("Evidence_Files/ — the actual uploaded evidence binaries (file-backed items).", PdfFonts.body(11)));
            doc.add(new Paragraph("Audit_Trail.xlsx — recent audit-log activity (who / what / when).", PdfFonts.body(11)));
            doc.add(new Paragraph("manifest.json — SHA-256 checksum of every file above (incl. each evidence binary).", PdfFonts.body(11)));

            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new ExportException("PDF", "Regulator package README failed", e);
        }
        return baos.toByteArray();
    }

    // ── manifest.json ────────────────────────────────────────────────────────

    private static String manifestJson(Long orgId, String orgName, LocalDateTime now, String generatedBy,
                                       int controls, int evidence, int auditEntries,
                                       String[] names, byte[][] blobs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"package\": \"regulator-evidence-package\",\n");
        sb.append("  \"organisationId\": ").append(orgId).append(",\n");
        sb.append("  \"organisation\": \"").append(esc(orgName)).append("\",\n");
        sb.append("  \"generatedAtUtc\": \"").append(now.format(TS)).append("\",\n");
        sb.append("  \"generatedBy\": \"").append(esc(generatedBy)).append("\",\n");
        sb.append("  \"counts\": { \"controls\": ").append(controls)
          .append(", \"evidenceItems\": ").append(evidence)
          .append(", \"auditEntries\": ").append(auditEntries).append(" },\n");
        sb.append("  \"files\": [\n");
        for (int i = 0; i < names.length; i++) {
            sb.append("    { \"name\": \"").append(esc(names[i]))
              .append("\", \"bytes\": ").append(blobs[i].length)
              .append(", \"sha256\": \"").append(sha256(blobs[i])).append("\" }");
            sb.append(i < names.length - 1 ? ",\n" : "\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /**
     * Append the actual uploaded evidence-file binaries (under {@code Evidence_Files/}) to the
     * package name/blob lists, so they land in the ZIP and are checksummed in the manifest.
     * Link-only / file-less evidence is skipped; a missing or over-budget file is skipped with a
     * log rather than failing the whole pack. A total-size budget guards against OOM (the ZIP is
     * buffered in memory).
     */
    private void embedEvidenceFiles(List<EvidenceItem> evidence, List<String> names, List<byte[]> blobs) {
        long remaining = MAX_EVIDENCE_TOTAL_BYTES;
        int embedded = 0, noFile = 0, missing = 0, tooBig = 0;
        Set<String> used = new HashSet<>();
        for (EvidenceItem e : evidence) {
            String path = e.getFilePath();
            if (path == null || path.isBlank()) { noFile++; continue; }
            byte[] bytes;
            try {
                bytes = evidenceStorage.load(path);
            } catch (RuntimeException ex) {
                missing++;
                log.warn("Regulator package: evidence file unavailable (id={}, path={}): {}",
                    e.getId(), path, ex.getMessage());
                continue;
            }
            if (bytes.length > remaining) { tooBig++; continue; }
            remaining -= bytes.length;
            names.add(uniqueName("Evidence_Files/" + e.getId() + "_" + safeFileName(e.getFileName()), used));
            blobs.add(bytes);
            embedded++;
        }
        log.info("Regulator package evidence binaries: embedded={}, no-file={}, missing={}, over-budget={}",
            embedded, noFile, missing, tooBig);
    }

    /** Sanitize an original filename for a ZIP entry: strip path separators + illegal chars, cap length. */
    private static String safeFileName(String raw) {
        String s = (raw == null || raw.isBlank()) ? "evidence.bin" : raw.trim();
        s = s.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        return s.length() > 120 ? s.substring(s.length() - 120) : s;
    }

    /** Ensure a unique ZIP entry name (case-insensitive), inserting {@code _n} before the extension. */
    private static String uniqueName(String base, Set<String> used) {
        String name = base;
        int n = 2;
        while (!used.add(name.toLowerCase(Locale.ROOT))) {
            int dot = base.lastIndexOf('.');
            int slash = base.lastIndexOf('/');
            name = (dot > slash) ? base.substring(0, dot) + "_" + n + base.substring(dot) : base + "_" + n;
            n++;
        }
        return name;
    }

    private static void addEntry(ZipOutputStream zip, String name, byte[] bytes) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(bytes);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
