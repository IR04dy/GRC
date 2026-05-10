// Lazy-loaded Recharts bundle. Wrappers in BarChart.tsx / LineChart.tsx /
// PieChart.tsx import this via React.lazy so Recharts (~85KB gz) only ships
// to users who actually open a chart-bearing page.

import {
  ResponsiveContainer,
  BarChart as RBarChart, Bar,
  LineChart as RLineChart, Line,
  PieChart as RPieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend,
} from 'recharts';
import type { ChartTheme } from './useChartTheme';

interface Props {
  kind: 'bar' | 'line' | 'pie';
  data: any[];
  series?: { key: string; name: string; color?: string; stackId?: string; dashed?: boolean }[];
  xKey?: string;
  formatY?: (v: number) => string;
  legend?: boolean;
  innerRadius?: number;
  onPointClick?: (datum: any, seriesKey?: string) => void;
  height: number;
  theme: ChartTheme;
}

export default function RechartsBundle(props: Props) {
  const { theme, height } = props;

  const tooltipStyle = {
    backgroundColor: theme.tooltip.bg,
    color: theme.tooltip.ink,
    border: `1px solid ${theme.tooltip.border}`,
    borderRadius: 6,
    fontSize: 12,
  };

  if (props.kind === 'bar') {
    return (
      <ResponsiveContainer width="100%" height={height}>
        <RBarChart data={props.data} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={theme.grid} />
          <XAxis dataKey={props.xKey} tick={theme.tick} stroke={theme.axis} reversed={theme.rtl} />
          <YAxis tick={theme.tick} stroke={theme.axis} tickFormatter={props.formatY} orientation={theme.rtl ? 'right' : 'left'} />
          <Tooltip contentStyle={tooltipStyle} />
          {props.legend && <Legend wrapperStyle={{ fontSize: theme.legend.fontSize }} />}
          {(props.series ?? []).map((s, i) => (
            <Bar
              key={s.key}
              dataKey={s.key}
              name={s.name}
              fill={s.color ?? theme.series[i % theme.series.length]}
              stackId={s.stackId}
              cursor={props.onPointClick ? 'pointer' : undefined}
              onClick={props.onPointClick ? (d: any) => props.onPointClick!(d.payload, s.key) : undefined}
            />
          ))}
        </RBarChart>
      </ResponsiveContainer>
    );
  }

  if (props.kind === 'line') {
    return (
      <ResponsiveContainer width="100%" height={height}>
        <RLineChart data={props.data} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={theme.grid} />
          <XAxis dataKey={props.xKey} tick={theme.tick} stroke={theme.axis} reversed={theme.rtl} />
          <YAxis tick={theme.tick} stroke={theme.axis} tickFormatter={props.formatY} orientation={theme.rtl ? 'right' : 'left'} />
          <Tooltip contentStyle={tooltipStyle} />
          {props.legend && <Legend wrapperStyle={{ fontSize: theme.legend.fontSize }} />}
          {(props.series ?? []).map((s, i) => (
            <Line
              key={s.key}
              type="monotone"
              dataKey={s.key}
              name={s.name}
              stroke={s.color ?? theme.series[i % theme.series.length]}
              strokeWidth={2}
              strokeDasharray={s.dashed ? '4 4' : undefined}
              dot={{ r: 3 }}
              // Enlarge the active dot when an onPointClick handler exists so users
              // know there's something to click.
              activeDot={props.onPointClick
                ? { r: 6, cursor: 'pointer', onClick: (_e: any, payload: any) => props.onPointClick!(payload?.payload, s.key) }
                : { r: 5 }}
            />
          ))}
        </RLineChart>
      </ResponsiveContainer>
    );
  }

  // pie
  return (
    <ResponsiveContainer width="100%" height={height}>
      <RPieChart>
        <Tooltip contentStyle={tooltipStyle} />
        {props.legend && <Legend wrapperStyle={{ fontSize: theme.legend.fontSize }} />}
        <Pie
          data={props.data}
          nameKey="name"
          dataKey="value"
          cx="50%"
          cy="50%"
          innerRadius={props.innerRadius ? `${props.innerRadius}%` : 0}
          outerRadius="80%"
          cursor={props.onPointClick ? 'pointer' : undefined}
          onClick={props.onPointClick ? (d: any) => props.onPointClick!(d.payload) : undefined}
        >
          {props.data.map((entry, i) => (
            <Cell key={`c-${i}`} fill={entry.color ?? theme.series[i % theme.series.length]} />
          ))}
        </Pie>
      </RPieChart>
    </ResponsiveContainer>
  );
}
