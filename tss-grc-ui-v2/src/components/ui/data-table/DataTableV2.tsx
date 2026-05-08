import { useRef, type ReactNode } from 'react';
import { flexRender, type ColumnDef, type Row } from '@tanstack/react-table';
import { useVirtualizer } from '@tanstack/react-virtual';
import { ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react';
import { useDataTable } from './useDataTable';
import type { Density, VirtualizationConfig } from './types';

/**
 * DataTable v2 — TanStack Table-backed list engine.
 *
 * Features in this scaffold:
 *   - Multi-column sort (Shift-click for secondary)
 *   - Column visibility (controlled externally for now; chooser UI in 2.1.b)
 *   - Pagination (client-side; manual mode for server-side in 2.1.c)
 *   - Row virtualization for large datasets (>500 rows recommended)
 *   - Selection mechanic (caller wires the checkbox column via columns)
 *   - Density toggle (compact/standard/comfortable)
 *   - Toolbar slot (caller renders FilterBar / BulkActionsBar / etc.)
 *
 * NOT yet in this scaffold (planned in 2.1 follow-ups):
 *   - Column chooser popover
 *   - Column resize via drag handle
 *   - Column reorder via drag-and-drop
 *   - Inline edit
 *   - CSV export from selection
 *   - Saved views integration
 *
 * Migration: imported as `DataTableV2` to live alongside the legacy 74-line
 * `DataTable` until pages are ported. Once all 30+ list pages are migrated,
 * the old DataTable will be deleted and this file renamed back to DataTable.
 */
export interface DataTableV2Props<TRow> {
  data: TRow[];
  columns: ColumnDef<TRow>[];
  /** Stable id per row — needed for selection persistence. Default: `row-${index}`. */
  getRowId?: (row: TRow, index: number) => string;
  /** Click handler on the row body. */
  onRowClick?: (row: TRow) => void;
  /** Toolbar above the table (filters, bulk actions, density control). */
  toolbar?: ReactNode;
  /** Empty state when data is []. */
  emptyState?: ReactNode;
  /** Loading state — shows skeleton rows when true. */
  loading?: boolean;
  /** Default density. Standard (40px row) is the safe choice. */
  density?: Density;
  /** Initial sort. e.g. `[{ id: 'createdAt', desc: true }]`. */
  initialSorting?: { id: string; desc: boolean }[];
  /** Page size (client-side pagination). */
  pageSize?: number;
  /** Enable selection (rows must include a checkbox column you define). */
  enableSelection?: boolean;
  /** Virtualize large lists. Recommended when data.length > 500. */
  virtualization?: VirtualizationConfig;
}

export function DataTableV2<TRow>({
  data, columns,
  getRowId = (_row, i) => `row-${i}`,
  onRowClick,
  toolbar,
  emptyState,
  loading = false,
  density = 'standard',
  initialSorting = [],
  pageSize = 50,
  enableSelection = false,
  virtualization = { enabled: false },
}: DataTableV2Props<TRow>) {
  const { table } = useDataTable<TRow>({
    data, columns, getRowId,
    initialPageSize: pageSize,
    initialSorting,
    enableSelection,
  });

  const containerRef = useRef<HTMLDivElement>(null);
  const rows = table.getRowModel().rows;

  const rowHeight = density === 'compact' ? 32 : density === 'comfortable' ? 56 : 40;

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => containerRef.current,
    estimateSize: () => virtualization.estimatedRowHeight ?? rowHeight,
    overscan: virtualization.overscan ?? 10,
    enabled: virtualization.enabled,
  });

  const virtualRows = virtualization.enabled ? virtualizer.getVirtualItems() : null;
  const totalSize = virtualization.enabled ? virtualizer.getTotalSize() : 0;

  return (
    <div className="dt2-root">
      {toolbar && <div className="dt2-toolbar">{toolbar}</div>}

      <div
        ref={containerRef}
        className="dt2-scroll"
        style={{
          maxHeight: virtualization.enabled ? '70vh' : undefined,
          overflow: 'auto',
        }}
      >
        <table className={`dt2 dt2-density-${density}`}>
          <thead className="dt2-head">
            {table.getHeaderGroups().map((hg) => (
              <tr key={hg.id}>
                {hg.headers.map((header) => {
                  const sort = header.column.getIsSorted();
                  const canSort = header.column.getCanSort();
                  return (
                    <th
                      key={header.id}
                      colSpan={header.colSpan}
                      style={{ width: header.getSize() }}
                      onClick={canSort ? header.column.getToggleSortingHandler() : undefined}
                      role={canSort ? 'button' : undefined}
                      className={canSort ? 'dt2-sortable' : ''}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                        {flexRender(header.column.columnDef.header, header.getContext())}
                        {canSort && (sort === 'asc'
                          ? <ChevronUp size={14} />
                          : sort === 'desc'
                            ? <ChevronDown size={14} />
                            : <ChevronsUpDown size={14} style={{ opacity: 0.4 }} />)}
                      </span>
                    </th>
                  );
                })}
              </tr>
            ))}
          </thead>

          <tbody>
            {loading && Array.from({ length: 5 }).map((_, i) => (
              <tr key={`skel-${i}`} className="dt2-skel">
                {columns.map((_c, j) => <td key={j}><span className="dt2-skel-bar" /></td>)}
              </tr>
            ))}

            {!loading && rows.length === 0 && (
              <tr><td colSpan={columns.length} className="dt2-empty">{emptyState}</td></tr>
            )}

            {!loading && !virtualization.enabled && rows.map((row) => (
              <DataTableRow key={row.id} row={row} onRowClick={onRowClick} />
            ))}

            {!loading && virtualization.enabled && virtualRows && (
              <>
                {virtualRows[0] && virtualRows[0].start > 0 && (
                  <tr style={{ height: virtualRows[0].start }} />
                )}
                {virtualRows.map((vr) => {
                  const row = rows[vr.index];
                  return (
                    <DataTableRow
                      key={row.id}
                      row={row}
                      onRowClick={onRowClick}
                      style={{ height: vr.size }}
                    />
                  );
                })}
                {virtualRows.length > 0 && (
                  <tr style={{
                    height: totalSize - (virtualRows[virtualRows.length - 1].end ?? 0),
                  }} />
                )}
              </>
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination footer */}
      {table.getPageCount() > 1 && (
        <div className="dt2-footer">
          <div className="dt2-page-info">
            {table.getState().pagination.pageIndex + 1} / {table.getPageCount()}
            {' · '}
            {rows.length} of {data.length}
          </div>
          <div className="dt2-page-controls">
            <button
              type="button"
              onClick={() => table.previousPage()}
              disabled={!table.getCanPreviousPage()}
              className="dt2-page-btn"
            >‹</button>
            <button
              type="button"
              onClick={() => table.nextPage()}
              disabled={!table.getCanNextPage()}
              className="dt2-page-btn"
            >›</button>
          </div>
        </div>
      )}
    </div>
  );
}

function DataTableRow<TRow>({
  row, onRowClick, style,
}: {
  row: Row<TRow>;
  onRowClick?: (row: TRow) => void;
  style?: React.CSSProperties;
}) {
  return (
    <tr
      onClick={onRowClick ? () => onRowClick(row.original) : undefined}
      className={onRowClick ? 'dt2-row dt2-row-clickable' : 'dt2-row'}
      style={style}
    >
      {row.getVisibleCells().map((cell) => (
        <td key={cell.id}>
          {flexRender(cell.column.columnDef.cell, cell.getContext())}
        </td>
      ))}
    </tr>
  );
}
