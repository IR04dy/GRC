import { createContext, type Context } from 'react';

/**
 * A React context whose **identity survives Vite's HMR module duplication**.
 *
 * <h3>The bug this fixes, as observed</h3>
 * Every hot update that touched `i18n.tsx` left the dev app's first render crashed with
 * `useI18n must be used inside <I18nProvider>` — thrown from `ToastProvider`, which React's own
 * stack showed as a **child** of `I18nProvider`. The nesting was never wrong. The module graph was:
 *
 * ```
 *   toast.tsx?t=1785875228775  →  imports useI18n from  i18n.tsx?t=1785875228775
 *   the <I18nProvider> that actually rendered came from  i18n.tsx?t=1785875228705
 * ```
 *
 * Two instances of one module, so two separate `createContext()` objects. The provider filled one;
 * the consumer read the other; the guard fired, correctly, on a context nobody had provided.
 * `ErrorBoundary` then remounted the tree from scratch, which is why the page *looked* fine and the
 * errors read as cosmetic — they are not. Every frontend verification pass began with a crashed
 * render, and it repeatedly cast doubt on changes that were sound.
 *
 * <h3>Why keying on `globalThis` is the fix and not a hack</h3>
 * The duplicated modules share one realm, so a registry on `globalThis` is the one thing both
 * instances can agree on. The second instance to load finds the first instance's context and returns
 * it, so provider and consumer are looking at the same object again.
 *
 * <h3>What this deliberately does NOT do</h3>
 * <ul>
 *   <li><b>It does not preserve state.</b> Only the context *identity* is shared; the provider's
 *       value still comes from whichever provider rendered. A hot update still resets state, as it
 *       should.</li>
 *   <li><b>It does not mask a genuinely missing provider.</b> The cached context keeps its
 *       {@code null} default, so `useX must be used inside <XProvider>` still throws when no
 *       provider rendered — which is the case those guards exist for.</li>
 *   <li><b>It is inert in production.</b> A production bundle contains exactly one instance of each
 *       module, so the registry is populated once and every lookup is the first one.</li>
 * </ul>
 *
 * <h3>Keys</h3>
 * The key must be stable across reloads and unique per context — the module's role, not a variable.
 * A duplicate key would hand two different contexts the same slot, which is why
 * `stableContext.test.ts` pins the list of keys in use.
 */
export function stableContext<T>(key: string, defaultValue: T): Context<T> {
  const g = globalThis as unknown as { __grcStableContexts?: Map<string, unknown> };
  const registry = (g.__grcStableContexts ??= new Map<string, unknown>());
  const existing = registry.get(key);
  if (existing) return existing as Context<T>;
  const created = createContext<T>(defaultValue);
  registry.set(key, created);
  return created;
}

/** The keys in use. Exported so a test can assert they are unique and that none was dropped. */
export const STABLE_CONTEXT_KEYS = [
  'grc.i18n', 'grc.toast', 'grc.dialog', 'grc.theme', 'grc.entitlement',
] as const;
