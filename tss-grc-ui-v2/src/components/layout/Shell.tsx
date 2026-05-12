import { useEffect, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { CommandPalette } from '../search/CommandPalette';

export function Shell({ title, sub, children }: { title: string; sub?: string; children: ReactNode }) {
  const [paletteOpen, setPaletteOpen] = useState(false);
  // Sprint 4 §2.8 — mobile sidebar drawer state.
  // Above 768px CSS pins the sidebar open; below, it slides in via .sb-open.
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const location = useLocation();

  // Global keyboard shortcut: Cmd/Ctrl+K opens the command palette. We
  // also listen for a custom `grc:open-palette` window event so toolbar
  // buttons (e.g. per-page Filter) can open the same surface without
  // re-implementing search.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const mod = e.metaKey || e.ctrlKey;
      if (mod && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault();
        setPaletteOpen(o => !o);
      }
      // Esc closes sidebar drawer on mobile
      if (e.key === 'Escape' && sidebarOpen) setSidebarOpen(false);
    }
    function onOpenPalette() { setPaletteOpen(true); }
    window.addEventListener('keydown', onKey);
    window.addEventListener('grc:open-palette', onOpenPalette);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('grc:open-palette', onOpenPalette);
    };
  }, [sidebarOpen]);

  // Auto-close drawer on route change (mobile UX)
  useEffect(() => { setSidebarOpen(false); }, [location.pathname]);

  return (
    <div className="grc-shell">
      <Sidebar open={sidebarOpen} />
      {sidebarOpen && (
        <div className="sb-backdrop" onClick={() => setSidebarOpen(false)} />
      )}
      <div className="grc-main">
        <Topbar
          title={title}
          sub={sub}
          onOpenPalette={() => setPaletteOpen(true)}
          onToggleSidebar={() => setSidebarOpen(o => !o)}
        />
        <div className="page">{children}</div>
      </div>
      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
    </div>
  );
}
