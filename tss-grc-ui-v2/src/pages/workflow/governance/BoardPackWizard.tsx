import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BrainCircuit, Building2, ClipboardCheck, Send, Stamp } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { tlabel } from '../../../lib/labels';
import { useToast } from '../../../lib/toast';
import { submitWizard } from '../../../lib/submitWizard';

/**
 * §7.6 Cross-module — Board Pack generator wizard.
 *
 * <p>5 steps:
 *   1. Pack profile — quarter + audience + cover letter
 *   2. Compose sections — pick from a 9-section default
 *   3. AI draft — V116 executive summary + key risks lens (human-reviewed)
 *   4. Approval routing — CRO / CISO / DPO / CEO
 *   5. Distribute — secure portal + watermarks
 */
type Section =
  | 'EXEC_SUMMARY'
  | 'TOP_RISKS'
  | 'INCIDENT_DIGEST'
  | 'COMPLIANCE_POSTURE'
  | 'BCM_READINESS'
  | 'PRIVACY_KPIS'
  | 'SUPPLIER_HEATMAP'
  | 'OPEN_FINDINGS'
  | 'REGULATORY_CALENDAR';

const SECTION_LABELS: Record<Section, { en: string; ar: string }> = {
  EXEC_SUMMARY:        { en: 'Executive summary',        ar: 'الملخص التنفيذي' },
  TOP_RISKS:           { en: 'Top 10 enterprise risks',  ar: 'أعلى ١٠ مخاطر' },
  INCIDENT_DIGEST:     { en: 'Incident digest (cyber + privacy + service)', ar: 'موجز الحوادث' },
  COMPLIANCE_POSTURE:  { en: 'Compliance posture (NCA + PDPL + SAMA)', ar: 'الموقف التنظيمي' },
  BCM_READINESS:       { en: 'Continuity readiness score',  ar: 'مؤشر جاهزية الاستمرارية' },
  PRIVACY_KPIS:        { en: 'Privacy KPIs (DSR + breach SLAs)', ar: 'مؤشرات الخصوصية' },
  SUPPLIER_HEATMAP:    { en: 'Supplier risk heatmap',    ar: 'خريطة مخاطر الموردين' },
  OPEN_FINDINGS:       { en: 'Open audit findings + CAPA aging', ar: 'الملاحظات المفتوحة' },
  REGULATORY_CALENDAR: { en: 'Regulatory calendar (next 90 days)', ar: 'التقويم التنظيمي' },
};

type Audience = 'BOARD' | 'AUDIT_COMMITTEE' | 'RISK_COMMITTEE' | 'EXEC_TEAM';

interface BoardPackDraft {
  quarter: string;
  audience: Audience;
  sections: Section[];
  coverLetter?: string;
  aiSummaryAccepted: boolean;
  aiSummaryEdits?: string;
  approverCro: boolean;
  approverCiso: boolean;
  approverDpo: boolean;
  approverCeo: boolean;
  distributionList: string;
  watermark: boolean;
  notes?: string;
}

const DEFAULT_SECTIONS: Section[] = [
  'EXEC_SUMMARY', 'TOP_RISKS', 'COMPLIANCE_POSTURE', 'INCIDENT_DIGEST',
  'BCM_READINESS', 'PRIVACY_KPIS', 'SUPPLIER_HEATMAP', 'OPEN_FINDINGS',
  'REGULATORY_CALENDAR',
];

export function BoardPackWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<BoardPackDraft>({
    quarter: '',
    audience: 'BOARD',
    sections: DEFAULT_SECTIONS,
    aiSummaryAccepted: false,
    approverCro: false,
    approverCiso: false,
    approverDpo: false,
    approverCeo: false,
    distributionList: '',
    watermark: true,
  });

  function set<K extends keyof BoardPackDraft>(key: K, value: BoardPackDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  function toggleSection(s: Section) {
    setDraft(d => ({
      ...d,
      sections: d.sections.includes(s)
        ? d.sections.filter(x => x !== s)
        : [...d.sections, s],
    }));
  }

  async function submit() {
    setSubmitting(true);
    try {
      const approvers = [
        draft.approverCro && 'CRO',
        draft.approverCiso && 'CISO',
        draft.approverDpo && 'DPO',
        draft.approverCeo && 'CEO',
      ].filter(Boolean).join('+');
      const created = await submitWizard({
        workflowId: 'governance-board-pack',
        workflowModule: 'GOVERNANCE',
        titleEn: `Board pack ${draft.quarter} (${draft.audience})`,
        summaryEn: `${draft.sections.length} sections · approvals: ${approvers}`
                 + (draft.watermark ? ' · watermarked' : ''),
        status: draft.approverCro && draft.approverCiso && draft.approverCeo
          ? 'APPROVED' : 'SUBMITTED',
        draft,
      });
      toast.success(isAr
        ? `تم توزيع ملف المجلس (#${created.id})`
        : `Board pack #${created.id} distributed`);
      navigate('/board-pack');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'profile',
      title: '1. Pack profile', titleAr: '١. ملف العرض',
      Icon: Building2,
      validate: () => !draft.quarter.trim()
        ? (isAr ? 'تحديد الربع مطلوب' : 'Quarter required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الربع' : 'Quarter'} required>
            <input className="input" value={draft.quarter}
                   onChange={e => set('quarter', e.target.value)}
                   placeholder="Q2 2026"/>
          </Field>
          <Field label={isAr ? 'الجمهور' : 'Audience'}>
            <select className="input" value={draft.audience}
                    onChange={e => set('audience', e.target.value as Audience)}>
              <option value="BOARD">Board of Directors</option>
              <option value="AUDIT_COMMITTEE">Audit Committee</option>
              <option value="RISK_COMMITTEE">Risk Committee</option>
              <option value="EXEC_TEAM">Executive Team</option>
            </select>
          </Field>
          <Field label={isAr ? 'خطاب التقديم' : 'Cover letter'} full>
            <textarea className="input" rows={4} value={draft.coverLetter ?? ''}
                      onChange={e => set('coverLetter', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: نشكر المجلس على الوقت المخصص لاستعراض هذا الربع…'
                        : 'e.g. We thank the Board for the time allocated to this quarter\'s review…'}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'sections',
      title: '2. Compose sections', titleAr: '٢. اختيار الأقسام',
      Icon: ClipboardCheck,
      validate: () => draft.sections.length === 0
        ? (isAr ? 'اختر قسماً واحداً على الأقل' : 'Pick at least one section')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الأقسام المضمنة' : 'Sections to include'} full>
            <div className="wf-toggle-grid">
              {(Object.keys(SECTION_LABELS) as Section[]).map(s => (
                <Toggle key={s}
                        checked={draft.sections.includes(s)}
                        onChange={() => toggleSection(s)}
                        label={tlabel(SECTION_LABELS, s, isAr)}/>
              ))}
            </div>
          </Field>
          <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
            <Badge tone="info">
              {draft.sections.length} {isAr ? 'قسماً' : 'sections'}
            </Badge>{' '}
            {isAr
              ? '— تُحدَّث الأقسام تلقائياً من بيانات المنصة عند التوزيع.'
              : '— sections auto-populate from platform data at distribution time.'}
          </div>
        </div>
      ),
    },

    {
      id: 'ai',
      title: '3. AI draft + review', titleAr: '٣. مسودة الذكاء الاصطناعي',
      Icon: BrainCircuit,
      validate: () => !draft.aiSummaryAccepted
        ? (isAr ? 'يلزم مراجعة المسودة قبل المتابعة'
                : 'AI draft must be reviewed before continuing')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
            <Badge tone="info">{isAr ? 'مسودة بالذكاء الاصطناعي (V116)' : 'AI draft (V116)'}</Badge>{' '}
            {isAr
              ? 'يولّد المحرّك ملخصاً تنفيذياً + قراءة أعلى ٥ مخاطر — تعديل بشري إلزامي.'
              : 'The engine drafts an executive summary + top-5 risk lens — human edits mandatory.'}
          </div>
          <Field label={isAr ? 'تعديلات يدوية على المسودة' : 'Manual edits to the AI summary'} full>
            <textarea className="input" rows={8} value={draft.aiSummaryEdits ?? ''}
                      onChange={e => set('aiSummaryEdits', e.target.value)}
                      placeholder={isAr
                        ? 'ضع هنا أي تعديل، أو دع المسودة الأصلية تُستخدم كما هي…'
                        : 'Edit the AI draft here, or leave blank to use the original draft…'}/>
          </Field>
          <Toggle checked={draft.aiSummaryAccepted}
                  onChange={v => set('aiSummaryAccepted', v)}
                  label={isAr ? 'تم استعراض المسودة وقبولها' : 'AI draft reviewed and accepted'}/>
        </div>
      ),
    },

    {
      id: 'approvals',
      title: '4. Approval routing', titleAr: '٤. الاعتمادات',
      Icon: Stamp,
      validate: () => !(draft.approverCro && draft.approverCiso && draft.approverCeo)
        ? (isAr ? 'يلزم اعتماد CRO + CISO + CEO قبل التوزيع'
                : 'CRO + CISO + CEO approvals required before distribution')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Toggle checked={draft.approverCro}
                  onChange={v => set('approverCro', v)}
                  label={isAr ? 'اعتماد CRO' : 'CRO approved'}/>
          <Toggle checked={draft.approverCiso}
                  onChange={v => set('approverCiso', v)}
                  label={isAr ? 'اعتماد CISO' : 'CISO approved'}/>
          <Toggle checked={draft.approverDpo}
                  onChange={v => set('approverDpo', v)}
                  label={isAr ? 'اعتماد DPO (إذا تضمن الملف بيانات شخصية)'
                              : 'DPO approved (if pack includes personal-data KPIs)'}/>
          <Toggle checked={draft.approverCeo}
                  onChange={v => set('approverCeo', v)}
                  label={isAr ? 'اعتماد CEO' : 'CEO approved'}/>
        </div>
      ),
    },

    {
      id: 'distribute',
      title: '5. Distribute', titleAr: '٥. التوزيع',
      Icon: Send,
      validate: () => !draft.distributionList.trim()
        ? (isAr ? 'قائمة التوزيع مطلوبة' : 'Distribution list required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'قائمة التوزيع' : 'Distribution list'} full required>
            <textarea className="input" rows={4} value={draft.distributionList}
                      onChange={e => set('distributionList', e.target.value)}
                      placeholder={isAr
                        ? 'أدوار أو حسابات: مجلس الإدارة، لجنة التدقيق، الإدارة التنفيذية…'
                        : 'Roles or accounts: Board members, Audit Committee, Executive Team…'}/>
          </Field>
          <Toggle checked={draft.watermark}
                  onChange={v => set('watermark', v)}
                  label={isAr ? 'علامة مائية لكل مستلم (سرّي وتتبع التسريب)'
                              : 'Per-recipient watermark (confidential, leak-traceable)'}/>
          <Field label={isAr ? 'ملاحظات التوزيع' : 'Distribution notes'} full>
            <textarea className="input" rows={2} value={draft.notes ?? ''}
                      onChange={e => set('notes', e.target.value)}/>
          </Field>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'الربع' : 'Quarter'} v={draft.quarter} />
            <Row k={isAr ? 'الجمهور' : 'Audience'} v={draft.audience} />
            <Row k={isAr ? 'عدد الأقسام' : 'Sections'} v={String(draft.sections.length)} />
            <Row k={isAr ? 'العلامة المائية' : 'Watermark'}
                 v={draft.watermark ? (isAr ? 'مفعّلة' : 'On') : (isAr ? 'متوقفة' : 'Off')} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'مولِّد ملف المجلس' : 'Board Pack Generator'}
        sub={isAr
          ? 'معالج من ٥ خطوات — ملف المجلس الفصلي مع تكوين، مسودة ذكاء اصطناعي، واعتماد متسلسل'
          : '5-step wizard for the quarterly Board GRC pack — compose, AI-draft, approve, distribute'}
        eyebrow={isAr ? 'الحوكمة' : 'Governance'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/board-pack')}
        submitLabel={isAr ? 'توزيع الملف' : 'Distribute pack'}/>
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
