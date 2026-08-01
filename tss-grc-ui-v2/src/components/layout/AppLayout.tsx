import { Suspense, createContext, lazy, useContext, useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { CommandPalette } from '../search/CommandPalette';
import { HelpButton } from '../HelpButton';
import { useI18n } from '../../lib/i18n';
import { EntitlementProvider, useEntitlement } from '../../lib/entitlement';
import { isBarePath } from '../../lib/publicPaths';

// F2-04b. Lazy so the gate screen costs nothing on the happy path — a licensed
// tenant never fetches this chunk.
const ModuleNotLicensedPage = lazy(() =>
  import('../../pages/ModuleNotLicensedPage').then(m => ({ default: m.ModuleNotLicensedPage })));

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
// their own layout and must NOT get the authenticated sidebar/topbar.
//
// The list itself moved to lib/publicPaths.ts with F2-04b, because the licence
// gate needs the same set — a path with no session has no tenant to entitle —
// and two copies of "which routes are public" would eventually disagree. See
// that file for why one list answers both questions.
function PageFallback() {
  return <div className="page" aria-busy="true" />;
}

/**
 * F2-04b — refuses the current route when its module is not licensed.
 *
 * <p>One gate over a path→SKU prefix map, rather than a wrapper on each of the
 * 328 {@code <Route>}s: same grain the server uses (package → SKU, not endpoint
 * → SKU), and it cannot be forgotten on a newly added route.
 *
 * <p>Fails open in every uncertain case — see lib/entitlement.tsx. This is UX,
 * not enforcement; {@code LicenseGuardAspect} is the boundary.
 */
function ModuleGate({ children }: { children: React.ReactNode }) {
  const { pathname } = useLocation();
  const { isPathEnabled } = useEntitlement();
  if (isPathEnabled(pathname)) return <>{children}</>;
  return <ModuleNotLicensedPage />;
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
  //
  // Deliberately OUTSIDE EntitlementProvider and ModuleGate: these routes have
  // no session, so there is nothing to entitle. `skuForPath` returns null for
  // them anyway (belt and braces), but keeping the provider off this branch also
  // keeps an anonymous whistleblower from issuing an `isAuthenticated()` request
  // that can only 401.
  if (isBarePath(pathname)) {
    return (
      <SidebarDrawerContext.Provider value={drawer}>
        <Suspense fallback={<PageFallback />}><Outlet /></Suspense>
      </SidebarDrawerContext.Provider>
    );
  }

  return (
    <SidebarDrawerContext.Provider value={drawer}>
      {/* F2-04b — one fetch of the tenant's enabled SKUs per login: AppLayout is
          the single persistent shell, so this does not refetch on navigation. */}
      <EntitlementProvider>
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
            <ModuleGate><Outlet /></ModuleGate>
          </Suspense>
          {/* Mounted once at shell level — persists across navigation. */}
          <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
          <HelpButton />
        </div>
      </EntitlementProvider>
    </SidebarDrawerContext.Provider>
  );
}
