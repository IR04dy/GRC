// MOC (Ministry of Commerce) compliance panel — DD-85.
//
// Three-card strip:
//   1. Tasattur Risk Index (TRI) — the headline number with band badge,
//      indicator-by-indicator breakdown, and MOC enforcement profile.
//   2. Consumer Protection requirements — refunds, labelling, warranties,
//      deceptive marketing, complaint resolution.
//   3. Commercial Registry obligations — annual confirmation, beneficial-
//      ownership, activity-code accuracy, capital-change notifications.
//
// Modelled on the MOC self-assessment portal pattern: TRI score on top,
// indicator breakdown for transparency, then the regulatory checklists.

import { useMemo } from 'react';
import { ShieldAlert, Building2, CheckCircle2, FileText } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Kpi } from '../ui/Kpi';
import { useI18n } from '../../lib/i18n';
import {
  loadMocState, computeTasatturScore, classifyTasatturBand, tasatturBandMeta,
  TASATTUR_INDICATORS, MOC_CONSUMER_REQUIREMENTS, MOC_REGISTRY_REQUIREMENTS,
  type MocRequirement,
} from '../../lib/moc';

export function MocPanel() {
  const { t, lang } = useI18n();

  const state = useMemo(() => loadMocState(), []);
  const score = computeTasatturScore(state);
  const band = classifyTasatturBand(score);
  const meta = tasatturBandMeta(band);

  return (
    <>
      {/* ── TRI hero card ── */}
      <Card title={t('mocTasatturTitle')} Icon={ShieldAlert}
        action={<Badge tone={meta.tone}>{lang === 'ar' ? meta.labelAr : meta.labelEn}</Badge>}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 16, lineHeight: 1.5 }}>
          {t('mocTasatturSub')}
        </div>

        <div className="kpi-grid" style={{ marginBottom: 16 }}>
          <Kpi Icon={ShieldAlert}  label={t('mocTriScoreKpi')}
               value={`${score}/100`}
               tone={meta.tone === 'danger' ? 'danger' : meta.tone === 'warning' ? 'warning' : meta.tone === 'info' ? 'info' : 'success'} />
          <Kpi Icon={CheckCircle2} label={t('mocTriBandKpi')}
               value={lang === 'ar' ? meta.labelAr : meta.labelEn}
               tone={meta.tone === 'danger' ? 'danger' : meta.tone === 'warning' ? 'warning' : meta.tone === 'info' ? 'info' : 'success'} />
          <Kpi Icon={FileText}     label={t('mocLastAssessmentKpi')}
               value={state.lastAssessmentAt ?? '—'} tone="primary" />
          <Kpi Icon={Building2}    label={t('mocLastCrConfirmationKpi')}
               value={state.lastCrConfirmationAt ?? '—'} tone="info" />
        </div>

        {/* MOC enforcement profile for current band */}
        <div style={{
          padding: 12, borderRadius: 'var(--r-sm)',
          background: `var(--${meta.tone}-bg, var(--surface-2))`,
          color: `var(--${meta.tone}-fg, var(--ink-1))`,
          fontSize: 12, lineHeight: 1.6, marginBottom: 16,
        }}>
          <div style={{ fontWeight: 700, marginBottom: 4 }}>{t('mocEnforcementLabel')}</div>
          <div>{lang === 'ar' ? meta.enforcementAr : meta.enforcementEn}</div>
        </div>

        {/* Indicator-by-indicator breakdown */}
        <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>
          {t('mocIndicatorBreakdown')}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {TASATTUR_INDICATORS.map(ind => {
            const raw = state.indicatorScores[ind.code] ?? 0;
            const contrib = Math.round((raw * ind.weight) / 10);
            const flagged = raw >= 5;
            return (
              <div key={ind.code} style={{
                padding: '10px 0', borderBottom: '1px dashed var(--border)',
                display: 'grid', gridTemplateColumns: '70px 1fr 60px 60px', gap: 10, alignItems: 'start',
              }}>
                <span className="mono" style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-2)' }}>{ind.code}</span>
                <div>
                  <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>
                    {lang === 'ar' ? ind.titleAr : ind.titleEn}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4, lineHeight: 1.5 }}>
                    {lang === 'ar' ? ind.rationaleAr : ind.rationaleEn}
                  </div>
                </div>
                <Badge tone={flagged ? 'warning' : 'neutral'}>{raw}/10</Badge>
                <span className="mono" style={{ fontSize: 11, fontWeight: 600, color: contrib > 0 ? 'var(--ink-2)' : 'var(--ink-3)', textAlign: 'end' }}>
                  +{contrib}
                </span>
              </div>
            );
          })}
        </div>
      </Card>

      {/* ── Consumer Protection requirements ── */}
      <Card title={t('mocConsumerReqsTitle')} Icon={CheckCircle2}>
        <RequirementList rows={MOC_CONSUMER_REQUIREMENTS} t={t} lang={lang} />
      </Card>

      {/* ── Commercial Registry requirements ── */}
      <Card title={t('mocRegistryReqsTitle')} Icon={Building2}>
        <RequirementList rows={MOC_REGISTRY_REQUIREMENTS} t={t} lang={lang} />
      </Card>
    </>
  );
}

function RequirementList({ rows, t, lang }: { rows: MocRequirement[]; t: (k: string) => string; lang: 'en' | 'ar' }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {rows.map(r => (
        <div key={r.code} style={{
          padding: '12px 0', borderBottom: '1px dashed var(--border)',
          display: 'grid', gridTemplateColumns: '70px 1fr 110px 110px', gap: 12, alignItems: 'start',
        }}>
          <span className="mono" style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-2)' }}>{r.code}</span>
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
              {lang === 'ar' ? r.titleAr : r.titleEn}
            </div>
            <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4, lineHeight: 1.5 }}>
              {lang === 'ar' ? r.descriptionAr : r.descriptionEn}
            </div>
          </div>
          <Badge tone={r.obligation === 'MANDATORY' ? 'danger' : 'info'}>
            {t(r.obligation === 'MANDATORY' ? 'nitaqatObligationMandatory' : 'nitaqatObligationRecommended')}
          </Badge>
          <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
            {r.cadence ? t(`nitaqatCadence${r.cadence}`) : '—'}
          </span>
        </div>
      ))}
    </div>
  );
}
