import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  GitBranch, AlertOctagon, AlertTriangle, CheckCircle2, FileQuestion,
  Plus, RefreshCw, Trash2, Network,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { ApiError, bcmApi, tprmApi } from '../../lib/api';
import {
  listAssessments, computeAssessment,
  listDependencies, createDependency, deleteDependency,
  type MtpdAssessment, type ProcessVendorDependency,
  type DependencyType, type DependencyStrength,
  DEPENDENCY_TYPE_LABELS, DEPENDENCY_STRENGTH_LABELS,
  dependencyTypeTone,
} from '../../lib/mtpdInheritance';

/**
 * Slice 10-E — Vendor MTPD Inheritance landing.
 *
 * <p>Two tabs:
 * <ul>
 *   <li><strong>Assessments</strong> — list past computations + button to
 *       compute a new cycle. Clicking a row opens the detail page.</li>
 *   <li><strong>Process Dependencies</strong> — CRUD for the
 *       {@code bcm.process_vendor_dependency} register that drives the
 *       compute engine.</li>
 * </ul>
 *
 * <p>Saudi-native bilingual: «وراثة المهلة القصوى للاضطراب من الموردين»
 * — NOT a literal translation of "MTPD inheritance from vendors".
 */
export function MtpdInheritancePage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const navigate = useNavigate();
  const toast = useToast();
  const dialog = useDialog();

  const [tab, setTab] = useState<'assessments' | 'dependencies'>('assessments');

  // ─── Assessments tab state ──────────────────────────────────────────
  const [assessments, setAssessments] = useState<MtpdAssessment[]>([]);
  const [loadingAssess, setLoadingAssess] = useState(false);
  const [computing, setComputing] = useState(false);

  const reloadAssessments = useCallback(async () => {
    setLoadingAssess(true);
    try {
      const rows = await listAssessments();
      setAssessments(Array.isArray(rows) ? rows : []);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل تقييمات وراثة المهلة القصوى' : 'Failed to load assessments'));
    } finally {
      setLoadingAssess(false);
    }
  }, [isAr, toast]);

  // ─── Dependencies tab state ─────────────────────────────────────────
  const [dependencies, setDependencies] = useState<ProcessVendorDependency[]>([]);
  const [loadingDeps, setLoadingDeps] = useState(false);

  const reloadDependencies = useCallback(async () => {
    setLoadingDeps(true);
    try {
      const rows = await listDependencies();
      setDependencies(Array.isArray(rows) ? rows : []);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل اعتمادات العمليات على الموردين' : 'Failed to load dependencies'));
    } finally {
      setLoadingDeps(false);
    }
  }, [isAr, toast]);

  useEffect(() => {
    if (tab === 'assessments') void reloadAssessments();
    else void reloadDependencies();
  }, [tab, reloadAssessments, reloadDependencies]);

  // ─── Compute new assessment ─────────────────────────────────────────
  async function handleCompute() {
    const yearStr = await dialog.prompt({
      title: isAr ? 'سنة دورة التقييم' : 'Cycle year',
      placeholder: String(new Date().getFullYear()),
      initial: String(new Date().getFullYear()),
      required: true,
    });
    if (yearStr === null) return;
    const year = Number(yearStr);
    if (!Number.isFinite(year)) {
      toast.danger(isAr ? 'سنة غير صالحة' : 'Invalid year');
      return;
    }
    const quarter = await dialog.prompt({
      title: isAr ? 'الربع (اختياري)' : 'Quarter (optional)',
      placeholder: 'Q1 / Q2 / Q3 / Q4',
    });
    if (quarter === null) return;

    setComputing(true);
    try {
      const result = await computeAssessment({
        cycleYear: year,
        cycleQuarter: quarter && quarter.trim() ? quarter.trim() : undefined,
      });
      toast.success(isAr ? `تم حساب التقييم ${result.assessmentCode}`
                         : `Computed assessment ${result.assessmentCode}`);
      // Navigate straight to detail.
      navigate(`/bcm/mtpd-inheritance/${result.id}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل حساب التقييم' : 'Compute failed'));
    } finally {
      setComputing(false);
    }
  }

  // ─── Dependency: add ────────────────────────────────────────────────
  async function handleAddDependency() {
    // Fetch processes + vendors as lookup pools.
    const [processesPage, vendorsPage] = await Promise.all([
      bcmApi.processes(0, 200),
      tprmApi.vendors(0, 500),
    ]);
    const processes = processesPage.content ?? [];
    const vendors = vendorsPage.content ?? [];
    if (processes.length === 0) {
      toast.warning(isAr ? 'سجل العمليات الحرجة فارغ' : 'No critical processes on file');
      return;
    }
    if (vendors.length === 0) {
      toast.warning(isAr ? 'سجل الموردين فارغ' : 'No vendors on file');
      return;
    }
    const processList = processes
      .map(p => `${p.id} — ${p.processNameEn}${p.processNameAr ? ' / ' + p.processNameAr : ''}`)
      .join('\n');
    const procStr = await dialog.prompt({
      title: isAr ? 'معرّف العملية' : 'Business process ID',
      helpText: isAr ? `العمليات المتاحة:\n${processList}` : `Available processes:\n${processList}`,
      type: 'textarea',
      required: true,
    });
    if (procStr === null) return;
    const procId = Number(procStr.trim().split(/\s|—/)[0]);
    if (!Number.isFinite(procId)) {
      toast.danger(isAr ? 'معرّف عملية غير صالح' : 'Invalid process ID');
      return;
    }
    const vendorList = vendors
      .map(v => `${v.id} — ${v.vendorName}${v.vendorNameAr ? ' / ' + v.vendorNameAr : ''}`)
      .join('\n');
    const vendStr = await dialog.prompt({
      title: isAr ? 'معرّف المورّد' : 'Vendor ID',
      helpText: isAr ? `الموردون المتاحون:\n${vendorList}` : `Available vendors:\n${vendorList}`,
      type: 'textarea',
      required: true,
    });
    if (vendStr === null) return;
    const vendId = Number(vendStr.trim().split(/\s|—/)[0]);
    if (!Number.isFinite(vendId)) {
      toast.danger(isAr ? 'معرّف مورد غير صالح' : 'Invalid vendor ID');
      return;
    }
    const depType = await dialog.prompt({
      title: isAr ? 'نوع الاعتماد' : 'Dependency type',
      helpText: 'REQUIRED / RECOMMENDED / FALLBACK',
      initial: 'REQUIRED',
      required: true,
    });
    if (depType === null) return;
    const depStrength = await dialog.prompt({
      title: isAr ? 'قوة الاعتماد' : 'Dependency strength',
      helpText: 'TIGHT / LOOSE / TRANSIENT',
      initial: 'TIGHT',
      required: true,
    });
    if (depStrength === null) return;

    try {
      await createDependency({
        businessProcessId: procId,
        vendorId: vendId,
        dependencyType: depType as DependencyType,
        dependencyStrength: depStrength as DependencyStrength,
      });
      toast.success(isAr ? 'تم تسجيل الاعتماد' : 'Dependency saved');
      await reloadDependencies();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل التسجيل' : 'Save failed'));
    }
  }

  async function handleDeleteDependency(d: ProcessVendorDependency) {
    const ok = await dialog.confirm({
      title: isAr ? 'حذف هذا الاعتماد؟' : 'Delete this dependency?',
      body: isAr
        ? 'لن يُحتسب ضمن دورة وراثة المهلة التالية.'
        : 'It will no longer drive future MTPD-inheritance compute cycles.',
      destructive: true,
    });
    if (!ok) return;
    try {
      await deleteDependency(d.id);
      toast.success(isAr ? 'تم الحذف' : 'Deleted');
      await reloadDependencies();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  // ─── KPIs across assessments ────────────────────────────────────────
  const totalBreaches = assessments.reduce((a, b) => a + (b.totalBreaches ?? 0), 0);
  const totalWarnings = assessments.reduce((a, b) => a + (b.totalWarnings ?? 0), 0);
  const totalNoAtt = assessments.reduce((a, b) => a + (b.totalNoAttestation ?? 0), 0);
  const publishedCount = assessments.filter(a => a.isPublished).length;

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'وراثة المهلة القصوى للاضطراب من الموردين' : 'Vendor MTPD Inheritance'}
        sub={isAr
          ? 'تحقق آلي من أن المهلة المُقَرّة من كل مورد جوهري لا تتجاوز مهلة العملية المعتمدة عليه — وفق إطار استمرارية الأعمال (مؤسسة النقد §3.4) والمواصفة ISO 22301 §8.2.'
          : 'Live check that each critical vendor\'s attested MTPD does not exceed the bank\'s own MTPD for any dependent process — per SAMA BCF §3.4 and ISO 22301 §8.2.'}
      />

      {/* Tabs */}
      <div className="cluster" style={{ gap: 8, marginBottom: 'var(--sp-6)' }}>
        <Button
          variant={tab === 'assessments' ? 'primary' : 'secondary'}
          Icon={AlertOctagon}
          onClick={() => setTab('assessments')}>
          {isAr ? 'التقييمات' : 'Assessments'}
        </Button>
        <Button
          variant={tab === 'dependencies' ? 'primary' : 'secondary'}
          Icon={Network}
          onClick={() => setTab('dependencies')}>
          {isAr ? 'اعتمادات العمليات على الموردين' : 'Process Dependencies'}
        </Button>
      </div>

      {tab === 'assessments' && (
        <>
          <div className="kpi-grid-sm" style={{ marginBottom: 'var(--sp-6)' }}>
            <Kpi
              Icon={GitBranch}
              label={isAr ? 'إجمالي التقييمات' : 'Total assessments'}
              value={String(assessments.length)}
            />
            <Kpi
              Icon={AlertOctagon}
              label={isAr ? 'مجموع الخروقات' : 'Cumulative breaches'}
              value={String(totalBreaches)}
              tone="danger"
            />
            <Kpi
              Icon={AlertTriangle}
              label={isAr ? 'مجموع التحذيرات' : 'Cumulative warnings'}
              value={String(totalWarnings)}
              tone="warning"
            />
            <Kpi
              Icon={FileQuestion}
              label={isAr ? 'فجوات إقرار' : 'Attestation gaps'}
              value={String(totalNoAtt)}
              tone="neutral"
            />
            <Kpi
              Icon={CheckCircle2}
              label={isAr ? 'مَنشورة للمراجعة' : 'Published for audit'}
              value={String(publishedCount)}
              tone="info"
            />
          </div>

          <Card
            title={isAr ? 'دورات التقييم السابقة' : 'Past assessment cycles'}
            Icon={AlertOctagon}
            action={
              <div className="cluster">
                <Button variant="secondary" Icon={RefreshCw}
                        onClick={() => void reloadAssessments()}>
                  {isAr ? 'تحديث' : 'Refresh'}
                </Button>
                <Button variant="primary" Icon={Plus}
                        disabled={computing}
                        onClick={() => void handleCompute()}>
                  {computing ? (isAr ? 'جارٍ الحساب…' : 'Computing…')
                             : (isAr ? 'حساب التقييم' : 'Compute new')}
                </Button>
              </div>
            }
          >
            {loadingAssess && assessments.length === 0 && (
              <p style={{ color: 'var(--ink-3)' }}>
                {isAr ? 'جارٍ التحميل…' : 'Loading…'}
              </p>
            )}
            {!loadingAssess && assessments.length === 0 && (
              <InlineEmpty
                text={isAr
                  ? 'لا توجد تقييمات بعد. انقر «حساب التقييم» لإطلاق أول دورة (تستلزم اعتمادات عمليات-موردين، وإقرارات BCP مُعتمَدة).'
                  : 'No assessments computed yet. Click "Compute new" to launch the first cycle (requires process-vendor dependencies and accepted BCP attestations).'}
              />
            )}
            {assessments.length > 0 && (
              <div className="table-responsive">
                <table className="table">
                  <thead>
                    <tr>
                      <th>{isAr ? 'الرمز' : 'Code'}</th>
                      <th>{isAr ? 'الدورة' : 'Cycle'}</th>
                      <th>{isAr ? 'العمليات' : 'Processes'}</th>
                      <th>{isAr ? 'خروقات' : 'Breaches'}</th>
                      <th>{isAr ? 'تحذيرات' : 'Warnings'}</th>
                      <th>{isAr ? 'فجوات' : 'Gaps'}</th>
                      <th>{isAr ? 'الحالة' : 'Status'}</th>
                      <th>{isAr ? 'حُسب في' : 'Computed at'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {assessments.map(a => (
                      <tr key={a.id}
                          style={{ cursor: 'pointer' }}
                          onClick={() => navigate(`/bcm/mtpd-inheritance/${a.id}`)}>
                        <td><strong>{a.assessmentCode}</strong></td>
                        <td>{a.cycleYear}{a.cycleQuarter ? ` / ${a.cycleQuarter}` : ''}</td>
                        <td>{a.totalProcesses}</td>
                        <td>{a.totalBreaches > 0
                          ? <Badge tone="danger">{a.totalBreaches}</Badge>
                          : a.totalBreaches}</td>
                        <td>{a.totalWarnings > 0
                          ? <Badge tone="warning">{a.totalWarnings}</Badge>
                          : a.totalWarnings}</td>
                        <td>{a.totalNoAttestation > 0
                          ? <Badge tone="neutral">{a.totalNoAttestation}</Badge>
                          : a.totalNoAttestation}</td>
                        <td>
                          {a.isPublished
                            ? <Badge tone="info">{isAr ? 'مَنشورة' : 'Published'}</Badge>
                            : <Badge tone="neutral">{isAr ? 'مسوّدة' : 'Draft'}</Badge>}
                        </td>
                        <td>{a.computedAt ? new Date(a.computedAt).toLocaleString(isAr ? 'ar-SA' : 'en-GB') : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      )}

      {tab === 'dependencies' && (
        <Card
          title={isAr ? 'اعتمادات العمليات على الموردين' : 'Process ↔ Vendor dependencies'}
          Icon={Network}
          action={
            <div className="cluster">
              <Button variant="secondary" Icon={RefreshCw}
                      onClick={() => void reloadDependencies()}>
                {isAr ? 'تحديث' : 'Refresh'}
              </Button>
              <Button variant="primary" Icon={Plus}
                      onClick={() => void handleAddDependency()}>
                {isAr ? 'إضافة اعتماد' : 'Add dependency'}
              </Button>
            </div>
          }
        >
          {loadingDeps && dependencies.length === 0 && (
            <p style={{ color: 'var(--ink-3)' }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</p>
          )}
          {!loadingDeps && dependencies.length === 0 && (
            <InlineEmpty
              text={isAr
                ? 'لا توجد اعتمادات مُسجَّلة. سجِّل اعتمادات العمليات الحرجة على الموردين الجوهريين (مدى، سريع، توكلنا، خدمات KMS سحابية…) قبل إطلاق دورة وراثة المهلة.'
                : 'No dependencies on file. Register critical-process dependencies on the material vendors (Mada, SARIE, Tawakkalna, cloud KMS, etc.) before running an MTPD inheritance cycle.'}
            />
          )}
          {dependencies.length > 0 && (
            <div className="table-responsive">
              <table className="table">
                <thead>
                  <tr>
                    <th>{isAr ? 'العملية' : 'Process'}</th>
                    <th>{isAr ? 'المورّد' : 'Vendor'}</th>
                    <th>{isAr ? 'النوع' : 'Type'}</th>
                    <th>{isAr ? 'القوة' : 'Strength'}</th>
                    <th>{isAr ? 'إجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {dependencies.map(d => (
                    <tr key={d.id}>
                      <td>
                        <strong>
                          {isAr && d.businessProcessNameAr ? d.businessProcessNameAr
                            : (d.businessProcessNameEn ?? `#${d.businessProcessId}`)}
                        </strong>
                      </td>
                      <td>
                        {isAr && d.vendorNameAr ? d.vendorNameAr
                          : (d.vendorName ?? `#${d.vendorId}`)}
                      </td>
                      <td>
                        <Badge tone={dependencyTypeTone(d.dependencyType)}>
                          {tlabel(DEPENDENCY_TYPE_LABELS, d.dependencyType, isAr)}
                        </Badge>
                      </td>
                      <td>
                        {tlabel(DEPENDENCY_STRENGTH_LABELS, d.dependencyStrength, isAr)}
                      </td>
                      <td>
                        <Button size="sm" variant="ghost" Icon={Trash2}
                                onClick={() => void handleDeleteDependency(d)}>
                          {isAr ? 'حذف' : 'Delete'}
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}
    </Shell>
  );
}
