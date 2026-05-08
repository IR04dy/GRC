import { useEffect, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { CommandPalette } from '../search/CommandPalette';
import { getOrgTheme } from '../../lib/orgTheme';

export function Shell({ title, sub, children }: { title: string; sub?: string; children: ReactNode }) {
  const [paletteOpen, setPaletteOpen] = useState(false);
  // Sprint 4 §2.8 — mobile sidebar drawer state.
  // Above 768px CSS pins the sidebar open; below, it slides in via .sb-open.
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const location = useLocation();

  // Global keyboard shortcut: Cmd/Ctrl+K opens the command palette.
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
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [sidebarOpen]);

  // Auto-close drawer on route change (mobile UX)
  useEffect(() => { setSidebarOpen(false); }, [location.pathname]);

  // Multi-tenant visual cue: a 3-px coloured stripe at the very top of the
  // page, painted in the active organisation's primaryColor. Makes a tenant
  // switch impossible to miss — TSS = navy, Aramco = teal, STC = green, etc.
  const orgTheme = getOrgTheme();
  return (
    <div className="grc-shell">
      {orgTheme?.primaryColor && (
        <div
          style={{
            position: 'fixed',
            top: 0, left: 0, right: 0,
            height: 3,
            background: orgTheme.primaryColor,
            zIndex: 1200,
            pointerEvents: 'none',
          }}
          aria-hidden="true"
          title={orgTheme.organisationNameEn}
        />
      )}
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
