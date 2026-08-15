import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Fingerprint, Inbox, ShieldAlert, Users, Filter, Download, Plus, BarChart3, FileText, Database, PlayCircle, CheckCheck, X as XIcon, Clock, Send, ShieldCheck, Archive, ScrollText, Shield } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { RaiseRequestButton } from '../../components/workflow/RaiseRequestButton';
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
import { formatRetentionMonths } from '../../lib/format';
import { SEVERITY_TONE, severityLabel, STATUS_TONE, statusLabel } from '../../lib/labels';
// F2-211: the register classifies severity, resolves the awareness stamp and asks
// whether SDAIA has been notified through exactly the predicates the breach console
// on this same page uses — the two surfaces cannot answer a statutory question two
// ways again (F2-196's defect, previously with both readers inside one component).
import {
  classifySeverity, severityIsProvisional, breachSeverityInputs,
  breachAwarenessAt, isSdaiaSubmitted, breachTypeLabel, formatHoursLabel,
} from '../../lib/pdplBreach';
import { isDsrOpen, isDsrTerminal } from '../../lib/dsrLifecycle';
import { useApiList } from '../../lib/useApiResource';
import { useLiveRefresh } from '../../lib/useLiveRefresh';
import { LivePulse } from '../../components/ui/LivePulse';
import { privacyApi, dpiaApi, consentApi, ApiError, type PrivacyDsrDto, type PrivacyDsrRequest, type PrivacyBreachDto, type PrivacyRopaDto, type DpiaDto, type ConsentStatsDto } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { useToast } from '../../lib/toast';
import { DSRS, BREACHES, ROPA, DPIAS } from './mockPrivacy';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';
import { FilterModal, distinctValues, type FilterField } from '../../components/ui/FilterModal';
import { ModuleEngineRequestsPanel } from '../../components/workflows/ModuleEngineRequestsPanel';
import { WorkflowQuickLaunch } from '../../components/workflows/WorkflowQuickLaunch';
import { ModuleSubmissionsPanel } from '../../components/workflows/ModuleSubmissionsPanel';
import { PRIVACY_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { PRIVACY_ANCHORS } from '../../lib/moduleAnchors';
import { CommsPanel } from '../../components/comms/CommsPanel';
import { RegulatoryAlignmentTab } from '../../components/regulatory/RegulatoryAlignmentTab';
import { usePromotedProvenance } from '../../lib/intakeProvenance';
import { PdplBreachConsole } from '../../components/privacy/PdplBreachConsole';
import { ConsentTab } from '../../components/privacy/ConsentTab';
import { PromotedBadge } from '../../components/intake/PromotedBadge';

type TabId = 'overview' | 'dsr' | 'breaches' | 'ropa' | 'dpia' | 'consent' | 'workflows' | 'comms' | 'alignment';

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

const VALID_TABS: readonly TabId[] = ['overview', 'dsr', 'breaches', 'ropa', 'dpia', 'consent', 'workflows', 'comms', 'alignment'] as const;

export function PrivacyPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  // Tab state lives in the URL so deep-links like /privacy?tab=consent
  // open the right tab and stay shareable. Falls back to 'overview'
  // when the param is missing or unrecognised.
  const [searchParams, setSearchParams] = useSearchParams();
  const urlTab = searchParams.get('tab') as TabId | null;
  const tab: TabId = (urlTab && VALID_TABS.includes(urlTab)) ? urlTab : 'overview';
  function setTab(next: TabId) {
    const updated = new URLSearchParams(searchParams);
    if (next === 'overview') updated.delete('tab');
    else updated.set('tab', next);
    setSearchParams(updated, { replace: true });
  }
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<PrivacyDsrDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<PrivacyDsrDto | null>(null);
  const [busy, setBusy] = useState(false);

  const dsrs     = useApiList<PrivacyDsrDto>(()    => privacyApi.dsrs(0, 100),     DSRS);
  const breaches = useApiList<PrivacyBreachDto>(() => privacyApi.breaches(0, 100), BREACHES);
  const ropa     = useApiList<PrivacyRopaDto>(()   => privacyApi.ropa(0, 100),     ROPA);

  // F2-195 — the real consent rate. The KPI below read a flat `"98%"`; `/privacy/consents/stats`
  // already returns the four terminal states, so the rate is active over the whole population.
  // A tenant with no consent records has no rate — that is `null` (an em-dash), not 0% and not 98%.
  const [consentStats, setConsentStats] = useState<ConsentStatsDto | null>(null);
  useEffect(() => {
    let live = true;
    consentApi.stats()
      .then(s => { if (live) setConsentStats(s); })
      .catch(() => { if (live) setConsentStats(null); });
    return () => { live = false; };
  }, []);
  const consentRate = useMemo(() => {
    if (!consentStats) return null;
    const active = consentStats.active ?? 0;
    const total = active + (consentStats.expired ?? 0)
      + (consentStats.withdrawn ?? 0) + (consentStats.superseded ?? 0);
    return total === 0 ? null : Math.round((active / total) * 100);
  }, [consentStats]);

  // DD-71: merge intake-promoted rows into DSRs and Breaches.
  const allDsrs     = dsrs.data;
  const allBreaches = breaches.data;
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
  // ⚠⚠ F2-211: this was `b.severity !== advFilters.severity` over a field with no
  // column, so `severity` was `undefined` on every row and picking ANY band filtered
  // the whole register away. That is worse than an inert control: an empty table
  // reads as "no breaches of that severity" — a false all-clear on a PDPL register.
  // It now filters the same derived band the column displays, so the control and the
  // column cannot disagree.
  const filteredBreaches = useMemo(() => allBreaches.filter(b => {
    if (advFilters.severity && classifySeverity(breachSeverityInputs(b)) !== advFilters.severity) return false;
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
    { id: 'alignment', label: lang === 'ar' ? 'التوافق التنظيمي' : 'Regulatory alignment' },
  ];

  const dsrCols: Column<PrivacyDsrDto>[] = [
    { header: t('privacyDsrSubject'),  cell: r => <div><div style={{ fontWeight: 600 }}>{r.requesterName}{dsrProv.has(String(r.id)) && <PromotedBadge sourceReferenceCode={dsrProv.get(String(r.id))!} />}</div><div className="mono" style={{ fontSize: 11 }}>{r.requesterEmail}</div></div> },
    { header: t('privacyDsrType'),     cell: r => <Badge tone="info">{r.requestType}</Badge> },
    { header: t('privacyDsrReceived'), cell: r => fmtDate(r.receivedAt), mono: true },
    { header: t('privacyDsrDue'),      cell: r => fmtDate(r.dueDate), mono: true },
    // PDPL 30-day countdown — green ≥ 7 days, amber 1-6 days, red ≤ 0 (overdue)
    { header: t('privacyDsrSlaLeft'),
      cell: r => {
        // F2-196: was `status === 'FULFILLED' || status === 'REJECTED'` — a token this
        // register holds in 0 of 20 rows — so the 7 rows that really were finished
        // (RESOLVED, COMPLETED) each rendered a red statutory-breach countdown.
        if (isDsrTerminal(r.status)) return <span style={{ color: 'var(--ink-3)' }}>—</span>;
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
        // F2-196: same whitelist as the SLA column above, so a finished request cannot be
        // offered Fulfil / Extend / Reject. The server's guards agreed with the old test,
        // so a RESOLVED request could be re-fulfilled and have its completedAt overwritten.
        const terminal = isDsrTerminal(r.status);
        if (!terminal && r.status === 'OPEN') extras.push({ Icon: PlayCircle, onClick: () => startDsrAction(r),  title: t('privacyActStartDsr'),  tone: 'primary' });
        if (!terminal)                          extras.push({ Icon: CheckCheck, onClick: () => fulfilDsrAction(r), title: t('privacyActFulfilDsr'), tone: 'success' });
        if (!terminal)                          extras.push({ Icon: Clock,      onClick: () => extendDsrAction(r), title: t('privacyActExtendDsr'), tone: 'accent' });
        if (!terminal)                          extras.push({ Icon: XIcon,      onClick: () => rejectDsrAction(r), title: t('privacyActRejectDsr') });
        // F2-171: promoted-intake DSRs are merged in above and exist only in this browser.
        return <RowActions disabled={isDemo} extras={extras} onEdit={() => { setEditing(r); setOpen(true); }} onDelete={() => setConfirmDel(r)} />;
      } },
  ];

  const breachCols: Column<PrivacyBreachDto>[] = [
    { header: t('colTitle'),    cell: r => <div><div style={{ fontWeight: 600 }}>{r.titleEn}{breachProv.has(String(r.id)) && <PromotedBadge sourceReferenceCode={breachProv.get(String(r.id))!} />}</div><div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{(r.descriptionEn ?? '').slice(0, 80)}</div></div> },
    // F2-211: rendered `r.breachType` raw, so all 17 rows printed tokens like
    // `UNAUTHORIZED_ACCESS` into an Arabic-primary UI (house rule 7).
    { header: t('colType'),     cell: r => <Badge tone="info">{breachTypeLabel(r.breachType, lang)}</Badge> },
    // ⚠⚠ F2-211: this column read `r.severity`, a field with no column in
    // `privacy.data_breaches` — so it rendered a neutral em-dash badge on all 17
    // live rows. The band is DERIVED from the affected-subject count, exactly as
    // the console above derives it, and carries the same provisional disclosure:
    // the platform records no special-category flag and no severity, so this is
    // not a completed Article 25 risk assessment and must not read as one.
    { header: t('colSeverity'), cell: r => {
        const inputs = breachSeverityInputs(r);
        const sev = classifySeverity(inputs);
        const provisional = severityIsProvisional(inputs);
        return (
          <Badge tone={SEVERITY_TONE[sev] ?? 'neutral'} dot
            title={provisional ? t('pdplSevProvisionalHint') : undefined}>
            {severityLabel(t, sev)}
            {provisional && <span aria-label={t('pdplSevProvisionalHint')}> ·&nbsp;{t('pdplSevProvisional')}</span>}
          </Badge>
        );
      } },
    // F2-211: was `r.affectedRecords`, no column. The count the server keeps is
    // `data_subjects_affected_count`, non-null on all 17 rows — the console one
    // component above was already reading it.
    { header: t('colRecords'),  cell: r => r.dataSubjectsAffectedCount?.toLocaleString() ?? '—', mono: true },
    // F2-211: was `colOccurred` over `r.occurredAt`, no column. `discovered_at` is
    // NOT NULL and is a DIFFERENT fact — when the controller became aware, which is
    // what PDPL runs the clock from — so the header says so rather than relabelling
    // a discovery stamp as the moment of occurrence.
    { header: t('colDiscovered'), cell: r => fmtDate(breachAwarenessAt(r) || undefined), mono: true },
    // PDPL Article 20 72-hour notification clock
    //
    // ⚠⚠ F2-211: this cell returned early on `!r.occurredAt` — a field the schema
    // has no column for — so the countdown NEVER rendered against real data, on
    // every row, for every tenant. It only ever displayed under the demo fallback
    // corpus, which mints `occurredAt` from `Date.now()` and so always produced a
    // plausible live countdown (F2-194: a clock-anchored fabrication has no tell).
    //
    // Discharge is `isSdaiaSubmitted` over the server's own notification column —
    // the same predicate the console uses. It used to be a `reportedToAuthority`
    // boolean cast in with `as`, which the wire does not carry either, so the
    // "Notified" badge could not render for any row.
    { header: t('privacyBreachClock'),
      cell: r => {
        if (r.status === 'CLOSED') return <span style={{ color: 'var(--ink-3)' }}>—</span>;
        // A breach the controller has assessed as not notifiable has no Article 20
        // clock to show. Stated, not left as a bare dash that reads as missing data.
        if (r.pdpcNotificationRequired === false) {
          return <span style={{ color: 'var(--ink-3)' }} title={t('privacyBreachNotRequiredHint')}>{t('privacyBreachNotRequired')}</span>;
        }
        if (isSdaiaSubmitted(r.sdaiaNotificationStatus)) return <Badge tone="success" dot>{t('privacyBreachNotified')}</Badge>;
        const aware = breachAwarenessAt(r);
        if (!aware) return <span style={{ color: 'var(--ink-3)' }}>—</span>;
        const remaining = 72 - Math.floor((Date.now() - new Date(aware).getTime()) / 3_600_000);
        const colour = remaining < 0 ? 'var(--danger)' : remaining <= 12 ? 'var(--accent-700)' : 'var(--success-fg)';
        // F2-211: `formatHoursLabel` rather than raw hours. Once the clock actually
        // ran, the real rows read "3486h overdue" — arithmetically right and useless;
        // nobody converts that to 145 days in their head, on a statutory deadline.
        // The console beside this table already used this formatter.
        return (
          <span className="mono" style={{ color: colour, fontWeight: 700 }}>
            {remaining < 0
              ? `${formatHoursLabel(remaining, lang)} ${t('privacyBreachOverdue')}`
              : `${formatHoursLabel(remaining, lang)} ${t('privacyBreachLeft')}`}
          </span>
        );
      } },
    { header: t('colStatus'),   cell: r => <Badge tone={STATUS_TONE[r.status ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.status ?? '—')}</Badge> },
    { header: '', align: 'end', width: '210px',
      cell: r => {
        const extras: Array<{ Icon: typeof Send; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (r.status === 'OPEN')          extras.push({ Icon: ShieldCheck, onClick: () => containBreachAction(r),    title: t('privacyActContainBreach'), tone: 'primary' });
        if (r.status !== 'CLOSED')        extras.push({ Icon: Send,        onClick: () => notifyDataOfficeAction(r), title: t('privacyActNotifyDataOffice'), tone: 'accent' });
        if (r.status !== 'CLOSED')        extras.push({ Icon: Archive,     onClick: () => closeBreachAction(r),      title: t('privacyActCloseBreach'),   tone: 'success' });
        // F2-171: promoted-intake breaches are merged in above and exist only in this browser.
        return <RowActions disabled={isDemo} extras={extras} />;
      } },
  ];

  const ropaCols: Column<PrivacyRopaDto>[] = [
    { header: t('colActivity'),    cell: r => <div><div style={{ fontWeight: 600 }}>{r.activityNameEn}</div><div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.activityNameAr ?? ''}</div></div> },
    { header: t('colPurpose'),     cell: r => <span style={{ color: 'var(--ink-2)', fontSize: 12 }}>{(r.purposeEn ?? '—').slice(0, 60)}</span> },
    { header: t('colLegalBasis'),  cell: r => <Badge tone="primary">{(r.legalBasis ?? '').replace(/_/g, ' ')}</Badge> },
    // F2-210: was `r.retentionPeriod`, a key the server has never sent — this
    // column rendered an em-dash on all 11 rows while every one of them records
    // a retention period.
    { header: t('colRetention'),   cell: r => formatRetentionMonths(r.retentionPeriodMonths, lang), mono: true },
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
  // F2-196: was an inclusion list `{PENDING, IN_PROGRESS, NEW}` and so answered 9 where 12
  // are open — it dropped `OPEN`, the status the intake promotion adapter writes, so a DSR
  // filed through the platform's own front door was invisible to the PDPL Article 23 tile.
  // Open is the complement of finished; an unrecognised status counts as open.
  const openDsrs = allDsrs.filter(d => isDsrOpen(d.status)).length;

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
            <RaiseRequestButton sourceModule="PRIVACY" />
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
      <WorkflowQuickLaunch modules="PDPL" />
      <ModuleSubmissionsPanel module="PDPL" />
      <div className="kpi-grid">
        {/* F2-195 — was `ropa.data.length || 142`. `0 || 142` is 142, so the fallback fired on
            exactly the tenant whose RoPA register is EMPTY: a PDPL Article 31 record of processing
            activities showing 142 entries for an organisation that has recorded none. Same shape
            F2-52 already fixed on the Admin page (`roles.length || 7`) and left here — its comment
            states the rule this restores: "The real count is the real count, including 0." */}
        {/* F2-198 — `to` was `/ropa`, a page that rendered 9 invented activities from a
            catalogue. It now points at this page's own ROPA tab, which lists the real register. */}
        <Kpi Icon={FileText}    label={t('privacyKpiActivities')} value={ropa.data.length} tone="primary" to="/privacy?tab=ropa" />
        {/* F2-198 — the one measurable figure the retired /ropa page surfaced that this screen did
            not. `crossBorderTransfer` is already on the wire (5 of 11 activities in dev and prod),
            and a PDPL Article 29 transfer out of the Kingdom is the fact a DPO is asked about, so
            retiring the page must not silently drop it. Counted over the SAME `ropa.data` the
            tile above counts, so the two can never disagree. */}
        <Kpi Icon={Database}    label={t('privacyKpiCrossBorder')}
          value={ropa.data.filter(r => r.crossBorderTransfer).length}
          tone={ropa.data.some(r => r.crossBorderTransfer) ? 'warning' : 'success'}
          to="/privacy?tab=ropa" />
        <Kpi Icon={Inbox}       label={t('privacyKpiDsr')}        value={openDsrs} tone="warning" to="/data-officer/dsr" />
        <Kpi Icon={Users}       label={t('privacyKpiConsent')}
          value={consentRate === null ? null : `${consentRate}%`}
          emptyHint={t('privacyKpiConsentEmpty')} tone="success" to="/data-officer/consents" />
        <Kpi Icon={ShieldAlert} label={t('privacyKpiBreaches')}   value={allBreaches.filter(b => b.status !== 'CLOSED').length} tone="danger" to="/data-officer/breaches" />
      </div>

      <OverviewStrip
        storageKey="overview.privacy"
        processBarTitle={t('privacyProcessBarTitle')}
        processBarSubtitle={t('privacyProcessBarSub')}
        lifecycles={[
          // F2-197 — `openDsrs` is F2-196's corrected complement predicate (open =
          // not terminal), so the tile and the PDPL Article 23 KPI above cannot give
          // different answers about the same register.
          { ...PRIVACY_LIFECYCLES[0], activeCount: openDsrs },
          { ...PRIVACY_LIFECYCLES[1], activeCount: allBreaches.filter(b => b.status !== 'CLOSED').length },
          // DPIA / RoPA / consent / cross-border stay unmeasured. `DPIAS` is a
          // hard-coded corpus (F2-194's filed population) and a RoPA entry is a
          // register record rather than work in flight, so neither has an honest
          // "in flight" figure — the catalogue asserted 2/1 and 1/0 for both.
          PRIVACY_LIFECYCLES[2],
          PRIVACY_LIFECYCLES[3],
          PRIVACY_LIFECYCLES[4],
          PRIVACY_LIFECYCLES[5],
        ]}
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
          {dsrs.loading
            ? <LoadingRow />
            : <DataTable
                columns={dsrCols}
                rows={filteredDsrs}
                rowHref={r => `/privacy/dsrs/${r.id}`}
                empty={<EmptyState Icon={Inbox} />} />}
        </Card>
      )}
      {tab === 'breaches' && (
        <>
          {/* Sprint 4 — PDPL 72-hour response console. Picks the most
              time-sensitive breach whose SDAIA notification is still
              undischarged, and lists the Article 25 fields with per-field
              provenance. (⚠ F2-209: this comment used to say it "surfaces the
              auto-generated SDAIA notification packet" — there was no such
              thing to surface honestly, and a stale comment describing a
              retired surface is how the next reader re-adds it.) */}
          <div style={{ marginBottom: 12 }}>
            {/* ⚠⚠ F2-209: `onDispatch={notifyDataOfficeAction}` was removed, not
                repointed. The console rendered it as "Dispatch to SDAIA" on a
                72-hour statutory countdown, and `notifyDataOffice` writes
                `pdpc_notification_required` + `pdpc_notified_at` — never
                `sdaia_notification_status`, never `sdaia_submitted_at` (the
                server's own discharge field), and with no audit row. The real
                recorder is inside the console's own SDAIA panel. The row action
                below keeps `notifyDataOffice` under its own honest label. */}
            <PdplBreachConsole breaches={allBreaches} onRecorded={() => breaches.reload()} />
          </div>
          <Card padded={false}>
            {breaches.loading
              ? <LoadingRow />
              : <DataTable
                  columns={breachCols}
                  rows={filteredBreaches}
                  rowHref={r => `/privacy/breaches/${r.id}`}
                  empty={<EmptyState Icon={ShieldAlert} title={t('privacyBreachesEmpty')} sub={t('privacyBreachesEmptySub')} />} />}
          </Card>
        </>
      )}
      {tab === 'ropa' && (
        <Card padded={false}>
          {ropa.loading
            ? <LoadingRow />
            : <DataTable
                columns={ropaCols}
                rows={ropa.data}
                rowHref={r => `/privacy/ropa/${r.id}`}
                empty={<EmptyState Icon={Database} title={t('privacyRopaEmpty')} sub={t('privacyRopaEmptySub')} />} />}
        </Card>
      )}
      {tab === 'dpia' && <DpiaTab toast={toast} t={t} />}
      {tab === 'consent' && <ConsentTab />}
      {tab === 'workflows' && <ModuleEngineRequestsPanel sourceModule="PRIVACY" />}
      {tab === 'comms' && <CommsPanel module="BREACH" />}
      {tab === 'alignment' && <RegulatoryAlignmentTab domain="PRIVACY" families={['Privacy', 'International']} />}

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
  // DD-75/F2-172: intake-promoted DPIAs are real privacy.dpia rows now, so they come
  // back from the list call below like any other. There is no overlay to merge.
  const items = apiItems;

  function reload() {
    // F2-169 — the demo substitution is the line above and nowhere else. The `.catch` used to
    // answer a failed DPIA fetch with `DPIAS`, and the page's badge reads `dsrs.isDemo` — a
    // different data set — so the fabricated DPIAs arrived with no badge at all.
    if (!isAuthenticated()) { setItems(DPIAS); return; }
    setLoading(true);
    dpiaApi.list(0, 100)
      .then(p => setItems(p.content))
      .catch(e => toast.danger(e instanceof ApiError ? e.message : t('loadFailed')))
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
      {items.map(d => {
      // F2-171 disabled every button here and dropped the title link, because a
      // promoted DPIA had no server row and its id addressed nothing — occasionally, under
      // the old positive-id scheme, a real and unrelated DPIA. F2-172 removed the cause
      // rather than the symptom: promotion writes privacy.dpia, so every row on this tab
      // is a record the PDPL workflow can actually advance.
      return (
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
      );
      })}
    </Card>
  );
}
