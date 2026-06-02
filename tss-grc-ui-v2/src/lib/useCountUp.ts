import { useEffect, useRef, useState } from 'react';

/**
 * useCountUp — animate a numeric value from 0 (or its previous value) to the
 * target on mount and on change, with eased timing. Respects
 * prefers-reduced-motion (snaps to the final value).
 *
 * Pair with the `.tnum` utility so the digits don't jitter mid-count.
 *
 *   const score = useCountUp(stats.posture);
 *   <span className="tnum">{Math.round(score)}</span>
 */
export function useCountUp(
  target: number,
  opts?: { durationMs?: number; decimals?: number; fromZeroOnMount?: boolean },
): number {
  const duration = opts?.durationMs ?? 600;
  const decimals = opts?.decimals ?? 0;
  const fromZeroOnMount = opts?.fromZeroOnMount ?? true;

  const [value, setValue] = useState(() => (fromZeroOnMount ? 0 : target));
  const fromRef = useRef(fromZeroOnMount ? 0 : target);
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    const reduce =
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

    if (reduce || duration <= 0 || !Number.isFinite(target)) {
      setValue(target);
      fromRef.current = target;
      return;
    }

    const from = fromRef.current;
    const delta = target - from;
    if (delta === 0) {
      setValue(target);
      return;
    }

    let start: number | null = null;
    // ease-out cubic — matches the visual feel of --ease-out
    const ease = (t: number) => 1 - Math.pow(1 - t, 3);
    const round = (n: number) => {
      const f = Math.pow(10, decimals);
      return Math.round(n * f) / f;
    };

    // performance.now is safe in the browser; guarded for SSR above.
    const tick = (now: number) => {
      if (start === null) start = now;
      const elapsed = now - start;
      const p = Math.min(1, elapsed / duration);
      setValue(round(from + delta * ease(p)));
      if (p < 1) {
        rafRef.current = requestAnimationFrame(tick);
      } else {
        fromRef.current = target;
      }
    };

    rafRef.current = requestAnimationFrame(tick);
    return () => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target, duration, decimals]);

  return value;
}
