import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertOctagon, ClipboardCheck, FileText, Siren, Users } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';

/**
 * PDPL §7.4 #8 — Personal Data Breach notification wizard.
 *
 * <p>PDPL Article 23 mandates SDAIA notification within 72 hours of
 * becoming aware of a personal-data breach. This wizard walks the DPO
 * through:
 *   1. Detect — when, who, what
 *   2. Severity — categories, volumes, special-category flag
 *   3. Scope — affected data subjects, data categories
 *   4. DPO triage decision (notify-SDAIA / notify-subjects / contain-only)
 *   5. Drafted notification (SDAIA + subjects)
 *   6. Approval + submission
 *
 * <p>Status mirrors the CrisisEvent pdpl_72h fields from V115; on submit
 * the wizard would create or update the crisis event + filing.
 */
type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

interface BreachDraft {
  titleEn: string;
  detectionTime: string;          // ISO datetime-local string
  detectedByName: string;

  severity: Severity;
  affectedSubjectCount?: number;
  specialCategory: boolean;
  dataCategories: string;         // comma-separated tags

  affectedScopeEn: string;
  containmentSummaryEn: string;

  decision?: 'NOTIFY_SDAIA' | 'NOTIFY_SUBJECTS' | 'CONTAIN_ONLY';
  decisionRationaleEn?: string;

  sdaiaDraftEn?: string;
  subjectsDraftEn?: string;

  approvedByDpo: boolean;
  approvedByCounsel: boolean;
}

const NOW = () => new Date().toISOString().slice(0, 16);

export function BreachNotificationWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<BreachDraft>({
    titleEn: '', detectionTime: NOW(), detectedByName: '',
    severity: 'MEDIUM', specialCategory: false,
    dataCategories: '', affectedScopeEn: '', containmentSummaryEn: '',
    approvedByDpo: false, approvedByCounsel: false,
  });

  function set<K extends keyof BreachDraft>(key: K, value: BreachDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  // 72-hour countdown — derived from detectionTime.
  const detectionDate = draft.detectionTime ? new Date(draft.detectionTime) : null;
  const deadline = detectionDate
    ? new Date(detectionDate.getTime() + 72 * 60 * 60 * 1000)
    : null;
  const hoursLeft = deadline
    ? Math.max(0, Math.round((deadline.getTime() - Date.now()) / 36e5))
    : null;
  const breached = hoursLeft !== null && hoursLeft <= 0;

  async function submit() {
    setSubmitting(true);
    try {
      // Real backend wiring: POST /api/v1/bcm/crisis-events with
      // personalDataAffected=true, then PATCH the pdpl_notification_drafted_at
      // field. Stubbed here so the flow demos without requiring a fresh
      // crisis event each time.
      await new Promise(r => setTimeout(r, 400));
      toast.success(isAr ? 'تم تسجيل الإشعار' : 'Breach notification recorded');
      navigate('/bcm/crisis-room');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'detect',
      title: 'Detect', titleAr: 'الاكتشاف',
      sub: 'When, who, what', subAr: 'متى ومن وماذا',
      Icon: Siren,
      validate: () => !draft.titleEn.trim() || !draft.detectionTime || !draft.detectedByName.trim()
        ? (isAr ? 'كل الحقول مطلوبة' : 'All fields are required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'عنوان الحدث' : 'Event title'} required full>
            <input className="input" value={draft.titleEn}
                   onChange={e => set('titleEn', e.target.value)}
                   placeholder="e.g. Exposed S3 bucket containing customer email list" />
          </Field>
          <Field label={isAr ? 'وقت الاكتشاف' : 'Detection time'} required>
            <input className="input" type="datetime-local"
                   value={draft.detectionTime}
                   onChange={e => set('detectionTime', e.target.value)} />
          </Field>
          <Field label={isAr ? 'اكتشف بواسطة' : 'Detected by'} required>
            <input className="input" value={draft.detectedByName}
                   onChange={e => set('detectedByName', e.target.value)} />
          </Field>
          {deadline && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone={breached ? 'danger' : (hoursLeft! < 24 ? 'warning' : 'info')}>
                PDPL Art. 23
              </Badge>{' '}
              {breached
                ? (isAr ? 'تم تجاوز نافذة ٧٢ ساعة.' : 'The 72-hour window has elapsed.')
                : (isAr ? `يتبقى نحو ${hoursLeft} ساعة على الموعد النهائي ${deadline.toLocaleString()}.`
                        : `~${hoursLeft}h left until ${deadline.toLocaleString()} deadline.`)}
            </div>
          )}
        </div>
      ),
    },

    {
      id: 'severity',
      title: 'Severity & scope', titleAr: 'الشدة والنطاق',
      sub: 'Categorise', subAr: 'تصنيف',
      Icon: AlertOctagon,
      validate: () => !draft.severity
        ? (isAr ? 'اختر الشدة' : 'Pick a severity')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الشدة' : 'Severity'} required>
            <select className="input" value={draft.severity}
                    onChange={e => set('severity', e.target.value as Severity)}>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </Field>
          <Field label={isAr ? 'عدد المتأثرين (تقدير)' : 'Affected subjects (estimate)'}>
            <input className="input" type="number" min={0}
                   value={draft.affectedSubjectCount ?? ''}
                   onChange={e => set('affectedSubjectCount', e.target.value ? Number(e.target.value) : undefined)} />
          </Field>
          <Field label={isAr ? 'فئة خاصة (صحية / مالية / بيومترية)' : 'Special-category data'} full>
            <label className="wf-toggle">
              <input type="checkbox" checked={draft.specialCategory}
                     onChange={e => set('specialCategory', e.target.checked)} />
              <span>{isAr ? 'يحتوي على بيانات حساسة' : 'Includes special-category data (health / biometric / financial)'}</span>
            </label>
          </Field>
          <Field label={isAr ? 'فئات البيانات (مفصولة بفاصلة)' : 'Data categories (comma-separated)'} full>
            <input className="input" value={draft.dataCategories}
                   onChange={e => set('dataCategories', e.target.value)}
                   placeholder="EMAIL, PHONE, NATIONAL_ID, FINANCIAL"/>
          </Field>
        </div>
      ),
    },

    {
      id: 'scope',
      title: 'Scope & containment', titleAr: 'النطاق والاحتواء',
      sub: 'What happened', subAr: 'ماذا حدث',
      Icon: FileText,
      validate: () => !draft.affectedScopeEn.trim()
        ? (isAr ? 'حدد النطاق' : 'Describe the scope')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'النطاق المتأثر' : 'Affected scope'} required full>
            <textarea className="input" rows={5} value={draft.affectedScopeEn}
                      onChange={e => set('affectedScopeEn', e.target.value)} />
          </Field>
          <Field label={isAr ? 'الاحتواء حتى الآن' : 'Containment so far'} full>
            <textarea className="input" rows={5} value={draft.containmentSummaryEn}
                      onChange={e => set('containmentSummaryEn', e.target.value)} />
          </Field>
        </div>
      ),
    },

    {
      id: 'triage',
      title: 'DPO triage', titleAr: 'قرار DPO',
      sub: 'Decision', subAr: 'القرار',
      Icon: Users,
      validate: () => !draft.decision
        ? (isAr ? 'اختر قراراً' : 'Pick a decision')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'القرار' : 'Decision'} required full>
            <select className="input"
                    value={draft.decision ?? ''}
                    onChange={e => set('decision', e.target.value as BreachDraft['decision'])}>
              <option value="">—</option>
              <option value="NOTIFY_SDAIA">Notify SDAIA only</option>
              <option value="NOTIFY_SUBJECTS">Notify SDAIA + affected data subjects</option>
              <option value="CONTAIN_ONLY">Contain — risk to subjects assessed as low</option>
            </select>
          </Field>
          <Field label={isAr ? 'سبب القرار' : 'Decision rationale'} full>
            <textarea className="input" rows={4}
                      value={draft.decisionRationaleEn ?? ''}
                      onChange={e => set('decisionRationaleEn', e.target.value)} />
          </Field>
        </div>
      ),
    },

    {
      id: 'draft',
      title: 'Draft notifications', titleAr: 'مسودات الإشعار',
      sub: 'SDAIA + subjects', subAr: 'الهيئة والمتأثرون',
      Icon: FileText,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'مسودة إشعار SDAIA' : 'SDAIA notification draft'} full>
            <textarea className="input" rows={7}
                      value={draft.sdaiaDraftEn ?? ''}
                      onChange={e => set('sdaiaDraftEn', e.target.value)}
                      placeholder={isAr
                        ? 'اشمل: الجدول الزمني، الفئات، عدد المتأثرين، الاحتواء، الخطوات التالية…'
                        : 'Include: timeline, categories, subject count, containment, next steps…'} />
          </Field>
          {(draft.decision === 'NOTIFY_SUBJECTS') && (
            <Field label={isAr ? 'مسودة إشعار المتأثرين' : 'Affected-subjects notification draft'} full>
              <textarea className="input" rows={6}
                        value={draft.subjectsDraftEn ?? ''}
                        onChange={e => set('subjectsDraftEn', e.target.value)} />
            </Field>
          )}
        </div>
      ),
    },

    {
      id: 'approve',
      title: 'Approve & submit', titleAr: 'الاعتماد والإرسال',
      sub: 'DPO + Legal', subAr: 'الحماية والقانون',
      Icon: ClipboardCheck,
      validate: () => !draft.approvedByDpo || !draft.approvedByCounsel
        ? (isAr ? 'يلزم اعتماد كلٍّ من DPO والمستشار القانوني' : 'Both DPO and Legal must approve')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'اعتماد مسؤول حماية البيانات' : 'DPO approval'} required full>
            <label className="wf-toggle">
              <input type="checkbox" checked={draft.approvedByDpo}
                     onChange={e => set('approvedByDpo', e.target.checked)} />
              <span>{isAr ? 'مُعتمد من DPO' : 'Approved by DPO'}</span>
            </label>
          </Field>
          <Field label={isAr ? 'اعتماد قانوني' : 'Legal counsel approval'} required full>
            <label className="wf-toggle">
              <input type="checkbox" checked={draft.approvedByCounsel}
                     onChange={e => set('approvedByCounsel', e.target.checked)} />
              <span>{isAr ? 'مُعتمد من المستشار القانوني' : 'Approved by Legal'}</span>
            </label>
          </Field>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'الحدث' : 'Event'} v={draft.titleEn} />
            <Row k={isAr ? 'الشدة' : 'Severity'} v={draft.severity} />
            <Row k={isAr ? 'المتأثرون' : 'Subjects'} v={String(draft.affectedSubjectCount ?? '—')} />
            <Row k={isAr ? 'القرار' : 'Decision'} v={draft.decision ?? '—'} />
            <Row k={isAr ? 'الموعد النهائي' : '72h deadline'}
                 v={deadline ? deadline.toLocaleString() : '—'} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'إشعار خرق بيانات شخصية' : 'Personal data breach notification'}
        sub={isAr ? 'معالج من ٦ خطوات وفق المادة ٢٣ من PDPL — نافذة ٧٢ ساعة'
                  : '6-step wizard aligned with PDPL Article 23 — 72-hour window'}
        eyebrow={isAr ? 'الخصوصية / PDPL' : 'Privacy / PDPL'}
      />
      <WizardShell
        steps={steps}
        active={step}
        onChange={setStep}
        rtl={isAr}
        submitting={submitting}
        onSubmit={submit}
        onCancel={() => navigate('/bcm/crisis-room')}
        submitLabel={isAr ? 'إنشاء الإشعار' : 'Create notification'}
      />
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
