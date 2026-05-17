import { Link } from 'react-router-dom';
import { Badge } from '../ui/Badge';
import { findAuthority } from '../../lib/authorities';
import { STATUS_TONE, STATUS_LABEL, type RegulatorAnchor } from '../../lib/evidenceAnchors';

/**
 * Inline row of regulator-anchor chips. Each chip is one authority + one
 * cited control + the per-authority control status. Leading platforms use
 * the same compact horizontal arrangement on its Evidence + Issue rows
 * so auditors can scan multi-authority coverage at a glance.
 */
export function AnchorChips({ anchors, max = 4 }: { anchors: RegulatorAnchor[]; max?: number }) {
  if (!anchors || anchors.length === 0) return null;
  const shown = anchors.slice(0, max);
  const hidden = anchors.length - shown.length;
  return (
    <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}>
      {shown.map((a, i) => <AnchorChip key={i} anchor={a} />)}
      {hidden > 0 && (
        <span title={anchors.slice(max).map(a => `${a.authorityCode} · ${a.controlRef}`).join('\n')}>
          <Badge tone="neutral">+{hidden}</Badge>
        </span>
      )}
    </div>
  );
}

function AnchorChip({ anchor }: { anchor: RegulatorAnchor }) {
  const auth = findAuthority(anchor.authorityCode);
  const tone = STATUS_TONE[anchor.controlStatus];
  const tooltip = [
    auth ? `${auth.nameEn} (${auth.nameAr})` : anchor.authorityCode,
    `${anchor.frameworkCode.replace(/_/g, ' ')} · ${anchor.controlRef}`,
    STATUS_LABEL[anchor.controlStatus],
    anchor.citationText,
  ].filter(Boolean).join('\n');

  return (
    <Link to={`/authority/${anchor.authorityCode}`} title={tooltip} style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      padding: '2px 6px',
      border: `1px solid var(--border)`,
      borderInlineStartWidth: 3, borderInlineStartColor: auth?.colorToken ?? 'var(--ink-3)',
      borderRadius: 'var(--r-sm)',
      background: 'var(--surface-1)',
      fontSize: 10,
      textDecoration: 'none',
      color: 'inherit',
      transition: 'background var(--dur-fast) var(--ease-standard)',
    }}
      onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = 'var(--surface-2)'; }}
      onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = 'var(--surface-1)'; }}
    >
      <span style={{ fontWeight: 700, color: auth?.colorToken ?? 'var(--ink-1)' }}>
        {anchor.authorityCode}
      </span>
      <span className="mono" style={{ color: 'var(--ink-2)' }}>{anchor.controlRef}</span>
      <Badge tone={tone}>{STATUS_LABEL[anchor.controlStatus]}</Badge>
    </Link>
  );
}
