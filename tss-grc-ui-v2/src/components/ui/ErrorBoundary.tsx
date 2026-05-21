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
 */
interface State {
  err: Error | null;
}

export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { err: null };

  static getDerivedStateFromError(err: Error): State {
    return { err };
  }

  componentDidCatch(err: Error, info: ErrorInfo): void {
    // Surface to the browser console for triage. We deliberately don't ship
    // this to a remote logger because doing so from inside the renderer can
    // amplify the crash. Use the network tab to inspect the failing call.
    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary]', err, info.componentStack);
  }

  render() {
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
