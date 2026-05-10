// Saudi-aligned risk appetite library — Sprint 4 deliverable.
//
// Reference appetite statements that listed/regulated Saudi entities are
// expected to maintain at board level. Each statement carries a regulator
// citation so an internal auditor or regulator inspection can trace it
// back to the obligation that drove it. This is the localization wedge —
// ServiceNow ships generic appetite templates; we ship the framework
// boards in Riyadh / Jeddah are actually being asked for.
//
// Sources:
//   • CMA Corporate Governance Regulations (Article 71 — risk management
//     committee responsibilities; Article 75 — annual risk-appetite review)
//   • SAMA Risk-Based Supervisory Framework (RBSF) — risk appetite as a
//     pillar of capital adequacy and ICAAP
//   • Vision 2030 sector strategies (financial sector dev'p, NTP)
//   • NCA Essential Cybersecurity Controls (ECC-1:2018) — control 1-1-1
//     mandates a board-approved cybersecurity risk appetite
//   • PDPL Implementing Regulations — DSR & breach response timelines
//   • SAMA Operational Resilience framework — RTO/RPO targets

import type { RiskAppetiteStatementDto } from './api';

export interface SaudiAppetiteExtras {
  /** Regulator code(s) that drove the statement. */
  regulators: string[];
  /** Specific clause / article reference — what an auditor will ask to see. */
  citation: string;
  /** Time horizon — short-term (operational) vs long-term (strategic). */
  horizon: 'SHORT_TERM' | 'MEDIUM_TERM' | 'LONG_TERM';
  /** Vision 2030 alignment marker, optional. */
  vision2030?: string;
  /** ID base — Saudi-derived statements live in the 6000+ range to avoid
   *  collisions with the original demo APPETITES (5101–5107). */
  saudi: true;
}

export type SaudiAppetiteStatement = RiskAppetiteStatementDto & SaudiAppetiteExtras;

export const SAUDI_APPETITES: SaudiAppetiteStatement[] = [
  // ── CMA Corporate Governance — board-level appetite ──────────────────────
  {
    id: 6101, saudi: true,
    titleEn: 'Governance — Whistleblowing Backlog',
    titleAr: 'الحوكمة — تراكم بلاغات المخالفات',
    statementEn: 'Whistleblowing cases must be triaged within 5 working days. Any case open beyond 90 days requires audit-committee review.',
    statementAr: 'يجب فرز بلاغات المخالفات خلال 5 أيام عمل، وأي بلاغ يبقى مفتوحاً لأكثر من 90 يوماً يتطلب مراجعة لجنة المراجعة.',
    riskDimension: 'GOVERNANCE', appetiteLevel: 'AVERSE',
    toleranceLower: 0, toleranceUpper: 0, unit: 'cases > 90 days', currentExposure: 1,
    approvedBy: 'Audit Committee', approvedAt: '2026-01-18T10:00:00', nextReviewDate: '2026-07-18',
    status: 'APPROVED',
    regulators: ['CMA', 'NAZAHA'], citation: 'CMA CGR Art. 71; Nazaha Anti-Corruption Strategy 2030',
    horizon: 'SHORT_TERM',
  },
  {
    id: 6102, saudi: true,
    titleEn: 'Governance — Board Independence',
    titleAr: 'الحوكمة — استقلالية مجلس الإدارة',
    statementEn: 'No more than 50% of board seats may be held by executive or non-independent directors at any time.',
    statementAr: 'يجب ألّا تتجاوز نسبة الأعضاء التنفيذيين أو غير المستقلين 50% من إجمالي مقاعد المجلس.',
    riskDimension: 'GOVERNANCE', appetiteLevel: 'MINIMAL',
    toleranceLower: 0, toleranceUpper: 50, unit: '% non-independent', currentExposure: 33,
    approvedBy: 'Board Chair', approvedAt: '2026-02-01T09:00:00', nextReviewDate: '2027-02-01',
    status: 'APPROVED',
    regulators: ['CMA'], citation: 'CMA CGR Art. 16 — Board composition',
    horizon: 'LONG_TERM',
  },

  // ── SAMA Risk-Based Supervision — capital, liquidity, operational ────────
  {
    id: 6103, saudi: true,
    titleEn: 'Capital — CET1 Buffer Above Regulatory Minimum',
    titleAr: 'رأس المال — هامش CET1 فوق الحد الأدنى التنظيمي',
    statementEn: 'Maintain a CET1 capital ratio of at least 200 bps above the SAMA regulatory minimum of 10.5%.',
    statementAr: 'الإبقاء على نسبة رأس المال الأساسي CET1 أعلى بـ 200 نقطة أساس على الأقل من الحد الأدنى التنظيمي لساما (10.5%).',
    riskDimension: 'FINANCIAL', appetiteLevel: 'CAUTIOUS',
    toleranceLower: 12.5, toleranceUpper: 100, unit: '% CET1', currentExposure: 14.2,
    approvedBy: 'Board Risk Committee', approvedAt: '2026-01-12T11:00:00', nextReviewDate: '2026-07-12',
    status: 'APPROVED',
    regulators: ['SAMA'], citation: 'SAMA Basel III implementation; ICAAP guidelines',
    horizon: 'MEDIUM_TERM',
  },
  {
    id: 6104, saudi: true,
    titleEn: 'Liquidity — LCR Buffer',
    titleAr: 'السيولة — هامش نسبة تغطية السيولة',
    statementEn: 'Liquidity Coverage Ratio must remain above 110% at all times (regulatory floor 100%).',
    statementAr: 'يجب أن تظل نسبة تغطية السيولة فوق 110% دائماً (الحد الأدنى التنظيمي 100%).',
    riskDimension: 'FINANCIAL', appetiteLevel: 'AVERSE',
    toleranceLower: 110, toleranceUpper: 1000, unit: '% LCR', currentExposure: 137,
    approvedBy: 'Treasurer', approvedAt: '2026-02-08T10:00:00', nextReviewDate: '2026-08-08',
    status: 'APPROVED',
    regulators: ['SAMA'], citation: 'SAMA Liquidity Risk Framework — LCR',
    horizon: 'SHORT_TERM',
  },
  {
    id: 6105, saudi: true,
    titleEn: 'Operational Resilience — Tier-0 Service RTO',
    titleAr: 'المرونة التشغيلية — هدف زمن الاستعادة للخدمات من الدرجة صفر',
    statementEn: 'Tier-0 customer-facing services must recover within 2 hours of any disruption.',
    statementAr: 'يجب استعادة الخدمات الحرجة (Tier-0) خلال ساعتين من أي اضطراب.',
    riskDimension: 'OPERATIONAL', appetiteLevel: 'MINIMAL',
    toleranceLower: 0, toleranceUpper: 120, unit: 'minutes RTO', currentExposure: 95,
    approvedBy: 'COO', approvedAt: '2026-01-22T14:00:00', nextReviewDate: '2026-07-22',
    status: 'APPROVED',
    regulators: ['SAMA', 'NCEMA'], citation: 'SAMA Operational Resilience Framework; NCEMA BCP standards',
    horizon: 'SHORT_TERM',
  },

  // ── NCA Cybersecurity ECC ────────────────────────────────────────────────
  {
    id: 6106, saudi: true,
    titleEn: 'Cyber — Critical Vulnerability MTTR',
    titleAr: 'الأمن السيبراني — متوسط زمن معالجة الثغرات الحرجة',
    statementEn: 'Critical vulnerabilities (CVSS ≥ 9.0) on internet-facing systems must be remediated within 7 days of publication.',
    statementAr: 'يجب معالجة الثغرات الحرجة (CVSS ≥ 9.0) في الأنظمة المتصلة بالإنترنت خلال 7 أيام من نشرها.',
    riskDimension: 'CYBER', appetiteLevel: 'AVERSE',
    toleranceLower: 0, toleranceUpper: 7, unit: 'days MTTR', currentExposure: 9,
    approvedBy: 'CISO', approvedAt: '2026-01-15T10:00:00', nextReviewDate: '2026-07-15',
    status: 'APPROVED',
    regulators: ['NCA'], citation: 'NCA ECC-1:2018 control 2-3-1 — Vulnerability management',
    horizon: 'SHORT_TERM',
  },
  {
    id: 6107, saudi: true,
    titleEn: 'Cyber — Phishing Click-Through Rate',
    titleAr: 'الأمن السيبراني — معدل النقر على رسائل التصيّد',
    statementEn: 'Quarterly phishing-simulation click-through rate must remain below 5% organisation-wide.',
    statementAr: 'يجب أن يبقى معدل النقر على محاكاة التصيّد الفصلية أقل من 5% على مستوى المنظمة.',
    riskDimension: 'CYBER', appetiteLevel: 'CAUTIOUS',
    toleranceLower: 0, toleranceUpper: 5, unit: '% click rate', currentExposure: 3.4,
    approvedBy: 'CISO', approvedAt: '2026-02-12T10:00:00', nextReviewDate: '2026-08-12',
    status: 'APPROVED',
    regulators: ['NCA'], citation: 'NCA ECC-1:2018 control 1-5-1 — Cybersecurity awareness',
    horizon: 'SHORT_TERM',
  },

  // ── PDPL Privacy ─────────────────────────────────────────────────────────
  {
    id: 6108, saudi: true,
    titleEn: 'Privacy — Cross-Border Data Transfers Without Adequacy',
    titleAr: 'الخصوصية — نقل البيانات عبر الحدود دون قرار ملاءمة',
    statementEn: 'Zero data transfers to non-adequate jurisdictions without SCCs or explicit data-subject consent.',
    statementAr: 'عدم وجود أي عمليات نقل بيانات إلى دول غير ذات ملاءمة دون عقود نموذجية أو موافقة صريحة من صاحب البيانات.',
    riskDimension: 'PRIVACY', appetiteLevel: 'AVERSE',
    toleranceLower: 0, toleranceUpper: 0, unit: 'flows without basis', currentExposure: 2,
    approvedBy: 'DPO', approvedAt: '2026-02-04T10:00:00', nextReviewDate: '2026-08-04',
    status: 'APPROVED',
    regulators: ['SDAIA'], citation: 'PDPL Implementing Regulations — Cross-border transfer Art. 29',
    horizon: 'SHORT_TERM',
  },

  // ── HRSD / Saudization ───────────────────────────────────────────────────
  {
    id: 6109, saudi: true,
    titleEn: 'HR — Saudization Band Risk',
    titleAr: 'الموارد البشرية — مخاطر نطاق السعودة',
    statementEn: 'Maintain a Saudization rate that keeps the organisation in the Green Mid band or higher of the Nitaqat programme at all times.',
    statementAr: 'الحفاظ على معدل سعودة يُبقي المنظمة في النطاق الأخضر المتوسط أو أعلى من برنامج نطاقات في جميع الأوقات.',
    riskDimension: 'OPERATIONAL', appetiteLevel: 'MINIMAL',
    toleranceLower: 0, toleranceUpper: 0, unit: 'bands below Green-Mid', currentExposure: 0,
    approvedBy: 'CHRO', approvedAt: '2026-01-30T09:00:00', nextReviewDate: '2026-04-30',
    status: 'APPROVED',
    regulators: ['HRSD'], citation: 'HRSD Nitaqat — Quarterly band assessment',
    horizon: 'SHORT_TERM',
    vision2030: 'NTP — Nationalisation of jobs',
  },

  // ── ZATCA / Tax ──────────────────────────────────────────────────────────
  {
    id: 6110, saudi: true,
    titleEn: 'Tax — ZATCA E-Invoicing Reject Rate',
    titleAr: 'الضرائب — معدل رفض الفواتير الإلكترونية لزاتكا',
    statementEn: 'ZATCA e-invoicing (Fatoora) rejections must remain below 0.5% of submitted invoices each month.',
    statementAr: 'يجب أن يبقى معدل رفض الفواتير الإلكترونية (فاتورة) من زاتكا أقل من 0.5% شهرياً.',
    riskDimension: 'COMPLIANCE', appetiteLevel: 'AVERSE',
    toleranceLower: 0, toleranceUpper: 0.5, unit: '% rejected', currentExposure: 0.18,
    approvedBy: 'CFO', approvedAt: '2026-02-20T11:00:00', nextReviewDate: '2026-08-20',
    status: 'APPROVED',
    regulators: ['ZATCA'], citation: 'ZATCA E-Invoicing Resolution — Phase 2 onboarding',
    horizon: 'SHORT_TERM',
  },

  // ── ESG / Vision 2030 ────────────────────────────────────────────────────
  {
    id: 6111, saudi: true,
    titleEn: 'ESG — Scope-1 Emissions Trajectory',
    titleAr: 'الاستدامة — مسار انبعاثات النطاق الأول',
    statementEn: 'Annual reduction in Scope-1 emissions must remain on the trajectory to a 30% cut by 2030 (Vision 2030 Saudi Green Initiative).',
    statementAr: 'يجب أن يبقى التخفيض السنوي لانبعاثات النطاق الأول على مسار خفض 30% بحلول 2030 (مبادرة السعودية الخضراء).',
    riskDimension: 'STRATEGIC', appetiteLevel: 'CAUTIOUS',
    toleranceLower: 0, toleranceUpper: 5, unit: '% off track', currentExposure: 7,
    approvedBy: 'Board ESG Committee', approvedAt: '2025-12-10T10:00:00', nextReviewDate: '2026-06-10',
    status: 'APPROVED',
    regulators: ['CMA'], citation: 'CMA ESG Disclosure Guidelines; Saudi Green Initiative',
    horizon: 'LONG_TERM',
    vision2030: 'Saudi Green Initiative — 30% emissions cut by 2030',
  },

  // ── Third Party concentration ────────────────────────────────────────────
  {
    id: 6112, saudi: true,
    titleEn: 'Third-Party — Cloud Region Concentration',
    titleAr: 'الطرف الثالث — تمركز مناطق السحابة',
    statementEn: 'No more than 60% of regulated workloads may run in a single cloud region; data residency in KSA required for SAMA-supervised data.',
    statementAr: 'يجب ألا تتجاوز نسبة الأعمال الخاضعة للتنظيم 60% في منطقة سحابية واحدة، ومتطلبات بقاء بيانات ساما داخل المملكة.',
    riskDimension: 'THIRD_PARTY', appetiteLevel: 'CAUTIOUS',
    toleranceLower: 0, toleranceUpper: 60, unit: '% in single region', currentExposure: 71,
    approvedBy: 'CIO', approvedAt: '2025-11-15T14:00:00', nextReviewDate: '2026-05-15',
    status: 'APPROVED',
    regulators: ['SAMA', 'CITC'], citation: 'SAMA Cloud Computing Framework; CITC Cloud Cybersecurity Regulatory Framework',
    horizon: 'MEDIUM_TERM',
  },
];

// ─── Severity classification ────────────────────────────────────────────────
//
// Slightly more nuanced than the existing severity() in RiskRegisterPage —
// adds a WARNING tier for "approaching tolerance" so we can flag an
// at-risk statement before it actually breaches.

export type AppetiteSeverity =
  | 'WITHIN_APPETITE'   // current exposure comfortably within tolerance
  | 'WARNING'           // > 80% of the way to the tolerance threshold
  | 'EXCEEDED'          // breach by < 25% of the threshold
  | 'CRITICAL';         // breach by ≥ 25% of the threshold

export function classifyAppetite(s: RiskAppetiteStatementDto): AppetiteSeverity {
  const v = s.currentExposure;
  const upper = s.toleranceUpper;
  const lower = s.toleranceLower;
  if (v == null || upper == null) return 'WITHIN_APPETITE';

  // Some statements are min-bound (e.g. capital ratios, IRR) — treat lower
  // as the floor that must be exceeded.
  const minBound = lower != null && v < lower;
  if (minBound) {
    const gap = lower - v;
    const pct = lower === 0 ? 100 : (gap / lower) * 100;
    return pct >= 25 ? 'CRITICAL' : 'EXCEEDED';
  }

  if (v <= upper) {
    if (upper === 0) return 'WITHIN_APPETITE';
    const consumed = upper === 0 ? 0 : (v / upper) * 100;
    return consumed >= 80 ? 'WARNING' : 'WITHIN_APPETITE';
  }

  const breachPct = upper === 0 ? 100 : ((v - upper) / Math.max(1, upper)) * 100;
  return breachPct >= 25 ? 'CRITICAL' : 'EXCEEDED';
}

export const APPETITE_SEVERITY_TONE: Record<AppetiteSeverity, 'success' | 'info' | 'warning' | 'danger'> = {
  WITHIN_APPETITE: 'success',
  WARNING:         'info',
  EXCEEDED:        'warning',
  CRITICAL:        'danger',
};

export function appetiteSeverityLabel(sev: AppetiteSeverity, lang: 'en' | 'ar'): string {
  const en = { WITHIN_APPETITE: 'Within appetite', WARNING: 'Approaching tolerance', EXCEEDED: 'Tolerance exceeded', CRITICAL: 'Critical breach' };
  const ar = { WITHIN_APPETITE: 'ضمن الشهية', WARNING: 'يقترب من الحد', EXCEEDED: 'تجاوز الحد', CRITICAL: 'تجاوز حرج' };
  return (lang === 'ar' ? ar : en)[sev];
}
