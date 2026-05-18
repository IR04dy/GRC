import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from './api';

/**
 * Generic single-resource loader.
 *
 * <p>Sibling to {@link useApiList} but for any async producer (not just a
 * Spring Page<T>). Captures the {loading, data, error} triplet that every
 * detail-page useState dance was duplicating, plus a reload() hook and a
 * `replace()` for optimistic updates.
 *
 * <p>Errors are surfaced as a string AND held in state so the page can
 * render an inline {@link ErrorState} card. Pages that want a toast on
 * failure can pass {toastOnError: true} and supply a toast push function.
 *
 * <p>Designed so pages can replace this idiom:
 *
 *   const [data, setData] = useState<T|null>(null);
 *   const [loading, setLoading] = useState(true);
 *   const [error, setError] = useState<string|null>(null);
 *   useEffect(() => { (async () => { ... })(); }, []);
 *
 * ...with:
 *
 *   const { data, loading, error, reload } = useLoadable(() => api.get<T>('/...'));
 */

export interface Loadable<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
  /** Optimistic update — mutate the cached value without re-fetching. */
  replace: (updater: (prev: T | null) => T | null) => void;
  /** True after the first successful (or failed) load completes. */
  loaded: boolean;
}

export interface LoadableOpts {
  /** Skip the initial fetch (handy when the call is gated on another value). */
  skip?: boolean;
  /** When the producer throws, do not flip into the error state — useful for
   *  optional sub-resources where 404 just means "not present yet". */
  swallow?: boolean;
}

export function useLoadable<T>(
  producer: () => Promise<T>,
  opts: LoadableOpts = {},
): Loadable<T> {
  const { skip = false, swallow = false } = opts;
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState<boolean>(!skip);
  const [error, setError] = useState<string | null>(null);
  const [loaded, setLoaded] = useState<boolean>(false);

  // Track mount state so a slow producer can't write into a stale component.
  const mountedRef = useRef(true);
  useEffect(() => () => { mountedRef.current = false; }, []);

  // Stable producer ref — pages call useLoadable with an inline arrow each
  // render. Without this ref the reload() callback would change identity
  // every render, breaking memoised consumers.
  const producerRef = useRef(producer);
  producerRef.current = producer;

  const reload = useCallback(async () => {
    if (skip) return;
    setLoading(true);
    setError(null);
    try {
      const result = await producerRef.current();
      if (!mountedRef.current) return;
      setData(result);
    } catch (err) {
      if (!mountedRef.current) return;
      if (swallow) {
        // Treat as "no data" but don't surface a red banner.
        setData(null);
      } else {
        const msg = err instanceof ApiError
          ? (err.status === 401 ? 'You need to sign in again to view this.' : err.message)
          : (err instanceof Error ? err.message : 'Network error');
        setError(msg);
      }
    } finally {
      if (mountedRef.current) {
        setLoading(false);
        setLoaded(true);
      }
    }
  }, [skip, swallow]);

  useEffect(() => { void reload(); }, [reload]);

  const replace = useCallback((updater: (prev: T | null) => T | null) => {
    setData(updater);
  }, []);

  return { data, loading, error, reload, replace, loaded };
}
