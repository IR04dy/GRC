import { useMemo, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  ListChecks, Hourglass, BarChart3, Network, Workflow, Zap,
  Plus, Search, Send, CheckCheck, Repeat, AlertTriangle,
  Filter, Download, ShieldCheck, TrendingUp, Activity,
  Stamp, Bell, FileText,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { FilterModal, distinctValues, type FilterField } from '../../components/ui/FilterModal';
import { EmptyState } from '../../components/ui/EmptyState';
import { Progress } from '../../components/ui/Progress';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  useCapas, CAPA_STATUS_LABEL, CAPA_STATUS_TONE, CAPA_SEVERITY_TONE,
  CAPA_FORWARD_TRANSITIONS, agingBucket, daysUntilDue, paretoCauses,
  effectivenessOverdue,
  type Capa, type CapaStatus, type RcaMethod, type RootCauseCategory,
} from '../../lib/capa';
import { scanPaTriggers, type PaTrigger } from '../../lib/paTriggers';
import { deriveCapaNotifs, audCommitteeDigest, type CapaNotif } from '../../lib/capaNotifications';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';
import { usePromoted } from '../../lib/promotedRecords';
import { toCapaFromObservation } from '../../lib/promotedAdapters';
import '../../styles/pages/capa.css';

type TabId = 'register' | 'aging' | 'pareto' | 'workflow' | 'triggers' | 'committee';

const ROOT_CAUSE_CATS: { id: RootCauseCategory; en: string; ar: string }[] = [
  { id: 'PEOPLE',     en: 'People',     ar: 'بشري' },
  { id: 'PROCESS',    en: 'Process',    ar: 'عملية' },
  { id: 'TECHNOLOGY', en: 'Technology', ar: 'تقنية' },
  { id: 'POLICY',     en: 'Policy',     ar: 'سياسة' },
  { id: 'TRAINING',   en: 'Training',   ar: 'تدريب' },
  { id: 'EXTERNAL',   en: 'External',   ar: 'خارجية' },
  { id: 'MANAGEMENT', en: 'Management', ar: 'إدارة' },
  { id: 'UNKNOWN',    en: 'Unknown',    ar: 'غير محدّد' },
];

export function CapaPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const { items: apiItems, advance, create, update } = useCapas();
  // DD-75: surface CAPAs that originated from a promoted CONTROL_OBSERVATION
  // intake. Promoted CAPAs are display-only — the lifecycle still operates
  // on the underlying source intake until a real backend lands.
  const promotedCapas = usePromoted('CAPA').map(toCapaFromObservation);
  const items = useMemo(() => [...promotedCapas, ...apiItems], [promotedCapas, apiItems]);
  const [tab, setTab] = useState<TabId>('register');
  const [q, setQ] = useState('');
  const [filterStatus, setFilterStatus] = useState<CapaStatus | 'ALL'>('ALL');
  const [filterType, setFilterType] = useState<'ALL' | 'CORRECTIVE' | 'PREVENTIVE'>('ALL');
  const [rcaTarget, setRcaTarget] = useState<Capa | null>(null);
  const [triggerTarget, setTriggerTarget] = useState<PaTrigger | null>(null);
  const [filterOpen, setFilterOpen] = useState(false);
  const [advFilters, setAdvFilters] = useState<Record<string, string | undefined>>({});

  const triggers = useMemo(() => scanPaTriggers(items), [items]);
  const openTriggers = triggers.filter(t => !t.alreadyCovered);
  const notifs = useMemo(() => deriveCapaNotifs(items), [items]);
  const criticalNotifs = notifs.filter(n => n.severity === 'CRITICAL');
  const digest = useMemo(() => audCommitteeDigest(items), [items]);

  const filtered = useMemo(() => {
    const ql = q.trim().toLowerCase();
    return items.filter(c => {
      if (filterStatus !== 'ALL' && c.status !== filterStatus) return false;
      if (filterType !== 'ALL' && c.type !== filterType) return false;
      // Advanced filters from the FilterModal
      if (advFilters.severity      && c.severity !== advFilters.severity) return false;
      if (advFilters.source        && c.source !== advFilters.source) return false;
      if (advFilters.rootCauseCategory && c.rootCauseCategory !== advFilters.rootCauseCategory) return false;
      if (advFilters.owner) {
        if (!c.owner.toLowerCase().includes(advFilters.owner.toLowerCase())) return false;
      }
      if (advFilters.dueAfter && c.dueDate < advFilters.dueAfter) return false;
      if (advFilters.dueBefore && c.dueDate > advFilters.dueBefore) return false;
      if (!ql) return true;
      return (
        c.code.toLowerCase().includes(ql) ||
        c.titleEn.toLowerCase().includes(ql) ||
        (c.titleAr ?? '').toLowerCase().includes(ql) ||
        c.owner.toLowerCase().includes(ql) ||
        (c.rootCause ?? '').toLowerCase().includes(ql)
      );
    });
  }, [items, q, filterStatus, filterType, advFilters]);

  // ── Filter modal config — DD-47 ────────────────────────────────────────
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
    { key: 'rootCauseCategory', label: t('capaFilterFieldRcCategory'), kind: 'select',
      options: distinctValues(items.filter(c => c.rootCauseCategory), 'rootCauseCategory' as const) },
    { key: 'owner', label: t('capaFilterFieldOwner'), kind: 'text',
      placeholder: 'e.g. dpo@tss.sa' },
    { key: 'dueAfter',  label: t('capaFilterFieldDueAfter'),  kind: 'date' },
    { key: 'dueBefore', label: t('capaFilterFieldDueBefore'), kind: 'date' },
  ];

  // KPI rollups
  const open = items.filter(c => c.status !== 'CLOSED' && c.status !== 'CANCELLED');
  const overdue = open.filter(c => daysUntilDue(c) < 0);
  const failed = items.filter(c => c.status === 'VERIFICATION_FAILED');
  const effectivenessDue = items.filter(c => effectivenessOverdue(c));
  const preventive = items.filter(c => c.type === 'PREVENTIVE');

  const tabs: TabItem<TabId>[] = [
    { id: 'register',  label: t('capaTabRegister') },
    { id: 'triggers',  label: `${t('capaTabTriggers')}${openTriggers.length ? ` · ${openTriggers.length}` : ''}` },
    { id: 'aging',     label: t('capaTabAging') },
    { id: 'pareto',    label: t('capaTabPareto') },
    { id: 'workflow',  label: t('capaTabWorkflow') },
    { id: 'committee', label: `${t('capaTabCommittee')}${criticalNotifs.length ? ` · ${criticalNotifs.length}` : ''}` },
  ];

  function quickAdvance(c: Capa) {
    const allowed = CAPA_FORWARD_TRANSITIONS[c.status];
    const to = allowed.find(s => s !== 'CANCELLED' && s !== 'VERIFICATION_FAILED');
    if (!to) {
      toast.info(t('capaNoTransition'));
      return;
    }
    advance(c.id, to, 'demo.user@tss.sa');
    toast.success(`${c.code} → ${CAPA_STATUS_LABEL[to][lang === 'ar' ? 'ar' : 'en']}`);
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
            const rows: ExportRow[] = filtered.map(c => ({
              code: c.code,
              type: c.type,
              source: c.source,
              titleEn: c.titleEn,
              titleAr: c.titleAr ?? '',
              status: c.status,
              severity: c.severity,
              owner: c.owner,
              openedAt: c.openedAt,
              dueDate: c.dueDate,
              progressPct: c.progressPct,
              rootCauseCategory: c.rootCauseCategory ?? '',
              rootCause: c.rootCause ?? '',
              rcaMethod: c.rcaMethod,
              parentCapaId: c.parentCapaId ?? '',
              effectivenessDueDate: c.effectivenessDueDate ?? '',
              customerDisclosureRequired: c.customerDisclosureRequired ? 'Y' : 'N',
              frameworkRefs: (c.frameworkRefs ?? []).join('; '),
            }));
            downloadCsv(rows, stampFilename('capa-register', 'csv'));
            toast.success(t('capaExportDone'));
          }}>{t('actionExport')}</Button>
          {/* DD-76: CAPAs originate from control failures / observations.
              Front door is the CONTROL_OBSERVATION intake which gets triaged
              into a finding, which then becomes a CAPA. */}
          <Button variant="primary" Icon={Plus} onClick={() => navigate('/intake/control_observation')}>{t('capaNew')}</Button>
        </>}
      />

      <div className="kpi-grid capa-kpi-strip">
        <Kpi Icon={ListChecks}    label={t('capaKpiOpen')}            value={open.length}                tone="primary" />
        <Kpi Icon={AlertTriangle} label={t('capaKpiOverdue')}         value={overdue.length}             tone="danger" />
        <Kpi Icon={Repeat}        label={t('capaKpiVerificationFail')} value={failed.length}             tone="warning" onClick={() => setFilterStatus('VERIFICATION_FAILED')} active={filterStatus === 'VERIFICATION_FAILED'} />
        <Kpi Icon={Hourglass}     label={t('capaKpiEffectiveness')}    value={effectivenessDue.length}   tone="warning" />
        <Kpi Icon={Workflow}      label={t('capaKpiPreventive')}      value={preventive.length}          tone="info" onClick={() => setFilterType('PREVENTIVE')} active={filterType === 'PREVENTIVE'} />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'register' && criticalNotifs.length > 0 && (
        <div className="capa-alert-banner">
          <AlertTriangle size={18} color="var(--danger-fg)" />
          <div className="capa-alert-banner__body">
            <div className="capa-alert-banner__title">
              {criticalNotifs.length} {t('capaAlertsStripTitle')}
            </div>
            <div className="capa-alert-banner__sub">
              {t('capaAlertsStripBody')}
            </div>
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
            <select value={filterType} onChange={e => setFilterType(e.target.value as any)}
              className="capa-input">
              <option value="ALL">{t('capaFilterTypeAll')}</option>
              <option value="CORRECTIVE">{t('capaFilterTypeCA')}</option>
              <option value="PREVENTIVE">{t('capaFilterTypePA')}</option>
            </select>
            <select value={filterStatus} onChange={e => setFilterStatus(e.target.value as any)}
              className="capa-input">
              <option value="ALL">{t('capaFilterStatusAll')}</option>
              {(Object.keys(CAPA_STATUS_LABEL) as CapaStatus[]).map(s =>
                <option key={s} value={s}>{CAPA_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}</option>)}
            </select>
            <Badge tone="primary">{filtered.length}</Badge>
          </div>}>
          {filtered.length === 0
            ? <EmptyState Icon={ListChecks} title={t('capaEmptyTitle')} sub={t('capaEmptyBody')} />
            : <div className="capa-row-list">
                {filtered.map(c => (
                  <CapaRow key={c.id} c={c} onAdvance={() => quickAdvance(c)} onRca={() => setRcaTarget(c)} />
                ))}
              </div>
          }
        </Card>
      )}

      {tab === 'aging' && <AgingTab items={items} />}
      {tab === 'pareto' && <ParetoTab items={items} />}
      {tab === 'workflow' && <WorkflowTab items={items} />}
      {tab === 'triggers' && (
        <TriggersTab triggers={triggers} onOpenPa={setTriggerTarget} />
      )}
      {tab === 'committee' && (
        <CommitteeTab notifs={notifs} digest={digest} />
      )}

      {rcaTarget && (
        <RcaModal
          c={rcaTarget}
          onClose={() => setRcaTarget(null)}
          update={update}
        />
      )}
      {triggerTarget && (
        <NewPaModal
          trig={triggerTarget}
          onClose={() => setTriggerTarget(null)}
          onCreate={(newPa) => {
            create(newPa);
            toast.success(t('capaPaCreated'));
            setTriggerTarget(null);
          }}
        />
      )}

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

function CapaRow({ c, onAdvance, onRca }: { c: Capa; onAdvance: () => void; onRca: () => void }) {
  const { t, lang } = useI18n();
  const dueIn = daysUntilDue(c);
  const overdue = dueIn < 0 && c.status !== 'CLOSED' && c.status !== 'CANCELLED';
  const title = lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn;

  return (
    <div className="capa-row">
      <div>
        <div className="mono capa-row__code">{c.code}</div>
        <Badge tone={c.type === 'PREVENTIVE' ? 'info' : 'primary'}>{c.type === 'PREVENTIVE' ? 'PA' : 'CA'}</Badge>
      </div>

      <div className="capa-row__title-block">
        <Link to={`/capa/${c.id}`}
              title={t('capaDetailOpenLink')}
              className="capa-row__title-link">
          {title}
        </Link>
        {c.sourceRecordTitle && (
          <div className="capa-row__source">
            {t('capaSourceLabel')}: <span className="capa-row__source-target">{c.sourceRecordTitle}</span>
          </div>
        )}
        {c.parentCapaId && (
          <div className="capa-row__recurrence">
            ↑ {t('capaRecurrenceOf')} {c.parentCapaId}
          </div>
        )}
      </div>

      <Badge tone={CAPA_SEVERITY_TONE[c.severity]}>{c.severity}</Badge>

      <Badge tone={CAPA_STATUS_TONE[c.status]} dot>
        {CAPA_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
      </Badge>

      <div>
        <Progress label={t('capaProgressLabel')} value={c.progressPct} />
      </div>

      <div>
        <div className={`mono capa-row__due${overdue ? ' capa-row__due--overdue' : ''}`}>
          {c.dueDate}
        </div>
        <div className={`capa-row__remaining${overdue ? ' capa-row__remaining--overdue' : ''}`}>
          {overdue ? `${Math.abs(dueIn)}d ${t('capaOverdue')}` : c.status === 'CLOSED' ? t('capaClosed') : `${dueIn}d ${t('capaRemaining')}`}
        </div>
      </div>

      <div className="capa-row__rca-block">
        {c.rcaMethod !== 'NONE' && c.rootCause
          ? <>
              <div className="capa-row__rca-label">{t('capaRootCauseLabel')} · <Badge tone="neutral">{c.rcaMethod.replace('_', '-')}</Badge></div>
              <div className="capa-row__rca-text">{c.rootCause}</div>
            </>
          : <Button variant="ghost" size="sm" Icon={Network} onClick={onRca}>{t('capaStartRca')}</Button>
        }
      </div>

      <div className="capa-row__actions">
        {c.rcaMethod !== 'NONE' && <Button variant="ghost" size="sm" Icon={Network} onClick={onRca}>{t('capaViewRca')}</Button>}
        {CAPA_FORWARD_TRANSITIONS[c.status].length > 0 && (c.status !== 'CLOSED') && (
          <Button variant="primary" size="sm" Icon={Send} onClick={onAdvance}>{t('capaAdvance')}</Button>
        )}
      </div>
    </div>
  );
}

function AgingTab({ items }: { items: Capa[] }) {
  const { t } = useI18n();
  const buckets: Record<string, Capa[]> = { OVERDUE: [], '0-30': [], '31-60': [], '61-90': [], '90+': [] };
  for (const c of items) {
    const b = agingBucket(c);
    if (b === 'CLOSED') continue;
    buckets[b].push(c);
  }
  const order: Array<keyof typeof buckets> = ['OVERDUE', '0-30', '31-60', '61-90', '90+'];
  return (
    <Card title={t('capaTabAging')} Icon={Hourglass}>
      <div className="capa-aging-grid">
        {order.map(b => (
          <div key={b} className="capa-bucket">
            <div className="capa-bucket__head">
              <Badge tone={b === 'OVERDUE' ? 'danger' : b === '90+' ? 'warning' : b === '61-90' ? 'warning' : b === '31-60' ? 'info' : 'success'}>
                {b === 'OVERDUE' ? t('capaAgingOverdue') : `${b} ${t('capaDaysSuffix')}`}
              </Badge>
              <span className="mono capa-bucket__count">{buckets[b].length}</span>
            </div>
            <div className="capa-bucket__list">
              {buckets[b].map(c => (
                <div key={c.id} className="capa-bucket__item">
                  <span className="mono capa-bucket__item-code">{c.code}</span> · {c.severity}
                </div>
              ))}
              {buckets[b].length === 0 && <span className="capa-bucket__empty">—</span>}
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}

function ParetoTab({ items }: { items: Capa[] }) {
  const { t, lang } = useI18n();
  const withCause = items.filter(c => c.rootCauseCategory);
  const top = paretoCauses(withCause, 8);
  const max = top[0]?.count ?? 1;

  return (
    <Card title={t('capaTabPareto')} Icon={BarChart3}
      action={<Badge tone="primary">{withCause.length} {t('capaParetoCausedTotal')}</Badge>}>
      {top.length === 0
        ? <EmptyState Icon={BarChart3} title={t('capaParetoEmpty')} sub={t('capaParetoEmptyBody')} />
        : <div className="capa-pareto-stack">
            {top.map(row => {
              const cat = ROOT_CAUSE_CATS.find(c => c.id === row.category);
              return (
                <div key={row.category} className="capa-pareto-row">
                  <div className="capa-pareto-label">
                    {cat ? (lang === 'ar' ? cat.ar : cat.en) : row.category}
                  </div>
                  <div className="capa-pareto-bar">
                    <div className="capa-pareto-fill" style={{ width: `${(row.count / max) * 100}%` }} />
                  </div>
                  <div className="mono capa-pareto-count">{row.count} ({row.pct}%)</div>
                </div>
              );
            })}
            <div className="capa-footnote">
              {t('capaParetoFootnote')}
            </div>
          </div>
      }
    </Card>
  );
}

function WorkflowTab({ items }: { items: Capa[] }) {
  const { t, lang } = useI18n();
  const stages: CapaStatus[] = ['IDENTIFIED','IMPLEMENTING','OWNER_TESTED','INDEPENDENT_TESTED','AUDIT_SIGNED_OFF','CLOSED'];

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
                  <div key={c.id} className="capa-kanban-card">
                    <div className="mono capa-kanban-card__code">{c.code}</div>
                    <div className="capa-kanban-card__title">
                      {(lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn).slice(0, 60)}
                    </div>
                    <div className="capa-kanban-card__badges">
                      <Badge tone={CAPA_SEVERITY_TONE[c.severity]}>{c.severity}</Badge>
                      <Badge tone={c.type === 'PREVENTIVE' ? 'info' : 'neutral'}>{c.type === 'PREVENTIVE' ? 'PA' : 'CA'}</Badge>
                    </div>
                  </div>
                ))}
                {lane.length === 0 && <div className="capa-kanban-empty">—</div>}
              </div>
            </div>
          );
        })}
      </div>
      <div className="capa-footnote-plain capa-footnote--plain">
        {t('capaWorkflowFootnote')}
      </div>
    </Card>
  );
}

// ── RCA wizard modal ──────────────────────────────────────────────────
// Read + edit a CAPA's root-cause analysis. Demo mode: shows the seeded
// FIVE_WHYS / FISHBONE / PARETO content. New CAPAs can pick a method
// and capture answers; the captured RCA is persisted via useCapas.update.

function RcaModal({ c, onClose, update }: {
  c: Capa;
  onClose: () => void;
  update: (id: string, patch: Partial<Capa>) => void;
}) {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [method, setMethod] = useState<RcaMethod>(c.rcaMethod);
  const [steps, setSteps] = useState<string[]>(c.rcaSteps.length ? c.rcaSteps : initialStepsFor(method));
  const [rootCause, setRootCause] = useState(c.rootCause ?? '');
  const [category, setCategory] = useState<RootCauseCategory>(c.rootCauseCategory ?? 'PROCESS');

  function initialStepsFor(m: RcaMethod): string[] {
    if (m === 'FIVE_WHYS') return ['', '', '', '', ''];
    if (m === 'FISHBONE') return ['', '', '', '', '', ''];
    if (m === 'PARETO') return [''];
    return [];
  }

  function setMethodAndSeed(m: RcaMethod) {
    setMethod(m);
    setSteps(prev => prev.length ? prev : initialStepsFor(m));
  }

  function save() {
    update(c.id, {
      rcaMethod: method,
      rcaSteps: steps,
      rootCause: rootCause.trim(),
      rootCauseCategory: category,
    });
    toast.success(t('capaRcaSaved'));
    onClose();
  }

  const fishboneLabels = ['People', 'Process', 'Technology', 'Materials', 'Environment', 'Management'];
  const fishboneLabelsAr = ['بشري', 'عملية', 'تقنية', 'مواد', 'بيئة', 'إدارة'];

  return (
    <Modal open onClose={onClose} title={`${c.code} — ${t('capaRcaTitle')}`} size="lg"
      footer={<>
        <Button variant="ghost" onClick={onClose}>{t('actionCancel')}</Button>
        <Button variant="primary" Icon={CheckCheck} onClick={save}>{t('actionSave')}</Button>
      </>}>
      <div className="capa-modal-stack">
        <div className="capa-subject-block">
          <div className="capa-modal-eyebrow">{t('capaSubject')}</div>
          <div className="capa-modal-title">
            {lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}
          </div>
          <div className="capa-modal-body">
            {lang === 'ar' && c.descriptionAr ? c.descriptionAr : c.descriptionEn}
          </div>
        </div>

        <div>
          <div className="capa-modal-eyebrow capa-modal-eyebrow--mb">{t('capaRcaMethod')}</div>
          <div className="capa-pill-row">
            {(['FIVE_WHYS', 'FISHBONE', 'PARETO', 'NONE'] as RcaMethod[]).map(m => (
              <button key={m} onClick={() => setMethodAndSeed(m)}
                className={`capa-pill${method === m ? ' is-active' : ''}`}>
                {m === 'FIVE_WHYS' ? t('capaRcaFiveWhys') :
                 m === 'FISHBONE' ? t('capaRcaFishbone') :
                 m === 'PARETO' ? t('capaRcaPareto') : t('capaRcaNone')}
              </button>
            ))}
          </div>
        </div>

        {method === 'FIVE_WHYS' && (
          <div>
            <div className="capa-modal-eyebrow capa-modal-eyebrow--mb">{t('capaRcaFiveWhys')}</div>
            <div className="capa-bucket__list">
              {steps.slice(0, 5).map((s, i) => (
                <div key={i} className="capa-why-row">
                  <Badge tone="primary">{t('capaRcaWhy')} {i + 1}</Badge>
                  <input value={s} onChange={e => setSteps(prev => prev.map((x, j) => j === i ? e.target.value : x))}
                    placeholder={t('capaRcaWhyPh')}
                    className="capa-input capa-input--full" />
                </div>
              ))}
            </div>
          </div>
        )}

        {method === 'FISHBONE' && (
          <div>
            <div className="capa-modal-eyebrow capa-modal-eyebrow--mb">{t('capaRcaFishbone')} (6M)</div>
            <div className="capa-fishbone-grid">
              {fishboneLabels.map((label, i) => (
                <div key={i}>
                  <div className="capa-fishbone-label">
                    {lang === 'ar' ? fishboneLabelsAr[i] : label}
                  </div>
                  <input value={steps[i] ?? ''} onChange={e => setSteps(prev => {
                      const next = [...prev]; while (next.length < 6) next.push(''); next[i] = e.target.value; return next;
                    })}
                    placeholder={t('capaRcaFishbonePh')}
                    className="capa-input capa-input--full" />
                </div>
              ))}
            </div>
          </div>
        )}

        {method === 'PARETO' && (
          <div>
            <div className="capa-modal-eyebrow capa-modal-eyebrow--mb">{t('capaRcaPareto')}</div>
            <textarea value={steps.join('\n')} onChange={e => setSteps(e.target.value.split('\n'))}
              placeholder={t('capaRcaParetoPh')}
              rows={6}
              className="capa-input capa-input--full capa-input--textarea" />
          </div>
        )}

        <div>
          <div className="capa-modal-eyebrow capa-modal-eyebrow--mb">{t('capaRootCauseLabel')}</div>
          <input value={rootCause} onChange={e => setRootCause(e.target.value)}
            placeholder={t('capaRootCausePh')}
            className="capa-input capa-input--full" />
        </div>

        <div>
          <div className="capa-modal-eyebrow capa-modal-eyebrow--mb">{t('capaRcaCategory')}</div>
          <div className="capa-pill-row capa-pill-row--wrap">
            {ROOT_CAUSE_CATS.filter(c => c.id !== 'UNKNOWN').map(cat => (
              <button key={cat.id} onClick={() => setCategory(cat.id)}
                className={`capa-pill capa-pill--xs${category === cat.id ? ' is-active' : ''}`}>
                {lang === 'ar' ? cat.ar : cat.en}
              </button>
            ))}
          </div>
        </div>
      </div>
    </Modal>
  );
}

// ── PA Triggers tab + new-PA modal ────────────────────────────────
// Triggers are derived from KRIs in amber/red, residual-equals-
// inherent open risks, and root-cause categories that account for
// >= 30% of CAPA history. Each trigger row offers a one-click
// "Open PA" action that pre-fills a PREVENTIVE Capa via useCapas.
// create — DD-43.

function TriggersTab({ triggers, onOpenPa }: { triggers: PaTrigger[]; onOpenPa: (t: PaTrigger) => void }) {
  const { t, lang } = useI18n();
  const KIND_ICON: Record<string, typeof Zap> = {
    KRI_BREACH: TrendingUp, RISK_TREND: Activity, RECURRENCE_PATTERN: Repeat,
  };
  const KIND_LABEL: Record<string, { en: string; ar: string }> = {
    KRI_BREACH:         { en: 'KRI breach',          ar: 'تجاوز مؤشر' },
    RISK_TREND:         { en: 'Risk trend',          ar: 'اتجاه الخطر' },
    RECURRENCE_PATTERN: { en: 'Recurrence pattern',  ar: 'نمط تكرار' },
  };

  if (triggers.length === 0) {
    return (
      <Card title={t('capaTabTriggers')} Icon={Zap}>
        <EmptyState Icon={ShieldCheck} title={t('capaTriggersEmpty')} sub={t('capaTriggersEmptyBody')} />
      </Card>
    );
  }

  return (
    <Card title={t('capaTabTriggers')} Icon={Zap}
      action={<Badge tone={triggers.some(t => !t.alreadyCovered) ? 'warning' : 'success'}>
        {triggers.filter(t => !t.alreadyCovered).length} {t('capaTriggersOpen')}
      </Badge>}>
      <div className="capa-trigger-intro">
        {t('capaTriggersIntro')}
      </div>
      <div className="capa-trigger-list">
        {triggers.map(trig => {
          const Icon = KIND_ICON[trig.kind] ?? Zap;
          return (
            <div key={trig.id} className="capa-trigger-row">
              <Icon size={20} strokeWidth={1.8} color="var(--warning-fg)" />
              <Badge tone={trig.kind === 'KRI_BREACH' ? 'warning' : trig.kind === 'RISK_TREND' ? 'info' : 'accent'}>
                {KIND_LABEL[trig.kind][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
              <div className="capa-trigger-main">
                <div className="capa-trigger-title">
                  {lang === 'ar' ? trig.titleAr : trig.titleEn}
                </div>
                <div className="capa-trigger-rationale">
                  {lang === 'ar' ? trig.rationaleAr : trig.rationaleEn}
                </div>
                <div className="capa-trigger-refs">
                  {trig.frameworkRefs.map(ref => <Badge key={ref} tone="neutral">{ref}</Badge>)}
                </div>
              </div>
              <Badge tone={trig.suggestedSeverity === 'CRITICAL' ? 'danger' : trig.suggestedSeverity === 'HIGH' ? 'warning' : 'info'}>
                {trig.suggestedSeverity}
              </Badge>
              <div className="mono capa-trigger-owner">
                {trig.suggestedOwner}
              </div>
              <div className="capa-trigger-actions">
                {trig.alreadyCovered
                  ? <Badge tone="success" dot>{t('capaTriggerCovered')}</Badge>
                  : <Button variant="primary" size="sm" Icon={Plus} onClick={() => onOpenPa(trig)}>{t('capaTriggerOpenPa')}</Button>
                }
              </div>
            </div>
          );
        })}
      </div>
      <div className="capa-footnote--plain">
        {t('capaTriggersFootnote')}
      </div>
    </Card>
  );
}

function NewPaModal({ trig, onClose, onCreate }: {
  trig: PaTrigger;
  onClose: () => void;
  onCreate: (init: Parameters<ReturnType<typeof useCapas>['create']>[0]) => void;
}) {
  const { t, lang } = useI18n();
  const [titleEn, setTitleEn] = useState(`PA — ${trig.titleEn}`);
  const [titleAr, setTitleAr] = useState(`إجراء وقائي — ${trig.titleAr}`);
  const [descriptionEn, setDescriptionEn] = useState(trig.rationaleEn);
  const [owner, setOwner] = useState(trig.suggestedOwner);
  const [severity, setSeverity] = useState<PaTrigger['suggestedSeverity']>(trig.suggestedSeverity);
  // Default due date: 90 days out — typical PA implementation horizon
  const [dueDate, setDueDate] = useState(() => {
    const d = new Date(); d.setDate(d.getDate() + 90); return d.toISOString().slice(0, 10);
  });

  function submit() {
    const today = new Date().toISOString().slice(0, 10);
    onCreate({
      code: `CAPA-${new Date().getFullYear()}-PA-${Math.floor(Math.random() * 900 + 100)}`,
      titleEn, titleAr, descriptionEn,
      type: 'PREVENTIVE',
      source: trig.kind === 'KRI_BREACH' ? 'KRI_BREACH' : trig.kind === 'RISK_TREND' ? 'RISK_TREND' : 'PROCESS_REVIEW',
      sourceRecordKey: trig.sourceRecordKey,
      sourceRecordTitle: trig.sourceRecordTitle,
      frameworkRefs: trig.frameworkRefs,
      severity,
      owner,
      status: 'IDENTIFIED',
      openedAt: today,
      dueDate,
      rcaMethod: 'NONE',
      rcaSteps: [],
    });
  }

  return (
    <Modal open onClose={onClose} title={t('capaNewPaTitle')} size="lg"
      footer={<>
        <Button variant="ghost" onClick={onClose}>{t('actionCancel')}</Button>
        <Button variant="primary" Icon={Plus} onClick={submit}>{t('capaNewPaCreate')}</Button>
      </>}>
      <div className="capa-modal-stack capa-modal-stack--tight">
        <div className="capa-trigger-banner">
          <div className="capa-trigger-banner__head">
            <Zap size={14} /> {t('capaNewPaTriggerLabel')}
          </div>
          <div className="capa-trigger-banner__title">
            {lang === 'ar' ? trig.titleAr : trig.titleEn}
          </div>
          <div className="capa-trigger-banner__body">
            {lang === 'ar' ? trig.rationaleAr : trig.rationaleEn}
          </div>
        </div>

        <Field label={t('capaNewPaTitleEn')} value={titleEn} onChange={setTitleEn} />
        <Field label={t('capaNewPaTitleAr')} value={titleAr} onChange={setTitleAr} />
        <Field label={t('capaNewPaDescription')} value={descriptionEn} onChange={setDescriptionEn} multiline />

        <div className="capa-three-col">
          <Field label={t('capaNewPaOwner')} value={owner} onChange={setOwner} />
          <div>
            <div className="capa-modal-eyebrow capa-modal-eyebrow--mb">{t('capaNewPaSeverity')}</div>
            <div className="capa-severity-row">
              {(['LOW','MEDIUM','HIGH','CRITICAL'] as const).map(s =>
                <button key={s} onClick={() => setSeverity(s)}
                  className={`capa-pill capa-pill--xs${severity === s ? ' is-active' : ''}`}>{s}</button>
              )}
            </div>
          </div>
          <div>
            <div className="capa-modal-eyebrow capa-modal-eyebrow--mb">{t('capaNewPaDueDate')}</div>
            <input type="date" value={dueDate} onChange={e => setDueDate(e.target.value)}
              className="capa-input capa-input--full" />
          </div>
        </div>
      </div>
    </Modal>
  );
}

function Field({ label, value, onChange, multiline }: { label: string; value: string; onChange: (v: string) => void; multiline?: boolean }) {
  return (
    <div>
      <div className="capa-modal-eyebrow capa-modal-eyebrow--mb">{label}</div>
      {multiline
        ? <textarea value={value} onChange={e => onChange(e.target.value)} rows={3}
            className="capa-input capa-input--full capa-input--textarea" />
        : <input value={value} onChange={e => onChange(e.target.value)}
            className="capa-input capa-input--full" />
      }
    </div>
  );
}

// ── Audit Committee tab — derived notifications + quarterly digest ──
// Combines two readouts in a single "tell the Audit Committee" pane:
//   1. The live notification stream (DUE_SOON / OVERDUE / VERIFICATION
//      _FAILED / etc) sorted newest-first
//   2. The current-quarter rollup digest (opened, closed, overdue,
//      recurrent, customer disclosures, top root causes)

function CommitteeTab({ notifs, digest }: {
  notifs: CapaNotif[];
  digest: ReturnType<typeof audCommitteeDigest>;
}) {
  const { t, lang } = useI18n();
  const toast = useToast();

  const KIND_LABEL: Record<string, { en: string; ar: string }> = {
    NEW_CRITICAL:           { en: 'New critical CAPA',          ar: 'إجراء حرج جديد' },
    DUE_SOON:               { en: 'Due in ≤ 7 days',            ar: 'يستحق خلال ≤ 7 أيام' },
    OVERDUE:                { en: 'Overdue',                    ar: 'متأخر' },
    EFFECTIVENESS_OVERDUE:  { en: 'Effectiveness re-test due',  ar: 'استحقاق إعادة اختبار الفاعلية' },
    VERIFICATION_FAILED:    { en: 'Verification failed',        ar: 'فشل التحقق' },
    CUSTOMER_DISCLOSURE:    { en: 'Customer disclosure window', ar: 'نافذة إبلاغ العميل' },
  };
  const KIND_TONE: Record<string, 'danger' | 'warning' | 'info' | 'accent'> = {
    NEW_CRITICAL: 'danger', OVERDUE: 'danger', VERIFICATION_FAILED: 'danger',
    DUE_SOON: 'warning', EFFECTIVENESS_OVERDUE: 'warning',
    CUSTOMER_DISCLOSURE: 'info',
  };

  const ROOT_CAUSE_LABEL: Record<string, { en: string; ar: string }> = {
    PEOPLE: { en: 'People', ar: 'بشري' }, PROCESS: { en: 'Process', ar: 'عملية' },
    TECHNOLOGY: { en: 'Technology', ar: 'تقنية' }, POLICY: { en: 'Policy', ar: 'سياسة' },
    TRAINING: { en: 'Training', ar: 'تدريب' }, EXTERNAL: { en: 'External', ar: 'خارجية' },
    MANAGEMENT: { en: 'Management', ar: 'إدارة' }, UNKNOWN: { en: 'Unknown', ar: 'غير محدد' },
  };

  return (
    <div className="capa-committee-stack">
      {/* Quarterly digest card */}
      <Card title={`${t('capaCommitteeDigest')} — ${digest.quarterLabel}`} Icon={Stamp}
        action={<Button variant="ghost" size="sm" Icon={FileText}
          onClick={() => {
            // Real export: render the digest's per-metric rows as a CSV the
            // audit committee can attach to the quarterly minutes pack.
            const rows: ExportRow[] = [
              { metric: t('capaCommitteeOpened'),               value: String(digest.opened) },
              { metric: t('capaCommitteeClosed'),               value: String(digest.closed) },
              { metric: t('capaCommitteeInFlight'),             value: String(digest.inFlight) },
              { metric: t('capaCommitteeOverdue'),              value: String(digest.overdue) },
              { metric: t('capaCommitteeVerificationFailed'),   value: String(digest.verificationFailed) },
              { metric: t('capaCommitteeRecurrent'),            value: String(digest.recurrent) },
              { metric: t('capaCommitteePreventive'),           value: String(digest.preventive) },
              { metric: t('capaCommitteeCustomerDisclosures'),  value: String(digest.customerDisclosures) },
              { metric: 'quarter',                              value: digest.quarterLabel },
              { metric: 'period_start',                         value: digest.quarterStart },
              { metric: 'period_end',                           value: digest.quarterEnd },
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
          <DigestStat label={t('capaCommitteeOpened')}              value={digest.opened}            tone="info" />
          <DigestStat label={t('capaCommitteeClosed')}              value={digest.closed}            tone="success" />
          <DigestStat label={t('capaCommitteeInFlight')}            value={digest.inFlight}          tone="primary" />
          <DigestStat label={t('capaCommitteeOverdue')}             value={digest.overdue}           tone={digest.overdue > 0 ? 'danger' : 'success'} />
          <DigestStat label={t('capaCommitteeVerificationFailed')}  value={digest.verificationFailed} tone={digest.verificationFailed > 0 ? 'danger' : 'success'} />
          <DigestStat label={t('capaCommitteeRecurrent')}           value={digest.recurrent}         tone={digest.recurrent > 0 ? 'warning' : 'success'} />
          <DigestStat label={t('capaCommitteePreventive')}          value={digest.preventive}        tone="accent" />
          <DigestStat label={t('capaCommitteeCustomerDisclosures')} value={digest.customerDisclosures} tone={digest.customerDisclosures > 0 ? 'warning' : 'success'} />
        </div>
        {digest.topRootCauses.length > 0 && (
          <div className="capa-roots-block">
            <div className="capa-modal-eyebrow capa-roots-block__title">
              {t('capaCommitteeTopRootCauses')}
            </div>
            <div className="capa-roots-list">
              {digest.topRootCauses.map(rc => {
                const lbl = ROOT_CAUSE_LABEL[rc.category];
                return (
                  <div key={rc.category} className="capa-root-chip">
                    <span className="capa-root-chip__label">
                      {lbl ? (lang === 'ar' ? lbl.ar : lbl.en) : rc.category}
                    </span>
                    <Badge tone="primary">{rc.count} ({rc.pct}%)</Badge>
                  </div>
                );
              })}
            </div>
          </div>
        )}
        <div className="capa-footnote">
          {t('capaCommitteeFootnote')}
        </div>
      </Card>

      {/* Live notification stream */}
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
                    <div className="mono capa-stream-code">{n.capaCode}</div>
                    <div className="capa-stream-message">
                      {lang === 'ar' ? n.messageAr : n.messageEn}
                    </div>
                  </div>
                  <span className="mono capa-stream-recipient">{n.recipient}</span>
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

function DigestStat({ label, value, tone }: { label: string; value: number; tone: 'info' | 'success' | 'primary' | 'danger' | 'warning' | 'accent' }) {
  return (
    <div className="capa-digest-stat" data-tone={tone}>
      <div className="mono capa-digest-stat__value">{value}</div>
      <div className="capa-digest-stat__label">{label}</div>
    </div>
  );
}
