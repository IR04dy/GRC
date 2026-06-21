// UX-007: lightweight WCAG contrast utilities for the Settings customizer.
//
// Used by the page-background / identity colour pickers to surface an inline
// "Low contrast" badge when a chosen colour would fail the WCAG 2.1 AA 4.5:1
// minimum against body ink (`--ink-1`). Kept dependency-free and pure so it
// can be unit-tested and reused anywhere a quick legibility guard is needed.

/** WCAG AA minimum contrast ratio for normal body text. */
export const AA_NORMAL_TEXT = 4.5;

/** Parse a `#rgb` / `#rrggbb` hex string into 0–255 channels, or null. */
function parseHex(hex: string): { r: number; g: number; b: number } | null {
  const m = hex.trim().replace(/^#/, '');
  if (m.length === 3) {
    const r = parseInt(m[0] + m[0], 16);
    const g = parseInt(m[1] + m[1], 16);
    const b = parseInt(m[2] + m[2], 16);
    return Number.isNaN(r + g + b) ? null : { r, g, b };
  }
  if (m.length === 6) {
    const r = parseInt(m.slice(0, 2), 16);
    const g = parseInt(m.slice(2, 4), 16);
    const b = parseInt(m.slice(4, 6), 16);
    return Number.isNaN(r + g + b) ? null : { r, g, b };
  }
  return null;
}

/** Relative luminance of a single sRGB channel (0–255 → 0–1, gamma-corrected). */
function channelLuminance(value: number): number {
  const c = value / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

/** WCAG relative luminance of a hex colour (0 = black, 1 = white). */
export function relativeLuminance(hex: string): number | null {
  const rgb = parseHex(hex);
  if (!rgb) return null;
  return 0.2126 * channelLuminance(rgb.r)
       + 0.7152 * channelLuminance(rgb.g)
       + 0.0722 * channelLuminance(rgb.b);
}

/**
 * WCAG contrast ratio between two hex colours (1–21). Returns null when either
 * colour cannot be parsed (e.g. a non-hex CSS keyword), so callers can treat
 * "unknown" as "no warning" rather than a false positive.
 */
export function contrastRatio(a: string, b: string): number | null {
  const la = relativeLuminance(a);
  const lb = relativeLuminance(b);
  if (la === null || lb === null) return null;
  const lighter = Math.max(la, lb);
  const darker = Math.min(la, lb);
  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * True when `colour` fails the AA normal-text minimum against `against`.
 * Unparseable input is treated as passing (returns false) to avoid noisy
 * false warnings on CSS keywords or partial typing.
 */
export function failsAaContrast(colour: string, against: string): boolean {
  const ratio = contrastRatio(colour, against);
  if (ratio === null) return false;
  return ratio < AA_NORMAL_TEXT;
}
