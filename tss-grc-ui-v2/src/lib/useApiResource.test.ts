import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';

import { useApiList } from './useApiResource';
import * as auth from './auth';
import { ApiError } from './api';

/**
 * W2-1b — the backend-down acceptance test, against the contract the platform actually ships.
 *
 * The plan text asks for "every register → error state, **zero rows**". The shipped behaviour is
 * different and deliberate: on a failed call — and also on a *successful* call that returns no
 * rows — the hook substitutes the page's demo dataset and raises `isDemo: true`, which every
 * consumer renders as a warning "Demo mode" badge and uses to disable live refresh. Measured
 * before writing these tests: **10 of 10 real consumers reference `isDemo`.**
 *
 * <p>So this file pins the real contract rather than asserting the plan's prescription over it.
 * Changing "demo rows + badge" to "empty + error" is a product decision about how the platform
 * presents itself, and it is recorded in the plan for the owner. Overwriting a disclosed,
 * consistently-implemented design because a plan line described it differently would be the
 * F2-40 mistake — mistaking an intended assertion for a defect.
 *
 * <p>What these tests DO enforce is that the disclosure cannot quietly disappear: if a future
 * change stops setting `isDemo` on either substitution path, or starts hiding a real API error,
 * these fail. Paired with the static guard in {@code __tests__/demoFallbackContract.test.ts}
 * (every consumer must reference `isDemo`), that closes the loop W2-1a-i and W2-1a-ii opened.
 */

interface Row { id: number; name: string }
const MOCK: Row[] = [{ id: 901, name: 'demo-row-a' }, { id: 902, name: 'demo-row-b' }];
const LIVE: Row[] = [{ id: 1, name: 'real-row' }];

describe('useApiList — what a register shows when the backend is down', () => {
  beforeEach(() => {
    // Authenticated: the unauthenticated path is a separate, explicitly-demo case.
    vi.spyOn(auth, 'isAuthenticated').mockReturnValue(true);
  });
  afterEach(() => vi.restoreAllMocks());

  it('surfaces the error AND flags demo mode when the API throws', async () => {
    const fetcher = vi.fn().mockRejectedValue(new ApiError(503, 'Service Unavailable'));
    const { result } = renderHook(() => useApiList<Row>(fetcher, MOCK));

    await waitFor(() => expect(result.current.loading).toBe(false));

    // An operator must be able to tell that the backend failed.
    expect(result.current.error).toBe('Service Unavailable');
    expect(result.current.isDemo).toBe(true);   // ← the flag pages render as a "Demo mode" badge
    expect(result.current.data).toEqual(MOCK);  // ← demo rows, disclosed, not an empty table
  });

  it('a network failure is reported, not swallowed into a silent empty list', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('boom'));
    const { result } = renderHook(() => useApiList<Row>(fetcher, MOCK));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBe('Network error');
    expect(result.current.isDemo).toBe(true);
  });

  /**
   * The path worth naming out loud. A register that is *genuinely* empty — a fresh tenant, or one
   * whose rows were deleted — is shown the demo set instead of its own empty state, because the
   * hook prefers "the rich demo set so the UI never looks broken". It is disclosed via `isDemo`,
   * but it does mean an operator cannot distinguish "no incidents" from "demo data" by the table
   * alone; the badge is the only signal. Pinned here so the trade-off is visible in the test suite
   * and not just in a comment inside the hook.
   */
  it('substitutes demo rows even on a SUCCESSFUL empty response, with isDemo set', async () => {
    const fetcher = vi.fn().mockResolvedValue({ content: [] });
    const { result } = renderHook(() => useApiList<Row>(fetcher, MOCK));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBeNull();      // nothing failed
    expect(result.current.isDemo).toBe(true);     // ...but this is not the tenant's data
    expect(result.current.data).toEqual(MOCK);
  });

  it('real rows are shown as real — no demo flag, no substitution', async () => {
    const fetcher = vi.fn().mockResolvedValue({ content: LIVE });
    const { result } = renderHook(() => useApiList<Row>(fetcher, MOCK));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual(LIVE);
    expect(result.current.isDemo).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('an unauthenticated visitor gets demo data without an error', async () => {
    vi.spyOn(auth, 'isAuthenticated').mockReturnValue(false);
    const fetcher = vi.fn();
    const { result } = renderHook(() => useApiList<Row>(fetcher, MOCK));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(fetcher).not.toHaveBeenCalled();   // no pointless call without a token
    expect(result.current.isDemo).toBe(true);
    expect(result.current.error).toBeNull();  // not signed in is not a failure
  });
});

/**
 * JRN-13 — the one failure that must not be answered with invented rows.
 *
 * <p>The block above pins, deliberately, that a *broken* backend still shows the demo corpus: "never
 * look broken on a fresh tenant" is a product decision and W2-1b recorded it for the owner rather
 * than overriding it. None of that reasoning reaches a **403**. A denial is the platform working
 * correctly, the rows it substituted were fabricated, and the badge it raised — *"Demo mode · using
 * sample data"* — told a denied analyst they were looking at a demo tenant rather than at a
 * permission boundary. `error` carried the truth the whole time and **0 of the 9 real consumers
 * read it**, so the fix had to change what the hook *returns*, not only what it records.
 *
 * <p>These tests are the fence around that narrowing: 403 and only 403 leaves the substitution
 * contract, and the four paths the block above pins must keep passing unchanged — which they do,
 * because none of them is a 403. If a later edit widens this to "any error", the 503 and network
 * cases up there go red and say so.
 */
describe('useApiList — a 403 is a permission boundary, not a demo tenant', () => {
  beforeEach(() => { vi.spyOn(auth, 'isAuthenticated').mockReturnValue(true); });
  afterEach(() => vi.restoreAllMocks());

  it('shows zero rows and raises denied — no fabricated register, no demo badge', async () => {
    const fetcher = vi.fn().mockRejectedValue(new ApiError(403, 'Forbidden'));
    const { result } = renderHook(() => useApiList<Row>(fetcher, MOCK));

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.denied).toBe(true);
    expect(result.current.data).toEqual([]);      // ← the whole point: nothing invented on screen
    expect(result.current.isDemo).toBe(false);    // ← and not dressed up as a demo tenant
    expect(result.current.error).toBe('Forbidden');
  });

  it('leaves every other failure on the substitution contract', async () => {
    for (const err of [new ApiError(503, 'Service Unavailable'), new ApiError(500, 'Server Error'),
                       new ApiError(401, 'Unauthorized'), new Error('boom')]) {
      const { result } = renderHook(() => useApiList<Row>(vi.fn().mockRejectedValue(err), MOCK));
      await waitFor(() => expect(result.current.loading).toBe(false));
      expect(result.current.denied, `${err} must not be treated as a denial`).toBe(false);
      expect(result.current.isDemo).toBe(true);
      expect(result.current.data).toEqual(MOCK);
    }
  });

  it('a successful load is not denied, and denied clears when a reload succeeds', async () => {
    const fetcher = vi.fn()
      .mockRejectedValueOnce(new ApiError(403, 'Forbidden'))
      .mockResolvedValue({ content: LIVE });
    const { result } = renderHook(() => useApiList<Row>(fetcher, MOCK));

    await waitFor(() => expect(result.current.denied).toBe(true));
    result.current.reload();
    await waitFor(() => expect(result.current.loading).toBe(false));

    // A permission that was granted between loads must not leave a stale denial banner up.
    expect(result.current.denied).toBe(false);
    expect(result.current.data).toEqual(LIVE);
  });

  it('an unauthenticated visitor is demo, never denied — no call was made to be refused', async () => {
    vi.spyOn(auth, 'isAuthenticated').mockReturnValue(false);
    const { result } = renderHook(() => useApiList<Row>(vi.fn(), MOCK));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.denied).toBe(false);
    expect(result.current.isDemo).toBe(true);
  });
});
