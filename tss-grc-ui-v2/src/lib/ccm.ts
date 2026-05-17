// Continuous Control Monitoring (CCM) engine — Sprint 4.
//
// Pulls together two existing pillars — the connector registry
// (`connectors.ts`) and the framework controls catalogue — and adds the
// missing third pillar: a *mapping* that says "this connector's check
// satisfies that control" plus a *freshness rule* that says "evidence
// from this check is good for N days; after that the control falls
// out of CCM coverage and a finding is auto-raised".
//
// This is the continuous-controls-monitoring pattern, scoped to the
// Saudi-aligned controls our compliance module already lists.

import type { ConnectorDefinition } from './connectors';
import { CONNECTOR_REGISTRY } from './connectors';
import type { WorkflowInstance } from './workflows';

// ─── Connector → control mapping ────────────────────────────────────────────

export interface CcmCheck {
  /** Stable identifier — `<connectorId>.<checkCode>`. */
  id: string;
  connectorId: string;
  /** What the connector is actually checking, e.g. "S3 bucket encryption-at-rest". */
  checkNameEn: string;
  checkNameAr: string;
  /** Controls this check provides evidence for. */
  controls: ControlRef[];
  /** How long evidence is valid before it goes stale (calendar days). */
  freshnessDays: number;
  /** ISO timestamp of the last successful pull. */
  lastEvidenceAt?: string;
  /** Result of the last pull. */
  lastResult: 'PASS' | 'FAIL' | 'WARN' | 'NO_DATA';
  /** Owner stakeholder code (matches the connector owner by default). */
  owner?: string;
}

export interface ControlRef {
  framework: 'NCA_ECC' | 'ISO_27001' | 'SOC_2' | 'PDPL' | 'PCI_DSS' | 'SAMA_CSF';
  code: string;
  titleEn: string;
  titleAr: string;
}

// Saudi-leaning catalogue: NCA ECC headlines first, then international
// crosswalks. Each control referenced by at least one CCM check.
const C_NCA_2_3 = (en: string, ar: string): ControlRef => ({
  framework: 'NCA_ECC', code: '2-3-1', titleEn: en, titleAr: ar,
});
const C_ISO_A832: ControlRef =
  { framework: 'ISO_27001', code: 'A.8.32', titleEn: 'Change management', titleAr: 'إدارة التغيير' };
const C_ISO_A824: ControlRef =
  { framework: 'ISO_27001', code: 'A.8.24', titleEn: 'Use of cryptography', titleAr: 'استخدام التشفير' };
const C_SOC2_CC61: ControlRef =
  { framework: 'SOC_2', code: 'CC6.1', titleEn: 'Logical access — restrictions', titleAr: 'الوصول المنطقي — القيود' };
const C_SOC2_CC74: ControlRef =
  { framework: 'SOC_2', code: 'CC7.4', titleEn: 'System monitoring — incident detection', titleAr: 'مراقبة الأنظمة' };
const C_NCA_2_6: ControlRef =
  { framework: 'NCA_ECC', code: '2-6', titleEn: 'Vulnerability management', titleAr: 'إدارة الثغرات' };
const C_NCA_2_4: ControlRef =
  { framework: 'NCA_ECC', code: '2-4', titleEn: 'Logging & monitoring', titleAr: 'التسجيل والمراقبة' };
const C_NCA_2_5: ControlRef =
  { framework: 'NCA_ECC', code: '2-5', titleEn: 'Cryptography', titleAr: 'التشفير' };
const C_PDPL_29: ControlRef =
  { framework: 'PDPL', code: 'Art. 29', titleEn: 'Cross-border transfer controls', titleAr: 'ضوابط النقل عبر الحدود' };
const C_SAMA_CSF_3_1: ControlRef =
  { framework: 'SAMA_CSF', code: '3.1.5', titleEn: 'Access management', titleAr: 'إدارة الوصول' };

// ─── Catalogue of CCM checks ────────────────────────────────────────────────
//
// Each connector typically runs many discrete checks. We enumerate the
// ones our reference Saudi/international controls depend on. Last-evidence
// timestamps anchor relative to "now" so the demo always shows realistic
// freshness without hard-coded dates going stale.

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString();

export const CCM_CHECKS: CcmCheck[] = [
  // AWS Evidence Connector — daily, multiple checks
  {
    id: 'cn-evidence-aws.s3-encryption',
    connectorId: 'cn-evidence-aws',
    checkNameEn: 'S3 bucket encryption-at-rest',
    checkNameAr: 'تشفير حاويات S3 أثناء التخزين',
    controls: [C_ISO_A824, C_SOC2_CC61, C_NCA_2_5],
    freshnessDays: 1,
    lastEvidenceAt: _daysAgo(0.2),
    lastResult: 'PASS',
    owner: 'cloud.platform@tss.sa',
  },
  {
    id: 'cn-evidence-aws.iam-mfa',
    connectorId: 'cn-evidence-aws',
    checkNameEn: 'IAM MFA enforcement (root + privileged)',
    checkNameAr: 'فرض المصادقة الثنائية للحسابات الإمتيازية',
    controls: [C_SOC2_CC61, C_SAMA_CSF_3_1, C_NCA_2_3('Identity & access management', 'إدارة الهوية والوصول')],
    freshnessDays: 1,
    lastEvidenceAt: _daysAgo(0.5),
    lastResult: 'WARN',
    owner: 'cloud.platform@tss.sa',
  },
  {
    id: 'cn-evidence-aws.security-group-open',
    connectorId: 'cn-evidence-aws',
    checkNameEn: 'No open security groups (0.0.0.0/0 on management ports)',
    checkNameAr: 'لا توجد مجموعات أمنية مفتوحة على منافذ الإدارة',
    controls: [C_SOC2_CC61, C_NCA_2_3('Network access control', 'التحكم في الوصول الشبكي')],
    freshnessDays: 1,
    lastEvidenceAt: _daysAgo(3.5), // stale
    lastResult: 'FAIL',
    owner: 'cloud.platform@tss.sa',
  },
  // Tenable — vulnerability MTTR
  {
    id: 'cn-tenable.critical-vulns',
    connectorId: 'cn-tenable',
    checkNameEn: 'Critical CVEs on internet-facing systems',
    checkNameAr: 'الثغرات الحرجة على الأنظمة المتصلة بالإنترنت',
    controls: [C_NCA_2_6],
    freshnessDays: 1,
    lastEvidenceAt: _daysAgo(0.3),
    lastResult: 'FAIL',
    owner: 'soc.lead@tss.sa',
  },
  // Splunk — log monitoring
  {
    id: 'cn-splunk.audit-coverage',
    connectorId: 'cn-splunk',
    checkNameEn: 'Audit-log forwarding from all critical systems',
    checkNameAr: 'تحويل سجلات التدقيق من جميع الأنظمة الحرجة',
    controls: [C_NCA_2_4, C_SOC2_CC74],
    freshnessDays: 1,
    lastEvidenceAt: _daysAgo(0.1),
    lastResult: 'PASS',
    owner: 'csirt.lead@tss.sa',
  },
  {
    id: 'cn-splunk.failed-login-correlation',
    connectorId: 'cn-splunk',
    checkNameEn: 'Failed-login correlation rule active',
    checkNameAr: 'قاعدة ربط محاولات الدخول الفاشلة نشطة',
    controls: [C_NCA_2_4, C_SOC2_CC74, C_SAMA_CSF_3_1],
    freshnessDays: 1,
    lastEvidenceAt: _daysAgo(0.1),
    lastResult: 'PASS',
    owner: 'csirt.lead@tss.sa',
  },
  // Vendor SOC 2 watcher
  {
    id: 'cn-vendor-attestation.soc2',
    connectorId: 'cn-vendor-attestation',
    checkNameEn: 'Vendor SOC 2 / ISO 27001 attestation currency',
    checkNameAr: 'سريان إقرارات الموردين SOC 2 / ISO 27001',
    controls: [{ framework: 'SOC_2', code: 'CC9.2', titleEn: 'Vendor risk management', titleAr: 'إدارة مخاطر الموردين' }],
    freshnessDays: 7,
    lastEvidenceAt: _daysAgo(8), // stale
    lastResult: 'WARN',
    owner: 'tprm.lead@tss.sa',
  },
  // SDAIA regulator watch — cross-border transfer
  {
    id: 'cn-sdaia-feed.cross-border-watch',
    connectorId: 'cn-sdaia-feed',
    checkNameEn: 'PDPL cross-border transfer adequacy decisions',
    checkNameAr: 'قرارات ملاءمة النقل عبر الحدود — PDPL',
    controls: [C_PDPL_29],
    freshnessDays: 7,
    lastEvidenceAt: _daysAgo(0.1),
    lastResult: 'PASS',
    owner: 'dpo@tss.sa',
  },
  // Change management
  {
    id: 'cn-evidence-aws.change-tracking',
    connectorId: 'cn-evidence-aws',
    checkNameEn: 'Production changes follow approved CAB process',
    checkNameAr: 'تغييرات الإنتاج تتبع إجراءات CAB المعتمدة',
    controls: [C_ISO_A832],
    freshnessDays: 7,
    lastEvidenceAt: _daysAgo(2),
    lastResult: 'PASS',
    owner: 'cloud.platform@tss.sa',
  },
];

// ─── Freshness classification ───────────────────────────────────────────────

export type CcmFreshness = 'FRESH' | 'STALE' | 'EXPIRED' | 'NO_DATA';

export function classifyFreshness(check: CcmCheck, now: Date = new Date()): CcmFreshness {
  if (!check.lastEvidenceAt) return 'NO_DATA';
  const daysOld = (now.getTime() - new Date(check.lastEvidenceAt).getTime()) / 86_400_000;
  const ratio = daysOld / check.freshnessDays;
  if (ratio <= 1) return 'FRESH';
  if (ratio <= 2) return 'STALE';
  return 'EXPIRED';
}

export const FRESHNESS_TONE: Record<CcmFreshness, 'success' | 'info' | 'warning' | 'danger' | 'neutral'> = {
  FRESH: 'success', STALE: 'warning', EXPIRED: 'danger', NO_DATA: 'neutral',
};

export const RESULT_TONE: Record<CcmCheck['lastResult'], 'success' | 'info' | 'warning' | 'danger' | 'neutral'> = {
  PASS: 'success', WARN: 'warning', FAIL: 'danger', NO_DATA: 'neutral',
};

// ─── Aggregation ────────────────────────────────────────────────────────────

export interface CcmCoverage {
  totalChecks: number;
  fresh: number;
  stale: number;
  expired: number;
  noData: number;
  passing: number;
  warning: number;
  failing: number;
  /** Distinct controls covered. */
  controlsCovered: number;
  /** Connectors actively contributing CCM checks. */
  activeConnectors: number;
}

export function aggregateCoverage(checks: ReadonlyArray<CcmCheck> = CCM_CHECKS, now: Date = new Date()): CcmCoverage {
  let fresh = 0, stale = 0, expired = 0, noData = 0;
  let passing = 0, warning = 0, failing = 0;
  const ctrlSet = new Set<string>();
  const connSet = new Set<string>();

  for (const c of checks) {
    const f = classifyFreshness(c, now);
    if (f === 'FRESH') fresh++;
    else if (f === 'STALE') stale++;
    else if (f === 'EXPIRED') expired++;
    else noData++;

    if (c.lastResult === 'PASS') passing++;
    else if (c.lastResult === 'WARN') warning++;
    else if (c.lastResult === 'FAIL') failing++;

    connSet.add(c.connectorId);
    for (const ctrl of c.controls) ctrlSet.add(`${ctrl.framework}:${ctrl.code}`);
  }
  return {
    totalChecks: checks.length,
    fresh, stale, expired, noData,
    passing, warning, failing,
    controlsCovered: ctrlSet.size,
    activeConnectors: connSet.size,
  };
}

// ─── Workflow synthesis ─────────────────────────────────────────────────────
//
// Stale or expired evidence becomes a `WorkflowInstance` so the SLA engine
// can show "X controls have evidence about to expire" alongside risk and
// privacy SLAs. The dueDate is `lastEvidenceAt + freshnessDays * 2` (i.e.
// the moment we cross from STALE → EXPIRED).

const STALE_EVIDENCE_STAGES = [
  'EVIDENCE_FRESH',
  'EVIDENCE_STALE',
  'EVIDENCE_EXPIRED',
  'CONTROL_FAILED',
] as const;

export function synthesiseStaleEvidenceWorkflows(
  checks: ReadonlyArray<CcmCheck> = CCM_CHECKS,
  now: Date = new Date(),
): WorkflowInstance[] {
  const out: WorkflowInstance[] = [];
  for (const c of checks) {
    if (!c.lastEvidenceAt) continue;
    const f = classifyFreshness(c, now);
    if (f === 'FRESH' || f === 'NO_DATA') continue;

    const last = new Date(c.lastEvidenceAt);
    const dueDate = new Date(last.getTime() + c.freshnessDays * 2 * 86_400_000);
    const ageDays = Math.round((now.getTime() - last.getTime()) / 86_400_000);
    const slaDays = c.freshnessDays * 2;
    const connector = CONNECTOR_REGISTRY.find(cn => cn.id === c.connectorId);

    out.push({
      id: `CCM-${c.id}`,
      module: 'Compliance',
      workflow: 'CCM Evidence Renewal',
      entityType: 'Evidence',
      entityRef: c.id,
      title: `${c.checkNameEn} — ${connector?.nameEn ?? c.connectorId}`,
      stages: [...STALE_EVIDENCE_STAGES],
      currentStage: f === 'STALE' ? 'EVIDENCE_STALE' : 'EVIDENCE_EXPIRED',
      progress: f === 'STALE' ? 50 : 80,
      owner: c.owner ?? 'Compliance',
      ownerRole: connector?.source,
      dueDate: dueDate.toISOString().slice(0, 10),
      ageDays,
      slaDays,
      escalation: 'AT_RISK', // overridden by deriveLiveSla
      nextStep: f === 'EXPIRED'
        ? `Re-run ${connector?.nameEn ?? 'connector'} or open compensating control`
        : `Re-pull evidence from ${connector?.nameEn ?? 'connector'}`,
      reason: c.lastResult === 'FAIL'
        ? `Last result: FAIL · ${c.controls.map(x => `${x.framework} ${x.code}`).join(', ')}`
        : `${ageDays}d since last successful pull (freshness budget ${c.freshnessDays}d)`,
      href: '/connectors',
    });
  }
  return out;
}

// ─── Helper for connector → checks ──────────────────────────────────────────
export function checksForConnector(connectorId: string, checks: ReadonlyArray<CcmCheck> = CCM_CHECKS): CcmCheck[] {
  return checks.filter(c => c.connectorId === connectorId);
}

export function connectorById(id: string): ConnectorDefinition | undefined {
  return CONNECTOR_REGISTRY.find(c => c.id === id);
}
