import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Fingerprint, ListChecks, ShieldCheck, MessageSquare, Link2, Send, CheckCheck, X as XIcon, RefreshCw } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { DetailLayout } from '../../components/layout/DetailLayout';
import { RailField, RailFieldGroup } from '../../components/layout/DetailRail';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LinkedChangesPanel } from '../../components/ui/LinkedChangesPanel';
import { dpiaApi, type DpiaDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  DPIAS, DPIA_QUESTIONS_BY_ID, DPIA_MITIGATIONS_BY_ID, DPIA_REVIEWS_BY_ID,
} from '../privacy/mockPrivacy';

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
  const toast = useToast();
  const [item, setItem] = useState<DpiaDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<TabId>('overview');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    dpiaApi.get(Number(id))
      .then(r => { setItem(r.dpia); })
      .catch(() => setItem(DPIAS.find(d => d.id === Number(id)) as DpiaDto ?? null))
      .finally(() => setLoading(false));
  }, [id]);

  function transitionDpia(toStatus: string, msgKey: string) {
    if (!item) return;
    setItem({ ...item, status: toStatus });
    toast.success(t(msgKey));
  }

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
        actions={<>
          {item.status === 'DRAFT' && <Button variant="primary" Icon={Send} onClick={() => transitionDpia('IN_REVIEW', 'dpiaActSubmitReview')}>{t('dpiaActSubmitReview')}</Button>}
          {item.status === 'IN_REVIEW' && <Button variant="primary" Icon={CheckCheck} onClick={() => transitionDpia('APPROVED', 'dpiaActApprove')}>{t('dpiaActApprove')}</Button>}
          {item.status === 'IN_REVIEW' && <Button variant="ghost" Icon={XIcon} onClick={() => transitionDpia('REJECTED', 'dpiaActReject')}>{t('dpiaActReject')}</Button>}
          {(item.status === 'APPROVED' || item.status === 'EXPIRED') && <Button variant="secondary" Icon={RefreshCw} onClick={() => transitionDpia('IN_REVIEW', 'dpiaActPeriodicReview')}>{t('dpiaActPeriodicReview')}</Button>}
        </>}
      />

      <DetailLayout
        rail={<>
          <Card title={t('detailAtAGlance')} Icon={Fingerprint}>
            <RailFieldGroup>
              <RailField
                label={t('fldStatus')}
                accent
                value={<Badge tone={STATUS_TONE[item.status] ?? 'neutral'} dot>{item.status.replace(/_/g, ' ')}</Badge>}
              />
              {item.overallResidualRisk && (
                <RailField
                  label={t('dpiaResidualRisk')}
                  value={<Badge tone={RISK_TONE[item.overallResidualRisk] ?? 'neutral'}>{item.overallResidualRisk}</Badge>}
                />
              )}
              <RailField label={t('dpiaTrigger')}        value={item.triggerReason.replace(/_/g, ' ')} />
              <RailField label={t('colDate')}            value={item.assessmentDate ?? '—'} mono />
              <RailField label={t('fldNextReviewDate')}  value={item.nextReviewDate ?? '—'} mono />
            </RailFieldGroup>
          </Card>
          <LinkedChangesPanel entityType="DPIA" entityId={item.id} />
        </>}
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <Card title={t('detailTabOverview')} Icon={Fingerprint}>
              <Field label={t('colTitle')} value={title} bold />
              <Field label={t('dpiaTrigger')} value={item.triggerReason.replace(/_/g, ' ')} />
              <Field label={t('dpiaResidualRisk')} value={item.overallResidualRisk ?? '—'} />
              <Field label={t('colDate')} value={item.assessmentDate ?? '—'} mono />
              <Field label={t('fldNextReviewDate')} value={item.nextReviewDate ?? '—'} mono />
              {item.conclusion && <Field label={t('detailConclusion')} value={item.conclusion} multiline />}
              {item.rejectionReason && <Field label={t('detailRejectionReason')} value={item.rejectionReason} multiline />}
            </Card>
          )}

      {tab === 'questions' && (() => {
        const rows = DPIA_QUESTIONS_BY_ID[item.id] ?? [];
        return (
          <Card title={t('detailTabQuestions')} Icon={ListChecks} action={<Badge tone="primary">{rows.length}</Badge>}>
            {rows.length === 0
              ? <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailQuestionsHint')}</div>
              : <div style={{ display: 'flex', flexDirection: 'column' }}>
                  {rows.map(q => (
                    <div key={q.id} style={{ padding: '10px 0', borderBottom: '1px dashed var(--border)' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                        <Badge tone="info">{q.section}</Badge>
                        <Badge tone={q.risk === 'CRITICAL' ? 'danger' : q.risk === 'HIGH' ? 'warning' : q.risk === 'MEDIUM' ? 'accent' : 'success'}>{q.risk}</Badge>
                      </div>
                      <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 2 }}>{q.question}</div>
                      <div style={{ fontSize: 12, color: 'var(--ink-2)' }}>{q.answer}</div>
                    </div>
                  ))}
                </div>}
          </Card>
        );
      })()}
      {tab === 'mitigations' && (() => {
        const rows = DPIA_MITIGATIONS_BY_ID[item.id] ?? [];
        return (
          <Card title={t('detailTabMitigations')} Icon={ShieldCheck} action={<Badge tone="primary">{rows.length}</Badge>}>
            {rows.length === 0
              ? <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailMitigationsHint')}</div>
              : <div style={{ display: 'flex', flexDirection: 'column' }}>
                  {rows.map(m => (
                    <div key={m.id} style={{ padding: '10px 0', borderBottom: '1px dashed var(--border)' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                        <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>{m.mitigation}</span>
                        <Badge tone={m.status === 'IMPLEMENTED' ? 'success' : m.status === 'IN_PROGRESS' ? 'info' : 'warning'}>{m.status.replace(/_/g, ' ')}</Badge>
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 2 }}>Mitigates: <em>{m.risk}</em></div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>Owner: <strong style={{ color: 'var(--ink-2)' }}>{m.owner}</strong> · due <span className="mono">{m.due}</span></div>
                    </div>
                  ))}
                </div>}
          </Card>
        );
      })()}
      {tab === 'reviews' && (() => {
        const rows = DPIA_REVIEWS_BY_ID[item.id] ?? [];
        return (
          <Card title={t('detailTabReviews')} Icon={MessageSquare} action={<Badge tone="primary">{rows.length}</Badge>}>
            {rows.length === 0
              ? <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailReviewsHint')}</div>
              : <div style={{ display: 'flex', flexDirection: 'column' }}>
                  {rows.map(r => (
                    <div key={r.id} style={{ display: 'grid', gridTemplateColumns: '110px 200px 1fr 130px', gap: 10, padding: '8px 0', borderBottom: '1px dashed var(--border)', alignItems: 'center' }}>
                      <span className="mono" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{r.date}</span>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 600 }}>{r.reviewer}</div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.role}</div>
                      </div>
                      <span style={{ fontSize: 12, color: 'var(--ink-2)' }}>{r.comments}</span>
                      <Badge tone={r.outcome === 'APPROVED' ? 'success' : r.outcome === 'CHANGES_REQUESTED' ? 'warning' : 'danger'}>{r.outcome.replace(/_/g, ' ')}</Badge>
                    </div>
                  ))}
                </div>}
          </Card>
        );
      })()}
      {tab === 'links' && (
        <Card title={t('detailTabLinks')} Icon={Link2}>
          <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>
            Linked records appear in the right rail. Use the rail's Linked Changes card to navigate to related change requests.
          </div>
        </Card>
      )}
        </>}
      />
    </Shell>
  );
}

function Field({ label, value, bold, mono, multiline }: { label: string; value: string; bold?: boolean; mono?: boolean; multiline?: boolean }) {
  return <div style={{ marginBottom: 8 }}>
    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</div>
    <div style={{ fontSize: 13, color: 'var(--ink-1)', fontWeight: bold ? 600 : 500, fontFamily: mono ? 'var(--font-mono)' : undefined, whiteSpace: multiline ? 'pre-wrap' : 'normal', marginTop: 2 }}>{value}</div>
  </div>;
}
