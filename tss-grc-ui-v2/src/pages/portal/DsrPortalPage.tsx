import { useState } from 'react';
import { CheckCircle2, FileText, Send, Copy, ArrowRight } from 'lucide-react';
import { PublicShell } from '../../components/layout/PublicShell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useIntake } from '../../lib/intake';

/**
 * Public DSR portal — DD-55.
 *
 * PDPL Art. 4-7 mandates that data subjects can submit access / erasure /
 * rectification / portability / objection requests. Until now this only
 * existed as an internal-only register; the customer had no way to file.
 *
 * Modelled on industry-standard tooling's privacy portal:
 *   - No-auth public surface
 *   - Multi-language toggle (EN/AR)
 *   - Bilingual auto-acknowledgement with reference code
 *   - Reference-code lookup at /portal/track for status checks
 *   - Identity verification step (optional, configurable)
 *   - Timestamps + 30-day SLA clock starts on submission
 *
 * The submission flows through useIntake.submit() into the same intake
 * registry the triager inbox reads from.
 */
export function DsrPortalPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const { submit } = useIntake();
  const [submitted, setSubmitted] = useState<{ refCode: string; slaDueAt: string } | null>(null);
  const [form, setForm] = useState({
    requesterName: '',
    requesterEmail: '',
    requestType: 'ACCESS',
    scope: '',
    verificationMethod: 'NATIONAL_ID',
    idLast4: '',
    consent: false,
  });

  const set = <K extends keyof typeof form>(k: K, v: typeof form[K]) => setForm(prev => ({ ...prev, [k]: v }));

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form.requesterName.trim() || !form.requesterEmail.trim()) {
      toast.danger(t('portalDsrErrorIdentity'));
      return;
    }
    if (!form.scope.trim()) {
      toast.danger(t('portalDsrErrorScope'));
      return;
    }
    if (!form.consent) {
      toast.danger(t('portalDsrErrorConsent'));
      return;
    }
    const rec = submit({
      kind: 'DSR_REQUEST',
      channel: 'WEB_FORM',
      submitterIsAnonymous: false,
      submitterRole: 'CUSTOMER',
      submitterName: form.requesterName.trim(),
      submitterEmail: form.requesterEmail.trim(),
      payload: {
        requestType: form.requestType,
        scope: form.scope.trim(),
        verificationMethod: form.verificationMethod,
        idLast4: form.idLast4.trim() ? form.idLast4.trim().slice(-4) : undefined,
      },
      severity: 'MEDIUM',
      locale: lang,
    });
    setSubmitted({ refCode: rec.referenceCode, slaDueAt: rec.slaDueAt ?? '' });
  }

  if (submitted) {
    return (
      <PublicShell title={t('portalDsrAckTitle')} sub={t('portalDsrAckSub')}>
        <Card>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: 24, textAlign: 'center' }}>
            <div style={{
              width: 72, height: 72, borderRadius: '50%',
              background: 'var(--success-bg)', color: 'var(--success-fg)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              marginBottom: 16,
            }}>
              <CheckCircle2 size={40} strokeWidth={2} />
            </div>
            <h2 style={{ margin: 0, fontSize: 20, fontWeight: 700, color: 'var(--ink-1)' }}>
              {t('portalDsrAckHeading')}
            </h2>
            <p style={{ marginTop: 8, fontSize: 14, color: 'var(--ink-2)', maxWidth: 520, lineHeight: 1.6 }}>
              {t('portalDsrAckBody')}
            </p>
          </div>

          <div style={{
            background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
            padding: 16, margin: '8px 0', border: '1px dashed var(--border)',
          }}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
              {t('portalDsrAckRefCode')}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 6 }}>
              <code className="mono" style={{
                fontSize: 22, fontWeight: 700, color: 'var(--primary)', letterSpacing: '0.05em',
              }}>{submitted.refCode}</code>
              <Button variant="ghost" size="sm" Icon={Copy} onClick={() => {
                navigator.clipboard.writeText(submitted.refCode);
                toast.success(t('portalDsrCopied'));
              }}>{t('actionCopy')}</Button>
            </div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 12 }}>
              {t('portalDsrAckSlaPrefix')}: <span className="mono" style={{ color: 'var(--ink-1)', fontWeight: 600 }}>{submitted.slaDueAt.slice(0, 10)}</span>
              {' '}<span style={{ color: 'var(--ink-3)' }}>· {t('portalDsrAckSlaSuffix')}</span>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 8, justifyContent: 'center', marginTop: 16, flexWrap: 'wrap' }}>
            <Button variant="primary" Icon={ArrowRight} onClick={() => {
              window.location.href = `/portal/track?code=${submitted.refCode}`;
            }}>{t('portalDsrAckTrack')}</Button>
            <Button variant="ghost" onClick={() => setSubmitted(null)}>{t('portalDsrAckAnother')}</Button>
          </div>
        </Card>
      </PublicShell>
    );
  }

  const requestTypes = [
    { value: 'ACCESS',         labelEn: 'Access — copy of my data',          labelAr: 'الوصول — الحصول على نسخة من بياناتي' },
    { value: 'ERASURE',        labelEn: 'Erasure — delete my data',          labelAr: 'المحو — حذف بياناتي' },
    { value: 'RECTIFICATION',  labelEn: 'Rectification — correct my data',   labelAr: 'التصحيح — تصحيح بياناتي' },
    { value: 'PORTABILITY',    labelEn: 'Portability — export my data',      labelAr: 'النقل — تصدير بياناتي' },
    { value: 'OBJECTION',      labelEn: 'Objection — stop a specific use',   labelAr: 'الاعتراض — إيقاف استخدام محدّد' },
    { value: 'WITHDRAW_CONSENT', labelEn: 'Withdraw consent',                labelAr: 'سحب الموافقة' },
  ];

  const verifyMethods = [
    { value: 'NATIONAL_ID', labelEn: 'National ID (Saudi)', labelAr: 'الهوية الوطنية' },
    { value: 'IQAMA',       labelEn: 'Iqama (resident ID)', labelAr: 'الإقامة' },
    { value: 'PASSPORT',    labelEn: 'Passport',            labelAr: 'جواز السفر' },
  ];

  return (
    <PublicShell title={t('portalDsrTitle')} sub={t('portalDsrSub')}>
      <Card title={t('portalDsrFormTitle')} Icon={FileText}>
        <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ background: 'var(--info-bg)', padding: 12, borderRadius: 'var(--r-sm)', fontSize: 12, color: 'var(--ink-2)' }}>
            <Badge tone="info">{t('portalDsrRefBadge')}</Badge> {t('portalDsrPdplNotice')}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label={t('portalDsrFldName')} required>
              <input value={form.requesterName} onChange={e => set('requesterName', e.target.value)}
                placeholder={t('portalDsrFldNamePh')} style={inputStyle} />
            </Field>
            <Field label={t('portalDsrFldEmail')} required>
              <input type="email" value={form.requesterEmail} onChange={e => set('requesterEmail', e.target.value)}
                placeholder="name@example.sa" style={inputStyle} />
            </Field>
          </div>

          <Field label={t('portalDsrFldRequestType')} required>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 6 }}>
              {requestTypes.map(rt => (
                <label key={rt.value} style={{
                  display: 'flex', alignItems: 'center', gap: 8,
                  padding: '8px 10px',
                  border: form.requestType === rt.value ? '1px solid var(--primary)' : '1px solid var(--border)',
                  background: form.requestType === rt.value ? 'var(--primary-50)' : 'var(--surface-2)',
                  borderRadius: 'var(--r-sm)', cursor: 'pointer', fontSize: 13,
                }}>
                  <input type="radio" name="requestType" value={rt.value}
                    checked={form.requestType === rt.value}
                    onChange={() => set('requestType', rt.value)} />
                  <span>{lang === 'ar' ? rt.labelAr : rt.labelEn}</span>
                </label>
              ))}
            </div>
          </Field>

          <Field label={t('portalDsrFldScope')} required>
            <textarea value={form.scope} onChange={e => set('scope', e.target.value)}
              placeholder={t('portalDsrFldScopePh')} rows={4}
              style={{ ...inputStyle, resize: 'vertical' }} />
          </Field>

          <div style={{ background: 'var(--surface-2)', padding: 12, borderRadius: 'var(--r-sm)' }}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>
              {t('portalDsrFldVerify')}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <select value={form.verificationMethod} onChange={e => set('verificationMethod', e.target.value)} style={inputStyle}>
                {verifyMethods.map(m => <option key={m.value} value={m.value}>{lang === 'ar' ? m.labelAr : m.labelEn}</option>)}
              </select>
              <input value={form.idLast4} onChange={e => set('idLast4', e.target.value.replace(/\D/g, '').slice(0, 4))}
                placeholder={t('portalDsrFldIdLast4')} maxLength={4} style={inputStyle} />
            </div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 8 }}>{t('portalDsrFldVerifyHint')}</div>
          </div>

          <label style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', cursor: 'pointer' }}>
            <input type="checkbox" checked={form.consent} onChange={e => set('consent', e.target.checked)} style={{ marginTop: 2 }} />
            <span style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.5 }}>{t('portalDsrConsent')}</span>
          </label>

          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button variant="primary" Icon={Send} onClick={() => {/* form onSubmit handles it */}}>
              {t('portalDsrSubmit')}
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

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>
        {label}{required ? ' *' : ''}
      </div>
      {children}
    </div>
  );
}
