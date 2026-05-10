import { useEffect, useState } from 'react';

/**
 * Visual indicator that data is being kept current via WebSocket. Pulses
 * green for ~1.5s on every incoming event, then settles to a quiet "live"
 * state. If you pass `enabled={false}` it shows a neutral dot meaning
 * "polling only / static snapshot".
 *
 * Use next to a page title or KPI strip to give users confidence that
 * what they see is up-to-the-second.
 */
export interface LivePulseProps {
  /** Bump this counter from a parent (e.g. useLiveRefresh().pulse) on each event. */
  pulse?: number;
  /** Override the connected state — defaults to true. */
  enabled?: boolean;
  /** Custom labels (default: "Live" / "Offline"). */
  labelEn?: string;
  labelAr?: string;
  /** Direction — pass 'rtl' or 'ltr'. Defaults to inheriting from <html dir>. */
  dir?: 'ltr' | 'rtl';
}

export function LivePulse({ pulse = 0, enabled = true, labelEn = 'Live', labelAr = 'مباشر', dir }: LivePulseProps) {
  const [flash, setFlash] = useState(false);
  const isAr = (dir ?? (typeof document !== 'undefined' ? document.documentElement.dir : 'ltr')) === 'rtl';

  useEffect(() => {
    if (pulse === 0) return;
    setFlash(true);
    const t = setTimeout(() => setFlash(false), 1500);
    return () => clearTimeout(t);
  }, [pulse]);

  const tone = enabled
    ? (flash ? 'var(--success-fg, #16a34a)' : 'var(--success-fg, #16a34a)')
    : 'var(--ink-3, #94a3b8)';

  return (
    <span
      aria-live="polite"
      title={enabled ? (isAr ? 'تحديثات لحظية مباشرة' : 'Streaming live updates') : (isAr ? 'لقطة ثابتة' : 'Static snapshot')}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 6,
        padding: '2px 8px', borderRadius: 'var(--r-pill, 999px)',
        background: enabled ? 'rgba(22, 163, 74, 0.10)' : 'var(--surface-2)',
        border: `1px solid ${enabled ? 'rgba(22, 163, 74, 0.25)' : 'var(--border)'}`,
        color: tone, fontSize: 11, fontWeight: 600,
        transition: 'background var(--dur-fast, 200ms) ease, border-color var(--dur-fast, 200ms) ease',
      }}>
      <span
        style={{
          width: 8, height: 8, borderRadius: '50%',
          background: tone,
          animation: enabled ? `live-pulse-${flash ? 'fast' : 'slow'} 1.5s infinite` : 'none',
          boxShadow: flash ? `0 0 0 4px rgba(22,163,74,0.18)` : 'none',
          transition: 'box-shadow var(--dur-fast, 200ms) ease',
        }}
      />
      {isAr ? labelAr : labelEn}
      <style>{`
        @keyframes live-pulse-slow {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.55; }
        }
        @keyframes live-pulse-fast {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.6; transform: scale(1.25); }
        }
      `}</style>
    </span>
  );
}
