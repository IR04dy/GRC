import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ClipboardCheck, FileCheck2, ListChecks, Link2, CheckCheck, AlertTriangle, ShieldOff, Calendar } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { DetailLayout } from '../../components/layout/DetailLayout';
import { RailField, RailFieldGroup } from '../../components/layout/DetailRail';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LinkedChangesPanel } from '../../components/ui/LinkedChangesPanel';
import { complianceApi, type ComplianceControlDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { EntityAnchorsPanel } from '../../components/authority/EntityAnchorsPanel';
import { CONTROL_ANCHORS_BY_ID } from '../../lib/entityAnchors';
import {
  COMPLIANCE_CONTROLS, CONTROL_SUBCONTROLS_BY_ID, CONTROL_EVIDENCE_BY_ID, CONTROL_TESTS_BY_ID,
} from '../compliance/mockCompliance';

type TabId = 'overview' | 'subcontrols' | 'evidence' | 'tests' | 'links';

const STATUS_TONE: Record<string, BadgeTone> = {
  IMPLEMENTED: 'success', PARTIAL: 'warning', PLANNED: 'info', NOT_APPLICABLE: 'neutral',
};

export function ControlDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [item, setItem] = useState<ComplianceControlDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<TabId>('overview');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    complianceApi.controls(0, 200)
      .then(p => setItem(p.content?.find(c => c.id === Number(id)) ?? null))
      .catch(() => setItem(COMPLIANCE_CONTROLS.find(c => c.id === Number(id)) ?? null))
      .finally(() => setLoading(false));
  }, [id]);

  function transitionControl(toStatus: string, msgKey: string) {
    if (!item) return;
    setItem({ ...item, status: toStatus });
    toast.success(t(msgKey));
  }

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
        actions={<>
          {item.status !== 'IMPLEMENTED' && <Button variant="primary" Icon={CheckCheck} onClick={() => transitionControl('IMPLEMENTED', 'controlActMarkImplemented')}>{t('controlActMarkImplemented')}</Button>}
          {item.status !== 'PARTIAL' && <Button variant="secondary" Icon={AlertTriangle} onClick={() => transitionControl('PARTIAL', 'controlActMarkPartial')}>{t('controlActMarkPartial')}</Button>}
          {item.status !== 'NOT_APPLICABLE' && <Button variant="ghost" Icon={ShieldOff} onClick={() => transitionControl('NOT_APPLICABLE', 'controlActMarkNA')}>{t('controlActMarkNA')}</Button>}
          <Button variant="ghost" Icon={Calendar}
            onClick={() => navigate(`/audit-workspace?tab=plan&controlId=${encodeURIComponent(String(item.id))}&action=schedule-test`)}>
            {t('controlActScheduleTest')}
          </Button>
        </>}
      />

      <DetailLayout
        rail={<>
          <Card title={t('detailAtAGlance')} Icon={ClipboardCheck}>
            <RailFieldGroup>
              <RailField
                label={t('fldStatus')}
                accent
                value={<Badge tone={STATUS_TONE[item.status] ?? 'neutral'} dot>{(item.status ?? '').replace(/_/g, ' ')}</Badge>}
              />
              <RailField label={t('fldCode')}      value={item.code} mono />
              <RailField label={t('colFramework')} value={(item.framework ?? '').replace(/_/g, ' ')} />
              <RailField label={t('fldDomain')}    value={item.domain ?? '—'} />
              <RailField label={t('colMaturity')}  value={`L${item.maturityLevel ?? 0}`} mono />
              <RailField label={t('colOwner')}     value={item.owner ?? '—'} />
            </RailFieldGroup>
          </Card>
          <EntityAnchorsPanel
            anchors={CONTROL_ANCHORS_BY_ID[item.id] ?? []}
            subtitle="Authorities this control satisfies." />
          <LinkedChangesPanel entityType="CONTROL" entityId={item.id} />
        </>}
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <Card title={t('detailTabOverview')} Icon={ClipboardCheck}>
              <Field label={t('fldCode')}     value={item.code} mono bold />
              <Field label={t('colControl')}  value={name} bold />
              <Field label={t('fldDomain')}   value={item.domain ?? '—'} />
              <Field label={t('colMaturity')} value={`L${item.maturityLevel ?? 0}`} mono />
              <Field label={t('colOwner')}    value={item.owner ?? '—'} />
              <Field label={t('colFramework')} value={(item.framework ?? '').replace(/_/g, ' ')} />
              <Field label={t('detailDescription')} value={item.description ?? '—'} multiline />
            </Card>
          )}

      {tab === 'subcontrols' && (
        <Card title={t('detailTabSubControls')} Icon={ListChecks}
          action={<Badge tone="primary">{(CONTROL_SUBCONTROLS_BY_ID[item.id] ?? []).length}</Badge>}>
          {(CONTROL_SUBCONTROLS_BY_ID[item.id] ?? []).length === 0
            ? <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailSubControlsHint')}</div>
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {(CONTROL_SUBCONTROLS_BY_ID[item.id] ?? []).map(s => (
                  <div key={s.id} style={{ display: 'grid', gridTemplateColumns: '120px 1fr 100px 80px', gap: 10, padding: '8px 0', borderBottom: '1px dashed var(--border)', alignItems: 'center' }}>
                    <span className="mono" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{s.code}</span>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600 }}>{s.nameEn}</div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{s.owner}</div>
                    </div>
                    <Badge tone={s.status === 'IMPLEMENTED' ? 'success' : s.status === 'PARTIAL' ? 'warning' : 'info'}>{s.status.replace(/_/g, ' ')}</Badge>
                    <span className="mono" style={{ fontSize: 12, fontWeight: 700 }}>L{s.maturityLevel}</span>
                  </div>
                ))}
              </div>}
        </Card>
      )}
      {tab === 'evidence' && (
        <Card title={t('detailTabEvidence')} Icon={FileCheck2}
          action={<Badge tone="primary">{(CONTROL_EVIDENCE_BY_ID[item.id] ?? []).length}</Badge>}>
          {(CONTROL_EVIDENCE_BY_ID[item.id] ?? []).length === 0
            ? <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailEvidenceHint')}</div>
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {(CONTROL_EVIDENCE_BY_ID[item.id] ?? []).map(e => (
                  <div key={e.id} style={{ display: 'grid', gridTemplateColumns: '110px 1fr 110px 90px 80px', gap: 10, padding: '8px 0', borderBottom: '1px dashed var(--border)', alignItems: 'center' }}>
                    <span className="mono" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{e.code}</span>
                    <span style={{ fontSize: 13, fontWeight: 600 }}>{e.titleEn}</span>
                    <Badge tone="neutral">{e.type.replace(/_/g, ' ')}</Badge>
                    <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{e.expiryDate}</span>
                    <Badge tone={e.status === 'APPROVED' ? 'success' : 'warning'}>{e.status.replace(/_/g, ' ')}</Badge>
                  </div>
                ))}
              </div>}
        </Card>
      )}
      {tab === 'tests' && (
        <Card title={t('detailTabTests')} Icon={ListChecks}
          action={<Badge tone="primary">{(CONTROL_TESTS_BY_ID[item.id] ?? []).length}</Badge>}>
          {(CONTROL_TESTS_BY_ID[item.id] ?? []).length === 0
            ? <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 13 }}>{t('detailTestsHint')}</div>
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {(CONTROL_TESTS_BY_ID[item.id] ?? []).map(tt => (
                  <div key={tt.id} style={{ display: 'grid', gridTemplateColumns: '110px 1fr 80px 80px', gap: 10, padding: '8px 0', borderBottom: '1px dashed var(--border)', alignItems: 'center' }}>
                    <span className="mono" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{tt.date}</span>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600 }}>{tt.tester}</div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{tt.notes}</div>
                    </div>
                    <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{tt.samples} samples · {tt.findings} findings</span>
                    <Badge tone={tt.result === 'PASS' ? 'success' : tt.result === 'FAIL' ? 'danger' : 'warning'}>{tt.result}</Badge>
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
