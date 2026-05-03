import { useEffect, useMemo, useState } from 'react';
import {
  ShieldAlert, AlertTriangle, Filter, Plus, RefreshCw, BarChart3, ListChecks,
  CheckCheck, X as XIcon, Send, Activity, EyeOff, Stamp, Flame,
  ChevronRight, Search, Trash2, ArrowRight, Lock, MessageSquare,
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
import {
  ethicsCaseApi, disclosureApi, frameworkApi, ApiError,
  type EthicsCaseDto, type CaseActionDto, type CaseLinkDto, type EthicsAnalyticsDto,
  type DisclosureDto, type FrameworkDto,
} from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { isAuthenticated } from '../../lib/auth';

type TabId = 'register' | 'kanban' | 'workflow' | 'disclosures' | 'analytics' | 'integrations';

const STATUS_TONE: Record<string, BadgeTone> = {
  INTAKE: 'neutral', TRIAGE: 'info', INVESTIGATION: 'accent',
  SUBSTANTIATED: 'warning', UNSUBSTANTIATED: 'success', NO_VIOLATION: 'success',
  REMEDIATION: 'primary', ESCALATED: 'danger', DISMISSED: 'neutral', CLOSED: 'success',
};
const SEVERITY_TONE: Record<string, BadgeTone> = {
  LOW: 'success', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger',
};
const CATEGORY_OPTIONS = [
  'BRIBERY', 'KICKBACK', 'CONFLICT_OF_INTEREST', 'FRAUD', 'THEFT',
  'HARASSMENT', 'DISCRIMINATION', 'RETALIATION', 'DATA_MISUSE',
  'AML_SUSPICION', 'POLICY_VIOLATION', 'SAFETY', 'OTHER',
].map(v => ({ value: v, label: v.replace(/_/g, ' ') }));
const SEVERITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(v => ({ value: v, label: v }));
const CHANNEL_OPTIONS = ['HOTLINE', 'WEB_FORM', 'EMAIL', 'IN_PERSON', 'LETTER', 'REGULATOR']
  .map(v => ({ value: v, label: v.replace(/_/g, ' ') }));

const KANBAN_LANES = [
  { id: 'INTAKE',          statuses: ['INTAKE'] },
  { id: 'TRIAGE',          statuses: ['TRIAGE'] },
  { id: 'INVESTIGATION',   statuses: ['INVESTIGATION'] },
  { id: 'OUTCOME',         statuses: ['SUBSTANTIATED', 'UNSUBSTANTIATED', 'NO_VIOLATION'] },
  { id: 'REMEDIATION',     statuses: ['REMEDIATION', 'ESCALATED'] },
  { id: 'CLOSED',          statuses: ['CLOSED', 'DISMISSED'] },
];

function nextStatus(c: EthicsCaseDto): string | null {
  switch (c.status) {
    case 'INTAKE':         return 'TRIAGE';
    case 'TRIAGE':         return 'INVESTIGATION';
    case 'INVESTIGATION':  return 'SUBSTANTIATED';
    case 'SUBSTANTIATED':  return 'REMEDIATION';
    case 'UNSUBSTANTIATED': return 'CLOSED';
    case 'NO_VIOLATION':   return 'CLOSED';
    case 'REMEDIATION':    return 'CLOSED';
    case 'ESCALATED':      return 'CLOSED';
    default:                return null;
  }
}

const EMPTY: Partial<EthicsCaseDto> = {
  category: 'POLICY_VIOLATION', severity: 'MEDIUM', intakeChannel: 'HOTLINE',
  isAnonymous: false, titleEn: '', description: '',
};

export function EthicsPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const demoMode = !isAuthenticated();

  const [tab, setTab] = useState<TabId>('register');
  const [items, setItems] = useState<EthicsCaseDto[]>([]);
  const [analytics, setAnalytics] = useState<EthicsAnalyticsDto | null>(null);
  const [frameworks, setFrameworks] = useState<FrameworkDto[]>([]);
  const [loading, setLoading] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<EthicsCaseDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<EthicsCaseDto | null>(null);
  const [busy, setBusy] = useState(false);
  const [detailId, setDetailId] = useState<number | null>(null);

  async function load() {
    if (demoMode) { setItems([]); setAnalytics(null); return; }
    setLoading(true);
    try {
      const [page, an, fws] = await Promise.all([
        ethicsCaseApi.list({ size: 200 }),
        ethicsCaseApi.analytics().catch(() => null),
        frameworkApi.list().catch(() => [] as FrameworkDto[]),
      ]);
      setItems(page.content ?? []);
      setAnalytics(an);
      setFrameworks(fws);
    } finally { setLoading(false); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, []);

  const ethicsFwOptions = useMemo(
    () => frameworks.filter(f => f.family === 'ETHICS' || f.family === 'FINCRIME')
                    .map(f => ({ value: String(f.id), label: f.code })),
    [frameworks]
  );

  const total       = items.length;
  const open        = items.filter(c => !['CLOSED', 'DISMISSED'].includes(c.status)).length;
  const critical    = items.filter(c => c.severity === 'CRITICAL').length;
  const substantPct = analytics && analytics.closed + analytics.substantiated > 0
    ? Math.round((analytics.substantiated / (analytics.closed + analytics.substantiated)) * 100)
    : 0;

  const tabs: TabItem<TabId>[] = [
    { id: 'register',     label: t('ethicsTabRegister') },
    { id: 'kanban',       label: t('ethicsTabKanban') },
    { id: 'workflow',     label: t('ethicsTabWorkflow') },
    { id: 'disclosures',  label: t('ethicsTabDisclosures') },
    { id: 'analytics',    label: t('ethicsTabAnalytics') },
    { id: 'integrations', label: t('ethicsTabIntegrations') },
  ];

  const FIELDS: FieldDef[] = [
    { kind: 'text',     key: 'caseCode',          label: t('ethicsFldCode'), span: 4, placeholder: 'CASE-2025-0042' },
    { kind: 'select',   key: 'category',          label: t('ethicsFldCategory') + ' *', required: true, options: CATEGORY_OPTIONS, span: 4 },
    { kind: 'select',   key: 'severity',          label: t('ethicsFldSeverity') + ' *', required: true, options: SEVERITY_OPTIONS, span: 4 },
    { kind: 'text',     key: 'titleEn',           label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 500, span: 6 },
    { kind: 'text',     key: 'titleAr',           label: t('fldTitleArLbl'), maxLength: 500, span: 6 },
    { kind: 'select',   key: 'intakeChannel',     label: t('ethicsFldChannel'), options: CHANNEL_OPTIONS, span: 4 },
    { kind: 'select',   key: 'frameworkId',       label: t('ethicsFldFramework'), options: ethicsFwOptions, span: 4 },
    { kind: 'text',     key: 'clauseRef',         label: t('frameworkClauseRef'), span: 4 },
    { kind: 'text',     key: 'reporterName',      label: t('ethicsFldReporterName') + ' (' + t('ethicsFldOptional') + ')', span: 6 },
    { kind: 'text',     key: 'reporterEmail',     label: t('ethicsFldReporterEmail'), span: 6 },
    { kind: 'text',     key: 'location',          label: t('ethicsFldLocation'), span: 6 },
    { kind: 'date',     key: 'dateOfIncident',    label: t('ethicsFldIncidentDate'), span: 6 },
    { kind: 'textarea', key: 'subjectSummary',    label: t('ethicsFldSubject'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'description',       label: t('ethicsFldDescription') + ' *', rows: 4, span: 12 },
  ];

  function reqFromDto(c: EthicsCaseDto): Partial<EthicsCaseDto> {
    return { ...c, frameworkId: c.frameworkId };
  }

  async function submitForm(values: Partial<EthicsCaseDto>) {
    try {
      // Normalise frameworkId from string (FormModal selects emit strings)
      const v: Partial<EthicsCaseDto> = { ...values };
      if (typeof v.frameworkId === 'string') v.frameworkId = Number(v.frameworkId);
      if (editing) await ethicsCaseApi.update(editing.id, v);
      else await ethicsCaseApi.create(v);
      toast.success(editing ? t('ethicsCaseUpdated') : t('ethicsCaseCreated'));
      setModalOpen(false); setEditing(null);
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }

  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await ethicsCaseApi.remove(confirmDel.id); toast.success('Case deleted'); await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }

  async function transition(c: EthicsCaseDto, target: string) {
    try { await ethicsCaseApi.transition(c.id, { target }); toast.success(`→ ${target}`); await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Transition failed'); }
  }

  const cols: Column<EthicsCaseDto>[] = [
    { header: t('ethicsFldCode'),     cell: c => <span className="mono" style={{ fontWeight: 600 }}>{c.caseCode ?? '—'}</span>, width: '130px' },
    { header: t('colTitle'),          cell: c => (
      <div>
        <div style={{ fontWeight: 600 }}>{lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}</div>
        {c.isAnonymous && <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 10, color: 'var(--ink-3)' }}><EyeOff size={10} /> {t('ethicsAnonymous')}</span>}
      </div>
    ) },
    { header: t('ethicsFldCategory'), cell: c => <Badge tone="primary">{c.category.replace(/_/g, ' ')}</Badge>, width: '160px' },
    { header: t('ethicsFldSeverity'), cell: c => <Badge tone={SEVERITY_TONE[c.severity] ?? 'neutral'} dot>{c.severity}</Badge>, width: '110px' },
    { header: t('colStatus'),         cell: c => <Badge tone={STATUS_TONE[c.status] ?? 'neutral'} dot>{c.status.replace(/_/g, ' ')}</Badge>, width: '160px' },
    { header: t('ethicsFldChannel'),  cell: c => c.intakeChannel.replace(/_/g, ' '), width: '110px' },
    { header: '', align: 'end', width: '160px',
      cell: c => <RowActions
        onEdit={() => { setEditing(c); setModalOpen(true); }}
        onDelete={() => setConfirmDel(c)}
        extras={[
          { Icon: ChevronRight, onClick: () => { setDetailId(c.id); setTab('workflow'); }, title: t('ethicsActOpen'), tone: 'primary' },
        ]} /> },
  ];

  return (
    <Shell title={t('modEthics')} sub="Anti-bribery · AML · Code of Conduct">
      <PageHeader
        eyebrow={t('ethicsEyebrow')}
        title={t('ethicsTitle')}
        sub={t('ethicsSub')}
        actions={
          <>
            {demoMode && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={RefreshCw} onClick={load} disabled={loading}>{t('dashRefresh')}</Button>
            <Button variant="secondary" Icon={Filter}>{t('actionFilter')}</Button>
            <Button variant="primary" Icon={Plus} onClick={() => { setEditing(null); setModalOpen(true); }}>{t('ethicsNew')}</Button>
          </>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={ShieldAlert}    label={t('ethicsKpiTotal')}        value={total}     tone="primary" />
        <Kpi Icon={Activity}       label={t('ethicsKpiOpen')}         value={open}      tone="info" />
        <Kpi Icon={Flame}          label={t('ethicsKpiCritical')}     value={critical}  tone="danger" />
        <Kpi Icon={Stamp}          label={t('ethicsKpiSubstantiated')} value={`${substantPct}%`} tone="accent" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'register' && (
        <Card padded={false}>
          {loading ? <Loading /> :
            <DataTable columns={cols} rows={items}
                       empty={<EmptyState Icon={ShieldAlert} title={t('ethicsEmptyTitle')} sub={t('ethicsEmptySub')} />} />
          }
        </Card>
      )}

      {tab === 'kanban' && (
        <KanbanBoard items={items} onTransition={(c) => transition(c, nextStatus(c) ?? c.status)}
                     onOpen={(id) => { setDetailId(id); setTab('workflow'); }}
                     t={t} lang={lang} />
      )}

      {tab === 'workflow' && (
        <WorkflowPanel items={items}
                       detailId={detailId} setDetailId={setDetailId}
                       onTransition={transition}
                       t={t} lang={lang} toast={toast}
                       reload={load} />
      )}

      {tab === 'disclosures' && <DisclosuresTab t={t} lang={lang} toast={toast} demoMode={demoMode} ethicsFwOptions={ethicsFwOptions} />}

      {tab === 'analytics' && <AnalyticsPanel analytics={analytics} t={t} />}

      {tab === 'integrations' && <IntegrationsPanel items={items} detailId={detailId} setDetailId={setDetailId}
                                                    t={t} toast={toast} />}

      <FormModal<Partial<EthicsCaseDto>>
        open={modalOpen}
        title={editing ? `${t('editPrefix')} · ${editing.caseCode ?? editing.titleEn}` : t('ethicsNew')}
        fields={FIELDS}
        initial={editing ? reqFromDto(editing) : EMPTY}
        onClose={() => { setModalOpen(false); setEditing(null); }}
        onSubmit={submitForm}
      />

      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title={t('ethicsConfirmDelete')}
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
  items: EthicsCaseDto[];
  onTransition: (c: EthicsCaseDto) => void | Promise<void>;
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
                  borderRadius: 'var(--r-sm)', padding: 8, cursor: 'pointer', fontSize: 12,
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{c.caseCode ?? `#${c.id}`}</span>
                    <Badge tone={SEVERITY_TONE[c.severity] ?? 'neutral'}>{c.severity}</Badge>
                    {c.isAnonymous && <Lock size={10} style={{ color: 'var(--ink-3)' }} />}
                  </div>
                  <div style={{ fontWeight: 600, color: 'var(--ink-1)', marginBottom: 4 }}>
                    {lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <Badge tone="primary">{c.category.replace(/_/g, ' ')}</Badge>
                    <button onClick={(e) => { e.stopPropagation(); void onTransition(c); }}
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

// ─── Workflow detail panel ─────────────────────────────────────────────────
function WorkflowPanel({ items, detailId, setDetailId, onTransition, t, lang, toast, reload }: {
  items: EthicsCaseDto[];
  detailId: number | null; setDetailId: (id: number | null) => void;
  onTransition: (c: EthicsCaseDto, target: string) => void | Promise<void>;
  t: (k: string) => string; lang: string;
  toast: ReturnType<typeof useToast>;
  reload: () => Promise<void>;
}) {
  const c = items.find(x => x.id === detailId) ?? null;
  const [actions, setActions] = useState<CaseActionDto[]>([]);

  async function loadActions() {
    if (!c) { setActions([]); return; }
    try { setActions(await ethicsCaseApi.actions(c.id)); } catch { /* silent */ }
  }
  useEffect(() => { void loadActions(); /* eslint-disable-next-line */ }, [detailId, items]);

  if (!c) {
    return (
      <Card title={t('ethicsTabWorkflow')}>
        <EmptyState Icon={ListChecks} title={t('ethicsWorkflowSelectTitle')} sub={t('ethicsWorkflowSelectSub')} />
      </Card>
    );
  }

  async function addAction() {
    const at = window.prompt(t('ethicsActionTypePrompt'), 'INTERVIEW');
    if (!at) return;
    const title = window.prompt(t('ethicsActionTitlePrompt'), '');
    if (!title) return;
    const description = window.prompt(t('ethicsActionDescPrompt'), '') ?? '';
    try { await ethicsCaseApi.addAction(c!.id, { actionType: at, title, description }); void loadActions(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const next = nextStatus(c);

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '320px 1fr', gap: 16 }}>
      <Card title={t('ethicsCaseList')} Icon={ListChecks} padded={false}>
        <div style={{ maxHeight: 460, overflowY: 'auto' }}>
          {items.map(it => (
            <button key={it.id} onClick={() => setDetailId(it.id)} style={{
              display: 'block', width: '100%', textAlign: 'start', padding: 10,
              background: it.id === detailId ? 'var(--surface-2)' : 'transparent',
              border: 'none', borderBottom: '1px solid var(--border)', cursor: 'pointer',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: 'var(--ink-3)' }}>
                <span className="mono">{it.caseCode ?? `#${it.id}`}</span>
                <Badge tone={STATUS_TONE[it.status] ?? 'neutral'} dot>{it.status.replace(/_/g, ' ')}</Badge>
              </div>
              <div style={{ fontSize: 13, fontWeight: 600, marginTop: 2, color: 'var(--ink-1)' }}>
                {lang === 'ar' && it.titleAr ? it.titleAr : it.titleEn}
              </div>
            </button>
          ))}
        </div>
      </Card>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Card
          title={`${c.caseCode ?? '#' + c.id} · ${lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}`}
          Icon={ShieldAlert}
          action={<>
            <Badge tone={STATUS_TONE[c.status] ?? 'neutral'} dot>{c.status.replace(/_/g, ' ')}</Badge>
            <Badge tone={SEVERITY_TONE[c.severity] ?? 'neutral'}>{c.severity}</Badge>
            <Badge tone="primary">{c.category.replace(/_/g, ' ')}</Badge>
            {c.regulatorNotified && <Badge tone="danger" dot>{t('ethicsRegulatorNotified')}</Badge>}
          </>}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, fontSize: 13 }}>
            <Field label={t('ethicsFldChannel')} value={c.intakeChannel.replace(/_/g, ' ')} />
            <Field label={t('ethicsFldReporter')}
                   value={c.isAnonymous ? t('ethicsAnonymous') : (c.reporterName ?? '—')} />
            <Field label={t('ethicsFldInvestigator')} value={c.investigatorName ?? '—'} />
            <Field label={t('ethicsFldCommittee')}    value={c.committeeName ?? '—'} />
            <Field label={t('ethicsFldLocation')}     value={c.location ?? '—'} />
            <Field label={t('ethicsFldIncidentDate')} value={c.dateOfIncident ?? '—'} />
          </div>
          <div style={{ marginTop: 12 }}>
            <Block label={t('ethicsFldSubject')} body={c.subjectSummary} />
            <Block label={t('ethicsFldDescription')} body={c.description} />
            {c.outcomeSummary && <Block label={t('ethicsFldOutcomeSummary')} body={c.outcomeSummary} />}
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 14 }}>
            {next && <Button variant="primary" Icon={ArrowRight} onClick={() => onTransition(c, next)}>{t('changeAdvanceTo')} {next.replace(/_/g, ' ')}</Button>}
            {c.status === 'INVESTIGATION' && (
              <>
                <Button variant="secondary" Icon={CheckCheck} onClick={() => onTransition(c, 'SUBSTANTIATED')}>{t('ethicsOutcomeSubstantiated')}</Button>
                <Button variant="ghost"     Icon={XIcon}      onClick={() => onTransition(c, 'UNSUBSTANTIATED')}>{t('ethicsOutcomeUnsubstantiated')}</Button>
                <Button variant="ghost"     Icon={Search}     onClick={() => onTransition(c, 'NO_VIOLATION')}>{t('ethicsOutcomeNoViolation')}</Button>
              </>
            )}
            {c.status !== 'CLOSED' && c.status !== 'DISMISSED' && (
              <Button variant="ghost" Icon={Send} onClick={() => onTransition(c, 'ESCALATED')}>{t('ethicsEscalate')}</Button>
            )}
          </div>
          <button onClick={() => reload()} style={{ display: 'none' }}>refresh</button>
        </Card>

        <Card title={t('ethicsActionTimeline')} Icon={Activity}
              action={<Button variant="secondary" Icon={Plus} onClick={addAction}>{t('ethicsAddAction')}</Button>}>
          {actions.length === 0
            ? <div style={{ color: 'var(--ink-3)', fontSize: 13 }}>{t('ethicsNoActions')}</div>
            : actions.map(a => (
                <div key={a.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--border)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 2, fontSize: 12 }}>
                    <Badge tone="info">{a.actionType.replace(/_/g, ' ')}</Badge>
                    <span style={{ fontWeight: 600, color: 'var(--ink-1)' }}>{a.title}</span>
                    {a.actionAt && <span className="mono" style={{ marginInlineStart: 'auto', fontSize: 11, color: 'var(--ink-4)' }}>{new Date(a.actionAt).toLocaleString()}</span>}
                  </div>
                  {a.description && <div style={{ fontSize: 12, color: 'var(--ink-2)' }}>{a.description}</div>}
                  {a.actorName && <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>— {a.actorName}</div>}
                </div>
              ))
          }
        </Card>
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
function Block({ label, body }: { label: string; body?: string | null }) {
  if (!body) return null;
  return <div style={{ marginTop: 8 }}>
    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 }}>{label}</div>
    <div style={{ fontSize: 13, color: 'var(--ink-1)', whiteSpace: 'pre-wrap', background: 'var(--surface-2)', padding: 8, borderRadius: 'var(--r-sm)' }}>{body}</div>
  </div>;
}

// ─── Analytics ─────────────────────────────────────────────────────────────
function AnalyticsPanel({ analytics, t }: { analytics: EthicsAnalyticsDto | null; t: (k: string) => string }) {
  if (!analytics) return <Card><div style={{ padding: 20, color: 'var(--ink-3)' }}>{t('ethicsNoAnalytics')}</div></Card>;
  const max = (m: Record<string, number>) => Math.max(1, ...Object.values(m));
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16 }}>
      <Card title={t('ethicsAnalyticsStatus')} Icon={BarChart3}>
        {Object.entries(analytics.byStatus).filter(([, v]) => v > 0).map(([k, v]) => (
          <Progress key={k} label={`${k.replace(/_/g, ' ')} · ${v}`} value={Math.round((v / max(analytics.byStatus)) * 100)}
                    tone={STATUS_TONE[k] === 'danger' ? 'danger' : STATUS_TONE[k] === 'success' ? 'success' : STATUS_TONE[k] === 'warning' ? 'accent' : 'primary'} />
        ))}
      </Card>
      <Card title={t('ethicsAnalyticsCategory')} Icon={ListChecks}>
        {Object.entries(analytics.byCategory).filter(([, v]) => v > 0).map(([k, v]) => (
          <Progress key={k} label={`${k.replace(/_/g, ' ')} · ${v}`} value={Math.round((v / max(analytics.byCategory)) * 100)} tone="primary" />
        ))}
      </Card>
    </div>
  );
}

// ─── Integrations panel ────────────────────────────────────────────────────
function IntegrationsPanel({ items, detailId, setDetailId, t, toast }: {
  items: EthicsCaseDto[];
  detailId: number | null; setDetailId: (id: number | null) => void;
  t: (k: string) => string;
  toast: ReturnType<typeof useToast>;
}) {
  const c = items.find(x => x.id === detailId) ?? null;
  const [links, setLinks] = useState<CaseLinkDto[]>([]);

  async function reload() {
    if (!c) { setLinks([]); return; }
    try { setLinks(await ethicsCaseApi.links(c.id)); } catch { /* silent */ }
  }
  useEffect(() => { void reload(); /* eslint-disable-next-line */ }, [detailId]);

  async function addLink() {
    if (!c) return;
    const entityType = window.prompt(t('changeLinkEntityType') + ' (RISK, AUDIT_FINDING, CONTROL, POLICY, CHANGE_REQUEST, INCIDENT, VENDOR, EVIDENCE, DPIA)', 'RISK');
    if (!entityType) return;
    const idStr = window.prompt(t('changeLinkEntityId'), '');
    if (!idStr) return;
    const entityId = Number(idStr);
    if (Number.isNaN(entityId)) { toast.danger('Invalid id'); return; }
    const linkType = window.prompt(t('changeLinkType') + ' (RELATES_TO, RAISED_FROM, RESULTED_IN, REMEDIATED_BY)', 'RELATES_TO') ?? 'RELATES_TO';
    try { await ethicsCaseApi.addLink(c.id, { entityType, entityId, linkType }); void reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function delLink(l: CaseLinkDto) {
    if (!l.id) return;
    if (!window.confirm(t('changeLinkDeleteConfirm'))) return;
    try { await ethicsCaseApi.deleteLink(l.id); void reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  if (!c) {
    return (
      <Card title={t('ethicsTabIntegrations')}>
        <EmptyState Icon={MessageSquare} title={t('ethicsIntegrationsSelectTitle')} sub={t('ethicsIntegrationsSelectSub')} />
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 12 }}>
          {items.slice(0, 12).map(it => (
            <button key={it.id} onClick={() => setDetailId(it.id)} style={{ padding: '6px 10px', background: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 'var(--r-pill)', cursor: 'pointer', fontSize: 12 }}>
              {it.caseCode ?? `#${it.id}`}
            </button>
          ))}
        </div>
      </Card>
    );
  }

  return (
    <Card title={`${t('ethicsIntegrationsFor')} · ${c.caseCode ?? '#' + c.id}`}
          Icon={MessageSquare}
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
                <button onClick={() => delLink(l)} title={t('deleteBtn')} style={{ marginInlineStart: 'auto', background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--danger-fg)' }}>
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

// ─── Disclosures (CoI / Gifts / Hospitality / Outside Business) ────────────
const DISCLOSURE_TYPE_OPTIONS = [
  'CONFLICT_OF_INTEREST', 'GIFT_RECEIVED', 'GIFT_GIVEN', 'HOSPITALITY',
  'OUTSIDE_BUSINESS', 'RELATED_PARTY', 'POLITICAL_DONATION', 'CHARITABLE',
].map(v => ({ value: v, label: v.replace(/_/g, ' ') }));
const DISCLOSURE_STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT: 'neutral', SUBMITTED: 'info', REVIEW: 'warning',
  APPROVED: 'success', REJECTED: 'danger', RECUSED: 'accent', MITIGATED: 'primary',
};

const DISCLOSURE_EMPTY: Partial<DisclosureDto> = {
  disclosureType: 'CONFLICT_OF_INTEREST', status: 'DRAFT',
  titleEn: '', description: '', discloserName: '', currency: 'SAR',
};

function DisclosuresTab({ t, lang, toast, demoMode, ethicsFwOptions }: {
  t: (k: string) => string;
  lang: string;
  toast: ReturnType<typeof useToast>;
  demoMode: boolean;
  ethicsFwOptions: { value: string; label: string }[];
}) {
  const [items, setItems] = useState<DisclosureDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DisclosureDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<DisclosureDto | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    if (demoMode) { setItems([]); return; }
    setLoading(true);
    try { setItems((await disclosureApi.list({ size: 200 })).content ?? []); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, []);

  const FIELDS: FieldDef[] = [
    { kind: 'text',     key: 'disclosureCode', label: t('discFldCode'), span: 4, placeholder: 'DISC-2025-0042' },
    { kind: 'select',   key: 'disclosureType', label: t('discFldType') + ' *', required: true, options: DISCLOSURE_TYPE_OPTIONS, span: 4 },
    { kind: 'select',   key: 'frameworkId',    label: t('ethicsFldFramework'), options: ethicsFwOptions, span: 4 },
    { kind: 'text',     key: 'titleEn',        label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 500, span: 6 },
    { kind: 'text',     key: 'titleAr',        label: t('fldTitleArLbl'), maxLength: 500, span: 6 },
    { kind: 'text',     key: 'discloserName',  label: t('discFldDiscloser') + ' *', required: true, span: 6 },
    { kind: 'text',     key: 'discloserEmail', label: t('discFldDiscloserEmail'), span: 6 },
    { kind: 'text',     key: 'discloserDepartment', label: t('discFldDept'), span: 6 },
    { kind: 'text',     key: 'discloserRole',  label: t('discFldRole'), span: 6 },
    { kind: 'text',     key: 'counterpartyName', label: t('discFldCounterparty'), span: 6 },
    { kind: 'text',     key: 'counterpartyRelationship', label: t('discFldCpRel'), span: 6 },
    { kind: 'number',   key: 'monetaryValue',  label: t('discFldValue'), span: 4 },
    { kind: 'text',     key: 'currency',       label: t('discFldCurrency'), span: 2 },
    { kind: 'date',     key: 'occurredOn',     label: t('discFldOccurredOn'), span: 6 },
    { kind: 'text',     key: 'clauseRef',      label: t('frameworkClauseRef'), span: 6 },
    { kind: 'textarea', key: 'description',    label: t('discFldDescription') + ' *', rows: 3, span: 12 },
  ];

  function reqFromDto(d: DisclosureDto): Partial<DisclosureDto> { return { ...d }; }

  async function submitForm(values: Partial<DisclosureDto>) {
    try {
      const v: Partial<DisclosureDto> = { ...values };
      if (typeof v.frameworkId === 'string') v.frameworkId = Number(v.frameworkId);
      if (typeof v.monetaryValue === 'string') v.monetaryValue = Number(v.monetaryValue);
      if (editing) await disclosureApi.update(editing.id, v);
      else await disclosureApi.create(v);
      toast.success(editing ? t('discUpdated') : t('discCreated'));
      setModalOpen(false); setEditing(null); await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await disclosureApi.remove(confirmDel.id); toast.success('Disclosure deleted'); await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }
  async function transition(d: DisclosureDto, target: string) {
    const decidedBy = ['APPROVED','REJECTED','RECUSED','MITIGATED'].includes(target)
      ? (window.prompt(t('discDecidedByPrompt'), '') ?? '') : '';
    const notes = ['APPROVED','REJECTED','RECUSED','MITIGATED'].includes(target)
      ? (window.prompt(t('discDecisionNotesPrompt'), '') ?? '') : '';
    try { await disclosureApi.transition(d.id, { target, decidedBy, notes }); toast.success(`→ ${target}`); await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Transition failed'); }
  }
  function nextDiscStatus(d: DisclosureDto): string | null {
    switch (d.status) {
      case 'DRAFT':     return 'SUBMITTED';
      case 'SUBMITTED': return 'REVIEW';
      case 'REVIEW':    return 'APPROVED';
      default:          return null;
    }
  }

  const cols: Column<DisclosureDto>[] = [
    { header: t('discFldCode'),    cell: d => <span className="mono" style={{ fontWeight: 600 }}>{d.disclosureCode ?? '—'}</span>, width: '130px' },
    { header: t('colTitle'),       cell: d => <span style={{ fontWeight: 600 }}>{lang === 'ar' && d.titleAr ? d.titleAr : d.titleEn}</span> },
    { header: t('discFldType'),    cell: d => <Badge tone="primary">{d.disclosureType.replace(/_/g, ' ')}</Badge>, width: '180px' },
    { header: t('discFldDiscloser'), cell: d => d.discloserName, width: '180px' },
    { header: t('discFldValue'),   cell: d => d.monetaryValue ? `${d.monetaryValue.toLocaleString()} ${d.currency ?? 'SAR'}` : '—', mono: true, width: '130px' },
    { header: t('colStatus'),      cell: d => <Badge tone={DISCLOSURE_STATUS_TONE[d.status] ?? 'neutral'} dot>{d.status}</Badge>, width: '120px' },
    { header: '', align: 'end', width: '180px',
      cell: d => {
        const next = nextDiscStatus(d);
        const extras: Array<{ Icon: typeof ArrowRight; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (next) extras.push({ Icon: ArrowRight, onClick: () => transition(d, next), title: `${t('changeAdvanceTo')} ${next}`, tone: 'primary' });
        if (d.status === 'REVIEW') extras.push({ Icon: XIcon, onClick: () => transition(d, 'RECUSED'), title: t('discActionRecuse'), tone: 'accent' });
        return <RowActions extras={extras} onEdit={() => { setEditing(d); setModalOpen(true); }} onDelete={() => setConfirmDel(d)} />;
      }
    },
  ];

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>
          {t('discDescription')}
        </div>
        <Button variant="primary" Icon={Plus} onClick={() => { setEditing(null); setModalOpen(true); }}>{t('discNew')}</Button>
      </div>
      <Card padded={false}>
        {loading ? <Loading /> :
          <DataTable columns={cols} rows={items}
                     empty={<EmptyState Icon={Stamp} title={t('discEmptyTitle')} sub={t('discEmptySub')} />} />
        }
      </Card>

      <FormModal<Partial<DisclosureDto>>
        open={modalOpen}
        title={editing ? `${t('editPrefix')} · ${editing.disclosureCode ?? editing.titleEn}` : t('discNew')}
        fields={FIELDS}
        initial={editing ? reqFromDto(editing) : DISCLOSURE_EMPTY}
        onClose={() => { setModalOpen(false); setEditing(null); }}
        onSubmit={submitForm}
      />

      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title={t('discConfirmDelete')}
        message={confirmDel ? `${confirmDel.titleEn} ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={() => setConfirmDel(null)}
        onConfirm={executeDelete}
      />
    </>
  );
}
