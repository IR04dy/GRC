import { describe, it, expect, beforeEach } from 'vitest';
import { createContext } from 'react';
import { stableContext, STABLE_CONTEXT_KEYS } from '../lib/stableContext';

/**
 * The HMR context-duplication fix.
 *
 * Observed: every hot update touching `i18n.tsx` crashed the dev app's first render with
 * `useI18n must be used inside <I18nProvider>`, thrown from `ToastProvider` — which React's own
 * stack showed as a CHILD of `I18nProvider`. Two module instances (`i18n.tsx?t=…705` and
 * `?t=…775`) meant two `createContext()` objects: the provider filled one, the consumer read the
 * other. `ErrorBoundary` remounted the tree, so the page looked fine and the errors read as
 * cosmetic. They were not — every frontend verification pass started from a crashed render.
 */
describe('stableContext', () => {
  beforeEach(() => {
    delete (globalThis as unknown as { __grcStableContexts?: unknown }).__grcStableContexts;
  });

  it('returns the SAME context object for a repeated key — the whole point', () => {
    // Two calls with one key stands in for two instances of one module after a hot update.
    const first = stableContext<string | null>('grc.test', null);
    const second = stableContext<string | null>('grc.test', null);
    expect(second).toBe(first);
  });

  it('plain createContext does NOT — this is the bug being fixed', () => {
    // Kept as the contrast: without the registry, the second module instance builds a new context
    // and every consumer wired to it reads a provider that never filled it.
    expect(createContext<string | null>(null)).not.toBe(createContext<string | null>(null));
  });

  it('different keys get different contexts', () => {
    expect(stableContext<string | null>('grc.a', null))
      .not.toBe(stableContext<string | null>('grc.b', null));
  });

  it('the first caller\'s default wins, so a later duplicate cannot change the contract', () => {
    const first = stableContext<string>('grc.default', 'original');
    const second = stableContext<string>('grc.default', 'different');
    expect(second).toBe(first);
    // React exposes the default on the internal `_currentValue`; assert through the public shape we
    // can reach, which is that the object is identical — a changed default would mean a new object.
    expect(Object.is(first, second)).toBe(true);
  });

  it('a missing provider STILL throws — the fix must not mask that', () => {
    // The cached context keeps its null default, so the `useX must be used inside <XProvider>`
    // guards keep working for the case they exist for. This test would fail if stableContext ever
    // started handing out a non-null placeholder to keep consumers quiet.
    const ctx = stableContext<{ ok: boolean } | null>('grc.guard', null);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    expect((ctx as any)._currentValue).toBeNull();
  });

  it('the published key list is unique and covers the five lib providers', () => {
    // A duplicate key would hand two different contexts the same slot.
    expect(new Set(STABLE_CONTEXT_KEYS).size).toBe(STABLE_CONTEXT_KEYS.length);
    expect(STABLE_CONTEXT_KEYS).toContain('grc.i18n');
    expect(STABLE_CONTEXT_KEYS).toContain('grc.toast');
    expect(STABLE_CONTEXT_KEYS).toContain('grc.dialog');
    expect(STABLE_CONTEXT_KEYS).toContain('grc.theme');
    // Entitlement matters most: its default is FAIL_OPEN, so duplication would not crash — it would
    // silently serve fail-open to every consumer, i.e. entitlement switching itself off.
    expect(STABLE_CONTEXT_KEYS).toContain('grc.entitlement');
  });
});
