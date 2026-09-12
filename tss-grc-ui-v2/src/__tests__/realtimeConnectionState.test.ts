import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { stripComments } from './support/sourceScan';

/**
 * JRN-01 — the realtime module may not keep its own copy of "am I connected?".
 *
 * <h3>What was wrong</h3>
 * `lib/realtime.ts` kept `let connected = false`, set true in `onConnect` and false in
 * `onDisconnect`, and `subscribe()` branched on it. In @stomp/stompjs `onDisconnect` is invoked
 * from exactly one place — `StompHandler.dispose()`, inside the receipt callback for a *graceful*
 * DISCONNECT frame, reached only via `client.deactivate()`. An **abrupt** drop (network loss,
 * backend restart, laptop sleep, heartbeat timeout) fires `_webSocket.onclose` instead, which runs
 * `_cleanUp()` (clearing the library's own `_connected`) and then `onWebSocketClose` — which this
 * module did not handle. So its flag stayed TRUE over a dead connection, `subscribe()` took the
 * connected branch, and `Client.subscribe()`'s `_checkConnection()` threw
 * `TypeError: There is no underlying STOMP connection` inside a `useEffect`, which React escalates
 * to the error boundary.
 *
 * <p>🔑 The crash was on the SUBSCRIBE half and the stale guard produced it. The unsubscribe half
 * cannot raise that error at all: `StompSubscription.unsubscribe()` routes to
 * `StompHandler.unsubscribe()`, which never calls `_checkConnection()`.
 *
 * <h3>Why re-attaching had to land in the same commit</h3>
 * `onConnect` drained only the queued-before-first-connect list, so every already-active
 * registration kept a handle from the dead session and nothing re-established it. Fixing the flag
 * alone would have converted a loud crash into permanent silence — strictly harder to notice
 * (F2-197), and invisible to every instrument here.
 *
 * <h3>Which legs discriminate (F2-225)</h3>
 * The first four fire on the pre-fix module. {@code theSourceKeepsNoCachedConnectionFlag} is the
 * structural tripwire that stops the cache being re-added; the two guard legs assert the fix did
 * not break the documented contract, and the census leg is a work list whose tripwire is a future
 * sixth fallback page.
 */

const ROOT = join(process.cwd(), 'src');

/** What the fake broker was asked to do, so a leg can assert absence as well as presence. */
interface BrokerLog { subscribed: string[]; unsubscribed: string[] }

interface Harness {
  subscribe: (topic: string, handler: (e: any) => void) => () => void;
  disconnectRealtime: () => void;
  log: BrokerLog;
  connect: () => void;
  abruptDrop: () => void;
  gracefulDisconnect: () => void;
  deliver: (topic: string, payload: unknown) => void;
}

/**
 * Build the module fresh over a fake `@stomp/stompjs`.
 *
 * ⚠ The fake's `subscribe` mirrors `Client._checkConnection()` exactly — it throws the library's
 * real message when not connected. Without that the crash leg would pass over the pre-fix module
 * for the wrong reason, which is the failure mode a control cannot see.
 */
async function harness(): Promise<Harness> {
  vi.resetModules();

  const log: BrokerLog = { subscribed: [], unsubscribed: [] };
  const live = { value: false };
  const cfg: { current: any } = { current: null };
  const handlers = new Map<string, (msg: { body: string }) => void>();
  let counter = 0;

  class FakeClient {
    constructor(config: any) { cfg.current = config; }
    activate() { /* the real client opens a socket; this harness drives connect by hand */ }
    deactivate() { live.value = false; }
    get connected() { return live.value; }
    set connectHeaders(_h: unknown) { /* beforeConnect writes this */ }
    subscribe(topic: string, cb: (msg: { body: string }) => void) {
      if (!live.value) throw new TypeError('There is no underlying STOMP connection');
      log.subscribed.push(topic);
      const id = `sub-${counter++}`;
      handlers.set(id, cb);
      return { id, unsubscribe: () => { log.unsubscribed.push(topic); handlers.delete(id); } };
    }
  }

  vi.doMock('@stomp/stompjs', () => ({ Client: FakeClient }));
  vi.doMock('../lib/auth', () => ({ getAccessToken: () => 'fake.jwt.token' }));

  const mod = await import('../lib/realtime');

  // ⚠ The client is built lazily on the first subscribe, so without this `connect()` would fire
  // `onConnect` on a null config and the module would never leave its disconnected branch — a leg
  // would then pass over the PRE-FIX module for a reason unrelated to its claim (F2-244), which is
  // exactly what the pre-fix control caught. Priming is symmetric and leaves no trace in `log`:
  // while disconnected both the old and the new module hold the registration without attaching, and
  // releasing it touches no broker handle.
  const primed = mod.subscribe('/__prime__', () => {});
  primed();

  return {
    subscribe: mod.subscribe,
    disconnectRealtime: mod.disconnectRealtime,
    log,
    connect: () => { live.value = true; cfg.current?.onConnect?.({}); },
    // The abrupt half. `?.` is deliberate: the pre-fix module declares no `onWebSocketClose`, and a
    // control has to be able to run against it.
    abruptDrop: () => { live.value = false; cfg.current?.onWebSocketClose?.({}); },
    gracefulDisconnect: () => { live.value = false; cfg.current?.onDisconnect?.({}); },
    deliver: (topic, payload) => {
      const body = JSON.stringify({ type: 'x', ts: '2026-09-12T00:00:00Z', data: payload });
      handlers.forEach((cb, id) => { if (id !== undefined) void id; cb({ body }); });
      void topic;
    },
  };
}

beforeEach(() => { vi.resetModules(); });

describe('JRN-01 — realtime connection state has one copy, and it is the library\'s', () => {
  it('subscribing after an abrupt drop does not throw', async () => {
    const h = await harness();
    h.connect();
    h.subscribe('/topic/org.1.changed.risk', () => {});
    h.abruptDrop();

    // The audited journey: register -> record -> connection dies -> navigate back -> the register
    // remounts and its useLiveRefresh effect subscribes again. Pre-fix this threw the library's
    // TypeError out of a useEffect, which React escalates to the error boundary.
    expect(() => h.subscribe('/topic/org.1.changed.risk', () => {}),
      'A subscribe while the connection is down must be queued, not attempted. The module cached '
      + 'its own `connected`, which only `onDisconnect` cleared — and `onDisconnect` fires only on '
      + 'a graceful deactivate(), never on a dropped socket.').not.toThrow();
  });

  it('every registration is re-attached on reconnect, not just the queued ones', async () => {
    const h = await harness();
    h.connect();
    h.subscribe('/topic/org.1.changed.risk', () => {});
    expect(h.log.subscribed, 'attached once while connected').toEqual(['/topic/org.1.changed.risk']);

    h.abruptDrop();
    h.connect();

    expect(h.log.subscribed, 'After a drop every handle belongs to the dead session. `onConnect` '
      + 'used to drain only the queued-before-first-connect list, so a live subscription was never '
      + 're-established and realtime went permanently silent after the first drop — which is why '
      + 'this leg had to land with the flag fix rather than after it (F2-197).')
      .toEqual(['/topic/org.1.changed.risk', '/topic/org.1.changed.risk']);
  });

  it('unsubscribing after a drop does not transmit on the dead session', async () => {
    const h = await harness();
    h.connect();
    const off = h.subscribe('/topic/org.1.changed.risk', () => {});
    h.abruptDrop();

    expect(() => off(), 'Effect cleanup runs during unmount, where a throw escalates to the error '
      + 'boundary.').not.toThrow();
    expect(h.log.unsubscribed, 'The broker discarded the subscription when the socket closed, so '
      + 'an UNSUBSCRIBE frame on that session is pointless — and a future @stomp/stompjs could '
      + 'route it through the same _checkConnection() that subscribe/publish/ack already use.')
      .toEqual([]);
  });

  it('two components may share a topic, and one unsubscribing leaves the other attached', async () => {
    const h = await harness();
    h.connect();
    const offA = h.subscribe('/topic/org.1.changed.risk', () => {});
    h.subscribe('/topic/org.1.changed.risk', () => {});
    offA();

    expect(h.log.subscribed, 'both attached').toHaveLength(2);
    expect(h.log.unsubscribed, 'The old Map<string, StompSubscription> was keyed by TOPIC, so the '
      + 'second subscribe overwrote the first handle and the first unsubscribe released the '
      + 'survivor. Latent — no two pages sharing a topic are mounted at once today — and absent '
      + 'now rather than latent (F2-213).').toEqual(['/topic/org.1.changed.risk']);

    // The surviving registration is still live: releasing it reaches the broker.
    h.disconnectRealtime();
    expect(h.log.unsubscribed, 'the survivor was still attached').toHaveLength(2);
  });

  it('GUARD — subscribing before the first connect still queues and replays', async () => {
    const h = await harness();
    const off = h.subscribe('/topic/org.1.changed.risk', () => {});
    expect(h.log.subscribed, 'nothing to attach to yet').toEqual([]);

    h.connect();
    expect(h.log.subscribed, 'subscribe() documents this contract').toEqual(['/topic/org.1.changed.risk']);

    off();
    expect(h.log.unsubscribed, 'released while connected').toEqual(['/topic/org.1.changed.risk']);
  });

  it('GUARD — a delivered message still reaches the handler, and stops after unsubscribe', async () => {
    const h = await harness();
    h.connect();
    const seen: unknown[] = [];
    const off = h.subscribe('/topic/org.1.changed.risk', env => seen.push(env.data));

    h.deliver('/topic/org.1.changed.risk', { id: 7 });
    expect(seen, 'the envelope is parsed and handed to the caller').toEqual([{ id: 7 }]);

    off();
    h.deliver('/topic/org.1.changed.risk', { id: 8 });
    expect(seen, 'no delivery after release').toEqual([{ id: 7 }]);
  });

  it('the source keeps no cached connection flag', async () => {
    // ⚠ Comments stripped: the fix documents the retired mechanism by name, so a comment-blind
    // scan reads the epitaph as the corpse (F2-193) and fails on a correct tree.
    const src = stripComments(readFileSync(join(ROOT, 'lib', 'realtime.ts'), 'utf8'));

    expect(/\b(?:let|var)\s+connected\b/.test(src),
      'A module-level `connected` is a second copy of a fact the library already owns, and it is '
      + 'the copy with no vote — the library clears its own in `_cleanUp()` on the socket close '
      + 'this module never heard about.').toBe(false);
    expect(src.includes('client.connected'),
      'Read the library\'s own connection state rather than caching it.').toBe(true);
    expect(src.includes('onWebSocketClose'),
      'The abrupt-drop callback. Without it the module learns about a dead connection only when a '
      + 'graceful deactivate() happens to be called.').toBe(true);
    expect(src.length, 'anti-vacuity — a stripper that emptied the file would pass every assertion '
      + 'above (F2-198)').toBeGreaterThan(1500);
  });

  it('every primary-record fallback gates on isDemoId — this is an exact census', () => {
    // JRN-01's third site. Five pages fall back to a fabricated record when a PRIMARY record load
    // fails; four gated it on `isDemoId` and RiskDetailPage did not, relying instead on
    // `demoRows()` shifting its corpus past DEMO_ID_BASE in a file it merely imports (F2-182).
    const PAGES = [
      'pages/detail/ControlDetailPage.tsx',
      'pages/detail/DpiaDetailPage.tsx',
      'pages/detail/FindingDetailPage.tsx',
      'pages/detail/VendorDetailPage.tsx',
      'pages/risk/RiskDetailPage.tsx',
    ];
    const unguarded = PAGES.filter(p =>
      !stripComments(readFileSync(join(ROOT, ...p.split('/')), 'utf8')).includes('isDemoId(Number(id))'));

    expect(unguarded, 'A fabricated record may only stand in for a fabricated id. A page relying on '
      + 'another file\'s id shift is one edit away from rendering an invented record under the '
      + 'title the user clicked — and a ceiling over mock IMPORTS cannot see which of the five '
      + 'carries an explicit guard, which is exactly the distinction (F2-198).').toEqual([]);
  });
});
