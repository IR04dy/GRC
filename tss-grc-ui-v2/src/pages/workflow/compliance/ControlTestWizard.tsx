import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardCheck, FileCheck2, Search, Stamp } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge, type BadgeTone } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';
import { submitWizard } from '../../../lib/submitWizard';

/**
 * §7.6 Cross-module — Control Test wizard.
 *
 * <p>4 steps:
 *   1. Control + scope + test period
 *   2. Test method + sampling
 *   3. Result + findings
 *   4. Owner + retest schedule + CAPA hand-off
 */
type Method = 'INQUIRY' | 'OBSERVATION' | 'INSPECTION' | 'REPERFORMANCE' | 'AUTOMATED';
type Result = 'EFFECTIVE' | 'PARTIALLY_EFFECTIVE' | 'INEFFECTIVE' | 'NOT_APPLICABLE';

const RESULT_TONE: Record<Result, BadgeTone> = {
  EFFECTIVE:           'success',
  PARTIALLY_EFFECTIVE: 'warning',
  INEFFECTIVE:         'danger',
  NOT_APPLICABLE:      'neutral',
};

interface ControlTestDraft {
  controlRef: string;
  controlTitle: string;
  framework: string;
  testPeriodFrom: string;
  testPeriodTo: string;
  method: Method;
  populationSize?: number;
  sampleSize?: number;
  samplingNotes?: string;
  result: Result;
  findings?: string;
  evidenceRefs?: string;
  testerRole: string;
  reviewerRole?: string;
  retestMonths: number;
  capaRequired: boolean;
  capaDescription?: string;
  notes?: string;
}

export function ControlTestWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<ControlTestDraft>({
    controlRef: '',
    controlTitle: '',
    framework: 'NCA ECC v2',
    testPeriodFrom: '',
    testPeriodTo: '',
    method: 'INSPECTION',
    result: 'EFFECTIVE',
    testerRole: '',
    retestMonths: 12,
    capaRequired: false,
  });

  function set<K extends keyof ControlTestDraft>(key: K, value: ControlTestDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  async function submit() {
    setSubmitting(true);
    try {
      const created = await submitWizard({
        workflowId: 'compliance-control-test',
        workflowModule: 'COMPLIANCE',
        titleEn: `Test: ${draft.controlRef} — ${draft.controlTitle}`.slice(0, 280),
        regulatoryAnchor: draft.framework,
        summaryEn: `${draft.result} · method=${draft.method}`
                 + (draft.sampleSize ? ` · sample=${draft.sampleSize}/${draft.populationSize ?? '?'}` : '')
                 + (draft.capaRequired ? ' · CAPA-OPENED' : '')
                 + ` · retest=${draft.retestMonths}m`,
        status: draft.result === 'EFFECTIVE' ? 'APPROVED'
              : draft.result === 'INEFFECTIVE' ? 'REJECTED'
              : 'UNDER_REVIEW',
        draft,
      });
      toast.success(isAr
        ? `تم اعتماد اختبار الضابط (#${created.id})`
        : draft.capaRequired
          ? `Control test #${created.id} recorded — CAPA opened`
          : `Control test #${created.id} recorded`);
      navigate('/compliance');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  // Auto-suggest: ineffective + partially effective need a CAPA
  const capaSuggested =
    draft.result === 'INEFFECTIVE' || draft.result === 'PARTIALLY_EFFECTIVE';

  const steps: WizardStep[] = [
    {
      id: 'control',
      title: '1. Control + period', titleAr: '١. الضابط والفترة',
      Icon: ClipboardCheck,
      validate: () =>
        !draft.controlRef.trim() || !draft.controlTitle.trim() || !draft.testPeriodFrom || !draft.testPeriodTo
          ? (isAr ? 'مرجع الضابط والعنوان والفترة مطلوبة'
                  : 'Control ref, title and period are required')
          : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'مرجع الضابط' : 'Control reference'} required>
            <input className="input" value={draft.controlRef}
                   onChange={e => set('controlRef', e.target.value)}
                   placeholder="e.g. ECC 2-3-3-1"/>
          </Field>
          <Field label={isAr ? 'الإطار' : 'Framework'}>
            <select className="input" value={draft.framework}
                    onChange={e => set('framework', e.target.value)}>
              <option>NCA ECC v2</option>
              <option>NCA CCC</option>
              <option>ISO 27001</option>
              <option>ISO 22301</option>
              <option>PDPL</option>
              <option>SAMA CSF</option>
              <option>DGA NDI</option>
              <option>Internal</option>
            </select>
          </Field>
          <Field label={isAr ? 'عنوان الضابط' : 'Control title'} full required>
            <input className="input" value={draft.controlTitle}
                   onChange={e => set('controlTitle', e.target.value)}
                   placeholder={isAr
                     ? 'مثال: تفعيل المصادقة متعددة العوامل على الأنظمة الحرجة'
                     : 'e.g. Enforce MFA on all critical systems'}/>
          </Field>
          <Field label={isAr ? 'من' : 'Period start'} required>
            <input className="input" type="date" value={draft.testPeriodFrom}
                   onChange={e => set('testPeriodFrom', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'إلى' : 'Period end'} required>
            <input className="input" type="date" value={draft.testPeriodTo}
                   onChange={e => set('testPeriodTo', e.target.value)}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'method',
      title: '2. Method + sampling', titleAr: '٢. الطريقة والعينات',
      Icon: Search,
      validate: () =>
        (draft.method === 'INSPECTION' || draft.method === 'REPERFORMANCE')
        && (!draft.populationSize || !draft.sampleSize)
          ? (isAr ? 'حجم المجتمع والعينة مطلوبان لطرق الاختبار الإحصائي'
                  : 'Population and sample size required for inspection/re-performance methods')
          : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'طريقة الاختبار' : 'Test method'}>
            <select className="input" value={draft.method}
                    onChange={e => set('method', e.target.value as Method)}>
              <option value="INQUIRY">Inquiry / interview</option>
              <option value="OBSERVATION">Observation</option>
              <option value="INSPECTION">Document inspection</option>
              <option value="REPERFORMANCE">Re-performance</option>
              <option value="AUTOMATED">Automated check (connector)</option>
            </select>
          </Field>
          <Field label={isAr ? 'حجم المجتمع' : 'Population size'}>
            <input className="input" type="number" min={0}
                   value={draft.populationSize ?? ''}
                   onChange={e => set('populationSize', e.target.value ? Number(e.target.value) : undefined)}/>
          </Field>
          <Field label={isAr ? 'حجم العينة' : 'Sample size'}>
            <input className="input" type="number" min={0}
                   value={draft.sampleSize ?? ''}
                   onChange={e => set('sampleSize', e.target.value ? Number(e.target.value) : undefined)}/>
          </Field>
          <Field label={isAr ? 'ملاحظات المعاينة' : 'Sampling notes / approach'} full>
            <textarea className="input" rows={3} value={draft.samplingNotes ?? ''}
                      onChange={e => set('samplingNotes', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: عينة عشوائية ٢٥ من ١٢٠٠ مستخدم، تأكيد تفعيل MFA لكل عينة'
                        : 'e.g. random sample of 25 of 1,200 users, verified MFA enforcement per user'}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'result',
      title: '3. Result + findings', titleAr: '٣. النتيجة والاكتشافات',
      Icon: FileCheck2,
      validate: () =>
        (draft.result === 'INEFFECTIVE' || draft.result === 'PARTIALLY_EFFECTIVE') && !draft.findings?.trim()
          ? (isAr ? 'يلزم وصف الاكتشافات للنتيجة غير الفعالة'
                  : 'Findings required for ineffective/partial results')
          : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'نتيجة الاختبار' : 'Test result'}>
            <select className="input" value={draft.result}
                    onChange={e => set('result', e.target.value as Result)}>
              <option value="EFFECTIVE">Effective</option>
              <option value="PARTIALLY_EFFECTIVE">Partially effective</option>
              <option value="INEFFECTIVE">Ineffective</option>
              <option value="NOT_APPLICABLE">Not applicable</option>
            </select>
          </Field>
          <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
            <Badge tone={RESULT_TONE[draft.result]}>{draft.result}</Badge>{' '}
            {capaSuggested && (isAr
              ? '— يلزم فتح إجراء تصحيحي (CAPA) في الخطوة التالية.'
              : '— a CAPA will be required at the next step.')}
          </div>
          <Field label={isAr ? 'الاكتشافات' : 'Findings'} full
                 required={draft.result === 'INEFFECTIVE' || draft.result === 'PARTIALLY_EFFECTIVE'}>
            <textarea className="input" rows={4} value={draft.findings ?? ''}
                      onChange={e => set('findings', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: ٣ من ٢٥ مستخدماً يتجاوزون MFA عبر تطبيق مكتبي قديم'
                        : 'e.g. 3 of 25 sampled users bypass MFA via a legacy desktop client'}/>
          </Field>
          <Field label={isAr ? 'مراجع الأدلة' : 'Evidence references'} full>
            <textarea className="input" rows={2} value={draft.evidenceRefs ?? ''}
                      onChange={e => set('evidenceRefs', e.target.value)}
                      placeholder="EVID-2026-0481, EVID-2026-0482, screenshot batch S-2026-Q2-MFA"/>
          </Field>
        </div>
      ),
    },

    {
      id: 'capa',
      title: '4. Owner + retest + CAPA', titleAr: '٤. المالك وإعادة الاختبار وCAPA',
      Icon: Stamp,
      validate: () =>
        !draft.testerRole.trim()
          ? (isAr ? 'دور المختبِر مطلوب' : 'Tester role required')
          : draft.capaRequired && !draft.capaDescription?.trim()
            ? (isAr ? 'وصف الإجراء التصحيحي مطلوب' : 'CAPA description required')
            : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'دور المختبِر' : 'Tester role'} required>
            <input className="input" value={draft.testerRole}
                   onChange={e => set('testerRole', e.target.value)}
                   placeholder="e.g. Internal Auditor"/>
          </Field>
          <Field label={isAr ? 'دور المراجع' : 'Reviewer role'}>
            <input className="input" value={draft.reviewerRole ?? ''}
                   onChange={e => set('reviewerRole', e.target.value)}
                   placeholder="e.g. Compliance Manager"/>
          </Field>
          <Field label={isAr ? 'إعادة الاختبار (أشهر)' : 'Retest interval (months)'}>
            <input className="input" type="number" min={1} max={36}
                   value={draft.retestMonths}
                   onChange={e => set('retestMonths', Number(e.target.value || 12))}/>
          </Field>
          <Toggle checked={draft.capaRequired}
                  onChange={v => set('capaRequired', v)}
                  label={isAr ? 'فتح إجراء تصحيحي (CAPA)' : 'Open a corrective action (CAPA)'}/>
          {(draft.capaRequired || capaSuggested) && (
            <Field label={isAr ? 'وصف الإجراء التصحيحي' : 'CAPA description'} full required={draft.capaRequired}>
              <textarea className="input" rows={3} value={draft.capaDescription ?? ''}
                        onChange={e => set('capaDescription', e.target.value)}
                        placeholder={isAr
                          ? 'مثال: تعطيل العميل المكتبي القديم وفرض MFA على كل المنافذ خلال ٣٠ يوماً'
                          : 'e.g. retire legacy desktop client and enforce MFA on all endpoints within 30 days'}/>
            </Field>
          )}
          <Field label={isAr ? 'ملاحظات' : 'Notes'} full>
            <textarea className="input" rows={2} value={draft.notes ?? ''}
                      onChange={e => set('notes', e.target.value)}/>
          </Field>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'الضابط' : 'Control'} v={`${draft.controlRef} · ${draft.controlTitle}`} />
            <Row k={isAr ? 'الإطار' : 'Framework'} v={draft.framework} />
            <Row k={isAr ? 'الطريقة' : 'Method'} v={draft.method} />
            <Row k={isAr ? 'النتيجة' : 'Result'} v={draft.result} />
            <Row k={isAr ? 'إعادة الاختبار' : 'Retest'} v={`${draft.retestMonths} ${isAr ? 'شهراً' : 'months'}`} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'اختبار الضابط' : 'Control Test'}
        sub={isAr
          ? 'معالج من ٤ خطوات — الضابط والفترة → الطريقة → النتيجة → CAPA'
          : '4-step wizard — control + period → method + sampling → result → owner + CAPA'}
        eyebrow={isAr ? 'الالتزام' : 'Compliance'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/compliance')}
        submitLabel={isAr ? 'حفظ الاختبار' : 'Save test'}/>
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
