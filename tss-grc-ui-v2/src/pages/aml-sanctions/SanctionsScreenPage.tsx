import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ScanSearch } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  sanctionsApi, type ScreenRequest, type SubjectType,
} from '../../lib/sanctions';

const SUBJECT_OPTIONS: { value: SubjectType; en: string; ar: string }[] = [
  { value: 'CUSTOMER_ONBOARDING',     en: 'Customer onboarding',     ar: 'استقبال عميل' },
  { value: 'TRANSACTION_COUNTERPARTY', en: 'Transaction counterparty', ar: 'طرف معاملة' },
  { value: 'BENEFICIAL_OWNER',         en: 'Beneficial owner',         ar: 'مستفيد حقيقي' },
  { value: 'EMPLOYEE_KYC',             en: 'Employee KYC',             ar: 'موظف' },
  { value: 'VENDOR_KYC',               en: 'Vendor KYC',               ar: 'مورد' },
  { value: 'AD_HOC',                   en: 'Ad-hoc',                   ar: 'فحص فوري' },
];

/**
 * Sprint 13 — Manual / quick-screening form.
 *
 * <p>For one-off lookups by AML / compliance officers. Posts to
 * {@code POST /api/v1/aml/sanctions/checks} with check_source=MANUAL and
 * routes to the resulting check detail page where the user can adjudicate
 * each hit.
 */
export function SanctionsScreenPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const navigate = useNavigate();

  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const [subjectType, setSubjectType] = useState<SubjectType>('CUSTOMER_ONBOARDING');
  const [subjectReference, setSubjectReference] = useState('');
  const [subjectNameEn, setSubjectNameEn] = useState('');
  const [subjectNameAr, setSubjectNameAr] = useState('');
  const [subjectDob, setSubjectDob] = useState('');
  const [subjectCountry, setSubjectCountry] = useState('');
  const [subjectIdDocument, setSubjectIdDocument] = useState('');

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!subjectNameEn.trim()) {
      setErr(isAr ? 'الاسم باللغة الإنجليزية مطلوب' : 'English name is required');
      return;
    }
    setSubmitting(true); setErr(null);
    try {
      const req: ScreenRequest = {
        subjectType,
        subjectReference: subjectReference.trim() || undefined,
        subjectNameEn: subjectNameEn.trim(),
        subjectNameAr: subjectNameAr.trim() || undefined,
        subjectDob: subjectDob || undefined,
        subjectCountry: subjectCountry.trim() || undefined,
        subjectIdDocument: subjectIdDocument.trim() || undefined,
        checkSource: 'MANUAL',
      };
      const check = await sanctionsApi.screen(req);
      navigate(`/aml/sanctions/checks/${check.id}`);
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
      setSubmitting(false);
    }
  }

  return (
    <Shell
      title={isAr ? 'فحص جديد' : 'New screening'}
      sub={isAr
        ? 'فحص فوري لاسم أو طرف ضد قوائم العقوبات النشطة'
        : 'Quick lookup of a name or party against active sanctions lists'}
    >
      <PageHeader
        eyebrow={isAr ? 'الفحص ضد قوائم العقوبات' : 'Sanctions screening'}
        title={isAr ? 'فحص يدوي' : 'Manual screening'}
        sub={isAr
          ? 'يفحص الموضوع ضد كل قائمة نشطة مرئية للمستأجر ويفتح تطابقات للفرز عند تجاوز عتبة ٧٥'
          : 'Screens the subject against every active visible list; hits ≥ threshold 75 open triage cases'}
        actions={
          <Link to="/aml/sanctions/dashboard">
            <Button variant="ghost" size="sm">
              {isAr ? 'لوحة المعلومات' : 'Dashboard'}
            </Button>
          </Link>
        }
      />

      <Card>
        <form onSubmit={onSubmit} style={{ padding: 16 }}>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: 12,
          }}>
            <Field label={isAr ? 'نوع الفاحوص *' : 'Subject type *'}>
              <select
                value={subjectType}
                onChange={e => setSubjectType(e.target.value as SubjectType)}
                style={selectStyle}>
                {SUBJECT_OPTIONS.map(o => (
                  <option key={o.value} value={o.value}>
                    {isAr ? o.ar : o.en}
                  </option>
                ))}
              </select>
            </Field>

            <Field label={isAr ? 'المرجع الداخلي' : 'Internal reference'}>
              <input
                type="text"
                value={subjectReference}
                onChange={e => setSubjectReference(e.target.value)}
                placeholder={isAr ? 'مثال: CUST-100123' : 'e.g. CUST-100123'}
                style={inputStyle} />
            </Field>

            <Field label={isAr ? 'الاسم باللغة الإنجليزية *' : 'Name (English) *'}>
              <input
                type="text"
                value={subjectNameEn}
                onChange={e => setSubjectNameEn(e.target.value)}
                required
                style={inputStyle} />
            </Field>

            <Field label={isAr ? 'الاسم باللغة العربية' : 'Name (Arabic)'}>
              <input
                type="text"
                value={subjectNameAr}
                onChange={e => setSubjectNameAr(e.target.value)}
                style={inputStyle} />
            </Field>

            <Field label={isAr ? 'تاريخ الميلاد' : 'Date of birth'}>
              <input
                type="date"
                value={subjectDob}
                onChange={e => setSubjectDob(e.target.value)}
                style={inputStyle} />
            </Field>

            <Field label={isAr ? 'البلد (ISO)' : 'Country (ISO)'}>
              <input
                type="text"
                value={subjectCountry}
                onChange={e => setSubjectCountry(e.target.value)}
                placeholder={isAr ? 'مثال: SA' : 'e.g. SA'}
                maxLength={3}
                style={inputStyle} />
            </Field>

            <Field label={isAr ? 'وثيقة الهوية' : 'ID document'}>
              <input
                type="text"
                value={subjectIdDocument}
                onChange={e => setSubjectIdDocument(e.target.value)}
                placeholder={isAr ? 'رقم الهوية / الجواز' : 'National ID / passport'}
                style={inputStyle} />
            </Field>
          </div>

          {err && (
            <div style={{
              marginTop: 12, padding: 10,
              background: 'var(--danger-bg)', color: 'var(--danger-fg)',
              fontSize: 12, borderRadius: 'var(--r-sm)',
            }}>{err}</div>
          )}

          <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
            <Button type="submit" variant="primary" disabled={submitting}>
              <ScanSearch size={14} />
              {submitting
                ? (isAr ? 'جارٍ الفحص…' : 'Screening…')
                : (isAr ? 'تشغيل الفحص' : 'Run screening')}
            </Button>
            <Link to="/aml/sanctions/dashboard">
              <Button variant="ghost" type="button">{isAr ? 'إلغاء' : 'Cancel'}</Button>
            </Link>
          </div>
        </form>
      </Card>

      <div style={{ marginTop: 12 }}>
        <Card>
          <div style={{ padding: 12, fontSize: 11, color: 'var(--ink-3)' }}>
            {isAr
              ? 'تنبيه: هذه الواجهة للفحوصات اليدوية فقط. تكامل الفحص في الزمن الحقيقي مع منصّات استقبال العملاء يستخدم نقطة الإطلاق نفسها (REAL_TIME_API) بدون تدخّل بشري.'
              : 'Note: this UI is for manual lookups only. Real-time integration with the onboarding platform uses the same endpoint (REAL_TIME_API mode) without human input.'}
          </div>
        </Card>
      </div>
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '8px 10px',
  fontSize: 12,
  borderRadius: 'var(--r-sm)',
  border: '1px solid var(--border)',
  background: 'var(--surface-1)',
  color: 'var(--ink-1)',
};

const selectStyle: React.CSSProperties = { ...inputStyle };

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{label}</span>
      {children}
    </label>
  );
}
