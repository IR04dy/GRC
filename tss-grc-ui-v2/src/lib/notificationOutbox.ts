// Notification outbox — Phase E of the infrastructure round.
//
// Replaces fire-and-forget toasts with a real notification queue + dispatcher
// modeled after AWS SES + Twilio + Slack/Teams Workflows. Every workflow
// event that should notify someone goes through this outbox.
//
// In production this is backed by a real send queue (SES/SNS/Unifonic
// SMS/WhatsApp Business/Slack Webhook). In demo it's persisted to
// localStorage and shown on the /notifications/outbox page so the user
// can see what would have been sent.

import { getUser, type User } from './userDirectory';

export type NotifChannel = 'IN_APP' | 'EMAIL' | 'SMS' | 'WHATSAPP' | 'SLACK' | 'TEAMS' | 'NAFATH' | 'TADAWUL_PORTAL';
export type NotifStatus = 'QUEUED' | 'SENDING' | 'SENT' | 'FAILED' | 'CANCELLED';
export type NotifPriority = 'P1' | 'P2' | 'P3' | 'P4';

export interface NotificationItem {
  id: string;
  /** Recipient — user id from directory. */
  recipientId: string;
  /** Channel routing (multiple). */
  channels: NotifChannel[];
  priority: NotifPriority;
  /** Bilingual subject. */
  subjectEn: string;
  subjectAr: string;
  /** Bilingual body. */
  bodyEn: string;
  bodyAr: string;
  /** Optional deep-link path. */
  link?: string;
  /** Source — what raised this. */
  source: string;
  /** Reference id (e.g. the entity that triggered). */
  refId?: string;
  status: NotifStatus;
  createdAt: string;
  sentAt?: string;
  /** Whether read in-app. */
  readAt?: string;
  /** Failure reason. */
  failureReason?: string;
}

const KEY = 'grc.notif.outbox.v1';
const EVENT = 'grc.notif.outbox.changed';

export function loadNotifs(): NotificationItem[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(KEY);
    return raw ? JSON.parse(raw) as NotificationItem[] : [];
  } catch { return []; }
}

export function enqueueNotification(input: Omit<NotificationItem, 'id' | 'status' | 'createdAt'>): NotificationItem {
  const item: NotificationItem = {
    ...input,
    id: `NOX-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 5)}`,
    status: 'QUEUED',
    createdAt: new Date().toISOString(),
  };
  const next = [...loadNotifs(), item];
  // Cap to last 500 to avoid runaway storage.
  const trimmed = next.slice(-500);
  try { window.localStorage.setItem(KEY, JSON.stringify(trimmed)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
  // Auto-send P1 immediately (demo simulation), others queued.
  if (item.priority === 'P1') setTimeout(() => markSent(item.id), 200);
  return item;
}

export function markSent(id: string): void {
  const all = loadNotifs();
  const next = all.map(n => n.id === id ? { ...n, status: 'SENT' as NotifStatus, sentAt: new Date().toISOString() } : n);
  try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function markFailed(id: string, reason: string): void {
  const all = loadNotifs();
  const next = all.map(n => n.id === id ? { ...n, status: 'FAILED' as NotifStatus, failureReason: reason } : n);
  try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function cancelNotification(id: string): void {
  const all = loadNotifs();
  const next = all.map(n => n.id === id ? { ...n, status: 'CANCELLED' as NotifStatus } : n);
  try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function markRead(id: string): void {
  const all = loadNotifs();
  const next = all.map(n => n.id === id ? { ...n, readAt: new Date().toISOString() } : n);
  try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function unreadInApp(): NotificationItem[] {
  return loadNotifs().filter(n => n.channels.includes('IN_APP') && !n.readAt && n.status !== 'CANCELLED');
}

export function subscribeNotifs(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  return () => window.removeEventListener(EVENT, handler);
}

/** Channel-routing engine — map (priority, recipient role) → channel set. */
export function routeChannels(priority: NotifPriority, recipient: User): NotifChannel[] {
  // P1 = critical: in-app + email + SMS + WhatsApp (everything that reaches them fast).
  if (priority === 'P1') return ['IN_APP', 'EMAIL', 'SMS', 'WHATSAPP'];
  // P2 = high: in-app + email + SMS.
  if (priority === 'P2') return ['IN_APP', 'EMAIL', 'SMS'];
  // P3 = standard: in-app + email; Slack if available for engineering roles.
  if (priority === 'P3') {
    const eng = ['CIO', 'CTO', 'CYBER_DEFENSE_HEAD', 'SOC_ANALYST', 'CSIRT_LEAD', 'IT_OPS_LEAD'];
    return eng.includes(recipient.role) ? ['IN_APP', 'EMAIL', 'SLACK'] : ['IN_APP', 'EMAIL'];
  }
  // P4 = informational: in-app only.
  return ['IN_APP'];
}

/** Channel display label (bilingual). */
export const CHANNEL_LABEL: Record<NotifChannel, { en: string; ar: string; provider: string }> = {
  IN_APP:         { en: 'In-app',         ar: 'داخل التطبيق',     provider: 'TSS GRC' },
  EMAIL:          { en: 'Email',          ar: 'بريد إلكتروني',    provider: 'AWS SES' },
  SMS:            { en: 'SMS',            ar: 'رسائل قصيرة',       provider: 'Unifonic (CITC)' },
  WHATSAPP:       { en: 'WhatsApp',        ar: 'واتساب',           provider: 'WhatsApp Business' },
  SLACK:          { en: 'Slack',          ar: 'Slack',            provider: 'Slack Webhook' },
  TEAMS:          { en: 'Teams',          ar: 'Teams',            provider: 'Microsoft Graph' },
  NAFATH:         { en: 'Nafath',         ar: 'نفاذ',              provider: 'SDAIA Nafath' },
  TADAWUL_PORTAL: { en: 'Tadawul portal', ar: 'بوابة تداول',      provider: 'Tadawul' },
};

export const STATUS_LABEL: Record<NotifStatus, { en: string; ar: string }> = {
  QUEUED:    { en: 'Queued',     ar: 'مُجدوَل' },
  SENDING:   { en: 'Sending',     ar: 'جارِ الإرسال' },
  SENT:      { en: 'Sent',        ar: 'مُرسَل' },
  FAILED:    { en: 'Failed',      ar: 'فاشل' },
  CANCELLED: { en: 'Cancelled',   ar: 'مُلغى' },
};

export const STATUS_TONE: Record<NotifStatus, 'info' | 'warning' | 'success' | 'danger' | 'neutral'> = {
  QUEUED: 'info', SENDING: 'warning', SENT: 'success', FAILED: 'danger', CANCELLED: 'neutral',
};

/** Convenience: enqueue with auto-routing. */
export function notify(recipientId: string, priority: NotifPriority, subjectEn: string, subjectAr: string, bodyEn: string, bodyAr: string, source: string, opts?: { link?: string; refId?: string; channelOverride?: NotifChannel[] }): NotificationItem | null {
  const recipient = getUser(recipientId);
  if (!recipient) return null;
  return enqueueNotification({
    recipientId, priority,
    channels: opts?.channelOverride ?? routeChannels(priority, recipient),
    subjectEn, subjectAr, bodyEn, bodyAr,
    source,
    link: opts?.link, refId: opts?.refId,
  });
}

/** Aggregation. */
export interface NotifStats {
  total: number;
  pending: number;
  sentToday: number;
  failed: number;
  unreadInApp: number;
}

export function aggregateNotifs(): NotifStats {
  const all = loadNotifs();
  const todayStart = new Date(); todayStart.setHours(0, 0, 0, 0);
  return {
    total: all.length,
    pending: all.filter(n => n.status === 'QUEUED' || n.status === 'SENDING').length,
    sentToday: all.filter(n => n.status === 'SENT' && n.sentAt && new Date(n.sentAt).getTime() >= todayStart.getTime()).length,
    failed: all.filter(n => n.status === 'FAILED').length,
    unreadInApp: unreadInApp().length,
  };
}
