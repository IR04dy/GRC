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
import { isIncidentOpen } from '../../lib/incidentStatus';
import { useApiList } from '../../lib/useApiResource';
import { useLiveRefresh } from '../../lib/useLiveRefresh';
import { LivePulse } from '../../components/ui/LivePulse';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';
import { FilterModal, distinctValues, type FilterField } from '../../components/ui/FilterModal';
import { incidentApi, ApiError, type IncidentDto, type IncidentRequest, type IncidentStatsDto, type RegulatoryTemplate, type SaudiRegulator } from '../../lib/api';
import { Modal } from '../../components/ui/Modal';
import { useToast } from '../../lib/toast';
import { INCIDENTS } from './mockIncidents';
import { usePromotedProvenance } from '../../lib/intakeProvenance';
import { PromotedBadge } from '../../components/intake/PromotedBadge';
import { PdplApplicabilityBadge } from '../../components/privacy/PdplApplicabilityBadge';
import { INCIDENT_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { INCIDENT_ANCHORS } from '../../lib/moduleAnchors';
import { CommsPanel } from '../../components/comms/CommsPanel';
import { useCapas } from '../../lib/capa';
import { hasAnyRole } from '../../lib/auth';

type TabId = 'overview' | 'register' | 'timeline' | 'lessons' | 'comms';

/**
 * F2-212 — the corrective actions actually raised from an incident, replacing a
 * fabricated "CAPA Themes (90d)" list of five inline hard-coded themes.
 *
 * It lives in its own component so the hook only mounts for someone entitled to the
 * register: **11 of the 29 roles holding INCIDENT:READ do not hold CAPA:READ**
 * (measured in dev and prod), and they include `IR_LEAD`, `FORENSICS_LEAD` and
 * `REGULATOR_VIEWER` — the people most likely to be reading this page. Calling
 * `useCapas()` unconditionally would fire a pointless 403 for all of them and render
 * a raw server error where a courtesy panel belongs.
 *
 * 🔑 And the state that matters is the one this whole programme keeps finding: an
 * empty list must not be able to mean two things. "You cannot see this register" and
 * "no corrective action has been raised" are different facts, so they say different
 * things — a missing permission may not read as a clean bill.
 */
function CapaFromIncidents({ t, lang }: { t: (k: string, fb?: string) => string; lang: 'en' | 'ar' }) {
  const navigate = useNavigate();
  const mayRead = hasAnyRole('CAPA_READ', 'PLATFORM_ADMIN');
  return mayRead
    ? <CapaFromIncidentsList t={t} lang={lang} navigate={navigate} />
    : <Card title={t('incidentsCardCapa')} Icon={FileText}>
        <EmptyState Icon={FileText} title={t('incidentsCapaNoAccess')} sub={t('incidentsCapaNoAccessSub')} />
      </Card>;
}

function CapaFromIncidentsList({ t, lang, navigate }: {
  t: (k: string, fb?: string) => string; lang: 'en' | 'ar'; navigate: (to: string) => void;
}) {
  const capas = useCapas();
  // `origin_type` is stored lower-case (`incident`) and the column is
  // case-INSENSITIVE, so this normalises rather than matching a literal (F2-179).
  const items = useMemo(
    () => capas.items.filter(c => c.originType?.trim().toLowerCase() === 'incident'),
    [capas.items]);
  return (
    <Card title={t('incidentsCardCapa')} Icon={FileText}>
      {capas.error
        ? <EmptyState Icon={FileText} title={t('incidentsCapaFromIncidentsError')} sub={capas.error} />
        : items.length === 0
          ? <EmptyState Icon={FileText} title={t('incidentsCapaFromIncidentsNone')}
              sub={t('incidentsCapaFromIncidentsNoneSub')} />
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 8, paddingTop: 4 }}>
              {items.map(c => (
                <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, padding: '8px 10px', background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', fontSize: 13 }}>
                  <span>
                    <span style={{ fontWeight: 600 }}>{lang === 'ar' ? c.titleAr || c.titleEn : c.titleEn}</span>
                    <span style={{ color: 'var(--ink-3)', marginInlineStart: 6 }}>{c.id}</span>
                  </span>
                  <Badge tone={STATUS_TONE[c.status] ?? 'neutral'}>{statusLabel(t, c.status)}</Badge>
                </div>
              ))}
            </div>}
      <div style={{ paddingTop: 10 }}>
        <Button size="sm" variant="ghost" Icon={ArrowRight} onClick={() => navigate('/capa')}>
          {t('incidentsCapaOpenRegister')}
        </Button>
      </div>
    </Card>
  );
}

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

// ⚠⚠ F2-212 — DATA LOSS, not just a display gap. `PUT /api/v1/incidents/{id}` is a
// FULL REPLACE and `IncidentService.update` calls `setLessonsLearned(req.lessonsLearned())`
// (and the same for `lessonsLearnedAr`, `resolutionDate`, `timelineJson`) unconditionally.
// This function omitted all four, so every field it left out arrived null and was written
// null: editing an incident through the register's own form ERASED its post-incident
// lesson, its resolution date and its response timeline, silently and irreversibly.
//
// Nothing on the page could show those fields — `IncidentDto` did not declare them — so
// the loss was invisible from both ends: the operator never saw the value before the edit
// and never saw it disappear after. Round-tripping every field the request accepts is the
// fix; the gate asserts the two types stay in step.
function incToReq(i: IncidentDto): IncidentRequest {
  return {
    title: i.title, titleAr: i.titleAr,
    severity: i.severity, category: i.category,
    status: i.status, discoveryDate: i.discoveryDate,
    assignee: i.assignee, description: i.description,
    resolutionDate: i.resolutionDate, timelineJson: i.timelineJson,
    lessonsLearned: i.lessonsLearned, lessonsLearnedAr: i.lessonsLearnedAr,
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

  // F2-195 — mean time to resolve, from the server. The KPI below used to read `"4.2h"` with a
  // `−1.1h` trend, both invented: `/incidents/stats` already returned `avgDays` (mean days between
  // discoveryDate and resolutionDate) and measured **4.5 days** in dev, so the tile understated
  // incident response by 26× *and* in the wrong unit. Null while loading or on failure, which the
  // Kpi tile renders as an em-dash rather than a zero (UX-008) — "not measured yet" is the truth
  // during a fetch, and a "0h" MTTR would be a better-looking lie than the one being removed.
  const [incidentStats, setIncidentStats] = useState<IncidentStatsDto | null>(null);
  useEffect(() => {
    let live = true;
    incidentApi.stats()
      .then(s => { if (live) setIncidentStats(s); })
      .catch(() => { if (live) setIncidentStats(null); });
    return () => { live = false; };
  }, []);

  // DD-71: merge intake-promoted rows into the visible register so a
  // triager who hits "Promote" on a security-event intake actually sees
  // their new incident appear here. Promoted rows are display-only —
  // edit / delete still operate on incidents.data.
  const allIncidents = incidents.data;
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
      .filter(i => isIncidentOpen(i.status))   // F2-360: same predicate as the KPIs (was already {CLOSED,RESOLVED})
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
    // F2-212: a free-text CAPA field was here, defaulting to a minted `CAPA-<1000+id>`.
    // `cyber.incidents` has no CAPA column, so whatever was typed went nowhere, and the
    // default matched no reference the CAPA register has ever issued. Linking an
    // incident to a corrective action is a real capability (`capa.tasks`) and it is not
    // this text box — inventing the citation is worse than not offering one.
  ];
  // F2-212: writes the lesson to `cyber.incidents.lessons_learned(_ar)` through the
  // server, which has accepted it all along. It used to append a row to localStorage
  // and mint a CAPA reference nothing had issued. The record is replaced from the
  // server's response rather than patched locally, so the screen cannot disagree with
  // what was stored (F2-201/F2-203's rule).
  async function onAddLesson(values: Record<string, string>) {
    const lessonText = String(values.lesson ?? '').trim();
    const incident = allIncidents.find(i => String(i.id) === values.incidentId);
    if (!incident || !lessonText) {
      toast.danger(t('toastValidationFailed') ?? 'Required fields missing');
      return;
    }
    setBusy(true);
    try {
      // PUT is a full replace, so start from the record as it stands and change the
      // one field. Arabic is the primary language (house rule 7) and the column pair
      // exists, so the lesson lands in the column matching the authoring language.
      const body: IncidentRequest = {
        ...incToReq(incident),
        ...(lang === 'ar' ? { lessonsLearnedAr: lessonText } : { lessonsLearned: lessonText }),
      };
      await incidentApi.update(incident.id, body);
      incidents.reload();
      setAddLessonOpen(false);
      toast.success(t('incidentsLessonAddedToast'));
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Save failed');
    } finally { setBusy(false); }
  }

  const tabs: TabItem<TabId>[] = [
    { id: 'overview', label: t('tabOverview') },
    { id: 'register', label: t('incidentsTabRegister') },
    { id: 'timeline', label: t('incidentsTabTimeline') },
    { id: 'lessons',  label: t('incidentsTabLessons') },
    { id: 'comms',    label: t('detailTabComms') },
  ];

  const cols: Column<IncidentDto>[] = [
    { header: t('incidentsTitleCol'),    cell: r => <span style={{ fontWeight: 600 }}>{r.title}{provenance.has(String(r.id)) && <PromotedBadge sourceReferenceCode={provenance.get(String(r.id))!} />}</span> },
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
        // F2-171: promoted-intake rows are merged in above and exist only in this browser.
        return <RowActions disabled={isDemo} extras={extras} onEdit={() => { setEditing(r); setOpen(true); }} onDelete={() => setConfirmDel(r)} />;
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

  // F2-360 — "open" = complement of {RESOLVED, CLOSED} (isIncidentOpen), not a hand-typed exclusion; the old open_count leaked the 10/44 RESOLVED rows in via the phantom RECOVERED (lib/incidentStatus.ts).
  const open_count = allIncidents.filter(i => isIncidentOpen(i.status)).length;
  const critical = allIncidents.filter(i => i.severity === 'CRITICAL' && isIncidentOpen(i.status)).length;
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

  // ── Lessons learned (NIST 800-61r2 §3.4 post-incident activity) ─────
  //
  // ⚠⚠ F2-212: this register used to have TWO sources and neither was the server.
  //
  //  (a) `derivedLessons` INVENTED the lesson text from the severity band —
  //      `severity === 'CRITICAL' ? 'Add to tabletop scenarios next quarter' :
  //       category === 'PRIVACY' ? 'Update DSR/breach playbook' :
  //       'Tighten alerting thresholds'` — which is F2-209's defect exactly: a
  //      statement about what an organisation concluded, asserted because a field
  //      crossed a threshold. It also minted `capa: 'CAPA-' + (1000 + i.id)`; real
  //      references look like `CAPA-2026-19E164FC4A7-00`, so the citation resolved
  //      to nothing (F2-207's client-side minter population).
  //
  //  (b) operator-authored entries went to `localStorage` under
  //      `tss.incidents.lessonsLearned` — one browser, invisible to everyone else,
  //      while `cyber.incidents.lessons_learned` / `_ar` already existed, were
  //      already returned by `IncidentResponse`, and were already accepted for
  //      WRITE by the server's `IncidentRequest`. The client type simply never
  //      declared the fields, so nothing could read or send them.
  //
  // ⚠ And the filter inverted the register. It admitted `CLOSED || RECOVERED`:
  // `RECOVERED` appears in ZERO rows (a token the vocabulary does not contain),
  // `RESOLVED` — 10 of 44 — was missing, and all three incidents that actually
  // carry a recorded lesson are INVESTIGATING/OPEN/OPEN. Measured in dev and prod:
  // **0 incidents with a real lesson were admitted**, so every lesson displayed was
  // invented and every real one was hidden. F2-196's rule — an inclusion list over
  // an open vocabulary silently drops whatever nobody remembered — so the register
  // is now simply "every incident that has a lesson recorded", with no status
  // predicate at all: a lesson exists because someone wrote one, not because the
  // incident reached a state this page happens to list.
  type LessonRow = { id: number; incident: string; lesson: string };
  const [addLessonOpen, setAddLessonOpen] = useState(false);

  const lessons: LessonRow[] = incidents.data
    .filter(i => (lang === 'ar' ? i.lessonsLearnedAr || i.lessonsLearned : i.lessonsLearned))
    .map(i => ({
      id: i.id,
      incident: i.title,
      lesson: (lang === 'ar' ? i.lessonsLearnedAr || i.lessonsLearned : i.lessonsLearned) ?? '',
    }));

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
        <Kpi Icon={Timer}        label={t('incidentsKpiMttr')}
          value={incidentStats ? `${incidentStats.avgDays.toFixed(1)}${t('incidentsKpiMttrDayUnit')}` : null}
          emptyHint={t('incidentsKpiMttrEmpty')} tone="success"
          to="/incidents/rca" />
        {/* F2-195 — no `trend` prop. The value is now real, but a trend is a SECOND claim: it
            asserts a prior-period figure, and nothing in the platform stores an MTTR time series.
            Wiring the value and keeping the trend would have left the half of the fabrication that
            implies history exists. */}
        <Kpi Icon={CheckCircle2} label={t('incidentsKpiClosed')}   value={closed} tone="primary"
          onClick={() => setAdvFilters({ status: 'CLOSED' })} active={advFilters.status === 'CLOSED'} />
      </div>

      <OverviewStrip
        storageKey="overview.incidents"
        processBarTitle={t('incidentsProcessBarTitle')}
        processBarSubtitle={t('incidentsProcessBarSub')}
        lifecycles={[
          // F2-197 — `open_count` (now via isIncidentOpen, F2-360) matches the "Open" KPI and the
          // register this tile links to. The other two tiles stay unmeasured: not fetched here nor
          // derivable from the incident list (the catalogue's old 0/0 asserted "nothing outstanding").
          { ...INCIDENT_LIFECYCLES[0], activeCount: open_count },
          INCIDENT_LIFECYCLES[1],
          INCIDENT_LIFECYCLES[2],
        ]}
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
                {/* ⚠ F2-212, found in the browser after every gate was green: this badge
                    read `{n} closed` / `{n} مغلقة`, and THE FIX ABOVE IS WHAT MADE IT FALSE.
                    While the register filtered CLOSED||RECOVERED the word described the
                    predicate; removing the filter left a label asserting a state the rows
                    do not hold — all three lesson-bearing incidents are INVESTIGATING/OPEN/
                    OPEN. F2-208's label-versus-predicate rule, except the slice that fixed
                    the predicate is what broke the label: when you remove a filter, read
                    every label that was describing it. */}
                <Badge tone="primary">{lessons.length} {t('incidentsLessonsRecorded')}</Badge>
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
                        {/* F2-212: the "user added" badge distinguished a localStorage
                            row from a derived one; both sources are gone, so every row
                            here is now the server's. The CAPA badge printed a minted
                            `CAPA-<1000+id>` that matched no real reference — a citation
                            is either issued by the CAPA register or absent. */}
                        <div style={{ fontWeight: 600, fontSize: 13 }}>{l.incident}</div>
                      </div>
                      <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>→ {l.lesson}</div>
                    </div>
                  ))}
                </div>}
          </Card>
          {/* ⚠⚠ F2-212, found in the BROWSER six lines below the register this slice had
              just made honest — F2-211's own headline, one card across. This was titled
              "CAPA Themes (90d)" and rendered five INLINE hard-coded themes with counts
              7/4/3/2/2. Measured: `capa.tasks` has 26 columns (read from sys.columns) and
              NO theme, root-cause, category or incident_id column, so a theme is not
              reconstructible at all (F2-209's third state); the counts summed to 18 against
              a register holding 16 live rows in dev and 13 in prod; and exactly ONE CAPA
              carries `origin_type = 'incident'` in both databases. The "(90d)" window was
              a second claim with nothing behind it.
              🔑 Three de-mocking gates were blind to it at once, each for a reason already
              recorded: `kpiProvenance` keys on the `<Kpi>` tag (F2-195), `pageDataProvenance`
              asks whether the PAGE calls a server and this one does (F2-198), and F2-194's
              corpus census looks for an exported `const` array — this literal is INLINE in
              JSX, so it is in none of the three populations.
              What replaces it is the real relation the platform does record: corrective
              actions whose origin is an incident, read from `GET /api/capa/tasks` through
              the register hook F2-208 rewired. No window is claimed, and an empty result
              says "none recorded" rather than rendering a green all-clear (F2-197). */}
          <CapaFromIncidents t={t} lang={lang} />
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
        initial={{ incidentId: '', lesson: '' }}
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
                      <PdplApplicabilityBadge tmpl={tmpl} isAr={lang === 'ar'} />
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
