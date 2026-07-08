import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import {
  Stamp, Calendar, FileText, ClipboardCheck, AlertTriangle,
  CheckCircle2, Download, ThumbsUp, Send, Eye, BookOpen,
  Wrench, Activity, AlertOctagon, GitBranch,
} from 'lucide-react';
import { LifecyclePipeline, type PipelineStage, type PipelineCard, dueLabel } from '../../components/lifecycle/LifecyclePipeline';
import { printPacket, packetTextToSections } from '../../lib/packetPrint';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  loadAnnualPlan, transitionPlan,
  workingPapersForEngagement, transitionWorkingPaper,
  loadFindings, findingsForEngagement, transitionFinding,
  loadCapas, transitionCapa,
  subscribeAuditLifecycle, aggregateAuditLifecycle,
  buildAcPacket,
  pickEngagementByCode,
  createFinding, createWorkingPaper,
  PLAN_STATUS_LABEL, PLAN_STATUS_TONE,
  WP_TYPE_LABEL, WP_STATUS_TONE,
  SEVERITY_TONE, FINDING_STATUS_LABEL,
  CAPA_STATUS_TONE, CAPA_STATUS_LABEL,
  type AnnualPlan, type WorkingPaper, type Finding, type CapaTask,
  type FindingSeverity, type WorkingPaperType,
} from '../../lib/auditLifecycle';
import { chainFindingToRiskAndCapa } from '../../lib/lifecycleChains';
import { AUDIT_ENGAGEMENTS } from '../compliance/mockCompliance';
import { loadWhiteLabel, subscribeWhiteLabel } from '../../lib/whiteLabel';
import { useRealtimeOrgEvent } from '../../lib/useRealtimeEntity';
import '../../styles/pages/audit-lifecycle.css';

type AlcTab = 'pipeline' | 'plan' | 'workingpapers' | 'findings' | 'capa' | 'packet';

export function AuditLifecyclePage() {
  const { t, lang } = useI18n();
  const toast = useToast();

  const [tab, setTab] = useState<AlcTab>('pipeline');
  const [engagementCode, setEngagementCode] = useState<string>(AUDIT_ENGAGEMENTS[0]?.engagementCode ?? '');
  const [_tick, setTick] = useState(0);
  useEffect(() => subscribeAuditLifecycle(() => setTick(t => t + 1)), []);

  // Sprint 4 §2.7 — backend `audit_lc.FindingService` broadcasts on every
  // finding transition. Tick the same counter so memoised data re-derives
  // for either local or remote events.
  useRealtimeOrgEvent('changed.audit_finding', () => setTick(t => t + 1));

  const stats = useMemo(() => aggregateAuditLifecycle(), [_tick]);
  const plan = useMemo(() => loadAnnualPlan(), [_tick]);
  const wps = useMemo(() => workingPapersForEngagement(engagementCode), [engagementCode, _tick]);
  const findings = useMemo(() => findingsForEngagement(engagementCode), [engagementCode, _tick]);
  const allFindings = useMemo(() => loadFindings(), [_tick]);
  const allCapas = useMemo(() => loadCapas(), [_tick]);

  const [brand, setBrand] = useState(() => loadWhiteLabel());
  useEffect(() => subscribeWhiteLabel(() => setBrand(loadWhiteLabel())), []);

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

  return (
    <Shell title={t('alcTitle')} sub={t('alcSub')}>
      <PageHeader
        eyebrow={t('alcEyebrow')}
        title={t('alcTitle')}
        sub={t('alcPageSub')}
        actions={
          <>
            <Button variant="secondary" Icon={Download} onClick={downloadPacket}>{t('alcDownloadPacket')}</Button>
            <Button variant="primary" onClick={printAcPdf}>{t('alcPrintAcPdf')}</Button>
          </>
        }
      />

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
            const next: Record<string, Finding['status']> = {
              OPEN: 'MGMT_RESPONSE',
              MGMT_RESPONSE: 'IN_REMEDIATION',
              IN_REMEDIATION: 'AWAITING_VERIFICATION',
              AWAITING_VERIFICATION: 'CLOSED',
            };
            const target = next[fromStatus];
            if (!target) return;
            transitionFinding(findingId, target);
            toast.success(t('lpcAdvancedToast'));
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

      {tab === 'plan' && <PlanTab plan={plan}
        onSubmitForReview={() => { transitionPlan('AC_REVIEW'); toast.info(t('alcPlanSubmittedToast')); }}
        onApprove={() => { transitionPlan('AC_APPROVED', 'Khalid Al-Falih (AC Chair)'); toast.success(t('alcPlanApprovedToast')); }}
        onExecute={() => { transitionPlan('EXECUTING'); toast.info(t('alcPlanExecutingToast')); }} />}

      {(tab === 'workingpapers' || tab === 'findings' || tab === 'capa') && (
        <EngagementPicker code={engagementCode} onChange={setEngagementCode} />
      )}

      {tab === 'workingpapers' && <WpTab wps={wps} engagementCode={engagementCode}
        onPeerReview={(wp) => { transitionWorkingPaper(wp.id, 'PEER_REVIEW'); toast.info(t('alcWpReviewToast')); }}
        onApprove={(wp) => { transitionWorkingPaper(wp.id, 'APPROVED', 'Sara Al-Zahrani (Audit Manager)'); toast.success(t('alcWpApprovedToast')); }}
        onReject={(wp) => { transitionWorkingPaper(wp.id, 'REJECTED'); toast.danger(t('alcWpRejectedToast')); }}
        onCreate={(type, titleEn, titleAr, hours) => { createWorkingPaper(engagementCode, type, titleEn, titleAr, 'Faisal Al-Otaibi (Auditor)', hours); toast.success(t('alcWpCreatedToast')); }} />}

      {tab === 'findings' && <FindingsTab findings={findings} engagementCode={engagementCode}
        onTransition={(f, status) => { transitionFinding(f.id, status); toast.info(`${t('alcStatus')}: ${FINDING_STATUS_LABEL[status][lang]}`); }}
        onCreate={(titleEn, titleAr, severity, conditionEn, conditionAr) => {
          createFinding(engagementCode, titleEn, titleAr, severity, conditionEn, conditionAr, 'Owner role');
          toast.success(t('alcFindingCreatedToast'));
        }}
        onChainToRisk={(f) => {
          const { planId, capaId } = chainFindingToRiskAndCapa(f);
          toast.success(`${t('alcChainedToast')}: ${planId} + ${capaId}`);
        }} />}

      {tab === 'capa' && <CapaTab findings={findings} allCapas={allCapas}
        onTransition={(c, status) => { transitionCapa(c.id, status); toast.info(`${t('alcStatus')}: ${CAPA_STATUS_LABEL[status][lang]}`); }} />}

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

function EngagementPicker({ code, onChange }: { code: string; onChange: (c: string) => void }) {
  const { t, lang } = useI18n();
  return (
    <Card title={t('alcPickEngagement')} Icon={Stamp}>
      <div className="alc-pick-row">
        {AUDIT_ENGAGEMENTS.slice(0, 8).map(e => {
          const isOn = e.engagementCode === code;
          const title = lang === 'ar' && e.titleAr ? e.titleAr : e.titleEn;
          return (
            <button key={e.engagementCode ?? String(e.id)} onClick={() => onChange(e.engagementCode ?? String(e.id))}
              className={`alc-pick-chip${isOn ? ' alc-pick-chip--on' : ''}`}>
              <span className="mono alc-pick-chip-code">{e.engagementCode}</span>
              <span className="alc-pick-chip-title">
                {title}
              </span>
              <Badge tone={e.status === 'FIELDWORK' ? 'warning' : e.status === 'PLANNED' ? 'info' : e.status === 'CLOSED' ? 'success' : 'primary'}>
                {e.status}
              </Badge>
            </button>
          );
        })}
      </div>
    </Card>
  );
}

// ─── Plan tab ───────────────────────────────────────────────────────────────

function PlanTab({ plan, onSubmitForReview, onApprove, onExecute }: {
  plan: AnnualPlan;
  onSubmitForReview: () => void;
  onApprove: () => void;
  onExecute: () => void;
}) {
  const { t, lang } = useI18n();
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
            <Badge tone={PLAN_STATUS_TONE[plan.status]}>{PLAN_STATUS_LABEL[plan.status][lang]}</Badge>
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
          {plan.status === 'DRAFT' && <Button variant="primary" Icon={Send} onClick={onSubmitForReview}>{t('alcSubmitForAc')}</Button>}
          {plan.status === 'AC_REVIEW' && <Button variant="primary" Icon={ThumbsUp} onClick={onApprove}>{t('alcAcApprove')}</Button>}
          {plan.status === 'AC_APPROVED' && <Button variant="primary" onClick={onExecute}>{t('alcStartExec')}</Button>}
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
            <div className="alc-quarter-title">{q.label} 2026</div>
            {q.codes.length === 0
              ? <InlineEmpty>—</InlineEmpty>
              : q.codes.map(code => {
                  const e = pickEngagementByCode(code);
                  if (!e) return null;
                  const title = lang === 'ar' && e.titleAr ? e.titleAr : e.titleEn;
                  return (
                    <div key={code} className="alc-quarter-row">
                      <div className="mono alc-quarter-row-code">{code}</div>
                      <div className="alc-quarter-row-title">{title}</div>
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

function WpTab({ wps, engagementCode: _ec, onPeerReview, onApprove, onReject, onCreate }: {
  wps: WorkingPaper[];
  engagementCode: string;
  onPeerReview: (w: WorkingPaper) => void;
  onApprove: (w: WorkingPaper) => void;
  onReject: (w: WorkingPaper) => void;
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
                      <Badge tone={WP_STATUS_TONE[wp.status]}>{wp.status}</Badge>
                      <Badge tone="info">{WP_TYPE_LABEL[wp.type][lang]}</Badge>
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
                    {wp.status === 'DRAFT' && <Button size="sm" variant="primary" Icon={Send} onClick={() => onPeerReview(wp)}>{t('alcWpSendReview')}</Button>}
                    {wp.status === 'PEER_REVIEW' && (
                      <>
                        <Button size="sm" variant="primary" Icon={ThumbsUp} onClick={() => onApprove(wp)}>{t('alcApprove')}</Button>
                        <Button size="sm" variant="ghost" onClick={() => onReject(wp)}>{t('alcReject')}</Button>
                      </>
                    )}
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

// ─── Findings tab ───────────────────────────────────────────────────────────

function FindingsTab({ findings, engagementCode: _ec, onTransition, onCreate, onChainToRisk }: {
  findings: Finding[];
  engagementCode: string;
  onTransition: (f: Finding, status: Finding['status']) => void;
  onCreate: (titleEn: string, titleAr: string, severity: FindingSeverity, conditionEn: string, conditionAr: string) => void;
  onChainToRisk: (f: Finding) => void;
}) {
  const { t, lang } = useI18n();
  const navigate = useNavigate();
  return (
    <div className="alc-section">
      <Card title={t('alcFindingTitle')} Icon={AlertTriangle}
        action={<Button size="sm" variant="primary" onClick={() => onCreate('New finding', 'ملاحظة جديدة', 'MEDIUM', 'Condition observed during fieldwork.', 'حالة مرصودة أثناء العمل الميداني.')}>{t('alcFindingCreate')}</Button>}>
        <div className="alc-card-sub">
          {t('alcFindingSub')}
        </div>
        {findings.length === 0
          ? <InlineEmpty>{t('alcFindingEmpty')}</InlineEmpty>
          : findings.map(f => (
              <div key={f.id} className="alc-item">
                <div className="alc-item-head">
                  <div className="alc-item-main">
                    <div className="alc-item-badges">
                      <span className="mono alc-item-code">{f.refNumber}</span>
                      <Badge tone={SEVERITY_TONE[f.severity]}>{f.severity}</Badge>
                      <Badge tone={f.status === 'CLOSED' ? 'success' : f.status === 'OPEN' ? 'danger' : 'warning'}>
                        {FINDING_STATUS_LABEL[f.status][lang]}
                      </Badge>
                      {f.targetDate && <Badge tone="info">{t('alcDue')}: <span className="mono">{f.targetDate}</span></Badge>}
                    </div>
                    <div className="alc-item-title--strong">
                      {lang === 'ar' ? f.titleAr : f.titleEn}
                    </div>
                    <div className="alc-item-sub--plain">
                      {f.ownerRole && <>{t('alcOwner')}: <b>{f.ownerRole}</b></>}
                    </div>
                  </div>
                  <div className="alc-item-actions alc-item-actions--wrap">
                    {/* Sprint 11 — open the Risk-Control-Finding pivot view in a new tab. */}
                    <Button size="sm" variant="ghost" Icon={GitBranch}
                            onClick={() => navigate(`/governance/pivot/finding/${f.id}`)}>
                      {lang === 'ar' ? 'محور' : 'Pivot'}
                    </Button>
                    {f.status === 'OPEN' && <Button size="sm" variant="primary" onClick={() => onTransition(f, 'MGMT_RESPONSE')}>{t('alcAwaitMgmt')}</Button>}
                    {f.status === 'MGMT_RESPONSE' && <Button size="sm" variant="primary" onClick={() => onTransition(f, 'IN_REMEDIATION')}>{t('alcStartRemediation')}</Button>}
                    {f.status === 'IN_REMEDIATION' && <Button size="sm" variant="primary" onClick={() => onTransition(f, 'AWAITING_VERIFICATION')}>{t('alcAwaitVerif')}</Button>}
                    {f.status === 'AWAITING_VERIFICATION' && <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onTransition(f, 'CLOSED')}>{t('alcCloseFinding')}</Button>}
                    {(f.severity === 'CRITICAL' || f.severity === 'HIGH') && f.status !== 'CLOSED' && (
                      <Button size="sm" variant="ghost" onClick={() => onChainToRisk(f)}>{t('alcChainToRisk')}</Button>
                    )}
                  </div>
                </div>

                {/* Crosswalk */}
                {f.controlCrosswalk.length > 0 && (
                  <div className="alc-crosswalk">
                    <span className="alc-controls-label">{t('alcCrosswalk')}:</span>
                    {f.controlCrosswalk.map(c => (
                      <Badge key={c.controlCode} tone="info">{c.framework}/{c.controlCode}</Badge>
                    ))}
                  </div>
                )}

                {/* Condition / Criteria / Cause / Recommendation */}
                <div className="alc-field-grid alc-field-grid--mb">
                  <FieldBlock label={t('alcCondition')} en={f.conditionEn} ar={f.conditionAr} lang={lang} />
                  <FieldBlock label={t('alcCriteria')}  en={f.criteriaEn}  ar={f.criteriaAr}  lang={lang} />
                  <FieldBlock label={t('alcCause')}     en={f.causeEn}     ar={f.causeAr}     lang={lang} />
                  <FieldBlock label={t('alcRecommendation')} en={f.recommendationEn} ar={f.recommendationAr} lang={lang} />
                </div>

                {/* Mgmt response */}
                {f.mgmtResponseEn && (
                  <div className="alc-mgmt-response" data-accepted={String(!!f.mgmtAccepts)}>
                    <div className="alc-mgmt-head">
                      <span className="alc-mgmt-head-label">
                        {t('alcMgmtResponse')}
                      </span>
                      <Badge tone={f.mgmtAccepts ? 'success' : 'warning'}>
                        {f.mgmtAccepts ? t('alcAccepted') : t('alcDisagreed')}
                      </Badge>
                      {f.mgmtResponseAuthor && <span className="alc-mgmt-head-author">· <b>{f.mgmtResponseAuthor}</b></span>}
                    </div>
                    {lang === 'ar' ? f.mgmtResponseAr : f.mgmtResponseEn}
                  </div>
                )}
              </div>
            ))
        }
      </Card>
    </div>
  );
}

function FieldBlock({ label, en, ar, lang }: { label: string; en: string; ar: string; lang: 'en' | 'ar' }) {
  return (
    <div>
      <div className="alc-field-label">{label}</div>
      <div className="alc-field-value">
        {lang === 'ar' ? ar : en}
      </div>
    </div>
  );
}

// ─── CAPA tab ──────────────────────────────────────────────────────────────

function CapaTab({ findings, allCapas, onTransition }: {
  findings: Finding[];
  allCapas: CapaTask[];
  onTransition: (c: CapaTask, status: CapaTask['status']) => void;
}) {
  const { t, lang } = useI18n();
  // CAPAs for the engagement's findings
  const findingIds = new Set(findings.map(f => f.id));
  const relevantCapas = allCapas.filter(c => findingIds.has(c.findingId));

  return (
    <div className="alc-section">
      <Card title={t('alcCapaTitle')} Icon={Wrench}>
        <div className="alc-card-sub">
          {t('alcCapaSub')}
        </div>
        {relevantCapas.length === 0
          ? <InlineEmpty>{t('alcCapaEmpty')}</InlineEmpty>
          : (
            <table className="tbl tbl--full">
              <thead>
                <tr>
                  <th>{t('alcCapaId')}</th>
                  <th>{t('alcCapaKind')}</th>
                  <th>{t('alcCapaTitleCol')}</th>
                  <th>{t('alcCapaOwner')}</th>
                  <th>{t('alcCapaTarget')}</th>
                  <th>{t('alcCapaStatus')}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {relevantCapas.map(c => {
                  const overdue = new Date(c.targetDate) < new Date() && c.status !== 'CLOSED';
                  return (
                    <tr key={c.id}>
                      <td className="mono alc-cell-evidence">{c.id}</td>
                      <td>
                        <Badge tone={c.kind === 'CORRECTIVE' ? 'warning' : 'info'}>
                          {c.kind === 'CORRECTIVE' ? t('alcCorrective') : t('alcPreventive')}
                        </Badge>
                      </td>
                      <td className="alc-cell-narrow">
                        <div className="alc-cell-strong">{lang === 'ar' ? c.titleAr : c.titleEn}</div>
                        {c.evidenceRef && <div className="mono alc-cell-evidence">{c.evidenceRef}</div>}
                      </td>
                      <td className="alc-item-sub--plain">{c.ownerRole}</td>
                      <td className={`mono alc-item-sub--plain${overdue ? ' alc-cell-due-late' : ''}`}>
                        {c.targetDate}
                      </td>
                      <td>
                        <Badge tone={overdue ? 'danger' : CAPA_STATUS_TONE[c.status]}>
                          {overdue ? t('alcCapaOverdueBadge') : CAPA_STATUS_LABEL[c.status][lang]}
                        </Badge>
                      </td>
                      <td>
                        {c.status === 'OPEN' && <Button size="sm" variant="ghost" onClick={() => onTransition(c, 'IN_PROGRESS')}>{t('alcStart')}</Button>}
                        {c.status === 'IN_PROGRESS' && <Button size="sm" variant="ghost" onClick={() => onTransition(c, 'AWAITING_VERIFICATION')}>{t('alcSubmit')}</Button>}
                        {c.status === 'AWAITING_VERIFICATION' && <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onTransition(c, 'CLOSED')}>{t('alcVerify')}</Button>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
      </Card>
    </div>
  );
}

// ─── AC packet tab ─────────────────────────────────────────────────────────

function PacketTab({ plan, findings, capas: _capas, stats }: { plan: AnnualPlan; findings: Finding[]; capas: CapaTask[]; stats: ReturnType<typeof aggregateAuditLifecycle> }) {
  const { t, lang } = useI18n();
  const sevCounts = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'OBSERVATION'].map(s => ({
    sev: s,
    count: findings.filter(f => f.severity === s).length,
  }));
  return (
    <div className="alc-section">
      <Card title={t('alcPacketTitle')} Icon={BookOpen}>
        <div className="alc-card-sub">
          {t('alcPacketSub')}
        </div>

        <div className="alc-packet-grid">
          <div>
            <div className="alc-packet-head">{t('alcPacketSevSection')}</div>
            <table className="tbl tbl--full">
              <thead>
                <tr><th>{t('alcSev')}</th><th>{t('alcCount')}</th></tr>
              </thead>
              <tbody>
                {sevCounts.map(s => (
                  <tr key={s.sev}>
                    <td>
                      <Badge tone={s.sev === 'CRITICAL' || s.sev === 'HIGH' ? 'danger' : s.sev === 'MEDIUM' ? 'warning' : 'info'}>{s.sev}</Badge>
                    </td>
                    <td className="mono alc-cell-strong">{s.count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div>
            <div className="alc-packet-head">{t('alcPacketCapaSection')}</div>
            <div className={`stat-tile stat-tile-${stats.closureRate >= 80 ? 'success' : 'warning'} alc-packet-tile`}>
              <div className="stat-tile-label">{t('alcCapaClosureRate')}</div>
              <div className="stat-tile-value">{stats.closureRate}%</div>
              <div className="alc-packet-tile-meta">
                <span>{t('alcCapaClosed')}: <b>{stats.capaClosed}</b></span>
                <span>{t('alcCapaTotal')}: <b>{stats.capaTotal}</b></span>
                <span className={stats.capaOverdue > 0 ? 'alc-packet-overdue' : undefined}>
                  {t('alcCapaOverdueLabel')}: <b>{stats.capaOverdue}</b>
                </span>
              </div>
            </div>
          </div>
        </div>

        <div className="alc-packet-section-head">{t('alcPacketPlanSection')}</div>
        <div className="alc-packet-plan">
          <div><b>{plan.id}</b> · <Badge tone={PLAN_STATUS_TONE[plan.status]}>{PLAN_STATUS_LABEL[plan.status][lang]}</Badge></div>
          {plan.acApprovedBy && <div className="alc-packet-plan-row">{t('alcApprovedBy')}: {plan.acApprovedBy}</div>}
          <div className="alc-packet-plan-meta">
            <span>Q1: {plan.q1.length}</span>
            <span>Q2: {plan.q2.length}</span>
            <span>Q3: {plan.q3.length}</span>
            <span>Q4: {plan.q4.length}</span>
            <span>· {plan.budgetHours.toLocaleString()} {t('alcHours')}</span>
          </div>
        </div>

        <div className="alc-packet-hint">
          {t('alcPacketHint')}
        </div>
      </Card>
    </div>
  );
}

// ─── Findings pipeline (interactive kanban) ────────────────────────────────

function FindingsPipeline({ allFindings, onAdvance, onCardClick }: {
  allFindings: Finding[];
  onAdvance: (id: string, fromStatus: string, toStatus: string | null) => void;
  onCardClick: (id: string) => void;
}) {
  const stages: PipelineStage[] = [
    { code: 'OPEN',                  labelEn: 'Open',                 labelAr: 'مفتوحة',           tone: 'danger',  Icon: AlertOctagon },
    { code: 'MGMT_RESPONSE',         labelEn: 'Mgmt response',        labelAr: 'ردّ الإدارة',      tone: 'warning', Icon: Send },
    { code: 'IN_REMEDIATION',        labelEn: 'In remediation',       labelAr: 'قيد المعالجة',     tone: 'info',    Icon: Wrench },
    { code: 'AWAITING_VERIFICATION', labelEn: 'Awaiting verification', labelAr: 'بانتظار التحقق',  tone: 'warning', Icon: Eye },
    { code: 'CLOSED',                labelEn: 'Closed',               labelAr: 'مُغلَقة',          tone: 'success', Icon: CheckCircle2 },
  ];
  const cards: PipelineCard[] = allFindings.map(f => {
    const due = dueLabel(f.targetDate, () => '');
    return {
      id: f.id, status: f.status, refCode: f.refNumber,
      titleEn: f.titleEn, titleAr: f.titleAr,
      subtitleEn: f.engagementCode + (f.ownerRole ? ' · ' + f.ownerRole : ''),
      subtitleAr: f.engagementCode + (f.ownerRole ? ' · ' + f.ownerRole : ''),
      badges: [
        { label: f.severity, tone: f.severity === 'CRITICAL' || f.severity === 'HIGH' ? 'danger' : f.severity === 'MEDIUM' ? 'warning' : 'info' as const },
        ...f.controlCrosswalk.slice(0, 1).map(c => ({ label: c.framework, tone: 'info' as const })),
      ],
      urgent: due?.urgent && f.status !== 'CLOSED',
      dueLabel: due?.label,
    };
  });
  return (
    <LifecyclePipeline
      stages={stages}
      cards={cards}
      introEn="Live findings pipeline across all engagements. Click a card to drill into its engagement; click the arrow to advance the finding to the next stage."
      introAr="مسار الملاحظات الحيّ عبر جميع الارتباطات. انقر على بطاقة للانتقال إلى ارتباطها، وانقر السهم لتقدّم الملاحظة إلى المرحلة التالية."
      onAdvance={onAdvance}
      onCardClick={onCardClick}
      emptyEn="No findings raised yet across any engagement."
      emptyAr="لا ملاحظات مرفوعة بعد عبر أي ارتباط."
    />
  );
}
