import { useCallback, useEffect, useState } from 'react';

/**
 * Shared loader hook for domain-dashboard pages.
 *
 * Replaces the anti-pattern where dashboards used `useState<T>(FALLBACK)`
 * — that pattern showed fake numbers on initial render and silently
 * kept showing them on API failure. With this hook the page knows
 * which state it's in and can show a Skeleton / retry / data UI.
 *
 * Lifecycle:
 *   'loading'  — call in flight, render <Skeleton />
 *   'ready'    — data populated, render full UI
 *   'error'    — call failed, render retry button + errMsg
 *
 * @example
 *   const { data, state, errMsg, retry } = useDashboardLoader(() => bcmApi.stats());
 *   if (state === 'loading') return <Skeleton />;
 *   if (state === 'error') return <Retry onClick={retry} />;
 *   // state === 'ready' && data !== null
 */
export type DashboardLoadState = 'loading' | 'ready' | 'error';

export interface DashboardLoaderResult<T> {
  data: T | null;
  state: DashboardLoadState;
  errMsg: string | null;
  retry: () => void;
}

export function useDashboardLoader<T>(loader: () => Promise<T>): DashboardLoaderResult<T> {
  const [data, setData] = useState<T | null>(null);
  const [state, setState] = useState<DashboardLoadState>('loading');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  const run = useCallback(async () => {
    setState('loading');
    setErrMsg(null);
    try {
      const v = await loader();
      setData(v);
      setState('ready');
    } catch (e) {
      setErrMsg((e as Error)?.message ?? String(e));
      setState('error');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void run(); }, [run]);

  return { data, state, errMsg, retry: run };
}
