import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Cloud, Building2, ShieldCheck, ShieldAlert, Database, LogOut } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  outsourcingCloudApi,
  ARRANGEMENT_STATUS_LABEL, ARRANGEMENT_STATUS_TONE,
  SERVICE_CATEGORY_LABEL, MATERIALITY_LABEL,
  DATA_CLASSIFICATION_LABEL, DATA_RESIDENCY_LABEL,
  type DashboardResponse, type ArrangementResponse,
} from '../../lib/outsourcingCloud';

/**
 * Sprint 30 Frontend — Outsourcing & Cloud Governance dashboard.
 * SAMA Outsourcing Reg §3-§8 + SAMA Cloud Computing Framework.
 * Single workspace for Risk Officer + Compliance + CISO + CIO.
 */
export function OutsourcingDashboardPage() {
  const { lang } = useI18n();
  const nav = useNavigate();
  const demoMode = !isAuthenticated();

  const [data, setData] = useState<DashboardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setError(null);
      try {
        const r = await outsourcingCloudApi.dashboard();
        if (!cancelled) setData(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل لوحة حوكمة الإسناد والسحابة'
                            : 'Failed to load Outsourcing dashboard'));
        }
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cols: Column<ArrangementResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '120px', mono: true,
      cell: a => <span style={{ fontSize: 12 }}>{a.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'المزوّد' : 'Provider',
      cell: a => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? a.providerLegalNameAr : a.providerLegalNameEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الفئة' : 'Category', width: '130px',
      cell: a => (
        <Badge tone="info">
          {SERVICE_CATEGORY_LABEL[a.serviceCategory][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الجوهرية' : 'Materiality', width: '110px',
      cell: a => (
        <Badge tone={a.materiality === 'MATERIAL' ? 'warning' : 'neutral'}>
          {MATERIALITY_LABEL[a.materiality][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: a => (
        <Badge tone={ARRANGEMENT_STATUS_TONE[a.status]}>
          {ARRANGEMENT_STATUS_LABEL[a.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={lang === 'ar' ? 'حوكمة الإسناد والسحابة' : 'Outsourcing & Cloud Governance'}
        sub={lang === 'ar'
          ? 'تنظيم ساما للإسناد §٣-§٨ + إطار ساما للحوسبة السحابية'
          : 'SAMA Outsourcing Regulation §3-§8 + SAMA Cloud Computing Framework'}
      />

      {error && (
        <section style={{ padding: 12, marginBottom: 16, borderInlineStart: '4px solid var(--danger)', borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <strong>{lang === 'ar' ? 'خطأ' : 'Error'}:</strong> {error}
        </section>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12, marginBottom: 16 }}>
        <Kpi
          label={lang === 'ar' ? 'ترتيبات الإسناد' : 'Arrangements'}
          value={data?.arrangementsTotal ?? 0}
          Icon={Building2}
        />
        <Kpi
          label={lang === 'ar' ? 'جوهرية فعّالة' : 'Material active'}
          value={data?.materialActive ?? 0}
          tone="warning"
          Icon={ShieldAlert}
        />
        <Kpi
          label={lang === 'ar' ? 'نافذة' : 'Live'}
          value={data?.arrangementsLive ?? 0}
          tone="success"
          Icon={ShieldCheck}
        />
        <Kpi
          label={lang === 'ar' ? 'خدمات سحابية' : 'Cloud services'}
          value={data?.cloudServicesTotal ?? 0}
          Icon={Cloud}
        />
        <Kpi
          label={lang === 'ar' ? 'سرّية+ (T3/T4)' : 'T3/T4 services'}
          value={data?.cloudServicesT3T4 ?? 0}
          tone="warning"
          Icon={Database}
        />
        <Kpi
          label={lang === 'ar' ? 'محظورة' : 'Blocked'}
          value={data?.cloudServicesBlocked ?? 0}
          tone="danger"
          Icon={ShieldAlert}
        />
        <Kpi
          label={lang === 'ar' ? 'مراجعات متأخرة' : 'Reviews overdue'}
          value={data?.reviewsOverdue ?? 0}
          tone={data && data.reviewsOverdue > 0 ? 'danger' : 'neutral'}
          Icon={ShieldAlert}
        />
        <Kpi
          label={lang === 'ar' ? 'خطط خروج جاهزة' : 'Exit plans ready'}
          value={data?.exitPlansReady ?? 0}
          tone="info"
          Icon={LogOut}
        />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12, marginBottom: 16 }}>
        <section style={{ padding: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <h3 style={{ marginTop: 0, fontSize: 14 }}>
            {lang === 'ar' ? 'الترتيبات حسب الحالة' : 'Arrangements by status'}
          </h3>
          {data?.arrangementsByStatus && Object.entries(data.arrangementsByStatus).length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {Object.entries(data.arrangementsByStatus).map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                  <Badge tone={ARRANGEMENT_STATUS_TONE[k as keyof typeof ARRANGEMENT_STATUS_TONE] ?? 'neutral'}>
                    {ARRANGEMENT_STATUS_LABEL[k as keyof typeof ARRANGEMENT_STATUS_LABEL]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}
                  </Badge>
                  <strong>{v}</strong>
                </div>
              ))}
            </div>
          ) : <InlineEmpty />}
        </section>

        <section style={{ padding: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <h3 style={{ marginTop: 0, fontSize: 14 }}>
            {lang === 'ar' ? 'السحابة حسب التصنيف' : 'Cloud by classification'}
          </h3>
          {data?.cloudServicesByClassification && Object.entries(data.cloudServicesByClassification).length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {Object.entries(data.cloudServicesByClassification).map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                  <Badge tone={k === 'T4' ? 'danger' : k === 'T3' ? 'warning' : 'info'}>
                    {DATA_CLASSIFICATION_LABEL[k as keyof typeof DATA_CLASSIFICATION_LABEL]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}
                  </Badge>
                  <strong>{v}</strong>
                </div>
              ))}
            </div>
          ) : <InlineEmpty />}
        </section>

        <section style={{ padding: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <h3 style={{ marginTop: 0, fontSize: 14 }}>
            {lang === 'ar' ? 'السحابة حسب مقرّ البيانات' : 'Cloud by residency'}
          </h3>
          {data?.cloudServicesByResidency && Object.entries(data.cloudServicesByResidency).length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {Object.entries(data.cloudServicesByResidency).map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                  <Badge tone={k === 'IN_KINGDOM' ? 'success' : k === 'GCC' ? 'info' : 'warning'}>
                    {DATA_RESIDENCY_LABEL[k as keyof typeof DATA_RESIDENCY_LABEL]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}
                  </Badge>
                  <strong>{v}</strong>
                </div>
              ))}
            </div>
          ) : <InlineEmpty />}
        </section>
      </div>

      <section style={{ padding: 16, marginBottom: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <h3 style={{ margin: 0, fontSize: 14 }}>
            {lang === 'ar' ? 'أحدث الترتيبات' : 'Recent arrangements'}
          </h3>
          <Button onClick={() => nav('/outsourcing/arrangements')} variant="ghost">
            {lang === 'ar' ? 'عرض السجل ←' : '← View register'}
          </Button>
        </div>
        <DataTable
          columns={cols}
          rows={data?.recentArrangements ?? []}
          rowHref={a => `/outsourcing/arrangements/${a.id}`}
          empty={<InlineEmpty />}
        />
      </section>

      {data?.overdueReviews && data.overdueReviews.length > 0 && (
        <section style={{ padding: 16, borderInlineStart: '4px solid var(--danger)', borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <h3 style={{ margin: '0 0 12px 0', fontSize: 14 }}>
            {lang === 'ar' ? 'مراجعات متأخرة (AR-269)' : 'Overdue reviews (AR-269)'}
          </h3>
          <DataTable
            columns={cols}
            rows={data.overdueReviews}
            rowHref={a => `/outsourcing/arrangements/${a.id}`}
          />
        </section>
      )}
    </Shell>
  );
}
