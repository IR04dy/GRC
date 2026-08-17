import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Network, Bug, Activity, ClipboardCheck, Share2,
  ShieldCheck, Stamp, Truck, AlertTriangle, ArrowUpRight, FileText,
} from 'lucide-react';
import { Card } from '../ui/Card';
import { Kpi } from '../ui/Kpi';
import { Badge } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import { listFourthParty, isConcentrationAtLeast, type FourthPartyExposure } from '../../lib/fourthParty';
import { listTpc, isResidualAtLeast, type ThirdPartyCyberAssessment } from '../../lib/thirdPartyCyber';
import { listSrt,                type SupplierResilienceTest } from '../../lib/resilienceTests';
import { listProcessorAssessments, type ProcessorAssessment } from '../../lib/processorAssessments';
import { listDsas,               type DataSharingAgreement } from '../../lib/dataSharing';

/**
 * TprmConsolidatedOverview — gap-analysis enhancement F.
 *
 * A unified view of third-party risk across the six modules that together
 * govern supplier risk:
 *   - Fourth-party exposure register
 *   - Third-party cyber assessment
 *   - Processor due diligence (PDPL Art. 28)
 *   - Data sharing agreements (PDPL Art. 6 + 29)
 *   - Supplier resilience tests (tabletops / failover / exit rehearsals)
 *   - Plus the underlying contract clause + alternate-supplier + exit-plan registers
 *
 * Surfaces top KPIs from each module + a per-vendor risk heat-strip and
 * deep-links into the canonical page for drilling in.
 */
export function TprmConsolidatedOverview() {
  const { t } = useI18n();
  const [fp,   setFp]   = useState<FourthPartyExposure[]>([]);
  const [tpc,  setTpc]  = useState<ThirdPartyCyberAssessment[]>([]);
  const [srt,  setSrt]  = useState<SupplierResilienceTest[]>([]);
  const [proc, setProc] = useState<ProcessorAssessment[]>([]);
  const [dsa,  setDsa]  = useState<DataSharingAgreement[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const [a, b, c, d, e] = await Promise.all([
          listFourthParty(undefined, 0, 200),
          listTpc(undefined, 0, 200),
          listSrt(undefined, 0, 200),
          listProcessorAssessments(undefined, 0, 200),
          listDsas(undefined, 0, 200),
        ]);
        setFp(a.content);  setTpc(b.content);  setSrt(c.content);
        setProc(d.content); setDsa(e.content);
      } finally { setLoading(false); }
    })();
  }, []);

  // ── Roll-ups ─────────────────────────────────────────────────────────────
  // F2-229: a threshold over the declared ladder. `=== 'HIGH'` skipped the CRITICAL row — and this
  // file already ranked the adjacent residual tier with tierRank(), so the idiom was here all along.
  const fpHighConc   = fp.filter(r => isConcentrationAtLeast(r.concentrationRisk, 'HIGH')).length;
  // F2-230 — these were two separate inclusion lists (`HIGH || CRITICAL` and `LOW || MODERATE`) which
  // between them omitted ELEVATED, so on 16 real rows (dev = prod) the two ELEVATED assessments were
  // in NEITHER bucket and the pair did not sum to the register. F2-196's rule on a tier: "elevated"
  // must be a threshold over the declared ladder, and "fine" must be its COMPLEMENT — never a second
  // list, because a second list drops whichever rung its author did not think of.
  //
  // The threshold is ELEVATED, matching ThirdPartyCyberPage's own "high-risk" tab. Those two surfaces
  // previously held DIFFERENT thresholds for one question and each looked self-consistent.
  const tpcHigh      = tpc.filter(r => isResidualAtLeast(r.residualRiskTier, 'ELEVATED')).length;
  const tpcOk        = tpc.length - tpcHigh;
  const procFail     = proc.filter(r => r.overallOutcome === 'FAIL').length;
  const srtFail      = srt.filter(r => r.outcome === 'FAIL').length;
  const srtUpcoming  = srt.filter(r => r.status === 'SCHEDULED').length;
  const dsaActive    = dsa.filter(r => r.status === 'ACTIVE').length;
  const dsaCross     = dsa.filter(r => r.crossBorderMechanism && r.crossBorderMechanism !== 'NONE').length;

  // ── Per-vendor heat strip ────────────────────────────────────────────────
  // Build a list of every vendor that appears in any module, with the
  // worst-case signal across that vendor's records.
  type Row = {
    vendor: string;
    fpEdges: number; fpHighConc: number;
    tpcResidual: string | null;
    procFails: number; procPwc: number;
    srtFail: number; srtNextDue: string | null;
    dsaActive: number;
  };
  const byVendor = new Map<string, Row>();
  function getRow(name: string): Row {
    let r = byVendor.get(name);
    if (!r) { r = { vendor: name, fpEdges: 0, fpHighConc: 0, tpcResidual: null, procFails: 0, procPwc: 0, srtFail: 0, srtNextDue: null, dsaActive: 0 }; byVendor.set(name, r); }
    return r;
  }
  fp.forEach(r => { const row = getRow(r.primaryVendorName); row.fpEdges++; if (isConcentrationAtLeast(r.concentrationRisk, 'HIGH')) row.fpHighConc++; });
  tpc.forEach(r => { const row = getRow(r.vendorName); if (!row.tpcResidual || tierRank(r.residualRiskTier) > tierRank(row.tpcResidual)) row.tpcResidual = r.residualRiskTier; });
  proc.forEach(r => { const row = getRow(r.processorName); if (r.overallOutcome === 'FAIL') row.procFails++; if (r.overallOutcome === 'PASS_WITH_CONDITIONS') row.procPwc++; });
  srt.forEach(r => { const row = getRow(r.vendorName); if (r.outcome === 'FAIL') row.srtFail++; if (r.status === 'SCHEDULED' && r.scheduledFor && (!row.srtNextDue || r.scheduledFor < row.srtNextDue)) row.srtNextDue = r.scheduledFor; });
  dsa.forEach(r => { const row = getRow(r.recipientName); if (r.status === 'ACTIVE') row.dsaActive++; });

  const rows = Array.from(byVendor.values())
    .sort((a, b) => vendorScore(b) - vendorScore(a))
    .slice(0, 12);

  return (
    <>
      <Card title={t('tpoTitle')} Icon={Truck}
        action={<Badge tone="primary">{rows.length}</Badge>}>
        <p style={{ fontSize: 13, color: 'var(--ink-3)', marginBottom: 12 }}>{t('tpoSub')}</p>

        <div className="kpi-grid" style={{ marginBottom: 16 }}>
          <Kpi Icon={Network}       label={t('tpoKpiFp')}        value={loading ? '…' : fp.length}   tone="primary" to="/fourth-party" />
          <Kpi Icon={Bug}            label={t('tpoKpiTpc')}       value={loading ? '…' : tpc.length}  tone="primary" to="/third-party-cyber" />
          <Kpi Icon={ClipboardCheck} label={t('tpoKpiProc')}      value={loading ? '…' : proc.length} tone="primary" to="/processor-assessments" />
          <Kpi Icon={Activity}       label={t('tpoKpiSrt')}       value={loading ? '…' : srt.length}  tone="primary" to="/resilience-tests" />
          <Kpi Icon={Share2}         label={t('tpoKpiDsa')}       value={loading ? '…' : dsa.length}  tone="primary" to="/data-sharing" />
        </div>

        <div className="kpi-grid" style={{ marginBottom: 16 }}>
          <Kpi Icon={AlertTriangle} label={t('tpoKpiHighRisk')}    value={loading ? '…' : fpHighConc + tpcHigh + procFail + srtFail} tone="danger" />
          <Kpi Icon={ShieldCheck}   label={t('tpoKpiHealthy')}     value={loading ? '…' : tpcOk}     tone="success" to="/third-party-cyber" />
          <Kpi Icon={Stamp}         label={t('tpoKpiDsaActive')}   value={loading ? '…' : dsaActive} tone="info" to="/data-sharing" />
          <Kpi Icon={FileText}      label={t('tpoKpiCrossBorder')} value={loading ? '…' : dsaCross}  tone="warning" to="/data-sharing" />
          <Kpi Icon={Activity}      label={t('tpoKpiNextTests')}   value={loading ? '…' : srtUpcoming} tone="warning" to="/resilience-tests" />
        </div>

        {/* Vendor heat strip */}
        <div>
          <h4 style={{ margin: '4px 0 8px', fontSize: 13, fontWeight: 600 }}>{t('tpoHeatTitle')}</h4>
          <table className="grc-table">
            <thead>
              <tr>
                <th>{t('tpoColVendor')}</th>
                <th style={{ textAlign: 'center' }}>{t('tpoCol4P')}</th>
                <th style={{ textAlign: 'center' }}>{t('tpoColCyber')}</th>
                <th style={{ textAlign: 'center' }}>{t('tpoColProc')}</th>
                <th style={{ textAlign: 'center' }}>{t('tpoColResil')}</th>
                <th style={{ textAlign: 'center' }}>{t('tpoColDsa')}</th>
                <th style={{ textAlign: 'right' }}>{t('tpoColScore')}</th>
              </tr>
            </thead>
            <tbody>
              {loading
                ? <tr><td colSpan={7} style={{ textAlign: 'center', color: 'var(--ink-3)', padding: 24 }}>{t('loading')}</td></tr>
                : rows.length === 0
                  ? <tr><td colSpan={7} style={{ textAlign: 'center', color: 'var(--ink-3)', padding: 24 }}>{t('tpoEmpty')}</td></tr>
                  : rows.map(r => (
                    <tr key={r.vendor}>
                      <td><strong>{r.vendor}</strong></td>
                      <td style={{ textAlign: 'center' }}>{r.fpEdges > 0 ? <Badge tone={r.fpHighConc ? 'danger' : 'info'}>{r.fpEdges}{r.fpHighConc ? ' · HC' : ''}</Badge> : <span style={{ color: 'var(--ink-3)' }}>—</span>}</td>
                      <td style={{ textAlign: 'center' }}>{r.tpcResidual ? <Badge tone={tierTone(r.tpcResidual)}>{r.tpcResidual}</Badge> : <span style={{ color: 'var(--ink-3)' }}>—</span>}</td>
                      <td style={{ textAlign: 'center' }}>{r.procFails ? <Badge tone="danger">FAIL × {r.procFails}</Badge> : r.procPwc ? <Badge tone="warning">PwC × {r.procPwc}</Badge> : <span style={{ color: 'var(--ink-3)' }}>—</span>}</td>
                      <td style={{ textAlign: 'center' }}>{r.srtFail ? <Badge tone="danger">FAIL</Badge> : r.srtNextDue ? <Badge tone="info">{r.srtNextDue.slice(0, 10)}</Badge> : <span style={{ color: 'var(--ink-3)' }}>—</span>}</td>
                      <td style={{ textAlign: 'center' }}>{r.dsaActive ? <Badge tone="success">{r.dsaActive}</Badge> : <span style={{ color: 'var(--ink-3)' }}>—</span>}</td>
                      <td style={{ textAlign: 'right' }} className="mono">{vendorScore(r)}</td>
                    </tr>
                  ))}
            </tbody>
          </table>
        </div>

        {/* Quick links */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 14 }}>
          <QuickLink to="/fourth-party"           Icon={Network}        label={t('tpoLinkFp')} />
          <QuickLink to="/third-party-cyber"      Icon={Bug}            label={t('tpoLinkTpc')} />
          <QuickLink to="/processor-assessments"  Icon={ClipboardCheck} label={t('tpoLinkProc')} />
          <QuickLink to="/resilience-tests"       Icon={Activity}       label={t('tpoLinkSrt')} />
          <QuickLink to="/data-sharing"           Icon={Share2}         label={t('tpoLinkDsa')} />
          <QuickLink to="/supplier-resilience"    Icon={ShieldCheck}    label={t('tpoLinkResil')} />
          <QuickLink to="/contract-clauses"       Icon={Stamp}          label={t('tpoLinkClauses')} />
        </div>
      </Card>
    </>
  );
}

// Vendor risk score: rough additive across signals. Used only to sort the
// heat strip so the worst-case vendors bubble to the top.
function vendorScore(r: { fpHighConc: number; tpcResidual: string | null; procFails: number; procPwc: number; srtFail: number; }): number {
  let s = 0;
  s += r.fpHighConc * 10;
  s += tierRank(r.tpcResidual) * 4;
  s += r.procFails * 12;
  s += r.procPwc * 3;
  s += r.srtFail * 8;
  return s;
}
/**
 * Worst-case rank across the modules this strip merges. Deliberately NOT `tpcResidualRank`: this one
 * spans two vocabularies — the residual ladder and TPRM's own MEDIUM-bearing tiers — which is why
 * MEDIUM shares MODERATE's rung. F2-230 left it as its own function for that reason and repointed the
 * five single-vocabulary readers instead; a shared rank that quietly accepted a token from a different
 * ladder would be the drift this finding is about, one level up.
 *
 * ⚠ Unknown ranks 0, not -1, because this feeds a "worst signal per vendor" reduction where a row with
 * no tier must not outrank a real LOW.
 */
function tierRank(t: string | null): number {
  const r: Record<string, number> = { LOW: 0, MODERATE: 1, ELEVATED: 2, HIGH: 3, CRITICAL: 4, MEDIUM: 1 };
  return t ? (r[t] ?? 0) : 0;
}
function tierTone(t: string): 'success' | 'info' | 'warning' | 'danger' | 'primary' {
  if (t === 'LOW')      return 'success';
  if (t === 'MODERATE' || t === 'MEDIUM') return 'info';
  if (t === 'ELEVATED') return 'warning';
  return 'danger';
}

function QuickLink({ to, Icon, label }: { to: string; Icon: typeof Network; label: string }) {
  return (
    <Link to={to} style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      padding: '6px 12px', background: 'var(--surface-2)',
      border: '1px solid var(--border)', borderRadius: 'var(--r-pill)',
      fontSize: 12, color: 'var(--ink-1)', textDecoration: 'none',
    }}>
      <Icon size={14} /> {label} <ArrowUpRight size={12} />
    </Link>
  );
}
