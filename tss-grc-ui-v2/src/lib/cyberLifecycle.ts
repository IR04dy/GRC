// Cyber lifecycle — production hardening (Option 5 — same pattern as risk +
// audit + privacy + BCM lifecycle workspaces).
//
// Wraps the existing cyber register with the full NCA ECC v3 + SAMA Cyber
// Resilience Framework lifecycle a CISO in Saudi Arabia is expected to run:
//
//   1. NCA ECC self-assessment — domain-by-domain maturity scoring with the
//      annual filing workflow (DRAFT → PEER_REVIEW → CISO_APPROVAL →
//      SUBMITTED). Generates the official ECC return packet for the NCA
//      "Haseen" portal.
//   2. Vulnerability management — CVE intake → triage → owner assignment →
//      remediation → verification with auto-computed SLA based on CVSS +
//      asset tier (Tier-1 7d for Critical / 14d High / 30d Medium / 60d Low).
//   3. Threat intelligence — NCA Haseen advisories + sector ISAC feeds with
//      MTTD/MTTR per advisory.
//   4. Asset criticality register — Tier classification driving patch SLA.
//   5. SOC alert triage — OPEN → INVESTIGATING → CONTAINED → CLOSED state
//      machine with MTTR tracking + handover to incident workflow when
//      severity escalates.
//   6. Penetration test programme — quarterly red-team for Tier-1 entities
//      per NCA ECC 2-5-1 + SAMA CSF 4.5 with finding integration.

import {
  CYBER_VULN_ADAPTER,
  mirrorSaveToBackend,
  mirrorTransitionToBackend,
} from './platformPersistence';

// ─── Types ──────────────────────────────────────────────────────────────────

export type EccDomain = '1' | '2' | '3' | '4' | '5';
export type EccMaturity = 0 | 1 | 2 | 3 | 4 | 5;
export type EccFilingStatus = 'DRAFT' | 'PEER_REVIEW' | 'CISO_APPROVAL' | 'SUBMITTED' | 'ACCEPTED';

export type AssetTier = 'TIER-1-CRITICAL' | 'TIER-2-HIGH' | 'TIER-3-MEDIUM' | 'TIER-4-LOW';

export type VulnSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type VulnStatus = 'NEW' | 'TRIAGED' | 'OWNER_ASSIGNED' | 'IN_REMEDIATION' | 'AWAITING_VERIFICATION' | 'CLOSED' | 'ACCEPTED';

export type SocSeverity = 'P1' | 'P2' | 'P3' | 'P4';
export type SocStatus = 'OPEN' | 'INVESTIGATING' | 'CONTAINED' | 'ESCALATED_TO_IR' | 'CLOSED';

export type PenTestStatus = 'PLANNED' | 'IN_PROGRESS' | 'REPORT_DRAFT' | 'CLOSED';
export type ThreatSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

// ─── NCA ECC v3 self-assessment ────────────────────────────────────────────

export interface EccDomainScore {
  domain: EccDomain;
  /** Domain name. */
  nameEn: string;
  nameAr: string;
  /** Number of controls in this domain. */
  controlsTotal: number;
  /** Maturity level scored (NCA scale 0-5). */
  maturity: EccMaturity;
  /** Self-assessment narrative — bilingual. */
  narrativeEn: string;
  narrativeAr: string;
  /** Number of controls with documented evidence. */
  evidencedControls: number;
  /** Number of controls in remediation. */
  remediationControls: number;
}

export interface EccFiling {
  id: string;
  /** Reporting year (Gregorian). */
  year: number;
  status: EccFilingStatus;
  scores: EccDomainScore[];
  /** Overall composite maturity (weighted average). */
  compositeMaturity: number;
  ciSpoc: string;
  reviewedBy?: string;
  reviewedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  submittedAt?: string;
  /** NCA acknowledgement reference. */
  ncaAckRef?: string;
  createdAt: string;
}

// ─── Vulnerability management ──────────────────────────────────────────────

export interface CyberAsset {
  id: string;
  name: string;
  /** What it does. */
  descriptionEn: string;
  descriptionAr: string;
  tier: AssetTier;
  /** Owner role. */
  owner: string;
  /** External-facing or internal-only. */
  externalFacing: boolean;
  /** Site location (Riyadh / Jeddah / cloud). */
  location: 'RIYADH-PRIMARY' | 'JEDDAH-DR' | 'AWS-RIYADH' | 'AWS-JEDDAH' | 'AZURE-DUBAI' | 'ON-PREM';
  /** Last patch applied. */
  lastPatchedAt?: string;
}

export interface Vulnerability {
  id: string;
  cveId: string;
  titleEn: string;
  titleAr: string;
  /** Linked asset id. */
  assetId: string;
  cvssScore: number;
  severity: VulnSeverity;
  /** SLA in days based on severity + asset tier. */
  slaDays: number;
  /** Discovery date. */
  discoveredAt: string;
  /** Auto-computed due date. */
  dueDate: string;
  /** Current status. */
  status: VulnStatus;
  /** Owner currently responsible. */
  currentOwner?: string;
  /** Remediation notes. */
  notesEn?: string;
  notesAr?: string;
  /** Closure date. */
  closedAt?: string;
}

// ─── Threat intel ──────────────────────────────────────────────────────────

export interface ThreatAdvisory {
  id: string;
  /** Source (NCA Haseen / KSA-CERT / SAMA / sector ISAC). */
  source: 'NCA-HASEEN' | 'KSA-CERT' | 'SAMA-ISAC' | 'TELECOM-ISAC' | 'INTERNAL';
  /** Advisory id from source. */
  sourceRef: string;
  titleEn: string;
  titleAr: string;
  severity: ThreatSeverity;
  /** Date issued. */
  issuedAt: string;
  /** Bilingual summary. */
  summaryEn: string;
  summaryAr: string;
  /** Affected products/CVEs. */
  affectedProducts: string[];
  /** Recommended action. */
  recommendedActionEn: string;
  recommendedActionAr: string;
  /** Has the entity acted on this? */
  acknowledged: boolean;
  /** Action timestamp. */
  actionedAt?: string;
  /** Action notes. */
  actionNotesEn?: string;
  actionNotesAr?: string;
}

// ─── SOC alert ─────────────────────────────────────────────────────────────

export interface SocAlert {
  id: string;
  titleEn: string;
  titleAr: string;
  severity: SocSeverity;
  status: SocStatus;
  /** Source detector. */
  detector: 'EDR' | 'SIEM' | 'IDS' | 'WAF' | 'EMAIL-GW' | 'CTI-FEED' | 'MANUAL';
  /** Affected asset id. */
  assetId?: string;
  /** Detection timestamp. */
  detectedAt: string;
  /** Acknowledged by analyst. */
  ackedAt?: string;
  /** Contained / closed timestamp. */
  resolvedAt?: string;
  /** Analyst handling. */
  analyst?: string;
  /** Bilingual narrative. */
  narrativeEn: string;
  narrativeAr: string;
  /** Whether escalated to incident response. */
  irRefId?: string;
}

// ─── Penetration test ─────────────────────────────────────────────────────

export interface PenTest {
  id: string;
  titleEn: string;
  titleAr: string;
  /** Type — internal, external, web app, mobile, red team. */
  scope: 'EXTERNAL' | 'INTERNAL' | 'WEB-APP' | 'MOBILE' | 'RED-TEAM' | 'API';
  status: PenTestStatus;
  /** External vendor or in-house. */
  vendor: string;
  /** Quarter scheduled. */
  scheduledQuarter: string;
  /** Started / completed dates. */
  startedAt?: string;
  completedAt?: string;
  /** Findings count by severity. */
  criticalFindings: number;
  highFindings: number;
  mediumFindings: number;
  /** Remediation status %. */
  remediationPct: number;
  /** Bilingual scope description. */
  scopeNotesEn: string;
  scopeNotesAr: string;
}

// ─── Storage ────────────────────────────────────────────────────────────────

const FILING_KEY = 'grc.cyber.filing.v1';
const VULN_KEY = 'grc.cyber.vuln.v1';
const ASSET_KEY = 'grc.cyber.asset.v1';
const THREAT_KEY = 'grc.cyber.threat.v1';
const SOC_KEY = 'grc.cyber.soc.v1';
const PENTEST_KEY = 'grc.cyber.pentest.v1';
const EVENT = 'grc.cyber.lifecycle.changed';

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString();
const _daysAhead = (n: number) => new Date(_now + n * 86_400_000).toISOString().slice(0, 10);

// ─── NCA ECC v3 — domain catalogue ─────────────────────────────────────────

export const ECC_DOMAINS_DEF: { code: EccDomain; nameEn: string; nameAr: string; controlsTotal: number }[] = [
  { code: '1', nameEn: 'Cybersecurity Governance',         nameAr: 'حوكمة الأمن السيبراني',          controlsTotal: 33 },
  { code: '2', nameEn: 'Cybersecurity Defense',             nameAr: 'الدفاع السيبراني',                 controlsTotal: 73 },
  { code: '3', nameEn: 'Cybersecurity Resilience',           nameAr: 'الصمود السيبراني',                  controlsTotal: 6  },
  { code: '4', nameEn: 'Third-Party Cybersecurity',          nameAr: 'الأمن السيبراني للأطراف الثالثة',  controlsTotal: 5  },
  { code: '5', nameEn: 'Industrial Control Systems',         nameAr: 'أنظمة التحكّم الصناعية',            controlsTotal: 4  },
];

// ─── Seed ───────────────────────────────────────────────────────────────────

const SEED_FILING: EccFiling = {
  id: 'ECC-2026',
  year: 2026,
  status: 'PEER_REVIEW',
  ciSpoc: 'Sara Al-Zahrani (CISO)',
  reviewedBy: 'Mohammed Al-Sulaiman (Head of Cyber Defense)',
  reviewedAt: _daysAgo(15),
  scores: [
    { domain: '1', nameEn: 'Cybersecurity Governance',     nameAr: 'حوكمة الأمن السيبراني',           controlsTotal: 33, maturity: 4, evidencedControls: 31, remediationControls: 2,
      narrativeEn: 'Cybersecurity policy + strategy approved by Board 2026-Q1. RACI matrix updated. Annual cyber risk assessment completed Mar 2026.',
      narrativeAr: 'اعتُمدت السياسة والاستراتيجية من المجلس في الربع الأول 2026. مصفوفة RACI مُحدَّثة. اكتمل تقييم المخاطر السيبراني السنوي مارس 2026.' },
    { domain: '2', nameEn: 'Cybersecurity Defense',         nameAr: 'الدفاع السيبراني',                  controlsTotal: 73, maturity: 4, evidencedControls: 68, remediationControls: 5,
      narrativeEn: 'EDR deployed to 100% of fleet. PAM enforced on all admin accounts (post FIN-2026-001 closure). 5 controls in remediation around legacy network segmentation.',
      narrativeAr: 'EDR مُنتشر على 100٪ من الأسطول. PAM مفروض على جميع حسابات المشرفين (بعد إغلاق FIN-2026-001). 5 ضوابط قيد المعالجة حول تجزئة الشبكة القديمة.' },
    { domain: '3', nameEn: 'Cybersecurity Resilience',       nameAr: 'الصمود السيبراني',                  controlsTotal: 6,  maturity: 5, evidencedControls: 6, remediationControls: 0,
      narrativeEn: 'IR plan tested quarterly. BCP/DRP runs annually with last full failover Q2 2026 (RTO met). Cyber-physical drill participated NCEMA Q3.',
      narrativeAr: 'خطة الاستجابة للحوادث تُختبر ربع سنوياً. خطة الاستمرارية والتعافي تنفَّذ سنوياً مع آخر تحويل كامل في الربع الثاني 2026 (تحقّق RTO). تمرين سيبراني-مادي مع NCEMA الربع الثالث.' },
    { domain: '4', nameEn: 'Third-Party Cybersecurity',      nameAr: 'الأمن السيبراني للأطراف الثالثة',   controlsTotal: 5,  maturity: 3, evidencedControls: 4, remediationControls: 1,
      narrativeEn: 'Tier-1 vendor due-diligence + annual reassessment in place. 1 control in remediation: continuous control monitoring for Tier-2 vendors not yet automated.',
      narrativeAr: 'العناية الواجبة للموردين من الفئة 1 وإعادة التقييم السنوية مطبَّقة. ضابط واحد قيد المعالجة: المراقبة المستمرة للضوابط للفئة 2 غير مؤتمتة بعد.' },
    { domain: '5', nameEn: 'Industrial Control Systems',     nameAr: 'أنظمة التحكّم الصناعية',             controlsTotal: 4,  maturity: 0, evidencedControls: 0, remediationControls: 0,
      narrativeEn: 'Not applicable — entity does not operate ICS environments. Marked N/A in self-assessment.',
      narrativeAr: 'لا ينطبق — المنشأة لا تشغّل بيئات أنظمة تحكّم صناعية. مُعلَّمة كـ N/A في التقييم الذاتي.' },
  ],
  compositeMaturity: 4.0,
  createdAt: _daysAgo(45),
};

const SEED_ASSETS: CyberAsset[] = [
  { id: 'AST-001', name: 'core-banking-switch',  descriptionEn: 'Core banking switch — primary',         descriptionAr: 'قاطع النظام المصرفي الأساسي — الرئيسي', tier: 'TIER-1-CRITICAL', owner: 'CIO', externalFacing: false, location: 'RIYADH-PRIMARY', lastPatchedAt: _daysAgo(45).slice(0, 10) },
  { id: 'AST-002', name: 'card-switch',          descriptionEn: 'Card payment switch',                    descriptionAr: 'قاطع مدفوعات البطاقات',                tier: 'TIER-1-CRITICAL', owner: 'Head of Cards', externalFacing: false, location: 'RIYADH-PRIMARY', lastPatchedAt: _daysAgo(30).slice(0, 10) },
  { id: 'AST-003', name: 'public-web',           descriptionEn: 'Public web frontend',                     descriptionAr: 'الواجهة الإلكترونية العامة',           tier: 'TIER-2-HIGH',     owner: 'Head of Digital', externalFacing: true,  location: 'AWS-RIYADH', lastPatchedAt: _daysAgo(15).slice(0, 10) },
  { id: 'AST-004', name: 'api-gateway-prod',      descriptionEn: 'API gateway — production',                descriptionAr: 'بوابة API — الإنتاج',                    tier: 'TIER-2-HIGH',     owner: 'Head of API', externalFacing: true, location: 'AWS-RIYADH', lastPatchedAt: _daysAgo(20).slice(0, 10) },
  { id: 'AST-005', name: 'corp-wiki',            descriptionEn: 'Corporate wiki — internal',                descriptionAr: 'الويكي المؤسسي — داخلي',                tier: 'TIER-3-MEDIUM',   owner: 'Head of HR', externalFacing: false, location: 'ON-PREM', lastPatchedAt: _daysAgo(75).slice(0, 10) },
  { id: 'AST-006', name: 'jenkins-ci',           descriptionEn: 'Jenkins CI/CD',                            descriptionAr: 'Jenkins CI/CD',                       tier: 'TIER-3-MEDIUM',   owner: 'Head of DevOps', externalFacing: false, location: 'AWS-JEDDAH', lastPatchedAt: _daysAgo(50).slice(0, 10) },
  { id: 'AST-007', name: 'ldap-server',          descriptionEn: 'LDAP directory server',                    descriptionAr: 'خادم دليل LDAP',                       tier: 'TIER-2-HIGH',     owner: 'Head of Identity', externalFacing: false, location: 'RIYADH-PRIMARY', lastPatchedAt: _daysAgo(40).slice(0, 10) },
];

const SEED_VULNS: Vulnerability[] = [
  { id: 'VLN-2026-001', cveId: 'CVE-2025-3041',
    titleEn: 'RCE in Spring Web — public web frontend',
    titleAr: 'تنفيذ كود عن بُعد في Spring Web — الواجهة العامة',
    assetId: 'AST-003', cvssScore: 9.8, severity: 'CRITICAL', slaDays: 7,
    discoveredAt: _daysAgo(5), dueDate: _daysAhead(2),
    status: 'IN_REMEDIATION', currentOwner: 'Head of Digital',
    notesEn: 'Patch tested in staging. Production deployment scheduled for tonight 2am-3am window.',
    notesAr: 'تم اختبار التصحيح في بيئة الاختبار. النشر الإنتاجي مقرّر الليلة 2-3 صباحاً.' },
  { id: 'VLN-2026-002', cveId: 'CVE-2025-2918',
    titleEn: 'SSRF in API gateway plugin',
    titleAr: 'SSRF في إضافة بوابة API',
    assetId: 'AST-004', cvssScore: 8.4, severity: 'HIGH', slaDays: 14,
    discoveredAt: _daysAgo(8), dueDate: _daysAhead(6),
    status: 'OWNER_ASSIGNED', currentOwner: 'Head of API',
    notesEn: 'Owner assigned; remediation plan in draft.',
    notesAr: 'تم تعيين المالك؛ خطة المعالجة في مرحلة المسوّدة.' },
  { id: 'VLN-2026-003', cveId: 'CVE-2025-2104',
    titleEn: 'SQL injection in reporting service',
    titleAr: 'حقن SQL في خدمة التقارير',
    assetId: 'AST-001', cvssScore: 8.0, severity: 'HIGH', slaDays: 14,
    discoveredAt: _daysAgo(12), dueDate: _daysAhead(2),
    status: 'AWAITING_VERIFICATION', currentOwner: 'CIO',
    notesEn: 'Hotfix deployed 2026-04-25; awaiting verification scan.',
    notesAr: 'نُشر التصحيح العاجل في 25-04-2026؛ بانتظار فحص التحقق.' },
  { id: 'VLN-2026-004', cveId: 'CVE-2024-9831',
    titleEn: 'Authentication bypass in Jenkins plugin',
    titleAr: 'تجاوز المصادقة في إضافة Jenkins',
    assetId: 'AST-006', cvssScore: 6.5, severity: 'MEDIUM', slaDays: 30,
    discoveredAt: _daysAgo(18), dueDate: _daysAhead(12),
    status: 'TRIAGED' },
  { id: 'VLN-2026-005', cveId: 'CVE-2024-7712',
    titleEn: 'Reflected XSS in wiki search',
    titleAr: 'XSS منعكس في بحث الويكي',
    assetId: 'AST-005', cvssScore: 4.2, severity: 'LOW', slaDays: 60,
    discoveredAt: _daysAgo(25), dueDate: _daysAhead(35),
    status: 'NEW' },
  { id: 'VLN-2026-006', cveId: 'CVE-2024-5641',
    titleEn: 'Privilege escalation in LDAP overlay',
    titleAr: 'تصعيد الصلاحيات في طبقة LDAP',
    assetId: 'AST-007', cvssScore: 9.1, severity: 'CRITICAL', slaDays: 7,
    discoveredAt: _daysAgo(35), dueDate: _daysAgo(28).slice(0, 10),
    status: 'CLOSED', closedAt: _daysAgo(30),
    notesEn: 'Patched 2026-04-02; verified 2026-04-04.',
    notesAr: 'تم التصحيح 02-04-2026؛ تم التحقق 04-04-2026.' },
];

const SEED_THREATS: ThreatAdvisory[] = [
  { id: 'THR-2026-001', source: 'NCA-HASEEN', sourceRef: 'NCA-AD-2026-0148',
    titleEn: 'Active exploitation of CVE-2025-3041 — Spring Web RCE',
    titleAr: 'استغلال نشط لـ CVE-2025-3041 — تنفيذ كود عن بُعد في Spring Web',
    severity: 'CRITICAL',
    issuedAt: _daysAgo(6),
    summaryEn: 'NCA Haseen reports active exploitation by APT group targeting Saudi financial sector. Patches available; emergency window required.',
    summaryAr: 'يُفيد حصين بوجود استغلال نشط من مجموعة APT تستهدف القطاع المالي السعودي. التصحيحات متاحة؛ نافذة طارئة مطلوبة.',
    affectedProducts: ['Spring Web 5.x', 'Spring Web 6.0.0–6.0.12'],
    recommendedActionEn: 'Apply patches within 7 days. Add WAF rule blocking known exploitation patterns. Hunt for IoCs in detection logs.',
    recommendedActionAr: 'تطبيق التصحيحات خلال 7 أيام. إضافة قاعدة WAF تحجب أنماط الاستغلال المعروفة. البحث عن مؤشّرات الاختراق في سجلات الاكتشاف.',
    acknowledged: true, actionedAt: _daysAgo(5),
    actionNotesEn: 'WAF rule deployed 2026-04-23. Patch in production tonight. Hunt completed — no IoCs found.',
    actionNotesAr: 'نُشرت قاعدة WAF في 23-04-2026. التصحيح الليلة في الإنتاج. اكتمل البحث — لا توجد مؤشّرات.' },
  { id: 'THR-2026-002', source: 'SAMA-ISAC', sourceRef: 'SAMA-CTI-2026-031',
    titleEn: 'Phishing campaign impersonating SAMA — financial sector targeted',
    titleAr: 'حملة تصيّد تنتحل صفة ساما — القطاع المالي مستهدف',
    severity: 'HIGH',
    issuedAt: _daysAgo(12),
    summaryEn: 'SAMA ISAC distributed indicators of compromise for a phishing campaign targeting bank employees with SAMA-themed lures. Domains + IPs included.',
    summaryAr: 'وزّع مركز مشاركة المعلومات لساما مؤشّرات اختراق لحملة تصيّد تستهدف موظفي البنوك بإغراءات تتعلّق بساما. النطاقات وعناوين IP مُرفقة.',
    affectedProducts: ['Email gateways', 'User awareness'],
    recommendedActionEn: 'Block IoC domains at email gateway + DNS. Distribute awareness alert to all staff within 24h.',
    recommendedActionAr: 'حجب نطاقات مؤشّرات الاختراق في بوابة البريد + DNS. توزيع تنبيه توعية لجميع الموظفين خلال 24 ساعة.',
    acknowledged: true, actionedAt: _daysAgo(11) },
  { id: 'THR-2026-003', source: 'KSA-CERT', sourceRef: 'KSA-CERT-2026-077',
    titleEn: 'Ransomware tactics shift — DDoS+extortion observed in region',
    titleAr: 'تحوّل تكتيكات الفدية — DDoS مع الابتزاز مرصود في المنطقة',
    severity: 'HIGH',
    issuedAt: _daysAgo(3),
    summaryEn: 'KSA-CERT observed regional ransomware actors shifting to triple extortion: encryption + data theft + DDoS amplification.',
    summaryAr: 'رصد فريق طوارئ الحاسب السعودي تحوّل جهات الفدية الإقليمية إلى ثلاثي الابتزاز: التشفير + سرقة البيانات + DDoS.',
    affectedProducts: ['Backup architecture', 'DDoS protection'],
    recommendedActionEn: 'Validate immutable backups. Test DDoS upstream provider failover. Run tabletop covering data-extortion scenario within 30 days.',
    recommendedActionAr: 'التحقق من النسخ الاحتياطية غير القابلة للتعديل. اختبار التحويل لمزوّد حماية DDoS. إجراء تمرين على سيناريو ابتزاز البيانات خلال 30 يوماً.',
    acknowledged: false },
];

const SEED_SOC: SocAlert[] = [
  { id: 'SOC-2026-1042', titleEn: 'EDR — process injection detected on web-app-prod-01',
    titleAr: 'EDR — رصد حقن عملية على web-app-prod-01',
    severity: 'P1', status: 'INVESTIGATING',
    detector: 'EDR', assetId: 'AST-003',
    detectedAt: _daysAgo(0.04),
    ackedAt: _daysAgo(0.03),
    analyst: 'Faisal Al-Otaibi (SOC Tier-2)',
    narrativeEn: 'EDR flagged unsigned binary spawning child processes from web-app process. Initial triage suggests possible exploitation of CVE-2025-3041. Containment in progress.',
    narrativeAr: 'علّم EDR تشغيل ملف غير موقّع يُنتج عمليات فرعية من عملية web-app. الفرز الأولي يشير لاستغلال محتمل لـ CVE-2025-3041. الاحتواء جارٍ.' },
  { id: 'SOC-2026-1041', titleEn: 'SIEM — failed admin login burst from foreign IP',
    titleAr: 'SIEM — اندفاع محاولات دخول مشرف فاشلة من IP أجنبي',
    severity: 'P2', status: 'CONTAINED',
    detector: 'SIEM', assetId: 'AST-007',
    detectedAt: _daysAgo(0.5),
    ackedAt: _daysAgo(0.45),
    resolvedAt: _daysAgo(0.3),
    analyst: 'Khalid Al-Harbi (SOC Tier-1)',
    narrativeEn: '142 failed admin login attempts in 4 minutes from IP 185.x.x.x (Russia). Source IP blocked at perimeter. No successful authentication observed.',
    narrativeAr: '142 محاولة دخول مشرف فاشلة في 4 دقائق من IP 185.x.x.x (روسيا). تم حجب IP المصدر على المحيط. لم تُرصد مصادقة ناجحة.' },
  { id: 'SOC-2026-1040', titleEn: 'WAF — SQL injection attempts on /api/reports',
    titleAr: 'WAF — محاولات حقن SQL على /api/reports',
    severity: 'P3', status: 'CLOSED',
    detector: 'WAF', assetId: 'AST-004',
    detectedAt: _daysAgo(1),
    ackedAt: _daysAgo(0.9),
    resolvedAt: _daysAgo(0.7),
    analyst: 'Khalid Al-Harbi (SOC Tier-1)',
    narrativeEn: 'WAF blocked all attempts. Source pattern matches commodity scanner. Logged for trend analysis.',
    narrativeAr: 'حجب WAF كل المحاولات. النمط المصدر يطابق ماسحاً عاماً. سُجِّل للتحليل الاتجاهي.' },
];

const SEED_PENTEST: PenTest[] = [
  { id: 'PT-2026-Q1', titleEn: 'Q1 external + web app pentest',
    titleAr: 'اختبار اختراق الربع الأول الخارجي وتطبيق الويب',
    scope: 'EXTERNAL', status: 'CLOSED',
    vendor: 'CrowdStrike services',
    scheduledQuarter: 'Q1-2026',
    startedAt: '2026-02-01', completedAt: '2026-02-20',
    criticalFindings: 1, highFindings: 4, mediumFindings: 11,
    remediationPct: 100,
    scopeNotesEn: 'External-facing web apps + API gateway + public DNS. Black-box. NCA ECC 2-5-1 + SAMA CSF 4.5.',
    scopeNotesAr: 'تطبيقات الويب الخارجية + بوابة API + DNS العام. صندوق أسود. NCA ECC 2-5-1 + SAMA CSF 4.5.' },
  { id: 'PT-2026-Q2', titleEn: 'Q2 internal + Active Directory pentest',
    titleAr: 'اختبار اختراق الربع الثاني الداخلي و Active Directory',
    scope: 'INTERNAL', status: 'REPORT_DRAFT',
    vendor: 'PwC Cyber',
    scheduledQuarter: 'Q2-2026',
    startedAt: '2026-04-12', completedAt: '2026-04-30',
    criticalFindings: 0, highFindings: 2, mediumFindings: 7,
    remediationPct: 35,
    scopeNotesEn: 'Internal network segmentation + AD + lateral-movement scenarios. Grey-box.',
    scopeNotesAr: 'تجزئة الشبكة الداخلية + AD + سيناريوهات الحركة الجانبية. صندوق رمادي.' },
  { id: 'PT-2026-Q3', titleEn: 'Q3 red-team — assumed-breach scenario',
    titleAr: 'الفريق الأحمر للربع الثالث — سيناريو افتراض الاختراق',
    scope: 'RED-TEAM', status: 'PLANNED',
    vendor: 'Mandiant',
    scheduledQuarter: 'Q3-2026',
    criticalFindings: 0, highFindings: 0, mediumFindings: 0,
    remediationPct: 0,
    scopeNotesEn: 'Assumed-breach red-team starting from internal foothold. Targets: domain controller + core banking + payment switch. Coordinate with SOC for purple-team observations.',
    scopeNotesAr: 'فريق أحمر افتراض الاختراق ينطلق من موطئ داخلي. الأهداف: متحكّم النطاق + النظام المصرفي + قاطع الدفع. التنسيق مع SOC للمراقبة الأرجوانية.' },
];

// ─── Persistence ───────────────────────────────────────────────────────────

export function loadFiling(): EccFiling {
  if (typeof window === 'undefined') return SEED_FILING;
  try {
    const raw = window.localStorage.getItem(FILING_KEY);
    return raw ? JSON.parse(raw) as EccFiling : SEED_FILING;
  } catch { return SEED_FILING; }
}

export function saveFiling(f: EccFiling): EccFiling {
  const composite = f.scores.filter(s => s.maturity > 0).reduce((acc, s) => acc + s.maturity, 0)
    / Math.max(1, f.scores.filter(s => s.maturity > 0).length);
  const next: EccFiling = { ...f, compositeMaturity: Math.round(composite * 10) / 10 };
  try { window.localStorage.setItem(FILING_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return next;
}

export function transitionFiling(status: EccFilingStatus, by?: string): EccFiling {
  const f = loadFiling();
  const updated: EccFiling = { ...f, status };
  const now = new Date().toISOString();
  if (status === 'PEER_REVIEW') { updated.reviewedBy = by; updated.reviewedAt = now; }
  if (status === 'CISO_APPROVAL') { updated.approvedBy = by; updated.approvedAt = now; }
  if (status === 'SUBMITTED') { updated.submittedAt = now; }
  if (status === 'ACCEPTED') { updated.ncaAckRef = `NCA-ACK-${new Date().getFullYear()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`; }
  return saveFiling(updated);
}

export function loadVulns(): Vulnerability[] {
  if (typeof window === 'undefined') return SEED_VULNS;
  try {
    const raw = window.localStorage.getItem(VULN_KEY);
    return raw ? JSON.parse(raw) as Vulnerability[] : SEED_VULNS;
  } catch { return SEED_VULNS; }
}

export function saveVuln(v: Vulnerability): Vulnerability {
  const existing = loadVulns();
  const isNew = !existing.some(x => x.id === v.id);
  const next = [...existing.filter(x => x.id !== v.id), v];
  try { window.localStorage.setItem(VULN_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  // Best-effort REST sync; silent in demo mode.
  mirrorSaveToBackend(CYBER_VULN_ADAPTER, v, isNew);
  emit();
  return v;
}

export function transitionVuln(id: string, status: VulnStatus): Vulnerability | null {
  const v = loadVulns().find(x => x.id === id);
  if (!v) return null;
  const updated: Vulnerability = { ...v, status };
  if (status === 'CLOSED') updated.closedAt = new Date().toISOString();
  // Mirror through backend's /transition endpoint — server enforces NCA ECC
  // ALLOWED map and computes SLA deadline from severity at TRIAGED.
  mirrorTransitionToBackend(CYBER_VULN_ADAPTER, id, { status });
  return saveVuln(updated);
}

export function saveAsset(a: CyberAsset): CyberAsset {
  const all = loadAssets().filter(x => x.id !== a.id);
  const next = [...all, a];
  try { window.localStorage.setItem(ASSET_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return a;
}

export function createAsset(name: string, descriptionEn: string, descriptionAr: string, tier: AssetTier, owner: string, externalFacing: boolean, location: CyberAsset['location']): CyberAsset {
  const id = `AST-${String(loadAssets().length + 1).padStart(3, '0')}`;
  return saveAsset({ id, name, descriptionEn, descriptionAr, tier, owner, externalFacing, location, lastPatchedAt: new Date().toISOString().slice(0, 10) });
}

export function markPatched(assetId: string): CyberAsset | null {
  const a = loadAssets().find(x => x.id === assetId);
  if (!a) return null;
  return saveAsset({ ...a, lastPatchedAt: new Date().toISOString().slice(0, 10) });
}

export function reclassifyAsset(assetId: string, newTier: AssetTier): CyberAsset | null {
  const a = loadAssets().find(x => x.id === assetId);
  if (!a) return null;
  return saveAsset({ ...a, tier: newTier });
}

export function createThreat(source: ThreatAdvisory['source'], severity: ThreatSeverity, titleEn: string, titleAr: string, summaryEn: string, summaryAr: string): ThreatAdvisory {
  const id = `THR-${new Date().getFullYear()}-${String(loadThreats().length + 1).padStart(3, '0')}`;
  const sourceRef = source === 'NCA-HASEEN' ? `NCA-AD-${new Date().getFullYear()}-${String(Math.floor(Math.random() * 999)).padStart(4, '0')}`
    : source === 'SAMA-ISAC' ? `SAMA-CTI-${new Date().getFullYear()}-${String(Math.floor(Math.random() * 999)).padStart(3, '0')}`
    : `INT-${Date.now()}`;
  const t: ThreatAdvisory = {
    id, source, sourceRef,
    titleEn, titleAr, severity,
    issuedAt: new Date().toISOString(),
    summaryEn, summaryAr,
    affectedProducts: [],
    recommendedActionEn: '', recommendedActionAr: '',
    acknowledged: false,
  };
  return saveThreat(t);
}

export function createSocAlert(severity: SocSeverity, detector: SocAlert['detector'], titleEn: string, titleAr: string, narrativeEn: string, narrativeAr: string, assetId?: string): SocAlert {
  const id = `SOC-${new Date().getFullYear()}-${String(Math.floor(Math.random() * 9999)).padStart(4, '0')}`;
  const a: SocAlert = {
    id, titleEn, titleAr, severity, status: 'OPEN',
    detector, assetId,
    detectedAt: new Date().toISOString(),
    narrativeEn, narrativeAr,
  };
  return saveSocAlert(a);
}

export function schedulePenTest(scope: PenTest['scope'], titleEn: string, titleAr: string, vendor: string, scheduledQuarter: string): PenTest {
  const id = `PT-${scheduledQuarter}-${Math.random().toString(36).slice(2, 5).toUpperCase()}`;
  const p: PenTest = {
    id, titleEn, titleAr, scope, status: 'PLANNED', vendor, scheduledQuarter,
    criticalFindings: 0, highFindings: 0, mediumFindings: 0,
    remediationPct: 0,
    scopeNotesEn: '', scopeNotesAr: '',
  };
  return savePenTest(p);
}

export function savePenTest(p: PenTest): PenTest {
  const all = loadPenTests().filter(x => x.id !== p.id);
  const next = [...all, p];
  try { window.localStorage.setItem(PENTEST_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return p;
}

export function recordPenTestFindings(id: string, critical: number, high: number, medium: number, remediationPct: number): PenTest | null {
  const p = loadPenTests().find(x => x.id === id);
  if (!p) return null;
  return savePenTest({ ...p, criticalFindings: critical, highFindings: high, mediumFindings: medium, remediationPct });
}

export function transitionPenTest(id: string, status: PenTestStatus): PenTest | null {
  const p = loadPenTests().find(x => x.id === id);
  if (!p) return null;
  const updated: PenTest = { ...p, status };
  if (status === 'IN_PROGRESS' && !p.startedAt) updated.startedAt = new Date().toISOString().slice(0, 10);
  if (status === 'CLOSED' && !p.completedAt) updated.completedAt = new Date().toISOString().slice(0, 10);
  return savePenTest(updated);
}

export function loadAssets(): CyberAsset[] {
  if (typeof window === 'undefined') return SEED_ASSETS;
  try {
    const raw = window.localStorage.getItem(ASSET_KEY);
    return raw ? JSON.parse(raw) as CyberAsset[] : SEED_ASSETS;
  } catch { return SEED_ASSETS; }
}

export function loadThreats(): ThreatAdvisory[] {
  if (typeof window === 'undefined') return SEED_THREATS;
  try {
    const raw = window.localStorage.getItem(THREAT_KEY);
    return raw ? JSON.parse(raw) as ThreatAdvisory[] : SEED_THREATS;
  } catch { return SEED_THREATS; }
}

export function saveThreat(t: ThreatAdvisory): ThreatAdvisory {
  const all = loadThreats().filter(x => x.id !== t.id);
  const next = [...all, t];
  try { window.localStorage.setItem(THREAT_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return t;
}

export function acknowledgeThreat(id: string): ThreatAdvisory | null {
  const t = loadThreats().find(x => x.id === id);
  if (!t) return null;
  return saveThreat({ ...t, acknowledged: true, actionedAt: new Date().toISOString() });
}

export function loadSocAlerts(): SocAlert[] {
  if (typeof window === 'undefined') return SEED_SOC;
  try {
    const raw = window.localStorage.getItem(SOC_KEY);
    return raw ? JSON.parse(raw) as SocAlert[] : SEED_SOC;
  } catch { return SEED_SOC; }
}

export function saveSocAlert(a: SocAlert): SocAlert {
  const all = loadSocAlerts().filter(x => x.id !== a.id);
  const next = [...all, a];
  try { window.localStorage.setItem(SOC_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return a;
}

export function transitionSocAlert(id: string, status: SocStatus): SocAlert | null {
  const a = loadSocAlerts().find(x => x.id === id);
  if (!a) return null;
  const updated: SocAlert = { ...a, status };
  const now = new Date().toISOString();
  if (status === 'CONTAINED' || status === 'CLOSED') updated.resolvedAt = now;
  if (status === 'ESCALATED_TO_IR') {
    updated.irRefId = `IR-${new Date().getFullYear()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`;
  }
  return saveSocAlert(updated);
}

export function loadPenTests(): PenTest[] {
  if (typeof window === 'undefined') return SEED_PENTEST;
  try {
    const raw = window.localStorage.getItem(PENTEST_KEY);
    return raw ? JSON.parse(raw) as PenTest[] : SEED_PENTEST;
  } catch { return SEED_PENTEST; }
}

export function subscribeCyber(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  const storage = (e: StorageEvent) => {
    if ([FILING_KEY, VULN_KEY, ASSET_KEY, THREAT_KEY, SOC_KEY, PENTEST_KEY].includes(e.key ?? '')) cb();
  };
  window.addEventListener('storage', storage);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storage);
  };
}

function emit() { try { window.dispatchEvent(new CustomEvent(EVENT)); } catch { /* ignore */ } }

// ─── NCA Haseen filing packet generator ────────────────────────────────────

export function buildHaseenPacket(orgEn: string, orgAr: string): string {
  const filing = loadFiling();
  const vulns = loadVulns();
  const tests = loadPenTests();

  const lines: string[] = [];
  const div = '─'.repeat(64);

  lines.push(div);
  lines.push('NCA HASEEN — ECC v3 ANNUAL SELF-ASSESSMENT FILING');
  lines.push('حصين — التقييم الذاتي السنوي للضوابط الأساسية v3');
  lines.push(div);
  lines.push(`Reporting entity: ${orgEn}`);
  lines.push(`الجهة          : ${orgAr}`);
  lines.push(`Reporting year: ${filing.year}`);
  lines.push(`Filing ID     : ${filing.id}`);
  lines.push(`Status        : ${filing.status}`);
  lines.push(`CISO          : ${filing.ciSpoc}`);
  if (filing.submittedAt) lines.push(`Submitted     : ${filing.submittedAt}`);
  if (filing.ncaAckRef)   lines.push(`NCA ack ref   : ${filing.ncaAckRef}`);
  lines.push('');
  lines.push(`Composite maturity: ${filing.compositeMaturity}/5`);
  lines.push('');

  filing.scores.forEach(s => {
    lines.push(`DOMAIN ${s.domain} — ${s.nameEn}`);
    lines.push(`              ${s.nameAr}`);
    lines.push(`  Maturity     : ${s.maturity === 0 ? 'N/A' : `${s.maturity}/5`}`);
    lines.push(`  Controls     : ${s.controlsTotal} total · ${s.evidencedControls} evidenced · ${s.remediationControls} in remediation`);
    lines.push(`  EN: ${s.narrativeEn}`);
    lines.push(`  AR: ${s.narrativeAr}`);
    lines.push('');
  });

  lines.push('VULNERABILITY MANAGEMENT POSTURE · حالة إدارة الثغرات');
  const sevCounts: Record<VulnSeverity, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
  vulns.forEach(v => sevCounts[v.severity]++);
  Object.entries(sevCounts).forEach(([sev, n]) => lines.push(`  ${sev.padEnd(10)}: ${n}`));
  const overdue = vulns.filter(v => new Date(v.dueDate) < new Date() && v.status !== 'CLOSED').length;
  lines.push(`  Overdue   : ${overdue}`);
  lines.push('');

  lines.push('PENETRATION TEST PROGRAMME · برنامج اختبار الاختراق');
  tests.forEach(t => {
    lines.push(`  [${t.scheduledQuarter}] ${t.scope} — ${t.vendor} · ${t.status}`);
    if (t.completedAt) {
      lines.push(`    Findings: ${t.criticalFindings}C / ${t.highFindings}H / ${t.mediumFindings}M · Remediation ${t.remediationPct}%`);
    }
  });
  lines.push('');

  lines.push(div);
  lines.push('Generated by TSS GRC Platform · upload to NCA Haseen portal');
  lines.push(div);
  return lines.join('\n');
}

// ─── Display helpers ───────────────────────────────────────────────────────

export const FILING_STATUS_LABEL: Record<EccFilingStatus, { en: string; ar: string }> = {
  DRAFT:          { en: 'Draft',           ar: 'مسوّدة' },
  PEER_REVIEW:    { en: 'Peer review',      ar: 'مراجعة الأقران' },
  CISO_APPROVAL:  { en: 'CISO approval',    ar: 'اعتماد CISO' },
  SUBMITTED:      { en: 'Submitted',         ar: 'مُقدَّم' },
  ACCEPTED:       { en: 'Accepted by NCA',  ar: 'مقبول من NCA' },
};

export const FILING_STATUS_TONE: Record<EccFilingStatus, 'neutral' | 'info' | 'warning' | 'success' | 'primary'> = {
  DRAFT: 'neutral', PEER_REVIEW: 'info', CISO_APPROVAL: 'warning', SUBMITTED: 'primary', ACCEPTED: 'success',
};

export const TIER_LABEL: Record<AssetTier, { en: string; ar: string }> = {
  'TIER-1-CRITICAL': { en: 'Tier 1 — Critical',    ar: 'الفئة 1 — حرج' },
  'TIER-2-HIGH':     { en: 'Tier 2 — High',         ar: 'الفئة 2 — عالٍ' },
  'TIER-3-MEDIUM':   { en: 'Tier 3 — Medium',       ar: 'الفئة 3 — متوسّط' },
  'TIER-4-LOW':      { en: 'Tier 4 — Low',          ar: 'الفئة 4 — منخفض' },
};

export const TIER_TONE: Record<AssetTier, 'danger' | 'warning' | 'info' | 'neutral'> = {
  'TIER-1-CRITICAL': 'danger', 'TIER-2-HIGH': 'warning', 'TIER-3-MEDIUM': 'info', 'TIER-4-LOW': 'neutral',
};

export const VULN_SEVERITY_TONE: Record<VulnSeverity, 'danger' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'neutral',
};

export const VULN_STATUS_LABEL: Record<VulnStatus, { en: string; ar: string }> = {
  NEW:                   { en: 'New',                    ar: 'جديدة' },
  TRIAGED:               { en: 'Triaged',                ar: 'مفروزة' },
  OWNER_ASSIGNED:        { en: 'Owner assigned',         ar: 'تم تعيين المالك' },
  IN_REMEDIATION:        { en: 'In remediation',         ar: 'قيد المعالجة' },
  AWAITING_VERIFICATION: { en: 'Awaiting verification',  ar: 'بانتظار التحقق' },
  CLOSED:                { en: 'Closed',                 ar: 'مُغلَقة' },
  ACCEPTED:              { en: 'Risk accepted',          ar: 'مقبول كمخاطرة' },
};

export const VULN_STATUS_TONE: Record<VulnStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  NEW: 'danger', TRIAGED: 'warning', OWNER_ASSIGNED: 'info', IN_REMEDIATION: 'warning',
  AWAITING_VERIFICATION: 'info', CLOSED: 'success', ACCEPTED: 'neutral',
};

export const SOC_SEVERITY_TONE: Record<SocSeverity, 'danger' | 'warning' | 'info' | 'neutral'> = {
  P1: 'danger', P2: 'warning', P3: 'info', P4: 'neutral',
};

export const SOC_STATUS_LABEL: Record<SocStatus, { en: string; ar: string }> = {
  OPEN:             { en: 'Open',              ar: 'مفتوح' },
  INVESTIGATING:    { en: 'Investigating',     ar: 'قيد التحقيق' },
  CONTAINED:        { en: 'Contained',         ar: 'مُحتواة' },
  ESCALATED_TO_IR:  { en: 'Escalated to IR',   ar: 'تصعيد للحوادث' },
  CLOSED:           { en: 'Closed',            ar: 'مُغلَقة' },
};

export const SOC_STATUS_TONE: Record<SocStatus, 'danger' | 'warning' | 'info' | 'success' | 'neutral'> = {
  OPEN: 'danger', INVESTIGATING: 'warning', CONTAINED: 'info', ESCALATED_TO_IR: 'warning', CLOSED: 'success',
};

export const THREAT_SOURCE_LABEL: Record<ThreatAdvisory['source'], { en: string; ar: string }> = {
  'NCA-HASEEN':    { en: 'NCA Haseen',         ar: 'حصين' },
  'KSA-CERT':      { en: 'KSA-CERT',           ar: 'فريق طوارئ الحاسب' },
  'SAMA-ISAC':     { en: 'SAMA ISAC',          ar: 'مركز ساما لمشاركة المعلومات' },
  'TELECOM-ISAC':  { en: 'Telecom ISAC',       ar: 'مركز الاتصالات للمعلومات' },
  'INTERNAL':      { en: 'Internal CTI',       ar: 'استخبارات داخلية' },
};

export const PENTEST_STATUS_LABEL: Record<PenTestStatus, { en: string; ar: string }> = {
  PLANNED:      { en: 'Planned',       ar: 'مخطّط' },
  IN_PROGRESS:  { en: 'In progress',    ar: 'قيد التنفيذ' },
  REPORT_DRAFT: { en: 'Report draft',   ar: 'مسوّدة التقرير' },
  CLOSED:       { en: 'Closed',         ar: 'مُغلَق' },
};

export const PENTEST_STATUS_TONE: Record<PenTestStatus, 'info' | 'warning' | 'neutral' | 'success'> = {
  PLANNED: 'info', IN_PROGRESS: 'warning', REPORT_DRAFT: 'warning', CLOSED: 'success',
};

// ─── Aggregation ────────────────────────────────────────────────────────────

export interface CyberLifecycleStats {
  filingMaturity: number;
  filingStatus: EccFilingStatus;
  vulnsCritical: number;
  vulnsOverdue: number;
  vulnsClosedThisMonth: number;
  threatsAcknowledged: number;
  threatsTotal: number;
  socOpenP1: number;
  socAvgMttrHours: number;
  pentestsCompletedYtd: number;
  assetsExternalFacing: number;
  assetsTier1: number;
}

export function aggregateCyber(): CyberLifecycleStats {
  const filing = loadFiling();
  const vulns = loadVulns();
  const threats = loadThreats();
  const soc = loadSocAlerts();
  const pentests = loadPenTests();
  const assets = loadAssets();
  const monthAgo = Date.now() - 30 * 86_400_000;
  const closedThisMonth = vulns.filter(v => v.status === 'CLOSED' && v.closedAt && new Date(v.closedAt).getTime() > monthAgo).length;
  const closedAlerts = soc.filter(a => a.status === 'CLOSED' && a.resolvedAt);
  const avgMttr = closedAlerts.length > 0
    ? Math.round(closedAlerts.reduce((acc, a) => acc + (new Date(a.resolvedAt!).getTime() - new Date(a.detectedAt).getTime()), 0) / (closedAlerts.length * 3_600_000) * 10) / 10
    : 0;
  const yearStart = new Date(new Date().getFullYear(), 0, 1).getTime();
  return {
    filingMaturity: filing.compositeMaturity,
    filingStatus: filing.status,
    vulnsCritical: vulns.filter(v => v.severity === 'CRITICAL' && v.status !== 'CLOSED').length,
    vulnsOverdue: vulns.filter(v => new Date(v.dueDate) < new Date() && v.status !== 'CLOSED').length,
    vulnsClosedThisMonth: closedThisMonth,
    threatsAcknowledged: threats.filter(t => t.acknowledged).length,
    threatsTotal: threats.length,
    socOpenP1: soc.filter(a => a.severity === 'P1' && a.status !== 'CLOSED').length,
    socAvgMttrHours: avgMttr,
    pentestsCompletedYtd: pentests.filter(p => p.status === 'CLOSED' && p.completedAt && new Date(p.completedAt).getTime() >= yearStart).length,
    assetsExternalFacing: assets.filter(a => a.externalFacing).length,
    assetsTier1: assets.filter(a => a.tier === 'TIER-1-CRITICAL').length,
  };
}
