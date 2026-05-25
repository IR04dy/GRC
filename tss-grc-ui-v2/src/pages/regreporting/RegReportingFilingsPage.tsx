import { useEffect, useState } from 'react';
import { FileText } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  regreportingApi,
  REGULATOR_LABEL, FILING_STATUS_LABEL, FILING_STATUS_TONE,
  FREQUENCY_LABEL,
  type FilingResponse,
} from '../../lib/regreporting';

/**
 * Filings Register — /regreporting/filings.
 *
 * Every filing across every obligation, sorted by due date ascending.
 * Click any row to open the workspace.
 */
export function RegReportingFilingsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<FilingResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setLoading(true); setError(null);
      try {
        const r = await regreportingApi.listFilings(0, 200);
        if (!cancelled) setRows(r.content);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar'
              ? 'تعذّر تحميل سجل التقديمات'
              : 'Failed to load filings register'));
        }
      } finally { if (!cancelled) setLoading(false); }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cols: Column<FilingResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference',
      width: '230px', mono: true,
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
      header: lang === 'ar' ? 'الدورية' : 'Frequency', width: '110px',
      cell: f => f.frequency ? (
        <Badge tone="neutral">
          {FREQUENCY_LABEL[f.frequency][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ) : <span>—</span>,
    },
    {
      header: lang === 'ar' ? 'التقرير' : 'Obligation',
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
        const d = f.daysUntilDue;
        let extra: React.ReactNode = null;
        if (f.overdue) {
          extra = <Badge tone="danger" className="m-l-6">
            {Math.abs(d ?? 0)} {lang === 'ar' ? 'يوم متأخّر' : 'days late'}
          </Badge>;
        } else if (d != null && d >= 0 && d <= 7) {
          extra = <Badge tone={d <= 3 ? 'warning' : 'info'} className="m-l-6">
            {d === 0
              ? (lang === 'ar' ? 'اليوم' : 'today')
              : `${d} ${lang === 'ar' ? 'أيام' : 'd'}`}
          </Badge>;
        }
        return <span style={{ fontSize: 12 }}>{dueStr}{extra}</span>;
      },
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'سجل التقديمات' : 'Filings Register'}
      sub={lang === 'ar'
        ? 'كل دورات التقارير المُلزمة عبر جميع الجهات الرقابية.'
        : 'Every filing cycle across every regulator.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'تقويم البلاغات · المرحلة ١٦'
                                : 'Reporting Calendar · Sprint 16'}
        title={lang === 'ar' ? 'سجل التقديمات' : 'Filings Register'}
        sub={lang === 'ar' ? 'مرتّب حسب تاريخ الاستحقاق'
                            : 'Sorted by due date'}
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      <Card padded title={lang === 'ar' ? 'كل التقديمات' : 'All filings'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cols}
            rows={rows}
            rowHref={f => `/regreporting/filings/${f.id}`}
            empty={<EmptyState
              Icon={FileText}
              title={lang === 'ar' ? 'لا توجد تقديمات' : 'No filings yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض السجل.' : 'Log in to view the register.')
                : (lang === 'ar'
                  ? 'افتح دورة جديدة من صفحة كتالوج التقارير.'
                  : 'Generate a cycle from the obligation catalog page.')} />}
          />
        )}
      </Card>
    </Shell>
  );
}
