// White-label configuration — Track A (sales-readiness).
//
// Lets a tenant override branding without forking the build:
//   • logo (data URL — uploaded once, sized to 80×80)
//   • primary colour (CSS hex; drives --primary + --primary-50 + --primary-700)
//   • accent colour (drives --accent-* family)
//   • organisation display name (for the cover page + login + emails)
//   • custom domain (recorded only — DNS routing happens on infra side)
//   • bilingual tagline (board pack + login subtitle)
//
// Stored in the persistence adapter so it survives demo→prod migration.
// Applied by injecting CSS variables into <html> on every load + on every
// change (storage event for multi-tab sync).

export interface WhiteLabelConfig {
  /** Display name shown on cover pages, emails, login. */
  organisationNameEn: string;
  organisationNameAr: string;
  /** Bilingual tagline. */
  taglineEn?: string;
  taglineAr?: string;
  /** Logo as a data URL (base64 image). Empty = use default ShieldCheck. */
  logoDataUrl?: string;
  /** Hex colour for the primary brand. */
  primaryHex: string;
  /** Hex colour for the accent (used on KPIs, action chips). */
  accentHex: string;
  /** Custom domain (e.g. "grc.aramco.com"). Recorded only. */
  customDomain?: string;
  /** When the configuration was last saved. */
  updatedAt?: string;
}

// White-label config is scoped per organisation so an admin customising
// Aramco's branding doesn't change what STC Bank sees. The storage key
// is `grc.whitelabel.v2.org<id>` for the active tenant; pre-login or
// when no org is selected, falls through to "global" (the login page,
// which has no tenant context yet).
//
// `grc.whitelabel.v1` was the legacy global key — when present we migrate
// it once to the current org's slot so an admin who previously customised
// (back when whitelabel was global) doesn't lose their settings.
const LEGACY_KEY = 'grc.whitelabel.v1';
const KEY_PREFIX = 'grc.whitelabel.v2.org';
const EVENT = 'grc.whitelabel.changed';

function currentOrgId(): string {
  if (typeof window === 'undefined') return 'global';
  try {
    const raw = window.sessionStorage.getItem('grc.user');
    if (!raw) return 'global';
    const u = JSON.parse(raw);
    return u?.organisationId != null ? String(u.organisationId) : 'global';
  } catch { return 'global'; }
}

function storageKey(): string {
  return `${KEY_PREFIX}${currentOrgId()}`;
}

const DEFAULT_CONFIG: WhiteLabelConfig = {
  organisationNameEn: 'TSS GRC Platform',
  organisationNameAr: 'منصة الحوكمة والمخاطر والامتثال',
  taglineEn: 'Saudi-first GRC platform',
  taglineAr: 'منصة الحوكمة والمخاطر والامتثال — صناعة سعودية',
  // Brand palette from styles/tokens.css. Previously these were
  // #4f46e5 (indigo) + #14b8a6 (emerald) which silently overwrote the
  // CSS tokens at runtime → every primary-tinted chip / badge /
  // hover-fill rendered as royal-indigo instead of TSS teal.
  primaryHex: '#0E5E5E',
  accentHex: '#C8932A',
};

export function loadWhiteLabel(): WhiteLabelConfig {
  if (typeof window === 'undefined') return DEFAULT_CONFIG;
  const key = storageKey();
  try {
    let raw = window.localStorage.getItem(key);
    // One-shot migration from the legacy global key. We only do this for
    // the first authenticated tenant that asks; the second tenant gets a
    // clean default config.
    if (!raw) {
      const legacy = window.localStorage.getItem(LEGACY_KEY);
      if (legacy && currentOrgId() !== 'global') {
        window.localStorage.setItem(key, legacy);
        window.localStorage.removeItem(LEGACY_KEY);
        raw = legacy;
      }
    }
    if (!raw) return DEFAULT_CONFIG;
    const parsed = JSON.parse(raw);
    // Migration: an earlier build seeded indigo/emerald defaults. Strip
    // those exact values so the user picks up the canonical TSS teal +
    // gold from DEFAULT_CONFIG instead of staying stuck on the old
    // palette. Custom colours other than the legacy pair are preserved.
    const LEGACY_PRIMARY = '#4f46e5';
    const LEGACY_ACCENT  = '#14b8a6';
    const migrated = { ...parsed };
    if (typeof migrated.primaryHex === 'string'
        && migrated.primaryHex.toLowerCase() === LEGACY_PRIMARY) {
      delete migrated.primaryHex;
    }
    if (typeof migrated.accentHex === 'string'
        && migrated.accentHex.toLowerCase() === LEGACY_ACCENT) {
      delete migrated.accentHex;
    }
    return { ...DEFAULT_CONFIG, ...migrated };
  } catch { return DEFAULT_CONFIG; }
}

export function saveWhiteLabel(config: Partial<WhiteLabelConfig>): WhiteLabelConfig {
  const current = loadWhiteLabel();
  const next: WhiteLabelConfig = {
    ...current, ...config,
    updatedAt: new Date().toISOString(),
  };
  try { window.localStorage.setItem(storageKey(), JSON.stringify(next)); } catch { /* ignore */ }
  applyToDom(next);
  window.dispatchEvent(new CustomEvent(EVENT));
  return next;
}

export function resetWhiteLabel(): void {
  try { window.localStorage.removeItem(storageKey()); } catch { /* ignore */ }
  applyToDom(DEFAULT_CONFIG);
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function subscribeWhiteLabel(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  // localStorage event fires across tabs; only react to changes for the
  // active tenant (and the legacy key during the migration window).
  const storageHandler = (e: StorageEvent) => {
    const key = storageKey();
    if (e.key === key || e.key === LEGACY_KEY) cb();
  };
  window.addEventListener('storage', storageHandler);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storageHandler);
  };
}

// ─── DOM application ───────────────────────────────────────────────────────

/** Apply colour overrides to the <html> element. Idempotent + cheap. */
export function applyToDom(config: WhiteLabelConfig = loadWhiteLabel()): void {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  // Compute lighter / darker variants from the base hex.
  const primaryBase = config.primaryHex;
  const primary50 = mixWithWhite(primaryBase, 0.92);
  const primary100 = mixWithWhite(primaryBase, 0.85);
  const primary700 = mixWithBlack(primaryBase, 0.20);
  root.style.setProperty('--primary', primaryBase);
  root.style.setProperty('--primary-50', primary50);
  root.style.setProperty('--primary-100', primary100);
  root.style.setProperty('--primary-700', primary700);

  const accentBase = config.accentHex;
  const accent50 = mixWithWhite(accentBase, 0.90);
  const accent500 = accentBase;
  const accent700 = mixWithBlack(accentBase, 0.20);
  root.style.setProperty('--accent', accentBase);
  root.style.setProperty('--accent-50', accent50);
  root.style.setProperty('--accent-500', accent500);
  root.style.setProperty('--accent-700', accent700);
}

/** Mix a hex colour with white by ratio 0–1 (1 = pure white). */
function mixWithWhite(hex: string, ratio: number): string {
  const { r, g, b } = parseHex(hex);
  const mix = (c: number) => Math.round(c + (255 - c) * ratio);
  return `#${[mix(r), mix(g), mix(b)].map(c => c.toString(16).padStart(2, '0')).join('')}`;
}

/** Mix a hex colour with black by ratio 0–1 (1 = pure black). */
function mixWithBlack(hex: string, ratio: number): string {
  const { r, g, b } = parseHex(hex);
  const mix = (c: number) => Math.round(c * (1 - ratio));
  return `#${[mix(r), mix(g), mix(b)].map(c => c.toString(16).padStart(2, '0')).join('')}`;
}

function parseHex(hex: string): { r: number; g: number; b: number } {
  const cleaned = hex.replace('#', '');
  const v = cleaned.length === 3
    ? cleaned.split('').map(c => c + c).join('')
    : cleaned;
  return {
    r: parseInt(v.slice(0, 2), 16),
    g: parseInt(v.slice(2, 4), 16),
    b: parseInt(v.slice(4, 6), 16),
  };
}

// ─── Quick palette presets (Saudi-flavored) ─────────────────────────────────

export interface PalettePreset {
  code: string;
  nameEn: string;
  nameAr: string;
  primaryHex: string;
  accentHex: string;
}

export const PALETTE_PRESETS: PalettePreset[] = [
  { code: 'default-indigo', nameEn: 'Indigo (default)',  nameAr: 'نيلي (الافتراضي)', primaryHex: '#4f46e5', accentHex: '#14b8a6' },
  { code: 'sama-blue',       nameEn: 'SAMA Blue',          nameAr: 'أزرق ساما',         primaryHex: '#0d3a6a', accentHex: '#d4af37' },
  { code: 'nca-green',       nameEn: 'NCA Green',          nameAr: 'أخضر الأمن السيبراني', primaryHex: '#0a6b3a', accentHex: '#f59e0b' },
  { code: 'cma-navy',        nameEn: 'CMA Navy',           nameAr: 'كحلي هيئة السوق',     primaryHex: '#012169', accentHex: '#c8a951' },
  { code: 'aramco-green',     nameEn: 'Aramco Green',        nameAr: 'أخضر أرامكو',         primaryHex: '#003c2f', accentHex: '#005bbb' },
  { code: 'neom-charcoal',    nameEn: 'NEOM Charcoal',       nameAr: 'نيوم فحمي',           primaryHex: '#1a1a1a', accentHex: '#e94e1b' },
];
