// Workflow versioning — Sprint 6 follow-on (A3).
//
// "Publish v2.0 without breaking in-flight v1.0 instances." ServiceNow
// Flow Designer ships this; until now our workflow engine only had a
// single version per definition.
//
// Model: each WorkflowDefinitionDto becomes a *family* keyed by its
// `code` (e.g. "RISK_LIFECYCLE"). Each family has an immutable list of
// versions ("1.0", "1.1", "2.0", "2.1"). Exactly one version is the
// PUBLISHED version — new instances created today bind to it. In-flight
// instances bound to older versions stay on those versions until they
// reach a terminal state.

import { WORKFLOW_DEFINITIONS } from '../pages/workflow/mockWorkflowDefs';
import { ALL_WORKFLOWS } from './mockWorkflows';
import type { WorkflowInstance } from './workflows';
import type { WorkflowDefinitionDto } from './api';

export type VersionStatus = 'DRAFT' | 'PUBLISHED' | 'DEPRECATED' | 'ARCHIVED';

export interface WorkflowVersion {
  /** Version label e.g. "1.0", "2.0", "2.1". */
  version: string;
  status: VersionStatus;
  /** When this version was published. */
  publishedAt?: string;
  /** When superseded (becomes DEPRECATED). */
  supersededAt?: string;
  /** Bilingual change summary vs previous version. */
  changeSummaryEn: string;
  changeSummaryAr: string;
  /** Approver who published this version. */
  approvedBy?: string;
  /** State / transition counts for the diff summary. */
  stateCount: number;
  transitionCount: number;
  /** Specific changes since previous version. */
  changes: VersionChange[];
}

export interface VersionChange {
  kind: 'STATE_ADDED' | 'STATE_REMOVED' | 'TRANSITION_ADDED' | 'TRANSITION_REMOVED' | 'SLA_TIGHTENED' | 'SLA_RELAXED' | 'ROLE_CHANGED';
  detailEn: string;
  detailAr: string;
}

export interface WorkflowFamily {
  /** Definition code (e.g. "RISK_LIFECYCLE"). */
  code: string;
  /** Display name from current published version. */
  nameEn: string;
  nameAr: string;
  targetModule: string;
  versions: WorkflowVersion[];
  /** In-flight instance count per version. */
  instancesByVersion: Record<string, number>;
}

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString();

// ─── Demo version histories ────────────────────────────────────────────────

const VERSION_HISTORIES: Record<string, WorkflowVersion[]> = {
  'RISK_LIFECYCLE': [
    {
      version: '2.1', status: 'PUBLISHED',
      publishedAt: _daysAgo(45),
      changeSummaryEn: 'Added Risk Committee escalation transition for HIGH residual risks. Tightened ASSESS SLA from 14 to 7 days post NCA ECC-1:2026.',
      changeSummaryAr: 'إضافة انتقال تصعيد للجنة المخاطر للمخاطر المتبقية العالية. تشديد SLA التقييم من 14 إلى 7 أيام وفق NCA ECC-1:2026.',
      approvedBy: 'CRO + Board Risk Committee',
      stateCount: 7, transitionCount: 9,
      changes: [
        { kind: 'STATE_ADDED', detailEn: 'Added ESCALATED state for residual risk acceptance.', detailAr: 'إضافة حالة "مُصعَّد" لقبول المخاطر المتبقية.' },
        { kind: 'TRANSITION_ADDED', detailEn: 'New transition ASSESSED → ESCALATED (role: Risk Committee).', detailAr: 'انتقال جديد ASSESSED → ESCALATED (الدور: لجنة المخاطر).' },
        { kind: 'SLA_TIGHTENED', detailEn: 'ASSESS SLA tightened from 168h → 84h.', detailAr: 'تشديد SLA لـASSESS من 168 ساعة إلى 84 ساعة.' },
      ],
    },
    {
      version: '2.0', status: 'DEPRECATED',
      publishedAt: _daysAgo(280), supersededAt: _daysAgo(45),
      changeSummaryEn: 'Major rewrite: removed legacy ACCEPT shortcut path; mandatory MONITOR stage before CLOSE.',
      changeSummaryAr: 'إعادة كتابة كبيرة: إزالة مسار ACCEPT المختصر القديم؛ مرحلة MONITOR إلزامية قبل CLOSE.',
      approvedBy: 'Risk Committee',
      stateCount: 6, transitionCount: 8,
      changes: [
        { kind: 'STATE_ADDED', detailEn: 'MONITORING stage added.', detailAr: 'إضافة مرحلة المراقبة.' },
        { kind: 'TRANSITION_REMOVED', detailEn: 'Removed direct ASSESSED → CLOSED shortcut.', detailAr: 'إزالة مختصر ASSESSED → CLOSED.' },
      ],
    },
    {
      version: '1.0', status: 'ARCHIVED',
      publishedAt: _daysAgo(720), supersededAt: _daysAgo(280),
      changeSummaryEn: 'Initial release.',
      changeSummaryAr: 'الإصدار الأولي.',
      stateCount: 5, transitionCount: 6,
      changes: [],
    },
  ],
  'CYBER_INCIDENT': [
    {
      version: '3.0', status: 'PUBLISHED',
      publishedAt: _daysAgo(60),
      changeSummaryEn: 'NIST 800-61 Rev 3 alignment — added LESSONS_LEARNED stage as terminal. SDAIA notification now branches at TRIAGE.',
      changeSummaryAr: 'مواءمة NIST 800-61 الإصدار 3 — إضافة مرحلة LESSONS_LEARNED كنهائية. إبلاغ سدايا يتفرّع الآن عند TRIAGE.',
      approvedBy: 'CISO',
      stateCount: 8, transitionCount: 11,
      changes: [
        { kind: 'STATE_ADDED', detailEn: 'LESSONS_LEARNED terminal stage.', detailAr: 'مرحلة نهائية LESSONS_LEARNED.' },
        { kind: 'TRANSITION_ADDED', detailEn: 'TRIAGE → SDAIA_NOTIFY for PDPL-relevant incidents.', detailAr: 'TRIAGE → SDAIA_NOTIFY للحوادث ذات الصلة بـPDPL.' },
      ],
    },
    {
      version: '2.0', status: 'DEPRECATED',
      publishedAt: _daysAgo(380), supersededAt: _daysAgo(60),
      changeSummaryEn: 'Added ERADICATION + RECOVERY split (was single REMEDIATION stage).',
      changeSummaryAr: 'فصل ERADICATION + RECOVERY (كان مرحلة REMEDIATION واحدة).',
      stateCount: 7, transitionCount: 9,
      changes: [],
    },
    {
      version: '1.0', status: 'ARCHIVED',
      publishedAt: _daysAgo(900), supersededAt: _daysAgo(380),
      changeSummaryEn: 'Initial release based on ISO 27035.',
      changeSummaryAr: 'الإصدار الأولي بناءً على ISO 27035.',
      stateCount: 5, transitionCount: 6,
      changes: [],
    },
  ],
  'BREACH_NOTIFICATION': [
    {
      version: '2.0', status: 'PUBLISHED',
      publishedAt: _daysAgo(20),
      changeSummaryEn: 'PDPL Implementing Regulations 2026 alignment — auto-fire SDAIA notification at +24h regardless of severity assessment status.',
      changeSummaryAr: 'مواءمة لائحة PDPL التنفيذية 2026 — إطلاق تلقائي لإبلاغ سدايا عند +24 ساعة بغض النظر عن حالة تقييم الشدّة.',
      approvedBy: 'DPO + General Counsel',
      stateCount: 6, transitionCount: 8,
      changes: [
        { kind: 'SLA_TIGHTENED', detailEn: 'TRIAGE SLA tightened from 24h → 12h.', detailAr: 'تشديد SLA لـTRIAGE من 24 إلى 12 ساعة.' },
      ],
    },
    {
      version: '1.0', status: 'DEPRECATED',
      publishedAt: _daysAgo(420), supersededAt: _daysAgo(20),
      changeSummaryEn: 'Initial PDPL 72h breach notification workflow.',
      changeSummaryAr: 'سير عمل إبلاغ خرق PDPL خلال 72 ساعة الأولي.',
      stateCount: 6, transitionCount: 7,
      changes: [],
    },
  ],
};

// ─── Family rollup ─────────────────────────────────────────────────────────

export function buildFamilies(): WorkflowFamily[] {
  return WORKFLOW_DEFINITIONS.map(d => {
    const versions = VERSION_HISTORIES[d.code] ?? [{
      version: d.version,
      status: 'PUBLISHED' as VersionStatus,
      publishedAt: _daysAgo(180),
      changeSummaryEn: 'Initial release.',
      changeSummaryAr: 'الإصدار الأولي.',
      stateCount: 0,
      transitionCount: 0,
      changes: [],
    }];
    // Count instances per version — for demo, distribute existing instances
    // across versions (most on current, a few on previous to demonstrate
    // "in-flight on old version" survival).
    const familyInstances = ALL_WORKFLOWS.filter(w =>
      w.workflow.toLowerCase().includes(d.targetModule.toLowerCase()) ||
      d.code.split('_').some(part => w.workflow.toUpperCase().includes(part)),
    );
    const instancesByVersion: Record<string, number> = {};
    versions.forEach((v, idx) => {
      // 80% on PUBLISHED, 15% on previous DEPRECATED, 5% on older.
      const ratio = v.status === 'PUBLISHED' ? 0.8 : idx === 1 ? 0.15 : 0.05;
      instancesByVersion[v.version] = Math.round(familyInstances.length * ratio);
    });
    return {
      code: d.code, nameEn: d.nameEn, nameAr: d.nameAr ?? d.nameEn,
      targetModule: d.targetModule,
      versions, instancesByVersion,
    };
  });
}

// ─── Stats ─────────────────────────────────────────────────────────────────

export interface VersioningStats {
  totalFamilies: number;
  totalVersions: number;
  draftCount: number;
  publishedCount: number;
  deprecatedCount: number;
  archivedCount: number;
  /** Instances bound to non-published versions (i.e. surviving old code). */
  legacyInflight: number;
}

export function aggregateVersioning(families: WorkflowFamily[] = buildFamilies()): VersioningStats {
  let draft = 0, published = 0, deprecated = 0, archived = 0;
  let totalVersions = 0;
  let legacyInflight = 0;
  for (const f of families) {
    totalVersions += f.versions.length;
    for (const v of f.versions) {
      if (v.status === 'DRAFT') draft++;
      else if (v.status === 'PUBLISHED') published++;
      else if (v.status === 'DEPRECATED') deprecated++;
      else if (v.status === 'ARCHIVED') archived++;
      if (v.status !== 'PUBLISHED' && v.status !== 'ARCHIVED') {
        legacyInflight += f.instancesByVersion[v.version] ?? 0;
      }
    }
  }
  return {
    totalFamilies: families.length,
    totalVersions,
    draftCount: draft,
    publishedCount: published,
    deprecatedCount: deprecated,
    archivedCount: archived,
    legacyInflight,
  };
}

export const STATUS_TONE: Record<VersionStatus, 'success' | 'info' | 'warning' | 'neutral'> = {
  PUBLISHED:  'success',
  DRAFT:      'info',
  DEPRECATED: 'warning',
  ARCHIVED:   'neutral',
};

export const STATUS_LABEL: Record<VersionStatus, { en: string; ar: string }> = {
  PUBLISHED:  { en: 'Published',   ar: 'منشور' },
  DRAFT:      { en: 'Draft',       ar: 'مسودة' },
  DEPRECATED: { en: 'Deprecated',  ar: 'مُتقاعد' },
  ARCHIVED:   { en: 'Archived',    ar: 'مؤرشف' },
};

export const CHANGE_KIND_LABEL: Record<VersionChange['kind'], { en: string; ar: string }> = {
  STATE_ADDED:        { en: 'State added',         ar: 'إضافة حالة' },
  STATE_REMOVED:      { en: 'State removed',       ar: 'إزالة حالة' },
  TRANSITION_ADDED:   { en: 'Transition added',    ar: 'إضافة انتقال' },
  TRANSITION_REMOVED: { en: 'Transition removed',  ar: 'إزالة انتقال' },
  SLA_TIGHTENED:      { en: 'SLA tightened',       ar: 'تشديد SLA' },
  SLA_RELAXED:        { en: 'SLA relaxed',         ar: 'تخفيف SLA' },
  ROLE_CHANGED:       { en: 'Role changed',        ar: 'تغيير الدور' },
};

export type { WorkflowDefinitionDto };
