package com.tss.grc.module.intake.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The server's copy of the frontend {@code INTAKE_CATALOG} (tss-grc-ui-v2/src/lib/intake.ts) —
 * F2-172.
 *
 * <h3>Why this class exists at all, rather than four maps in the service</h3>
 *
 * <p>{@code IntakeService} carried {@code SLA_HOURS}, {@code DEFAULT_ASSIGNEE} and
 * {@code CODE_PREFIX} as three parallel {@code Map.ofEntries} literals, and only the first was
 * consulted as the validity gate. So a kind present in {@code SLA_HOURS} and absent from the other
 * two did not fail: {@code CODE_PREFIX.getOrDefault(kind, "II")} minted a reference code under a
 * prefix that means nothing, and {@code DEFAULT_ASSIGNEE.get(kind)} routed the submission to
 * {@code null}. Three literals that must agree, with one of them as the gate, is a shape where two
 * of the three can be wrong invisibly. One record per kind makes that unrepresentable — a kind is
 * either fully described or absent.
 *
 * <h3>The divergence this closes, and why nothing caught it</h3>
 *
 * <p>Measured 2026-08-09: the frontend catalogue held <b>26</b> kinds and the server held
 * <b>16</b>. The ten it lacked are the DD-88 HRSD and DD-94 MOC workflows, every one of which
 * {@code submit()} would have rejected with {@code "Unknown intake kind"} — including the
 * <b>24-hour GOSI workplace-injury notification</b> and the <b>WPS payroll confirmation</b>, both
 * statutory Saudi obligations. {@code IntakeService}'s own javadoc said the metadata "is mirrored
 * from frontend INTAKE_CATALOG so the two stay in sync". It was mirrored <em>once</em>; the
 * frontend then grew by ten kinds and nothing asked. Prose is not a control — the control is
 * {@code IntakeCatalogueParityTest}, which reads the TypeScript and fails on any disagreement in
 * either direction, on any of the five fields.
 *
 * <p>⚠ This mattered only because nothing had ever called the server. Had the client been wired to
 * it, 10 of 26 forms would have 400'd on submit, and the two that carry a statutory clock would
 * have failed silently at the moment they were needed. It is the reason the parity check landed
 * <em>before</em> the rewire rather than after it.
 *
 * @see IntakeService the only consumer
 */
public final class IntakeKindRouting {

    private IntakeKindRouting() {
    }

    /**
     * Routing metadata for one intake kind. Every field is mirrored from the frontend catalogue
     * entry of the same name and pinned by {@code IntakeCatalogueParityTest}.
     *
     * @param codePrefix       two-letter prefix for the human-facing reference code
     * @param slaHours         hours from submission to a mandatory triage decision
     * @param defaultTarget    the register a promotion lands in. <b>Server-derived, never taken
     *                         from the request body</b> — see {@code IntakeService#triage}
     * @param defaultAssignee  who the submission is routed to, as a <b>role name from
     *                         {@code iam.roles}</b> — the value written to
     *                         {@code intake.intake_records.assigned_to}. F2-174: ten of the
     *                         original fourteen codes matched no row in the platform's own role
     *                         catalogue ({@code CISO_OFFICE}, {@code ETHICS_CTE}, {@code CSIRT},
     *                         {@code RISK_OFFICER}, {@code COMPLIANCE_LEAD},
     *                         {@code POLICY_OFFICER}, {@code TPRM_LEAD}, {@code BCM_LEAD},
     *                         {@code AUDIT_LEAD}, {@code CAB}), so <em>no tenant administrator
     *                         could make them reach anybody</em> — a free-text label dressed as
     *                         routing. Pinned against the catalogue by
     *                         {@code IntakeAssigneeRoleResolutionTest}. ⚠ Naming a real role is
     *                         not the same as reaching a person: nothing resolves this column yet,
     *                         and several of these roles have no active holder in dev or prod
     * @param allowsAnonymous  whether an anonymity claim on this kind is <b>honoured</b>. Load
     *                         bearing: a claim on a kind that does not allow it is refused rather
     *                         than obeyed, otherwise any caller could make every submission
     *                         unattributable simply by asking
     * @param titleEn          the catalogue's English display name
     * @param titleAr          the same in Arabic. Also load-bearing, and for a reason outside this
     *                         class: it is what a promoted register record's <b>mandatory</b>
     *                         {@code title_ar} is composed from ({@code IntakePromotionContext
     *                         #titleAr}), because intake payloads carry no Arabic. Without it the
     *                         only way to satisfy the bilingual rule is to copy the English text
     *                         into an Arabic column, which satisfies the constraint and lies to
     *                         the reader
     */
    public record Routing(
        String codePrefix,
        int slaHours,
        String defaultTarget,
        String defaultAssignee,
        boolean allowsAnonymous,
        String titleEn,
        String titleAr
    ) {
    }

    private static final Map<String, Routing> CATALOG = new LinkedHashMap<>();

    private static void put(String kind, String prefix, int slaHours, String target,
                           String assignee, boolean allowsAnonymous, String titleEn, String titleAr) {
        CATALOG.put(kind, new Routing(prefix, slaHours, target, assignee, allowsAnonymous,
            titleEn, titleAr));
    }

    static {
        // ── The original 16 (DD-54). Unchanged — all sixteen already agreed with the frontend. ──
        put("DSR_REQUEST",         "DS", 30 * 24, "DSR",          "DPO",                false,
            "Data subject request", "طلب صاحب البيانات");
        put("BREACH_REPORT",       "BR", 72,      "BREACH",       "DPO",                true,
            "Report a privacy breach", "الإبلاغ عن خرق خصوصية");
        put("VRP_DISCLOSURE",      "VR", 24 * 5,  "INCIDENT",     "CISO",               true,
            "Vulnerability disclosure", "إفصاح عن ثغرة");
        put("SPEAKUP",             "SU", 24 * 7,  "ETHICS_CASE",  "ETHICS_OFFICER",     true,
            "SpeakUp — anonymous report", "تحدّث — بلاغ مجهول");
        put("SECURITY_INCIDENT",   "SI", 72,      "INCIDENT",     "IR_LEAD",            false,
            "Report a security event", "الإبلاغ عن حدث أمني");
        put("PRIVACY_BREACH",      "PB", 72,      "BREACH",       "DPO",                false,
            "Report a privacy breach (internal)", "الإبلاغ عن خرق خصوصية (داخلي)");
        put("RISK_ISSUE",          "RI", 24 * 14, "RISK",         "RISK_MANAGER",       false,
            "Raise a risk issue", "الإبلاغ عن مخاطرة");
        put("CONTROL_OBSERVATION", "CO", 24 * 14, "FINDING",      "COMPLIANCE_OFFICER", false,
            "Report a control failure / observation", "الإبلاغ عن خلل في ضابط رقابي");
        put("POLICY_EXCEPTION",    "PE", 24 * 7,  "EXCEPTION",    "COMPLIANCE_OFFICER", false,
            "Request a policy exception", "طلب استثناء من سياسة");
        put("VENDOR_ONBOARDING",   "VO", 24 * 14, "VENDOR",       "SUPPLIER_MANAGER",   false,
            "Onboard a new vendor", "إدراج مورد جديد");
        put("DPIA_INITIATION",     "DI", 24 * 14, "DPIA",         "DPO",                false,
            "Initiate a DPIA", "بدء تقييم أثر حماية البيانات");
        put("BIA_SUBMISSION",      "BI", 24 * 30, "BIA",          "BCM_MANAGER",        false,
            "Submit BIA for my business unit", "تقديم تقييم الأثر للوحدة");
        put("AUDIT_REQUEST",       "AR", 24 * 14, "ENGAGEMENT",   "INTERNAL_AUDITOR",   false,
            "Request an internal audit", "طلب مراجعة داخلية");
        put("CHANGE_REQUEST",      "CR", 48,      "CHANGE",       "CHANGE_MANAGER",     false,
            "Submit a change request", "تقديم طلب تغيير");
        put("ETHICS_DISCLOSURE",   "ED", 24 * 14, "ETHICS_CASE",  "ETHICS_OFFICER",     false,
            "Submit a disclosure (COI / gift / outside employment)",
            "تقديم إفصاح (تضارب مصالح / هدية / عمل خارجي)");
        put("CONTROL_ATTESTATION", "CA", 24 * 14, "CONTROL_TEST", "COMPLIANCE_OFFICER", false,
            "Attest to a control / policy", "إقرار بضابط/سياسة");

        // ── DD-88: HRSD (Saudi Ministry of Human Resources) workflows. ──
        // Added by F2-172. Every one of these was rejected by the server before it.
        put("SAUDIZATION_REPORT",     "SR", 24 * 5,  "CONTROL_TEST", "HR_LEAD",            false,
            "Submit monthly Saudization report", "تقديم تقرير السعودة الشهري");
        put("WORKPLACE_INJURY",       "WI", 24,      "INCIDENT",     "HR_LEAD",            false,
            "Report a workplace injury / occupational illness",
            "الإبلاغ عن إصابة عمل أو مرض مهني");
        put("GOSI_EMPLOYEE_REGISTER", "GR", 24 * 5,  "CONTROL_TEST", "HR_LEAD",            false,
            "Register a new employee with GOSI", "تسجيل موظف جديد في التأمينات الاجتماعية");
        put("EMPLOYMENT_CONTRACT",    "EC", 24 * 7,  "POLICY",       "HR_LEAD",            false,
            "Draft / renew employment contract", "إعداد / تجديد عقد عمل");
        put("WPS_PAYROLL_RUN",        "WP", 24 * 11, "CONTROL_TEST", "HR_LEAD",            false,
            "Confirm monthly WPS payroll run", "تأكيد تشغيل نظام حماية الأجور الشهري");

        // ── DD-94: MOC (Ministry of Commerce) workflows. Added by F2-172. ──
        put("TASATTUR_SELF_ASSESSMENT",        "TA", 24 * 30, "CONTROL_TEST", "COMPLIANCE_OFFICER", false,
            "Submit annual Tasattur self-assessment", "تقديم التقييم الذاتي السنوي للتستر");
        put("BENEFICIAL_OWNERSHIP_DISCLOSURE", "BO", 24 * 14, "POLICY",       "COMPLIANCE_OFFICER", false,
            "File beneficial-ownership disclosure", "تقديم إفصاح المُستفيد الحقيقي");
        put("CR_ANNUAL_CONFIRMATION",          "CR", 24 * 30, "CONTROL_TEST", "COMPLIANCE_OFFICER", false,
            "Confirm annual commercial-registry data", "تأكيد البيانات السنوية للسجل التجاري");
        put("CAPITAL_CHANGE_NOTIFICATION",     "CC", 24 * 15, "POLICY",       "COMPLIANCE_OFFICER", false,
            "Notify MOC of capital change", "إبلاغ وزارة التجارة بتغيّر رأس المال");
        put("CONSUMER_COMPLAINT_RESPONSE",     "CP", 24 * 5,  "INCIDENT",     "COMPLIANCE_OFFICER", false,
            "Respond to MOC consumer complaint", "الردّ على شكوى المستهلك من وزارة التجارة");
    }

    /** Every kind the server accepts. Unmodifiable; iteration order is declaration order. */
    public static Set<String> kinds() {
        return java.util.Collections.unmodifiableSet(CATALOG.keySet());
    }

    /** @return the routing for {@code kind}, or {@code null} when the kind is not catalogued. */
    public static Routing get(String kind) {
        return kind == null ? null : CATALOG.get(kind);
    }

    public static boolean isKnown(String kind) {
        return get(kind) != null;
    }

    /** Every entry, in frontend declaration order — for the parity and promotion-contract tests. */
    public static Map<String, Routing> all() {
        return java.util.Collections.unmodifiableMap(CATALOG);
    }

    /**
     * The routing for a kind that must be present.
     *
     * @throws IllegalArgumentException (→ 400) naming the kind. A submission for an uncatalogued
     *         kind must be refused, not stored with a null SLA: an intake with no deadline is
     *         invisible to the overdue bucket that drives triage.
     */
    public static Routing require(String kind) {
        Routing r = get(kind);
        if (r == null) throw new IllegalArgumentException("Unknown intake kind: " + kind);
        return r;
    }
}
