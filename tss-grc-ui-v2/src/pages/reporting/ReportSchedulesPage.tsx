import { useEffect, useMemo, useState } from 'react';
import {
  Calendar, Clock, Mail, Play, Plus, RefreshCw, FileText, AlertTriangle,
  CheckCheck, X as XIcon, ToggleLeft, ToggleRight,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { HijriDate } from '../../components/ui/HijriDate';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import {
  reportScheduleApi, ApiError,
  type ReportScheduleDto, type ReportRunDto,
} from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import { isAuthenticated } from '../../lib/auth';
import { REPORT_SCHEDULES, REPORT_RUNS } from './mockReports';

const REPORT_TYPE_OPTIONS = [
  { value: 'RISK_REGISTER',  label: 'Risk Register' },
  { value: 'FINDINGS_AGING', label: 'Findings Aging' },
  { value: 'KRI_DASHBOARD',  label: 'KRI Dashboard' },
  { value: 'BOARD_PACK',     label: 'Board Pack (multi-section)' },
  { value: 'AI_MODEL_REGISTRY', label: 'AI/ML Model Registry (posture pack)' },
];
const FORMAT_OPTIONS = [
  { value: 'PDF',  label: 'PDF' },
  { value: 'XLSX', label: 'Excel' },
  { value: 'DOCX', label: 'Word' },
  { value: 'PPTX', label: 'PowerPoint (board-pack only)' },
];
const LANGUAGE_OPTIONS = [{ value: 'en', label: 'English' }, { value: 'ar', label: 'العربية' }];
// F2-293 — must stay in step with `ReportSchedulerService.SUPPORTED_CADENCES`, which is the set the
// scheduler can compute a next run for and which the write boundary now refuses anything outside.
// SEMIANNUAL was missing here AND from the scheduler's switch while the register stored it on a live
// row, so the semi-annual BCM readiness report was rescheduled daily — and this select could not
// express the cadence its own row held. `ReportCadenceContractTest` reads this file and fails the
// build if the two lists disagree.
const CADENCE_OPTIONS = [
  { value: 'DAILY',      label: 'Daily' },
  { value: 'WEEKLY',     label: 'Weekly' },
  { value: 'MONTHLY',    label: 'Monthly' },
  { value: 'QUARTERLY',  label: 'Quarterly' },
  { value: 'SEMIANNUAL', label: 'Semi-annual' },
];

const STATUS_TONE: Record<string, BadgeTone> = {
  SUCCESS: 'success', FAILED: 'danger', SKIPPED: 'neutral',
};

const EMPTY: Partial<ReportScheduleDto> = {
  name: '', reportType: 'RISK_REGISTER', format: 'PDF', language: 'en',
  cadence: 'WEEKLY', cadenceAnchor: 'MONDAY', timeOfDay: '06:00',
  recipients: '', isActive: true, nextRunAt: new Date(Date.now() + 60_000).toISOString(),
};

export function ReportSchedulesPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  // F2-98: state the substitution sets, not a const derived from the session — `applyDemo()`
  // also runs for a signed-in user whose schedule list comes back empty.
  const [demoMode, setDemoMode] = useState(!isAuthenticated());
  const [items, setItems] = useState<ReportScheduleDto[]>([]);
  const [recent, setRecent] = useState<ReportRunDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ReportScheduleDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<ReportScheduleDto | null>(null);
  const [busy, setBusy] = useState(false);

  function applyDemo() { setItems(REPORT_SCHEDULES); setRecent(REPORT_RUNS); setDemoMode(true); }

  async function load() {
    if (!isAuthenticated()) { applyDemo(); return; }
    setLoading(true);
    // Per-call .catch() — if `list` fails fall back to demo, but don't let
    // a transient `recent` failure also wipe out the (potentially good) list.
    const [page, runs] = await Promise.all([
      reportScheduleApi.list(0, 100).catch(() => null),
      reportScheduleApi.recent().catch(() => [] as ReportRunDto[]),
    ]);
    const list = page?.content ?? [];
    if (list.length === 0) { applyDemo(); setLoading(false); return; }
    setItems(list);
    setRecent(runs);
    setDemoMode(false);
    setLoading(false);
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, []);

  const totalActive = useMemo(() => items.filter(s => s.isActive).length, [items]);
  const failedToday = useMemo(() => {
    const d = new Date(); d.setHours(0, 0, 0, 0);
    return recent.filter(r => r.status === 'FAILED' && r.triggeredAt && new Date(r.triggeredAt) >= d).length;
  }, [recent]);
  const sentLast7d  = useMemo(() => {
    const d = new Date(); d.setDate(d.getDate() - 7);
    return recent.filter(r => r.status === 'SUCCESS' && r.triggeredAt && new Date(r.triggeredAt) >= d).length;
  }, [recent]);

  const FIELDS: FieldDef[] = [
    { kind: 'text',     key: 'name',          label: t('rsFldName') + ' *', required: true, span: 8 },
    { kind: 'select',   key: 'isActive',      label: t('rsFldActive'), options: [{ value: 'true', label: 'Active' }, { value: 'false', label: 'Paused' }], span: 4 },
    // F2-278 — `ReportScheduleService#update` has always written name_ar/description_ar from the
    // request and `ReportScheduleDto` declared neither, so both were frozen: populated on 8 of 11
    // live rows in dev and prod by the seed, and uneditable from the one screen that edits this
    // register. Arabic is the platform's primary language (house rule 7).
    { kind: 'text',     key: 'nameAr',        label: t('rsFldNameAr'), span: 12 },
    { kind: 'select',   key: 'reportType',    label: t('rsFldReportType') + ' *', required: true, options: REPORT_TYPE_OPTIONS, span: 4 },
    { kind: 'select',   key: 'format',        label: t('rsFldFormat'), options: FORMAT_OPTIONS, span: 4 },
    { kind: 'select',   key: 'language',      label: t('rsFldLanguage'), options: LANGUAGE_OPTIONS, span: 4 },
    { kind: 'select',   key: 'cadence',       label: t('rsFldCadence') + ' *', required: true, options: CADENCE_OPTIONS, span: 4 },
    { kind: 'text',     key: 'cadenceAnchor', label: t('rsFldAnchor'), span: 4, placeholder: t('rsAnchorPlaceholder') },
    { kind: 'text',     key: 'timeOfDay',     label: t('rsFldTimeOfDay') + ' (UTC)', span: 4, placeholder: '06:00' },
    { kind: 'textarea', key: 'recipients',    label: t('rsFldRecipients') + ' *', rows: 2, span: 12 },
    { kind: 'textarea', key: 'ccRecipients',  label: t('rsFldCc'), rows: 1, span: 12 },
    { kind: 'textarea', key: 'description',   label: t('rsFldDescription'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'descriptionAr', label: t('rsFldDescriptionAr'), rows: 2, span: 12 },
  ];

  function reqFromDto(s: ReportScheduleDto): Partial<ReportScheduleDto> {
    return { ...s, isActive: s.isActive };
  }

  async function submitForm(values: Partial<ReportScheduleDto>) {
    try {
      const v: Partial<ReportScheduleDto> = { ...values };
      // FormModal selects emit strings — coerce isActive
      if (typeof v.isActive === 'string') v.isActive = (v.isActive as unknown as string) === 'true';
      if (editing) await reportScheduleApi.update(editing.id, v);
      else        await reportScheduleApi.create(v);
      toast.success(editing ? t('rsUpdated') : t('rsCreated'));
      setModalOpen(false); setEditing(null); await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await reportScheduleApi.remove(confirmDel.id); toast.success('Schedule deleted'); await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }
  async function runNow(s: ReportScheduleDto) {
    try { const r = await reportScheduleApi.runNow(s.id);
      toast.success(`${t('rsRanNow')} · ${r.status}${r.message ? ' — ' + r.message : ''}`);
      await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Run failed'); }
  }
  async function togglePause(s: ReportScheduleDto) {
    try { await reportScheduleApi.update(s.id, { isActive: !s.isActive });
      toast.success(s.isActive ? t('rsPaused') : t('rsResumed'));
      await load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const cols: Column<ReportScheduleDto>[] = [
    { header: t('rsFldName'), cell: s => (
      <div>
        {/* F2-278 — prefer the Arabic label when reading in Arabic, falling back to the English
            one rather than to a blank cell: `name` is NOT NULL and `name_ar` is nullable (3 of 11
            live rows have none), so an unconditional Arabic read would empty those rows. */}
        <div style={{ fontWeight: 600 }}>{lang === 'ar' && s.nameAr ? s.nameAr : s.name}</div>
        {(lang === 'ar' && s.descriptionAr ? s.descriptionAr : s.description) && (
          <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
            {lang === 'ar' && s.descriptionAr ? s.descriptionAr : s.description}
          </div>
        )}
      </div>
    ) },
    { header: t('rsFldReportType'), cell: s => <Badge tone="primary">{s.reportType.replace(/_/g, ' ')}</Badge>, width: '160px' },
    { header: t('rsFldFormat'),     cell: s => <Badge tone={s.format === 'PDF' ? 'accent' : 'success'}>{s.format} · {s.language.toUpperCase()}</Badge>, width: '130px' },
    { header: t('rsFldCadence'),    cell: s => (
      <span style={{ fontSize: 12 }}>
        {s.cadence}
        {s.cadenceAnchor && <span style={{ color: 'var(--ink-3)' }}> · {s.cadenceAnchor}</span>}
        {s.timeOfDay && <span className="mono" style={{ color: 'var(--ink-3)' }}> @ {s.timeOfDay}</span>}
      </span>
    ), width: '200px' },
    { header: t('rsFldNextRun'),    cell: s => <HijriDate iso={s.nextRunAt} layout="stacked" mono />, width: '180px' },
    { header: t('rsFldLastRun'),    cell: s => s.lastRunAt
        ? <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Badge tone={STATUS_TONE[s.lastRunStatus ?? ''] ?? 'neutral'} dot>{s.lastRunStatus ?? '—'}</Badge>
            <HijriDate iso={s.lastRunAt} layout="stacked" mono />
          </div>
        : '—', width: '180px' },
    { header: t('rsFldActive'),     cell: s => s.isActive
        ? <Badge tone="success" dot>{t('rsActiveLabel')}</Badge>
        : <Badge tone="neutral">{t('rsPausedLabel')}</Badge>,
      width: '100px' },
    { header: '', align: 'end', width: '180px',
      cell: s => {
        const extras: Array<{ Icon: typeof Play; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [
          { Icon: Play, onClick: () => runNow(s), title: t('rsRunNow'), tone: 'primary' },
          { Icon: s.isActive ? ToggleRight : ToggleLeft, onClick: () => togglePause(s), title: s.isActive ? t('rsPause') : t('rsResume'), tone: 'accent' },
        ];
        return <RowActions disabled={demoMode} extras={extras}
                  onEdit={() => { setEditing(s); setModalOpen(true); }}
                  onDelete={() => setConfirmDel(s)} />;
      }
    },
  ];

  return (
    <Shell title={t('rsTitle')} sub={t('rsSub')}>
      <PageHeader
        eyebrow={t('rsEyebrow')}
        title={t('rsTitle')}
        sub={t('rsSub')}
        actions={
          <>
            {demoMode && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={RefreshCw} onClick={load} disabled={loading}>{t('dashRefresh')}</Button>
            <Button variant="primary" Icon={Plus} onClick={() => { setEditing(null); setModalOpen(true); }}>{t('rsNew')}</Button>
          </>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={Calendar}     label={t('rsKpiTotal')}   value={items.length} tone="primary" to="/reports/centre" />
        <Kpi Icon={Clock}        label={t('rsKpiActive')}  value={totalActive}  tone="success" to="/reports/centre" />
        <Kpi Icon={CheckCheck}   label={t('rsKpiSent7d')}  value={sentLast7d}   tone="info" to="/reports/centre" />
        <Kpi Icon={AlertTriangle} label={t('rsKpiFailedToday')} value={failedToday} tone={failedToday > 0 ? 'danger' : 'success'} to="/reports/centre" />
      </div>

      <Card padded={false}>
        {loading
          ? <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>
          : items.length === 0
            ? <EmptyState Icon={Calendar} title={t('rsEmptyTitle')} sub={t('rsEmptySub')} />
            : <DataTable columns={cols} rows={items} empty={<EmptyState />} />
        }
      </Card>

      <Card title={t('rsRecentRuns')} Icon={FileText}>
        {recent.length === 0
          ? <div style={{ color: 'var(--ink-3)', fontSize: 13 }}>{t('rsNoRuns')}</div>
          : (
            <div style={{ display: 'grid', gap: 6 }}>
              {recent.map(r => (
                <div key={r.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: 8, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', fontSize: 12 }}>
                  <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(r.status, lang)}</Badge>
                  <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.triggeredBy}</span>
                  {r.triggeredAt && <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{new Date(r.triggeredAt).toLocaleString()}</span>}
                  {typeof r.bytes === 'number' && r.bytes > 0 && <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{Math.round(r.bytes / 1024)} KB</span>}
                  {typeof r.durationMs === 'number' && <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.durationMs} ms</span>}
                  {r.recipients && <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 11, color: 'var(--ink-3)' }}><Mail size={11} /> {r.recipients}</span>}
                  {r.message && <span style={{ flex: 1, fontSize: 11, color: 'var(--ink-2)', minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.message}</span>}
                </div>
              ))}
            </div>
          )
        }
      </Card>

      <FormModal<Partial<ReportScheduleDto>>
        open={modalOpen}
        title={editing ? `${t('editPrefix')} · ${editing.name}` : t('rsNew')}
        fields={FIELDS}
        initial={editing ? reqFromDto(editing) : EMPTY}
        onClose={() => { setModalOpen(false); setEditing(null); }}
        onSubmit={submitForm}
      />

      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title={t('rsConfirmDelete')}
        message={confirmDel ? `${confirmDel.name} ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={() => setConfirmDel(null)}
        onConfirm={executeDelete}
      />

      {/* Suppress unused-var lint warning for XIcon — kept for future toolbar use */}
      <div style={{ display: 'none' }}><XIcon size={1} /></div>
    </Shell>
  );
}
