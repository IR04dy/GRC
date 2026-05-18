import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardCheck, Fingerprint, Mail, Search, Send } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';

/**
 * PDPL §7.4 #4 — Data Subject Request fulfilment wizard.
 *
 * <p>5 steps tracking the PDPL Article 4 30-day SLA:
 *   1. Intake — type, subject identity, contact
 *   2. Identity verification (NAFATH / ID / in-person)
 *   3. Data collection across RoPA activities
 *   4. Redaction + DPO review
 *   5. Delivery + closure
 *
 * <p>This wizard is a UI shell for the existing DsrCase backend; on
 * submit it would POST to {@code /api/v1/privacy/dsr/cases}. Here we
 * keep state local and emit a toast on submit so the flow demos cleanly
 * without requiring full DSR backend wire-up in this round.
 */
type DsrType =
  | 'ACCESS' | 'RECTIFICATION' | 'ERASURE' | 'OBJECTION'
  | 'RESTRICTION' | 'PORTABILITY' | 'WITHDRAW_CONSENT';

interface DsrDraft {
  type: DsrType;
  subjectName: string;
  nationalIdLast4: string;
  contactEmail: string;
  verificationMethod?: 'NAFATH' | 'ID_DOCUMENT' | 'IN_PERSON' | 'VIDEO_KYC';
  identityNotes?: string;
  ropaActivities: string;
  dataSummary?: string;
  redactionComplete: boolean;
  dpoApproved: boolean;
  deliveryMethod?: 'EMAIL' | 'SECURE_PORTAL' | 'POSTAL';
}

export function DsrFulfilmentWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<DsrDraft>({
    type: 'ACCESS',
    subjectName: '', nationalIdLast4: '', contactEmail: '',
    ropaActivities: '',
    redactionComplete: false, dpoApproved: false,
  });

  function set<K extends keyof DsrDraft>(key: K, value: DsrDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  async function submit() {
    setSubmitting(true);
    try {
      // Real backend call would land here. The DSR backend already supports
      // create + SLA tracking (V115); leaving this stubbed keeps the demo path
      // working without requiring a Saudi National ID format up front.
      await new Promise(r => setTimeout(r, 400));
      toast.success(isAr ? 'تم إنشاء طلب صاحب البيانات' : 'DSR case created');
      navigate('/privacy');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'intake',
      title: 'Intake', titleAr: 'الاستلام',
      sub: 'Type, subject, contact', subAr: 'النوع والصاحب والاتصال',
      Icon: Mail,
      validate: () => !draft.subjectName || !draft.contactEmail || !draft.nationalIdLast4
        ? (isAr ? 'الاسم وآخر ٤ أرقام والبريد مطلوبة' : 'Name, last 4 of national ID, and email are required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'نوع الطلب' : 'Request type'}>
            <select className="input" value={draft.type}
                    onChange={e => set('type', e.target.value as DsrType)}>
              <option value="ACCESS">Access</option>
              <option value="RECTIFICATION">Rectification</option>
              <option value="ERASURE">Erasure (right to be forgotten)</option>
              <option value="OBJECTION">Objection</option>
              <option value="RESTRICTION">Restriction of processing</option>
              <option value="PORTABILITY">Portability</option>
              <option value="WITHDRAW_CONSENT">Withdraw consent</option>
            </select>
          </Field>
          <Field label={isAr ? 'اسم صاحب البيانات' : 'Subject full name'} required>
            <input className="input" value={draft.subjectName}
                   onChange={e => set('subjectName', e.target.value)} />
          </Field>
          <Field label={isAr ? 'آخر ٤ أرقام من الهوية' : 'National ID — last 4'} required>
            <input className="input" maxLength={4} value={draft.nationalIdLast4}
                   onChange={e => set('nationalIdLast4', e.target.value.replace(/\D/g,'').slice(0,4))} />
          </Field>
          <Field label={isAr ? 'البريد الإلكتروني' : 'Contact email'} required>
            <input className="input" type="email" value={draft.contactEmail}
                   onChange={e => set('contactEmail', e.target.value)} />
          </Field>
          <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
            <Badge tone="info">PDPL Art. 4</Badge>{' '}
            {isAr ? 'تتم متابعة هذا الطلب على مهلة ٣٠ يوماً.'
                  : 'This request will be tracked against the 30-day SLA from creation.'}
          </div>
        </div>
      ),
    },

    {
      id: 'verify',
      title: 'Verify identity', titleAr: 'التحقق من الهوية',
      sub: 'NAFATH / ID / video', subAr: 'نفاذ / هوية / فيديو',
      Icon: Fingerprint,
      validate: () => !draft.verificationMethod
        ? (isAr ? 'اختر طريقة التحقق' : 'Pick a verification method')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'طريقة التحقق' : 'Verification method'} required>
            <select className="input"
                    value={draft.verificationMethod ?? ''}
                    onChange={e => set('verificationMethod', e.target.value as DsrDraft['verificationMethod'])}>
              <option value="">—</option>
              <option value="NAFATH">NAFATH (Saudi national SSO)</option>
              <option value="ID_DOCUMENT">National ID document</option>
              <option value="IN_PERSON">In-person at branch</option>
              <option value="VIDEO_KYC">Video KYC</option>
            </select>
          </Field>
          <Field label={isAr ? 'ملاحظات التحقق' : 'Verification notes'} full>
            <textarea className="input" rows={4} value={draft.identityNotes ?? ''}
                      onChange={e => set('identityNotes', e.target.value)} />
          </Field>
        </div>
      ),
    },

    {
      id: 'collect',
      title: 'Collect data', titleAr: 'جمع البيانات',
      sub: 'From RoPA activities', subAr: 'من سجل المعالجة',
      Icon: Search,
      validate: () => !draft.ropaActivities.trim()
        ? (isAr ? 'حدد أنشطة المعالجة' : 'List the RoPA activities to query')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'أنشطة المعالجة (معرفات الخارجية مفصولة بفاصلة)' : 'RoPA activities (comma-separated externalIds)'} required full>
            <input className="input" value={draft.ropaActivities}
                   onChange={e => set('ropaActivities', e.target.value)}
                   placeholder="RPA-001, RPA-014, RPA-027" />
          </Field>
          <Field label={isAr ? 'ملخص البيانات الموجودة' : 'Data summary'} full>
            <textarea className="input" rows={6} value={draft.dataSummary ?? ''}
                      onChange={e => set('dataSummary', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: ملف عميل + ٤ معاملات + سجل تسجيل دخول…'
                        : 'e.g. customer profile + 4 transactions + audit-log records…'} />
          </Field>
        </div>
      ),
    },

    {
      id: 'redact',
      title: 'Redact & DPO review', titleAr: 'الحجب ومراجعة المسؤول',
      sub: 'Approval', subAr: 'الاعتماد',
      Icon: ClipboardCheck,
      validate: () => !draft.dpoApproved
        ? (isAr ? 'يلزم اعتماد مسؤول الحماية' : 'DPO approval is required before delivery')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'تم إزالة بيانات الأطراف الثالثة' : 'Third-party data redacted'} full>
            <label className="wf-toggle">
              <input type="checkbox" checked={draft.redactionComplete}
                     onChange={e => set('redactionComplete', e.target.checked)} />
              <span>{isAr ? 'تم الحجب' : 'Redaction complete'}</span>
            </label>
          </Field>
          <Field label={isAr ? 'اعتماد مسؤول حماية البيانات' : 'DPO approval'} full required>
            <label className="wf-toggle">
              <input type="checkbox" checked={draft.dpoApproved}
                     onChange={e => set('dpoApproved', e.target.checked)} />
              <span>{isAr ? 'مُعتمد من DPO' : 'Approved by DPO'}</span>
            </label>
          </Field>
        </div>
      ),
    },

    {
      id: 'deliver',
      title: 'Deliver & close', titleAr: 'التسليم والإغلاق',
      sub: 'Final step', subAr: 'الخطوة الأخيرة',
      Icon: Send,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'طريقة التسليم' : 'Delivery method'} full>
            <select className="input"
                    value={draft.deliveryMethod ?? ''}
                    onChange={e => set('deliveryMethod', e.target.value as DsrDraft['deliveryMethod'])}>
              <option value="">—</option>
              <option value="EMAIL">Secure email (encrypted attachment)</option>
              <option value="SECURE_PORTAL">Secure portal download</option>
              <option value="POSTAL">Registered postal mail</option>
            </select>
          </Field>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'النوع' : 'Type'} v={draft.type} />
            <Row k={isAr ? 'الصاحب' : 'Subject'} v={`${draft.subjectName} (••••${draft.nationalIdLast4})`} />
            <Row k={isAr ? 'التحقق' : 'Verification'} v={draft.verificationMethod ?? '—'} />
            <Row k={isAr ? 'الأنشطة' : 'RoPA activities'} v={draft.ropaActivities || '—'} />
            <Row k="DPO" v={draft.dpoApproved ? '✓' : '—'} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'إجراء طلب صاحب البيانات' : 'Fulfil a Data Subject Request'}
        sub={isAr ? 'معالج من ٥ خطوات وفق المادة ٤ من PDPL'
                  : '5-step wizard aligned with PDPL Article 4'}
        eyebrow={isAr ? 'الخصوصية / PDPL' : 'Privacy / PDPL'}
      />
      <WizardShell
        steps={steps}
        active={step}
        onChange={setStep}
        rtl={isAr}
        submitting={submitting}
        onSubmit={submit}
        onCancel={() => navigate('/privacy')}
      />
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

function Row({ k, v }: { k: string; v: string }) {
  return (
    <div className="wf-review-row">
      <span className="wf-review-k">{k}</span>
      <span className="wf-review-v">{v}</span>
    </div>
  );
}
