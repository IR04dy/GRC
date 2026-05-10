// Shared types for the production DataTable v2 (TanStack Table-backed).
//
// Keep this file lightweight — re-export from @tanstack/react-table where
// useful so callers don't need a second import.

import type {
  ColumnDef as TanstackColumnDef,
  RowSelectionState,
  SortingState,
  VisibilityState,
} from '@tanstack/react-table';
import type { ReactNode } from 'react';

/** Column definition. Re-exports TanStack's, narrowed to common types. */
export type ColumnDef<TRow> = TanstackColumnDef<TRow>;

/** Row-level action shown in the kebab menu of each row. */
export interface RowAction<TRow> {
  /** Stable id, used as React key. */
  id: string;
  /** Bilingual label (caller can pass localized string directly). */
  label: string;
  /** Optional icon (Lucide). */
  icon?: ReactNode;
  /** Click handler; receives the row data. */
  onClick: (row: TRow) => void;
  /** Disable for some rows (returns true to disable). */
  disabled?: (row: TRow) => boolean;
  /** Visual emphasis. */
  variant?: 'default' | 'destructive';
}

/** Bulk-action shown in the floating action bar when rows are selected. */
export interface BulkAction<TRow> {
  id: string;
  label: string;
  icon?: ReactNode;
  /** Receives the selected rows. */
  onClick: (rows: TRow[]) => void | Promise<void>;
  variant?: 'default' | 'destructive';
}

/** Selection configuration. */
export interface SelectionConfig<TRow> {
  enabled: boolean;
  onChange?: (rows: TRow[]) => void;
  /** Disable per-row selection. */
  isRowSelectable?: (row: TRow) => boolean;
  bulkActions?: BulkAction<TRow>[];
}

/** Pagination configuration. */
export interface PaginationConfig {
  pageSize?: number;
  pageSizeOptions?: number[];
  /** Server-side pagination — caller manages page index. */
  manual?: boolean;
  totalRows?: number;
  onPageChange?: (pageIndex: number) => void;
  onPageSizeChange?: (pageSize: number) => void;
}

/** Virtualization configuration — recommended for >500 rows. */
export interface VirtualizationConfig {
  enabled: boolean;
  estimatedRowHeight?: number;
  overscan?: number;
}

/** Density options. */
export type Density = 'compact' | 'standard' | 'comfortable';

/** Snapshot-able state for "save as view" support. */
export interface DataTableState {
  sorting: SortingState;
  columnVisibility: VisibilityState;
  rowSelection: RowSelectionState;
  density: Density;
  pageSize: number;
}
