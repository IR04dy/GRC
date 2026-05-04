import { useEffect, useMemo, useState } from 'react';
import { Send, RotateCcw } from 'lucide-react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import { STAKEHOLDERS } from '../../lib/stakeholders';
import { type NotificationSpec, type CommLogEntry, formatDeadline } from '../../lib/notificationMatrix';
import { renderTemplate } from '../../lib/notificationTemplates';

/**
 * "Compose" modal — opens with the spec's bilingual template pre-filled
 * into an editable textarea. The user can review and tweak before
 * "sending"; on submit the comm transitions PENDING → SENT (or IN_FLIGHT
 * if recipient acknowledgement is required), and the rendered body is
 * stored on the comm-log entry's note for the audit trail.
 */
export function ComposeModal({
  open, spec, recordKey, entry, onClose, onSend, recordTitle,
}: {
  open: boolean;
  spec: NotificationSpec | null;
  recordKey: string | null;
  entry: CommLogEntry | null;
  /** Optional override for the {recordTitle} placeholder. */
  recordTitle?: string;
  onClose: () => void;
  onSend: (update: { state: 'IN_FLIGHT' | 'SENT'; subject: string; body: string }) => void;
}) {
  const { t, lang } = useI18n();
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [state, setState] = useState<'IN_FLIGHT' | 'SENT'>('SENT');

  const rendered = useMemo(() => {
    if (!spec || !entry || !recordKey) return null;
    return renderTemplate({ spec, entry, recordKey, recordTitle }, lang);
  }, [spec, entry, recordKey, recordTitle, lang]);

  useEffect(() => {
    if (open && rendered) {
      setSubject(rendered.subject);
      setBody(rendered.body);
      setState('SENT');
    }
  }, [open, rendered]);

  if (!spec || !entry) return null;
  const stakeholder = STAKEHOLDERS.find(s => s.code === spec.toStakeholderCode);

  return (
    <Modal open={open} onClose={onClose} title={t('commsComposeTitle')}
      footer={<>
        <Button variant="ghost" Icon={RotateCcw} onClick={() => {
          if (rendered) { setSubject(rendered.subject); setBody(rendered.body); }
        }}>{t('commsComposeReset')}</Button>
        <Button variant="secondary" onClick={onClose}>{t('actionCancel')}</Button>
        <Button variant="primary" Icon={Send}
          onClick={() => onSend({ state, subject: subject.trim(), body: body.trim() })}>
          {t('commsComposeSend')}
        </Button>
      </>}>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{
          padding: '10px 12px', borderRadius: 'var(--r-sm)',
          background: 'var(--surface-2)',
          fontSize: 12,
        }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
            <Badge tone="primary">{spec.channel.replace(/_/g, ' ')}</Badge>
            <span>→ <strong>{lang === 'ar' ? stakeholder?.nameAr : stakeholder?.nameEn}</strong></span>
            <Badge tone="info">{formatDeadline(spec.withinHours, lang)}</Badge>
            <span className="mono" style={{ color: 'var(--ink-3)' }}>{spec.regulatoryRef}</span>
          </div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 6 }}>
            <span className="mono">{recordKey}</span>
          </div>
        </div>

        <div>
          <label style={{ display: 'block', fontSize: 11, color: 'var(--ink-3)',
                          textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700, marginBottom: 4 }}>
            {t('commsComposeSubject')}
          </label>
          <input type="text" value={subject} onChange={(e) => setSubject(e.target.value)}
            className="form-input" style={{ width: '100%', fontWeight: 600 }} />
        </div>

        <div>
          <label style={{ display: 'block', fontSize: 11, color: 'var(--ink-3)',
                          textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700, marginBottom: 4 }}>
            {t('commsComposeBody')}
          </label>
          <textarea value={body} onChange={(e) => setBody(e.target.value)}
            rows={18} className="form-input"
            style={{ width: '100%', resize: 'vertical', fontFamily: 'var(--font-mono)', fontSize: 12, lineHeight: 1.5 }} />
          <div style={{ fontSize: 10, color: 'var(--ink-4)', marginTop: 4 }}>
            {t('commsComposeBodyHint')}
          </div>
        </div>

        <fieldset style={{ display: 'flex', gap: 12, alignItems: 'center', border: 'none', padding: 0 }}>
          <legend style={{ fontSize: 11, color: 'var(--ink-3)',
                           textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700, padding: 0 }}>
            {t('commsComposeAfter')}
          </legend>
          <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, cursor: 'pointer' }}>
            <input type="radio" checked={state === 'SENT'} onChange={() => setState('SENT')} />
            {t('commsComposeAfterSent')}
          </label>
          <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, cursor: 'pointer' }}>
            <input type="radio" checked={state === 'IN_FLIGHT'} onChange={() => setState('IN_FLIGHT')} />
            {t('commsComposeAfterInFlight')}
          </label>
        </fieldset>
      </div>
    </Modal>
  );
}
