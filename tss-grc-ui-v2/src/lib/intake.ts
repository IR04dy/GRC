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
import { ApiError, intakeApi, type IntakeRecordWire } from './api';

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
  /** The server-assigned id (intake.intake_records.id) — F2-172. Was a client-minted
   *  string ('intake-001') while the substrate lived in localStorage. */
  id: number;
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
  assignedTo?: string;       // role name from iam.roles (e.g. 'DPO', 'CISO') — see defaultAssignee
  slaDueAt?: string;         // ISO date — promotion or rejection deadline
  /** Severity self-asserted by submitter, may be re-classified at triage. */
  severity?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

  /** Auto-acknowledgement timestamp (drives SLA start). */
  acknowledgedAt: string;

  /** Promotion outcome (when triager promotes intake to a register record). */
  /** Promotion outcome. `recordId` is NULL until F2-172s second half creates the target
   *  row — the server refuses a client-supplied id, so there is nothing to fall back to. */
  promotedTo?: { entityType: RegisterTarget; recordId: number | string | null; promotedAt: string };
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
  /** Who triages by default, as a **role name from `iam.roles`** — not a free-text
   *  stakeholder label (F2-174). Ten of the original fourteen codes matched no role in
   *  the platform's own catalogue (`CISO_OFFICE`, `ETHICS_CTE`, `CSIRT`, `RISK_OFFICER`,
   *  `COMPLIANCE_LEAD`, `POLICY_OFFICER`, `TPRM_LEAD`, `BCM_LEAD`, `AUDIT_LEAD`, `CAB`),
   *  so no tenant administrator could make them reach anybody. Server-side this string is
   *  what lands in `intake.intake_records.assigned_to`, and it is pinned against the role
   *  catalogue by `IntakeAssigneeRoleResolutionTest`. ⚠ Still only a label on this screen —
   *  it names a role that exists, which is not the same as reaching a person. */
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
    defaultAssignee: 'CISO', reference: 'NCA ECC 6.1, ISO 29147',
    publicSurface: true, allowsAnonymous: true,
    titleEn: 'Vulnerability disclosure', titleAr: 'إفصاح عن ثغرة',
    blurbEn: 'Responsible disclosure for security researchers — we triage within 5 working days.',
    blurbAr: 'الإفصاح المسؤول لباحثي الأمن — نُجري الفرز خلال 5 أيام عمل.',
  },
  SPEAKUP: {
    kind: 'SPEAKUP', codePrefix: 'SU', slaHours: 24 * 7, defaultTarget: 'ETHICS_CASE',
    defaultAssignee: 'ETHICS_OFFICER', reference: 'Nazaha, ISO 37001 §8.3',
    publicSurface: true, allowsAnonymous: true,
    titleEn: 'SpeakUp — anonymous report', titleAr: 'تحدّث — بلاغ مجهول',
    blurbEn: 'Confidential channel for ethics, fraud, harassment or governance concerns.',
    blurbAr: 'قناة سرية للإبلاغ عن مخاوف أخلاقية أو احتيال أو تحرّش أو مخالفات حوكمة.',
  },
  SECURITY_INCIDENT: {
    kind: 'SECURITY_INCIDENT', codePrefix: 'SI', slaHours: 72, defaultTarget: 'INCIDENT',
    defaultAssignee: 'IR_LEAD', reference: 'NCA ECC 6.1, ISO 27001 §6.8, NIST 800-61',
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
    defaultAssignee: 'RISK_MANAGER', reference: 'ISO 31000 §5.4.2, COSO ERM C4',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Raise a risk issue', titleAr: 'الإبلاغ عن مخاطرة',
    blurbEn: 'Identify an emerging risk — we triage within 2 weeks and add to the register.',
    blurbAr: 'رصد مخاطرة ناشئة — يتم الفرز خلال أسبوعين وإضافتها للسجل.',
  },
  CONTROL_OBSERVATION: {
    kind: 'CONTROL_OBSERVATION', codePrefix: 'CO', slaHours: 24 * 14, defaultTarget: 'FINDING',
    defaultAssignee: 'COMPLIANCE_OFFICER', reference: 'ISO 27001 §10.2, IIA IPPF 2410',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Report a control failure / observation', titleAr: 'الإبلاغ عن خلل في ضابط رقابي',
    blurbEn: 'Spotted a control not working as designed? Log it for triage.',
    blurbAr: 'لاحظت ضابطاً رقابياً لا يعمل كما هو مُصمَّم؟ سجِّل ملاحظتك للفرز.',
  },
  POLICY_EXCEPTION: {
    kind: 'POLICY_EXCEPTION', codePrefix: 'PE', slaHours: 24 * 7, defaultTarget: 'EXCEPTION',
    defaultAssignee: 'COMPLIANCE_OFFICER', reference: 'ISO 27001 §A.5.36',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Request a policy exception', titleAr: 'طلب استثناء من سياسة',
    blurbEn: 'Time-bound exception with justification, owner sign-off, and sunset date.',
    blurbAr: 'استثناء مؤقّت بمبرّر وموافقة المالك وتاريخ انتهاء.',
  },
  VENDOR_ONBOARDING: {
    kind: 'VENDOR_ONBOARDING', codePrefix: 'VO', slaHours: 24 * 14, defaultTarget: 'VENDOR',
    defaultAssignee: 'SUPPLIER_MANAGER', reference: 'SAMA Outsourcing §5.1, NRC, ISO 27036',
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
    defaultAssignee: 'BCM_MANAGER', reference: 'ISO 22301 §8.2.2',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Submit BIA for my business unit', titleAr: 'تقديم تقييم الأثر للوحدة',
    blurbEn: 'Annual Business Impact Assessment for your business unit.',
    blurbAr: 'تقييم الأثر السنوي على الأعمال للوحدة التابعة.',
  },
  AUDIT_REQUEST: {
    kind: 'AUDIT_REQUEST', codePrefix: 'AR', slaHours: 24 * 14, defaultTarget: 'ENGAGEMENT',
    defaultAssignee: 'INTERNAL_AUDITOR', reference: 'IIA IPPF 2010',
    publicSurface: false, allowsAnonymous: false,
    titleEn: 'Request an internal audit', titleAr: 'طلب مراجعة داخلية',
    blurbEn: 'Business unit-driven request for an internal audit on a specific topic.',
    blurbAr: 'طلب من وحدة الأعمال لمراجعة داخلية على موضوع محدّد.',
  },
  CHANGE_REQUEST: {
    kind: 'CHANGE_REQUEST', codePrefix: 'CR', slaHours: 48, defaultTarget: 'CHANGE',
    defaultAssignee: 'CHANGE_MANAGER', reference: 'ITIL v4, ISO 20000-1 §8.2.6',
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
    defaultAssignee: 'COMPLIANCE_OFFICER', reference: 'ISO 27001 §9.2',
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

// F2-172 — `generateReferenceCode()` and `slaDeadline()` were DELETED here, not moved.
//
// Both minted, on the client, a value that only the server can legitimately produce: the
// tracking code a submitter is told to keep, and the date a statutory clock expires. While
// the substrate was client-only they were load-bearing; now `IntakeService` mints the code
// (`IntakeKindRouting.codePrefix` + a `SecureRandom` segment) and computes the deadline from
// the same catalogue, and a second implementation on the client could only ever disagree
// with it. A client-side reference-code generator is also precisely the tool needed to
// fabricate an acknowledgement for a submission that was never filed, which is the defect
// this slice removes from `/portal/dsr` — so leaving it exported would leave the mechanism
// available with nothing pointing at it.
//
// `hoursUntilSla` stays: it reads the server's `slaDueAt` rather than deciding it.

/** Hours remaining before SLA deadline (negative = overdue). */
export function hoursUntilSla(rec: IntakeRecord, now: Date = new Date()): number {
  if (!rec.slaDueAt) return Infinity;
  return Math.round((new Date(rec.slaDueAt).getTime() - now.getTime()) / (60 * 60 * 1000));
}

// ── The server-backed hook ──────────────────────────────────────────────
//
// F2-172. What this replaced, and why none of it survives:
//
//   * a `SEED_INTAKE` array of 5 fabricated records, merged into the inbox for every
//     caller. Under the rule F2-169 settled, fabricated rows may substitute only for a
//     fabricated parent or with no session at all — an authenticated triager looking at
//     a real (possibly empty) tenant is neither. An empty inbox is a fact; five invented
//     submissions with invented submitter names are not, and they made the honest empty
//     state unreachable (F2-166's shape).
//
//   * a `localStorage` overlay (`grc.intake`) holding `added` and `patches`. Every
//     submission the platform's front door ever received lived in the submitter's own
//     browser: invisible to the triager it was routed to, invisible to every other user,
//     and gone with the site data. `IntakeController` had been built at DD-82, was
//     reachable, and had never once been called — probed live on 2026-08-09 the inbox
//     answered 200 with totalElements 0 against 0 rows in `intake.intake_records`.
//
//   * a client-authored triage `actor` ('demo.triager@tss.sa', hardcoded at the call
//     site). The server takes the actor from the security context, so the parameter is
//     gone rather than ignored — a parameter nothing reads is an invitation to believe
//     it matters.
//
// ⚠ The public case-tracker deliberately does NOT use this hook. It is an unauthenticated
// page, so mounting an authenticated inbox load there would 401 on every visit. See
// trackByReferenceCode below.

/** Wire → domain. `payload` crosses as a JSON string because the column is NVARCHAR(MAX). */
function fromWire(w: IntakeRecordWire): IntakeRecord {
  let payload: Record<string, unknown> = {};
  if (w.payload) {
    try {
      const parsed: unknown = JSON.parse(w.payload);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        payload = parsed as Record<string, unknown>;
      }
    } catch {
      // A payload the server cannot round-trip is a data problem, not a render problem.
      // Surface it as a field rather than dropping the record — the triager still needs
      // to see that something arrived.
      payload = { _unparseablePayload: w.payload };
    }
  }
  return {
    id: w.id,
    referenceCode: w.referenceCode,
    kind: w.kind as IntakeKind,
    status: w.status as IntakeStatus,
    channel: w.channel as IntakeChannel,
    submitterIsAnonymous: w.submitterIsAnonymous,
    submitterRole: (w.submitterRole ?? 'EMPLOYEE') as SubmitterRole,
    ...(w.submitterName ? { submitterName: w.submitterName } : {}),
    ...(w.submitterEmail ? { submitterEmail: w.submitterEmail } : {}),
    ...(w.submitterPhone ? { submitterPhone: w.submitterPhone } : {}),
    payload,
    ...(w.assignedTo ? { assignedTo: w.assignedTo } : {}),
    ...(w.slaDueAt ? { slaDueAt: w.slaDueAt } : {}),
    ...(w.severity ? { severity: w.severity as IntakeRecord['severity'] } : {}),
    acknowledgedAt: w.acknowledgedAt,
    ...(w.promotedToEntityType
      ? {
          promotedTo: {
            entityType: w.promotedToEntityType as RegisterTarget,
            // The primary key of the register row the server wrote, stamped from the write
            // itself. Still typed nullable: rows promoted before F2-172's second half landed
            // carry a NULL here, and a UI that assumes an id would render "#undefined" on
            // them. There is nothing to fall back to — an id the server did not issue is the
            // defect, not the gap.
            recordId: w.promotedToRecordId,
            promotedAt: w.promotedAt ?? w.acknowledgedAt,
          },
        }
      : {}),
    ...(w.rejectionReason ? { rejectionReason: w.rejectionReason } : {}),
    history: (w.history ?? []).map(e => ({
      at: e.at,
      status: e.status as IntakeStatus,
      actor: e.actor,
      ...(e.note ? { note: e.note } : {}),
    })),
    ...(w.locale ? { locale: w.locale as 'en' | 'ar' } : {}),
  };
}

export type IntakeSubmitInit =
  Omit<IntakeRecord,
    'id' | 'referenceCode' | 'acknowledgedAt' | 'slaDueAt' | 'history' | 'status' | 'assignedTo'>
  & { severity?: IntakeRecord['severity'] };

/**
 * The triager inbox and the submit/triage actions, backed by `/api/v1/intake`.
 *
 * `error` is a first-class return value rather than a swallowed catch: an inbox that renders
 * empty because the request failed is indistinguishable from a tenant with no submissions,
 * and that ambiguity is what four earlier findings on this programme turned out to be.
 */
export function useIntake(options?: { skip?: boolean }) {
  const skip = options?.skip ?? false;
  const [items, setItems] = useState<IntakeRecord[]>([]);
  const [loading, setLoading] = useState(!skip);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (skip) return;
    setLoading(true);
    setError(null);
    try {
      const page = await intakeApi.list({ size: 200 });
      setItems((page.content ?? []).map(fromWire));
    } catch (e) {
      setItems([]);
      setError(e instanceof Error ? e.message : 'Could not load the intake inbox');
    } finally {
      setLoading(false);
    }
  }, [skip]);

  useEffect(() => { void reload(); }, [reload]);

  /** Files a submission. Throws on failure — callers already funnel ApiError into a toast,
   *  and a swallowed failure here would show a reference code for a report nobody received,
   *  which is the defect this whole slice exists to remove. */
  const submit = useCallback(async (init: IntakeSubmitInit): Promise<IntakeRecord> => {
    const wire = await intakeApi.submit({
      kind: init.kind,
      channel: init.channel,
      submitterIsAnonymous: init.submitterIsAnonymous,
      ...(init.submitterRole ? { submitterRole: init.submitterRole } : {}),
      ...(init.submitterName ? { submitterName: init.submitterName } : {}),
      ...(init.submitterEmail ? { submitterEmail: init.submitterEmail } : {}),
      ...(init.submitterPhone ? { submitterPhone: init.submitterPhone } : {}),
      payload: JSON.stringify(init.payload ?? {}),
      ...(init.severity ? { severity: init.severity } : {}),
      ...(init.locale ? { locale: init.locale } : {}),
    });
    const rec = fromWire(wire);
    setItems(prev => [...prev, rec]);
    return rec;
  }, []);

  /**
   * Applies a triage decision. No `actor` parameter: the server stamps it from the
   * authenticated principal.
   *
   * On PROMOTE the server creates the register record inside the same transaction that flips
   * the status, so what comes back names a row that exists. This function's job is therefore
   * only to *report* it — everything it used to do is gone:
   *
   *   * `mintPromotedId()` + `addPromoted()`, which wrote the "promoted record" into
   *     `localStorage`. F2-171 made that placeholder inert (a negative id, refused by the
   *     api client, with disabled actions); F2-172's server half made it unnecessary.
   *     Keeping an inert placeholder beside a real row would be worse than either alone —
   *     two answers to "was this promoted?", one of them per-browser.
   *   * deriving the target from `INTAKE_CATALOG` here. The client's catalogue and the
   *     server's agreed at the time, but the authoritative copy is the one that wrote the
   *     row, and it is on the response.
   *
   * Returns null for a non-promote decision, and null if the server somehow reports a
   * promotion with no record — that would be a server defect, and inventing an id to paper
   * over it is the exact move this finding removed.
   */
  const triage = useCallback(async (
    id: number,
    decision: 'PROMOTE' | 'REJECT' | 'CLOSE',
    note?: string,
  ) => {
    const wire = await intakeApi.triage(id, { decision, ...(note ? { note } : {}) });
    const rec = fromWire(wire);
    setItems(prev => prev.map(r => (r.id === id ? rec : r)));

    if (decision !== 'PROMOTE') return null;
    const promoted = rec.promotedTo;
    if (!promoted?.recordId) return null;
    return { entityType: promoted.entityType, recordId: promoted.recordId };
  }, []);

  /**
   * Re-reads one record from `GET /api/v1/intake/{id}` and refreshes it in place.
   *
   * Called when a triager opens the decision modal, and the reason is not freshness for its own
   * sake: the list is a snapshot taken when the page loaded, and two triagers looking at the
   * same queue would otherwise both decide on it. Deciding from stale state is how one
   * submission gets promoted twice. Returns null and leaves the list untouched on failure —
   * the modal still opens on the row the triager clicked, which is the safe degradation.
   */
  const refreshOne = useCallback(async (id: number): Promise<IntakeRecord | null> => {
    try {
      const rec = fromWire(await intakeApi.get(id));
      setItems(prev => prev.map(r => (r.id === id ? rec : r)));
      return rec;
    } catch {
      return null;
    }
  }, []);

  return { items, loading, error, submit, triage, reload, refreshOne };
}

/**
 * The PUBLIC case-tracker's projection — deliberately smaller than an IntakeRecord.
 *
 * The server sends no submitter identity, no payload, no assignee and **no event history**
 * to an unauthenticated caller (SEC-FIN-001 — a reference code is a weak, enumerable
 * bearer). A page rendering this must show less rather than fill the gaps: the timeline the
 * old fabricated store displayed here had no server counterpart and cannot honestly be
 * reconstructed.
 */
export interface IntakePublicStatus {
  referenceCode: string;
  kind: IntakeKind;
  status: IntakeStatus;
  acknowledgedAt: string;
  slaDueAt?: string;
}

/**
 * Looks a submission up by reference code against `GET /api/v1/intake/track/{code}`, no token.
 *
 * A plain function, not a hook, and deliberately separate from useIntake: the tracker is an
 * unauthenticated page, so loading the triager inbox there would 401 on every visit and
 * bounce a member of the public to a login screen.
 */
export async function trackByReferenceCode(code: string): Promise<IntakePublicStatus | null> {
  const trimmed = code.trim().toUpperCase();
  if (!trimmed) return null;
  try {
    const w = await intakeApi.track(trimmed);
    return {
      referenceCode: w.referenceCode,
      kind: w.kind as IntakeKind,
      status: w.status as IntakeStatus,
      acknowledgedAt: w.acknowledgedAt,
      ...(w.slaDueAt ? { slaDueAt: w.slaDueAt } : {}),
    };
  } catch (e) {
    // 404 is the normal "no such code" answer and must read as not-found, not as an outage.
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}
