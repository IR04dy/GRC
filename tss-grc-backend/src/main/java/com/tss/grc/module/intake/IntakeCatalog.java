package com.tss.grc.module.intake;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The server-side intake catalogue — F2-172.
 *
 * <p>Routing metadata for every {@code IntakeKind}: code prefix, SLA window, default
 * assignee, target register, and the bilingual display name. It was previously three
 * private maps inside {@code IntakeService} covering <b>16</b> of the 26 kinds — the ten
 * HRSD (DD-88) and MOC (DD-94) kinds were missing entirely, so every one of them would
 * have been rejected with "Unknown intake kind" the moment the frontend started posting
 * to the server. Nobody noticed because no product code called the endpoint (F2-171).
 *
 * <p><b>{@code defaultTarget} is the reason this is not just a lookup table.</b> The
 * triage endpoint used to take {@code promotedToEntityType} from the request body, so a
 * client could promote a DSR into the risk register. The target is a property of the
 * kind, and the kind is on the row the server already loaded — so the server decides it
 * and the client cannot express an opinion.
 *
 * <p><b>Generated, not hand-typed</b>, from {@code tss-grc-ui-v2/src/lib/intake.ts}, and
 * pinned to it by {@code IntakeCatalogParityTest}. Two copies of a routing table that
 * only one side can see is how the 16-of-26 gap survived; a copy with a gate is a
 * mirror. Edit the frontend catalogue and regenerate — a hand-edit here fails the build.
 */
public final class IntakeCatalog {

    private IntakeCatalog() {}

    /**
     * @param codePrefix      two letters opening the public reference code
     * @param slaHours        hours from receipt to a mandatory triage decision
     * @param defaultTarget   the register a promotion creates a record in
     * @param defaultAssignee stakeholder code the submission routes to
     * @param titleEn         display name
     * @param titleAr         display name in Arabic — load-bearing, not decoration: it is
     *                        what the promoted record's mandatory {@code title_ar} is built
     *                        from, since intake payloads carry no Arabic
     */
    public record Entry(
        String codePrefix,
        int slaHours,
        String defaultTarget,
        String defaultAssignee,
        String titleEn,
        String titleAr
    ) {}

    private static final Map<String, Entry> ENTRIES = build();

    private static Map<String, Entry> build() {
        Map<String, Entry> m = new LinkedHashMap<>();
        m.put("DSR_REQUEST", new Entry("DS", 720, "DSR", "DPO",
            "Data subject request",
            "طلب صاحب البيانات"));
        m.put("BREACH_REPORT", new Entry("BR", 72, "BREACH", "DPO",
            "Report a privacy breach",
            "الإبلاغ عن خرق خصوصية"));
        m.put("VRP_DISCLOSURE", new Entry("VR", 120, "INCIDENT", "CISO_OFFICE",
            "Vulnerability disclosure",
            "إفصاح عن ثغرة"));
        m.put("SPEAKUP", new Entry("SU", 168, "ETHICS_CASE", "ETHICS_CTE",
            "SpeakUp — anonymous report",
            "تحدّث — بلاغ مجهول"));
        m.put("SECURITY_INCIDENT", new Entry("SI", 72, "INCIDENT", "CSIRT",
            "Report a security event",
            "الإبلاغ عن حدث أمني"));
        m.put("PRIVACY_BREACH", new Entry("PB", 72, "BREACH", "DPO",
            "Report a privacy breach (internal)",
            "الإبلاغ عن خرق خصوصية (داخلي)"));
        m.put("RISK_ISSUE", new Entry("RI", 336, "RISK", "RISK_OFFICER",
            "Raise a risk issue",
            "الإبلاغ عن مخاطرة"));
        m.put("CONTROL_OBSERVATION", new Entry("CO", 336, "FINDING", "COMPLIANCE_LEAD",
            "Report a control failure / observation",
            "الإبلاغ عن خلل في ضابط رقابي"));
        m.put("POLICY_EXCEPTION", new Entry("PE", 168, "EXCEPTION", "POLICY_OFFICER",
            "Request a policy exception",
            "طلب استثناء من سياسة"));
        m.put("VENDOR_ONBOARDING", new Entry("VO", 336, "VENDOR", "TPRM_LEAD",
            "Onboard a new vendor",
            "إدراج مورد جديد"));
        m.put("DPIA_INITIATION", new Entry("DI", 336, "DPIA", "DPO",
            "Initiate a DPIA",
            "بدء تقييم أثر حماية البيانات"));
        m.put("BIA_SUBMISSION", new Entry("BI", 720, "BIA", "BCM_LEAD",
            "Submit BIA for my business unit",
            "تقديم تقييم الأثر للوحدة"));
        m.put("AUDIT_REQUEST", new Entry("AR", 336, "ENGAGEMENT", "AUDIT_LEAD",
            "Request an internal audit",
            "طلب مراجعة داخلية"));
        m.put("CHANGE_REQUEST", new Entry("CR", 48, "CHANGE", "CAB",
            "Submit a change request",
            "تقديم طلب تغيير"));
        m.put("ETHICS_DISCLOSURE", new Entry("ED", 336, "ETHICS_CASE", "ETHICS_OFFICER",
            "Submit a disclosure (COI / gift / outside employment)",
            "تقديم إفصاح (تضارب مصالح / هدية / عمل خارجي)"));
        m.put("CONTROL_ATTESTATION", new Entry("CA", 336, "CONTROL_TEST", "COMPLIANCE_LEAD",
            "Attest to a control / policy",
            "إقرار بضابط/سياسة"));
        m.put("SAUDIZATION_REPORT", new Entry("SR", 120, "CONTROL_TEST", "HR_LEAD",
            "Submit monthly Saudization report",
            "تقديم تقرير السعودة الشهري"));
        m.put("WORKPLACE_INJURY", new Entry("WI", 24, "INCIDENT", "HR_LEAD",
            "Report a workplace injury / occupational illness",
            "الإبلاغ عن إصابة عمل أو مرض مهني"));
        m.put("GOSI_EMPLOYEE_REGISTER", new Entry("GR", 120, "CONTROL_TEST", "HR_LEAD",
            "Register a new employee with GOSI",
            "تسجيل موظف جديد في التأمينات الاجتماعية"));
        m.put("EMPLOYMENT_CONTRACT", new Entry("EC", 168, "POLICY", "HR_LEAD",
            "Draft / renew employment contract",
            "إعداد / تجديد عقد عمل"));
        m.put("WPS_PAYROLL_RUN", new Entry("WP", 264, "CONTROL_TEST", "HR_LEAD",
            "Confirm monthly WPS payroll run",
            "تأكيد تشغيل نظام حماية الأجور الشهري"));
        m.put("TASATTUR_SELF_ASSESSMENT", new Entry("TA", 720, "CONTROL_TEST", "COMPLIANCE_OFFICER",
            "Submit annual Tasattur self-assessment",
            "تقديم التقييم الذاتي السنوي للتستر"));
        m.put("BENEFICIAL_OWNERSHIP_DISCLOSURE", new Entry("BO", 336, "POLICY", "COMPLIANCE_OFFICER",
            "File beneficial-ownership disclosure",
            "تقديم إفصاح المُستفيد الحقيقي"));
        m.put("CR_ANNUAL_CONFIRMATION", new Entry("CR", 720, "CONTROL_TEST", "COMPLIANCE_OFFICER",
            "Confirm annual commercial-registry data",
            "تأكيد البيانات السنوية للسجل التجاري"));
        m.put("CAPITAL_CHANGE_NOTIFICATION", new Entry("CC", 360, "POLICY", "COMPLIANCE_OFFICER",
            "Notify MOC of capital change",
            "إبلاغ وزارة التجارة بتغيّر رأس المال"));
        m.put("CONSUMER_COMPLAINT_RESPONSE", new Entry("CP", 120, "INCIDENT", "COMPLIANCE_OFFICER",
            "Respond to MOC consumer complaint",
            "الردّ على شكوى المستهلك من وزارة التجارة"));
        return Map.copyOf(m);
    }

    /** Every catalogued kind, in frontend declaration order. */
    public static Map<String, Entry> all() {
        return ENTRIES;
    }

    public static Optional<Entry> find(String kind) {
        return Optional.ofNullable(ENTRIES.get(kind));
    }

    /**
     * @throws IllegalArgumentException (→ 400) naming the kind. A submission for an
     *         uncatalogued kind must be refused, not stored with a null SLA: an intake
     *         with no deadline is invisible to the overdue bucket that drives triage.
     */
    public static Entry require(String kind) {
        return find(kind).orElseThrow(() -> new IllegalArgumentException(
            "Unknown intake kind: " + kind));
    }
}
