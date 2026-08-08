/**
 * F2-16 tranche 2 — the TCFD alignment tab, moved out of `EsgPage.tsx`.
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
  Plus, ArrowRight, Link2,
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
  listClimateItems,
  type ClimateRiskOpportunity,
} from '../../lib/climateRisk';
import {
  listTcfd, tcfdReadiness, createTcfd, updateTcfd,
  tcfdMarkDisclosed, tcfdMarkPartial, tcfdMarkNotDisclosed,
  tcfdMarkNotApplicable, tcfdMarkUnderReview,
  tcfdAttachEvidence, tcfdRecordGap, tcfdLinkClimateRisk,
  tcfdStatusTone, tcfdStatusLabel, tcfdPillarLabel,
  hasTcfdGap, hasTcfdEvidence,
  type TcfdItem as TcfdI, type TcfdReadiness as TcfdR, type TcfdPillar, type TcfdStatus,
} from '../../lib/sustainabilityTcfd';

// ─── TCFD alignment tab (V61 — slice 7) ─────────────────────────────────────
//
// Promoted from the static TCFD_ITEMS constant to a live API-driven
// matrix grouped by pillar. Mirrors CmaTab's lifecycle UX — same
// 5 status buttons, Attach Evidence + Record Gap modals, Rule 5 + 6
// enforcement client-side mirror — plus a Rule 7 banner when the
// Strategy resilience recommendation is in a non-DISCLOSED state.

export function TcfdTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems]     = useState<TcfdI[]>([]);
  const [readiness, setReadi] = useState<TcfdR | null>(null);
  const [loading, setLoading] = useState(true);
  const [evidencingId, setEvidencingId] = useState<number | null>(null);
  const [gappingId, setGappingId]       = useState<{ id: number; toStatus?: 'PARTIAL' | 'NOT_DISCLOSED' } | null>(null);
  const [openNew, setOpenNew]           = useState(false);
  const [editTcfd, setEditTcfd]         = useState<TcfdI | null>(null);
  // Rule-7 traceability — link a TCFD recommendation to a climate risk in the
  // R&O register. The picker is populated from the kind=RISK items.
  const [linkingTcfd, setLinkingTcfd]   = useState<TcfdI | null>(null);
  const [climateRisks, setClimateRisks] = useState<ClimateRiskOpportunity[]>([]);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — matrix, readiness and the climate-risk picker load
    // independently.
    const [pg, r, croPg] = await Promise.all([
      listTcfd({ year: 2025, size: 100 }).catch(() => ({ content: [] as TcfdI[], totalElements: 0 })),
      tcfdReadiness(2025).catch(() => null as TcfdR | null),
      listClimateItems({ kind: 'RISK', size: 100 }).catch(() => ({ content: [] as ClimateRiskOpportunity[], totalElements: 0 })),
    ]);
    setItems(pg.content);
    setReadi(r);
    setClimateRisks(croPg.content);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  const climateRiskLabel = (id?: number) => {
    if (id == null) return null;
    const cro = climateRisks.find(c => c.id === id);
    if (!cro) return `#${id}`;
    return `${cro.itemCode ? cro.itemCode + ' · ' : ''}${isAr && cro.titleAr ? cro.titleAr : cro.titleEn}`;
  };

  const linkFields: FieldDef[] = useMemo(() => [
    { kind: 'select', key: 'climateRiskId', required: true, span: 12,
      label: isAr ? 'مخاطرة مناخية' : 'Climate risk',
      options: climateRisks.map(c => ({
        value: String(c.id),
        label: `${c.itemCode ? c.itemCode + ' · ' : ''}${isAr && c.titleAr ? c.titleAr : c.titleEn}`,
      })) },
  ], [climateRisks, isAr]);

  async function submitLink(values: Record<string, unknown>) {
    if (linkingTcfd?.id == null) return;
    const climateRiskId = Number(values.climateRiskId);
    if (!Number.isInteger(climateRiskId) || climateRiskId <= 0) {
      toast.danger(isAr ? 'اختر مخاطرة مناخية' : 'Select a climate risk'); return;
    }
    try {
      await tcfdLinkClimateRisk(linkingTcfd.id, climateRiskId);
      toast.success(isAr ? 'تم ربط المخاطرة المناخية' : 'Climate risk linked');
      setLinkingTcfd(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  const byPillar = useMemo(() => {
    const map = new Map<TcfdPillar, TcfdI[]>();
    for (const t of items) {
      if (!map.has(t.pillar)) map.set(t.pillar, []);
      map.get(t.pillar)!.push(t);
    }
    return map;
  }, [items]);

  async function runTransition(action: () => Promise<unknown>) {
    try { await action(); toast.success(isAr ? 'تم التحديث' : 'Updated'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onMarkDisclosed(t: TcfdI)    { await runTransition(() => tcfdMarkDisclosed(t.id!)); }
  async function onMarkPartial(t: TcfdI) {
    if (!hasTcfdGap(t)) { setGappingId({ id: t.id!, toStatus: 'PARTIAL' }); return; }
    await runTransition(() => tcfdMarkPartial(t.id!));
  }
  async function onMarkNotDisclosed(t: TcfdI) {
    if (!hasTcfdGap(t)) { setGappingId({ id: t.id!, toStatus: 'NOT_DISCLOSED' }); return; }
    await runTransition(() => tcfdMarkNotDisclosed(t.id!));
  }
  async function onMarkNotApplicable(t: TcfdI) { await runTransition(() => tcfdMarkNotApplicable(t.id!)); }
  async function onMarkUnderReview(t: TcfdI)   { await runTransition(() => tcfdMarkUnderReview(t.id!)); }

  async function submitAttachEvidence(values: Record<string, unknown>) {
    if (evidencingId == null) return;
    const ref = String(values.evidenceRef ?? '').trim();
    const owner = String(values.owner ?? '').trim() || undefined;
    if (!ref) { toast.danger(isAr ? 'مرجع الدليل مطلوب' : 'Evidence reference is required'); return; }
    try {
      await tcfdAttachEvidence(evidencingId, ref, owner);
      toast.success(isAr ? 'تم إرفاق الدليل' : 'Evidence attached');
      setEvidencingId(null);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function submitGap(values: Record<string, unknown>) {
    if (gappingId == null) return;
    const gapEn = String(values.gapEn ?? '').trim() || undefined;
    const gapAr = String(values.gapAr ?? '').trim() || undefined;
    if (!gapEn && !gapAr) {
      toast.danger(isAr ? 'وصف الفجوة مطلوب' : 'Gap description is required'); return;
    }
    try {
      await tcfdRecordGap(gappingId.id, gapEn, gapAr);
      if (gappingId.toStatus === 'PARTIAL')        await tcfdMarkPartial(gappingId.id);
      if (gappingId.toStatus === 'NOT_DISCLOSED')  await tcfdMarkNotDisclosed(gappingId.id);
      toast.success(isAr ? 'تم تسجيل الفجوة' : 'Gap recorded');
      setGappingId(null);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const evidenceFields: FieldDef[] = [
    { kind: 'text', key: 'evidenceRef', required: true, span: 12,
      label: isAr ? 'مرجع الدليل (مسار، قسم، URL، إلخ)' : 'Evidence reference (path, section, URL, etc.)' },
    { kind: 'text', key: 'owner', span: 12,
      label: isAr ? 'مالك الدليل (اختياري)' : 'Evidence owner (optional)' },
  ];
  const gapFields: FieldDef[] = [
    { kind: 'textarea', key: 'gapEn', span: 12, rows: 3,
      label: isAr ? 'وصف الفجوة (إنجليزي)' : 'Gap description (English)' },
    { kind: 'textarea', key: 'gapAr', span: 12, rows: 3,
      label: isAr ? 'وصف الفجوة (عربي)' : 'Gap description (Arabic)' },
  ];

  // Create starts in a gap-free state so it never trips Rule 5.
  const TCFD_CREATE_STATUSES: TcfdStatus[] = ['UNDER_REVIEW', 'DISCLOSED', 'NOT_APPLICABLE'];
  const TCFD_PILLARS: TcfdPillar[] = ['GOVERNANCE', 'STRATEGY', 'RISK_MGMT', 'METRICS_TARGETS'];
  const createFields: FieldDef[] = [
    { kind: 'select', key: 'pillar', required: true, span: 6,
      label: isAr ? 'الركيزة' : 'Pillar',
      options: TCFD_PILLARS.map(p => ({ value: p, label: tcfdPillarLabel(p, isAr ? 'ar' : 'en') })) },
    { kind: 'text', key: 'recommendationCode', required: true, span: 3,
      label: isAr ? 'رمز التوصية' : 'Rec. code', placeholder: 'a, b, c…' },
    { kind: 'number', key: 'reportingYear', required: true, span: 3, min: 2000, max: 2100,
      label: isAr ? 'سنة التقرير' : 'Reporting year' },
    { kind: 'text', key: 'titleEn', required: true, span: 6,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 6,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'select', key: 'disclosureStatus', span: 6,
      label: isAr ? 'حالة الإفصاح' : 'Disclosure status',
      options: TCFD_CREATE_STATUSES.map(s => ({ value: s, label: tcfdStatusLabel(s, isAr ? 'ar' : 'en') })) },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المالك' : 'Owner' },
    { kind: 'textarea', key: 'recommendationEn', span: 12, rows: 2,
      label: isAr ? 'نص التوصية (إنجليزي)' : 'Recommendation (English)' },
  ];

  // Edit-recommendation — descriptive fields only. Disclosure status is owned
  // by the mark-* actions, evidence by Attach, gaps by Gap, so none are in
  // this form. disclosureStatus is echoed back deliberately: the entity
  // defaults the column to "UNDER_REVIEW", so an omitted value would
  // deserialize to that default and update() would overwrite the reviewer's
  // real classification. The service re-applies Rules 5/6 against it on save.
  const editFields: FieldDef[] = [
    { kind: 'text', key: 'titleEn', required: true, span: 6,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 6,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المالك' : 'Owner' },
    { kind: 'text', key: 'ownerEmail', span: 6,
      label: isAr ? 'بريد المالك' : 'Owner email' },
    { kind: 'text', key: 'reviewerName', span: 12,
      label: isAr ? 'اسم المراجع' : 'Reviewer name' },
    { kind: 'textarea', key: 'recommendationEn', span: 12, rows: 2,
      label: isAr ? 'نص التوصية (إنجليزي)' : 'Recommendation (English)' },
    { kind: 'textarea', key: 'recommendationAr', span: 12, rows: 2,
      label: isAr ? 'نص التوصية (عربي)' : 'Recommendation (Arabic)' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2,
      label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  const editInitial = useMemo<Record<string, unknown>>(() => ({
    titleEn:           editTcfd?.titleEn ?? '',
    titleAr:           editTcfd?.titleAr ?? '',
    ownerName:         editTcfd?.ownerName ?? '',
    ownerEmail:        editTcfd?.ownerEmail ?? '',
    reviewerName:      editTcfd?.reviewerName ?? '',
    recommendationEn:  editTcfd?.recommendationEn ?? '',
    recommendationAr:  editTcfd?.recommendationAr ?? '',
    notes:             editTcfd?.notes ?? '',
  }), [editTcfd]);

  async function submitEdit(values: Record<string, unknown>): Promise<void> {
    if (!editTcfd?.id) return;
    const titleEn = String(values.titleEn ?? '').trim();
    if (!titleEn) {
      toast.danger(isAr ? 'العنوان (إنجليزي) مطلوب' : 'English title is required');
      return;
    }
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      await updateTcfd(editTcfd.id, {
        disclosureStatus: editTcfd.disclosureStatus,
        titleEn,
        titleAr: str(values.titleAr),
        recommendationEn: str(values.recommendationEn),
        recommendationAr: str(values.recommendationAr),
        ownerName: str(values.ownerName),
        ownerEmail: str(values.ownerEmail),
        reviewerName: str(values.reviewerName),
        notes: str(values.notes),
      });
      toast.success(isAr ? 'حُدّثت التوصية' : 'Recommendation updated');
      setEditTcfd(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحديث التوصية' : 'Failed to update recommendation'));
    }
  }

  async function submitCreate(values: Record<string, unknown>): Promise<void> {
    const titleEn = String(values.titleEn ?? '').trim();
    const pillar = String(values.pillar ?? '').trim();
    const code = String(values.recommendationCode ?? '').trim();
    const year = Number(values.reportingYear);
    if (!titleEn || !pillar || !code || !Number.isFinite(year)) {
      toast.danger(isAr
        ? 'العنوان (إنجليزي) والركيزة ورمز التوصية وسنة التقرير حقول مطلوبة'
        : 'English title, pillar, recommendation code and reporting year are required');
      return;
    }
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      const created = await createTcfd({
        pillar: pillar as TcfdPillar,
        recommendationCode: code,
        reportingYear: year,
        disclosureStatus: (String(values.disclosureStatus ?? 'UNDER_REVIEW') || 'UNDER_REVIEW') as TcfdStatus,
        titleEn,
        titleAr: str(values.titleAr),
        recommendationEn: str(values.recommendationEn),
        ownerName: str(values.ownerName),
      });
      toast.success(isAr
        ? `تمت إضافة توصية ${created.recommendationCode}`
        : `Recommendation ${created.recommendationCode} added`);
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّرت إضافة التوصية' : 'Failed to add recommendation'));
    }
  }

  return (
    <div className="esg-stack">
      {/* Rule 7 banner — Strategy resilience recommendation is missing/partial */}
      {readiness?.strategyResilienceGap && (
        <div style={{
          padding: 12, borderRadius: 'var(--r-sm)',
          background: 'var(--warning-bg)', color: 'var(--warning-fg)',
          fontSize: 12, lineHeight: 1.5,
          display: 'flex', gap: 8, alignItems: 'center',
        }}>
          <AlertCircle size={16} className="esg-flex-shrink-0" />
          <div>
            <strong>{isAr ? 'تنبيه القاعدة 7:' : 'Rule 7 alert:'}</strong>{' '}
            {isAr
              ? 'توصية «الصمود تحت سيناريوهات مناخية مختلفة» (الاستراتيجية ج) غير مُفصَحة بالكامل — يُنصح برفع إجراء تصحيحي مرتبط بمخاطر مناخية.'
              : 'The Strategy resilience-scenario recommendation (Strategy c) is not fully disclosed — consider raising a climate-risk-linked corrective action.'}
          </div>
        </div>
      )}

      {/* Alignment KPI strip */}
      <div className="kpi-grid">
        <Kpi Icon={FileText}     label={isAr ? 'محاذاة TCFD' : 'TCFD alignment'} value={`${readiness?.alignmentPct ?? 0}%`}
             tone={(readiness?.alignmentPct ?? 0) >= 80 ? 'success' : (readiness?.alignmentPct ?? 0) >= 50 ? 'warning' : 'danger'} to="/esg" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'مُفصَح' : 'Disclosed'}    value={readiness?.byStatus.DISCLOSED ?? 0}      tone="success" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'جزئي' : 'Partial'}        value={readiness?.byStatus.PARTIAL ?? 0}        tone="warning" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'غير مُفصَح' : 'Not disclosed'} value={readiness?.byStatus.NOT_DISCLOSED ?? 0} tone="danger" to="/esg" />
        <Kpi Icon={Paperclip}    label={isAr ? 'يتطلّب دليلاً' : 'Evidence required'} value={readiness?.byStatus.EVIDENCE_REQUIRED ?? 0} tone="danger" to="/esg" />
        <Kpi Icon={ArrowRight}   label={isAr ? 'قيد المراجعة' : 'Under review'}      value={readiness?.byStatus.UNDER_REVIEW ?? 0}      tone="info" to="/esg" />
      </div>

      <Card title={isAr ? 'مصفوفة المحاذاة مع TCFD' : 'TCFD alignment matrix'} Icon={FileText}
        action={<Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>
          {isAr ? 'توصية جديدة' : 'New recommendation'}
        </Button>}>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'إفصاحات TCFD الـ 11 الموزّعة على 4 ركائز (الحوكمة، الاستراتيجية، إدارة المخاطر، المقاييس والأهداف). نفس قواعد CMA (5 و 6) تنطبق هنا، بالإضافة إلى القاعدة 7 لتوصية صمود الاستراتيجية.'
            : 'TCFD’s 11 recommendations across the 4 pillars (Governance, Strategy, Risk Mgmt, Metrics & Targets). CMA Rules 5 + 6 apply here too, plus Rule 7 on the Strategy resilience recommendation.'}
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : items.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد توصيات' : 'No recommendations'}</InlineEmpty>
            : (['GOVERNANCE', 'STRATEGY', 'RISK_MGMT', 'METRICS_TARGETS'] as TcfdPillar[]).map(pillar => {
                const pillarItems = byPillar.get(pillar) ?? [];
                if (pillarItems.length === 0) return null;
                return (
                  <div key={pillar} className="esg-cma-pillar">
                    <div className="esg-cma-pillar__title">
                      {tcfdPillarLabel(pillar, isAr ? 'ar' : 'en')}
                    </div>
                    {pillarItems.map(t => (
                      <div key={t.id} className="esg-disclosure-row">
                        <div className="esg-disclosure-row__main">
                          <div className="esg-disclosure-row__head">
                            <span className="mono esg-disclosure-row__code">
                              ({t.recommendationCode})
                            </span>
                            <span className="esg-disclosure-row__title">
                              {isAr && t.titleAr ? t.titleAr : t.titleEn}
                            </span>
                          </div>
                          {(isAr ? t.recommendationAr : t.recommendationEn) && (
                            <div className="esg-disclosure-row__desc">
                              {isAr && t.recommendationAr ? t.recommendationAr : t.recommendationEn}
                            </div>
                          )}
                          {hasTcfdEvidence(t) && (
                            <div className="esg-disclosure-row__evidence">
                              <Paperclip size={10} />
                              <span>{isAr ? 'الدليل:' : 'Evidence:'}</span>
                              <span className="mono">{t.evidenceRef}</span>
                            </div>
                          )}
                          {hasTcfdGap(t) && (
                            <div className="esg-disclosure-row__gap esg-banner esg-banner--warning">
                              <AlertCircle size={11} className="esg-icon-inline" />
                              <strong>{isAr ? 'فجوة:' : 'Gap:'}</strong>{' '}
                              {isAr && t.gapDescriptionAr ? t.gapDescriptionAr : (t.gapDescriptionEn ?? t.gapDescriptionAr)}
                            </div>
                          )}
                        </div>
                        <div className="esg-disclosure-row__actions">
                          <Badge tone={tcfdStatusTone(t.disclosureStatus)}>{tcfdStatusLabel(t.disclosureStatus, isAr ? 'ar' : 'en')}</Badge>
                          <div className="esg-disclosure-row__btn-row">
                            {t.disclosureStatus !== 'DISCLOSED'     && <Button size="sm" variant="ghost" onClick={() => onMarkDisclosed(t)}>{isAr ? 'مُفصَح' : 'Disclosed'}</Button>}
                            {t.disclosureStatus !== 'PARTIAL'       && <Button size="sm" variant="ghost" onClick={() => onMarkPartial(t)}>{isAr ? 'جزئي' : 'Partial'}</Button>}
                            {t.disclosureStatus !== 'NOT_DISCLOSED' && <Button size="sm" variant="ghost" onClick={() => onMarkNotDisclosed(t)}>{isAr ? 'غير مُفصَح' : 'Not'}</Button>}
                            {t.disclosureStatus !== 'UNDER_REVIEW'  && <Button size="sm" variant="ghost" onClick={() => onMarkUnderReview(t)}>{isAr ? 'مراجعة' : 'Review'}</Button>}
                            {t.disclosureStatus !== 'NOT_APPLICABLE'&& <Button size="sm" variant="ghost" onClick={() => onMarkNotApplicable(t)}>{isAr ? 'لا ينطبق' : 'N/A'}</Button>}
                          </div>
                          <div className="esg-disclosure-row__btn-row">
                            <Button size="sm" variant="ghost" Icon={Pencil}
                              title={isAr ? 'تعديل' : 'Edit'} onClick={() => setEditTcfd(t)} />
                            <Button size="sm" variant="secondary" Icon={Paperclip}   onClick={() => setEvidencingId(t.id!)}>{isAr ? 'إرفاق' : 'Attach'}</Button>
                            <Button size="sm" variant="secondary" Icon={AlertCircle} onClick={() => setGappingId({ id: t.id! })}>{isAr ? 'فجوة' : 'Gap'}</Button>
                            <Button size="sm" variant="secondary" Icon={Link2} onClick={() => setLinkingTcfd(t)}>
                              {t.relatedClimateRiskId ? (isAr ? 'تغيير المخاطرة' : 'Relink risk') : (isAr ? 'ربط مخاطرة' : 'Link risk')}
                            </Button>
                          </div>
                          {t.relatedClimateRiskId != null && (
                            <div className="text-2xs text-muted" style={{ marginTop: 2 }}>
                              <Link2 size={10} className="esg-icon-inline" />
                              {isAr ? 'مخاطرة مرتبطة: ' : 'Linked risk: '}{climateRiskLabel(t.relatedClimateRiskId)}
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                );
              })}
      </Card>

      <FormModal
        open={evidencingId !== null}
        title={isAr ? 'إرفاق دليل للتوصية' : 'Attach evidence to recommendation'}
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
            ? (isAr ? `الفجوة مطلوبة لـ ${gappingId.toStatus === 'PARTIAL' ? 'الإفصاح الجزئي' : 'عدم الإفصاح'} (القاعدة 5)`
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
        open={openNew}
        title={isAr ? 'توصية TCFD جديدة' : 'New TCFD recommendation'}
        fields={createFields}
        initial={{
          pillar: 'GOVERNANCE', recommendationCode: '', reportingYear: 2025,
          titleEn: '', titleAr: '', disclosureStatus: 'UNDER_REVIEW',
          ownerName: '', recommendationEn: '',
        }}
        size="lg"
        submitLabel={isAr ? 'إضافة التوصية' : 'Add recommendation'}
        onClose={() => setOpenNew(false)}
        onSubmit={submitCreate}
      />

      <FormModal
        open={editTcfd !== null}
        title={editTcfd
          ? (isAr
              ? `تعديل التوصية · ${tcfdPillarLabel(editTcfd.pillar, 'ar')} (${editTcfd.recommendationCode})`
              : `Edit recommendation · ${tcfdPillarLabel(editTcfd.pillar, 'en')} (${editTcfd.recommendationCode})`)
          : ''}
        fields={editFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditTcfd(null)}
        onSubmit={submitEdit}
      />

      <FormModal
        open={linkingTcfd !== null}
        title={linkingTcfd
          ? (isAr ? `ربط مخاطرة مناخية · ${linkingTcfd.recommendationCode}` : `Link climate risk · ${linkingTcfd.recommendationCode}`)
          : ''}
        fields={linkFields}
        initial={{ climateRiskId: linkingTcfd?.relatedClimateRiskId != null ? String(linkingTcfd.relatedClimateRiskId) : '' }}
        size="md"
        submitLabel={isAr ? 'ربط' : 'Link'}
        onClose={() => setLinkingTcfd(null)}
        onSubmit={submitLink}
      />
    </div>
  );
}
