import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Database, FileText, Globe, Stamp } from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';
import { privacyApi } from '../../../lib/api';

/**
 * PDPL §7.4 #8 — Record of Processing Activities (RoPA) wizard.
 *
 * <p>4 steps tracking PDPL-aligned RoPA fields (mirrors Article 30
 * equivalent under PDPL implementing regulations):
 *   1. Activity + lawful basis
 *   2. Data categories + subject categories + retention
 *   3. Recipients + transfers
 *   4. DPO review + publish
 */
type LawfulBasis =
  | 'CONSENT'
  | 'CONTRACT'
  | 'LEGAL_OBLIGATION'
  | 'VITAL_INTEREST'
  | 'PUBLIC_INTEREST'
  | 'LEGITIMATE_INTEREST';

const BASIS_LABELS: Record<LawfulBasis, { en: string; ar: string }> = {
  CONSENT:             { en: 'Consent',                ar: 'الموافقة' },
  CONTRACT:            { en: 'Contract necessity',     ar: 'ضرورة تعاقدية' },
  LEGAL_OBLIGATION:    { en: 'Legal obligation',       ar: 'التزام قانوني' },
  VITAL_INTEREST:      { en: 'Vital interest',         ar: 'مصلحة حيوية' },
  PUBLIC_INTEREST:     { en: 'Public interest',        ar: 'مصلحة عامة' },
  LEGITIMATE_INTEREST: { en: 'Legitimate interest',    ar: 'مصلحة مشروعة' },
};

interface RopaDraft {
  activityName: string;
  ownerRole: string;
  purposeEn: string;
  lawfulBasis: LawfulBasis;
  lawfulBasisJustification?: string;
  dataCategories: string;
  specialCategories: string;
  subjectCategories: string;
  retentionMonths: number;
  retentionRule?: string;
  recipients?: string;
  internationalTransfer: boolean;
  transferDetails?: string;
  systemNames?: string;
  dpoReviewed: boolean;
  isPublished: boolean;
  notes?: string;
}

export function RopaWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<RopaDraft>({
    activityName: '',
    ownerRole: '',
    purposeEn: '',
    lawfulBasis: 'CONTRACT',
    dataCategories: '',
    specialCategories: '',
    subjectCategories: '',
    retentionMonths: 60,
    internationalTransfer: false,
    dpoReviewed: false,
    isPublished: false,
  });

  function set<K extends keyof RopaDraft>(key: K, value: RopaDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  async function submit() {
    setSubmitting(true);
    try {
      // Composite data categories blob includes special categories + recipients
      // + retention rule + transfer context — the backend RoPA row uses
      // single text fields so we squash the rich wizard state into them.
      const dataCategoriesBlob = [
        draft.dataCategories,
        draft.specialCategories?.trim() ? `Special: ${draft.specialCategories}` : null,
        draft.subjectCategories?.trim() ? `Subjects: ${draft.subjectCategories}` : null,
        draft.systemNames?.trim() ? `Systems: ${draft.systemNames}` : null,
        draft.recipients?.trim() ? `Recipients: ${draft.recipients}` : null,
        draft.internationalTransfer && draft.transferDetails?.trim()
          ? `Transfer: ${draft.transferDetails}`
          : null,
        draft.lawfulBasisJustification?.trim() ? `Basis note: ${draft.lawfulBasisJustification}` : null,
      ].filter(Boolean).join(' · ');

      const retentionPeriod = draft.retentionRule?.trim()
        ? `${draft.retentionMonths} months — ${draft.retentionRule}`
        : `${draft.retentionMonths} months`;

      const created = await privacyApi.createRopa({
        processNameEn: draft.activityName,
        processNameAr: undefined,
        legalBasis: draft.lawfulBasis,
        dataCategories: dataCategoriesBlob,
        retentionPeriod,
        status: draft.isPublished ? 'PUBLISHED'
              : draft.dpoReviewed ? 'REVIEWED'
              : 'DRAFT',
      });
      toast.success(isAr
        ? `تم نشر نشاط المعالجة في السجل (#${created.id})`
        : `Activity #${created.id} published to RoPA`);
      navigate('/ropa');
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'activity',
      title: '1. Activity + basis', titleAr: '١. النشاط والأساس القانوني',
      Icon: FileText,
      validate: () => !draft.activityName.trim() || !draft.purposeEn.trim() || !draft.ownerRole.trim()
        ? (isAr ? 'الاسم والغرض والمالك مطلوبة'
                : 'Activity name, purpose and owner role are required')
        : draft.lawfulBasis === 'LEGITIMATE_INTEREST' && !draft.lawfulBasisJustification?.trim()
          ? (isAr ? 'يلزم تبرير المصلحة المشروعة' : 'Legitimate-interest balancing test required')
          : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'اسم نشاط المعالجة' : 'Activity name'} required>
            <input className="input" value={draft.activityName}
                   onChange={e => set('activityName', e.target.value)}
                   placeholder={isAr
                     ? 'مثال: تشغيل برنامج الولاء'
                     : 'e.g. loyalty programme operation'}/>
          </Field>
          <Field label={isAr ? 'دور المالك' : 'Owner role'} required>
            <input className="input" value={draft.ownerRole}
                   onChange={e => set('ownerRole', e.target.value)}
                   placeholder={isAr ? 'مثال: مدير التسويق' : 'e.g. Marketing Director'}/>
          </Field>
          <Field label={isAr ? 'الغرض' : 'Purpose'} full required>
            <textarea className="input" rows={2} value={draft.purposeEn}
                      onChange={e => set('purposeEn', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'الأساس القانوني' : 'Lawful basis'}>
            <select className="input" value={draft.lawfulBasis}
                    onChange={e => set('lawfulBasis', e.target.value as LawfulBasis)}>
              {(Object.keys(BASIS_LABELS) as LawfulBasis[]).map(b => (
                <option key={b} value={b}>
                  {isAr ? BASIS_LABELS[b].ar : BASIS_LABELS[b].en}
                </option>
              ))}
            </select>
          </Field>
          <Field label={isAr ? 'تبرير الأساس القانوني' : 'Basis justification'} full
                 required={draft.lawfulBasis === 'LEGITIMATE_INTEREST'}>
            <textarea className="input" rows={3} value={draft.lawfulBasisJustification ?? ''}
                      onChange={e => set('lawfulBasisJustification', e.target.value)}
                      placeholder={draft.lawfulBasis === 'LEGITIMATE_INTEREST'
                        ? (isAr
                            ? 'اختبار الموازنة: لماذا تتجاوز المصلحة المشروعة حقوق صاحب البيانات؟'
                            : 'Balancing test: why does the legitimate interest override the subject\'s rights?')
                        : ''}/>
          </Field>
        </div>
      ),
    },

    {
      id: 'data',
      title: '2. Data + retention', titleAr: '٢. البيانات والاحتفاظ',
      Icon: Database,
      validate: () => !draft.dataCategories.trim() || !draft.subjectCategories.trim()
        ? (isAr ? 'فئات البيانات وأصحاب البيانات مطلوبة'
                : 'Data + subject categories required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'فئات البيانات' : 'Data categories'} full required>
            <textarea className="input" rows={3} value={draft.dataCategories}
                      onChange={e => set('dataCategories', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: الاسم، البريد، رقم الهاتف، سجل المعاملات'
                        : 'e.g. name, email, phone, transaction history'}/>
          </Field>
          <Field label={isAr ? 'فئات حساسة (إن وُجدت)' : 'Special / sensitive categories'} full>
            <textarea className="input" rows={2} value={draft.specialCategories}
                      onChange={e => set('specialCategories', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: بيانات صحية، بيومترية، أديان، أبناء'
                        : 'e.g. health, biometric, religious, minors'}/>
          </Field>
          <Field label={isAr ? 'فئات أصحاب البيانات' : 'Subject categories'} full required>
            <textarea className="input" rows={2} value={draft.subjectCategories}
                      onChange={e => set('subjectCategories', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: عملاء، موظفون، زوار، أبناء العملاء'
                        : 'e.g. customers, employees, visitors, customer\'s children'}/>
          </Field>
          <Field label={isAr ? 'الاحتفاظ (أشهر)' : 'Retention (months)'}>
            <input className="input" type="number" min={1} max={240}
                   value={draft.retentionMonths}
                   onChange={e => set('retentionMonths', Number(e.target.value || 60))}/>
          </Field>
          <Field label={isAr ? 'قاعدة الاحتفاظ' : 'Retention rule'} full>
            <input className="input" value={draft.retentionRule ?? ''}
                   onChange={e => set('retentionRule', e.target.value)}
                   placeholder={isAr
                     ? 'مثال: نظام التعاملات الإلكترونية ٧ سنوات'
                     : 'e.g. Electronic Transactions Law — 7 years'}/>
          </Field>
          <Field label={isAr ? 'الأنظمة المستضيفة' : 'Hosting systems'} full>
            <input className="input" value={draft.systemNames ?? ''}
                   onChange={e => set('systemNames', e.target.value)}
                   placeholder="e.g. CRM, Loyalty DB, S3 backups"/>
          </Field>
        </div>
      ),
    },

    {
      id: 'recipients',
      title: '3. Recipients + transfers', titleAr: '٣. المستلمون والنقل',
      Icon: Globe,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'المستلمون الداخليون والخارجيون' : 'Recipients (internal + external)'} full>
            <textarea className="input" rows={3} value={draft.recipients ?? ''}
                      onChange={e => set('recipients', e.target.value)}
                      placeholder={isAr
                        ? 'مثال: قسم التسويق، شركة البريد، مزود السحابة'
                        : 'e.g. Marketing team, postal vendor, cloud provider'}/>
          </Field>
          <Toggle checked={draft.internationalTransfer}
                  onChange={v => set('internationalTransfer', v)}
                  label={isAr ? 'يوجد نقل دولي للبيانات' : 'International transfer involved'}/>
          {draft.internationalTransfer && (
            <>
              <Field label={isAr ? 'تفاصيل النقل' : 'Transfer details'} full>
                <textarea className="input" rows={3} value={draft.transferDetails ?? ''}
                          onChange={e => set('transferDetails', e.target.value)}
                          placeholder={isAr
                            ? 'مثال: نقل البيانات إلى الإمارات بآلية SCC، مرجع SDO-2026-XXXX'
                            : 'e.g. transfer to UAE under SCC, SDO ref SDO-2026-XXXX'}/>
              </Field>
              <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
                <Badge tone="info">{isAr ? 'مرتبط' : 'Linked'}</Badge>{' '}
                {isAr
                  ? 'يجب إنشاء سجل "نقل عبر الحدود" مستقل لكل ولاية مستلمة.'
                  : 'A separate Cross-Border Transfer record is required per recipient jurisdiction.'}
              </div>
            </>
          )}
        </div>
      ),
    },

    {
      id: 'publish',
      title: '4. DPO review + publish', titleAr: '٤. مراجعة DPO ونشر',
      Icon: Stamp,
      validate: () => !draft.dpoReviewed
        ? (isAr ? 'يلزم مراجعة DPO قبل النشر' : 'DPO review required before publish')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'ملاحظات DPO' : 'DPO notes'} full>
            <textarea className="input" rows={3} value={draft.notes ?? ''}
                      onChange={e => set('notes', e.target.value)}/>
          </Field>
          <Toggle checked={draft.dpoReviewed}
                  onChange={v => set('dpoReviewed', v)}
                  label={isAr ? 'تمت مراجعة DPO' : 'DPO reviewed'}/>
          <Toggle checked={draft.isPublished}
                  onChange={v => set('isPublished', v)}
                  label={isAr ? 'نشر في سجل المعالجة' : 'Publish to RoPA register'}/>
          <div className="wf-review" style={{ gridColumn: '1 / -1' }}>
            <Row k={isAr ? 'النشاط' : 'Activity'} v={draft.activityName} />
            <Row k={isAr ? 'المالك' : 'Owner'} v={draft.ownerRole} />
            <Row k={isAr ? 'الأساس' : 'Basis'}
                 v={isAr ? BASIS_LABELS[draft.lawfulBasis].ar : BASIS_LABELS[draft.lawfulBasis].en} />
            <Row k={isAr ? 'الاحتفاظ' : 'Retention'}
                 v={`${draft.retentionMonths} ${isAr ? 'شهراً' : 'months'}`} />
            <Row k={isAr ? 'نقل دولي' : 'International transfer'}
                 v={draft.internationalTransfer ? (isAr ? 'نعم' : 'Yes') : (isAr ? 'لا' : 'No')} />
            <Row k={isAr ? 'الحساس' : 'Sensitive'}
                 v={draft.specialCategories.trim() ? (isAr ? 'يحتوي' : 'Included') : '—'} />
          </div>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'سجل أنشطة المعالجة (RoPA)' : 'Record of Processing Activities'}
        sub={isAr
          ? 'معالج من ٤ خطوات — النشاط → البيانات → المستلمون → اعتماد DPO ونشر'
          : '4-step wizard — activity → data → recipients → DPO review + publish'}
        eyebrow={isAr ? 'الخصوصية / PDPL' : 'PDPL / Privacy'}
      />
      <WizardShell steps={steps} active={step} onChange={setStep} rtl={isAr}
        submitting={submitting} onSubmit={submit} onCancel={() => navigate('/ropa')}
        submitLabel={isAr ? 'إضافة إلى السجل' : 'Add to RoPA'}/>
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
