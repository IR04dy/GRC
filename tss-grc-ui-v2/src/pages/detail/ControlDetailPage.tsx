import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { ClipboardCheck, FileCheck2, ListChecks, Link2 } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { LinkedChangesPanel } from '../../components/ui/LinkedChangesPanel';
import { complianceApi, type ComplianceControlDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { EntityAnchorsPanel } from '../../components/authority/EntityAnchorsPanel';
import { CONTROL_ANCHORS_BY_ID } from '../../lib/entityAnchors';

type TabId = 'overview' | 'subcontrols' | 'evidence' | 'tests' | 'links';

const STATUS_TONE: Record<string, BadgeTone> = {
  IMPLEMENTED: 'success', PARTIAL: 'warning', PLANNED: 'info', NOT_APPLICABLE: 'neutral',
};

export function ControlDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t, lang } = useI18n();
  const [item, setItem] = useState<ComplianceControlDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<TabId>('overview');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    complianceApi.controls(0, 200)
      .then(p => setItem(p.content?.find(c => c.id === Number(id)) ?? null))
      .catch(() => setItem(null))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <Shell title={t('modCompliance')}><div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div></Shell>;
  if (!item)   return <Shell title={t('modCompliance')}><div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>Not found</div></Shell>;

  const name = lang === 'ar' && item.nameAr ? item.nameAr : item.nameEn;
  const tabs: TabItem<TabId>[] = [
    { id: 'overview',    label: t('detailTabOverview') },
    { id: 'subcontrols', label: t('detailTabSubControls') },
    { id: 'evidence',    label: t('detailTabEvidence') },
    { id: 'tests',       label: t('detailTabTests') },
    { id: 'links',       label: t('detailTabLinks') },
  ];

  return (
    <Shell title={t('modCompliance')} sub={t('complianceSub')}>
      <DetailHeader
        breadcrumbs={[
          { label: t('modCompliance'), to: '/compliance' },
          { label: t('complianceTabControls'), to: '/compliance?tab=controls' },
          { label: item.code },
        ]}
        eyebrow={item.code}
        title={name}
        sub={item.description}
        badges={<>
          <Badge tone="primary">{(item.framework ?? '').replace(/_/g, ' ')}</Badge>
          <Badge tone={STATUS_TONE[item.status] ?? 'neutral'} dot>{(item.status ?? '').replace(/_/g, ' ')}</Badge>
          <Badge tone="info">L{item.maturityLevel ?? 0}</Badge>
        </>}
      />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 16 }}>
            <Card title={t('detailTabOverview')} Icon={ClipboardCheck}>
              <Field label={t('fldCode')}     value={item.code} mono bold />
              <Field label={t('colControl')}  value={name} bold />
              <Field label={t('fldDomain')}   value={item.domain ?? '—'} />
              <Field label={t('colMaturity')} value={`L${item.maturityLevel ?? 0}`} mono />
              <Field label={t('colOwner')}    value={item.owner ?? '—'} />
              <Field label={t('colFramework')} value={(item.framework ?? '').replace(/_/g, ' ')} />
              <Field label={t('detailDescription')} value={item.description ?? '—'} multiline />
            </Card>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <LinkedChangesPanel entityType="CONTROL" entityId={item.id} />
            </div>
          </div>
          <div style={{ marginTop: 16 }}>
            <EntityAnchorsPanel
              anchors={CONTROL_ANCHORS_BY_ID[item.id] ?? []}
              subtitle="Authorities + control references this control satisfies. Click any anchor to open the authority workspace." />
          </div>
        </>
      )}

      {tab === 'subcontrols' && <Card title={t('detailTabSubControls')} Icon={ListChecks}><div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailSubControlsHint')}</div></Card>}
      {tab === 'evidence'    && <Card title={t('detailTabEvidence')}    Icon={FileCheck2}><div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailEvidenceHint')}</div></Card>}
      {tab === 'tests'       && <Card title={t('detailTabTests')}       Icon={ListChecks}><div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailTestsHint')}</div></Card>}
      {tab === 'links'       && <Card title={t('detailTabLinks')}       Icon={Link2}><LinkedChangesPanel entityType="CONTROL" entityId={item.id} /></Card>}
    </Shell>
  );
}

function Field({ label, value, bold, mono, multiline }: { label: string; value: string; bold?: boolean; mono?: boolean; multiline?: boolean }) {
  return <div style={{ marginBottom: 8 }}>
    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</div>
    <div style={{ fontSize: 13, color: 'var(--ink-1)', fontWeight: bold ? 600 : 500, fontFamily: mono ? 'var(--font-mono)' : undefined, whiteSpace: multiline ? 'pre-wrap' : 'normal', marginTop: 2 }}>{value}</div>
  </div>;
}
