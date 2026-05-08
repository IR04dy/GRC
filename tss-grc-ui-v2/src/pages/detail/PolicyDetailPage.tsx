import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { BookCopy, History, Award, Link2, Send, CheckCheck, Megaphone, RefreshCw, Archive } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { DetailLayout } from '../../components/layout/DetailLayout';
import { RailField, RailFieldGroup } from '../../components/layout/DetailRail';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LinkedChangesPanel } from '../../components/ui/LinkedChangesPanel';
import { complianceApi, frameworkApi, type PolicyDto, type PolicyMappingDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { EntityAnchorsPanel } from '../../components/authority/EntityAnchorsPanel';
import { POLICY_ANCHORS_BY_ID } from '../../lib/entityAnchors';
import { POLICIES, POLICY_LIFECYCLE_EVENTS_BY_ID } from '../compliance/mockCompliance';
import { PolicyLifecycleStrip } from '../../components/compliance/PolicyLifecycleStrip';
import { PolicyVersionHistory } from '../../components/compliance/PolicyVersionHistory';
import type { PolicyStage } from '../../lib/policyLifecycle';

type TabId = 'overview' | 'mappings' | 'lifecycle' | 'versions' | 'links';

const STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT: 'neutral', LEGAL_REVIEW: 'info', EXEC_APPROVAL: 'accent',
  PUBLISHED: 'success', REVIEW_DUE: 'warning', RETIRED: 'neutral', REJECTED: 'danger',
};

export function PolicyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t, lang } = useI18n();
  const toast = useToast();
  const [item, setItem] = useState<PolicyDto | null>(null);
  const [mappings, setMappings] = useState<PolicyMappingDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<TabId>('overview');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([
      complianceApi.policies(0, 200)
        .then(p => p.content?.find(p => p.id === Number(id)) ?? null)
        .catch(() => POLICIES.find(p => p.id === Number(id)) ?? null),
      frameworkApi.mappingsFor(Number(id)).catch(() => [] as PolicyMappingDto[]),
    ]).then(([p, m]) => { setItem(p); setMappings(m); }).finally(() => setLoading(false));
  }, [id]);

  // Demo-mode workflow transitions — mutate local item.status + audit toast.
  // (Real backend wires would call complianceApi.policy{SubmitForReview,Approve,Publish,…}
  //  but those don't exist; in this build we surface the lifecycle so the
  //  detail page is no longer read-only.)
  function transitionPolicy(toStatus: string, msgKey: string) {
    if (!item) return;
    setItem({ ...item, status: toStatus });
    toast.success(t(msgKey));
  }

  if (loading) return <Shell title={t('modCompliance')}><div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div></Shell>;
  if (!item)   return <Shell title={t('modCompliance')}><div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>Not found</div></Shell>;

  const title = lang === 'ar' && item.titleAr ? item.titleAr : item.titleEn;
  const tabs: TabItem<TabId>[] = [
    { id: 'overview',  label: t('detailTabOverview') },
    { id: 'mappings',  label: t('detailTabMappings') },
    { id: 'versions',  label: t('policyTabVersions') },
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
        actions={<>
          {item.status === 'DRAFT' && <Button variant="primary"   Icon={Send}      onClick={() => transitionPolicy('LEGAL_REVIEW',  'policyActSubmitLegal')}>{t('policyActSubmitLegal')}</Button>}
          {item.status === 'LEGAL_REVIEW' && <Button variant="primary"   Icon={CheckCheck} onClick={() => transitionPolicy('EXEC_APPROVAL', 'policyActSendExec')}>{t('policyActSendExec')}</Button>}
          {item.status === 'EXEC_APPROVAL' && <Button variant="primary" Icon={Megaphone}  onClick={() => transitionPolicy('PUBLISHED',     'policyActPublish')}>{t('policyActPublish')}</Button>}
          {item.status === 'PUBLISHED' && <Button variant="secondary" Icon={RefreshCw} onClick={() => transitionPolicy('REVIEW_DUE',    'policyActMarkReview')}>{t('policyActMarkReview')}</Button>}
          {item.status === 'REVIEW_DUE' && <Button variant="primary"   Icon={CheckCheck} onClick={() => transitionPolicy('PUBLISHED',     'policyActReaffirm')}>{t('policyActReaffirm')}</Button>}
          {(item.status === 'PUBLISHED' || item.status === 'REVIEW_DUE') && <Button variant="ghost" Icon={Archive} onClick={() => transitionPolicy('RETIRED',       'policyActRetire')}>{t('policyActRetire')}</Button>}
        </>}
      />

      <DetailLayout
        rail={<>
          <Card title={t('detailAtAGlance')} Icon={BookCopy}>
            <RailFieldGroup>
              <RailField
                label={t('fldStatus')}
                accent
                value={<Badge tone={STATUS_TONE[item.status] ?? 'neutral'} dot>{(item.status ?? '').replace(/_/g, ' ')}</Badge>}
              />
              <RailField label={t('fldCode')}     value={item.code ?? '—'} mono />
              <RailField label={t('fldVersion')}  value={`v${item.version ?? '—'}`} mono />
              <RailField label={t('fldCategory')} value={(item.category ?? '').replace(/_/g, ' ')} />
              <RailField label={t('complianceFldEffectiveFrom')} value={item.effectiveFrom ?? '—'} mono />
              <RailField label={t('fldNextReviewDate')}          value={item.nextReviewDate ?? '—'} mono />
            </RailFieldGroup>
          </Card>
          <EntityAnchorsPanel
            anchors={POLICY_ANCHORS_BY_ID[item.id] ?? []}
            subtitle="Authorities this policy satisfies." />
          <LinkedChangesPanel entityType="POLICY" entityId={item.id} />
        </>}
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <>
              {/* Sprint 6 — visual lifecycle strip on top of the overview */}
              <div style={{ marginBottom: 16 }}>
                <PolicyLifecycleStrip current={item.status as PolicyStage} />
              </div>
              <Card title={t('detailTabOverview')} Icon={BookCopy}>
                <Field label={t('fldCode')}     value={item.code ?? '—'} mono bold />
                <Field label={t('colTitle')}    value={title} bold />
                <Field label={t('fldVersion')}  value={item.version ?? '—'} mono />
                <Field label={t('fldCategory')} value={(item.category ?? '').replace(/_/g, ' ')} />
                <Field label={t('complianceFldEffectiveFrom')} value={item.effectiveFrom ?? '—'} mono />
                <Field label={t('fldNextReviewDate')} value={item.nextReviewDate ?? '—'} mono />
                <Field label={t('complianceFldPolicyContent')} value={item.contentEn ?? '—'} multiline />
              </Card>
            </>
          )}

          {tab === 'versions' && (
            <PolicyVersionHistory policyId={item.id} />
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
      {tab === 'lifecycle' && (
        <Card title={t('detailTabLifecycle')} Icon={History}
          action={<Badge tone="primary">{(POLICY_LIFECYCLE_EVENTS_BY_ID[item.id] ?? []).length}</Badge>}>
          {(POLICY_LIFECYCLE_EVENTS_BY_ID[item.id] ?? []).length === 0
            ? <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailLifecycleHint')}</div>
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {(POLICY_LIFECYCLE_EVENTS_BY_ID[item.id] ?? []).map(ev => (
                  <div key={ev.id} style={{ display: 'grid', gridTemplateColumns: '160px 140px 1fr', gap: 12, padding: '8px 0', borderBottom: '1px dashed var(--border)', alignItems: 'flex-start' }}>
                    <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{ev.at.replace('T', ' ').slice(0, 16)}</span>
                    <Badge tone={ev.stage === 'PUBLISHED' ? 'success' : ev.stage === 'EXEC_APPROVAL' ? 'accent' : ev.stage === 'LEGAL_REVIEW' ? 'info' : ev.stage === 'REVIEW_DUE' ? 'warning' : ev.stage === 'RETIRED' ? 'neutral' : 'primary'}>{ev.stage.replace(/_/g, ' ')}</Badge>
                    <div>
                      <div style={{ fontSize: 13, color: 'var(--ink-1)' }}>{ev.message}</div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>by {ev.actor}</div>
                    </div>
                  </div>
                ))}
              </div>}
        </Card>
      )}
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
