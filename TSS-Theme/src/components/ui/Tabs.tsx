import { cn } from '../../lib/cn';

export interface TabItem<T extends string = string> { id: T; label: string; }

export function Tabs<T extends string>({
  items, active, onChange,
}: {
  items: TabItem<T>[];
  active: T;
  onChange: (id: T) => void;
}) {
  return (
    <div className="tabs" role="tablist">
      {items.map(it => (
        <button
          key={it.id}
          role="tab"
          aria-selected={active === it.id}
          className={cn('tab', active === it.id && 'active')}
          onClick={() => onChange(it.id)}
          type="button"
        >
          {it.label}
        </button>
      ))}
    </div>
  );
}
