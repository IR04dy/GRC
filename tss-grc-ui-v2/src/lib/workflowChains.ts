// Cross-module workflow chaining — Sprint 5.
//
// The orchestration layer the platform was missing: declarative rules
// that watch events from one module and spawn work in another. This is
// what turns "GRC tooling" into "GRC platform" — close one record and
// the downstream re-assessments / re-tests / attestations / approvals
// fire automatically.
//
// Rule shape:
//   trigger  : an event the platform raises (approval decided, workflow
//              closed, breach contained, evidence expired …)
//   condition: optional predicate over the event payload
//   action   : what to spawn (workflow / approval / delayed task)
//
// Actions either fire immediately or schedule for later (e.g. 90-day
// CAPA effectiveness re-test). Scheduled fires use the SLA engine the
// rest of Sprint 4 already built.
//
// Saudi-aligned: every rule carries a regulator citation so an auditor
// asking "why did this control re-test fire?" gets a one-line answer.

import type { ApprovalRequest } from './approvals';

// ─── Event taxonomy ─────────────────────────────────────────────────────────

export type ChainEventType =
  | 'APPROVAL_APPROVED'
  | 'APPROVAL_REJECTED'
  | 'WORKFLOW_CLOSED'
  | 'INCIDENT_CLOSED'
  | 'BREACH_CONTAINED'
  | 'DPIA_APPROVED'
  | 'AUDIT_FINDING_CLOSED'
  | 'APPETITE_EXCEEDED'
  | 'EVIDENCE_EXPIRED'
  | 'POLICY_PUBLISHED'
  | 'VENDOR_ONBOARDED'
  | 'CHANGE_DEPLOYED'
  | 'RISK_ACCEPTED'
  | 'RISK_CLOSED';

export interface ChainEvent {
  type: ChainEventType;
  /** Stable reference of the source record. */
  sourceRef: string;
  module: string;
  /** Event metadata used by rule predicates. */
  payload?: Record<string, string | number | boolean>;
  /** ISO timestamp when the event was raised. */
  raisedAt: string;
}

// ─── Action taxonomy ────────────────────────────────────────────────────────

export type ChainActionType =
  | 'SPAWN_WORKFLOW'   // immediate
  | 'SPAWN_APPROVAL'   // immediate
  | 'SCHEDULE_REVIEW'  // delayed re-assessment / re-test
  | 'SPAWN_FINDING'    // auto-create audit finding
  | 'SPAWN_CAPA';      // auto-create corrective action

export interface ChainAction {
  type: ChainActionType;
  /** Module the action targets. */
  targetModule: 'Risk' | 'Compliance' | 'Privacy' | 'Cyber' | 'TPRM' | 'BCM' | 'Audit' | 'CAPA' | 'Change' | 'Ethics';
  /** Bilingual description shown in the chain catalogue. */
  descriptionEn: string;
  descriptionAr: string;
  /** Default delay in calendar days before action fires (0 = immediate). */
  delayDays: number;
  /** SLA budget for the spawned record in days. */
  spawnedSlaDays: number;
}

// ─── Chain rule ─────────────────────────────────────────────────────────────

export interface ChainRule {
  id: string;
  /** Human-readable name (matches the demo / sales narrative). */
  nameEn: string;
  nameAr: string;
  trigger: ChainEventType;
  /** Optional predicate — narrative form for the catalogue UI. */
  conditionLabelEn?: string;
  conditionLabelAr?: string;
  /** Predicate function. */
  condition?: (event: ChainEvent) => boolean;
  action: ChainAction;
  /** Saudi/international regulator anchor — answers "why does this exist?". */
  regulatorCitation: string;
  /** Whether the rule is currently active. */
  active: boolean;
}

export const CHAIN_RULES: ChainRule[] = [
  {
    id: 'chain-incident-to-risk',
    nameEn: 'Major incident → risk re-assessment',
    nameAr: 'حادث كبير → إعادة تقييم المخاطر',
    trigger: 'INCIDENT_CLOSED',
    conditionLabelEn: 'severity ≥ HIGH',
    conditionLabelAr: 'الشدة ≥ عالية',
    condition: e => ['HIGH', 'CRITICAL'].includes(String(e.payload?.severity ?? '')),
    action: {
      type: 'SPAWN_WORKFLOW', targetModule: 'Risk',
      descriptionEn: 'Spawn a Risk lifecycle re-assessment in the Risk module owned by the Risk Manager.',
      descriptionAr: 'إنشاء إعادة تقييم في وحدة المخاطر يديرها مدير المخاطر.',
      delayDays: 0, spawnedSlaDays: 14,
    },
    regulatorCitation: 'COSO ERM Component 7 — Information & communication',
    active: true,
  },
  {
    id: 'chain-breach-to-attestation',
    nameEn: 'PDPL breach contained → control re-attestation',
    nameAr: 'احتواء اختراق PDPL → إعادة إقرار الضوابط',
    trigger: 'BREACH_CONTAINED',
    action: {
      type: 'SPAWN_WORKFLOW', targetModule: 'Compliance',
      descriptionEn: 'Trigger a control re-attestation campaign on the affected control(s).',
      descriptionAr: 'إطلاق حملة إعادة إقرار للضوابط المتأثرة.',
      delayDays: 7, spawnedSlaDays: 30,
    },
    regulatorCitation: 'PDPL Art. 28 — Lessons learned',
    active: true,
  },
  {
    id: 'chain-dpia-to-control-test',
    nameEn: 'DPIA approved → control effectiveness re-test',
    nameAr: 'اعتماد تقييم الأثر → إعادة اختبار فاعلية الضوابط',
    trigger: 'DPIA_APPROVED',
    action: {
      type: 'SPAWN_WORKFLOW', targetModule: 'Compliance',
      descriptionEn: 'Schedule a 30-day effectiveness re-test for every control referenced in the DPIA.',
      descriptionAr: 'جدولة إعادة اختبار فاعلية بعد 30 يوماً لكل ضابط مرجَع في تقييم الأثر.',
      delayDays: 30, spawnedSlaDays: 14,
    },
    regulatorCitation: 'PDPL Art. 24; ISO 27701 §6.4',
    active: true,
  },
  {
    id: 'chain-finding-to-capa',
    nameEn: 'Audit finding closure approved → 90-day CAPA effectiveness re-test',
    nameAr: 'اعتماد إغلاق الملاحظة → إعادة اختبار فاعلية الإجراء بعد 90 يوماً',
    trigger: 'AUDIT_FINDING_CLOSED',
    action: {
      type: 'SCHEDULE_REVIEW', targetModule: 'CAPA',
      descriptionEn: 'Schedule effectiveness re-test 90 days after closure to confirm the fix held.',
      descriptionAr: 'جدولة إعادة اختبار الفاعلية بعد 90 يوماً من الإغلاق للتأكد من ثبات الحل.',
      delayDays: 90, spawnedSlaDays: 14,
    },
    regulatorCitation: 'ISO 9001 §10.2.2; FDA 21 CFR §820.100',
    active: true,
  },
  {
    id: 'chain-appetite-to-treatment',
    nameEn: 'Appetite EXCEEDED → mandatory risk treatment plan',
    nameAr: 'تجاوز الشهية → خطة معالجة مخاطر إلزامية',
    trigger: 'APPETITE_EXCEEDED',
    action: {
      type: 'SPAWN_APPROVAL', targetModule: 'Risk',
      descriptionEn: 'Force a risk treatment plan into the approval queue for Risk Committee sign-off.',
      descriptionAr: 'إرغام خطة معالجة المخاطر في طابور الموافقات لاعتماد لجنة المخاطر.',
      delayDays: 0, spawnedSlaDays: 7,
    },
    regulatorCitation: 'CMA CGR Art. 75; ISO 31000 §6.5',
    active: true,
  },
  {
    id: 'chain-vendor-to-review',
    nameEn: 'Vendor onboarded → tier-driven periodic review',
    nameAr: 'استقطاب مورد → مراجعة دورية حسب الفئة',
    trigger: 'VENDOR_ONBOARDED',
    conditionLabelEn: 'tier ∈ {1, 2}',
    conditionLabelAr: 'الفئة ∈ {1, 2}',
    condition: e => [1, 2].includes(Number(e.payload?.tier ?? 0)),
    action: {
      type: 'SCHEDULE_REVIEW', targetModule: 'TPRM',
      descriptionEn: 'Schedule next periodic review per tier cadence (T1 = 365d, T2 = 730d).',
      descriptionAr: 'جدولة المراجعة الدورية القادمة حسب الفئة (الفئة 1 = 365 يوماً، الفئة 2 = 730 يوماً).',
      delayDays: 365, spawnedSlaDays: 30,
    },
    regulatorCitation: 'SAMA Outsourcing Framework §6.2',
    active: true,
  },
  {
    id: 'chain-evidence-expired-to-finding',
    nameEn: 'CCM evidence expired → auto-finding',
    nameAr: 'انتهاء دليل CCM → ملاحظة تلقائية',
    trigger: 'EVIDENCE_EXPIRED',
    action: {
      type: 'SPAWN_FINDING', targetModule: 'Compliance',
      descriptionEn: 'Auto-create a finding against the affected control with severity = MEDIUM.',
      descriptionAr: 'إنشاء ملاحظة تلقائياً على الضابط المتأثر بشدّة = متوسطة.',
      delayDays: 0, spawnedSlaDays: 30,
    },
    regulatorCitation: 'NCA ECC-1 control 4-2-2; SOC 2 CC4.1',
    active: true,
  },
  {
    id: 'chain-policy-to-attestation',
    nameEn: 'Policy published → 7-day attestation campaign',
    nameAr: 'نشر السياسة → حملة إقرار خلال 7 أيام',
    trigger: 'POLICY_PUBLISHED',
    action: {
      type: 'SPAWN_WORKFLOW', targetModule: 'Compliance',
      descriptionEn: 'Launch an attestation campaign to all in-scope employees within 7 days of publication.',
      descriptionAr: 'إطلاق حملة إقرار لجميع الموظفين المعنيين خلال 7 أيام من النشر.',
      delayDays: 7, spawnedSlaDays: 30,
    },
    regulatorCitation: 'NCA ECC-1 control 1-3-1; ISO 27001 A.5.1',
    active: true,
  },
  {
    id: 'chain-change-to-pir',
    nameEn: 'Change deployed → 14-day Post-Implementation Review',
    nameAr: 'نشر التغيير → مراجعة ما بعد التنفيذ خلال 14 يوماً',
    trigger: 'CHANGE_DEPLOYED',
    action: {
      type: 'SCHEDULE_REVIEW', targetModule: 'Change',
      descriptionEn: 'Schedule a PIR 14 days post-deploy: success criteria met? incidents arising? lessons learned?',
      descriptionAr: 'جدولة مراجعة بعد التنفيذ بعد 14 يوماً: تحقق المعايير؟ حوادث ناشئة؟ دروس مستفادة؟',
      delayDays: 14, spawnedSlaDays: 7,
    },
    regulatorCitation: 'ITIL v4 Change Enablement; SAMA CSF §3.3',
    active: true,
  },
  {
    id: 'chain-risk-accepted-to-quarterly',
    nameEn: 'Risk accepted → 90-day quarterly review trigger',
    nameAr: 'قبول المخاطر → مراجعة ربع سنوية بعد 90 يوماً',
    trigger: 'RISK_ACCEPTED',
    action: {
      type: 'SCHEDULE_REVIEW', targetModule: 'Risk',
      descriptionEn: 'Quarterly re-affirmation that the accepted risk still falls within the appetite framework.',
      descriptionAr: 'إعادة تأكيد ربع سنوية أن المخاطر المقبولة لا تزال ضمن إطار الشهية.',
      delayDays: 90, spawnedSlaDays: 14,
    },
    regulatorCitation: 'CMA CGR Art. 75 — annual risk-appetite review',
    active: true,
  },
];

// ─── Execution log ──────────────────────────────────────────────────────────
//
// Records every chain fire. Persisted in localStorage so demo mutations
// survive a page refresh; in production this would be the chain audit log
// the regulator can inspect.

export interface ChainFire {
  id: string;
  ruleId: string;
  ruleNameEn: string;
  ruleNameAr: string;
  triggerSourceRef: string;
  triggerModule: string;
  spawnedRef: string;
  spawnedModule: string;
  spawnedTitleEn: string;
  spawnedTitleAr: string;
  spawnedDueDate: string;
  firedAt: string;
}

const FIRES_KEY = 'grc.chains.fires.v1';
const FIRES_EVENT = 'grc.chains.fires.changed';

export function loadFires(): ChainFire[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(FIRES_KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr : [];
  } catch { return []; }
}

function saveFires(fires: ChainFire[]) {
  try { window.localStorage.setItem(FIRES_KEY, JSON.stringify(fires)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(FIRES_EVENT));
}

export function clearFires() {
  saveFires([]);
}

export function subscribeFires(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(FIRES_EVENT, handler);
  // Multi-tab sync.
  const storageHandler = (e: StorageEvent) => { if (e.key === FIRES_KEY) cb(); };
  window.addEventListener('storage', storageHandler);
  return () => {
    window.removeEventListener(FIRES_EVENT, handler);
    window.removeEventListener('storage', storageHandler);
  };
}

// ─── Executor ───────────────────────────────────────────────────────────────

let _seq = 0;

export function executeChain(event: ChainEvent, rules: ReadonlyArray<ChainRule> = CHAIN_RULES): ChainFire[] {
  const matched = rules.filter(r =>
    r.active
    && r.trigger === event.type
    && (!r.condition || r.condition(event)),
  );

  const firedAt = new Date().toISOString();
  const fires: ChainFire[] = matched.map(rule => {
    _seq += 1;
    const dueDate = new Date(Date.now() + rule.action.spawnedSlaDays * 86_400_000 + rule.action.delayDays * 86_400_000);
    const spawnedRef = `${chainPrefix(rule.action.type)}-${event.sourceRef.replace(/[^A-Z0-9-]/gi, '')}-${_seq}`;
    return {
      id: `cf-${Date.now()}-${_seq}`,
      ruleId: rule.id,
      ruleNameEn: rule.nameEn,
      ruleNameAr: rule.nameAr,
      triggerSourceRef: event.sourceRef,
      triggerModule: event.module,
      spawnedRef,
      spawnedModule: rule.action.targetModule,
      spawnedTitleEn: rule.action.descriptionEn,
      spawnedTitleAr: rule.action.descriptionAr,
      spawnedDueDate: dueDate.toISOString().slice(0, 10),
      firedAt,
    };
  });

  if (fires.length > 0) {
    const existing = loadFires();
    const merged = [...fires, ...existing].slice(0, 60); // keep last 60
    saveFires(merged);
  }
  return fires;
}

function chainPrefix(t: ChainActionType): string {
  switch (t) {
    case 'SPAWN_WORKFLOW': return 'CWF';
    case 'SPAWN_APPROVAL': return 'CAP';
    case 'SCHEDULE_REVIEW': return 'CSR';
    case 'SPAWN_FINDING': return 'CFD';
    case 'SPAWN_CAPA': return 'CCP';
  }
}

// ─── Convenience: derive ChainEvent from approval decision ──────────────────

const CATEGORY_TO_EVENT: Partial<Record<ApprovalRequest['category'], ChainEventType>> = {
  POLICY_PUBLICATION:    'POLICY_PUBLISHED',
  RISK_ACCEPTANCE:       'RISK_ACCEPTED',
  POLICY_EXCEPTION:      'WORKFLOW_CLOSED', // generic
  VENDOR_ONBOARDING:     'VENDOR_ONBOARDED',
  DSR_ERASURE:           'WORKFLOW_CLOSED',
  BREACH_NOTIFICATION:   'BREACH_CONTAINED',
  DPIA_SIGN_OFF:         'DPIA_APPROVED',
  CHANGE_REQUEST:        'CHANGE_DEPLOYED',
  AUDIT_FINDING_CLOSURE: 'AUDIT_FINDING_CLOSED',
  CAPITAL_EXPENDITURE:   'WORKFLOW_CLOSED',
  CONTROL_OVERRIDE:      'WORKFLOW_CLOSED',
};

/**
 * When an approval moves to APPROVED, derive a ChainEvent from the request
 * category and fire matching chain rules. Returns the resulting fires so
 * callers can show "spawned X" toasts.
 */
export function fireFromApproval(req: ApprovalRequest): ChainFire[] {
  const eventType = CATEGORY_TO_EVENT[req.category];
  if (!eventType) return [];
  const event: ChainEvent = {
    type: eventType,
    sourceRef: req.sourceRef,
    module: req.module,
    payload: { tier: 1 }, // demo default — production reads from source record
    raisedAt: new Date().toISOString(),
  };
  return executeChain(event);
}

// ─── Statistics ─────────────────────────────────────────────────────────────

export interface ChainStats {
  totalRules: number;
  activeRules: number;
  totalFires24h: number;
  totalFires7d: number;
  byRule: Record<string, number>;
}

export function aggregateStats(rules: ReadonlyArray<ChainRule> = CHAIN_RULES, fires: ReadonlyArray<ChainFire> = loadFires()): ChainStats {
  const now = Date.now();
  const fires24h = fires.filter(f => now - new Date(f.firedAt).getTime() < 24 * 3_600_000).length;
  const fires7d = fires.filter(f => now - new Date(f.firedAt).getTime() < 7 * 86_400_000).length;
  const byRule: Record<string, number> = {};
  for (const f of fires) byRule[f.ruleId] = (byRule[f.ruleId] ?? 0) + 1;
  return {
    totalRules: rules.length,
    activeRules: rules.filter(r => r.active).length,
    totalFires24h: fires24h,
    totalFires7d: fires7d,
    byRule,
  };
}

// ─── Display helpers ────────────────────────────────────────────────────────

export const ACTION_TYPE_LABEL: Record<ChainActionType, { en: string; ar: string }> = {
  SPAWN_WORKFLOW:  { en: 'Spawn workflow',  ar: 'إنشاء سير عمل' },
  SPAWN_APPROVAL:  { en: 'Spawn approval',  ar: 'إنشاء موافقة' },
  SCHEDULE_REVIEW: { en: 'Schedule review', ar: 'جدولة مراجعة' },
  SPAWN_FINDING:   { en: 'Spawn finding',   ar: 'إنشاء ملاحظة' },
  SPAWN_CAPA:      { en: 'Spawn CAPA',      ar: 'إنشاء إجراء تصحيحي' },
};

export const TRIGGER_LABEL: Record<ChainEventType, { en: string; ar: string }> = {
  APPROVAL_APPROVED:    { en: 'Approval approved',     ar: 'تمت الموافقة' },
  APPROVAL_REJECTED:    { en: 'Approval rejected',     ar: 'تمّ الرفض' },
  WORKFLOW_CLOSED:      { en: 'Workflow closed',       ar: 'إغلاق سير عمل' },
  INCIDENT_CLOSED:      { en: 'Incident closed',       ar: 'إغلاق حادثة' },
  BREACH_CONTAINED:     { en: 'Breach contained',      ar: 'احتواء اختراق' },
  DPIA_APPROVED:        { en: 'DPIA approved',         ar: 'اعتماد تقييم الأثر' },
  AUDIT_FINDING_CLOSED: { en: 'Audit finding closed',  ar: 'إغلاق ملاحظة تدقيق' },
  APPETITE_EXCEEDED:    { en: 'Appetite exceeded',     ar: 'تجاوز الشهية' },
  EVIDENCE_EXPIRED:     { en: 'Evidence expired',      ar: 'انتهاء الدليل' },
  POLICY_PUBLISHED:     { en: 'Policy published',      ar: 'نشر السياسة' },
  VENDOR_ONBOARDED:     { en: 'Vendor onboarded',      ar: 'استقطاب مورد' },
  CHANGE_DEPLOYED:      { en: 'Change deployed',       ar: 'نشر تغيير' },
  RISK_ACCEPTED:        { en: 'Risk accepted',         ar: 'قبول مخاطر' },
  RISK_CLOSED:          { en: 'Risk closed',           ar: 'إغلاق مخاطر' },
};
