import { useEffect, useMemo, useState } from 'react';
import {
  Factory, Target, BarChart3, CheckCircle2,
  Send, Archive, TrendingDown,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listEmissions, transitionEmission,
  listTargets, getTargetProgress, getEmissionsTotal,
  ASSET_CLASS_LABELS, EMISSIONS_STAGE_LABELS, ALIGNMENT_LABELS,
  emissionsStageTone, dataQualityTone,
  fmtTco2e, fmtSarMillions, fmtPercent,
  type FinancedEmissionsRecord, type FinancedEmissionsTarget,
  type TargetProgress, type EmissionsLifecycleStage,
  type FinancedEmissionsTotal,
} from '../../lib/climate';

type Tab = 'records' | 'targets' | 'trend';

/**
 * Sprint 11 — Financed Emissions (Scope-3 Cat-15 / PCAF).
 *
 * <p>Tabs:
 * <ul>
 *   <li><b>Records</b> — per asset-class × year, with PCAF data-quality
 *       score (1-5, 1 best) and lifecycle workflow.</li>
 *   <li><b>Targets</b> — net-zero + interim reduction commitments,
 *       aligned to SBTi 1.5°C / NZBA / custom.</li>
 *   <li><b>Trend chart</b> — total tCO₂e per year (simple bar
 *       visualisation against the latest target).</li>
 * </ul>
 *
 * <p>Saudi-native title throughout: "الانبعاثات المُموَّلة" (NOT literal
 * "الانبعاثات الممولة").
 */
export function FinancedEmissionsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [tab, setTab] = useState<Tab>('records');
  const [records, setRecords] = useState<FinancedEmissionsRecord[]>([]);
  const [targets, setTargets] = useState<FinancedEmissionsTarget[]>([]);
  const [progress, setProgress] = useState<TargetProgress[]>([]);
  const [total, setTotal] = useState<FinancedEmissionsTotal | null>(null);
  const [loading, setLoading] = useState(false);
  const currentYear = 2026;

  async function reload() {
    setLoading(true);
    try {
      const [recs, tgts, prog, tot] = await Promise.all([
        listEmissions(),
        listTargets(),
        getTargetProgress(),
        getEmissionsTotal(currentYear).catch(() => null),
      ]);
      setRecords(Array.isArray(recs) ? recs : []);
      setTargets(Array.isArray(tgts) ? tgts : []);
      setProgress(Array.isArray(prog) ? prog : []);
      setTotal(tot);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل الانبعاثات المُموَّلة' : 'Failed to load financed emissions'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const totalPublished = useMemo(
    () => records.filter(r => r.lifecycleStage === 'PUBLISHED').length,
    [records],
  );

  // Group by year for the trend tab.
  const byYear = useMemo(() => {
    const map = new Map<number, number>();
    for (const r of records) {
      if (r.lifecycleStage !== 'APPROVED' && r.lifecycleStage !== 'PUBLISHED') continue;
      const v = Number(r.totalFinancedEmissionsTco2e);
      if (!Number.isFinite(v)) continue;
      map.set(r.reportingYear, (map.get(r.reportingYear) ?? 0) + v);
    }
    return [...map.entries()].sort((a, b) => a[0] - b[0]);
  }, [records]);

  async function changeStage(r: FinancedEmissionsRecord, to: EmissionsLifecycleStage) {
    try {
      await transitionEmission(r.externalId, to);
      toast.success(isAr ? `الحالة: ${EMISSIONS_STAGE_LABELS[to].ar}` : `Stage: ${EMISSIONS_STAGE_LABELS[to].en}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'الانبعاثات المُموَّلة' : 'Financed Emissions (PCAF Scope-3)'}
        sub={isAr
          ? 'النطاق 3 الفئة 15 وفق منهجية PCAF — انبعاثات المحفظة الائتمانية الموزَّعة على فئات الأصول لتقرير IFRS S2.'
          : 'PCAF Scope-3 Category 15 — credit-portfolio emissions attributed across asset classes for IFRS S2 reporting.'}
      />

      <div className="kpi-grid-sm" style={{ marginBottom: 'var(--space-md)' }}>
        <Kpi
          Icon={Factory}
          label={isAr ? 'إجمالي الانبعاثات المُموَّلة' : 'Total financed emissions'}
          value={fmtTco2e(total?.totalFinancedEmissionsTco2e)}
          tone="info"
        />
        <Kpi
          Icon={Target}
          label={isAr ? 'الأهداف المسجَّلة' : 'Targets registered'}
          value={String(targets.length)}
          tone="success"
        />
        <Kpi
          Icon={CheckCircle2}
          label={isAr ? 'السجلّات المنشورة' : 'Records published'}
          value={String(totalPublished)}
          tone="success"
        />
        <Kpi
          Icon={BarChart3}
          label={isAr ? 'متوسّط جودة البيانات (PCAF)' : 'Average PCAF data quality'}
          value={total ? Number(total.weightedAverageDataQuality).toFixed(1) : '—'}
          tone={total && Number(total.weightedAverageDataQuality) <= 2.5 ? 'success' : 'warning'}
        />
      </div>

      <Card>
        <div className="cluster" style={{ gap: 'var(--space-sm)', marginBottom: 'var(--space-md)' }}>
          <Button variant={tab === 'records' ? 'primary' : 'secondary'} onClick={() => setTab('records')}>
            {isAr ? 'السجلّات' : 'Records'}
          </Button>
          <Button variant={tab === 'targets' ? 'primary' : 'secondary'} onClick={() => setTab('targets')}>
            {isAr ? 'الأهداف' : 'Targets'}
          </Button>
          <Button variant={tab === 'trend' ? 'primary' : 'secondary'} onClick={() => setTab('trend')}>
            {isAr ? 'الاتّجاه الزمني' : 'Trend'}
          </Button>
        </div>

        {loading && <p>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</p>}

        {!loading && tab === 'records' && (
          records.length === 0 ? (
            <InlineEmpty text={isAr ? 'لا توجد سجلّات انبعاثات بعد' : 'No emissions records yet'} />
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    <th style={th}>{isAr ? 'السنة' : 'Year'}</th>
                    <th style={th}>{isAr ? 'فئة الأصل' : 'Asset class'}</th>
                    <th style={th}>{isAr ? 'القائم' : 'Outstanding'}</th>
                    <th style={th}>{isAr ? 'عامل الإسناد' : 'Attribution'}</th>
                    <th style={th}>{isAr ? 'جودة البيانات' : 'Quality'}</th>
                    <th style={th}>{isAr ? 'الإجمالي' : 'Total'}</th>
                    <th style={th}>{isAr ? 'الحالة' : 'Stage'}</th>
                    <th style={th}>{isAr ? 'إجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {records.map(r => (
                    <tr key={r.externalId}>
                      <td style={td}>{r.reportingYear} · {r.reportingPeriod}</td>
                      <td style={td}>
                        {isAr ? ASSET_CLASS_LABELS[r.assetClass].ar : ASSET_CLASS_LABELS[r.assetClass].en}
                      </td>
                      <td style={td}>{fmtSarMillions(r.outstandingAmountSarMillions, isAr)}</td>
                      <td style={td}>{Number(r.attributionFactor).toFixed(6)}</td>
                      <td style={td}>
                        <Badge tone={dataQualityTone(r.dataQualityScore)}>
                          PCAF {r.dataQualityScore}
                        </Badge>
                      </td>
                      <td style={td}>{fmtTco2e(r.totalFinancedEmissionsTco2e)}</td>
                      <td style={td}>
                        <Badge tone={emissionsStageTone(r.lifecycleStage)}>
                          {isAr ? EMISSIONS_STAGE_LABELS[r.lifecycleStage].ar : EMISSIONS_STAGE_LABELS[r.lifecycleStage].en}
                        </Badge>
                      </td>
                      <td style={td}>
                        {r.lifecycleStage === 'DRAFT' && (
                          <Button variant="secondary" Icon={Send}
                                  onClick={() => changeStage(r, 'IN_REVIEW')}>
                            {isAr ? 'للمراجعة' : 'Submit'}
                          </Button>
                        )}
                        {r.lifecycleStage === 'IN_REVIEW' && (
                          <Button variant="primary" Icon={CheckCircle2}
                                  onClick={() => changeStage(r, 'APPROVED')}>
                            {isAr ? 'اعتماد' : 'Approve'}
                          </Button>
                        )}
                        {r.lifecycleStage === 'APPROVED' && (
                          <Button variant="primary" Icon={Send}
                                  onClick={() => changeStage(r, 'PUBLISHED')}>
                            {isAr ? 'نشر' : 'Publish'}
                          </Button>
                        )}
                        {r.lifecycleStage === 'PUBLISHED' && (
                          <Button variant="secondary" Icon={Archive}
                                  onClick={() => changeStage(r, 'ARCHIVED')}>
                            {isAr ? 'أرشفة' : 'Archive'}
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        )}

        {!loading && tab === 'targets' && (
          progress.length === 0 ? (
            <InlineEmpty text={isAr ? 'لا توجد أهداف مسجَّلة بعد' : 'No targets registered yet'} />
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    <th style={th}>{isAr ? 'الكود' : 'Code'}</th>
                    <th style={th}>{isAr ? 'العنوان' : 'Title'}</th>
                    <th style={th}>{isAr ? 'الإطار' : 'Framework'}</th>
                    <th style={th}>{isAr ? 'السنة الأساس' : 'Baseline'}</th>
                    <th style={th}>{isAr ? 'الهدف' : 'Target'}</th>
                    <th style={th}>{isAr ? 'الخفض المُلتزَم' : 'Committed reduction'}</th>
                    <th style={th}>{isAr ? 'المُتحقَّق' : 'Achieved'}</th>
                    <th style={th}>{isAr ? 'النسبة' : 'Ratio'}</th>
                  </tr>
                </thead>
                <tbody>
                  {progress.map(p => (
                    <tr key={p.targetExternalId}>
                      <td style={td}><code>{p.targetCode}</code></td>
                      <td style={td}>{isAr ? (p.titleAr || p.titleEn) : p.titleEn}</td>
                      <td style={td}>
                        <Badge tone={p.sbtiAligned ? 'success' : 'info'}>
                          {isAr ? ALIGNMENT_LABELS[p.alignmentFramework].ar : ALIGNMENT_LABELS[p.alignmentFramework].en}
                        </Badge>
                      </td>
                      <td style={td}>{p.baselineYear} · {fmtTco2e(p.baselineEmissionsTco2e)}</td>
                      <td style={td}>{p.targetYear} · {fmtTco2e(p.targetEmissionsTco2e)}</td>
                      <td style={td}>{fmtPercent(p.reductionPercent)}</td>
                      <td style={td}>{fmtPercent(p.achievedReductionPercent)}</td>
                      <td style={td}>
                        <Badge tone={Number(p.achievementRatio) >= 1 ? 'success'
                                      : Number(p.achievementRatio) >= 0.5 ? 'warning' : 'danger'}>
                          {Number(p.achievementRatio).toFixed(2)}×
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        )}

        {!loading && tab === 'trend' && (
          byYear.length === 0 ? (
            <InlineEmpty text={isAr ? 'لا توجد بيانات اتّجاه بعد' : 'No trend data yet'} />
          ) : (
            <TrendChart
              data={byYear}
              isAr={isAr}
              baseline={progress[0]?.baselineEmissionsTco2e}
              targetEmissions={progress[0]?.targetEmissionsTco2e}
            />
          )
        )}
      </Card>
    </Shell>
  );
}

function TrendChart({
  data, isAr, baseline, targetEmissions,
}: {
  data: [number, number][];
  isAr: boolean;
  baseline?: number | string;
  targetEmissions?: number | string;
}) {
  const max = Math.max(...data.map(d => d[1]), Number(baseline) || 0, 1);
  return (
    <div className="form-stack">
      <p>
        {isAr
          ? 'إجمالي الانبعاثات المُموَّلة لكل سنة تقريرية (السجلّات المُعتمَدة والمنشورة فقط).'
          : 'Total financed emissions per reporting year (APPROVED + PUBLISHED records only).'}
      </p>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12, height: 200, padding: '12px 0' }}>
        {data.map(([year, value]) => (
          <div key={year} style={{ flex: 1, textAlign: 'center' }}>
            <div style={{
              height: `${(value / max) * 180}px`,
              background: 'var(--color-primary, #2563eb)',
              borderRadius: 4,
              marginBottom: 4,
            }} title={fmtTco2e(value)} />
            <small>{year}</small>
            <div><small>{fmtTco2e(value)}</small></div>
          </div>
        ))}
      </div>
      {targetEmissions && (
        <p>
          <TrendingDown size={16} style={{ verticalAlign: 'middle' }} />
          {' '}
          {isAr ? 'الهدف:' : 'Target:'} {fmtTco2e(targetEmissions)}
          {' · '}
          {isAr ? 'الأساس:' : 'Baseline:'} {fmtTco2e(baseline)}
        </p>
      )}
    </div>
  );
}

const th: React.CSSProperties = {
  textAlign: 'start',
  padding: '8px 12px',
  borderBottom: '1px solid var(--color-border)',
  fontWeight: 600,
};
const td: React.CSSProperties = {
  padding: '8px 12px',
  borderBottom: '1px solid var(--color-border)',
  verticalAlign: 'top',
};
