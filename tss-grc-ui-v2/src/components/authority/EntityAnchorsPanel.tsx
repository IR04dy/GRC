import { Link } from 'react-router-dom';
import { Landmark, ExternalLink } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { findAuthority } from '../../lib/authorities';
import { STATUS_LABEL, type RegulatorAnchor } from '../../lib/evidenceAnchors';

const STATUS_TONE: Record<RegulatorAnchor['controlStatus'], 'success' | 'warning' | 'accent' | 'danger' | 'neutral'> = {
  IMPLEMENTED:    'success',
  PARTIAL:        'warning',
  COMPENSATING:   'accent',
  NOT_IMPLEMENTED:'danger',
  NOT_APPLICABLE: 'neutral',
};

/**
 * Detail-page panel that surfaces every regulatory anchor for the entity
 * (Finding / Control / Policy / Risk / DPIA / Vendor). Each row is a
 * direct hyperlink to the authority workspace so an auditor can pivot
 * from "this finding" → "every other finding under NCA ECC".
 *
 * ServiceNow IRM Issue + Policy + Control records expose the same
 * "Citations" related list — this is its compact equivalent.
 */
export function EntityAnchorsPanel({
  anchors, title, subtitle,
}: {
  anchors: RegulatorAnchor[];
  title?: string;
  subtitle?: string;
}) {
  if (!anchors || anchors.length === 0) {
    return null;
  }
  return (
    <Card title={title ?? 'Regulatory anchors'} Icon={Landmark}
      action={<Badge tone="primary">{anchors.length}</Badge>}>
      {subtitle && <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>{subtitle}</div>}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {anchors.map((a, i) => {
          const auth = findAuthority(a.authorityCode);
          return (
            <Link key={i} to={`/authority/${a.authorityCode}`} style={{
              display: 'grid',
              gridTemplateColumns: '4px 1fr auto auto auto',
              gap: 10,
              alignItems: 'center',
              padding: '8px 10px 8px 0',
              borderRadius: 'var(--r-sm)',
              border: '1px solid var(--border)',
              textDecoration: 'none',
              color: 'inherit',
              background: 'var(--surface-1)',
              transition: 'border-color var(--dur-fast) var(--ease-standard), background var(--dur-fast) var(--ease-standard)',
            }}
              onMouseEnter={(e) => {
                const el = e.currentTarget as HTMLElement;
                el.style.borderColor = 'var(--primary-300)';
                el.style.background  = 'var(--primary-50)';
              }}
              onMouseLeave={(e) => {
                const el = e.currentTarget as HTMLElement;
                el.style.borderColor = 'var(--border)';
                el.style.background  = 'var(--surface-1)';
              }}
            >
              <span style={{ alignSelf: 'stretch', background: auth?.colorToken ?? 'var(--ink-3)' }} />
              <div style={{ minWidth: 0, paddingInlineStart: 6 }}>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
                  <span style={{ fontWeight: 700, fontSize: 13, color: auth?.colorToken ?? 'var(--ink-1)' }}>
                    {a.authorityCode}
                  </span>
                  <span className="mono" style={{ fontSize: 12, color: 'var(--ink-2)' }}>
                    {a.frameworkCode.replace(/_/g, ' ')} · {a.controlRef}
                  </span>
                </div>
                {a.citationText && (
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 1, fontStyle: 'italic' }}>
                    {a.citationText}
                  </div>
                )}
                {auth && (
                  <div style={{ fontSize: 10, color: 'var(--ink-4)', marginTop: 1 }}>
                    {auth.nameEn}
                  </div>
                )}
              </div>
              <Badge tone={STATUS_TONE[a.controlStatus]} dot>{STATUS_LABEL[a.controlStatus]}</Badge>
              <span style={{ fontSize: 10, color: 'var(--ink-3)' }}>{auth?.jurisdiction ?? ''}</span>
              <ExternalLink size={12} style={{ color: 'var(--ink-3)' }} />
            </Link>
          );
        })}
      </div>
    </Card>
  );
}
