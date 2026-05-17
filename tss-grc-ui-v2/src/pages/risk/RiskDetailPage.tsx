import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { AlertTriangle, Activity, ListChecks, MessageSquare, Link2, Edit, Send, CheckCheck, ShieldOff, Archive, RotateCcw } from 'lucide-react';
import { CommsPanel } from '../../components/comms/CommsPanel';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { DetailLayout } from '../../components/layout/DetailLayout';
import { RailField, RailFieldGroup } from '../../components/layout/DetailRail';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LinkedChangesPanel } from '../../components/ui/LinkedChangesPanel';
import { riskApi, ApiError, type RiskResponse } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { EntityAnchorsPanel } from '../../components/authority/EntityAnchorsPanel';
import { RISK_ANCHORS_BY_ID } from '../../lib/entityAnchors';
import {
  MOCK_RISKS,
  RISK_ASSESSMENTS_BY_ID, RISK_TREATMENTS_BY_ID,
  RISK_KRIS_BY_ID, RISK_HISTORY_BY_ID, RISK_LINKS_BY_ID,
} from './mockRisks';
import {
  listAssessments, recordAssessment,
  listTreatments, createTreatment, transitionTreatment, deleteTreatment,
  scoreColor, treatmentTypeTone, treatmentStatusTone, nextTreatmentStatuses,
  type RiskAssessment, type RiskTreatment, type RiskTreatmentType, type RiskTreatmentStatus,
} from '../../lib/riskAdvanced';
import { EmptyState } from '../../components/ui/EmptyState';

type TabId = 'overview' | 'assessments' | 'treatments' | 'kris' | 'links' | 'history' | 'comms';

const STATUS_TONE: Record<string, BadgeTone> = {
  OPEN: 'danger', UNDER_ASSESSMENT: 'warning', TREATMENT_IN_PROGRESS: 'info',
  ACCEPTED: 'accent', TRANSFERRED: 'primary', CLOSED: 'success',
};
const RISK_LEVEL_TONE: Record<string, BadgeTone> = {
  LOW: 'success', MEDIUM: 'info', HIGH: 'warning', VERY_HIGH: 'accent', CRITICAL: 'danger',
};

export function RiskDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t, lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const [risk, setRisk] = useState<RiskResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>('overview');

  // Live data from V80 endpoints. When the backend is reachable these
  // arrays replace the mock fallbacks below.
  const [liveAssessments, setLiveAssessments] = useState<RiskAssessment[] | null>(null);
  const [liveTreatments, setLiveTreatments] = useState<RiskTreatment[] | null>(null);

  async function load() {
    if (!id) return;
    setLoading(true); setError(null);
    try {
      setRisk(await riskApi.get(Number(id)));
    } catch (e) {
      // Demo-mode fallback: when no JWT is present (or the backend is
      // unreachable), fall back to MOCK_RISKS so the detail page renders.
      const fallback = MOCK_RISKS.find(r => r.id === Number(id));
      if (fallback) setRisk(fallback);
      else setError(e instanceof ApiError ? e.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [id]);

  // Fetch real assessments + treatments once the risk loads. Silent
  // failure here keeps the demo / unauthenticated path working — the
  // mock arrays still render.
  useEffect(() => {
    if (!risk) return;
    void (async () => {
      try { setLiveAssessments(await listAssessments(risk.id)); } catch { /* keep mock */ }
      try { setLiveTreatments(await listTreatments(risk.id));   } catch { /* keep mock */ }
    })();
  }, [risk?.id]);  // eslint-disable-line react-hooks/exhaustive-deps

  async function onRecordAssessment() {
    if (!risk) return;
    const lAns = await dialog.prompt({
      title: 'Record re-assessment',
      label: 'Likelihood (1..5)', type: 'number', required: true, initial: '3',
    });
    if (!lAns) return;
    const iAns = await dialog.prompt({
      title: 'Record re-assessment',
      label: 'Impact (1..5)', type: 'number', required: true, initial: '3',
    });
    if (!iAns) return;
    const notes = await dialog.prompt({
      title: 'Record re-assessment',
      label: 'Notes (optional)', type: 'textarea',
    });
    try {
      await recordAssessment(risk.id, {
        likelihood: Number(lAns), impact: Number(iAns),
        assessmentNotesEn: notes ?? undefined,
      });
      toast.success('Assessment recorded — residual score updated');
      setLiveAssessments(await listAssessments(risk.id));
      // Refresh the Risk to pick up the new residual snapshot.
      try { setRisk(await riskApi.get(risk.id)); } catch { /* ignore */ }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to record assessment');
    }
  }

  async function onCreateTreatment() {
    if (!risk) return;
    const type = await dialog.prompt({
      title: 'New treatment',
      label: 'Type — AVOID / MITIGATE / TRANSFER / ACCEPT', type: 'text',
      required: true, initial: 'MITIGATE',
    });
    if (!type) return;
    const upper = type.toUpperCase();
    if (!['AVOID', 'MITIGATE', 'TRANSFER', 'ACCEPT'].includes(upper)) {
      toast.danger('Type must be AVOID, MITIGATE, TRANSFER, or ACCEPT'); return;
    }
    const description = await dialog.prompt({
      title: 'New treatment',
      label: 'Description', type: 'textarea', required: true,
    });
    if (!description) return;
    const due = await dialog.prompt({
      title: 'New treatment',
      label: 'Due date (YYYY-MM-DD, optional)', type: 'text',
    });
    try {
      await createTreatment(risk.id, {
        treatmentType: upper as RiskTreatmentType,
        descriptionEn: description,
        dueDate: due || undefined,
      });
      toast.success('Treatment created');
      setLiveTreatments(await listTreatments(risk.id));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to create treatment');
    }
  }

  async function onTransitionTreatment(tr: RiskTreatment, to: RiskTreatmentStatus) {
    try {
      await transitionTreatment(tr.id, to);
      toast.success(`Treatment → ${to}`);
      if (risk) setLiveTreatments(await listTreatments(risk.id));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Transition failed');
    }
  }

  async function onDeleteTreatment(tr: RiskTreatment) {
    const ok = await dialog.confirm({
      title: 'Delete treatment',
      body: `Soft-delete "${tr.descriptionEn.substring(0, 60)}…"?`,
      destructive: true, submitLabel: 'Delete',
    });
    if (!ok) return;
    try {
      await deleteTreatment(tr.id);
      toast.success('Treatment deleted');
      if (risk) setLiveTreatments(await listTreatments(risk.id));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Delete failed');
    }
  }

  async function workflow(fn: (n: number) => Promise<RiskResponse>, msg: string) {
    if (!risk) return;
    try { const updated = await fn(risk.id); setRisk(updated); toast.success(msg); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  if (loading) {
    return <Shell title={t('modRisk')} sub="ISO 31000 + COSO ERM">
      <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>
    </Shell>;
  }
  if (error || !risk) {
    return <Shell title={t('modRisk')} sub="ISO 31000 + COSO ERM">
      <div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>{error ?? 'Not found'}</div>
    </Shell>;
  }

  const title = lang === 'ar' && risk.titleAr ? risk.titleAr : risk.titleEn;

  const tabs: TabItem<TabId>[] = [
    { id: 'overview',    label: t('detailTabOverview') },
    { id: 'assessments', label: t('detailTabAssessments') },
    { id: 'treatments',  label: t('detailTabTreatments') },
    { id: 'kris',        label: t('detailTabKris') },
    { id: 'links',       label: t('detailTabLinks') },
    { id: 'history',     label: t('detailTabHistory') },
    { id: 'comms',       label: t('detailTabComms') },
  ];

  return (
    <Shell title={t('modRisk')} sub="ISO 31000 + COSO ERM">
      <DetailHeader
        breadcrumbs={[
          { label: t('modRisk'), to: '/risk' },
          { label: risk.riskCode },
        ]}
        eyebrow={risk.riskCode}
        title={title}
        sub={risk.descriptionEn}
        badges={<>
          <Badge tone={STATUS_TONE[risk.status] ?? 'neutral'} dot>{risk.status.replace(/_/g, ' ')}</Badge>
          {risk.riskLevel && <Badge tone={RISK_LEVEL_TONE[risk.riskLevel] ?? 'neutral'}>{risk.riskLevel}</Badge>}
          {risk.categoryNameEn && <Badge tone="primary">{lang === 'ar' && risk.categoryNameAr ? risk.categoryNameAr : risk.categoryNameEn}</Badge>}
        </>}
        actions={<>
          {risk.status === 'OPEN'             && <Button variant="primary" Icon={Send}      onClick={() => workflow(riskApi.submitForReview, 'Submitted for review')}>{t('riskActSubmitReview')}</Button>}
          {risk.status === 'UNDER_ASSESSMENT' && <Button variant="primary" Icon={CheckCheck} onClick={() => workflow(riskApi.approveRisk, 'Approved for treatment')}>{t('riskActApprove')}</Button>}
          {(risk.status === 'OPEN' || risk.status === 'UNDER_ASSESSMENT') &&
            <Button variant="secondary" Icon={ShieldOff} onClick={() => workflow(riskApi.acceptRisk, 'Risk accepted')}>{t('riskActAccept')}</Button>}
          {(risk.status === 'TREATMENT_IN_PROGRESS' || risk.status === 'ACCEPTED' || risk.status === 'TRANSFERRED') &&
            <Button variant="secondary" Icon={Archive} onClick={() => workflow(riskApi.closeRisk, 'Risk closed')}>{t('riskActClose')}</Button>}
          {risk.status === 'CLOSED' && <Button variant="secondary" Icon={RotateCcw} onClick={() => workflow(riskApi.reopenRisk, 'Risk reopened')}>{t('riskActReopen')}</Button>}
        </>}
      />

      {/* Score header strip */}
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(4, minmax(140px, 1fr))', marginBottom: 12 }}>
        <ScoreTile label={t('riskColInherent')} score={risk.inherentScore} />
        <ScoreTile label={t('riskColResidual')} score={risk.residualScore ?? 0} />
        <FieldTile label={t('riskColOwner')}    value={risk.ownerFullNameEn ?? risk.ownerName ?? '—'} />
        <FieldTile label={t('detailIdentified')} value={risk.identifiedDate ?? '—'} />
      </div>

      <DetailLayout
        rail={<>
          <Card title={t('detailAtAGlance')} Icon={AlertTriangle}>
            <RailFieldGroup>
              <RailField
                label={t('fldStatus')}
                accent
                value={<Badge tone={STATUS_TONE[risk.status] ?? 'neutral'} dot>{risk.status.replace(/_/g, ' ')}</Badge>}
              />
              {risk.riskLevel && (
                <RailField
                  label={t('riskColLevel')}
                  value={<Badge tone={RISK_LEVEL_TONE[risk.riskLevel] ?? 'neutral'}>{risk.riskLevel}</Badge>}
                />
              )}
              <RailField label={t('riskColInherent')} value={`L${risk.inherentLikelihood} × I${risk.inherentImpact} = ${risk.inherentScore}`} mono />
              <RailField
                label={t('riskColResidual')}
                value={risk.residualScore != null
                  ? `L${risk.residualLikelihood} × I${risk.residualImpact} = ${risk.residualScore}`
                  : '—'}
                mono
              />
              <RailField label={t('riskColOwner')}    value={risk.ownerFullNameEn ?? risk.ownerName ?? '—'} />
              <RailField label={t('riskColCategory')} value={lang === 'ar' && risk.categoryNameAr ? risk.categoryNameAr : (risk.categoryNameEn ?? risk.categoryName ?? '—')} />
              <RailField label={t('detailIdentified')} value={risk.identifiedDate ?? '—'} mono />
              <RailField label={t('detailReviewDate')} value={risk.reviewDate ?? '—'} mono />
            </RailFieldGroup>
          </Card>
          <EntityAnchorsPanel
            anchors={RISK_ANCHORS_BY_ID[risk.id] ?? []}
            subtitle="Authorities this risk threatens." />
          <LinkedChangesPanel entityType="RISK" entityId={risk.id} />
          <Card title={t('detailQuickActions')} Icon={Edit}>
            <Link to={`/risk?focus=${risk.id}`} style={{ fontSize: 13, color: 'var(--primary)' }}>{t('detailEditInRegister')}</Link>
          </Card>
        </>}
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <Card title={t('detailTabOverview')} Icon={AlertTriangle}>
              <Field label={t('riskColName')} value={title} bold />
              <Field label={t('detailDescription')} value={risk.descriptionEn ?? '—'} multiline />
            </Card>
          )}

      {tab === 'assessments' && (() => {
        const live = liveAssessments;
        const useLive = live !== null;
        const mockRows = RISK_ASSESSMENTS_BY_ID[risk.id] ?? [];
        const count = useLive ? live.length : mockRows.length;
        return (
          <Card title={t('detailTabAssessments')} Icon={Activity}
            action={
              <div style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
                <Badge tone="primary">{count}</Badge>
                {useLive && <Button size="sm" variant="primary" Icon={Edit} onClick={onRecordAssessment}>
                  Record re-assessment
                </Button>}
              </div>
            }>
            {count === 0
              ? <EmptyState Icon={Activity} title="No re-assessments yet" sub={t('detailAssessmentsHint')} />
              : useLive
                ? <div style={{ display: 'flex', flexDirection: 'column' }}>
                    {live.map(a => (
                      <div key={a.id} style={{ display: 'grid', gridTemplateColumns: '110px 1fr 80px 80px', gap: 12, padding: '10px 0', borderBottom: '1px dashed var(--border)', alignItems: 'center' }}>
                        <span className="mono" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{a.assessmentDate}</span>
                        <div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                            L={a.likelihood} · I={a.impact}
                          </div>
                          <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 2 }}>
                            {a.assessmentNotesEn ?? '—'}
                          </div>
                        </div>
                        <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                          #{a.id}
                        </span>
                        <span style={{
                          display: 'inline-block', padding: '2px 9px', borderRadius: 'var(--r-pill)',
                          background: scoreColor(a.score),
                          color: '#fff', fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: 700,
                        }}>{a.score ?? a.likelihood * a.impact}</span>
                      </div>
                    ))}
                  </div>
                : <div style={{ display: 'flex', flexDirection: 'column' }}>
                    {mockRows.map(a => (
                      <div key={a.id} style={{ display: 'grid', gridTemplateColumns: '110px 1fr 80px 80px', gap: 12, padding: '10px 0', borderBottom: '1px dashed var(--border)', alignItems: 'center' }}>
                        <span className="mono" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{a.date}</span>
                        <div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>{a.assessor}</div>
                          <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 2 }}>{a.notes}</div>
                        </div>
                        <span style={{ display: 'inline-block', padding: '2px 9px', borderRadius: 'var(--r-pill)', background: scoreColor(a.inherentScore), color: '#fff', fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: 700 }}>{a.inherentScore}</span>
                        {a.residualScore != null
                          ? <span style={{ display: 'inline-block', padding: '2px 9px', borderRadius: 'var(--r-pill)', background: scoreColor(a.residualScore), color: '#fff', fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: 700 }}>{a.residualScore}</span>
                          : <span className="mono" style={{ color: 'var(--ink-4)' }}>—</span>}
                      </div>
                    ))}
                  </div>
            }
          </Card>
        );
      })()}

      {tab === 'treatments' && (() => {
        const live = liveTreatments;
        const useLive = live !== null;
        const mockRows = RISK_TREATMENTS_BY_ID[risk.id] ?? [];
        const count = useLive ? live.length : mockRows.length;
        return (
          <Card title={t('detailTabTreatments')} Icon={ListChecks}
            action={
              <div style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
                <Badge tone="primary">{count}</Badge>
                {useLive && <Button size="sm" variant="primary" Icon={Edit} onClick={onCreateTreatment}>
                  Add treatment
                </Button>}
              </div>
            }>
            {count === 0
              ? <EmptyState Icon={ListChecks} title="No treatments planned" sub={t('detailTreatmentsHint')} />
              : useLive
                ? <div style={{ display: 'flex', flexDirection: 'column' }}>
                    {live.map(tr => (
                      <div key={tr.id} style={{ padding: '10px 0', borderBottom: '1px dashed var(--border)' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, marginBottom: 4 }}>
                          <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
                            <Badge tone={treatmentTypeTone(tr.treatmentType)}>{tr.treatmentType}</Badge>
                            <span style={{ fontSize: 13, fontWeight: 600 }}>{tr.descriptionEn}</span>
                          </div>
                          <Badge tone={treatmentStatusTone(tr.status)}>{tr.status.replace(/_/g, ' ')}</Badge>
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', display: 'flex', gap: 12, marginBottom: 4 }}>
                          <span>Owner: <strong style={{ color: 'var(--ink-2)' }}>{tr.ownerId ?? '—'}</strong></span>
                          <span>Due: <span className="mono">{tr.dueDate ?? '—'}</span></span>
                          {tr.completedAt && <span>Completed: <span className="mono">{tr.completedAt.substring(0, 10)}</span></span>}
                        </div>
                        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                          {nextTreatmentStatuses(tr.status).map(next => (
                            <Button key={next} size="sm" variant="ghost"
                              onClick={() => onTransitionTreatment(tr, next)}>
                              → {next}
                            </Button>
                          ))}
                          {(tr.status === 'PLANNED' || tr.status === 'IN_PROGRESS') && (
                            <Button size="sm" variant="ghost" onClick={() => onDeleteTreatment(tr)}>
                              Delete
                            </Button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                : <div style={{ display: 'flex', flexDirection: 'column' }}>
                    {mockRows.map(tr => (
                      <div key={tr.id} style={{ padding: '10px 0', borderBottom: '1px dashed var(--border)' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, marginBottom: 4 }}>
                          <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
                            <Badge tone={tr.type === 'AVOID' ? 'danger' : tr.type === 'TRANSFER' ? 'accent' : tr.type === 'ACCEPT' ? 'warning' : 'primary'}>{tr.type}</Badge>
                            <span style={{ fontSize: 13, fontWeight: 600 }}>{tr.action}</span>
                          </div>
                          <Badge tone={tr.status === 'IMPLEMENTED' ? 'success' : tr.status === 'IN_PROGRESS' ? 'info' : tr.status === 'CANCELLED' ? 'neutral' : 'warning'}>{tr.status.replace(/_/g, ' ')}</Badge>
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', display: 'flex', gap: 12 }}>
                          <span>Owner: <strong style={{ color: 'var(--ink-2)' }}>{tr.owner}</strong></span>
                          <span>Due: <span className="mono">{tr.dueDate}</span></span>
                        </div>
                        <div style={{ marginTop: 6, height: 6, background: 'var(--surface-3)', borderRadius: 'var(--r-pill)', overflow: 'hidden' }}>
                          <div style={{ width: `${tr.progressPct}%`, height: '100%', background: tr.progressPct >= 80 ? 'var(--success)' : tr.progressPct >= 40 ? 'var(--primary)' : 'var(--accent)' }} />
                        </div>
                      </div>
                    ))}
                  </div>
            }
          </Card>
        );
      })()}

      {tab === 'kris' && (
        <Card title={t('detailTabKris')} Icon={Activity}
          action={<Badge tone="primary">{(RISK_KRIS_BY_ID[risk.id] ?? []).length}</Badge>}>
          {(RISK_KRIS_BY_ID[risk.id] ?? []).length === 0
            ? <EmptyState Icon={Activity} title="No KRIs linked" sub={t('detailKrisHint')} />
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {(RISK_KRIS_BY_ID[risk.id] ?? []).map(k => (
                  <div key={k.id} style={{ display: 'grid', gridTemplateColumns: '110px 1fr 100px 80px', gap: 12, padding: '10px 0', borderBottom: '1px dashed var(--border)', alignItems: 'center' }}>
                    <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{k.code}</span>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600 }}>{k.nameEn}</div>
                      <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>Green ≤ {k.greenMax} · Amber ≤ {k.amberMax} {k.unit}</div>
                    </div>
                    <span className="mono" style={{ fontSize: 14, fontWeight: 700, color: 'var(--ink-1)' }}>{k.current} <span style={{ fontSize: 10, color: 'var(--ink-3)', fontWeight: 400 }}>{k.unit}</span></span>
                    <Badge tone={k.band === 'RED' ? 'danger' : k.band === 'AMBER' ? 'warning' : k.band === 'GREEN' ? 'success' : 'neutral'} dot>{k.band}</Badge>
                  </div>
                ))}
              </div>}
        </Card>
      )}

      {tab === 'links' && (
        <Card title={t('detailTabLinks')} Icon={Link2}
          action={<Badge tone="primary">{(RISK_LINKS_BY_ID[risk.id] ?? []).length}</Badge>}>
          {(RISK_LINKS_BY_ID[risk.id] ?? []).length === 0
            ? <EmptyState Icon={Link2} title="No links" sub={t('detailLinksHint')} />
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {(RISK_LINKS_BY_ID[risk.id] ?? []).map(lk => (
                  <Link key={lk.id} to={lk.href} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, padding: '8px 0', borderBottom: '1px dashed var(--border)', textDecoration: 'none', color: 'inherit' }}>
                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                      <Badge tone="info">{lk.type}</Badge>
                      <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{lk.ref}</span>
                      <span style={{ fontSize: 13, fontWeight: 600 }}>{lk.title}</span>
                    </div>
                    {lk.status && <Badge tone={lk.status === 'IMPLEMENTED' ? 'success' : lk.status === 'AMBER' ? 'warning' : lk.status === 'RED' ? 'danger' : 'neutral'}>{lk.status}</Badge>}
                  </Link>
                ))}
              </div>}
        </Card>
      )}

      {tab === 'history' && (
        <Card title={t('detailTabHistory')} Icon={MessageSquare}
          action={<Badge tone="primary">{(RISK_HISTORY_BY_ID[risk.id] ?? []).length}</Badge>}>
          {(RISK_HISTORY_BY_ID[risk.id] ?? []).length === 0
            ? <EmptyState Icon={MessageSquare} title="No history yet" sub={t('detailHistoryHint')} />
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {(RISK_HISTORY_BY_ID[risk.id] ?? []).map(h => (
                  <div key={h.id} style={{ display: 'grid', gridTemplateColumns: '160px 100px 1fr', gap: 12, padding: '8px 0', borderBottom: '1px dashed var(--border)', alignItems: 'flex-start' }}>
                    <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{h.at.replace('T', ' ').slice(0, 16)}</span>
                    <Badge tone={h.type === 'CREATED' ? 'primary' : h.type === 'STATUS_CHANGE' ? 'info' : h.type === 'SCORE_CHANGE' ? 'accent' : h.type === 'TREATMENT_ADDED' ? 'success' : h.type === 'REVIEW' ? 'warning' : 'neutral'}>{h.type.replace(/_/g, ' ')}</Badge>
                    <div>
                      <div style={{ fontSize: 13, color: 'var(--ink-1)' }}>{h.message}</div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>by {h.actor}</div>
                    </div>
                  </div>
                ))}
              </div>}
        </Card>
      )}

      {tab === 'comms' && (
        <CommsPanel
          module="RISK"
          eventKind={risk.riskLevel === 'CRITICAL' ? 'BOARD_RISK_ESCALATION' : undefined}
          severity={(risk.riskLevel as 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL') ?? undefined}
          eventDetectedAt={risk.identifiedDate ? `${risk.identifiedDate}T09:00:00` : undefined}
          recordKey={`RISK:${risk.id}`} />
      )}
        </>}
      />

    </Shell>
  );
}

function Field({ label, value, bold, mono, multiline }: { label: string; value: string; bold?: boolean; mono?: boolean; multiline?: boolean }) {
  return <div style={{ marginBottom: 8 }}>
    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</div>
    <div style={{
      fontSize: 13,
      color: 'var(--ink-1)',
      fontWeight: bold ? 600 : 500,
      fontFamily: mono ? 'var(--font-mono)' : undefined,
      whiteSpace: multiline ? 'pre-wrap' : 'normal',
      marginTop: 2,
    }}>{value}</div>
  </div>;
}

function ScoreTile({ label, score }: { label: string; score: number }) {
  const tone = score >= 16 ? 'var(--risk-crit)' : score >= 12 ? 'var(--risk-vhigh)' : score >= 8 ? 'var(--risk-high)' : score >= 4 ? 'var(--risk-med)' : 'var(--risk-low)';
  return <div className="kpi" style={{ padding: 12 }}>
    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>{label}</div>
    <div style={{ display: 'inline-block', marginTop: 6, padding: '2px 12px', background: tone, color: '#fff', borderRadius: 'var(--r-pill)', fontFamily: 'var(--font-mono)', fontSize: 22, fontWeight: 700 }}>{score}</div>
  </div>;
}

function FieldTile({ label, value }: { label: string; value: string }) {
  return <div className="kpi" style={{ padding: 12 }}>
    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>{label}</div>
    <div style={{ marginTop: 6, fontSize: 18, fontWeight: 600, color: 'var(--ink-1)' }}>{value}</div>
  </div>;
}
