import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ClipboardList, FileText, AlertTriangle, CheckCircle2, Plus,
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
  reginspectApi,
  INSPECTION_STATUS_LABEL, INSPECTION_STATUS_TONE, REGULATOR_LABEL,
  type DashboardResponse, type InspectionResponse,
} from '../../lib/reginspect';

/**
 * Regulatory Inspection Dashboard — /reginspect/dashboard.
 *
 * Surfaces what an Inspection Coordinator needs first thing in the
 * morning: how many inspections are live, how many DRs open, how
 * many are overdue (= SLA breach with the regulator), and the most
 * recent inspections with status + DR counters.
 */
export function RegInspectDashboardPage() {
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
        const r = await reginspectApi.dashboard();
        if (!cancelled) setData(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar'
              ? 'تعذّر تحميل لوحة التفتيش الرقابي'
              : 'Failed to load Regulatory Inspection dashboard'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cols: Column<InspectionResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'External Ref',
      width: '180px', mono: true,
      cell: i => <span style={{ fontSize: 12 }}>{i.externalReference}</span>,
    },
    {
      header: lang === 'ar' ? 'الجهة' : 'Regulator', width: '120px',
      cell: i => (
        <Badge tone="info">
          {REGULATOR_LABEL[i.regulator][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: i => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? i.titleAr : i.titleEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '160px',
      cell: i => (
        <Badge tone={INSPECTION_STATUS_TONE[i.status]}>
          {INSPECTION_STATUS_LABEL[i.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الطلبات' : 'DRs', width: '120px',
      cell: i => (
        <span style={{ fontSize: 12 }}>
          <strong>{i.openDrCount ?? 0}</strong>{' '}
          {lang === 'ar' ? 'مفتوحة' : 'open'}
          {(i.overdueDrCount ?? 0) > 0 && (
            <Badge tone="danger" className="m-l-6">
              {i.overdueDrCount} {lang === 'ar' ? 'متأخّر' : 'overdue'}
            </Badge>
          )}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'تاريخ الافتتاح' : 'Entry', width: '120px',
      cell: i => (
        <span style={{ fontSize: 12 }}>
          {i.entryDate
            ? new Date(i.entryDate)
                .toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')
            : '—'}
        </span>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'لوحة التفتيش الرقابي'
                            : 'Regulatory Inspection Dashboard'}
      sub={lang === 'ar'
        ? 'ورشة إدارة تفتيش ساما وهيئة الأمن السيبراني وهيئة السوق المالية وسدايا.'
        : 'Workspace for SAMA / NCA / CMA / SDAIA on-site inspections.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'التفتيش الرقابي · المرحلة ١٥'
                                : 'Regulatory Inspection · Sprint 15'}
        title={lang === 'ar' ? 'لوحة التفتيش الرقابي'
                              : 'Regulatory Inspection Dashboard'}
        sub={lang === 'ar'
          ? 'إدارة جميع زيارات الجهات الرقابية الميدانية'
          : 'Manage all on-site regulatory visits'}
        actions={
          <Button onClick={() => nav('/reginspect/inspections')}
                  variant="primary" Icon={Plus}>
            {lang === 'ar' ? 'فتح ورشة جديدة' : 'New inspection'}
          </Button>
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
        <Kpi Icon={ClipboardList} tone="info"
          label={lang === 'ar' ? 'تفتيش نشط' : 'Active inspections'}
          value={data?.activeCount ?? 0}
          to="/reginspect/inspections" />
        <Kpi Icon={FileText} tone="warning"
          label={lang === 'ar' ? 'طلبات وثائق مفتوحة' : 'Open DRs'}
          value={data?.openDrCount ?? 0}
          to="/reginspect/inspections" />
        <Kpi Icon={AlertTriangle} tone="danger"
          label={lang === 'ar' ? 'طلبات متأخّرة' : 'Overdue DRs'}
          value={data?.overdueDrCount ?? 0}
          to="/reginspect/inspections" />
        <Kpi Icon={CheckCircle2} tone="success"
          label={lang === 'ar' ? 'تفتيش مغلق' : 'Closed inspections'}
          value={data?.closedCount ?? 0}
          to="/reginspect/inspections" />
      </div>

      {/* Recent inspections */}
      <Card padded
        title={lang === 'ar' ? 'أحدث ورش التفتيش'
                              : 'Recent inspections'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cols}
            rows={data?.recentInspections ?? []}
            rowHref={i => `/reginspect/inspections/${i.id}`}
            empty={<EmptyState
              Icon={ClipboardList}
              title={lang === 'ar' ? 'لا توجد ورش تفتيش'
                                    : 'No inspections yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض الورش.' : 'Log in to view inspections.')
                : (lang === 'ar'
                  ? 'افتح ورشة جديدة عند استلام إشعار تفتيش من إحدى الجهات الرقابية.'
                  : 'Open a new workspace when a regulator notifies you of an upcoming visit.')} />}
          />
        )}
      </Card>
    </Shell>
  );
}
