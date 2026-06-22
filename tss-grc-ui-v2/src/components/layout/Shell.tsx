import { type ReactNode } from 'react';
import { Topbar } from './Topbar';
import { useSidebarDrawer } from './AppLayout';

/**
 * Shell — per-page main column (Topbar + scrollable page body).
 *
 * <p>The sidebar, command palette, help button and skip-link used to live here
 * and were rebuilt on every navigation. They now live in {@link AppLayout},
 * which mounts them once and renders this component through an {@code <Outlet/>}.
 * Shell therefore renders only the main column ({@code .grc-main}) — the second
 * cell of the persistent {@code .grc-shell} grid — so the sidebar keeps its
 * scroll/expand state across navigations.</p>
 *
 * <p>The Topbar's hamburger toggles the mobile drawer via the shared
 * {@link useSidebarDrawer} context; its search button opens the palette by
 * dispatching the {@code grc:open-palette} event AppLayout listens for.</p>
 */
export function Shell({ title, sub, children }: { title?: string; sub?: string; children: ReactNode }) {
  const { toggle } = useSidebarDrawer();
  return (
    <div className="grc-main">
      <Topbar
        title={title}
        sub={sub}
        onOpenPalette={() => window.dispatchEvent(new Event('grc:open-palette'))}
        onToggleSidebar={toggle}
      />
      <main id="main-content" className="page" tabIndex={-1}>{children}</main>
    </div>
  );
}
