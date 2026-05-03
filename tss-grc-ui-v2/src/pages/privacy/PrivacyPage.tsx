import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
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
import { privacyApi, dpiaApi, ApiError, type PrivacyDsrDto, type PrivacyDsrRequest, type PrivacyBreachDto, type PrivacyRopaDto, type DpiaDto } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { useToast } from '../../lib/toast';
import { DSRS } from './mockPrivacy';

type TabId = 'overview' | 'dsr' | 'breaches' | 'ropa' | 'dpia' | 'consent';

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
  const [tab, setTab] = useState<TabId>('overview');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<PrivacyDsrDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<PrivacyDsrDto | null>(null);
  const [busy, setBusy] = useState(false);

  const dsrs     = useApiList<PrivacyDsrDto>(()    => privacyApi.dsrs(0, 100),     DSRS as unknown as PrivacyDsrDto[]);
  const breaches = useApiList<PrivacyBreachDto>(() => privacyApi.breaches(0, 100), []);
  const ropa     = useApiList<PrivacyRopaDto>(()   => privacyApi.ropa(0, 100),     []);

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
  ];

  const dsrCols: Column<PrivacyDsrDto>[] = [
    { header: t('privacyDsrSubject'),  cell: r => <div><div style={{ fontWeight: 600 }}>{r.requesterName}</div><div className="mono" style={{ fontSize: 11 }}>{r.requesterEmail}</div></div> },
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
    { header: t('colTitle'),    cell: r => <div><div style={{ fontWeight: 600 }}>{r.titleEn}</div><div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{(r.descriptionEn ?? '').slice(0, 80)}</div></div> },
    { header: t('colType'),     cell: r => <Badge tone="info">{r.breachType ?? '—'}</Badge> },
    { header: t('colSeverity'), cell: r => <Badge tone={SEVERITY_TONE[r.severity ?? ''] ?? 'neutral'} dot>{severityLabel(t, r.severity ?? '—')}</Badge> },
    { header: t('colRecords'),  cell: r => r.affectedRecords?.toLocaleString() ?? '—', mono: true },
    { header: t('colOccurred'), cell: r => fmtDate(r.occurredAt), mono: true },
    // PDPL Article 20 72-hour notification clock
    { header: t('privacyBreachClock'),
      cell: r => {
        if (r.status === 'CLOSED') return <span style={{ color: 'var(--ink-3)' }}>—</span>;
        const reportedToAuthority = (r as PrivacyBreachDto & { reportedToAuthority?: boolean }).reportedToAuthority ?? false;
        if (reportedToAuthority) return <Badge tone="success" dot>Notified</Badge>;
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
      toast.danger(err instanceof ApiError ? err.message : t('toastRiskFailed'));
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
    const daysStr = window.prompt('Extend the DSR due date by how many days?', '30');
    if (daysStr === null) return;
    const days = Math.max(1, Number(daysStr) || 30);
    try { await privacyApi.extendDsr(r.id, days); toast.success(`Due date extended by ${days} days`); dsrs.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Extend failed'); }
  }
  async function rejectDsrAction(r: PrivacyDsrDto) {
    if (!window.confirm('Reject this DSR? The data subject must be notified with reasoning per PDPL.')) return;
    try { await privacyApi.rejectDsr(r.id); toast.success('DSR rejected'); dsrs.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  // ── Breach workflow handlers (PDPL Article 20 — 72-hour notification) ──
  async function containBreachAction(r: PrivacyBreachDto) {
    try { await privacyApi.containBreach(r.id); toast.success('Breach contained — investigation in progress'); breaches.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function notifyDataOfficeAction(r: PrivacyBreachDto) {
    if (!window.confirm('Mark the Saudi Data Office as notified? This stamps the notification time and unlocks the audit trail.')) return;
    try { await privacyApi.notifyDataOffice(r.id); toast.success('Saudi Data Office notification recorded'); breaches.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function closeBreachAction(r: PrivacyBreachDto) {
    try { await privacyApi.closeBreach(r.id); toast.success('Breach closed'); breaches.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const isDemo = dsrs.isDemo;
  const openDsrs = dsrs.data.filter(d => d.status === 'PENDING' || d.status === 'IN_PROGRESS').length;

  return (
    <Shell title={t('modPdpl')} sub="Saudi PDPL · ISO 27701">
      <PageHeader
        eyebrow={t('privacyEyebrow')} title={t('privacyTitle')} sub={t('privacySub')}
        actions={
          <>
            {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={Filter}>{t('actionFilter')}</Button>
            <Button variant="secondary" Icon={Download}>{t('actionExport')}</Button>
            <Button variant="primary"   Icon={Plus} onClick={() => { setEditing(null); setOpen(true); }}>{t('actionNew')}</Button>
          </>
        }
      />
      <div className="kpi-grid">
        <Kpi Icon={FileText}    label={t('privacyKpiActivities')} value={ropa.data.length || 142} tone="primary" />
        <Kpi Icon={Inbox}       label={t('privacyKpiDsr')}        value={openDsrs} tone="warning" />
        <Kpi Icon={Users}       label={t('privacyKpiConsent')}    value="98%" tone="success" />
        <Kpi Icon={ShieldAlert} label={t('privacyKpiBreaches')}   value={breaches.data.filter(b => b.status !== 'CLOSED').length} tone="danger" />
      </div>
      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div className="dash-grid">
          <Card title={t('privacyTabDsr')} Icon={BarChart3} action={<Badge tone="primary">SLA · 30d</Badge>}>
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
          {dsrs.loading ? <LoadingRow /> : <DataTable columns={dsrCols} rows={dsrs.data} empty={<EmptyState Icon={Inbox} />} />}
        </Card>
      )}
      {tab === 'breaches' && (
        <Card padded={false}>
          {breaches.loading ? <LoadingRow /> : <DataTable columns={breachCols} rows={breaches.data} empty={<EmptyState Icon={ShieldAlert} title="No data breaches recorded" sub="Report breaches here within the 72-hour PDPL deadline." />} />}
        </Card>
      )}
      {tab === 'ropa' && (
        <Card padded={false}>
          {ropa.loading ? <LoadingRow /> : <DataTable columns={ropaCols} rows={ropa.data} empty={<EmptyState Icon={Database} title="No processing activities" sub="Article 30 RoPA — every processing activity must be recorded." />} />}
        </Card>
      )}
      {tab === 'dpia' && <DpiaTab toast={toast} t={t} />}
      {tab === 'consent' && (
        <div className="dash-grid">
          <Card title="Consent Coverage" Icon={Users} action={<Badge tone="primary">By channel</Badge>}>
            <Progress label="Web sign-up form"   value={98} tone="success" />
            <Progress label="Mobile app onboarding" value={94} tone="success" />
            <Progress label="Branch / paper form"   value={71} tone="accent" />
            <Progress label="Phone (recorded)"       value={62} tone="primary" />
          </Card>
          <Card title="Withdrawals (90d)" Icon={ShieldAlert}>
            <Progress label="Marketing emails" value={6}  tone="accent" />
            <Progress label="SMS notifications" value={4}  tone="primary" />
            <Progress label="Profiling"         value={2}  tone="success" />
            <Progress label="3rd-party sharing" value={1}  tone="success" />
          </Card>
        </div>
      )}

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
  const [items, setItems] = useState<DpiaDto[]>([]);
  const [loading, setLoading] = useState(false);

  function reload() {
    if (!isAuthenticated()) { setItems([]); return; }
    setLoading(true);
    dpiaApi.list(0, 100)
      .then(p => setItems(p.content))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }
  useEffect(() => { reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function submit(d: DpiaDto)  { try { await dpiaApi.submitForReview(d.id); toast.success('DPIA submitted'); reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); } }
  async function approve(d: DpiaDto) {
    const conclusion = window.prompt('Conclusion (optional)?', '') ?? '';
    try { await dpiaApi.approve(d.id, conclusion); toast.success('DPIA approved'); reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Approve failed'); }
  }
  async function reject(d: DpiaDto)  {
    const reason = window.prompt('Rejection reason?', '') ?? '';
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
