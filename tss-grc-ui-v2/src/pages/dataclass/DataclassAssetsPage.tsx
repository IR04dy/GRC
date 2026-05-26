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
  dataclassApi,
  CLASSIFICATION_LABEL, CLASSIFICATION_TONE,
  ASSET_STATUS_LABEL, ASSET_STATUS_TONE,
  SPECIAL_CATEGORY_LABEL,
  type AssetResponse,
} from '../../lib/dataclass';

/** Data assets register — Sprint 32 frontend. */
export function DataclassAssetsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();
  const [rows, setRows] = useState<AssetResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setError(null);
      try {
        const r = await dataclassApi.listAssets();
        if (!cancelled) setRows(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل سجل أصول البيانات' : 'Failed to load data assets'));
        }
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cols: Column<AssetResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', width: '130px', mono: true,
      cell: a => <span style={{ fontSize: 12 }}>{a.reference}</span> },
    { header: lang === 'ar' ? 'الأصل' : 'Asset',
      cell: a => (
        <div>
          <div style={{ fontWeight: 500, fontSize: 13 }}>
            {lang === 'ar' ? a.nameAr : a.nameEn}
          </div>
          {a.hostSystemLabel && (
            <div style={{ fontSize: 11, opacity: 0.7 }}>{a.hostSystemLabel}</div>
          )}
        </div>
      ) },
    { header: lang === 'ar' ? 'النطاق' : 'Domain', width: '160px',
      cell: a => a.domain
        ? <span style={{ fontSize: 12, opacity: 0.8 }}>{a.domain.replace(/_/g, ' ')}</span>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span> },
    { header: lang === 'ar' ? 'التصنيف' : 'Classification', width: '140px',
      cell: a => a.classification
        ? <Badge tone={CLASSIFICATION_TONE[a.classification]}>
            {CLASSIFICATION_LABEL[a.classification][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span> },
    { header: lang === 'ar' ? 'فئة حسّاسة' : 'Special category', width: '150px',
      cell: a => a.pdplSpecialCategory !== 'NONE'
        ? <Badge tone="danger">
            {SPECIAL_CATEGORY_LABEL[a.pdplSpecialCategory][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span> },
    { header: lang === 'ar' ? 'احتفاظ (سنوات)' : 'Retention (yrs)', width: '120px',
      cell: a => <span style={{ fontSize: 12 }}>{a.retentionYears ?? '—'}</span> },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '150px',
      cell: a => <Badge tone={ASSET_STATUS_TONE[a.status]}>
        {ASSET_STATUS_LABEL[a.status][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'المراجعة القادمة' : 'Next review', width: '130px',
      cell: a => <span style={{ fontSize: 12 }}>{a.nextReviewDue ?? '—'}</span> },
  ];

  return (
    <Shell>
      <PageHeader
        title={lang === 'ar' ? 'سجل أصول البيانات' : 'Data Assets Register'}
        sub={lang === 'ar'
          ? 'سياسة التصنيف NDMO — كل أصل بتصنيف ومالك ومدة احتفاظ ومرجع تقييم تأثير حماية البيانات'
          : 'NDMO Data Classification Policy — every asset has classification + owner + retention + DPIA reference'}
      />

      {error && (
        <section style={{ padding: 12, marginBottom: 16, borderInlineStart: '4px solid var(--color-danger)', borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <strong>{lang === 'ar' ? 'خطأ' : 'Error'}:</strong> {error}
        </section>
      )}

      <section style={{ padding: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
        <DataTable columns={cols} rows={rows} empty={<InlineEmpty />} />
      </section>
    </Shell>
  );
}
