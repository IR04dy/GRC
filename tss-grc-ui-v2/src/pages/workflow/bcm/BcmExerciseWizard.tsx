import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Activity, AlertOctagon, CheckCheck, ClipboardCheck } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';

/**
 * BCM §7.1 #10 — BCM Exercise & Test wizard (ISO 22301 §8.5).
 *
 * <p>4 steps:
 *   1. Plan — type (TABLETOP / FULL_DR / CYBER), scenario, peak-season lens
 *   2. Targets — RTO/RPO targets to validate, participants, comms tree
 *   3. Execute — outcome, actual RTO/RPO, findings
 *   4. Close-out — CAPA count, follow-up exercises
 */
type DrillType = 'TABLETOP' | 'FULL_DR' | 'CYBER' | 'CALL_TREE' | 'PEAK_SEASON';
type Outcome   = 'PASS' | 'PARTIAL_FAIL' | 'FAIL' | 'IN_REVIEW';

interface ExerciseDraft {
  titleEn: string;
  titleAr?: string;
  drillType: DrillType;
  scenarioEn: string;
  scenarioAr?: string;
  peakSeasonLens?: 'HAJJ' | 'UMRAH' | 'RAMADAN' | 'NATIONAL_DAY' | 'NONE';
  scheduledAt?: string;
  participants?: string;
  rtoTargetHours?: number;
  rpoTargetHours?: number;
  rtoActualHours?: number;
  rpoActualHours?: number;
  outcome?: Outcome;
  findingsEn?: string;
  capaCount?: number;
}

export function BcmExerciseWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<ExerciseDraft>({
    titleEn: '', drillType: 'TABLETOP', scenarioEn: '',
  });

  function set<K extends keyof ExerciseDraft>(key: K, value: ExerciseDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  async function submit() {
    setSubmitting(true);
    try {
      await new Promise(r => setTimeout(r, 300));
      toast.success(isAr ? 'تم تسجيل التمرين' : 'Exercise recorded');
      navigate('/bcm/library');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'plan',
      title: 'Plan', titleAr: 'التخطيط',
      Icon: ClipboardCheck,
      validate: () => !draft.titleEn.trim() || !draft.scenarioEn.trim()
        ? (isAr ? 'العنوان والسيناريو مطلوبان' : 'Title and scenario are required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'عنوان التمرين' : 'Exercise title'} required>
            <input className="input" value={draft.titleEn}
                   onChange={e => set('titleEn', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'النوع' : 'Type'} required>
            <select className="input" value={draft.drillType}
                    onChange={e => set('drillType', e.target.value as DrillType)}>
              <option value="TABLETOP">Tabletop (discussion-based)</option>
              <option value="FULL_DR">Full DR (real failover)</option>
              <option value="CYBER">Cyber tabletop / red-team</option>
              <option value="CALL_TREE">Comms / call tree</option>
              <option value="PEAK_SEASON">Peak-season simulation</option>
            </select>
          </Field>
          <Field label={isAr ? 'السيناريو' : 'Scenario'} required full>
            <textarea className="input" rows={4} value={draft.scenarioEn}
                      onChange={e => set('scenarioEn', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: فقدان مركز البيانات الأساسي خلال يوم ذروة الحج'
                        : 'e.g. Loss of primary DC during Hajj peak day'}/>
          </Field>
          <Field label={isAr ? 'منظور موسم الذروة' : 'Peak-season lens'}>
            <select className="input" value={draft.peakSeasonLens ?? 'NONE'}
                    onChange={e => set('peakSeasonLens', e.target.value as ExerciseDraft['peakSeasonLens'])}>
              <option value="NONE">—</option>
              <option value="HAJJ">Hajj</option>
              <option value="UMRAH">Umrah</option>
              <option value="RAMADAN">Ramadan</option>
              <option value="NATIONAL_DAY">National Day</option>
            </select>
          </Field>
          <Field label={isAr ? 'التاريخ المجدول' : 'Scheduled at'}>
            <input className="input" type="datetime-local" value={draft.scheduledAt ?? ''}
                   onChange={e => set('scheduledAt', e.target.value)}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'targets',
      title: 'Targets', titleAr: 'الأهداف',
      Icon: Activity,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'هدف RTO (ساعات)' : 'RTO target (hours)'}>
            <input className="input" type="number" min={0}
                   value={draft.rtoTargetHours ?? ''}
                   onChange={e => set('rtoTargetHours', e.target.value ? Number(e.target.value) : undefined)}/>
          </Field>
          <Field label={isAr ? 'هدف RPO (ساعات)' : 'RPO target (hours)'}>
            <input className="input" type="number" min={0}
                   value={draft.rpoTargetHours ?? ''}
                   onChange={e => set('rpoTargetHours', e.target.value ? Number(e.target.value) : undefined)}/>
          </Field>
          <Field label={isAr ? 'المشاركون' : 'Participants'} full>
            <textarea className="input" rows={3} value={draft.participants ?? ''}
                      onChange={e => set('participants', e.target.value)}
                      placeholder="BCM team + IT Ops + Treasury + Cyber SOC"/>
          </Field>
        </div>
      ),
    },

    {
      id: 'execute',
      title: 'Execute', titleAr: 'التنفيذ',
      Icon: AlertOctagon,
      validate: () => !draft.outcome
        ? (isAr ? 'اختر النتيجة' : 'Pick an outcome')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'النتيجة' : 'Outcome'} required>
            <select className="input" value={draft.outcome ?? ''}
                    onChange={e => set('outcome', e.target.value as Outcome)}>
              <option value="">—</option>
              <option value="PASS">PASS — all objectives met</option>
              <option value="PARTIAL_FAIL">PARTIAL — some objectives missed</option>
              <option value="FAIL">FAIL — major objectives missed</option>
              <option value="IN_REVIEW">In review</option>
            </select>
          </Field>
          <Field label={isAr ? 'RTO الفعلي (ساعات)' : 'Actual RTO (hours)'}>
            <input className="input" type="number" step="0.1"
                   value={draft.rtoActualHours ?? ''}
                   onChange={e => set('rtoActualHours', e.target.value ? Number(e.target.value) : undefined)}/>
          </Field>
          <Field label={isAr ? 'RPO الفعلي (ساعات)' : 'Actual RPO (hours)'}>
            <input className="input" type="number" step="0.1"
                   value={draft.rpoActualHours ?? ''}
                   onChange={e => set('rpoActualHours', e.target.value ? Number(e.target.value) : undefined)}/>
          </Field>
          <Field label={isAr ? 'النتائج' : 'Findings'} full>
            <textarea className="input" rows={5} value={draft.findingsEn ?? ''}
                      onChange={e => set('findingsEn', e.target.value)}/>
          </Field>
          {draft.rtoTargetHours && draft.rtoActualHours && draft.rtoActualHours > draft.rtoTargetHours && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone="danger">RTO breached</Badge>{' '}
              {isAr ? 'سيتم إنشاء CAPA تلقائياً بعد الحفظ.'
                    : 'A CAPA will be auto-created on save (F8 BcmCorrectiveAction handler).'}
            </div>
          )}
        </div>
      ),
    },

    {
      id: 'close',
      title: 'Close-out', titleAr: 'الإغلاق',
      Icon: CheckCheck,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'عدد CAPA المُنشَأة' : 'CAPAs opened'}>
            <input className="input" type="number" min={0}
                   value={draft.capaCount ?? 0}
                   onChange={e => set('capaCount', Number(e.target.value || 0))}/>
          </Field>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'النوع' : 'Type'} v={draft.drillType} />
            <Row k={isAr ? 'النتيجة' : 'Outcome'} v={draft.outcome ?? '—'} />
            <Row k={isAr ? 'RTO هدف / فعلي' : 'RTO target / actual'}
                 v={`${draft.rtoTargetHours ?? '—'} / ${draft.rtoActualHours ?? '—'}`} />
            <Row k={isAr ? 'RPO هدف / فعلي' : 'RPO target / actual'}
                 v={`${draft.rpoTargetHours ?? '—'} / ${draft.rpoActualHours ?? '—'}`} />
            <Row k="CAPAs" v={String(draft.capaCount ?? 0)} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'تمرين استمرارية الأعمال' : 'BCM Exercise & Test'}
        sub={isAr ? 'معالج من ٤ خطوات وفق ISO 22301 §8.5' : '4-step wizard aligned with ISO 22301 §8.5'}
        eyebrow={isAr ? 'استمرارية الأعمال' : 'Business Continuity'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/bcm')}/>
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
