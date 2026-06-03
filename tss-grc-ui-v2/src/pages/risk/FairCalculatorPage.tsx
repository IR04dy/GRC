import { useCallback, useEffect, useState } from 'react';
import { Calculator, Play, BarChart3, TrendingUp, Sigma, Activity } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { fairApi, ApiError, type FairInputs, type FairTriangular, type FairResult } from '../../lib/api';

const INPUT_STYLE: React.CSSProperties = {
  padding: '6px 8px', fontSize: 13, width: '100%', border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};

interface Preset { id: string; en: string; ar: string; inputs: Omit<FairInputs, 'scenarioName'>; }

const PRESETS: Preset[] = [
  { id: 'ransomware', en: 'Ransomware on core banking', ar: 'فدية على النظام البنكي الأساسي',
    inputs: { threatEventFrequency: { min: 0.5, mostLikely: 2, max: 6 }, vulnerability: { min: 0.1, mostLikely: 0.3, max: 0.6 },
      primaryLoss: { min: 500_000, mostLikely: 2_000_000, max: 8_000_000 }, secondaryLoss: { min: 1_000_000, mostLikely: 5_000_000, max: 25_000_000 }, iterations: 20_000 } },
  { id: 'pdpl', en: 'PDPL personal-data breach', ar: 'تسريب بيانات شخصية (PDPL)',
    inputs: { threatEventFrequency: { min: 0.2, mostLikely: 1, max: 3 }, vulnerability: { min: 0.2, mostLikely: 0.4, max: 0.7 },
      primaryLoss: { min: 300_000, mostLikely: 1_500_000, max: 5_000_000 }, secondaryLoss: { min: 2_000_000, mostLikely: 8_000_000, max: 40_000_000 }, iterations: 20_000 } },
  { id: 'ddos', en: 'DDoS on digital channels', ar: 'حجب الخدمة على القنوات الرقمية',
    inputs: { threatEventFrequency: { min: 2, mostLikely: 6, max: 15 }, vulnerability: { min: 0.1, mostLikely: 0.25, max: 0.5 },
      primaryLoss: { min: 100_000, mostLikely: 500_000, max: 2_000_000 }, secondaryLoss: { min: 200_000, mostLikely: 1_000_000, max: 6_000_000 }, iterations: 20_000 } },
  { id: 'insider', en: 'Insider data exfiltration', ar: 'تسريب داخلي للبيانات',
    inputs: { threatEventFrequency: { min: 0.1, mostLikely: 0.5, max: 2 }, vulnerability: { min: 0.3, mostLikely: 0.5, max: 0.8 },
      primaryLoss: { min: 200_000, mostLikely: 1_000_000, max: 4_000_000 }, secondaryLoss: { min: 1_000_000, mostLikely: 4_000_000, max: 18_000_000 }, iterations: 20_000 } },
];

const compact = (n: number) => new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(n);
const full = (n: number) => new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(n);

/**
 * FAIR quantitative-risk calculator — surfaces POST /api/v1/risk/fair/calculate.
 * A stateless Monte-Carlo engine over four triangular factors (TEF, vulnerability,
 * primary + secondary loss) producing the annualised loss-exposure (ALE)
 * distribution. Editable inputs + Saudi-native scenario presets + a tail-coloured
 * ALE histogram with p50 / p90 markers. Bilingual; currency normalised to SAR.
 */
export function FairCalculatorPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [scenarioName, setScenarioName] = useState(isAr ? PRESETS[0].ar : PRESETS[0].en);
  const [tef, setTef] = useState<FairTriangular>(PRESETS[0].inputs.threatEventFrequency);
  const [vuln, setVuln] = useState<FairTriangular>(PRESETS[0].inputs.vulnerability);
  const [primary, setPrimary] = useState<FairTriangular>(PRESETS[0].inputs.primaryLoss);
  const [secondary, setSecondary] = useState<FairTriangular>(PRESETS[0].inputs.secondaryLoss);
  const [iterations, setIterations] = useState(20_000);

  const [result, setResult] = useState<FairResult | null>(null);
  const [busy, setBusy] = useState(false);

  const sar = (n: number) => (isAr ? `${compact(n)} ﷼` : `SAR ${compact(n)}`);

  const applyPreset = (p: Preset) => {
    setScenarioName(isAr ? p.ar : p.en);
    setTef(p.inputs.threatEventFrequency); setVuln(p.inputs.vulnerability);
    setPrimary(p.inputs.primaryLoss); setSecondary(p.inputs.secondaryLoss);
    setIterations(p.inputs.iterations ?? 20_000);
  };

  const validTri = (t: FairTriangular) => t.min >= 0 && t.mostLikely >= t.min && t.max >= t.mostLikely;

  const calculate = useCallback(async (inputs: FairInputs) => {
    if (!inputs.scenarioName.trim()) { toast.danger(isAr ? 'اسم السيناريو مطلوب' : 'Scenario name is required'); return; }
    for (const [t, lbl] of [[inputs.threatEventFrequency, isAr ? 'تكرار التهديد' : 'TEF'], [inputs.vulnerability, isAr ? 'القابلية' : 'Vulnerability'],
      [inputs.primaryLoss, isAr ? 'الخسارة الأولية' : 'Primary loss'], [inputs.secondaryLoss, isAr ? 'الخسارة الثانوية' : 'Secondary loss']] as const) {
      if (!validTri(t as FairTriangular)) { toast.danger(isAr ? `قيم غير صالحة: ${lbl} (الحد الأدنى ≤ المرجّح ≤ الأقصى)` : `Invalid ${lbl}: min ≤ likely ≤ max`); return; }
    }
    if (inputs.vulnerability.max > 1) { toast.danger(isAr ? 'القابلية يجب ألا تتجاوز 1.0' : 'Vulnerability max must be ≤ 1.0'); return; }
    setBusy(true);
    try {
      setResult(await fairApi.calculate(inputs));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الحساب' : 'Calculation failed'));
    } finally { setBusy(false); }
  }, [isAr]);  // eslint-disable-line react-hooks/exhaustive-deps

  const run = () => void calculate({ scenarioName, threatEventFrequency: tef, vulnerability: vuln, primaryLoss: primary, secondaryLoss: secondary, iterations });

  // Auto-calculate the default scenario on first mount.
  useEffect(() => {
    void calculate({ scenarioName: isAr ? PRESETS[0].ar : PRESETS[0].en, ...PRESETS[0].inputs });
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps

  // Parse "lo..hi" histogram keys → sorted buckets.
  const buckets = result
    ? Object.entries(result.aleHistogram)
        .map(([k, count]) => { const [lo, hi] = k.split('..').map(Number); return { lo, hi, mid: (lo + hi) / 2, count }; })
        .sort((a, b) => a.lo - b.lo)
    : [];
  const maxCount = Math.max(1, ...buckets.map(b => b.count));
  const bucketColor = (mid: number) => {
    if (!result) return 'var(--primary)';
    if (mid >= result.aleP90) return 'var(--danger)';
    if (mid >= result.aleP50) return 'var(--warning)';
    return 'var(--success)';
  };

  const factors: { label: string; hint: string; value: FairTriangular; set: (t: FairTriangular) => void; step: number }[] = [
    { label: isAr ? 'تكرار حدث التهديد (TEF)' : 'Threat event frequency (TEF)', hint: isAr ? 'حدث / سنة' : 'events / year', value: tef, set: setTef, step: 0.1 },
    { label: isAr ? 'القابلية للتأثّر' : 'Vulnerability', hint: isAr ? 'احتمال [0–1]' : 'probability [0–1]', value: vuln, set: setVuln, step: 0.05 },
    { label: isAr ? 'الخسارة الأولية / حدث' : 'Primary loss / event', hint: isAr ? 'ريال' : 'SAR', value: primary, set: setPrimary, step: 100_000 },
    { label: isAr ? 'الخسارة الثانوية / حدث' : 'Secondary loss / event', hint: isAr ? 'ريال (غرامات + سمعة)' : 'SAR (fines + reputation)', value: secondary, set: setSecondary, step: 100_000 },
  ];

  return (
    <Shell title={isAr ? 'حاسبة FAIR' : 'FAIR calculator'} sub={isAr ? 'القياس الكمّي للمخاطر — Open FAIR' : 'Quantitative risk — Open FAIR'}>
      <PageHeader
        eyebrow={isAr ? 'المخاطر · كمّي · Open FAIR' : 'Risk · Quantitative · Open FAIR'}
        title={isAr ? 'حاسبة FAIR' : 'FAIR calculator'}
        sub={isAr
          ? 'محاكاة مونت كارلو لتقدير التعرّض السنوي للخسارة (ALE) من أربعة عوامل مثلّثية — وفق تصنيف Open FAIR. القيم بالريال السعودي.'
          : 'A Monte-Carlo simulation of annualised loss exposure (ALE) from four triangular factors, per the Open FAIR taxonomy. Amounts in SAR.'}
      />

      <Card title={isAr ? 'مدخلات السيناريو' : 'Scenario inputs'} Icon={Calculator}
        action={
          <select value="" onChange={e => { const p = PRESETS.find(x => x.id === e.target.value); if (p) applyPreset(p); e.currentTarget.value = ''; }}
            style={{ ...INPUT_STYLE, width: 'auto', fontSize: 12 }} aria-label={isAr ? 'سيناريو جاهز' : 'Preset scenario'}>
            <option value="">{isAr ? 'تحميل سيناريو جاهز…' : 'Load preset…'}</option>
            {PRESETS.map(p => <option key={p.id} value={p.id}>{isAr ? p.ar : p.en}</option>)}
          </select>
        }>
        <div style={{ marginBottom: 14 }}>
          <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)', display: 'block', marginBottom: 4 }}>{isAr ? 'اسم السيناريو' : 'Scenario name'}</label>
          <input type="text" value={scenarioName} onChange={e => setScenarioName(e.target.value)} style={{ ...INPUT_STYLE, maxWidth: 460 }} maxLength={200} />
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr 1fr 1fr', gap: 8, marginBottom: 4, maxWidth: 720 }}>
          <span />
          <span className="text-2xs text-muted" style={{ textAlign: 'center' }}>{isAr ? 'الأدنى' : 'Min'}</span>
          <span className="text-2xs text-muted" style={{ textAlign: 'center' }}>{isAr ? 'المرجّح' : 'Most likely'}</span>
          <span className="text-2xs text-muted" style={{ textAlign: 'center' }}>{isAr ? 'الأقصى' : 'Max'}</span>
        </div>
        {factors.map(f => (
          <div key={f.label} style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr 1fr 1fr', gap: 8, alignItems: 'center', marginBottom: 8, maxWidth: 720 }}>
            <div>
              <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>{f.label}</div>
              <div className="text-2xs text-muted">{f.hint}</div>
            </div>
            {(['min', 'mostLikely', 'max'] as const).map(k => (
              <input key={k} type="number" step={f.step} value={f.value[k]}
                onChange={e => f.set({ ...f.value, [k]: e.target.value === '' ? 0 : Number(e.target.value) })}
                style={{ ...INPUT_STYLE, textAlign: 'end' }} aria-label={`${f.label} ${k}`} />
            ))}
          </div>
        ))}

        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 14, flexWrap: 'wrap' }}>
          <label style={{ fontSize: 12, color: 'var(--ink-2)' }}>{isAr ? 'التكرارات' : 'Iterations'}</label>
          <select value={iterations} onChange={e => setIterations(Number(e.target.value))} style={{ ...INPUT_STYLE, width: 'auto', fontSize: 12 }}>
            {[1_000, 10_000, 20_000, 50_000, 100_000].map(n => <option key={n} value={n}>{compact(n)}</option>)}
          </select>
          <Button size="sm" variant="primary" Icon={Play} onClick={run} disabled={busy}>
            {busy ? (isAr ? 'جارٍ الحساب…' : 'Calculating…') : (isAr ? 'احسب' : 'Calculate')}
          </Button>
          <span className="text-2xs text-muted" style={{ marginInlineStart: 'auto' }}>
            ALE = (TEF × {isAr ? 'القابلية' : 'Vuln'}) × ({isAr ? 'أولية' : 'Primary'} + {isAr ? 'ثانوية' : 'Secondary'})
          </span>
        </div>
      </Card>

      {result && (
        <>
          <div className="kpi-grid" style={{ marginBottom: 16 }}>
            <Kpi Icon={Sigma}      label={isAr ? 'الوسيط ALE (p50)' : 'Median ALE (p50)'} value={sar(result.aleP50)} tone="primary" />
            <Kpi Icon={TrendingUp} label={isAr ? 'p90 ALE (الحالة السيئة)' : 'p90 ALE (worst-case)'} value={sar(result.aleP90)} tone="danger" />
            <Kpi Icon={Activity}   label={isAr ? 'المتوسط ALE' : 'Mean ALE'} value={sar(result.aleMean)} tone="warning" />
            <Kpi Icon={BarChart3}  label={isAr ? 'الأقصى ALE' : 'Max ALE'} value={sar(result.aleMax)} tone="neutral" />
          </div>

          <Card title={isAr ? 'توزيع التعرّض السنوي للخسارة' : 'Annualised loss-exposure distribution'} Icon={BarChart3}
            action={
              <span className="text-2xs text-muted">
                {isAr
                  ? `${full(result.iterations)} تكرار · LEF ${result.lefMean.toFixed(2)}/سنة · LM ${sar(result.lmMean)}`
                  : `${full(result.iterations)} iterations · LEF ${result.lefMean.toFixed(2)}/yr · LM ${sar(result.lmMean)}`}
              </span>
            }>
            {/* Histogram */}
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: 6, height: 200, padding: '8px 0 0' }}>
              {buckets.map((b, i) => (
                <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'flex-end', height: '100%' }}
                  title={`${sar(b.lo)} – ${sar(b.hi)}: ${full(b.count)}`}>
                  <span className="mono text-2xs text-muted" style={{ marginBottom: 3 }}>{b.count > 0 ? full(b.count) : ''}</span>
                  <div style={{ width: '100%', height: `${(b.count / maxCount) * 100}%`, minHeight: b.count > 0 ? 2 : 0,
                    background: bucketColor(b.mid), borderRadius: '3px 3px 0 0', transition: 'height .3s ease' }} />
                </div>
              ))}
            </div>
            <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
              {buckets.map((b, i) => (
                <span key={i} className="mono text-2xs text-muted" style={{ flex: 1, textAlign: 'center', whiteSpace: 'nowrap' }}>{compact(b.lo)}</span>
              ))}
            </div>
            {/* Legend */}
            <div style={{ display: 'flex', gap: 16, marginTop: 14, flexWrap: 'wrap' }}>
              {[
                { c: 'var(--success)', l: isAr ? 'أقل من الوسيط' : 'Below median' },
                { c: 'var(--warning)', l: isAr ? 'الوسيط ← p90' : 'Median → p90' },
                { c: 'var(--danger)', l: isAr ? 'الذيل (≥ p90)' : 'Tail (≥ p90)' },
              ].map(x => (
                <span key={x.l} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--ink-2)' }}>
                  <span style={{ width: 10, height: 10, borderRadius: 2, background: x.c }} />{x.l}
                </span>
              ))}
            </div>
          </Card>
        </>
      )}
    </Shell>
  );
}
