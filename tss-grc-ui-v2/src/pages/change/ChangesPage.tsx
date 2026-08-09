/**
 * The Change Enablement workspace — ITIL v4 change enablement on route `/changes`.
 *
 * <p><b>S5c: this file is now a composition root, not a workspace.</b> It owns the route,
 * the tab vocabulary, the register table and the create/edit form, and delegates every other
 * surface to a sibling in this folder. It was 1,036 lines holding seven screens; the panels
 * were extracted byte-for-byte so the split carries no behaviour change.
 *
 * <p><b>Why the tabs stay tabs.</b> §11 draws each panel as its own route. CE-13 does that,
 * gated on S7 giving the CAB screens something to render. Routing them now would put rows in
 * the sidebar that resolve to nothing — the F2-02 defect CE-0 removed from this very group.
 * The folder is the skeleton; the routes come with the screens.
 */
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  GitBranch, Filter, Plus, AlertTriangle, ShieldCheck, Clock, ChevronRight,
  X as XIcon, RefreshCw, FolderOpen,
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
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { RowActions } from '../../components/ui/RowActions';
import { EntityDocsModal } from '../../components/ui/EntityDocsModal';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { ExportMenu } from '../../components/ui/ExportMenu';
import {
  changeApi, reportingApi, ApiError,
  type ChangeRequestDto, type ChangeAnalyticsDto,
} from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { isAuthenticated, canExport } from '../../lib/auth';
import { CHANGE_REQUESTS, CHANGE_ANALYTICS } from './mockChanges';
import { CHANGE_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { CHANGE_ANCHORS } from '../../lib/moduleAnchors';
import { CommsPanel } from '../../components/comms/CommsPanel';
import { GovernancePanel } from './GovernancePanel';
import { usePromoted, isUnsyncedId } from '../../lib/promotedRecords';
import { toChangeRequestDto, usePromotedProvenance } from '../../lib/promotedAdapters';
import { PromotedBadge } from '../../components/intake/PromotedBadge';
import {
  TAB_IDS, REVIEW_STATUSES, STATUS_TONE, TYPE_TONE, RISK_TONE,
  TYPE_OPTIONS, CATEGORY_OPTIONS, RISK_OPTIONS, EMPTY, type TabId,
} from './changeVocabulary';
import { KanbanBoard } from './ChangeBoardPanel';
import { WorkflowPanel } from './ChangeDetailPanel';
import { AnalyticsPanel } from './ChangeAnalyticsPanel';
import { IntegrationsPanel } from './ChangeIntegrationsPanel';
import '../../styles/pages/changes.css';

export function ChangesPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  /**
   * F2-98 — the badge has to track the *substitution*, not the *session*.
   *
   * <p>This was `const demoMode = !isAuthenticated()`: evaluated once, never updated. But
   * `applyDemoChanges()` runs from three places, and only one of them is "not signed in". The
   * other two — an empty register, and the API throwing — happen to a signed-in user, where
   * `!isAuthenticated()` is false and the badge therefore did **not** render. So the one case a
   * real tenant can actually hit (a fresh tenant with no changes yet, or the backend down) was
   * the one case that showed six fabricated Saudi change requests with nothing saying so.
   *
   * <p>`useApiResource` already models this correctly — `isDemo` starts at `!isAuthenticated()`
   * and is then *set by the substitution*. This page hand-rolls its loader and so inherited none
   * of it; `demoFallbackContract.test.ts` did not catch it because that guard's trigger is a call
   * to the hook. Same contract, now reactive, and the guard has been widened to the behaviour
   * rather than the hook.
   */
  const [demoMode, setDemoMode] = useState(!isAuthenticated());

  // Change-Enablement process-bar deep-links: ?tab= opens a tab; ?review=1 shows
  // changes awaiting a post-implementation review; ?type= filters by change type.
  const reviewMode = searchParams.get('review') === '1';
  const typeParam = (searchParams.get('type') ?? '').toUpperCase();

  // The URL is the source of truth for the active tab — deliberately, not incidentally.
  //
  // This was `useState(() => searchParams.get('tab'))`, whose initialiser runs exactly once. React
  // Router does not remount this component when only the query string changes, so every deep-link
  // that arrived while the page was already open was read and discarded. The visible symptom was the
  // sidebar's "Calendar & freeze windows" row (it points at `/changes?tab=governance`): clicking it
  // updated the address bar and left the register on screen, which reads as a frozen page rather
  // than a dropped parameter. Reported from the running app, reproduced client-side, fixed here.
  //
  // Deriving instead of syncing also makes browser Back/Forward move between tabs, and makes any
  // tab shareable as a link. `replace: true` keeps tab clicks out of the history stack, so Back
  // still leaves the page rather than walking the tabs the user just visited.
  const tabParam = searchParams.get('tab') as TabId | null;
  const tab: TabId = tabParam && TAB_IDS.includes(tabParam) ? tabParam : 'register';
  const setTab = (next: TabId) => {
    const next_ = new URLSearchParams(searchParams);
    // 'register' is the default, so it stays out of the URL — /changes and /changes?tab=register
    // must not be two different-looking addresses for one screen.
    if (next === 'register') next_.delete('tab');
    else next_.set('tab', next);
    setSearchParams(next_, { replace: true });
  };
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

  // F2-98: the single place that puts fabricated rows on screen, and therefore the single place
  // that raises the disclosure. Keeping the two together is the point — they drifted apart
  // precisely because the flag lived at the top of the component and the substitution at the bottom.
  function applyDemoChanges() {
    setItems(CHANGE_REQUESTS); setAnalytics(CHANGE_ANALYTICS); setDemoMode(true);
  }

  async function load() {
    if (!isAuthenticated()) { applyDemoChanges(); return; }
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
      // A reload that finally returns the tenant's own rows must clear the badge, or the page
      // keeps apologising for data it is no longer showing.
      setDemoMode(false);
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
    { id: 'governance',   label: t('changeTabGovernance') },
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
      // F2-171: promoted-intake changes are merged in above and exist only in this browser.
      cell: c => <RowActions disabled={demoMode || isUnsyncedId(c.id)} onEdit={() => { setEditing(c); setModalOpen(true); }}
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
{/* F2-27: the change register is the ONE export whose grants differ from READ —
                CHANGE:EXPORT is withheld from VIEWER, which CHANGE:READ is not. Hide the
                menu rather than let a read-only user click into a 403. */}
            {canExport('CHANGE') && (
              <ExportMenu
                demoMode={demoMode} t={t} toast={toast}
                // F2-98: a signed-in user looking at sample rows would otherwise be told to
                // "sign in", and the file they downloaded would be their real, empty register.
                disabledReason={isAuthenticated() ? 'exportDemoDataBlocked' : 'exportSignInRequired'}
                onPdf={() => reportingApi.changeRegisterPdf(lang)}
                onDocx={() => reportingApi.changeRegisterDocx(lang)}
                onXlsx={() => reportingApi.changeRegisterXlsx()}
              />
            )}
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

      {tab === 'governance' && <GovernancePanel toast={toast} />}

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

function Loading() {
  const { t } = useI18n();
  return <div className="changes-loading">{t('riskLoading')}</div>;
}
