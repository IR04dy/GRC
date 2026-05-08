import { useMemo } from 'react';
import {
  Activity, AlertOctagon, ShieldCheck, Calendar, Server, TrendingUp, Clock,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { HijriDate } from '../../components/ui/HijriDate';
import {
  SERVICES, ACTIVE_ALERTS, SLOS, ON_CALL,
  HEALTH_TONE, HEALTH_LABEL, SEVERITY_TONE,
  aggregateMonitoring,
} from '../../lib/monitoring';

/**
 * MonitoringPage — Sprint 6 production hardening.
 *
 * Single-pane-of-glass for production monitoring:
 *   • Service health grid (uptime + latency + error rate per service)
 *   • SLO panel (target vs actual + burn rate)
 *   • Active alerts feed (with acknowledged state)
 *   • On-call rotation (next 4 weeks)
 *
 * Production wires Prometheus + Alertmanager + Grafana; this page
 * surfaces the same data the SOC + on-call see at 03:00 KSA when
 * something pages.
 */
export function MonitoringPage() {
  const { t, lang } = useI18n();
  const stats = useMemo(() => aggregateMonitoring(), []);

  return (
    <Shell title={t('monTitle')} sub={t('monSub')}>
      <PageHeader
        eyebrow={t('monEyebrow')}
        title={t('monTitle')}
        sub={t('monPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Server}        label={t('monKpiServices')}    value={String(stats.totalServices)} tone="primary" />
        <Kpi Icon={ShieldCheck}    label={t('monKpiHealthy')}      value={String(stats.healthyServices)} tone="success" />
        <Kpi Icon={Activity}       label={t('monKpiDegraded')}     value={String(stats.degradedServices)} tone={stats.degradedServices > 0 ? 'warning' : 'success'} />
        <Kpi Icon={AlertOctagon}    label={t('monKpiAlerts')}       value={String(stats.activeAlerts)} tone={stats.activeAlerts > 0 ? 'warning' : 'success'} />
        <Kpi Icon={TrendingUp}      label={t('monKpiSlo')}          value={`${stats.slosOnTrack} / ${stats.totalSlos}`} tone={stats.slosOnTrack === stats.totalSlos ? 'success' : 'warning'} />
        <Kpi Icon={Calendar}        label={t('monKpiOnCall')}       value={lang === 'ar' ? ON_CALL[0].primaryAr : ON_CALL[0].primaryEn} tone="info" />
      </div>

      {/* Service health grid */}
      <Card title={t('monServicesTitle')} Icon={Server}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('monServicesSub')}
        </div>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
          <thead>
            <tr>
              <th style={th}>{t('monTblService')}</th>
              <th style={th}>{t('monTblHealth')}</th>
              <th style={th}>{t('monTblUptime')}</th>
              <th style={th}>{t('monTblLatency')}</th>
              <th style={th}>{t('monTblError')}</th>
              <th style={th}>{t('monTblChecked')}</th>
            </tr>
          </thead>
          <tbody>
            {SERVICES.map(s => (
              <tr key={s.code} style={{ borderBottom: '1px dashed var(--border)' }}>
                <td style={td}>
                  <strong>{lang === 'ar' ? s.nameAr : s.nameEn}</strong>
                  <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{s.code}</div>
                </td>
                <td style={td}>
                  <Badge tone={HEALTH_TONE[s.health]} dot>
                    {lang === 'ar' ? HEALTH_LABEL[s.health].ar : HEALTH_LABEL[s.health].en}
                  </Badge>
                </td>
                <td style={{ ...td, fontFamily: 'var(--font-mono)' }}>{s.uptime30d.toFixed(2)}%</td>
                <td style={{ ...td, fontFamily: 'var(--font-mono)' }}>{s.latencyP95Ms}ms</td>
                <td style={{ ...td, fontFamily: 'var(--font-mono)', color: s.errorRatePct > 0.1 ? 'var(--danger)' : 'inherit' }}>{s.errorRatePct.toFixed(2)}%</td>
                <td style={{ ...td, fontFamily: 'var(--font-mono)', fontSize: 10, color: 'var(--ink-3)' }}>{s.lastCheckedAt.slice(11, 19)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      {/* SLO panel */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('monSloTitle')} Icon={TrendingUp}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('monSloSub')}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12 }}>
            {SLOS.map(slo => {
              const onTrack = slo.actual >= slo.target;
              const pct = Math.min(100, (slo.actual / slo.target) * 100);
              return (
                <div key={slo.code} style={{
                  padding: 14, borderRadius: 'var(--r-md)',
                  background: onTrack ? 'var(--success-bg)' : 'var(--warning-bg)',
                  border: `1px solid ${onTrack ? 'var(--success-fg)' : 'var(--warning-fg)'}`,
                }}>
                  <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 6 }}>
                    {lang === 'ar' ? slo.nameAr : slo.nameEn}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 8 }}>
                    <span className="mono" style={{ fontSize: 24, fontWeight: 800 }}>{slo.actual.toFixed(2)}%</span>
                    <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>/ {slo.target.toFixed(2)}%</span>
                  </div>
                  <div style={{
                    height: 6, background: 'var(--surface-1)',
                    borderRadius: 3, overflow: 'hidden', marginBottom: 6,
                  }}>
                    <div style={{
                      width: `${pct}%`, height: '100%',
                      background: onTrack ? 'var(--success-fg)' : 'var(--warning-fg)',
                    }} />
                  </div>
                  <div style={{ display: 'flex', gap: 8, fontSize: 10, color: 'var(--ink-3)' }}>
                    <span>{slo.window}</span>
                    <span>·</span>
                    <span>{t('monBurn')} {slo.burnRate.toFixed(1)}×</span>
                    {slo.burnRate > 1 && <Badge tone="warning">{t('monBurning')}</Badge>}
                  </div>
                </div>
              );
            })}
          </div>
        </Card>
      </div>

      {/* Active alerts */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('monAlertsTitle')} Icon={AlertOctagon}
          action={<Badge tone={stats.activeAlerts > 0 ? 'warning' : 'success'}>{stats.activeAlerts}</Badge>}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('monAlertsSub')}
          </div>
          {ACTIVE_ALERTS.length === 0
            ? <div style={{ padding: 16, fontSize: 12, color: 'var(--ink-3)', textAlign: 'center' }}>
                {t('monAlertsEmpty')}
              </div>
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {ACTIVE_ALERTS.map(a => (
                  <div key={a.id} style={{
                    padding: '12px 0', borderBottom: '1px dashed var(--border)',
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                      <span className="mono" style={{ fontSize: 11 }}>{a.id}</span>
                      <Badge tone={SEVERITY_TONE[a.severity]}>{a.severity}</Badge>
                      <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{a.rule}</span>
                      {a.acknowledged && <Badge tone="success">{t('monAcked')}</Badge>}
                    </div>
                    <div style={{ fontSize: 13, color: 'var(--ink-1)', lineHeight: 1.5 }}>
                      {lang === 'ar' ? a.narrativeAr : a.narrativeEn}
                    </div>
                    <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 4 }}>
                      <Clock size={10} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
                      {t('monStartedAt')}: <span className="mono">{a.startedAt.slice(0, 16).replace('T', ' ')}</span>
                    </div>
                  </div>
                ))}
              </div>}
        </Card>
      </div>

      {/* On-call rotation */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('monOnCallTitle')} Icon={Calendar}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('monOnCallSub')}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12 }}>
            {ON_CALL.map((slot, idx) => (
              <div key={slot.date} style={{
                padding: 14, borderRadius: 'var(--r-md)',
                background: idx === 0 ? 'var(--primary-50)' : 'var(--surface-2)',
                border: `1px solid ${idx === 0 ? 'var(--primary)' : 'var(--border)'}`,
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                  <HijriDate iso={slot.date} layout="inline" mono />
                  {idx === 0 && <Badge tone="primary">{t('monThisWeek')}</Badge>}
                </div>
                <div style={{ fontSize: 13, fontWeight: 600 }}>
                  {t('monPrimary')}: {lang === 'ar' ? slot.primaryAr : slot.primaryEn}
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                  {t('monBackup')}: {lang === 'ar' ? slot.backupAr : slot.backupEn}
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </Shell>
  );
}

const th = {
  padding: '8px 10px', textAlign: 'start' as const, fontSize: 10,
  textTransform: 'uppercase' as const, letterSpacing: '0.05em',
  color: 'var(--ink-3)', background: 'var(--surface-2)',
  borderBottom: '2px solid var(--ink-3)',
};
const td = { padding: '10px', verticalAlign: 'middle' as const, lineHeight: 1.5 };
