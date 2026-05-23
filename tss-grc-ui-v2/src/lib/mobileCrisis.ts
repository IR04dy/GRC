// Slice 9-F — Mobile Crisis Room client.
//
// Talks to:
//   GET    /api/v1/bcm/crisis/active-summary       (commander-tier roles only)
//   POST   /api/v1/platform/push/subscriptions     (any authenticated user)
//   GET    /api/v1/platform/push/subscriptions
//   DELETE /api/v1/platform/push/subscriptions/{id}
//
// Surfaces typed shapes the mobile page consumes directly, plus a few
// pure helpers for Saudi-native countdown phrasing.

import { api } from './api';
// Re-export the pure helpers so callers can keep `from './mobileCrisis'`
// while the node:test harness imports them directly from the Vite-free path.
export {
  toEasternArabicDigits,
  formatCountdown,
  formatRelativeAgo,
  urlBase64ToUint8Array,
} from './mobileCrisisHelpers';
import { urlBase64ToUint8Array } from './mobileCrisisHelpers';

// ─── Active crisis summary ────────────────────────────────────────

export interface ActiveCrisisSummaryRow {
  id: number;
  code: string;
  titleEn: string;
  titleAr?: string;
  severity: 1 | 2 | 3 | 4 | 5;
  status: string;
  ownerUserId?: number | null;
  /** T1 (sev 4-5) · T2 (sev 3) · T3 (sev 1-2). */
  escalationTier: 'T1' | 'T2' | 'T3';
  openedAt?: string | null;
  regulatoryRegulator?: string | null;
  regulatoryDeadlineAt?: string | null;
  regulatoryMinutesRemaining?: number | null;
  personalDataAffected: boolean;
  cyberRelated: boolean;
  supplierInvolved: boolean;
  /** Pre-formatted Arabic next-action label from the backend. */
  nextActionLabel: string;
}

export async function fetchActiveCrisisSummary(): Promise<ActiveCrisisSummaryRow[]> {
  return api.get<ActiveCrisisSummaryRow[]>('/api/v1/bcm/crisis/active-summary');
}

// ─── Push subscription ───────────────────────────────────────────

export interface PushSubscriptionResponse {
  id: number;
  externalId: string;
  userId: number;
  deviceLabel?: string;
  enabled: boolean;
  lastUsedAt?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface PushSubscriptionRegisterRequest {
  endpoint: string;
  p256dh: string;
  auth: string;
  deviceLabel?: string;
}

export async function registerPushSubscription(body: PushSubscriptionRegisterRequest):
    Promise<PushSubscriptionResponse> {
  return api.post<PushSubscriptionResponse>('/api/v1/platform/push/subscriptions', body);
}

export async function listPushSubscriptions(): Promise<PushSubscriptionResponse[]> {
  return api.get<PushSubscriptionResponse[]>('/api/v1/platform/push/subscriptions');
}

export async function unregisterPushSubscription(id: number): Promise<void> {
  await api.delete<unknown>(`/api/v1/platform/push/subscriptions/${id}`);
}

// ─── Service worker + push subscription helpers ────────────────────
//
// Pure helpers (toEasternArabicDigits / formatCountdown / formatRelativeAgo
// / urlBase64ToUint8Array) live in ./mobileCrisisHelpers — re-exported at
// the top of this file so callers can keep importing from './mobileCrisis'.

/**
 * Result of {@link ensurePushSubscription}: either {@code 'denied'},
 * {@code 'dismissed'}, {@code 'unsupported'}, or {@code 'ok'} carrying
 * the registered subscription.
 */
export type EnsurePushResult =
  | { kind: 'unsupported' }
  | { kind: 'denied' }
  | { kind: 'dismissed' }
  | { kind: 'ok'; subscription: PushSubscriptionResponse };

/**
 * Full opt-in dance: register the SW, request Notification permission,
 * subscribe via PushManager, and POST the envelope to the backend.
 *
 * <p>Catches every kind of "this browser/device just can't" so the caller
 * can quietly hide the opt-in banner without throwing in console.
 */
export async function ensurePushSubscription(vapidPublicKey?: string): Promise<EnsurePushResult> {
  if (typeof window === 'undefined') return { kind: 'unsupported' };
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
    return { kind: 'unsupported' };
  }
  if (Notification.permission === 'denied') return { kind: 'denied' };

  // Ask the user.
  if (Notification.permission === 'default') {
    const granted = await Notification.requestPermission();
    if (granted === 'denied') return { kind: 'denied' };
    if (granted !== 'granted') return { kind: 'dismissed' };
  }

  try {
    const reg = await navigator.serviceWorker.register('/sw.js');
    await navigator.serviceWorker.ready;

    // Re-use any existing subscription on the SW first — saves round-trips.
    let sub = await reg.pushManager.getSubscription();
    if (!sub) {
      const options: PushSubscriptionOptionsInit = { userVisibleOnly: true };
      if (vapidPublicKey) {
        // `applicationServerKey` is typed as BufferSource in modern lib.dom.
        // Pass a fresh ArrayBuffer (not the Uint8Array directly) to avoid
        // the SharedArrayBuffer-vs-ArrayBuffer variance error in newer
        // TypeScript lib.dom defs.
        const keyArr = urlBase64ToUint8Array(vapidPublicKey);
        const buf = new ArrayBuffer(keyArr.byteLength);
        new Uint8Array(buf).set(keyArr);
        options.applicationServerKey = buf;
      }
      sub = await reg.pushManager.subscribe(options);
    }

    const json = sub.toJSON();
    const endpoint = json.endpoint ?? sub.endpoint;
    const p256dh   = json.keys?.p256dh;
    const auth     = json.keys?.auth;
    if (!endpoint || !p256dh || !auth) {
      return { kind: 'unsupported' };
    }
    const deviceLabel = navigator.userAgent.length > 240
      ? navigator.userAgent.slice(0, 240)
      : navigator.userAgent;
    const saved = await registerPushSubscription({ endpoint, p256dh, auth, deviceLabel });
    return { kind: 'ok', subscription: saved };
  } catch (err) {
    // Don't let an SW registration failure crash the mobile page.
    // eslint-disable-next-line no-console
    console.warn('[mobileCrisis] push subscription failed', err);
    return { kind: 'unsupported' };
  }
}
