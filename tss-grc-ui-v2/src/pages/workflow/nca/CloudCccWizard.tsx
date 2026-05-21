import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardCheck, Cloud, Globe, ShieldCheck, Stamp } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';
import { submitWizard } from '../../../lib/submitWizard';

/**
 * NCA §7.5 #10 — Cloud Cybersecurity Controls (CCC) Assessment.
 *
 * <p>5 steps:
 *   1. Applicability — service model + deployment + Kingdom data residency
 *   2. Tier classification (1–3, CCC §1.2)
 *   3. Control evaluation per domain (Identity / Data / Workloads / OT-Cloud)
 *   4. Cross-border + sub-processor inventory
 *   5. CISO + Data Office sign-off
 */
type ServiceModel    = 'SAAS' | 'PAAS' | 'IAAS';
type DeploymentModel = 'PUBLIC' | 'PRIVATE' | 'HYBRID' | 'COMMUNITY';
type CcTier          = 'TIER_1' | 'TIER_2' | 'TIER_3';

const DOMAINS = [
  { code: 'IDM',    en: 'Identity & access management',    ar: 'إدارة الهوية والوصول' },
  { code: 'DATA',   en: 'Data protection in cloud',        ar: 'حماية البيانات في السحابة' },
  { code: 'WORK',   en: 'Workload + network protection',   ar: 'حماية الأحمال والشبكة' },
  { code: 'AUDIT',  en: 'Audit + monitoring + IR',         ar: 'المراجعة والمراقبة والاستجابة' },
  { code: 'BCP',    en: 'Continuity + exit',                ar: 'الاستمرارية والخروج' },
];

interface CloudDraft {
  serviceName: string;
  provider: string;
  serviceModel: ServiceModel;
  deploymentModel: DeploymentModel;
  kingdomDataResident: boolean;
  residencyRegion?: string;
  cscctier: CcTier;
  domainMaturity: Record<string, number>;
  crossBorderTransfer: boolean;
  subProcessors?: string;
  dataOfficeReference?: string;
  cisoApproverName?: string;
  dpoApproverName?: string;
}

export function CloudCccWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<CloudDraft>({
    serviceName: '', provider: '',
    serviceModel: 'SAAS', deploymentModel: 'PUBLIC',
    kingdomDataResident: true,
    cscctier: 'TIER_2',
    domainMaturity: {},
    crossBorderTransfer: false,
  });

  function set<K extends keyof CloudDraft>(key: K, value: CloudDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }
  function setMaturity(code: string, level: number) {
    setDraft(d => ({ ...d, domainMaturity: { ...d.domainMaturity, [code]: level } }));
  }

  const avgMaturity = (() => {
    const v = Object.values(draft.domainMaturity);
    return v.length === 0 ? 0 : Math.round((v.reduce((a, b) => a + b, 0) / v.length) * 10) / 10;
  })();

  async function submit() {
    setSubmitting(true);
    try {
      const created = await submitWizard({
        workflowId: 'nca-cloud-ccc',
        workflowModule: 'NCA',
        titleEn: `CCC: ${draft.serviceName} (${draft.provider})`.slice(0, 280),
        regulatoryAnchor: 'NCA CCC',
        summaryEn: `${draft.serviceModel}/${draft.deploymentModel} · ${draft.cscctier}`
                 + ` · avg maturity ${avgMaturity}/5`
                 + (draft.kingdomDataResident ? ' · Kingdom-resident' : ' · OFF-KINGDOM')
                 + (draft.crossBorderTransfer ? ' · cross-border' : ''),
        status: draft.cisoApproverName && draft.dpoApproverName ? 'APPROVED' : 'SUBMITTED',
        draft,
      });
      toast.success(isAr
        ? `تم تسجيل تقييم CCC (#${created.id})`
        : `Cloud CCC #${created.id} recorded`);
      navigate('/cyber');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'applicability',
      title: 'Applicability', titleAr: 'القابلية',
      Icon: Cloud,
      validate: () => !draft.serviceName.trim() || !draft.provider.trim()
        ? (isAr ? 'الاسم والمزود مطلوبان' : 'Service + provider are required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'اسم الخدمة السحابية' : 'Cloud service name'} required>
            <input className="input" value={draft.serviceName}
                   onChange={e => set('serviceName', e.target.value)}
                   placeholder="STC Cloud — Production"/>
          </Field>
          <Field label={isAr ? 'المزود' : 'Provider'} required>
            <input className="input" value={draft.provider}
                   onChange={e => set('provider', e.target.value)}
                   placeholder="STC / Microsoft / Oracle / AWS"/>
          </Field>
          <Field label={isAr ? 'نموذج الخدمة' : 'Service model'}>
            <select className="input" value={draft.serviceModel}
                    onChange={e => set('serviceModel', e.target.value as ServiceModel)}>
              <option value="SAAS">SaaS</option>
              <option value="PAAS">PaaS</option>
              <option value="IAAS">IaaS</option>
            </select>
          </Field>
          <Field label={isAr ? 'نموذج النشر' : 'Deployment model'}>
            <select className="input" value={draft.deploymentModel}
                    onChange={e => set('deploymentModel', e.target.value as DeploymentModel)}>
              <option value="PUBLIC">Public</option>
              <option value="PRIVATE">Private</option>
              <option value="HYBRID">Hybrid</option>
              <option value="COMMUNITY">Community / government cloud</option>
            </select>
          </Field>
          <Field label={isAr ? 'إقامة البيانات في المملكة' : 'Kingdom data residency'}>
            <Toggle checked={draft.kingdomDataResident}
                    onChange={v => set('kingdomDataResident', v)}
                    label={isAr ? 'البيانات تبقى داخل المملكة' : 'Customer data stays inside KSA'}/>
          </Field>
          <Field label={isAr ? 'المنطقة' : 'Residency region'}>
            <input className="input" value={draft.residencyRegion ?? ''}
                   onChange={e => set('residencyRegion', e.target.value)}
                   placeholder={draft.kingdomDataResident ? 'Riyadh / Jeddah / Dammam' : 'EU / US / Other'}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'tier',
      title: 'Tier classification', titleAr: 'تصنيف الطبقة',
      Icon: ShieldCheck,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'طبقة CCC' : 'CCC tier'}>
            <select className="input" value={draft.cscctier}
                    onChange={e => set('cscctier', e.target.value as CcTier)}>
              <option value="TIER_1">Tier 1 — sensitive / classified workloads</option>
              <option value="TIER_2">Tier 2 — confidential business data</option>
              <option value="TIER_3">Tier 3 — internal / public-facing</option>
            </select>
          </Field>
          <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
            <Badge tone={draft.cscctier === 'TIER_1' ? 'danger' : draft.cscctier === 'TIER_2' ? 'warning' : 'info'}>
              {draft.cscctier}
            </Badge>{' '}
            {isAr
              ? 'هذه الطبقة تحدد الضوابط الإلزامية الواردة في CCC v3.'
              : 'Selected tier determines the mandatory CCC v3 control baseline.'}
          </div>
        </div>
      ),
    },

    {
      id: 'maturity',
      title: 'Control evaluation', titleAr: 'تقييم الضوابط',
      Icon: ClipboardCheck,
      validate: () => Object.keys(draft.domainMaturity).length < DOMAINS.length
        ? (isAr ? 'قَيِّم كل ميادين CCC' : 'Rate every CCC domain')
        : null,
      content: () => (
        <div className="wf-form-grid">
          {DOMAINS.map(d => (
            <Field key={d.code} label={isAr ? d.ar : d.en} required>
              <div className="wf-score-row">
                {[1,2,3,4,5].map(n => (
                  <button key={n} type="button"
                          className={'wf-score-chip' + (draft.domainMaturity[d.code] === n ? ' is-active' : '')}
                          onClick={() => setMaturity(d.code, n)}>
                    <span className="wf-score-num">{n}</span>
                    <span className="wf-score-label">
                      {n===1?'Ad-hoc':n===2?'Repeatable':n===3?'Defined':n===4?'Measured':'Optimised'}
                    </span>
                  </button>
                ))}
              </div>
            </Field>
          ))}
          {avgMaturity > 0 && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone={avgMaturity >= 4 ? 'success' : avgMaturity >= 3 ? 'info' : 'warning'}>
                {isAr ? 'متوسط النضج' : 'Avg maturity'}: {avgMaturity}/5
              </Badge>
            </div>
          )}
        </div>
      ),
    },

    {
      id: 'transfers',
      title: 'Cross-border + sub-processors', titleAr: 'الانتقال عبر الحدود',
      Icon: Globe,
      validate: () => draft.crossBorderTransfer && !draft.dataOfficeReference?.trim()
        ? (isAr ? 'يلزم مرجع مكتب البيانات السعودي للنقل غير الكافي' : 'Saudi Data Office reference required for non-adequate transfers')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'نقل بيانات عبر الحدود' : 'Cross-border data transfer'}>
            <Toggle checked={draft.crossBorderTransfer}
                    onChange={v => set('crossBorderTransfer', v)}
                    label={isAr ? 'البيانات تخرج من المملكة' : 'Customer data leaves KSA'}/>
          </Field>
          {draft.crossBorderTransfer && (
            <Field label={isAr ? 'مرجع مكتب البيانات السعودي' : 'Saudi Data Office reference'} required>
              <input className="input" value={draft.dataOfficeReference ?? ''}
                     onChange={e => set('dataOfficeReference', e.target.value)}
                     placeholder="SDO-2026-XB-0014"/>
            </Field>
          )}
          <Field label={isAr ? 'معالجو الباطن (الطرف الرابع)' : 'Sub-processors (fourth parties)'} full>
            <textarea className="input" rows={4} value={draft.subProcessors ?? ''}
                      onChange={e => set('subProcessors', e.target.value)}
                      placeholder={isAr
                        ? 'مثال:\n- Microsoft (subprocessor for backups)\n- CDN provider'
                        : 'e.g.\n- Microsoft Azure (backup target)\n- CDN provider for static assets'}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'signoff',
      title: 'Sign-off', titleAr: 'الاعتمادات',
      Icon: Stamp,
      validate: () => !draft.cisoApproverName?.trim()
        ? (isAr ? 'اعتماد CISO مطلوب' : 'CISO sign-off required')
        : (draft.crossBorderTransfer && !draft.dpoApproverName?.trim())
          ? (isAr ? 'اعتماد DPO مطلوب للنقل عبر الحدود' : 'DPO sign-off required for cross-border transfers')
          : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label="CISO" required>
            <input className="input" value={draft.cisoApproverName ?? ''}
                   onChange={e => set('cisoApproverName', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'DPO (إن كان هناك نقل)' : 'DPO (if cross-border)'}>
            <input className="input" value={draft.dpoApproverName ?? ''}
                   onChange={e => set('dpoApproverName', e.target.value)}/>
          </Field>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'الخدمة' : 'Service'} v={`${draft.serviceName} (${draft.provider})`} />
            <Row k={isAr ? 'النموذج' : 'Model'} v={`${draft.serviceModel} / ${draft.deploymentModel}`} />
            <Row k={isAr ? 'الإقامة' : 'Residency'}
                 v={`${draft.kingdomDataResident ? 'KSA' : 'International'} (${draft.residencyRegion ?? '—'})`} />
            <Row k="CCC tier" v={draft.cscctier} />
            <Row k={isAr ? 'متوسط النضج' : 'Avg maturity'} v={`${avgMaturity}/5`} />
            <Row k={isAr ? 'نقل عبر الحدود' : 'Cross-border'}
                 v={draft.crossBorderTransfer ? `Yes (${draft.dataOfficeReference ?? 'no ref'})` : 'No'} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'تقييم ضوابط الأمن السيبراني للحوسبة السحابية' : 'Cloud CCC Assessment'}
        sub={isAr ? 'معالج من ٥ خطوات وفق ضوابط NCA CCC v3' : '5-step wizard aligned with NCA CCC v3'}
        eyebrow={isAr ? 'الأمن السيبراني / NCA' : 'NCA / Cybersecurity'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/cyber')}/>
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
