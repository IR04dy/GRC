import { useEffect, useMemo, useState } from 'react';
import { Send, Save } from 'lucide-react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { useI18n } from '../../lib/i18n';
import { STAKEHOLDERS, type CommChannel } from '../../lib/stakeholders';
import { NOTIFICATION_MATRIX } from '../../lib/notificationMatrix';
import type { Communication } from '../../lib/communications';
import { renderTemplate } from '../../lib/notificationTemplates';

/**
 * "New Communication" modal — lets an auditor compose an ad-hoc comm
 * (proactive board update, voluntary regulator engagement, customer
 * advisory, etc.) that isn't pre-defined in the notification matrix.
 *
 * If the user picks a NotificationSpec from the dropdown, the body is
 * pre-filled from the template; otherwise the body starts blank. The
 * comm enters at DRAFT (default) or IN_REVIEW (on Submit-for-review)
 * — never directly SENT, since this flow exists precisely to enforce
 * the review gate.
 */
export function NewCommunicationModal({
  open, onClose, onCreate, presetSourceRecordKey,
}: {
  open: boolean;
  onClose: () => void;
  onCreate: (init: Omit<Communication, 'id' | 'createdAt' | 'status'> & { status?: 'DRAFT' | 'IN_REVIEW' }) => void;
  /** Optional — when launched from a detail page, pre-fills the source. */
  presetSourceRecordKey?: string;
}) {
  const { t, lang } = useI18n();

  const [recipientCode, setRecipientCode] = useState<string>('');
  const [channel, setChannel]             = useState<CommChannel>('EMAIL');
  const [subject, setSubject]             = useState('');
  const [bodyEn, setBodyEn]               = useState('');
  const [bodyAr, setBodyAr]               = useState('');
  const [sourceRecordKey, setSourceRecordKey] = useState<string>(presetSourceRecordKey ?? '');
  const [sourceSpecId, setSourceSpecId]       = useState<string>('');

  // When the user picks a notification spec, render its template into
  // the body fields so they can edit instead of writing from scratch.
  const selectedSpec = useMemo(
    () => NOTIFICATION_MATRIX.find(s => s.id === sourceSpecId),
    [sourceSpecId],
  );

  useEffect(() => {
    if (!open) return;
    setRecipientCode('');
    setChannel('EMAIL');
    setSubject('');
    setBodyEn('');
    setBodyAr('');
    setSourceRecordKey(presetSourceRecordKey ?? '');
    setSourceSpecId('');
  }, [open, presetSourceRecordKey]);

  useEffect(() => {
    if (!selectedSpec) return;
    setRecipientCode(selectedSpec.toStakeholderCode);
    setChannel(selectedSpec.channel);
    // Render English first
    const en = renderTemplate({
      spec: selectedSpec,
      entry: { id: 'preview', specId: selectedSpec.id, eventDetectedAt: new Date().toISOString(), state: 'PENDING' },
      recordKey: sourceRecordKey || 'AD-HOC',
    }, 'en');
    const ar = renderTemplate({
      spec: selectedSpec,
      entry: { id: 'preview', specId: selectedSpec.id, eventDetectedAt: new Date().toISOString(), state: 'PENDING' },
      recordKey: sourceRecordKey || 'AD-HOC',
    }, 'ar');
    setSubject(lang === 'ar' ? ar.subject : en.subject);
    setBodyEn(en.body);
    setBodyAr(ar.body);
  }, [selectedSpec, sourceRecordKey, lang]);

  function handle(action: 'DRAFT' | 'IN_REVIEW') {
    if (!recipientCode || !subject.trim() || !bodyEn.trim()) return;
    onCreate({
      subject: subject.trim(),
      bodyEn:  bodyEn.trim(),
      bodyAr:  bodyAr.trim() || undefined,
      channel,
      recipientCode,
      sourceRecordKey: sourceRecordKey.trim() || undefined,
      sourceSpecId: sourceSpecId || undefined,
      createdBy: 'GRC Operator',
      status: action,
    });
    onClose();
  }

  const recipientOptions = STAKEHOLDERS.map(s => ({
    value: s.code,
    label: lang === 'ar' ? s.nameAr : s.nameEn,
  }));

  const specOptions = [
    { value: '', label: '— ' + t('newCommNoSpec') + ' —' },
    ...NOTIFICATION_MATRIX.map(s => ({
      value: s.id,
      label: `${s.id} · ${lang === 'ar' ? s.descriptionAr : s.descriptionEn}`,
    })),
  ];

  const channelOptions: CommChannel[] = ['PORTAL','EMAIL','PHONE','SECURE_MSG','FORMAL_LETTER','IN_PERSON','TEAMS_SLACK','PUBLIC_DISCLOSURE'];

  return (
    <Modal open={open} onClose={onClose} title={t('newCommTitle')}
      footer={<>
        <Button variant="secondary" onClick={onClose}>{t('actionCancel')}</Button>
        <Button variant="ghost"     Icon={Save} onClick={() => handle('DRAFT')}>{t('newCommSaveDraft')}</Button>
        <Button variant="primary"   Icon={Send} onClick={() => handle('IN_REVIEW')}>{t('newCommSubmitReview')}</Button>
      </>}>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Row label={t('newCommSpec')}>
          <select className="form-input" value={sourceSpecId}
            onChange={(e) => setSourceSpecId(e.target.value)}>
            {specOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
          <div style={{ fontSize: 10, color: 'var(--ink-4)', marginTop: 4 }}>{t('newCommSpecHint')}</div>
        </Row>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Row label={t('newCommRecipient') + ' *'}>
            <select className="form-input" value={recipientCode}
              onChange={(e) => setRecipientCode(e.target.value)} required>
              <option value="">— {t('selectPlaceholder')} —</option>
              {recipientOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Row>

          <Row label={t('newCommChannel')}>
            <select className="form-input" value={channel}
              onChange={(e) => setChannel(e.target.value as CommChannel)}>
              {channelOptions.map(c => <option key={c} value={c}>{c.replace(/_/g, ' ')}</option>)}
            </select>
          </Row>
        </div>

        <Row label={t('newCommSourceRecord')}>
          <input type="text" className="form-input" value={sourceRecordKey}
            placeholder={t('newCommSourceRecordPlaceholder')}
            onChange={(e) => setSourceRecordKey(e.target.value)} />
        </Row>

        <Row label={t('newCommSubject') + ' *'}>
          <input type="text" className="form-input" value={subject}
            onChange={(e) => setSubject(e.target.value)} required />
        </Row>

        <Row label={t('newCommBodyEn') + ' *'}>
          <textarea className="form-input" rows={8}
            value={bodyEn} onChange={(e) => setBodyEn(e.target.value)}
            style={{ fontFamily: 'var(--font-mono)', fontSize: 12, lineHeight: 1.5, resize: 'vertical' }} required />
        </Row>

        <Row label={t('newCommBodyAr')}>
          <textarea className="form-input" rows={6}
            value={bodyAr} onChange={(e) => setBodyAr(e.target.value)}
            style={{ fontFamily: 'var(--font-mono)', fontSize: 12, lineHeight: 1.5, resize: 'vertical', direction: 'rtl' }} />
        </Row>
      </div>
    </Modal>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 11, color: 'var(--ink-3)',
                      textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700, marginBottom: 4 }}>
        {label}
      </label>
      {children}
    </div>
  );
}
