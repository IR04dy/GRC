import { useEffect, useState } from 'react';
import { Package, Plus } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  shariaApi,
  PRODUCT_STATUS_LABEL, PRODUCT_STATUS_TONE,
  PRODUCT_STRUCTURE_LABEL,
  nextProductStatuses,
  type ProductResponse, type ProductStatus, type ProductStructure,
} from '../../lib/sharia';

const STRUCTURES: ProductStructure[] = [
  'MURABAHA', 'IJARA', 'TAWARRUQ', 'SUKUK', 'MUDARABA',
  'MUSHARAKA', 'SALAM', 'ISTISNA', 'OTHER',
];

/** Sprint 20 PR #3 — Islamic-product approval pipeline. */
export function ShariaProductsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<ProductResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  const [showNew, setShowNew] = useState(false);
  const [ref, setRef] = useState('');
  const [nameEn, setNameEn] = useState('');
  const [nameAr, setNameAr] = useState('');
  const [structure, setStructure] = useState<ProductStructure>('MURABAHA');
  const [descEn, setDescEn] = useState('');
  const [descAr, setDescAr] = useState('');

  const [transitionTarget, setTransitionTarget] = useState<ProductResponse | null>(null);
  const [toStatus, setToStatus] = useState<ProductStatus | null>(null);
  const [linkedResolution, setLinkedResolution] = useState<number | ''>('');
  const [reasonEn, setReasonEn] = useState('');
  const [reasonAr, setReasonAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await shariaApi.listProducts(0, 200);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل المنتجات' : 'Failed to load products'));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (!ref.trim() || !nameEn.trim() || !nameAr.trim()) return;
    setActing(true);
    try {
      await shariaApi.submitProduct({
        reference: ref.trim(), productNameEn: nameEn.trim(), productNameAr: nameAr.trim(),
        structure,
        descriptionEn: descEn || undefined, descriptionAr: descAr || undefined,
      });
      setShowNew(false);
      setRef(''); setNameEn(''); setNameAr(''); setStructure('MURABAHA');
      setDescEn(''); setDescAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تقديم المنتج' : 'Failed to submit product'));
    } finally { setActing(false); }
  };

  const submitTransition = async () => {
    if (!transitionTarget || !toStatus) return;
    setActing(true);
    try {
      await shariaApi.transitionProduct(transitionTarget.id, {
        toStatus,
        linkedResolutionId: typeof linkedResolution === 'number' ? linkedResolution : undefined,
        reasonEn: reasonEn || undefined, reasonAr: reasonAr || undefined,
      });
      setTransitionTarget(null); setToStatus(null);
      setLinkedResolution(''); setReasonEn(''); setReasonAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ التحوّل' : 'Failed to transition'));
    } finally { setActing(false); }
  };

  const cols: Column<ProductResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', mono: true, width: '130px',
      cell: p => <span style={{ fontSize: 12 }}>{p.reference}</span> },
    { header: lang === 'ar' ? 'المنتج' : 'Product',
      cell: p => <span style={{ fontWeight: 500, fontSize: 13 }}>
        {lang === 'ar' ? p.productNameAr : p.productNameEn}
      </span> },
    { header: lang === 'ar' ? 'الهيكل' : 'Structure', width: '120px',
      cell: p => <Badge tone="info">
        {PRODUCT_STRUCTURE_LABEL[p.structure][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '170px',
      cell: p => <Badge tone={PRODUCT_STATUS_TONE[p.status]}>
        {PRODUCT_STATUS_LABEL[p.status][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'القرار المرتبط' : 'Linked res', width: '120px',
      cell: p => <span style={{ fontSize: 12 }}>{p.linkedResolutionId ?? '—'}</span> },
    { header: lang === 'ar' ? 'الإطلاق' : 'Launched', width: '120px',
      cell: p => <span style={{ fontSize: 11 }}>{p.launchedAt ?? '—'}</span> },
    { header: '', width: '110px',
      cell: p => {
        const next = nextProductStatuses(p.status);
        if (next.length === 0) return null;
        return (
          <Button variant="ghost" size="sm"
            onClick={(e) => { e.stopPropagation();
              setTransitionTarget(p); setToStatus(next[0]);
              setLinkedResolution(''); setReasonEn(''); setReasonAr(''); }}>
            {lang === 'ar' ? 'تحريك' : 'Move'}
          </Button>
        );
      } },
  ];

  return (
    <Shell title={lang === 'ar' ? 'اعتماد المنتجات الإسلامية' : 'Islamic Product Approvals'}
      sub={lang === 'ar' ? 'مسار اعتماد المنتجات الإسلامية من التقديم إلى الإطلاق.'
                          : 'Islamic product pipeline from submission through to launch.'}>
      <PageHeader
        eyebrow={lang === 'ar' ? 'الحوكمة الشرعية · المرحلة ٢٠'
                                : 'Sharia Governance · Sprint 20'}
        title={lang === 'ar' ? 'اعتماد المنتجات' : 'Product Approvals'}
        sub={lang === 'ar'
          ? 'لا يُطلَق منتج إسلامي بدون موافقة نهائية من الهيئة الشرعية (ساما §4.2 + أيوفي GSIFI 3)'
          : 'No Islamic product launches without APPROVED_FINAL SSB resolution (SAMA §4.2 + AAOIFI GSIFI 3)'}
        actions={
          <Button onClick={() => setShowNew(s => !s)} variant="primary" Icon={Plus}>
            {showNew ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
                      : (lang === 'ar' ? 'تقديم منتج' : 'Submit product')}
          </Button>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'تقديم منتج إسلامي جديد' : 'Submit a new Islamic product'}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'المرجع *' : 'Reference *'}>
                <input value={ref} onChange={e => setRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="PA-2026-021" />
              </Field>
              <Field label={lang === 'ar' ? 'الهيكل' : 'Structure'}>
                <select value={structure}
                  onChange={e => setStructure(e.target.value as ProductStructure)}
                  style={inputStyle}>
                  {STRUCTURES.map(s => (
                    <option key={s} value={s}>
                      {PRODUCT_STRUCTURE_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'الاسم (إنجليزي) *' : 'Name (English) *'}>
                <input value={nameEn} onChange={e => setNameEn(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="Personal Vehicle Murabaha (2026 series)" />
              </Field>
              <Field label={lang === 'ar' ? 'الاسم (عربي) *' : 'Name (Arabic) *'}>
                <input value={nameAr} dir="rtl" onChange={e => setNameAr(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="مرابحة السيارات الشخصية (سلسلة 2026)" />
              </Field>
              <Field label={lang === 'ar' ? 'الوصف (إنجليزي)' : 'Description (English)'}>
                <textarea value={descEn} onChange={e => setDescEn(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'الوصف (عربي)' : 'Description (Arabic)'}>
                <textarea value={descAr} dir="rtl" onChange={e => setDescAr(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submit}
                      disabled={acting || !ref.trim() || !nameEn.trim() || !nameAr.trim()}>
                {acting ? (lang === 'ar' ? 'جارٍ التقديم…' : 'Submitting…')
                         : (lang === 'ar' ? 'تقديم' : 'Submit')}
              </Button>
              <Button variant="secondary" onClick={() => setShowNew(false)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {transitionTarget && toStatus && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar'
            ? `تحريك ${transitionTarget.reference}`
            : `Move ${transitionTarget.reference}`}>
            <Field label={lang === 'ar' ? 'الحالة الجديدة' : 'Next status'}>
              <select value={toStatus}
                onChange={e => setToStatus(e.target.value as ProductStatus)}
                style={inputStyle}>
                {nextProductStatuses(transitionTarget.status).map(s => (
                  <option key={s} value={s}>
                    {PRODUCT_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </Field>
            {toStatus === 'APPROVED_FINAL' && (
              <Field label={lang === 'ar' ? 'معرّف القرار المرتبط *' : 'Linked resolution id *'}>
                <input type="number" value={linkedResolution}
                  onChange={e => setLinkedResolution(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} placeholder="14" />
              </Field>
            )}
            <div style={{ display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12, marginTop: 12 }}>
              <Field label={lang === 'ar' ? 'السبب (إنجليزي)' : 'Reason (English)'}>
                <textarea value={reasonEn} onChange={e => setReasonEn(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'السبب (عربي)' : 'Reason (Arabic)'}>
                <textarea value={reasonAr} dir="rtl" onChange={e => setReasonAr(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submitTransition} disabled={acting}>
                {acting ? (lang === 'ar' ? 'جارٍ التنفيذ…' : 'Applying…')
                         : (lang === 'ar' ? 'تنفيذ' : 'Apply')}
              </Button>
              <Button variant="secondary"
                onClick={() => { setTransitionTarget(null); setToStatus(null); }}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded title={lang === 'ar' ? 'كل المنتجات' : 'All products'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable columns={cols} rows={rows}
            empty={<EmptyState Icon={Package}
              title={lang === 'ar' ? 'لا منتجات' : 'No products yet'}
              sub={lang === 'ar'
                ? 'قدّم أوّل منتج إسلامي ليبدأ مسار اعتماد الهيئة الشرعية.'
                : 'Submit the first Islamic product to start the SSB pipeline.'} />}
          />
        )}
      </Card>
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: 6, border: '1px solid var(--border)', borderRadius: 4,
  fontSize: 13, width: '100%', fontFamily: 'inherit',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 12, fontWeight: 600 }}>{label}</span>
      {children}
    </label>
  );
}
