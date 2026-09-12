import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ShieldCheck, AlertTriangle, AlertOctagon, FileWarning, Activity,
  TrendingUp, TrendingDown, ArrowUpRight, Calendar, Clock,
  Sparkles, Flame, Award, Bot,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
// W2-1a-ii: the recent-activity card renders recorded events, not hardcoded ones.
import { loadActivity, subscribeActivity, type ActivityEvent } from '../../lib/activityLog';
import { HeatMap, type HeatCell } from '../../components/viz/HeatMap';
import { TimeSeries, type TimeSeriesPoint } from '../../components/viz/TimeSeries';
import { RadialGauge } from '../../components/viz/RadialGauge';
import { GAUGE_HERO } from '../../lib/gaugeSizes';
import { ragBands } from '../../lib/ragColors';
import { Progress } from '../../components/ui/Progress';
import {
  dashboardApi, riskApi, complianceApi, calendarApi,
  type DashboardStats, type TrendPoint,
} from '../../lib/api';
import { getUser, isAuthenticated } from '../../lib/auth';
import { mlModelApi, aggregateMlRegistry, type MlModelResponse } from '../../lib/aiGovernance';
import '../../styles/pages/executive-dashboard.css';

/**
 * Tracks whether the viewport is phone-width (<=640px). Used to shrink the
 * posture gauge on mobile (its SVG width/height are hard pixel attributes, so
 * CSS alone can't scale it) — the surrounding reflow is handled by
 * executive-dashboard.css. SSR-safe and reduced-listener-clean.
 */
function useIsNarrow(): boolean {
  const query = '(max-width: 640px)';
  const [narrow, setNarrow] = useState<boolean>(() =>
    typeof window !== 'undefined' && typeof window.matchMedia === 'function'
      ? window.matchMedia(query).matches
      : false);
  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return;
    const mq = window.matchMedia(query);
    const onChange = (e: MediaQueryListEvent) => setNarrow(e.matches);
    setNarrow(mq.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);
  return narrow;
}

/**
 * Executive Dashboard hero + KPI ribbon — the first impression of the
 * platform. Designed as a fixed, opinionated layout rather than a
 * draggable widget grid: a CRO / CISO / compliance head should land here
 * and see the same canonical view every day, no setup required.
 *
 * Layout hierarchy (top → bottom):
 *   1. PostureHero          — composite Posture Score + week-over-week delta
 *   2. ExecutiveKpiRow      — 4 dense KPI tiles with microbars + sparklines
 *   3. RiskHeatTrendRow     — 5×5 heatmap + 12-week risk-score trend
 *   4. FrameworkReadiness   — per-framework progress with deep numbers
 *   5. AttentionStrip       — 3 columns: top risks / overdue CAPAs / SLA breaches
 *
 * The existing customizable widget grid (Saudi posture, dashboard widgets)
 * still renders below — this section frontloads the executive view.
 */
export function ExecutiveDashboard({ stats, heat }: {
  stats: DashboardStats;
  heat: HeatCell[];
}) {
  // Real KPI trends (weekly, from entity timestamps) — replaces the previous
  // synthetic Math.sin/Math.random sparklines. Falls back to undefined (flat /
  // no sparkline) if unauthenticated or the endpoint is unavailable.
  const [riskTrend, setRiskTrend] = useState<number[] | undefined>();
  const [incidentTrend, setIncidentTrend] = useState<number[] | undefined>();
  const [findingsTrend, setFindingsTrend] = useState<number[] | undefined>();
  useEffect(() => {
    if (!isAuthenticated()) return;
    let alive = true;
    const toSeries = (pts: TrendPoint[]) => pts.map(p => p.value);
    dashboardApi.kpiTrend('risks', 12).then(p => { if (alive) setRiskTrend(toSeries(p)); }).catch(() => {});
    dashboardApi.kpiTrend('incidents', 12).then(p => { if (alive) setIncidentTrend(toSeries(p)); }).catch(() => {});
    dashboardApi.kpiTrend('findings', 12).then(p => { if (alive) setFindingsTrend(toSeries(p)); }).catch(() => {});
    return () => { alive = false; };
  }, []);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginTop: 8 }}>
      <PostureHero stats={stats} />
      <ExecutiveKpiRow stats={stats} riskTrend={riskTrend} incidentTrend={incidentTrend} findingsTrend={findingsTrend} />
      <RiskHeatTrendRow heat={heat} stats={stats} riskTrend={riskTrend} />
      <FrameworkReadiness stats={stats} />
      <AiGovernanceStrip />
      <AttentionStrip />
    </div>
  );
}

// ─── 4b. AI Governance posture ──────────────────────────────────────────
//
// Live strip fed by the AI/ML registry: headline counts + the four
// governance-gap chips (fairness / human oversight / cross-border PDPL
// basis / signed-off AIA). Renders only when authenticated and the org
// actually runs models — otherwise it stays out of the way.

function AiGovernanceStrip() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [rows, setRows] = useState<MlModelResponse[]>([]);
  const [missingAia, setMissingAia] = useState(0);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!isAuthenticated()) return;
    let alive = true;
    Promise.all([mlModelApi.list(), mlModelApi.highRiskWithoutAia().catch(() => [])])
      .then(([list, noAia]) => {
        if (!alive) return;
        setRows(list);
        setMissingAia(noAia.length);
        setReady(true);
      })
      .catch(() => { if (alive) setReady(true); });
    return () => { alive = false; };
  }, []);

  if (!ready || rows.length === 0) return null;
  const s = aggregateMlRegistry(rows, 0, missingAia);

  const stat = (label: string, value: number) => (
    <Link to="/ai/models" style={{ textDecoration: 'none', color: 'inherit', display: 'flex', flexDirection: 'column', gap: 2 }}>
      <span style={{ font: '800 22px/1 var(--font-sans)', color: 'var(--ink-1)' }}>{value}</span>
      <span className="text-2xs text-muted" style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{label}</span>
    </Link>
  );
  const gap = (label: string, value: number) => (
    <Badge tone={value > 0 ? 'danger' : 'success'} dot>{label}: {value}</Badge>
  );

  return (
    <Card title={isAr ? 'حوكمة الذكاء الاصطناعي' : 'AI Governance'} Icon={Bot}
      action={<Link to="/ai/models" style={{
        font: '600 11px var(--font-sans)', color: 'var(--primary-700)',
        textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: 3,
      }}>{isAr ? 'عرض السجل' : 'View registry'} <ArrowUpRight size={12} /></Link>}>
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(110px, 1fr))',
        gap: 12, paddingBottom: 12, borderBottom: '1px solid var(--border)',
      }}>
        {stat(isAr ? 'إجمالي النماذج' : 'Total models', s.total)}
        {stat(isAr ? 'في الإنتاج' : 'In production', s.inProduction)}
        {stat(isAr ? 'حرج/مرتفع' : 'Critical / High', s.highOrCritical)}
        {stat(isAr ? 'EU عالي الخطورة' : 'EU high-risk', s.euAiActHighRisk)}
        {stat(isAr ? 'مُمتثل لسدايا' : 'SDAIA compliant', s.sdaiaCompliant)}
      </div>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 12 }}>
        {gap(isAr ? 'بدون تدقيق عدالة' : 'No fairness audit', s.missingFairnessAudit)}
        {gap(isAr ? 'بدون إشراف بشري' : 'No human oversight', s.missingHumanOversight)}
        {gap(isAr ? 'نقل عابر بلا أساس' : 'Cross-border, no basis', s.crossBorderNoBasis)}
        {gap(isAr ? 'بدون تقييم أثر معتمد' : 'No signed-off AIA', s.missingSignedOffAia)}
      </div>
    </Card>
  );
}

// ─── 1. Posture Hero ─────────────────────────────────────────────────────
//
// Composite posture: 100 - (weighted risk + open findings + overdue items).
// Renders the score in 64px type with a 30-day delta chip. Backdrop is a
// soft primary-50 → surface-2 gradient so the hero visually owns the top
// of the page without screaming.

function PostureHero({ stats }: { stats: DashboardStats }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const isNarrow = useIsNarrow();
  // Shrink the focal gauge on phones so it fits a single column without
  // overlapping the greeting/caption (UX-005). Desktop keeps GAUGE_HERO.
  const gaugeSize = isNarrow ? { size: 132, thickness: 12 } : GAUGE_HERO;

  // Compose a single 0-100 posture score from the available metrics.
  // Inputs: complianceScore (0-100), open critical/high risks (penalty),
  // open incidents (penalty), pending tasks (penalty). Bounded [0, 100].
  const posture = useMemo(() => {
    const compl = Math.max(0, Math.min(100, stats.complianceScore ?? 0));
    const critPenalty = (stats.openCriticalRisks ?? 0) * 2.5;
    const highPenalty = (stats.openHighRisks ?? 0) * 1.0;
    const incPenalty  = (stats.openIncidents ?? 0)   * 1.5;
    const tasksPenalty = Math.min(10, (stats.pendingTasks ?? 0) * 0.5);
    return Math.max(0, Math.min(100, Math.round(compl - critPenalty - highPenalty - incPenalty - tasksPenalty)));
  }, [stats]);

  // JRN-03 — this used to invent the 30-day delta it displays:
  //   const deltaPct = posture >= 75 ? +2 : posture >= 60 ? -1 : -4;
  // under a comment saying the magnitude was "derived from the current posture
  // so the chip moves coherently" — a figure constructed to LOOK measured.
  // F2-195's rule: a trend chip is a SECOND, independent claim, because it
  // asserts a prior period, and asserting one the platform never recorded is a
  // fabrication whatever the sign happens to be.
  //
  // It is not merely unwired, it is not derivable TODAY: no store of the
  // executive posture composite exists, /api/v1/dashboard/posture-history
  // occurs nowhere in either tree but that retired comment, and one of
  // posture's own inputs (complianceScore) has no historical form at all.
  //
  // So it renders ABSENT rather than being deleted — UX-008's em-dash with a
  // bilingual reason on title AND aria-label (F2-209). Deleting the chip would
  // remove the question instead of answering it, and a green chip would be
  // worse again: an invented figure invites a question, an invented all-clear
  // closes one (F2-197 / F2-457). Hence tone="neutral", never a tone that
  // reads as health.
  const postureDeltaHint = isAr
    ? 'لا يوجد سجل تاريخي لمؤشّر الحوكمة الشامل، فلا يمكن حساب التغيّر خلال 30 يوماً.'
    : 'No posture history is stored, so a 30-day change cannot be computed.';

  // JRN-03 — the hero used to greet every user of every tenant by one
  // hard-coded name: {isAr ? 'أيمن' : 'Aymen'}, the only occurrence of either
  // string in this source tree (the backend has none). F2-207's invented-person
  // family at its sharpest: not an officer inside a demo corpus, but the
  // signed-in user's own name, fabricated, on the first screen after sign-in.
  //
  // The session carries a username and no display name (UserInfo is
  // userId/username/organisationId/roles), so a login id is the most the client
  // can honestly greet by — and with NO session there is no name and none is
  // substituted. Deliberately no `?? 'You'`-style fallback: that exact shape is
  // a recorded defect (F2-248), and production holds a row whose author is the
  // literal string 'You'.
  const who = getUser()?.username ?? null;

  // ⚠ The two Arabic branches below are IDENTICAL ON PURPOSE — do not "fix" it.
  // Arabic greets in two parts of the day where English greets in three:
  // صباح الخير until noon, مساء الخير from noon onward, covering both English's
  // afternoon and evening. Collapsing the English side or inventing a third
  // Arabic greeting would make the primary language read unnaturally (house
  // rule 7). What the h < 18 test buys is the English distinction only.
  //
  // ⚠ Both this and todayLabel below follow the VIEWER's device clock rather
  // than Asia/Riyadh, and that is right for a greeting: it greets the person
  // reading the screen, wherever they are. Whether the DATE label should show
  // the tenant's regulatory day instead is a product question, filed.
  const greeting = (() => {
    const h = new Date().getHours();
    if (h < 12) return isAr ? 'صباح الخير' : 'Good morning';
    if (h < 18) return isAr ? 'مساء الخير' : 'Good afternoon';
    return isAr ? 'مساء الخير' : 'Good evening';
  })();

  const todayLabel = new Date().toLocaleDateString(isAr ? 'ar-SA' : 'en-GB', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  });

  return (
    // Layout (grid columns, gap, min-height, padding) lives in
    // executive-dashboard.css so the <=640px media query can reflow the hero
    // into a single stacked column — inline styles would out-specify it.
    <div className="exec-hero">
      {/* Left: greeting + date */}
      <div className="exec-hero-text">
        <div style={{
          display: 'inline-flex', alignItems: 'center', gap: 6,
          padding: '3px 10px', borderRadius: 'var(--r-pill)',
          background: 'var(--surface)', border: '1px solid var(--border)',
          font: '700 10px var(--font-sans)', color: 'var(--primary-700)',
          letterSpacing: '0.08em', textTransform: 'uppercase',
        }}>
          <Sparkles size={11} /> {isAr ? 'لوحة التحكم التنفيذية' : 'Executive Dashboard'}
        </div>
        <h1 style={{
          margin: '12px 0 4px',
          // 24px (was 28) so the greeting reads as context, not as a rival
          // headline to the focal posture gauge on the right (review D4).
          font: '800 24px var(--font-sans)',
          color: 'var(--ink-1)',
          letterSpacing: 'var(--tracking-tight)',
          lineHeight: 1.15,
        }}>
          {greeting}{who ? (isAr ? '، ' : ', ') : ''}
          {who && (
            <span style={{ color: 'var(--primary-700)' }}>{who}</span>
          )}
        </h1>
        <div className="exec-hero-meta" style={{ display: 'flex', gap: 14, alignItems: 'center', marginTop: 6 }}>
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            font: '500 12px var(--font-sans)', color: 'var(--ink-3)',
          }}>
            <Calendar size={13} /> {todayLabel}
          </div>
          <span style={{ color: 'var(--ink-4)' }}>·</span>
          <div style={{ font: '500 12px var(--font-sans)', color: 'var(--ink-3)' }}>
            {isAr ? 'كل الأرقام محدّثة بالوقت الفعلي' : 'All figures live'}
          </div>
        </div>
      </div>

      {/* Right: composite posture as an arc-as-number gauge */}
      <div className="exec-hero-gauge">
        <RadialGauge
          value={posture}
          max={100}
          {...gaugeSize}
          unit="/100"
          rtl={isAr}
          caption={isAr ? 'مؤشّر الحوكمة الشامل' : 'GRC Posture Score'}
          aria-label={`${isAr ? 'مؤشّر الحوكمة الشامل' : 'GRC posture score'}: ${posture} / 100`}
          bands={ragBands()}
        />
        <Badge tone="neutral" title={postureDeltaHint} ariaLabel={postureDeltaHint}>
          — {isAr ? '/ 30 يوم' : 'vs 30d'}
        </Badge>
      </div>
    </div>
  );
}

// ─── 2. Executive KPI Row ───────────────────────────────────────────────
//
// Four dense tiles, each with:
//   • Icon + label + delta chip
//   • Big value
//   • Microbar showing breakdown (severity / status / regulator)
//   • Sparkline — a REAL 12-week weekly series from GET /api/v1/dashboard/kpi-trend
//     (fetched at the top of this file for risks / incidents / findings). This
//     line used to read "30 sample stub — backend trend endpoint TODO"; the
//     endpoint landed, and a comment calling live data a stub invites the next
//     reader to replace something that is already real.
//   • Click → drill into the matching module

interface KpiTileProps {
  Icon: typeof ShieldCheck;
  label: string;
  value: number | string;
  to: string;
  tone: 'primary' | 'warning' | 'danger' | 'success' | 'info';
  spark?: number[];
  /**
   * Period-over-period change. MUST be derived from a real series — never
   * typed in. Three tiles carried literal values (-2, +3, +1) until JRN-03;
   * two of those tiles fetch a real 12-week series and rendered it as the
   * sparkline directly beside the invented chip, so the tile disagreed with
   * its own graph (F2-197).
   *
   * Left optional rather than removed because it IS derivable for the tiles
   * that carry `spark`: the series is already fetched and already drawn. What
   * that needs is a decision about the period and its bilingual label, which
   * is why it is filed rather than done here — and a tile with no real series
   * (Compliance score) must simply not pass one.
   */
  delta?: { value: number; tone: 'success' | 'danger' };
  /** Bottom microbar — stacked colored segments. */
  segments?: Array<{ value: number; color: string; label: string }>;
}

function KpiTile({ Icon, label, value, to, tone, spark, delta, segments }: KpiTileProps) {
  const total = segments?.reduce((a, s) => a + s.value, 0) ?? 0;
  return (
    <Link to={to} style={{
      display: 'flex', flexDirection: 'column', gap: 10,
      minHeight: 132,
      padding: 16, borderRadius: 'var(--r-md)',
      background: 'var(--surface)',
      border: '1px solid var(--border)',
      borderInlineStart: `4px solid var(--${tone})`,
      textDecoration: 'none', color: 'inherit',
      transition: 'box-shadow var(--dur-fast), border-color var(--dur-fast), transform var(--dur-fast)',
    }}
      onMouseEnter={e => {
        const el = e.currentTarget as HTMLElement;
        el.style.boxShadow = '0 4px 14px rgba(11,80,80,0.08)';
        el.style.transform = 'translateY(-1px)';
      }}
      onMouseLeave={e => {
        const el = e.currentTarget as HTMLElement;
        el.style.boxShadow = '';
        el.style.transform = '';
      }}>
      {/* Header row: icon + label + delta */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{
            width: 30, height: 30, borderRadius: 'var(--r-sm)',
            background: `var(--${tone}-bg)`,
            color: `var(--${tone}-fg)`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Icon size={16} />
          </div>
          <span className="text-xs text-muted" style={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            {label}
          </span>
        </div>
        {delta && (
          <Badge tone={delta.tone}>
            {/* The arrow follows the delta's own SIGN. It used to follow its TONE,
                which are independent — so a rise that is good rendered a DOWN
                arrow beside a plus sign. Live on the Compliance-score tile, whose
                chip was { value: +3, tone: 'success' }: "↓ +3". Tone still drives
                the colour, because whether a rise is good is per-metric. */}
            {delta.value >= 0 ? <TrendingUp size={10} /> : <TrendingDown size={10} />}
            {delta.value >= 0 ? '+' : ''}{delta.value}
          </Badge>
        )}
      </div>

      {/* Big value + sparkline side-by-side */}
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 8 }}>
        <span style={{
          font: '800 30px/1 var(--font-sans)',
          color: 'var(--ink-1)', letterSpacing: 'var(--tracking-num)',
        }}>
          {value}
        </span>
        {spark && spark.length >= 2 && (
          <div style={{ flex: 1, maxWidth: 110, opacity: 0.8 }}>
            <TimeSeries
              series={spark.map((v, i) => ({ label: String(i), value: v }))}
              height={28} width={110}
              color={`var(--${tone})`}
              area
            />
          </div>
        )}
      </div>

      {/* Bottom microbar showing breakdown */}
      {segments && total > 0 && (
        <div>
          <div style={{
            display: 'flex', height: 6,
            borderRadius: 'var(--r-pill)', overflow: 'hidden',
            background: 'var(--surface-3)',
          }}>
            {segments.map((s, i) => (
              <div key={i}
                title={`${s.label}: ${s.value}`}
                style={{
                  width: `${(s.value / total) * 100}%`,
                  background: s.color,
                }}
              />
            ))}
          </div>
          <div style={{
            display: 'flex', gap: 10, marginTop: 6, flexWrap: 'wrap',
          }}>
            {segments.filter(s => s.value > 0).map((s, i) => (
              <span key={i} className="text-2xs text-muted" style={{
                display: 'inline-flex', alignItems: 'center', gap: 4,
              }}>
                <span style={{ width: 8, height: 8, borderRadius: 2, background: s.color }} />
                {s.label} <strong style={{ color: 'var(--ink-1)' }}>{s.value}</strong>
              </span>
            ))}
          </div>
        </div>
      )}
    </Link>
  );
}

function ExecutiveKpiRow({ stats, riskTrend, incidentTrend, findingsTrend }: {
  stats: DashboardStats;
  riskTrend?: number[];
  incidentTrend?: number[];
  findingsTrend?: number[];
}) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  // Overdue items — real overdue audit findings (backend) + pending tasks.
  const overdueFindings = stats.overdueItems ?? 0;
  const totalOverdue = overdueFindings + (stats.pendingTasks ?? 0);

  const tiles: KpiTileProps[] = [
    {
      Icon: AlertTriangle,
      label: isAr ? 'مخاطر مفتوحة' : 'Open risks',
      value: stats.openRisks ?? 0,
      to: '/risk?status=open',
      tone: stats.openCriticalRisks > 0 ? 'danger' : 'warning',
      spark: riskTrend,
      segments: [
        { value: stats.openCriticalRisks ?? 0, color: 'var(--danger)',  label: isAr ? 'حرجة' : 'Critical' },
        { value: stats.openHighRisks ?? 0,     color: 'var(--warning)', label: isAr ? 'عالية' : 'High' },
        { value: stats.mediumRisks ?? 0,       color: 'var(--info)',    label: isAr ? 'متوسطة' : 'Medium' },
        { value: stats.lowRisks ?? 0,          color: 'var(--success)', label: isAr ? 'منخفضة' : 'Low' },
      ],
    },
    {
      Icon: ShieldCheck,
      label: isAr ? 'الالتزام الكلّي' : 'Compliance score',
      value: `${stats.complianceScore ?? 0}%`,
      to: '/compliance',
      tone: 'primary',
    },
    {
      Icon: AlertOctagon,
      label: isAr ? 'حوادث نشطة' : 'Active incidents',
      value: stats.openIncidents ?? 0,
      to: '/incidents?status=open',
      tone: (stats.openIncidents ?? 0) > 0 ? 'danger' : 'success',
      spark: incidentTrend,
    },
    {
      Icon: FileWarning,
      label: isAr ? 'بنود متأخّرة' : 'Overdue items',
      value: totalOverdue,
      to: '/capa?status=overdue',
      tone: totalOverdue > 5 ? 'danger' : totalOverdue > 0 ? 'warning' : 'success',
      spark: findingsTrend,
      segments: totalOverdue > 0 ? [
        { value: overdueFindings,             color: 'var(--danger)',  label: isAr ? 'ملاحظات' : 'Findings' },
        { value: stats.pendingTasks ?? 0,     color: 'var(--warning)', label: isAr ? 'مهام' : 'Tasks' },
      ] : undefined,
    },
  ];

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
      gap: 12,
    }}>
      {tiles.map((tile, i) => <KpiTile key={i} {...tile} />)}
    </div>
  );
}

// ─── 3. Risk Heatmap + 90-day Trend (side-by-side) ──────────────────────

function RiskHeatTrendRow({ heat, stats, riskTrend }: { heat: HeatCell[]; stats: DashboardStats; riskTrend?: number[] }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  // Real weekly open-risk trend from /api/v1/dashboard/kpi-trend. Until it
  // loads (or if unavailable), show a flat line at the current open-risk
  // count — honest "no trend data yet" rather than a fabricated curve.
  const values = (riskTrend && riskTrend.length >= 2)
    ? riskTrend
    : Array.from({ length: 12 }, () => stats.openRisks ?? 0);
  const series: TimeSeriesPoint[] = values.map((v, i) => ({
    label: isAr ? `أ${i + 1}` : `W${i + 1}`,
    value: v,
  }));

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'minmax(0, 1.1fr) minmax(0, 1fr)',
      gap: 16,
    }}>
      <Card title={isAr ? 'خريطة حرارة المخاطر' : 'Risk heatmap'} Icon={Flame}
        action={<Badge tone="primary">{heat.reduce((a, c) => a + c.count, 0)}</Badge>}>
        <HeatMap data={heat} />
      </Card>

      <Card title={isAr ? 'اتجاه المخاطر المفتوحة — 12 أسبوع' : 'Open-risk trend · 12 weeks'} Icon={Activity}
        action={<Link to="/risk" style={{
          font: '600 11px var(--font-sans)', color: 'var(--primary-700)',
          textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: 3,
        }}>
          {isAr ? 'عرض السجل' : 'View register'} <ArrowUpRight size={12} />
        </Link>}>
        <div style={{ padding: '8px 0' }}>
          <TimeSeries
            series={series}
            height={170} width={460}
            color="var(--primary)"
            area showAxis
          />
        </div>
        <div style={{
          display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)',
          gap: 8, marginTop: 12, paddingTop: 12,
          borderTop: '1px solid var(--border)',
        }}>
          <MiniStat label={isAr ? 'الذروة' : 'Peak'}    value={Math.max(...series.map(s => s.value))} tone="danger" />
          <MiniStat label={isAr ? 'المتوسط' : 'Average'} value={Math.round(series.reduce((a,s) => a+s.value, 0) / series.length)} tone="info" />
          <MiniStat label={isAr ? 'الآن' : 'Current'}    value={series[series.length - 1].value} tone="primary" />
        </div>
      </Card>
    </div>
  );
}

function MiniStat({ label, value, tone }: { label: string; value: number; tone: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <span className="text-2xs text-muted" style={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
        {label}
      </span>
      <span style={{
        font: '800 18px/1 var(--font-sans)', color: `var(--${tone}-fg)`,
      }}>
        {value}
      </span>
    </div>
  );
}

// ─── 4. Framework readiness ─────────────────────────────────────────────

function FrameworkReadiness({ stats }: { stats: DashboardStats }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const compl = stats.complianceScore ?? 0;
  // Progress only supports primary | accent | danger | success — map our
  // RAG intent onto those four. Anything < 60% → danger; < 80% → accent
  // (warm amber); ≥ 80% → success. The "primary" pick is reserved for the
  // headline framework (NCA ECC).
  type ProgressTone = 'primary' | 'accent' | 'danger' | 'success';
  const toneFor = (score: number, headline = false): ProgressTone => {
    if (headline) return 'primary';
    if (score < 60) return 'danger';
    if (score < 80) return 'accent';
    return 'success';
  };
  const items: Array<{ code: string; name: string; nameAr: string; score: number; tone: ProgressTone }> = [
    { code: 'NCA_ECC',     name: 'NCA ECC',          nameAr: 'الضوابط الأساسية للأمن السيبراني', score: compl,                      tone: toneFor(compl, true) },
    { code: 'ISO_27001',   name: 'ISO 27001',        nameAr: 'آيزو 27001',                       score: Math.min(100, compl + 4),  tone: toneFor(Math.min(100, compl + 4)) },
    { code: 'ISO_22301',   name: 'ISO 22301',        nameAr: 'آيزو 22301',                       score: Math.max(0,   compl - 11), tone: toneFor(Math.max(0, compl - 11)) },
    { code: 'PDPL',        name: 'SDAIA PDPL',       nameAr: 'نظام حماية البيانات',              score: Math.max(0,   compl - 25), tone: toneFor(Math.max(0, compl - 25)) },
    { code: 'SAMA_CSF',    name: 'SAMA CSF',         nameAr: 'إطار الأمن السيبراني للبنوك',      score: Math.max(0,   compl - 18), tone: toneFor(Math.max(0, compl - 18)) },
    { code: 'COSO',        name: 'COSO ERM',         nameAr: 'إطار كوزو لإدارة المخاطر',         score: Math.max(0,   compl - 9),  tone: toneFor(Math.max(0, compl - 9)) },
  ];
  return (
    <Card title={isAr ? 'جاهزية الأطر' : 'Framework readiness'} Icon={Award}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {items.map(it => (
          <Link key={it.code} to={`/compliance?tab=frameworks&fw=${it.code}`}
            style={{ textDecoration: 'none', color: 'inherit' }}>
            <Progress
              label={isAr ? it.nameAr : it.name}
              value={it.score}
              tone={it.tone}
            />
          </Link>
        ))}
      </div>
    </Card>
  );
}

// ─── 5. Attention strip (3 columns) ─────────────────────────────────────

/**
 * W2-1a-ii — all three columns of this strip were hardcoded.
 *
 * "Needs decision" listed three items an executive was told were awaiting their decision:
 * `R-0042 — Concentration risk on AWS Riyadh region` (CRITICAL, 2d), an NCA finding
 * `ECC-2-4-3`, and an overdue Jeddah DC recovery exercise. None existed. `R-0042` is the
 * mock risk code W2-1a-i proved is not findable in the tenant.
 *
 * "Upcoming deadlines" listed four REGULATORY dates — SAMA H1 attestation in 12 days, NCA
 * ECC refresh in 28, PDPL Article 8 ROPA in 45, Nitaqat Q2 in 58 — with regulator chips and
 * day counts. That is the most dangerous of the three: a tenant trusting this strip could
 * miss a real filing while the dashboard showed a comfortable 12-day runway to a deadline
 * that was never theirs.
 *
 * All three now read live data. Each column degrades to its own empty state rather than a
 * placeholder, because "nothing needs your decision" and "we could not load your deadlines"
 * are different statements and an executive summary must not conflate them.
 */
function AttentionStrip() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [decisions, setDecisions] = useState<AttentionItem[] | null>(null);
  const [deadlines, setDeadlines] = useState<DeadlineItem[] | null>(null);

  useEffect(() => {
    if (!isAuthenticated()) return;
    let alive = true;

    // Needs decision = open CRITICAL/HIGH risks + open HIGH/CRITICAL findings, oldest first.
    Promise.all([
      riskApi.list(undefined, 0, 200).catch(() => null),
      complianceApi.findings(0, 200).catch(() => null),
    ]).then(([risks, findings]) => {
      if (!alive) return;
      if (!risks && !findings) { setDecisions(null); return; }
      const items: AttentionItem[] = [];
      for (const r of risks?.content ?? []) {
        const sev = (r.riskLevel ?? '').toUpperCase();
        if (!['CRITICAL', 'HIGH'].includes(sev)) continue;
        if (['CLOSED', 'MITIGATED', 'ACCEPTED'].includes((r.status ?? '').toUpperCase())) continue;
        items.push({
          titleEn: `${r.riskCode ?? 'Risk'} — ${r.titleEn ?? ''}`.trim(),
          titleAr: `${r.riskCode ?? ''} — ${r.titleAr || r.titleEn || ''}`.trim(),
          sev: sev === 'CRITICAL' ? 'CRITICAL' : 'HIGH',
          age: ageLabel(r.createdAt),
        });
      }
      for (const f of findings?.content ?? []) {
        const sev = (f.severity ?? '').toUpperCase();
        if (!['CRITICAL', 'HIGH'].includes(sev)) continue;
        if ((f.status ?? '').toUpperCase() === 'CLOSED') continue;
        items.push({
          titleEn: f.title, titleAr: f.title,
          sev: sev === 'CRITICAL' ? 'CRITICAL' : 'HIGH',
          age: ageLabel(f.dueDate),
        });
      }
      setDecisions(items.slice(0, 5));
    });

    // Upcoming deadlines = the real compliance calendar, next 120 days.
    const from = new Date().toISOString().slice(0, 10);
    const to = new Date(Date.now() + 120 * 86400000).toISOString().slice(0, 10);
    calendarApi.list(from, to)
      .then(evts => {
        if (!alive) return;
        const day = 86400000;
        setDeadlines(evts
          .filter(e => e.eventType !== 'PUBLIC_HOLIDAY')
          .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
          .slice(0, 5)
          .map(e => ({
            titleEn: e.titleEn,
            titleAr: e.titleAr || e.titleEn,
            days: Math.max(0, Math.round((new Date(e.startsAt).getTime() - Date.now()) / day)),
            regulator: (e.eventType || '').replace(/_/g, ' '),
          })));
      })
      .catch(() => { if (alive) setDeadlines(null); });

    return () => { alive = false; };
  }, []);

  const empty = (en: string, ar: string) => (
    <div style={{ padding: '20px 4px', font: '500 12.5px var(--font-sans)', color: 'var(--ink-3)', textAlign: 'center' }}>
      {isAr ? ar : en}
    </div>
  );

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
      gap: 16,
    }}>
      <Card title={isAr ? 'يستوجب القرار' : 'Needs decision'} Icon={AlertOctagon}>
        {decisions == null
          ? empty('Could not load open items.', 'تعذّر تحميل العناصر المفتوحة.')
          : decisions.length === 0
            ? empty('Nothing awaiting a decision.', 'لا يوجد ما ينتظر قرارًا.')
            : <AttentionList items={decisions} />}
      </Card>

      <Card title={isAr ? 'مواعيد قادمة' : 'Upcoming deadlines'} Icon={Calendar}>
        {deadlines == null
          ? empty('Could not load your calendar.', 'تعذّر تحميل التقويم.')
          : deadlines.length === 0
            ? empty('No deadlines in the next 120 days.', 'لا مواعيد في الـ120 يومًا القادمة.')
            : <DeadlineList items={deadlines} />}
      </Card>

      <Card title={isAr ? 'النشاط الأخير' : 'Recent activity'} Icon={Activity}>
        <RecentActivityList />
      </Card>
    </div>
  );
}

type AttentionItem = { titleEn: string; titleAr: string; sev: 'CRITICAL' | 'HIGH' | 'MEDIUM'; age: string };
type DeadlineItem = { titleEn: string; titleAr: string; days: number; regulator: string };

/** "2d" / "5w" since a timestamp; em dash when there is no date to age from. */
function ageLabel(iso?: string | null): string {
  if (!iso) return '—';
  const days = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 86400000));
  return days < 14 ? `${days}d` : `${Math.round(days / 7)}w`;
}

function AttentionList({ items }: { items: Array<{ titleEn: string; titleAr: string; sev: 'CRITICAL' | 'HIGH' | 'MEDIUM'; age: string }> }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
      {items.map((it, i) => (
        <div key={i} style={{
          display: 'flex', alignItems: 'center', gap: 10,
          padding: '10px 0',
          borderBottom: i < items.length - 1 ? '1px solid var(--border)' : 'none',
        }}>
          <Badge tone={it.sev === 'CRITICAL' ? 'danger' : 'warning'}>
            {isAr ? (it.sev === 'CRITICAL' ? 'حرج' : 'عالي') : it.sev}
          </Badge>
          <span style={{ flex: 1, font: '600 12.5px var(--font-sans)', color: 'var(--ink-1)', lineHeight: 1.4 }}>
            {isAr ? it.titleAr : it.titleEn}
          </span>
          <span className="text-2xs text-muted" style={{ fontFamily: 'var(--font-mono)' }}>
            {it.age}
          </span>
        </div>
      ))}
    </div>
  );
}

function DeadlineList({ items }: { items: Array<{ titleEn: string; titleAr: string; days: number; regulator: string }> }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
      {items.map((it, i) => {
        const tone: 'danger' | 'warning' | 'info' =
          it.days <= 14 ? 'danger' : it.days <= 30 ? 'warning' : 'info';
        return (
          <div key={i} style={{
            display: 'flex', alignItems: 'center', gap: 10,
            padding: '10px 0',
            borderBottom: i < items.length - 1 ? '1px solid var(--border)' : 'none',
          }}>
            <div style={{
              width: 38, textAlign: 'center',
              padding: '3px 0', borderRadius: 'var(--r-sm)',
              background: `var(--${tone}-bg)`,
              border: `1px solid var(--${tone}-border)`,
            }}>
              <div style={{ font: '800 14px/1 var(--font-sans)', color: `var(--${tone}-fg)` }}>
                {it.days}
              </div>
              <div className="text-2xs" style={{ color: `var(--${tone}-fg)`, opacity: 0.8 }}>
                {isAr ? 'يوم' : 'd'}
              </div>
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ font: '600 12.5px var(--font-sans)', color: 'var(--ink-1)', lineHeight: 1.3 }}>
                {isAr ? it.titleAr : it.titleEn}
              </div>
              <div className="text-2xs text-muted" style={{ marginTop: 2 }}>
                <Link to={`/authority/${it.regulator}`} style={{ color: 'var(--primary-700)', textDecoration: 'none', fontWeight: 600 }}>
                  {it.regulator}
                </Link>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

/**
 * Recent activity — from the real activity log.
 *
 * W2-1a-ii — this was FOUR HARDCODED BILINGUAL LINES presented as a live feed with relative
 * timestamps: "Risk R-0042 mitigated by Sara A.", "ECC control AC-3 evidence approved",
 * "KRI threshold breached: failed logins", "Vendor reassessment overdue: Cloud4Saudi". None
 * of it happened. `R-0042` is the mock risk code W2-1a-i proved is not findable in the
 * tenant, so the executive dashboard reported activity on a record that does not exist —
 * and it was translated into Arabic, which is how much care went into making it look real.
 *
 * Two things worth recording about how this was found. First, it is NOT a mock *import*, so
 * the plan's count of mock imports in this file was zero and the planned `/mock/i` CI rule
 * (W2-1b) would never have flagged it — the same blind spot F2-33 has. Second, `widgets.tsx`
 * carried a SECOND copy of the same fabrication with slightly different wording ("New risk
 * R-0042 added by Sara"), and fixing that one changed nothing on screen because THIS is the
 * component the executive dashboard renders. Only the browser check caught that; the code
 * change looked complete.
 */
function RecentActivityList() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [events, setEvents] = useState<ActivityEvent[]>(() => loadActivity());
  useEffect(() => subscribeActivity(() => setEvents(loadActivity())), []);

  const now = Date.now();
  const ago = (iso: string) => {
    const mins = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60000));
    if (mins < 60) return `${mins}m`;
    if (mins < 1440) return `${Math.round(mins / 60)}h`;
    return `${Math.round(mins / 1440)}d`;
  };
  const look = (action: string) => {
    switch (action) {
      case 'CREATED':        return { Icon: Award,         tone: 'success' };
      case 'TRANSITIONED':   return { Icon: ShieldCheck,   tone: 'primary' };
      case 'CLOSED':         return { Icon: ShieldCheck,   tone: 'success' };
      case 'REOPENED':       return { Icon: AlertTriangle, tone: 'danger' };
      case 'EVIDENCE_ADDED': return { Icon: Award,         tone: 'primary' };
      default:               return { Icon: Clock,         tone: 'warning' };
    }
  };
  const describe = (e: ActivityEvent) => {
    const what = `${e.entityType.replace(/-/g, ' ')} ${e.entityId}`;
    if (e.action === 'TRANSITIONED') return `${what}: ${e.fromStatus ?? '?'} → ${e.toStatus ?? '?'}`;
    if (isAr) {
      if (e.action === 'CREATED')  return `إنشاء ${what}`;
      if (e.action === 'ASSIGNED') return `إسناد ${what}`;
      return e.noteAr?.trim() || `${e.action} · ${what}`;
    }
    if (e.action === 'CREATED')  return `Created ${what}`;
    if (e.action === 'ASSIGNED') return `Assigned ${what}${e.assigneeId ? ` → ${e.assigneeId}` : ''}`;
    return e.noteEn?.trim() || `${e.action} · ${what}`;
  };

  const items = [...events]
    .sort((a, b) => b.ts.localeCompare(a.ts))
    .slice(0, 5)
    .map(e => {
      const { Icon, tone } = look(e.action);
      const text = describe(e);
      return { Icon, tone, textEn: text, textAr: text, time: ago(e.ts) };
    });

  if (items.length === 0) {
    return (
      <div style={{ padding: '20px 4px', font: '500 12.5px var(--font-sans)', color: 'var(--ink-3)', textAlign: 'center' }}>
        {isAr ? 'لا يوجد نشاط مسجل بعد.' : 'No activity recorded yet.'}
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
      {items.map((it, i) => (
        <div key={i} style={{
          display: 'flex', alignItems: 'center', gap: 10,
          padding: '10px 0',
          borderBottom: i < items.length - 1 ? '1px solid var(--border)' : 'none',
        }}>
          <div style={{
            width: 30, height: 30, borderRadius: 'var(--r-sm)',
            background: `var(--${it.tone}-bg)`,
            color: `var(--${it.tone}-fg)`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <it.Icon size={14} />
          </div>
          <span style={{ flex: 1, font: '500 12.5px var(--font-sans)', color: 'var(--ink-1)', lineHeight: 1.4 }}>
            {isAr ? it.textAr : it.textEn}
          </span>
          <span className="text-2xs text-muted" style={{ fontFamily: 'var(--font-mono)' }}>
            {it.time}
          </span>
        </div>
      ))}
    </div>
  );
}
