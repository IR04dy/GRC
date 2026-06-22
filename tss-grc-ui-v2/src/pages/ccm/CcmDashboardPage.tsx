import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity, ShieldCheck, AlertOctagon, AlertTriangle, ClipboardCheck,
  Fingerprint, BarChart3,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { isAuthenticated } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import {
  ccmApi,
  CCM_STATUS_LABEL, CCM_STATUS_TONE,
  CCM_SEVERITY_LABEL, CCM_SEVERITY_TONE,
  CCM_TARGET_SYSTEM_LABEL,
  shortHash,
  type DashboardSnapshot, type TestRunResponse, type FailingTestSummary,
} from '../../lib/ccmEngine';

/**
 * CCM Dashboard — Sprint 11 Continuous Control Monitoring engine.
 *
 * Saudi-native bilingual heading:
 *   ar: المراقبة المستمرة للضوابط  (not literal "المراقبة المتواصلة")
 *   en: Continuous Control Monitoring (CCM)
 *
 * Wired to GET /api/v1/ccm/dashboard. Tiles: total tests / enabled /
 * passing-last-24h / failing-last-24h / pass-rate-last-24h. Below that:
 * a 20-row recent-runs table and a "failing tests" panel grouped by
 * severity.
 */
export function CcmDashboardPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [snap, setSnap] = useState<DashboardSnapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    if (demoMode) {
      setSnap(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setSnap(await ccmApi.dashboard());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load CCM dashboard');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  const passRatePct = snap == null ? 0 : Math.round(snap.passRateLast24h * 100);

  const runCols: Column<TestRunResponse>[] = [
    {
      header: lang === 'ar' ? 'الوقت' : 'Time', width: '160px',
      cell: r => <span style={{ fontVariantNumeric: 'tabular-nums', fontSize: 12 }}>
        {r.runAt ? new Date(r.runAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB') : '—'}
      </span>,
    },
    {
      header: lang === 'ar' ? 'رمز الاختبار' : 'Test code', width: '220px',
      cell: r => <code style={{ fontSize: 12 }}>{r.testCode ?? '—'}</code>,
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
      header: lang === 'ar' ? 'النظام' : 'Target', width: '140px',
      cell: r => r.targetSystemType
        ? CCM_TARGET_SYSTEM_LABEL[r.targetSystemType][lang === 'ar' ? 'ar' : 'en']
        : '—',
    },
    {
      header: lang === 'ar' ? 'بصمة الدليل' : 'Evidence hash', width: '180px',
      cell: r => (
        <code title={r.evidenceHash ?? ''} style={{ fontSize: 11, color: 'var(--ink-3)' }}>
          <Fingerprint size={11} style={{ verticalAlign: '-1px', marginInlineEnd: 4 }} />
          {shortHash(r.evidenceHash)}
        </code>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'المراقبة المستمرة للضوابط' : 'Continuous Control Monitoring'}
      sub={lang === 'ar' ? 'SAMA · NCA ECC-2 · PDPL' : 'SAMA · NCA ECC-2 · PDPL'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الامتثال' : 'Compliance'}
        title={lang === 'ar' ? 'المراقبة المستمرة للضوابط' : 'Continuous Control Monitoring'}
        sub={lang === 'ar'
          ? 'اختبارات آليّة لضوابط الأمان والامتثال تعمل وفق جدول دوري وتُنتج دليلاً مُتحقَّقاً من سلامته (SHA-256) دون الحاجة إلى لقطات يدوية. يفي بمتطلبات المراقبة المستمرة وفق إطار SAMA للأمن السيبراني (القسم 2.6) والضابط 2-12 من ECC-2.'
          : 'Scheduled, automated control tests producing tamper-evident (SHA-256) evidence without manual screenshots. Satisfies SAMA CSF section 2.6 (Continuous Monitoring) and NCA ECC-2 control 2-12.'}
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            <Link to="/ccm/tests"
                  style={{ padding: '6px 12px', border: '1px solid var(--border)', borderRadius: 6,
                           textDecoration: 'none', color: 'var(--ink-1)', fontSize: 13 }}>
              <ClipboardCheck size={14} style={{ verticalAlign: '-2px', marginInlineEnd: 6 }} />
              {lang === 'ar' ? 'تعريفات الاختبارات' : 'Test definitions'}
            </Link>
            {demoMode && <Badge tone="warning">{lang === 'ar' ? 'وضع العرض' : 'Demo mode'}</Badge>}
          </div>
        }
      />

      <div className="kpi-grid-sm">
        <Kpi Icon={ClipboardCheck}
             label={lang === 'ar' ? 'إجمالي الاختبارات' : 'Total tests'}
             value={snap?.totalTests ?? 0} tone="primary" to="/ccm/tests" />
        <Kpi Icon={ShieldCheck}
             label={lang === 'ar' ? 'المُفعَّلة' : 'Enabled'}
             value={snap?.enabledTests ?? 0} tone="info" to="/ccm/tests" />
        <Kpi Icon={Activity}
             label={lang === 'ar' ? 'ناجح (24 ساعة)' : 'Passing (24h)'}
             value={snap?.passingLast24h ?? 0} tone="success" to="/ccm/tests" />
        <Kpi Icon={AlertOctagon}
             label={lang === 'ar' ? 'فاشل (24 ساعة)' : 'Failing (24h)'}
             value={snap?.failingLast24h ?? 0}
             tone={(snap?.failingLast24h ?? 0) > 0 ? 'danger' : 'success'} to="/ccm/tests" />
        <Kpi Icon={AlertTriangle}
             label={lang === 'ar' ? 'أخطاء (24 ساعة)' : 'Errors (24h)'}
             value={snap?.errorLast24h ?? 0}
             tone={(snap?.errorLast24h ?? 0) > 0 ? 'warning' : 'success'} to="/ccm/tests" />
        <Kpi Icon={BarChart3}
             label={lang === 'ar' ? 'نسبة النجاح (24 ساعة)' : 'Pass rate (24h)'}
             value={`${passRatePct}%`}
             tone={passRatePct >= 95 ? 'success' : passRatePct >= 80 ? 'warning' : 'danger'} to="/ccm/tests" />
      </div>

      {error && (
        <Card padded>
          <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
        </Card>
      )}

      <Card padded>
        <h3 style={{ marginTop: 0 }}>
          {lang === 'ar' ? 'آخر التشغيلات' : 'Recent runs'}
        </h3>
        {loading
          ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : <DataTable
              columns={runCols}
              rows={snap?.recentRuns ?? []}
              empty={<EmptyState
                Icon={Activity}
                title={lang === 'ar' ? 'لا توجد تشغيلات بعد' : 'No runs yet'}
                sub={lang === 'ar'
                  ? 'يبدأ الجدول في الامتلاء فور تفعيل أول اختبار وتشغيله.'
                  : 'The table will populate once tests are enabled and run.'} />}
            />
        }
      </Card>

      <Card padded>
        <h3 style={{ marginTop: 0 }}>
          {lang === 'ar' ? 'الاختبارات الفاشلة' : 'Failing tests'}
        </h3>
        <FailingPanel items={snap?.failingTests ?? []} lang={lang} />
      </Card>
    </Shell>
  );
}

interface FailingPanelProps {
  items: FailingTestSummary[];
  lang: 'en' | 'ar';
}

function FailingPanel({ items, lang }: FailingPanelProps) {
  if (items.length === 0) {
    return <InlineEmpty>
      {lang === 'ar' ? 'لا توجد اختبارات فاشلة حالياً.' : 'No failing tests right now.'}
    </InlineEmpty>;
  }
  // Group by severity (CRITICAL first).
  const order: Array<'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'> = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
  const groups = order.map(sev => ({
    sev, items: items.filter(i => i.severity === sev),
  })).filter(g => g.items.length > 0);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {groups.map(g => (
        <div key={g.sev}>
          <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6, color: 'var(--ink-3)' }}>
            <Badge tone={CCM_SEVERITY_TONE[g.sev]}>
              {CCM_SEVERITY_LABEL[g.sev][lang === 'ar' ? 'ar' : 'en']}
            </Badge>
            <span style={{ marginInlineStart: 8 }}>{g.items.length}</span>
          </div>
          <ul style={{ listStyle: 'none', padding: 0, margin: 0,
                       display: 'flex', flexDirection: 'column', gap: 4 }}>
            {g.items.map(i => (
              <li key={i.externalId}>
                <Link to={`/ccm/tests/${i.externalId}`}
                      style={{ display: 'flex', justifyContent: 'space-between',
                               padding: '6px 8px', border: '1px solid var(--border)',
                               borderRadius: 4, textDecoration: 'none', color: 'var(--ink-1)' }}>
                  <span>
                    <code style={{ fontSize: 12, color: 'var(--ink-3)' }}>{i.testCode}</code>
                    <span style={{ marginInlineStart: 8 }}>
                      {lang === 'ar' && i.titleAr ? i.titleAr : i.titleEn}
                    </span>
                  </span>
                  <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                    {lang === 'ar' ? `${i.consecutiveFailures} مرّات متتالية` : `${i.consecutiveFailures} consecutive`}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}
