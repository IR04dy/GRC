import { type ReactNode, useState } from 'react';
import { useNavigate } from 'react-router-dom';

export interface Column<T> {
  /** Header text shown in <th>. */
  header: string;
  /** Render a cell. The full row is passed in. */
  cell: (row: T) => ReactNode;
  /** Optional CSS width applied to the <th>. */
  width?: string;
  /** Cell text alignment. */
  align?: 'start' | 'end' | 'center';
  /** Renders cell with the monospace token (e.g. for IDs and codes). */
  mono?: boolean;
  /**
   * Numeric column: end-aligns the cell (so digits line up on the decimal
   * edge for scanning) and applies `tabular-nums` so figures don't jitter
   * between rows. Use for counts, scores, currency, percentages. An explicit
   * `align` still wins if you set one.
   */
  numeric?: boolean;
}

/** Resolve the effective alignment: explicit `align` wins, else numeric → end. */
function colAlign<T>(c: Column<T>): 'start' | 'end' | 'center' | undefined {
  return c.align ?? (c.numeric ? 'end' : undefined);
}

/**
 * PERF-FE-003: default page size. The shared table is used by 72 registers and
 * previously materialised one <tr> per row with no windowing — a register that
 * fetched its whole result set (e.g. via an unpaged endpoint) rendered thousands
 * of DOM nodes. Pagination only kicks in when rows EXCEED this size, so the
 * common case (a server-paged page, or any list of ≤100 rows) renders exactly as
 * before with no pager — backward-compatible for all existing consumers.
 */
const DEFAULT_PAGE_SIZE = 100;

interface DataTableProps<T extends { id: string | number }> {
  columns: Column<T>[];
  rows: T[];
  empty?: ReactNode;
  /**
   * When set, each row becomes a clickable surface that navigates to the
   * returned route. Returning `undefined` for a given row leaves it inert.
   */
  rowHref?: (row: T) => string | undefined;
  /**
   * PERF-FE-003: client-side page size. Defaults to {@link DEFAULT_PAGE_SIZE}.
   * Pagination is bypassed entirely when the row count is at or below this
   * value, so small/server-paged tables are unaffected. Pass a smaller value to
   * force earlier pagination, or `0` to disable the cap and render every row.
   */
  pageSize?: number;
}

export function DataTable<T extends { id: string | number }>(
  { columns, rows, empty, rowHref, pageSize = DEFAULT_PAGE_SIZE }: DataTableProps<T>,
) {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);

  if (rows.length === 0 && empty) return <>{empty}</>;

  // PERF-FE-003: only paginate when there's actually something to bound.
  const paginate = pageSize > 0 && rows.length > pageSize;
  const pageCount = paginate ? Math.ceil(rows.length / pageSize) : 1;
  // Clamp so a shrinking result set (e.g. after a filter) can never strand us on
  // an out-of-range page.
  const safePage = Math.min(page, pageCount - 1);
  const start = paginate ? safePage * pageSize : 0;
  const visibleRows = paginate ? rows.slice(start, start + pageSize) : rows;

  return (
    <>
      <table className="risk-table">
        <thead>
          <tr>
            {columns.map(c => (
              <th key={c.header} style={{ width: c.width, textAlign: colAlign(c) }}>{c.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {visibleRows.map(row => {
            const href = rowHref?.(row);
            const onClick = href ? (e: React.MouseEvent) => {
              // Don't hijack clicks on inline buttons / links inside the row.
              const target = e.target as HTMLElement;
              if (target.closest('a, button, input, select, textarea, [role="button"]')) return;
              navigate(href);
            } : undefined;
            const onKey = href ? (e: React.KeyboardEvent) => {
              if (e.key === 'Enter') { e.preventDefault(); navigate(href); }
            } : undefined;
            return (
              <tr
                key={row.id}
                onClick={onClick}
                onKeyDown={onKey}
                tabIndex={href ? 0 : undefined}
                role={href ? 'link' : undefined}
                className={href ? 'row-clickable' : undefined}
                style={href ? { cursor: 'pointer' } : undefined}
              >
                {columns.map((c, i) => {
                  const align = colAlign(c);
                  // End-aligned columns are numeric (or actions, where it's a
                  // no-op) — give them tabular figures so digits don't jitter.
                  const tnum = c.numeric || align === 'end';
                  return (
                    <td
                      key={i}
                      className={c.mono ? 'mono' : undefined}
                      style={{ textAlign: align, fontVariantNumeric: tnum ? 'tabular-nums' : undefined }}
                    >
                      {c.cell(row)}
                    </td>
                  );
                })}
              </tr>
            );
          })}
        </tbody>
      </table>
      {paginate && (
        <nav className="datatable-pager" aria-label="Table pagination">
          <span className="datatable-pager__status" aria-live="polite">
            {start + 1}–{Math.min(start + pageSize, rows.length)} / {rows.length}
          </span>
          <button
            type="button"
            className="datatable-pager__btn"
            onClick={() => setPage(p => Math.max(0, Math.min(p, pageCount - 1) - 1))}
            disabled={safePage <= 0}
            aria-label="Previous page"
          >‹</button>
          <button
            type="button"
            className="datatable-pager__btn"
            onClick={() => setPage(p => Math.min(pageCount - 1, Math.min(p, pageCount - 1) + 1))}
            disabled={safePage >= pageCount - 1}
            aria-label="Next page"
          >›</button>
        </nav>
      )}
    </>
  );
}
