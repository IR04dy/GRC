import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { DataTable, type Column } from './DataTable';

// PERF-FE-003: lock the client-side pagination contract on the shared table.
interface Row { id: number; name: string }
const columns: Column<Row>[] = [{ header: 'Name', cell: r => r.name }];
const makeRows = (n: number): Row[] =>
  Array.from({ length: n }, (_, i) => ({ id: i + 1, name: `row-${i + 1}` }));

function renderTable(rows: Row[], pageSize?: number) {
  return render(
    <MemoryRouter>
      <DataTable columns={columns} rows={rows} pageSize={pageSize} />
    </MemoryRouter>,
  );
}

describe('DataTable pagination (PERF-FE-003)', () => {
  it('renders every row and no pager when at/below the page size', () => {
    renderTable(makeRows(100)); // default page size is 100
    expect(screen.getAllByRole('row')).toHaveLength(100 + 1); // + header row
    expect(screen.queryByLabelText('Table pagination')).toBeNull();
  });

  it('paginates and shows a pager when rows exceed the page size', () => {
    renderTable(makeRows(12), 5);
    // 5 body rows + 1 header row visible on page 1.
    expect(screen.getAllByRole('row')).toHaveLength(5 + 1);
    expect(screen.getByText('row-1')).toBeInTheDocument();
    expect(screen.queryByText('row-6')).toBeNull();
    expect(screen.getByLabelText('Table pagination')).toBeInTheDocument();
    expect(screen.getByText('1–5 / 12')).toBeInTheDocument();
  });

  it('advances to the next page and disables Previous on the first page', () => {
    renderTable(makeRows(12), 5);
    const prev = screen.getByLabelText('Previous page');
    const next = screen.getByLabelText('Next page');
    expect(prev).toBeDisabled();

    fireEvent.click(next);
    expect(screen.getByText('row-6')).toBeInTheDocument();
    expect(screen.queryByText('row-1')).toBeNull();
    expect(screen.getByText('6–10 / 12')).toBeInTheDocument();
    expect(prev).not.toBeDisabled();
  });

  it('disables Next on the last page and shows the remainder', () => {
    renderTable(makeRows(12), 5);
    const next = screen.getByLabelText('Next page');
    fireEvent.click(next); // page 2 (6-10)
    fireEvent.click(next); // page 3 (11-12)
    expect(screen.getByText('11–12 / 12')).toBeInTheDocument();
    expect(screen.getByLabelText('Next page')).toBeDisabled();
  });

  it('pageSize=0 disables the cap and renders all rows', () => {
    renderTable(makeRows(250), 0);
    expect(screen.getAllByRole('row')).toHaveLength(250 + 1);
    expect(screen.queryByLabelText('Table pagination')).toBeNull();
  });
});
