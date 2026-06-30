import { useCallback, useEffect, useState } from 'react';
import { Layers, Save } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { methodologyApi, riskImpactApi, ApiError } from '../../lib/api';

/**
 * ERM epic E-F — multi-dimensional impact. Lets an assessor score a risk's
 * impact across the org's configured dimensions; the worst (max) dimension
 * becomes the risk's inherent impact (drives L×I + level). Dimension list +
 * score ceiling come from the methodology config (GET /api/v1/risk/methodology).
 */
const DIM_LABEL_AR: Record<string, string> = {
  FINANCIAL: 'مالي', OPERATIONAL: 'تشغيلي', REGULATORY: 'تنظيمي', REPUTATIONAL: 'سمعة',
  CYBER: 'سيبراني', PRIVACY: 'خصوصية', SAFETY: 'سلامة', STRATEGIC: 'استراتيجي',
};

export function ImpactDimensionsPanel({ riskId, onSaved }: { riskId: number; onSaved?: () => void }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [dims, setDims] = useState<string[]>([]);
  const [max, setMax] = useState(5);
  const [scores, setScores] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [cfg, ratings] = await Promise.all([
        methodologyApi.get(),
        riskImpactApi.getDimensions(riskId),
      ]);
      setDims(cfg.impactDimensions);
      setMax(cfg.impactScaleMax);
      const s: Record<string, number> = {};
      ratings.dimensions.forEach(d => { if (d.score != null) s[d.dimension] = d.score; });
      setScores(s);
    } catch {
      setDims([]);
    } finally { setLoading(false); }
  }, [riskId]);
  useEffect(() => { void load(); }, [load]);

  const overall = Object.values(scores).filter(v => v > 0).reduce((a, b) => Math.max(a, b), 0);

  function setScore(dim: string, v: number) {
    setScores(prev => ({ ...prev, [dim]: v }));
  }

  async function save() {
    const entries = Object.entries(scores)
      .filter(([, v]) => v > 0)
      .map(([dimension, score]) => ({ dimension, score }));
    if (entries.length === 0) { toast.warning(isAr ? 'أدخل درجة واحدة على الأقل' : 'Score at least one dimension'); return; }
    setBusy(true);
    try {
      const res = await riskImpactApi.setDimensions(riskId, entries);
      toast.success(isAr
        ? `حُفظت الأبعاد — الأثر الكامن = ${res.overallImpact} (أسوأ بُعد)`
        : `Saved — inherent impact = ${res.overallImpact} (worst dimension)`);
      onSaved?.();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الحفظ' : 'Save failed'));
    } finally { setBusy(false); }
  }

  const label = (d: string) => isAr ? (DIM_LABEL_AR[d] ?? d) : d;

  return (
    <Card title={isAr ? 'أبعاد الأثر' : 'Impact dimensions'} Icon={Layers}
      action={<Badge tone={overall >= 4 ? 'danger' : overall >= 3 ? 'warning' : 'neutral'}>
        {isAr ? 'الأثر الكامن' : 'Inherent impact'}: {overall || '—'}
      </Badge>}>
      {loading ? (
        <div className="text-muted" style={{ fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
      ) : dims.length === 0 ? (
        <div className="text-muted" style={{ fontSize: 12 }}>
          {isAr ? 'تعذّر تحميل منهجية الأبعاد.' : 'Could not load the dimension methodology.'}
        </div>
      ) : (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 8 }}>
            {dims.map(d => (
              <label key={d} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                      gap: 8, border: '1px solid var(--border)', borderRadius: 6, padding: '6px 10px' }}>
                <span style={{ fontSize: 13 }}>{label(d)}</span>
                <select value={scores[d] ?? 0} onChange={e => setScore(d, Number(e.target.value))}
                        style={{ padding: '4px 6px', borderRadius: 6, border: '1px solid var(--border)',
                                 background: 'var(--surface)', color: 'var(--ink-1)', fontSize: 13 }}>
                  <option value={0}>—</option>
                  {Array.from({ length: max }, (_, i) => i + 1).map(n => (
                    <option key={n} value={n}>{n}</option>
                  ))}
                </select>
              </label>
            ))}
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 10 }}>
            <Button size="sm" variant="primary" Icon={Save} disabled={busy} onClick={() => void save()}>
              {busy ? (isAr ? 'جارٍ…' : 'Saving…') : (isAr ? 'حفظ الأبعاد' : 'Save dimensions')}
            </Button>
          </div>
        </>
      )}
    </Card>
  );
}
