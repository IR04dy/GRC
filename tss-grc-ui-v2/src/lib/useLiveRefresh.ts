// useLiveRefresh — Sprint 4 §2.7 dashboard live wiring.
//
// Subscribes to multiple `changed.{type}` STOMP topics and calls a single
// debounced refresh function whenever any of them fires. Returns a `pulse`
// counter that bumps on every incoming event so the UI can flash a "Live"
// indicator without re-rendering the whole tree.
//
// Why debounce? When a bulk import or a CAPA cascade fires 20 broadcasts in
// 200ms, we don't want 20 round-trips to /api/v1/dashboard/stats — one is
// enough. Default 500ms wait, configurable.

import { useEffect, useRef, useState } from 'react';
import { subscribe, currentOrgId } from './realtime';

export interface UseLiveRefreshOptions {
  /** Event kinds to subscribe to, e.g. ['changed.risk', 'changed.incident']. */
  kinds: string[];
  /** Refresh callback — called at most once per `waitMs` after a burst settles. */
  onRefresh: () => void;
  /** Debounce window in ms. Default 500. */
  waitMs?: number;
  /** Disable subscription entirely (e.g. when user is offline / demo mode). */
  enabled?: boolean;
}

export interface UseLiveRefreshResult {
  /** Bumps every time any subscribed topic fires — even before debounce. */
  pulse: number;
  /** True between event arrival and the debounced refresh resolving. */
  refreshing: boolean;
}

export function useLiveRefresh({ kinds, onRefresh, waitMs = 500, enabled = true }: UseLiveRefreshOptions): UseLiveRefreshResult {
  const [pulse, setPulse] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  // Hold the latest onRefresh in a ref so we don't re-subscribe when the
  // caller passes a fresh function each render.
  const onRefreshRef = useRef(onRefresh);
  onRefreshRef.current = onRefresh;

  useEffect(() => {
    if (!enabled) return;
    const orgId = currentOrgId();
    if (!orgId) return;

    let timer: ReturnType<typeof setTimeout> | null = null;
    const unsubs: Array<() => void> = [];

    function trigger() {
      setPulse(p => p + 1);
      setRefreshing(true);
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => {
        try { onRefreshRef.current(); } finally { setRefreshing(false); }
      }, waitMs);
    }

    for (const kind of kinds) {
      const topic = `/topic/org.${orgId}.${kind}`;
      unsubs.push(subscribe(topic, trigger));
    }

    return () => {
      unsubs.forEach(u => u());
      if (timer) clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, waitMs, kinds.join('|')]);

  return { pulse, refreshing };
}
