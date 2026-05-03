import { useEffect, useState, type ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { CommandPalette } from '../search/CommandPalette';

export function Shell({ title, sub, children }: { title: string; sub?: string; children: ReactNode }) {
  const [paletteOpen, setPaletteOpen] = useState(false);

  // Global keyboard shortcut: Cmd/Ctrl+K opens the command palette.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const mod = e.metaKey || e.ctrlKey;
      if (mod && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault();
        setPaletteOpen(o => !o);
      }
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  return (
    <div className="grc-shell">
      <Sidebar />
      <div className="grc-main">
        <Topbar title={title} sub={sub} onOpenPalette={() => setPaletteOpen(true)} />
        <div className="page">{children}</div>
      </div>
      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
    </div>
  );
}
