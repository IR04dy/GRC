import type { ReactNode } from 'react';
import { ArrowLeft } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../ui/Button';
import { Breadcrumb, type BreadcrumbItem } from './Breadcrumb';
import { useI18n } from '../../lib/i18n';

/**
 * Header section for entity detail pages. Renders a breadcrumb, a back
 * button, the entity title (with optional code/badge), description and a
 * trailing actions slot.
 */
export function DetailHeader({
  breadcrumbs, eyebrow, title, sub, badges, actions, backTo,
}: {
  breadcrumbs: BreadcrumbItem[];
  eyebrow?: string;
  title: string;
  sub?: ReactNode;
  badges?: ReactNode;
  actions?: ReactNode;
  /** Where the back arrow takes the user. Defaults to history(-1) when omitted. */
  backTo?: string;
}) {
  const { t, lang } = useI18n();
  const navigate = useNavigate();
  return (
    <header style={{ marginBottom: 16 }}>
      <Breadcrumb items={breadcrumbs} />
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginTop: 8 }}>
        <Button
          variant="ghost"
          Icon={ArrowLeft}
          onClick={() => backTo ? navigate(backTo) : navigate(-1)}
          aria-label={t('back') ?? 'Back'}
          style={{ transform: lang === 'ar' ? 'scaleX(-1)' : undefined }}
        />
        <div style={{ flex: 1, minWidth: 0 }}>
          {eyebrow && <div className="eyebrow">{eyebrow}</div>}
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap', marginTop: 2 }}>
            <h1 style={{ fontSize: 22, fontWeight: 800, color: 'var(--ink-1)', letterSpacing: '-0.01em', margin: 0 }}>
              {title}
            </h1>
            {badges}
          </div>
          {sub && <div style={{ fontSize: 13, color: 'var(--ink-2)', marginTop: 4, lineHeight: 1.5 }}>{sub}</div>}
        </div>
        {actions && <div style={{ display: 'flex', gap: 8, flexShrink: 0, alignSelf: 'center' }}>{actions}</div>}
      </div>
    </header>
  );
}
