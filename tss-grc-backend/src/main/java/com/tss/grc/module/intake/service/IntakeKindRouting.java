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
     * @param defaultAssignee  stakeholder code the submission is routed to
     * @param allowsAnonymous  whether an anonymity claim on this kind is <b>honoured</b>. Load
     *                         bearing: a claim on a kind that does not allow it is refused rather
     *                         than obeyed, otherwise any caller could make every submission
     *                         unattributable simply by asking
     */
    public record Routing(
        String codePrefix,
        int slaHours,
        String defaultTarget,
        String defaultAssignee,
        boolean allowsAnonymous
    ) {
    }

    private static final Map<String, Routing> CATALOG = new LinkedHashMap<>();

    private static void put(String kind, String prefix, int slaHours, String target,
                           String assignee, boolean allowsAnonymous) {
        CATALOG.put(kind, new Routing(prefix, slaHours, target, assignee, allowsAnonymous));
    }

    static {
        // ── The original 16 (DD-54). Unchanged — all sixteen already agreed with the frontend. ──
        put("DSR_REQUEST",         "DS", 30 * 24, "DSR",          "DPO",             false);
        put("BREACH_REPORT",       "BR", 72,      "BREACH",       "DPO",             true);
        put("VRP_DISCLOSURE",      "VR", 24 * 5,  "INCIDENT",     "CISO_OFFICE",     true);
        put("SPEAKUP",             "SU", 24 * 7,  "ETHICS_CASE",  "ETHICS_CTE",      true);
        put("SECURITY_INCIDENT",   "SI", 72,      "INCIDENT",     "CSIRT",           false);
        put("PRIVACY_BREACH",      "PB", 72,      "BREACH",       "DPO",             false);
        put("RISK_ISSUE",          "RI", 24 * 14, "RISK",         "RISK_OFFICER",    false);
        put("CONTROL_OBSERVATION", "CO", 24 * 14, "FINDING",      "COMPLIANCE_LEAD", false);
        put("POLICY_EXCEPTION",    "PE", 24 * 7,  "EXCEPTION",    "POLICY_OFFICER",  false);
        put("VENDOR_ONBOARDING",   "VO", 24 * 14, "VENDOR",       "TPRM_LEAD",       false);
        put("DPIA_INITIATION",     "DI", 24 * 14, "DPIA",         "DPO",             false);
        put("BIA_SUBMISSION",      "BI", 24 * 30, "BIA",          "BCM_LEAD",        false);
        put("AUDIT_REQUEST",       "AR", 24 * 14, "ENGAGEMENT",   "AUDIT_LEAD",      false);
        put("CHANGE_REQUEST",      "CR", 48,      "CHANGE",       "CAB",             false);
        put("ETHICS_DISCLOSURE",   "ED", 24 * 14, "ETHICS_CASE",  "ETHICS_OFFICER",  false);
        put("CONTROL_ATTESTATION", "CA", 24 * 14, "CONTROL_TEST", "COMPLIANCE_LEAD", false);

        // ── DD-88: HRSD (Saudi Ministry of Human Resources) workflows. ──
        // Added by F2-172. Every one of these was rejected by the server before it.
        put("SAUDIZATION_REPORT",     "SR", 24 * 5,  "CONTROL_TEST", "HR_LEAD", false);
        put("WORKPLACE_INJURY",       "WI", 24,      "INCIDENT",     "HR_LEAD", false);
        put("GOSI_EMPLOYEE_REGISTER", "GR", 24 * 5,  "CONTROL_TEST", "HR_LEAD", false);
        put("EMPLOYMENT_CONTRACT",    "EC", 24 * 7,  "POLICY",       "HR_LEAD", false);
        put("WPS_PAYROLL_RUN",        "WP", 24 * 11, "CONTROL_TEST", "HR_LEAD", false);

        // ── DD-94: MOC (Ministry of Commerce) workflows. Added by F2-172. ──
        put("TASATTUR_SELF_ASSESSMENT",        "TA", 24 * 30, "CONTROL_TEST", "COMPLIANCE_OFFICER", false);
        put("BENEFICIAL_OWNERSHIP_DISCLOSURE", "BO", 24 * 14, "POLICY",       "COMPLIANCE_OFFICER", false);
        put("CR_ANNUAL_CONFIRMATION",          "CR", 24 * 30, "CONTROL_TEST", "COMPLIANCE_OFFICER", false);
        put("CAPITAL_CHANGE_NOTIFICATION",     "CC", 24 * 15, "POLICY",       "COMPLIANCE_OFFICER", false);
        put("CONSUMER_COMPLAINT_RESPONSE",     "CP", 24 * 5,  "INCIDENT",     "COMPLIANCE_OFFICER", false);
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
}
