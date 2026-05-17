import { cn } from '../../lib/cn';

export interface TabItem<T extends string = string> {
  id: T;
  label: string;
  /**
   * Row / record count for this tab. Renders as a small chip after the
   * label so the user sees how much data is behind each tab without having
   * to click through. Pass `0` for an empty tab — combined with `hideEmpty`
   * on the parent, a 0-count tab is dropped from the bar entirely.
   */
  count?: number;
  /**
   * Force-hide regardless of count. Use for sub-tabs gated by a feature
   * flag or role (e.g. "Approvals" tab only visible to approvers).
   */
  hidden?: boolean;
}

export function Tabs<T extends string>({
  items, active, onChange, hideEmpty,
}: {
  items: TabItem<T>[];
  active: T;
  onChange: (id: T) => void;
  /**
   * When true, any tab where count===0 is dropped from the bar. The user
   * doesn't see tabs they can't engage with. Recommended for module pages
   * where some sub-tabs may legitimately have no rows in a fresh tenant.
   */
  hideEmpty?: boolean;
}) {
  const visible = items.filter(it => !it.hidden && !(hideEmpty && it.count === 0));
  return (
    <div className="tabs" role="tablist">
      {visible.map(it => (
        <button
          key={it.id}
          role="tab"
          aria-selected={active === it.id}
          className={cn('tab', active === it.id && 'active')}
          onClick={() => onChange(it.id)}
          type="button"
        >
          {it.label}
          {typeof it.count === 'number' && it.count > 0 && (
            <span className="tab-count" aria-label={`${it.count} items`}>{it.count}</span>
          )}
        </button>
      ))}
    </div>
  );
}
