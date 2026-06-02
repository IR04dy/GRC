import { type ReactNode } from 'react';
import { type LucideIcon } from 'lucide-react';

export function Card({
  title, Icon, action, children, padded = true, className,
}: {
  title?: string;
  Icon?: LucideIcon;
  action?: ReactNode;
  children: ReactNode;
  padded?: boolean;
  /** Extra class on the card root — e.g. a grid-span hint like `widget-wide`. */
  className?: string;
}) {
  return (
    <div className={className ? `card ${className}` : 'card'}>
      {(title || action) && (
        <div className="card-header">
          <div className="card-title">
            {Icon && <Icon size={16} color="var(--primary)" strokeWidth={2} />}
            {title}
          </div>
          {action}
        </div>
      )}
      <div className="card-body" style={!padded ? { padding: 0 } : undefined}>{children}</div>
    </div>
  );
}
