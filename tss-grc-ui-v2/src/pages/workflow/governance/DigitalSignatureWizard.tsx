import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { FileCheck2, Lock } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { tlabel } from '../../../lib/labels';
import { useToast } from '../../../lib/toast';
import { submitWizard } from '../../../lib/submitWizard';

/**
 * §7.6 Cross-module — Digital Signature wizard.
 *
 * <p>Wraps any approvable record (policy, BCP, AAR, board pack, attestation,
 * risk acceptance) in a signature transaction with audit trail.
 *
 * <p>2 steps:
 *   1. Record selection + signer profile + 2FA/Nafath method
 *   2. Sign + audit trail
 */
type RecordKind =
  | 'POLICY'
  | 'BCP'
  | 'DRP'
  | 'AAR'
  | 'BOARD_PACK'
  | 'RISK_ACCEPTANCE'
  | 'ATTESTATION'
  | 'DPIA'
  | 'MGMT_REVIEW';

const KIND_LABELS: Record<RecordKind, { en: string; ar: string }> = {
  POLICY:           { en: 'Policy',                 ar: 'سياسة' },
  BCP:              { en: 'Business Continuity Plan', ar: 'خطة استمرارية الأعمال' },
  DRP:              { en: 'Disaster Recovery Plan', ar: 'خطة التعافي' },
  AAR:              { en: 'After-Action Report',    ar: 'تقرير ما بعد الحدث' },
  BOARD_PACK:       { en: 'Board pack',             ar: 'ملف المجلس' },
  RISK_ACCEPTANCE:  { en: 'Risk acceptance',        ar: 'قبول مخاطر' },
  ATTESTATION:      { en: 'Periodic attestation',   ar: 'إقرار دوري' },
  DPIA:             { en: 'DPIA',                   ar: 'تقييم أثر الخصوصية' },
  MGMT_REVIEW:      { en: 'Management review',      ar: 'مراجعة إدارية' },
};

type SigMethod = 'NAFATH' | 'OTP_SMS' | 'OTP_EMAIL' | 'HARDWARE_TOKEN' | 'INTERNAL_CERT';

interface SignatureDraft {
  recordKind: RecordKind;
  recordRef: string;
  recordTitle: string;
  signerRole: string;
  signerName: string;
  method: SigMethod;
  intent: 'APPROVAL' | 'CERTIFY' | 'WITNESS' | 'REJECT';
  otpEntered?: string;
  ack1: boolean;
  ack2: boolean;
  notes?: string;
}

export function DigitalSignatureWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<SignatureDraft>({
    recordKind: 'POLICY',
    recordRef: '',
    recordTitle: '',
    signerRole: '',
    signerName: '',
    method: 'NAFATH',
    intent: 'APPROVAL',
    ack1: false,
    ack2: false,
  });

  function set<K extends keyof SignatureDraft>(key: K, value: SignatureDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  async function submit() {
    setSubmitting(true);
    try {
      // Real Nafath / Adobe Sign / DocuSign adapter is vendor-blocked. For
      // now the signature transaction is captured as a workflow submission
      // (immutable audit ledger) — swapping in a provider later only needs
      // the redirect/callback path, not a schema change.
      const created = await submitWizard({
        workflowId: 'governance-digital-signature',
        workflowModule: 'GOVERNANCE',
        titleEn: `${draft.intent}: ${draft.recordRef} (${draft.recordKind})`,
        summaryEn: `${draft.signerName} (${draft.signerRole}) via ${draft.method}`
                 + (draft.intent === 'REJECT' ? ' · REJECTED' : ''),
        status: draft.intent === 'REJECT' ? 'REJECTED' : 'APPROVED',
        draft: { ...draft, otpEntered: draft.otpEntered ? '***' : undefined },
      });
      toast.success(isAr
        ? `تم توقيع السجل (#${created.id})`
        : `Signature #${created.id} captured — audit trail saved`);
      navigate('/signatures');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const needsOtp = draft.method !== 'NAFATH' && draft.method !== 'HARDWARE_TOKEN';

  const steps: WizardStep[] = [
    {
      id: 'record',
      title: '1. Record + signer', titleAr: '١. السجل والموقّع',
      Icon: FileCheck2,
      validate: () =>
        !draft.recordRef.trim() || !draft.recordTitle.trim() ||
        !draft.signerRole.trim() || !draft.signerName.trim()
          ? (isAr ? 'مرجع السجل وعنوانه واسم الموقع ودوره مطلوبة'
                  : 'Record reference, title, signer name + role required')
          : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'نوع السجل' : 'Record kind'}>
            <select className="input" value={draft.recordKind}
                    onChange={e => set('recordKind', e.target.value as RecordKind)}>
              {(Object.keys(KIND_LABELS) as RecordKind[]).map(k => (
                <option key={k} value={k}>{tlabel(KIND_LABELS, k, isAr)}</option>
              ))}
            </select>
          </Field>
          <Field label={isAr ? 'نية التوقيع' : 'Signing intent'}>
            <select className="input" value={draft.intent}
                    onChange={e => set('intent', e.target.value as SignatureDraft['intent'])}>
              <option value="APPROVAL">Approval</option>
              <option value="CERTIFY">Certification (read + attest)</option>
              <option value="WITNESS">Witness</option>
              <option value="REJECT">Reject with reason</option>
            </select>
          </Field>
          <Field label={isAr ? 'مرجع السجل' : 'Record reference'} required>
            <input className="input" value={draft.recordRef}
                   onChange={e => set('recordRef', e.target.value)}
                   placeholder="e.g. POL-2026-014 / AAR-2026-002"/>
          </Field>
          <Field label={isAr ? 'عنوان السجل' : 'Record title'} required>
            <input className="input" value={draft.recordTitle}
                   onChange={e => set('recordTitle', e.target.value)}
                   placeholder={isAr ? 'مثال: سياسة الاحتفاظ بالبيانات' : 'e.g. Data Retention Policy'}/>
          </Field>
          <Field label={isAr ? 'اسم الموقّع' : 'Signer name'} required>
            <input className="input" value={draft.signerName}
                   onChange={e => set('signerName', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'دور الموقّع' : 'Signer role'} required>
            <input className="input" value={draft.signerRole}
                   onChange={e => set('signerRole', e.target.value)}
                   placeholder={isAr ? 'مثال: الرئيس التنفيذي' : 'e.g. CEO'}/>
          </Field>
          <Field label={isAr ? 'طريقة التوقيع' : 'Signature method'} full>
            <select className="input" value={draft.method}
                    onChange={e => set('method', e.target.value as SigMethod)}>
              <option value="NAFATH">Nafath (SSO)</option>
              <option value="OTP_SMS">OTP — SMS</option>
              <option value="OTP_EMAIL">OTP — Email</option>
              <option value="HARDWARE_TOKEN">Hardware token (YubiKey)</option>
              <option value="INTERNAL_CERT">Internal certificate</option>
            </select>
          </Field>
        </div>
      ),
    },

    {
      id: 'sign',
      title: '2. Sign + audit trail', titleAr: '٢. التوقيع وسجل التدقيق',
      Icon: Lock,
      validate: () =>
        !draft.ack1 || !draft.ack2
          ? (isAr ? 'يلزم تأكيد الإقرارين' : 'Both acknowledgements required')
          : needsOtp && !draft.otpEntered?.trim()
            ? (isAr ? 'يلزم إدخال OTP' : 'OTP entry required')
            : null,
      content: () => (
        <div className="wf-form-grid">
          <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
            <Badge tone="info">{tlabel(KIND_LABELS, draft.recordKind, isAr)}</Badge>{' '}
            <strong>{draft.recordRef}</strong> · {draft.recordTitle}
          </div>
          {needsOtp && (
            <Field label={isAr ? 'رمز OTP' : 'OTP code'} required>
              <input className="input" inputMode="numeric" autoComplete="one-time-code"
                     value={draft.otpEntered ?? ''}
                     onChange={e => set('otpEntered', e.target.value)}
                     placeholder="123456"/>
            </Field>
          )}
          {!needsOtp && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone="success">
                {draft.method === 'NAFATH'
                  ? (isAr ? 'سيتم التوقيع عبر نفاذ' : 'Will sign via Nafath')
                  : (isAr ? 'سيتم التوقيع عبر الجهاز' : 'Will sign via hardware token')}
              </Badge>
            </div>
          )}
          <Field label={isAr ? 'ملاحظات' : 'Notes'} full>
            <textarea className="input" rows={2} value={draft.notes ?? ''}
                      onChange={e => set('notes', e.target.value)}/>
          </Field>
          <Toggle checked={draft.ack1}
                  onChange={v => set('ack1', v)}
                  label={isAr
                    ? 'أقرّ بأنني قرأت السجل بالكامل وأدركت آثاره'
                    : 'I have read the record in full and understand its implications'}/>
          <Toggle checked={draft.ack2}
                  onChange={v => set('ack2', v)}
                  label={isAr
                    ? 'أقرّ بأن توقيعي مكافئ قانونياً للتوقيع الخطي'
                    : 'I acknowledge this signature carries the same legal weight as a wet-ink signature'}/>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'الموقّع' : 'Signer'} v={`${draft.signerName} (${draft.signerRole})`} />
            <Row k={isAr ? 'الطريقة' : 'Method'} v={draft.method} />
            <Row k={isAr ? 'النية' : 'Intent'} v={draft.intent} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'التوقيع الرقمي' : 'Digital Signature'}
        sub={isAr
          ? 'معالج من خطوتين — أي سجل قابل للاعتماد (سياسة، خطة، تقرير، إقرار)'
          : '2-step wizard for any approvable record — policy, plan, report, attestation'}
        eyebrow={isAr ? 'الحوكمة' : 'Governance'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/signatures')}
        submitLabel={isAr ? 'توقيع السجل' : 'Sign record'}/>
    </Shell>
  );
}

function Field({ label, required, full, children }: {
  label: string; required?: boolean; full?: boolean; children: React.ReactNode;
}) {
  return (
    <label className={'wf-field' + (full ? ' wf-field-full' : '')}>
      <span className="wf-label">
        {label}{required && <span className="wf-required"> *</span>}
      </span>
      {children}
    </label>
  );
}

function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <label className="wf-toggle">
      <input type="checkbox" checked={checked} onChange={e => onChange(e.target.checked)}/>
      <span>{label}</span>
    </label>
  );
}

function Row({ k, v }: { k: string; v: string }) {
  return (
    <div className="wf-review-row">
      <span className="wf-review-k">{k}</span>
      <span className="wf-review-v">{v}</span>
    </div>
  );
}
