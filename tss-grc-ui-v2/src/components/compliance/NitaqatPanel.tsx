// Nitaqat (نطاقات) compliance panel — DD-83.
//
// Shows the firm's current Saudization band + requirements for the three
// HRSD compliance regimes (Nitaqat, Labor Law, GOSI). Modelled on the
// Qiwa portal's "نطاقات" dashboard: at-a-glance band colour, Saudization
// percentage, distance to next band, then a list of mandatory requirements
// each marked with cadence and obligation level.

import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Award, Users, AlertTriangle, CheckCircle2, Clock, Send } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Kpi } from '../ui/Kpi';
import { Progress } from '../ui/Progress';
import { useI18n } from '../../lib/i18n';
import {
  loadNitaqatState, computeSaudization, classifyNitaqatBand,
  nitaqatMeta, NITAQAT_BANDS,
  NITAQAT_REQUIREMENTS, HRSD_LABOR_REQUIREMENTS, HRSD_GOSI_REQUIREMENTS,
  type NitaqatBand, type NitaqatRequirement,
} from '../../lib/nitaqat';
import { REQUIREMENT_TO_INTAKE } from './HrsdProcessInitiation';

export function NitaqatPanel() {
  const { t, lang } = useI18n();

  const state = useMemo(() => loadNitaqatState(), []);
  const saudization = computeSaudization(state);
  const band = classifyNitaqatBand(saudization);
  const meta = nitaqatMeta(band);

  // Distance to next band — shown to give the user a "you need 12 more
  // Saudis to reach Green High" steer.
  const next = nextBandUp(band);
  const nextMeta = next ? nitaqatMeta(next) : null;
  const headcountToNext = nextMeta
    ? Math.max(0, Math.ceil((nextMeta.minPercent / 100) * state.totalEmployees) - state.saudiEmployees)
    : 0;

  return (
    <>
      {/* ── Hero band card ──────────────────────────────────────── */}
      <Card title={t('nitaqatTitle')} Icon={Award}
        action={<Badge tone={meta.tone}>{lang === 'ar' ? meta.labelAr : meta.labelEn}</Badge>}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 16, lineHeight: 1.5 }}>
          {t('nitaqatSub')}
        </div>

        <div className="kpi-grid" style={{ marginBottom: 16 }}>
          <Kpi Icon={Users} label={t('nitaqatTotalHeadcount')} value={state.totalEmployees} tone="primary" />
          <Kpi Icon={Users} label={t('nitaqatSaudiHeadcount')} value={state.saudiEmployees} tone="success" />
          <Kpi Icon={Award} label={t('nitaqatBandKpi')}        value={lang === 'ar' ? meta.labelAr : meta.labelEn} tone={meta.tone === 'primary' ? 'primary' : meta.tone === 'success' ? 'success' : meta.tone === 'warning' ? 'warning' : meta.tone === 'danger' ? 'danger' : 'info'} />
          <Kpi Icon={CheckCircle2} label={t('nitaqatSaudizationKpi')} value={`${saudization}%`} tone={meta.tone === 'danger' ? 'danger' : meta.tone === 'warning' ? 'warning' : 'success'} />
        </div>

        {/* ── Band ladder visualisation ── */}
        <div style={{ marginBottom: 12 }}>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>
            {t('nitaqatBandLadder')}
          </div>
          <div style={{ display: 'flex', alignItems: 'stretch', gap: 4 }}>
            {[...NITAQAT_BANDS].reverse().map(b => {
              const isCurrent = b.band === band;
              return (
                <div key={b.band} style={{
                  flex: 1, minWidth: 0,
                  padding: '8px 6px', borderRadius: 'var(--r-sm)',
                  background: isCurrent ? `var(--${b.tone}-bg, var(--surface-2))` : 'var(--surface-2)',
                  border: isCurrent
                    ? `2px solid var(--${b.tone}-fg, var(--border))`
                    : '1px solid var(--border)',
                  textAlign: 'center',
                }}>
                  <div style={{ fontSize: 10, fontWeight: 700, color: isCurrent ? `var(--${b.tone}-fg)` : 'var(--ink-2)' }}>
                    {lang === 'ar' ? b.labelAr : b.labelEn}
                  </div>
                  <div style={{ fontSize: 9, color: 'var(--ink-3)', marginTop: 2 }}>
                    ≥ {b.minPercent}%
                  </div>
                  {isCurrent && (
                    <div style={{ fontSize: 9, color: `var(--${b.tone}-fg)`, fontWeight: 700, marginTop: 2 }}>
                      ◆ {t('nitaqatYouAreHere')}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* ── Privileges + restrictions for current band ── */}
        <div style={{
          padding: 12, borderRadius: 'var(--r-sm)',
          background: `var(--${meta.tone}-bg, var(--surface-2))`,
          color: `var(--${meta.tone}-fg, var(--ink-1))`,
          fontSize: 12, lineHeight: 1.6, marginBottom: 12,
        }}>
          <div style={{ fontWeight: 700, marginBottom: 4 }}>{t('nitaqatBandPrivileges')}</div>
          <div>{lang === 'ar' ? meta.privilegesAr : meta.privilegesEn}</div>
          {meta.restrictionsEn && (
            <>
              <div style={{ fontWeight: 700, marginTop: 8, marginBottom: 4 }}>{t('nitaqatBandRestrictions')}</div>
              <div>{lang === 'ar' ? meta.restrictionsAr : meta.restrictionsEn}</div>
            </>
          )}
        </div>

        {/* ── Distance to next band ── */}
        {nextMeta && (
          <div style={{ padding: 12, borderRadius: 'var(--r-sm)', background: 'var(--surface-2)', border: '1px dashed var(--border)' }}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 6 }}>
              {t('nitaqatDistanceToNext')}
            </div>
            <div style={{ fontSize: 13, color: 'var(--ink-1)' }}>
              {t('nitaqatHeadcountToNext')
                .replace('{count}', String(headcountToNext))
                .replace('{nextBand}', lang === 'ar' ? nextMeta.labelAr : nextMeta.labelEn)
                .replace('{nextPct}', String(nextMeta.minPercent))}
            </div>
            <Progress label={`${saudization}% / ${nextMeta.minPercent}%`} value={Math.min(100, (saudization / nextMeta.minPercent) * 100)} />
          </div>
        )}

        <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 12, lineHeight: 1.5 }}>
          {t('nitaqatLastReport')}: <span className="mono" style={{ color: 'var(--ink-2)' }}>{state.lastReportSubmittedAt ?? '—'}</span> · {t('nitaqatSector')}: {state.sector.replace('_', ' ')} · {t('nitaqatSize')}: {state.establishmentSize}
        </div>
      </Card>

      {/* ── Requirements: Nitaqat ── */}
      <Card title={t('nitaqatReqsTitle')} Icon={AlertTriangle}>
        <RequirementList rows={NITAQAT_REQUIREMENTS} t={t} lang={lang} />
      </Card>

      {/* ── Requirements: Saudi Labor Law ── */}
      <Card title={t('hrsdLaborReqsTitle')} Icon={Users}>
        <RequirementList rows={HRSD_LABOR_REQUIREMENTS} t={t} lang={lang} />
      </Card>

      {/* ── Requirements: GOSI ── */}
      <Card title={t('hrsdGosiReqsTitle')} Icon={CheckCircle2}>
        <RequirementList rows={HRSD_GOSI_REQUIREMENTS} t={t} lang={lang} />
      </Card>
    </>
  );
}

function nextBandUp(band: NitaqatBand): NitaqatBand | null {
  const order: NitaqatBand[] = ['RED', 'YELLOW', 'GREEN_LOW', 'GREEN_MID', 'GREEN_HIGH', 'PLATINUM'];
  const i = order.indexOf(band);
  if (i < 0 || i === order.length - 1) return null;
  return order[i + 1];
}

function RequirementList({ rows, t, lang }: { rows: NitaqatRequirement[]; t: (k: string) => string; lang: 'en' | 'ar' }) {
  const navigate = useNavigate();
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {rows.map(r => {
        // DD-88: if this requirement has a registered intake workflow,
        // render an inline launcher so the user can act on it without
        // leaving the panel.
        const launchKind = REQUIREMENT_TO_INTAKE[r.code];
        return (
          <div key={r.code} style={{
            padding: '12px 0', borderBottom: '1px dashed var(--border)',
            display: 'grid', gridTemplateColumns: '90px 1fr 110px 110px 110px', gap: 12, alignItems: 'start',
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
            {r.cadence ? (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 11, color: 'var(--ink-3)' }}>
                <Clock size={11} />
                {t(`nitaqatCadence${r.cadence}`)}
              </span>
            ) : <span />}
            {launchKind ? (
              <button onClick={() => navigate(`/intake/${launchKind.toLowerCase()}`)} style={{
                display: 'inline-flex', alignItems: 'center', gap: 4,
                padding: '4px 10px',
                background: 'var(--primary-50, var(--info-bg))',
                color: 'var(--primary-700, var(--info-fg))',
                border: '1px solid var(--primary-100, var(--border))',
                borderRadius: 'var(--r-sm)',
                fontSize: 11, fontWeight: 600, cursor: 'pointer', alignSelf: 'flex-start',
              }}>
                <Send size={11} /> {t('nitaqatReqLaunch')}
              </button>
            ) : <span />}
          </div>
        );
      })}
    </div>
  );
}
