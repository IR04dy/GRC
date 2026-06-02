import { useEffect, useRef, useState, type ReactNode } from 'react';
import { ShieldCheck } from 'lucide-react';

/**
 * CountdownRing — a live, self-depleting regulatory-deadline clock.
 *
 * Drives off an absolute deadline (epoch ms) against a window (e.g. the PDPL
 * 72-hour SDAIA breach-notification window). The arc depletes, the centre
 * ticks HH:MM:SS, and the colour crosses success → warning → danger as the
 * deadline nears. Once notified, a green shield seal replaces the clock.
 *
 * Reduced-motion: still shows the correct remaining time, just without the
 * per-second visual urgency. RTL-aware (arc mirrors).
 */
export interface CountdownRingProps {
  /** Absolute deadline, epoch milliseconds. */
  deadlineMs: number;
  /** Window size in ms the ring represents full (e.g. 72h). */
  totalMs: number;
  /** When set, the clock is replaced by a "notified / sealed" state. */
  sealed?: boolean;
  sealLabel?: ReactNode;
  size?: number;
  thickness?: number;
  /** Tick every second. */
  live?: boolean;
  caption?: ReactNode;
  rtl?: boolean;
}

function fmt(ms: number): string {
  const neg = ms < 0;
  const s = Math.floor(Math.abs(ms) / 1000);
  const hh = Math.floor(s / 3600);
  const mm = Math.floor((s % 3600) / 60);
  const ss = s % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${neg ? '-' : ''}${pad(hh)}:${pad(mm)}:${pad(ss)}`;
}

function arcPath(cx: number, cy: number, r: number, a0: number, a1: number): string {
  const p = (deg: number): [number, number] => {
    const t = ((-90 + deg) * Math.PI) / 180;
    return [cx + r * Math.cos(t), cy + r * Math.sin(t)];
  };
  const [x0, y0] = p(a0);
  const [x1, y1] = p(a1);
  const large = Math.abs(a1 - a0) > 180 ? 1 : 0;
  const sweep = a1 > a0 ? 1 : 0;
  return `M ${x0.toFixed(2)} ${y0.toFixed(2)} A ${r} ${r} 0 ${large} ${sweep} ${x1.toFixed(2)} ${y1.toFixed(2)}`;
}

export function CountdownRing({
  deadlineMs,
  totalMs,
  sealed = false,
  sealLabel,
  size = 168,
  thickness = 13,
  live = true,
  caption,
  rtl = false,
}: CountdownRingProps) {
  const [now, setNow] = useState(() => Date.now());
  const reduceRef = useRef(
    typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches,
  );

  useEffect(() => {
    if (sealed || !live) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [sealed, live]);

  const cx = size / 2;
  const cy = size / 2;
  const r = (size - thickness) / 2 - 2;

  if (sealed) {
    return (
      <div style={{ position: 'relative', width: size, height: size }}
        role="img" aria-label={typeof sealLabel === 'string' ? sealLabel : 'Notified'}>
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
          <circle cx={cx} cy={cy} r={r} fill="none" stroke="var(--success)" strokeOpacity={0.25} strokeWidth={thickness} />
          <circle cx={cx} cy={cy} r={r} fill="var(--success)" fillOpacity={0.08} />
        </svg>
        <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 4 }}>
          <ShieldCheck size={Math.round(size * 0.3)} color="var(--success)" />
          {sealLabel && <span style={{ font: '700 11px var(--font-sans)', color: 'var(--success)', textAlign: 'center' }}>{sealLabel}</span>}
        </div>
      </div>
    );
  }

  const remaining = deadlineMs - now;
  const frac = Math.max(0, Math.min(1, remaining / totalMs));
  const overdue = remaining <= 0;
  // Colour: >50% ok, >1/6 of window (e.g. 12h/72h) warning, else danger.
  const color = overdue ? 'var(--danger)'
    : frac > 0.5 ? 'var(--success)'
    : frac > 1 / 6 ? 'var(--warning)'
    : 'var(--danger)';

  const sweep = 360;
  const start = 0;
  const valueEnd = start + frac * sweep * (rtl ? -1 : 1);
  const urgent = !overdue && frac <= 1 / 6;

  return (
    <div style={{ position: 'relative', width: size, height: size }}
      role="img"
      aria-label={`${overdue ? 'overdue by' : 'remaining'} ${fmt(remaining)}`}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}
        className={urgent && !reduceRef.current ? 'viz-pulse' : undefined} aria-hidden="true">
        {/* Track */}
        <circle cx={cx} cy={cy} r={r} fill="none" stroke="var(--surface-3)" strokeWidth={thickness} />
        {/* Remaining arc */}
        {!overdue && frac > 0 && (
          <path d={arcPath(cx, cy, r, start, valueEnd)} fill="none" stroke={color}
            strokeWidth={thickness} strokeLinecap="round" />
        )}
        {overdue && (
          <circle cx={cx} cy={cy} r={r} fill="none" stroke="var(--danger)" strokeWidth={thickness} strokeOpacity={0.85} />
        )}
      </svg>
      <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2 }}>
        <span className="tnum" style={{ font: `800 ${Math.round(size * 0.16)}px/1 var(--font-mono)`, color, letterSpacing: '0.01em' }}>
          {fmt(remaining)}
        </span>
        <span style={{ font: '700 9px var(--font-sans)', color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.1em', textAlign: 'center' }}>
          {overdue ? (caption ?? 'OVERDUE') : caption}
        </span>
      </div>
    </div>
  );
}
