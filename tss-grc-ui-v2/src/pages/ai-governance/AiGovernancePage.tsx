import { useMemo, useState } from 'react';
import {
  Bot, Shield, AlertOctagon, Cpu, ScrollText, Globe, ShieldAlert, CheckCheck,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import {
  AI_MODELS, RISK_TIER_TONE, RISK_TIER_LABEL, STAGE_TONE, STAGE_LABEL,
  USE_CASE_LABEL, aggregateAi,
  type AiModel,
} from '../../lib/aiGovernance';
import { HijriDate } from '../../components/ui/HijriDate';

/**
 * AiGovernancePage — Sprint 6 follow-on (C1).
 *
 * SDAIA AI Ethics + SAMA AI in FS playbook. AI inventory with risk tier,
 * lifecycle stage, fairness score, AIA sign-off. The detail pane shows
 * the full record of one model with bias-audit cadence + AIA score.
 * Saudi context: KSA data residency flag, human-in-the-loop default,
 * BLOCKED tier renders red so unacceptable proposals are visibly stopped.
 */
export function AiGovernancePage() {
  const { t, lang } = useI18n();
  const [selected, setSelected] = useState<string>(AI_MODELS[0].code);
  const stats = useMemo(() => aggregateAi(), []);
  const current = AI_MODELS.find(m => m.code === selected) ?? AI_MODELS[0];

  return (
    <Shell title={t('aiTitle')} sub={t('aiSub')}>
      <PageHeader
        eyebrow={t('aiEyebrow')}
        title={t('aiTitle')}
        sub={t('aiPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Bot}            label={t('aiKpiTotal')}        value={String(stats.total)} tone="primary" />
        <Kpi Icon={ShieldAlert}     label={t('aiKpiHigh')}          value={String(stats.byTier.HIGH)} tone="warning" />
        <Kpi Icon={AlertOctagon}    label={t('aiKpiBlocked')}        value={String(stats.byTier.UNACCEPTABLE)} tone={stats.byTier.UNACCEPTABLE > 0 ? 'danger' : 'success'} />
        <Kpi Icon={Cpu}              label={t('aiKpiProduction')}    value={String(stats.inProduction)} tone="info" />
        <Kpi Icon={CheckCheck}       label={t('aiKpiAia')}            value={String(stats.aiaSignedOff)} tone="success" />
        <Kpi Icon={Shield}           label={t('aiKpiFairness')}        value={String(stats.avgFairness)} tone={stats.avgFairness >= 0.85 ? 'success' : 'warning'} />
        <Kpi Icon={ShieldAlert}      label={t('aiKpiNeedsAudit')}     value={String(stats.needsAudit)} tone={stats.needsAudit > 0 ? 'warning' : 'success'} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '380px 1fr', gap: 16 }}>
        {/* Inventory list */}
        <Card padded={false} title={t('aiInventoryTitle')} Icon={Bot}
          action={<Badge tone="primary">{AI_MODELS.length}</Badge>}>
          <div style={{ maxHeight: 660, overflowY: 'auto' }}>
            {AI_MODELS.map(m => {
              const isSelected = m.code === selected;
              return (
                <button key={m.code} onClick={() => setSelected(m.code)}
                  style={{
                    width: '100%', textAlign: 'start',
                    padding: '12px 14px', border: 'none', cursor: 'pointer',
                    borderBottom: '1px solid var(--border)',
                    background: isSelected ? 'var(--primary-50)' : 'transparent',
                    display: 'flex', flexDirection: 'column', gap: 6,
                  }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{m.code}</span>
                    <Badge tone={RISK_TIER_TONE[m.riskTier]}>{lang === 'ar' ? RISK_TIER_LABEL[m.riskTier].ar : RISK_TIER_LABEL[m.riskTier].en}</Badge>
                    <Badge tone={STAGE_TONE[m.stage]}>{lang === 'ar' ? STAGE_LABEL[m.stage].ar : STAGE_LABEL[m.stage].en}</Badge>
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>
                    {lang === 'ar' ? m.nameAr : m.nameEn}
                  </div>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {lang === 'ar' ? USE_CASE_LABEL[m.useCase].ar : USE_CASE_LABEL[m.useCase].en} · {m.modality} · {m.vendor}
                  </div>
                </button>
              );
            })}
          </div>
        </Card>

        {/* Detail */}
        <AiModelDetail model={current} t={t} lang={lang} />
      </div>
    </Shell>
  );
}

function AiModelDetail({ model, t, lang }: { model: AiModel; t: (k: string) => string; lang: 'en' | 'ar' }) {
  const blocked = model.stage === 'BLOCKED';
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, flexWrap: 'wrap' }}>
          <span className="mono" style={{ fontSize: 11, color: 'var(--primary)' }}>{model.code}</span>
          <Badge tone={RISK_TIER_TONE[model.riskTier]}>{lang === 'ar' ? RISK_TIER_LABEL[model.riskTier].ar : RISK_TIER_LABEL[model.riskTier].en}</Badge>
          <Badge tone={STAGE_TONE[model.stage]}>{lang === 'ar' ? STAGE_LABEL[model.stage].ar : STAGE_LABEL[model.stage].en}</Badge>
          <Badge tone="info">{lang === 'ar' ? USE_CASE_LABEL[model.useCase].ar : USE_CASE_LABEL[model.useCase].en}</Badge>
          <Badge tone="neutral">{model.modality}</Badge>
          <Badge tone={model.dataResidency === 'KSA' ? 'success' : 'warning'}>
            <Globe size={10} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />
            {model.dataResidency}
          </Badge>
        </div>
        <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 6 }}>
          {lang === 'ar' ? model.nameAr : model.nameEn}
        </div>
        <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6 }}>
          {lang === 'ar' ? model.descriptionAr : model.descriptionEn}
        </div>
        {blocked && (
          <div style={{
            marginTop: 12, padding: 10,
            background: 'var(--danger-bg)', color: 'var(--danger-fg)',
            borderRadius: 'var(--r-sm)', fontSize: 12, lineHeight: 1.6,
            display: 'inline-flex', alignItems: 'center', gap: 6,
          }}>
            <AlertOctagon size={14} />
            {t('aiBlockedNote')}
          </div>
        )}
      </Card>

      {/* AIA + Fairness */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <Card title={t('aiCardAia')} Icon={ScrollText}
          action={
            model.aiaSignedOff
              ? <Badge tone="success">{t('aiAiaSigned')}</Badge>
              : <Badge tone="warning">{t('aiAiaPending')}</Badge>
          }>
          {(() => {
            const aia = model.aiaScore ?? 0;
            const tone = aia >= 75 ? 'success' : aia >= 50 ? 'warning' : 'danger';
            return (
              <div className={`stat-tile stat-tile-${tone}`} style={{ flexDirection: 'row', alignItems: 'baseline', gap: 12 }}>
                <div className="stat-tile-value">{model.aiaScore ?? '—'}</div>
                <div style={{ fontSize: 14, color: 'var(--ink-3)' }}>/100</div>
              </div>
            );
          })()}
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 6 }}>
            {t('aiAiaScoreSub')}
          </div>
        </Card>

        <Card title={t('aiCardFairness')} Icon={Shield}>
          {(() => {
            const f = model.fairnessScore ?? 0;
            const tone = f >= 0.85 ? 'success' : f >= 0.70 ? 'warning' : 'danger';
            return (
              <div className={`stat-tile stat-tile-${tone}`} style={{ flexDirection: 'row', alignItems: 'baseline', gap: 12 }}>
                <div className="stat-tile-value">{model.fairnessScore != null ? model.fairnessScore.toFixed(2) : '—'}</div>
                <div style={{ fontSize: 14, color: 'var(--ink-3)' }}>/1.00</div>
              </div>
            );
          })()}
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 6 }}>
            {t('aiFairnessScoreSub')}
            {model.lastBiasAuditAt && (
              <> · {t('aiLastBiasAudit')}: <HijriDate iso={model.lastBiasAuditAt} layout="inline" mono /></>
            )}
          </div>
        </Card>
      </div>

      {/* Operational attrs */}
      <Card title={t('aiCardOps')} Icon={Cpu}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16 }}>
          <Field label={t('aiFieldVendor')} value={model.vendor} />
          <Field label={t('aiFieldOwner')}  value={model.ownerEmail} mono />
          <Field label={t('aiFieldHumanLoop')}
            value={model.humanInTheLoop ? t('aiHumanLoopYes') : t('aiHumanLoopNo')}
            danger={!model.humanInTheLoop && (model.riskTier === 'HIGH' || model.riskTier === 'UNACCEPTABLE')} />
        </div>
      </Card>
    </div>
  );
}

function Field({ label, value, mono, danger }: { label: string; value: string; mono?: boolean; danger?: boolean }) {
  return (
    <div>
      <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{label}</div>
      <div style={{
        fontSize: 13, marginTop: 4, fontWeight: 600,
        fontFamily: mono ? 'var(--font-mono)' : undefined,
        color: danger ? 'var(--danger)' : 'var(--ink-1)',
      }}>{value}</div>
    </div>
  );
}
