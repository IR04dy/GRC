import { useCallback, useEffect, useState } from 'react';
import { DEFAULT_LAYOUT, WIDGETS_BY_ID, type WidgetSize } from './widgets';

export type LayoutItem = { id: string; size: WidgetSize };

const STORAGE_KEY = 'grc.dashboard.layout';

function readLayout(): LayoutItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_LAYOUT;
    const parsed = JSON.parse(raw) as LayoutItem[];
    if (!Array.isArray(parsed)) return DEFAULT_LAYOUT;
    // Drop entries whose widget id no longer exists in the registry.
    return parsed.filter(it => it && typeof it.id === 'string' && WIDGETS_BY_ID[it.id]);
  } catch { return DEFAULT_LAYOUT; }
}

export function useDashboardLayout() {
  const [items, setItems] = useState<LayoutItem[]>(() => readLayout());

  useEffect(() => {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(items)); } catch { /* quota */ }
  }, [items]);

  const move = useCallback((from: number, to: number) => {
    setItems(prev => {
      if (from === to || from < 0 || to < 0 || from >= prev.length || to >= prev.length) return prev;
      const next = [...prev];
      const [moved] = next.splice(from, 1);
      next.splice(to, 0, moved);
      return next;
    });
  }, []);

  const remove = useCallback((id: string) => {
    setItems(prev => prev.filter(it => it.id !== id));
  }, []);

  const add = useCallback((id: string) => {
    if (!WIDGETS_BY_ID[id]) return;
    setItems(prev => prev.some(it => it.id === id) ? prev : [...prev, { id, size: WIDGETS_BY_ID[id].defaultSize }]);
  }, []);

  const setSize = useCallback((id: string, size: WidgetSize) => {
    setItems(prev => prev.map(it => it.id === id ? { ...it, size } : it));
  }, []);

  const reset = useCallback(() => setItems(DEFAULT_LAYOUT), []);

  return { items, move, remove, add, setSize, reset };
}
