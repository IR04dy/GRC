import { type ReactNode } from 'react';

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
}

export function DataTable<T extends { id: string | number }>({ columns, rows, empty }: DataTableProps<T>) {
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
        {rows.map(row => (
          <tr key={row.id}>
            {columns.map((c, i) => (
              <td key={i} className={c.mono ? 'mono' : undefined} style={{ textAlign: c.align }}>
                {c.cell(row)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
