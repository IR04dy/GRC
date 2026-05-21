import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Building2, ClipboardCheck, FileCheck2, FileText,
  ShieldAlert, Truck,
} from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';

/**
 * SCM §7.2 — Supplier Onboarding wizard.
 *
 * <p>6 steps:
 *   1. Identification (legal name, CR, vendor type)
 *   2. Criticality — services supported + tier
 *   3. Contract — start/end, value, renewal
 *   4. Due-diligence — financial, legal, sanctions, references
 *   5. BCM + cyber + privacy evidence review
 *   6. Approval — procurement + risk + DPO if processor
 */
type Tier = 'TIER_1' | 'TIER_2' | 'TIER_3' | 'TIER_4';

interface SupplierDraft {
  legalName: string; legalNameAr?: string;
  commercialRegistrationNo?: string;
  vendorType?: 'TECHNOLOGY' | 'PROFESSIONAL_SERVICES' | 'STAFFING' | 'OUTSOURCING' | 'UTILITY' | 'OTHER';

  servicesSupported: string;
  tier?: Tier;
  isProcessor: boolean;
  isCriticalSupplier: boolean;

  contractStart?: string;
  contractEnd?: string;
  contractValue?: string;
  renewalType?: 'AUTO' | 'MANUAL' | 'NONE';

  financialCheckPassed: boolean;
  sanctionsCheckPassed: boolean;
  referencesPassed: boolean;
  ddNotes?: string;

  hasBcp: boolean;
  hasCyberAttestation: boolean;
  hasPrivacyAddendum: boolean;
  evidenceNotes?: string;

  procurementApproved: boolean;
  riskApproved: boolean;
  dpoApproved: boolean;
}

export function SupplierOnboardingWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<SupplierDraft>({
    legalName: '',
    servicesSupported: '',
    isProcessor: false, isCriticalSupplier: false,
    financialCheckPassed: false, sanctionsCheckPassed: false, referencesPassed: false,
    hasBcp: false, hasCyberAttestation: false, hasPrivacyAddendum: false,
    procurementApproved: false, riskApproved: false, dpoApproved: false,
  });

  function set<K extends keyof SupplierDraft>(key: K, value: SupplierDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  async function submit() {
    setSubmitting(true);
    try {
      await new Promise(r => setTimeout(r, 400));
      toast.success(isAr ? 'تم إدراج المورد' : 'Supplier onboarded');
      navigate('/scm');
    } catch (e) { toast.danger((e as Error).message); }
    finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'identify',
      title: 'Identification', titleAr: 'التعريف',
      Icon: Building2,
      validate: () => !draft.legalName.trim()
        ? (isAr ? 'الاسم القانوني مطلوب' : 'Legal name is required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الاسم القانوني (إنجليزي)' : 'Legal name (English)'} required>
            <input className="input" value={draft.legalName}
                   onChange={e => set('legalName', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'الاسم القانوني (عربي)' : 'Legal name (Arabic)'}>
            <input className="input" value={draft.legalNameAr ?? ''}
                   onChange={e => set('legalNameAr', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'رقم السجل التجاري' : 'Commercial registration #'}>
            <input className="input" value={draft.commercialRegistrationNo ?? ''}
                   onChange={e => set('commercialRegistrationNo', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'نوع المورد' : 'Vendor type'}>
            <select className="input" value={draft.vendorType ?? ''}
                    onChange={e => set('vendorType', e.target.value as SupplierDraft['vendorType'])}>
              <option value="">—</option>
              <option value="TECHNOLOGY">Technology / SaaS</option>
              <option value="PROFESSIONAL_SERVICES">Professional services</option>
              <option value="STAFFING">Staffing / consulting</option>
              <option value="OUTSOURCING">Business process outsourcing</option>
              <option value="UTILITY">Utility / infrastructure</option>
              <option value="OTHER">Other</option>
            </select>
          </Field>
        </div>
      ),
    },

    {
      id: 'criticality',
      title: 'Criticality', titleAr: 'الأهمية',
      Icon: ShieldAlert,
      validate: () => !draft.tier
        ? (isAr ? 'اختر طبقة الأهمية' : 'Pick a criticality tier')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الخدمات المدعومة' : 'Services supported'} full>
            <textarea className="input" rows={3} value={draft.servicesSupported}
                      onChange={e => set('servicesSupported', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: استضافة قاعدة بيانات المعاملات، شبكة النسخ الاحتياطي…'
                        : 'e.g. transactional DB hosting, backup network…'}/>
          </Field>
          <Field label={isAr ? 'الطبقة' : 'Tier'} required>
            <select className="input" value={draft.tier ?? ''}
                    onChange={e => set('tier', e.target.value as Tier)}>
              <option value="">—</option>
              <option value="TIER_1">Tier 1 — supports critical national service</option>
              <option value="TIER_2">Tier 2 — supports critical business service</option>
              <option value="TIER_3">Tier 3 — important but not critical</option>
              <option value="TIER_4">Tier 4 — commodity</option>
            </select>
          </Field>
          <Field label={isAr ? 'يعالج بيانات شخصية؟' : 'Processes personal data?'}>
            <Toggle checked={draft.isProcessor} onChange={v => set('isProcessor', v)}
                    label={isAr ? 'نعم — مطلوب اعتماد DPO' : 'Yes — DPO approval required'}/>
          </Field>
          <Field label={isAr ? 'مورد حرج؟' : 'Critical supplier?'}>
            <Toggle checked={draft.isCriticalSupplier}
                    onChange={v => set('isCriticalSupplier', v)}
                    label={isAr ? 'نعم — يتطلب BCP من المورد' : 'Yes — requires supplier BCP'} />
          </Field>
        </div>
      ),
    },

    {
      id: 'contract',
      title: 'Contract', titleAr: 'العقد',
      Icon: FileText,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'تاريخ البدء' : 'Start date'}>
            <input className="input" type="date" value={draft.contractStart ?? ''}
                   onChange={e => set('contractStart', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'تاريخ الانتهاء' : 'End date'}>
            <input className="input" type="date" value={draft.contractEnd ?? ''}
                   onChange={e => set('contractEnd', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'القيمة (SAR)' : 'Value (SAR)'}>
            <input className="input" type="number" min={0}
                   value={draft.contractValue ?? ''}
                   onChange={e => set('contractValue', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'نوع التجديد' : 'Renewal'}>
            <select className="input" value={draft.renewalType ?? ''}
                    onChange={e => set('renewalType', e.target.value as SupplierDraft['renewalType'])}>
              <option value="">—</option>
              <option value="AUTO">Automatic</option>
              <option value="MANUAL">Manual</option>
              <option value="NONE">Fixed-term</option>
            </select>
          </Field>
        </div>
      ),
    },

    {
      id: 'dd',
      title: 'Due diligence', titleAr: 'العناية الواجبة',
      Icon: ClipboardCheck,
      validate: () => !draft.sanctionsCheckPassed
        ? (isAr ? 'يلزم اجتياز فحص العقوبات' : 'Sanctions screening must pass')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الفحص المالي' : 'Financial check'}>
            <Toggle checked={draft.financialCheckPassed}
                    onChange={v => set('financialCheckPassed', v)}
                    label={isAr ? 'مُنجَز بنجاح' : 'Passed'}/>
          </Field>
          <Field label={isAr ? 'فحص العقوبات (OFAC / UN / KSA)' : 'Sanctions screening (OFAC / UN / KSA)'} required>
            <Toggle checked={draft.sanctionsCheckPassed}
                    onChange={v => set('sanctionsCheckPassed', v)}
                    label={isAr ? 'لا يوجد تطابق' : 'No match'}/>
          </Field>
          <Field label={isAr ? 'المراجع' : 'References'}>
            <Toggle checked={draft.referencesPassed}
                    onChange={v => set('referencesPassed', v)}
                    label={isAr ? 'تم التحقق' : 'Checked'}/>
          </Field>
          <Field label={isAr ? 'ملاحظات' : 'Notes'} full>
            <textarea className="input" rows={4} value={draft.ddNotes ?? ''}
                      onChange={e => set('ddNotes', e.target.value)}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'evidence',
      title: 'BCM + Cyber + Privacy evidence', titleAr: 'الأدلة',
      Icon: FileCheck2,
      validate: () => draft.isCriticalSupplier && !draft.hasBcp
        ? (isAr ? 'المورد الحرج يحتاج BCP' : 'Critical supplier requires a BCP')
        : draft.isProcessor && !draft.hasPrivacyAddendum
        ? (isAr ? 'معالج البيانات يحتاج ملحق خصوصية' : 'Processor requires a privacy addendum')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'BCP من المورد' : 'Supplier BCP on file'}>
            <Toggle checked={draft.hasBcp} onChange={v => set('hasBcp', v)}
                    label={isAr ? 'موجود' : 'On file'}/>
          </Field>
          <Field label={isAr ? 'إقرار سيبراني / ISO 27001' : 'Cyber attestation / ISO 27001'}>
            <Toggle checked={draft.hasCyberAttestation}
                    onChange={v => set('hasCyberAttestation', v)}
                    label={isAr ? 'موجود' : 'On file'}/>
          </Field>
          <Field label={isAr ? 'ملحق خصوصية PDPL' : 'PDPL processor addendum'}>
            <Toggle checked={draft.hasPrivacyAddendum}
                    onChange={v => set('hasPrivacyAddendum', v)}
                    label={isAr ? 'موقَّع' : 'Signed'}/>
          </Field>
          <Field label={isAr ? 'ملاحظات الأدلة' : 'Evidence notes'} full>
            <textarea className="input" rows={3} value={draft.evidenceNotes ?? ''}
                      onChange={e => set('evidenceNotes', e.target.value)}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'approve',
      title: 'Approvals', titleAr: 'الاعتمادات',
      Icon: Truck,
      validate: () => {
        if (!draft.procurementApproved) return isAr ? 'يلزم اعتماد المشتريات' : 'Procurement approval required';
        if (!draft.riskApproved)       return isAr ? 'يلزم اعتماد الفريق' : 'Risk team approval required';
        if (draft.isProcessor && !draft.dpoApproved) return isAr ? 'يلزم اعتماد DPO' : 'DPO approval required for processors';
        return null;
      },
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'اعتماد المشتريات' : 'Procurement approval'} required>
            <Toggle checked={draft.procurementApproved}
                    onChange={v => set('procurementApproved', v)}
                    label={isAr ? 'مُعتمد' : 'Approved'}/>
          </Field>
          <Field label={isAr ? 'اعتماد المخاطر' : 'Risk approval'} required>
            <Toggle checked={draft.riskApproved}
                    onChange={v => set('riskApproved', v)}
                    label={isAr ? 'مُعتمد' : 'Approved'}/>
          </Field>
          {draft.isProcessor && (
            <Field label={isAr ? 'اعتماد DPO' : 'DPO approval'} required>
              <Toggle checked={draft.dpoApproved}
                      onChange={v => set('dpoApproved', v)}
                      label={isAr ? 'مُعتمد' : 'Approved'}/>
            </Field>
          )}
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'الاسم' : 'Name'} v={draft.legalName} />
            <Row k={isAr ? 'الطبقة' : 'Tier'} v={draft.tier ?? '—'} />
            <Row k={isAr ? 'معالج' : 'Processor'} v={draft.isProcessor ? 'Yes' : 'No'} />
            <Row k={isAr ? 'حرج' : 'Critical'} v={draft.isCriticalSupplier ? 'Yes' : 'No'} />
          </div>
          {draft.isProcessor && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone="warning">PDPL Art. 37</Badge>{' '}
              {isAr
                ? 'سيتم إنشاء تقييم معالج بعد الاعتماد.'
                : 'A processor assessment will be created on approval.'}
            </div>
          )}
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'إدراج مورد جديد' : 'Onboard a new supplier'}
        sub={isAr ? 'معالج من ٦ خطوات لإدراج المورد ومراجعة الأدلة'
                  : '6-step wizard from identification to approval'}
        eyebrow={isAr ? 'سلسلة الإمداد' : 'Supply Chain'}
      />
      <WizardShell
        steps={steps}
        active={step}
        onChange={setStep}
        rtl={isAr}
        submitting={submitting}
        onSubmit={submit}
        onCancel={() => navigate('/scm')}
      />
    </Shell>
  );
}

function Field({ label, required, full, children }: {
  label: string; required?: boolean; full?: boolean; children: React.ReactNode;
}) {
  return (
    <label className={'wf-field' + (full ? ' wf-field-full' : '')}>
      <span className="wf-label">{label}{required && <span className="wf-required"> *</span>}</span>
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
