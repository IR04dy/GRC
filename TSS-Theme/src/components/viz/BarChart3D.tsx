export interface BarItem { label: string; value: number; color: string; shade?: string; }

export function BarChart3D({ items, max }: { items: BarItem[]; max?: number }) {
  const m = max ?? Math.max(...items.map(i => i.value), 1);
  return (
    <div className="bars3d">
      {items.map(it => (
        <div key={it.label} className="bar-col">
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
      ))}
    </div>
  );
}
