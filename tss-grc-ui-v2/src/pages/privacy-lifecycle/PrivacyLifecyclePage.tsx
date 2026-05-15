import { useEffect, useMemo, useState } from 'react';
import {
  Files, ShieldAlert, Globe, UserCheck, Clock,
  AlertTriangle, CheckCircle2, FileText, Activity,
} from 'lucide-react';
import { LifecyclePipeline, type PipelineStage, type PipelineCard, dueLabel } from '../../components/lifecycle/LifecyclePipeline';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  loadRopa, saveRopa, loadDpia, loadCbt, loadDsr, saveDsr, transitionDsr, subscribePrivacy,
  detectHighRisk, dsrSlaRemaining, aggregatePrivacy,
  ADEQUACY_BY_COUNTRY, ADEQUACY_TONE, ADEQUACY_LABEL, SAFEGUARD_LABEL,
  ROPA_STATUS_LABEL, ROPA_STATUS_TONE, LAWFUL_BASIS_LABEL,
  DSR_TYPE_LABEL, DSR_STATUS_LABEL, DSR_STATUS_TONE,
  transitionDpia, createDpia, transitionCbt, upgradeCbtSafeguard, createDsr,
  type RopaActivity, type DsrCase, type DsrStatus,
  type DpiaAssessment, type DpiaStatus, type CrossBorderTransfer, type CbtStatus,
  type SafeguardMechanism, type DsrType,
} from '../../lib/privacyLifecycle';
import { useRealtimeOrgEvent } from '../../lib/useRealtimeEntity';

type PrcTab = 'pipeline' | 'ropa' | 'dpia' | 'cbt' | 'dsr';

export function PrivacyLifecyclePage() {
  const { t, lang } = useI18n();
  const toast = useToast();

  const [tab, setTab] = useState<PrcTab>('pipeline');
  const [_tick, setTick] = useState(0);
  useEffect(() => subscribePrivacy(() => setTick(t => t + 1)), []);

  // Sprint 4 §2.7 — backend `privacy_lc.DsrCaseService` broadcasts on every
  // DSR transition (PDPL-30-day SLA pulse, fulfillment, rejection). Tick the
  // same counter so memoised data re-derives for either local or remote events.
  useRealtimeOrgEvent('changed.dsr_case', () => setTick(t => t + 1));

  const stats = useMemo(() => aggregatePrivacy(), [_tick]);
  const ropa = useMemo(() => loadRopa(), [_tick]);
  const dpias = useMemo(() => loadDpia(), [_tick]);
  const cbts = useMemo(() => loadCbt(), [_tick]);
  const dsrs = useMemo(() => loadDsr(), [_tick]);

  return (
    <Shell title={t('prcTitle')} sub={t('prcSub')}>
      <PageHeader
        eyebrow={t('prcEyebrow')}
        title={t('prcTitle')}
        sub={t('prcPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Files}          label={t('prcKpiRopa')}          value={`${stats.ropaApproved} / ${stats.ropaTotal}`} tone="primary" />
        <Kpi Icon={ShieldAlert}    label={t('prcKpiDpiaHighRisk')}  value={String(stats.dpiaHighRisk)} tone={stats.dpiaHighRisk > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Globe}          label={t('prcKpiCbt')}            value={`${stats.cbtAdequate} / ${stats.cbtTotal}`} tone="info" />
        <Kpi Icon={Globe}          label={t('prcKpiCbtBlocked')}     value={String(stats.cbtBlocked)} tone={stats.cbtBlocked > 0 ? 'danger' : 'success'} />
        <Kpi Icon={UserCheck}       label={t('prcKpiDsrOpen')}        value={String(stats.dsrOpen)} tone={stats.dsrOpen > 0 ? 'info' : 'success'} />
        <Kpi Icon={AlertTriangle}  label={t('prcKpiDsrOverdue')}     value={String(stats.dsrOverdue)} tone={stats.dsrOverdue > 0 ? 'danger' : 'success'} />
        <Kpi Icon={Clock}          label={t('prcKpiAvgDays')}        value={`${stats.dsrCloseAvgDays}d`} tone="info" />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'pipeline', labelKey: 'lpcTabPipeline', Icon: Activity },
          { id: 'ropa', labelKey: 'prcTabRopa', Icon: Files },
          { id: 'dpia', labelKey: 'prcTabDpia', Icon: ShieldAlert },
          { id: 'cbt',  labelKey: 'prcTabCbt',  Icon: Globe },
          { id: 'dsr',  labelKey: 'prcTabDsr',  Icon: UserCheck },
        ] as { id: PrcTab; labelKey: string; Icon: typeof Files }[]).map(tabDef => (
          <button key={tabDef.id} onClick={() => setTab(tabDef.id)}
            style={{
              padding: '8px 12px', borderRadius: 'var(--r-sm)', fontSize: 12, fontWeight: 600,
              border: `1px solid ${tab === tabDef.id ? 'var(--primary)' : 'var(--border)'}`,
              background: tab === tabDef.id ? 'var(--primary-50)' : 'var(--surface-1)',
              cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6,
            }}>
            <tabDef.Icon size={13} />
            {t(tabDef.labelKey)}
          </button>
        ))}
      </div>

      {tab === 'pipeline' && <DsrPipeline dsrs={dsrs}
        onAdvance={(id, fromStatus) => {
          const next: Record<string, DsrStatus> = {
            INTAKE: 'VERIFYING_IDENTITY',
            VERIFYING_IDENTITY: 'COLLECTING',
            COLLECTING: 'REDACTING',
            REDACTING: 'DELIVERED',
          };
          const target = next[fromStatus];
          if (!target) return;
          transitionDsr(id, target);
          toast.success(t('lpcAdvancedToast'));
        }}
        onCardClick={() => setTab('dsr')} />}

      {tab === 'ropa' && <RopaTab ropa={ropa}
        onCreate={() => {
          const id = `ROPA-${new Date().getFullYear()}-${String(ropa.length + 1).padStart(3, '0')}`;
          saveRopa({
            id,
            nameEn: 'New processing activity', nameAr: 'نشاط معالجة جديد',
            lawfulBasis: 'CONSENT',
            purposesEn: '', purposesAr: '',
            dataCategories: [], subjectCategories: [],
            recipientsEn: '', recipientsAr: '',
            storageLocation: 'Riyadh primary',
            retentionPeriodEn: '', retentionPeriodAr: '',
            securityMeasures: [],
            owner: '',
            status: 'DRAFT',
            createdAt: new Date().toISOString(),
          });
          toast.info(t('prcRopaCreatedToast'));
        }}
        onApprove={(r) => {
          saveRopa({ ...r, status: 'APPROVED', approvedBy: 'DPO + CRO', approvedAt: new Date().toISOString() });
          toast.success(t('prcRopaApprovedToast'));
        }} />}
      {tab === 'dpia' && <DpiaTab dpias={dpias} ropa={ropa}
        onCreate={(ropaId) => { createDpia(ropaId); toast.info(t('prcDpiaCreatedToast')); }}
        onTransition={(d, status) => {
          const by = status === 'APPROVED' ? 'Layla Al-Ghamdi (DPO)' : undefined;
          transitionDpia(d.id, status, by);
          toast.info(`DPIA ${status}`);
        }} />}
      {tab === 'cbt'  && <CbtTab  cbts={cbts}  ropa={ropa}
        onTransition={(c, status) => {
          const by = status === 'APPROVED' ? 'Layla Al-Ghamdi (DPO)' : undefined;
          transitionCbt(c.id, status, by);
          toast.info(`CBT ${status}`);
        }}
        onUpgradeSafeguard={(c, sg) => {
          upgradeCbtSafeguard(c.id, sg);
          toast.success(t('prcCbtUpgradedToast'));
        }} />}
      {tab === 'dsr'  && <DsrTab  dsrs={dsrs}
        onCreate={(type, name, idLast4, email) => { createDsr(type, name, idLast4, email); toast.success(t('prcDsrCreatedToast')); }}
        onTransition={(d, status) => { transitionDsr(d.id, status); toast.info(`${t('prcDsrStatusUpdated')}: ${DSR_STATUS_LABEL[status][lang]}`); }}
        onExtend={(d) => {
          saveDsr({
            ...d, status: 'EXTENDED', extended: true,
            extensionReasonEn: 'Complex multi-system request — Article 4 60-day extension invoked.',
            extensionReasonAr: 'طلب معقّد متعدّد الأنظمة — تمديد المادة 4 إلى 60 يوماً.',
            slaDeadline: new Date(new Date(d.slaDeadline).getTime() + 30 * 86_400_000).toISOString().slice(0, 10),
          });
          toast.warning(t('prcDsrExtendedToast'));
        }} />}
    </Shell>
  );
}

// ─── ROPA tab ───────────────────────────────────────────────────────────────

function RopaTab({ ropa, onCreate, onApprove }: {
  ropa: RopaActivity[];
  onCreate: () => void;
  onApprove: (r: RopaActivity) => void;
}) {
  const { t, lang } = useI18n();
  return (
    <Card title={t('prcRopaTitle')} Icon={Files}
      action={<Button size="sm" variant="secondary" onClick={onCreate}>{t('prcRopaCreate')}</Button>}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('prcRopaSub')}
      </div>
      {ropa.map(r => {
        const hr = detectHighRisk(r);
        return (
          <div key={r.id} style={{ padding: 14, marginBottom: 10, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12, marginBottom: 8, flexWrap: 'wrap' }}>
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 4 }}>
                  <span className="mono" style={{ fontSize: 11, color: 'var(--primary)' }}>{r.id}</span>
                  <Badge tone={ROPA_STATUS_TONE[r.status]}>{ROPA_STATUS_LABEL[r.status][lang]}</Badge>
                  <Badge tone="info">{LAWFUL_BASIS_LABEL[r.lawfulBasis][lang]}</Badge>
                  {hr.isHigh && <Badge tone="warning"><AlertTriangle size={11} /> {t('prcHighRisk')}</Badge>}
                  {r.dpiaId && <Badge tone="primary">{r.dpiaId}</Badge>}
                  {r.cbtId && <Badge tone="primary">{r.cbtId}</Badge>}
                </div>
                <div style={{ fontSize: 14, fontWeight: 700 }}>{lang === 'ar' ? r.nameAr : r.nameEn}</div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                  {t('prcRopaOwner')}: <b>{r.owner || '—'}</b>
                </div>
              </div>
              {r.status === 'IN_REVIEW' && <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onApprove(r)}>{t('prcApprove')}</Button>}
            </div>

            {/* Article 22 9-field grid */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, fontSize: 11 }}>
              <FieldRow label={t('prcRopaPurposes')} value={lang === 'ar' ? r.purposesAr : r.purposesEn} />
              <FieldRow label={t('prcRopaRecipients')} value={lang === 'ar' ? r.recipientsAr : r.recipientsEn} />
              <FieldRow label={t('prcRopaDataCats')} value={r.dataCategories.join(', ') || '—'} />
              <FieldRow label={t('prcRopaSubjectCats')} value={r.subjectCategories.join(', ') || '—'} />
              <FieldRow label={t('prcRopaStorage')} value={r.storageLocation} />
              <FieldRow label={t('prcRopaRetention')} value={lang === 'ar' ? r.retentionPeriodAr : r.retentionPeriodEn} />
              <FieldRow label={t('prcRopaSecurity')} value={r.securityMeasures.join(' · ') || '—'} colspan={2} />
            </div>

            {hr.isHigh && (
              <div style={{ marginTop: 8, padding: 8, background: 'rgba(245,158,11,0.06)', borderRadius: 4, borderInlineStart: '3px solid var(--warning)' }}>
                <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', marginBottom: 4 }}>
                  {t('prcArticle25')}
                </div>
                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                  {hr.triggers.map((t_, i) => (
                    <Badge key={i} tone="warning">{lang === 'ar' ? t_.ar : t_.en}</Badge>
                  ))}
                </div>
              </div>
            )}
          </div>
        );
      })}
    </Card>
  );
}

function FieldRow({ label, value, colspan }: { label: string; value: string; colspan?: number }) {
  return (
    <div style={{ gridColumn: colspan ? `span ${colspan}` : undefined }}>
      <div style={{ fontSize: 9, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 2 }}>
        {label}
      </div>
      <div style={{ fontSize: 11, color: 'var(--ink-2)', lineHeight: 1.5 }}>{value}</div>
    </div>
  );
}

// ─── DPIA tab ───────────────────────────────────────────────────────────────

function DpiaTab({ dpias, ropa, onCreate, onTransition }: {
  dpias: DpiaAssessment[]; ropa: RopaActivity[];
  onCreate: (ropaId: string) => void;
  onTransition: (d: DpiaAssessment, status: DpiaStatus) => void;
}) {
  const { t, lang } = useI18n();
  // Find ROPA activities flagged high-risk that have no DPIA yet.
  const candidates = ropa.filter(r => detectHighRisk(r).isHigh && !dpias.some(d => d.ropaId === r.id));
  return (
    <Card title={t('prcDpiaTitle')} Icon={ShieldAlert}
      action={candidates.length > 0
        ? <Button size="sm" variant="primary" onClick={() => onCreate(candidates[0].id)}>{t('prcDpiaCreate')} ({candidates.length})</Button>
        : null}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('prcDpiaSub')}
      </div>
      {dpias.length === 0
        ? <div style={{ padding: 20, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
            {t('prcDpiaEmpty')}
          </div>
        : dpias.map(d => {
            const r = ropa.find(x => x.id === d.ropaId);
            return (
              <div key={d.id} style={{ padding: 14, marginBottom: 10, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12, marginBottom: 8, flexWrap: 'wrap' }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 4 }}>
                      <span className="mono" style={{ fontSize: 11, color: 'var(--primary)' }}>{d.id}</span>
                      <Badge tone={d.status === 'APPROVED' ? 'success' : d.status === 'CONSULTATION' ? 'warning' : 'info'}>
                        {d.status}
                      </Badge>
                      {d.isHighRisk && <Badge tone="warning"><AlertTriangle size={11} /> {t('prcHighRisk')}</Badge>}
                      {d.needsSdaiaConsultation && <Badge tone="danger">SDAIA consultation</Badge>}
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>
                      {r ? (lang === 'ar' ? r.nameAr : r.nameEn) : d.ropaId}
                    </div>
                    {d.dpoSignedBy && (
                      <div style={{ fontSize: 11, color: 'var(--success-fg)', marginTop: 2 }}>
                        {t('prcDpoSign')}: {d.dpoSignedBy}
                      </div>
                    )}
                  </div>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <Stat label={t('prcDpiaInherent')} value={String(d.riskScore)} tone={d.riskScore > 12 ? 'danger' : 'warning'} />
                    <Stat label={t('prcDpiaResidual')} value={String(d.residualScore)} tone={d.residualScore > 6 ? 'warning' : 'success'} />
                  </div>
                </div>
                {/* Transition buttons */}
                <div style={{ display: 'flex', gap: 6, marginBottom: 8, flexWrap: 'wrap' }}>
                  {d.status === 'DRAFT' && <Button size="sm" variant="primary" onClick={() => onTransition(d, 'RISK_ASSESSMENT')}>{t('prcDpiaToRiskAssess')}</Button>}
                  {d.status === 'RISK_ASSESSMENT' && d.needsSdaiaConsultation && <Button size="sm" variant="primary" onClick={() => onTransition(d, 'CONSULTATION')}>{t('prcDpiaToSdaiaConsult')}</Button>}
                  {(d.status === 'RISK_ASSESSMENT' || d.status === 'CONSULTATION') && <Button size="sm" variant="primary" onClick={() => onTransition(d, 'APPROVED')}>{t('prcDpiaDpoApprove')}</Button>}
                  {d.status === 'APPROVED' && <Button size="sm" variant="ghost" onClick={() => onTransition(d, 'REJECTED')}>{t('prcDpiaReject')}</Button>}
                </div>
                <div style={{ marginBottom: 8 }}>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', marginBottom: 4 }}>
                    {t('prcDpiaTriggers')}
                  </div>
                  <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                    {(lang === 'ar' ? d.triggersAr : d.triggersEn).map((trg, i) => (
                      <Badge key={i} tone="warning">{trg}</Badge>
                    ))}
                  </div>
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-2)', lineHeight: 1.6, padding: 8, background: 'var(--surface-2)', borderRadius: 4 }}>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', marginBottom: 4 }}>
                    {t('prcDpiaMitigations')}
                  </div>
                  {lang === 'ar' ? d.mitigationsAr : d.mitigationsEn}
                </div>
              </div>
            );
          })
      }
    </Card>
  );
}

// ─── Cross-border tab ───────────────────────────────────────────────────────

function CbtTab({ cbts, ropa, onTransition, onUpgradeSafeguard }: {
  cbts: CrossBorderTransfer[]; ropa: RopaActivity[];
  onTransition: (c: CrossBorderTransfer, status: CbtStatus) => void;
  onUpgradeSafeguard: (c: CrossBorderTransfer, sg: SafeguardMechanism) => void;
}) {
  const { t, lang } = useI18n();
  return (
    <>
      <Card title={t('prcCbtTitle')} Icon={Globe}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {t('prcCbtSub')}
        </div>
        {cbts.map(c => {
          const r = ropa.find(x => x.id === c.ropaId);
          return (
            <div key={c.id} style={{ padding: 14, marginBottom: 10, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12, marginBottom: 8, flexWrap: 'wrap' }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 4 }}>
                    <span className="mono" style={{ fontSize: 11, color: 'var(--primary)' }}>{c.id}</span>
                    <Badge tone={ADEQUACY_TONE[c.adequacy]}>{ADEQUACY_LABEL[c.adequacy][lang]}</Badge>
                    <Badge tone="info">{SAFEGUARD_LABEL[c.safeguard][lang]}</Badge>
                    <Badge tone="primary">{c.destinationCountry}</Badge>
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>
                    {r ? (lang === 'ar' ? r.nameAr : r.nameEn) : c.ropaId}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 4, lineHeight: 1.6 }}>
                    {lang === 'ar' ? c.purposeAr : c.purposeEn}
                  </div>
                  {c.nextReviewAt && (
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                      {t('prcCbtNextReview')}: <span className="mono">{c.nextReviewAt}</span>
                      {' · '} {t('prcCbtInterval')}: {c.reviewIntervalMonths}m
                    </div>
                  )}
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <Stat label={t('prcCbtPreRisk')} value={String(c.preRiskScore)} tone="warning" />
                  <Stat label={t('prcCbtPostRisk')} value={String(c.postRiskScore)} tone={c.postRiskScore <= 6 ? 'success' : 'warning'} />
                </div>
              </div>
              {/* Action row */}
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center', paddingTop: 8, borderTop: '1px dashed var(--border)' }}>
                {c.status === 'DRAFT' && <Button size="sm" variant="primary" onClick={() => onTransition(c, 'ASSESSING')}>{t('prcCbtStartAssess')}</Button>}
                {c.status === 'ASSESSING' && c.adequacy !== 'PROHIBITED' && <Button size="sm" variant="primary" onClick={() => onTransition(c, 'APPROVED')}>{t('prcCbtApprove')}</Button>}
                {c.status === 'ASSESSING' && c.adequacy === 'PROHIBITED' && <Button size="sm" variant="danger" onClick={() => onTransition(c, 'BLOCKED')}>{t('prcCbtBlock')}</Button>}
                {c.status === 'APPROVED' && <Button size="sm" variant="secondary" onClick={() => onTransition(c, 'PERIODIC_REVIEW')}>{t('prcCbtTrigger Review')}</Button>}
                {c.adequacy === 'NOT_ADEQUATE' && c.safeguard !== 'BINDING_CORPORATE_RULES' && (
                  <select
                    onChange={e => { if (e.target.value) onUpgradeSafeguard(c, e.target.value as SafeguardMechanism); }}
                    defaultValue=""
                    style={{ padding: '4px 8px', fontSize: 11, border: '1px solid var(--border)', borderRadius: 4 }}>
                    <option value="">{t('prcCbtUpgradeSelect')}</option>
                    {(['STANDARD_CLAUSES','BINDING_CORPORATE_RULES','CERTIFICATION','EXPLICIT_CONSENT'] as SafeguardMechanism[])
                      .filter(s => s !== c.safeguard)
                      .map(s => <option key={s} value={s}>{SAFEGUARD_LABEL[s][lang]}</option>)}
                  </select>
                )}
                <Badge tone={c.status === 'APPROVED' ? 'success' : c.status === 'BLOCKED' ? 'danger' : c.status === 'PERIODIC_REVIEW' ? 'warning' : 'info'}>
                  {c.status}
                </Badge>
              </div>
            </div>
          );
        })}
      </Card>

      <div style={{ marginTop: 16 }}>
        <Card title={t('prcAdequacyTitle')} Icon={Globe}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('prcAdequacySub')}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
            {(['ADEQUATE', 'CONDITIONAL', 'NOT_ADEQUATE', 'PROHIBITED'] as const).map(level => (
              <div key={level} style={{ padding: 10, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)' }}>
                <div style={{ marginBottom: 6 }}>
                  <Badge tone={ADEQUACY_TONE[level]}>{ADEQUACY_LABEL[level][lang]}</Badge>
                </div>
                <ul style={{ margin: 0, paddingInlineStart: 14, fontSize: 11, color: 'var(--ink-2)', lineHeight: 1.7 }}>
                  {Object.entries(ADEQUACY_BY_COUNTRY)
                    .filter(([, l]) => l === level)
                    .map(([country]) => <li key={country}>{country}</li>)}
                </ul>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </>
  );
}

// ─── DSR tab ────────────────────────────────────────────────────────────────

function DsrTab({ dsrs, onCreate, onTransition, onExtend }: {
  dsrs: DsrCase[];
  onCreate: (type: DsrType, name: string, idLast4: string, email: string) => void;
  onTransition: (d: DsrCase, status: DsrStatus) => void;
  onExtend: (d: DsrCase) => void;
}) {
  const { t, lang } = useI18n();
  const [showForm, setShowForm] = useState(false);
  const [newType, setNewType] = useState<DsrType>('ACCESS');
  const [newName, setNewName] = useState('');
  const [newId, setNewId] = useState('');
  const [newEmail, setNewEmail] = useState('');
  function submit() {
    if (!newName || !newId || !newEmail) return;
    onCreate(newType, newName, newId, newEmail);
    setNewName(''); setNewId(''); setNewEmail(''); setShowForm(false);
  }
  return (
    <Card title={t('prcDsrTitle')} Icon={UserCheck}
      action={<Button size="sm" variant="secondary" onClick={() => setShowForm(s => !s)}>{showForm ? t('prcDsrCancelNew') : t('prcDsrNewBtn')}</Button>}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('prcDsrSub')}
      </div>
      {showForm && (
        <div style={{ padding: 12, marginBottom: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', display: 'grid', gridTemplateColumns: 'auto 1fr 1fr 1fr auto', gap: 8, alignItems: 'center' }}>
          <select value={newType} onChange={e => setNewType(e.target.value as DsrType)} style={{ padding: 6, fontSize: 11, border: '1px solid var(--border)', borderRadius: 4 }}>
            {(['ACCESS', 'RECTIFICATION', 'ERASURE', 'OBJECTION', 'RESTRICTION', 'PORTABILITY', 'WITHDRAW_CONSENT'] as DsrType[]).map(tp =>
              <option key={tp} value={tp}>{DSR_TYPE_LABEL[tp][lang]}</option>
            )}
          </select>
          <input value={newName} onChange={e => setNewName(e.target.value)} placeholder={t('prcDsrFormName')} style={{ padding: 6, fontSize: 11, border: '1px solid var(--border)', borderRadius: 4 }} />
          <input value={newId} onChange={e => setNewId(e.target.value)} placeholder={t('prcDsrFormId')} maxLength={4} className="mono" style={{ padding: 6, fontSize: 11, border: '1px solid var(--border)', borderRadius: 4 }} />
          <input value={newEmail} onChange={e => setNewEmail(e.target.value)} placeholder={t('prcDsrFormEmail')} type="email" style={{ padding: 6, fontSize: 11, border: '1px solid var(--border)', borderRadius: 4 }} />
          <Button size="sm" variant="primary" onClick={submit}>{t('prcDsrFormSubmit')}</Button>
        </div>
      )}
      {dsrs.map(d => {
        const sla = dsrSlaRemaining(d);
        return (
          <div key={d.id} style={{ padding: 14, marginBottom: 12, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12, marginBottom: 8, flexWrap: 'wrap' }}>
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 4 }}>
                  <span className="mono" style={{ fontSize: 11, color: 'var(--primary)' }}>{d.id}</span>
                  <Badge tone={DSR_STATUS_TONE[d.status]}>{DSR_STATUS_LABEL[d.status][lang]}</Badge>
                  <Badge tone="info">{DSR_TYPE_LABEL[d.type][lang]}</Badge>
                  {d.verificationMethod && <Badge tone="primary">{d.verificationMethod}</Badge>}
                  {d.extended && <Badge tone="warning">{t('prcDsrExtendedBadge')}</Badge>}
                </div>
                <div style={{ fontSize: 13, fontWeight: 600 }}>
                  {d.subjectName} · National ID …{d.nationalIdLast4}
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)' }} className="mono">
                  {d.contactEmail}
                </div>
                {d.dataSummaryEn && (
                  <div style={{ marginTop: 6, fontSize: 11, color: 'var(--ink-2)', lineHeight: 1.6, padding: 6, background: 'var(--surface-2)', borderRadius: 4 }}>
                    {lang === 'ar' ? d.dataSummaryAr : d.dataSummaryEn}
                  </div>
                )}
                {d.extensionReasonEn && (
                  <div style={{ marginTop: 6, fontSize: 11, color: 'var(--warning-fg)', lineHeight: 1.6, padding: 6, background: 'rgba(245,158,11,0.06)', borderRadius: 4 }}>
                    {lang === 'ar' ? d.extensionReasonAr : d.extensionReasonEn}
                  </div>
                )}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-end', minWidth: 200 }}>
                {/* SLA bar */}
                <div style={{ width: 200 }}>
                  <div style={{ fontSize: 10, color: sla.isOverdue ? 'var(--danger-fg)' : 'var(--ink-3)', textAlign: 'end', marginBottom: 2 }}>
                    {sla.isOverdue ? t('prcDsrOverdue') : `${sla.hoursRemaining}h ${t('prcDsrRemaining')}`}
                  </div>
                  <div style={{ height: 8, background: 'var(--surface-2)', borderRadius: 4, overflow: 'hidden' }}>
                    <div style={{
                      height: '100%', width: `${sla.pctElapsed}%`,
                      background: sla.isOverdue ? 'var(--danger)' : sla.pctElapsed > 80 ? 'var(--warning)' : 'var(--success)',
                    }} />
                  </div>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)', textAlign: 'end', marginTop: 2 }}>
                    {t('prcDsrDeadline')}: <span className="mono">{d.slaDeadline}</span>
                  </div>
                </div>
                {/* Action buttons */}
                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                  {d.status === 'INTAKE' && <Button size="sm" variant="primary" onClick={() => onTransition(d, 'VERIFYING_IDENTITY')}>{t('prcDsrVerify')}</Button>}
                  {d.status === 'VERIFYING_IDENTITY' && <Button size="sm" variant="primary" onClick={() => onTransition(d, 'COLLECTING')}>{t('prcDsrCollect')}</Button>}
                  {d.status === 'COLLECTING' && <Button size="sm" variant="primary" onClick={() => onTransition(d, 'REDACTING')}>{t('prcDsrRedact')}</Button>}
                  {d.status === 'REDACTING' && <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onTransition(d, 'DELIVERED')}>{t('prcDsrDeliver')}</Button>}
                  {d.status !== 'DELIVERED' && d.status !== 'REJECTED' && d.status !== 'EXTENDED' && (
                    <Button size="sm" variant="ghost" onClick={() => onExtend(d)}>{t('prcDsrExtend')}</Button>
                  )}
                </div>
              </div>
            </div>
            {/* Workflow stages */}
            <div style={{ display: 'flex', gap: 0, marginTop: 8, fontSize: 10 }}>
              {[
                { code: 'INTAKE',             label: t('prcDsrSt1'), at: d.filedAt },
                { code: 'VERIFYING_IDENTITY', label: t('prcDsrSt2'), at: d.identityVerifiedAt },
                { code: 'COLLECTING',          label: t('prcDsrSt3'), at: d.dataCollectedAt },
                { code: 'REDACTING',           label: t('prcDsrSt4'), at: d.redactionCompletedAt },
                { code: 'DELIVERED',           label: t('prcDsrSt5'), at: d.deliveredAt },
              ].map((s, i, arr) => {
                const done = !!s.at;
                return (
                  <div key={s.code} style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 4 }}>
                    <div style={{
                      width: 18, height: 18, borderRadius: '50%',
                      background: done ? 'var(--success)' : 'var(--surface-2)',
                      color: done ? 'white' : 'var(--ink-3)',
                      display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: 10, fontWeight: 700,
                    }}>
                      {done ? <CheckCircle2 size={10} /> : i + 1}
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ color: 'var(--ink-2)', fontWeight: 600 }}>{s.label}</div>
                      <div className="mono" style={{ color: 'var(--ink-3)', fontSize: 9 }}>
                        {s.at ? s.at.slice(0, 10) : '—'}
                      </div>
                    </div>
                    {i < arr.length - 1 && (
                      <div style={{ width: 16, height: 1, background: 'var(--border)' }} />
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
    </Card>
  );
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function Stat({ label, value, tone }: { label: string; value: string; tone: 'primary' | 'warning' | 'danger' | 'success' }) {
  const colour = tone === 'danger' ? 'var(--danger-fg)' : tone === 'warning' ? 'var(--warning-fg)' : tone === 'success' ? 'var(--success-fg)' : 'var(--primary)';
  return (
    <div style={{
      padding: 8, border: `1px solid ${colour}`, borderRadius: 'var(--r-sm)',
      background: 'var(--surface-2)', minWidth: 70, textAlign: 'center',
    }}>
      <div style={{ fontSize: 9, color: 'var(--ink-3)', textTransform: 'uppercase' }}>{label}</div>
      <div className="mono" style={{ fontSize: 16, fontWeight: 700, color: colour }}>{value}</div>
    </div>
  );
}

// ─── DSR pipeline (interactive kanban) ─────────────────────────────────────

function DsrPipeline({ dsrs, onAdvance, onCardClick }: {
  dsrs: DsrCase[];
  onAdvance: (id: string, fromStatus: string, toStatus: string | null) => void;
  onCardClick: (id: string) => void;
}) {
  const stages: PipelineStage[] = [
    { code: 'INTAKE',              labelEn: 'Intake',              labelAr: 'استلام',                tone: 'neutral', Icon: FileText },
    { code: 'VERIFYING_IDENTITY',  labelEn: 'Verifying identity',   labelAr: 'التحقق من الهوية',      tone: 'info',    Icon: UserCheck },
    { code: 'COLLECTING',          labelEn: 'Collecting data',      labelAr: 'جمع البيانات',           tone: 'info',    Icon: Files },
    { code: 'REDACTING',           labelEn: 'Redacting',             labelAr: 'إخفاء البيانات',         tone: 'warning', Icon: ShieldAlert },
    { code: 'DELIVERED',           labelEn: 'Delivered',            labelAr: 'مُسلَّم',                 tone: 'success', Icon: CheckCircle2 },
    { code: 'EXTENDED',            labelEn: 'Extended (60d)',        labelAr: 'مُمدَّد (60 يوم)',         tone: 'warning', Icon: Clock },
    { code: 'REJECTED',            labelEn: 'Rejected',              labelAr: 'مرفوض',                  tone: 'danger',  Icon: AlertTriangle },
  ];
  const cards: PipelineCard[] = dsrs.map(d => {
    const due = dueLabel(d.slaDeadline, () => '');
    return {
      id: d.id, status: d.status, refCode: d.id,
      titleEn: `${d.subjectName} · ${d.type}`,
      titleAr: `${d.subjectName} · ${d.type}`,
      subtitleEn: `NID …${d.nationalIdLast4} · ${d.contactEmail}`,
      subtitleAr: `هوية …${d.nationalIdLast4} · ${d.contactEmail}`,
      badges: [
        { label: d.verificationMethod ?? '—', tone: 'primary' as const },
        ...(d.extended ? [{ label: 'EXT', tone: 'warning' as const }] : []),
      ],
      urgent: due?.urgent && d.status !== 'DELIVERED' && d.status !== 'REJECTED',
      dueLabel: due?.label,
    };
  });
  return (
    <LifecyclePipeline
      stages={stages}
      cards={cards}
      introEn="Live data subject request pipeline. PDPL Article 4 30-day SLA tracked per case; complex requests can be EXTENDED to 60 days. Click a card to drill into the DSR detail."
      introAr="مسار طلبات أصحاب البيانات الحيّ. التزام 30 يوماً من المادة 4 لكل حالة؛ الطلبات المعقّدة يمكن تمديدها إلى 60 يوماً. انقر على بطاقة لفتح تفاصيل الطلب."
      onAdvance={onAdvance}
      onCardClick={onCardClick}
      emptyEn="No data subject requests yet."
      emptyAr="لا طلبات أصحاب بيانات بعد."
    />
  );
}
