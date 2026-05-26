import { useEffect, useState } from 'react';
import { Database, Lock, ShieldAlert, Globe, FileWarning, BadgeAlert } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  dataclassApi,
  CLASSIFICATION_LABEL, CLASSIFICATION_TONE,
  ASSET_STATUS_LABEL, ASSET_STATUS_TONE,
  FLOW_STATUS_LABEL, FLOW_STATUS_TONE,
  ADEQUACY_LABEL,
  SPECIAL_CATEGORY_LABEL,
  type DashboardResponse, type AssetResponse, type FlowResponse,
} from '../../lib/dataclass';

/**
 * Sprint 32 Frontend — Data Classification + NDMO dashboard.
 * NDMO Data Classification Policy + PDPL Art.4/6/7.
 * Workspace for DPO + CISO + Data Steward + Compliance.
 */
export function DataclassDashboardPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [data, setData] = useState<DashboardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setError(null);
      try {
        const r = await dataclassApi.dashboard();
        if (!cancelled) setData(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل لوحة تصنيف البيانات' : 'Failed to load Data Classification dashboard'));
        }
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const assetCols: Column<AssetResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '130px', mono: true,
      cell: a => <span style={{ fontSize: 12 }}>{a.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'الأصل' : 'Asset',
      cell: a => (
        <div>
          <div style={{ fontWeight: 500, fontSize: 13 }}>
            {lang === 'ar' ? a.nameAr : a.nameEn}
          </div>
          {a.hostSystemLabel && (
            <div style={{ fontSize: 11, opacity: 0.7 }}>{a.hostSystemLabel}</div>
          )}
        </div>
      ),
    },
    {
      header: lang === 'ar' ? 'التصنيف' : 'Classification', width: '140px',
      cell: a => a.classification
        ? (
          <Badge tone={CLASSIFICATION_TONE[a.classification]}>
            {CLASSIFICATION_LABEL[a.classification][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        )
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>,
    },
    {
      header: lang === 'ar' ? 'فئة حسّاسة' : 'Special category', width: '150px',
      cell: a => a.pdplSpecialCategory !== 'NONE'
        ? (
          <Badge tone="danger">
            {SPECIAL_CATEGORY_LABEL[a.pdplSpecialCategory][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        )
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '150px',
      cell: a => (
        <Badge tone={ASSET_STATUS_TONE[a.status]}>
          {ASSET_STATUS_LABEL[a.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
  ];

  const flowCols: Column<FlowResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '140px', mono: true,
      cell: f => <span style={{ fontSize: 12 }}>{f.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'الوجهة' : 'Destination',
      cell: f => (
        <div>
          <div style={{ fontWeight: 500, fontSize: 13 }}>{f.destinationSystemLabel ?? '—'}</div>
          <div style={{ fontSize: 11, opacity: 0.7 }}>{f.destinationCountry ?? ''}</div>
        </div>
      ),
    },
    {
      header: lang === 'ar' ? 'مستوى الحماية' : 'Adequacy', width: '160px',
      cell: f => f.destinationAdequacy
        ? (
          <Badge tone={
            f.destinationAdequacy === 'IN_KINGDOM' ? 'success' :
            f.destinationAdequacy === 'GCC' ? 'info' :
            f.destinationAdequacy === 'ADEQUATE' ? 'warning' : 'danger'}>
            {ADEQUACY_LABEL[f.destinationAdequacy][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        )
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>,
    },
    {
      header: lang === 'ar' ? 'مرجع المادة ٧' : 'PDPL Art.7', width: '160px',
      cell: f => f.pdplArt7TransferRef
        ? <span style={{ fontSize: 11, fontFamily: 'monospace' }}>{f.pdplArt7TransferRef}</span>
        : <Badge tone="danger">{lang === 'ar' ? 'مفقود' : 'Missing'}</Badge>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '130px',
      cell: f => (
        <Badge tone={FLOW_STATUS_TONE[f.status]}>
          {FLOW_STATUS_LABEL[f.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={lang === 'ar' ? 'تصنيف البيانات وفق NDMO' : 'Data Classification + NDMO'}
        sub={lang === 'ar'
          ? 'سياسة تصنيف البيانات NDMO (٤ مستويات) + المادة ٤ من نظام حماية البيانات (بيانات حسّاسة) + المادة ٧ (بوّابة عبر الحدود)'
          : 'NDMO Data Classification Policy (4 levels) + PDPL Art.4 (special-category) + Art.7 (cross-border gate)'}
      />

      {error && (
        <section style={{ padding: 12, marginBottom: 16, borderInlineStart: '4px solid var(--color-danger)', borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <strong>{lang === 'ar' ? 'خطأ' : 'Error'}:</strong> {error}
        </section>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12, marginBottom: 16 }}>
        <Kpi label={lang === 'ar' ? 'إجمالي الأصول' : 'Total assets'} value={data?.assetsTotal ?? 0} Icon={Database} />
        <Kpi label={lang === 'ar' ? 'سرّي للغاية' : 'Top secret'} value={data?.assetsTopSecret ?? 0} tone="danger" Icon={Lock} />
        <Kpi label={lang === 'ar' ? 'سرّي' : 'Confidential'} value={data?.assetsConfidential ?? 0} tone="warning" Icon={Lock} />
        <Kpi label={lang === 'ar' ? 'فئة حسّاسة (المادة ٤)' : 'Special category (Art.4)'} value={data?.assetsSpecialCategory ?? 0} tone="danger" Icon={ShieldAlert} />
        <Kpi label={lang === 'ar' ? 'تدفقات نشطة' : 'Active flows'} value={data?.flowsActive ?? 0} tone="success" Icon={Globe} />
        <Kpi label={lang === 'ar' ? 'تدفقات عبر الحدود' : 'Cross-border flows'} value={data?.flowsCrossBorder ?? 0} tone="warning" Icon={Globe} />
        <Kpi label={lang === 'ar' ? 'استثناءات نشطة' : 'Active exceptions'} value={data?.exceptionsActive ?? 0} tone="info" Icon={FileWarning} />
        <Kpi label={lang === 'ar' ? 'تنتهي خلال ٣٠ يوماً' : 'Expiring ≤30d'} value={data?.exceptionsExpiringNext30d ?? 0} tone={data && data.exceptionsExpiringNext30d > 0 ? 'warning' : 'neutral'} Icon={BadgeAlert} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12, marginBottom: 16 }}>
        <section style={{ padding: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <h3 style={{ marginTop: 0, fontSize: 14 }}>
            {lang === 'ar' ? 'الأصول حسب الحالة' : 'Assets by status'}
          </h3>
          {data?.assetsByStatus && Object.entries(data.assetsByStatus).length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {Object.entries(data.assetsByStatus).map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                  <Badge tone={ASSET_STATUS_TONE[k as keyof typeof ASSET_STATUS_TONE] ?? 'neutral'}>
                    {ASSET_STATUS_LABEL[k as keyof typeof ASSET_STATUS_LABEL]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}
                  </Badge>
                  <strong>{v}</strong>
                </div>
              ))}
            </div>
          ) : <InlineEmpty />}
        </section>

        <section style={{ padding: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <h3 style={{ marginTop: 0, fontSize: 14 }}>
            {lang === 'ar' ? 'التدفقات حسب الحالة' : 'Flows by status'}
          </h3>
          {data?.flowsByStatus && Object.entries(data.flowsByStatus).length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {Object.entries(data.flowsByStatus).map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                  <Badge tone={FLOW_STATUS_TONE[k as keyof typeof FLOW_STATUS_TONE] ?? 'neutral'}>
                    {FLOW_STATUS_LABEL[k as keyof typeof FLOW_STATUS_LABEL]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}
                  </Badge>
                  <strong>{v}</strong>
                </div>
              ))}
            </div>
          ) : <InlineEmpty />}
        </section>

        <section style={{ padding: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <h3 style={{ marginTop: 0, fontSize: 14 }}>
            {lang === 'ar' ? 'الاستثناءات حسب النوع' : 'Exceptions by type'}
          </h3>
          {data?.exceptionsByType && Object.entries(data.exceptionsByType).length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {Object.entries(data.exceptionsByType).map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                  <span style={{ fontSize: 12, opacity: 0.8 }}>{k}</span>
                  <strong>{v}</strong>
                </div>
              ))}
            </div>
          ) : <InlineEmpty />}
        </section>
      </div>

      <section style={{ padding: 16, marginBottom: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
        <h3 style={{ margin: '0 0 12px 0', fontSize: 14 }}>
          {lang === 'ar' ? 'أحدث الأصول' : 'Recent assets'}
        </h3>
        <DataTable
          columns={assetCols}
          rows={data?.recentAssets ?? []}
          rowHref={a => `/dataclass/assets/${a.id}`}
          empty={<InlineEmpty />}
        />
      </section>

      {data?.recentCrossBorderFlows && data.recentCrossBorderFlows.length > 0 && (
        <section style={{ padding: 16, borderInlineStart: '4px solid var(--color-warning)', borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <h3 style={{ margin: '0 0 12px 0', fontSize: 14 }}>
            {lang === 'ar' ? 'تدفقات عبر الحدود (AR-285)' : 'Cross-border flows (AR-285)'}
          </h3>
          <DataTable columns={flowCols} rows={data.recentCrossBorderFlows} empty={<InlineEmpty />} />
        </section>
      )}
    </Shell>
  );
}
