import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Fingerprint, ListChecks, ShieldCheck, MessageSquare, Link2 } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { LinkedChangesPanel } from '../../components/ui/LinkedChangesPanel';
import { dpiaApi, type DpiaDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';

type TabId = 'overview' | 'questions' | 'mitigations' | 'reviews' | 'links';

const STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT: 'neutral', IN_REVIEW: 'warning', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'neutral',
};
const RISK_TONE: Record<string, BadgeTone> = {
  LOW: 'success', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger',
};

export function DpiaDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t, lang } = useI18n();
  const [item, setItem] = useState<DpiaDto | null>(null);
  const [questions, setQuestions] = useState<unknown[]>([]);
  const [mitigations, setMitigations] = useState<unknown[]>([]);
  const [reviews, setReviews] = useState<unknown[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<TabId>('overview');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    dpiaApi.get(Number(id))
      .then(r => { setItem(r.dpia); setQuestions(r.questions ?? []); setMitigations(r.mitigations ?? []); setReviews(r.reviews ?? []); })
      .catch(() => setItem(null))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <Shell title={t('modPdpl')}><div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div></Shell>;
  if (!item)   return <Shell title={t('modPdpl')}><div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>Not found</div></Shell>;

  const title = lang === 'ar' && item.titleAr ? item.titleAr : item.titleEn;
  const tabs: TabItem<TabId>[] = [
    { id: 'overview',    label: t('detailTabOverview') },
    { id: 'questions',   label: t('detailTabQuestions') },
    { id: 'mitigations', label: t('detailTabMitigations') },
    { id: 'reviews',     label: t('detailTabReviews') },
    { id: 'links',       label: t('detailTabLinks') },
  ];

  return (
    <Shell title={t('modPdpl')}>
      <DetailHeader
        breadcrumbs={[{ label: t('modPdpl'), to: '/privacy' }, { label: `DPIA #${item.id}` }]}
        eyebrow={`DPIA #${item.id}`}
        title={title}
        sub={item.conclusion}
        badges={<>
          <Badge tone={STATUS_TONE[item.status] ?? 'neutral'} dot>{item.status.replace(/_/g, ' ')}</Badge>
          <Badge tone="primary">{item.triggerReason.replace(/_/g, ' ')}</Badge>
          {item.overallResidualRisk && <Badge tone={RISK_TONE[item.overallResidualRisk] ?? 'neutral'}>{item.overallResidualRisk}</Badge>}
        </>}
      />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 16 }}>
          <Card title={t('detailTabOverview')} Icon={Fingerprint}>
            <Field label={t('colTitle')} value={title} bold />
            <Field label={t('dpiaTrigger')} value={item.triggerReason.replace(/_/g, ' ')} />
            <Field label={t('dpiaResidualRisk')} value={item.overallResidualRisk ?? '—'} />
            <Field label={t('colDate')} value={item.assessmentDate ?? '—'} mono />
            <Field label={t('fldNextReviewDate')} value={item.nextReviewDate ?? '—'} mono />
            {item.conclusion && <Field label={t('detailConclusion')} value={item.conclusion} multiline />}
            {item.rejectionReason && <Field label={t('detailRejectionReason')} value={item.rejectionReason} multiline />}
          </Card>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <Card title={t('detailTabMitigations')} Icon={ShieldCheck} action={<Badge tone="primary">{mitigations.length}</Badge>}>
              <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{t('detailMitigationsHint')}</div>
            </Card>
            <LinkedChangesPanel entityType="DPIA" entityId={item.id} />
          </div>
        </div>
      )}

      {tab === 'questions'   && <Card title={t('detailTabQuestions')}   Icon={ListChecks}    action={<Badge tone="primary">{questions.length}</Badge>}><div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailQuestionsHint')}</div></Card>}
      {tab === 'mitigations' && <Card title={t('detailTabMitigations')} Icon={ShieldCheck}   action={<Badge tone="primary">{mitigations.length}</Badge>}><div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailMitigationsHint')}</div></Card>}
      {tab === 'reviews'     && <Card title={t('detailTabReviews')}     Icon={MessageSquare} action={<Badge tone="primary">{reviews.length}</Badge>}><div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailReviewsHint')}</div></Card>}
      {tab === 'links'       && <Card title={t('detailTabLinks')}       Icon={Link2}><LinkedChangesPanel entityType="DPIA" entityId={item.id} /></Card>}
    </Shell>
  );
}

function Field({ label, value, bold, mono, multiline }: { label: string; value: string; bold?: boolean; mono?: boolean; multiline?: boolean }) {
  return <div style={{ marginBottom: 8 }}>
    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</div>
    <div style={{ fontSize: 13, color: 'var(--ink-1)', fontWeight: bold ? 600 : 500, fontFamily: mono ? 'var(--font-mono)' : undefined, whiteSpace: multiline ? 'pre-wrap' : 'normal', marginTop: 2 }}>{value}</div>
  </div>;
}
