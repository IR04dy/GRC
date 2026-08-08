/**
 * F2-16 tranche 3 — the annual sustainability annex & reporting-year lifecycle tab, moved out of `EsgPage.tsx`.
 *
 * One of seven extracted together, each **self-contained by measurement**: zero references to any
 * page-local identifier outside its own range, exactly one call site in the page's tab switch, and
 * nothing declared inside it needed outside.
 *
 * <p>This file carries four companions the tab cannot work without — `RY_FORWARD`,
 * `RY_STATUS_LABEL`, the `RyForm` shape and `ryFields()`. They were spread on either side of
 * `AnnexTab` in the page and are used by nothing else; the boundary rule collected them
 * without needing a special case, which is the argument for the rule.
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
  FileText,
  CheckCircle2, Building2,
  Plus, ArrowRight,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import {
  listReportingYears, createReportingYear, advanceReportingYear, listReportsForYear, generateAnnex, generateReport,
  reportTypeLabel,
  submitReport, approveReport, publishReport, exportReport,
  ryStatusTone, reportStatusTone,
  type ReportingYear as Ry, type SustainabilityReport as SR,
  type ReportType, type RyStatus,
} from '../../lib/sustainabilityAnnex';

// ─── Annual Sustainability Annex tab (V57) ───────────────────────────────────
//
// The headline Saudi compliance deliverable: bilingual annex covering CMA
// Articles 91-94, Saudi Green Initiative, TCFD, GHG inventory, and Climate
// Risks. Click "Generate" → backend composes Markdown + HTML + JSON from
// live data → DRAFT report row appears. Submit → Approve → Publish moves
// it through the lifecycle. Export downloads any of the three formats.

// Reporting-year lifecycle: a single linear forward step plus ARCHIVED from any
// non-archived state (mirrors SustainabilityReportService.transitionYear).
const RY_FORWARD: Record<RyStatus, RyStatus | null> = {
  ACTIVE: 'DATA_COLLECTION', DATA_COLLECTION: 'REVIEW', REVIEW: 'APPROVED',
  APPROVED: 'PUBLISHED', PUBLISHED: null, ARCHIVED: null,
};
const RY_STATUS_LABEL: Record<RyStatus, { en: string; ar: string }> = {
  ACTIVE:          { en: 'Active',          ar: 'نشطة' },
  DATA_COLLECTION: { en: 'Data collection', ar: 'جمع البيانات' },
  REVIEW:          { en: 'Review',          ar: 'مراجعة' },
  APPROVED:        { en: 'Approved',        ar: 'معتمدة' },
  PUBLISHED:       { en: 'Published',       ar: 'منشورة' },
  ARCHIVED:        { en: 'Archived',        ar: 'مؤرشفة' },
};

export function AnnexTab() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [years, setYears]     = useState<Ry[]>([]);
  const [activeRy, setActive] = useState<Ry | null>(null);
  const [reports, setReports] = useState<SR[]>([]);
  const [loading, setLoading] = useState(true);
  const [generating, setGen]  = useState(false);
  const [previewId, setPreviewId] = useState<number | null>(null);
  // Slice-12 — selected report type for the Generate flow.
  const [selectedReportType, setSelectedReportType] = useState<ReportType>('ANNUAL_ANNEX');

  // New-reporting-year modal. The tab could pick a year, generate annexes and
  // run the report lifecycle, but had no way to open a fresh reporting year,
  // leaving createReportingYear orphaned. Memoised initial keeps a stable
  // identity for FormModal's open-reset effect.
  const [openNewYear, setOpenNewYear] = useState(false);
  const yearInitial = useMemo<RyForm>(() => ({
    year: '', reportingEntityEn: '', reportingEntityAr: '',
    periodStart: '', periodEnd: '', preparedBy: '', notes: '',
  }), []);

  async function loadYears() {
    setLoading(true);
    try {
      const ys = await listReportingYears();
      setYears(ys);
      if (ys.length > 0 && !activeRy) {
        setActive(ys[0]);
        await loadReports(ys[0].id!);
      }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('annexLoadFailed'));
    } finally { setLoading(false); }
  }
  async function loadReports(ryId: number) {
    try {
      const list = await listReportsForYear(ryId);
      setReports(list);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('annexLoadFailed'));
    }
  }
  useEffect(() => { void loadYears(); }, []); // eslint-disable-line

  async function pickYear(y: Ry) {
    setActive(y);
    setPreviewId(null);
    await loadReports(y.id!);
  }

  // Open a fresh reporting year. Client validation mirrors the entity: the
  // year (a SQL SMALLINT) and the English reporting entity are required;
  // the server defaults the status to ACTIVE. On success we reload the year
  // list and focus the newly-created year.
  async function onCreateYear(v: RyForm) {
    const yearNum = Number(v.year);
    if (!Number.isInteger(yearNum) || yearNum < 2000 || yearNum > 2100) {
      toast.danger(isAr ? 'أدخل سنة صحيحة (2000–2100)' : 'Enter a valid year (2000–2100)');
      return;
    }
    const reportingEntityEn = String(v.reportingEntityEn ?? '').trim();
    if (!reportingEntityEn) {
      toast.danger(isAr ? 'اسم جهة الإبلاغ (إنجليزي) مطلوب' : 'Reporting entity (EN) is required');
      return;
    }
    try {
      const created = await createReportingYear({
        year: yearNum,
        reportingEntityEn,
        reportingEntityAr: String(v.reportingEntityAr ?? '').trim() || undefined,
        periodStart: String(v.periodStart ?? '').trim() || undefined,
        periodEnd: String(v.periodEnd ?? '').trim() || undefined,
        preparedBy: String(v.preparedBy ?? '').trim() || undefined,
        notes: String(v.notes ?? '').trim() || undefined,
      });
      toast.success(isAr ? `تم إنشاء سنة الإبلاغ ${created.year}` : `Reporting year ${created.year} created`);
      setOpenNewYear(false);
      await loadYears();
      if (created.id) { setActive(created); await loadReports(created.id); }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل إنشاء سنة الإبلاغ' : 'Create failed'));
    }
  }

  // Advance the active reporting year through its lifecycle. The server allows
  // a single linear forward step (ACTIVE -> DATA_COLLECTION -> REVIEW ->
  // APPROVED -> PUBLISHED) plus ARCHIVED from any state; the UI offers exactly
  // those so an illegal transition is never attempted.
  async function onAdvance(to: RyStatus) {
    if (!activeRy?.id) return;
    try {
      const updated = await advanceReportingYear(activeRy.id, to);
      setActive(updated);
      setYears(prev => prev.map(y => (y.id === updated.id ? updated : y)));
      toast.success(isAr ? `حالة سنة الإبلاغ → ${RY_STATUS_LABEL[to][isAr ? 'ar' : 'en']}` : `Reporting year → ${to}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تغيير الحالة' : 'Failed to advance'));
    }
  }

  async function onGenerate() {
    if (!activeRy?.id) return;
    setGen(true);
    try {
      // Slice 12 — dispatch via the generic generator when a non-default
      // type is selected; otherwise stay on the legacy endpoint.
      const r = selectedReportType === 'ANNUAL_ANNEX'
        ? await generateAnnex(activeRy.id)
        : await generateReport(activeRy.id, selectedReportType);
      toast.success(t('annexGenerated').replace('{v}', r.version));
      await loadReports(activeRy.id);
      setPreviewId(r.id!);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('annexActionFailed'));
    } finally { setGen(false); }
  }

  async function onSubmit_(id: number) {
    try { await submitReport(id); toast.success(t('annexSubmitted')); await loadReports(activeRy!.id!); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('annexActionFailed')); }
  }
  async function onApprove(id: number) {
    try { await approveReport(id); toast.success(t('annexApproved')); await loadReports(activeRy!.id!); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('annexActionFailed')); }
  }
  async function onPublish(id: number) {
    if (!window.confirm(t('annexPublishConfirm'))) return;
    try { await publishReport(id); toast.success(t('annexPublished')); await loadReports(activeRy!.id!); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('annexActionFailed')); }
  }
  async function onExport(id: number, fmt: 'md' | 'html' | 'json') {
    try { await exportReport(id, fmt); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('annexActionFailed')); }
  }

  const previewReport = useMemo(
    () => reports.find(r => r.id === previewId) ?? null,
    [reports, previewId]
  );

  if (loading) return <InlineEmpty>{t('annexLoading')}</InlineEmpty>;

  return (
    <div className="esg-stack">
      {/* Year picker strip */}
      <div className="row-gap-md" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <span className="text-2xs text-muted esg-text-eyebrow">{t('annexYear')}</span>
        {years.map(y => (
          <button key={y.id} type="button" onClick={() => pickYear(y)}
            style={{
              padding: '6px 12px', borderRadius: 'var(--r-sm)', fontSize: 12, fontWeight: 600,
              border: `1px solid ${y.id === activeRy?.id ? 'var(--primary)' : 'var(--border)'}`,
              background: y.id === activeRy?.id ? 'var(--primary-50)' : 'var(--surface-1)',
              cursor: 'pointer',
            }}>
            {y.year}
          </button>
        ))}
        <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenNewYear(true)}>
          {isAr ? 'سنة إبلاغ جديدة' : 'New reporting year'}
        </Button>
      </div>

      {years.length === 0 && <InlineEmpty>{t('annexNoYears')}</InlineEmpty>}

      {/* Selected-year summary card */}
      {activeRy && (
        <Card title={t('annexYearTitle').replace('{y}', String(activeRy.year))} Icon={Building2}
          action={
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <Badge tone={ryStatusTone(activeRy.status)}>{activeRy.status}</Badge>
              {activeRy.status && RY_FORWARD[activeRy.status] && (
                <Button size="sm" variant="primary" onClick={() => void onAdvance(RY_FORWARD[activeRy.status!]!)}>
                  {isAr
                    ? `الانتقال إلى ${RY_STATUS_LABEL[RY_FORWARD[activeRy.status]!].ar}`
                    : `Advance to ${RY_STATUS_LABEL[RY_FORWARD[activeRy.status]!].en}`}
                </Button>
              )}
              {activeRy.status && activeRy.status !== 'ARCHIVED' && (
                <Button size="sm" variant="ghost" onClick={() => void onAdvance('ARCHIVED')}>
                  {isAr ? 'أرشفة' : 'Archive'}
                </Button>
              )}
            </div>
          }>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
            <div className="stat-tile">
              <div className="stat-tile-label">{t('annexEntity')}</div>
              <div className="esg-text-strong">
                {isAr && activeRy.reportingEntityAr ? activeRy.reportingEntityAr : activeRy.reportingEntityEn}
              </div>
            </div>
            <div className={`stat-tile stat-tile-${(activeRy.readinessScorePct ?? 0) >= 80 ? 'success' : 'warning'}`}>
              <div className="stat-tile-label">{t('annexReadiness')}</div>
              <div className="stat-tile-value">{activeRy.readinessScorePct == null ? '—' : `${activeRy.readinessScorePct}%`}</div>
            </div>
            <div className={`stat-tile stat-tile-${(activeRy.openGapsCount ?? 0) === 0 ? 'success' : 'warning'}`}>
              <div className="stat-tile-label">{t('annexOpenGaps')}</div>
              <div className="stat-tile-value">{activeRy.openGapsCount ?? 0}</div>
            </div>
            <div className="stat-tile">
              <div className="stat-tile-label">{t('annexPrepared')}</div>
              <div style={{ fontWeight: 500, color: 'var(--ink-2)', fontSize: 13 }}>{activeRy.preparedBy ?? '—'}</div>
            </div>
          </div>
          {activeRy.notes && (
            <div style={{ marginTop: 12, padding: 10, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', fontSize: 12, color: 'var(--ink-2)' }}>
              {activeRy.notes}
            </div>
          )}
          <div style={{ marginTop: 12, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            {/* Slice-12 — report-type picker. Default ANNUAL_ANNEX
                preserves the legacy one-button flow. */}
            <select
              value={selectedReportType}
              onChange={e => setSelectedReportType(e.target.value as ReportType)}
              style={{
                padding: '8px 10px', fontSize: 12,
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                background: 'var(--surface-1)', color: 'var(--ink-1)', minWidth: 240,
              }}
              disabled={generating}
            >
              {(['ANNUAL_ANNEX', 'BOARD_ESG', 'CMA_MATRIX', 'TCFD_REPORT', 'GHG_REPORT',
                 'SGI_PROGRESS', 'CLIMATE_RISK', 'CAPA_STATUS', 'EVIDENCE_PKG', 'DASH_EXPORT'] as ReportType[]).map(t => (
                <option key={t} value={t}>{reportTypeLabel(t, lang)}</option>
              ))}
            </select>
            <Button variant="primary" Icon={FileText} onClick={onGenerate} disabled={generating || !activeRy.id}>
              {generating ? t('annexGenerating') : t('annexGenerateBtn')}
            </Button>
          </div>
        </Card>
      )}

      {/* Reports list */}
      {activeRy && (
      <Card title={t('annexReportsTitle')} Icon={FileText}>
        {reports.length === 0
          ? <InlineEmpty>{t('annexNoReports')}</InlineEmpty>
          : (
            <table className="grc-table">
              <thead>
                <tr>
                  <th>{t('annexColCode')}</th>
                  <th>{t('annexColType')}</th>
                  <th>{t('annexColVersion')}</th>
                  <th>{t('annexColGenerated')}</th>
                  <th>{t('annexColStatus')}</th>
                  <th className="esg-text-end">{t('annexColActions')}</th>
                </tr>
              </thead>
              <tbody>
                {reports.map(r => (
                  <tr key={r.id} style={r.id === previewId ? { background: 'var(--primary-50)' } : undefined}>
                    <td className="mono">{r.reportCode ?? `#${r.id}`}</td>
                    <td><Badge tone="info">{r.reportType}</Badge></td>
                    <td className="mono">{r.version}</td>
                    <td className="mono text-xs">{r.generatedAt?.slice(0, 16).replace('T', ' ')}</td>
                    <td><Badge tone={reportStatusTone(r.status)}>{statusLabel(r.status, lang)}</Badge></td>
                    <td className="esg-text-end">
                      <div className="esg-actions-row">
                        <Button variant="ghost"     size="sm" onClick={() => setPreviewId(r.id!)}>{t('annexActPreview')}</Button>
                        <Button variant="ghost"     size="sm" onClick={() => onExport(r.id!, 'md')}>MD</Button>
                        <Button variant="ghost"     size="sm" onClick={() => onExport(r.id!, 'html')}>HTML</Button>
                        <Button variant="ghost"     size="sm" onClick={() => onExport(r.id!, 'json')}>JSON</Button>
                        {r.status === 'DRAFT'     && <Button variant="secondary" size="sm" Icon={ArrowRight}   onClick={() => onSubmit_(r.id!)}>{t('annexActSubmit')}</Button>}
                        {r.status === 'SUBMITTED' && <Button variant="secondary" size="sm" Icon={CheckCircle2} onClick={() => onApprove(r.id!)}>{t('annexActApprove')}</Button>}
                        {r.status === 'APPROVED'  && <Button variant="primary"   size="sm" Icon={CheckCircle2} onClick={() => onPublish(r.id!)}>{t('annexActPublish')}</Button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
      </Card>
      )}

      {/* Live preview pane */}
      {previewReport && previewReport.contentMarkdown && (
        <Card title={t('annexPreviewTitle').replace('{v}', previewReport.version)} Icon={FileText}
          action={<Badge tone={reportStatusTone(previewReport.status)}>{previewReport.status}</Badge>}>
          <pre style={{
            fontFamily: 'var(--font-mono)', fontSize: 11, lineHeight: 1.55,
            background: 'var(--surface-2)', color: 'var(--ink-1)',
            padding: 16, borderRadius: 'var(--r-sm)',
            border: '1px solid var(--border)',
            whiteSpace: 'pre-wrap', maxHeight: 480, overflow: 'auto',
            direction: 'ltr',
          }}>{previewReport.contentMarkdown}</pre>
        </Card>
      )}

      <FormModal
        open={openNewYear}
        title={isAr ? 'سنة إبلاغ جديدة' : 'New reporting year'}
        size="md"
        submitLabel={isAr ? 'إنشاء' : 'Create'}
        fields={ryFields(isAr)}
        initial={yearInitial}
        onClose={() => setOpenNewYear(false)}
        onSubmit={onCreateYear}
      />
    </div>
  );
}

// All-string create-reporting-year form. `year` is a number-kind field whose
// runtime value the submit handler coerces; the rest are plain strings.
interface RyForm {
  year:              string;
  reportingEntityEn: string;
  reportingEntityAr: string;
  periodStart:       string;
  periodEnd:         string;
  preparedBy:        string;
  notes:             string;
}

function ryFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'number', key: 'year', label: isAr ? 'السنة' : 'Year', span: 4, min: 2000, max: 2100, required: true },
    { kind: 'date', key: 'periodStart', label: isAr ? 'بداية الفترة' : 'Period start', span: 4 },
    { kind: 'date', key: 'periodEnd', label: isAr ? 'نهاية الفترة' : 'Period end', span: 4 },

    { kind: 'text', key: 'reportingEntityEn', label: isAr ? 'جهة الإبلاغ (إنجليزي)' : 'Reporting entity (EN)',
      span: 6, required: true, maxLength: 255, placeholder: isAr ? 'مثلاً: TSS GRC Platform' : 'e.g. TSS GRC Platform' },
    { kind: 'text', key: 'reportingEntityAr', label: isAr ? 'جهة الإبلاغ (عربي)' : 'Reporting entity (AR)',
      span: 6, maxLength: 255, placeholder: isAr ? 'مثلاً: منصة تي إس إس للحوكمة' : 'e.g. منصة تي إس إس للحوكمة' },

    { kind: 'text', key: 'preparedBy', label: isAr ? 'أُعدّ بواسطة' : 'Prepared by', span: 12, maxLength: 255 },
    { kind: 'textarea', key: 'notes', label: isAr ? 'ملاحظات' : 'Notes', span: 12, rows: 2 },
  ];
}
