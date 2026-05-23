/* Slice 9-F — Service Worker for Mobile Crisis Room push notifications.
 *
 * Minimal Web Push handler. The server (Sprint 12) will POST a VAPID-
 * signed envelope to the browser push service; the service worker
 * receives it, parses the JSON body, and shows a notification.
 *
 * Payload shape (matches DispatchPayload in the backend):
 *   { code: "CRIS-2026-0007",
 *     titleAr: "أزمة سيبرانية",
 *     titleEn: "Cyber crisis",
 *     severity: "CRITICAL",
 *     deepLink: "/crisis/mobile" }
 *
 * Lang resolution: we don't have access to React's i18n here, but the
 * browser exposes navigator.language — pick AR title for ar-* locales,
 * EN otherwise. Reasonable default for a Saudi-Arabic-first deployment.
 */

self.addEventListener('install', (event) => {
  // skipWaiting so the SW takes control immediately on first install.
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('push', (event) => {
  // Defensive: a push without a body is a "wake-up ping" — refresh the
  // mobile page in any open client tabs so they re-fetch the active list.
  if (!event.data) {
    event.waitUntil((async () => {
      const all = await self.clients.matchAll({ type: 'window' });
      for (const c of all) {
        try { c.postMessage({ kind: 'crisis-push', payload: null }); } catch (e) { /* swallow */ }
      }
    })());
    return;
  }

  let payload = {};
  try { payload = event.data.json(); } catch (e) {
    payload = { titleEn: 'Crisis update', titleAr: 'تحديث أزمة' };
  }

  const lang = (self.navigator.language || 'en').toLowerCase();
  const isAr = lang.startsWith('ar');
  const title = isAr
    ? (payload.titleAr || payload.titleEn || 'تحديث أزمة')
    : (payload.titleEn || payload.titleAr || 'Crisis update');
  const body = payload.code
    ? (isAr ? `${payload.code} — ${payload.severity || ''}` : `${payload.code} — ${payload.severity || ''}`)
    : (isAr ? 'حدثت أزمة جديدة' : 'A new crisis has been declared');

  const opts = {
    body: body,
    icon: '/icon-192.png',
    badge: '/icon-72.png',
    tag: payload.code || 'crisis',
    renotify: true,
    requireInteraction: (payload.severity || '').toUpperCase() === 'CRITICAL',
    data: { deepLink: payload.deepLink || '/crisis/mobile' },
  };

  event.waitUntil(self.registration.showNotification(title, opts));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const target = (event.notification.data && event.notification.data.deepLink) || '/crisis/mobile';
  event.waitUntil((async () => {
    const all = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    // If a tab is already on the mobile page, focus it.
    for (const c of all) {
      if (c.url.endsWith(target) && 'focus' in c) {
        return c.focus();
      }
    }
    // Otherwise open a new tab.
    if (self.clients.openWindow) {
      return self.clients.openWindow(target);
    }
    return null;
  })());
});
