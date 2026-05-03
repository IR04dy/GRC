import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { BookCopy, History, Award, Link2 } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { LinkedChangesPanel } from '../../components/ui/LinkedChangesPanel';
import { complianceApi, frameworkApi, type PolicyDto, type PolicyMappingDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';

type TabId = 'overview' | 'mappings' | 'lifecycle' | 'links';

const STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT: 'neutral', LEGAL_REVIEW: 'info', EXEC_APPROVAL: 'accent',
  PUBLISHED: 'success', REVIEW_DUE: 'warning', RETIRED: 'neutral', REJECTED: 'danger',
};

export function PolicyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t, lang } = useI18n();
  const [item, setItem] = useState<PolicyDto | null>(null);
  const [mappings, setMappings] = useState<PolicyMappingDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<TabId>('overview');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([
      complianceApi.policies(0, 200).then(p => p.content?.find(p => p.id === Number(id)) ?? null),
      frameworkApi.mappingsFor(Number(id)).catch(() => [] as PolicyMappingDto[]),
    ]).then(([p, m]) => { setItem(p); setMappings(m); }).finally(() => setLoading(false));
  }, [id]);

  if (loading) return <Shell title={t('modCompliance')}><div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div></Shell>;
  if (!item)   return <Shell title={t('modCompliance')}><div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>Not found</div></Shell>;

  const title = lang === 'ar' && item.titleAr ? item.titleAr : item.titleEn;
  const tabs: TabItem<TabId>[] = [
    { id: 'overview',  label: t('detailTabOverview') },
    { id: 'mappings',  label: t('detailTabMappings') },
    { id: 'lifecycle', label: t('detailTabLifecycle') },
    { id: 'links',     label: t('detailTabLinks') },
  ];

  return (
    <Shell title={t('modCompliance')} sub={t('complianceSub')}>
      <DetailHeader
        breadcrumbs={[
          { label: t('modCompliance'), to: '/compliance' },
          { label: t('complianceTabPolicies'), to: '/compliance?tab=policies' },
          { label: item.code ?? `#${item.id}` },
        ]}
        eyebrow={item.code}
        title={title}
        badges={<>
          <Badge tone={STATUS_TONE[item.status] ?? 'neutral'} dot>{(item.status ?? '').replace(/_/g, ' ')}</Badge>
          {item.version && <Badge tone="primary">v{item.version}</Badge>}
          {item.category && <Badge tone="info">{item.category.replace(/_/g, ' ')}</Badge>}
        </>}
      />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 16 }}>
          <Card title={t('detailTabOverview')} Icon={BookCopy}>
            <Field label={t('fldCode')}     value={item.code ?? '—'} mono bold />
            <Field label={t('colTitle')}    value={title} bold />
            <Field label={t('fldVersion')}  value={item.version ?? '—'} mono />
            <Field label={t('fldCategory')} value={(item.category ?? '').replace(/_/g, ' ')} />
            <Field label={t('complianceFldEffectiveFrom')} value={item.effectiveFrom ?? '—'} mono />
            <Field label={t('fldNextReviewDate')} value={item.nextReviewDate ?? '—'} mono />
            <Field label={t('complianceFldPolicyContent')} value={item.contentEn ?? '—'} multiline />
          </Card>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <Card title={t('detailTabMappings')} Icon={Award}>
              {mappings.length === 0
                ? <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{t('detailMappingsEmpty')}</div>
                : mappings.map(m => (
                  <div key={m.mappingId} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '4px 0', borderBottom: '1px solid var(--border)', fontSize: 12 }}>
                    <Badge tone="primary">{m.frameworkCode}</Badge>
                    {m.clauseRef && <span className="mono" style={{ fontSize: 11 }}>{m.clauseRef}</span>}
                    <Badge tone={m.satisfaction === 'FULL' ? 'success' : m.satisfaction === 'PARTIAL' ? 'warning' : 'neutral'}>{m.satisfaction.replace(/_/g, ' ')}</Badge>
                  </div>
                ))
              }
            </Card>
            <LinkedChangesPanel entityType="POLICY" entityId={item.id} />
          </div>
        </div>
      )}

      {tab === 'mappings' && (
        <Card title={t('detailTabMappings')} Icon={Award}>
          {mappings.length === 0
            ? <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailMappingsEmpty')}</div>
            : <div style={{ display: 'grid', gap: 6, padding: 4 }}>
              {mappings.map(m => (
                <div key={m.mappingId} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: 10, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', fontSize: 13 }}>
                  <Badge tone="primary">{m.frameworkCode}{m.frameworkVersion ? ' · ' + m.frameworkVersion : ''}</Badge>
                  <span style={{ flex: 1 }}>{m.frameworkName}</span>
                  {m.clauseRef && <span className="mono" style={{ fontSize: 12 }}>{m.clauseRef}</span>}
                  <Badge tone={m.satisfaction === 'FULL' ? 'success' : m.satisfaction === 'PARTIAL' ? 'warning' : 'neutral'}>{m.satisfaction.replace(/_/g, ' ')}</Badge>
                </div>
              ))}
            </div>
          }
        </Card>
      )}
      {tab === 'lifecycle' && <Card title={t('detailTabLifecycle')} Icon={History}><div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailLifecycleHint')}</div></Card>}
      {tab === 'links'     && <Card title={t('detailTabLinks')}     Icon={Link2}><LinkedChangesPanel entityType="POLICY" entityId={item.id} /></Card>}
    </Shell>
  );
}

function Field({ label, value, bold, mono, multiline }: { label: string; value: string; bold?: boolean; mono?: boolean; multiline?: boolean }) {
  return <div style={{ marginBottom: 8 }}>
    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</div>
    <div style={{ fontSize: 13, color: 'var(--ink-1)', fontWeight: bold ? 600 : 500, fontFamily: mono ? 'var(--font-mono)' : undefined, whiteSpace: multiline ? 'pre-wrap' : 'normal', marginTop: 2 }}>{value}</div>
  </div>;
}
