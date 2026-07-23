import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  GitBranch, Filter, Plus, BarChart3, ListChecks, KanbanSquare, Activity,
  CheckCheck, X as XIcon, ArrowRight, AlertTriangle, ShieldCheck, Clock, Send,
  Wrench, Stamp, ChevronRight, MessageSquare, Link2, RefreshCw, Trash2, FolderOpen,
  ClipboardCheck, FileDown,
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
import { EntityDocsModal } from '../../components/ui/EntityDocsModal';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { ExportMenu } from '../../components/ui/ExportMenu';
import {
  changeApi, reportingApi, ApiError,
  type ChangeRequestDto, type ChangeApprovalDto, type ChangeTaskDto,
  type ChangeLinkDto, type ChangeCommentDto, type ChangeAnalyticsDto,
} from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { isAuthenticated, getUser } from '../../lib/auth';
import { CHANGE_REQUESTS, CHANGE_ANALYTICS } from './mockChanges';
import { CHANGE_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { CHANGE_ANCHORS } from '../../lib/moduleAnchors';
import { CommsPanel } from '../../components/comms/CommsPanel';
import { usePromoted } from '../../lib/promotedRecords';
import { toChangeRequestDto, usePromotedProvenance } from '../../lib/promotedAdapters';
import { PromotedBadge } from '../../components/intake/PromotedBadge';
import '../../styles/pages/changes.css';

type TabId = 'register' | 'kanban' | 'workflow' | 'analytics' | 'integrations' | 'comms';
const TAB_IDS: TabId[] = ['register', 'kanban', 'workflow', 'analytics', 'integrations', 'comms'];
// Changes eligible for a post-implementation review (the PIR process queue).
const REVIEW_STATUSES = ['IMPLEMENTED', 'ROLLED_BACK', 'POST_REVIEW'];

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
  impactSummary: '', rollbackPlan: '', testPlan: '', implementationPlan: '',
  businessJustification: '', requestedByName: '', changeOwnerName: '',
  scheduledStart: undefined, scheduledEnd: undefined,
};

export function ChangesPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const demoMode = !isAuthenticated();

  // Change-Enablement process-bar deep-links: ?tab= opens a tab; ?review=1 shows
  // changes awaiting a post-implementation review; ?type= filters by change type.
  const reviewMode = searchParams.get('review') === '1';
  const typeParam = (searchParams.get('type') ?? '').toUpperCase();
  const [tab, setTab] = useState<TabId>(() => {
    const tp = searchParams.get('tab') as TabId | null;
    return tp && TAB_IDS.includes(tp) ? tp : 'register';
  });
  const [apiItems, setItems] = useState<ChangeRequestDto[]>([]);
  // DD-71: include intake-promoted change requests in the visible register.
  const promotedChanges = usePromoted('CHANGE').map(toChangeRequestDto);
  const items = useMemo(() => [...promotedChanges, ...apiItems], [promotedChanges, apiItems]);
  const changeProv = usePromotedProvenance('CHANGE');
  // Register rows honour the process-bar deep-link filters (review queue / type).
  const registerFiltered = reviewMode || !!typeParam;
  const registerRows = useMemo(() => {
    let rows = items;
    if (reviewMode) rows = rows.filter(c => REVIEW_STATUSES.includes(c.status));
    if (typeParam)  rows = rows.filter(c => (c.changeType ?? '').toUpperCase() === typeParam);
    return rows;
  }, [items, reviewMode, typeParam]);
  const [loading, setLoading] = useState(false);
  const [analytics, setAnalytics] = useState<ChangeAnalyticsDto | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ChangeRequestDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<ChangeRequestDto | null>(null);
  const [docsTarget, setDocsTarget] = useState<ChangeRequestDto | null>(null);
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
    { id: 'comms',        label: t('detailTabComms') },
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
    { kind: 'textarea', key: 'implementationPlan',   label: t('changeFldImplPlan'), rows: 2, span: 12 },
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
    { header: t('changeFldCode'),   cell: c => <span className="mono changes-cell-mono-bold">{c.changeCode ?? '—'}</span>, width: '130px' },
    { header: t('colTitle'),        cell: c => <div className="changes-cell-title">{lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}{changeProv.has(c.id) && <PromotedBadge sourceReferenceCode={changeProv.get(c.id)!} />}</div> },
    { header: t('changeFldType'),   cell: c => <Badge tone={TYPE_TONE[c.changeType] ?? 'neutral'}>{c.changeType}</Badge>, width: '120px' },
    { header: t('changeFldRisk'),   cell: c => c.riskLevel ? <Badge tone={RISK_TONE[c.riskLevel] ?? 'neutral'} dot>{c.riskLevel}</Badge> : '—', width: '110px' },
    { header: t('colStatus'),       cell: c => <Badge tone={STATUS_TONE[c.status] ?? 'neutral'} dot>{c.status.replace(/_/g, ' ')}</Badge>, width: '170px' },
    { header: t('changeFldScheduledStart'), cell: c => c.scheduledStart ? new Date(c.scheduledStart).toLocaleDateString() : '—', mono: true, width: '120px' },
    { header: '', align: 'end', width: '160px',
      cell: c => <RowActions onEdit={() => { setEditing(c); setModalOpen(true); }}
                              onDelete={() => setConfirmDel(c)}
                              extras={[
                                { Icon: FolderOpen, onClick: () => setDocsTarget(c), title: lang === 'ar' ? 'المستندات والضوابط' : 'Docs & controls' },
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
            <Button variant="secondary" Icon={Filter}
              onClick={() => window.dispatchEvent(new CustomEvent('grc:open-palette'))}>
              {t('actionFilter')}
            </Button>
            <ExportMenu
              demoMode={demoMode} t={t} toast={toast}
              onPdf={() => reportingApi.changeRegisterPdf(lang)}
              onDocx={() => reportingApi.changeRegisterDocx(lang)}
              onXlsx={() => reportingApi.changeRegisterXlsx()}
            />
            {/* DD-76: route new change requests through Services Center so the
                risk-assessment fields (DD-70) get filled and a CAB gate is computed. */}
            <Button variant="primary" Icon={Plus} onClick={() => navigate('/intake/change_request')}>{t('changeNew')}</Button>
          </>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={GitBranch}    label={t('changeKpiTotal')}   value={total}     tone="primary" to="/changes" />
        <Kpi Icon={Clock}        label={t('changeKpiInFlight')} value={inFlight}  tone="info" onClick={() => setTab('register')} />
        <Kpi Icon={AlertTriangle} label={t('changeKpiOverdue')} value={overdue}  tone="danger" onClick={() => setTab('register')} />
        <Kpi Icon={ShieldCheck}  label={t('changeKpiSuccess')} value={`${successPct}%`} tone="success" onClick={() => setTab('analytics')} />
      </div>

      <OverviewStrip
        storageKey="overview.changes"
        processBarTitle={t('changeProcessBarTitle')}
        processBarSubtitle={t('changeProcessBarSub')}
        lifecycles={CHANGE_LIFECYCLES}
        postureTitle={t('changePostureTitle')}
        anchors={CHANGE_ANCHORS}
        />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'register' && (
        <>
          {registerFiltered && (
            <div className="changes-filter-banner">
              <span>
                {reviewMode ? t('changeReviewQueue')
                            : `${typeParam} ${lang === 'ar' ? 'تغييرات' : 'changes'}`}
                <span className="changes-filter-banner__count"> · {registerRows.length}</span>
              </span>
              <button type="button" onClick={() => setSearchParams({})} className="changes-filter-banner__clear">
                <XIcon size={13} /> {t('changeFilterClear')}
              </button>
            </div>
          )}
          <Card padded={false}>
            {loading ? <Loading /> :
              <DataTable columns={cols} rows={registerRows}
                         empty={<EmptyState Icon={GitBranch}
                           title={registerFiltered ? t('changeReviewEmptyTitle') : t('changeEmptyTitle')}
                           sub={registerFiltered ? t('changeReviewEmptySub') : t('changeEmptySub')} />} />
            }
          </Card>
        </>
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

      {tab === 'comms' && <CommsPanel module="CHANGE" />}

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

      <EntityDocsModal
        entityType="CHANGE_REQUEST"
        entityId={docsTarget?.id ?? null}
        open={!!docsTarget}
        recordLabel={docsTarget?.changeCode ?? undefined}
        onClose={() => setDocsTarget(null)}
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
    <div className="changes-kanban">
      {KANBAN_LANES.map(lane => {
        const lst = items.filter(c => lane.statuses.includes(c.status));
        return (
          <div key={lane.id} className="changes-kanban__lane">
            <div className="changes-kanban__lane-head">
              <span className="changes-kanban__lane-title">{lane.id}</span>
              <Badge tone="neutral">{lst.length}</Badge>
            </div>
            <div className="changes-kanban__cards">
              {lst.map(c => (
                <div key={c.id} onClick={() => onOpen(c.id)} className="changes-kanban__card">
                  <div className="changes-kanban__card-head">
                    <span className="mono changes-kanban__card-code">{c.changeCode ?? `#${c.id}`}</span>
                    <Badge tone={TYPE_TONE[c.changeType] ?? 'neutral'}>{c.changeType}</Badge>
                  </div>
                  <div className="changes-kanban__card-title">
                    {lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}
                  </div>
                  <div className="changes-kanban__card-meta">
                    {c.riskLevel && <Badge tone={RISK_TONE[c.riskLevel] ?? 'neutral'} dot>{c.riskLevel}</Badge>}
                    {c.scheduledStart && <span className="mono changes-kanban__card-date">{new Date(c.scheduledStart).toLocaleDateString()}</span>}
                    <button onClick={(e) => { e.stopPropagation(); void onTransition(c, nextStatus(c) ?? c.status); }}
                            disabled={!nextStatus(c)}
                            className="changes-kanban__transition-btn">
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
  const dialog = useDialog();
  // CH-8: real themed modals replace the old window.prompt / window.confirm flows.
  const [voteDecision, setVoteDecision] = useState<'APPROVE' | 'REJECT' | 'DEFER' | 'ABSTAIN' | null>(null);
  const [taskOpen, setTaskOpen] = useState(false);
  const voteInitial = useMemo(() => ({ approverName: getUser()?.username ?? '', approverRole: '', comments: '' }), [voteDecision]);
  const taskInitial = useMemo(() => ({ titleEn: '', ownerName: '', dueDate: '' }), []);

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

  function castVote(decision: 'APPROVE' | 'REJECT' | 'DEFER' | 'ABSTAIN') {
    setVoteDecision(decision);
  }
  async function submitVote(v: { approverName: string; approverRole: string; comments: string }) {
    if (!voteDecision) return;
    try {
      await changeApi.approve(change!.id, {
        approverName: v.approverName, approverRole: v.approverRole,
        decision: voteDecision, comments: v.comments,
      });
      toast.success(t('changeVoteRecorded'));
      setVoteDecision(null);
      void reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  function addTask() { setTaskOpen(true); }
  // CH-5 captures owner + due date so the SLA scheduler can act on the task;
  // CH-8 moves that capture out of chained window.prompt calls into a real modal.
  async function submitTask(v: { titleEn: string; ownerName: string; dueDate: string }) {
    const dueAt = /^\d{4}-\d{2}-\d{2}$/.test(v.dueDate) ? `${v.dueDate}T17:00:00` : undefined;
    try {
      await changeApi.addTask(change!.id, { titleEn: v.titleEn, status: 'PENDING',
        ownerName: v.ownerName || undefined, dueAt });
      setTaskOpen(false);
      void reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function flipTask(task: ChangeTaskDto) {
    const next = task.status === 'PENDING' ? 'IN_PROGRESS' : task.status === 'IN_PROGRESS' ? 'DONE' : 'PENDING';
    try { await changeApi.updateTask(task.id!, { status: next }); void reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function deleteTask(task: ChangeTaskDto) {
    const ok = await dialog.confirm({ title: t('deleteBtn'), body: t('changeTaskDeleteConfirm'),
      destructive: true, submitLabel: t('deleteBtn') });
    if (!ok) return;
    try { await changeApi.deleteTask(task.id!); void reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function postComment() {
    if (!commentBody.trim()) return;
    try {
      // CH-8: attribute the comment to the signed-in user, not a hardcoded 'You'.
      await changeApi.comment(change!.id, { authorName: getUser()?.username ?? 'You', body: commentBody });
      setCommentBody(''); void reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const next = nextStatus(change);

  return (
    <div className="changes-workflow-grid">
      <Card title={t('changeWorkflowList')} Icon={ListChecks} padded={false}>
        <div className="changes-list-pane">
          {items.map(c => (
            <button key={c.id} onClick={() => setDetailId(c.id)}
              className={`changes-list-item${c.id === detailId ? ' is-selected' : ''}`}>
              <div className="changes-list-item__head">
                <span className="mono">{c.changeCode ?? `#${c.id}`}</span>
                <Badge tone={STATUS_TONE[c.status] ?? 'neutral'} dot>{c.status.replace(/_/g, ' ')}</Badge>
              </div>
              <div className="changes-list-item__title">
                {lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}
              </div>
            </button>
          ))}
        </div>
      </Card>

      <div className="changes-detail-stack">
        <Card title={`${change.changeCode ?? '#' + change.id} · ${lang === 'ar' && change.titleAr ? change.titleAr : change.titleEn}`}
              Icon={GitBranch}
              action={<>
                <Badge tone={STATUS_TONE[change.status] ?? 'neutral'} dot>{change.status.replace(/_/g, ' ')}</Badge>
                <Badge tone={TYPE_TONE[change.changeType] ?? 'neutral'}>{change.changeType}</Badge>
                {change.riskLevel && <Badge tone={RISK_TONE[change.riskLevel] ?? 'neutral'}>{change.riskLevel}</Badge>}
              </>}>
          <div className="changes-detail-grid">
            <Field label={t('changeFldRequestedBy')} value={change.requestedByName} />
            <Field label={t('changeFldOwner')}       value={change.changeOwnerName} />
            <Field label={t('changeFldImplementer')} value={change.implementerName} />
            <Field label={t('changeFldCategory')}    value={change.category} />
            <Field label={t('changeFldScheduledStart')} value={change.scheduledStart ? new Date(change.scheduledStart).toLocaleString() : '—'} />
            <Field label={t('changeFldScheduledEnd')}   value={change.scheduledEnd   ? new Date(change.scheduledEnd).toLocaleString()   : '—'} />
          </div>
          <div className="changes-blocks">
            {change.descriptionEn && <Block label={t('changeFldDescription')} body={change.descriptionEn} />}
            {change.businessJustification && <Block label={t('changeFldJustification')} body={change.businessJustification} />}
            {change.impactSummary && <Block label={t('changeFldImpact')} body={change.impactSummary} />}
            {change.implementationPlan && <Block label={t('changeFldImplPlan')} body={change.implementationPlan} />}
            {change.rollbackPlan  && <Block label={t('changeFldRollback')} body={change.rollbackPlan} />}
            {change.testPlan      && <Block label={t('changeFldTestPlan')} body={change.testPlan} />}
          </div>

          <div className="changes-actions-row">
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
            <Button variant="secondary" Icon={FileDown} onClick={async () => {
              try { await reportingApi.cabPackPdf(change!.id, lang); }
              catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Export failed'); }
            }}>{t('changeCabPack')}</Button>
          </div>
        </Card>

        <PirCard change={change} onChanged={onChanged} toast={toast} />

        <Card title={t('changeApprovals')} Icon={Stamp}>
          {approvals.length === 0
            ? <div className="changes-empty-note">{t('changeNoApprovals')}</div>
            : approvals.map(a => (
                <div key={a.id} className="changes-approval-row">
                  <Badge tone={a.decision === 'APPROVE' ? 'success' : a.decision === 'REJECT' ? 'danger' : a.decision === 'DEFER' ? 'warning' : 'neutral'}>{a.decision}</Badge>
                  <div className="changes-approval-row__main">
                    <strong>{a.approverName}</strong>{a.approverRole && <span className="changes-approval-row__role"> · {a.approverRole}</span>}
                    {a.comments && <div className="changes-approval-row__comments">{a.comments}</div>}
                  </div>
                  {a.decidedAt && <span className="mono changes-approval-row__time">{new Date(a.decidedAt).toLocaleString()}</span>}
                </div>
              ))
          }
        </Card>

        <Card title={t('changeTasks')} Icon={ListChecks}
              action={<Button variant="secondary" Icon={Plus} onClick={addTask}>{t('changeAddTask')}</Button>}>
          {tasks.length === 0
            ? <div className="changes-empty-note">{t('changeNoTasks')}</div>
            : tasks.map(task => (
                <div key={task.id} className="changes-task-row">
                  <button onClick={() => flipTask(task)} title={t('changeTaskFlip')}
                          data-status={task.status}
                          className="changes-task-btn">
                    {task.status === 'DONE' ? <CheckCheck size={12} /> : task.status === 'IN_PROGRESS' ? <Activity size={12} /> : <Clock size={12} />}
                  </button>
                  <span className="changes-task-label" data-done={task.status === 'DONE' ? 'true' : 'false'}>
                    {lang === 'ar' && task.titleAr ? task.titleAr : task.titleEn}
                    {task.ownerName && <span className="changes-task-owner">· {task.ownerName}</span>}
                  </span>
                  <Badge tone={task.status === 'DONE' ? 'success' : task.status === 'IN_PROGRESS' ? 'info' : task.status === 'BLOCKED' ? 'danger' : 'neutral'}>{task.status.replace(/_/g, ' ')}</Badge>
                  <button onClick={() => deleteTask(task)} title={t('deleteBtn')} className="changes-task-del">
                    <Trash2 size={13} />
                  </button>
                </div>
              ))
          }
        </Card>

        <Card title={t('changeComments')} Icon={MessageSquare}>
          <div className="changes-comments-scroll">
            {comments.length === 0
              ? <div className="changes-empty-note">{t('changeNoComments')}</div>
              : comments.map(c => (
                  <div key={c.id} className="changes-comment-item">
                    <div className="changes-comment-meta">
                      <strong className="changes-comment-author">{c.authorName}</strong>
                      {c.createdAt && <span className="mono changes-comment-time">{new Date(c.createdAt).toLocaleString()}</span>}
                    </div>
                    <div className="changes-comment-body">{c.body}</div>
                  </div>
                ))
            }
          </div>
          <div className="changes-comment-form">
            <input type="text" value={commentBody} onChange={e => setCommentBody(e.target.value)}
                   placeholder={t('changeCommentPlaceholder')}
                   className="changes-comment-input" />
            <Button variant="primary" Icon={Send} onClick={postComment}>{t('changeCommentPost')}</Button>
          </div>
        </Card>

        <FormModal
          open={voteDecision !== null}
          title={`${t('changeVoteModalTitle')} · ${voteDecision ?? ''}`}
          size="md"
          fields={[
            { kind: 'text', key: 'approverName', label: t('changeApproverName'), required: true, span: 12 },
            { kind: 'text', key: 'approverRole', label: t('changeApproverRole'), span: 12 },
            { kind: 'textarea', key: 'comments', label: t('changeApproverComments'), rows: 3, span: 12 },
          ]}
          initial={voteInitial}
          submitLabel={t('changeVoteRecordBtn')}
          onClose={() => setVoteDecision(null)}
          onSubmit={submitVote}
        />
        <FormModal
          open={taskOpen}
          title={t('changeAddTask')}
          size="md"
          fields={[
            { kind: 'text', key: 'titleEn', label: t('changeTaskTitle'), required: true, span: 12 },
            { kind: 'text', key: 'ownerName', label: t('changeTaskOwner'), span: 6 },
            { kind: 'date', key: 'dueDate', label: t('changeTaskDueDate'), span: 6 },
          ]}
          initial={taskInitial}
          submitLabel={t('changeAddTask')}
          onClose={() => setTaskOpen(false)}
          onSubmit={submitTask}
        />
        <button onClick={() => onChanged()} className="changes-refresh-hidden">refresh-poke</button>
      </div>
    </div>
  );
}

function Field({ label, value }: { label: string; value?: string | null }) {
  return <div>
    <div className="changes-field-label">{label}</div>
    <div className="changes-field-value">{value ?? '—'}</div>
  </div>;
}
function Block({ label, body }: { label: string; body: string }) {
  return <div>
    <div className="changes-block-label">{label}</div>
    <div className="changes-block-body">{body}</div>
  </div>;
}

// ─── CH-11: post-implementation review (PIR) ───────────────────────────────
// Surfaced once a change reaches an implemented/rolled-back state. Editable
// until the change is CLOSED (a NORMAL/EMERGENCY change cannot close without a
// PIR summary — the backend enforces the mandatory-retrospective gate). The
// review sign-off (who/when) is stamped by the POST_REVIEW transition server-side.
const PIR_STATES = ['IMPLEMENTED', 'POST_REVIEW', 'ROLLED_BACK', 'CLOSED'];
function PirCard({ change, onChanged, toast }: {
  change: ChangeRequestDto;
  onChanged: () => void | Promise<void>;
  toast: ReturnType<typeof useToast>;
}) {
  const { t } = useI18n();
  const locked = change.status === 'CLOSED';
  const [summary, setSummary] = useState('');
  const [met, setMet] = useState<'' | 'true' | 'false'>('');
  const [lessons, setLessons] = useState('');
  const [followUp, setFollowUp] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setSummary(change.pirSummary ?? '');
    setMet(change.pirObjectivesMet === true ? 'true' : change.pirObjectivesMet === false ? 'false' : '');
    setLessons(change.pirLessons ?? '');
    setFollowUp(change.pirFollowUp ?? '');
  }, [change.id, change.pirSummary, change.pirObjectivesMet, change.pirLessons, change.pirFollowUp]);

  if (!PIR_STATES.includes(change.status)) return null;

  async function save() {
    setSaving(true);
    try {
      await changeApi.update(change.id, {
        pirSummary: summary || undefined,
        pirObjectivesMet: met === '' ? undefined : met === 'true',
        pirLessons: lessons || undefined,
        pirFollowUp: followUp || undefined,
      });
      toast.success(t('changePirSaved'));
      await onChanged();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
    finally { setSaving(false); }
  }

  const signoff = change.pirReviewedAt
    ? <Badge tone="success">{t('changePirReviewed')} · {new Date(change.pirReviewedAt).toLocaleDateString()}</Badge>
    : <Badge tone="warning">{t('changePirPending')}</Badge>;

  if (locked) {
    return (
      <Card title={t('changePirTitle')} Icon={ClipboardCheck} action={signoff}>
        <div className="changes-blocks">
          {change.pirSummary && <Block label={t('changePirSummary')} body={change.pirSummary} />}
          <Field label={t('changePirObjectives')}
                 value={change.pirObjectivesMet == null ? t('changePirUnset') : change.pirObjectivesMet ? t('changePirYes') : t('changePirNo')} />
          {change.pirLessons && <Block label={t('changePirLessons')} body={change.pirLessons} />}
          {change.pirFollowUp && <Block label={t('changePirFollowUp')} body={change.pirFollowUp} />}
          {change.pirReviewedBy && <Field label={t('changePirReviewedBy')} value={change.pirReviewedBy} />}
        </div>
      </Card>
    );
  }

  return (
    <Card title={t('changePirTitle')} Icon={ClipboardCheck} action={signoff}>
      <div className="changes-pir-form">
        <label className="changes-block-label">{t('changePirSummary')}</label>
        <textarea rows={2} value={summary} onChange={e => setSummary(e.target.value)} className="changes-pir-input" />
        <label className="changes-block-label">{t('changePirObjectives')}</label>
        <select value={met} onChange={e => setMet(e.target.value as '' | 'true' | 'false')} className="changes-pir-input">
          <option value="">{t('changePirUnset')}</option>
          <option value="true">{t('changePirYes')}</option>
          <option value="false">{t('changePirNo')}</option>
        </select>
        <label className="changes-block-label">{t('changePirLessons')}</label>
        <textarea rows={2} value={lessons} onChange={e => setLessons(e.target.value)} className="changes-pir-input" />
        <label className="changes-block-label">{t('changePirFollowUp')}</label>
        <textarea rows={2} value={followUp} onChange={e => setFollowUp(e.target.value)} className="changes-pir-input" />
        {change.pirReviewedBy && <Field label={t('changePirReviewedBy')} value={change.pirReviewedBy} />}
        <div className="changes-actions-row">
          <Button variant="primary" Icon={ClipboardCheck} onClick={save} disabled={saving}>{t('changePirSave')}</Button>
        </div>
      </div>
    </Card>
  );
}

// ─── Analytics ─────────────────────────────────────────────────────────────
function AnalyticsPanel({ analytics, t }: { analytics: ChangeAnalyticsDto | null; t: (k: string) => string }) {
  if (!analytics) return <Card><div className="changes-analytics-empty">{t('changeAnalyticsNoData')}</div></Card>;
  const status = analytics.byStatus, type = analytics.byType, outcome = analytics.byOutcome;
  const max = (m: Record<string, number>) => Math.max(1, ...Object.values(m));
  return (
    <div className="changes-analytics-grid">
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
  const dialog = useDialog();
  const [linkOpen, setLinkOpen] = useState(false);
  const linkInitial = useMemo(() => ({ entityType: 'RISK', entityId: undefined as number | undefined, linkType: 'AFFECTS' }), []);

  async function reload() {
    if (!change) { setLinks([]); return; }
    try { setLinks(await changeApi.links(change.id)); } catch { /* silent */ }
  }
  useEffect(() => { void reload(); /* eslint-disable-next-line */ }, [detailId]);

  function addLink() { if (change) setLinkOpen(true); }
  // CH-8: a real modal with proper <select> controls for entity/link type —
  // replaces three chained window.prompt calls that also allowed invalid free-text.
  async function submitLink(v: { entityType: string; entityId?: number; linkType: string }) {
    if (!change) return;
    const entityId = Number(v.entityId);
    if (!Number.isFinite(entityId)) { toast.danger(t('changeLinkInvalidId')); return; }
    try {
      await changeApi.addLink(change.id, { entityType: v.entityType, entityId, linkType: v.linkType });
      setLinkOpen(false); void reload(); void onChanged();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function deleteLink(l: ChangeLinkDto) {
    if (!l.id) return;
    const ok = await dialog.confirm({ title: t('changeLinkRemove'), body: t('changeLinkDeleteConfirm'),
      destructive: true, submitLabel: t('changeLinkRemove') });
    if (!ok) return;
    try { await changeApi.deleteLink(l.id); void reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  if (!change) {
    return (
      <Card title={t('changeTabIntegrations')}>
        <EmptyState Icon={Link2} title={t('changeIntegrationsSelectTitle')} sub={t('changeIntegrationsSelectSub')} />
        <div className="changes-integration-tags">
          {items.slice(0, 12).map(c => (
            <button key={c.id} onClick={() => setDetailId(c.id)} className="changes-integration-pill">
              {c.changeCode ?? `#${c.id}`}
            </button>
          ))}
        </div>
      </Card>
    );
  }

  return (
    <>
      <Card title={`${t('changeIntegrationsFor')} · ${change.changeCode ?? '#' + change.id}`}
          Icon={Link2}
          action={<Button variant="primary" Icon={Plus} onClick={addLink}>{t('changeLinkAdd')}</Button>}>
      {links.length === 0
        ? <div className="changes-empty-note">{t('changeNoLinks')}</div>
        : (
          <div className="changes-integrations-list">
            {links.map(l => (
              <div key={l.id} className="changes-integration-row">
                <Badge tone="primary">{l.entityType.replace(/_/g, ' ')}</Badge>
                <span className="mono changes-integration-row__id">#{l.entityId}</span>
                <Badge tone="info">{l.linkType.replace(/_/g, ' ')}</Badge>
                {l.notes && <span className="changes-integration-row__notes">{l.notes}</span>}
                <button onClick={() => deleteLink(l)} title={t('deleteBtn')} className="changes-integration-row__del">
                  <Trash2 size={14} />
                </button>
              </div>
            ))}
          </div>
        )
      }
      </Card>
      <FormModal
        open={linkOpen}
        title={t('changeLinkAdd')}
        size="md"
        fields={[
          { kind: 'select', key: 'entityType', label: t('changeLinkEntityType'), required: true, span: 6, options: ENTITY_TYPE_OPTIONS },
          { kind: 'number', key: 'entityId', label: t('changeLinkEntityId'), required: true, span: 6, min: 1 },
          { kind: 'select', key: 'linkType', label: t('changeLinkType'), required: true, span: 12, options: LINK_TYPE_OPTIONS },
        ]}
        initial={linkInitial}
        submitLabel={t('changeLinkAdd')}
        onClose={() => setLinkOpen(false)}
        onSubmit={submitLink}
      />
    </>
  );
}

function Loading() {
  const { t } = useI18n();
  return <div className="changes-loading">{t('riskLoading')}</div>;
}
