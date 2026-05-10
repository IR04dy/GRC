// Notification channel configuration — Sprint 6 production hardening.
//
// Each channel adapter (SMTP, WhatsApp Business, Twilio SMS, in-app push,
// Tadawul announcement) needs three things to go live:
//   1. Provider credentials (API keys / SMTP host:port:user:pass)
//   2. Sender identity (verified email domain, WhatsApp display name)
//   3. Health check that confirms the provider responds
//
// The frontend stores the config (sans secrets — the secrets live in
// AWS Secrets Manager / Spring config server) and reflects health.

import type { CommChannel } from './stakeholders';

export type ChannelHealth = 'CONFIGURED' | 'NOT_CONFIGURED' | 'ERROR' | 'DEGRADED';

export interface ChannelProviderConfig {
  channel: CommChannel;
  /** Whether the channel is wired to a real provider (vs the mock). */
  configured: boolean;
  /** Provider name for display (e.g. "AWS SES (eu-central-1)"). */
  providerEn: string;
  providerAr: string;
  /** Sender identity — domain for email, display name for WhatsApp, etc. */
  senderIdentity?: string;
  /** Region / data residency. */
  region?: string;
  /** Last successful provider health check. */
  lastHealthyAt?: string;
  /** Health check status. */
  health: ChannelHealth;
  /** Daily send quota set on the provider. */
  dailyQuota?: number;
  /** Saudi-relevance — whether this is a regulator-mandated channel. */
  saudiMandatory?: boolean;
  /** Required for: which use cases? */
  requiredFor?: string[];
}

const KEY = 'grc.notifications.channels.v1';
const EVENT = 'grc.notifications.channels.changed';

// ─── Default config (demo) ─────────────────────────────────────────────────

const _now = Date.now();
const _hoursAgo = (h: number) => new Date(_now - h * 3_600_000).toISOString();

export const DEFAULT_CHANNEL_CONFIG: ChannelProviderConfig[] = [
  {
    channel: 'EMAIL',
    configured: false,
    providerEn: 'AWS SES (eu-central-1)',
    providerAr: 'AWS SES (فرانكفورت)',
    senderIdentity: 'noreply@tss.sa',
    region: 'eu-central-1 / Frankfurt',
    health: 'NOT_CONFIGURED',
    dailyQuota: 50_000,
    saudiMandatory: true,
    requiredFor: ['SDAIA breach notifications', 'Board-level approvals', 'Audit committee comms'],
  },
  {
    channel: 'WHATSAPP',
    configured: false,
    providerEn: 'WhatsApp Business Cloud API',
    providerAr: 'واتساب للأعمال (السحابي)',
    senderIdentity: '+966 11 200 0000',
    region: 'Global (Meta)',
    health: 'NOT_CONFIGURED',
    dailyQuota: 10_000,
    saudiMandatory: true,
    requiredFor: ['CFO mobile approvals', 'CISO incident pages', 'Customer alerts'],
  },
  {
    channel: 'SMS',
    configured: false,
    providerEn: 'CITC-licensed Saudi SMS gateway (Unifonic)',
    providerAr: 'بوابة رسائل سعودية مرخَّصة (Unifonic)',
    senderIdentity: 'TSS-GRC',
    region: 'Riyadh',
    health: 'NOT_CONFIGURED',
    dailyQuota: 20_000,
    saudiMandatory: true,
    requiredFor: ['Customer change pre-advisories', 'OTP', 'High-severity outage alerts'],
  },
  {
    channel: 'IN_APP',
    configured: true,
    providerEn: 'In-app push (web + mobile)',
    providerAr: 'إشعار داخل التطبيق (ويب + جوّال)',
    senderIdentity: 'TSS GRC Platform',
    region: 'Local',
    health: 'CONFIGURED',
    lastHealthyAt: _hoursAgo(0.2),
  },
  {
    channel: 'PORTAL',
    configured: true,
    providerEn: 'Vendor / DSR / SpeakUp portals',
    providerAr: 'بوابات الموردين / طلبات الخصوصية / الإبلاغ',
    senderIdentity: 'portal.tss.sa',
    region: 'KSA (AWS Riyadh)',
    health: 'CONFIGURED',
    lastHealthyAt: _hoursAgo(0.5),
    saudiMandatory: true,
    requiredFor: ['SDAIA breach packet submission', 'NCA HEY portal'],
  },
  {
    channel: 'SECURE_MSG',
    configured: false,
    providerEn: 'Encrypted secure-channel (TLS 1.3 + S/MIME)',
    providerAr: 'قناة آمنة مشفّرة',
    health: 'NOT_CONFIGURED',
  },
  {
    channel: 'PUBLIC_DISCLOSURE',
    configured: false,
    providerEn: 'Tadawul announcement portal',
    providerAr: 'بوابة إعلانات تداول',
    senderIdentity: 'TSS Saudi Holding (4180)',
    region: 'KSA',
    health: 'NOT_CONFIGURED',
    saudiMandatory: true,
    requiredFor: ['CMA listed-entity material-event disclosure'],
  },
  {
    channel: 'TEAMS_SLACK',
    configured: true,
    providerEn: 'Microsoft Teams (incoming webhook)',
    providerAr: 'تيمز (Webhook)',
    region: 'Global',
    health: 'CONFIGURED',
    lastHealthyAt: _hoursAgo(1),
  },
  // Marker entries for the rest of the channels.
  {
    channel: 'PHONE',
    configured: false,
    providerEn: 'Recorded voice call (Twilio Voice)',
    providerAr: 'مكالمة هاتفية مُسجَّلة',
    health: 'NOT_CONFIGURED',
  },
  {
    channel: 'IN_PERSON',
    configured: true,
    providerEn: 'Logged in-person briefing',
    providerAr: 'إحاطة شخصية مُسجَّلة',
    health: 'CONFIGURED',
  },
  {
    channel: 'FORMAL_LETTER',
    configured: true,
    providerEn: 'Aramex priority delivery',
    providerAr: 'أرامكس — تسليم سريع',
    health: 'CONFIGURED',
    region: 'KSA',
  },
];

// ─── Persistence ───────────────────────────────────────────────────────────

export function loadChannels(): ChannelProviderConfig[] {
  if (typeof window === 'undefined') return DEFAULT_CHANNEL_CONFIG;
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return DEFAULT_CHANNEL_CONFIG;
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) && parsed.length > 0 ? parsed : DEFAULT_CHANNEL_CONFIG;
  } catch { return DEFAULT_CHANNEL_CONFIG; }
}

export function saveChannels(channels: ChannelProviderConfig[]): void {
  try { window.localStorage.setItem(KEY, JSON.stringify(channels)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function configureChannel(channel: CommChannel): void {
  const all = loadChannels();
  const next = all.map(c => c.channel === channel ? {
    ...c,
    configured: true,
    health: 'CONFIGURED' as ChannelHealth,
    lastHealthyAt: new Date().toISOString(),
  } : c);
  saveChannels(next);
}

export function disableChannel(channel: CommChannel): void {
  const all = loadChannels();
  const next = all.map(c => c.channel === channel ? {
    ...c,
    configured: false,
    health: 'NOT_CONFIGURED' as ChannelHealth,
  } : c);
  saveChannels(next);
}

export function subscribe(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  const storageHandler = (e: StorageEvent) => { if (e.key === KEY) cb(); };
  window.addEventListener('storage', storageHandler);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storageHandler);
  };
}

// ─── Aggregation ───────────────────────────────────────────────────────────

export interface ChannelStats {
  total: number;
  configured: number;
  saudiMandatoryConfigured: number;
  saudiMandatoryTotal: number;
  /** Whether all Saudi-mandatory channels are wired — drives prod-readiness. */
  saudiReady: boolean;
}

export function aggregateChannels(channels: ChannelProviderConfig[] = loadChannels()): ChannelStats {
  const total = channels.length;
  const configured = channels.filter(c => c.configured).length;
  const saudiMandatory = channels.filter(c => c.saudiMandatory);
  const saudiMandatoryConfigured = saudiMandatory.filter(c => c.configured).length;
  return {
    total, configured,
    saudiMandatoryConfigured,
    saudiMandatoryTotal: saudiMandatory.length,
    saudiReady: saudiMandatoryConfigured === saudiMandatory.length,
  };
}

export const HEALTH_TONE: Record<ChannelHealth, 'success' | 'warning' | 'danger' | 'neutral'> = {
  CONFIGURED:     'success',
  NOT_CONFIGURED: 'neutral',
  DEGRADED:       'warning',
  ERROR:          'danger',
};

export const HEALTH_LABEL: Record<ChannelHealth, { en: string; ar: string }> = {
  CONFIGURED:     { en: 'Healthy',         ar: 'سليم' },
  NOT_CONFIGURED: { en: 'Not configured',  ar: 'غير مُهيَّأ' },
  DEGRADED:       { en: 'Degraded',        ar: 'متدهور' },
  ERROR:          { en: 'Error',           ar: 'خطأ' },
};
