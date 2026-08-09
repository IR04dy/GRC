// Intake / registration substrate — DD-54.
//
// Until now the platform was a state-management surface: registers,
// dashboards, lifecycles for records that already exist. This library
// closes the gap on the *front door* — every catalog item / form /
// portal where new work originates and gets triaged into the existing
// register lifecycle.
//
// Architecture references:
//   industry-standard GRC tooling     — Service-Catalog model: every intake is a typed
//                        catalog item with form schema, SLA, RACI, target
//                        register. Generic Issue entity classifies into
//                        risk / finding / incident.
//   industry-standard tooling Privacy   — Customer-facing DSR webform with reference-code
//                        tracker (multi-lingual, captcha-protected,
//                        auto-acknowledgement)
//   NAVEX EthicsPoint  — Anonymous whistleblower portal, multi-channel,
//                        case-tracking by reference code
//   continuous-monitoring tooling / continuous-monitoring tooling      — Customer Trust Center (public certifications,
//                        sub-processors), connector-first auto-intake
//   industry-standard tooling (RSA)       — Generic Findings Library with auto-classification
//   industry-standard tooling       — Loss-event capture (Basel-style operational
//                        risk), risk self-assessment
//
// Standards anchor:
//   PDPL Art. 4-7      — Data Subject Request handling
//   PDPL Art. 27       — Data breach notification
//   NCA ECC 6.1        — Security incident reporting
//   ISO 27001 §6.8     — Reporting information security events
//   ISO 27001 §A.5.36  — Compliance with policies (exceptions)
//   ISO 31000 §5.4.2   — Risk identification
//   ISO 22301 §8.2.2   — BIA
//   ISO 37001 §8.3     — Whistleblower (Nazaha)
//   IIA IPPF 2010      — Audit planning intake
//   ITIL v4            — RFC catalog
//   ISO 9001 §10.2.2   — Customer disclosure
//   SAMA Outsourcing §5.1 — Vendor onboarding
//   COSO ERM C4        — Risk assessment
//   NIST 800-61 §3.2   — Incident reporting

import { useCallback, useEffect, useState } from 'react';
import { intakeApi, type IntakeRecordDto } from './api';
import { isAuthenticated } from './auth';
import { SEED_INTAKE } from './intakeSeed';

// ── Domain types ────────────────────────────────────────────────────────

/** Every supported entry point. The kind drives the form schema, SLA,
 *  triage routing, and which register the intake ultimately promotes to. */
export type IntakeKind =
  // Public-facing (no auth required)
  | 'DSR_REQUEST'         // Customer DSR — PDPL Art. 4-7
  | 'BREACH_REPORT'       // Customer / 3rd-party reports a breach — PDPL Art. 27
  | 'VRP_DISCLOSURE'      // Security researcher responsible-disclosure — NCA ECC 6.1
  | 'SPEAKUP'             // Anonymous whistleblower — Nazaha, ISO 37001 §8.3
  // Authenticated employee intake
  | 'SECURITY_INCIDENT'   // Employee reports an event — NCA ECC 6.1, ISO 27001 §6.8
  | 'PRIVACY_BREACH'      // Employee reports privacy breach internally
  | 'RISK_ISSUE'          // Employee identifies emerging risk — ISO 31000 §5.4.2
  | 'CONTROL_OBSERVATION' // Employee notes a control gap / failure — ISO 27001 §10.2
  | 'POLICY_EXCEPTION'    // Time-bound exception request — ISO 27001 §A.5.36
  | 'VENDOR_ONBOARDING'   // Business owner registers new vendor — SAMA §5.1
  | 'DPIA_INITIATION'     // Project owner triggers DPIA — PDPL Art. 25
  | 'BIA_SUBMISSION'      // BU submits BIA — ISO 22301 §8.2.2
  | 'AUDIT_REQUEST'       // BU requests internal audit — IIA IPPF 2010
  | 'CHANGE_REQUEST'      // RFC — ITIL v4
  | 'ETHICS_DISCLOSURE'   // COI / gift / outside employment — Nazaha, IFAC §150
  | 'CONTROL_ATTESTATION' // Periodic attestation campaign response — ISO 27001 §9.2
  // ── DD-88: HRSD (Saudi Ministry of HR) workflows ──
  | 'SAUDIZATION_REPORT'  // Monthly Nitaqat headcount snapshot — Qiwa portal
  | 'WORKPLACE_INJURY'    // 24-hour GOSI injury notification
  | 'GOSI_EMPLOYEE_REGISTER' // Within 5 working days of hire — GOSI registration
  | 'EMPLOYMENT_CONTRACT' // New contract / renewal — Saudi Labor Law Art. 51
  | 'WPS_PAYROLL_RUN'     // Monthly Wage Protection System submission
  // ── DD-94: MOC (Ministry of Commerce) workflows ──
  | 'TASATTUR_SELF_ASSESSMENT'      // Annual Tasattur Risk Index self-assessment
  | 'BENEFICIAL_OWNERSHIP_DISCLOSURE' // BO disclosure under MOC Commercial Registry
  | 'CR_ANNUAL_CONFIRMATION'         // Annual commercial-registry confirmation
  | 'CAPITAL_CHANGE_NOTIFICATION'    // Capital-change notification (15 days)
  | 'CONSUMER_COMPLAINT_RESPONSE';   // MOC consumer-protection complaint response

export type IntakeStatus =
  | 'SUBMITTED'      // just received, auto-acked, SLA started
  | 'TRIAGING'       // assigned to a triager, awaiting decision
  | 'PROMOTED'       // converted to a register record (Risk / Incident / etc.)
  | 'REJECTED'       // not actionable, closed by triager
  | 'CLOSED';        // promoted record reached terminal state

export type IntakeChannel =
  | 'WEB_FORM'       // submitted via this portal
  | 'EMAIL'          // forwarded from intake@... mailbox
  | 'PHONE'          // captured during call
  | 'IN_PERSON'      // captured by reception / front desk
  | 'API_INGESTION'  // pushed by a connector (SIEM / scanner / regulator-watch)
  | 'MOBILE';        // mobile app — placeholder for future

export type SubmitterRole =
  | 'CUSTOMER'        // external customer / data subject
  | 'EMPLOYEE'        // authenticated staff
  | 'VENDOR'          // external supplier / partner
  | 'AUDITOR'         // external assurance party
  | 'REGULATOR'       // regulator-driven referral
  | 'PUBLIC'          // anonymous member of the public
  | 'SYSTEM';         // automated connector

export type RegisterTarget =
  | 'RISK' | 'INCIDENT' | 'DSR' | 'BREACH' | 'VENDOR' | 'CAPA' | 'FINDING'
  | 'CHANGE' | 'EXCEPTION' | 'DPIA' | 'BIA' | 'ENGAGEMENT' | 'ETHICS_CASE'
  | 'POLICY' | 'CONTROL_TEST';

export interface IntakeEvent {
  at: string;          // ISO timestamp
  status: IntakeStatus;
  actor: string;       // stakeholder code or 'system' / 'anonymous'
  note?: string;
}

export interface IntakeRecord {
  /** Stable unique id (used internally). */
  id: string;
  /** Human-friendly reference code shown to the submitter, used for
   *  case-tracking by anonymous users. Format: "{KIND-PREFIX}-XXXX-YYYY". */
  referenceCode: string;
  kind: IntakeKind;
  status: IntakeStatus;
  channel: IntakeChannel;

  /** Submitter context. Anonymous flag drives whether name/email are stored. */
  submitterIsAnonymous: boolean;
  submitterRole: SubmitterRole;
  submitterName?: string;
  submitterEmail?: string;
  submitterPhone?: string;

  /** Kind-specific form payload — every catalog item has its own schema. */
  payload: Record<string, unknown>;

  /** Triage routing — populated on submission via routeFor(). */
  assignedTo?: string;       // stakeholder code (e.g. 'DPO', 'CISO_OFFICE')
  slaDueAt?: string;         // ISO date — promotion or rejection deadline
  /** Severity self-asserted by submitter, may be re-classified at triage. */
  severity?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

  /** Auto-acknowledgement timestamp (drives SLA start). */
  acknowledgedAt: string;

  /** Promotion outcome (when triager promotes intake to a register record). */
  promotedTo?: { entityType: RegisterTarget; recordId: number | string; promotedAt: string };
  /** Rejection reason (when triager rejects). */
  rejectionReason?: string;

  /** Audit trail of every state transition + triager note. */
  history: IntakeEvent[];

  /** Free-form locale at submission time — drives reply language. */
  locale?: 'en' | 'ar';
}

// ── Catalog metadata ────────────────────────────────────────────────────
// Each kind has fixed metadata: who triages, the SLA window, the target
// register, regulator citation, ack templates. This lives next to the
// types so triage and SLA computation are consistent across the app.

export interface IntakeCatalogEntry {
  kind: IntakeKind;
  /** Two-letter prefix used in reference codes (e.g. "DS" for DSR). */
  codePrefix: string;
  /** Hours from submission to mandatory triage decision. PDPL Art. 4 → 30 days
   *  for DSR; NCA ECC 6.1 → 72 hours for cyber-incident notification; etc. */
  slaHours: number;
  /** Where promoted record lands. */
  defaultTarget: RegisterTarget;
  /** Stakeholder code who triages by default. */
  defaultAssignee: string;
  /** Standard / regulator citation. */
  reference: string;
  /** True when the form is reachable via /portal/* without auth. */
  publicSurface: boolean;
  /** True when submitter identity is optional (whistleblower pattern). */
  allowsAnonymous: boolean;
  /** Title shown on catalog tile + form header. */
  titleEn: string;
  titleAr: string;
  /** Short scope blurb for catalog tile. */
  blurbEn: string;
  blurbAr: string;
}

export const INTAKE_CATALOG: Record<IntakeKind, IntakeCatalogEntry> = {
  DSR_REQUEST: {
    kind: 'DSR_REQUEST', codePrefix: 'DS', slaHours: 30 * 24, defaultTarget: 'DSR',
    defaultAssignee: 'DPO', reference: 'PDPL Art. 4-7',
    publicSurface: true, allowsAnonymous: false,
    titleEn: 'Data subject request', titleAr: 'طلب صاحب البيانات',
    blurbEn: 'Access, erasure, rectification, portability or objection request under PDPL.',
    blurbAr: 'طلب الوصول أو المحو أو التصحيح أو النقل أو الاعتراض وفقاً لنظام حماية البيانات.',
  },
  BREACH_REPORT: {
    kind: 'BREACH_REPORT', codePrefix: 'BR', slaHours: 72, defaultTarget: 'BREACH',
    defaultAssignee: 'DPO', reference: 'PDPL Art. 27',
    publicSurface: true, allowsAnonymous: true,
    titleEn: 'Report a privacy breach', titleAr: 'الإبلاغ عن خرق خصوصية',
    blurbEn: 'External report of a suspected privacy breach involving our systems or data.',
    blurbAr: 'بلاغ خارجي عن خرق محتمل في الخصوصية يطال أنظمتنا أو بياناتنا.',
  },
  VRP_DISCLOSURE: {
    kind: 'VRP_DISCLOSURE', codePrefix: 'VR', slaHours: 24 * 5, defaultTarget: 'INCIDENT',
    defaultAssignee: 'CISO_OFFICE', reference: 'NCA ECC 6.1, ISO 29147',
    publicSurface: true, allowsAnonymous: true,
    titleEn: 'Vulnerability disclosure', titleAr: 'إفصاح عن ثغرة',
    blurbEn: 'Responsible disclosure for security researchers — we triage within 5 working days.',
    blurbAr: 'الإفصاح المسؤول لباحثي الأمن — نُجري الفرز خلال 5 أيام عمل.',
  },
  SPEAKUP: {
    kind: 'SPEAKUP', codePrefix: 'SU', slaHours: 24 * 7, defaultTarget: 'ETHICS_CASE',
    defaultAssignee: 'ETHICS_CTE', reference: 'Nazaha, ISO 37001 §8.3',
    publicSurface: true, allowsAnonymous: true,
    titleEn: 'SpeakUp — anonymous report', titleAr: 'تحدّث — بلاغ مجهول',
    blurbEn: 'Confidential channel for ethics, fraud, harassment or governance concerns.',
    blurbAr: 'قناة سرية للإبلاغ عن مخاوف أخلاقية أو احتيال أو تحرّش أو مخالفات حوكمة.',
  },
  SECURITY_INCIDENT: {
    kind: 'SECURITY_INCIDENT', codePrefix: 'SI', slaHours: 72, defaultTarget: 'INCIDENT',
    defaultAssignee: 'CSIRT', reference: 'NCA ECC 6.1, ISO 27001 §6.8, NIST 800-61',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Report a security event', titleAr: 'الإبلاغ عن حدث أمني',
    blurbEn: 'Phishing email, suspected malware, lost device, suspicious activity.',
    blurbAr: 'رسالة تصيّد، اشتباه ببرنامج خبيث، فقدان جهاز، نشاط مريب.',
  },
  PRIVACY_BREACH: {
    kind: 'PRIVACY_BREACH', codePrefix: 'PB', slaHours: 72, defaultTarget: 'BREACH',
    defaultAssignee: 'DPO', reference: 'PDPL Art. 27',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Report a privacy breach (internal)', titleAr: 'الإبلاغ عن خرق خصوصية (داخلي)',
    blurbEn: 'Lost data, mis-directed email, exposed personal data — internal channel.',
    blurbAr: 'فقدان بيانات، بريد مُرسَل عن طريق الخطأ، بيانات شخصية مكشوفة — قناة داخلية.',
  },
  RISK_ISSUE: {
    kind: 'RISK_ISSUE', codePrefix: 'RI', slaHours: 24 * 14, defaultTarget: 'RISK',
    defaultAssignee: 'RISK_OFFICER', reference: 'ISO 31000 §5.4.2, COSO ERM C4',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Raise a risk issue', titleAr: 'الإبلاغ عن مخاطرة',
    blurbEn: 'Identify an emerging risk — we triage within 2 weeks and add to the register.',
    blurbAr: 'رصد مخاطرة ناشئة — يتم الفرز خلال أسبوعين وإضافتها للسجل.',
  },
  CONTROL_OBSERVATION: {
    kind: 'CONTROL_OBSERVATION', codePrefix: 'CO', slaHours: 24 * 14, defaultTarget: 'FINDING',
    defaultAssignee: 'COMPLIANCE_LEAD', reference: 'ISO 27001 §10.2, IIA IPPF 2410',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Report a control failure / observation', titleAr: 'الإبلاغ عن خلل في ضابط رقابي',
    blurbEn: 'Spotted a control not working as designed? Log it for triage.',
    blurbAr: 'لاحظت ضابطاً رقابياً لا يعمل كما هو مُصمَّم؟ سجِّل ملاحظتك للفرز.',
  },
  POLICY_EXCEPTION: {
    kind: 'POLICY_EXCEPTION', codePrefix: 'PE', slaHours: 24 * 7, defaultTarget: 'EXCEPTION',
    defaultAssignee: 'POLICY_OFFICER', reference: 'ISO 27001 §A.5.36',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Request a policy exception', titleAr: 'طلب استثناء من سياسة',
    blurbEn: 'Time-bound exception with justification, owner sign-off, and sunset date.',
    blurbAr: 'استثناء مؤقّت بمبرّر وموافقة المالك وتاريخ انتهاء.',
  },
  VENDOR_ONBOARDING: {
    kind: 'VENDOR_ONBOARDING', codePrefix: 'VO', slaHours: 24 * 14, defaultTarget: 'VENDOR',
    defaultAssignee: 'TPRM_LEAD', reference: 'SAMA Outsourcing §5.1, NRC, ISO 27036',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Onboard a new vendor', titleAr: 'إدراج مورد جديد',
    blurbEn: 'Tier classification → IT, security, legal and finance reviews → activation.',
    blurbAr: 'تصنيف الدرجة → مراجعات تقنية وأمنية وقانونية ومالية → تفعيل.',
  },
  DPIA_INITIATION: {
    kind: 'DPIA_INITIATION', codePrefix: 'DI', slaHours: 24 * 14, defaultTarget: 'DPIA',
    defaultAssignee: 'DPO', reference: 'PDPL Art. 25',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Initiate a DPIA', titleAr: 'بدء تقييم أثر حماية البيانات',
    blurbEn: 'New processing activity that triggers a DPIA per PDPL Art. 25.',
    blurbAr: 'نشاط معالجة جديد يستدعي تقييم أثر حماية البيانات.',
  },
  BIA_SUBMISSION: {
    kind: 'BIA_SUBMISSION', codePrefix: 'BI', slaHours: 24 * 30, defaultTarget: 'BIA',
    defaultAssignee: 'BCM_LEAD', reference: 'ISO 22301 §8.2.2',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Submit BIA for my business unit', titleAr: 'تقديم تقييم الأثر للوحدة',
    blurbEn: 'Annual Business Impact Assessment for your business unit.',
    blurbAr: 'تقييم الأثر السنوي على الأعمال للوحدة التابعة.',
  },
  AUDIT_REQUEST: {
    kind: 'AUDIT_REQUEST', codePrefix: 'AR', slaHours: 24 * 14, defaultTarget: 'ENGAGEMENT',
    defaultAssignee: 'AUDIT_LEAD', reference: 'IIA IPPF 2010',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Request an internal audit', titleAr: 'طلب مراجعة داخلية',
    blurbEn: 'Business unit-driven request for an internal audit on a specific topic.',
    blurbAr: 'طلب من وحدة الأعمال لمراجعة داخلية على موضوع محدّد.',
  },
  CHANGE_REQUEST: {
    kind: 'CHANGE_REQUEST', codePrefix: 'CR', slaHours: 48, defaultTarget: 'CHANGE',
    defaultAssignee: 'CAB', reference: 'ITIL v4, ISO 20000-1 §8.2.6',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Submit a change request', titleAr: 'تقديم طلب تغيير',
    blurbEn: 'Standard / Normal / Emergency change request routed to the Change Advisory Board.',
    blurbAr: 'طلب تغيير قياسي/طبيعي/طارئ يُحال إلى لجنة استشارية للتغيير.',
  },
  ETHICS_DISCLOSURE: {
    kind: 'ETHICS_DISCLOSURE', codePrefix: 'ED', slaHours: 24 * 14, defaultTarget: 'ETHICS_CASE',
    defaultAssignee: 'ETHICS_OFFICER', reference: 'Nazaha, ISO 37001 §8.3, IFAC §150',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Submit a disclosure (COI / gift / outside employment)',
    titleAr: 'تقديم إفصاح (تضارب مصالح / هدية / عمل خارجي)',
    blurbEn: 'Conflict of interest, gift register, outside employment disclosure.',
    blurbAr: 'تضارب مصالح، تسجيل الهدايا، الإفصاح عن عمل خارجي.',
  },
  CONTROL_ATTESTATION: {
    kind: 'CONTROL_ATTESTATION', codePrefix: 'CA', slaHours: 24 * 14, defaultTarget: 'CONTROL_TEST',
    defaultAssignee: 'COMPLIANCE_LEAD', reference: 'ISO 27001 §9.2',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Attest to a control / policy', titleAr: 'إقرار بضابط/سياسة',
    blurbEn: 'Confirm operating effectiveness for the controls assigned to you.',
    blurbAr: 'تأكيد الفاعلية التشغيلية للضوابط المُسنَدة إليك.',
  },

  // ── DD-88: HRSD workflows ─────────────────────────────────────────────
  SAUDIZATION_REPORT: {
    kind: 'SAUDIZATION_REPORT', codePrefix: 'SR', slaHours: 24 * 5, defaultTarget: 'CONTROL_TEST',
    defaultAssignee: 'HR_LEAD', reference: 'HRSD Nitaqat — Qiwa portal',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Submit monthly Saudization report',
    titleAr: 'تقديم تقرير السعودة الشهري',
    blurbEn: 'Headcount + Saudi/non-Saudi breakdown — Qiwa portal submission, due by the 5th of following month.',
    blurbAr: 'إحصاء العمالة + توزيع السعوديين/غير السعوديين — يُقدَّم عبر بوابة قِوى قبل اليوم الخامس من الشهر التالي.',
  },
  WORKPLACE_INJURY: {
    kind: 'WORKPLACE_INJURY', codePrefix: 'WI', slaHours: 24, defaultTarget: 'INCIDENT',
    defaultAssignee: 'HR_LEAD', reference: 'HRSD GOSI — 24-hour notification',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Report a workplace injury / occupational illness',
    titleAr: 'الإبلاغ عن إصابة عمل أو مرض مهني',
    blurbEn: 'Mandatory 24-hour GOSI notification. Failure exposes the employer to medical-claim cost recovery.',
    blurbAr: 'إبلاغ إلزامي للتأمينات خلال 24 ساعة. عدم الإبلاغ يُحمّل صاحب العمل تكاليف المطالبات الطبية.',
  },
  GOSI_EMPLOYEE_REGISTER: {
    kind: 'GOSI_EMPLOYEE_REGISTER', codePrefix: 'GR', slaHours: 24 * 5, defaultTarget: 'CONTROL_TEST',
    defaultAssignee: 'HR_LEAD', reference: 'HRSD GOSI — 5 working days of hire',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Register a new employee with GOSI',
    titleAr: 'تسجيل موظف جديد في التأمينات الاجتماعية',
    blurbEn: 'Mandatory within 5 working days of hire. Saudi: 22% combined contribution; non-Saudi: 2% occupational hazards.',
    blurbAr: 'إلزامي خلال 5 أيام عمل من تاريخ التعيين. سعودي: اشتراك 22٪؛ غير سعودي: 2٪ تأمين الأخطار المهنية.',
  },
  EMPLOYMENT_CONTRACT: {
    kind: 'EMPLOYMENT_CONTRACT', codePrefix: 'EC', slaHours: 24 * 7, defaultTarget: 'POLICY',
    defaultAssignee: 'HR_LEAD', reference: 'Saudi Labor Law Art. 51, 52, 53',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Draft / renew employment contract',
    titleAr: 'إعداد / تجديد عقد عمل',
    blurbEn: 'Arabic-language contract with Saudi-compliant terms (probation ≤ 90 days, EOSB clause, weekly rest, annual leave).',
    blurbAr: 'عقد باللغة العربية بشروط متوافقة (تجربة ≤ 90 يوماً، بند مكافأة نهاية الخدمة، راحة أسبوعية، إجازة سنوية).',
  },
  WPS_PAYROLL_RUN: {
    kind: 'WPS_PAYROLL_RUN', codePrefix: 'WP', slaHours: 24 * 11, defaultTarget: 'CONTROL_TEST',
    defaultAssignee: 'HR_LEAD', reference: 'HRSD WPS — by 11th of following month',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Confirm monthly WPS payroll run',
    titleAr: 'تأكيد تشغيل نظام حماية الأجور الشهري',
    blurbEn: 'Salary deposits via SAMA-licensed bank by the 11th of following month. Non-compliance suspends new hires + visa renewals.',
    blurbAr: 'إيداع الرواتب عبر بنك مرخَّص من ساما قبل اليوم 11 من الشهر التالي. عدم الامتثال يوقف التوظيف الجديد + تجديد التأشيرات.',
  },
  // ── DD-94: MOC (Ministry of Commerce) intakes ──
  TASATTUR_SELF_ASSESSMENT: {
    kind: 'TASATTUR_SELF_ASSESSMENT', codePrefix: 'TA', slaHours: 24 * 30, defaultTarget: 'CONTROL_TEST',
    defaultAssignee: 'COMPLIANCE_OFFICER', reference: 'MOC Tasattur Risk Index — annual',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Submit annual Tasattur self-assessment',
    titleAr: 'تقديم التقييم الذاتي السنوي للتستر',
    blurbEn: 'Self-score the 6 Tasattur risk indicators and submit through the MOC portal. Bands: LOW / MEDIUM / HIGH / CRITICAL drive enforcement intensity.',
    blurbAr: 'تقييم ذاتي للمؤشرات الستة للتستر التجاري وتقديمه عبر بوابة وزارة التجارة. النطاقات: منخفض / متوسط / عالي / حرج تحدّد مستوى التنفيذ.',
  },
  BENEFICIAL_OWNERSHIP_DISCLOSURE: {
    kind: 'BENEFICIAL_OWNERSHIP_DISCLOSURE', codePrefix: 'BO', slaHours: 24 * 14, defaultTarget: 'POLICY',
    defaultAssignee: 'COMPLIANCE_OFFICER', reference: 'MOC Commercial Registry — BO disclosure',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'File beneficial-ownership disclosure',
    titleAr: 'تقديم إفصاح المُستفيد الحقيقي',
    blurbEn: 'Disclose every natural person owning ≥ 25% directly or indirectly. Required at incorporation, annually, and within 14 days of any change.',
    blurbAr: 'الإفصاح عن كل شخص طبيعي يملك 25٪ أو أكثر مباشرةً أو غير مباشرة. مطلوب عند التأسيس وسنوياً وخلال 14 يوماً من أي تغيير.',
  },
  CR_ANNUAL_CONFIRMATION: {
    kind: 'CR_ANNUAL_CONFIRMATION', codePrefix: 'CR', slaHours: 24 * 30, defaultTarget: 'CONTROL_TEST',
    defaultAssignee: 'COMPLIANCE_OFFICER', reference: 'MOC Commercial Registry — annual confirmation',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Confirm annual commercial-registry data',
    titleAr: 'تأكيد البيانات السنوية للسجل التجاري',
    blurbEn: 'Confirm or update CR data (activity codes, capital, address, manager, BO) within 30 days of CR-anniversary. Late confirmation incurs daily fines.',
    blurbAr: 'تأكيد أو تحديث بيانات السجل التجاري (الأنشطة، رأس المال، العنوان، المدير، المُستفيد الحقيقي) خلال 30 يوماً من تاريخ السجل السنوي. التأخير يستوجب غرامات يومية.',
  },
  CAPITAL_CHANGE_NOTIFICATION: {
    kind: 'CAPITAL_CHANGE_NOTIFICATION', codePrefix: 'CC', slaHours: 24 * 15, defaultTarget: 'POLICY',
    defaultAssignee: 'COMPLIANCE_OFFICER', reference: 'MOC Commercial Registry — capital change',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Notify MOC of capital change',
    titleAr: 'إبلاغ وزارة التجارة بتغيّر رأس المال',
    blurbEn: 'Capital increase or decrease must be reflected in the CR within 15 days, including auditor confirmation and shareholder resolution.',
    blurbAr: 'الزيادة أو التخفيض في رأس المال يجب تسجيله في السجل التجاري خلال 15 يوماً، مع تأكيد المراجع وقرار الجمعية العامة.',
  },
  CONSUMER_COMPLAINT_RESPONSE: {
    kind: 'CONSUMER_COMPLAINT_RESPONSE', codePrefix: 'CP', slaHours: 24 * 5, defaultTarget: 'INCIDENT',
    defaultAssignee: 'COMPLIANCE_OFFICER', reference: 'MOC Consumer Protection — 5-day response',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Respond to MOC consumer complaint',
    titleAr: 'الردّ على شكوى المستهلك من وزارة التجارة',
    blurbEn: 'Substantive response within 5 working days of complaint receipt via "Maaroof" portal. Failure escalates to enforcement.',
    blurbAr: 'ردّ موضوعي خلال 5 أيام عمل من استلام الشكوى عبر منصة "معروف". عدم الردّ يصعّد إلى التنفيذ.',
  },
};

export const INTAKE_STATUS_TONE: Record<IntakeStatus, 'neutral' | 'info' | 'primary' | 'warning' | 'success' | 'danger'> = {
  SUBMITTED: 'info', TRIAGING: 'warning', PROMOTED: 'primary',
  REJECTED: 'danger', CLOSED: 'success',
};

export const INTAKE_STATUS_LABEL: Record<IntakeStatus, { en: string; ar: string }> = {
  SUBMITTED:    { en: 'Submitted',    ar: 'تم الاستلام' },
  TRIAGING:     { en: 'Triaging',     ar: 'قيد الفرز' },
  PROMOTED:     { en: 'Promoted',     ar: 'مُحوَّل لسجل' },
  REJECTED:     { en: 'Rejected',     ar: 'مرفوض' },
  CLOSED:       { en: 'Closed',       ar: 'مغلق' },
};

// ── Helpers ─────────────────────────────────────────────────────────────

/** Build a human-friendly reference code: "DS-A4F2-9XYZ". The user
 *  bookmarks this to track their submission anonymously. */
export function generateReferenceCode(kind: IntakeKind): string {
  const prefix = INTAKE_CATALOG[kind].codePrefix;
  const seg = () => Math.random().toString(36).toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 4).padEnd(4, 'X');
  return `${prefix}-${seg()}-${seg()}`;
}

export function slaDeadline(kind: IntakeKind, submittedAt: Date = new Date()): string {
  const ms = INTAKE_CATALOG[kind].slaHours * 60 * 60 * 1000;
  return new Date(submittedAt.getTime() + ms).toISOString();
}

/** Hours remaining before SLA deadline (negative = overdue). */
export function hoursUntilSla(rec: IntakeRecord, now: Date = new Date()): number {
  if (!rec.slaDueAt) return Infinity;
  return Math.round((new Date(rec.slaDueAt).getTime() - now.getTime()) / (60 * 60 * 1000));
}

// ── Seed data ──────────────────────────────────────────────────────────
// Moved to ./intakeSeed — F2-172. Re-exported so existing importers keep working,
// and consumed by useIntake() only when there is no session.
export { SEED_INTAKE } from './intakeSeed';


// ── Persistence — F2-172 ────────────────────────────────────────────────
//
// Everything below used to be `localStorage` and nothing else, for the whole lifecycle:
// submit, triage, promote, case-track. A signed-in DPO pressed New → DPIA, a triager
// pressed Promote, and the only trace was in one browser's site data (F2-171/F2-172).
//
// The rule now, and it is F2-169's rule rather than a new one — *fabricated data may
// substitute only for a fabricated parent, or when there is no session*:
//
//   • signed in  → the server is the ONLY source. A failed load is reported, never
//                  replaced. The seed records above are fabricated, so a signed-in user
//                  must not see them either — they used to render unconditionally.
//   • no session → the seed + the browser overlay, which is the public-portal path.
//                  That path is blocked on F2-138 (there is no anonymous submit endpoint
//                  to point at), and `publicPortalBackedByServer.test.ts` holds its two
//                  exemptions open with that reason recorded.
//
// ⚠ Promotion is authenticated by construction — the triager inbox is behind a login — so
// the no-session branch cannot promote. It throws rather than falling back, because a
// fallback there is precisely the defect: a promotion recorded in a browser.

const STORAGE_KEY = 'grc.intake';

interface IntakeOverlay {
  added: IntakeRecord[];
  patches: Record<string, Partial<IntakeRecord>>;
}

function load(): IntakeOverlay {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { added: [], patches: {} };
    const parsed = JSON.parse(raw);
    return { added: parsed.added ?? [], patches: parsed.patches ?? {} };
  } catch { return { added: [], patches: {} }; }
}
function save(o: IntakeOverlay) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(o)); } catch { /* ignore */ }
}

/** Server DTO → the shape every page in this app already renders. */
function fromDto(d: IntakeRecordDto): IntakeRecord {
  let payload: Record<string, unknown> = {};
  if (d.payload) {
    try {
      const parsed = JSON.parse(d.payload);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        payload = parsed as Record<string, unknown>;
      }
    } catch { /* a payload we cannot parse renders as empty, not as an error page */ }
  }
  return {
    // The server keys on a bigint and this interface on a string. Kept as the plain
    // decimal so `Number(rec.id)` round-trips for the triage call below.
    id: String(d.id),
    referenceCode: d.referenceCode,
    kind: d.kind as IntakeKind,
    status: d.status as IntakeStatus,
    channel: d.channel as IntakeChannel,
    submitterIsAnonymous: d.submitterIsAnonymous,
    submitterRole: (d.submitterRole ?? 'EMPLOYEE') as SubmitterRole,
    submitterName: d.submitterName ?? undefined,
    submitterEmail: d.submitterEmail ?? undefined,
    submitterPhone: d.submitterPhone ?? undefined,
    payload,
    assignedTo: d.assignedTo ?? undefined,
    slaDueAt: d.slaDueAt ?? undefined,
    severity: (d.severity ?? undefined) as IntakeRecord['severity'],
    acknowledgedAt: d.acknowledgedAt,
    promotedTo: d.promotedToEntityType && d.promotedToRecordId
      ? {
          entityType: d.promotedToEntityType as RegisterTarget,
          recordId: d.promotedToRecordId,
          promotedAt: d.promotedAt ?? d.acknowledgedAt,
        }
      : undefined,
    rejectionReason: d.rejectionReason ?? undefined,
    history: (d.history ?? []).map(h => ({
      at: h.at, status: h.status as IntakeStatus, actor: h.actor,
      ...(h.note ? { note: h.note } : {}),
    })),
    locale: (d.locale ?? undefined) as IntakeRecord['locale'],
  };
}

export interface PromotionOutcome {
  entityType: RegisterTarget;
  /** The id of the record the SERVER created. Never minted here. */
  recordId: string;
}

/**
 * React hook over the intake substrate.
 *
 * `submit` and `triage` are **async** — they were synchronous while everything happened in
 * memory, and every caller has to await them now. That is the visible cost of the record
 * being real, and it is the point: a synchronous "promoted" was only ever possible because
 * nothing was written.
 */
export function useIntake() {
  const signedIn = isAuthenticated();
  const [overlay, setOverlay] = useState<IntakeOverlay>(() => load());
  const [server, setServer] = useState<IntakeRecord[] | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [loading, setLoading] = useState(signedIn);

  useEffect(() => { if (!signedIn) save(overlay); }, [overlay, signedIn]);

  const refresh = useCallback(async () => {
    if (!signedIn) return;
    setLoading(true);
    try {
      const page = await intakeApi.list();
      setServer(page.content.map(fromDto));
      setLoadFailed(false);
    } catch {
      // ⚠ Deliberately NOT `setServer(SEED_INTAKE)`. A signed-in user whose inbox failed to
      // load must see that it failed; showing five fabricated submissions instead is
      // F2-169's defect, and on a triage queue it is worse than on a register — it invites
      // a decision on a case that does not exist.
      setServer(null);
      setLoadFailed(true);
    } finally {
      setLoading(false);
    }
  }, [signedIn]);

  useEffect(() => { void refresh(); }, [refresh]);

  const items: IntakeRecord[] = signedIn
    ? (server ?? [])
    : [
        ...SEED_INTAKE.map(r => overlay.patches[r.id] ? { ...r, ...overlay.patches[r.id] } : r),
        ...overlay.added,
      ];

  const submit = useCallback(async (init: Omit<IntakeRecord, 'id' | 'referenceCode' | 'acknowledgedAt' | 'slaDueAt' | 'history' | 'status' | 'assignedTo'> & {
    severity?: IntakeRecord['severity'];
  }): Promise<IntakeRecord> => {
    if (signedIn) {
      const dto = await intakeApi.submit({
        kind: init.kind,
        channel: init.channel,
        submitterIsAnonymous: init.submitterIsAnonymous,
        submitterRole: init.submitterRole,
        submitterName: init.submitterName,
        submitterEmail: init.submitterEmail,
        submitterPhone: init.submitterPhone,
        payload: JSON.stringify(init.payload ?? {}),
        severity: init.severity,
        locale: init.locale,
      });
      const rec = fromDto(dto);
      setServer(prev => [...(prev ?? []), rec]);
      return rec;
    }

    // No session: the public-portal path, still client-only pending F2-138.
    const id = `intake-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`;
    const referenceCode = generateReferenceCode(init.kind);
    const now = new Date();
    const acknowledgedAt = now.toISOString();
    const slaDueAt = slaDeadline(init.kind, now);
    const catalog = INTAKE_CATALOG[init.kind];
    const next: IntakeRecord = {
      ...init,
      id,
      referenceCode,
      status: 'TRIAGING',            // auto-ack moves it straight to triaging
      acknowledgedAt,
      slaDueAt,
      assignedTo: catalog.defaultAssignee,
      history: [
        { at: acknowledgedAt, status: 'SUBMITTED', actor: init.submitterIsAnonymous ? 'anonymous' : (init.submitterEmail ?? init.submitterName ?? 'unknown'), note: `Submitted via ${init.channel}` },
        { at: acknowledgedAt, status: 'TRIAGING', actor: 'system', note: `Auto-acknowledged. Routed to ${catalog.defaultAssignee}. SLA due ${new Date(slaDueAt).toISOString().slice(0, 10)}.` },
      ],
    };
    setOverlay(prev => ({ ...prev, added: [...prev.added, next] }));
    return next;
  }, [signedIn]);

  /**
   * Apply a triage decision.
   *
   * On PROMOTE the returned {@link PromotionOutcome} carries the id of a record the
   * **server** created — `IntakeService` resolves the target from the kind's catalogue
   * entry and stamps the id from the row it wrote. Nothing is minted here, and there is no
   * request field for either value.
   *
   * Errors propagate. A promotion can legitimately fail — the caller lacks the target
   * module's write permission (403), the submission was already promoted (409), the target
   * has no register (409), or a BIA names a business process this tenant does not have
   * (409). Each of those is a sentence the triager needs; swallowing it and flipping the
   * row to "Promoted" is exactly what F2-171 was.
   */
  const triage = useCallback(async (
    id: string,
    decision: 'PROMOTE' | 'REJECT' | 'CLOSE',
    _actor: string,
    extra?: Partial<IntakeRecord>,
  ): Promise<PromotionOutcome | null> => {
    if (!signedIn) {
      throw new Error('Triage requires a signed-in session — the decision is recorded on the '
        + 'server, and the promoted record is created there.');
    }
    const numericId = Number(id);
    if (!Number.isFinite(numericId)) {
      // A `intake-<base36>` id belongs to an overlay row from before this wiring. There is
      // no server record to triage, and inventing one is the defect.
      throw new Error(`Intake ${id} exists only in this browser and cannot be triaged — it was `
        + 'submitted before intake was wired to the server (F2-172).');
    }
    const dto = await intakeApi.triage(numericId, decision, extra?.rejectionReason ?? undefined);
    const updated = fromDto(dto);
    setServer(prev => (prev ?? []).map(r => (r.id === updated.id ? updated : r)));

    if (decision !== 'PROMOTE' || !updated.promotedTo) return null;
    return {
      entityType: updated.promotedTo.entityType,
      recordId: String(updated.promotedTo.recordId),
    };
  }, [signedIn]);

  const findByReferenceCode = useCallback((code: string): IntakeRecord | null => {
    const trimmed = code.trim().toUpperCase();
    return items.find(r => r.referenceCode.toUpperCase() === trimmed) ?? null;
  }, [items]);

  const reset = useCallback(() => setOverlay({ added: [], patches: {} }), []);

  return { items, submit, triage, findByReferenceCode, reset, refresh, loading, loadFailed, signedIn };
}
