import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertOctagon, RefreshCw } from 'lucide-react';
import { readStoredLang } from '../../lib/i18n';

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

/**
 * ⚠⚠ The card's copy is INLINE and bilingual, which is deliberate rather than an oversight of
 * house rule 7's dictionary convention. THREE independent reasons, any one of which settles it:
 *
 *  1. This boundary is mounted ABOVE `I18nProvider` (App.tsx), so there is no i18n context to
 *     read — `useI18n()` throws "must be used inside <I18nProvider>". Mounting it INSIDE the
 *     provider instead would mean a crash in `ThemeProvider` or `I18nProvider` itself renders a
 *     white screen, which is the one condition this component exists to prevent.
 *  2. It is a CLASS component, because `getDerivedStateFromError` / `componentDidCatch` have no
 *     hook equivalent — so it could not call a hook even if the context were in scope.
 *  3. 🔑 The sharpest: `t()` resolves against a dictionary fetched as a SEPARATE CHUNK
 *     (PERF-FE-002), and a failed chunk fetch is precisely one of the crashes this card handles
 *     (see `CHUNK_ERROR_RE` above). A card whose own text depends on that fetch renders raw keys
 *     exactly when it is needed most.
 *
 * So do NOT "fix" this by moving the strings into `translations/`. `__tests__/crashCardBilingual`
 * pins all three reasons, in both directions.
 */
const COPY = {
  en: {
    dir: 'ltr' as const,
    title: 'Something broke on this page',
    /*
     * ⚠ This sentence read "The page state has been preserved — click reload to start a fresh
     * session" until JRN-12, one line above the `window.location.reload()` that DISCARDS exactly
     * that state — and it contradicted itself inside its own second clause. F2-184 at the one
     * moment a user is reading a screen for guidance, so the wording states the loss plainly.
     */
    body: 'We couldn’t finish rendering this view. Anything you had not saved is lost — reloading starts a fresh session.',
    reload: 'Reload',
  },
  ar: {
    dir: 'rtl' as const,
    title: 'تعذّر عرض هذه الصفحة',
    body: 'تعذّر إكمال عرض هذه الشاشة. أي تغييرات لم تُحفظ ستُفقد — وإعادة التحميل تبدأ جلسة جديدة.',
    reload: 'إعادة التحميل',
  },
} as const;

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

    // Read at RENDER time, not at construction: the language can be switched while the app is
    // running, and this component is never remounted in between.
    const copy = COPY[readStoredLang()];

    // `dir` is set explicitly rather than inherited. `I18nProvider` sets it on <html> from an
    // effect, so a crash in the provider itself — or in anything above it — leaves the document
    // at the LTR default and would render the Arabic card left-to-right.
    return (
      <div className="error-boundary-card" role="alert" dir={copy.dir}>
        <AlertOctagon size={36} strokeWidth={1.8} />
        <h2>{copy.title}</h2>
        <p>{copy.body}</p>
        {this.state.err.message && (
          <pre className="error-boundary-detail">{this.state.err.message}</pre>
        )}
        <button type="button"
                className="btn btn-primary"
                onClick={() => window.location.reload()}>
          <RefreshCw size={16} strokeWidth={2} /> {copy.reload}
        </button>
      </div>
    );
  }
}
