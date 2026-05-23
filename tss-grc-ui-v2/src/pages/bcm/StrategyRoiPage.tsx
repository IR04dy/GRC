import { useEffect, useMemo, useState } from 'react';
import {
  TrendingUp, ChevronLeft, AlertTriangle, CheckCircle2, Clock,
  XCircle, FileText, Banknote, Sigma,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { ApiError } from '../../lib/api';
import {
  listStrategyRoi,
  recommendStrategyRoi,
  archiveStrategyRoi,
  type StrategyRoiAnalysis,
  type Recommendation,
  STRATEGY_TYPE_LABELS,
  RECOMMENDATION_LABELS,
  recommendationTone,
  recommendationBarColor,
  fmtSar,
  fmtPercent,
  fmtPaybackMonths,
} from '../../lib/strategyRoi';

/**
 * Slice 9-D — Continuity Strategy ROI register.
 *
 * <p>Board-pack landing page for the CFO and Risk Committee. Shows every
 * proposed continuity strategy attached to a BIA with its monetised
 * justification:
 * <ul>
 *   <li>Avoidable loss over planning horizon, total cost, ROI %, NPV at
 *       SAMA 8% reference rate, payback months</li>
 *   <li>Side-by-side comparison (table) when 2+ rows are selected</li>
 *   <li>ROI bar chart — each strategy as a horizontal bar, coloured by
 *       recommendation status</li>
 *   <li>"Recommend" actions for CFO/CISO/PLATFORM_ADMIN — set APPROVED,
 *       DEFERRED or REJECTED with bilingual rationale</li>
 * </ul>
 *
 * <p>Saudi-native phrasing throughout: "تحليل العائد على الاستثمار
 * لاستراتيجيات الاستمرارية" (not literal "العائد على الاستثمار" alone).
 * SAR currency only.
 *
 * <p>Optional URL filter: {@code ?biaId=NN} narrows the list to a single
 * BIA's strategies. The page reads {@code window.location.search} once
 * on mount — no react-router params plumbing needed.
 */
export function StrategyRoiPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const dialog = useDialog();

  const [items, setItems] = useState<StrategyRoiAnalysis[]>([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [biaFilter, setBiaFilter] = useState<number | null>(null);

  // ─── Load ──────────────────────────────────────────────────────────
  useEffect(() => {
    // Read ?biaId= from the URL once
    const params = new URLSearchParams(window.location.search);
    const v = params.get('biaId');
    if (v != null) {
      const n = Number(v);
      if (Number.isFinite(n)) setBiaFilter(n);
    }
  }, []);

  async function reload() {
    setLoading(true);
    try {
      const list = await listStrategyRoi(biaFilter ?? undefined);
      setItems(Array.isArray(list) ? list : []);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل تحليلات العائد على الاستثمار'
                : 'Failed to load ROI analyses'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, [biaFilter]); // eslint-disable-line react-hooks/exhaustive-deps

  function toggleSelect(id: number) {
    setSelected(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  // ─── Computed: max ROI for bar scaling, KPIs ─────────────────────
  const maxRoi = useMemo(() => {
    let m = 0;
    for (const it of items) {
      const r = Number(it.roiPercent);
      if (Number.isFinite(r) && r > m) m = r;
    }
    return m;
  }, [items]);

  const approvedCount = items.filter(i => i.recommendation === 'APPROVED').length;
  const pendingCount  = items.filter(i => i.recommendation === 'PENDING_REVIEW').length;
  const deferredCount = items.filter(i => i.recommendation === 'DEFERRED').length;

  const selectedItems = useMemo(
    () => items.filter(i => selected.has(i.id)),
    [items, selected],
  );

  // ─── Actions ──────────────────────────────────────────────────────
  async function decide(item: StrategyRoiAnalysis, decision: Recommendation) {
    const who = await dialog.prompt({
      title: isAr ? 'اسم متخذ القرار' : 'Decided by (name)',
      placeholder: isAr ? 'مثال: فيصل السبيعي — المدير المالي للمجموعة'
        : 'e.g. Faisal Al-Subaie — Group CFO',
      required: true,
    });
    if (who === null) return;
    const rationaleEn = await dialog.prompt({
      title: 'Rationale (English)', type: 'textarea',
      placeholder: 'Why this recommendation? Cite BIA numbers + regulatory drivers.',
    });
    if (rationaleEn === null) return;
    const rationaleAr = await dialog.prompt({
      title: 'المبرّر (عربي)', type: 'textarea',
      placeholder: 'لماذا هذه التوصية؟ استند للأرقام الكمية ومتطلبات الجهات التنظيمية.',
    });
    if (rationaleAr === null) return;
    try {
      await recommendStrategyRoi(item.externalId, {
        recommendation: decision,
        decidedByName: who,
        recommendationRationaleEn: rationaleEn,
        recommendationRationaleAr: rationaleAr,
      });
      toast.success(isAr ? 'تم تسجيل التوصية' : 'Recommendation recorded');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function archive(item: StrategyRoiAnalysis) {
    const ok = await dialog.confirm({
      title: isAr ? 'أرشفة هذا التحليل؟' : 'Archive this ROI analysis?',
      body: isAr
        ? 'لن يَظهر في القوائم النشطة. يمكن استرجاعه من قاعدة البيانات.'
        : 'It will no longer appear in active lists. Recoverable from the database.',
      destructive: true,
    });
    if (!ok) return;
    try {
      await archiveStrategyRoi(item.externalId);
      toast.success(isAr ? 'تمت الأرشفة' : 'Archived');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  function clearBiaFilter() {
    setBiaFilter(null);
    const url = new URL(window.location.href);
    url.searchParams.delete('biaId');
    window.history.replaceState({}, '', url.toString());
  }

  // ─── Render ───────────────────────────────────────────────────────
  return (
    <Shell>
      <PageHeader
        title={isAr
          ? 'تحليل العائد على الاستثمار لاستراتيجيات الاستمرارية'
          : 'Continuity Strategy ROI'}
        sub={isAr
          ? 'المبرّر المالي الذي يطلبه المجلس لكل استراتيجية استمرارية — العائد، صافي القيمة الحالية، فترة الاسترداد. مرتبط بتحليل الأثر الكمي (BIA).'
          : 'The board-grade financial justification for every continuity strategy — ROI, NPV, payback. Layered on top of the Quantitative BIA.'}
      />

      {biaFilter != null && (
        <Card>
          <div className="cluster" style={{ alignItems: 'center', justifyContent: 'space-between' }}>
            <div>
              <Badge tone="info">
                {isAr ? `مُصفّى حسب تحليل الأثر #${biaFilter}` : `Filtered by BIA #${biaFilter}`}
              </Badge>
            </div>
            <Button variant="secondary" Icon={ChevronLeft} onClick={clearBiaFilter}>
              {isAr ? 'إزالة التصفية' : 'Clear filter'}
            </Button>
          </div>
        </Card>
      )}

      <div className="kpi-grid-sm" style={{ marginBottom: 'var(--space-md)' }}>
        <Kpi
          Icon={Sigma}
          label={isAr ? 'إجمالي التحليلات' : 'Total analyses'}
          value={String(items.length)}
        />
        <Kpi
          Icon={CheckCircle2}
          label={isAr ? 'مُعتمَدة' : 'Approved'}
          value={String(approvedCount)}
          tone="success"
        />
        <Kpi
          Icon={Clock}
          label={isAr ? 'بانتظار المراجعة' : 'Pending review'}
          value={String(pendingCount)}
          tone="info"
        />
        <Kpi
          Icon={AlertTriangle}
          label={isAr ? 'مُؤجَّلة' : 'Deferred'}
          value={String(deferredCount)}
          tone="warning"
        />
      </div>

      {/* ROI bar chart */}
      {items.length > 0 && (
        <Card
          title={isAr ? 'مقارنة العائد على الاستثمار' : 'ROI comparison chart'}
          Icon={TrendingUp}
        >
          <div className="stack" style={{ gap: 'var(--space-sm)' }}>
            {items.map(it => {
              const roi = Number(it.roiPercent);
              const widthPct = maxRoi > 0
                ? Math.max(2, Math.min(100, (roi / maxRoi) * 100))
                : 2;
              return (
                <div key={it.id}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                    <span style={{ fontSize: 13 }}>
                      <strong>{it.analysisCode}</strong>
                      {' — '}
                      <span style={{ color: 'var(--text-muted)' }}>
                        {isAr ? STRATEGY_TYPE_LABELS[it.strategyType].ar
                              : STRATEGY_TYPE_LABELS[it.strategyType].en}
                      </span>
                    </span>
                    <span style={{ fontSize: 13, fontWeight: 600 }}>
                      {fmtPercent(it.roiPercent)}
                    </span>
                  </div>
                  <div style={{
                    height: 18,
                    background: 'var(--surface-2, #f3f4f6)',
                    borderRadius: 4,
                    overflow: 'hidden',
                  }}>
                    <div style={{
                      width: `${widthPct}%`,
                      height: '100%',
                      background: recommendationBarColor(it.recommendation),
                      transition: 'width 0.3s ease',
                    }} />
                  </div>
                </div>
              );
            })}
          </div>
        </Card>
      )}

      {/* Comparison view when 2+ selected */}
      {selectedItems.length >= 2 && (
        <Card title={isAr ? 'مقارنة جانب لجانب' : 'Side-by-side comparison'}>
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table" style={{ minWidth: 800 }}>
              <thead>
                <tr>
                  <th>{isAr ? 'البند' : 'Metric'}</th>
                  {selectedItems.map(it => (
                    <th key={it.id}>{it.analysisCode}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>{isAr ? 'النوع' : 'Strategy type'}</td>
                  {selectedItems.map(it => (
                    <td key={it.id}>
                      {isAr ? STRATEGY_TYPE_LABELS[it.strategyType].ar
                            : STRATEGY_TYPE_LABELS[it.strategyType].en}
                    </td>
                  ))}
                </tr>
                <tr>
                  <td>{isAr ? 'الانقطاع المُستهدَف (ساعات)' : 'Expected RTO (h)'}</td>
                  {selectedItems.map(it => (
                    <td key={it.id}>{it.expectedRtoHours}</td>
                  ))}
                </tr>
                <tr>
                  <td>{isAr ? 'النفقات الرأسمالية' : 'Capex'}</td>
                  {selectedItems.map(it => (
                    <td key={it.id}>{fmtSar(it.capexSar, isAr)}</td>
                  ))}
                </tr>
                <tr>
                  <td>{isAr ? 'النفقات التشغيلية السنوية' : 'Opex / yr'}</td>
                  {selectedItems.map(it => (
                    <td key={it.id}>{fmtSar(it.opexAnnualSar, isAr)}</td>
                  ))}
                </tr>
                <tr>
                  <td>{isAr ? 'إجمالي التكلفة' : 'Total cost'}</td>
                  {selectedItems.map(it => (
                    <td key={it.id}><strong>{fmtSar(it.totalCostSar, isAr)}</strong></td>
                  ))}
                </tr>
                <tr>
                  <td>{isAr ? 'الخسارة القابلة للتجنُّب' : 'Avoidable loss'}</td>
                  {selectedItems.map(it => (
                    <td key={it.id}><strong>{fmtSar(it.totalAvoidableLossSar, isAr)}</strong></td>
                  ))}
                </tr>
                <tr>
                  <td>{isAr ? 'العائد على الاستثمار' : 'ROI %'}</td>
                  {selectedItems.map(it => (
                    <td key={it.id}><strong>{fmtPercent(it.roiPercent)}</strong></td>
                  ))}
                </tr>
                <tr>
                  <td>{isAr ? 'فترة الاسترداد' : 'Payback period'}</td>
                  {selectedItems.map(it => (
                    <td key={it.id}>{fmtPaybackMonths(it.paybackPeriodMonths, isAr)}</td>
                  ))}
                </tr>
                <tr>
                  <td>{isAr ? 'صافي القيمة الحالية (8%)' : 'NPV @ 8%'}</td>
                  {selectedItems.map(it => (
                    <td key={it.id}>{fmtSar(it.npvSar, isAr)}</td>
                  ))}
                </tr>
                <tr>
                  <td>{isAr ? 'التوصية' : 'Recommendation'}</td>
                  {selectedItems.map(it => (
                    <td key={it.id}>
                      <Badge tone={recommendationTone(it.recommendation)}>
                        {isAr ? RECOMMENDATION_LABELS[it.recommendation].ar
                              : RECOMMENDATION_LABELS[it.recommendation].en}
                      </Badge>
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Main register table */}
      <Card title={isAr ? 'سجل تحليلات العائد' : 'ROI register'} Icon={Banknote}>
        {loading ? (
          <p style={{ color: 'var(--text-muted)' }}>
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </p>
        ) : items.length === 0 ? (
          <InlineEmpty
            text={isAr
              ? 'لا توجد تحليلات بعد. ابدأ بإنشاء تحليل عائد لكل استراتيجية استمرارية مرتبطة بتحليل أثر كمي.'
              : 'No analyses yet. Create an ROI analysis for each continuity strategy attached to a Quantitative BIA.'}
          />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table" style={{ minWidth: 1100 }}>
              <thead>
                <tr>
                  <th style={{ width: 40 }}></th>
                  <th>{isAr ? 'الرمز' : 'Code'}</th>
                  <th>{isAr ? 'الاستراتيجية' : 'Strategy'}</th>
                  <th>{isAr ? 'النوع' : 'Type'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'الانقطاع المُستهدَف' : 'RTO (h)'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'إجمالي التكلفة' : 'Total cost'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'الخسارة المُتجنَّبة' : 'Avoidable loss'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'العائد %' : 'ROI %'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'فترة الاسترداد' : 'Payback'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'صافي القيمة الحالية' : 'NPV'}</th>
                  <th>{isAr ? 'التوصية' : 'Recommendation'}</th>
                  <th>{isAr ? 'الإجراءات' : 'Actions'}</th>
                </tr>
              </thead>
              <tbody>
                {items.map(it => (
                  <tr key={it.id}>
                    <td>
                      <input
                        type="checkbox"
                        checked={selected.has(it.id)}
                        onChange={() => toggleSelect(it.id)}
                        aria-label={isAr ? 'للمقارنة' : 'Compare'}
                      />
                    </td>
                    <td><strong>{it.analysisCode}</strong></td>
                    <td>{isAr ? it.strategyNameAr : it.strategyNameEn}</td>
                    <td>
                      {isAr ? STRATEGY_TYPE_LABELS[it.strategyType].ar
                            : STRATEGY_TYPE_LABELS[it.strategyType].en}
                    </td>
                    <td style={{ textAlign: 'end' }}>{it.expectedRtoHours}</td>
                    <td style={{ textAlign: 'end' }}>{fmtSar(it.totalCostSar, isAr)}</td>
                    <td style={{ textAlign: 'end' }}>{fmtSar(it.totalAvoidableLossSar, isAr)}</td>
                    <td style={{ textAlign: 'end' }}><strong>{fmtPercent(it.roiPercent)}</strong></td>
                    <td style={{ textAlign: 'end' }}>{fmtPaybackMonths(it.paybackPeriodMonths, isAr)}</td>
                    <td style={{ textAlign: 'end' }}>{fmtSar(it.npvSar, isAr)}</td>
                    <td>
                      <Badge tone={recommendationTone(it.recommendation)}>
                        {isAr ? RECOMMENDATION_LABELS[it.recommendation].ar
                              : RECOMMENDATION_LABELS[it.recommendation].en}
                      </Badge>
                    </td>
                    <td>
                      <div className="cluster" style={{ gap: 4 }}>
                        {it.recommendation === 'PENDING_REVIEW' || it.recommendation === 'DEFERRED' ? (
                          <>
                            <Button size="sm" variant="primary" Icon={CheckCircle2}
                                    onClick={() => void decide(it, 'APPROVED')}>
                              {isAr ? 'اعتمد' : 'Approve'}
                            </Button>
                            {it.recommendation === 'PENDING_REVIEW' && (
                              <Button size="sm" variant="secondary" Icon={Clock}
                                      onClick={() => void decide(it, 'DEFERRED')}>
                                {isAr ? 'أجِّل' : 'Defer'}
                              </Button>
                            )}
                            <Button size="sm" variant="danger" Icon={XCircle}
                                    onClick={() => void decide(it, 'REJECTED')}>
                              {isAr ? 'ارفض' : 'Reject'}
                            </Button>
                          </>
                        ) : (
                          <Button size="sm" variant="secondary" Icon={FileText}
                                  onClick={() => void decide(it, 'PENDING_REVIEW')}>
                            {isAr ? 'أعد الفتح' : 'Re-open'}
                          </Button>
                        )}
                        <Button size="sm" variant="ghost" onClick={() => void archive(it)}>
                          {isAr ? 'أرشف' : 'Archive'}
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </Shell>
  );
}
