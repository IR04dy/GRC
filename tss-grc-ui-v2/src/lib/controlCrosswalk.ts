// Control inheritance / crosswalks — Sprint 6 follow-on (B5).
//
// Every Saudi bank's compliance team maintains an Excel sheet titled
// "frameworks crosswalk" that says: when we test "MFA on privileged
// access", that one control satisfies NCA ECC 2-3-1, SAMA CSF 3.1.5,
// SOC 2 CC6.1, ISO 27001 A.5.17, PCI DSS 8.4, and GDPR Art. 32. The
// spreadsheet is brittle, never current, and never shared.
//
// This module makes that crosswalk a first-class platform object:
//   • A "master control" is the internal canonical control we test once
//   • It has a list of `frameworkRefs` — clauses it satisfies in each
//     external framework
//   • Status, evidence, and remediation flow up from the master through
//     the references — change once, satisfy 6 frameworks
//
// Demo footprint: 14 master controls covering the most-cited topics
// (MFA, encryption, vulnerability mgmt, incident response, BCP, log
// retention, vendor risk, …) with ~5 framework references each.

export type Framework =
  | 'NCA_ECC'
  | 'ISO_27001'
  | 'SOC_2'
  | 'SAMA_CSF'
  | 'PDPL'
  | 'PCI_DSS'
  | 'NIST_CSF'
  | 'GDPR';

export const FRAMEWORK_LABEL: Record<Framework, { en: string; ar: string }> = {
  NCA_ECC:   { en: 'NCA ECC-1',         ar: 'NCA ECC-1' },
  ISO_27001: { en: 'ISO 27001:2022',     ar: 'ISO 27001:2022' },
  SOC_2:     { en: 'SOC 2 (TSC)',        ar: 'SOC 2' },
  SAMA_CSF:  { en: 'SAMA CSF',           ar: 'إطار ساما السيبراني' },
  PDPL:      { en: 'PDPL (Saudi)',       ar: 'PDPL (السعودي)' },
  PCI_DSS:   { en: 'PCI DSS v4.0',       ar: 'PCI DSS v4.0' },
  NIST_CSF:  { en: 'NIST CSF',           ar: 'إطار NIST CSF' },
  GDPR:      { en: 'GDPR (EU)',          ar: 'النظام الأوروبي GDPR' },
};

/** Whether this framework is a Saudi regulator. Drives the "Saudi-first"
 *  badge in the UI. */
export const FRAMEWORK_IS_SAUDI: Record<Framework, boolean> = {
  NCA_ECC: true, SAMA_CSF: true, PDPL: true,
  ISO_27001: false, SOC_2: false, PCI_DSS: false, NIST_CSF: false, GDPR: false,
};

export type ControlStatus = 'IMPLEMENTED' | 'PARTIAL' | 'NOT_IMPLEMENTED' | 'NOT_APPLICABLE';

export interface FrameworkRef {
  framework: Framework;
  /** Clause / control code as the framework refers to it. */
  clause: string;
  /** Optional short title to clarify the clause for humans. */
  titleEn?: string;
  titleAr?: string;
}

export interface MasterControl {
  /** Internal canonical code, e.g. "M-MFA-PRIV". */
  code: string;
  titleEn: string;
  titleAr: string;
  descriptionEn: string;
  descriptionAr: string;
  /** Topic group for filtering. */
  domainEn: string;
  domainAr: string;
  status: ControlStatus;
  /** Evidence reference — usually a CCM check id, policy code, or audit id. */
  evidenceRef?: string;
  /** Owner email. */
  ownerEmail: string;
  /** Frameworks + clauses this master control satisfies. */
  frameworkRefs: FrameworkRef[];
}

// ─── Master control catalogue ──────────────────────────────────────────────

export const MASTER_CONTROLS: MasterControl[] = [
  {
    code: 'M-MFA-PRIV',
    titleEn: 'Multi-factor authentication on privileged access',
    titleAr: 'مصادقة ثنائية للوصول الإمتيازي',
    descriptionEn: 'MFA enforced on every administrative + remote-access path. Service-account paths covered by certificate-based auth.',
    descriptionAr: 'مصادقة ثنائية على جميع مسارات الوصول الإداري وعن بُعد. حسابات الخدمة بمصادقة شهادات.',
    domainEn: 'Identity & Access', domainAr: 'الهوية والوصول',
    status: 'PARTIAL',
    evidenceRef: 'cn-evidence-aws.iam-mfa',
    ownerEmail: 'iam.team@tss.sa',
    frameworkRefs: [
      { framework: 'NCA_ECC',   clause: '2-3-1', titleEn: 'Authentication & authorisation' },
      { framework: 'ISO_27001', clause: 'A.5.17', titleEn: 'Authentication information' },
      { framework: 'SOC_2',     clause: 'CC6.1',  titleEn: 'Logical access — restrictions' },
      { framework: 'SAMA_CSF',  clause: '3.1.5',  titleEn: 'Access management' },
      { framework: 'PCI_DSS',   clause: '8.4',     titleEn: 'MFA for non-console access' },
      { framework: 'GDPR',      clause: 'Art. 32', titleEn: 'Security of processing' },
    ],
  },
  {
    code: 'M-ENCRYPT-REST',
    titleEn: 'Encryption of data at rest',
    titleAr: 'تشفير البيانات أثناء التخزين',
    descriptionEn: 'AES-256 for all Tier-0/1 data on databases, file shares, and cloud storage. Key management via KMS.',
    descriptionAr: 'تشفير AES-256 لجميع بيانات الفئة 0/1 في قواعد البيانات والمشاركات والسحابة. إدارة المفاتيح عبر KMS.',
    domainEn: 'Cryptography', domainAr: 'التشفير',
    status: 'IMPLEMENTED',
    evidenceRef: 'cn-evidence-aws.s3-encryption',
    ownerEmail: 'cloud.platform@tss.sa',
    frameworkRefs: [
      { framework: 'NCA_ECC',   clause: '2-5-1', titleEn: 'Cryptography — at rest' },
      { framework: 'ISO_27001', clause: 'A.8.24', titleEn: 'Use of cryptography' },
      { framework: 'SOC_2',     clause: 'CC6.7',  titleEn: 'Information transmission & disposal' },
      { framework: 'PCI_DSS',   clause: '3.5',     titleEn: 'Cardholder data encryption' },
      { framework: 'PDPL',      clause: 'Art. 19', titleEn: 'Personal data security' },
      { framework: 'GDPR',      clause: 'Art. 32', titleEn: 'Security of processing' },
    ],
  },
  {
    code: 'M-ENCRYPT-TRANSIT',
    titleEn: 'Encryption of data in transit',
    titleAr: 'تشفير البيانات أثناء النقل',
    descriptionEn: 'TLS 1.3 enforced on all external + internal sensitive flows. Legacy TLS 1.0/1.1 disabled.',
    descriptionAr: 'تطبيق TLS 1.3 على جميع التدفقات الخارجية والداخلية الحساسة. تعطيل TLS 1.0/1.1.',
    domainEn: 'Cryptography', domainAr: 'التشفير',
    status: 'IMPLEMENTED',
    ownerEmail: 'platform.lead@tss.sa',
    frameworkRefs: [
      { framework: 'NCA_ECC',   clause: '2-5-2', titleEn: 'Cryptography — in transit' },
      { framework: 'ISO_27001', clause: 'A.8.24', titleEn: 'Use of cryptography' },
      { framework: 'SAMA_CSF',  clause: '3.3.4',  titleEn: 'Network security' },
      { framework: 'PCI_DSS',   clause: '4.2',     titleEn: 'Strong cryptography for cardholder data' },
    ],
  },
  {
    code: 'M-VULN-MTTR',
    titleEn: 'Critical vulnerability MTTR',
    titleAr: 'متوسط زمن معالجة الثغرات الحرجة',
    descriptionEn: 'Critical CVEs (CVSS ≥ 9.0) on internet-facing systems remediated within 7 days of publication.',
    descriptionAr: 'الثغرات الحرجة (CVSS ≥ 9.0) على الأنظمة المتصلة بالإنترنت تُعالَج خلال 7 أيام.',
    domainEn: 'Vulnerability Management', domainAr: 'إدارة الثغرات',
    status: 'PARTIAL',
    evidenceRef: 'POL-VULN-001 v3.0 (DRAFT)',
    ownerEmail: 'soc.lead@tss.sa',
    frameworkRefs: [
      { framework: 'NCA_ECC',   clause: '2-6-2', titleEn: 'Vulnerability remediation SLA' },
      { framework: 'ISO_27001', clause: 'A.8.8',  titleEn: 'Management of technical vulnerabilities' },
      { framework: 'PCI_DSS',   clause: '11.3.1', titleEn: 'Vulnerability management' },
      { framework: 'NIST_CSF',  clause: 'PR.IP-12', titleEn: 'Vulnerability management plan' },
    ],
  },
  {
    code: 'M-LOG-CENTRAL',
    titleEn: 'Centralised audit logging',
    titleAr: 'تسجيل تدقيق مركزي',
    descriptionEn: 'Audit logs from all critical systems forwarded to a SIEM with 12-month online retention + tamper-proof storage.',
    descriptionAr: 'سجلات تدقيق من جميع الأنظمة الحرجة مُحوَّلة إلى SIEM باحتفاظ 12 شهراً مع تخزين مقاوم للتلاعب.',
    domainEn: 'Logging & Monitoring', domainAr: 'التسجيل والمراقبة',
    status: 'IMPLEMENTED',
    evidenceRef: 'cn-splunk.audit-coverage',
    ownerEmail: 'csirt.lead@tss.sa',
    frameworkRefs: [
      { framework: 'NCA_ECC',   clause: '2-4-1', titleEn: 'Centralised logging' },
      { framework: 'ISO_27001', clause: 'A.8.15', titleEn: 'Logging' },
      { framework: 'SOC_2',     clause: 'CC7.2',  titleEn: 'Monitoring of system components' },
      { framework: 'PCI_DSS',   clause: '10.2',    titleEn: 'Audit log content' },
      { framework: 'NIST_CSF',  clause: 'DE.CM-1', titleEn: 'Continuous monitoring' },
    ],
  },
  {
    code: 'M-INCIDENT-RESP',
    titleEn: 'Incident response plan + 24/7 SOC',
    titleAr: 'خطة استجابة للحوادث + SOC على مدار الساعة',
    descriptionEn: 'Documented IR plan tested annually. SOC operates 24/7 with documented use-cases for high-severity alerts.',
    descriptionAr: 'خطة استجابة للحوادث موثَّقة ومُختبَرة سنوياً. SOC يعمل 24/7 مع حالات استخدام موثَّقة للتنبيهات الحرجة.',
    domainEn: 'Incident Response', domainAr: 'الاستجابة للحوادث',
    status: 'IMPLEMENTED',
    ownerEmail: 'csirt.lead@tss.sa',
    frameworkRefs: [
      { framework: 'NCA_ECC',   clause: '3-1-1', titleEn: 'Incident response capability' },
      { framework: 'ISO_27001', clause: 'A.5.24', titleEn: 'Information security incident management planning' },
      { framework: 'SOC_2',     clause: 'CC7.4',  titleEn: 'Incident detection & response' },
      { framework: 'NIST_CSF',  clause: 'RS.AN',   titleEn: 'Analysis function' },
      { framework: 'SAMA_CSF',  clause: '3.4.1',   titleEn: 'Cyber incident handling' },
    ],
  },
  {
    code: 'M-BCP-CYBER',
    titleEn: 'BCP includes cyber scenarios + annual exercise',
    titleAr: 'استمرارية الأعمال تشمل سيناريوهات سيبرانية وتمريناً سنوياً',
    descriptionEn: 'BCP explicitly addresses ransomware, DDoS, and data-integrity scenarios with tested playbooks. Annual cyber resilience exercise.',
    descriptionAr: 'خطة الاستمرارية تعالج صراحةً سيناريوهات برامج الفدية وحجب الخدمة وسلامة البيانات. تمرين سنوي للصمود السيبراني.',
    domainEn: 'Resilience', domainAr: 'الصمود',
    status: 'IMPLEMENTED',
    evidenceRef: 'POL-BCP-001 §6.3',
    ownerEmail: 'bcm.lead@tss.sa',
    frameworkRefs: [
      { framework: 'NCA_ECC',   clause: '3-1-1', titleEn: 'BCP cyber scenarios' },
      { framework: 'ISO_27001', clause: 'A.5.30', titleEn: 'ICT readiness for business continuity' },
      { framework: 'SAMA_CSF',  clause: '3.5.2',  titleEn: 'Resilience' },
      { framework: 'NIST_CSF',  clause: 'RC.RP-1', titleEn: 'Recovery plan' },
    ],
  },
  {
    code: 'M-VENDOR-CYBER',
    titleEn: 'Vendor cybersecurity assessment',
    titleAr: 'تقييم أمن سيبراني للموردين',
    descriptionEn: 'Pre-onboarding cybersecurity questionnaire + tier-driven attestation requirements. Periodic re-assessment per tier.',
    descriptionAr: 'استبيان أمن سيبراني قبل الاستلام + متطلبات شهادات حسب فئة المورد. إعادة تقييم دورية.',
    domainEn: 'Third-Party', domainAr: 'الأطراف الثالثة',
    status: 'IMPLEMENTED',
    evidenceRef: 'TPRM module',
    ownerEmail: 'tprm.lead@tss.sa',
    frameworkRefs: [
      { framework: 'NCA_ECC',   clause: '4-1-1', titleEn: 'Vendor cybersecurity' },
      { framework: 'ISO_27001', clause: 'A.5.20', titleEn: 'Addressing information security in supplier agreements' },
      { framework: 'SOC_2',     clause: 'CC9.2',  titleEn: 'Vendor risk management' },
      { framework: 'SAMA_CSF',  clause: '3.6.1',  titleEn: 'Outsourcing — cyber' },
    ],
  },
  {
    code: 'M-DSR-30D',
    titleEn: 'PDPL data-subject request fulfilment within 30 days',
    titleAr: 'تنفيذ طلب صاحب البيانات خلال 30 يوماً',
    descriptionEn: 'PDPL Art. 17 requests fulfilled within 30 days. Identity verification, legal-hold check, end-to-end audit trail.',
    descriptionAr: 'طلبات المادة 17 تُنفَّذ خلال 30 يوماً. تحقق الهوية، فحص الحجز القانوني، أثر تدقيق متكامل.',
    domainEn: 'Privacy', domainAr: 'الخصوصية',
    status: 'IMPLEMENTED',
    ownerEmail: 'dpo@tss.sa',
    frameworkRefs: [
      { framework: 'PDPL',      clause: 'Art. 17', titleEn: 'Right of access / erasure' },
      { framework: 'GDPR',      clause: 'Art. 12', titleEn: 'Transparent information' },
      { framework: 'ISO_27001', clause: 'A.5.34', titleEn: 'Privacy of PII' },
    ],
  },
  {
    code: 'M-DSR-72H-BREACH',
    titleEn: 'PDPL 72-hour breach notification',
    titleAr: 'إبلاغ خرق البيانات خلال 72 ساعة',
    descriptionEn: 'Personal-data breaches notified to SDAIA within 72h of awareness with PDPL Art. 25 fields.',
    descriptionAr: 'إبلاغ سدايا بحوادث اختراق البيانات خلال 72 ساعة من العلم مع جميع متطلبات المادة 25.',
    domainEn: 'Privacy', domainAr: 'الخصوصية',
    status: 'IMPLEMENTED',
    evidenceRef: 'PdplBreachConsole',
    ownerEmail: 'dpo@tss.sa',
    frameworkRefs: [
      { framework: 'PDPL',      clause: 'Art. 25', titleEn: 'Breach notification 72h' },
      { framework: 'GDPR',      clause: 'Art. 33', titleEn: 'Notification to supervisory authority' },
      { framework: 'NCA_ECC',   clause: '3-1-2',   titleEn: 'Notification to authorities' },
    ],
  },
  {
    code: 'M-CHANGE-CAB',
    titleEn: 'Approved change-enablement (CAB)',
    titleAr: 'إدارة التغيير المُعتمَدة (CAB)',
    descriptionEn: 'All production changes follow approved CAB process with risk assessment, peer review, and post-implementation review.',
    descriptionAr: 'جميع تغييرات الإنتاج تتبع إجراء CAB مع تقييم مخاطر ومراجعة نظيرة ومراجعة ما بعد التنفيذ.',
    domainEn: 'Change Management', domainAr: 'إدارة التغيير',
    status: 'IMPLEMENTED',
    evidenceRef: 'cn-evidence-aws.change-tracking',
    ownerEmail: 'devops.lead@tss.sa',
    frameworkRefs: [
      { framework: 'NCA_ECC',   clause: '2-2-1',   titleEn: 'Change management' },
      { framework: 'ISO_27001', clause: 'A.8.32',  titleEn: 'Change management' },
      { framework: 'SOC_2',     clause: 'CC8.1',   titleEn: 'Change management' },
      { framework: 'PCI_DSS',   clause: '6.5',     titleEn: 'Production changes' },
    ],
  },
  {
    code: 'M-AWARE-PHISH',
    titleEn: 'Cybersecurity awareness + phishing simulation',
    titleAr: 'التوعية بالأمن السيبراني ومحاكاة التصيّد',
    descriptionEn: 'Quarterly awareness training + phishing-simulation programme covering all employees. Click-rate target < 5%.',
    descriptionAr: 'تدريب توعوي ربع سنوي وبرنامج محاكاة تصيّد يشمل جميع الموظفين. هدف معدل النقر أقل من 5%.',
    domainEn: 'People', domainAr: 'العنصر البشري',
    status: 'IMPLEMENTED',
    ownerEmail: 'ciso@tss.sa',
    frameworkRefs: [
      { framework: 'NCA_ECC',   clause: '1-5-1', titleEn: 'Cybersecurity awareness' },
      { framework: 'ISO_27001', clause: 'A.6.3', titleEn: 'Information security awareness' },
      { framework: 'SOC_2',     clause: 'CC1.4', titleEn: 'COSO commitment to integrity' },
      { framework: 'PCI_DSS',   clause: '12.6',   titleEn: 'Security awareness programme' },
    ],
  },
  {
    code: 'M-RESIDENCY-KSA',
    titleEn: 'KSA data residency for regulated workloads',
    titleAr: 'إقامة البيانات في المملكة للأحمال المُنظَّمة',
    descriptionEn: 'Tier-1 data resides in KSA cloud regions. Cross-border transfer requires written approval per workload.',
    descriptionAr: 'بيانات الفئة 1 تبقى في مناطق سحابية داخل المملكة. النقل عبر الحدود يتطلب موافقة كتابية لكل عبء عمل.',
    domainEn: 'Data Residency', domainAr: 'إقامة البيانات',
    status: 'PARTIAL',
    evidenceRef: 'POL-CLOUD-001 v2.1',
    ownerEmail: 'cloud.platform@tss.sa',
    frameworkRefs: [
      { framework: 'NCA_ECC',  clause: '4-2-1',   titleEn: 'Cloud regional residency' },
      { framework: 'SAMA_CSF', clause: '3.6.3',    titleEn: 'Cloud computing — data residency' },
      { framework: 'PDPL',     clause: 'Art. 29',  titleEn: 'Cross-border transfer' },
    ],
  },
  {
    code: 'M-PEN-TEST',
    titleEn: 'Annual external penetration test',
    titleAr: 'اختبار اختراق خارجي سنوي',
    descriptionEn: 'Annual independent pen-test of internet-facing systems. Findings tracked to closure.',
    descriptionAr: 'اختبار اختراق سنوي مستقل للأنظمة المتصلة بالإنترنت. النتائج مُتابَعة حتى الإغلاق.',
    domainEn: 'Vulnerability Management', domainAr: 'إدارة الثغرات',
    status: 'IMPLEMENTED',
    evidenceRef: 'PT-2026-EY',
    ownerEmail: 'soc.lead@tss.sa',
    frameworkRefs: [
      { framework: 'NCA_ECC',   clause: '2-7-1', titleEn: 'Penetration testing' },
      { framework: 'ISO_27001', clause: 'A.8.29', titleEn: 'Security testing in development & acceptance' },
      { framework: 'PCI_DSS',   clause: '11.4',   titleEn: 'External and internal pen testing' },
    ],
  },
];

// ─── Stats / display ───────────────────────────────────────────────────────

export const STATUS_TONE: Record<ControlStatus, 'success' | 'warning' | 'danger' | 'neutral'> = {
  IMPLEMENTED: 'success',
  PARTIAL: 'warning',
  NOT_IMPLEMENTED: 'danger',
  NOT_APPLICABLE: 'neutral',
};

export const STATUS_LABEL: Record<ControlStatus, { en: string; ar: string }> = {
  IMPLEMENTED:     { en: 'Implemented',     ar: 'مُطبَّق' },
  PARTIAL:         { en: 'Partial',         ar: 'جزئي' },
  NOT_IMPLEMENTED: { en: 'Not implemented', ar: 'غير مُطبَّق' },
  NOT_APPLICABLE:  { en: 'N/A',             ar: 'غير منطبق' },
};

export interface CrosswalkStats {
  totalMasters: number;
  totalRefs: number;
  uniqueFrameworks: number;
  avgRefsPerMaster: number;
  /** Refs per framework — counts how many master controls reference each. */
  byFramework: Record<Framework, number>;
  /** Status rollup. */
  implementedPct: number;
}

export function aggregateCrosswalk(masters: ReadonlyArray<MasterControl> = MASTER_CONTROLS): CrosswalkStats {
  const byFramework: Record<Framework, number> = {
    NCA_ECC: 0, ISO_27001: 0, SOC_2: 0, SAMA_CSF: 0, PDPL: 0, PCI_DSS: 0, NIST_CSF: 0, GDPR: 0,
  };
  let totalRefs = 0;
  let implemented = 0;
  for (const m of masters) {
    totalRefs += m.frameworkRefs.length;
    if (m.status === 'IMPLEMENTED') implemented++;
    else if (m.status === 'PARTIAL') implemented += 0.5;
    for (const r of m.frameworkRefs) byFramework[r.framework]++;
  }
  const uniqueFrameworks = Object.values(byFramework).filter(v => v > 0).length;
  return {
    totalMasters: masters.length,
    totalRefs,
    uniqueFrameworks,
    avgRefsPerMaster: masters.length > 0 ? Math.round((totalRefs / masters.length) * 10) / 10 : 0,
    byFramework,
    implementedPct: masters.length > 0 ? Math.round((implemented / masters.length) * 100) : 0,
  };
}

export function findMasterByFrameworkClause(framework: Framework, clause: string): MasterControl | undefined {
  return MASTER_CONTROLS.find(m =>
    m.frameworkRefs.some(r => r.framework === framework && r.clause === clause));
}
