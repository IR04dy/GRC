// Corrective & Preventive Action (CAPA) — first-class entity.
//
// Until DD-41, "an action that fixes something" was scattered across
// eight different shapes (compliance findings.capa string, cyber
// action plans, risk treatment rows, DPIA mitigations, vendor
// assessment findings text, incident lessons-learned strings, …).
// None of them carried Root-Cause Analysis fields, none distinguished
// CORRECTIVE from PREVENTIVE actions, and none had effectiveness-
// verification gates. That meant the system couldn't answer the
// questions every external auditor asks:
//
//   - "Why did this happen?" (root cause)
//   - "Was the fix verified to work, and how long after?" (90-day check)
//   - "Did the same root-cause recur?" (parent-CAPA chain)
//   - "Did you also act PREVENTIVELY against analogous risks?" (PA)
//
// Standards anchor:
//   ISO 9001:2015 §10.2     — Nonconformity & corrective action
//                              + RCA + effectiveness review
//   ISO 27001:2022 §10.1+10.2 — Continual improvement + nonconformity
//                                with documented evidence of action
//   FDA 21 CFR §820.100      — CAPA procedure incl. verification
//                                of effectiveness, customer
//                                disclosure where applicable
//   IIA IPPF 2500            — Monitoring of management's action
//                              (the audit observation lifecycle)
//   COSO ERM Component 5     — Information & Communication of
//                              corrective actions
//   NCA ECC 1.5              — Continual cybersecurity improvement
//
// ServiceNow IRM equivalent: the `capa` table — every CA/PA carries
// its own row, RCA artefacts, verification log, and recurrence link.

import { useCallback, useEffect, useState } from 'react';

// ── Domain types ────────────────────────────────────────────────────

export type CapaType = 'CORRECTIVE' | 'PREVENTIVE';

/** Where the CAPA originated from. Drives both the icon and the
    "see source" link on the register row. */
export type CapaSource =
  | 'AUDIT_FINDING'      // internal/external audit observation
  | 'RISK_TREATMENT'     // risk register treatment plan row
  | 'DPIA_MITIGATION'    // privacy DPIA mitigation
  | 'INCIDENT_LL'        // incident lessons-learned
  | 'VENDOR_FINDING'     // vendor assessment gap
  | 'CONTROL_GAP'        // control test failed
  | 'KRI_BREACH'         // PA: triggered when a KRI breaches threshold
  | 'RISK_TREND'         // PA: triggered when residual-risk trend up
  | 'PROCESS_REVIEW'     // PA: from periodic process review
  | 'OTHER';

export type CapaSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

/** Lifecycle. The forward path is the IIA / 21 CFR canonical path:
    IDENTIFIED → IMPLEMENTING → OWNER_TESTED → INDEPENDENT_TESTED
    → AUDIT_SIGNED_OFF → CLOSED.
    Side states: VERIFICATION_FAILED (loops back to IMPLEMENTING),
                 CANCELLED. */
export type CapaStatus =
  | 'IDENTIFIED'
  | 'IMPLEMENTING'
  | 'OWNER_TESTED'
  | 'INDEPENDENT_TESTED'
  | 'AUDIT_SIGNED_OFF'
  | 'CLOSED'
  | 'VERIFICATION_FAILED'
  | 'CANCELLED';

export type RcaMethod =
  | 'NONE'
  | 'FIVE_WHYS'      // 5 successive "why?" answers
  | 'FISHBONE'       // 6 categories (People · Process · Technology
                     //               · Materials · Environment · Mgmt)
  | 'PARETO'         // top contributing causes by frequency
  | 'FAULT_TREE';    // hierarchical fault decomposition

export type RootCauseCategory =
  | 'PEOPLE'
  | 'PROCESS'
  | 'TECHNOLOGY'
  | 'POLICY'
  | 'TRAINING'
  | 'EXTERNAL'
  | 'MANAGEMENT'
  | 'UNKNOWN';

export type TestResult = 'PASS' | 'PARTIAL' | 'FAIL' | 'NOT_TESTED';

/** A single timestamped event in a CAPA's history. */
export interface CapaEvent {
  at: string;          // ISO timestamp
  status: CapaStatus;
  actor: string;
  note?: string;
}

export interface Capa {
  id: string;
  code: string;                     // CAPA-2026-001
  titleEn: string;
  titleAr?: string;
  descriptionEn: string;
  descriptionAr?: string;

  type: CapaType;
  source: CapaSource;
  /** Joins to source record — e.g. "FINDING:32", "RISK:101",
      "DPIA:45", "INCIDENT:14", "VENDOR:8", "KRI:1006". */
  sourceRecordKey?: string;
  sourceRecordTitle?: string;
  /** Authority / framework clauses this CAPA addresses (e.g.
      ["NCA ECC AC-2.1.3", "ISO 27001 A.5.7"]). */
  frameworkRefs?: string[];

  severity: CapaSeverity;
  owner: string;
  status: CapaStatus;

  openedAt: string;                 // ISO date
  dueDate: string;                  // target completion
  closedAt?: string;
  cancelledAt?: string;
  cancellationReason?: string;
  progressPct: number;              // 0–100

  /** Owner's own functional test of the implemented control. */
  ownerTestAt?: string;
  ownerTestResult?: TestResult;
  ownerTestNotes?: string;
  /** Independent-tester (peer / 2LoD) verification. */
  independentTestAt?: string;
  independentTestResult?: TestResult;
  independentTestNotes?: string;
  /** Internal-audit (3LoD) sign-off. */
  auditSignoffAt?: string;
  auditSignedOffBy?: string;
  auditSignoffNotes?: string;

  /** Post-closure effectiveness check — typically 90 days after
      AUDIT_SIGNED_OFF. Failure here flips status to
      VERIFICATION_FAILED and re-opens IMPLEMENTING. */
  effectivenessDueDate?: string;
  effectivenessVerifiedAt?: string;
  effectivenessVerifiedBy?: string;
  effectivenessVerified?: boolean;
  effectivenessNotes?: string;

  /** Recurrence linkage — if a CAPA replaces an earlier one whose
      root cause re-surfaced, parentCapaId points at it. The
      register's Pareto chart groups by parent chain. */
  parentCapaId?: string;
  /** Customer / regulator disclosure requirement (FDA 21 CFR §820.100
      and ISO 9001 §10.2.2 mandate this where the nonconformity
      affected an external party). */
  customerDisclosureRequired?: boolean;
  customerDisclosureDate?: string;

  // Root-cause analysis
  rcaMethod: RcaMethod;
  /** For FIVE_WHYS: 5 strings (why-1 .. why-5).
      For FISHBONE:  6 strings (people / process / technology /
                                materials / environment / management).
      For PARETO:    list of contributing causes ordered by frequency.
      For NONE:      empty array. */
  rcaSteps: string[];
  rootCause?: string;
  rootCauseCategory?: RootCauseCategory;

  history: CapaEvent[];
}

// ── Lifecycle helpers ───────────────────────────────────────────────

/** Allowed forward transitions per state. */
export const CAPA_FORWARD_TRANSITIONS: Record<CapaStatus, CapaStatus[]> = {
  IDENTIFIED:           ['IMPLEMENTING', 'CANCELLED'],
  IMPLEMENTING:         ['OWNER_TESTED', 'CANCELLED'],
  OWNER_TESTED:         ['INDEPENDENT_TESTED', 'IMPLEMENTING'],
  INDEPENDENT_TESTED:   ['AUDIT_SIGNED_OFF', 'IMPLEMENTING'],
  AUDIT_SIGNED_OFF:     ['CLOSED', 'VERIFICATION_FAILED'],
  CLOSED:               ['VERIFICATION_FAILED'],
  VERIFICATION_FAILED:  ['IMPLEMENTING'],
  CANCELLED:            [],
};

export const CAPA_STATUS_LABEL: Record<CapaStatus, { en: string; ar: string }> = {
  IDENTIFIED:          { en: 'Identified',          ar: 'محدّدة' },
  IMPLEMENTING:        { en: 'Implementing',        ar: 'قيد التنفيذ' },
  OWNER_TESTED:        { en: 'Owner-tested',        ar: 'اختبار المالك' },
  INDEPENDENT_TESTED:  { en: 'Independent-tested',  ar: 'اختبار مستقل' },
  AUDIT_SIGNED_OFF:    { en: 'Audit signed-off',    ar: 'اعتماد المراجعة' },
  CLOSED:              { en: 'Closed',              ar: 'مغلقة' },
  VERIFICATION_FAILED: { en: 'Verification failed', ar: 'فشل التحقق' },
  CANCELLED:           { en: 'Cancelled',           ar: 'ملغاة' },
};

export const CAPA_STATUS_TONE: Record<CapaStatus, 'neutral' | 'info' | 'primary' | 'accent' | 'success' | 'warning' | 'danger'> = {
  IDENTIFIED:          'neutral',
  IMPLEMENTING:        'info',
  OWNER_TESTED:        'primary',
  INDEPENDENT_TESTED:  'accent',
  AUDIT_SIGNED_OFF:    'primary',
  CLOSED:              'success',
  VERIFICATION_FAILED: 'danger',
  CANCELLED:           'neutral',
};

export const CAPA_SEVERITY_TONE: Record<CapaSeverity, 'success' | 'info' | 'warning' | 'danger'> = {
  LOW: 'success', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger',
};

/** Days until / since `dueDate` (negative = overdue). */
export function daysUntilDue(c: Capa, today: Date = new Date()): number {
  const due = new Date(c.dueDate);
  const ms = due.getTime() - today.getTime();
  return Math.round(ms / 86_400_000);
}

/** Aging bucket for the register — overdue / 0–30 / 31–60 / 61–90 / 90+. */
export function agingBucket(c: Capa, today: Date = new Date()): '0-30' | '31-60' | '61-90' | '90+' | 'OVERDUE' | 'CLOSED' {
  if (c.status === 'CLOSED' || c.status === 'CANCELLED') return 'CLOSED';
  const opened = new Date(c.openedAt);
  const ageDays = Math.round((today.getTime() - opened.getTime()) / 86_400_000);
  if (daysUntilDue(c, today) < 0) return 'OVERDUE';
  if (ageDays <= 30) return '0-30';
  if (ageDays <= 60) return '31-60';
  if (ageDays <= 90) return '61-90';
  return '90+';
}

/** Effectiveness check is overdue if effectivenessDueDate has passed
    and we haven't yet verified. */
export function effectivenessOverdue(c: Capa, today: Date = new Date()): boolean {
  if (!c.effectivenessDueDate) return false;
  if (c.effectivenessVerified) return false;
  return new Date(c.effectivenessDueDate).getTime() < today.getTime();
}

/** Top-N root causes (Pareto). */
export function paretoCauses(items: Capa[], topN = 5): Array<{ category: RootCauseCategory; count: number; pct: number }> {
  const total = items.length || 1;
  const byCat = new Map<RootCauseCategory, number>();
  for (const c of items) {
    const cat = c.rootCauseCategory ?? 'UNKNOWN';
    byCat.set(cat, (byCat.get(cat) ?? 0) + 1);
  }
  return Array.from(byCat.entries())
    .map(([category, count]) => ({ category, count, pct: Math.round((count / total) * 100) }))
    .sort((a, b) => b.count - a.count)
    .slice(0, topN);
}

// ── Seeded demo CAPAs ───────────────────────────────────────────────
// Twenty demo CAPAs spanning all eight source types, with a mix of
// stages (most IMPLEMENTING / OWNER_TESTED, a few CLOSED, one each in
// VERIFICATION_FAILED and AUDIT_SIGNED_OFF awaiting closure). Two are
// PREVENTIVE (PA) — the rest are corrective (CA).

function ev(at: string, status: CapaStatus, actor: string, note?: string): CapaEvent {
  return { at, status, actor, ...(note ? { note } : {}) };
}

export const CAPAS: Capa[] = [
  // ── Compliance audit findings (carry forward + augment) ───────────
  { id: 'capa-001', code: 'CAPA-2026-001',
    titleEn: 'Cross-border data transfer mechanism not formalised',
    titleAr: 'آلية نقل البيانات عبر الحدود غير مُوثَّقة رسمياً',
    descriptionEn: 'PDPL Article 29 requires SCC or BCR before vendor data export. Ad-hoc DPA in place but no SCC.',
    type: 'CORRECTIVE', source: 'AUDIT_FINDING',
    sourceRecordKey: 'FINDING:31', sourceRecordTitle: 'KPMG Q1 PDPL audit — finding 31',
    frameworkRefs: ['PDPL §29', 'NCA ECC 2.7.1'],
    severity: 'HIGH', owner: 'dpo@tss.sa', status: 'IMPLEMENTING',
    openedAt: '2026-03-18', dueDate: '2026-06-30', progressPct: 45,
    rcaMethod: 'FIVE_WHYS',
    rcaSteps: [
      'Why was no SCC executed? — Vendor onboarded before PDPL came into force.',
      'Why was the DPA not refreshed? — Renewal cycle not aligned with regulator updates.',
      'Why is renewal not aligned? — Vendor governance review is annual; PDPL changed mid-cycle.',
      'Why is review only annual? — Process inherited from pre-2023 cyber-only review cadence.',
      'Why was the cadence not updated? — No trigger from regulator-watch process to TPRM team.',
    ],
    rootCause: 'Regulator-watch outputs not routed to TPRM contract-renewal workflow.',
    rootCauseCategory: 'PROCESS',
    history: [
      ev('2026-03-18T09:30:00', 'IDENTIFIED', 'KPMG audit team'),
      ev('2026-04-02T14:15:00', 'IMPLEMENTING', 'dpo@tss.sa', 'SCC negotiation kicked off with vendor.'),
    ] },

  { id: 'capa-002', code: 'CAPA-2026-002',
    titleEn: 'Privileged session recording not extended to DBA bastion',
    titleAr: 'تسجيل الجلسات المُمتازة لا يُغطّي خادم وسيط مدراء قواعد البيانات',
    descriptionEn: 'CyberArk session recording is enforced for sysadmins but DBA bastion is excluded.',
    type: 'CORRECTIVE', source: 'AUDIT_FINDING',
    sourceRecordKey: 'FINDING:32', sourceRecordTitle: 'Internal IT audit — finding 32',
    frameworkRefs: ['NCA ECC AC-2.1.2', 'ISO 27001 A.8.2'],
    severity: 'MEDIUM', owner: 'pam.team@tss.sa', status: 'OWNER_TESTED',
    openedAt: '2026-03-20', dueDate: '2026-05-31', progressPct: 70,
    ownerTestAt: '2026-04-25', ownerTestResult: 'PARTIAL',
    ownerTestNotes: 'Recording enabled but transcript-search not yet indexed.',
    rcaMethod: 'FIVE_WHYS',
    rcaSteps: [
      'Why was DBA bastion excluded? — Originally provisioned outside CyberArk PAM scope.',
      'Why was it provisioned outside scope? — Migrated from legacy bastion in 2024 without PAM tag.',
      'Why was the migration not tagged? — Migration runbook lacked PAM-coverage checklist.',
      'Why was the runbook incomplete? — Runbook author was sysadmin team, not PAM team.',
      'Why was PAM team not consulted? — No RACI for cross-team migration sign-off.',
    ],
    rootCause: 'Migration runbook lacks PAM team as required reviewer.',
    rootCauseCategory: 'PROCESS',
    history: [
      ev('2026-03-20T10:00:00', 'IDENTIFIED', 'internal.audit@tss.sa'),
      ev('2026-03-25T11:30:00', 'IMPLEMENTING', 'pam.team@tss.sa'),
      ev('2026-04-25T16:00:00', 'OWNER_TESTED', 'pam.team@tss.sa', 'Recording on; indexing pending.'),
    ] },

  { id: 'capa-003', code: 'CAPA-2026-003',
    titleEn: 'Cryptographic key rotation policy needs annual cadence',
    titleAr: 'سياسة تدوير مفاتيح التشفير تحتاج جدولة سنوية',
    descriptionEn: 'Production KMS lacks an enforced annual key-rotation policy; ad-hoc rotations only.',
    type: 'CORRECTIVE', source: 'AUDIT_FINDING',
    sourceRecordKey: 'FINDING:34', sourceRecordTitle: 'SAMA CSF Q1 audit — finding 34',
    frameworkRefs: ['SAMA CSF 4.5.3', 'PCI DSS 3.6.4'],
    severity: 'LOW', owner: 'pki.team@tss.sa', status: 'INDEPENDENT_TESTED',
    openedAt: '2026-03-15', dueDate: '2026-05-15', progressPct: 90,
    ownerTestAt: '2026-04-02', ownerTestResult: 'PASS', ownerTestNotes: 'Annual rotation policy live in KMS.',
    independentTestAt: '2026-04-18', independentTestResult: 'PASS',
    independentTestNotes: 'Sample rotation verified across 3 production keys.',
    rcaMethod: 'FIVE_WHYS',
    rcaSteps: [
      'Why no enforced rotation? — Policy stated "as required" without cadence.',
      'Why was cadence unspecified? — Policy authored before KMS automation existed.',
      'Why was it not updated? — Annual policy review focused on access not crypto.',
      'Why was crypto skipped? — No cryptography champion in policy review WG.',
      'Why no champion? — PKI team only added to working group in 2025.',
    ],
    rootCause: 'Policy review WG composition gap — no PKI representation until 2025.',
    rootCauseCategory: 'POLICY',
    history: [
      ev('2026-03-15T09:00:00', 'IDENTIFIED', 'EY audit'),
      ev('2026-03-22T10:00:00', 'IMPLEMENTING', 'pki.team@tss.sa'),
      ev('2026-04-02T14:00:00', 'OWNER_TESTED', 'pki.team@tss.sa'),
      ev('2026-04-18T15:00:00', 'INDEPENDENT_TESTED', 'security.officer@tss.sa'),
    ] },

  { id: 'capa-004', code: 'CAPA-2026-004',
    titleEn: 'BCP exercise scoring methodology inconsistent',
    titleAr: 'منهجية تقييم تمارين استمرارية الأعمال غير مُوحَّدة',
    descriptionEn: 'Each BU scored their own BCP exercise differently; no aggregate maturity rollup possible.',
    type: 'CORRECTIVE', source: 'AUDIT_FINDING',
    sourceRecordKey: 'FINDING:35', sourceRecordTitle: 'BSI ISO 22301 audit',
    frameworkRefs: ['ISO 22301 §8.5'],
    severity: 'LOW', owner: 'bcm.team@tss.sa', status: 'CLOSED',
    openedAt: '2026-02-10', dueDate: '2026-05-01',
    closedAt: '2026-04-04T14:00:00', progressPct: 100,
    ownerTestAt: '2026-03-10', ownerTestResult: 'PASS',
    independentTestAt: '2026-03-22', independentTestResult: 'PASS',
    auditSignoffAt: '2026-04-04', auditSignedOffBy: 'audit.lead@tss.sa',
    auditSignoffNotes: 'Unified rubric adopted across all 8 BUs; verified via Q1 exercise.',
    effectivenessDueDate: '2026-07-04',
    rcaMethod: 'FISHBONE',
    rcaSteps: [
      'People: Each BCC owner used own scoring intuition.',
      'Process: No unified rubric in BCM playbook.',
      'Technology: Spreadsheets diverged across BUs.',
      'Materials: Training materials lacked scoring examples.',
      'Environment: Exercises run on different days, different facilitators.',
      'Management: BCM lead did not standardise after 2024 reorg.',
    ],
    rootCause: 'Absence of unified scoring rubric in BCM playbook post-2024 reorg.',
    rootCauseCategory: 'PROCESS',
    history: [
      ev('2026-02-10T09:00:00', 'IDENTIFIED', 'BSI audit'),
      ev('2026-02-15T10:00:00', 'IMPLEMENTING', 'bcm.team@tss.sa'),
      ev('2026-03-10T14:00:00', 'OWNER_TESTED', 'bcm.team@tss.sa'),
      ev('2026-03-22T15:00:00', 'INDEPENDENT_TESTED', 'risk.officer@tss.sa'),
      ev('2026-04-04T14:00:00', 'AUDIT_SIGNED_OFF', 'audit.lead@tss.sa'),
      ev('2026-04-04T14:30:00', 'CLOSED', 'audit.lead@tss.sa'),
    ] },

  { id: 'capa-005', code: 'CAPA-2026-005',
    titleEn: 'External vendor SOC 2 attestation overdue',
    titleAr: 'تقرير SOC 2 من المورد الخارجي مُتأخّر',
    descriptionEn: 'Cloud-storage vendor has not provided refreshed SOC 2 within 12 months of last attestation.',
    type: 'CORRECTIVE', source: 'VENDOR_FINDING',
    sourceRecordKey: 'VENDOR:8', sourceRecordTitle: 'CloudObjStorage Co — periodic assessment',
    frameworkRefs: ['SOC 2 CC-9.2', 'NCA ECC 4.1'],
    severity: 'HIGH', owner: 'tprm.lead@tss.sa', status: 'IDENTIFIED',
    openedAt: '2026-04-22', dueDate: '2026-05-30', progressPct: 10,
    rcaMethod: 'NONE',
    rcaSteps: [],
    history: [
      ev('2026-04-22T09:00:00', 'IDENTIFIED', 'tprm.lead@tss.sa'),
    ] },

  // ── Risk treatment originated CAPAs ────────────────────────────
  { id: 'capa-006', code: 'CAPA-2026-006',
    titleEn: 'MFA coverage gap on legacy ERP gateway',
    titleAr: 'فجوة تغطية المصادقة الثنائية على بوابة نظام تخطيط الموارد القديم',
    descriptionEn: 'ERP-Legacy gateway accepts password-only auth for ~12% of users.',
    type: 'CORRECTIVE', source: 'RISK_TREATMENT',
    sourceRecordKey: 'RISK:101', sourceRecordTitle: 'Inadequate authentication on legacy ERP',
    frameworkRefs: ['NCA ECC 2.4.1', 'ISO 27001 A.5.17'],
    severity: 'CRITICAL', owner: 'iam.team@tss.sa', status: 'IMPLEMENTING',
    openedAt: '2026-02-28', dueDate: '2026-06-15', progressPct: 55,
    rcaMethod: 'FIVE_WHYS',
    rcaSteps: [
      'Why are users on password-only? — Legacy ERP SSO connector predates MFA.',
      'Why was the connector not upgraded? — Vendor support contract excludes auth modules.',
      'Why was it excluded? — Original procurement scoped only break-fix.',
      'Why was scope so narrow? — Cost optimisation drive in 2022.',
      'Why was security trade-off accepted? — Risk-acceptance signed 2022 with no expiry.',
    ],
    rootCause: 'Open-ended risk acceptance from 2022 procurement decision.',
    rootCauseCategory: 'POLICY',
    history: [
      ev('2026-02-28T09:00:00', 'IDENTIFIED', 'risk.officer@tss.sa'),
      ev('2026-03-05T10:00:00', 'IMPLEMENTING', 'iam.team@tss.sa', 'IDP federation rollout phase 2 in progress.'),
    ] },

  { id: 'capa-007', code: 'CAPA-2026-007',
    titleEn: 'Concentration risk on single payment processor',
    titleAr: 'تركز المخاطر على معالج مدفوعات وحيد',
    descriptionEn: 'Single-vendor exposure for payment processing exceeds 70% of transaction volume.',
    type: 'CORRECTIVE', source: 'RISK_TREATMENT',
    sourceRecordKey: 'RISK:103', sourceRecordTitle: 'Vendor concentration risk',
    frameworkRefs: ['SAMA Outsourcing Rules 4.3'],
    severity: 'HIGH', owner: 'tprm.lead@tss.sa', status: 'IMPLEMENTING',
    openedAt: '2026-03-01', dueDate: '2026-09-30', progressPct: 25,
    rcaMethod: 'FIVE_WHYS',
    rcaSteps: [
      'Why concentration? — Secondary processor decommissioned in 2024.',
      'Why decommissioned? — Cost reduction; secondary handled <5% of volume.',
      'Why was tail-risk not modelled? — Concentration KRI added to dashboard only in 2025.',
      'Why so late? — KRI library expansion paused during 2023 budget freeze.',
      'Why was the freeze accepted? — Risk function had no seat at finance prioritisation.',
    ],
    rootCause: 'Risk function under-represented in 2023 budget prioritisation.',
    rootCauseCategory: 'MANAGEMENT',
    history: [
      ev('2026-03-01T09:00:00', 'IDENTIFIED', 'risk.officer@tss.sa'),
      ev('2026-03-08T10:00:00', 'IMPLEMENTING', 'tprm.lead@tss.sa', 'Secondary processor RFP launched.'),
    ] },

  // ── DPIA mitigation originated CAPAs ───────────────────────────
  { id: 'capa-008', code: 'CAPA-2026-008',
    titleEn: 'Bias monitoring KPIs for AI credit scoring',
    titleAr: 'مؤشرات مراقبة تحيّز نظام تقييم الائتمان بالذكاء الاصطناعي',
    descriptionEn: 'Continuous bias-monitoring metrics required as DPIA condition for AI credit-scoring rollout.',
    type: 'CORRECTIVE', source: 'DPIA_MITIGATION',
    sourceRecordKey: 'DPIA:41', sourceRecordTitle: 'AI-based credit scoring',
    frameworkRefs: ['PDPL §16', 'SDAIA AI Ethics Principles'],
    severity: 'HIGH', owner: 'ai.governance@tss.sa', status: 'OWNER_TESTED',
    openedAt: '2026-02-14', dueDate: '2026-05-30', progressPct: 75,
    ownerTestAt: '2026-04-20', ownerTestResult: 'PASS',
    ownerTestNotes: 'Demographic-parity dashboard live; weekly review scheduled.',
    rcaMethod: 'NONE',
    rcaSteps: [],
    rootCause: 'New control — DPIA condition prior to launch.',
    rootCauseCategory: 'PROCESS',
    history: [
      ev('2026-02-14T09:00:00', 'IDENTIFIED', 'dpo@tss.sa'),
      ev('2026-02-20T10:00:00', 'IMPLEMENTING', 'ai.governance@tss.sa'),
      ev('2026-04-20T15:00:00', 'OWNER_TESTED', 'ai.governance@tss.sa'),
    ] },

  { id: 'capa-009', code: 'CAPA-2026-009',
    titleEn: 'Cross-border payroll vendor SCC + encryption',
    titleAr: 'بنود تعاقدية معيارية + تشفير لمورد الرواتب عبر الحدود',
    descriptionEn: 'Cross-border payroll vendor requires SDAIA SCC + end-to-end encryption certification.',
    type: 'CORRECTIVE', source: 'DPIA_MITIGATION',
    sourceRecordKey: 'DPIA:42', sourceRecordTitle: 'Cross-border payroll vendor',
    frameworkRefs: ['PDPL §29', 'PDPL §30'],
    severity: 'HIGH', owner: 'dpo@tss.sa', status: 'IMPLEMENTING',
    openedAt: '2026-04-12', dueDate: '2026-07-31', progressPct: 35,
    rcaMethod: 'NONE',
    rcaSteps: [],
    history: [
      ev('2026-04-12T09:00:00', 'IDENTIFIED', 'dpo@tss.sa'),
      ev('2026-04-18T10:00:00', 'IMPLEMENTING', 'dpo@tss.sa', 'SCC drafted; awaiting vendor counter-signature.'),
    ] },

  // ── Incident lessons-learned ─────────────────────────────────
  { id: 'capa-010', code: 'CAPA-2026-010',
    titleEn: 'VPN brute-force rate-limiting tightening',
    titleAr: 'تشديد حدود مُعدَّل المحاولات على خدمة VPN',
    descriptionEn: 'Post-incident: VPN gateway accepted 200+ login attempts/min before lockout. Tighten to 5/min/IP.',
    type: 'CORRECTIVE', source: 'INCIDENT_LL',
    sourceRecordKey: 'INCIDENT:14', sourceRecordTitle: 'VPN credential-stuffing campaign — March 2026',
    frameworkRefs: ['NCA ECC 2.4.3', 'ISO 27001 A.8.5'],
    severity: 'HIGH', owner: 'network.security@tss.sa', status: 'CLOSED',
    openedAt: '2026-03-22', dueDate: '2026-04-30',
    closedAt: '2026-04-15T14:00:00', progressPct: 100,
    ownerTestAt: '2026-04-05', ownerTestResult: 'PASS',
    independentTestAt: '2026-04-12', independentTestResult: 'PASS',
    auditSignoffAt: '2026-04-15', auditSignedOffBy: 'csirt.lead@tss.sa',
    effectivenessDueDate: '2026-07-15',
    rcaMethod: 'FIVE_WHYS',
    rcaSteps: [
      'Why was rate-limit absent? — Default vendor template applied during 2023 rollout.',
      'Why was template not hardened? — No security baseline review in network-engineering checklist.',
      'Why no review step? — Network team operated outside ISMS boundary until 2024.',
      'Why outside ISMS? — Org chart placed network under infrastructure not security.',
      'Why has this not been corrected? — Org realignment in flight; rollout staggered.',
    ],
    rootCause: 'Organisational gap — network engineering only recently inside ISMS scope.',
    rootCauseCategory: 'MANAGEMENT',
    history: [
      ev('2026-03-22T09:00:00', 'IDENTIFIED', 'csirt.lead@tss.sa'),
      ev('2026-03-23T10:00:00', 'IMPLEMENTING', 'network.security@tss.sa'),
      ev('2026-04-05T14:00:00', 'OWNER_TESTED', 'network.security@tss.sa'),
      ev('2026-04-12T15:00:00', 'INDEPENDENT_TESTED', 'soc.lead@tss.sa'),
      ev('2026-04-15T14:00:00', 'AUDIT_SIGNED_OFF', 'csirt.lead@tss.sa'),
      ev('2026-04-15T14:30:00', 'CLOSED', 'csirt.lead@tss.sa'),
    ] },

  { id: 'capa-011', code: 'CAPA-2026-011',
    titleEn: 'MFA enforcement on all VPN profiles',
    titleAr: 'فرض المصادقة الثنائية على جميع ملفات VPN',
    descriptionEn: 'Same incident: 2 of 7 VPN profile groups lacked MFA. Enforce on all profiles.',
    type: 'CORRECTIVE', source: 'INCIDENT_LL',
    sourceRecordKey: 'INCIDENT:14', sourceRecordTitle: 'VPN credential-stuffing campaign — March 2026',
    frameworkRefs: ['NCA ECC 2.4.1', 'PCI DSS 8.4'],
    severity: 'CRITICAL', owner: 'iam.team@tss.sa', status: 'AUDIT_SIGNED_OFF',
    openedAt: '2026-03-22', dueDate: '2026-04-30', progressPct: 95,
    ownerTestAt: '2026-04-04', ownerTestResult: 'PASS',
    independentTestAt: '2026-04-12', independentTestResult: 'PASS',
    auditSignoffAt: '2026-04-20', auditSignedOffBy: 'csirt.lead@tss.sa',
    effectivenessDueDate: '2026-07-20',
    rcaMethod: 'FIVE_WHYS',
    rcaSteps: [
      'Why was MFA missing on 2 groups? — Group policies were copies of legacy contractor profile.',
      'Why was contractor profile exempt? — Mid-2024 hot-fix to unblock vendor maintenance.',
      'Why was hot-fix not reverted? — No expiry attached to the exception.',
      'Why no expiry? — Exception process at the time did not require time-bounding.',
      'Why was the process weak? — Pre-2025 risk-acceptance template lacked sunset field.',
    ],
    rootCause: 'Pre-2025 risk-acceptance template lacked mandatory sunset clause.',
    rootCauseCategory: 'POLICY',
    history: [
      ev('2026-03-22T09:00:00', 'IDENTIFIED', 'csirt.lead@tss.sa'),
      ev('2026-03-23T11:00:00', 'IMPLEMENTING', 'iam.team@tss.sa'),
      ev('2026-04-04T14:00:00', 'OWNER_TESTED', 'iam.team@tss.sa'),
      ev('2026-04-12T15:00:00', 'INDEPENDENT_TESTED', 'soc.lead@tss.sa'),
      ev('2026-04-20T16:00:00', 'AUDIT_SIGNED_OFF', 'csirt.lead@tss.sa'),
    ] },

  // ── Control gap (failed test) ─────────────────────────────────
  { id: 'capa-012', code: 'CAPA-2026-012',
    titleEn: 'Quarterly access recertification — Tier-0 systems',
    titleAr: 'إعادة اعتماد الوصول ربع السنوية — أنظمة المستوى صفر',
    descriptionEn: 'Q1 access recert completed for 78% of Tier-0 systems vs 100% target — 22% gap.',
    type: 'CORRECTIVE', source: 'CONTROL_GAP',
    sourceRecordKey: 'CONTROL:303', sourceRecordTitle: 'AC-2.1.3 Quarterly access recertification',
    frameworkRefs: ['NCA ECC AC-2.1.3', 'ISO 27001 A.5.18'],
    severity: 'HIGH', owner: 'iam.team@tss.sa', status: 'OWNER_TESTED',
    openedAt: '2026-04-08', dueDate: '2026-06-30', progressPct: 60,
    ownerTestAt: '2026-04-25', ownerTestResult: 'PARTIAL',
    ownerTestNotes: 'Recert workflow now automated for 92% of Tier-0; 8% manual.',
    rcaMethod: 'FIVE_WHYS',
    rcaSteps: [
      'Why was recert at 78%? — 7 systems lack joiner/mover/leaver hooks into IGA.',
      'Why no hooks? — Built before IGA platform; not migrated.',
      'Why not migrated? — Each migration costs 4-6 weeks of integration work.',
      'Why is integration so heavy? — Bespoke RBAC models per system.',
      'Why bespoke? — Historical reasons; no enterprise RBAC standard until 2024.',
    ],
    rootCause: 'Legacy systems lack IGA integration; bespoke RBAC models block automation.',
    rootCauseCategory: 'TECHNOLOGY',
    history: [
      ev('2026-04-08T09:00:00', 'IDENTIFIED', 'compliance.lead@tss.sa'),
      ev('2026-04-10T10:00:00', 'IMPLEMENTING', 'iam.team@tss.sa'),
      ev('2026-04-25T16:00:00', 'OWNER_TESTED', 'iam.team@tss.sa'),
    ] },

  { id: 'capa-013', code: 'CAPA-2026-013',
    titleEn: 'SIEM alert tuning to <5% FP rate',
    titleAr: 'ضبط تنبيهات نظام الرصد لتقليل المعلومات المضللة دون 5%',
    descriptionEn: 'SIEM false-positive rate measured 11.4% in Q1; target is <5%. Tuning sprint required.',
    type: 'CORRECTIVE', source: 'CONTROL_GAP',
    sourceRecordKey: 'CONTROL:902', sourceRecordTitle: 'A.8.16.2 Alert tuning',
    frameworkRefs: ['ISO 27001 A.8.16'],
    severity: 'MEDIUM', owner: 'soc.lead@tss.sa', status: 'OWNER_TESTED',
    openedAt: '2026-04-04', dueDate: '2026-06-30', progressPct: 65,
    ownerTestAt: '2026-04-12', ownerTestResult: 'PARTIAL',
    ownerTestNotes: 'FP rate down to 7.8%; further tuning of cloud-IAM ruleset required.',
    rcaMethod: 'PARETO',
    rcaSteps: [
      'Cloud-IAM ruleset: 38% of FPs',
      'EDR behaviour-detection: 24% of FPs',
      'Email gateway: 17% of FPs',
      'Network-IDS legacy signatures: 14% of FPs',
      'Other: 7%',
    ],
    rootCause: 'Cloud-IAM ruleset baseline mis-calibrated post-2025 cloud expansion.',
    rootCauseCategory: 'TECHNOLOGY',
    history: [
      ev('2026-04-04T09:00:00', 'IDENTIFIED', 'soc.lead@tss.sa'),
      ev('2026-04-06T10:00:00', 'IMPLEMENTING', 'soc.lead@tss.sa'),
      ev('2026-04-12T15:00:00', 'OWNER_TESTED', 'soc.lead@tss.sa'),
    ] },

  // ── Verification-failed (recurrence) — child of capa-004 ───────
  { id: 'capa-014', code: 'CAPA-2026-014',
    titleEn: 'BCP rubric — Q2 effectiveness re-test failure',
    titleAr: 'إعادة اختبار فاعلية رُبريك استمرارية الأعمال — رُبع 2 فشل',
    descriptionEn: '90-day post-closure check on capa-004: 2 of 8 BUs reverted to old scoring methodology.',
    type: 'CORRECTIVE', source: 'AUDIT_FINDING',
    sourceRecordKey: 'CAPA:capa-004', sourceRecordTitle: 'BCP rubric (parent CAPA)',
    parentCapaId: 'capa-004',
    frameworkRefs: ['ISO 22301 §8.5', 'IIA IPPF 2500'],
    severity: 'MEDIUM', owner: 'bcm.team@tss.sa', status: 'IMPLEMENTING',
    openedAt: '2026-04-25', dueDate: '2026-06-30', progressPct: 20,
    rcaMethod: 'FIVE_WHYS',
    rcaSteps: [
      'Why did 2 BUs revert? — New BCC owners in those BUs unaware of unified rubric.',
      'Why unaware? — Onboarding kit not updated to include rubric.',
      'Why not updated? — Kit is owned by HR not BCM.',
      'Why HR-owned? — Cross-functional onboarding content sits with HR by default.',
      'Why no BCM hand-off? — No mandatory hand-off for role-specific tools.',
    ],
    rootCause: 'Onboarding content ownership gap between HR and functional teams.',
    rootCauseCategory: 'PROCESS',
    history: [
      ev('2026-04-25T09:00:00', 'IDENTIFIED', 'audit.lead@tss.sa', 'Effectiveness check failed.'),
      ev('2026-04-26T10:00:00', 'IMPLEMENTING', 'bcm.team@tss.sa'),
    ] },

  // ── Customer disclosure case (FDA/ISO 9001 §10.2.2) ────────────
  { id: 'capa-015', code: 'CAPA-2026-015',
    titleEn: 'Customer-data export accidental over-share',
    titleAr: 'مشاركة عرضية مُفرِطة في تصدير بيانات العملاء',
    descriptionEn: 'Bulk-export endpoint returned 5% extra rows beyond intended scope for 2 weeks.',
    type: 'CORRECTIVE', source: 'INCIDENT_LL',
    sourceRecordKey: 'INCIDENT:18', sourceRecordTitle: 'Customer export over-share — April 2026',
    frameworkRefs: ['PDPL §27 (breach notice)', 'ISO 9001 §10.2.2'],
    severity: 'CRITICAL', owner: 'platform.eng@tss.sa', status: 'AUDIT_SIGNED_OFF',
    openedAt: '2026-04-12', dueDate: '2026-05-15', progressPct: 90,
    ownerTestAt: '2026-04-22', ownerTestResult: 'PASS',
    independentTestAt: '2026-04-28', independentTestResult: 'PASS',
    auditSignoffAt: '2026-05-01', auditSignedOffBy: 'csirt.lead@tss.sa',
    customerDisclosureRequired: true,
    customerDisclosureDate: '2026-04-15',
    rcaMethod: 'FIVE_WHYS',
    rcaSteps: [
      'Why over-share? — Default LIMIT clause omitted in API change.',
      'Why omitted? — Code review checklist did not flag pagination changes.',
      'Why not flagged? — Checklist was control-coverage based, not pattern based.',
      'Why pattern-blind? — Threat-modelling for data egress had not been added.',
      'Why missing? — Threat-model coverage focused on inbound, not outbound.',
    ],
    rootCause: 'Threat-model coverage gap on data-egress patterns.',
    rootCauseCategory: 'PROCESS',
    history: [
      ev('2026-04-12T09:00:00', 'IDENTIFIED', 'csirt.lead@tss.sa'),
      ev('2026-04-13T10:00:00', 'IMPLEMENTING', 'platform.eng@tss.sa'),
      ev('2026-04-22T15:00:00', 'OWNER_TESTED', 'platform.eng@tss.sa'),
      ev('2026-04-28T16:00:00', 'INDEPENDENT_TESTED', 'security.officer@tss.sa'),
      ev('2026-05-01T09:00:00', 'AUDIT_SIGNED_OFF', 'csirt.lead@tss.sa'),
    ] },

  // ── Vendor finding ─────────────────────────────────────────────
  { id: 'capa-016', code: 'CAPA-2026-016',
    titleEn: 'Vendor SLA breach — incident notification delays',
    titleAr: 'إخلال المورد باتفاقية مستوى الخدمة — تأخير إشعارات الحوادث',
    descriptionEn: 'TPRM finding: vendor incident-notice average is 9h vs contractual 4h.',
    type: 'CORRECTIVE', source: 'VENDOR_FINDING',
    sourceRecordKey: 'VENDOR:5', sourceRecordTitle: 'CloudPlatformProvider — Q1 review',
    frameworkRefs: ['SAMA Outsourcing 5.1.4'],
    severity: 'MEDIUM', owner: 'tprm.lead@tss.sa', status: 'IMPLEMENTING',
    openedAt: '2026-04-02', dueDate: '2026-06-30', progressPct: 30,
    rcaMethod: 'NONE',
    rcaSteps: [],
    history: [
      ev('2026-04-02T09:00:00', 'IDENTIFIED', 'tprm.lead@tss.sa'),
      ev('2026-04-08T10:00:00', 'IMPLEMENTING', 'tprm.lead@tss.sa', 'SLA-breach notice issued; remediation plan due.'),
    ] },

  // ── PREVENTIVE actions (PA) — DD-43 will trigger more ────────
  { id: 'capa-017', code: 'CAPA-2026-017',
    titleEn: 'PA — Pre-emptive MFA hardening for sister legacy systems',
    titleAr: 'إجراء وقائي — تشديد المصادقة الثنائية على الأنظمة القديمة المُماثلة',
    descriptionEn: 'After capa-006 (legacy ERP MFA gap), proactively scan for the same pattern across other legacy systems.',
    type: 'PREVENTIVE', source: 'RISK_TREND',
    sourceRecordKey: 'RISK:101', sourceRecordTitle: 'Inadequate authentication on legacy ERP (parent risk)',
    parentCapaId: 'capa-006',
    frameworkRefs: ['NCA ECC 2.4.1', 'ISO 27001 A.5.17'],
    severity: 'MEDIUM', owner: 'iam.team@tss.sa', status: 'IMPLEMENTING',
    openedAt: '2026-03-15', dueDate: '2026-08-31', progressPct: 35,
    rcaMethod: 'NONE',
    rcaSteps: [],
    rootCause: 'Anticipated recurrence — legacy systems share procurement era and likely share controls gap.',
    rootCauseCategory: 'POLICY',
    history: [
      ev('2026-03-15T09:00:00', 'IDENTIFIED', 'risk.officer@tss.sa', 'Triggered preventively from capa-006 trend.'),
      ev('2026-03-22T10:00:00', 'IMPLEMENTING', 'iam.team@tss.sa', 'Inventory scan complete; 4 candidate systems.'),
    ] },

  { id: 'capa-018', code: 'CAPA-2026-018',
    titleEn: 'PA — Open audit findings approaching SLA breach',
    titleAr: 'إجراء وقائي — ملاحظات المراجعة المفتوحة المُقترِبة من إخلال SLA',
    descriptionEn: 'KRI-COM-01 trending up: 6 open findings within 14 days of due date. Pre-emptive remediation sprint.',
    type: 'PREVENTIVE', source: 'KRI_BREACH',
    sourceRecordKey: 'KRI:1006', sourceRecordTitle: 'Open audit findings beyond due date',
    frameworkRefs: ['IIA IPPF 2500.A1'],
    severity: 'MEDIUM', owner: 'compliance.lead@tss.sa', status: 'IMPLEMENTING',
    openedAt: '2026-04-22', dueDate: '2026-05-31', progressPct: 50,
    rcaMethod: 'PARETO',
    rcaSteps: [
      'Owner-team capacity: 50% of slippage',
      'Dependency on vendor: 25%',
      'Awaiting policy approval: 15%',
      'Other: 10%',
    ],
    rootCause: 'Owner-team capacity constraints in IAM and TPRM teams.',
    rootCauseCategory: 'PEOPLE',
    history: [
      ev('2026-04-22T09:00:00', 'IDENTIFIED', 'compliance.lead@tss.sa', 'KRI breach detected.'),
      ev('2026-04-23T10:00:00', 'IMPLEMENTING', 'compliance.lead@tss.sa', 'Daily stand-up triage of overdue findings.'),
    ] },

  // ── Process review (PA) ─────────────────────────────────────
  { id: 'capa-019', code: 'CAPA-2026-019',
    titleEn: 'PA — Annual TPRM contract-renewal cadence alignment',
    titleAr: 'إجراء وقائي — مواءمة جدولة تجديد عقود إدارة مخاطر الأطراف الثالثة سنوياً',
    descriptionEn: 'Address root cause of capa-001 systemically: align TPRM renewals with regulator-watch outputs.',
    type: 'PREVENTIVE', source: 'PROCESS_REVIEW',
    sourceRecordKey: 'CAPA:capa-001', sourceRecordTitle: 'Cross-border data transfer (parent CAPA)',
    parentCapaId: 'capa-001',
    frameworkRefs: ['ISO 9001 §10.2.2', 'PDPL §29'],
    severity: 'MEDIUM', owner: 'tprm.lead@tss.sa', status: 'IDENTIFIED',
    openedAt: '2026-04-10', dueDate: '2026-08-31', progressPct: 5,
    rcaMethod: 'NONE',
    rcaSteps: [],
    rootCause: 'Process gap identified by capa-001 RCA — addressing systemically.',
    rootCauseCategory: 'PROCESS',
    history: [
      ev('2026-04-10T09:00:00', 'IDENTIFIED', 'tprm.lead@tss.sa'),
    ] },

  { id: 'capa-020', code: 'CAPA-2026-020',
    titleEn: 'CCTV retention alignment across branches',
    titleAr: 'مواءمة فترة استبقاء كاميرات المراقبة في الفروع',
    descriptionEn: 'Three branches retained CCTV beyond 90-day policy. Reconfigure NVR retention; audit quarterly.',
    type: 'CORRECTIVE', source: 'AUDIT_FINDING',
    sourceRecordKey: 'FINDING:38', sourceRecordTitle: 'PDPL audit — CCTV retention',
    frameworkRefs: ['PDPL §15', 'ISO 27001 A.7.4'],
    severity: 'MEDIUM', owner: 'dpo@tss.sa', status: 'AUDIT_SIGNED_OFF',
    openedAt: '2026-03-08', dueDate: '2026-05-20', progressPct: 90,
    ownerTestAt: '2026-03-30', ownerTestResult: 'PASS',
    independentTestAt: '2026-04-15', independentTestResult: 'PASS',
    auditSignoffAt: '2026-04-26', auditSignedOffBy: 'audit.lead@tss.sa',
    effectivenessDueDate: '2026-07-26',
    rcaMethod: 'FIVE_WHYS',
    rcaSteps: [
      'Why was retention long? — NVR default config was 365 days.',
      'Why default? — Vendor template applied during 2024 rollout.',
      'Why not customised? — Procurement spec did not include retention requirement.',
      'Why omitted? — Data-protection input not collected during physical-security procurement.',
      'Why no DPO sign-off? — DPO not in physical-security workflow.',
    ],
    rootCause: 'DPO not embedded in physical-security procurement workflow.',
    rootCauseCategory: 'PROCESS',
    history: [
      ev('2026-03-08T09:00:00', 'IDENTIFIED', 'dpo@tss.sa'),
      ev('2026-03-12T10:00:00', 'IMPLEMENTING', 'dpo@tss.sa'),
      ev('2026-03-30T15:00:00', 'OWNER_TESTED', 'dpo@tss.sa'),
      ev('2026-04-15T16:00:00', 'INDEPENDENT_TESTED', 'security.officer@tss.sa'),
      ev('2026-04-26T16:00:00', 'AUDIT_SIGNED_OFF', 'audit.lead@tss.sa'),
    ] },
];

// ── Convenience accessors ──────────────────────────────────────────

/** All CAPAs filed against a specific source record. */
export function capasBySource(items: Capa[], sourceRecordKey: string): Capa[] {
  return items.filter(c => c.sourceRecordKey === sourceRecordKey);
}

/** All CAPAs in a given recurrence chain (top-level + children). */
export function capaChain(items: Capa[], rootId: string): Capa[] {
  return items.filter(c => c.id === rootId || c.parentCapaId === rootId);
}

/** Count by status — for the register stat strip. */
export function capaStatusCounts(items: Capa[]): Record<CapaStatus, number> {
  const empty: Record<CapaStatus, number> = {
    IDENTIFIED: 0, IMPLEMENTING: 0, OWNER_TESTED: 0, INDEPENDENT_TESTED: 0,
    AUDIT_SIGNED_OFF: 0, CLOSED: 0, VERIFICATION_FAILED: 0, CANCELLED: 0,
  };
  for (const c of items) empty[c.status] = (empty[c.status] ?? 0) + 1;
  return empty;
}

// ── Local mutation overlay (demo mode) ────────────────────────────
// The detail page / register can persist new CAPAs and status
// transitions in localStorage so the user sees changes survive
// reload. Same pattern as useCommunications.

const STORAGE_KEY = 'grc.capas';

interface CapaOverlay {
  added: Capa[];
  patches: Record<string, Partial<Capa>>;
}

function load(): CapaOverlay {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { added: [], patches: {} };
    const parsed = JSON.parse(raw);
    return { added: parsed.added ?? [], patches: parsed.patches ?? {} };
  } catch { return { added: [], patches: {} }; }
}

function save(o: CapaOverlay) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(o)); } catch { /* ignore */ }
}

/** React hook: merged static seed + localStorage overlay for CAPAs. */
export function useCapas() {
  const [overlay, setOverlay] = useState<CapaOverlay>(() => load());
  useEffect(() => save(overlay), [overlay]);

  const items: Capa[] = (() => {
    const merged = CAPAS.map(c => overlay.patches[c.id] ? { ...c, ...overlay.patches[c.id] } : c);
    return [...merged, ...overlay.added];
  })();

  const create = useCallback((init: Omit<Capa, 'id' | 'history' | 'progressPct'> & { id?: string; progressPct?: number }) => {
    const id = init.id ?? `capa-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`;
    const code = init.code ?? `CAPA-${new Date().getFullYear()}-${String(CAPAS.length + 1).padStart(3, '0')}`;
    const next: Capa = {
      ...init, id, code, progressPct: init.progressPct ?? 0,
      history: [{ at: new Date().toISOString(), status: init.status, actor: init.owner }],
    };
    setOverlay(prev => ({ ...prev, added: [...prev.added, next] }));
    return next;
  }, []);

  const advance = useCallback((id: string, to: CapaStatus, by: string, extra?: Partial<Capa>) => {
    setOverlay(prev => {
      const existingPatch = prev.patches[id] ?? {};
      const seed = CAPAS.find(c => c.id === id);
      const current = seed ? { ...seed, ...existingPatch } : prev.added.find(c => c.id === id);
      if (!current) return prev;
      const stamp = new Date().toISOString();
      const stampField =
        to === 'OWNER_TESTED'        ? { ownerTestAt: stamp, ownerTestResult: 'PASS' as TestResult } :
        to === 'INDEPENDENT_TESTED'  ? { independentTestAt: stamp, independentTestResult: 'PASS' as TestResult } :
        to === 'AUDIT_SIGNED_OFF'    ? { auditSignoffAt: stamp, auditSignedOffBy: by } :
        to === 'CLOSED'              ? { closedAt: stamp, progressPct: 100 } :
        to === 'CANCELLED'           ? { cancelledAt: stamp } :
        {};
      const updatedHistory = [...current.history, { at: stamp, status: to, actor: by, ...(extra?.history ? {} : {}) }];
      const newPatch = {
        ...existingPatch,
        ...stampField,
        ...(extra ?? {}),
        status: to,
        history: updatedHistory,
      };
      // Mutate added array if it's an overlay-created item
      if (prev.added.some(c => c.id === id)) {
        return {
          ...prev,
          added: prev.added.map(c => c.id === id ? { ...c, ...newPatch } : c),
        };
      }
      return { ...prev, patches: { ...prev.patches, [id]: newPatch } };
    });
  }, []);

  const update = useCallback((id: string, patch: Partial<Capa>) => {
    setOverlay(prev => {
      if (prev.added.some(c => c.id === id)) {
        return { ...prev, added: prev.added.map(c => c.id === id ? { ...c, ...patch } : c) };
      }
      return { ...prev, patches: { ...prev.patches, [id]: { ...(prev.patches[id] ?? {}), ...patch } } };
    });
  }, []);

  const reset = useCallback(() => setOverlay({ added: [], patches: {} }), []);

  return { items, create, advance, update, reset };
}
