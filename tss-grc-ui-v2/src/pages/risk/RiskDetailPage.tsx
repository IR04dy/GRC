import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { AlertTriangle, Activity, ListChecks, MessageSquare, Link2, Edit, Send, CheckCheck, ShieldOff, Archive, RotateCcw } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LinkedChangesPanel } from '../../components/ui/LinkedChangesPanel';
import { riskApi, ApiError, type RiskResponse } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';

type TabId = 'overview' | 'assessments' | 'treatments' | 'kris' | 'links' | 'history';

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
  const [risk, setRisk] = useState<RiskResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>('overview');

  async function load() {
    if (!id) return;
    setLoading(true); setError(null);
    try { setRisk(await riskApi.get(Number(id))); }
    catch (e) { setError(e instanceof ApiError ? e.message : 'Failed to load'); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [id]);

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

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 16 }}>
          <Card title={t('detailTabOverview')} Icon={AlertTriangle}>
            <Field label={t('riskColName')} value={title} bold />
            <Field label={t('detailDescription')} value={risk.descriptionEn ?? '—'} multiline />
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 12 }}>
              <Field label={t('riskColInherent')} value={`L${risk.inherentLikelihood} × I${risk.inherentImpact} = ${risk.inherentScore}`} />
              <Field label={t('riskColResidual')} value={risk.residualScore != null ? `L${risk.residualLikelihood} × I${risk.residualImpact} = ${risk.residualScore}` : '—'} />
              <Field label={t('riskColCategory')} value={lang === 'ar' && risk.categoryNameAr ? risk.categoryNameAr : (risk.categoryNameEn ?? risk.categoryName ?? '—')} />
              <Field label={t('detailReviewDate')} value={risk.reviewDate ?? '—'} mono />
            </div>
          </Card>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <LinkedChangesPanel entityType="RISK" entityId={risk.id} />
            <Card title={t('detailQuickActions')} Icon={Edit}>
              <Link to={`/risk?focus=${risk.id}`} style={{ fontSize: 13, color: 'var(--primary)' }}>{t('detailEditInRegister')}</Link>
            </Card>
          </div>
        </div>
      )}

      {tab === 'assessments' && (
        <Card title={t('detailTabAssessments')} Icon={Activity}>
          <div style={{ fontSize: 13, color: 'var(--ink-3)', padding: 12 }}>
            {t('detailAssessmentsHint')}
          </div>
        </Card>
      )}

      {tab === 'treatments' && (
        <Card title={t('detailTabTreatments')} Icon={ListChecks}>
          <div style={{ fontSize: 13, color: 'var(--ink-3)', padding: 12 }}>
            {t('detailTreatmentsHint')}
          </div>
        </Card>
      )}

      {tab === 'kris' && (
        <Card title={t('detailTabKris')} Icon={Activity}>
          <div style={{ fontSize: 13, color: 'var(--ink-3)', padding: 12 }}>
            {t('detailKrisHint')}
          </div>
        </Card>
      )}

      {tab === 'links' && (
        <div style={{ display: 'grid', gap: 16 }}>
          <LinkedChangesPanel entityType="RISK" entityId={risk.id} />
          <Card title={t('detailTabLinks')} Icon={Link2}>
            <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{t('detailLinksHint')}</div>
          </Card>
        </div>
      )}

      {tab === 'history' && (
        <Card title={t('detailTabHistory')} Icon={MessageSquare}>
          <div style={{ fontSize: 13, color: 'var(--ink-3)', padding: 12 }}>
            {t('detailHistoryHint')}
          </div>
        </Card>
      )}
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
