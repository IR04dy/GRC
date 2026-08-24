import { useEffect, useMemo, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import {
  Stamp, Calendar, FileText, ClipboardCheck, AlertTriangle,
  CheckCircle2, Download, ThumbsUp, Send, Eye, BookOpen,
  Wrench, Activity, AlertOctagon,
} from 'lucide-react';
import { printPacket, packetTextToSections } from '../../lib/packetPrint';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { reportingApi, ApiError } from '../../lib/api';
import {
  loadAnnualPlan, transitionPlan,
  workingPapersForEngagement, transitionWorkingPaper,
  loadFindings, findingsForEngagement, transitionFinding,
  loadCapas, transitionCapa,
  subscribeAuditLifecycle, aggregateAuditLifecycle,
  auditLifecycleLoadFailed, hydrateAuditLifecycle,
  buildAcPacket, buildAcSheets,
  pickEngagementByCode, registerEngagementCodes,
  createFinding, createWorkingPaper,
  nextFindingStatuses, isFindingOpen, findingStatusLabel,
  nextPlanStatuses, isPlanStatusManaged, planStatusLabel,
  nextWorkingPaperStatuses, isWorkingPaperStatusManaged, workingPaperStatusLabel,
  capaStatusLabel,
  PLAN_STATUS_TONE,
  workingPaperTypeLabel, WP_STATUS_TONE,
  type AnnualPlan, type WorkingPaper,
  type WorkingPaperType, type ManagedAnnualPlanStatus, type ManagedWorkingPaperStatus,
} from '../../lib/auditLifecycle';
import { chainFindingToRiskAndCapa } from '../../lib/lifecycleChains';
// F2-203: `AUDIT_ENGAGEMENTS` (a demoRows() corpus for a DIFFERENT register) used to be
// imported here as the engagement authority. The picker now offers the codes the
// findings and working-paper registers actually reference — see registerEngagementCodes.
import { loadWhiteLabel, subscribeWhiteLabel } from '../../lib/whiteLabel';
import { useRealtimeOrgEvent } from '../../lib/useRealtimeEntity';
import { FindingsTab, CapaTab, PacketTab, FindingsPipeline } from './AuditLifecycleTabs';
import '../../styles/pages/audit-lifecycle.css';

type AlcTab = 'pipeline' | 'plan' | 'workingpapers' | 'findings' | 'capa' | 'packet';

export function AuditLifecyclePage() {
  const { t, lang } = useI18n();
  const toast = useToast();

  const [tab, setTab] = useState<AlcTab>('pipeline');
  const [engagementCode, setEngagementCode] = useState<string>('');
  const [_tick, setTick] = useState(0);
  useEffect(() => subscribeAuditLifecycle(() => setTick(t => t + 1)), []);

  // F2-203: the selectable engagements are the ones the registers reference, so the
  // Working papers / Findings / CAPA tabs cannot be empty for every option — which is
  // exactly what they were, on all eight options, while 20 findings and 12 working
  // papers sat in the tables.
  const engagementCodes = useMemo(() => registerEngagementCodes(), [_tick]);
  useEffect(() => {
    if (!engagementCode && engagementCodes.length > 0) setEngagementCode(engagementCodes[0]);
  }, [engagementCode, engagementCodes]);

  // Sprint 4 §2.7 — backend `audit_lc.FindingService` broadcasts on every
  // finding transition from ANOTHER user. F2-327: a broadcast means the
  // server's copy has moved, so the /api/audit-lc/* caches must be RE-FETCHED
  // (hydrateAuditLifecycle(true)) — a bare setTick only re-renders the stale
  // caches, leaving the subscription inert for cross-user updates (F2-205).
  useRealtimeOrgEvent('changed.audit_finding', () => { void hydrateAuditLifecycle(true).then(() => setTick(t => t + 1)); });

  const stats = useMemo(() => aggregateAuditLifecycle(), [_tick]);
  const plan = useMemo(() => loadAnnualPlan(), [_tick]);
  const loadFailed = useMemo(() => auditLifecycleLoadFailed(), [_tick]);
  const wps = useMemo(() => workingPapersForEngagement(engagementCode), [engagementCode, _tick]);
  const findings = useMemo(() => findingsForEngagement(engagementCode), [engagementCode, _tick]);
  const allFindings = useMemo(() => loadFindings(), [_tick]);
  const allCapas = useMemo(() => loadCapas(), [_tick]);

  const [brand, setBrand] = useState(() => loadWhiteLabel());
  useEffect(() => subscribeWhiteLabel(() => setBrand(loadWhiteLabel())), []);

  /**
   * F2-203: every mutation on this page used to be synchronous and unconditionally
   * followed by a success toast, over a store that wrote nothing. A write that fails
   * must say so — and must not leave the screen showing a state the server rejected,
   * which is why each caller re-reads from the store the handler updated.
   */
  async function run(work: () => Promise<unknown>, okMsg: string) {
    try {
      await work();
      toast.success(okMsg);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (lang === 'ar' ? 'تعذّر الحفظ' : 'Could not save'));
    }
  }

  function downloadPacket() {
    const text = buildAcPacket(brand.organisationNameEn, brand.organisationNameAr, getQuarter());
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `AC-Packet-${getQuarter()}.txt`;
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast.success(t('alcPacketDownloadedToast'));
  }

  function printAcPdf() {
    const text = buildAcPacket(brand.organisationNameEn, brand.organisationNameAr, getQuarter());
    printPacket({
      titleEn: 'Audit Committee Executive Packet',
      titleAr: 'حزمة لجنة المراجعة التنفيذية',
      eyebrowEn: 'CMA Article 95 · Quarterly',
      eyebrowAr: 'المادة 95 من حوكمة الشركات · فصلي',
      referenceId: `AC-${getQuarter()}`,
      classificationEn: 'CONFIDENTIAL — AUDIT COMMITTEE',
      classificationAr: 'سرّي — لجنة المراجعة',
      sections: packetTextToSections(text),
    }, lang);
  }

  async function exportAc(kind: 'xlsx' | 'docx') {
    const payload = { module: 'CMA', exportType: 'AuditCommitteePacket', lang, sheets: buildAcSheets() };
    try {
      await (kind === 'xlsx' ? reportingApi.packetXlsx(payload) : reportingApi.packetDocx(payload));
      toast.success(lang === 'ar' ? 'تم التصدير' : 'Exported');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (lang === 'ar' ? 'تعذّر التصدير' : 'Export failed'));
    }
  }

  return (
    <Shell title={t('alcTitle')} sub={t('alcSub')}>
      <PageHeader
        eyebrow={t('alcEyebrow')}
        title={t('alcTitle')}
        sub={t('alcPageSub')}
        actions={
          <>
            <Button variant="primary" onClick={printAcPdf}>{t('alcPrintAcPdf')}</Button>
            <Button variant="secondary" Icon={Download} onClick={() => exportAc('xlsx')}>{lang === 'ar' ? 'تصدير Excel' : 'Export Excel'}</Button>
            <Button variant="secondary" Icon={Download} onClick={() => exportAc('docx')}>{lang === 'ar' ? 'تصدير Word' : 'Export Word'}</Button>
            <Button variant="ghost" Icon={Download} onClick={downloadPacket}>{t('alcDownloadPacket')}</Button>
          </>
        }
      />

      {loadFailed && (
        <Card title={lang === 'ar' ? 'تعذّر تحميل سجلّات التدقيق' : 'Audit registers could not be loaded'} Icon={AlertOctagon}>
          <div className="alc-card-sub">
            {lang === 'ar'
              ? 'فشل تحميل واحد أو أكثر من سجلّات التدقيق من الخادم. الأرقام أدناه غير مكتملة — أعد المحاولة قبل الاعتماد عليها.'
              : 'One or more audit registers failed to load from the server. The figures below are incomplete — retry before relying on them.'}
          </div>
          <Button size="sm" variant="secondary" onClick={() => void hydrateAuditLifecycle(true)}>
            {lang === 'ar' ? 'إعادة المحاولة' : 'Retry'}
          </Button>
        </Card>
      )}

      <div className="kpi-grid mb-lg">
        <Kpi Icon={Stamp}             label={t('alcKpiEngs')}           value={String(stats.totalEngagements)}     tone="primary" onClick={() => setTab('plan')} />
        <Kpi Icon={ClipboardCheck}     label={t('alcKpiInFlight')}        value={String(stats.engagementsInFlight)}  tone="info" onClick={() => setTab('plan')} />
        <Kpi Icon={FileText}           label={t('alcKpiWps')}             value={String(stats.workingPapersTotal)}   tone="info" onClick={() => setTab('workingpapers')} />
        <Kpi Icon={Eye}                 label={t('alcKpiWpsReview')}        value={String(stats.workingPapersInReview)} tone="warning" onClick={() => setTab('workingpapers')} />
        <Kpi Icon={AlertTriangle}      label={t('alcKpiFindings')}        value={String(stats.findingsOpen)}          tone={stats.findingsOpen > 0 ? 'warning' : 'success'} to="/issues" />
        <Kpi Icon={AlertTriangle}      label={t('alcKpiCritical')}        value={String(stats.findingsCritical)}      tone={stats.findingsCritical > 0 ? 'danger' : 'success'} to="/issues" />
        <Kpi Icon={Wrench}              label={t('alcKpiCapaClosed')}      value={`${stats.capaClosed} / ${stats.capaTotal}`} tone="info" to="/capa" />
        <Kpi Icon={CheckCircle2}        label={t('alcKpiClosure')}         value={`${stats.closureRate}%`}              tone={stats.closureRate >= 80 ? 'success' : 'warning'} to="/capa" />
      </div>

      {/* Tabs */}
      <div className="alc-tab-row">
        {([
          { id: 'pipeline',      labelKey: 'lpcTabPipeline',     Icon: Activity },
          { id: 'plan',          labelKey: 'alcTabPlan',         Icon: Calendar },
          { id: 'workingpapers', labelKey: 'alcTabWp',           Icon: FileText },
          { id: 'findings',      labelKey: 'alcTabFindings',     Icon: AlertTriangle },
          { id: 'capa',          labelKey: 'alcTabCapa',         Icon: Wrench },
          { id: 'packet',        labelKey: 'alcTabPacket',       Icon: BookOpen },
        ] as { id: AlcTab; labelKey: string; Icon: typeof Stamp }[]).map(tabDef => (
          <button key={tabDef.id} onClick={() => setTab(tabDef.id)}
            className={`alc-tab-btn${tab === tabDef.id ? ' alc-tab-btn--on' : ''}`}>
            <tabDef.Icon size={13} />
            {t(tabDef.labelKey)}
          </button>
        ))}
      </div>

      {tab === 'pipeline' && (
        <FindingsPipeline
          allFindings={allFindings}
          onAdvance={(findingId, fromStatus) => {
            // F2-203: this was a fourth copy of the machine, written as a lookup map.
            // Ask the one table instead — it is pinned against FindingService.ALLOWED.
            const target = nextFindingStatuses(fromStatus)[0];
            if (!target) {
              toast.info(lang === 'ar'
                ? `لا يوجد انتقال مُعلَن من الحالة ${fromStatus}`
                : `No transition is declared out of ${fromStatus}`);
              return;
            }
            void run(() => transitionFinding(findingId, target), t('lpcAdvancedToast'));
          }}
          onCardClick={(findingId) => {
            // Switch to findings tab + select the engagement that owns it.
            const f = allFindings.find(x => x.id === findingId);
            if (f) {
              setEngagementCode(f.engagementCode);
              setTab('findings');
            }
          }}
        />
      )}

      {/* F2-203: one handler taking the target from the table, replacing three buttons each
          gated on a hard-coded `plan.status === …`. The plan the organisation is executing
          holds APPROVED, which the machine has no edge out of, so all three were dead on it —
          and `PLAN_STATUS_LABEL[plan.status][lang]` crashed the tab outright. No approver
          argument either: the server stamps the authenticated principal (SEC-IAM-002), and
          this used to pass an invented name and display it. */}
      {tab === 'plan' && <PlanTab plan={plan}
        onTransition={(to) => void run(() => transitionPlan(to),
          `${t('alcStatus')}: ${planStatusLabel(to, lang)}`)} />}

      {(tab === 'workingpapers' || tab === 'findings' || tab === 'capa') && (
        <EngagementPicker codes={engagementCodes} code={engagementCode} onChange={setEngagementCode} />
      )}

      {/* F2-203: same shape. No reviewer/author arguments — the author one was worse than a
          display bug, because the server saved that invented name. */}
      {tab === 'workingpapers' && <WpTab wps={wps} engagementCode={engagementCode}
        onTransition={(wp, to) => void run(() => transitionWorkingPaper(wp.id, to),
          `${t('alcStatus')}: ${workingPaperStatusLabel(to, lang)}`)}
        onCreate={(type, titleEn, titleAr, hours) =>
          void run(() => createWorkingPaper(engagementCode, type, titleEn, titleAr, hours), t('alcWpCreatedToast'))} />}

      {tab === 'findings' && <FindingsTab findings={findings} engagementCode={engagementCode}
        onTransition={(f, status) =>
          void run(() => transitionFinding(f.id, status), `${t('alcStatus')}: ${findingStatusLabel(status, lang)}`)}
        onCreate={(titleEn, titleAr, severity, conditionEn, conditionAr, ownerRole) =>
          void run(() => createFinding(engagementCode, titleEn, titleAr, severity, conditionEn, conditionAr, ownerRole),
                   t('alcFindingCreatedToast'))}
        /* F2-204: the chain no longer creates a treatment plan. It used a hard-coded risk id,
           and now that the treatment-plan store POSTs, that would PERSIST a plan against
           whatever risk is id 1 in the tenant. Only the CAPA half is a real write. */
        onChainToRisk={(f) => {
          const { capaId } = chainFindingToRiskAndCapa(f);
          toast.success(`${t('alcChainedToast')}: ${capaId}`);
        }} />}

      {tab === 'capa' && <CapaTab findings={findings} allCapas={allCapas}
        onTransition={(c, status) => { transitionCapa(c.id, status); toast.info(`${t('alcStatus')}: ${capaStatusLabel(status, lang)}`); }} />}


      {tab === 'packet' && <PacketTab plan={plan} findings={allFindings} capas={allCapas} stats={stats} />}
    </Shell>
  );
}

function getQuarter(): string {
  const m = new Date().getMonth();
  const q = Math.floor(m / 3) + 1;
  return `Q${q}-${new Date().getFullYear()}`;
}

// ─── Engagement picker ──────────────────────────────────────────────────────

function EngagementPicker({ codes, code, onChange }: {
  codes: string[]; code: string; onChange: (c: string) => void;
}) {
  const { t, lang } = useI18n();
  if (codes.length === 0) {
    return (
      <Card title={t('alcPickEngagement')} Icon={Stamp}>
        <InlineEmpty>
          {lang === 'ar'
            ? 'لا يوجد ارتباط مُشار إليه في أي ورقة عمل أو ملاحظة في هذا السجلّ.'
            : 'No engagement is referenced by any working paper or finding in this register.'}
        </InlineEmpty>
      </Card>
    );
  }
  return (
    <Card title={t('alcPickEngagement')} Icon={Stamp}>
      <div className="alc-pick-row">
        {codes.map(c => {
          // The real catalogue supplies a title WHEN the code is in it. It is a separate
          // register with its own code space, so most codes resolve to nothing — and the
          // code itself is then all the platform honestly knows about the engagement.
          const known = pickEngagementByCode(c);
          const title = known ? (lang === 'ar' && known.titleAr ? known.titleAr : known.titleEn) : '';
          return (
            <button key={c} onClick={() => onChange(c)}
              className={`alc-pick-chip${c === code ? ' alc-pick-chip--on' : ''}`}>
              <span className="mono alc-pick-chip-code">{c}</span>
              {title && <span className="alc-pick-chip-title">{title}</span>}
              {known?.status && <Badge tone={isFindingOpen(known.status) ? 'info' : 'success'}>{known.status}</Badge>}
            </button>
          );
        })}
      </div>
    </Card>
  );
}

// ─── Plan tab ───────────────────────────────────────────────────────────────

/** Keyed on the TARGET state, so a new edge surfaces as a missing key, not a missing button. */
const PLAN_ADVANCE_LABEL_KEY: Record<ManagedAnnualPlanStatus, string> = {
  DRAFT:       'alcStatus',
  AC_REVIEW:   'alcSubmitForAc',
  AC_APPROVED: 'alcAcApprove',
  EXECUTING:   'alcStartExec',
  CLOSED:      'alcStatus',
};

function PlanTab({ plan, onTransition }: {
  plan: AnnualPlan | null;
  onTransition: (to: ManagedAnnualPlanStatus) => void;
}) {
  const { t, lang } = useI18n();
  // F2-203: an empty register used to be filled with SEED_PLAN — an invented Audit
  // Committee chair, an invented approval date, and a green "AC approved" badge. Saying
  // nothing is recorded is the only honest thing to show here (F2-197).
  if (!plan) {
    return (
      <Card title={t('alcPlanTitle')} Icon={Calendar}>
        <InlineEmpty>
          {lang === 'ar'
            ? 'لا توجد خطة تدقيق سنوية مُسجَّلة لهذه الجهة.'
            : 'No annual audit plan is recorded for this organisation.'}
        </InlineEmpty>
      </Card>
    );
  }
  const quarters = [
    { label: 'Q1', codes: plan.q1 },
    { label: 'Q2', codes: plan.q2 },
    { label: 'Q3', codes: plan.q3 },
    { label: 'Q4', codes: plan.q4 },
  ];
  return (
    <Card title={t('alcPlanTitle')} Icon={Calendar}>
      <div className="alc-plan-head">
        <div>
          <div className="alc-plan-badges">
            <span className="mono alc-plan-id">{plan.id}</span>
            <Badge tone={PLAN_STATUS_TONE[plan.status] ?? 'neutral'}>{planStatusLabel(plan.status, lang)}</Badge>
            {!isPlanStatusManaged(plan.status) && (
              <span role="status">
                <Badge tone="warning">
                  {lang === 'ar' ? 'لا يوجد انتقال مُعلَن' : 'No declared transition'}
                </Badge>
              </span>
            )}
            <Badge tone="info">{plan.year}</Badge>
            <Badge tone="primary">{plan.budgetHours.toLocaleString()} {t('alcHours')}</Badge>
          </div>
          {plan.acApprovedBy && (
            <div className="alc-plan-approved">
              {t('alcApprovedBy')}: <b>{plan.acApprovedBy}</b>
              {plan.acApprovedAt && <> · <span className="mono">{plan.acApprovedAt.slice(0, 10)}</span></>}
            </div>
          )}
        </div>
        <div className="alc-plan-actions">
          {nextPlanStatuses(plan.status).map(to => (
            <Button key={to} variant="primary"
                    Icon={to === 'AC_REVIEW' ? Send : to === 'AC_APPROVED' ? ThumbsUp : undefined}
                    onClick={() => onTransition(to)}>
              {t(PLAN_ADVANCE_LABEL_KEY[to])}
            </Button>
          ))}
        </div>
      </div>

      {plan.acNotesEn && (
        <div className="alc-ac-notes">
          <div className="alc-ac-notes-label">
            {t('alcAcNotes')}
          </div>
          {lang === 'ar' ? plan.acNotesAr : plan.acNotesEn}
        </div>
      )}

      <div className="alc-quarter-grid">
        {quarters.map(q => (
          <div key={q.label} className="alc-quarter-card">
            {/* F2-203: this was the literal `2026`, so the 2027 plan's quarters were
                labelled 2026 — a hard-coded year beside a real one from the same row. */}
            <div className="alc-quarter-title">{q.label} {plan.year}</div>
            {q.codes.length === 0
              ? <InlineEmpty>—</InlineEmpty>
              : q.codes.map(code => {
                  // F2-203: `if (!e) return null` dropped the row, so a quarter with
                  // three scheduled engagements rendered as an empty card — and the
                  // annual plan's codes (ENG-CYBER-2026Q1…) are in none of the five
                  // engagement code spaces, so that was every quarter of both plans.
                  const e = pickEngagementByCode(code);
                  const title = e ? (lang === 'ar' && e.titleAr ? e.titleAr : e.titleEn) : '';
                  return (
                    <div key={code} className="alc-quarter-row">
                      <div className="mono alc-quarter-row-code">{code}</div>
                      {title && <div className="alc-quarter-row-title">{title}</div>}
                    </div>
                  );
                })}
          </div>
        ))}
      </div>
    </Card>
  );
}

// ─── Working papers tab ─────────────────────────────────────────────────────

const WP_ADVANCE_LABEL_KEY: Record<ManagedWorkingPaperStatus, string> = {
  DRAFT:       'alcWpSendReview',
  PEER_REVIEW: 'alcWpSendReview',
  APPROVED:    'alcApprove',
  REJECTED:    'alcReject',
};

function WpTab({ wps, engagementCode: _ec, onTransition, onCreate }: {
  wps: WorkingPaper[];
  engagementCode: string;
  onTransition: (w: WorkingPaper, to: ManagedWorkingPaperStatus) => void;
  onCreate: (type: WorkingPaperType, titleEn: string, titleAr: string, hours: number) => void;
}) {
  const { t, lang } = useI18n();
  return (
    <div className="alc-section">
      <Card title={t('alcWpTitle')} Icon={FileText}
        action={<Button size="sm" variant="primary" onClick={() => onCreate('TEST', 'New test working paper', 'ورقة عمل اختبار جديدة', 4)}>{t('alcWpCreate')}</Button>}>
        <div className="alc-card-sub">
          {t('alcWpSub')}
        </div>
        {wps.length === 0
          ? <InlineEmpty>{t('alcWpEmpty')}</InlineEmpty>
          : wps.map(wp => (
              <div key={wp.id} className="alc-item">
                <div className="alc-item-head">
                  <div className="alc-item-main">
                    <div className="alc-item-badges">
                      <span className="mono alc-item-code">{wp.refNumber}</span>
                      <Badge tone={WP_STATUS_TONE[wp.status] ?? 'neutral'}>{workingPaperStatusLabel(wp.status, lang)}</Badge>
                      {!isWorkingPaperStatusManaged(wp.status) && (
                        <span role="status">
                          <Badge tone="warning">
                            {lang === 'ar' ? 'لا يوجد انتقال مُعلَن' : 'No declared transition'}
                          </Badge>
                        </span>
                      )}
                      <Badge tone="info">{workingPaperTypeLabel(wp.type, lang)}</Badge>
                      <Badge tone="neutral">{wp.hours}h</Badge>
                    </div>
                    <div className="alc-item-title">
                      {lang === 'ar' ? wp.titleAr : wp.titleEn}
                    </div>
                    <div className="alc-item-sub">
                      {t('alcAuthor')}: <b>{wp.author}</b>
                      {wp.reviewer && <> · {t('alcReviewer')}: <b>{wp.reviewer}</b></>}
                      {wp.reviewedAt && <> · <span className="mono">{wp.reviewedAt.slice(0, 10)}</span></>}
                    </div>
                  </div>
                  <div className="alc-item-actions">
                    {/* F2-203: from the table. 8 of the 12 working papers hold REVIEWED, which
                        WP_TRANSITIONS has no edge out of, so both hard-coded buttons were dead
                        on two thirds of the register with nothing saying why. */}
                    {nextWorkingPaperStatuses(wp.status).map(to => (
                      <Button key={to} size="sm"
                              variant={to === 'REJECTED' ? 'ghost' : 'primary'}
                              Icon={to === 'PEER_REVIEW' ? Send : to === 'APPROVED' ? ThumbsUp : undefined}
                              onClick={() => onTransition(wp, to)}>
                        {t(WP_ADVANCE_LABEL_KEY[to])}
                      </Button>
                    ))}
                  </div>
                </div>
                <div className="alc-field-grid">
                  <div>
                    <div className="alc-field-label">
                      {t('alcWpProcedure')}
                    </div>
                    <div className="alc-field-value">
                      {lang === 'ar' ? wp.procedureAr : wp.procedureEn}
                    </div>
                  </div>
                  <div>
                    <div className="alc-field-label">
                      {t('alcWpConclusion')}
                    </div>
                    <div className="alc-field-value">
                      {lang === 'ar' ? wp.conclusionAr : wp.conclusionEn}
                    </div>
                  </div>
                </div>
                {wp.controlsCovered.length > 0 && (
                  <div className="alc-controls-row">
                    <span className="alc-controls-label">{t('alcWpControls')}:</span>
                    {wp.controlsCovered.map(c => <Badge key={c} tone="info">{c}</Badge>)}
                  </div>
                )}
              </div>
            ))
        }
      </Card>
    </div>
  );
}
