import { lazy, Suspense, type CSSProperties } from 'react';
import { useChartTheme } from './useChartTheme';

const RechartsLazy = lazy(() => import('./_recharts-bundle'));

export interface LineChartSeries {
  key: string;
  name: string;
  color?: string;
  /** Dashed for "compare to last period" series. */
  dashed?: boolean;
}

export interface LineChartProps<T> {
  data: T[];
  series: LineChartSeries[];
  xKey: keyof T & string;
  formatY?: (v: number) => string;
  legend?: boolean;
  onPointClick?: (datum: T, seriesKey: string) => void;
  height?: number;
  style?: CSSProperties;
  emptyState?: React.ReactNode;
}

/** Themed line chart — for time-series (KRI trends, SLA compliance over time). */
export function LineChart<T extends Record<string, any>>(props: LineChartProps<T>) {
  const theme = useChartTheme();

  if (!props.data || props.data.length === 0) {
    return (
      <div style={{ height: props.height ?? 280, display: 'flex', alignItems: 'center', justifyContent: 'center', color: theme.tick.fill, ...props.style }}>
        {props.emptyState ?? '—'}
      </div>
    );
  }

  return (
    <Suspense fallback={<div style={{ height: props.height ?? 280 }}>...</div>}>
      <RechartsLazy kind="line" {...props} theme={theme} height={props.height ?? 280} legend={props.legend ?? true} />
    </Suspense>
  );
}
