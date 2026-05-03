import { useEffect, useMemo, useState } from 'react';
import {
  GitBranch, Filter, Plus, BarChart3, ListChecks, KanbanSquare, Activity,
  CheckCheck, X as XIcon, ArrowRight, AlertTriangle, ShieldCheck, Clock, Send,
  Wrench, Stamp, ChevronRight, MessageSquare, Link2, RefreshCw, Trash2,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { Progress } from '../../components/ui/Progress';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { ExportMenu } from '../../components/ui/ExportMenu';
import {
  changeApi, reportingApi, ApiError,
  type ChangeRequestDto, type ChangeApprovalDto, type ChangeTaskDto,
  type ChangeLinkDto, type ChangeCommentDto, type ChangeAnalyticsDto,
} from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { isAuthenticated } from '../../lib/auth';
import { CHANGE_REQUESTS, CHANGE_ANALYTICS } from './mockChanges';
import { ProcessLifecycleBar } from '../../components/workflows/ProcessLifecycleBar';
import { CHANGE_LIFECYCLES } from '../../lib/moduleLifecycles';
import { RegulatorPostureCard } from '../../components/authority/RegulatorPostureCard';
import { CHANGE_ANCHORS } from '../../lib/moduleAnchors';

type TabId = 'register' | 'kanban' | 'workflow' | 'analytics' | 'integrations';

const STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT: 'neutral', SUBMITTED: 'info', IMPACT_ASSESSED: 'info',
  CAB_PENDING: 'warning', CAB_APPROVED: 'primary', CAB_REJECTED: 'danger',
  EMERGENCY_APPROVED: 'accent',
  SCHEDULED: 'primary', IMPLEMENTING: 'accent', IMPLEMENTED: 'success',
  ROLLED_BACK: 'danger', POST_REVIEW: 'info', CLOSED: 'success', CANCELLED: 'neutral',
};
const TYPE_TONE: Record<string, BadgeTone> = {
  STANDARD: 'success', NORMAL: 'primary', EMERGENCY: 'danger',
};
const RISK_TONE: Record<string, BadgeTone> = {
  LOW: 'success', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger',
};

// Simplified kanban board lanes (post-review + closed go to a final lane)
const KANBAN_LANES = [
  { id: 'DRAFT',           statuses: ['DRAFT'] },
  { id: 'SUBMITTED',       statuses: ['SUBMITTED', 'IMPACT_ASSESSED'] },
  { id: 'CAB',             statuses: ['CAB_PENDING'] },
  { id: 'APPROVED',        statuses: ['CAB_APPROVED', 'EMERGENCY_APPROVED', 'SCHEDULED'] },
  { id: 'IMPLEMENTING',    statuses: ['IMPLEMENTING'] },
  { id: 'COMPLETED',       statuses: ['IMPLEMENTED', 'POST_REVIEW', 'CLOSED', 'ROLLED_BACK', 'CAB_REJECTED', 'CANCELLED'] },
];

const TYPE_OPTIONS = [
  { value: 'STANDARD',  label: 'Standard (pre-approved)' },
  { value: 'NORMAL',    label: 'Normal (CAB review)' },
  { value: 'EMERGENCY', label: 'Emergency' },
];
const CATEGORY_OPTIONS = [
  'INFRASTRUCTURE', 'APPLICATION', 'NETWORK', 'SECURITY', 'POLICY',
  'PROCESS', 'COMPLIANCE_REMEDIATION', 'EMERGENCY_FIX', 'DATA', 'THIRD_PARTY',
].map(v => ({ value: v, label: v.replace(/_/g, ' ') }));
const RISK_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(v => ({ value: v, label: v }));

const ENTITY_TYPE_OPTIONS = [
  'RISK', 'CONTROL', 'INCIDENT', 'VENDOR', 'BCM_PLAN', 'AUDIT_FINDING',
  'POLICY', 'REGULATORY_CHANGE', 'DPIA', 'KRI', 'ASSET', 'EVIDENCE',
].map(v => ({ value: v, label: v.replace(/_/g, ' ') }));
const LINK_TYPE_OPTIONS = ['AFFECTS', 'TRIGGERED_BY', 'MITIGATES', 'TARGETS', 'REMEDIATES', 'DEPENDS_ON', 'INFORMS']
  .map(v => ({ value: v, label: v.replace(/_/g, ' ') }));

const EMPTY: Partial<ChangeRequestDto> = {
  changeType: 'NORMAL', riskLevel: 'MEDIUM', category: 'INFRASTRUCTURE',
  titleEn: '', titleAr: '', descriptionEn: '',
  impactSummary: '', rollbackPlan: '', testPlan: '',
  businessJustification: '', requestedByName: '', changeOwnerName: '',
  scheduledStart: undefined, scheduledEnd: undefined,
};

export function ChangesPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const demoMode = !isAuthenticated();

  const [tab, setTab] = useState<TabId>('register');
  const [items, setItems] = useState<ChangeRequestDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [analytics, setAnalytics] = useState<ChangeAnalyticsDto | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ChangeRequestDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<ChangeRequestDto | null>(null);
  const [busy, setBusy] = useState(false);

  const [detailId, setDetailId] = useState<number | null>(null);

  function applyDemoChanges() { setItems(CHANGE_REQUESTS); setAnalytics(CHANGE_ANALYTICS); }

  async function load() {
    if (demoMode) { applyDemoChanges(); return; }
    setLoading(true);
    try {
      const [page, an] = await Promise.all([
        changeApi.list({ size: 200 }),
        changeApi.analytics().catch(() => null),
      ]);
      const list = page.content ?? [];
      if (list.length === 0) { applyDemoChanges(); return; }
      setItems(list);
      setAnalytics(an);
    } catch { applyDemoChanges(); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, []);

  const total       = items.length;
  const inFlight    = items.filter(c => ['SUBMITTED','IMPACT_ASSESSED','CAB_PENDING','CAB_APPROVED','SCHEDULED','EMERGENCY_APPROVED','IMPLEMENTING'].includes(c.status)).length;
  const overdue     = items.filter(c => c.scheduledStart && c.status !== 'CLOSED' && new Date(c.scheduledStart) < new Date()).length;
  const successPct  = analytics?.successRatePct ?? 0;

  const tabs: TabItem<TabId>[] = [
    { id: 'register',     label: t('changeTabRegister') },
    { id: 'kanban',       label: t('changeTabKanban') },
    { id: 'workflow',     label: t('changeTabWorkflow') },
    { id: 'analytics',    label: t('changeTabAnalytics') },
    { id: 'integrations', label: t('changeTabIntegrations') },
  ];

  const FIELDS: FieldDef[] = [
    { kind: 'text',     key: 'changeCode',           label: t('changeFldCode'), span: 4, placeholder: 'CHG-2025-0042' },
    { kind: 'select',   key: 'changeType',           label: t('changeFldType') + ' *', required: true, options: TYPE_OPTIONS, span: 4 },
    { kind: 'select',   key: 'riskLevel',            label: t('changeFldRisk'),  options: RISK_OPTIONS, span: 4 },
    { kind: 'text',     key: 'titleEn',              label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 500, span: 6 },
    { kind: 'text',     key: 'titleAr',              label: t('fldTitleArLbl'), maxLength: 500, span: 6 },
    { kind: 'select',   key: 'category',             label: t('changeFldCategory'), options: CATEGORY_OPTIONS, span: 6 },
    { kind: 'text',     key: 'requestedByName',      label: t('changeFldRequestedBy'), span: 6 },
    { kind: 'text',     key: 'changeOwnerName',      label: t('changeFldOwner'), span: 6 },
    { kind: 'text',     key: 'implementerName',      label: t('changeFldImplementer'), span: 6 },
    { kind: 'date',     key: 'scheduledStart',       label: t('changeFldScheduledStart'), span: 6 },
    { kind: 'date',     key: 'scheduledEnd',         label: t('changeFldScheduledEnd'), span: 6 },
    { kind: 'textarea', key: 'descriptionEn',        label: t('changeFldDescription'), rows: 3, span: 12 },
    { kind: 'textarea', key: 'impactSummary',        label: t('changeFldImpact'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'rollbackPlan',         label: t('changeFldRollback'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'testPlan',             label: t('changeFldTestPlan'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'businessJustification',label: t('changeFldJustification'), rows: 2, span: 12 },
  ];

  function reqFromDto(c: ChangeRequestDto): Partial<ChangeRequestDto> {
    return { ...c };
  }

  async function submitForm(values: Partial<ChangeRequestDto>) {
    try {
      if (editing) await changeApi.update(editing.id, values);
      else await changeApi.create(values);
      toast.success(editing ? t('changeUpdated') : t('changeCreated'));
      setModalOpen(false); setEditing(null);
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }

  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await changeApi.remove(confirmDel.id); toast.success('Change deleted'); await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }

  async function transition(c: ChangeRequestDto, target: string, notes?: string, outcome?: string) {
    try { await changeApi.transition(c.id, { target, notes, outcome }); toast.success(`→ ${target}`); await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Transition failed'); }
  }

  // Columns for register
  const cols: Column<ChangeRequestDto>[] = [
    { header: t('changeFldCode'),   cell: c => <span className="mono" style={{ fontWeight: 600 }}>{c.changeCode ?? '—'}</span>, width: '130px' },
    { header: t('colTitle'),        cell: c => <div style={{ fontWeight: 600 }}>{lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}</div> },
    { header: t('changeFldType'),   cell: c => <Badge tone={TYPE_TONE[c.changeType] ?? 'neutral'}>{c.changeType}</Badge>, width: '120px' },
    { header: t('changeFldRisk'),   cell: c => c.riskLevel ? <Badge tone={RISK_TONE[c.riskLevel] ?? 'neutral'} dot>{c.riskLevel}</Badge> : '—', width: '110px' },
    { header: t('colStatus'),       cell: c => <Badge tone={STATUS_TONE[c.status] ?? 'neutral'} dot>{c.status.replace(/_/g, ' ')}</Badge>, width: '170px' },
    { header: t('changeFldScheduledStart'), cell: c => c.scheduledStart ? new Date(c.scheduledStart).toLocaleDateString() : '—', mono: true, width: '120px' },
    { header: '', align: 'end', width: '160px',
      cell: c => <RowActions onEdit={() => { setEditing(c); setModalOpen(true); }}
                              onDelete={() => setConfirmDel(c)}
                              extras={[
                                { Icon: ChevronRight, onClick: () => { setDetailId(c.id); setTab('workflow'); }, title: t('changeActOpen'), tone: 'primary' },
                              ]} /> },
  ];

  return (
    <Shell title={t('modChanges')} sub="ITIL v4 · Change Enablement">
      <PageHeader
        eyebrow={t('changeEyebrow')}
        title={t('changeTitle')}
        sub={t('changeSub')}
        actions={
          <>
            {demoMode && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={RefreshCw} onClick={load} disabled={loading}>{t('dashRefresh')}</Button>
            <Button variant="secondary" Icon={Filter}>{t('actionFilter')}</Button>
            <ExportMenu
              demoMode={demoMode} t={t} toast={toast}
              onPdf={() => reportingApi.riskRegisterPdf(lang)}
              onXlsx={() => reportingApi.riskRegisterXlsx()}
            />
            <Button variant="primary" Icon={Plus} onClick={() => { setEditing(null); setModalOpen(true); }}>{t('changeNew')}</Button>
          </>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={GitBranch}    label={t('changeKpiTotal')}   value={total}     tone="primary" />
        <Kpi Icon={Clock}        label={t('changeKpiInFlight')} value={inFlight}  tone="info" />
        <Kpi Icon={AlertTriangle} label={t('changeKpiOverdue')} value={overdue}  tone="danger" />
        <Kpi Icon={ShieldCheck}  label={t('changeKpiSuccess')} value={`${successPct}%`} tone="success" />
      </div>

      <ProcessLifecycleBar
        title="Change Enablement — Processes & Lifecycles"
        subtitle="ITIL v4 Change Enablement — every named process active in this domain."
        lifecycles={CHANGE_LIFECYCLES} lang="en" />

      <RegulatorPostureCard
        title="Regulator Posture · Change Enablement"
        anchors={CHANGE_ANCHORS} />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'register' && (
        <Card padded={false}>
          {loading ? <Loading /> :
            <DataTable columns={cols} rows={items}
                       empty={<EmptyState Icon={GitBranch} title={t('changeEmptyTitle')} sub={t('changeEmptySub')} />} />
          }
        </Card>
      )}

      {tab === 'kanban' && (
        <KanbanBoard items={items} onTransition={transition} onOpen={(id) => { setDetailId(id); setTab('workflow'); }} t={t} lang={lang} />
      )}

      {tab === 'workflow' && (
        <WorkflowPanel
          items={items}
          detailId={detailId} setDetailId={setDetailId}
          onTransition={transition}
          onChanged={load}
          t={t} lang={lang} toast={toast}
        />
      )}

      {tab === 'analytics' && <AnalyticsPanel analytics={analytics} t={t} />}

      {tab === 'integrations' && (
        <IntegrationsPanel
          items={items}
          detailId={detailId} setDetailId={setDetailId}
          onChanged={load}
          t={t} toast={toast}
        />
      )}

      <FormModal<Partial<ChangeRequestDto>>
        open={modalOpen}
        title={editing ? `${t('editPrefix')} · ${editing.changeCode ?? editing.titleEn}` : t('changeNew')}
        fields={FIELDS}
        initial={editing ? reqFromDto(editing) : EMPTY}
        onClose={() => { setModalOpen(false); setEditing(null); }}
        onSubmit={submitForm}
      />

      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title={t('changeConfirmDelete')}
        message={confirmDel ? `${confirmDel.titleEn} ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={() => setConfirmDel(null)}
        onConfirm={executeDelete}
      />
    </Shell>
  );
}

// ─── Kanban ────────────────────────────────────────────────────────────────
function KanbanBoard({ items, onTransition, onOpen, t, lang }: {
  items: ChangeRequestDto[];
  onTransition: (c: ChangeRequestDto, target: string) => void | Promise<void>;
  onOpen: (id: number) => void;
  t: (k: string) => string;
  lang: string;
}) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: `repeat(${KANBAN_LANES.length}, minmax(220px, 1fr))`, gap: 12, overflowX: 'auto' }}>
      {KANBAN_LANES.map(lane => {
        const lst = items.filter(c => lane.statuses.includes(c.status));
        return (
          <div key={lane.id} style={{ minHeight: 320, background: 'var(--surface-2)', borderRadius: 'var(--r-md)', padding: 8, border: '1px solid var(--border)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '4px 6px 8px' }}>
              <span style={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--ink-2)' }}>{lane.id}</span>
              <Badge tone="neutral">{lst.length}</Badge>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {lst.map(c => (
                <div key={c.id} onClick={() => onOpen(c.id)} style={{
                  background: 'var(--surface)', border: '1px solid var(--border)',
                  borderRadius: 'var(--r-sm)', padding: 8, cursor: 'pointer',
                  fontSize: 12,
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{c.changeCode ?? `#${c.id}`}</span>
                    <Badge tone={TYPE_TONE[c.changeType] ?? 'neutral'}>{c.changeType}</Badge>
                  </div>
                  <div style={{ fontWeight: 600, color: 'var(--ink-1)', marginBottom: 4 }}>
                    {lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    {c.riskLevel && <Badge tone={RISK_TONE[c.riskLevel] ?? 'neutral'} dot>{c.riskLevel}</Badge>}
                    {c.scheduledStart && <span style={{ fontSize: 10, color: 'var(--ink-3)' }} className="mono">{new Date(c.scheduledStart).toLocaleDateString()}</span>}
                    <button onClick={(e) => { e.stopPropagation(); void onTransition(c, nextStatus(c) ?? c.status); }}
                            disabled={!nextStatus(c)}
                            style={{ marginInlineStart: 'auto', background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--primary)', fontSize: 10 }}>
                      {nextStatus(c) ? `→ ${nextStatus(c)?.replace(/_/g, ' ')}` : t('changeKanbanDone')}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function nextStatus(c: ChangeRequestDto): string | null {
  switch (c.status) {
    case 'DRAFT':            return c.changeType === 'EMERGENCY' ? 'EMERGENCY_APPROVED' : 'SUBMITTED';
    case 'SUBMITTED':        return 'IMPACT_ASSESSED';
    case 'IMPACT_ASSESSED':  return 'CAB_PENDING';
    case 'CAB_PENDING':      return 'CAB_APPROVED';
    case 'CAB_APPROVED':     return 'SCHEDULED';
    case 'SCHEDULED':        return 'IMPLEMENTING';
    case 'EMERGENCY_APPROVED': return 'IMPLEMENTING';
    case 'IMPLEMENTING':     return 'IMPLEMENTED';
    case 'IMPLEMENTED':      return 'POST_REVIEW';
    case 'POST_REVIEW':      return 'CLOSED';
    case 'ROLLED_BACK':      return 'POST_REVIEW';
    default:                  return null;
  }
}

// ─── Workflow detail panel ─────────────────────────────────────────────────
function WorkflowPanel({ items, detailId, setDetailId, onTransition, onChanged, t, lang, toast }: {
  items: ChangeRequestDto[];
  detailId: number | null; setDetailId: (id: number | null) => void;
  onTransition: (c: ChangeRequestDto, target: string, notes?: string, outcome?: string) => void | Promise<void>;
  onChanged: () => void | Promise<void>;
  t: (k: string) => string; lang: string;
  toast: ReturnType<typeof useToast>;
}) {
  const change = items.find(c => c.id === detailId) ?? null;
  const [approvals, setApprovals] = useState<ChangeApprovalDto[]>([]);
  const [tasks, setTasks] = useState<ChangeTaskDto[]>([]);
  const [comments, setComments] = useState<ChangeCommentDto[]>([]);
  const [commentBody, setCommentBody] = useState('');

  async function reload() {
    if (!change) { setApprovals([]); setTasks([]); setComments([]); return; }
    try {
      const [a, k, c] = await Promise.all([
        changeApi.approvals(change.id),
        changeApi.tasks(change.id),
        changeApi.comments(change.id),
      ]);
      setApprovals(a); setTasks(k); setComments(c);
    } catch { /* silent */ }
  }
  useEffect(() => { void reload(); /* eslint-disable-next-line */ }, [detailId, items]);

  if (!change) {
    return (
      <Card title={t('changeTabWorkflow')}>
        <EmptyState Icon={ListChecks} title={t('changeWorkflowSelectTitle')} sub={t('changeWorkflowSelectSub')} />
      </Card>
    );
  }

  async function castVote(decision: 'APPROVE' | 'REJECT' | 'DEFER' | 'ABSTAIN') {
    const name = window.prompt(t('changeApproverName'), '');
    if (!name) return;
    const role = window.prompt(t('changeApproverRole'), '') ?? '';
    const comments = window.prompt(t('changeApproverComments'), '') ?? '';
    try {
      await changeApi.approve(change!.id, { approverName: name, approverRole: role, decision, comments });
      toast.success(t('changeVoteRecorded'));
      void reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function addTask() {
    const title = window.prompt(t('changeTaskTitle'), '');
    if (!title) return;
    try { await changeApi.addTask(change!.id, { titleEn: title, status: 'PENDING' }); void reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function flipTask(task: ChangeTaskDto) {
    const next = task.status === 'PENDING' ? 'IN_PROGRESS' : task.status === 'IN_PROGRESS' ? 'DONE' : 'PENDING';
    try { await changeApi.updateTask(task.id!, { status: next }); void reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function deleteTask(task: ChangeTaskDto) {
    if (!window.confirm(t('changeTaskDeleteConfirm'))) return;
    try { await changeApi.deleteTask(task.id!); void reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function postComment() {
    if (!commentBody.trim()) return;
    try {
      await changeApi.comment(change!.id, { authorName: 'You', body: commentBody });
      setCommentBody(''); void reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const next = nextStatus(change);

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '320px 1fr', gap: 16 }}>
      <Card title={t('changeWorkflowList')} Icon={ListChecks} padded={false}>
        <div style={{ maxHeight: 460, overflowY: 'auto' }}>
          {items.map(c => (
            <button key={c.id} onClick={() => setDetailId(c.id)} style={{
              display: 'block', width: '100%', textAlign: 'start', padding: 10,
              background: c.id === detailId ? 'var(--surface-2)' : 'transparent',
              border: 'none', borderBottom: '1px solid var(--border)', cursor: 'pointer',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: 'var(--ink-3)' }}>
                <span className="mono">{c.changeCode ?? `#${c.id}`}</span>
                <Badge tone={STATUS_TONE[c.status] ?? 'neutral'} dot>{c.status.replace(/_/g, ' ')}</Badge>
              </div>
              <div style={{ fontSize: 13, fontWeight: 600, marginTop: 2, color: 'var(--ink-1)' }}>
                {lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}
              </div>
            </button>
          ))}
        </div>
      </Card>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Card title={`${change.changeCode ?? '#' + change.id} · ${lang === 'ar' && change.titleAr ? change.titleAr : change.titleEn}`}
              Icon={GitBranch}
              action={<>
                <Badge tone={STATUS_TONE[change.status] ?? 'neutral'} dot>{change.status.replace(/_/g, ' ')}</Badge>
                <Badge tone={TYPE_TONE[change.changeType] ?? 'neutral'}>{change.changeType}</Badge>
                {change.riskLevel && <Badge tone={RISK_TONE[change.riskLevel] ?? 'neutral'}>{change.riskLevel}</Badge>}
              </>}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, fontSize: 13 }}>
            <Field label={t('changeFldRequestedBy')} value={change.requestedByName} />
            <Field label={t('changeFldOwner')}       value={change.changeOwnerName} />
            <Field label={t('changeFldImplementer')} value={change.implementerName} />
            <Field label={t('changeFldCategory')}    value={change.category} />
            <Field label={t('changeFldScheduledStart')} value={change.scheduledStart ? new Date(change.scheduledStart).toLocaleString() : '—'} />
            <Field label={t('changeFldScheduledEnd')}   value={change.scheduledEnd   ? new Date(change.scheduledEnd).toLocaleString()   : '—'} />
          </div>
          <div style={{ marginTop: 10, display: 'grid', gap: 8 }}>
            {change.descriptionEn && <Block label={t('changeFldDescription')} body={change.descriptionEn} />}
            {change.impactSummary && <Block label={t('changeFldImpact')} body={change.impactSummary} />}
            {change.rollbackPlan  && <Block label={t('changeFldRollback')} body={change.rollbackPlan} />}
            {change.testPlan      && <Block label={t('changeFldTestPlan')} body={change.testPlan} />}
          </div>

          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 14 }}>
            {next && <Button variant="primary" Icon={ArrowRight} onClick={() => onTransition(change, next)}>{t('changeAdvanceTo')} {next.replace(/_/g, ' ')}</Button>}
            {change.status === 'CAB_PENDING' && (
              <>
                <Button variant="secondary" Icon={CheckCheck} onClick={() => castVote('APPROVE')}>{t('changeVoteApprove')}</Button>
                <Button variant="ghost"     Icon={XIcon}      onClick={() => castVote('REJECT')}>{t('changeVoteReject')}</Button>
                <Button variant="ghost"     Icon={Clock}      onClick={() => castVote('DEFER')}>{t('changeVoteDefer')}</Button>
              </>
            )}
            {change.status === 'IMPLEMENTING' && (
              <Button variant="ghost" Icon={Wrench} onClick={() => onTransition(change, 'ROLLED_BACK')}>{t('changeRollback')}</Button>
            )}
          </div>
        </Card>

        <Card title={t('changeApprovals')} Icon={Stamp}>
          {approvals.length === 0
            ? <div style={{ color: 'var(--ink-3)', fontSize: 13 }}>{t('changeNoApprovals')}</div>
            : approvals.map(a => (
                <div key={a.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--border)', fontSize: 13 }}>
                  <Badge tone={a.decision === 'APPROVE' ? 'success' : a.decision === 'REJECT' ? 'danger' : a.decision === 'DEFER' ? 'warning' : 'neutral'}>{a.decision}</Badge>
                  <div style={{ flex: 1 }}>
                    <strong>{a.approverName}</strong>{a.approverRole && <span style={{ color: 'var(--ink-3)' }}> · {a.approverRole}</span>}
                    {a.comments && <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>{a.comments}</div>}
                  </div>
                  {a.decidedAt && <span style={{ fontSize: 11, color: 'var(--ink-4)' }} className="mono">{new Date(a.decidedAt).toLocaleString()}</span>}
                </div>
              ))
          }
        </Card>

        <Card title={t('changeTasks')} Icon={ListChecks}
              action={<Button variant="secondary" Icon={Plus} onClick={addTask}>{t('changeAddTask')}</Button>}>
          {tasks.length === 0
            ? <div style={{ color: 'var(--ink-3)', fontSize: 13 }}>{t('changeNoTasks')}</div>
            : tasks.map(task => (
                <div key={task.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--border)' }}>
                  <button onClick={() => flipTask(task)} title={t('changeTaskFlip')}
                          style={{ background: 'transparent', border: '1px solid var(--border)', width: 22, height: 22, borderRadius: 'var(--r-sm)', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: task.status === 'DONE' ? 'var(--success)' : 'var(--ink-3)' }}>
                    {task.status === 'DONE' ? <CheckCheck size={12} /> : task.status === 'IN_PROGRESS' ? <Activity size={12} /> : <Clock size={12} />}
                  </button>
                  <span style={{ flex: 1, fontSize: 13, textDecoration: task.status === 'DONE' ? 'line-through' : 'none', color: task.status === 'DONE' ? 'var(--ink-3)' : 'var(--ink-1)' }}>
                    {lang === 'ar' && task.titleAr ? task.titleAr : task.titleEn}
                    {task.ownerName && <span style={{ marginInlineStart: 8, fontSize: 11, color: 'var(--ink-3)' }}>· {task.ownerName}</span>}
                  </span>
                  <Badge tone={task.status === 'DONE' ? 'success' : task.status === 'IN_PROGRESS' ? 'info' : task.status === 'BLOCKED' ? 'danger' : 'neutral'}>{task.status.replace(/_/g, ' ')}</Badge>
                  <button onClick={() => deleteTask(task)} title={t('deleteBtn')} style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--ink-3)' }}>
                    <Trash2 size={13} />
                  </button>
                </div>
              ))
          }
        </Card>

        <Card title={t('changeComments')} Icon={MessageSquare}>
          <div style={{ maxHeight: 240, overflowY: 'auto', marginBottom: 10 }}>
            {comments.length === 0
              ? <div style={{ color: 'var(--ink-3)', fontSize: 13 }}>{t('changeNoComments')}</div>
              : comments.map(c => (
                  <div key={c.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--border)' }}>
                    <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>
                      <strong style={{ color: 'var(--ink-2)' }}>{c.authorName}</strong>
                      {c.createdAt && <span className="mono" style={{ marginInlineStart: 8 }}>{new Date(c.createdAt).toLocaleString()}</span>}
                    </div>
                    <div style={{ fontSize: 13, color: 'var(--ink-1)', marginTop: 2 }}>{c.body}</div>
                  </div>
                ))
            }
          </div>
          <div style={{ display: 'flex', gap: 6 }}>
            <input type="text" value={commentBody} onChange={e => setCommentBody(e.target.value)}
                   placeholder={t('changeCommentPlaceholder')}
                   style={{ flex: 1, padding: 8, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface)', color: 'var(--ink-1)' }} />
            <Button variant="primary" Icon={Send} onClick={postComment}>{t('changeCommentPost')}</Button>
          </div>
        </Card>

        <button onClick={() => onChanged()} style={{ display: 'none' }}>refresh-poke</button>
      </div>
    </div>
  );
}

function Field({ label, value }: { label: string; value?: string | null }) {
  return <div>
    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</div>
    <div style={{ fontSize: 13, color: 'var(--ink-1)', fontWeight: 500 }}>{value ?? '—'}</div>
  </div>;
}
function Block({ label, body }: { label: string; body: string }) {
  return <div>
    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 }}>{label}</div>
    <div style={{ fontSize: 13, color: 'var(--ink-1)', whiteSpace: 'pre-wrap', background: 'var(--surface-2)', padding: 8, borderRadius: 'var(--r-sm)' }}>{body}</div>
  </div>;
}

// ─── Analytics ─────────────────────────────────────────────────────────────
function AnalyticsPanel({ analytics, t }: { analytics: ChangeAnalyticsDto | null; t: (k: string) => string }) {
  if (!analytics) return <Card><div style={{ padding: 20, color: 'var(--ink-3)' }}>{t('changeAnalyticsNoData')}</div></Card>;
  const status = analytics.byStatus, type = analytics.byType, outcome = analytics.byOutcome;
  const max = (m: Record<string, number>) => Math.max(1, ...Object.values(m));
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16 }}>
      <Card title={t('changeAnalyticsStatus')} Icon={BarChart3}>
        {Object.entries(status).filter(([, v]) => v > 0).map(([k, v]) => (
          <Progress key={k} label={`${k.replace(/_/g, ' ')} · ${v}`} value={Math.round((v / max(status)) * 100)}
                    tone={STATUS_TONE[k] === 'danger' ? 'danger' : STATUS_TONE[k] === 'success' ? 'success' : STATUS_TONE[k] === 'warning' ? 'accent' : 'primary'} />
        ))}
      </Card>
      <Card title={t('changeAnalyticsType')} Icon={KanbanSquare}>
        {Object.entries(type).map(([k, v]) => (
          <Progress key={k} label={`${k} · ${v}`} value={Math.round((v / max(type)) * 100)}
                    tone={k === 'EMERGENCY' ? 'danger' : k === 'STANDARD' ? 'success' : 'primary'} />
        ))}
      </Card>
      <Card title={t('changeAnalyticsOutcome')} Icon={ShieldCheck}
            action={<Badge tone={analytics.successRatePct >= 90 ? 'success' : analytics.successRatePct >= 70 ? 'primary' : 'accent'}>{analytics.successRatePct}% {t('changeKpiSuccess')}</Badge>}>
        {Object.entries(outcome).map(([k, v]) => (
          <Progress key={k} label={`${k.replace(/_/g, ' ')} · ${v}`} value={Math.round((v / max(outcome)) * 100)}
                    tone={k === 'SUCCESS' ? 'success' : k === 'PARTIAL_SUCCESS' ? 'primary' : 'danger'} />
        ))}
      </Card>
    </div>
  );
}

// ─── Integrations (links from a selected change to other modules) ──────────
function IntegrationsPanel({ items, detailId, setDetailId, onChanged, t, toast }: {
  items: ChangeRequestDto[];
  detailId: number | null; setDetailId: (id: number | null) => void;
  onChanged: () => void | Promise<void>;
  t: (k: string) => string;
  toast: ReturnType<typeof useToast>;
}) {
  const change = items.find(c => c.id === detailId) ?? null;
  const [links, setLinks] = useState<ChangeLinkDto[]>([]);

  async function reload() {
    if (!change) { setLinks([]); return; }
    try { setLinks(await changeApi.links(change.id)); } catch { /* silent */ }
  }
  useEffect(() => { void reload(); /* eslint-disable-next-line */ }, [detailId]);

  async function addLink() {
    if (!change) return;
    const entityType = window.prompt(t('changeLinkEntityType') + ' (' + ENTITY_TYPE_OPTIONS.map(o => o.value).join(', ') + ')', 'RISK');
    if (!entityType) return;
    const entityIdStr = window.prompt(t('changeLinkEntityId'), '');
    if (!entityIdStr) return;
    const entityId = Number(entityIdStr);
    if (Number.isNaN(entityId)) { toast.danger('Invalid id'); return; }
    const linkType = window.prompt(t('changeLinkType') + ' (' + LINK_TYPE_OPTIONS.map(o => o.value).join(', ') + ')', 'AFFECTS') ?? 'AFFECTS';
    try { await changeApi.addLink(change.id, { entityType, entityId, linkType }); void reload(); void onChanged(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function deleteLink(l: ChangeLinkDto) {
    if (!l.id) return;
    if (!window.confirm(t('changeLinkDeleteConfirm'))) return;
    try { await changeApi.deleteLink(l.id); void reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  if (!change) {
    return (
      <Card title={t('changeTabIntegrations')}>
        <EmptyState Icon={Link2} title={t('changeIntegrationsSelectTitle')} sub={t('changeIntegrationsSelectSub')} />
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 12 }}>
          {items.slice(0, 12).map(c => (
            <button key={c.id} onClick={() => setDetailId(c.id)} style={{ padding: '6px 10px', background: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 'var(--r-pill)', cursor: 'pointer', fontSize: 12 }}>
              {c.changeCode ?? `#${c.id}`}
            </button>
          ))}
        </div>
      </Card>
    );
  }

  return (
    <Card title={`${t('changeIntegrationsFor')} · ${change.changeCode ?? '#' + change.id}`}
          Icon={Link2}
          action={<Button variant="primary" Icon={Plus} onClick={addLink}>{t('changeLinkAdd')}</Button>}>
      {links.length === 0
        ? <div style={{ color: 'var(--ink-3)', fontSize: 13 }}>{t('changeNoLinks')}</div>
        : (
          <div style={{ display: 'grid', gap: 6 }}>
            {links.map(l => (
              <div key={l.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: 10, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)' }}>
                <Badge tone="primary">{l.entityType.replace(/_/g, ' ')}</Badge>
                <span className="mono" style={{ fontSize: 12 }}>#{l.entityId}</span>
                <Badge tone="info">{l.linkType.replace(/_/g, ' ')}</Badge>
                {l.notes && <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>{l.notes}</span>}
                <button onClick={() => deleteLink(l)} title={t('deleteBtn')} style={{ marginInlineStart: 'auto', background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--danger-fg)' }}>
                  <Trash2 size={14} />
                </button>
              </div>
            ))}
          </div>
        )
      }
    </Card>
  );
}

function Loading() {
  const { t } = useI18n();
  return <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>;
}
