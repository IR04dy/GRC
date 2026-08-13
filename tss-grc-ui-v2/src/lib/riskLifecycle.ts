// Risk lifecycle — production hardening (Option 1 of "build the three").
//
// Brings the Risk Register from a CRUD list to a working risk-program
// pipeline. Per-risk we track:
//
//   1. Quantitative scoring — simplified Monte Carlo over likelihood × impact
//      probability distributions, producing a residual-risk distribution and
//      Value-at-Risk style 95th-percentile loss number.
//   2. KRI breach pipeline — when a KRI threshold fires, an escalation event
//      is created with auto-routing to the responsible role per Saudi
//      governance (CRO → Risk Committee → Board within 30 days).
//   3. Treatment plan workflow — state machine DRAFT → SUBMITTED → APPROVED
//      → IN_PROGRESS → COMPLETED, with target dates and evidence-collection
//      hooks tied to CCM.
//   4. Risk acceptance approval — for residual risks above appetite,
//      Board-level dual attestation with Hijri-aware sign-off dates and
//      auto-expiry (re-attestation required annually per CMA Art. 75).
//   5. Risk-control linkage — which controls mitigate this risk, what their
//      CCM coverage is, and what evidence was last collected.

import { ApiError, treatmentPlanApi, TREATMENT_ALLOWED, type TreatmentPlanDto } from './api';
import { MOCK_RISKS } from '../pages/risk/mockRisks';
import type { RiskResponse } from './api';
// F2-202: the `platformPersistence` import that used to sit here is gone with the file.
// This store is BROWSER_LOCAL — declared, with its real endpoint and measured row count,
// in `lib/lifecycleStores.ts`.

export type TreatmentStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'IN_PROGRESS' | 'COMPLETED' | 'REJECTED';
export type AcceptanceStatus = 'DRAFT' | 'CRO_REVIEW' | 'BOARD_APPROVAL' | 'APPROVED' | 'EXPIRED' | 'WITHDRAWN';
export type TreatmentStrategy = 'MITIGATE' | 'TRANSFER' | 'AVOID' | 'ACCEPT';

export interface TreatmentAction {
  id: string;
  titleEn: string;
  titleAr: string;
  ownerRole: string;
  /** ISO target completion date. */
  targetDate: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'BLOCKED';
  /** Reduction in residual risk score expected on completion (0-1). */
  expectedRiskReduction: number;
  /** Actual completion timestamp. */
  completedAt?: string;
  /** Evidence reference (file id / control code). */
  evidenceRef?: string;
}

export interface TreatmentPlan {
  id: string;
  riskId: number;
  strategy: TreatmentStrategy;
  rationaleEn: string;
  rationaleAr: string;
  status: TreatmentStatus;
  /** Estimated cost in SAR thousands. */
  costSarK: number;
  /** Bilingual cost-benefit summary. */
  costBenefitEn: string;
  costBenefitAr: string;
  /**
   * ⚠⚠ F2-204 CORRECTION. The server DOES send these — `TreatmentPlanDto` declares
   * `List<TreatmentActionDto> actions` and maps all 10 `risk_lc.treatment_actions` rows on every
   * read. An earlier draft of this comment said the opposite, because I read the FRONTEND
   * `TreatmentPlanDto` interface in `lib/api.ts`, which had simply never declared the field
   * (F2-198's `crossBorderTransfer` shape). The backend half of the lifecycle-store gate refuted
   * it by finding the `TreatmentAction` @Entity. 🔑 One file short is the whole error.
   *
   * What is genuinely missing is the WRITE: `TreatmentPlanController` has no actions
   * sub-resource, so no client can change an action's status. `addPlanAction` and
   * `updateActionStatus` are deleted for that reason and the "Mark done" control is gone —
   * an update that reports success and writes nothing is F2-201's defect. The list itself is
   * real and read-only. Filed: build the sub-resource.
   *
   * Optional because a plan created in the same session has none until it is re-read.
   */
  actions?: TreatmentAction[];
  createdAt: string;
  submittedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  rejectedReason?: string;
}

export interface KriBreachEvent {
  id: string;
  riskId: number;
  kriCode: string;
  kriNameEn: string;
  kriNameAr: string;
  /** Threshold value crossed. */
  thresholdValue: number;
  /** Actual observed value. */
  actualValue: number;
  /** Direction — UP_BAD or DOWN_BAD. */
  direction: 'UP_BAD' | 'DOWN_BAD';
  detectedAt: string;
  status: 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'ESCALATED';
  /** Owner currently assigned to action. */
  currentOwner: string;
  /** Escalation chain followed. */
  escalationLog: { stepEn: string; stepAr: string; toOwner: string; atTs: string }[];
  /** Resolution notes once closed. */
  resolutionEn?: string;
  resolutionAr?: string;
}

export interface AcceptanceRequest {
  id: string;
  riskId: number;
  /** Why we are accepting rather than treating. */
  rationaleEn: string;
  rationaleAr: string;
  /** Residual score above appetite — what we're asking the board to accept. */
  residualScore: number;
  appetiteUpper: number;
  status: AcceptanceStatus;
  /** Risk Officer signature. */
  croSignedBy?: string;
  croSignedAt?: string;
  /** Board / Audit Committee signature. */
  boardSignedBy?: string;
  boardSignedAt?: string;
  /** ISO date — acceptance auto-expires for re-attestation. */
  expiresAt: string;
  /** Hijri date string for the acceptance — required by CMA practice. */
  expiresAtHijri?: string;
  /** Free-text conditions of acceptance. */
  conditionsEn?: string;
  conditionsAr?: string;
  createdAt: string;
}

export interface MonteCarloInput {
  /** Triangular distribution params for likelihood (0-1 each). */
  pMin: number;
  pMode: number;
  pMax: number;
  /** Triangular distribution params for impact in SAR. */
  iMin: number;
  iMode: number;
  iMax: number;
  /** Number of trials. */
  trials: number;
}

export interface MonteCarloResult {
  /** Expected loss SAR (mean). */
  expectedLoss: number;
  /** 95th percentile loss SAR (Value-at-Risk). */
  p95Loss: number;
  /** 99th percentile loss SAR (tail risk). */
  p99Loss: number;
  /** Standard deviation. */
  stdev: number;
  /** Histogram for chart — 12 buckets. */
  histogram: { rangeStart: number; rangeEnd: number; count: number }[];
}

export interface ControlLinkage {
  /** Control code. */
  controlCode: string;
  controlTitleEn: string;
  controlTitleAr: string;
  /** Framework anchor. */
  framework: string;
  /** Maturity 1-5. */
  maturity: number;
  /** Last evidence collection date. */
  lastEvidenceAt?: string;
  /** CCM coverage % (continuous control monitoring). */
  ccmCoveragePct: number;
}

// ─── Storage keys ───────────────────────────────────────────────────────────

const KRI_BREACH_KEY = 'grc.risk.kribreach.v1';
const ACCEPT_KEY = 'grc.risk.accept.v1';
const EVENT = 'grc.risk.lifecycle.changed';

// ─── Seeded data per risk (deterministic) ───────────────────────────────────

/** Default control linkages computed deterministically per risk id. */
export function controlsForRisk(riskId: number): ControlLinkage[] {
  // Deterministic mapping from risk id to controls based on category seed.
  const risk = MOCK_RISKS.find(r => r.id === riskId);
  if (!risk) return [];
  const cat = risk.categoryNameEn ?? '';
  const linkages: ControlLinkage[] = [];
  if (cat.toLowerCase().includes('cyber')) {
    linkages.push(
      { controlCode: 'ECC-2-2-1', controlTitleEn: 'Identity and access management', controlTitleAr: 'إدارة الهويات والوصول', framework: 'NCA-ECC-V3', maturity: 4, lastEvidenceAt: '2026-04-12', ccmCoveragePct: 92 },
      { controlCode: 'ECC-2-3-1', controlTitleEn: 'Privileged access management', controlTitleAr: 'إدارة الوصول المُميَّز', framework: 'NCA-ECC-V3', maturity: 3, lastEvidenceAt: '2026-03-20', ccmCoveragePct: 78 },
      { controlCode: 'ECC-2-5-1', controlTitleEn: 'Vulnerability management', controlTitleAr: 'إدارة الثغرات', framework: 'NCA-ECC-V3', maturity: 4, lastEvidenceAt: '2026-04-30', ccmCoveragePct: 85 },
    );
  } else if (cat.toLowerCase().includes('compliance') || cat.toLowerCase().includes('privacy')) {
    linkages.push(
      { controlCode: 'PDPL-L-002', controlTitleEn: 'Record of Processing Activities (ROPA)', controlTitleAr: 'سجل أنشطة المعالجة', framework: 'PDPL', maturity: 4, lastEvidenceAt: '2026-03-15', ccmCoveragePct: 88 },
      { controlCode: 'PDPL-B-001', controlTitleEn: '72-hour breach notification to SDAIA', controlTitleAr: 'إشعار الخرق إلى سدايا خلال 72 ساعة', framework: 'PDPL', maturity: 5, lastEvidenceAt: '2026-04-22', ccmCoveragePct: 100 },
    );
  } else if (cat.toLowerCase().includes('third')) {
    linkages.push(
      { controlCode: 'ECC-4-1-1', controlTitleEn: 'Third-party risk assessment before contract', controlTitleAr: 'تقييم مخاطر الأطراف الثالثة قبل التعاقد', framework: 'NCA-ECC-V3', maturity: 3, lastEvidenceAt: '2026-02-28', ccmCoveragePct: 72 },
      { controlCode: 'RBSF-IT-001', controlTitleEn: 'IT outsourcing register and exit plans', controlTitleAr: 'سجل إسناد تقنية المعلومات وخطط الخروج', framework: 'SAMA-RBSF', maturity: 3, lastEvidenceAt: '2026-03-08', ccmCoveragePct: 80 },
    );
  } else {
    linkages.push(
      { controlCode: 'RBSF-OR-002', controlTitleEn: 'Risk and control self-assessment (RCSA)', controlTitleAr: 'التقييم الذاتي للمخاطر والضوابط', framework: 'SAMA-RBSF', maturity: 3, lastEvidenceAt: '2026-03-30', ccmCoveragePct: 75 },
      { controlCode: 'ECC-1-2-1', controlTitleEn: 'Cybersecurity policy approved by the board', controlTitleAr: 'سياسة الأمن السيبراني المعتمدة من مجلس الإدارة', framework: 'NCA-ECC-V3', maturity: 5, lastEvidenceAt: '2026-01-18', ccmCoveragePct: 100 },
    );
  }
  return linkages;
}

// ─── Persistence: treatment plans — SERVER-BACKED (F2-204) ───────────────────
//
// This half of the module used to read `grc.risk.treatment.v1` and fall back to
// `SEED_TREATMENT_PLANS`, while `risk_lc.treatment_plans` held **10 live rows in dev and
// prod** behind a complete, hardened controller: GET, GET /{id}, POST, POST /{id}/transition
// and DELETE, each guarded by `hasPermission('RISK', …)` under `@RequireModule("RISK")`, with
// a `Map<String, Set<String>> ALLOWED` machine answering 409 outside it. Same inverse shape
// as F2-201 and F2-203: a finished server-side control with no client.
//
// ⚠⚠ `transitionPlan` is an APPROVAL. `DRAFT → SUBMITTED → APPROVED/REJECTED → IN_PROGRESS
// → COMPLETED` is the ERM sign-off on how a risk will be treated, and every decision taken on
// that screen was written to localStorage and nowhere else.
//
// ⚠⚠ And the page invented the SUBSTANCE of the decision, not just the identity: it passed
// `'Mohammed Al-Mahmoud (CRO)'` as the approving Chief Risk Officer **and**
// `'Insufficient cost-benefit justification'` as the rejection reason. The server persists
// `rejectedReason`, so wiring this naively would have written invented text into
// `risk_lc.treatment_plans.rejected_reason` as a real officer's stated rationale. Both are
// gone: the server stamps the approver from the authenticated principal (F2-22b(vi)), and a
// rejection reason now comes from the rejecting officer or not at all.
//
// ⚙ `actions` is DELETED from `TreatmentPlan`. `TreatmentPlanDto` has no such field, there is
// no actions sub-resource on the controller, and `risk_lc.treatment_actions` holds **10 rows
// no endpoint serves**. Reconstructing them client-side would be F2-196's defect and keeping
// the editor would be F2-201's — an update that reports success and writes nothing. The page
// discloses that action tracking has no server surface; the 10 rows are filed.

/** Server rows, cached. Empty until {@link hydrateTreatmentPlans} resolves. */
let _plans: TreatmentPlan[] = [];
let _plansHydrated = false;
let _plansLoadFailed = false;
let _plansPromise: Promise<void> | null = null;

function fromDto(d: TreatmentPlanDto): TreatmentPlan {
  return {
    id: d.id,
    riskId: d.riskId,
    strategy: d.strategy as TreatmentStrategy,
    rationaleEn: d.rationaleEn,
    rationaleAr: d.rationaleAr,
    status: d.status as TreatmentStatus,
    costSarK: d.costSarK,
    costBenefitEn: d.costBenefitEn ?? '',
    costBenefitAr: d.costBenefitAr ?? '',
    createdAt: d.createdAt ?? '',
    submittedAt: d.submittedAt ?? undefined,
    approvedBy: d.approvedBy ?? undefined,
    approvedAt: d.approvedAt ?? undefined,
    rejectedReason: d.rejectedReason ?? undefined,
    // F2-204: real server rows, and the reason this mapping exists at all — see the note on
    // TreatmentPlan.actions for the claim it corrects.
    actions: (d.actions ?? []).map(a => ({
      id: a.id,
      titleEn: a.titleEn,
      titleAr: a.titleAr,
      ownerRole: a.ownerRole,
      targetDate: a.targetDate,
      status: a.status as TreatmentAction['status'],
      expectedRiskReduction: a.expectedRiskReduction,
      completedAt: a.completedAt ?? undefined,
      evidenceRef: a.evidenceRef ?? undefined,
    })),
  };
}

/**
 * Fetch the treatment-plan register. Safe to call repeatedly unless `force`.
 *
 * There is no seed fallback: an empty register reads as empty and an unreachable one reads as
 * a failure. The old `catch → SEED_TREATMENT_PLANS` collapsed those two facts into one
 * plausible screen, which is the F2-158 shape.
 */
export function hydrateTreatmentPlans(force = false): Promise<void> {
  if (_plansPromise && !force) return _plansPromise;
  _plansLoadFailed = false;
  _plansPromise = treatmentPlanApi.listForOrg()
    .then(rows => { _plans = rows.map(fromDto); _plansHydrated = true; emit(); })
    .catch(e => {
      if (!(e instanceof ApiError) || e.status !== 403) _plansLoadFailed = true;
      console.warn('[riskLifecycle] treatment-plan fetch failed:', e);
      _plansHydrated = true;
      emit();
    });
  return _plansPromise;
}

export function areTreatmentPlansHydrated(): boolean { return _plansHydrated; }
export function treatmentPlansLoadFailed(): boolean { return _plansLoadFailed; }

export function loadTreatmentPlans(): TreatmentPlan[] {
  return _plans;
}

export function plansForRisk(riskId: number): TreatmentPlan[] {
  return _plans.filter(p => p.riskId === riskId);
}

/** The legal next states, empty for terminal AND for a status the machine does not declare. */
export function nextTreatmentStatuses(status: string): readonly TreatmentStatus[] {
  return (TREATMENT_ALLOWED[status] ?? []) as readonly TreatmentStatus[];
}

export function isTreatmentStatusManaged(status: string): boolean {
  return Object.prototype.hasOwnProperty.call(TREATMENT_ALLOWED, status);
}

/** Derived from the machine, never typed twice (F2-196). */
export const TREATMENT_TERMINAL: readonly string[] =
  Object.keys(TREATMENT_ALLOWED).filter(k => TREATMENT_ALLOWED[k].length === 0);

/** A label for any status, including one this build has never heard of. */
export function treatmentStatusLabel(status: string, lang: 'en' | 'ar'): string {
  const e = TREATMENT_STATUS_LABEL[status as TreatmentStatus];
  return e ? e[lang] : status;
}

/**
 * Raise a treatment plan. The SERVER mints the reference.
 *
 * F2-204: this used to mint `TP-<year>-<n>` from the length of the local cache — which
 * included seeded plans, so the first real plan on a fresh tenant would have been TP-…-004.
 * Only the server may issue a reference (F2-172).
 */
export async function createTreatmentPlan(
  riskId: number, strategy: TreatmentStrategy,
  rationaleEn: string, rationaleAr: string, costSarK: number,
): Promise<TreatmentPlan> {
  const saved = fromDto(await treatmentPlanApi.create({
    riskId, strategy, rationaleEn, rationaleAr, costSarK,
  }));
  _plans = [saved, ..._plans.filter(p => p.id !== saved.id)];
  emit();
  return saved;
}

/**
 * Decide on a plan. The row comes back FROM THE SERVER.
 *
 * F2-204: no `by` parameter — the approver is the authenticated principal, stamped by
 * `TreatmentPlanService` (F2-22b(vi)). `reason` is the rejecting officer's own words and the
 * caller must not default it. The status is never computed here; the cache is replaced with
 * whatever the server returned, so a transition the machine refuses cannot appear to succeed.
 */
export async function transitionPlan(
  id: string, status: TreatmentStatus, reason?: string,
): Promise<TreatmentPlan> {
  const saved = fromDto(await treatmentPlanApi.transition(id, {
    status, rejectedReason: status === 'REJECTED' ? reason : undefined,
  }));
  _plans = _plans.map(p => p.id === id ? saved : p);
  emit();
  return saved;
}

// ─── Persistence: KRI breach events ─────────────────────────────────────────

export function loadBreachEvents(): KriBreachEvent[] {
  if (typeof window === 'undefined') return SEED_BREACHES;
  try {
    const raw = window.localStorage.getItem(KRI_BREACH_KEY);
    if (!raw) return SEED_BREACHES;
    return JSON.parse(raw) as KriBreachEvent[];
  } catch { return SEED_BREACHES; }
}

export function breachesForRisk(riskId: number): KriBreachEvent[] {
  return loadBreachEvents().filter(b => b.riskId === riskId);
}

export function fireBreach(input: Omit<KriBreachEvent, 'id' | 'detectedAt' | 'status' | 'escalationLog'>): KriBreachEvent {
  const event: KriBreachEvent = {
    ...input,
    id: `KRI-BR-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    detectedAt: new Date().toISOString(),
    status: 'OPEN',
    escalationLog: [{
      stepEn: 'Detection — auto-routed to KRI owner',
      stepAr: 'الاكتشاف — توجيه تلقائي إلى مالك مؤشر الأداء',
      toOwner: input.currentOwner,
      atTs: new Date().toISOString(),
    }],
  };
  const next = [...loadBreachEvents(), event];
  try { window.localStorage.setItem(KRI_BREACH_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return event;
}

export function escalateBreach(id: string, toOwner: string, stepEn: string, stepAr: string): KriBreachEvent | null {
  const breach = loadBreachEvents().find(b => b.id === id);
  if (!breach) return null;
  const updated: KriBreachEvent = {
    ...breach,
    currentOwner: toOwner,
    status: 'ESCALATED',
    escalationLog: [
      ...breach.escalationLog,
      { stepEn, stepAr, toOwner, atTs: new Date().toISOString() },
    ],
  };
  const next = loadBreachEvents().map(b => b.id === id ? updated : b);
  try { window.localStorage.setItem(KRI_BREACH_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return updated;
}

export function resolveBreach(id: string, resolutionEn: string, resolutionAr: string): KriBreachEvent | null {
  const breach = loadBreachEvents().find(b => b.id === id);
  if (!breach) return null;
  const updated: KriBreachEvent = {
    ...breach, status: 'RESOLVED',
    resolutionEn, resolutionAr,
    escalationLog: [
      ...breach.escalationLog,
      { stepEn: `Resolved: ${resolutionEn}`, stepAr: `حُلَّت: ${resolutionAr}`, toOwner: breach.currentOwner, atTs: new Date().toISOString() },
    ],
  };
  const next = loadBreachEvents().map(b => b.id === id ? updated : b);
  try { window.localStorage.setItem(KRI_BREACH_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return updated;
}

// ─── Persistence: acceptance requests ───────────────────────────────────────

export function loadAcceptances(): AcceptanceRequest[] {
  if (typeof window === 'undefined') return SEED_ACCEPTANCES;
  try {
    const raw = window.localStorage.getItem(ACCEPT_KEY);
    if (!raw) return SEED_ACCEPTANCES;
    return JSON.parse(raw) as AcceptanceRequest[];
  } catch { return SEED_ACCEPTANCES; }
}

export function acceptancesForRisk(riskId: number): AcceptanceRequest[] {
  return loadAcceptances().filter(a => a.riskId === riskId);
}

export function saveAcceptance(req: AcceptanceRequest): AcceptanceRequest {
  const all = loadAcceptances().filter(a => a.id !== req.id);
  const next = [...all, req];
  try { window.localStorage.setItem(ACCEPT_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return req;
}

export function transitionAcceptance(id: string, status: AcceptanceStatus, signedBy: string): AcceptanceRequest | null {
  const req = loadAcceptances().find(a => a.id === id);
  if (!req) return null;
  const now = new Date().toISOString();
  let updated: AcceptanceRequest = { ...req, status };
  if (status === 'CRO_REVIEW') updated = { ...updated, croSignedBy: signedBy, croSignedAt: now };
  if (status === 'APPROVED') updated = { ...updated, boardSignedBy: signedBy, boardSignedAt: now };
  return saveAcceptance(updated);
}

export function subscribeRiskLifecycle(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  const storage = (e: StorageEvent) => {
    // F2-204: TREATMENT_KEY is gone — treatment plans are server-backed, so a cross-tab
    // localStorage event can no longer signal a change to them. The two remaining keys are the
    // NO_SERVER_SURFACE stores (KRI breaches, risk acceptances), declared in lib/lifecycleStores.
    if (e.key === KRI_BREACH_KEY || e.key === ACCEPT_KEY) cb();
  };
  window.addEventListener('storage', storage);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storage);
  };
}

function emit() {
  try { window.dispatchEvent(new CustomEvent(EVENT)); } catch { /* ignore */ }
}

// ─── Quantitative scoring ─────────────────────────────────────────────────

/** Triangular distribution sampler. */
function sampleTriangular(min: number, mode: number, max: number): number {
  const u = Math.random();
  const c = (mode - min) / (max - min);
  return u < c
    ? min + Math.sqrt(u * (max - min) * (mode - min))
    : max - Math.sqrt((1 - u) * (max - min) * (max - mode));
}

/** Run a Monte Carlo simulation over likelihood × impact. */
export function runMonteCarlo(input: MonteCarloInput): MonteCarloResult {
  const losses: number[] = [];
  for (let i = 0; i < input.trials; i++) {
    const p = sampleTriangular(input.pMin, input.pMode, input.pMax);
    const i_ = sampleTriangular(input.iMin, input.iMode, input.iMax);
    // Bernoulli realisation: with probability p, full impact occurs.
    losses.push(Math.random() < p ? i_ : 0);
  }
  losses.sort((a, b) => a - b);
  const sum = losses.reduce((a, b) => a + b, 0);
  const mean = sum / losses.length;
  const variance = losses.reduce((acc, x) => acc + (x - mean) ** 2, 0) / losses.length;
  const stdev = Math.sqrt(variance);
  const p95 = losses[Math.floor(losses.length * 0.95)];
  const p99 = losses[Math.floor(losses.length * 0.99)];

  // Histogram (12 buckets).
  const max = Math.max(...losses, 1);
  const bucketSize = max / 12;
  const histogram = Array.from({ length: 12 }, (_, b) => ({
    rangeStart: b * bucketSize,
    rangeEnd: (b + 1) * bucketSize,
    count: 0,
  }));
  losses.forEach(l => {
    const idx = Math.min(11, Math.floor(l / bucketSize));
    histogram[idx].count++;
  });

  return {
    expectedLoss: Math.round(mean),
    p95Loss: Math.round(p95),
    p99Loss: Math.round(p99),
    stdev: Math.round(stdev),
    histogram,
  };
}

// ─── Seed data ─────────────────────────────────────────────────────────────

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString();

// F2-204: `SEED_TREATMENT_PLANS` is deleted. The register holds 10 real rows in dev and
// prod; a tenant with none now sees none, and an unreachable server is reported as a
// failure rather than replaced with invented plans (the F2-158 fallback shape).

const SEED_BREACHES: KriBreachEvent[] = [
  {
    id: 'KRI-BR-2026-001',
    riskId: MOCK_RISKS[0]?.id ?? 1,
    kriCode: 'KRI-CYBER-001',
    kriNameEn: 'Phishing click rate (last 30d)',
    kriNameAr: 'معدّل النقر على رسائل التصيّد (آخر 30 يوم)',
    thresholdValue: 5,
    actualValue: 7.2,
    direction: 'UP_BAD',
    detectedAt: _daysAgo(2),
    status: 'INVESTIGATING',
    currentOwner: 'CISO',
    escalationLog: [
      { stepEn: 'Detection — auto-routed to SOC team',           stepAr: 'الاكتشاف — توجيه تلقائي لفريق SOC',                 toOwner: 'SOC team',   atTs: _daysAgo(2) },
      { stepEn: 'Escalated to CISO (threshold +44% over target)', stepAr: 'تصعيد إلى رئيس أمن المعلومات (تجاوز 44٪)',         toOwner: 'CISO',       atTs: _daysAgo(1) },
    ],
  },
];

const SEED_ACCEPTANCES: AcceptanceRequest[] = [];

// ─── Display helpers ────────────────────────────────────────────────────────

export const TREATMENT_STATUS_LABEL: Record<TreatmentStatus, { en: string; ar: string }> = {
  DRAFT:        { en: 'Draft',         ar: 'مسوّدة' },
  SUBMITTED:    { en: 'Submitted',     ar: 'مُقدَّمة' },
  APPROVED:     { en: 'Approved',      ar: 'مُعتمَدة' },
  IN_PROGRESS:  { en: 'In progress',   ar: 'قيد التنفيذ' },
  COMPLETED:    { en: 'Completed',     ar: 'مُنجَزة' },
  REJECTED:     { en: 'Rejected',      ar: 'مرفوضة' },
};

export const TREATMENT_STATUS_TONE: Record<TreatmentStatus, 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'primary'> = {
  DRAFT: 'neutral', SUBMITTED: 'warning', APPROVED: 'info',
  IN_PROGRESS: 'primary', COMPLETED: 'success', REJECTED: 'danger',
};

export const STRATEGY_LABEL: Record<TreatmentStrategy, { en: string; ar: string }> = {
  MITIGATE: { en: 'Mitigate', ar: 'تخفيف' },
  TRANSFER: { en: 'Transfer', ar: 'تحويل' },
  AVOID:    { en: 'Avoid',    ar: 'تجنّب' },
  ACCEPT:   { en: 'Accept',   ar: 'قبول' },
};

export const ACCEPTANCE_STATUS_LABEL: Record<AcceptanceStatus, { en: string; ar: string }> = {
  DRAFT:           { en: 'Draft',                  ar: 'مسوّدة' },
  CRO_REVIEW:      { en: 'CRO review',              ar: 'مراجعة رئيس المخاطر' },
  BOARD_APPROVAL:  { en: 'Board approval',          ar: 'اعتماد مجلس الإدارة' },
  APPROVED:        { en: 'Approved',                ar: 'مُعتمَد' },
  EXPIRED:         { en: 'Expired',                  ar: 'منتهي' },
  WITHDRAWN:       { en: 'Withdrawn',               ar: 'مسحوب' },
};

export const ACCEPTANCE_STATUS_TONE: Record<AcceptanceStatus, 'neutral' | 'info' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'neutral', CRO_REVIEW: 'info', BOARD_APPROVAL: 'warning',
  APPROVED: 'success', EXPIRED: 'danger', WITHDRAWN: 'neutral',
};

export const BREACH_STATUS_TONE: Record<KriBreachEvent['status'], 'danger' | 'warning' | 'success' | 'info'> = {
  OPEN: 'danger', INVESTIGATING: 'warning', ESCALATED: 'warning', RESOLVED: 'success',
};

export const BREACH_STATUS_LABEL: Record<KriBreachEvent['status'], { en: string; ar: string }> = {
  OPEN:          { en: 'Open',          ar: 'مفتوح' },
  INVESTIGATING: { en: 'Investigating', ar: 'قيد التحقيق' },
  ESCALATED:     { en: 'Escalated',     ar: 'مُصعَّد' },
  RESOLVED:      { en: 'Resolved',      ar: 'مُغلَق' },
};

// ─── Aggregation ────────────────────────────────────────────────────────────

export interface RiskLifecycleStats {
  risksTotal: number;
  risksWithOpenBreach: number;
  risksWithApprovedTreatment: number;
  risksAwaitingAcceptance: number;
  acceptedExpiringSoon: number; // within 60d
  totalTreatmentSpendK: number;
}

export function aggregateRiskLifecycle(): RiskLifecycleStats {
  const breaches = loadBreachEvents();
  const plans = loadTreatmentPlans();
  const accepts = loadAcceptances();
  const risksWithOpenBreach = new Set(breaches.filter(b => b.status === 'OPEN' || b.status === 'INVESTIGATING' || b.status === 'ESCALATED').map(b => b.riskId)).size;
  const risksWithApprovedTreatment = new Set(plans.filter(p => p.status === 'APPROVED' || p.status === 'IN_PROGRESS' || p.status === 'COMPLETED').map(p => p.riskId)).size;
  const risksAwaitingAcceptance = accepts.filter(a => a.status === 'CRO_REVIEW' || a.status === 'BOARD_APPROVAL').length;
  const sixtyDaysFromNow = Date.now() + 60 * 86_400_000;
  const acceptedExpiringSoon = accepts.filter(a =>
    a.status === 'APPROVED' && new Date(a.expiresAt).getTime() < sixtyDaysFromNow
  ).length;
  const totalTreatmentSpendK = plans
    .filter(p => p.status === 'APPROVED' || p.status === 'IN_PROGRESS' || p.status === 'COMPLETED')
    .reduce((acc, p) => acc + p.costSarK, 0);
  return {
    risksTotal: MOCK_RISKS.length,
    risksWithOpenBreach,
    risksWithApprovedTreatment,
    risksAwaitingAcceptance,
    acceptedExpiringSoon,
    totalTreatmentSpendK,
  };
}

export function pickRiskById(id: number): RiskResponse | undefined {
  return MOCK_RISKS.find(r => r.id === id);
}
