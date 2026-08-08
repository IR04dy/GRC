/**
 * F2-16 tranche 2 — the ESG metrics register tab, moved out of `EsgPage.tsx`.
 *
 * One of the four regulatory tabs extracted together (CMA, TCFD, materiality, metrics): they sat
 * contiguously at lines 1287–3055 of the page and were **self-contained by measurement** — zero
 * references to any page-local helper, one call site each in the page's tab switch, and nothing
 * declared inside them needed outside. That is why they could move as a block.
 *
 * <b>The move is proven textually, not trusted.</b> `EsgPage.tsx` has no behavioural test beyond the
 * overview surface tranche 1 extracted, so the guarantee is exact-text identity: this range appears
 * here verbatim with one deliberate change — the `export` keyword — and everything left in the page
 * below its imports is byte-identical to what was there before, minus the moved span.
 * `noUnusedLocals` then makes `tsc` the checker for the import surgery.
 *
 * <b>⚠ Boundaries were derived, not typed.</b> Tranche 1's ranges were hand-read from a grep and
 * every one of these four would have been off by one — the tabs are separated by a blank line as
 * well as a banner, so the hand-read "last" line was `''`, not `}`. A range one line short still
 * compiles; it leaves a stray brace behind.
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Leaf, TrendingDown,
  Trash2, CheckCircle2, AlertCircle, Building2,
  Plus,
  Pencil,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  listMetrics, metricsAggregate, recordMetricReading, archiveMetric, createMetric, updateMetric,
  computeMetricProgress, metricStatusTone, metricStatusLabel,
  metricCategoryLabel as esgMetricCategoryLabel,
  type EsgMetric as EsgM, type MetricAggregate, type MetricCategory,
} from '../../lib/sustainabilityMetrics';

// ─── ESG Metrics tab (V63 — slice 9) ─────────────────────────────────────────
//
// Unified register for Environmental / Social / Governance metrics.
// Each row tracks baseline / current / target with a unit; progress is
// direction-aware (renewable % rises to target; LTIFR + water intensity
// fall to target). "Record reading" updates current_value + as_of so
// the dashboard reflects the latest data without rewriting the whole row.

export function MetricsTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems]   = useState<EsgM[]>([]);
  const [agg, setAgg]       = useState<MetricAggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [categoryFilter, setCategoryFilter] = useState<MetricCategory | 'ALL'>('ALL');
  const [readingMetric, setReadingMetric]   = useState<EsgM | null>(null);
  const [openNew, setOpenNew]               = useState(false);
  const [editMetric, setEditMetric]         = useState<EsgM | null>(null);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — metrics list and aggregate are independent.
    const [pg, a] = await Promise.all([
      listMetrics({ year: 2025, size: 200 }).catch(() => ({ content: [] as EsgM[], totalElements: 0 })),
      metricsAggregate(2025).catch(() => null as MetricAggregate | null),
    ]);
    setItems(pg.content);
    setAgg(a);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  const filtered = categoryFilter === 'ALL'
    ? items
    : items.filter(m => m.category === categoryFilter);

  async function submitReading(values: Record<string, unknown>) {
    if (!readingMetric) return;
    const cur = Number(values.currentValue);
    const asOf = String(values.asOf ?? '').trim() || undefined;
    if (!Number.isFinite(cur)) { toast.danger(isAr ? 'القيمة غير صالحة' : 'Invalid value'); return; }
    try {
      await recordMetricReading(readingMetric.id!, cur, asOf);
      toast.success(isAr ? 'تم تسجيل القراءة' : 'Reading recorded');
      setReadingMetric(null);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onArchive(m: EsgM) {
    if (!window.confirm(isAr ? 'أرشفة هذا المؤشّر؟' : 'Archive this metric?')) return;
    try { await archiveMetric(m.id!); toast.info(isAr ? 'تم الأرشفة' : 'Archived'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const readingFields: FieldDef[] = [
    { kind: 'number', key: 'currentValue', required: true, span: 6,
      label: isAr ? 'القيمة الحالية' : 'Current value' },
    { kind: 'date', key: 'asOf', span: 6,
      label: isAr ? 'تاريخ القياس' : 'As of' },
  ];

  const createFields: FieldDef[] = [
    { kind: 'text', key: 'metricNameEn', required: true, span: 6,
      label: isAr ? 'اسم المؤشّر (إنجليزي)' : 'Metric name (English)' },
    { kind: 'text', key: 'metricNameAr', span: 6,
      label: isAr ? 'اسم المؤشّر (عربي)' : 'Metric name (Arabic)' },
    { kind: 'select', key: 'category', required: true, span: 6,
      label: isAr ? 'الفئة' : 'Category',
      options: (['ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => ({
        value: c, label: esgMetricCategoryLabel(c, isAr ? 'ar' : 'en'),
      })) },
    { kind: 'text', key: 'subcategory', span: 6,
      label: isAr ? 'الفئة الفرعية' : 'Subcategory' },
    { kind: 'text', key: 'unit', required: true, span: 4,
      label: isAr ? 'الوحدة' : 'Unit', placeholder: 'MWh, %, tCO₂e…' },
    { kind: 'number', key: 'reportingYear', required: true, span: 4, min: 2000, max: 2100,
      label: isAr ? 'سنة التقرير' : 'Reporting year' },
    { kind: 'text', key: 'metricCode', span: 4,
      label: isAr ? 'الكود (اختياري)' : 'Code (optional)', placeholder: 'ENV-ENERGY-TOTAL' },
    { kind: 'number', key: 'baselineValue', span: 3,
      label: isAr ? 'قيمة الأساس' : 'Baseline value' },
    { kind: 'number', key: 'baselineYear', span: 3, min: 2000, max: 2100,
      label: isAr ? 'سنة الأساس' : 'Baseline year' },
    { kind: 'number', key: 'targetValue', span: 3,
      label: isAr ? 'القيمة المستهدفة' : 'Target value' },
    { kind: 'number', key: 'targetYear', span: 3, min: 2000, max: 2100,
      label: isAr ? 'سنة الهدف' : 'Target year' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المسؤول' : 'Owner' },
    { kind: 'text', key: 'responsibleDepartment', span: 6,
      label: isAr ? 'الإدارة المسؤولة' : 'Responsible department' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
  ];

  // Edit-metric — descriptive fields + baseline/target correction. The
  // current value keeps its dedicated "Record" action, and the reporting
  // year / code are fixed once created. Numeric columns have no entity
  // default (null), so omitting current_value leaves it untouched.
  const editFields: FieldDef[] = [
    { kind: 'text', key: 'metricNameEn', required: true, span: 6,
      label: isAr ? 'اسم المؤشّر (إنجليزي)' : 'Metric name (English)' },
    { kind: 'text', key: 'metricNameAr', span: 6,
      label: isAr ? 'اسم المؤشّر (عربي)' : 'Metric name (Arabic)' },
    { kind: 'select', key: 'category', required: true, span: 6,
      label: isAr ? 'الفئة' : 'Category',
      options: (['ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => ({
        value: c, label: esgMetricCategoryLabel(c, isAr ? 'ar' : 'en'),
      })) },
    { kind: 'text', key: 'subcategory', span: 3,
      label: isAr ? 'الفئة الفرعية' : 'Subcategory' },
    { kind: 'text', key: 'unit', required: true, span: 3,
      label: isAr ? 'الوحدة' : 'Unit', placeholder: 'MWh, %, tCO₂e…' },
    { kind: 'number', key: 'baselineValue', span: 3,
      label: isAr ? 'قيمة الأساس' : 'Baseline value' },
    { kind: 'number', key: 'baselineYear', span: 3, min: 2000, max: 2100,
      label: isAr ? 'سنة الأساس' : 'Baseline year' },
    { kind: 'number', key: 'targetValue', span: 3,
      label: isAr ? 'القيمة المستهدفة' : 'Target value' },
    { kind: 'number', key: 'targetYear', span: 3, min: 2000, max: 2100,
      label: isAr ? 'سنة الهدف' : 'Target year' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المسؤول' : 'Owner' },
    { kind: 'text', key: 'ownerEmail', span: 6,
      label: isAr ? 'بريد المسؤول' : 'Owner email' },
    { kind: 'text', key: 'responsibleDepartment', span: 12,
      label: isAr ? 'الإدارة المسؤولة' : 'Responsible department' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
    { kind: 'textarea', key: 'descriptionAr', span: 12, rows: 2,
      label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2,
      label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  const editInitial = useMemo<Record<string, unknown>>(() => ({
    metricNameEn:          editMetric?.metricNameEn ?? '',
    metricNameAr:          editMetric?.metricNameAr ?? '',
    category:              editMetric?.category ?? 'ENVIRONMENTAL',
    subcategory:           editMetric?.subcategory ?? '',
    unit:                  editMetric?.unit ?? '',
    baselineValue:         editMetric?.baselineValue != null ? String(editMetric.baselineValue) : '',
    baselineYear:          editMetric?.baselineYear != null ? String(editMetric.baselineYear) : '',
    targetValue:           editMetric?.targetValue != null ? String(editMetric.targetValue) : '',
    targetYear:            editMetric?.targetYear != null ? String(editMetric.targetYear) : '',
    ownerName:             editMetric?.ownerName ?? '',
    ownerEmail:            editMetric?.ownerEmail ?? '',
    responsibleDepartment: editMetric?.responsibleDepartment ?? '',
    descriptionEn:         editMetric?.descriptionEn ?? '',
    descriptionAr:         editMetric?.descriptionAr ?? '',
    notes:                 editMetric?.notes ?? '',
  }), [editMetric]);

  async function submitEdit(values: Record<string, unknown>): Promise<void> {
    if (!editMetric?.id) return;
    const nameEn = String(values.metricNameEn ?? '').trim();
    const unit = String(values.unit ?? '').trim();
    const category = String(values.category ?? '').trim();
    if (!nameEn || !unit || !category) {
      toast.danger(isAr
        ? 'اسم المؤشّر والفئة والوحدة حقول مطلوبة'
        : 'Metric name, category and unit are required');
      return;
    }
    const num = (v: unknown) => {
      const n = Number(v);
      return v === '' || v == null || !Number.isFinite(n) ? undefined : n;
    };
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      await updateMetric(editMetric.id, {
        metricNameEn: nameEn,
        metricNameAr: str(values.metricNameAr),
        category: category as MetricCategory,
        subcategory: str(values.subcategory),
        unit,
        baselineValue: num(values.baselineValue),
        baselineYear: num(values.baselineYear),
        targetValue: num(values.targetValue),
        targetYear: num(values.targetYear),
        ownerName: str(values.ownerName),
        ownerEmail: str(values.ownerEmail),
        responsibleDepartment: str(values.responsibleDepartment),
        descriptionEn: str(values.descriptionEn),
        descriptionAr: str(values.descriptionAr),
        notes: str(values.notes),
      });
      toast.success(isAr ? 'حُدّث المؤشّر' : 'Metric updated');
      setEditMetric(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحديث المؤشّر' : 'Failed to update metric'));
    }
  }

  async function submitCreate(values: Record<string, unknown>): Promise<void> {
    const nameEn = String(values.metricNameEn ?? '').trim();
    const unit = String(values.unit ?? '').trim();
    const category = String(values.category ?? '').trim();
    const year = Number(values.reportingYear);
    if (!nameEn || !unit || !category || !Number.isFinite(year)) {
      toast.danger(isAr
        ? 'اسم المؤشّر والفئة والوحدة وسنة التقرير حقول مطلوبة'
        : 'Metric name, category, unit and reporting year are required');
      return;
    }
    const num = (v: unknown) => {
      const n = Number(v);
      return v === '' || v == null || !Number.isFinite(n) ? undefined : n;
    };
    const str = (v: unknown) => {
      const s = String(v ?? '').trim();
      return s || undefined;
    };
    try {
      const created = await createMetric({
        metricNameEn: nameEn,
        metricNameAr: str(values.metricNameAr),
        category: category as MetricCategory,
        subcategory: str(values.subcategory),
        unit,
        reportingYear: year,
        metricCode: str(values.metricCode),
        baselineValue: num(values.baselineValue),
        baselineYear: num(values.baselineYear),
        targetValue: num(values.targetValue),
        targetYear: num(values.targetYear),
        ownerName: str(values.ownerName),
        responsibleDepartment: str(values.responsibleDepartment),
        descriptionEn: str(values.descriptionEn),
        status: 'ACTIVE',
      });
      toast.success(isAr
        ? `تمت إضافة المؤشّر ${created.metricCode ?? created.metricNameEn}`
        : `Metric ${created.metricCode ?? created.metricNameEn} added`);
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّرت إضافة المؤشّر' : 'Failed to add metric'));
    }
  }

  return (
    <div className="esg-stack">
      {/* Per-pillar mean-progress strip */}
      <div className="kpi-grid">
        <Kpi Icon={TrendingDown} label={isAr ? 'إجمالي المؤشّرات' : 'Total metrics'}                value={agg?.total ?? 0} tone="primary" onClick={() => setCategoryFilter('ALL')} active={categoryFilter === 'ALL'} />
        <Kpi Icon={Leaf}         label={isAr ? 'متوسّط تقدّم البيئة' : 'Env. mean progress'}     value={`${agg ? Number(agg.meanProgressByCategory.ENVIRONMENTAL ?? 0).toFixed(0) : 0}%`} tone="success" onClick={() => setCategoryFilter('ENVIRONMENTAL')} active={categoryFilter === 'ENVIRONMENTAL'} />
        <Kpi Icon={Building2}    label={isAr ? 'متوسّط تقدّم الاجتماع' : 'Social mean progress'} value={`${agg ? Number(agg.meanProgressByCategory.SOCIAL ?? 0).toFixed(0) : 0}%`}        tone="info" onClick={() => setCategoryFilter('SOCIAL')} active={categoryFilter === 'SOCIAL'} />
        <Kpi Icon={Building2}    label={isAr ? 'متوسّط تقدّم الحوكمة' : 'Gov. mean progress'}   value={`${agg ? Number(agg.meanProgressByCategory.GOVERNANCE ?? 0).toFixed(0) : 0}%`}    tone="primary" onClick={() => setCategoryFilter('GOVERNANCE')} active={categoryFilter === 'GOVERNANCE'} />
        <Kpi Icon={CheckCircle2} label={isAr ? 'بلغ الهدف' : 'At target'}                       value={agg
          ? (agg.atTargetByCategory.ENVIRONMENTAL + agg.atTargetByCategory.SOCIAL + agg.atTargetByCategory.GOVERNANCE)
          : 0} tone="success" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'خارج المسار' : 'Off track'}                     value={agg
          ? (agg.offTrackByCategory.ENVIRONMENTAL + agg.offTrackByCategory.SOCIAL + agg.offTrackByCategory.GOVERNANCE)
          : 0} tone="danger" to="/esg" />
      </div>

      <Card title={isAr ? 'سجلّ مؤشّرات الاستدامة (E/S/G)' : 'ESG Metrics Register (E/S/G)'} Icon={TrendingDown}
        action={<Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>
          {isAr ? 'مؤشّر جديد' : 'New metric'}
        </Button>}>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'سجلّ موحَّد لمؤشّرات البيئة والاجتماع والحوكمة. التقدّم يُحسب وفق الاتجاه — يدعم أهداف الارتفاع («حصة المتجدّدة») وأهداف الانخفاض («LTIFR»).'
            : 'Unified register for Environmental, Social, and Governance metrics. Progress is direction-aware — supports both raise-to-target ("renewable share") and lower-to-target ("LTIFR").'}
        </div>

        {/* Category filter chips */}
        <div className="esg-filter-row">
          {(['ALL', 'ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => (
            <button key={c} onClick={() => setCategoryFilter(c)}
              className={`esg-filter-btn${categoryFilter === c ? ' is-active' : ''}`}>
              {c === 'ALL' ? (isAr ? 'الكل' : 'All') : esgMetricCategoryLabel(c as MetricCategory, isAr ? 'ar' : 'en')}
            </button>
          ))}
          <span className="text-2xs text-muted esg-filter-row__count">
            {filtered.length} / {items.length}
          </span>
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : filtered.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد مؤشّرات' : 'No metrics yet'}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'المؤشّر' : 'Metric'}</th>
                    <th>{isAr ? 'الفئة' : 'Category'}</th>
                    <th>{isAr ? 'خط الأساس' : 'Baseline'}</th>
                    <th>{isAr ? 'الحالي' : 'Current'}</th>
                    <th>{isAr ? 'الهدف' : 'Target'}</th>
                    <th>{isAr ? 'التقدّم' : 'Progress'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th className="esg-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(m => {
                    const pct = computeMetricProgress(m);
                    return (
                      <tr key={m.id}>
                        <td className="mono text-2xs">{m.metricCode ?? `#${m.id}`}</td>
                        <td>
                          <div className="esg-cell-title">
                            {isAr && m.metricNameAr ? m.metricNameAr : m.metricNameEn}
                          </div>
                          {m.ownerName && <div className="text-2xs text-muted esg-cell-sub">{m.ownerName}</div>}
                        </td>
                        <td>
                          <Badge tone="neutral">{esgMetricCategoryLabel(m.category, isAr ? 'ar' : 'en')}</Badge>
                          {m.subcategory && <div className="text-2xs text-muted esg-cell-sub">{m.subcategory}</div>}
                        </td>
                        <td className="mono text-xs esg-text-end">
                          {m.baselineValue != null ? Number(m.baselineValue).toLocaleString() : '—'}
                          <div className="text-2xs text-muted">{m.unit}</div>
                        </td>
                        <td className="mono text-xs esg-text-end esg-text-bold">
                          {m.currentValue != null ? Number(m.currentValue).toLocaleString() : '—'}
                          {m.currentValueAsOf && <div className="text-2xs text-muted">{m.currentValueAsOf}</div>}
                        </td>
                        <td className="mono text-xs esg-text-end">
                          {m.targetValue != null ? Number(m.targetValue).toLocaleString() : '—'}
                          {m.targetYear && <div className="text-2xs text-muted">→ {m.targetYear}</div>}
                        </td>
                        <td style={{ minWidth: 120 }}>
                          <div className="esg-progress-wrap">
                            <div className="esg-progress-track">
                              <div className="esg-progress-fill"
                                style={{
                                  width: `${Math.min(100, pct)}%`,
                                  background: pct >= 100 ? 'var(--success)' : pct >= 50 ? 'var(--primary)' : 'var(--warning)',
                                }} />
                            </div>
                            <span className="mono text-2xs esg-progress-label esg-text-end" style={{ minWidth: 40 }}>{pct.toFixed(0)}%</span>
                          </div>
                        </td>
                        <td><Badge tone={metricStatusTone(m.status)}>{metricStatusLabel(m.status, isAr ? 'ar' : 'en')}</Badge></td>
                        <td className="esg-text-end">
                          <div className="esg-actions-row">
                            {m.status !== 'ARCHIVED' && (
                              <Button size="sm" variant="ghost" Icon={Pencil}
                                title={isAr ? 'تعديل' : 'Edit'}
                                onClick={() => setEditMetric(m)} />
                            )}
                            {m.status !== 'ARCHIVED' && (
                              <Button size="sm" variant="secondary" onClick={() => setReadingMetric(m)}>
                                {isAr ? 'تسجيل' : 'Record'}
                              </Button>
                            )}
                            {m.status !== 'ARCHIVED' && (
                              <Button size="sm" variant="ghost" Icon={Trash2}
                                title={isAr ? 'أرشفة' : 'Archive'}
                                onClick={() => onArchive(m)} />
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
      </Card>

      <FormModal
        open={readingMetric !== null}
        title={isAr ? `تسجيل قراءة · ${readingMetric?.metricCode ?? ''}` : `Record reading · ${readingMetric?.metricCode ?? ''}`}
        fields={readingFields}
        initial={{
          currentValue: readingMetric?.currentValue ?? 0,
          asOf: new Date().toISOString().slice(0, 10),
        }}
        size="md"
        submitLabel={isAr ? 'حفظ' : 'Save'}
        onClose={() => setReadingMetric(null)}
        onSubmit={submitReading}
      />

      <FormModal
        open={openNew}
        title={isAr ? 'مؤشّر استدامة جديد' : 'New ESG metric'}
        fields={createFields}
        initial={{
          metricNameEn: '', metricNameAr: '', category: 'ENVIRONMENTAL',
          subcategory: '', unit: '', reportingYear: 2025, metricCode: '',
          baselineValue: '', baselineYear: '', targetValue: '', targetYear: '',
          ownerName: '', responsibleDepartment: '', descriptionEn: '',
        }}
        size="lg"
        submitLabel={isAr ? 'إضافة المؤشّر' : 'Add metric'}
        onClose={() => setOpenNew(false)}
        onSubmit={submitCreate}
      />

      <FormModal
        open={editMetric !== null}
        title={editMetric
          ? (isAr ? `تعديل المؤشّر · ${editMetric.metricCode ?? ''}` : `Edit metric · ${editMetric.metricCode ?? ''}`)
          : ''}
        fields={editFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditMetric(null)}
        onSubmit={submitEdit}
      />
    </div>
  );
}
