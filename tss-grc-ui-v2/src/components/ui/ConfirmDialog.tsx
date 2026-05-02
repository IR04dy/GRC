import { Modal } from './Modal';
import { Button } from './Button';
import { useI18n } from '../../lib/i18n';
import { AlertTriangle } from 'lucide-react';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string;
  /** Defaults to "Delete" / "Confirm". */
  confirmLabel?: string;
  /** When true, renders the confirm button in danger styling. */
  destructive?: boolean;
  onCancel: () => void;
  onConfirm: () => void | Promise<void>;
  busy?: boolean;
}

/**
 * Light-weight confirmation modal used for destructive actions.
 * Sits on top of the page modal stack via portal — same z-index as FormModal.
 */
export function ConfirmDialog({
  open, title, message, confirmLabel, destructive, onCancel, onConfirm, busy,
}: ConfirmDialogProps) {
  const { t } = useI18n();
  return (
    <Modal
      open={open}
      title={title}
      size="sm"
      onClose={() => { if (!busy) onCancel(); }}
      footer={
        <>
          <Button variant="ghost" onClick={onCancel} disabled={busy}>{t('cancel')}</Button>
          <Button variant={destructive ? 'danger' : 'primary'} onClick={onConfirm} disabled={busy}>
            {busy ? t('saving') : (confirmLabel ?? t('save'))}
          </Button>
        </>
      }
    >
      <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
        <div style={{
          flexShrink: 0, width: 40, height: 40,
          borderRadius: 'var(--r-md)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          background: destructive ? 'var(--danger-bg)' : 'var(--warning-bg)',
          color: destructive ? 'var(--danger-fg)' : 'var(--warning-fg)',
        }}>
          <AlertTriangle size={20} strokeWidth={2.2} />
        </div>
        <div style={{ flex: 1, fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.55, paddingTop: 4 }}>
          {message}
        </div>
      </div>
    </Modal>
  );
}
