import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCheck, ClipboardCheck, Server, ShieldAlert, ShieldCheck } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';

/**
 * SCM §7.2 #3 — Supplier Risk Assessment wizard.
 *
 * <p>5 steps:
 *   1. Supplier context
 *   2. Inherent risk (data access × criticality × concentration)
 *   3. Control review (BCP / cyber attestation / privacy addendum / certs)
 *   4. Residual scoring + tier-recommendation
 *   5. Approval — procurement + risk + DPO (if processor)
 */
type AccessLevel = 'NONE' | 'METADATA' | 'PII' | 'SPECIAL_CATEGORY' | 'FINANCIAL';
type Criticality = 'TIER_1' | 'TIER_2' | 'TIER_3' | 'TIER_4';

interface VendorAssessment {
  vendorName: string;
  dataAccess: AccessLevel;
  criticality: Criticality;
  concentrationFlag: boolean;
  isProcessor: boolean;
  fourthPartyExposure: boolean;
  hasBcp: boolean;
  hasCyberAttestation: boolean;
  hasPrivacyAddendum: boolean;
  hasIsoCert: boolean;
  inherentScore?: number;
  residualScore?: number;
  recommendedTier?: Criticality;
  procurementApproved: boolean;
  riskApproved: boolean;
  dpoApproved: boolean;
  notes?: string;
}

export function VendorRiskAssessmentWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<VendorAssessment>({
    vendorName: '',
    dataAccess: 'NONE',
    criticality: 'TIER_3',
    concentrationFlag: false,
    isProcessor: false,
    fourthPartyExposure: false,
    hasBcp: false,
    hasCyberAttestation: false,
    hasPrivacyAddendum: false,
    hasIsoCert: false,
    procurementApproved: false,
    riskApproved: false,
    dpoApproved: false,
  });

  function set<K extends keyof VendorAssessment>(key: K, value: VendorAssessment[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  // Quick inherent-risk calculator: 4 axes × weights = 0..25
  const inherentCalc = (() => {
    const accessWeight = { NONE: 0, METADATA: 2, PII: 4, FINANCIAL: 5, SPECIAL_CATEGORY: 5 }[draft.dataAccess];
    const tierWeight = { TIER_1: 5, TIER_2: 4, TIER_3: 2, TIER_4: 1 }[draft.criticality];
    const concentration = draft.concentrationFlag ? 3 : 0;
    const fourthParty = draft.fourthPartyExposure ? 3 : 0;
    return Math.min(25, accessWeight * 2 + tierWeight * 2 + concentration + fourthParty);
  })();

  const controlsCovered = [draft.hasBcp, draft.hasCyberAttestation, draft.hasPrivacyAddendum, draft.hasIsoCert]
    .filter(Boolean).length;
  const residualCalc = Math.max(0, Math.round(inherentCalc * (1 - controlsCovered * 0.15)));

  async function submit() {
    setSubmitting(true);
    try {
      await new Promise(r => setTimeout(r, 400));
      toast.success(isAr ? 'تم تسجيل تقييم المورد' : 'Vendor assessment recorded');
      navigate('/tprm');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'context',
      title: 'Context', titleAr: 'السياق',
      Icon: Server,
      validate: () => !draft.vendorName.trim()
        ? (isAr ? 'اسم المورد مطلوب' : 'Vendor name is required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'اسم المورد' : 'Vendor name'} required>
            <input className="input" value={draft.vendorName}
                   onChange={e => set('vendorName', e.target.value)} />
          </Field>
        </div>
      ),
    },

    {
      id: 'inherent',
      title: 'Inherent risk', titleAr: 'الخطر المتأصل',
      Icon: ShieldAlert,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'مستوى الوصول إلى البيانات' : 'Data access level'}>
            <select className="input" value={draft.dataAccess}
                    onChange={e => set('dataAccess', e.target.value as AccessLevel)}>
              <option value="NONE">None</option>
              <option value="METADATA">Metadata only</option>
              <option value="PII">PII (PDPL-regulated)</option>
              <option value="FINANCIAL">Financial data</option>
              <option value="SPECIAL_CATEGORY">Special category (health / biometric)</option>
            </select>
          </Field>
          <Field label={isAr ? 'طبقة الأهمية' : 'Criticality tier'}>
            <select className="input" value={draft.criticality}
                    onChange={e => set('criticality', e.target.value as Criticality)}>
              <option value="TIER_1">Tier 1 — supports critical national service</option>
              <option value="TIER_2">Tier 2 — supports critical business service</option>
              <option value="TIER_3">Tier 3 — important but not critical</option>
              <option value="TIER_4">Tier 4 — commodity</option>
            </select>
          </Field>
          <Field label={isAr ? 'مورد تركيز' : 'Concentration risk'}>
            <Toggle checked={draft.concentrationFlag}
                    onChange={v => set('concentrationFlag', v)}
                    label={isAr ? 'مورد متكرر عبر عدة خدمات حرجة' : 'Same vendor supports multiple critical services'}/>
          </Field>
          <Field label={isAr ? 'تعرض الطرف الرابع' : 'Fourth-party exposure'}>
            <Toggle checked={draft.fourthPartyExposure}
                    onChange={v => set('fourthPartyExposure', v)}
                    label={isAr ? 'يستخدم مقاول من الباطن' : 'Vendor uses a subcontractor for critical functions'}/>
          </Field>
          <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
            <Badge tone={inherentCalc >= 18 ? 'danger' : inherentCalc >= 12 ? 'warning' : inherentCalc >= 6 ? 'info' : 'success'}>
              {isAr ? 'النتيجة المتأصلة' : 'Inherent score'}: {inherentCalc}/25
            </Badge>
          </div>
        </div>
      ),
    },

    {
      id: 'controls',
      title: 'Controls', titleAr: 'الضوابط',
      Icon: ShieldCheck,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'BCP / DRP من المورد' : 'Supplier BCP / DRP on file'}>
            <Toggle checked={draft.hasBcp} onChange={v => set('hasBcp', v)}
                    label={isAr ? 'موجود ومُجدَّد' : 'On file + current'} />
          </Field>
          <Field label={isAr ? 'إقرار سيبراني / ISO 27001' : 'Cyber attestation / ISO 27001'}>
            <Toggle checked={draft.hasCyberAttestation}
                    onChange={v => set('hasCyberAttestation', v)}
                    label={isAr ? 'موجود ومُجدَّد' : 'On file + current'} />
          </Field>
          <Field label={isAr ? 'مورد بيانات شخصية؟' : 'Processes personal data?'}>
            <Toggle checked={draft.isProcessor}
                    onChange={v => set('isProcessor', v)}
                    label={isAr ? 'نعم — يلزم اعتماد DPO' : 'Yes — DPO approval required at step 5'} />
          </Field>
          <Field label={isAr ? 'ملحق خصوصية PDPL' : 'PDPL processor addendum'}>
            <Toggle checked={draft.hasPrivacyAddendum}
                    onChange={v => set('hasPrivacyAddendum', v)}
                    label={isAr ? 'موقَّع' : 'Signed'} />
          </Field>
          <Field label={isAr ? 'شهادة أيزو' : 'ISO certification'}>
            <Toggle checked={draft.hasIsoCert}
                    onChange={v => set('hasIsoCert', v)}
                    label={isAr ? 'ISO 27001 / 22301 سارية' : 'ISO 27001 / 22301 current'} />
          </Field>
          <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
            <Badge tone="info">
              {isAr ? 'الضوابط' : 'Controls'}: {controlsCovered}/4
            </Badge>
          </div>
        </div>
      ),
    },

    {
      id: 'residual',
      title: 'Residual & tier', titleAr: 'المتبقي والطبقة',
      Icon: ClipboardCheck,
      content: () => (
        <div className="wf-form-grid">
          <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
            <Badge tone={residualCalc >= 18 ? 'danger' : residualCalc >= 12 ? 'warning' : residualCalc >= 6 ? 'info' : 'success'}>
              {isAr ? 'النتيجة المتبقية' : 'Residual score'}: {residualCalc}/25
            </Badge>
            {' '}({inherentCalc} {isAr ? 'متأصل −' : 'inherent −'} {(controlsCovered * 15)}{isAr ? '٪ ضوابط' : '% controls'})
          </div>
          <Field label={isAr ? 'ملاحظات الفريق' : 'Reviewer notes'} full>
            <textarea className="input" rows={5} value={draft.notes ?? ''}
                      onChange={e => set('notes', e.target.value)} />
          </Field>
        </div>
      ),
    },

    {
      id: 'approve',
      title: 'Approvals', titleAr: 'الاعتمادات',
      Icon: CheckCheck,
      validate: () => {
        if (!draft.procurementApproved) return isAr ? 'اعتماد المشتريات مطلوب' : 'Procurement approval required';
        if (!draft.riskApproved) return isAr ? 'اعتماد الفريق مطلوب' : 'Risk approval required';
        if (draft.isProcessor && !draft.dpoApproved)
          return isAr ? 'اعتماد DPO مطلوب لمعالجي البيانات' : 'DPO approval required for processors';
        return null;
      },
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'المشتريات' : 'Procurement'} required>
            <Toggle checked={draft.procurementApproved}
                    onChange={v => set('procurementApproved', v)}
                    label={isAr ? 'مُعتمد' : 'Approved'}/>
          </Field>
          <Field label={isAr ? 'المخاطر' : 'Risk team'} required>
            <Toggle checked={draft.riskApproved}
                    onChange={v => set('riskApproved', v)}
                    label={isAr ? 'مُعتمد' : 'Approved'}/>
          </Field>
          {draft.isProcessor && (
            <Field label="DPO" required>
              <Toggle checked={draft.dpoApproved}
                      onChange={v => set('dpoApproved', v)}
                      label={isAr ? 'مُعتمد' : 'Approved'}/>
            </Field>
          )}
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'المورد' : 'Vendor'} v={draft.vendorName} />
            <Row k={isAr ? 'الطبقة' : 'Tier'} v={draft.criticality} />
            <Row k={isAr ? 'المتأصل / المتبقي' : 'Inherent / Residual'}
                 v={`${inherentCalc} / ${residualCalc}`} />
            <Row k={isAr ? 'معالج بيانات' : 'Processor'} v={draft.isProcessor ? 'Yes' : 'No'} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'تقييم مخاطر المورد' : 'Vendor Risk Assessment'}
        sub={isAr ? 'معالج من ٥ خطوات — متأصل × ضوابط × متبقي'
                  : '5-step wizard — inherent × controls × residual'}
        eyebrow={isAr ? 'سلسلة الإمداد / TPRM' : 'Supply Chain / TPRM'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/tprm')}/>
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
