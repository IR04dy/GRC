import { useMemo, useState } from 'react';
import {
  Server, Database, Cloud, Network, Building2, Cog, Smartphone, Layers,
  AlertOctagon, Activity, ChevronRight, Filter, Landmark, FileCheck2, GitBranch,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  ASSETS, downstreamOf, upstreamOf, transitiveBlastRadius,
  ccmChecksForAsset, approvalsForAsset,
  CRITICALITY_TONE, TYPE_LABEL, RESIDENCY_TONE,
  type Asset, type AssetType, type AssetCriticality,
} from '../../lib/assets';
import { FRESHNESS_TONE, RESULT_TONE, classifyFreshness } from '../../lib/ccm';

const TYPE_ICON: Record<AssetType, typeof Server> = {
  APPLICATION:    Layers,
  DATABASE:       Database,
  INFRASTRUCTURE: Server,
  NETWORK:        Network,
  SERVICE:        Cog,
  PROCESS:        Activity,
  FACILITY:       Building2,
  CLOUD_REGION:   Cloud,
  DEVICE:         Smartphone,
};

/**
 * AssetRegisterPage — Sprint 5 unified asset register / CMDB-lite.
 *
 * Left rail: filterable asset list grouped by type.
 * Right pane: selected asset's blast radius — what depends on it (downstream),
 * what it depends on (upstream), CCM checks monitoring it, pending approvals
 * waiting on its owner. The single demo that answers ServiceNow's CMDB pitch:
 * "if AWS Riyadh goes down, here's the full impact across modules."
 */
export function AssetRegisterPage() {
  const { t, lang } = useI18n();
  const [selected, setSelected] = useState<string>(ASSETS[0].code);
  const [typeFilter, setTypeFilter] = useState<AssetType | 'ALL'>('ALL');
  const [critFilter, setCritFilter] = useState<AssetCriticality | 'ALL'>('ALL');

  const filtered = useMemo(() =>
    ASSETS.filter(a =>
      (typeFilter === 'ALL' || a.type === typeFilter)
      && (critFilter === 'ALL' || a.criticality === critFilter)
    ),
    [typeFilter, critFilter],
  );

  const selectedAsset = ASSETS.find(a => a.code === selected) ?? ASSETS[0];

  // KPI row
  const kpiTotal = ASSETS.length;
  const kpiCritical = ASSETS.filter(a => a.criticality === 'CRITICAL').length;
  const kpiKsa = ASSETS.filter(a => a.dataResidency === 'KSA').length;
  const kpiCrossBorder = ASSETS.filter(a => a.dataResidency === 'GLOBAL').length;
  const kpiSama = ASSETS.filter(a => a.regulatorScope.includes('SAMA')).length;
  const kpiPdpl = ASSETS.filter(a => a.regulatorScope.includes('PDPL')).length;

  return (
    <Shell title={t('assetsTitle')} sub={t('assetsSub')}>
      <PageHeader
        eyebrow={t('assetsEyebrow')}
        title={t('assetsTitle')}
        sub={t('assetsPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Server}       label={t('assetsKpiTotal')}        value={String(kpiTotal)}        tone="primary" />
        <Kpi Icon={AlertOctagon} label={t('assetsKpiCritical')}      value={String(kpiCritical)}     tone="danger" />
        <Kpi Icon={Cloud}         label={t('assetsKpiKsa')}          value={String(kpiKsa)}          tone="success" />
        <Kpi Icon={Cloud}         label={t('assetsKpiCrossBorder')}  value={String(kpiCrossBorder)}  tone="warning" />
        <Kpi Icon={Landmark}      label={t('assetsKpiSama')}         value={String(kpiSama)}         tone="info" />
        <Kpi Icon={Landmark}      label={t('assetsKpiPdpl')}         value={String(kpiPdpl)}         tone="info" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '320px 1fr', gap: 16 }}>
        {/* ── Asset list ─────────────────────────────────────────── */}
        <Card padded={false}
          title={t('assetsListTitle')}
          Icon={Layers}
          action={<Badge tone="primary">{filtered.length}</Badge>}>
          <div style={{ padding: '8px 14px', borderBottom: '1px solid var(--border)', display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
            <Filter size={11} color="var(--ink-3)" />
            <select value={typeFilter} onChange={e => setTypeFilter(e.target.value as AssetType | 'ALL')}
              style={{ padding: '4px 6px', fontSize: 11, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-2)' }}>
              <option value="ALL">{t('assetsFilterTypeAll')}</option>
              {(Object.keys(TYPE_LABEL) as AssetType[]).map(typ =>
                <option key={typ} value={typ}>{lang === 'ar' ? TYPE_LABEL[typ].ar : TYPE_LABEL[typ].en}</option>
              )}
            </select>
            <select value={critFilter} onChange={e => setCritFilter(e.target.value as AssetCriticality | 'ALL')}
              style={{ padding: '4px 6px', fontSize: 11, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-2)' }}>
              <option value="ALL">{t('assetsFilterCritAll')}</option>
              <option value="CRITICAL">CRITICAL</option>
              <option value="HIGH">HIGH</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="LOW">LOW</option>
            </select>
          </div>

          <div style={{ maxHeight: 660, overflowY: 'auto' }}>
            {filtered.map(a => {
              const Icon = TYPE_ICON[a.type];
              const isSelected = a.code === selected;
              return (
                <button key={a.code}
                  onClick={() => setSelected(a.code)}
                  style={{
                    width: '100%', textAlign: 'start',
                    padding: '10px 14px', display: 'grid', gridTemplateColumns: '24px 1fr auto', gap: 10,
                    alignItems: 'center', cursor: 'pointer', border: 'none',
                    background: isSelected ? 'var(--primary-50)' : 'transparent',
                    borderBottom: '1px solid var(--border)',
                  }}>
                  <Icon size={16} color={isSelected ? 'var(--primary)' : 'var(--ink-3)'} />
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {lang === 'ar' ? a.nameAr : a.nameEn}
                    </div>
                    <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }} className="mono">
                      {a.code}
                    </div>
                  </div>
                  <Badge tone={CRITICALITY_TONE[a.criticality]}>{a.criticality}</Badge>
                </button>
              );
            })}
          </div>
        </Card>

        {/* ── Blast-radius pane ──────────────────────────────────── */}
        <BlastRadiusPane asset={selectedAsset} t={t} lang={lang} />
      </div>
    </Shell>
  );
}

function BlastRadiusPane({ asset, t, lang }: { asset: Asset; t: (k: string) => string; lang: 'en' | 'ar' }) {
  const downstream = useMemo(() => downstreamOf(asset.code), [asset.code]);
  const upstream   = useMemo(() => upstreamOf(asset.code), [asset.code]);
  const transitive = useMemo(() => transitiveBlastRadius(asset.code), [asset.code]);
  const ccm        = useMemo(() => ccmChecksForAsset(asset), [asset]);
  const approvals  = useMemo(() => approvalsForAsset(asset), [asset]);
  const Icon = TYPE_ICON[asset.type];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* ── Asset header ─────────────────────────────────────── */}
      <Card>
        <div style={{ display: 'grid', gridTemplateColumns: '48px 1fr auto', gap: 16, alignItems: 'flex-start' }}>
          <div style={{
            width: 48, height: 48, borderRadius: 'var(--r-sm)',
            background: 'var(--primary-50)', color: 'var(--primary)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Icon size={24} />
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }} className="mono">
              {asset.code}
            </div>
            <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--ink-1)', marginTop: 2 }}>
              {lang === 'ar' ? asset.nameAr : asset.nameEn}
            </div>
            {(asset.descriptionEn || asset.descriptionAr) && (
              <div style={{ fontSize: 12, color: 'var(--ink-2)', marginTop: 6, lineHeight: 1.5 }}>
                {lang === 'ar' ? (asset.descriptionAr ?? asset.descriptionEn) : asset.descriptionEn}
              </div>
            )}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 10 }}>
              <Badge tone="info">{lang === 'ar' ? TYPE_LABEL[asset.type].ar : TYPE_LABEL[asset.type].en}</Badge>
              <Badge tone={CRITICALITY_TONE[asset.criticality]}>{asset.criticality}</Badge>
              <Badge tone={RESIDENCY_TONE[asset.dataResidency]}>{t('assetResidency')} {asset.dataResidency}</Badge>
              {asset.regulatorScope.filter(r => r !== 'NONE').map(r =>
                <Badge key={r} tone="primary">{r}</Badge>)}
              {asset.rtoHours && <Badge tone="warning">RTO {asset.rtoHours}h</Badge>}
              <Badge tone="neutral">{t('assetOwner')} {lang === 'ar' ? asset.ownerNameAr : asset.ownerNameEn}</Badge>
            </div>
          </div>
        </div>
      </Card>

      {/* ── Blast radius KPIs ────────────────────────────────── */}
      <div className="kpi-grid">
        <Kpi Icon={GitBranch}    label={t('assetsBlastUpstream')}    value={String(upstream.length)}   tone="info" />
        <Kpi Icon={GitBranch}    label={t('assetsBlastDownstream')}  value={String(downstream.length)} tone={downstream.length > 0 ? 'warning' : 'success'} />
        <Kpi Icon={AlertOctagon} label={t('assetsBlastTransitive')}  value={String(transitive.size)}    tone={transitive.size > 0 ? 'danger' : 'success'} />
        <Kpi Icon={FileCheck2}   label={t('assetsBlastCcm')}         value={String(ccm.length)}         tone="primary" />
        <Kpi Icon={Activity}     label={t('assetsBlastApprovals')}   value={String(approvals.length)}   tone={approvals.length > 0 ? 'warning' : 'success'} />
      </div>

      {/* ── What depends on this (downstream) ─────────────── */}
      <Card title={t('assetsBlastDownstreamTitle')} Icon={GitBranch}>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
          {t('assetsBlastDownstreamSub')}
        </div>
        {downstream.length === 0
          ? <div style={{ padding: 16, textAlign: 'center', fontSize: 12, color: 'var(--ink-3)' }}>{t('assetsBlastNone')}</div>
          : <div style={{ display: 'flex', flexDirection: 'column' }}>
              {downstream.map(d => {
                const Icon = TYPE_ICON[d.type];
                return (
                  <div key={d.code} style={{
                    display: 'grid', gridTemplateColumns: '24px 1fr auto auto', gap: 10,
                    alignItems: 'center', padding: '8px 0', borderBottom: '1px dashed var(--border)',
                  }}>
                    <Icon size={14} color="var(--ink-3)" />
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                        {lang === 'ar' ? d.nameAr : d.nameEn}
                      </div>
                      <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }} className="mono">{d.code}</div>
                    </div>
                    <Badge tone={CRITICALITY_TONE[d.criticality]}>{d.criticality}</Badge>
                    <ChevronRight size={14} color="var(--ink-3)" />
                  </div>
                );
              })}
            </div>}
      </Card>

      {/* ── What this depends on (upstream) ───────────────── */}
      <Card title={t('assetsBlastUpstreamTitle')} Icon={GitBranch}>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
          {t('assetsBlastUpstreamSub')}
        </div>
        {upstream.length === 0
          ? <div style={{ padding: 16, textAlign: 'center', fontSize: 12, color: 'var(--ink-3)' }}>{t('assetsBlastUpstreamNone')}</div>
          : <div style={{ display: 'flex', flexDirection: 'column' }}>
              {upstream.map(u => {
                const Icon = TYPE_ICON[u.type];
                return (
                  <div key={u.code} style={{
                    display: 'grid', gridTemplateColumns: '24px 1fr auto', gap: 10,
                    alignItems: 'center', padding: '8px 0', borderBottom: '1px dashed var(--border)',
                  }}>
                    <Icon size={14} color="var(--ink-3)" />
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                        {lang === 'ar' ? u.nameAr : u.nameEn}
                      </div>
                      <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }} className="mono">{u.code}</div>
                    </div>
                    <Badge tone={CRITICALITY_TONE[u.criticality]}>{u.criticality}</Badge>
                  </div>
                );
              })}
            </div>}
      </Card>

      {/* ── CCM checks ─────────────────────────────────────── */}
      <Card title={t('assetsBlastCcmTitle')} Icon={FileCheck2}>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
          {t('assetsBlastCcmSub')}
        </div>
        {ccm.length === 0
          ? <div style={{ padding: 16, textAlign: 'center', fontSize: 12, color: 'var(--ink-3)' }}>{t('assetsBlastCcmNone')}</div>
          : <div style={{ display: 'flex', flexDirection: 'column' }}>
              {ccm.map(c => {
                const f = classifyFreshness(c);
                return (
                  <div key={c.id} style={{
                    display: 'grid', gridTemplateColumns: '1fr auto auto', gap: 10,
                    alignItems: 'center', padding: '8px 0', borderBottom: '1px dashed var(--border)',
                  }}>
                    <div>
                      <div style={{ fontSize: 12, color: 'var(--ink-1)' }}>
                        {lang === 'ar' ? c.checkNameAr : c.checkNameEn}
                      </div>
                      <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }} className="mono">
                        {c.controls.map(x => `${x.framework} ${x.code}`).join(' · ')}
                      </div>
                    </div>
                    <Badge tone={RESULT_TONE[c.lastResult]}>{c.lastResult}</Badge>
                    <Badge tone={FRESHNESS_TONE[f]}>{f}</Badge>
                  </div>
                );
              })}
            </div>}
      </Card>

      {/* ── Approvals waiting ──────────────────────────────── */}
      {approvals.length > 0 && (
        <Card title={t('assetsBlastApprovalsTitle')} Icon={Activity}>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {approvals.map(a => (
              <div key={a.id} style={{
                padding: '8px 0', borderBottom: '1px dashed var(--border)',
                display: 'grid', gridTemplateColumns: '1fr auto', gap: 10, alignItems: 'center',
              }}>
                <div>
                  <div style={{ fontSize: 12, fontWeight: 600 }}>{lang === 'ar' ? a.titleAr : a.titleEn}</div>
                  <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{a.module} · {a.sourceRef}</div>
                </div>
                <Badge tone="info">{a.status}</Badge>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}
