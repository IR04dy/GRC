import { useEffect, useMemo, useState } from 'react';
import {
  Stamp, Calendar, FileText, ClipboardCheck, AlertTriangle,
  CheckCircle2, Download, ThumbsUp, Send, Eye, BookOpen,
  Layers, Wrench, Activity, AlertOctagon, Clock,
} from 'lucide-react';
import { LifecyclePipeline, type PipelineStage, type PipelineCard, dueLabel } from '../../components/lifecycle/LifecyclePipeline';
import { printPacket, textToSection } from '../../lib/packetPrint';
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
  loadWorkingPapers, workingPapersForEngagement, transitionWorkingPaper,
  loadFindings, findingsForEngagement, transitionFinding,
  loadCapas, capasForFinding, transitionCapa,
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
      sections: [textToSection('Full packet', 'الحزمة الكاملة', text)],
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

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Stamp}             label={t('alcKpiEngs')}           value={String(stats.totalEngagements)}     tone="primary" />
        <Kpi Icon={ClipboardCheck}     label={t('alcKpiInFlight')}        value={String(stats.engagementsInFlight)}  tone="info" />
        <Kpi Icon={FileText}           label={t('alcKpiWps')}             value={String(stats.workingPapersTotal)}   tone="info" />
        <Kpi Icon={Eye}                 label={t('alcKpiWpsReview')}        value={String(stats.workingPapersInReview)} tone="warning" />
        <Kpi Icon={AlertTriangle}      label={t('alcKpiFindings')}        value={String(stats.findingsOpen)}          tone={stats.findingsOpen > 0 ? 'warning' : 'success'} />
        <Kpi Icon={AlertTriangle}      label={t('alcKpiCritical')}        value={String(stats.findingsCritical)}      tone={stats.findingsCritical > 0 ? 'danger' : 'success'} />
        <Kpi Icon={Wrench}              label={t('alcKpiCapaClosed')}      value={`${stats.capaClosed} / ${stats.capaTotal}`} tone="info" />
        <Kpi Icon={CheckCircle2}        label={t('alcKpiClosure')}         value={`${stats.closureRate}%`}              tone={stats.closureRate >= 80 ? 'success' : 'warning'} />
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'pipeline',      labelKey: 'lpcTabPipeline',     Icon: Activity },
          { id: 'plan',          labelKey: 'alcTabPlan',         Icon: Calendar },
          { id: 'workingpapers', labelKey: 'alcTabWp',           Icon: FileText },
          { id: 'findings',      labelKey: 'alcTabFindings',     Icon: AlertTriangle },
          { id: 'capa',          labelKey: 'alcTabCapa',         Icon: Wrench },
          { id: 'packet',        labelKey: 'alcTabPacket',       Icon: BookOpen },
        ] as { id: AlcTab; labelKey: string; Icon: typeof Stamp }[]).map(tabDef => (
          <button key={tabDef.id} onClick={() => setTab(tabDef.id)}
            style={{
              padding: '8px 12px', borderRadius: 'var(--r-sm)', fontSize: 12, fontWeight: 600,
              border: `1px solid ${tab === tabDef.id ? 'var(--primary)' : 'var(--border)'}`,
              background: tab === tabDef.id ? 'var(--primary-50)' : 'var(--surface-1)',
              cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6,
            }}>
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
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {AUDIT_ENGAGEMENTS.slice(0, 8).map(e => {
          const isOn = e.engagementCode === code;
          const title = lang === 'ar' && e.titleAr ? e.titleAr : e.titleEn;
          return (
            <button key={e.engagementCode ?? String(e.id)} onClick={() => onChange(e.engagementCode ?? String(e.id))}
              style={{
                padding: '8px 12px', borderRadius: 'var(--r-sm)', fontSize: 11,
                border: `1px solid ${isOn ? 'var(--primary)' : 'var(--border)'}`,
                background: isOn ? 'var(--primary-50)' : 'var(--surface-1)',
                cursor: 'pointer', textAlign: 'start', maxWidth: 240,
                display: 'flex', flexDirection: 'column', gap: 2,
              }}>
              <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{e.engagementCode}</span>
              <span style={{ fontSize: 12, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12, marginBottom: 12, flexWrap: 'wrap' }}>
        <div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 4, flexWrap: 'wrap' }}>
            <span className="mono" style={{ fontSize: 12, color: 'var(--primary)' }}>{plan.id}</span>
            <Badge tone={PLAN_STATUS_TONE[plan.status]}>{PLAN_STATUS_LABEL[plan.status][lang]}</Badge>
            <Badge tone="info">{plan.year}</Badge>
            <Badge tone="primary">{plan.budgetHours.toLocaleString()} {t('alcHours')}</Badge>
          </div>
          {plan.acApprovedBy && (
            <div style={{ fontSize: 11, color: 'var(--success-fg)' }}>
              {t('alcApprovedBy')}: <b>{plan.acApprovedBy}</b>
              {plan.acApprovedAt && <> · <span className="mono">{plan.acApprovedAt.slice(0, 10)}</span></>}
            </div>
          )}
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
          {plan.status === 'DRAFT' && <Button variant="primary" Icon={Send} onClick={onSubmitForReview}>{t('alcSubmitForAc')}</Button>}
          {plan.status === 'AC_REVIEW' && <Button variant="primary" Icon={ThumbsUp} onClick={onApprove}>{t('alcAcApprove')}</Button>}
          {plan.status === 'AC_APPROVED' && <Button variant="primary" onClick={onExecute}>{t('alcStartExec')}</Button>}
        </div>
      </div>

      {plan.acNotesEn && (
        <div style={{
          padding: 12, marginBottom: 16,
          background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
          borderInlineStart: '3px solid var(--primary)',
          fontSize: 12, lineHeight: 1.6,
        }}>
          <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 4 }}>
            {t('alcAcNotes')}
          </div>
          {lang === 'ar' ? plan.acNotesAr : plan.acNotesEn}
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
        {quarters.map(q => (
          <div key={q.label} style={{
            padding: 12, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
            background: 'var(--surface-1)',
          }}>
            <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 8, color: 'var(--primary)' }}>{q.label} 2026</div>
            {q.codes.length === 0
              ? <div style={{ fontSize: 11, color: 'var(--ink-3)', textAlign: 'center', padding: 8 }}>—</div>
              : q.codes.map(code => {
                  const e = pickEngagementByCode(code);
                  if (!e) return null;
                  const title = lang === 'ar' && e.titleAr ? e.titleAr : e.titleEn;
                  return (
                    <div key={code} style={{
                      padding: 6, marginBottom: 4, background: 'var(--surface-2)', borderRadius: 4,
                      fontSize: 10,
                    }}>
                      <div className="mono" style={{ color: 'var(--ink-3)', fontSize: 9 }}>{code}</div>
                      <div style={{ fontWeight: 600, marginTop: 2 }}>{title}</div>
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
    <div style={{ marginTop: 16 }}>
      <Card title={t('alcWpTitle')} Icon={FileText}
        action={<Button size="sm" variant="primary" onClick={() => onCreate('TEST', 'New test working paper', 'ورقة عمل اختبار جديدة', 4)}>{t('alcWpCreate')}</Button>}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {t('alcWpSub')}
        </div>
        {wps.length === 0
          ? <div style={{ padding: 16, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
              {t('alcWpEmpty')}
            </div>
          : wps.map(wp => (
              <div key={wp.id} style={{ padding: 12, marginBottom: 12, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap', marginBottom: 8 }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 4 }}>
                      <span className="mono" style={{ fontSize: 11, color: 'var(--primary)' }}>{wp.refNumber}</span>
                      <Badge tone={WP_STATUS_TONE[wp.status]}>{wp.status}</Badge>
                      <Badge tone="info">{WP_TYPE_LABEL[wp.type][lang]}</Badge>
                      <Badge tone="neutral">{wp.hours}h</Badge>
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>
                      {lang === 'ar' ? wp.titleAr : wp.titleEn}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                      {t('alcAuthor')}: <b>{wp.author}</b>
                      {wp.reviewer && <> · {t('alcReviewer')}: <b>{wp.reviewer}</b></>}
                      {wp.reviewedAt && <> · <span className="mono">{wp.reviewedAt.slice(0, 10)}</span></>}
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 4 }}>
                    {wp.status === 'DRAFT' && <Button size="sm" variant="primary" Icon={Send} onClick={() => onPeerReview(wp)}>{t('alcWpSendReview')}</Button>}
                    {wp.status === 'PEER_REVIEW' && (
                      <>
                        <Button size="sm" variant="primary" Icon={ThumbsUp} onClick={() => onApprove(wp)}>{t('alcApprove')}</Button>
                        <Button size="sm" variant="ghost" onClick={() => onReject(wp)}>{t('alcReject')}</Button>
                      </>
                    )}
                  </div>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 8 }}>
                  <div>
                    <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 4 }}>
                      {t('alcWpProcedure')}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-2)', lineHeight: 1.6 }}>
                      {lang === 'ar' ? wp.procedureAr : wp.procedureEn}
                    </div>
                  </div>
                  <div>
                    <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 4 }}>
                      {t('alcWpConclusion')}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-2)', lineHeight: 1.6 }}>
                      {lang === 'ar' ? wp.conclusionAr : wp.conclusionEn}
                    </div>
                  </div>
                </div>
                {wp.controlsCovered.length > 0 && (
                  <div style={{ marginTop: 8, display: 'flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}>
                    <span style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase' }}>{t('alcWpControls')}:</span>
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
  return (
    <div style={{ marginTop: 16 }}>
      <Card title={t('alcFindingTitle')} Icon={AlertTriangle}
        action={<Button size="sm" variant="primary" onClick={() => onCreate('New finding', 'ملاحظة جديدة', 'MEDIUM', 'Condition observed during fieldwork.', 'حالة مرصودة أثناء العمل الميداني.')}>{t('alcFindingCreate')}</Button>}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {t('alcFindingSub')}
        </div>
        {findings.length === 0
          ? <div style={{ padding: 16, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
              {t('alcFindingEmpty')}
            </div>
          : findings.map(f => (
              <div key={f.id} style={{ padding: 12, marginBottom: 12, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap', marginBottom: 8 }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 4 }}>
                      <span className="mono" style={{ fontSize: 11, color: 'var(--primary)' }}>{f.refNumber}</span>
                      <Badge tone={SEVERITY_TONE[f.severity]}>{f.severity}</Badge>
                      <Badge tone={f.status === 'CLOSED' ? 'success' : f.status === 'OPEN' ? 'danger' : 'warning'}>
                        {FINDING_STATUS_LABEL[f.status][lang]}
                      </Badge>
                      {f.targetDate && <Badge tone="info">{t('alcDue')}: <span className="mono">{f.targetDate}</span></Badge>}
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 4 }}>
                      {lang === 'ar' ? f.titleAr : f.titleEn}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                      {f.ownerRole && <>{t('alcOwner')}: <b>{f.ownerRole}</b></>}
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
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
                  <div style={{ display: 'flex', gap: 4, marginBottom: 8, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase' }}>{t('alcCrosswalk')}:</span>
                    {f.controlCrosswalk.map(c => (
                      <Badge key={c.controlCode} tone="info">{c.framework}/{c.controlCode}</Badge>
                    ))}
                  </div>
                )}

                {/* Condition / Criteria / Cause / Recommendation */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 8 }}>
                  <FieldBlock label={t('alcCondition')} en={f.conditionEn} ar={f.conditionAr} lang={lang} />
                  <FieldBlock label={t('alcCriteria')}  en={f.criteriaEn}  ar={f.criteriaAr}  lang={lang} />
                  <FieldBlock label={t('alcCause')}     en={f.causeEn}     ar={f.causeAr}     lang={lang} />
                  <FieldBlock label={t('alcRecommendation')} en={f.recommendationEn} ar={f.recommendationAr} lang={lang} />
                </div>

                {/* Mgmt response */}
                {f.mgmtResponseEn && (
                  <div style={{
                    padding: 10, background: f.mgmtAccepts ? 'rgba(16,185,129,0.06)' : 'rgba(245,158,11,0.06)',
                    borderRadius: 4, borderInlineStart: `3px solid ${f.mgmtAccepts ? 'var(--success)' : 'var(--warning)'}`,
                    fontSize: 11, lineHeight: 1.6,
                  }}>
                    <div style={{ display: 'flex', gap: 6, marginBottom: 4 }}>
                      <span style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                        {t('alcMgmtResponse')}
                      </span>
                      <Badge tone={f.mgmtAccepts ? 'success' : 'warning'}>
                        {f.mgmtAccepts ? t('alcAccepted') : t('alcDisagreed')}
                      </Badge>
                      {f.mgmtResponseAuthor && <span style={{ fontSize: 10, color: 'var(--ink-3)' }}>· <b>{f.mgmtResponseAuthor}</b></span>}
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
      <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 11, color: 'var(--ink-2)', lineHeight: 1.6 }}>
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
    <div style={{ marginTop: 16 }}>
      <Card title={t('alcCapaTitle')} Icon={Wrench}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {t('alcCapaSub')}
        </div>
        {relevantCapas.length === 0
          ? <div style={{ padding: 16, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
              {t('alcCapaEmpty')}
            </div>
          : (
            <table className="tbl" style={{ width: '100%' }}>
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
                      <td className="mono" style={{ fontSize: 11 }}>{c.id}</td>
                      <td>
                        <Badge tone={c.kind === 'CORRECTIVE' ? 'warning' : 'info'}>
                          {c.kind === 'CORRECTIVE' ? t('alcCorrective') : t('alcPreventive')}
                        </Badge>
                      </td>
                      <td style={{ maxWidth: 320 }}>
                        <div style={{ fontSize: 12, fontWeight: 600 }}>{lang === 'ar' ? c.titleAr : c.titleEn}</div>
                        {c.evidenceRef && <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{c.evidenceRef}</div>}
                      </td>
                      <td style={{ fontSize: 11 }}>{c.ownerRole}</td>
                      <td className="mono" style={{ fontSize: 11, color: overdue ? 'var(--danger-fg)' : undefined }}>
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

function PacketTab({ plan, findings, capas, stats }: { plan: AnnualPlan; findings: Finding[]; capas: CapaTask[]; stats: ReturnType<typeof aggregateAuditLifecycle> }) {
  const { t, lang } = useI18n();
  const sevCounts = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'OBSERVATION'].map(s => ({
    sev: s,
    count: findings.filter(f => f.severity === s).length,
  }));
  return (
    <div style={{ marginTop: 16 }}>
      <Card title={t('alcPacketTitle')} Icon={BookOpen}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 16, lineHeight: 1.5 }}>
          {t('alcPacketSub')}
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <div>
            <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--primary)', marginBottom: 8 }}>{t('alcPacketSevSection')}</div>
            <table className="tbl" style={{ width: '100%' }}>
              <thead>
                <tr><th>{t('alcSev')}</th><th>{t('alcCount')}</th></tr>
              </thead>
              <tbody>
                {sevCounts.map(s => (
                  <tr key={s.sev}>
                    <td>
                      <Badge tone={s.sev === 'CRITICAL' || s.sev === 'HIGH' ? 'danger' : s.sev === 'MEDIUM' ? 'warning' : 'info'}>{s.sev}</Badge>
                    </td>
                    <td className="mono" style={{ fontWeight: 700 }}>{s.count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div>
            <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--primary)', marginBottom: 8 }}>{t('alcPacketCapaSection')}</div>
            <div style={{ padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)' }}>
              <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{t('alcCapaClosureRate')}</div>
              <div style={{ fontSize: 32, fontWeight: 700, marginTop: 4, color: stats.closureRate >= 80 ? 'var(--success-fg)' : 'var(--warning-fg)' }} className="mono">
                {stats.closureRate}%
              </div>
              <div style={{ display: 'flex', gap: 12, fontSize: 11, color: 'var(--ink-3)', marginTop: 8 }}>
                <span>{t('alcCapaClosed')}: <b>{stats.capaClosed}</b></span>
                <span>{t('alcCapaTotal')}: <b>{stats.capaTotal}</b></span>
                <span style={{ color: stats.capaOverdue > 0 ? 'var(--danger-fg)' : undefined }}>
                  {t('alcCapaOverdueLabel')}: <b>{stats.capaOverdue}</b>
                </span>
              </div>
            </div>
          </div>
        </div>

        <div style={{ marginTop: 16, fontSize: 12, fontWeight: 700, color: 'var(--primary)', marginBottom: 8 }}>{t('alcPacketPlanSection')}</div>
        <div style={{ padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', fontSize: 11 }}>
          <div><b>{plan.id}</b> · <Badge tone={PLAN_STATUS_TONE[plan.status]}>{PLAN_STATUS_LABEL[plan.status][lang]}</Badge></div>
          {plan.acApprovedBy && <div style={{ marginTop: 4 }}>{t('alcApprovedBy')}: {plan.acApprovedBy}</div>}
          <div style={{ display: 'flex', gap: 12, marginTop: 6, color: 'var(--ink-3)' }}>
            <span>Q1: {plan.q1.length}</span>
            <span>Q2: {plan.q2.length}</span>
            <span>Q3: {plan.q3.length}</span>
            <span>Q4: {plan.q4.length}</span>
            <span>· {plan.budgetHours.toLocaleString()} {t('alcHours')}</span>
          </div>
        </div>

        <div style={{ marginTop: 12, fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.5 }}>
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
      introAr="خط أنابيب الملاحظات الحيّ عبر جميع الارتباطات. انقر على بطاقة للانتقال إلى ارتباطها، وانقر السهم لتقدّم الملاحظة إلى المرحلة التالية."
      onAdvance={onAdvance}
      onCardClick={onCardClick}
      emptyEn="No findings raised yet across any engagement."
      emptyAr="لا ملاحظات مرفوعة بعد عبر أي ارتباط."
    />
  );
}
