import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { stripComments, walkSources, relPosix } from './support/sourceScan';

/**
 * F2-327 — a realtime subscription whose handler does not RE-FETCH is inert for the
 * cross-user updates it exists to deliver.
 *
 * F2-205 recorded a blind spot NO instrument in this tree could see: `VulnerabilitiesV2Page`
 * subscribed to the server broadcast `changed.vulnerability` and answered it by re-reading a
 * browser-local store — so another user's real transition arrived as a websocket frame that
 * refreshed a constant no server event could ever change. Every component was genuine; only the
 * direction the data flowed was wrong, which is why a reachability scan (is the symbol called?)
 * and a provenance gate (does the page call a server?) both answer "yes" over it. F2-205 fixed the
 * one instance it found and left the generalisation: "when you find a realtime subscription, read
 * what its handler does with the frame."
 *
 * Asked of every `useRealtimeOrgEvent` consumer, the generalisation found two more instances of
 * the exact shape — `RiskLifecyclePage` (`changed.treatment_plan`) and `AuditLifecyclePage`
 * (`changed.audit_finding`) — each a hydrate-once SERVER cache (`hydrateTreatmentPlans` /
 * `hydrateAuditLifecycle`, both `if (_promise && !force) return _promise`) whose handler called
 * only `setTick(t => t + 1)`. A bare tick re-renders the STALE cache; the other user's change is on
 * the server, not in this browser's `_plans`/`_findings`, so the subscription was inert for its
 * sole purpose. The fix is F2-205's proven pattern: `hydrateX(true).then(re-render)` — `force`
 * bypasses the cached promise and `emit()`s, so the page re-derives over fresh rows.
 *
 * This gate closes F2-205's blind spot: it enumerates every `useRealtimeOrgEvent` handler and
 * requires each to RE-FETCH — a `hydrate*(true)` force-refresh, or a call to an async server
 * loader — never a bare state-setter over a cached/local read. The population is asserted EXACTLY
 * (F2-204): a new consumer forces a verdict here, and a stale verdict fails the reverse leg.
 */

const SRC = join(__dirname, '..');

type Verdict =
  | { verdict: 'REFETCH'; token: RegExp; why: string }
  | { verdict: 'FILED';   why: string };

// key = "<relative page path> :: <topic>"
const SITES: Record<string, Verdict> = {
  'pages/cyber/VulnerabilitiesV2Page.tsx :: changed.vulnerability': {
    verdict: 'REFETCH', token: /hydrateVulns\(\s*true\b/,
    why: 'F2-205, the exemplar: a broadcast means the server copy moved, so force-refetch.',
  },
  'pages/privacy-lifecycle/PrivacyLifecyclePage.tsx :: changed.dsr_case': {
    verdict: 'REFETCH', token: /loadDsrCases\(\)/,
    why: 'loadDsrCases is an async server fetch (useCallback(async …)); calling it re-fetches.',
  },
  'pages/risk-lifecycle/RiskLifecyclePage.tsx :: changed.treatment_plan': {
    verdict: 'REFETCH', token: /hydrateTreatmentPlans\(\s*true\b/,
    why: 'F2-327: hydrateTreatmentPlans(true) force-refetches the server cache _plans.',
  },
  'pages/audit-lifecycle/AuditLifecyclePage.tsx :: changed.audit_finding': {
    verdict: 'REFETCH', token: /hydrateAuditLifecycle\(\s*true\b/,
    why: 'F2-327: hydrateAuditLifecycle(true) force-refetches the /api/audit-lc/* caches.',
  },
  'pages/bcm-lifecycle/BcmLifecyclePage.tsx :: changed.bcm_invocation': {
    verdict: 'FILED',
    why: 'F2-204: bcmLifecycle invocations is a pure browser-local store (grc.bcm.invocation.v1, ' +
         'SEED_INVOCATIONS fallback) with NO server hydrate — so the handler cannot force-refetch. ' +
         'The Kind-B fix is to wire the store to a server endpoint first; filed, not this slice. ' +
         'This entry fails if someone adds a fake refetch over the still-local store, or genuinely ' +
         'wires it (move to REFETCH then).',
  },
};

/** Extract every useRealtimeOrgEvent(topic, handler) call from comment-stripped source. */
function realtimeCalls(src: string): { topic: string; handler: string }[] {
  const out: { topic: string; handler: string }[] = [];
  const re = /useRealtimeOrgEvent\s*\(/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(src)) !== null) {
    const open = m.index + m[0].length - 1; // index of '('
    let depth = 0, i = open;
    for (; i < src.length; i++) {
      if (src[i] === '(') depth++;
      else if (src[i] === ')') { depth--; if (depth === 0) break; }
    }
    const args = src.slice(open + 1, i);
    const tm = args.match(/^\s*['"]([^'"]+)['"]\s*,/);
    out.push({ topic: tm ? tm[1] : '(unparsed)', handler: tm ? args.slice(tm[0].length) : args });
  }
  return out;
}

function foundSites(): Map<string, string> {
  const sites = new Map<string, string>();
  for (const file of walkSources(join(SRC, 'pages'))) {
    if (/\.test\.tsx?$/.test(file)) continue;
    const src = stripComments(readFileSync(file, 'utf8'));
    if (!src.includes('useRealtimeOrgEvent')) continue;
    const rel = relPosix(SRC, file);
    for (const { topic, handler } of realtimeCalls(src)) {
      sites.set(`${rel} :: ${topic}`, handler);
    }
  }
  return sites;
}

describe('F2-327 realtime subscriptions must re-fetch, not re-render a cache', () => {
  const sites = foundSites();

  it('found the useRealtimeOrgEvent population (the scan has not rotted)', () => {
    expect(sites.size, 'no useRealtimeOrgEvent sites parsed — the scan has rotted').toBeGreaterThanOrEqual(5);
  });

  it('every subscription site has a recorded verdict, and every verdict names a live site', () => {
    // Forward: a new consumer must be classified here (F2-204 exact population).
    for (const key of sites.keys()) {
      expect(SITES[key], `unclassified realtime subscription: ${key} — add a verdict`).toBeTruthy();
    }
    // Reverse: a verdict for a site that no longer exists is stale (F2-199).
    for (const key of Object.keys(SITES)) {
      expect(sites.has(key), `stale verdict for a subscription that no longer exists: ${key}`).toBe(true);
    }
  });

  it('every REFETCH handler actually re-fetches from the server', () => {
    for (const [key, v] of Object.entries(SITES)) {
      if (v.verdict !== 'REFETCH') continue;
      const handler = sites.get(key)!;
      expect(v.token.test(handler),
        `${key}: handler must re-fetch (${v.token}). A bare setTick/setState re-renders the ` +
        `STALE cache and the subscription is inert for cross-user updates (F2-205/F2-327). Handler: ${handler.trim()}`)
        .toBe(true);
    }
  });

  it('a FILED site does not masquerade as fixed with a force-refetch it cannot honour', () => {
    for (const [key, v] of Object.entries(SITES)) {
      if (v.verdict !== 'FILED') continue;
      const handler = sites.get(key)!;
      expect(/hydrate\w*\(\s*true\b/.test(handler),
        `${key}: this store is unwired (F2-204); a force-refetch here is a lie unless the store ` +
        `is wired to a server first. If you wired it, change the verdict to REFETCH.`)
        .toBe(false);
    }
  });

  // Negative control (F2-208/F2-274): the classifier must FLAG a bare setter, or a green gate is vacuous.
  it('self-test: a bare setTick handler carries no refetch token', () => {
    const calls = realtimeCalls("useRealtimeOrgEvent('x', () => setTick(t => t + 1));");
    expect(calls).toHaveLength(1);
    expect(calls[0].topic).toBe('x');
    const bare = calls[0].handler;
    for (const v of Object.values(SITES)) {
      if (v.verdict === 'REFETCH') {
        expect(v.token.test(bare), `token ${v.token} must NOT match a bare setter`).toBe(false);
      }
    }
    // and the multi-line force-refetch form the fix uses IS recognised
    const fixed = realtimeCalls(
      "useRealtimeOrgEvent('changed.treatment_plan', () => {\n  void hydrateTreatmentPlans(true).then(() => setTick(t => t + 1));\n});");
    expect(/hydrateTreatmentPlans\(\s*true\b/.test(fixed[0].handler)).toBe(true);
  });
});
