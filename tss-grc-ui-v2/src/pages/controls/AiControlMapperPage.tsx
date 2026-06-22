import { useCallback, useEffect, useRef, useState } from 'react';
import { Sparkles, Target, ScanSearch, Layers, Gauge, Wand2 } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  aiControlMapApi, ApiError,
  type ControlSuggestResponse, type ControlSuggestion,
} from '../../lib/api';

const INPUT_STYLE: React.CSSProperties = {
  padding: '6px 8px', fontSize: 13, border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};

// domain → categorical colour for the chip
const DOMAIN_COLOR: Record<string, string> = {
  CYBER: 'var(--cat-1)', BCM: 'var(--cat-2)', PRIVACY: 'var(--cat-3)',
  TPRM: 'var(--cat-4)', RISK: 'var(--cat-5)', COMPLIANCE: 'var(--cat-6)',
  IAM: 'var(--cat-7)',
};
const domColor = (d?: string) => DOMAIN_COLOR[(d ?? '').toUpperCase()] ?? 'var(--cat-8)';

const DOMAINS = ['', 'CYBER', 'BCM', 'PRIVACY', 'TPRM', 'RISK', 'COMPLIANCE'];
const TOPN_OPTIONS = [5, 10, 20];

interface Preset { en: string; ar: string; q: string; }
const PRESETS: Preset[] = [
  { en: 'MFA & least-privilege access', ar: 'التحقق متعدد العوامل وأقل صلاحية',
    q: 'multi-factor authentication least privilege role-based access recertification' },
  { en: 'Cross-border personal-data transfer', ar: 'النقل الحدودي للبيانات الشخصية',
    q: 'cross-border transfer of personal data outside the Kingdom adequacy SDAIA' },
  { en: 'DR exercise & RTO/RPO', ar: 'تمرين التعافي ووقت/نقطة الاستعادة',
    q: 'SAMA business continuity disaster recovery exercise testing RTO RPO MTPD' },
  { en: 'Material outsourcing to SAMA', ar: 'الإسناد الجوهري لساما',
    q: 'material outsourcing vendor due diligence SAMA notification exit plan' },
  { en: 'Ransomware backup & recovery', ar: 'النسخ والاستعادة من برامج الفدية',
    q: 'ransomware backup immutable copies restoration testing recover data' },
  { en: 'SOC / SIEM monitoring', ar: 'مراقبة مركز العمليات وسيم',
    q: 'security operations centre SIEM 24x7 monitoring log retention detect incidents' },
];

const confTone = (c: number) => (c >= 0.35 ? 'var(--success)' : c >= 0.15 ? 'var(--info)' : 'var(--ink-3)');
const pctRaw = (v: number) => `${Math.round(v * 100)}%`;

/**
 * AI-assisted control mapper — surfaces POST /api/v1/controls/ai/suggest.
 *
 * A control owner / compliance analyst pastes a free-text risk, finding,
 * audit observation or regulatory requirement and gets the top-N matching
 * controls from the unified library, each with a confidence score and a
 * plain-language rationale (which Saudi-regulatory keywords and shared terms
 * drove the match). The backend scorer is deterministic (Jaccard + Saudi
 * keyword boost + code-mention) — auditable and reproducible for a SAMA/NCA
 * examiner, no LLM black-box. Bilingual; domains coloured categorically.
 */
export function AiControlMapperPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [description, setDescription] = useState('');
  const [domain, setDomain] = useState('');
  const [topN, setTopN] = useState(10);
  const [result, setResult] = useState<ControlSuggestResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [searched, setSearched] = useState(false);
  const taRef = useRef<HTMLTextAreaElement>(null);

  const run = useCallback(async (descOverride?: string, domOverride?: string) => {
    const desc = (descOverride ?? description).trim();
    if (!desc) {
      toast.danger(isAr ? 'اكتب وصفًا أولًا' : 'Enter a description first');
      taRef.current?.focus();
      return;
    }
    setBusy(true);
    setSearched(true);
    try {
      const r = await aiControlMapApi.suggest({
        description: desc,
        domain: (domOverride ?? domain) || undefined,
        topN,
      });
      setResult(r);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : (isAr ? 'فشل الاقتراح' : 'Suggestion failed');
      toast.danger(msg);
      setResult(null);
    } finally { setBusy(false); }
  }, [description, domain, topN, isAr]);  // eslint-disable-line react-hooks/exhaustive-deps

  // Seed the page with a representative query so it isn't empty on first load.
  useEffect(() => {
    const seed = PRESETS[2].q;  // DR exercise & RTO/RPO — a strong, multi-hit demo
    setDescription(seed);
    void run(seed, '');
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps

  const usePreset = (p: Preset) => { setDescription(p.q); void run(p.q, domain); };

  const suggestions = result?.suggestions ?? [];
  const topConf = suggestions.reduce((m, s) => Math.max(m, s.confidence), 0);

  const name = (en: string, ar?: string | null) => (isAr ? (ar || en) : en) || en;
  const domLabel = (d: string) => {
    if (!d) return isAr ? 'كل المجالات' : 'All domains';
    const map: Record<string, string> = isAr
      ? { CYBER: 'الأمن السيبراني', BCM: 'استمرارية الأعمال', PRIVACY: 'الخصوصية', TPRM: 'مخاطر الأطراف الثالثة', RISK: 'المخاطر', COMPLIANCE: 'الالتزام' }
      : { CYBER: 'Cyber', BCM: 'BCM', PRIVACY: 'Privacy', TPRM: 'TPRM', RISK: 'Risk', COMPLIANCE: 'Compliance' };
    return map[d] ?? d;
  };

  return (
    <Shell
      title={isAr ? 'مُخطِّط الضوابط الذكي' : 'AI control mapper'}
      sub={isAr ? 'اقترح الضوابط المطابقة من المكتبة الموحّدة' : 'Suggest matching controls from the unified library'}
    >
      <PageHeader
        eyebrow={isAr ? 'الضوابط · ربط NCA / ساما / PDPL' : 'Controls · NCA / SAMA / PDPL mapping'}
        title={isAr ? 'مُخطِّط الضوابط الذكي' : 'AI control mapper'}
        sub={isAr
          ? 'الصق وصفًا حرًّا لخطر أو ملاحظة تدقيق أو متطلب تنظيمي، ويعيد المحرّك أعلى الضوابط المطابقة من المكتبة الموحّدة مع درجة ثقة وتبرير واضح. التقييم حتمي (تطابق الكلمات + تعزيز المصطلحات التنظيمية السعودية) — قابل للتدقيق والتكرار أمام مُفتّش ساما أو الهيئة، دون صندوق أسود.'
          : 'Paste a free-text risk, audit finding or regulatory requirement and the engine returns the top matching controls from the unified library, each with a confidence score and a plain-language rationale. Scoring is deterministic (token overlap + Saudi-regulatory keyword boost) — auditable and reproducible for a SAMA/NCA examiner, no black box.'}
      />

      <Card title={isAr ? 'صف المتطلب أو الخطر' : 'Describe the requirement or risk'} Icon={Wand2}>
        <textarea
          ref={taRef}
          value={description}
          onChange={e => setDescription(e.target.value)}
          onKeyDown={e => { if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); void run(); } }}
          rows={3}
          placeholder={isAr
            ? 'مثال: التحقق متعدد العوامل وأقل صلاحية للوصول إلى النظام البنكي الأساسي…'
            : 'e.g. multi-factor authentication and least-privilege access to the core-banking system…'}
          style={{ ...INPUT_STYLE, width: '100%', resize: 'vertical', lineHeight: 1.5, fontFamily: 'var(--font-sans)' }}
          aria-label={isAr ? 'وصف المتطلب' : 'Requirement description'}
        />
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12, flexWrap: 'wrap', marginTop: 12 }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>
            {isAr ? 'المجال' : 'Domain'}
            <select value={domain} onChange={e => setDomain(e.target.value)} style={{ ...INPUT_STYLE, minWidth: 150 }}
              aria-label={isAr ? 'تصفية المجال' : 'Domain filter'}>
              {DOMAINS.map(d => <option key={d || 'all'} value={d}>{domLabel(d)}</option>)}
            </select>
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>
            {isAr ? 'عدد النتائج' : 'Results'}
            <select value={topN} onChange={e => setTopN(Number(e.target.value))} style={{ ...INPUT_STYLE, minWidth: 90 }}
              aria-label={isAr ? 'عدد النتائج' : 'Number of results'}>
              {TOPN_OPTIONS.map(n => <option key={n} value={n}>{isAr ? `أعلى ${n}` : `Top ${n}`}</option>)}
            </select>
          </label>
          <Button size="md" variant="primary" Icon={busy ? ScanSearch : Sparkles} onClick={() => void run()} disabled={busy}>
            {busy ? (isAr ? 'جارٍ التحليل…' : 'Analysing…') : (isAr ? 'اقترح الضوابط' : 'Suggest controls')}
          </Button>
          <span className="text-2xs text-muted" style={{ marginInlineStart: 'auto' }}>
            {isAr ? 'Ctrl+Enter للتشغيل' : 'Ctrl+Enter to run'}
          </span>
        </div>

        {/* preset chips */}
        <div style={{ display: 'flex', gap: 8, marginTop: 14, flexWrap: 'wrap' }}>
          <span className="text-2xs text-muted" style={{ alignSelf: 'center' }}>{isAr ? 'أمثلة:' : 'Examples:'}</span>
          {PRESETS.map(p => (
            <button key={p.en} onClick={() => usePreset(p)} type="button" className="badge-sm"
              style={{ cursor: 'pointer', border: '1px solid var(--border)', background: 'var(--surface-2)', color: 'var(--ink-2)' }}>
              {isAr ? p.ar : p.en}
            </button>
          ))}
        </div>
      </Card>

      {searched && result && (
        <>
          <div className="kpi-grid" style={{ marginBottom: 16 }}>
            <Kpi Icon={Target} label={isAr ? 'ضوابط مطابقة' : 'Controls matched'} value={result.totalScored} tone={result.totalScored > 0 ? 'primary' : 'neutral'} to="/controls/library" />
            <Kpi Icon={Gauge}  label={isAr ? 'أعلى ثقة' : 'Top confidence'} value={topConf > 0 ? pctRaw(topConf) : '—'} tone={topConf >= 0.35 ? 'success' : topConf >= 0.15 ? 'info' : 'neutral'} />
            <Kpi Icon={Layers} label={isAr ? 'معروضة' : 'Shown'} value={suggestions.length} tone="info" />
          </div>

          <Card
            title={isAr ? 'الضوابط المقترحة' : 'Suggested controls'}
            Icon={Sparkles}
            action={<span className="text-2xs text-muted">{isAr ? `مرتّبة حسب الثقة · ${domLabel(domain)}` : `ranked by confidence · ${domLabel(domain)}`}</span>}
          >
            {suggestions.length === 0 ? (
              <div style={{ padding: '28px 8px', textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
                {isAr
                  ? 'لا ضوابط مطابقة — جرّب وصفًا أكثر تحديدًا أو أزِل تصفية المجال.'
                  : 'No matching controls — try a more specific description or clear the domain filter.'}
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {suggestions.map((s: ControlSuggestion, i) => (
                  <div key={s.controlId} style={{
                    display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 14, alignItems: 'center',
                    padding: '12px 14px', border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                    background: i === 0 ? 'var(--surface-2)' : 'var(--surface-1)',
                  }}>
                    {/* rank + confidence ring */}
                    <ConfRing rank={i + 1} confidence={s.confidence} color={confTone(s.confidence)} />

                    {/* identity + rationale */}
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                        <code className="mono text-2xs" style={{ color: 'var(--ink-1)', fontWeight: 700 }}>{s.controlCode}</code>
                        <span className="badge-sm" style={{ background: domColor(s.domain), color: 'var(--ink-0)' }}>{s.domain}</span>
                      </div>
                      <div style={{ fontSize: 13, color: 'var(--ink-1)', marginTop: 4, fontWeight: 600 }}>{name(s.titleEn, s.titleAr)}</div>
                      <div className="text-2xs text-muted" style={{ marginTop: 4, lineHeight: 1.5 }}>{s.rationale}</div>
                    </div>

                    {/* scores */}
                    <div style={{ textAlign: isAr ? 'left' : 'right', minWidth: 120 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: isAr ? 'flex-start' : 'flex-end' }}>
                        <div style={{ width: 64, height: 6, borderRadius: 3, background: 'var(--surface-2)', overflow: 'hidden' }}>
                          <div style={{ width: pctRaw(topConf > 0 ? s.confidence / topConf : 0), height: '100%', background: confTone(s.confidence) }} />
                        </div>
                        <span className="mono text-2xs" style={{ color: confTone(s.confidence), fontWeight: 700, minWidth: 34, textAlign: isAr ? 'left' : 'right' }}>{pctRaw(s.confidence)}</span>
                      </div>
                      <div className="mono text-2xs text-muted" style={{ marginTop: 3 }}>
                        {isAr ? 'تطابق' : 'overlap'} {s.jaccard.toFixed(3)}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
            <p className="text-2xs text-muted" style={{ marginTop: 12, marginBottom: 0 }}>
              {isAr
                ? 'الثقة = تطابق Jaccard للكلمات × (1 + تعزيز الكلمات السعودية) + تعزيز ذكر رمز الضابط. شريط النسبة يُعرض نسبةً إلى أعلى نتيجة؛ الرقم هو الثقة المطلقة.'
                : 'Confidence = Jaccard token overlap × (1 + Saudi-keyword boost) + control-code-mention boost. The bar is shown relative to the top hit; the number is the absolute confidence.'}
            </p>
          </Card>
        </>
      )}
    </Shell>
  );
}

// ─── Rank badge + confidence ring ───────────────────────────────────────────
function ConfRing({ rank, confidence, color }: { rank: number; confidence: number; color: string }) {
  const r = 18, c = 2 * Math.PI * r;
  const pctClamped = Math.max(0, Math.min(1, confidence));
  return (
    <div style={{ position: 'relative', width: 44, height: 44, flexShrink: 0 }}>
      <svg width={44} height={44} viewBox="0 0 44 44" style={{ transform: 'rotate(-90deg)' }} aria-hidden>
        <circle cx={22} cy={22} r={r} fill="none" stroke="var(--surface-2)" strokeWidth={3.5} />
        <circle cx={22} cy={22} r={r} fill="none" stroke={color} strokeWidth={3.5} strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={c * (1 - pctClamped)} />
      </svg>
      <span style={{
        position: 'absolute', inset: 0, display: 'grid', placeItems: 'center',
        fontSize: 13, fontWeight: 700, color: 'var(--ink-1)',
      }} className="mono">{rank}</span>
    </div>
  );
}
