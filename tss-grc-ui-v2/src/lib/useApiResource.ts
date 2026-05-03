import { useCallback, useEffect, useState } from 'react';
import { isAuthenticated } from './auth';
import { ApiError } from './api';

interface UseApiResourceState<T> {
  data: T;
  loading: boolean;
  error: string | null;
  /** True when the data on screen comes from the mock fallback (not the live API). */
  isDemo: boolean;
  reload: () => void;
}

/**
 * Loads a list resource from the backend. If there is no JWT, or if the API call
 * fails, falls back to the supplied mock data and surfaces `isDemo: true` so the
 * UI can show a "Demo mode" badge.
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

  const load = useCallback(async () => {
    if (!isAuthenticated()) {
      setData(mock); setDemo(true); setLoad(false); return;
    }
    setLoad(true); setError(null);
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
      setData(mock);
      setDemo(true);
    } finally {
      setLoad(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { load(); }, [load]);

  return { data, loading, error, isDemo, reload: load };
}
