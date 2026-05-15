// Per-event notification matrix — "who must be told, on what channel, by
// when, with what regulatory citation."
//
// industry-standard GRC tooling equivalent: "Notification Engine" + "Regulatory Reporting
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
  | 'AML_SANCTIONS_MATCH'
  | 'MATERIAL_EVENT_LISTED'
  // Operational / resilience
  | 'BCP_INVOCATION'
  | 'CRITICAL_TELECOM_OUTAGE'
  | 'CRITICAL_VENDOR_FAILURE'
  | 'BCM_EXERCISE_OUTCOME'
  | 'RTO_RPO_BREACH'
  // Audit / compliance
  | 'AUDIT_CRITICAL_FINDING'
  | 'CONTROL_SIGNIFICANT_FAILURE'
  | 'REG_CHANGE_PUBLISHED'
  // Risk
  | 'KRI_RED_BREACH'
  | 'APPETITE_BREACH'
  // TPRM / SCM
  | 'CRITICAL_VENDOR_OFFBOARD'
  | 'SUB_PROCESSOR_CHANGE'
  | 'VENDOR_SOC2_EXPIRY'
  // Policy / DPIA
  | 'POLICY_REVIEW_DUE'
  | 'POLICY_EXCEPTION_APPROVED'
  | 'DPIA_PRIOR_CONSULTATION'
  // Change management
  | 'CHANGE_EMERGENCY_EXECUTED'
  | 'CHANGE_FAILED'
  | 'CAB_FORWARD_SCHEDULE'
  | 'CHANGE_CUSTOMER_PRE_ADVISORY'
  | 'CHANGE_CUSTOMER_DURING'
  | 'CHANGE_CUSTOMER_POST'
  | 'CHANGE_FREEZE_ANNOUNCE'
  | 'CHANGE_CAB_NOTICE'
  // Ethics / governance
  | 'WHISTLEBLOWER_DISCLOSURE'
  | 'BOARD_RISK_ESCALATION'
  // Evidence
  | 'EVIDENCE_EXPIRY'
  // Periodic regulator filings
  | 'NRC_SEMI_ANNUAL_FILING'
  | 'SAMA_CSF_ANNUAL_ASSESSMENT'
  | 'PDPL_ANNUAL_REPORT'
  | 'PDPL_DSR_RESPONSE'
  | 'AML_ANNUAL_REPORT'
  | 'NAZAHA_ANNUAL_INTEGRITY';

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

  // ── DD-27 expansion: closing the gap ────────────────────────────────────

  // KRI red-band breach
  { id: 'KRI-RED-RISK-CTE',
    eventKind: 'KRI_RED_BREACH', triggerSeverity: 'ANY',
    toStakeholderCode: 'RISK_CTE',           channel: 'IN_PERSON',
    withinHours: 7 * 24, mandatory: true,
    regulatoryRef: 'ISO 31000 §6.6 · COSO ERM Component 4',
    descriptionEn: 'Tabling KRI red-band breach at Risk Committee.',
    descriptionAr: 'عرض تجاوز مؤشر الخطر (نطاق أحمر) على لجنة المخاطر.' },
  { id: 'KRI-RED-CRO',
    eventKind: 'KRI_RED_BREACH', triggerSeverity: 'ANY',
    toStakeholderCode: 'CRO',                channel: 'EMAIL',
    withinHours: 1, mandatory: true,
    regulatoryRef: 'Internal escalation matrix',
    descriptionEn: 'Immediate KRI breach alert to CRO.',
    descriptionAr: 'تنبيه فوري لرئيس المخاطر بتجاوز مؤشر الخطر.' },

  // Appetite breach
  { id: 'APPETITE-BOARD',
    eventKind: 'APPETITE_BREACH', triggerSeverity: 'ANY',
    toStakeholderCode: 'BOARD',              channel: 'IN_PERSON',
    withinHours: 30 * 24, mandatory: true,
    regulatoryRef: 'COSO ERM Component 2 (appetite) · ISO 31000 §5.4.4',
    descriptionEn: 'Risk appetite breach — Board re-affirmation required.',
    descriptionAr: 'تجاوز مستوى تقبّل المخاطر — يستلزم اعتماد المجلس مجدداً.' },

  // Regulatory change ingestion
  { id: 'REGCHG-COMPLIANCE',
    eventKind: 'REG_CHANGE_PUBLISHED', triggerSeverity: 'ANY',
    toStakeholderCode: 'COMPLIANCE_LEAD',    channel: 'EMAIL',
    withinHours: 7 * 24, mandatory: true,
    regulatoryRef: 'COBIT MEA03 · Internal regulatory-watch',
    descriptionEn: 'New / updated regulator publication — open impact assessment.',
    descriptionAr: 'إصدار جديد من الجهة التنظيمية — فتح تقييم أثر.' },
  { id: 'REGCHG-EXCO',
    eventKind: 'REG_CHANGE_PUBLISHED', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'EXCO',               channel: 'IN_PERSON',
    withinHours: 14 * 24, mandatory: true,
    regulatoryRef: 'Internal escalation matrix',
    descriptionEn: 'Briefing ExCo on HIGH-impact regulator change.',
    descriptionAr: 'إحاطة اللجنة التنفيذية بتغيير تنظيمي عالي الأثر.' },

  // Control failure
  { id: 'CTRL-FAIL-RISKCTE',
    eventKind: 'CONTROL_SIGNIFICANT_FAILURE', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'RISK_CTE',           channel: 'IN_PERSON',
    withinHours: 14 * 24, mandatory: true,
    regulatoryRef: 'COSO ERM Component 4 · NCA ECC 1.5',
    descriptionEn: 'Significant control failure — Risk Committee escalation.',
    descriptionAr: 'إخفاق ضابط جوهري — تصعيد إلى لجنة المخاطر.' },
  { id: 'CTRL-FAIL-AUDITCTE',
    eventKind: 'CONTROL_SIGNIFICANT_FAILURE', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'AUDIT_CTE',          channel: 'IN_PERSON',
    withinHours: 14 * 24, mandatory: true,
    regulatoryRef: 'IIA IPPF 2440',
    descriptionEn: 'Significant control failure — Audit Committee briefing.',
    descriptionAr: 'إخفاق ضابط جوهري — إحاطة لجنة المراجعة.' },

  // BCM exercise outcome
  { id: 'BCM-EX-AUDITCTE',
    eventKind: 'BCM_EXERCISE_OUTCOME', triggerSeverity: 'ANY',
    toStakeholderCode: 'AUDIT_CTE',          channel: 'IN_PERSON',
    withinHours: 30 * 24, mandatory: true,
    regulatoryRef: 'ISO 22301 §8.5 + §9.3',
    descriptionEn: 'BCP exercise outcome reporting — Audit Committee.',
    descriptionAr: 'تقرير نتائج تمرين الاستمرارية — لجنة المراجعة.' },

  // RTO/RPO breach
  { id: 'RTO-EXCO',
    eventKind: 'RTO_RPO_BREACH', triggerSeverity: 'ANY',
    toStakeholderCode: 'EXCO',               channel: 'IN_PERSON',
    withinHours: 4, mandatory: true,
    regulatoryRef: 'ISO 22301 §8.4.4',
    descriptionEn: 'RTO / RPO breach during invocation — ExCo escalation.',
    descriptionAr: 'تجاوز مستهدفات الاستعادة أثناء التفعيل — تصعيد للجنة التنفيذية.' },

  // Critical vendor off-boarding
  { id: 'TPRM-OFFBOARD-EXCO',
    eventKind: 'CRITICAL_VENDOR_OFFBOARD', triggerSeverity: 'ANY',
    toStakeholderCode: 'EXCO',               channel: 'IN_PERSON',
    withinHours: 7 * 24, mandatory: true,
    regulatoryRef: 'ISO 27036 · NIST 800-161 §3.3.5',
    descriptionEn: 'Critical-vendor off-boarding decision tabled at ExCo.',
    descriptionAr: 'عرض قرار إنهاء التعاقد مع مورد حرج على اللجنة التنفيذية.' },

  // Sub-processor change
  { id: 'SUBPROC-DPO',
    eventKind: 'SUB_PROCESSOR_CHANGE', triggerSeverity: 'ANY',
    toStakeholderCode: 'DPO',                channel: 'EMAIL',
    withinHours: 30 * 24, mandatory: true,
    regulatoryRef: 'PDPL Art.20 + Art.27',
    descriptionEn: 'Notify DPO of sub-processor change for downstream review.',
    descriptionAr: 'إبلاغ مسؤول حماية البيانات بتغيير المعالج الفرعي.' },

  // Vendor SOC 2 expiry
  { id: 'SOC2-EXPIRY-COMPLIANCE',
    eventKind: 'VENDOR_SOC2_EXPIRY', triggerSeverity: 'ANY',
    toStakeholderCode: 'COMPLIANCE_LEAD',    channel: 'EMAIL',
    withinHours: 30 * 24, mandatory: true,
    regulatoryRef: 'AICPA SOC 2 attestation cycle',
    descriptionEn: 'Vendor SOC 2 attestation due within 30 days — chase fresh report.',
    descriptionAr: 'انتهاء شهادة SOC 2 للمورد خلال 30 يوماً — متابعة الحصول على شهادة جديدة.' },

  // Policy review-due
  { id: 'POLREVIEW-OWNER',
    eventKind: 'POLICY_REVIEW_DUE', triggerSeverity: 'ANY',
    toStakeholderCode: 'COMPLIANCE_LEAD',    channel: 'EMAIL',
    withinHours: 30 * 24, mandatory: true,
    regulatoryRef: 'ISO 27001 A.5.1.2',
    descriptionEn: 'Policy review-due — owner notification.',
    descriptionAr: 'مراجعة السياسة مستحقة — إشعار المالك.' },

  // Policy exception approved
  { id: 'POLEXCEPT-RISKCTE',
    eventKind: 'POLICY_EXCEPTION_APPROVED', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'RISK_CTE',           channel: 'IN_PERSON',
    withinHours: 30 * 24, mandatory: true,
    regulatoryRef: 'NCA ECC 1.5',
    descriptionEn: 'Policy exception (HIGH/CRITICAL impact) — Risk Committee briefing.',
    descriptionAr: 'استثناء سياسة (عالي/حرج) — إحاطة لجنة المخاطر.' },

  // DPIA prior consultation
  { id: 'DPIA-PRIOR-SDAIA',
    eventKind: 'DPIA_PRIOR_CONSULTATION', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'SDAIA',              channel: 'PORTAL',
    withinHours: 30 * 24, mandatory: true,
    regulatoryRef: 'PDPL Art.26 IR §22',
    descriptionEn: 'SDAIA prior consultation when residual HIGH risk in DPIA.',
    descriptionAr: 'استشارة مسبقة مع هيئة البيانات عند وجود مخاطر متبقية عالية.' },

  // AML sanctions match
  { id: 'AML-SANC-LEGAL',
    eventKind: 'AML_SANCTIONS_MATCH', triggerSeverity: 'ANY',
    toStakeholderCode: 'GENERAL_COUNSEL',    channel: 'EMAIL',
    withinHours: 4, mandatory: true,
    regulatoryRef: 'SAMA AML §5 · FATF Rec 6',
    descriptionEn: 'Sanctions match — immediate Legal review (no tipping-off).',
    descriptionAr: 'تطابق عقوبات — مراجعة قانونية فورية (دون إخبار العميل).' },
  { id: 'AML-SANC-SAMA',
    eventKind: 'AML_SANCTIONS_MATCH', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'SAMA',               channel: 'SECURE_MSG',
    withinHours: 24, mandatory: true,
    regulatoryRef: 'SAMA AML §5.4',
    descriptionEn: 'Confirmed sanctions match — SAMA notification.',
    descriptionAr: 'تطابق عقوبات مؤكد — إبلاغ البنك المركزي.' },

  // Change management
  { id: 'CHGEMERG-BOARD',
    eventKind: 'CHANGE_EMERGENCY_EXECUTED', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'BOARD',              channel: 'IN_PERSON',
    withinHours: 7 * 24, mandatory: false,
    regulatoryRef: 'ITIL 4 + Internal escalation',
    descriptionEn: 'Emergency change executed — Board notification (CRITICAL only).',
    descriptionAr: 'تنفيذ تغيير طارئ — إبلاغ المجلس (حرجة فقط).' },
  { id: 'CHGFAIL-EXCO',
    eventKind: 'CHANGE_FAILED', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'EXCO',               channel: 'IN_PERSON',
    withinHours: 24, mandatory: true,
    regulatoryRef: 'ITIL 4 · ISO 20000-1 §9.1.3',
    descriptionEn: 'Failed change — ExCo brief + post-mortem trigger.',
    descriptionAr: 'تغيير فاشل — إحاطة اللجنة التنفيذية وفتح تحليل سبب جذري.' },

  // Evidence expiry
  { id: 'EV-EXPIRY-OWNER',
    eventKind: 'EVIDENCE_EXPIRY', triggerSeverity: 'ANY',
    toStakeholderCode: 'COMPLIANCE_LEAD',    channel: 'EMAIL',
    withinHours: 30 * 24, mandatory: true,
    regulatoryRef: 'ISO 27001 A.7.5 · Audit-trail integrity',
    descriptionEn: 'Evidence expiring within 30 days — owner re-collection.',
    descriptionAr: 'انتهاء صلاحية الدليل خلال 30 يوماً — إعادة جمع من المالك.' },

  // Periodic — annual reports
  { id: 'PDPL-ANNUAL',
    eventKind: 'PDPL_ANNUAL_REPORT', triggerSeverity: 'ANY',
    toStakeholderCode: 'SDAIA',              channel: 'PORTAL',
    withinHours: 365 * 24, mandatory: true,
    regulatoryRef: 'PDPL IR §29',
    descriptionEn: 'Annual PDPL compliance report submission to SDAIA.',
    descriptionAr: 'تقديم التقرير السنوي للامتثال لنظام حماية البيانات.' },
  { id: 'AML-ANNUAL-BOARD',
    eventKind: 'AML_ANNUAL_REPORT', triggerSeverity: 'ANY',
    toStakeholderCode: 'BOARD',              channel: 'IN_PERSON',
    withinHours: 365 * 24, mandatory: true,
    regulatoryRef: 'SAMA AML §10 · FATF Rec 18',
    descriptionEn: 'Annual AML programme report to Board.',
    descriptionAr: 'التقرير السنوي لبرنامج مكافحة غسل الأموال — المجلس.' },
  { id: 'NAZAHA-INTEGRITY',
    eventKind: 'NAZAHA_ANNUAL_INTEGRITY', triggerSeverity: 'ANY',
    toStakeholderCode: 'NAZAHA',             channel: 'PORTAL',
    withinHours: 365 * 24, mandatory: false,
    regulatoryRef: 'Nazaha integrity framework',
    descriptionEn: 'Annual integrity report — Nazaha submission.',
    descriptionAr: 'التقرير السنوي للنزاهة — الإبلاغ لهيئة الرقابة.' },

  // ── DD-33 expansion: Change Management communication layer ──────────────
  // Forward Schedule of Change (FSC) — weekly distribution to stakeholder
  // groups so business + IT can plan around scheduled work.
  { id: 'CAB-FSC-WEEKLY',
    eventKind: 'CAB_FORWARD_SCHEDULE', triggerSeverity: 'ANY',
    toStakeholderCode: 'EXCO',               channel: 'EMAIL',
    withinHours: 7 * 24, mandatory: true,
    regulatoryRef: 'ITIL 4 Change Enablement · ISO 20000-1 §9.1.4',
    descriptionEn: 'Weekly Forward Schedule of Change — distribution to subscribers.',
    descriptionAr: 'الجدول الأسبوعي للتغييرات المستقبلية — التوزيع على المشتركين.' },

  // CAB notice — Normal change requires CAB before SCHEDULED status.
  { id: 'CHG-CAB-NOTICE',
    eventKind: 'CHANGE_CAB_NOTICE', triggerSeverity: 'ANY',
    toStakeholderCode: 'EXCO',               channel: 'EMAIL',
    withinHours: 48, mandatory: true,
    regulatoryRef: 'ITIL 4 · NCA ECC 4-3-2',
    descriptionEn: 'CAB approval notice — distribute decision to change-owner + sponsor.',
    descriptionAr: 'إشعار اعتماد CAB — توزيع القرار على مالك التغيير والراعي.' },

  // Customer-impact comms — three-stage cycle: pre / during / post.
  { id: 'CHG-CUST-PRE',
    eventKind: 'CHANGE_CUSTOMER_PRE_ADVISORY', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'AFFECTED_DATA_SUBJECTS', channel: 'EMAIL',
    withinHours: 7 * 24, mandatory: true,
    regulatoryRef: 'ITIL 4 (customer comms) · SAMA CSF 4.4.4',
    descriptionEn: 'Customer pre-change advisory — outage window + impact summary.',
    descriptionAr: 'إشعار العملاء قبل التغيير — نافذة الانقطاع وملخص الأثر.' },
  { id: 'CHG-CUST-DURING',
    eventKind: 'CHANGE_CUSTOMER_DURING', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'AFFECTED_DATA_SUBJECTS', channel: 'PUBLIC_DISCLOSURE',
    withinHours: 1, mandatory: true,
    regulatoryRef: 'ITIL 4 · Internal SLA',
    descriptionEn: 'Status-page update during change window — service health.',
    descriptionAr: 'تحديث صفحة الحالة أثناء نافذة التغيير — صحة الخدمة.' },
  { id: 'CHG-CUST-POST',
    eventKind: 'CHANGE_CUSTOMER_POST', triggerSeverity: 'HIGH_OR_CRITICAL',
    toStakeholderCode: 'AFFECTED_DATA_SUBJECTS', channel: 'EMAIL',
    withinHours: 4, mandatory: true,
    regulatoryRef: 'ITIL 4 · Internal SLA',
    descriptionEn: 'Customer post-change all-clear — outcome summary.',
    descriptionAr: 'إشعار العملاء بعد التغيير — ملخص النتيجة وانتهاء العمل.' },

  // Change freeze announcements — calendar-driven org-wide notice.
  { id: 'CHG-FREEZE-ANNOUNCE',
    eventKind: 'CHANGE_FREEZE_ANNOUNCE', triggerSeverity: 'ANY',
    toStakeholderCode: 'EXCO',               channel: 'EMAIL',
    withinHours: 30 * 24, mandatory: false,
    regulatoryRef: 'ITIL 4 — change freeze · Internal release calendar',
    descriptionEn: 'Change-freeze announcement — exception process + dates.',
    descriptionAr: 'إعلان تجميد التغييرات — تواريخ الفترة وآلية الاستثناء.' },
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
  // Change requests — DD-33 demo seed
  'CHANGE_REQUEST:1': [
    { id: 'log-15', specId: 'CHG-CAB-NOTICE', eventDetectedAt: '2026-04-22T15:00:00',
      state: 'SENT', sentAt: '2026-04-22T15:30:00',
      noteEn: 'CAB approval circulated to change-owner + sponsor — PostgreSQL 16 upgrade.' },
    { id: 'log-16', specId: 'CHG-CUST-PRE', eventDetectedAt: '2026-04-25T09:00:00',
      state: 'SENT', sentAt: '2026-04-26T10:00:00',
      noteEn: '7-day customer pre-advisory dispatched — financial-systems read-only window.' },
    { id: 'log-17', specId: 'CHG-CUST-DURING', eventDetectedAt: '2026-05-04T22:00:00',
      state: 'PENDING',
      noteEn: 'Status-page update scheduled for cutover-window start (2026-05-04T22:00).' },
    { id: 'log-18', specId: 'CHG-CUST-POST', eventDetectedAt: '2026-05-05T02:00:00',
      state: 'PENDING',
      noteEn: 'All-clear advisory queued for after the maintenance window.' },
  ],
  'CHANGE_REQUEST:2': [
    { id: 'log-19', specId: 'CHG-CAB-NOTICE', eventDetectedAt: '2026-04-26T14:00:00',
      state: 'SENT', sentAt: '2026-04-26T14:20:00',
      noteEn: 'CAB approved firewall ruleset v3 — phased site-by-site rollout.' },
  ],
  'PERIODIC:CAB-FSC-2026-W18': [
    { id: 'log-20', specId: 'CAB-FSC-WEEKLY', eventDetectedAt: '2026-04-27T00:00:00',
      state: 'SENT', sentAt: '2026-04-27T08:00:00',
      noteEn: 'Week-18 FSC distributed — 7 SCHEDULED changes + 2 EMERGENCY pre-approved.' },
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
