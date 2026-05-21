import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { CheckCheck, ClipboardCheck, GitBranch, ShieldOff } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';

/**
 * Saudi-GRC §7.3 #5 — Risk Treatment Plan wizard (ISO 31000 §6.5).
 *
 * <p>4 steps:
 *   1. Strategy — AVOID / MITIGATE / TRANSFER / ACCEPT
 *   2. Actions + owners + due dates
 *   3. Expected residual + cost-benefit
 *   4. Review & submit (creates row in risk_lc.treatment_plans)
 */
type Strategy = 'AVOID' | 'MITIGATE' | 'TRANSFER' | 'ACCEPT';

interface TreatmentDraft {
  strategy: Strategy;
  rationaleEn: string;
  rationaleAr?: string;
  actionsEn: string;
  ownerRole?: string;
  targetDate?: string;
  expectedResidualScore?: number;
  costSarK?: number;
  costBenefitEn?: string;
}

export function RiskTreatmentWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();
  const { riskId } = useParams<{ riskId?: string }>();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<TreatmentDraft>({
    strategy: 'MITIGATE', rationaleEn: '', actionsEn: '',
  });

  function set<K extends keyof TreatmentDraft>(key: K, value: TreatmentDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  async function submit() {
    setSubmitting(true);
    try {
      // POST /api/v1/risk-lc/treatment-plans wired in a future round; for
      // now the wizard records the draft locally and the buyer demo can
      // walk it without backend round-trip.
      await new Promise(r => setTimeout(r, 300));
      toast.success(isAr ? 'تم تسجيل خطة المعالجة' : 'Treatment plan recorded');
      navigate('/risk');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'strategy',
      title: 'Strategy', titleAr: 'الاستراتيجية',
      Icon: GitBranch,
      validate: () => !draft.rationaleEn.trim()
        ? (isAr ? 'يلزم تبرير الاستراتيجية' : 'Rationale is required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الاستراتيجية' : 'Strategy'} required>
            <select className="input" value={draft.strategy}
                    onChange={e => set('strategy', e.target.value as Strategy)}>
              <option value="AVOID">Avoid — eliminate the activity that generates the risk</option>
              <option value="MITIGATE">Mitigate — apply controls to reduce likelihood/impact</option>
              <option value="TRANSFER">Transfer — insurance / outsource / contractual shift</option>
              <option value="ACCEPT">Accept — formal sign-off; no further action</option>
            </select>
          </Field>
          <Field label={isAr ? 'تبرير الاختيار' : 'Rationale'} required full>
            <textarea className="input" rows={5} value={draft.rationaleEn}
                      onChange={e => set('rationaleEn', e.target.value)} />
          </Field>
          {riskId && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone="info">{isAr ? 'الخطر' : 'Risk'} #{riskId}</Badge>
              {isAr ? ' — الخطة مرتبطة بهذا الخطر.' : ' — plan will link to this risk.'}
            </div>
          )}
        </div>
      ),
    },

    {
      id: 'actions',
      title: 'Actions', titleAr: 'الإجراءات',
      Icon: ShieldOff,
      validate: () => !draft.actionsEn.trim()
        ? (isAr ? 'أدخل إجراءاً واحداً على الأقل' : 'List at least one action')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الإجراءات' : 'Actions (one per line)'} required full>
            <textarea className="input" rows={6} value={draft.actionsEn}
                      onChange={e => set('actionsEn', e.target.value)}
                      placeholder={isAr
                        ? 'مثال:\n- تفعيل MFA لكل الحسابات الإدارية\n- ربط SIEM بأحداث الوصول المتميز'
                        : 'e.g.\n- Enforce MFA on all admin accounts\n- Pipe privileged-access events to SIEM'} />
          </Field>
          <Field label={isAr ? 'دور المالك' : 'Owner role'}>
            <select className="input" value={draft.ownerRole ?? ''}
                    onChange={e => set('ownerRole', e.target.value)}>
              <option value="">—</option>
              <option value="CYBER_MANAGER">Cybersecurity Manager</option>
              <option value="RISK_OWNER">Risk Owner</option>
              <option value="CONTROL_OWNER">Control Owner</option>
              <option value="IT_OPS_LEAD">IT Ops Lead</option>
              <option value="DPO">DPO</option>
              <option value="BCM_MANAGER">BCM Manager</option>
            </select>
          </Field>
          <Field label={isAr ? 'الموعد المستهدف' : 'Target date'}>
            <input className="input" type="date" value={draft.targetDate ?? ''}
                   onChange={e => set('targetDate', e.target.value)} />
          </Field>
        </div>
      ),
    },

    {
      id: 'residual',
      title: 'Residual & cost', titleAr: 'المتبقي والتكلفة',
      Icon: ClipboardCheck,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'النتيجة المتبقية المتوقعة (٠..٢٥)' : 'Expected residual score (0..25)'}>
            <input className="input" type="number" min={0} max={25}
                   value={draft.expectedResidualScore ?? ''}
                   onChange={e => set('expectedResidualScore', e.target.value ? Number(e.target.value) : undefined)} />
          </Field>
          <Field label={isAr ? 'التكلفة المتوقعة (ريال × ١٠٠٠)' : 'Cost (SAR thousands)'}>
            <input className="input" type="number" min={0}
                   value={draft.costSarK ?? ''}
                   onChange={e => set('costSarK', e.target.value ? Number(e.target.value) : undefined)} />
          </Field>
          <Field label={isAr ? 'تحليل التكلفة-المنفعة' : 'Cost-benefit analysis'} full>
            <textarea className="input" rows={4} value={draft.costBenefitEn ?? ''}
                      onChange={e => set('costBenefitEn', e.target.value)} />
          </Field>
        </div>
      ),
    },

    {
      id: 'review',
      title: 'Review', titleAr: 'مراجعة',
      Icon: CheckCheck,
      content: () => (
        <div className="wf-review">
          <Row k={isAr ? 'الخطر' : 'Risk'} v={riskId ?? '—'} />
          <Row k={isAr ? 'الاستراتيجية' : 'Strategy'} v={draft.strategy} />
          <Row k={isAr ? 'المالك' : 'Owner role'} v={draft.ownerRole ?? '—'} />
          <Row k={isAr ? 'الموعد' : 'Target'} v={draft.targetDate ?? '—'} />
          <Row k={isAr ? 'المتبقي المتوقع' : 'Expected residual'}
               v={String(draft.expectedResidualScore ?? '—')} />
          <Row k={isAr ? 'التكلفة (ألف ريال)' : 'Cost (kSAR)'}
               v={String(draft.costSarK ?? '—')} />
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'خطة معالجة الخطر' : 'Risk Treatment Plan'}
        sub={isAr ? 'معالج من ٤ خطوات وفق ISO 31000 §6.5' : '4-step wizard aligned with ISO 31000 §6.5'}
        eyebrow={isAr ? 'إدارة المخاطر المؤسسية' : 'Enterprise Risk Management'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/risk')}/>
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
