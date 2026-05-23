// Slice 9-F — Mobile Crisis Room pure helpers.
//
// Lifted out of {@code lib/mobileCrisis.ts} so the node:test suite at
// {@code src/__tests__/mobileCrisis.test.ts} can import them without
// dragging in {@code lib/api.ts}, which evaluates {@code import.meta.env}
// at load time and is therefore Vite-only.
//
// {@code lib/mobileCrisis.ts} re-exports these so application code can
// continue to import either path.

/**
 * Convert ASCII digits to Arabic-Indic (Eastern Arabic) digits so the
 * countdown chip reads naturally to a Saudi commander.
 */
export function toEasternArabicDigits(input: string | number): string {
  const map: Record<string, string> = {
    '0': '٠', '1': '١', '2': '٢', '3': '٣', '4': '٤',
    '5': '٥', '6': '٦', '7': '٧', '8': '٨', '9': '٩',
  };
  return String(input).replace(/[0-9]/g, d => map[d] ?? d);
}

/**
 * Saudi-native phrasing of "N hours / minutes remaining" (or breached).
 *
 *  > 0  → "٤٧ ساعة متبقية"  / "47 hours remaining"
 *  > 0 small → "١٢ دقيقة متبقية" / "12 minutes remaining"
 *  ≤ 0  → "تم تجاوز المهلة"  / "Deadline breached"
 */
export function formatCountdown(minutesRemaining: number | null | undefined,
                                lang: 'en' | 'ar' = 'ar'): string {
  if (minutesRemaining === null || minutesRemaining === undefined) {
    return '—';
  }
  if (minutesRemaining <= 0) {
    return lang === 'ar' ? 'تم تجاوز المهلة' : 'Deadline breached';
  }
  const hours = Math.floor(minutesRemaining / 60);
  if (hours >= 1) {
    return lang === 'ar'
      ? `${toEasternArabicDigits(hours)} ساعة متبقية`
      : `${hours} hours remaining`;
  }
  const m = Math.max(1, Math.floor(minutesRemaining));
  return lang === 'ar'
    ? `${toEasternArabicDigits(m)} دقيقة متبقية`
    : `${m} minutes remaining`;
}

/**
 * Relative time in Saudi-native Arabic — used by the bottom-sheet brief.
 *  ≥ 60 min → "منذ ٢ ساعة"
 *  ≥ 1 min  → "منذ ١٢ دقيقة"
 *  < 1 min  → "الآن"
 */
export function formatRelativeAgo(iso: string | null | undefined,
                                  lang: 'en' | 'ar' = 'ar'): string {
  if (!iso) return '—';
  const then = new Date(iso).getTime();
  if (!isFinite(then)) return '—';
  const minutes = Math.max(0, Math.floor((Date.now() - then) / 60000));
  if (minutes < 1) return lang === 'ar' ? 'الآن' : 'just now';
  if (minutes < 60) {
    return lang === 'ar'
      ? `منذ ${toEasternArabicDigits(minutes)} دقيقة`
      : `${minutes} min ago`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return lang === 'ar'
      ? `منذ ${toEasternArabicDigits(hours)} ساعة`
      : `${hours}h ago`;
  }
  const days = Math.floor(hours / 24);
  return lang === 'ar'
    ? `منذ ${toEasternArabicDigits(days)} يوم`
    : `${days}d ago`;
}

/**
 * Convert a base64-url VAPID public key string into the {@code Uint8Array}
 * the browser's {@code PushManager.subscribe()} demands. Pure helper —
 * exported for unit-testing the encoding logic separately from the network.
 */
export function urlBase64ToUint8Array(base64Url: string): Uint8Array {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4);
  const base64  = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  const arr = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) arr[i] = raw.charCodeAt(i);
  return arr;
}
