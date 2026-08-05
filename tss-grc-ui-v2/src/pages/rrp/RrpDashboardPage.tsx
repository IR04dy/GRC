import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Shield, AlertTriangle, CheckCircle2, Banknote, Plus,
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
  rrpApi,
  CYCLE_STATUS_LABEL, CYCLE_STATUS_TONE,
  type DashboardResponse, type CycleResponse,
} from '../../lib/rrp';

/**
 * Recovery & Resolution Plan dashboard — /rrp/dashboard.
 *
 * What the CRO + CFO need first thing: active cycle count, total
 * recovery capacity on the menu, indicators in AMBER/RED, recent
 * cycles + status breakdown.
 */
export function RrpDashboardPage() {
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
        const r = await rrpApi.dashboard();
        if (!cancelled) setData(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar'
              ? 'تعذّر تحميل لوحة خطّة الاستعادة'
              : 'Failed to load RRP dashboard'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cols: Column<CycleResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference',
      width: '160px', mono: true,
      cell: c => <span style={{ fontSize: 12 }}>{c.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'السنة' : 'FY', width: '70px',
      cell: c => <strong>{c.fiscalYear}</strong>,
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: c => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? c.titleAr : c.titleEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '150px',
      cell: c => (
        <Badge tone={CYCLE_STATUS_TONE[c.status]}>
          {CYCLE_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الطاقة' : 'Capacity', width: '140px',
      cell: c => (
        <span style={{ fontSize: 12 }}>
          {(c.totalRecoveryCapacitySarM ?? 0).toLocaleString(
            lang === 'ar' ? 'ar-SA' : 'en-US')}{' '}
          {lang === 'ar' ? 'مليون ريال' : 'SAR M'}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'المؤشّرات' : 'Indicators', width: '160px',
      cell: c => (
        <span style={{ fontSize: 12 }}>
          {(c.indicatorsRed ?? 0) > 0 && (
            <Badge tone="danger" className="m-r-6">
              {c.indicatorsRed} {lang === 'ar' ? 'أحمر' : 'red'}
            </Badge>
          )}
          {(c.indicatorsAmber ?? 0) > 0 && (
            <Badge tone="warning" className="m-r-6">
              {c.indicatorsAmber} {lang === 'ar' ? 'كهرماني' : 'amber'}
            </Badge>
          )}
          <span>{c.indicatorCount ?? 0} {lang === 'ar' ? 'إجمالي' : 'total'}</span>
        </span>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'خطّة الاستعادة والحلّ' : 'Recovery & Resolution Plan'}
      sub={lang === 'ar'
        ? 'الخطّة السنوية الإلزامية لساما — الاستعادة والحلّ.'
        : 'SAMA mandatory annual Recovery & Resolution Plan.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الاستعادة والحلّ · المرحلة ١٧'
                                : 'RRP · Sprint 17'}
        title={lang === 'ar' ? 'لوحة الاستعادة والحلّ' : 'RRP Dashboard'}
        sub={lang === 'ar'
          ? 'الخطّة السنوية الإلزامية لساما — الوظائف الحيوية، خيارات الاستعادة، ومؤشّرات الإنذار'
          : 'SAMA mandatory plan — critical functions, recovery options, early-warning indicators'}
        actions={
          <Button onClick={() => nav('/rrp/cycles')}
                  variant="primary" Icon={Plus}>
            {lang === 'ar' ? 'الدورات' : 'Cycles'}
          </Button>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12,
        marginBottom: 16,
      }}>
        <Kpi Icon={Shield} tone="info"
          label={lang === 'ar' ? 'دورات نشطة' : 'Active cycles'}
          value={data?.activeCycleCount ?? 0}
          to="/rrp/cycles" />
        {/* F2-132 — placed beside "Active cycles" on purpose: an overdue cycle is counted in
            that tile too, so the two must be read together. Nothing in this module compared
            submissionDueDate to anything before this. */}
        <Kpi Icon={AlertTriangle} tone={(data?.overdueCycleCount ?? 0) > 0 ? 'danger' : 'neutral'}
          label={lang === 'ar' ? 'متأخّرة عن موعد التقديم' : 'Past submission due date'}
          value={data?.overdueCycleCount ?? 0}
          to="/rrp/cycles" />
        <Kpi Icon={CheckCircle2} tone="success"
          label={lang === 'ar' ? 'مُقدَّمة لساما' : 'Submitted to SAMA'}
          value={data?.submittedCycleCount ?? 0}
          to="/rrp/cycles" />
        <Kpi Icon={AlertTriangle} tone="danger"
          label={lang === 'ar' ? 'مؤشّرات أحمر/كهرماني' : 'Amber / Red indicators'}
          value={(data?.indicatorsRed ?? 0) + (data?.indicatorsAmber ?? 0)}
          to="/rrp/cycles" />
        <Kpi Icon={Banknote} tone="info"
          label={lang === 'ar' ? 'طاقة الاستعادة' : 'Recovery capacity'}
          value={(data?.currentRecoveryCapacitySarM ?? 0).toLocaleString(
            lang === 'ar' ? 'ar-SA' : 'en-US') + ' ' +
            (lang === 'ar' ? 'مليون' : 'SAR M')}
          to="/rrp/cycles" />
      </div>

      {/* Status breakdown */}
      <Card padded
        title={lang === 'ar' ? 'توزيع الدورات حسب الحالة'
                              : 'Cycles by status'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {Object.entries(data?.byStatus ?? {}).map(([s, n]) => (
              <Badge key={s} tone={
                CYCLE_STATUS_TONE[s as keyof typeof CYCLE_STATUS_TONE]
                  ?? 'neutral'}>
                {CYCLE_STATUS_LABEL[s as keyof typeof CYCLE_STATUS_LABEL]
                  ?.[lang === 'ar' ? 'ar' : 'en'] ?? s}: {n}
              </Badge>
            ))}
            {Object.keys(data?.byStatus ?? {}).length === 0 && (
              <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                {lang === 'ar' ? 'لا توجد دورات بعد.' : 'No cycles yet.'}
              </span>
            )}
          </div>
        )}
      </Card>

      <div style={{ marginTop: 16 }}>
        <Card padded
          title={lang === 'ar' ? 'أحدث الدورات' : 'Recent cycles'}>
          {loading ? (
            <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          ) : (
            <DataTable
              columns={cols}
              rows={data?.recentCycles ?? []}
              rowHref={c => `/rrp/cycles/${c.id}`}
              empty={<EmptyState
                Icon={Shield}
                title={lang === 'ar' ? 'لا توجد دورات' : 'No cycles yet'}
                sub={demoMode
                  ? (lang === 'ar' ? 'سجّل دخولك لعرض الدورات.' : 'Log in to view cycles.')
                  : (lang === 'ar'
                    ? 'افتح دورة جديدة للسنة المالية القادمة.'
                    : 'Open a new RRP cycle for the upcoming fiscal year.')} />}
            />
          )}
        </Card>
      </div>
    </Shell>
  );
}
