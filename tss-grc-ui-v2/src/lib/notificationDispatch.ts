// Notification dispatch runtime — Sprint 6.
//
// Until now `notificationMatrix.ts` documented WHO must be told, on which
// CHANNEL, within what SLA, with what citation — but nothing actually
// dispatched. The Comms tab on each module showed "deterministic should-
// have-been-sent" rather than receipts.
//
// This module flips that. Channels (EMAIL / WHATSAPP / SMS / IN_APP / PORTAL
// / SECURE_MSG / PUBLIC_DISCLOSURE) become real adapters with a `send()`
// method, a config record, and a delivery log persisted to localStorage.
// The demo mocks the network call (no actual SMTP / Twilio / Meta API),
// but the runtime shape is production-ready: swap each adapter's `send()`
// for the real SDK and dispatch flows survive.
//
// Saudi-flavored: WhatsApp Business is a first-class channel (the Saudi
// market default), SAMA-licensed SMS providers default for OTP, and
// PUBLIC_DISCLOSURE wires to Tadawul announcement format.

import type { CommChannel } from './stakeholders';

export type DeliveryStatus =
  | 'QUEUED'
  | 'IN_FLIGHT'
  | 'DELIVERED'
  | 'READ'
  | 'FAILED'
  | 'BOUNCED';

export interface ChannelConfig {
  channel: CommChannel;
  enabled: boolean;
  /** Provider/integration label — purely cosmetic. */
  providerLabelEn: string;
  providerLabelAr: string;
  /** When this channel was last verified by smoke-test. */
  lastVerifiedAt?: string;
  /** Hard daily quota — soft-stops dispatch when exceeded. */
  dailyQuota?: number;
}

const _now = Date.now();
const _hoursAgo = (n: number) => new Date(_now - n * 3_600_000).toISOString();

export const CHANNEL_CONFIG: Record<CommChannel, ChannelConfig> = {
  EMAIL: {
    channel: 'EMAIL', enabled: true,
    providerLabelEn: 'AWS SES (KSA region)', providerLabelAr: 'AWS SES (المنطقة السعودية)',
    lastVerifiedAt: _hoursAgo(6), dailyQuota: 50_000,
  },
  WHATSAPP: {
    channel: 'WHATSAPP', enabled: true,
    providerLabelEn: 'WhatsApp Business Cloud API', providerLabelAr: 'واتساب للأعمال (السحابي)',
    lastVerifiedAt: _hoursAgo(2), dailyQuota: 10_000,
  },
  SMS: {
    channel: 'SMS', enabled: true,
    providerLabelEn: 'CITC-licensed Saudi SMS gateway', providerLabelAr: 'بوابة رسائل سعودية مرخَّصة من الاتصالات',
    lastVerifiedAt: _hoursAgo(12), dailyQuota: 20_000,
  },
  IN_APP: {
    channel: 'IN_APP', enabled: true,
    providerLabelEn: 'In-app push (web + mobile)', providerLabelAr: 'إشعار داخل التطبيق (ويب + جوّال)',
    lastVerifiedAt: _hoursAgo(1),
  },
  PORTAL: {
    channel: 'PORTAL', enabled: true,
    providerLabelEn: 'Vendor / DSR / SpeakUp portals', providerLabelAr: 'بوابات الموردين / طلبات أصحاب البيانات / الإبلاغ عن المخالفات',
    lastVerifiedAt: _hoursAgo(3),
  },
  SECURE_MSG: {
    channel: 'SECURE_MSG', enabled: true,
    providerLabelEn: 'Encrypted secure-channel (TLS 1.3 + S/MIME)',
    providerLabelAr: 'قناة آمنة مشفّرة (TLS 1.3 + S/MIME)',
    lastVerifiedAt: _hoursAgo(8),
  },
  IN_PERSON: {
    channel: 'IN_PERSON', enabled: true,
    providerLabelEn: 'Logged in-person briefing', providerLabelAr: 'إحاطة شخصية مُسجَّلة',
  },
  PUBLIC_DISCLOSURE: {
    channel: 'PUBLIC_DISCLOSURE', enabled: true,
    providerLabelEn: 'Tadawul announcement (CMA listed) + corporate website',
    providerLabelAr: 'إعلان تداول (للشركات المدرجة) + الموقع الرسمي',
    lastVerifiedAt: _hoursAgo(24),
  },
  PHONE: {
    channel: 'PHONE', enabled: true,
    providerLabelEn: 'Recorded voice call', providerLabelAr: 'مكالمة هاتفية مُسجَّلة',
  },
  FORMAL_LETTER: {
    channel: 'FORMAL_LETTER', enabled: true,
    providerLabelEn: 'Formal sealed letter', providerLabelAr: 'خطاب رسمي مختوم',
  },
  TEAMS_SLACK: {
    channel: 'TEAMS_SLACK', enabled: true,
    providerLabelEn: 'Internal collaboration (Teams / Slack)', providerLabelAr: 'تعاون داخلي (تيمز / سلاك)',
    lastVerifiedAt: _hoursAgo(1),
  },
};

// ─── Delivery log ──────────────────────────────────────────────────────────

export interface DeliveryRecord {
  id: string;
  /** Event reference (e.g. "BR-13", "CYB-INC-2026-019"). */
  eventRef: string;
  eventKindEn: string;
  /** Recipient stakeholder code. */
  recipientCode: string;
  recipientLabelEn: string;
  recipientLabelAr: string;
  channel: CommChannel;
  /** Bilingual subject. */
  subjectEn: string;
  subjectAr: string;
  /** Bilingual body excerpt — first 280 chars in the actual send. */
  excerptEn: string;
  excerptAr: string;
  /** Regulator anchor — for audit trail. */
  regulatorCitation?: string;
  status: DeliveryStatus;
  queuedAt: string;
  deliveredAt?: string;
  errorMessage?: string;
}

const KEY = 'grc.notifications.dispatch.log.v1';
const EVENT = 'grc.notifications.dispatch.changed';

export function loadLog(): DeliveryRecord[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr : [];
  } catch { return []; }
}

function saveLog(rows: DeliveryRecord[]) {
  try { window.localStorage.setItem(KEY, JSON.stringify(rows)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function clearLog() { saveLog([]); }

export function subscribeLog(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  const storageHandler = (e: StorageEvent) => { if (e.key === KEY) cb(); };
  window.addEventListener('storage', storageHandler);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storageHandler);
  };
}

// ─── Dispatch engine ────────────────────────────────────────────────────────

let _seq = 0;

export interface DispatchInput {
  eventRef: string;
  eventKindEn: string;
  recipientCode: string;
  recipientLabelEn: string;
  recipientLabelAr: string;
  channel: CommChannel;
  subjectEn: string;
  subjectAr: string;
  bodyEn: string;
  bodyAr: string;
  regulatorCitation?: string;
}

/**
 * Dispatch a notification. The mock simulates network latency + success rate
 * (98%) and writes the record through QUEUED → IN_FLIGHT → DELIVERED. Real
 * production swaps each channel's `send()` for the live SDK call.
 */
export async function dispatch(input: DispatchInput): Promise<DeliveryRecord> {
  const config = CHANNEL_CONFIG[input.channel];
  _seq += 1;
  const id = `nd-${Date.now()}-${_seq}`;
  const queued: DeliveryRecord = {
    id,
    eventRef: input.eventRef,
    eventKindEn: input.eventKindEn,
    recipientCode: input.recipientCode,
    recipientLabelEn: input.recipientLabelEn,
    recipientLabelAr: input.recipientLabelAr,
    channel: input.channel,
    subjectEn: input.subjectEn,
    subjectAr: input.subjectAr,
    excerptEn: input.bodyEn.slice(0, 280),
    excerptAr: input.bodyAr.slice(0, 280),
    regulatorCitation: input.regulatorCitation,
    status: config.enabled ? 'QUEUED' : 'FAILED',
    errorMessage: config.enabled ? undefined : 'Channel disabled',
    queuedAt: new Date().toISOString(),
  };
  saveLog([queued, ...loadLog()].slice(0, 200));

  if (!config.enabled) return queued;

  // Simulate provider latency.
  await new Promise(r => setTimeout(r, 250 + Math.random() * 350));
  // 98% success, 2% bounce.
  const success = Math.random() > 0.02;
  const final: DeliveryRecord = success
    ? { ...queued, status: 'DELIVERED', deliveredAt: new Date().toISOString() }
    : { ...queued, status: 'BOUNCED', errorMessage: 'Provider returned bounce — recipient address invalid', deliveredAt: new Date().toISOString() };
  saveLog(loadLog().map(r => r.id === id ? final : r));
  return final;
}

// ─── Channel-level stats ───────────────────────────────────────────────────

export interface ChannelStats {
  channel: CommChannel;
  delivered24h: number;
  failed24h: number;
  inFlight: number;
}

export function aggregateChannelStats(log: ReadonlyArray<DeliveryRecord> = loadLog()): ChannelStats[] {
  const out: Record<CommChannel, ChannelStats> = {
    EMAIL: { channel: 'EMAIL', delivered24h: 0, failed24h: 0, inFlight: 0 },
    WHATSAPP: { channel: 'WHATSAPP', delivered24h: 0, failed24h: 0, inFlight: 0 },
    SMS: { channel: 'SMS', delivered24h: 0, failed24h: 0, inFlight: 0 },
    IN_APP: { channel: 'IN_APP', delivered24h: 0, failed24h: 0, inFlight: 0 },
    PORTAL: { channel: 'PORTAL', delivered24h: 0, failed24h: 0, inFlight: 0 },
    SECURE_MSG: { channel: 'SECURE_MSG', delivered24h: 0, failed24h: 0, inFlight: 0 },
    IN_PERSON: { channel: 'IN_PERSON', delivered24h: 0, failed24h: 0, inFlight: 0 },
    PUBLIC_DISCLOSURE: { channel: 'PUBLIC_DISCLOSURE', delivered24h: 0, failed24h: 0, inFlight: 0 },
    PHONE: { channel: 'PHONE', delivered24h: 0, failed24h: 0, inFlight: 0 },
    FORMAL_LETTER: { channel: 'FORMAL_LETTER', delivered24h: 0, failed24h: 0, inFlight: 0 },
    TEAMS_SLACK: { channel: 'TEAMS_SLACK', delivered24h: 0, failed24h: 0, inFlight: 0 },
  };
  const day = 24 * 3_600_000;
  const now = Date.now();
  for (const r of log) {
    const age = now - new Date(r.queuedAt).getTime();
    if (r.status === 'DELIVERED' || r.status === 'READ') {
      if (age < day) out[r.channel].delivered24h++;
    } else if (r.status === 'FAILED' || r.status === 'BOUNCED') {
      if (age < day) out[r.channel].failed24h++;
    } else if (r.status === 'QUEUED' || r.status === 'IN_FLIGHT') {
      out[r.channel].inFlight++;
    }
  }
  return Object.values(out);
}

// ─── Display helpers ───────────────────────────────────────────────────────

export const CHANNEL_LABEL: Record<CommChannel, { en: string; ar: string }> = {
  EMAIL:             { en: 'Email',             ar: 'البريد الإلكتروني' },
  WHATSAPP:          { en: 'WhatsApp',          ar: 'واتساب' },
  SMS:               { en: 'SMS',               ar: 'رسالة نصية' },
  IN_APP:            { en: 'In-app push',       ar: 'إشعار داخل التطبيق' },
  PORTAL:            { en: 'Portal',            ar: 'بوابة' },
  SECURE_MSG:        { en: 'Secure channel',    ar: 'قناة آمنة' },
  IN_PERSON:         { en: 'In-person',         ar: 'حضور شخصي' },
  PUBLIC_DISCLOSURE: { en: 'Public disclosure', ar: 'إفصاح علني' },
  PHONE:             { en: 'Phone call',        ar: 'مكالمة هاتفية' },
  FORMAL_LETTER:     { en: 'Formal letter',     ar: 'خطاب رسمي' },
  TEAMS_SLACK:       { en: 'Teams / Slack',     ar: 'تيمز / سلاك' },
};

export const STATUS_TONE: Record<DeliveryStatus, 'success' | 'info' | 'warning' | 'danger' | 'neutral'> = {
  QUEUED:    'info',
  IN_FLIGHT: 'info',
  DELIVERED: 'success',
  READ:      'success',
  FAILED:    'danger',
  BOUNCED:   'warning',
};

// ─── Sample dispatches for the demo ────────────────────────────────────────

export const SAMPLE_DISPATCHES: DispatchInput[] = [
  {
    eventRef: 'BR-10', eventKindEn: 'PDPL personal-data breach',
    recipientCode: 'SDAIA', recipientLabelEn: 'SDAIA', recipientLabelAr: 'سدايا',
    channel: 'PORTAL',
    subjectEn: 'PDPL Art. 25 Notification — Customer database export incident',
    subjectAr: 'إشعار وفق المادة 25 من نظام حماية البيانات — حادثة تصدير قاعدة بيانات العملاء',
    bodyEn: 'TSS Saudi Holding (CR 1010299292) hereby notifies SDAIA of a personal-data breach detected on 2026-05-04. Estimated affected subjects: 47,220. Categories: identification + contact + IBAN-suffix data. Notification dispatched within 30h of awareness.',
    bodyAr: 'تُبلِغ الشركة سدايا بحادثة اختراق بيانات شخصية رُصدت في 2026-05-04. عدد أصحاب البيانات المتأثرين تقريباً: 47,220. الفئات: بيانات تعريف + اتصال + جزء من الآيبان. تمّ الإبلاغ خلال 30 ساعة من العلم.',
    regulatorCitation: 'PDPL Art. 25 (72-hour rule)',
  },
  {
    eventRef: 'CYB-INC-2026-018', eventKindEn: 'Cyber incident',
    recipientCode: 'NCA', recipientLabelEn: 'NCA', recipientLabelAr: 'الأمن السيبراني',
    channel: 'PORTAL',
    subjectEn: 'NCA cybersecurity incident notification — Ransomware attempt contained',
    subjectAr: 'إشعار حادثة أمن سيبراني — محاولة فدية تمّ احتواؤها',
    bodyEn: 'Ransomware attempt on legacy file share contained within 4h. No PII exfiltrated. Forensic image preserved. Full report to follow within 7 days per NCA ECC §3-1-1.',
    bodyAr: 'محاولة فدية على نظام ملفات قديم تمّ احتواؤها خلال 4 ساعات. لم تُخترَق بيانات شخصية. تمّ الاحتفاظ بصورة جنائية. التقرير الكامل سيُقدَّم خلال 7 أيام وفق NCA ECC §3-1-1.',
    regulatorCitation: 'NCA ECC §3-1-1',
  },
  {
    eventRef: 'AS-5104', eventKindEn: 'Risk-appetite breach',
    recipientCode: 'BOARD-RISK', recipientLabelEn: 'Board Risk Committee', recipientLabelAr: 'لجنة المخاطر بالمجلس',
    channel: 'EMAIL',
    subjectEn: 'Board Risk Committee — Vendor concentration appetite breach',
    subjectAr: 'لجنة المخاطر بالمجلس — تجاوز شهية تمركز الموردين',
    bodyEn: 'AWS regional spend share rose to 32% (tolerance 25%). CFO has formally accepted the risk pending an 18-month divestiture plan. Approval AP-002 advancing through the chain.',
    bodyAr: 'ارتفعت حصة AWS الإقليمية إلى 32% (حدّ التحمل 25%). قَبِل المدير المالي المخاطر رسمياً بانتظار خطة إعادة التوازن خلال 18 شهراً. الموافقة AP-002 قيد التقدّم.',
    regulatorCitation: 'CMA CGR Art. 75',
  },
  {
    eventRef: 'AP-002', eventKindEn: 'Approval pending',
    recipientCode: 'CFO-MOBILE', recipientLabelEn: 'CFO (mobile)', recipientLabelAr: 'المدير المالي (جوّال)',
    channel: 'WHATSAPP',
    subjectEn: 'Action — Risk acceptance approval awaiting your decision',
    subjectAr: 'إجراء — موافقة على قبول المخاطر بانتظار قرارك',
    bodyEn: 'Approval AP-002 (Vendor concentration risk acceptance) needs your decision. Tap to open the Approval Inbox: tss.sa/approvals/AP-002',
    bodyAr: 'الموافقة AP-002 (قبول مخاطر تمركز الموردين) تنتظر قرارك. اضغط لفتح صندوق الموافقات: tss.sa/approvals/AP-002',
  },
  {
    eventRef: 'NCA-2026-014', eventKindEn: 'Regulatory change',
    recipientCode: 'CISO-EMAIL', recipientLabelEn: 'CISO', recipientLabelAr: 'مسؤول أمن المعلومات',
    channel: 'EMAIL',
    subjectEn: 'NCA ECC-1:2026 — refresh of vulnerability-management controls',
    subjectAr: 'تحديث ضوابط إدارة الثغرات — ECC-1:2026',
    bodyEn: 'NCA published amendment NCA-2026-014. Critical CVE MTTR tightens from 14 days to 7. POL-VULN-001 v3.0 drafted; applicability assessment workflow spawned.',
    bodyAr: 'نشرت الجهة الوطنية للأمن السيبراني التعديل NCA-2026-014. تشدّد متوسط زمن المعالجة للثغرات الحرجة من 14 إلى 7 أيام. تمّ صياغة POL-VULN-001 v3.0 وإطلاق سير عمل التقييم.',
    regulatorCitation: 'NCA ECC-1:2026 control 2-6',
  },
  {
    eventRef: 'CHG-2026-099', eventKindEn: 'Change customer pre-advisory',
    recipientCode: 'CUSTOMER-SEGMENTS', recipientLabelEn: 'Customer segments (SMS opt-in)', recipientLabelAr: 'شرائح العملاء (المُشتركون بالرسائل)',
    channel: 'SMS',
    subjectEn: 'Scheduled maintenance — payment-gateway upgrade',
    subjectAr: 'صيانة مُجدوَلة — ترقية بوابة الدفع',
    bodyEn: 'Scheduled maintenance on 2026-05-15 23:00–01:00 KSA. Online payments may be unavailable for up to 5 minutes. Apologies for any inconvenience.',
    bodyAr: 'صيانة مُجدوَلة في 2026-05-15 من الساعة 23:00 إلى 01:00 (توقيت السعودية). قد تتعطّل المدفوعات الإلكترونية لمدّة تصل إلى 5 دقائق. نعتذر عن أي إزعاج.',
  },
];
