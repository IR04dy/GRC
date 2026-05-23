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
// Status: SCAFFOLD. Auto-reconnect, exponential backoff, and offline queue
// are the next iteration.

import { Client, type StompSubscription, type Message } from '@stomp/stompjs';
import { getAccessToken } from './auth';

export type RealtimeHandler = (envelope: { type: string; ts: string; data: any }) => void;

let client: Client | null = null;
let connected = false;
const pendingSubscriptions: Array<{ topic: string; handler: RealtimeHandler }> = [];
const activeSubscriptions = new Map<string, StompSubscription>();

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
    onConnect: () => {
      connected = true;
      // Drain any subscribe-before-connect calls
      while (pendingSubscriptions.length) {
        const p = pendingSubscriptions.shift()!;
        doSubscribe(p.topic, p.handler);
      }
    },
    onDisconnect: () => { connected = false; },
    onStompError: (frame) => {
      console.warn('[realtime] STOMP error', frame.headers, frame.body);
    },
  });
  client.activate();
  return client;
}

function doSubscribe(topic: string, handler: RealtimeHandler) {
  if (!client) return;
  const sub = client.subscribe(topic, (msg: Message) => {
    try { handler(JSON.parse(msg.body)); }
    catch (e) { console.warn('[realtime] malformed message on', topic, e); }
  });
  activeSubscriptions.set(topic, sub);
}

/**
 * Subscribe to a topic. Returns an unsubscribe function. Safe to call before
 * connection is established — subscriptions queue and replay on connect.
 *
 * Topic conventions:
 *   /topic/org.{orgId}.notifications           — tenant-wide notifications
 *   /topic/org.{orgId}.entity.{type}.{id}      — record changes
 *   /user/queue/notifications                  — direct-to-current-user
 */
export function subscribe(topic: string, handler: RealtimeHandler): () => void {
  ensureClient();
  if (connected) {
    doSubscribe(topic, handler);
  } else {
    pendingSubscriptions.push({ topic, handler });
  }
  return () => {
    const sub = activeSubscriptions.get(topic);
    if (sub) {
      sub.unsubscribe();
      activeSubscriptions.delete(topic);
    } else {
      const idx = pendingSubscriptions.findIndex(p => p.topic === topic && p.handler === handler);
      if (idx >= 0) pendingSubscriptions.splice(idx, 1);
    }
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
  activeSubscriptions.forEach(s => s.unsubscribe());
  activeSubscriptions.clear();
  pendingSubscriptions.length = 0;
  if (client) {
    client.deactivate();
    client = null;
    connected = false;
  }
}
