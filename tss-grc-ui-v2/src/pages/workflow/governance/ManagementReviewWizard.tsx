import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardCheck, FileText, Stamp, Users } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';

/**
 * §7.6 Cross-module — Management Review wizard.
 *
 * <p>Generic shell used by:
 *   - BCM §9.3 (ISO 22301) — annual continuity review
 *   - ISO 27001 §9.3 — annual ISMS review
 *   - NDI cycle close-out — bi-annual data-governance review
 *
 * <p>4 steps:
 *   1. Scope + attendees + period
 *   2. Inputs (clauses a–g of ISO 22301 §9.3.2 — performance, KPIs, audits, prior actions)
 *   3. Decisions + improvement opportunities
 *   4. Action items + sign-off
 */
type ReviewScope = 'BCM_22301' | 'ISMS_27001' | 'NDI' | 'NCA_ECC' | 'PDPL';

const SCOPE_LABELS: Record<ReviewScope, { en: string; ar: string; clause: string }> = {
  BCM_22301: { en: 'BCM — ISO 22301 §9.3',  ar: 'استمرارية الأعمال — ISO 22301', clause: 'ISO 22301 §9.3' },
  ISMS_27001:{ en: 'ISMS — ISO 27001 §9.3', ar: 'أمن المعلومات — ISO 27001',    clause: 'ISO 27001 §9.3' },
  NDI:       { en: 'NDI cycle close-out',   ar: 'إغلاق دورة NDI',              clause: 'NDI cycle' },
  NCA_ECC:   { en: 'NCA ECC governance review', ar: 'مراجعة الحوكمة ECC',     clause: 'NCA ECC §1-3' },
  PDPL:      { en: 'PDPL data-protection review', ar: 'مراجعة حماية البيانات', clause: 'PDPL governance' },
};

interface MgmtReviewDraft {
  scope: ReviewScope;
  period: string;
  attendees: string;
  chairRole: string;
  reviewDate: string;
  inputPerformance: string;
  inputAudits: string;
  inputPriorActions: string;
  inputObjectives: string;
  inputOpportunities: string;
  decisions: string;
  changesNeeded?: string;
  actionItems: string;
  signedByChair: boolean;
  signedBySecretary: boolean;
  notes?: string;
}

export function ManagementReviewWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<MgmtReviewDraft>({
    scope: 'BCM_22301',
    period: '',
    attendees: '',
    chairRole: '',
    reviewDate: '',
    inputPerformance: '',
    inputAudits: '',
    inputPriorActions: '',
    inputObjectives: '',
    inputOpportunities: '',
    decisions: '',
    actionItems: '',
    signedByChair: false,
    signedBySecretary: false,
  });

  function set<K extends keyof MgmtReviewDraft>(key: K, value: MgmtReviewDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  async function submit() {
    setSubmitting(true);
    try {
      await new Promise(r => setTimeout(r, 300));
      toast.success(isAr ? 'تم اعتماد محضر المراجعة' : 'Management review minutes locked');
      navigate('/dashboard');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'scope',
      title: '1. Scope + attendees', titleAr: '١. النطاق والحضور',
      Icon: Users,
      validate: () => !draft.period.trim() || !draft.attendees.trim() || !draft.chairRole.trim() || !draft.reviewDate
        ? (isAr ? 'النطاق والحضور والرئاسة والتاريخ مطلوبة'
                : 'Scope, attendees, chair and date are required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'النطاق' : 'Review scope'}>
            <select className="input" value={draft.scope}
                    onChange={e => set('scope', e.target.value as ReviewScope)}>
              {(Object.keys(SCOPE_LABELS) as ReviewScope[]).map(s => (
                <option key={s} value={s}>{isAr ? SCOPE_LABELS[s].ar : SCOPE_LABELS[s].en}</option>
              ))}
            </select>
          </Field>
          <Field label={isAr ? 'الفترة المُراجعة' : 'Review period'} required>
            <input className="input" value={draft.period}
                   onChange={e => set('period', e.target.value)}
                   placeholder={isAr ? 'مثال: الفترة من يناير إلى ديسمبر ٢٠٢٥' : 'e.g. Jan–Dec 2025'}/>
          </Field>
          <Field label={isAr ? 'تاريخ المراجعة' : 'Review date'} required>
            <input className="input" type="date" value={draft.reviewDate}
                   onChange={e => set('reviewDate', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'دور رئيس الجلسة' : 'Chair role'} required>
            <input className="input" value={draft.chairRole}
                   onChange={e => set('chairRole', e.target.value)}
                   placeholder={isAr ? 'مثال: الرئيس التنفيذي' : 'e.g. CEO'}/>
          </Field>
          <Field label={isAr ? 'الحضور (دور + اسم)' : 'Attendees (role + name)'} full required>
            <textarea className="input" rows={3} value={draft.attendees}
                      onChange={e => set('attendees', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: رئيس مجلس الإدارة، الرئيس التنفيذي، CISO، DPO، مدير الالتزام'
                        : 'e.g. Board Chair, CEO, CISO, DPO, Compliance Lead'}/>
          </Field>
          <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
            <Badge tone="info">{SCOPE_LABELS[draft.scope].clause}</Badge>{' '}
            {isAr
              ? 'يجب أن تشمل المُدخلات بنود الفقرة ٩.٣.٢ من المعيار.'
              : 'Inputs must cover the standard\'s clause-9.3.2 list.'}
          </div>
        </div>
      ),
    },

    {
      id: 'inputs',
      title: '2. Inputs', titleAr: '٢. المدخلات',
      Icon: FileText,
      validate: () => !draft.inputPerformance.trim() || !draft.inputAudits.trim() || !draft.inputPriorActions.trim()
        ? (isAr ? 'الأداء والتدقيق والإجراءات السابقة مطلوبة'
                : 'Performance, audits, and prior actions are required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'أداء النظام + مؤشرات الأداء' : 'System performance + KPIs'} full required>
            <textarea className="input" rows={3} value={draft.inputPerformance}
                      onChange={e => set('inputPerformance', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'نتائج التدقيق' : 'Audit results (internal + external)'} full required>
            <textarea className="input" rows={3} value={draft.inputAudits}
                      onChange={e => set('inputAudits', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'حالة الإجراءات السابقة' : 'Status of actions from previous reviews'} full required>
            <textarea className="input" rows={3} value={draft.inputPriorActions}
                      onChange={e => set('inputPriorActions', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'مدى تحقيق الأهداف' : 'Achievement of objectives'} full>
            <textarea className="input" rows={3} value={draft.inputObjectives}
                      onChange={e => set('inputObjectives', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'فرص التحسين' : 'Opportunities for improvement'} full>
            <textarea className="input" rows={3} value={draft.inputOpportunities}
                      onChange={e => set('inputOpportunities', e.target.value)}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'decisions',
      title: '3. Decisions', titleAr: '٣. القرارات',
      Icon: ClipboardCheck,
      validate: () => !draft.decisions.trim()
        ? (isAr ? 'يلزم تسجيل قرارات المراجعة' : 'Decisions log required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'القرارات والتوجيهات' : 'Decisions + management direction'} full required>
            <textarea className="input" rows={4} value={draft.decisions}
                      onChange={e => set('decisions', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'تغييرات في النظام (إن وُجدت)' : 'Changes to the management system'} full>
            <textarea className="input" rows={3} value={draft.changesNeeded ?? ''}
                      onChange={e => set('changesNeeded', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: تحديث سياسة الاستمرارية + رفع ميزانية BCM'
                        : 'e.g. revise continuity policy + raise BCM budget'}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'signoff',
      title: '4. Action items + sign-off', titleAr: '٤. الإجراءات والاعتماد',
      Icon: Stamp,
      validate: () => !draft.actionItems.trim() || !draft.signedByChair
        ? (isAr ? 'الإجراءات واعتماد الرئيس مطلوبان'
                : 'Action items + chair sign-off required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'بنود العمل (مع المالك والموعد)' : 'Action items (with owner + due date)'} full required>
            <textarea className="input" rows={4} value={draft.actionItems}
                      onChange={e => set('actionItems', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'ملاحظات' : 'Notes'} full>
            <textarea className="input" rows={2} value={draft.notes ?? ''}
                      onChange={e => set('notes', e.target.value)}/>
          </Field>
          <Toggle checked={draft.signedByChair}
                  onChange={v => set('signedByChair', v)}
                  label={isAr ? 'اعتماد رئيس الجلسة' : 'Chair signed'}/>
          <Toggle checked={draft.signedBySecretary}
                  onChange={v => set('signedBySecretary', v)}
                  label={isAr ? 'اعتماد سكرتير اللجنة' : 'Secretary signed'}/>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'النطاق' : 'Scope'}
                 v={isAr ? SCOPE_LABELS[draft.scope].ar : SCOPE_LABELS[draft.scope].en} />
            <Row k={isAr ? 'الفترة' : 'Period'} v={draft.period} />
            <Row k={isAr ? 'التاريخ' : 'Date'} v={draft.reviewDate} />
            <Row k={isAr ? 'الرئاسة' : 'Chair'} v={draft.chairRole} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'المراجعة الإدارية' : 'Management Review'}
        sub={isAr
          ? 'معالج عام من ٤ خطوات — يُستخدم لمراجعات BCM وISMS وNDI'
          : 'Generic 4-step wizard — serves BCM, ISMS, and NDI cycle reviews'}
        eyebrow={isAr ? 'الحوكمة' : 'Governance'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/dashboard')}
        submitLabel={isAr ? 'اعتماد المحضر' : 'Lock minutes'}/>
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
