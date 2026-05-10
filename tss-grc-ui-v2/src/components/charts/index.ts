// Themed Recharts wrappers — see Sprint 4 plan §2.2.
//
// All wrappers lazy-load the Recharts bundle so the initial JS payload
// stays small. RTL-aware (axis order flips on Arabic), drill-down support
// via `onPointClick`, and bilingual tooltips via the `name` prop on series.

export { BarChart } from './BarChart';
export { LineChart } from './LineChart';
export { PieChart } from './PieChart';
export { useChartTheme } from './useChartTheme';
export type { BarChartSeries, BarChartProps } from './BarChart';
export type { LineChartSeries, LineChartProps } from './LineChart';
export type { PieDatum, PieChartProps } from './PieChart';
