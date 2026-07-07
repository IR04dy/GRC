import { useEffect, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useParams } from 'react-router-dom';
import { AlertTriangle, ListChecks, ArrowRight, ShieldCheck, Stamp, Lock, Wrench, Briefcase, Link2, RotateCcw } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { DetailLayout } from '../../components/layout/DetailLayout';
import { RaiseRequestButton } from '../../components/workflow/RaiseRequestButton';
import { RailField, RailFieldGroup } from '../../components/layout/DetailRail';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { SignaturePanel } from '../../components/ui/SignaturePanel';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LinkedChangesPanel } from '../../components/ui/LinkedChangesPanel';
import { complianceApi, ApiError, type ComplianceFindingDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { SEVERITY_TONE, severityLabel } from '../../lib/labels';
import { EntityAnchorsPanel } from '../../components/authority/EntityAnchorsPanel';
import { FINDING_ANCHORS_BY_ID } from '../../lib/entityAnchors';
import { COMPLIANCE_FINDINGS, FINDING_EVIDENCE_BY_ID, FINDING_HISTORY_BY_ID } from '../compliance/mockCompliance';
import { CommsPanel } from '../../components/comms/CommsPanel';
import { EntityActivityTimeline } from '../../components/audit/EntityActivityTimeline';
import { EntityAutomationRuns } from '../../components/audit/EntityAutomationRuns';

type TabId = 'overview' | 'capa' | 'evidence' | 'docs' | 'signoff' | 'links' | 'history' | 'comms';

const CAPA_STAGE_TONE: Record<string, BadgeTone> = {
  IDENTIFIED: 'neutral', IMPLEMENTING: 'info', OWNER_TESTED: 'primary',
  INDEPENDENT_TESTED: 'accent', AUDIT_SIGNED_OFF: 'warning', CLOSED: 'success',
};

export function FindingDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t, lang } = useI18n();
  const toast = useToast();
  const [item, setItem] = useState<ComplianceFindingDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<TabId>('overview');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    complianceApi.findings(0, 200)
      .then(p => setItem(p.content?.find(f => f.id === Number(id)) ?? COMPLIANCE_FINDINGS.find(f => f.id === Number(id)) ?? null))
      .catch(() => setItem(COMPLIANCE_FINDINGS.find(f => f.id === Number(id)) ?? null))
      .finally(() => setLoading(false));
  }, [id]);

  async function capaAction(fn: (n: number) => Promise<unknown>, msg: string) {
    if (!item) return;
    try { await fn(item.id); toast.success(msg);
      const p = await complianceApi.findings(0, 200);
      setItem(p.content?.find(f => f.id === item.id) ?? null);
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  if (loading) return <Shell title={t('modCompliance')}><InlineEmpty>{t('riskLoading')}</InlineEmpty></Shell>;
  if (!item)   return <Shell title={t('modCompliance')}><div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>Not found</div></Shell>;

  const tabs: TabItem<TabId>[] = [
    { id: 'overview', label: t('detailTabOverview') },
    { id: 'capa',     label: t('detailTabCapa') },
    { id: 'evidence', label: t('detailTabEvidence') },
    { id: 'docs',     label: lang === 'ar' ? 'المستندات والضوابط' : 'Docs & controls' },
    { id: 'signoff',  label: lang === 'ar' ? 'الاعتماد' : 'Sign-off' },
    { id: 'links',    label: t('detailTabLinks') },
    { id: 'history',  label: t('detailTabHistory') },
    { id: 'comms',    label: t('detailTabComms') },
  ];

  const stage = item.capaStage ?? 'IDENTIFIED';

  return (
    <Shell title={t('modCompliance')} sub={t('complianceSub')}>
      <DetailHeader
        breadcrumbs={[
          { label: t('modCompliance'), to: '/compliance' },
          { label: t('complianceTabFindings'), to: '/compliance?tab=findings' },
          { label: `#${item.id}` },
        ]}
        eyebrow={`Finding #${item.id}`}
        title={item.title}
        sub={item.capa}
        badges={<>
          <Badge tone={SEVERITY_TONE[item.severity] ?? 'neutral'} dot>{severityLabel(t, item.severity)}</Badge>
          <Badge tone={item.status === 'CLOSED' ? 'success' : 'warning'} dot>{item.status}</Badge>
          <Badge tone={CAPA_STAGE_TONE[stage] ?? 'neutral'}>{stage.replace(/_/g, ' ')}</Badge>
        </>}
        actions={<>
          <RaiseRequestButton sourceModule="COMPLIANCE" sourceRecordType="Finding" sourceRecordId={Number(id) || undefined} />
          {stage === 'IDENTIFIED'         && <Button variant="primary" Icon={Wrench}      onClick={() => capaAction((id) => complianceApi.capaStartImplementing(id), t('capaActStart'))}>{t('capaActStart')}</Button>}
          {stage === 'IMPLEMENTING'       && <Button variant="primary" Icon={ShieldCheck} onClick={async () => {
            const r = window.prompt('PASS / FAIL / PARTIAL', 'PASS'); if (!r) return;
            await capaAction((id) => complianceApi.capaOwnerTest(id, { result: r.trim().toUpperCase() as 'PASS' | 'FAIL' | 'PARTIAL' }), t('capaActOwnerTest'));
          }}>{t('capaActOwnerTest')}</Button>}
          {stage === 'OWNER_TESTED'       && <Button variant="primary" Icon={Briefcase}   onClick={async () => {
            const r = window.prompt('PASS / FAIL / PARTIAL', 'PASS'); if (!r) return;
            await capaAction((id) => complianceApi.capaIndependentTest(id, { result: r.trim().toUpperCase() as 'PASS' | 'FAIL' | 'PARTIAL' }), t('capaActIndepTest'));
          }}>{t('capaActIndepTest')}</Button>}
          {stage === 'INDEPENDENT_TESTED' && <Button variant="primary" Icon={Stamp}       onClick={() => capaAction((id) => complianceApi.capaAuditSignoff(id, {}), t('capaActSignoff'))}>{t('capaActSignoff')}</Button>}
          {stage === 'AUDIT_SIGNED_OFF'   && <Button variant="primary" Icon={Lock}        onClick={() => capaAction((id) => complianceApi.capaClose(id), t('capaActClose'))}>{t('capaActClose')}</Button>}
          {stage === 'CLOSED'             && <Button variant="secondary" Icon={RotateCcw} onClick={async () => {
            const reason = window.prompt(lang === 'ar' ? 'سبب إعادة فتح الملاحظة' : 'Reason for reopening this finding');
            if (!reason || !reason.trim()) return;
            await capaAction((id) => complianceApi.capaReopen(id, { reason: reason.trim() }), t('capaActReopen'));
          }}>{t('capaActReopen')}</Button>}
        </>}
      />

      <DetailLayout
        rail={<>
          <Card title={t('detailAtAGlance')} Icon={AlertTriangle}>
            <RailFieldGroup>
              <RailField
                label={t('fldStatus')}
                accent
                value={<Badge tone={item.status === 'CLOSED' ? 'success' : 'warning'} dot>{item.status}</Badge>}
              />
              <RailField
                label={t('colSeverity')}
                value={<Badge tone={SEVERITY_TONE[item.severity] ?? 'neutral'} dot>{severityLabel(t, item.severity)}</Badge>}
              />
              <RailField
                label={t('detailCapaStage')}
                value={<Badge tone={CAPA_STAGE_TONE[stage] ?? 'neutral'}>{stage.replace(/_/g, ' ')}</Badge>}
              />
              <RailField label={t('colOwner')} value={item.owner ?? '—'} />
              <RailField label={t('colDue')}   value={item.dueDate ?? '—'} mono />
            </RailFieldGroup>
          </Card>
          <Card title={t('detailCapaStage')} Icon={ListChecks}>
            <CapaStepper stage={stage} t={t} />
          </Card>
          <EntityAnchorsPanel
            anchors={FINDING_ANCHORS_BY_ID[item.id] ?? []}
            subtitle="Authorities this finding addresses." />
          <LinkedChangesPanel entityType="AUDIT_FINDING" entityId={item.id} />
        </>}
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <Card title={t('detailTabOverview')} Icon={AlertTriangle}>
              <Field label={t('colTitle')}    value={item.title} bold />
              <Field label={t('colCapa')}     value={item.capa ?? '—'} multiline />
              <Field label={t('colDue')}      value={item.dueDate ?? '—'} mono />
              <Field label={t('colOwner')}    value={item.owner ?? '—'} />
            </Card>
          )}

      {tab === 'capa' && (
        <Card title={t('detailTabCapa')} Icon={ListChecks}>
          <CapaStepper stage={stage} t={t} />
          <div style={{ marginTop: 16, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12 }}>
            <Field label={t('detailOwnerTest')}        value={item.ownerTestResult ? `${item.ownerTestResult} · ${item.ownerTestAt?.slice(0, 10) ?? ''}` : '—'} />
            <Field label={t('detailIndependentTest')}  value={item.independentTestResult ? `${item.independentTestResult} · ${item.independentTestAt?.slice(0, 10) ?? ''}` : '—'} />
            <Field label={t('detailAuditSignoff')}     value={item.auditSignoffAt ? item.auditSignoffAt.slice(0, 10) : '—'} />
          </div>
        </Card>
      )}

      {tab === 'evidence' && (
        <Card title={t('detailTabEvidence')} Icon={ShieldCheck}
          action={<Badge tone="primary">{(FINDING_EVIDENCE_BY_ID[item.id] ?? []).length}</Badge>}>
          {(FINDING_EVIDENCE_BY_ID[item.id] ?? []).length === 0
            ? <InlineEmpty compact>{t('detailEvidenceHint')}</InlineEmpty>
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {(FINDING_EVIDENCE_BY_ID[item.id] ?? []).map(e => (
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
      {tab === 'docs' && (
        <Card title={lang === 'ar' ? 'المستندات وربط الضوابط' : 'Documents & control mapping'} Icon={ShieldCheck}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <AttachmentsField entityType="AUDIT_FINDING" entityId={item.id} />
            <div style={{ borderTop: '1px solid var(--border)' }} />
            <ControlMappingPicker entityType="AUDIT_FINDING" entityId={item.id} />
          </div>
        </Card>
      )}
      {tab === 'signoff' && (
        <Card title={lang === 'ar' ? 'اعتماد الملاحظة' : 'Finding sign-off'} Icon={Stamp}>
          <SignaturePanel entityType="AUDIT_FINDING" entityId={item.id} />
        </Card>
      )}
      {tab === 'links' && (
        <Card title={t('detailTabLinks')} Icon={Link2}>
          <InlineEmpty compact>Linked records appear in the right rail. Use the rail's Linked Changes card to navigate to related change requests.</InlineEmpty>
        </Card>
      )}
      {tab === 'history' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Real forensic history from platform.audit_log — populated by
              CapaWorkflowService (PR #44) on every CAPA-stage transition.
              Self-hides on empty so a fresh finding without audit rows
              doesn't show a phantom card; the legacy mock below takes
              the visible primacy in that case. As real audit data
              accumulates, this card becomes the source of truth. */}
          <EntityActivityTimeline entityType="AuditFinding" entityId={item.id} />

          {/* Sibling card — F8 automation rule fires keyed to the same
              entity. Same self-hiding behaviour. */}
          <EntityAutomationRuns entityType="AuditFinding" entityId={item.id} />

          {/* Legacy static history kept for seed-data findings that
              predate the audit-log integration. Will be retired when
              the seed migrates over. */}
          <Card title={t('detailTabHistory')} Icon={ListChecks}
            action={<Badge tone="primary">{(FINDING_HISTORY_BY_ID[item.id] ?? []).length}</Badge>}>
            {(FINDING_HISTORY_BY_ID[item.id] ?? []).length === 0
              ? <InlineEmpty compact>{t('detailHistoryHint')}</InlineEmpty>
              : <div style={{ display: 'flex', flexDirection: 'column' }}>
                  {(FINDING_HISTORY_BY_ID[item.id] ?? []).map(h => (
                    <div key={h.id} style={{ display: 'grid', gridTemplateColumns: '160px 130px 1fr', gap: 12, padding: '8px 0', borderBottom: '1px dashed var(--border)', alignItems: 'flex-start' }}>
                      <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{h.at.replace('T', ' ').slice(0, 16)}</span>
                      <Badge tone={h.type === 'CREATED' ? 'primary' : h.type === 'STATUS_CHANGE' ? 'info' : h.type === 'CAPA_PROGRESS' ? 'success' : h.type === 'TEST_RESULT' ? 'accent' : h.type === 'SIGNOFF' ? 'success' : 'neutral'}>{h.type.replace(/_/g, ' ')}</Badge>
                      <div>
                        <div style={{ fontSize: 13, color: 'var(--ink-1)' }}>{h.message}</div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>by {h.actor}</div>
                      </div>
                    </div>
                  ))}
                </div>}
          </Card>
        </div>
      )}

      {tab === 'comms' && (
        <CommsPanel
          module="AUDIT"
          eventKind={item.severity === 'CRITICAL' || item.severity === 'HIGH' ? 'AUDIT_CRITICAL_FINDING' : undefined}
          severity={item.severity as 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'}
          eventDetectedAt={item.dueDate ? `${item.dueDate}T00:00:00` : undefined}
          recordKey={`AUDIT_FINDING:${item.id}`} />
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

const CAPA_STEPS = ['IDENTIFIED', 'IMPLEMENTING', 'OWNER_TESTED', 'INDEPENDENT_TESTED', 'AUDIT_SIGNED_OFF', 'CLOSED'];

function CapaStepper({ stage, t }: { stage: string; t: (k: string) => string }) {
  const idx = CAPA_STEPS.indexOf(stage);
  return <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
    {CAPA_STEPS.map((s, i) => {
      const done = i < idx, current = i === idx;
      return <div key={s} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 8px', background: current ? 'var(--primary-50)' : 'transparent', borderRadius: 'var(--r-sm)', fontSize: 12 }}>
        <span style={{ width: 18, height: 18, borderRadius: '50%', background: done ? 'var(--success)' : current ? 'var(--primary)' : 'var(--surface-3)', color: '#fff', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 700 }}>{done ? '✓' : i + 1}</span>
        <span style={{ fontWeight: current ? 600 : 500, color: current ? 'var(--primary)' : done ? 'var(--ink-2)' : 'var(--ink-3)' }}>{s.replace(/_/g, ' ')}</span>
        {current && <ArrowRight size={11} style={{ marginInlineStart: 'auto', color: 'var(--primary)' }} />}
      </div>;
    })}
    {/* Suppress unused warning for Link2 — kept for future use */}
    <span style={{ display: 'none' }}><Link2 size={1} /></span>
    <span style={{ display: 'none' }}>{t('detailCapaStage')}</span>
  </div>;
}
