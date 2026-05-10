// Audit lifecycle — production hardening (Option 2).
//
// Wraps the existing audit engagement model with the full IIA Standards-
// aligned audit lifecycle that an Audit Committee chair in Saudi Arabia
// expects:
//
//   1. Annual audit plan — risk-based selection of engagements per quarter,
//      Audit Committee-approved per CMA Corporate Governance Regulations
//      Article 95 (Audit Committee responsibilities).
//   2. Working papers — per-engagement bundles of test work, interview
//      notes, observations and document reviews, with reviewer @mentions
//      per IIA Standard 2330 (Documenting Information).
//   3. Finding write-up — bilingual finding with severity, control
//      crosswalk to NCA ECC / SAMA RBSF, management response and target
//      remediation date.
//   4. CAPA tracking — corrective + preventive actions linked back to the
//      finding, with closure verification.
//   5. Audit Committee packet — quarterly executive report exporting
//      engagement progress, finding aging, CAPA closure rate and the next
//      quarter's plan.

import { AUDIT_ENGAGEMENTS as MOCK_ENGAGEMENTS } from '../pages/compliance/mockCompliance';
import type { AuditEngagementDto } from './api';
import {
  AUDIT_FINDING_ADAPTER,
  mirrorSaveToBackend,
  mirrorTransitionToBackend,
} from './platformPersistence';

// ─── Types ──────────────────────────────────────────────────────────────────

export type AnnualPlanStatus = 'DRAFT' | 'AC_REVIEW' | 'AC_APPROVED' | 'EXECUTING' | 'CLOSED';

export type WorkingPaperType = 'TEST' | 'INTERVIEW' | 'OBSERVATION' | 'DOCUMENT_REVIEW' | 'WALKTHROUGH';
export type WorkingPaperStatus = 'DRAFT' | 'PEER_REVIEW' | 'APPROVED' | 'REJECTED';

export type FindingSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'OBSERVATION';
export type FindingStatus = 'OPEN' | 'MGMT_RESPONSE' | 'IN_REMEDIATION' | 'AWAITING_VERIFICATION' | 'CLOSED';

export type CapaStatus = 'OPEN' | 'IN_PROGRESS' | 'AWAITING_VERIFICATION' | 'CLOSED' | 'OVERDUE';

export interface AnnualPlan {
  /** e.g. "AP-2026". */
  id: string;
  year: number;
  status: AnnualPlanStatus;
  /** ISO timestamp of last status change. */
  lastUpdated: string;
  acApprovedBy?: string;
  acApprovedAt?: string;
  /** Engagement codes scheduled per quarter (Q1-Q4). */
  q1: string[];
  q2: string[];
  q3: string[];
  q4: string[];
  /** Total budget hours for the year. */
  budgetHours: number;
  /** Notes attached during AC review. */
  acNotesEn?: string;
  acNotesAr?: string;
}

export interface WorkingPaper {
  id: string;
  engagementCode: string;
  type: WorkingPaperType;
  /** Sequential reference within the engagement (e.g. "WP-001"). */
  refNumber: string;
  titleEn: string;
  titleAr: string;
  /** Author (auditor name). */
  author: string;
  /** ISO date created. */
  createdAt: string;
  status: WorkingPaperStatus;
  /** Reviewer (manager / partner). */
  reviewer?: string;
  reviewedAt?: string;
  /** Free-text procedure performed. */
  procedureEn: string;
  procedureAr: string;
  /** Bilingual conclusion. */
  conclusionEn: string;
  conclusionAr: string;
  /** Linked control codes. */
  controlsCovered: string[];
  /** Hours spent on this paper. */
  hours: number;
}

export interface Finding {
  id: string;
  engagementCode: string;
  /** Sequential within engagement. */
  refNumber: string;
  titleEn: string;
  titleAr: string;
  severity: FindingSeverity;
  status: FindingStatus;
  /** Control / framework crosswalk. */
  controlCrosswalk: { framework: string; controlCode: string }[];
  /** Bilingual condition (what was observed). */
  conditionEn: string;
  conditionAr: string;
  /** Bilingual criteria (what should be — policy/regulation). */
  criteriaEn: string;
  criteriaAr: string;
  /** Bilingual root cause. */
  causeEn: string;
  causeAr: string;
  /** Bilingual recommendation. */
  recommendationEn: string;
  recommendationAr: string;
  /** Management response — bilingual + author + accepted/disagreed. */
  mgmtResponseEn?: string;
  mgmtResponseAr?: string;
  mgmtResponseAuthor?: string;
  mgmtAccepts?: boolean;
  /** Target remediation date. */
  targetDate?: string;
  /** Who owns remediation. */
  ownerRole?: string;
  /** ISO timestamp when finding was raised. */
  raisedAt: string;
  /** Linked CAPA task ids. */
  capaIds: string[];
}

export interface CapaTask {
  id: string;
  findingId: string;
  /** Corrective vs Preventive. */
  kind: 'CORRECTIVE' | 'PREVENTIVE';
  titleEn: string;
  titleAr: string;
  ownerRole: string;
  /** Target completion. */
  targetDate: string;
  status: CapaStatus;
  /** ISO when closed. */
  closedAt?: string;
  /** Verification notes by audit. */
  verificationNotesEn?: string;
  verificationNotesAr?: string;
  /** Evidence reference. */
  evidenceRef?: string;
}

// ─── Storage ────────────────────────────────────────────────────────────────

const PLAN_KEY = 'grc.audit.lifecycle.plan.v1';
const WP_KEY = 'grc.audit.lifecycle.wp.v1';
const FINDING_KEY = 'grc.audit.lifecycle.finding.v1';
const CAPA_KEY = 'grc.audit.lifecycle.capa.v1';
const EVENT = 'grc.audit.lifecycle.changed';

// ─── Seeded data ────────────────────────────────────────────────────────────

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString();
const _daysAhead = (n: number) => new Date(_now + n * 86_400_000).toISOString().slice(0, 10);

const SEED_PLAN: AnnualPlan = {
  id: 'AP-2026',
  year: 2026,
  status: 'EXECUTING',
  lastUpdated: _daysAgo(45),
  acApprovedBy: 'Khalid Al-Falih (Audit Committee Chair)',
  acApprovedAt: _daysAgo(40),
  // engagementCode is optional in the source mock; filter out undefined for the typed string[]
  q1: MOCK_ENGAGEMENTS.slice(0, 2).map(e => e.engagementCode).filter((c): c is string => !!c),
  q2: MOCK_ENGAGEMENTS.slice(2, 4).map(e => e.engagementCode).filter((c): c is string => !!c),
  q3: MOCK_ENGAGEMENTS.slice(4, 6).map(e => e.engagementCode).filter((c): c is string => !!c),
  q4: MOCK_ENGAGEMENTS.slice(6, 8).map(e => e.engagementCode).filter((c): c is string => !!c),
  budgetHours: 4_800,
  acNotesEn: 'Approved at AC meeting 2026-01-15. SAMA Cyber Resilience focus added per Q3 supervisory letter. Banking subsidiary scope expanded.',
  acNotesAr: 'اعتُمدت في اجتماع لجنة المراجعة 15-01-2026. أُضيفت محاور المرونة السيبراني لساما وفق خطاب الإشراف للربع الثالث. تمّ توسيع نطاق التابعة المصرفية.',
};

const SEED_WPS: WorkingPaper[] = [
  {
    id: 'WP-2026-001',
    engagementCode: MOCK_ENGAGEMENTS[0]?.engagementCode ?? 'ENG-001',
    type: 'TEST',
    refNumber: 'WP-001',
    titleEn: 'Privileged access management — sample testing of 25 admin accounts',
    titleAr: 'إدارة الوصول المُميَّز — اختبار عيّنة من 25 حساب مشرف',
    author: 'Faisal Al-Otaibi (Senior Auditor)',
    createdAt: _daysAgo(12),
    status: 'APPROVED',
    reviewer: 'Sara Al-Zahrani (Audit Manager)',
    reviewedAt: _daysAgo(10),
    procedureEn: 'Selected 25 of 142 active admin accounts using random sampling. For each, verified: (a) MFA enrolled, (b) last 90-day access review completed, (c) join/move/leave triggers had run, (d) PAM session recording was active.',
    procedureAr: 'اختير 25 من 142 حساب مشرف نشط بالعيّنة العشوائية. لكل حساب جرى التحقق من: (أ) تسجيل المصادقة متعدّدة العوامل، (ب) إكمال مراجعة الوصول كل 90 يوماً، (ج) تشغيل محفّزات الانضمام/التنقّل/المغادرة، (د) فعالية تسجيل جلسات PAM.',
    conclusionEn: '23/25 fully compliant. 2 findings raised — one MFA gap (FIN-001), one missing access-review evidence (FIN-002).',
    conclusionAr: '23/25 ملتزمة بالكامل. رُفعت ملاحظتان — فجوة MFA (FIN-001) ونقص دليل مراجعة الوصول (FIN-002).',
    controlsCovered: ['ECC-2-3-1', 'ECC-2-2-1', 'CSF-3-2-4'],
    hours: 18,
  },
  {
    id: 'WP-2026-002',
    engagementCode: MOCK_ENGAGEMENTS[0]?.engagementCode ?? 'ENG-001',
    type: 'INTERVIEW',
    refNumber: 'WP-002',
    titleEn: 'Interview with CISO on cybersecurity strategy',
    titleAr: 'مقابلة مع رئيس أمن المعلومات حول الاستراتيجية',
    author: 'Faisal Al-Otaibi (Senior Auditor)',
    createdAt: _daysAgo(20),
    status: 'APPROVED',
    reviewer: 'Sara Al-Zahrani (Audit Manager)',
    reviewedAt: _daysAgo(18),
    procedureEn: 'Conducted 90-minute interview with CISO covering 2026 cybersecurity strategy, alignment to NCA ECC v3, and resource adequacy. Notes documented within 24h per IIA Standard 2330.',
    procedureAr: 'أُجريت مقابلة 90 دقيقة مع رئيس أمن المعلومات تغطّي استراتيجية 2026 ومحاذاتها مع NCA ECC v3 وكفاية الموارد. وُثِّقت الملاحظات خلال 24 ساعة وفق المعيار 2330.',
    conclusionEn: 'Strategy aligned to NCA ECC v3 and SAMA CRF. Resource gap identified in Tier-2 SOC coverage during ramadan night shifts — flagged as observation, not finding.',
    conclusionAr: 'الاستراتيجية متّسقة مع NCA ECC v3 وإطار ساما السيبراني. حُدِّدت فجوة موارد في تغطية SOC من الفئة الثانية خلال نوبات ليالي رمضان — أُشير إليها كملاحظة وليست فجوة.',
    controlsCovered: ['ECC-1-1-1'],
    hours: 4,
  },
  {
    id: 'WP-2026-003',
    engagementCode: MOCK_ENGAGEMENTS[0]?.engagementCode ?? 'ENG-001',
    type: 'WALKTHROUGH',
    refNumber: 'WP-003',
    titleEn: 'Incident response playbook walkthrough',
    titleAr: 'تجوال على دليل الاستجابة للحوادث',
    author: 'Khalid Al-Harbi (Auditor)',
    createdAt: _daysAgo(8),
    status: 'PEER_REVIEW',
    procedureEn: 'Walked through ransomware playbook end-to-end with CSIRT lead. Validated playbook covers detection → containment → eradication → recovery → lessons-learned per NCA Incident Reporting Guidelines.',
    procedureAr: 'تجوال شامل على دليل الفدية مع قائد CSIRT. التحقق من تغطية الدليل لـ الاكتشاف ← الاحتواء ← الاستئصال ← التعافي ← الدروس المستفادة وفق إرشادات إبلاغ الحوادث.',
    conclusionEn: 'Playbook complete and current (last revised 2026-02). One gap: SAMA T+4h notification step not explicitly documented; raising as low-severity finding.',
    conclusionAr: 'الدليل مكتمل ومحدّث (آخر مراجعة 02-2026). فجوة واحدة: خطوة إبلاغ ساما خلال 4 ساعات غير موثَّقة صراحةً؛ سترفع كملاحظة منخفضة الخطورة.',
    controlsCovered: ['ECC-3-1-1'],
    hours: 6,
  },
];

const SEED_FINDINGS: Finding[] = [
  {
    id: 'FIN-2026-001',
    engagementCode: MOCK_ENGAGEMENTS[0]?.engagementCode ?? 'ENG-001',
    refNumber: 'FIN-001',
    titleEn: 'MFA not enforced on 2 admin accounts',
    titleAr: 'المصادقة متعدّدة العوامل غير مُفعَّلة على حسابَي مشرف',
    severity: 'HIGH',
    status: 'IN_REMEDIATION',
    controlCrosswalk: [
      { framework: 'NCA-ECC-V3', controlCode: 'ECC-2-3-1' },
      { framework: 'SAMA-CSF',   controlCode: 'CSF-3-2-4' },
    ],
    conditionEn: 'Sample testing of 25 admin accounts revealed 2 accounts (8%) without MFA enrolled. Both accounts had been used to access production systems within the last 30 days.',
    conditionAr: 'اختبار عيّنة من 25 حساب مشرف كشف 2 حسابات (8٪) دون تسجيل المصادقة متعدّدة العوامل. كلا الحسابين استُخدما للوصول إلى أنظمة الإنتاج خلال آخر 30 يوماً.',
    criteriaEn: 'NCA ECC v3 control 2-3-1 mandates multi-factor authentication on all privileged accounts. SAMA CSF 3-2-4 requires same for financial-sector entities.',
    criteriaAr: 'يُلزم ضابط NCA ECC v3 رقم 2-3-1 بالمصادقة متعدّدة العوامل على جميع الحسابات المُميَّزة. يفرض ضابط ساما CSF 3-2-4 المطلب نفسه على الجهات المالية.',
    causeEn: 'Service-account exemption process was undocumented; engineering team made ad-hoc carve-outs without security review.',
    causeAr: 'عملية استثناء حسابات الخدمة كانت غير موثَّقة؛ منح فريق الهندسة استثناءات دون مراجعة أمنية.',
    recommendationEn: 'Enrol the 2 affected accounts in MFA within 7 days. Document a formal exemption process requiring CISO approval. Implement monthly automated audit of admin accounts vs MFA enrolment.',
    recommendationAr: 'تسجيل الحسابَين في MFA خلال 7 أيام. توثيق عملية استثناء رسمية تتطلّب اعتماد رئيس أمن المعلومات. تنفيذ تدقيق شهري تلقائي لحسابات المشرفين مقابل تسجيل MFA.',
    mgmtResponseEn: 'We accept the finding. Both accounts were enrolled in MFA the next business day. New exemption process drafted and being routed for approval.',
    mgmtResponseAr: 'نقبل الملاحظة. سُجِّل الحسابان في MFA في يوم العمل التالي. سياسة الاستثناء الجديدة مُسوَّدة وتُمرَّر للاعتماد.',
    mgmtResponseAuthor: 'Sara Al-Zahrani (CISO)',
    mgmtAccepts: true,
    targetDate: _daysAhead(14),
    ownerRole: 'CISO',
    raisedAt: _daysAgo(10),
    capaIds: ['CAPA-2026-001', 'CAPA-2026-002'],
  },
  {
    id: 'FIN-2026-002',
    engagementCode: MOCK_ENGAGEMENTS[0]?.engagementCode ?? 'ENG-001',
    refNumber: 'FIN-002',
    titleEn: 'Quarterly access review evidence missing for 1 admin account',
    titleAr: 'دليل المراجعة الربعية للوصول مفقود لحساب مشرف واحد',
    severity: 'MEDIUM',
    status: 'AWAITING_VERIFICATION',
    controlCrosswalk: [
      { framework: 'NCA-ECC-V3', controlCode: 'ECC-2-2-1' },
    ],
    conditionEn: 'For 1 of 25 sampled admin accounts, no evidence of Q4 2025 access review could be located in the IAM system.',
    conditionAr: 'لـ 1 من 25 حساب مشرف ضمن العيّنة، لم يُعثر على دليل مراجعة الوصول للربع الرابع 2025 في نظام IAM.',
    criteriaEn: 'NCA ECC v3 control 2-2-1 requires quarterly access reviews for all privileged accounts with documented evidence retained ≥ 7 years.',
    criteriaAr: 'يفرض ضابط NCA ECC v3 رقم 2-2-1 مراجعة الوصول الربعية لجميع الحسابات المُميَّزة مع توثيق وحفظ ≥ 7 سنوات.',
    causeEn: 'Access-review attestations were exported to a shared drive but the file for one division got overwritten when migrating to the new IAM platform.',
    causeAr: 'صُدِّرت شهادات مراجعة الوصول إلى مساحة مشتركة لكن ملف إحدى الإدارات استُبدل خلال الترحيل إلى منصة IAM الجديدة.',
    recommendationEn: 'Re-perform the missing access review and retain the evidence in the new IAM platform. Implement automated retention policy.',
    recommendationAr: 'إعادة تنفيذ مراجعة الوصول المفقودة وحفظ الدليل في منصة IAM الجديدة. تطبيق سياسة احتفاظ آلية.',
    mgmtResponseEn: 'Access review re-performed 2026-04-22. Evidence uploaded with retention tag.',
    mgmtResponseAr: 'أُعيدت مراجعة الوصول في 22-04-2026. رُفع الدليل بوسم الاحتفاظ.',
    mgmtResponseAuthor: 'Khalid Al-Harbi (CIO)',
    mgmtAccepts: true,
    targetDate: _daysAhead(7),
    ownerRole: 'CIO',
    raisedAt: _daysAgo(8),
    capaIds: ['CAPA-2026-003'],
  },
];

const SEED_CAPAS: CapaTask[] = [
  {
    id: 'CAPA-2026-001',
    findingId: 'FIN-2026-001',
    kind: 'CORRECTIVE',
    titleEn: 'Enrol both affected admin accounts in MFA',
    titleAr: 'تسجيل الحسابَين المُتأثَّرَين في MFA',
    ownerRole: 'CISO',
    targetDate: _daysAhead(2),
    status: 'CLOSED',
    closedAt: _daysAgo(5),
    verificationNotesEn: 'Verified — both accounts now in MFA. Test re-performed 2026-05-01.',
    verificationNotesAr: 'تمّ التحقق — الحسابان الآن في MFA. أُعيد الاختبار 01-05-2026.',
    evidenceRef: 'EVD-2026-MFA-217',
  },
  {
    id: 'CAPA-2026-002',
    findingId: 'FIN-2026-001',
    kind: 'PREVENTIVE',
    titleEn: 'Document MFA exemption process + monthly automated audit',
    titleAr: 'توثيق عملية استثناء MFA + تدقيق شهري آلي',
    ownerRole: 'CISO',
    targetDate: _daysAhead(14),
    status: 'IN_PROGRESS',
  },
  {
    id: 'CAPA-2026-003',
    findingId: 'FIN-2026-002',
    kind: 'CORRECTIVE',
    titleEn: 'Re-perform missing access review with retention tag',
    titleAr: 'إعادة تنفيذ مراجعة الوصول المفقودة مع وسم الاحتفاظ',
    ownerRole: 'CIO',
    targetDate: _daysAhead(7),
    status: 'AWAITING_VERIFICATION',
    evidenceRef: 'EVD-2026-AR-045',
  },
];

// ─── Persistence ───────────────────────────────────────────────────────────

export function loadAnnualPlan(): AnnualPlan {
  if (typeof window === 'undefined') return SEED_PLAN;
  try {
    const raw = window.localStorage.getItem(PLAN_KEY);
    return raw ? JSON.parse(raw) as AnnualPlan : SEED_PLAN;
  } catch { return SEED_PLAN; }
}

export function saveAnnualPlan(plan: AnnualPlan): AnnualPlan {
  const next = { ...plan, lastUpdated: new Date().toISOString() };
  try { window.localStorage.setItem(PLAN_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return next;
}

export function transitionPlan(status: AnnualPlanStatus, by?: string): AnnualPlan {
  const plan = loadAnnualPlan();
  const updated: AnnualPlan = { ...plan, status };
  if (status === 'AC_APPROVED') { updated.acApprovedBy = by; updated.acApprovedAt = new Date().toISOString(); }
  return saveAnnualPlan(updated);
}

export function loadWorkingPapers(): WorkingPaper[] {
  if (typeof window === 'undefined') return SEED_WPS;
  try {
    const raw = window.localStorage.getItem(WP_KEY);
    return raw ? JSON.parse(raw) as WorkingPaper[] : SEED_WPS;
  } catch { return SEED_WPS; }
}

export function workingPapersForEngagement(code: string): WorkingPaper[] {
  return loadWorkingPapers().filter(w => w.engagementCode === code);
}

export function saveWorkingPaper(wp: WorkingPaper): WorkingPaper {
  const all = loadWorkingPapers().filter(w => w.id !== wp.id);
  const next = [...all, wp];
  try { window.localStorage.setItem(WP_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return wp;
}

export function transitionWorkingPaper(id: string, status: WorkingPaperStatus, reviewer?: string): WorkingPaper | null {
  const wp = loadWorkingPapers().find(w => w.id === id);
  if (!wp) return null;
  return saveWorkingPaper({
    ...wp, status,
    reviewer: status === 'APPROVED' ? reviewer ?? wp.reviewer : wp.reviewer,
    reviewedAt: status === 'APPROVED' ? new Date().toISOString() : wp.reviewedAt,
  });
}

export function loadFindings(): Finding[] {
  if (typeof window === 'undefined') return SEED_FINDINGS;
  try {
    const raw = window.localStorage.getItem(FINDING_KEY);
    return raw ? JSON.parse(raw) as Finding[] : SEED_FINDINGS;
  } catch { return SEED_FINDINGS; }
}

export function findingsForEngagement(code: string): Finding[] {
  return loadFindings().filter(f => f.engagementCode === code);
}

export function saveFinding(f: Finding): Finding {
  const existing = loadFindings();
  const isNew = !existing.some(x => x.id === f.id);
  const next = [...existing.filter(x => x.id !== f.id), f];
  try { window.localStorage.setItem(FINDING_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  // Best-effort REST sync; silent in demo mode.
  mirrorSaveToBackend(AUDIT_FINDING_ADAPTER, f, isNew);
  emit();
  return f;
}

export function createFinding(engagementCode: string, titleEn: string, titleAr: string, severity: FindingSeverity, conditionEn: string, conditionAr: string, ownerRole: string): Finding {
  const existing = loadFindings().filter(f => f.engagementCode === engagementCode);
  const refNumber = `FIN-${String(existing.length + 1).padStart(3, '0')}`;
  const id = `FIN-${new Date().getFullYear()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`;
  const f: Finding = {
    id, engagementCode, refNumber,
    titleEn, titleAr, severity, status: 'OPEN',
    controlCrosswalk: [],
    conditionEn, conditionAr,
    criteriaEn: '', criteriaAr: '',
    causeEn: '', causeAr: '',
    recommendationEn: '', recommendationAr: '',
    targetDate: new Date(Date.now() + 30 * 86_400_000).toISOString().slice(0, 10),
    ownerRole,
    raisedAt: new Date().toISOString(),
    capaIds: [],
  };
  return saveFinding(f);
}

export function createWorkingPaper(engagementCode: string, type: WorkingPaperType, titleEn: string, titleAr: string, author: string, hours: number): WorkingPaper {
  const existing = loadWorkingPapers().filter(w => w.engagementCode === engagementCode);
  const refNumber = `WP-${String(existing.length + 1).padStart(3, '0')}`;
  const id = `WP-${new Date().getFullYear()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`;
  const wp: WorkingPaper = {
    id, engagementCode, type, refNumber,
    titleEn, titleAr,
    author,
    createdAt: new Date().toISOString(),
    status: 'DRAFT',
    procedureEn: '', procedureAr: '',
    conclusionEn: '', conclusionAr: '',
    controlsCovered: [],
    hours,
  };
  return saveWorkingPaper(wp);
}

export function createCapa(findingId: string, kind: 'CORRECTIVE' | 'PREVENTIVE', titleEn: string, titleAr: string, ownerRole: string): CapaTask {
  const id = `CAPA-${new Date().getFullYear()}-${String(loadCapas().length + 1).padStart(3, '0')}`;
  const c: CapaTask = {
    id, findingId, kind,
    titleEn, titleAr, ownerRole,
    targetDate: new Date(Date.now() + 30 * 86_400_000).toISOString().slice(0, 10),
    status: 'OPEN',
  };
  saveCapa(c);
  // Link CAPA to its finding.
  const f = loadFindings().find(x => x.id === findingId);
  if (f) saveFinding({ ...f, capaIds: [...f.capaIds, id] });
  return c;
}

export function transitionFinding(id: string, status: FindingStatus): Finding | null {
  const f = loadFindings().find(x => x.id === id);
  if (!f) return null;
  // Mirror through backend's /transition endpoint — server enforces ALLOWED map.
  mirrorTransitionToBackend(AUDIT_FINDING_ADAPTER, id, { status });
  return saveFinding({ ...f, status });
}

export function loadCapas(): CapaTask[] {
  if (typeof window === 'undefined') return SEED_CAPAS;
  try {
    const raw = window.localStorage.getItem(CAPA_KEY);
    return raw ? JSON.parse(raw) as CapaTask[] : SEED_CAPAS;
  } catch { return SEED_CAPAS; }
}

export function capasForFinding(findingId: string): CapaTask[] {
  return loadCapas().filter(c => c.findingId === findingId);
}

export function saveCapa(c: CapaTask): CapaTask {
  const all = loadCapas().filter(x => x.id !== c.id);
  const next = [...all, c];
  try { window.localStorage.setItem(CAPA_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return c;
}

export function transitionCapa(id: string, status: CapaStatus, evidenceRef?: string, notesEn?: string, notesAr?: string): CapaTask | null {
  const c = loadCapas().find(x => x.id === id);
  if (!c) return null;
  return saveCapa({
    ...c, status,
    closedAt: status === 'CLOSED' ? new Date().toISOString() : c.closedAt,
    evidenceRef: evidenceRef ?? c.evidenceRef,
    verificationNotesEn: notesEn ?? c.verificationNotesEn,
    verificationNotesAr: notesAr ?? c.verificationNotesAr,
  });
}

export function subscribeAuditLifecycle(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  const storage = (e: StorageEvent) => {
    if ([PLAN_KEY, WP_KEY, FINDING_KEY, CAPA_KEY].includes(e.key ?? '')) cb();
  };
  window.addEventListener('storage', storage);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storage);
  };
}

function emit() { try { window.dispatchEvent(new CustomEvent(EVENT)); } catch { /* ignore */ } }

// ─── AC packet generator ───────────────────────────────────────────────────

export function buildAcPacket(orgEn: string, orgAr: string, quarter: string): string {
  const plan = loadAnnualPlan();
  const wps = loadWorkingPapers();
  const findings = loadFindings();
  const capas = loadCapas();

  const lines: string[] = [];
  const div = '─'.repeat(64);
  const dt = (iso: string) => new Date(iso).toLocaleString('en-GB', { timeZone: 'Asia/Riyadh' });

  lines.push(div);
  lines.push('AUDIT COMMITTEE EXECUTIVE PACKET');
  lines.push('حزمة لجنة المراجعة التنفيذية');
  lines.push(div);
  lines.push(`Reporting entity: ${orgEn}`);
  lines.push(`الجهة          : ${orgAr}`);
  lines.push(`Quarter        : ${quarter}`);
  lines.push(`Generated      : ${new Date().toISOString()}`);
  lines.push('');

  lines.push('1. ANNUAL AUDIT PLAN STATUS · حالة خطة التدقيق السنوية');
  lines.push(`   Plan ID  : ${plan.id} (${plan.year})`);
  lines.push(`   Status   : ${plan.status}`);
  lines.push(`   Approved : ${plan.acApprovedBy ?? '—'} on ${plan.acApprovedAt ? dt(plan.acApprovedAt) : '—'}`);
  lines.push(`   Budget   : ${plan.budgetHours.toLocaleString()} hours`);
  lines.push(`   Q1 (${plan.q1.length}): ${plan.q1.join(', ')}`);
  lines.push(`   Q2 (${plan.q2.length}): ${plan.q2.join(', ')}`);
  lines.push(`   Q3 (${plan.q3.length}): ${plan.q3.join(', ')}`);
  lines.push(`   Q4 (${plan.q4.length}): ${plan.q4.join(', ')}`);
  lines.push('');

  lines.push('2. ENGAGEMENT PROGRESS · تقدّم الارتباطات');
  MOCK_ENGAGEMENTS.slice(0, 8).forEach(e => {
    const wpsForEng = wps.filter(w => w.engagementCode === e.engagementCode);
    const findingsForEng = findings.filter(f => f.engagementCode === e.engagementCode);
    lines.push(`   [${e.engagementCode}] ${e.titleEn} / ${e.titleAr}`);
    lines.push(`     Status: ${e.status} · WPs: ${wpsForEng.length} · Findings: ${findingsForEng.length}`);
  });
  lines.push('');

  lines.push('3. FINDINGS BY SEVERITY · الملاحظات بحسب الخطورة');
  const sevCounts: Record<FindingSeverity, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, OBSERVATION: 0 };
  findings.forEach(f => sevCounts[f.severity]++);
  Object.entries(sevCounts).forEach(([sev, n]) => lines.push(`   ${sev.padEnd(12)}: ${n}`));
  const overdue = findings.filter(f => f.targetDate && new Date(f.targetDate) < new Date() && f.status !== 'CLOSED').length;
  lines.push(`   Overdue     : ${overdue}`);
  lines.push('');

  lines.push('4. CAPA CLOSURE RATE · معدّل إغلاق إجراءات CAPA');
  const totalCapas = capas.length;
  const closedCapas = capas.filter(c => c.status === 'CLOSED').length;
  const overdueCapas = capas.filter(c => new Date(c.targetDate) < new Date() && c.status !== 'CLOSED').length;
  const closurePct = totalCapas > 0 ? Math.round((closedCapas / totalCapas) * 100) : 0;
  lines.push(`   Total tasks : ${totalCapas}`);
  lines.push(`   Closed      : ${closedCapas} (${closurePct}%)`);
  lines.push(`   In progress : ${capas.filter(c => c.status === 'IN_PROGRESS').length}`);
  lines.push(`   Overdue     : ${overdueCapas}`);
  lines.push('');

  lines.push('5. KEY FINDINGS DETAIL · تفاصيل أبرز الملاحظات');
  findings
    .filter(f => f.severity === 'CRITICAL' || f.severity === 'HIGH')
    .forEach(f => {
      lines.push(`   [${f.refNumber}] ${f.severity} · ${f.status}`);
      lines.push(`     EN: ${f.titleEn}`);
      lines.push(`     AR: ${f.titleAr}`);
      lines.push(`     Owner: ${f.ownerRole ?? '—'}`);
      lines.push(`     Target: ${f.targetDate ?? '—'}`);
      lines.push(`     Frameworks: ${f.controlCrosswalk.map(c => `${c.framework}/${c.controlCode}`).join(', ')}`);
    });
  lines.push('');

  lines.push(div);
  lines.push('Generated by TSS GRC Platform');
  lines.push(div);
  return lines.join('\n');
}

// ─── Display helpers ───────────────────────────────────────────────────────

export const PLAN_STATUS_LABEL: Record<AnnualPlanStatus, { en: string; ar: string }> = {
  DRAFT:        { en: 'Draft',          ar: 'مسوّدة' },
  AC_REVIEW:    { en: 'AC review',      ar: 'مراجعة لجنة المراجعة' },
  AC_APPROVED:  { en: 'AC approved',    ar: 'مُعتمَدة من لجنة المراجعة' },
  EXECUTING:    { en: 'Executing',      ar: 'قيد التنفيذ' },
  CLOSED:       { en: 'Closed',         ar: 'مُغلَقة' },
};

export const PLAN_STATUS_TONE: Record<AnnualPlanStatus, 'neutral' | 'info' | 'success' | 'primary'> = {
  DRAFT: 'neutral', AC_REVIEW: 'info', AC_APPROVED: 'success', EXECUTING: 'primary', CLOSED: 'neutral',
};

export const WP_TYPE_LABEL: Record<WorkingPaperType, { en: string; ar: string }> = {
  TEST:            { en: 'Test',             ar: 'اختبار' },
  INTERVIEW:       { en: 'Interview',        ar: 'مقابلة' },
  OBSERVATION:     { en: 'Observation',      ar: 'ملاحظة' },
  DOCUMENT_REVIEW: { en: 'Document review',  ar: 'مراجعة مستند' },
  WALKTHROUGH:     { en: 'Walkthrough',      ar: 'تجوال' },
};

export const WP_STATUS_TONE: Record<WorkingPaperStatus, 'neutral' | 'warning' | 'success' | 'danger'> = {
  DRAFT: 'neutral', PEER_REVIEW: 'warning', APPROVED: 'success', REJECTED: 'danger',
};

export const SEVERITY_TONE: Record<FindingSeverity, 'danger' | 'warning' | 'info' | 'success' | 'neutral'> = {
  CRITICAL: 'danger', HIGH: 'danger', MEDIUM: 'warning', LOW: 'info', OBSERVATION: 'neutral',
};

export const FINDING_STATUS_LABEL: Record<FindingStatus, { en: string; ar: string }> = {
  OPEN:                  { en: 'Open',                  ar: 'مفتوحة' },
  MGMT_RESPONSE:         { en: 'Mgmt response',         ar: 'ردّ الإدارة' },
  IN_REMEDIATION:        { en: 'In remediation',        ar: 'قيد المعالجة' },
  AWAITING_VERIFICATION: { en: 'Awaiting verification', ar: 'بانتظار التحقق' },
  CLOSED:                { en: 'Closed',                ar: 'مُغلَقة' },
};

export const CAPA_STATUS_TONE: Record<CapaStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  OPEN: 'neutral', IN_PROGRESS: 'info', AWAITING_VERIFICATION: 'warning', CLOSED: 'success', OVERDUE: 'danger',
};

export const CAPA_STATUS_LABEL: Record<CapaStatus, { en: string; ar: string }> = {
  OPEN:                  { en: 'Open',                  ar: 'مفتوحة' },
  IN_PROGRESS:           { en: 'In progress',           ar: 'قيد التنفيذ' },
  AWAITING_VERIFICATION: { en: 'Awaiting verification', ar: 'بانتظار التحقق' },
  CLOSED:                { en: 'Closed',                ar: 'مُغلَقة' },
  OVERDUE:               { en: 'Overdue',               ar: 'متأخّرة' },
};

// ─── Aggregation ────────────────────────────────────────────────────────────

export interface AuditLifecycleStats {
  totalEngagements: number;
  engagementsInFlight: number;
  workingPapersTotal: number;
  workingPapersInReview: number;
  findingsOpen: number;
  findingsCritical: number;
  capaTotal: number;
  capaClosed: number;
  capaOverdue: number;
  closureRate: number;
}

export function aggregateAuditLifecycle(): AuditLifecycleStats {
  const findings = loadFindings();
  const capas = loadCapas();
  const wps = loadWorkingPapers();
  const engagementsInFlight = MOCK_ENGAGEMENTS.filter(e => e.status === 'PLANNED' || e.status === 'FIELDWORK').length;
  const findingsOpen = findings.filter(f => f.status !== 'CLOSED').length;
  const findingsCritical = findings.filter(f => f.severity === 'CRITICAL' || f.severity === 'HIGH').length;
  const capaClosed = capas.filter(c => c.status === 'CLOSED').length;
  const capaOverdue = capas.filter(c => new Date(c.targetDate) < new Date() && c.status !== 'CLOSED').length;
  return {
    totalEngagements: MOCK_ENGAGEMENTS.length,
    engagementsInFlight,
    workingPapersTotal: wps.length,
    workingPapersInReview: wps.filter(w => w.status === 'PEER_REVIEW').length,
    findingsOpen,
    findingsCritical,
    capaTotal: capas.length,
    capaClosed,
    capaOverdue,
    closureRate: capas.length > 0 ? Math.round((capaClosed / capas.length) * 100) : 0,
  };
}

export function pickEngagementByCode(code: string): AuditEngagementDto | undefined {
  return MOCK_ENGAGEMENTS.find(e => e.engagementCode === code);
}
