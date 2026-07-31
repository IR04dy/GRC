package com.tss.grc.module.iam.governance.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.Permission;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.governance.entity.AccessReviewCampaign;
import com.tss.grc.module.iam.governance.entity.AccessReviewItem;
import com.tss.grc.module.iam.governance.entity.SodViolation;
import com.tss.grc.module.iam.governance.repository.AccessRequestRepository;
import com.tss.grc.module.iam.governance.repository.AccessReviewCampaignRepository;
import com.tss.grc.module.iam.governance.repository.AccessReviewItemRepository;
import com.tss.grc.module.iam.governance.repository.SodViolationRepository;
import com.tss.grc.module.iam.repository.RoleRepository;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.platform.export.ExportNaming;
import com.tss.grc.module.platform.export.PdfFonts;
import com.tss.grc.module.platform.export.PdfTheme;
import com.tss.grc.module.platform.export.XlsxStyler;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * RBAC Access-Governance P2 (plan §6.7) — the one-click access-evidence pack handed to
 * a SAMA/NCA inspector: branded {@code README.pdf} + {@code Role_Catalogue.xlsx} +
 * {@code Permission_Matrix.xlsx} + {@code User_Assignments.xlsx} +
 * {@code SoD_Violations.xlsx} + {@code Recertification_Evidence.xlsx} +
 * {@code manifest.json} (SHA-256 of every entry). Same export stack as the
 * regulator package (XlsxStyler / PdfTheme / ExportNaming); org-scoped throughout.
 */
@Service
@RequiredArgsConstructor
public class AccessEvidencePackageService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_ROWS = 5000;

    private final RoleRepository roleRepo;
    private final AccessRequestRepository assignments;
    private final SodViolationRepository violationRepo;
    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewItemRepository itemRepo;
    private final OrganisationRepository orgRepo;
    private final BrandThemeService brandThemeService;

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

        // ── gather (org-scoped; global template roles included read-only) ──
        List<Role> roles = roleRepo.findByOrganisationIdOrOrganisationIdIsNull(org).stream()
            .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
            .sorted(Comparator.comparing(Role::getRoleName))
            .toList();
        List<Object[]> assignmentRows = assignments.listAssignments(org);
        List<SodViolation> violations =
            violationRepo.findByOrganisationIdAndIsDeletedFalseOrderByDetectedAtDesc(org);
        List<AccessReviewCampaign> campaigns =
            campaignRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(org);

        // ── sheets ──────────────────────────────────────────────────────────
        byte[] roleCatalogue = writeXlsx(brandColor, "Role Catalogue",
            new String[]{"Role", "Display (EN)", "Display (AR)", "Scope", "System Role", "Status", "Grants"},
            roles, r -> new String[]{
                nz(r.getRoleName()), nz(r.getDisplayNameEn()), nz(r.getDisplayNameAr()),
                r.getOrganisationId() == null ? "GLOBAL TEMPLATE" : "TENANT",
                Boolean.TRUE.equals(r.getIsSystemRole()) ? "YES" : "NO",
                // F2-62: shares Role#grantsAuthority with the authority mint, so this column is
                // now a statement about access rather than a stored flag nothing consulted. The
                // predicate is unchanged (Boolean.TRUE.equals) — what changed is that it is true.
                r.grantsAuthority() ? "ACTIVE" : "INACTIVE",
                String.valueOf(r.getPermissions().size())
            });

        // F2-62: the Status column carries onto every matrix row. Without it the two sheets
        // disagree — sheet 1 calls a role INACTIVE while sheet 2 lists its module/action grants
        // in the same shape as a live role's, and the matrix is the sheet an assessor reads as
        // "who can do what". The catalogue's Grants count stays the configured count, which is
        // what it has always measured; INACTIVE is what tells the reader it buys nothing.
        List<String[]> matrixRows = new ArrayList<>();
        for (Role r : roles) {
            String status = r.grantsAuthority() ? "ACTIVE" : "INACTIVE";
            r.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getModule).thenComparing(Permission::getAction))
                .forEach(p -> matrixRows.add(
                    new String[]{r.getRoleName(), p.getModule(), p.getAction(), status}));
        }
        byte[] matrix = writeXlsx(brandColor, "Permission Matrix",
            new String[]{"Role", "Module", "Action", "Role Status"},
            matrixRows, x -> x);

        byte[] userAssignments = writeXlsx(brandColor, "User Assignments",
            new String[]{"User", "Role", "Source", "Valid From", "Valid To", "Justification",
                "Assigned At", "Approved By"},
            assignmentRows.size() > MAX_ROWS ? assignmentRows.subList(0, MAX_ROWS) : assignmentRows,
            a -> new String[]{
                str(a[0]), str(a[1]), str(a[2]), str(a[3]), str(a[4]), str(a[5]), str(a[6]), str(a[7])
            });

        byte[] sodSheet = writeXlsx(brandColor, "SoD Violations",
            new String[]{"Rule", "Severity", "User", "Conflict", "Source", "Status",
                "Detected (UTC)", "Accepted Until", "Disposition Note"},
            violations, v -> new String[]{
                nz(v.getRuleCode()), nz(v.getSeverity()), nz(v.getUsername()), nz(v.getDetailEn()),
                nz(v.getDetectionSource()), nz(v.getStatus()),
                v.getDetectedAt() == null ? "" : v.getDetectedAt().format(TS),
                v.getAcceptedUntil() == null ? "" : v.getAcceptedUntil().toString(),
                nz(v.getDispositionNote())
            });

        List<String[]> recertRows = new ArrayList<>();
        Map<Long, AccessReviewCampaign> byId = new HashMap<>();
        campaigns.forEach(c -> byId.put(c.getId(), c));
        for (AccessReviewCampaign c : campaigns) {
            for (AccessReviewItem i : itemRepo.findByCampaignIdAndIsDeletedFalseOrderByUsernameAscRoleNameAsc(c.getId())) {
                recertRows.add(new String[]{
                    nz(c.getTitleEn()), nz(c.getScope()),
                    c.getWindowStart() + " → " + c.getWindowEnd(), nz(c.getWindowHijri()),
                    nz(c.getStatus()), nz(i.getUsername()), nz(i.getRoleName()), nz(i.getDecision()),
                    i.getDecidedAt() == null ? "" : i.getDecidedAt().format(TS), nz(i.getNote())
                });
                if (recertRows.size() >= MAX_ROWS) break;
            }
            if (recertRows.size() >= MAX_ROWS) break;
        }
        byte[] recert = writeXlsx(brandColor, "Recertification Evidence",
            new String[]{"Campaign", "Scope", "Window", "Window (Hijri)", "Campaign Status",
                "User", "Role", "Decision", "Decided (UTC)", "Note"},
            recertRows, x -> x);

        long openSod = violations.stream().filter(v -> "OPEN".equals(v.getStatus())).count();
        byte[] readme = readmePdf(orgName, o == null ? null : o.getNameAr(), brand, brandColor, now,
            generatedByName, roles.size(), assignmentRows.size(), openSod, campaigns.size());

        // ── assemble ZIP (manifest last — it checksums every other entry) ───
        String[] names = {"README.pdf", "Role_Catalogue.xlsx", "Permission_Matrix.xlsx",
            "User_Assignments.xlsx", "SoD_Violations.xlsx", "Recertification_Evidence.xlsx"};
        byte[][] blobs = {readme, roleCatalogue, matrix, userAssignments, sodSheet, recert};
        String manifest = manifestJson(org, orgName, now, generatedByName,
            roles.size(), assignmentRows.size(), violations.size(), campaigns.size(), names, blobs);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < names.length; i++) addEntry(zip, names[i], blobs[i]);
            addEntry(zip, "manifest.json", manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new ExportException("ZIP", "Access evidence package generation failed", e);
        }

        String filename = ExportNaming.build("AccessGovernance", "EvidencePackage", "en", "zip");
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
            throw new ExportException("XLSX", "Access evidence sheet failed", e);
        }
    }

    // ── README PDF ─────────────────────────────────────────────────────────

    private byte[] readmePdf(String orgEn, String orgAr, BrandThemeService.ResolvedBrand brand,
                             Color brandColor, LocalDateTime now, String generatedBy,
                             int roles, int assignments, long openSod, int campaigns) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(PdfTheme.professionalFooter("Access Governance Evidence Package", "Confidential"));
            doc.open();

            PdfTheme.Cover cv = new PdfTheme.Cover();
            cv.eyebrow = "Access Governance Evidence Package";
            cv.eyebrowAr = "حزمة أدلة حوكمة الوصول";
            cv.titleEn = "Access Evidence";
            cv.titleAr = "أدلة إدارة الوصول";
            cv.subtitle = brand.taglineEn();
            cv.subtitleAr = brand.taglineAr();
            cv.orgNameEn = orgEn;
            cv.orgNameAr = brand.orgNameAr() != null ? brand.orgNameAr() : orgAr;
            cv.brand = brandColor;
            cv.classification = "Confidential";
            cv.generatedAt = now;
            PdfTheme.coverHero(doc, cv);

            PdfTheme.kpiStrip(doc, List.of(
                new PdfTheme.Kpi("Roles", String.valueOf(roles), "Catalogue entries"),
                new PdfTheme.Kpi("Assignments", String.valueOf(assignments), "Live user↔role"),
                new PdfTheme.Kpi("Open SoD", String.valueOf(openSod), "Violations"),
                new PdfTheme.Kpi("Campaigns", String.valueOf(campaigns), "Recertifications")
            ));

            var meta = PdfTheme.metadataGrid();
            PdfTheme.addMetaCell(meta, "Organisation", orgEn);
            PdfTheme.addMetaCell(meta, "Generated by", generatedBy == null ? "—" : generatedBy);
            PdfTheme.addMetaCell(meta, "Generated (UTC)", now.format(TS));
            PdfTheme.addMetaCell(meta, "Hijri", PdfTheme.formatHijri(now.toLocalDate()));
            doc.add(meta);

            PdfTheme.sectionDivider(doc, "Package contents", "محتويات الحزمة", brandColor);
            doc.add(new Paragraph("README.pdf — this cover and index.", PdfFonts.body(11)));
            doc.add(new Paragraph("Role_Catalogue.xlsx — every role with scope, status and grant count.", PdfFonts.body(11)));
            doc.add(new Paragraph("Permission_Matrix.xlsx — role × module × action grants (flat).", PdfFonts.body(11)));
            doc.add(new Paragraph("User_Assignments.xlsx — live assignments incl. validity window and justification.", PdfFonts.body(11)));
            doc.add(new Paragraph("SoD_Violations.xlsx — segregation-of-duties findings and dispositions.", PdfFonts.body(11)));
            doc.add(new Paragraph("Recertification_Evidence.xlsx — campaign windows (Hijri + Gregorian) and item decisions.", PdfFonts.body(11)));
            doc.add(new Paragraph("manifest.json — SHA-256 checksum of every file above.", PdfFonts.body(11)));

            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new ExportException("PDF", "Access evidence README failed", e);
        }
        return baos.toByteArray();
    }

    // ── manifest.json ────────────────────────────────────────────────────────

    private static String manifestJson(Long orgId, String orgName, LocalDateTime now, String generatedBy,
                                       int roles, int assignments, int violations, int campaigns,
                                       String[] names, byte[][] blobs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"package\": \"access-governance-evidence-package\",\n");
        sb.append("  \"organisationId\": ").append(orgId).append(",\n");
        sb.append("  \"organisation\": \"").append(esc(orgName)).append("\",\n");
        sb.append("  \"generatedAtUtc\": \"").append(now.format(TS)).append("\",\n");
        sb.append("  \"generatedBy\": \"").append(esc(generatedBy)).append("\",\n");
        sb.append("  \"counts\": { \"roles\": ").append(roles)
          .append(", \"assignments\": ").append(assignments)
          .append(", \"sodViolations\": ").append(violations)
          .append(", \"campaigns\": ").append(campaigns).append(" },\n");
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

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
