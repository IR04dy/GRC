import { type ReactNode } from 'react';
import { ArrowLeft } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../ui/Button';
import { useI18n } from '../../lib/i18n';

export function PageHeader({
  eyebrow, title, sub, badges, actions, backTo,
}: {
  eyebrow?: string;
  title: string;
  sub?: string;
  /** Status/identity badges shown beside the title — the conventional home
   *  for a record's most-scanned attribute (matches DetailHeader). Keep the
   *  `actions` slot for buttons only. */
  badges?: ReactNode;
  actions?: ReactNode;
  /** When set, renders the canonical back arrow (icon-only, RTL-flipped) to the
   *  left of the header — the single back-affordance across detail pages (DT7).
   *  Pass a route to navigate there, or '' to go back in history. */
  backTo?: string;
}) {
  const { t, lang } = useI18n();
  const navigate = useNavigate();
  return (
    <div className="page-header">
      <div className="page-header-lead">
        {backTo !== undefined && (
          <Button
            variant="ghost"
            Icon={ArrowLeft}
            onClick={() => (backTo ? navigate(backTo) : navigate(-1))}
            aria-label={t('back') ?? 'Back'}
            style={{ transform: lang === 'ar' ? 'scaleX(-1)' : undefined, flexShrink: 0 }}
          />
        )}
        <div style={{ minWidth: 0 }}>
          {eyebrow && <div className="eyebrow">{eyebrow}</div>}
          <div className="page-title-row">
            <h2 className="page-title">{title}</h2>
            {badges}
          </div>
          {sub && <p className="page-sub">{sub}</p>}
        </div>
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}
