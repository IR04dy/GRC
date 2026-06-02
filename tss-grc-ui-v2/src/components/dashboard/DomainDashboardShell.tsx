import { type ReactNode } from 'react';
import { type LucideIcon } from 'lucide-react';
import { Shell } from '../layout/Shell';
import { PageHeader } from '../layout/PageHeader';
import { Card } from '../ui/Card';
import { Kpi } from '../ui/Kpi';
import { Badge } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';

/**
 * Reusable scaffold for the per-domain dashboards (BCM / SCM / ERM /
 * Cyber / Privacy) — task #36.
 *
 * Every domain dashboard follows the same three-zone structure so
 * users learn the navigation once:
 *
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │  Page header                                                 │
 *   ├──────────────────────────────────────────────────────────────┤
 *   │  KPI strip   (4-6 small tiles)                               │
 *   ├──────────────────────────────────────────────────────────────┤
 *   │  Widget grid (cards, dash-grid layout)                       │
 *   └──────────────────────────────────────────────────────────────┘
 *
 * Per the Saudi GRC prompt §30 each domain has its own dashboard;
 * filterable family-level views remain on the Compliance Posture Hub
 * (F6). This shell keeps the per-domain pages thin — they just
 * declare their KPIs and widget cards.
 */
export interface DomainDashboardKpi {
  Icon: LucideIcon;
  label: string;
  value: string | number;
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'info';
  to?: string;
}

export interface DomainDashboardWidget {
  id: string;
  title: string;
  badge?: string;
  badgeTone?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral';
  body: ReactNode;
  /** Optional "see all" link target shown in the card header. */
  href?: string;
  /** Span the full row (for wide charts / time-series). */
  wide?: boolean;
}

export interface DomainDashboardShellProps {
  /** Page title (already translated by caller). */
  title: string;
  /** Sub-title beneath the page title. */
  sub: string;
  /** Eyebrow text shown above the title. */
  eyebrow: string;
  kpis: DomainDashboardKpi[];
  widgets: DomainDashboardWidget[];
  /** Optional top-right action (a refresh button, an export menu, etc.). */
  headerAction?: ReactNode;
}

export function DomainDashboardShell({
  title, sub, eyebrow, kpis, widgets, headerAction,
}: DomainDashboardShellProps) {
  const cols = Math.min(kpis.length, 6);

  return (
    <Shell title={title} sub={sub}>
      <PageHeader eyebrow={eyebrow} title={title} actions={headerAction} />

      {kpis.length > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: `repeat(${cols}, 1fr)`, gap: 12, marginBottom: 14 }}>
          {kpis.map((k, i) => (
            <Kpi key={i} Icon={k.Icon} label={k.label} value={k.value} tone={k.tone} to={k.to} />
          ))}
        </div>
      )}

      <div className="widget-grid">
        {widgets.map(w => (
          <Card key={w.id} title={w.title} className={w.wide ? 'widget-wide' : undefined}
            action={w.badge ? <Badge tone={w.badgeTone ?? 'primary'}>{w.badge}</Badge> : undefined}>
            {w.body}
            {w.href && (
              <div style={{ marginTop: 12, fontSize: 12 }}>
                <a href={w.href} style={{ color: 'var(--primary)', textDecoration: 'none' }}>
                  <DomainDashboardSeeAll href={w.href} />
                </a>
              </div>
            )}
          </Card>
        ))}
      </div>
    </Shell>
  );
}

function DomainDashboardSeeAll({ href }: { href: string }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  void href;
  return <>{isAr ? 'عرض الكل ←' : '→ See all'}</>;
}
