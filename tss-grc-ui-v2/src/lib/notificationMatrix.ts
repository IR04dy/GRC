// Per-event notification matrix — "who must be told, on what channel, by
// when, with what regulatory citation."
//
// ServiceNow IRM equivalent: "Notification Engine" + "Regulatory Reporting
// Calendar". Indexed by event-kind so the comms tab on a detail page can
// answer "what notifications does this record trigger?" deterministically.
//
// Standards anchor:
//   NIST CSF RS.CO-2 / RS.CO-3 — incidents reported per criteria + info shared
//   NIST 800-61 §3.2.7 — information sharing
//   ISO 27001 A.5.5, A.5.24, A.6.6 — contact with authorities
//   ISO 22301 §7.4 — communication during disruption
//   COSO ERM Component 5 — information, communication and reporting

import type { CommChannel } from './stakeholders';

export type EventKind =
  // Cyber / privacy
  | 'CYBER_INCIDENT'
  | 'PRIVACY_BREACH'
  | 'CLOUD_OUTAGE'
  // Financial / AML
  | 'AML_SUSPICIOUS_TRANSACTION'
  | 'AML_SAR_FILING'
  | 'MATERIAL_EVENT_LISTED'
  // Operational / resilience
  | 'BCP_INVOCATION'
  | 'CRITICAL_TELECOM_OUTAGE'
  | 'CRITICAL_VENDOR_FAILURE'
  // Audit / compliance
  | 'AUDIT_CRITICAL_FINDING'
  | 'CONTROL_SIGNIFICANT_FAILURE'
  // Ethics / governance
  | 'WHISTLEBLOWER_DISCLOSURE'
  | 'BOARD_RISK_ESCALATION'
  // Periodic regulator filings
  | 'NRC_SEMI_ANNUAL_FILING'
  | 'SAMA_CSF_ANNUAL_ASSESSMENT'
  | 'PDPL_DSR_RESPONSE';

export type SeverityFilter = 'ANY' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | 'HIGH_OR_CRITICAL';

export interface NotificationSpec {
  /** Stable id for matrix lookup. */
  id: string;
  eventKind: EventKind;
  /** Severity threshold that triggers this notification (if any). */
  triggerSeverity: SeverityFilter;
  /** Receiving stakeholder — joined to STAKEHOLDERS.code. */
  toStakeholderCode: string;
  channel: CommChannel;
  /** Mandatory deadline in hours from event detection. 0 = immediate. */
  withinHours: number;
  /** Regulatory / standards citation that mandates this notification. */
  regulatoryRef: string;
  /** Short description shown in the comms tab. */
  descriptionEn: string;
  descriptionAr: string;
  /** Whether this notification is mandatory or recommended. */
  mandatory: boolean;
}

// ── Matrix ─────────────────────────────────────────────────────────────────
//
// Indexed by event kind. Each event kind can fan out to multiple
// notifications (regulator + board + customer + internal escalation).

export const NOTIFICATION_MATRIX: NotificationSpec[] = [
  // ── Cyber incident (NCA HEY 24h is the headline obligation) ──────────────
  { id: 'CYB-NCA-24H',
    eventKind: 'CYBER_INCIDENT', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'NCA',                channel: 'PORTAL',
    withinHours: 24, mandatory: true,
    regulatoryRef: 'NCA ECC 7.5.1 · HEY portal',
    descriptionEn: 'Submit incident notification to NCA HEY portal.',
    descriptionAr: 'تقديم إشعار الحادثة عبر بوابة "حصين" للهيئة الوطنية للأمن السيبراني.' },
  { id: 'CYB-SAMA-IR',
    eventKind: 'CYBER_INCIDENT', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'SAMA',               channel: 'SECURE_MSG',
    withinHours: 4, mandatory: true,
    regulatoryRef: 'SAMA CSF 4.1.4 (financial-sector)',
    descriptionEn: 'Notify SAMA Cyber Security Department (financial-sector entities).',
    descriptionAr: 'إخطار إدارة الأمن السيبراني في البنك المركزي السعودي.' },
  { id: 'CYB-EXCO',
    eventKind: 'CYBER_INCIDENT', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'EXCO',               channel: 'IN_PERSON',
    withinHours: 2, mandatory: true,
    regulatoryRef: 'NIST CSF RS.CO-1 + internal IR runbook',
    descriptionEn: 'Brief Executive Committee — situation room activation.',
    descriptionAr: 'إحاطة اللجنة التنفيذية — تفعيل غرفة الأزمات.' },
  { id: 'CYB-BOARD',
    eventKind: 'CYBER_INCIDENT', triggerSeverity: 'CRITICAL',
    toStakeholderCode: 'BOARD',              channel: 'IN_PERSON',
    withinHours: 24, mandatory: true,
    regulatoryRef: 'COSO ERM Component 5 + NCA ECC 1.1.2',
    descriptionEn: 'Inform Board of Directors of CRITICAL cyber incident.',
    descriptionAr: 'إبلاغ مجلس الإدارة بالحادثة السيبرانية الحرجة.' },
  { id: 'CYB-PRESS',
    eventKind: 'CYBER_INCIDENT', triggerSeverity: 'CRITICAL',
    toStakeholderCode: 'PUBLIC_PRESS',       channel: 'PUBLIC_DISCLOSURE',
    withinHours: 72, mandatory: false,
    regulatoryRef: 'CMA Listing Rules §53 (if listed) + best practice',
    descriptionEn: 'Coordinate public statement / press release.',
    descriptionAr: 'إعداد بيان عام بالتنسيق مع إدارة الاتصال.' },

  // ── Privacy breach (PDPL Article 20 — 72-hour clock is the headline) ─────
  { id: 'PDPL-SDAIA-72H',
    eventKind: 'PRIVACY_BREACH', triggerSeverity: 'ANY',
    toStakeholderCode: 'SDAIA',              channel: 'PORTAL',
    withinHours: 72, mandatory: true,
    regulatoryRef: 'PDPL Art.20 · Implementing Regulations §17',
    descriptionEn: 'Notify SDAIA of personal-data breach.',
    descriptionAr: 'إبلاغ هيئة البيانات السعودية باختراق البيانات الشخصية.' },
  { id: 'PDPL-SUBJECTS',
    eventKind: 'PRIVACY_BREACH', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'AFFECTED_DATA_SUBJECTS', channel: 'EMAIL',
    withinHours: 72, mandatory: true,
    regulatoryRef: 'PDPL Art.20 (high-risk threshold)',
    descriptionEn: 'Direct notification to affected data subjects.',
    descriptionAr: 'إشعار مباشر لأصحاب البيانات المتأثرين.' },
  { id: 'PDPL-DPO-ESC',
    eventKind: 'PRIVACY_BREACH', triggerSeverity: 'ANY',
    toStakeholderCode: 'DPO',                channel: 'EMAIL',
    withinHours: 1, mandatory: true,
    regulatoryRef: 'PDPL Art.30 (DPO accountability)',
    descriptionEn: 'Escalate to Data Protection Officer.',
    descriptionAr: 'تصعيد إلى مسؤول حماية البيانات.' },
  { id: 'PDPL-CEO',
    eventKind: 'PRIVACY_BREACH', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'CEO',                channel: 'EMAIL',
    withinHours: 4, mandatory: true,
    regulatoryRef: 'Internal escalation matrix',
    descriptionEn: 'Inform Chief Executive Officer.',
    descriptionAr: 'إبلاغ الرئيس التنفيذي.' },
  { id: 'PDPL-DSR-30D',
    eventKind: 'PDPL_DSR_RESPONSE', triggerSeverity: 'ANY',
    toStakeholderCode: 'AFFECTED_DATA_SUBJECTS', channel: 'EMAIL',
    withinHours: 30 * 24, mandatory: true,
    regulatoryRef: 'PDPL Art.12 + Implementing Regulations §6',
    descriptionEn: 'Respond to data-subject request within 30 days.',
    descriptionAr: 'الرد على طلب صاحب البيانات خلال 30 يوماً.' },

  // ── AML — Suspicious Activity Report ─────────────────────────────────────
  { id: 'AML-SAFIU',
    eventKind: 'AML_SAR_FILING', triggerSeverity: 'ANY',
    toStakeholderCode: 'SAMA',               channel: 'SECURE_MSG',
    withinHours: 0, mandatory: true,
    regulatoryRef: 'AML Law Art.15 · SAMA AML §6.4 · FATF Rec 20',
    descriptionEn: 'File Suspicious Activity Report with SAFIU (no tipping-off).',
    descriptionAr: 'إرسال بلاغ نشاط مشبوه إلى وحدة التحريات المالية (دون إخبار العميل).' },
  { id: 'AML-MLRO',
    eventKind: 'AML_SUSPICIOUS_TRANSACTION', triggerSeverity: 'ANY',
    toStakeholderCode: 'AML_OFFICER',        channel: 'EMAIL',
    withinHours: 0, mandatory: true,
    regulatoryRef: 'AML Law Art.15 · SAMA AML §6.2',
    descriptionEn: 'Escalate to MLRO for SAR/STR decision.',
    descriptionAr: 'تصعيد إلى مسؤول مكافحة غسل الأموال لاتخاذ قرار البلاغ.' },

  // ── Material event (CMA — listed entities) ───────────────────────────────
  { id: 'CMA-MATERIAL-4H',
    eventKind: 'MATERIAL_EVENT_LISTED', triggerSeverity: 'ANY',
    toStakeholderCode: 'CMA',                channel: 'PORTAL',
    withinHours: 4, mandatory: true,
    regulatoryRef: 'CMA Listing Rules §53 · Resolution 4-78-2018',
    descriptionEn: 'Disclose material event to Tadawul + CMA portal.',
    descriptionAr: 'الإفصاح عن الحدث الجوهري عبر منصة تداول وبوابة هيئة السوق المالية.' },

  // ── Business continuity invocation ───────────────────────────────────────
  { id: 'BCP-NCEMA',
    eventKind: 'BCP_INVOCATION', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'NCEMA',              channel: 'PORTAL',
    withinHours: 24, mandatory: true,
    regulatoryRef: 'NCEMA crisis-reporting framework',
    descriptionEn: 'Notify NCEMA of crisis-level disruption.',
    descriptionAr: 'إبلاغ المركز الوطني لإدارة الكوارث بالاضطراب الجسيم.' },
  { id: 'BCP-EXCO-ALL',
    eventKind: 'BCP_INVOCATION', triggerSeverity: 'ANY',
    toStakeholderCode: 'EXCO',               channel: 'IN_PERSON',
    withinHours: 1, mandatory: true,
    regulatoryRef: 'ISO 22301 §8.4.4 · Internal BCP runbook',
    descriptionEn: 'Convene Crisis Management Team — invoke BCP runbook.',
    descriptionAr: 'استدعاء فريق إدارة الأزمات — تفعيل خطة الاستمرارية.' },

  // ── Telecom / cloud outage ───────────────────────────────────────────────
  { id: 'TELECOM-CITC',
    eventKind: 'CRITICAL_TELECOM_OUTAGE', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'CITC',               channel: 'PORTAL',
    withinHours: 24, mandatory: true,
    regulatoryRef: 'CITC outage-reporting circular',
    descriptionEn: 'Report telecom outage to CITC.',
    descriptionAr: 'إبلاغ هيئة الاتصالات وتقنية المعلومات بانقطاع الخدمة.' },
  { id: 'CLOUD-CITC',
    eventKind: 'CLOUD_OUTAGE', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'CITC',               channel: 'PORTAL',
    withinHours: 24, mandatory: true,
    regulatoryRef: 'CITC Cloud Computing Regulatory Framework v3',
    descriptionEn: 'Report cloud-service outage affecting level-3/4 workloads.',
    descriptionAr: 'إبلاغ هيئة الاتصالات بانقطاع خدمة سحابية تؤثر على المستوى 3/4.' },

  // ── Critical vendor failure ──────────────────────────────────────────────
  { id: 'TPRM-EXCO',
    eventKind: 'CRITICAL_VENDOR_FAILURE', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'EXCO',               channel: 'IN_PERSON',
    withinHours: 4, mandatory: true,
    regulatoryRef: 'NIST 800-161 §3.3 · ISO 27036',
    descriptionEn: 'Brief ExCo on critical vendor failure + activate continuity plan.',
    descriptionAr: 'إحاطة اللجنة التنفيذية بإخفاق مورد حرج وتفعيل خطة الاستمرارية.' },

  // ── Critical audit finding ───────────────────────────────────────────────
  { id: 'AUDIT-CTE',
    eventKind: 'AUDIT_CRITICAL_FINDING', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'AUDIT_CTE',          channel: 'IN_PERSON',
    withinHours: 7 * 24, mandatory: true,
    regulatoryRef: 'IIA IPPF 2440 · COSO ERM Component 5',
    descriptionEn: 'Escalate critical finding to Audit Committee.',
    descriptionAr: 'تصعيد الملاحظة الحرجة إلى لجنة المراجعة.' },

  // ── Whistleblower ────────────────────────────────────────────────────────
  { id: 'WB-NAZAHA',
    eventKind: 'WHISTLEBLOWER_DISCLOSURE', triggerSeverity: 'ANY',
    toStakeholderCode: 'NAZAHA',             channel: 'PORTAL',
    withinHours: 7 * 24, mandatory: false,
    regulatoryRef: 'Nazaha protected-disclosure framework',
    descriptionEn: 'Coordinate referral to Nazaha if corruption is substantiated.',
    descriptionAr: 'إحالة الحالات المُثبتة إلى هيئة الرقابة ومكافحة الفساد.' },
  { id: 'WB-AUDIT-CTE',
    eventKind: 'WHISTLEBLOWER_DISCLOSURE', triggerSeverity: 'ANY',
    toStakeholderCode: 'AUDIT_CTE',          channel: 'SECURE_MSG',
    withinHours: 7 * 24, mandatory: true,
    regulatoryRef: 'ISO 37001 + Audit Committee charter',
    descriptionEn: 'Audit Committee briefing — protected disclosure.',
    descriptionAr: 'إحاطة لجنة المراجعة — بلاغ محمي.' },

  // ── Periodic regulator filings ───────────────────────────────────────────
  { id: 'NRC-SEMI',
    eventKind: 'NRC_SEMI_ANNUAL_FILING', triggerSeverity: 'ANY',
    toStakeholderCode: 'NRC',                channel: 'PORTAL',
    withinHours: 30 * 24, mandatory: true,
    regulatoryRef: 'NRC national risk register requirements',
    descriptionEn: 'Submit semi-annual national risk register filing.',
    descriptionAr: 'تقديم بيانات السجل الوطني للمخاطر نصف سنوياً.' },
  { id: 'SAMA-CSF-ANNUAL',
    eventKind: 'SAMA_CSF_ANNUAL_ASSESSMENT', triggerSeverity: 'ANY',
    toStakeholderCode: 'SAMA',               channel: 'SECURE_MSG',
    withinHours: 90 * 24, mandatory: true,
    regulatoryRef: 'SAMA CSF 1.1.1 · annual self-assessment',
    descriptionEn: 'Submit annual SAMA CSF self-assessment.',
    descriptionAr: 'تقديم التقييم الذاتي السنوي لإطار البنك المركزي.' },

  // ── Board-level risk escalation ──────────────────────────────────────────
  { id: 'BOARD-RISK',
    eventKind: 'BOARD_RISK_ESCALATION', triggerSeverity: 'CRITICAL',
    toStakeholderCode: 'BOARD',              channel: 'IN_PERSON',
    withinHours: 7 * 24, mandatory: true,
    regulatoryRef: 'COSO ERM Component 1 + ISO 31000 §5.4.4',
    descriptionEn: 'Tabling at Board — risk acceptance / treatment decision.',
    descriptionAr: 'عرض على المجلس — قرار قبول أو معالجة المخاطرة.' },
];

// ── Helpers ────────────────────────────────────────────────────────────────
export function notificationsForEvent(
  eventKind: EventKind,
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL',
): NotificationSpec[] {
  return NOTIFICATION_MATRIX.filter(spec => {
    if (spec.eventKind !== eventKind) return false;
    switch (spec.triggerSeverity) {
      case 'ANY':              return true;
      case 'HIGH_OR_CRITICAL': return severity === 'HIGH' || severity === 'CRITICAL';
      default:                 return spec.triggerSeverity === severity;
    }
  });
}

/** Format a deadline for display: 4h / 24h / 3d / 30d. */
export function formatDeadline(hours: number, lang: 'en' | 'ar' = 'en'): string {
  if (hours <= 0)   return lang === 'ar' ? 'فوري' : 'Immediate';
  if (hours < 24)   return `${hours}${lang === 'ar' ? ' ساعة' : 'h'}`;
  const days = Math.round(hours / 24);
  return `${days}${lang === 'ar' ? ' يوم' : 'd'}`;
}

// ── Comms-log demo data ────────────────────────────────────────────────────
//
// Per-record audit trail of communications already sent. Keyed by the
// "kind:id" composite (e.g. CYBER_INCIDENT:501). The detail page's comms
// tab merges this with the live notification matrix so the auditor sees
// "what was sent, what's still pending."

export type CommState = 'PENDING' | 'IN_FLIGHT' | 'SENT' | 'ACKNOWLEDGED' | 'OVERDUE';

export interface CommLogEntry {
  id: string;
  /** Joins to NOTIFICATION_MATRIX.id. */
  specId: string;
  /** When the underlying event was detected (drives the SLA clock). */
  eventDetectedAt: string;
  /** Resolution state. */
  state: CommState;
  /** When the comm was sent (if SENT/ACKNOWLEDGED). */
  sentAt?: string;
  /** Reference number returned by the regulator portal / counterparty. */
  receiptRef?: string;
  /** Free-form note. */
  noteEn?: string;
}

export const COMMS_LOG_BY_RECORD: Record<string, CommLogEntry[]> = {
  // Cyber incidents
  'CYBER_INCIDENT:501': [
    { id: 'log-1', specId: 'CYB-NCA-24H',  eventDetectedAt: '2026-04-22T08:14:00',
      state: 'SENT',         sentAt: '2026-04-22T11:42:00', receiptRef: 'HEY-2026-04220011',
      noteEn: 'Submitted via NCA HEY portal — incident type PHISHING.' },
    { id: 'log-2', specId: 'CYB-EXCO',     eventDetectedAt: '2026-04-22T08:14:00',
      state: 'SENT',         sentAt: '2026-04-22T08:45:00',
      noteEn: 'Situation room activated within 31 minutes of detection.' },
  ],
  'CYBER_INCIDENT:502': [
    { id: 'log-3', specId: 'CYB-NCA-24H',  eventDetectedAt: '2026-04-19T03:42:00',
      state: 'SENT',         sentAt: '2026-04-19T05:55:00', receiptRef: 'HEY-2026-04190007',
      noteEn: 'Ransomware attempt — submitted within 2h 13m of detection.' },
    { id: 'log-4', specId: 'CYB-SAMA-IR',  eventDetectedAt: '2026-04-19T03:42:00',
      state: 'SENT',         sentAt: '2026-04-19T04:02:00', receiptRef: 'SAMA-CSD-2026-118',
      noteEn: 'Notified SAMA CSD — financial-systems exposure.' },
    { id: 'log-5', specId: 'CYB-BOARD',    eventDetectedAt: '2026-04-19T03:42:00',
      state: 'SENT',         sentAt: '2026-04-19T18:00:00',
      noteEn: 'Board emergency session — chairman briefed live.' },
    { id: 'log-6', specId: 'CYB-EXCO',     eventDetectedAt: '2026-04-19T03:42:00',
      state: 'SENT',         sentAt: '2026-04-19T04:15:00',
      noteEn: 'CMT activated; CISO leading.' },
  ],
  // Privacy breaches
  'PRIVACY_BREACH:13': [
    { id: 'log-7', specId: 'PDPL-DPO-ESC', eventDetectedAt: '2026-04-02T22:00:00',
      state: 'SENT',         sentAt: '2026-04-02T22:18:00',
      noteEn: 'DPO paged on rotation — investigation kicked off.' },
    { id: 'log-8', specId: 'PDPL-SDAIA-72H', eventDetectedAt: '2026-04-02T22:00:00',
      state: 'SENT',         sentAt: '2026-04-04T14:30:00', receiptRef: 'SDAIA-BR-2026-0084',
      noteEn: 'Submitted at 40h 30m post-detection — within the 72-hour window.' },
    { id: 'log-9', specId: 'PDPL-SUBJECTS', eventDetectedAt: '2026-04-02T22:00:00',
      state: 'IN_FLIGHT',    noteEn: '18,440 affected subjects — staged email campaign in progress.' },
    { id: 'log-10', specId: 'PDPL-CEO',    eventDetectedAt: '2026-04-02T22:00:00',
      state: 'SENT',         sentAt: '2026-04-03T01:00:00' },
  ],
  'PRIVACY_BREACH:12': [
    { id: 'log-11', specId: 'PDPL-DPO-ESC', eventDetectedAt: '2026-04-19T09:42:00',
      state: 'SENT',         sentAt: '2026-04-19T09:55:00' },
    { id: 'log-12', specId: 'PDPL-SDAIA-72H', eventDetectedAt: '2026-04-19T09:42:00',
      state: 'PENDING',      noteEn: 'Awaiting impact assessment — internal scope only, may not reach reporting threshold.' },
  ],
  // Audit findings (CRITICAL ones)
  'AUDIT_FINDING:31': [
    { id: 'log-13', specId: 'AUDIT-CTE',   eventDetectedAt: '2026-04-15T00:00:00',
      state: 'SENT',         sentAt: '2026-04-22T15:00:00',
      noteEn: 'Tabled at Audit Committee — cross-border transfer mechanism.' },
  ],
  'AUDIT_FINDING:36': [
    { id: 'log-14', specId: 'AUDIT-CTE',   eventDetectedAt: '2026-04-08T00:00:00',
      state: 'OVERDUE',      noteEn: 'Vendor SOC 2 attestation overdue — escalation due to Audit Committee.' },
  ],
};
