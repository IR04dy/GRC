import { RefreshCw } from 'lucide-react';
import { DomainDashboardShell } from './DomainDashboardShell';
import { Skeleton } from '../ui/Skeleton';
import { useI18n } from '../../lib/i18n';

/**
 * Render-only shell for the loading state of a domain dashboard.
 *
 * Used by BCM / ERM / PDPL / SCM / Cyber dashboards so all five share
 * the same Skeleton row pattern instead of each pasting one inline.
 */
export function DashboardLoading({
  title,
  eyebrow,
}: {
  title: string;
  eyebrow: string;
}) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  return (
    <DomainDashboardShell
      title={title}
      sub={isAr ? 'جارٍ التحميل…' : 'Loading…'}
      eyebrow={eyebrow}
      kpis={[]}
      widgets={[{
        id: 'loading',
        title: '',
        body: (
          <div className="dashboard-skeleton">
            <Skeleton height={20} />
            <Skeleton height={20} />
            <Skeleton height={20} />
            <Skeleton height={20} />
          </div>
        ),
      }]}
    />
  );
}

/**
 * Render-only shell for the error state of a domain dashboard. Shows
 * the message + a retry button so the user can recover from a
 * transient backend hiccup without page-reloading.
 */
export function DashboardError({
  title,
  eyebrow,
  errMsg,
  onRetry,
}: {
  title: string;
  eyebrow: string;
  errMsg: string | null;
  onRetry: () => void;
}) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  return (
    <DomainDashboardShell
      title={title}
      sub={isAr ? 'تعذّر تحميل البيانات' : 'Could not load dashboard'}
      eyebrow={eyebrow}
      kpis={[]}
      widgets={[{
        id: 'error',
        title: isAr ? 'خطأ' : 'Error',
        body: (
          <div className="dashboard-error">
            <div className="text-muted text-xs">
              {errMsg ?? (isAr ? 'تحقق من اتصال الخادم' : 'Check backend connectivity')}
            </div>
            <button type="button" className="btn btn-sm btn-outline mt-2" onClick={onRetry}>
              <RefreshCw size={14} /> {isAr ? 'إعادة المحاولة' : 'Retry'}
            </button>
          </div>
        ),
      }]}
    />
  );
}
