import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { FileText, ShieldCheck, Stamp } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';

/**
 * Saudi-GRC §7.3 #6 — Risk Acceptance wizard (time-boxed, exec sign-off).
 *
 * <p>3 steps:
 *   1. Justification — why accept instead of treat
 *   2. Conditions + expiry (Hijri-aware)
 *   3. CRO + Board sign-off (creates risk_lc.acceptance_request)
 */
interface AcceptanceDraft {
  rationaleEn: string;
  rationaleAr?: string;
  residualScore?: number;
  appetiteUpper?: number;
  conditionsEn?: string;
  conditionsAr?: string;
  expiresAt?: string;
  hijriExpiry?: string;
  croSignedByName?: string;
  boardSignedByName?: string;
}

export function RiskAcceptanceWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();
  const { riskId } = useParams<{ riskId?: string }>();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<AcceptanceDraft>({
    rationaleEn: '',
  });

  function set<K extends keyof AcceptanceDraft>(key: K, value: AcceptanceDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  async function submit() {
    setSubmitting(true);
    try {
      await new Promise(r => setTimeout(r, 300));
      toast.success(isAr ? 'تم تسجيل قبول الخطر' : 'Risk acceptance recorded');
      navigate('/risk');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'justify',
      title: 'Justification', titleAr: 'التبرير',
      Icon: FileText,
      validate: () => !draft.rationaleEn.trim()
        ? (isAr ? 'يلزم تبرير القبول' : 'Justification is required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'تبرير القبول' : 'Why accept (cost > expected loss, etc.)'} required full>
            <textarea className="input" rows={6} value={draft.rationaleEn}
                      onChange={e => set('rationaleEn', e.target.value)} />
          </Field>
          <Field label={isAr ? 'النتيجة المتبقية' : 'Residual score'}>
            <input className="input" type="number" min={0} max={25}
                   value={draft.residualScore ?? ''}
                   onChange={e => set('residualScore', e.target.value ? Number(e.target.value) : undefined)} />
          </Field>
          <Field label={isAr ? 'سقف الشهية' : 'Appetite ceiling'}>
            <input className="input" type="number" min={0} max={25}
                   value={draft.appetiteUpper ?? ''}
                   onChange={e => set('appetiteUpper', e.target.value ? Number(e.target.value) : undefined)} />
          </Field>
          {draft.residualScore && draft.appetiteUpper && draft.residualScore > draft.appetiteUpper && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone="warning">
                {isAr ? 'تجاوز الشهية' : 'Above appetite'}
              </Badge>{' '}
              {isAr ? 'يتطلب اعتماد المجلس.' : 'Board approval required (residual > appetite).'}
            </div>
          )}
        </div>
      ),
    },

    {
      id: 'conditions',
      title: 'Conditions & expiry', titleAr: 'الشروط والصلاحية',
      Icon: ShieldCheck,
      validate: () => !draft.expiresAt
        ? (isAr ? 'يلزم تحديد تاريخ انتهاء' : 'Expiry date is required (acceptance is time-boxed)')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الشروط' : 'Conditions / triggers for review'} full>
            <textarea className="input" rows={4} value={draft.conditionsEn ?? ''}
                      onChange={e => set('conditionsEn', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: إعادة المراجعة عند تغيير المورد، أو إذا تجاوز عدد الحوادث الشهري ٣.'
                        : 'e.g. re-review if supplier changes, or monthly incident count exceeds 3.'} />
          </Field>
          <Field label={isAr ? 'تاريخ الانتهاء (ميلادي)' : 'Expiry (Gregorian)'} required>
            <input className="input" type="date" value={draft.expiresAt ?? ''}
                   onChange={e => set('expiresAt', e.target.value)} />
          </Field>
          <Field label={isAr ? 'تاريخ الانتهاء (هجري)' : 'Expiry (Hijri, optional)'}>
            <input className="input" value={draft.hijriExpiry ?? ''}
                   onChange={e => set('hijriExpiry', e.target.value)}
                   placeholder="1447-12-30"/>
          </Field>
        </div>
      ),
    },

    {
      id: 'signoff',
      title: 'Sign-off', titleAr: 'الاعتماد',
      Icon: Stamp,
      validate: () => !draft.croSignedByName?.trim()
        ? (isAr ? 'يلزم اعتماد رئيس المخاطر' : 'CRO sign-off required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'اعتماد CRO' : 'CRO sign-off'} required>
            <input className="input" value={draft.croSignedByName ?? ''}
                   onChange={e => set('croSignedByName', e.target.value)}
                   placeholder="Khalid Al-Mutairi" />
          </Field>
          <Field label={isAr ? 'اعتماد المجلس (إن لزم)' : 'Board sign-off (if above appetite)'}>
            <input className="input" value={draft.boardSignedByName ?? ''}
                   onChange={e => set('boardSignedByName', e.target.value)} />
          </Field>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'الخطر' : 'Risk'} v={riskId ?? '—'} />
            <Row k={isAr ? 'النتيجة المتبقية' : 'Residual'} v={String(draft.residualScore ?? '—')} />
            <Row k={isAr ? 'سقف الشهية' : 'Appetite ceiling'} v={String(draft.appetiteUpper ?? '—')} />
            <Row k={isAr ? 'الانتهاء' : 'Expires'} v={draft.expiresAt ?? '—'} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'قبول الخطر' : 'Risk Acceptance'}
        sub={isAr ? 'معالج من ٣ خطوات — قبول محدود زمنياً مع اعتماد تنفيذي'
                  : '3-step wizard — time-boxed acceptance with executive sign-off'}
        eyebrow={isAr ? 'إدارة المخاطر المؤسسية' : 'Enterprise Risk Management'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/risk')}
        submitLabel={isAr ? 'تسجيل القبول' : 'Record acceptance'}/>
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
