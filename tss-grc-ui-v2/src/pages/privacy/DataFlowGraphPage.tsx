import { useEffect, useMemo, useState } from 'react';
import { Share2, Globe2, ShieldAlert, FileText, Tag, RefreshCw } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { pdplDataFlowApi, ApiError, type DataFlowGraph, type DataFlowNode, type DataFlowEdge } from '../../lib/api';

/** Subset of the page's derived model that the SVG flow graph needs. */
interface FlowModel {
  cats: DataFlowNode[];
  acts: DataFlowNode[];
  countries: DataFlowNode[];
  catIndex: Map<string, number>;
  usedBy: DataFlowEdge[];
  transfers: DataFlowEdge[];
  isolatedActs: number;
}

/** Adequacy → tone. NONE = red (no protection), UNKNOWN/UNDOCUMENTED = amber, ADEQUATE = green. */
const adqColor = (a?: string) => {
  switch ((a ?? '').toUpperCase()) {
    case 'ADEQUATE': case 'ADEQUATE_DECISION': case 'WHITELIST': return 'var(--success)';
    case 'NONE': case 'NOT_ADEQUATE': return 'var(--danger)';
    case 'UNKNOWN': case 'UNDOCUMENTED': case '': return 'var(--warning)';
    default: return 'var(--info)';
  }
};
const catColor = (i: number) => `var(--cat-${(i % 8) + 1})`;
const trunc = (s: string, n = 26) => (s.length > n ? s.slice(0, n - 1) + '…' : s);

/**
 * PDPL data-flow graph — surfaces GET /api/v1/privacy/data-flow-graph.
 *
 * Renders the RoPA as the SDAIA inspection picture: a 3-lane node-link flow
 * (data category → processing activity → cross-border destination), a
 * cross-border transfer register (the DPO's key artefact: activity → country
 * [adequacy] via mechanism), and category / mechanism frequency bars.
 * Bilingual; cross-border edges coloured by adequacy.
 */
export function DataFlowGraphPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [graph, setGraph] = useState<DataFlowGraph | null>(null);
  const [loading, setLoading] = useState(true);
  const [hoverAct, setHoverAct] = useState<string | null>(null);

  const label = (n?: DataFlowNode) => (n ? (isAr ? (n.labelAr || n.labelEn) : n.labelEn) || n.labelEn : '—');

  const load = async () => {
    setLoading(true);
    try {
      setGraph(await pdplDataFlowApi.graph());
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل الرسم' : 'Failed to load graph'));
    } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);  // eslint-disable-line react-hooks/exhaustive-deps

  // ── Derive lanes + lookups ─────────────────────────────────────────
  const model = useMemo(() => {
    if (!graph) return null;
    const byId = new Map(graph.nodes.map(n => [n.id, n]));
    const cats = graph.nodes.filter(n => n.type === 'CATEGORY');
    const countries = graph.nodes.filter(n => n.type === 'COUNTRY');
    const catIndex = new Map(cats.map((c, i) => [c.id, i]));

    const usedBy = graph.edges.filter(e => e.relation === 'USED_BY');        // category → activity
    const transfers = graph.edges.filter(e => e.relation === 'TRANSFERS_TO'); // activity → country
    const governed = graph.edges.filter(e => e.relation === 'GOVERNED_BY');   // activity → mechanism
    const mechByActivity = new Map(governed.map(e => [e.source, byId.get(e.target)]));
    const adqByActivity = new Map(transfers.map(e => [e.source, e.label]));
    const countryByActivity = new Map(transfers.map(e => [e.source, byId.get(e.target)]));

    // In-flow activities = those touched by any edge (category or cross-border).
    const inFlowIds = new Set<string>();
    usedBy.forEach(e => inFlowIds.add(e.target));
    transfers.forEach(e => inFlowIds.add(e.source));
    const acts = graph.nodes.filter(n => n.type === 'ACTIVITY' && inFlowIds.has(n.id));
    const isolatedActs = graph.summary.activityCount - acts.length;

    // Cross-border transfer register rows.
    const register = transfers.map(e => {
      const act = byId.get(e.source);
      const country = byId.get(e.target);
      const mech = mechByActivity.get(e.source);
      return {
        actId: e.source,
        activity: label(act),
        legalBasis: act?.attrs?.legalBasis || '',
        special: String(act?.attrs?.specialCategoryFlag) === 'true',
        retention: act?.attrs?.retentionPeriodMonths || '',
        country: label(country),
        adequacy: (e.label || country?.attrs?.adequacy || 'UNKNOWN'),
        mechanism: mech ? label(mech) : (isAr ? 'غير موثق' : 'Undocumented'),
      };
    }).sort((a, b) => {
      // Riskiest first: non-adequate destinations on top.
      const rank = (x: string) => (adqColor(x) === 'var(--danger)' ? 0 : adqColor(x) === 'var(--warning)' ? 1 : 2);
      return rank(a.adequacy) - rank(b.adequacy);
    });

    return { byId, cats, countries, acts, catIndex, usedBy, transfers, mechByActivity, adqByActivity, countryByActivity, isolatedActs, register };
  }, [graph, isAr]);  // eslint-disable-line react-hooks/exhaustive-deps

  const specialCount = useMemo(
    () => (graph?.nodes.filter(n => n.type === 'ACTIVITY' && String(n.attrs?.specialCategoryFlag) === 'true').length ?? 0),
    [graph],
  );

  return (
    <Shell
      title={isAr ? 'خريطة تدفّق البيانات' : 'Data-flow graph'}
      sub={isAr ? 'تدفّق البيانات الشخصية — سجل المعالجة (RoPA)' : 'Personal-data flow — RoPA'}
    >
      <PageHeader
        eyebrow={isAr ? 'الخصوصية · نظام حماية البيانات الشخصية · هيئة البيانات (سدايا)' : 'Privacy · PDPL · SDAIA'}
        title={isAr ? 'خريطة تدفّق البيانات الشخصية' : 'Personal-data flow graph'}
        sub={isAr
          ? 'صورة المعالجة التي يحتاجها مسؤول حماية البيانات للتفتيش: من فئة البيانات ← نشاط المعالجة ← الوجهة عبر الحدود وآلية النقل، مبنية مباشرةً من سجل أنشطة المعالجة.'
          : 'The processing picture a DPO needs for inspection: data category → processing activity → cross-border destination and transfer mechanism, built straight from the Record of Processing Activities.'}
      />

      {loading && <Card title=""><div style={{ padding: 24, color: 'var(--ink-3)' }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div></Card>}

      {graph && model && (
        <>
          <div className="kpi-grid" style={{ marginBottom: 16 }}>
            <Kpi Icon={FileText}    label={isAr ? 'أنشطة المعالجة' : 'Processing activities'} value={graph.summary.activityCount} tone="primary" />
            <Kpi Icon={Globe2}      label={isAr ? 'تحويلات عبر الحدود' : 'Cross-border transfers'} value={graph.summary.crossBorderActivityCount} tone={graph.summary.crossBorderActivityCount > 0 ? 'warning' : 'success'} />
            <Kpi Icon={Share2}      label={isAr ? 'وجهات متمايزة' : 'Distinct destinations'} value={graph.summary.distinctDestinations} tone="info" />
            <Kpi Icon={ShieldAlert} label={isAr ? 'أنشطة بيانات حسّاسة' : 'Special-category activities'} value={specialCount} tone={specialCount > 0 ? 'danger' : 'success'} />
          </div>

          {/* ── 3-lane flow graph ──────────────────────────────────── */}
          <Card title={isAr ? 'الرسم التدفّقي' : 'Flow graph'} Icon={Share2}
            action={<span className="text-2xs text-muted">{isAr ? 'فئة ← نشاط ← وجهة' : 'category → activity → destination'}</span>}>
            <FlowGraph model={model} isAr={isAr} hoverAct={hoverAct} setHoverAct={setHoverAct} label={label} />
            <div style={{ display: 'flex', gap: 16, marginTop: 12, flexWrap: 'wrap' }}>
              {[
                { c: 'var(--danger)', l: isAr ? 'دولة غير ملائمة' : 'Non-adequate destination' },
                { c: 'var(--warning)', l: isAr ? 'ملاءمة غير معروفة' : 'Adequacy unknown' },
                { c: 'var(--success)', l: isAr ? 'دولة ملائمة' : 'Adequate destination' },
              ].map(x => (
                <span key={x.l} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--ink-2)' }}>
                  <span style={{ width: 14, height: 3, borderRadius: 2, background: x.c }} />{x.l}
                </span>
              ))}
              {model.isolatedActs > 0 && (
                <span className="text-2xs text-muted" style={{ marginInlineStart: 'auto' }}>
                  {isAr ? `+ ${model.isolatedActs} نشاط محلي بلا فئة حسّاسة أو نقل` : `+ ${model.isolatedActs} domestic activities with no special-category or cross-border flow`}
                </span>
              )}
            </div>
          </Card>

          {/* ── Cross-border transfer register ─────────────────────── */}
          {model.register.length > 0 && (
            <Card title={isAr ? 'سجل التحويلات عبر الحدود' : 'Cross-border transfer register'} Icon={Globe2}>
              <div style={{ overflowX: 'auto' }}>
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th>{isAr ? 'النشاط' : 'Activity'}</th>
                      <th>{isAr ? 'الأساس النظامي' : 'Legal basis'}</th>
                      <th>{isAr ? 'الوجهة' : 'Destination'}</th>
                      <th>{isAr ? 'الملاءمة' : 'Adequacy'}</th>
                      <th>{isAr ? 'آلية النقل' : 'Mechanism'}</th>
                      <th>{isAr ? 'الاحتفاظ' : 'Retention'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {model.register.map((r, i) => (
                      <tr key={i}>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                            <span style={{ fontSize: 12.5, color: 'var(--ink-1)' }}>{r.activity}</span>
                            {r.special && <span className="badge-sm" style={{ background: 'var(--danger-bg)', color: 'var(--danger-fg)', fontWeight: 700 }}>{isAr ? 'حسّاسة' : 'Special'}</span>}
                          </div>
                        </td>
                        <td className="text-2xs text-muted">{(r.legalBasis || '—').replace(/_/g, ' ')}</td>
                        <td>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12.5 }}>
                            <Globe2 size={13} style={{ color: 'var(--ink-3)' }} />{r.country}
                          </span>
                        </td>
                        <td>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 11.5, fontWeight: 600 }}>
                            <span style={{ width: 9, height: 9, borderRadius: 2, background: adqColor(r.adequacy) }} />
                            {(r.adequacy || 'UNKNOWN').replace(/_/g, ' ')}
                          </span>
                        </td>
                        <td className="text-2xs">{r.mechanism}</td>
                        <td className="mono text-2xs text-muted">{r.retention ? `${r.retention}${isAr ? ' ش' : ' mo'}` : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          )}

          {/* ── Frequency bars ─────────────────────────────────────── */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 16 }}>
            <Card title={isAr ? 'تعرّض فئات البيانات الحسّاسة' : 'Special-category exposure'} Icon={Tag}>
              <FreqBars data={graph.summary.categoryFrequency} colorBy="cat" empty={isAr ? 'لا فئات حسّاسة مُسجّلة' : 'No special categories recorded'} />
            </Card>
            <Card title={isAr ? 'آليات النقل' : 'Transfer mechanisms'} Icon={RefreshCw}>
              <FreqBars data={graph.summary.mechanismFrequency} colorBy="info" empty={isAr ? 'لا آليات نقل' : 'No transfer mechanisms'} />
            </Card>
          </div>
        </>
      )}
    </Shell>
  );
}

/* ── 3-lane layered SVG flow: CATEGORY → ACTIVITY → COUNTRY ──────────── */
function FlowGraph({ model, isAr, hoverAct, setHoverAct, label }: {
  model: FlowModel;
  isAr: boolean;
  hoverAct: string | null;
  setHoverAct: (id: string | null) => void;
  label: (n?: DataFlowNode) => string;
}) {
  const PAD = 12, NODE_H = 32, GAP = 12, LANE_GAP = 84;
  const CAT_W = 128, ACT_W = 230, CO_W = 150;
  const { cats, acts, countries, catIndex, usedBy, transfers } = model;

  const laneH = (n: number) => (n === 0 ? 0 : n * NODE_H + (n - 1) * GAP);
  const contentH = Math.max(laneH(cats.length), laneH(acts.length), laneH(countries.length), NODE_H);
  const svgH = contentH + 2 * PAD;
  const catX = PAD, actX = PAD + CAT_W + LANE_GAP, coX = PAD + CAT_W + LANE_GAP + ACT_W + LANE_GAP;
  const svgW = coX + CO_W + PAD;

  // RTL mirrors x.
  const mx = (x: number, w: number) => (isAr ? svgW - x - w : x);
  const yOf = (i: number, n: number) => PAD + (contentH - laneH(n)) / 2 + i * (NODE_H + GAP) + NODE_H / 2;

  const catIdx = (id: string) => cats.findIndex(c => c.id === id);
  const actIdx = (id: string) => acts.findIndex(a => a.id === id);
  const coIdx = (id: string) => countries.findIndex(c => c.id === id);

  // Edge endpoint x (facing side depends on RTL).
  const rightOf = (x: number, w: number) => (isAr ? mx(x, w) : mx(x, w) + w);
  const leftOf = (x: number, w: number) => (isAr ? mx(x, w) + w : mx(x, w));
  const dir = isAr ? -1 : 1;

  const bez = (x0: number, y0: number, x1: number, y1: number) => {
    const cp = LANE_GAP * 0.5;
    return `M ${x0} ${y0} C ${x0 + dir * cp} ${y0}, ${x1 - dir * cp} ${y1}, ${x1} ${y1}`;
  };

  const dim = (actId: string) => hoverAct !== null && hoverAct !== actId;

  return (
    <div style={{ overflowX: 'auto' }}>
      <svg width={svgW} height={svgH} viewBox={`0 0 ${svgW} ${svgH}`} style={{ maxWidth: '100%' }} role="img"
        aria-label={isAr ? 'رسم تدفّق البيانات' : 'Data-flow graph'}>
        {/* USED_BY ribbons: category → activity */}
        {usedBy.map((e, i) => {
          const ci = catIdx(e.source), ai = actIdx(e.target);
          if (ci < 0 || ai < 0) return null;
          const x0 = rightOf(catX, CAT_W), y0 = yOf(ci, cats.length);
          const x1 = leftOf(actX, ACT_W), y1 = yOf(ai, acts.length);
          return <path key={`u${i}`} d={bez(x0, y0, x1, y1)} fill="none"
            stroke={catColor(catIndex.get(e.source) ?? ci)} strokeOpacity={dim(e.target) ? 0.08 : 0.4}
            strokeWidth={2} style={{ transition: 'stroke-opacity .15s' }} />;
        })}
        {/* TRANSFERS_TO ribbons: activity → country (coloured by adequacy) */}
        {transfers.map((e, i) => {
          const ai = actIdx(e.source), oi = coIdx(e.target);
          if (ai < 0 || oi < 0) return null;
          const x0 = rightOf(actX, ACT_W), y0 = yOf(ai, acts.length);
          const x1 = leftOf(coX, CO_W), y1 = yOf(oi, countries.length);
          return <path key={`t${i}`} d={bez(x0, y0, x1, y1)} fill="none"
            stroke={adqColor(e.label ?? undefined)} strokeOpacity={dim(e.source) ? 0.08 : 0.7}
            strokeWidth={dim(e.source) ? 2 : 2.5} style={{ transition: 'stroke-opacity .15s' }} />;
        })}

        {/* Category nodes */}
        {cats.map((c, i) => {
          const y = yOf(i, cats.length) - NODE_H / 2, x = mx(catX, CAT_W);
          const col = catColor(catIndex.get(c.id) ?? i);
          return (
            <g key={c.id}>
              <rect x={x} y={y} width={CAT_W} height={NODE_H} rx={7} fill="var(--surface-1)" stroke={col} strokeWidth={1.5} />
              <rect x={x} y={y} width={4} height={NODE_H} rx={2} fill={col} />
              <text x={x + CAT_W / 2} y={y + NODE_H / 2} textAnchor="middle" dominantBaseline="central"
                style={{ font: '600 11px var(--font-sans)', fill: 'var(--ink-1)' }}>{trunc(label(c), 16)}</text>
            </g>
          );
        })}
        {/* Activity nodes */}
        {acts.map((a, i) => {
          const y = yOf(i, acts.length) - NODE_H / 2, x = mx(actX, ACT_W);
          const special = String(a.attrs?.specialCategoryFlag) === 'true';
          return (
            <g key={a.id} onMouseEnter={() => setHoverAct(a.id)} onMouseLeave={() => setHoverAct(null)} style={{ cursor: 'default' }}>
              <rect x={x} y={y} width={ACT_W} height={NODE_H} rx={7}
                fill={hoverAct === a.id ? 'var(--surface-2)' : 'var(--surface-1)'}
                stroke={special ? 'var(--danger)' : 'var(--border)'} strokeWidth={special ? 1.6 : 1}
                opacity={dim(a.id) ? 0.4 : 1} style={{ transition: 'opacity .15s, fill .15s' }} />
              <text x={isAr ? x + ACT_W - 10 : x + 10} y={y + NODE_H / 2} textAnchor={isAr ? 'end' : 'start'} dominantBaseline="central"
                opacity={dim(a.id) ? 0.4 : 1}
                style={{ font: '600 11.5px var(--font-sans)', fill: 'var(--ink-1)' }}>{trunc(label(a), 30)}</text>
            </g>
          );
        })}
        {/* Country nodes */}
        {countries.map((c, i) => {
          const y = yOf(i, countries.length) - NODE_H / 2, x = mx(coX, CO_W);
          const col = adqColor(c.attrs?.adequacy);
          return (
            <g key={c.id}>
              <rect x={x} y={y} width={CO_W} height={NODE_H} rx={7} fill="var(--surface-1)" stroke={col} strokeWidth={1.5} />
              <rect x={isAr ? x : x + CO_W - 4} y={y} width={4} height={NODE_H} rx={2} fill={col} />
              <text x={x + CO_W / 2} y={y + NODE_H / 2} textAnchor="middle" dominantBaseline="central"
                style={{ font: '600 11px var(--font-sans)', fill: 'var(--ink-1)' }}>{trunc(label(c), 18)}</text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}

/* Horizontal frequency bars from a {key: count} map. */
function FreqBars({ data, colorBy, empty }: { data: Record<string, number>; colorBy: 'cat' | 'info'; empty: string }) {
  const entries = Object.entries(data).sort((a, b) => b[1] - a[1]);
  if (entries.length === 0) return <div style={{ padding: '14px 4px', color: 'var(--ink-3)', fontSize: 12.5 }}>{empty}</div>;
  const max = Math.max(1, ...entries.map(([, v]) => v));
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {entries.map(([k, v], i) => (
        <div key={k} style={{ display: 'grid', gridTemplateColumns: '120px 1fr 28px', alignItems: 'center', gap: 8 }}>
          <span className="text-2xs" style={{ color: 'var(--ink-2)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{k.replace(/_/g, ' ')}</span>
          <div style={{ height: 8, borderRadius: 4, background: 'var(--surface-2)', overflow: 'hidden' }}>
            <div style={{ width: `${(v / max) * 100}%`, height: '100%', background: colorBy === 'cat' ? catColor(i) : 'var(--info)' }} />
          </div>
          <span className="mono text-2xs text-muted" style={{ textAlign: 'end' }}>{v}</span>
        </div>
      ))}
    </div>
  );
}
