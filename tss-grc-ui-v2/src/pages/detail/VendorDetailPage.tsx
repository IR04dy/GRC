import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Truck, ListChecks, FileCheck2, Link2 } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { LinkedChangesPanel } from '../../components/ui/LinkedChangesPanel';
import { tprmApi, type TprmVendorDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { VENDOR_ASSESSMENTS_BY_ID, VENDOR_CONTRACTS_BY_ID } from '../tprm/mockTprm';

type TabId = 'overview' | 'assessments' | 'contracts' | 'links';

const RISK_TONE: Record<string, BadgeTone> = {
  LOW: 'success', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger',
};

export function VendorDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t, lang } = useI18n();
  const [item, setItem] = useState<TprmVendorDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<TabId>('overview');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    tprmApi.vendors(0, 200)
      .then(p => setItem(p.content?.find(v => v.id === Number(id)) ?? null))
      .catch(() => setItem(null))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <Shell title={t('modThirdParty')}><div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div></Shell>;
  if (!item)   return <Shell title={t('modThirdParty')}><div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>Not found</div></Shell>;

  const name = lang === 'ar' && item.vendorNameAr ? item.vendorNameAr : item.vendorName;
  const tabs: TabItem<TabId>[] = [
    { id: 'overview',    label: t('detailTabOverview') },
    { id: 'assessments', label: t('detailTabAssessments') },
    { id: 'contracts',   label: t('detailTabContracts') },
    { id: 'links',       label: t('detailTabLinks') },
  ];

  return (
    <Shell title={t('modThirdParty')}>
      <DetailHeader
        breadcrumbs={[{ label: t('modThirdParty'), to: '/tprm' }, { label: name }]}
        eyebrow={t('modThirdParty')}
        title={name}
        sub={item.description}
        badges={<>
          <Badge tone={RISK_TONE[item.riskRating] ?? 'neutral'} dot>{item.riskRating}</Badge>
          <Badge tone={item.status === 'ACTIVE' ? 'success' : 'neutral'}>{item.status}</Badge>
          {item.vendorType && <Badge tone="primary">{item.vendorType}</Badge>}
          {item.country && <Badge tone="info">{item.country}</Badge>}
        </>}
      />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 16 }}>
          <Card title={t('detailTabOverview')} Icon={Truck}>
            <Field label={t('vendorFldName')}      value={name} bold />
            <Field label={t('vendorFldType')}      value={item.vendorType ?? '—'} />
            <Field label={t('vendorFldCountry')}   value={item.country ?? '—'} />
            <Field label={t('vendorFldRisk')}      value={item.riskRating} />
            <Field label={t('vendorFldStatus')}    value={item.status} />
            <Field label={t('vendorFldContractEnd')} value={item.contractEndDate ?? '—'} mono />
            <Field label={t('detailDescription')}  value={item.description ?? '—'} multiline />
          </Card>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <LinkedChangesPanel entityType="VENDOR" entityId={item.id} />
          </div>
        </div>
      )}

      {tab === 'assessments' && (
        <Card title={t('detailTabAssessments')} Icon={ListChecks}
          action={<Badge tone="primary">{(VENDOR_ASSESSMENTS_BY_ID[item.id] ?? []).length}</Badge>}>
          {(VENDOR_ASSESSMENTS_BY_ID[item.id] ?? []).length === 0
            ? <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailAssessmentsHint')}</div>
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {(VENDOR_ASSESSMENTS_BY_ID[item.id] ?? []).map(a => (
                  <div key={a.id} style={{ display: 'grid', gridTemplateColumns: '110px 80px 1fr 80px 90px', gap: 10, padding: '8px 0', borderBottom: '1px dashed var(--border)', alignItems: 'center' }}>
                    <span className="mono" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{a.date}</span>
                    <Badge tone={a.type === 'PERIODIC' ? 'info' : a.type === 'TRIGGERED' ? 'warning' : 'primary'}>{a.type}</Badge>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600 }}>{a.assessor}</div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{a.findings}</div>
                    </div>
                    <span className="mono" style={{ fontSize: 13, fontWeight: 700 }}>{a.score}</span>
                    <Badge tone={a.risk === 'LOW' ? 'success' : a.risk === 'MEDIUM' ? 'info' : a.risk === 'HIGH' ? 'warning' : 'danger'}>{a.risk}</Badge>
                  </div>
                ))}
              </div>}
        </Card>
      )}
      {tab === 'contracts' && (
        <Card title={t('detailTabContracts')} Icon={FileCheck2}
          action={<Badge tone="primary">{(VENDOR_CONTRACTS_BY_ID[item.id] ?? []).length}</Badge>}>
          {(VENDOR_CONTRACTS_BY_ID[item.id] ?? []).length === 0
            ? <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailContractsHint')}</div>
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {(VENDOR_CONTRACTS_BY_ID[item.id] ?? []).map(c => (
                  <div key={c.id} style={{ display: 'grid', gridTemplateColumns: '140px 1fr 90px 110px 110px 100px', gap: 10, padding: '8px 0', borderBottom: '1px dashed var(--border)', alignItems: 'center' }}>
                    <span className="mono" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{c.contractNumber}</span>
                    <span style={{ fontSize: 13, fontWeight: 600 }}>{c.titleEn}</span>
                    <Badge tone="neutral">{c.type}</Badge>
                    <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{c.startDate} → {c.endDate}</span>
                    <span className="mono" style={{ fontSize: 12, fontWeight: 700, textAlign: 'end' }}>{c.value.toLocaleString()} {c.currency}</span>
                    <Badge tone={c.status === 'ACTIVE' ? 'success' : c.status === 'EXPIRING_SOON' ? 'warning' : 'neutral'}>{c.status.replace(/_/g, ' ')}</Badge>
                  </div>
                ))}
              </div>}
        </Card>
      )}
      {tab === 'links'       && <Card title={t('detailTabLinks')}       Icon={Link2}><LinkedChangesPanel entityType="VENDOR" entityId={item.id} /></Card>}
    </Shell>
  );
}

function Field({ label, value, bold, mono, multiline }: { label: string; value: string; bold?: boolean; mono?: boolean; multiline?: boolean }) {
  return <div style={{ marginBottom: 8 }}>
    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</div>
    <div style={{ fontSize: 13, color: 'var(--ink-1)', fontWeight: bold ? 600 : 500, fontFamily: mono ? 'var(--font-mono)' : undefined, whiteSpace: multiline ? 'pre-wrap' : 'normal', marginTop: 2 }}>{value}</div>
  </div>;
}
