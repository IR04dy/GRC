import { useEffect, useMemo, useState, type CSSProperties, type ReactNode } from 'react';
import { ClipboardCheck, Play, Plus, ToggleLeft, ToggleRight } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { isAuthenticated } from '../../lib/auth';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  ccmApi,
  CCM_STATUS_LABEL, CCM_STATUS_TONE,
  CCM_SEVERITY_LABEL, CCM_SEVERITY_TONE,
  CCM_TEST_TYPE_LABEL, CCM_TARGET_SYSTEM_LABEL,
  type TestDefinitionResponse, type TestDefinitionRequest,
  type CcmTestType, type CcmTargetSystem, type CcmSeverity, type CcmRunStatus,
} from '../../lib/ccmEngine';

/** Cron presets so an operator picks a cadence without hand-writing crontab. */
const CRON_PRESETS: { cron: string; en: string; ar: string }[] = [
  { cron: '*/15 * * * *', en: 'Every 15 minutes',     ar: 'كل ١٥ دقيقة' },
  { cron: '0 * * * *',    en: 'Hourly',               ar: 'كل ساعة' },
  { cron: '0 2 * * *',    en: 'Daily — 02:00',        ar: 'يومياً — ٠٢:٠٠' },
  { cron: '0 2 * * 0',    en: 'Weekly — Sunday 02:00',ar: 'أسبوعياً — الأحد ٠٢:٠٠' },
  { cron: '0 2 1 * *',    en: 'Monthly — 1st 02:00',  ar: 'شهرياً — الأول ٠٢:٠٠' },
];

interface CreateForm {
  testCode: string;
  titleEn: string;
  titleAr: string;
  descEn: string;
  descAr: string;
  testType: CcmTestType;
  targetSystemType: CcmTargetSystem;
  severity: CcmSeverity;
  frequencyCron: string;
  timeoutSeconds: string;
  targetConfigJson: string;
  expectedConditionJson: string;
}

const EMPTY_CREATE: CreateForm = {
  testCode: '', titleEn: '', titleAr: '', descEn: '', descAr: '',
  testType: 'MFA_ENROLLMENT_PERCENTAGE',
  targetSystemType: 'AZURE_AD',
  severity: 'MEDIUM',
  frequencyCron: '0 2 * * *',
  timeoutSeconds: '30',
  targetConfigJson: '',
  expectedConditionJson: '',
};

/**
 * CCM Tests register — Sprint 11.
 *
 * Filterable list of every test definition for the current tenant.
 * Per-row "Run now" and "Enable / Disable" buttons (role-gated by the
 * backend; the UI is permissive and falls back to a toast on 403).
 */
export function CcmTestsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<TestDefinitionResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Define-test modal
  const [showCreate, setShowCreate] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<CreateForm>(EMPTY_CREATE);
  const set = <K extends keyof CreateForm>(k: K, v: CreateForm[K]) => setForm(f => ({ ...f, [k]: v }));

  const [filterType, setFilterType] = useState<'ALL' | CcmTestType>('ALL');
  const [filterSeverity, setFilterSeverity] = useState<'ALL' | CcmSeverity>('ALL');
  const [filterEnabled, setFilterEnabled] = useState<'ALL' | 'YES' | 'NO'>('ALL');
  const [filterLastStatus, setFilterLastStatus] = useState<'ALL' | CcmRunStatus>('ALL');

  async function load() {
    if (demoMode) { setRows([]); return; }
    setLoading(true);
    setError(null);
    try {
      setRows(await ccmApi.listTests());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load CCM tests');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  async function toggle(row: TestDefinitionResponse) {
    setBusyId(row.externalId);
    try {
      const op = row.isEnabled ? ccmApi.disableTest : ccmApi.enableTest;
      const updated = await op(row.externalId);
      setRows(prev => prev.map(r => r.externalId === row.externalId ? updated : r));
      toast.success(lang === 'ar'
        ? (updated.isEnabled ? 'تم تفعيل الاختبار' : 'تم تعطيل الاختبار')
        : (updated.isEnabled ? 'Test enabled' : 'Test disabled'));
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Toggle failed');
    } finally {
      setBusyId(null);
    }
  }

  function openCreate() {
    setForm(EMPTY_CREATE);
    setShowCreate(true);
  }

  async function submitCreate() {
    if (!form.titleEn.trim()) {
      toast.danger(isAr ? 'العنوان (بالإنجليزية) مطلوب' : 'Title (EN) is required');
      return;
    }
    // Validate optional JSON config blocks so the backend never sees malformed JSON.
    for (const [val, label] of [
      [form.targetConfigJson, isAr ? 'إعدادات النظام المستهدف' : 'Target config'],
      [form.expectedConditionJson, isAr ? 'الشرط المتوقع' : 'Expected condition'],
    ] as const) {
      if (val.trim()) {
        try { JSON.parse(val); }
        catch { toast.danger((isAr ? 'JSON غير صالح — ' : 'Invalid JSON — ') + label); return; }
      }
    }
    setSaving(true);
    try {
      const body: TestDefinitionRequest = {
        testCode: form.testCode.trim() || undefined,
        titleEn: form.titleEn.trim(),
        titleAr: form.titleAr.trim() || undefined,
        descriptionEn: form.descEn.trim() || undefined,
        descriptionAr: form.descAr.trim() || undefined,
        testType: form.testType,
        targetSystemType: form.targetSystemType,
        severity: form.severity,
        frequencyCron: form.frequencyCron,
        timeoutSeconds: form.timeoutSeconds.trim() ? Number(form.timeoutSeconds) : undefined,
        targetConfigJson: form.targetConfigJson.trim() || undefined,
        expectedConditionJson: form.expectedConditionJson.trim() || undefined,
      };
      const created = await ccmApi.createTest(body);
      toast.success(isAr
        ? `تم تعريف الاختبار ${created.testCode} — يبدأ معطّلاً`
        : `Test ${created.testCode} defined — starts disabled`);
      setShowCreate(false);
      await load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (isAr ? 'تعذّر تعريف الاختبار' : 'Failed to define test'));
    } finally {
      setSaving(false);
    }
  }

  async function runNow(row: TestDefinitionResponse) {
    setBusyId(row.externalId);
    try {
      const run = await ccmApi.runNow(row.externalId);
      toast.success(lang === 'ar'
        ? `تم تشغيل الاختبار: ${CCM_STATUS_LABEL[run.status].ar}`
        : `Run completed: ${CCM_STATUS_LABEL[run.status].en}`);
      // Reload to pick up updated lastRunAt + lastRunStatus.
      await load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Run failed');
    } finally {
      setBusyId(null);
    }
  }

  const filtered = useMemo(() => rows.filter(r => {
    if (filterType !== 'ALL' && r.testType !== filterType) return false;
    if (filterSeverity !== 'ALL' && r.severity !== filterSeverity) return false;
    if (filterEnabled === 'YES' && !r.isEnabled) return false;
    if (filterEnabled === 'NO' && r.isEnabled) return false;
    if (filterLastStatus !== 'ALL' && r.lastRunStatus !== filterLastStatus) return false;
    return true;
  }), [rows, filterType, filterSeverity, filterEnabled, filterLastStatus]);

  const cols: Column<TestDefinitionResponse>[] = [
    {
      header: lang === 'ar' ? 'رمز الاختبار' : 'Test code', width: '220px',
      cell: r => <code style={{ fontSize: 12 }}>{r.testCode}</code>,
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: r => lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn,
    },
    {
      header: lang === 'ar' ? 'النوع' : 'Type', width: '180px',
      cell: r => <span style={{ fontSize: 12 }}>
        {CCM_TEST_TYPE_LABEL[r.testType][lang === 'ar' ? 'ar' : 'en']}
      </span>,
    },
    {
      header: lang === 'ar' ? 'النظام' : 'Target', width: '140px',
      cell: r => <span style={{ fontSize: 12 }}>
        {CCM_TARGET_SYSTEM_LABEL[r.targetSystemType][lang === 'ar' ? 'ar' : 'en']}
      </span>,
    },
    {
      header: lang === 'ar' ? 'الخطورة' : 'Severity', width: '110px', align: 'center',
      cell: r => (
        <Badge tone={CCM_SEVERITY_TONE[r.severity]}>
          {CCM_SEVERITY_LABEL[r.severity][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'مُفعَّل' : 'Enabled', width: '90px', align: 'center',
      cell: r => r.isEnabled
        ? <Badge tone="success">{lang === 'ar' ? 'نعم' : 'Yes'}</Badge>
        : <Badge tone="neutral">{lang === 'ar' ? 'لا' : 'No'}</Badge>,
    },
    {
      header: lang === 'ar' ? 'آخر حالة' : 'Last status', width: '120px', align: 'center',
      cell: r => r.lastRunStatus
        ? <Badge tone={CCM_STATUS_TONE[r.lastRunStatus]} dot>
            {CCM_STATUS_LABEL[r.lastRunStatus][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        : <span style={{ color: 'var(--ink-3)', fontSize: 12 }}>—</span>,
    },
    {
      header: lang === 'ar' ? 'إجراء' : 'Action', width: '180px', align: 'center',
      cell: r => (
        <div style={{ display: 'flex', gap: 4, justifyContent: 'center' }} onClick={e => e.stopPropagation()}>
          <button
            type="button"
            disabled={busyId === r.externalId}
            onClick={() => runNow(r)}
            title={lang === 'ar' ? 'تشغيل الآن' : 'Run now'}
            style={{ padding: '3px 8px', border: '1px solid var(--border)',
                     borderRadius: 4, background: 'var(--surface)', cursor: 'pointer', fontSize: 11 }}>
            <Play size={11} style={{ verticalAlign: '-1px' }} />
            {' '}{lang === 'ar' ? 'تشغيل' : 'Run'}
          </button>
          <button
            type="button"
            disabled={busyId === r.externalId}
            onClick={() => toggle(r)}
            title={r.isEnabled ? (lang === 'ar' ? 'تعطيل' : 'Disable') : (lang === 'ar' ? 'تفعيل' : 'Enable')}
            style={{ padding: '3px 8px', border: '1px solid var(--border)',
                     borderRadius: 4, background: 'var(--surface)', cursor: 'pointer', fontSize: 11 }}>
            {r.isEnabled ? <ToggleRight size={11} /> : <ToggleLeft size={11} />}
            {' '}{r.isEnabled ? (lang === 'ar' ? 'تعطيل' : 'Disable') : (lang === 'ar' ? 'تفعيل' : 'Enable')}
          </button>
        </div>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'تعريفات الاختبارات' : 'CCM test definitions'}
      sub={lang === 'ar' ? 'المراقبة المستمرة للضوابط' : 'Continuous Control Monitoring'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الامتثال' : 'Compliance'}
        title={lang === 'ar' ? 'تعريفات الاختبارات' : 'Test definitions'}
        sub={lang === 'ar'
          ? 'كل اختبار يحدد ما يتم اختباره وكيف ومتى. الاختبارات تبدأ معطّلة افتراضياً؛ يجب تفعيل كل اختبار يدوياً قبل تشغيله مقابل أي نظام خارجي.'
          : 'Every test specifies what to check, how, and when. Tests start disabled — operators opt in per-test before any external connection.'}
        actions={demoMode
          ? <Badge tone="warning">{isAr ? 'وضع العرض' : 'Demo mode'}</Badge>
          : <Button variant="primary" size="sm" Icon={Plus} onClick={openCreate}>
              {isAr ? 'تعريف اختبار' : 'Define test'}
            </Button>}
      />

      <Card padded>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <FilterSelect
            label={lang === 'ar' ? 'النوع' : 'Type'}
            value={filterType}
            onChange={v => setFilterType(v as 'ALL' | CcmTestType)}
            options={[
              { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
              ...(Object.keys(CCM_TEST_TYPE_LABEL) as CcmTestType[]).map(k => ({
                value: k, label: CCM_TEST_TYPE_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
              })),
            ]} />
          <FilterSelect
            label={lang === 'ar' ? 'الخطورة' : 'Severity'}
            value={filterSeverity}
            onChange={v => setFilterSeverity(v as 'ALL' | CcmSeverity)}
            options={[
              { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
              ...(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as CcmSeverity[]).map(k => ({
                value: k, label: CCM_SEVERITY_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
              })),
            ]} />
          <FilterSelect
            label={lang === 'ar' ? 'مُفعَّل' : 'Enabled'}
            value={filterEnabled}
            onChange={v => setFilterEnabled(v as 'ALL' | 'YES' | 'NO')}
            options={[
              { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
              { value: 'YES', label: lang === 'ar' ? 'نعم' : 'Yes' },
              { value: 'NO',  label: lang === 'ar' ? 'لا'  : 'No' },
            ]} />
          <FilterSelect
            label={lang === 'ar' ? 'آخر حالة' : 'Last status'}
            value={filterLastStatus}
            onChange={v => setFilterLastStatus(v as 'ALL' | CcmRunStatus)}
            options={[
              { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
              ...(['PASS', 'FAIL', 'ERROR', 'TIMEOUT', 'SKIPPED'] as CcmRunStatus[]).map(k => ({
                value: k, label: CCM_STATUS_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
              })),
            ]} />
        </div>
      </Card>

      {error && (
        <Card padded>
          <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
        </Card>
      )}

      <Card padded>
        {loading
          ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : <DataTable
              columns={cols}
              rows={filtered}
              rowHref={r => `/ccm/tests/${r.externalId}`}
              empty={<EmptyState
                Icon={ClipboardCheck}
                title={lang === 'ar' ? 'لا توجد اختبارات' : 'No tests'}
                sub={lang === 'ar'
                  ? 'ستظهر هنا كل تعريفات اختبارات المراقبة المستمرة عند توفّرها.'
                  : 'Defined CCM tests will appear here once seeded or created.'} />}
            />
        }
      </Card>

      {/* Define-test modal */}
      <Modal
        open={showCreate}
        onClose={() => { if (!saving) setShowCreate(false); }}
        size="lg"
        title={isAr ? 'تعريف اختبار مراقبة مستمرة' : 'Define a continuous-control test'}
        footer={
          <>
            <Button variant="ghost" onClick={() => setShowCreate(false)} disabled={saving}>
              {isAr ? 'إلغاء' : 'Cancel'}
            </Button>
            <Button variant="primary" Icon={Plus} onClick={() => void submitCreate()} disabled={saving}>
              {saving ? (isAr ? 'جارٍ التعريف…' : 'Defining…') : (isAr ? 'تعريف الاختبار' : 'Define test')}
            </Button>
          </>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <p style={{ fontSize: 11.5, color: 'var(--ink-3)', margin: 0, lineHeight: 1.6 }}>
            {isAr
              ? 'يُحدِّد الاختبار ما يُفحَص وكيف ومتى. يبدأ كل اختبار جديد مُعطَّلاً — فعّله يدوياً من السجل قبل أن يتصل بأي نظام خارجي.'
              : 'A test specifies what to check, how, and when. Every new test starts disabled — enable it from the register before it connects to any external system.'}
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <FormField label={isAr ? 'العنوان (إنجليزي) *' : 'Title (EN) *'}>
              <input style={ctrl} value={form.titleEn} onChange={e => set('titleEn', e.target.value)}
                placeholder={isAr ? 'مثال: نسبة تفعيل التحقق الثنائي' : 'e.g. MFA enrolment percentage'} />
            </FormField>
            <FormField label={isAr ? 'العنوان (عربي)' : 'Title (AR)'}>
              <input style={ctrl} value={form.titleAr} onChange={e => set('titleAr', e.target.value)} dir="rtl"
                placeholder={isAr ? 'مثال: نسبة تفعيل التحقق الثنائي' : 'العنوان بالعربية'} />
            </FormField>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <FormField label={isAr ? 'نوع الاختبار *' : 'Test type *'}>
              <select style={ctrl} value={form.testType} onChange={e => set('testType', e.target.value as CcmTestType)}>
                {(Object.keys(CCM_TEST_TYPE_LABEL) as CcmTestType[]).map(k => (
                  <option key={k} value={k}>{CCM_TEST_TYPE_LABEL[k][isAr ? 'ar' : 'en']}</option>
                ))}
              </select>
            </FormField>
            <FormField label={isAr ? 'النظام المستهدف *' : 'Target system *'}>
              <select style={ctrl} value={form.targetSystemType} onChange={e => set('targetSystemType', e.target.value as CcmTargetSystem)}>
                {(Object.keys(CCM_TARGET_SYSTEM_LABEL) as CcmTargetSystem[]).map(k => (
                  <option key={k} value={k}>{CCM_TARGET_SYSTEM_LABEL[k][isAr ? 'ar' : 'en']}</option>
                ))}
              </select>
            </FormField>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <FormField label={isAr ? 'الخطورة' : 'Severity'}>
              <select style={ctrl} value={form.severity} onChange={e => set('severity', e.target.value as CcmSeverity)}>
                {(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as CcmSeverity[]).map(k => (
                  <option key={k} value={k}>{CCM_SEVERITY_LABEL[k][isAr ? 'ar' : 'en']}</option>
                ))}
              </select>
            </FormField>
            <FormField label={isAr ? 'التكرار' : 'Frequency'}>
              <select style={ctrl} value={form.frequencyCron} onChange={e => set('frequencyCron', e.target.value)}>
                {CRON_PRESETS.map(p => (
                  <option key={p.cron} value={p.cron}>{isAr ? p.ar : p.en}</option>
                ))}
              </select>
            </FormField>
            <FormField label={isAr ? 'المهلة (ثانية)' : 'Timeout (sec)'}>
              <input style={ctrl} type="number" min={1} value={form.timeoutSeconds}
                onChange={e => set('timeoutSeconds', e.target.value)} />
            </FormField>
          </div>

          <FormField label={isAr ? 'رمز الاختبار (اختياري)' : 'Test code (optional)'}>
            <input style={ctrl} value={form.testCode} onChange={e => set('testCode', e.target.value)}
              placeholder={isAr ? 'يُولَّد تلقائياً حسب النوع' : 'Auto-generated from type when blank'} />
          </FormField>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <FormField label={isAr ? 'إعدادات النظام (JSON اختياري)' : 'Target config (JSON, optional)'}>
              <textarea style={{ ...ctrl, minHeight: 70, resize: 'vertical', fontFamily: 'var(--font-mono, monospace)' }}
                value={form.targetConfigJson} onChange={e => set('targetConfigJson', e.target.value)}
                placeholder={'{ "tenant": "primary" }'} dir="ltr" />
            </FormField>
            <FormField label={isAr ? 'الشرط المتوقع (JSON اختياري)' : 'Expected condition (JSON, optional)'}>
              <textarea style={{ ...ctrl, minHeight: 70, resize: 'vertical', fontFamily: 'var(--font-mono, monospace)' }}
                value={form.expectedConditionJson} onChange={e => set('expectedConditionJson', e.target.value)}
                placeholder={'{ "op": ">=", "value": 95 }'} dir="ltr" />
            </FormField>
          </div>
        </div>
      </Modal>
    </Shell>
  );
}

const ctrl: CSSProperties = {
  width: '100%', padding: '7px 9px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 6,
  background: 'var(--surface)', color: 'var(--ink-1)', outline: 'none',
};

function FormField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
      <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--ink-2)' }}>{label}</span>
      {children}
    </label>
  );
}

interface FilterSelectProps {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
}

function FilterSelect({ label, value, onChange, options }: FilterSelectProps) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 140 }}>
      <label style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: 0.4 }}>
        {label}
      </label>
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        style={{
          padding: '6px 8px',
          border: '1px solid var(--border)',
          borderRadius: 4,
          background: 'var(--surface)',
          color: 'var(--ink-1)',
          fontSize: 13,
        }}
      >
        {options.map(o => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
    </div>
  );
}
