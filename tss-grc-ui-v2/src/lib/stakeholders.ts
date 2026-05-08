// Stakeholder + RACI register — the platform's "who" layer.
//
// Mirrors ServiceNow IRM "Stakeholder Management" + the RACI design
// factor from COBIT 2019 (Design Factor #11). Every workflow / process
// in the platform should resolve to a RACI assignment so the auditor
// always knows who is Responsible / Accountable / Consulted / Informed
// at each stage.
//
// Standards anchor: ISO 31000 §5.4.2 (interested parties), ISO 22301
// §4.2 + §7.4 (interested parties + communication during disruption),
// ISO 27001 A.5.5 / A.5.24 / A.6.6 (contact with authorities and
// special-interest groups), NIST CSF RS.CO-1..5, NIST SP 800-61 §3.2.7,
// COSO ERM Component 5 (Information, Communication and Reporting).

export type StakeholderType =
  | 'INTERNAL_BOARD'        // Board / committees
  | 'INTERNAL_EXEC'         // C-suite + senior management
  | 'INTERNAL_FUNCTION'     // Function leads (CISO, DPO, BCM, etc.)
  | 'INTERNAL_TEAM'         // Team-level contact (SOC, IRT, …)
  | 'EXTERNAL_REGULATOR'    // Saudi or international regulator
  | 'EXTERNAL_AUDITOR'      // External audit firm
  | 'EXTERNAL_VENDOR'       // Critical third-party
  | 'EXTERNAL_CUSTOMER'     // Customers / data subjects
  | 'EXTERNAL_PUBLIC';      // General public / press

export type RaciRole = 'R' | 'A' | 'C' | 'I';

export type CommChannel =
  | 'PORTAL'        // Regulator portal submission (e.g. NCA HEY, ZATCA FATOORA)
  | 'EMAIL'
  | 'PHONE'
  | 'SECURE_MSG'    // Secure messaging (encrypted email, secure portal)
  | 'FORMAL_LETTER'
  | 'IN_PERSON'     // Board / committee meeting
  | 'TEAMS_SLACK'   // Internal collaboration channel
  | 'PUBLIC_DISCLOSURE'
  // ── Sprint 6 dispatch-runtime channels ──
  | 'WHATSAPP'      // WhatsApp Business Cloud API — Saudi default
  | 'SMS'           // CITC-licensed SMS gateway
  | 'IN_APP';       // In-app push (web + mobile)

export interface Stakeholder {
  /** Stable code used by RACI + notification matrix joins. */
  code: string;
  type: StakeholderType;
  nameEn: string;
  nameAr: string;
  /** Role title (internal) or formal entity description (external). */
  titleEn?: string;
  titleAr?: string;
  /** Primary contact channel (email, portal URL, phone, …). */
  contactChannel?: string;
  /** Channel type — drives the icon shown in the comms log. */
  channel?: CommChannel;
  /** If EXTERNAL_REGULATOR — joins to AuthorityDto.code. */
  authorityCode?: string;
  /** Optional escalation parent — clicking "escalate" walks up this chain. */
  escalatesTo?: string;
}

// ── Stakeholder directory ──────────────────────────────────────────────────
export const STAKEHOLDERS: Stakeholder[] = [
  // ── Board + governance committees ────────────────────────────────────────
  { code: 'BOARD',         type: 'INTERNAL_BOARD',  nameEn: 'Board of Directors',           nameAr: 'مجلس الإدارة',
    titleEn: 'Highest governance body', titleAr: 'أعلى جهة حوكمة',
    channel: 'IN_PERSON' },
  { code: 'AUDIT_CTE',     type: 'INTERNAL_BOARD',  nameEn: 'Audit Committee',              nameAr: 'لجنة المراجعة',
    titleEn: 'Board sub-committee — independent assurance', titleAr: 'لجنة فرعية للمجلس — توكيد مستقل',
    channel: 'IN_PERSON', escalatesTo: 'BOARD' },
  { code: 'RISK_CTE',      type: 'INTERNAL_BOARD',  nameEn: 'Risk Committee',               nameAr: 'لجنة المخاطر',
    titleEn: 'Board sub-committee — enterprise risk', titleAr: 'لجنة فرعية للمجلس — مخاطر المؤسسة',
    channel: 'IN_PERSON', escalatesTo: 'BOARD' },
  { code: 'EXCO',          type: 'INTERNAL_EXEC',   nameEn: 'Executive Committee',          nameAr: 'اللجنة التنفيذية',
    titleEn: 'CEO + direct reports', titleAr: 'الرئيس التنفيذي ومرؤوسوه المباشرون',
    channel: 'IN_PERSON', escalatesTo: 'BOARD' },
  // ── Executive officers ───────────────────────────────────────────────────
  { code: 'CEO',           type: 'INTERNAL_EXEC',   nameEn: 'Chief Executive Officer',      nameAr: 'الرئيس التنفيذي',
    contactChannel: 'ceo@tss.sa', channel: 'EMAIL', escalatesTo: 'BOARD' },
  { code: 'CFO',           type: 'INTERNAL_EXEC',   nameEn: 'Chief Financial Officer',      nameAr: 'الرئيس المالي',
    contactChannel: 'cfo@tss.sa', channel: 'EMAIL', escalatesTo: 'CEO' },
  { code: 'COO',           type: 'INTERNAL_EXEC',   nameEn: 'Chief Operating Officer',      nameAr: 'الرئيس التشغيلي',
    contactChannel: 'coo@tss.sa', channel: 'EMAIL', escalatesTo: 'CEO' },
  { code: 'CIO',           type: 'INTERNAL_EXEC',   nameEn: 'Chief Information Officer',    nameAr: 'الرئيس المعلوماتي',
    contactChannel: 'cio@tss.sa', channel: 'EMAIL', escalatesTo: 'CEO' },
  { code: 'GENERAL_COUNSEL', type: 'INTERNAL_EXEC', nameEn: 'General Counsel',              nameAr: 'المستشار العام',
    titleEn: 'Head of legal', titleAr: 'رئيس الشؤون القانونية',
    contactChannel: 'gc@tss.sa', channel: 'EMAIL', escalatesTo: 'CEO' },
  // ── Function leads (second line + control owners) ────────────────────────
  { code: 'CISO',          type: 'INTERNAL_FUNCTION', nameEn: 'Chief Information Security Officer', nameAr: 'رئيس أمن المعلومات',
    titleEn: 'Cybersecurity programme owner', titleAr: 'مالك برنامج الأمن السيبراني',
    contactChannel: 'ciso@tss.sa', channel: 'EMAIL', escalatesTo: 'CIO' },
  { code: 'DPO',           type: 'INTERNAL_FUNCTION', nameEn: 'Data Protection Officer',          nameAr: 'مسؤول حماية البيانات',
    titleEn: 'PDPL accountability owner', titleAr: 'مسؤول الامتثال لنظام حماية البيانات',
    contactChannel: 'dpo@tss.sa', channel: 'EMAIL', escalatesTo: 'GENERAL_COUNSEL' },
  { code: 'CRO',           type: 'INTERNAL_FUNCTION', nameEn: 'Chief Risk Officer',                nameAr: 'رئيس المخاطر',
    contactChannel: 'cro@tss.sa', channel: 'EMAIL', escalatesTo: 'CEO' },
  { code: 'BCM_LEAD',      type: 'INTERNAL_FUNCTION', nameEn: 'Business Continuity Lead',          nameAr: 'مسؤول استمرارية الأعمال',
    contactChannel: 'bcm.team@tss.sa', channel: 'EMAIL', escalatesTo: 'COO' },
  { code: 'COMPLIANCE_LEAD', type: 'INTERNAL_FUNCTION', nameEn: 'Compliance Lead',                  nameAr: 'مسؤول الامتثال',
    contactChannel: 'compliance.lead@tss.sa', channel: 'EMAIL', escalatesTo: 'GENERAL_COUNSEL' },
  { code: 'TPRM_LEAD',     type: 'INTERNAL_FUNCTION', nameEn: 'Third-Party Risk Lead',             nameAr: 'مسؤول مخاطر الأطراف الثالثة',
    contactChannel: 'tprm.lead@tss.sa', channel: 'EMAIL', escalatesTo: 'CRO' },
  { code: 'AML_OFFICER',   type: 'INTERNAL_FUNCTION', nameEn: 'AML / Compliance Officer (MLRO)',   nameAr: 'مسؤول مكافحة غسل الأموال',
    titleEn: 'Money Laundering Reporting Officer', titleAr: 'الضابط المسؤول عن إبلاغ غسل الأموال',
    contactChannel: 'mlro@tss.sa', channel: 'EMAIL', escalatesTo: 'GENERAL_COUNSEL' },
  { code: 'INTERNAL_AUDIT',type: 'INTERNAL_FUNCTION', nameEn: 'Internal Audit',                     nameAr: 'المراجعة الداخلية',
    contactChannel: 'audit@tss.sa', channel: 'EMAIL', escalatesTo: 'AUDIT_CTE' },
  { code: 'HR_DIRECTOR',   type: 'INTERNAL_FUNCTION', nameEn: 'HR Director',                        nameAr: 'مدير الموارد البشرية',
    contactChannel: 'hr.director@tss.sa', channel: 'EMAIL', escalatesTo: 'COO' },
  { code: 'COMMS_DIRECTOR',type: 'INTERNAL_FUNCTION', nameEn: 'Communications Director',            nameAr: 'مدير الاتصال',
    titleEn: 'Owns external messaging + press', titleAr: 'مسؤول الرسائل الخارجية والعلاقات الإعلامية',
    contactChannel: 'comms.director@tss.sa', channel: 'EMAIL', escalatesTo: 'CEO' },
  // ── Team-level operational contacts ──────────────────────────────────────
  { code: 'SOC',           type: 'INTERNAL_TEAM', nameEn: 'Security Operations Centre',  nameAr: 'مركز عمليات الأمن',
    contactChannel: 'soc@tss.sa', channel: 'TEAMS_SLACK', escalatesTo: 'CISO' },
  { code: 'CSIRT',         type: 'INTERNAL_TEAM', nameEn: 'Cyber Incident Response Team',nameAr: 'فريق الاستجابة للحوادث',
    contactChannel: 'csirt@tss.sa', channel: 'PHONE',      escalatesTo: 'CISO' },
  { code: 'IAM_TEAM',      type: 'INTERNAL_TEAM', nameEn: 'IAM Team',                    nameAr: 'فريق إدارة الهويات',
    contactChannel: 'iam.team@tss.sa', channel: 'EMAIL',  escalatesTo: 'CISO' },
  { code: 'NETWORK_TEAM',  type: 'INTERNAL_TEAM', nameEn: 'Network Team',                nameAr: 'فريق الشبكات',
    contactChannel: 'network.team@tss.sa', channel: 'EMAIL', escalatesTo: 'CIO' },
  { code: 'SRE_TEAM',      type: 'INTERNAL_TEAM', nameEn: 'Site Reliability Engineering',nameAr: 'هندسة الموثوقية',
    contactChannel: 'sre.team@tss.sa', channel: 'TEAMS_SLACK', escalatesTo: 'CIO' },
  { code: 'LEGAL_TEAM',    type: 'INTERNAL_TEAM', nameEn: 'Legal Team',                  nameAr: 'الفريق القانوني',
    contactChannel: 'legal@tss.sa', channel: 'EMAIL', escalatesTo: 'GENERAL_COUNSEL' },
  // ── Saudi regulators ─────────────────────────────────────────────────────
  { code: 'NCA',           type: 'EXTERNAL_REGULATOR', nameEn: 'National Cybersecurity Authority',  nameAr: 'الهيئة الوطنية للأمن السيبراني',
    titleEn: 'HEY portal — incident notification within 24 hours', titleAr: 'بوابة "حصين" — إشعار الحوادث خلال 24 ساعة',
    contactChannel: 'https://hey.nca.gov.sa', channel: 'PORTAL', authorityCode: 'NCA' },
  { code: 'SAMA',          type: 'EXTERNAL_REGULATOR', nameEn: 'Saudi Central Bank',                nameAr: 'البنك المركزي السعودي',
    titleEn: 'CSF + AML reporting — incidents + SAR/STR', titleAr: 'إطار CSF + إبلاغ غسل الأموال',
    contactChannel: 'cyber.security@sama.gov.sa', channel: 'SECURE_MSG', authorityCode: 'SAMA' },
  { code: 'SDAIA',         type: 'EXTERNAL_REGULATOR', nameEn: 'Saudi Data & AI Authority',          nameAr: 'الهيئة السعودية للبيانات والذكاء الاصطناعي',
    titleEn: 'PDPL — 72h breach + 30-day DSR + cross-border permit', titleAr: 'نظام حماية البيانات — إشعار خلال 72 ساعة',
    contactChannel: 'https://sdaia.gov.sa/pdpl', channel: 'PORTAL', authorityCode: 'SDAIA' },
  { code: 'CITC',          type: 'EXTERNAL_REGULATOR', nameEn: 'Communications & IT Commission',    nameAr: 'هيئة الاتصالات وتقنية المعلومات',
    titleEn: 'Cloud + telecom outage notifications', titleAr: 'إشعارات السحابة وانقطاعات الاتصالات',
    contactChannel: 'https://citc.gov.sa', channel: 'PORTAL', authorityCode: 'CITC' },
  { code: 'NRC',           type: 'EXTERNAL_REGULATOR', nameEn: 'Saudi National Risk Center',         nameAr: 'المركز الوطني لإدارة المخاطر',
    titleEn: 'National risk register — semi-annual filings', titleAr: 'السجل الوطني للمخاطر — تقارير نصف سنوية',
    contactChannel: 'https://nrc.gov.sa', channel: 'PORTAL', authorityCode: 'NRC' },
  { code: 'CMA',           type: 'EXTERNAL_REGULATOR', nameEn: 'Capital Market Authority',           nameAr: 'هيئة السوق المالية',
    titleEn: 'Material event disclosures — 4 business hours (listed)', titleAr: 'إفصاحات الأحداث الجوهرية — خلال 4 ساعات عمل',
    contactChannel: 'https://cma.org.sa', channel: 'PORTAL', authorityCode: 'CMA' },
  { code: 'ZATCA',         type: 'EXTERNAL_REGULATOR', nameEn: 'Zakat, Tax & Customs Authority',     nameAr: 'هيئة الزكاة والضريبة والجمارك',
    titleEn: 'FATOORA e-invoicing transmission status', titleAr: 'حالة إرسال الفواتير الإلكترونية',
    contactChannel: 'https://zatca.gov.sa', channel: 'PORTAL', authorityCode: 'ZATCA' },
  { code: 'NAZAHA',        type: 'EXTERNAL_REGULATOR', nameEn: 'Oversight & Anti-Corruption Authority', nameAr: 'هيئة الرقابة ومكافحة الفساد',
    titleEn: 'Whistleblower + corruption reporting', titleAr: 'الإبلاغ عن الفساد + بلاغات حماية المُبلِغين',
    contactChannel: 'https://nazaha.gov.sa', channel: 'PORTAL', authorityCode: 'NAZAHA' },
  { code: 'NCEMA',         type: 'EXTERNAL_REGULATOR', nameEn: 'National Center for Emergency Management', nameAr: 'المركز الوطني لإدارة الكوارث والأزمات',
    titleEn: 'Crisis + disruption reporting', titleAr: 'تقارير الأزمات والاضطرابات',
    contactChannel: 'https://ncema.gov.sa', channel: 'PORTAL', authorityCode: 'NCEMA' },
  { code: 'HRSD',          type: 'EXTERNAL_REGULATOR', nameEn: 'Ministry of Human Resources',        nameAr: 'وزارة الموارد البشرية',
    titleEn: 'Labour + GOSI reporting', titleAr: 'تقارير العمل والتأمينات',
    contactChannel: 'https://hrsd.gov.sa', channel: 'PORTAL', authorityCode: 'HRSD' },
  { code: 'DGA',           type: 'EXTERNAL_REGULATOR', nameEn: 'Digital Government Authority',       nameAr: 'هيئة الحكومة الرقمية',
    titleEn: 'Government digital service compliance', titleAr: 'الامتثال للخدمات الحكومية الرقمية',
    contactChannel: 'https://dga.gov.sa', channel: 'PORTAL', authorityCode: 'DGA' },
  // ── External audit firms ─────────────────────────────────────────────────
  { code: 'EXT_AUDITOR',   type: 'EXTERNAL_AUDITOR', nameEn: 'External Audit Firm',                  nameAr: 'مكتب المراجعة الخارجي',
    titleEn: 'KPMG / PwC / Deloitte / EY — engagement-specific', titleAr: 'KPMG · PwC · Deloitte · EY',
    channel: 'FORMAL_LETTER' },
  // ── Customers + public ───────────────────────────────────────────────────
  { code: 'AFFECTED_DATA_SUBJECTS', type: 'EXTERNAL_CUSTOMER', nameEn: 'Affected Data Subjects',     nameAr: 'أصحاب البيانات المتأثرون',
    titleEn: 'PDPL Article 20 — direct notification when high risk', titleAr: 'المادة 20 من PDPL — إشعار مباشر',
    channel: 'EMAIL' },
  { code: 'PUBLIC_PRESS',  type: 'EXTERNAL_PUBLIC',  nameEn: 'Press / Public Disclosure',           nameAr: 'الإفصاح العام / الصحافة',
    channel: 'PUBLIC_DISCLOSURE' },
];

// ── RACI per process / workflow stage ──────────────────────────────────────
//
// Each entry resolves to "for this workflow stage in this module, here is the
// canonical RACI". Values are illustrative + standards-anchored; an
// implementer can refine them per organisation.

export interface ProcessRaci {
  /** Stable key joined to detail-page comms tabs. */
  processKey: string;
  module: 'RISK' | 'INCIDENT' | 'BREACH' | 'AUDIT' | 'COMPLIANCE'
        | 'BCM' | 'TPRM' | 'SCM' | 'CHANGE' | 'POLICY' | 'AML' | 'DPIA' | 'ETHICS' | 'EVIDENCE';
  titleEn: string;
  titleAr: string;
  /** Each row: {stakeholderCode, role}. Order: A first, then R, then C, then I. */
  raci: Array<{ stakeholderCode: string; role: RaciRole }>;
  /** Standards anchoring this RACI. */
  standardRefs: string[];
  /** Optional note explaining unusual choices. */
  noteEn?: string;
}

export const PROCESS_RACI: ProcessRaci[] = [
  // ── Cyber incident response ──────────────────────────────────────────────
  { processKey: 'CYBER_INCIDENT_DETECTION',
    module: 'INCIDENT',
    titleEn: 'Cyber incident — detection + triage',
    titleAr: 'حادثة سيبرانية — الكشف والفرز',
    standardRefs: ['NIST 800-61 §3.2', 'ISO 27035 §6', 'NCA ECC 7.2'],
    raci: [
      { stakeholderCode: 'CISO',      role: 'A' },
      { stakeholderCode: 'CSIRT',     role: 'R' },
      { stakeholderCode: 'SOC',       role: 'R' },
      { stakeholderCode: 'CIO',       role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },
  { processKey: 'CYBER_INCIDENT_CONTAINMENT',
    module: 'INCIDENT',
    titleEn: 'Cyber incident — containment + eradication',
    titleAr: 'حادثة سيبرانية — الاحتواء والاستئصال',
    standardRefs: ['NIST 800-61 §3.3', 'NCA ECC 7.3'],
    raci: [
      { stakeholderCode: 'CISO',      role: 'A' },
      { stakeholderCode: 'CSIRT',     role: 'R' },
      { stakeholderCode: 'NETWORK_TEAM', role: 'R' },
      { stakeholderCode: 'IAM_TEAM',  role: 'C' },
      { stakeholderCode: 'CIO',       role: 'C' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },
  { processKey: 'CYBER_INCIDENT_NCA_NOTIFY',
    module: 'INCIDENT',
    titleEn: 'Cyber incident — NCA HEY notification (24h)',
    titleAr: 'حادثة سيبرانية — إشعار "حصين" خلال 24 ساعة',
    standardRefs: ['NCA ECC 7.5.1', 'NCA Incident Notification Circular'],
    raci: [
      { stakeholderCode: 'CISO',      role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'NCA',       role: 'I' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
      { stakeholderCode: 'CEO',       role: 'I' },
      { stakeholderCode: 'BOARD',     role: 'I' },
    ] },
  { processKey: 'CYBER_INCIDENT_LESSONS',
    module: 'INCIDENT',
    titleEn: 'Cyber incident — lessons learned + closure',
    titleAr: 'حادثة سيبرانية — الدروس المستفادة والإغلاق',
    standardRefs: ['NIST 800-61 §3.4', 'ISO 27035 §9'],
    raci: [
      { stakeholderCode: 'CISO',      role: 'A' },
      { stakeholderCode: 'CSIRT',     role: 'R' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
    ] },
  // ── Privacy breach (PDPL) ───────────────────────────────────────────────
  { processKey: 'PDPL_BREACH_TRIAGE',
    module: 'BREACH',
    titleEn: 'PDPL breach — triage + impact assessment',
    titleAr: 'اختراق بيانات شخصية — الفرز وتقييم الأثر',
    standardRefs: ['PDPL Art.20', 'GDPR Art.33 (reference)'],
    raci: [
      { stakeholderCode: 'DPO',       role: 'A' },
      { stakeholderCode: 'CISO',      role: 'C' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
      { stakeholderCode: 'COMMS_DIRECTOR', role: 'C' },
      { stakeholderCode: 'CEO',       role: 'I' },
    ] },
  { processKey: 'PDPL_BREACH_SDAIA_NOTIFY',
    module: 'BREACH',
    titleEn: 'PDPL breach — SDAIA notification (72h)',
    titleAr: 'اختراق بيانات — إشعار هيئة البيانات خلال 72 ساعة',
    standardRefs: ['PDPL Art.20', 'PDPL Implementing Regulations §17'],
    raci: [
      { stakeholderCode: 'DPO',       role: 'A' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'R' },
      { stakeholderCode: 'SDAIA',     role: 'I' },
      { stakeholderCode: 'CEO',       role: 'I' },
      { stakeholderCode: 'BOARD',     role: 'I' },
    ] },
  { processKey: 'PDPL_BREACH_SUBJECT_NOTIFY',
    module: 'BREACH',
    titleEn: 'PDPL breach — affected data-subject notification',
    titleAr: 'اختراق بيانات — إشعار أصحاب البيانات المتأثرين',
    standardRefs: ['PDPL Art.20 (high-risk threshold)', 'GDPR Art.34 (reference)'],
    raci: [
      { stakeholderCode: 'DPO',       role: 'A' },
      { stakeholderCode: 'COMMS_DIRECTOR', role: 'R' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
      { stakeholderCode: 'AFFECTED_DATA_SUBJECTS', role: 'I' },
    ] },
  // ── Risk management ──────────────────────────────────────────────────────
  { processKey: 'RISK_IDENTIFICATION',
    module: 'RISK',
    titleEn: 'Risk identification + initial scoring',
    titleAr: 'تحديد المخاطر والتقييم الأولي',
    standardRefs: ['ISO 31000 §6.4', 'COSO ERM Component 4'],
    raci: [
      { stakeholderCode: 'CRO',       role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'C' },
      { stakeholderCode: 'RISK_CTE',  role: 'I' },
    ] },
  { processKey: 'RISK_ACCEPTANCE',
    module: 'RISK',
    titleEn: 'Risk acceptance — formal sign-off',
    titleAr: 'قبول المخاطرة — الاعتماد الرسمي',
    standardRefs: ['ISO 31000 §6.5.2', 'NCA ECC 1.5 (risk acceptance)'],
    noteEn: 'CRITICAL / HIGH residual risk requires Board approval, not just ExCo.',
    raci: [
      { stakeholderCode: 'BOARD',     role: 'A' },
      { stakeholderCode: 'CRO',       role: 'R' },
      { stakeholderCode: 'CEO',       role: 'C' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'C' },
      { stakeholderCode: 'RISK_CTE',  role: 'I' },
    ] },
  // ── Audit + CAPA ─────────────────────────────────────────────────────────
  { processKey: 'AUDIT_FINDING_RAISE',
    module: 'AUDIT',
    titleEn: 'Audit finding raised',
    titleAr: 'إثارة ملاحظة تدقيق',
    standardRefs: ['IIA IPPF 2400', 'ISO 19011 §6.4'],
    raci: [
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'C' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
    ] },
  { processKey: 'CAPA_AUDIT_SIGNOFF',
    module: 'AUDIT',
    titleEn: 'CAPA — audit sign-off + closure',
    titleAr: 'الإجراء التصحيحي — اعتماد المراجعة والإغلاق',
    standardRefs: ['IIA IPPF 2500', 'ISO 9001 §10.2'],
    raci: [
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
      { stakeholderCode: 'EXT_AUDITOR', role: 'I' },
    ] },
  // ── Business continuity invocation ───────────────────────────────────────
  { processKey: 'BCP_INVOCATION',
    module: 'BCM',
    titleEn: 'Business continuity plan invocation',
    titleAr: 'تفعيل خطة استمرارية الأعمال',
    standardRefs: ['ISO 22301 §8.4.4', 'NCEMA framework'],
    raci: [
      { stakeholderCode: 'CEO',       role: 'A' },
      { stakeholderCode: 'BCM_LEAD',  role: 'R' },
      { stakeholderCode: 'COO',       role: 'C' },
      { stakeholderCode: 'COMMS_DIRECTOR', role: 'C' },
      { stakeholderCode: 'NCEMA',     role: 'I' },
      { stakeholderCode: 'BOARD',     role: 'I' },
    ] },
  // ── Third-party risk ─────────────────────────────────────────────────────
  { processKey: 'TPRM_CRITICAL_VENDOR_OFFBOARDING',
    module: 'TPRM',
    titleEn: 'Critical vendor off-boarding',
    titleAr: 'إنهاء التعاقد مع مورد حرج',
    standardRefs: ['ISO 27036', 'NIST SP 800-161 §3.3.5', 'OCC 2013-29'],
    raci: [
      { stakeholderCode: 'COO',       role: 'A' },
      { stakeholderCode: 'TPRM_LEAD', role: 'R' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
      { stakeholderCode: 'CISO',      role: 'C' },
      { stakeholderCode: 'BCM_LEAD',  role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },
  // ── AML SAR / STR ────────────────────────────────────────────────────────
  { processKey: 'AML_SAR_FILING',
    module: 'AML',
    titleEn: 'Suspicious Activity Report — SAFIU filing',
    titleAr: 'بلاغ نشاط مشبوه — إرسال وحدة التحريات المالية',
    standardRefs: ['SAMA AML §6', 'FATF Rec 20 + 21', 'AML Law Art. 15'],
    raci: [
      { stakeholderCode: 'AML_OFFICER', role: 'A' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
      { stakeholderCode: 'SAMA',      role: 'I' },
      { stakeholderCode: 'CEO',       role: 'I' },
    ] },
  // ── Ethics / whistleblower ───────────────────────────────────────────────
  { processKey: 'WHISTLEBLOWER_INTAKE',
    module: 'ETHICS',
    titleEn: 'Whistleblower disclosure intake',
    titleAr: 'استقبال بلاغات حماية المُبلغين',
    standardRefs: ['ISO 37001', 'Nazaha protected disclosures'],
    raci: [
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'A' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'R' },
      { stakeholderCode: 'HR_DIRECTOR', role: 'C' },
      { stakeholderCode: 'NAZAHA',    role: 'I' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
    ] },
  // ── Material event (listed entities) ─────────────────────────────────────
  { processKey: 'MATERIAL_EVENT_DISCLOSURE',
    module: 'COMPLIANCE',
    titleEn: 'Material event disclosure (listed entity)',
    titleAr: 'إفصاح عن حدث جوهري (شركة مُدرجة)',
    standardRefs: ['CMA Listing Rules §53', 'CMA Resolution 4-78-2018'],
    noteEn: '4 business hours from event becoming material.',
    raci: [
      { stakeholderCode: 'CEO',       role: 'A' },
      { stakeholderCode: 'CFO',       role: 'R' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'R' },
      { stakeholderCode: 'COMMS_DIRECTOR', role: 'C' },
      { stakeholderCode: 'CMA',       role: 'I' },
      { stakeholderCode: 'BOARD',     role: 'I' },
    ] },
  // ── Policy publication ───────────────────────────────────────────────────
  { processKey: 'POLICY_PUBLICATION',
    module: 'POLICY',
    titleEn: 'Policy publication + organisation-wide rollout',
    titleAr: 'نشر السياسة والإعلان عنها على مستوى المؤسسة',
    standardRefs: ['ISO 27001 A.5.1', 'COBIT APO01'],
    raci: [
      { stakeholderCode: 'EXCO',      role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
      { stakeholderCode: 'COMMS_DIRECTOR', role: 'C' },
      { stakeholderCode: 'HR_DIRECTOR', role: 'I' },
    ] },
  // ── DPIA approval ────────────────────────────────────────────────────────
  { processKey: 'DPIA_APPROVAL',
    module: 'DPIA',
    titleEn: 'DPIA approval (high-risk processing)',
    titleAr: 'اعتماد تقييم أثر حماية البيانات',
    standardRefs: ['PDPL Art.26', 'GDPR Art.35 (reference)'],
    raci: [
      { stakeholderCode: 'DPO',       role: 'A' },
      { stakeholderCode: 'CISO',      role: 'C' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
      { stakeholderCode: 'SDAIA',     role: 'C', /* prior consultation if residual HIGH */ },
    ] },

  // ─── DD-27 expansion: second-tier processes per module ──────────────────────

  // Risk — broader lifecycle
  { processKey: 'RISK_TREATMENT_DECISION', module: 'RISK',
    titleEn: 'Risk treatment selection (mitigate / transfer / accept / avoid)',
    titleAr: 'اختيار معالجة المخاطر (تخفيف / نقل / قبول / تجنّب)',
    standardRefs: ['ISO 31000 §6.5.2', 'COSO ERM Component 3'],
    raci: [
      { stakeholderCode: 'CRO',       role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'CFO',       role: 'C' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'C' },
      { stakeholderCode: 'RISK_CTE',  role: 'I' },
    ] },
  { processKey: 'RISK_PERIODIC_REVIEW', module: 'RISK',
    titleEn: 'Risk register periodic review',
    titleAr: 'المراجعة الدورية لسجل المخاطر',
    standardRefs: ['ISO 31000 §6.6', 'NCA ECC 1.5'],
    raci: [
      { stakeholderCode: 'CRO',       role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'C' },
      { stakeholderCode: 'RISK_CTE',  role: 'I' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
    ] },
  { processKey: 'RISK_TRANSFER_INSURANCE', module: 'RISK',
    titleEn: 'Risk transfer — insurance + contractual',
    titleAr: 'نقل المخاطر — التأمين والتعاقد',
    standardRefs: ['ISO 31000 §6.5.2.b', 'COSO ERM (risk-sharing)'],
    raci: [
      { stakeholderCode: 'CFO',       role: 'A' },
      { stakeholderCode: 'CRO',       role: 'R' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
      { stakeholderCode: 'CEO',       role: 'I' },
    ] },
  { processKey: 'RISK_KRI_BREACH', module: 'RISK',
    titleEn: 'KRI breach response (red-band)',
    titleAr: 'الاستجابة لتجاوز مؤشر الخطر (نطاق أحمر)',
    standardRefs: ['ISO 31000 §6.6 (monitoring)', 'COSO ERM Component 4'],
    raci: [
      { stakeholderCode: 'CRO',       role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'RISK_CTE',  role: 'I' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },
  { processKey: 'RISK_CLOSURE', module: 'RISK',
    titleEn: 'Risk closure',
    titleAr: 'إغلاق المخاطرة',
    standardRefs: ['ISO 31000 §6.6 + 6.7'],
    raci: [
      { stakeholderCode: 'CRO',       role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'C' },
      { stakeholderCode: 'RISK_CTE',  role: 'I' },
    ] },

  // Cyber incident — eradication, recovery, vuln, threat-intel
  { processKey: 'CYBER_INCIDENT_ERADICATION', module: 'INCIDENT',
    titleEn: 'Cyber incident — eradication',
    titleAr: 'حادثة سيبرانية — الاستئصال',
    standardRefs: ['NIST 800-61 §3.3.4', 'ISO 27035 §7'],
    raci: [
      { stakeholderCode: 'CISO',      role: 'A' },
      { stakeholderCode: 'CSIRT',     role: 'R' },
      { stakeholderCode: 'NETWORK_TEAM', role: 'C' },
      { stakeholderCode: 'IAM_TEAM',  role: 'C' },
    ] },
  { processKey: 'CYBER_INCIDENT_RECOVERY', module: 'INCIDENT',
    titleEn: 'Cyber incident — recovery + service restoration',
    titleAr: 'حادثة سيبرانية — التعافي واستعادة الخدمة',
    standardRefs: ['NIST 800-61 §3.3.5', 'NCA ECC 7.4'],
    raci: [
      { stakeholderCode: 'CIO',       role: 'A' },
      { stakeholderCode: 'SRE_TEAM',  role: 'R' },
      { stakeholderCode: 'CSIRT',     role: 'C' },
      { stakeholderCode: 'COMMS_DIRECTOR', role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },
  { processKey: 'CYBER_VULN_REMEDIATION', module: 'INCIDENT',
    titleEn: 'Vulnerability remediation (CVE / scan finding)',
    titleAr: 'معالجة الثغرات (سي في إي / نتائج الفحص)',
    standardRefs: ['NCA ECC 5.2', 'NIST CSF DE.CM-8 + RS.MI'],
    raci: [
      { stakeholderCode: 'CISO',      role: 'A' },
      { stakeholderCode: 'SOC',       role: 'R' },
      { stakeholderCode: 'SRE_TEAM',  role: 'C' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'I' },
    ] },
  { processKey: 'CYBER_THREAT_INTEL_SHARING', module: 'INCIDENT',
    titleEn: 'Threat-intel sharing (NCA Hassan, sectoral ISACs)',
    titleAr: 'مشاركة معلومات التهديدات (حسّان، مراكز ISAC القطاعية)',
    standardRefs: ['NCA Hassan platform', 'ISO 27001 A.5.6'],
    raci: [
      { stakeholderCode: 'CISO',      role: 'A' },
      { stakeholderCode: 'SOC',       role: 'R' },
      { stakeholderCode: 'NCA',       role: 'I' },
    ] },

  // Privacy breach — cross-border + joint controller + annual report
  { processKey: 'BREACH_CROSS_BORDER_NOTIFY', module: 'BREACH',
    titleEn: 'Cross-border processor notification',
    titleAr: 'إشعار مُعالج البيانات في الخارج',
    standardRefs: ['PDPL Art.29', 'PDPL IR §17'],
    raci: [
      { stakeholderCode: 'DPO',       role: 'A' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'R' },
      { stakeholderCode: 'TPRM_LEAD', role: 'C' },
    ] },
  { processKey: 'BREACH_JOINT_CONTROLLER', module: 'BREACH',
    titleEn: 'Joint-controller alignment + coordinated notification',
    titleAr: 'التنسيق مع المتحكم المشترك وإشعار موحَّد',
    standardRefs: ['PDPL Art.7 (joint controller)', 'GDPR Art.26 (reference)'],
    raci: [
      { stakeholderCode: 'DPO',       role: 'A' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'R' },
      { stakeholderCode: 'COMMS_DIRECTOR', role: 'C' },
    ] },
  { processKey: 'PDPL_ANNUAL_REPORT', module: 'BREACH',
    titleEn: 'Annual PDPL compliance report to SDAIA',
    titleAr: 'التقرير السنوي للامتثال لنظام حماية البيانات',
    standardRefs: ['PDPL IR §29', 'SDAIA Annual Reporting Circular'],
    raci: [
      { stakeholderCode: 'DPO',       role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'CEO',       role: 'I' },
      { stakeholderCode: 'SDAIA',     role: 'I' },
      { stakeholderCode: 'BOARD',     role: 'I' },
    ] },

  // BCM — broader lifecycle
  { processKey: 'BIA_REVIEW', module: 'BCM',
    titleEn: 'Business impact analysis review + sign-off',
    titleAr: 'مراجعة واعتماد تحليل أثر الأعمال',
    standardRefs: ['ISO 22301 §8.2.2', 'ISO 22317'],
    raci: [
      { stakeholderCode: 'BCM_LEAD',  role: 'A' },
      { stakeholderCode: 'COO',       role: 'C' },
      { stakeholderCode: 'CRO',       role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },
  { processKey: 'BCM_EXERCISE', module: 'BCM',
    titleEn: 'BCP exercise execution + outcome reporting',
    titleAr: 'تنفيذ تمرين خطة الاستمرارية وإبلاغ النتائج',
    standardRefs: ['ISO 22301 §8.5', 'NCEMA exercise guidance'],
    raci: [
      { stakeholderCode: 'BCM_LEAD',  role: 'A' },
      { stakeholderCode: 'CSIRT',     role: 'R' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'C' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
    ] },
  { processKey: 'BCM_RTO_RPO_BREACH', module: 'BCM',
    titleEn: 'RTO / RPO breach — escalation',
    titleAr: 'تجاوز مستهدفات الاستعادة (RTO/RPO) — تصعيد',
    standardRefs: ['ISO 22301 §8.4.4'],
    raci: [
      { stakeholderCode: 'COO',       role: 'A' },
      { stakeholderCode: 'BCM_LEAD',  role: 'R' },
      { stakeholderCode: 'SRE_TEAM',  role: 'R' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },
  { processKey: 'BCM_PLAN_APPROVAL', module: 'BCM',
    titleEn: 'BCP plan-version approval',
    titleAr: 'اعتماد إصدار خطة استمرارية الأعمال',
    standardRefs: ['ISO 22301 §8.4'],
    raci: [
      { stakeholderCode: 'BOARD',     role: 'A' },
      { stakeholderCode: 'BCM_LEAD',  role: 'R' },
      { stakeholderCode: 'CEO',       role: 'C' },
    ] },

  // TPRM — onboarding, assessment, contract, sub-processor, vendor IR
  { processKey: 'TPRM_ONBOARDING_GATING', module: 'TPRM',
    titleEn: 'Vendor onboarding gating (security + privacy + AML)',
    titleAr: 'بوابة تأهيل المورد (أمن + خصوصية + غسل أموال)',
    standardRefs: ['ISO 27036 §6', 'NIST 800-161 §3.3.1'],
    raci: [
      { stakeholderCode: 'TPRM_LEAD', role: 'A' },
      { stakeholderCode: 'CISO',      role: 'C' },
      { stakeholderCode: 'DPO',       role: 'C' },
      { stakeholderCode: 'AML_OFFICER', role: 'C' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
    ] },
  { processKey: 'TPRM_RISK_ASSESSMENT', module: 'TPRM',
    titleEn: 'Periodic vendor risk assessment review',
    titleAr: 'المراجعة الدورية لتقييم مخاطر الموردين',
    standardRefs: ['ISO 27036 §7', 'OCC 2013-29'],
    raci: [
      { stakeholderCode: 'TPRM_LEAD', role: 'A' },
      { stakeholderCode: 'CISO',      role: 'C' },
      { stakeholderCode: 'CRO',       role: 'I' },
    ] },
  { processKey: 'TPRM_CONTRACT_RENEWAL', module: 'TPRM',
    titleEn: 'Critical-vendor contract renewal sign-off',
    titleAr: 'اعتماد تجديد عقد مورد حرج',
    standardRefs: ['NIST 800-161 §3.3.4'],
    raci: [
      { stakeholderCode: 'COO',       role: 'A' },
      { stakeholderCode: 'TPRM_LEAD', role: 'R' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'R' },
      { stakeholderCode: 'CFO',       role: 'C' },
    ] },
  { processKey: 'TPRM_SUB_PROCESSOR_CHANGE', module: 'TPRM',
    titleEn: 'Sub-processor change notification',
    titleAr: 'إشعار تغيير المعالج الفرعي',
    standardRefs: ['PDPL Art.20 + 27', 'GDPR Art.28(2) (reference)'],
    raci: [
      { stakeholderCode: 'DPO',       role: 'A' },
      { stakeholderCode: 'TPRM_LEAD', role: 'R' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
    ] },
  { processKey: 'TPRM_VENDOR_INCIDENT', module: 'TPRM',
    titleEn: 'Vendor-side security incident — coordinated response',
    titleAr: 'حادثة أمنية لدى المورد — استجابة منسَّقة',
    standardRefs: ['ISO 27036 §8', 'NIST 800-161 §3.4'],
    raci: [
      { stakeholderCode: 'TPRM_LEAD', role: 'A' },
      { stakeholderCode: 'CISO',      role: 'R' },
      { stakeholderCode: 'CSIRT',     role: 'C' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
    ] },

  // Audit — engagement lifecycle
  { processKey: 'AUDIT_ENGAGEMENT_PLANNING', module: 'AUDIT',
    titleEn: 'Audit engagement planning + scoping',
    titleAr: 'تخطيط ارتباط المراجعة وتحديد النطاق',
    standardRefs: ['IIA IPPF 2200', 'ISO 19011 §6.2'],
    raci: [
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'A' },
      { stakeholderCode: 'EXT_AUDITOR', role: 'C' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },
  { processKey: 'AUDIT_FIELDWORK_STATUS', module: 'AUDIT',
    titleEn: 'Audit fieldwork — status reporting',
    titleAr: 'العمل الميداني للمراجعة — التقارير الدورية',
    standardRefs: ['IIA IPPF 2300', 'ISO 19011 §6.4'],
    raci: [
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'C' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
    ] },
  { processKey: 'AUDIT_DRAFT_REPORT_REVIEW', module: 'AUDIT',
    titleEn: 'Draft audit report — management response',
    titleAr: 'مسوَّدة تقرير المراجعة — رد الإدارة',
    standardRefs: ['IIA IPPF 2410 + 2420'],
    raci: [
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'A' },
      { stakeholderCode: 'EXCO',      role: 'R' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
    ] },
  { processKey: 'AUDIT_FOLLOWUP_TESTING', module: 'AUDIT',
    titleEn: 'Audit follow-up testing of remediation',
    titleAr: 'متابعة اختبار المعالجة',
    standardRefs: ['IIA IPPF 2500', 'ISO 9001 §10.2'],
    raci: [
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
    ] },

  // Compliance — regulatory change + control failure
  { processKey: 'REG_CHANGE_IMPACT', module: 'COMPLIANCE',
    titleEn: 'Regulatory change — impact assessment',
    titleAr: 'تقييم أثر التغيير التنظيمي',
    standardRefs: ['COBIT MEA03', 'ISO 27001 §4.2'],
    raci: [
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'A' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'R' },
      { stakeholderCode: 'CISO',      role: 'C' },
      { stakeholderCode: 'DPO',       role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },
  { processKey: 'CONTROL_FAILURE', module: 'COMPLIANCE',
    titleEn: 'Significant control failure — escalation',
    titleAr: 'إخفاق ضابط جوهري — تصعيد',
    standardRefs: ['COSO ERM Component 4', 'NCA ECC 1.5'],
    raci: [
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'A' },
      { stakeholderCode: 'CRO',       role: 'C' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'C' },
      { stakeholderCode: 'RISK_CTE',  role: 'I' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
    ] },
  { processKey: 'FRAMEWORK_TRANSITION', module: 'COMPLIANCE',
    titleEn: 'Framework version transition (e.g. NCA ECC v1 → v2)',
    titleAr: 'الانتقال بين إصدارات الإطار (مثلاً ECC الإصدار 1 → 2)',
    standardRefs: ['NCA Circulars + Implementing Regs'],
    raci: [
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'A' },
      { stakeholderCode: 'CISO',      role: 'R' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },

  // Policy — full lifecycle
  { processKey: 'POLICY_DRAFTING', module: 'POLICY',
    titleEn: 'Policy drafting + stakeholder review',
    titleAr: 'صياغة السياسة ومراجعة أصحاب المصلحة',
    standardRefs: ['ISO 27001 A.5.1.1', 'COBIT APO01.04'],
    raci: [
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'A' },
      { stakeholderCode: 'LEGAL_TEAM', role: 'C' },
      { stakeholderCode: 'CISO',      role: 'C' },
      { stakeholderCode: 'DPO',       role: 'C' },
    ] },
  { processKey: 'POLICY_LEGAL_REVIEW', module: 'POLICY',
    titleEn: 'Policy — legal sign-off',
    titleAr: 'السياسة — اعتماد قانوني',
    standardRefs: ['ISO 27001 A.5.1.2'],
    raci: [
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'A' },
      { stakeholderCode: 'LEGAL_TEAM', role: 'R' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'I' },
    ] },
  { processKey: 'POLICY_RETIREMENT', module: 'POLICY',
    titleEn: 'Policy retirement / sunset',
    titleAr: 'إنهاء العمل بالسياسة',
    standardRefs: ['ISO 9001 §7.5.3', 'COBIT APO01.04'],
    raci: [
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'A' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
      { stakeholderCode: 'COMMS_DIRECTOR', role: 'C' },
      { stakeholderCode: 'HR_DIRECTOR', role: 'I' },
    ] },
  { processKey: 'POLICY_EXCEPTION', module: 'POLICY',
    titleEn: 'Policy exception / waiver approval',
    titleAr: 'اعتماد استثناء من السياسة',
    standardRefs: ['ISO 27001 A.5.1.2', 'NCA ECC 1.5'],
    raci: [
      { stakeholderCode: 'CRO',       role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'CISO',      role: 'C' },
      { stakeholderCode: 'RISK_CTE',  role: 'I' },
    ] },

  // DPIA — initiation, review, prior consultation
  { processKey: 'DPIA_INITIATION', module: 'DPIA',
    titleEn: 'DPIA initiation (high-risk processing trigger)',
    titleAr: 'بدء تقييم أثر حماية البيانات',
    standardRefs: ['PDPL Art.26', 'PDPL IR §22'],
    raci: [
      { stakeholderCode: 'DPO',       role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'C' },
      { stakeholderCode: 'CISO',      role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },
  { processKey: 'DPIA_PERIODIC_REVIEW', module: 'DPIA',
    titleEn: 'DPIA periodic review',
    titleAr: 'المراجعة الدورية لتقييم أثر حماية البيانات',
    standardRefs: ['PDPL Art.26', 'GDPR Art.35(11) (reference)'],
    raci: [
      { stakeholderCode: 'DPO',       role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },
  { processKey: 'DPIA_PRIOR_CONSULTATION', module: 'DPIA',
    titleEn: 'SDAIA prior consultation (residual HIGH risk)',
    titleAr: 'الاستشارة المسبقة مع هيئة البيانات (مخاطر متبقية عالية)',
    standardRefs: ['PDPL Art.26 IR §22', 'GDPR Art.36 (reference)'],
    raci: [
      { stakeholderCode: 'DPO',       role: 'A' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'R' },
      { stakeholderCode: 'SDAIA',     role: 'I' },
      { stakeholderCode: 'CEO',       role: 'I' },
    ] },

  // AML — KYC, EDD, sanctions, PEP, TM tuning
  { processKey: 'AML_KYC_REVIEW', module: 'AML',
    titleEn: 'KYC / CDD review',
    titleAr: 'مراجعة العناية الواجبة للعميل',
    standardRefs: ['SAMA AML §3', 'FATF Rec 10'],
    raci: [
      { stakeholderCode: 'AML_OFFICER', role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
    ] },
  { processKey: 'AML_EDD_ESCALATION', module: 'AML',
    titleEn: 'Enhanced Due Diligence escalation',
    titleAr: 'تصعيد العناية الواجبة المعزّزة',
    standardRefs: ['SAMA AML §4', 'FATF Rec 12 + 13'],
    raci: [
      { stakeholderCode: 'AML_OFFICER', role: 'A' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
      { stakeholderCode: 'CEO',       role: 'I' },
    ] },
  { processKey: 'AML_SANCTIONS_MATCH', module: 'AML',
    titleEn: 'Sanctions / PEP match handling',
    titleAr: 'التعامل مع تطابق العقوبات أو الأشخاص ذوي النفوذ',
    standardRefs: ['SAMA AML §5', 'FATF Rec 6 + 7'],
    raci: [
      { stakeholderCode: 'AML_OFFICER', role: 'A' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'R' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'C' },
      { stakeholderCode: 'SAMA',      role: 'I' },
    ] },
  { processKey: 'AML_TM_TUNING', module: 'AML',
    titleEn: 'Transaction-monitoring tuning + threshold review',
    titleAr: 'ضبط مراقبة المعاملات ومراجعة العتبات',
    standardRefs: ['SAMA AML §6.3', 'FATF Rec 20'],
    raci: [
      { stakeholderCode: 'AML_OFFICER', role: 'A' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'C' },
    ] },

  // Ethics — investigation, disciplinary, anti-retaliation, CoI, gifts
  { processKey: 'ETHICS_INVESTIGATION', module: 'ETHICS',
    titleEn: 'Ethics investigation',
    titleAr: 'تحقيق أخلاقي',
    standardRefs: ['ISO 37001 §8.10', 'Nazaha protected disclosures'],
    raci: [
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'A' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'R' },
      { stakeholderCode: 'HR_DIRECTOR', role: 'C' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
    ] },
  { processKey: 'ETHICS_DISCIPLINARY', module: 'ETHICS',
    titleEn: 'Disciplinary action — sanctions / termination',
    titleAr: 'إجراء تأديبي — عقوبات أو إنهاء خدمة',
    standardRefs: ['HR policy + Saudi Labor Law', 'ISO 37001 §8.11'],
    raci: [
      { stakeholderCode: 'HR_DIRECTOR', role: 'A' },
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'C' },
      { stakeholderCode: 'CEO',       role: 'I' },
    ] },
  { processKey: 'ETHICS_CONFLICT_OF_INTEREST', module: 'ETHICS',
    titleEn: 'Conflict-of-interest disclosure review',
    titleAr: 'مراجعة إفصاحات تضارب المصالح',
    standardRefs: ['ISO 37001 §7.2', 'Nazaha integrity framework'],
    raci: [
      { stakeholderCode: 'GENERAL_COUNSEL', role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
    ] },
  { processKey: 'ETHICS_GIFT_HOSPITALITY', module: 'ETHICS',
    titleEn: 'Gifts + hospitality disclosure review',
    titleAr: 'مراجعة إفصاحات الهدايا والضيافة',
    standardRefs: ['ISO 37001 §7.4', 'CoC + Nazaha guidance'],
    raci: [
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'A' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'C' },
      { stakeholderCode: 'AUDIT_CTE', role: 'I' },
    ] },

  // Change management — standard / normal CAB / emergency / PIR
  { processKey: 'CHANGE_STANDARD', module: 'CHANGE',
    titleEn: 'Standard pre-approved change — execution',
    titleAr: 'تغيير قياسي معتمد مسبقاً — تنفيذ',
    standardRefs: ['ITIL 4 — Change Enablement', 'ISO 20000-1 §9.1'],
    raci: [
      { stakeholderCode: 'CIO',       role: 'A' },
      { stakeholderCode: 'SRE_TEAM',  role: 'R' },
    ] },
  { processKey: 'CHANGE_NORMAL_CAB', module: 'CHANGE',
    titleEn: 'Normal change — Change Advisory Board (CAB) approval',
    titleAr: 'تغيير طبيعي — اعتماد لجنة استشارة التغيير',
    standardRefs: ['ITIL 4', 'ISO 20000-1 §9.1'],
    raci: [
      { stakeholderCode: 'CIO',       role: 'A' },
      { stakeholderCode: 'SRE_TEAM',  role: 'R' },
      { stakeholderCode: 'CISO',      role: 'C' },
      { stakeholderCode: 'BCM_LEAD',  role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },
  { processKey: 'CHANGE_EMERGENCY', module: 'CHANGE',
    titleEn: 'Emergency change — expedited approval',
    titleAr: 'تغيير طارئ — اعتماد سريع',
    standardRefs: ['ITIL 4 — emergency change'],
    raci: [
      { stakeholderCode: 'CIO',       role: 'A' },
      { stakeholderCode: 'SRE_TEAM',  role: 'R' },
      { stakeholderCode: 'CISO',      role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
      { stakeholderCode: 'BOARD',     role: 'I' },
    ] },
  { processKey: 'CHANGE_PIR', module: 'CHANGE',
    titleEn: 'Post-implementation review (PIR)',
    titleAr: 'المراجعة بعد التنفيذ',
    standardRefs: ['ITIL 4', 'ISO 20000-1 §9.1.3'],
    raci: [
      { stakeholderCode: 'CIO',       role: 'A' },
      { stakeholderCode: 'SRE_TEAM',  role: 'R' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'I' },
    ] },

  // SCM — tier mapping + supplier disruption
  { processKey: 'SCM_TIER_MAPPING', module: 'SCM',
    titleEn: 'Supplier tier mapping (Tier 1 / 2 / 3) — NRC submission',
    titleAr: 'تصنيف الموردين حسب المستوى — إرسال السجل الوطني',
    standardRefs: ['NRC tier-mapping requirement', 'NIST 800-161 §3.1'],
    raci: [
      { stakeholderCode: 'TPRM_LEAD', role: 'A' },
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'R' },
      { stakeholderCode: 'NRC',       role: 'I' },
    ] },
  { processKey: 'SCM_SUPPLIER_DISRUPTION', module: 'SCM',
    titleEn: 'Supplier disruption — operational response',
    titleAr: 'اضطراب لدى المورد — استجابة تشغيلية',
    standardRefs: ['ISO 22301 §8.4.4 (interactions)', 'NIST 800-161 §3.4'],
    raci: [
      { stakeholderCode: 'COO',       role: 'A' },
      { stakeholderCode: 'TPRM_LEAD', role: 'R' },
      { stakeholderCode: 'BCM_LEAD',  role: 'C' },
      { stakeholderCode: 'COMMS_DIRECTOR', role: 'C' },
      { stakeholderCode: 'EXCO',      role: 'I' },
    ] },

  // Evidence — expiry + connector failure (operational care)
  { processKey: 'EVIDENCE_EXPIRY', module: 'EVIDENCE',
    titleEn: 'Evidence expiry — owner re-collection',
    titleAr: 'انتهاء صلاحية الدليل — إعادة جمع من المالك',
    standardRefs: ['ISO 27001 A.7.5', 'NCA ECC documentation'],
    raci: [
      { stakeholderCode: 'COMPLIANCE_LEAD', role: 'A' },
      { stakeholderCode: 'INTERNAL_AUDIT', role: 'C' },
    ] },
];

export const STATUS_TONE_RACI: Record<RaciRole, 'danger' | 'primary' | 'info' | 'neutral'> = {
  A: 'danger',    // Accountable — single point of accountability
  R: 'primary',   // Responsible — does the work
  C: 'info',      // Consulted — two-way input
  I: 'neutral',   // Informed — one-way update
};

export const RACI_LABEL_LONG: Record<RaciRole, { en: string; ar: string }> = {
  A: { en: 'Accountable', ar: 'مسؤول التزاماً' },
  R: { en: 'Responsible', ar: 'منفِّذ' },
  C: { en: 'Consulted',   ar: 'مُستشار' },
  I: { en: 'Informed',    ar: 'مُبلَّغ' },
};

// ── Helpers ────────────────────────────────────────────────────────────────
export function findStakeholder(code: string): Stakeholder | undefined {
  return STAKEHOLDERS.find(s => s.code === code);
}

export function findProcessRaci(processKey: string): ProcessRaci | undefined {
  return PROCESS_RACI.find(p => p.processKey === processKey);
}

/** All RACI rows for a given module — used by the comms tab on detail pages. */
export function raciForModule(module: ProcessRaci['module']): ProcessRaci[] {
  return PROCESS_RACI.filter(p => p.module === module);
}
