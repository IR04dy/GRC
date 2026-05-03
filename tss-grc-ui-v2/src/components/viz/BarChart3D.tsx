export interface BarItem { label: string; value: number; color: string; shade?: string; href?: string; }

export function BarChart3D({ items, max, onItemClick }: { items: BarItem[]; max?: number; onItemClick?: (item: BarItem) => void }) {
  const m = max ?? Math.max(...items.map(i => i.value), 1);
  return (
    <div className="bars3d">
      {items.map(it => {
        const clickable = !!(onItemClick || it.href);
        return (
          <div
            key={it.label}
            className={`bar-col${clickable ? ' bar-col-clickable' : ''}`}
            role={clickable ? 'button' : undefined}
            tabIndex={clickable ? 0 : undefined}
            onClick={clickable ? () => onItemClick?.(it) : undefined}
            onKeyDown={clickable ? (e) => {
              if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onItemClick?.(it); }
            } : undefined}
            title={clickable ? `${it.label}: ${it.value} — drill in` : undefined}
            style={clickable ? { cursor: 'pointer' } : undefined}
          >
            <div className="bar-val">{it.value}</div>
            <div
              className="bar3d"
              style={{
                height: `${(it.value / m) * 100}%`,
                background: `linear-gradient(180deg, ${it.color}, ${it.shade ?? '#15201D'})`,
              }}
            />
            <div className="bar-lbl">{it.label}</div>
          </div>
        );
      })}
    </div>
  );
}
