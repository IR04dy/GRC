import { useEffect, useMemo, useState } from 'react';
import {
  Factory, Target, BarChart3, CheckCircle2,
  Send, Archive, TrendingDown, Plus,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listEmissions, transitionEmission, createEmission,
  listTargets, getTargetProgress, getEmissionsTotal, createTarget,
  ASSET_CLASS_LABELS, EMISSIONS_STAGE_LABELS, ALIGNMENT_LABELS,
  emissionsStageTone, dataQualityTone,
  fmtTco2e, fmtSarMillions, fmtPercent,
  type FinancedEmissionsRecord, type FinancedEmissionsTarget,
  type TargetProgress, type EmissionsLifecycleStage,
  type FinancedEmissionsTotal, type AssetClass, type ReportingPeriod,
  type AlignmentFramework,
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
  const [openCreate, setOpenCreate] = useState(false);
  const [openTarget, setOpenTarget] = useState(false);
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
      toast.success(isAr ? `الحالة: ${tlabel(EMISSIONS_STAGE_LABELS, to, true)}` : `Stage: ${tlabel(EMISSIONS_STAGE_LABELS, to, false)}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  // ─── Add a financed-emissions record (starts DRAFT) ──────────────
  const ASSET_CLASSES = Object.keys(ASSET_CLASS_LABELS) as AssetClass[];
  const PERIODS: ReportingPeriod[] = ['ANNUAL', 'Q1', 'Q2', 'Q3', 'Q4'];
  const createFields: FieldDef[] = [
    { key: 'reportingYear', label: isAr ? 'سنة التقرير' : 'Reporting year', kind: 'number', min: 2000, max: 2100, required: true, span: 4 },
    { key: 'reportingPeriod', label: isAr ? 'الفترة' : 'Period', kind: 'select', required: true, span: 4,
      options: PERIODS.map(p => ({ value: p, label: p === 'ANNUAL' ? (isAr ? 'سنوي' : 'Annual') : p })) },
    { key: 'dataQualityScore', label: isAr ? 'جودة بيانات PCAF' : 'PCAF data quality', kind: 'select', required: true, span: 4,
      options: [1, 2, 3, 4, 5].map(n => ({ value: String(n), label: isAr ? `${n} (${n === 1 ? 'الأفضل' : n === 5 ? 'الأضعف' : 'متوسط'})` : `${n} (${n === 1 ? 'best' : n === 5 ? 'worst' : 'proxy'})` })) },
    { key: 'assetClass', label: isAr ? 'فئة الأصل' : 'Asset class', kind: 'select', required: true, span: 12,
      options: ASSET_CLASSES.map(a => ({ value: a, label: isAr ? ASSET_CLASS_LABELS[a].ar : ASSET_CLASS_LABELS[a].en })) },
    { key: 'outstandingAmountSarMillions', label: isAr ? 'المبلغ القائم (مليون ر.س)' : 'Outstanding (M SAR)', kind: 'number', min: 0, required: true, span: 6 },
    { key: 'attributionFactor', label: isAr ? 'عامل الإسناد (0–1)' : 'Attribution factor (0–1)', kind: 'number', min: 0, max: 1, required: true, span: 6 },
    { key: 'emissionsScope1Tco2e', label: isAr ? 'النطاق 1 (طن CO₂e)' : 'Scope 1 (tCO₂e)', kind: 'number', min: 0, span: 4 },
    { key: 'emissionsScope2Tco2e', label: isAr ? 'النطاق 2 (طن CO₂e)' : 'Scope 2 (tCO₂e)', kind: 'number', min: 0, span: 4 },
    { key: 'emissionsScope3Tco2e', label: isAr ? 'النطاق 3 (طن CO₂e)' : 'Scope 3 (tCO₂e)', kind: 'number', min: 0, span: 4 },
    { key: 'methodologyEn', label: isAr ? 'المنهجية (إنجليزي)' : 'Methodology (English)', kind: 'textarea', rows: 2, span: 12 },
    { key: 'methodologyAr', label: isAr ? 'المنهجية (عربي)' : 'Methodology (Arabic)', kind: 'textarea', rows: 2, span: 12 },
  ];
  const createInitial = {
    reportingYear: String(currentYear), reportingPeriod: 'ANNUAL', dataQualityScore: '3',
    assetClass: 'BUSINESS_LOANS', outstandingAmountSarMillions: '', attributionFactor: '',
    emissionsScope1Tco2e: '', emissionsScope2Tco2e: '', emissionsScope3Tco2e: '',
    methodologyEn: '', methodologyAr: '',
  };

  async function submitCreate(v: Record<string, string>) {
    const assetClass = String(v.assetClass) as AssetClass;
    const labels = ASSET_CLASS_LABELS[assetClass];
    const reportingYear = Number(v.reportingYear);
    const outstanding = Number(v.outstandingAmountSarMillions);
    const attribution = Number(v.attributionFactor);
    const dq = Number(v.dataQualityScore);
    if (!reportingYear || !v.reportingPeriod || !assetClass || !labels
        || !Number.isFinite(outstanding) || !Number.isFinite(attribution) || !dq) {
      toast.danger(isAr ? 'يُرجى تعبئة الحقول المطلوبة' : 'Please fill the required fields');
      return;
    }
    // One row per (year × period × asset-class) — enforced by the DB unique
    // index UQ_fe_year_class_period. Catch the clash here so the user gets an
    // actionable message instead of a raw 500 from the duplicate-key insert.
    const dup = records.find(r =>
      r.reportingYear === reportingYear
      && r.reportingPeriod === v.reportingPeriod
      && r.assetClass === assetClass);
    if (dup) {
      const periodLabel = v.reportingPeriod === 'ANNUAL' ? (isAr ? 'سنوي' : 'Annual') : v.reportingPeriod;
      toast.danger(isAr
        ? `يوجد سجل بالفعل لعام ${reportingYear} · ${periodLabel} · ${labels.ar}. عدّل السجل القائم أو اختر فئة أصل أو سنة مختلفة.`
        : `A record already exists for ${reportingYear} · ${periodLabel} · ${labels.en}. Edit the existing record or choose a different asset class or year.`);
      return;
    }
    const num = (x: string) => {
      const n = Number(x);
      return x !== '' && Number.isFinite(n) ? n : undefined;
    };
    try {
      await createEmission({
        reportingYear,
        reportingPeriod: v.reportingPeriod as ReportingPeriod,
        assetClass,
        assetClassLabelEn: labels.en,
        assetClassLabelAr: labels.ar,
        outstandingAmountSarMillions: outstanding,
        attributionFactor: attribution,
        dataQualityScore: dq,
        emissionsScope1Tco2e: num(v.emissionsScope1Tco2e),
        emissionsScope2Tco2e: num(v.emissionsScope2Tco2e),
        emissionsScope3Tco2e: num(v.emissionsScope3Tco2e),
        methodologyEn: String(v.methodologyEn || '').trim() || undefined,
        methodologyAr: String(v.methodologyAr || '').trim() || undefined,
      });
      toast.success(isAr ? `تم تسجيل انبعاثات ${labels.ar} (مسوّدة)` : `Recorded emissions for ${labels.en} (DRAFT)`);
      setOpenCreate(false);
      setTab('records');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تسجيل السجل' : 'Failed to create record'));
    }
  }

  // ─── Register a reduction target ─────────────────────────────────
  const ALIGNMENTS = Object.keys(ALIGNMENT_LABELS) as AlignmentFramework[];
  const targetFields: FieldDef[] = [
    { key: 'titleEn', label: isAr ? 'عنوان الهدف (إنجليزي)' : 'Target title (English)', kind: 'text', required: true, maxLength: 255, span: 12 },
    { key: 'titleAr', label: isAr ? 'عنوان الهدف (عربي)' : 'Target title (Arabic)', kind: 'text', maxLength: 255, span: 12 },
    { key: 'baselineYear', label: isAr ? 'سنة الأساس' : 'Baseline year', kind: 'number', min: 2000, max: 2100, required: true, span: 4 },
    { key: 'targetYear', label: isAr ? 'سنة الهدف' : 'Target year', kind: 'number', min: 2000, max: 2100, required: true, span: 4 },
    { key: 'alignmentFramework', label: isAr ? 'إطار المواءمة' : 'Alignment framework', kind: 'select', required: true, span: 4,
      options: ALIGNMENTS.map(a => ({ value: a, label: isAr ? ALIGNMENT_LABELS[a].ar : ALIGNMENT_LABELS[a].en })) },
    { key: 'baselineEmissionsTco2e', label: isAr ? 'انبعاثات الأساس (طن CO₂e)' : 'Baseline emissions (tCO₂e)', kind: 'number', min: 0, required: true, span: 6 },
    { key: 'targetEmissionsTco2e', label: isAr ? 'انبعاثات الهدف (طن CO₂e)' : 'Target emissions (tCO₂e)', kind: 'number', min: 0, required: true, span: 6 },
    { key: 'sbtiAligned', label: isAr ? 'مُعتمَد من SBTi؟' : 'SBTi-validated?', kind: 'select', required: true, span: 12,
      options: [
        { value: 'false', label: isAr ? 'لا' : 'No' },
        { value: 'true', label: isAr ? 'نعم' : 'Yes' },
      ] },
  ];
  const targetInitial = {
    titleEn: '', titleAr: '', baselineYear: String(currentYear), targetYear: '2030',
    alignmentFramework: 'SBTI_1_5C', baselineEmissionsTco2e: '', targetEmissionsTco2e: '', sbtiAligned: 'false',
  };

  async function submitTarget(v: Record<string, string>) {
    const titleEn = String(v.titleEn || '').trim();
    const alignmentFramework = String(v.alignmentFramework) as AlignmentFramework;
    const targetYear = Number(v.targetYear);
    const baselineYear = Number(v.baselineYear);
    const baseline = Number(v.baselineEmissionsTco2e);
    const target = Number(v.targetEmissionsTco2e);
    if (!titleEn || !alignmentFramework || !targetYear || !baselineYear
        || !Number.isFinite(baseline) || !Number.isFinite(target)) {
      toast.danger(isAr ? 'يُرجى تعبئة الحقول المطلوبة' : 'Please fill the required fields');
      return;
    }
    if (targetYear <= baselineYear) {
      toast.danger(isAr ? 'سنة الهدف يجب أن تكون بعد سنة الأساس' : 'Target year must be after the baseline year');
      return;
    }
    if (target >= baseline) {
      toast.danger(isAr
        ? 'انبعاثات الهدف يجب أن تكون أقل من انبعاثات الأساس لتمثّل خفضًا.'
        : 'Target emissions must be lower than the baseline to represent a reduction.');
      return;
    }
    try {
      await createTarget({
        titleEn,
        titleAr: String(v.titleAr || '').trim() || undefined,
        targetYear,
        baselineYear,
        baselineEmissionsTco2e: baseline,
        targetEmissionsTco2e: target,
        sbtiAligned: v.sbtiAligned === 'true',
        alignmentFramework,
      });
      toast.success(isAr ? 'تم تسجيل الهدف' : 'Reduction target registered');
      setOpenTarget(false);
      setTab('targets');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تسجيل الهدف' : 'Failed to create target'));
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

      <div className="kpi-grid-sm" style={{ marginBottom: 'var(--sp-6)' }}>
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
        <div className="cluster" style={{ gap: 'var(--sp-4)', marginBottom: 'var(--sp-6)' }}>
          <Button variant={tab === 'records' ? 'primary' : 'secondary'} onClick={() => setTab('records')}>
            {isAr ? 'السجلّات' : 'Records'}
          </Button>
          <Button variant={tab === 'targets' ? 'primary' : 'secondary'} onClick={() => setTab('targets')}>
            {isAr ? 'الأهداف' : 'Targets'}
          </Button>
          <Button variant={tab === 'trend' ? 'primary' : 'secondary'} onClick={() => setTab('trend')}>
            {isAr ? 'الاتّجاه الزمني' : 'Trend'}
          </Button>
          {tab === 'records' && (
            <Button variant="primary" Icon={Plus} style={{ marginInlineStart: 'auto' }}
                    onClick={() => setOpenCreate(true)}>
              {isAr ? 'إضافة سجل' : 'Add record'}
            </Button>
          )}
          {tab === 'targets' && (
            <Button variant="primary" Icon={Plus} style={{ marginInlineStart: 'auto' }}
                    onClick={() => setOpenTarget(true)}>
              {isAr ? 'إضافة هدف' : 'Add target'}
            </Button>
          )}
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
                        {tlabel(ASSET_CLASS_LABELS, r.assetClass, isAr)}
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
                          {tlabel(EMISSIONS_STAGE_LABELS, r.lifecycleStage, isAr)}
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
                          {tlabel(ALIGNMENT_LABELS, p.alignmentFramework, isAr)}
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

      <FormModal
        open={openCreate}
        title={isAr ? 'إضافة سجل انبعاثات مُموَّلة' : 'Add financed-emissions record'}
        fields={createFields}
        initial={createInitial}
        submitLabel={isAr ? 'تسجيل' : 'Create'}
        size="lg"
        onClose={() => setOpenCreate(false)}
        onSubmit={submitCreate}
      />

      <FormModal
        open={openTarget}
        title={isAr ? 'إضافة هدف خفض' : 'Add reduction target'}
        fields={targetFields}
        initial={targetInitial}
        submitLabel={isAr ? 'تسجيل' : 'Create'}
        size="lg"
        onClose={() => setOpenTarget(false)}
        onSubmit={submitTarget}
      />
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
  borderBottom: '1px solid var(--border)',
  fontWeight: 600,
};
const td: React.CSSProperties = {
  padding: '8px 12px',
  borderBottom: '1px solid var(--border)',
  verticalAlign: 'top',
};
