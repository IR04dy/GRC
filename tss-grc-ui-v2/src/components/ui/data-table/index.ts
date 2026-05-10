// Barrel re-exports for the v2 DataTable.
//
// Migration plan: pages import `DataTableV2` here. Once all 30+ list pages
// are migrated off the legacy 74-line `DataTable` (`../DataTable.tsx`), this
// barrel becomes the canonical `DataTable` and the old file is deleted.

export { DataTableV2 } from './DataTableV2';
export { useDataTable } from './useDataTable';
export type {
  ColumnDef,
  RowAction,
  BulkAction,
  SelectionConfig,
  PaginationConfig,
  VirtualizationConfig,
  Density,
  DataTableState,
} from './types';
