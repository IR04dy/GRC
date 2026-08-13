// Privacy / PDPL lifecycle — production hardening (Option 3).
//
// Saudi Personal Data Protection Law (PDPL) production lifecycle covering
// three lifecycle pieces the existing pdplBreach console doesn't:
//
//   1. ROPA Article 22 wizard — guided creation walking through 9 fields
//   2. DPIA workflow with PDPL-specific risk scoring (high-risk processing
//      definitions per Implementing Regulations Article 25)
//   3. Cross-border transfer adequacy assessment per Article 29 — country
//      whitelist + safeguard mechanism picker (BCRs / SCCs / certification)
//
// ⚠ F2-202 — the DSR (Article 4) workflow that used to be the fourth piece here has MOVED
// to `lib/privacyLcApi.ts` and the real register at `/api/privacy-lc/dsr-cases` (12 rows in
// dev, 12 in prod). It is not a fifth surface: `privacy_lc.dsr_cases` is the same table the
// server-side machine, the SLA sweep and F2-196's derived terminal set already govern.
//
// Nothing DSR-shaped may come back into this file. The three cases that lived here as
// `SEED_DSR` were clock-minted at module load, `createDsr()` issued the PDPL Article 4
// statutory deadline and the tracking reference in the browser, and the "REST mirror" it
// called could not send anything because the persistence-mode switch it read had no setter
// anywhere in the product.
//
// ⚠⚠ The ROPA / DPIA / cross-border corpora BELOW are still browser-local, and that is a
// filed finding, not a decision — see `BROWSER_LOCAL_LIFECYCLE_STORES` in
// `lib/lifecycleStores.ts`, which names each one, its real endpoint and its measured row
// count. Do not read the presence of a DSR rewire as evidence the rest of this file is
// backed by anything.

export type RopaStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'SUPERSEDED';
export type DpiaStatus = 'DRAFT' | 'RISK_ASSESSMENT' | 'CONSULTATION' | 'APPROVED' | 'REJECTED';
export type CbtStatus = 'DRAFT' | 'ASSESSING' | 'APPROVED' | 'BLOCKED' | 'PERIODIC_REVIEW';

// ─── ROPA — Article 22 ──────────────────────────────────────────────────────

export interface RopaActivity {
  /** Stable id, e.g. "ROPA-2026-001". */
  id: string;
  /** Bilingual processing activity name. */
  nameEn: string;
  nameAr: string;
  /** PDPL Article 5 lawful basis. */
  lawfulBasis: 'CONSENT' | 'CONTRACT' | 'LEGAL_OBLIGATION' | 'VITAL_INTEREST' | 'PUBLIC_INTEREST' | 'LEGITIMATE_INTEREST';
  /** Processing purposes — bilingual. */
  purposesEn: string;
  purposesAr: string;
  /** Categories of personal data processed. */
  dataCategories: ('IDENTITY' | 'CONTACT' | 'FINANCIAL' | 'BIOMETRIC' | 'HEALTH' | 'BEHAVIOURAL' | 'LOCATION' | 'CHILDREN')[];
  /** Categories of data subjects. */
  subjectCategories: ('CUSTOMER' | 'EMPLOYEE' | 'CONTRACTOR' | 'VISITOR' | 'PROSPECT' | 'MINOR')[];
  /** Recipients of the data. */
  recipientsEn: string;
  recipientsAr: string;
  /** Storage location (data residency). */
  storageLocation: string;
  /** Retention period. */
  retentionPeriodEn: string;
  retentionPeriodAr: string;
  /** Security measures applied. */
  securityMeasures: string[];
  /** Owner / data steward. */
  owner: string;
  status: RopaStatus;
  createdAt: string;
  approvedBy?: string;
  approvedAt?: string;
  /** Linked DPIA id (if processing is high-risk). */
  dpiaId?: string;
  /** Linked CBT id (if cross-border transfer applies). */
  cbtId?: string;
}

// ─── DPIA — Article 25 ──────────────────────────────────────────────────────

export interface DpiaAssessment {
  id: string;
  ropaId: string;
  status: DpiaStatus;
  /** Likelihood of harm (1-5). */
  likelihood: number;
  /** Severity of impact on subjects (1-5). */
  severity: number;
  /** Combined risk score (likelihood × severity). */
  riskScore: number;
  /** Whether this triggers Article 25 mandatory DPIA. */
  isHighRisk: boolean;
  /** Bilingual high-risk triggers identified. */
  triggersEn: string[];
  triggersAr: string[];
  /** Mitigations proposed. */
  mitigationsEn: string;
  mitigationsAr: string;
  /** Residual risk after mitigations. */
  residualScore: number;
  /** Whether SDAIA prior consultation is required. */
  needsSdaiaConsultation: boolean;
  /** Consultation outcome if applicable. */
  consultationOutcomeEn?: string;
  consultationOutcomeAr?: string;
  /** DPO sign-off. */
  dpoSignedBy?: string;
  dpoSignedAt?: string;
  createdAt: string;
}

// ─── Cross-Border Transfer — Article 29 ─────────────────────────────────────

export type AdequacyLevel = 'ADEQUATE' | 'CONDITIONAL' | 'NOT_ADEQUATE' | 'PROHIBITED';
export type SafeguardMechanism = 'NONE_NEEDED' | 'STANDARD_CLAUSES' | 'BINDING_CORPORATE_RULES' | 'CERTIFICATION' | 'EXPLICIT_CONSENT';

export interface CrossBorderTransfer {
  id: string;
  ropaId: string;
  /** Destination country. */
  destinationCountry: string;
  /** Transfer purpose. */
  purposeEn: string;
  purposeAr: string;
  /** SDAIA-recognised adequacy level. */
  adequacy: AdequacyLevel;
  /** Safeguard mechanism applied. */
  safeguard: SafeguardMechanism;
  /** Date safeguard was put in place. */
  safeguardDate?: string;
  /** Risk before safeguard. */
  preRiskScore: number;
  /** Risk after safeguard. */
  postRiskScore: number;
  /** Periodic review interval (months). */
  reviewIntervalMonths: number;
  /** Last review date. */
  lastReviewedAt?: string;
  /** Next review date. */
  nextReviewAt?: string;
  status: CbtStatus;
  approvedBy?: string;
  approvedAt?: string;
  createdAt: string;
}

/** SDAIA adequacy whitelist (illustrative — actual list maintained by SDAIA). */
export const ADEQUACY_BY_COUNTRY: Record<string, AdequacyLevel> = {
  // Adequate (subset of countries SDAIA has indicated alignment with)
  'United Arab Emirates': 'ADEQUATE',
  'Bahrain': 'ADEQUATE',
  'Kuwait': 'ADEQUATE',
  'Oman': 'ADEQUATE',
  'Qatar': 'ADEQUATE',
  // Conditional — adequate for some sectors / data categories
  'United Kingdom': 'CONDITIONAL',
  'European Union (collectively)': 'CONDITIONAL',
  'Switzerland': 'CONDITIONAL',
  // Not adequate — safeguards required
  'United States of America': 'NOT_ADEQUATE',
  'Singapore': 'NOT_ADEQUATE',
  'India': 'NOT_ADEQUATE',
  'Canada': 'NOT_ADEQUATE',
  'Australia': 'NOT_ADEQUATE',
  // Prohibited — no transfers permitted
  'Iran': 'PROHIBITED',
  'Syria': 'PROHIBITED',
};

// ─── Storage ────────────────────────────────────────────────────────────────

const ROPA_KEY = 'grc.privacy.ropa.v1';
const DPIA_KEY = 'grc.privacy.dpia.v1';
const CBT_KEY = 'grc.privacy.cbt.v1';
const EVENT = 'grc.privacy.changed';

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString();
const _daysAhead = (n: number) => new Date(_now + n * 86_400_000).toISOString().slice(0, 10);

// ─── Seed ───────────────────────────────────────────────────────────────────

const SEED_ROPA: RopaActivity[] = [
  {
    id: 'ROPA-2026-001',
    nameEn: 'Customer onboarding (KYC + AML)',
    nameAr: 'تسجيل العملاء (اعرف عميلك + مكافحة غسل الأموال)',
    lawfulBasis: 'LEGAL_OBLIGATION',
    purposesEn: 'KYC and AML compliance per SAMA Customer Due Diligence Rules. Identity verification + sanctions screening.',
    purposesAr: 'الالتزام باعرف عميلك ومكافحة غسل الأموال وفق قواعد ساما للعناية الواجبة. التحقق من الهوية + فحص العقوبات.',
    dataCategories: ['IDENTITY', 'CONTACT', 'FINANCIAL'],
    subjectCategories: ['CUSTOMER', 'PROSPECT'],
    recipientsEn: 'Internal compliance team, SAMA on request, audit firms during external audit.',
    recipientsAr: 'فريق الامتثال الداخلي، ساما عند الطلب، شركات التدقيق أثناء المراجعة الخارجية.',
    storageLocation: 'Riyadh primary + Jeddah DR',
    retentionPeriodEn: '10 years post-relationship per SAMA AML Rules',
    retentionPeriodAr: '10 سنوات بعد انتهاء العلاقة وفق قواعد مكافحة غسل الأموال',
    securityMeasures: ['Encryption at rest (AES-256)', 'Encryption in transit (TLS 1.3)', 'PAM for production access', 'Annual penetration testing'],
    owner: 'Layla Al-Ghamdi (DPO)',
    status: 'APPROVED',
    createdAt: _daysAgo(120),
    approvedBy: 'Khalid Al-Falih (Audit Committee Chair) + DPO',
    approvedAt: _daysAgo(90),
    dpiaId: 'DPIA-2026-001',
  },
  {
    id: 'ROPA-2026-002',
    nameEn: 'Workforce HR file management',
    nameAr: 'إدارة ملفات الموارد البشرية للموظفين',
    lawfulBasis: 'CONTRACT',
    purposesEn: 'Employment contract performance: payroll, GOSI contributions, leave, performance reviews.',
    purposesAr: 'تنفيذ عقد التوظيف: الرواتب، مساهمات التأمينات الاجتماعية، الإجازات، تقييمات الأداء.',
    dataCategories: ['IDENTITY', 'CONTACT', 'FINANCIAL', 'BEHAVIOURAL'],
    subjectCategories: ['EMPLOYEE', 'CONTRACTOR'],
    recipientsEn: 'GOSI for social insurance, MoL for visa processing, payroll provider.',
    recipientsAr: 'التأمينات الاجتماعية، وزارة الموارد البشرية لمعالجة التأشيرات، مزوّد الرواتب.',
    storageLocation: 'Riyadh primary',
    retentionPeriodEn: '7 years post-employment per Labor Law Art. 78',
    retentionPeriodAr: '7 سنوات بعد انتهاء التوظيف وفق المادة 78 من نظام العمل',
    securityMeasures: ['Role-based access', 'Encryption at rest', 'Annual access review'],
    owner: 'Salman Al-Dossari (CHRO)',
    status: 'APPROVED',
    createdAt: _daysAgo(150),
    approvedBy: 'DPO',
    approvedAt: _daysAgo(140),
  },
  {
    id: 'ROPA-2026-003',
    nameEn: 'Marketing analytics — anonymised',
    nameAr: 'تحليلات التسويق — مجهولة الهوية',
    lawfulBasis: 'LEGITIMATE_INTEREST',
    purposesEn: 'Aggregated cohort analysis for product improvement. Personal identifiers stripped pre-analysis.',
    purposesAr: 'تحليل المجموعات المُجمَّعة لتطوير المنتج. تُزال المُعرّفات الشخصية قبل التحليل.',
    dataCategories: ['BEHAVIOURAL'],
    subjectCategories: ['CUSTOMER'],
    recipientsEn: 'Marketing team, US-based analytics provider (Mixpanel) — see CBT-2026-001.',
    recipientsAr: 'فريق التسويق، مزوّد تحليلات أمريكي (Mixpanel) — انظر CBT-2026-001.',
    storageLocation: 'Riyadh primary + Mixpanel US',
    retentionPeriodEn: '24 months rolling',
    retentionPeriodAr: '24 شهراً متجدّدة',
    securityMeasures: ['Anonymisation pre-export', 'Standard contractual clauses with Mixpanel'],
    owner: 'Hessa Al-Rasheed (Head of Marketing)',
    status: 'IN_REVIEW',
    createdAt: _daysAgo(15),
    cbtId: 'CBT-2026-001',
  },
];

const SEED_DPIA: DpiaAssessment[] = [
  {
    id: 'DPIA-2026-001',
    ropaId: 'ROPA-2026-001',
    status: 'APPROVED',
    likelihood: 3, severity: 4, riskScore: 12,
    isHighRisk: true,
    triggersEn: ['Large-scale processing', 'Financial data', 'Automated decision-making (sanctions screening)'],
    triggersAr: ['معالجة واسعة النطاق', 'بيانات مالية', 'اتخاذ قرارات آلي (فحص العقوبات)'],
    mitigationsEn: 'Pseudonymisation of identifiers in non-production environments. Quarterly access reviews. Annual penetration testing. Manual override mandatory for sanctions positives.',
    mitigationsAr: 'إخفاء المُعرّفات في البيئات غير الإنتاجية. مراجعة الوصول ربع سنوياً. اختبار اختراق سنوي. تدخّل يدوي إلزامي عند الإشارة الإيجابية للعقوبات.',
    residualScore: 6,
    needsSdaiaConsultation: false,
    dpoSignedBy: 'Layla Al-Ghamdi (DPO)',
    dpoSignedAt: _daysAgo(95),
    createdAt: _daysAgo(110),
  },
];

const SEED_CBT: CrossBorderTransfer[] = [
  {
    id: 'CBT-2026-001',
    ropaId: 'ROPA-2026-003',
    destinationCountry: 'United States of America',
    purposeEn: 'Send anonymised behavioural events to Mixpanel for product analytics.',
    purposeAr: 'إرسال أحداث سلوكية مجهولة إلى Mixpanel لتحليلات المنتج.',
    adequacy: 'NOT_ADEQUATE',
    safeguard: 'STANDARD_CLAUSES',
    safeguardDate: _daysAgo(60),
    preRiskScore: 12,
    postRiskScore: 4,
    reviewIntervalMonths: 12,
    lastReviewedAt: _daysAgo(30),
    nextReviewAt: _daysAhead(335),
    status: 'APPROVED',
    approvedBy: 'Layla Al-Ghamdi (DPO)',
    approvedAt: _daysAgo(28),
    createdAt: _daysAgo(45),
  },
];


// ─── Persistence ────────────────────────────────────────────────────────────

export function loadRopa(): RopaActivity[] {
  if (typeof window === 'undefined') return SEED_ROPA;
  try {
    const raw = window.localStorage.getItem(ROPA_KEY);
    return raw ? JSON.parse(raw) as RopaActivity[] : SEED_ROPA;
  } catch { return SEED_ROPA; }
}

export function saveRopa(r: RopaActivity): RopaActivity {
  const all = loadRopa().filter(x => x.id !== r.id);
  const next = [...all, r];
  try { window.localStorage.setItem(ROPA_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return r;
}

export function loadDpia(): DpiaAssessment[] {
  if (typeof window === 'undefined') return SEED_DPIA;
  try {
    const raw = window.localStorage.getItem(DPIA_KEY);
    return raw ? JSON.parse(raw) as DpiaAssessment[] : SEED_DPIA;
  } catch { return SEED_DPIA; }
}

export function saveDpia(d: DpiaAssessment): DpiaAssessment {
  const all = loadDpia().filter(x => x.id !== d.id);
  const next = [...all, d];
  try { window.localStorage.setItem(DPIA_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return d;
}

export function loadCbt(): CrossBorderTransfer[] {
  if (typeof window === 'undefined') return SEED_CBT;
  try {
    const raw = window.localStorage.getItem(CBT_KEY);
    return raw ? JSON.parse(raw) as CrossBorderTransfer[] : SEED_CBT;
  } catch { return SEED_CBT; }
}

export function saveCbt(c: CrossBorderTransfer): CrossBorderTransfer {
  const all = loadCbt().filter(x => x.id !== c.id);
  const next = [...all, c];
  try { window.localStorage.setItem(CBT_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return c;
}

export function transitionDpia(id: string, status: DpiaStatus, dpoSignedBy?: string): DpiaAssessment | null {
  const d = loadDpia().find(x => x.id === id);
  if (!d) return null;
  const updated: DpiaAssessment = { ...d, status };
  if (status === 'APPROVED' && dpoSignedBy) {
    updated.dpoSignedBy = dpoSignedBy;
    updated.dpoSignedAt = new Date().toISOString();
  }
  return saveDpia(updated);
}

export function createDpia(ropaId: string): DpiaAssessment {
  const id = `DPIA-${new Date().getFullYear()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`;
  const dpia: DpiaAssessment = {
    id, ropaId, status: 'DRAFT',
    likelihood: 3, severity: 3, riskScore: 9,
    isHighRisk: false,
    triggersEn: [], triggersAr: [],
    mitigationsEn: '', mitigationsAr: '',
    residualScore: 9,
    needsSdaiaConsultation: false,
    createdAt: new Date().toISOString(),
  };
  return saveDpia(dpia);
}

export function transitionCbt(id: string, status: CbtStatus, approvedBy?: string): CrossBorderTransfer | null {
  const c = loadCbt().find(x => x.id === id);
  if (!c) return null;
  const updated: CrossBorderTransfer = { ...c, status };
  if (status === 'APPROVED' && approvedBy) {
    updated.approvedBy = approvedBy;
    updated.approvedAt = new Date().toISOString();
    if (!updated.lastReviewedAt) updated.lastReviewedAt = new Date().toISOString();
    if (!updated.nextReviewAt) {
      const next = new Date(Date.now() + updated.reviewIntervalMonths * 30 * 86_400_000);
      updated.nextReviewAt = next.toISOString().slice(0, 10);
    }
  }
  return saveCbt(updated);
}

export function upgradeCbtSafeguard(id: string, newSafeguard: SafeguardMechanism): CrossBorderTransfer | null {
  const c = loadCbt().find(x => x.id === id);
  if (!c) return null;
  // Upgrading typically reduces post-risk score by 2-4 points.
  const reduction = newSafeguard === 'BINDING_CORPORATE_RULES' ? 4 :
                    newSafeguard === 'CERTIFICATION' ? 3 :
                    newSafeguard === 'STANDARD_CLAUSES' ? 2 : 0;
  return saveCbt({
    ...c,
    safeguard: newSafeguard,
    safeguardDate: new Date().toISOString(),
    postRiskScore: Math.max(2, c.preRiskScore - reduction),
  });
}

export function subscribePrivacy(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  const storage = (e: StorageEvent) => {
    if ([ROPA_KEY, DPIA_KEY, CBT_KEY].includes(e.key ?? '')) cb();
  };
  window.addEventListener('storage', storage);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storage);
  };
}

function emit() { try { window.dispatchEvent(new CustomEvent(EVENT)); } catch { /* ignore */ } }

// ─── Derived helpers ────────────────────────────────────────────────────────

/** Detect Article 25 high-risk DPIA triggers. */
export function detectHighRisk(activity: RopaActivity): { isHigh: boolean; triggers: { en: string; ar: string }[] } {
  const triggers: { en: string; ar: string }[] = [];
  if (activity.dataCategories.includes('BIOMETRIC')) {
    triggers.push({ en: 'Biometric data', ar: 'بيانات حيوية' });
  }
  if (activity.dataCategories.includes('HEALTH')) {
    triggers.push({ en: 'Health data', ar: 'بيانات صحية' });
  }
  if (activity.dataCategories.includes('CHILDREN') || activity.subjectCategories.includes('MINOR')) {
    triggers.push({ en: 'Children\'s data', ar: 'بيانات قاصرين' });
  }
  if (activity.dataCategories.includes('LOCATION')) {
    triggers.push({ en: 'Systematic location tracking', ar: 'تتبّع موقع منهجي' });
  }
  if (activity.subjectCategories.length >= 3 || activity.dataCategories.length >= 4) {
    triggers.push({ en: 'Large-scale processing', ar: 'معالجة واسعة النطاق' });
  }
  return { isHigh: triggers.length > 0, triggers };
}

// ─── Display helpers ───────────────────────────────────────────────────────

export const ROPA_STATUS_LABEL: Record<RopaStatus, { en: string; ar: string }> = {
  DRAFT:      { en: 'Draft',       ar: 'مسوّدة' },
  IN_REVIEW:  { en: 'In review',   ar: 'قيد المراجعة' },
  APPROVED:   { en: 'Approved',    ar: 'مُعتمَدة' },
  SUPERSEDED: { en: 'Superseded',  ar: 'مُستبدَلة' },
};

export const ROPA_STATUS_TONE: Record<RopaStatus, 'neutral' | 'info' | 'success' | 'warning'> = {
  DRAFT: 'neutral', IN_REVIEW: 'info', APPROVED: 'success', SUPERSEDED: 'warning',
};

export const LAWFUL_BASIS_LABEL: Record<RopaActivity['lawfulBasis'], { en: string; ar: string }> = {
  CONSENT:             { en: 'Consent',                ar: 'الموافقة' },
  CONTRACT:            { en: 'Contract performance',   ar: 'تنفيذ العقد' },
  LEGAL_OBLIGATION:    { en: 'Legal obligation',       ar: 'التزام نظامي' },
  VITAL_INTEREST:      { en: 'Vital interest',         ar: 'مصلحة حيوية' },
  PUBLIC_INTEREST:     { en: 'Public interest',        ar: 'مصلحة عامة' },
  LEGITIMATE_INTEREST: { en: 'Legitimate interest',    ar: 'مصلحة مشروعة' },
};

export const ADEQUACY_TONE: Record<AdequacyLevel, 'success' | 'warning' | 'info' | 'danger'> = {
  ADEQUATE: 'success', CONDITIONAL: 'info', NOT_ADEQUATE: 'warning', PROHIBITED: 'danger',
};

export const ADEQUACY_LABEL: Record<AdequacyLevel, { en: string; ar: string }> = {
  ADEQUATE:     { en: 'Adequate',         ar: 'كافية' },
  CONDITIONAL:  { en: 'Conditional',      ar: 'مشروطة' },
  NOT_ADEQUATE: { en: 'Not adequate',     ar: 'غير كافية' },
  PROHIBITED:   { en: 'Prohibited',       ar: 'محظورة' },
};

export const SAFEGUARD_LABEL: Record<SafeguardMechanism, { en: string; ar: string }> = {
  NONE_NEEDED:             { en: 'None required',                 ar: 'غير مطلوبة' },
  STANDARD_CLAUSES:        { en: 'Standard contractual clauses',  ar: 'بنود تعاقدية نموذجية' },
  BINDING_CORPORATE_RULES: { en: 'Binding corporate rules',       ar: 'قواعد الشركات الملزمة' },
  CERTIFICATION:           { en: 'Certification scheme',           ar: 'نظام شهادات' },
  EXPLICIT_CONSENT:        { en: 'Explicit consent',                ar: 'موافقة صريحة' },
};

// The DSR label/tone maps moved to `lib/privacyLcApi.ts` with the register they describe.
// Duplicating them here would leave a second vocabulary for one statutory object, which is
// the F2-196 defect this rewire exists to stop repeating.

// ─── Aggregation ────────────────────────────────────────────────────────────

/**
 * ⚠ F2-202 removed `dsrOpen` / `dsrOverdue` / `dsrCloseAvgDays` from this type.
 *
 * They are not "gone" — they moved to `aggregateDsr()` in `lib/privacyLcApi.ts`, which
 * computes them over rows fetched from `/api/privacy-lc/dsr-cases`. They could not stay
 * here: this function is synchronous because ROPA/DPIA/CBT are read out of localStorage,
 * and a synchronous accessor cannot answer a question whose data comes from a server.
 *
 * That asymmetry is the honest signal. Everything this function still returns is browser-
 * local (see `BROWSER_LOCAL_LIFECYCLE_STORES`); the DSR figures had to leave for the same
 * reason they are now trustworthy.
 */
export interface PrivacyLifecycleStats {
  ropaTotal: number;
  ropaApproved: number;
  dpiaTotal: number;
  dpiaHighRisk: number;
  cbtTotal: number;
  cbtAdequate: number;
  cbtBlocked: number;
}

export function aggregatePrivacy(): PrivacyLifecycleStats {
  const ropa = loadRopa();
  const dpias = loadDpia();
  const cbts = loadCbt();
  return {
    ropaTotal: ropa.length,
    ropaApproved: ropa.filter(r => r.status === 'APPROVED').length,
    dpiaTotal: dpias.length,
    dpiaHighRisk: dpias.filter(d => d.isHighRisk).length,
    cbtTotal: cbts.length,
    cbtAdequate: cbts.filter(c => c.adequacy === 'ADEQUATE').length,
    cbtBlocked: cbts.filter(c => c.status === 'BLOCKED' || c.adequacy === 'PROHIBITED').length,
  };
}
