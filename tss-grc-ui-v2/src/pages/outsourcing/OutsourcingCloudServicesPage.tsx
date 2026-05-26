import { useEffect, useState } from 'react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  outsourcingCloudApi,
  CLOUD_STATUS_LABEL, CLOUD_STATUS_TONE,
  DATA_CLASSIFICATION_LABEL, DATA_RESIDENCY_LABEL,
  type CloudServiceResponse,
} from '../../lib/outsourcingCloud';

/** Cloud services register — Sprint 30 frontend. */
export function OutsourcingCloudServicesPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<CloudServiceResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setError(null);
      try {
        const r = await outsourcingCloudApi.listCloudServices();
        if (!cancelled) setRows(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل سجل الخدمات السحابية' : 'Failed to load cloud services'));
        }
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cols: Column<CloudServiceResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '120px', mono: true,
      cell: c => <span style={{ fontSize: 12 }}>{c.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'الخدمة' : 'Service',
      cell: c => (
        <div>
          <div style={{ fontWeight: 500, fontSize: 13 }}>
            {lang === 'ar' ? c.serviceNameAr : c.serviceNameEn}
          </div>
          <div style={{ fontSize: 11, opacity: 0.7 }}>
            {lang === 'ar' ? c.cspLegalNameAr : c.cspLegalNameEn}
            {c.cspRegionLabel ? ` · ${c.cspRegionLabel}` : ''}
          </div>
        </div>
      ),
    },
    {
      header: lang === 'ar' ? 'النمط' : 'Deployment', width: '90px',
      cell: c => <Badge tone="info">{c.deploymentModel}</Badge>,
    },
    {
      header: lang === 'ar' ? 'التصنيف' : 'Classification', width: '140px',
      cell: c => c.dataClassification
        ? (
          <Badge tone={c.dataClassification === 'T4' ? 'danger' : c.dataClassification === 'T3' ? 'warning' : 'info'}>
            {DATA_CLASSIFICATION_LABEL[c.dataClassification][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        )
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>,
    },
    {
      header: lang === 'ar' ? 'مقرّ البيانات' : 'Residency', width: '130px',
      cell: c => c.dataResidency
        ? (
          <Badge tone={c.dataResidency === 'IN_KINGDOM' ? 'success' : c.dataResidency === 'GCC' ? 'info' : 'warning'}>
            {DATA_RESIDENCY_LABEL[c.dataResidency][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        )
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '160px',
      cell: c => (
        <Badge tone={CLOUD_STATUS_TONE[c.status]}>
          {CLOUD_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={lang === 'ar' ? 'الخدمات السحابية' : 'Cloud Services'}
        sub={lang === 'ar'
          ? 'إطار ساما للحوسبة السحابية — بوابة مقرّ البيانات (T3/T4 → داخل المملكة، T2 → داخل المملكة أو دول الخليج، T1 → عالمي)'
          : 'SAMA Cloud Computing Framework — data residency gate (T3/T4 → IN_KINGDOM, T2 → IN_KINGDOM|GCC, T1 → GLOBAL)'}
      />

      {error && (
        <section style={{ padding: 12, marginBottom: 16, borderInlineStart: '4px solid var(--color-danger)', borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <strong>{lang === 'ar' ? 'خطأ' : 'Error'}:</strong> {error}
        </section>
      )}

      <section style={{ padding: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
        <DataTable
          columns={cols}
          rows={rows}
          empty={<InlineEmpty />}
        />
      </section>
    </Shell>
  );
}
