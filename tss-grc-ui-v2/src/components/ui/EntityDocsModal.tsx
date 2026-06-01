// Reusable "Documents & control mapping" modal for register pages that have no
// detail page. Drop a row-action that sets a target, then render one of these.
// Bundles the typed AttachmentsField + the optional/manual ControlMappingPicker.
import { Modal } from './Modal';
import { AttachmentsField } from './AttachmentsField';
import { ControlMappingPicker } from './ControlMappingPicker';
import { useI18n } from '../../lib/i18n';

export interface EntityDocsModalProps {
  entityType: string;
  entityId: number | null;
  open: boolean;
  onClose: () => void;
  /** Optional row label shown in the modal title (e.g. the record code). */
  recordLabel?: string;
}

export function EntityDocsModal({ entityType, entityId, open, onClose, recordLabel }: EntityDocsModalProps) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  if (!open || entityId == null) return null;
  const base = ar ? 'المستندات وربط الضوابط' : 'Documents & control mapping';
  return (
    <Modal open title={recordLabel ? `${base} · ${recordLabel}` : base} size="lg" onClose={onClose}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <AttachmentsField entityType={entityType} entityId={entityId} />
        <div style={{ borderTop: '1px solid var(--border)' }} />
        <ControlMappingPicker entityType={entityType} entityId={entityId} />
      </div>
    </Modal>
  );
}
