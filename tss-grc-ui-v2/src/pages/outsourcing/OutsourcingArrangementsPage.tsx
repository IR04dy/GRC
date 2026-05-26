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
  ARRANGEMENT_STATUS_LABEL, ARRANGEMENT_STATUS_TONE,
  SERVICE_CATEGORY_LABEL, MATERIALITY_LABEL,
  type ArrangementResponse,
} from '../../lib/outsourcingCloud';

/** Outsourcing Arrangements register — Sprint 30 frontend. */
export function OutsourcingArrangementsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<ArrangementResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setError(null);
      try {
        const r = await outsourcingCloudApi.listArrangements();
        if (!cancelled) setRows(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل سجل الترتيبات' : 'Failed to load arrangements'));
        }
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cols: Column<ArrangementResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '110px', mono: true,
      cell: a => <span style={{ fontSize: 12 }}>{a.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'المزوّد' : 'Provider',
      cell: a => (
        <div>
          <div style={{ fontWeight: 500, fontSize: 13 }}>
            {lang === 'ar' ? a.providerLegalNameAr : a.providerLegalNameEn}
          </div>
          <div style={{ fontSize: 11, opacity: 0.7 }}>
            {a.providerCountry ?? '—'}{a.providerJurisdiction ? ` · ${a.providerJurisdiction}` : ''}
          </div>
        </div>
      ),
    },
    {
      header: lang === 'ar' ? 'الخدمة' : 'Service',
      cell: a => (
        <span style={{ fontSize: 13 }}>
          {lang === 'ar' ? a.serviceTitleAr : a.serviceTitleEn}
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
      header: lang === 'ar' ? 'الجوهرية' : 'Materiality', width: '120px',
      cell: a => (
        <Badge tone={a.materiality === 'MATERIAL' ? 'warning' : 'neutral'}>
          {MATERIALITY_LABEL[a.materiality][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'نشاط جوهري' : 'Core activity', width: '110px',
      cell: a => (a.isCoreActivity
        ? <Badge tone="danger">{lang === 'ar' ? 'نشاط جوهري' : 'Core activity'}</Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '150px',
      cell: a => (
        <Badge tone={ARRANGEMENT_STATUS_TONE[a.status]}>
          {ARRANGEMENT_STATUS_LABEL[a.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'المراجعة القادمة' : 'Next review', width: '120px',
      cell: a => <span style={{ fontSize: 12 }}>{a.nextReviewDue ?? '—'}</span>,
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={lang === 'ar' ? 'سجل ترتيبات الإسناد' : 'Outsourcing Arrangements'}
        sub={lang === 'ar'
          ? 'تنظيم ساما للإسناد §٣-§٨ — كل ترتيب يحمل تقييم مخاطر وموافقة المجلس وإخطار ساما (للجوهري)'
          : 'SAMA Outsourcing Regulation §3-§8 — each arrangement carries risk assessment + Board approval + SAMA notification (for material)'}
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
          rowHref={a => `/outsourcing/arrangements/${a.id}`}
          empty={<InlineEmpty />}
        />
      </section>
    </Shell>
  );
}
