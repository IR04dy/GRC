import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
  ethicsCaseApi, disclosureApi, kycApi, amlAlertApi, frameworkApi, ApiError,
  type EthicsCaseDto, type CaseActionDto, type CaseLinkDto, type EthicsAnalyticsDto,
  type DisclosureDto, type KycSubjectDto, type ScreeningHitDto, type KycAnalyticsDto,
  type AmlAlertDto, type AmlAnalyticsDto, type FrameworkDto,
} from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { DemoDataNotice } from '../../components/ui/DemoDataNotice';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import { isAuthenticated } from '../../lib/auth';
import {
  ETHICS_CASES, ETHICS_ANALYTICS,
  DISCLOSURES,
  KYC_SUBJECTS, KYC_HITS_BY_SUBJECT,
  AML_ALERTS, AML_ANALYTICS,
} from './mockEthics';
import { ETHICS_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { ETHICS_ANCHORS as ETHICS_REG_ANCHORS } from '../../lib/moduleAnchors';
import { usePromoted, isUnsyncedId } from '../../lib/promotedRecords';
import { toEthicsCaseDto, usePromotedProvenance } from '../../lib/promotedAdapters';
import { PromotedBadge } from '../../components/intake/PromotedBadge';
import '../../styles/pages/ethics.css';

type TabId = 'register' | 'kanban' | 'workflow' | 'disclosures' | 'kyc' | 'aml' | 'analytics' | 'integrations';

const STATUS_TONE: Record<string, BadgeTone> = {
  INTAKE: 'neutral', TRIAGE: 'info', INVESTIGATION: 'accent',
  SUBSTANTIATED: 'warning', UNSUBSTANTIATED: 'success', NO_VIOLATION: 'success',
  REMEDIATION: 'primary', ESCALATED: 'danger', DISMISSED: 'neutral', CLOSED: 'success',
};
const SEVERITY_TONE: Record<string, BadgeTone> = {
  LOW: 'risk-low', MEDIUM: 'risk-med', HIGH: 'risk-high', CRITICAL: 'risk-crit',
};
const CATEGORY_OPTIONS = [
  'BRIBERY', 'KICKBACK', 'CONFLICT_OF_INTEREST', 'FRAUD', 'THEFT',
  'HARASSMENT', 'DISCRIMINATION', 'RETALIATION', 'DATA_MISUSE',
  'AML_SUSPICION', 'POLICY_VIOLATION', 'SAFETY', 'OTHER',
].map(v => ({ value: v, label: v.replace(/_/g, ' ') }));
const SEVERITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(v => ({ value: v, label: v }));
// Whistleblowing intake channels — expanded to cover every path a Saudi
// employee / stakeholder might use to raise an ethics concern. Aligned
// with NAZAHA-mandated channels + the AML/sanctions reporting routes.
const CHANNEL_OPTIONS = [
  { value: 'HOTLINE',            label: 'Hotline (24/7 voice)' },
  { value: 'WEB_FORM',           label: 'Web form (this portal)' },
  { value: 'EMAIL',              label: 'Email' },
  { value: 'MOBILE_APP',         label: 'Mobile app' },
  { value: 'SMS_WHATSAPP',       label: 'SMS / WhatsApp' },
  { value: 'IN_PERSON',          label: 'In-person disclosure' },
  { value: 'LETTER',             label: 'Postal letter' },
  { value: 'MANAGER',            label: 'Manager / supervisor' },
  { value: 'HR_PARTNER',         label: 'HR partner' },
  { value: 'COMPLIANCE_OFFICER', label: 'Compliance officer' },
  { value: 'AUDIT_COMMITTEE',    label: 'Audit committee' },
  { value: 'OMBUDSMAN',          label: 'Ombudsman' },
  { value: 'REGULATOR',          label: 'Regulator (NAZAHA / SAMA / NCA / SDAIA)' },
  { value: 'EXTERNAL_COUNSEL',   label: 'External legal counsel' },
  { value: 'ANONYMOUS_DROPBOX',  label: 'Anonymous physical dropbox' },
  { value: 'OTHER',              label: 'Other' },
];

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
  const navigate = useNavigate();
  // F2-98: reactive, because `applyDemoCases()` also fires on an empty list and on failure.
  const [demoMode, setDemoMode] = useState(!isAuthenticated());

  const [tab, setTab] = useState<TabId>('register');
  const [apiItems, setItems] = useState<EthicsCaseDto[]>([]);
  // DD-71: include intake-promoted speakup / ethics-disclosure cases.
  const promotedCases = usePromoted('ETHICS_CASE').map(toEthicsCaseDto);
  const items = useMemo(() => [...promotedCases, ...apiItems], [promotedCases, apiItems]);
  const ethicsProv = usePromotedProvenance('ETHICS_CASE');
  const [analytics, setAnalytics] = useState<EthicsAnalyticsDto | null>(null);
  const [frameworks, setFrameworks] = useState<FrameworkDto[]>([]);
  const [loading, setLoading] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<EthicsCaseDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<EthicsCaseDto | null>(null);
  const [busy, setBusy] = useState(false);
  const [detailId, setDetailId] = useState<number | null>(null);

  function applyDemoCases() {
    setItems(ETHICS_CASES); setAnalytics(ETHICS_ANALYTICS); setDemoMode(true);
  }

  async function load() {
    if (!isAuthenticated()) { applyDemoCases(); return; }
    setLoading(true);
    try {
      const [page, an, fws] = await Promise.all([
        ethicsCaseApi.list({ size: 200 }),
        ethicsCaseApi.analytics().catch(() => null),
        frameworkApi.list().catch(() => [] as FrameworkDto[]),
      ]);
      const list = page.content ?? [];
      if (list.length === 0) { applyDemoCases(); setFrameworks(fws); return; }
      setItems(list);
      setAnalytics(an);
      setFrameworks(fws);
      setDemoMode(false);
    } catch { applyDemoCases(); }
    finally { setLoading(false); }
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
    { id: 'kyc',          label: t('ethicsTabKyc') },
    { id: 'aml',          label: t('ethicsTabAml') },
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
    { header: t('ethicsFldCode'),     cell: c => <span className="mono ethics-mono-bold">{c.caseCode ?? '—'}</span>, width: '130px' },
    { header: t('colTitle'),          cell: c => (
      <div>
        <div className="ethics-cell-title">{lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}{ethicsProv.has(c.id) && <PromotedBadge sourceReferenceCode={ethicsProv.get(c.id)!} />}</div>
        {c.isAnonymous && <span className="ethics-cell-anonymous"><EyeOff size={10} /> {t('ethicsAnonymous')}</span>}
      </div>
    ) },
    { header: t('ethicsFldCategory'), cell: c => <Badge tone="primary">{c.category.replace(/_/g, ' ')}</Badge>, width: '160px' },
    { header: t('ethicsFldSeverity'), cell: c => <Badge tone={SEVERITY_TONE[c.severity] ?? 'neutral'} dot>{c.severity}</Badge>, width: '110px' },
    { header: t('colStatus'),         cell: c => <Badge tone={STATUS_TONE[c.status] ?? 'neutral'} dot>{c.status.replace(/_/g, ' ')}</Badge>, width: '160px' },
    { header: t('ethicsFldChannel'),  cell: c => c.intakeChannel.replace(/_/g, ' '), width: '110px' },
    { header: '', align: 'end', width: '160px',
      // F2-171: promoted-intake cases are merged in above and exist only in this browser.
      cell: c => <RowActions disabled={demoMode || isUnsyncedId(c.id)}
        onEdit={() => { setEditing(c); setModalOpen(true); }}
        onDelete={() => setConfirmDel(c)}
        extras={[
          { Icon: ChevronRight, onClick: () => { setDetailId(c.id); setTab('workflow'); }, title: t('ethicsActOpen'), tone: 'primary' },
        ]} /> },
  ];

  return (
    <Shell title={t('modEthics')} sub={t('ethicsShellSub')}>
      <PageHeader
        eyebrow={t('ethicsEyebrow')}
        title={t('ethicsTitle')}
        sub={t('ethicsSub')}
        actions={
          <>
            {demoMode && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={RefreshCw} onClick={load} disabled={loading}>{t('dashRefresh')}</Button>
            <Button variant="secondary" Icon={Filter}
              onClick={() => window.dispatchEvent(new CustomEvent('grc:open-palette'))}>
              {t('actionFilter')}
            </Button>
            {/* DD-76: route new ethics-case creation through Services Center
                (employee-facing path) — anonymous reports go via /portal/speakup. */}
            <Button variant="primary" Icon={Plus} onClick={() => navigate('/intake/ethics_disclosure')}>{t('ethicsNew')}</Button>
          </>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={ShieldAlert}    label={t('ethicsKpiTotal')}        value={total}     tone="primary" to="/ethics" />
        <Kpi Icon={Activity}       label={t('ethicsKpiOpen')}         value={open}      tone="info" onClick={() => setTab('register')} />
        <Kpi Icon={Flame}          label={t('ethicsKpiCritical')}     value={critical}  tone="danger" onClick={() => setTab('register')} />
        <Kpi Icon={Stamp}          label={t('ethicsKpiSubstantiated')} value={`${substantPct}%`} tone="accent" onClick={() => setTab('analytics')} />
      </div>

      <OverviewStrip
        storageKey="overview.ethics"
        processBarTitle={t('ethicsProcessBarTitle')}
        processBarSubtitle={t('ethicsProcessBarSubtitle')}
        lifecycles={ETHICS_LIFECYCLES}
        postureTitle={t('ethicsPostureTitle')}
        anchors={ETHICS_REG_ANCHORS}
        lang={lang} />

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

      {tab === 'kyc' && <KycTab t={t} lang={lang} toast={toast} demoMode={demoMode} ethicsFwOptions={ethicsFwOptions} />}

      {tab === 'aml' && <AmlAlertsTab t={t} lang={lang} toast={toast} demoMode={demoMode} ethicsFwOptions={ethicsFwOptions} />}

      {tab === 'analytics' && <AnalyticsPanel analytics={analytics} t={t} />}

      {tab === 'integrations' && <IntegrationsPanel items={items} detailId={detailId} setDetailId={setDetailId}
                                                    t={t} toast={toast} />}

      <FormModal<Partial<EthicsCaseDto>>
        open={modalOpen}
        title={editing ? `${t('editPrefix')} · ${editing.caseCode ?? (lang === 'ar' && editing.titleAr ? editing.titleAr : editing.titleEn)}` : t('ethicsNew')}
        fields={FIELDS}
        initial={editing ? reqFromDto(editing) : EMPTY}
        onClose={() => { setModalOpen(false); setEditing(null); }}
        onSubmit={submitForm}
      />

      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title={t('ethicsConfirmDelete')}
        message={confirmDel ? `${(lang === 'ar' && confirmDel.titleAr ? confirmDel.titleAr : confirmDel.titleEn)} ${t('confirmDeleteSuffix')}` : ''}
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
    <div className="ethics-kanban">
      {KANBAN_LANES.map(lane => {
        const lst = items.filter(c => lane.statuses.includes(c.status));
        return (
          <div key={lane.id} className="ethics-kanban__lane">
            <div className="ethics-kanban__lane-head">
              <span className="ethics-kanban__lane-title">{lane.id}</span>
              <Badge tone="neutral">{lst.length}</Badge>
            </div>
            <div className="ethics-kanban__cards">
              {lst.map(c => (
                <div key={c.id} onClick={() => onOpen(c.id)} className="ethics-kanban__card">
                  <div className="ethics-kanban__card-head">
                    <span className="mono ethics-kanban__card-code">{c.caseCode ?? `#${c.id}`}</span>
                    <Badge tone={SEVERITY_TONE[c.severity] ?? 'neutral'}>{c.severity}</Badge>
                    {c.isAnonymous && <Lock size={10} className="ethics-kanban__card-lock" />}
                  </div>
                  <div className="ethics-kanban__card-title">
                    {lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}
                  </div>
                  <div className="ethics-kanban__card-meta">
                    <Badge tone="primary">{c.category.replace(/_/g, ' ')}</Badge>
                    <button onClick={(e) => { e.stopPropagation(); void onTransition(c); }}
                            disabled={!nextStatus(c)}
                            className="ethics-kanban__transition-btn">
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
    <div className="rail-layout">
      <Card title={t('ethicsCaseList')} Icon={ListChecks} padded={false}>
        <div className="ethics-list-pane">
          {items.map(it => (
            <button key={it.id} onClick={() => setDetailId(it.id)}
              className={`ethics-list-item${it.id === detailId ? ' is-selected' : ''}`}>
              <div className="ethics-list-item__head">
                <span className="mono">{it.caseCode ?? `#${it.id}`}</span>
                <Badge tone={STATUS_TONE[it.status] ?? 'neutral'} dot>{it.status.replace(/_/g, ' ')}</Badge>
              </div>
              <div className="ethics-list-item__title">
                {lang === 'ar' && it.titleAr ? it.titleAr : it.titleEn}
              </div>
            </button>
          ))}
        </div>
      </Card>

      <div className="ethics-detail-stack">
        <Card
          title={`${c.caseCode ?? '#' + c.id} · ${lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}`}
          Icon={ShieldAlert}
          action={<>
            <Badge tone={STATUS_TONE[c.status] ?? 'neutral'} dot>{c.status.replace(/_/g, ' ')}</Badge>
            <Badge tone={SEVERITY_TONE[c.severity] ?? 'neutral'}>{c.severity}</Badge>
            <Badge tone="primary">{c.category.replace(/_/g, ' ')}</Badge>
            {c.regulatorNotified && <Badge tone="danger" dot>{t('ethicsRegulatorNotified')}</Badge>}
          </>}>
          <div className="ethics-detail-grid">
            <Field label={t('ethicsFldChannel')} value={c.intakeChannel.replace(/_/g, ' ')} />
            <Field label={t('ethicsFldReporter')}
                   value={c.isAnonymous ? t('ethicsAnonymous') : (c.reporterName ?? '—')} />
            <Field label={t('ethicsFldInvestigator')} value={c.investigatorName ?? '—'} />
            <Field label={t('ethicsFldCommittee')}    value={c.committeeName ?? '—'} />
            <Field label={t('ethicsFldLocation')}     value={c.location ?? '—'} />
            <Field label={t('ethicsFldIncidentDate')} value={c.dateOfIncident ?? '—'} />
          </div>
          <div className="ethics-blocks">
            <Block label={t('ethicsFldSubject')} body={c.subjectSummary} />
            <Block label={t('ethicsFldDescription')} body={c.description} />
            {c.outcomeSummary && <Block label={t('ethicsFldOutcomeSummary')} body={c.outcomeSummary} />}
          </div>
          <div className="ethics-actions-row">
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
            ? <div className="ethics-empty-note">{t('ethicsNoActions')}</div>
            : actions.map(a => (
                <div key={a.id} className="ethics-action-timeline-item">
                  <div className="ethics-action-timeline-head">
                    <Badge tone="info">{a.actionType.replace(/_/g, ' ')}</Badge>
                    <span className="ethics-action-timeline-title">{a.title}</span>
                    {a.actionAt && <span className="mono ethics-action-timeline-time">{new Date(a.actionAt).toLocaleString()}</span>}
                  </div>
                  {a.description && <div className="ethics-action-timeline-desc">{a.description}</div>}
                  {a.actorName && <div className="ethics-action-timeline-actor">— {a.actorName}</div>}
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
    <div className="ethics-field-label">{label}</div>
    <div className="ethics-field-value">{value ?? '—'}</div>
  </div>;
}
function Block({ label, body }: { label: string; body?: string | null }) {
  if (!body) return null;
  return <div className="ethics-block">
    <div className="ethics-block-label">{label}</div>
    <div className="ethics-block-body">{body}</div>
  </div>;
}

// ─── Analytics ─────────────────────────────────────────────────────────────
function AnalyticsPanel({ analytics, t }: { analytics: EthicsAnalyticsDto | null; t: (k: string) => string }) {
  if (!analytics) return <Card><div className="ethics-analytics-empty">{t('ethicsNoAnalytics')}</div></Card>;
  const max = (m: Record<string, number>) => Math.max(1, ...Object.values(m));
  return (
    <div className="ethics-analytics-grid">
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
        <div className="ethics-integration-tags">
          {items.slice(0, 12).map(it => (
            <button key={it.id} onClick={() => setDetailId(it.id)} className="ethics-integration-pill">
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
        ? <div className="ethics-empty-note">{t('changeNoLinks')}</div>
        : (
          <div className="ethics-integrations-list">
            {links.map(l => (
              <div key={l.id} className="ethics-integration-row">
                <Badge tone="primary">{l.entityType.replace(/_/g, ' ')}</Badge>
                <span className="mono ethics-integration-row__id">#{l.entityId}</span>
                <Badge tone="info">{l.linkType.replace(/_/g, ' ')}</Badge>
                <button onClick={() => delLink(l)} title={t('deleteBtn')} className="ethics-integration-row__del">
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
  return <div className="ethics-loading">{t('riskLoading')}</div>;
}

// ─── AML transaction alerts (case management) ──────────────────────────────
const AML_ALERT_TYPE_OPTIONS = [
  'STRUCTURING', 'UNUSUAL_VOLUME', 'UNUSUAL_VELOCITY', 'HIGH_RISK_GEO',
  'SANCTIONS_HIT', 'PEP_TRANSACTION', 'RAPID_MOVEMENT', 'CASH_INTENSIVE',
  'UNKNOWN_COUNTERPARTY', 'POLITICAL_EXPOSURE', 'OTHER',
].map(v => ({ value: v, label: v.replace(/_/g, ' ') }));
const AML_STATUS_TONE: Record<string, BadgeTone> = {
  OPEN: 'warning', INVESTIGATING: 'info', CLEARED: 'success',
  ESCALATED: 'accent', STR_FILED: 'danger', CLOSED: 'neutral',
};
const AML_SEVERITY_TONE: Record<string, BadgeTone> = {
  LOW: 'risk-low', MEDIUM: 'risk-med', HIGH: 'risk-high', CRITICAL: 'risk-crit',
};
const AML_EMPTY: Partial<AmlAlertDto> = {
  alertType: 'STRUCTURING', severity: 'MEDIUM', status: 'OPEN',
  titleEn: '', description: '', currency: 'SAR',
};

function AmlAlertsTab({ t, lang, toast, demoMode, ethicsFwOptions }: {
  t: (k: string) => string;
  lang: string;
  toast: ReturnType<typeof useToast>;
  demoMode: boolean;
  ethicsFwOptions: { value: string; label: string }[];
}) {
  const [items, setItems] = useState<AmlAlertDto[]>([]);
  const [analytics, setAnalytics] = useState<AmlAnalyticsDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AmlAlertDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<AmlAlertDto | null>(null);
  const [busy, setBusy] = useState(false);

  // F2-98: this tab substitutes independently of the parent, so it discloses independently —
  // the cases list can be the tenant's own while these alerts are invented, and the parent's
  // single header badge cannot represent both.
  const [showingDemo, setShowingDemo] = useState(demoMode);
  function applyDemoAml() {
    setItems(AML_ALERTS); setAnalytics(AML_ANALYTICS); setShowingDemo(true);
  }

  async function load() {
    if (!isAuthenticated()) { applyDemoAml(); return; }
    setLoading(true);
    try {
      const [page, an] = await Promise.all([
        amlAlertApi.list({ size: 200 }),
        amlAlertApi.analytics().catch(() => null),
      ]);
      const list = page.content ?? [];
      if (list.length === 0) { applyDemoAml(); return; }
      setItems(list);
      setAnalytics(an);
      setShowingDemo(false);
    } catch { applyDemoAml(); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, []);

  const FIELDS: FieldDef[] = [
    { kind: 'text',     key: 'alertCode',      label: t('amlFldCode'), span: 4, placeholder: 'AML-2025-0042' },
    { kind: 'select',   key: 'alertType',      label: t('amlFldType') + ' *', required: true, options: AML_ALERT_TYPE_OPTIONS, span: 4 },
    { kind: 'select',   key: 'severity',       label: t('amlFldSeverity'), options: SEVERITY_OPTIONS, span: 4 },
    { kind: 'text',     key: 'titleEn',        label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 500, span: 6 },
    { kind: 'text',     key: 'titleAr',        label: t('fldTitleArLbl'), maxLength: 500, span: 6 },
    { kind: 'text',     key: 'ruleId',         label: t('amlFldRuleId'), span: 4, placeholder: 'TM-007' },
    { kind: 'text',     key: 'ruleName',       label: t('amlFldRuleName'), span: 8 },
    { kind: 'text',     key: 'subjectName',    label: t('amlFldSubjectName'), span: 6 },
    { kind: 'text',     key: 'counterpartyName', label: t('amlFldCounterparty'), span: 6 },
    { kind: 'text',     key: 'counterpartyCountry', label: t('amlFldCpCountry'), span: 3, placeholder: 'SAU' },
    { kind: 'number',   key: 'transactionCount', label: t('amlFldTxnCount'), span: 3 },
    { kind: 'number',   key: 'aggregateAmount',  label: t('amlFldAmount'), span: 3 },
    { kind: 'text',     key: 'currency',         label: t('discFldCurrency'), span: 3 },
    { kind: 'date',     key: 'periodFrom',       label: t('amlFldPeriodFrom'), span: 6 },
    { kind: 'date',     key: 'periodTo',         label: t('amlFldPeriodTo'), span: 6 },
    { kind: 'select',   key: 'frameworkId',      label: t('ethicsFldFramework'), options: ethicsFwOptions, span: 6 },
    { kind: 'text',     key: 'clauseRef',        label: t('frameworkClauseRef'), span: 6 },
    { kind: 'textarea', key: 'description',      label: t('ethicsFldDescription') + ' *', rows: 3, span: 12 },
  ];

  function reqFromDto(a: AmlAlertDto): Partial<AmlAlertDto> { return { ...a }; }

  async function submitForm(values: Partial<AmlAlertDto>) {
    try {
      const v: Partial<AmlAlertDto> = { ...values };
      if (typeof v.frameworkId === 'string') v.frameworkId = Number(v.frameworkId);
      if (typeof v.aggregateAmount === 'string') v.aggregateAmount = Number(v.aggregateAmount);
      if (typeof v.transactionCount === 'string') v.transactionCount = Number(v.transactionCount);
      if (editing) await amlAlertApi.update(editing.id, v);
      else await amlAlertApi.create(v);
      toast.success(editing ? t('amlUpdated') : t('amlCreated'));
      setModalOpen(false); setEditing(null); await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await amlAlertApi.remove(confirmDel.id); toast.success('Alert deleted'); await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }
  async function transition(a: AmlAlertDto, target: string) {
    let body: Record<string, unknown> = { target };
    if (target === 'CLEARED') {
      const notes = window.prompt(t('amlCleardNotesPrompt'), '') ?? '';
      const decidedBy = window.prompt(t('amlDecidedByPrompt'), '') ?? '';
      body = { ...body, disposition: 'FALSE_POSITIVE', notes, decidedBy };
    }
    if (target === 'STR_FILED') {
      const ref = window.prompt(t('amlStrRefPrompt'), '');
      if (!ref) return;
      const notes = window.prompt(t('amlStrNotesPrompt'), '') ?? '';
      const decidedBy = window.prompt(t('amlDecidedByPrompt'), '') ?? '';
      body = { ...body, disposition: 'TRUE_POSITIVE', strReference: ref, notes, decidedBy };
    }
    if (target === 'ESCALATED') {
      const notes = window.prompt(t('amlEscalateNotesPrompt'), '') ?? '';
      body = { ...body, notes, disposition: 'TRUE_POSITIVE' };
    }
    try { await amlAlertApi.transition(a.id, body); toast.success(`→ ${target}`); await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Transition failed'); }
  }
  async function assign(a: AmlAlertDto) {
    const name = window.prompt(t('amlAssignPrompt'), a.analystName ?? '');
    if (!name) return;
    try { await amlAlertApi.assign(a.id, name); toast.success(t('amlAssigned')); await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const cols: Column<AmlAlertDto>[] = [
    { header: t('amlFldCode'),     cell: a => <span className="mono ethics-mono-bold">{a.alertCode ?? `#${a.id}`}</span>, width: '130px' },
    { header: t('colTitle'),       cell: a => (
      <div>
        <div className="ethics-cell-title">{lang === 'ar' && a.titleAr ? a.titleAr : a.titleEn}</div>
        <div className="ethics-cell-sub">
          {a.alertType.replace(/_/g, ' ')}{a.ruleId && ` · ${a.ruleId}`}
        </div>
      </div>
    ) },
    { header: t('amlFldSubjectName'), cell: a => a.subjectName ?? '—', width: '180px' },
    { header: t('amlFldAmount'),   numeric: true, cell: a => a.aggregateAmount
        ? <span className="mono">{a.aggregateAmount.toLocaleString()} {a.currency ?? 'SAR'}</span>
        : '—', width: '160px' },
    { header: t('amlFldSeverity'), cell: a => <Badge tone={AML_SEVERITY_TONE[a.severity] ?? 'neutral'} dot>{a.severity}</Badge>, width: '110px' },
    { header: t('colStatus'),      cell: a => <Badge tone={AML_STATUS_TONE[a.status] ?? 'neutral'} dot>{a.status.replace(/_/g, ' ')}</Badge>, width: '140px' },
    { header: t('amlFldStr'),      cell: a => a.strFiled ? <Badge tone="danger">{a.strReference ?? 'STR'}</Badge> : '—', width: '120px' },
    { header: '', align: 'end', width: '300px',
      cell: a => {
        const extras: Array<{ Icon: typeof CheckCheck; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (a.status === 'OPEN') extras.push({ Icon: Send, onClick: () => assign(a), title: t('amlAssign'), tone: 'primary' });
        if (a.status === 'OPEN') extras.push({ Icon: Activity, onClick: () => transition(a, 'INVESTIGATING'), title: t('amlInvestigate'), tone: 'primary' });
        if (a.status === 'INVESTIGATING') {
          extras.push({ Icon: CheckCheck, onClick: () => transition(a, 'CLEARED'),     title: t('amlClear'),     tone: 'success' });
          extras.push({ Icon: Send,       onClick: () => transition(a, 'ESCALATED'),  title: t('amlEscalate'),  tone: 'accent' });
          extras.push({ Icon: Stamp,      onClick: () => transition(a, 'STR_FILED'), title: t('amlFileStr'),  tone: 'accent' });
        }
        if (['CLEARED','ESCALATED','STR_FILED'].includes(a.status))
          extras.push({ Icon: Lock, onClick: () => transition(a, 'CLOSED'), title: t('amlClose') });
        return <RowActions disabled={showingDemo} extras={extras} onEdit={() => { setEditing(a); setModalOpen(true); }} onDelete={() => setConfirmDel(a)} />;
      }
    },
  ];

  return (
    <>
      {showingDemo && <DemoDataNotice signedIn={isAuthenticated()} detailKey="demoNoticeAml" />}
      {analytics && (
        <div className="ethics-kpi-5">
          <Kpi Icon={Activity}    label={t('amlKpiTotal')}     value={analytics.total}      tone="primary" to="/aml/sanctions/dashboard" />
          <Kpi Icon={AlertTriangle} label={t('amlKpiOpen')}    value={analytics.openAlerts} tone="warning" to="/aml/sanctions/checks" />
          <Kpi Icon={Flame}       label={t('amlKpiCritical')}  value={analytics.bySeverity?.CRITICAL ?? 0} tone="danger" to="/aml/sanctions/dashboard" />
          <Kpi Icon={Stamp}       label={t('amlKpiStrFiled')}  value={analytics.strFiled}   tone="danger" to="/aml/sanctions/dashboard" />
          <Kpi Icon={CheckCheck}  label={t('amlKpiCleared')}   value={analytics.byStatus?.CLEARED ?? 0} tone="success" to="/aml/sanctions/dashboard" />
        </div>
      )}
      <div className="ethics-toolbar">
        <div className="ethics-toolbar__hint">{t('amlDescription')}</div>
        <Button variant="primary" Icon={Plus} onClick={() => { setEditing(null); setModalOpen(true); }}>{t('amlNew')}</Button>
      </div>
      <Card padded={false}>
        {loading ? <Loading /> :
          items.length === 0
            ? <EmptyState Icon={ShieldAlert} title={t('amlEmptyTitle')} sub={t('amlEmptySub')} />
            : <DataTable columns={cols} rows={items} empty={<EmptyState />} />
        }
      </Card>

      <FormModal<Partial<AmlAlertDto>>
        open={modalOpen}
        title={editing ? `${t('editPrefix')} · ${editing.alertCode ?? (lang === 'ar' && editing.titleAr ? editing.titleAr : editing.titleEn)}` : t('amlNew')}
        fields={FIELDS}
        initial={editing ? reqFromDto(editing) : AML_EMPTY}
        onClose={() => { setModalOpen(false); setEditing(null); }}
        onSubmit={submitForm}
      />

      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title={t('amlConfirmDelete')}
        message={confirmDel ? `${(lang === 'ar' && confirmDel.titleAr ? confirmDel.titleAr : confirmDel.titleEn)} ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={() => setConfirmDel(null)}
        onConfirm={executeDelete}
      />
    </>
  );
}

// ─── KYC (Sanctions / PEP / CDD / EDD) ─────────────────────────────────────
const RISK_RATING_TONE: Record<string, BadgeTone> = {
  LOW: 'success', STANDARD: 'info', HIGH: 'warning', PROHIBITED: 'danger',
};
const KYC_STATUS_TONE: Record<string, BadgeTone> = {
  PENDING: 'neutral', UNDER_REVIEW: 'warning', APPROVED: 'success',
  REJECTED: 'danger', OFFBOARDED: 'neutral',
};
const SUBJECT_TYPE_OPTIONS = [
  { value: 'INDIVIDUAL',   label: 'Individual' },
  { value: 'LEGAL_ENTITY', label: 'Legal entity' },
];
const RISK_RATING_OPTIONS = [
  { value: 'LOW', label: 'Low' }, { value: 'STANDARD', label: 'Standard' },
  { value: 'HIGH', label: 'High' }, { value: 'PROHIBITED', label: 'Prohibited' },
];
const CDD_LEVEL_OPTIONS = [
  { value: 'SIMPLIFIED', label: 'Simplified' }, { value: 'STANDARD', label: 'Standard' }, { value: 'ENHANCED', label: 'Enhanced' },
];
const KYC_EMPTY: Partial<KycSubjectDto> = {
  subjectType: 'INDIVIDUAL', riskRating: 'STANDARD', cddLevel: 'STANDARD',
  status: 'PENDING', isPep: false, isSanctioned: false, eddRequired: false,
  fullName: '',
};
// Manual screening-hit capture options (recordHit).
const HIT_LIST_SOURCES: { value: string; en: string; ar: string }[] = [
  { value: 'OFAC_SDN',        en: 'OFAC SDN',          ar: 'قائمة OFAC' },
  { value: 'UN_CONSOLIDATED', en: 'UN consolidated',   ar: 'قائمة الأمم المتحدة' },
  { value: 'EU_CONSOLIDATED', en: 'EU consolidated',   ar: 'قائمة الاتحاد الأوروبي' },
  { value: 'SAMA_LOCAL',      en: 'SAMA local list',   ar: 'القائمة المحلية (ساما)' },
  { value: 'PEP_GLOBAL',      en: 'PEP (global)',      ar: 'الأشخاص المعرّضون سياسياً' },
  { value: 'INTERPOL_RED',    en: 'Interpol red',      ar: 'النشرة الحمراء (الإنتربول)' },
  { value: 'ADVERSE_MEDIA',   en: 'Adverse media',     ar: 'تغطية إعلامية سلبية' },
  { value: 'INTERNAL',        en: 'Internal watchlist', ar: 'قائمة مراقبة داخلية' },
];
const HIT_DISPOSITIONS: { value: string; en: string; ar: string }[] = [
  { value: 'NEEDS_REVIEW',    en: 'Needs review',      ar: 'بحاجة لمراجعة' },
  { value: 'POTENTIAL_MATCH', en: 'Potential match',   ar: 'مطابقة محتملة' },
  { value: 'TRUE_MATCH',      en: 'True match',        ar: 'مطابقة مؤكّدة' },
  { value: 'FALSE_POSITIVE',  en: 'False positive',    ar: 'إيجابي كاذب' },
];
const hitCtrl: React.CSSProperties = {
  padding: '6px 8px', border: '1px solid var(--border)', borderRadius: 4,
  background: 'var(--surface)', color: 'var(--ink-1)', fontSize: 13, width: '100%', boxSizing: 'border-box',
};

function KycTab({ t, lang, toast, demoMode, ethicsFwOptions }: {
  t: (k: string) => string;
  lang: string;
  toast: ReturnType<typeof useToast>;
  demoMode: boolean;
  ethicsFwOptions: { value: string; label: string }[];
}) {
  const [items, setItems] = useState<KycSubjectDto[]>([]);
  const [analytics, setAnalytics] = useState<KycAnalyticsDto | null>(null);
  const [hits, setHits] = useState<Record<number, ScreeningHitDto[]>>({});
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<KycSubjectDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<KycSubjectDto | null>(null);
  const [busy, setBusy] = useState(false);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  // F2-98: as AmlAlertsTab — substitutes on its own, so it discloses on its own.
  const [showingDemo, setShowingDemo] = useState(demoMode);
  function applyDemoKyc() {
    setShowingDemo(true);
    setItems(KYC_SUBJECTS);
    setAnalytics({
      total: KYC_SUBJECTS.length,
      approved: KYC_SUBJECTS.filter(s => s.status === 'APPROVED').length,
      rejected: KYC_SUBJECTS.filter(s => s.status === 'REJECTED').length,
      pending: KYC_SUBJECTS.filter(s => s.status === 'PENDING').length,
      underReview: KYC_SUBJECTS.filter(s => s.status === 'UNDER_REVIEW').length,
      highRisk: KYC_SUBJECTS.filter(s => s.riskRating === 'HIGH').length,
      prohibited: KYC_SUBJECTS.filter(s => s.riskRating === 'PROHIBITED').length,
      peps: KYC_SUBJECTS.filter(s => s.isPep).length,
      sanctioned: KYC_SUBJECTS.filter(s => s.isSanctioned).length,
    });
    setHits(KYC_HITS_BY_SUBJECT);
  }

  async function load() {
    if (!isAuthenticated()) { applyDemoKyc(); return; }
    setLoading(true);
    try {
      const [page, an] = await Promise.all([
        kycApi.list({ size: 200 }),
        kycApi.analytics().catch(() => null),
      ]);
      const list = page.content ?? [];
      if (list.length === 0) { applyDemoKyc(); return; }
      setItems(list);
      setAnalytics(an);
      setShowingDemo(false);
    } catch { applyDemoKyc(); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, []);

  async function loadHits(id: number) {
    if (hits[id]) return;
    try { setHits(h => ({ ...h, [id]: [] })); /* placeholder */
      const list = await kycApi.hits(id);
      setHits(h => ({ ...h, [id]: list }));
    } catch { setHits(h => ({ ...h, [id]: [] })); }
  }
  async function refetchHits(id: number) {
    try {
      const list = await kycApi.hits(id);
      setHits(h => ({ ...h, [id]: list }));
    } catch { /* keep existing */ }
  }

  // ── Manual screening-hit capture (POST /ethics/kyc/{id}/hits) ──
  // Auto-screening (runScreening) generates hits; this lets a compliance
  // officer log a hit found through a manual / offline check.
  const [hitFormFor, setHitFormFor] = useState<number | null>(null);
  const [savingHit, setSavingHit] = useState(false);
  const [hitDraft, setHitDraft] = useState<Partial<ScreeningHitDto>>({
    listSource: 'OFAC_SDN', matchScore: 90, disposition: 'NEEDS_REVIEW',
  });
  function openHitForm(id: number) {
    setHitDraft({ listSource: 'OFAC_SDN', matchScore: 90, disposition: 'NEEDS_REVIEW', matchSummary: '', listEntryRef: '' });
    setHitFormFor(id);
  }
  async function submitHit(subjectId: number) {
    if (!hitDraft.matchSummary?.trim()) {
      toast.danger(lang === 'ar' ? 'أدخل ملخّص المطابقة' : 'Enter a match summary');
      return;
    }
    setSavingHit(true);
    try {
      await kycApi.recordHit(subjectId, {
        listSource:   hitDraft.listSource,
        listEntryRef: hitDraft.listEntryRef?.trim() || undefined,
        matchScore:   Number(hitDraft.matchScore) || 0,
        matchSummary: hitDraft.matchSummary.trim(),
        disposition:  hitDraft.disposition,
      } as Partial<ScreeningHitDto>);
      toast.success(lang === 'ar' ? 'تم تسجيل المطابقة' : 'Hit recorded');
      setHitFormFor(null);
      await refetchHits(subjectId);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (lang === 'ar' ? 'تعذّر تسجيل المطابقة' : 'Failed to record hit'));
    } finally { setSavingHit(false); }
  }

  const FIELDS: FieldDef[] = [
    { kind: 'text',     key: 'subjectCode',      label: t('kycFldCode'), span: 4, placeholder: 'KYC-IND-0001' },
    { kind: 'select',   key: 'subjectType',      label: t('kycFldType') + ' *', required: true, options: SUBJECT_TYPE_OPTIONS, span: 4 },
    { kind: 'select',   key: 'riskRating',       label: t('kycFldRisk'), options: RISK_RATING_OPTIONS, span: 4 },
    { kind: 'text',     key: 'fullName',         label: t('kycFldFullName') + ' *', required: true, span: 6 },
    { kind: 'text',     key: 'fullNameAr',       label: t('kycFldFullNameAr'), span: 6 },
    { kind: 'text',     key: 'nationality',      label: t('kycFldNationality'), span: 3, placeholder: 'SAU' },
    { kind: 'text',     key: 'countryOfResidence', label: t('kycFldResidence'), span: 3, placeholder: 'SAU' },
    { kind: 'text',     key: 'nationalId',       label: t('kycFldNationalId'), span: 3 },
    { kind: 'text',     key: 'commercialRegistration', label: t('kycFldCR'), span: 3 },
    { kind: 'select',   key: 'cddLevel',         label: t('kycFldCddLevel'), options: CDD_LEVEL_OPTIONS, span: 4 },
    { kind: 'select',   key: 'frameworkId',      label: t('ethicsFldFramework'), options: ethicsFwOptions, span: 4 },
    { kind: 'date',     key: 'nextReviewDate',   label: t('fldNextReviewDate'), span: 4 },
    { kind: 'textarea', key: 'notes',            label: t('kycFldNotes'), rows: 2, span: 12 },
  ];

  function reqFromDto(s: KycSubjectDto): Partial<KycSubjectDto> { return { ...s }; }

  async function submitForm(values: Partial<KycSubjectDto>) {
    try {
      const v: Partial<KycSubjectDto> = { ...values };
      if (typeof v.frameworkId === 'string') v.frameworkId = Number(v.frameworkId);
      if (editing) await kycApi.update(editing.id, v);
      else await kycApi.create(v);
      toast.success(editing ? t('kycUpdated') : t('kycCreated'));
      setModalOpen(false); setEditing(null); await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await kycApi.remove(confirmDel.id); toast.success('Subject deleted'); await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }

  async function action(fn: () => Promise<unknown>, msg: string) {
    try { await fn(); toast.success(msg); await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const cols: Column<KycSubjectDto>[] = [
    { header: t('kycFldCode'),    cell: s => <span className="mono ethics-mono-bold">{s.subjectCode ?? '—'}</span>, width: '130px' },
    { header: t('kycFldFullName'), cell: s => (
      <div>
        <div className="ethics-cell-title">{lang === 'ar' && s.fullNameAr ? s.fullNameAr : s.fullName}</div>
        <div className="ethics-cell-sub">{s.subjectType.replace(/_/g, ' ')}{s.nationality && ` · ${s.nationality}`}</div>
      </div>
    ) },
    { header: t('kycFldRisk'),    cell: s => <Badge tone={RISK_RATING_TONE[s.riskRating] ?? 'neutral'} dot>{s.riskRating}</Badge>, width: '120px' },
    { header: t('kycFldFlags'),   cell: s => (
      <div className="ethics-cell-flags">
        {s.isPep && <Badge tone="warning">PEP</Badge>}
        {s.isSanctioned && <Badge tone="danger">SANCTIONED</Badge>}
        {s.eddRequired && <Badge tone="accent">EDD</Badge>}
      </div>
    ), width: '180px' },
    { header: t('kycFldCdd'),     cell: s => s.cddCompletedAt ? <Badge tone="success" dot>{t('kycCddDone')}</Badge> : <Badge tone="neutral">—</Badge>, width: '110px' },
    { header: t('colStatus'),     cell: s => <Badge tone={KYC_STATUS_TONE[s.status] ?? 'neutral'} dot>{s.status.replace(/_/g, ' ')}</Badge>, width: '130px' },
    { header: t('kycFldLastScreen'), cell: s => s.lastScreenedAt ? new Date(s.lastScreenedAt).toLocaleDateString() : '—', mono: true, width: '120px' },
    { header: '', align: 'end', width: '300px',
      cell: s => {
        const extras: Array<{ Icon: typeof CheckCheck; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (!s.cddCompletedAt) extras.push({ Icon: CheckCheck, onClick: () => action(() => kycApi.completeCdd(s.id), t('kycCddCompleted')), title: t('kycCompleteCdd'), tone: 'primary' });
        if (s.eddRequired && !s.eddCompletedAt) extras.push({ Icon: Stamp, onClick: () => action(() => kycApi.completeEdd(s.id), t('kycEddCompleted')), title: t('kycCompleteEdd'), tone: 'accent' });
        extras.push({ Icon: Search, onClick: () => action(() => kycApi.runScreening(s.id), t('kycScreeningDone')), title: t('kycRunScreening'), tone: 'primary' });
        if (s.status !== 'APPROVED' && s.status !== 'REJECTED') {
          extras.push({ Icon: CheckCheck, onClick: () => action(() => kycApi.approve(s.id), t('kycApproved')), title: t('kycApprove'), tone: 'success' });
          extras.push({ Icon: XIcon, onClick: () => {
            const reason = window.prompt(t('kycRejectReason'), '') ?? '';
            if (!reason) return;
            void action(() => kycApi.reject(s.id, reason), t('kycRejected'));
          }, title: t('kycReject') });
        }
        extras.push({ Icon: ChevronRight, onClick: () => { setExpandedId(prev => prev === s.id ? null : s.id); void loadHits(s.id); }, title: t('kycViewHits'), tone: 'accent' });
        return <RowActions disabled={showingDemo} extras={extras} onEdit={() => { setEditing(s); setModalOpen(true); }} onDelete={() => setConfirmDel(s)} />;
      } },
  ];

  return (
    <>
      {showingDemo && <DemoDataNotice signedIn={isAuthenticated()} detailKey="demoNoticeKyc" />}
      {analytics && (
        <div className="ethics-kpi-5">
          <Kpi Icon={Activity}    label={t('kycKpiTotal')}      value={analytics.total}      tone="primary" to="/aml/sanctions/dashboard" />
          <Kpi Icon={CheckCheck}  label={t('kycKpiApproved')}   value={analytics.approved}   tone="success" to="/aml/sanctions/dashboard" />
          <Kpi Icon={Stamp}       label={t('kycKpiPep')}        value={analytics.peps}       tone="accent" to="/aml/sanctions/screen" />
          <Kpi Icon={ShieldAlert} label={t('kycKpiSanctioned')} value={analytics.sanctioned} tone="danger" to="/aml/sanctions/screen" />
          <Kpi Icon={Lock}        label={t('kycKpiProhibited')} value={analytics.prohibited} tone="danger" to="/aml/sanctions/lists" />
        </div>
      )}
      <div className="ethics-toolbar">
        <div className="ethics-toolbar__hint">{t('kycDescription')}</div>
        <Button variant="primary" Icon={Plus} onClick={() => { setEditing(null); setModalOpen(true); }}>{t('kycNew')}</Button>
      </div>
      <Card padded={false}>
        {loading ? <Loading /> :
          items.length === 0
            ? <EmptyState Icon={Search} title={t('kycEmptyTitle')} sub={t('kycEmptySub')} />
            : (
              <>
                <DataTable columns={cols} rows={items} empty={<EmptyState />} />
                {expandedId != null && (
                  <div className="ethics-kyc-expand">
                    <div className="ethics-kyc-expand__title" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
                      <span>{t('kycHitsFor')} #{expandedId}</span>
                      {!demoMode && hitFormFor !== expandedId && (
                        <Button variant="secondary" size="sm" Icon={Plus} onClick={() => openHitForm(expandedId)}>
                          {lang === 'ar' ? 'تسجيل مطابقة' : 'Record hit'}
                        </Button>
                      )}
                    </div>
                    {hitFormFor === expandedId && (
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 10,
                                    padding: 12, marginBottom: 12, border: '1px solid var(--border)',
                                    borderRadius: 6, background: 'var(--surface-2)' }}>
                        <label style={{ display: 'grid', gap: 4, fontSize: 12 }}>
                          <span style={{ color: 'var(--ink-3)' }}>{lang === 'ar' ? 'مصدر القائمة' : 'List source'}</span>
                          <select value={hitDraft.listSource} onChange={e => setHitDraft(d => ({ ...d, listSource: e.target.value }))}
                                  style={hitCtrl}>
                            {HIT_LIST_SOURCES.map(s => <option key={s.value} value={s.value}>{lang === 'ar' ? s.ar : s.en}</option>)}
                          </select>
                        </label>
                        <label style={{ display: 'grid', gap: 4, fontSize: 12 }}>
                          <span style={{ color: 'var(--ink-3)' }}>{lang === 'ar' ? 'درجة المطابقة %' : 'Match score %'}</span>
                          <input type="number" min={0} max={100} value={hitDraft.matchScore ?? ''}
                                 onChange={e => setHitDraft(d => ({ ...d, matchScore: e.target.value === '' ? undefined : Number(e.target.value) }))}
                                 style={hitCtrl} />
                        </label>
                        <label style={{ display: 'grid', gap: 4, fontSize: 12 }}>
                          <span style={{ color: 'var(--ink-3)' }}>{lang === 'ar' ? 'القرار الأولي' : 'Disposition'}</span>
                          <select value={hitDraft.disposition} onChange={e => setHitDraft(d => ({ ...d, disposition: e.target.value }))}
                                  style={hitCtrl}>
                            {HIT_DISPOSITIONS.map(s => <option key={s.value} value={s.value}>{lang === 'ar' ? s.ar : s.en}</option>)}
                          </select>
                        </label>
                        <label style={{ display: 'grid', gap: 4, fontSize: 12 }}>
                          <span style={{ color: 'var(--ink-3)' }}>{lang === 'ar' ? 'مرجع القائمة' : 'List entry ref'}</span>
                          <input value={hitDraft.listEntryRef ?? ''} onChange={e => setHitDraft(d => ({ ...d, listEntryRef: e.target.value }))}
                                 placeholder={lang === 'ar' ? 'مثال: OFAC-12345' : 'e.g. OFAC-12345'} style={hitCtrl} />
                        </label>
                        <label style={{ display: 'grid', gap: 4, fontSize: 12, gridColumn: '1 / -1' }}>
                          <span style={{ color: 'var(--ink-3)' }}>{lang === 'ar' ? 'ملخّص المطابقة' : 'Match summary'} *</span>
                          <input value={hitDraft.matchSummary ?? ''} onChange={e => setHitDraft(d => ({ ...d, matchSummary: e.target.value }))}
                                 placeholder={lang === 'ar' ? 'وصف موجز لسبب المطابقة' : 'Brief description of why this matched'} style={hitCtrl} />
                        </label>
                        <div style={{ gridColumn: '1 / -1', display: 'flex', gap: 8 }}>
                          <Button variant="primary" size="sm" disabled={savingHit} onClick={() => void submitHit(expandedId)}>
                            {savingHit ? (lang === 'ar' ? 'جارٍ الحفظ…' : 'Saving…') : (lang === 'ar' ? 'حفظ المطابقة' : 'Save hit')}
                          </Button>
                          <Button variant="ghost" size="sm" disabled={savingHit} onClick={() => setHitFormFor(null)}>
                            {lang === 'ar' ? 'إلغاء' : 'Cancel'}
                          </Button>
                        </div>
                      </div>
                    )}
                    {(hits[expandedId] ?? []).length === 0
                      ? <div className="ethics-kyc-expand__empty">{t('kycNoHits')}</div>
                      : (
                        <div className="ethics-kyc-expand__hits">
                          {(hits[expandedId] ?? []).map(h => (
                            <div key={h.id} className="ethics-kyc-hit">
                              <Badge tone="primary">{h.listSource.replace(/_/g, ' ')}</Badge>
                              <span className="mono ethics-kyc-hit__score">{h.matchScore}%</span>
                              <Badge tone={h.disposition === 'TRUE_MATCH' ? 'danger' : h.disposition === 'FALSE_POSITIVE' ? 'success' : 'warning'}>{h.disposition.replace(/_/g, ' ')}</Badge>
                              {h.sarFiled && <Badge tone="accent">SAR {h.sarReference ? `· ${h.sarReference}` : ''}</Badge>}
                              <span className="ethics-kyc-hit__summary">{h.matchSummary ?? '—'}</span>
                              {h.decidedByName && <span className="ethics-kyc-hit__decider">— {h.decidedByName}</span>}
                            </div>
                          ))}
                        </div>
                      )
                    }
                  </div>
                )}
              </>
            )
        }
      </Card>

      <FormModal<Partial<KycSubjectDto>>
        open={modalOpen}
        title={editing ? `${t('editPrefix')} · ${editing.subjectCode ?? editing.fullName}` : t('kycNew')}
        fields={FIELDS}
        initial={editing ? reqFromDto(editing) : KYC_EMPTY}
        onClose={() => { setModalOpen(false); setEditing(null); }}
        onSubmit={submitForm}
      />

      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title={t('kycConfirmDelete')}
        message={confirmDel ? `${confirmDel.fullName} ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={() => setConfirmDel(null)}
        onConfirm={executeDelete}
      />
    </>
  );
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
  const navigate = useNavigate();
  const [items, setItems] = useState<DisclosureDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DisclosureDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<DisclosureDto | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    // F2-169 — the demo gate is HERE, and it is the only one. `demoMode` is what raises the badge
    // and disables the row actions, so a substitution made anywhere else in this function is
    // fabricated data the page presents as real. Two used to be: an empty tenant was shown
    // `DISCLOSURES` (F2-166's shape — the honest empty state was unreachable code), and a failed
    // fetch was too, which turned an outage into a plausible ethics register.
    if (demoMode) { setItems(DISCLOSURES); return; }
    setLoading(true);
    try {
      setItems((await disclosureApi.list({ size: 200 })).content ?? []);
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : t('loadFailed')); }
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
    { kind: 'text',     key: 'currency',       label: t('discFldCurrency'), span: 3 },
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
    { header: t('discFldCode'),    cell: d => <span className="mono ethics-mono-bold">{d.disclosureCode ?? '—'}</span>, width: '130px' },
    { header: t('colTitle'),       cell: d => <span className="ethics-cell-title">{lang === 'ar' && d.titleAr ? d.titleAr : d.titleEn}</span> },
    { header: t('discFldType'),    cell: d => <Badge tone="primary">{d.disclosureType.replace(/_/g, ' ')}</Badge>, width: '180px' },
    { header: t('discFldDiscloser'), cell: d => d.discloserName, width: '180px' },
    { header: t('discFldValue'),   cell: d => d.monetaryValue ? `${d.monetaryValue.toLocaleString()} ${d.currency ?? 'SAR'}` : '—', mono: true, width: '130px' },
    { header: t('colStatus'),      cell: d => <Badge tone={DISCLOSURE_STATUS_TONE[d.status] ?? 'neutral'} dot>{statusLabel(d.status, lang)}</Badge>, width: '120px' },
    { header: '', align: 'end', width: '180px',
      cell: d => {
        const next = nextDiscStatus(d);
        const extras: Array<{ Icon: typeof ArrowRight; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (next) extras.push({ Icon: ArrowRight, onClick: () => transition(d, next), title: `${t('changeAdvanceTo')} ${next}`, tone: 'primary' });
        if (d.status === 'REVIEW') extras.push({ Icon: XIcon, onClick: () => transition(d, 'RECUSED'), title: t('discActionRecuse'), tone: 'accent' });
        return <RowActions disabled={demoMode} extras={extras} onEdit={() => { setEditing(d); setModalOpen(true); }} onDelete={() => setConfirmDel(d)} />;
      }
    },
  ];

  return (
    <>
      <div className="ethics-toolbar">
        <div className="ethics-toolbar__hint">
          {t('discDescription')}
        </div>
        {/* DD-79: route new disclosures through Services Center so they get
            a reference code, SLA + Ethics-Cte triage like every other intake. */}
        <Button variant="primary" Icon={Plus} onClick={() => navigate('/intake/ethics_disclosure')}>{t('discNew')}</Button>
      </div>
      <Card padded={false}>
        {loading ? <Loading /> :
          <DataTable columns={cols} rows={items}
                     empty={<EmptyState Icon={Stamp} title={t('discEmptyTitle')} sub={t('discEmptySub')} />} />
        }
      </Card>

      <FormModal<Partial<DisclosureDto>>
        open={modalOpen}
        title={editing ? `${t('editPrefix')} · ${editing.disclosureCode ?? (lang === 'ar' && editing.titleAr ? editing.titleAr : editing.titleEn)}` : t('discNew')}
        fields={FIELDS}
        initial={editing ? reqFromDto(editing) : DISCLOSURE_EMPTY}
        onClose={() => { setModalOpen(false); setEditing(null); }}
        onSubmit={submitForm}
      />

      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title={t('discConfirmDelete')}
        message={confirmDel ? `${(lang === 'ar' && confirmDel.titleAr ? confirmDel.titleAr : confirmDel.titleEn)} ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={() => setConfirmDel(null)}
        onConfirm={executeDelete}
      />
    </>
  );
}
