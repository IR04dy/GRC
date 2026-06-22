import { useCallback, useEffect, useMemo, useState } from 'react';
import { Network, Play, Zap, AlertTriangle, Layers, Clock, GitBranch, ShieldAlert } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  bcmApi, dependencyCascadeApi, CASCADE_BANDS, ApiError,
  type CascadeResult, type CascadeImpactRow,
} from '../../lib/api';

const INPUT_STYLE: React.CSSProperties = {
  padding: '6px 8px', fontSize: 13, width: '100%', border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};

const CRIT_COLOR: Record<string, string> = {
  CRITICAL: 'var(--risk-crit)', HIGH: 'var(--risk-high)', MEDIUM: 'var(--risk-med)',
  LOW: 'var(--risk-low)', VERY_LOW: 'var(--risk-vlow)',
};
const critColor = (c?: string) => CRIT_COLOR[(c ?? '').toUpperCase()] ?? 'var(--ink-3)';

type Node = { id: number; nameEn: string; nameAr?: string; criticality?: string };

/**
 * BCM dependency-cascade simulator — surfaces
 * GET /api/v1/bcm/dependency-cascade/{failedProcessId}.
 *
 * A BCM manager picks a critical process and asks "if this fails now, which
 * downstream processes are dragged down, when, and which breach their MTPD?"
 * The backend BFS-walks bcm.process_dependencies and buckets impact at the
 * standard 1h / 4h / 8h / 24h recovery bands, decaying confidence with cascade
 * depth. This page renders the time-band blast-radius + a per-process impact
 * register. Bilingual; severity uses the shared --risk-* ramp.
 */
export function DependencyCascadePage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [nodes, setNodes] = useState<Node[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [result, setResult] = useState<CascadeResult | null>(null);
  const [loadingNodes, setLoadingNodes] = useState(true);
  const [busy, setBusy] = useState(false);

  const critLabel = useCallback((c?: string) => {
    const k = (c ?? '').toUpperCase();
    if (isAr) return ({ CRITICAL: 'حرجة', HIGH: 'عالية', MEDIUM: 'متوسطة', LOW: 'منخفضة', VERY_LOW: 'منخفضة جدًا' } as Record<string, string>)[k] ?? (c ?? '—');
    return ({ CRITICAL: 'Critical', HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low', VERY_LOW: 'Very low' } as Record<string, string>)[k] ?? (c ?? '—');
  }, [isAr]);

  const name = useCallback((en?: string, ar?: string) => (isAr ? (ar || en) : en) || en || '—', [isAr]);

  const simulate = useCallback(async (pid: number) => {
    setBusy(true);
    try {
      const r = await dependencyCascadeApi.simulate(pid);
      setResult(r);
      if (r.breachedCount > 0) {
        toast.danger(isAr ? `${r.breachedCount} عملية تتجاوز MTPD` : `${r.breachedCount} process${r.breachedCount === 1 ? '' : 'es'} breach MTPD`);
      }
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : (isAr ? 'فشلت المحاكاة' : 'Simulation failed');
      toast.danger(msg);
      setResult(null);
    } finally { setBusy(false); }
  }, [isAr]);  // eslint-disable-line react-hooks/exhaustive-deps

  // Load the source-process list, default to the most-impactful root cause.
  useEffect(() => {
    let live = true;
    (async () => {
      try {
        const g = await bcmApi.dependencyGraph();
        if (!live) return;
        const ns = (g.nodes ?? []) as Node[];
        setNodes(ns);
        if (ns.length) {
          // Prefer an infrastructure/core root cause, else first CRITICAL, else first.
          const infra = ns.find(n => /infrastructure|بنية/i.test(n.nameEn) || /infrastructure|بنية/i.test(n.nameAr ?? ''));
          const crit = ns.find(n => (n.criticality ?? '').toUpperCase() === 'CRITICAL');
          const def = infra ?? crit ?? ns[0];
          setSelectedId(def.id);
          void simulate(def.id);
        }
      } catch (e) {
        toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل العمليات' : 'Failed to load processes'));
      } finally {
        if (live) setLoadingNodes(false);
      }
    })();
    return () => { live = false; };
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps

  const run = () => { if (selectedId != null) void simulate(selectedId); };

  const impacts = result?.impacts ?? [];
  const maxDepth = impacts.reduce((m, r) => Math.max(m, r.depth), 0);
  const maxReach = impacts.reduce((m, r) => Math.max(m, r.reachHour), 0);

  // Time-band columns (cumulative impacted/breached at each band).
  const bands = useMemo(() => {
    if (!result) return [];
    const peak = Math.max(1, result.totalImpactedCount);
    return CASCADE_BANDS.map(h => {
      const b = result.byHourBand?.[String(h)] ?? { impacted: 0, breached: 0 };
      return { h, impacted: b.impacted, breached: b.breached, ok: Math.max(0, b.impacted - b.breached), pct: (b.impacted / peak) * 100 };
    });
  }, [result]);

  const depthLabel = (d: number) =>
    isAr ? `المستوى ${d}` : (d === 1 ? 'Direct' : `Tier ${d}`);
  const confTone = (c: number) => (c >= 0.75 ? 'var(--success)' : c >= 0.5 ? 'var(--warning)' : 'var(--ink-3)');

  return (
    <Shell
      title={isAr ? 'محاكاة تتالي الاعتمادية' : 'Dependency-cascade simulator'}
      sub={isAr ? 'الأثر المتدرّج لتعطّل عملية حرجة' : 'Downstream blast-radius of a process failure'}
    >
      <PageHeader
        eyebrow={isAr ? 'استمرارية الأعمال · ساما BCM / ISO 22301' : 'Continuity · SAMA BCM / ISO 22301'}
        title={isAr ? 'محاكاة تتالي الاعتمادية' : 'Dependency-cascade simulator'}
        sub={isAr
          ? 'اختر عملية حرجة وافترض تعطّلها الآن: تتبّع المحاكاة الاعتماديات النازلة وتوزّع الأثر على نطاقات التعافي (1 / 4 / 8 / 24 ساعة)، مع تمييز ما يتجاوز أقصى مدة تعطّل مقبولة (MTPD) وثقة تتناقص مع عمق التتالي.'
          : 'Pick a critical process and assume it fails now: the simulation walks the downstream dependencies and spreads the impact across the recovery time-bands (1 / 4 / 8 / 24h), flagging what breaches its Maximum Tolerable Period of Disruption (MTPD), with confidence that decays as the cascade deepens.'}
      />

      <Card title={isAr ? 'العملية المتعطّلة' : 'Failed process'} Icon={Zap}>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: 280 }}>
            <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)', display: 'block', marginBottom: 4 }}>
              {isAr ? 'اختر العملية الحرجة' : 'Select critical process'}
            </label>
            <select
              value={selectedId ?? ''} disabled={loadingNodes}
              onChange={e => { const id = Number(e.target.value); setSelectedId(id); void simulate(id); }}
              style={INPUT_STYLE} aria-label={isAr ? 'العملية المتعطّلة' : 'Failed process'}
            >
              {loadingNodes && <option>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</option>}
              {nodes.map(n => (
                <option key={n.id} value={n.id}>
                  {name(n.nameEn, n.nameAr)}{n.criticality ? ` — ${critLabel(n.criticality)}` : ''}
                </option>
              ))}
            </select>
          </div>
          <Button size="sm" variant="primary" Icon={busy ? Network : Play} onClick={run} disabled={busy || selectedId == null}>
            {busy ? (isAr ? 'جارٍ المحاكاة…' : 'Simulating…') : (isAr ? 'حاكِ التعطّل' : 'Simulate failure')}
          </Button>
        </div>
        {result && (
          <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 8, fontSize: 12.5, color: 'var(--ink-2)' }}>
            <span style={{ width: 9, height: 9, borderRadius: 2, background: critColor(result.sourceCriticality), flexShrink: 0 }} />
            <strong style={{ color: 'var(--ink-1)' }}>{name(result.sourceProcessNameEn, result.sourceProcessNameAr)}</strong>
            <span className="text-2xs text-muted">· {critLabel(result.sourceCriticality)} · {isAr ? 'مصدر التتالي' : 'cascade source'}</span>
          </div>
        )}
      </Card>

      {result && (
        <>
          <div className="kpi-grid" style={{ marginBottom: 16 }}>
            <Kpi Icon={Layers}        label={isAr ? 'عمليات متأثّرة' : 'Processes impacted'} value={result.totalImpactedCount} tone={result.totalImpactedCount > 0 ? 'primary' : 'neutral'} to="/bcm/dependency" />
            <Kpi Icon={ShieldAlert}   label={isAr ? 'تجاوزات MTPD' : 'MTPD breaches'} value={result.breachedCount} tone={result.breachedCount > 0 ? 'danger' : 'success'} />
            <Kpi Icon={Clock}         label={isAr ? 'أقصى زمن وصول' : 'Furthest reach'} value={maxReach ? `${maxReach}h` : '—'} tone="warning" />
            <Kpi Icon={GitBranch}     label={isAr ? 'أقصى عمق تتالي' : 'Deepest tier'} value={maxDepth || '—'} tone="info" />
          </div>

          {/* Time-band blast radius */}
          <Card title={isAr ? 'نطاق الأثر عبر زمن التعافي' : 'Blast radius across recovery time'} Icon={Clock}
            action={<span className="text-2xs text-muted">{isAr ? 'تراكمي · متأثّر / متجاوز' : 'cumulative · impacted / breached'}</span>}>
            {result.totalImpactedCount === 0 ? (
              <div style={{ padding: '24px 8px', textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
                {isAr ? 'لا اعتماديات نازلة — هذه العملية لا تُغذّي عمليات أخرى.' : 'No downstream dependencies — nothing consumes this process.'}
              </div>
            ) : (
              <>
                <div style={{ display: 'flex', alignItems: 'flex-end', gap: 16, height: 180, padding: '8px 0 0' }}>
                  {bands.map(b => (
                    <div key={b.h} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'flex-end', height: '100%' }}
                      title={`${isAr ? 'حتى' : 'by'} ${b.h}h: ${b.impacted} ${isAr ? 'متأثّر' : 'impacted'} · ${b.breached} ${isAr ? 'متجاوز' : 'breached'}`}>
                      <span className="mono text-2xs" style={{ marginBottom: 3, color: 'var(--ink-2)' }}>{b.impacted}{b.breached > 0 ? ` · ${b.breached}` : ''}</span>
                      <div style={{ width: '100%', maxWidth: 80, height: `${b.pct}%`, minHeight: b.impacted > 0 ? 4 : 0, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', borderRadius: '4px 4px 0 0', overflow: 'hidden', background: 'var(--surface-2)' }}>
                        {b.breached > 0 && <div style={{ height: `${(b.breached / Math.max(1, b.impacted)) * 100}%`, background: 'var(--danger)' }} />}
                        {b.ok > 0 && <div style={{ height: `${(b.ok / Math.max(1, b.impacted)) * 100}%`, background: 'var(--warning)' }} />}
                      </div>
                    </div>
                  ))}
                </div>
                <div style={{ display: 'flex', gap: 16, marginTop: 6 }}>
                  {bands.map(b => (
                    <span key={b.h} className="mono text-2xs text-muted" style={{ flex: 1, textAlign: 'center' }}>≤ {b.h}h</span>
                  ))}
                </div>
                <div style={{ display: 'flex', gap: 16, marginTop: 14, flexWrap: 'wrap' }}>
                  {[
                    { c: 'var(--danger)', l: isAr ? 'يتجاوز MTPD' : 'Breaches MTPD' },
                    { c: 'var(--warning)', l: isAr ? 'متأثّر ضمن الحد' : 'Impacted, within tolerance' },
                  ].map(x => (
                    <span key={x.l} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--ink-2)' }}>
                      <span style={{ width: 10, height: 10, borderRadius: 2, background: x.c }} />{x.l}
                    </span>
                  ))}
                </div>
              </>
            )}
          </Card>

          {/* Impact register */}
          {impacts.length > 0 && (
            <Card title={isAr ? 'سجلّ العمليات المتأثّرة' : 'Impacted-process register'} Icon={AlertTriangle}>
              <div style={{ overflowX: 'auto' }}>
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th>{isAr ? 'العملية' : 'Process'}</th>
                      <th>{isAr ? 'المستوى' : 'Tier'}</th>
                      <th>{isAr ? 'زمن الوصول' : 'Reach'}</th>
                      <th>{isAr ? 'MTPD / RTO' : 'MTPD / RTO'}</th>
                      <th>{isAr ? 'الحالة' : 'Status'}</th>
                      <th>{isAr ? 'الثقة' : 'Confidence'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {impacts.map((r: CascadeImpactRow) => (
                      <tr key={r.processId}>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                            <span style={{ width: 9, height: 9, borderRadius: 2, background: critColor(r.criticality), flexShrink: 0 }} title={critLabel(r.criticality)} />
                            <span style={{ fontSize: 12.5, color: 'var(--ink-1)' }}>{name(r.processNameEn, r.processNameAr)}</span>
                          </div>
                        </td>
                        <td><span className="badge-sm" style={{ background: 'var(--surface-2)', color: 'var(--ink-2)' }}>{depthLabel(r.depth)}</span></td>
                        <td className="mono text-2xs">{r.reachHour}h</td>
                        <td className="mono text-2xs text-muted">{r.mtpdHours ?? '—'}h / {r.rtoHours ?? '—'}h</td>
                        <td>
                          {r.breached
                            ? <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 11.5, fontWeight: 700, color: 'var(--danger-fg)' }}><ShieldAlert size={13} />{isAr ? 'تجاوز MTPD' : 'MTPD breach'}</span>
                            : <span style={{ fontSize: 11.5, color: 'var(--ink-3)' }}>{isAr ? 'ضمن الحد' : 'Within tolerance'}</span>}
                        </td>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <div style={{ width: 56, height: 5, borderRadius: 3, background: 'var(--surface-2)', overflow: 'hidden' }}>
                              <div style={{ width: `${Math.round(r.confidence * 100)}%`, height: '100%', background: confTone(r.confidence) }} />
                            </div>
                            <span className="mono text-2xs text-muted">{Math.round(r.confidence * 100)}%</span>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="text-2xs text-muted" style={{ marginTop: 10, marginBottom: 0 }}>
                {isAr
                  ? 'تقدير الزمن حسب عمق التتالي (مستوى 1 = 1 س، 2 = 4 س، 3 = 8 س، أعمق = 24 س)؛ الثقة تتناقص مع العمق.'
                  : 'Reach is estimated from cascade depth (tier 1 = 1h, 2 = 4h, 3 = 8h, deeper = 24h); confidence decays with depth.'}
              </p>
            </Card>
          )}
        </>
      )}
    </Shell>
  );
}
