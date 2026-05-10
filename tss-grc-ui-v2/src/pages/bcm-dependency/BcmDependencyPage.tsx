import { useMemo, useState, type CSSProperties } from 'react';
import {
  GitBranch, Cloud, Truck, Activity, AlertTriangle, ChevronRight, Building2, Server,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import {
  buildUpstreamTree, criticalProcesses,
  vendorRollupForProcesses, regionRollupForProcesses, rtoChainAnalysis,
  aggregateBcmDeps,
} from '../../lib/bcmDependency';
import { ASSETS, CRITICALITY_TONE, TYPE_LABEL } from '../../lib/assets';

/**
 * BcmDependencyPage — Sprint 6 follow-on (B4).
 *
 * Process → asset → vendor → region dependency map. Pick a critical
 * process, see its full upstream chain visualised as a hierarchical tree;
 * the right side rolls up vendors + regions impacted, and the RTO chain
 * panel highlights any RTO-mismatch (declared 1h, effective 4h because of
 * a slower upstream dependency).
 */
export function BcmDependencyPage() {
  const { t, lang } = useI18n();
  const processes = useMemo(() => criticalProcesses(), []);
  const [selected, setSelected] = useState<string>(processes[0]?.code ?? '');
  const stats = useMemo(() => aggregateBcmDeps(), []);
  const vendors = useMemo(() => vendorRollupForProcesses(processes), [processes]);
  const regions = useMemo(() => regionRollupForProcesses(processes), [processes]);
  const rtoResults = useMemo(() => rtoChainAnalysis(processes), [processes]);

  const tree = useMemo(() => buildUpstreamTree(selected, 6), [selected]);
  const currentRto = rtoResults.find(r => r.processCode === selected);
  const current = ASSETS.find(a => a.code === selected);

  return (
    <Shell title={t('bdTitle')} sub={t('bdSub')}>
      <PageHeader
        eyebrow={t('bdEyebrow')}
        title={t('bdTitle')}
        sub={t('bdPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Activity}      label={t('bdKpiProcesses')} value={String(stats.criticalProcessCount)} tone="primary" />
        <Kpi Icon={Truck}          label={t('bdKpiVendors')}    value={String(stats.uniqueVendors)} tone="info" />
        <Kpi Icon={Cloud}          label={t('bdKpiRegions')}    value={String(stats.uniqueRegions)} tone="info" />
        <Kpi Icon={AlertTriangle}  label={t('bdKpiRtoMismatch')} value={String(stats.rtoMismatches)} tone={stats.rtoMismatches > 0 ? 'danger' : 'success'} />
        <Kpi Icon={GitBranch}      label={t('bdKpiLargest')}    value={String(stats.largestProcessImpact.impact)} tone="warning" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '320px 1fr', gap: 16 }}>
        {/* Process picker */}
        <Card padded={false} title={t('bdProcessListTitle')} Icon={Activity}
          action={<Badge tone="primary">{processes.length}</Badge>}>
          <div style={{ maxHeight: 600, overflowY: 'auto' }}>
            {processes.map(p => {
              const isSelected = p.code === selected;
              const rto = rtoResults.find(r => r.processCode === p.code);
              return (
                <button key={p.code} onClick={() => setSelected(p.code)}
                  style={{
                    width: '100%', textAlign: 'start',
                    padding: '12px 14px', border: 'none', cursor: 'pointer',
                    borderBottom: '1px solid var(--border)',
                    background: isSelected ? 'var(--primary-50)' : 'transparent',
                    display: 'flex', flexDirection: 'column', gap: 4,
                  }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{p.code}</span>
                    <Badge tone={CRITICALITY_TONE[p.criticality]}>{p.criticality}</Badge>
                    {rto?.mismatch && <Badge tone="danger">RTO!</Badge>}
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>{lang === 'ar' ? p.nameAr : p.nameEn}</div>
                  {rto && (
                    <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                      {t('bdDeclaredRto')}: {rto.declaredRtoHours ?? '—'}h · {t('bdEffectiveRto')}: {rto.effectiveRtoHours}h
                    </div>
                  )}
                </button>
              );
            })}
          </div>
        </Card>

        {/* Detail */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {current && (
            <Card>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                <span className="mono" style={{ fontSize: 11, color: 'var(--primary)' }}>{current.code}</span>
                <Badge tone={CRITICALITY_TONE[current.criticality]}>{current.criticality}</Badge>
                {current.rtoHours && <Badge tone="warning">RTO {current.rtoHours}h</Badge>}
              </div>
              <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 4 }}>
                {lang === 'ar' ? current.nameAr : current.nameEn}
              </div>
              {current.descriptionEn && (
                <div style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.6 }}>
                  {lang === 'ar' ? current.descriptionAr : current.descriptionEn}
                </div>
              )}
            </Card>
          )}

          {/* RTO chain analysis */}
          {currentRto && (
            <Card title={t('bdRtoChainTitle')} Icon={AlertTriangle}
              action={
                currentRto.mismatch
                  ? <Badge tone="danger">{t('bdRtoMismatch')}</Badge>
                  : <Badge tone="success">{t('bdRtoAligned')}</Badge>
              }>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
                <div>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('bdDeclaredRto')}</div>
                  <div style={{ fontSize: 22, fontWeight: 800, marginTop: 4 }} className="mono">
                    {currentRto.declaredRtoHours ?? '—'}h
                  </div>
                </div>
                <div>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('bdEffectiveRto')}</div>
                  <div style={{ fontSize: 22, fontWeight: 800, marginTop: 4, color: currentRto.mismatch ? 'var(--danger)' : 'var(--ink-1)' }} className="mono">
                    {currentRto.effectiveRtoHours}h
                  </div>
                </div>
                <div>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('bdBottleneck')}</div>
                  <div style={{ fontSize: 13, fontWeight: 600, marginTop: 4 }} className="mono">
                    {currentRto.bottleneckAssetCode}
                  </div>
                </div>
              </div>
              {currentRto.mismatch && (
                <div style={{ marginTop: 12, padding: 10, background: 'var(--danger-bg)', color: 'var(--danger-fg)', borderRadius: 'var(--r-sm)', fontSize: 12 }}>
                  {t('bdRtoMismatchNote').replace('{declared}', String(currentRto.declaredRtoHours))
                    .replace('{effective}', String(currentRto.effectiveRtoHours))
                    .replace('{bottleneck}', currentRto.bottleneckAssetCode)}
                </div>
              )}
            </Card>
          )}

          {/* Dependency tree */}
          <Card title={t('bdTreeTitle')} Icon={GitBranch}
            action={<Badge tone="primary">{tree.length} {t('bdNodesSuffix')}</Badge>}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 12 }}>
              {t('bdTreeSub')}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {tree.map((node, idx) => (
                <div key={`${node.asset.code}-${idx}`} style={{
                  display: 'grid',
                  gridTemplateColumns: `${20 * node.level}px 18px 1fr auto auto`,
                  gap: 8, alignItems: 'center',
                  padding: '6px 8px',
                  background: node.level === 0 ? 'var(--primary-50)' : 'transparent',
                  borderRadius: 'var(--r-sm)',
                  borderInlineStart: node.level > 0 ? '2px solid var(--border)' : 'none',
                }}>
                  <span></span>
                  <ChevronRight size={12} color="var(--ink-3)" />
                  <div>
                    <div style={{ fontSize: 12, fontWeight: 600 }}>
                      {lang === 'ar' ? node.asset.nameAr : node.asset.nameEn}
                    </div>
                    <div className="mono" style={{ fontSize: 9, color: 'var(--ink-3)', marginTop: 1 }}>
                      {node.asset.code} · {lang === 'ar' ? TYPE_LABEL[node.asset.type].ar : TYPE_LABEL[node.asset.type].en}
                      {node.asset.hostedByVendor && ` · ${t('bdHostedBy')}: ${node.asset.hostedByVendor}`}
                    </div>
                  </div>
                  {node.asset.rtoHours != null && (
                    <Badge tone="warning">RTO {node.asset.rtoHours}h</Badge>
                  )}
                  <Badge tone={CRITICALITY_TONE[node.asset.criticality]}>{node.asset.criticality}</Badge>
                </div>
              ))}
            </div>
          </Card>

          {/* Vendor + Region rollups */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Card title={t('bdVendorRollupTitle')} Icon={Truck}
              action={<Badge tone="primary">{vendors.length}</Badge>}>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
                {t('bdVendorRollupSub')}
              </div>
              {vendors.length === 0
                ? <div style={{ padding: 12, fontSize: 12, color: 'var(--ink-3)' }}>{t('bdRollupEmpty')}</div>
                : <div style={{ display: 'flex', flexDirection: 'column' }}>
                    {vendors.slice(0, 8).map(v => (
                      <div key={v.vendorCode} style={rollupRow}>
                        <Building2 size={12} color="var(--ink-3)" />
                        <span className="mono" style={{ fontSize: 12, fontWeight: 600 }}>{v.vendorCode}</span>
                        <Badge tone={v.processCodes.length >= 3 ? 'danger' : v.processCodes.length === 2 ? 'warning' : 'info'}>
                          {v.processCodes.length} {t('bdImpactedSuffix')}
                        </Badge>
                      </div>
                    ))}
                  </div>}
            </Card>
            <Card title={t('bdRegionRollupTitle')} Icon={Cloud}
              action={<Badge tone="primary">{regions.length}</Badge>}>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
                {t('bdRegionRollupSub')}
              </div>
              {regions.length === 0
                ? <div style={{ padding: 12, fontSize: 12, color: 'var(--ink-3)' }}>{t('bdRollupEmpty')}</div>
                : <div style={{ display: 'flex', flexDirection: 'column' }}>
                    {regions.map(r => (
                      <div key={r.regionCode} style={rollupRow}>
                        <Server size={12} color="var(--ink-3)" />
                        <span className="mono" style={{ fontSize: 12, fontWeight: 600 }}>{r.regionCode}</span>
                        <Badge tone={r.processCodes.length >= 4 ? 'danger' : r.processCodes.length >= 2 ? 'warning' : 'info'}>
                          {r.processCodes.length} {t('bdImpactedSuffix')}
                        </Badge>
                      </div>
                    ))}
                  </div>}
            </Card>
          </div>
        </div>
      </div>
    </Shell>
  );
}

const rollupRow: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '16px 1fr auto',
  gap: 8,
  alignItems: 'center',
  padding: '8px 0',
  borderBottom: '1px dashed var(--border)',
};
