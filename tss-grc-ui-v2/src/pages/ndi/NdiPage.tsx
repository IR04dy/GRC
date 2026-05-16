import { useEffect, useMemo, useState } from 'react';
import {
  Database, FileText, Target, Wrench, AlertCircle, CheckCircle2,
  Plus, ArrowRight, Trash2, Copy, Archive as ArchiveIcon,
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

      {tab === 'cycles' && <CyclesTab />}
      {tab !== 'cycles' && <NotYetShipped tab={tab} isAr={isAr} t={t} />}
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

// ─── Tabs that aren't shipped yet (slices 2-14) ──────────────────────

const PENDING_SLICE: Record<NdiTab, number | null> = {
  dashboard: 12, cycles: null, framework: 2, maturity: 3, compliance: 4,
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
