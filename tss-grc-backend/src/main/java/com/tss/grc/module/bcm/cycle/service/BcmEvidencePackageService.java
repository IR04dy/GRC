package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.exception.ExportException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.platform.export.PdfFonts;
import com.tss.grc.module.platform.export.PdfTheme;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.util.Locale;
import java.awt.Color;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * BCM Audit Evidence Package generator — audit Tier-2 (Auditor journey 0✅/4🔴 fix).
 *
 * <p>Produces a single ZIP file containing every approved BCM artefact for a
 * given assessment cycle, ready for handover to an external auditor or
 * regulator. The package contains:
 * <ul>
 *   <li>{@code README.pdf}                  — branded cover (PdfTheme + tenant
 *       BrandTheme): org, cycle name, status/score, generated-at/by, contents.</li>
 *   <li>{@code bias.xlsx}                   — every APPROVED / ACTIVE BIA in the
 *       cycle with its time-band matrix, criticality, owners.</li>
 *   <li>{@code risks.xlsx}                  — BCM risk register (likelihood,
 *       impact, residual score, treatment plan).</li>
 *   <li>{@code strategies.xlsx}             — continuity strategies (RTO/RPO
 *       requirement, selected strategy, cost, feasibility, residual risk).</li>
 *   <li>{@code gaps_and_capas.xlsx}         — open + closed gaps and CAPAs.</li>
 *   <li>{@code compliance_matrix.xlsx}      — every clause × status row with
 *       evidence and gap linkages.</li>
 *   <li>{@code audit_log.csv}               — every F7 audit-log entry for this
 *       cycle ({@code source_module='bcm'} or a BCM entity type — F2-324),
 *       carrying who/when/what including the actor ({@code user_username}/
 *       {@code user_role}) the unified viewer shows — F2-325.</li>
 *   <li>{@code MANIFEST.txt}                — per-file SHA-256 checksums so a
 *       regulator can verify integrity.</li>
 * </ul>
 *
 * <p>No row counts are excluded — auditors need the full register. For very
 * large tenants this can produce multi-MB packages; the service streams
 * directly to a {@link ByteArrayOutputStream} but doesn't paginate.
 *
 * <p>Plan PDFs (per spec §Q report #15) are not included here — that requires
 * the BCP/DRP plan editors with named sections (deferred audit T1.4b). When
 * those land, this service grows three more entries: bcp_v{n}.pdf, drp_v{n}.pdf,
 * crisis_event_{code}.pdf.
 */
@Service
@RequiredArgsConstructor
public class BcmEvidencePackageService {

    private static final Logger log = LoggerFactory.getLogger(BcmEvidencePackageService.class);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final BcmAssessmentCycleRepository cycles;
    private final BcmBiaAssessmentRepository bias;
    private final BcmRiskRepository risks;
    private final ContinuityStrategyRepository strategies;
    private final BcmGapRepository gaps;
    private final BcmCorrectiveActionRepository capas;
    private final BcmComplianceMappingRepository compliance;
    private final AuditLogRepository auditLog;

    /**
     * \u26a0\u26a0 F2-324 \u2014 injected as a FIELD, not a constructor parameter, so
     * {@code @RequiredArgsConstructor} is unchanged and {@code BcmEvidencePackageServiceTest}'s
     * {@code @InjectMocks} keeps working. Same reason F2-323 declared its transaction manager this
     * way. It is used only to enumerate this module's entity types (see {@link #bcmEntityTypes}).
     */
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;
    private final OrganisationRepository organisations;
    private final AuditLogService audit;
    private final BrandThemeService brandThemeService;

    /**
     * Result holder for the controller — bytes + suggested filename.
     */
    public record EvidencePackage(byte[] bytes, String filename) {}

    /**
     * Produce the package for {@code cycleId} owned by the current tenant.
     * Throws if the cycle is not found in this tenant.
     */
    @Transactional(readOnly = true)
    public EvidencePackage generate(Long cycleId, String generatedByName) {
        Long org = TenantContext.getCurrentTenant();
        if (org == null) throw new IllegalStateException("No tenant context");

        BcmAssessmentCycle cycle = cycles.findByIdAndOrganisationId(cycleId, org)
            .orElseThrow(() -> new EntityNotFoundException("Cycle not found: " + cycleId));

        String orgName = organisations.findById(org).map(Organisation::getNameEn)
            .orElse("Organisation #" + org);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            // Collect everything in scope of this cycle. Risks / strategies /
            // gaps / capas tie to the cycle via cycle_id; compliance + audit
            // log are tenant-scoped (the regulator wants the whole tenant
            // picture, not just rows tagged with the cycle).
            List<BcmBiaAssessment>          biaRows         = bias.findByOrganisationIdAndCycleIdOrderByIdDesc(org, cycleId);
            List<BcmRisk>                   riskRows        = risks.findByOrganisationIdAndCycleIdOrderByIdDesc(org, cycleId);
            List<ContinuityStrategy>        strategyRows    = strategies.findByOrganisationIdAndCycleIdOrderByIdDesc(org, cycleId);
            List<BcmGap>                    gapRows         = gaps.findByOrganisationIdAndCycleIdOrderByIdDesc(org, cycleId);
            List<BcmCorrectiveAction>       capaRows        = capas.findByOrganisationIdOrderByIdDesc(org);
            List<BcmComplianceMapping>      complianceRows  = compliance.findByOrganisationIdOrderBySourceFrameworkAscRequirementCodeAsc(org);
            // \u26a0\u26a0 F2-324 \u2014 `source_module = 'bcm'` alone carried 15.0% of the BCM trail in dev
            // and 6.8% in prod, because 686 of the platform's 722 audit-writing sites could not set
            // the column at all. It omitted 136 BcmAssessmentCycle rows: the trail of the very cycle
            // this package documents, in the file a regulator reads as that trail, with README.pdf
            // printing the truncated count as the total.
            //
            // \U0001f511 The column is derived now (AuditLogService#callerModule), so rows written from
            // here on carry `bcm`. Historical rows stay NULL and must \u2014 the ledger is append-only and
            // F2-31 hash-chains it \u2014 so they are recovered by entity_type instead.
            java.util.Set<String> bcmTypes = bcmEntityTypes();
            List<AuditLogEntry> auditRows = bcmTypes.isEmpty()
                ? auditLog.findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(
                      org, "bcm", Pageable.unpaged()).getContent()
                : auditLog.findForModuleOrEntityTypes(org, "bcm", bcmTypes);

            byte[] bias_xlsx        = writeXlsx("BIA assessments",          biaRows,        BcmEvidencePackageService::biaRow,        biaHeader());
            byte[] risks_xlsx       = writeXlsx("BCM risks",                riskRows,       BcmEvidencePackageService::riskRow,       riskHeader());
            byte[] strategies_xlsx  = writeXlsx("Continuity strategies",    strategyRows,   BcmEvidencePackageService::strategyRow,   strategyHeader());
            byte[] gaps_xlsx        = writeGapsAndCapasXlsx(gapRows, capaRows);
            byte[] compliance_xlsx  = writeXlsx("Compliance matrix",        complianceRows, BcmEvidencePackageService::complianceRow, complianceHeader());
            byte[] audit_csv        = writeAuditCsv(auditRows);
            byte[] readme_pdf       = writeReadmePdf(orgName, cycle, generatedByName,
                                            biaRows.size(), riskRows.size(), strategyRows.size(),
                                            gapRows.size(), capaRows.size(), complianceRows.size(),
                                            auditRows.size());

            addEntry(zip, "README.pdf",               readme_pdf);
            addEntry(zip, "bias.xlsx",                bias_xlsx);
            addEntry(zip, "risks.xlsx",               risks_xlsx);
            addEntry(zip, "strategies.xlsx",          strategies_xlsx);
            addEntry(zip, "gaps_and_capas.xlsx",      gaps_xlsx);
            addEntry(zip, "compliance_matrix.xlsx",   compliance_xlsx);
            addEntry(zip, "audit_log.csv",            audit_csv);

            // MANIFEST.txt last — depends on the other files' bytes for checksums
            String manifest = manifest(
                "README.pdf",             readme_pdf,
                "bias.xlsx",              bias_xlsx,
                "risks.xlsx",             risks_xlsx,
                "strategies.xlsx",        strategies_xlsx,
                "gaps_and_capas.xlsx",    gaps_xlsx,
                "compliance_matrix.xlsx", compliance_xlsx,
                "audit_log.csv",          audit_csv);
            addEntry(zip, "MANIFEST.txt", manifest.getBytes());
        } catch (IOException e) {
            throw new ExportException("ZIP", "Failed to assemble evidence package: " + e.getMessage(), e);
        }

        byte[] zipBytes = out.toByteArray();
        String filename = String.format(Locale.ROOT, "bcm-evidence-%s-cycle-%d-%s.zip",
            cycle.getReportingYear(), cycleId, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

        audit.log("REPORT", "BcmAssessmentCycle", cycleId,
            "Evidence package generated: " + filename + " size=" + zipBytes.length + "B");

        return new EvidencePackage(zipBytes, filename);
    }

    // ─── XLSX writers ─────────────────────────────────────────────

    @FunctionalInterface
    private interface RowMapper<T> { Object[] map(T row); }

    private static <T> byte[] writeXlsx(String sheetName, List<T> rows, RowMapper<T> mapper, String[] header) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet(sheetName);
            Row hr = sh.createRow(0);
            CellStyle boldStyle = wb.createCellStyle();
            Font bold = wb.createFont(); bold.setBold(true); boldStyle.setFont(bold);
            for (int c = 0; c < header.length; c++) {
                Cell cell = hr.createCell(c);
                cell.setCellValue(header[c]);
                cell.setCellStyle(boldStyle);
            }
            int r = 1;
            for (T row : rows) {
                Object[] values = mapper.map(row);
                Row xr = sh.createRow(r++);
                for (int c = 0; c < values.length; c++) {
                    Cell cell = xr.createCell(c);
                    Object v = values[c];
                    if (v == null)                cell.setCellValue("");
                    else if (v instanceof Number n) cell.setCellValue(n.doubleValue());
                    else                          cell.setCellValue(String.valueOf(v));
                }
            }
            for (int c = 0; c < header.length; c++) sh.autoSizeColumn(c);
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new ExportException("Excel", "Failed to write " + sheetName + " sheet: " + e.getMessage(), e);
        }
    }

    private static String[] biaHeader() {
        return new String[] { "id", "bia_code", "service_id", "process_id", "department",
            "mtpd_h", "rto_h", "rpo_h", "overall_criticality", "approval_status",
            "manual_workaround", "mbco", "approved_by", "approved_at" };
    }
    private static Object[] biaRow(BcmBiaAssessment b) {
        return new Object[] { b.getId(), b.getBiaCode(), b.getServiceId(), b.getProcessId(), b.getDepartment(),
            b.getMtpdHours(), b.getRtoHours(), b.getRpoHours(), b.getOverallCriticality(), b.getApprovalStatus(),
            b.getManualWorkaroundAvailable(), b.getMbcoDescription(), b.getApprovedBy(),
            b.getApprovedAt() == null ? null : b.getApprovedAt().format(ISO) };
    }

    private static String[] riskHeader() {
        return new String[] { "id", "title_en", "threat_category", "likelihood", "impact",
            "inherent_risk_score", "control_effectiveness", "residual_risk_score",
            "risk_owner_id", "status", "treatment_plan" };
    }
    private static Object[] riskRow(BcmRisk r) {
        return new Object[] { r.getId(), r.getTitleEn(), r.getThreatCategory(), r.getLikelihood(), r.getImpact(),
            r.getInherentRiskScore(), r.getControlEffectiveness(), r.getResidualRiskScore(),
            r.getRiskOwnerId(), r.getStatus(), r.getTreatmentPlan() };
    }

    private static String[] strategyHeader() {
        return new String[] { "id", "service_id", "process_id", "rto_req_h", "rpo_req_h",
            "selected_strategy", "feasibility", "cost_estimate", "residual_risk",
            "approval_status", "implementation_status" };
    }
    private static Object[] strategyRow(ContinuityStrategy s) {
        return new Object[] { s.getId(), s.getRelatedServiceId(), s.getRelatedProcessId(),
            s.getRtoRequirementHours(), s.getRpoRequirementHours(),
            s.getSelectedStrategy(), s.getFeasibility(), s.getCostEstimate(),
            s.getResidualRisk(), s.getApprovalStatus(), s.getImplementationStatus() };
    }

    /**
     * Two-sheet workbook: gaps + capas. Auditors expect them paired so they
     * can trace each high/critical gap to its remediating CAPA.
     */
    private byte[] writeGapsAndCapasXlsx(List<BcmGap> gapRows, List<BcmCorrectiveAction> capaRows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            CellStyle boldStyle = wb.createCellStyle();
            Font bold = wb.createFont(); bold.setBold(true); boldStyle.setFont(bold);

            Sheet gapsSheet = wb.createSheet("Gaps");
            String[] gh = new String[] { "id", "source", "title_en", "severity", "status", "due_date", "description" };
            writeHeader(gapsSheet, gh, boldStyle);
            int gr = 1;
            for (BcmGap g : gapRows) {
                Row r = gapsSheet.createRow(gr++);
                Object[] v = { g.getId(), g.getSource(), g.getTitleEn(), g.getSeverity(), g.getStatus(),
                    g.getDueDate() == null ? null : g.getDueDate().toString(), g.getDescription() };
                for (int c = 0; c < v.length; c++) {
                    Cell cell = r.createCell(c);
                    if (v[c] == null) cell.setCellValue("");
                    else cell.setCellValue(String.valueOf(v[c]));
                }
            }
            for (int c = 0; c < gh.length; c++) gapsSheet.autoSizeColumn(c);

            Sheet capasSheet = wb.createSheet("CAPAs");
            String[] ch = new String[] { "id", "gap_id", "title_en", "priority", "status",
                "progress_pct", "due_date", "closure_date", "evidence_id", "action_plan" };
            writeHeader(capasSheet, ch, boldStyle);
            int cr = 1;
            for (BcmCorrectiveAction c : capaRows) {
                Row r = capasSheet.createRow(cr++);
                Object[] v = { c.getId(), c.getGapId(), c.getTitleEn(), c.getPriority(), c.getStatus(),
                    c.getProgressPercent(),
                    c.getDueDate() == null ? null : c.getDueDate().toString(),
                    c.getClosureDate() == null ? null : c.getClosureDate().toString(),
                    c.getEvidenceId(), c.getActionPlan() };
                for (int i = 0; i < v.length; i++) {
                    Cell cell = r.createCell(i);
                    if (v[i] == null) cell.setCellValue("");
                    else if (v[i] instanceof Number n) cell.setCellValue(n.doubleValue());
                    else cell.setCellValue(String.valueOf(v[i]));
                }
            }
            for (int i = 0; i < ch.length; i++) capasSheet.autoSizeColumn(i);

            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new ExportException("Excel", "Failed to write gaps/capas sheet: " + e.getMessage(), e);
        }
    }

    private static void writeHeader(Sheet sheet, String[] header, CellStyle boldStyle) {
        Row r = sheet.createRow(0);
        for (int c = 0; c < header.length; c++) {
            Cell cell = r.createCell(c);
            cell.setCellValue(header[c]);
            cell.setCellStyle(boldStyle);
        }
    }

    private static String[] complianceHeader() {
        return new String[] { "framework", "requirement_code", "domain", "title_en",
            "related_workflow", "compliance_status", "is_placeholder",
            "evidence_id", "gap_id", "assessed_by", "assessed_at" };
    }
    private static Object[] complianceRow(BcmComplianceMapping m) {
        return new Object[] { m.getSourceFramework(), m.getRequirementCode(), m.getDomain(),
            m.getRequirementTitleEn(), m.getRelatedWorkflow(), m.getComplianceStatus(),
            m.getIsPlaceholder(), m.getEvidenceId(), m.getGapId(), m.getAssessedBy(),
            m.getAssessedAt() == null ? null : m.getAssessedAt().format(ISO) };
    }

    // ─── CSV writer for audit log ─────────────────────────────────

    /**
     * CSV instead of XLSX for audit_log: typically thousands of rows, and
     * regulators import these into their own SIEM tools that prefer CSV.
     */
    private static byte[] writeAuditCsv(List<AuditLogEntry> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,created_at,user_username,user_role,action_type,entity_type,entity_id,entity_code,severity,source_module,correlation_id,message\n");
        for (AuditLogEntry e : rows) {
            sb.append(e.getId()).append(',');
            sb.append(e.getCreatedAt() == null ? "" : e.getCreatedAt().format(ISO)).append(',');
            // F2-325: the actor (who) and the role held at the time. The entity's own javadoc
            // cites §29 — who/when/what/where — and the unified viewer renders a User column, but
            // this regulator export dropped WHO. Recorded on 89/90 of the on-request BCM rows this
            // package carries; blank where the writer was an off-request sweep/automation (the
            // honest state the viewer shows as "—", not a fabrication). user_id is NOT exported:
            // it is 0% populated in both databases (the service records the username, never the id).
            sb.append(csv(e.getUserUsername())).append(',');
            sb.append(csv(e.getUserRole())).append(',');
            sb.append(csv(e.getActionType())).append(',');
            sb.append(csv(e.getEntityType())).append(',');
            sb.append(e.getEntityId() == null ? "" : e.getEntityId()).append(',');
            sb.append(csv(e.getEntityCode())).append(',');
            sb.append(csv(e.getSeverity())).append(',');
            sb.append(csv(e.getSourceModule())).append(',');
            sb.append(csv(e.getCorrelationId())).append(',');
            sb.append(csv(e.getMessage())).append('\n');
        }
        return sb.toString().getBytes();
    }

    private static String csv(String v) {
        if (v == null) return "";
        // SEC-BCM-005: defend against CSV/spreadsheet formula injection. Audit-log
        // messages are attacker-influenceable free text; a cell beginning with
        // = + - @ (or tab/CR) is executed as a formula when a regulator opens the
        // package in Excel/Sheets. Prefix such cells with a single quote so the
        // value is rendered as literal text (OWASP CSV-injection guidance).
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n")
                || v.contains("\r") || v.contains("\t")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // ─── Cover sheet + manifest ───────────────────────────────────

    /** Branded README.pdf cover (PdfTheme + tenant BrandTheme) — replaces the former
     *  plain cover.txt so the evidence pack opens with a leadership-grade cover page. */
    private byte[] writeReadmePdf(String orgName, BcmAssessmentCycle cycle, String generatedBy,
                                  int biaCount, int riskCount, int strategyCount,
                                  int gapCount, int capaCount, int complianceCount, int auditCount) {
        BrandThemeService.ResolvedBrand brand = brandThemeService.resolve();
        Color brandColor = brand.primaryColor();
        String orgEn = brand.orgNameEn() != null ? brand.orgNameEn() : orgName;
        LocalDateTime now = LocalDateTime.now();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(PdfTheme.professionalFooter("BCM Audit Evidence Package", "Confidential"));
            doc.open();

            PdfTheme.Cover cv = new PdfTheme.Cover();
            cv.eyebrow = "BCM Audit Evidence Package";
            cv.eyebrowAr = "حزمة أدلة تدقيق استمرارية الأعمال";
            cv.titleEn = cycle.getCycleNameEn() == null ? "Evidence Package" : cycle.getCycleNameEn();
            cv.subtitle = "Reporting year " + cycle.getReportingYear();
            cv.orgNameEn = orgEn;
            cv.orgNameAr = brand.orgNameAr();
            cv.brand = brandColor;
            cv.classification = "Confidential";
            cv.generatedAt = now;
            PdfTheme.coverHero(doc, cv);

            PdfTheme.kpiStrip(doc, List.of(
                new PdfTheme.Kpi("BIA", String.valueOf(biaCount), "Assessments"),
                new PdfTheme.Kpi("Risks", String.valueOf(riskCount), "BCM register"),
                new PdfTheme.Kpi("Strategies", String.valueOf(strategyCount), "Continuity"),
                new PdfTheme.Kpi("Compliance", String.valueOf(complianceCount), "Clauses")
            ));

            var meta = PdfTheme.metadataGrid();
            PdfTheme.addMetaCell(meta, "Organisation", orgEn);
            PdfTheme.addMetaCell(meta, "Cycle", cycle.getCycleNameEn() + " (" + cycle.getReportingYear() + ")");
            PdfTheme.addMetaCell(meta, "Cycle status", cycle.getStatus());
            PdfTheme.addMetaCell(meta, "Overall score", cycle.getOverallScore() == null ? "—" : String.valueOf(cycle.getOverallScore()));
            PdfTheme.addMetaCell(meta, "Generated by", generatedBy == null ? "system" : generatedBy);
            PdfTheme.addMetaCell(meta, "Generated (UTC)", now.format(ISO));
            doc.add(meta);

            PdfTheme.sectionDivider(doc, "Package contents", "محتويات الحزمة", brandColor);
            doc.add(new Paragraph("bias.xlsx — " + biaCount + " BIA assessment row(s).", PdfFonts.body(11)));
            doc.add(new Paragraph("risks.xlsx — " + riskCount + " BCM risk row(s).", PdfFonts.body(11)));
            doc.add(new Paragraph("strategies.xlsx — " + strategyCount + " continuity strategy row(s).", PdfFonts.body(11)));
            doc.add(new Paragraph("gaps_and_capas.xlsx — " + gapCount + " gap(s) + " + capaCount + " CAPA(s).", PdfFonts.body(11)));
            doc.add(new Paragraph("compliance_matrix.xlsx — " + complianceCount + " clause(s).", PdfFonts.body(11)));
            doc.add(new Paragraph("audit_log.csv — " + auditCount + " audit entry/entries.", PdfFonts.body(11)));
            doc.add(new Paragraph("MANIFEST.txt — SHA-256 of every file (verify: sha256sum -c / Get-FileHash).", PdfFonts.body(11)));

            PdfTheme.sectionDivider(doc, "Notes", "ملاحظات", brandColor);
            doc.add(new Paragraph("This pack contains every approved + active BCM artefact for the cycle.", PdfFonts.body(10)));
            doc.add(new Paragraph("Plan PDFs (BCP / DRP / Crisis after-action) are exported separately.", PdfFonts.body(10)));
            doc.add(new Paragraph("Compliance rows flagged is_placeholder require official regulatory control-library import to be final.", PdfFonts.body(10)));

            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new ExportException("PDF", "BCM evidence README failed", e);
        }
        return baos.toByteArray();
    }

    /**
     * Produce {@code MANIFEST.txt} with a SHA-256 over each file. Auditors
     * verify integrity with {@code sha256sum} or {@code Get-FileHash}.
     */
    private static String manifest(Object... nameAndBytes) {
        StringBuilder b = new StringBuilder();
        b.append("# Per-file SHA-256 hashes — verify with sha256sum or Get-FileHash\n");
        for (int i = 0; i + 1 < nameAndBytes.length; i += 2) {
            String name = (String) nameAndBytes[i];
            byte[] data = (byte[]) nameAndBytes[i + 1];
            b.append(sha256(data)).append("  ").append(name).append('\n');
        }
        return b.toString();
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            return "unavailable";
        }
    }

    private static void addEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    /**
     * \u26a0\u26a0 F2-324 \u2014 this module's entity types, taken from the JPA metamodel.
     *
     * <p>\U0001f511 <b>Derived, never listed.</b> {@code module/bcm} declares 52 entity classes; a
     * constant naming them is the artefact [[F2-217]] found measuring its own contents, and it would
     * silently stop covering the next entity somebody adds. The metamodel is already built at
     * startup, so this costs a filter over an in-memory set rather than a classpath scan.
     *
     * <p>\u26a0 Empty when no {@code EntityManager} is present (a directly-constructed unit test), in
     * which case the caller falls back to the module-only query \u2014 the pre-F2-324 behaviour. That is
     * why the widened path needs a test that supplies one, and why this method is package-private.
     */
    java.util.Set<String> bcmEntityTypes() {
        if (em == null) return java.util.Set.of();
        return em.getMetamodel().getEntities().stream()
            .map(jakarta.persistence.metamodel.Type::getJavaType)
            .filter(c -> c.getName().startsWith("com.tss.grc.module.bcm."))
            .map(Class::getSimpleName)
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }
}
