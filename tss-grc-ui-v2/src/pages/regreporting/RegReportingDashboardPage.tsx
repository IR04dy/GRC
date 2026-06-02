import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  CalendarDays, AlertTriangle, CheckCircle2, Clock, Plus,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  regreportingApi,
  FILING_STATUS_LABEL, FILING_STATUS_TONE, REGULATOR_LABEL,
  type DashboardResponse, type FilingResponse,
} from '../../lib/regreporting';

/**
 * Regulatory Reporting Calendar dashboard — /regreporting/dashboard.
 *
 * Shows what a Compliance Officer needs first thing in the morning:
 * active obligation count, in-flight filings, what's due in the next
 * 7 days, what's already overdue, plus a regulator-by-regulator
 * breakdown of in-flight work.
 */
export function RegReportingDashboardPage() {
  const { lang } = useI18n();
  const nav = useNavigate();
  const demoMode = !isAuthenticated();

  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const r = await regreportingApi.dashboard();
        if (!cancelled) setData(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar'
              ? 'تعذّر تحميل لوحة تقويم البلاغات'
              : 'Failed to load Regulatory Reporting dashboard'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filingCols = (overdue: boolean): Column<FilingResponse>[] => [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference',
      width: '220px', mono: true,
      cell: f => <span style={{ fontSize: 12 }}>{f.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'الجهة' : 'Regulator', width: '110px',
      cell: f => f.regulator ? (
        <Badge tone="info">
          {REGULATOR_LABEL[f.regulator][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ) : <span>—</span>,
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: f => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {(lang === 'ar' ? f.obligationTitleAr : f.obligationTitleEn) || '—'}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '150px',
      cell: f => (
        <Badge tone={FILING_STATUS_TONE[f.status]}>
          {FILING_STATUS_LABEL[f.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الاستحقاق' : 'Due', width: '180px',
      cell: f => {
        const dueStr = f.dueDate
          ? new Date(f.dueDate)
              .toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')
          : '—';
        const days = f.daysUntilDue;
        let badge: React.ReactNode = null;
        if (overdue || (days != null && days < 0)) {
          badge = <Badge tone="danger" className="m-l-6">
            {Math.abs(days ?? 0)} {lang === 'ar' ? 'يوم متأخّر' : 'days late'}
          </Badge>;
        } else if (days != null && days <= 3) {
          badge = <Badge tone="warning" className="m-l-6">
            {days === 0
              ? (lang === 'ar' ? 'اليوم' : 'today')
              : `${days} ${lang === 'ar' ? 'أيام' : 'd'}`}
          </Badge>;
        } else if (days != null && days <= 7) {
          badge = <Badge tone="info" className="m-l-6">
            {days} {lang === 'ar' ? 'أيام' : 'd'}
          </Badge>;
        }
        return (
          <span style={{ fontSize: 12 }}>
            {dueStr}{badge}
          </span>
        );
      },
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'تقويم البلاغات التنظيمية'
                            : 'Regulatory Reporting Calendar'}
      sub={lang === 'ar'
        ? 'متابعة كل التقارير الإلزامية لساما وهيئة الأمن السيبراني وهيئة السوق المالية وزاتكا وسدايا.'
        : 'Track every mandatory SAMA, NCA, CMA, ZATCA, and SDAIA return.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'تقويم البلاغات · المرحلة ١٦'
                                : 'Reporting Calendar · Sprint 16'}
        title={lang === 'ar' ? 'لوحة تقويم البلاغات'
                              : 'Reporting Calendar Dashboard'}
        sub={lang === 'ar'
          ? 'التقارير المُلزمة من جميع الجهات الرقابية السعودية'
          : 'Mandatory returns across all Saudi regulators'}
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="ghost"
              onClick={() => nav('/regreporting/obligations')}>
              {lang === 'ar' ? 'كتالوج التقارير' : 'Obligation catalog'}
            </Button>
            <Button variant="primary" Icon={Plus}
              onClick={() => nav('/regreporting/filings')}>
              {lang === 'ar' ? 'سجل التقديمات' : 'Filings register'}
            </Button>
          </div>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      {/* KPIs */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12,
        marginBottom: 16,
      }}>
        <Kpi Icon={CalendarDays} tone="info"
          label={lang === 'ar' ? 'تقارير نشطة' : 'Active obligations'}
          value={data?.activeObligationCount ?? 0}
          to="/regreporting/obligations" />
        <Kpi Icon={Clock} tone="warning"
          label={lang === 'ar' ? 'تستحق خلال ٧ أيام' : 'Due within 7 days'}
          value={data?.dueWithin7Count ?? 0}
          to="/regreporting/filings" />
        <Kpi Icon={AlertTriangle} tone="danger"
          label={lang === 'ar' ? 'متأخّرة' : 'Overdue'}
          value={data?.overdueCount ?? 0}
          to="/regreporting/filings" />
        <Kpi Icon={CheckCircle2} tone="success"
          label={lang === 'ar' ? 'مُقدَّمة هذا الربع' : 'Submitted this quarter'}
          value={data?.submittedThisQuarterCount ?? 0} />
      </div>

      {/* In-flight by regulator + status */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        gap: 12,
        marginBottom: 16,
      }}>
        <Card padded
          title={lang === 'ar' ? 'قيد التنفيذ — حسب الجهة'
                                : 'In-flight — by regulator'}>
          {loading ? (
            <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          ) : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {Object.entries(data?.inFlightByRegulator ?? {}).map(([reg, n]) => (
                <Badge key={reg} tone="info">
                  {REGULATOR_LABEL[reg as keyof typeof REGULATOR_LABEL]?.[
                    lang === 'ar' ? 'ar' : 'en'] ?? reg}: {n}
                </Badge>
              ))}
              {Object.keys(data?.inFlightByRegulator ?? {}).length === 0 && (
                <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? 'لا توجد تقارير قيد التنفيذ.'
                                  : 'No in-flight filings.'}
                </span>
              )}
            </div>
          )}
        </Card>

        <Card padded
          title={lang === 'ar' ? 'قيد التنفيذ — حسب الحالة'
                                : 'In-flight — by status'}>
          {loading ? (
            <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          ) : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {Object.entries(data?.inFlightByStatus ?? {}).map(([s, n]) => (
                <Badge key={s} tone={
                  FILING_STATUS_TONE[s as keyof typeof FILING_STATUS_TONE]
                    ?? 'neutral'}>
                  {FILING_STATUS_LABEL[s as keyof typeof FILING_STATUS_LABEL]
                    ?.[lang === 'ar' ? 'ar' : 'en'] ?? s}: {n}
                </Badge>
              ))}
              {Object.keys(data?.inFlightByStatus ?? {}).length === 0 && (
                <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? 'لا توجد تقارير قيد التنفيذ.'
                                  : 'No in-flight filings.'}
                </span>
              )}
            </div>
          )}
        </Card>
      </div>

      {/* Upcoming next 14 days */}
      <Card padded
        title={lang === 'ar' ? 'القادمة خلال ١٤ يوماً'
                              : 'Upcoming — next 14 days'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={filingCols(false)}
            rows={data?.upcoming ?? []}
            rowHref={f => `/regreporting/filings/${f.id}`}
            empty={<EmptyState
              Icon={CalendarDays}
              title={lang === 'ar' ? 'لا تقارير قادمة'
                                    : 'No upcoming filings'}
              sub={lang === 'ar'
                ? 'كل التقارير المُجدوَلة تستحق بعد أكثر من أسبوعين.'
                : 'Every scheduled filing is more than two weeks out.'} />}
          />
        )}
      </Card>

      {/* Overdue */}
      <div style={{ marginTop: 16 }}>
        <Card padded
          title={lang === 'ar' ? 'متأخّرة — تحتاج تدخّل فوري'
                                : 'Overdue — needs immediate attention'}>
          {loading ? (
            <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          ) : (
            <DataTable
              columns={filingCols(true)}
              rows={data?.overdueRecent ?? []}
              rowHref={f => `/regreporting/filings/${f.id}`}
              empty={<EmptyState
                Icon={CheckCircle2}
                title={lang === 'ar' ? 'لا تقارير متأخّرة'
                                      : 'No overdue filings'}
                sub={lang === 'ar' ? 'الحالة نظيفة عبر جميع الجهات.'
                                    : 'Clean across every regulator.'} />}
            />
          )}
        </Card>
      </div>
    </Shell>
  );
}
