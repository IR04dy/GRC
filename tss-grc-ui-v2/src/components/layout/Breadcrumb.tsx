import { Link } from 'react-router-dom';
import { ChevronRight, Home } from 'lucide-react';
import { useI18n } from '../../lib/i18n';

export interface BreadcrumbItem {
  /** Visible label */
  label: string;
  /** When set, this crumb is a link */
  to?: string;
}

/**
 * Compact breadcrumb trail for detail pages. The first crumb is always a
 * Home icon linking to /dashboard. Items after that mirror the route depth
 * (module list page → entity detail).
 */
export function Breadcrumb({ items }: { items: BreadcrumbItem[] }) {
  const { lang } = useI18n();
  const Sep = ChevronRight;
  return (
    <nav aria-label="Breadcrumb" style={{
      display: 'flex', alignItems: 'center', gap: 6,
      fontSize: 12, color: 'var(--ink-3)',
      padding: '8px 0', flexWrap: 'wrap',
    }}>
      <Link to="/dashboard" style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: 'var(--ink-3)', textDecoration: 'none' }}>
        <Home size={12} />
      </Link>
      {items.map((item, idx) => {
        const isLast = idx === items.length - 1;
        return (
          <span key={idx} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <Sep size={12} style={{ color: 'var(--ink-4)', transform: lang === 'ar' ? 'scaleX(-1)' : undefined }} />
            {item.to && !isLast
              ? <Link to={item.to} style={{ color: 'var(--ink-2)', textDecoration: 'none', fontWeight: 500 }}>{item.label}</Link>
              : <span style={{ color: isLast ? 'var(--ink-1)' : 'var(--ink-2)', fontWeight: isLast ? 600 : 500 }}>{item.label}</span>
            }
          </span>
        );
      })}
    </nav>
  );
}
