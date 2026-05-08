// Provenance badge — DD-72.
//
// Marks a register row as having originated from an intake promotion,
// showing the source intake reference code (e.g. "↩ SI-K7L9-Z4M2") so
// auditors and users can trace any record back to the front-door
// submission. Click navigates to /intake-tracker?ref=<code> to open the
// case-tracker view of the source intake.

import { Link } from 'react-router-dom';
import { CornerDownLeft } from 'lucide-react';
import { useI18n } from '../../lib/i18n';

export function PromotedBadge({ sourceReferenceCode }: { sourceReferenceCode: string }) {
  const { t } = useI18n();
  return (
    <Link
      to={`/intake-tracker?ref=${encodeURIComponent(sourceReferenceCode)}`}
      onClick={e => e.stopPropagation()}
      title={t('promotedBadgeTitle')}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 4,
        padding: '2px 6px', marginInlineStart: 6,
        fontSize: 10, fontWeight: 600, letterSpacing: '0.02em',
        color: 'var(--ink-3)', background: 'var(--surface-2)',
        border: '1px dashed var(--border)', borderRadius: 'var(--r-sm)',
        textDecoration: 'none', fontFamily: 'var(--font-mono)',
        lineHeight: 1.2, verticalAlign: 'middle',
      }}>
      <CornerDownLeft size={10} />
      {sourceReferenceCode}
    </Link>
  );
}
