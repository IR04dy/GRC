/**
 * F2-16 tranche 3 — the climate risks & opportunities register tab, moved out of `EsgPage.tsx`.
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
  Target,
  Trash2, CheckCircle2, AlertCircle,
  CloudLightning, Plus, ArrowRight, Link2,
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
  listClimateItems, createClimateItem, updateClimateItem, assessClimateItem, treatClimateItem,
  closeClimateItem, retireClimateItem, linkClimateItemToErm,
  riskScoreTone, riskScoreBand, shouldOfferErmLink,
  type ClimateRiskOpportunity, type CroKind,
} from '../../lib/climateRisk';

/**
 * Climate Risks & Opportunities tab — V55 vertical slice.
 *
 * Reads from `/api/sustainability/climate-risks` (Sustainability module
 * backend). Each row is a risk OR an opportunity (kind discriminator),
 * scored on a 5×5 likelihood/impact matrix. Items with risk-score ≥15
 * (High/Critical) and no existing ERM link surface an inline "Link to
 * ERM" prompt — that's Rule 8 from the spec.
 */
export function ClimateTab() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<ClimateRiskOpportunity[]>([]);
  const [loading, setLoading] = useState(true);
  const [openNew, setOpenNew] = useState(false);
  const [editItem, setEditItem] = useState<ClimateRiskOpportunity | null>(null);
  // ERM-link prompt modal (replaces window.prompt). Holds the climate-item
  // id so the modal knows which row to link when the user submits.
  const [linkingErmFor, setLinkingErmFor] = useState<number | null>(null);

  async function reload() {
    setLoading(true);
    try {
      const page = await listClimateItems({ size: 100 });
      setItems(page.content);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('cliLoadFailed'));
      setItems([]);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  const risks         = items.filter(i => i.kind === 'RISK');
  const opportunities = items.filter(i => i.kind === 'OPPORTUNITY');
  const needsErmLink  = risks.filter(shouldOfferErmLink);

  async function onCreate(values: Record<string, unknown>) {
    try {
      await createClimateItem({
        kind:          (values.kind as CroKind) ?? 'RISK',
        category:      String(values.category ?? 'PHYSICAL'),
        titleEn:       String(values.titleEn ?? '').trim(),
        titleAr:       String(values.titleAr ?? '').trim() || undefined,
        descriptionEn: String(values.descriptionEn ?? '').trim() || undefined,
        timeHorizon:   (values.timeHorizon as 'SHORT' | 'MEDIUM' | 'LONG') ?? 'MEDIUM',
        likelihood:    Number(values.likelihood ?? 3),
        impact:        Number(values.impact ?? 3),
        reportingYear: Number(values.reportingYear ?? new Date().getFullYear()),
        ownerName:     String(values.ownerName ?? '').trim() || undefined,
      });
      toast.success(t('cliCreated'));
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed'));
    }
  }
  // Edit (PUT) — partial null-means-keep on the server. kind stays fixed
  // (the category whitelist is kind-specific) and is NOT shown in the form,
  // but it MUST be echoed in the payload: the update endpoint binds a JPA
  // @RequestBody entity whose kind defaults to "RISK", so an omitted kind
  // arrives non-null and would clobber an OPPORTUNITY to RISK — failing the
  // category validation. Echoing editItem.kind makes the overwrite a no-op.
  // reportingYear is immutable via update. The server recomputes
  // riskScore = likelihood × impact; status is owned by the transition
  // endpoints and never touched here.
  async function onUpdate(values: Record<string, unknown>) {
    if (!editItem) return;
    try {
      await updateClimateItem(editItem.id!, {
        kind:          editItem.kind,
        category:      String(values.category ?? editItem.category),
        timeHorizon:   (values.timeHorizon as 'SHORT' | 'MEDIUM' | 'LONG') ?? editItem.timeHorizon,
        titleEn:       String(values.titleEn ?? '').trim(),
        titleAr:       String(values.titleAr ?? '').trim() || undefined,
        descriptionEn: String(values.descriptionEn ?? '').trim() || undefined,
        likelihood:    Number(values.likelihood ?? editItem.likelihood),
        impact:        Number(values.impact ?? editItem.impact),
        ownerName:     String(values.ownerName ?? '').trim() || undefined,
      });
      toast.success(t('cliUpdated'));
      setEditItem(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed'));
    }
  }
  async function onAssess(id: number)  { try { await assessClimateItem(id);  toast.success(t('cliAssessed'));  await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed')); } }
  async function onTreat(id: number)   { try { await treatClimateItem(id);   toast.success(t('cliTreated'));   await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed')); } }
  async function onClose_(id: number)  { try { await closeClimateItem(id);   toast.success(t('cliClosed'));    await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed')); } }
  async function onRetire(id: number)  { if (!window.confirm(t('cliRetireConfirm'))) return; try { await retireClimateItem(id); toast.success(t('cliRetired')); await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed')); } }
  async function submitErmLink(values: Record<string, unknown>) {
    const id = linkingErmFor;
    if (id == null) return;
    const ermId = Number(values.ermRiskId);
    if (!Number.isFinite(ermId) || ermId <= 0) {
      toast.danger(isAr ? 'مُعرّف غير صالح' : 'Invalid ERM risk ID');
      return;
    }
    try {
      await linkClimateItemToErm(id, ermId);
      toast.success(t('cliLinkedErm'));
      setLinkingErmFor(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed'));
    }
  }

  const ermLinkFields: FieldDef[] = [
    { kind: 'number', key: 'ermRiskId', required: true, min: 1, span: 12,
      label: isAr ? 'مُعرّف المخاطرة في سجلّ المخاطر المؤسسية (ERM)'
                  : 'ERM risk ID to link' },
  ];

  const FIELDS: FieldDef[] = [
    { kind: 'select', key: 'kind',         label: t('cliFldKind') + ' *',        required: true, span: 4,
      options: [{ value: 'RISK', label: isAr ? 'مخاطرة' : 'Risk' }, { value: 'OPPORTUNITY', label: isAr ? 'فرصة' : 'Opportunity' }] },
    { kind: 'select', key: 'category',     label: t('cliFldCategory') + ' *',    required: true, span: 4,
      options: [
        { value: 'PHYSICAL',     label: isAr ? 'مادي'          : 'Physical' },
        { value: 'TRANSITION',   label: isAr ? 'انتقالي'        : 'Transition' },
        { value: 'REGULATORY',   label: isAr ? 'تنظيمي'         : 'Regulatory' },
        { value: 'MARKET',       label: isAr ? 'سوق'           : 'Market' },
        { value: 'TECHNOLOGY',   label: isAr ? 'تقني'          : 'Technology' },
        { value: 'REPUTATION',   label: isAr ? 'سُمعة'          : 'Reputation' },
        { value: 'SUPPLY_CHAIN', label: isAr ? 'سلسلة الإمداد' : 'Supply chain' },
        { value: 'RESOURCE_EFFICIENCY', label: isAr ? 'كفاءة الموارد' : 'Resource efficiency (opp.)' },
        { value: 'ENERGY_SOURCE',       label: isAr ? 'مصدر طاقة'      : 'Energy source (opp.)' },
        { value: 'PRODUCT_SERVICE',     label: isAr ? 'منتج/خدمة'     : 'Product/service (opp.)' },
        { value: 'MARKETS',             label: isAr ? 'أسواق'         : 'Markets (opp.)' },
        { value: 'RESILIENCE',          label: isAr ? 'صمود'          : 'Resilience (opp.)' },
      ] },
    { kind: 'select', key: 'timeHorizon',  label: t('cliFldHorizon') + ' *',     required: true, span: 4,
      options: [
        { value: 'SHORT',  label: isAr ? 'قصير المدى (≤ سنة)'  : 'Short (≤1y)' },
        { value: 'MEDIUM', label: isAr ? 'متوسط المدى (1-5 س)' : 'Medium (1-5y)' },
        { value: 'LONG',   label: isAr ? 'طويل المدى (> 5 س)'  : 'Long (>5y)' },
      ] },
    { kind: 'text',     key: 'titleEn',       label: t('cliFldTitleEn') + ' *', required: true, span: 12 },
    { kind: 'text',     key: 'titleAr',       label: t('cliFldTitleAr'),         span: 12 },
    { kind: 'textarea', key: 'descriptionEn', label: t('cliFldDescription'),     rows: 3, span: 12 },
    { kind: 'number',   key: 'likelihood',    label: t('cliFldLikelihood') + ' *', required: true, min: 1, max: 5, span: 3 },
    { kind: 'number',   key: 'impact',        label: t('cliFldImpact') + ' *',     required: true, min: 1, max: 5, span: 3 },
    { kind: 'number',   key: 'reportingYear', label: t('cliFldYear'),              min: 2020, max: 2050, span: 3 },
    { kind: 'text',     key: 'ownerName',     label: t('cliFldOwner'),             span: 3 },
  ];

  // Category whitelist is kind-specific on the server; scope the edit
  // options to the item's kind so an edit can never produce an invalid
  // kind/category pair. Kind itself is omitted from the edit surface.
  const RISK_CAT_OPTS = [
    { value: 'PHYSICAL',     label: isAr ? 'مادي'          : 'Physical' },
    { value: 'TRANSITION',   label: isAr ? 'انتقالي'        : 'Transition' },
    { value: 'REGULATORY',   label: isAr ? 'تنظيمي'         : 'Regulatory' },
    { value: 'MARKET',       label: isAr ? 'سوق'           : 'Market' },
    { value: 'TECHNOLOGY',   label: isAr ? 'تقني'          : 'Technology' },
    { value: 'REPUTATION',   label: isAr ? 'سُمعة'          : 'Reputation' },
    { value: 'SUPPLY_CHAIN', label: isAr ? 'سلسلة الإمداد' : 'Supply chain' },
  ];
  const OPP_CAT_OPTS = [
    { value: 'RESOURCE_EFFICIENCY', label: isAr ? 'كفاءة الموارد' : 'Resource efficiency' },
    { value: 'ENERGY_SOURCE',       label: isAr ? 'مصدر طاقة'      : 'Energy source' },
    { value: 'PRODUCT_SERVICE',     label: isAr ? 'منتج/خدمة'     : 'Product/service' },
    { value: 'MARKETS',             label: isAr ? 'أسواق'         : 'Markets' },
    { value: 'RESILIENCE',          label: isAr ? 'صمود'          : 'Resilience' },
  ];
  const editCatOpts = editItem?.kind === 'OPPORTUNITY' ? OPP_CAT_OPTS : RISK_CAT_OPTS;
  const EDIT_FIELDS: FieldDef[] = [
    { kind: 'select',   key: 'category',      label: t('cliFldCategory') + ' *', required: true, span: 6, options: editCatOpts },
    { kind: 'select',   key: 'timeHorizon',   label: t('cliFldHorizon') + ' *',  required: true, span: 6,
      options: [
        { value: 'SHORT',  label: isAr ? 'قصير المدى (≤ سنة)'  : 'Short (≤1y)' },
        { value: 'MEDIUM', label: isAr ? 'متوسط المدى (1-5 س)' : 'Medium (1-5y)' },
        { value: 'LONG',   label: isAr ? 'طويل المدى (> 5 س)'  : 'Long (>5y)' },
      ] },
    { kind: 'text',     key: 'titleEn',       label: t('cliFldTitleEn') + ' *', required: true, span: 12 },
    { kind: 'text',     key: 'titleAr',       label: t('cliFldTitleAr'),         span: 12 },
    { kind: 'textarea', key: 'descriptionEn', label: t('cliFldDescription'),     rows: 3, span: 12 },
    { kind: 'number',   key: 'likelihood',    label: t('cliFldLikelihood') + ' *', required: true, min: 1, max: 5, span: 3 },
    { kind: 'number',   key: 'impact',        label: t('cliFldImpact') + ' *',     required: true, min: 1, max: 5, span: 3 },
    { kind: 'text',     key: 'ownerName',     label: t('cliFldOwner'),             span: 6 },
  ];
  const editInitial = useMemo(() => editItem ? {
    category:      editItem.category,
    timeHorizon:   editItem.timeHorizon,
    titleEn:       editItem.titleEn,
    titleAr:       editItem.titleAr ?? '',
    descriptionEn: editItem.descriptionEn ?? '',
    likelihood:    editItem.likelihood,
    impact:        editItem.impact,
    ownerName:     editItem.ownerName ?? '',
  } : {}, [editItem]);

  return (
    <div className="esg-stack">
      {/* KPI strip — risks vs opportunities + Rule-8 alert chip */}
      <div className="kpi-grid">
        <Kpi Icon={CloudLightning} label={t('cliKpiRisks')}    value={risks.length}         tone="warning" to="/esg" />
        <Kpi Icon={Target}          label={t('cliKpiOpps')}      value={opportunities.length} tone="success" to="/esg" />
        <Kpi Icon={AlertCircle}     label={t('cliKpiHighScore')} value={risks.filter(r => (r.riskScore ?? 0) >= 15).length} tone="danger" to="/risk" />
        <Kpi Icon={Link2}           label={t('cliKpiNeedsErm')}  value={needsErmLink.length}  tone={needsErmLink.length > 0 ? 'danger' : 'success'} to="/risk" />
      </div>

      <Card title={t('cliPageTitle')} Icon={CloudLightning}
        action={<Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>{t('cliNew')}</Button>}>
        {loading
          ? <InlineEmpty>{t('cliLoading')}</InlineEmpty>
          : items.length === 0
            ? <InlineEmpty>{t('cliEmpty')}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{t('cliColCode')}</th>
                    <th>{t('cliColTitle')}</th>
                    <th>{t('cliColKind')}</th>
                    <th>{t('cliColCategory')}</th>
                    <th>{t('cliColHorizon')}</th>
                    <th className="esg-th-center">{t('cliColScore')}</th>
                    <th>{t('cliColStatus')}</th>
                    <th className="esg-text-end">{t('cliColActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map(it => {
                    const tone = riskScoreTone(it.riskScore ?? 0);
                    const band = riskScoreBand(it.riskScore ?? 0, isAr ? 'ar' : 'en');
                    return (
                      <tr key={it.id}>
                        <td className="mono">{it.itemCode ?? `#${it.id}`}</td>
                        <td>
                          <div className="esg-cell-title">
                            {isAr && it.titleAr ? it.titleAr : it.titleEn}
                          </div>
                          {it.ownerName && <div className="text-2xs text-muted esg-cell-sub">{it.ownerName}</div>}
                        </td>
                        <td><Badge tone={it.kind === 'RISK' ? 'danger' : 'success'}>{isAr ? (it.kind === 'RISK' ? 'مخاطرة' : 'فرصة') : it.kind}</Badge></td>
                        <td className="text-xs">{it.category}</td>
                        <td><Badge tone="neutral">{it.timeHorizon}</Badge></td>
                        <td className="esg-text-center">
                          <Badge tone={tone}>{it.riskScore} · {band}</Badge>
                        </td>
                        <td><Badge tone={it.status === 'CLOSED' ? 'success' : it.status === 'RETIRED' ? 'neutral' : it.status === 'TREATED' ? 'info' : 'warning'}>{it.status}</Badge></td>
                        <td className="esg-text-end">
                          <div className="esg-actions-row">
                            {/* Rule 8: offer ERM link inline when score >= 15 and not yet linked */}
                            {shouldOfferErmLink(it) && (
                              <Button variant="primary" size="sm" Icon={Link2}
                                onClick={() => setLinkingErmFor(it.id!)}>{t('cliActLinkErm')}</Button>
                            )}
                            {it.status !== 'RETIRED' && (
                              <Button variant="ghost" size="sm" Icon={Pencil}
                                title={t('cliActEdit')} onClick={() => setEditItem(it)} />
                            )}
                            {it.status === 'IDENTIFIED' && (
                              <Button variant="secondary" size="sm" Icon={ArrowRight} onClick={() => onAssess(it.id!)}>{t('cliActAssess')}</Button>
                            )}
                            {it.status === 'ASSESSED' && (
                              <Button variant="secondary" size="sm" Icon={ArrowRight} onClick={() => onTreat(it.id!)}>{t('cliActTreat')}</Button>
                            )}
                            {it.status === 'TREATED' && (
                              <Button variant="secondary" size="sm" Icon={CheckCircle2} onClick={() => onClose_(it.id!)}>{t('cliActClose')}</Button>
                            )}
                            {it.status !== 'RETIRED' && it.status !== 'CLOSED' && (
                              <Button variant="ghost" size="sm" Icon={Trash2}
                                title={isAr ? 'سحب من السجل' : 'Retire'}
                                onClick={() => onRetire(it.id!)} />
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
        open={openNew}
        title={t('cliNewTitle')}
        fields={FIELDS}
        initial={{ kind: 'RISK', category: 'PHYSICAL', timeHorizon: 'MEDIUM', likelihood: 3, impact: 3, reportingYear: new Date().getFullYear() }}
        onClose={() => setOpenNew(false)}
        onSubmit={onCreate}
      />

      <FormModal
        open={!!editItem}
        title={editItem ? `${t('cliEditTitle')} — ${editItem.itemCode ?? `#${editItem.id}`}` : ''}
        fields={EDIT_FIELDS}
        initial={editInitial}
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditItem(null)}
        onSubmit={onUpdate}
      />

      {/* ERM-link prompt — replaces window.prompt. Stays in-window so the
          user can see which row they're linking from. */}
      <FormModal
        open={linkingErmFor !== null}
        title={isAr ? 'ربط بسجل المخاطر المؤسسية' : 'Link to ERM register'}
        fields={ermLinkFields}
        initial={{ ermRiskId: '' }}
        size="md"
        submitLabel={isAr ? 'ربط' : 'Link'}
        onClose={() => setLinkingErmFor(null)}
        onSubmit={submitErmLink}
      />
    </div>
  );
}
