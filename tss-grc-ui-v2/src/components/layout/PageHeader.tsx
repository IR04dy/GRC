import { type ReactNode } from 'react';

export function PageHeader({
  eyebrow, title, sub, badges, actions,
}: {
  eyebrow?: string;
  title: string;
  sub?: string;
  /** Status/identity badges shown beside the title — the conventional home
   *  for a record's most-scanned attribute (matches DetailHeader). Keep the
   *  `actions` slot for buttons only. */
  badges?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="page-header">
      <div style={{ minWidth: 0 }}>
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <div className="page-title-row">
          <h2 className="page-title">{title}</h2>
          {badges}
        </div>
        {sub && <p className="page-sub">{sub}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}
