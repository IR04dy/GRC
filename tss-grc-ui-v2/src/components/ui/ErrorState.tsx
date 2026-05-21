import { AlertTriangle, RefreshCw, ServerCrash, ShieldOff } from 'lucide-react';
import { Button } from './Button';

/**
 * Standardised inline error UI. Replaces the ad-hoc
 *   <div style={{color:'red'}}>Failed to load</div>
 * sprinkled across pages.
 *
 * <p>Selects an icon + tone based on the error string:
 *   - "sign in" / "401" → ShieldOff (auth)
 *   - "network" / "Failed to fetch" → ServerCrash (transport)
 *   - everything else → AlertTriangle (generic)
 *
 * <p>Always shows a "Try again" button when an onRetry handler is provided.
 */
export function ErrorState({
  title,
  message,
  onRetry,
  compact = false,
}: {
  title?: string;
  message?: string | null;
  onRetry?: () => void;
  compact?: boolean;
}) {
  const msg = message ?? 'Something went wrong.';
  const Icon = pickIcon(msg);
  const finalTitle = title ?? defaultTitle(msg);

  return (
    <div className={compact ? 'error-state error-state-compact' : 'error-state'}
         role="alert">
      <Icon size={compact ? 18 : 32} strokeWidth={1.7} className="error-state-icon" />
      <div className="error-state-body">
        <div className="error-state-title">{finalTitle}</div>
        {msg && msg !== finalTitle && (
          <div className="error-state-sub">{msg}</div>
        )}
      </div>
      {onRetry && (
        <Button variant="secondary" size={compact ? 'sm' : 'md'}
                onClick={onRetry} Icon={RefreshCw}>
          Try again
        </Button>
      )}
    </div>
  );
}

function pickIcon(msg: string) {
  const m = msg.toLowerCase();
  if (m.includes('sign in') || m.includes('401') || m.includes('unauthor')) {
    return ShieldOff;
  }
  if (m.includes('network') || m.includes('fetch') || m.includes('503') || m.includes('connection')) {
    return ServerCrash;
  }
  return AlertTriangle;
}

function defaultTitle(msg: string) {
  const m = msg.toLowerCase();
  if (m.includes('sign in') || m.includes('401') || m.includes('unauthor')) return 'Session expired';
  if (m.includes('network') || m.includes('fetch')) return 'Connection problem';
  if (m.includes('not found') || m.includes('404')) return 'Not found';
  if (m.includes('403') || m.includes('forbidden')) return 'Not authorised';
  return 'Couldn’t load this section';
}
