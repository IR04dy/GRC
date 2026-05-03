import { useEffect, useState } from 'react';
import {
  RefreshCw, Activity, TimerReset, Gauge, Filter, Download, Plus, BarChart3, Cpu, Calendar,
  CheckCheck, PlayCircle, RotateCcw, FileCheck2, Megaphone, Send,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { Progress } from '../../components/ui/Progress';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { STATUS_TONE, statusLabel } from '../../lib/labels';
import { useApiList } from '../../lib/useApiResource';
import {
  bcmApi, crisisCommsApi,
  type BcmPlanDto, type BcmPlanRequest,
  type BcmProcessDto, type BcmProcessRequest,
  type BcmBiaDto, type BcmBiaRequest,
  type BcmExerciseDto, type BcmExerciseRequest,
  type CrisisCommsTemplateDto, type CrisisCommsAudienceDto, type CrisisCommsDistributionDto,
  ApiError,
} from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { useToast } from '../../lib/toast';
import { BCM_PLANS, BCM_PROCESSES, BCM_BIA, BCM_EXERCISES } from './mockBcm';
import { ActiveWorkflowsPanel } from '../../components/workflows/ActiveWorkflowsPanel';
import { ProcessLifecycleBar } from '../../components/workflows/ProcessLifecycleBar';
import { BCM_WORKFLOWS } from '../../lib/mockWorkflows';
import { BCM_LIFECYCLES } from '../../lib/moduleLifecycles';

type TabId = 'overview' | 'plans' | 'processes' | 'bia' | 'graph' | 'exercises' | 'comms' | 'workflows';

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: 'Draft' }, { value: 'UNDER_REVIEW', label: 'Under Review' },
  { value: 'APPROVED', label: 'Approved' }, { value: 'EXPIRED', label: 'Expired' },
];
const PLAN_TYPE_OPTIONS = [
  { value: 'BUSINESS_CONTINUITY', label: 'Business Continuity' },
  { value: 'DISASTER_RECOVERY',   label: 'Disaster Recovery' },
  { value: 'CRISIS_MANAGEMENT',   label: 'Crisis Management' },
  { value: 'INCIDENT_RESPONSE',   label: 'Incident Response' },
];
const CRITICALITY_OPTIONS = [
  { value: 'CRITICAL', label: 'Critical' },
  { value: 'HIGH',     label: 'High' },
  { value: 'MEDIUM',   label: 'Medium' },
  { value: 'LOW',      label: 'Low' },
];
const IMPACT_OPTIONS = CRITICALITY_OPTIONS;
const EXERCISE_TYPE_OPTIONS = [
  { value: 'TABLETOP',      label: 'Tabletop' },
  { value: 'FUNCTIONAL',    label: 'Functional' },
  { value: 'FULL',          label: 'Full' },
  { value: 'COMMUNICATION', label: 'Communication' },
];

const PLAN_EMPTY: BcmPlanRequest = {
  planNameEn: '', planNameAr: '', planType: 'BUSINESS_CONTINUITY',
  version: '1.0', status: 'DRAFT', nextReviewDate: '',
};
const PROCESS_EMPTY: BcmProcessRequest = {
  processNameEn: '', processNameAr: '', ownerId: null, criticality: 'HIGH',
};
const BIA_EMPTY: BcmBiaRequest = {
  processId: null, assessmentDate: '', rtoHours: null, rpoHours: null, mtpdHours: null,
  overallImpact: 'MEDIUM', status: 'DRAFT', linkedRiskId: null,
};

// Exercise lifecycle status (matches backend CHECK constraint)
const EXERCISE_STATUS_OPTIONS = [
  { value: 'SCHEDULED',   label: 'Scheduled' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'COMPLETED',   label: 'Completed' },
  { value: 'CANCELLED',   label: 'Cancelled' },
];
const EXERCISE_EMPTY: BcmExerciseRequest = {
  exerciseNameEn: '', exerciseNameAr: '',
  exerciseType: 'TABLETOP', scenario: '',
  scheduledDate: '', executedDate: '',
  score: null, status: 'SCHEDULED',
  findings: '', capaCount: 0, facilitator: '',
  planId: null,
};

const IMPACT_TONE: Record<string, 'danger' | 'warning' | 'info' | 'success'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'success',
};

function planToReq(p: BcmPlanDto): BcmPlanRequest {
  return {
    planNameEn: p.planNameEn, planNameAr: p.planNameAr,
    planType: p.planType, version: p.version,
    status: p.status, nextReviewDate: p.nextReviewDate,
  };
}
function processToReq(p: BcmProcessDto): BcmProcessRequest {
  return {
    processNameEn: p.processNameEn, processNameAr: p.processNameAr,
    ownerId: p.ownerId ?? null, criticality: p.criticality,
  };
}
function biaToReq(b: BcmBiaDto): BcmBiaRequest {
  return {
    processId: b.processId ?? null, assessmentDate: b.assessmentDate,
    rtoHours: b.rtoHours ?? null, rpoHours: b.rpoHours ?? null, mtpdHours: b.mtpdHours ?? null,
    overallImpact: b.overallImpact, status: b.status, linkedRiskId: b.linkedRiskId ?? null,
  };
}

// Generic per-entity state hook (mirrors the pattern used in CyberPage).
function useEntityState<T>() {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<T | null>(null);
  const [confirmDel, setConfirmDel] = useState<T | null>(null);
  return {
    open, editing, confirmDel,
    startCreate: () => { setEditing(null); setOpen(true); },
    startEdit: (r: T) => { setEditing(r); setOpen(true); },
    askDelete: (r: T) => setConfirmDel(r),
    closeModal: () => { setOpen(false); setEditing(null); },
    closeConfirm: () => setConfirmDel(null),
  };
}

export function BcmPage() {
  const { t } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('overview');
  const [busy, setBusy] = useState(false);

  const planS = useEntityState<BcmPlanDto>();
  const procS = useEntityState<BcmProcessDto>();
  const biaS  = useEntityState<BcmBiaDto>();
  const exerS = useEntityState<BcmExerciseDto>();

  const plans = useApiList<BcmPlanDto>(() => bcmApi.plans(0, 100), BCM_PLANS);
  const procs = useApiList<BcmProcessDto>(() => bcmApi.processes(0, 100), BCM_PROCESSES);
  // /bia returns a plain array — adapt to the Page<T> shape useApiList expects.
  const bia   = useApiList<BcmBiaDto>(() => bcmApi.bia().then(arr => ({ content: arr })), BCM_BIA);
  const exercises = useApiList<BcmExerciseDto>(() => bcmApi.exercises(0, 200), BCM_EXERCISES);

  // ── Field defs (built per-render so labels translate via t()) ──
  const PLAN_FIELDS: FieldDef[] = [
    { kind: 'text',   key: 'planNameEn',     label: `${t('bcmFldPlanName')} (${t('fldTitleEnLbl').match(/\(([^)]+)\)/)?.[1] ?? 'EN'})`, required: true, maxLength: 200, span: 6 },
    { kind: 'text',   key: 'planNameAr',     label: `${t('bcmFldPlanName')} (${t('fldTitleArLbl').match(/\(([^)]+)\)/)?.[1] ?? 'AR'})`, maxLength: 200, span: 6 },
    { kind: 'select', key: 'planType',       label: t('bcmFldPlanType'), options: PLAN_TYPE_OPTIONS, span: 6 },
    { kind: 'text',   key: 'version',        label: t('fldVersion'), placeholder: '1.0', span: 6 },
    { kind: 'select', key: 'status',         label: t('fldStatusLbl'), options: STATUS_OPTIONS, span: 6 },
    { kind: 'date',   key: 'nextReviewDate', label: t('fldNextReviewDate'), span: 6 },
  ];

  const PROCESS_FIELDS: FieldDef[] = [
    { kind: 'text',   key: 'processNameEn', label: `${t('bcmFldProcessName')} (${t('fldTitleEnLbl').match(/\(([^)]+)\)/)?.[1] ?? 'EN'})`, required: true, maxLength: 200, span: 6 },
    { kind: 'text',   key: 'processNameAr', label: `${t('bcmFldProcessName')} (${t('fldTitleArLbl').match(/\(([^)]+)\)/)?.[1] ?? 'AR'})`, maxLength: 200, span: 6 },
    { kind: 'select', key: 'criticality',   label: t('fldCriticality'), options: CRITICALITY_OPTIONS, span: 6 },
    { kind: 'number', key: 'ownerId',       label: t('bcmFldProcessOwner'), min: 1, span: 6 },
  ];

  const processOptions = [
    { value: '', label: t('selectPlaceholder') },
    ...procs.data.map(p => ({ value: String(p.id), label: p.processNameEn })),
  ];

  const BIA_FIELDS: FieldDef[] = [
    { kind: 'select', key: 'processId',     label: t('bcmFldBiaProcess'), options: processOptions, span: 6 },
    { kind: 'date',   key: 'assessmentDate', label: t('fldAssessmentDate'), span: 6 },
    { kind: 'number', key: 'rtoHours',      label: t('bcmFldRtoHours'),  min: 0, span: 4 },
    { kind: 'number', key: 'rpoHours',      label: t('bcmFldRpoHours'),  min: 0, span: 4 },
    { kind: 'number', key: 'mtpdHours',     label: t('bcmFldMtpdHours'), min: 0, span: 4 },
    { kind: 'select', key: 'overallImpact', label: t('bcmFldOverallImpact'), options: IMPACT_OPTIONS, span: 6 },
    { kind: 'select', key: 'status',        label: t('fldStatusLbl'), options: STATUS_OPTIONS, span: 6 },
    { kind: 'number', key: 'linkedRiskId',  label: t('bcmFldLinkedRisk'), min: 1, span: 12 },
  ];

  const planSelectOptions = [
    { value: '', label: t('selectPlaceholder') },
    ...plans.data.map(p => ({ value: String(p.id), label: p.planNameEn })),
  ];
  const EXERCISE_FIELDS: FieldDef[] = [
    { kind: 'text',     key: 'exerciseNameEn', label: `${t('bcmFldExerciseName')} (${t('fldTitleEnLbl').match(/\(([^)]+)\)/)?.[1] ?? 'EN'})`, required: true, maxLength: 200, span: 6 },
    { kind: 'text',     key: 'exerciseNameAr', label: `${t('bcmFldExerciseName')} (${t('fldTitleArLbl').match(/\(([^)]+)\)/)?.[1] ?? 'AR'})`, maxLength: 200, span: 6 },
    { kind: 'select',   key: 'exerciseType',   label: t('bcmFldExerciseType'), options: EXERCISE_TYPE_OPTIONS, span: 6 },
    { kind: 'select',   key: 'status',         label: t('fldStatusLbl'), options: EXERCISE_STATUS_OPTIONS, span: 6 },
    { kind: 'date',     key: 'scheduledDate',  label: t('bcmFldExerciseDate'), span: 6 },
    { kind: 'date',     key: 'executedDate',   label: t('bcmFldExerciseExecuted'), span: 6 },
    { kind: 'number',   key: 'score',          label: t('bcmFldExerciseScore'), min: 0, max: 100, span: 6 },
    { kind: 'select',   key: 'planId',         label: t('bcmFldExerciseLinkedPlan'), options: planSelectOptions, span: 6 },
    { kind: 'text',     key: 'facilitator',    label: t('bcmFldExerciseFacilitator'), span: 12 },
    { kind: 'textarea', key: 'scenario',       label: t('bcmFldExerciseScenario'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'findings',       label: t('bcmFldExerciseFindings'), rows: 2, span: 12 },
  ];

  function exerciseToReq(e: BcmExerciseDto): BcmExerciseRequest {
    return {
      planId: e.planId ?? null,
      exerciseNameEn: e.exerciseNameEn, exerciseNameAr: e.exerciseNameAr,
      exerciseType: e.exerciseType, scenario: e.scenario,
      scheduledDate: e.scheduledDate, executedDate: e.executedDate,
      score: e.score ?? null, status: e.status,
      findings: e.findings, capaCount: e.capaCount ?? 0,
      facilitator: e.facilitator,
    };
  }

  const tabs: TabItem<TabId>[] = [
    { id: 'overview',  label: t('tabOverview') },
    { id: 'plans',     label: t('bcmTabPlans') },
    { id: 'processes', label: t('bcmTabProcesses') },
    { id: 'bia',       label: t('bcmTabBia') },
    { id: 'graph',     label: t('bcmTabDepGraph') },
    { id: 'exercises', label: t('bcmTabExercises') },
    { id: 'comms',     label: t('bcmTabComms') },
    { id: 'workflows', label: t('bcmTabWorkflows') ?? 'Workflows' },
  ];

  const planCols: Column<BcmPlanDto>[] = [
    { header: t('bcmPlanCol'),    cell: r => <span style={{ fontWeight: 600 }}>{r.planNameEn}</span> },
    { header: t('colType'),       cell: r => <Badge tone="info">{(r.planType ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colVersion'),    cell: r => r.version, mono: true },
    { header: t('bcmReviewCol'),  cell: r => r.nextReviewDate ?? '—', mono: true },
    { header: t('bcmStatusCol'),  cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(t, r.status)}</Badge> },
    { header: '', align: 'end', width: '160px',
      cell: r => {
        const extras: Array<{ Icon: typeof CheckCheck; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (r.status === 'DRAFT' || r.status === 'UNDER_REVIEW') {
          extras.push({ Icon: CheckCheck, onClick: () => approvePlan(r), title: t('bcmActApprovePlan'), tone: 'success' });
        }
        if (r.status === 'APPROVED') {
          extras.push({ Icon: PlayCircle, onClick: () => invokePlan(r), title: t('bcmActInvokePlan'), tone: 'accent' });
        }
        if (r.status === 'INVOKED') {
          extras.push({ Icon: RotateCcw, onClick: () => restorePlan(r), title: t('bcmActRestorePlan'), tone: 'primary' });
        }
        return <RowActions extras={extras} onEdit={() => planS.startEdit(r)} onDelete={() => planS.askDelete(r)} />;
      } },
  ];

  const procCols: Column<BcmProcessDto>[] = [
    { header: t('colName'),           cell: r => <span style={{ fontWeight: 600 }}>{r.processNameEn}</span> },
    { header: t('colArabic'),         cell: r => <span style={{ color: 'var(--ink-2)' }}>{r.processNameAr ?? '—'}</span> },
    { header: t('bcmCriticalityCol'), cell: r => <Badge tone={IMPACT_TONE[r.criticality ?? ''] ?? 'neutral'}>{r.criticality ?? '—'}</Badge> },
    { header: '', align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => procS.startEdit(r)} onDelete={() => procS.askDelete(r)} /> },
  ];

  const biaCols: Column<BcmBiaDto>[] = [
    { header: t('colName'),           cell: r => `#${r.processId ?? '—'}`, mono: true },
    { header: t('fldAssessmentDate'), cell: r => r.assessmentDate ?? '—', mono: true },
    { header: t('colRto'),            cell: r => r.rtoHours != null ? `${r.rtoHours}h` : '—', mono: true },
    { header: t('colRpo'),            cell: r => r.rpoHours != null ? `${r.rpoHours}h` : '—', mono: true },
    { header: t('colMtpd'),           cell: r => r.mtpdHours != null ? `${r.mtpdHours}h` : '—', mono: true },
    { header: t('colImpact'),         cell: r => <Badge tone={IMPACT_TONE[r.overallImpact ?? ''] ?? 'neutral'}>{r.overallImpact ?? '—'}</Badge> },
    { header: t('bcmStatusCol'),      cell: r => <Badge tone={STATUS_TONE[r.status ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.status ?? '—')}</Badge> },
    { header: '', align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => biaS.startEdit(r)} onDelete={() => biaS.askDelete(r)} /> },
  ];

  // ── Submit handlers ──
  async function submitPlan(values: BcmPlanRequest) {
    try {
      if (planS.editing) await bcmApi.updatePlan(planS.editing.id, values);
      else await bcmApi.createPlan(values);
      toast.success(planS.editing ? 'Plan updated' : t('toastRiskAdded'));
      planS.closeModal(); plans.reload();
    } catch (err) { toast.danger(err instanceof ApiError ? err.message : t('toastRiskFailed')); }
  }
  async function submitProcess(values: BcmProcessRequest) {
    try {
      const body: BcmProcessRequest = {
        ...values,
        ownerId: values.ownerId ? Number(values.ownerId) : null,
      };
      if (procS.editing) await bcmApi.updateProcess(procS.editing.id, body);
      else await bcmApi.createProcess(body);
      toast.success(procS.editing ? 'Process updated' : 'Process added');
      procS.closeModal(); procs.reload();
    } catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Save failed'); }
  }
  async function submitBia(values: BcmBiaRequest) {
    try {
      const body: BcmBiaRequest = {
        ...values,
        processId:    values.processId    ? Number(values.processId)    : null,
        rtoHours:     values.rtoHours     != null && values.rtoHours     !== ('' as unknown as number) ? Number(values.rtoHours)     : null,
        rpoHours:     values.rpoHours     != null && values.rpoHours     !== ('' as unknown as number) ? Number(values.rpoHours)     : null,
        mtpdHours:    values.mtpdHours    != null && values.mtpdHours    !== ('' as unknown as number) ? Number(values.mtpdHours)    : null,
        linkedRiskId: values.linkedRiskId != null && values.linkedRiskId !== ('' as unknown as number) ? Number(values.linkedRiskId) : null,
      };
      if (biaS.editing) await bcmApi.updateBia(biaS.editing.id, body);
      else await bcmApi.createBia(body);
      toast.success(biaS.editing ? 'BIA updated' : 'BIA added');
      biaS.closeModal(); bia.reload();
    } catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Save failed'); }
  }
  async function submitExercise(values: BcmExerciseRequest) {
    try {
      const body: BcmExerciseRequest = {
        ...values,
        planId: values.planId ? Number(values.planId) : null,
        score: values.score != null && values.score !== ('' as unknown as number) ? Number(values.score) : null,
        capaCount: values.capaCount ? Number(values.capaCount) : 0,
      };
      if (exerS.editing) await bcmApi.updateExercise(exerS.editing.id, body);
      else await bcmApi.createExercise(body);
      toast.success(exerS.editing ? 'Exercise updated' : 'Exercise added');
      exerS.closeModal(); exercises.reload();
    } catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Save failed'); }
  }

  // ── Plan workflow handlers (ISO 22301 lifecycle) ──
  async function approvePlan(p: BcmPlanDto) {
    try { await bcmApi.approvePlan(p.id); toast.success('Plan approved'); plans.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Approve failed'); }
  }
  async function invokePlan(p: BcmPlanDto) {
    if (!window.confirm(`Invoke plan "${p.planNameEn}"? This signals the plan is now active in response to a real disruption.`)) return;
    try { await bcmApi.invokePlan(p.id); toast.success('Plan invoked'); plans.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Invoke failed'); }
  }
  async function restorePlan(p: BcmPlanDto) {
    try { await bcmApi.restorePlan(p.id); toast.success('Plan restored'); plans.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Restore failed'); }
  }
  async function completeExercise(e: BcmExerciseDto) {
    const scoreStr = window.prompt('Final score (0-100)?', String(e.score ?? 80));
    if (scoreStr === null) return;
    const score = Math.max(0, Math.min(100, Number(scoreStr) || 0));
    const findings = window.prompt('Findings (brief)?', e.findings ?? '') ?? '';
    try { await bcmApi.completeExercise(e.id, score, findings); toast.success('Exercise completed'); exercises.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Complete failed'); }
  }

  // ── Delete handlers ──
  async function execDelete<T extends { id: number }>(
    target: T | null,
    apiFn: (id: number) => Promise<unknown>,
    onClose: () => void,
    reload: () => void,
    label: string,
  ) {
    if (!target) return;
    setBusy(true);
    try { await apiFn(target.id); toast.success(`${label} deleted`); reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Delete failed'); }
    finally { setBusy(false); onClose(); }
  }

  // ── Tab-aware "New" button ──
  function newAction() {
    return () => {
      switch (tab) {
        case 'plans':     return planS.startCreate();
        case 'processes': return procS.startCreate();
        case 'bia':       return biaS.startCreate();
        case 'exercises': return exerS.startCreate();
        default:          return planS.startCreate();
      }
    };
  }
  const showNewButton = tab !== 'overview';

  const isDemo = plans.isDemo;

  return (
    <Shell title={t('modBcm')} sub="ISO 22301:2019">
      <PageHeader
        eyebrow={t('bcmEyebrow')} title={t('bcmTitle')} sub={t('bcmSub')}
        actions={
          <>
            {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={Filter}>{t('actionFilter')}</Button>
            <Button variant="secondary" Icon={Download}>{t('actionExport')}</Button>
            {showNewButton && (
              <Button variant="primary" Icon={Plus} onClick={newAction()}>{t('actionNew')}</Button>
            )}
          </>
        }
      />
      <div className="kpi-grid">
        <Kpi Icon={RefreshCw}  label={t('bcmKpiPlans')}     value={plans.data.length} tone="primary" />
        <Kpi Icon={Activity}   label={t('bcmKpiProcesses')} value={procs.data.length || 38} tone="info" />
        <Kpi Icon={Gauge}      label={t('bcmKpiExercises')} value="92%" trend={{ dir: 'up', value: '+5%' }} tone="success" />
        <Kpi Icon={TimerReset} label={t('bcmKpiRto')}       value="76%" trend={{ dir: 'down', value: '−2%' }} tone="warning" />
      </div>

      <ProcessLifecycleBar
        title="Business Continuity — Processes & Lifecycles"
        subtitle="ISO 22301 + ISO 22313 + ISO 22361 — every named process active in this domain."
        lifecycles={BCM_LIFECYCLES} lang="en" />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div className="dash-grid">
          <Card title={t('bcmKpiPlans')} Icon={BarChart3}>
            <Progress label="Approved"  value={Math.round(plans.data.filter(p => p.status === 'APPROVED').length / Math.max(1, plans.data.length) * 100)} tone="success" />
            <Progress label="In Review" value={Math.round(plans.data.filter(p => p.status === 'UNDER_REVIEW').length / Math.max(1, plans.data.length) * 100)} tone="accent" />
            <Progress label="Draft"     value={Math.round(plans.data.filter(p => p.status === 'DRAFT').length / Math.max(1, plans.data.length) * 100)} tone="primary" />
          </Card>
          <Card title={t('bcmTabExercises')}>
            {exercises.data.filter(e => e.score != null).slice(0, 4).map(e => (
              <Progress key={e.id} label={e.exerciseNameEn} value={e.score ?? 0}
                tone={(e.score ?? 0) >= 90 ? 'success' : (e.score ?? 0) >= 70 ? 'primary' : 'accent'} />
            ))}
            {exercises.data.filter(e => e.score != null).length === 0 && (
              <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>No completed exercises yet.</div>
            )}
          </Card>
        </div>
      )}
      {tab === 'plans' && (
        <Card padded={false}>
          {plans.loading ? <LoadingRow /> : <DataTable columns={planCols} rows={plans.data} empty={<EmptyState />} />}
        </Card>
      )}
      {tab === 'processes' && (
        <Card padded={false}>
          {procs.loading ? <LoadingRow /> : <DataTable columns={procCols} rows={procs.data} empty={<EmptyState Icon={Activity} title="No critical processes" sub="Map critical processes here to drive BIA, RTO and BCP planning." />} />}
        </Card>
      )}
      {tab === 'bia' && (
        <Card padded={false}>
          {bia.loading ? <LoadingRow /> : bia.data.length === 0
            ? <EmptyState Icon={Cpu} title="No BIA records" sub="Run a Business Impact Analysis on a critical process to populate this view." />
            : <DataTable columns={biaCols} rows={bia.data.map((b, i) => ({ ...b, id: b.id ?? i }))} />}
        </Card>
      )}
      {tab === 'exercises' && (
        <Card title={t('cardExerciseCalendar')} Icon={Calendar} padded>
          {exercises.loading ? <LoadingRow /> : exercises.data.length === 0
            ? <EmptyState Icon={Calendar} title="No exercises scheduled" sub="Schedule tabletop, functional or full exercises to validate plans." />
            : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {exercises.data.map(e => {
                  const score = e.score ?? 0;
                  const date = e.executedDate ?? e.scheduledDate ?? '—';
                  return (
                    <div key={e.id} style={{ display: 'grid', gridTemplateColumns: '1fr 110px 110px 110px 130px 90px', gap: 12, alignItems: 'center', padding: '10px 12px', border: '1px solid var(--border)', borderRadius: 'var(--r-md)', background: 'var(--surface-2)' }}>
                      <div>
                        <div style={{ fontWeight: 600 }}>{e.exerciseNameEn}</div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }} className="mono">{date}</div>
                      </div>
                      <Badge tone="info">{e.exerciseType}</Badge>
                      <Badge tone={STATUS_TONE[e.status] ?? 'neutral'} dot>{statusLabel(t, e.status)}</Badge>
                      {e.score != null
                        ? <span className="mono" style={{ fontWeight: 700, color: score >= 90 ? 'var(--success-fg)' : score >= 70 ? 'var(--primary-700)' : 'var(--accent-700)' }}>{score}%</span>
                        : <Badge tone="neutral">{e.status === 'SCHEDULED' ? 'Scheduled' : '—'}</Badge>}
                      {e.status !== 'COMPLETED' && e.status !== 'CANCELLED'
                        ? <Button variant="ghost" size="sm" Icon={FileCheck2} onClick={() => completeExercise(e)}>{t('bcmActCompleteExercise')}</Button>
                        : <span />}
                      <RowActions onEdit={() => exerS.startEdit(e)} onDelete={() => exerS.askDelete(e)} />
                    </div>
                  );
                })}
              </div>
            )}
        </Card>
      )}

      {tab === 'graph'  && <DependencyGraphTab t={t} />}

      {tab === 'comms' && <CrisisCommsTab toast={toast} t={t} />}
      {tab === 'workflows' && <ActiveWorkflowsPanel
        workflows={BCM_WORKFLOWS}
        title={t('bcmTabWorkflows') ?? 'Workflows'}
        sub="BCP plan approvals, exercise execution, and crisis-management readiness."
        t={t} />}

      {/* ── Modals ── */}
      <FormModal<BcmPlanRequest> open={planS.open}
        title={planS.editing ? `${t('editPrefix')} · ${planS.editing.planNameEn}` : t('newPlanTitle')}
        fields={PLAN_FIELDS}
        initial={planS.editing ? planToReq(planS.editing) : PLAN_EMPTY}
        onClose={planS.closeModal} onSubmit={submitPlan} />

      <FormModal<BcmProcessRequest> open={procS.open}
        title={procS.editing ? `${t('editPrefix')} · ${procS.editing.processNameEn}` : t('newProcessTitle')}
        fields={PROCESS_FIELDS}
        initial={procS.editing ? processToReq(procS.editing) : PROCESS_EMPTY}
        onClose={procS.closeModal} onSubmit={submitProcess} />

      <FormModal<BcmBiaRequest> open={biaS.open}
        title={biaS.editing ? `${t('editPrefix')} · BIA #${biaS.editing.id}` : t('newBiaTitle')}
        fields={BIA_FIELDS}
        initial={biaS.editing ? biaToReq(biaS.editing) : BIA_EMPTY}
        onClose={biaS.closeModal} onSubmit={submitBia} />

      <FormModal<BcmExerciseRequest> open={exerS.open}
        title={exerS.editing ? `${t('editPrefix')} · ${exerS.editing.exerciseNameEn}` : t('newExerciseTitle')}
        fields={EXERCISE_FIELDS}
        initial={exerS.editing ? exerciseToReq(exerS.editing) : EXERCISE_EMPTY}
        onClose={exerS.closeModal} onSubmit={submitExercise} />

      {/* ── Confirms ── */}
      <ConfirmDialog
        open={!!planS.confirmDel} destructive busy={busy}
        title={t('bcmConfirmDeletePlan')}
        message={planS.confirmDel ? `Plan "${planS.confirmDel.planNameEn}" ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={planS.closeConfirm}
        onConfirm={() => execDelete(planS.confirmDel, bcmApi.deletePlan, planS.closeConfirm, plans.reload, 'Plan')}
      />
      <ConfirmDialog
        open={!!procS.confirmDel} destructive busy={busy}
        title={t('bcmConfirmDeleteProcess')}
        message={procS.confirmDel ? `Process "${procS.confirmDel.processNameEn}" ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={procS.closeConfirm}
        onConfirm={() => execDelete(procS.confirmDel, bcmApi.deleteProcess, procS.closeConfirm, procs.reload, 'Process')}
      />
      <ConfirmDialog
        open={!!biaS.confirmDel} destructive busy={busy}
        title={t('bcmConfirmDeleteBia')}
        message={biaS.confirmDel ? `BIA record #${biaS.confirmDel.id} ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={biaS.closeConfirm}
        onConfirm={() => execDelete(biaS.confirmDel, bcmApi.deleteBia, biaS.closeConfirm, bia.reload, 'BIA record')}
      />
      <ConfirmDialog
        open={!!exerS.confirmDel} destructive busy={busy}
        title={t('bcmConfirmDeleteExercise')}
        message={exerS.confirmDel ? `Exercise "${exerS.confirmDel.exerciseNameEn}" ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={exerS.closeConfirm}
        onConfirm={() => execDelete(exerS.confirmDel, bcmApi.deleteExercise, exerS.closeConfirm, exercises.reload, 'Exercise')}
      />
    </Shell>
  );
}

/**
 * Crisis Communication tab — assessment §5.5 #5.
 * Lists pre-approved templates + audience groups + recent distributions, with
 * a one-click "Send" action that simulates the channel-side delivery and
 * records an audit row.
 */
function CrisisCommsTab({ toast, t }: { toast: ReturnType<typeof useToast>; t: ReturnType<typeof useI18n>['t'] }) {
  const [templates, setTemplates] = useState<CrisisCommsTemplateDto[]>([]);
  const [audiences, setAudiences] = useState<CrisisCommsAudienceDto[]>([]);
  const [distributions, setDistributions] = useState<CrisisCommsDistributionDto[]>([]);
  const [loading, setLoading] = useState(false);

  function reload() {
    if (!isAuthenticated()) return;
    setLoading(true);
    Promise.all([crisisCommsApi.templates(), crisisCommsApi.audiences(), crisisCommsApi.distributions()])
      .then(([ts, as_, ds]) => { setTemplates(ts); setAudiences(as_); setDistributions(ds); })
      .catch(() => { setTemplates([]); setAudiences([]); setDistributions([]); })
      .finally(() => setLoading(false));
  }
  useEffect(() => { reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function send(template: CrisisCommsTemplateDto) {
    const matchingAudiences = audiences.filter(a =>
      a.audienceRole === template.audienceRole || template.audienceRole === 'ALL');
    if (matchingAudiences.length === 0) { toast.danger(`No audience configured for role ${template.audienceRole}`); return; }
    const target = matchingAudiences[0];
    if (!window.confirm(`Send template "${template.nameEn}" to audience "${target.name}" (${target.recipientCount} recipients) via ${template.channel}?`)) return;
    try {
      await crisisCommsApi.send(template.id, target.id);
      toast.success(`${template.code} → ${target.name}: send recorded (simulated)`);
      reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Send failed'); }
  }

  if (loading) return <Card padded><div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div></Card>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="dash-grid">
        {/* Templates */}
        <Card title={t('bcmCommsTemplates')} Icon={Megaphone} action={<Badge tone="primary">{templates.length}</Badge>}>
          {templates.length === 0
            ? <div style={{ padding: 12, fontSize: 12, color: 'var(--ink-3)' }}>No templates configured.</div>
            : templates.map(tmpl => (
              <div key={tmpl.id} style={{
                padding: '10px 0', borderBottom: '1px dashed var(--border)',
                display: 'grid', gridTemplateColumns: '1fr auto', gap: 8, alignItems: 'flex-start',
              }}>
                <div>
                  <div style={{ fontWeight: 600, fontSize: 13 }}>{tmpl.nameEn}</div>
                  {tmpl.nameAr && <div style={{ fontSize: 11, color: 'var(--ink-3)' }} dir="rtl">{tmpl.nameAr}</div>}
                  <div style={{ marginTop: 4, display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                    <Badge tone="primary">{tmpl.audienceRole}</Badge>
                    <Badge tone="info">{tmpl.channel}</Badge>
                    {tmpl.severityBand && <Badge tone="warning">{tmpl.severityBand}</Badge>}
                    <span style={{ fontSize: 10, color: 'var(--ink-3)' }} className="mono">v{tmpl.version}</span>
                  </div>
                </div>
                <Button size="sm" variant="primary" Icon={Send} onClick={() => send(tmpl)}>{t('bcmCommsActSend')}</Button>
              </div>
            ))}
        </Card>

        {/* Audiences */}
        <Card title={t('bcmCommsAudiences')} Icon={Activity} action={<Badge tone="primary">{audiences.length}</Badge>}>
          {audiences.length === 0
            ? <div style={{ padding: 12, fontSize: 12, color: 'var(--ink-3)' }}>No audience groups configured.</div>
            : audiences.map(a => (
              <div key={a.id} style={{ padding: '10px 0', borderBottom: '1px dashed var(--border)' }}>
                <div style={{ fontWeight: 600, fontSize: 13 }}>{a.name}</div>
                {a.nameAr && <div style={{ fontSize: 11, color: 'var(--ink-3)' }} dir="rtl">{a.nameAr}</div>}
                <div style={{ marginTop: 4, display: 'flex', gap: 4 }}>
                  <Badge tone="primary">{a.audienceRole}</Badge>
                  <Badge tone="info">{a.recipientCount} recipients</Badge>
                </div>
              </div>
            ))}
        </Card>
      </div>

      {/* Distribution audit log */}
      <Card title={t('bcmCommsDistributions')} padded>
        {distributions.length === 0
          ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>No distributions recorded yet.</div>
          : distributions.slice(0, 12).map(d => (
            <div key={d.id} style={{
              display: 'grid', gridTemplateColumns: '180px 1fr 100px 110px 90px', gap: 10,
              padding: '8px 0', borderBottom: '1px dashed var(--border)', alignItems: 'center', fontSize: 12,
            }}>
              <span className="mono">{d.sentAt.slice(0, 16).replace('T', ' ')}</span>
              <div>
                <div style={{ fontWeight: 600 }}>{d.subjectSnapshot ?? '—'}</div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                  template #{d.templateId} → audience #{d.audienceId}
                </div>
              </div>
              <Badge tone="info">{d.channel}</Badge>
              <Badge tone="primary">{d.recipientCount} recipients</Badge>
              <Badge tone={d.deliveryStatus === 'DELIVERED' ? 'success' : d.deliveryStatus === 'FAILED' ? 'danger' : 'neutral'} dot>
                {d.deliveryStatus}
              </Badge>
            </div>
          ))}
      </Card>
    </div>
  );
}

function LoadingRow() {
  const { t } = useI18n();
  return <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>;
}

// ─── BIA dependency graph (M3) ──────────────────────────────────────────────
type GraphNode = { id: number; nameEn: string; nameAr?: string; criticality?: string };
type GraphEdge = { id: number; from: number; to: number; dependencyType: string; criticalityImpact?: string; notes?: string };

const CRIT_COLOR: Record<string, string> = {
  CRITICAL: 'var(--risk-crit)',
  HIGH:     'var(--risk-vhigh)',
  MEDIUM:   'var(--risk-med)',
  LOW:      'var(--risk-low)',
};
const DEP_TYPE_DASH: Record<string, string> = {
  INTERNAL_PROCESS: '0',
  UPSTREAM_VENDOR:  '6,4',
  INFRASTRUCTURE:   '2,4',
  DATA_FEED:        '8,2,2,2',
  PEOPLE_SKILL:     '1,4',
};

function DependencyGraphTab({ t }: { t: (k: string) => string }) {
  const { lang } = useI18n();
  const [nodes, setNodes] = useState<GraphNode[]>([]);
  const [edges, setEdges] = useState<GraphEdge[]>([]);
  const [loading, setLoading] = useState(false);
  const [focusId, setFocusId] = useState<number | null>(null);

  useEffect(() => {
    if (!isAuthenticated()) return;
    setLoading(true);
    bcmApi.dependencyGraph()
      .then(d => { setNodes(d.nodes); setEdges(d.edges); })
      .catch(() => { setNodes([]); setEdges([]); })
      .finally(() => setLoading(false));
  }, []);

  // Layered layout: longest-path from any source. Sources have no incoming edges.
  const layout = layeredLayout(nodes, edges);
  const w = Math.max(720, layout.cols * 240 + 80);
  const h = Math.max(360, layout.maxRows * 80 + 80);

  // Compute upstream/downstream chain for the focused node
  const upstreamIds = focusId != null ? closure(focusId, edges, 'up')   : new Set<number>();
  const downstreamIds = focusId != null ? closure(focusId, edges, 'down') : new Set<number>();

  if (!isAuthenticated()) {
    return <Card title={t('bcmTabDepGraph')}><div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>{t('signInToView')}</div></Card>;
  }
  if (loading) return <Card><LoadingRow /></Card>;
  if (nodes.length === 0) {
    return <Card title={t('bcmTabDepGraph')}>
      <EmptyState Icon={Activity} title={t('bcmDepGraphEmptyTitle')} sub={t('bcmDepGraphEmptySub')} />
    </Card>;
  }

  return (
    <Card title={t('bcmTabDepGraph')} action={<>
      <Badge tone="primary">{nodes.length} {t('bcmDepGraphProcesses')}</Badge>
      <Badge tone="info">{edges.length} {t('bcmDepGraphEdges')}</Badge>
    </>}>
      <div style={{ display: 'flex', gap: 12, fontSize: 11, color: 'var(--ink-3)', marginBottom: 8, flexWrap: 'wrap' }}>
        <span>{t('bcmDepGraphLegend')}:</span>
        <LegendDot color="var(--risk-crit)"  label={t('critCritical')} />
        <LegendDot color="var(--risk-vhigh)" label={t('critHigh')} />
        <LegendDot color="var(--risk-med)"   label={t('critMedium')} />
        <LegendDot color="var(--risk-low)"   label={t('critLow')} />
        <span style={{ marginLeft: 'auto' }}>
          {focusId != null && <button onClick={() => setFocusId(null)} className="lang-toggle">{t('bcmDepGraphClearFocus')}</button>}
        </span>
      </div>
      <div style={{ overflow: 'auto', border: '1px solid var(--border)', borderRadius: 'var(--r-md)', background: 'var(--surface)' }}>
        <svg width={w} height={h} style={{ display: 'block' }}>
          <defs>
            <marker id="arrowDefault" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--ink-4)" />
            </marker>
            <marker id="arrowFocus" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--accent-500)" />
            </marker>
          </defs>
          {edges.map(e => {
            const a = layout.pos.get(e.from);
            const b = layout.pos.get(e.to);
            if (!a || !b) return null;
            const focused = focusId != null && (e.from === focusId || e.to === focusId
              || (upstreamIds.has(e.from) && upstreamIds.has(e.to))
              || (downstreamIds.has(e.from) && downstreamIds.has(e.to)));
            const dim = focusId != null && !focused;
            const color = focused ? 'var(--accent-500)' : 'var(--ink-4)';
            const x1 = a.x + 120, y1 = a.y + 24;
            const x2 = b.x,       y2 = b.y + 24;
            const cx = (x1 + x2) / 2;
            return (
              <g key={e.id} opacity={dim ? 0.18 : 1}>
                <path d={`M ${x1} ${y1} C ${cx} ${y1}, ${cx} ${y2}, ${x2} ${y2}`}
                      stroke={color} strokeWidth={focused ? 2 : 1.4} fill="none"
                      strokeDasharray={DEP_TYPE_DASH[e.dependencyType] ?? '0'}
                      markerEnd={focused ? 'url(#arrowFocus)' : 'url(#arrowDefault)'} />
              </g>
            );
          })}
          {nodes.map(n => {
            const p = layout.pos.get(n.id);
            if (!p) return null;
            const isFocus  = n.id === focusId;
            const inChain  = focusId != null && (upstreamIds.has(n.id) || downstreamIds.has(n.id));
            const dim      = focusId != null && !isFocus && !inChain;
            const fill     = CRIT_COLOR[n.criticality ?? ''] ?? 'var(--surface-2)';
            const label    = lang === 'ar' && n.nameAr ? n.nameAr : n.nameEn;
            return (
              <g key={n.id} opacity={dim ? 0.25 : 1} style={{ cursor: 'pointer' }} onClick={() => setFocusId(prev => prev === n.id ? null : n.id)}>
                <rect x={p.x} y={p.y} width={120} height={48} rx={8} ry={8}
                      fill={fill} stroke={isFocus ? 'var(--accent-700)' : 'var(--border-strong)'} strokeWidth={isFocus ? 2.5 : 1} />
                <text x={p.x + 60} y={p.y + 22} textAnchor="middle" fontSize="11" fontWeight="600" fill="var(--ink-1)">
                  <tspan>{truncate(label, 18)}</tspan>
                </text>
                <text x={p.x + 60} y={p.y + 38} textAnchor="middle" fontSize="9" fill="var(--ink-2)">
                  {n.criticality}
                </text>
              </g>
            );
          })}
        </svg>
      </div>
      {focusId != null && (
        <div style={{ marginTop: 12, fontSize: 12, color: 'var(--ink-2)' }}>
          <strong>{t('bcmDepGraphUpstream')}:</strong> {Array.from(upstreamIds).filter(id => id !== focusId).map(id => nodes.find(n => n.id === id)?.nameEn).filter(Boolean).join(', ') || '—'}
          <br/>
          <strong>{t('bcmDepGraphDownstream')}:</strong> {Array.from(downstreamIds).filter(id => id !== focusId).map(id => nodes.find(n => n.id === id)?.nameEn).filter(Boolean).join(', ') || '—'}
        </div>
      )}
    </Card>
  );
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
    <span style={{ width: 10, height: 10, borderRadius: 2, background: color, display: 'inline-block' }} />{label}
  </span>;
}

function truncate(s: string, n: number): string {
  return s.length > n ? s.slice(0, n - 1) + '…' : s;
}

/** Compute longest-path layered layout; returns positions per node id. */
function layeredLayout(nodes: GraphNode[], edges: GraphEdge[]): { pos: Map<number, { x: number; y: number }>; cols: number; maxRows: number } {
  // depth[id] = longest chain from any source ending at id
  const depth = new Map<number, number>();
  const incoming = new Map<number, number[]>();
  const outgoing = new Map<number, number[]>();
  for (const n of nodes) { incoming.set(n.id, []); outgoing.set(n.id, []); }
  for (const e of edges) {
    incoming.get(e.to)?.push(e.from);
    outgoing.get(e.from)?.push(e.to);
  }
  // Topological order via Kahn's
  const order: number[] = [];
  const indeg = new Map<number, number>();
  for (const n of nodes) indeg.set(n.id, (incoming.get(n.id) ?? []).length);
  const queue: number[] = [];
  for (const [id, d] of indeg.entries()) if (d === 0) queue.push(id);
  while (queue.length) {
    const id = queue.shift()!;
    order.push(id);
    for (const nx of outgoing.get(id) ?? []) {
      const nd = (indeg.get(nx) ?? 0) - 1;
      indeg.set(nx, nd);
      if (nd === 0) queue.push(nx);
    }
  }
  for (const id of order) {
    const ins = incoming.get(id) ?? [];
    if (ins.length === 0) depth.set(id, 0);
    else depth.set(id, Math.max(...ins.map(p => (depth.get(p) ?? 0) + 1)));
  }
  // Cycle fallback — any unset gets depth 0
  for (const n of nodes) if (!depth.has(n.id)) depth.set(n.id, 0);

  // Group by depth column
  const byCol = new Map<number, number[]>();
  for (const n of nodes) {
    const c = depth.get(n.id) ?? 0;
    if (!byCol.has(c)) byCol.set(c, []);
    byCol.get(c)!.push(n.id);
  }
  // Sort each column by id for stable layout
  for (const arr of byCol.values()) arr.sort((a, b) => a - b);

  const cols = Math.max(...byCol.keys()) + 1;
  let maxRows = 0;
  for (const arr of byCol.values()) maxRows = Math.max(maxRows, arr.length);

  const pos = new Map<number, { x: number; y: number }>();
  for (const [c, ids] of byCol.entries()) {
    ids.forEach((id, i) => {
      pos.set(id, { x: 40 + c * 220, y: 40 + i * 80 });
    });
  }
  return { pos, cols, maxRows };
}

/** BFS closure of nodes upstream OR downstream from the seed. */
function closure(seed: number, edges: GraphEdge[], dir: 'up' | 'down'): Set<number> {
  const out = new Set<number>([seed]);
  const queue: number[] = [seed];
  while (queue.length) {
    const cur = queue.shift()!;
    for (const e of edges) {
      const next = dir === 'up' ? (e.to === cur ? e.from : null) : (e.from === cur ? e.to : null);
      if (next != null && !out.has(next)) {
        out.add(next);
        queue.push(next);
      }
    }
  }
  return out;
}
