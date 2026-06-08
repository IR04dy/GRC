import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, FileText, ShieldCheck, AlertTriangle, LogOut, History, RefreshCw, Plus, ClipboardCheck, Pencil } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { EvidenceReadinessPanel } from '../../components/ui/EvidenceReadinessPanel';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import { getUser } from '../../lib/auth';
import {
  getArrangement,
  updateArrangement,
  listDueDiligence,
  listEvents,
  getExitPlan,
  createExitPlan,
  recordExitTest,
  recordReview,
  recordEvent,
  recordDueDiligence,
  type OutsourcingArrangement,
  type DueDiligence,
  type MonitoringEvent,
  type ExitPlan,
  type EventType,
  type Severity,
  type Recommendation,
  type TestFrequency,
  type ServiceType,
  type CriticalFunction,
  LIFECYCLE_LABELS,
  SERVICE_TYPE_LABELS,
  CRITICAL_FUNCTION_LABELS,
  EVENT_TYPE_LABELS,
  SEVERITY_LABELS,
  TEST_FREQUENCY_LABELS,
  lifecycleTone,
  severityTone,
  recommendationTone,
  fmtSar,
} from '../../lib/outsourcing';

type TabId = 'overview' | 'dd' | 'events' | 'exit' | 'activity';

// Shared token-driven styles (the page previously used non-existent Tailwind
// utility classes — these render the intended layout with design tokens).
const row: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8 };
const rowStart: CSSProperties = { display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 };
const stack: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12, marginTop: 16 };
const overviewGrid: CSSProperties = { display: 'grid', gap: 16, marginTop: 16, gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))' };
const fullSpan: CSSProperties = { gridColumn: '1 / -1' };
const muted: CSSProperties = { color: 'var(--ink-3)' };

const EVENT_TYPES: EventType[] = [
  'KPI_REVIEW', 'SLA_BREACH', 'INCIDENT', 'AUDIT_REPORT', 'CONTRACT_AMENDMENT',
  'FINANCIAL_HEALTH_FLAG', 'REGULATORY_QUERY', 'CHANGE_OF_CONTROL', 'RFP_RECOMPETE', 'EXIT_TRIGGER',
];
const SEVERITIES: Severity[] = ['INFO', 'MINOR', 'MODERATE', 'MAJOR', 'CRITICAL'];

const RECOMMENDATIONS: Recommendation[] = ['APPROVE', 'CONDITIONAL', 'REJECT'];
const RECOMMENDATION_LABELS: Record<Recommendation, { en: string; ar: string }> = {
  APPROVE:     { en: 'Approve',                ar: 'موافقة' },
  CONDITIONAL: { en: 'Conditional approval',   ar: 'موافقة مشروطة' },
  REJECT:      { en: 'Reject',                 ar: 'رفض' },
};
// Six due-diligence sub-score fields (each 0-100). Composite is server-computed.
const DD_SCORES: Array<{ key: string; en: string; ar: string }> = [
  { key: 'financialHealthScore',       en: 'Financial health',        ar: 'الوضع المالي' },
  { key: 'operationalCapabilityScore', en: 'Operational capability',  ar: 'القدرة التشغيلية' },
  { key: 'cybersecurityScore',         en: 'Cybersecurity',           ar: 'الأمن السيبراني' },
  { key: 'bcmScore',                   en: 'BCM resilience',          ar: 'استمرارية الأعمال' },
  { key: 'pdplComplianceScore',        en: 'PDPL compliance',         ar: 'الامتثال لنظام حماية البيانات' },
  { key: 'reputationScore',            en: 'Reputation',              ar: 'السمعة' },
];

// Exit-plan test cadence (SAMA outsourcing exit-strategy testing). Ordered most
// common first; NEVER is a board-accepted no-test posture, NEVER_TESTED a backlog.
const TEST_FREQUENCIES: TestFrequency[] = ['ANNUAL', 'BIANNUAL', 'NEVER_TESTED', 'NEVER'];

export function OutsourcingDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const arrangementId = Number(id);
  const [arrangement, setArrangement] = useState<OutsourcingArrangement | null>(null);
  const [dd, setDd] = useState<DueDiligence[]>([]);
  const [events, setEvents] = useState<MonitoringEvent[]>([]);
  const [exitPlan, setExitPlan] = useState<ExitPlan | null>(null);
  const [tab, setTab] = useState<TabId>('overview');
  const [loading, setLoading] = useState(false);
  const [openEvent, setOpenEvent] = useState(false);
  const [openDd, setOpenDd] = useState(false);
  const [openExit, setOpenExit] = useState(false);
  const [testingExit, setTestingExit] = useState(false);
  const [editOpen, setEditOpen] = useState(false);

  async function reload() {
    if (!Number.isFinite(arrangementId)) return;
    setLoading(true);
    try {
      const [a, ddList, evList] = await Promise.all([
        getArrangement(arrangementId),
        listDueDiligence(arrangementId).catch(() => []),
        listEvents(arrangementId).catch(() => []),
      ]);
      setArrangement(a);
      setDd(ddList);
      setEvents(evList);
      try {
        const ep = await getExitPlan(arrangementId);
        setExitPlan(ep);
      } catch { setExitPlan(null); }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل الترتيب' : 'Failed to load arrangement'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, [arrangementId]); // eslint-disable-line react-hooks/exhaustive-deps

  async function onRecordReview() {
    try {
      const updated = await recordReview(arrangementId);
      setArrangement(updated);
      toast.success(isAr ? 'تمّ تسجيل المراجعة الدورية' : 'Periodic review recorded');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تسجيل المراجعة' : 'Failed to record review'));
    }
  }

  // ── Edit the arrangement record (PATCH — partial null-means-keep) ──────
  // Focused on the routinely-corrected descriptive + contract fields. The
  // lifecycle stage moves via transitions, and materiality/SAMA flags have
  // their own downstream-triggering flows, so they're left out of this quick
  // edit. Server takes a partial DTO, so omitted fields keep their values.
  const serviceTypeOptions = useMemo(
    () => Object.entries(SERVICE_TYPE_LABELS).map(([value, l]) => ({ value, label: isAr ? l.ar : l.en })), [isAr]);
  const criticalFnOptions = useMemo(
    () => Object.entries(CRITICAL_FUNCTION_LABELS).map(([value, l]) => ({ value, label: isAr ? l.ar : l.en })), [isAr]);
  const editFields: FieldDef[] = useMemo(() => [
    { key: 'titleEn', kind: 'text', required: true, span: 6, label: isAr ? 'العنوان (إنجليزي)' : 'Title (EN)' },
    { key: 'titleAr', kind: 'text', required: true, span: 6, label: isAr ? 'العنوان (عربي)' : 'Title (AR)' },
    { key: 'descriptionEn', kind: 'textarea', span: 12, rows: 2, label: isAr ? 'الوصف (إنجليزي)' : 'Description (EN)' },
    { key: 'serviceType', kind: 'select', required: true, span: 6, options: serviceTypeOptions, label: isAr ? 'نوع الخدمة' : 'Service type' },
    { key: 'criticalFunction', kind: 'select', required: true, span: 6, options: criticalFnOptions, label: isAr ? 'الوظيفة الحرجة' : 'Critical function' },
    { key: 'contractValueSarAnnual', kind: 'number', span: 4, min: 0, label: isAr ? 'القيمة السنوية (ريال)' : 'Annual value (SAR)' },
    { key: 'contractEndDate', kind: 'date', span: 4, label: isAr ? 'نهاية العقد' : 'Contract end' },
    { key: 'nextReviewDueDate', kind: 'date', span: 4, label: isAr ? 'المراجعة القادمة' : 'Next review due' },
  ], [isAr, serviceTypeOptions, criticalFnOptions]);
  const editInitial = useMemo(() => arrangement ? {
    titleEn: arrangement.titleEn,
    titleAr: arrangement.titleAr,
    descriptionEn: arrangement.descriptionEn ?? '',
    serviceType: arrangement.serviceType,
    criticalFunction: arrangement.criticalFunction,
    contractValueSarAnnual: arrangement.contractValueSarAnnual ?? '',
    contractEndDate: arrangement.contractEndDate ? arrangement.contractEndDate.slice(0, 10) : '',
    nextReviewDueDate: arrangement.nextReviewDueDate ? arrangement.nextReviewDueDate.slice(0, 10) : '',
  } : {}, [arrangement]);
  async function onUpdate(values: Record<string, unknown>) {
    try {
      const updated = await updateArrangement(arrangementId, {
        titleEn: String(values.titleEn ?? '').trim(),
        titleAr: String(values.titleAr ?? '').trim(),
        descriptionEn: String(values.descriptionEn ?? '').trim() || undefined,
        serviceType: values.serviceType as ServiceType,
        criticalFunction: values.criticalFunction as CriticalFunction,
        contractValueSarAnnual: typeof values.contractValueSarAnnual === 'number' && !Number.isNaN(values.contractValueSarAnnual)
          ? values.contractValueSarAnnual : undefined,
        contractEndDate: String(values.contractEndDate ?? '').trim() || undefined,
        nextReviewDueDate: String(values.nextReviewDueDate ?? '').trim() || undefined,
      });
      setArrangement(updated);
      setEditOpen(false);
      toast.success(isAr ? 'تم تحديث الترتيب' : 'Arrangement updated');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل تحديث الترتيب' : 'Failed to update arrangement'));
    }
  }

  // ── Record a monitoring event (continuous-oversight log) ──────────────
  const today = useMemo(() => new Date().toISOString().slice(0, 10), []);
  const eventInitial: Record<string, string> = useMemo(() => ({
    eventType: 'KPI_REVIEW', eventDate: today, severity: 'INFO',
    descriptionEn: '', descriptionAr: '', actionTakenEn: '', actionTakenAr: '',
  }), [today]);

  const eventFields: FieldDef[] = useMemo(() => [
    { key: 'eventType', kind: 'select', required: true, span: 6,
      label: isAr ? 'نوع الحدث' : 'Event type',
      options: EVENT_TYPES.map(t => ({ value: t, label: EVENT_TYPE_LABELS[t][isAr ? 'ar' : 'en'] })) },
    { key: 'eventDate', kind: 'date', span: 3, label: isAr ? 'تاريخ الحدث' : 'Event date' },
    { key: 'severity', kind: 'select', span: 3,
      label: isAr ? 'الخطورة' : 'Severity',
      options: SEVERITIES.map(s => ({ value: s, label: SEVERITY_LABELS[s][isAr ? 'ar' : 'en'] })) },
    { key: 'descriptionEn', kind: 'textarea', span: 12, rows: 3,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (EN)' },
    { key: 'descriptionAr', kind: 'textarea', span: 12, rows: 3,
      label: isAr ? 'الوصف (عربي)' : 'Description (AR)' },
    { key: 'actionTakenEn', kind: 'textarea', span: 12, rows: 2,
      label: isAr ? 'الإجراء المُتَّخذ (إنجليزي)' : 'Action taken (EN)' },
    { key: 'actionTakenAr', kind: 'textarea', span: 12, rows: 2,
      label: isAr ? 'الإجراء المُتَّخذ (عربي)' : 'Action taken (AR)' },
  ], [isAr]);

  async function submitEvent(v: Record<string, string>) {
    const eventType = (v.eventType ?? '') as EventType;
    const eventDate = String(v.eventDate ?? '').trim();
    const descriptionEn = String(v.descriptionEn ?? '').trim();
    const descriptionAr = String(v.descriptionAr ?? '').trim();
    if (!eventType) { toast.danger(isAr ? 'نوع الحدث مطلوب' : 'Event type is required'); return; }
    if (!eventDate) { toast.danger(isAr ? 'تاريخ الحدث مطلوب' : 'Event date is required'); return; }
    if (!descriptionEn) { toast.danger(isAr ? 'الوصف بالإنجليزية مطلوب' : 'Description (EN) is required'); return; }
    if (!descriptionAr) { toast.danger(isAr ? 'الوصف بالعربية مطلوب' : 'Description (AR) is required'); return; }
    const opt = (s?: string) => { const x = String(s ?? '').trim(); return x ? x : undefined; };
    try {
      await recordEvent({
        arrangementId,
        eventType,
        eventDate,
        severity: ((v.severity as Severity) || 'INFO'),
        descriptionEn,
        descriptionAr,
        actionTakenEn: opt(v.actionTakenEn),
        actionTakenAr: opt(v.actionTakenAr),
        resolved: false,
      });
      toast.success(isAr ? 'تمّ تسجيل حدث المراقبة' : 'Monitoring event recorded');
      setOpenEvent(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تسجيل الحدث' : 'Failed to record event'));
    }
  }

  // ── Record a due-diligence assessment (six sub-scores -> composite) ────
  const ddInitial: Record<string, string> = useMemo(() => ({
    assessmentDate: today,
    financialHealthScore: '80', operationalCapabilityScore: '80', cybersecurityScore: '80',
    bcmScore: '80', pdplComplianceScore: '80', reputationScore: '80',
    recommendation: 'APPROVE', rationaleEn: '', rationaleAr: '',
  }), [today]);

  const ddFields: FieldDef[] = useMemo(() => [
    { key: 'assessmentDate', kind: 'date', span: 6, label: isAr ? 'تاريخ التقييم' : 'Assessment date' },
    { key: 'recommendation', kind: 'select', required: true, span: 6,
      label: isAr ? 'التوصية' : 'Recommendation',
      options: RECOMMENDATIONS.map(r => ({ value: r, label: RECOMMENDATION_LABELS[r][isAr ? 'ar' : 'en'] })) },
    ...DD_SCORES.map((s): FieldDef => ({
      key: s.key, kind: 'number', required: true, span: 4, min: 0, max: 100,
      label: isAr ? s.ar : s.en,
    })),
    { key: 'rationaleEn', kind: 'textarea', span: 12, rows: 3, label: isAr ? 'المبرّر (إنجليزي)' : 'Rationale (EN)' },
    { key: 'rationaleAr', kind: 'textarea', span: 12, rows: 3, label: isAr ? 'المبرّر (عربي)' : 'Rationale (AR)' },
  ], [isAr]);

  async function submitDd(v: Record<string, string>) {
    const assessmentDate = String(v.assessmentDate ?? '').trim();
    const recommendation = (v.recommendation ?? '') as Recommendation;
    const rationaleEn = String(v.rationaleEn ?? '').trim();
    const rationaleAr = String(v.rationaleAr ?? '').trim();
    const assessorUserId = getUser()?.userId;
    if (!assessmentDate) { toast.danger(isAr ? 'تاريخ التقييم مطلوب' : 'Assessment date is required'); return; }
    if (!recommendation) { toast.danger(isAr ? 'التوصية مطلوبة' : 'Recommendation is required'); return; }
    if (!rationaleEn) { toast.danger(isAr ? 'المبرّر بالإنجليزية مطلوب' : 'Rationale (EN) is required'); return; }
    if (!rationaleAr) { toast.danger(isAr ? 'المبرّر بالعربية مطلوب' : 'Rationale (AR) is required'); return; }
    if (assessorUserId == null) { toast.danger(isAr ? 'تعذّر تحديد المُقيِّم' : 'Could not resolve the assessor'); return; }
    // FormModal number-kind fields hold runtime numbers; coerce + bounds-check.
    const scores: Record<string, number> = {};
    for (const s of DD_SCORES) {
      const n = Number(v[s.key]);
      if (!Number.isFinite(n) || n < 0 || n > 100) {
        toast.danger(isAr ? `قيمة غير صحيحة: ${s.ar} (0-100)` : `Invalid score: ${s.en} (0-100)`);
        return;
      }
      scores[s.key] = Math.round(n);
    }
    try {
      await recordDueDiligence({
        arrangementId,
        assessmentDate,
        assessorUserId,
        financialHealthScore: scores.financialHealthScore,
        operationalCapabilityScore: scores.operationalCapabilityScore,
        cybersecurityScore: scores.cybersecurityScore,
        bcmScore: scores.bcmScore,
        pdplComplianceScore: scores.pdplComplianceScore,
        reputationScore: scores.reputationScore,
        recommendation,
        rationaleEn,
        rationaleAr,
      });
      toast.success(isAr ? 'تمّ تسجيل تقييم العناية الواجبة' : 'Due diligence recorded');
      setOpenDd(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تسجيل العناية الواجبة' : 'Failed to record due diligence'));
    }
  }

  // ── Create the exit plan (SAMA stressed-exit strategy) ─────────────────
  const exitInitial: Record<string, string> = useMemo(() => ({
    triggerConditionsEn: '', triggerConditionsAr: '',
    testFrequency: 'ANNUAL',
    dataRepatriationPlanEn: '', dataRepatriationPlanAr: '',
    lastTestedDate: '',
  }), []);

  const exitFields: FieldDef[] = useMemo(() => [
    { key: 'triggerConditionsEn', kind: 'textarea', span: 12, rows: 3, maxLength: 2000,
      label: isAr ? 'شروط التفعيل (إنجليزي)' : 'Trigger conditions (EN)' },
    { key: 'triggerConditionsAr', kind: 'textarea', span: 12, rows: 3, maxLength: 2000,
      label: isAr ? 'شروط التفعيل (عربي)' : 'Trigger conditions (AR)' },
    { key: 'testFrequency', kind: 'select', required: true, span: 6,
      label: isAr ? 'تواتر الاختبار' : 'Test frequency',
      options: TEST_FREQUENCIES.map(f => ({ value: f, label: TEST_FREQUENCY_LABELS[f][isAr ? 'ar' : 'en'] })) },
    { key: 'lastTestedDate', kind: 'date', span: 6, label: isAr ? 'تاريخ آخر اختبار (اختياري)' : 'Last tested date (optional)' },
    { key: 'dataRepatriationPlanEn', kind: 'textarea', span: 12, rows: 3, maxLength: 2000,
      label: isAr ? 'خطة استرداد البيانات (إنجليزي)' : 'Data repatriation plan (EN)' },
    { key: 'dataRepatriationPlanAr', kind: 'textarea', span: 12, rows: 3, maxLength: 2000,
      label: isAr ? 'خطة استرداد البيانات (عربي)' : 'Data repatriation plan (AR)' },
  ], [isAr]);

  async function submitExit(v: Record<string, string>) {
    const triggerConditionsEn = String(v.triggerConditionsEn ?? '').trim();
    const triggerConditionsAr = String(v.triggerConditionsAr ?? '').trim();
    const testFrequency = (v.testFrequency ?? '') as TestFrequency;
    const lastTestedDate = String(v.lastTestedDate ?? '').trim();
    if (!triggerConditionsEn) { toast.danger(isAr ? 'شروط التفعيل بالإنجليزية مطلوبة' : 'Trigger conditions (EN) are required'); return; }
    if (!triggerConditionsAr) { toast.danger(isAr ? 'شروط التفعيل بالعربية مطلوبة' : 'Trigger conditions (AR) are required'); return; }
    if (!testFrequency) { toast.danger(isAr ? 'تواتر الاختبار مطلوب' : 'Test frequency is required'); return; }
    const opt = (s?: string) => { const x = String(s ?? '').trim(); return x ? x : undefined; };
    try {
      await createExitPlan({
        arrangementId,
        triggerConditionsEn,
        triggerConditionsAr,
        testFrequency,
        dataRepatriationPlanEn: opt(v.dataRepatriationPlanEn),
        dataRepatriationPlanAr: opt(v.dataRepatriationPlanAr),
        lastTestedDate: lastTestedDate || undefined,
      });
      toast.success(isAr ? 'تمّ إنشاء خطة الخروج' : 'Exit plan created');
      setOpenExit(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل إنشاء خطة الخروج' : 'Failed to create exit plan'));
    }
  }

  async function onRecordExitTest() {
    if (!exitPlan) return;
    setTestingExit(true);
    try {
      const updated = await recordExitTest(exitPlan.id, today);
      setExitPlan(updated);
      toast.success(isAr ? 'تمّ تسجيل اختبار خطة الخروج' : 'Exit-plan test recorded');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تسجيل اختبار الخروج' : 'Failed to record exit test'));
    } finally {
      setTestingExit(false);
    }
  }

  const tabs: TabItem<TabId>[] = useMemo(() => [
    { id: 'overview', label: isAr ? 'نظرة عامة' : 'Overview' },
    { id: 'dd',       label: isAr ? 'العناية الواجبة' : 'Due Diligence', count: dd.length },
    { id: 'events',   label: isAr ? 'المراقبة المستمرّة' : 'Monitoring', count: events.length },
    { id: 'exit',     label: isAr ? 'خطة الخروج' : 'Exit Plan' },
    { id: 'activity', label: isAr ? 'النشاط' : 'Activity' },
  ], [isAr, dd.length, events.length]);

  if (!Number.isFinite(arrangementId)) {
    return <Shell><div style={{ padding: 24 }}>{isAr ? 'معرّف غير صحيح' : 'Invalid id'}</div></Shell>;
  }

  if (loading && !arrangement) {
    return <Shell><div style={{ padding: 24 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div></Shell>;
  }

  if (!arrangement) {
    return (
      <Shell>
        <div style={{ padding: 24 }}>
          <Button variant="ghost" Icon={ArrowLeft} onClick={() => navigate('/tprm/outsourcing')}>
            {isAr ? 'الرجوع للسجل' : 'Back to register'}
          </Button>
          <div style={{ marginTop: 16 }}>{isAr ? 'ترتيب غير موجود' : 'Arrangement not found'}</div>
        </div>
      </Shell>
    );
  }

  return (
    <Shell>
      <PageHeader
        backTo="/tprm/outsourcing"
        eyebrow={`${arrangement.arrangementCode}`}
        title={isAr ? arrangement.titleAr : arrangement.titleEn}
        sub={isAr ? arrangement.titleEn : arrangement.titleAr}
        badges={
          <>
            {arrangement.isMaterial
              ? <Badge tone="warning">{isAr ? 'إسناد جوهري' : 'Material'}</Badge>
              : <Badge tone="neutral">{isAr ? 'غير جوهري' : 'Non-material'}</Badge>}
            <Badge tone={lifecycleTone(arrangement.lifecycleStage)}>
              {LIFECYCLE_LABELS[arrangement.lifecycleStage]?.[isAr ? 'ar' : 'en'] ?? arrangement.lifecycleStage}
            </Badge>
          </>
        }
        actions={
          <div style={row}>
            <Button variant="secondary" Icon={Pencil} onClick={() => setEditOpen(true)}>
              {isAr ? 'تعديل' : 'Edit'}
            </Button>
            <Button variant="secondary" Icon={RefreshCw} onClick={onRecordReview}>
              {isAr ? 'تسجيل مراجعة' : 'Record review'}
            </Button>
          </div>
        }
      />

      {/* Regulatory-context chips (status + materiality now sit by the title) */}
      <Card>
        <div style={{ ...row, flexWrap: 'wrap' }}>
          <Badge tone="info">
            {SERVICE_TYPE_LABELS[arrangement.serviceType]?.[isAr ? 'ar' : 'en'] ?? arrangement.serviceType}
          </Badge>
          {arrangement.criticalFunction !== 'NA' && (
            <Badge tone="warning">
              {CRITICAL_FUNCTION_LABELS[arrangement.criticalFunction]?.[isAr ? 'ar' : 'en'] ?? arrangement.criticalFunction}
            </Badge>
          )}
          {arrangement.samaNonObjectionRequired && (
            arrangement.samaNonObjectionObtained
              ? <Badge tone="success">{isAr ? `عدم ممانعة ساما: ${arrangement.samaNonObjectionRef ?? '✓'}` : `SAMA non-objection: ${arrangement.samaNonObjectionRef ?? '✓'}`}</Badge>
              : <Badge tone="danger">{isAr ? 'عدم ممانعة ساما — مطلوب' : 'SAMA non-objection required'}</Badge>
          )}
        </div>
      </Card>

      <div style={{ marginTop: 16 }}>
        <Tabs items={tabs} active={tab} onChange={(id) => setTab(id as TabId)} />
      </div>

      {tab === 'overview' && (
        <div style={overviewGrid}>
          <Card title={isAr ? 'العقد' : 'Contract'} Icon={FileText}>
            <DataPair label={isAr ? 'القيمة السنوية' : 'Annual value'} value={fmtSar(arrangement.contractValueSarAnnual)} />
            <DataPair label={isAr ? 'بداية العقد' : 'Contract start'} value={arrangement.contractStartDate ?? '—'} />
            <DataPair label={isAr ? 'نهاية العقد' : 'Contract end'} value={arrangement.contractEndDate ?? '—'} />
            <DataPair label={isAr ? 'مرجع العقد' : 'Contract ref'} value={arrangement.contractRef ?? '—'} />
          </Card>
          <Card title={isAr ? 'المعالجة' : 'Processing'} Icon={ShieldCheck}>
            <DataPair label={isAr ? 'بلد المعالجة' : 'Country of processing'} value={arrangement.countryOfProcessing} />
            <DataPair label={isAr ? 'المنطقة' : 'Region'} value={arrangement.regionOfProcessing ?? '—'} />
            <DataPair label={isAr ? 'توطين البيانات بالمملكة' : 'KSA data residency'} value={arrangement.dataResidencyKdc ? '✓' : '✗'} />
            <DataPair label={isAr ? 'بيانات شخصية' : 'Personal data processed'} value={arrangement.personalDataProcessed ? '✓' : '✗'} />
            <DataPair label={isAr ? 'اتفاقية معالجة البيانات (PDPL)' : 'PDPL DPA'} value={arrangement.pdplDpaSigned ? (arrangement.pdplDpaDate ?? '✓') : '✗'} />
            {arrangement.crossBorderSafeguardType && (
              <DataPair label={isAr ? 'ضمانة عبر الحدود' : 'Cross-border safeguard'} value={arrangement.crossBorderSafeguardType} />
            )}
          </Card>
          <div style={fullSpan}>
            <Card>
              <AttachmentsField entityType="OUTSOURCING" entityId={arrangement.id} />
            </Card>
          </div>
          <div style={fullSpan}>
            <Card>
              <ControlMappingPicker entityType="OUTSOURCING" entityId={arrangement.id} evidence={false} />
            </Card>
          </div>
          <div style={fullSpan}>
            <Card title={isAr ? 'جاهزية الأدلة' : 'Evidence readiness'}>
              <EvidenceReadinessPanel entityType="OUTSOURCING" entityId={arrangement.id} />
            </Card>
          </div>
          <div style={fullSpan}>
            <Card title={isAr ? 'الجوهرية' : 'Materiality'} Icon={AlertTriangle}>
              <DataPair label={isAr ? 'مُصنَّفة' : 'Classified'} value={arrangement.isMaterial ? (isAr ? 'جوهرية' : 'Material') : (isAr ? 'غير جوهرية' : 'Non-material')} />
              {arrangement.materialityBasisEn && (
                <DataPair label={isAr ? 'الأساس' : 'Basis'} value={isAr ? (arrangement.materialityBasisAr ?? '') : arrangement.materialityBasisEn} />
              )}
            </Card>
          </div>
        </div>
      )}

      {tab === 'dd' && (
        <div style={stack}>
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button Icon={Plus} onClick={() => setOpenDd(true)}>
              {isAr ? 'تسجيل تقييم' : 'Record assessment'}
            </Button>
          </div>
          {dd.length === 0 && <Card><InlineEmpty text={isAr ? 'لا توجد سجلات عناية واجبة' : 'No due diligence records'} /></Card>}
          {dd.map(d => (
            <Card key={d.id}>
              <div style={rowStart}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>
                    {isAr ? 'تاريخ التقييم:' : 'Assessment date:'} {d.assessmentDate}
                  </div>
                  <div style={{ fontSize: 11, ...muted }}>
                    {isAr ? 'المُقيِّم' : 'Assessor'} #{d.assessorUserId}
                  </div>
                </div>
                <div style={row}>
                  <Badge tone={recommendationTone(d.recommendation)}>{d.recommendation}</Badge>
                  <span className="tnum" style={{ fontSize: 24, fontWeight: 800, letterSpacing: 'var(--tracking-num)' }}>{d.compositeScore.toFixed(2)}</span>
                </div>
              </div>
              <div style={{ marginTop: 12, display: 'grid', gap: 8, gridTemplateColumns: 'repeat(auto-fit, minmax(96px, 1fr))' }}>
                <ScoreCell label={isAr ? 'المالية' : 'Financial'} score={d.financialHealthScore} />
                <ScoreCell label={isAr ? 'التشغيلية' : 'Operational'} score={d.operationalCapabilityScore} />
                <ScoreCell label={isAr ? 'السيبراني' : 'Cyber'} score={d.cybersecurityScore} />
                <ScoreCell label={isAr ? 'BCM' : 'BCM'} score={d.bcmScore} />
                <ScoreCell label={isAr ? 'PDPL' : 'PDPL'} score={d.pdplComplianceScore} />
                <ScoreCell label={isAr ? 'السمعة' : 'Reputation'} score={d.reputationScore} />
              </div>
              <div style={{ marginTop: 12, fontSize: 13, ...muted }}>
                {isAr ? d.rationaleAr : d.rationaleEn}
              </div>
            </Card>
          ))}
        </div>
      )}

      {tab === 'events' && (
        <div style={stack}>
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button Icon={Plus} onClick={() => setOpenEvent(true)}>
              {isAr ? 'تسجيل حدث' : 'Record event'}
            </Button>
          </div>
          {events.length === 0 && <Card><InlineEmpty text={isAr ? 'لا توجد أحداث مراقبة' : 'No monitoring events'} /></Card>}
          {events.map(e => (
            <Card key={e.id}>
              <div style={rowStart}>
                <div>
                  <div style={{ ...row, flexWrap: 'wrap' }}>
                    <Badge tone={severityTone(e.severity)}>{SEVERITY_LABELS[e.severity]?.[isAr ? 'ar' : 'en'] ?? e.severity}</Badge>
                    <Badge tone="info">{EVENT_TYPE_LABELS[e.eventType]?.[isAr ? 'ar' : 'en'] ?? e.eventType}</Badge>
                    {e.resolved
                      ? <Badge tone="success">{isAr ? 'مُغلَق' : 'Resolved'}</Badge>
                      : <Badge tone="warning">{isAr ? 'مفتوح' : 'Open'}</Badge>}
                  </div>
                  <div style={{ marginTop: 4, fontSize: 11, ...muted }}>{e.eventDate}</div>
                </div>
              </div>
              <p style={{ marginTop: 8, fontSize: 13 }}>{isAr ? e.descriptionAr : e.descriptionEn}</p>
              {(e.actionTakenEn || e.actionTakenAr) && (
                <p style={{ marginTop: 4, fontSize: 11, ...muted }}>
                  <strong>{isAr ? 'الإجراء المُتَّخذ: ' : 'Action: '}</strong>
                  {isAr ? e.actionTakenAr : e.actionTakenEn}
                </p>
              )}
            </Card>
          ))}
        </div>
      )}

      {tab === 'exit' && (
        <div style={{ marginTop: 16 }}>
          {!exitPlan
            ? (
              <div style={stack}>
                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                  <Button Icon={Plus} onClick={() => setOpenExit(true)}>
                    {isAr ? 'إنشاء خطة خروج' : 'Create exit plan'}
                  </Button>
                </div>
                <Card><InlineEmpty text={isAr ? 'لا توجد خطة خروج لهذا الترتيب' : 'No exit plan for this arrangement'} /></Card>
              </div>
            )
            : (
              <Card title={isAr ? 'خطة الخروج' : 'Exit plan'} Icon={LogOut} action={
                <div style={row}>
                  <Badge tone={exitPlan.lastTestedDate ? 'success' : 'warning'}>
                    {TEST_FREQUENCY_LABELS[exitPlan.testFrequency]?.[isAr ? 'ar' : 'en'] ?? exitPlan.testFrequency}
                  </Badge>
                  {exitPlan.lastTestedDate && (
                    <span style={{ fontSize: 11, ...muted }}>
                      {isAr ? 'آخر اختبار:' : 'Last test:'} {exitPlan.lastTestedDate}
                    </span>
                  )}
                  <Button variant="secondary" Icon={ClipboardCheck} onClick={onRecordExitTest} disabled={testingExit}>
                    {isAr ? 'تسجيل اختبار' : 'Record test'}
                  </Button>
                </div>
              }>
                <DataPair
                  label={isAr ? 'شروط التفعيل' : 'Trigger conditions'}
                  value={isAr ? exitPlan.triggerConditionsAr : exitPlan.triggerConditionsEn}
                />
                {(exitPlan.dataRepatriationPlanEn || exitPlan.dataRepatriationPlanAr) && (
                  <DataPair
                    label={isAr ? 'خطة استرداد البيانات' : 'Data repatriation'}
                    value={isAr ? (exitPlan.dataRepatriationPlanAr ?? '') : (exitPlan.dataRepatriationPlanEn ?? '')}
                  />
                )}
                {exitPlan.exitStepsJson && (
                  <div style={{ marginTop: 12 }}>
                    <div style={{ fontSize: 10, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4, ...muted }}>
                      {isAr ? 'خطوات الخروج' : 'Exit steps'}
                    </div>
                    <ExitSteps json={exitPlan.exitStepsJson} isAr={isAr} />
                  </div>
                )}
              </Card>
            )}
        </div>
      )}

      {tab === 'activity' && (
        <div style={{ marginTop: 16 }}>
          <Card title={isAr ? 'سجل النشاط' : 'Activity log'} Icon={History}>
            <p style={{ fontSize: 13, ...muted }}>
              {isAr
                ? 'يُجلب سجل التدقيق من خدمة F7 على مستوى المنصة.'
                : 'F7 platform-level audit log entries are surfaced via the audit module.'}
            </p>
          </Card>
        </div>
      )}

      <FormModal
        open={editOpen}
        title={isAr ? 'تعديل الترتيب' : 'Edit arrangement'}
        fields={editFields}
        initial={editInitial}
        onClose={() => setEditOpen(false)}
        onSubmit={onUpdate}
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
      />

      <FormModal
        open={openEvent}
        title={isAr ? 'تسجيل حدث مراقبة' : 'Record monitoring event'}
        fields={eventFields}
        initial={eventInitial}
        onClose={() => setOpenEvent(false)}
        onSubmit={submitEvent}
        submitLabel={isAr ? 'تسجيل الحدث' : 'Record event'}
      />

      <FormModal
        open={openDd}
        title={isAr ? 'تسجيل تقييم عناية واجبة' : 'Record due-diligence assessment'}
        fields={ddFields}
        initial={ddInitial}
        onClose={() => setOpenDd(false)}
        onSubmit={submitDd}
        submitLabel={isAr ? 'تسجيل التقييم' : 'Record assessment'}
      />

      <FormModal
        open={openExit}
        title={isAr ? 'إنشاء خطة خروج' : 'Create exit plan'}
        fields={exitFields}
        initial={exitInitial}
        onClose={() => setOpenExit(false)}
        onSubmit={submitExit}
        submitLabel={isAr ? 'إنشاء الخطة' : 'Create plan'}
      />
    </Shell>
  );
}

function DataPair({ label, value }: { label: string; value: string | number }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(120px, 1fr) 2fr', gap: 8, padding: '4px 0', fontSize: 13, alignItems: 'baseline' }}>
      <div style={{ color: 'var(--ink-3)' }}>{label}</div>
      <div style={{ fontWeight: 500, color: 'var(--ink-1)' }}>{value}</div>
    </div>
  );
}

function ScoreCell({ label, score }: { label: string; score: number }) {
  const color =
    score >= 85 ? 'var(--success)'
    : score >= 70 ? 'var(--warning)'
    : 'var(--danger)';
  return (
    <div style={{ borderRadius: 'var(--r-md)', border: '1px solid var(--border)', background: 'var(--surface-2)', padding: 8 }}>
      <div style={{ fontSize: 10, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--ink-3)' }}>{label}</div>
      <div className="tnum" style={{ fontSize: 18, fontWeight: 700, color }}>{score}</div>
    </div>
  );
}

interface ExitStep { step: string; owner?: string; target_date_relative_days?: number }

function ExitSteps({ json, isAr }: { json: string; isAr: boolean }) {
  let parsed: ExitStep[] = [];
  try { parsed = JSON.parse(json) as ExitStep[]; } catch { /* ignore */ }
  if (!Array.isArray(parsed) || parsed.length === 0) {
    return <pre style={{ fontSize: 11, whiteSpace: 'pre-wrap' }}>{json}</pre>;
  }
  return (
    <ol style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 13, margin: 0, paddingInlineStart: 0, listStyle: 'none' }}>
      {parsed.map((s, i) => (
        <li key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
          <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{i + 1}.</span>
          <div style={{ flex: 1 }}>
            <div>{s.step}</div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
              {s.owner && <span>{isAr ? 'المسؤول: ' : 'Owner: '}{s.owner}</span>}
              {typeof s.target_date_relative_days === 'number' && (
                <span style={{ marginInlineStart: 12 }}>
                  {isAr
                    ? `الموعد المُستهدف: +${s.target_date_relative_days} يوم`
                    : `Target: T+${s.target_date_relative_days}d`}
                </span>
              )}
            </div>
          </div>
        </li>
      ))}
    </ol>
  );
}
