import { lazy, Suspense, type CSSProperties } from 'react';
import { useChartTheme } from './useChartTheme';

const RechartsLazy = lazy(() => import('./_recharts-bundle'));

export interface PieDatum {
  name: string;
  value: number;
  color?: string;
}

export interface PieChartProps {
  data: PieDatum[];
  /** Donut hole inner radius (0-100, % of outer). */
  innerRadius?: number;
  legend?: boolean;
  onPointClick?: (datum: PieDatum) => void;
  height?: number;
  style?: CSSProperties;
  emptyState?: React.ReactNode;
}

export function PieChart(props: PieChartProps) {
  const theme = useChartTheme();

  if (!props.data || props.data.length === 0) {
    return (
      <div style={{ height: props.height ?? 240, display: 'flex', alignItems: 'center', justifyContent: 'center', color: theme.tick.fill, ...props.style }}>
        {props.emptyState ?? '—'}
      </div>
    );
  }

  return (
    <Suspense fallback={<div style={{ height: props.height ?? 240 }}>...</div>}>
      <RechartsLazy kind="pie" {...props} theme={theme} height={props.height ?? 240} legend={props.legend ?? true} />
    </Suspense>
  );
}
