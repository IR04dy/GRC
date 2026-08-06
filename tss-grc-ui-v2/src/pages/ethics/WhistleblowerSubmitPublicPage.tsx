import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, CheckCircle2, Copy, ShieldAlert } from 'lucide-react';
import { PublicShell } from '../../components/layout/PublicShell';
import { Card } from '../../components/ui/Card';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { readPortalOrg, portalLinkMisconfigured } from '../../lib/publicPortalOrg';
import {
  whistleblowerApi,
  CATEGORY_LABEL, SEVERITY_LABEL,
  type Category, type Severity, type ReporterType,
  type CaseSubmissionRequest, type CaseSubmissionResponse,
} from '../../lib/whistleblower';

/**
 * Public submission page — /ethics/whistleblower/submit.
 *
 * <p>Mandated by SAMA CG Art.28 + CMA CG Art.87. Anonymous-by-default;
 * the reporter retains a tracking token (UUID) returned ONCE on
 * submission. We never log the reporter's IP address on this path —
 * see {@code WhistleblowerCaseService.anonymousAudit()}.
 */
export function WhistleblowerSubmitPublicPage() {
  const { lang, dir } = useI18n();
  const navigate = useNavigate();

  // The tenant whose hotline this link belongs to (F2-83). Read once from the URL.
  const portalOrg = readPortalOrg();

  const [isAnonymous, setIsAnonymous] = useState(true);
  const [reporterType, setReporterType] = useState<ReporterType>('ANONYMOUS');
  const [reporterName, setReporterName] = useState('');
  const [reporterEmail, setReporterEmail] = useState('');
  const [reporterPhone, setReporterPhone] = useState('');
  const [reporterEmployeeId, setReporterEmployeeId] = useState('');

  const [category, setCategory] = useState<Category>('FRAUD');
  const [severity, setSeverity] = useState<Severity>('MEDIUM');
  const [subjectEn, setSubjectEn] = useState('');
  const [subjectAr, setSubjectAr] = useState('');
  const [narrativeEn, setNarrativeEn] = useState('');
  const [narrativeAr, setNarrativeAr] = useState('');
  const [partiesInvolved, setPartiesInvolved] = useState('');
  const [allegedPeriodStart, setAllegedPeriodStart] = useState('');
  const [allegedPeriodEnd, setAllegedPeriodEnd] = useState('');
  const [involvesSeniorExecutive, setInvolvesSeniorExecutive] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState<CaseSubmissionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tokenCopied, setTokenCopied] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    // Unreachable in practice — the misconfigured-link screen renders instead of this form — but
    // the form must never post without a tenant, so the invariant is stated where it is enforced
    // rather than relied on from three screens away.
    if (!portalOrg) return;
    setError(null);
    setSubmitting(true);
    const req: CaseSubmissionRequest = {
      submissionChannel: 'PUBLIC_PORTAL',
      reporterType: isAnonymous ? 'ANONYMOUS' : reporterType,
      reporterName: isAnonymous ? undefined : reporterName || undefined,
      reporterEmail: isAnonymous ? undefined : reporterEmail || undefined,
      reporterPhone: isAnonymous ? undefined : reporterPhone || undefined,
      reporterEmployeeId: isAnonymous ? undefined : reporterEmployeeId || undefined,
      isAnonymous,
      category,
      severity,
      subjectEn,
      subjectAr,
      narrativeEn,
      narrativeAr,
      partiesInvolved: partiesInvolved || undefined,
      allegedPeriodStart: allegedPeriodStart || undefined,
      allegedPeriodEnd: allegedPeriodEnd || undefined,
      involvesSeniorExecutive,
    };
    try {
      const resp = await whistleblowerApi.publicSubmit(req, portalOrg);
      setSubmitted(resp);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إرسال البلاغ' : 'Failed to submit'));
    } finally {
      setSubmitting(false);
    }
  }

  function copyToken() {
    if (!submitted) return;
    navigator.clipboard.writeText(submitted.submissionToken).then(() => {
      setTokenCopied(true);
      setTimeout(() => setTokenCopied(false), 2000);
    }).catch(() => { /* clipboard blocked — user can still select-copy */ });
  }

  // F2-83 — the portal link must name the receiving tenant. Detected here, before the reporter
  // types anything: this form collects a confidential account of misconduct, and a page that
  // accepts one and then discards it because the link was misconfigured is a worse failure than
  // one that says so up front.
  if (!portalOrg) {
    const msg = portalLinkMisconfigured(lang);
    return (
      <PublicShell title={msg.title} sub="">
        <Card padded>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14 }} dir={dir}>
            <AlertCircle size={28} style={{ color: 'var(--danger)', flexShrink: 0 }} />
            <div style={{ fontSize: 14, lineHeight: 1.7 }}>{msg.body}</div>
          </div>
        </Card>
      </PublicShell>
    );
  }

  if (submitted) {
    return (
      <PublicShell
        title={lang === 'ar' ? 'تم استلام بلاغك' : 'Your report has been received'}
        sub={lang === 'ar'
          ? 'يرجى حفظ رمز التتبّع — لا يمكننا استرجاعه لك.'
          : 'Please save your tracking token — we cannot recover it for you.'}
      >
        <Card padded>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 18 }}>
            <CheckCircle2 size={28} style={{ color: 'var(--success)', flexShrink: 0 }} />
            <div>
              <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 4 }}>
                {lang === 'ar' ? `رقم البلاغ: ${submitted.caseCode}` : `Case code: ${submitted.caseCode}`}
              </div>
              <div style={{ fontSize: 13, color: 'var(--ink-2)' }}>
                {lang === 'ar'
                  ? 'سيقوم مسؤول الأخلاقيات بمراجعة بلاغك. ستظهر التحديثات على صفحة الحالة عند توفّرها.'
                  : 'The Ethics Officer will review your report. Updates will appear on the status page as they become available.'}
              </div>
            </div>
          </div>

          <div style={{
            background: 'var(--surface-2)', border: '1px solid var(--border)',
            borderRadius: 'var(--r-sm)', padding: 16, marginBottom: 16,
          }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)',
              textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>
              {lang === 'ar' ? 'رمز التتبّع' : 'Tracking token'}
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
            background: 'var(--warning-50, #fff7e6)', borderLeft: '3px solid var(--warning, #d97706)',
            padding: '12px 14px', borderRadius: 'var(--r-sm)', marginBottom: 16,
            fontSize: 13, color: 'var(--ink-1)',
          }}>
            <strong>{lang === 'ar' ? 'ملاحظة هامة: ' : 'Important: '}</strong>
            {lang === 'ar'
              ? 'احتفظ بهذا الرمز في مكان آمن. هو الطريقة الوحيدة لمتابعة حالة بلاغك والردّ على استفسارات المحقّق. لن نتمكّن من استرجاعه لك في حال فقدانه.'
              : 'Keep this token in a safe place. It is the only way to check the status of your report and reply to the investigator. We cannot recover it for you if lost.'}
          </div>

          <div style={{ display: 'flex', gap: 10 }}>
            <button onClick={() => navigate(`/ethics/whistleblower/status/${submitted.submissionToken}`)}
              style={{
                padding: '10px 18px', fontSize: 14, fontWeight: 500,
                background: 'var(--primary)', color: 'var(--on-primary, #fff)',
                border: 'none', borderRadius: 'var(--r-sm)', cursor: 'pointer',
              }}>
              {lang === 'ar' ? 'الانتقال لصفحة الحالة' : 'Go to status page'}
            </button>
          </div>
        </Card>
      </PublicShell>
    );
  }

  return (
    <PublicShell
      title={lang === 'ar' ? 'الإبلاغ عن المخالفات الأخلاقية' : 'Report an ethical concern'}
      sub={lang === 'ar'
        ? 'يحقّ لك الإبلاغ عن أي مخالفة أخلاقية، احتيال، تضارب مصالح، أو إخلال بالأنظمة دون الكشف عن هويتك. تُحمى هويتك ولا تُسجَّل عناوين IP. السرية مكفولة بموجب لوائح حوكمة الشركات الصادرة عن البنك المركزي السعودي وهيئة السوق المالية.'
        : 'You may report any ethical breach, fraud, conflict of interest, or regulatory violation without disclosing your identity. Your identity is protected and IP addresses are not logged. Confidentiality is guaranteed under SAMA + CMA Corporate Governance regulations.'}
    >
      <Card padded>
        <div style={{
          background: 'var(--info-50, #eff6ff)', borderLeft: '3px solid var(--primary)',
          padding: '12px 14px', borderRadius: 'var(--r-sm)', marginBottom: 20,
          fontSize: 13, color: 'var(--ink-1)',
          display: 'flex', alignItems: 'flex-start', gap: 10,
        }}>
          <ShieldAlert size={18} style={{ color: 'var(--primary)', flexShrink: 0, marginTop: 1 }} />
          <div>
            <strong>{lang === 'ar' ? 'حماية المبلّغين من الانتقام: ' : 'Retaliation protection: '}</strong>
            {lang === 'ar'
              ? 'يُحظر اتخاذ أي إجراء تأديبي أو انتقامي ضد المبلّغ بحسن نية. وأي محاولة للانتقام تُعدّ مخالفة منفصلة يُتعامل معها بسرية تامة عبر مسؤول مستقل.'
              : 'Any disciplinary or retaliatory action against a good-faith reporter is prohibited. Any attempted retaliation is a separate offence handled confidentially by an independent protector.'}
          </div>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }} dir={dir}>
          {/* Anonymous toggle */}
          <Section title={lang === 'ar' ? 'معلومات المُبلِّغ' : 'Reporter information'}>
            <Row>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={isAnonymous}
                  onChange={e => {
                    setIsAnonymous(e.target.checked);
                    if (e.target.checked) setReporterType('ANONYMOUS');
                  }}
                />
                {lang === 'ar' ? 'بلاغ مجهول الهوية (لن نطلب اسمك)' : 'Submit anonymously (we will not ask for your name)'}
              </label>
            </Row>
            {!isAnonymous && (
              <>
                <Row>
                  <Field label={lang === 'ar' ? 'نوع المُبلِّغ' : 'Reporter type'}>
                    <select value={reporterType}
                      onChange={e => setReporterType(e.target.value as ReporterType)}
                      style={inputStyle}>
                      <option value="EMPLOYEE">{lang === 'ar' ? 'موظف' : 'Employee'}</option>
                      <option value="MANAGER">{lang === 'ar' ? 'مدير' : 'Manager'}</option>
                      <option value="CONTRACTOR">{lang === 'ar' ? 'متعاقد' : 'Contractor'}</option>
                      <option value="VENDOR">{lang === 'ar' ? 'مورّد' : 'Vendor'}</option>
                      <option value="CUSTOMER">{lang === 'ar' ? 'عميل' : 'Customer'}</option>
                      <option value="EXTERNAL">{lang === 'ar' ? 'خارجي' : 'External'}</option>
                    </select>
                  </Field>
                </Row>
                <Row>
                  <Field label={lang === 'ar' ? 'الاسم' : 'Name'}>
                    <input value={reporterName} onChange={e => setReporterName(e.target.value)}
                      style={inputStyle} />
                  </Field>
                  <Field label={lang === 'ar' ? 'البريد الإلكتروني' : 'Email'}>
                    <input type="email" value={reporterEmail} onChange={e => setReporterEmail(e.target.value)}
                      style={inputStyle} />
                  </Field>
                </Row>
                <Row>
                  <Field label={lang === 'ar' ? 'الهاتف' : 'Phone'}>
                    <input value={reporterPhone} onChange={e => setReporterPhone(e.target.value)}
                      style={inputStyle} />
                  </Field>
                  <Field label={lang === 'ar' ? 'الرقم الوظيفي' : 'Employee ID'}>
                    <input value={reporterEmployeeId} onChange={e => setReporterEmployeeId(e.target.value)}
                      style={inputStyle} />
                  </Field>
                </Row>
              </>
            )}
          </Section>

          {/* Category + Severity */}
          <Section title={lang === 'ar' ? 'تصنيف البلاغ' : 'Report classification'}>
            <Row>
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
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, cursor: 'pointer' }}>
                <input type="checkbox" checked={involvesSeniorExecutive}
                  onChange={e => setInvolvesSeniorExecutive(e.target.checked)} />
                {lang === 'ar'
                  ? 'البلاغ يتعلّق بمسؤول تنفيذي رفيع — يفعّل التصعيد لرئيس لجنة المراجعة'
                  : 'Report involves a senior executive — triggers escalation to the Audit Committee Chair'}
              </label>
            </Row>
          </Section>

          {/* Subject + Narrative — bilingual */}
          <Section title={lang === 'ar' ? 'تفاصيل البلاغ' : 'Report details'}>
            <Row>
              <Field label={lang === 'ar' ? 'الموضوع (إنجليزي)' : 'Subject (English)'} required>
                <input value={subjectEn} onChange={e => setSubjectEn(e.target.value)}
                  required maxLength={255} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'الموضوع (عربي)' : 'Subject (Arabic)'} required>
                <input value={subjectAr} onChange={e => setSubjectAr(e.target.value)}
                  required maxLength={255} dir="rtl" style={inputStyle} />
              </Field>
            </Row>
            <Row>
              <Field label={lang === 'ar' ? 'تفاصيل البلاغ (إنجليزي)' : 'Narrative (English)'} required>
                <textarea value={narrativeEn} onChange={e => setNarrativeEn(e.target.value)}
                  required rows={6} style={{ ...inputStyle, resize: 'vertical' }} />
              </Field>
            </Row>
            <Row>
              <Field label={lang === 'ar' ? 'تفاصيل البلاغ (عربي)' : 'Narrative (Arabic)'} required>
                <textarea value={narrativeAr} onChange={e => setNarrativeAr(e.target.value)}
                  required rows={6} dir="rtl" style={{ ...inputStyle, resize: 'vertical' }} />
              </Field>
            </Row>
            <Row>
              <Field label={lang === 'ar' ? 'الأطراف ذات العلاقة' : 'Parties involved'}>
                <input value={partiesInvolved} onChange={e => setPartiesInvolved(e.target.value)}
                  maxLength={1000} style={inputStyle} />
              </Field>
            </Row>
            <Row>
              <Field label={lang === 'ar' ? 'بداية الفترة' : 'Period start'}>
                <input type="date" value={allegedPeriodStart}
                  onChange={e => setAllegedPeriodStart(e.target.value)} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'نهاية الفترة' : 'Period end'}>
                <input type="date" value={allegedPeriodEnd}
                  onChange={e => setAllegedPeriodEnd(e.target.value)} style={inputStyle} />
              </Field>
            </Row>
          </Section>

          {error && (
            <div style={{
              padding: '10px 14px', borderRadius: 'var(--r-sm)',
              background: 'var(--danger-50, #fef2f2)', color: 'var(--danger)',
              fontSize: 13, display: 'flex', alignItems: 'center', gap: 8,
            }}>
              <AlertCircle size={16} /> {error}
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 8 }}>
            <button type="submit" disabled={submitting} style={{
              padding: '10px 24px', fontSize: 14, fontWeight: 500,
              background: 'var(--primary)', color: 'var(--on-primary, #fff)',
              border: 'none', borderRadius: 'var(--r-sm)',
              cursor: submitting ? 'not-allowed' : 'pointer',
              opacity: submitting ? 0.6 : 1,
            }}>
              {submitting
                ? (lang === 'ar' ? 'جارٍ الإرسال…' : 'Submitting…')
                : (lang === 'ar' ? 'إرسال البلاغ' : 'Submit report')}
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
      <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--ink-2)', marginBottom: 4 }}>
        {label}{required ? <span style={{ color: 'var(--danger)', marginInlineStart: 4 }}>*</span> : null}
      </label>
      {children}
    </div>
  );
}
