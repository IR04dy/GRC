import { type ReactNode } from 'react';

/**
 * PillTabs — one token-driven segmented / filter-pill control.
 *
 * Replaces the several hand-rolled pill implementations (GrcExpert family
 * tabs, Posture family chips, Evidence framework sub-tabs) that each used
 * different radii (20 / 999 / pill), paddings, and active colours. Active
 * pill = `--primary` fill with `--on-primary` text (theme-correct).
 *
 * RTL flows naturally (flex + logical spacing). Keyboard/ARIA accessible
 * (`role="tablist"`, `aria-pressed`).
 */
export interface PillTabOption<T extends string | number> {
  id: T;
  label: ReactNode;
  count?: number;
}

export interface PillTabsProps<T extends string | number> {
  options: PillTabOption<T>[];
  active: T;
  onChange: (id: T) => void;
  'aria-label'?: string;
  className?: string;
}

export function PillTabs<T extends string | number>({
  options,
  active,
  onChange,
  className,
  ...rest
}: PillTabsProps<T>) {
  return (
    <div className={`pill-tabs${className ? ' ' + className : ''}`} role="tablist" aria-label={rest['aria-label']}>
      {options.map((o) => {
        const on = o.id === active;
        return (
          <button
            key={String(o.id)}
            type="button"
            role="tab"
            aria-selected={on}
            aria-pressed={on}
            className="pill-tab"
            onClick={() => onChange(o.id)}
          >
            {o.label}
            {o.count != null && <span className="pill-tab__count">{o.count}</span>}
          </button>
        );
      })}
    </div>
  );
}
