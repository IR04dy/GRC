import { Layers, AlertTriangle, MapPin, ShieldCheck, Globe } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { SUPPLY_CHAIN_TIERS, type SupplyChainTier, type TierLink } from './tierMap';

/**
 * Multi-tier supply-chain map — Saudi National Risk Center requirement.
 *
 * Renders one row per critical-service supply chain. Each row visualises
 * the chain Service → Tier-1 vendor → Tier-2 sub-supplier → Tier-N. Inline
 * badges flag concentration risk and foreign-dependency. Single-points-of-
 * failure and mitigations are surfaced inline so the user sees the
 * complete risk picture in one glance.
 */
export function TierMapPanel({ t, lang }: { t: (k: string) => string; lang: string }) {
  // Aggregate stats — feeds the panel header chips.
  const total       = SUPPLY_CHAIN_TIERS.length;
  const withSpof    = SUPPLY_CHAIN_TIERS.filter(s => !!s.spof).length;
  const offshoreTier1 = SUPPLY_CHAIN_TIERS.filter(s => s.chain.some(l => l.tier === 1 && !l.inKingdom)).length;

  const critTone: Record<SupplyChainTier['criticality'], 'danger' | 'warning' | 'info' | 'success'> = {
    CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'success',
  };

  return (
    <Card title={t('scmTierMapTitle') ?? 'Supply Chain Tier Mapping'} Icon={Layers}
      action={<>
        <Badge tone="primary">{total} chains</Badge>
        {withSpof > 0 && <Badge tone="danger">{withSpof} SPOFs</Badge>}
        {offshoreTier1 > 0 && <Badge tone="warning">{offshoreTier1} foreign Tier-1</Badge>}
      </>}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 12 }}>
        {t('scmTierMapSub') ?? 'Saudi National Risk Center + NIST SP 800-161 + ISO 28001 — multi-tier visibility into the supply chain (Tier-1 → Tier-N) so single-points-of-failure, foreign-dependency and concentration risk can be quantified and treated.'}
      </div>

      {/* Legend */}
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 12, fontSize: 10, color: 'var(--ink-3)' }}>
        <span><span style={{ display: 'inline-block', width: 10, height: 10, background: 'var(--primary-700)', borderRadius: '50%', marginInlineEnd: 4 }} /> Service</span>
        <span><span style={{ display: 'inline-block', width: 10, height: 10, background: 'var(--accent-700)', borderRadius: 2, marginInlineEnd: 4 }} /> Tier 1 (direct)</span>
        <span><span style={{ display: 'inline-block', width: 10, height: 10, background: 'var(--cat-2)', borderRadius: 2, marginInlineEnd: 4 }} /> Tier 2 (sub-supplier)</span>
        <span><span style={{ display: 'inline-block', width: 10, height: 10, background: 'var(--ink-3)', borderRadius: 2, marginInlineEnd: 4 }} /> Tier 3 (deep chain)</span>
        <span><Globe size={10} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />In-Kingdom</span>
        <span><AlertTriangle size={10} style={{ marginInlineEnd: 4, verticalAlign: 'middle', color: 'var(--danger-fg)' }} />Concentration / SPOF</span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {SUPPLY_CHAIN_TIERS.map((sc, idx) => (
          <div key={idx} style={{
            border: '1px solid var(--border)',
            borderRadius: 'var(--r-md)',
            background: 'var(--surface-2)',
            padding: 12,
          }}>
            {/* Header row */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--ink-1)' }}>
                  {lang === 'ar' && sc.serviceNameAr ? sc.serviceNameAr : sc.serviceNameEn}
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                  Last reviewed <span className="mono">{sc.lastReviewedOn}</span>
                </div>
              </div>
              <Badge tone={critTone[sc.criticality]}>{sc.criticality}</Badge>
            </div>

            {/* Chain visualisation: service → tier-1 → tier-2 → ... */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, alignItems: 'center' }}>
              <ServiceNode label={lang === 'ar' && sc.serviceNameAr ? sc.serviceNameAr : sc.serviceNameEn} />
              {sc.chain.map((link, i) => (
                <Connector key={i} link={link} />
              ))}
            </div>

            {/* SPOF + mitigations */}
            {(sc.spof || sc.mitigations) && (
              <div style={{ marginTop: 10, padding: 8, borderRadius: 'var(--r-sm)', background: 'var(--surface-3)', fontSize: 11 }}>
                {sc.spof && (
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 6, color: 'var(--danger-fg)', marginBottom: sc.mitigations ? 4 : 0 }}>
                    <AlertTriangle size={11} style={{ flexShrink: 0, marginTop: 1 }} />
                    <span><strong>SPOF:</strong> {sc.spof}</span>
                  </div>
                )}
                {sc.mitigations && (
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 6, color: 'var(--ink-2)' }}>
                    <ShieldCheck size={11} style={{ flexShrink: 0, marginTop: 1, color: 'var(--success-fg)' }} />
                    <span><strong>Mitigation:</strong> {sc.mitigations}</span>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </Card>
  );
}

function ServiceNode({ label }: { label: string }) {
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 5,
      padding: '5px 10px',
      borderRadius: 'var(--r-pill)',
      background: 'var(--primary-700)',
      color: '#fff',
      fontSize: 11, fontWeight: 700,
      maxWidth: 200, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
    }} title={label}>
      ●&nbsp;{label}
    </span>
  );
}

function Connector({ link }: { link: TierLink }) {
  // Pick visual styling per tier
  const tone =
    link.tier === 1 ? { bg: 'var(--accent-50)',  fg: 'var(--accent-700)',  border: 'var(--accent-700)' } :
    link.tier === 2 ? { bg: 'var(--surface-3)',  fg: 'var(--ink-1)',       border: 'var(--cat-2)' } :
                      { bg: 'var(--surface-3)',  fg: 'var(--ink-2)',       border: 'var(--ink-4)' };
  return (
    <>
      <span style={{ color: 'var(--ink-4)', fontSize: 16, lineHeight: 1 }}>→</span>
      <span style={{
        display: 'inline-flex', alignItems: 'center', gap: 4,
        padding: '4px 8px',
        border: `1px solid ${tone.border}`,
        borderRadius: 'var(--r-sm)',
        background: tone.bg,
        color: tone.fg,
        fontSize: 11, fontWeight: 600,
        maxWidth: 220,
      }}
      title={`Tier ${link.tier} · ${link.role} · ${link.country}`}
      >
        <span style={{
          fontFamily: 'var(--font-mono)', fontSize: 9, fontWeight: 700,
          padding: '0 4px', borderRadius: 2,
          background: tone.border, color: '#fff',
        }}>T{link.tier}</span>
        {link.inKingdom && <Globe size={10} style={{ flexShrink: 0 }} aria-label="In-Kingdom" />}
        <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{link.vendor}</span>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 2, fontSize: 9, color: 'var(--ink-3)', borderInlineStart: '1px solid var(--border)', paddingInlineStart: 4 }}>
          <MapPin size={9} />{link.country}
        </span>
        {link.concentration && (
          <AlertTriangle size={11} style={{ color: 'var(--danger-fg)', flexShrink: 0 }} aria-label="Concentration risk" />
        )}
      </span>
    </>
  );
}
