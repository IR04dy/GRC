import { useEffect, useMemo, useState } from 'react';
import {
  Database, FileText, Target, Wrench, AlertCircle, CheckCircle2,
  Plus, ArrowRight, Trash2, Copy, Archive as ArchiveIcon, Upload, Check,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import {
  listCycles, cyclesAggregate, createCycle, transitionCycle,
  archiveCycle, cloneCycle, deleteCycle, nextStep,
  cycleStatusTone, cycleStatusLabel, readinessTone, readinessLabel,
  type NdiAssessmentCycle, type NdiCycleAggregate,
} from '../../lib/ndiCycles';
import {
  listFramework, frameworkAggregate, markOfficial, markPlaceholder,
  importFrameworkJson, deleteFrameworkItem,
  componentTone, componentLabel, priorityTone, priorityLabel,
  type NdiFrameworkItem, type NdiFrameworkAggregate, type NdiComponent,
} from '../../lib/ndiFramework';
import {
  listMaturity, maturityAggregate, startMaturityForCycle, updateMaturity,
  submitMaturity, markUnderReviewMaturity, returnMaturity, approveMaturity,
  reopenMaturity,
  maturityStatusTone, maturityStatusLabel, levelLabel, levelColor,
  type NdiMaturityAssessment, type NdiMaturityAggregate,
} from '../../lib/ndiMaturity';

/**
 * SDAIA National Data Index (NDI / نضيء) Management page.
 *
 * <p>Routed at {@code /ndi}. 15-tab shell mirroring the spec menu —
 * slice 1 (this commit) implements the Cycles tab end-to-end; slices
 * 2-14 will fill the remaining tabs incrementally, slice 15 ships
 * tests + QA checklist.
 *
 * <p>Tabs visible in the strip but not yet populated render a
 * placeholder card pointing at the relevant follow-up slice.
 */
type NdiTab =
  | 'dashboard'       // slice 12
  | 'cycles'          // slice 1 (this commit)
  | 'framework'       // slice 2
  | 'maturity'        // slice 3
  | 'compliance'      // slice 4
  | 'opex'            // slice 5
  | 'domains'         // slice 6
  | 'evidence'        // slice 7
  | 'gaps'            // slice 8
  | 'improvement'    // slice 9
  | 'submission'     // slice 10
  | 'reports'        // slice 11
  | 'settings'       // slice 13
  | 'import'         // slice 2 sub-page (import official framework)
  | 'audit';         // slice 14

export function NdiPage() {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const [tab, setTab] = useState<NdiTab>('cycles');

  return (
    <Shell
      title={isAr ? 'إدارة مؤشر نضيء — المؤشر الوطني للبيانات' : 'SDAIA NDI Management'}
      sub={isAr ? 'المؤشر الوطني للبيانات — جاهزية وتقييم سدايا' : 'National Data Index — SDAIA readiness + assessment'}>
      <PageHeader
        eyebrow={isAr ? 'حوكمة البيانات' : 'Data Governance'}
        title={isAr ? 'إدارة مؤشر نضيء' : 'SDAIA NDI Management'}
        sub={isAr
          ? 'إدارة دورات التقييم والإطار والنضج والامتثال والتميز التشغيلي والأدلة والفجوات وحزمة الرفع.'
          : 'Manage assessment cycles, framework, maturity, compliance, operational excellence, evidence, gaps, and submission package.'}
      />

      {/* Tab strip — 15 tabs matching the spec's menu order */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'dashboard',   label: { en: 'Dashboard',          ar: 'لوحة المعلومات' },       Icon: Database },
          { id: 'cycles',      label: { en: 'Assessment cycles',  ar: 'دورات التقييم' },        Icon: ArrowRight },
          { id: 'framework',   label: { en: 'Framework library',  ar: 'مكتبة الإطار' },         Icon: FileText },
          { id: 'maturity',    label: { en: 'Maturity',           ar: 'النضج' },                Icon: Target },
          { id: 'compliance',  label: { en: 'Compliance',         ar: 'الامتثال' },             Icon: CheckCircle2 },
          { id: 'opex',        label: { en: 'Operational excellence', ar: 'التميز التشغيلي' }, Icon: Target },
          { id: 'domains',     label: { en: 'Governance domains', ar: 'مجالات الحوكمة' },       Icon: Database },
          { id: 'evidence',    label: { en: 'Evidence',           ar: 'الأدلة' },               Icon: FileText },
          { id: 'gaps',        label: { en: 'Gaps + actions',     ar: 'الفجوات والإجراءات' },   Icon: AlertCircle },
          { id: 'improvement', label: { en: 'Improvement plan',   ar: 'خطة التحسين' },          Icon: Target },
          { id: 'submission',  label: { en: 'Submission package', ar: 'حزمة الرفع' },           Icon: FileText },
          { id: 'reports',     label: { en: 'Reports',            ar: 'التقارير' },             Icon: FileText },
          { id: 'settings',    label: { en: 'Settings',           ar: 'الإعدادات' },            Icon: Wrench },
          { id: 'import',      label: { en: 'Import framework',   ar: 'استيراد الإطار' },       Icon: FileText },
          { id: 'audit',       label: { en: 'Audit trail',        ar: 'سجل التدقيق' },          Icon: FileText },
        ] as { id: NdiTab; label: { en: string; ar: string }; Icon: typeof Database }[]).map(td => (
          <button key={td.id} onClick={() => setTab(td.id)}
            style={{
              padding: '8px 12px', borderRadius: 'var(--r-sm)', fontSize: 12, fontWeight: 600,
              border: `1px solid ${tab === td.id ? 'var(--primary)' : 'var(--border)'}`,
              background: tab === td.id ? 'var(--primary-50)' : 'var(--surface-1)',
              cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6,
            }}>
            <td.Icon size={13} />
            {isAr ? td.label.ar : td.label.en}
          </button>
        ))}
      </div>

      {tab === 'cycles'    && <CyclesTab />}
      {tab === 'framework' && <FrameworkTab />}
      {tab === 'maturity'  && <MaturityTab />}
      {tab !== 'cycles' && tab !== 'framework' && tab !== 'maturity' && <NotYetShipped tab={tab} isAr={isAr} t={t} />}
    </Shell>
  );
}

// ─── Cycles tab (slice 1 — end-to-end implementation) ──────────────────

function CyclesTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<NdiAssessmentCycle[]>([]);
  const [agg, setAgg] = useState<NdiCycleAggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [openNew, setOpenNew] = useState(false);

  async function reload() {
    setLoading(true);
    try {
      const [pg, a] = await Promise.all([listCycles({ size: 100 }), cyclesAggregate()]);
      setItems(pg.content);
      setAgg(a);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل الدورات' : 'Failed to load cycles'));
      setItems([]); setAgg(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  async function onCreate(values: Record<string, unknown>) {
    try {
      await createCycle({
        cycleNameEn:   String(values.cycleNameEn ?? '').trim(),
        cycleNameAr:   String(values.cycleNameAr ?? '').trim() || undefined,
        reportingYear: Number(values.reportingYear ?? new Date().getFullYear()),
        scope:         String(values.scope ?? '').trim() || undefined,
        entityNameEn:  String(values.entityNameEn ?? '').trim() || undefined,
        entityNameAr:  String(values.entityNameAr ?? '').trim() || undefined,
        dataOfficeOwnerName:    String(values.dataOfficeOwnerName ?? '').trim() || undefined,
        assessmentManagerName:  String(values.assessmentManagerName ?? '').trim() || undefined,
        status: 'DRAFT',
      });
      toast.success(isAr ? 'أُنشئت الدورة' : 'Cycle created');
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function onTransition(c: NdiAssessmentCycle) {
    const next = nextStep(c.status);
    if (!next) {
      toast.info(isAr ? 'لا توجد خطوة تالية متاحة' : 'No further forward step available');
      return;
    }
    try {
      await transitionCycle(c.id!, next);
      toast.success(isAr ? `الحالة الآن: ${cycleStatusLabel(next, 'ar')}` : `Now: ${cycleStatusLabel(next, 'en')}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function onArchive(c: NdiAssessmentCycle) {
    const ok = await dialog.confirm({
      title: isAr ? 'أرشفة الدورة؟' : 'Archive cycle?',
      body: isAr ? 'الأرشفة نهائية. لن تستطيع إعادتها بعد ذلك.' : 'Archive is terminal. You won\'t be able to reopen it.',
      destructive: true, submitLabel: isAr ? 'أرشفة' : 'Archive',
    });
    if (!ok) return;
    try { await archiveCycle(c.id!); toast.info(isAr ? 'تمت الأرشفة' : 'Archived'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onClone(c: NdiAssessmentCycle) {
    const ans = await dialog.prompt({
      title: isAr ? 'استنساخ الدورة' : 'Clone cycle',
      label: isAr ? 'سنة الدورة الجديدة' : 'New reporting year',
      type: 'number', required: true, min: 2020, max: 2100,
      initial: String((c.reportingYear ?? new Date().getFullYear()) + 1),
    });
    if (!ans) return;
    try {
      await cloneCycle(c.id!, Number(ans));
      toast.success(isAr ? 'أُنشئت الدورة المستنسَخة كمسودّة' : 'Cloned cycle created as DRAFT');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function onDelete(c: NdiAssessmentCycle) {
    const ok = await dialog.confirm({
      title: isAr ? 'حذف الدورة؟' : 'Delete cycle?',
      body: isAr ? 'حذف ناعم — يمكن استرجاعها عبر قاعدة البيانات.' : 'Soft delete — can be restored from the database.',
      destructive: true, submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try { await deleteCycle(c.id!); toast.info(isAr ? 'تم الحذف' : 'Deleted'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const newFields: FieldDef[] = useMemo(() => [
    { kind: 'text',     key: 'cycleNameEn',           required: true, span: 6,
      label: isAr ? 'اسم الدورة (إنجليزي) *' : 'Cycle name (English) *',
      placeholder: 'NDI Readiness Cycle 2026' },
    { kind: 'text',     key: 'cycleNameAr',           span: 6,
      label: isAr ? 'اسم الدورة (عربي)' : 'Cycle name (Arabic)',
      placeholder: 'دورة جاهزية نضيء 2026' },
    { kind: 'number',   key: 'reportingYear',         required: true, span: 3, min: 2020, max: 2100,
      label: isAr ? 'سنة الإبلاغ *' : 'Reporting year *' },
    { kind: 'text',     key: 'entityNameEn',          span: 6,
      label: isAr ? 'اسم الجهة (إنجليزي)' : 'Entity name (English)' },
    { kind: 'text',     key: 'entityNameAr',          span: 6,
      label: isAr ? 'اسم الجهة (عربي)' : 'Entity name (Arabic)' },
    { kind: 'text',     key: 'dataOfficeOwnerName',   span: 6,
      label: isAr ? 'مالك مكتب البيانات' : 'Data Office owner',
      placeholder: isAr ? 'مثال: سارة الزهراني' : 'e.g. Sara Al-Zahrani' },
    { kind: 'text',     key: 'assessmentManagerName', span: 6,
      label: isAr ? 'مدير التقييم' : 'Assessment manager',
      placeholder: isAr ? 'مثال: خالد العتيبي' : 'e.g. Khalid Al-Otaibi' },
    { kind: 'textarea', key: 'scope', rows: 3, span: 12,
      label: isAr ? 'نطاق التقييم' : 'Assessment scope' },
  ], [isAr]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="kpi-grid">
        <Kpi Icon={Database}      label={isAr ? 'إجمالي الدورات' : 'Total cycles'}      value={agg?.total ?? 0}  tone="primary" />
        <Kpi Icon={ArrowRight}    label={isAr ? 'دورات نشطة'    : 'Active cycles'}     value={agg?.active ?? 0} tone="info" />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'مُعتمَدة'        : 'Approved'}          value={agg?.byStatus.APPROVED ?? 0}  tone="success" />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'مُقدَّمة'         : 'Submitted'}         value={agg?.byStatus.SUBMITTED ?? 0} tone="success" />
        <Kpi Icon={AlertCircle}   label={isAr ? 'معالجة فجوات' : 'Gap remediation'}   value={agg?.byStatus.GAP_REMEDIATION ?? 0}  tone="warning" />
      </div>

      <Card title={isAr ? 'دورات تقييم نضيء' : 'NDI assessment cycles'} Icon={Database}
        action={
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenNew(true)}>
            {isAr ? 'دورة جديدة' : 'New cycle'}
          </Button>
        }>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {isAr
            ? 'مصفوفة دورات جاهزية مؤشر نضيء. كل دورة تمر بـ 12 خطوة من المسودّة إلى التقديم لسدايا، مع 13 حالة لحظية مرصودة. الإطار والنضج والامتثال والأدلة سترتبط بهذه الدورات لاحقاً عبر الشرائح 2-14.'
            : 'NDI readiness cycles. Each cycle moves through 12 forward steps from Draft to Submitted, with 13 distinct statuses tracked. Framework / maturity / compliance / evidence will link to these cycles via slices 2-14.'}
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : items.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد دورات بعد — ابدأ بإنشاء واحدة' : 'No cycles yet — create one to start'}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'الاسم' : 'Name'}</th>
                    <th>{isAr ? 'السنة' : 'Year'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th>{isAr ? 'النتيجة الإجمالية' : 'Overall'}</th>
                    <th>{isAr ? 'الجاهزية' : 'Readiness'}</th>
                    <th>{isAr ? 'المالك' : 'Owner'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map(c => {
                    const next = nextStep(c.status);
                    const isTerminal = c.status === 'CLOSED' || c.status === 'ARCHIVED';
                    return (
                      <tr key={c.id}>
                        <td className="mono text-2xs">{c.cycleCode ?? `#${c.id}`}</td>
                        <td>
                          <div style={{ fontWeight: 600 }}>
                            {isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn}
                          </div>
                          {c.entityNameEn && (
                            <div className="text-2xs text-muted" style={{ marginTop: 2 }}>
                              {isAr && c.entityNameAr ? c.entityNameAr : c.entityNameEn}
                            </div>
                          )}
                        </td>
                        <td className="mono">{c.reportingYear}</td>
                        <td><Badge tone={cycleStatusTone(c.status)}>{cycleStatusLabel(c.status, isAr ? 'ar' : 'en')}</Badge></td>
                        <td className="mono">{c.overallScore != null ? `${Number(c.overallScore).toFixed(0)} / 100` : '—'}</td>
                        <td><Badge tone={readinessTone(c.submissionReadinessStatus)}>{readinessLabel(c.submissionReadinessStatus, isAr ? 'ar' : 'en')}</Badge></td>
                        <td className="text-xs">{c.dataOfficeOwnerName ?? '—'}</td>
                        <td style={{ textAlign: 'end' }}>
                          <div style={{ display: 'inline-flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                            {next && !isTerminal && (
                              <Button size="sm" variant="secondary" Icon={ArrowRight}
                                onClick={() => onTransition(c)}
                                title={isAr ? `التالي: ${cycleStatusLabel(next, 'ar')}` : `Next: ${cycleStatusLabel(next, 'en')}`}>
                                {isAr ? 'تقدّم' : 'Advance'}
                              </Button>
                            )}
                            <Button size="sm" variant="ghost" Icon={Copy}
                              title={isAr ? 'استنساخ' : 'Clone'}
                              onClick={() => onClone(c)} />
                            {c.status !== 'ARCHIVED' && (
                              <Button size="sm" variant="ghost" Icon={ArchiveIcon}
                                title={isAr ? 'أرشفة' : 'Archive'}
                                onClick={() => onArchive(c)} />
                            )}
                            <Button size="sm" variant="ghost" Icon={Trash2}
                              title={isAr ? 'حذف' : 'Delete'}
                              onClick={() => onDelete(c)} />
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
        title={isAr ? 'دورة جاهزية نضيء جديدة' : 'New NDI assessment cycle'}
        fields={newFields}
        initial={{ reportingYear: new Date().getFullYear() }}
        size="lg"
        submitLabel={isAr ? 'إنشاء' : 'Create'}
        onClose={() => setOpenNew(false)}
        onSubmit={onCreate}
      />
    </div>
  );
}

// ─── Framework Library tab (slice 2 — V67) ─────────────────────────

function FrameworkTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<NdiFrameworkItem[]>([]);
  const [agg, setAgg] = useState<NdiFrameworkAggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [filterComponent, setFilterComponent] = useState<NdiComponent | 'ALL'>('ALL');
  const [importOpen, setImportOpen] = useState(false);

  async function reload() {
    setLoading(true);
    try {
      const [pg, a] = await Promise.all([listFramework({ size: 500 }), frameworkAggregate()]);
      setItems(pg.content);
      setAgg(a);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل مكتبة الإطار' : 'Failed to load framework library'));
      setItems([]); setAgg(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  const filtered = useMemo(() => filterComponent === 'ALL'
    ? items
    : items.filter(i => i.component === filterComponent), [items, filterComponent]);

  async function onMarkOfficial(it: NdiFrameworkItem) {
    try { await markOfficial(it.id!); toast.success(isAr ? 'علّمت كرسمي' : 'Marked as official'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onMarkPlaceholder(it: NdiFrameworkItem) {
    try { await markPlaceholder(it.id!); toast.info(isAr ? 'أُعيد وضع المؤقت' : 'Marked as placeholder'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onDelete(it: NdiFrameworkItem) {
    const ok = await dialog.confirm({
      title: isAr ? 'حذف العنصر؟' : 'Delete framework item?',
      body: isAr ? 'حذف ناعم — يمكن استرجاعه من قاعدة البيانات.' : 'Soft delete — can be restored from the database.',
      destructive: true, submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try { await deleteFrameworkItem(it.id!); toast.info(isAr ? 'تم الحذف' : 'Deleted'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onImport(values: Record<string, unknown>) {
    const raw = String(values.json ?? '').trim();
    if (!raw) {
      toast.danger(isAr ? 'الصق JSON' : 'Paste JSON');
      return;
    }
    let parsed: unknown;
    try { parsed = JSON.parse(raw); }
    catch { toast.danger(isAr ? 'JSON غير صالح' : 'Invalid JSON'); return; }

    const items = Array.isArray(parsed)
      ? parsed
      : (parsed as { items?: unknown }).items;
    if (!Array.isArray(items)) {
      toast.danger(isAr ? 'يجب أن يكون JSON مصفوفة أو {items: [...]}'
                        : 'JSON must be an array or {items: [...]}');
      return;
    }

    try {
      const summary = await importFrameworkJson(items as Partial<NdiFrameworkItem>[]);
      const msg = isAr
        ? `أُدرج ${summary.inserted} • حُدّث ${summary.updated} • فشل ${summary.failed}`
        : `${summary.inserted} inserted · ${summary.updated} updated · ${summary.failed} failed`;
      if (summary.failed > 0) toast.warning(msg);
      else toast.success(msg);
      setImportOpen(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Import failed');
    }
  }

  const importFields: FieldDef[] = [
    { kind: 'textarea', key: 'json', span: 12, rows: 12,
      label: isAr ? 'JSON للاستيراد' : 'JSON to import' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="kpi-grid">
        <Kpi Icon={FileText}     label={isAr ? 'إجمالي البنود' : 'Total items'}   value={agg?.total ?? 0}            tone="primary" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'بنود رسمية'   : 'Official'}      value={agg?.officialCount ?? 0}    tone="success" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'بنود مؤقتة'   : 'Placeholder'}   value={agg?.placeholderCount ?? 0} tone="warning" />
        <Kpi Icon={Target}       label={isAr ? 'إلزامية'      : 'Mandatory'}     value={agg?.mandatoryCount ?? 0}   tone="danger" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'الامتثال'    : 'Compliance'}    value={agg?.byComponent.COMPLIANCE ?? 0}             tone="info" />
        <Kpi Icon={Target}       label={isAr ? 'النضج'        : 'Maturity'}      value={agg?.byComponent.MATURITY ?? 0}               tone="primary" />
        <Kpi Icon={Target}       label={isAr ? 'تميز تشغيلي' : 'Op. excellence'} value={agg?.byComponent.OPERATIONAL_EXCELLENCE ?? 0} tone="success" />
      </div>

      {agg && agg.placeholderCount > 0 && (
        <div style={{
          padding: '12px 16px', borderRadius: 'var(--r-md)',
          background: 'var(--warning-bg)', color: 'var(--warning-fg)',
          border: '1px solid var(--warning)',
          display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 12, alignItems: 'center',
        }}>
          <AlertCircle size={20} />
          <div style={{ fontSize: 12, lineHeight: 1.5 }}>
            <strong>{isAr ? 'تنبيه:' : 'Notice:'}</strong>{' '}
            {isAr
              ? `${agg.placeholderCount} من أصل ${agg.total} عنصر مؤقت — يتطلب استيراد مكتبة الضوابط الرسمية من سدايا/نضيء قبل التقديم الرسمي.`
              : `${agg.placeholderCount} of ${agg.total} items are placeholders — requires official SDAIA/NDI control library import before formal submission.`}
          </div>
        </div>
      )}

      <Card title={isAr ? 'مكتبة إطار نضيء' : 'NDI framework library'} Icon={FileText}
        action={
          <Button size="sm" variant="primary" Icon={Upload} onClick={() => setImportOpen(true)}>
            {isAr ? 'استيراد JSON' : 'Import JSON'}
          </Button>
        }>
        <div style={{ display: 'flex', gap: 6, marginBottom: 12, flexWrap: 'wrap' }}>
          {(['ALL', 'COMPLIANCE', 'MATURITY', 'OPERATIONAL_EXCELLENCE'] as const).map(c => (
            <button key={c} onClick={() => setFilterComponent(c)}
              style={{
                padding: '4px 10px', fontSize: 11, fontWeight: 600,
                border: `1px solid ${filterComponent === c ? 'var(--primary)' : 'var(--border)'}`,
                background: filterComponent === c ? 'var(--primary-50)' : 'var(--surface-1)',
                color: filterComponent === c ? 'var(--primary)' : 'var(--ink-2)',
                borderRadius: 'var(--r-sm)', cursor: 'pointer',
              }}>
              {c === 'ALL' ? (isAr ? 'الكل' : 'All') : componentLabel(c as NdiComponent, isAr ? 'ar' : 'en')}
            </button>
          ))}
          <span className="text-2xs text-muted" style={{ marginInlineStart: 'auto', alignSelf: 'center' }}>
            {filtered.length} / {items.length}
          </span>
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : filtered.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد بنود — ابدأ بالاستيراد' : 'No items — try import'}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'العنوان' : 'Title'}</th>
                    <th>{isAr ? 'المكوّن' : 'Component'}</th>
                    <th>{isAr ? 'المجال' : 'Domain'}</th>
                    <th>{isAr ? 'الأولوية' : 'Priority'}</th>
                    <th>{isAr ? 'الإلزام' : 'Mandatory'}</th>
                    <th>{isAr ? 'المصدر' : 'Source'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(it => (
                    <tr key={it.id}>
                      <td className="mono text-2xs">{it.requirementCode}</td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{isAr && it.titleAr ? it.titleAr : it.titleEn}</div>
                        {it.subdomain && (
                          <div className="text-2xs text-muted" style={{ marginTop: 2 }}>{it.subdomain}</div>
                        )}
                      </td>
                      <td><Badge tone={componentTone(it.component)}>{componentLabel(it.component, isAr ? 'ar' : 'en')}</Badge></td>
                      <td className="text-xs">{it.domain}</td>
                      <td><Badge tone={priorityTone(it.priority)}>{priorityLabel(it.priority, isAr ? 'ar' : 'en')}</Badge></td>
                      <td>{it.mandatory ? <Badge tone="danger">{isAr ? 'إلزامي' : 'Yes'}</Badge> : <span className="text-muted">—</span>}</td>
                      <td>
                        {it.isOfficialImport
                          ? <Badge tone="success">{isAr ? 'رسمي' : 'Official'}</Badge>
                          : <Badge tone="warning">{isAr ? 'مؤقت' : 'Placeholder'}</Badge>}
                      </td>
                      <td style={{ textAlign: 'end' }}>
                        <div style={{ display: 'inline-flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                          {!it.isOfficialImport && (
                            <Button size="sm" variant="secondary" Icon={Check}
                              onClick={() => onMarkOfficial(it)}>
                              {isAr ? 'علّم رسمي' : 'Mark official'}
                            </Button>
                          )}
                          {it.isOfficialImport && (
                            <Button size="sm" variant="ghost" Icon={AlertCircle}
                              onClick={() => onMarkPlaceholder(it)}
                              title={isAr ? 'إعادة وضع المؤقت' : 'Mark as placeholder'} />
                          )}
                          <Button size="sm" variant="ghost" Icon={Trash2}
                            title={isAr ? 'حذف' : 'Delete'}
                            onClick={() => onDelete(it)} />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
      </Card>

      <FormModal
        open={importOpen}
        title={isAr ? 'استيراد بنود الإطار (JSON)' : 'Import framework items (JSON)'}
        fields={importFields}
        initial={{ json: '' }}
        size="lg"
        submitLabel={isAr ? 'استيراد' : 'Import'}
        onClose={() => setImportOpen(false)}
        onSubmit={onImport}
      />
    </div>
  );
}

// ─── Maturity Assessment tab (slice 3 — V68) ────────────────────────
//
// Per-cycle responses to MATURITY framework items. Picks the most-
// recent active cycle on load; "Start maturity assessment" bulk-creates
// rows from MATURITY framework items if none exist yet. Per-row edit
// uses an inline level dropdown — submit/approve flows go through
// state-machine endpoints with bilingual toasts.

function MaturityTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [cycles, setCycles] = useState<NdiAssessmentCycle[]>([]);
  const [cycleId, setCycleId] = useState<number | null>(null);
  const [items, setItems] = useState<NdiMaturityAssessment[]>([]);
  const [agg, setAgg] = useState<NdiMaturityAggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<NdiMaturityAssessment | null>(null);

  async function loadCycles() {
    try {
      const pg = await listCycles({ size: 100 });
      setCycles(pg.content);
      if (pg.content.length > 0 && !cycleId) setCycleId(pg.content[0].id!);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to load cycles');
    }
  }
  useEffect(() => { void loadCycles(); }, []);

  async function reload() {
    if (!cycleId) return;
    setLoading(true);
    try {
      const [pg, a] = await Promise.all([
        listMaturity(cycleId, { size: 500 }),
        maturityAggregate(cycleId),
      ]);
      setItems(pg.content);
      setAgg(a);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل النضج' : 'Failed to load maturity'));
      setItems([]); setAgg(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { if (cycleId) void reload(); }, [cycleId]);

  async function onStart() {
    if (!cycleId) return;
    try {
      const s = await startMaturityForCycle(cycleId);
      toast.success(isAr
        ? `أُنشئ ${s.created} • تُجوهل ${s.skipped}`
        : `Created ${s.created} · Skipped ${s.skipped}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function onLevelChange(it: NdiMaturityAssessment, field: 'currentLevel' | 'targetLevel', val: number) {
    try {
      await updateMaturity(it.id!, { [field]: val });
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onSubmit(it: NdiMaturityAssessment) {
    try { await submitMaturity(it.id!); toast.success(isAr ? 'مُقدَّم' : 'Submitted'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onReview(it: NdiMaturityAssessment) {
    try { await markUnderReviewMaturity(it.id!); toast.info(isAr ? 'تحت المراجعة' : 'Under review'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onReturn(it: NdiMaturityAssessment) {
    const reason = await dialog.prompt({
      title: isAr ? 'إعادة للتعديل' : 'Return for changes',
      label: isAr ? 'سبب الإرجاع' : 'Reason',
      type: 'textarea', required: true,
    });
    if (!reason) return;
    try { await returnMaturity(it.id!, reason); toast.info(isAr ? 'أُعيد للتعديل' : 'Returned'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onApprove(it: NdiMaturityAssessment) {
    const approverName = await dialog.prompt({
      title: isAr ? 'اعتماد البند' : 'Approve item',
      label: isAr ? 'اسم المعتمد' : 'Approver name',
      required: true,
    });
    if (!approverName) return;
    try { await approveMaturity(it.id!, approverName); toast.success(isAr ? 'مُعتمَد' : 'Approved'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function onReopen(it: NdiMaturityAssessment) {
    try { await reopenMaturity(it.id!); toast.info(isAr ? 'أُعيد الفتح' : 'Reopened'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onSaveEvidence(values: Record<string, unknown>) {
    if (!editing) return;
    try {
      await updateMaturity(editing.id!, {
        evidenceRef: String(values.evidenceRef ?? '').trim() || undefined,
        responseTextEn: String(values.responseTextEn ?? '').trim() || undefined,
        responseTextAr: String(values.responseTextAr ?? '').trim() || undefined,
        justification:  String(values.justification  ?? '').trim() || undefined,
      });
      toast.success(isAr ? 'تم الحفظ' : 'Saved');
      setEditing(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Save failed');
    }
  }

  const evidenceFields: FieldDef[] = [
    { kind: 'text',     key: 'evidenceRef',     span: 12,
      label: isAr ? 'مرجع الدليل' : 'Evidence reference',
      placeholder: 'e.g. Q1-2026-DG-policy.pdf' },
    { kind: 'textarea', key: 'responseTextEn',  rows: 3, span: 12,
      label: isAr ? 'الرد (إنجليزي)' : 'Response (English)' },
    { kind: 'textarea', key: 'responseTextAr',  rows: 3, span: 12,
      label: isAr ? 'الرد (عربي)' : 'Response (Arabic)' },
    { kind: 'textarea', key: 'justification',   rows: 3, span: 12,
      label: isAr ? 'مبرّر (إن كان الهدف < الحالي)' : 'Justification (if target < current)' },
  ];

  const groupedByDomain = useMemo(() => {
    const m = new Map<string, NdiMaturityAssessment[]>();
    for (const i of items) {
      if (!m.has(i.domain)) m.set(i.domain, []);
      m.get(i.domain)!.push(i);
    }
    return m;
  }, [items]);

  if (cycles.length === 0) {
    return <InlineEmpty>{isAr ? 'أنشئ دورة تقييم أولاً' : 'Create an assessment cycle first'}</InlineEmpty>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card title={isAr ? 'تقييم النضج' : 'Maturity assessment'} Icon={Target}
        action={
          <div style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
            <select
              value={cycleId ?? ''}
              onChange={e => setCycleId(Number(e.target.value))}
              style={{
                padding: '6px 10px', fontSize: 12, minWidth: 240,
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                background: 'var(--surface-1)', color: 'var(--ink-1)',
              }}>
              {cycles.map(c => (
                <option key={c.id} value={c.id}>
                  {isAr && c.cycleNameAr ? c.cycleNameAr : c.cycleNameEn} ({c.reportingYear})
                </option>
              ))}
            </select>
            <Button size="sm" variant="primary" Icon={Plus} onClick={onStart}>
              {isAr ? 'إنشاء بنود من الإطار' : 'Start from framework'}
            </Button>
          </div>
        }>
        {agg && (
          <div className="kpi-grid">
            <Kpi Icon={Target}       label={isAr ? 'نقاط النضج' : 'Maturity score'} value={`${Number(agg.maturityScorePct).toFixed(0)}%`}
                 tone={agg.maturityScorePct >= 60 ? 'success' : agg.maturityScorePct >= 40 ? 'warning' : 'danger'} />
            <Kpi Icon={FileText}     label={isAr ? 'إجمالي البنود' : 'Total items'}    value={agg.total}            tone="primary" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'بلا رد'        : 'Unresponded'}    value={agg.unresponded}      tone="warning" />
            <Kpi Icon={CheckCircle2} label={isAr ? 'مُعتمَد'        : 'Approved'}      value={agg.byStatus.APPROVED ?? 0}  tone="success" />
            <Kpi Icon={ArrowRight}   label={isAr ? 'تحسين مقترح'  : 'Improv. suggested'} value={agg.improvementSuggestedCount} tone="info" />
            <Kpi Icon={AlertCircle}  label={isAr ? 'مخالفة القاعدة 2' : 'Rule 2 violations'} value={agg.rule2ViolationCount} tone={agg.rule2ViolationCount > 0 ? 'danger' : 'success'} />
          </div>
        )}
      </Card>

      {loading
        ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        : items.length === 0
          ? <InlineEmpty>
              <div>
                {isAr ? 'لا توجد بنود نضج في هذه الدورة بعد.' : 'No maturity items in this cycle yet.'}
                <div style={{ marginTop: 8 }}>
                  <Button variant="primary" Icon={Plus} onClick={onStart}>
                    {isAr ? 'إنشاء من الإطار' : 'Generate from framework'}
                  </Button>
                </div>
              </div>
            </InlineEmpty>
          : Array.from(groupedByDomain.entries()).map(([domain, rows]) => {
              const domainPct = agg?.byDomainPct[domain];
              return (
                <Card key={domain}
                  title={domain}
                  Icon={Target}
                  action={domainPct != null
                    ? <Badge tone={Number(domainPct) >= 60 ? 'success' : Number(domainPct) >= 40 ? 'warning' : 'danger'}>
                        {Number(domainPct).toFixed(0)}%
                      </Badge>
                    : undefined}>
                  <table className="grc-table">
                    <thead>
                      <tr>
                        <th>{isAr ? 'البند' : 'Item'}</th>
                        <th style={{ width: 130 }}>{isAr ? 'المستوى الحالي' : 'Current level'}</th>
                        <th style={{ width: 130 }}>{isAr ? 'الهدف' : 'Target'}</th>
                        <th>{isAr ? 'الحالة' : 'Status'}</th>
                        <th>{isAr ? 'الدليل' : 'Evidence'}</th>
                        <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map(it => {
                        const isApproved = it.status === 'APPROVED';
                        const canSubmit = ['DRAFT', 'IN_PROGRESS', 'RETURNED'].includes(it.status);
                        return (
                          <tr key={it.id}>
                            <td>
                              <div className="text-2xs mono text-muted">{it.frameworkItemId}</div>
                              <div className="text-2xs">{it.subdomain ?? '—'}</div>
                              {it.improvementActionSuggested && (
                                <Badge tone="info">{isAr ? 'تحسين مقترح' : 'Improvement suggested'}</Badge>
                              )}
                            </td>
                            <td>
                              <select
                                value={it.currentLevel ?? ''}
                                disabled={isApproved}
                                onChange={e => onLevelChange(it, 'currentLevel', Number(e.target.value))}
                                style={{
                                  padding: '4px 6px', fontSize: 12, width: '100%',
                                  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                                  background: 'var(--surface-1)',
                                  borderLeft: `4px solid ${levelColor(it.currentLevel)}`,
                                }}>
                                <option value="">—</option>
                                {[0, 1, 2, 3, 4, 5].map(l => (
                                  <option key={l} value={l}>{l} · {levelLabel(l, isAr ? 'ar' : 'en')}</option>
                                ))}
                              </select>
                            </td>
                            <td>
                              <select
                                value={it.targetLevel ?? ''}
                                disabled={isApproved}
                                onChange={e => onLevelChange(it, 'targetLevel', Number(e.target.value))}
                                style={{
                                  padding: '4px 6px', fontSize: 12, width: '100%',
                                  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                                  background: 'var(--surface-1)',
                                  borderLeft: `4px solid ${levelColor(it.targetLevel)}`,
                                }}>
                                <option value="">—</option>
                                {[0, 1, 2, 3, 4, 5].map(l => (
                                  <option key={l} value={l}>{l} · {levelLabel(l, isAr ? 'ar' : 'en')}</option>
                                ))}
                              </select>
                            </td>
                            <td><Badge tone={maturityStatusTone(it.status)}>{maturityStatusLabel(it.status, isAr ? 'ar' : 'en')}</Badge></td>
                            <td className="text-xs">
                              {it.evidenceRef ?? <span className="text-muted">—</span>}
                              {it.currentLevel != null && it.currentLevel >= 4 && !it.evidenceRef && (
                                <div style={{ fontSize: 10, color: 'var(--danger-fg)', marginTop: 2 }}>
                                  {isAr ? 'القاعدة 2: مطلوب' : 'Rule 2: required'}
                                </div>
                              )}
                            </td>
                            <td style={{ textAlign: 'end' }}>
                              <div style={{ display: 'inline-flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                                {!isApproved && (
                                  <Button size="sm" variant="ghost" onClick={() => setEditing(it)}>
                                    {isAr ? 'تعديل' : 'Edit'}
                                  </Button>
                                )}
                                {canSubmit && (
                                  <Button size="sm" variant="secondary" Icon={ArrowRight} onClick={() => onSubmit(it)}>
                                    {isAr ? 'تقديم' : 'Submit'}
                                  </Button>
                                )}
                                {it.status === 'SUBMITTED' && (
                                  <Button size="sm" variant="secondary" onClick={() => onReview(it)}>
                                    {isAr ? 'مراجعة' : 'Review'}
                                  </Button>
                                )}
                                {it.status === 'UNDER_REVIEW' && (
                                  <>
                                    <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onApprove(it)}>
                                      {isAr ? 'اعتماد' : 'Approve'}
                                    </Button>
                                    <Button size="sm" variant="ghost" onClick={() => onReturn(it)}>
                                      {isAr ? 'إعادة' : 'Return'}
                                    </Button>
                                  </>
                                )}
                                {isApproved && (
                                  <Button size="sm" variant="ghost" onClick={() => onReopen(it)}>
                                    {isAr ? 'إعادة فتح' : 'Reopen'}
                                  </Button>
                                )}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </Card>
              );
            })}

      <FormModal
        open={editing !== null}
        title={isAr ? 'تعديل بند النضج' : 'Edit maturity item'}
        fields={evidenceFields}
        initial={{
          evidenceRef:    editing?.evidenceRef ?? '',
          responseTextEn: editing?.responseTextEn ?? '',
          responseTextAr: editing?.responseTextAr ?? '',
          justification:  editing?.justification ?? '',
        }}
        size="lg"
        submitLabel={isAr ? 'حفظ' : 'Save'}
        onClose={() => setEditing(null)}
        onSubmit={onSaveEvidence}
      />
    </div>
  );
}

// ─── Tabs that aren't shipped yet (slices 2-14) ──────────────────────

const PENDING_SLICE: Record<NdiTab, number | null> = {
  dashboard: 12, cycles: null, framework: null, maturity: null, compliance: 4,
  opex: 5, domains: 6, evidence: 7, gaps: 8, improvement: 9,
  submission: 10, reports: 11, settings: 13, import: 2, audit: 14,
};

function NotYetShipped({ tab, isAr, t }: { tab: NdiTab; isAr: boolean; t: (k: string) => string }) {
  void t;  // reserved for translation-key migration in slice 15
  const slice = PENDING_SLICE[tab];
  return (
    <Card title={isAr ? 'هذه الواجهة قيد التطوير' : 'Tab not shipped yet'} Icon={Wrench}>
      <div style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.6 }}>
        {isAr
          ? `هذه الواجهة جزء من شريحة لاحقة (الشريحة ${slice}) في خطة إدارة نضيء. الشريحة 1 (دورات التقييم) هي الأساس وقد شُحنت بالكامل في هذا الإصدار.`
          : `This tab is part of a later slice (slice ${slice}) in the NDI module rollout plan. Slice 1 (Assessment cycles) is the foundation and is fully shipped in this commit.`}
      </div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 8, padding: 10,
                    background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', lineHeight: 1.5 }}>
        <strong>{isAr ? 'ملاحظة' : 'Note'}:</strong>{' '}
        {isAr
          ? 'محتوى إطار نضيء الرسمي غير مستورَد بعد. كل ما يُعرَض هنا لاحقاً سيُصنَّف "عنصر مؤقت" حتى استيراد المكتبة الرسمية من سدايا.'
          : 'Official NDI framework content has not been imported yet. Anything rendered here will be flagged "Placeholder — requires official SDAIA/NDI control library import" until the official library lands.'}
      </div>
    </Card>
  );
}
