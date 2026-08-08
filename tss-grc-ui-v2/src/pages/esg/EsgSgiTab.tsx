/**
 * F2-16 tranche 3 — the SGI commitments register tab, moved out of `EsgPage.tsx`.
 *
 * One of seven extracted together, each **self-contained by measurement**: zero references to any
 * page-local identifier outside its own range, exactly one call site in the page's tab switch, and
 * nothing declared inside it needed outside.
 *
 * <b>⚠ The boundary rule that got this tab here is not the one tranche 2 used, and the difference is
 * a defect tranche 2's rule would have shipped.</b> That tranche walked up from the declaration over
 * adjacent `//` lines. `ClimateTab` is documented with a `/** … *&#47;` block and `CapaTab`/`AnnexTab`
 * have multi-paragraph banners split by blank lines — so the walk-up would have left their
 * documentation behind on the page, orphaned above an unrelated declaration, compiling cleanly. The
 * rule now is the file's own convention: everything between the previous top-level declaration and
 * this one belongs to this one.
 *
 * <b>Proven, not trusted.</b> The moved range appears here verbatim with one deliberate `export`; the
 * page keeps every line it did not lose, byte for byte; no `function` or banner line of a moved tab
 * survives on the page; and the line count is conserved. `noUnusedLocals` makes `tsc` the checker for
 * the import surgery. Tests: `EsgOperationalTabs.test.tsx`.
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Leaf, Target,
  Download, Trash2, CheckCircle2, AlertCircle,
  Plus, ArrowRight, Link2, Clock,
  Paperclip, Pencil, RefreshCw,
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
  listSgi, sgiAggregate, achieveSgi, retireSgi, createSgi, updateSgi, sweepSgi, refreshValueSgi, exportSgiXlsx,
  sgiStatusTone, sgiSectorLabel, sgiStatusLabel, sgiTierTone, sgiTierLabel, sgiValueSourceLabel,
  computeProgressPct, canAchieve, computeTier, isMilestoneOverdue,
  type SgiCommitment as SgiC, type SgiAggregate as SgiAgg, type SgiSector, type SgiValueSource,
} from '../../lib/sustainabilitySgi';

// ─── SGI tab ────────────────────────────────────────────────────────────────

// ─── SGI commitments tab (V59 — slice 5) ───────────────────────────────────
//
// Promoted from the static SGI_COMMITMENTS constant (kept for backwards
// compatibility with the overview KPI math in lib/esg.ts) to a live
// API-driven register. Per-commitment lifecycle:
//   - Achieve: requires current value to actually meet target (Rule A
//     mirrored client-side via canAchieve)
//   - Retire: requires a written reason (Rule B — captured via dialog)
//
// Progress bars are computed client-side from baseline/current/target so
// the tab doesn't have to hit /progress for every row.

export function SgiTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<SgiC[]>([]);
  const [agg, setAgg]     = useState<SgiAgg | null>(null);
  const [loading, setLoading] = useState(true);
  const [retiringId, setRetiringId]   = useState<number | null>(null);
  const [openNew, setOpenNew]         = useState(false);
  const [editSgi, setEditSgi]         = useState<SgiC | null>(null);
  const [sweeping, setSweeping]       = useState(false);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — SGI list and aggregate load independently.
    const [pg, a] = await Promise.all([
      listSgi({ size: 200 }).catch(() => ({ content: [] as SgiC[], totalElements: 0 })),
      sgiAggregate().catch(() => null as SgiAgg | null),
    ]);
    setItems(pg.content);
    setAgg(a);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  // Group active commitments by sector, in display order.
  const bySector = useMemo(() => {
    const map = new Map<string, SgiC[]>();
    for (const c of items) {
      if (!map.has(c.sector)) map.set(c.sector, []);
      map.get(c.sector)!.push(c);
    }
    return map;
  }, [items]);

  // SGI-A — Achieve is a disclosure-grade governance action: no typed name
  // (the server stamps the authenticated principal), and it is APPROVE-guarded
  // with Rule C (achiever ≠ last editor, 409) and Rule D (evidence attached,
  // 400). A plain confirm suffices; the server's message surfaces on failure.
  async function onAchieve(id: number) {
    if (!window.confirm(isAr
      ? 'تأكيد إنجاز الالتزام؟ يتطلب دليلاً مرفقاً، ويجب أن يختلف المُعتمِد عن آخر مُحرِّر (فصل المهام).'
      : 'Confirm this commitment as achieved? Requires attached evidence, and the approver must differ from the last editor (SoD).')) return;
    try {
      await achieveSgi(id);
      toast.success(isAr ? 'تم الإنجاز' : 'Achieved');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }
  // SGI-B — run the surveillance sweep on demand (also runs daily 03:00 UTC).
  async function onSweep() {
    setSweeping(true);
    try {
      const r = await sweepSgi();
      const total = r.delayedCapas + r.milestoneCapas;
      toast.success(total === 0
        ? (isAr ? 'اكتمل الفحص — لا انحرافات جديدة' : 'Sweep complete — no new gaps')
        : (isAr
          ? `اكتمل الفحص — ${r.delayedCapas} متأخّر و${r.milestoneCapas} مرحلة فائتة`
          : `Sweep complete — ${r.delayedCapas} behind-plan, ${r.milestoneCapas} overdue-milestone CAPA(s)`));
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر الفحص' : 'Sweep failed'));
    } finally {
      setSweeping(false);
    }
  }
  // SGI-C — pull current_value from the commitment's linked source.
  async function onRefresh(id: number) {
    try {
      const c = await refreshValueSgi(id);
      toast.success(isAr
        ? `حُدّثت القيمة إلى ${Number(c.currentValue ?? 0).toLocaleString()} ${c.unit}`
        : `Value refreshed to ${Number(c.currentValue ?? 0).toLocaleString()} ${c.unit}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر التحديث من المصدر' : 'Refresh failed'));
    }
  }
  async function submitRetire(values: Record<string, unknown>) {
    if (retiringId == null) return;
    const reason = String(values.reason ?? '').trim();
    if (!reason) {
      toast.danger(isAr ? 'السبب مطلوب' : 'A reason is required'); return;
    }
    try {
      await retireSgi(retiringId, reason);
      toast.info(isAr ? 'تم التقاعد' : 'Retired');
      setRetiringId(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  const retireFields: FieldDef[] = [
    // FieldDef.textarea doesn't carry a `required` flag — submitRetire
    // re-validates that the reason isn't empty before dispatch (Rule B).
    // Retirer identity is stamped from the principal, not typed (SGI-A).
    { kind: 'textarea', key: 'reason', span: 12, rows: 3,
      label: isAr ? 'سبب التقاعد (للسجل المراجعي) *' : 'Reason (for audit trail) *' },
  ];

  const SGI_SECTORS: SgiSector[] = ['ENERGY', 'INDUSTRY', 'TRANSPORT', 'WASTE', 'FORESTRY', 'WATER', 'LAND', 'OTHER'];
  const createFields: FieldDef[] = [
    { kind: 'text', key: 'commitmentCode', required: true, span: 6,
      label: isAr ? 'كود الالتزام' : 'Commitment code', placeholder: 'SGI-RENEWABLE-50' },
    { kind: 'select', key: 'sector', required: true, span: 6,
      label: isAr ? 'القطاع' : 'Sector',
      options: SGI_SECTORS.map(s => ({ value: s, label: sgiSectorLabel(s, isAr ? 'ar' : 'en') })) },
    { kind: 'text', key: 'titleEn', required: true, span: 6,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 6,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'text', key: 'unit', required: true, span: 4,
      label: isAr ? 'الوحدة' : 'Unit', placeholder: 'tCO₂e/yr, %, MW…' },
    { kind: 'number', key: 'targetValue', required: true, span: 4,
      label: isAr ? 'القيمة المستهدفة' : 'Target value' },
    { kind: 'number', key: 'targetYear', required: true, span: 4, min: 2020, max: 2100,
      label: isAr ? 'سنة الهدف' : 'Target year' },
    { kind: 'number', key: 'baselineValue', span: 4,
      label: isAr ? 'قيمة الأساس' : 'Baseline value' },
    { kind: 'number', key: 'baselineYear', span: 4, min: 2000, max: 2100,
      label: isAr ? 'سنة الأساس' : 'Baseline year' },
    { kind: 'number', key: 'currentValue', span: 4,
      label: isAr ? 'القيمة الحالية' : 'Current value' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المالك' : 'Owner' },
    { kind: 'text', key: 'nextMilestoneEn', span: 6,
      label: isAr ? 'المرحلة التالية' : 'Next milestone' },
    // SGI-C — source lineage for current_value.
    { kind: 'select', key: 'valueSource', span: 4,
      label: isAr ? 'مصدر القيمة' : 'Value source',
      options: (['MANUAL', 'ESG_METRIC', 'GHG_TOTAL'] as SgiValueSource[])
        .map(s => ({ value: s, label: sgiValueSourceLabel(s, isAr ? 'ar' : 'en') })) },
    { kind: 'number', key: 'sourceMetricId', span: 4,
      label: isAr ? 'رقم مؤشر الاستدامة' : 'ESG metric ID' },
    { kind: 'number', key: 'sourceScope', span: 4, min: 1, max: 3,
      label: isAr ? 'نطاق الانبعاثات (١-٣)' : 'GHG scope (1–3)' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
  ];

  // Edit-commitment — the full editable definition. commitmentCode is the
  // immutable identifier and status is owned by the achieve/retire actions,
  // so neither is in the form. Numeric columns have no entity default (null),
  // so any omitted value is left untouched on save.
  const editFields: FieldDef[] = [
    { kind: 'select', key: 'sector', required: true, span: 6,
      label: isAr ? 'القطاع' : 'Sector',
      options: SGI_SECTORS.map(s => ({ value: s, label: sgiSectorLabel(s, isAr ? 'ar' : 'en') })) },
    { kind: 'text', key: 'titleEn', required: true, span: 6,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 12,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'text', key: 'unit', required: true, span: 4,
      label: isAr ? 'الوحدة' : 'Unit', placeholder: 'tCO₂e/yr, %, MW…' },
    { kind: 'number', key: 'targetValue', required: true, span: 4,
      label: isAr ? 'القيمة المستهدفة' : 'Target value' },
    { kind: 'number', key: 'targetYear', required: true, span: 4, min: 2020, max: 2100,
      label: isAr ? 'سنة الهدف' : 'Target year' },
    { kind: 'number', key: 'baselineValue', span: 4,
      label: isAr ? 'قيمة الأساس' : 'Baseline value' },
    { kind: 'number', key: 'baselineYear', span: 4, min: 2000, max: 2100,
      label: isAr ? 'سنة الأساس' : 'Baseline year' },
    { kind: 'number', key: 'currentValue', span: 4,
      label: isAr ? 'القيمة الحالية' : 'Current value' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المالك' : 'Owner' },
    { kind: 'text', key: 'ownerEmail', span: 6,
      label: isAr ? 'بريد المالك' : 'Owner email' },
    { kind: 'date', key: 'nextMilestoneDate', span: 4,
      label: isAr ? 'تاريخ المرحلة التالية' : 'Next milestone date' },
    { kind: 'text', key: 'nextMilestoneEn', span: 8,
      label: isAr ? 'المرحلة التالية (إنجليزي)' : 'Next milestone (English)' },
    { kind: 'text', key: 'nextMilestoneAr', span: 12,
      label: isAr ? 'المرحلة التالية (عربي)' : 'Next milestone (Arabic)' },
    // SGI-C — source lineage.
    { kind: 'select', key: 'valueSource', span: 4,
      label: isAr ? 'مصدر القيمة' : 'Value source',
      options: (['MANUAL', 'ESG_METRIC', 'GHG_TOTAL'] as SgiValueSource[])
        .map(s => ({ value: s, label: sgiValueSourceLabel(s, isAr ? 'ar' : 'en') })) },
    { kind: 'number', key: 'sourceMetricId', span: 4,
      label: isAr ? 'رقم مؤشر الاستدامة' : 'ESG metric ID' },
    { kind: 'number', key: 'sourceScope', span: 4, min: 1, max: 3,
      label: isAr ? 'نطاق الانبعاثات (١-٣)' : 'GHG scope (1–3)' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
    { kind: 'textarea', key: 'descriptionAr', span: 12, rows: 2,
      label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)' },
    { kind: 'textarea', key: 'notesEn', span: 12, rows: 2,
      label: isAr ? 'ملاحظات (إنجليزي)' : 'Notes (English)' },
    { kind: 'textarea', key: 'notesAr', span: 12, rows: 2,
      label: isAr ? 'ملاحظات (عربي)' : 'Notes (Arabic)' },
    // SGI-E — attach an evidence reference (Rule D proof for achievement).
    { kind: 'text', key: 'evidenceRef', span: 12,
      label: isAr ? 'مرجع الدليل' : 'Evidence reference',
      placeholder: isAr ? 'مثال: محضر مجلس 2030-Q4' : 'e.g. board minutes 2030-Q4' },
  ];

  const editInitial = useMemo<Record<string, unknown>>(() => ({
    sector:            editSgi?.sector ?? 'ENERGY',
    titleEn:           editSgi?.titleEn ?? '',
    titleAr:           editSgi?.titleAr ?? '',
    unit:              editSgi?.unit ?? '',
    targetValue:       editSgi?.targetValue != null ? String(editSgi.targetValue) : '',
    targetYear:        editSgi?.targetYear != null ? String(editSgi.targetYear) : '',
    baselineValue:     editSgi?.baselineValue != null ? String(editSgi.baselineValue) : '',
    baselineYear:      editSgi?.baselineYear != null ? String(editSgi.baselineYear) : '',
    currentValue:      editSgi?.currentValue != null ? String(editSgi.currentValue) : '',
    ownerName:         editSgi?.ownerName ?? '',
    ownerEmail:        editSgi?.ownerEmail ?? '',
    nextMilestoneDate: editSgi?.nextMilestoneDate ?? '',
    nextMilestoneEn:   editSgi?.nextMilestoneEn ?? '',
    nextMilestoneAr:   editSgi?.nextMilestoneAr ?? '',
    descriptionEn:     editSgi?.descriptionEn ?? '',
    descriptionAr:     editSgi?.descriptionAr ?? '',
    notesEn:           editSgi?.notesEn ?? '',
    notesAr:           editSgi?.notesAr ?? '',
    valueSource:       editSgi?.valueSource ?? 'MANUAL',
    sourceMetricId:    editSgi?.sourceMetricId != null ? String(editSgi.sourceMetricId) : '',
    sourceScope:       editSgi?.sourceScope != null ? String(editSgi.sourceScope) : '',
    evidenceRef:       editSgi?.evidenceRef ?? '',
  }), [editSgi]);

  async function submitEdit(values: Record<string, unknown>): Promise<void> {
    if (!editSgi?.id) return;
    const titleEn = String(values.titleEn ?? '').trim();
    const sector = String(values.sector ?? '').trim();
    const unit = String(values.unit ?? '').trim();
    const targetValue = Number(values.targetValue);
    const targetYear = Number(values.targetYear);
    if (!titleEn || !sector || !unit
        || !Number.isFinite(targetValue) || !Number.isFinite(targetYear)) {
      toast.danger(isAr
        ? 'العنوان والقطاع والوحدة والقيمة المستهدفة وسنة الهدف حقول مطلوبة'
        : 'Title, sector, unit, target value and target year are required');
      return;
    }
    const num = (v: unknown) => {
      const n = Number(v);
      return v === '' || v == null || !Number.isFinite(n) ? undefined : n;
    };
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      await updateSgi(editSgi.id, {
        sector: sector as SgiSector,
        titleEn,
        titleAr: str(values.titleAr),
        unit,
        targetValue,
        targetYear,
        baselineValue: num(values.baselineValue),
        baselineYear: num(values.baselineYear),
        currentValue: num(values.currentValue),
        ownerName: str(values.ownerName),
        ownerEmail: str(values.ownerEmail),
        nextMilestoneDate: str(values.nextMilestoneDate),
        nextMilestoneEn: str(values.nextMilestoneEn),
        nextMilestoneAr: str(values.nextMilestoneAr),
        descriptionEn: str(values.descriptionEn),
        descriptionAr: str(values.descriptionAr),
        notesEn: str(values.notesEn),
        notesAr: str(values.notesAr),
        valueSource: (str(values.valueSource) as SgiValueSource) ?? 'MANUAL',
        sourceMetricId: num(values.sourceMetricId),
        sourceScope: num(values.sourceScope),
        evidenceRef: str(values.evidenceRef),
      });
      toast.success(isAr ? 'حُدّث الالتزام' : 'Commitment updated');
      setEditSgi(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحديث الالتزام' : 'Failed to update commitment'));
    }
  }

  async function submitCreate(values: Record<string, unknown>): Promise<void> {
    const code = String(values.commitmentCode ?? '').trim();
    const titleEn = String(values.titleEn ?? '').trim();
    const sector = String(values.sector ?? '').trim();
    const unit = String(values.unit ?? '').trim();
    const targetValue = Number(values.targetValue);
    const targetYear = Number(values.targetYear);
    if (!code || !titleEn || !sector || !unit
        || !Number.isFinite(targetValue) || !Number.isFinite(targetYear)) {
      toast.danger(isAr
        ? 'الكود والعنوان والقطاع والوحدة والقيمة المستهدفة وسنة الهدف حقول مطلوبة'
        : 'Code, title, sector, unit, target value and target year are required');
      return;
    }
    const num = (v: unknown) => {
      const n = Number(v);
      return v === '' || v == null || !Number.isFinite(n) ? undefined : n;
    };
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      const created = await createSgi({
        commitmentCode: code,
        sector: sector as SgiSector,
        titleEn,
        titleAr: str(values.titleAr),
        unit,
        targetValue,
        targetYear,
        baselineValue: num(values.baselineValue),
        baselineYear: num(values.baselineYear),
        currentValue: num(values.currentValue),
        ownerName: str(values.ownerName),
        nextMilestoneEn: str(values.nextMilestoneEn),
        descriptionEn: str(values.descriptionEn),
        valueSource: (str(values.valueSource) as SgiValueSource) ?? 'MANUAL',
        sourceMetricId: num(values.sourceMetricId),
        sourceScope: num(values.sourceScope),
        status: 'ACTIVE',
      });
      toast.success(isAr
        ? `تمت إضافة الالتزام ${created.commitmentCode}`
        : `Commitment ${created.commitmentCode} added`);
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّرت إضافة الالتزام' : 'Failed to add commitment'));
    }
  }

  return (
    <div className="esg-stack">
      {/* KPI strip — tier counts + lifecycle totals */}
      <div className="kpi-grid">
        <Kpi Icon={Target}       label={isAr ? 'الالتزامات النشطة' : 'Active commitments'} value={agg?.activeTotal ?? 0}   tone="primary" to="/esg" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'على المسار' : 'On track'}                  value={agg?.byTier.ON_TRACK ?? 0} tone="success" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'مُعرَّض للخطر' : 'At risk'}                value={agg?.byTier.AT_RISK ?? 0}  tone="warning" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'متأخّر' : 'Delayed'}                       value={agg?.byTier.DELAYED ?? 0}  tone="danger" to="/esg" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'مُنجَزة' : 'Achieved'}                     value={agg?.achievedTotal ?? 0}   tone="success" to="/esg" />
        {/* Retired is intentionally tone-less (primary default — black ink)
            so it reads as a quiet historical counter alongside the
            louder status colors. KpiTone doesn't have a "neutral". */}
        <Kpi Icon={Trash2}       label={isAr ? 'مُتقاعَدة' : 'Retired'}                   value={agg?.retiredTotal ?? 0} to="/esg" />
      </div>

      <Card title={isAr ? 'التزامات المبادرة السعودية الخضراء' : 'Saudi Green Initiative commitments'} Icon={Target}
        action={<div className="row-actions">
          <Button variant="ghost" size="sm" Icon={Download}
            onClick={() => exportSgiXlsx(isAr ? 'ar' : 'en').catch(e =>
              toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر التصدير' : 'Export failed')))}>
            {isAr ? 'تصدير' : 'Export'}
          </Button>
          <Button variant="secondary" size="sm" Icon={RefreshCw} onClick={onSweep} disabled={sweeping}>
            {sweeping ? (isAr ? 'جارٍ الفحص…' : 'Sweeping…') : (isAr ? 'فحص المراقبة' : 'Run sweep')}
          </Button>
          <Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>
            {isAr ? 'التزام جديد' : 'New commitment'}
          </Button>
        </div>}>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'سجلّ الالتزامات المحفوظ مع تتبّع التقدّم، المالك، الموعد التالي، والسجل المراجعي. الإنجاز يتطلّب الوصول للهدف؛ التقاعد يتطلّب سبباً موثّقاً.'
            : 'Registered commitments with progress tracking, owner, next milestone, and audit trail. Achievement requires the target to be met; retirement requires a documented reason.'}
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : items.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد التزامات' : 'No commitments yet'}</InlineEmpty>
            : Array.from(bySector.entries()).map(([sector, sectorItems]) => (
              <div key={sector} className="mb-lg">
                <div className="esg-cma-pillar__title esg-text-eyebrow">
                  <Leaf size={12} className="esg-icon-inline" />
                  {sgiSectorLabel(sector as 'ENERGY', isAr ? 'ar' : 'en')}
                </div>
                {sectorItems.map(c => {
                  const progressPct = computeProgressPct(c);
                  const isActive = c.status === 'ACTIVE';
                  return (
                    <div key={c.id} className={`esg-sgi-card${c.status === 'ACHIEVED' ? ' esg-sgi-card--achieved' : c.status === 'RETIRED' ? ' esg-sgi-card--retired' : ''}`}>
                      <div className="esg-sgi-card__head">
                        <div>
                          <div className="esg-sgi-card__title">
                            {isAr && c.titleAr ? c.titleAr : c.titleEn}
                          </div>
                          <div className="esg-sgi-card__meta">
                            <span className="mono">{c.commitmentCode}</span>
                            {' · '}
                            {isAr ? 'الهدف' : 'Target'} {c.targetYear}
                            {c.ownerName && <> {' · '} {c.ownerName}</>}
                          </div>
                        </div>
                        <div className="esg-sgi-card__actions">
                          <Badge tone={sgiStatusTone(c.status)}>{sgiStatusLabel(c.status, isAr ? 'ar' : 'en')}</Badge>
                          {/* SGI-B — indicative tier badge (default AT_RISK factor;
                              the sweep + KPI strip are authoritative). */}
                          {(() => { const tier = computeTier(c);
                            return tier && tier !== 'ON_TRACK'
                              ? <Badge tone={sgiTierTone(tier)}>{sgiTierLabel(tier, isAr ? 'ar' : 'en')}</Badge>
                              : null; })()}
                          {isMilestoneOverdue(c) && (
                            <Badge tone="warning" title={isAr ? 'المرحلة التالية فائتة' : 'Next milestone overdue'}>
                              <Clock size={11} /> {isAr ? 'مرحلة فائتة' : 'Milestone overdue'}
                            </Badge>
                          )}
                          {/* SGI-E — evidence chip (Rule D proof: item FK or ref). */}
                          {(c.evidenceItemId != null || (c.evidenceRef && c.evidenceRef.trim())) && (
                            <Badge tone="success" title={c.evidenceRef || (isAr ? 'دليل مرفق' : 'Evidence attached')}>
                              <Paperclip size={11} /> {isAr ? 'دليل' : 'Evidence'}
                            </Badge>
                          )}
                          {isActive && (
                            <>
                              {c.valueSource && c.valueSource !== 'MANUAL' && (
                                <Button size="sm" variant="ghost" Icon={RefreshCw}
                                  title={isAr ? 'تحديث من المصدر' : 'Refresh from source'}
                                  onClick={() => onRefresh(c.id!)} />
                              )}
                              <Button size="sm" variant="ghost" Icon={Pencil}
                                title={isAr ? 'تعديل' : 'Edit'}
                                onClick={() => setEditSgi(c)} />
                              <Button size="sm" variant="secondary" Icon={CheckCircle2}
                                disabled={!canAchieve(c)}
                                title={!canAchieve(c) ? (isAr ? 'لم يصل القياس الحالي إلى الهدف بعد' : 'Current value has not met target yet') : undefined}
                                onClick={() => onAchieve(c.id!)}>
                                {isAr ? 'إنجاز' : 'Achieve'}
                              </Button>
                              <Button size="sm" variant="ghost" Icon={Trash2}
                                title={isAr ? 'تقاعد' : 'Retire'}
                                onClick={() => setRetiringId(c.id!)} />
                            </>
                          )}
                        </div>
                      </div>
                      <div className="esg-sgi-card__progress">
                        <div className="esg-sgi-card__progress-bar">
                          <div className="esg-sgi-card__progress-fill" style={{
                            width: `${Math.min(100, progressPct)}%`,
                            background: progressPct >= 75 ? 'var(--success)'
                                       : progressPct >= 40 ? 'var(--primary)'
                                       : 'var(--warning)',
                          }} />
                        </div>
                        <div className="mono esg-sgi-card__progress-label">
                          {progressPct.toFixed(1)}%
                        </div>
                      </div>
                      <div className="esg-sgi-card__values">
                        {c.baselineValue != null && <span>{isAr ? 'خط الأساس' : 'Baseline'}: <span className="mono">{Number(c.baselineValue).toLocaleString()}</span> {c.unit}</span>}
                        {c.currentValue != null && <span>{isAr ? 'الحالي' : 'Current'}: <span className="mono">{Number(c.currentValue).toLocaleString()}</span> {c.unit}</span>}
                        <span>{isAr ? 'الهدف' : 'Target'}: <span className="mono">{Number(c.targetValue).toLocaleString()}</span> {c.unit}</span>
                      </div>
                      {c.valueSource && c.valueSource !== 'MANUAL' && (
                        <div className="esg-sgi-card__notes" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <Link2 size={11} />
                          <span>
                            {isAr ? 'المصدر' : 'Source'}: {sgiValueSourceLabel(c.valueSource, isAr ? 'ar' : 'en')}
                            {c.valueSource === 'ESG_METRIC' && c.sourceMetricId != null && ` #${c.sourceMetricId}`}
                            {c.valueSource === 'GHG_TOTAL' && c.sourceScope != null && ` · ${isAr ? 'النطاق' : 'Scope'} ${c.sourceScope}`}
                            {c.currentValueAsOf && ` · ${isAr ? 'حتى' : 'as of'} ${c.currentValueAsOf}`}
                          </span>
                        </div>
                      )}
                      {(isAr ? c.notesAr : c.notesEn) && (
                        <div className="esg-sgi-card__notes">
                          {isAr && c.notesAr ? c.notesAr : c.notesEn}
                        </div>
                      )}
                      {c.nextMilestoneDate && (
                        <div className="esg-sgi-card__milestone">
                          <ArrowRight size={11} />
                          <span>{isAr ? 'المرحلة التالية' : 'Next milestone'} ({c.nextMilestoneDate}):</span>
                          <span>{isAr && c.nextMilestoneAr ? c.nextMilestoneAr : c.nextMilestoneEn}</span>
                        </div>
                      )}
                      {c.status === 'RETIRED' && c.retiredReason && (
                        <div className="esg-sgi-card__retired">
                          {isAr ? 'سبب التقاعد:' : 'Retired:'} {c.retiredReason}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            ))}
      </Card>

      <FormModal
        open={retiringId !== null}
        title={isAr ? 'تقاعد الالتزام' : 'Retire commitment'}
        fields={retireFields}
        initial={{ reason: '' }}
        size="md"
        submitLabel={isAr ? 'تقاعد' : 'Retire'}
        onClose={() => setRetiringId(null)}
        onSubmit={submitRetire}
      />
      <FormModal
        open={openNew}
        title={isAr ? 'التزام مبادرة خضراء جديد' : 'New Green-Initiative commitment'}
        fields={createFields}
        initial={{
          commitmentCode: '', sector: 'ENERGY', titleEn: '', titleAr: '',
          unit: '', targetValue: '', targetYear: 2030,
          baselineValue: '', baselineYear: '', currentValue: '',
          ownerName: '', nextMilestoneEn: '', descriptionEn: '',
          valueSource: 'MANUAL', sourceMetricId: '', sourceScope: '',
        }}
        size="lg"
        submitLabel={isAr ? 'إضافة الالتزام' : 'Add commitment'}
        onClose={() => setOpenNew(false)}
        onSubmit={submitCreate}
      />

      <FormModal
        open={editSgi !== null}
        title={editSgi
          ? (isAr ? `تعديل الالتزام · ${editSgi.commitmentCode}` : `Edit commitment · ${editSgi.commitmentCode}`)
          : ''}
        fields={editFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditSgi(null)}
        onSubmit={submitEdit}
      />
    </div>
  );
}
