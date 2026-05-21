import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BrainCircuit, ClipboardCheck, GitBranch, Stamp } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';
import { submitWizard } from '../../../lib/submitWizard';

/**
 * §7.6 Cross-module — Root-Cause Analysis wizard.
 *
 * <p>4 steps:
 *   1. Incident reference + framework (5-Whys / Fishbone / Bowtie)
 *   2. Causal chain capture (per-framework)
 *   3. Root cause + contributing factors + AI draft (V116)
 *   4. Corrective actions + owner + review
 */
type Framework = 'FIVE_WHYS' | 'FISHBONE' | 'BOWTIE';

interface RcaDraft {
  incidentRef: string;
  framework: Framework;
  whyChain: string[];                 // 5-Whys answers
  fishboneCategories: Record<string, string>; // category → notes
  rootCause: string;
  contributingFactors?: string;
  aiDraftAccepted: boolean;
  correctiveActions: string;
  ownerRole?: string;
  reviewerRole?: string;
  reviewDate?: string;
  closed: boolean;
  notes?: string;
}

const FISHBONE_CATS = ['People', 'Process', 'Technology', 'Environment', 'Vendor'] as const;

export function IncidentRcaWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<RcaDraft>({
    incidentRef: '',
    framework: 'FIVE_WHYS',
    whyChain: ['', '', '', '', ''],
    fishboneCategories: {},
    rootCause: '',
    aiDraftAccepted: false,
    correctiveActions: '',
    closed: false,
  });

  function set<K extends keyof RcaDraft>(key: K, value: RcaDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  function setWhy(i: number, val: string) {
    setDraft(d => {
      const next = [...d.whyChain];
      next[i] = val;
      return { ...d, whyChain: next };
    });
  }

  async function submit() {
    setSubmitting(true);
    try {
      const created = await submitWizard({
        workflowId: 'incident-rca',
        workflowModule: 'INCIDENT',
        titleEn: `RCA: ${draft.incidentRef}`,
        summaryEn: `${draft.framework} · root cause: `
                 + draft.rootCause.slice(0, 100)
                 + (draft.rootCause.length > 100 ? '…' : '')
                 + (draft.ownerRole ? ` · owner=${draft.ownerRole}` : ''),
        status: draft.closed ? 'CLOSED' : 'SUBMITTED',
        draft,
      });
      toast.success(isAr
        ? `تم اعتماد تحليل السبب الجذري (#${created.id})`
        : `RCA #${created.id} recorded`);
      navigate('/rca');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'intake',
      title: '1. Incident + framework', titleAr: '١. الحدث والإطار',
      Icon: ClipboardCheck,
      validate: () => !draft.incidentRef.trim()
        ? (isAr ? 'مرجع الحدث مطلوب' : 'Incident reference required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'مرجع الحدث' : 'Incident reference'} required>
            <input className="input" value={draft.incidentRef}
                   onChange={e => set('incidentRef', e.target.value)}
                   placeholder="INC-2026-00187"/>
          </Field>
          <Field label={isAr ? 'إطار التحليل' : 'Analysis framework'}>
            <select className="input" value={draft.framework}
                    onChange={e => set('framework', e.target.value as Framework)}>
              <option value="FIVE_WHYS">5-Whys</option>
              <option value="FISHBONE">Fishbone / Ishikawa</option>
              <option value="BOWTIE">Bowtie</option>
            </select>
          </Field>
        </div>
      ),
    },

    {
      id: 'causal',
      title: '2. Causal chain', titleAr: '٢. السلسلة السببية',
      Icon: GitBranch,
      validate: () => {
        if (draft.framework === 'FIVE_WHYS') {
          return draft.whyChain.filter(w => w.trim()).length < 3
            ? (isAr ? 'يلزم ٣ مستويات على الأقل' : 'At least 3 levels required')
            : null;
        }
        if (draft.framework === 'FISHBONE') {
          return Object.values(draft.fishboneCategories).filter(v => v?.trim()).length < 2
            ? (isAr ? 'يلزم فئتان على الأقل من الأسماك' : 'Fill at least 2 fishbone categories')
            : null;
        }
        return null;
      },
      content: () => (
        <div className="wf-form-grid">
          {draft.framework === 'FIVE_WHYS' && draft.whyChain.map((w, i) => (
            <Field key={i} label={`${isAr ? 'لماذا' : 'Why'} #${i + 1}`} full>
              <input className="input" value={w}
                     onChange={e => setWhy(i, e.target.value)}
                     placeholder={i === 0
                       ? (isAr ? 'لماذا حدث ذلك؟' : 'Why did this happen?')
                       : (isAr ? 'ولماذا حدث ذلك؟' : 'And why did that happen?')}/>
            </Field>
          ))}
          {draft.framework === 'FISHBONE' && FISHBONE_CATS.map(cat => (
            <Field key={cat} label={cat} full>
              <textarea className="input" rows={2}
                        value={draft.fishboneCategories[cat] ?? ''}
                        onChange={e => setDraft(d => ({
                          ...d,
                          fishboneCategories: { ...d.fishboneCategories, [cat]: e.target.value },
                        }))}/>
            </Field>
          ))}
          {draft.framework === 'BOWTIE' && (
            <Field label={isAr ? 'الفصل الكامل (تهديد ← حدث ← أثر)' : 'Full bowtie (threat → event → impact)'} full>
              <textarea className="input" rows={6}
                        value={draft.contributingFactors ?? ''}
                        onChange={e => set('contributingFactors', e.target.value)}/>
            </Field>
          )}
        </div>
      ),
    },

    {
      id: 'cause',
      title: '3. Root cause + AI', titleAr: '٣. السبب الجذري والذكاء الاصطناعي',
      Icon: BrainCircuit,
      validate: () => !draft.rootCause.trim()
        ? (isAr ? 'يلزم وصف السبب الجذري' : 'Root cause statement required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'السبب الجذري' : 'Root cause'} full required>
            <textarea className="input" rows={4} value={draft.rootCause}
                      onChange={e => set('rootCause', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: قاعدة جدار الحماية مكنت الوصول العام بسبب خطأ بشري في القاعدة الجديدة'
                        : 'e.g. firewall rule allowed public access due to a human error in the new ruleset'}/>
          </Field>
          <Field label={isAr ? 'العوامل المساهمة' : 'Contributing factors'} full>
            <textarea className="input" rows={3} value={draft.contributingFactors ?? ''}
                      onChange={e => set('contributingFactors', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: غياب المراجعة الزوجية، عدم وجود اختبار آلي بعد التغيير'
                        : 'e.g. no peer review, no automated post-change validation'}/>
          </Field>
          <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
            <Badge tone="info">{isAr ? 'مسودة بالذكاء الاصطناعي (V116)' : 'AI draft (V116)'}</Badge>{' '}
            {isAr
              ? 'يولّد المحرّك مسودة استناداً إلى السجل والإطار — تظل المراجعة البشرية إلزامية.'
              : 'The engine drafts an analysis from the timeline + framework — human review remains mandatory.'}
          </div>
          <Toggle checked={draft.aiDraftAccepted}
                  onChange={v => set('aiDraftAccepted', v)}
                  label={isAr ? 'تم استعراض اقتراح الذكاء الاصطناعي' : 'AI suggestion reviewed'}/>
        </div>
      ),
    },

    {
      id: 'capa',
      title: '4. Corrective actions', titleAr: '٤. الإجراءات التصحيحية',
      Icon: Stamp,
      validate: () => !draft.correctiveActions.trim() || !draft.ownerRole?.trim()
        ? (isAr ? 'الإجراءات والمالك مطلوبة' : 'Corrective actions and owner required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الإجراءات التصحيحية' : 'Corrective actions'} full required>
            <textarea className="input" rows={4} value={draft.correctiveActions}
                      onChange={e => set('correctiveActions', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: إلزام المراجعة الزوجية لتغييرات الجدار، إضافة اختبار آلي'
                        : 'e.g. mandate peer review for firewall changes, add automated post-change test'}/>
          </Field>
          <Field label={isAr ? 'دور المالك' : 'Owner role'} required>
            <input className="input" value={draft.ownerRole ?? ''}
                   onChange={e => set('ownerRole', e.target.value)}
                   placeholder="e.g. Network Security Lead"/>
          </Field>
          <Field label={isAr ? 'دور المراجع' : 'Reviewer role'}>
            <input className="input" value={draft.reviewerRole ?? ''}
                   onChange={e => set('reviewerRole', e.target.value)}
                   placeholder="e.g. CISO"/>
          </Field>
          <Field label={isAr ? 'تاريخ المراجعة' : 'Review date'}>
            <input className="input" type="date" value={draft.reviewDate ?? ''}
                   onChange={e => set('reviewDate', e.target.value)}/>
          </Field>
          <Toggle checked={draft.closed}
                  onChange={v => set('closed', v)}
                  label={isAr ? 'إغلاق تحليل السبب الجذري' : 'Close this RCA'}/>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'الحدث' : 'Incident'} v={draft.incidentRef} />
            <Row k={isAr ? 'الإطار' : 'Framework'} v={draft.framework} />
            <Row k={isAr ? 'السبب الجذري' : 'Root cause'}
                 v={draft.rootCause.slice(0, 80) + (draft.rootCause.length > 80 ? '…' : '')} />
            <Row k={isAr ? 'المالك' : 'Owner'} v={draft.ownerRole ?? '—'} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'تحليل السبب الجذري' : 'Root-Cause Analysis'}
        sub={isAr
          ? 'معالج من ٤ خطوات — مرجع الحدث → السلسلة → السبب الجذري → الإجراءات'
          : '4-step wizard — incident ref → causal chain → root cause + AI → corrective actions'}
        eyebrow={isAr ? 'إدارة الحوادث' : 'Incident management'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/rca')}
        submitLabel={isAr ? 'اعتماد التحليل' : 'Lock RCA'}/>
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
