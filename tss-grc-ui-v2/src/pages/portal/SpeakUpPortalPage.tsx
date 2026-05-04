import { useState } from 'react';
import { ShieldAlert, Send, Copy, ArrowRight, Lock, EyeOff } from 'lucide-react';
import { PublicShell } from '../../components/layout/PublicShell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useIntake } from '../../lib/intake';

/**
 * SpeakUp anonymous whistleblower portal — DD-57.
 *
 * Modelled on NAVEX EthicsPoint:
 *  - Anonymous-by-default (the form actively discourages identifying yourself)
 *  - Multi-channel awareness: web (this), phone, mobile (future), in-person
 *  - Reference code for case tracking — the only way an anonymous reporter
 *    can check status without disclosing identity
 *  - Multi-language (EN/AR) — Saudi Nazaha guidance
 *  - No analytics, no IP logging note shown to reporter
 *
 * Standards anchor:
 *  - Saudi Nazaha (Anti-Corruption Authority) reporting framework
 *  - ISO 37001:2016 §8.3 — anti-bribery management, reporting concerns
 *  - EU Whistleblower Directive 2019/1937 (best-practice template)
 *  - IFAC §150 — fundamental ethics principles for professionals
 *
 * Critical security/UX rules:
 *  - The "remain anonymous" option must be visually equivalent to identifying
 *  - The case code must be displayed prominently and downloadable
 *  - No required-field-asterisks on identity inputs
 *  - Submit must succeed even with zero identity inputs
 */
export function SpeakUpPortalPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const { submit } = useIntake();
  const [submitted, setSubmitted] = useState<{ refCode: string } | null>(null);
  const [form, setForm] = useState({
    anonymous: true,
    reporterName: '',
    reporterEmail: '',
    reporterPhone: '',
    category: 'PROCUREMENT_FRAUD',
    summary: '',
    involvedParties: '',
    location: '',
    occurredAt: '',
    evidenceAvailable: false,
    evidenceDescription: '',
    consent: false,
  });
  const set = <K extends keyof typeof form>(k: K, v: typeof form[K]) => setForm(prev => ({ ...prev, [k]: v }));

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form.summary.trim() || form.summary.trim().length < 30) {
      toast.danger(t('portalSpeakUpErrorSummary'));
      return;
    }
    if (!form.consent) {
      toast.danger(t('portalSpeakUpErrorConsent'));
      return;
    }
    const rec = submit({
      kind: 'SPEAKUP',
      channel: 'WEB_FORM',
      submitterIsAnonymous: form.anonymous,
      submitterRole: 'PUBLIC',
      submitterName: form.anonymous ? undefined : (form.reporterName.trim() || undefined),
      submitterEmail: form.anonymous ? undefined : (form.reporterEmail.trim() || undefined),
      submitterPhone: form.anonymous ? undefined : (form.reporterPhone.trim() || undefined),
      payload: {
        category: form.category,
        summary: form.summary.trim(),
        involvedParties: form.involvedParties.trim() || undefined,
        location: form.location.trim() || undefined,
        occurredAt: form.occurredAt || undefined,
        evidenceAvailable: form.evidenceAvailable,
        evidenceDescription: form.evidenceAvailable ? form.evidenceDescription.trim() : undefined,
      },
      severity: 'HIGH',
      locale: lang,
    });
    setSubmitted({ refCode: rec.referenceCode });
  }

  if (submitted) {
    return (
      <PublicShell title={t('portalSpeakUpAckTitle')} sub={t('portalSpeakUpAckSub')}>
        <Card>
          <div style={{
            background: 'var(--warning-bg)', border: '1px solid var(--warning-fg)',
            padding: 16, borderRadius: 'var(--r-sm)', marginBottom: 16,
            display: 'flex', gap: 12, alignItems: 'flex-start',
          }}>
            <Lock size={20} color="var(--warning-fg)" style={{ flexShrink: 0, marginTop: 2 }} />
            <div>
              <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--warning-fg)' }}>
                {t('portalSpeakUpSaveCode')}
              </div>
              <div style={{ fontSize: 12, color: 'var(--ink-2)', marginTop: 6, lineHeight: 1.6 }}>
                {t('portalSpeakUpSaveCodeBody')}
              </div>
            </div>
          </div>

          <div style={{
            background: 'var(--surface-2)', border: '2px solid var(--primary)',
            borderRadius: 'var(--r-sm)', padding: 24, textAlign: 'center', marginBottom: 16,
          }}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.08em' }}>
              {t('portalSpeakUpCaseCode')}
            </div>
            <code className="mono" style={{
              fontSize: 32, fontWeight: 700, color: 'var(--primary)',
              letterSpacing: '0.08em', display: 'block', margin: '12px 0',
            }}>{submitted.refCode}</code>
            <Button variant="primary" size="sm" Icon={Copy} onClick={() => {
              navigator.clipboard.writeText(submitted.refCode);
              toast.success(t('portalDsrCopied'));
            }}>{t('actionCopy')}</Button>
          </div>

          <div style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.6, marginBottom: 16 }}>
            {t('portalSpeakUpAckBody')}
          </div>

          <div style={{ display: 'flex', gap: 8, justifyContent: 'center', flexWrap: 'wrap' }}>
            <Button variant="primary" Icon={ArrowRight} onClick={() => {
              window.location.href = `/portal/track?code=${submitted.refCode}`;
            }}>{t('portalSpeakUpTrack')}</Button>
            <Button variant="ghost" onClick={() => { setSubmitted(null); setForm({
              anonymous: true, reporterName: '', reporterEmail: '', reporterPhone: '',
              category: 'PROCUREMENT_FRAUD', summary: '', involvedParties: '', location: '',
              occurredAt: '', evidenceAvailable: false, evidenceDescription: '', consent: false,
            }); }}>{t('portalDsrAckAnother')}</Button>
          </div>
        </Card>
      </PublicShell>
    );
  }

  const categories = [
    { value: 'PROCUREMENT_FRAUD',  labelEn: 'Procurement fraud', labelAr: 'احتيال في المشتريات' },
    { value: 'BRIBERY',            labelEn: 'Bribery / kickback', labelAr: 'رشوة / عمولات' },
    { value: 'CONFLICT_OF_INTEREST', labelEn: 'Conflict of interest', labelAr: 'تضارب مصالح' },
    { value: 'HARASSMENT',         labelEn: 'Harassment / discrimination', labelAr: 'تحرّش / تمييز' },
    { value: 'DATA_MISUSE',        labelEn: 'Misuse of data',     labelAr: 'إساءة استخدام البيانات' },
    { value: 'FINANCIAL_MISCONDUCT', labelEn: 'Financial misconduct', labelAr: 'مخالفات مالية' },
    { value: 'SAFETY',             labelEn: 'Safety / regulatory violation', labelAr: 'سلامة / مخالفة تنظيمية' },
    { value: 'OTHER',              labelEn: 'Other',              labelAr: 'أخرى' },
  ];

  return (
    <PublicShell title={t('portalSpeakUpTitle')} sub={t('portalSpeakUpSub')}>
      <div style={{
        background: 'var(--info-bg)', border: '1px solid var(--info-fg)',
        padding: 16, borderRadius: 'var(--r-sm)', marginBottom: 16,
        display: 'flex', gap: 12, alignItems: 'flex-start',
      }}>
        <EyeOff size={20} color="var(--info-fg)" style={{ flexShrink: 0, marginTop: 2 }} />
        <div style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.6 }}>
          <strong style={{ color: 'var(--info-fg)' }}>{t('portalSpeakUpAnonHeading')}</strong>
          <br />
          {t('portalSpeakUpAnonBody')}
        </div>
      </div>

      <Card title={t('portalSpeakUpFormTitle')} Icon={ShieldAlert}>
        <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ background: 'var(--surface-2)', padding: 12, borderRadius: 'var(--r-sm)' }}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>
              {t('portalSpeakUpIdentity')}
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <label style={{
                flex: 1, display: 'flex', alignItems: 'center', gap: 8, padding: '10px 12px', cursor: 'pointer',
                border: form.anonymous ? '1px solid var(--primary)' : '1px solid var(--border)',
                background: form.anonymous ? 'var(--primary-50)' : 'var(--surface-1)',
                borderRadius: 'var(--r-sm)', fontSize: 13,
              }}>
                <input type="radio" name="anon" checked={form.anonymous} onChange={() => set('anonymous', true)} />
                <strong>{t('portalSpeakUpAnonChoice')}</strong>
              </label>
              <label style={{
                flex: 1, display: 'flex', alignItems: 'center', gap: 8, padding: '10px 12px', cursor: 'pointer',
                border: !form.anonymous ? '1px solid var(--primary)' : '1px solid var(--border)',
                background: !form.anonymous ? 'var(--primary-50)' : 'var(--surface-1)',
                borderRadius: 'var(--r-sm)', fontSize: 13,
              }}>
                <input type="radio" name="anon" checked={!form.anonymous} onChange={() => set('anonymous', false)} />
                <span>{t('portalSpeakUpIdentChoice')}</span>
              </label>
            </div>
            {!form.anonymous && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginTop: 12 }}>
                <input value={form.reporterName} onChange={e => set('reporterName', e.target.value)}
                  placeholder={t('portalDsrFldNamePh')} style={inputStyle} />
                <input type="email" value={form.reporterEmail} onChange={e => set('reporterEmail', e.target.value)}
                  placeholder="name@example.sa" style={inputStyle} />
                <input value={form.reporterPhone} onChange={e => set('reporterPhone', e.target.value)}
                  placeholder="+966 5..." style={inputStyle} />
              </div>
            )}
          </div>

          <Field label={t('portalSpeakUpFldCategory')} required>
            <select value={form.category} onChange={e => set('category', e.target.value)} style={inputStyle}>
              {categories.map(c => <option key={c.value} value={c.value}>{lang === 'ar' ? c.labelAr : c.labelEn}</option>)}
            </select>
          </Field>

          <Field label={t('portalSpeakUpFldSummary')} required hint={t('portalSpeakUpFldSummaryHint')}>
            <textarea value={form.summary} onChange={e => set('summary', e.target.value)}
              placeholder={t('portalSpeakUpFldSummaryPh')} rows={6}
              style={{ ...inputStyle, resize: 'vertical' }} />
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
              {form.summary.trim().length}/30 {t('portalSpeakUpMinChars')}
            </div>
          </Field>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <Field label={t('portalSpeakUpFldInvolved')}>
              <input value={form.involvedParties} onChange={e => set('involvedParties', e.target.value)}
                placeholder={t('portalSpeakUpFldInvolvedPh')} style={inputStyle} />
            </Field>
            <Field label={t('portalSpeakUpFldLocation')}>
              <input value={form.location} onChange={e => set('location', e.target.value)}
                placeholder={t('portalSpeakUpFldLocationPh')} style={inputStyle} />
            </Field>
            <Field label={t('portalSpeakUpFldWhen')}>
              <input type="date" value={form.occurredAt} onChange={e => set('occurredAt', e.target.value)} style={inputStyle} />
            </Field>
          </div>

          <Field label={t('portalSpeakUpFldEvidence')}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, padding: 8, fontSize: 13, color: 'var(--ink-2)' }}>
              <input type="checkbox" checked={form.evidenceAvailable} onChange={e => set('evidenceAvailable', e.target.checked)} />
              {t('portalSpeakUpFldEvidenceAvail')}
            </label>
            {form.evidenceAvailable && (
              <textarea value={form.evidenceDescription} onChange={e => set('evidenceDescription', e.target.value)}
                placeholder={t('portalSpeakUpFldEvidencePh')} rows={3}
                style={{ ...inputStyle, resize: 'vertical' }} />
            )}
          </Field>

          <label style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', cursor: 'pointer' }}>
            <input type="checkbox" checked={form.consent} onChange={e => set('consent', e.target.checked)} style={{ marginTop: 2 }} />
            <span style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.5 }}>{t('portalSpeakUpConsent')}</span>
          </label>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
            <Badge tone="warning">{t('portalSpeakUpRetention')}</Badge>
            <Button variant="primary" Icon={Send} onClick={() => {/* form onSubmit */}}>
              {t('portalSpeakUpSubmit')}
            </Button>
          </div>
        </form>
      </Card>
    </PublicShell>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '10px 12px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)', color: 'var(--ink-1)',
};

function Field({ label, required, hint, children }: { label: string; required?: boolean; hint?: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>
        {label}{required ? ' *' : ''}
      </div>
      {children}
      {hint && <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>{hint}</div>}
    </div>
  );
}
