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
            <th key={c.header} style={{ width: c.width, textAlign: c.align }}>{c.header}</th>
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
              {columns.map((c, i) => (
                <td key={i} className={c.mono ? 'mono' : undefined} style={{ textAlign: c.align }}>
                  {c.cell(row)}
                </td>
              ))}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
