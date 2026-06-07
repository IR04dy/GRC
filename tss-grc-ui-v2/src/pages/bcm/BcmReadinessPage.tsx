import { useEffect, useMemo, useState } from 'react';
import { CalendarClock, Shield, AlertTriangle, Activity, Phone, FileWarning, Workflow, Plus } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import {
  listCycles, transitionCycle,
  listGovernance, createGovernance, transitionGovernance,
  listRisks, createRisk,
  listStrategies,
  listContacts, recordCallTreeTest, contactTestStats,
  cycleStatusTone, governanceStatusTone, bcmRiskStatusTone, residualToTone,
  type BcmAssessmentCycle, type BcmGovernanceRecord, type BcmRisk,
  type ContinuityStrategy, type BcmCommunicationContact, type CycleStatus, type GovernanceStatus,
  type ThreatCategory,
} from '../../lib/bcmCycle';
import { downloadEvidencePackage } from '../../lib/bcmEvidencePackage';

/**
 * BCM Readiness page — single workspace for BCM-1, BCM-2 and BCM-3
 * artefacts. Tabs:
 *   - Cycles
 *   - Governance
 *   - BCM Risks
 *   - Strategies
 *   - Call Tree (BCM-2)
 *
 * Designed as a "one-page workspace" pattern matching the existing
 * `/bcm` page. The improvement plan calls for 25 sub-pages eventually
 * (BCM_IMPROVEMENT_PLAN.md §8) — this page is the foundation those
 * will fold into during BCM-4 / BCM-5 sprints.
 */
type TabId = 'cycles' | 'governance' | 'risks' | 'strategies' | 'calltree';

// ── BCM risk authoring vocab (bilingual) ───────────────────────────────
// 18 ISO 22301 / NCA threat categories surfaced as a searchable select.
const THREAT_CATEGORIES: Array<{ value: ThreatCategory; en: string; ar: string }> = [
  { value: 'TECH_FAILURE',             en: 'Technology failure',            ar: 'عطل تقني' },
  { value: 'CYBERATTACK',              en: 'Cyberattack',                   ar: 'هجوم سيبراني' },
  { value: 'DATA_CENTER_OUTAGE',       en: 'Data-centre outage',            ar: 'انقطاع مركز البيانات' },
  { value: 'CLOUD_OUTAGE',             en: 'Cloud outage',                  ar: 'انقطاع الحوسبة السحابية' },
  { value: 'TELECOM_OUTAGE',           en: 'Telecom outage',                ar: 'انقطاع الاتصالات' },
  { value: 'POWER_FAILURE',            en: 'Power failure',                 ar: 'انقطاع الطاقة' },
  { value: 'FACILITY_UNAVAILABILITY',  en: 'Facility unavailability',       ar: 'عدم توفر المقر' },
  { value: 'WORKFORCE_UNAVAILABILITY', en: 'Workforce unavailability',      ar: 'عدم توفر الكوادر' },
  { value: 'SUPPLIER_FAILURE',         en: 'Supplier failure',              ar: 'إخفاق مورّد' },
  { value: 'PANDEMIC',                 en: 'Pandemic',                      ar: 'جائحة' },
  { value: 'NATURAL_HAZARD',           en: 'Natural hazard',                ar: 'خطر طبيعي' },
  { value: 'FIRE',                     en: 'Fire',                          ar: 'حريق' },
  { value: 'FLOOD',                    en: 'Flood',                         ar: 'فيضان' },
  { value: 'REGIONAL_DISRUPTION',      en: 'Regional disruption',           ar: 'اضطراب إقليمي' },
  { value: 'REGULATORY_DISRUPTION',    en: 'Regulatory disruption',         ar: 'اضطراب تنظيمي' },
  { value: 'DATA_LOSS',                en: 'Data loss',                     ar: 'فقدان بيانات' },
  { value: 'THIRD_PARTY_DEPENDENCY',   en: 'Third-party dependency',        ar: 'اعتماد على طرف ثالث' },
  { value: 'CRITICAL_INFRASTRUCTURE',  en: 'Critical infrastructure',       ar: 'بنية تحتية حرجة' },
];

type ControlEffectiveness = NonNullable<BcmRisk['controlEffectiveness']>;
const CONTROL_EFFECTIVENESS: Array<{ value: ControlEffectiveness; en: string; ar: string }> = [
  { value: 'EFFECTIVE',         en: 'Effective (residual ÷4)',        ar: 'فعّالة (المتبقّي ÷٤)' },
  { value: 'NEEDS_IMPROVEMENT', en: 'Needs improvement (÷2)',         ar: 'تحتاج تحسيناً (÷٢)' },
  { value: 'INEFFECTIVE',       en: 'Ineffective (no reduction)',     ar: 'غير فعّالة (دون تخفيض)' },
  { value: 'NOT_TESTED',        en: 'Not tested (no reduction)',      ar: 'لم تُختبر (دون تخفيض)' },
];

// ── BCM governance-policy review cadence (bilingual) ───────────────────
type ReviewFrequency = NonNullable<BcmGovernanceRecord['reviewFrequency']>;
const REVIEW_FREQUENCIES: Array<{ value: ReviewFrequency; en: string; ar: string }> = [
  { value: 'ANNUAL',      en: 'Annual',       ar: 'سنوي' },
  { value: 'SEMI_ANNUAL', en: 'Semi-annual',  ar: 'نصف سنوي' },
  { value: 'QUARTERLY',   en: 'Quarterly',    ar: 'ربع سنوي' },
  { value: 'ON_CHANGE',   en: 'On change',    ar: 'عند التغيير' },
];

export function BcmReadinessPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const dialog = useDialog();

  const [tab, setTab] = useState<TabId>('cycles');

  // ── State ────
  const [cycles, setCycles] = useState<BcmAssessmentCycle[]>([]);
  const [governance, setGovernance] = useState<BcmGovernanceRecord[]>([]);
  const [risks, setRisks] = useState<BcmRisk[]>([]);
  const [strategies, setStrategies] = useState<ContinuityStrategy[]>([]);
  const [contacts, setContacts] = useState<BcmCommunicationContact[]>([]);
  const [contactStats, setContactStats] = useState<{ passed: number; failed: number; notReached: number } | null>(null);
  const [loading, setLoading] = useState(false);
  const [openRisk, setOpenRisk] = useState(false);
  const [openGov, setOpenGov] = useState(false);

  // ── Loaders ────
  async function reload() {
    setLoading(true);
    // Per-call .catch() so a partial backend failure (e.g. missing-table 503
    // on a fresh dev DB) doesn't blank the whole page. Each panel degrades
    // independently to its empty default.
    const [c, g, r, s, ct, cs] = await Promise.all([
      listCycles().catch(() => []),
      listGovernance().catch(() => []),
      listRisks().catch(() => []),
      listStrategies().catch(() => []),
      listContacts().catch(() => []),
      contactTestStats().catch(() => null),
    ]);
    setCycles(c); setGovernance(g); setRisks(r); setStrategies(s);
    setContacts(ct); setContactStats(cs);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Actions ────
  async function moveCycle(c: BcmAssessmentCycle, to: CycleStatus) {
    try {
      await transitionCycle(c.id, to);
      toast.success(isAr ? `الدورة → ${to}` : `Cycle → ${to}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Transition failed');
    }
  }
  async function moveGovernance(g: BcmGovernanceRecord, to: GovernanceStatus) {
    try {
      await transitionGovernance(g.id, to);
      toast.success(isAr ? `السياسة → ${to}` : `Governance → ${to}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Transition failed');
    }
  }
  async function recordTest(c: BcmCommunicationContact) {
    const ans = await dialog.prompt({
      title: isAr ? 'سجّل نتيجة اختبار الاتصال' : 'Record call-tree test result',
      label: isAr ? 'اكتب PASSED / FAILED / NOT_REACHED' : 'Type PASSED / FAILED / NOT_REACHED',
      required: true,
    });
    if (!ans) return;
    const r = String(ans).toUpperCase().trim();
    if (!['PASSED', 'FAILED', 'NOT_REACHED'].includes(r)) {
      toast.danger(isAr ? 'قيمة غير صالحة' : 'Invalid value');
      return;
    }
    try {
      await recordCallTreeTest(c.id, r as 'PASSED' | 'FAILED' | 'NOT_REACHED');
      toast.success(isAr ? 'سُجّلت النتيجة' : 'Recorded');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Record failed');
    }
  }

  // ── Add a BCM risk (inherent/residual scores computed server-side) ─────
  const riskInitial: Record<string, string> = useMemo(() => ({
    titleEn: '', titleAr: '',
    threatCategory: 'CYBERATTACK',
    likelihood: '3', impact: '3',
    controlEffectiveness: 'NOT_TESTED',
    existingControls: '',
  }), []);

  const riskFields: FieldDef[] = useMemo(() => [
    { key: 'titleEn', kind: 'text', required: true, span: 6, maxLength: 200,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (EN)' },
    { key: 'titleAr', kind: 'text', span: 6, maxLength: 200,
      label: isAr ? 'العنوان (عربي)' : 'Title (AR)' },
    { key: 'threatCategory', kind: 'select-searchable', required: true, span: 12,
      label: isAr ? 'فئة التهديد' : 'Threat category',
      options: THREAT_CATEGORIES.map(t => ({ value: t.value, label: isAr ? t.ar : t.en })) },
    { key: 'likelihood', kind: 'number', span: 3, min: 1, max: 5,
      label: isAr ? 'الاحتمالية (١-٥)' : 'Likelihood (1-5)' },
    { key: 'impact', kind: 'number', span: 3, min: 1, max: 5,
      label: isAr ? 'الأثر (١-٥)' : 'Impact (1-5)' },
    { key: 'controlEffectiveness', kind: 'select', span: 6,
      label: isAr ? 'فاعلية الضوابط' : 'Control effectiveness',
      options: CONTROL_EFFECTIVENESS.map(c => ({ value: c.value, label: isAr ? c.ar : c.en })) },
    { key: 'existingControls', kind: 'textarea', span: 12, rows: 3,
      label: isAr ? 'الضوابط القائمة' : 'Existing controls' },
  ], [isAr]);

  async function submitRisk(v: Record<string, string>) {
    const titleEn = String(v.titleEn ?? '').trim();
    const titleAr = String(v.titleAr ?? '').trim();
    const threatCategory = (v.threatCategory ?? '') as ThreatCategory;
    const existingControls = String(v.existingControls ?? '').trim();
    if (!titleEn) { toast.danger(isAr ? 'العنوان بالإنجليزية مطلوب' : 'Title (EN) is required'); return; }
    if (!threatCategory) { toast.danger(isAr ? 'فئة التهديد مطلوبة' : 'Threat category is required'); return; }
    // FormModal number-kind fields hold runtime numbers; coerce + bounds-check (1..5).
    const scale = (raw: string, label: string): number | null | undefined => {
      const s = String(raw ?? '').trim();
      if (s === '') return undefined;
      const n = Number(raw);
      if (!Number.isFinite(n) || n < 1 || n > 5) {
        toast.danger(isAr ? `${label}: قيمة بين ١ و٥` : `${label}: must be 1-5`);
        return null;
      }
      return Math.round(n);
    };
    const likelihood = scale(v.likelihood, isAr ? 'الاحتمالية' : 'Likelihood');
    if (likelihood === null) return;
    const impact = scale(v.impact, isAr ? 'الأثر' : 'Impact');
    if (impact === null) return;
    const ce = String(v.controlEffectiveness ?? '').trim() as ControlEffectiveness | '';
    try {
      await createRisk({
        titleEn,
        titleAr: titleAr || undefined,
        threatCategory,
        likelihood: likelihood ?? undefined,
        impact: impact ?? undefined,
        controlEffectiveness: ce || undefined,
        existingControls: existingControls || undefined,
        status: 'DRAFT',
      });
      toast.success(isAr ? 'أُضيفت المخاطرة' : 'BCM risk added');
      setOpenRisk(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل إضافة المخاطرة' : 'Failed to add risk'));
    }
  }

  // ── Add a governance policy (starts DRAFT; version defaults 1.0) ───────
  const govInitial: Record<string, string> = useMemo(() => ({
    policyTitleEn: '', policyTitleAr: '',
    policyVersion: '1.0',
    reviewFrequency: 'ANNUAL',
    nextReviewDate: '',
    scope: '', objective: '',
  }), []);

  const govFields: FieldDef[] = useMemo(() => [
    { key: 'policyTitleEn', kind: 'text', required: true, span: 6, maxLength: 200,
      label: isAr ? 'عنوان السياسة (إنجليزي)' : 'Policy title (EN)' },
    { key: 'policyTitleAr', kind: 'text', span: 6, maxLength: 200,
      label: isAr ? 'عنوان السياسة (عربي)' : 'Policy title (AR)' },
    { key: 'policyVersion', kind: 'text', span: 4, maxLength: 20,
      label: isAr ? 'الإصدار' : 'Version' },
    { key: 'reviewFrequency', kind: 'select', span: 4,
      label: isAr ? 'تكرار المراجعة' : 'Review frequency',
      options: REVIEW_FREQUENCIES.map(f => ({ value: f.value, label: isAr ? f.ar : f.en })) },
    { key: 'nextReviewDate', kind: 'date', span: 4,
      label: isAr ? 'تاريخ المراجعة القادمة' : 'Next review date' },
    { key: 'scope', kind: 'textarea', span: 12, rows: 2,
      label: isAr ? 'النطاق' : 'Scope' },
    { key: 'objective', kind: 'textarea', span: 12, rows: 3,
      label: isAr ? 'الهدف' : 'Objective' },
  ], [isAr]);

  async function submitGov(v: Record<string, string>) {
    const policyTitleEn = String(v.policyTitleEn ?? '').trim();
    const policyTitleAr = String(v.policyTitleAr ?? '').trim();
    const policyVersion = String(v.policyVersion ?? '').trim();
    const reviewFrequency = String(v.reviewFrequency ?? '').trim() as ReviewFrequency | '';
    const nextReviewDate = String(v.nextReviewDate ?? '').trim();
    const scope = String(v.scope ?? '').trim();
    const objective = String(v.objective ?? '').trim();
    if (!policyTitleEn) { toast.danger(isAr ? 'عنوان السياسة بالإنجليزية مطلوب' : 'Policy title (EN) is required'); return; }
    try {
      await createGovernance({
        policyTitleEn,
        policyTitleAr: policyTitleAr || undefined,
        policyVersion: policyVersion || '1.0',
        reviewFrequency: reviewFrequency || undefined,
        nextReviewDate: nextReviewDate || undefined,
        scope: scope || undefined,
        objective: objective || undefined,
        approvalStatus: 'DRAFT',
      });
      toast.success(isAr ? 'أُضيفت السياسة' : 'Policy added');
      setOpenGov(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل إضافة السياسة' : 'Failed to add policy'));
    }
  }

  const tabs: TabItem<TabId>[] = [
    { id: 'cycles',     label: isAr ? 'دورات التقييم'    : 'Cycles' },
    { id: 'governance', label: isAr ? 'الحوكمة'           : 'Governance' },
    { id: 'risks',      label: isAr ? 'مخاطر BCM'         : 'BCM Risks' },
    { id: 'strategies', label: isAr ? 'استراتيجيات'      : 'Strategies' },
    { id: 'calltree',   label: isAr ? 'شجرة الاتصال'     : 'Call Tree' },
  ];

  // ── KPI strip ────
  const activeCycle = cycles.find(c => c.status === 'IN_PROGRESS' || c.status === 'UNDER_REVIEW') ?? cycles[0];
  const openRisks = risks.filter(r => r.status !== 'CLOSED' && r.status !== 'MITIGATED').length;
  const approvedStrategies = strategies.filter(s => s.approvalStatus === 'APPROVED').length;
  const failedCallTrees = contactStats?.failed ?? 0;

  return (
    <Shell
      title={isAr ? 'جاهزية استمرارية الأعمال' : 'BCM Readiness'}
      sub={isAr ? 'الدورات والحوكمة والمخاطر والاستراتيجيات وشجرة الاتصال' : 'Cycles, governance, risks, strategies, and call tree'}>
      <PageHeader
        eyebrow={isAr ? 'الاستمرارية' : 'Resilience'}
        title={isAr ? 'مساحة جاهزية BCM' : 'BCM readiness workspace'} />

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 12, marginBottom: 14 }}>
        <Kpi Icon={CalendarClock}
             label={isAr ? 'الدورة الحالية' : 'Active cycle'}
             value={activeCycle ? String(activeCycle.reportingYear) : '—'}
             tone={activeCycle && cycleStatusTone(activeCycle.status) !== 'neutral'
                   ? (cycleStatusTone(activeCycle.status) as 'info' | 'warning' | 'success')
                   : 'primary'} />
        <Kpi Icon={Shield}
             label={isAr ? 'سياسات معتمدة' : 'Active policies'}
             value={governance.filter(g => g.approvalStatus === 'APPROVED' || g.approvalStatus === 'ACTIVE').length}
             tone="success" />
        <Kpi Icon={AlertTriangle}
             label={isAr ? 'مخاطر BCM مفتوحة' : 'Open BCM risks'}
             value={openRisks}
             tone={openRisks > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Activity}
             label={isAr ? 'استراتيجيات معتمدة' : 'Approved strategies'}
             value={approvedStrategies}
             tone="info" />
        <Kpi Icon={Phone}
             label={isAr ? 'فشل في شجرة الاتصال' : 'Call-tree failures'}
             value={failedCallTrees}
             tone={failedCallTrees > 0 ? 'danger' : 'success'} />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {/* ── Cycles ── */}
      {tab === 'cycles' && (
        <Card title={isAr ? 'دورات التقييم' : 'Assessment cycles'}
          action={<Badge tone="primary">{cycles.length}</Badge>}>
          {loading ? <LoadingRow /> : cycles.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد دورات بعد' : 'No cycles yet'}</InlineEmpty>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'السنة' : 'Year'}</th>
                  <th>{isAr ? 'الاسم' : 'Name'}</th>
                  <th>{isAr ? 'الحالة' : 'Status'}</th>
                  <th>{isAr ? 'BIA%' : 'BIA%'}</th>
                  <th>{isAr ? 'BCP%' : 'BCP%'}</th>
                  <th>{isAr ? 'DRP%' : 'DRP%'}</th>
                  <th>{isAr ? 'فجوات' : 'Gaps'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'إجراءات' : 'Actions'}</th>
                </tr></thead>
                <tbody>
                  {cycles.map(c => (
                    <tr key={c.id}>
                      <td className="mono text-2xs">{c.reportingYear}</td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn}</div>
                        {c.approvedBy && <div className="text-2xs text-muted">{isAr ? 'اعتمدها' : 'approved by'}: {c.approvedBy}</div>}
                      </td>
                      <td><Badge tone={cycleStatusTone(c.status)}>{statusLabel(c.status, lang)}</Badge></td>
                      <td className="mono text-2xs">{c.biaCompletionPercent ?? '—'}</td>
                      <td className="mono text-2xs">{c.bcpCompletionPercent ?? '—'}</td>
                      <td className="mono text-2xs">{c.drpCompletionPercent ?? '—'}</td>
                      <td className="mono text-2xs">
                        {c.openGapCount} {c.criticalGapCount > 0 && <Badge tone="danger">{c.criticalGapCount} crit</Badge>}
                      </td>
                      <td style={{ textAlign: 'end' }}>
                        <CycleActions c={c} onMove={moveCycle} isAr={isAr} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>}
        </Card>
      )}

      {/* ── Governance ── */}
      {tab === 'governance' && (
        <Card title={isAr ? 'سجل الحوكمة' : 'Governance records'}
          action={
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <Badge tone="primary">{governance.length}</Badge>
              <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenGov(true)}>
                {isAr ? 'إضافة سياسة' : 'Add policy'}
              </Button>
            </div>
          }>
          {governance.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد سياسات' : 'No governance records yet'}</InlineEmpty>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'العنوان' : 'Policy'}</th>
                  <th>{isAr ? 'الإصدار' : 'Version'}</th>
                  <th>{isAr ? 'الحالة' : 'Status'}</th>
                  <th>{isAr ? 'تكرار المراجعة' : 'Frequency'}</th>
                  <th>{isAr ? 'المراجعة القادمة' : 'Next review'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'إجراءات' : 'Actions'}</th>
                </tr></thead>
                <tbody>
                  {governance.map(g => (
                    <tr key={g.id}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{isAr && g.policyTitleAr ? g.policyTitleAr : g.policyTitleEn}</div>
                        {g.objective && <div className="text-2xs text-muted">{g.objective.substring(0, 80)}</div>}
                      </td>
                      <td className="mono text-2xs">v{g.policyVersion}</td>
                      <td><Badge tone={governanceStatusTone(g.approvalStatus)}>{g.approvalStatus}</Badge></td>
                      <td className="text-xs">{g.reviewFrequency ?? '—'}</td>
                      <td className="mono text-2xs">{g.nextReviewDate ?? '—'}</td>
                      <td style={{ textAlign: 'end' }}>
                        <GovernanceActions g={g} onMove={moveGovernance} isAr={isAr} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>}
        </Card>
      )}

      {/* ── Risks ── */}
      {tab === 'risks' && (
        <Card title={isAr ? 'سجل مخاطر BCM' : 'BCM risk register'}
          action={
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <Badge tone="primary">{risks.length}</Badge>
              <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenRisk(true)}>
                {isAr ? 'إضافة مخاطرة' : 'Add risk'}
              </Button>
            </div>
          }>
          {risks.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد مخاطر مسجّلة' : 'No BCM risks yet'}</InlineEmpty>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'العنوان' : 'Title'}</th>
                  <th>{isAr ? 'فئة التهديد' : 'Threat category'}</th>
                  <th>{isAr ? 'احتمالية' : 'L'}</th>
                  <th>{isAr ? 'أثر' : 'I'}</th>
                  <th>{isAr ? 'كامن' : 'Inherent'}</th>
                  <th>{isAr ? 'متبقّي' : 'Residual'}</th>
                  <th>{isAr ? 'الحالة' : 'Status'}</th>
                </tr></thead>
                <tbody>
                  {risks.map(r => (
                    <tr key={r.id}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{isAr && r.titleAr ? r.titleAr : r.titleEn}</div>
                        {r.relatedErmRiskId && <Badge tone="info">{isAr ? 'مرتبط بـERM' : 'linked to ERM'}</Badge>}
                      </td>
                      <td className="text-2xs">{r.threatCategory}</td>
                      <td className="mono text-2xs">{r.likelihood ?? '—'}</td>
                      <td className="mono text-2xs">{r.impact ?? '—'}</td>
                      <td className="mono text-2xs">{r.inherentRiskScore ?? '—'}</td>
                      <td>
                        {r.residualRiskScore != null
                          ? <Badge tone={residualToTone(r.residualRiskScore)}>{r.residualRiskScore}</Badge>
                          : <span className="text-muted">—</span>}
                      </td>
                      <td><Badge tone={bcmRiskStatusTone(r.status)}>{statusLabel(r.status, lang)}</Badge></td>
                    </tr>
                  ))}
                </tbody>
              </table>}
        </Card>
      )}

      {/* ── Strategies ── */}
      {tab === 'strategies' && (
        <Card title={isAr ? 'استراتيجيات الاستمرارية' : 'Continuity strategies'}
          action={<Badge tone="primary">{strategies.length}</Badge>}>
          {strategies.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد استراتيجيات' : 'No strategies yet'}</InlineEmpty>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'النوع' : 'Type'}</th>
                  <th>{isAr ? 'RTO (س)' : 'RTO (h)'}</th>
                  <th>{isAr ? 'RPO (س)' : 'RPO (h)'}</th>
                  <th>{isAr ? 'الجدوى' : 'Feasibility'}</th>
                  <th>{isAr ? 'التكلفة' : 'Cost'}</th>
                  <th>{isAr ? 'الاعتماد' : 'Approval'}</th>
                  <th>{isAr ? 'التنفيذ' : 'Implementation'}</th>
                </tr></thead>
                <tbody>
                  {strategies.map(s => (
                    <tr key={s.id}>
                      <td className="text-xs"><Badge tone="primary">{s.selectedStrategy}</Badge></td>
                      <td className="mono text-2xs">{s.rtoRequirementHours ?? '—'}</td>
                      <td className="mono text-2xs">{s.rpoRequirementHours ?? '—'}</td>
                      <td className="text-2xs">{s.feasibility ?? '—'}</td>
                      <td className="mono text-2xs">{s.costEstimate ? Number(s.costEstimate).toLocaleString() : '—'}</td>
                      <td><Badge tone={s.approvalStatus === 'APPROVED' ? 'success' : 'neutral'}>{s.approvalStatus}</Badge></td>
                      <td className="text-2xs">{s.implementationStatus ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>}
        </Card>
      )}

      {/* ── Call Tree ── */}
      {tab === 'calltree' && (
        <div className="dash-grid">
          {contactStats && (
            <div style={{ gridColumn: '1/-1', display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
              <Kpi Icon={Activity} label={isAr ? 'نجاح' : 'Passed'} value={contactStats.passed} tone="success" />
              <Kpi Icon={FileWarning} label={isAr ? 'فشل' : 'Failed'} value={contactStats.failed}
                   tone={contactStats.failed > 0 ? 'danger' : 'success'} />
              <Kpi Icon={Workflow} label={isAr ? 'لم يُجَب' : 'Not reached'} value={contactStats.notReached}
                   tone={contactStats.notReached > 0 ? 'warning' : 'info'} />
            </div>
          )}
          <Card title={isAr ? 'جهات الاتصال' : 'Contacts'}
            action={<Badge tone="primary">{contacts.length}</Badge>}>
            {contacts.length === 0
              ? <InlineEmpty>{isAr ? 'لا توجد جهات اتصال' : 'No contacts yet'}</InlineEmpty>
              : <table className="grc-table">
                  <thead><tr>
                    <th>{isAr ? 'الاسم' : 'Name'}</th>
                    <th>{isAr ? 'الدور' : 'Role'}</th>
                    <th>{isAr ? 'مستوى التصعيد' : 'Escalation'}</th>
                    <th>{isAr ? 'الهاتف' : 'Phone'}</th>
                    <th>{isAr ? 'البريد' : 'Email'}</th>
                    <th>{isAr ? 'آخر اختبار' : 'Last test'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'إجراءات' : 'Actions'}</th>
                  </tr></thead>
                  <tbody>
                    {contacts.map(c => (
                      <tr key={c.id}>
                        <td style={{ fontWeight: 600 }}>{c.name}</td>
                        <td className="text-xs">{c.roleTitle ?? '—'}</td>
                        <td><Badge tone={c.escalationLevel >= 4 ? 'danger' : 'info'}>L{c.escalationLevel}</Badge></td>
                        <td className="mono text-2xs">{c.primaryPhone ?? '—'}</td>
                        <td className="mono text-2xs">{c.email ?? '—'}</td>
                        <td className="text-2xs">
                          {c.lastTestResult
                            ? <Badge tone={c.lastTestResult === 'PASSED' ? 'success' : c.lastTestResult === 'FAILED' ? 'danger' : 'warning'}>
                                {c.lastTestResult}
                              </Badge>
                            : <span className="text-muted">—</span>}
                          {c.lastTestedDate && <span className="text-2xs text-muted" style={{ marginInlineStart: 6 }}>{c.lastTestedDate}</span>}
                        </td>
                        <td style={{ textAlign: 'end' }}>
                          <Button size="sm" variant="ghost" onClick={() => recordTest(c)}>
                            {isAr ? 'سجّل اختباراً' : 'Record test'}
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>}
          </Card>
        </div>
      )}

      <FormModal
        open={openRisk}
        title={isAr ? 'إضافة مخاطرة استمرارية' : 'Add BCM risk'}
        fields={riskFields}
        initial={riskInitial}
        onClose={() => setOpenRisk(false)}
        onSubmit={submitRisk}
        submitLabel={isAr ? 'إضافة المخاطرة' : 'Add risk'}
      />

      <FormModal
        open={openGov}
        title={isAr ? 'إضافة سياسة حوكمة' : 'Add governance policy'}
        fields={govFields}
        initial={govInitial}
        onClose={() => setOpenGov(false)}
        onSubmit={submitGov}
        submitLabel={isAr ? 'إضافة السياسة' : 'Add policy'}
      />
    </Shell>
  );
}

function LoadingRow() {
  const { lang } = useI18n();
  return <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>
    {lang === 'ar' ? 'جاري التحميل…' : 'Loading…'}
  </div>;
}

function CycleActions({ c, onMove, isAr }: { c: BcmAssessmentCycle; onMove: (c: BcmAssessmentCycle, to: CycleStatus) => void; isAr: boolean }) {
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  async function pack() {
    setBusy(true);
    try { await downloadEvidencePackage(c.id); }
    catch (e) {
      // Don't fail silently — without this the download button just appears
      // to do nothing if the backend returns 404/500.
      console.error('evidence pack failed', e);
      toast.danger(isAr ? 'فشل توليد حزمة الأدلة' : 'Evidence package failed: '
        + (e instanceof Error ? e.message : 'unknown error'));
    }
    finally { setBusy(false); }
  }
  return (
    <div style={{ display: 'inline-flex', gap: 4 }}>
      {c.status === 'DRAFT' && (
        <Button size="sm" variant="primary" onClick={() => onMove(c, 'IN_PROGRESS')}>
          {isAr ? 'بدء' : 'Start'}
        </Button>
      )}
      {c.status === 'IN_PROGRESS' && (
        <Button size="sm" variant="ghost" onClick={() => onMove(c, 'UNDER_REVIEW')}>
          {isAr ? 'مراجعة' : 'Review'}
        </Button>
      )}
      {c.status === 'UNDER_REVIEW' && (
        <>
          <Button size="sm" variant="primary" onClick={() => onMove(c, 'APPROVED')}>
            {isAr ? 'اعتماد' : 'Approve'}
          </Button>
          <Button size="sm" variant="ghost" onClick={() => onMove(c, 'IN_PROGRESS')}>
            {isAr ? 'إعادة' : 'Return'}
          </Button>
        </>
      )}
      {c.status === 'APPROVED' && (
        <Button size="sm" variant="ghost" onClick={() => onMove(c, 'ARCHIVED')}>
          {isAr ? 'أرشفة' : 'Archive'}
        </Button>
      )}
      {/* Evidence package available at any stage — auditors may request it
          mid-cycle for an in-flight review. */}
      <Button size="sm" variant="ghost" disabled={busy} onClick={() => void pack()}>
        {busy
          ? (isAr ? '…' : '…')
          : (isAr ? 'حزمة الأدلة' : 'Evidence pack')}
      </Button>
    </div>
  );
}

function GovernanceActions({ g, onMove, isAr }: { g: BcmGovernanceRecord; onMove: (g: BcmGovernanceRecord, to: GovernanceStatus) => void; isAr: boolean }) {
  return (
    <div style={{ display: 'inline-flex', gap: 4 }}>
      {g.approvalStatus === 'DRAFT' && (
        <Button size="sm" variant="primary" onClick={() => onMove(g, 'UNDER_REVIEW')}>
          {isAr ? 'مراجعة' : 'Review'}
        </Button>
      )}
      {g.approvalStatus === 'UNDER_REVIEW' && (
        <>
          <Button size="sm" variant="primary" onClick={() => onMove(g, 'APPROVED')}>
            {isAr ? 'اعتماد' : 'Approve'}
          </Button>
          <Button size="sm" variant="ghost" onClick={() => onMove(g, 'CHANGES_REQUIRED')}>
            {isAr ? 'تعديل' : 'Changes'}
          </Button>
        </>
      )}
      {g.approvalStatus === 'CHANGES_REQUIRED' && (
        <Button size="sm" variant="ghost" onClick={() => onMove(g, 'DRAFT')}>
          {isAr ? 'مسوّدة' : 'To draft'}
        </Button>
      )}
      {g.approvalStatus === 'APPROVED' && (
        <Button size="sm" variant="primary" onClick={() => onMove(g, 'ACTIVE')}>
          {isAr ? 'تنشيط' : 'Activate'}
        </Button>
      )}
    </div>
  );
}
