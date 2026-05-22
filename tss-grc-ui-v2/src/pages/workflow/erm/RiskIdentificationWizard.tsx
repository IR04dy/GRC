import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle, ClipboardCheck, FileText, Settings2, Sparkles } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';
import { riskApi, ApiError } from '../../../lib/api';
import { ai } from '../../../lib/ai';

/**
 * Saudi-GRC §7.3 #3 + #4 — Risk Identification & Assessment wizard.
 *
 * <p>5 steps:
 *   1. Scope — title, category, owner, framework anchor
 *   2. Causes & consequences (with AI draft assist)
 *   3. Inherent scoring (likelihood × impact 1..5)
 *   4. Controls & residual scoring
 *   5. Review & submit
 *
 * <p>Persists to {@code POST /api/v1/risk/risks} on submit. The §27 AI
 * "risk description" generator is wired into step 2.
 */
interface RiskDraft {
  titleEn: string;
  titleAr?: string;
  categoryCode?: string;
  ownerName?: string;
  riskFrameworkClause?: string;

  causesEn: string;
  consequencesEn: string;

  inherentLikelihood?: number;
  inherentImpact?: number;

  controlsEn: string;
  residualLikelihood?: number;
  residualImpact?: number;
}

/** 1-5 likelihood/impact scale. Bilingual so Saudi users see Arabic
 *  risk terminology in the wizard instead of English placeholders. */
const SCORE_LABELS_EN = ['', 'Very low', 'Low', 'Moderate', 'High', 'Very high'];
const SCORE_LABELS_AR = ['', 'منخفضة جدًا', 'منخفضة', 'متوسطة', 'عالية', 'عالية جدًا'];

export function RiskIdentificationWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [aiBusy, setAiBusy] = useState(false);
  const [draft, setDraft] = useState<RiskDraft>({
    titleEn: '', causesEn: '', consequencesEn: '', controlsEn: '',
  });

  function set<K extends keyof RiskDraft>(key: K, value: RiskDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  async function fillFromAi() {
    if (!draft.titleEn.trim()) {
      toast.warning(isAr ? 'أدخل عنوان الخطر أولاً' : 'Enter the risk title first');
      return;
    }
    setAiBusy(true);
    try {
      const s = await ai.riskDescription({
        titleEn: draft.titleEn,
        categoryCode: draft.categoryCode,
        inherentLikelihood: draft.inherentLikelihood,
        inherentImpact: draft.inherentImpact,
      });
      const out = JSON.parse(s.outputJson) as {
        causes?: string[]; consequences?: string[];
      };
      setDraft(d => ({
        ...d,
        causesEn:       (out.causes ?? []).map(c => '• ' + c).join('\n')
                          || d.causesEn,
        consequencesEn: (out.consequences ?? []).map(c => '• ' + c).join('\n')
                          || d.consequencesEn,
      }));
      toast.success(isAr ? 'تم ملء المسودة من الذكاء الاصطناعي' : 'Filled from AI draft');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'AI draft failed');
    } finally { setAiBusy(false); }
  }

  async function submit() {
    setSubmitting(true);
    try {
      await riskApi.create({
        titleEn:                 draft.titleEn,
        titleAr:                 draft.titleAr,
        descriptionEn:           [draft.causesEn, '', draft.consequencesEn].join('\n').trim(),
        inherentLikelihood:      draft.inherentLikelihood,
        inherentImpact:          draft.inherentImpact,
        residualLikelihood:      draft.residualLikelihood ?? draft.inherentLikelihood,
        residualImpact:          draft.residualImpact ?? draft.inherentImpact,
        riskFrameworkClause:     draft.riskFrameworkClause,
      } as Parameters<typeof riskApi.create>[0]);
      toast.success(isAr ? 'تم تسجيل الخطر' : 'Risk recorded');
      navigate('/risk');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تسجيل الخطر' : 'Failed to create risk'));
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'scope',
      title: 'Scope & ownership',
      titleAr: 'النطاق والمالك',
      sub: 'Title, category, owner, anchor',
      subAr: 'العنوان والفئة والمالك والإسناد',
      Icon: AlertTriangle,
      validate: () => !draft.titleEn.trim()
        ? (isAr ? 'العنوان مطلوب' : 'Risk title is required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'عنوان الخطر (إنجليزي)' : 'Risk title (English)'} required>
            <input className="input" value={draft.titleEn}
                   onChange={e => set('titleEn', e.target.value)}
                   placeholder="e.g. SAMA-reportable breach of customer PII via supplier compromise"/>
          </Field>
          <Field label={isAr ? 'عنوان الخطر (عربي)' : 'Risk title (Arabic)'}>
            <input className="input" value={draft.titleAr ?? ''}
                   onChange={e => set('titleAr', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'الفئة' : 'Category'}>
            <select className="input"
                    value={draft.categoryCode ?? ''}
                    onChange={e => set('categoryCode', e.target.value)}>
              <option value="">—</option>
              <option value="CYBER">Cyber / NCA</option>
              <option value="PRIVACY">Privacy / PDPL</option>
              <option value="OPERATIONAL">Operational</option>
              <option value="COMPLIANCE">Compliance</option>
              <option value="STRATEGIC">Strategic</option>
              <option value="FINANCIAL">Financial</option>
              <option value="REPUTATIONAL">Reputational</option>
              <option value="THIRD_PARTY">Third party</option>
            </select>
          </Field>
          <Field label={isAr ? 'المالك' : 'Owner'}>
            <input className="input" value={draft.ownerName ?? ''}
                   onChange={e => set('ownerName', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'الإسناد التنظيمي' : 'Framework anchor'}>
            <input className="input" value={draft.riskFrameworkClause ?? ''}
                   onChange={e => set('riskFrameworkClause', e.target.value)}
                   placeholder="e.g. NCA ECC §1-3, PDPL Art. 23"/>
          </Field>
        </div>
      ),
    },

    {
      id: 'causes',
      title: 'Causes & consequences',
      titleAr: 'الأسباب والنتائج',
      sub: 'AI-assisted draft',
      subAr: 'مسودة بمساعدة الذكاء الاصطناعي',
      Icon: FileText,
      validate: () => !draft.causesEn.trim() || !draft.consequencesEn.trim()
        ? (isAr ? 'كلا الحقلين مطلوبان' : 'Both causes and consequences are required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <div style={{ gridColumn: '1 / -1', display: 'flex', justifyContent: 'flex-end' }}>
            <Button variant="secondary" Icon={Sparkles} size="sm"
                    onClick={fillFromAi} disabled={aiBusy}>
              {aiBusy ? '…' : (isAr ? 'ملء بالذكاء الاصطناعي' : 'Draft with AI')}
            </Button>
          </div>
          <Field label={isAr ? 'الأسباب' : 'Causes'} required full>
            <textarea className="input" rows={6} value={draft.causesEn}
                      onChange={e => set('causesEn', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'النتائج المحتملة' : 'Consequences'} required full>
            <textarea className="input" rows={6} value={draft.consequencesEn}
                      onChange={e => set('consequencesEn', e.target.value)}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'inherent',
      title: 'Inherent score',
      titleAr: 'التقييم المتأصل',
      sub: 'Likelihood × impact, 1..5',
      subAr: 'الاحتمالية × الأثر، ١..٥',
      Icon: AlertTriangle,
      validate: () => !draft.inherentLikelihood || !draft.inherentImpact
        ? (isAr ? 'يلزم اختيار الاحتمالية والأثر' : 'Pick both likelihood and impact')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <ScoreField label={isAr ? 'الاحتمالية' : 'Likelihood'}
                      value={draft.inherentLikelihood}
                      onChange={v => set('inherentLikelihood', v)} isAr={isAr} />
          <ScoreField label={isAr ? 'الأثر' : 'Impact'}
                      value={draft.inherentImpact}
                      onChange={v => set('inherentImpact', v)} isAr={isAr} />
          {draft.inherentLikelihood && draft.inherentImpact && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <strong>{isAr ? 'النتيجة المتأصلة:' : 'Inherent score:'}</strong>{' '}
              {draft.inherentLikelihood * draft.inherentImpact}/25
            </div>
          )}
        </div>
      ),
    },

    {
      id: 'controls',
      title: 'Controls & residual',
      titleAr: 'الضوابط والمخاطر المتبقية',
      sub: 'After mitigation',
      subAr: 'بعد التخفيف',
      Icon: Settings2,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الضوابط الحالية والمخططة' : 'Existing & planned controls'} full>
            <textarea className="input" rows={5} value={draft.controlsEn}
                      onChange={e => set('controlsEn', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: مراجعة MFA لحسابات المسؤول، اختبار اختراق ربع سنوي…'
                        : 'e.g. enforce MFA on admin accounts, quarterly pentest…'}/>
          </Field>
          <ScoreField label={isAr ? 'الاحتمالية المتبقية' : 'Residual likelihood'}
                      value={draft.residualLikelihood}
                      onChange={v => set('residualLikelihood', v)} isAr={isAr} />
          <ScoreField label={isAr ? 'الأثر المتبقي' : 'Residual impact'}
                      value={draft.residualImpact}
                      onChange={v => set('residualImpact', v)} isAr={isAr} />
        </div>
      ),
    },

    {
      id: 'review',
      title: 'Review & submit',
      titleAr: 'المراجعة والإرسال',
      sub: 'Final check',
      subAr: 'التحقق النهائي',
      Icon: ClipboardCheck,
      content: () => (
        <div className="wf-review">
          <Row k={isAr ? 'العنوان' : 'Title'} v={draft.titleEn} />
          <Row k={isAr ? 'الفئة'   : 'Category'} v={draft.categoryCode ?? '—'} />
          <Row k={isAr ? 'المالك' : 'Owner'} v={draft.ownerName ?? '—'} />
          <Row k={isAr ? 'الإسناد' : 'Anchor'} v={draft.riskFrameworkClause ?? '—'} />
          <Row k={isAr ? 'المتأصل' : 'Inherent'}
               v={`${draft.inherentLikelihood ?? '?'} × ${draft.inherentImpact ?? '?'} = ${(draft.inherentLikelihood ?? 0) * (draft.inherentImpact ?? 0)}/25`} />
          <Row k={isAr ? 'المتبقي' : 'Residual'}
               v={`${draft.residualLikelihood ?? '?'} × ${draft.residualImpact ?? '?'} = ${(draft.residualLikelihood ?? 0) * (draft.residualImpact ?? 0)}/25`} />
          <div className="wf-summary" style={{ marginTop: 12 }}>
            <Badge tone="info">{isAr ? 'جاهز للحفظ' : 'Ready to save'}</Badge>
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'تحديد خطر جديد' : 'Identify a new risk'}
        sub={isAr ? 'معالج من ٥ خطوات وفق ISO 31000' : '5-step wizard aligned with ISO 31000'}
        eyebrow={isAr ? 'إدارة المخاطر المؤسسية' : 'Enterprise Risk Management'}
      />
      <WizardShell
        steps={steps}
        active={step}
        onChange={setStep}
        rtl={isAr}
        submitting={submitting}
        onSubmit={submit}
        onCancel={() => navigate('/risk')}
        onSaveDraft={() => { toast.info(isAr ? 'تم حفظ المسودة محلياً' : 'Draft kept in memory'); }}
      />
    </Shell>
  );
}

// ── Local helpers ─────────────────────────────────────────────────

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

function ScoreField({ label, value, onChange, isAr }: {
  label: string;
  value?: number;
  onChange: (v: number) => void;
  isAr: boolean;
}) {
  const labels = isAr ? SCORE_LABELS_AR : SCORE_LABELS_EN;
  return (
    <Field label={label}>
      <div className="wf-score-row">
        {[1,2,3,4,5].map(n => (
          <button key={n} type="button"
                  className={'wf-score-chip' + (value === n ? ' is-active' : '')}
                  onClick={() => onChange(n)}>
            <span className="wf-score-num">{n}</span>
            <span className="wf-score-label">{labels[n]}</span>
          </button>
        ))}
      </div>
    </Field>
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
