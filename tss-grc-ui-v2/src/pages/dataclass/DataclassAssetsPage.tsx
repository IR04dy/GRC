import { useEffect, useState } from 'react';
import { Plus, Database, FolderOpen } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EntityDocsModal } from '../../components/ui/EntityDocsModal';
import { Card } from '../../components/ui/Card';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  dataclassApi,
  CLASSIFICATION_LABEL, CLASSIFICATION_TONE,
  ASSET_STATUS_LABEL, ASSET_STATUS_TONE,
  SPECIAL_CATEGORY_LABEL,
  nextAssetStatuses,
  type AssetResponse, type AssetStatus, type AssetDomain,
  type Classification, type SpecialCategory,
} from '../../lib/dataclass';

const DOMAINS: AssetDomain[] = [
  'CUSTOMER_PII','FINANCIAL_TRANSACTION','EMPLOYEE','SHARIA',
  'AML_KYC','MARKET_DATA','CONTRACT','CREDIT_FILE','OPERATIONAL','OTHER',
];
const CLASSIFICATIONS: Classification[] = ['PUBLIC','INTERNAL','CONFIDENTIAL','TOP_SECRET'];
const SPECIAL_CATS: SpecialCategory[] = [
  'NONE','HEALTH','BIOMETRIC','GENETIC','RELIGIOUS','RACIAL','POLITICAL','CRIMINAL',
];

/** Sprint 32 — Data Assets register (NDMO 4-level + PDPL Art.4). */
export function DataclassAssetsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<AssetResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  // create
  const [showNew, setShowNew] = useState(false);
  const [ref, setRef] = useState('');
  const [nameEn, setNameEn] = useState('');
  const [nameAr, setNameAr] = useState('');
  const [domain, setDomain] = useState<AssetDomain>('CUSTOMER_PII');
  const [host, setHost] = useState('');
  const [special, setSpecial] = useState<SpecialCategory>('NONE');
  const [lawful, setLawful] = useState('');
  const [retention, setRetention] = useState<number | ''>('');

  // classify dialog
  const [classifyTarget, setClassifyTarget] = useState<AssetResponse | null>(null);
  const [docsTarget, setDocsTarget] = useState<AssetResponse | null>(null);
  const [klass, setKlass] = useState<Classification>('INTERNAL');
  const [klassSpecial, setKlassSpecial] = useState<SpecialCategory>('NONE');
  const [nextReview, setNextReview] = useState('');

  // transition
  const [moveTarget, setMoveTarget] = useState<AssetResponse | null>(null);
  const [toStatus, setToStatus] = useState<AssetStatus | null>(null);
  const [reasonEn, setReasonEn] = useState('');
  const [reasonAr, setReasonAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await dataclassApi.listAssets();
      setRows(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل سجل الأصول' : 'Failed to load assets'));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (!ref.trim() || !nameEn.trim() || !nameAr.trim()) return;
    setActing(true);
    try {
      await dataclassApi.createAsset({
        reference: ref.trim(),
        nameEn: nameEn.trim(), nameAr: nameAr.trim(),
        domain, hostSystemLabel: host || undefined,
        pdplSpecialCategory: special,
        pdplLawfulBasis: lawful || undefined,
        retentionYears: typeof retention === 'number' ? retention : undefined,
      });
      setShowNew(false);
      setRef(''); setNameEn(''); setNameAr(''); setHost('');
      setSpecial('NONE'); setLawful(''); setRetention('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إنشاء الأصل' : 'Failed to create asset'));
    } finally { setActing(false); }
  };

  const submitClassify = async () => {
    if (!classifyTarget) return;
    setActing(true);
    try {
      await dataclassApi.classifyAsset(classifyTarget.id, {
        classification: klass,
        pdplSpecialCategory: klassSpecial,
        nextReviewDue: nextReview || undefined,
      });
      setClassifyTarget(null);
      setKlass('INTERNAL'); setKlassSpecial('NONE'); setNextReview('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تصنيف الأصل' : 'Failed to classify asset'));
    } finally { setActing(false); }
  };

  const submitTransition = async () => {
    if (!moveTarget || !toStatus) return;
    setActing(true);
    try {
      await dataclassApi.transitionAsset(moveTarget.id, {
        toStatus,
        reasonEn: reasonEn || undefined,
        reasonAr: reasonAr || undefined,
      });
      setMoveTarget(null); setToStatus(null);
      setReasonEn(''); setReasonAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ التحوّل' : 'Failed to transition'));
    } finally { setActing(false); }
  };

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
    { header: lang === 'ar' ? 'التصنيف' : 'Classification', width: '140px',
      cell: a => a.classification
        ? <Badge tone={CLASSIFICATION_TONE[a.classification]}>
            {CLASSIFICATION_LABEL[a.classification][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span> },
    { header: lang === 'ar' ? 'فئة حسّاسة' : 'Special', width: '140px',
      cell: a => a.pdplSpecialCategory !== 'NONE'
        ? <Badge tone="danger">
            {SPECIAL_CATEGORY_LABEL[a.pdplSpecialCategory][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span> },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: a => <Badge tone={ASSET_STATUS_TONE[a.status]}>
        {ASSET_STATUS_LABEL[a.status][lang === 'ar' ? 'ar' : 'en']}</Badge> },
    { header: '', width: '180px',
      cell: a => {
        const next = nextAssetStatuses(a.status);
        return (
          <div style={{ display: 'flex', gap: 4 }}>
            <Button variant="ghost" size="sm" Icon={FolderOpen}
              title={lang === 'ar' ? 'المستندات والضوابط' : 'Docs & controls'}
              onClick={(e) => { e.stopPropagation(); setDocsTarget(a); }} />
            {a.status === 'DRAFT' && (
              <Button variant="ghost" size="sm"
                onClick={(e) => { e.stopPropagation();
                  setClassifyTarget(a); setKlass(a.classification ?? 'INTERNAL');
                  setKlassSpecial(a.pdplSpecialCategory); setNextReview(''); }}>
                {lang === 'ar' ? 'تصنيف' : 'Classify'}
              </Button>
            )}
            {next.length > 0 && (
              <Button variant="ghost" size="sm"
                onClick={(e) => { e.stopPropagation();
                  setMoveTarget(a); setToStatus(next[0]);
                  setReasonEn(''); setReasonAr(''); }}>
                {lang === 'ar' ? 'تحريك' : 'Move'}
              </Button>
            )}
          </div>
        );
      } },
  ];

  return (
    <Shell>
      <PageHeader
        eyebrow={lang === 'ar' ? 'تصنيف البيانات · المرحلة ٣٢' : 'Data Classification · Sprint 32'}
        title={lang === 'ar' ? 'سجل أصول البيانات' : 'Data Assets Register'}
        sub={lang === 'ar'
          ? 'سياسة التصنيف NDMO — كل أصل بتصنيف ومالك ومدة احتفاظ ومرجع تقييم الأثر'
          : 'NDMO Data Classification Policy — every asset has classification + owner + retention + DPIA reference'}
        actions={
          <Button onClick={() => setShowNew(s => !s)} variant="primary" Icon={Plus}>
            {showNew ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
                     : (lang === 'ar' ? 'أصل جديد' : 'New asset')}
          </Button>
        } />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'تسجيل أصل بيانات جديد' : 'Register a new data asset'}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'المرجع *' : 'Reference *'}>
                <input value={ref} onChange={e => setRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="DA-CUST-2026-009" />
              </Field>
              <Field label={lang === 'ar' ? 'النطاق' : 'Domain'}>
                <select value={domain}
                  onChange={e => setDomain(e.target.value as AssetDomain)} style={inputStyle}>
                  {DOMAINS.map(d => <option key={d} value={d}>{d.replace(/_/g, ' ')}</option>)}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'الاسم (إنجليزي) *' : 'Name (English) *'}>
                <input value={nameEn} onChange={e => setNameEn(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="Retail customer master file" />
              </Field>
              <Field label={lang === 'ar' ? 'الاسم (عربي) *' : 'Name (Arabic) *'}>
                <input value={nameAr} dir="rtl" onChange={e => setNameAr(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="ملف عملاء التجزئة الرئيس" />
              </Field>
              <Field label={lang === 'ar' ? 'النظام المضيف' : 'Host system'}>
                <input value={host} onChange={e => setHost(e.target.value)}
                  style={inputStyle} maxLength={128} placeholder="T24 — CRM module" />
              </Field>
              <Field label={lang === 'ar' ? 'فئة حسّاسة (مادة ٤)' : 'Special category (Art.4)'}>
                <select value={special}
                  onChange={e => setSpecial(e.target.value as SpecialCategory)} style={inputStyle}>
                  {SPECIAL_CATS.map(s => (
                    <option key={s} value={s}>
                      {SPECIAL_CATEGORY_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'الأساس القانوني (مادة ٦)' : 'Lawful basis (Art.6)'}>
                <input value={lawful} onChange={e => setLawful(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="CONSENT / CONTRACT / LEGAL_OBLIGATION" />
              </Field>
              <Field label={lang === 'ar' ? 'مدة الاحتفاظ (سنوات)' : 'Retention (years)'}>
                <input type="number" value={retention}
                  onChange={e => setRetention(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} placeholder="10" />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submit}
                disabled={acting || !ref.trim() || !nameEn.trim() || !nameAr.trim()}>
                {acting ? (lang === 'ar' ? 'جارٍ الإنشاء…' : 'Creating…')
                         : (lang === 'ar' ? 'إنشاء' : 'Create')}
              </Button>
              <Button variant="secondary" onClick={() => setShowNew(false)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {classifyTarget && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? `تصنيف ${classifyTarget.reference}` : `Classify ${classifyTarget.reference}`}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'التصنيف *' : 'Classification *'}>
                <select value={klass}
                  onChange={e => setKlass(e.target.value as Classification)} style={inputStyle}>
                  {CLASSIFICATIONS.map(c => (
                    <option key={c} value={c}>
                      {CLASSIFICATION_LABEL[c][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'فئة حسّاسة' : 'Special category'}>
                <select value={klassSpecial}
                  onChange={e => setKlassSpecial(e.target.value as SpecialCategory)} style={inputStyle}>
                  {SPECIAL_CATS.map(s => (
                    <option key={s} value={s}>
                      {SPECIAL_CATEGORY_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'موعد المراجعة القادم' : 'Next review due'}>
                <input type="date" value={nextReview} onChange={e => setNextReview(e.target.value)}
                  style={inputStyle} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submitClassify} disabled={acting}>
                {acting ? (lang === 'ar' ? 'جارٍ التصنيف…' : 'Classifying…')
                         : (lang === 'ar' ? 'تصنيف' : 'Classify')}
              </Button>
              <Button variant="secondary" onClick={() => setClassifyTarget(null)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {moveTarget && toStatus && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? `تحريك ${moveTarget.reference}` : `Move ${moveTarget.reference}`}>
            <Field label={lang === 'ar' ? 'الحالة الجديدة' : 'Next status'}>
              <select value={toStatus}
                onChange={e => setToStatus(e.target.value as AssetStatus)}
                style={inputStyle}>
                {nextAssetStatuses(moveTarget.status).map(s => (
                  <option key={s} value={s}>
                    {ASSET_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </Field>
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
                onClick={() => { setMoveTarget(null); setToStatus(null); }}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded title={lang === 'ar' ? 'كل الأصول' : 'All assets'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable columns={cols} rows={rows}
            empty={<EmptyState Icon={Database}
              title={lang === 'ar' ? 'لا أصول بعد' : 'No assets yet'}
              sub={lang === 'ar'
                ? 'سجّل أوّل أصل بيانات لبدء دورة التصنيف.'
                : 'Register the first data asset to start the classification lifecycle.'} />}
          />
        )}
      </Card>
      <EntityDocsModal
        entityType="DATACLASS_ASSET"
        entityId={docsTarget?.id ?? null}
        open={!!docsTarget}
        recordLabel={docsTarget?.reference}
        onClose={() => setDocsTarget(null)}
      />
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
