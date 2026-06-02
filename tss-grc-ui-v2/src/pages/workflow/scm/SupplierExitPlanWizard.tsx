import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertOctagon, Database, Stamp, Truck } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { tlabel } from '../../../lib/labels';
import { useToast } from '../../../lib/toast';
import { submitWizard } from '../../../lib/submitWizard';

/**
 * SCM §7.2 #11 — Supplier Exit Plan wizard.
 *
 * <p>4 steps:
 *   1. Triggers — events that activate the exit plan
 *   2. Alternates — pre-qualified vendors / in-house fallback
 *   3. Data return & destruction — what comes back, what gets destroyed
 *   4. Notice + comms + sign-off
 */
type Trigger =
  | 'CONTRACT_EXPIRY'
  | 'BREACH_OF_SLA'
  | 'REGULATORY_ORDER'
  | 'STRATEGIC_SHIFT'
  | 'VENDOR_INSOLVENCY'
  | 'DATA_BREACH'
  | 'CONCENTRATION_RISK';

const TRIGGER_LABELS: Record<Trigger, { en: string; ar: string }> = {
  CONTRACT_EXPIRY:     { en: 'Contract expiry',          ar: 'انتهاء العقد' },
  BREACH_OF_SLA:       { en: 'Persistent SLA breach',    ar: 'إخلال متكرر بـ SLA' },
  REGULATORY_ORDER:    { en: 'Regulatory order',         ar: 'أمر تنظيمي' },
  STRATEGIC_SHIFT:     { en: 'Strategic re-sourcing',    ar: 'إعادة توجيه استراتيجي' },
  VENDOR_INSOLVENCY:   { en: 'Vendor insolvency',        ar: 'إعسار المورد' },
  DATA_BREACH:         { en: 'Material data breach',     ar: 'اختراق بيانات جوهري' },
  CONCENTRATION_RISK:  { en: 'Concentration risk',       ar: 'مخاطر التركّز' },
};

interface ExitPlanDraft {
  vendorName: string;
  servicesScope: string;
  triggers: Trigger[];
  noticeDays: number;
  alternateOption: 'PREQUALIFIED' | 'IN_HOUSE' | 'TBD';
  alternateName?: string;
  dataReturnScope?: string;
  destructionScope?: string;
  destructionDeadlineDays: number;
  isProcessor: boolean;
  commsPlanEn?: string;
  procurementApproved: boolean;
  riskApproved: boolean;
  dpoApproved: boolean;
  notes?: string;
}

export function SupplierExitPlanWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<ExitPlanDraft>({
    vendorName: '',
    servicesScope: '',
    triggers: [],
    noticeDays: 90,
    alternateOption: 'TBD',
    destructionDeadlineDays: 30,
    isProcessor: false,
    procurementApproved: false,
    riskApproved: false,
    dpoApproved: false,
  });

  function set<K extends keyof ExitPlanDraft>(key: K, value: ExitPlanDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  function toggleTrigger(t: Trigger) {
    setDraft(d => ({
      ...d,
      triggers: d.triggers.includes(t)
        ? d.triggers.filter(x => x !== t)
        : [...d.triggers, t],
    }));
  }

  async function submit() {
    setSubmitting(true);
    try {
      const created = await submitWizard({
        workflowId: 'scm-exit-plan',
        workflowModule: 'SCM',
        titleEn: `Exit: ${draft.vendorName}`.slice(0, 280),
        summaryEn: `${draft.triggers.length} triggers · ${draft.alternateOption}`
                 + ` · notice=${draft.noticeDays}d`
                 + ` · destruction=${draft.destructionDeadlineDays}d`
                 + (draft.isProcessor ? ' · PROCESSOR' : ''),
        regulatoryAnchor: draft.isProcessor ? 'PDPL §18' : undefined,
        status: draft.procurementApproved && draft.riskApproved
          && (!draft.isProcessor || draft.dpoApproved)
            ? 'APPROVED' : 'SUBMITTED',
        draft,
      });
      toast.success(isAr
        ? `تم اعتماد خطة الخروج (#${created.id})`
        : `Exit plan #${created.id} locked in`);
      navigate('/scm');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  // Hijri-aware long-stop view of notice + destruction = "outside date"
  const outsideDays = draft.noticeDays + draft.destructionDeadlineDays;

  const steps: WizardStep[] = [
    {
      id: 'triggers',
      title: '1. Triggers', titleAr: '١. المحفزات',
      Icon: AlertOctagon,
      validate: () => !draft.vendorName.trim() || draft.triggers.length === 0
        ? (isAr ? 'اسم المورد ومحفز واحد على الأقل مطلوب'
                : 'Vendor name and at least one trigger are required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'اسم المورد' : 'Vendor name'} required>
            <input className="input" value={draft.vendorName}
                   onChange={e => set('vendorName', e.target.value)}
                   placeholder="e.g. AlBilad Cloud Services"/>
          </Field>
          <Field label={isAr ? 'نطاق الخدمات' : 'Services in scope'} full>
            <textarea className="input" rows={3} value={draft.servicesScope}
                      onChange={e => set('servicesScope', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: استضافة بوابة العملاء + خدمات DNS'
                        : 'e.g. customer portal hosting + DNS services'}/>
          </Field>
          <Field label={isAr ? 'محفزات الخروج' : 'Exit triggers'} full>
            <div className="wf-toggle-grid">
              {(Object.keys(TRIGGER_LABELS) as Trigger[]).map(t => (
                <Toggle key={t}
                        checked={draft.triggers.includes(t)}
                        onChange={() => toggleTrigger(t)}
                        label={tlabel(TRIGGER_LABELS, t, isAr)}/>
              ))}
            </div>
          </Field>
          <Toggle checked={draft.isProcessor}
                  onChange={v => set('isProcessor', v)}
                  label={isAr ? 'هذا المورد معالج بيانات شخصية' : 'Vendor is a personal-data processor'}/>
        </div>
      ),
    },

    {
      id: 'alternates',
      title: '2. Alternates', titleAr: '٢. البدائل',
      Icon: Truck,
      validate: () => draft.alternateOption === 'PREQUALIFIED' && !draft.alternateName?.trim()
        ? (isAr ? 'اسم المورد البديل مطلوب' : 'Alternate vendor name required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الخيار البديل' : 'Alternate option'}>
            <select className="input" value={draft.alternateOption}
                    onChange={e => set('alternateOption', e.target.value as ExitPlanDraft['alternateOption'])}>
              <option value="PREQUALIFIED">Pre-qualified vendor</option>
              <option value="IN_HOUSE">In-house fallback</option>
              <option value="TBD">To be determined</option>
            </select>
          </Field>
          {draft.alternateOption === 'PREQUALIFIED' && (
            <Field label={isAr ? 'اسم المورد البديل' : 'Alternate vendor name'} required>
              <input className="input" value={draft.alternateName ?? ''}
                     onChange={e => set('alternateName', e.target.value)}
                     placeholder="e.g. STC Cloud"/>
            </Field>
          )}
          {draft.alternateOption === 'TBD' && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone="warning">
                {isAr ? 'تحذير' : 'Warning'}
              </Badge>{' '}
              {isAr
                ? 'بقاء البديل غير محدد يبقي خطر التركّز قائماً — حدد خياراً قبل التوقيع النهائي.'
                : 'Leaving alternate as TBD keeps concentration risk live — pick a path before final sign-off.'}
            </div>
          )}
        </div>
      ),
    },

    {
      id: 'data',
      title: '3. Data return + destruction', titleAr: '٣. إعادة البيانات والإتلاف',
      Icon: Database,
      validate: () => draft.isProcessor && !draft.dataReturnScope?.trim()
        ? (isAr ? 'حدّد نطاق إعادة البيانات لمعالج البيانات الشخصية'
                : 'Data-return scope required for a personal-data processor')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'نطاق البيانات المُعادة' : 'Data return scope'} full
                 required={draft.isProcessor}>
            <textarea className="input" rows={3} value={draft.dataReturnScope ?? ''}
                      onChange={e => set('dataReturnScope', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: سجلات العملاء + النسخ الاحتياطية بصيغة CSV مشفّر'
                        : 'e.g. customer records + backups in encrypted CSV'}/>
          </Field>
          <Field label={isAr ? 'نطاق الإتلاف' : 'Destruction scope'} full>
            <textarea className="input" rows={3} value={draft.destructionScope ?? ''}
                      onChange={e => set('destructionScope', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: نسخ السجلات في بيئة المورد + نسخ DR'
                        : 'e.g. copies of records in vendor environment + DR replicas'}/>
          </Field>
          <Field label={isAr ? 'مدة الإتلاف (أيام)' : 'Destruction deadline (days)'}>
            <input className="input" type="number" min={1} max={365}
                   value={draft.destructionDeadlineDays}
                   onChange={e => set('destructionDeadlineDays', Number(e.target.value || 30))}/>
          </Field>
          {draft.isProcessor && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone="info">PDPL §18</Badge>{' '}
              {isAr
                ? 'يلتزم المعالج بإعادة أو إتلاف البيانات وتقديم شهادة إتلاف عند الإغلاق.'
                : 'Processor must return or destroy personal data and issue a destruction certificate at closeout.'}
            </div>
          )}
        </div>
      ),
    },

    {
      id: 'notice',
      title: '4. Notice + sign-off', titleAr: '٤. الإشعار والاعتماد',
      Icon: Stamp,
      validate: () => !draft.procurementApproved || !draft.riskApproved
        ? (isAr ? 'يلزم اعتماد المشتريات وإدارة المخاطر'
                : 'Procurement + Risk approvals required')
        : draft.isProcessor && !draft.dpoApproved
          ? (isAr ? 'يلزم اعتماد DPO لمعالجات البيانات الشخصية'
                  : 'DPO approval required for personal-data processors')
          : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'مدة الإشعار التعاقدي (أيام)' : 'Contractual notice (days)'}>
            <input className="input" type="number" min={0} max={365}
                   value={draft.noticeDays}
                   onChange={e => set('noticeDays', Number(e.target.value || 0))}/>
          </Field>
          <Field label={isAr ? 'خطة التواصل' : 'Comms plan'} full>
            <textarea className="input" rows={3} value={draft.commsPlanEn ?? ''}
                      onChange={e => set('commsPlanEn', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: إشعار العملاء قبل ٣٠ يوماً + إخطار الجهة التنظيمية'
                        : 'e.g. customer notification 30 days in advance + regulator notification'}/>
          </Field>
          <Field label={isAr ? 'ملاحظات' : 'Notes'} full>
            <textarea className="input" rows={3} value={draft.notes ?? ''}
                      onChange={e => set('notes', e.target.value)}/>
          </Field>
          <Toggle checked={draft.procurementApproved}
                  onChange={v => set('procurementApproved', v)}
                  label={isAr ? 'اعتماد المشتريات' : 'Procurement approved'}/>
          <Toggle checked={draft.riskApproved}
                  onChange={v => set('riskApproved', v)}
                  label={isAr ? 'اعتماد إدارة المخاطر' : 'Risk approved'}/>
          {draft.isProcessor && (
            <Toggle checked={draft.dpoApproved}
                    onChange={v => set('dpoApproved', v)}
                    label={isAr ? 'اعتماد DPO' : 'DPO approved'}/>
          )}
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'المورد' : 'Vendor'} v={draft.vendorName} />
            <Row k={isAr ? 'المحفزات' : 'Triggers'}
                 v={draft.triggers.map(t => tlabel(TRIGGER_LABELS, t, isAr)).join(', ') || '—'} />
            <Row k={isAr ? 'البديل' : 'Alternate'}
                 v={draft.alternateOption === 'PREQUALIFIED'
                    ? (draft.alternateName ?? '—')
                    : draft.alternateOption === 'IN_HOUSE'
                      ? (isAr ? 'حل داخلي' : 'In-house')
                      : (isAr ? 'لم يُحدد بعد' : 'TBD')} />
            <Row k={isAr ? 'إشعار + إتلاف' : 'Notice + destruction'}
                 v={`${draft.noticeDays} + ${draft.destructionDeadlineDays} = ${outsideDays} ${isAr ? 'يوم' : 'days'}`} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'خطة الخروج من المورد' : 'Supplier Exit Plan'}
        sub={isAr
          ? 'معالج من ٤ خطوات — المحفزات → البدائل → إعادة البيانات → الإشعار والاعتماد'
          : '4-step wizard — triggers → alternates → data return → notice + sign-off'}
        eyebrow={isAr ? 'إدارة سلسلة التوريد / SCM' : 'SCM / Supply chain'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/scm')}
        submitLabel={isAr ? 'اعتماد خطة الخروج' : 'Lock exit plan'}/>
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
