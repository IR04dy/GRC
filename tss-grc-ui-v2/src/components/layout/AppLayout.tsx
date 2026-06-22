import { Suspense, createContext, useContext, useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { CommandPalette } from '../search/CommandPalette';
import { HelpButton } from '../HelpButton';
import { useI18n } from '../../lib/i18n';

/**
 * AppLayout — the single persistent application shell.
 *
 * <p>Before this existed, every page rendered its own {@code <Shell>}, so the
 * {@code <Sidebar>} was destroyed and rebuilt on <em>every</em> navigation —
 * which is why the sidebar scroll position, expanded groups and badge counts
 * reset each time you clicked a link. This layout route mounts the Sidebar
 * <b>once</b>; only the {@code <Outlet/>} (the page's own {@code <Shell>} body)
 * remounts as you navigate, so the nav keeps its place natively.</p>
 *
 * <p>The {@code <Suspense>} boundary lives <i>inside</i> this layout, wrapping
 * only the Outlet — so a lazy page chunk loading swaps just the main column
 * while the Sidebar stays put (no flicker, no remount).</p>
 */

// ── Mobile drawer state ───────────────────────────────────────────────
// AppLayout owns the sidebar-drawer open state; the per-page Shell's Topbar
// hamburger toggles it through this context (Sidebar and Topbar now live in
// different components, so a shared context replaces the old prop drilling).
interface SidebarDrawerCtx { open: boolean; toggle: () => void; close: () => void; }
const SidebarDrawerContext = createContext<SidebarDrawerCtx>({
  open: false, toggle: () => {}, close: () => {},
});
export const useSidebarDrawer = () => useContext(SidebarDrawerContext);

// ── Bare (no-chrome) routes ───────────────────────────────────────────
// Public portals (PublicShell) + the login + token/full-screen surfaces bring
// their own layout and must NOT get the authenticated sidebar/topbar. Derived
// from the actual page set: the 9 PublicShell pages + LoginPage,
// MobileCrisisRoomPage, PublicSubprocessorListPage, VendorAttestationSubmitPage
// (which render neither Shell nor PublicShell). Everything else renders <Shell>
// and therefore gets the persistent chrome.
const BARE_PREFIXES = [
  '/login',
  '/public/',
  '/portal/',
  '/privacy/public/',
  '/ethics/whistleblower/submit',
  '/ethics/whistleblower/status',
  '/tprm/attestations/',
  '/crisis/mobile',
  '/bcm/crisis/mobile',
];
function isBarePath(pathname: string): boolean {
  if (pathname === '/') return true; // redirects to /login
  return BARE_PREFIXES.some(pre => pathname === pre || pathname.startsWith(pre));
}

function PageFallback() {
  return <div className="page" aria-busy="true" />;
}

export function AppLayout() {
  const { lang } = useI18n();
  const { pathname, search } = useLocation();
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Global Cmd/Ctrl+K opens the command palette; Esc closes the mobile drawer.
  // The per-page Topbar opens the palette via a `grc:open-palette` window event
  // (it no longer holds the palette state itself).
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const mod = e.metaKey || e.ctrlKey;
      if (mod && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault();
        setPaletteOpen(o => !o);
      }
      if (e.key === 'Escape' && drawerOpen) setDrawerOpen(false);
    }
    function onOpenPalette() { setPaletteOpen(true); }
    window.addEventListener('keydown', onKey);
    window.addEventListener('grc:open-palette', onOpenPalette);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('grc:open-palette', onOpenPalette);
    };
  }, [drawerOpen]);

  // Close the mobile drawer on every navigation. Keyed on the full location
  // (path + query) — not just pathname — so query-only sub-links like the NDI
  // tabs (/ndi?tab=…) also dismiss the overlay drawer on mobile. Otherwise the
  // page changed underneath while the drawer stayed open covering it, which
  // read as "I clicked the sub-link and nothing happened".
  useEffect(() => { setDrawerOpen(false); }, [pathname, search]);

  const drawer = useMemo<SidebarDrawerCtx>(() => ({
    open: drawerOpen,
    toggle: () => setDrawerOpen(o => !o),
    close: () => setDrawerOpen(false),
  }), [drawerOpen]);

  // Public / full-screen routes: no persistent chrome — the page supplies its
  // own layout. Still wrapped in Suspense for lazy chunks.
  if (isBarePath(pathname)) {
    return (
      <SidebarDrawerContext.Provider value={drawer}>
        <Suspense fallback={<PageFallback />}><Outlet /></Suspense>
      </SidebarDrawerContext.Provider>
    );
  }

  return (
    <SidebarDrawerContext.Provider value={drawer}>
      <div className="grc-shell">
        {/* A11y — jump past the persistent chrome straight to page content. */}
        <a href="#main-content" className="skip-link">
          {lang === 'ar' ? 'تخطَّ إلى المحتوى الرئيسي' : 'Skip to main content'}
        </a>
        <Sidebar open={drawerOpen} />
        {drawerOpen && <div className="sb-backdrop" onClick={() => setDrawerOpen(false)} />}
        {/* Inner Suspense: only the main column shows the fallback while a page
            chunk loads — the Sidebar above stays mounted. */}
        <Suspense fallback={<PageFallback />}>
          <Outlet />
        </Suspense>
        {/* Mounted once at shell level — persists across navigation. */}
        <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
        <HelpButton />
      </div>
    </SidebarDrawerContext.Provider>
  );
}
