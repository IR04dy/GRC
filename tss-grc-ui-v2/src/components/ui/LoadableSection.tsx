import { type ReactNode } from 'react';
import { type LucideIcon } from 'lucide-react';
import { Skeleton } from './Skeleton';
import { EmptyState } from './EmptyState';
import { ErrorState } from './ErrorState';

/**
 * Render a section that loads data — handles the three states
 * (loading / error / empty / data) so pages don't keep re-implementing
 * the same `{loading ? … : error ? … : data.length===0 ? … : <Table/>}`
 * ladder.
 *
 * <p>Pair with {@link useLoadable} or {@link useApiList} — pass through
 * their `loading`, `error`, optional `onRetry` (=reload), and the actual
 * data. The function-as-children pattern lets the caller decide how to
 * render the loaded shape.
 */
export function LoadableSection<T>({
  loading,
  error,
  onRetry,
  data,
  empty,
  emptyTitle,
  emptySub,
  emptyIcon,
  skeletonRows = 3,
  loadingFallback,
  children,
}: {
  loading: boolean;
  error?: string | null;
  onRetry?: () => void;
  data: T | null | undefined;
  /** Override the empty-state check; default treats arrays via .length and
   *  any other non-null value as present. */
  empty?: (data: T) => boolean;
  emptyTitle?: string;
  emptySub?: string;
  emptyIcon?: LucideIcon;
  skeletonRows?: number;
  loadingFallback?: ReactNode;
  children: (data: T) => ReactNode;
}) {
  if (loading) {
    if (loadingFallback) return <>{loadingFallback}</>;
    return (
      <div className="loadable-section-skeleton" aria-busy="true">
        {Array.from({ length: skeletonRows }).map((_, i) => (
          <div key={i} className="loadable-section-skeleton-row">
            <Skeleton height={20} />
          </div>
        ))}
      </div>
    );
  }
  if (error) {
    return <ErrorState message={error} onRetry={onRetry} />;
  }
  if (data == null) {
    return <EmptyState Icon={emptyIcon} title={emptyTitle} sub={emptySub} />;
  }
  const isEmpty = empty
    ? empty(data)
    : Array.isArray(data) ? data.length === 0 : false;
  if (isEmpty) {
    return <EmptyState Icon={emptyIcon} title={emptyTitle} sub={emptySub} />;
  }
  return <>{children(data)}</>;
}
