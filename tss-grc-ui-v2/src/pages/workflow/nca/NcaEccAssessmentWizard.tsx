import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardCheck, FileCheck2, ShieldCheck, Stamp } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';
import { submitWizard } from '../../../lib/submitWizard';

/**
 * NCA §7.5 #3 — NCA ECC Self-Assessment wizard.
 *
 * <p>4 steps:
 *   1. Scope — applicability + framework version (ECC-1:2018 vs ECC-2:2024)
 *   2. Maturity per domain (1..5)
 *   3. Evidence + gap notes
 *   4. CISO sign-off + submit (creates nca_ecc.filings row)
 */
const ECC_DOMAINS = [
  { code: '1', en: 'Cybersecurity Governance', ar: 'حوكمة الأمن السيبراني' },
  { code: '2', en: 'Cybersecurity Defence',    ar: 'الدفاع السيبراني' },
  { code: '3', en: 'Cybersecurity Resilience', ar: 'الصمود السيبراني' },
  { code: '4', en: 'Third-Party + Cloud',      ar: 'الطرف الثالث والحوسبة السحابية' },
  { code: '5', en: 'Industrial Control Systems', ar: 'أنظمة التحكم الصناعية' },
];

interface NcaDraft {
  fiscalYear: number;
  frameworkVersion: 'ECC-1:2018' | 'ECC-2:2024';
  applicability: 'FULL' | 'PARTIAL' | 'NOT_APPLICABLE';
  domainMaturity: Record<string, number>;
  evidenceCount: number;
  gapNotesEn?: string;
  cisoApproverName?: string;
}

export function NcaEccAssessmentWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<NcaDraft>({
    fiscalYear: new Date().getFullYear(),
    frameworkVersion: 'ECC-2:2024',
    applicability: 'FULL',
    domainMaturity: {},
    evidenceCount: 0,
  });

  function set<K extends keyof NcaDraft>(key: K, value: NcaDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }
  function setMaturity(code: string, level: number) {
    setDraft(d => ({ ...d, domainMaturity: { ...d.domainMaturity, [code]: level } }));
  }

  const avgMaturity = (() => {
    const vals = Object.values(draft.domainMaturity);
    if (vals.length === 0) return 0;
    return Math.round((vals.reduce((a, b) => a + b, 0) / vals.length) * 10) / 10;
  })();

  async function submit() {
    setSubmitting(true);
    try {
      const created = await submitWizard({
        workflowId: 'nca-ecc-assessment',
        workflowModule: 'NCA',
        titleEn: `NCA ECC ${draft.frameworkVersion} — FY${draft.fiscalYear}`,
        regulatoryAnchor: `NCA ${draft.frameworkVersion}`,
        summaryEn: `${draft.applicability} · avg maturity ${avgMaturity}/5`
                 + ` · ${draft.evidenceCount} evidence items`
                 + (draft.cisoApproverName ? ` · CISO=${draft.cisoApproverName}` : ''),
        status: draft.cisoApproverName ? 'APPROVED' : 'SUBMITTED',
        draft,
      });
      toast.success(isAr
        ? `تم تسجيل التقييم الذاتي للهيئة (#${created.id})`
        : `NCA ECC self-assessment #${created.id} recorded`);
      navigate('/compliance');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'scope',
      title: 'Scope', titleAr: 'النطاق',
      Icon: ShieldCheck,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'السنة المالية' : 'Fiscal year'}>
            <input className="input" type="number" min={2020} max={2030}
                   value={draft.fiscalYear}
                   onChange={e => set('fiscalYear', Number(e.target.value))}/>
          </Field>
          <Field label={isAr ? 'إصدار الإطار' : 'Framework version'}>
            <select className="input" value={draft.frameworkVersion}
                    onChange={e => set('frameworkVersion', e.target.value as NcaDraft['frameworkVersion'])}>
              <option value="ECC-1:2018">ECC-1 (2018)</option>
              <option value="ECC-2:2024">ECC-2 (2024) — current</option>
            </select>
          </Field>
          <Field label={isAr ? 'القابلية' : 'Applicability'}>
            <select className="input" value={draft.applicability}
                    onChange={e => set('applicability', e.target.value as NcaDraft['applicability'])}>
              <option value="FULL">Full — all 5 domains in scope</option>
              <option value="PARTIAL">Partial — some domains scoped out</option>
              <option value="NOT_APPLICABLE">Not applicable</option>
            </select>
          </Field>
        </div>
      ),
    },

    {
      id: 'maturity',
      title: 'Maturity', titleAr: 'النضج',
      Icon: ClipboardCheck,
      validate: () => Object.keys(draft.domainMaturity).length < ECC_DOMAINS.length
        ? (isAr ? 'قَيِّم كل الميادين' : 'Rate every domain')
        : null,
      content: () => (
        <div className="wf-form-grid">
          {ECC_DOMAINS.map(d => (
            <Field key={d.code} label={isAr ? `${d.code}. ${d.ar}` : `${d.code}. ${d.en}`} required>
              <div className="wf-score-row">
                {[1,2,3,4,5].map(n => (
                  <button key={n} type="button"
                          className={'wf-score-chip' + (draft.domainMaturity[d.code] === n ? ' is-active' : '')}
                          onClick={() => setMaturity(d.code, n)}>
                    <span className="wf-score-num">{n}</span>
                    <span className="wf-score-label">
                      {n===1?'Initial':n===2?'Repeatable':n===3?'Defined':n===4?'Managed':'Optimised'}
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
      id: 'evidence',
      title: 'Evidence + gaps', titleAr: 'الأدلة والفجوات',
      Icon: FileCheck2,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'عدد الأدلة المرفقة' : 'Evidence items attached'}>
            <input className="input" type="number" min={0}
                   value={draft.evidenceCount}
                   onChange={e => set('evidenceCount', Number(e.target.value || 0))}/>
          </Field>
          <Field label={isAr ? 'ملاحظات الفجوات' : 'Gap notes / remediation plan'} full>
            <textarea className="input" rows={6} value={draft.gapNotesEn ?? ''}
                      onChange={e => set('gapNotesEn', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: لا يوجد سجل أصول OT شامل في الميدان ٥؛ سيتم إنشاؤه بنهاية الربع.'
                        : 'e.g. Domain 5 lacks comprehensive OT asset inventory; remediation owner + target date.'}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'signoff',
      title: 'CISO sign-off', titleAr: 'اعتماد CISO',
      Icon: Stamp,
      validate: () => !draft.cisoApproverName?.trim()
        ? (isAr ? 'يلزم اعتماد CISO' : 'CISO sign-off required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'اسم CISO' : 'CISO name'} required>
            <input className="input" value={draft.cisoApproverName ?? ''}
                   onChange={e => set('cisoApproverName', e.target.value)}/>
          </Field>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'السنة' : 'Year'} v={String(draft.fiscalYear)} />
            <Row k={isAr ? 'الإصدار' : 'Framework'} v={draft.frameworkVersion} />
            <Row k={isAr ? 'متوسط النضج' : 'Avg maturity'} v={`${avgMaturity}/5`} />
            <Row k={isAr ? 'الأدلة' : 'Evidence'} v={String(draft.evidenceCount)} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'تقييم ذاتي للهيئة الوطنية للأمن السيبراني' : 'NCA ECC self-assessment'}
        sub={isAr ? 'معالج من ٤ خطوات — ٥ ميادين × ٥ مستويات نضج'
                  : '4-step wizard — 5 domains × 5 maturity levels'}
        eyebrow={isAr ? 'الأمن السيبراني / NCA' : 'NCA / Cybersecurity'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/compliance')}
        submitLabel={isAr ? 'تسجيل التقييم' : 'Record assessment'}/>
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
