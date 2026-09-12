import { useEffect, useState } from 'react';
import {
  ShieldAlert, Search, AlertTriangle, CheckCircle2, Wallet,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  fraudApi,
  CASE_STATUS_LABEL, CASE_STATUS_TONE,
  SEVERITY_LABEL, SEVERITY_TONE,
  type CaseResponse, type DashboardResponse,
} from '../../lib/fraud';

/**
 * Fraud Risk Dashboard — /fraud/dashboard.
 *
 * SAMA Anti-Fraud Framework 2018 (Art.4-9) + SAMA AML/CFT Art.25 +
 * SAMA Cyber Subdomain 3.3.13. Operator-natural Arabic title:
 * "لوحة مخاطر الاحتيال". Renders 5 KPI tiles + a "Recent cases"
 * register with row-click drill-down to the detail page.
 */
export function FraudDashboardPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) { setData(null); return; }
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const r = await fraudApi.dashboard();
        if (!cancelled) setData(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل لوحة الاحتيال' : 'Failed to load fraud dashboard'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const sarFmt = new Intl.NumberFormat(lang === 'ar' ? 'ar-SA' : 'en-SA', {
    style: 'currency', currency: 'SAR', maximumFractionDigits: 0,
  });

  const cols: Column<CaseResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'External Ref', width: '140px', mono: true,
      cell: c => <span style={{ fontSize: 12 }}>{c.externalReference}</span>,
    },
    {
      header: lang === 'ar' ? 'الموضوع' : 'Subject',
      cell: c => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? c.subjectAr : c.subjectEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'النمط' : 'Typology', width: '160px',
      cell: c => <code style={{ fontSize: 12 }}>{c.typologyCodeSnapshot}</code>,
    },
    {
      header: lang === 'ar' ? 'الخطورة' : 'Severity', width: '110px',
      cell: c => (
        <Badge tone={SEVERITY_TONE[c.severity]}>
          {SEVERITY_LABEL[c.severity][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: c => (
        <Badge tone={CASE_STATUS_TONE[c.status]}>
          {CASE_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'تاريخ الرصد' : 'Detected At', width: '130px',
      cell: c => (
        <span style={{ fontSize: 12 }}>
          {new Date(c.detectedAt).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
        </span>
      ),
    },
  ];

  const recent = data?.recentCases ?? [];

  return (
    <Shell
      title={lang === 'ar' ? 'لوحة مخاطر الاحتيال' : 'Fraud Risk Dashboard'}
      sub={lang === 'ar'
        ? 'بحسب إطار مكافحة الاحتيال 2018 (ساما) — المواد 4 إلى 9.'
        : 'SAMA Anti-Fraud Framework 2018 — Art.4-9.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'مكافحة الاحتيال' : 'Fraud Risk'}
        title={lang === 'ar' ? 'لوحة مخاطر الاحتيال' : 'Fraud Risk Dashboard'}
        sub={lang === 'ar'
          ? 'بحسب إطار مكافحة الاحتيال 2018 (ساما)'
          : 'SAMA Anti-Fraud Framework 2018'}
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {/* KPIs */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12,
        marginBottom: 16,
      }}>
        <Kpi Icon={ShieldAlert} tone="warning"
          label={lang === 'ar' ? 'قضايا مفتوحة' : 'Open Cases'}
          value={data?.openCases ?? 0}
          to="/fraud/cases" />
        <Kpi Icon={Search} tone="info"
          label={lang === 'ar' ? 'قيد التحقيق' : 'Investigating'}
          value={data?.investigatingCount ?? 0}
          to="/fraud/cases" />
        <Kpi Icon={AlertTriangle} tone="danger"
          label={lang === 'ar' ? 'قضايا مؤكَّدة (السنة الجارية)' : 'Confirmed (YTD)'}
          value={data?.confirmedCount ?? 0}
          to="/fraud/cases" />
        <Kpi Icon={CheckCircle2} tone="neutral"
          label={lang === 'ar' ? 'قضايا مغلقة (السنة الجارية)' : 'Closed (YTD)'}
          value={data?.closedCount ?? 0}
          to="/fraud/cases" />
        <Kpi Icon={Wallet} tone="danger"
          label={lang === 'ar' ? 'الخسائر المؤكَّدة (السنة الجارية)' : 'YTD Confirmed Loss'}
          value={sarFmt.format(data?.confirmedLossYtdSar ?? 0)}
          to="/fraud/cases" />
      </div>

      {/* Recent cases */}
      <Card padded title={lang === 'ar' ? 'أحدث القضايا' : 'Recent cases'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cols}
            rows={recent}
            rowHref={c => `/fraud/cases/${c.id}`}
            empty={<EmptyState
              Icon={ShieldAlert}
              title={lang === 'ar' ? 'لا توجد قضايا حديثة' : 'No recent cases'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض قضايا الاحتيال.' : 'Log in to view fraud cases.')
                : (lang === 'ar'
                  ? 'لم تُسجَّل أي قضية احتيال بعد. ستظهر هنا أحدث القضايا فور رصدها.'
                  : 'No fraud cases recorded yet. New cases will appear here as they are detected.')} />}
          />
        )}
      </Card>
    </Shell>
  );
}
