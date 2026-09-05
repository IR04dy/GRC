/*
 * The CAPA register — /capa.
 *
 * ⚠⚠ F2-208. This page used to render `useCapas()`'s merge of the real register
 * with a twenty-row browser corpus and a localStorage overlay, and its two write
 * controls wrote into that overlay:
 *
 *   • "Advance" called `advance(c.id, to, 'demo.user@tss.sa')` — a patch keyed on
 *     the row id, applied on render on top of the SERVER rows too. So advancing a
 *     real CAPA to audit sign-off changed what this screen said about a record the
 *     server had never been told about, and it survived reload.
 *   • "Start RCA" captured a root-cause analysis into the same overlay. There is
 *     no RCA column anywhere on `capa.tasks`, so that modal could never have been
 *     wired; it is deleted, not rewired.
 *
 * Both are now the server's: `POST /api/capa/tasks/{id}/transition`, replacing the
 * row from the response. The 409 an illegal edge earns is surfaced, because a
 * screen that disagrees with the register is the defect this slice removed.
 *
 * Tabs removed with the corpus, each because its whole input was invented:
 *   • Pareto — grouped by `rootCauseCategory`, which has no column.
 *   • PA triggers — `lib/paTriggers.ts` derived "a KRI has breached, file a
 *     preventive action" from `pages/risk/mockRisks`. Wiring `create` would have
 *     turned an invented breach into a real CAPA, which is worse than the fake
 *     write it replaced. Deleted; the capability is filed.
 */
import { useMemo, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  ListChecks, Hourglass, Workflow, Plus, Search, Send, AlertTriangle,
  Filter, Download, ShieldCheck, Stamp, Bell, FileText, CheckCircle2,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FilterModal, distinctValues, type FilterField } from '../../components/ui/FilterModal';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { getErrorDisplayMessage } from '../../lib/apiError';
import {
  useCapas, CAPA_STATUS_LABEL, CAPA_STATUS_TONE, CAPA_SEVERITY_TONE,
  CAPA_ALLOWED, CAPA_SOURCE_LABEL, agingBucket, daysUntilDue, isCapaOpen,
  capaForwardPath, capaAbandonState,
  type Capa, type CapaStatus,
} from '../../lib/capa';
import { deriveCapaNotifs, audCommitteeDigest, type CapaNotif } from '../../lib/capaNotifications';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';
import '../../styles/pages/capa.css';

type TabId = 'register' | 'aging' | 'workflow' | 'committee';

/**
 * The forward step the register offers per state.
 *
 * ⚠ DERIVED from CAPA_ALLOWED, never a second table: the forward step is the one
 * allowed target that is not CANCELLED and not a step backwards. F2-196's rule —
 * a restated vocabulary drifts from the one that decides, and here the one that
 * decides answers 409.
 */
function forwardStep(status: CapaStatus): CapaStatus | null {
  const back: Partial<Record<CapaStatus, CapaStatus>> = { AWAITING_VERIFICATION: 'IN_PROGRESS' };
  return CAPA_ALLOWED[status].find(s => s !== 'CANCELLED' && s !== back[status]) ?? null;
}

export function CapaPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const { items, loading, error, transition } = useCapas();
  const [tab, setTab] = useState<TabId>('register');
  const [q, setQ] = useState('');
  const [filterStatus, setFilterStatus] = useState<CapaStatus | 'ALL'>('ALL');
  const [filterType, setFilterType] = useState<'ALL' | 'CORRECTIVE' | 'PREVENTIVE'>('ALL');
  const [busyId, setBusyId] = useState<string | null>(null);
  const [filterOpen, setFilterOpen] = useState(false);
  const [advFilters, setAdvFilters] = useState<Record<string, string | undefined>>({});

  const notifs = useMemo(() => deriveCapaNotifs(items), [items]);
  const criticalNotifs = notifs.filter(n => n.severity === 'CRITICAL');
  const digest = useMemo(() => audCommitteeDigest(items), [items]);

  const filtered = useMemo(() => {
    const ql = q.trim().toLowerCase();
    return items.filter(c => {
      if (filterStatus !== 'ALL' && c.status !== filterStatus) return false;
      if (filterType !== 'ALL' && c.type !== filterType) return false;
      if (advFilters.severity && c.severity !== advFilters.severity) return false;
      if (advFilters.source && c.source !== advFilters.source) return false;
      if (advFilters.owner && !c.owner.toLowerCase().includes(advFilters.owner.toLowerCase())) return false;
      if (advFilters.dueAfter && c.dueDate < advFilters.dueAfter) return false;
      if (advFilters.dueBefore && c.dueDate > advFilters.dueBefore) return false;
      if (!ql) return true;
      return (
        c.id.toLowerCase().includes(ql) ||
        c.titleEn.toLowerCase().includes(ql) ||
        (c.titleAr ?? '').toLowerCase().includes(ql) ||
        c.owner.toLowerCase().includes(ql) ||
        c.originId.toLowerCase().includes(ql)
      );
    });
  }, [items, q, filterStatus, filterType, advFilters]);

  const filterFields: FilterField[] = [
    { key: 'severity', label: t('capaFilterFieldSeverity'), kind: 'select',
      options: [
        { value: 'LOW',      label: 'LOW' },
        { value: 'MEDIUM',   label: 'MEDIUM' },
        { value: 'HIGH',     label: 'HIGH' },
        { value: 'CRITICAL', label: 'CRITICAL' },
      ] },
    { key: 'source', label: t('capaFilterFieldSource'), kind: 'select',
      options: distinctValues(items, 'source') },
    { key: 'owner', label: t('capaFilterFieldOwner'), kind: 'text' },
    { key: 'dueAfter',  label: t('capaFilterFieldDueAfter'),  kind: 'date' },
    { key: 'dueBefore', label: t('capaFilterFieldDueBefore'), kind: 'date' },
  ];

  // KPI rollups — every one of these is a count over a real column.
  const open = items.filter(isCapaOpen);
  const overdue = open.filter(c => daysUntilDue(c) < 0);
  const awaiting = items.filter(c => c.status === 'AWAITING_VERIFICATION');
  const preventive = items.filter(c => c.type === 'PREVENTIVE');
  const criticalOpen = open.filter(c => c.severity === 'HIGH' || c.severity === 'CRITICAL');

  const tabs: TabItem<TabId>[] = [
    { id: 'register',  label: t('capaTabRegister') },
    { id: 'aging',     label: t('capaTabAging') },
    { id: 'workflow',  label: t('capaTabWorkflow') },
    { id: 'committee', label: `${t('capaTabCommittee')}${criticalNotifs.length ? ` · ${criticalNotifs.length}` : ''}` },
  ];

  /**
   * Take the one forward step the machine allows, through the server.
   *
   * ⚠ Nothing here computes the resulting status: the row is replaced with what
   * came back. An edge the server refuses arrives as a 409 and is shown — the
   * previous version could not fail, because it only wrote to the browser.
   */
  async function advance(c: Capa) {
    const to = forwardStep(c.status);
    if (!to) { toast.info(t('capaNoTransition')); return; }
    setBusyId(c.id);
    try {
      const saved = await transition(c.id, { status: to });
      toast.success(`${saved.id} → ${CAPA_STATUS_LABEL[saved.status][lang === 'ar' ? 'ar' : 'en']}`);
    } catch (e) {
      toast.danger(getErrorDisplayMessage(e));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <Shell title={t('modCapa')} sub={t('capaSub')}>
      <PageHeader
        eyebrow={t('modCapa')}
        title={t('capaTitle')}
        sub={t('capaPageSub')}
        actions={<>
          <Button variant="ghost" Icon={Filter} onClick={() => setFilterOpen(true)}>
            {t('actionFilter')}{Object.values(advFilters).filter(Boolean).length > 0 ? ` · ${Object.values(advFilters).filter(Boolean).length}` : ''}
          </Button>
          <Button variant="ghost" Icon={Download} onClick={() => {
            // Every column here is a column on capa.tasks. The previous export
            // wrote the merged list — twenty invented CAPAs alongside the real
            // ones — into a CSV an audit committee could attach to its minutes.
            const rows: ExportRow[] = filtered.map(c => ({
              reference: c.id,
              type: c.type,
              originType: c.originType,
              originId: c.originId,
              titleEn: c.titleEn,
              titleAr: c.titleAr,
              status: c.status,
              severity: c.severity,
              owner: c.owner,
              raisedAt: c.createdAt?.slice(0, 10) ?? '',
              targetDate: c.dueDate,
              startedAt: c.startedAt?.slice(0, 10) ?? '',
              submittedForVerificationAt: c.submittedForVerificationAt?.slice(0, 10) ?? '',
              verifiedAt: c.verifiedAt?.slice(0, 10) ?? '',
              verifiedBy: c.verifiedBy ?? '',
              closedAt: c.closedAt?.slice(0, 10) ?? '',
              cancellationReason: c.cancellationReason ?? '',
              progressNotesEn: c.progressNotesEn ?? '',
              evidenceRefs: c.evidenceRefs ?? '',
            }));
            downloadCsv(rows, stampFilename('capa-register', 'csv'));
            toast.success(t('capaExportDone'));
          }}>{t('actionExport')}</Button>
          {/* DD-76 — the front door is the intake substrate. ⚠ F2-208: this
              button used to be labelled "New CAPA", and a CONTROL_OBSERVATION
              submission is triaged into a FINDING, not into this register. The
              label now says what the control does. */}
          <Button variant="primary" Icon={Plus} onClick={() => navigate('/intake/control_observation')}>
            {t('capaReportObservation')}
          </Button>
        </>}
      />

      {error && (
        <div className="capa-alert-banner" role="status">
          <AlertTriangle size={18} color="var(--danger-fg)" />
          <div className="capa-alert-banner__body">
            <div className="capa-alert-banner__title">{t('capaLoadFailed')}</div>
            <div className="capa-alert-banner__sub">{error}</div>
          </div>
        </div>
      )}

      <div className="kpi-grid capa-kpi-strip">
        <Kpi Icon={ListChecks}    label={t('capaKpiOpen')}         value={open.length}         tone="primary" />
        <Kpi Icon={AlertTriangle} label={t('capaKpiOverdue')}      value={overdue.length}      tone="danger"
             onClick={() => { setTab('aging'); }} />
        <Kpi Icon={CheckCircle2}  label={t('capaKpiAwaiting')}     value={awaiting.length}     tone="accent"
             onClick={() => setFilterStatus('AWAITING_VERIFICATION')} active={filterStatus === 'AWAITING_VERIFICATION'} />
        <Kpi Icon={ShieldCheck}   label={t('capaKpiCriticalOpen')} value={criticalOpen.length} tone="warning" />
        <Kpi Icon={Workflow}      label={t('capaKpiPreventive')}   value={preventive.length}   tone="info"
             onClick={() => setFilterType('PREVENTIVE')} active={filterType === 'PREVENTIVE'} />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'register' && criticalNotifs.length > 0 && (
        <div className="capa-alert-banner">
          <AlertTriangle size={18} color="var(--danger-fg)" />
          <div className="capa-alert-banner__body">
            <div className="capa-alert-banner__title">
              {criticalNotifs.length} {t('capaAlertsStripTitle')}
            </div>
            <div className="capa-alert-banner__sub">{t('capaAlertsStripBody')}</div>
          </div>
          <Button variant="primary" size="sm" Icon={Send} onClick={() => setTab('committee')}>
            {t('capaAlertsStripCta')}
          </Button>
        </div>
      )}

      {tab === 'register' && (
        <Card title={t('capaTabRegister')} Icon={ListChecks}
          action={<div className="capa-toolbar">
            <div className="capa-search-wrap">
              <Search size={14} className="capa-search-icon" />
              <input
                value={q} onChange={e => setQ(e.target.value)}
                placeholder={t('capaSearchPh')}
                className="capa-search-input"
              />
            </div>
            <select value={filterType} onChange={e => setFilterType(e.target.value as typeof filterType)}
              className="capa-input">
              <option value="ALL">{t('capaFilterTypeAll')}</option>
              <option value="CORRECTIVE">{t('capaFilterTypeCA')}</option>
              <option value="PREVENTIVE">{t('capaFilterTypePA')}</option>
            </select>
            <select value={filterStatus} onChange={e => setFilterStatus(e.target.value as typeof filterStatus)}
              className="capa-input">
              <option value="ALL">{t('capaFilterStatusAll')}</option>
              {(Object.keys(CAPA_STATUS_LABEL) as CapaStatus[]).map(s =>
                <option key={s} value={s}>{CAPA_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}</option>)}
            </select>
            <Badge tone="primary">{filtered.length}</Badge>
          </div>}>
          {filtered.length === 0
            ? <EmptyState Icon={ListChecks}
                title={loading ? t('capaLoading') : t('capaEmptyTitle')}
                sub={loading ? '' : t('capaEmptyBody')} />
            : <div className="capa-row-list">
                {filtered.map(c => (
                  <CapaRow key={c.id} c={c} busy={busyId === c.id} onAdvance={() => void advance(c)} />
                ))}
              </div>
          }
        </Card>
      )}

      {tab === 'aging' && <AgingTab items={items} />}
      {tab === 'workflow' && <WorkflowTab items={items} />}
      {tab === 'committee' && <CommitteeTab notifs={notifs} digest={digest} />}

      <FilterModal
        open={filterOpen}
        fields={filterFields}
        value={advFilters}
        hint={t('capaFilterModalHint')}
        onApply={v => { setAdvFilters(v); toast.success(t('filterModalApplied')); }}
        onClose={() => setFilterOpen(false)}
      />
    </Shell>
  );
}

function CapaRow({ c, busy, onAdvance }: { c: Capa; busy: boolean; onAdvance: () => void }) {
  const { t, lang } = useI18n();
  const dueIn = daysUntilDue(c);
  const open = isCapaOpen(c);
  const overdue = dueIn < 0 && open;
  const title = lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn;
  const next = forwardStep(c.status);
  const source = CAPA_SOURCE_LABEL[c.source];

  return (
    <div className="capa-row">
      <div>
        <div className="mono capa-row__code">{c.id}</div>
        <Badge tone={c.type === 'PREVENTIVE' ? 'info' : 'primary'}>{c.type === 'PREVENTIVE' ? 'PA' : 'CA'}</Badge>
      </div>

      <div className="capa-row__title-block">
        <Link to={`/capa/${c.id}`} title={t('capaDetailOpenLink')} className="capa-row__title-link">
          {title}
        </Link>
        <div className="capa-row__source">
          {t('capaSourceLabel')}: <span className="capa-row__source-target">
            {source[lang === 'ar' ? 'ar' : 'en']} · {c.originId}
          </span>
        </div>
      </div>

      <Badge tone={CAPA_SEVERITY_TONE[c.severity]}>{c.severity}</Badge>

      <Badge tone={CAPA_STATUS_TONE[c.status]} dot>
        {CAPA_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
      </Badge>

      <div className="capa-row__owner">{c.owner}</div>

      <div>
        <div className={`mono capa-row__due${overdue ? ' capa-row__due--overdue' : ''}`}>
          {c.dueDate}
        </div>
        <div className={`capa-row__remaining${overdue ? ' capa-row__remaining--overdue' : ''}`}>
          {overdue ? `${Math.abs(dueIn)}d ${t('capaOverdue')}`
            : !open ? t('capaClosed')
            : `${dueIn}d ${t('capaRemaining')}`}
        </div>
      </div>

      {/* Verification attribution — the server stamps it from the authenticated
          principal when it closes the CAPA, so this is a real name or nothing.
          It used to be a client-supplied string. */}
      <div className="capa-row__verifier">
        {c.verifiedBy
          ? <><span className="capa-row__verifier-label">{t('capaVerifiedBy')}</span> {c.verifiedBy}</>
          : <span className="capa-row__verifier-none">—</span>}
      </div>

      <div className="capa-row__actions">
        {next && (
          <Button variant="primary" size="sm" Icon={Send} disabled={busy} onClick={onAdvance}
            title={t('capaAdvanceTo')}>
            {CAPA_STATUS_LABEL[next][lang === 'ar' ? 'ar' : 'en']}
          </Button>
        )}
      </div>
    </div>
  );
}

function AgingTab({ items }: { items: Capa[] }) {
  const { t } = useI18n();
  const buckets: Record<string, Capa[]> = {
    OVERDUE: [], '0-30': [], '31-60': [], '61-90': [], '90+': [], UNKNOWN: [],
  };
  for (const c of items) {
    const b = agingBucket(c);
    if (b === 'CLOSED') continue;
    buckets[b].push(c);
  }
  // ⚠ UNKNOWN is a real bucket, not a hidden one: a CAPA whose raise date the
  // server did not send cannot be aged, and dropping it would make the buckets
  // sum to less than the open population without saying so (F2-197).
  const order = ['OVERDUE', '0-30', '31-60', '61-90', '90+', 'UNKNOWN'] as const;
  return (
    <Card title={t('capaTabAging')} Icon={Hourglass}>
      <div className="capa-aging-grid">
        {order.filter(b => b !== 'UNKNOWN' || buckets.UNKNOWN.length > 0).map(b => (
          <div key={b} className="capa-bucket">
            <div className="capa-bucket__head">
              <Badge tone={b === 'OVERDUE' ? 'danger'
                : b === '90+' || b === '61-90' ? 'warning'
                : b === '31-60' ? 'info'
                : b === 'UNKNOWN' ? 'neutral' : 'success'}>
                {b === 'OVERDUE' ? t('capaAgingOverdue')
                  : b === 'UNKNOWN' ? t('capaAgingUnknown')
                  : `${b} ${t('capaDaysSuffix')}`}
              </Badge>
              <span className="mono capa-bucket__count">{buckets[b].length}</span>
            </div>
            <div className="capa-bucket__list">
              {buckets[b].map(c => (
                <div key={c.id} className="capa-bucket__item">
                  <span className="mono capa-bucket__item-code">{c.id}</span> · {c.severity}
                </div>
              ))}
              {buckets[b].length === 0 && <span className="capa-bucket__empty">—</span>}
            </div>
          </div>
        ))}
      </div>
      <div className="capa-footnote-plain">{t('capaAgingFootnote')}</div>
    </Card>
  );
}

function WorkflowTab({ items }: { items: Capa[] }) {
  const { t, lang } = useI18n();
  // ⚠ Lanes are the machine's own key order, not a hand-written list. The old
  // version named six stages, three of which the server has no state for.
  const stages = Object.keys(CAPA_ALLOWED) as CapaStatus[];
  const stageLabel = (s: CapaStatus) => CAPA_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en'];
  const abandon = capaAbandonState();

  return (
    <Card title={t('capaTabWorkflow')} Icon={Workflow}>
      <div className="capa-kanban-grid" style={{ gridTemplateColumns: `repeat(${stages.length}, 1fr)` }}>
        {stages.map(s => {
          const lane = items.filter(c => c.status === s);
          return (
            <div key={s} className="capa-kanban-lane">
              <div className="capa-kanban-lane__head">
                <Badge tone={CAPA_STATUS_TONE[s]} dot>{CAPA_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}</Badge>
                <span className="mono capa-kanban-lane__count">{lane.length}</span>
              </div>
              <div className="capa-kanban-list">
                {lane.map(c => (
                  <Link key={c.id} to={`/capa/${c.id}`} className="capa-kanban-card">
                    <div className="mono capa-kanban-card__code">{c.id}</div>
                    <div className="capa-kanban-card__title">
                      {(lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn).slice(0, 60)}
                    </div>
                    <div className="capa-kanban-card__badges">
                      <Badge tone={CAPA_SEVERITY_TONE[c.severity]}>{c.severity}</Badge>
                      <Badge tone={c.type === 'PREVENTIVE' ? 'info' : 'neutral'}>{c.type === 'PREVENTIVE' ? 'PA' : 'CA'}</Badge>
                    </div>
                  </Link>
                ))}
                {lane.length === 0 && <div className="capa-kanban-empty">—</div>}
              </div>
            </div>
          );
        })}
      </div>
      {/*
        F2-465: COMPOSED from the machine, never written beside it. `capaWorkflowFootnote`
        was one sentence naming six stages — Identified, Implementing, Owner-tested (1LoD),
        Independent-tested (2LoD), Audit signed-off (3LoD), Closed — of which this register
        has one, plus "a 90-day post-closure check fires VERIFICATION_FAILED on regression"
        for a module that carries no @Scheduled method at all. It rendered directly beneath
        the lanes above, which have named the five real states since F2-208 moved them onto
        CAPA_ALLOWED — so the card contradicted itself, and the comment recording that fix
        sat fourteen lines above the surviving copy.
      */}
      <div className="capa-footnote-plain capa-footnote--plain">
        {`${t('capaWorkflowForward')} ${capaForwardPath().map(stageLabel).join(' → ')}.`}
        {abandon ? ` ${t('capaWorkflowCancel')} ${stageLabel(abandon)}.` : ''}
      </div>
    </Card>
  );
}

/**
 * Audit-Committee readout — the quarterly rollup plus the live signal list.
 *
 * ⚠ F2-208 removed four digest figures (verification failures, recurrent CAPAs,
 * customer disclosures, top root causes) rather than letting them read zero. Each
 * was derived from a field with no column, so a zero here would have told an audit
 * committee that the same root cause has not come back — an invented all-clear,
 * which F2-197 records as worse than an invented number.
 */
function CommitteeTab({ notifs, digest }: {
  notifs: CapaNotif[];
  digest: ReturnType<typeof audCommitteeDigest>;
}) {
  const { t, lang } = useI18n();
  const toast = useToast();

  const KIND_LABEL: Record<CapaNotif['kind'], { en: string; ar: string }> = {
    NEW_CRITICAL: { en: 'New critical CAPA', ar: 'إجراء حرج جديد' },
    DUE_SOON:     { en: 'Due in ≤ 7 days',   ar: 'يستحق خلال ≤ 7 أيام' },
    OVERDUE:      { en: 'Overdue',           ar: 'متأخر' },
  };
  const KIND_TONE: Record<CapaNotif['kind'], 'danger' | 'warning'> = {
    NEW_CRITICAL: 'danger', OVERDUE: 'danger', DUE_SOON: 'warning',
  };

  return (
    <div className="capa-committee-stack">
      <Card title={`${t('capaCommitteeDigest')} — ${digest.quarterLabel}`} Icon={Stamp}
        action={<Button variant="ghost" size="sm" Icon={FileText}
          onClick={() => {
            const rows: ExportRow[] = [
              { metric: t('capaCommitteeOpened'),        value: String(digest.opened) },
              { metric: t('capaCommitteeClosed'),        value: String(digest.closed) },
              { metric: t('capaCommitteeInFlight'),      value: String(digest.inFlight) },
              { metric: t('capaCommitteeOverdue'),       value: String(digest.overdue) },
              { metric: t('capaCommitteeCriticalOpen'),  value: String(digest.criticalOpen) },
              { metric: t('capaCommitteeAwaiting'),      value: String(digest.awaitingVerification) },
              { metric: t('capaCommitteePreventive'),    value: String(digest.preventive) },
              { metric: t('capaCommitteeUndated'),       value: String(digest.undatedRows) },
              { metric: 'quarter',                       value: digest.quarterLabel },
              { metric: 'period_start',                  value: digest.quarterStart },
              { metric: 'period_end',                    value: digest.quarterEnd },
            ];
            downloadCsv(rows, stampFilename(`capa-committee-${digest.quarterLabel}`, 'csv'));
            toast.success(t('exportCsvDone'));
          }}>
          {t('capaCommitteeExport')}
        </Button>}>
        <div className="capa-committee-period">
          {t('capaCommitteeDigestPeriod')}: <span className="mono">{digest.quarterStart} → {digest.quarterEnd}</span>
        </div>
        <div className="capa-digest-grid">
          <DigestStat label={t('capaCommitteeOpened')}       value={digest.opened}   tone="info" />
          <DigestStat label={t('capaCommitteeClosed')}       value={digest.closed}   tone="success" />
          <DigestStat label={t('capaCommitteeInFlight')}     value={digest.inFlight} tone="primary" />
          <DigestStat label={t('capaCommitteeOverdue')}      value={digest.overdue}
            tone={digest.overdue > 0 ? 'danger' : 'success'} />
          <DigestStat label={t('capaCommitteeCriticalOpen')} value={digest.criticalOpen}
            tone={digest.criticalOpen > 0 ? 'warning' : 'success'} />
          <DigestStat label={t('capaCommitteeAwaiting')}     value={digest.awaitingVerification} tone="accent" />
          <DigestStat label={t('capaCommitteePreventive')}   value={digest.preventive} tone="accent" />
        </div>
        {/* The denominator disclosure. A quarter's "opened" count is only as
            complete as the raise dates behind it. */}
        {digest.undatedRows > 0 && (
          <div className="capa-footnote" role="status">
            {digest.undatedRows} {t('capaCommitteeUndatedNote')}
          </div>
        )}
        <div className="capa-footnote">{t('capaCommitteeFootnote')}</div>
      </Card>

      <Card title={t('capaCommitteeLiveStream')} Icon={Bell}
        action={<Badge tone={notifs.some(n => n.severity === 'CRITICAL') ? 'danger' : 'primary'}>
          {notifs.length}
        </Badge>}>
        {notifs.length === 0
          ? <EmptyState Icon={ShieldCheck} title={t('capaCommitteeNoAlerts')} sub={t('capaCommitteeNoAlertsBody')} />
          : <div className="capa-row-list">
              {notifs.map(n => (
                <div key={n.id} className="capa-stream-row">
                  <span className="mono capa-stream-mono">{n.firedAt}</span>
                  <Badge tone={KIND_TONE[n.kind]}>
                    {KIND_LABEL[n.kind][lang === 'ar' ? 'ar' : 'en']}
                  </Badge>
                  <div className="capa-stream-main">
                    <div className="mono capa-stream-code">{n.capaId}</div>
                    <div className="capa-stream-message">
                      {lang === 'ar' ? n.messageAr : n.messageEn}
                    </div>
                  </div>
                  <Badge tone={n.severity === 'CRITICAL' ? 'danger' : n.severity === 'WARN' ? 'warning' : 'info'}>
                    {n.severity}
                  </Badge>
                </div>
              ))}
            </div>
        }
      </Card>
    </div>
  );
}

function DigestStat({ label, value, tone }: {
  label: string; value: number;
  tone: 'info' | 'success' | 'primary' | 'danger' | 'warning' | 'accent';
}) {
  return (
    <div className="capa-digest-stat" data-tone={tone}>
      <div className="mono capa-digest-stat__value">{value}</div>
      <div className="capa-digest-stat__label">{label}</div>
    </div>
  );
}
