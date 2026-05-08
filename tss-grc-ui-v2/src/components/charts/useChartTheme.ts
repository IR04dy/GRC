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

const PALETTE = [
  '#0c5460',  // teal — brand primary
  '#dc2626',  // red — danger
  '#f59e0b',  // amber — warning
  '#16a34a',  // green — success
  '#2563eb',  // blue — info
  '#9333ea',  // purple
  '#ec4899',  // pink
  '#0891b2',  // cyan
];

export function useChartTheme(): ChartTheme {
  const { lang } = useI18n();
  // Read CSS custom-properties at runtime so dark/light/white-label themes work.
  const css = typeof window !== 'undefined' ? getComputedStyle(document.documentElement) : null;
  const ink2   = css?.getPropertyValue('--ink-2').trim() || '#475569';
  const ink3   = css?.getPropertyValue('--ink-3').trim() || '#94a3b8';
  const surf1  = css?.getPropertyValue('--surface-1').trim() || '#ffffff';
  const border = css?.getPropertyValue('--border').trim() || '#e2e8f0';

  return {
    series: PALETTE,
    axis: ink3,
    grid: border,
    tick: { fill: ink3, fontSize: 11 },
    tooltip: { bg: surf1, ink: ink2, border },
    legend: { fontSize: 11 },
    rtl: lang === 'ar',
  };
}
