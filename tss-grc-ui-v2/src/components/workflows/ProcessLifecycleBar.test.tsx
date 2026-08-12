import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Activity } from 'lucide-react';
import {
  ProcessLifecycleBar, lifecycleHealth, rankLifecycles,
  type ProcessLifecycle,
} from './ProcessLifecycleBar';

/**
 * F2-197 — the behaviour that makes deleting the catalogue's 112 hard-coded counts
 * an honest change rather than a cosmetic one.
 *
 * <p>The catalogue in `lib/moduleLifecycles.ts` declared `activeCount` and
 * `overdueCount` for all 56 processes as literals, so 11 module pages rendered 130
 * "active" items and 25 red role="status" "N items need attention" badges that no
 * server had ever produced. The trap in removing them is the stripe expression:
 * `overdue > 0 ? danger : active > 0 ? accent : success` turns every tile
 * `--success` green once the numbers are gone, which swaps an invented figure for
 * an invented all-clear. So the load-bearing assertions here are the ones about
 * **unmeasured**, not the ones about zero.
 */

const base: ProcessLifecycle = {
  name: 'Risk Treatment Plan',
  Icon: Activity,
  primary: { label: 'Plan', href: '/risk' },
};

function lc(over: Partial<ProcessLifecycle>): ProcessLifecycle {
  return { ...base, ...over };
}

describe('lifecycleHealth', () => {
  it('separates a real zero from an absent count', () => {
    // The whole finding in one assertion pair. `clear` is an assurance ("nothing in
    // flight, nothing late") and it needs both numbers to have arrived.
    expect(lifecycleHealth(lc({ activeCount: 0, overdueCount: 0 }))).toBe('clear');
    expect(lifecycleHealth(lc({}))).toBe('unmeasured');
  });

  it('refuses to call a tile clear when only half of it was measured', () => {
    // A measured zero beside an unknown is not health: the platform may simply not
    // compute overdue for this process, which is true of 30 of the 56 catalogued SLAs
    // ("Quarterly", "CFO escalation", "Regulator-specific" — free text, no due date).
    expect(lifecycleHealth(lc({ activeCount: 0 }))).toBe('unmeasured');
    expect(lifecycleHealth(lc({ overdueCount: 0 }))).toBe('unmeasured');
  });

  it('reports overdue ahead of active, and both ahead of clear', () => {
    expect(lifecycleHealth(lc({ activeCount: 7, overdueCount: 2 }))).toBe('overdue');
    expect(lifecycleHealth(lc({ activeCount: 0, overdueCount: 2 }))).toBe('overdue');
    expect(lifecycleHealth(lc({ activeCount: 7, overdueCount: 0 }))).toBe('active');
    expect(lifecycleHealth(lc({ activeCount: 7 }))).toBe('active');
  });
});

describe('rankLifecycles', () => {
  it('never lets an unmeasured process outrank a measured one', () => {
    const unmeasured = lc({ name: 'unmeasured' });
    const clear      = lc({ name: 'clear',  activeCount: 0, overdueCount: 0 });
    const active     = lc({ name: 'active', activeCount: 3, overdueCount: 0 });
    const overdue    = lc({ name: 'overdue', activeCount: 1, overdueCount: 1 });
    // Declaration order is deliberately the reverse of the wanted order, because
    // Array.prototype.sort is stable: a comparator that returned 0 for these would
    // pass an "is the first one overdue?" test purely by input order.
    const ranked = rankLifecycles([unmeasured, clear, active, overdue]);
    expect(ranked.map(r => r.name)).toEqual(['overdue', 'active', 'clear', 'unmeasured']);
  });

  it('orders within a health band by volume', () => {
    const ranked = rankLifecycles([
      lc({ name: 'a', activeCount: 1, overdueCount: 1 }),
      lc({ name: 'b', activeCount: 1, overdueCount: 9 }),
      lc({ name: 'c', activeCount: 4, overdueCount: 0 }),
      lc({ name: 'd', activeCount: 9, overdueCount: 0 }),
    ]);
    expect(ranked.map(r => r.name)).toEqual(['b', 'a', 'd', 'c']);
  });

  it('does not mutate its argument', () => {
    const input = [lc({ name: 'x' }), lc({ name: 'y', activeCount: 5 })];
    rankLifecycles(input);
    expect(input.map(r => r.name)).toEqual(['x', 'y']);
  });
});

function renderBar(lifecycles: ProcessLifecycle[], compact = true) {
  return render(
    <MemoryRouter>
      <ProcessLifecycleBar title="Processes" lifecycles={lifecycles} lang="en" compact={compact} />
    </MemoryRouter>,
  );
}

describe('the compact rail', () => {
  it('promotes no "Recommended next action" when nothing is measured', () => {
    // UX-006 promoted `ranked[0]` unconditionally. With the catalogue's numbers gone
    // that would still crown one tile per module — recommending work whose existence
    // nobody has established.
    renderBar([lc({ name: 'One' }), lc({ name: 'Two' }), lc({ name: 'Three' })]);
    expect(screen.queryByTitle(/Recommended next action/)).toBeNull();
    expect(screen.getByTitle(/^One —/)).toBeTruthy();
  });

  it('promotes nothing when every measured process is clear', () => {
    // A recommendation needs something to act on; 0 in flight and 0 late is not it.
    renderBar([
      lc({ name: 'One', activeCount: 0, overdueCount: 0 }),
      lc({ name: 'Two', activeCount: 0, overdueCount: 0 }),
    ]);
    expect(screen.queryByTitle(/Recommended next action/)).toBeNull();
  });

  it('promotes the breaching process when one is really measured as breaching', () => {
    renderBar([
      lc({ name: 'Quiet', activeCount: 0, overdueCount: 0 }),
      lc({ name: 'Late',  activeCount: 2, overdueCount: 1 }),
    ]);
    expect(screen.getByTitle(/Recommended next action: Late/)).toBeTruthy();
  });

  it('discloses "not computed" in the tooltip of an unmeasured chip', () => {
    renderBar([lc({ name: 'Solo' })]);
    expect(screen.getByTitle(/no counter yet/i)).toBeTruthy();
  });

  it('shows no attention badge and no counter for an unmeasured process', () => {
    renderBar([lc({ name: 'Solo' })]);
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('still shows the attention badge when a real breach count arrives', () => {
    renderBar([lc({ name: 'Solo', activeCount: 4, overdueCount: 3 })]);
    expect(screen.getByRole('status').getAttribute('aria-label')).toBe('3 items need attention');
  });

  it('keeps the chip budget at five whether or not a chip is promoted', () => {
    const seven = Array.from({ length: 7 }, (_, i) => lc({ name: `P${i}` }));
    renderBar(seven);
    // Nothing measured → no promotion → 5 quiet chips + "+2".
    expect(screen.getByText('+2')).toBeTruthy();

    const withOne = [lc({ name: 'Hot', activeCount: 1, overdueCount: 1 }), ...seven];
    renderBar(withOne);
    // 1 promoted + 4 quiet + "+3" — the budget does not silently grow.
    expect(screen.getByText('+3')).toBeTruthy();
  });
});

describe('the expanded tile', () => {
  it('renders an em-dash with an accessible hint instead of a hard 0', () => {
    // The counter chip used to render `{active}` unconditionally, so an absent count
    // printed `0` — indistinguishable from a measured empty process.
    renderBar([lc({ name: 'Solo' })], false);
    const dash = screen.getByLabelText(/no counter yet/i);
    expect(dash.textContent).toBe('—');
  });

  it('renders a genuine zero as 0, not as an em-dash', () => {
    // Mirror of Kpi's `isNoData = value == null` rule: `!value` here would hide a
    // measured empty register behind the placeholder built to expose ignorance.
    renderBar([lc({ name: 'Solo', activeCount: 0, overdueCount: 0 })], false);
    expect(screen.getByText('0')).toBeTruthy();
    expect(screen.queryByLabelText(/no counter yet/i)).toBeNull();
  });
});
