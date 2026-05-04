import { useMemo, useState } from 'react';
import {
  AlertOctagon, Flame, Timer, CheckCircle2, Filter, Download, Plus, BarChart3, Lightbulb,
  Activity, FileText, AlertTriangle, ShieldCheck, RefreshCw, ArrowRight, Send, Archive, Scroll,
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
import { BarChart3D } from '../../components/viz/BarChart3D';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { Timeline, type TimelineEvent } from '../../components/ui/Timeline';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { STATUS_TONE, SEVERITY_TONE, statusLabel, severityLabel } from '../../lib/labels';
import { useApiList } from '../../lib/useApiResource';
import { incidentApi, ApiError, type IncidentDto, type IncidentRequest, type RegulatoryTemplate, type SaudiRegulator } from '../../lib/api';
import { Modal } from '../../components/ui/Modal';
import { useToast } from '../../lib/toast';
import { INCIDENTS } from './mockIncidents';
import { INCIDENT_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { INCIDENT_ANCHORS } from '../../lib/moduleAnchors';
import { CommsPanel } from '../../components/comms/CommsPanel';

type TabId = 'overview' | 'register' | 'timeline' | 'lessons' | 'comms';

const SEVERITY_OPTIONS = [
  { value: 'CRITICAL', label: 'Critical' }, { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' }, { value: 'LOW', label: 'Low' },
];
const CATEGORY_OPTIONS = [
  { value: 'CYBER', label: 'Cyber' }, { value: 'PRIVACY', label: 'Privacy' },
  { value: 'OPS', label: 'Operations' }, { value: 'PHYSICAL', label: 'Physical' },
  { value: 'COMPLIANCE', label: 'Compliance' },
];
const STATUS_OPTIONS = [
  { value: 'OPEN', label: 'Open' }, { value: 'INVESTIGATING', label: 'Investigating' },
  { value: 'CONTAINED', label: 'Contained' }, { value: 'ERADICATED', label: 'Eradicated' },
  { value: 'RECOVERED', label: 'Recovered' }, { value: 'CLOSED', label: 'Closed' },
];
// FIELDS now built inside the component via t()
const EMPTY: IncidentRequest = {
  title: '', titleAr: '', severity: 'MEDIUM', category: 'CYBER',
  status: 'OPEN', discoveryDate: '', assignee: '', description: '',
};

function incToReq(i: IncidentDto): IncidentRequest {
  return {
    title: i.title, titleAr: i.titleAr,
    severity: i.severity, category: i.category,
    status: i.status, discoveryDate: i.discoveryDate,
    assignee: i.assignee, description: i.description,
  };
}

export function IncidentsPage() {
  const { t } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('overview');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<IncidentDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<IncidentDto | null>(null);
  const [busy, setBusy] = useState(false);
  // Saudi regulatory-report templates state
  const [templatesOpen, setTemplatesOpen] = useState<{ incident: IncidentDto; templates: RegulatoryTemplate[] } | null>(null);

  const incidents = useApiList<IncidentDto>(() => incidentApi.list(0, 100), INCIDENTS);

  // ── NIST 800-61 phase + Saudi regulator workflow handlers ──
  async function advancePhase(r: IncidentDto) {
    try { await incidentApi.advancePhase(r.id); toast.success('Phase advanced'); incidents.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function closeIncidentAction(r: IncidentDto) {
    try { await incidentApi.closeIncident(r.id); toast.success('Incident closed'); incidents.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Close failed'); }
  }
  async function openTemplates(r: IncidentDto) {
    try {
      const templates = await incidentApi.regulatoryTemplates(r.id);
      setTemplatesOpen({ incident: r, templates });
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Could not load templates'); }
  }
  async function notifyRegulator(incidentId: number, regulator: SaudiRegulator) {
    try {
      await incidentApi.notifyRegulator(incidentId, regulator);
      toast.success(`${regulator.replace('_', ' ')} notification recorded`);
      // Refresh templates so the modal shows the updated notifiedAt
      const fresh = await incidentApi.regulatoryTemplates(incidentId);
      setTemplatesOpen(prev => prev ? { ...prev, templates: fresh } : null);
      incidents.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Notify failed'); }
  }

  const FIELDS: FieldDef[] = [
    { kind: 'text', key: 'title',         label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 200, span: 8 },
    { kind: 'select', key: 'severity',    label: t('fldSeverity') + ' *', options: SEVERITY_OPTIONS, required: true, span: 4 },
    { kind: 'text', key: 'titleAr',       label: t('fldTitleArLbl'), maxLength: 200, span: 12 },
    { kind: 'select', key: 'category',    label: t('fldCategory'), options: CATEGORY_OPTIONS, span: 4 },
    { kind: 'select', key: 'status',      label: t('fldStatusLbl'), options: STATUS_OPTIONS, span: 4 },
    { kind: 'date', key: 'discoveryDate', label: t('incidentsDetectedCol'), span: 4 },
    { kind: 'text', key: 'assignee',      label: t('cyberFldIncLead'), span: 12 },
    { kind: 'textarea', key: 'description', label: t('fldDescriptionLbl'), rows: 3, span: 12 },
  ];

  const tabs: TabItem<TabId>[] = [
    { id: 'overview', label: t('tabOverview') },
    { id: 'register', label: t('incidentsTabRegister') },
    { id: 'timeline', label: t('incidentsTabTimeline') },
    { id: 'lessons',  label: t('incidentsTabLessons') },
    { id: 'comms',    label: t('detailTabComms') },
  ];

  const cols: Column<IncidentDto>[] = [
    { header: t('incidentsTitleCol'),    cell: r => <span style={{ fontWeight: 600 }}>{r.title}</span> },
    { header: t('incidentsCategoryCol'), cell: r => <Badge tone="info">{r.category ?? '—'}</Badge> },
    { header: t('incidentsSeverityCol'), cell: r => <Badge tone={SEVERITY_TONE[r.severity] ?? 'neutral'} dot>{severityLabel(t, r.severity)}</Badge> },
    // NIST 800-61 phase column
    { header: t('incidentsNistCol'),     cell: r => <Badge tone="primary">{((r as IncidentDto & { nistPhase?: string }).nistPhase ?? 'IDENTIFICATION').replace(/_/g, ' ')}</Badge> },
    { header: t('colAssignee'),          cell: r => r.assignee ?? '—', mono: true },
    { header: t('incidentsDetectedCol'), cell: r => r.discoveryDate ?? '—', mono: true },
    { header: t('bcmStatusCol'),         cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(t, r.status)}</Badge> },
    { header: '', align: 'end', width: '230px',
      cell: r => {
        const phase = (r as IncidentDto & { nistPhase?: string }).nistPhase ?? 'IDENTIFICATION';
        const extras: Array<{ Icon: typeof ArrowRight; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (r.status !== 'CLOSED' && phase !== 'POST_INCIDENT') extras.push({ Icon: ArrowRight, onClick: () => advancePhase(r), title: t('incidentsActAdvancePhase'), tone: 'primary' });
        if (r.status !== 'CLOSED')                              extras.push({ Icon: Scroll,     onClick: () => openTemplates(r),  title: t('incidentsActSaudiTemplates'), tone: 'accent' });
        if (r.status !== 'CLOSED')                              extras.push({ Icon: Archive,    onClick: () => closeIncidentAction(r), title: t('incidentsActClose'),    tone: 'success' });
        return <RowActions extras={extras} onEdit={() => { setEditing(r); setOpen(true); }} onDelete={() => setConfirmDel(r)} />;
      } },
  ];

  const timelineEvents: TimelineEvent[] = useMemo(() => {
    const sorted = [...incidents.data].sort((a, b) => (b.discoveryDate ?? '').localeCompare(a.discoveryDate ?? ''));
    return sorted.slice(0, 12).map(i => {
      const tone: TimelineEvent['tone'] = i.severity === 'CRITICAL' ? 'danger'
                              : i.severity === 'HIGH' ? 'warning'
                              : i.status === 'CLOSED' ? 'success'
                              : 'primary';
      const Icon = i.severity === 'CRITICAL' ? Flame
                 : i.status === 'CLOSED' ? CheckCircle2
                 : i.status === 'CONTAINED' ? ShieldCheck
                 : i.status === 'RECOVERED' ? RefreshCw
                 : AlertTriangle;
      return {
        id: i.id,
        time: i.discoveryDate ?? '—',
        title: i.title,
        Icon, tone,
        body: <span><Badge tone="info">{i.category ?? '—'}</Badge> · status {statusLabel(t, i.status)} · {i.assignee ? `assignee ${i.assignee}` : 'unassigned'}</span>,
      };
    });
  }, [incidents.data, t]);

  const open_count = incidents.data.filter(i => i.status !== 'CLOSED' && i.status !== 'RECOVERED').length;
  const critical = incidents.data.filter(i => i.severity === 'CRITICAL' && i.status !== 'CLOSED').length;
  const closed = incidents.data.filter(i => i.status === 'CLOSED').length;

  async function onSubmit(values: IncidentRequest) {
    try {
      if (editing) await incidentApi.update(editing.id, values);
      else await incidentApi.create(values);
      toast.success(editing ? 'Incident updated' : t('toastRiskAdded'));
      setOpen(false); setEditing(null); incidents.reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : t('toastRiskFailed'));
    }
  }
  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await incidentApi.remove(confirmDel.id); toast.success('Incident deleted'); incidents.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }

  const lessons = incidents.data
    .filter(i => i.status === 'CLOSED' || i.status === 'RECOVERED')
    .slice(0, 5)
    .map(i => ({
      id: i.id, incident: i.title,
      lesson: i.severity === 'CRITICAL' ? 'Add to tabletop scenarios next quarter'
            : i.category === 'PRIVACY' ? 'Update DSR/breach playbook'
            : 'Tighten alerting thresholds',
      capa: `CAPA-${1000 + i.id}`,
    }));

  const isDemo = incidents.isDemo;

  return (
    <Shell title={t('incidentsTitleHeader')} sub={t('incidentsSub')}>
      <PageHeader
        eyebrow={t('incidentsEyebrow')} title={t('incidentsTitle')} sub={t('incidentsSub')}
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
        <Kpi Icon={AlertOctagon} label={t('incidentsKpiOpen')}     value={open_count} tone="warning" />
        <Kpi Icon={Flame}        label={t('incidentsKpiCritical')} value={critical}    tone="danger" />
        <Kpi Icon={Timer}        label={t('incidentsKpiMttr')}     value="4.2h" trend={{ dir: 'down', value: '−1.1h' }} tone="success" />
        <Kpi Icon={CheckCircle2} label={t('incidentsKpiClosed')}   value={closed} tone="primary" />
      </div>

      <OverviewStrip
        storageKey="overview.incidents"
        processBarTitle={t('incidentsProcessBarTitle')}
        processBarSubtitle={t('incidentsProcessBarSub')}
        lifecycles={INCIDENT_LIFECYCLES}
        postureTitle={t('incidentsPostureTitle')}
        anchors={INCIDENT_ANCHORS}
        lang="en" />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div className="dash-grid">
          <Card title={t('incidentsKpiOpen')} Icon={BarChart3} action={<Badge tone="primary">{t('incidentsCardBySeverity')}</Badge>}>
            <BarChart3D items={[
              { label: t('sevCritical'), value: incidents.data.filter(i => i.severity === 'CRITICAL').length, color: 'var(--danger)', shade: '#7E2A25' },
              { label: t('sevHigh'),     value: incidents.data.filter(i => i.severity === 'HIGH').length,     color: 'var(--cat-6)',  shade: '#5C3220' },
              { label: t('sevMedium'),   value: incidents.data.filter(i => i.severity === 'MEDIUM').length,   color: 'var(--cat-2)',  shade: '#7A5612' },
              { label: t('sevLow'),      value: incidents.data.filter(i => i.severity === 'LOW').length,      color: 'var(--cat-3)',  shade: '#324754' },
            ]} />
          </Card>
          <Card title={t('incidentsCardByCategory')} Icon={Activity}>
            <BarChart3D items={[
              { label: t('incidentsCatCyber'),    value: incidents.data.filter(i => i.category === 'CYBER').length,    color: 'var(--cat-1)', shade: '#062E2E' },
              { label: t('incidentsCatPrivacy'),  value: incidents.data.filter(i => i.category === 'PRIVACY').length,  color: 'var(--cat-4)', shade: '#522F45' },
              { label: t('incidentsCatOps'),      value: incidents.data.filter(i => i.category === 'OPS').length,      color: 'var(--cat-2)', shade: '#7A5612' },
              { label: t('incidentsCatPhysical'), value: incidents.data.filter(i => i.category === 'PHYSICAL').length, color: 'var(--cat-3)', shade: '#324754' },
            ]} />
          </Card>
        </div>
      )}
      {tab === 'register' && (
        <Card padded={false}>
          {incidents.loading ? <LoadingRow /> : <DataTable columns={cols} rows={incidents.data} empty={<EmptyState />} />}
        </Card>
      )}
      {tab === 'timeline' && (
        <Card title={t('incidentsCardTimeline')} Icon={Activity} action={<Badge tone="primary">{t('incidentsBadgeLast12')}</Badge>}>
          {timelineEvents.length === 0
            ? <EmptyState Icon={Activity} title={t('incidentsEmptyTitle')} sub={t('incidentsEmptySub')} />
            : <Timeline events={timelineEvents} />}
        </Card>
      )}
      {tab === 'lessons' && (
        <div className="dash-grid">
          <Card title={t('incidentsCardLessons')} Icon={Lightbulb} action={<Badge tone="primary">{lessons.length} {t('incidentsLessonsClosed')}</Badge>}>
            {lessons.length === 0
              ? <EmptyState Icon={Lightbulb} title={t('incidentsLessonsEmpty')} sub={t('incidentsLessonsEmptySub')} />
              : <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {lessons.map(l => (
                    <div key={l.id} style={{ padding: '10px 12px', background: 'var(--surface-2)', borderRadius: 'var(--r-md)', border: '1px solid var(--border)' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 4 }}>
                        <div style={{ fontWeight: 600, fontSize: 13 }}>{l.incident}</div>
                        <Badge tone="primary">{l.capa}</Badge>
                      </div>
                      <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>→ {l.lesson}</div>
                    </div>
                  ))}
                </div>}
          </Card>
          <Card title={t('incidentsCardCapa')} Icon={FileText}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, paddingTop: 4 }}>
              {[
                { theme: 'Phishing simulation cadence',  count: 7 },
                { theme: 'Access review automation',     count: 4 },
                { theme: 'Backup integrity tests',       count: 3 },
                { theme: 'Vendor incident coordination', count: 2 },
                { theme: 'Logging gap remediation',      count: 2 },
              ].map(it => (
                <div key={it.theme} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 10px', background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', fontSize: 13 }}>
                  <span>{it.theme}</span>
                  <Badge tone="primary">{it.count}</Badge>
                </div>
              ))}
            </div>
          </Card>
        </div>
      )}
      {tab === 'comms' && <CommsPanel module="INCIDENT" />}

      <FormModal<IncidentRequest>
        open={open}
        title={editing ? `${t('editPrefix')} · ${editing.title}` : t('newIncidentTitle')}
        fields={FIELDS}
        initial={editing ? incToReq(editing) : EMPTY}
        onClose={() => { setOpen(false); setEditing(null); }}
        onSubmit={onSubmit}
      />
      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title="Delete this incident?"
        message={confirmDel ? `Incident "${confirmDel.title}" will be removed. This cannot be undone.` : ''}
        confirmLabel="Delete"
        onCancel={() => setConfirmDel(null)} onConfirm={executeDelete}
      />

      {/* ── Saudi regulatory-report templates modal ── */}
      <Modal
        open={!!templatesOpen}
        title={templatesOpen ? `${t('incidentsTemplatesTitle')} · ${templatesOpen.incident.title}` : ''}
        size="lg"
        onClose={() => setTemplatesOpen(null)}
      >
        {templatesOpen && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <p style={{ fontSize: 13, color: 'var(--ink-2)', margin: 0 }}>
              {t('incidentsTemplatesSub')}
            </p>
            {templatesOpen.templates.map(tmpl => {
              const remaining = tmpl.hoursRemaining;
              const colour = tmpl.notifiedAt
                ? 'var(--success-fg)'
                : remaining < 0 ? 'var(--danger)'
                : remaining <= 6 ? 'var(--accent-700)'
                : 'var(--success-fg)';
              return (
                <div key={tmpl.code} style={{
                  border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
                  padding: 12, background: 'var(--surface-2)',
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 700, fontSize: 14 }}>{tmpl.titleEn}</div>
                      <div style={{ fontSize: 12, color: 'var(--ink-3)' }} dir="rtl">{tmpl.titleAr}</div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>{tmpl.applicabilityHint}</div>
                    </div>
                    <div style={{ minWidth: 180, textAlign: 'right' }}>
                      <div className="mono" style={{ color: colour, fontWeight: 700, fontSize: 13 }}>
                        {tmpl.notifiedAt
                          ? `Notified ${tmpl.notifiedAt.slice(0, 16).replace('T', ' ')}`
                          : remaining < 0 ? `${Math.abs(remaining)}h overdue`
                          : `${remaining}h remaining`}
                      </div>
                      <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{tmpl.deadlineHours}h deadline</div>
                    </div>
                  </div>
                  <pre style={{
                    fontSize: 11, fontFamily: 'var(--font-mono)', background: 'var(--surface)',
                    padding: 10, borderRadius: 'var(--r-sm)', marginTop: 8, marginBottom: 8,
                    whiteSpace: 'pre-wrap', maxHeight: 180, overflow: 'auto',
                  }}>{tmpl.prefilledBody}</pre>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{tmpl.submissionChannel}</div>
                    {!tmpl.notifiedAt && (
                      <Button variant="primary" size="sm" Icon={Send}
                        onClick={() => notifyRegulator(templatesOpen.incident.id, tmpl.code)}>
                        {t('incidentsActMarkNotified')}
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Modal>
    </Shell>
  );
}

function LoadingRow() {
  const { t } = useI18n();
  return <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>;
}
