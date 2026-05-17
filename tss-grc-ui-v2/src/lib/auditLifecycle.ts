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
//
// Persistence: in-memory module cache hydrated once from /api/audit-lc/*
// (annual-plans / working-papers / findings / capas). Writes are sync to
// the caller and fire-and-forget to the backend. The page subscribes via
// {@link subscribeAuditLifecycle} and triggers re-renders through the
// emit() channel whenever the cache changes.

import { api, ApiError } from './api';
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

// ─── Backend wire types ─────────────────────────────────────────────────────
//
// These mirror the JPA entities serialised through Jackson — they use CSV
// strings for the array-shaped frontend fields (q1_codes, controlsCovered,
// capaIds) and split-out timestamps. We never expose these to the page; the
// from*/to* adapters below are the only translation surface.

interface BackendAnnualPlan {
  id?: number;
  externalId: string;
  organisationId?: number;
  year: number;
  status: AnnualPlanStatus;
  lastUpdated?: string;
  acApprovedBy?: string;
  acApprovedAt?: string;
  q1Codes?: string;
  q2Codes?: string;
  q3Codes?: string;
  q4Codes?: string;
  budgetHours?: number;
  acNotesEn?: string;
  acNotesAr?: string;
}

interface BackendWorkingPaper {
  id?: number;
  externalId: string;
  organisationId?: number;
  engagementCode: string;
  type: WorkingPaperType;
  refNumber: string;
  titleEn: string;
  titleAr: string;
  author: string;
  createdAt?: string;
  status: WorkingPaperStatus;
  reviewer?: string;
  reviewedAt?: string;
  procedureEn?: string;
  procedureAr?: string;
  conclusionEn?: string;
  conclusionAr?: string;
  controlsCovered?: string;       // CSV
  hours?: number | string;        // BigDecimal serialises as either
}

interface BackendCapaTask {
  id?: number;
  externalId: string;
  organisationId?: number;
  findingId: string;
  kind: 'CORRECTIVE' | 'PREVENTIVE';
  titleEn: string;
  titleAr: string;
  ownerRole: string;
  targetDate: string;
  status: CapaStatus;
  closedAt?: string;
  verificationNotesEn?: string;
  verificationNotesAr?: string;
  evidenceRef?: string;
}

interface BackendFinding {
  id: string;                 // FindingDto exposes external_id as `id`
  engagementCode: string;
  refNumber?: string;
  titleEn: string;
  titleAr: string;
  severity: FindingSeverity;
  status: FindingStatus;
  controlCrosswalk?: string;  // JSON
  conditionEn?: string;
  conditionAr?: string;
  criteriaEn?: string;
  criteriaAr?: string;
  causeEn?: string;
  causeAr?: string;
  recommendationEn?: string;
  recommendationAr?: string;
  mgmtResponseEn?: string;
  mgmtResponseAr?: string;
  mgmtResponseAuthor?: string;
  mgmtAccepts?: boolean;
  targetDate?: string;
  ownerRole?: string;
  raisedAt?: string;
  capaIds?: string;           // CSV
}

// ─── Adapters ───────────────────────────────────────────────────────────────

function splitCsv(s: string | undefined): string[] {
  if (!s) return [];
  return s.split(',').map(t => t.trim()).filter(Boolean);
}

function joinCsv(xs: string[] | undefined): string {
  return (xs ?? []).join(',');
}

function parseCrosswalk(json: string | undefined): { framework: string; controlCode: string }[] {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json) as unknown;
    if (Array.isArray(parsed)) return parsed as { framework: string; controlCode: string }[];
    return [];
  } catch { return []; }
}

function fromBackendPlan(b: BackendAnnualPlan): AnnualPlan {
  return {
    id: b.externalId,
    year: b.year,
    status: b.status,
    lastUpdated: b.lastUpdated ?? new Date().toISOString(),
    acApprovedBy: b.acApprovedBy,
    acApprovedAt: b.acApprovedAt,
    q1: splitCsv(b.q1Codes),
    q2: splitCsv(b.q2Codes),
    q3: splitCsv(b.q3Codes),
    q4: splitCsv(b.q4Codes),
    budgetHours: b.budgetHours ?? 0,
    acNotesEn: b.acNotesEn,
    acNotesAr: b.acNotesAr,
  };
}

function toBackendPlan(p: AnnualPlan): Partial<BackendAnnualPlan> {
  return {
    externalId: p.id,
    year: p.year,
    status: p.status,
    acApprovedBy: p.acApprovedBy,
    acApprovedAt: p.acApprovedAt,
    q1Codes: joinCsv(p.q1),
    q2Codes: joinCsv(p.q2),
    q3Codes: joinCsv(p.q3),
    q4Codes: joinCsv(p.q4),
    budgetHours: p.budgetHours,
    acNotesEn: p.acNotesEn,
    acNotesAr: p.acNotesAr,
  };
}

function fromBackendWp(b: BackendWorkingPaper): WorkingPaper {
  return {
    id: b.externalId,
    engagementCode: b.engagementCode,
    type: b.type,
    refNumber: b.refNumber,
    titleEn: b.titleEn,
    titleAr: b.titleAr,
    author: b.author,
    createdAt: b.createdAt ?? new Date().toISOString(),
    status: b.status,
    reviewer: b.reviewer,
    reviewedAt: b.reviewedAt,
    procedureEn: b.procedureEn ?? '',
    procedureAr: b.procedureAr ?? '',
    conclusionEn: b.conclusionEn ?? '',
    conclusionAr: b.conclusionAr ?? '',
    controlsCovered: splitCsv(b.controlsCovered),
    hours: typeof b.hours === 'string' ? parseFloat(b.hours) : (b.hours ?? 0),
  };
}

function toBackendWp(w: WorkingPaper): Partial<BackendWorkingPaper> {
  return {
    externalId: w.id,
    engagementCode: w.engagementCode,
    type: w.type,
    refNumber: w.refNumber,
    titleEn: w.titleEn,
    titleAr: w.titleAr,
    author: w.author,
    createdAt: w.createdAt,
    status: w.status,
    reviewer: w.reviewer,
    reviewedAt: w.reviewedAt,
    procedureEn: w.procedureEn,
    procedureAr: w.procedureAr,
    conclusionEn: w.conclusionEn,
    conclusionAr: w.conclusionAr,
    controlsCovered: joinCsv(w.controlsCovered),
    hours: w.hours,
  };
}

function fromBackendCapa(b: BackendCapaTask): CapaTask {
  return {
    id: b.externalId,
    findingId: b.findingId,
    kind: b.kind,
    titleEn: b.titleEn,
    titleAr: b.titleAr,
    ownerRole: b.ownerRole,
    targetDate: b.targetDate,
    status: b.status,
    closedAt: b.closedAt,
    verificationNotesEn: b.verificationNotesEn,
    verificationNotesAr: b.verificationNotesAr,
    evidenceRef: b.evidenceRef,
  };
}

function toBackendCapa(c: CapaTask): Partial<BackendCapaTask> {
  return {
    externalId: c.id,
    findingId: c.findingId,
    kind: c.kind,
    titleEn: c.titleEn,
    titleAr: c.titleAr,
    ownerRole: c.ownerRole,
    targetDate: c.targetDate,
    status: c.status,
    closedAt: c.closedAt,
    verificationNotesEn: c.verificationNotesEn,
    verificationNotesAr: c.verificationNotesAr,
    evidenceRef: c.evidenceRef,
  };
}

function fromBackendFinding(b: BackendFinding): Finding {
  return {
    id: b.id,
    engagementCode: b.engagementCode,
    refNumber: b.refNumber ?? '',
    titleEn: b.titleEn,
    titleAr: b.titleAr,
    severity: b.severity,
    status: b.status,
    controlCrosswalk: parseCrosswalk(b.controlCrosswalk),
    conditionEn: b.conditionEn ?? '',
    conditionAr: b.conditionAr ?? '',
    criteriaEn: b.criteriaEn ?? '',
    criteriaAr: b.criteriaAr ?? '',
    causeEn: b.causeEn ?? '',
    causeAr: b.causeAr ?? '',
    recommendationEn: b.recommendationEn ?? '',
    recommendationAr: b.recommendationAr ?? '',
    mgmtResponseEn: b.mgmtResponseEn,
    mgmtResponseAr: b.mgmtResponseAr,
    mgmtResponseAuthor: b.mgmtResponseAuthor,
    mgmtAccepts: b.mgmtAccepts,
    targetDate: b.targetDate,
    ownerRole: b.ownerRole,
    raisedAt: b.raisedAt ?? new Date().toISOString(),
    capaIds: splitCsv(b.capaIds),
  };
}

// ─── Seeded fallback data ───────────────────────────────────────────────────
// Used as the in-memory default before hydrate completes — the AC packet
// and aggregation must always have something to compute against even when
// the backend hasn't been reached yet (login screen, dev mock mode, etc.).

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
    controlCrosswalk: [{ framework: 'NCA-ECC-V3', controlCode: 'ECC-2-3-1' }],
    conditionEn: 'Sample testing of 25 admin accounts revealed 2 accounts (8%) without MFA enrolled.',
    conditionAr: 'اختبار عيّنة من 25 حساب مشرف كشف 2 حسابات (8٪) دون تسجيل المصادقة متعدّدة العوامل.',
    criteriaEn: 'NCA ECC v3 control 2-3-1 mandates multi-factor authentication on all privileged accounts.',
    criteriaAr: 'يُلزم ضابط NCA ECC v3 رقم 2-3-1 بالمصادقة متعدّدة العوامل على جميع الحسابات المُميَّزة.',
    causeEn: 'Service-account exemption process was undocumented.',
    causeAr: 'عملية استثناء حسابات الخدمة كانت غير موثَّقة.',
    recommendationEn: 'Enrol the 2 affected accounts in MFA within 7 days.',
    recommendationAr: 'تسجيل الحسابَين في MFA خلال 7 أيام.',
    targetDate: _daysAhead(14),
    ownerRole: 'CISO',
    raisedAt: _daysAgo(10),
    capaIds: ['CAPA-2026-001'],
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
    verificationNotesEn: 'Verified — both accounts now in MFA.',
    verificationNotesAr: 'تمّ التحقق — الحسابان الآن في MFA.',
    evidenceRef: 'EVD-2026-MFA-217',
  },
];

// ─── In-memory cache + hydrate ──────────────────────────────────────────────

let _plan: AnnualPlan | null = null;
let _wps: WorkingPaper[] = [];
let _findings: Finding[] = [];
let _capas: CapaTask[] = [];
let _hydratePromise: Promise<void> | null = null;
let _hydrated = false;

const EVENT = 'grc.audit.lifecycle.changed';

/**
 * Fetch the four audit-lifecycle resources from the backend and seed the
 * in-memory cache. Safe to call multiple times — only the first call
 * actually hits the API; later calls return the same promise.
 *
 * <p>Fall back to the in-memory SEED_* constants on network failure so
 * the page renders something even in offline / unauthenticated mode.
 */
export function hydrateAuditLifecycle(force = false): Promise<void> {
  if (_hydratePromise && !force) return _hydratePromise;
  _hydratePromise = (async () => {
    try {
      const plans    = await api.get<BackendAnnualPlan[]>('/api/audit-lc/annual-plans').catch(logAndEmpty<BackendAnnualPlan>);
      const wps      = await api.get<BackendWorkingPaper[]>('/api/audit-lc/working-papers').catch(logAndEmpty<BackendWorkingPaper>);
      const findings = await api.get<BackendFinding[]>('/api/audit-lc/findings').catch(logAndEmpty<BackendFinding>);
      const capas    = await api.get<BackendCapaTask[]>('/api/audit-lc/capas').catch(logAndEmpty<BackendCapaTask>);
      _plan = (plans.length > 0) ? fromBackendPlan(plans[0]) : SEED_PLAN;
      _wps      = wps.map(fromBackendWp);
      _findings = findings.map(fromBackendFinding);
      _capas    = capas.map(fromBackendCapa);
      if (_wps.length === 0)      _wps      = SEED_WPS;
      if (_findings.length === 0) _findings = SEED_FINDINGS;
      if (_capas.length === 0)    _capas    = SEED_CAPAS;
      _hydrated = true;
      emit();
    } catch (e) {
      console.warn('[auditLifecycle] hydrate failed, falling back to seed:', e);
      _plan = SEED_PLAN; _wps = SEED_WPS; _findings = SEED_FINDINGS; _capas = SEED_CAPAS;
      _hydrated = true;
      emit();
    }
  })();
  return _hydratePromise;
}

function logAndEmpty<T>(e: unknown): T[] {
  if (!(e instanceof ApiError) || e.status >= 500) {
    console.warn('[auditLifecycle] backend fetch failed:', e);
  }
  return [] as T[];
}

/** Has hydrate() finished at least once? Exposed for tests / sanity checks. */
export function isAuditLifecycleHydrated(): boolean { return _hydrated; }

// ─── Annual plan ────────────────────────────────────────────────────────────

export function loadAnnualPlan(): AnnualPlan {
  return _plan ?? SEED_PLAN;
}

export function saveAnnualPlan(plan: AnnualPlan): AnnualPlan {
  const next = { ...plan, lastUpdated: new Date().toISOString() };
  _plan = next;
  emit();
  // Fire-and-forget — try update first; if the row doesn't exist on the
  // backend (404), fall back to create.
  void api.put<BackendAnnualPlan>(`/api/audit-lc/annual-plans/${encodeURIComponent(next.id)}`, toBackendPlan(next))
    .catch(async (err) => {
      if (err instanceof ApiError && err.status === 404) {
        try { await api.post<BackendAnnualPlan>('/api/audit-lc/annual-plans', toBackendPlan(next)); }
        catch (e) { console.warn('[auditLifecycle] save plan create-fallback failed:', e); }
      } else {
        console.warn('[auditLifecycle] save plan failed:', err);
      }
    });
  return next;
}

export function transitionPlan(status: AnnualPlanStatus, by?: string): AnnualPlan {
  const plan = loadAnnualPlan();
  const updated: AnnualPlan = { ...plan, status };
  if (status === 'AC_APPROVED') { updated.acApprovedBy = by; updated.acApprovedAt = new Date().toISOString(); }
  _plan = { ...updated, lastUpdated: new Date().toISOString() };
  emit();
  // Server enforces the legal transitions; if it rejects, fall back to a
  // full save so the displayed status doesn't drift from the server.
  void api.post<BackendAnnualPlan>(
    `/api/audit-lc/annual-plans/${encodeURIComponent(updated.id)}/transition`,
    { to: status })
    .catch(err => {
      if (err instanceof ApiError && err.status === 404) {
        void api.post<BackendAnnualPlan>('/api/audit-lc/annual-plans', toBackendPlan(updated));
      } else {
        console.warn('[auditLifecycle] transition plan failed:', err);
      }
    });
  return _plan;
}

// ─── Working papers ─────────────────────────────────────────────────────────

export function loadWorkingPapers(): WorkingPaper[] {
  return _wps.length > 0 ? _wps : SEED_WPS;
}

export function workingPapersForEngagement(code: string): WorkingPaper[] {
  return loadWorkingPapers().filter(w => w.engagementCode === code);
}

export function saveWorkingPaper(wp: WorkingPaper): WorkingPaper {
  const exists = _wps.some(w => w.id === wp.id);
  _wps = [..._wps.filter(w => w.id !== wp.id), wp];
  emit();
  if (exists) {
    void api.put<BackendWorkingPaper>(`/api/audit-lc/working-papers/${encodeURIComponent(wp.id)}`, toBackendWp(wp))
      .catch(e => console.warn('[auditLifecycle] save wp failed:', e));
  } else {
    void api.post<BackendWorkingPaper>('/api/audit-lc/working-papers', toBackendWp(wp))
      .catch(e => console.warn('[auditLifecycle] create wp failed:', e));
  }
  return wp;
}

export function transitionWorkingPaper(id: string, status: WorkingPaperStatus, reviewer?: string): WorkingPaper | null {
  const wp = loadWorkingPapers().find(w => w.id === id);
  if (!wp) return null;
  const updated: WorkingPaper = {
    ...wp, status,
    reviewer: status === 'APPROVED' || status === 'REJECTED' ? reviewer ?? wp.reviewer : wp.reviewer,
    reviewedAt: status === 'APPROVED' || status === 'REJECTED' ? new Date().toISOString() : wp.reviewedAt,
  };
  _wps = _wps.map(w => w.id === id ? updated : w);
  emit();
  void api.post<BackendWorkingPaper>(
    `/api/audit-lc/working-papers/${encodeURIComponent(id)}/transition`,
    { to: status })
    .catch(e => console.warn('[auditLifecycle] transition wp failed:', e));
  return updated;
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

// ─── Findings ───────────────────────────────────────────────────────────────

export function loadFindings(): Finding[] {
  return _findings.length > 0 ? _findings : SEED_FINDINGS;
}

export function findingsForEngagement(code: string): Finding[] {
  return loadFindings().filter(f => f.engagementCode === code);
}

export function saveFinding(f: Finding): Finding {
  const isNew = !_findings.some(x => x.id === f.id);
  _findings = [..._findings.filter(x => x.id !== f.id), f];
  emit();
  // Findings only have create/transition on the backend — full-field edits
  // mirror through the existing AUDIT_FINDING_ADAPTER (best-effort POST).
  mirrorSaveToBackend(AUDIT_FINDING_ADAPTER, f, isNew);
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

export function transitionFinding(id: string, status: FindingStatus): Finding | null {
  const f = loadFindings().find(x => x.id === id);
  if (!f) return null;
  const updated = { ...f, status };
  _findings = _findings.map(x => x.id === id ? updated : x);
  emit();
  mirrorTransitionToBackend(AUDIT_FINDING_ADAPTER, id, { status });
  return updated;
}

// ─── CAPAs ──────────────────────────────────────────────────────────────────

export function loadCapas(): CapaTask[] {
  return _capas.length > 0 ? _capas : SEED_CAPAS;
}

export function capasForFinding(findingId: string): CapaTask[] {
  return loadCapas().filter(c => c.findingId === findingId);
}

export function saveCapa(c: CapaTask): CapaTask {
  const exists = _capas.some(x => x.id === c.id);
  _capas = [..._capas.filter(x => x.id !== c.id), c];
  emit();
  if (exists) {
    void api.put<BackendCapaTask>(`/api/audit-lc/capas/${encodeURIComponent(c.id)}`, toBackendCapa(c))
      .catch(e => console.warn('[auditLifecycle] save capa failed:', e));
  } else {
    void api.post<BackendCapaTask>('/api/audit-lc/capas', toBackendCapa(c))
      .catch(e => console.warn('[auditLifecycle] create capa failed:', e));
  }
  return c;
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

export function transitionCapa(id: string, status: CapaStatus, evidenceRef?: string, notesEn?: string, notesAr?: string): CapaTask | null {
  const c = loadCapas().find(x => x.id === id);
  if (!c) return null;
  const updated: CapaTask = {
    ...c, status,
    closedAt: status === 'CLOSED' ? new Date().toISOString() : c.closedAt,
    evidenceRef: evidenceRef ?? c.evidenceRef,
    verificationNotesEn: notesEn ?? c.verificationNotesEn,
    verificationNotesAr: notesAr ?? c.verificationNotesAr,
  };
  _capas = _capas.map(x => x.id === id ? updated : x);
  emit();
  void api.post<BackendCapaTask>(
    `/api/audit-lc/capas/${encodeURIComponent(id)}/transition`,
    { to: status })
    .catch(e => console.warn('[auditLifecycle] transition capa failed:', e));
  // Persist verification notes / evidence separately (transition endpoint
  // doesn't accept them on the backend) — fall back to a PUT.
  if (notesEn || notesAr || evidenceRef) {
    void api.put<BackendCapaTask>(`/api/audit-lc/capas/${encodeURIComponent(id)}`, toBackendCapa(updated))
      .catch(() => { /* already logged */ });
  }
  return updated;
}

// ─── Subscriptions ──────────────────────────────────────────────────────────

export function subscribeAuditLifecycle(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  // Kick off the hydrate if no subscriber has yet — first subscriber
  // is typically AuditLifecyclePage on mount, so this gives us a real-data
  // first render without extra wiring at the call site.
  if (!_hydratePromise) void hydrateAuditLifecycle();
  return () => {
    window.removeEventListener(EVENT, handler);
  };
}

function emit() {
  try { window.dispatchEvent(new CustomEvent(EVENT)); } catch { /* ignore */ }
}

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
