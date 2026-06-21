package com.tss.grc.module.reporting.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.entity.Framework;
import com.tss.grc.module.compliance.entity.PolicyFrameworkMapping;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.compliance.repository.FrameworkRepository;
import com.tss.grc.module.compliance.repository.PolicyFrameworkMappingRepository;
import com.tss.grc.module.ethics.repository.AmlAlertRepository;
import com.tss.grc.module.ethics.repository.EthicsCaseRepository;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.risk.entity.KriDefinition;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.repository.KriReadingRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S1 — One-click board reporting pack generator.
 *
 * Produces a multi-section PDF combining:
 *  1. Cover page (organisation + period + generated-on)
 *  2. Executive summary (KPIs from every domain)
 *  3. Risk Register — top 20 by inherent score
 *  4. Findings aging snapshot (escalation tier breakdown)
 *  5. KRI dashboard (band counts + KRI table)
 *  6. Compliance framework coverage (per framework: policies/findings/controls)
 *  7. Change management snapshot (by status + by type)
 *  8. Ethics & Compliance highlights (cases by category + AML STR count)
 *  9. Closing notes
 *
 * Bilingual: pass lang="ar" to flip to RTL throughout.
 */
@Service
@RequiredArgsConstructor
public class BoardPackService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrganisationRepository orgRepo;
    private final RiskRepository riskRepo;
    private final AuditFindingRepository findingRepo;
    private final KriDefinitionRepository kriRepo;
    private final KriReadingRepository readingRepo;
    private final FrameworkRepository frameworkRepo;
    private final PolicyFrameworkMappingRepository mappingRepo;
    private final ControlRepository controlRepo;
    private final ChangeRequestRepository changeRepo;
    private final EthicsCaseRepository caseRepo;
    private final AmlAlertRepository amlRepo;

    @Transactional(readOnly = true)
    public byte[] generatePdf(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            // Shared executive footer — hairline + classification + Page X of Y.
            // Board packs sit at the highest sensitivity in the GRC platform
            // (combine financial KRIs + open findings + ethics cases), so they
            // ship as Confidential.
            writer.setPageEvent(PdfTheme.professionalFooter(
                ar ? "حزمة المجلس — TSS GRC" : "Board Pack — TSS GRC",
                "Confidential"));
            doc.open();

            // 1. Cover
            renderCover(doc, o, ar);

            // 2. Executive summary
            doc.newPage();
            renderSectionHeader(doc, ar ? "ملخص تنفيذي" : "Executive Summary", ar);
            renderExecutiveSummary(doc, org, ar);

            // 3. Risk Register
            doc.newPage();
            renderSectionHeader(doc, ar ? "سجل المخاطر — أعلى ٢٠" : "Risk Register — Top 20", ar);
            renderRiskRegister(doc, org, ar);

            // 4. Findings aging
            doc.newPage();
            renderSectionHeader(doc, ar ? "ملاحظات التدقيق وتقادمها" : "Audit Findings — Aging Snapshot", ar);
            renderFindings(doc, org, ar);

            // 5. KRI dashboard
            doc.newPage();
            renderSectionHeader(doc, ar ? "لوحة مؤشرات المخاطر الرئيسية" : "Key Risk Indicators", ar);
            renderKris(doc, org, ar);

            // 6. Framework coverage
            doc.newPage();
            renderSectionHeader(doc, ar ? "تغطية الأطر التنظيمية" : "Regulatory Framework Coverage", ar);
            renderFrameworks(doc, org, ar);

            // 7. Change management
            doc.newPage();
            renderSectionHeader(doc, ar ? "إدارة التغيير" : "Change Management", ar);
            renderChanges(doc, org, ar);

            // 8. Ethics
            doc.newPage();
            renderSectionHeader(doc, ar ? "الأخلاقيات والامتثال" : "Ethics & Compliance", ar);
            renderEthics(doc, org, ar);

            // 9. Closing
            doc.newPage();
            renderClosing(doc, ar);

            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new ExportException("PDF", "Board pack PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── Section: Cover ─────────────────────────────────────────────────────
    private void renderCover(Document doc, Organisation o, boolean ar) throws DocumentException {
        // Shared executive cover hero — brand band + eyebrow + bilingual
        // organisation name as the title + tagline + Hijri+Gregorian stamp
        // + Confidential badge. Identical look across BCP/Crisis/NDI/board
        // pack so the platform's printed output reads as one document family.
        PdfTheme.Cover cv = new PdfTheme.Cover();
        cv.eyebrow    = "Quarterly Board Pack";
        cv.eyebrowAr  = "حزمة الإدارة الفصلية";
        cv.titleEn    = o == null ? "—" : o.getNameEn();
        cv.titleAr    = o == null ? null : o.getNameAr();
        cv.subtitle   = "Risk · Compliance · Continuity Snapshot";
        cv.subtitleAr = "نظرة شاملة على المخاطر والامتثال والاستمرارية";
        cv.classification = "Confidential";
        PdfTheme.coverHero(doc, cv);

        Paragraph footnote = new Paragraph(
            ar ? "تم إنشاء هذه الحزمة تلقائيًا بواسطة منصة TSS GRC."
               : "This pack was generated automatically by the TSS GRC Platform.",
            PdfFonts.small(9));
        footnote.setAlignment(Element.ALIGN_CENTER);
        footnote.setSpacingBefore(180);
        doc.add(footnote);
    }

    // ── Section: Executive Summary ──────────────────────────────────────────
    private void renderExecutiveSummary(Document doc, Long org, boolean ar) throws DocumentException {
        long totalRisks    = riskRepo.countByOrganisationId(org);
        long criticalRisks = riskRepo.countByOrganisationIdAndRiskLevel(org, "CRITICAL");
        long openRisks     = riskRepo.countByOrganisationIdAndStatus(org, "OPEN");

        long openFindings  = findingRepo.countOpenByOrganisationId(org);
        long totalFindings = findingRepo.countByOrganisationId(org);

        long totalKris = kriRepo.countByOrganisationId(org);
        long totalControls = controlRepo.countByOrganisationId(org);
        long totalCases = caseRepo.countByOrganisationId(org);

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);

        kpi(t, ar ? "المخاطر" : "Risks",         String.valueOf(totalRisks),    ar ? openRisks + " مفتوحة"  : openRisks + " open", ar);
        kpi(t, ar ? "حرجة"   : "Critical",       String.valueOf(criticalRisks), ar ? "تتطلب اهتمام"        : "Need attention",      ar);
        kpi(t, ar ? "ملاحظات مفتوحة" : "Open Findings", String.valueOf(openFindings),  ar ? "من إجمالي " + totalFindings : "of " + totalFindings + " total", ar);
        kpi(t, ar ? "ضوابط"  : "Controls",       String.valueOf(totalControls), "—", ar);
        kpi(t, ar ? "مؤشرات المخاطر" : "KRIs",  String.valueOf(totalKris),     "—", ar);
        kpi(t, ar ? "قضايا أخلاقيات" : "Ethics Cases", String.valueOf(totalCases), "—", ar);
        kpi(t, ar ? "تقارير STR" : "STRs Filed",   String.valueOf(amlRepo.countByOrganisationIdAndStrFiled(org, true)), "—", ar);
        kpi(t, ar ? "تنبيهات AML" : "AML Alerts", String.valueOf(amlRepo.countByOrganisationId(org)),   "—", ar);

        doc.add(t);
    }

    /** RTL-aware KPI tile. Can't quite share {@link PdfTheme#kpiTile} verbatim
     *  because the board pack flips paragraph alignment per cell when ar=true;
     *  PdfTheme.kpiTile is LTR-only since it serves the per-document exporters
     *  where Arabic stays inline as a second line. */
    private void kpi(PdfPTable t, String label, String value, String sub, boolean ar) {
        PdfPCell c = new PdfPCell();
        c.setPadding(10);
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setBackgroundColor(PdfFonts.PAPER_BG);
        c.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);

        Paragraph l = new Paragraph(label, PdfFonts.small(9));
        l.setAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        c.addElement(l);

        Paragraph v = new Paragraph(value, PdfFonts.kpiValue(20));
        v.setAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        c.addElement(v);

        Paragraph s = new Paragraph(sub, PdfFonts.small(8));
        s.setAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        c.addElement(s);

        t.addCell(c);
    }

    // ── Section: Risk Register top 20 ──────────────────────────────────────
    private void renderRiskRegister(Document doc, Long org, boolean ar) throws DocumentException {
        // Page.getContent() is immutable — copy into a mutable list before sorting
        // (the in-place sort below otherwise throws UnsupportedOperationException).
        List<Risk> risks = new java.util.ArrayList<>(
            riskRepo.findByOrganisationId(org, PageRequest.of(0, 200)).getContent());
        risks.sort(Comparator.comparingInt((Risk r) -> r.getInherentScore() == null ? 0 : r.getInherentScore()).reversed());
        if (risks.size() > 20) risks = risks.subList(0, 20);

        float[] widths = ar ? new float[]{ 1f, 4.5f, 1f, 1f, 1.5f } : new float[]{ 1f, 4.5f, 1f, 1f, 1.5f };
        PdfPTable t = buildTable(widths, ar);
        String[] hdr = ar
            ? new String[]{ "رقم", "المخاطرة", "النتيجة", "المتبقية", "الحالة" }
            : new String[]{ "ID",  "Risk",     "Score",   "Residual", "Status" };
        for (String h : hdr) t.addCell(headerCell(h, ar));
        for (Risk r : risks) {
            String name = ar && r.getTitleAr() != null ? r.getTitleAr() : r.getTitleEn();
            t.addCell(bodyCell("R-" + String.format("%05d", r.getId()), false));
            t.addCell(bodyCell(safe(name), ar));
            t.addCell(bodyCell(String.valueOf(r.getInherentScore() == null ? 0 : r.getInherentScore()), false));
            t.addCell(bodyCell(r.getResidualScore() == null ? "—" : String.valueOf(r.getResidualScore()), false));
            t.addCell(bodyCell(safe(r.getStatus()), false));
        }
        doc.add(t);
    }

    // ── Section: Findings ───────────────────────────────────────────────────
    private void renderFindings(Document doc, Long org, boolean ar) throws DocumentException {
        // Page.getContent() is immutable — copy before the in-place sort below.
        List<AuditFinding> findings = new java.util.ArrayList<>(
            findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent());
        LocalDate today = LocalDate.now();

        long onTrack = 0, mgr = 0, dir = 0, exec = 0;
        for (AuditFinding f : findings) {
            if ("CLOSED".equals(f.getStatus())) continue;
            LocalDateTime created = f.getCreatedAt();
            long days = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
            if (days >= 90) exec++;
            else if (days >= 60) dir++;
            else if (days >= 30) mgr++;
            else onTrack++;
        }
        Paragraph escalation = new Paragraph(
            ar ? "تقادم الملاحظات: في المسار " + onTrack + " · مدير " + mgr + " · مدير عام " + dir + " · تنفيذي " + exec
               : "Aging tiers: On Track " + onTrack + " · Manager " + mgr + " · Director " + dir + " · Exec " + exec,
            PdfFonts.normal(11));
        escalation.setSpacingAfter(10);
        escalation.setAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        doc.add(escalation);

        // Open findings table — top 15 oldest first
        findings.sort(Comparator.comparing((AuditFinding f) -> f.getCreatedAt() == null ? LocalDateTime.MAX : f.getCreatedAt()));
        int rowCount = 0;
        float[] widths = new float[]{ 4f, 1.3f, 1.5f, 1f, 1.3f };
        PdfPTable t = buildTable(widths, ar);
        String[] hdr = ar
            ? new String[]{ "الملاحظة", "الأهمية", "المالك", "الأيام", "الحالة" }
            : new String[]{ "Finding",  "Severity", "Owner",  "Days",   "Status" };
        for (String h : hdr) t.addCell(headerCell(h, ar));
        for (AuditFinding f : findings) {
            if ("CLOSED".equals(f.getStatus())) continue;
            LocalDateTime created = f.getCreatedAt();
            long days = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
            t.addCell(bodyCell(safe(f.getTitle()), ar));
            t.addCell(bodyCell(safe(f.getSeverity()), false));
            t.addCell(bodyCell(safe(f.getOwner()), ar));
            t.addCell(bodyCell(String.valueOf(days), false));
            t.addCell(bodyCell(safe(f.getStatus()), false));
            if (++rowCount >= 15) break;
        }
        doc.add(t);
    }

    // ── Section: KRIs ───────────────────────────────────────────────────────
    private void renderKris(Document doc, Long org, boolean ar) throws DocumentException {
        List<KriDefinition> kris = kriRepo.findByOrganisationIdOrderByCodeAsc(org, PageRequest.of(0, 200)).getContent();

        // QUAL-BCMP-006: one query for each KRI's latest reading instead of a
        // per-KRI N+1 loop. Key present ⇒ a reading exists; value [0]=band (may
        // be null), [1]=readingValue. Mirrors findFirstByKriIdOrderByReadingAtDesc.
        Map<Long, Object[]> latestByKri = new HashMap<>();
        List<Long> kriIds = kris.stream().map(KriDefinition::getId).toList();
        if (!kriIds.isEmpty()) {
            for (Object[] row : readingRepo.findLatestReadingByKriIdIn(kriIds)) {
                latestByKri.put((Long) row[0], new Object[]{ row[1], row[2] });
            }
        }

        long green = 0, amber = 0, red = 0, noData = 0;
        for (KriDefinition k : kris) {
            Object[] latest = latestByKri.get(k.getId());
            String latestBand = latest == null ? null : (String) latest[0];
            if (latestBand == null) noData++;
            else if ("GREEN".equals(latestBand)) green++;
            else if ("AMBER".equals(latestBand)) amber++;
            else if ("RED".equals(latestBand)) red++;
        }
        Paragraph bands = new Paragraph(
            ar ? "نطاقات: أخضر " + green + " · أصفر " + amber + " · أحمر " + red + " · بدون بيانات " + noData
               : "Bands: Green " + green + " · Amber " + amber + " · Red " + red + " · No Data " + noData,
            PdfFonts.normal(11));
        bands.setSpacingAfter(10);
        bands.setAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        doc.add(bands);

        float[] widths = new float[]{ 1.2f, 4f, 1f, 1.2f, 1.2f, 1.4f };
        PdfPTable t = buildTable(widths, ar);
        String[] hdr = ar
            ? new String[]{ "الرمز", "اسم المؤشر", "نطاق", "آخر قراءة", "أخضر ≤", "أصفر ≤" }
            : new String[]{ "Code", "KRI",        "Band",  "Latest",     "Green ≤", "Amber ≤" };
        for (String h : hdr) t.addCell(headerCell(h, ar));
        for (KriDefinition k : kris) {
            Object[] latest = latestByKri.get(k.getId());
            String name = ar && k.getNameAr() != null ? k.getNameAr() : k.getNameEn();
            t.addCell(bodyCell(safe(k.getCode()), false));
            t.addCell(bodyCell(safe(name), ar));
            t.addCell(bodyCell(latest != null ? safe((String) latest[0]) : "—", false));
            t.addCell(bodyCell(latest != null ? ((BigDecimal) latest[1]).toPlainString() : "—", false));
            t.addCell(bodyCell(k.getGreenMax() != null ? k.getGreenMax().toPlainString() : "—", false));
            t.addCell(bodyCell(k.getAmberMax() != null ? k.getAmberMax().toPlainString() : "—", false));
        }
        doc.add(t);
    }

    // ── Section: Framework coverage ─────────────────────────────────────────
    private void renderFrameworks(Document doc, Long org, boolean ar) throws DocumentException {
        List<Framework> frameworks = frameworkRepo.findByIsActiveTrueOrderByFamilyAscCodeAsc();

        float[] widths = new float[]{ 1.3f, 4f, 1f, 1f, 1.3f, 1.3f };
        PdfPTable t = buildTable(widths, ar);
        String[] hdr = ar
            ? new String[]{ "الرمز", "الإطار", "ضوابط", "سياسات", "تغطية كاملة", "ملاحظات مفتوحة" }
            : new String[]{ "Code", "Framework","Ctrls", "Polcs",  "Full %",      "Open Findings" };
        for (String h : hdr) t.addCell(headerCell(h, ar));

        for (Framework f : frameworks) {
            List<PolicyFrameworkMapping> rows = mappingRepo.findByFrameworkId(f.getId());
            long full = 0;
            for (PolicyFrameworkMapping m : rows) {
                if ("FULL".equals(m.getSatisfaction())) full++;
            }
            int policyCount = rows.size();
            long controlCount = controlRepo.countByOrganisationIdAndFrameworkId(org, f.getId());
            long openFw = findingRepo.countOpenByOrganisationIdAndFrameworkId(org, f.getId());
            String fullPct = policyCount == 0 ? "—" : Math.round(((double) full / policyCount) * 100) + "%";

            // Skip frameworks with zero footprint to keep the page readable
            if (policyCount == 0 && controlCount == 0 && openFw == 0) continue;

            t.addCell(bodyCell(safe(f.getCode()), false));
            t.addCell(bodyCell(ar && f.getNameAr() != null ? f.getNameAr() : f.getNameEn(), ar));
            t.addCell(bodyCell(String.valueOf(controlCount), false));
            t.addCell(bodyCell(String.valueOf(policyCount), false));
            t.addCell(bodyCell(fullPct, false));
            t.addCell(bodyCell(String.valueOf(openFw), false));
        }
        doc.add(t);

        // Small-print attribution
        Paragraph note = new Paragraph(
            ar ? "* لا تظهر الأطر الخالية تمامًا من السياسات والضوابط والملاحظات."
               : "* Frameworks with zero policies, controls and findings are omitted.",
            PdfFonts.small(8));
        note.setSpacingBefore(8);
        note.setAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        doc.add(note);
    }

    // ── Section: Change Mgmt ────────────────────────────────────────────────
    private void renderChanges(Document doc, Long org, boolean ar) throws DocumentException {
        long total      = changeRepo.countByOrganisationId(org);
        // QUAL-BCMP-006: one GROUP BY status query instead of five per-status counts.
        Map<String, Long> changeByStatus = new HashMap<>();
        for (Object[] row : changeRepo.countByStatusGrouped(org)) {
            changeByStatus.put((String) row[0], (Long) row[1]);
        }
        long inFlight   = changeByStatus.getOrDefault("IMPLEMENTING", 0L)
                        + changeByStatus.getOrDefault("SCHEDULED", 0L)
                        + changeByStatus.getOrDefault("CAB_PENDING", 0L)
                        + changeByStatus.getOrDefault("IMPACT_ASSESSED", 0L)
                        + changeByStatus.getOrDefault("EMERGENCY_APPROVED", 0L);
        long success    = changeRepo.countByOrganisationIdAndOutcome(org, "SUCCESS");
        long rolledBack = changeRepo.countByOrganisationIdAndOutcome(org, "ROLLED_BACK");
        long emergency  = changeRepo.countByOrganisationIdAndChangeType(org, "EMERGENCY");

        Paragraph p = new Paragraph(
            ar
              ? "إجمالي " + total + " · قيد التنفيذ " + inFlight + " · ناجحة " + success
                + " · تراجع " + rolledBack + " · طارئة " + emergency
              : "Total " + total + " · In-flight " + inFlight + " · Success " + success
                + " · Rolled-back " + rolledBack + " · Emergency " + emergency,
            PdfFonts.normal(11));
        p.setAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        doc.add(p);
    }

    // ── Section: Ethics & Compliance ───────────────────────────────────────
    private void renderEthics(Document doc, Long org, boolean ar) throws DocumentException {
        long total      = caseRepo.countByOrganisationId(org);
        long openCases  = caseRepo.countByOrganisationIdAndStatus(org, "INTAKE")
                        + caseRepo.countByOrganisationIdAndStatus(org, "TRIAGE")
                        + caseRepo.countByOrganisationIdAndStatus(org, "INVESTIGATION");
        long substantiated = caseRepo.countByOrganisationIdAndStatus(org, "SUBSTANTIATED")
                           + caseRepo.countByOrganisationIdAndStatus(org, "REMEDIATION");
        long bribery       = caseRepo.countByOrganisationIdAndCategory(org, "BRIBERY")
                           + caseRepo.countByOrganisationIdAndCategory(org, "KICKBACK");
        long amlSusp       = caseRepo.countByOrganisationIdAndCategory(org, "AML_SUSPICION");
        long retal         = caseRepo.countByOrganisationIdAndCategory(org, "RETALIATION");
        long strs          = amlRepo.countByOrganisationIdAndStrFiled(org, true);

        Paragraph p = new Paragraph(
            ar
              ? "قضايا: " + total + " · مفتوحة " + openCases + " · مُثبتة " + substantiated
                + " · رشاوى/عمولات " + bribery + " · غسل أموال " + amlSusp + " · انتقام " + retal
                + " · STR مُرفعة " + strs
              : "Cases: " + total + " · Open " + openCases + " · Substantiated " + substantiated
                + " · Bribery/Kickback " + bribery + " · AML suspicion " + amlSusp + " · Retaliation " + retal
                + " · STRs filed " + strs,
            PdfFonts.normal(11));
        p.setAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        doc.add(p);
    }

    private void renderClosing(Document doc, boolean ar) throws DocumentException {
        Paragraph p = new Paragraph(
            ar
              ? "تم إعداد هذه الحزمة آليًا من بيانات منصة TSS GRC الحية. "
                + "للاستفسارات حول البيانات الواردة، يرجى التواصل مع مكتب إدارة المخاطر المؤسسية."
              : "This pack is auto-generated from live TSS GRC platform data. "
                + "For questions about any figure, contact the Group ERM Office.",
            PdfFonts.small(10));
        p.setSpacingBefore(40);
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private void renderSectionHeader(Document doc, String title, boolean ar) throws DocumentException {
        Paragraph p = new Paragraph(title, PdfFonts.title(16));
        p.setAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        p.setSpacingAfter(12);
        doc.add(p);
    }

    private PdfPTable buildTable(float[] widths, boolean ar) throws DocumentException {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        return t;
    }

    private PdfPCell headerCell(String text, boolean ar) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.header(10)));
        c.setBackgroundColor(PdfFonts.BRAND_PRIMARY);
        c.setPadding(6);
        c.setHorizontalAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        c.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        c.setBorderColor(PdfFonts.HAIRLINE);
        return c;
    }

    private PdfPCell bodyCell(String text, boolean rtl) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.body(9)));
        c.setPadding(5);
        c.setHorizontalAlignment(rtl ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        c.setRunDirection(rtl ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        return c;
    }

    private static String safe(String s) { return s == null ? "—" : s; }
}
