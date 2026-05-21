import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Database, Globe, ShieldCheck, Stamp } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge, type BadgeTone } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';
import { submitWizard } from '../../../lib/submitWizard';

/**
 * PDPL §7.4 #6 — Cross-Border Personal Data Transfer wizard.
 *
 * <p>4 steps tracking PDPL Article 29 + Saudi Data Office adequacy regime:
 *   1. Transfer profile — purpose, data categories, volume, recipient
 *   2. Mechanism — adequacy / SCC / binding rules / consent / vital
 *   3. Saudi Data Office reference + safeguards
 *   4. DPO sign-off + governance
 */
type Mechanism =
  | 'ADEQUACY'
  | 'SCC'
  | 'BCR'
  | 'CONSENT'
  | 'VITAL_INTEREST'
  | 'PUBLIC_INTEREST'
  | 'CONTRACT_NECESSITY';

const MECHANISM_LABELS: Record<Mechanism, { en: string; ar: string; needsSdo: boolean }> = {
  ADEQUACY:           { en: 'Adequacy decision',             ar: 'قرار كفاية',             needsSdo: true },
  SCC:                { en: 'Standard contractual clauses',  ar: 'بنود تعاقدية قياسية',     needsSdo: true },
  BCR:                { en: 'Binding corporate rules',       ar: 'قواعد ملزمة للشركات',     needsSdo: true },
  CONSENT:            { en: 'Explicit consent',              ar: 'موافقة صريحة',           needsSdo: false },
  VITAL_INTEREST:     { en: 'Vital interest of subject',     ar: 'مصلحة حيوية للمعني',     needsSdo: false },
  PUBLIC_INTEREST:    { en: 'Public interest',               ar: 'مصلحة عامة',            needsSdo: true },
  CONTRACT_NECESSITY: { en: 'Contract necessity',            ar: 'ضرورة تعاقدية',          needsSdo: false },
};

type Sensitivity = 'STANDARD' | 'SENSITIVE' | 'HEALTH' | 'CHILDREN' | 'BIOMETRIC';

interface TransferDraft {
  recipientName: string;
  recipientCountry: string;
  purposeEn: string;
  dataCategories: string;
  sensitivity: Sensitivity;
  volumeBand: 'LT_100' | 'LT_10K' | 'LT_1M' | 'GTE_1M';
  isProcessor: boolean;
  mechanism: Mechanism;
  sdoReference?: string;
  safeguards?: string;
  reviewPeriodMonths: number;
  dpoApproved: boolean;
  ciscoSpEsignoff: boolean;
  notes?: string;
}

export function CrossBorderTransferWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<TransferDraft>({
    recipientName: '',
    recipientCountry: '',
    purposeEn: '',
    dataCategories: '',
    sensitivity: 'STANDARD',
    volumeBand: 'LT_10K',
    isProcessor: true,
    mechanism: 'ADEQUACY',
    reviewPeriodMonths: 12,
    dpoApproved: false,
    ciscoSpEsignoff: false,
  });

  function set<K extends keyof TransferDraft>(key: K, value: TransferDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  const needsSdo = MECHANISM_LABELS[draft.mechanism].needsSdo;
  const riskTone: BadgeTone =
    draft.sensitivity === 'CHILDREN' || draft.sensitivity === 'BIOMETRIC' ? 'danger' :
    draft.sensitivity === 'SENSITIVE' || draft.sensitivity === 'HEALTH' ? 'warning' :
    'info';

  async function submit() {
    setSubmitting(true);
    try {
      const created = await submitWizard({
        workflowId: 'pdpl-cross-border',
        workflowModule: 'PDPL',
        titleEn: `Transfer: ${draft.recipientName} (${draft.recipientCountry})`.slice(0, 280),
        regulatoryAnchor: 'PDPL Art. 29',
        summaryEn: `${draft.mechanism} · ${draft.sensitivity} · ${draft.volumeBand}`
                 + (draft.sdoReference ? ` · SDO=${draft.sdoReference}` : '')
                 + ` · review every ${draft.reviewPeriodMonths}m`,
        status: draft.dpoApproved ? 'APPROVED' : 'SUBMITTED',
        draft,
      });
      toast.success(isAr
        ? `تم اعتماد النقل عبر الحدود (#${created.id})`
        : `Cross-border transfer #${created.id} authorised`);
      navigate('/privacy');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'profile',
      title: '1. Transfer profile', titleAr: '١. ملف النقل',
      Icon: Globe,
      validate: () =>
        !draft.recipientName.trim() || !draft.recipientCountry.trim() || !draft.purposeEn.trim()
          ? (isAr ? 'حقول المستلم والدولة والغرض مطلوبة'
                  : 'Recipient, country and purpose are required')
          : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'اسم المستلم' : 'Recipient name'} required>
            <input className="input" value={draft.recipientName}
                   onChange={e => set('recipientName', e.target.value)}
                   placeholder="e.g. Acme Inc."/>
          </Field>
          <Field label={isAr ? 'الدولة' : 'Recipient country'} required>
            <input className="input" value={draft.recipientCountry}
                   onChange={e => set('recipientCountry', e.target.value)}
                   placeholder={isAr ? 'مثال: الإمارات' : 'e.g. United Arab Emirates'}/>
          </Field>
          <Field label={isAr ? 'الغرض' : 'Purpose of transfer'} full required>
            <textarea className="input" rows={2} value={draft.purposeEn}
                      onChange={e => set('purposeEn', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: استضافة بيانات العملاء على بنية تحتية إقليمية'
                        : 'e.g. host customer data on regional infrastructure'}/>
          </Field>
          <Field label={isAr ? 'فئات البيانات' : 'Data categories'} full>
            <textarea className="input" rows={2} value={draft.dataCategories}
                      onChange={e => set('dataCategories', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: الاسم، البريد، رقم الهاتف، سجل المعاملات'
                        : 'e.g. name, email, phone, transaction history'}/>
          </Field>
          <Field label={isAr ? 'حساسية البيانات' : 'Data sensitivity'}>
            <select className="input" value={draft.sensitivity}
                    onChange={e => set('sensitivity', e.target.value as Sensitivity)}>
              <option value="STANDARD">Standard PII</option>
              <option value="SENSITIVE">Sensitive PII</option>
              <option value="HEALTH">Health</option>
              <option value="CHILDREN">Children</option>
              <option value="BIOMETRIC">Biometric</option>
            </select>
          </Field>
          <Field label={isAr ? 'حجم النقل' : 'Transfer volume'}>
            <select className="input" value={draft.volumeBand}
                    onChange={e => set('volumeBand', e.target.value as TransferDraft['volumeBand'])}>
              <option value="LT_100">&lt; 100 subjects</option>
              <option value="LT_10K">&lt; 10,000 subjects</option>
              <option value="LT_1M">&lt; 1,000,000 subjects</option>
              <option value="GTE_1M">≥ 1,000,000 subjects</option>
            </select>
          </Field>
          <Toggle checked={draft.isProcessor}
                  onChange={v => set('isProcessor', v)}
                  label={isAr ? 'المستلم معالج بيانات' : 'Recipient is a data processor'}/>
          {(draft.sensitivity === 'CHILDREN' || draft.sensitivity === 'BIOMETRIC' || draft.volumeBand === 'GTE_1M') && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone={riskTone}>{isAr ? 'تحذير' : 'High-risk transfer'}</Badge>{' '}
              {isAr
                ? 'يلزم تقييم أثر خصوصية (DPIA) قبل اعتماد آلية النقل.'
                : 'A DPIA is required before approving the transfer mechanism.'}
            </div>
          )}
        </div>
      ),
    },

    {
      id: 'mechanism',
      title: '2. Transfer mechanism', titleAr: '٢. آلية النقل',
      Icon: ShieldCheck,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الآلية' : 'Mechanism'} full>
            <select className="input" value={draft.mechanism}
                    onChange={e => set('mechanism', e.target.value as Mechanism)}>
              {(Object.keys(MECHANISM_LABELS) as Mechanism[]).map(m => (
                <option key={m} value={m}>
                  {isAr ? MECHANISM_LABELS[m].ar : MECHANISM_LABELS[m].en}
                </option>
              ))}
            </select>
          </Field>
          <Field label={isAr ? 'الضمانات' : 'Safeguards'} full>
            <textarea className="input" rows={3} value={draft.safeguards ?? ''}
                      onChange={e => set('safeguards', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: تشفير AES-256 أثناء النقل والتخزين، تنقيح، عقود معالج، تدقيق سنوي'
                        : 'e.g. AES-256 encryption at rest and in transit, pseudonymisation, processor contracts, annual audit'}/>
          </Field>
          {draft.mechanism === 'CONSENT' && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone="warning">PDPL Art. 5</Badge>{' '}
              {isAr
                ? 'الموافقة آلية ضعيفة للنقل الجماعي — يلزم إثبات الموافقة لكل صاحب بيانات.'
                : 'Consent is a weak basis for bulk transfers — per-subject proof of consent required.'}
            </div>
          )}
        </div>
      ),
    },

    {
      id: 'sdo',
      title: '3. SDO + safeguards', titleAr: '٣. مكتب البيانات والضمانات',
      Icon: Database,
      validate: () => needsSdo && !draft.sdoReference?.trim()
        ? (isAr ? 'يلزم مرجع مكتب البيانات السعودي لهذه الآلية'
                : 'Saudi Data Office reference required for this mechanism')
        : null,
      content: () => (
        <div className="wf-form-grid">
          {needsSdo ? (
            <Field label={isAr ? 'مرجع مكتب البيانات السعودي' : 'Saudi Data Office reference'} required>
              <input className="input" value={draft.sdoReference ?? ''}
                     onChange={e => set('sdoReference', e.target.value)}
                     placeholder="SDO-2026-XXXX"/>
            </Field>
          ) : (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone="info">{isAr ? 'لا يلزم مرجع SDO' : 'No SDO reference needed'}</Badge>{' '}
              {isAr
                ? 'الآلية المختارة لا تتطلب تسجيلاً لدى مكتب البيانات.'
                : 'The selected mechanism does not require Saudi Data Office filing.'}
            </div>
          )}
          <Field label={isAr ? 'فترة المراجعة (أشهر)' : 'Review period (months)'}>
            <input className="input" type="number" min={1} max={36}
                   value={draft.reviewPeriodMonths}
                   onChange={e => set('reviewPeriodMonths', Number(e.target.value || 12))}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'signoff',
      title: '4. DPO sign-off', titleAr: '٤. اعتماد DPO',
      Icon: Stamp,
      validate: () => !draft.dpoApproved
        ? (isAr ? 'يلزم اعتماد DPO قبل التفعيل' : 'DPO approval required before go-live')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'ملاحظات الاعتماد' : 'Approval notes'} full>
            <textarea className="input" rows={3} value={draft.notes ?? ''}
                      onChange={e => set('notes', e.target.value)}/>
          </Field>
          <Toggle checked={draft.dpoApproved}
                  onChange={v => set('dpoApproved', v)}
                  label={isAr ? 'اعتماد مسؤول حماية البيانات (DPO)' : 'DPO approval'}/>
          <Toggle checked={draft.ciscoSpEsignoff}
                  onChange={v => set('ciscoSpEsignoff', v)}
                  label={isAr ? 'اعتماد CISO (للنقل عالي الحساسية)' : 'CISO sign-off (high-sensitivity transfers)'}/>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'المستلم' : 'Recipient'} v={`${draft.recipientName} (${draft.recipientCountry})`} />
            <Row k={isAr ? 'الآلية' : 'Mechanism'}
                 v={isAr ? MECHANISM_LABELS[draft.mechanism].ar : MECHANISM_LABELS[draft.mechanism].en} />
            <Row k="SDO" v={draft.sdoReference ?? (needsSdo ? '—' : (isAr ? 'غير لازم' : 'Not required'))} />
            <Row k={isAr ? 'الحساسية' : 'Sensitivity'} v={draft.sensitivity} />
            <Row k={isAr ? 'المراجعة' : 'Review'}
                 v={`${draft.reviewPeriodMonths} ${isAr ? 'شهراً' : 'months'}`} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'نقل البيانات عبر الحدود' : 'Cross-Border Transfer'}
        sub={isAr
          ? 'معالج من ٤ خطوات — الملف → الآلية → مرجع SDO → اعتماد DPO'
          : '4-step wizard — profile → mechanism → SDO reference → DPO sign-off'}
        eyebrow={isAr ? 'الخصوصية / PDPL' : 'PDPL / Privacy'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/privacy')}
        submitLabel={isAr ? 'اعتماد النقل' : 'Authorise transfer'}/>
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
