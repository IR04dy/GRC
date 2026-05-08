import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Fingerprint, Inbox, ShieldAlert, Users, Filter, Download, Plus, BarChart3, FileText, Database, PlayCircle, CheckCheck, X as XIcon, Clock, Send, ShieldCheck, Archive, ScrollText, Shield } from 'lucide-react';
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
import { SEVERITY_TONE, severityLabel, STATUS_TONE, statusLabel } from '../../lib/labels';
import { useApiList } from '../../lib/useApiResource';
import { useLiveRefresh } from '../../lib/useLiveRefresh';
import { LivePulse } from '../../components/ui/LivePulse';
import { privacyApi, dpiaApi, ApiError, type PrivacyDsrDto, type PrivacyDsrRequest, type PrivacyBreachDto, type PrivacyRopaDto, type DpiaDto } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { useToast } from '../../lib/toast';
import { DSRS, BREACHES, ROPA, DPIAS } from './mockPrivacy';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';
import { FilterModal, distinctValues, type FilterField } from '../../components/ui/FilterModal';
import { ActiveWorkflowsPanel } from '../../components/workflows/ActiveWorkflowsPanel';
import { PRIVACY_WORKFLOWS } from '../../lib/mockWorkflows';
import { PRIVACY_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { PRIVACY_ANCHORS } from '../../lib/moduleAnchors';
import { CommsPanel } from '../../components/comms/CommsPanel';
import { usePromoted } from '../../lib/promotedRecords';
import { toDsrDto, toBreachDto, toDpiaDto, usePromotedProvenance } from '../../lib/promotedAdapters';
import { PdplBreachConsole } from '../../components/privacy/PdplBreachConsole';
import { PromotedBadge } from '../../components/intake/PromotedBadge';

type TabId = 'overview' | 'dsr' | 'breaches' | 'ropa' | 'dpia' | 'consent' | 'workflows' | 'comms';

const TYPE_OPTIONS = [
  { value: 'ACCESS', label: 'Access' }, { value: 'ERASURE', label: 'Erasure' },
  { value: 'RECTIFICATION', label: 'Rectification' }, { value: 'PORTABILITY', label: 'Portability' },
  { value: 'OBJECTION', label: 'Objection' },
];
const STATUS_OPTIONS = [
  { value: 'PENDING', label: 'Pending' }, { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'CLOSED', label: 'Closed' }, { value: 'APPROVED', label: 'Approved' },
];
// FIELDS now built inside component
const EMPTY: PrivacyDsrRequest = {
  requestType: 'ACCESS', requesterName: '', requesterEmail: '',
  receivedAt: '', dueDate: '', status: 'PENDING', description: '',
};

function dsrToReq(d: PrivacyDsrDto): PrivacyDsrRequest {
  return {
    requestType: d.requestType, requesterName: d.requesterName,
    requesterEmail: d.requesterEmail, receivedAt: d.receivedAt?.slice(0, 10),
    dueDate: d.dueDate, status: d.status,
  };
}

export function PrivacyPage() {
  const { t } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [tab, setTab] = useState<TabId>('overview');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<PrivacyDsrDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<PrivacyDsrDto | null>(null);
  const [busy, setBusy] = useState(false);

  const dsrs     = useApiList<PrivacyDsrDto>(()    => privacyApi.dsrs(0, 100),     DSRS);
  const breaches = useApiList<PrivacyBreachDto>(() => privacyApi.breaches(0, 100), BREACHES);
  const ropa     = useApiList<PrivacyRopaDto>(()   => privacyApi.ropa(0, 100),     ROPA);

  // DD-71: merge intake-promoted rows into DSRs and Breaches.
  const promotedDsrs     = usePromoted('DSR').map(toDsrDto);
  const promotedBreaches = usePromoted('BREACH').map(toBreachDto);
  const allDsrs     = useMemo(() => [...promotedDsrs, ...dsrs.data], [promotedDsrs, dsrs.data]);
  const allBreaches = useMemo(() => [...promotedBreaches, ...breaches.data], [promotedBreaches, breaches.data]);
  const dsrProv    = usePromotedProvenance('DSR');
  const breachProv = usePromotedProvenance('BREACH');

  // ── DD-47: filter modal — applies tab-specific filters ─────────────
  const [filterOpen, setFilterOpen] = useState(false);
  const [advFilters, setAdvFilters] = useState<Record<string, string | undefined>>({});
  const filteredDsrs = useMemo(() => allDsrs.filter(d => {
    if (advFilters.status      && d.status !== advFilters.status) return false;
    if (advFilters.requestType && d.requestType !== advFilters.requestType) return false;
    return true;
  }), [allDsrs, advFilters]);
  const filteredBreaches = useMemo(() => allBreaches.filter(b => {
    if (advFilters.severity && b.severity !== advFilters.severity) return false;
    if (advFilters.status   && b.status !== advFilters.status) return false;
    return true;
  }), [allBreaches, advFilters]);
  const privacyFilterFields: FilterField[] = (() => {
    if (tab === 'breaches') return [
      { key: 'severity', label: t('filterFieldSeverity'), kind: 'select',
        options: [{value:'LOW',label:'LOW'},{value:'MEDIUM',label:'MEDIUM'},{value:'HIGH',label:'HIGH'},{value:'CRITICAL',label:'CRITICAL'}] },
      { key: 'status',   label: t('filterFieldStatus'),   kind: 'select', options: distinctValues(breaches.data, 'status') },
    ];
    return [
      { key: 'status',      label: t('filterFieldStatus'),       kind: 'select', options: distinctValues(dsrs.data, 'status') },
      { key: 'requestType', label: t('filterFieldType'),         kind: 'select', options: distinctValues(dsrs.data, 'requestType') },
    ];
  })();

  const FIELDS: FieldDef[] = [
    { kind: 'text',   key: 'requesterName',  label: t('privacyFldReqName') + ' *', required: true, maxLength: 200, span: 6 },
    { kind: 'text',   key: 'requesterEmail', label: t('privacyFldReqEmail'), maxLength: 200, span: 6 },
    { kind: 'select', key: 'requestType',    label: t('privacyFldReqType') + ' *', options: TYPE_OPTIONS, required: true, span: 4 },
    { kind: 'select', key: 'status',         label: t('fldStatusLbl'), options: STATUS_OPTIONS, span: 4 },
    { kind: 'date',   key: 'receivedAt',     label: t('fldReceivedAt'), span: 4 },
    { kind: 'date',   key: 'dueDate',        label: t('fldDueDate'), span: 6 },
    { kind: 'textarea', key: 'description',  label: t('fldDescriptionLbl'), rows: 3, span: 12 },
  ];

  const tabs: TabItem<TabId>[] = [
    { id: 'overview', label: t('tabOverview') },
    { id: 'dsr',      label: t('privacyTabDsr') },
    { id: 'breaches', label: t('privacyTabBreaches') },
    { id: 'ropa',     label: t('privacyTabRopa') },
    { id: 'dpia',     label: t('privacyTabDpia') },
    { id: 'consent',  label: t('privacyTabConsent') },
    { id: 'workflows', label: t('privacyTabWorkflows') ?? 'Workflows' },
    { id: 'comms',     label: t('detailTabComms') },
  ];

  const dsrCols: Column<PrivacyDsrDto>[] = [
    { header: t('privacyDsrSubject'),  cell: r => <div><div style={{ fontWeight: 600 }}>{r.requesterName}{dsrProv.has(r.id) && <PromotedBadge sourceReferenceCode={dsrProv.get(r.id)!} />}</div><div className="mono" style={{ fontSize: 11 }}>{r.requesterEmail}</div></div> },
    { header: t('privacyDsrType'),     cell: r => <Badge tone="info">{r.requestType}</Badge> },
    { header: t('privacyDsrReceived'), cell: r => fmtDate(r.receivedAt), mono: true },
    { header: t('privacyDsrDue'),      cell: r => fmtDate(r.dueDate), mono: true },
    // PDPL 30-day countdown — green ≥ 7 days, amber 1-6 days, red ≤ 0 (overdue)
    { header: t('privacyDsrSlaLeft'),
      cell: r => {
        if (r.status === 'FULFILLED' || r.status === 'REJECTED') return <span style={{ color: 'var(--ink-3)' }}>—</span>;
        if (!r.dueDate) return <span style={{ color: 'var(--ink-3)' }}>—</span>;
        const ms = new Date(r.dueDate).getTime() - Date.now();
        const days = Math.ceil(ms / 86400000);
        const colour = days < 0 ? 'var(--danger)' : days <= 6 ? 'var(--accent-700)' : 'var(--success-fg)';
        return <span className="mono" style={{ color: colour, fontWeight: 700 }}>{days < 0 ? `${Math.abs(days)}d overdue` : `${days}d left`}</span>;
      } },
    { header: t('bcmStatusCol'),       cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(t, r.status)}</Badge> },
    { header: '', align: 'end', width: '230px',
      cell: r => {
        const extras: Array<{ Icon: typeof PlayCircle; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        const terminal = r.status === 'FULFILLED' || r.status === 'REJECTED';
        if (!terminal && r.status === 'OPEN') extras.push({ Icon: PlayCircle, onClick: () => startDsrAction(r),  title: t('privacyActStartDsr'),  tone: 'primary' });
        if (!terminal)                          extras.push({ Icon: CheckCheck, onClick: () => fulfilDsrAction(r), title: t('privacyActFulfilDsr'), tone: 'success' });
        if (!terminal)                          extras.push({ Icon: Clock,      onClick: () => extendDsrAction(r), title: t('privacyActExtendDsr'), tone: 'accent' });
        if (!terminal)                          extras.push({ Icon: XIcon,      onClick: () => rejectDsrAction(r), title: t('privacyActRejectDsr') });
        return <RowActions extras={extras} onEdit={() => { setEditing(r); setOpen(true); }} onDelete={() => setConfirmDel(r)} />;
      } },
  ];

  const breachCols: Column<PrivacyBreachDto>[] = [
    { header: t('colTitle'),    cell: r => <div><div style={{ fontWeight: 600 }}>{r.titleEn}{breachProv.has(r.id) && <PromotedBadge sourceReferenceCode={breachProv.get(r.id)!} />}</div><div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{(r.descriptionEn ?? '').slice(0, 80)}</div></div> },
    { header: t('colType'),     cell: r => <Badge tone="info">{r.breachType ?? '—'}</Badge> },
    { header: t('colSeverity'), cell: r => <Badge tone={SEVERITY_TONE[r.severity ?? ''] ?? 'neutral'} dot>{severityLabel(t, r.severity ?? '—')}</Badge> },
    { header: t('colRecords'),  cell: r => r.affectedRecords?.toLocaleString() ?? '—', mono: true },
    { header: t('colOccurred'), cell: r => fmtDate(r.occurredAt), mono: true },
    // PDPL Article 20 72-hour notification clock
    { header: t('privacyBreachClock'),
      cell: r => {
        if (r.status === 'CLOSED') return <span style={{ color: 'var(--ink-3)' }}>—</span>;
        const reportedToAuthority = (r as PrivacyBreachDto & { reportedToAuthority?: boolean }).reportedToAuthority ?? false;
        if (reportedToAuthority) return <Badge tone="success" dot>{t('privacyBreachNotified')}</Badge>;
        if (!r.occurredAt) return <span style={{ color: 'var(--ink-3)' }}>—</span>;
        const ms = Date.now() - new Date(r.occurredAt).getTime();
        const hours = Math.floor(ms / 3600000);
        const remaining = 72 - hours;
        const colour = remaining < 0 ? 'var(--danger)' : remaining <= 12 ? 'var(--accent-700)' : 'var(--success-fg)';
        return <span className="mono" style={{ color: colour, fontWeight: 700 }}>{remaining < 0 ? `${Math.abs(remaining)}h overdue` : `${remaining}h left`}</span>;
      } },
    { header: t('colStatus'),   cell: r => <Badge tone={STATUS_TONE[r.status ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.status ?? '—')}</Badge> },
    { header: '', align: 'end', width: '210px',
      cell: r => {
        const extras: Array<{ Icon: typeof Send; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (r.status === 'OPEN')          extras.push({ Icon: ShieldCheck, onClick: () => containBreachAction(r),    title: t('privacyActContainBreach'), tone: 'primary' });
        if (r.status !== 'CLOSED')        extras.push({ Icon: Send,        onClick: () => notifyDataOfficeAction(r), title: t('privacyActNotifyDataOffice'), tone: 'accent' });
        if (r.status !== 'CLOSED')        extras.push({ Icon: Archive,     onClick: () => closeBreachAction(r),      title: t('privacyActCloseBreach'),   tone: 'success' });
        return <RowActions extras={extras} />;
      } },
  ];

  const ropaCols: Column<PrivacyRopaDto>[] = [
    { header: t('colActivity'),    cell: r => <div><div style={{ fontWeight: 600 }}>{r.activityNameEn}</div><div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.activityNameAr ?? ''}</div></div> },
    { header: t('colPurpose'),     cell: r => <span style={{ color: 'var(--ink-2)', fontSize: 12 }}>{(r.purposeEn ?? '—').slice(0, 60)}</span> },
    { header: t('colLegalBasis'),  cell: r => <Badge tone="primary">{(r.legalBasis ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colRetention'),   cell: r => r.retentionPeriod ?? '—', mono: true },
    { header: t('colStatus'),      cell: r => <Badge tone={STATUS_TONE[r.status ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.status ?? '—')}</Badge> },
  ];

  async function onSubmit(values: PrivacyDsrRequest) {
    try {
      if (editing) await privacyApi.updateDsr(editing.id, values);
      else await privacyApi.createDsr(values);
      toast.success(editing ? 'DSR updated' : t('toastRiskAdded'));
      setOpen(false); setEditing(null); dsrs.reload();
    } catch (err) {
      // Demo-mode fallback: when the backend is unreachable / returns 403,
      // inject the record into the local list optimistically so the user
      // can still demonstrate the customer-DSR flow. Real auth bypasses
      // this branch entirely.
      const isAuthIssue = err instanceof ApiError && (err.status === 401 || err.status === 403);
      if (isAuthIssue || !(err instanceof ApiError)) {
        if (editing) {
          dsrs.replace(prev => prev.map(d => d.id === editing.id ? { ...d, ...values } : d));
          toast.success('DSR updated (demo mode)');
        } else {
          const nextId = Math.max(0, ...dsrs.data.map(d => d.id)) + 1;
          const newDsr: PrivacyDsrDto = { id: nextId, ...values, status: values.status ?? 'PENDING' } as PrivacyDsrDto;
          dsrs.replace(prev => [newDsr, ...prev]);
          toast.success(t('toastRiskAdded'));
        }
        setOpen(false); setEditing(null);
      } else {
        toast.danger(err.message);
      }
    }
  }
  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await privacyApi.deleteDsr(confirmDel.id); toast.success('DSR deleted'); dsrs.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }

  // ── DSR workflow handlers (PDPL 30-day SLA) ──
  async function startDsrAction(r: PrivacyDsrDto) {
    try { await privacyApi.startDsr(r.id); toast.success('DSR started; 30-day SLA tracker armed'); dsrs.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function fulfilDsrAction(r: PrivacyDsrDto) {
    try { await privacyApi.fulfilDsr(r.id); toast.success('DSR fulfilled within SLA'); dsrs.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function extendDsrAction(r: PrivacyDsrDto) {
    const daysStr = window.prompt(t('privacyPromptExtendDsr'), '30');
    if (daysStr === null) return;
    const days = Math.max(1, Number(daysStr) || 30);
    try { await privacyApi.extendDsr(r.id, days); toast.success(`${t('privacyToastDsrExtended')} ${days} ${t('privacyToastDsrExtendedDays')}`); dsrs.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Extend failed'); }
  }
  async function rejectDsrAction(r: PrivacyDsrDto) {
    if (!window.confirm(t('privacyPromptRejectDsr'))) return;
    try { await privacyApi.rejectDsr(r.id); toast.success('DSR rejected'); dsrs.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  // ── Breach workflow handlers (PDPL Article 20 — 72-hour notification) ──
  async function containBreachAction(r: PrivacyBreachDto) {
    try { await privacyApi.containBreach(r.id); toast.success('Breach contained — investigation in progress'); breaches.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function notifyDataOfficeAction(r: PrivacyBreachDto) {
    if (!window.confirm(t('privacyPromptNotifyDataOffice'))) return;
    try { await privacyApi.notifyDataOffice(r.id); toast.success('Saudi Data Office notification recorded'); breaches.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function closeBreachAction(r: PrivacyBreachDto) {
    try { await privacyApi.closeBreach(r.id); toast.success('Breach closed'); breaches.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const isDemo = dsrs.isDemo;
  const openDsrs = allDsrs.filter(d => d.status === 'PENDING' || d.status === 'IN_PROGRESS' || d.status === 'NEW').length;

  // Sprint 4 §2.7 — live privacy dashboard (DSR SLA, breach lifecycle)
  const { pulse } = useLiveRefresh({
    kinds: ['changed.dsr_case', 'changed.data_breach', 'changed.processing_activity'],
    onRefresh: () => { dsrs.reload(); breaches.reload(); ropa.reload(); },
    waitMs: 600,
    enabled: !isDemo,
  });

  return (
    <Shell title={t('modPdpl')} sub="Saudi PDPL · ISO 27701">
      <PageHeader
        eyebrow={t('privacyEyebrow')} title={t('privacyTitle')} sub={t('privacySub')}
        actions={
          <>
            {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
            {!isDemo && <LivePulse pulse={pulse} />}
            <Button variant="secondary" Icon={Filter} onClick={() => setFilterOpen(true)}>
              {t('actionFilter')}{Object.values(advFilters).filter(Boolean).length > 0 ? ` · ${Object.values(advFilters).filter(Boolean).length}` : ''}
            </Button>
            <Button variant="secondary" Icon={Download} onClick={() => {
              const map: Record<string, { rows: ExportRow[]; name: string }> = {
                dsr:      { rows: filteredDsrs as unknown as ExportRow[],     name: 'privacy-dsrs' },
                breaches: { rows: filteredBreaches as unknown as ExportRow[], name: 'privacy-breaches' },
                ropa:     { rows: ropa.data as unknown as ExportRow[],        name: 'privacy-ropa' },
                dpia:     { rows: DPIAS as unknown as ExportRow[],            name: 'privacy-dpias' },
              };
              const cur = map[tab] ?? map.dsr;
              downloadCsv(cur.rows, stampFilename(cur.name, 'csv'));
              toast.success(t('exportCsvDone'));
            }}>{t('actionExport')}</Button>
            {/* DD-76: route DSR / breach / DPIA creation through Services Center.
                For tabs without a registered intake (RoPA, consent), fall back to inline modal. */}
            <Button variant="primary"   Icon={Plus} onClick={() => {
              const kindByTab: Partial<Record<TabId, string>> = {
                dsr: 'dsr_request', breaches: 'privacy_breach', dpia: 'dpia_initiation',
              };
              const kind = kindByTab[tab];
              if (kind) navigate(`/intake/${kind}`);
              else { setEditing(null); setOpen(true); }
            }}>{t('actionNew')}</Button>
          </>
        }
      />
      <div className="kpi-grid">
        <Kpi Icon={FileText}    label={t('privacyKpiActivities')} value={ropa.data.length || 142} tone="primary" />
        <Kpi Icon={Inbox}       label={t('privacyKpiDsr')}        value={openDsrs} tone="warning" />
        <Kpi Icon={Users}       label={t('privacyKpiConsent')}    value="98%" tone="success" />
        <Kpi Icon={ShieldAlert} label={t('privacyKpiBreaches')}   value={allBreaches.filter(b => b.status !== 'CLOSED').length} tone="danger" />
      </div>

      <OverviewStrip
        storageKey="overview.privacy"
        processBarTitle={t('privacyProcessBarTitle')}
        processBarSubtitle={t('privacyProcessBarSub')}
        lifecycles={PRIVACY_LIFECYCLES}
        postureTitle={t('privacyPostureTitle')}
        anchors={PRIVACY_ANCHORS}
        />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div className="dash-grid">
          <Card title={t('privacyTabDsr')} Icon={BarChart3} action={<Badge tone="primary">{t('privacySlaBadge')}</Badge>}>
            {['ACCESS','ERASURE','RECTIFICATION','PORTABILITY','OBJECTION'].map(type => {
              const count = dsrs.data.filter(d => d.requestType === type).length;
              const pct = Math.round((count / Math.max(1, dsrs.data.length)) * 100);
              const tone = type === 'ERASURE' ? 'danger' : type === 'OBJECTION' ? 'accent' : 'primary';
              return <Progress key={type} label={`${type[0]}${type.slice(1).toLowerCase()} · ${count}`} value={pct} tone={tone} />;
            })}
          </Card>
          <Card title={t('privacyTabRopa')} Icon={Fingerprint}>
            {ropa.data.length === 0
              ? <Progress label="HR" value={100} tone="success" />
              : Array.from(new Set(ropa.data.map(r => r.legalBasis))).filter(Boolean).slice(0, 5).map(basis => {
                  const cnt = ropa.data.filter(r => r.legalBasis === basis).length;
                  const pct = Math.round((cnt / ropa.data.length) * 100);
                  return <Progress key={basis as string} label={(basis as string).replace(/_/g, ' ')} value={pct} tone="primary" />;
                })}
          </Card>
        </div>
      )}
      {tab === 'dsr' && (
        <Card padded={false}>
          {dsrs.loading ? <LoadingRow /> : <DataTable columns={dsrCols} rows={filteredDsrs} empty={<EmptyState Icon={Inbox} />} />}
        </Card>
      )}
      {tab === 'breaches' && (
        <>
          {/* Sprint 4 — PDPL 72-hour response console. Picks the most
              time-sensitive open breach automatically and surfaces the
              auto-generated SDAIA notification packet (PDPL Art. 25). */}
          <div style={{ marginBottom: 12 }}>
            <PdplBreachConsole breaches={allBreaches} />
          </div>
          <Card padded={false}>
            {breaches.loading ? <LoadingRow /> : <DataTable columns={breachCols} rows={filteredBreaches} empty={<EmptyState Icon={ShieldAlert} title={t('privacyBreachesEmpty')} sub={t('privacyBreachesEmptySub')} />} />}
          </Card>
        </>
      )}
      {tab === 'ropa' && (
        <Card padded={false}>
          {ropa.loading ? <LoadingRow /> : <DataTable columns={ropaCols} rows={ropa.data} empty={<EmptyState Icon={Database} title={t('privacyRopaEmpty')} sub={t('privacyRopaEmptySub')} />} />}
        </Card>
      )}
      {tab === 'dpia' && <DpiaTab toast={toast} t={t} />}
      {tab === 'consent' && (
        <div className="dash-grid">
          <Card title={t('privacyConsentCard')} Icon={Users} action={<Badge tone="primary">{t('privacyConsentChannel')}</Badge>}>
            <Progress label={t('privacyChannelWeb')}    value={98} tone="success" />
            <Progress label={t('privacyChannelMobile')} value={94} tone="success" />
            <Progress label={t('privacyChannelBranch')} value={71} tone="accent" />
            <Progress label={t('privacyChannelPhone')}  value={62} tone="primary" />
          </Card>
          <Card title={t('privacyWithdrawalsCard')} Icon={ShieldAlert}>
            <Progress label={t('privacyWithdrawMarketing')} value={6}  tone="accent" />
            <Progress label="SMS notifications" value={4}  tone="primary" />
            <Progress label="Profiling"         value={2}  tone="success" />
            <Progress label="3rd-party sharing" value={1}  tone="success" />
          </Card>
        </div>
      )}
      {tab === 'workflows' && <ActiveWorkflowsPanel
        workflows={PRIVACY_WORKFLOWS}
        title={t('privacyTabWorkflows') ?? 'Workflows'}
        sub="DSR fulfilment (PDPL 30-day), breach notification (PDPL 72-hour) and DPIA review."
        t={t} />}
      {tab === 'comms' && <CommsPanel module="BREACH" />}

      <FormModal<PrivacyDsrRequest>
        open={open}
        title={editing ? `${t('editPrefix')} · ${editing.requesterName}` : t('newDsrTitle')}
        fields={FIELDS}
        initial={editing ? dsrToReq(editing) : EMPTY}
        onClose={() => { setOpen(false); setEditing(null); }}
        onSubmit={onSubmit}
      />
      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title="Delete this DSR?"
        message={confirmDel ? `Request from "${confirmDel.requesterName}" will be removed. This cannot be undone.` : ''}
        confirmLabel="Delete"
        onCancel={() => setConfirmDel(null)} onConfirm={executeDelete}
      />
      <FilterModal open={filterOpen} fields={privacyFilterFields} value={advFilters}
        onApply={v => { setAdvFilters(v); toast.success(t('filterModalApplied')); }}
        onClose={() => setFilterOpen(false)} />
    </Shell>
  );
}

function fmtDate(s?: string): string { return s ? s.slice(0, 10) : '—'; }
function LoadingRow() {
  const { t } = useI18n();
  return <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>;
}

const DPIA_RISK_TONE: Record<string, 'danger' | 'warning' | 'info' | 'success' | 'neutral'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'success',
};

/**
 * DPIA tab — assessment §5.4 critical recommendation #3.
 * Lists DPIA records with their state, residual risk, and exposes the
 * lifecycle workflow (submit-for-review / approve / reject / expire).
 */
function DpiaTab({ toast, t }: { toast: ReturnType<typeof useToast>; t: ReturnType<typeof useI18n>['t'] }) {
  const [apiItems, setItems] = useState<DpiaDto[]>([]);
  const [loading, setLoading] = useState(false);
  // DD-75: merge intake-promoted DPIAs into the visible register.
  const promoted = usePromoted('DPIA').map(toDpiaDto);
  const items = [...promoted, ...apiItems];

  function reload() {
    if (!isAuthenticated()) { setItems(DPIAS); return; }
    setLoading(true);
    dpiaApi.list(0, 100)
      .then(p => setItems(p.content))
      .catch(() => setItems(DPIAS))
      .finally(() => setLoading(false));
  }
  useEffect(() => { reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function submit(d: DpiaDto)  { try { await dpiaApi.submitForReview(d.id); toast.success('DPIA submitted'); reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); } }
  async function approve(d: DpiaDto) {
    const conclusion = window.prompt(t('privacyPromptDpiaConclusion'), '') ?? '';
    try { await dpiaApi.approve(d.id, conclusion); toast.success('DPIA approved'); reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Approve failed'); }
  }
  async function reject(d: DpiaDto)  {
    const reason = window.prompt(t('privacyPromptDpiaRejection'), '') ?? '';
    if (!reason.trim()) return;
    try { await dpiaApi.reject(d.id, reason); toast.success('DPIA rejected'); reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Reject failed'); }
  }
  async function expire(d: DpiaDto)  { try { await dpiaApi.expire(d.id); toast.success('DPIA expired'); reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Expire failed'); } }

  if (loading) return <Card padded><div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div></Card>;
  if (items.length === 0) return <Card padded><EmptyState Icon={ScrollText} title={t('privacyDpiaEmptyTitle')} sub={t('privacyDpiaEmptySub')} /></Card>;

  return (
    <Card padded={false}>
      {items.map(d => (
        <div key={d.id} style={{
          display: 'grid', gridTemplateColumns: '1fr auto auto auto auto',
          gap: 12, padding: '12px 16px', borderBottom: '1px solid var(--border)', alignItems: 'center',
        }}>
          <div>
            <Link to={`/dpias/${d.id}`} style={{ fontWeight: 700, fontSize: 13, color: 'var(--ink-1)', textDecoration: 'none' }}>{d.titleEn}</Link>
            {d.titleAr && <div style={{ fontSize: 11, color: 'var(--ink-3)' }} dir="rtl">{d.titleAr}</div>}
            <div style={{ marginTop: 4, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              <Badge tone="primary">{d.triggerReason.replace(/_/g, ' ')}</Badge>
              {d.assessmentDate && <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>assessed {d.assessmentDate}</span>}
              {d.nextReviewDate && <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>· review due {d.nextReviewDate}</span>}
            </div>
            {d.conclusion && <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 4 }}>{d.conclusion.slice(0, 200)}</div>}
            {d.rejectionReason && <div style={{ fontSize: 11, color: 'var(--danger)', marginTop: 4 }}>Rejected: {d.rejectionReason.slice(0, 150)}</div>}
          </div>
          <Badge tone={STATUS_TONE[d.status] ?? 'info'} dot>{d.status.replace(/_/g, ' ')}</Badge>
          <Badge tone={DPIA_RISK_TONE[d.overallResidualRisk ?? ''] ?? 'neutral'}>
            <Shield size={10} style={{ marginRight: 3, verticalAlign: 'middle' }} />
            {d.overallResidualRisk ?? '—'}
          </Badge>
          <div style={{ display: 'flex', gap: 6 }}>
            {d.status === 'DRAFT'     && <Button size="sm" variant="ghost" Icon={Send}       onClick={() => submit(d)}>{t('privacyDpiaActSubmit')}</Button>}
            {d.status === 'IN_REVIEW' && <Button size="sm" variant="primary" Icon={CheckCheck} onClick={() => approve(d)}>{t('privacyDpiaActApprove')}</Button>}
            {(d.status === 'IN_REVIEW' || d.status === 'DRAFT') && <Button size="sm" variant="ghost" Icon={XIcon} onClick={() => reject(d)}>{t('privacyDpiaActReject')}</Button>}
            {d.status === 'APPROVED'  && <Button size="sm" variant="ghost" Icon={Clock}      onClick={() => expire(d)}>{t('privacyDpiaActExpire')}</Button>}
          </div>
          <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>#{d.id}</span>
        </div>
      ))}
    </Card>
  );
}
