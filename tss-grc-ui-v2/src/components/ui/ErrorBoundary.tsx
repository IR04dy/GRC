import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertOctagon, RefreshCw } from 'lucide-react';

/**
 * App-root error boundary.
 *
 * <p>Catches any render-time crash in the page tree and shows a recovery
 * card instead of the white-screen-of-death. The "Reload" button reloads
 * the SPA from scratch (clearing whatever bad state caused the crash).
 *
 * <p>Functional errors (a failed fetch, a bad API response) should be
 * handled by {@link useLoadable} + {@link ErrorState} inline — those
 * never bubble up here. This boundary is the last-resort net.
 *
 * <p><b>Stale-chunk auto-recovery.</b> Lazy-loaded routes are code-split, so
 * each build hashes its chunk filenames. When the server ships a new build
 * (a production deploy, or — in dev — Vite re-bundling after a source edit)
 * a tab opened against the <i>previous</i> build still references the old
 * hashes. Navigating to a not-yet-loaded route then 404s on a chunk that no
 * longer exists ("Failed to fetch dynamically imported module"), which lands
 * here as a render crash. That is a transient "your tab is older than the
 * server" condition, not a real bug, and a reload fixes it — so we reload
 * once automatically (guarded against loops) instead of showing the card.
 */
interface State {
  err: Error | null;
  /** True while an automatic stale-chunk reload is in flight — render a quiet
   *  placeholder rather than flashing the error card before the reload lands. */
  recovering: boolean;
}

/** Cross-browser signatures of a failed code-split chunk fetch. */
const CHUNK_ERROR_RE =
  /failed to fetch dynamically imported module|error loading dynamically imported module|importing a module script failed|chunkloaderror|loading chunk \S+ failed|loading css chunk/i;

function isChunkLoadError(err: Error | null): boolean {
  if (!err) return false;
  return CHUNK_ERROR_RE.test(err.message ?? '') || CHUNK_ERROR_RE.test(err.name ?? '');
}

/** sessionStorage key holding the last auto-reload timestamp (loop guard). */
const RELOAD_GUARD_KEY = 'grc.chunkReloadAt';
/** Don't auto-reload more than once per this window — if a reload didn't fix
 *  it (chunk genuinely gone), show the card instead of looping forever. */
const RELOAD_GUARD_MS = 10_000;

export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { err: null, recovering: false };

  static getDerivedStateFromError(err: Error): Partial<State> {
    return { err };
  }

  componentDidCatch(err: Error, info: ErrorInfo): void {
    // A stale code-split chunk is transient and self-heals on reload — recover
    // automatically (at most once per guard window) rather than blaming the user.
    if (isChunkLoadError(err) && this.tryAutoReload()) {
      this.setState({ recovering: true });
      return;
    }
    // Surface to the browser console for triage. We deliberately don't ship
    // this to a remote logger because doing so from inside the renderer can
    // amplify the crash. Use the network tab to inspect the failing call.
    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary]', err, info.componentStack);
  }

  /** Reload at most once per guard window. Returns true if a reload was kicked
   *  off (so the caller can show the recovering placeholder). */
  private tryAutoReload(): boolean {
    try {
      const now = Date.now();
      const last = Number(sessionStorage.getItem(RELOAD_GUARD_KEY) ?? 0);
      if (now - last < RELOAD_GUARD_MS) return false; // already tried — let the card show
      sessionStorage.setItem(RELOAD_GUARD_KEY, String(now));
    } catch {
      /* sessionStorage unavailable (private mode / quota) — reload anyway; the
         worst case is one extra reload, never a loop the user can't escape. */
    }
    window.location.reload();
    return true;
  }

  render() {
    // Auto-recovery in flight — quiet placeholder, no scary card, reload imminent.
    if (this.state.recovering) {
      return <div className="page" aria-busy="true" />;
    }

    if (!this.state.err) return this.props.children;

    return (
      <div className="error-boundary-card" role="alert">
        <AlertOctagon size={36} strokeWidth={1.8} />
        <h2>Something broke on this page</h2>
        <p>
          We couldn’t finish rendering this view. The page state has been preserved —
          click reload to start a fresh session.
        </p>
        {this.state.err.message && (
          <pre className="error-boundary-detail">{this.state.err.message}</pre>
        )}
        <button type="button"
                className="btn btn-primary"
                onClick={() => window.location.reload()}>
          <RefreshCw size={16} strokeWidth={2} /> Reload
        </button>
      </div>
    );
  }
}
