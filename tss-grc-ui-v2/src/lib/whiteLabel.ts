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

const KEY = 'grc.whitelabel.v1';
const EVENT = 'grc.whitelabel.changed';

const DEFAULT_CONFIG: WhiteLabelConfig = {
  organisationNameEn: 'TSS GRC Platform',
  organisationNameAr: 'منصة الحوكمة والمخاطر والامتثال',
  taglineEn: 'Saudi-first GRC platform',
  taglineAr: 'منصة الحوكمة والمخاطر والامتثال — صناعة سعودية',
  primaryHex: '#4f46e5',
  accentHex: '#14b8a6',
};

export function loadWhiteLabel(): WhiteLabelConfig {
  if (typeof window === 'undefined') return DEFAULT_CONFIG;
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return DEFAULT_CONFIG;
    const parsed = JSON.parse(raw);
    return { ...DEFAULT_CONFIG, ...parsed };
  } catch { return DEFAULT_CONFIG; }
}

export function saveWhiteLabel(config: Partial<WhiteLabelConfig>): WhiteLabelConfig {
  const current = loadWhiteLabel();
  const next: WhiteLabelConfig = {
    ...current, ...config,
    updatedAt: new Date().toISOString(),
  };
  try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
  applyToDom(next);
  window.dispatchEvent(new CustomEvent(EVENT));
  return next;
}

export function resetWhiteLabel(): void {
  try { window.localStorage.removeItem(KEY); } catch { /* ignore */ }
  applyToDom(DEFAULT_CONFIG);
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function subscribeWhiteLabel(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  const storageHandler = (e: StorageEvent) => { if (e.key === KEY) cb(); };
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
