/**
 * F2-16 tranche 2 — the ESG double-materiality assessment tab, moved out of `EsgPage.tsx`.
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
  Leaf, Target,
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
  listMateriality, generateMatrix, scoreMaterialTopic, approveMaterialTopic,
  archiveMaterialTopic, createMaterialTopic, updateMaterialTopic,
  materialTierTone, materialTierLabel, materialCategoryLabel,
  materialStatusTone, materialStatusLabel,
  type MaterialTopic, type MaterialMatrix, type MaterialCategory,
} from '../../lib/sustainabilityMateriality';

// ─── ESG Materiality tab (V62 — slice 8) ─────────────────────────────────────
//
// Double-materiality assessment: each topic is scored on two INDEPENDENT
// 0-5 axes — impact materiality (inside-out) and financial materiality
// (outside-in). Service computes materiality_score = max(impact, financial)
// (material if material from either perspective) and buckets into
// CRITICAL/HIGH/MEDIUM/LOW on every save. Approved topics anchor CMA
// Article 92(a) and the climate-risk topic register.

export function MaterialityTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [topics, setTopics]   = useState<MaterialTopic[]>([]);
  const [matrix, setMatrix]   = useState<MaterialMatrix | null>(null);
  const [loading, setLoading] = useState(true);
  const [categoryFilter, setCategoryFilter] = useState<MaterialCategory | 'ALL'>('ALL');
  const [scoringTopic, setScoringTopic]     = useState<MaterialTopic | null>(null);
  const [openNew, setOpenNew]               = useState(false);
  const [editTopic, setEditTopic]           = useState<MaterialTopic | null>(null);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — list and generated matrix load independently.
    const [pg, m] = await Promise.all([
      listMateriality({ year: 2025, size: 100 }).catch(() => ({ content: [] as MaterialTopic[], totalElements: 0 })),
      generateMatrix(2025).catch(() => null as MaterialMatrix | null),
    ]);
    setTopics(pg.content);
    setMatrix(m);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  const filtered = categoryFilter === 'ALL'
    ? topics
    : topics.filter(t => t.category === categoryFilter);

  async function submitScore(values: Record<string, unknown>) {
    if (!scoringTopic) return;
    const impact = Number(values.impactMateriality);
    const financial = Number(values.financialMateriality);
    if (!Number.isFinite(impact) || !Number.isFinite(financial) || impact < 0 || impact > 5 || financial < 0 || financial > 5) {
      toast.danger(isAr ? 'القيم يجب أن تكون 0..5' : 'Values must be 0..5'); return;
    }
    try {
      await scoreMaterialTopic(scoringTopic.id!, impact, financial);
      toast.success(isAr ? 'تم التقييم' : 'Scored');
      setScoringTopic(null);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  /**
   * F2-236 — the "Approver name" field is gone. It fed sust.material_topic.approved_by, which the
   * server stamps beside its own approvedAt clock, so the approver is the authenticated principal
   * and not whatever was typed. Its placeholder read "e.g. Khalid Al-Otaibi" — an invented officer
   * in a placeholder is how a free-text attribution field teaches an operator to type somebody
   * else's name (the 8th recorded instance of that shape in this codebase).
   */
  async function onApproveTopic(id: number) {
    if (!window.confirm(isAr
      ? 'اعتماد هذا الموضوع؟ سيُسجَّل الاعتماد باسمك.'
      : 'Approve this topic? The approval will be recorded in your name.')) return;
    try {
      await approveMaterialTopic(id);
      toast.success(isAr ? 'تم الاعتماد' : 'Approved');
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onArchive(t: MaterialTopic) {
    if (!window.confirm(isAr ? 'أرشفة هذا الموضوع؟' : 'Archive this topic?')) return;
    try {
      await archiveMaterialTopic(t.id!);
      toast.info(isAr ? 'تم الأرشفة' : 'Archived');
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const scoreFields: FieldDef[] = [
    { kind: 'number', key: 'impactMateriality', required: true, min: 0, max: 5, span: 6,
      label: isAr ? 'الأهمية النسبية للأثر (من الداخل إلى الخارج) ٠–٥' : 'Impact materiality (inside-out) 0–5' },
    { kind: 'number', key: 'financialMateriality', required: true, min: 0, max: 5, span: 6,
      label: isAr ? 'الأهمية النسبية المالية (من الخارج إلى الداخل) ٠–٥' : 'Financial materiality (outside-in) 0–5' },
  ];

  const createFields: FieldDef[] = [
    { kind: 'text', key: 'topicEn', required: true, span: 6,
      label: isAr ? 'الموضوع (إنجليزي)' : 'Topic (English)' },
    { kind: 'text', key: 'topicAr', span: 6,
      label: isAr ? 'الموضوع (عربي)' : 'Topic (Arabic)' },
    { kind: 'select', key: 'category', required: true, span: 6,
      label: isAr ? 'الفئة' : 'Category',
      options: (['ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => ({
        value: c, label: materialCategoryLabel(c, isAr ? 'ar' : 'en'),
      })) },
    { kind: 'number', key: 'reportingYear', required: true, span: 6, min: 2000, max: 2100,
      label: isAr ? 'سنة التقرير' : 'Reporting year' },
    { kind: 'number', key: 'impactMateriality', min: 0, max: 5, span: 6,
      label: isAr ? 'الأهمية النسبية للأثر (٠–٥)' : 'Impact materiality (0–5)' },
    { kind: 'number', key: 'financialMateriality', min: 0, max: 5, span: 6,
      label: isAr ? 'الأهمية النسبية المالية (٠–٥)' : 'Financial materiality (0–5)' },
    { kind: 'text', key: 'stakeholderGroups', span: 6,
      label: isAr ? 'مجموعات أصحاب المصلحة' : 'Stakeholder groups' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المسؤول' : 'Owner' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
  ];

  // Edit-topic — descriptive + ownership fields only. Scoring has its own
  // dedicated "Score" action, and the year is fixed once created.
  const editFields: FieldDef[] = [
    { kind: 'text', key: 'topicEn', required: true, span: 6,
      label: isAr ? 'الموضوع (إنجليزي)' : 'Topic (English)' },
    { kind: 'text', key: 'topicAr', span: 6,
      label: isAr ? 'الموضوع (عربي)' : 'Topic (Arabic)' },
    { kind: 'select', key: 'category', required: true, span: 6,
      label: isAr ? 'الفئة' : 'Category',
      options: (['ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => ({
        value: c, label: materialCategoryLabel(c, isAr ? 'ar' : 'en'),
      })) },
    { kind: 'text', key: 'stakeholderGroups', span: 6,
      label: isAr ? 'مجموعات أصحاب المصلحة' : 'Stakeholder groups' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المسؤول' : 'Owner' },
    { kind: 'text', key: 'ownerEmail', span: 6,
      label: isAr ? 'بريد المسؤول' : 'Owner email' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
    { kind: 'textarea', key: 'descriptionAr', span: 12, rows: 2,
      label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2,
      label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  const editInitial = useMemo<Record<string, unknown>>(() => ({
    topicEn:           editTopic?.topicEn ?? '',
    topicAr:           editTopic?.topicAr ?? '',
    category:          editTopic?.category ?? 'ENVIRONMENTAL',
    stakeholderGroups: editTopic?.stakeholderGroups ?? '',
    ownerName:         editTopic?.ownerName ?? '',
    ownerEmail:        editTopic?.ownerEmail ?? '',
    descriptionEn:     editTopic?.descriptionEn ?? '',
    descriptionAr:     editTopic?.descriptionAr ?? '',
    notes:             editTopic?.notes ?? '',
  }), [editTopic]);

  async function submitEdit(values: Record<string, unknown>): Promise<void> {
    if (!editTopic?.id) return;
    const topicEn = String(values.topicEn ?? '').trim();
    const category = String(values.category ?? '').trim();
    if (!topicEn || !category) {
      toast.danger(isAr ? 'الموضوع والفئة حقلان مطلوبان' : 'Topic and category are required');
      return;
    }
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      await updateMaterialTopic(editTopic.id, {
        topicEn,
        topicAr: str(values.topicAr),
        category: category as MaterialCategory,
        descriptionEn: str(values.descriptionEn),
        descriptionAr: str(values.descriptionAr),
        stakeholderGroups: str(values.stakeholderGroups),
        ownerName: str(values.ownerName),
        ownerEmail: str(values.ownerEmail),
        notes: str(values.notes),
        // Preserve the double-materiality axes: the entity defaults both to 0
        // and the service recomputes materiality_score = max(impact, financial)
        // on every save, so an omitted value would zero out the score + tier.
        // Scoring is owned by the dedicated "Score" action.
        impactMateriality: editTopic.impactMateriality ?? editTopic.stakeholderImportance,
        financialMateriality: editTopic.financialMateriality ?? editTopic.businessImpact,
      });
      toast.success(isAr ? 'حُدّث الموضوع' : 'Topic updated');
      setEditTopic(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحديث الموضوع' : 'Failed to update topic'));
    }
  }

  async function submitCreate(values: Record<string, unknown>): Promise<void> {
    const topicEn = String(values.topicEn ?? '').trim();
    const category = String(values.category ?? '').trim();
    const year = Number(values.reportingYear);
    if (!topicEn || !category || !Number.isFinite(year)) {
      toast.danger(isAr
        ? 'الموضوع والفئة وسنة التقرير حقول مطلوبة'
        : 'Topic, category and reporting year are required');
      return;
    }
    const num = (v: unknown) => {
      const n = Number(v);
      return v === '' || v == null || !Number.isFinite(n) ? undefined : n;
    };
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      const created = await createMaterialTopic({
        topicEn,
        topicAr: str(values.topicAr),
        category: category as MaterialCategory,
        reportingYear: year,
        impactMateriality: num(values.impactMateriality) ?? 0,
        financialMateriality: num(values.financialMateriality) ?? 0,
        stakeholderGroups: str(values.stakeholderGroups),
        ownerName: str(values.ownerName),
        descriptionEn: str(values.descriptionEn),
        status: 'DRAFT',
      });
      toast.success(isAr
        ? `تمت إضافة الموضوع ${created.topicCode ?? created.topicEn}`
        : `Topic ${created.topicCode ?? created.topicEn} added`);
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّرت إضافة الموضوع' : 'Failed to add topic'));
    }
  }

  return (
    <div className="esg-stack">
      {/* KPI strip — tier counts + category counts */}
      <div className="kpi-grid">
        <Kpi Icon={Target}       label={isAr ? 'إجمالي الموضوعات' : 'Total topics'}     value={matrix?.total ?? 0} tone="primary" onClick={() => setCategoryFilter('ALL')} active={categoryFilter === 'ALL'} />
        <Kpi Icon={AlertCircle}  label={isAr ? 'حرج' : 'Critical'}                       value={matrix?.byTier.CRITICAL ?? 0} tone="danger" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'مرتفع' : 'High'}                         value={matrix?.byTier.HIGH ?? 0}     tone="warning" to="/esg" />
        <Kpi Icon={Leaf}         label={isAr ? 'البيئة' : 'Environmental'}                value={matrix?.byCategory.ENVIRONMENTAL ?? 0} tone="success" onClick={() => setCategoryFilter('ENVIRONMENTAL')} active={categoryFilter === 'ENVIRONMENTAL'} />
        <Kpi Icon={Building2}    label={isAr ? 'الاجتماع' : 'Social'}                     value={matrix?.byCategory.SOCIAL ?? 0}        tone="info" onClick={() => setCategoryFilter('SOCIAL')} active={categoryFilter === 'SOCIAL'} />
        <Kpi Icon={Building2}    label={isAr ? 'الحوكمة' : 'Governance'}                  value={matrix?.byCategory.GOVERNANCE ?? 0}    tone="primary" onClick={() => setCategoryFilter('GOVERNANCE')} active={categoryFilter === 'GOVERNANCE'} />
      </div>

      <Card title={isAr ? 'مصفوفة تقييم الأهمية النسبية المزدوجة' : 'Double-materiality assessment matrix'} Icon={Target}
        action={<Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>
          {isAr ? 'موضوع جديد' : 'New topic'}
        </Button>}>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'الأهمية النسبية المزدوجة (وفق CSRD): يُقيَّم كل موضوع على محورين مستقلين من ٠ إلى ٥ — الأهمية النسبية للأثر (من الداخل إلى الخارج) والأهمية النسبية المالية (من الخارج إلى الداخل). درجة الأهمية النسبية = الأعلى بين المحورين (يُعدّ الموضوع ذا أهمية نسبية إذا كان كذلك من أيٍّ من المنظورين).'
            : 'Double materiality (CSRD): each topic is scored on two INDEPENDENT axes (0-5) — impact materiality (inside-out) and financial materiality (outside-in). Materiality score = max of the two — material if material from either perspective.'}
        </div>

        {/* Category filter chips */}
        <div className="esg-filter-row">
          {(['ALL', 'ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => (
            <button key={c} onClick={() => setCategoryFilter(c)}
              className={`esg-filter-btn${categoryFilter === c ? ' is-active' : ''}`}>
              {c === 'ALL' ? (isAr ? 'الكل' : 'All') : materialCategoryLabel(c as MaterialCategory, isAr ? 'ar' : 'en')}
            </button>
          ))}
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : filtered.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد موضوعات مطابقة' : 'No matching topics'}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'الموضوع' : 'Topic'}</th>
                    <th>{isAr ? 'الفئة' : 'Category'}</th>
                    <th>{isAr ? 'الأثر' : 'Impact'}</th>
                    <th>{isAr ? 'المالية' : 'Financial'}</th>
                    <th>{isAr ? 'الدرجة' : 'Score'}</th>
                    <th>{isAr ? 'المستوى' : 'Tier'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th className="esg-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(t => (
                    <tr key={t.id}>
                      <td className="mono text-2xs">{t.topicCode ?? `#${t.id}`}</td>
                      <td>
                        <div className="esg-cell-title">
                          {isAr && t.topicAr ? t.topicAr : t.topicEn}
                        </div>
                        {t.ownerName && <div className="text-2xs text-muted esg-cell-sub">{t.ownerName}</div>}
                      </td>
                      <td><Badge tone="neutral">{materialCategoryLabel(t.category, isAr ? 'ar' : 'en')}</Badge></td>
                      <td className="mono text-xs esg-text-center">{Number(t.impactMateriality ?? t.stakeholderImportance).toFixed(1)}</td>
                      <td className="mono text-xs esg-text-center">{Number(t.financialMateriality ?? t.businessImpact).toFixed(1)}</td>
                      <td className="mono text-xs" style={{ textAlign: 'center', fontWeight: 700 }}>
                        {t.materialityScore != null ? Number(t.materialityScore).toFixed(1) : '—'}
                        {Number(t.impactMateriality ?? t.stakeholderImportance) >= 3 && Number(t.financialMateriality ?? t.businessImpact) >= 3 && (
                          <span title={isAr ? 'ذو أهمية نسبية مزدوجة' : 'Double material'}> <Badge tone="danger">2×</Badge></span>
                        )}
                      </td>
                      <td>
                        {t.priorityTier && <Badge tone={materialTierTone(t.priorityTier)}>{materialTierLabel(t.priorityTier, isAr ? 'ar' : 'en')}</Badge>}
                      </td>
                      <td><Badge tone={materialStatusTone(t.status)}>{materialStatusLabel(t.status, isAr ? 'ar' : 'en')}</Badge></td>
                      <td className="esg-text-end">
                        <div className="esg-actions-row">
                          {t.status !== 'ARCHIVED' && (
                            <Button size="sm" variant="ghost" Icon={Pencil}
                              title={isAr ? 'تعديل' : 'Edit'}
                              onClick={() => setEditTopic(t)} />
                          )}
                          {t.status !== 'ARCHIVED' && (
                            <Button size="sm" variant="secondary" onClick={() => setScoringTopic(t)}>
                              {isAr ? 'تقييم' : 'Score'}
                            </Button>
                          )}
                          {t.status === 'SCORED' && (
                            <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onApproveTopic(t.id!)}>
                              {isAr ? 'اعتماد' : 'Approve'}
                            </Button>
                          )}
                          {t.status !== 'ARCHIVED' && (
                            <Button size="sm" variant="ghost" Icon={Trash2}
                              title={isAr ? 'أرشفة' : 'Archive'}
                              onClick={() => onArchive(t)} />
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
      </Card>

      {/* Materiality matrix — visual grid where Y = stakeholder, X = business */}
      {matrix && matrix.matrix.length > 0 && (
        <Card title={isAr ? 'الرسم المرئي للمصفوفة' : 'Matrix visualization'} Icon={Target}>
          <div style={{ position: 'relative', width: '100%', maxWidth: 520, margin: '0 auto', aspectRatio: '1 / 1', border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-2)' }}>
            {/* Axis labels */}
            <div style={{ position: 'absolute', top: '50%', insetInlineStart: -8, transform: 'translate(-100%, -50%) rotate(-90deg)', fontSize: 10, color: 'var(--ink-3)', whiteSpace: 'nowrap' }}>
              {isAr ? '← الأهمية النسبية للأثر' : 'Impact materiality →'}
            </div>
            <div style={{ position: 'absolute', bottom: -20, left: 0, right: 0, textAlign: 'center', fontSize: 10, color: 'var(--ink-3)' }}>
              {isAr ? '← الأهمية النسبية المالية' : 'Financial materiality →'}
            </div>
            {/* Quadrant guides at 2.5 / 4.0 */}
            <div style={{ position: 'absolute', insetInlineStart: '50%', top: 0, bottom: 0, width: 1, background: 'var(--border)' }} />
            <div style={{ position: 'absolute', insetBlockStart: '50%', left: 0, right: 0, height: 1, background: 'var(--border)' }} />
            {/* Plot points */}
            {matrix.matrix.map(e => {
              // X = financial materiality; Y = impact materiality (invert Y so high = top).
              const x = (Number(e.financialMateriality ?? e.businessImpact) / 5) * 100;
              const y = (1 - Number(e.impactMateriality ?? e.stakeholderImportance) / 5) * 100;
              const bg = e.priorityTier === 'CRITICAL' ? 'var(--danger)'
                       : e.priorityTier === 'HIGH'    ? 'var(--warning)'
                       : e.priorityTier === 'MEDIUM'   ? 'var(--info)' : 'var(--ink-3)';
              return (
                <div key={e.id}
                     title={`${e.topicCode ?? e.id}: ${isAr && e.topicAr ? e.topicAr : e.topicEn} (${Number(e.materialityScore).toFixed(1)})`}
                     style={{
                       position: 'absolute',
                       insetInlineStart: `${x}%`, top: `${y}%`,
                       transform: 'translate(-50%, -50%)',
                       width: 14, height: 14, borderRadius: '50%',
                       background: bg, border: '2px solid var(--surface)',
                       boxShadow: 'var(--shadow-1)',
                     }} />
              );
            })}
          </div>
          <div style={{ display: 'flex', gap: 16, marginTop: 24, fontSize: 11, color: 'var(--ink-3)', justifyContent: 'center', flexWrap: 'wrap' }}>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: 'var(--danger)', verticalAlign: 'middle', marginInlineEnd: 4 }} />{materialTierLabel('CRITICAL', isAr ? 'ar' : 'en')}</span>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: 'var(--warning)', verticalAlign: 'middle', marginInlineEnd: 4 }} />{materialTierLabel('HIGH', isAr ? 'ar' : 'en')}</span>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: 'var(--info)', verticalAlign: 'middle', marginInlineEnd: 4 }} />{materialTierLabel('MEDIUM', isAr ? 'ar' : 'en')}</span>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: 'var(--ink-3)', verticalAlign: 'middle', marginInlineEnd: 4 }} />{materialTierLabel('LOW', isAr ? 'ar' : 'en')}</span>
          </div>
        </Card>
      )}

      <FormModal
        open={scoringTopic !== null}
        title={isAr ? `تقييم الموضوع · ${scoringTopic?.topicCode ?? ''}` : `Score topic · ${scoringTopic?.topicCode ?? ''}`}
        fields={scoreFields}
        initial={{
          impactMateriality: scoringTopic?.impactMateriality ?? scoringTopic?.stakeholderImportance ?? 3,
          financialMateriality: scoringTopic?.financialMateriality ?? scoringTopic?.businessImpact ?? 3,
        }}
        size="md"
        submitLabel={isAr ? 'حفظ التقييم' : 'Save score'}
        onClose={() => setScoringTopic(null)}
        onSubmit={submitScore}
      />
      <FormModal
        open={openNew}
        title={isAr ? 'موضوع جوهرية جديد' : 'New materiality topic'}
        fields={createFields}
        initial={{
          topicEn: '', topicAr: '', category: 'ENVIRONMENTAL', reportingYear: 2025,
          impactMateriality: 3, financialMateriality: 3,
          stakeholderGroups: '', ownerName: '', descriptionEn: '',
        }}
        size="lg"
        submitLabel={isAr ? 'إضافة الموضوع' : 'Add topic'}
        onClose={() => setOpenNew(false)}
        onSubmit={submitCreate}
      />
      <FormModal
        open={editTopic !== null}
        title={editTopic
          ? (isAr ? `تعديل الموضوع · ${editTopic.topicCode ?? ''}` : `Edit topic · ${editTopic.topicCode ?? ''}`)
          : ''}
        fields={editFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditTopic(null)}
        onSubmit={submitEdit}
      />
    </div>
  );
}
