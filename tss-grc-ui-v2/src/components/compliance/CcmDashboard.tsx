import { useEffect, useMemo, useState } from 'react';
import { Activity, ShieldCheck, AlertTriangle, AlertOctagon, Database, Plug, FileCheck2, RefreshCw } from 'lucide-react';
import { Card } from '../ui/Card';
import { Kpi } from '../ui/Kpi';
import { Badge } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import {
  CCM_CHECKS, aggregateCoverage, classifyFreshness,
  synthesiseStaleEvidenceWorkflows, FRESHNESS_TONE, RESULT_TONE, connectorById,
} from '../../lib/ccm';
import { deriveAllLive } from '../../lib/sla';
import { ESCALATION_TONE } from '../../lib/workflows';
import { SlaCountdown } from '../workflows/SlaCountdown';

/**
 * CcmDashboard — Sprint 4 Continuous Control Monitoring panel.
 *
 * continuous-controls-monitoring pattern. Pulls together three pillars:
 *   1. Connectors (catalogue) — already ship under DD-62 in connectors.ts
 *   2. Control catalogue — Saudi NCA ECC + ISO + SOC 2 + PDPL
 *   3. The mapping that says "this connector check satisfies that control"
 * Then layers an evidence-freshness rule on top so a stale check turns
 * into a live workflow instance the SLA engine can countdown against.
 */
export function CcmDashboard() {
  const { t, lang } = useI18n();
  const [now, setNow] = useState(() => new Date());

  // Re-tick every minute so the freshness classifier stays in sync.
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(id);
  }, []);

  const coverage = useMemo(() => aggregateCoverage(CCM_CHECKS, now), [now]);
  const staleWorkflows = useMemo(
    () => deriveAllLive(synthesiseStaleEvidenceWorkflows(CCM_CHECKS, now), undefined, now),
    [now],
  );

  // Group checks by connector so the matrix doesn't sprawl.
  const byConnector = useMemo(() => {
    const map = new Map<string, typeof CCM_CHECKS>();
    for (const c of CCM_CHECKS) {
      if (!map.has(c.connectorId)) map.set(c.connectorId, []);
      map.get(c.connectorId)!.push(c);
    }
    return Array.from(map.entries());
  }, []);

  return (
    <Card title={t('ccmTitle')} Icon={Activity}
      action={<Badge tone={coverage.expired > 0 ? 'danger' : coverage.stale > 0 ? 'warning' : 'success'}>
        {Math.round((coverage.fresh / coverage.totalChecks) * 100)}% {t('ccmHealthSuffix')}
      </Badge>}>

      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
        {t('ccmSub')}
      </div>

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={FileCheck2}    label={t('ccmKpiChecks')}     value={String(coverage.totalChecks)} tone="primary" to="/ccm/tests" />
        <Kpi Icon={ShieldCheck}   label={t('ccmKpiFresh')}      value={String(coverage.fresh)}       tone="success" to="/ccm/tests" />
        <Kpi Icon={AlertTriangle} label={t('ccmKpiStale')}      value={String(coverage.stale)}       tone="warning" to="/ccm/tests" />
        <Kpi Icon={AlertOctagon}  label={t('ccmKpiExpired')}    value={String(coverage.expired)}     tone="danger" to="/ccm/tests" />
        <Kpi Icon={Database}      label={t('ccmKpiControls')}   value={String(coverage.controlsCovered)} tone="info" to="/controls/library" />
        <Kpi Icon={Plug}          label={t('ccmKpiConnectors')} value={String(coverage.activeConnectors)} tone="primary" to="/connectors" />
      </div>

      {/* ── Stale evidence feed (driven by SLA engine) ── */}
      {staleWorkflows.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 6 }}>
            {t('ccmStaleFeedTitle')}
          </div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
            {t('ccmStaleFeedSub')}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {staleWorkflows.map(w => (
              <div key={w.id} style={{
                padding: '10px 0', borderBottom: '1px dashed var(--border)',
                display: 'grid', gridTemplateColumns: '1fr auto', gap: 8, alignItems: 'center',
              }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>{w.title}</div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>{w.reason}</div>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
                  <SlaCountdown instance={w} />
                  <Badge tone={ESCALATION_TONE[w.sla.tier]}>{w.owner}</Badge>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Connector × Check coverage matrix ── */}
      <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 8 }}>
        {t('ccmCoverageTitle')}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {byConnector.map(([connectorId, checks]) => {
          const connector = connectorById(connectorId);
          return (
            <div key={connectorId} style={{
              padding: '10px 0', borderBottom: '1px dashed var(--border)',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                <Plug size={13} color="var(--primary)" />
                <span style={{ fontSize: 13, fontWeight: 700 }}>{connector?.nameEn ?? connectorId}</span>
                <Badge tone="neutral">{connector?.source}</Badge>
                <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{checks.length} {t('ccmChecksLabel')}</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, paddingInlineStart: 22 }}>
                {checks.map(c => {
                  const f = classifyFreshness(c, now);
                  return (
                    <div key={c.id} style={{
                      display: 'grid', gridTemplateColumns: '1fr auto auto auto', gap: 8, alignItems: 'center',
                      padding: '4px 0',
                    }}>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: 12, color: 'var(--ink-1)' }}>
                          {lang === 'ar' ? c.checkNameAr : c.checkNameEn}
                        </div>
                        <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }} className="mono">
                          {c.controls.map(ctrl => `${ctrl.framework} ${ctrl.code}`).join(' · ')}
                        </div>
                      </div>
                      <Badge tone={RESULT_TONE[c.lastResult]}>{c.lastResult}</Badge>
                      <Badge tone={FRESHNESS_TONE[f]}>{t(`ccmFreshness${f.charAt(0)}${f.slice(1).toLowerCase()}`) || f}</Badge>
                      <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)', minWidth: 50, textAlign: 'end' }}>
                        {c.lastEvidenceAt ? agoLabel(c.lastEvidenceAt, now, t) : '—'}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      <div style={{ marginTop: 12, fontSize: 11, color: 'var(--ink-3)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
        <RefreshCw size={11} />
        {t('ccmAutoRefresh')}
      </div>
    </Card>
  );
}

function agoLabel(iso: string, now: Date, t: (k: string) => string): string {
  const ms = now.getTime() - new Date(iso).getTime();
  const hours = ms / 3_600_000;
  if (hours < 1) return t('ccmAgoMin');
  if (hours < 24) return t('ccmAgoHours').replace('{n}', String(Math.round(hours)));
  const days = hours / 24;
  return t('ccmAgoDays').replace('{n}', String(Math.round(days)));
}
