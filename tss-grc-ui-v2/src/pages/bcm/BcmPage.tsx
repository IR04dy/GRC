import { useState } from 'react';
import { RefreshCw, Activity, TimerReset, Gauge, Filter, Download, Plus, BarChart3, Cpu, Calendar } from 'lucide-react';
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
  bcmApi,
  type BcmPlanDto, type BcmPlanRequest,
  type BcmProcessDto, type BcmProcessRequest,
  type BcmBiaDto, type BcmBiaRequest,
  ApiError,
} from '../../lib/api';
import { useToast } from '../../lib/toast';
import { BCM_PLANS } from './mockBcm';

type TabId = 'overview' | 'plans' | 'processes' | 'bia' | 'exercises';

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

interface ExerciseRow { id: number; name: string; date: string; type: string; score: number }
const INITIAL_EXERCISES: ExerciseRow[] = [
  { id: 1, name: 'Tabletop · Q1 2026',    date: '2026-03-12', score: 100, type: 'TABLETOP' },
  { id: 2, name: 'Functional · Q1 2026',  date: '2026-03-26', score: 92,  type: 'FUNCTIONAL' },
  { id: 3, name: 'Full · Q4 2025',        date: '2025-12-08', score: 78,  type: 'FULL' },
  { id: 4, name: 'Crisis Comms · Q3 2025', date: '2025-09-19', score: 64,  type: 'COMMUNICATION' },
  { id: 5, name: 'Tabletop · Q2 2026',    date: '2026-06-02', score: 0,   type: 'TABLETOP' },
];
const EXERCISE_EMPTY: ExerciseRow = { id: 0, name: '', date: '', type: 'TABLETOP', score: 0 };

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
  const exerS = useEntityState<ExerciseRow>();

  const plans = useApiList<BcmPlanDto>(() => bcmApi.plans(0, 100), BCM_PLANS as unknown as BcmPlanDto[]);
  const procs = useApiList<BcmProcessDto>(() => bcmApi.processes(0, 100), []);
  // /bia returns a plain array — adapt to the Page<T> shape useApiList expects.
  const bia   = useApiList<BcmBiaDto>(() => bcmApi.bia().then(arr => ({ content: arr })), []);

  // Exercises are demo-only (no backend). Hold them in local state so add/edit/delete still work.
  const [exercises, setExercises] = useState<ExerciseRow[]>(INITIAL_EXERCISES);

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

  const EXERCISE_FIELDS: FieldDef[] = [
    { kind: 'text',   key: 'name',  label: t('bcmFldExerciseName'), required: true, maxLength: 200, span: 12 },
    { kind: 'select', key: 'type',  label: t('bcmFldExerciseType'), options: EXERCISE_TYPE_OPTIONS, span: 6 },
    { kind: 'date',   key: 'date',  label: t('bcmFldExerciseDate'), span: 6 },
    { kind: 'number', key: 'score', label: t('bcmFldExerciseScore'), min: 0, max: 100, span: 12 },
  ];

  const tabs: TabItem<TabId>[] = [
    { id: 'overview',  label: t('tabOverview') },
    { id: 'plans',     label: t('bcmTabPlans') },
    { id: 'processes', label: t('bcmTabProcesses') },
    { id: 'bia',       label: t('bcmTabBia') },
    { id: 'exercises', label: t('bcmTabExercises') },
  ];

  const planCols: Column<BcmPlanDto>[] = [
    { header: t('bcmPlanCol'),    cell: r => <span style={{ fontWeight: 600 }}>{r.planNameEn}</span> },
    { header: t('colType'),       cell: r => <Badge tone="info">{(r.planType ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colVersion'),    cell: r => r.version, mono: true },
    { header: t('bcmReviewCol'),  cell: r => r.nextReviewDate ?? '—', mono: true },
    { header: t('bcmStatusCol'),  cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(t, r.status)}</Badge> },
    { header: '', align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => planS.startEdit(r)} onDelete={() => planS.askDelete(r)} /> },
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
  function submitExercise(values: ExerciseRow) {
    const score = Number(values.score) || 0;
    if (exerS.editing) {
      setExercises(prev => prev.map(e => e.id === exerS.editing!.id ? { ...exerS.editing!, ...values, score } : e));
      toast.success('Exercise updated');
    } else {
      const id = exercises.length ? Math.max(...exercises.map(e => e.id)) + 1 : 1;
      setExercises(prev => [...prev, { ...values, id, score }]);
      toast.success('Exercise added');
    }
    exerS.closeModal();
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
  function deleteExercise() {
    if (!exerS.confirmDel) return;
    setExercises(prev => prev.filter(e => e.id !== exerS.confirmDel!.id));
    toast.success('Exercise deleted');
    exerS.closeConfirm();
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
      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div className="dash-grid">
          <Card title={t('bcmKpiPlans')} Icon={BarChart3}>
            <Progress label="Approved"  value={Math.round(plans.data.filter(p => p.status === 'APPROVED').length / Math.max(1, plans.data.length) * 100)} tone="success" />
            <Progress label="In Review" value={Math.round(plans.data.filter(p => p.status === 'UNDER_REVIEW').length / Math.max(1, plans.data.length) * 100)} tone="accent" />
            <Progress label="Draft"     value={Math.round(plans.data.filter(p => p.status === 'DRAFT').length / Math.max(1, plans.data.length) * 100)} tone="primary" />
          </Card>
          <Card title={t('bcmTabExercises')}>
            {exercises.slice(0, 4).map(e => <Progress key={e.id} label={e.name} value={e.score} tone={e.score >= 90 ? 'success' : e.score >= 70 ? 'primary' : 'accent'} />)}
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
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {exercises.map(e => (
              <div key={e.id} style={{ display: 'grid', gridTemplateColumns: '1fr 80px 100px 130px 90px', gap: 12, alignItems: 'center', padding: '10px 12px', border: '1px solid var(--border)', borderRadius: 'var(--r-md)', background: 'var(--surface-2)' }}>
                <div>
                  <div style={{ fontWeight: 600 }}>{e.name}</div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }} className="mono">{e.date}</div>
                </div>
                <Badge tone="info">{e.type}</Badge>
                {e.score > 0
                  ? <span className="mono" style={{ fontWeight: 700, color: e.score >= 90 ? 'var(--success-fg)' : e.score >= 70 ? 'var(--primary-700)' : 'var(--accent-700)' }}>{e.score}%</span>
                  : <Badge tone="neutral">Scheduled</Badge>}
                <Button variant="ghost" size="sm">View report →</Button>
                <RowActions onEdit={() => exerS.startEdit(e)} onDelete={() => exerS.askDelete(e)} />
              </div>
            ))}
          </div>
        </Card>
      )}

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

      <FormModal<ExerciseRow> open={exerS.open}
        title={exerS.editing ? `${t('editPrefix')} · ${exerS.editing.name}` : t('newExerciseTitle')}
        fields={EXERCISE_FIELDS}
        initial={exerS.editing ?? EXERCISE_EMPTY}
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
        message={exerS.confirmDel ? `Exercise "${exerS.confirmDel.name}" ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={exerS.closeConfirm}
        onConfirm={deleteExercise}
      />
    </Shell>
  );
}

function LoadingRow() {
  const { t } = useI18n();
  return <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>;
}
