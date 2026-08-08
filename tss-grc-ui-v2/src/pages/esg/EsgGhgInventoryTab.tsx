/**
 * F2-16 tranche 3 — the GHG inventory tab, moved out of `EsgPage.tsx`.
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
  Leaf, TrendingDown,
  Trash2, CheckCircle2, AlertCircle,
  Plus, ArrowRight,
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
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import {
  listGhgRecords, ghgAggregate, ghgYoy,
  createGhgRecord, updateGhgRecord, submitGhgRecord, verifyGhgRecord, approveGhgRecord, archiveGhgRecord,
  ghgStatusTone, canVerify,
  type GhgEmissionRecord, type GhgScope, type GhgAggregate, type GhgYoY,
} from '../../lib/ghgInventory';

// ─── GHG Inventory tab (V56) ─────────────────────────────────────────────────
//
// Live, queryable, verifiable GHG inventory — replaces the static mock
// totals on the overview tab with a per-record register. Surfaces:
//   • Aggregate KPIs (Scope 1/2/3 totals, grand total, Scope-3 coverage %)
//   • Rule-1 dashboard alert when Scope-3 weighted coverage < 70%
//   • YoY chips per scope (computed server-side from prior-year aggregate)
//   • Status-gated lifecycle buttons (Submit → Verify → Approve)
//   • Rule-2 client mirror: Verify button disabled until evidence_ref set

export function GhgInventoryTab() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [year, setYear]   = useState<number>(new Date().getFullYear() - 1); // align with seed (2025)
  const [scope, setScope] = useState<GhgScope | undefined>(undefined);
  const [rows, setRows]   = useState<GhgEmissionRecord[]>([]);
  const [agg, setAgg]     = useState<GhgAggregate | null>(null);
  const [yoy, setYoy]     = useState<GhgYoY | null>(null);
  const [loading, setLoading] = useState(true);
  const [openNew, setOpenNew] = useState(false);
  const [editRec, setEditRec] = useState<GhgEmissionRecord | null>(null);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — GHG records list, aggregate, and YoY are
    // independent; loss of one shouldn't blank the GHG tab.
    const [page, aggData, yoyData] = await Promise.all([
      listGhgRecords({ year, scope, size: 200 }).catch(() => ({ content: [] as GhgEmissionRecord[], totalElements: 0 })),
      ghgAggregate(year).catch(() => null as GhgAggregate | null),
      ghgYoy(year).catch(() => null as GhgYoY | null),
    ]);
    setRows(page.content);
    setAgg(aggData);
    setYoy(yoyData);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, [year, scope]); // eslint-disable-line react-hooks/exhaustive-deps

  async function onCreate(values: Record<string, unknown>) {
    try {
      await createGhgRecord({
        reportingYear: Number(values.reportingYear ?? year),
        scope: Number(values.scope ?? 1) as GhgScope,
        category: String(values.category ?? 'STATIONARY_COMBUSTION'),
        scope3Category: values.scope3Category ? Number(values.scope3Category) : undefined,
        sourceNameEn: String(values.sourceNameEn ?? '').trim(),
        sourceNameAr: String(values.sourceNameAr ?? '').trim() || undefined,
        emissionValueTco2e: Number(values.emissionValueTco2e ?? 0),
        coveragePercent:    Number(values.coveragePercent ?? 100),
        evidenceRef: String(values.evidenceRef ?? '').trim() || undefined,
        ownerName:   String(values.ownerName ?? '').trim() || undefined,
        methodology: String(values.methodology ?? '').trim() || undefined,
        verified: false,
      });
      toast.success(t('ghgCreated'));
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('ghgActionFailed'));
    }
  }
  // Edit (PUT) — partial null-means-keep on the server; reportingYear and
  // scope are immutable identity (the update endpoint never reads them) so
  // they're excluded from the edit surface. emissionValueTco2e is server-
  // recomputed from activityData × emissionFactor only when both are present;
  // for the manually-entered rows here it is taken verbatim.
  async function onUpdate(values: Record<string, unknown>) {
    if (!editRec) return;
    try {
      await updateGhgRecord(editRec.id!, {
        category:           String(values.category ?? editRec.category),
        sourceNameEn:       String(values.sourceNameEn ?? '').trim(),
        sourceNameAr:       String(values.sourceNameAr ?? '').trim() || undefined,
        emissionValueTco2e: Number(values.emissionValueTco2e ?? 0),
        coveragePercent:    Number(values.coveragePercent ?? 100),
        methodology:        String(values.methodology ?? '').trim() || undefined,
        evidenceRef:        String(values.evidenceRef ?? '').trim() || undefined,
        ownerName:          String(values.ownerName ?? '').trim() || undefined,
      });
      toast.success(t('ghgUpdated'));
      setEditRec(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('ghgActionFailed'));
    }
  }
  async function onSubmit_(id: number) {
    try { await submitGhgRecord(id); toast.success(t('ghgSubmitted')); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('ghgActionFailed')); }
  }
  async function onVerify(id: number) {
    try { await verifyGhgRecord(id); toast.success(t('ghgVerified')); await reload(); }
    catch (e) {
      // Rule 2 — server rejects with a clear message if evidence missing.
      toast.danger(e instanceof ApiError ? e.message : t('ghgActionFailed'));
    }
  }
  async function onApprove(id: number) {
    try { await approveGhgRecord(id); toast.success(t('ghgApproved')); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('ghgActionFailed')); }
  }
  async function onArchive(id: number) {
    // ARCHIVED is terminal (no transition back out), so confirm first.
    if (!window.confirm(isAr
      ? 'أرشفة هذا السجل؟ لا يمكن التراجع عن الأرشفة.'
      : 'Archive this record? Archiving cannot be undone.')) return;
    try { await archiveGhgRecord(id); toast.success(isAr ? 'تمت أرشفة السجل' : 'Record archived'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('ghgActionFailed')); }
  }

  const FIELDS: FieldDef[] = [
    { kind: 'number',   key: 'reportingYear',      label: t('ghgFldYear') + ' *',  required: true, min: 2020, max: 2050, span: 3 },
    { kind: 'select',   key: 'scope',              label: t('ghgFldScope') + ' *', required: true, span: 3,
      options: [{ value: '1', label: 'Scope 1' }, { value: '2', label: 'Scope 2' }, { value: '3', label: 'Scope 3' }] },
    { kind: 'select',   key: 'category',           label: t('ghgFldCategory') + ' *', required: true, span: 6,
      options: [
        { value: 'STATIONARY_COMBUSTION',    label: 'Stationary combustion (S1)' },
        { value: 'MOBILE_COMBUSTION',        label: 'Mobile combustion (S1)' },
        { value: 'FUGITIVE',                 label: 'Fugitive (S1)' },
        { value: 'PROCESS',                  label: 'Process (S1)' },
        { value: 'PURCHASED_ELECTRICITY',    label: 'Purchased electricity (S2)' },
        { value: 'PURCHASED_HEAT_STEAM',     label: 'Purchased heat/steam (S2)' },
        { value: 'PURCHASED_GOODS_SERVICES', label: 'Purchased goods & services (S3)' },
        { value: 'CAPITAL_GOODS',            label: 'Capital goods (S3)' },
        { value: 'BUSINESS_TRAVEL',          label: 'Business travel (S3)' },
        { value: 'EMPLOYEE_COMMUTING',       label: 'Employee commuting (S3)' },
        { value: 'UPSTREAM_TRANSPORT',       label: 'Upstream transport (S3)' },
        { value: 'WASTE_OPERATIONS',         label: 'Waste from operations (S3)' },
        { value: 'USE_OF_SOLD_PRODUCTS',     label: 'Use of sold products (S3)' },
      ] },
    // V295 — GHG Protocol Scope-3 value-chain category (backend rejects it on S1/S2 rows).
    { kind: 'select',   key: 'scope3Category',     label: isAr ? 'فئة النطاق 3 (1–15)' : 'Scope-3 category (1–15)', span: 6,
      options: [
        { value: '', label: isAr ? '— لا ينطبق —' : '— n/a —' },
        { value: '1',  label: '1 · Purchased goods & services' },
        { value: '2',  label: '2 · Capital goods' },
        { value: '3',  label: '3 · Fuel & energy activities' },
        { value: '4',  label: '4 · Upstream transport' },
        { value: '5',  label: '5 · Waste in operations' },
        { value: '6',  label: '6 · Business travel' },
        { value: '7',  label: '7 · Employee commuting' },
        { value: '8',  label: '8 · Upstream leased assets' },
        { value: '9',  label: '9 · Downstream transport' },
        { value: '10', label: '10 · Processing of sold products' },
        { value: '11', label: '11 · Use of sold products' },
        { value: '12', label: '12 · End-of-life of sold products' },
        { value: '13', label: '13 · Downstream leased assets' },
        { value: '14', label: '14 · Franchises' },
        { value: '15', label: '15 · Investments' },
      ] },
    { kind: 'text',     key: 'sourceNameEn',       label: t('ghgFldSourceEn') + ' *', required: true, span: 12 },
    { kind: 'text',     key: 'sourceNameAr',       label: t('ghgFldSourceAr'),         span: 12 },
    { kind: 'number',   key: 'emissionValueTco2e', label: t('ghgFldValue') + ' *',     required: true, min: 0, span: 6 },
    { kind: 'number',   key: 'coveragePercent',    label: t('ghgFldCoverage'),         min: 0, max: 100, span: 6 },
    { kind: 'text',     key: 'methodology',        label: t('ghgFldMethodology'),      placeholder: 'GHG Protocol Corporate Standard', span: 6 },
    { kind: 'text',     key: 'evidenceRef',        label: t('ghgFldEvidence'),         span: 6 },
    { kind: 'text',     key: 'ownerName',          label: t('ghgFldOwner'),            span: 6 },
  ];

  // Edit reuses the create field defs minus the immutable identity fields
  // (reporting year + scope are fixed once a record exists).
  const EDIT_FIELDS: FieldDef[] = FIELDS.filter(f => f.key !== 'reportingYear' && f.key !== 'scope');
  const editInitial = useMemo(() => editRec ? {
    category:           editRec.category,
    sourceNameEn:       editRec.sourceNameEn,
    sourceNameAr:       editRec.sourceNameAr ?? '',
    emissionValueTco2e: Number(editRec.emissionValueTco2e),
    coveragePercent:    editRec.coveragePercent != null ? Number(editRec.coveragePercent) : 100,
    methodology:        editRec.methodology ?? '',
    evidenceRef:        editRec.evidenceRef ?? '',
    ownerName:          editRec.ownerName ?? '',
  } : {}, [editRec]);

  function pctChip(v?: number) {
    if (v == null) return '—';
    const sign = v > 0 ? '+' : '';
    return `${sign}${Number(v).toFixed(1)}%`;
  }

  return (
    <div className="esg-stack">
      {/* Year + scope filter strip */}
      <div className="row-gap-md" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <span className="text-2xs text-muted esg-text-eyebrow">{t('ghgFilterYear')}</span>
        <select className="input" style={{ width: 'auto' }} value={year} onChange={e => setYear(Number(e.target.value))}>
          {[2024, 2025, 2026].map(y => <option key={y} value={y}>{y}</option>)}
        </select>
        <span className="text-2xs text-muted" style={{ textTransform: 'uppercase', marginInlineStart: 12 }}>{t('ghgFilterScope')}</span>
        <select className="input" style={{ width: 'auto' }} value={scope ?? ''} onChange={e => setScope(e.target.value ? Number(e.target.value) as GhgScope : undefined)}>
          <option value="">{isAr ? 'الكل' : 'All'}</option>
          <option value="1">Scope 1</option>
          <option value="2">Scope 2</option>
          <option value="3">Scope 3</option>
        </select>
      </div>

      {/* KPI strip — totals + YoY + Rule-1 flag */}
      <div className="kpi-grid">
        <Kpi Icon={Leaf} label={t('ghgKpiScope1')} value={agg ? `${(Number(agg.scope1Total) / 1000).toFixed(1)}k` : '—'} tone="success" onClick={() => setScope(1)} active={scope === 1} />
        <Kpi Icon={Leaf} label={t('ghgKpiScope2')} value={agg ? `${(Number(agg.scope2Total) / 1000).toFixed(1)}k` : '—'} tone="success" onClick={() => setScope(2)} active={scope === 2} />
        <Kpi Icon={Leaf} label={t('ghgKpiScope3')} value={agg ? `${(Number(agg.scope3Total) / 1000).toFixed(1)}k` : '—'} tone="warning" onClick={() => setScope(3)} active={scope === 3} />
        <Kpi Icon={Leaf} label={t('ghgKpiTotal')}  value={agg ? `${(Number(agg.grandTotal) / 1000).toFixed(1)}k` : '—'} tone="primary" onClick={() => setScope(undefined)} active={scope === undefined} />
        <Kpi Icon={CheckCircle2} label={t('ghgKpiS3Coverage')} value={agg ? `${Number(agg.scope3Coverage).toFixed(0)}%` : '—'}
          tone={agg?.scope3PartialFlag ? 'danger' : 'success'} to="/esg" />
      </div>

      {/* Rule 1 — Scope-3 partial-disclosure warning */}
      {agg?.scope3PartialFlag && (
        <div style={{
          display: 'flex', gap: 10, alignItems: 'flex-start',
          padding: 12, borderRadius: 'var(--r-md)',
          background: 'var(--danger-bg)', borderInlineStart: '4px solid var(--danger)',
        }}>
          <AlertCircle size={18} style={{ color: 'var(--danger-fg)', flexShrink: 0, marginTop: 2 }} />
          <div className="text-sm" style={{ color: 'var(--danger-fg)', lineHeight: 1.5 }}>
            <strong>{t('ghgRule1Title')}:</strong> {t('ghgRule1Body').replace('{coverage}', Number(agg.scope3Coverage).toFixed(0))}
          </div>
        </div>
      )}

      {/* YoY chips */}
      {yoy && (
        <Card title={t('ghgYoyTitle')} Icon={TrendingDown}>
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            {[
              { label: 'Scope 1', v: Number(yoy.scope1YoY) },
              { label: 'Scope 2', v: Number(yoy.scope2YoY) },
              { label: 'Scope 3', v: Number(yoy.scope3YoY) },
              { label: t('ghgYoyTotal'), v: Number(yoy.totalYoY) },
            ].map((it, i) => (
              <div key={i} className="stack-xs">
                <span className="text-2xs text-muted esg-text-eyebrow">{it.label}</span>
                <Badge tone={it.v < 0 ? 'success' : it.v > 0 ? 'danger' : 'neutral'}>{pctChip(it.v)}</Badge>
              </div>
            ))}
          </div>
        </Card>
      )}

      <Card title={t('ghgPageTitle')} Icon={Leaf}
        action={<Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>{t('ghgNew')}</Button>}>
        {loading
          ? <InlineEmpty>{t('ghgLoading')}</InlineEmpty>
          : rows.length === 0
            ? <InlineEmpty>{t('ghgEmpty')}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{t('ghgColCode')}</th>
                    <th>{t('ghgColScope')}</th>
                    <th>{t('ghgColSource')}</th>
                    <th>{t('ghgColCategory')}</th>
                    <th className="esg-text-end">{t('ghgColValue')}</th>
                    <th className="esg-text-center">{t('ghgColCoverage')}</th>
                    <th>{t('ghgColStatus')}</th>
                    <th className="esg-text-end">{t('ghgColActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map(r => (
                    <tr key={r.id}>
                      <td className="mono">{r.recordCode ?? `#${r.id}`}</td>
                      <td><Badge tone={r.scope === 1 ? 'success' : r.scope === 2 ? 'info' : 'warning'}>S{r.scope}</Badge></td>
                      <td>
                        <div className="esg-cell-title">
                          {isAr && r.sourceNameAr ? r.sourceNameAr : r.sourceNameEn}
                        </div>
                        {r.facility && <div className="text-2xs text-muted esg-cell-sub">{r.facility}</div>}
                      </td>
                      <td className="text-xs">{r.category}</td>
                      <td className="mono esg-text-end">{Number(r.emissionValueTco2e).toLocaleString()}</td>
                      <td className="esg-text-center">
                        <Badge tone={Number(r.coveragePercent) >= 90 ? 'success' : Number(r.coveragePercent) >= 70 ? 'info' : 'warning'}>
                          {Number(r.coveragePercent).toFixed(0)}%
                        </Badge>
                      </td>
                      <td><Badge tone={ghgStatusTone(r.status)}>{statusLabel(r.status, lang)}</Badge></td>
                      <td className="esg-text-end">
                        <div className="esg-actions-row">
                          {(r.status === 'DRAFT' || r.status === 'SUBMITTED') && (
                            <Button variant="ghost" size="sm" Icon={Pencil}
                              title={isAr ? 'تعديل' : 'Edit'}
                              onClick={() => setEditRec(r)} />
                          )}
                          {r.status === 'DRAFT'     && <Button variant="secondary" size="sm" Icon={ArrowRight} onClick={() => onSubmit_(r.id!)}>{t('ghgActSubmit')}</Button>}
                          {r.status === 'SUBMITTED' && (
                            <Button variant="secondary" size="sm" Icon={CheckCircle2}
                              onClick={() => onVerify(r.id!)}
                              disabled={!canVerify(r)}
                              title={canVerify(r) ? '' : (isAr ? 'يجب إرفاق دليل قبل التحقّق (قاعدة 2)' : 'Attach evidence before verifying (Rule 2)')}>
                              {t('ghgActVerify')}
                            </Button>
                          )}
                          {r.status === 'VERIFIED'  && <Button variant="primary"   size="sm" Icon={CheckCircle2} onClick={() => onApprove(r.id!)}>{t('ghgActApprove')}</Button>}
                          {r.status !== 'ARCHIVED'  && (
                            <Button variant="ghost" size="sm" Icon={Trash2}
                              title={isAr ? 'أرشفة' : 'Archive'}
                              onClick={() => onArchive(r.id!)} />
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
      </Card>

      <FormModal
        open={openNew}
        title={t('ghgNewTitle')}
        fields={FIELDS}
        initial={{ reportingYear: year, scope: 1, coveragePercent: 100 }}
        onClose={() => setOpenNew(false)}
        onSubmit={onCreate}
      />

      <FormModal
        open={!!editRec}
        title={editRec ? `${isAr ? 'تعديل سجل الانبعاثات' : 'Edit emission record'} — ${editRec.recordCode ?? `#${editRec.id}`}` : ''}
        fields={EDIT_FIELDS}
        initial={editInitial}
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditRec(null)}
        onSubmit={onUpdate}
      />
    </div>
  );
}
