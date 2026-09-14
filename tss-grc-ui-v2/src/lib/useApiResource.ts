import { useCallback, useEffect, useState } from 'react';
import { isAuthenticated } from './auth';
import { ApiError } from './api';

interface UseApiResourceState<T> {
  data: T;
  loading: boolean;
  error: string | null;
  /** True when the data on screen comes from the mock fallback (not the live API). */
  isDemo: boolean;
  /**
   * True when the backend answered **403** — the session is valid, the caller is
   * simply not permitted to read this register.
   *
   * <p>JRN-13. A denial used to take the same branch as an outage: substitute the
   * demo corpus and raise `isDemo`, which every consumer renders as
   * *"Demo mode · using sample data"*. So a user denied BCM, CYBER, PRIVACY or
   * TPRM got a **populated, plausible, un-writable register** and a badge that
   * reads as a demo tenant rather than as a permission boundary. The rows were
   * fabricated and the reason they were fabricated was never shown — `error` was
   * set on this path all along, and measured across the tree **0 of the 14 real
   * consumers read it**.
   *
   * <p>A 403 is the one failure that is not an accident, so it is the one failure
   * that must not be papered over with invented data. Everything else — 401, 5xx,
   * a network error, a genuinely empty register — keeps the substitution contract
   * W2-1b pinned deliberately, because "never look broken when the backend is
   * down" is a real product decision and this is not that case.
   */
  denied: boolean;
  reload: () => void;
  /** Demo-mode optimistic update — lets the page mutate the local list when
   *  the backend write fails (typically auth-gated). Real API writes should
   *  reload() instead. */
  replace: (updater: (prev: T) => T) => void;
}

/**
 * Loads a list resource from the backend. If there is no JWT, or if the API call
 * fails, falls back to the supplied mock data and surfaces `isDemo: true` so the
 * UI can show a "Demo mode" badge — except on a **403**, which surfaces
 * `denied: true` and an empty list instead (see {@link UseApiResourceState.denied}).
 *
 * The fetcher is expected to return a Spring `Page<T>` shape; we extract `.content`.
 */
export function useApiList<T>(
  fetcher: () => Promise<{ content: T[] }>,
  mock: T[],
): UseApiResourceState<T[]> {
  const [data, setData]     = useState<T[]>(isAuthenticated() ? [] : mock);
  const [loading, setLoad]  = useState<boolean>(isAuthenticated());
  const [error, setError]   = useState<string | null>(null);
  const [isDemo, setDemo]   = useState<boolean>(!isAuthenticated());
  const [denied, setDenied] = useState<boolean>(false);

  const load = useCallback(async () => {
    if (!isAuthenticated()) {
      setData(mock); setDemo(true); setDenied(false); setLoad(false); return;
    }
    setLoad(true); setError(null); setDenied(false);
    try {
      const page = await fetcher();
      const list = page.content ?? [];
      // If the backend returns no rows, prefer the rich demo set so the UI
      // never looks broken on a fresh tenant. Stay in non-demo mode so the
      // user's writes still hit the real API on next save.
      if (list.length === 0 && mock.length > 0) {
        setData(mock); setDemo(true);
      } else {
        setData(list); setDemo(false);
      }
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Network error';
      setError(msg);
      if (err instanceof ApiError && err.status === 403) {
        // Denied: zero rows and no demo badge. Leaving `data` empty is what keeps
        // fabricated ids out of the table — and out of RowActions, whose ids
        // overlap real ones (F2-167).
        setDenied(true);
        setData([]);
        setDemo(false);
      } else {
        setData(mock);
        setDemo(true);
      }
    } finally {
      setLoad(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { load(); }, [load]);

  const replace = useCallback((updater: (prev: T[]) => T[]) => {
    setData(prev => updater(prev));
  }, []);

  return { data, loading, error, isDemo, denied, reload: load, replace };
}
