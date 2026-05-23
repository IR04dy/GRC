import { useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { ShieldCheck, AlertTriangle, CheckCircle2, Upload } from 'lucide-react';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  getAttestationByToken,
  submitAttestationByToken,
  type VendorBcpAttestation,
} from '../../lib/vendorBcp';

/**
 * Slice 10-D — Vendor-portal stub.
 *
 * <p>Public page (no JWT) that resolves a per-row attestation by its
 * opaque {@code submission_token} and lets the vendor's contact submit
 * the response. v1 logs the evidence file path only — the full upload
 * pipeline (S3 + virus-scan + tamper-evident hash) is a follow-up
 * documented in the Slice 10-D PR body.
 *
 * <p>Locale: defaults to whatever the i18n provider says, but a vendor
 * accessing via an Arabic-domain alias will get RTL by default. Both
 * field labels and helper text are bilingual.
 */
export function VendorAttestationSubmitPage() {
  const { token } = useParams<{ token: string }>();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [item, setItem] = useState<VendorBcpAttestation | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Form fields
  const [rto, setRto] = useState('');
  const [mtpd, setMtpd] = useState('');
  const [hasTested, setHasTested] = useState<'yes' | 'no' | ''>('');
  const [bcpDate, setBcpDate] = useState('');
  const [drDate, setDrDate] = useState('');
  const [subprocs, setSubprocs] = useState('');
  const [filePath, setFilePath] = useState('');
  const [fileName, setFileName] = useState('');
  const [contactName, setContactName] = useState('');
  const [contactEmail, setContactEmail] = useState('');

  useEffect(() => {
    if (!token) return;
    (async () => {
      try {
        const a = await getAttestationByToken(token);
        setItem(a);
      } catch (e) {
        setError(e instanceof ApiError ? e.message
          : (isAr ? 'رابط غير صالح أو منتهي' : 'Invalid or expired link'));
      } finally {
        setLoading(false);
      }
    })();
  }, [token, isAr]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!token) return;
    const rtoN = Number(rto);
    const mtpdN = Number(mtpd);
    if (!Number.isFinite(rtoN) || rtoN <= 0) {
      toast.danger(isAr ? 'يرجى إدخال قيمة RTO صحيحة' : 'Please enter a valid RTO');
      return;
    }
    if (!Number.isFinite(mtpdN) || mtpdN <= 0) {
      toast.danger(isAr ? 'يرجى إدخال قيمة MTPD صحيحة' : 'Please enter a valid MTPD');
      return;
    }
    if (hasTested === '') {
      toast.danger(isAr ? 'يرجى الإجابة عن سؤال اختبار الخطة' : 'Please answer the BCP tested question');
      return;
    }
    try {
      await submitAttestationByToken(token, {
        rtoHoursAttested: rtoN,
        mtpdHoursAttested: mtpdN,
        hasTestedBcp: hasTested === 'yes',
        lastBcpTestDate: bcpDate || undefined,
        lastDrTestDate: drDate || undefined,
        subprocessorDependencies: subprocs || undefined,
        evidenceFilePath: filePath || undefined,
        evidenceFileName: fileName || undefined,
        submittedByContactName: contactName || undefined,
        submittedByContactEmail: contactEmail || undefined,
      });
      setSubmitted(true);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message
        : (isAr ? 'فشل التقديم' : 'Submission failed'));
    }
  }

  const dir = isAr ? 'rtl' : 'ltr';
  const containerStyle: React.CSSProperties = {
    maxWidth: 640, margin: '0 auto', padding: 32, direction: dir,
    fontFamily: 'system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif',
    color: '#1f2937',
  };

  if (loading) {
    return <div style={containerStyle}><p>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</p></div>;
  }
  if (error) {
    return (
      <div style={containerStyle}>
        <div style={{ borderTop: '4px solid #b91c1c', paddingTop: 16 }}>
          <AlertTriangle size={32} color="#b91c1c" />
          <h1 style={{ color: '#7f1d1d', fontSize: 20 }}>{isAr ? 'الرابط غير صالح' : 'Invalid link'}</h1>
          <p>{error}</p>
        </div>
      </div>
    );
  }
  if (submitted || (item && ['SUBMITTED', 'ACCEPTED', 'REJECTED', 'NA_OUT_OF_SCOPE'].includes(item.status))) {
    return (
      <div style={containerStyle}>
        <div style={{ borderTop: '4px solid #15803d', paddingTop: 16 }}>
          <CheckCircle2 size={32} color="#15803d" />
          <h1 style={{ color: '#14532d', fontSize: 20 }}>
            {isAr ? 'شكراً — تم استلام شهادتكم' : 'Thank you — your attestation has been received'}
          </h1>
          <p>{isAr
            ? 'سيقوم فريق إدارة مخاطر الأطراف الخارجية لدينا بمراجعة المعلومات والتواصل معكم عند الحاجة.'
            : 'Our Third-Party Risk Management team will review the submission and follow up if needed.'}</p>
        </div>
      </div>
    );
  }

  // Active submission form
  return (
    <div style={containerStyle}>
      <div style={{ borderTop: '4px solid #1d4ed8', paddingTop: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
          <ShieldCheck size={28} color="#1d4ed8" />
          <div>
            <div style={{ fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#1d4ed8', fontWeight: 700 }}>
              {isAr ? 'بوابة الموردين — شهادة استمرارية الأعمال' : 'Vendor portal — BCP attestation'}
            </div>
            <h1 style={{ color: '#1e3a8a', fontSize: 22, margin: '4px 0' }}>
              {isAr ? 'إرسال شهادة استمرارية الأعمال' : 'Submit your BCP attestation'}
            </h1>
          </div>
        </div>

        <p style={{ fontSize: 14, color: '#374151', lineHeight: 1.6 }}>
          {isAr
            ? 'يرجى تعبئة الحقول التالية بالقيم المُعلنة لديكم لقدرة استمرارية الأعمال والتعافي من الكوارث. هذه المعلومات مطلوبة وفق إطار استمرارية الأعمال لمؤسسة النقد وضوابط الهيئة الوطنية للأمن السيبراني.'
            : 'Please complete the fields below with your declared Business Continuity and Disaster Recovery values. This information is required under SAMA BCF and NCA ECC-2 Annex H.'}
        </p>

        <form onSubmit={onSubmit} style={{ marginTop: 16 }}>
          <FormRow label={isAr ? 'RTO المُعلن (ساعات)' : 'Declared RTO (hours)'} required>
            <input type="number" min={1} value={rto} onChange={e => setRto(e.target.value)}
                   className="grc-input" required />
          </FormRow>
          <FormRow label={isAr ? 'MTPD المُعلن (ساعات)' : 'Declared MTPD (hours)'} required>
            <input type="number" min={1} value={mtpd} onChange={e => setMtpd(e.target.value)}
                   className="grc-input" required />
          </FormRow>
          <FormRow label={isAr ? 'هل خطة الاستمرارية مُختبَرة خلال آخر 12 شهراً؟' : 'Has your BCP been tested in the last 12 months?'} required>
            <div style={{ display: 'flex', gap: 16 }}>
              <label><input type="radio" name="tested" value="yes"
                onChange={() => setHasTested('yes')} checked={hasTested === 'yes'} /> {isAr ? 'نعم' : 'Yes'}</label>
              <label><input type="radio" name="tested" value="no"
                onChange={() => setHasTested('no')} checked={hasTested === 'no'} /> {isAr ? 'لا' : 'No'}</label>
            </div>
          </FormRow>
          <FormRow label={isAr ? 'تاريخ آخر اختبار للخطة' : 'Last BCP test date'}>
            <input type="date" value={bcpDate} onChange={e => setBcpDate(e.target.value)}
                   className="grc-input" />
          </FormRow>
          <FormRow label={isAr ? 'تاريخ آخر اختبار للتعافي' : 'Last DR test date'}>
            <input type="date" value={drDate} onChange={e => setDrDate(e.target.value)}
                   className="grc-input" />
          </FormRow>
          <FormRow
            label={isAr ? 'المعالجون الفرعيون / الأطراف الرابعة' : 'Sub-processors / fourth parties'}
            helper={isAr
              ? 'قائمة JSON بالأطراف الفرعية: [{"name":"…","role":"…"}]'
              : 'JSON list of sub-processors: [{"name":"…","role":"…"}]'}
          >
            <textarea rows={4} value={subprocs} onChange={e => setSubprocs(e.target.value)}
                      className="grc-input" />
          </FormRow>
          <FormRow
            label={isAr ? 'مسار ملف الدليل' : 'Evidence file path'}
            helper={isAr
              ? 'النسخة 1: يُسجَّل المسار فقط — يأتي رفع الملف الكامل في تحديث لاحق.'
              : 'v1: path is logged only — full upload pipeline ships in a follow-up.'}
          >
            <div style={{ display: 'flex', gap: 8 }}>
              <input type="text" placeholder="/tmp/bcp/report.pdf"
                     value={filePath} onChange={e => setFilePath(e.target.value)}
                     className="grc-input" style={{ flex: 1 }} />
              <input type="text" placeholder={isAr ? 'اسم الملف' : 'File name'}
                     value={fileName} onChange={e => setFileName(e.target.value)}
                     className="grc-input" style={{ flex: 1 }} />
            </div>
          </FormRow>
          <FormRow label={isAr ? 'الاسم' : 'Your name'}>
            <input type="text" value={contactName} onChange={e => setContactName(e.target.value)}
                   className="grc-input" />
          </FormRow>
          <FormRow label={isAr ? 'البريد الإلكتروني' : 'Email'}>
            <input type="email" value={contactEmail} onChange={e => setContactEmail(e.target.value)}
                   className="grc-input" />
          </FormRow>

          <button type="submit"
                  style={{
                    marginTop: 20, background: '#1d4ed8', color: '#ffffff',
                    border: 0, padding: '12px 24px', borderRadius: 4,
                    fontWeight: 700, fontSize: 15, cursor: 'pointer',
                    display: 'inline-flex', alignItems: 'center', gap: 8,
                  }}>
            <Upload size={16} />
            {isAr ? 'إرسال الشهادة' : 'Submit attestation'}
          </button>
        </form>

        <hr style={{ border: 0, borderTop: '1px solid #e5e7eb', margin: '24px 0 16px' }} />
        <p style={{ fontSize: 12, color: '#9ca3af' }}>
          {isAr
            ? 'منصة TSS GRC · بوابة الموردين — شهادات استمرارية الأعمال'
            : 'TSS GRC Platform · Vendor portal — BCP attestations'}
        </p>
      </div>
    </div>
  );
}

function FormRow({
  label, helper, required, children,
}: {
  label: string;
  helper?: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div style={{ marginBottom: 14 }}>
      <label style={{ display: 'block', fontSize: 13, color: '#374151', marginBottom: 4, fontWeight: 600 }}>
        {label} {required && <span style={{ color: '#b91c1c' }}>*</span>}
      </label>
      {children}
      {helper && (
        <div style={{ fontSize: 11, color: '#9ca3af', marginTop: 4 }}>{helper}</div>
      )}
    </div>
  );
}
