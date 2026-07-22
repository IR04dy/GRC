import { useEffect, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useParams } from 'react-router-dom';
import { BookCopy, Award, Link2, CheckCheck, ArrowRight } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { DetailLayout } from '../../components/layout/DetailLayout';
import { RailField, RailFieldGroup } from '../../components/layout/DetailRail';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Field } from '../../components/ui/Field';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { SignaturePanel } from '../../components/ui/SignaturePanel';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LinkedChangesPanel } from '../../components/ui/LinkedChangesPanel';
import { EntityActivityTimeline } from '../../components/audit/EntityActivityTimeline';
import { PolicyPublishApprovalButton } from '../../components/workflow/ApprovalRequestForms';
import { ModuleEngineRequestsPanel } from '../../components/workflows/ModuleEngineRequestsPanel';
import { frameworkApi, ApiError, type PolicyMappingDto } from '../../lib/api';
import {
  getPolicy, transitionPolicy, policyStatusTone, policyStatusLabel, nextPolicyStatuses,
  type PolicyDocument, type PolicyStatus,
} from '../../lib/policy';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { EntityAnchorsPanel } from '../../components/authority/EntityAnchorsPanel';
import { POLICY_ANCHORS_BY_ID } from '../../lib/entityAnchors';

type TabId = 'overview' | 'mappings' | 'docs' | 'signoff' | 'links' | 'activity';

export function PolicyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t, lang } = useI18n();
  const toast = useToast();
  const [item, setItem] = useState<PolicyDocument | null>(null);
  const [mappings, setMappings] = useState<PolicyMappingDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [tab, setTab] = useState<TabId>('overview');

  // CH-9: read the canonical register (policy.documents). No mock fallback — a
  // bad/unknown id honestly shows "Not found" instead of fabricated demo data.
  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([
      getPolicy(Number(id)).catch(() => null),
      frameworkApi.mappingsFor(Number(id)).catch(() => [] as PolicyMappingDto[]),
    ]).then(([p, m]) => { setItem(p); setMappings(m); }).finally(() => setLoading(false));
  }, [id]);

  // Real lifecycle transition against the canonical API (replaces the former
  // demo-only local status flip). The backend enforces the state machine + SoD.
  async function runTransition(to: PolicyStatus) {
    if (!item?.id || busy) return;
    setBusy(true);
    try {
      const updated = await transitionPolicy(item.id, to);
      setItem(updated);
      toast.success(`${lang === 'ar' ? '→ ' : '→ '}${policyStatusLabel(to, lang === 'ar' ? 'ar' : 'en')}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Shell title={t('modCompliance')}><InlineEmpty>{t('riskLoading')}</InlineEmpty></Shell>;
  if (!item)   return <Shell title={t('modCompliance')}><div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>Not found</div></Shell>;

  const title = lang === 'ar' && item.titleAr ? item.titleAr : item.titleEn;
  const l = lang === 'ar' ? 'ar' : 'en';
  const tabs: TabItem<TabId>[] = [
    { id: 'overview',  label: t('detailTabOverview') },
    { id: 'mappings',  label: t('detailTabMappings') },
    { id: 'docs',      label: lang === 'ar' ? 'المستندات' : 'Documents' },
    { id: 'signoff',   label: lang === 'ar' ? 'التوقيعات' : 'Sign-off' },
    { id: 'links',     label: t('detailTabLinks') },
    { id: 'activity',  label: lang === 'ar' ? 'سجل النشاط' : 'Activity' },
  ];

  return (
    <Shell title={t('modCompliance')} sub={t('complianceSub')}>
      <DetailHeader
        breadcrumbs={[
          { label: t('modCompliance'), to: '/compliance' },
          { label: t('complianceTabPolicies'), to: '/policy-management' },
          { label: item.policyCode ?? `#${item.id}` },
        ]}
        eyebrow={item.policyCode}
        title={title}
        badges={<>
          <Badge tone={policyStatusTone(item.status)} dot>{policyStatusLabel(item.status, l)}</Badge>
          {item.version && <Badge tone="primary">v{item.version}</Badge>}
          {item.category && <Badge tone="info">{item.category.replace(/_/g, ' ')}</Badge>}
        </>}
        actions={<>
          {nextPolicyStatuses(item.status).map(next => (
            <Button key={next} variant={next === 'PUBLISHED' || next === 'APPROVED' || next === 'REVIEW' ? 'primary' : 'ghost'}
                    Icon={ArrowRight} disabled={busy} onClick={() => runTransition(next)}>
              {policyStatusLabel(next, l)}
            </Button>
          ))}
          {item.status === 'APPROVED' && (
            <PolicyPublishApprovalButton variant="secondary"
              sourceRecordId={item.id!}
              defaultTitleEn={`Publish approval — ${item.policyCode ?? title}`} />
          )}
        </>}
      />

      <DetailLayout
        rail={<>
          <Card title={t('detailAtAGlance')} Icon={BookCopy}>
            <RailFieldGroup>
              <RailField
                label={t('fldStatus')}
                accent
                value={<Badge tone={policyStatusTone(item.status)} dot>{policyStatusLabel(item.status, l)}</Badge>}
              />
              <RailField label={t('fldCode')}     value={item.policyCode ?? '—'} mono />
              <RailField label={t('fldVersion')}  value={`v${item.version ?? '—'}`} mono />
              <RailField label={t('fldCategory')} value={(item.category ?? '').replace(/_/g, ' ')} />
              <RailField label={t('complianceFldEffectiveFrom')} value={item.effectiveDate ?? '—'} mono />
              <RailField label={t('fldNextReviewDate')}          value={item.nextReviewDate ?? '—'} mono />
              {item.ownerName && <RailField label={lang === 'ar' ? 'المالك' : 'Owner'} value={item.ownerName} />}
              {item.approverName && <RailField label={lang === 'ar' ? 'المعتمِد' : 'Approver'} value={item.approverName} />}
            </RailFieldGroup>
          </Card>
          <EntityAnchorsPanel
            anchors={POLICY_ANCHORS_BY_ID[item.id!] ?? []}
            subtitle="Authorities this policy satisfies." />
          <LinkedChangesPanel entityType="POLICY" entityId={item.id!} />
          <ModuleEngineRequestsPanel sourceModule="COMPLIANCE" sourceRecordType="Policy" sourceRecordId={item.id!}
            titleEn="Approvals & requests" titleAr="الاعتمادات والطلبات" />
        </>}
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <Card title={t('detailTabOverview')} Icon={BookCopy}>
              <Field label={t('fldCode')}     value={item.policyCode ?? '—'} mono bold />
              <Field label={t('colTitle')}    value={title} bold />
              <Field label={t('fldVersion')}  value={item.version ?? '—'} mono />
              <Field label={t('fldCategory')} value={(item.category ?? '').replace(/_/g, ' ')} />
              <Field label={t('complianceFldEffectiveFrom')} value={item.effectiveDate ?? '—'} mono />
              <Field label={t('fldNextReviewDate')} value={item.nextReviewDate ?? '—'} mono />
              <Field label={t('complianceFldPolicyContent')}
                     value={(lang === 'ar' ? item.summaryAr : item.summaryEn) ?? item.summaryEn ?? '—'} multiline />
            </Card>
          )}

          {tab === 'mappings' && (
            <Card title={t('detailTabMappings')} Icon={Award}>
              {mappings.length === 0
                ? <InlineEmpty compact>{t('detailMappingsEmpty')}</InlineEmpty>
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

          {tab === 'docs' && (
            <Card title={lang === 'ar' ? 'مستندات السياسة' : 'Policy documents'} Icon={BookCopy}>
              <AttachmentsField entityType="POLICY" entityId={item.id!} />
            </Card>
          )}
          {tab === 'signoff' && (
            <Card title={lang === 'ar' ? 'اعتماد السياسة' : 'Policy sign-off'} Icon={CheckCheck}>
              <SignaturePanel entityType="POLICY" entityId={item.id!} />
            </Card>
          )}
          {tab === 'links' && (
            <Card title={t('detailTabLinks')} Icon={Link2}>
              <InlineEmpty compact>Linked records appear in the right rail. Use the rail's Linked Changes card to navigate to related change requests.</InlineEmpty>
            </Card>
          )}
          {tab === 'activity' && (
            <EntityActivityTimeline
              entityType="Policy"
              entityId={item.id!}
              titleEn="Policy activity timeline"
              titleAr="سجل نشاط السياسة" />
          )}
        </>}
      />
    </Shell>
  );
}
