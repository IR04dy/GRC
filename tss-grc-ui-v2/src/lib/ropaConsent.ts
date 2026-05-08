// ROPA + Consent management — Sprint 6 follow-on (B2).
//
// PDPL Art. 31 requires controllers to maintain a Record of Processing
// Activities (ROPA) — the "what data, why, with whom, for how long, on
// what legal basis" register every Saudi DPO is asked to produce in an
// SDAIA inspection. Today most teams keep this in Excel + Visio and
// merge once a year for the regulator.
//
// This module makes ROPA + consent first-class:
//   • ROPA entries with bilingual purpose, lawful basis, retention,
//     cross-border transfer flag, sub-processors, source asset
//   • Consent records keyed by data subject + purpose + channel with
//     granted/withdrawn status, version of the consent text accepted
//   • Consent-cycle metrics (active, withdrawn, expired)
//
// The asset spine (Sprint 5) provides the upstream source for each ROPA
// row — change asset residency and the ROPA cross-border flag flips
// automatically.

import { ASSETS, type Asset } from './assets';

export type LawfulBasis =
  | 'CONSENT'
  | 'CONTRACT'
  | 'LEGAL_OBLIGATION'
  | 'VITAL_INTEREST'
  | 'PUBLIC_TASK'
  | 'LEGITIMATE_INTEREST';

export type RopaCategory =
  | 'IDENTIFICATION'
  | 'CONTACT'
  | 'FINANCIAL'
  | 'HEALTH'
  | 'BEHAVIOURAL'
  | 'LOCATION'
  | 'EMPLOYMENT'
  | 'BIOMETRIC'
  | 'SPECIAL_CATEGORY';

export type DataSubjectGroup =
  | 'CUSTOMERS'
  | 'EMPLOYEES'
  | 'CONTRACTORS'
  | 'PROSPECTS'
  | 'WEBSITE_VISITORS'
  | 'JOB_APPLICANTS'
  | 'BENEFICIARIES';

export interface RopaEntry {
  /** Internal code, e.g. "ROPA-001". */
  code: string;
  activityNameEn: string;
  activityNameAr: string;
  purposeEn: string;
  purposeAr: string;
  lawfulBasis: LawfulBasis;
  /** Categories of personal data. */
  categories: RopaCategory[];
  /** Data subject groups affected. */
  dataSubjects: DataSubjectGroup[];
  /** Source asset — the system where this processing happens. */
  sourceAssetCode: string;
  /** Sub-processors / shared-with parties. */
  subProcessors: string[];
  /** Retention period in days. */
  retentionDays: number;
  /** Cross-border transfer? Auto-derived from asset residency. */
  crossBorder: boolean;
  /** Last review date — annual at minimum per PDPL IR. */
  lastReviewedAt: string;
  /** Owner email — usually the business unit lead, not DPO. */
  ownerEmail: string;
}

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString().slice(0, 10);

export const ROPA_ENTRIES: RopaEntry[] = [
  {
    code: 'ROPA-001',
    activityNameEn: 'Customer onboarding & KYC',
    activityNameAr: 'استلام العملاء والتحقّق من الهوية',
    purposeEn: 'Establish customer relationship + comply with SAMA AML/KYC obligations.',
    purposeAr: 'تأسيس علاقة عميل + الامتثال لمتطلّبات ساما لمكافحة غسل الأموال واعرف عميلك.',
    lawfulBasis: 'LEGAL_OBLIGATION',
    categories: ['IDENTIFICATION', 'CONTACT', 'FINANCIAL'],
    dataSubjects: ['CUSTOMERS'],
    sourceAssetCode: 'DB-PRIMARY',
    subProcessors: ['Elm Tabadul (Yakeen identity)', 'Saudi Credit Bureau (SIMAH)'],
    retentionDays: 365 * 10,
    crossBorder: false,
    lastReviewedAt: _daysAgo(45),
    ownerEmail: 'compliance.lead@tss.sa',
  },
  {
    code: 'ROPA-002',
    activityNameEn: 'Online / mobile authentication',
    activityNameAr: 'مصادقة الخدمات الإلكترونية والجوّال',
    purposeEn: 'Authenticate user identity for online + mobile banking sessions.',
    purposeAr: 'مصادقة هوية المستخدم لجلسات الخدمات المصرفية الإلكترونية والجوّال.',
    lawfulBasis: 'CONTRACT',
    categories: ['IDENTIFICATION', 'BEHAVIOURAL'],
    dataSubjects: ['CUSTOMERS'],
    sourceAssetCode: 'INF-LDAP',
    subProcessors: ['Nafath SSO'],
    retentionDays: 365 * 7,
    crossBorder: false,
    lastReviewedAt: _daysAgo(30),
    ownerEmail: 'iam.team@tss.sa',
  },
  {
    code: 'ROPA-003',
    activityNameEn: 'Payment processing',
    activityNameAr: 'معالجة المدفوعات',
    purposeEn: 'Authorise + settle card and account-to-account payments via SAMA-licensed rails.',
    purposeAr: 'تفويض وتسوية مدفوعات البطاقات وبين الحسابات عبر شبكات مرخَّصة من ساما.',
    lawfulBasis: 'CONTRACT',
    categories: ['FINANCIAL', 'BEHAVIOURAL'],
    dataSubjects: ['CUSTOMERS'],
    sourceAssetCode: 'APP-PAYMENT-GW',
    subProcessors: ['mada / SARIE / SADAD', 'Visa / Mastercard'],
    retentionDays: 365 * 7,
    crossBorder: true,
    lastReviewedAt: _daysAgo(15),
    ownerEmail: 'payments.lead@tss.sa',
  },
  {
    code: 'ROPA-004',
    activityNameEn: 'Marketing communications',
    activityNameAr: 'الاتصالات التسويقية',
    purposeEn: 'Send marketing communications to customers who have opted in.',
    purposeAr: 'إرسال اتصالات تسويقية للعملاء الذين أبدوا موافقتهم.',
    lawfulBasis: 'CONSENT',
    categories: ['CONTACT', 'BEHAVIOURAL'],
    dataSubjects: ['CUSTOMERS', 'PROSPECTS'],
    sourceAssetCode: 'DB-WAREHOUSE',
    subProcessors: ['SendGrid', 'WhatsApp Business'],
    retentionDays: 365 * 2,
    crossBorder: true,
    lastReviewedAt: _daysAgo(60),
    ownerEmail: 'marketing.lead@tss.sa',
  },
  {
    code: 'ROPA-005',
    activityNameEn: 'Employee payroll (WPS)',
    activityNameAr: 'كشوف رواتب الموظفين (نظام حماية الأجور)',
    purposeEn: 'Process payroll + comply with HRSD WPS reporting + GOSI contributions.',
    purposeAr: 'تشغيل كشوف الرواتب + الامتثال لتقارير نظام حماية الأجور + اشتراكات التأمينات.',
    lawfulBasis: 'LEGAL_OBLIGATION',
    categories: ['IDENTIFICATION', 'EMPLOYMENT', 'FINANCIAL'],
    dataSubjects: ['EMPLOYEES'],
    sourceAssetCode: 'DB-PRIMARY',
    subProcessors: ['Mudad / WPS', 'GOSI'],
    retentionDays: 365 * 10,
    crossBorder: false,
    lastReviewedAt: _daysAgo(20),
    ownerEmail: 'hr.payroll@tss.sa',
  },
  {
    code: 'ROPA-006',
    activityNameEn: 'Email + collaboration',
    activityNameAr: 'البريد والتعاون',
    purposeEn: 'Internal collaboration + customer correspondence via Microsoft 365.',
    purposeAr: 'التعاون الداخلي ومراسلات العملاء عبر Microsoft 365.',
    lawfulBasis: 'LEGITIMATE_INTEREST',
    categories: ['CONTACT', 'BEHAVIOURAL'],
    dataSubjects: ['EMPLOYEES', 'CUSTOMERS'],
    sourceAssetCode: 'SVC-OFFICE365',
    subProcessors: ['Microsoft 365 (EU + US regions)'],
    retentionDays: 365 * 5,
    crossBorder: true,
    lastReviewedAt: _daysAgo(10),
    ownerEmail: 'it.lead@tss.sa',
  },
  {
    code: 'ROPA-007',
    activityNameEn: 'Job applicant screening',
    activityNameAr: 'فرز المرشّحين للوظائف',
    purposeEn: 'Screen + select candidates per Saudi Labor Law.',
    purposeAr: 'فرز واختيار المرشّحين وفق نظام العمل السعودي.',
    lawfulBasis: 'CONSENT',
    categories: ['IDENTIFICATION', 'CONTACT', 'EMPLOYMENT'],
    dataSubjects: ['JOB_APPLICANTS'],
    sourceAssetCode: 'DB-PRIMARY',
    subProcessors: ['LinkedIn (recruiter)', 'Background check provider'],
    retentionDays: 365 * 1,
    crossBorder: true,
    lastReviewedAt: _daysAgo(70),
    ownerEmail: 'hr.lead@tss.sa',
  },
  {
    code: 'ROPA-008',
    activityNameEn: 'CCTV monitoring (branch + HQ)',
    activityNameAr: 'كاميرات المراقبة (الفروع والمقر الرئيسي)',
    purposeEn: 'Physical security + safety monitoring of premises per SAMA Operational Resilience.',
    purposeAr: 'الأمن المادي ومراقبة سلامة المباني وفق إطار المرونة التشغيلية لساما.',
    lawfulBasis: 'LEGITIMATE_INTEREST',
    categories: ['BIOMETRIC', 'BEHAVIOURAL'],
    dataSubjects: ['EMPLOYEES', 'CUSTOMERS', 'WEBSITE_VISITORS'],
    sourceAssetCode: 'FAC-HQ-RIYADH',
    subProcessors: [],
    retentionDays: 30,
    crossBorder: false,
    lastReviewedAt: _daysAgo(5),
    ownerEmail: 'facilities.lead@tss.sa',
  },
];

// ─── Consent records ───────────────────────────────────────────────────────

export type ConsentChannel = 'WEB' | 'MOBILE' | 'BRANCH' | 'PHONE' | 'EMAIL';

export type ConsentStatus = 'ACTIVE' | 'WITHDRAWN' | 'EXPIRED';

export interface ConsentRecord {
  id: string;
  /** Data subject reference (national id last 4 + email hash). */
  subjectRef: string;
  /** ROPA activity this consent maps to. */
  ropaCode: string;
  /** Consent text version accepted (e.g. "v3.2"). */
  textVersion: string;
  channel: ConsentChannel;
  status: ConsentStatus;
  grantedAt: string;
  withdrawnAt?: string;
}

// Demo consent rollup — driven by realistic-looking aggregate counts so
// the page can show consent volume without rendering 50k rows.
export interface ConsentSummary {
  ropaCode: string;
  active: number;
  withdrawn: number;
  expired: number;
  /** Withdrawal rate as % of granted. */
  withdrawalRate: number;
}

export const CONSENT_SUMMARIES: ConsentSummary[] = [
  { ropaCode: 'ROPA-004', active: 142_310, withdrawn: 8_420, expired: 1_200, withdrawalRate: 5.6 },
  { ropaCode: 'ROPA-007', active: 18_220, withdrawn: 240, expired: 5_840, withdrawalRate: 1.3 },
];

// ─── Helpers ───────────────────────────────────────────────────────────────

export function ropaSourceAsset(entry: RopaEntry): Asset | undefined {
  return ASSETS.find(a => a.code === entry.sourceAssetCode);
}

export const LAWFUL_BASIS_LABEL: Record<LawfulBasis, { en: string; ar: string }> = {
  CONSENT:              { en: 'Consent',                 ar: 'الموافقة' },
  CONTRACT:             { en: 'Contract',                ar: 'عقد' },
  LEGAL_OBLIGATION:     { en: 'Legal obligation',         ar: 'التزام قانوني' },
  VITAL_INTEREST:       { en: 'Vital interest',           ar: 'مصلحة حيوية' },
  PUBLIC_TASK:          { en: 'Public task',              ar: 'مهمة عامة' },
  LEGITIMATE_INTEREST:  { en: 'Legitimate interest',      ar: 'مصلحة مشروعة' },
};

export const CATEGORY_LABEL: Record<RopaCategory, { en: string; ar: string }> = {
  IDENTIFICATION:    { en: 'Identification',     ar: 'بيانات تعريفية' },
  CONTACT:           { en: 'Contact',             ar: 'بيانات اتصال' },
  FINANCIAL:         { en: 'Financial',           ar: 'بيانات مالية' },
  HEALTH:            { en: 'Health',              ar: 'بيانات صحية' },
  BEHAVIOURAL:       { en: 'Behavioural',         ar: 'بيانات سلوكية' },
  LOCATION:          { en: 'Location',            ar: 'بيانات موقع' },
  EMPLOYMENT:        { en: 'Employment',          ar: 'بيانات توظيف' },
  BIOMETRIC:         { en: 'Biometric',           ar: 'بيانات حيوية' },
  SPECIAL_CATEGORY:  { en: 'Special category',    ar: 'فئة خاصة' },
};

export const SUBJECT_LABEL: Record<DataSubjectGroup, { en: string; ar: string }> = {
  CUSTOMERS:        { en: 'Customers',         ar: 'العملاء' },
  EMPLOYEES:        { en: 'Employees',         ar: 'الموظفون' },
  CONTRACTORS:      { en: 'Contractors',       ar: 'المتعاقدون' },
  PROSPECTS:        { en: 'Prospects',         ar: 'العملاء المحتملون' },
  WEBSITE_VISITORS: { en: 'Website visitors',  ar: 'زوّار الموقع' },
  JOB_APPLICANTS:   { en: 'Job applicants',    ar: 'المتقدّمون للوظائف' },
  BENEFICIARIES:    { en: 'Beneficiaries',     ar: 'المستفيدون' },
};

export interface RopaStats {
  total: number;
  consentBased: number;
  crossBorder: number;
  reviewedLast90Days: number;
  needsReview: number;
}

export function aggregateRopa(entries: ReadonlyArray<RopaEntry> = ROPA_ENTRIES): RopaStats {
  const now = Date.now();
  let consentBased = 0, crossBorder = 0, reviewedLast90Days = 0, needsReview = 0;
  for (const e of entries) {
    if (e.lawfulBasis === 'CONSENT') consentBased++;
    if (e.crossBorder) crossBorder++;
    const days = (now - new Date(e.lastReviewedAt).getTime()) / 86_400_000;
    if (days <= 90) reviewedLast90Days++;
    if (days > 365) needsReview++;
  }
  return { total: entries.length, consentBased, crossBorder, reviewedLast90Days, needsReview };
}
