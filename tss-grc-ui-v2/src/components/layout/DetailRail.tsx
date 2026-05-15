import type { ReactNode } from 'react';

/**
 * Compact key/value row for the persistent detail rail. Designed to be
 * stacked inside a single Card so the rail stays scannable rather than
 * bloating into many separate cards. industry convention.
 */
export function RailField({
  label, value, mono, accent,
}: {
  label: string;
  value: ReactNode;
  mono?: boolean;
  /** Render the value bigger / bolder, e.g. for status pill or key score. */
  accent?: boolean;
}) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '110px 1fr',
        columnGap: 12,
        padding: '6px 0',
        borderBottom: '1px dashed var(--border)',
        alignItems: 'center',
      }}
    >
      <span
        style={{
          fontSize: 11,
          color: 'var(--ink-3)',
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
        }}
      >
        {label}
      </span>
      <span
        style={{
          fontSize: accent ? 14 : 13,
          fontWeight: accent ? 700 : 500,
          color: 'var(--ink-1)',
          fontFamily: mono ? 'var(--font-mono)' : undefined,
          minWidth: 0,
          overflowWrap: 'anywhere',
        }}
      >
        {value}
      </span>
    </div>
  );
}

/** Vertical block of RailField rows. */
export function RailFieldGroup({ children }: { children: ReactNode }) {
  return <div style={{ display: 'flex', flexDirection: 'column' }}>{children}</div>;
}
