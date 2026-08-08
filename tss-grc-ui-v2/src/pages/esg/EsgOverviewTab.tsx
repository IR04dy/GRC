/**
 * F2-16 tranche 1 — the executive sustainability dashboard, moved out of `EsgPage.tsx`.
 *
 * `EsgPage.tsx` was **5,582 lines**, the largest file in the frontend, and this is the first cut of
 * F2-16's split. The range moved here is `OverviewTab` plus the fourteen widgets it composes and the
 * emissions trend chart — self-contained by measurement: every widget is referenced only from
 * `OverviewTab`'s own JSX, and `OverviewTab` only from the page's tab switch.
 *
 * <b>⚠ The plan's stated reason for splitting this page before `NdiPage.tsx` is FALSE, and it is
 * worth knowing before the next tranche.</b> The row says *"`EsgPage` first, `sust` has ~200 tests
 * to catch mistakes"* — those are BACKEND tests for `module/sust`. Measured 2026-08-08: the only file
 * in the frontend that so much as names `EsgPage.tsx` is `sourceFileSizeRatchet.test.ts`. This page
 * has **no test at all**, so nothing would have caught a mistake in the move. The ordering may still
 * be right (this file is bigger, and `ndi` has 82 write endpoints to characterise first) — but not
 * for the reason given.
 *
 * <b>So the move was proven textually rather than trusted.</b> The extracted range appears here
 * verbatim, byte for byte, with exactly one deliberate change — the `export` keyword on
 * `OverviewTab` — and everything left in `EsgPage.tsx` below its imports is byte-identical to what
 * was there before, minus this range. `noUnusedLocals` then makes `tsc` the checker for the import
 * surgery: an import kept here that is not needed, or dropped there that is, fails the typecheck.
 *
 * <b>The widgets are pure, and now that they have a module they are tested.</b> Fourteen of them take
 * `{ data, isAr }` and hold no state — which is why they were testable the moment they stopped being
 * buried in the middle of a 5,582-line page. See `EsgOverviewTab.test.tsx`.
 */
import { useEffect, useState } from 'react';
import {
  Leaf, TrendingDown, TrendingUp, Target, FileText, 
  CheckCircle2, AlertCircle, 
  CloudLightning, ArrowRight, Wrench, Clock,
  Paperclip, Coins, 
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import {
  getDashboard, esgBandTone, esgBandLabel, esgBandColor, topGapLabel,
  type DashboardPayload,
} from '../../lib/sustainabilityDashboard';
import {
  getSettings, 
  type OrganisationSettings as SustSettings,
} from '../../lib/sustainabilitySettings';
import {
  esgTrajectoryApi, mapTrajectory, 
  type EsgTab, type EsgTrajectoryPoint,
} from '../../lib/esgDashboard';

// ─── Executive Sustainability Dashboard (V64 follow-up — slice 11) ──────────
//
// The 12-widget executive dashboard composed from /api/sustainability/
// dashboard. One read-only round-trip hydrates every widget; widgets
// pull from sub-objects on the payload (cma, tcfd, ghg, sgi, etc.).
//
// Widget grid (responsive auto-fit):
//   Row 1 (hero):  ESG Overall Score gauge | Top Gaps panel
//   Row 2 (GHG):   Total emissions card | Scope breakdown | Verification donut
//   Row 3 (regs):  SGI tier counts | CMA readiness | TCFD alignment
//   Row 4 (ops):   Climate risks | Corrective actions | Evidence completeness
//   Row 5 (close): Annex readiness gauge | Materiality matrix preview
//   Row 6 (trend): Existing emissions timeseries chart (preserved)

// GF-3 — onNavigate is the page's setTab so the horizon KPI tiles can land on
// their owning tabs (targets / ifrs / env).
export function OverviewTab({ onNavigate }: { onNavigate: (t: EsgTab) => void }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [data, setData] = useState<DashboardPayload | null>(null);
  // Slice 14-C — settings drive Rule 14 banner thresholds. Loaded in
  // parallel with the dashboard; soft-fail (null) if the call errors,
  // banner just won't render in that case.
  const [settings, setSettings] = useState<SustSettings | null>(null);
  const [loading, setLoading] = useState(true);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — settings already soft-fail; do the same for the
    // dashboard payload so the page shell still renders.
    const [dash, s] = await Promise.all([
      getDashboard(2025).catch(() => null),
      getSettings().catch(() => null),  // banner is non-critical
    ]);
    setData(dash);
    setSettings(s as SustSettings | null);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  if (loading) {
    return <InlineEmpty>{isAr ? 'جارٍ تحميل لوحة المعلومات…' : 'Loading dashboard…'}</InlineEmpty>;
  }
  if (!data) {
    return <InlineEmpty>{isAr ? 'لا توجد بيانات' : 'No data'}</InlineEmpty>;
  }

  // Rule 14 — show readiness banner when score drops below the
  // configured warning / critical thresholds.
  const score = Number(data.overall.score);
  const warnThr = settings?.esgReadinessWarningThreshold ?? 60;
  const critThr = settings?.esgReadinessCriticalThreshold ?? 40;
  const bannerTone: 'danger' | 'warning' | null =
      score < critThr ? 'danger'
    : score < warnThr ? 'warning'
    : null;

  return (
    <div className="esg-stack">
      {/* Rule 14 — Low ESG-readiness banner */}
      {bannerTone && (
        <div className={`esg-hero-banner ${bannerTone === 'danger' ? 'esg-hero-banner--danger' : 'esg-hero-banner--warning'}`}>
          <AlertCircle size={20} />
          <div className="esg-hero-banner__body">
            <div className="esg-hero-banner__title">
              {bannerTone === 'danger'
                ? (isAr ? 'تحذير حرج — جاهزية الاستدامة منخفضة جداً' : 'Critical — ESG readiness severely below threshold')
                : (isAr ? 'تحذير — جاهزية الاستدامة تحت العتبة' : 'Warning — ESG readiness below threshold')}
            </div>
            <div className="esg-hero-banner__sub">
              {isAr
                ? `الدرجة الحالية ${score}/100 — العتبة المعدّة ${bannerTone === 'danger' ? critThr : warnThr}/100. راجع أبرز الثغرات أدناه.`
                : `Current score ${score}/100 — configured threshold ${bannerTone === 'danger' ? critThr : warnThr}/100. Review top gaps below.`}
            </div>
          </div>
          <Button size="sm" variant="ghost" onClick={() => void reload()} title={isAr ? 'تحديث' : 'Refresh'}>
            <ArrowRight size={14} />
          </Button>
        </div>
      )}

      {/* ── GF-3: horizon KPI strip — the four figures the review found
             missing from the dashboard; each tile lands on its owning tab. */}
      {data.horizon && (
        <div className="kpi-grid">
          <Kpi Icon={Coins} label={isAr ? 'نسبة تخصيص المتحصلات' : 'Proceeds allocation'}
            value={`${Number(data.horizon.allocationRatioPct).toFixed(1)}%`}
            tone={Number(data.horizon.allocationRatioPct) >= 90 ? 'success' : 'info'}
            to="/green-finance" />
          <Kpi Icon={FileText} label={isAr ? 'تغطية IFRS S1/S2' : 'IFRS S1/S2 coverage'}
            value={`${Number(data.horizon.ifrsCoveragePct).toFixed(1)}%`}
            tone={Number(data.horizon.ifrsCoveragePct) >= 80 ? 'success' : 'warning'}
            onClick={() => onNavigate('ifrs')} />
          <Kpi Icon={AlertCircle} label={isAr ? 'تصاريح تنتهي ≤ 90 يوماً' : 'Permits expiring ≤ 90d'}
            value={data.horizon.permitsExpiring90d}
            tone={data.horizon.permitsExpiring90d > 0 ? 'danger' : 'success'}
            onClick={() => onNavigate('env')} />
          <Kpi Icon={Target} label={isAr ? 'مستهدفات على المسار' : 'Targets on track'}
            value={`${data.horizon.targetsOnTrack} / ${data.horizon.targetsOnTrack + data.horizon.targetsOffTrack + data.horizon.targetsNoData}`}
            tone={data.horizon.targetsOffTrack > 0 ? 'warning' : 'success'}
            onClick={() => onNavigate('targets')} />
        </div>
      )}

      {/* ── Row 1: Hero — ESG Overall Score + Top Gaps ────────────── */}
      <div className="esg-grid-2col">
        <EsgOverallScoreWidget data={data} isAr={isAr} />
        <TopGapsWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 2: GHG cluster ───────────────────────────────────── */}
      <div className="esg-grid-auto-fit">
        <GhgTotalWidget data={data} isAr={isAr} />
        <ScopeBreakdownWidget data={data} isAr={isAr} />
        <VerificationStatusWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 3: Regulatory readiness ──────────────────────────── */}
      <div className="esg-grid-auto-fit">
        <SgiProgressWidget data={data} isAr={isAr} />
        <CmaReadinessWidget data={data} isAr={isAr} />
        <TcfdAlignmentWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 4: Operational tracking ──────────────────────────── */}
      <div className="esg-grid-auto-fit">
        <ClimateRiskWidget data={data} isAr={isAr} />
        <CapaTrackerWidget data={data} isAr={isAr} />
        <EvidenceCompletenessWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 5: Annex readiness + Materiality matrix ──────────── */}
      <div className="esg-grid-2col">
        <AnnexReadinessWidget data={data} isAr={isAr} />
        <MaterialityMatrixWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 6: Emissions timeseries trend (preserved chart) ──── */}
      <Card title={isAr ? 'مسار الانبعاثات السنوية' : 'Annual emissions trend'} Icon={TrendingDown}>
        <EmissionsTrendChart isAr={isAr} />
      </Card>
    </div>
  );
}

// ─── Dashboard widgets ──────────────────────────────────────────────────────

function EsgOverallScoreWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const score = Number(data.overall.score);
  const band = data.overall.band;
  const color = esgBandColor(band);
  // SVG ring: arc length proportional to score
  const radius = 64;
  const stroke = 12;
  const circ = 2 * Math.PI * radius;
  const dash = (Math.min(100, Math.max(0, score)) / 100) * circ;

  return (
    <Card title={isAr ? 'مؤشّر الاستدامة الشامل' : 'ESG Overall Score'} Icon={Target}>
      <div className="esg-score-hero">
        <svg width={160} height={160} viewBox="0 0 160 160">
          <circle cx={80} cy={80} r={radius} stroke="var(--surface-2)" strokeWidth={stroke} fill="none" />
          <circle cx={80} cy={80} r={radius} stroke={color} strokeWidth={stroke} fill="none"
                  strokeDasharray={`${dash} ${circ}`} strokeDashoffset={0}
                  strokeLinecap="round" transform="rotate(-90 80 80)" />
          <text x={80} y={78} textAnchor="middle" fontSize="32" fontWeight="800" fill="var(--ink-1)">{score}</text>
          <text x={80} y={100} textAnchor="middle" fontSize="11" fill="var(--ink-3)">/ 100</text>
        </svg>
        <div className="esg-score-hero__main">
          <Badge tone={esgBandTone(band)}>{esgBandLabel(band, isAr ? 'ar' : 'en')}</Badge>
          <div className="esg-score-hero__hint">
            {isAr
              ? 'مؤشّر مركّب موزون (التحقّق من الانبعاثات + التزامات SGI + إفصاحات CMA + TCFD + الإجراءات التصحيحية + الأدلة + المخاطر المناخية).'
              : 'Weighted composite (GHG verification + SGI on-track + CMA + TCFD + corrective actions + evidence + climate risks).'}
          </div>
          <details className="esg-score-hero__details">
            <summary>{isAr ? 'تفاصيل الحساب' : 'Score breakdown'}</summary>
            <table className="esg-score-hero__table">
              <tbody>
                {Object.entries(data.overall.breakdown).map(([k, v]) => (
                  <tr key={k}>
                    <td>{k}</td>
                    <td className="mono esg-text-end">{Number(v).toFixed(1)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </details>
        </div>
      </div>
    </Card>
  );
}

function TopGapsWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  return (
    <Card title={isAr ? 'أبرز الثغرات التنفيذية' : 'Top ESG gaps'} Icon={AlertCircle}>
      {data.topGaps.length === 0 ? (
        <div className="esg-status-banner-success">
          <CheckCircle2 size={14} className="esg-icon-inline" />
          {isAr ? 'لا توجد ثغرات تنفيذية ظاهرة في الدورة الحالية.' : 'No executive-level gaps detected for the current cycle.'}
        </div>
      ) : (
        <ul className="esg-gaps-list">
          {data.topGaps.map(g => (
            <li key={g}>
              <span className={g.includes('OVERDUE') || g.includes('SCOPE_3') || g.includes('RESILIENCE') ? 'esg-gaps-bullet--danger' : 'esg-gaps-bullet--warning'}>●</span>
              {' '}
              {topGapLabel(g, isAr ? 'ar' : 'en')}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function GhgTotalWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const total = Number(data.ghg.grandTotal);
  const yoy = Number(data.ghgYoy.totalYoY);
  return (
    <Card title={isAr ? 'إجمالي الانبعاثات' : 'GHG total emissions'} Icon={Leaf}>
      <div className="esg-score-hero__big">
        {total.toLocaleString()}
      </div>
      <div className="esg-score-hero__unit">tCO2e — {data.ghg.year}</div>
      <div className="esg-score-hero__yoy">
        <span className={yoy < 0 ? 'esg-score-hero__yoy-positive' : yoy > 0 ? 'esg-score-hero__yoy-negative' : 'esg-score-hero__yoy-flat'}>
          {yoy < 0 ? <TrendingDown size={14} className="esg-icon-trailing" /> : <TrendingUp size={14} className="esg-icon-trailing" />}
          {' '}{yoy > 0 ? '+' : ''}{yoy.toFixed(1)}% {isAr ? 'مقارنة بـ' : 'vs'} {data.ghgYoy.year - 1}
        </span>
      </div>
      {data.ghg.scope3PartialFlag && (
        <div className="esg-score-hero__alert">
          <AlertCircle size={11} className="esg-icon-inline" />
          {isAr ? 'تغطية النطاق 3 جزئية' : 'Scope 3 partial coverage'} ({Number(data.ghg.scope3Coverage).toFixed(0)}%)
        </div>
      )}
    </Card>
  );
}

function ScopeBreakdownWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const s1 = Number(data.ghg.scope1Total);
  const s2 = Number(data.ghg.scope2Total);
  const s3 = Number(data.ghg.scope3Total);
  const total = s1 + s2 + s3;
  const rows = [
    { label: isAr ? 'النطاق 1' : 'Scope 1', value: s1, color: '#10b981', yoy: Number(data.ghgYoy.scope1YoY) },
    { label: isAr ? 'النطاق 2' : 'Scope 2', value: s2, color: '#0ea5e9', yoy: Number(data.ghgYoy.scope2YoY) },
    { label: isAr ? 'النطاق 3' : 'Scope 3', value: s3, color: '#f59e0b', yoy: Number(data.ghgYoy.scope3YoY) },
  ];
  return (
    <Card title={isAr ? 'توزيع النطاقات' : 'Scope breakdown'} Icon={Leaf}>
      <div className="stack-sm">
        {rows.map(r => {
          const pct = total > 0 ? (r.value / total) * 100 : 0;
          return (
            <div key={r.label}>
              <div className="esg-progress-row">
                <span className="esg-text-strong">{r.label}</span>
                <span className="mono">{r.value.toLocaleString()} ({pct.toFixed(0)}%)</span>
              </div>
              <div className="esg-progress-track">
                <div className="esg-progress-fill" style={{ width: `${pct}%`, background: r.color }} />
              </div>
              <div className={`text-2xs ${r.yoy < 0 ? 'esg-score-hero__yoy-positive' : 'esg-score-hero__yoy-negative'}`} style={{ marginTop: 2 }}>
                YoY {r.yoy > 0 ? '+' : ''}{r.yoy.toFixed(1)}%
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

function VerificationStatusWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  // Verification rate from the dashboard breakdown
  const verifRate = Number(data.overall.breakdown.ghgVerification);
  return (
    <Card title={isAr ? 'حالة التحقّق' : 'Verification status'} Icon={CheckCircle2}>
      <div className="esg-score-hero">
        <div className="esg-verify-ring">
          <svg viewBox="0 0 100 100" className="esg-verify-ring__svg">
            <circle cx={50} cy={50} r={40} stroke="var(--surface-2)" strokeWidth={12} fill="none" />
            <circle cx={50} cy={50} r={40} stroke="var(--success)" strokeWidth={12} fill="none"
                    strokeDasharray={`${(verifRate / 100) * 2 * Math.PI * 40} ${2 * Math.PI * 40}`}
                    strokeLinecap="round" />
          </svg>
          <div className="esg-verify-ring__center">
            <div className="esg-verify-ring__value">{verifRate.toFixed(0)}%</div>
            <div className="esg-verify-ring__label">{isAr ? 'مُتحقَّق' : 'verified'}</div>
          </div>
        </div>
        <div className="esg-score-hero__main esg-score-hero__hint">
          {isAr
            ? `${data.ghg.recordCount} سجل انبعاثات إجمالاً. التحقّق الخارجي يدعم الإفصاحات الرئيسية.`
            : `${data.ghg.recordCount} GHG records total. External verification underpins the headline disclosures.`}
        </div>
      </div>
    </Card>
  );
}

function SgiProgressWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const onTrack = data.sgi.byTier.ON_TRACK ?? 0;
  const atRisk  = data.sgi.byTier.AT_RISK ?? 0;
  const delayed = data.sgi.byTier.DELAYED ?? 0;
  const total = onTrack + atRisk + delayed;
  return (
    <Card title={isAr ? 'التزامات SGI' : 'SGI commitments'} Icon={Target}>
      <div className="esg-widget-headline esg-widget-headline--mb">
        {onTrack}/{total} <span className="esg-widget-sub-inline">{isAr ? 'على المسار' : 'on track'}</span>
      </div>
      <div className="esg-tile-grid-3">
        <div className="esg-tile esg-tile--success">
          <div className="esg-tile__value-lg esg-tile__value-success">{onTrack}</div>
          <div className="esg-tile__label">{isAr ? 'على المسار' : 'On track'}</div>
        </div>
        <div className="esg-tile esg-tile--warning">
          <div className="esg-tile__value-lg esg-tile__value-warning">{atRisk}</div>
          <div className="esg-tile__label">{isAr ? 'مُعرَّض' : 'At risk'}</div>
        </div>
        <div className="esg-tile esg-tile--danger">
          <div className="esg-tile__value-lg esg-tile__value-danger">{delayed}</div>
          <div className="esg-tile__label">{isAr ? 'متأخّر' : 'Delayed'}</div>
        </div>
      </div>
    </Card>
  );
}

function CmaReadinessWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const pct = data.cma.readinessPct;
  const disclosed = data.cma.byStatus.DISCLOSED ?? 0;
  const partial   = data.cma.byStatus.PARTIAL ?? 0;
  const evidenceReq = data.cma.byStatus.EVIDENCE_REQUIRED ?? 0;
  return (
    <Card title={isAr ? 'جاهزية إفصاحات CMA' : 'CMA disclosure readiness'} Icon={FileText}>
      <div className="esg-widget-headline">{pct}%</div>
      <div className="esg-widget-sub">
        {disclosed}/{data.cma.applicable} {isAr ? 'مُفصَح' : 'disclosed'}
      </div>
      <div className="esg-bar-h8">
        <div className="esg-bar-fill-h8" style={{
          width: `${pct}%`,
          background: pct >= 80 ? 'var(--success)' : pct >= 50 ? 'var(--warning)' : 'var(--danger)',
        }} />
      </div>
      <div className="row-tight">
        <Badge tone="warning">{partial} {isAr ? 'جزئي' : 'Partial'}</Badge>
        {evidenceReq > 0 && <Badge tone="danger">{evidenceReq} {isAr ? 'يتطلّب دليلاً' : 'Evidence req.'}</Badge>}
      </div>
    </Card>
  );
}

function TcfdAlignmentWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const pct = data.tcfd.alignmentPct;
  return (
    <Card title={isAr ? 'محاذاة TCFD' : 'TCFD alignment'} Icon={FileText}>
      <div className="esg-widget-headline">{pct}%</div>
      <div className="esg-widget-sub">
        {data.tcfd.byStatus.DISCLOSED}/{data.tcfd.applicable} {isAr ? 'مُفصَح' : 'disclosed'}
      </div>
      <div className="esg-tile-grid-4" style={{ marginBottom: 8 }}>
        {(['GOVERNANCE', 'STRATEGY', 'RISK_MGMT', 'METRICS_TARGETS'] as const).map(p => {
          const total = data.tcfd.byPillar[p] ?? 0;
          const ok = data.tcfd.disclosedByPillar[p] ?? 0;
          return (
            <div key={p} className="esg-tile esg-tile--surface">
              <div className="esg-tile__value-md">{ok}/{total}</div>
              <div className="esg-tile__label-tiny">
                {p === 'GOVERNANCE' ? (isAr ? 'حوكمة' : 'Gov')
                 : p === 'STRATEGY' ? (isAr ? 'استراتيجية' : 'Strat')
                 : p === 'RISK_MGMT' ? (isAr ? 'مخاطر' : 'Risk')
                 : (isAr ? 'مقاييس' : 'Metrics')}
              </div>
            </div>
          );
        })}
      </div>
      {data.tcfd.strategyResilienceGap && (
        <div className="esg-text-footnote esg-date-due-soon">
          <AlertCircle size={10} className="esg-icon-inline" />
          {isAr ? 'القاعدة 7: ثغرة في صمود الاستراتيجية' : 'Rule 7: Strategy resilience gap'}
        </div>
      )}
    </Card>
  );
}

function ClimateRiskWidget({ isAr }: { data: DashboardPayload; isAr: boolean }) {
  // Lean preview — the full heat-map lives on the climate-risks tab.
  // Dashboard shows the 5×5 grid with placeholder counts.
  return (
    <Card title={isAr ? 'مصفوفة المخاطر المناخية' : 'Climate risk heatmap'} Icon={CloudLightning}>
      <div style={{ position: 'relative', width: '100%', maxWidth: 180, aspectRatio: '1 / 1', margin: '0 auto' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gridTemplateRows: 'repeat(5, 1fr)', gap: 2, width: '100%', height: '100%' }}>
          {Array.from({ length: 25 }, (_, i) => {
            const x = i % 5;
            const y = Math.floor(i / 5);
            const severity = (4 - y) * (x + 1);  // higher right + top = worse
            const bg = severity >= 16 ? 'var(--danger)'
                    : severity >= 9 ? 'var(--warning)'
                    : severity >= 4 ? 'var(--info)' : 'var(--success-bg)';
            return <div key={i} style={{ background: bg, borderRadius: 2 }} />;
          })}
        </div>
      </div>
      <div style={{ fontSize: 10, color: 'var(--ink-3)', textAlign: 'center', marginTop: 8 }}>
        {isAr ? 'الأثر × الاحتمالية' : 'Impact × Likelihood'}
      </div>
    </Card>
  );
}

function CapaTrackerWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  return (
    <Card title={isAr ? 'الإجراءات التصحيحية' : 'Corrective actions'} Icon={Wrench}>
      <div className="esg-widget-headline">{data.capa.activeTotal}</div>
      <div className="esg-widget-sub">{isAr ? 'نشطة' : 'active'}</div>
      <div className="esg-tile-grid-4">
        <div className="esg-tile esg-tile--warning">
          <div className="esg-tile__value-md">{data.capa.open}</div>
          <div className="esg-tile__label">{isAr ? 'مفتوحة' : 'Open'}</div>
        </div>
        <div className="esg-tile esg-tile--info">
          <div className="esg-tile__value-md">{data.capa.inProgress}</div>
          <div className="esg-tile__label">{isAr ? 'تنفيذ' : 'WIP'}</div>
        </div>
        <div className="esg-tile esg-tile--success">
          <div className="esg-tile__value-md">{data.capa.verified}</div>
          <div className="esg-tile__label">{isAr ? 'مُتحقَّق' : 'Verified'}</div>
        </div>
        <div className={`esg-tile ${data.capa.overdue > 0 ? 'esg-tile--danger' : 'esg-tile--surface'}`}>
          <div className={`esg-tile__value-md${data.capa.overdue > 0 ? ' esg-tile__value-danger' : ''}`}>{data.capa.overdue}</div>
          <div className="esg-tile__label">{isAr ? 'متأخّرة' : 'Overdue'}</div>
        </div>
      </div>
    </Card>
  );
}

function EvidenceCompletenessWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const approved = data.evidence.byStatus.APPROVED ?? 0;
  const total = data.evidence.total;
  const pct = total > 0 ? Math.round((approved / total) * 100) : 0;
  return (
    <Card title={isAr ? 'اكتمال الأدلة' : 'Evidence completeness'} Icon={Paperclip}>
      <div className="esg-widget-headline">{pct}%</div>
      <div className="esg-widget-sub">
        {approved}/{total} {isAr ? 'معتمدة' : 'approved'}
      </div>
      <div className="esg-bar-h8">
        <div className="esg-bar-fill-h8" style={{
          width: `${pct}%`,
          background: pct >= 80 ? 'var(--success)' : pct >= 50 ? 'var(--info)' : 'var(--warning)',
        }} />
      </div>
      {data.evidence.expiringSoon30d > 0 && (
        <div className="esg-score-hero__alert">
          <Clock size={11} className="esg-icon-inline" />
          {data.evidence.expiringSoon30d} {isAr ? 'تنتهي خلال 30 يوماً' : 'expiring in 30d'}
        </div>
      )}
    </Card>
  );
}

function AnnexReadinessWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  // Composite — averages CMA readiness + TCFD alignment + evidence approval rate
  const cma = data.cma.readinessPct;
  const tcfd = data.tcfd.alignmentPct;
  const evidence = Number(data.overall.breakdown.evidenceApproved);
  const composite = Math.round((cma + tcfd + evidence) / 3);
  return (
    <Card title={isAr ? 'جاهزية الملحق السنوي' : 'Annex readiness gauge'} Icon={FileText}>
      <div className="esg-gauge-wrap">
        <svg viewBox="0 0 200 110" className="esg-gauge-svg">
          {/* Semi-circle gauge */}
          <path d="M 20 100 A 80 80 0 0 1 180 100" stroke="var(--surface-2)" strokeWidth={14} fill="none" />
          <path d={`M 20 100 A 80 80 0 0 1 ${20 + (160 * composite / 100)} ${100 - Math.sin(Math.PI * composite / 100) * 80}`}
                stroke={composite >= 80 ? 'var(--success)' : composite >= 50 ? 'var(--warning)' : 'var(--danger)'}
                strokeWidth={14} fill="none" strokeLinecap="round" />
          <text x={100} y={88} textAnchor="middle" fontSize="28" fontWeight="800" fill="var(--ink-1)">{composite}%</text>
        </svg>
        <div className="esg-gauge-sub">
          {isAr ? 'المتوسّط الموزون لـ CMA + TCFD + الأدلة' : 'Weighted mean of CMA + TCFD + Evidence'}
        </div>
      </div>
    </Card>
  );
}

function MaterialityMatrixWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const matrix = data.materiality.matrix;
  return (
    <Card title={isAr ? 'الأهمية النسبية — أهم الموضوعات' : 'Materiality — top topics'} Icon={Target}>
      <div style={{ display: 'flex', gap: 8, marginBottom: 8, fontSize: 11 }}>
        <Badge tone="danger">{data.materiality.criticalCount} {isAr ? 'حرج' : 'Critical'}</Badge>
        <Badge tone="warning">{data.materiality.highCount} {isAr ? 'مرتفع' : 'High'}</Badge>
      </div>
      {matrix.slice(0, 4).map(t => (
        <div key={t.id} style={{
          padding: 8, marginBottom: 6, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
          display: 'grid', gridTemplateColumns: '1fr auto', gap: 8,
        }}>
          <div className="esg-text-strong">
            {isAr && t.topicAr ? t.topicAr : t.topicEn}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11 }}>
            <span className="mono esg-text-bold">{Number(t.materialityScore).toFixed(1)}</span>
            <span style={{
              width: 10, height: 10, borderRadius: '50%',
              background: t.priorityTier === 'CRITICAL' ? 'var(--danger)'
                       : t.priorityTier === 'HIGH'    ? 'var(--warning)'
                       : t.priorityTier === 'MEDIUM'  ? 'var(--info)' : 'var(--ink-3)',
            }} />
          </div>
        </div>
      ))}
      {matrix.length === 0 && (
        <div style={{ fontSize: 11, color: 'var(--ink-3)', textAlign: 'center', padding: 12 }}>
          {isAr ? 'لا توجد موضوعات ذات أهمية نسبية مُقيَّمة بعد' : 'No materiality topics scored yet'}
        </div>
      )}
    </Card>
  );
}

function EmissionsTrendChart({ isAr }: { isAr: boolean }) {
  // S-P — live multi-year trajectory from /ghg/trajectory (was the
  // EMISSIONS_TIMESERIES mock). Only years with real records are plotted.
  const [series, setSeries] = useState<EsgTrajectoryPoint[]>([]);
  const [loaded, setLoaded] = useState(false);
  useEffect(() => {
    esgTrajectoryApi.get(2020, 2025)
      .then(r => setSeries(mapTrajectory(r).filter(p => p.scope1 + p.scope2 + p.scope3 > 0)))
      .catch(() => setSeries([]))
      .finally(() => setLoaded(true));
  }, []);
  if (loaded && series.length === 0) {
    return (
      <div className="esg-text-eyebrow-sm">
        {isAr ? 'لا توجد بيانات انبعاثات مسجّلة بعد.' : 'No emissions data recorded yet.'}
      </div>
    );
  }
  const maxTotal = Math.max(1, ...series.map(r => r.scope1 + r.scope2 + r.scope3));
  return (
    <>
      <div className="esg-text-eyebrow-sm">
        {isAr ? 'الانبعاثات الإجمالية مكدّسة بالنطاقات عبر السنوات.' : 'Total emissions stacked by scope across years.'}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.max(1, series.length)}, 1fr)`, gap: 12, alignItems: 'end', height: 220 }}>
        {series.map(r => {
          const total = r.scope1 + r.scope2 + r.scope3;
          const heightPct = (total / maxTotal) * 100;
          const s1Pct = (r.scope1 / total) * 100;
          const s2Pct = (r.scope2 / total) * 100;
          const s3Pct = (r.scope3 / total) * 100;
          return (
            <div key={r.year} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, height: '100%' }}>
              <div style={{ flex: 1, width: '100%', display: 'flex', flexDirection: 'column-reverse', alignItems: 'center' }}>
                <div style={{ width: '60%', height: `${heightPct}%`, display: 'flex', flexDirection: 'column', borderRadius: '4px 4px 0 0', overflow: 'hidden', boxShadow: '0 1px 0 rgba(0,0,0,0.08)' }}>
                  <div style={{ flex: s3Pct, background: '#f59e0b', minHeight: 2 }} title={`Scope 3: ${r.scope3.toLocaleString()}`} />
                  <div style={{ flex: s2Pct, background: '#0ea5e9', minHeight: 2 }} title={`Scope 2: ${r.scope2.toLocaleString()}`} />
                  <div style={{ flex: s1Pct, background: '#10b981', minHeight: 2 }} title={`Scope 1: ${r.scope1.toLocaleString()}`} />
                </div>
              </div>
              <div style={{ fontSize: 11, fontWeight: 700 }}>{r.year}</div>
              <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{(total / 1000).toFixed(0)}k</div>
            </div>
          );
        })}
      </div>
      <div style={{ display: 'flex', gap: 12, marginTop: 16, fontSize: 11, color: 'var(--ink-3)' }}>
        <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#10b981', borderRadius: 2, verticalAlign: 'middle', marginInlineEnd: 4 }} />{isAr ? 'النطاق 1' : 'Scope 1'}</span>
        <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#0ea5e9', borderRadius: 2, verticalAlign: 'middle', marginInlineEnd: 4 }} />{isAr ? 'النطاق 2' : 'Scope 2'}</span>
        <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#f59e0b', borderRadius: 2, verticalAlign: 'middle', marginInlineEnd: 4 }} />{isAr ? 'النطاق 3' : 'Scope 3'}</span>
      </div>
    </>
  );
}
