// Realtime client — Sprint 4 §2.7.
//
// STOMP over SockJS, talking to the Spring backend at /ws. Use this instead
// of `setInterval` polling for live notifications, lifecycle transitions, and
// SLA pulses.
//
// Multi-tenant safety: the server-side handshake validates the JWT in the
// CONNECT frame and rejects subscriptions whose topic prefix doesn't match
// the user's `org` claim. Don't try to subscribe to other tenants — you'll
// get an ERROR frame.
//
// JRN-01 — connection state has exactly ONE copy, and it is the library's.
//
// This module used to keep its own `let connected = false`, set true in
// `onConnect` and false in `onDisconnect`. That is two copies of one fact, and
// the copy this module's guard read was the one with no vote (F2-177/F2-205):
//
//   * `onDisconnect` is invoked from exactly ONE place in @stomp/stompjs —
//     `StompHandler.dispose()`, inside the receipt callback for a *graceful*
//     DISCONNECT frame, reached only via `client.deactivate()`, i.e. our own
//     logout below.
//   * An ABRUPT drop — network loss, backend restart, laptop sleep, a
//     heartbeat timeout — fires `_webSocket.onclose` instead, which runs
//     `_cleanUp()` (setting the library's own `_connected = false`) and then
//     `onWebSocketClose`. This module did not handle that, so its flag stayed
//     TRUE over a dead connection.
//   * `subscribe()` then took the connected branch, `Client.subscribe()` ran
//     `_checkConnection()`, and the caller got
//     `TypeError: There is no underlying STOMP connection` — thrown inside a
//     `useEffect`, which React escalates to the error boundary.
//
// 🔑 So the crash was on the SUBSCRIBE half, and the stale guard is what
// produced it. The unsubscribe half cannot raise that error at all:
// `StompSubscription.unsubscribe()` routes to `StompHandler.unsubscribe()`,
// which never calls `_checkConnection()` — it reaches `_webSocket.send()`,
// and per the WHATWG spec `send()` on a CLOSED socket is a silent no-op.
//
// ⚠ Re-attaching on connect is NOT a separate improvement — it has to land
// with the flag fix. `onConnect` used to drain only `pendingSubscriptions`,
// leaving every already-active registration holding a handle from the dead
// session, so after the first drop no event ever arrived again. Fixing the
// flag alone would have turned a loud crash into permanent silence, which is
// strictly harder to notice (F2-197).

import { Client, type StompSubscription, type Message } from '@stomp/stompjs';
import { getAccessToken } from './auth';

export type RealtimeHandler = (envelope: { type: string; ts: string; data: any }) => void;

/**
 * One subscription as this module understands it: what the caller asked for,
 * plus the live broker handle when there is one.
 *
 * `sub === null` means "not currently attached" and covers both states the old
 * code kept in separate collections — queued before the first connect, and
 * orphaned by a drop. A Set of records rather than a Map keyed by topic,
 * because two components may legitimately subscribe to the same topic and the
 * old `Map<string, StompSubscription>` could only hold one handle for it: the
 * second subscribe overwrote the first, and the first unsubscribe killed both.
 * Latent (no two pages sharing a topic are mounted at once today) and absent
 * now rather than latent (F2-213).
 */
interface Registration {
  topic: string;
  handler: RealtimeHandler;
  sub: StompSubscription | null;
}

let client: Client | null = null;
const registrations = new Set<Registration>();

function backendBaseUrl(): string {
  const apiBase = (import.meta.env?.VITE_API_BASE ?? '').replace(/\/$/, '');
  return apiBase || (typeof window !== 'undefined' ? window.location.origin : '');
}

/** Build the raw WebSocket URL — `ws://host:port/ws/websocket`. Uses
 *  Spring SockJS's native-WebSocket sub-path (`/websocket`) which bypasses
 *  SockJS HTTP transport fallbacks. STOMP CONNECT/SUBSCRIBE/MESSAGE frames
 *  flow normally over raw WebSocket.
 *
 *  Dev caveat: Vite's `/ws` HTTP proxy with `ws: true` doesn't reliably
 *  forward WebSocket upgrade requests to Spring (HMR collision + Vite
 *  internals). When `VITE_WS_BASE` is set we use that; otherwise in dev
 *  (port 5173) we fall back to the backend port directly. In prod the
 *  origin is shared so we just stay on `location.origin`.
 */
function brokerUrl(): string {
  const explicitWs = (import.meta.env?.VITE_WS_BASE ?? '').replace(/\/$/, '');
  if (explicitWs) {
    return explicitWs.replace(/^http/, 'ws') + '/ws/websocket';
  }
  const base = backendBaseUrl();
  // Dev fallback: Vite serves the SPA on :5173 but the Spring backend lives
  // on :8084. The HTTP proxy on /api works for fetch, but WebSocket upgrade
  // needs a direct connection.
  if (typeof window !== 'undefined' && window.location.port === '5173') {
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${proto}://${window.location.hostname}:8084/ws/websocket`;
  }
  return base.replace(/^http/, 'ws') + '/ws/websocket';
}

/**
 * Is the broker connection live *right now*?
 *
 * `Client.connected` is `!!this._stompHandler && this._stompHandler.connected`,
 * and the handler clears its own flag in `_cleanUp()` — which runs on the
 * socket's `onclose`, i.e. on the abrupt drop this module used to miss. Read
 * it; never cache it.
 */
function isConnected(): boolean {
  return !!client && client.connected;
}

/** Attach a registration to the broker, if there is a broker to attach to. */
function attach(reg: Registration): void {
  if (!isConnected()) return;
  reg.sub = client!.subscribe(reg.topic, (msg: Message) => {
    try { reg.handler(JSON.parse(msg.body)); }
    catch (e) { console.warn('[realtime] malformed message on', reg.topic, e); }
  });
}

/**
 * Release a registration's broker handle.
 *
 * ⚠ Guarded on the live connection and wrapped: an UNSUBSCRIBE on a dead
 * session is pointless (the broker discarded the subscription when the socket
 * closed) and a future @stomp/stompjs could route it through the same
 * `_checkConnection()` that `publish`/`subscribe`/`ack` already use. Either
 * way this runs inside a React effect cleanup, where a throw escalates to the
 * error boundary during unmount.
 */
function detach(reg: Registration): void {
  if (reg.sub && isConnected()) {
    try { reg.sub.unsubscribe(); }
    catch (e) { console.warn('[realtime] unsubscribe failed for', reg.topic, e); }
  }
  reg.sub = null;
}

/** Every handle from the previous session is dead. Drop them without transmitting. */
function orphanAll(): void {
  registrations.forEach(reg => { reg.sub = null; });
}

/** Lazily construct the STOMP client. Called on first subscribe. */
function ensureClient(): Client {
  if (client) return client;
  client = new Client({
    brokerURL: brokerUrl(),
    // The token can refresh mid-session (PR #143 added transparent 401 →
    // /refresh → retry). Reading connectHeaders once at client creation
    // would mean every STOMP reconnect (reconnectDelay=5s) reuses the
    // stale token captured at app start — real-time notifications would
    // silently die after the first refresh. `beforeConnect` is the
    // @stomp/stompjs hook to re-evaluate headers on every (re)connect.
    beforeConnect: () => {
      const tok = getAccessToken() ?? '';
      if (client) client.connectHeaders = { Authorization: `Bearer ${tok}` };
    },
    reconnectDelay: 5000,                 // initial reconnect attempt after 5s
    heartbeatIncoming: 30_000,
    heartbeatOutgoing: 30_000,
    debug: () => { /* silenced — set to console.log when debugging */ },
    // Re-attach EVERY registration, not just the ones queued before the first
    // connect. `_connected = true` is set before this fires, so `attach` sees
    // a live connection. On a first connect every registration is unattached;
    // on a reconnect every previously-live handle belongs to the dead session,
    // which is why this does not discriminate between them.
    onConnect: () => {
      registrations.forEach(reg => { reg.sub = null; attach(reg); });
    },
    // The graceful half — our own `disconnectRealtime()` below.
    onDisconnect: () => { orphanAll(); },
    // The abrupt half, and the one this module used to be deaf to.
    onWebSocketClose: () => { orphanAll(); },
    onStompError: (frame) => {
      console.warn('[realtime] STOMP error', frame.headers, frame.body);
    },
  });
  client.activate();
  return client;
}

/**
 * Subscribe to a topic. Returns an unsubscribe function. Safe to call before
 * connection is established, and safe to call while the connection is down —
 * the registration is held and attached on the next connect.
 *
 * Topic conventions:
 *   /topic/org.{orgId}.notifications           — tenant-wide notifications
 *   /topic/org.{orgId}.entity.{type}.{id}      — record changes
 *   /user/queue/notifications                  — direct-to-current-user
 */
export function subscribe(topic: string, handler: RealtimeHandler): () => void {
  ensureClient();
  const reg: Registration = { topic, handler, sub: null };
  registrations.add(reg);
  attach(reg);
  return () => {
    registrations.delete(reg);
    detach(reg);
  };
}

/** Read the orgId out of the stored JWT (decoded only — signature was already
 *  verified by the server on every API call). */
export function currentOrgId(): string | null {
  try {
    const tok = getAccessToken();
    if (!tok) return null;
    const payload = JSON.parse(atob(tok.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return payload?.org ?? null;
  } catch { return null; }
}

/** Disconnect everything. Call on logout. */
export function disconnectRealtime(): void {
  registrations.forEach(detach);
  registrations.clear();
  if (client) {
    client.deactivate();
    client = null;
  }
}
