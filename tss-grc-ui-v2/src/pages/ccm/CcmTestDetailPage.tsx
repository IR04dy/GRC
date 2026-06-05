import { useEffect, useState, type CSSProperties, type ReactNode } from 'react';
import { useParams } from 'react-router-dom';
import {
  ClipboardCheck, Play, Pencil, ToggleLeft, ToggleRight, Fingerprint,
  Activity,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { Badge } from '../../components/ui/Badge';
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
  prettyJson, shortHash,
  type TestDefinitionResponse, type TestDefinitionRequest, type TestRunResponse,
  type CcmRunStatus, type CcmTestType, type CcmTargetSystem, type CcmSeverity,
} from '../../lib/ccmEngine';

interface EditForm {
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

/**
 * CCM Test detail — Sprint 11.
 *
 * Layout: header card with the bilingual title, severity + status
 * badges, then a config card with the pretty-printed
 * target_config_json + expected_condition_json. Below that, a run
 * history table and a status sparkline of the last 30 runs (PASS = up,
 * FAIL = down).
 */
export function CcmTestDetailPage() {
  const { externalId } = useParams<{ externalId: string }>();
  const { lang } = useI18n();
  const toast = useToast();
  const demoMode = !isAuthenticated();

  const [test, setTest] = useState<TestDefinitionResponse | null>(null);
  const [runs, setRuns] = useState<TestRunResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Edit-definition modal
  const [showEdit, setShowEdit] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<EditForm | null>(null);
  const set = <K extends keyof EditForm>(k: K, v: EditForm[K]) =>
    setForm(f => (f ? { ...f, [k]: v } : f));

  async function load() {
    if (!externalId || demoMode) return;
    setLoading(true);
    setError(null);
    try {
      const t = await ccmApi.getTest(externalId);
      setTest(t);
      const r = await ccmApi.getRuns(externalId, 50);
      setRuns(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load test');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [externalId]);

  async function toggle() {
    if (!test) return;
    setBusy(true);
    try {
      const op = test.isEnabled ? ccmApi.disableTest : ccmApi.enableTest;
      const updated = await op(test.externalId);
      setTest(updated);
      toast.success(lang === 'ar'
        ? (updated.isEnabled ? 'تم تفعيل الاختبار' : 'تم تعطيل الاختبار')
        : (updated.isEnabled ? 'Test enabled' : 'Test disabled'));
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Toggle failed');
    } finally {
      setBusy(false);
    }
  }

  async function runNow() {
    if (!test) return;
    setBusy(true);
    try {
      const run = await ccmApi.runNow(test.externalId);
      toast.success(lang === 'ar'
        ? `تم تشغيل الاختبار: ${CCM_STATUS_LABEL[run.status].ar}`
        : `Run completed: ${CCM_STATUS_LABEL[run.status].en}`);
      await load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Run failed');
    } finally {
      setBusy(false);
    }
  }

  function openEdit() {
    if (!test) return;
    setForm({
      titleEn: test.titleEn ?? '',
      titleAr: test.titleAr ?? '',
      descEn: test.descriptionEn ?? '',
      descAr: test.descriptionAr ?? '',
      testType: test.testType,
      targetSystemType: test.targetSystemType,
      severity: test.severity,
      frequencyCron: test.frequencyCron ?? '0 2 * * *',
      timeoutSeconds: String(test.timeoutSeconds ?? 30),
      targetConfigJson: test.targetConfigJson ?? '',
      expectedConditionJson: test.expectedConditionJson ?? '',
    });
    setShowEdit(true);
  }

  async function submitEdit() {
    if (!test || !form) return;
    if (!form.titleEn.trim()) {
      toast.danger(lang === 'ar' ? 'العنوان (بالإنجليزية) مطلوب' : 'Title (EN) is required');
      return;
    }
    for (const [val, label] of [
      [form.targetConfigJson, lang === 'ar' ? 'إعدادات النظام المستهدف' : 'Target config'],
      [form.expectedConditionJson, lang === 'ar' ? 'الشرط المتوقع' : 'Expected condition'],
    ] as const) {
      if (val.trim()) {
        try { JSON.parse(val); }
        catch { toast.danger((lang === 'ar' ? 'JSON غير صالح — ' : 'Invalid JSON — ') + label); return; }
      }
    }
    setSaving(true);
    try {
      const body: TestDefinitionRequest = {
        titleEn: form.titleEn.trim(),
        titleAr: form.titleAr.trim() || undefined,
        descriptionEn: form.descEn.trim() || undefined,
        descriptionAr: form.descAr.trim() || undefined,
        testType: form.testType,
        targetSystemType: form.targetSystemType,
        severity: form.severity,
        frequencyCron: form.frequencyCron.trim() || '0 2 * * *',
        timeoutSeconds: form.timeoutSeconds.trim() ? Number(form.timeoutSeconds) : undefined,
        targetConfigJson: form.targetConfigJson.trim() || undefined,
        expectedConditionJson: form.expectedConditionJson.trim() || undefined,
      };
      const updated = await ccmApi.updateTest(test.externalId, body);
      setTest(updated);
      toast.success(lang === 'ar' ? 'تم تحديث تعريف الاختبار' : 'Test definition updated');
      setShowEdit(false);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (lang === 'ar' ? 'تعذّر تحديث الاختبار' : 'Failed to update test'));
    } finally {
      setSaving(false);
    }
  }

  const runCols: Column<TestRunResponse>[] = [
    {
      header: lang === 'ar' ? 'الوقت' : 'Time', width: '180px',
      cell: r => <span style={{ fontVariantNumeric: 'tabular-nums', fontSize: 12 }}>
        {r.runAt ? new Date(r.runAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB') : '—'}
      </span>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '120px', align: 'center',
      cell: r => (
        <Badge tone={CCM_STATUS_TONE[r.status]} dot>
          {CCM_STATUS_LABEL[r.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'المدة' : 'Duration', width: '90px', align: 'center',
      cell: r => r.durationMs == null
        ? '—'
        : <span style={{ fontVariantNumeric: 'tabular-nums', fontSize: 12 }}>{r.durationMs} ms</span>,
    },
    {
      header: lang === 'ar' ? 'بصمة الدليل' : 'Evidence hash', width: '180px',
      cell: r => <code title={r.evidenceHash ?? ''}
                      style={{ fontSize: 11, color: 'var(--ink-3)' }}>
        <Fingerprint size={11} style={{ verticalAlign: '-1px', marginInlineEnd: 4 }} />
        {shortHash(r.evidenceHash)}
      </code>,
    },
    {
      header: lang === 'ar' ? 'السبب' : 'Reason',
      cell: r => r.failureReason
        ? <span style={{ fontSize: 12 }}>{r.failureReason}</span>
        : <span style={{ color: 'var(--ink-3)' }}>—</span>,
    },
  ];

  if (loading && !test) {
    return <Shell title="CCM"><InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty></Shell>;
  }
  if (error && !test) {
    return <Shell title="CCM"><Card padded><div style={{ color: 'var(--danger)' }}>{error}</div></Card></Shell>;
  }
  if (!test) {
    return <Shell title="CCM">
      <EmptyState Icon={ClipboardCheck}
        title={lang === 'ar' ? 'الاختبار غير موجود' : 'Test not found'} />
    </Shell>;
  }

  return (
    <Shell
      title={lang === 'ar' && test.titleAr ? test.titleAr : test.titleEn}
      sub={lang === 'ar' ? 'المراقبة المستمرة للضوابط' : 'Continuous Control Monitoring'}
    >
      <PageHeader
        eyebrow={test.testCode}
        title={lang === 'ar' && test.titleAr ? test.titleAr : test.titleEn}
        sub={lang === 'ar' && test.descriptionAr ? test.descriptionAr : test.descriptionEn}
        actions={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <Badge tone={CCM_SEVERITY_TONE[test.severity]}>
              {CCM_SEVERITY_LABEL[test.severity][lang === 'ar' ? 'ar' : 'en']}
            </Badge>
            {test.lastRunStatus && (
              <Badge tone={CCM_STATUS_TONE[test.lastRunStatus]} dot>
                {CCM_STATUS_LABEL[test.lastRunStatus][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
            )}
            {!demoMode && (
              <button
                type="button"
                disabled={busy}
                onClick={openEdit}
                style={{ padding: '6px 12px', border: '1px solid var(--border)',
                         borderRadius: 6, background: 'var(--surface)', cursor: 'pointer', fontSize: 13 }}>
                <Pencil size={13} style={{ verticalAlign: '-2px', marginInlineEnd: 4 }} />
                {lang === 'ar' ? 'تعديل' : 'Edit'}
              </button>
            )}
            <button
              type="button"
              disabled={busy}
              onClick={runNow}
              style={{ padding: '6px 12px', border: '1px solid var(--border)',
                       borderRadius: 6, background: 'var(--surface)', cursor: 'pointer', fontSize: 13 }}>
              <Play size={13} style={{ verticalAlign: '-2px', marginInlineEnd: 4 }} />
              {lang === 'ar' ? 'تشغيل الآن' : 'Run now'}
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={toggle}
              style={{ padding: '6px 12px', border: '1px solid var(--border)',
                       borderRadius: 6, background: 'var(--surface)', cursor: 'pointer', fontSize: 13 }}>
              {test.isEnabled
                ? <><ToggleRight size={13} style={{ verticalAlign: '-2px', marginInlineEnd: 4 }} />
                    {lang === 'ar' ? 'تعطيل' : 'Disable'}</>
                : <><ToggleLeft size={13} style={{ verticalAlign: '-2px', marginInlineEnd: 4 }} />
                    {lang === 'ar' ? 'تفعيل' : 'Enable'}</>}
            </button>
          </div>
        }
      />

      <div className="kpi-grid-sm">
        <Kpi label={lang === 'ar' ? 'النوع' : 'Type'}
             value={CCM_TEST_TYPE_LABEL[test.testType][lang === 'ar' ? 'ar' : 'en']} />
        <Kpi label={lang === 'ar' ? 'النظام' : 'Target system'}
             value={CCM_TARGET_SYSTEM_LABEL[test.targetSystemType][lang === 'ar' ? 'ar' : 'en']} />
        <Kpi label={lang === 'ar' ? 'الجدول' : 'Schedule (cron)'}
             value={<code style={{ fontSize: 12 }}>{test.frequencyCron}</code>} />
        <Kpi label={lang === 'ar' ? 'مهلة التنفيذ' : 'Timeout'}
             value={`${test.timeoutSeconds}s`} />
        <Kpi label={lang === 'ar' ? 'إخفاقات متتالية' : 'Consecutive failures'}
             value={test.consecutiveFailures} />
        <Kpi label={lang === 'ar' ? 'مُفعَّل' : 'Enabled'}
             value={test.isEnabled ? (lang === 'ar' ? 'نعم' : 'Yes') : (lang === 'ar' ? 'لا' : 'No')} />
      </div>

      <Card padded>
        <h3 style={{ marginTop: 0 }}>{lang === 'ar' ? 'سجل النتائج (آخر 30 تشغيلة)' : 'Status sparkline (last 30 runs)'}</h3>
        <Sparkline runs={runs.slice(0, 30)} />
      </Card>

      <Card padded>
        <h3 style={{ marginTop: 0 }}>{lang === 'ar' ? 'الإعداد' : 'Configuration'}</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <div>
            <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 4 }}>
              {lang === 'ar' ? 'إعدادات النظام المستهدف' : 'target_config_json'}
            </div>
            <pre style={{ background: 'var(--surface-2)', padding: 12, borderRadius: 6,
                          fontSize: 12, overflow: 'auto', margin: 0 }}>
              {prettyJson(test.targetConfigJson) || '—'}
            </pre>
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 4 }}>
              {lang === 'ar' ? 'الشرط المتوقّع' : 'expected_condition_json'}
            </div>
            <pre style={{ background: 'var(--surface-2)', padding: 12, borderRadius: 6,
                          fontSize: 12, overflow: 'auto', margin: 0 }}>
              {prettyJson(test.expectedConditionJson) || '—'}
            </pre>
          </div>
        </div>
      </Card>

      <Card padded>
        <h3 style={{ marginTop: 0 }}>{lang === 'ar' ? 'سجل التشغيل' : 'Run history'}</h3>
        {loading
          ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : <DataTable
              columns={runCols}
              rows={runs}
              empty={<EmptyState
                Icon={Activity}
                title={lang === 'ar' ? 'لا يوجد سجل تشغيل' : 'No runs yet'}
                sub={lang === 'ar'
                  ? 'فعّل الاختبار أو شغّله الآن لتسجيل أول تشغيلة.'
                  : 'Enable the test or run it now to record the first execution.'} />}
            />
        }
      </Card>

      {/* Documents & control mapping — test procedure, sample evidence, monitored controls */}
      <div style={{ marginTop: 16 }}>
        <Card padded title={lang === 'ar' ? 'المستندات وربط الضوابط' : 'Documents & control mapping'}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <AttachmentsField entityType="CCM_TEST" entityId={test.id} />
            <div style={{ borderTop: '1px solid var(--border)' }} />
            <ControlMappingPicker entityType="CCM_TEST" entityId={test.id} />
          </div>
        </Card>
      </div>

      {/* Edit-definition modal */}
      <Modal
        open={showEdit && form != null}
        onClose={() => { if (!saving) setShowEdit(false); }}
        size="lg"
        title={lang === 'ar' ? `تعديل ${test.testCode}` : `Edit ${test.testCode}`}
        footer={
          <>
            <Button variant="ghost" onClick={() => setShowEdit(false)} disabled={saving}>
              {lang === 'ar' ? 'إلغاء' : 'Cancel'}
            </Button>
            <Button variant="primary" Icon={Pencil} onClick={() => void submitEdit()} disabled={saving}>
              {saving ? (lang === 'ar' ? 'جارٍ الحفظ…' : 'Saving…') : (lang === 'ar' ? 'حفظ التغييرات' : 'Save changes')}
            </Button>
          </>
        }
      >
        {form && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Field label={lang === 'ar' ? 'العنوان (إنجليزي) *' : 'Title (EN) *'}>
                <input style={ctrl} value={form.titleEn} onChange={e => set('titleEn', e.target.value)} />
              </Field>
              <Field label={lang === 'ar' ? 'العنوان (عربي)' : 'Title (AR)'}>
                <input style={ctrl} value={form.titleAr} onChange={e => set('titleAr', e.target.value)} dir="rtl" />
              </Field>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Field label={lang === 'ar' ? 'نوع الاختبار *' : 'Test type *'}>
                <select style={ctrl} value={form.testType} onChange={e => set('testType', e.target.value as CcmTestType)}>
                  {(Object.keys(CCM_TEST_TYPE_LABEL) as CcmTestType[]).map(k => (
                    <option key={k} value={k}>{CCM_TEST_TYPE_LABEL[k][lang === 'ar' ? 'ar' : 'en']}</option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'النظام المستهدف *' : 'Target system *'}>
                <select style={ctrl} value={form.targetSystemType} onChange={e => set('targetSystemType', e.target.value as CcmTargetSystem)}>
                  {(Object.keys(CCM_TARGET_SYSTEM_LABEL) as CcmTargetSystem[]).map(k => (
                    <option key={k} value={k}>{CCM_TARGET_SYSTEM_LABEL[k][lang === 'ar' ? 'ar' : 'en']}</option>
                  ))}
                </select>
              </Field>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
              <Field label={lang === 'ar' ? 'الخطورة' : 'Severity'}>
                <select style={ctrl} value={form.severity} onChange={e => set('severity', e.target.value as CcmSeverity)}>
                  {(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as CcmSeverity[]).map(k => (
                    <option key={k} value={k}>{CCM_SEVERITY_LABEL[k][lang === 'ar' ? 'ar' : 'en']}</option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'الجدول (cron)' : 'Schedule (cron)'}>
                <input style={ctrl} value={form.frequencyCron} onChange={e => set('frequencyCron', e.target.value)} dir="ltr" />
              </Field>
              <Field label={lang === 'ar' ? 'المهلة (ثانية)' : 'Timeout (sec)'}>
                <input style={ctrl} type="number" min={1} value={form.timeoutSeconds} onChange={e => set('timeoutSeconds', e.target.value)} />
              </Field>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Field label={lang === 'ar' ? 'الوصف (إنجليزي)' : 'Description (EN)'}>
                <input style={ctrl} value={form.descEn} onChange={e => set('descEn', e.target.value)} />
              </Field>
              <Field label={lang === 'ar' ? 'الوصف (عربي)' : 'Description (AR)'}>
                <input style={ctrl} value={form.descAr} onChange={e => set('descAr', e.target.value)} dir="rtl" />
              </Field>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Field label={lang === 'ar' ? 'إعدادات النظام (JSON)' : 'Target config (JSON)'}>
                <textarea style={{ ...ctrl, minHeight: 70, resize: 'vertical', fontFamily: 'var(--font-mono, monospace)' }}
                  value={form.targetConfigJson} onChange={e => set('targetConfigJson', e.target.value)} dir="ltr" />
              </Field>
              <Field label={lang === 'ar' ? 'الشرط المتوقع (JSON)' : 'Expected condition (JSON)'}>
                <textarea style={{ ...ctrl, minHeight: 70, resize: 'vertical', fontFamily: 'var(--font-mono, monospace)' }}
                  value={form.expectedConditionJson} onChange={e => set('expectedConditionJson', e.target.value)} dir="ltr" />
              </Field>
            </div>
          </div>
        )}
      </Modal>
    </Shell>
  );
}

const ctrl: CSSProperties = {
  width: '100%', padding: '7px 9px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 6,
  background: 'var(--surface)', color: 'var(--ink-1)', outline: 'none',
};

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
      <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--ink-2)' }}>{label}</span>
      {children}
    </label>
  );
}

interface KpiProps { label: string; value: React.ReactNode }
function Kpi({ label, value }: KpiProps) {
  return (
    <div style={{ padding: 12, border: '1px solid var(--border)', borderRadius: 6,
                  background: 'var(--surface)' }}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: 0.4 }}>
        {label}
      </div>
      <div style={{ fontSize: 16, fontWeight: 600, marginTop: 4 }}>{value}</div>
    </div>
  );
}

interface SparklineProps { runs: TestRunResponse[] }
function Sparkline({ runs }: SparklineProps) {
  if (runs.length === 0) {
    return <InlineEmpty>—</InlineEmpty>;
  }
  // runs are sorted DESC by runAt — reverse for chronological order.
  const points = [...runs].reverse();
  return (
    <div style={{ display: 'flex', gap: 2, alignItems: 'flex-end', height: 40 }}>
      {points.map((r, i) => {
        const tone = toneFor(r.status);
        const height = r.status === 'PASS' ? 30 : r.status === 'FAIL' ? 38 : 20;
        return <span
          key={i}
          title={`${r.runAt} — ${r.status}`}
          style={{
            display: 'inline-block',
            width: 10,
            height,
            background: tone,
            borderRadius: 2,
          }} />;
      })}
    </div>
  );
}

function toneFor(status: CcmRunStatus): string {
  switch (status) {
    case 'PASS':    return 'var(--success, #10b981)';
    case 'FAIL':    return 'var(--danger,  #ef4444)';
    case 'ERROR':
    case 'TIMEOUT': return 'var(--warning, #f59e0b)';
    case 'SKIPPED': return 'var(--text-muted, #9ca3af)';
    default:        return 'var(--text-muted, #9ca3af)';
  }
}
