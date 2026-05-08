// Per-tenant theming.
//
// When the active organisation has a primaryColor (returned by
// /api/v1/platform/organisations/accessible), we override the global
// --primary-* CSS variables on :root so every component that uses
// `var(--primary)` picks up the tenant's brand colour automatically.
// We also expose --org-primary for the few places that want an explicit
// "this is the active tenant" cue (e.g. the topbar tenant stripe).
//
// On switch, Topbar persists the new colour via setOrgTheme() and reloads
// the page; main.tsx reads it back from sessionStorage and applies it
// before React mounts so there's no flash of unstyled colour.

const STORAGE_KEY = 'grc.org.theme';

export interface OrgTheme {
  primaryColor: string;           // hex, e.g. "#0E5E5E"
  organisationId: number;
  organisationNameEn: string;
  organisationNameAr?: string;
  shortCode?: string;
}

// ─── Read / persist ─────────────────────────────────────────────────

export function getOrgTheme(): OrgTheme | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) as OrgTheme : null;
  } catch { return null; }
}

export function setOrgTheme(theme: OrgTheme): void {
  try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(theme)); } catch { /* ignore */ }
  applyOrgTheme(theme.primaryColor);
}

export function clearOrgTheme(): void {
  try { sessionStorage.removeItem(STORAGE_KEY); } catch { /* ignore */ }
  resetOrgTheme();
}

// ─── Apply / reset ──────────────────────────────────────────────────

/**
 * Override the global --primary-* CSS variables so every component using
 * `var(--primary)` (and its 50/300/500/600/700 ramp) picks up the
 * tenant's brand colour. Idempotent — safe to call repeatedly.
 */
export function applyOrgTheme(primaryColor: string): void {
  if (!primaryColor || !/^#[0-9A-F]{6}$/i.test(primaryColor)) return;
  const root = document.documentElement.style;

  // The base primaryColor lands as --primary-500 (mid). The other shades
  // are derived by lightening/darkening so hover/active states still
  // contrast cleanly. --primary-50 is the "soft" surface tone used in
  // chip-active backgrounds, link underlines, etc.
  const ramp = deriveRamp(primaryColor);
  root.setProperty('--primary-50',  ramp.p50);
  root.setProperty('--primary-100', ramp.p100);
  root.setProperty('--primary-200', ramp.p200);
  root.setProperty('--primary-300', ramp.p300);
  root.setProperty('--primary-400', ramp.p400);
  root.setProperty('--primary-500', ramp.p500);
  root.setProperty('--primary-600', ramp.p600);
  root.setProperty('--primary-700', ramp.p700);
  root.setProperty('--primary-800', ramp.p800);
  root.setProperty('--primary-900', ramp.p900);
  // Aliases that resolve via var() chain don't need an override on their
  // own — once --primary-500 / 600 / 700 change, --primary, --primary-hover,
  // --primary-press, --primary-soft all flow through. But set --org-primary
  // explicitly so components can pin to the raw chosen colour without
  // riding the alias chain.
  root.setProperty('--org-primary', primaryColor);
}

export function resetOrgTheme(): void {
  const root = document.documentElement.style;
  ['--primary-50','--primary-100','--primary-200','--primary-300','--primary-400',
   '--primary-500','--primary-600','--primary-700','--primary-800','--primary-900',
   '--org-primary'].forEach(k => root.removeProperty(k));
}

/** Apply theme from sessionStorage on app startup. Called by main.tsx. */
export function bootOrgTheme(): void {
  const t = getOrgTheme();
  if (t) applyOrgTheme(t.primaryColor);
}

// ─── Colour ramp derivation ─────────────────────────────────────────

interface Ramp {
  p50: string; p100: string; p200: string; p300: string; p400: string;
  p500: string; p600: string; p700: string; p800: string; p900: string;
}

function deriveRamp(hex: string): Ramp {
  const [h, s, l] = hexToHsl(hex);
  // Target lightness levels — picked to mirror the in-house teal palette
  // shape (--primary-50 ≈ 92%, --primary-500 ≈ user-supplied, --primary-900 ≈ 8%).
  const ramp = (lightness: number) => hslToHex(h, s, lightness);
  return {
    p50:  ramp(Math.min(95, l + 38)),
    p100: ramp(Math.min(90, l + 30)),
    p200: ramp(Math.min(82, l + 22)),
    p300: ramp(Math.min(72, l + 12)),
    p400: ramp(Math.min(62, l + 6)),
    p500: hex,
    p600: ramp(Math.max(8, l - 6)),
    p700: ramp(Math.max(5, l - 12)),
    p800: ramp(Math.max(3, l - 20)),
    p900: ramp(Math.max(2, l - 28)),
  };
}

function hexToHsl(hex: string): [number, number, number] {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  let h = 0, s = 0;
  const l = (max + min) / 2;
  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case r: h = ((g - b) / d + (g < b ? 6 : 0)); break;
      case g: h = (b - r) / d + 2; break;
      case b: h = (r - g) / d + 4; break;
    }
    h /= 6;
  }
  return [h * 360, s * 100, l * 100];
}

function hslToHex(h: number, s: number, l: number): string {
  s /= 100; l /= 100;
  const k = (n: number) => (n + h / 30) % 12;
  const a = s * Math.min(l, 1 - l);
  const f = (n: number) => {
    const v = l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
    return Math.round(v * 255).toString(16).padStart(2, '0');
  };
  return `#${f(0)}${f(8)}${f(4)}`;
}
