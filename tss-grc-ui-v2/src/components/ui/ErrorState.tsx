import { AlertTriangle, RefreshCw, ServerCrash, ShieldOff, DatabaseZap, Copy } from 'lucide-react';
import { Button } from './Button';
import { useI18n } from '../../lib/i18n';
import {
  getErrorDisplayMessage,
  isBootstrapNeeded,
  suggestedBootstrapCommand,
  getProblemDetail,
} from '../../lib/apiError';

/**
 * Standardised inline error UI. Replaces the ad-hoc
 *   <div style={{color:'red'}}>Failed to load</div>
 * sprinkled across pages.
 *
 * <p>Selects an icon + tone based on the error:
 *   - PR #117 bootstrap-needed flavour → DatabaseZap (with setup command snippet)
 *   - "sign in" / 401 → ShieldOff (auth)
 *   - "network" / "fetch" / 503 → ServerCrash (transport)
 *   - everything else → AlertTriangle (generic)
 *
 * <p>Now bilingual (en/ar) and ProblemDetail-aware: pass either a string,
 * an Error, or an ApiError. RFC-7807 title + detail are extracted and
 * shown verbatim when present (so backend Saudi-native messages survive).
 */
export function ErrorState({
  title,
  message,
  error,
  onRetry,
  compact = false,
}: {
  /** Optional pre-localised title override. */
  title?: string;
  /** Legacy string message — kept for back-compat with existing callers. */
  message?: string | null;
  /** Preferred — pass the raw error (ApiError | Error | unknown) and we
   *  extract ProblemDetail title/detail + decide the right UX flavour. */
  error?: unknown;
  onRetry?: () => void;
  compact?: boolean;
}) {
  const { t, lang } = useI18n();

  // Bootstrap-needed gets the dedicated dev-install UX with a copyable
  // command snippet. This is the day-2-day GRC-platform-onboarding case.
  if (error !== undefined && isBootstrapNeeded(error)) {
    return <BootstrapNeededState error={error} compact={compact} t={t} lang={lang} />;
  }

  const rawMsg = error !== undefined
    ? getErrorDisplayMessage(error)
    : (message ?? t('errorStateGeneric'));
  const Icon = pickIcon(rawMsg);
  const finalTitle = title ?? defaultTitle(rawMsg, t);

  return (
    <div className={compact ? 'error-state error-state-compact' : 'error-state'}
         role="alert">
      <Icon size={compact ? 18 : 32} strokeWidth={1.7} className="error-state-icon" />
      <div className="error-state-body">
        <div className="error-state-title">{finalTitle}</div>
        {rawMsg && rawMsg !== finalTitle && (
          <div className="error-state-sub">{rawMsg}</div>
        )}
      </div>
      {onRetry && (
        <Button variant="secondary" size={compact ? 'sm' : 'md'}
                onClick={onRetry} Icon={RefreshCw}>
          {t('errorStateTryAgain')}
        </Button>
      )}
    </div>
  );
}

function BootstrapNeededState({ error, compact, t, lang }: {
  error: unknown;
  compact: boolean;
  t: (k: string, fb?: string) => string;
  lang: 'en' | 'ar';
}) {
  const cmd  = suggestedBootstrapCommand(error);
  const pd   = getProblemDetail(error);
  const root = typeof pd?.rootCause === 'string' ? pd.rootCause : undefined;

  const copyCmd = () => {
    try { void navigator.clipboard?.writeText(cmd); } catch { /* clipboard may be blocked */ }
  };

  return (
    <div className={compact ? 'error-state error-state-compact' : 'error-state'}
         role="alert"
         style={{ flexDirection: 'column', alignItems: lang === 'ar' ? 'flex-end' : 'flex-start' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <DatabaseZap size={compact ? 18 : 28} strokeWidth={1.7} className="error-state-icon" />
        <div className="error-state-title">{t('errorStateBootstrapTitle')}</div>
      </div>
      <div className="error-state-sub" style={{ marginTop: 6 }}>
        {t('errorStateBootstrapHint')}
      </div>
      <div style={{
        marginTop: 12,
        padding: '8px 12px',
        background: 'var(--surface-2)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-sm)',
        fontSize: 12,
        width: '100%',
        maxWidth: 540,
        direction: 'ltr',  // commands are always LTR even in RTL pages
      }}>
        <div style={{ color: 'var(--ink-3)', marginBottom: 4 }}>
          {t('errorStateBootstrapCmdLabel')}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'space-between' }}>
          <code style={{ fontFamily: 'var(--font-mono, monospace)', fontSize: 12.5 }}>{cmd}</code>
          <button onClick={copyCmd}
            aria-label="Copy"
            title="Copy"
            style={{
              background: 'transparent', border: '1px solid var(--border)',
              borderRadius: 4, cursor: 'pointer', padding: '2px 6px',
              color: 'var(--ink-2)',
            }}>
            <Copy size={12} />
          </button>
        </div>
        {root && (
          <div style={{ marginTop: 6, fontSize: 11, color: 'var(--ink-3)' }}>
            {/* The schema-qualified table name from the SQL error — helps
                the operator diagnose which migration is missing. */}
            <code style={{ fontFamily: 'var(--font-mono, monospace)' }}>{root}</code>
          </div>
        )}
      </div>
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

function defaultTitle(msg: string, t: (k: string, fb?: string) => string) {
  const m = msg.toLowerCase();
  if (m.includes('sign in') || m.includes('401') || m.includes('unauthor')) {
    return t('errorStateSessionExpired');
  }
  if (m.includes('network') || m.includes('fetch') || m.includes('connection')) {
    return t('errorStateConnectionProblem');
  }
  if (m.includes('not found') || m.includes('404')) {
    return t('errorStateNotFound');
  }
  if (m.includes('403') || m.includes('forbidden')) {
    return t('errorStateForbidden');
  }
  return t('errorStateGeneric');
}
