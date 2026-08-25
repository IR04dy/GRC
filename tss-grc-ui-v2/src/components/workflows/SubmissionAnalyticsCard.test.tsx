import { afterEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { I18nProvider } from '../../lib/i18n';
import type { WorkflowSubmissionDto } from '../../lib/api';
import { SubmissionAnalyticsCard, computeTtrStats } from './SubmissionAnalyticsCard';

/**
 * F2-358 — "Open for review" is a count of pending submissions, not a by-product of the
 * time-to-review mean.
 *
 * <p>`ttrStats` computed BOTH the mean review time (which needs `reviewed.length > 0` as its
 * denominator) and `openCount` (SUBMITTED | UNDER_REVIEW — independent of any review) in one
 * `useMemo`, with `openCount` placed AFTER the mean's `if (reviewed.length === 0) return … `
 * early return. So on a fresh tenant — or any view where nothing has been reviewed yet — the
 * "Open for review" KPI read 0 / "No pending submissions" while submissions were genuinely
 * pending. The card only hides itself when `items.length === 0`, so this state renders.
 *
 * <p>An under-report in the flattering direction (F2-193 / F2-195 / F2-210): a reviewer's queue
 * says "nothing to do" when there is. The load-bearing legs are `computeTtrStats` (the pure
 * arithmetic) and the render leg proving the KPI actually reads `openCount`.
 */

function sub(over: Partial<WorkflowSubmissionDto>): WorkflowSubmissionDto {
  return {
    id: 1,
    workflowId: 'wf-x',
    workflowModule: 'RISK',
    titleEn: 'Sub',
    status: 'SUBMITTED',
    payloadJson: '{}',
    submittedAt: '2026-08-01T00:00:00Z',
    ...over,
  };
}

describe('computeTtrStats', () => {
  it('counts pending submissions even when NOTHING has been reviewed (the F2-358 defect)', () => {
    // 5 pending + 1 closed, none reviewed. openCount must survive the mean's divide-by-zero guard.
    const items = [
      sub({ id: 1, status: 'SUBMITTED' }),
      sub({ id: 2, status: 'SUBMITTED' }),
      sub({ id: 3, status: 'SUBMITTED' }),
      sub({ id: 4, status: 'UNDER_REVIEW' }),
      sub({ id: 5, status: 'UNDER_REVIEW' }),
      sub({ id: 6, status: 'CLOSED' }),
    ];
    const stats = computeTtrStats(items);
    expect(stats.openCount).toBe(5);   // pre-fix: 0, swept into the early return
    expect(stats.avgHours).toBeNull(); // the mean genuinely has no denominator yet
    expect(stats.count).toBe(0);
  });

  it('counts only SUBMITTED and UNDER_REVIEW as open, regardless of reviews', () => {
    const items = [
      sub({ id: 1, status: 'SUBMITTED' }),
      sub({ id: 2, status: 'UNDER_REVIEW' }),
      sub({ id: 3, status: 'APPROVED', reviewedAt: '2026-08-02T00:00:00Z' }),
      sub({ id: 4, status: 'REJECTED', reviewedAt: '2026-08-02T00:00:00Z' }),
      sub({ id: 5, status: 'CLOSED' }),
      sub({ id: 6, status: 'DRAFT' }),
    ];
    expect(computeTtrStats(items).openCount).toBe(2);
  });

  it('reports a genuine zero when no submission is pending', () => {
    const items = [sub({ id: 1, status: 'CLOSED' }), sub({ id: 2, status: 'APPROVED' })];
    expect(computeTtrStats(items).openCount).toBe(0);
  });

  it('averages reviewedAt − submittedAt in hours over the reviewed set only', () => {
    const items = [
      sub({ id: 1, status: 'APPROVED', submittedAt: '2026-08-01T00:00:00Z', reviewedAt: '2026-08-01T10:00:00Z' }), // 10h
      sub({ id: 2, status: 'APPROVED', submittedAt: '2026-08-01T00:00:00Z', reviewedAt: '2026-08-01T20:00:00Z' }), // 20h
      sub({ id: 3, status: 'SUBMITTED' }), // pending — excluded from the mean, included in openCount
    ];
    const stats = computeTtrStats(items);
    expect(stats.avgHours).toBe(15);
    expect(stats.count).toBe(2);
    expect(stats.openCount).toBe(1);
  });
});

describe('the "Open for review" KPI reads openCount', () => {
  afterEach(() => localStorage.clear());

  function renderCard(items: WorkflowSubmissionDto[]) {
    localStorage.setItem('grc.lang', 'en');
    return render(
      <MemoryRouter>
        <I18nProvider>
          <SubmissionAnalyticsCard items={items} />
        </I18nProvider>
      </MemoryRouter>,
    );
  }

  it('shows the pending count (not 0) when nothing has been reviewed yet', async () => {
    // Pre-fix this renders "0" / "No pending submissions" — the whole finding, on the surface.
    const items = [
      sub({ id: 1, status: 'SUBMITTED' }),
      sub({ id: 2, status: 'SUBMITTED' }),
      sub({ id: 3, status: 'SUBMITTED' }),
      sub({ id: 4, status: 'UNDER_REVIEW' }),
      sub({ id: 5, status: 'UNDER_REVIEW' }),
      sub({ id: 6, status: 'CLOSED' }),   // items.length 6 ≠ openCount 5, so "5" is unambiguous
    ];
    renderCard(items);
    // openCount = 5; distinct from items.length (6, the header badge) and the reviewed count (0).
    expect(await screen.findByText('5')).toBeTruthy();
    expect(screen.getByText('awaiting reviewer decision')).toBeTruthy();
    expect(screen.queryByText('No pending submissions')).toBeNull();
  });
});
