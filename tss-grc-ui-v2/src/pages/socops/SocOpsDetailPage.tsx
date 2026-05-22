import { useEffect, useState, type ReactNode } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ShieldAlert, BookOpen, ShieldCheck, LockKeyhole, Server, Users,
  ScanLine, AlertOctagon, FileSearch, Cpu, Globe, GraduationCap,
  BadgeAlert, Eye, Archive, Briefcase, Activity, CalendarDays, Package,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { EntityActivityTimeline } from '../../components/audit/EntityActivityTimeline';
import { useI18n } from '../../lib/i18n';
import { socopsApi } from '../../lib/socops';

/**
 * Generic detail surface for every SOC ops register entity. Route:
 * {@code /socops/:resource/:id}. Single page replaces what would otherwise
 * have been 18 near-identical bespoke detail pages — each entity is
 * configured below in {@link RESOURCE_CONFIG} (fetcher, label, icon, the
 * field list it should render, the audit-log entityType it uses).
 *
 * <p>Pages that want richer per-entity behaviour (workflow buttons,
 * step-by-step editors, inline actions) can graduate to a bespoke detail
 * page later — they just override their entry in {@code RESOURCE_CONFIG}
 * or get their own route. The generic page is the "no UI gap" floor.
 *
 * <p>Always drops in {@link EntityActivityTimeline} for the right
 * {@code entityType} so the audit log + F8 rule fires for that row show
 * up inline.
 */

// ─────────────────────────────────────────────────────────────────────────
// Field renderers
// ─────────────────────────────────────────────────────────────────────────

type FieldFormatter = (value: unknown, row: Record<string, unknown>) => ReactNode;

interface Field {
  key: string;
  label: { en: string; ar: string };
  format?: FieldFormatter;
}

const fmtDate: FieldFormatter = (v) => {
  if (typeof v !== 'string' || !v) return '—';
  return <span className="mono" style={{ fontSize: 12 }}>{v.slice(0, 16).replace('T', ' ')}</span>;
};

const fmtDateOnly: FieldFormatter = (v) => {
  if (typeof v !== 'string' || !v) return '—';
  return <span className="mono" style={{ fontSize: 12 }}>{v.slice(0, 10)}</span>;
};

const fmtBool: FieldFormatter = (v) => (
  <Badge tone={v ? 'success' : 'neutral'}>{v ? 'YES' : '—'}</Badge>
);

const fmtNum: FieldFormatter = (v) => (
  <span className="mono" style={{ fontSize: 12 }}>{v === null || v === undefined ? '—' : String(v)}</span>
);

const fmtSar: FieldFormatter = (v) => {
  if (typeof v !== 'number') return '—';
  return <span className="mono" style={{ fontSize: 12 }}>SAR {v.toLocaleString()}</span>;
};

function fmtBadge(tones: Record<string, BadgeTone>): FieldFormatter {
  return (v) => {
    if (v === null || v === undefined || v === '') return '—';
    const key = String(v);
    return <Badge tone={tones[key] ?? 'neutral'}>{key}</Badge>;
  };
}

const SEVERITY_TONE: Record<string, BadgeTone> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'neutral', INFO: 'neutral',
};

const STATUS_TONE: Record<string, BadgeTone> = {
  NEW: 'warning', OPEN: 'warning', TRIAGED: 'info', RUNNING: 'info', QUEUED: 'neutral',
  ACTIVE: 'success', COMPLETED: 'success', RESOLVED: 'success', CLOSED: 'neutral',
  FALSE_POSITIVE: 'neutral', CANCELLED: 'neutral', FAILED: 'danger', EXPIRED: 'danger',
  PENDING: 'warning', APPROVED: 'info', REJECTED: 'danger',
};

// ─────────────────────────────────────────────────────────────────────────
// Resource config — one row per SOC ops entity
// ─────────────────────────────────────────────────────────────────────────

interface ResourceConfig {
  /** Backend fetch — returns the raw entity. */
  fetcher: (id: number) => Promise<Record<string, unknown>>;
  /** Audit-log {@code entity_type} — must match the Java class name. */
  entityType: string;
  /** Sidebar label. */
  label: { en: string; ar: string };
  /** Module breadcrumb back to the register. */
  register: { path: string; label: { en: string; ar: string } };
  /** Icon for the page header. */
  Icon: typeof ShieldAlert;
  /** Headline = bold title + secondary line. Falls back gracefully. */
  headline: (row: Record<string, unknown>, isAr: boolean) => { title: string; sub?: string };
  /** Ordered fields to render in the body grid. */
  fields: Field[];
}

const RESOURCE_CONFIG: Record<string, ResourceConfig> = {
  events: {
    fetcher: (id) => socopsApi.eventGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'SecurityEvent',
    label: { en: 'SOC event', ar: 'حدث المركز' },
    register: { path: '/socops/events', label: { en: 'SOC event queue', ar: 'طابور أحداث المركز' } },
    Icon: ShieldAlert,
    headline: (r) => ({
      title: String(r.ruleName ?? r.externalId ?? `Event #${r.id}`),
      sub: [r.sourceSystem, r.severity, r.category].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'externalId',   label: { en: 'External ID',  ar: 'المعرف الخارجي' } },
      { key: 'sourceSystem', label: { en: 'Source',       ar: 'المصدر' } },
      { key: 'severity',     label: { en: 'Severity',     ar: 'الخطورة' }, format: fmtBadge(SEVERITY_TONE) },
      { key: 'status',       label: { en: 'Status',       ar: 'الحالة' },   format: fmtBadge(STATUS_TONE) },
      { key: 'category',     label: { en: 'Category',     ar: 'الفئة' } },
      { key: 'sourceHost',   label: { en: 'Source host',  ar: 'مضيف المصدر' } },
      { key: 'sourceIp',     label: { en: 'Source IP',    ar: 'IP المصدر' } },
      { key: 'destinationHost', label: { en: 'Dest. host', ar: 'مضيف الوجهة' } },
      { key: 'destinationIp',   label: { en: 'Dest. IP',   ar: 'IP الوجهة' } },
      { key: 'username',     label: { en: 'Username',     ar: 'اسم المستخدم' } },
      { key: 'eventTime',    label: { en: 'Event time',   ar: 'وقت الحدث' },     format: fmtDate },
      { key: 'ingestedAt',   label: { en: 'Ingested',     ar: 'تم الاستلام' },   format: fmtDate },
      { key: 'slaDueAt',     label: { en: 'SLA due',      ar: 'استحقاق SLA' },   format: fmtDate },
      { key: 'triagedAt',    label: { en: 'Triaged at',   ar: 'وقت الفرز' },     format: fmtDate },
      { key: 'resolvedAt',   label: { en: 'Resolved at',  ar: 'وقت الإغلاق' },   format: fmtDate },
      { key: 'notes',        label: { en: 'Notes',        ar: 'ملاحظات' } },
    ],
  },
  playbooks: {
    fetcher: (id) => socopsApi.playbookGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'IrPlaybook',
    label: { en: 'IR playbook', ar: 'دليل الاستجابة' },
    register: { path: '/socops/playbooks', label: { en: 'IR playbooks', ar: 'أدلة الاستجابة' } },
    Icon: BookOpen,
    headline: (r, isAr) => ({
      title: String((isAr ? r.titleAr : r.titleEn) ?? r.playbookCode ?? `Playbook #${r.id}`),
      sub: [r.category, r.severityTrigger, r.status].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'playbookCode',     label: { en: 'Code',           ar: 'الرمز' } },
      { key: 'category',         label: { en: 'Category',       ar: 'الفئة' } },
      { key: 'severityTrigger',  label: { en: 'Triggers at',    ar: 'يفعّل عند' }, format: fmtBadge(SEVERITY_TONE) },
      { key: 'status',           label: { en: 'Status',         ar: 'الحالة' },     format: fmtBadge(STATUS_TONE) },
      { key: 'targetMttdMinutes', label: { en: 'Target MTTD (min)', ar: 'متوسط الاكتشاف (د)' }, format: fmtNum },
      { key: 'targetMttrMinutes', label: { en: 'Target MTTR (min)', ar: 'متوسط الحل (د)' },     format: fmtNum },
      { key: 'alignedFrameworks', label: { en: 'Frameworks',    ar: 'الأطر' } },
      { key: 'invocationCount',  label: { en: 'Invocations',    ar: 'مرات التشغيل' }, format: fmtNum },
      { key: 'lastReviewedAt',   label: { en: 'Last reviewed',  ar: 'آخر مراجعة' },   format: fmtDate },
      { key: 'version',          label: { en: 'Version',        ar: 'النسخة' },       format: fmtNum },
    ],
  },
  pentests: {
    fetcher: (id) => socopsApi.pentestGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'PentestEngagement',
    label: { en: 'Pentest', ar: 'اختبار اختراق' },
    register: { path: '/socops/pentests', label: { en: 'Pentest engagements', ar: 'مشاركات اختبار الاختراق' } },
    Icon: ShieldCheck,
    headline: (r, isAr) => ({
      title: String((isAr ? r.titleAr : r.titleEn) ?? r.engagementCode ?? `Pentest #${r.id}`),
      sub: [r.pentestType, r.vendorName, r.status].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'engagementCode',   label: { en: 'Engagement code', ar: 'رمز المشاركة' } },
      { key: 'pentestType',      label: { en: 'Type',            ar: 'النوع' } },
      { key: 'vendorName',       label: { en: 'Vendor',          ar: 'المورد' } },
      { key: 'ncaCertifiedVendor', label: { en: 'NCA certified', ar: 'معتمد من الهيئة' }, format: fmtBool },
      { key: 'status',           label: { en: 'Status',          ar: 'الحالة' }, format: fmtBadge(STATUS_TONE) },
      { key: 'startedAt',        label: { en: 'Started',         ar: 'بداية' },     format: fmtDate },
      { key: 'completedAt',      label: { en: 'Completed',       ar: 'انتهاء' },    format: fmtDate },
      { key: 'reportDeliveredAt', label: { en: 'Report delivered', ar: 'تسليم التقرير' }, format: fmtDate },
      { key: 'retestDueAt',      label: { en: 'Retest due',      ar: 'استحقاق إعادة' }, format: fmtDate },
      { key: 'findingsTotal',    label: { en: 'Findings',        ar: 'ملاحظات' },   format: fmtNum },
      { key: 'findingsCritical', label: { en: 'Critical',        ar: 'حرجة' },      format: fmtNum },
      { key: 'findingsHigh',     label: { en: 'High',            ar: 'عالية' },     format: fmtNum },
      { key: 'scope',            label: { en: 'Scope',           ar: 'النطاق' } },
      { key: 'rulesOfEngagement', label: { en: 'Rules of engagement', ar: 'قواعد المشاركة' } },
    ],
  },
  'ot-assets': {
    fetcher: (id) => socopsApi.otAssetGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'OtAsset',
    label: { en: 'OT asset', ar: 'أصل تشغيلي' },
    register: { path: '/socops/ot-assets', label: { en: 'OT assets', ar: 'أصول العمليات' } },
    Icon: Server,
    headline: (r, isAr) => ({
      title: String((isAr ? r.titleAr : r.titleEn) ?? r.assetCode ?? `OT asset #${r.id}`),
      sub: [`L${r.purdueLevel ?? '?'}`, r.assetClass, r.criticality].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'assetCode',     label: { en: 'Asset code',    ar: 'رمز الأصل' } },
      { key: 'assetClass',    label: { en: 'Class',         ar: 'الفئة' } },
      { key: 'purdueLevel',   label: { en: 'Purdue level',  ar: 'مستوى بيرديو' }, format: fmtNum },
      { key: 'criticality',   label: { en: 'Criticality',   ar: 'الأهمية' },      format: fmtBadge(SEVERITY_TONE) },
      { key: 'safetyIntegrityLevel', label: { en: 'SIL',    ar: 'مستوى السلامة' } },
      { key: 'vendor',        label: { en: 'Vendor',        ar: 'المورد' } },
      { key: 'model',         label: { en: 'Model',         ar: 'الموديل' } },
      { key: 'firmwareVersion', label: { en: 'Firmware',    ar: 'البرنامج الثابت' } },
      { key: 'serialNumber',  label: { en: 'Serial #',      ar: 'الرقم التسلسلي' } },
      { key: 'siteCode',      label: { en: 'Site code',     ar: 'رمز الموقع' } },
      { key: 'siteName',      label: { en: 'Site',          ar: 'الموقع' } },
      { key: 'processFunction', label: { en: 'Process function', ar: 'الوظيفة' } },
      { key: 'industrialProtocols', label: { en: 'Protocols', ar: 'البروتوكولات' } },
      { key: 'lastPatchedAt', label: { en: 'Last patched',  ar: 'آخر تصحيح' },     format: fmtDate },
      { key: 'patchWindow',   label: { en: 'Patch window',  ar: 'نافذة التصحيح' } },
      { key: 'remoteAccessEnabled', label: { en: 'Remote access', ar: 'وصول عن بُعد' }, format: fmtBool },
      { key: 'networkSegment', label: { en: 'Network segment', ar: 'قطاع الشبكة' } },
      { key: 'status',        label: { en: 'Status',        ar: 'الحالة' },        format: fmtBadge(STATUS_TONE) },
    ],
  },
  'regulator-exams': {
    fetcher: (id) => socopsApi.regulatorExamGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'RegulatorExamination',
    label: { en: 'Regulator exam', ar: 'فحص تنظيمي' },
    register: { path: '/socops/regulator-exams', label: { en: 'Regulator exams', ar: 'الفحوصات التنظيمية' } },
    Icon: LockKeyhole,
    headline: (r, isAr) => ({
      title: String((isAr ? r.titleAr : r.titleEn) ?? r.examinationCode ?? `Exam #${r.id}`),
      sub: [r.regulator, r.examinationType].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'examinationCode', label: { en: 'Examination code', ar: 'رمز الفحص' } },
      { key: 'regulator',       label: { en: 'Regulator',        ar: 'الجهة التنظيمية' } },
      { key: 'examinationType', label: { en: 'Type',             ar: 'النوع' } },
      { key: 'scopeAreas',      label: { en: 'Scope areas',      ar: 'مناطق النطاق' } },
      { key: 'leadExaminerName', label: { en: 'Lead examiner',   ar: 'رئيس الفاحصين' } },
      { key: 'notifiedAt',      label: { en: 'Notified',         ar: 'وقت الإبلاغ' },  format: fmtDate },
      { key: 'fieldworkStartAt', label: { en: 'Fieldwork start', ar: 'بداية الميدان' }, format: fmtDate },
      { key: 'fieldworkEndAt',  label: { en: 'Fieldwork end',    ar: 'نهاية الميدان' }, format: fmtDate },
      { key: 'draftReportAt',   label: { en: 'Draft report',     ar: 'مسودة التقرير' }, format: fmtDate },
      { key: 'finalReportAt',   label: { en: 'Final report',     ar: 'التقرير النهائي' }, format: fmtDate },
      { key: 'responseDueAt',   label: { en: 'Response due',     ar: 'استحقاق الرد' },  format: fmtDate },
      { key: 'responseSubmittedAt', label: { en: 'Response submitted', ar: 'تسليم الرد' }, format: fmtDate },
    ],
  },
  'iam-jml': {
    fetcher: (id) => socopsApi.iamJmlGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'IamJmlEvent',
    label: { en: 'IAM event', ar: 'حدث هوية' },
    register: { path: '/socops/iam-jml', label: { en: 'IAM JML', ar: 'دورة الهوية' } },
    Icon: Users,
    headline: (r) => ({
      title: String(r.subjectUsername ?? r.eventCode ?? `IAM event #${r.id}`),
      sub: [r.eventType, r.subjectDepartment, r.subjectJobTitle].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'eventCode',         label: { en: 'Event code',     ar: 'رمز الحدث' } },
      { key: 'eventType',         label: { en: 'Type',           ar: 'النوع' } },
      { key: 'occurredAt',        label: { en: 'Occurred',       ar: 'وقت الحدث' }, format: fmtDate },
      { key: 'subjectUsername',   label: { en: 'Subject',        ar: 'الشخص' } },
      { key: 'subjectEmail',      label: { en: 'Email',          ar: 'البريد' } },
      { key: 'subjectDepartment', label: { en: 'Department',     ar: 'القسم' } },
      { key: 'subjectJobTitle',   label: { en: 'Job title',      ar: 'المسمى الوظيفي' } },
      { key: 'sourceSystem',      label: { en: 'Source system',  ar: 'نظام المصدر' } },
      { key: 'targetSystem',      label: { en: 'Target system',  ar: 'النظام الهدف' } },
      { key: 'roleOrEntitlement', label: { en: 'Role / entitlement', ar: 'الدور / الصلاحية' } },
      { key: 'privileged',        label: { en: 'Privileged',     ar: 'صلاحية حرجة' }, format: fmtBool },
      { key: 'sodViolationDetected', label: { en: 'SoD violation', ar: 'مخالفة فصل المهام' }, format: fmtBool },
      { key: 'sodViolationDetail', label: { en: 'SoD detail',    ar: 'تفاصيل المخالفة' } },
      { key: 'approverUsername',  label: { en: 'Approver',       ar: 'المعتمد' } },
      { key: 'approverRole',      label: { en: 'Approver role',  ar: 'دور المعتمد' } },
      { key: 'outcome',           label: { en: 'Outcome',        ar: 'النتيجة' }, format: fmtBadge(STATUS_TONE) },
      { key: 'justification',     label: { en: 'Justification',  ar: 'التبرير' } },
    ],
  },
  'threat-intel': {
    fetcher: (id) => socopsApi.threatIntelGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'ThreatIntel',
    label: { en: 'Threat intel', ar: 'استخبارات' },
    register: { path: '/socops/threat-intel', label: { en: 'Threat intel', ar: 'استخبارات سيبرانية' } },
    Icon: AlertOctagon,
    headline: (r, isAr) => ({
      title: String((isAr ? r.titleAr : r.titleEn) ?? r.externalId ?? `TI #${r.id}`),
      sub: [r.source, r.severity, r.tlp].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'source',          label: { en: 'Source',     ar: 'المصدر' } },
      { key: 'externalId',      label: { en: 'External ID', ar: 'المعرف الخارجي' } },
      { key: 'severity',        label: { en: 'Severity',   ar: 'الخطورة' }, format: fmtBadge(SEVERITY_TONE) },
      { key: 'tlp',             label: { en: 'TLP',        ar: 'TLP' } },
      { key: 'iocType',         label: { en: 'IOC type',   ar: 'نوع المؤشر' } },
      { key: 'iocValue',        label: { en: 'IOC value',  ar: 'قيمة المؤشر' } },
      { key: 'mitreTechniques', label: { en: 'MITRE techniques', ar: 'تقنيات MITRE' } },
      { key: 'affectedSectors', label: { en: 'Affected sectors', ar: 'القطاعات المتأثرة' } },
      { key: 'publishedAt',     label: { en: 'Published',  ar: 'تاريخ النشر' }, format: fmtDateOnly },
      { key: 'expiresAt',       label: { en: 'Expires',    ar: 'تاريخ الانتهاء' }, format: fmtDate },
      { key: 'actioned',        label: { en: 'Actioned',   ar: 'تم الإجراء' },  format: fmtBool },
      { key: 'actionedAt',      label: { en: 'Actioned at', ar: 'وقت الإجراء' }, format: fmtDate },
      { key: 'actionNotes',     label: { en: 'Notes',      ar: 'ملاحظات' } },
      { key: 'summary',         label: { en: 'Summary',    ar: 'الملخص' } },
    ],
  },
  'scanner-runs': {
    fetcher: (id) => socopsApi.scannerRunGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'ScannerRun',
    label: { en: 'Scanner run', ar: 'تشغيل ماسح' },
    register: { path: '/socops/scanner-runs', label: { en: 'Scanner runs', ar: 'تشغيلات الماسحات' } },
    Icon: ScanLine,
    headline: (r) => ({
      title: String(r.scanName ?? `${r.scanner} scan #${r.id}`),
      sub: [r.scanner, r.status].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'scanner',          label: { en: 'Scanner',    ar: 'الماسح' } },
      { key: 'scanName',         label: { en: 'Scan name',  ar: 'اسم المسح' } },
      { key: 'status',           label: { en: 'Status',     ar: 'الحالة' }, format: fmtBadge(STATUS_TONE) },
      { key: 'startedAt',        label: { en: 'Started',    ar: 'بداية' }, format: fmtDate },
      { key: 'completedAt',      label: { en: 'Completed',  ar: 'انتهاء' }, format: fmtDate },
      { key: 'assetsScanned',    label: { en: 'Assets scanned', ar: 'الأصول الممسوحة' }, format: fmtNum },
      { key: 'findingsTotal',    label: { en: 'Findings total', ar: 'إجمالي الملاحظات' }, format: fmtNum },
      { key: 'findingsCritical', label: { en: 'Critical',   ar: 'حرجة' }, format: fmtNum },
      { key: 'findingsHigh',     label: { en: 'High',       ar: 'عالية' }, format: fmtNum },
      { key: 'findingsMedium',   label: { en: 'Medium',     ar: 'متوسطة' }, format: fmtNum },
      { key: 'findingsLow',      label: { en: 'Low',        ar: 'منخفضة' }, format: fmtNum },
      { key: 'scope',            label: { en: 'Scope',      ar: 'النطاق' } },
      { key: 'errorMessage',     label: { en: 'Error',      ar: 'الخطأ' } },
    ],
  },
  'crypto-assets': {
    fetcher: (id) => socopsApi.cryptoAssetGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'CryptoAsset',
    label: { en: 'Crypto asset', ar: 'أصل تشفير' },
    register: { path: '/socops/crypto-assets', label: { en: 'Crypto inventory', ar: 'سجل التشفير' } },
    Icon: FileSearch,
    headline: (r, isAr) => ({
      title: String((isAr ? r.titleAr : r.titleEn) ?? r.subjectDn ?? r.assetCode ?? `Cert #${r.id}`),
      sub: [r.assetType, r.algorithm, r.keyLength != null ? `${r.keyLength} bit` : ''].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'assetCode',  label: { en: 'Asset code', ar: 'رمز الأصل' } },
      { key: 'assetType',  label: { en: 'Type',       ar: 'النوع' } },
      { key: 'subjectDn',  label: { en: 'Subject',    ar: 'الموضوع' } },
      { key: 'issuedBy',   label: { en: 'Issued by',  ar: 'الجهة المصدرة' } },
      { key: 'fingerprint', label: { en: 'Fingerprint', ar: 'البصمة' } },
      { key: 'algorithm',  label: { en: 'Algorithm',  ar: 'الخوارزمية' } },
      { key: 'keyLength',  label: { en: 'Key length', ar: 'طول المفتاح' }, format: fmtNum },
      { key: 'validFrom',  label: { en: 'Valid from', ar: 'صالح من' },     format: fmtDate },
      { key: 'validUntil', label: { en: 'Valid until', ar: 'صالح حتى' },   format: fmtDate },
    ],
  },
  sbom: {
    fetcher: (id) => api404(id, 'SBOM component lookup endpoint not implemented'),
    entityType: 'SbomComponent',
    label: { en: 'SBOM component', ar: 'مكوّن SBOM' },
    register: { path: '/socops/sbom', label: { en: 'SBOM', ar: 'قائمة المكوّنات' } },
    Icon: Package,
    headline: (r) => ({
      title: String(r.componentName ?? `Component #${r.id}`),
      sub: [r.componentVersion, r.packageEcosystem].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'applicationName',   label: { en: 'Application', ar: 'التطبيق' } },
      { key: 'componentName',     label: { en: 'Component',   ar: 'المكوّن' } },
      { key: 'componentVersion',  label: { en: 'Version',     ar: 'الإصدار' } },
      { key: 'packageEcosystem',  label: { en: 'Ecosystem',   ar: 'النظام' } },
      { key: 'supplier',          label: { en: 'Supplier',    ar: 'المورد' } },
      { key: 'license',           label: { en: 'License',     ar: 'الترخيص' } },
      { key: 'purl',              label: { en: 'PURL',        ar: 'PURL' } },
      { key: 'hasKnownCves',      label: { en: 'Has CVEs',    ar: 'يحوي ثغرات' }, format: fmtBool },
      { key: 'highestCveSeverity', label: { en: 'Highest CVE', ar: 'أعلى ثغرة' }, format: fmtBadge(SEVERITY_TONE) },
      { key: 'upgradeAvailable',  label: { en: 'Upgrade available', ar: 'ترقية متاحة' }, format: fmtBool },
      { key: 'lastScannedAt',     label: { en: 'Last scanned', ar: 'آخر مسح' }, format: fmtDate },
    ],
  },
  'zt-maturity': {
    fetcher: (id) => api404(id, 'ZT maturity row lookup endpoint not implemented'),
    entityType: 'ZtMaturity',
    label: { en: 'ZT pillar', ar: 'ركيزة ZT' },
    register: { path: '/socops/zt-maturity', label: { en: 'Zero-trust maturity', ar: 'نضج صفر-ثقة' } },
    Icon: Cpu,
    headline: (r) => ({
      title: String(r.pillar ?? `ZT row #${r.id}`),
      sub: r.currentStage ? String(r.currentStage) : undefined,
    }),
    fields: [
      { key: 'assessmentCode', label: { en: 'Assessment code', ar: 'رمز التقييم' } },
      { key: 'pillar',         label: { en: 'Pillar',          ar: 'الركيزة' } },
      { key: 'currentStage',   label: { en: 'Current stage',   ar: 'المرحلة الحالية' } },
      { key: 'targetStage',    label: { en: 'Target stage',    ar: 'المرحلة المستهدفة' } },
      { key: 'assessedAt',     label: { en: 'Assessed at',     ar: 'وقت التقييم' }, format: fmtDate },
      { key: 'targetByQuarter', label: { en: 'Target quarter', ar: 'الربع المستهدف' } },
      { key: 'notes',          label: { en: 'Notes',           ar: 'ملاحظات' } },
    ],
  },
  'seasonal-overlays': {
    fetcher: (id) => api404(id, 'Seasonal overlay lookup endpoint not implemented'),
    entityType: 'SeasonalOverlay',
    label: { en: 'Seasonal overlay', ar: 'موسم تهديدي' },
    register: { path: '/socops/seasonal-overlays', label: { en: 'Seasonal overlays', ar: 'الموجات الموسمية' } },
    Icon: CalendarDays,
    headline: (r, isAr) => ({
      title: String((isAr ? r.titleAr : r.titleEn) ?? r.overlayCode ?? `Overlay #${r.id}`),
      sub: r.hijriLabel ? String(r.hijriLabel) : undefined,
    }),
    fields: [
      { key: 'overlayCode',     label: { en: 'Overlay code',   ar: 'رمز الموسم' } },
      { key: 'startsAt',        label: { en: 'Starts',         ar: 'يبدأ' }, format: fmtDate },
      { key: 'endsAt',          label: { en: 'Ends',           ar: 'ينتهي' }, format: fmtDate },
      { key: 'hijriLabel',      label: { en: 'Hijri label',    ar: 'التسمية الهجرية' } },
      { key: 'threatProfile',   label: { en: 'Threat profile', ar: 'ملف التهديد' } },
      { key: 'autoTightenKris', label: { en: 'Auto-tighten KRIs', ar: 'تشديد المؤشرات' }, format: fmtBool },
      { key: 'autoTightenSlas', label: { en: 'Auto-tighten SLAs', ar: 'تشديد SLA' },     format: fmtBool },
      { key: 'isActive',        label: { en: 'Active',         ar: 'نشط' },               format: fmtBool },
    ],
  },
  awareness: {
    fetcher: (id) => socopsApi.awarenessGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'AwarenessCampaign',
    label: { en: 'Awareness campaign', ar: 'حملة وعي' },
    register: { path: '/socops/awareness', label: { en: 'Awareness', ar: 'حملات الوعي' } },
    Icon: GraduationCap,
    headline: (r, isAr) => ({
      title: String((isAr ? r.titleAr : r.titleEn) ?? r.campaignCode ?? `Campaign #${r.id}`),
      sub: [r.campaignType, r.status].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'campaignCode',  label: { en: 'Code',          ar: 'الرمز' } },
      { key: 'campaignType',  label: { en: 'Type',          ar: 'النوع' } },
      { key: 'status',        label: { en: 'Status',        ar: 'الحالة' }, format: fmtBadge(STATUS_TONE) },
      { key: 'startedAt',     label: { en: 'Started',       ar: 'بداية' }, format: fmtDate },
      { key: 'completedAt',   label: { en: 'Completed',     ar: 'انتهاء' }, format: fmtDate },
      { key: 'targetCount',   label: { en: 'Target count',  ar: 'العدد المستهدف' }, format: fmtNum },
      { key: 'completedCount', label: { en: 'Completed',    ar: 'مكتمل' }, format: fmtNum },
      { key: 'failedCount',   label: { en: 'Failed',        ar: 'فشل' }, format: fmtNum },
      { key: 'repeatOffenders', label: { en: 'Repeat offenders', ar: 'مكرر الأخطاء' }, format: fmtNum },
      { key: 'passRate',      label: { en: 'Pass rate (%)', ar: 'نسبة النجاح %' }, format: fmtNum },
      { key: 'topic',         label: { en: 'Topic',         ar: 'الموضوع' } },
    ],
  },
  appsec: {
    fetcher: (id) => socopsApi.appsecGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'AppsecFinding',
    label: { en: 'AppSec finding', ar: 'ملاحظة أمن تطبيقات' },
    register: { path: '/socops/appsec', label: { en: 'AppSec', ar: 'أمن التطبيقات' } },
    Icon: BadgeAlert,
    headline: (r) => ({
      title: String(r.findingTitle ?? r.cve ?? `Finding #${r.id}`),
      sub: [r.scanner, r.scanType, r.severity].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'scanner',           label: { en: 'Scanner',       ar: 'الماسح' } },
      { key: 'scanType',          label: { en: 'Scan type',     ar: 'نوع المسح' } },
      { key: 'applicationName',   label: { en: 'Application',   ar: 'التطبيق' } },
      { key: 'repositoryUrl',     label: { en: 'Repository',    ar: 'المستودع' } },
      { key: 'branch',            label: { en: 'Branch',        ar: 'الفرع' } },
      { key: 'cwe',               label: { en: 'CWE',           ar: 'CWE' } },
      { key: 'cve',               label: { en: 'CVE',           ar: 'CVE' } },
      { key: 'severity',          label: { en: 'Severity',      ar: 'الخطورة' }, format: fmtBadge(SEVERITY_TONE) },
      { key: 'cvssScore',         label: { en: 'CVSS',          ar: 'CVSS' }, format: fmtNum },
      { key: 'filePath',          label: { en: 'File path',     ar: 'المسار' } },
      { key: 'lineNumber',        label: { en: 'Line',          ar: 'السطر' }, format: fmtNum },
      { key: 'componentName',     label: { en: 'Component',     ar: 'المكوّن' } },
      { key: 'componentVersion',  label: { en: 'Component ver.', ar: 'إصدار المكوّن' } },
      { key: 'fixedVersion',      label: { en: 'Fixed in',      ar: 'تم الإصلاح في' } },
      { key: 'status',            label: { en: 'Status',        ar: 'الحالة' }, format: fmtBadge(STATUS_TONE) },
      { key: 'discoveredAt',      label: { en: 'Discovered',    ar: 'تاريخ الاكتشاف' }, format: fmtDate },
      { key: 'slaDueAt',          label: { en: 'SLA due',       ar: 'استحقاق SLA' }, format: fmtDate },
      { key: 'remediationAdvice', label: { en: 'Remediation',   ar: 'التوصية' } },
      { key: 'description',       label: { en: 'Description',   ar: 'الوصف' } },
    ],
  },
  dlp: {
    fetcher: (id) => socopsApi.dlpGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'DlpEvent',
    label: { en: 'DLP event', ar: 'حدث DLP' },
    register: { path: '/socops/dlp', label: { en: 'DLP events', ar: 'أحداث منع التسرّب' } },
    Icon: Eye,
    headline: (r) => ({
      title: String(r.policyName ?? r.externalId ?? `DLP #${r.id}`),
      sub: [r.sourceSystem, r.severity, r.dataClassification].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'externalId',        label: { en: 'External ID',   ar: 'المعرف الخارجي' } },
      { key: 'sourceSystem',      label: { en: 'Source',        ar: 'المصدر' } },
      { key: 'eventTime',         label: { en: 'Event time',    ar: 'وقت الحدث' }, format: fmtDate },
      { key: 'policyName',        label: { en: 'Policy',        ar: 'السياسة' } },
      { key: 'policyCategory',    label: { en: 'Policy category', ar: 'فئة السياسة' } },
      { key: 'dataClassification', label: { en: 'Data class',   ar: 'تصنيف البيانات' } },
      { key: 'username',          label: { en: 'User',          ar: 'المستخدم' } },
      { key: 'userEmail',         label: { en: 'Email',         ar: 'البريد' } },
      { key: 'department',        label: { en: 'Department',    ar: 'القسم' } },
      { key: 'channel',           label: { en: 'Channel',       ar: 'القناة' } },
      { key: 'destination',       label: { en: 'Destination',   ar: 'الوجهة' } },
      { key: 'fileName',          label: { en: 'File name',     ar: 'اسم الملف' } },
      { key: 'fileSizeBytes',     label: { en: 'File size (B)', ar: 'حجم الملف (بايت)' }, format: fmtNum },
      { key: 'fileHash',          label: { en: 'File hash',     ar: 'هاش الملف' } },
      { key: 'severity',          label: { en: 'Severity',      ar: 'الخطورة' }, format: fmtBadge(SEVERITY_TONE) },
      { key: 'actionTaken',       label: { en: 'Action taken',  ar: 'الإجراء' } },
      { key: 'reviewed',          label: { en: 'Reviewed',      ar: 'تمت المراجعة' }, format: fmtBool },
      { key: 'reviewOutcome',     label: { en: 'Outcome',       ar: 'النتيجة' } },
      { key: 'reviewNotes',       label: { en: 'Review notes',  ar: 'ملاحظات المراجعة' } },
    ],
  },
  'cyber-insurance': {
    fetcher: (id) => socopsApi.cyberInsuranceGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'CyberInsurance',
    label: { en: 'Cyber insurance', ar: 'تأمين سيبراني' },
    register: { path: '/socops/cyber-insurance', label: { en: 'Cyber insurance', ar: 'بوليصة التأمين' } },
    Icon: Briefcase,
    headline: (r) => ({
      title: String(r.policyNumber ?? `Policy #${r.id}`),
      sub: [r.carrierName, r.policyType].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'policyNumber',     label: { en: 'Policy number', ar: 'رقم البوليصة' } },
      { key: 'carrierName',      label: { en: 'Carrier',       ar: 'شركة التأمين' } },
      { key: 'brokerName',       label: { en: 'Broker',        ar: 'الوسيط' } },
      { key: 'policyType',       label: { en: 'Type',          ar: 'النوع' } },
      { key: 'coverageAreas',    label: { en: 'Coverage',      ar: 'التغطية' } },
      { key: 'aggregateLimitSar', label: { en: 'Aggregate limit', ar: 'الحد الإجمالي' }, format: fmtSar },
      { key: 'perEventLimitSar', label: { en: 'Per-event limit', ar: 'حد كل حادثة' }, format: fmtSar },
      { key: 'retentionSar',     label: { en: 'Retention',     ar: 'الاحتفاظ' }, format: fmtSar },
      { key: 'effectiveFrom',    label: { en: 'Effective from', ar: 'ساري من' }, format: fmtDateOnly },
      { key: 'effectiveTo',      label: { en: 'Effective to',   ar: 'ساري حتى' }, format: fmtDateOnly },
      { key: 'renewalDueAt',     label: { en: 'Renewal due',   ar: 'استحقاق التجديد' }, format: fmtDateOnly },
      { key: 'status',           label: { en: 'Status',        ar: 'الحالة' }, format: fmtBadge(STATUS_TONE) },
      { key: 'claimCount',       label: { en: 'Claims',        ar: 'المطالبات' }, format: fmtNum },
      { key: 'aggregateClaimsPaidSar', label: { en: 'Claims paid', ar: 'المدفوع' }, format: fmtSar },
    ],
  },
  'brand-monitoring': {
    fetcher: (id) => socopsApi.brandAlertGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'BrandMonitoring',
    label: { en: 'Brand alert', ar: 'تنبيه علامة تجارية' },
    register: { path: '/socops/brand-monitoring', label: { en: 'Brand monitoring', ar: 'مراقبة العلامة' } },
    Icon: Globe,
    headline: (r) => ({
      title: String(r.alertCode ?? r.urlObserved ?? `Alert #${r.id}`),
      sub: [r.sourceSystem, r.severity, r.alertType].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'alertCode',         label: { en: 'Alert code',     ar: 'رمز التنبيه' } },
      { key: 'sourceSystem',      label: { en: 'Source',         ar: 'المصدر' } },
      { key: 'detectedAt',        label: { en: 'Detected',       ar: 'وقت الاكتشاف' }, format: fmtDate },
      { key: 'severity',          label: { en: 'Severity',       ar: 'الخطورة' }, format: fmtBadge(SEVERITY_TONE) },
      { key: 'alertType',         label: { en: 'Alert type',     ar: 'نوع التنبيه' } },
      { key: 'brandKeyword',      label: { en: 'Brand keyword',  ar: 'كلمة مفتاحية' } },
      { key: 'location',          label: { en: 'Location',       ar: 'الموقع' } },
      { key: 'urlObserved',       label: { en: 'URL observed',   ar: 'العنوان المرصود' } },
      { key: 'evidenceSummary',   label: { en: 'Evidence',       ar: 'الدليل' } },
      { key: 'status',            label: { en: 'Status',         ar: 'الحالة' }, format: fmtBadge(STATUS_TONE) },
      { key: 'takedownRequestedAt', label: { en: 'Takedown requested', ar: 'طلب الإزالة' }, format: fmtDate },
      { key: 'takedownCompletedAt', label: { en: 'Takedown completed', ar: 'تمت الإزالة' }, format: fmtDate },
    ],
  },
  forensics: {
    fetcher: (id) => socopsApi.forensicsGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'ForensicsArtefact',
    label: { en: 'Forensics artefact', ar: 'دليل جنائي' },
    register: { path: '/socops/forensics', label: { en: 'Forensics', ar: 'الأدلة الجنائية' } },
    Icon: Archive,
    headline: (r, isAr) => ({
      title: String((isAr ? r.titleAr : r.titleEn) ?? r.artefactCode ?? `Artefact #${r.id}`),
      sub: [r.artefactType, r.caseCode].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'artefactCode',       label: { en: 'Artefact code', ar: 'رمز الدليل' } },
      { key: 'caseCode',           label: { en: 'Case code',     ar: 'رمز القضية' } },
      { key: 'artefactType',       label: { en: 'Type',          ar: 'النوع' } },
      { key: 'sourceHost',         label: { en: 'Source host',   ar: 'مضيف المصدر' } },
      { key: 'capturedAt',         label: { en: 'Captured',      ar: 'تاريخ الالتقاط' }, format: fmtDate },
      { key: 'capturedByHandler',  label: { en: 'Captured by',   ar: 'الملتقط' } },
      { key: 'captureTool',        label: { en: 'Capture tool',  ar: 'الأداة' } },
      { key: 'captureMethod',      label: { en: 'Capture method', ar: 'الطريقة' } },
      { key: 'sizeBytes',          label: { en: 'Size (bytes)',  ar: 'الحجم (بايت)' }, format: fmtNum },
      { key: 'sha256Hash',         label: { en: 'SHA-256',       ar: 'SHA-256' } },
      { key: 'md5Hash',            label: { en: 'MD5',           ar: 'MD5' } },
      { key: 'integrityStatus',    label: { en: 'Integrity',     ar: 'السلامة' }, format: fmtBadge(STATUS_TONE) },
      { key: 'integrityVerifiedAt', label: { en: 'Integrity verified', ar: 'تحقق السلامة' }, format: fmtDate },
      { key: 'currentCustodian',   label: { en: 'Custodian',     ar: 'الحافظ' } },
      { key: 'currentLocation',    label: { en: 'Location',      ar: 'الموقع' } },
      { key: 'retentionUntil',     label: { en: 'Retention until', ar: 'حتى تاريخ' }, format: fmtDate },
      { key: 'legalHold',          label: { en: 'Legal hold',    ar: 'حجز قانوني' }, format: fmtBool },
      { key: 'privileged',         label: { en: 'Privileged',    ar: 'مميّز' }, format: fmtBool },
      { key: 'disposedAt',         label: { en: 'Disposed',      ar: 'تم التخلص' }, format: fmtDate },
      { key: 'disposalMethod',     label: { en: 'Disposal method', ar: 'طريقة التخلص' } },
    ],
  },
  'ma-due-diligence': {
    fetcher: (id) => socopsApi.maDueDiligenceGet(id) as unknown as Promise<Record<string, unknown>>,
    entityType: 'MaDueDiligence',
    label: { en: 'M&A DD', ar: 'العناية الواجبة' },
    register: { path: '/socops/ma-due-diligence', label: { en: 'M&A cyber DD', ar: 'العناية الواجبة' } },
    Icon: Activity,
    headline: (r, isAr) => ({
      title: String((isAr ? r.titleAr : r.titleEn) ?? r.engagementCode ?? `DD #${r.id}`),
      sub: [r.targetEntityName, r.dealType, r.phase].filter(Boolean).join(' · '),
    }),
    fields: [
      { key: 'engagementCode',     label: { en: 'Engagement code', ar: 'رمز المشاركة' } },
      { key: 'targetEntityName',   label: { en: 'Target entity',   ar: 'الكيان المستهدف' } },
      { key: 'targetIndustry',     label: { en: 'Industry',        ar: 'القطاع' } },
      { key: 'targetCountry',      label: { en: 'Country',         ar: 'الدولة' } },
      { key: 'dealType',           label: { en: 'Deal type',       ar: 'نوع الصفقة' } },
      { key: 'phase',              label: { en: 'Phase',           ar: 'المرحلة' } },
      { key: 'dealStatus',         label: { en: 'Deal status',     ar: 'حالة الصفقة' } },
      { key: 'findingsTotal',      label: { en: 'Findings',        ar: 'الملاحظات' }, format: fmtNum },
      { key: 'findingsCritical',   label: { en: 'Critical',        ar: 'حرجة' }, format: fmtNum },
      { key: 'findingsHigh',       label: { en: 'High',            ar: 'عالية' }, format: fmtNum },
      { key: 'priceAdjustmentSar', label: { en: 'Price adjustment', ar: 'تعديل السعر' }, format: fmtSar },
      { key: 'cyberLeadName',      label: { en: 'Cyber lead',      ar: 'قائد الأمن' } },
      { key: 'legalLeadName',      label: { en: 'Legal lead',      ar: 'القائد القانوني' } },
      { key: 'financeLeadName',    label: { en: 'Finance lead',    ar: 'القائد المالي' } },
      { key: 'loiSignedAt',        label: { en: 'LOI signed',      ar: 'توقيع نية الشراء' }, format: fmtDate },
      { key: 'fieldworkStartAt',   label: { en: 'Fieldwork start', ar: 'بداية الميدان' }, format: fmtDate },
      { key: 'fieldworkEndAt',     label: { en: 'Fieldwork end',   ar: 'نهاية الميدان' }, format: fmtDate },
      { key: 'finalReportAt',      label: { en: 'Final report',    ar: 'التقرير النهائي' }, format: fmtDate },
      { key: 'dealCloseAt',        label: { en: 'Deal close',      ar: 'إقفال الصفقة' }, format: fmtDate },
      { key: 'cyberRecommendation', label: { en: 'Recommendation', ar: 'التوصية' } },
      { key: 'recommendationNotes', label: { en: 'Notes',          ar: 'ملاحظات' } },
    ],
  },
};

/** Helper to fail gracefully for entities whose backend get-by-id endpoint
 *  hasn't been wired yet (SBOM components, ZT maturity rows, seasonal
 *  overlays). Returns a stub that lets the page render an Empty state
 *  instead of crashing. */
function api404(_id: number, reason: string): Promise<Record<string, unknown>> {
  return Promise.reject(new Error(reason));
}

// ─────────────────────────────────────────────────────────────────────────
// Page
// ─────────────────────────────────────────────────────────────────────────

export function SocOpsDetailPage() {
  const { resource, id } = useParams<{ resource: string; id: string }>();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const config = resource ? RESOURCE_CONFIG[resource] : undefined;
  const [row, setRow] = useState<Record<string, unknown> | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!config || !id) { setLoading(false); return; }
    let cancelled = false;
    setLoading(true);
    setErr(null);
    config.fetcher(Number(id))
      .then(r => { if (!cancelled) setRow(r); })
      .catch(e => { if (!cancelled) setErr((e as Error).message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [config, id]);

  if (!config) {
    return (
      <Shell title={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}>
        <div style={{ padding: 32 }}>
          <EmptyState Icon={ShieldAlert}
            title={isAr ? 'الكيان غير معروف' : 'Unknown SOC entity'}
            sub={`/${resource}`} />
          <div style={{ marginTop: 16, textAlign: 'center' }}>
            <Link to="/socops" style={{ color: 'var(--primary)', fontSize: 13 }}>
              ← {isAr ? 'عودة لعمليات الأمن' : 'Back to SOC Operations'}
            </Link>
          </div>
        </div>
      </Shell>
    );
  }

  if (loading) {
    return (
      <Shell title={isAr ? config.label.ar : config.label.en}>
        <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>
          {isAr ? 'جارٍ التحميل…' : 'Loading…'}
        </div>
      </Shell>
    );
  }

  if (err || !row) {
    return (
      <Shell title={isAr ? config.label.ar : config.label.en}>
        <DetailHeader
          breadcrumbs={[
            { label: isAr ? 'عمليات الأمن' : 'SOC Ops', to: '/socops' },
            { label: isAr ? config.register.label.ar : config.register.label.en, to: config.register.path },
            { label: `#${id}` },
          ]}
          title={isAr ? 'تعذّر التحميل' : 'Failed to load'}
          sub={err ?? (isAr ? 'لم يتم العثور على السجل' : 'Record not found')}
          backTo={config.register.path}
        />
      </Shell>
    );
  }

  const headline = config.headline(row, isAr);

  return (
    <Shell title={isAr ? config.label.ar : config.label.en}>
      <DetailHeader
        breadcrumbs={[
          { label: isAr ? 'عمليات الأمن' : 'SOC Ops', to: '/socops' },
          { label: isAr ? config.register.label.ar : config.register.label.en, to: config.register.path },
          { label: `#${row.id ?? id}` },
        ]}
        eyebrow={isAr ? config.label.ar : config.label.en}
        title={headline.title}
        sub={headline.sub}
        backTo={config.register.path}
      />

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1fr)', gap: 16, alignItems: 'start' }}>
        <Card title={isAr ? 'التفاصيل' : 'Details'} Icon={config.Icon}>
          <dl style={{
            display: 'grid', gridTemplateColumns: '180px 1fr',
            margin: 0, gap: '8px 16px', alignItems: 'baseline',
          }}>
            {config.fields.map(f => {
              const v = row[f.key];
              if (v === null || v === undefined || v === '') return null;
              return (
                <FieldRow key={f.key}
                  label={isAr ? f.label.ar : f.label.en}
                  value={f.format ? f.format(v, row) : <span style={{ fontSize: 12 }}>{String(v)}</span>}
                />
              );
            })}
          </dl>
        </Card>

        <EntityActivityTimeline entityType={config.entityType} entityId={Number(row.id ?? id)} />
      </div>
    </Shell>
  );
}

function FieldRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <>
      <dt style={{ fontSize: 12, color: 'var(--ink-3)', fontWeight: 500 }}>{label}</dt>
      <dd style={{ margin: 0, fontSize: 13, color: 'var(--ink-1)' }}>{value}</dd>
    </>
  );
}
