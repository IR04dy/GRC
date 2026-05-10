import { useMemo, useState } from 'react';
import {
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type RowSelectionState,
  type SortingState,
  type VisibilityState,
} from '@tanstack/react-table';
import type { Density } from './types';

interface UseDataTableArgs<TRow> {
  data: TRow[];
  columns: ColumnDef<TRow>[];
  /** Stable row identifier function — required for selection persistence. */
  getRowId: (row: TRow, index: number) => string;
  initialPageSize?: number;
  initialSorting?: SortingState;
  initialColumnVisibility?: VisibilityState;
  enableSelection?: boolean;
  enablePagination?: boolean;
  /** When true, table doesn't paginate client-side — caller handles. */
  manualPagination?: boolean;
}

/**
 * Headless DataTable hook. Wraps `useReactTable` with our default config:
 *   - core row model + sorted + filtered + paginated
 *   - selection state managed inside (caller can read via .getSelectedRows)
 *   - density + column visibility persisted
 *
 * Pure logic — no rendering. Pair with `<DataTable>` for the visual layer or
 * use the returned `table` instance directly for custom layouts.
 */
export function useDataTable<TRow>(args: UseDataTableArgs<TRow>) {
  const {
    data, columns, getRowId,
    initialPageSize = 50,
    initialSorting = [],
    initialColumnVisibility = {},
    enableSelection = false,
    enablePagination = true,
    manualPagination = false,
  } = args;

  const [sorting, setSorting] = useState<SortingState>(initialSorting);
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>(initialColumnVisibility);
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
  const [density, setDensity] = useState<Density>('standard');
  const [pageSize, setPageSize] = useState(initialPageSize);
  const [pageIndex, setPageIndex] = useState(0);

  const table = useReactTable({
    data,
    columns,
    state: { sorting, columnVisibility, rowSelection, pagination: { pageIndex, pageSize } },
    enableRowSelection: enableSelection,
    enableMultiRowSelection: enableSelection,
    onSortingChange: setSorting,
    onColumnVisibilityChange: setColumnVisibility,
    onRowSelectionChange: setRowSelection,
    onPaginationChange: (updater) => {
      const next = typeof updater === 'function'
        ? updater({ pageIndex, pageSize })
        : updater;
      setPageIndex(next.pageIndex);
      setPageSize(next.pageSize);
    },
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: enablePagination && !manualPagination
      ? getPaginationRowModel()
      : undefined,
    getRowId,
    manualPagination,
  });

  const selectedRows = useMemo(
    () => table.getSelectedRowModel().rows.map((r) => r.original),
    [table, rowSelection]                       // eslint-disable-line react-hooks/exhaustive-deps
  );

  return {
    table,
    selectedRows,
    state: { sorting, columnVisibility, rowSelection, density, pageSize, pageIndex },
    setDensity,
    /** Reset all state — useful for "Reset view" button. */
    reset: () => {
      setSorting([]);
      setColumnVisibility({});
      setRowSelection({});
      setDensity('standard');
      setPageSize(initialPageSize);
      setPageIndex(0);
    },
  };
}
