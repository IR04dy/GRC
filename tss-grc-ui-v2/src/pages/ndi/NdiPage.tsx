import { Fragment, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  Database, FileText, Target, Wrench, AlertCircle, CheckCircle2,
  Plus, ArrowRight, Trash2, Copy, Archive as ArchiveIcon, Upload, Check, Paperclip, Replace,
  Calendar, Pencil,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import {
  listCycles, cyclesAggregate, createCycle, updateCycle, transitionCycle,
  archiveCycle, cloneCycle, deleteCycle, nextStep,
  cycleStatusTone, cycleStatusLabel, readinessTone, readinessLabel,
  type NdiAssessmentCycle, type NdiCycleAggregate,
} from '../../lib/ndiCycles';
import {
  listFramework, frameworkAggregate, markOfficial, markPlaceholder,
  importFrameworkJson, deleteFrameworkItem, createFrameworkItem, updateFrameworkItem,
  componentTone, componentLabel, priorityTone, priorityLabel,
  type NdiFrameworkItem, type NdiFrameworkAggregate, type NdiComponent,
  type NdiPriority,
} from '../../lib/ndiFramework';
import {
  listMaturity, maturityAggregate, startMaturityForCycle, updateMaturity,
  submitMaturity, markUnderReviewMaturity, returnMaturity, approveMaturity,
  reopenMaturity,
  maturityStatusTone, maturityStatusLabel, levelLabel, levelColor,
  type NdiMaturityAssessment, type NdiMaturityAggregate,
} from '../../lib/ndiMaturity';
import {
  listCompliance, complianceAggregate, startComplianceForCycle,
  setComplianceStatus, attachComplianceEvidence, updateCompliance,
  submitCompliance, markUnderReviewCompliance, returnCompliance,
  approveCompliance, reopenCompliance,
  complianceStatusLabel, lifecycleTone, lifecycleLabel,
  type NdiComplianceAssessment, type NdiComplianceAggregate, type NdiComplianceStatus,
} from '../../lib/ndiCompliance';
import {
  listOpex, opexAggregate, updateOpexActual, createOpex, updateOpex,
  kpiStatusTone, kpiStatusLabel, progressFraction,
  type NdiOpexKpi, type NdiOpexAggregate, type NdiKpiFrequency,
} from '../../lib/ndiOpex';
import {
  listDomains, rollupForCycle, createDomain, updateDomain, deleteDomain,
  markDomainOfficial, markDomainPlaceholder,
  ndiScoreTone, ndiScoreLabel,
  type NdiGovernanceDomain, type CycleRollup, type DomainRollup,
} from '../../lib/ndiDomains';
import {
  listEvidence, evidenceAggregate, createEvidence, approveEvidence,
  rejectEvidence, runExpiryCheck, deleteEvidence, supersedeEvidence,
  evidenceStatusTone, evidenceStatusLabel, evidenceTypeLabel,
  classificationTone, classificationLabel, daysUntil,
  type NdiEvidenceItem, type NdiEvidenceAggregate, type NdiEvidenceType,
} from '../../lib/ndiEvidence';
import {
  listGaps, gapsAggregate, createGap, updateGap, deleteGap,
  listCapas, createCapa, updateCapa, deleteCapa,
  severityTone, severityLabel, gapStatusTone, gapStatusLabel,
  capaStatusTone, capaStatusLabel, actionTypeLabel,
  type NdiGap, type NdiCorrectiveAction, type GapAggregate,
  type GapSeverity, type GapStatus, type CapaStatus, type CapaActionType,
} from '../../lib/ndiGaps';
import {
  listPlans, getPlan, createPlan, approvePlan, transitionPlan, deletePlan,
  listItems as listPlanItems, updateItem as updatePlanItem,
  deleteItem as deletePlanItem, recomputeAllPlans,
  planStatusTone, planStatusLabel, priorityTone as planPriorityTone,
  priorityLabelPlan, nextPlanStatuses,
  type NdiImprovementPlan, type EnrichedPlanItem, type NdiPlanStatus, type NdiPlanItemPriority,
} from '../../lib/ndiPlan';
import {
  listSubmissions, preflight as fetchPreflight, createSubmission,
  regenerateSubmission, finalizeSubmission, approveSubmission, submitSubmission,
  acknowledgeSubmission, rejectSubmission, reopenSubmission, deleteSubmission,
  submissionStatusTone, submissionStatusLabel,
  type NdiSubmissionPackage, type Preflight,
} from '../../lib/ndiSubmission';
import {
  listReports, runReport, runAdHocReport, deleteReport, createReport, updateReport,
  downloadReport, downloadAdHocReport,
  reportTypeLabel,
  type NdiReportDefinition, type NdiReportType, type ReportResult,
} from '../../lib/ndiReports';
import {
  getDashboard,
  type DashboardSnapshot, type UpcomingItem,
} from '../../lib/ndiDashboard';
import {
  listSettingsGrouped, setSettingValue, resetSetting, createSetting,
  settingCategoryLabel,
  type NdiSetting, type NdiSettingType,
} from '../../lib/ndiSettings';
import {
  searchAudit, auditAggregate, severityTone as auditSeverityTone,
  type NdiAuditEvent, type AuditAggregate, type AuditSeverity, type AuditAction,
} from '../../lib/ndiAudit';
import '../../styles/pages/ndi.css';

/**
 * SDAIA National Data Index (NDI / نضيء) Management page.
 *
 * <p>Routed at {@code /ndi}. 15-tab shell mirroring the spec menu —
 * slice 1 (this commit) implements the Cycles tab end-to-end; slices
 * 2-14 will fill the remaining tabs incrementally, slice 15 ships
 * tests + QA checklist.
 *
 * <p>Tabs visible in the strip but not yet populated render a
 * placeholder card pointing at the relevant follow-up slice.
 */
type NdiTab =
  | 'dashboard'       // slice 12
  | 'cycles'          // slice 1 (this commit)
  | 'framework'       // slice 2
  | 'maturity'        // slice 3
  | 'compliance'      // slice 4
  | 'opex'            // slice 5
  | 'domains'         // slice 6
  | 'evidence'        // slice 7
  | 'gaps'            // slice 8
  | 'improvement'    // slice 9
  | 'submission'     // slice 10
  | 'reports'        // slice 11
  | 'settings'       // slice 13
  | 'import'         // slice 2 sub-page (import official framework)
  | 'audit';         // slice 14

// Tabs the sidebar/other surfaces can deep-link to via /ndi?tab=NAME.
// Validated against the NdiTab union below; falls back to 'cycles' otherwise
// (matches the prior default).
const VALID_NDI_TABS: NdiTab[] = ['dashboard','cycles','framework','maturity','compliance','opex','domains','evidence','gaps','improvement','submission','reports','settings','import','audit'];

export function NdiPage() {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const [searchParams, setSearchParams] = useSearchParams();
  // Initial tab comes from ?tab= so a sidebar deep-link to e.g.
  // /ndi?tab=evidence lands the DPO directly on the evidence locker.
  const initialTab = (() => {
    const t = searchParams.get('tab') as NdiTab | null;
    return t && VALID_NDI_TABS.includes(t) ? t : 'cycles';
  })();
  const [tab, setTabState] = useState<NdiTab>(initialTab);
  // Reflect tab changes back into the URL so links the user clicks here
  // remain shareable/bookmarkable.
  const setTab = (next: NdiTab) => {
    setTabState(next);
    const sp = new URLSearchParams(searchParams);
    sp.set('tab', next);
    setSearchParams(sp, { replace: true });
  };
  // If the URL changes (back/forward, sidebar re-click) sync the tab.
  useEffect(() => {
    const t = searchParams.get('tab') as NdiTab | null;
    if (t && VALID_NDI_TABS.includes(t) && t !== tab) setTabState(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  return (
    <Shell
      title={isAr ? 'إدارة مؤشر نضيء — المؤشر الوطني للبيانات' : 'SDAIA NDI Management'}
      sub={isAr ? 'المؤشر الوطني للبيانات — جاهزية وتقييم سدايا' : 'National Data Index — SDAIA readiness + assessment'}>
      <PageHeader
        eyebrow={isAr ? 'حوكمة البيانات' : 'Data Governance'}
        title={isAr ? 'إدارة مؤشر نضيء' : 'SDAIA NDI Management'}
        sub={isAr
          ? 'إدارة دورات التقييم والإطار والنضج والامتثال والتميز التشغيلي والأدلة والفجوات وحزمة الرفع.'
          : 'Manage assessment cycles, framework, maturity, compliance, operational excellence, evidence, gaps, and submission package.'}
      />

      {/* Cross-link to the Compliance Calendar — NDI cycle submission
          deadlines (periodEnd) surface there as REPORT_DEADLINE rows
          via the aggregator added in PR #62. Useful for a buyer who's
          looking at the cycles tab and wants to see the whole quarter's
          regulatory cadence at a glance. */}
      <div className="ndi-calendar-banner">
        <Calendar size={14} color="var(--accent-700)" />
        <span className="ndi-calendar-banner__text">
          {isAr
            ? 'استحقاقات دورات نضيء تظهر تلقائياً في تقويم الالتزام مع تنبيهات يوم العمل السعودي.'
            : 'NDI cycle deadlines surface on the Compliance Calendar with Saudi-workday-aware reminders.'}
        </span>
        <Link to="/calendar?types=REPORT_DEADLINE&v=agenda" className="ndi-calendar-banner__link">
          {isAr ? 'افتح التقويم →' : 'Open the calendar →'}
        </Link>
      </div>

      {/* Tab strip — 15 tabs matching the spec's menu order */}
      <div className="ndi-tab-strip">
        {([
          { id: 'dashboard',   label: { en: 'Dashboard',          ar: 'لوحة المعلومات' },       Icon: Database },
          { id: 'cycles',      label: { en: 'Assessment cycles',  ar: 'دورات التقييم' },        Icon: ArrowRight },
          { id: 'framework',   label: { en: 'Framework library',  ar: 'مكتبة الإطار' },         Icon: FileText },
          { id: 'maturity',    label: { en: 'Maturity',           ar: 'النضج' },                Icon: Target },
          { id: 'compliance',  label: { en: 'Compliance',         ar: 'الامتثال' },             Icon: CheckCircle2 },
          { id: 'opex',        label: { en: 'Operational excellence', ar: 'التميز التشغيلي' }, Icon: Target },
          { id: 'domains',     label: { en: 'Governance domains', ar: 'مجالات الحوكمة' },       Icon: Database },
          { id: 'evidence',    label: { en: 'Evidence',           ar: 'الأدلة' },               Icon: FileText },
          { id: 'gaps',        label: { en: 'Gaps + actions',     ar: 'الفجوات والإجراءات' },   Icon: AlertCircle },
          { id: 'improvement', label: { en: 'Improvement plan',   ar: 'خطة التحسين' },          Icon: Target },
          { id: 'submission',  label: { en: 'Submission package', ar: 'حزمة الرفع' },           Icon: FileText },
          { id: 'reports',     label: { en: 'Reports',            ar: 'التقارير' },             Icon: FileText },
          { id: 'settings',    label: { en: 'Settings',           ar: 'الإعدادات' },            Icon: Wrench },
          { id: 'import',      label: { en: 'Import framework',   ar: 'استيراد الإطار' },       Icon: FileText },
          { id: 'audit',       label: { en: 'Audit trail',        ar: 'سجل التدقيق' },          Icon: FileText },
        ] as { id: NdiTab; label: { en: string; ar: string }; Icon: typeof Database }[]).map(td => (
          <button key={td.id} onClick={() => setTab(td.id)}
            className={`ndi-tab-btn${tab === td.id ? ' is-active' : ''}`}>
            <td.Icon size={13} />
            {isAr ? td.label.ar : td.label.en}
          </button>
        ))}
      </div>

      {tab === 'cycles'     && <CyclesTab />}
      {tab === 'framework'  && <FrameworkTab />}
      {tab === 'maturity'   && <MaturityTab />}
      {tab === 'compliance' && <ComplianceTab />}
      {tab === 'opex'       && <OpexTab />}
      {tab === 'domains'    && <DomainsTab />}
      {tab === 'evidence'   && <EvidenceTab />}
      {tab === 'gaps'       && <GapsTab />}
      {tab === 'improvement' && <ImprovementTab />}
      {tab === 'submission' && <SubmissionTab />}
      {tab === 'reports'    && <ReportsTab />}
      {tab === 'dashboard'  && <DashboardTab />}
      {tab === 'settings'   && <SettingsTab />}
      {tab === 'audit'      && <AuditTab />}
      {tab === 'import'     && <FrameworkTab />}
      {tab !== 'cycles' && tab !== 'framework' && tab !== 'maturity' && tab !== 'compliance' && tab !== 'opex' && tab !== 'domains' && tab !== 'evidence' && tab !== 'gaps' && tab !== 'improvement' && tab !== 'submission' && tab !== 'reports' && tab !== 'dashboard' && tab !== 'settings' && tab !== 'audit' && tab !== 'import'
        && <NotYetShipped tab={tab} isAr={isAr} t={t} />}
    </Shell>
  );
}

// ─── Cycles tab (slice 1 — end-to-end implementation) ──────────────────

function CyclesTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<NdiAssessmentCycle[]>([]);
  const [agg, setAgg] = useState<NdiCycleAggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [openNew, setOpenNew] = useState(false);
  const [editCycle, setEditCycle] = useState<NdiAssessmentCycle | null>(null);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — render whichever loaded; aggregate failure shouldn't blank the list.
    const [pg, a] = await Promise.all([
      listCycles({ size: 100 }).catch(() => ({ content: [] as NdiAssessmentCycle[], totalElements: 0 })),
      cyclesAggregate().catch(() => null as NdiCycleAggregate | null),
    ]);
    setItems(pg.content);
    setAgg(a);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  // Edit modal seed — mapped from the selected cycle, keyed on [editCycle] so
  // FormModal's reset effect re-runs when a different row is opened. Reuses the
  // same field keys as the create form (newFields).
  const editInitial = useMemo<Record<string, unknown>>(() => ({
    cycleNameEn:           editCycle?.cycleNameEn ?? '',
    cycleNameAr:           editCycle?.cycleNameAr ?? '',
    reportingYear:         editCycle?.reportingYear ?? new Date().getFullYear(),
    entityNameEn:          editCycle?.entityNameEn ?? '',
    entityNameAr:          editCycle?.entityNameAr ?? '',
    dataOfficeOwnerName:   editCycle?.dataOfficeOwnerName ?? '',
    assessmentManagerName: editCycle?.assessmentManagerName ?? '',
    scope:                 editCycle?.scope ?? '',
  }), [editCycle]);

  async function onUpdate(values: Record<string, unknown>) {
    if (!editCycle?.id) return;
    try {
      await updateCycle(editCycle.id, {
        cycleNameEn:           String(values.cycleNameEn ?? '').trim(),
        cycleNameAr:           String(values.cycleNameAr ?? '').trim() || undefined,
        reportingYear:         Number(values.reportingYear ?? editCycle.reportingYear),
        scope:                 String(values.scope ?? '').trim() || undefined,
        entityNameEn:          String(values.entityNameEn ?? '').trim() || undefined,
        entityNameAr:          String(values.entityNameAr ?? '').trim() || undefined,
        dataOfficeOwnerName:   String(values.dataOfficeOwnerName ?? '').trim() || undefined,
        assessmentManagerName: String(values.assessmentManagerName ?? '').trim() || undefined,
      });
      toast.success(isAr ? 'حُدّثت الدورة' : 'Cycle updated');
      setEditCycle(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function onCreate(values: Record<string, unknown>) {
    try {
      await createCycle({
        cycleNameEn:   String(values.cycleNameEn ?? '').trim(),
        cycleNameAr:   String(values.cycleNameAr ?? '').trim() || undefined,
        reportingYear: Number(values.reportingYear ?? new Date().getFullYear()),
        scope:         String(values.scope ?? '').trim() || undefined,
        entityNameEn:  String(values.entityNameEn ?? '').trim() || undefined,
        entityNameAr:  String(values.entityNameAr ?? '').trim() || undefined,
        dataOfficeOwnerName:    String(values.dataOfficeOwnerName ?? '').trim() || undefined,
        assessmentManagerName:  String(values.assessmentManagerName ?? '').trim() || undefined,
        status: 'DRAFT',
      });
      toast.success(isAr ? 'أُنشئت الدورة' : 'Cycle created');
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function onTransition(c: NdiAssessmentCycle) {
    const next = nextStep(c.status);
    if (!next) {
      toast.info(isAr ? 'لا توجد خطوة تالية متاحة' : 'No further forward step available');
      return;
    }
    try {
      await transitionCycle(c.id!, next);
      toast.success(isAr ? `الحالة الآن: ${cycleStatusLabel(next, 'ar')}` : `Now: ${cycleStatusLabel(next, 'en')}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function onArchive(c: NdiAssessmentCycle) {
    const ok = await dialog.confirm({
      title: isAr ? 'أرشفة الدورة؟' : 'Archive cycle?',
      body: isAr ? 'الأرشفة نهائية. لن تستطيع إعادتها بعد ذلك.' : 'Archive is terminal. You won\'t be able to reopen it.',
      destructive: true, submitLabel: isAr ? 'أرشفة' : 'Archive',
    });
    if (!ok) return;
    try { await archiveCycle(c.id!); toast.info(isAr ? 'تمت الأرشفة' : 'Archived'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onClone(c: NdiAssessmentCycle) {
    const ans = await dialog.prompt({
      title: isAr ? 'استنساخ الدورة' : 'Clone cycle',
      label: isAr ? 'سنة الدورة الجديدة' : 'New reporting year',
      type: 'number', required: true, min: 2020, max: 2100,
      initial: String((c.reportingYear ?? new Date().getFullYear()) + 1),
    });
    if (!ans) return;
    try {
      await cloneCycle(c.id!, Number(ans));
      toast.success(isAr ? 'أُنشئت الدورة المستنسَخة كمسودّة' : 'Cloned cycle created as DRAFT');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function onDelete(c: NdiAssessmentCycle) {
    const ok = await dialog.confirm({
      title: isAr ? 'حذف الدورة؟' : 'Delete cycle?',
      body: isAr ? 'حذف ناعم — يمكن استرجاعها عبر قاعدة البيانات.' : 'Soft delete — can be restored from the database.',
      destructive: true, submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try { await deleteCycle(c.id!); toast.info(isAr ? 'تم الحذف' : 'Deleted'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const newFields: FieldDef[] = useMemo(() => [
    { kind: 'text',     key: 'cycleNameEn',           required: true, span: 6,
      label: isAr ? 'اسم الدورة (إنجليزي) *' : 'Cycle name (English) *',
      placeholder: 'NDI Readiness Cycle 2026' },
    { kind: 'text',     key: 'cycleNameAr',           span: 6,
      label: isAr ? 'اسم الدورة (عربي)' : 'Cycle name (Arabic)',
      placeholder: 'دورة جاهزية نضيء 2026' },
    { kind: 'number',   key: 'reportingYear',         required: true, span: 3, min: 2020, max: 2100,
      label: isAr ? 'سنة الإبلاغ *' : 'Reporting year *' },
    { kind: 'text',     key: 'entityNameEn',          span: 6,
      label: isAr ? 'اسم الجهة (إنجليزي)' : 'Entity name (English)' },
    { kind: 'text',     key: 'entityNameAr',          span: 6,
      label: isAr ? 'اسم الجهة (عربي)' : 'Entity name (Arabic)' },
    { kind: 'text',     key: 'dataOfficeOwnerName',   span: 6,
      label: isAr ? 'مالك مكتب البيانات' : 'Data Office owner',
      placeholder: isAr ? 'مثال: سارة الزهراني' : 'e.g. Sara Al-Zahrani' },
    { kind: 'text',     key: 'assessmentManagerName', span: 6,
      label: isAr ? 'مدير التقييم' : 'Assessment manager',
      placeholder: isAr ? 'مثال: خالد العتيبي' : 'e.g. Khalid Al-Otaibi' },
    { kind: 'textarea', key: 'scope', rows: 3, span: 12,
      label: isAr ? 'نطاق التقييم' : 'Assessment scope' },
  ], [isAr]);

  return (
    <div className="ndi-stack">
      <div className="kpi-grid">
        <Kpi Icon={Database}      label={isAr ? 'إجمالي الدورات' : 'Total cycles'}      value={agg?.total ?? 0}  tone="primary" to="/ndi" />
        <Kpi Icon={ArrowRight}    label={isAr ? 'دورات نشطة'    : 'Active cycles'}     value={agg?.active ?? 0} tone="info" to="/ndi" />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'مُعتمَدة'        : 'Approved'}          value={agg?.byStatus.APPROVED ?? 0}  tone="success" to="/ndi" />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'مُقدَّمة'         : 'Submitted'}         value={agg?.byStatus.SUBMITTED ?? 0} tone="success" to="/ndi" />
        <Kpi Icon={AlertCircle}   label={isAr ? 'معالجة فجوات' : 'Gap remediation'}   value={agg?.byStatus.GAP_REMEDIATION ?? 0}  tone="warning" to="/ndi" />
      </div>

      <Card title={isAr ? 'دورات تقييم نضيء' : 'NDI assessment cycles'} Icon={Database}
        action={
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenNew(true)}>
            {isAr ? 'دورة جديدة' : 'New cycle'}
          </Button>
        }>
        <div className="ndi-card-lede">
          {isAr
            ? 'مصفوفة دورات جاهزية مؤشر نضيء. كل دورة تمر بـ 12 خطوة من المسودّة إلى التقديم لسدايا، مع 13 حالة لحظية مرصودة. الإطار والنضج والامتثال والأدلة سترتبط بهذه الدورات لاحقاً عبر الشرائح 2-14.'
            : 'NDI readiness cycles. Each cycle moves through 12 forward steps from Draft to Submitted, with 13 distinct statuses tracked. Framework / maturity / compliance / evidence will link to these cycles via slices 2-14.'}
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : items.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد دورات بعد — ابدأ بإنشاء واحدة' : 'No cycles yet — create one to start'}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'الاسم' : 'Name'}</th>
                    <th>{isAr ? 'السنة' : 'Year'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th>{isAr ? 'النتيجة الإجمالية' : 'Overall'}</th>
                    <th>{isAr ? 'الجاهزية' : 'Readiness'}</th>
                    <th>{isAr ? 'المالك' : 'Owner'}</th>
                    <th className="ndi-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map(c => {
                    const next = nextStep(c.status);
                    const isTerminal = c.status === 'CLOSED' || c.status === 'ARCHIVED';
                    return (
                      <tr key={c.id}>
                        <td className="mono text-2xs">{c.cycleCode ?? `#${c.id}`}</td>
                        <td>
                          <div className="ndi-cell-title">
                            {isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn}
                          </div>
                          {c.entityNameEn && (
                            <div className="text-2xs text-muted ndi-cell-sub">
                              {isAr && c.entityNameAr ? c.entityNameAr : c.entityNameEn}
                            </div>
                          )}
                        </td>
                        <td className="mono">{c.reportingYear}</td>
                        <td><Badge tone={cycleStatusTone(c.status)}>{cycleStatusLabel(c.status, isAr ? 'ar' : 'en')}</Badge></td>
                        <td className="mono">{c.overallScore != null ? `${Number(c.overallScore).toFixed(0)} / 100` : '—'}</td>
                        <td><Badge tone={readinessTone(c.submissionReadinessStatus)}>{readinessLabel(c.submissionReadinessStatus, isAr ? 'ar' : 'en')}</Badge></td>
                        <td className="text-xs">{c.dataOfficeOwnerName ?? '—'}</td>
                        <td className="ndi-text-end">
                          <div className="ndi-actions-row">
                            {next && !isTerminal && (
                              <Button size="sm" variant="secondary" Icon={ArrowRight}
                                onClick={() => onTransition(c)}
                                title={isAr ? `التالي: ${cycleStatusLabel(next, 'ar')}` : `Next: ${cycleStatusLabel(next, 'en')}`}>
                                {isAr ? 'تقدّم' : 'Advance'}
                              </Button>
                            )}
                            {!isTerminal && (
                              <Button size="sm" variant="ghost" Icon={Pencil}
                                title={isAr ? 'تعديل' : 'Edit'}
                                onClick={() => setEditCycle(c)} />
                            )}
                            <Button size="sm" variant="ghost" Icon={Copy}
                              title={isAr ? 'استنساخ' : 'Clone'}
                              onClick={() => onClone(c)} />
                            {c.status !== 'ARCHIVED' && (
                              <Button size="sm" variant="ghost" Icon={ArchiveIcon}
                                title={isAr ? 'أرشفة' : 'Archive'}
                                onClick={() => onArchive(c)} />
                            )}
                            <Button size="sm" variant="ghost" Icon={Trash2}
                              title={isAr ? 'حذف' : 'Delete'}
                              onClick={() => onDelete(c)} />
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
      </Card>

      <FormModal
        open={openNew}
        title={isAr ? 'دورة جاهزية نضيء جديدة' : 'New NDI assessment cycle'}
        fields={newFields}
        initial={{ reportingYear: new Date().getFullYear() }}
        size="lg"
        submitLabel={isAr ? 'إنشاء' : 'Create'}
        onClose={() => setOpenNew(false)}
        onSubmit={onCreate}
      />

      <FormModal
        open={!!editCycle}
        title={isAr ? 'تعديل دورة التقييم' : 'Edit assessment cycle'}
        fields={newFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditCycle(null)}
        onSubmit={onUpdate}
      />
    </div>
  );
}

// ─── Framework Library tab (slice 2 — V67) ─────────────────────────

// "Define requirement" form — every field a string except the numeric
// weight (kind:'number' yields a runtime number). The server stamps org,
// keeps is_placeholder=true (manual entries stay placeholders until an
// official SDAIA/NDI library import flips them), and defaults the rest.
interface FrameworkForm {
  requirementCode: string;
  component: string;
  domain: string;
  subdomain: string;
  titleEn: string;
  titleAr: string;
  priority: string;
  mandatory: string;        // 'YES' | 'NO'
  reviewFrequency: string;
  status: string;
  weight: number;
  scoringMethod: string;
  ownerRole: string;
  descriptionEn: string;
  descriptionAr: string;
  questionEn: string;
  questionAr: string;
  evidenceGuidanceEn: string;
  evidenceGuidanceAr: string;
  relatedNdmoControl: string;
  relatedPdplRequirement: string;
  relatedCyberControl: string;
  relatedIsoMapping: string;
  effectiveDate: string;
  notes: string;
}

function FrameworkTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<NdiFrameworkItem[]>([]);
  const [agg, setAgg] = useState<NdiFrameworkAggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [filterComponent, setFilterComponent] = useState<NdiComponent | 'ALL'>('ALL');
  const [importOpen, setImportOpen] = useState(false);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — framework list and aggregate load independently.
    const [pg, a] = await Promise.all([
      listFramework({ size: 500 }).catch(() => ({ content: [] as NdiFrameworkItem[], totalElements: 0 })),
      frameworkAggregate().catch(() => null as NdiFrameworkAggregate | null),
    ]);
    setItems(pg.content);
    setAgg(a);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  const filtered = useMemo(() => filterComponent === 'ALL'
    ? items
    : items.filter(i => i.component === filterComponent), [items, filterComponent]);

  async function onMarkOfficial(it: NdiFrameworkItem) {
    try { await markOfficial(it.id!); toast.success(isAr ? 'علّمت كرسمي' : 'Marked as official'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onMarkPlaceholder(it: NdiFrameworkItem) {
    try { await markPlaceholder(it.id!); toast.info(isAr ? 'أُعيد وضع المؤقت' : 'Marked as placeholder'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onDelete(it: NdiFrameworkItem) {
    const ok = await dialog.confirm({
      title: isAr ? 'حذف العنصر؟' : 'Delete framework item?',
      body: isAr ? 'حذف ناعم — يمكن استرجاعه من قاعدة البيانات.' : 'Soft delete — can be restored from the database.',
      destructive: true, submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try { await deleteFrameworkItem(it.id!); toast.info(isAr ? 'تم الحذف' : 'Deleted'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onImport(values: Record<string, unknown>) {
    const raw = String(values.json ?? '').trim();
    if (!raw) {
      toast.danger(isAr ? 'الصق JSON' : 'Paste JSON');
      return;
    }
    let parsed: unknown;
    try { parsed = JSON.parse(raw); }
    catch { toast.danger(isAr ? 'JSON غير صالح' : 'Invalid JSON'); return; }

    const items = Array.isArray(parsed)
      ? parsed
      : (parsed as { items?: unknown }).items;
    if (!Array.isArray(items)) {
      toast.danger(isAr ? 'يجب أن يكون JSON مصفوفة أو {items: [...]}'
                        : 'JSON must be an array or {items: [...]}');
      return;
    }

    try {
      const summary = await importFrameworkJson(items as Partial<NdiFrameworkItem>[]);
      const msg = isAr
        ? `أُدرج ${summary.inserted} • حُدّث ${summary.updated} • فشل ${summary.failed}`
        : `${summary.inserted} inserted · ${summary.updated} updated · ${summary.failed} failed`;
      if (summary.failed > 0) toast.warning(msg);
      else toast.success(msg);
      setImportOpen(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Import failed');
    }
  }

  const importFields: FieldDef[] = [
    { kind: 'textarea', key: 'json', span: 12, rows: 12,
      label: isAr ? 'JSON للاستيراد' : 'JSON to import' },
  ];

  // ── Define requirement (createFrameworkItem) ──────────────────────
  const [openNew, setOpenNew] = useState(false);
  // ── Edit requirement (updateFrameworkItem) ────────────────────────
  const [editItem, setEditItem] = useState<NdiFrameworkItem | null>(null);
  const L = isAr ? 'ar' : 'en';

  const componentOpts = (['COMPLIANCE', 'MATURITY', 'OPERATIONAL_EXCELLENCE'] as NdiComponent[])
    .map(c => ({ value: c, label: componentLabel(c, L) }));
  const priorityOpts = (['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as NdiPriority[])
    .map(p => ({ value: p, label: priorityLabel(p, L) }));
  const mandatoryOpts = [
    { value: 'NO',  label: isAr ? 'اختياري' : 'Optional' },
    { value: 'YES', label: isAr ? 'إلزامي'  : 'Mandatory' },
  ];
  const freqOpts = [
    { value: '',            label: '—' },
    { value: 'ANNUAL',      label: isAr ? 'سنوي'      : 'Annual' },
    { value: 'SEMI_ANNUAL', label: isAr ? 'نصف سنوي'  : 'Semi-annual' },
    { value: 'QUARTERLY',   label: isAr ? 'ربع سنوي'  : 'Quarterly' },
    { value: 'MONTHLY',     label: isAr ? 'شهري'      : 'Monthly' },
  ];
  const scoringOpts = [
    { value: '',          label: '—' },
    { value: 'LEVEL_0_5', label: isAr ? 'مستوى 0–5'   : 'Level 0–5' },
    { value: 'BINARY',    label: isAr ? 'ثنائي'        : 'Binary' },
    { value: 'PERCENT',   label: isAr ? 'نسبة مئوية'   : 'Percentage' },
    { value: 'RUBRIC',    label: isAr ? 'مرجع تقييم'   : 'Rubric' },
  ];
  const statusOpts = [
    { value: 'DRAFT',   label: isAr ? 'مسودة'   : 'Draft' },
    { value: 'ACTIVE',  label: isAr ? 'نشط'     : 'Active' },
    { value: 'RETIRED', label: isAr ? 'متقاعد'  : 'Retired' },
  ];

  const reqFields: FieldDef[] = [
    { kind: 'text', key: 'requirementCode', span: 6, required: true, maxLength: 40,
      label: isAr ? 'كود المتطلب' : 'Requirement code', placeholder: 'NDI-COMP-001' },
    { kind: 'select', key: 'component', span: 6, required: true,
      label: isAr ? 'المكوّن' : 'Component', options: componentOpts },
    { kind: 'text', key: 'domain', span: 6, required: true, maxLength: 80,
      label: isAr ? 'المجال' : 'Domain', placeholder: isAr ? 'حوكمة البيانات' : 'Data Governance' },
    { kind: 'text', key: 'subdomain', span: 6, maxLength: 80,
      label: isAr ? 'المجال الفرعي' : 'Subdomain' },
    { kind: 'text', key: 'titleEn', span: 6, required: true, maxLength: 255,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 6, maxLength: 255,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'select', key: 'priority', span: 3,
      label: isAr ? 'الأولوية' : 'Priority', options: priorityOpts },
    { kind: 'select', key: 'mandatory', span: 3,
      label: isAr ? 'الإلزام' : 'Mandatory', options: mandatoryOpts },
    { kind: 'select', key: 'reviewFrequency', span: 3,
      label: isAr ? 'دورية المراجعة' : 'Review frequency', options: freqOpts },
    { kind: 'select', key: 'status', span: 3,
      label: isAr ? 'الحالة' : 'Status', options: statusOpts },
    { kind: 'number', key: 'weight', span: 4, min: 0, max: 999.99,
      label: isAr ? 'الوزن' : 'Weight' },
    { kind: 'select', key: 'scoringMethod', span: 4,
      label: isAr ? 'طريقة التقييم' : 'Scoring method', options: scoringOpts },
    { kind: 'text', key: 'ownerRole', span: 4, maxLength: 80,
      label: isAr ? 'الدور المالك' : 'Owner role' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
    { kind: 'textarea', key: 'descriptionAr', span: 12, rows: 2,
      label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)' },
    { kind: 'textarea', key: 'questionEn', span: 12, rows: 2,
      label: isAr ? 'سؤال التقييم (إنجليزي)' : 'Assessment question (English)' },
    { kind: 'textarea', key: 'questionAr', span: 12, rows: 2,
      label: isAr ? 'سؤال التقييم (عربي)' : 'Assessment question (Arabic)' },
    { kind: 'textarea', key: 'evidenceGuidanceEn', span: 12, rows: 2,
      label: isAr ? 'إرشاد الأدلة (إنجليزي)' : 'Evidence guidance (English)' },
    { kind: 'textarea', key: 'evidenceGuidanceAr', span: 12, rows: 2,
      label: isAr ? 'إرشاد الأدلة (عربي)' : 'Evidence guidance (Arabic)' },
    { kind: 'text', key: 'relatedNdmoControl', span: 6, maxLength: 120,
      label: isAr ? 'ضابط ندمو ذو صلة' : 'Related NDMO control' },
    { kind: 'text', key: 'relatedPdplRequirement', span: 6, maxLength: 120,
      label: isAr ? 'متطلب حماية البيانات' : 'Related PDPL requirement' },
    { kind: 'text', key: 'relatedCyberControl', span: 6, maxLength: 120,
      label: isAr ? 'ضابط سيبراني ذو صلة' : 'Related cyber control' },
    { kind: 'text', key: 'relatedIsoMapping', span: 6, maxLength: 120,
      label: isAr ? 'مطابقة ISO' : 'Related ISO mapping' },
    { kind: 'date', key: 'effectiveDate', span: 6,
      label: isAr ? 'تاريخ السريان' : 'Effective date' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2,
      label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  const newInitial = useMemo<FrameworkForm>(() => ({
    requirementCode: '', component: 'COMPLIANCE', domain: '', subdomain: '',
    titleEn: '', titleAr: '',
    priority: 'MEDIUM', mandatory: 'NO', reviewFrequency: '', status: 'ACTIVE',
    weight: 1, scoringMethod: '', ownerRole: '',
    descriptionEn: '', descriptionAr: '', questionEn: '', questionAr: '',
    evidenceGuidanceEn: '', evidenceGuidanceAr: '',
    relatedNdmoControl: '', relatedPdplRequirement: '', relatedCyberControl: '', relatedIsoMapping: '',
    effectiveDate: '', notes: '',
  }), []);

  async function onCreate(v: FrameworkForm) {
    const u = (s: string) => { const t = String(s ?? '').trim(); return t === '' ? undefined : t; };
    const code = String(v.requirementCode ?? '').trim();
    const titleEn = String(v.titleEn ?? '').trim();
    const domain = String(v.domain ?? '').trim();
    const component = String(v.component ?? '').trim();
    if (!code)      { toast.danger(isAr ? 'كود المتطلب مطلوب' : 'Requirement code is required'); return; }
    if (!component) { toast.danger(isAr ? 'المكوّن مطلوب' : 'Component is required'); return; }
    if (!domain)    { toast.danger(isAr ? 'المجال مطلوب' : 'Domain is required'); return; }
    if (!titleEn)   { toast.danger(isAr ? 'العنوان (إنجليزي) مطلوب' : 'English title is required'); return; }
    const weight = Number.isFinite(v.weight) && v.weight > 0 ? v.weight : 1;

    const body: Partial<NdiFrameworkItem> = {
      requirementCode: code,
      component: component as NdiComponent,
      domain,
      subdomain: u(v.subdomain),
      titleEn,
      titleAr: u(v.titleAr),
      priority: (v.priority || 'MEDIUM') as NdiPriority,
      mandatory: v.mandatory === 'YES',
      reviewFrequency: u(v.reviewFrequency) as NdiFrameworkItem['reviewFrequency'],
      status: (v.status || 'ACTIVE') as NdiFrameworkItem['status'],
      weight,
      scoringMethod: u(v.scoringMethod) as NdiFrameworkItem['scoringMethod'],
      ownerRole: u(v.ownerRole),
      descriptionEn: u(v.descriptionEn),
      descriptionAr: u(v.descriptionAr),
      questionEn: u(v.questionEn),
      questionAr: u(v.questionAr),
      evidenceGuidanceEn: u(v.evidenceGuidanceEn),
      evidenceGuidanceAr: u(v.evidenceGuidanceAr),
      relatedNdmoControl: u(v.relatedNdmoControl),
      relatedPdplRequirement: u(v.relatedPdplRequirement),
      relatedCyberControl: u(v.relatedCyberControl),
      relatedIsoMapping: u(v.relatedIsoMapping),
      effectiveDate: u(v.effectiveDate),
      notes: u(v.notes),
    };
    try {
      await createFrameworkItem(body);
      toast.success(isAr ? 'أُضيف المتطلب' : 'Requirement added');
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Create failed');
    }
  }

  // Edit modal pre-fills from the selected row. Keyed on item id so the
  // memoized identity changes whenever a different row is opened, which
  // re-runs FormModal's reset effect against the new initial.
  const editInitial = useMemo<FrameworkForm>(() => ({
    requirementCode: editItem?.requirementCode ?? '',
    component: editItem?.component ?? 'COMPLIANCE',
    domain: editItem?.domain ?? '',
    subdomain: editItem?.subdomain ?? '',
    titleEn: editItem?.titleEn ?? '',
    titleAr: editItem?.titleAr ?? '',
    priority: editItem?.priority ?? 'MEDIUM',
    mandatory: editItem?.mandatory ? 'YES' : 'NO',
    reviewFrequency: editItem?.reviewFrequency ?? '',
    status: editItem?.status ?? 'ACTIVE',
    weight: editItem?.weight ?? 1,
    scoringMethod: editItem?.scoringMethod ?? '',
    ownerRole: editItem?.ownerRole ?? '',
    descriptionEn: editItem?.descriptionEn ?? '',
    descriptionAr: editItem?.descriptionAr ?? '',
    questionEn: editItem?.questionEn ?? '',
    questionAr: editItem?.questionAr ?? '',
    evidenceGuidanceEn: editItem?.evidenceGuidanceEn ?? '',
    evidenceGuidanceAr: editItem?.evidenceGuidanceAr ?? '',
    relatedNdmoControl: editItem?.relatedNdmoControl ?? '',
    relatedPdplRequirement: editItem?.relatedPdplRequirement ?? '',
    relatedCyberControl: editItem?.relatedCyberControl ?? '',
    relatedIsoMapping: editItem?.relatedIsoMapping ?? '',
    effectiveDate: editItem?.effectiveDate ?? '',
    notes: editItem?.notes ?? '',
  }), [editItem]);

  async function onUpdate(v: FrameworkForm) {
    if (!editItem?.id) return;
    const u = (s: string) => { const t = String(s ?? '').trim(); return t === '' ? undefined : t; };
    const code = String(v.requirementCode ?? '').trim();
    const titleEn = String(v.titleEn ?? '').trim();
    const domain = String(v.domain ?? '').trim();
    const component = String(v.component ?? '').trim();
    if (!code)      { toast.danger(isAr ? 'كود المتطلب مطلوب' : 'Requirement code is required'); return; }
    if (!component) { toast.danger(isAr ? 'المكوّن مطلوب' : 'Component is required'); return; }
    if (!domain)    { toast.danger(isAr ? 'المجال مطلوب' : 'Domain is required'); return; }
    if (!titleEn)   { toast.danger(isAr ? 'العنوان (إنجليزي) مطلوب' : 'English title is required'); return; }
    const weight = Number.isFinite(v.weight) && v.weight > 0 ? v.weight : 1;

    const body: Partial<NdiFrameworkItem> = {
      requirementCode: code,
      component: component as NdiComponent,
      domain,
      subdomain: u(v.subdomain),
      titleEn,
      titleAr: u(v.titleAr),
      priority: (v.priority || 'MEDIUM') as NdiPriority,
      mandatory: v.mandatory === 'YES',
      reviewFrequency: u(v.reviewFrequency) as NdiFrameworkItem['reviewFrequency'],
      status: (v.status || 'ACTIVE') as NdiFrameworkItem['status'],
      weight,
      scoringMethod: u(v.scoringMethod) as NdiFrameworkItem['scoringMethod'],
      ownerRole: u(v.ownerRole),
      descriptionEn: u(v.descriptionEn),
      descriptionAr: u(v.descriptionAr),
      questionEn: u(v.questionEn),
      questionAr: u(v.questionAr),
      evidenceGuidanceEn: u(v.evidenceGuidanceEn),
      evidenceGuidanceAr: u(v.evidenceGuidanceAr),
      relatedNdmoControl: u(v.relatedNdmoControl),
      relatedPdplRequirement: u(v.relatedPdplRequirement),
      relatedCyberControl: u(v.relatedCyberControl),
      relatedIsoMapping: u(v.relatedIsoMapping),
      effectiveDate: u(v.effectiveDate),
      notes: u(v.notes),
    };
    try {
      await updateFrameworkItem(editItem.id, body);
      toast.success(isAr ? 'حُدّث المتطلب' : 'Requirement updated');
      setEditItem(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  return (
    <div className="ndi-stack">
      <div className="kpi-grid">
        <Kpi Icon={FileText}     label={isAr ? 'إجمالي البنود' : 'Total items'}   value={agg?.total ?? 0}            tone="primary"
             onClick={() => setFilterComponent('ALL')} active={filterComponent === 'ALL'} />
        <Kpi Icon={CheckCircle2} label={isAr ? 'بنود رسمية'   : 'Official'}      value={agg?.officialCount ?? 0}    tone="success" to="/ndi" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'بنود مؤقتة'   : 'Placeholder'}   value={agg?.placeholderCount ?? 0} tone="warning" to="/ndi" />
        <Kpi Icon={Target}       label={isAr ? 'إلزامية'      : 'Mandatory'}     value={agg?.mandatoryCount ?? 0}   tone="danger" to="/ndi" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'الامتثال'    : 'Compliance'}    value={agg?.byComponent.COMPLIANCE ?? 0}             tone="info"
             onClick={() => setFilterComponent('COMPLIANCE')} active={filterComponent === 'COMPLIANCE'} />
        <Kpi Icon={Target}       label={isAr ? 'النضج'        : 'Maturity'}      value={agg?.byComponent.MATURITY ?? 0}               tone="primary"
             onClick={() => setFilterComponent('MATURITY')} active={filterComponent === 'MATURITY'} />
        <Kpi Icon={Target}       label={isAr ? 'تميز تشغيلي' : 'Op. excellence'} value={agg?.byComponent.OPERATIONAL_EXCELLENCE ?? 0} tone="success"
             onClick={() => setFilterComponent('OPERATIONAL_EXCELLENCE')} active={filterComponent === 'OPERATIONAL_EXCELLENCE'} />
      </div>

      {agg && agg.placeholderCount > 0 && (
        <div className="ndi-warning-banner">
          <AlertCircle size={20} />
          <div className="ndi-warning-banner__body">
            <strong>{isAr ? 'تنبيه:' : 'Notice:'}</strong>{' '}
            {isAr
              ? `${agg.placeholderCount} من أصل ${agg.total} عنصر مؤقت — يتطلب استيراد مكتبة الضوابط الرسمية من سدايا/نضيء قبل التقديم الرسمي.`
              : `${agg.placeholderCount} of ${agg.total} items are placeholders — requires official SDAIA/NDI control library import before formal submission.`}
          </div>
        </div>
      )}

      <Card title={isAr ? 'مكتبة إطار نضيء' : 'NDI framework library'} Icon={FileText}
        action={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenNew(true)}>
              {isAr ? 'تعريف متطلب' : 'Define requirement'}
            </Button>
            <Button size="sm" variant="secondary" Icon={Upload} onClick={() => setImportOpen(true)}>
              {isAr ? 'استيراد JSON' : 'Import JSON'}
            </Button>
          </div>
        }>
        <div className="ndi-filter-row">
          {(['ALL', 'COMPLIANCE', 'MATURITY', 'OPERATIONAL_EXCELLENCE'] as const).map(c => (
            <button key={c} onClick={() => setFilterComponent(c)}
              className={`ndi-filter-btn${filterComponent === c ? ' is-active' : ''}`}>
              {c === 'ALL' ? (isAr ? 'الكل' : 'All') : componentLabel(c as NdiComponent, isAr ? 'ar' : 'en')}
            </button>
          ))}
          <span className="text-2xs text-muted ndi-filter-row__count">
            {filtered.length} / {items.length}
          </span>
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : filtered.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد بنود — ابدأ بالاستيراد' : 'No items — try import'}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'العنوان' : 'Title'}</th>
                    <th>{isAr ? 'المكوّن' : 'Component'}</th>
                    <th>{isAr ? 'المجال' : 'Domain'}</th>
                    <th>{isAr ? 'الأولوية' : 'Priority'}</th>
                    <th>{isAr ? 'الإلزام' : 'Mandatory'}</th>
                    <th>{isAr ? 'المصدر' : 'Source'}</th>
                    <th className="ndi-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(it => (
                    <tr key={it.id}>
                      <td className="mono text-2xs">{it.requirementCode}</td>
                      <td>
                        <div className="ndi-cell-title">{isAr && it.titleAr ? it.titleAr : it.titleEn}</div>
                        {it.subdomain && (
                          <div className="text-2xs text-muted ndi-cell-sub">{it.subdomain}</div>
                        )}
                      </td>
                      <td><Badge tone={componentTone(it.component)}>{componentLabel(it.component, isAr ? 'ar' : 'en')}</Badge></td>
                      <td className="text-xs">{it.domain}</td>
                      <td><Badge tone={priorityTone(it.priority)}>{priorityLabel(it.priority, isAr ? 'ar' : 'en')}</Badge></td>
                      <td>{it.mandatory ? <Badge tone="danger">{isAr ? 'إلزامي' : 'Yes'}</Badge> : <span className="text-muted">—</span>}</td>
                      <td>
                        {it.isOfficialImport
                          ? <Badge tone="success">{isAr ? 'رسمي' : 'Official'}</Badge>
                          : <Badge tone="warning">{isAr ? 'مؤقت' : 'Placeholder'}</Badge>}
                      </td>
                      <td className="ndi-text-end">
                        <div className="ndi-actions-row">
                          <Button size="sm" variant="ghost" Icon={Pencil}
                            title={isAr ? 'تعديل' : 'Edit'}
                            onClick={() => setEditItem(it)} />
                          {!it.isOfficialImport && (
                            <Button size="sm" variant="secondary" Icon={Check}
                              onClick={() => onMarkOfficial(it)}>
                              {isAr ? 'علّم رسمي' : 'Mark official'}
                            </Button>
                          )}
                          {it.isOfficialImport && (
                            <Button size="sm" variant="ghost" Icon={AlertCircle}
                              onClick={() => onMarkPlaceholder(it)}
                              title={isAr ? 'إعادة وضع المؤقت' : 'Mark as placeholder'} />
                          )}
                          <Button size="sm" variant="ghost" Icon={Trash2}
                            title={isAr ? 'حذف' : 'Delete'}
                            onClick={() => onDelete(it)} />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
      </Card>

      <FormModal
        open={importOpen}
        title={isAr ? 'استيراد بنود الإطار (JSON)' : 'Import framework items (JSON)'}
        fields={importFields}
        initial={{ json: '' }}
        size="lg"
        submitLabel={isAr ? 'استيراد' : 'Import'}
        onClose={() => setImportOpen(false)}
        onSubmit={onImport}
      />

      <FormModal<FrameworkForm>
        open={openNew}
        title={isAr ? 'تعريف متطلب إطار نضيء' : 'Define NDI framework requirement'}
        fields={reqFields}
        initial={newInitial}
        size="lg"
        submitLabel={isAr ? 'إضافة المتطلب' : 'Add requirement'}
        onClose={() => setOpenNew(false)}
        onSubmit={onCreate}
      />

      <FormModal<FrameworkForm>
        open={!!editItem}
        title={isAr ? 'تعديل متطلب الإطار' : 'Edit framework requirement'}
        fields={reqFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditItem(null)}
        onSubmit={onUpdate}
      />
    </div>
  );
}

// ─── Maturity Assessment tab (slice 3 — V68) ────────────────────────
//
// Per-cycle responses to MATURITY framework items. Picks the most-
// recent active cycle on load; "Start maturity assessment" bulk-creates
// rows from MATURITY framework items if none exist yet. Per-row edit
// uses an inline level dropdown — submit/approve flows go through
// state-machine endpoints with bilingual toasts.

function MaturityTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [cycles, setCycles] = useState<NdiAssessmentCycle[]>([]);
  const [cycleId, setCycleId] = useState<number | null>(null);
  const [items, setItems] = useState<NdiMaturityAssessment[]>([]);
  const [agg, setAgg] = useState<NdiMaturityAggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<NdiMaturityAssessment | null>(null);

  async function loadCycles() {
    try {
      const pg = await listCycles({ size: 100 });
      setCycles(pg.content);
      if (pg.content.length > 0 && !cycleId) setCycleId(pg.content[0].id!);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to load cycles');
    }
  }
  useEffect(() => { void loadCycles(); }, []);

  async function reload() {
    if (!cycleId) return;
    setLoading(true);
    // Per-call .catch() — maturity list and aggregate load independently.
    const [pg, a] = await Promise.all([
      listMaturity(cycleId, { size: 500 }).catch(() => ({ content: [] as NdiMaturityAssessment[], totalElements: 0 })),
      maturityAggregate(cycleId).catch(() => null as NdiMaturityAggregate | null),
    ]);
    setItems(pg.content);
    setAgg(a);
    setLoading(false);
  }
  useEffect(() => { if (cycleId) void reload(); }, [cycleId]);

  async function onStart() {
    if (!cycleId) return;
    try {
      const s = await startMaturityForCycle(cycleId);
      toast.success(isAr
        ? `أُنشئ ${s.created} • تُجوهل ${s.skipped}`
        : `Created ${s.created} · Skipped ${s.skipped}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function onLevelChange(it: NdiMaturityAssessment, field: 'currentLevel' | 'targetLevel', val: number) {
    try {
      await updateMaturity(it.id!, { [field]: val });
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onSubmit(it: NdiMaturityAssessment) {
    try { await submitMaturity(it.id!); toast.success(isAr ? 'مُقدَّم' : 'Submitted'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onReview(it: NdiMaturityAssessment) {
    try { await markUnderReviewMaturity(it.id!); toast.info(isAr ? 'تحت المراجعة' : 'Under review'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onReturn(it: NdiMaturityAssessment) {
    const reason = await dialog.prompt({
      title: isAr ? 'إعادة للتعديل' : 'Return for changes',
      label: isAr ? 'سبب الإرجاع' : 'Reason',
      type: 'textarea', required: true,
    });
    if (!reason) return;
    try { await returnMaturity(it.id!, reason); toast.info(isAr ? 'أُعيد للتعديل' : 'Returned'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onApprove(it: NdiMaturityAssessment) {
    const approverName = await dialog.prompt({
      title: isAr ? 'اعتماد البند' : 'Approve item',
      label: isAr ? 'اسم المعتمد' : 'Approver name',
      required: true,
    });
    if (!approverName) return;
    try { await approveMaturity(it.id!, approverName); toast.success(isAr ? 'مُعتمَد' : 'Approved'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onReopen(it: NdiMaturityAssessment) {
    try { await reopenMaturity(it.id!); toast.info(isAr ? 'أُعيد الفتح' : 'Reopened'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onSaveEvidence(values: Record<string, unknown>) {
    if (!editing) return;
    try {
      await updateMaturity(editing.id!, {
        evidenceRef: String(values.evidenceRef ?? '').trim() || undefined,
        responseTextEn: String(values.responseTextEn ?? '').trim() || undefined,
        responseTextAr: String(values.responseTextAr ?? '').trim() || undefined,
        justification:  String(values.justification  ?? '').trim() || undefined,
      });
      toast.success(isAr ? 'تم الحفظ' : 'Saved');
      setEditing(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Save failed');
    }
  }

  const evidenceFields: FieldDef[] = [
    { kind: 'text',     key: 'evidenceRef',     span: 12,
      label: isAr ? 'مرجع الدليل' : 'Evidence reference',
      placeholder: 'e.g. Q1-2026-DG-policy.pdf' },
    { kind: 'textarea', key: 'responseTextEn',  rows: 3, span: 12,
      label: isAr ? 'الرد (إنجليزي)' : 'Response (English)' },
    { kind: 'textarea', key: 'responseTextAr',  rows: 3, span: 12,
      label: isAr ? 'الرد (عربي)' : 'Response (Arabic)' },
    { kind: 'textarea', key: 'justification',   rows: 3, span: 12,
      label: isAr ? 'مبرّر (إن كان الهدف < الحالي)' : 'Justification (if target < current)' },
  ];

  const groupedByDomain = useMemo(() => {
    const m = new Map<string, NdiMaturityAssessment[]>();
    for (const i of items) {
      if (!m.has(i.domain)) m.set(i.domain, []);
      m.get(i.domain)!.push(i);
    }
    return m;
  }, [items]);

  if (cycles.length === 0) {
    return <InlineEmpty>{isAr ? 'أنشئ دورة تقييم أولاً' : 'Create an assessment cycle first'}</InlineEmpty>;
  }

  return (
    <div className="ndi-stack">
      <Card title={isAr ? 'تقييم النضج' : 'Maturity assessment'} Icon={Target}
        action={
          <div className="ndi-actions-inline--center">
            <select
              value={cycleId ?? ''}
              onChange={e => setCycleId(Number(e.target.value))}
              className="ndi-mini-select" style={{ minWidth: 240 }}>
              {cycles.map(c => (
                <option key={c.id} value={c.id}>
                  {isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn} ({c.reportingYear})
                </option>
              ))}
            </select>
            <Button size="sm" variant="primary" Icon={Plus} onClick={onStart}>
              {isAr ? 'إنشاء بنود من الإطار' : 'Start from framework'}
            </Button>
          </div>
        }>
        {agg && (
          <div className="kpi-grid">
            <Kpi Icon={Target}       label={isAr ? 'نقاط النضج' : 'Maturity score'} value={`${Number(agg.maturityScorePct).toFixed(0)}%`}
                 tone={agg.maturityScorePct >= 60 ? 'success' : agg.maturityScorePct >= 40 ? 'warning' : 'danger'} to="/ndi" />
            <Kpi Icon={FileText}     label={isAr ? 'إجمالي البنود' : 'Total items'}    value={agg.total}            tone="primary" to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'بلا رد'        : 'Unresponded'}    value={agg.unresponded}      tone="warning" to="/ndi" />
            <Kpi Icon={CheckCircle2} label={isAr ? 'مُعتمَد'        : 'Approved'}      value={agg.byStatus.APPROVED ?? 0}  tone="success" to="/ndi" />
            <Kpi Icon={ArrowRight}   label={isAr ? 'تحسين مقترح'  : 'Improv. suggested'} value={agg.improvementSuggestedCount} tone="info" to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'مخالفة القاعدة 2' : 'Rule 2 violations'} value={agg.rule2ViolationCount} tone={agg.rule2ViolationCount > 0 ? 'danger' : 'success'} to="/ndi" />
          </div>
        )}
      </Card>

      {loading
        ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        : items.length === 0
          ? <InlineEmpty>
              <div>
                {isAr ? 'لا توجد بنود نضج في هذه الدورة بعد.' : 'No maturity items in this cycle yet.'}
                <div className="mt-md">
                  <Button variant="primary" Icon={Plus} onClick={onStart}>
                    {isAr ? 'إنشاء من الإطار' : 'Generate from framework'}
                  </Button>
                </div>
              </div>
            </InlineEmpty>
          : Array.from(groupedByDomain.entries()).map(([domain, rows]) => {
              const domainPct = agg?.byDomainPct[domain];
              return (
                <Card key={domain}
                  title={domain}
                  Icon={Target}
                  action={domainPct != null
                    ? <Badge tone={Number(domainPct) >= 60 ? 'success' : Number(domainPct) >= 40 ? 'warning' : 'danger'}>
                        {Number(domainPct).toFixed(0)}%
                      </Badge>
                    : undefined}>
                  <table className="grc-table">
                    <thead>
                      <tr>
                        <th>{isAr ? 'البند' : 'Item'}</th>
                        <th className="ndi-th-w-130">{isAr ? 'المستوى الحالي' : 'Current level'}</th>
                        <th className="ndi-th-w-130">{isAr ? 'الهدف' : 'Target'}</th>
                        <th>{isAr ? 'الحالة' : 'Status'}</th>
                        <th>{isAr ? 'الدليل' : 'Evidence'}</th>
                        <th className="ndi-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map(it => {
                        const isApproved = it.status === 'APPROVED';
                        const canSubmit = ['DRAFT', 'IN_PROGRESS', 'RETURNED'].includes(it.status);
                        return (
                          <tr key={it.id}>
                            <td>
                              <div className="text-2xs mono text-muted">{it.frameworkItemId}</div>
                              <div className="text-2xs">{it.subdomain ?? '—'}</div>
                              {it.improvementActionSuggested && (
                                <Badge tone="info">{isAr ? 'تحسين مقترح' : 'Improvement suggested'}</Badge>
                              )}
                            </td>
                            <td>
                              <select
                                value={it.currentLevel ?? ''}
                                disabled={isApproved}
                                onChange={e => onLevelChange(it, 'currentLevel', Number(e.target.value))}
                                className="ndi-level-select"
                                style={{ borderInlineStart: `4px solid ${levelColor(it.currentLevel)}` }}>
                                <option value="">—</option>
                                {[0, 1, 2, 3, 4, 5].map(l => (
                                  <option key={l} value={l}>{l} · {levelLabel(l, isAr ? 'ar' : 'en')}</option>
                                ))}
                              </select>
                            </td>
                            <td>
                              <select
                                value={it.targetLevel ?? ''}
                                disabled={isApproved}
                                onChange={e => onLevelChange(it, 'targetLevel', Number(e.target.value))}
                                className="ndi-level-select"
                                style={{ borderInlineStart: `4px solid ${levelColor(it.targetLevel)}` }}>
                                <option value="">—</option>
                                {[0, 1, 2, 3, 4, 5].map(l => (
                                  <option key={l} value={l}>{l} · {levelLabel(l, isAr ? 'ar' : 'en')}</option>
                                ))}
                              </select>
                            </td>
                            <td><Badge tone={maturityStatusTone(it.status)}>{maturityStatusLabel(it.status, isAr ? 'ar' : 'en')}</Badge></td>
                            <td className="text-xs">
                              {it.evidenceRef ?? <span className="text-muted">—</span>}
                              {it.currentLevel != null && it.currentLevel >= 4 && !it.evidenceRef && (
                                <div className="ndi-cell-warn">
                                  {isAr ? 'القاعدة 2: مطلوب' : 'Rule 2: required'}
                                </div>
                              )}
                            </td>
                            <td className="ndi-text-end">
                              <div className="ndi-actions-row">
                                {!isApproved && (
                                  <Button size="sm" variant="ghost" onClick={() => setEditing(it)}>
                                    {isAr ? 'تعديل' : 'Edit'}
                                  </Button>
                                )}
                                {canSubmit && (
                                  <Button size="sm" variant="secondary" Icon={ArrowRight} onClick={() => onSubmit(it)}>
                                    {isAr ? 'تقديم' : 'Submit'}
                                  </Button>
                                )}
                                {it.status === 'SUBMITTED' && (
                                  <Button size="sm" variant="secondary" onClick={() => onReview(it)}>
                                    {isAr ? 'مراجعة' : 'Review'}
                                  </Button>
                                )}
                                {it.status === 'UNDER_REVIEW' && (
                                  <>
                                    <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onApprove(it)}>
                                      {isAr ? 'اعتماد' : 'Approve'}
                                    </Button>
                                    <Button size="sm" variant="ghost" onClick={() => onReturn(it)}>
                                      {isAr ? 'إعادة' : 'Return'}
                                    </Button>
                                  </>
                                )}
                                {isApproved && (
                                  <Button size="sm" variant="ghost" onClick={() => onReopen(it)}>
                                    {isAr ? 'إعادة فتح' : 'Reopen'}
                                  </Button>
                                )}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </Card>
              );
            })}

      <FormModal
        open={editing !== null}
        title={isAr ? 'تعديل بند النضج' : 'Edit maturity item'}
        fields={evidenceFields}
        initial={{
          evidenceRef:    editing?.evidenceRef ?? '',
          responseTextEn: editing?.responseTextEn ?? '',
          responseTextAr: editing?.responseTextAr ?? '',
          justification:  editing?.justification ?? '',
        }}
        size="lg"
        submitLabel={isAr ? 'حفظ' : 'Save'}
        onClose={() => setEditing(null)}
        onSubmit={onSaveEvidence}
      />
    </div>
  );
}

// ─── Compliance Assessment tab (slice 4 — V69) ──────────────────────
//
// Mirrors MaturityTab in shape — cycle picker, bulk-start, KPI strip,
// per-domain grouped items. Differs in the per-row interaction: instead
// of a level dropdown, a compliance-status dropdown applies Rules 3/4/5
// on the server side (auto-flips to EVIDENCE_REQUIRED when COMPLIANT
// with no evidence, sets needs_gap=true for PARTIAL/NON_COMPLIANT,
// refuses NOT_APPLICABLE without a justification).

function ComplianceTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [cycles, setCycles] = useState<NdiAssessmentCycle[]>([]);
  const [cycleId, setCycleId] = useState<number | null>(null);
  const [items, setItems] = useState<NdiComplianceAssessment[]>([]);
  const [agg, setAgg] = useState<NdiComplianceAggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [evidencing, setEvidencing] = useState<NdiComplianceAssessment | null>(null);
  const [editItem, setEditItem] = useState<NdiComplianceAssessment | null>(null);

  async function loadCycles() {
    try {
      const pg = await listCycles({ size: 100 });
      setCycles(pg.content);
      if (pg.content.length > 0 && !cycleId) setCycleId(pg.content[0].id!);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to load cycles');
    }
  }
  useEffect(() => { void loadCycles(); }, []);

  async function reload() {
    if (!cycleId) return;
    setLoading(true);
    // Per-call .catch() — compliance list and aggregate load independently.
    const [pg, a] = await Promise.all([
      listCompliance(cycleId, { size: 500 }).catch(() => ({ content: [] as NdiComplianceAssessment[], totalElements: 0 })),
      complianceAggregate(cycleId).catch(() => null as NdiComplianceAggregate | null),
    ]);
    setItems(pg.content);
    setAgg(a);
    setLoading(false);
  }
  useEffect(() => { if (cycleId) void reload(); }, [cycleId]);

  async function onStart() {
    if (!cycleId) return;
    try {
      const s = await startComplianceForCycle(cycleId);
      toast.success(isAr
        ? `أُنشئ ${s.created} • تُجوهل ${s.skipped}`
        : `Created ${s.created} · Skipped ${s.skipped}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function onStatusChange(it: NdiComplianceAssessment, status: NdiComplianceStatus) {
    // Rule 5 — prompt for justification when picking NOT_APPLICABLE.
    let justification: string | undefined;
    if (status === 'NOT_APPLICABLE') {
      const ans = await dialog.prompt({
        title: isAr ? 'مبرر "غير منطبق" (القاعدة 5)' : 'NOT_APPLICABLE justification (Rule 5)',
        label: isAr ? 'سبب عدم انطباق هذا البند' : 'Why is this item not applicable?',
        type: 'textarea', required: true,
        initial: it.naJustification ?? '',
      });
      if (!ans) return;
      justification = ans;
    }
    try {
      await setComplianceStatus(it.id!, status, justification);
      toast.success(isAr ? 'تم تحديث الحالة' : 'Status updated');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onSubmit(it: NdiComplianceAssessment) {
    try { await submitCompliance(it.id!); toast.success(isAr ? 'مُقدَّم' : 'Submitted'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onReview(it: NdiComplianceAssessment) {
    try { await markUnderReviewCompliance(it.id!); toast.info(isAr ? 'تحت المراجعة' : 'Under review'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onReturn(it: NdiComplianceAssessment) {
    const reason = await dialog.prompt({
      title: isAr ? 'إعادة للتعديل' : 'Return for changes',
      label: isAr ? 'سبب الإرجاع' : 'Reason',
      type: 'textarea', required: true,
    });
    if (!reason) return;
    try { await returnCompliance(it.id!, reason); toast.info(isAr ? 'أُعيد' : 'Returned'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onApprove(it: NdiComplianceAssessment) {
    const approverName = await dialog.prompt({
      title: isAr ? 'اعتماد البند' : 'Approve item',
      label: isAr ? 'اسم المعتمد' : 'Approver name',
      required: true,
    });
    if (!approverName) return;
    try { await approveCompliance(it.id!, approverName); toast.success(isAr ? 'مُعتمَد' : 'Approved'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onReopen(it: NdiComplianceAssessment) {
    try { await reopenCompliance(it.id!); toast.info(isAr ? 'أُعيد الفتح' : 'Reopened'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onSaveEvidence(values: Record<string, unknown>) {
    if (!evidencing) return;
    const ref = String(values.evidenceRef ?? '').trim();
    if (!ref) { toast.danger(isAr ? 'المرجع مطلوب' : 'evidence_ref required'); return; }
    try {
      await attachComplianceEvidence(evidencing.id!, ref, String(values.evidenceOwner ?? '').trim() || undefined);
      toast.success(isAr ? 'أُرفق الدليل' : 'Evidence attached');
      setEvidencing(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Save failed');
    }
  }

  // Edit-response — the bilingual narrative + ownership fields that the
  // set-status / attach-evidence quick actions don't reach. Trim-or-undefined
  // so cleared fields are sent as null-means-keep (no accidental blanking).
  //
  // complianceStatus is echoed back deliberately: the entity defaults the
  // column to "UNDER_REVIEW", so an omitted value would deserialize to that
  // default and the service's update() would overwrite the reviewer's real
  // classification. Sending the current status preserves it; the service
  // re-applies Rules 3/4/5 against it on save.
  async function onSaveResponse(values: Record<string, unknown>) {
    if (!editItem?.id) return;
    const u = (s: unknown) => { const t = String(s ?? '').trim(); return t === '' ? undefined : t; };
    try {
      await updateCompliance(editItem.id, {
        complianceStatus:  editItem.complianceStatus,
        responseTextEn:    u(values.responseTextEn),
        responseTextAr:    u(values.responseTextAr),
        controlOwnerName:  u(values.controlOwnerName),
        controlOwnerEmail: u(values.controlOwnerEmail),
        evidenceOwnerName: u(values.evidenceOwnerName),
        notes:             u(values.notes),
      });
      toast.success(isAr ? 'حُدّثت الاستجابة' : 'Response updated');
      setEditItem(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Save failed');
    }
  }

  const evidenceFields: FieldDef[] = [
    { kind: 'text', key: 'evidenceRef',   required: true, span: 12,
      label: isAr ? 'مرجع الدليل' : 'Evidence reference',
      placeholder: 'e.g. Annual Report 2025 §3.4' },
    { kind: 'text', key: 'evidenceOwner', span: 12,
      label: isAr ? 'مالك الدليل' : 'Evidence owner' },
  ];

  const responseFields: FieldDef[] = [
    { kind: 'textarea', key: 'responseTextEn', span: 12, rows: 3,
      label: isAr ? 'نص الاستجابة (إنجليزي)' : 'Response narrative (EN)' },
    { kind: 'textarea', key: 'responseTextAr', span: 12, rows: 3,
      label: isAr ? 'نص الاستجابة (عربي)' : 'Response narrative (AR)' },
    { kind: 'text', key: 'controlOwnerName', span: 6, maxLength: 255,
      label: isAr ? 'مالك الضابط' : 'Control owner' },
    { kind: 'text', key: 'controlOwnerEmail', span: 6, maxLength: 255,
      label: isAr ? 'بريد مالك الضابط' : 'Control owner email' },
    { kind: 'text', key: 'evidenceOwnerName', span: 6, maxLength: 255,
      label: isAr ? 'مالك الدليل' : 'Evidence owner' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2,
      label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  const editInitial = useMemo<Record<string, unknown>>(() => ({
    responseTextEn:    editItem?.responseTextEn ?? '',
    responseTextAr:    editItem?.responseTextAr ?? '',
    controlOwnerName:  editItem?.controlOwnerName ?? '',
    controlOwnerEmail: editItem?.controlOwnerEmail ?? '',
    evidenceOwnerName: editItem?.evidenceOwnerName ?? '',
    notes:             editItem?.notes ?? '',
  }), [editItem]);

  const groupedByDomain = useMemo(() => {
    const m = new Map<string, NdiComplianceAssessment[]>();
    for (const i of items) {
      if (!m.has(i.domain)) m.set(i.domain, []);
      m.get(i.domain)!.push(i);
    }
    return m;
  }, [items]);

  if (cycles.length === 0) {
    return <InlineEmpty>{isAr ? 'أنشئ دورة تقييم أولاً' : 'Create an assessment cycle first'}</InlineEmpty>;
  }

  return (
    <div className="ndi-stack">
      <Card title={isAr ? 'تقييم الامتثال' : 'Compliance assessment'} Icon={CheckCircle2}
        action={
          <div className="ndi-actions-inline--center">
            <select
              value={cycleId ?? ''}
              onChange={e => setCycleId(Number(e.target.value))}
              className="ndi-mini-select" style={{ minWidth: 240 }}>
              {cycles.map(c => (
                <option key={c.id} value={c.id}>
                  {isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn} ({c.reportingYear})
                </option>
              ))}
            </select>
            <Button size="sm" variant="primary" Icon={Plus} onClick={onStart}>
              {isAr ? 'إنشاء بنود من الإطار' : 'Start from framework'}
            </Button>
          </div>
        }>
        {agg && (
          <div className="kpi-grid">
            <Kpi Icon={CheckCircle2} label={isAr ? 'نقاط الامتثال' : 'Compliance score'} value={`${Number(agg.compliancePct).toFixed(0)}%`}
                 tone={agg.compliancePct >= 80 ? 'success' : agg.compliancePct >= 50 ? 'warning' : 'danger'} to="/ndi" />
            <Kpi Icon={FileText}     label={isAr ? 'إجمالي البنود' : 'Total items'}     value={agg.total}                                  tone="primary" to="/ndi" />
            <Kpi Icon={CheckCircle2} label={isAr ? 'ملتزم' : 'Compliant'}              value={agg.byCompliance.COMPLIANT ?? 0}             tone="success" to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'جزئي' : 'Partial'}                 value={agg.byCompliance.PARTIAL ?? 0}               tone="warning" to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'غير ملتزم' : 'Non-compliant'}      value={agg.byCompliance.NON_COMPLIANT ?? 0}         tone="danger" to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'يتطلب دليلاً' : 'Evidence req.'}    value={agg.evidenceRequiredCount}                  tone="warning" to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'تحتاج فجوة' : 'Need gap'}          value={agg.needsGapCount}                          tone={agg.needsGapCount > 0 ? 'danger' : 'success'} to="/ndi" />
          </div>
        )}
      </Card>

      {loading
        ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        : items.length === 0
          ? <InlineEmpty>
              <div>
                {isAr ? 'لا توجد بنود امتثال في هذه الدورة بعد.' : 'No compliance items in this cycle yet.'}
                <div className="mt-md">
                  <Button variant="primary" Icon={Plus} onClick={onStart}>
                    {isAr ? 'إنشاء من الإطار' : 'Generate from framework'}
                  </Button>
                </div>
              </div>
            </InlineEmpty>
          : Array.from(groupedByDomain.entries()).map(([domain, rows]) => {
              const domainPct = agg?.byDomainPct[domain];
              return (
                <Card key={domain}
                  title={domain}
                  Icon={CheckCircle2}
                  action={domainPct != null
                    ? <Badge tone={Number(domainPct) >= 80 ? 'success' : Number(domainPct) >= 50 ? 'warning' : 'danger'}>
                        {Number(domainPct).toFixed(0)}%
                      </Badge>
                    : undefined}>
                  <table className="grc-table">
                    <thead>
                      <tr>
                        <th>{isAr ? 'الكود' : 'Code'}</th>
                        <th className="ndi-th-w-220">{isAr ? 'حالة الامتثال' : 'Compliance status'}</th>
                        <th>{isAr ? 'الدليل' : 'Evidence'}</th>
                        <th>{isAr ? 'مسار العمل' : 'Lifecycle'}</th>
                        <th className="ndi-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map(it => {
                        const isApproved = it.lifecycleStatus === 'APPROVED';
                        const canSubmit = ['DRAFT', 'IN_PROGRESS', 'RETURNED'].includes(it.lifecycleStatus);
                        return (
                          <tr key={it.id}>
                            <td className="mono text-2xs">
                              {it.requirementCode}
                              {it.subdomain && (
                                <div className="text-2xs text-muted ndi-cell-sub">{it.subdomain}</div>
                              )}
                              {it.needsGap && (
                                <Badge tone="danger">{isAr ? 'فجوة' : 'Gap'}</Badge>
                              )}
                            </td>
                            <td>
                              <select
                                value={it.complianceStatus}
                                disabled={isApproved}
                                onChange={e => onStatusChange(it, e.target.value as NdiComplianceStatus)}
                                className="ndi-level-select">
                                {(['COMPLIANT', 'PARTIAL', 'NON_COMPLIANT', 'NOT_APPLICABLE', 'EVIDENCE_REQUIRED', 'UNDER_REVIEW'] as NdiComplianceStatus[])
                                  .map(s => (
                                    <option key={s} value={s}>{complianceStatusLabel(s, isAr ? 'ar' : 'en')}</option>
                                  ))}
                              </select>
                              {it.complianceStatus === 'NOT_APPLICABLE' && it.naJustification && (
                                <div className="text-2xs text-muted ndi-cell-sub" style={{ lineHeight: 1.4 }}>
                                  {isAr ? 'المبرّر:' : 'Justification:'} {it.naJustification.slice(0, 80)}{it.naJustification.length > 80 ? '…' : ''}
                                </div>
                              )}
                            </td>
                            <td className="text-xs">
                              {it.evidenceRef ?? <span className="text-muted">—</span>}
                              {!it.evidenceRef && (it.complianceStatus === 'COMPLIANT' || it.complianceStatus === 'PARTIAL') && (
                                <div className="ndi-cell-warn">
                                  {isAr ? 'القاعدة 3: مطلوب' : 'Rule 3: required'}
                                </div>
                              )}
                            </td>
                            <td><Badge tone={lifecycleTone(it.lifecycleStatus)}>{lifecycleLabel(it.lifecycleStatus, isAr ? 'ar' : 'en')}</Badge></td>
                            <td className="ndi-text-end">
                              <div className="ndi-actions-row">
                                {!isApproved && (
                                  <Button size="sm" variant="ghost" Icon={Pencil}
                                    title={isAr ? 'تعديل الاستجابة' : 'Edit response'}
                                    onClick={() => setEditItem(it)} />
                                )}
                                {!isApproved && (
                                  <Button size="sm" variant="ghost" Icon={Paperclip}
                                    onClick={() => setEvidencing(it)}>
                                    {isAr ? 'إرفاق' : 'Attach'}
                                  </Button>
                                )}
                                {canSubmit && (
                                  <Button size="sm" variant="secondary" Icon={ArrowRight} onClick={() => onSubmit(it)}>
                                    {isAr ? 'تقديم' : 'Submit'}
                                  </Button>
                                )}
                                {it.lifecycleStatus === 'SUBMITTED' && (
                                  <Button size="sm" variant="secondary" onClick={() => onReview(it)}>
                                    {isAr ? 'مراجعة' : 'Review'}
                                  </Button>
                                )}
                                {it.lifecycleStatus === 'UNDER_REVIEW' && (
                                  <>
                                    <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onApprove(it)}>
                                      {isAr ? 'اعتماد' : 'Approve'}
                                    </Button>
                                    <Button size="sm" variant="ghost" onClick={() => onReturn(it)}>
                                      {isAr ? 'إعادة' : 'Return'}
                                    </Button>
                                  </>
                                )}
                                {isApproved && (
                                  <Button size="sm" variant="ghost" onClick={() => onReopen(it)}>
                                    {isAr ? 'إعادة فتح' : 'Reopen'}
                                  </Button>
                                )}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </Card>
              );
            })}

      <FormModal
        open={evidencing !== null}
        title={isAr ? 'إرفاق دليل' : 'Attach evidence'}
        fields={evidenceFields}
        initial={{
          evidenceRef: evidencing?.evidenceRef ?? '',
          evidenceOwner: evidencing?.evidenceOwnerName ?? '',
        }}
        size="md"
        submitLabel={isAr ? 'إرفاق' : 'Attach'}
        onClose={() => setEvidencing(null)}
        onSubmit={onSaveEvidence}
      />

      <FormModal
        open={editItem !== null}
        title={editItem
          ? (isAr ? `تعديل استجابة ${editItem.requirementCode}` : `Edit response — ${editItem.requirementCode}`)
          : ''}
        fields={responseFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ' : 'Save'}
        onClose={() => setEditItem(null)}
        onSubmit={onSaveResponse}
      />
    </div>
  );
}

// ─── Operational Excellence KPI tab (slice 5 — V70) ─────────────────
//
// Per-cycle KPI register. Differs from maturity/compliance:
//   - No bulk-start from framework — KPIs are standalone metrics
//   - Status is computed server-side from actual vs target (Rule 13)
//   - Primary user action is "record new actual" (slider + as-of date)
//   - Visualisation = progress bar toward target, not status badge alone

// All-string create-KPI form shape. Numeric fields are number-kind in the
// FormModal (runtime values are numbers); the submit handler coerces them.
// Status is intentionally absent — the server computes it (Rule 13) from
// actual vs target, so a freshly-defined KPI starts DATA_MISSING/NOT_STARTED.
interface OpexForm {
  category:        string;
  kpiCode:         string;
  indicatorNameEn: string;
  indicatorNameAr: string;
  target:          string;
  baseline:        string;
  unit:            string;
  frequency:       string;
  dataSource:      string;
  ownerName:       string;
  ownerEmail:      string;
  definitionEn:    string;
  definitionAr:    string;
  notes:           string;
}

const OPEX_FREQUENCIES: NdiKpiFrequency[] = ['MONTHLY', 'QUARTERLY', 'SEMI_ANNUAL', 'ANNUAL'];
const FREQ_LABEL_AR: Record<NdiKpiFrequency, string> = {
  MONTHLY: 'شهري', QUARTERLY: 'ربع سنوي', SEMI_ANNUAL: 'نصف سنوي', ANNUAL: 'سنوي',
};
const FREQ_LABEL_EN: Record<NdiKpiFrequency, string> = {
  MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', SEMI_ANNUAL: 'Semi-annual', ANNUAL: 'Annual',
};

function opexFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'text', key: 'category', label: isAr ? 'الفئة' : 'Category', span: 6, required: true, maxLength: 60,
      placeholder: isAr ? 'مثلاً: جاهزية الخدمة' : 'e.g. Service Availability' },
    { kind: 'text', key: 'kpiCode', label: isAr ? 'كود المؤشّر (اختياري)' : 'KPI code (optional)', span: 6, maxLength: 40,
      placeholder: 'e.g. OPEX-AVAIL-01' },

    { kind: 'text', key: 'indicatorNameEn', label: isAr ? 'اسم المؤشّر (إنجليزي)' : 'Indicator name (EN)', span: 6, required: true, maxLength: 255 },
    { kind: 'text', key: 'indicatorNameAr', label: isAr ? 'اسم المؤشّر (عربي)' : 'Indicator name (AR)', span: 6, maxLength: 255 },

    { kind: 'number', key: 'target', label: isAr ? 'الهدف' : 'Target', span: 3, required: true },
    { kind: 'number', key: 'baseline', label: isAr ? 'خط الأساس' : 'Baseline', span: 3 },
    { kind: 'text', key: 'unit', label: isAr ? 'الوحدة' : 'Unit', span: 3, maxLength: 20, placeholder: '%, ms, hrs' },
    { kind: 'select', key: 'frequency', label: isAr ? 'التكرار' : 'Frequency', span: 3,
      options: OPEX_FREQUENCIES.map(f => ({ value: f, label: isAr ? FREQ_LABEL_AR[f] : FREQ_LABEL_EN[f] })) },

    { kind: 'text', key: 'dataSource', label: isAr ? 'مصدر البيانات' : 'Data source', span: 12, maxLength: 255,
      placeholder: isAr ? 'مثلاً: لوحة مراقبة الخدمة' : 'e.g. Service monitoring dashboard' },

    { kind: 'text', key: 'ownerName', label: isAr ? 'اسم المالك' : 'Owner name', span: 6, maxLength: 255 },
    { kind: 'text', key: 'ownerEmail', label: isAr ? 'بريد المالك' : 'Owner email', span: 6, maxLength: 255 },

    { kind: 'textarea', key: 'definitionEn', label: isAr ? 'التعريف (إنجليزي)' : 'Definition (EN)', span: 12, rows: 2 },
    { kind: 'textarea', key: 'definitionAr', label: isAr ? 'التعريف (عربي)' : 'Definition (AR)', span: 12, rows: 2 },
    { kind: 'textarea', key: 'notes', label: isAr ? 'ملاحظات' : 'Notes', span: 12, rows: 2 },
  ];
}

function OpexTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [cycles, setCycles] = useState<NdiAssessmentCycle[]>([]);
  const [cycleId, setCycleId] = useState<number | null>(null);
  const [items, setItems] = useState<NdiOpexKpi[]>([]);
  const [agg, setAgg] = useState<NdiOpexAggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [openNew, setOpenNew] = useState(false);
  const [editItem, setEditItem] = useState<NdiOpexKpi | null>(null);
  const newInitial = useMemo<OpexForm>(() => ({
    category: '', kpiCode: '', indicatorNameEn: '', indicatorNameAr: '',
    target: '', baseline: '', unit: '', frequency: 'QUARTERLY', dataSource: '',
    ownerName: '', ownerEmail: '', definitionEn: '', definitionAr: '', notes: '',
  }), []);
  // Edit modal seed — mapped from the selected KPI. Keyed on [editItem] so
  // FormModal's reset effect re-runs when a different row is opened. Numeric
  // columns are stringified for the number-kind fields; status is omitted
  // (the server recomputes it from actual vs target on every save).
  const editInitial = useMemo<OpexForm>(() => ({
    category:        editItem?.category ?? '',
    kpiCode:         editItem?.kpiCode ?? '',
    indicatorNameEn: editItem?.indicatorNameEn ?? '',
    indicatorNameAr: editItem?.indicatorNameAr ?? '',
    target:          editItem?.target != null ? String(editItem.target) : '',
    baseline:        editItem?.baseline != null ? String(editItem.baseline) : '',
    unit:            editItem?.unit ?? '',
    frequency:       editItem?.frequency ?? '',
    dataSource:      editItem?.dataSource ?? '',
    ownerName:       editItem?.ownerName ?? '',
    ownerEmail:      editItem?.ownerEmail ?? '',
    definitionEn:    editItem?.definitionEn ?? '',
    definitionAr:    editItem?.definitionAr ?? '',
    notes:           editItem?.notes ?? '',
  }), [editItem]);

  async function loadCycles() {
    try {
      const pg = await listCycles({ size: 100 });
      setCycles(pg.content);
      if (pg.content.length > 0 && !cycleId) setCycleId(pg.content[0].id!);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to load cycles');
    }
  }
  useEffect(() => { void loadCycles(); }, []);

  async function reload() {
    if (!cycleId) return;
    setLoading(true);
    // Per-call .catch() — KPI list and aggregate load independently.
    const [pg, a] = await Promise.all([
      listOpex(cycleId, { size: 500 }).catch(() => ({ content: [] as NdiOpexKpi[], totalElements: 0 })),
      opexAggregate(cycleId).catch(() => null as NdiOpexAggregate | null),
    ]);
    setItems(pg.content);
    setAgg(a);
    setLoading(false);
  }
  useEffect(() => { if (cycleId) void reload(); }, [cycleId]);

  async function onUpdateActual(k: NdiOpexKpi) {
    const ans = await dialog.prompt({
      title: isAr ? 'تسجيل قراءة جديدة' : 'Record new actual',
      label: isAr
        ? `الهدف ${k.target}${k.unit ?? ''} • الحالي ${k.actual ?? '—'}${k.unit ?? ''}`
        : `Target ${k.target}${k.unit ?? ''} · Current ${k.actual ?? '—'}${k.unit ?? ''}`,
      type: 'number', required: true,
      initial: String(k.actual ?? ''),
    });
    if (!ans) return;
    try {
      await updateOpexActual(k.id!, Number(ans));
      toast.success(isAr ? 'تم التحديث وأُعيد حساب الحالة' : 'Updated · status recomputed');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onCreate(v: OpexForm) {
    if (!cycleId) { toast.danger(isAr ? 'اختر دورة أولاً' : 'Select a cycle first'); return; }
    const category = String(v.category ?? '').trim();
    const indicatorNameEn = String(v.indicatorNameEn ?? '').trim();
    const targetStr = String(v.target ?? '').trim();
    const target = Number(targetStr);
    if (!category)        { toast.danger(isAr ? 'الفئة مطلوبة' : 'Category is required'); return; }
    if (!indicatorNameEn) { toast.danger(isAr ? 'اسم المؤشّر (إنجليزي) مطلوب' : 'Indicator name (EN) is required'); return; }
    if (targetStr === '' || !Number.isFinite(target)) {
      toast.danger(isAr ? 'قيمة هدف صحيحة مطلوبة' : 'A valid target value is required'); return;
    }
    const baselineStr = String(v.baseline ?? '').trim();
    const baseline = baselineStr === '' ? undefined : Number(baselineStr);
    const opt = (s?: string) => { const x = String(s ?? '').trim(); return x ? x : undefined; };
    try {
      await createOpex({
        cycleId,
        category,
        kpiCode: opt(v.kpiCode),
        indicatorNameEn,
        indicatorNameAr: opt(v.indicatorNameAr),
        target,
        baseline: baseline != null && Number.isFinite(baseline) ? baseline : undefined,
        unit: opt(v.unit),
        frequency: (v.frequency as NdiKpiFrequency) || undefined,
        dataSource: opt(v.dataSource),
        ownerName: opt(v.ownerName),
        ownerEmail: opt(v.ownerEmail),
        definitionEn: opt(v.definitionEn),
        definitionAr: opt(v.definitionAr),
        notes: opt(v.notes),
        // Server computes the real status (Rule 13); seed a valid placeholder.
        status: 'NOT_STARTED',
      });
      toast.success(isAr ? `تمت إضافة المؤشّر «${indicatorNameEn}»` : `KPI "${indicatorNameEn}" added`);
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّرت إضافة المؤشّر' : 'Failed to add the KPI'));
    }
  }

  async function onUpdate(v: OpexForm) {
    if (!editItem?.id) return;
    const category = String(v.category ?? '').trim();
    const indicatorNameEn = String(v.indicatorNameEn ?? '').trim();
    const targetStr = String(v.target ?? '').trim();
    const target = Number(targetStr);
    if (!category)        { toast.danger(isAr ? 'الفئة مطلوبة' : 'Category is required'); return; }
    if (!indicatorNameEn) { toast.danger(isAr ? 'اسم المؤشّر (إنجليزي) مطلوب' : 'Indicator name (EN) is required'); return; }
    if (targetStr === '' || !Number.isFinite(target)) {
      toast.danger(isAr ? 'قيمة هدف صحيحة مطلوبة' : 'A valid target value is required'); return;
    }
    const baselineStr = String(v.baseline ?? '').trim();
    const baseline = baselineStr === '' ? undefined : Number(baselineStr);
    const opt = (s?: string) => { const x = String(s ?? '').trim(); return x ? x : undefined; };
    // Partial body — only fields the edit form owns. Status is intentionally
    // omitted; the service recomputes it (Rule 13) from actual vs target.
    const body: Partial<NdiOpexKpi> = {
      category,
      kpiCode: opt(v.kpiCode),
      indicatorNameEn,
      indicatorNameAr: opt(v.indicatorNameAr),
      target,
      baseline: baseline != null && Number.isFinite(baseline) ? baseline : undefined,
      unit: opt(v.unit),
      frequency: (v.frequency as NdiKpiFrequency) || undefined,
      dataSource: opt(v.dataSource),
      ownerName: opt(v.ownerName),
      ownerEmail: opt(v.ownerEmail),
      definitionEn: opt(v.definitionEn),
      definitionAr: opt(v.definitionAr),
      notes: opt(v.notes),
    };
    try {
      await updateOpex(editItem.id, body);
      toast.success(isAr ? 'حُدّث المؤشّر' : 'KPI updated');
      setEditItem(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحديث المؤشّر' : 'Failed to update the KPI'));
    }
  }

  const groupedByCategory = useMemo(() => {
    const m = new Map<string, NdiOpexKpi[]>();
    for (const i of items) {
      if (!m.has(i.category)) m.set(i.category, []);
      m.get(i.category)!.push(i);
    }
    return m;
  }, [items]);

  if (cycles.length === 0) {
    return <InlineEmpty>{isAr ? 'أنشئ دورة تقييم أولاً' : 'Create an assessment cycle first'}</InlineEmpty>;
  }

  return (
    <div className="ndi-stack">
      <Card title={isAr ? 'مؤشّرات التميز التشغيلي' : 'Operational excellence KPIs'} Icon={Target}
        action={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <select
              value={cycleId ?? ''}
              onChange={e => setCycleId(Number(e.target.value))}
              className="ndi-mini-select" style={{ minWidth: 240 }}>
              {cycles.map(c => (
                <option key={c.id} value={c.id}>
                  {isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn} ({c.reportingYear})
                </option>
              ))}
            </select>
            <Button size="sm" variant="primary" Icon={Plus} disabled={!cycleId} onClick={() => setOpenNew(true)}>
              {isAr ? 'مؤشّر جديد' : 'New KPI'}
            </Button>
          </div>
        }>
        {agg && (
          <div className="kpi-grid">
            <Kpi Icon={Target}       label={isAr ? 'نقاط التميز التشغيلي' : 'OpEx score'} value={`${Number(agg.opexScorePct).toFixed(0)}%`}
                 tone={agg.opexScorePct >= 80 ? 'success' : agg.opexScorePct >= 50 ? 'warning' : 'danger'} to="/ndi" />
            <Kpi Icon={FileText}     label={isAr ? 'إجمالي المؤشّرات' : 'Total KPIs'}    value={agg.total}                              tone="primary" to="/ndi" />
            <Kpi Icon={CheckCircle2} label={isAr ? 'مُحقَّق'         : 'Achieved'}      value={agg.achievedCount}                      tone="success" to="/ndi" />
            <Kpi Icon={CheckCircle2} label={isAr ? 'على المسار'    : 'On track'}      value={agg.byStatus.ON_TRACK ?? 0}             tone="success" to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'مُعرَّض'         : 'At risk'}       value={agg.atRiskCount}                        tone="warning" to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'متأخّر'         : 'Behind plan'}   value={agg.behindPlanCount}                    tone={agg.behindPlanCount > 0 ? 'danger' : 'success'} to="/ndi" />
          </div>
        )}
      </Card>

      {loading
        ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        : items.length === 0
          ? <InlineEmpty>{isAr ? 'لا توجد مؤشّرات' : 'No KPIs yet'}</InlineEmpty>
          : Array.from(groupedByCategory.entries()).map(([category, rows]) => (
              <Card key={category} title={category} Icon={Target}>
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th>{isAr ? 'الكود' : 'Code'}</th>
                      <th>{isAr ? 'المؤشّر' : 'KPI'}</th>
                      <th className="ndi-th-w-220">{isAr ? 'التقدّم' : 'Progress'}</th>
                      <th>{isAr ? 'الحالة' : 'Status'}</th>
                      <th>{isAr ? 'آخر تحديث' : 'Last updated'}</th>
                      <th className="ndi-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map(k => {
                      const pct = progressFraction(k) * 100;
                      const barColor = k.status === 'ACHIEVED' || k.status === 'ON_TRACK' ? 'var(--success)'
                                     : k.status === 'AT_RISK' ? 'var(--warning)'
                                     : k.status === 'BEHIND_PLAN' ? 'var(--danger)'
                                     : 'var(--surface-3)';
                      return (
                        <tr key={k.id}>
                          <td className="mono text-2xs">{k.kpiCode ?? `#${k.id}`}</td>
                          <td>
                            <div className="ndi-cell-title">{isAr && k.indicatorNameAr ? k.indicatorNameAr : k.indicatorNameEn}</div>
                            <div className="text-2xs text-muted ndi-cell-sub">
                              {isAr ? 'الهدف' : 'Target'}: {k.target}{k.unit ?? ''}{' · '}
                              {isAr ? 'خط الأساس' : 'Baseline'}: {k.baseline ?? '—'}{k.unit ?? ''}
                            </div>
                          </td>
                          <td>
                            <div className="ndi-progress-wrap">
                              <div className="ndi-progress-track ndi-progress-track--lg">
                                <div className="ndi-progress-fill"
                                  style={{ width: `${Math.min(100, Math.max(0, pct))}%`, background: barColor }} />
                              </div>
                              <span className="mono text-2xs ndi-progress-label">
                                {k.actual ?? '—'}{k.unit ?? ''} / {k.target}{k.unit ?? ''}
                              </span>
                            </div>
                          </td>
                          <td><Badge tone={kpiStatusTone(k.status)}>{kpiStatusLabel(k.status, isAr ? 'ar' : 'en')}</Badge></td>
                          <td className="text-xs">{k.lastActualAt ?? <span className="text-muted">—</span>}</td>
                          <td className="ndi-text-end">
                            <div style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
                              <Button size="sm" variant="ghost" Icon={Pencil}
                                title={isAr ? 'تعديل' : 'Edit'} onClick={() => setEditItem(k)} />
                              <Button size="sm" variant="primary" Icon={ArrowRight} onClick={() => onUpdateActual(k)}>
                                {isAr ? 'تسجيل قراءة' : 'Record actual'}
                              </Button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </Card>
            ))}

      <FormModal<OpexForm>
        open={openNew}
        title={isAr ? 'تعريف مؤشّر تميّز تشغيلي' : 'Define an operational excellence KPI'}
        fields={opexFields(isAr)}
        initial={newInitial}
        submitLabel={isAr ? 'إضافة' : 'Add KPI'}
        size="lg"
        onClose={() => setOpenNew(false)}
        onSubmit={onCreate}
      />

      <FormModal<OpexForm>
        open={!!editItem}
        title={isAr ? 'تعديل مؤشّر التميّز التشغيلي' : 'Edit operational excellence KPI'}
        fields={opexFields(isAr)}
        initial={editInitial}
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        size="lg"
        onClose={() => setEditItem(null)}
        onSubmit={onUpdate}
      />
    </div>
  );
}

// ─── Governance Domains tab (slice 6) ────────────────────────────────

// All-string create-domain form shape. Numeric fields are number-kind in the
// FormModal (runtime values are numbers); the submit handler coerces them.
interface DomainForm {
  code:             string;
  nameEn:           string;
  nameAr:           string;
  weight:           string;
  sortOrder:        string;
  weightMaturity:   string;
  weightCompliance: string;
  weightOpex:       string;
  domainAliases:    string;
  ownerName:        string;
  ownerEmail:       string;
  descriptionEn:    string;
  descriptionAr:    string;
}

function domainFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'text', key: 'code', label: isAr ? 'الكود' : 'Code', span: 4, required: true, maxLength: 40,
      placeholder: isAr ? 'مثلاً: DATA_QUALITY' : 'e.g. DATA_QUALITY' },
    { kind: 'number', key: 'weight', label: isAr ? 'الوزن (0-100)' : 'Weight (0-100)', span: 4, min: 0, max: 100, required: true },
    { kind: 'number', key: 'sortOrder', label: isAr ? 'ترتيب الفرز' : 'Sort order', span: 4, min: 0, required: true },

    { kind: 'text', key: 'nameEn', label: isAr ? 'الاسم (إنجليزي)' : 'Name (EN)', span: 6, required: true, maxLength: 255,
      placeholder: isAr ? 'مثلاً: Data Quality' : 'e.g. Data Quality' },
    { kind: 'text', key: 'nameAr', label: isAr ? 'الاسم (عربي)' : 'Name (AR)', span: 6, maxLength: 255,
      placeholder: isAr ? 'مثلاً: جودة البيانات' : 'e.g. جودة البيانات' },

    { kind: 'number', key: 'weightMaturity', label: isAr ? 'وزن النضج (0-1)' : 'Maturity weight (0-1)', span: 4, min: 0, max: 1 },
    { kind: 'number', key: 'weightCompliance', label: isAr ? 'وزن الالتزام (0-1)' : 'Compliance weight (0-1)', span: 4, min: 0, max: 1 },
    { kind: 'number', key: 'weightOpex', label: isAr ? 'وزن التميّز (0-1)' : 'OpEx weight (0-1)', span: 4, min: 0, max: 1 },

    { kind: 'text', key: 'domainAliases', label: isAr ? 'أسماء بديلة (مفصولة بـ |)' : 'Aliases (pipe-separated)', span: 12,
      placeholder: isAr ? 'مثلاً: Data Quality|جودة البيانات|DQ' : 'e.g. Data Quality|DQ|جودة البيانات' },

    { kind: 'text', key: 'ownerName', label: isAr ? 'اسم المالك' : 'Owner name', span: 6, maxLength: 255 },
    { kind: 'text', key: 'ownerEmail', label: isAr ? 'بريد المالك' : 'Owner email', span: 6, maxLength: 255 },

    { kind: 'textarea', key: 'descriptionEn', label: isAr ? 'الوصف (إنجليزي)' : 'Description (EN)', span: 12, rows: 2 },
    { kind: 'textarea', key: 'descriptionAr', label: isAr ? 'الوصف (عربي)' : 'Description (AR)', span: 12, rows: 2 },
  ];
}

function DomainsTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [cycles, setCycles] = useState<NdiAssessmentCycle[]>([]);
  const [cycleId, setCycleId] = useState<number | null>(null);
  const [domains, setDomains] = useState<NdiGovernanceDomain[]>([]);
  const [rollup, setRollup] = useState<CycleRollup | null>(null);
  const [loading, setLoading] = useState(true);

  // Create-domain modal (slice 6). The page could already edit/mark/delete
  // domains but had no way to add one beyond the V71 seed catalogue, leaving
  // the createDomain client orphaned. New domains are seeded as placeholders
  // (isPlaceholder=true) so they inherit the page's "pending official SDAIA
  // import" narrative until an admin flips them via "Mark official".
  // Memoised initial keeps a stable identity for FormModal's open-reset effect.
  const [openNew, setOpenNew] = useState(false);
  const domainInitial = useMemo<DomainForm>(() => ({
    code: '', nameEn: '', nameAr: '',
    weight: '10', sortOrder: '100',
    weightMaturity: '0.40', weightCompliance: '0.40', weightOpex: '0.20',
    domainAliases: '', ownerName: '', ownerEmail: '',
    descriptionEn: '', descriptionAr: '',
  }), []);

  async function loadCycles() {
    try {
      const pg = await listCycles({ size: 100 });
      setCycles(pg.content);
      if (pg.content.length > 0 && !cycleId) setCycleId(pg.content[0].id!);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to load cycles');
    }
  }
  useEffect(() => { void loadCycles(); }, []);

  async function reload() {
    if (!cycleId) return;
    setLoading(true);
    // Per-call .catch() — domains and rollup load independently.
    const [list, r] = await Promise.all([
      listDomains(false).catch(() => [] as NdiGovernanceDomain[]),
      rollupForCycle(cycleId).catch(() => null as CycleRollup | null),
    ]);
    setDomains(list);
    setRollup(r);
    setLoading(false);
  }
  useEffect(() => { if (cycleId) void reload(); }, [cycleId]);

  async function onEditWeight(d: NdiGovernanceDomain) {
    const ans = await dialog.prompt({
      title: isAr ? 'تعديل وزن المجال' : 'Edit domain weight',
      label: isAr
        ? `النسبة الحالية للمجال "${d.nameAr ?? d.nameEn}" هي ${d.weight}. أدخل النسبة الجديدة (0-100).`
        : `Current weight for "${d.nameEn}" is ${d.weight}. Enter the new weight (0-100).`,
      type: 'number', required: true,
      initial: String(d.weight),
    });
    if (ans == null) return;
    const n = Number(ans);
    if (!isFinite(n) || n < 0 || n > 100) {
      toast.danger(isAr ? 'القيمة يجب أن تكون بين 0 و 100' : 'Value must be between 0 and 100');
      return;
    }
    try {
      await updateDomain(d.id!, { weight: n });
      toast.success(isAr ? 'تم تحديث الوزن' : 'Weight updated');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  // Create a brand-new governance domain. Client validation mirrors the
  // service contract: code + nameEn required; component weights (maturity /
  // compliance / opex) must each be in [0,1] and sum to 1.00 (±0.005).
  async function onCreate(v: DomainForm) {
    const code   = String(v.code ?? '').trim();
    const nameEn = String(v.nameEn ?? '').trim();
    if (!code) { toast.danger(isAr ? 'الكود مطلوب' : 'Code is required'); return; }
    if (!nameEn) { toast.danger(isAr ? 'الاسم الإنجليزي مطلوب' : 'English name is required'); return; }

    const weight = Number(v.weight);
    if (!Number.isFinite(weight) || weight < 0 || weight > 100) {
      toast.danger(isAr ? 'الوزن يجب أن يكون بين 0 و 100' : 'Weight must be between 0 and 100');
      return;
    }
    const sortOrder = Number(v.sortOrder);
    if (!Number.isFinite(sortOrder) || sortOrder < 0) {
      toast.danger(isAr ? 'ترتيب الفرز يجب أن يكون رقماً موجباً' : 'Sort order must be a non-negative number');
      return;
    }
    const wM = Number(v.weightMaturity), wC = Number(v.weightCompliance), wO = Number(v.weightOpex);
    for (const x of [wM, wC, wO]) {
      if (!Number.isFinite(x) || x < 0 || x > 1) {
        toast.danger(isAr ? 'أوزان المكوّنات يجب أن تكون بين 0 و 1' : 'Component weights must each be between 0 and 1');
        return;
      }
    }
    const sum = wM + wC + wO;
    if (Math.abs(sum - 1) > 0.005) {
      toast.danger(isAr
        ? `أوزان المكوّنات يجب أن يكون مجموعها 1.00 (الحالي ${sum.toFixed(2)})`
        : `Component weights must sum to 1.00 (currently ${sum.toFixed(2)})`);
      return;
    }
    try {
      await createDomain({
        code, nameEn,
        nameAr:        String(v.nameAr ?? '').trim() || undefined,
        weight, sortOrder,
        weightMaturity:   wM,
        weightCompliance: wC,
        weightOpex:       wO,
        domainAliases: String(v.domainAliases ?? '').trim() || undefined,
        ownerName:     String(v.ownerName ?? '').trim() || undefined,
        ownerEmail:    String(v.ownerEmail ?? '').trim() || undefined,
        descriptionEn: String(v.descriptionEn ?? '').trim() || undefined,
        descriptionAr: String(v.descriptionAr ?? '').trim() || undefined,
        isActive: true,
        isPlaceholder: true,
      });
      toast.success(isAr ? `تمت إضافة المجال ${code}` : `Domain ${code} created`);
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل إنشاء المجال' : 'Create failed'));
    }
  }

  async function onMarkOfficial(d: NdiGovernanceDomain) {
    const ok = await dialog.confirm({
      title: isAr ? 'تعليم كرسمي' : 'Mark as official',
      body: isAr
        ? 'سيتم إزالة شارة "عنصر مؤقت" من هذا المجال. تابع فقط إذا تم استيراد المكتبة الرسمية من سدايا.'
        : 'This removes the "Placeholder" flag from the domain. Only proceed if the official SDAIA library has been imported.',
      submitLabel: isAr ? 'تأكيد' : 'Confirm',
    });
    if (!ok) return;
    try {
      await markDomainOfficial(d.id!);
      toast.success(isAr ? 'تم التعليم كرسمي' : 'Marked as official');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onMarkPlaceholder(d: NdiGovernanceDomain) {
    try {
      await markDomainPlaceholder(d.id!);
      toast.success(isAr ? 'أُعيد المجال إلى حالة مؤقتة' : 'Reset to placeholder');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onDelete(d: NdiGovernanceDomain) {
    const ok = await dialog.confirm({
      title: isAr ? 'حذف المجال' : 'Delete domain',
      body: isAr
        ? `سيتم حذف المجال "${d.nameAr ?? d.nameEn}". هذا الإجراء مرن (حذف منطقي).`
        : `Domain "${d.nameEn}" will be soft-deleted.`,
      destructive: true,
      submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try {
      await deleteDomain(d.id!);
      toast.success(isAr ? 'تم الحذف' : 'Deleted');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Delete failed');
    }
  }

  const rollupByDomainId = useMemo(() => {
    const m = new Map<number, DomainRollup>();
    if (rollup) for (const r of rollup.domains) m.set(r.domainId, r);
    return m;
  }, [rollup]);

  const unmatched = useMemo(() => {
    if (!rollup || rollup.domains.length === 0) return [] as string[];
    return rollup.domains[0].unmatchedDomains ?? [];
  }, [rollup]);

  if (cycles.length === 0) {
    return <InlineEmpty>{isAr ? 'أنشئ دورة تقييم أولاً' : 'Create an assessment cycle first'}</InlineEmpty>;
  }

  const overallTone = ndiScoreTone(rollup?.overallNdiPct);
  const placeholderCount = domains.filter(d => d.isPlaceholder).length;

  return (
    <div className="ndi-stack">
      <Card title={isAr ? 'مجالات حوكمة البيانات' : 'Data governance domains'} Icon={Database}
        action={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <select
              value={cycleId ?? ''}
              onChange={e => setCycleId(Number(e.target.value))}
              className="ndi-mini-select" style={{ minWidth: 240 }}>
              {cycles.map(c => (
                <option key={c.id} value={c.id}>
                  {isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn} ({c.reportingYear})
                </option>
              ))}
            </select>
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenNew(true)}>
              {isAr ? 'مجال جديد' : 'New domain'}
            </Button>
          </div>
        }>
        {rollup && (
          <div className="kpi-grid">
            <Kpi Icon={Database}     label={isAr ? 'مؤشّر نضيء الإجمالي' : 'Overall NDI score'}
                 value={`${Number(rollup.overallNdiPct).toFixed(0)}% · ${ndiScoreLabel(rollup.overallNdiPct, isAr ? 'ar' : 'en')}`}
                 tone={overallTone === 'success' ? 'success' : overallTone === 'info' ? 'primary'
                       : overallTone === 'warning' ? 'warning' : overallTone === 'danger' ? 'danger' : 'primary'} to="/ndi" />
            <Kpi Icon={FileText}     label={isAr ? 'عدد المجالات' : 'Domains'}        value={domains.length}                    tone="primary" to="/ndi" />
            <Kpi Icon={CheckCircle2} label={isAr ? 'مجالات مفعّلة' : 'Active'}        value={domains.filter(d => d.isActive).length} tone="success" to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'مكتبة مؤقتة' : 'Placeholder'}     value={placeholderCount}                  tone={placeholderCount > 0 ? 'warning' : 'success'} to="/ndi" />
            <Kpi Icon={FileText}     label={isAr ? 'إجمالي الوزن' : 'Total weight'}   value={`${Number(rollup.totalWeight).toFixed(1)}`} tone="primary" to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'سلاسل غير مطابقة' : 'Unmatched strings'} value={unmatched.length}            tone={unmatched.length > 0 ? 'warning' : 'success'} to="/ndi" />
          </div>
        )}
      </Card>

      {placeholderCount > 0 && (
        <Card title={isAr ? 'تنبيه: مكتبة المجالات مؤقتة' : 'Heads-up: placeholder domain library'} Icon={AlertCircle}>
          <div className="ndi-card-lede--ink2">
            {isAr
              ? `يوجد ${placeholderCount} مجال مُعلَّم كـ "عنصر مؤقت — يتطلب استيراد مكتبة الضوابط الرسمية من سدايا/نضيء". لا تَدَّعِ الاكتمال الرسمي حتى يتم استيراد المكتبة الرسمية. بعد الاستيراد، علِّم كل مجال يدوياً عبر زر "تعليم كرسمي".`
              : `${placeholderCount} domain${placeholderCount === 1 ? ' is' : 's are'} flagged as "Placeholder — requires official SDAIA/NDI control library import". Do not claim official completeness until the official library is imported. After import, flip each domain to official via the "Mark official" action.`}
          </div>
        </Card>
      )}

      {unmatched.length > 0 && (
        <Card title={isAr ? 'سلاسل بلا تخصيص' : 'Unmatched domain strings'} Icon={AlertCircle}>
          <div className="ndi-card-lede--ink2 ndi-text-card-body--mb">
            {isAr
              ? 'هذه السلاسل تُستخدَم في تقييمات النضج أو الالتزام أو مؤشّرات التميز التشغيلي، لكنها لا تتطابق مع أي مجال في القائمة أعلاه. أضِفها إلى أسماء البدائل (aliases) في المجال المناسب.'
              : 'These strings are used by maturity, compliance or operational-excellence rows but match no domain in the catalog above. Add them to the appropriate domain\'s aliases.'}
          </div>
          <div className="ndi-status-strip">
            {unmatched.map(s => (
              <span key={s} className="mono text-2xs ndi-stat-chip">{s}</span>
            ))}
          </div>
        </Card>
      )}

      {loading ? (
        <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
      ) : domains.length === 0 ? (
        <InlineEmpty>{isAr ? 'لا توجد مجالات' : 'No domains'}</InlineEmpty>
      ) : (
        <Card title={isAr ? 'تفصيل لكل مجال' : 'Per-domain breakdown'} Icon={Target}>
          <table className="grc-table">
            <thead>
              <tr>
                <th>{isAr ? 'الكود' : 'Code'}</th>
                <th>{isAr ? 'المجال' : 'Domain'}</th>
                <th className="ndi-th-w-70">{isAr ? 'الوزن' : 'Weight'}</th>
                <th>{isAr ? 'نضج' : 'Maturity'}</th>
                <th>{isAr ? 'التزام' : 'Compliance'}</th>
                <th>{isAr ? 'تميز' : 'OpEx'}</th>
                <th className="ndi-th-w-200">{isAr ? 'مجموع المجال' : 'Domain score'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th className="ndi-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
              </tr>
            </thead>
            <tbody>
              {domains.map(d => {
                const r = rollupByDomainId.get(d.id!);
                const blended = r?.blendedScorePct;
                const tone = ndiScoreTone(blended);
                const barColor = tone === 'success' ? 'var(--success)'
                               : tone === 'info'    ? 'var(--primary)'
                               : tone === 'warning' ? 'var(--warning)'
                               : tone === 'danger'  ? 'var(--danger)'
                               : 'var(--surface-3)';
                return (
                  <tr key={d.id}>
                    <td className="mono text-2xs">{d.code}</td>
                    <td>
                      <div className="ndi-cell-title">{isAr && d.nameAr ? d.nameAr : d.nameEn}</div>
                      {d.isPlaceholder && (
                        <div className="text-2xs ndi-cell-warn--warning">
                          {isAr ? 'عنصر مؤقت — يتطلب استيراد مكتبة سدايا' : 'Placeholder — requires official SDAIA import'}
                        </div>
                      )}
                    </td>
                    <td className="mono text-xs">{Number(d.weight).toFixed(1)}</td>
                    <td className="text-xs">
                      {r && r.maturityCount > 0
                        ? <>
                            <div>{r.maturityAvgLevel?.toFixed(2)} / 5</div>
                            <div className="text-2xs text-muted">{r.maturityCount} {isAr ? 'تقييم' : 'rows'}</div>
                          </>
                        : <span className="text-muted">—</span>}
                    </td>
                    <td className="text-xs">
                      {r && r.compliancePct != null
                        ? <>
                            <div>{r.compliancePct.toFixed(0)}%</div>
                            <div className="text-2xs text-muted">{r.complianceCount} {isAr ? 'تقييم' : 'rows'}</div>
                          </>
                        : r && r.complianceCount > 0
                          ? <span className="text-muted">{r.complianceCount} {isAr ? 'بلا نتيجة' : 'no score'}</span>
                          : <span className="text-muted">—</span>}
                    </td>
                    <td className="text-xs">
                      {r && r.opexScorePct != null
                        ? <>
                            <div>{r.opexScorePct.toFixed(0)}%</div>
                            <div className="text-2xs text-muted">{r.opexCount} {isAr ? 'مؤشّر' : 'kpis'}</div>
                          </>
                        : <span className="text-muted">—</span>}
                    </td>
                    <td>
                      {blended != null ? (
                        <div className="ndi-progress-wrap">
                          <div className="ndi-progress-track" style={{ width: 120 }}>
                            <div className="ndi-progress-fill"
                              style={{ width: `${Math.min(100, Math.max(0, blended))}%`, background: barColor }} />
                          </div>
                          <span className="mono text-2xs ndi-progress-label--sm">
                            {blended.toFixed(0)}%
                          </span>
                        </div>
                      ) : <span className="text-muted text-2xs">{isAr ? 'بلا بيانات' : 'No data'}</span>}
                    </td>
                    <td>
                      <Badge tone={
                        tone === 'success' ? 'success'
                        : tone === 'info'    ? 'info'
                        : tone === 'warning' ? 'warning'
                        : tone === 'danger'  ? 'danger' : 'neutral'
                      }>{ndiScoreLabel(blended, isAr ? 'ar' : 'en')}</Badge>
                    </td>
                    <td className="ndi-text-end">
                      <div className="ndi-actions-row ndi-actions-row--tight">
                        <Button size="sm" variant="ghost" Icon={Target} onClick={() => onEditWeight(d)}
                          title={isAr ? 'تعديل الوزن' : 'Edit weight'} />
                        {d.isPlaceholder
                          ? <Button size="sm" variant="ghost" Icon={Check} onClick={() => onMarkOfficial(d)}
                              title={isAr ? 'تعليم كرسمي' : 'Mark official'} />
                          : <Button size="sm" variant="ghost" Icon={ArchiveIcon} onClick={() => onMarkPlaceholder(d)}
                              title={isAr ? 'إعادة كحالة مؤقتة' : 'Reset to placeholder'} />}
                        <Button size="sm" variant="ghost" Icon={Trash2} onClick={() => onDelete(d)}
                          title={isAr ? 'حذف' : 'Delete'} />
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>
      )}

      <FormModal
        open={openNew}
        title={isAr ? 'مجال حوكمة جديد' : 'New governance domain'}
        fields={domainFields(isAr)}
        initial={domainInitial}
        size="lg"
        submitLabel={isAr ? 'إنشاء' : 'Create'}
        onClose={() => setOpenNew(false)}
        onSubmit={onCreate}
      />
    </div>
  );
}

// ─── Evidence Repository tab (slice 7) ───────────────────────────────

function EvidenceTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<NdiEvidenceItem[]>([]);
  const [agg, setAgg] = useState<NdiEvidenceAggregate | null>(null);
  const [expiring, setExpiring] = useState<NdiEvidenceItem[]>([]);
  const [typeFilter, setTypeFilter] = useState<NdiEvidenceType | ''>('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [supersedeTarget, setSupersedeTarget] = useState<NdiEvidenceItem | null>(null);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — evidence list, aggregate and expiring-soon
    // load independently. One missing endpoint shouldn't blank the tab.
    const [pg, a, exp] = await Promise.all([
      listEvidence({ size: 500,
        type: typeFilter || undefined,
        status: (statusFilter as 'PENDING' | 'ACTIVE' | 'EXPIRED' | 'SUPERSEDED' | 'REJECTED') || undefined })
        .catch(() => ({ content: [] as NdiEvidenceItem[], totalElements: 0 })),
      evidenceAggregate().catch(() => null as NdiEvidenceAggregate | null),
      runExpiryCheckSilently().then(() => /* re-run */ {/* noop */}).then(() =>
        import('../../lib/ndiEvidence').then(m => m.expiringSoon(60)))
        .catch(() => [] as NdiEvidenceItem[]),
    ]);
    setItems(pg.content);
    setAgg(a);
    setExpiring(exp);
    setLoading(false);
  }

  // Best-effort expiry flip — silently swallow errors so a misconfigured
  // permission doesn't crash the tab load.
  async function runExpiryCheckSilently() {
    try { await runExpiryCheck(); } catch { /* noop */ }
  }

  useEffect(() => { void reload(); }, [typeFilter, statusFilter]);

  async function onCreate(body: Partial<NdiEvidenceItem>) {
    try {
      await createEvidence(body);
      toast.success(isAr ? 'تم إضافة الدليل' : 'Evidence created');
      setShowCreate(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Create failed');
    }
  }

  async function onApprove(e: NdiEvidenceItem) {
    try {
      await approveEvidence(e.id!, 'admin');
      toast.success(isAr ? 'تم الاعتماد' : 'Approved');
      await reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Approve failed');
    }
  }

  async function onReject(e: NdiEvidenceItem) {
    const reason = await dialog.prompt({
      title: isAr ? 'سبب الرفض' : 'Rejection reason',
      label: isAr ? 'اشرح بإيجاز لماذا تم رفض هذا الدليل.' : 'Briefly explain why this evidence is being rejected.',
      type: 'textarea', required: true,
    });
    if (!reason) return;
    try {
      await rejectEvidence(e.id!, reason, 'admin');
      toast.success(isAr ? 'تم الرفض' : 'Rejected');
      await reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Reject failed');
    }
  }

  async function onDelete(e: NdiEvidenceItem) {
    const ok = await dialog.confirm({
      title: isAr ? 'حذف الدليل' : 'Delete evidence',
      body: isAr
        ? `سيتم حذف "${e.titleAr ?? e.titleEn}". الحذف منطقي (يمكن إستعادته من قاعدة البيانات).`
        : `"${e.titleEn}" will be soft-deleted.`,
      destructive: true,
      submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try {
      await deleteEvidence(e.id!);
      toast.success(isAr ? 'تم الحذف' : 'Deleted');
      await reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Delete failed');
    }
  }

  // Supersede an evidence item by a newer one already in the register: the old
  // flips to SUPERSEDED and the replacement records supersedesId. The picker
  // lists every other non-superseded item so a fresh upload can retire a stale
  // policy/report without deleting the audit trail.
  const supersedeCandidates = useMemo(
    () => items.filter(c => c.id !== supersedeTarget?.id && c.status !== 'SUPERSEDED'),
    [items, supersedeTarget],
  );
  const supersedeFields: FieldDef[] = useMemo(() => [
    { kind: 'select', key: 'newId', required: true, span: 12,
      label: isAr ? 'الدليل البديل (الأحدث)' : 'Replacement evidence (the newer item)',
      options: supersedeCandidates.map(c => ({
        value: String(c.id),
        label: `${c.evidenceCode ?? '#' + c.id} · ${isAr && c.titleAr ? c.titleAr : c.titleEn}`,
      })) },
  ], [supersedeCandidates, isAr]);

  async function onSupersede(values: Record<string, unknown>) {
    if (supersedeTarget?.id == null) return;
    const newId = Number(values.newId);
    if (!Number.isInteger(newId) || newId <= 0) {
      toast.danger(isAr ? 'اختر الدليل البديل' : 'Select a replacement'); return;
    }
    try {
      await supersedeEvidence(supersedeTarget.id, newId);
      toast.success(isAr ? 'تم الاستبدال' : 'Evidence superseded');
      setSupersedeTarget(null);
      await reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Supersede failed');
    }
  }

  async function onRunExpiry() {
    try {
      const r = await runExpiryCheck();
      toast.success(isAr
        ? `تم التحقق — ${r.flippedCount} دليلاً انتهت صلاحيته`
        : `Checked — ${r.flippedCount} evidence item${r.flippedCount === 1 ? '' : 's'} flipped to expired`);
      await reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Expiry check failed');
    }
  }

  const createFields: FieldDef[] = [
    { kind: 'text',   key: 'titleEn', required: true, span: 6, label: isAr ? 'العنوان (إنجليزي) *' : 'Title (EN) *' },
    { kind: 'text',   key: 'titleAr', span: 6, label: isAr ? 'العنوان (عربي)' : 'Title (AR)' },
    { kind: 'select', key: 'evidenceType', required: true, span: 4, label: isAr ? 'النوع *' : 'Type *',
      options: [
        { value: 'POLICY',      label: isAr ? 'سياسة' : 'Policy' },
        { value: 'PROCEDURE',   label: isAr ? 'إجراء' : 'Procedure' },
        { value: 'REPORT',      label: isAr ? 'تقرير' : 'Report' },
        { value: 'SCREENSHOT',  label: isAr ? 'لقطة شاشة' : 'Screenshot' },
        { value: 'ATTESTATION', label: isAr ? 'تصريح' : 'Attestation' },
        { value: 'CERTIFICATE', label: isAr ? 'شهادة' : 'Certificate' },
        { value: 'AUDIT_LOG',   label: isAr ? 'سجل تدقيق' : 'Audit log' },
        { value: 'DATASET',     label: isAr ? 'مجموعة بيانات' : 'Dataset' },
        { value: 'OTHER',       label: isAr ? 'أخرى' : 'Other' },
      ] },
    { kind: 'select', key: 'classification', required: true, span: 4, label: isAr ? 'التصنيف *' : 'Classification *',
      options: [
        { value: 'PUBLIC',       label: isAr ? 'عام' : 'Public' },
        { value: 'INTERNAL',     label: isAr ? 'داخلي' : 'Internal' },
        { value: 'CONFIDENTIAL', label: isAr ? 'سرّي' : 'Confidential' },
        { value: 'RESTRICTED',   label: isAr ? 'مقيَّد' : 'Restricted' },
      ] },
    { kind: 'number', key: 'cycleId', span: 4, label: isAr ? 'معرّف الدورة' : 'Cycle ID' },
    { kind: 'text',     key: 'fileRef',       span: 12, label: isAr ? 'مرجع الملف / رابط' : 'File reference / URL' },
    { kind: 'text',     key: 'ownerName',     span: 6,  label: isAr ? 'المالك' : 'Owner' },
    { kind: 'text',     key: 'ownerEmail',    span: 6,  label: isAr ? 'بريد المالك' : 'Owner email' },
    { kind: 'date',     key: 'effectiveDate', span: 6,  label: isAr ? 'تاريخ السريان' : 'Effective date' },
    { kind: 'date',     key: 'expiryDate',    span: 6,  label: isAr ? 'تاريخ الانتهاء' : 'Expiry date' },
    { kind: 'text',     key: 'tags',          span: 12, label: isAr ? 'وسوم (مفصولة بـ |)' : 'Tags (pipe-separated)' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2, label: isAr ? 'الوصف (إنجليزي)' : 'Description (EN)' },
    { kind: 'textarea', key: 'descriptionAr', span: 12, rows: 2, label: isAr ? 'الوصف (عربي)' : 'Description (AR)' },
  ];

  return (
    <div className="ndi-stack">
      <Card title={isAr ? 'مستودع الأدلة' : 'Evidence repository'} Icon={Paperclip}
        action={
          <div className="ndi-actions-inline">
            <Button size="sm" variant="ghost" Icon={Check} onClick={onRunExpiry}>
              {isAr ? 'تحقق من الانتهاء' : 'Check expiry'}
            </Button>
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => setShowCreate(true)}>
              {isAr ? 'إضافة دليل' : 'Add evidence'}
            </Button>
          </div>
        }>
        {agg && (
          <div className="kpi-grid">
            <Kpi Icon={Paperclip}    label={isAr ? 'إجمالي الأدلة' : 'Total'}        value={agg.total}            tone="primary" to="/ndi" />
            <Kpi Icon={CheckCircle2} label={isAr ? 'سارٍ'         : 'Active'}       value={agg.activeCount}      tone="success"
                 onClick={() => setStatusFilter('ACTIVE')} active={statusFilter === 'ACTIVE'} />
            <Kpi Icon={FileText}     label={isAr ? 'قيد المراجعة' : 'Pending'}      value={agg.pendingCount}     tone="primary"
                 onClick={() => setStatusFilter('PENDING')} active={statusFilter === 'PENDING'} />
            <Kpi Icon={AlertCircle}  label={isAr ? 'منتهي'        : 'Expired'}      value={agg.expiredCount}     tone={agg.expiredCount > 0 ? 'danger' : 'success'}
                 onClick={() => setStatusFilter('EXPIRED')} active={statusFilter === 'EXPIRED'} />
            <Kpi Icon={AlertCircle}  label={isAr ? 'قريب الانتهاء' : 'Expiring soon'} value={agg.expiringSoonCount}
                 tone={agg.expiringSoonCount > 0 ? 'warning' : 'success'} to="/ndi" />
            <Kpi Icon={Trash2}       label={isAr ? 'مرفوض'        : 'Rejected'}     value={agg.rejectedCount}    tone={agg.rejectedCount > 0 ? 'danger' : 'success'}
                 onClick={() => setStatusFilter('REJECTED')} active={statusFilter === 'REJECTED'} />
          </div>
        )}
      </Card>

      {expiring.length > 0 && (
        <Card title={isAr ? `أدلة تنتهي خلال 60 يوماً (${expiring.length})` : `Evidence expiring within 60 days (${expiring.length})`} Icon={AlertCircle}>
          <table className="grc-table">
            <thead>
              <tr>
                <th>{isAr ? 'الكود' : 'Code'}</th>
                <th>{isAr ? 'العنوان' : 'Title'}</th>
                <th>{isAr ? 'النوع' : 'Type'}</th>
                <th>{isAr ? 'تاريخ الانتهاء' : 'Expiry'}</th>
                <th>{isAr ? 'متبقٍ' : 'Days left'}</th>
              </tr>
            </thead>
            <tbody>
              {expiring.map(e => {
                const d = daysUntil(e.expiryDate);
                return (
                  <tr key={e.id}>
                    <td className="mono text-2xs">{e.evidenceCode ?? `#${e.id}`}</td>
                    <td>{isAr && e.titleAr ? e.titleAr : e.titleEn}</td>
                    <td className="text-xs">{evidenceTypeLabel(e.evidenceType, isAr ? 'ar' : 'en')}</td>
                    <td className="text-xs">{e.expiryDate}</td>
                    <td><Badge tone={d != null && d < 30 ? 'danger' : 'warning'}>{d ?? '—'}</Badge></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>
      )}

      <Card title={isAr ? 'جميع الأدلة' : 'All evidence'} Icon={FileText}
        action={
          <div className="ndi-actions-inline">
            <select value={typeFilter}
              onChange={e => setTypeFilter(e.target.value as NdiEvidenceType | '')}
              className="ndi-mini-select ndi-mini-select--w-140">
              <option value="">{isAr ? 'كل الأنواع' : 'All types'}</option>
              {(['POLICY','PROCEDURE','REPORT','SCREENSHOT','ATTESTATION','CERTIFICATE','AUDIT_LOG','DATASET','OTHER'] as NdiEvidenceType[])
                .map(t => <option key={t} value={t}>{evidenceTypeLabel(t, isAr ? 'ar' : 'en')}</option>)}
            </select>
            <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
              className="ndi-mini-select ndi-mini-select--w-140">
              <option value="">{isAr ? 'كل الحالات' : 'All statuses'}</option>
              <option value="PENDING">{isAr ? 'قيد المراجعة' : 'Pending'}</option>
              <option value="ACTIVE">{isAr ? 'سارٍ' : 'Active'}</option>
              <option value="EXPIRED">{isAr ? 'منتهٍ' : 'Expired'}</option>
              <option value="SUPERSEDED">{isAr ? 'مُستبدَل' : 'Superseded'}</option>
              <option value="REJECTED">{isAr ? 'مرفوض' : 'Rejected'}</option>
            </select>
          </div>
        }>
        {loading ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
         : items.length === 0 ? <InlineEmpty>{isAr ? 'لا توجد أدلة' : 'No evidence'}</InlineEmpty>
         : (
          <table className="grc-table">
            <thead>
              <tr>
                <th>{isAr ? 'الكود' : 'Code'}</th>
                <th>{isAr ? 'العنوان' : 'Title'}</th>
                <th>{isAr ? 'النوع' : 'Type'}</th>
                <th>{isAr ? 'التصنيف' : 'Classification'}</th>
                <th>{isAr ? 'المالك' : 'Owner'}</th>
                <th>{isAr ? 'سريان' : 'Effective'}</th>
                <th>{isAr ? 'انتهاء' : 'Expiry'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th className="ndi-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
              </tr>
            </thead>
            <tbody>
              {items.map(e => (
                <tr key={e.id}>
                  <td className="mono text-2xs">{e.evidenceCode ?? `#${e.id}`}</td>
                  <td>
                    <div className="ndi-cell-title">{isAr && e.titleAr ? e.titleAr : e.titleEn}</div>
                    {e.fileRef && (
                      <div className="text-2xs text-muted mono ndi-cell-sub">{e.fileRef}</div>
                    )}
                  </td>
                  <td className="text-xs">{evidenceTypeLabel(e.evidenceType, isAr ? 'ar' : 'en')}</td>
                  <td><Badge tone={classificationTone(e.classification)}>{classificationLabel(e.classification, isAr ? 'ar' : 'en')}</Badge></td>
                  <td className="text-xs">{e.ownerName ?? <span className="text-muted">—</span>}</td>
                  <td className="text-xs">{e.effectiveDate ?? <span className="text-muted">—</span>}</td>
                  <td className="text-xs">{e.expiryDate ?? <span className="text-muted">—</span>}</td>
                  <td><Badge tone={evidenceStatusTone(e.status)}>{evidenceStatusLabel(e.status, isAr ? 'ar' : 'en')}</Badge></td>
                  <td className="ndi-text-end">
                    <div className="ndi-actions-row ndi-actions-row--tight">
                      {e.status === 'PENDING' && (
                        <>
                          <Button size="sm" variant="ghost" Icon={Check} onClick={() => onApprove(e)}
                            title={isAr ? 'اعتماد' : 'Approve'} />
                          <Button size="sm" variant="ghost" Icon={Trash2} onClick={() => onReject(e)}
                            title={isAr ? 'رفض' : 'Reject'} />
                        </>
                      )}
                      {(e.status === 'ACTIVE' || e.status === 'EXPIRED') && (
                        <Button size="sm" variant="ghost" Icon={Replace} onClick={() => setSupersedeTarget(e)}
                          title={isAr ? 'استبدال بإصدار أحدث' : 'Supersede with a newer item'} />
                      )}
                      <Button size="sm" variant="ghost" Icon={Trash2} onClick={() => onDelete(e)}
                        title={isAr ? 'حذف' : 'Delete'} />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <FormModal
        open={showCreate}
        title={isAr ? 'إضافة دليل' : 'Add evidence'}
        fields={createFields}
        initial={{ classification: 'INTERNAL', evidenceType: 'POLICY' }}
        size="lg"
        submitLabel={isAr ? 'إضافة' : 'Create'}
        onClose={() => setShowCreate(false)}
        onSubmit={async (vals: Record<string, unknown>) => { await onCreate(vals as unknown as Partial<NdiEvidenceItem>); }}
      />

      <FormModal
        open={supersedeTarget !== null}
        title={supersedeTarget
          ? (isAr
              ? `استبدال الدليل · ${supersedeTarget.evidenceCode ?? '#' + supersedeTarget.id}`
              : `Supersede evidence · ${supersedeTarget.evidenceCode ?? '#' + supersedeTarget.id}`)
          : ''}
        fields={supersedeFields}
        initial={{ newId: '' }}
        size="md"
        submitLabel={isAr ? 'استبدال' : 'Supersede'}
        onClose={() => setSupersedeTarget(null)}
        onSubmit={onSupersede}
      />
    </div>
  );
}

// ─── Gaps + Corrective Actions tab (slice 8) ─────────────────────────

function GapsTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [cycles, setCycles] = useState<NdiAssessmentCycle[]>([]);
  const [cycleId, setCycleId] = useState<number | null>(null);
  const [items, setItems] = useState<NdiGap[]>([]);
  const [agg, setAgg] = useState<GapAggregate | null>(null);
  const [capasByGap, setCapasByGap] = useState<Record<number, NdiCorrectiveAction[]>>({});
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [statusFilter, setStatusFilter] = useState<GapStatus | ''>('');
  const [severityFilter, setSeverityFilter] = useState<GapSeverity | ''>('');
  const [loading, setLoading] = useState(true);
  const [showCreateGap, setShowCreateGap] = useState(false);

  async function loadCycles() {
    try {
      const pg = await listCycles({ size: 100 });
      setCycles(pg.content);
      if (pg.content.length > 0 && !cycleId) setCycleId(pg.content[0].id!);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to load cycles');
    }
  }
  useEffect(() => { void loadCycles(); }, []);

  async function reload() {
    if (!cycleId) return;
    setLoading(true);
    // Per-call .catch() — gaps list and aggregate load independently.
    const [pg, a] = await Promise.all([
      listGaps(cycleId, { status: statusFilter || undefined, severity: severityFilter || undefined, size: 500 })
        .catch(() => ({ content: [] as NdiGap[], totalElements: 0 })),
      gapsAggregate(cycleId).catch(() => null as GapAggregate | null),
    ]);
    setItems(pg.content);
    setAgg(a);
    setLoading(false);
  }
  useEffect(() => { if (cycleId) void reload(); }, [cycleId, statusFilter, severityFilter]);

  async function toggleExpand(g: NdiGap) {
    const id = g.id!;
    const nxt = new Set(expanded);
    if (nxt.has(id)) { nxt.delete(id); setExpanded(nxt); return; }
    nxt.add(id); setExpanded(nxt);
    if (!capasByGap[id]) {
      try {
        const list = await listCapas(id);
        setCapasByGap(prev => ({ ...prev, [id]: list }));
      } catch (e) {
        toast.danger(e instanceof ApiError ? e.message : 'Failed to load CAPAs');
      }
    }
  }

  async function refreshCapas(gapId: number) {
    try {
      const list = await listCapas(gapId);
      setCapasByGap(prev => ({ ...prev, [gapId]: list }));
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Reload failed');
    }
  }

  async function onCreateGap(body: Record<string, unknown>) {
    try {
      await createGap({ ...body, cycleId } as unknown as Partial<NdiGap>);
      toast.success(isAr ? 'تم إنشاء الفجوة' : 'Gap created');
      setShowCreateGap(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Create failed');
    }
  }

  async function onChangeGapStatus(g: NdiGap, status: GapStatus) {
    try {
      await updateGap(g.id!, { status });
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onDeleteGap(g: NdiGap) {
    const ok = await dialog.confirm({
      title: isAr ? 'حذف الفجوة' : 'Delete gap',
      body: isAr ? 'سيتم حذف الفجوة وكل إجراءاتها التصحيحية.' : 'The gap and all its corrective actions will be soft-deleted.',
      destructive: true,
      submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try {
      await deleteGap(g.id!);
      toast.success(isAr ? 'تم الحذف' : 'Deleted');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Delete failed');
    }
  }

  async function onAddCapa(g: NdiGap) {
    const action = await dialog.prompt({
      title: isAr ? 'إجراء تصحيحي جديد' : 'New corrective action',
      label: isAr ? 'اكتب وصف الإجراء التصحيحي.' : 'Describe the corrective action.',
      type: 'textarea', required: true,
    });
    if (!action) return;
    try {
      await createCapa(g.id!, {
        actionEn: action, actionType: 'CONTROL',
        status: 'NOT_STARTED', progressPct: 0,
      });
      toast.success(isAr ? 'تم إضافة الإجراء' : 'CAPA added');
      await refreshCapas(g.id!);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Add failed');
    }
  }

  async function onUpdateCapaProgress(c: NdiCorrectiveAction) {
    const ans = await dialog.prompt({
      title: isAr ? 'تحديث التقدّم' : 'Update progress',
      label: isAr ? `الحالي ${c.progressPct}%. أدخل النسبة الجديدة (0-100).` : `Current ${c.progressPct}%. New value (0-100).`,
      type: 'number', required: true,
      initial: String(c.progressPct),
    });
    if (ans == null) return;
    const n = Number(ans);
    if (n < 0 || n > 100) {
      toast.danger(isAr ? 'القيمة 0-100' : 'Must be 0..100');
      return;
    }
    const newStatus: CapaStatus = n >= 100 ? 'COMPLETED' : n > 0 ? 'IN_PROGRESS' : 'NOT_STARTED';
    try {
      await updateCapa(c.id!, { progressPct: n, status: newStatus });
      toast.success(isAr ? 'تم التحديث' : 'Updated');
      await refreshCapas(c.gapId);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onChangeCapaStatus(c: NdiCorrectiveAction, status: CapaStatus) {
    try {
      await updateCapa(c.id!, { status });
      await refreshCapas(c.gapId);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onDeleteCapa(c: NdiCorrectiveAction) {
    const ok = await dialog.confirm({
      title: isAr ? 'حذف الإجراء' : 'Delete CAPA',
      body: isAr ? 'سيتم حذف الإجراء التصحيحي. هل تريد المتابعة؟' : 'The corrective action will be soft-deleted.',
      destructive: true,
      submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try {
      await deleteCapa(c.id!);
      await refreshCapas(c.gapId);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Delete failed');
    }
  }

  const gapFields: FieldDef[] = useMemo(() => [
    { kind: 'text',     key: 'titleEn',  required: true, span: 6, label: isAr ? 'العنوان (إنجليزي) *' : 'Title (EN) *' },
    { kind: 'text',     key: 'titleAr',  span: 6, label: isAr ? 'العنوان (عربي)' : 'Title (AR)' },
    { kind: 'select',   key: 'severity', required: true, span: 4, label: isAr ? 'الخطورة *' : 'Severity *',
      options: [
        { value: 'CRITICAL', label: isAr ? 'حرج' : 'Critical' },
        { value: 'HIGH',     label: isAr ? 'مرتفع' : 'High' },
        { value: 'MEDIUM',   label: isAr ? 'متوسط' : 'Medium' },
        { value: 'LOW',      label: isAr ? 'منخفض' : 'Low' },
      ] },
    { kind: 'select', key: 'sourceType', span: 4, label: isAr ? 'المصدر' : 'Source',
      options: [
        { value: 'MANUAL',     label: isAr ? 'يدوي' : 'Manual' },
        { value: 'COMPLIANCE', label: isAr ? 'الالتزام' : 'Compliance' },
        { value: 'MATURITY',   label: isAr ? 'النضج' : 'Maturity' },
        { value: 'KPI',        label: isAr ? 'مؤشّر' : 'KPI' },
        { value: 'AUDIT',      label: isAr ? 'تدقيق' : 'Audit' },
      ] },
    { kind: 'text',     key: 'domain',      span: 4, label: isAr ? 'المجال' : 'Domain' },
    { kind: 'text',     key: 'ownerName',   span: 6, label: isAr ? 'المالك' : 'Owner' },
    { kind: 'date',     key: 'targetDate',  span: 6, label: isAr ? 'تاريخ الهدف' : 'Target date' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2, label: isAr ? 'الوصف (إنجليزي)' : 'Description (EN)' },
    { kind: 'textarea', key: 'rootCause',     span: 12, rows: 2, label: isAr ? 'السبب الجذري' : 'Root cause' },
  ], [isAr]);

  if (cycles.length === 0) {
    return <InlineEmpty>{isAr ? 'أنشئ دورة تقييم أولاً' : 'Create an assessment cycle first'}</InlineEmpty>;
  }

  return (
    <div className="ndi-stack">
      <Card title={isAr ? 'الفجوات والإجراءات التصحيحية' : 'Gaps & corrective actions'} Icon={AlertCircle}
        action={
          <div className="ndi-actions-inline">
            <select value={cycleId ?? ''} onChange={e => setCycleId(Number(e.target.value))}
              className="ndi-mini-select ndi-mini-select--w-220">
              {cycles.map(c => (
                <option key={c.id} value={c.id}>
                  {isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn} ({c.reportingYear})
                </option>
              ))}
            </select>
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => setShowCreateGap(true)}>
              {isAr ? 'فجوة جديدة' : 'New gap'}
            </Button>
          </div>
        }>
        {agg && (
          <div className="kpi-grid">
            <Kpi Icon={AlertCircle}  label={isAr ? 'إجمالي الفجوات' : 'Total gaps'} value={agg.total}              tone="primary" to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'حرجة مفتوحة'   : 'Critical open'} value={agg.criticalOpenCount}
                 tone={agg.criticalOpenCount > 0 ? 'danger' : 'success'} to="/ndi" />
            <Kpi Icon={ArrowRight}   label={isAr ? 'قيد المعالجة'   : 'In progress'} value={agg.inProgressCount}   tone="warning"
                 onClick={() => setStatusFilter('IN_PROGRESS')} active={statusFilter === 'IN_PROGRESS'} />
            <Kpi Icon={CheckCircle2} label={isAr ? 'محلولة/مغلقة'   : 'Resolved'}    value={agg.resolvedOrClosedCount} tone="success"
                 onClick={() => setStatusFilter('RESOLVED')} active={statusFilter === 'RESOLVED'} />
            <Kpi Icon={AlertCircle}  label={isAr ? 'إجراءات متأخرة' : 'Overdue CAPAs'} value={agg.overdueCount}
                 tone={agg.overdueCount > 0 ? 'danger' : 'success'} to="/ndi" />
            <Kpi Icon={Target}       label={isAr ? 'متوسط التقدّم' : 'Avg progress'} value={`${agg.avgRollupProgress}%`}
                 tone={agg.avgRollupProgress >= 70 ? 'success' : agg.avgRollupProgress >= 40 ? 'warning' : 'danger'} to="/ndi" />
          </div>
        )}
      </Card>

      <Card title={isAr ? 'تفاصيل الفجوات' : 'Gap details'} Icon={FileText}
        action={
          <div className="ndi-actions-inline">
            <select value={severityFilter} onChange={e => setSeverityFilter(e.target.value as GapSeverity | '')}
              className="ndi-mini-select ndi-mini-select--w-130">
              <option value="">{isAr ? 'كل الخطورات' : 'All severity'}</option>
              <option value="CRITICAL">{isAr ? 'حرج' : 'Critical'}</option>
              <option value="HIGH">{isAr ? 'مرتفع' : 'High'}</option>
              <option value="MEDIUM">{isAr ? 'متوسط' : 'Medium'}</option>
              <option value="LOW">{isAr ? 'منخفض' : 'Low'}</option>
            </select>
            <select value={statusFilter} onChange={e => setStatusFilter(e.target.value as GapStatus | '')}
              className="ndi-mini-select ndi-mini-select--w-140">
              <option value="">{isAr ? 'كل الحالات' : 'All statuses'}</option>
              <option value="OPEN">{isAr ? 'مفتوح' : 'Open'}</option>
              <option value="IN_PROGRESS">{isAr ? 'قيد المعالجة' : 'In progress'}</option>
              <option value="RESOLVED">{isAr ? 'محلول' : 'Resolved'}</option>
              <option value="ACCEPTED_RISK">{isAr ? 'خطر مقبول' : 'Accepted risk'}</option>
              <option value="CLOSED">{isAr ? 'مغلق' : 'Closed'}</option>
            </select>
          </div>
        }>
        {loading ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
         : items.length === 0 ? <InlineEmpty>{isAr ? 'لا توجد فجوات' : 'No gaps'}</InlineEmpty>
         : (
          <table className="grc-table">
            <thead>
              <tr>
                <th className="ndi-th-w-30" />
                <th>{isAr ? 'الكود' : 'Code'}</th>
                <th>{isAr ? 'العنوان' : 'Title'}</th>
                <th>{isAr ? 'الخطورة' : 'Severity'}</th>
                <th>{isAr ? 'المالك' : 'Owner'}</th>
                <th>{isAr ? 'الهدف' : 'Target'}</th>
                <th className="ndi-th-w-180">{isAr ? 'التقدّم' : 'Progress'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th className="ndi-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
              </tr>
            </thead>
            <tbody>
              {items.map(g => {
                const open = expanded.has(g.id!);
                const childCapas = capasByGap[g.id!] ?? [];
                const progColor = g.rollupProgress >= 70 ? 'var(--success)'
                                : g.rollupProgress >= 40 ? 'var(--warning)'
                                : 'var(--danger)';
                return (
                  <Fragment key={g.id}>
                    <tr>
                      <td>
                        <Button size="sm" variant="ghost" Icon={open ? ArchiveIcon : ArrowRight}
                          onClick={() => toggleExpand(g)} title={open ? '−' : '+'} />
                      </td>
                      <td className="mono text-2xs">{g.gapCode ?? `#${g.id}`}</td>
                      <td>
                        <div className="ndi-cell-title">{isAr && g.titleAr ? g.titleAr : g.titleEn}</div>
                        <div className="text-2xs text-muted ndi-cell-sub">
                          {g.domain ?? '—'} · {g.sourceType}
                        </div>
                      </td>
                      <td><Badge tone={severityTone(g.severity)}>{severityLabel(g.severity, isAr ? 'ar' : 'en')}</Badge></td>
                      <td className="text-xs">{g.ownerName ?? <span className="text-muted">—</span>}</td>
                      <td className="text-xs">{g.targetDate ?? <span className="text-muted">—</span>}</td>
                      <td>
                        <div className="ndi-progress-wrap">
                          <div className="ndi-progress-track">
                            <div className="ndi-progress-fill" style={{ width: `${g.rollupProgress}%`, background: progColor }} />
                          </div>
                          <span className="mono text-2xs">{g.rollupProgress}% · {g.capaCompletedCount}/{g.capaCount}</span>
                        </div>
                      </td>
                      <td>
                        <select value={g.status}
                          onChange={e => onChangeGapStatus(g, e.target.value as GapStatus)}
                          className="ndi-status-select">
                          <option value="OPEN">{gapStatusLabel('OPEN', isAr ? 'ar' : 'en')}</option>
                          <option value="IN_PROGRESS">{gapStatusLabel('IN_PROGRESS', isAr ? 'ar' : 'en')}</option>
                          <option value="RESOLVED">{gapStatusLabel('RESOLVED', isAr ? 'ar' : 'en')}</option>
                          <option value="ACCEPTED_RISK">{gapStatusLabel('ACCEPTED_RISK', isAr ? 'ar' : 'en')}</option>
                          <option value="CLOSED">{gapStatusLabel('CLOSED', isAr ? 'ar' : 'en')}</option>
                        </select>
                        <span className="ndi-marker">
                          <Badge tone={gapStatusTone(g.status)}>{gapStatusLabel(g.status, isAr ? 'ar' : 'en')}</Badge>
                        </span>
                      </td>
                      <td className="ndi-text-end">
                        <div className="ndi-actions-row ndi-actions-row--tight">
                          <Button size="sm" variant="ghost" Icon={Plus} onClick={() => onAddCapa(g)}
                            title={isAr ? 'إضافة إجراء' : 'Add CAPA'} />
                          <Button size="sm" variant="ghost" Icon={Trash2} onClick={() => onDeleteGap(g)}
                            title={isAr ? 'حذف' : 'Delete'} />
                        </div>
                      </td>
                    </tr>
                    {open && (
                      <tr key={`${g.id}-capas`}>
                        <td />
                        <td colSpan={8} className="ndi-expanded-cell">
                          {childCapas.length === 0
                            ? <div className="ndi-expanded-empty">
                                {isAr ? 'لا توجد إجراءات تصحيحية بعد.' : 'No corrective actions yet.'}
                              </div>
                            : <table className="grc-table ndi-expanded-table">
                                <thead>
                                  <tr>
                                    <th>{isAr ? 'الكود' : 'Code'}</th>
                                    <th>{isAr ? 'الإجراء' : 'Action'}</th>
                                    <th>{isAr ? 'النوع' : 'Type'}</th>
                                    <th>{isAr ? 'المالك' : 'Owner'}</th>
                                    <th>{isAr ? 'الهدف' : 'Target'}</th>
                                    <th>{isAr ? 'التقدّم' : 'Progress'}</th>
                                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                                    <th className="ndi-text-end">{isAr ? '' : ''}</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {childCapas.map(c => (
                                    <tr key={c.id}>
                                      <td className="mono text-2xs">{c.actionCode ?? `#${c.id}`}</td>
                                      <td className="text-xs">{isAr && c.actionAr ? c.actionAr : c.actionEn}</td>
                                      <td className="text-xs">{actionTypeLabel(c.actionType, isAr ? 'ar' : 'en')}</td>
                                      <td className="text-xs">{c.ownerName ?? <span className="text-muted">—</span>}</td>
                                      <td className="text-xs">{c.targetDate ?? <span className="text-muted">—</span>}</td>
                                      <td className="mono text-2xs">{c.progressPct}%</td>
                                      <td>
                                        <select value={c.status}
                                          onChange={e => onChangeCapaStatus(c, e.target.value as CapaStatus)}
                                          className="ndi-status-select">
                                          <option value="NOT_STARTED">{capaStatusLabel('NOT_STARTED', isAr ? 'ar' : 'en')}</option>
                                          <option value="IN_PROGRESS">{capaStatusLabel('IN_PROGRESS', isAr ? 'ar' : 'en')}</option>
                                          <option value="BLOCKED">{capaStatusLabel('BLOCKED', isAr ? 'ar' : 'en')}</option>
                                          <option value="COMPLETED">{capaStatusLabel('COMPLETED', isAr ? 'ar' : 'en')}</option>
                                          <option value="DEFERRED">{capaStatusLabel('DEFERRED', isAr ? 'ar' : 'en')}</option>
                                        </select>
                                        <span className="ndi-marker">
                                          <Badge tone={capaStatusTone(c.status)}>{capaStatusLabel(c.status, isAr ? 'ar' : 'en')}</Badge>
                                        </span>
                                      </td>
                                      <td className="ndi-text-end">
                                        <div className="ndi-actions-row ndi-actions-row--tight">
                                          <Button size="sm" variant="ghost" Icon={ArrowRight} onClick={() => onUpdateCapaProgress(c)}
                                            title={isAr ? 'تحديث التقدّم' : 'Update progress'} />
                                          <Button size="sm" variant="ghost" Icon={Trash2} onClick={() => onDeleteCapa(c)}
                                            title={isAr ? 'حذف' : 'Delete'} />
                                        </div>
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        )}
      </Card>

      <FormModal
        open={showCreateGap}
        title={isAr ? 'فجوة جديدة' : 'New gap'}
        fields={gapFields}
        initial={{ severity: 'MEDIUM', sourceType: 'MANUAL' }}
        size="lg"
        submitLabel={isAr ? 'إنشاء' : 'Create'}
        onClose={() => setShowCreateGap(false)}
        onSubmit={onCreateGap}
      />
    </div>
  );
}

void ((_a: CapaActionType) => _a);  // silence unused type-import warning

// ─── Improvement Plan tab (slice 9) ──────────────────────────────────

function ImprovementTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [cycles, setCycles] = useState<NdiAssessmentCycle[]>([]);
  const [cycleId, setCycleId] = useState<number | null>(null);
  const [plans, setPlans] = useState<NdiImprovementPlan[]>([]);
  const [currentPlan, setCurrentPlan] = useState<NdiImprovementPlan | null>(null);
  const [items, setItems] = useState<EnrichedPlanItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreatePlan, setShowCreatePlan] = useState(false);

  async function loadCycles() {
    try {
      const pg = await listCycles({ size: 100 });
      setCycles(pg.content);
      if (pg.content.length > 0 && !cycleId) setCycleId(pg.content[0].id!);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to load cycles');
    }
  }
  useEffect(() => { void loadCycles(); }, []);

  async function reload() {
    if (!cycleId) return;
    setLoading(true);
    try {
      // Recompute first so the displayed totals reflect the latest CAPA state.
      try { await recomputeAllPlans(); } catch { /* noop */ }
      const list = await listPlans(cycleId);
      setPlans(list);
      const first = list[0] ?? null;
      setCurrentPlan(first);
      if (first?.id) {
        const its = await listPlanItems(first.id);
        setItems(its);
      } else {
        setItems([]);
      }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل الخطط' : 'Failed to load plans'));
      setPlans([]); setItems([]); setCurrentPlan(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { if (cycleId) void reload(); }, [cycleId]);

  async function loadPlan(p: NdiImprovementPlan) {
    try {
      const [latest, its] = await Promise.all([getPlan(p.id!), listPlanItems(p.id!)]);
      setCurrentPlan(latest);
      setItems(its);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to load plan');
    }
  }

  async function onCreatePlan(body: Record<string, unknown>) {
    try {
      const created = await createPlan({ ...body, cycleId } as unknown as Partial<NdiImprovementPlan>);
      toast.success(isAr ? 'تم إنشاء الخطة' : 'Plan created');
      setShowCreatePlan(false);
      await reload();
      if (created.id) await loadPlan(created);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Create failed');
    }
  }

  async function onApprove() {
    if (!currentPlan) return;
    try {
      await approvePlan(currentPlan.id!, 'admin');
      toast.success(isAr ? 'تم الاعتماد' : 'Approved');
      await loadPlan(currentPlan);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Approve failed');
    }
  }

  async function onTransition(to: NdiPlanStatus) {
    if (!currentPlan) return;
    try {
      await transitionPlan(currentPlan.id!, to);
      toast.success(isAr ? 'تم التحديث' : `Moved to ${to}`);
      await loadPlan(currentPlan);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Transition failed');
    }
  }

  async function onDeletePlan() {
    if (!currentPlan) return;
    const ok = await dialog.confirm({
      title: isAr ? 'حذف الخطة' : 'Delete plan',
      body: isAr ? 'سيتم حذف الخطة وجميع بنودها.' : 'The plan and all its items will be soft-deleted.',
      destructive: true,
      submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try {
      await deletePlan(currentPlan.id!);
      toast.success(isAr ? 'تم الحذف' : 'Deleted');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Delete failed');
    }
  }

  async function onUpdateItemPriority(it: EnrichedPlanItem, priority: NdiPlanItemPriority) {
    try {
      await updatePlanItem(it.id, { priority });
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onUpdateItemQuarter(it: EnrichedPlanItem) {
    const ans = await dialog.prompt({
      title: isAr ? 'تحديث الربع' : 'Update quarter',
      label: isAr ? 'صيغة Y####-Q[1-4] (مثال: Y2026-Q3)' : 'Format Y####-Q[1-4] (e.g. Y2026-Q3)',
      type: 'text', required: true,
      initial: it.targetQuarter ?? '',
    });
    if (!ans) return;
    try {
      await updatePlanItem(it.id, { targetQuarter: ans });
      toast.success(isAr ? 'تم التحديث' : 'Updated');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onDeleteItem(it: EnrichedPlanItem) {
    const ok = await dialog.confirm({
      title: isAr ? 'إزالة البند' : 'Remove item',
      body: isAr ? 'سيتم إزالة هذا البند من الخطة (الإجراء التصحيحي يبقى).' : 'Item will be removed from the plan (the corrective action itself stays).',
      destructive: true,
      submitLabel: isAr ? 'إزالة' : 'Remove',
    });
    if (!ok) return;
    try {
      await deletePlanItem(it.id);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Delete failed');
    }
  }

  const planFields: FieldDef[] = useMemo(() => [
    { kind: 'text',     key: 'nameEn',       required: true, span: 6, label: isAr ? 'الاسم (إنجليزي) *' : 'Name (EN) *' },
    { kind: 'text',     key: 'nameAr',       span: 6,  label: isAr ? 'الاسم (عربي)' : 'Name (AR)' },
    { kind: 'date',     key: 'periodStart',  required: true, span: 3, label: isAr ? 'بداية الفترة *' : 'Period start *' },
    { kind: 'date',     key: 'periodEnd',    required: true, span: 3, label: isAr ? 'نهاية الفترة *' : 'Period end *' },
    { kind: 'text',     key: 'planCode',     span: 6, label: isAr ? 'رمز الخطة' : 'Plan code' },
    { kind: 'text',     key: 'ownerName',    span: 6, label: isAr ? 'المالك' : 'Owner' },
    { kind: 'number',   key: 'budgetTotal',  span: 6, label: isAr ? 'إجمالي الميزانية (ر.س.)' : 'Budget total (SAR)' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2, label: isAr ? 'الوصف (إنجليزي)' : 'Description (EN)' },
    { kind: 'textarea', key: 'descriptionAr', span: 12, rows: 2, label: isAr ? 'الوصف (عربي)' : 'Description (AR)' },
  ], [isAr]);

  // ─── Derived state ──────────────────────────────────────────────────
  // NOTE: every hook below MUST stay above the `cycles.length === 0` early
  // return — moving it back below would break React's Rules of Hooks
  // (different hook count on first render vs. once cycles populate) and
  // would crash the tab to a blank page.

  // Group items by target quarter (sorted by quarter string ascending).
  const itemsByQuarter = useMemo(() => {
    const m = new Map<string, EnrichedPlanItem[]>();
    for (const it of items) {
      const k = it.targetQuarter ?? '(unassigned)';
      if (!m.has(k)) m.set(k, []);
      m.get(k)!.push(it);
    }
    return new Map(Array.from(m.entries()).sort(([a], [b]) => a.localeCompare(b)));
  }, [items]);

  if (cycles.length === 0) {
    return <InlineEmpty>{isAr ? 'أنشئ دورة تقييم أولاً' : 'Create an assessment cycle first'}</InlineEmpty>;
  }

  const totalBudget = currentPlan?.budgetTotal ?? 0;
  const allocated = items.reduce((s, it) => s + (it.allocatedBudget ?? 0), 0);
  const completedBudget = items
    .filter(it => it.capaStatus === 'COMPLETED')
    .reduce((s, it) => s + (it.allocatedBudget ?? 0), 0);

  return (
    <div className="ndi-stack">
      <Card title={isAr ? 'خطة التحسين' : 'Improvement plan'} Icon={Target}
        action={
          <div className="ndi-actions-inline">
            <select value={cycleId ?? ''} onChange={e => setCycleId(Number(e.target.value))}
              className="ndi-mini-select ndi-mini-select--w-220">
              {cycles.map(c => (
                <option key={c.id} value={c.id}>
                  {isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn} ({c.reportingYear})
                </option>
              ))}
            </select>
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => setShowCreatePlan(true)}>
              {isAr ? 'خطة جديدة' : 'New plan'}
            </Button>
          </div>
        }>
        {plans.length === 0
          ? <InlineEmpty>{isAr ? 'لا توجد خطة لهذه الدورة بعد.' : 'No plan exists for this cycle yet.'}</InlineEmpty>
          : (
            <div className="ndi-plan-chip-row">
              {plans.map(p => (
                <button key={p.id}
                  onClick={() => loadPlan(p)}
                  className={`ndi-plan-chip${currentPlan?.id === p.id ? ' is-active' : ''}`}>
                  {p.planCode ?? '—'} · {isAr && p.nameAr ? p.nameAr : p.nameEn}
                </button>
              ))}
            </div>
          )}

        {currentPlan && (
          <div className="kpi-grid">
            <Kpi Icon={Target}       label={isAr ? 'الحالة' : 'Status'}        value={planStatusLabel(currentPlan.status, isAr ? 'ar' : 'en')}
                 tone={planStatusTone(currentPlan.status) === 'primary' ? 'primary'
                       : planStatusTone(currentPlan.status) === 'success' ? 'success'
                       : planStatusTone(currentPlan.status) === 'warning' ? 'warning'
                       : planStatusTone(currentPlan.status) === 'info' ? 'info' : 'primary'} to="/ndi" />
            <Kpi Icon={FileText}     label={isAr ? 'البنود' : 'Items'}         value={`${currentPlan.completedItems}/${currentPlan.totalItems}`} tone="primary" to="/ndi" />
            <Kpi Icon={ArrowRight}   label={isAr ? 'متوسط التقدّم' : 'Progress'} value={`${currentPlan.progressPct}%`}
                 tone={currentPlan.progressPct >= 70 ? 'success' : currentPlan.progressPct >= 40 ? 'warning' : 'danger'} to="/ndi" />
            <Kpi Icon={Database}     label={isAr ? 'إجمالي الميزانية' : 'Budget'}   value={totalBudget ? totalBudget.toLocaleString() : '—'} tone="primary" to="/ndi" />
            <Kpi Icon={CheckCircle2} label={isAr ? 'مُخصَّص' : 'Allocated'}    value={allocated.toLocaleString()} tone="info" to="/ndi" />
            <Kpi Icon={CheckCircle2} label={isAr ? 'مُسلَّم' : 'Delivered'}    value={completedBudget.toLocaleString()}
                 tone={completedBudget > 0 ? 'success' : 'primary'} to="/ndi" />
          </div>
        )}

        {currentPlan && (
          <div className="ndi-chip-set--row">
            {currentPlan.status === 'DRAFT' && (
              <Button size="sm" variant="primary" Icon={Check} onClick={onApprove}>
                {isAr ? 'اعتماد' : 'Approve'}
              </Button>
            )}
            {nextPlanStatuses(currentPlan.status).map(s => (
              <Button key={s} size="sm" variant="secondary" Icon={ArrowRight} onClick={() => onTransition(s)}>
                {planStatusLabel(s, isAr ? 'ar' : 'en')}
              </Button>
            ))}
            <Button size="sm" variant="ghost" Icon={Trash2} onClick={onDeletePlan}>
              {isAr ? 'حذف' : 'Delete'}
            </Button>
          </div>
        )}
      </Card>

      {loading ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
       : !currentPlan ? null
       : itemsByQuarter.size === 0
         ? <InlineEmpty>{isAr ? 'لا توجد بنود في الخطة' : 'No items in this plan yet'}</InlineEmpty>
         : Array.from(itemsByQuarter.entries()).map(([q, rows]) => (
            <Card key={q} title={`${q} · ${rows.length} ${isAr ? 'بنود' : 'items'}`} Icon={FileText}>
              <table className="grc-table">
                <thead>
                  <tr>
                    <th className="ndi-th-w-50">#</th>
                    <th>{isAr ? 'كود الإجراء' : 'CAPA code'}</th>
                    <th>{isAr ? 'الإجراء' : 'Action'}</th>
                    <th>{isAr ? 'الأولوية' : 'Priority'}</th>
                    <th>{isAr ? 'الميزانية' : 'Budget'}</th>
                    <th className="ndi-th-w-140">{isAr ? 'التقدّم' : 'Progress'}</th>
                    <th>{isAr ? 'حالة الإجراء' : 'CAPA status'}</th>
                    <th className="ndi-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map(it => {
                    const prog = it.capaProgressPct ?? 0;
                    const color = prog >= 70 ? 'var(--success)' : prog >= 40 ? 'var(--warning)' : 'var(--danger)';
                    return (
                      <tr key={it.id}>
                        <td className="mono text-2xs">{it.sequenceOrder}</td>
                        <td className="mono text-2xs">{it.actionCode ?? `#${it.capaId}`}</td>
                        <td className="text-xs">{isAr && it.actionAr ? it.actionAr : it.actionEn}</td>
                        <td>
                          <select value={it.priority}
                            onChange={e => onUpdateItemPriority(it, e.target.value as NdiPlanItemPriority)}
                            className="ndi-status-select">
                            <option value="CRITICAL">{priorityLabelPlan('CRITICAL', isAr ? 'ar' : 'en')}</option>
                            <option value="HIGH">{priorityLabelPlan('HIGH', isAr ? 'ar' : 'en')}</option>
                            <option value="MEDIUM">{priorityLabelPlan('MEDIUM', isAr ? 'ar' : 'en')}</option>
                            <option value="LOW">{priorityLabelPlan('LOW', isAr ? 'ar' : 'en')}</option>
                          </select>
                          <span className="ndi-marker">
                            <Badge tone={planPriorityTone(it.priority)}>{priorityLabelPlan(it.priority, isAr ? 'ar' : 'en')}</Badge>
                          </span>
                        </td>
                        <td className="mono text-2xs">{it.allocatedBudget?.toLocaleString() ?? '—'}</td>
                        <td>
                          <div className="ndi-progress-wrap">
                            <div className="ndi-progress-track" style={{ width: 80 }}>
                              <div className="ndi-progress-fill" style={{ width: `${prog}%`, background: color }} />
                            </div>
                            <span className="mono text-2xs">{prog}%</span>
                          </div>
                        </td>
                        <td>
                          {it.capaStatus
                            ? <Badge tone={capaStatusTone(it.capaStatus)}>{capaStatusLabel(it.capaStatus, isAr ? 'ar' : 'en')}</Badge>
                            : <span className="text-muted text-2xs">—</span>}
                        </td>
                        <td className="ndi-text-end">
                          <div className="ndi-actions-row ndi-actions-row--tight">
                            <Button size="sm" variant="ghost" Icon={ArrowRight} onClick={() => onUpdateItemQuarter(it)}
                              title={isAr ? 'تعديل الربع' : 'Edit quarter'} />
                            <Button size="sm" variant="ghost" Icon={Trash2} onClick={() => onDeleteItem(it)}
                              title={isAr ? 'إزالة' : 'Remove'} />
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </Card>
          ))}

      <FormModal
        open={showCreatePlan}
        title={isAr ? 'خطة تحسين جديدة' : 'New improvement plan'}
        fields={planFields}
        initial={{}}
        size="lg"
        submitLabel={isAr ? 'إنشاء' : 'Create'}
        onClose={() => setShowCreatePlan(false)}
        onSubmit={onCreatePlan}
      />
    </div>
  );
}

// ─── Submission Package tab (slice 10) ───────────────────────────────

function SubmissionTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [cycles, setCycles] = useState<NdiAssessmentCycle[]>([]);
  const [cycleId, setCycleId] = useState<number | null>(null);
  const [packages, setPackages] = useState<NdiSubmissionPackage[]>([]);
  const [current, setCurrent] = useState<NdiSubmissionPackage | null>(null);
  const [pf, setPf] = useState<Preflight | null>(null);
  const [loading, setLoading] = useState(true);

  async function loadCycles() {
    try {
      const pg = await listCycles({ size: 100 });
      setCycles(pg.content);
      if (pg.content.length > 0 && !cycleId) setCycleId(pg.content[0].id!);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to load cycles');
    }
  }
  useEffect(() => { void loadCycles(); }, []);

  async function reload() {
    if (!cycleId) return;
    setLoading(true);
    // Per-call .catch() — submission list and preflight load independently.
    const [list, p] = await Promise.all([
      listSubmissions(cycleId).catch(() => [] as NdiSubmissionPackage[]),
      fetchPreflight(cycleId).catch(() => null as Preflight | null),
    ]);
    setPackages(list);
    setCurrent(list[0] ?? null);
    setPf(p);
    setLoading(false);
  }
  useEffect(() => { if (cycleId) void reload(); }, [cycleId]);

  async function onCreate() {
    if (!cycleId) return;
    try {
      await createSubmission(cycleId, { preparerName: 'admin' });
      toast.success(isAr ? 'تم إنشاء الحزمة' : 'Package created');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Create failed');
    }
  }

  async function onRegenerate() {
    if (!current) return;
    try {
      await regenerateSubmission(current.id!);
      toast.success(isAr ? 'تم تحديث اللقطة' : 'Snapshot regenerated');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Regenerate failed');
    }
  }

  async function onFinalize() {
    if (!current) return;
    try {
      await finalizeSubmission(current.id!);
      toast.success(isAr ? 'تم التجهيز' : 'Package finalized');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Finalize failed');
    }
  }

  async function onApprove() {
    if (!current) return;
    try {
      await approveSubmission(current.id!, 'admin');
      toast.success(isAr ? 'تم الاعتماد' : 'Approved');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Approve failed');
    }
  }

  async function onSubmit() {
    if (!current) return;
    const attestation = await dialog.prompt({
      title: isAr ? 'نص الإقرار' : 'Attestation text',
      label: isAr
        ? 'أقرّ بصحّة البيانات المُقدَّمة وأنّها تمثل واقع الجهة في تاريخ التقديم. أدخل النص الإنجليزي للإقرار.'
        : 'Declaration that the data submitted is accurate and represents the entity\'s state at submission date. Enter the English attestation text.',
      type: 'textarea', required: true,
      initial: 'I, the undersigned, declare that the data submitted in this NDI package is accurate and represents the entity\'s state as of the submission date.',
    });
    if (!attestation) return;
    try {
      await submitSubmission(current.id!, {
        submitterName: 'admin',
        attestationEn: attestation,
      });
      toast.success(isAr ? 'تم التقديم لسدايا' : 'Submitted to SDAIA');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Submit failed');
    }
  }

  async function onAcknowledge() {
    if (!current) return;
    const ref = await dialog.prompt({
      title: isAr ? 'مرجع سدايا' : 'SDAIA reference',
      label: isAr ? 'أدخل المرجع الذي أعادته سدايا.' : 'Enter the reference returned by SDAIA.',
      type: 'text', required: true,
    });
    if (!ref) return;
    try {
      await acknowledgeSubmission(current.id!, ref);
      toast.success(isAr ? 'تم تسجيل الإقرار' : 'Acknowledgement recorded');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Acknowledge failed');
    }
  }

  async function onReject() {
    if (!current) return;
    const reason = await dialog.prompt({
      title: isAr ? 'سبب الرفض' : 'Rejection reason',
      label: isAr ? 'أدخل سبب الرفض الذي أعادته سدايا.' : 'Reason returned by SDAIA.',
      type: 'textarea', required: true,
    });
    if (!reason) return;
    try {
      await rejectSubmission(current.id!, reason);
      toast.success(isAr ? 'تم تسجيل الرفض' : 'Rejection recorded');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Reject failed');
    }
  }

  async function onReopen() {
    if (!current) return;
    try {
      await reopenSubmission(current.id!);
      toast.success(isAr ? 'تم فتح الحزمة من جديد' : 'Package reopened');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Reopen failed');
    }
  }

  async function onDelete() {
    if (!current) return;
    const ok = await dialog.confirm({
      title: isAr ? 'حذف الحزمة' : 'Delete package',
      body: isAr ? 'سيتم حذف الحزمة منطقياً.' : 'The package will be soft-deleted.',
      destructive: true,
      submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try {
      await deleteSubmission(current.id!);
      toast.success(isAr ? 'تم الحذف' : 'Deleted');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Delete failed');
    }
  }

  if (cycles.length === 0) {
    return <InlineEmpty>{isAr ? 'أنشئ دورة تقييم أولاً' : 'Create an assessment cycle first'}</InlineEmpty>;
  }

  return (
    <div className="ndi-stack">
      <Card title={isAr ? 'حزمة التقديم لسدايا' : 'SDAIA submission package'} Icon={Upload}
        action={
          <div className="ndi-actions-inline">
            <select value={cycleId ?? ''} onChange={e => setCycleId(Number(e.target.value))}
              className="ndi-mini-select ndi-mini-select--w-220">
              {cycles.map(c => (
                <option key={c.id} value={c.id}>
                  {isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn} ({c.reportingYear})
                </option>
              ))}
            </select>
            <Button size="sm" variant="primary" Icon={Plus} onClick={onCreate}>
              {isAr ? 'حزمة جديدة' : 'New package'}
            </Button>
          </div>
        }>
        {pf && (
          <div className="kpi-grid">
            <Kpi Icon={CheckCircle2} label={isAr ? 'جاهز للتجهيز' : 'Can finalize'}
                 value={pf.canFinalize ? (isAr ? 'نعم' : 'Yes') : (isAr ? 'لا' : 'No')}
                 tone={pf.canFinalize ? 'success' : 'danger'} to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'فجوات حرجة بلا إجراءات' : 'Critical w/o CAPA'}
                 value={pf.criticalGapsWithoutCapa}
                 tone={pf.criticalGapsWithoutCapa > 0 ? 'danger' : 'success'} to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'فجوات حرجة مفتوحة' : 'Critical open'}
                 value={pf.openCriticalGaps}
                 tone={pf.openCriticalGaps > 0 ? 'warning' : 'success'} to="/ndi" />
            <Kpi Icon={FileText}     label={isAr ? 'التزام غير معتمد' : 'Compliance unapproved'} value={pf.complianceNotApproved}
                 tone={pf.complianceNotApproved > 0 ? 'warning' : 'success'} to="/ndi" />
            <Kpi Icon={FileText}     label={isAr ? 'نضج غير معتمد' : 'Maturity unapproved'} value={pf.maturityNotApproved}
                 tone={pf.maturityNotApproved > 0 ? 'warning' : 'success'} to="/ndi" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'أدلة منتهية' : 'Expired evidence'} value={pf.expiredEvidence}
                 tone={pf.expiredEvidence > 0 ? 'danger' : 'success'} to="/ndi" />
          </div>
        )}
        {pf && pf.blockers.length > 0 && (
          <div className="ndi-banner ndi-banner--danger">
            <strong>{isAr ? 'حواجز قبل التجهيز:' : 'Blockers before finalize:'}</strong>
            <ul className="ndi-banner-list">
              {pf.blockers.map((b, i) => <li key={i}>{b}</li>)}
            </ul>
          </div>
        )}
        {pf && pf.warnings.length > 0 && (
          <div className="ndi-banner ndi-banner--warning ndi-banner--mt-sm">
            <strong>{isAr ? 'تنبيهات (لا تمنع التجهيز):' : 'Warnings (do not block finalize):'}</strong>
            <ul className="ndi-banner-list">
              {pf.warnings.map((w, i) => <li key={i}>{w}</li>)}
            </ul>
          </div>
        )}
      </Card>

      {packages.length === 0
        ? <InlineEmpty>{isAr ? 'لا توجد حزمة لهذه الدورة بعد. اضغط "حزمة جديدة" لإنشاء أوّل لقطة.' : 'No package for this cycle yet. Click "New package" to create the first snapshot.'}</InlineEmpty>
        : (
          <div className="ndi-plan-chip-row ndi-plan-chip-row--no-mb">
            {packages.map(p => (
              <button key={p.id} onClick={() => setCurrent(p)}
                className={`ndi-plan-chip${current?.id === p.id ? ' is-active' : ''}`}>
                {p.packageCode ?? `#${p.id}`} · v{p.version} · {submissionStatusLabel(p.status, isAr ? 'ar' : 'en')}
              </button>
            ))}
          </div>
        )}

      {current && (
        <Card title={`${current.packageCode ?? `#${current.id}`} · v${current.version}`} Icon={FileText}>
          <div className="kpi-grid">
            <Kpi Icon={Upload}      label={isAr ? 'الحالة' : 'Status'}
                 value={submissionStatusLabel(current.status, isAr ? 'ar' : 'en')}
                 tone={submissionStatusTone(current.status) === 'success' ? 'success'
                       : submissionStatusTone(current.status) === 'danger' ? 'danger'
                       : submissionStatusTone(current.status) === 'warning' ? 'warning'
                       : submissionStatusTone(current.status) === 'primary' ? 'primary'
                       : submissionStatusTone(current.status) === 'info' ? 'info' : 'primary'} to="/ndi" />
            <Kpi Icon={Database}    label={isAr ? 'مؤشّر نضيء' : 'NDI score'}
                 value={current.overallNdiPct != null ? `${Number(current.overallNdiPct).toFixed(0)}%` : '—'}
                 tone={current.overallNdiPct != null && current.overallNdiPct >= 70 ? 'success'
                       : current.overallNdiPct != null && current.overallNdiPct >= 40 ? 'warning' : 'danger'} to="/ndi" />
            <Kpi Icon={Paperclip}   label={isAr ? 'أدلة' : 'Evidence'}    value={current.totalEvidence}   tone="primary" to="/ndi" />
            <Kpi Icon={AlertCircle} label={isAr ? 'فجوات' : 'Gaps'}       value={`${current.criticalGaps}/${current.totalGaps}`}
                 tone={current.criticalGaps > 0 ? 'warning' : 'primary'} to="/ndi" />
            <Kpi Icon={Target}      label={isAr ? 'إجراءات' : 'Actions'}  value={`${current.completedActions}/${current.totalActions}`} tone="primary" to="/ndi" />
            <Kpi Icon={FileText}    label={isAr ? 'بصمة' : 'Checksum'}    value={current.manifestChecksum ? current.manifestChecksum.substring(0, 12) + '…' : '—'}
                 tone="primary" to="/ndi" />
          </div>

          {current.isPlaceholder && (
            <div className="ndi-banner ndi-banner--warning">
              <strong>{isAr ? 'تنبيه: مكتبة مؤقتة' : 'Heads-up: placeholder content'}</strong><br/>
              {isAr
                ? 'هذه الحزمة تستند إلى مجالات/إطار مُعلَّمة كـ "عنصر مؤقت — يتطلب استيراد مكتبة سدايا الرسمية". لا تَدَّعِ الاكتمال الرسمي حتى يتم استيراد المكتبة.'
                : 'This package is based on domains/framework flagged as "Placeholder — requires official SDAIA/NDI control library import". Do not claim official completeness until the official library is imported.'}
            </div>
          )}

          <div className="ndi-chip-set--row" style={{ marginTop: 16 }}>
            {(current.status === 'DRAFT' || current.status === 'READY') && (
              <Button size="sm" variant="ghost" Icon={ArrowRight} onClick={onRegenerate}>
                {isAr ? 'تحديث اللقطة' : 'Regenerate snapshot'}
              </Button>
            )}
            {current.status === 'DRAFT' && (
              <Button size="sm" variant="primary" Icon={Check} onClick={onFinalize}
                disabled={!pf?.canFinalize}>
                {isAr ? 'تجهيز (Ready)' : 'Finalize (Ready)'}
              </Button>
            )}
            {current.status === 'READY' && !current.approvedAt && (
              <Button size="sm" variant="primary" Icon={Check} onClick={onApprove}>
                {isAr ? 'اعتماد' : 'Approve'}
              </Button>
            )}
            {current.status === 'READY' && current.approvedAt && (
              <Button size="sm" variant="primary" Icon={Upload} onClick={onSubmit}>
                {isAr ? 'تقديم لسدايا' : 'Submit to SDAIA'}
              </Button>
            )}
            {current.status === 'SUBMITTED' && (
              <>
                <Button size="sm" variant="primary" Icon={Check} onClick={onAcknowledge}>
                  {isAr ? 'تسجيل الإقرار' : 'Record acknowledgement'}
                </Button>
                <Button size="sm" variant="ghost" Icon={Trash2} onClick={onReject}>
                  {isAr ? 'تسجيل الرفض' : 'Record rejection'}
                </Button>
              </>
            )}
            {current.status === 'REJECTED' && (
              <Button size="sm" variant="primary" Icon={ArrowRight} onClick={onReopen}>
                {isAr ? 'إعادة الفتح' : 'Reopen as DRAFT'}
              </Button>
            )}
            {(current.status === 'DRAFT' || current.status === 'READY' || current.status === 'REJECTED') && (
              <Button size="sm" variant="ghost" Icon={Trash2} onClick={onDelete}>
                {isAr ? 'حذف' : 'Delete'}
              </Button>
            )}
          </div>

          <div className="ndi-text-card-body">
            <div><strong>{isAr ? 'إعداد:' : 'Prepared:'}</strong> {current.preparerName ?? '—'}{current.preparedAt ? ` · ${current.preparedAt}` : ''}</div>
            {current.reviewedAt && <div><strong>{isAr ? 'مراجعة:' : 'Reviewed:'}</strong> {current.reviewedAt}</div>}
            {current.approvedAt && <div><strong>{isAr ? 'اعتماد:' : 'Approved:'}</strong> {current.approverName ?? '—'} · {current.approvedAt}</div>}
            {current.submittedAt && <div><strong>{isAr ? 'تقديم:' : 'Submitted:'}</strong> {current.submitterName ?? '—'} · {current.submittedAt}</div>}
            {current.acknowledgedAt && <div><strong>{isAr ? 'إقرار سدايا:' : 'SDAIA acknowledged:'}</strong> {current.sdaiaReference ?? '—'} · {current.acknowledgedAt}</div>}
            {current.rejectionReason && <div className="ndi-text-danger"><strong>{isAr ? 'سبب الرفض:' : 'Rejection reason:'}</strong> {current.rejectionReason}</div>}
          </div>

          {current.snapshotJson && (
            <details className="ndi-banner--mt-md">
              <summary className="ndi-details-summary">
                {isAr ? 'محتوى اللقطة (JSON)' : 'Snapshot JSON'}
              </summary>
              <pre className="ndi-pre-block" style={{ maxHeight: 320, overflow: 'auto' }}>{tryFormat(current.snapshotJson)}</pre>
            </details>
          )}
        </Card>
      )}
      {loading && <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>}
    </div>
  );
}

function tryFormat(s: string | undefined): string {
  if (!s) return '';
  try { return JSON.stringify(JSON.parse(s), null, 2); }
  catch { return s; }
}

// ─── Reports tab (slice 11) ──────────────────────────────────────────

// All 12 NDI report types — used to populate the "Save report" picker.
const ALL_REPORT_TYPES: NdiReportType[] = [
  'NDI_EXECUTIVE_SUMMARY', 'MATURITY_HEATMAP', 'COMPLIANCE_STATUS',
  'OPEX_KPI_DASHBOARD', 'GAP_REGISTER', 'CAPA_REGISTER',
  'IMPROVEMENT_PLAN_ROADMAP', 'EVIDENCE_INVENTORY', 'DOMAIN_ROLLUP',
  'CYCLE_HISTORY', 'AUDIT_TRAIL', 'SUBMISSION_PACKAGE_EXPORT',
];

// "Save report" definition form — every field a string. The server stamps
// org and (via @PrePersist) defaults format=JSON, language=EN, status=ACTIVE.
interface ReportForm {
  nameEn: string;
  nameAr: string;
  reportCode: string;
  reportType: string;
  format: string;
  language: string;
  status: string;
  descriptionEn: string;
  descriptionAr: string;
  scheduleCron: string;
  recipients: string;
  filtersJson: string;
}

function ReportsTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [cycles, setCycles] = useState<NdiAssessmentCycle[]>([]);
  const [cycleId, setCycleId] = useState<number | null>(null);
  const [defs, setDefs] = useState<NdiReportDefinition[]>([]);
  const [result, setResult] = useState<ReportResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [openNew, setOpenNew] = useState(false);
  const [editDef, setEditDef] = useState<NdiReportDefinition | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const pg = await listCycles({ size: 100 });
        setCycles(pg.content);
        if (pg.content.length > 0 && !cycleId) setCycleId(pg.content[0].id!);
      } catch (e) {
        toast.danger(e instanceof ApiError ? e.message : 'Failed to load cycles');
      }
    })();
  }, []);

  async function reload() {
    setLoading(true);
    try {
      setDefs(await listReports());
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل التقارير' : 'Failed to load reports'));
      setDefs([]);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  async function onRun(d: NdiReportDefinition) {
    if (!cycleId && d.reportType !== 'CYCLE_HISTORY') {
      toast.danger(isAr ? 'اختر دورة أولاً' : 'Pick a cycle first');
      return;
    }
    try {
      const r = await runReport(d.id!, d.reportType === 'CYCLE_HISTORY' ? undefined : cycleId!);
      setResult(r);
      toast.success(isAr ? 'تم التشغيل' : 'Report executed');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Run failed');
    }
  }

  async function onRunAdHoc(type: NdiReportType) {
    if (!cycleId && type !== 'CYCLE_HISTORY') {
      toast.danger(isAr ? 'اختر دورة أولاً' : 'Pick a cycle first');
      return;
    }
    try {
      const r = await runAdHocReport(type, type === 'CYCLE_HISTORY' ? undefined : cycleId!);
      setResult(r);
      toast.success(isAr ? 'تم التشغيل' : 'Ad-hoc report executed');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Run failed');
    }
  }

  async function onDelete(d: NdiReportDefinition) {
    const ok = await dialog.confirm({
      title: isAr ? 'حذف التقرير' : 'Delete report',
      body: isAr ? 'سيتم حذف تعريف التقرير منطقياً.' : 'The report definition will be soft-deleted.',
      destructive: true, submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try {
      await deleteReport(d.id!);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Delete failed');
    }
  }

  // ── Save report definition (createReport) ─────────────────────────
  const L = isAr ? 'ar' : 'en';
  const reportTypeOpts = ALL_REPORT_TYPES.map(t => ({ value: t, label: reportTypeLabel(t, L) }));
  const formatOpts = (['JSON', 'PDF', 'XLSX', 'CSV'] as const).map(f => ({ value: f, label: f }));
  const languageOpts = [
    { value: 'EN',   label: isAr ? 'الإنجليزية' : 'English' },
    { value: 'AR',   label: isAr ? 'العربية'    : 'Arabic' },
    { value: 'BOTH', label: isAr ? 'كلاهما'     : 'Both' },
  ];
  const statusOpts = [
    { value: 'ACTIVE',   label: isAr ? 'نشط'    : 'Active' },
    { value: 'DISABLED', label: isAr ? 'معطّل'  : 'Disabled' },
    { value: 'ARCHIVED', label: isAr ? 'مؤرشف'  : 'Archived' },
  ];

  const reportFields: FieldDef[] = [
    { kind: 'text', key: 'nameEn', span: 6, required: true, maxLength: 255,
      label: isAr ? 'الاسم (إنجليزي)' : 'Name (English)' },
    { kind: 'text', key: 'nameAr', span: 6, maxLength: 255,
      label: isAr ? 'الاسم (عربي)' : 'Name (Arabic)' },
    { kind: 'select', key: 'reportType', span: 6, required: true,
      label: isAr ? 'نوع التقرير' : 'Report type', options: reportTypeOpts },
    { kind: 'text', key: 'reportCode', span: 6, maxLength: 40,
      label: isAr ? 'كود التقرير' : 'Report code', placeholder: 'RPT-EXEC-01' },
    { kind: 'select', key: 'format', span: 4,
      label: isAr ? 'الصيغة' : 'Format', options: formatOpts },
    { kind: 'select', key: 'language', span: 4,
      label: isAr ? 'اللغة' : 'Language', options: languageOpts },
    { kind: 'select', key: 'status', span: 4,
      label: isAr ? 'الحالة' : 'Status', options: statusOpts },
    { kind: 'text', key: 'scheduleCron', span: 6, maxLength: 100,
      label: isAr ? 'جدولة (cron)' : 'Schedule (cron)', placeholder: '0 0 1 * *' },
    { kind: 'text', key: 'recipients', span: 6,
      label: isAr ? 'المستلمون (بفواصل)' : 'Recipients (comma-separated)',
      placeholder: 'cdo@bank.com.sa, grc@bank.com.sa' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
    { kind: 'textarea', key: 'descriptionAr', span: 12, rows: 2,
      label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)' },
    { kind: 'textarea', key: 'filtersJson', span: 12, rows: 3,
      label: isAr ? 'مرشّحات (JSON اختياري) — مثال: { "component": "MATURITY" }'
                  : 'Filters (optional JSON) — e.g. { "component": "MATURITY" }' },
  ];

  const newInitial = useMemo<ReportForm>(() => ({
    nameEn: '', nameAr: '', reportCode: '',
    reportType: 'NDI_EXECUTIVE_SUMMARY', format: 'PDF', language: 'BOTH', status: 'ACTIVE',
    descriptionEn: '', descriptionAr: '', scheduleCron: '', recipients: '', filtersJson: '',
  }), []);

  async function onCreate(v: ReportForm) {
    const u = (s: string) => { const t = String(s ?? '').trim(); return t === '' ? undefined : t; };
    const nameEn = String(v.nameEn ?? '').trim();
    const reportType = String(v.reportType ?? '').trim();
    if (!nameEn)     { toast.danger(isAr ? 'الاسم (إنجليزي) مطلوب' : 'English name is required'); return; }
    if (!reportType) { toast.danger(isAr ? 'نوع التقرير مطلوب' : 'Report type is required'); return; }

    const filters = u(v.filtersJson);
    if (filters) {
      try { JSON.parse(filters); }
      catch { toast.danger(isAr ? 'مرشّحات JSON غير صالحة' : 'Filters must be valid JSON'); return; }
    }

    const body: Partial<NdiReportDefinition> = {
      nameEn,
      nameAr: u(v.nameAr),
      reportCode: u(v.reportCode),
      reportType: reportType as NdiReportType,
      format: (v.format || 'PDF') as NdiReportDefinition['format'],
      language: (v.language || 'BOTH') as NdiReportDefinition['language'],
      status: (v.status || 'ACTIVE') as NdiReportDefinition['status'],
      descriptionEn: u(v.descriptionEn),
      descriptionAr: u(v.descriptionAr),
      scheduleCron: u(v.scheduleCron),
      recipients: u(v.recipients),
      filtersJson: filters,
    };
    try {
      await createReport(body);
      toast.success(isAr ? 'حُفظ التقرير' : 'Report definition saved');
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Create failed');
    }
  }

  // ── Edit report definition (updateReport) ─────────────────────────
  // Pre-fill from the selected def; memoized on the def so FormModal's
  // reset effect re-keys when a different row is opened.
  const editInitial = useMemo<ReportForm>(() => ({
    nameEn: editDef?.nameEn ?? '',
    nameAr: editDef?.nameAr ?? '',
    reportCode: editDef?.reportCode ?? '',
    reportType: editDef?.reportType ?? 'NDI_EXECUTIVE_SUMMARY',
    format: editDef?.format ?? 'PDF',
    language: editDef?.language ?? 'BOTH',
    status: editDef?.status ?? 'ACTIVE',
    descriptionEn: editDef?.descriptionEn ?? '',
    descriptionAr: editDef?.descriptionAr ?? '',
    scheduleCron: editDef?.scheduleCron ?? '',
    recipients: editDef?.recipients ?? '',
    filtersJson: editDef?.filtersJson ?? '',
  }), [editDef]);

  async function onUpdate(v: ReportForm) {
    if (!editDef?.id) return;
    const u = (s: string) => { const t = String(s ?? '').trim(); return t === '' ? undefined : t; };
    const nameEn = String(v.nameEn ?? '').trim();
    const reportType = String(v.reportType ?? '').trim();
    if (!nameEn)     { toast.danger(isAr ? 'الاسم (إنجليزي) مطلوب' : 'English name is required'); return; }
    if (!reportType) { toast.danger(isAr ? 'نوع التقرير مطلوب' : 'Report type is required'); return; }

    const filters = u(v.filtersJson);
    if (filters) {
      try { JSON.parse(filters); }
      catch { toast.danger(isAr ? 'مرشّحات JSON غير صالحة' : 'Filters must be valid JSON'); return; }
    }

    const body: Partial<NdiReportDefinition> = {
      nameEn,
      nameAr: u(v.nameAr),
      reportCode: u(v.reportCode),
      reportType: reportType as NdiReportType,
      format: (v.format || 'PDF') as NdiReportDefinition['format'],
      language: (v.language || 'BOTH') as NdiReportDefinition['language'],
      status: (v.status || 'ACTIVE') as NdiReportDefinition['status'],
      descriptionEn: u(v.descriptionEn),
      descriptionAr: u(v.descriptionAr),
      scheduleCron: u(v.scheduleCron),
      recipients: u(v.recipients),
      filtersJson: filters,
    };
    try {
      await updateReport(editDef.id, body);
      toast.success(isAr ? 'حُدّث التقرير' : 'Report definition updated');
      setEditDef(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  return (
    <div className="ndi-stack">
      <Card title={isAr ? 'التقارير' : 'Reports'} Icon={FileText}
        action={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <select value={cycleId ?? ''} onChange={e => setCycleId(Number(e.target.value))}
              className="ndi-mini-select ndi-mini-select--w-220">
              {cycles.map(c => (
                <option key={c.id} value={c.id}>
                  {isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn} ({c.reportingYear})
                </option>
              ))}
            </select>
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenNew(true)}>
              {isAr ? 'حفظ تقرير' : 'Save report'}
            </Button>
          </div>
        }>
        {loading ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
         : defs.length === 0 ? <InlineEmpty>{isAr ? 'لا توجد تقارير محفوظة' : 'No saved reports'}</InlineEmpty>
         : (
          <table className="grc-table">
            <thead>
              <tr>
                <th>{isAr ? 'الكود' : 'Code'}</th>
                <th>{isAr ? 'الاسم' : 'Name'}</th>
                <th>{isAr ? 'النوع' : 'Type'}</th>
                <th>{isAr ? 'الصيغة' : 'Format'}</th>
                <th>{isAr ? 'آخر تشغيل' : 'Last run'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th className="ndi-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
              </tr>
            </thead>
            <tbody>
              {defs.map(d => (
                <tr key={d.id}>
                  <td className="mono text-2xs">{d.reportCode ?? `#${d.id}`}</td>
                  <td>{isAr && d.nameAr ? d.nameAr : d.nameEn}</td>
                  <td className="text-xs">{reportTypeLabel(d.reportType, isAr ? 'ar' : 'en')}</td>
                  <td className="text-xs">{d.format}</td>
                  <td className="text-xs">{d.lastRunAt ?? <span className="text-muted">—</span>}</td>
                  <td>
                    <Badge tone={d.lastRunStatus === 'SUCCESS' ? 'success'
                               : d.lastRunStatus === 'FAILED' ? 'danger' : 'neutral'}>
                      {d.lastRunStatus ?? (isAr ? 'لم يُشغَّل' : 'Not run')}
                    </Badge>
                  </td>
                  <td className="ndi-text-end">
                    <div className="ndi-actions-row ndi-actions-row--tight">
                      <Button size="sm" variant="primary" Icon={ArrowRight} onClick={() => onRun(d)}>
                        {isAr ? 'تشغيل' : 'Run'}
                      </Button>
                      <Button size="sm" variant="ghost" Icon={Pencil}
                        onClick={() => setEditDef(d)} title={isAr ? 'تعديل' : 'Edit'} />
                      <Button size="sm" variant="ghost" Icon={FileText}
                        onClick={() => downloadReport(d.id!, 'XLSX', cycleId ?? undefined).catch(e => toast.danger(String(e)))}
                        title="XLSX">XLSX</Button>
                      <Button size="sm" variant="ghost" Icon={FileText}
                        onClick={() => downloadReport(d.id!, 'PDF', cycleId ?? undefined).catch(e => toast.danger(String(e)))}
                        title="PDF">PDF</Button>
                      <Button size="sm" variant="ghost" Icon={Trash2} onClick={() => onDelete(d)} title={isAr ? 'حذف' : 'Delete'} />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Card title={isAr ? 'تشغيل تقرير عابر' : 'Ad-hoc report'} Icon={Target}>
        <div className="ndi-text-eyebrow-sm">
          {isAr ? 'اختر نوع التقرير وشغّله مباشرة دون حفظ التعريف.' : 'Pick a report type and run without saving a definition.'}
        </div>
        <div className="ndi-status-strip">
          {(['NDI_EXECUTIVE_SUMMARY','DOMAIN_ROLLUP','GAP_REGISTER','CAPA_REGISTER','EVIDENCE_INVENTORY','OPEX_KPI_DASHBOARD','CYCLE_HISTORY'] as NdiReportType[])
            .map(t => (
              <Button key={t} size="sm" variant="secondary" Icon={ArrowRight} onClick={() => onRunAdHoc(t)}>
                {reportTypeLabel(t, isAr ? 'ar' : 'en')}
              </Button>
            ))}
        </div>
      </Card>

      {result && (
        <Card title={`${isAr ? 'النتيجة:' : 'Result:'} ${reportTypeLabel(result.reportType, isAr ? 'ar' : 'en')}`} Icon={FileText}
          action={
            <div className="ndi-actions-inline">
              <Button size="sm" variant="secondary" Icon={FileText}
                onClick={() => downloadAdHocReport(result.reportType, 'XLSX', result.cycleId).catch(e => toast.danger(String(e)))}>
                XLSX
              </Button>
              <Button size="sm" variant="secondary" Icon={FileText}
                onClick={() => downloadAdHocReport(result.reportType, 'PDF', result.cycleId).catch(e => toast.danger(String(e)))}>
                PDF
              </Button>
              <Button size="sm" variant="secondary" Icon={FileText}
                onClick={() => downloadAdHocReport(result.reportType, 'CSV', result.cycleId).catch(e => toast.danger(String(e)))}>
                CSV
              </Button>
            </div>
          }>
          <div className="text-xs text-muted ndi-footer-note">
            {isAr ? 'تاريخ التوليد' : 'Generated at'}: {result.generatedAt} · {result.rows.length} {isAr ? 'صف' : 'rows'}
          </div>
          <details>
            <summary className="ndi-details-summary">
              {isAr ? 'الملخص' : 'Summary'}
            </summary>
            <pre className="ndi-pre" style={{ maxHeight: 200 }}>
              {JSON.stringify(result.summary, null, 2)}
            </pre>
          </details>
          <details className="ndi-banner--mt-sm">
            <summary className="ndi-details-summary">
              {isAr ? 'الصفوف' : 'Rows'}
            </summary>
            <pre className="ndi-pre" style={{ maxHeight: 320 }}>
              {JSON.stringify(result.rows, null, 2)}
            </pre>
          </details>
        </Card>
      )}

      <FormModal<ReportForm>
        open={openNew}
        title={isAr ? 'حفظ تعريف تقرير نضيء' : 'Save NDI report definition'}
        fields={reportFields}
        initial={newInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التقرير' : 'Save report'}
        onClose={() => setOpenNew(false)}
        onSubmit={onCreate}
      />

      <FormModal<ReportForm>
        open={!!editDef}
        title={isAr ? 'تعديل تعريف التقرير' : 'Edit report definition'}
        fields={reportFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditDef(null)}
        onSubmit={onUpdate}
      />
    </div>
  );
}

// ─── Dashboard tab (slice 12) ────────────────────────────────────────

function DashboardTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [cycles, setCycles] = useState<NdiAssessmentCycle[]>([]);
  const [cycleId, setCycleId] = useState<number | null>(null);
  const [snap, setSnap] = useState<DashboardSnapshot | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const pg = await listCycles({ size: 100 });
        setCycles(pg.content);
        if (pg.content.length > 0 && !cycleId) setCycleId(pg.content[0].id!);
      } catch (e) {
        toast.danger(e instanceof ApiError ? e.message : 'Failed to load cycles');
      }
    })();
  }, []);

  async function reload() {
    if (!cycleId) return;
    setLoading(true);
    try {
      setSnap(await getDashboard(cycleId));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل لوحة المعلومات' : 'Failed to load dashboard'));
      setSnap(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { if (cycleId) void reload(); }, [cycleId]);

  if (cycles.length === 0) {
    return <InlineEmpty>{isAr ? 'أنشئ دورة تقييم أولاً' : 'Create an assessment cycle first'}</InlineEmpty>;
  }
  if (loading || !snap) return <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>;

  const ndi = snap.overallNdiPct;
  const ndiTone = ndi == null ? 'primary'
    : ndi >= 80 ? 'success'
    : ndi >= 60 ? 'info'
    : ndi >= 40 ? 'warning' : 'danger';

  return (
    <div className="ndi-stack">
      <Card title={isAr ? 'نظرة عامة' : 'Overview'} Icon={Database}
        action={
          <select value={cycleId ?? ''} onChange={e => setCycleId(Number(e.target.value))}
            className="ndi-mini-select ndi-mini-select--w-220">
            {cycles.map(c => (
              <option key={c.id} value={c.id}>
                {isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn} ({c.reportingYear})
              </option>
            ))}
          </select>
        }>
        <div className="kpi-grid">
          <Kpi Icon={Database}     label={isAr ? 'مؤشّر نضيء' : 'NDI score'}
               value={ndi != null ? `${Number(ndi).toFixed(0)}%` : '—'} tone={ndiTone} to="/ndi" />
          <Kpi Icon={Target}       label={isAr ? 'نضج' : 'Maturity'}
               value={snap.maturityScorePct != null ? `${Number(snap.maturityScorePct).toFixed(0)}%` : '—'} tone="primary" to="/ndi" />
          <Kpi Icon={Check}        label={isAr ? 'التزام' : 'Compliance'}
               value={snap.compliancePct != null ? `${Number(snap.compliancePct).toFixed(0)}%` : '—'} tone="info" to="/ndi" />
          <Kpi Icon={Target}       label={isAr ? 'تميز تشغيلي' : 'OpEx'}
               value={snap.opexScorePct != null ? `${Number(snap.opexScorePct).toFixed(0)}%` : '—'} tone="primary" to="/ndi" />
          <Kpi Icon={AlertCircle}  label={isAr ? 'فجوات حرجة' : 'Critical open'} value={snap.criticalOpen}
               tone={snap.criticalOpen > 0 ? 'danger' : 'success'} to="/ndi" />
          <Kpi Icon={Paperclip}    label={isAr ? 'أدلة سارية' : 'Active evidence'} value={snap.evidenceActive} tone="success" to="/ndi" />
        </div>
      </Card>

      <Card title={isAr ? 'تقدّم العمل' : 'Work in flight'} Icon={ArrowRight}>
        <div className="kpi-grid">
          <Kpi Icon={FileText}     label={isAr ? 'نضج مُعتمَد' : 'Maturity approved'} value={`${snap.maturityApproved}/${snap.totalMaturity}`} tone="primary" to="/ndi" />
          <Kpi Icon={FileText}     label={isAr ? 'التزام مُعتمَد' : 'Compliance approved'} value={`${snap.complianceApproved}/${snap.totalCompliance}`} tone="primary" to="/ndi" />
          <Kpi Icon={Target}       label={isAr ? 'مؤشّرات مُحقَّقة' : 'KPIs achieved'} value={`${snap.opexAchieved}/${snap.totalOpex}`} tone="success" to="/ndi" />
          <Kpi Icon={AlertCircle}  label={isAr ? 'مؤشّرات متأخّرة' : 'KPIs behind plan'} value={snap.opexBehindPlan}
               tone={snap.opexBehindPlan > 0 ? 'danger' : 'success'} to="/ndi" />
          <Kpi Icon={AlertCircle}  label={isAr ? 'فجوات محلولة' : 'Gaps resolved'} value={`${snap.gapsResolved}/${snap.totalGaps}`} tone="success" to="/ndi" />
          <Kpi Icon={AlertCircle}  label={isAr ? 'إجراءات متأخّرة' : 'CAPAs overdue'} value={snap.capasOverdue}
               tone={snap.capasOverdue > 0 ? 'danger' : 'success'} to="/ndi" />
          <Kpi Icon={Target}       label={isAr ? 'إجراءات مكتملة' : 'CAPAs completed'} value={`${snap.capasCompleted}/${snap.totalCapas}`} tone="primary" to="/ndi" />
          <Kpi Icon={AlertCircle}  label={isAr ? 'أدلة قريبة الانتهاء' : 'Evidence expiring'} value={snap.evidenceExpiringSoon}
               tone={snap.evidenceExpiringSoon > 0 ? 'warning' : 'success'} to="/ndi" />
          <Kpi Icon={Upload}       label={isAr ? 'حزم مُعتمَدة من سدايا' : 'SDAIA acknowledged'} value={`${snap.submissionsAcknowledged}/${snap.submissionsTotal}`} tone="primary" to="/ndi" />
        </div>
      </Card>

      <Card title={isAr ? 'مجالات تحتاج تحسين' : 'Domains needing attention'} Icon={AlertCircle}>
        {snap.topUnderperforming.length === 0
          ? <InlineEmpty>{isAr ? 'كل المجالات في وضع جيد' : 'All domains in good shape'}</InlineEmpty>
          : (
            <table className="grc-table">
              <thead>
                <tr>
                  <th>{isAr ? 'الكود' : 'Code'}</th>
                  <th>{isAr ? 'المجال' : 'Domain'}</th>
                  <th>{isAr ? 'الوزن' : 'Weight'}</th>
                  <th>{isAr ? 'النتيجة' : 'Score'}</th>
                </tr>
              </thead>
              <tbody>
                {snap.topUnderperforming.map(d => (
                  <tr key={d.code}>
                    <td className="mono text-2xs">{d.code}</td>
                    <td>
                      <div className="ndi-cell-title">{isAr && d.nameAr ? d.nameAr : d.nameEn}</div>
                      {d.isPlaceholder && (
                        <div className="text-2xs ndi-cell-warn--warning">
                          {isAr ? 'عنصر مؤقت' : 'Placeholder'}
                        </div>
                      )}
                    </td>
                    <td className="mono text-xs">{Number(d.weight).toFixed(1)}</td>
                    <td>
                      <Badge tone={d.blendedScorePct >= 60 ? 'info'
                                  : d.blendedScorePct >= 40 ? 'warning' : 'danger'}>
                        {Number(d.blendedScorePct).toFixed(0)}%
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
      </Card>

      <Card title={isAr ? 'أعمال قادمة' : 'Upcoming work'} Icon={ArrowRight}>
        {snap.upcoming.length === 0
          ? <InlineEmpty>{isAr ? 'لا توجد أعمال قادمة خلال 60 يوماً' : 'No upcoming work within 60 days'}</InlineEmpty>
          : (
            <table className="grc-table">
              <thead>
                <tr>
                  <th>{isAr ? 'النوع' : 'Type'}</th>
                  <th>{isAr ? 'الكود' : 'Code'}</th>
                  <th>{isAr ? 'العنوان' : 'Title'}</th>
                  <th>{isAr ? 'الاستحقاق' : 'Due'}</th>
                  <th>{isAr ? 'متبقٍ' : 'Days left'}</th>
                </tr>
              </thead>
              <tbody>
                {snap.upcoming.map((u: UpcomingItem, i) => (
                  <tr key={i}>
                    <td><Badge tone={u.type === 'evidence' ? 'info' : 'warning'}>
                      {u.type === 'evidence' ? (isAr ? 'دليل' : 'Evidence') : (isAr ? 'إجراء' : 'CAPA')}
                    </Badge></td>
                    <td className="mono text-2xs">{u.code ?? '—'}</td>
                    <td className="text-xs">{isAr && u.titleAr ? u.titleAr : u.titleEn}</td>
                    <td className="text-xs">{u.dueDate}</td>
                    <td><Badge tone={u.daysUntil < 14 ? 'danger' : u.daysUntil < 30 ? 'warning' : 'info'}>
                      {u.daysUntil}
                    </Badge></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
      </Card>

      {Object.keys(snap.latestSubmission).length > 0 && (
        <Card title={isAr ? 'آخر حزمة تقديم' : 'Latest submission'} Icon={Upload}>
          <pre className="ndi-pre-block" style={{ maxHeight: 220, overflow: 'auto' }}>
            {JSON.stringify(snap.latestSubmission, null, 2)}
          </pre>
        </Card>
      )}
    </div>
  );
}

// ─── Settings tab (slice 13) ─────────────────────────────────────────

// "Define setting" form — every field a string. The server stamps org,
// enforces a unique settingKey per tenant, and type-validates valueText
// (INTEGER/DECIMAL/BOOLEAN must parse). isLocked is a 'YES'|'NO' string
// the handler coerces to boolean; valueType is one of NdiSettingType.
interface SettingForm {
  settingKey: string;
  category: string;
  valueType: string;        // NdiSettingType
  valueText: string;
  nameEn: string;
  nameAr: string;
  descriptionEn: string;
  descriptionAr: string;
  defaultValue: string;
  isLocked: string;         // 'YES' | 'NO'
}

function SettingsTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [grouped, setGrouped] = useState<Record<string, NdiSetting[]>>({});
  const [loading, setLoading] = useState(true);
  const [openNew, setOpenNew] = useState(false);

  async function reload() {
    setLoading(true);
    try {
      setGrouped(await listSettingsGrouped());
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل الإعدادات' : 'Failed to load settings'));
      setGrouped({});
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  async function onEdit(s: NdiSetting) {
    const ans = await dialog.prompt({
      title: isAr ? 'تعديل قيمة' : 'Edit value',
      label: `${isAr && s.nameAr ? s.nameAr : s.nameEn} · ${s.valueType}\n${isAr && s.descriptionAr ? s.descriptionAr : s.descriptionEn ?? ''}`,
      type: s.valueType === 'INTEGER' || s.valueType === 'DECIMAL' ? 'number'
          : s.valueType === 'JSON' ? 'textarea'
          : 'text',
      initial: s.valueText ?? '',
      required: true,
    });
    if (ans == null) return;
    try {
      await setSettingValue(s.settingKey, ans);
      toast.success(isAr ? 'تم التحديث' : 'Updated');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onReset(s: NdiSetting) {
    try {
      await resetSetting(s.settingKey);
      toast.success(isAr ? 'تمت إعادة التعيين' : 'Reset to default');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Reset failed');
    }
  }

  // ── Define setting (createSetting) ────────────────────────────────
  const L = isAr ? 'ar' : 'en';

  const categoryOpts = (['GENERAL', 'THRESHOLDS', 'WORKFLOW', 'ATTESTATION', 'GUARDRAIL'])
    .map(c => ({ value: c, label: settingCategoryLabel(c, L) }));
  const typeOpts = (['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'JSON'] as NdiSettingType[])
    .map(t => ({ value: t, label: t }));
  const lockedOpts = [
    { value: 'NO',  label: isAr ? 'لا'  : 'No' },
    { value: 'YES', label: isAr ? 'نعم' : 'Yes' },
  ];

  const settingFields: FieldDef[] = [
    { kind: 'text', key: 'settingKey', span: 6, required: true, maxLength: 80,
      label: isAr ? 'مفتاح الإعداد' : 'Setting key', placeholder: 'ndi.attestation.escalation_days' },
    { kind: 'select', key: 'category', span: 6, required: true,
      label: isAr ? 'الفئة' : 'Category', options: categoryOpts },
    { kind: 'text', key: 'nameEn', span: 6, required: true, maxLength: 255,
      label: isAr ? 'الاسم (إنجليزي)' : 'Name (English)' },
    { kind: 'text', key: 'nameAr', span: 6, maxLength: 255,
      label: isAr ? 'الاسم (عربي)' : 'Name (Arabic)' },
    { kind: 'select', key: 'valueType', span: 4, required: true,
      label: isAr ? 'نوع القيمة' : 'Value type', options: typeOpts },
    { kind: 'text', key: 'valueText', span: 4,
      label: isAr ? 'القيمة الحالية' : 'Current value' },
    { kind: 'text', key: 'defaultValue', span: 4,
      label: isAr ? 'القيمة الافتراضية' : 'Default value' },
    { kind: 'select', key: 'isLocked', span: 6,
      label: isAr ? 'مقفل' : 'Locked', options: lockedOpts },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
    { kind: 'textarea', key: 'descriptionAr', span: 12, rows: 2,
      label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)' },
  ];

  const newInitial = useMemo<SettingForm>(() => ({
    settingKey: '', category: 'GENERAL', valueType: 'STRING', valueText: '',
    nameEn: '', nameAr: '', descriptionEn: '', descriptionAr: '',
    defaultValue: '', isLocked: 'NO',
  }), []);

  async function onCreate(v: SettingForm) {
    const u = (s: string) => { const t = String(s ?? '').trim(); return t === '' ? undefined : t; };
    const key = String(v.settingKey ?? '').trim();
    const nameEn = String(v.nameEn ?? '').trim();
    const type = (String(v.valueType ?? 'STRING').trim() || 'STRING') as NdiSettingType;
    if (!key)    { toast.danger(isAr ? 'مفتاح الإعداد مطلوب' : 'Setting key is required'); return; }
    if (!nameEn) { toast.danger(isAr ? 'الاسم (إنجليزي) مطلوب' : 'English name is required'); return; }

    // Mirror the server's type validation so the user sees the problem inline,
    // before the round-trip — INTEGER/DECIMAL/BOOLEAN must parse; JSON must parse.
    const typed = (raw: string, labelEn: string, labelAr: string): boolean => {
      const s = String(raw ?? '').trim();
      if (s === '') return true;
      if (type === 'INTEGER' && !/^-?\d+$/.test(s)) {
        toast.danger(isAr ? `${labelAr} يجب أن تكون عددًا صحيحًا` : `${labelEn} must be a whole number`); return false;
      }
      if (type === 'DECIMAL' && !Number.isFinite(Number(s))) {
        toast.danger(isAr ? `${labelAr} يجب أن تكون رقمًا` : `${labelEn} must be a number`); return false;
      }
      if (type === 'BOOLEAN' && !/^(true|false)$/i.test(s)) {
        toast.danger(isAr ? `${labelAr} يجب أن تكون true أو false` : `${labelEn} must be true or false`); return false;
      }
      if (type === 'JSON') {
        try { JSON.parse(s); }
        catch { toast.danger(isAr ? `${labelAr} يجب أن تكون JSON صالحًا` : `${labelEn} must be valid JSON`); return false; }
      }
      return true;
    };
    if (!typed(v.valueText, 'Current value', 'القيمة الحالية')) return;
    if (!typed(v.defaultValue, 'Default value', 'القيمة الافتراضية')) return;

    const body: Partial<NdiSetting> = {
      settingKey: key,
      category: String(v.category ?? 'GENERAL').trim() || 'GENERAL',
      valueType: type,
      valueText: u(v.valueText),
      nameEn,
      nameAr: u(v.nameAr),
      descriptionEn: u(v.descriptionEn),
      descriptionAr: u(v.descriptionAr),
      defaultValue: u(v.defaultValue),
      isLocked: v.isLocked === 'YES',
    };
    try {
      await createSetting(body);
      toast.success(isAr ? 'أُضيف الإعداد' : 'Setting added');
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّرت الإضافة' : 'Create failed'));
    }
  }

  if (loading) return <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>;

  const categories = Object.entries(grouped);

  return (
    <div className="ndi-stack">
      <Card title={isAr ? 'إعدادات نضيء' : 'NDI settings'} Icon={Wrench}
        action={
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenNew(true)}>
            {isAr ? 'تعريف إعداد' : 'Define setting'}
          </Button>
        }>
        <div className="text-2xs text-muted">
          {isAr
            ? 'إعدادات تهيئة وحدة نضيء على مستوى المؤسسة — العتبات وسير العمل والإقرار والحماية. القيم المقفلة لا تُعدَّل ولا تُعاد لقيمتها الافتراضية.'
            : 'Organisation-wide NDI module configuration — thresholds, workflow, attestation and guardrails. Locked values cannot be edited or reset.'}
        </div>
      </Card>

      {categories.length === 0 && (
        <InlineEmpty>{isAr ? 'لا توجد إعدادات بعد — ابدأ بتعريف إعداد' : 'No settings yet — define one to start'}</InlineEmpty>
      )}

      {categories.map(([category, items]) => (
        <Card key={category} title={settingCategoryLabel(category, isAr ? 'ar' : 'en')} Icon={Wrench}>
          <table className="grc-table">
            <thead>
              <tr>
                <th>{isAr ? 'المفتاح' : 'Key'}</th>
                <th>{isAr ? 'الاسم' : 'Name'}</th>
                <th>{isAr ? 'النوع' : 'Type'}</th>
                <th>{isAr ? 'القيمة' : 'Value'}</th>
                <th>{isAr ? 'الافتراضي' : 'Default'}</th>
                <th className="ndi-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
              </tr>
            </thead>
            <tbody>
              {items.map(s => (
                <tr key={s.id}>
                  <td className="mono text-2xs">{s.settingKey}</td>
                  <td>
                    <div className="ndi-cell-title">{isAr && s.nameAr ? s.nameAr : s.nameEn}</div>
                    {(s.descriptionEn || s.descriptionAr) && (
                      <div className="text-2xs text-muted ndi-cell-sub">
                        {isAr && s.descriptionAr ? s.descriptionAr : s.descriptionEn}
                      </div>
                    )}
                  </td>
                  <td className="text-xs">{s.valueType}</td>
                  <td className="mono text-xs ndi-trunc-mono">
                    {s.valueText ?? <span className="text-muted">—</span>}
                  </td>
                  <td className="mono text-2xs text-muted">{s.defaultValue ?? '—'}</td>
                  <td className="ndi-text-end">
                    <div className="ndi-actions-row ndi-actions-row--tight">
                      <Button size="sm" variant="ghost" Icon={ArrowRight} onClick={() => onEdit(s)} disabled={s.isLocked} />
                      <Button size="sm" variant="ghost" Icon={ArchiveIcon} onClick={() => onReset(s)} disabled={s.isLocked} />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      ))}

      <FormModal<SettingForm>
        open={openNew}
        title={isAr ? 'تعريف إعداد نضيء' : 'Define NDI setting'}
        fields={settingFields}
        initial={newInitial}
        size="lg"
        submitLabel={isAr ? 'إضافة الإعداد' : 'Add setting'}
        onClose={() => setOpenNew(false)}
        onSubmit={onCreate}
      />
    </div>
  );
}

// ─── Audit trail tab (slice 14) ──────────────────────────────────────

function AuditTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [events, setEvents] = useState<NdiAuditEvent[]>([]);
  const [agg, setAgg] = useState<AuditAggregate | null>(null);
  const [actionFilter, setActionFilter] = useState<AuditAction | ''>('');
  const [severityFilter, setSeverityFilter] = useState<AuditSeverity | ''>('');
  const [entityTypeFilter, setEntityTypeFilter] = useState<string>('');
  const [loading, setLoading] = useState(true);

  async function reload() {
    setLoading(true);
    try {
      const [pg, a] = await Promise.all([
        searchAudit({
          action: actionFilter || undefined,
          severity: severityFilter || undefined,
          entityType: entityTypeFilter || undefined,
          size: 500,
        }),
        auditAggregate(),
      ]);
      setEvents(pg.content);
      setAgg(a);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل السجل' : 'Failed to load audit log'));
      setEvents([]); setAgg(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, [actionFilter, severityFilter, entityTypeFilter]);

  return (
    <div className="ndi-stack">
      <Card title={isAr ? 'مسار التدقيق' : 'Audit trail'} Icon={FileText}>
        {agg && (
          <div className="kpi-grid">
            <Kpi Icon={FileText}    label={isAr ? 'إجمالي الأحداث' : 'Total events'} value={agg.total} tone="primary" to="/ndi" />
            <Kpi Icon={AlertCircle} label={isAr ? 'تحذيرات' : 'Warnings'} value={agg.bySeverity.WARN ?? 0}
                 tone={(agg.bySeverity.WARN ?? 0) > 0 ? 'warning' : 'success'}
                 onClick={() => setSeverityFilter('WARN')} active={severityFilter === 'WARN'} />
            <Kpi Icon={AlertCircle} label={isAr ? 'أخطاء' : 'Errors'} value={agg.bySeverity.ERROR ?? 0}
                 tone={(agg.bySeverity.ERROR ?? 0) > 0 ? 'danger' : 'success'}
                 onClick={() => setSeverityFilter('ERROR')} active={severityFilter === 'ERROR'} />
            <Kpi Icon={CheckCircle2} label={isAr ? 'إنشاءات' : 'Creates'} value={agg.byAction.CREATE ?? 0} tone="info"
                 onClick={() => setActionFilter('CREATE')} active={actionFilter === 'CREATE'} />
            <Kpi Icon={ArrowRight}   label={isAr ? 'انتقالات' : 'Transitions'} value={agg.byAction.TRANSITION ?? 0} tone="info"
                 onClick={() => setActionFilter('TRANSITION')} active={actionFilter === 'TRANSITION'} />
            <Kpi Icon={Upload}       label={isAr ? 'تقديم' : 'Submits'} value={agg.byAction.SUBMIT ?? 0} tone="success"
                 onClick={() => setActionFilter('SUBMIT')} active={actionFilter === 'SUBMIT'} />
          </div>
        )}
      </Card>

      <Card title={isAr ? 'الأحداث' : 'Events'} Icon={FileText}
        action={
          <div className="ndi-actions-inline">
            <select value={entityTypeFilter} onChange={e => setEntityTypeFilter(e.target.value)}
              className="ndi-mini-select ndi-mini-select--w-130">
              <option value="">{isAr ? 'كل الكيانات' : 'All entities'}</option>
              {agg && Object.keys(agg.byEntityType).map(et => <option key={et} value={et}>{et}</option>)}
            </select>
            <select value={actionFilter} onChange={e => setActionFilter(e.target.value as AuditAction | '')}
              className="ndi-mini-select ndi-mini-select--w-130">
              <option value="">{isAr ? 'كل الإجراءات' : 'All actions'}</option>
              {agg && Object.keys(agg.byAction).map(a => <option key={a} value={a}>{a}</option>)}
            </select>
            <select value={severityFilter} onChange={e => setSeverityFilter(e.target.value as AuditSeverity | '')}
              className="ndi-mini-select ndi-mini-select--w-110">
              <option value="">{isAr ? 'كل المستويات' : 'All severity'}</option>
              <option value="INFO">INFO</option>
              <option value="WARN">WARN</option>
              <option value="ERROR">ERROR</option>
            </select>
          </div>
        }>
        {loading ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
         : events.length === 0 ? <InlineEmpty>{isAr ? 'لا توجد أحداث' : 'No events match the filters'}</InlineEmpty>
         : (
          <table className="grc-table">
            <thead>
              <tr>
                <th>{isAr ? 'الوقت' : 'Time'}</th>
                <th>{isAr ? 'الكيان' : 'Entity'}</th>
                <th>{isAr ? 'الكود' : 'Code'}</th>
                <th>{isAr ? 'الإجراء' : 'Action'}</th>
                <th>{isAr ? 'الانتقال' : 'Transition'}</th>
                <th>{isAr ? 'المستخدم' : 'Actor'}</th>
                <th>{isAr ? 'الخطورة' : 'Severity'}</th>
                <th>{isAr ? 'ملخص' : 'Summary'}</th>
              </tr>
            </thead>
            <tbody>
              {events.map(e => (
                <tr key={e.id}>
                  <td className="mono text-2xs">{e.occurredAt.substring(0, 19).replace('T', ' ')}</td>
                  <td className="text-xs">{e.entityType}</td>
                  <td className="mono text-2xs">{e.entityCode ?? `#${e.entityId ?? ''}`}</td>
                  <td><Badge tone="info">{e.action}</Badge></td>
                  <td className="text-2xs">
                    {e.fromState || e.toState
                      ? <span>{e.fromState ?? '—'} → {e.toState ?? '—'}</span>
                      : <span className="text-muted">—</span>}
                  </td>
                  <td className="text-xs">{e.actorUsername ?? <span className="text-muted">—</span>}</td>
                  <td><Badge tone={auditSeverityTone(e.severity)}>{e.severity}</Badge></td>
                  <td className="text-xs">{e.summary ?? <span className="text-muted">—</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  );
}

// ─── Tabs that aren't shipped yet (slices 2-14) ──────────────────────

const PENDING_SLICE: Record<NdiTab, number | null> = {
  dashboard: null, cycles: null, framework: null, maturity: null, compliance: null,
  opex: null, domains: null, evidence: null, gaps: null, improvement: null,
  submission: null, reports: null, settings: null, import: null, audit: null,
};

function NotYetShipped({ tab, isAr, t }: { tab: NdiTab; isAr: boolean; t: (k: string) => string }) {
  void t;  // reserved for translation-key migration in slice 15
  const slice = PENDING_SLICE[tab];
  return (
    <Card title={isAr ? 'هذه الواجهة قيد التطوير' : 'Tab not shipped yet'} Icon={Wrench}>
      <div className="ndi-card-lede--ink2">
        {isAr
          ? `هذه الواجهة جزء من شريحة لاحقة (الشريحة ${slice}) في خطة إدارة نضيء. الشريحة 1 (دورات التقييم) هي الأساس وقد شُحنت بالكامل في هذا الإصدار.`
          : `This tab is part of a later slice (slice ${slice}) in the NDI module rollout plan. Slice 1 (Assessment cycles) is the foundation and is fully shipped in this commit.`}
      </div>
      <div className="ndi-text-card-hint">
        <strong>{isAr ? 'ملاحظة' : 'Note'}:</strong>{' '}
        {isAr
          ? 'محتوى إطار نضيء الرسمي غير مستورَد بعد. كل ما يُعرَض هنا لاحقاً سيُصنَّف "عنصر مؤقت" حتى استيراد المكتبة الرسمية من سدايا.'
          : 'Official NDI framework content has not been imported yet. Anything rendered here will be flagged "Placeholder — requires official SDAIA/NDI control library import" until the official library lands.'}
      </div>
    </Card>
  );
}
