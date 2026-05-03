import type { ReactNode } from 'react';

/**
 * Two-column detail-page wrapper with a persistent right rail — ServiceNow
 * IRM "Record Producer" pattern. The rail stays visible while the user
 * switches main-area tabs and scrolls long content, so context (status,
 * owner, citations, related-records counts, quick actions) is never
 * pushed offscreen.
 *
 * Use under <DetailHeader>. Tabs typically belong inside `main` so they
 * scroll with the content; the rail stays anchored.
 */
export function DetailLayout({
  main, rail, railWidth = 320,
}: {
  main: ReactNode;
  rail: ReactNode;
  /** Pixel width of the right rail. Default 320 (≈20rem). */
  railWidth?: number;
}) {
  return (
    <div
      className="detail-layout"
      style={{
        display: 'grid',
        gridTemplateColumns: `minmax(0, 1fr) ${railWidth}px`,
        gap: 16,
        alignItems: 'start',
      }}
    >
      <div style={{ minWidth: 0, display: 'flex', flexDirection: 'column', gap: 16 }}>{main}</div>
      <aside
        style={{
          position: 'sticky',
          top: 12,
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
          maxHeight: 'calc(100vh - 24px)',
          overflowY: 'auto',
        }}
      >
        {rail}
      </aside>
    </div>
  );
}
