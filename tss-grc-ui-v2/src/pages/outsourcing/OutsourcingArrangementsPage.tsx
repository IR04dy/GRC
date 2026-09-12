import { useEffect, useState } from 'react';
import { Plus, Briefcase } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  outsourcingCloudApi,
  ARRANGEMENT_STATUS_LABEL, ARRANGEMENT_STATUS_TONE,
  SERVICE_CATEGORY_LABEL, MATERIALITY_LABEL,
  nextArrangementStatuses,
  type ArrangementResponse, type ArrangementStatus,
  type ServiceCategory, type Materiality,
} from '../../lib/outsourcingCloud';

const SERVICE_CATEGORIES: ServiceCategory[] = [
  'CORE_BANKING','PAYMENTS','CARDS','CUSTODY','MARKET_DATA',
  'IT_INFRASTRUCTURE','CLOUD','CALL_CENTRE','COLLECTIONS',
  'PRINTING','HR','LEGAL','AUDIT','MARKETING','OTHER',
];
const MATERIALITIES: Materiality[] = ['NON_MATERIAL','MATERIAL'];

/** Sprint 30 — Outsourcing Arrangements register (SAMA Outsourcing Reg §3-§8). */
export function OutsourcingArrangementsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<ArrangementResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  // create form
  const [showNew, setShowNew] = useState(false);
  const [ref, setRef] = useState('');
  const [providerEn, setProviderEn] = useState('');
  const [providerAr, setProviderAr] = useState('');
  const [country, setCountry] = useState('SA');
  const [jurisdiction, setJurisdiction] = useState('Saudi Arabia');
  const [serviceEn, setServiceEn] = useState('');
  const [serviceAr, setServiceAr] = useState('');
  const [serviceCategory, setServiceCategory] = useState<ServiceCategory>('CORE_BANKING');
  const [materiality, setMateriality] = useState<Materiality>('MATERIAL');
  const [isCoreActivity, setIsCoreActivity] = useState(false);
  const [annualSpend, setAnnualSpend] = useState<number | ''>('');

  // transition form
  const [moveTarget, setMoveTarget] = useState<ArrangementResponse | null>(null);
  const [toStatus, setToStatus] = useState<ArrangementStatus | null>(null);
  const [samaRef, setSamaRef] = useState('');
  const [riskRating, setRiskRating] = useState('');
  const [reasonEn, setReasonEn] = useState('');
  const [reasonAr, setReasonAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await outsourcingCloudApi.listArrangements();
      setRows(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل سجل الترتيبات' : 'Failed to load arrangements'));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (!ref.trim() || !providerEn.trim() || !providerAr.trim() ||
        !serviceEn.trim() || !serviceAr.trim()) return;
    setActing(true);
    try {
      await outsourcingCloudApi.createArrangement({
        reference: ref.trim(),
        providerLegalNameEn: providerEn.trim(),
        providerLegalNameAr: providerAr.trim(),
        providerCountry: country || undefined,
        providerJurisdiction: jurisdiction || undefined,
        serviceTitleEn: serviceEn.trim(),
        serviceTitleAr: serviceAr.trim(),
        serviceCategory, materiality, isCoreActivity,
        annualSpendSar: typeof annualSpend === 'number' ? annualSpend : undefined,
      });
      setShowNew(false);
      setRef(''); setProviderEn(''); setProviderAr('');
      setServiceEn(''); setServiceAr(''); setAnnualSpend('');
      setIsCoreActivity(false);
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إنشاء الترتيب' : 'Failed to create arrangement'));
    } finally { setActing(false); }
  };

  const submitTransition = async () => {
    if (!moveTarget || !toStatus) return;
    setActing(true);
    try {
      await outsourcingCloudApi.transitionArrangement(moveTarget.id, {
        toStatus,
        samaNonObjectionRef: samaRef || undefined,
        riskRating: riskRating || undefined,
        reasonEn: reasonEn || undefined,
        reasonAr: reasonAr || undefined,
      });
      setMoveTarget(null); setToStatus(null);
      setSamaRef(''); setRiskRating(''); setReasonEn(''); setReasonAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ التحوّل' : 'Failed to transition'));
    } finally { setActing(false); }
  };

  const cols: Column<ArrangementResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', width: '110px', mono: true,
      cell: a => <span style={{ fontSize: 12 }}>{a.reference}</span> },
    { header: lang === 'ar' ? 'المزوّد' : 'Provider',
      cell: a => (
        <div>
          <div style={{ fontWeight: 500, fontSize: 13 }}>
            {lang === 'ar' ? a.providerLegalNameAr : a.providerLegalNameEn}
          </div>
          <div style={{ fontSize: 11, opacity: 0.7 }}>
            {a.providerCountry ?? '—'}{a.providerJurisdiction ? ` · ${a.providerJurisdiction}` : ''}
          </div>
        </div>
      ) },
    { header: lang === 'ar' ? 'الخدمة' : 'Service',
      cell: a => <span style={{ fontSize: 13 }}>
        {lang === 'ar' ? a.serviceTitleAr : a.serviceTitleEn}</span> },
    { header: lang === 'ar' ? 'الفئة' : 'Category', width: '130px',
      cell: a => <Badge tone="info">
        {SERVICE_CATEGORY_LABEL[a.serviceCategory][lang === 'ar' ? 'ar' : 'en']}</Badge> },
    { header: lang === 'ar' ? 'الجوهرية' : 'Materiality', width: '120px',
      cell: a => <Badge tone={a.materiality === 'MATERIAL' ? 'warning' : 'neutral'}>
        {MATERIALITY_LABEL[a.materiality][lang === 'ar' ? 'ar' : 'en']}</Badge> },
    { header: lang === 'ar' ? 'نشاط جوهري' : 'Core', width: '90px',
      cell: a => (a.isCoreActivity
        ? <Badge tone="danger">{lang === 'ar' ? 'نعم' : 'Yes'}</Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>) },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '150px',
      cell: a => <Badge tone={ARRANGEMENT_STATUS_TONE[a.status]}>
        {ARRANGEMENT_STATUS_LABEL[a.status][lang === 'ar' ? 'ar' : 'en']}</Badge> },
    { header: '', width: '95px',
      cell: a => {
        const next = nextArrangementStatuses(a.status);
        if (next.length === 0) return null;
        return (
          <Button variant="ghost" size="sm"
            onClick={(e) => { e.stopPropagation();
              setMoveTarget(a); setToStatus(next[0]);
              setSamaRef(''); setRiskRating(''); setReasonEn(''); setReasonAr(''); }}>
            {lang === 'ar' ? 'تحريك' : 'Move'}
          </Button>
        );
      } },
  ];

  return (
    <Shell>
      <PageHeader
        eyebrow={lang === 'ar' ? 'حوكمة الإسناد' : 'Outsourcing'}
        title={lang === 'ar' ? 'سجل ترتيبات الإسناد' : 'Outsourcing Arrangements'}
        sub={lang === 'ar'
          ? 'تنظيم ساما للإسناد §٣-§٨ — كل ترتيب يحمل تقييم مخاطر وموافقة المجلس وإخطار ساما (للجوهري)'
          : 'SAMA Outsourcing Reg §3-§8 — risk assessment + Board approval + SAMA 60-day notification (material)'}
        actions={
          <Button onClick={() => setShowNew(s => !s)} variant="primary" Icon={Plus}>
            {showNew ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
                      : (lang === 'ar' ? 'ترتيب جديد' : 'New arrangement')}
          </Button>
        } />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'ترتيب إسناد جديد' : 'New outsourcing arrangement'}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'المرجع *' : 'Reference *'}>
                <input value={ref} onChange={e => setRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="OUT-2026-001" />
              </Field>
              <Field label={lang === 'ar' ? 'فئة الخدمة' : 'Service category'}>
                <select value={serviceCategory}
                  onChange={e => setServiceCategory(e.target.value as ServiceCategory)}
                  style={inputStyle}>
                  {SERVICE_CATEGORIES.map(c => (
                    <option key={c} value={c}>
                      {SERVICE_CATEGORY_LABEL[c][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'الجوهرية' : 'Materiality'}>
                <select value={materiality}
                  onChange={e => setMateriality(e.target.value as Materiality)}
                  style={inputStyle}>
                  {MATERIALITIES.map(m => (
                    <option key={m} value={m}>
                      {MATERIALITY_LABEL[m][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'المزوّد (إنجليزي) *' : 'Provider (English) *'}>
                <input value={providerEn} onChange={e => setProviderEn(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="Tata Consultancy Services Ltd" />
              </Field>
              <Field label={lang === 'ar' ? 'المزوّد (عربي) *' : 'Provider (Arabic) *'}>
                <input value={providerAr} dir="rtl" onChange={e => setProviderAr(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="تاتا للاستشارات الخدمية المحدودة" />
              </Field>
              <Field label={lang === 'ar' ? 'بلد المزوّد' : 'Provider country'}>
                <input value={country} onChange={e => setCountry(e.target.value)}
                  style={inputStyle} maxLength={2} placeholder="SA" />
              </Field>
              <Field label={lang === 'ar' ? 'القانون الحاكم' : 'Governing jurisdiction'}>
                <input value={jurisdiction} onChange={e => setJurisdiction(e.target.value)}
                  style={inputStyle} maxLength={64} />
              </Field>
              <Field label={lang === 'ar' ? 'الخدمة (إنجليزي) *' : 'Service (English) *'}>
                <input value={serviceEn} onChange={e => setServiceEn(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="T24 core-banking platform managed services" />
              </Field>
              <Field label={lang === 'ar' ? 'الخدمة (عربي) *' : 'Service (Arabic) *'}>
                <input value={serviceAr} dir="rtl" onChange={e => setServiceAr(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="خدمات مُدارة لمنصّة T24 الأساسية" />
              </Field>
              <Field label={lang === 'ar' ? 'الإنفاق السنوي (ريال)' : 'Annual spend (SAR)'}>
                <input type="number" value={annualSpend}
                  onChange={e => setAnnualSpend(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} placeholder="12500000" />
              </Field>
              <Field label={lang === 'ar' ? 'نشاط جوهري؟' : 'Core activity?'}>
                <label style={{ display: 'inline-flex', gap: 8, alignItems: 'center', fontSize: 13 }}>
                  <input type="checkbox" checked={isCoreActivity}
                    onChange={e => setIsCoreActivity(e.target.checked)} />
                  {lang === 'ar' ? 'يحتاج خطاب عدم ممانعة من ساما' : 'Requires SAMA non-objection letter'}
                </label>
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submit}
                disabled={acting || !ref.trim() || !providerEn.trim() || !providerAr.trim()
                  || !serviceEn.trim() || !serviceAr.trim()}>
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

      {moveTarget && toStatus && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? `تحريك ${moveTarget.reference}` : `Move ${moveTarget.reference}`}>
            <Field label={lang === 'ar' ? 'الحالة الجديدة' : 'Next status'}>
              <select value={toStatus}
                onChange={e => setToStatus(e.target.value as ArrangementStatus)}
                style={inputStyle}>
                {nextArrangementStatuses(moveTarget.status).map(s => (
                  <option key={s} value={s}>
                    {ARRANGEMENT_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </Field>
            {toStatus === 'RISK_ASSESSED' && (
              <Field label={lang === 'ar' ? 'تقدير المخاطر' : 'Risk rating'}>
                <select value={riskRating} onChange={e => setRiskRating(e.target.value)}
                  style={inputStyle}>
                  <option value="">—</option>
                  <option value="LOW">{lang === 'ar' ? 'منخفض' : 'Low'}</option>
                  <option value="MEDIUM">{lang === 'ar' ? 'متوسط' : 'Medium'}</option>
                  <option value="HIGH">{lang === 'ar' ? 'مرتفع' : 'High'}</option>
                  <option value="CRITICAL">{lang === 'ar' ? 'حرج' : 'Critical'}</option>
                </select>
              </Field>
            )}
            {toStatus === 'SAMA_NOTIFIED' && (
              <Field label={lang === 'ar' ? 'رقم خطاب عدم ممانعة ساما' : 'SAMA non-objection ref'}>
                <input value={samaRef} onChange={e => setSamaRef(e.target.value)}
                  style={inputStyle} placeholder="SAMA-NOL-2026-0142" />
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
                onClick={() => { setMoveTarget(null); setToStatus(null); }}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded title={lang === 'ar' ? 'كل الترتيبات' : 'All arrangements'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable columns={cols} rows={rows}
            empty={<EmptyState Icon={Briefcase}
              title={lang === 'ar' ? 'لا ترتيبات بعد' : 'No arrangements yet'}
              sub={lang === 'ar'
                ? 'أنشئ أوّل ترتيب إسناد لبدء سير العمل ساما §٣-§٨.'
                : 'Create the first arrangement to start the SAMA §3-§8 lifecycle.'} />}
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
