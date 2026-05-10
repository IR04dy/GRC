// Production mode flag — env-driven with a localStorage override for
// per-session toggling.
//
// "demo" — no backend writes; localStorage is canonical (current state)
// "migrating" — UI is dual-writing during the one-time migration
// "production" — API is canonical, localStorage is offline cache

export type Mode = 'demo' | 'migrating' | 'production';

const MODE_KEY = 'grc.mode.v1';
const MODE_EVENT = 'grc.mode.changed';

// `ImportMetaEnv.VITE_MODE` is declared in `src/vite-env.d.ts` — single source of truth.

/** Default mode from Vite env. Set VITE_MODE=production in `.env.production`. */
function envDefault(): Mode {
  try {
    const v = (import.meta.env?.VITE_MODE ?? '').toLowerCase();
    if (v === 'production') return 'production';
    if (v === 'migrating') return 'migrating';
  } catch { /* SSR / non-Vite context */ }
  return 'demo';
}

export function currentMode(): Mode {
  if (typeof window === 'undefined') return envDefault();
  try {
    const override = window.localStorage.getItem(MODE_KEY);
    if (override === 'demo' || override === 'migrating' || override === 'production') return override;
  } catch { /* ignore */ }
  return envDefault();
}

export function setMode(mode: Mode): void {
  try { window.localStorage.setItem(MODE_KEY, mode); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(MODE_EVENT));
}

export function clearOverride(): void {
  try { window.localStorage.removeItem(MODE_KEY); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(MODE_EVENT));
}

export function subscribeMode(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(MODE_EVENT, handler);
  const storageHandler = (e: StorageEvent) => { if (e.key === MODE_KEY) cb(); };
  window.addEventListener('storage', storageHandler);
  return () => {
    window.removeEventListener(MODE_EVENT, handler);
    window.removeEventListener('storage', storageHandler);
  };
}

export function isDemoMode(): boolean { return currentMode() === 'demo'; }
export function isMigratingMode(): boolean { return currentMode() === 'migrating'; }
export function isProductionMode(): boolean { const m = currentMode(); return m === 'production' || m === 'migrating'; }

export const MODE_LABEL: Record<Mode, { en: string; ar: string }> = {
  demo:        { en: 'Demo (localStorage)',     ar: 'تجريبي (تخزين محلي)' },
  migrating:   { en: 'Migrating',                ar: 'قيد الترحيل' },
  production:  { en: 'Production',               ar: 'إنتاج' },
};

export const MODE_TONE: Record<Mode, 'neutral' | 'warning' | 'success'> = {
  demo:       'neutral',
  migrating:  'warning',
  production: 'success',
};
