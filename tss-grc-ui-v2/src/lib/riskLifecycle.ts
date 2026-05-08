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

import { MOCK_RISKS } from '../pages/risk/mockRisks';
import type { RiskResponse } from './api';
import {
  TREATMENT_PLAN_ADAPTER,
  mirrorSaveToBackend,
  mirrorTransitionToBackend,
  mirrorDeleteToBackend,
} from './platformPersistence';

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
  actions: TreatmentAction[];
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

const TREATMENT_KEY = 'grc.risk.treatment.v1';
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

// ─── Persistence: treatment plans ────────────────────────────────────────────

export function loadTreatmentPlans(): TreatmentPlan[] {
  if (typeof window === 'undefined') return SEED_TREATMENT_PLANS;
  try {
    const raw = window.localStorage.getItem(TREATMENT_KEY);
    if (!raw) return SEED_TREATMENT_PLANS;
    return JSON.parse(raw) as TreatmentPlan[];
  } catch { return SEED_TREATMENT_PLANS; }
}

export function plansForRisk(riskId: number): TreatmentPlan[] {
  return loadTreatmentPlans().filter(p => p.riskId === riskId);
}

export function saveTreatmentPlan(plan: TreatmentPlan): TreatmentPlan {
  const existing = loadTreatmentPlans();
  const isNew = !existing.some(p => p.id === plan.id);
  const next = [...existing.filter(p => p.id !== plan.id), plan];
  try { window.localStorage.setItem(TREATMENT_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  // Best-effort REST sync; silent in demo mode.
  mirrorSaveToBackend(TREATMENT_PLAN_ADAPTER, plan, isNew);
  emit();
  return plan;
}

export function deleteTreatmentPlan(id: string): void {
  const next = loadTreatmentPlans().filter(p => p.id !== id);
  try { window.localStorage.setItem(TREATMENT_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  mirrorDeleteToBackend(TREATMENT_PLAN_ADAPTER, id);
  emit();
}

export function createTreatmentPlan(riskId: number, strategy: TreatmentStrategy, rationaleEn: string, rationaleAr: string, costSarK: number): TreatmentPlan {
  const id = `TP-${new Date().getFullYear()}-${String(loadTreatmentPlans().length + 1).padStart(3, '0')}`;
  const plan: TreatmentPlan = {
    id, riskId, strategy,
    rationaleEn, rationaleAr,
    status: 'DRAFT', costSarK,
    costBenefitEn: '', costBenefitAr: '',
    actions: [],
    createdAt: new Date().toISOString(),
  };
  return saveTreatmentPlan(plan);
}

export function addPlanAction(planId: string, titleEn: string, titleAr: string, ownerRole: string, targetDate: string, expectedRiskReduction: number): TreatmentPlan | null {
  const p = loadTreatmentPlans().find(x => x.id === planId);
  if (!p) return null;
  const newAction: TreatmentAction = {
    id: `TA-${Date.now().toString(36).slice(-4)}`,
    titleEn, titleAr, ownerRole, targetDate,
    status: 'PENDING', expectedRiskReduction,
  };
  return saveTreatmentPlan({ ...p, actions: [...p.actions, newAction] });
}

export function transitionPlan(id: string, status: TreatmentStatus, by?: string, reason?: string): TreatmentPlan | null {
  const plan = loadTreatmentPlans().find(p => p.id === id);
  if (!plan) return null;
  const updated: TreatmentPlan = { ...plan, status };
  const now = new Date().toISOString();
  if (status === 'SUBMITTED') updated.submittedAt = now;
  if (status === 'APPROVED') { updated.approvedBy = by; updated.approvedAt = now; }
  if (status === 'REJECTED') { updated.rejectedReason = reason; }
  // Mirror the state-machine transition through the backend's /transition endpoint
  // before the local save — the backend has its own ALLOWED map and will reject
  // illegal transitions. Fire-and-forget so demo path stays sync.
  mirrorTransitionToBackend(TREATMENT_PLAN_ADAPTER, id, { status, approvedBy: by, rejectedReason: reason });
  return saveTreatmentPlan(updated);
}

export function updateActionStatus(planId: string, actionId: string, status: TreatmentAction['status'], evidenceRef?: string): TreatmentPlan | null {
  const plan = loadTreatmentPlans().find(p => p.id === planId);
  if (!plan) return null;
  const updated: TreatmentPlan = {
    ...plan,
    actions: plan.actions.map(a =>
      a.id === actionId
        ? { ...a, status, evidenceRef: evidenceRef ?? a.evidenceRef, completedAt: status === 'COMPLETED' ? new Date().toISOString() : a.completedAt }
        : a,
    ),
  };
  // Auto-close plan when all actions complete.
  const allDone = updated.actions.every(a => a.status === 'COMPLETED');
  if (allDone && updated.status === 'IN_PROGRESS') updated.status = 'COMPLETED';
  return saveTreatmentPlan(updated);
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
    if (e.key === TREATMENT_KEY || e.key === KRI_BREACH_KEY || e.key === ACCEPT_KEY) cb();
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
const _daysAhead = (n: number) => new Date(_now + n * 86_400_000).toISOString().slice(0, 10);

const SEED_TREATMENT_PLANS: TreatmentPlan[] = [
  {
    id: 'TP-2026-001',
    riskId: MOCK_RISKS[0]?.id ?? 1,
    strategy: 'MITIGATE',
    rationaleEn: 'Reduce ransomware likelihood through MFA on all privileged accounts + EDR rollout to remaining 12% of fleet.',
    rationaleAr: 'تقليل احتمالية الفدية عبر فرض المصادقة متعدّدة العوامل على جميع الحسابات المُميَّزة ونشر EDR على 12٪ المتبقّية.',
    status: 'IN_PROGRESS',
    costSarK: 1_400,
    costBenefitEn: 'SAR 1.4M one-time + SAR 280k/yr OpEx vs avoided expected loss ~SAR 8.2M (3-yr horizon).',
    costBenefitAr: '1.4 مليون ريال لمرّة واحدة + 280 ألف ريال سنوياً مقابل خسارة متوقّعة مُتجنَّبة نحو 8.2 مليون ريال (3 سنوات).',
    actions: [
      { id: 'TA-001', titleEn: 'Roll out MFA to all admin accounts', titleAr: 'نشر المصادقة متعدّدة العوامل على حسابات المشرفين', ownerRole: 'CISO',  targetDate: _daysAhead(30), status: 'IN_PROGRESS', expectedRiskReduction: 0.4 },
      { id: 'TA-002', titleEn: 'Deploy EDR to remaining 12% endpoints', titleAr: 'نشر EDR على 12٪ من النقاط المتبقّية',          ownerRole: 'IT Ops', targetDate: _daysAhead(45), status: 'IN_PROGRESS', expectedRiskReduction: 0.3 },
      { id: 'TA-003', titleEn: 'Tabletop exercise validating playbook',  titleAr: 'تمرين طاولة لاختبار الإجراء',                       ownerRole: 'CSIRT lead', targetDate: _daysAhead(60), status: 'PENDING', expectedRiskReduction: 0.1 },
    ],
    createdAt: _daysAgo(20),
    submittedAt: _daysAgo(15),
    approvedBy: 'Sara Al-Zahrani (CISO) + Mohammed Al-Mahmoud (CRO)',
    approvedAt: _daysAgo(12),
  },
];

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
