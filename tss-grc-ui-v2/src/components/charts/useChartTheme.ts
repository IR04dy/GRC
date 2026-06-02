import { useI18n } from '../../lib/i18n';

/**
 * Bilingual + dark-mode-aware chart theme.
 *
 * Recharts is colour-agnostic — it expects you to pass `stroke`, `fill`, `tick`
 * and `cursor` props per chart. This hook centralises the palette so every
 * chart in the platform looks consistent and respects RTL automatically.
 */
export interface ChartTheme {
  series: string[];
  axis: string;
  grid: string;
  tick: { fill: string; fontSize: number };
  tooltip: { bg: string; ink: string; border: string };
  legend: { fontSize: number };
  /** True when locale is Arabic; consumers should reverse axis order on `dir="rtl"`. */
  rtl: boolean;
}

// On-brand categorical palette fallbacks — these mirror the `--cat-1..8` design
// tokens so charts stay branded even before styles resolve (SSR/first paint).
// The live values are read from the tokens at runtime (see below), so dark mode
// and white-label themes propagate automatically.
const CAT_FALLBACK = [
  '#0E5E5E', // --cat-1 teal-petrol (brand primary)
  '#C8932A', // --cat-2 sand-amber (accent)
  '#5F8A8A', // --cat-3 desaturated teal
  '#8E5A7A', // --cat-4 mauve
  '#3F8C5C', // --cat-5 green
  '#B5663C', // --cat-6 terracotta
  '#6A4E8E', // --cat-7 purple
  '#C8B042', // --cat-8 gold
];

export function useChartTheme(): ChartTheme {
  const { lang } = useI18n();
  // Read CSS custom-properties at runtime so dark/light/white-label themes work.
  const css = typeof window !== 'undefined' ? getComputedStyle(document.documentElement) : null;
  const tok = (name: string, fallback: string) => css?.getPropertyValue(name).trim() || fallback;
  const ink2   = tok('--ink-2', '#475569');
  const ink3   = tok('--ink-3', '#94a3b8');
  const surf1  = tok('--surface-1', '#ffffff');
  const border = tok('--border', '#e2e8f0');

  // Series colours come from the canonical `--cat-1..8` tokens, not a hardcoded
  // off-brand palette — one source of truth, dark-mode-correct.
  const series = CAT_FALLBACK.map((fb, i) => tok(`--cat-${i + 1}`, fb));

  return {
    series,
    axis: ink3,
    grid: border,
    tick: { fill: ink3, fontSize: 11 },
    tooltip: { bg: surf1, ink: ink2, border },
    legend: { fontSize: 11 },
    rtl: lang === 'ar',
  };
}
