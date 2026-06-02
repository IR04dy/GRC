import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TrendingUp, AlertTriangle, ShieldCheck, Activity, Layers, Target, RefreshCw } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { BarChart } from '../../components/charts/BarChart';
import { PieChart, type PieDatum } from '../../components/charts/PieChart';
import { LineChart } from '../../components/charts/LineChart';
import { RiskMatrix5x5, type RiskMovement } from '../../components/viz/RiskMatrix5x5';
import { useI18n } from '../../lib/i18n';
import { dashboardApi, ermAdvancedApi, riskApi, type DashboardStats, ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';

/**
 * ERM Dashboard — interactive, live-data version (replaces text-only widgets).
 *
 * <p>Per user feedback "no actual dashboards or live charts" — this rebuild
 * surfaces:
 * <ol>
 *   <li>KPI strip: total risks, open critical, open high, residual closed,
 *       compliance score, pending treatment tasks (with drill-down `to=`).</li>
 *   <li>Risk-by-severity donut (live counts).</li>
 *   <li>Risk-by-category horizontal bar (top 8 categories).</li>
 *   <li>5x5 inherent-risk heat-map matrix (clickable cell → filter register).</li>
 *   <li>KRI band donut (green/amber/red/no-data).</li>
 *   <li>Risk appetite breach summary (live counts vs tolerance).</li>
 * </ol>
 * Every tile drills into the underlying register via NavLink — the CRO can
 * go from "I see 4 open criticals" → the register filtered to those rows in
 * one click. Data refreshes via the explicit refresh button or on tab focus.
 */
export function ErmDashboardPage() {
  const { lang } = useI18n();
  const navigate = useNavigate();
  const isAr = lang === 'ar';

  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [heatmap, setHeatmap] = useState<HeatmapCell[]>([]);
  const [byCat, setByCat] = useState<CategoryBucket | null>(null);
  const [kri, setKri] = useState<KriBands | null>(null);
  const [appetite, setAppetite] = useState<AppetiteSummary | null>(null);
  const [trend, setTrend] = useState<Array<{ week: string; open: number; critical: number }>>([]);
  const [movements, setMovements] = useState<RiskMovement[]>([]);
  const [showVectors, setShowVectors] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    if (!isAuthenticated()) { setLoading(false); return; }
    setLoading(true);
    try {
      const [s, h, c, k, a, tRisks, tCrit, risks] = await Promise.all([
        dashboardApi.stats(),
        dashboardApi.heatmap() as Promise<{ cells: HeatmapCell[] }>,
        dashboardApi.riskByCategory() as Promise<CategoryBucket>,
        ermAdvancedApi.kriDashboard(),
        ermAdvancedApi.appetiteDashboard().catch(() => null),
        dashboardApi.kpiTrend('risks', 12).catch(() => []),
        dashboardApi.kpiTrend('critical_risks', 12).catch(() => []),
        riskApi.list(undefined, 0, 200).catch(() => null),
      ]);
      setStats(s);
      setHeatmap(h?.cells ?? []);
      setByCat(c ?? null);
      setKri(k ?? null);
      setAppetite(a as AppetiteSummary | null);
      setTrend(tRisks.map((p, i) => ({ week: p.label, open: p.value, critical: tCrit[i]?.value ?? 0 })));
      // Inherent→residual movement vectors (control effectiveness made visible).
      const movs: RiskMovement[] = (risks?.content ?? [])
        .filter(r => r.residualLikelihood != null && r.residualImpact != null)
        .map(r => ({
          fromL: r.inherentLikelihood, fromI: r.inherentImpact,
          toL: r.residualLikelihood as number, toI: r.residualImpact as number,
        }))
        .filter(m => m.fromL >= 1 && m.fromL <= 5 && m.fromI >= 1 && m.fromI <= 5
                  && m.toL >= 1 && m.toL <= 5 && m.toI >= 1 && m.toI <= 5);
      setMovements(movs);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load dashboard');
    } finally { setLoading(false); }
  }
  useEffect(() => { void load();
    function onFocus() { if (document.visibilityState === 'visible') void load(); }
    document.addEventListener('visibilitychange', onFocus);
    return () => document.removeEventListener('visibilitychange', onFocus);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ─── KPI strip data ───────────────────────────────────────────────
  const kpis = stats ? [
    { Icon: Layers,         label: isAr ? 'إجمالي المخاطر'     : 'Total risks',     value: stats.totalRisks,         tone: 'primary' as const, to: '/risk' },
    { Icon: AlertTriangle,  label: isAr ? 'حرجة مفتوحة'        : 'Open critical',   value: stats.openCriticalRisks,  tone: stats.openCriticalRisks > 0 ? 'danger' as const : 'success' as const, to: '/risk?severity=CRITICAL&status=OPEN' },
    { Icon: TrendingUp,     label: isAr ? 'عالية مفتوحة'       : 'Open high',       value: stats.openHighRisks,      tone: stats.openHighRisks > 0 ? 'warning' as const : 'success' as const, to: '/risk?severity=HIGH&status=OPEN' },
    { Icon: ShieldCheck,    label: isAr ? 'مغلقة'              : 'Closed',          value: stats.closedRisks,        tone: 'info' as const, to: '/risk?status=CLOSED' },
    { Icon: Target,         label: isAr ? 'نسبة الامتثال'      : 'Compliance score', value: `${stats.complianceScore}%`,
      tone: stats.complianceScore >= 80 ? 'success' as const : stats.complianceScore >= 60 ? 'warning' as const : 'danger' as const, to: '/compliance/posture' },
    { Icon: Activity,       label: isAr ? 'مهام بانتظار التنفيذ' : 'Pending tasks',  value: stats.pendingTasks,
      tone: stats.pendingTasks > 5 ? 'warning' as const : 'info' as const, to: '/approvals' },
  ] : [];

  // ─── Pie data ─────────────────────────────────────────────────────
  const severityPie: PieDatum[] = stats ? [
    { name: isAr ? 'حرجة'   : 'Critical', value: stats.criticalRisks, color: '#dc2626' },
    { name: isAr ? 'عالية'  : 'High',     value: stats.highRisks,     color: '#ea580c' },
    { name: isAr ? 'متوسطة' : 'Medium',   value: stats.mediumRisks,   color: '#ca8a04' },
    { name: isAr ? 'منخفضة' : 'Low',      value: stats.lowRisks,      color: '#16a34a' },
  ].filter(d => d.value > 0) : [];

  const kriPie: PieDatum[] = kri ? [
    { name: isAr ? 'أخضر'        : 'Green',   value: kri.green,  color: '#16a34a' },
    { name: isAr ? 'أصفر'        : 'Amber',   value: kri.amber,  color: '#ca8a04' },
    { name: isAr ? 'أحمر'        : 'Red',     value: kri.red,    color: '#dc2626' },
    { name: isAr ? 'بدون بيانات' : 'No data', value: kri.noData, color: '#94a3b8' },
  ].filter(d => d.value > 0) : [];

  // ─── Risk-by-category bar data — top 8 categories ─────────────────
  const categoryBars = byCat
    ? byCat.labels.map((label, i) => ({ label, value: byCat.values[i] ?? 0 }))
        .sort((a, b) => b.value - a.value)
        .slice(0, 8)
    : [];

  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'إدارة المخاطر' : 'Enterprise Risk'}
        title={isAr ? 'لوحة إدارة المخاطر المؤسسية' : 'Enterprise Risk Dashboard'}
        sub={isAr
          ? 'بيانات حيّة من سجل المخاطر · مصفوفة احتمال/أثر تفاعلية · جميع البطاقات قابلة للنقر'
          : 'Live risk-register data · interactive likelihood/impact heat-map · every tile drills into the register'}
        actions={
          <Button variant="ghost" Icon={RefreshCw} onClick={load}>
            {isAr ? 'تحديث' : 'Refresh'}
          </Button>
        }
      />

      {error && <Card><div style={{ padding: 12, color: 'var(--danger,#a00)' }}>{error}</div></Card>}

      {/* ── KPI strip ── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 12, marginBottom: 16 }}>
        {(loading && !stats) && Array.from({ length: 6 }).map((_, i) =>
          <Kpi key={i} Icon={Layers} label={isAr ? 'جارٍ التحميل…' : 'Loading…'} value="—" tone="neutral" />
        )}
        {kpis.map((k, i) => (
          <Kpi key={i} {...k} />
        ))}
      </div>

      {/* ── Charts row 1: severity donut + KRI donut ── */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
        <Card>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ fontWeight: 700 }}>{isAr ? 'توزيع المخاطر حسب الشدّة' : 'Risk by severity'}</div>
            <Badge>{stats ? stats.totalRisks : 0}</Badge>
          </div>
          <div style={{ padding: 12 }}>
            <PieChart data={severityPie} innerRadius={55} legend height={240}
              onPointClick={(d) => navigate(`/risk?severity=${d.name.toUpperCase()}`)}
              emptyState={isAr ? 'لا توجد مخاطر' : 'No risks yet'} />
          </div>
        </Card>
        <Card>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ fontWeight: 700 }}>{isAr ? 'مؤشرات المخاطر الرئيسية (KRI)' : 'KRI bands'}</div>
            <div style={{ display: 'flex', gap: 6, fontSize: 12 }}>
              {kri && <Badge tone="success">{kri.green}</Badge>}
              {kri && <Badge tone="warning">{kri.amber}</Badge>}
              {kri && <Badge tone="danger">{kri.red}</Badge>}
            </div>
          </div>
          <div style={{ padding: 12 }}>
            <PieChart data={kriPie} innerRadius={55} legend height={240}
              onPointClick={() => navigate('/risk?tab=kri')}
              emptyState={isAr ? 'لا توجد قراءات' : 'No KRI readings'} />
          </div>
        </Card>
      </div>

      {/* ── Risk trend over time (real, 12 weeks from /kpi-trend) ── */}
      <div style={{ marginBottom: 16 }}>
        <Card>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ fontWeight: 700 }}>{isAr ? 'اتجاه المخاطر المفتوحة — 12 أسبوع' : 'Open-risk trend · 12 weeks'}</div>
            <Badge tone="neutral">{trend.length ? trend[trend.length - 1].open : 0}</Badge>
          </div>
          <div style={{ padding: 12 }}>
            <LineChart
              data={trend}
              xKey="week"
              height={220}
              series={[
                { key: 'open', name: isAr ? 'مخاطر مفتوحة' : 'Open risks', color: 'var(--primary)' },
                { key: 'critical', name: isAr ? 'حرجة' : 'Critical', color: '#dc2626' },
              ]}
              emptyState={isAr ? 'لا توجد بيانات اتجاه' : 'No trend data yet'}
              onPointClick={() => navigate('/risk')} />
          </div>
        </Card>
      </div>

      {/* ── Charts row 2: heat-map + categories bar ── */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.2fr', gap: 12, marginBottom: 16 }}>
        <Card>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
            <div style={{ fontWeight: 700 }}>
              {isAr ? 'مصفوفة المخاطر (٥×٥) — الكامن ← المتبقي' : 'Risk matrix (5×5) — inherent → residual'}
            </div>
            {movements.length > 0 && (
              <button type="button" onClick={() => setShowVectors(v => !v)}
                className="card--interactive"
                style={{
                  font: '600 11px var(--font-sans)', cursor: 'pointer',
                  padding: '4px 10px', borderRadius: 'var(--r-pill)',
                  border: '1px solid var(--border)',
                  background: showVectors ? 'var(--primary-soft)' : 'var(--surface)',
                  color: showVectors ? 'var(--primary-700)' : 'var(--ink-3)',
                }}>
                {isAr ? 'إظهار الحركة' : 'Show movement'}
              </button>
            )}
          </div>
          <div style={{ padding: 14, display: 'flex', justifyContent: 'center' }}>
            <RiskMatrix5x5 cells={heatmap} movements={movements} showVectors={showVectors} ar={isAr}
              onCellClick={(L, I) => navigate(`/risk?likelihood=${L}&impact=${I}`)} />
          </div>
        </Card>
        <Card>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)', fontWeight: 700 }}>
            {isAr ? 'المخاطر حسب الفئة — أعلى ٨' : 'Risks by category — top 8'}
          </div>
          <div style={{ padding: 12 }}>
            <BarChart data={categoryBars}
              xKey="label"
              series={[{ key: 'value', name: isAr ? 'العدد' : 'Risks', color: '#0d9488' }]}
              height={260}
              onPointClick={(d) => navigate(`/risk?category=${encodeURIComponent(d.label)}`)}
              emptyState={isAr ? 'لا توجد فئات' : 'No categories'} />
          </div>
        </Card>
      </div>

      {/* ── Appetite tile ── */}
      {appetite && (
        <Card>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ fontWeight: 700 }}>{isAr ? 'شهية المخاطر — حالة الاختراق' : 'Risk appetite — breach status'}</div>
            <Badge tone={appetite.exceeded > 0 || appetite.critical > 0 ? 'danger' : 'success'}>
              {appetite.exceeded + appetite.critical > 0 ? (isAr ? 'مُخترَقة' : 'BREACHED') : (isAr ? 'ضمن الحدود' : 'WITHIN')}
            </Badge>
          </div>
          <div style={{ padding: 14, display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 16, textAlign: 'center' }}>
            <AppetiteTile label={isAr ? 'إجمالي' : 'Total'} value={appetite.total} />
            <AppetiteTile label={isAr ? 'ضمن الحدود' : 'Within tolerance'} value={appetite.withinTolerance} tone="success" />
            <AppetiteTile label={isAr ? 'تجاوز' : 'Exceeded'} value={appetite.exceeded} tone="warning" />
            <AppetiteTile label={isAr ? 'حرج' : 'Critical'} value={appetite.critical} tone="danger" />
            <AppetiteTile label={isAr ? 'بدون تعرّض' : 'No exposure'} value={appetite.noExposure} />
          </div>
        </Card>
      )}
    </Shell>
  );
}

interface HeatmapCell { likelihood: number; impact: number; count: number; }
interface CategoryBucket { labels: string[]; values: number[]; }
interface KriBands { totalKris: number; green: number; amber: number; red: number; noData: number; openBreaches: number; }
interface AppetiteSummary { total: number; withinTolerance: number; exceeded: number; critical: number; noExposure: number; }

function AppetiteTile({ label, value, tone }: { label: string; value: number; tone?: 'success' | 'warning' | 'danger' }) {
  const color = tone === 'success' ? '#16a34a' : tone === 'warning' ? '#ca8a04' : tone === 'danger' ? '#dc2626' : 'inherit';
  return (
    <div>
      <div style={{ fontSize: 24, fontWeight: 700, color, lineHeight: 1 }}>{value}</div>
      <div style={{ fontSize: 11, opacity: 0.7, marginTop: 4 }}>{label}</div>
    </div>
  );
}
