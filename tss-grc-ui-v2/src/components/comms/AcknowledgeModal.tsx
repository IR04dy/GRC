import { useEffect, useState } from 'react';
import { CheckCheck, Send } from 'lucide-react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import { STAKEHOLDERS } from '../../lib/stakeholders';
import { type NotificationSpec, type CommLogEntry, formatDeadline } from '../../lib/notificationMatrix';

/**
 * "Mark sent" / "Acknowledge" modal — captures receipt-ref + notes when an
 * auditor records that a regulator notification has been dispatched. Used
 * by both /communications (workspace pending list) and the comms tab on
 * detail pages.
 *
 * On submit, fires onAcknowledge with the user-entered receipt-ref +
 * note + chosen state (SENT vs ACKNOWLEDGED). The caller is responsible
 * for persisting the mutation (local state + optionally localStorage).
 */
export function AcknowledgeModal({
  open, spec, recordKey, existing, onClose, onAcknowledge,
}: {
  open: boolean;
  spec: NotificationSpec | null;
  recordKey: string | null;
  /** If editing an existing entry. */
  existing?: CommLogEntry;
  onClose: () => void;
  onAcknowledge: (update: { state: 'SENT' | 'ACKNOWLEDGED'; receiptRef: string; note: string }) => void;
}) {
  const { lang, t } = useI18n();
  const [receiptRef, setReceiptRef] = useState('');
  const [note, setNote] = useState('');
  const [state, setState] = useState<'SENT' | 'ACKNOWLEDGED'>('SENT');

  useEffect(() => {
    if (open) {
      setReceiptRef(existing?.receiptRef ?? '');
      setNote(existing?.noteEn ?? '');
      setState(existing?.state === 'ACKNOWLEDGED' ? 'ACKNOWLEDGED' : 'SENT');
    }
  }, [open, existing]);

  if (!spec) return null;
  const stakeholder = STAKEHOLDERS.find(s => s.code === spec.toStakeholderCode);

  return (
    <Modal open={open} onClose={onClose} title={t('commsAckTitle')}
      footer={<>
        <Button variant="secondary" onClick={onClose}>{t('actionCancel') ?? 'Cancel'}</Button>
        <Button variant="primary" Icon={CheckCheck}
          onClick={() => onAcknowledge({ state, receiptRef: receiptRef.trim(), note: note.trim() })}>
          {t('commsAckConfirm')}
        </Button>
      </>}>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{
          padding: '10px 12px', borderRadius: 'var(--r-sm)',
          background: 'var(--surface-2)',
        }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 4 }}>
            {lang === 'ar' ? spec.descriptionAr : spec.descriptionEn}
          </div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
            <span className="mono">{recordKey}</span>
            {' · → '}
            {lang === 'ar' ? stakeholder?.nameAr : stakeholder?.nameEn}
            {' · '}
            <Badge tone="info">{formatDeadline(spec.withinHours, lang)}</Badge>
            {' · '}
            <span className="mono">{spec.regulatoryRef}</span>
          </div>
        </div>

        <fieldset style={{ display: 'flex', gap: 8, alignItems: 'center', border: 'none', padding: 0 }}>
          <legend style={{ fontSize: 11, color: 'var(--ink-3)',
                           textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700, padding: 0, marginBottom: 4 }}>
            {t('commsAckState')}
          </legend>
          <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, cursor: 'pointer' }}>
            <input type="radio" checked={state === 'SENT'} onChange={() => setState('SENT')} />
            <Send size={12} /> {t('commsAckStateSent')}
          </label>
          <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, cursor: 'pointer' }}>
            <input type="radio" checked={state === 'ACKNOWLEDGED'} onChange={() => setState('ACKNOWLEDGED')} />
            <CheckCheck size={12} /> {t('commsAckStateAck')}
          </label>
        </fieldset>

        <div>
          <label style={{ display: 'block', fontSize: 11, color: 'var(--ink-3)',
                          textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700, marginBottom: 4 }}>
            {t('commsAckReceipt')}
          </label>
          <input type="text" value={receiptRef} onChange={(e) => setReceiptRef(e.target.value)}
            placeholder={t('commsAckReceiptPlaceholder')}
            className="form-input" style={{ width: '100%' }} />
          <div style={{ fontSize: 10, color: 'var(--ink-4)', marginTop: 4 }}>
            {t('commsAckReceiptHint')}
          </div>
        </div>

        <div>
          <label style={{ display: 'block', fontSize: 11, color: 'var(--ink-3)',
                          textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700, marginBottom: 4 }}>
            {t('commsAckNote')}
          </label>
          <textarea value={note} onChange={(e) => setNote(e.target.value)}
            rows={3} className="form-input"
            placeholder={t('commsAckNotePlaceholder')}
            style={{ width: '100%', resize: 'vertical' }} />
        </div>
      </div>
    </Modal>
  );
}
