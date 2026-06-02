import { type ReactNode } from 'react';
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

interface DataTableProps<T extends { id: string | number }> {
  columns: Column<T>[];
  rows: T[];
  empty?: ReactNode;
  /**
   * When set, each row becomes a clickable surface that navigates to the
   * returned route. Returning `undefined` for a given row leaves it inert.
   */
  rowHref?: (row: T) => string | undefined;
}

export function DataTable<T extends { id: string | number }>({ columns, rows, empty, rowHref }: DataTableProps<T>) {
  const navigate = useNavigate();
  if (rows.length === 0 && empty) return <>{empty}</>;
  return (
    <table className="risk-table">
      <thead>
        <tr>
          {columns.map(c => (
            <th key={c.header} style={{ width: c.width, textAlign: colAlign(c) }}>{c.header}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map(row => {
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
  );
}
