import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { AlertTriangle, Activity, ListChecks, MessageSquare, Edit, Send, CheckCheck, ShieldOff, Archive, RotateCcw, Calculator } from 'lucide-react';
import { CommsPanel } from '../../components/comms/CommsPanel';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { DetailLayout } from '../../components/layout/DetailLayout';
import { RailField, RailFieldGroup } from '../../components/layout/DetailRail';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Field } from '../../components/ui/Field';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { EvidenceRequestsField } from '../../components/ui/EvidenceRequestsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LinkedChangesPanel } from '../../components/ui/LinkedChangesPanel';
import { riskApi, ApiError, type RiskResponse } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { EntityAnchorsPanel } from '../../components/authority/EntityAnchorsPanel';
import { EntityActivityTimeline } from '../../components/audit/EntityActivityTimeline';
import { EntityAutomationRuns } from '../../components/audit/EntityAutomationRuns';
import { RISK_ANCHORS_BY_ID } from '../../lib/entityAnchors';
import {
  MOCK_RISKS,
  RISK_ASSESSMENTS_BY_ID, RISK_TREATMENTS_BY_ID,
  RISK_KRIS_BY_ID, RISK_HISTORY_BY_ID,
} from './mockRisks';
import {
  listAssessments, recordAssessment, correctAssessment,
  listTreatments, createTreatment, updateTreatment, transitionTreatment, deleteTreatment,
  treatmentTypeTone, treatmentStatusTone, nextTreatmentStatuses,
  type RiskAssessment, type RiskTreatment, type RiskTreatmentType, type RiskTreatmentStatus,
} from '../../lib/riskAdvanced';
import { ermGap } from '../../lib/buyerGap';
import { RiskLinksPanel } from '../../components/risk/RiskLinksPanel';
import { EmptyState } from '../../components/ui/EmptyState';
import '../../styles/pages/risk-detail.css';

type TabId = 'overview' | 'assessments' | 'treatments' | 'kris' | 'links' | 'history' | 'comms';

/** Map a numeric risk score to a CSS class that paints the score pill with
 *  the appropriate `--risk-*` token. Pure helper — no JSX. */
function scoreToneClass(score: number): string {
  if (score >= 16) return 'risk-tone-crit';
  if (score >= 12) return 'risk-tone-vhigh';
  if (score >= 8)  return 'risk-tone-high';
  if (score >= 4)  return 'risk-tone-med';
  return 'risk-tone-low';
}

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
    const notesAr = await dialog.prompt({
      title: 'Record re-assessment',
      label: 'Notes — Arabic / ملاحظات بالعربية (optional)', type: 'textarea',
    });
    try {
      await recordAssessment(risk.id, {
        likelihood: Number(lAns), impact: Number(iAns),
        assessmentNotesEn: notes ?? undefined,
        assessmentNotesAr: notesAr ?? undefined,
      });
      toast.success('Assessment recorded — residual score updated');
      setLiveAssessments(await listAssessments(risk.id));
      // Refresh the Risk to pick up the new residual snapshot.
      try { setRisk(await riskApi.get(risk.id)); } catch { /* ignore */ }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to record assessment');
    }
  }

  // E-B — re-derive residual from the aggregate control posture (effectiveness
  // weighted by contribution). Complements the auto-recompute on control link.
  async function onRecomputeResidual() {
    if (!risk) return;
    const ar = lang === 'ar';
    try {
      const r = await ermGap.recomputeResidual(risk.id);
      if (!r.applied) {
        toast.warning(ar
          ? 'تعذّر إعادة الاحتساب — لا توجد درجة كامنة أو المخاطرة في حالة نهائية'
          : 'Not recomputed — no inherent baseline, or risk is in a terminal state');
      } else {
        const pct = Math.round(r.aggregateMitigation * 100);
        toast.success(ar
          ? `أُعيد احتساب المتبقّي من ${r.linkedControls} ضابط — تخفيف ${pct}٪ — ${r.previousResidualScore ?? '—'} ← ${r.newResidualScore ?? '—'}${r.uncontrolled ? ' (بدون ضوابط)' : ''}`
          : `Residual re-derived from ${r.linkedControls} control(s) — ${pct}% mitigation — ${r.previousResidualScore ?? '—'} → ${r.newResidualScore ?? '—'}${r.uncontrolled ? ' · uncontrolled' : ''}`);
      }
      try { setRisk(await riskApi.get(risk.id)); } catch { /* ignore */ }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to recompute residual');
    }
  }

  // Correct an existing assessment in place (typo / mis-keyed score). The
  // endpoint takes likelihood / impact / English notes (null-means-keep) and
  // re-mirrors the parent risk's residual score when this is still the latest
  // assessment. Prompts are prefilled with the row's current values; the
  // Arabic note isn't carried by this endpoint so only the English note shows.
  async function onCorrectAssessment(a: RiskAssessment) {
    if (!risk) return;
    const ar = lang === 'ar';
    const lAns = await dialog.prompt({
      title: ar ? 'تصحيح التقييم' : 'Correct assessment',
      label: ar ? 'الاحتمالية (1..5)' : 'Likelihood (1..5)', type: 'number',
      required: true, min: 1, max: 5, initial: String(a.likelihood),
      submitLabel: ar ? 'حفظ' : 'Save',
    });
    if (lAns === null) return;
    const iAns = await dialog.prompt({
      title: ar ? 'تصحيح التقييم' : 'Correct assessment',
      label: ar ? 'الأثر (1..5)' : 'Impact (1..5)', type: 'number',
      required: true, min: 1, max: 5, initial: String(a.impact),
      submitLabel: ar ? 'حفظ' : 'Save',
    });
    if (iAns === null) return;
    const notes = await dialog.prompt({
      title: ar ? 'تصحيح التقييم' : 'Correct assessment',
      label: ar ? 'ملاحظات (اختياري)' : 'Notes (optional)', type: 'textarea',
      initial: a.assessmentNotesEn ?? '',
      submitLabel: ar ? 'حفظ' : 'Save',
    });
    if (notes === null) return;
    try {
      await correctAssessment(a.id, {
        likelihood: Number(lAns), impact: Number(iAns),
        assessmentNotesEn: notes.trim() || undefined,
      });
      toast.success(ar ? 'تم تصحيح التقييم' : 'Assessment corrected');
      setLiveAssessments(await listAssessments(risk.id));
      try { setRisk(await riskApi.get(risk.id)); } catch { /* ignore */ }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (ar ? 'تعذّر تصحيح التقييم' : 'Failed to correct assessment'));
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
    const descriptionAr = await dialog.prompt({
      title: 'New treatment',
      label: 'Description — Arabic / الوصف بالعربية (optional)', type: 'textarea',
    });
    const due = await dialog.prompt({
      title: 'New treatment',
      label: 'Due date (YYYY-MM-DD, optional)', type: 'text',
    });
    try {
      await createTreatment(risk.id, {
        treatmentType: upper as RiskTreatmentType,
        descriptionEn: description,
        descriptionAr: descriptionAr || undefined,
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

  // Edit a planned/in-progress treatment. The update endpoint only touches
  // description / owner / due date (treatment TYPE is immutable once recorded
  // and STATUS moves via transitions), so the edit surfaces description + due
  // date, prefilled. Both prompts cancel-out (null) cleanly; an empty due date
  // is omitted so it keeps the existing value (server is null-means-keep).
  async function onEditTreatment(tr: RiskTreatment) {
    if (!risk) return;
    const ar = lang === 'ar';
    const description = await dialog.prompt({
      title: ar ? 'تعديل المعالجة' : 'Edit treatment',
      label: ar ? 'الوصف' : 'Description', type: 'textarea', required: true,
      initial: tr.descriptionEn,
      submitLabel: ar ? 'حفظ' : 'Save',
    });
    if (description === null) return;
    const due = await dialog.prompt({
      title: ar ? 'تعديل المعالجة' : 'Edit treatment',
      label: ar ? 'تاريخ الاستحقاق (YYYY-MM-DD، اختياري)' : 'Due date (YYYY-MM-DD, optional)',
      type: 'text', initial: tr.dueDate ? tr.dueDate.slice(0, 10) : '',
      submitLabel: ar ? 'حفظ' : 'Save',
    });
    if (due === null) return;
    try {
      await updateTreatment(tr.id, {
        descriptionEn: description,
        dueDate: due.trim() || undefined,
      });
      toast.success(ar ? 'تم تحديث المعالجة' : 'Treatment updated');
      setLiveTreatments(await listTreatments(risk.id));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (ar ? 'تعذّر تحديث المعالجة' : 'Failed to update treatment'));
    }
  }

  async function workflow(fn: (n: number) => Promise<RiskResponse>, msg: string) {
    if (!risk) return;
    try { const updated = await fn(risk.id); setRisk(updated); toast.success(msg); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  if (loading) {
    return <Shell title={t('modRisk')} sub="ISO 31000 + COSO ERM">
      <div className="risk-detail-loading">{t('riskLoading')}</div>
    </Shell>;
  }
  if (error || !risk) {
    return <Shell title={t('modRisk')} sub="ISO 31000 + COSO ERM">
      <div className="risk-detail-error">{error ?? 'Not found'}</div>
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
        sub={lang === 'ar' && risk.descriptionAr ? risk.descriptionAr : risk.descriptionEn}
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
          {risk.status !== 'CLOSED' &&
            <Button variant="secondary" Icon={AlertTriangle}
              title={lang === 'ar'
                ? 'رفع احتمالية المخاطرة المتبقية +1 (بحدٍّ أقصى 5) عند تحقّق حادثة مرتبطة بهذه المخاطرة'
                : 'Bump residual likelihood +1 (capped at 5) when an incident is realised for this risk'}
              onClick={() => workflow(riskApi.incidentBump, lang === 'ar' ? 'تم رفع الاحتمالية المتبقية لتحقّق حادثة' : 'Residual likelihood bumped — incident realised')}>
              {lang === 'ar' ? 'تحقّق حادثة' : 'Incident realised'}
            </Button>}
          {risk.status !== 'CLOSED' &&
            <Button variant="secondary" Icon={Calculator}
              title={lang === 'ar'
                ? 'إعادة احتساب الدرجة المتبقية من فعالية الضوابط المرتبطة (مرجّحة بنسبة المساهمة)'
                : 'Re-derive residual from linked control effectiveness (weighted by contribution)'}
              onClick={onRecomputeResidual}>
              {lang === 'ar' ? 'احتساب المتبقّي من الضوابط' : 'Recompute from controls'}
            </Button>}
        </>}
      />

      {/* Score header strip */}
      <div className="kpi-grid risk-detail-score-strip">
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
          <Card>
            <AttachmentsField entityType="RISK" entityId={risk.id} />
          </Card>
          <Card>
            {/* E-C: real evidence-request workflow (request-from-department,
                SLA reminders/escalation, review/reject, comments) — same generic
                backend as BCM/compliance, anchored on entityType="Risk". */}
            <EvidenceRequestsField entityType="Risk" entityId={risk.id} />
          </Card>
          <Card>
            <ControlMappingPicker entityType="RISK" entityId={risk.id} />
          </Card>
          <Card title={t('detailQuickActions')} Icon={Edit}>
            <Link to={`/risk?focus=${risk.id}`} className="risk-detail-quick-link">{t('detailEditInRegister')}</Link>
          </Card>
        </>}
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <Card title={t('detailTabOverview')} Icon={AlertTriangle}>
              <Field label={t('riskColName')} value={title} bold />
              <Field label={t('detailDescription')} value={(lang === 'ar' && risk.descriptionAr ? risk.descriptionAr : risk.descriptionEn) ?? '—'} multiline />
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
              <div className="risk-detail-card-action">
                <Badge tone="primary">{count}</Badge>
                {useLive && <Button size="sm" variant="primary" Icon={Edit} onClick={onRecordAssessment}>
                  Record re-assessment
                </Button>}
              </div>
            }>
            {count === 0
              ? <EmptyState Icon={Activity} title="No re-assessments yet" sub={t('detailAssessmentsHint')} />
              : useLive
                ? <div className="risk-detail-rows">
                    {live.map(a => (
                      <div key={a.id} className="risk-detail-row-assessment">
                        <span className="mono risk-detail-cell-date">{a.assessmentDate}</span>
                        <div>
                          <div className="risk-detail-cell-title">
                            L={a.likelihood} · I={a.impact}
                          </div>
                          <div className="risk-detail-cell-sub">
                            {(lang === 'ar' && a.assessmentNotesAr ? a.assessmentNotesAr : a.assessmentNotesEn) ?? '—'}
                          </div>
                        </div>
                        <span className="mono risk-detail-cell-id">
                          #{a.id}
                        </span>
                        <span className={`risk-detail-score-pill ${scoreToneClass(a.score ?? a.likelihood * a.impact)}`}>{a.score ?? a.likelihood * a.impact}</span>
                        <Button size="sm" variant="ghost" Icon={Edit}
                          title={lang === 'ar' ? 'تصحيح التقييم' : 'Correct assessment'}
                          onClick={() => onCorrectAssessment(a)}>
                          {lang === 'ar' ? 'تصحيح' : 'Correct'}
                        </Button>
                      </div>
                    ))}
                  </div>
                : <div className="risk-detail-rows">
                    {mockRows.map(a => (
                      <div key={a.id} className="risk-detail-row-assessment">
                        <span className="mono risk-detail-cell-date">{a.date}</span>
                        <div>
                          <div className="risk-detail-cell-title">{a.assessor}</div>
                          <div className="risk-detail-cell-sub">{a.notes}</div>
                        </div>
                        <span className={`risk-detail-score-pill ${scoreToneClass(a.inherentScore)}`}>{a.inherentScore}</span>
                        {a.residualScore != null
                          ? <span className={`risk-detail-score-pill ${scoreToneClass(a.residualScore)}`}>{a.residualScore}</span>
                          : <span className="mono risk-detail-cell-empty">—</span>}
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
              <div className="risk-detail-card-action">
                <Badge tone="primary">{count}</Badge>
                {useLive && <Button size="sm" variant="primary" Icon={Edit} onClick={onCreateTreatment}>
                  Add treatment
                </Button>}
              </div>
            }>
            {count === 0
              ? <EmptyState Icon={ListChecks} title="No treatments planned" sub={t('detailTreatmentsHint')} />
              : useLive
                ? <div className="risk-detail-rows">
                    {live.map(tr => (
                      <div key={tr.id} className="risk-detail-row-treatment">
                        <div className="risk-detail-row-head">
                          <div className="risk-detail-row-head__group">
                            <Badge tone={treatmentTypeTone(tr.treatmentType)}>{tr.treatmentType}</Badge>
                            <span className="risk-detail-cell-title--bold">{lang === 'ar' && tr.descriptionAr ? tr.descriptionAr : tr.descriptionEn}</span>
                          </div>
                          <Badge tone={treatmentStatusTone(tr.status)}>{tr.status.replace(/_/g, ' ')}</Badge>
                        </div>
                        <div className="risk-detail-row-meta">
                          <span>Owner: <strong className="risk-detail-row-meta-strong">{tr.ownerId ?? '—'}</strong></span>
                          <span>Due: <span className="mono">{tr.dueDate ?? '—'}</span></span>
                          {tr.completedAt && <span>Completed: <span className="mono">{tr.completedAt.substring(0, 10)}</span></span>}
                        </div>
                        <div className="risk-detail-treatment-actions">
                          {nextTreatmentStatuses(tr.status).map(next => (
                            <Button key={next} size="sm" variant="ghost"
                              onClick={() => onTransitionTreatment(tr, next)}>
                              → {next}
                            </Button>
                          ))}
                          {(tr.status === 'PLANNED' || tr.status === 'IN_PROGRESS') && (
                            <Button size="sm" variant="ghost" Icon={Edit} onClick={() => onEditTreatment(tr)}>
                              {lang === 'ar' ? 'تعديل' : 'Edit'}
                            </Button>
                          )}
                          {(tr.status === 'PLANNED' || tr.status === 'IN_PROGRESS') && (
                            <Button size="sm" variant="ghost" onClick={() => onDeleteTreatment(tr)}>
                              Delete
                            </Button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                : <div className="risk-detail-rows">
                    {mockRows.map(tr => {
                      const progressTone = tr.progressPct >= 80 ? 'success' : tr.progressPct >= 40 ? 'primary' : 'accent';
                      return (
                      <div key={tr.id} className="risk-detail-row-treatment">
                        <div className="risk-detail-row-head">
                          <div className="risk-detail-row-head__group">
                            <Badge tone={tr.type === 'AVOID' ? 'danger' : tr.type === 'TRANSFER' ? 'accent' : tr.type === 'ACCEPT' ? 'warning' : 'primary'}>{tr.type}</Badge>
                            <span className="risk-detail-cell-title--bold">{tr.action}</span>
                          </div>
                          <Badge tone={tr.status === 'IMPLEMENTED' ? 'success' : tr.status === 'IN_PROGRESS' ? 'info' : tr.status === 'CANCELLED' ? 'neutral' : 'warning'}>{tr.status.replace(/_/g, ' ')}</Badge>
                        </div>
                        <div className="risk-detail-row-meta">
                          <span>Owner: <strong className="risk-detail-row-meta-strong">{tr.owner}</strong></span>
                          <span>Due: <span className="mono">{tr.dueDate}</span></span>
                        </div>
                        <div className="risk-detail-progress">
                          <div className="risk-detail-progress__fill" data-tone={progressTone} style={{ width: `${tr.progressPct}%` }} />
                        </div>
                      </div>
                      );
                    })}
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
            : <div className="risk-detail-rows">
                {(RISK_KRIS_BY_ID[risk.id] ?? []).map(k => (
                  <div key={k.id} className="risk-detail-row-kri">
                    <span className="mono risk-detail-cell-id">{k.code}</span>
                    <div>
                      <div className="risk-detail-cell-kri-name">{k.nameEn}</div>
                      <div className="risk-detail-cell-kri-thresh">Green ≤ {k.greenMax} · Amber ≤ {k.amberMax} {k.unit}</div>
                    </div>
                    <span className="mono risk-detail-cell-kri-current">{k.current} <span className="risk-detail-cell-kri-unit">{k.unit}</span></span>
                    <Badge tone={k.band === 'RED' ? 'danger' : k.band === 'AMBER' ? 'warning' : k.band === 'GREEN' ? 'success' : 'neutral'} dot>{k.band}</Badge>
                  </div>
                ))}
              </div>}
        </Card>
      )}

      {tab === 'links' && <RiskLinksPanel riskId={risk.id} />}

      {tab === 'history' && (
        <div className="risk-detail-history-stack">
          {/* Real audit log + F8 rule runs — same reusable cards as
           *  /workflows/submissions/:id and /bcm/aars/:id (PR #29).
           *  Renders silently when there are no rows so a fresh risk
           *  without history shows just the legacy mock history below. */}
          <EntityActivityTimeline entityType="Risk" entityId={risk.id} />
          <EntityAutomationRuns   entityType="Risk" entityId={risk.id} />

          {/* Legacy mock history — kept while the audit log is backfilled
           *  with old transitions. Will be removed once Risk activity
           *  pre-dates the audit-log retention window. */}
          <Card title={t('detailTabHistory')} Icon={MessageSquare}
            action={<Badge tone="primary">{(RISK_HISTORY_BY_ID[risk.id] ?? []).length}</Badge>}>
            {(RISK_HISTORY_BY_ID[risk.id] ?? []).length === 0
              ? <EmptyState Icon={MessageSquare} title="No history yet" sub={t('detailHistoryHint')} />
              : <div className="risk-detail-rows">
                  {(RISK_HISTORY_BY_ID[risk.id] ?? []).map(h => (
                    <div key={h.id} className="risk-detail-row-history">
                      <span className="mono risk-detail-cell-id">{h.at.replace('T', ' ').slice(0, 16)}</span>
                      <Badge tone={h.type === 'CREATED' ? 'primary' : h.type === 'STATUS_CHANGE' ? 'info' : h.type === 'SCORE_CHANGE' ? 'accent' : h.type === 'TREATMENT_ADDED' ? 'success' : h.type === 'REVIEW' ? 'warning' : 'neutral'}>{h.type.replace(/_/g, ' ')}</Badge>
                      <div>
                        <div className="risk-detail-history-msg">{h.message}</div>
                        <div className="risk-detail-history-actor">by {h.actor}</div>
                      </div>
                    </div>
                  ))}
                </div>}
          </Card>
        </div>
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

function ScoreTile({ label, score }: { label: string; score: number }) {
  return <div className="kpi risk-detail-tile">
    <div className="risk-detail-tile__label">{label}</div>
    <div className={`risk-detail-tile__pill ${scoreToneClass(score)}`}>{score}</div>
  </div>;
}

function FieldTile({ label, value }: { label: string; value: string }) {
  return <div className="kpi risk-detail-tile">
    <div className="risk-detail-tile__label">{label}</div>
    <div className="risk-detail-tile__value">{value}</div>
  </div>;
}
