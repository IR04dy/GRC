import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardCheck, FileText, Stamp } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';
import { submitWizard } from '../../../lib/submitWizard';

/**
 * PDPL §7.4 #7 — Consent management wizard.
 *
 * <p>3 steps:
 *   1. Processing activity + lawful basis
 *   2. Policy text + version + collection mechanism
 *   3. DPO validation + governance
 */
type Channel = 'WEB_FORM' | 'IN_PERSON' | 'CALL_CENTRE' | 'MOBILE_APP' | 'PAPER';
type Purpose =
  | 'MARKETING'
  | 'ANALYTICS'
  | 'PROFILING'
  | 'COOKIES'
  | 'NEWSLETTER'
  | 'THIRD_PARTY_SHARE'
  | 'CROSS_BORDER';

const PURPOSE_LABELS: Record<Purpose, { en: string; ar: string }> = {
  MARKETING:         { en: 'Direct marketing',        ar: 'تسويق مباشر' },
  ANALYTICS:         { en: 'Behavioural analytics',   ar: 'تحليلات السلوك' },
  PROFILING:         { en: 'Profiling / automated decisions', ar: 'تحليل/قرارات آلية' },
  COOKIES:           { en: 'Cookies + similar tech',  ar: 'الكوكيز والتقنيات المماثلة' },
  NEWSLETTER:        { en: 'Newsletter / updates',    ar: 'نشرة بريدية' },
  THIRD_PARTY_SHARE: { en: 'Third-party sharing',     ar: 'مشاركة مع طرف ثالث' },
  CROSS_BORDER:      { en: 'Cross-border transfer',   ar: 'نقل عبر الحدود' },
};

interface ConsentDraft {
  activityName: string;
  purposes: Purpose[];
  channel: Channel;
  policyVersion: string;
  policyTextEn: string;
  policyTextAr: string;
  granularConsent: boolean;
  hasWithdrawFlow: boolean;
  ageGate: boolean;
  parentalRequired: boolean;
  evidenceStoreMonths: number;
  dpoApproved: boolean;
  notes?: string;
}

export function ConsentManagementWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<ConsentDraft>({
    activityName: '',
    purposes: [],
    channel: 'WEB_FORM',
    policyVersion: 'v1.0',
    policyTextEn: '',
    policyTextAr: '',
    granularConsent: true,
    hasWithdrawFlow: true,
    ageGate: false,
    parentalRequired: false,
    evidenceStoreMonths: 36,
    dpoApproved: false,
  });

  function set<K extends keyof ConsentDraft>(key: K, value: ConsentDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  function togglePurpose(p: Purpose) {
    setDraft(d => ({
      ...d,
      purposes: d.purposes.includes(p)
        ? d.purposes.filter(x => x !== p)
        : [...d.purposes, p],
    }));
  }

  async function submit() {
    setSubmitting(true);
    try {
      const created = await submitWizard({
        workflowId: 'pdpl-consent',
        workflowModule: 'PDPL',
        titleEn: `Consent: ${draft.activityName}`.slice(0, 280),
        regulatoryAnchor: 'PDPL Art. 7',
        summaryEn: `${draft.purposes.length} purposes · ${draft.channel}`
                 + ` · ${draft.policyVersion}`
                 + (draft.granularConsent ? ' · granular' : ' · BUNDLED')
                 + (draft.ageGate ? ` · age-gate` : '')
                 + (draft.parentalRequired ? ' · parental' : ''),
        status: draft.dpoApproved ? 'APPROVED' : 'SUBMITTED',
        draft,
      });
      toast.success(isAr
        ? `تم اعتماد نموذج الموافقة (#${created.id})`
        : `Consent template #${created.id} published`);
      navigate('/privacy');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'activity',
      title: '1. Activity + purposes', titleAr: '١. النشاط والأغراض',
      Icon: ClipboardCheck,
      validate: () => !draft.activityName.trim() || draft.purposes.length === 0
        ? (isAr ? 'اسم النشاط وغرض واحد على الأقل مطلوبان'
                : 'Activity name and at least one purpose required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'اسم نشاط المعالجة' : 'Processing activity name'} required>
            <input className="input" value={draft.activityName}
                   onChange={e => set('activityName', e.target.value)}
                   placeholder={isAr
                     ? 'مثال: تسجيل العملاء في تطبيق الجوال'
                     : 'e.g. customer registration in mobile app'}/>
          </Field>
          <Field label={isAr ? 'قناة الجمع' : 'Collection channel'}>
            <select className="input" value={draft.channel}
                    onChange={e => set('channel', e.target.value as Channel)}>
              <option value="WEB_FORM">Web form</option>
              <option value="MOBILE_APP">Mobile app</option>
              <option value="IN_PERSON">In person</option>
              <option value="CALL_CENTRE">Call centre (recorded)</option>
              <option value="PAPER">Paper form</option>
            </select>
          </Field>
          <Field label={isAr ? 'الأغراض المُغطّاة بالموافقة' : 'Consent purposes'} full>
            <div className="wf-toggle-grid">
              {(Object.keys(PURPOSE_LABELS) as Purpose[]).map(p => (
                <Toggle key={p}
                        checked={draft.purposes.includes(p)}
                        onChange={() => togglePurpose(p)}
                        label={isAr ? PURPOSE_LABELS[p].ar : PURPOSE_LABELS[p].en}/>
              ))}
            </div>
          </Field>
          <Toggle checked={draft.granularConsent}
                  onChange={v => set('granularConsent', v)}
                  label={isAr ? 'موافقة حُبيبية لكل غرض' : 'Granular consent (per-purpose toggles)'}/>
          <Toggle checked={draft.hasWithdrawFlow}
                  onChange={v => set('hasWithdrawFlow', v)}
                  label={isAr ? 'يتيح سحب الموافقة بسهولة' : 'Easy withdrawal flow available'}/>
          {!draft.granularConsent && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              <Badge tone="warning">PDPL Art. 7</Badge>{' '}
              {isAr
                ? 'الموافقة المُجمَّعة قد لا تعتبر صحيحة — راجع مع DPO.'
                : 'Bundled consent may not satisfy PDPL Article 7 — review with DPO.'}
            </div>
          )}
        </div>
      ),
    },

    {
      id: 'policy',
      title: '2. Policy text + version', titleAr: '٢. نص السياسة',
      Icon: FileText,
      validate: () => !draft.policyVersion.trim() || !draft.policyTextEn.trim() || !draft.policyTextAr.trim()
        ? (isAr ? 'النسخة والنص (بالعربية والإنجليزية) مطلوبة'
                : 'Version and policy text (EN + AR) required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'نسخة السياسة' : 'Policy version'} required>
            <input className="input" value={draft.policyVersion}
                   onChange={e => set('policyVersion', e.target.value)}
                   placeholder="v1.0"/>
          </Field>
          <Field label={isAr ? 'مدة حفظ دليل الموافقة (أشهر)' : 'Consent evidence retention (months)'}>
            <input className="input" type="number" min={6} max={120}
                   value={draft.evidenceStoreMonths}
                   onChange={e => set('evidenceStoreMonths', Number(e.target.value || 36))}/>
          </Field>
          <Field label="Policy text (English)" full required>
            <textarea className="input" rows={4} value={draft.policyTextEn}
                      onChange={e => set('policyTextEn', e.target.value)}
                      placeholder="Plain-language consent text shown to the subject…"/>
          </Field>
          <Field label="نص السياسة (العربية)" full required>
            <textarea className="input" rows={4} value={draft.policyTextAr}
                      onChange={e => set('policyTextAr', e.target.value)}
                      placeholder="نص الموافقة بلغة بسيطة كما يُعرض على صاحب البيانات…"/>
          </Field>
          <Toggle checked={draft.ageGate}
                  onChange={v => set('ageGate', v)}
                  label={isAr ? 'يطلب التحقق من السن (PDPL م. ٧)' : 'Age gate required (PDPL Art. 7)'}/>
          {draft.ageGate && (
            <Toggle checked={draft.parentalRequired}
                    onChange={v => set('parentalRequired', v)}
                    label={isAr ? 'تطلب موافقة ولي الأمر' : 'Parental/guardian consent required'}/>
          )}
        </div>
      ),
    },

    {
      id: 'signoff',
      title: '3. DPO sign-off', titleAr: '٣. اعتماد DPO',
      Icon: Stamp,
      validate: () => !draft.dpoApproved
        ? (isAr ? 'يلزم اعتماد DPO قبل النشر' : 'DPO approval required before publish')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'ملاحظات' : 'Notes'} full>
            <textarea className="input" rows={3} value={draft.notes ?? ''}
                      onChange={e => set('notes', e.target.value)}/>
          </Field>
          <Toggle checked={draft.dpoApproved}
                  onChange={v => set('dpoApproved', v)}
                  label={isAr ? 'اعتماد مسؤول حماية البيانات (DPO)' : 'DPO approval'}/>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'النشاط' : 'Activity'} v={draft.activityName} />
            <Row k={isAr ? 'القناة' : 'Channel'} v={draft.channel} />
            <Row k={isAr ? 'الأغراض' : 'Purposes'}
                 v={draft.purposes.map(p => isAr ? PURPOSE_LABELS[p].ar : PURPOSE_LABELS[p].en).join(', ') || '—'} />
            <Row k={isAr ? 'النسخة' : 'Version'} v={draft.policyVersion} />
            <Row k={isAr ? 'الاحتفاظ' : 'Retention'}
                 v={`${draft.evidenceStoreMonths} ${isAr ? 'شهراً' : 'months'}`} />
            <Row k={isAr ? 'بوابة السن' : 'Age gate'}
                 v={draft.ageGate ? (isAr ? 'مفعّلة' : 'On') : (isAr ? 'لا' : 'Off')} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'إدارة الموافقة' : 'Consent Management'}
        sub={isAr
          ? 'معالج من ٣ خطوات — النشاط → نص السياسة → اعتماد DPO'
          : '3-step wizard — activity → policy text → DPO sign-off'}
        eyebrow={isAr ? 'الخصوصية / PDPL' : 'PDPL / Privacy'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/privacy')}
        submitLabel={isAr ? 'نشر النموذج' : 'Publish template'}/>
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
