import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Activity } from 'lucide-react';
import { Kpi } from './Kpi';

/**
 * F2-195 — the presentation contract three rewired KPI tiles now depend on.
 *
 * <p>The slice replaced nine fabricated KPI values with real ones, and for the three metrics the
 * platform cannot compute today it passes `value={null}` with an `emptyHint`. That choice is only
 * honest if the component actually does what its docblock says: render the em-dash treatment with
 * the hint as an accessible tooltip, and — critically — keep a **genuine zero** as a number.
 *
 * <p>🔑 The zero case is the one worth a test. The real NCA ECC score measured **0%** (0 of 29
 * controls implemented), and the whole point of that fix is that the tile now says 0% rather than
 * the fabricated 87%. If `isNoData` were `!value` instead of `value == null`, a true 0 would render
 * as an em-dash and the finding would be re-hidden by the fix meant to expose it — the failure would
 * look like a rendering nicety and be a suppressed regulatory number.
 */

function renderKpi(props: Partial<React.ComponentProps<typeof Kpi>> = {}) {
  return render(
    <MemoryRouter>
      <Kpi Icon={Activity} label="Metric" value={1} {...props} />
    </MemoryRouter>,
  );
}

describe('F2-195 — Kpi tells "not measured" apart from "measured zero"', () => {
  it('a null value renders the em-dash, with the hint as its accessible name', () => {
    renderKpi({ value: null, emptyHint: 'Not computed — nothing to score' });
    const dash = screen.getByLabelText('Not computed — nothing to score');
    expect(dash.textContent).toBe('—');
    expect(dash.className).toContain('empty-inline-compact');
    // The tooltip and the accessible name must both carry it: sighted users hover, others do not.
    expect(dash.getAttribute('title')).toBe('Not computed — nothing to score');
  });

  it('a genuine zero renders as 0, NOT as the em-dash', () => {
    // The regression this guards: `0` is falsy. `isNoData` must be `value == null`.
    renderKpi({ value: '0%', label: 'NCA ECC Score' });
    expect(screen.getByText('0%')).toBeTruthy();
    expect(screen.queryByText('—')).toBeNull();
  });

  it('a numeric zero also survives, since counts arrive as numbers', () => {
    renderKpi({ value: 0, label: 'Critical Processes' });
    expect(screen.getByText('0')).toBeTruthy();
    expect(screen.queryByText('—')).toBeNull();
  });

  it('a real value is rendered verbatim', () => {
    renderKpi({ value: '4.5d', label: 'Mean TTR' });
    expect(screen.getByText('4.5d')).toBeTruthy();
  });

  it('undefined is treated as not-measured too', () => {
    // `evidenceCount` starts undefined-ish while the fetch is in flight; during that window the
    // tile must not read as a measurement of zero evidence items.
    renderKpi({ value: undefined, emptyHint: 'Loading' });
    expect(screen.getByLabelText('Loading').textContent).toBe('—');
  });
});
