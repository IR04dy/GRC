import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, CheckCircle2, Copy, ShieldCheck } from 'lucide-react';
import { PublicShell } from '../../components/layout/PublicShell';
import { Card } from '../../components/ui/Card';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  complaintsApi, CATEGORY_LABEL, CHANNEL_LABEL, SEVERITY_LABEL, CUSTOMER_ID_TYPE_LABEL,
  type Category, type Channel, type Severity, type CustomerIdType,
  type SubmitComplaintRequest, type SubmitComplaintResponse,
} from '../../lib/complaints';

/**
 * Public complaint submission — /public/complaint.
 *
 * <p>UNAUTHENTICATED — anyone can reach this page. Mandated by SAMA
 * Banking Consumer Protection Principles (5-day acknowledgement +
 * 20-day resolution SLAs). Returns a one-time tracking token used to
 * follow the case on /public/track-complaint.
 */
export function PublicComplaintSubmissionPage() {
  const { lang, dir } = useI18n();
  const navigate = useNavigate();

  const [channel, setChannel] = useState<Channel>('WEB');
  const [category, setCategory] = useState<Category>('ACCOUNT_ACCESS');
  const [subcategory, setSubcategory] = useState('');
  const [severity, setSeverity] = useState<Severity>('MEDIUM');
  const [customerNameEn, setCustomerNameEn] = useState('');
  const [customerNameAr, setCustomerNameAr] = useState('');
  const [customerIdType, setCustomerIdType] = useState<CustomerIdType | ''>('');
  const [customerIdLast4, setCustomerIdLast4] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [customerEmail, setCustomerEmail] = useState('');
  const [customerCity, setCustomerCity] = useState('');
  const [productCode, setProductCode] = useState('');
  const [accountLast4, setAccountLast4] = useState('');
  const [descriptionEn, setDescriptionEn] = useState('');
  const [descriptionAr, setDescriptionAr] = useState('');

  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState<SubmitComplaintResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tokenCopied, setTokenCopied] = useState(false);

  function validateDigits(val: string): string {
    return val.replace(/\D/g, '').slice(0, 4);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!descriptionAr.trim()) {
      setError(lang === 'ar' ? 'وصف الشكوى بالعربية مطلوب.' : 'Arabic description is required.');
      return;
    }
    if (customerIdLast4 && customerIdLast4.length !== 4) {
      setError(lang === 'ar' ? 'يجب أن تكون آخر ٤ أرقام من الهوية ٤ أرقام بالضبط.'
        : 'ID last 4 must be exactly 4 digits.');
      return;
    }
    if (accountLast4 && accountLast4.length !== 4) {
      setError(lang === 'ar' ? 'يجب أن تكون آخر ٤ أرقام من الحساب ٤ أرقام بالضبط.'
        : 'Account last 4 must be exactly 4 digits.');
      return;
    }
    setSubmitting(true);
    const req: SubmitComplaintRequest = {
      channel, category,
      subcategory: subcategory || undefined,
      severity,
      customerNameEn: customerNameEn || undefined,
      customerNameAr: customerNameAr || undefined,
      customerIdType: customerIdType || undefined,
      customerIdLast4: customerIdLast4 || undefined,
      customerPhone: customerPhone || undefined,
      customerEmail: customerEmail || undefined,
      customerCity: customerCity || undefined,
      productCode: productCode || undefined,
      accountLast4: accountLast4 || undefined,
      descriptionEn: descriptionEn || undefined,
      descriptionAr,
    };
    try {
      const resp = await complaintsApi.publicSubmit(req);
      setSubmitted(resp);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إرسال الشكوى' : 'Failed to submit complaint'));
    } finally {
      setSubmitting(false);
    }
  }

  function copyToken() {
    if (!submitted) return;
    navigator.clipboard.writeText(submitted.submissionToken).then(() => {
      setTokenCopied(true);
      setTimeout(() => setTokenCopied(false), 2500);
    }).catch(() => { /* clipboard blocked — user can still select-copy */ });
  }

  if (submitted) {
    return (
      <PublicShell
        title={lang === 'ar' ? 'تم استلام شكواك' : 'Your complaint has been received'}
        sub={lang === 'ar'
          ? submitted.retentionNoticeAr
          : submitted.retentionNoticeEn}
      >
        <Card padded>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 18 }}>
            <CheckCircle2 size={28} style={{ color: 'var(--success)', flexShrink: 0 }} />
            <div>
              <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 4 }}>
                {lang === 'ar' ? `رقم الشكوى: ${submitted.complaintNo}` : `Complaint #: ${submitted.complaintNo}`}
              </div>
              <div style={{ fontSize: 13, color: 'var(--ink-2)' }}>
                {lang === 'ar'
                  ? 'سيتم الإقرار باستلام شكواك خلال خمسة أيام عمل وحلّها خلال عشرين يوم عمل وفقاً لمبادئ حماية عملاء المصارف الصادرة عن ساما.'
                  : 'Your complaint will be acknowledged within 5 working days and resolved within 20 working days per SAMA Banking Consumer Protection Principles.'}
              </div>
            </div>
          </div>

          <div style={{
            background: 'var(--surface-2)', border: '1px solid var(--border)',
            borderRadius: 'var(--r-sm)', padding: 16, marginBottom: 16,
          }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)',
              textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>
              {lang === 'ar' ? 'رمز التتبّع' : 'Tracking code'}
            </div>
            <div style={{
              fontFamily: 'monospace', fontSize: 14, fontWeight: 500,
              padding: '8px 10px', background: 'var(--surface-1)',
              border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
              wordBreak: 'break-all', userSelect: 'all',
            }}>
              {submitted.submissionToken}
            </div>
            <button type="button" onClick={copyToken} style={{
              marginTop: 10, display: 'inline-flex', alignItems: 'center', gap: 6,
              padding: '6px 12px', fontSize: 13, border: '1px solid var(--border)',
              borderRadius: 'var(--r-sm)', background: 'var(--surface-1)',
              color: 'var(--ink-1)', cursor: 'pointer',
            }}>
              <Copy size={14} />
              {tokenCopied
                ? (lang === 'ar' ? 'تم النسخ' : 'Copied')
                : (lang === 'ar' ? 'نسخ' : 'Copy')}
            </button>
          </div>

          <div style={{
            background: 'var(--warning-bg, #fff7e6)', borderInlineStart: '3px solid var(--warning)',
            padding: '12px 14px', borderRadius: 'var(--r-sm)', marginBottom: 16,
            fontSize: 13, color: 'var(--ink-1)',
          }}>
            <strong>{lang === 'ar' ? 'ملاحظة هامة: ' : 'Important: '}</strong>
            {lang === 'ar'
              ? 'احفظ هذا الرمز في مكان آمن. هو الطريقة الوحيدة لمتابعة حالة شكواك. لن نتمكّن من استرجاعه لك في حال فقدانه.'
              : 'Keep this code in a safe place. It is the only way to follow up on your complaint. We cannot recover it for you if lost.'}
          </div>

          <div style={{ display: 'flex', gap: 10 }}>
            <button onClick={() => navigate(`/public/track-complaint?token=${encodeURIComponent(submitted.submissionToken)}`)}
              style={primaryBtnStyle}>
              {lang === 'ar' ? 'تتبّع شكواي' : 'Track my complaint'}
            </button>
          </div>
        </Card>
      </PublicShell>
    );
  }

  return (
    <PublicShell
      title={lang === 'ar'
        ? 'تقديم شكوى بشأن الخدمات المصرفية'
        : 'Submit a complaint about banking services'}
      sub={lang === 'ar'
        ? 'سيتم الإقرار باستلام شكواك خلال خمسة أيام عمل وحلّها خلال عشرين يوم عمل وفقاً لمبادئ حماية عملاء المصارف الصادرة عن ساما.'
        : 'Your complaint will be acknowledged within 5 working days and resolved within 20 working days per SAMA Banking Consumer Protection Principles.'}
    >
      <Card padded>
        <div style={{
          background: 'var(--info-bg, #eff6ff)', borderInlineStart: '3px solid var(--info, #3b82f6)',
          padding: '12px 14px', borderRadius: 'var(--r-sm)', marginBottom: 20,
          fontSize: 13, color: 'var(--ink-1)',
          display: 'flex', alignItems: 'flex-start', gap: 10,
        }}>
          <ShieldCheck size={18} style={{ color: 'var(--info, #3b82f6)', flexShrink: 0, marginTop: 1 }} />
          <div>
            <strong>{lang === 'ar' ? 'حماية بياناتك: ' : 'Your data: '}</strong>
            {lang === 'ar'
              ? 'تُعالَج بياناتك وفقاً لنظام حماية البيانات الشخصية السعودي، ولن تُستخدم إلا للنظر في شكواك.'
              : 'Your data is processed under the Saudi Personal Data Protection Law and used solely to investigate your complaint.'}
          </div>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }} dir={dir}>
          {/* Channel + Category + Severity */}
          <Section title={lang === 'ar' ? 'تصنيف الشكوى' : 'Complaint classification'}>
            <Row>
              <Field label={lang === 'ar' ? 'القناة' : 'Channel'} required>
                <select value={channel} onChange={e => setChannel(e.target.value as Channel)} style={inputStyle}>
                  {(Object.keys(CHANNEL_LABEL) as Channel[]).map(c => (
                    <option key={c} value={c}>{CHANNEL_LABEL[c][lang === 'ar' ? 'ar' : 'en']}</option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'الفئة' : 'Category'} required>
                <select value={category} onChange={e => setCategory(e.target.value as Category)} style={inputStyle}>
                  {(Object.keys(CATEGORY_LABEL) as Category[]).map(c => (
                    <option key={c} value={c}>{CATEGORY_LABEL[c][lang === 'ar' ? 'ar' : 'en']}</option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'الأهمّية' : 'Severity'}>
                <select value={severity} onChange={e => setSeverity(e.target.value as Severity)} style={inputStyle}>
                  {(Object.keys(SEVERITY_LABEL) as Severity[]).map(s => (
                    <option key={s} value={s}>{SEVERITY_LABEL[s][lang === 'ar' ? 'ar' : 'en']}</option>
                  ))}
                </select>
              </Field>
            </Row>
            <Row>
              <Field label={lang === 'ar' ? 'الفئة الفرعية' : 'Sub-category'}>
                <input value={subcategory} onChange={e => setSubcategory(e.target.value)}
                  maxLength={120} style={inputStyle} />
              </Field>
            </Row>
          </Section>

          {/* Customer */}
          <Section title={lang === 'ar' ? 'بياناتك' : 'Your details'}>
            <Row>
              <Field label={lang === 'ar' ? 'الاسم (عربي)' : 'Name (Arabic)'}>
                <input value={customerNameAr} onChange={e => setCustomerNameAr(e.target.value)}
                  dir="rtl" style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'الاسم (إنجليزي)' : 'Name (English)'}>
                <input value={customerNameEn} onChange={e => setCustomerNameEn(e.target.value)}
                  style={inputStyle} />
              </Field>
            </Row>
            <Row>
              <Field label={lang === 'ar' ? 'نوع الهوية' : 'ID type'}>
                <select value={customerIdType} onChange={e => setCustomerIdType(e.target.value as CustomerIdType | '')}
                  style={inputStyle}>
                  <option value="">{lang === 'ar' ? '— لا شيء —' : '— None —'}</option>
                  {(Object.keys(CUSTOMER_ID_TYPE_LABEL) as CustomerIdType[]).map(it => (
                    <option key={it} value={it}>{CUSTOMER_ID_TYPE_LABEL[it][lang === 'ar' ? 'ar' : 'en']}</option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'آخر ٤ أرقام من الهوية' : 'ID — last 4 digits'}>
                <input value={customerIdLast4}
                  onChange={e => setCustomerIdLast4(validateDigits(e.target.value))}
                  inputMode="numeric" pattern="\d{4}" maxLength={4} style={inputStyle} />
              </Field>
            </Row>
            <Row>
              <Field label={lang === 'ar' ? 'الجوال' : 'Phone'}>
                <input value={customerPhone} onChange={e => setCustomerPhone(e.target.value)}
                  type="tel" style={inputStyle} placeholder="+966 5XX XXX XXX" />
              </Field>
              <Field label={lang === 'ar' ? 'البريد الإلكتروني' : 'Email'}>
                <input value={customerEmail} onChange={e => setCustomerEmail(e.target.value)}
                  type="email" style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'المدينة' : 'City'}>
                <input value={customerCity} onChange={e => setCustomerCity(e.target.value)}
                  style={inputStyle} />
              </Field>
            </Row>
          </Section>

          {/* Product */}
          <Section title={lang === 'ar' ? 'بيانات المنتج' : 'Product details'}>
            <Row>
              <Field label={lang === 'ar' ? 'رمز المنتج' : 'Product code'}>
                <input value={productCode} onChange={e => setProductCode(e.target.value)}
                  style={inputStyle} placeholder={lang === 'ar' ? 'مثال: CC-PLAT' : 'e.g. CC-PLAT'} />
              </Field>
              <Field label={lang === 'ar' ? 'آخر ٤ أرقام من الحساب/البطاقة' : 'Account/card — last 4 digits'}>
                <input value={accountLast4}
                  onChange={e => setAccountLast4(validateDigits(e.target.value))}
                  inputMode="numeric" pattern="\d{4}" maxLength={4} style={inputStyle} />
              </Field>
            </Row>
          </Section>

          {/* Narrative */}
          <Section title={lang === 'ar' ? 'تفاصيل الشكوى' : 'Complaint details'}>
            <Row>
              <Field label={lang === 'ar' ? 'تفاصيل الشكوى (عربي)' : 'Description (Arabic)'} required>
                <textarea value={descriptionAr} onChange={e => setDescriptionAr(e.target.value)}
                  required rows={6} dir="rtl" style={{ ...inputStyle, resize: 'vertical' }} />
              </Field>
            </Row>
            <Row>
              <Field label={lang === 'ar' ? 'تفاصيل الشكوى (إنجليزي)' : 'Description (English)'}>
                <textarea value={descriptionEn} onChange={e => setDescriptionEn(e.target.value)}
                  rows={6} style={{ ...inputStyle, resize: 'vertical' }} />
              </Field>
            </Row>
          </Section>

          {error && (
            <div style={{
              padding: '10px 14px', borderRadius: 'var(--r-sm)',
              background: 'var(--danger-bg)', color: 'var(--danger-fg)',
              fontSize: 13, display: 'flex', alignItems: 'center', gap: 8,
            }}>
              <AlertCircle size={16} /> {error}
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 8 }}>
            <button type="submit" disabled={submitting} style={{
              ...primaryBtnStyle,
              cursor: submitting ? 'not-allowed' : 'pointer',
              opacity: submitting ? 0.6 : 1,
            }}>
              {submitting
                ? (lang === 'ar' ? 'جارٍ الإرسال…' : 'Submitting…')
                : (lang === 'ar' ? 'إرسال الشكوى' : 'Submit complaint')}
            </button>
          </div>
        </form>
      </Card>
    </PublicShell>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '8px 10px',
  fontSize: 13,
  border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)',
  color: 'var(--ink-1)',
  boxSizing: 'border-box',
};

const primaryBtnStyle: React.CSSProperties = {
  padding: '10px 24px',
  fontSize: 14,
  fontWeight: 500,
  background: 'var(--primary)',
  color: '#fff',
  border: 'none',
  borderRadius: 'var(--r-sm)',
  cursor: 'pointer',
};

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{
        fontSize: 12, fontWeight: 600, color: 'var(--ink-3)',
        textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8,
      }}>{title}</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>{children}</div>
    </div>
  );
}

function Row({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
      {children}
    </div>
  );
}

function Field({ label, required, children }:
  { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <div style={{ flex: 1, minWidth: 220 }}>
      <label style={{ display: 'block', fontSize: 12, fontWeight: 500,
        color: 'var(--ink-2)', marginBottom: 4 }}>
        {label}{required ? <span style={{ color: 'var(--danger)', marginInlineStart: 4 }}>*</span> : null}
      </label>
      {children}
    </div>
  );
}
