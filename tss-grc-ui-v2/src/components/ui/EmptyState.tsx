import { type LucideIcon, Construction } from 'lucide-react';
import { useI18n } from '../../lib/i18n';

interface EmptyStateProps {
  Icon?: LucideIcon;
  title?: string;
  sub?: string;
}

export function EmptyState({ Icon = Construction, title, sub }: EmptyStateProps) {
  const { t } = useI18n();
  return (
    <div className="placeholder">
      <Icon size={48} strokeWidth={1.5} />
      <h4>{title ?? t('comingSoon')}</h4>
      <p>{sub ?? t('comingSoonSub')}</p>
    </div>
  );
}
