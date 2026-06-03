import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Activity, ShieldCheck, ShieldAlert, CalendarClock, HelpCircle, TrendingUp } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  drTestTrendApi, DR_TREND_DEFAULT_QUARTERS, ApiError,
  type DrTrendReport, type DrQuarterStats,
} from '../../lib/api';

const INPUT_STYLE: React.CSSProperties = {
  padding: '6px 8px', fontSize: 13, border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};

const PASS = 'var(--success)';
const FAIL = 'var(--danger)';
const GAP  = 'var(--warning)';
const RATE = 'var(--primary)';

const QUARTER_OPTIONS = [4, 8, 12];

const pct = (v: number) => `${Math.round(v * 100)}%`;

/** "gap / other" = the service's partial + na buckets. The DB outcome
 * taxonomy is PASSED / FAILED / GAP / NOT_TESTED, so `partial` is always 0
 * and GAP/NOT_TESTED land in `na`; we present them as one amber series. */
const otherOf = (q: DrQuarterStats) => q.partial + q.na;

/**
 * BCM DR-test trend — surfaces GET /api/v1/bcm/dr-test-trend?quarters=N.
 *
 * The Board pack question "what is our DR-test pass-rate over the last N
 * quarters, and is it improving?" The backend aggregates bcm.dr_plans into a
 * rolling-quarter outcome series (passed / failed / gap-or-other) plus a
 * coverage snapshot (total DRPs, never-tested, overdue >12mo, recent pass
 * rate). This page renders the combo chart (stacked outcome bars + overlaid
 * pass-rate line) the executive committee expects, a coverage breakdown, and
 * a per-quarter register. Bilingual; outcome colours on the semantic ramp.
 */
export function DrTestTrendPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [quarters, setQuarters] = useState(DR_TREND_DEFAULT_QUARTERS);
  const [report, setReport] = useState<DrTrendReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    setLoading(true);
    (async () => {
      try {
        const r = await drTestTrendApi.trend(quarters);
        if (!live) return;
        setReport(r);
        setError(null);
      } catch (e) {
        if (!live) return;
        const msg = e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل اتجاه الاختبارات' : 'Failed to load test trend');
        setError(msg);
        toast.danger(msg);
      } finally {
        if (live) setLoading(false);
      }
    })();
    return () => { live = false; };
  }, [quarters]);  // eslint-disable-line react-hooks/exhaustive-deps

  const snap = report?.snapshot;
  const passTone = snap == null ? 'neutral'
    : snap.recentPassRate >= 0.8 ? 'success' : snap.recentPassRate >= 0.5 ? 'warning' : 'danger';

  return (
    <Shell
      title={isAr ? 'اتجاه اختبارات التعافي من الكوارث' : 'DR-test trend'}
      sub={isAr ? 'نسبة نجاح تمارين التعافي عبر الأرباع' : 'DR-exercise pass-rate across quarters'}
    >
      <PageHeader
        eyebrow={isAr ? 'استمرارية الأعمال · ساما BCM / ISO 22301' : 'Continuity · SAMA BCM / ISO 22301'}
        title={isAr ? 'اتجاه اختبارات التعافي من الكوارث' : 'DR-test trend'}
        sub={isAr
          ? 'سلسلة زمنية ربعية لنتائج تمارين التعافي من الكوارث (نجاح / إخفاق / ملاحظات) مع لقطة تغطية لخطط التعافي: المُختبَرة حديثًا، والمتأخّرة عن 12 شهرًا، وغير المُختبَرة — وهي اللوحة التي تتوقّعها لجنة المخاطر لقياس نضج برنامج التعافي.'
          : 'A rolling-quarter time-series of DR-exercise outcomes (passed / failed / gaps) with a coverage snapshot of the DR plans — tested-recently, overdue beyond 12 months, and never-tested — the view the risk committee expects to gauge DR-programme maturity.'}
        actions={
          <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 12.5, color: 'var(--ink-2)' }}>
            {isAr ? 'المدى' : 'Window'}
            <select value={quarters} onChange={e => setQuarters(Number(e.target.value))} style={INPUT_STYLE}
              aria-label={isAr ? 'عدد الأرباع' : 'Number of quarters'}>
              {QUARTER_OPTIONS.map(q => (
                <option key={q} value={q}>{isAr ? `${q} أرباع` : `${q} quarters`}</option>
              ))}
            </select>
          </label>
        }
      />

      {error && !report && (
        <Card title={isAr ? 'خطأ' : 'Error'}>
          <div style={{ padding: '20px 8px', textAlign: 'center', color: 'var(--danger-fg)', fontSize: 13 }}>{error}</div>
        </Card>
      )}

      {report && snap && (
        <>
          <div className="kpi-grid" style={{ marginBottom: 16 }}>
            <Kpi Icon={Activity}      label={isAr ? 'إجمالي خطط التعافي' : 'Total DR plans'} value={snap.totalDrps} tone="primary" />
            <Kpi Icon={TrendingUp}    label={isAr ? `نسبة النجاح · ${snap.passedRecently}/${snap.testedRecently} (آخر 12 شهرًا)` : `Pass rate · ${snap.passedRecently}/${snap.testedRecently} (last 12 mo)`} value={pct(snap.recentPassRate)} tone={passTone} />
            <Kpi Icon={CalendarClock} label={isAr ? 'متأخّرة (> 12 شهرًا)' : 'Overdue (> 12 mo)'} value={snap.testedOverdue} tone={snap.testedOverdue > 0 ? 'warning' : 'success'} />
            <Kpi Icon={HelpCircle}    label={isAr ? 'لم تُختبر قط' : 'Never tested'} value={snap.neverTested} tone={snap.neverTested > 0 ? 'danger' : 'success'} />
          </div>

          <Card
            title={isAr ? 'نتائج التمارين عبر الأرباع' : 'Exercise outcomes by quarter'}
            Icon={Activity}
            action={<span className="text-2xs text-muted">{isAr ? `حتى ${report.evaluatedOn}` : `as of ${report.evaluatedOn}`}</span>}
          >
            <TrendChart series={report.series} isAr={isAr} loading={loading} />
            <Legend isAr={isAr} />
          </Card>

          <CoverageCard snap={snap} isAr={isAr} />

          <Card title={isAr ? 'سجلّ الأرباع' : 'Quarterly register'} Icon={Activity}>
            <div style={{ overflowX: 'auto' }} dir={isAr ? 'rtl' : 'ltr'}>
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الربع' : 'Quarter'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'مُختبَرة' : 'Tested'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'نجاح' : 'Passed'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'إخفاق' : 'Failed'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'ملاحظات / أخرى' : 'Gap / other'}</th>
                    <th style={{ width: 150 }}>{isAr ? 'نسبة النجاح' : 'Pass rate'}</th>
                  </tr>
                </thead>
                <tbody>
                  {report.series.map(q => {
                    const other = otherOf(q);
                    return (
                      <tr key={q.quarter}>
                        <td className="mono text-2xs" style={{ color: 'var(--ink-1)' }}>{q.quarter}</td>
                        <td className="mono text-2xs" style={{ textAlign: 'end' }}>{q.totalTested || '—'}</td>
                        <td className="mono text-2xs" style={{ textAlign: 'end', color: q.passed ? 'var(--success-fg)' : 'var(--ink-3)' }}>{q.passed || '—'}</td>
                        <td className="mono text-2xs" style={{ textAlign: 'end', color: q.failed ? 'var(--danger-fg)' : 'var(--ink-3)' }}>{q.failed || '—'}</td>
                        <td className="mono text-2xs" style={{ textAlign: 'end', color: other ? 'var(--warning-fg)' : 'var(--ink-3)' }}>{other || '—'}</td>
                        <td>
                          {q.totalTested === 0
                            ? <span className="text-2xs text-muted">{isAr ? 'لا تمارين' : 'no exercises'}</span>
                            : (
                              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                <div style={{ flex: 1, height: 6, borderRadius: 3, background: 'var(--surface-2)', overflow: 'hidden' }}>
                                  <div style={{ width: pct(q.passRate), height: '100%', background: rateColor(q.passRate) }} />
                                </div>
                                <span className="mono text-2xs" style={{ minWidth: 34, textAlign: 'end', color: rateColor(q.passRate) }}>{pct(q.passRate)}</span>
                              </div>
                            )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <p className="text-2xs text-muted" style={{ marginTop: 10, marginBottom: 0 }}>
              {isAr
                ? '«ملاحظات / أخرى» تجمع نتائج التمارين ذات الملاحظات وغير المُصنّفة (GAP)؛ نسبة النجاح = الناجحة ÷ إجمالي المُختبَرة في الربع. الربع يعكس آخر اختبار مُسجّل لكل خطة.'
                : '“Gap / other” combines exercises closed with findings or unclassified (GAP); pass rate = passed ÷ total tested in the quarter. Each quarter reflects the last recorded test of each plan.'}
            </p>
          </Card>
        </>
      )}
    </Shell>
  );
}

// ─── Combo chart: stacked outcome bars + overlaid pass-rate line ─────────────
function TrendChart({ series, isAr, loading }: { series: DrQuarterStats[]; isAr: boolean; loading: boolean }) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const [w, setW] = useState(760);

  useLayoutEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const update = () => setW(Math.max(320, el.clientWidth));
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const H = 248;
  const PAD = { t: 22, r: 30, b: 30, l: 14 };
  const plotW = Math.max(1, w - PAD.l - PAD.r);
  const plotH = H - PAD.t - PAD.b;
  const n = series.length;
  const band = n > 0 ? plotW / n : plotW;
  const barW = Math.max(8, Math.min(48, band * 0.52));
  const maxTested = Math.max(1, ...series.map(s => s.totalTested));

  const cx = (i: number) => PAD.l + band * i + band / 2;
  const yCount = (v: number) => PAD.t + plotH * (1 - v / maxTested);
  const yRate = (r: number) => PAD.t + plotH * (1 - r);
  const base = PAD.t + plotH;

  // Pass-rate markers only for quarters that actually had exercises.
  const ratePts = useMemo(
    () => series.map((s, i) => ({ i, x: cx(i), y: yRate(s.passRate), has: s.totalTested > 0, rate: s.passRate }))
              .filter(p => p.has),
    [series, w],  // eslint-disable-line react-hooks/exhaustive-deps
  );
  const linePath = ratePts.map((p, k) => `${k === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');

  if (loading && series.length === 0) {
    return <div style={{ height: H, display: 'grid', placeItems: 'center', color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>;
  }

  return (
    <div ref={wrapRef} style={{ width: '100%', opacity: loading ? 0.55 : 1, transition: 'opacity .15s' }} dir="ltr">
      <svg width={w} height={H} role="img"
        aria-label={isAr ? 'مخطّط اتجاه نتائج اختبارات التعافي' : 'DR-test outcome trend chart'}>
        {/* pass-rate gridlines (right-axis scale) */}
        {[0, 0.5, 1].map(p => {
          const y = yRate(p);
          return (
            <g key={p}>
              <line x1={PAD.l} y1={y} x2={PAD.l + plotW} y2={y} stroke="var(--border)" strokeWidth={1}
                strokeDasharray={p === 0 ? undefined : '3 3'} />
              <text x={PAD.l + plotW + 6} y={y + 3} fontSize={9.5} fill="var(--ink-3)" className="mono">{p * 100}%</text>
            </g>
          );
        })}

        {/* stacked outcome bars */}
        {series.map((s, i) => {
          const other = otherOf(s);
          const x = cx(i) - barW / 2;
          const hPass  = (s.passed / maxTested) * plotH;
          const hFail  = (s.failed / maxTested) * plotH;
          const hOther = (other / maxTested) * plotH;
          let y = base;
          const segs: Array<{ h: number; c: string }> = [
            { h: hPass, c: PASS }, { h: hFail, c: FAIL }, { h: hOther, c: GAP },
          ];
          return (
            <g key={s.quarter}>
              {segs.map((seg, k) => {
                if (seg.h <= 0) return null;
                y -= seg.h;
                return <rect key={k} x={x} y={y} width={barW} height={seg.h}
                  fill={seg.c} opacity={0.9} rx={1.5} />;
              })}
              {/* count label atop the stack */}
              {s.totalTested > 0 && (
                <text x={cx(i)} y={yCount(s.totalTested) - 5} textAnchor="middle" fontSize={9.5}
                  fill="var(--ink-3)" className="mono">{s.totalTested}</text>
              )}
              {/* quarter label */}
              <text x={cx(i)} y={H - 11} textAnchor="middle" fontSize={9.5} fill="var(--ink-2)" className="mono">{s.quarter}</text>
            </g>
          );
        })}

        {/* pass-rate line + markers */}
        {ratePts.length > 1 && <path d={linePath} fill="none" stroke={RATE} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />}
        {ratePts.map(p => (
          <g key={p.i}>
            <circle cx={p.x} cy={p.y} r={3.6} fill="var(--surface-1)" stroke={RATE} strokeWidth={2} />
            <text x={p.x} y={p.y - 9} textAnchor="middle" fontSize={9.5} fontWeight={700} fill={RATE} className="mono">{pct(p.rate)}</text>
          </g>
        ))}
      </svg>
    </div>
  );
}

function Legend({ isAr }: { isAr: boolean }) {
  const items = [
    { c: PASS, l: isAr ? 'نجاح' : 'Passed' },
    { c: FAIL, l: isAr ? 'إخفاق' : 'Failed' },
    { c: GAP,  l: isAr ? 'ملاحظات / أخرى' : 'Gap / other' },
    { c: RATE, l: isAr ? 'نسبة النجاح' : 'Pass rate', line: true },
  ];
  return (
    <div style={{ display: 'flex', gap: 18, marginTop: 12, flexWrap: 'wrap' }}>
      {items.map(x => (
        <span key={x.l} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--ink-2)' }}>
          {x.line
            ? <span style={{ width: 16, height: 0, borderTop: `2px solid ${x.c}`, display: 'inline-block' }} />
            : <span style={{ width: 10, height: 10, borderRadius: 2, background: x.c }} />}
          {x.l}
        </span>
      ))}
    </div>
  );
}

// ─── DR-test coverage breakdown ─────────────────────────────────────────────
function CoverageCard({ snap, isAr }: { snap: DrTrendReport['snapshot']; isAr: boolean }) {
  const total = Math.max(1, snap.totalDrps);
  const segs = [
    { n: snap.testedRecently, c: 'var(--success)', l: isAr ? 'مُختبَرة حديثًا (≤ 12 شهرًا)' : 'Tested recently (≤ 12 mo)', Icon: ShieldCheck },
    { n: snap.testedOverdue,  c: 'var(--warning)', l: isAr ? 'متأخّرة (> 12 شهرًا)' : 'Overdue (> 12 mo)', Icon: CalendarClock },
    { n: snap.neverTested,    c: 'var(--ink-3)',   l: isAr ? 'لم تُختبر قط' : 'Never tested', Icon: HelpCircle },
  ];
  return (
    <Card title={isAr ? 'تغطية اختبار خطط التعافي' : 'DR-plan test coverage'} Icon={ShieldAlert}
      action={<span className="text-2xs text-muted">{snap.totalDrps} {isAr ? 'خطة' : 'plans'}</span>}>
      <div style={{ display: 'flex', height: 16, borderRadius: 'var(--r-sm)', overflow: 'hidden', background: 'var(--surface-2)' }}>
        {segs.map((s, k) => s.n > 0 && (
          <div key={k} style={{ width: `${(s.n / total) * 100}%`, background: s.c }}
            title={`${s.l}: ${s.n}`} />
        ))}
      </div>
      <div style={{ display: 'flex', gap: 20, marginTop: 14, flexWrap: 'wrap' }}>
        {segs.map((s, k) => (
          <div key={k} style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
            <s.Icon size={16} style={{ color: s.c }} />
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: 17, fontWeight: 700, color: 'var(--ink-1)', lineHeight: 1.1 }} className="mono">{s.n}</span>
              <span style={{ fontSize: 11.5, color: 'var(--ink-2)' }}>{s.l}</span>
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}

function rateColor(r: number): string {
  if (r >= 0.8) return 'var(--success-fg)';
  if (r >= 0.5) return 'var(--warning-fg)';
  return 'var(--danger-fg)';
}
