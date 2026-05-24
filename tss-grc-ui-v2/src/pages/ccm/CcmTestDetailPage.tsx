import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  ClipboardCheck, Play, ToggleLeft, ToggleRight, Fingerprint,
  Activity,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
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
  type TestDefinitionResponse, type TestRunResponse, type CcmRunStatus,
} from '../../lib/ccmEngine';

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
                      style={{ fontSize: 11, color: 'var(--text-muted)' }}>
        <Fingerprint size={11} style={{ verticalAlign: '-1px', marginInlineEnd: 4 }} />
        {shortHash(r.evidenceHash)}
      </code>,
    },
    {
      header: lang === 'ar' ? 'السبب' : 'Reason',
      cell: r => r.failureReason
        ? <span style={{ fontSize: 12 }}>{r.failureReason}</span>
        : <span style={{ color: 'var(--text-muted)' }}>—</span>,
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
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>
              {lang === 'ar' ? 'إعدادات النظام المستهدف' : 'target_config_json'}
            </div>
            <pre style={{ background: 'var(--surface-2)', padding: 12, borderRadius: 6,
                          fontSize: 12, overflow: 'auto', margin: 0 }}>
              {prettyJson(test.targetConfigJson) || '—'}
            </pre>
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>
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
    </Shell>
  );
}

interface KpiProps { label: string; value: React.ReactNode }
function Kpi({ label, value }: KpiProps) {
  return (
    <div style={{ padding: 12, border: '1px solid var(--border)', borderRadius: 6,
                  background: 'var(--surface)' }}>
      <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4 }}>
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
