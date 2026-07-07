import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  AlertOctagon, Flame, Timer, CheckCircle2, Filter, Download, Plus, BarChart3, Lightbulb,
  Activity, FileText, AlertTriangle, ShieldCheck, RefreshCw, ArrowRight, Send, Archive, Scroll, FolderOpen,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { RaiseRequestButton } from '../../components/workflow/RaiseRequestButton';
import { ModuleEngineRequestsPanel } from '../../components/workflows/ModuleEngineRequestsPanel';
import { WorkflowQuickLaunch } from '../../components/workflows/WorkflowQuickLaunch';
import { ModuleSubmissionsPanel } from '../../components/workflows/ModuleSubmissionsPanel';
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
import { EntityDocsModal } from '../../components/ui/EntityDocsModal';
import { useI18n } from '../../lib/i18n';
import { STATUS_TONE, SEVERITY_TONE, statusLabel, severityLabel } from '../../lib/labels';
import { useApiList } from '../../lib/useApiResource';
import { useLiveRefresh } from '../../lib/useLiveRefresh';
import { LivePulse } from '../../components/ui/LivePulse';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';
import { FilterModal, distinctValues, type FilterField } from '../../components/ui/FilterModal';
import { incidentApi, ApiError, type IncidentDto, type IncidentRequest, type RegulatoryTemplate, type SaudiRegulator } from '../../lib/api';
import { Modal } from '../../components/ui/Modal';
import { useToast } from '../../lib/toast';
import { INCIDENTS } from './mockIncidents';
import { usePromoted } from '../../lib/promotedRecords';
import { toIncidentDto, usePromotedProvenance } from '../../lib/promotedAdapters';
import { PromotedBadge } from '../../components/intake/PromotedBadge';
import { INCIDENT_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { INCIDENT_ANCHORS } from '../../lib/moduleAnchors';
import { CommsPanel } from '../../components/comms/CommsPanel';

type TabId = 'overview' | 'register' | 'timeline' | 'lessons' | 'comms';

const SEVERITY_OPTIONS = [
  { value: 'CRITICAL', label: 'Critical' }, { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' }, { value: 'LOW', label: 'Low' },
];
// Incident category taxonomy — Saudi-aligned. Covers what a SAMA/NCA-
// regulated entity is required to triage:
//   • Technical:    cyber, privacy, infrastructure, application, network
//   • Operational:  ops, physical, third-party, supply-chain, business-continuity
//   • People:       safety, insider-threat, hr (workplace violation)
//   • Compliance:   regulatory breach, AML/sanctions, financial-fraud, ethics
// Each row carries an explicit Arabic label so RTL renders natively, not as
// a transliterated value. Use the value-side for stable enum identifiers.
const CATEGORY_OPTIONS = [
  // Technical
  { value: 'CYBER',                label: 'Cyber',                labelAr: 'الأمن السيبراني' },
  { value: 'PRIVACY',              label: 'Privacy',              labelAr: 'الخصوصية' },
  { value: 'INFRASTRUCTURE',       label: 'Infrastructure',       labelAr: 'البنية التحتية' },
  { value: 'APPLICATION',          label: 'Application',          labelAr: 'تطبيق' },
  { value: 'NETWORK',              label: 'Network',              labelAr: 'الشبكة' },
  { value: 'CLOUD',                label: 'Cloud',                labelAr: 'السحابة' },
  { value: 'DATA_LOSS',            label: 'Data loss / leak',     labelAr: 'فقد أو تسرّب بيانات' },
  // Operational
  { value: 'OPS',                  label: 'Operations',           labelAr: 'العمليات' },
  { value: 'PHYSICAL',             label: 'Physical',             labelAr: 'مادي / منشآت' },
  { value: 'THIRD_PARTY',          label: 'Third party',          labelAr: 'طرف ثالث' },
  { value: 'SUPPLY_CHAIN',         label: 'Supply chain',         labelAr: 'سلسلة الإمداد' },
  { value: 'BUSINESS_CONTINUITY',  label: 'Business continuity',  labelAr: 'استمرارية الأعمال' },
  { value: 'DR_FAILOVER',          label: 'DR / failover',        labelAr: 'التعافي من الكوارث' },
  // People
  { value: 'SAFETY',               label: 'Health & safety',      labelAr: 'الصحة والسلامة' },
  { value: 'INSIDER_THREAT',       label: 'Insider threat',       labelAr: 'تهديد داخلي' },
  { value: 'HR_VIOLATION',         label: 'HR / workplace',       labelAr: 'مخالفة موارد بشرية' },
  // Compliance / regulatory
  { value: 'COMPLIANCE',           label: 'Compliance',           labelAr: 'الالتزام' },
  { value: 'REGULATORY_BREACH',    label: 'Regulatory breach',    labelAr: 'مخالفة تنظيمية' },
  { value: 'AML_SANCTIONS',        label: 'AML / sanctions',      labelAr: 'غسل أموال / عقوبات' },
  { value: 'FINANCIAL_FRAUD',      label: 'Financial fraud',      labelAr: 'احتيال مالي' },
  { value: 'ETHICS',               label: 'Ethics / misconduct',  labelAr: 'مخالفة سلوكية' },
  // Other / catch-all
  { value: 'OTHER',                label: 'Other',                labelAr: 'أخرى' },
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
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();

  // Tab is URL-driven so deep-links like /incidents?tab=lessons land on the
  // right tab and stay shareable. Manual tab clicks update the URL via
  // setTab() below.
  const VALID_TABS: TabId[] = ['overview', 'register', 'timeline', 'lessons', 'comms'];
  const urlTab = params.get('tab') as TabId | null;
  const tab: TabId = (urlTab && VALID_TABS.includes(urlTab)) ? urlTab : 'overview';
  function setTab(next: TabId) {
    const updated = new URLSearchParams(params);
    if (next === 'overview') updated.delete('tab');
    else updated.set('tab', next);
    setParams(updated, { replace: true });
  }
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<IncidentDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<IncidentDto | null>(null);
  const [docsTarget, setDocsTarget] = useState<IncidentDto | null>(null);
  const [busy, setBusy] = useState(false);
  // Saudi regulatory-report templates state
  const [templatesOpen, setTemplatesOpen] = useState<{ incident: IncidentDto; templates: RegulatoryTemplate[] } | null>(null);

  const incidents = useApiList<IncidentDto>(() => incidentApi.list(0, 100), INCIDENTS);

  // DD-71: merge intake-promoted rows into the visible register so a
  // triager who hits "Promote" on a security-event intake actually sees
  // their new incident appear here. Promoted rows are display-only —
  // edit / delete still operate on incidents.data.
  const promotedRows = usePromoted('INCIDENT').map(toIncidentDto);
  const allIncidents = useMemo(() => [...promotedRows, ...incidents.data], [promotedRows, incidents.data]);
  const provenance = usePromotedProvenance('INCIDENT');

  // ── DD-47: filter modal state + predicate ─────────────────────────
  const [filterOpen, setFilterOpen] = useState(false);
  const [advFilters, setAdvFilters] = useState<Record<string, string | undefined>>({});
  const filteredIncidents = useMemo(() => allIncidents.filter(i => {
    if (advFilters.severity && i.severity !== advFilters.severity) return false;
    if (advFilters.status   && i.status !== advFilters.status) return false;
    if (advFilters.category && i.category !== advFilters.category) return false;
    if (advFilters.assignee && !(i.assignee ?? '').toLowerCase().includes(advFilters.assignee.toLowerCase())) return false;
    if (advFilters.discoveredAfter  && (i.discoveryDate ?? '') < advFilters.discoveredAfter) return false;
    if (advFilters.discoveredBefore && (i.discoveryDate ?? '') > advFilters.discoveredBefore) return false;
    return true;
  }), [allIncidents, advFilters]);
  const incidentFilterFields: FilterField[] = [
    { key: 'severity', label: t('filterFieldSeverity'), kind: 'select',
      options: [{value:'LOW',label:'LOW'},{value:'MEDIUM',label:'MEDIUM'},{value:'HIGH',label:'HIGH'},{value:'CRITICAL',label:'CRITICAL'}] },
    { key: 'status',   label: t('filterFieldStatus'),   kind: 'select', options: distinctValues(incidents.data, 'status') },
    { key: 'category', label: t('filterFieldCategory'), kind: 'select', options: distinctValues(incidents.data, 'category') },
    { key: 'assignee', label: t('filterFieldAssignee'), kind: 'text', placeholder: 'e.g. soc.lead@tss.sa' },
    { key: 'discoveredAfter',  label: t('filterFieldDiscoveredAfter'),  kind: 'date' },
    { key: 'discoveredBefore', label: t('filterFieldDiscoveredBefore'), kind: 'date' },
  ];

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

  // Sprint 4 follow-up — deep-link from the module-lifecycle dashboard CTA
  // ("قوالب الجهات السعودية" / "Saudi templates") opens the templates modal
  // automatically for the most-critical OPEN incident. Falls back gracefully
  // when no candidate exists.
  useEffect(() => {
    if (params.get('regulator') !== '1') return;
    if (incidents.loading || allIncidents.length === 0) return;
    const SEV_RANK: Record<string, number> = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
    const candidate = [...allIncidents]
      .filter(i => i.status !== 'CLOSED' && i.status !== 'RESOLVED')
      .sort((a, b) => (SEV_RANK[b.severity ?? ''] ?? 0) - (SEV_RANK[a.severity ?? ''] ?? 0))
      [0];
    if (!candidate) {
      toast.info(t('incidentsRegulatorNoOpen'));
    } else {
      openTemplates(candidate);
    }
    // Strip the param so reloading doesn't re-trigger.
    const next = new URLSearchParams(params);
    next.delete('regulator');
    setParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params, incidents.loading, allIncidents.length]);
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

  // Pick AR or EN labels for the dropdown depending on UI locale. The enum
  // value stays the same regardless of locale (it's the wire format).
  const categoryOptionsLocalized = CATEGORY_OPTIONS.map(c => ({
    value: c.value,
    label: lang === 'ar' ? c.labelAr : c.label,
  }));
  const FIELDS: FieldDef[] = [
    { kind: 'text', key: 'title',         label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 200, span: 8 },
    { kind: 'select', key: 'severity',    label: t('fldSeverity') + ' *', options: SEVERITY_OPTIONS, required: true, span: 4 },
    { kind: 'text', key: 'titleAr',       label: t('fldTitleArLbl'), maxLength: 200, span: 12 },
    // Switched to select-searchable: 22 categories is long enough to want
    // typeahead; users who think in their own term ("data leak", "AML",
    // "insider") find the right enum without scanning the list.
    { kind: 'select-searchable', key: 'category', label: t('fldCategory'),
      options: categoryOptionsLocalized, span: 4,
      placeholder: lang === 'ar' ? 'ابحث عن فئة…' : 'Search category…' },
    { kind: 'select', key: 'status',      label: t('fldStatusLbl'), options: STATUS_OPTIONS, span: 4 },
    { kind: 'date', key: 'discoveryDate', label: t('incidentsDetectedCol'), span: 4 },
    { kind: 'text', key: 'assignee',      label: t('cyberFldIncLead'), span: 12 },
    { kind: 'textarea', key: 'description', label: t('fldDescriptionLbl'), rows: 3, span: 12 },
  ];

  // Form for "Add lesson learned" — picks an incident, captures lesson text,
  // optional CAPA reference. Lessons are not their own entity; they live as a
  // satellite annotation on incidents.
  //
  // Source-incident dropdown:
  //   • Includes the full incident pool, not just CLOSED/RECOVERED — lessons
  //     can legitimately be drafted while an incident is still in CONTAINED
  //     or INVESTIGATING (post-mortem early notes). Each row carries a status
  //     subtitle so the user knows what they're picking.
  //   • Uses select-searchable so a typeahead works for long pools (>20).
  //   • onCreate spawns a quick incident inline if the lesson refers to
  //     something the platform doesn't track yet (e.g. near-miss noticed
  //     during a tabletop). The created incident lands in DETECTED status
  //     and the new id flows back into the field automatically.
  const lessonIncidentOptions = allIncidents.map(i => ({
    value: String(i.id),
    label: i.title,
    sub: `${i.id ? `INC-${String(i.id).padStart(4, '0')} · ` : ''}${statusLabel(t, i.status)} · ${severityLabel(t, i.severity)}`,
  }));
  async function quickCreateIncident(typedTitle: string): Promise<string | null> {
    try {
      const body: IncidentRequest = {
        title: typedTitle,
        severity: 'MEDIUM',
        category: 'OPS',
        status: 'DETECTED',
        description: lang === 'ar'
          ? 'تمّ إنشاء الحادثة من نموذج الدرس المستفاد. حدّث التفاصيل لاحقاً.'
          : 'Created inline from the Lessons-Learned form. Update the details later.',
      } as IncidentRequest;
      const created = await incidentApi.create(body);
      toast.success(lang === 'ar' ? 'تمّ إنشاء الحادثة وتم اختيارها' : 'Incident created and selected');
      incidents.reload();
      return String(created.id);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (lang === 'ar' ? 'تعذّر الإنشاء' : 'Create failed'));
      return null;
    }
  }
  const LESSON_FIELDS: FieldDef[] = [
    { kind: 'select-searchable', key: 'incidentId',
      label: t('incidentsLessonFldIncident') + ' *',
      required: true,
      options: lessonIncidentOptions,
      placeholder: lang === 'ar' ? 'ابحث عن حادثة أو أنشئ جديدة…' : 'Search incidents or create new…',
      emptyHint: lang === 'ar'
        ? 'لا توجد حوادث بعد — استخدم زرّ "إنشاء" أعلاه.'
        : 'No incidents yet — use the Create option above.',
      onCreate: quickCreateIncident,
      createLabel: (typed) => lang === 'ar' ? `+ إنشاء حادثة جديدة: "${typed}"` : `+ Create new incident: "${typed}"`,
      span: 12 },
    { kind: 'textarea', key: 'lesson', label: t('incidentsLessonFldLesson') + ' *', rows: 3, span: 12 },
    { kind: 'text', key: 'capa', label: t('incidentsLessonFldCapa'), placeholder: 'CAPA-1234', span: 12 },
  ];
  function onAddLesson(values: Record<string, string>) {
    const incidentId = values.incidentId;
    const lessonText = String(values.lesson ?? '').trim();
    const incident = allIncidents.find(i => String(i.id) === incidentId);
    if (!incident || !lessonText) {
      toast.danger(t('toastValidationFailed') ?? 'Required fields missing');
      return;
    }
    const newRow: LessonRow = {
      id: `user-${Date.now()}`,
      incident: incident.title,
      lesson: lessonText,
      capa: String(values.capa ?? '').trim() || `CAPA-${1000 + incident.id}`,
      userAdded: true,
    };
    persistUserLessons([newRow, ...userLessons]);
    setAddLessonOpen(false);
    toast.success(t('incidentsLessonAddedToast'));
  }

  const tabs: TabItem<TabId>[] = [
    { id: 'overview', label: t('tabOverview') },
    { id: 'register', label: t('incidentsTabRegister') },
    { id: 'timeline', label: t('incidentsTabTimeline') },
    { id: 'lessons',  label: t('incidentsTabLessons') },
    { id: 'comms',    label: t('detailTabComms') },
  ];

  const cols: Column<IncidentDto>[] = [
    { header: t('incidentsTitleCol'),    cell: r => <span style={{ fontWeight: 600 }}>{r.title}{provenance.has(r.id) && <PromotedBadge sourceReferenceCode={provenance.get(r.id)!} />}</span> },
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
        extras.push({ Icon: FolderOpen, onClick: () => setDocsTarget(r), title: lang === 'ar' ? 'المستندات والضوابط' : 'Docs & controls' });
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

  const open_count = allIncidents.filter(i => i.status !== 'CLOSED' && i.status !== 'RECOVERED').length;
  const critical = allIncidents.filter(i => i.severity === 'CRITICAL' && i.status !== 'CLOSED').length;
  const closed = allIncidents.filter(i => i.status === 'CLOSED').length;

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

  // ── Lessons learned ─────────────────────────────────────────────────
  // Two sources: (a) auto-derived from closed/recovered incidents (the
  // historical demo shape), (b) user-authored entries persisted to
  // localStorage. The "Add lesson" CTA on the Lessons tab opens a
  // FormModal that writes into (b); both are rendered together.
  type LessonRow = { id: string | number; incident: string; lesson: string; capa: string; userAdded?: boolean };
  const LESSON_STORE_KEY = 'tss.incidents.lessonsLearned';
  const [userLessons, setUserLessons] = useState<LessonRow[]>(() => {
    try { return JSON.parse(localStorage.getItem(LESSON_STORE_KEY) ?? '[]') as LessonRow[]; }
    catch { return []; }
  });
  function persistUserLessons(next: LessonRow[]) {
    setUserLessons(next);
    try { localStorage.setItem(LESSON_STORE_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  }
  const [addLessonOpen, setAddLessonOpen] = useState(false);

  const derivedLessons = incidents.data
    .filter(i => i.status === 'CLOSED' || i.status === 'RECOVERED')
    .slice(0, 5)
    .map(i => ({
      id: i.id, incident: i.title,
      lesson: i.severity === 'CRITICAL' ? 'Add to tabletop scenarios next quarter'
            : i.category === 'PRIVACY' ? 'Update DSR/breach playbook'
            : 'Tighten alerting thresholds',
      capa: `CAPA-${1000 + i.id}`,
    }));
  const lessons: LessonRow[] = [...userLessons, ...derivedLessons];

  const isDemo = incidents.isDemo;

  // Sprint 4 §2.7 — live incidents (NIST 800-61r2 phase machine + regulator notify)
  const { pulse } = useLiveRefresh({
    kinds: ['changed.incident', 'changed.cyber_incident'],
    onRefresh: () => { incidents.reload(); },
    waitMs: 600,
    enabled: !isDemo,
  });

  return (
    <Shell title={t('incidentsTitleHeader')} sub={t('incidentsSub')}>
      <PageHeader
        eyebrow={t('incidentsEyebrow')} title={t('incidentsTitle')} sub={t('incidentsSub')}
        actions={
          <>
            <RaiseRequestButton sourceModule="INCIDENT" />
            {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
            {!isDemo && <LivePulse pulse={pulse} />}
            <Button variant="secondary" Icon={Filter} onClick={() => setFilterOpen(true)}>
              {t('actionFilter')}{Object.values(advFilters).filter(Boolean).length > 0 ? ` · ${Object.values(advFilters).filter(Boolean).length}` : ''}
            </Button>
            <Button variant="secondary" Icon={Download} onClick={() => {
              downloadCsv(filteredIncidents as unknown as ExportRow[], stampFilename('incidents-register', 'csv'));
              toast.success(t('exportCsvDone'));
            }}>{t('actionExport')}</Button>
            {/* DD-76: route every new submission through the Services Center intake form
                so it gets a reference code, SLA, audit trail, and triage routing. */}
            <Button variant="primary"   Icon={Plus} onClick={() => navigate('/intake/security_incident')}>{t('actionNew')}</Button>
          </>
        }
      />
      <WorkflowQuickLaunch modules="INCIDENT" />
      <ModuleSubmissionsPanel module="INCIDENT" />
      <ModuleEngineRequestsPanel sourceModule="INCIDENT" />
      <div className="kpi-grid">
        <Kpi Icon={AlertOctagon} label={t('incidentsKpiOpen')}     value={open_count} tone="warning"
          onClick={() => setTab('register')} active={tab === 'register'} />
        <Kpi Icon={Flame}        label={t('incidentsKpiCritical')} value={critical}    tone="danger"
          onClick={() => setAdvFilters({ severity: 'CRITICAL' })} active={advFilters.severity === 'CRITICAL'} />
        <Kpi Icon={Timer}        label={t('incidentsKpiMttr')}     value="4.2h" trend={{ dir: 'down', value: '−1.1h' }} tone="success"
          to="/incidents/rca" />
        <Kpi Icon={CheckCircle2} label={t('incidentsKpiClosed')}   value={closed} tone="primary"
          onClick={() => setAdvFilters({ status: 'CLOSED' })} active={advFilters.status === 'CLOSED'} />
      </div>

      <OverviewStrip
        storageKey="overview.incidents"
        processBarTitle={t('incidentsProcessBarTitle')}
        processBarSubtitle={t('incidentsProcessBarSub')}
        lifecycles={INCIDENT_LIFECYCLES}
        postureTitle={t('incidentsPostureTitle')}
        anchors={INCIDENT_ANCHORS}
        />

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
          {incidents.loading ? <LoadingRow /> : <DataTable columns={cols} rows={filteredIncidents} empty={<EmptyState />} />}
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
          <Card title={t('incidentsCardLessons')} Icon={Lightbulb}
            action={
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <Badge tone="primary">{lessons.length} {t('incidentsLessonsClosed')}</Badge>
                <Button size="sm" variant="primary" Icon={Plus} onClick={() => setAddLessonOpen(true)}>
                  {t('incidentsLessonAddBtn')}
                </Button>
              </div>
            }>
            {lessons.length === 0
              ? <EmptyState Icon={Lightbulb} title={t('incidentsLessonsEmpty')} sub={t('incidentsLessonsEmptySub')} />
              : <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {lessons.map(l => (
                    <div key={l.id} style={{ padding: '10px 12px', background: 'var(--surface-2)', borderRadius: 'var(--r-md)', border: '1px solid var(--border)' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 4 }}>
                        <div style={{ fontWeight: 600, fontSize: 13 }}>
                          {l.incident}
                          {l.userAdded && <span style={{ marginInlineStart: 6 }}><Badge tone="success">{t('incidentsLessonUserBadge')}</Badge></span>}
                        </div>
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
      <FormModal<Record<string, string>>
        open={addLessonOpen}
        title={t('incidentsLessonAddTitle')}
        fields={LESSON_FIELDS}
        initial={{ incidentId: '', lesson: '', capa: '' }}
        onClose={() => setAddLessonOpen(false)}
        onSubmit={onAddLesson}
      />
      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title="Delete this incident?"
        message={confirmDel ? `Incident "${confirmDel.title}" will be removed. This cannot be undone.` : ''}
        confirmLabel="Delete"
        onCancel={() => setConfirmDel(null)} onConfirm={executeDelete}
      />

      <EntityDocsModal
        entityType="INCIDENT"
        entityId={docsTarget?.id ?? null}
        open={!!docsTarget}
        recordLabel={docsTarget ? `INC-${String(docsTarget.id).padStart(4, '0')}` : undefined}
        onClose={() => setDocsTarget(null)}
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
      <FilterModal open={filterOpen} fields={incidentFilterFields} value={advFilters}
        onApply={v => { setAdvFilters(v); toast.success(t('filterModalApplied')); }}
        onClose={() => setFilterOpen(false)} />
    </Shell>
  );
}

function LoadingRow() {
  const { t } = useI18n();
  return <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>;
}
