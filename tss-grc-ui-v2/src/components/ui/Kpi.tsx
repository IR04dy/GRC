import { type LucideIcon, TrendingUp, TrendingDown } from 'lucide-react';

type KpiTone = 'primary' | 'danger' | 'success' | 'warning' | 'info' | 'accent';

interface KpiProps {
  Icon: LucideIcon;
  label: string;
  value: string | number;
  trend?: { dir: 'up' | 'down'; value: string };
  tone?: KpiTone;
}

const TONES: Record<KpiTone, { bg: string; fg: string }> = {
  primary: { bg: 'var(--primary-50)', fg: 'var(--primary)' },
  danger:  { bg: 'var(--danger-bg)',  fg: 'var(--danger-fg)' },
  success: { bg: 'var(--success-bg)', fg: 'var(--success-fg)' },
  warning: { bg: 'var(--warning-bg)', fg: 'var(--warning-fg)' },
  info:    { bg: 'var(--info-bg)',    fg: 'var(--info-fg)' },
  accent:  { bg: 'var(--accent-50)',  fg: 'var(--accent-700)' },
};

export function Kpi({ Icon, label, value, trend, tone = 'primary' }: KpiProps) {
  const { bg, fg } = TONES[tone];
  return (
    <div className="kpi">
      <div className="kpi-top">
        <div className="kpi-icon-wrap" style={{ background: bg, color: fg }}>
          <Icon size={20} strokeWidth={2} />
        </div>
        {trend && (
          <div className={`kpi-trend ${trend.dir}`}>
            {trend.dir === 'up' ? <TrendingUp size={11} /> : <TrendingDown size={11} />}
            {trend.value}
          </div>
        )}
      </div>
      <div className="kpi-value">{value}</div>
      <div className="kpi-label">{label}</div>
    </div>
  );
}
