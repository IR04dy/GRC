// Auditor Register — audit-firm + lead-partner profiles with the
// rotation-clock, independence flags and non-audit-services tracker
// that Saudi regulators (SAMA + CMA) explicitly require.
//
// Standards anchor:
//   SAMA Banking — external auditor lead-partner rotation every 7 years,
//     firm rotation every 11 years (SAMA Banking Inspection Manual)
//   CMA Resolution 8-127-2016 — listed-entity external auditor rotation
//     every 5 years (lead engagement partner)
//   ISA 220 (Quality Mgmt for Audit Engagements)
//   IFAC Code of Ethics §540 (Long Association)
//   IFAC §600 (non-audit services + independence)
//   SOX §201 (prohibited non-audit services for issuer audits)
//   IIA IPPF 1100 (independence + objectivity for internal auditors)
//   ServiceNow IRM — `auditor` table with rotation flags

export type AuditorRole =
  | 'INTERNAL'              // Internal audit team member
  | 'EXTERNAL_FINANCIAL'    // External financial auditor (CMA / SOCPA)
  | 'EXTERNAL_REGULATORY'   // Regulator-mandated audit (SAMA, NCA)
  | 'EXTERNAL_QSA'          // PCI DSS QSA
  | 'EXTERNAL_CERTIFICATION'; // ISO 27001 / 22301 / SOC 2 etc.

export type IndependenceFlag = 'CLEAN' | 'NON_AUDIT_SERVICES' | 'CONFLICT_DISCLOSED' | 'INDEPENDENCE_BREACH';

export interface AuditorProfile {
  /** Stable code joined to plan rows + appointments. */
  code: string;
  firmEn: string;
  firmAr: string;
  /** Lead engagement-partner name (per ISA 220 + CMA Res 8-127). */
  leadPartner: string;
  /** Yyyy-mm-dd the lead partner started on this engagement. */
  leadPartnerSince: string;
  /** Yyyy-mm-dd the firm started auditing the entity. */
  firmSince: string;
  role: AuditorRole;
  /** Country of registration. */
  country: string;
  /** SOCPA / SAMA / professional license reference. */
  licenseRef?: string;
  /** Independence assessment flag — drives the warning chip. */
  independence: IndependenceFlag;
  /** Has the firm provided non-audit (consulting) services in the
      same entity in the last 12 months? Triggers IFAC §600 review. */
  nonAuditServices?: string[];
  /** Free-form note attached to the most-recent independence review. */
  noteEn?: string;
  noteAr?: string;
}

// ── Rotation rules ─────────────────────────────────────────────────────────
// Returns the maximum allowed years for the given auditor role.

export interface RotationRule {
  partnerYears: number;
  firmYears?: number;
  /** Citation for the rule. */
  ref: string;
}

export const ROTATION_RULES: Record<AuditorRole, RotationRule> = {
  EXTERNAL_FINANCIAL:    { partnerYears: 5,  firmYears: 11, ref: 'CMA Res 8-127-2016 + SAMA Banking Inspection Manual' },
  EXTERNAL_REGULATORY:   { partnerYears: 7,  firmYears: 11, ref: 'SAMA Banking Inspection Manual' },
  EXTERNAL_CERTIFICATION:{ partnerYears: 7,                 ref: 'IFAC §540 (long association)' },
  EXTERNAL_QSA:          { partnerYears: 3,                 ref: 'PCI SSC QSA rotation guidance' },
  INTERNAL:              { partnerYears: 5,                 ref: 'IIA IPPF 1130 (impairment) — rotation good practice' },
};

// ── Demo data ─────────────────────────────────────────────────────────────
export const AUDITORS: AuditorProfile[] = [
  // External financial — CMA-listed audit (5y partner / 11y firm)
  { code: 'PWC',     firmEn: 'PwC Saudi Arabia',           firmAr: 'برايس ووترهاوس كوبرز السعودية',
    leadPartner: 'Sarah Mitchell',  leadPartnerSince: '2022-06-01', firmSince: '2018-04-01',
    role: 'EXTERNAL_FINANCIAL', country: 'Saudi Arabia',
    licenseRef: 'SOCPA-PWC-2024', independence: 'CLEAN',
    noteEn: 'Annual independence confirmation received 2025-12-30.' },

  // External regulatory — SAMA mandate (7y partner / 11y firm)
  { code: 'EY',      firmEn: 'EY MENA',                    firmAr: 'إرنست ويونغ الشرق الأوسط',
    leadPartner: 'Khaled Ahmad',    leadPartnerSince: '2020-03-15', firmSince: '2016-02-20',
    role: 'EXTERNAL_REGULATORY', country: 'Saudi Arabia',
    licenseRef: 'SOCPA-EY-2024', independence: 'NON_AUDIT_SERVICES',
    nonAuditServices: ['Tax advisory (ZATCA filings)', 'Cyber readiness consulting (2024)'],
    noteEn: 'Two non-audit services declared; IFAC §600 review concluded permissible — services pre-date current SAMA-mandate engagement.' },

  // ISO 27001 certification — IFAC long-association rule (7y partner)
  { code: 'KPMG',    firmEn: 'KPMG Middle East',           firmAr: 'كي بي إم جي الشرق الأوسط',
    leadPartner: 'David Frost',     leadPartnerSince: '2019-01-01', firmSince: '2015-06-01',
    role: 'EXTERNAL_CERTIFICATION', country: 'Saudi Arabia',
    licenseRef: 'SOCPA-KPMG-2024', independence: 'CLEAN' },

  // PDPL external review
  { code: 'DELOITTE', firmEn: 'Deloitte Middle East',       firmAr: 'ديلويت الشرق الأوسط',
    leadPartner: 'Layan Kheir',     leadPartnerSince: '2024-02-08', firmSince: '2024-02-08',
    role: 'EXTERNAL_REGULATORY', country: 'Saudi Arabia',
    licenseRef: 'SOCPA-DELOITTE-2024', independence: 'CLEAN',
    noteEn: 'New engagement — fresh independence assessment on appointment.' },

  // PCI DSS QSA (3y partner rotation)
  { code: 'ATT-QSA', firmEn: 'AT&T Cybersecurity (QSA)',   firmAr: 'AT&T للأمن السيبراني (QSA)',
    leadPartner: 'Roberto Esposito', leadPartnerSince: '2023-11-04', firmSince: '2021-03-10',
    role: 'EXTERNAL_QSA', country: 'United States',
    licenseRef: 'PCI-SSC-QSA-2024-A1234', independence: 'CLEAN' },

  // ISO 22301 cert
  { code: 'BSI',     firmEn: 'BSI Group',                  firmAr: 'مجموعة BSI',
    leadPartner: 'Helen Carpenter',  leadPartnerSince: '2024-08-01', firmSince: '2022-05-10',
    role: 'EXTERNAL_CERTIFICATION', country: 'United Kingdom',
    licenseRef: 'UKAS-FS-12345', independence: 'CLEAN' },

  // Internal audit team
  { code: 'INT-AUD', firmEn: 'Internal Audit (in-house)',   firmAr: 'المراجعة الداخلية (داخلي)',
    leadPartner: 'Mona Al-Bishi',   leadPartnerSince: '2025-04-08', firmSince: '2018-01-01',
    role: 'INTERNAL', country: 'Saudi Arabia',
    licenseRef: 'CIA + CISA',  independence: 'CLEAN',
    noteEn: 'Reports functionally to Audit Committee, administratively to CEO — IIA IPPF 1110 dual-reporting line.' },

  // Borderline case — independence concern
  { code: 'BDO',     firmEn: 'BDO Saudi Arabia',           firmAr: 'BDO السعودية',
    leadPartner: 'Saud Al-Otaibi',  leadPartnerSince: '2014-09-01', firmSince: '2014-09-01',
    role: 'EXTERNAL_FINANCIAL', country: 'Saudi Arabia',
    licenseRef: 'SOCPA-BDO-2024', independence: 'INDEPENDENCE_BREACH',
    nonAuditServices: ['IT-implementation services (live, $1.2M, 2025)'],
    noteEn: 'Lead partner has been on this engagement >11 years (2014→) — exceeds CMA Res 8-127 5-year cap. Active IT-implementation work simultaneously breaches IFAC §600. Mandatory rotation + service termination required.' },
];

// ── Helpers ────────────────────────────────────────────────────────────────

export const INDEPENDENCE_TONE: Record<IndependenceFlag, 'success' | 'warning' | 'accent' | 'danger'> = {
  CLEAN: 'success',
  NON_AUDIT_SERVICES: 'warning',
  CONFLICT_DISCLOSED: 'accent',
  INDEPENDENCE_BREACH: 'danger',
};

export const INDEPENDENCE_LABEL: Record<IndependenceFlag, { en: string; ar: string }> = {
  CLEAN:                { en: 'Clean',                 ar: 'سليم' },
  NON_AUDIT_SERVICES:   { en: 'Non-audit services',    ar: 'خدمات غير تدقيقية' },
  CONFLICT_DISCLOSED:   { en: 'Conflict disclosed',    ar: 'تضارب مُفصَح عنه' },
  INDEPENDENCE_BREACH:  { en: 'Independence breach',   ar: 'إخلال بالاستقلالية' },
};

export const ROLE_LABEL: Record<AuditorRole, { en: string; ar: string }> = {
  INTERNAL:               { en: 'Internal',                  ar: 'داخلي' },
  EXTERNAL_FINANCIAL:     { en: 'External — Financial',      ar: 'خارجي — مالي' },
  EXTERNAL_REGULATORY:    { en: 'External — Regulatory',     ar: 'خارجي — تنظيمي' },
  EXTERNAL_QSA:           { en: 'External — QSA',            ar: 'خارجي — QSA' },
  EXTERNAL_CERTIFICATION: { en: 'External — Certification',  ar: 'خارجي — شهادة' },
};

/** Years the lead partner has been on this engagement. */
export function yearsAsLeadPartner(a: AuditorProfile, now = new Date()): number {
  const years = (now.getTime() - new Date(a.leadPartnerSince).getTime()) / (365.25 * 86_400_000);
  return Math.round(years * 10) / 10;
}

export function yearsAsFirm(a: AuditorProfile, now = new Date()): number {
  const years = (now.getTime() - new Date(a.firmSince).getTime()) / (365.25 * 86_400_000);
  return Math.round(years * 10) / 10;
}

export type RotationStatus =
  | 'WITHIN_LIMITS'        // both partner + firm within mandated rotation periods
  | 'PARTNER_DUE_SOON'     // partner rotation due within 12 months
  | 'PARTNER_OVERDUE'      // partner is past the cap
  | 'FIRM_DUE_SOON'        // firm rotation due within 12 months
  | 'FIRM_OVERDUE';        // firm is past the cap

export function rotationStatus(a: AuditorProfile, now = new Date()): {
  status: RotationStatus; partnerYears: number; firmYears: number; rule: RotationRule;
} {
  const rule = ROTATION_RULES[a.role];
  const partnerYears = yearsAsLeadPartner(a, now);
  const firmYears    = yearsAsFirm(a, now);

  if (rule.firmYears != null && firmYears > rule.firmYears) {
    return { status: 'FIRM_OVERDUE',    partnerYears, firmYears, rule };
  }
  if (partnerYears > rule.partnerYears) {
    return { status: 'PARTNER_OVERDUE', partnerYears, firmYears, rule };
  }
  if (rule.firmYears != null && firmYears > rule.firmYears - 1) {
    return { status: 'FIRM_DUE_SOON',    partnerYears, firmYears, rule };
  }
  if (partnerYears > rule.partnerYears - 1) {
    return { status: 'PARTNER_DUE_SOON', partnerYears, firmYears, rule };
  }
  return { status: 'WITHIN_LIMITS', partnerYears, firmYears, rule };
}

export const ROTATION_TONE: Record<RotationStatus, 'success' | 'warning' | 'danger'> = {
  WITHIN_LIMITS:    'success',
  PARTNER_DUE_SOON: 'warning',
  FIRM_DUE_SOON:    'warning',
  PARTNER_OVERDUE:  'danger',
  FIRM_OVERDUE:     'danger',
};

export const ROTATION_LABEL: Record<RotationStatus, { en: string; ar: string }> = {
  WITHIN_LIMITS:    { en: 'Within rotation limits',  ar: 'ضمن حدود التدوير' },
  PARTNER_DUE_SOON: { en: 'Partner rotation due',    ar: 'تدوير الشريك مستحقّ قريباً' },
  PARTNER_OVERDUE:  { en: 'Partner OVERDUE',         ar: 'تدوير الشريك متأخّر' },
  FIRM_DUE_SOON:    { en: 'Firm rotation due',       ar: 'تدوير المكتب مستحقّ قريباً' },
  FIRM_OVERDUE:     { en: 'Firm OVERDUE',            ar: 'تدوير المكتب متأخّر' },
};
