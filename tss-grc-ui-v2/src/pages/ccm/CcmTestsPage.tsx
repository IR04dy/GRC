import { useEffect, useMemo, useState } from 'react';
import { ClipboardCheck, Play, ToggleLeft, ToggleRight } from 'lucide-react';
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
  type TestDefinitionResponse, type CcmTestType, type CcmSeverity, type CcmRunStatus,
} from '../../lib/ccmEngine';

/**
 * CCM Tests register — Sprint 11.
 *
 * Filterable list of every test definition for the current tenant.
 * Per-row "Run now" and "Enable / Disable" buttons (role-gated by the
 * backend; the UI is permissive and falls back to a toast on 403).
 */
export function CcmTestsPage() {
  const { lang } = useI18n();
  const toast = useToast();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<TestDefinitionResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

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
        : <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>—</span>,
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
        actions={demoMode ? <Badge tone="warning">{lang === 'ar' ? 'وضع العرض' : 'Demo mode'}</Badge> : null}
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
    </Shell>
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
      <label style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4 }}>
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
          color: 'var(--text)',
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
