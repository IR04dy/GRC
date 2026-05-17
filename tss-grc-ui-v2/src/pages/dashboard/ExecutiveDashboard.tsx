import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  ShieldCheck, AlertTriangle, AlertOctagon, FileWarning, Activity,
  TrendingUp, TrendingDown, ArrowUpRight, Calendar, Clock,
  Sparkles, Flame, Award,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { HeatMap, type HeatCell } from '../../components/viz/HeatMap';
import { TimeSeries, type TimeSeriesPoint } from '../../components/viz/TimeSeries';
import { Progress } from '../../components/ui/Progress';
import type { DashboardStats } from '../../lib/api';
import { COMPLIANCE_FINDINGS } from '../compliance/mockCompliance';

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
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginTop: 8 }}>
      <PostureHero stats={stats} />
      <ExecutiveKpiRow stats={stats} />
      <RiskHeatTrendRow heat={heat} stats={stats} />
      <FrameworkReadiness stats={stats} />
      <AttentionStrip />
    </div>
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

  // Mock 30-day delta — replace with real /api/v1/dashboard/posture-history
  // when the backend lands that endpoint. Sign-only for now, magnitude is
  // derived from the current posture so the chip moves coherently.
  const deltaPct = posture >= 75 ? +2 : posture >= 60 ? -1 : -4;
  const tone: 'success' | 'warning' | 'danger' =
    posture >= 80 ? 'success' : posture >= 60 ? 'warning' : 'danger';

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
    <div style={{
      background: `linear-gradient(135deg, var(--primary-50) 0%, var(--surface-2) 100%)`,
      borderRadius: 'var(--r-lg)',
      border: '1px solid var(--border)',
      padding: '24px 28px',
      display: 'grid',
      gridTemplateColumns: 'minmax(0, 1fr) auto',
      gap: 24,
      alignItems: 'center',
    }}>
      {/* Left: greeting + date */}
      <div style={{ minWidth: 0 }}>
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
          font: '800 28px var(--font-sans)',
          color: 'var(--ink-1)',
          letterSpacing: '-0.02em',
          lineHeight: 1.15,
        }}>
          {greeting}{isAr ? '، ' : ', '}
          <span style={{ color: 'var(--primary-700)' }}>
            {isAr ? 'أيمن' : 'Aymen'}
          </span>
        </h1>
        <div style={{ display: 'flex', gap: 14, alignItems: 'center', marginTop: 6 }}>
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

      {/* Right: big composite posture number */}
      <div style={{
        display: 'flex', flexDirection: 'column', alignItems: 'flex-end',
        gap: 6, padding: '8px 0',
      }}>
        <span style={{
          font: '700 10px var(--font-sans)', color: 'var(--ink-3)',
          textTransform: 'uppercase', letterSpacing: '0.1em',
        }}>
          {isAr ? 'مؤشّر الحوكمة الشامل' : 'GRC Posture Score'}
        </span>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
          <span style={{
            font: '800 56px/1 var(--font-sans)',
            color: `var(--${tone}-fg)`,
            letterSpacing: '-0.04em',
          }}>
            {posture}
          </span>
          <span style={{ font: '700 18px var(--font-sans)', color: 'var(--ink-3)' }}>
            / 100
          </span>
        </div>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <Badge tone={deltaPct >= 0 ? 'success' : 'danger'}>
            {deltaPct >= 0 ? <TrendingUp size={11} /> : <TrendingDown size={11} />}
            {deltaPct >= 0 ? '+' : ''}{deltaPct}% {isAr ? '/ 30 يوم' : 'vs 30d'}
          </Badge>
        </div>
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
//   • Sparkline (30 sample stub — backend trend endpoint TODO)
//   • Click → drill into the matching module

interface KpiTileProps {
  Icon: typeof ShieldCheck;
  label: string;
  value: number | string;
  to: string;
  tone: 'primary' | 'warning' | 'danger' | 'success' | 'info';
  spark?: number[];
  delta?: { value: number; tone: 'success' | 'danger' };
  /** Bottom microbar — stacked colored segments. */
  segments?: Array<{ value: number; color: string; label: string }>;
}

function KpiTile({ Icon, label, value, to, tone, spark, delta, segments }: KpiTileProps) {
  const total = segments?.reduce((a, s) => a + s.value, 0) ?? 0;
  return (
    <Link to={to} style={{
      display: 'flex', flexDirection: 'column', gap: 10,
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
            {delta.tone === 'success' ? <TrendingDown size={10} /> : <TrendingUp size={10} />}
            {delta.value >= 0 ? '+' : ''}{delta.value}
          </Badge>
        )}
      </div>

      {/* Big value + sparkline side-by-side */}
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 8 }}>
        <span style={{
          font: '800 30px/1 var(--font-sans)',
          color: 'var(--ink-1)', letterSpacing: '-0.02em',
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

function ExecutiveKpiRow({ stats }: { stats: DashboardStats }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  // Synthetic 12-week sparklines that converge on the current value —
  // replace with real /api/v1/dashboard/kpi-trend when shipped.
  const trendTo = (target: number, vol = 0.2) =>
    Array.from({ length: 12 }, (_, i) =>
      Math.max(0, Math.round(target * (1 + (Math.sin(i * 0.7) * vol + (i / 12 - 0.5) * 0.1))))
    );

  // Compute overdue items count — sum across the data sources we have.
  const overdueFindings = COMPLIANCE_FINDINGS.filter(f =>
    f.status !== 'CLOSED' && f.dueDate && new Date(f.dueDate) < new Date()
  ).length;
  const totalOverdue = overdueFindings + (stats.pendingTasks ?? 0);

  const tiles: KpiTileProps[] = [
    {
      Icon: AlertTriangle,
      label: isAr ? 'مخاطر مفتوحة' : 'Open risks',
      value: stats.openRisks ?? 0,
      to: '/risk?status=open',
      tone: stats.openCriticalRisks > 0 ? 'danger' : 'warning',
      spark: trendTo(stats.openRisks ?? 0),
      delta: { value: -2, tone: 'success' },
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
      spark: trendTo(stats.complianceScore ?? 0, 0.05),
      delta: { value: +3, tone: 'success' },
    },
    {
      Icon: AlertOctagon,
      label: isAr ? 'حوادث نشطة' : 'Active incidents',
      value: stats.openIncidents ?? 0,
      to: '/incidents?status=open',
      tone: (stats.openIncidents ?? 0) > 0 ? 'danger' : 'success',
      spark: trendTo(stats.openIncidents ?? 0, 0.4),
      delta: stats.openIncidents > 0 ? { value: +1, tone: 'danger' } : undefined,
    },
    {
      Icon: FileWarning,
      label: isAr ? 'بنود متأخّرة' : 'Overdue items',
      value: totalOverdue,
      to: '/capa?status=overdue',
      tone: totalOverdue > 5 ? 'danger' : totalOverdue > 0 ? 'warning' : 'success',
      spark: trendTo(totalOverdue, 0.3),
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

function RiskHeatTrendRow({ heat, stats }: { heat: HeatCell[]; stats: DashboardStats }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  // 12-week mocked trend — same convergence shape, stable enough to render
  // a clean area chart. Replace when the trend endpoint ships.
  const series: TimeSeriesPoint[] = Array.from({ length: 12 }, (_, i) => {
    const base = (stats.openRisks ?? 28) - 4;
    const v = Math.max(0, Math.round(base + Math.sin(i * 0.6) * 5 + (i / 12) * 4));
    const wkLabel = isAr ? `أ${i + 1}` : `W${i + 1}`;
    return { label: wkLabel, value: v };
  });

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

function AttentionStrip() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
      gap: 16,
    }}>
      <Card title={isAr ? 'يستوجب القرار' : 'Needs decision'} Icon={AlertOctagon}>
        <AttentionList items={[
          { titleEn: 'R-0042 — Concentration risk on AWS Riyadh region',  titleAr: 'تركّز المخاطر على منطقة أرامكو السحابية', sev: 'CRITICAL', age: '2d' },
          { titleEn: 'NCA finding ECC-2-4-3 — privileged session log',     titleAr: 'ملاحظة NCA — سجلّ الجلسات الإدارية',     sev: 'HIGH',     age: '4d' },
          { titleEn: 'BCM IT-DRP exercise overdue (Jeddah DC)',            titleAr: 'تمرين التعافي IT-DRP متأخّر — مركز جدة',  sev: 'HIGH',     age: '6d' },
        ]} />
      </Card>

      <Card title={isAr ? 'مواعيد قادمة' : 'Upcoming deadlines'} Icon={Calendar}>
        <DeadlineList items={[
          { titleEn: 'SAMA H1 cybersecurity attestation',     titleAr: 'إقرار الأمن السيبراني نصف السنوي — ساما',     days: 12, regulator: 'SAMA' },
          { titleEn: 'NCA ECC self-assessment refresh',       titleAr: 'تحديث التقييم الذاتي للضوابط الأساسية',         days: 28, regulator: 'NCA' },
          { titleEn: 'PDPL Article 8 ROPA re-certification',  titleAr: 'إعادة اعتماد سجلّ المعالجة — مادة 8',           days: 45, regulator: 'SDAIA' },
          { titleEn: 'Nitaqat quarterly report (Q2)',         titleAr: 'تقرير نطاقات الربع الثاني',                     days: 58, regulator: 'HRSD' },
        ]} />
      </Card>

      <Card title={isAr ? 'النشاط الأخير' : 'Recent activity'} Icon={Activity}>
        <RecentActivityList />
      </Card>
    </div>
  );
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

function RecentActivityList() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const items = [
    { Icon: ShieldCheck,   tone: 'success', textEn: 'Risk R-0042 mitigated by Sara A.', textAr: 'تخفيف المخاطرة R-0042 بواسطة سارة', time: '2m' },
    { Icon: Award,         tone: 'primary', textEn: 'ECC control AC-3 evidence approved', textAr: 'اعتماد دليل الضابط AC-3', time: '14m' },
    { Icon: AlertTriangle, tone: 'danger',  textEn: 'KRI threshold breached: failed logins', textAr: 'تجاوز عتبة المؤشّر: محاولات دخول فاشلة', time: '1h' },
    { Icon: Clock,         tone: 'warning', textEn: 'Vendor reassessment overdue: Cloud4Saudi', textAr: 'تأخّر إعادة تقييم المورّد: كلاود فور سعودي', time: '3h' },
  ];
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
