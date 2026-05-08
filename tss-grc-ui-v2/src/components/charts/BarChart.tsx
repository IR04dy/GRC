import { lazy, Suspense, type CSSProperties } from 'react';
import { useChartTheme } from './useChartTheme';

// Lazy-load the heavy Recharts module so it doesn't bloat the initial bundle.
const RechartsLazy = lazy(() => import('./_recharts-bundle'));

export interface BarChartSeries {
  /** Series key (must match data row property name). */
  key: string;
  /** Display name (bilingual — caller passes already-translated string). */
  name: string;
  /** Override colour from the theme palette. */
  color?: string;
  /** Stacking group id — series sharing a stackId stack on top of each other. */
  stackId?: string;
}

export interface BarChartProps<T> {
  data: T[];
  series: BarChartSeries[];
  /** Property to use for the X axis (categorical). */
  xKey: keyof T & string;
  /** Format ticks on Y. */
  formatY?: (v: number) => string;
  /** Show legend. */
  legend?: boolean;
  /** Click handler — fires with the data point + series key. */
  onPointClick?: (datum: T, seriesKey: string) => void;
  height?: number;
  style?: CSSProperties;
  /** Empty-state placeholder. */
  emptyState?: React.ReactNode;
}

/**
 * Themed bar chart wrapper around Recharts. Drill-down via `onPointClick`.
 *
 * Use cases:
 *   - Risks by category (bar series = severity), drill = filtered risk list
 *   - Vulnerabilities by asset tier, drill = filtered vuln list
 *   - Findings by quarter, stacked by status
 */
export function BarChart<T extends Record<string, any>>({ data, series, xKey, formatY, legend = true, onPointClick, height = 280, style, emptyState }: BarChartProps<T>) {
  const theme = useChartTheme();

  if (!data || data.length === 0) {
    return (
      <div style={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center', color: theme.tick.fill, ...style }}>
        {emptyState ?? '—'}
      </div>
    );
  }

  return (
    <Suspense fallback={<div style={{ height }}>...</div>}>
      <RechartsLazy
        kind="bar"
        data={data}
        series={series}
        xKey={xKey}
        formatY={formatY}
        legend={legend}
        onPointClick={onPointClick}
        height={height}
        theme={theme}
      />
    </Suspense>
  );
}
