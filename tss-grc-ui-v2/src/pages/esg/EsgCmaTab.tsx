/**
 * F2-16 tranche 2 — the CMA Articles 91–94 disclosure tab, moved out of `EsgPage.tsx`.
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
  FileText,
  CheckCircle2, AlertCircle,
  Plus, ArrowRight,
  Paperclip, Pencil,
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
  listCma, cmaReadiness, markDisclosed, markPartial, markNotDisclosed,
  markNotApplicable, markUnderReview, attachCmaEvidence, recordCmaGap, createCma, updateCma, approveCma,
  cmaStatusTone, cmaStatusLabel, articleLabel, hasGap, hasEvidence,
  type CmaDisclosure as CmaD, type CmaReadiness as CmaR, type CmaStatus,
} from '../../lib/sustainabilityCma';
import { getUser } from '../../lib/auth';

// ─── CMA Articles 91–94 disclosure tab (V60 — slice 6) ─────────────────────
//
// Promoted from the static CMA_ARTICLES constant to a live API-driven
// matrix. Per-row workflow:
//   - Mark Disclosed / Partial / Not Disclosed / Not Applicable
//     (Rule 5 requires a gap for PARTIAL/NOT_DISCLOSED — captured via
//      dialog; Rule 6 auto-flips DISCLOSED/PARTIAL to EVIDENCE_REQUIRED
//      server-side when no evidence is attached)
//   - Attach Evidence (text reference + owner)
//   - Record Gap (English/Arabic narrative for the audit trail)

export function CmaTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems]       = useState<CmaD[]>([]);
  const [readiness, setReadi]   = useState<CmaR | null>(null);
  const [loading, setLoading]   = useState(true);
  const [evidencingId, setEvidencingId] = useState<number | null>(null);
  const [gappingId, setGappingId]       = useState<{ id: number; toStatus?: 'PARTIAL' | 'NOT_DISCLOSED' } | null>(null);
  const [openNew, setOpenNew]           = useState(false);
  const [editCma, setEditCma]           = useState<CmaD | null>(null);
  const [approvingCma, setApprovingCma] = useState<CmaD | null>(null);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — disclosures and readiness load independently.
    const [pg, r] = await Promise.all([
      listCma({ year: 2025, size: 100 }).catch(() => ({ content: [] as CmaD[], totalElements: 0 })),
      cmaReadiness(2025).catch(() => null as CmaR | null),
    ]);
    setItems(pg.content);
    setReadi(r);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  // Group by article (91 → 92 → 93 → 94).
  const byArticle = useMemo(() => {
    const map = new Map<string, CmaD[]>();
    for (const d of items) {
      if (!map.has(d.article)) map.set(d.article, []);
      map.get(d.article)!.push(d);
    }
    return map;
  }, [items]);

  async function runTransition(action: () => Promise<unknown>) {
    try {
      await action();
      toast.success(isAr ? 'تم التحديث' : 'Updated');
      await reload();
    } catch (e) {
      // Rule 5/6 violations surface as 400 with a clear message — pipe through.
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function onMarkDisclosed(d: CmaD) {
    await runTransition(() => markDisclosed(d.id!));
  }
  async function onMarkPartial(d: CmaD) {
    // Rule 5 — refuse PARTIAL without a gap. If the row already carries
    // one (e.g. from edit), let the server handle it. Otherwise prompt.
    if (!hasGap(d)) {
      setGappingId({ id: d.id!, toStatus: 'PARTIAL' });
      return;
    }
    await runTransition(() => markPartial(d.id!));
  }
  async function onMarkNotDisclosed(d: CmaD) {
    if (!hasGap(d)) {
      setGappingId({ id: d.id!, toStatus: 'NOT_DISCLOSED' });
      return;
    }
    await runTransition(() => markNotDisclosed(d.id!));
  }
  async function onMarkNotApplicable(d: CmaD) {
    await runTransition(() => markNotApplicable(d.id!));
  }
  async function onMarkUnderReview(d: CmaD) {
    await runTransition(() => markUnderReview(d.id!));
  }

  async function submitAttachEvidence(values: Record<string, unknown>) {
    if (evidencingId == null) return;
    const ref = String(values.evidenceRef ?? '').trim();
    const owner = String(values.owner ?? '').trim() || undefined;
    if (!ref) { toast.danger(isAr ? 'مرجع الدليل مطلوب' : 'Evidence reference is required'); return; }
    try {
      await attachCmaEvidence(evidencingId, ref, owner);
      toast.success(isAr ? 'تم إرفاق الدليل' : 'Evidence attached');
      setEvidencingId(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function submitGap(values: Record<string, unknown>) {
    if (gappingId == null) return;
    const gapEn = String(values.gapEn ?? '').trim() || undefined;
    const gapAr = String(values.gapAr ?? '').trim() || undefined;
    if (!gapEn && !gapAr) {
      toast.danger(isAr ? 'وصف الفجوة مطلوب (إنجليزي أو عربي)' : 'Gap description is required (en or ar)');
      return;
    }
    try {
      await recordCmaGap(gappingId.id, gapEn, gapAr);
      // If the user opened this dialog from a Mark Partial / Not Disclosed
      // attempt, chase the transition after the gap is recorded.
      if (gappingId.toStatus === 'PARTIAL')     await markPartial(gappingId.id);
      if (gappingId.toStatus === 'NOT_DISCLOSED') await markNotDisclosed(gappingId.id);
      toast.success(isAr ? 'تم تسجيل الفجوة' : 'Gap recorded');
      setGappingId(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  // Sign-off (CMA Art. 91-94 board attestation). Only DISCLOSED / PARTIAL /
  // NOT_APPLICABLE rows can be approved (the service rejects others); the
  // approver name is captured for the audit trail and defaults to the user.
  const approveInitial: Record<string, string> = useMemo(
    () => ({ approvedBy: getUser()?.username ?? '' }), [approvingCma]);
  const approveFields: FieldDef[] = [
    { kind: 'text', key: 'approvedBy', required: true, span: 12, maxLength: 128,
      label: isAr ? 'المُعتمِد' : 'Approved by',
      placeholder: isAr ? 'اسم المسؤول عن الاعتماد' : 'Name of the approving officer' },
  ];
  async function submitApprove(values: Record<string, unknown>) {
    if (approvingCma?.id == null) return;
    const approvedBy = String(values.approvedBy ?? '').trim();
    if (!approvedBy) { toast.danger(isAr ? 'اسم المُعتمِد مطلوب' : 'Approver name is required'); return; }
    try {
      await approveCma(approvingCma.id, approvedBy);
      toast.success(isAr ? 'تم اعتماد الإفصاح' : 'Disclosure approved');
      setApprovingCma(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  const evidenceFields: FieldDef[] = [
    { kind: 'text', key: 'evidenceRef', required: true, span: 12,
      label: isAr ? 'مرجع الدليل (مسار، قسم، URL، إلخ)' : 'Evidence reference (path, section, URL, etc.)',
      placeholder: isAr ? 'مثال: Annual Report 2025 §3.4' : 'e.g. Annual Report 2025 §3.4' },
    { kind: 'text', key: 'owner', span: 12,
      label: isAr ? 'مالك الدليل (اختياري)' : 'Evidence owner (optional)',
      placeholder: isAr ? 'مثال: سارة الزهراني' : 'e.g. Sara Al-Zahrani' },
  ];

  // FieldDef.textarea doesn't accept `placeholder`. The labels carry
  // enough hint in this case; submitGap re-validates that at least one
  // of the two fields was filled before dispatch.
  const gapFields: FieldDef[] = [
    { kind: 'textarea', key: 'gapEn', span: 12, rows: 3,
      label: isAr ? 'وصف الفجوة (إنجليزي)' : 'Gap description (English)' },
    { kind: 'textarea', key: 'gapAr', span: 12, rows: 3,
      label: isAr ? 'وصف الفجوة (عربي)' : 'Gap description (Arabic)' },
  ];

  // Create starts in a gap-free state (UNDER_REVIEW / DISCLOSED / NOT_APPLICABLE)
  // so it never trips Rule 5 (PARTIAL / NOT_DISCLOSED need a gap first).
  const CREATE_STATUSES: CmaStatus[] = ['UNDER_REVIEW', 'DISCLOSED', 'NOT_APPLICABLE'];
  const createFields: FieldDef[] = [
    { kind: 'select', key: 'article', required: true, span: 4,
      label: isAr ? 'المادة' : 'Article',
      options: (['91', '92', '93', '94'] as const).map(a => ({
        value: a, label: isAr ? `المادة ${a}` : `Article ${a}`,
      })) },
    { kind: 'number', key: 'reportingYear', required: true, span: 4, min: 2000, max: 2100,
      label: isAr ? 'سنة التقرير' : 'Reporting year' },
    { kind: 'select', key: 'disclosureStatus', span: 4,
      label: isAr ? 'حالة الإفصاح' : 'Disclosure status',
      options: CREATE_STATUSES.map(s => ({ value: s, label: cmaStatusLabel(s, isAr ? 'ar' : 'en') })) },
    { kind: 'text', key: 'clause', span: 4,
      label: isAr ? 'الفقرة' : 'Clause', placeholder: 'e.g. 91(a)' },
    { kind: 'text', key: 'titleEn', required: true, span: 8,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 6,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'text', key: 'responsibleDepartment', span: 6,
      label: isAr ? 'الإدارة المسؤولة' : 'Responsible department' },
    { kind: 'textarea', key: 'requirementEn', span: 12, rows: 2,
      label: isAr ? 'نص المتطلب (إنجليزي)' : 'Requirement (English)' },
  ];

  // Edit-disclosure — descriptive fields only. Disclosure status is owned by
  // the mark-* actions, evidence by Attach, and gaps by Gap, so none of those
  // are in this form. disclosureStatus is echoed back deliberately: the entity
  // defaults the column to "UNDER_REVIEW", so an omitted value would
  // deserialize to that default and update() would overwrite the reviewer's
  // real classification. The service re-applies Rules 5/6 against it on save.
  const editFields: FieldDef[] = [
    { kind: 'text', key: 'titleEn', required: true, span: 6,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 6,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'text', key: 'responsibleDepartment', span: 6,
      label: isAr ? 'الإدارة المسؤولة' : 'Responsible department' },
    { kind: 'text', key: 'reportSectionReference', span: 6,
      label: isAr ? 'مرجع قسم التقرير' : 'Report section reference' },
    { kind: 'text', key: 'reviewerName', span: 6,
      label: isAr ? 'اسم المراجع' : 'Reviewer name' },
    { kind: 'text', key: 'reviewerEmail', span: 6,
      label: isAr ? 'بريد المراجع' : 'Reviewer email' },
    { kind: 'textarea', key: 'requirementEn', span: 12, rows: 2,
      label: isAr ? 'نص المتطلب (إنجليزي)' : 'Requirement (English)' },
    { kind: 'textarea', key: 'requirementAr', span: 12, rows: 2,
      label: isAr ? 'نص المتطلب (عربي)' : 'Requirement (Arabic)' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2,
      label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  const editInitial = useMemo<Record<string, unknown>>(() => ({
    titleEn:                editCma?.titleEn ?? '',
    titleAr:                editCma?.titleAr ?? '',
    responsibleDepartment:  editCma?.responsibleDepartment ?? '',
    reportSectionReference: editCma?.reportSectionReference ?? '',
    reviewerName:           editCma?.reviewerName ?? '',
    reviewerEmail:          editCma?.reviewerEmail ?? '',
    requirementEn:          editCma?.requirementEn ?? '',
    requirementAr:          editCma?.requirementAr ?? '',
    notes:                  editCma?.notes ?? '',
  }), [editCma]);

  async function submitEdit(values: Record<string, unknown>): Promise<void> {
    if (!editCma?.id) return;
    const titleEn = String(values.titleEn ?? '').trim();
    if (!titleEn) {
      toast.danger(isAr ? 'العنوان (إنجليزي) مطلوب' : 'English title is required');
      return;
    }
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      await updateCma(editCma.id, {
        disclosureStatus: editCma.disclosureStatus,
        titleEn,
        titleAr: str(values.titleAr),
        requirementEn: str(values.requirementEn),
        requirementAr: str(values.requirementAr),
        responsibleDepartment: str(values.responsibleDepartment),
        reportSectionReference: str(values.reportSectionReference),
        reviewerName: str(values.reviewerName),
        reviewerEmail: str(values.reviewerEmail),
        notes: str(values.notes),
      });
      toast.success(isAr ? 'حُدّث الإفصاح' : 'Disclosure updated');
      setEditCma(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحديث الإفصاح' : 'Failed to update disclosure'));
    }
  }

  async function submitCreate(values: Record<string, unknown>): Promise<void> {
    const titleEn = String(values.titleEn ?? '').trim();
    const article = String(values.article ?? '').trim();
    const year = Number(values.reportingYear);
    if (!titleEn || !article || !Number.isFinite(year)) {
      toast.danger(isAr
        ? 'العنوان (إنجليزي) والمادة وسنة التقرير حقول مطلوبة'
        : 'English title, article and reporting year are required');
      return;
    }
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      const created = await createCma({
        article: article as CmaD['article'],
        reportingYear: year,
        disclosureStatus: (String(values.disclosureStatus ?? 'UNDER_REVIEW') || 'UNDER_REVIEW') as CmaStatus,
        clause: str(values.clause),
        titleEn,
        titleAr: str(values.titleAr),
        requirementEn: str(values.requirementEn),
        responsibleDepartment: str(values.responsibleDepartment),
      });
      toast.success(isAr
        ? `تمت إضافة إفصاح المادة ${created.article}`
        : `Article ${created.article} disclosure added`);
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّرت إضافة الإفصاح' : 'Failed to add disclosure'));
    }
  }

  return (
    <div className="esg-stack">
      {/* Readiness KPI strip */}
      <div className="kpi-grid">
        <Kpi Icon={FileText}     label={isAr ? 'جاهزية الإفصاح' : 'Disclosure readiness'} value={`${readiness?.readinessPct ?? 0}%`}
             tone={(readiness?.readinessPct ?? 0) >= 80 ? 'success' : (readiness?.readinessPct ?? 0) >= 50 ? 'warning' : 'danger'} to="/esg" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'مُفصَح' : 'Disclosed'}      value={readiness?.byStatus.DISCLOSED ?? 0}         tone="success" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'جزئي' : 'Partial'}          value={readiness?.byStatus.PARTIAL ?? 0}           tone="warning" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'غير مُفصَح' : 'Not disclosed'} value={readiness?.byStatus.NOT_DISCLOSED ?? 0}  tone="danger" to="/esg" />
        <Kpi Icon={Paperclip}    label={isAr ? 'يتطلّب دليلاً' : 'Evidence required'} value={readiness?.byStatus.EVIDENCE_REQUIRED ?? 0} tone="danger" to="/esg" />
        <Kpi Icon={ArrowRight}   label={isAr ? 'قيد المراجعة' : 'Under review'}      value={readiness?.byStatus.UNDER_REVIEW ?? 0}      tone="info" to="/esg" />
      </div>

      <Card title={isAr ? 'سجلّ إفصاحات المواد 91–94' : 'CMA Articles 91–94 disclosure matrix'} Icon={FileText}
        action={<Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>
          {isAr ? 'إفصاح جديد' : 'New disclosure'}
        </Button>}>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'مصفوفة الإفصاحات الإلزامية لهيئة السوق المالية. الجزئي وغير المُفصَح يتطلّبان وصف فجوة (القاعدة 5). الإفصاحات المعتمدة بلا دليل تُعلَّم تلقائياً «يتطلّب دليلاً» (القاعدة 6).'
            : 'CMA mandatory disclosure matrix. Partial and Not-Disclosed states require a gap description (Rule 5). Disclosed items without evidence are auto-flagged Evidence Required (Rule 6).'}
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : items.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد إفصاحات لهذه السنة' : 'No disclosures for this year'}</InlineEmpty>
            : Array.from(byArticle.entries()).map(([article, articleItems]) => (
                <div key={article} className="esg-cma-article">
                  <div className="esg-cma-pillar__title">
                    {isAr ? `المادة ${article}` : `Article ${article}`}
                  </div>
                  {articleItems.map(d => (
                    <div key={d.id} className="esg-disclosure-row">
                      <div className="esg-disclosure-row__main">
                        <div className="esg-disclosure-row__head">
                          <span className="mono esg-disclosure-row__code">
                            {articleLabel(d, isAr ? 'ar' : 'en')}
                          </span>
                          <span className="esg-disclosure-row__title">
                            {isAr && d.titleAr ? d.titleAr : d.titleEn}
                          </span>
                        </div>
                        <div className="esg-disclosure-row__desc">
                          {isAr && d.requirementAr ? d.requirementAr : d.requirementEn}
                        </div>
                        {hasEvidence(d) && (
                          <div className="esg-disclosure-row__evidence">
                            <Paperclip size={10} />
                            <span>{isAr ? 'الدليل:' : 'Evidence:'}</span>
                            <span className="mono">{d.evidenceRef}</span>
                            {d.evidenceOwner && <span>· {d.evidenceOwner}</span>}
                          </div>
                        )}
                        {hasGap(d) && (
                          <div className="esg-disclosure-row__gap esg-banner esg-banner--warning">
                            <AlertCircle size={11} className="esg-icon-inline" />
                            <strong>{isAr ? 'فجوة:' : 'Gap:'}</strong>{' '}
                            {isAr && d.gapDescriptionAr ? d.gapDescriptionAr : (d.gapDescriptionEn ?? d.gapDescriptionAr)}
                          </div>
                        )}
                      </div>
                      <div className="esg-disclosure-row__actions">
                        <Badge tone={cmaStatusTone(d.disclosureStatus)}>{cmaStatusLabel(d.disclosureStatus, isAr ? 'ar' : 'en')}</Badge>
                        <div className="esg-disclosure-row__btn-row">
                          {d.disclosureStatus !== 'DISCLOSED' && (
                            <Button size="sm" variant="ghost" onClick={() => onMarkDisclosed(d)}>{isAr ? 'مُفصَح' : 'Disclosed'}</Button>
                          )}
                          {d.disclosureStatus !== 'PARTIAL' && (
                            <Button size="sm" variant="ghost" onClick={() => onMarkPartial(d)}>{isAr ? 'جزئي' : 'Partial'}</Button>
                          )}
                          {d.disclosureStatus !== 'NOT_DISCLOSED' && (
                            <Button size="sm" variant="ghost" onClick={() => onMarkNotDisclosed(d)}>{isAr ? 'غير مُفصَح' : 'Not'}</Button>
                          )}
                          {d.disclosureStatus !== 'UNDER_REVIEW' && (
                            <Button size="sm" variant="ghost" onClick={() => onMarkUnderReview(d)}>{isAr ? 'مراجعة' : 'Review'}</Button>
                          )}
                          {d.disclosureStatus !== 'NOT_APPLICABLE' && (
                            <Button size="sm" variant="ghost" onClick={() => onMarkNotApplicable(d)}>{isAr ? 'لا ينطبق' : 'N/A'}</Button>
                          )}
                        </div>
                        <div className="esg-disclosure-row__btn-row">
                          <Button size="sm" variant="ghost" Icon={Pencil}
                            title={isAr ? 'تعديل' : 'Edit'}
                            onClick={() => setEditCma(d)} />
                          <Button size="sm" variant="secondary" Icon={Paperclip} onClick={() => setEvidencingId(d.id!)}>
                            {isAr ? 'إرفاق دليل' : 'Attach'}
                          </Button>
                          <Button size="sm" variant="secondary" Icon={AlertCircle} onClick={() => setGappingId({ id: d.id! })}>
                            {isAr ? 'تسجيل فجوة' : 'Gap'}
                          </Button>
                          {d.approvalStatus === 'APPROVED'
                            ? <Badge tone="success" title={d.approvedBy ? `${isAr ? 'اعتمده' : 'by'} ${d.approvedBy}` : undefined}>
                                <CheckCircle2 size={11} className="esg-icon-inline" />
                                {isAr ? 'مُعتمَد' : 'Approved'}{d.approvedBy ? ` · ${d.approvedBy}` : ''}
                              </Badge>
                            : (d.disclosureStatus === 'DISCLOSED' || d.disclosureStatus === 'PARTIAL' || d.disclosureStatus === 'NOT_APPLICABLE') && (
                                <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => setApprovingCma(d)}>
                                  {isAr ? 'اعتماد' : 'Approve'}
                                </Button>
                              )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ))}
      </Card>

      <FormModal
        open={evidencingId !== null}
        title={isAr ? 'إرفاق دليل للإفصاح' : 'Attach evidence to disclosure'}
        fields={evidenceFields}
        initial={{ evidenceRef: '', owner: '' }}
        size="md"
        submitLabel={isAr ? 'إرفاق' : 'Attach'}
        onClose={() => setEvidencingId(null)}
        onSubmit={submitAttachEvidence}
      />
      <FormModal
        open={gappingId !== null}
        title={
          gappingId?.toStatus
            ? (isAr
                ? `الفجوة مطلوبة لـ ${gappingId.toStatus === 'PARTIAL' ? 'الإفصاح الجزئي' : 'عدم الإفصاح'} (القاعدة 5)`
                : `Gap required for ${gappingId.toStatus === 'PARTIAL' ? 'Partial' : 'Not Disclosed'} (Rule 5)`)
            : (isAr ? 'تسجيل فجوة' : 'Record gap')
        }
        fields={gapFields}
        initial={{ gapEn: '', gapAr: '' }}
        size="md"
        submitLabel={isAr ? 'حفظ' : 'Save'}
        onClose={() => setGappingId(null)}
        onSubmit={submitGap}
      />
      <FormModal
        open={approvingCma !== null}
        title={isAr
          ? `اعتماد الإفصاح${approvingCma ? ` — ${articleLabel(approvingCma, 'ar')}` : ''}`
          : `Approve disclosure${approvingCma ? ` — ${articleLabel(approvingCma, 'en')}` : ''}`}
        fields={approveFields}
        initial={approveInitial}
        size="sm"
        submitLabel={isAr ? 'اعتماد' : 'Approve'}
        onClose={() => setApprovingCma(null)}
        onSubmit={submitApprove}
      />
      <FormModal
        open={openNew}
        title={isAr ? 'إفصاح هيئة السوق المالية جديد' : 'New CMA disclosure'}
        fields={createFields}
        initial={{
          article: '91', reportingYear: 2025, disclosureStatus: 'UNDER_REVIEW',
          clause: '', titleEn: '', titleAr: '', responsibleDepartment: '', requirementEn: '',
        }}
        size="lg"
        submitLabel={isAr ? 'إضافة الإفصاح' : 'Add disclosure'}
        onClose={() => setOpenNew(false)}
        onSubmit={submitCreate}
      />

      <FormModal
        open={editCma !== null}
        title={editCma
          ? (isAr ? `تعديل الإفصاح · ${articleLabel(editCma, 'ar')}` : `Edit disclosure · ${articleLabel(editCma, 'en')}`)
          : ''}
        fields={editFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditCma(null)}
        onSubmit={submitEdit}
      />
    </div>
  );
}
