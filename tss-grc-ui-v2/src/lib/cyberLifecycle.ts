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

// ⚠⚠ F2-205: this file holds SIX corpora and exactly ONE of them is now server-backed.
//
//   VULNERABILITIES  → SERVER (`/api/cyber-lc/vulnerabilities`, `cyber_lc.vulnerabilities`,
//                      36 rows in dev AND prod). Wired by F2-205.
//   filing / assets / threats / soc / pentests → still BROWSER_LOCAL.
//
// The five that remain are declared, with their real endpoints and measured row counts, in
// `lib/lifecycleStores.ts`, and that declaration is gated. Do not add a sixth localStorage
// corpus here: `lifecycleStoreContract.test.ts` derives its population from the `grc.*.v1`
// keys this file writes, so a new one fails the build until it is declared.

import { vulnLifecycleApi, ApiError, type VulnLifecycleDto } from './api';

// ─── Types ──────────────────────────────────────────────────────────────────

export type EccDomain = '1' | '2' | '3' | '4' | '5';
export type EccMaturity = 0 | 1 | 2 | 3 | 4 | 5;
export type EccFilingStatus = 'DRAFT' | 'PEER_REVIEW' | 'CISO_APPROVAL' | 'SUBMITTED' | 'ACCEPTED';

export type AssetTier = 'TIER-1-CRITICAL' | 'TIER-2-HIGH' | 'TIER-3-MEDIUM' | 'TIER-4-LOW';

export type VulnSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

/**
 * ⚠⚠ F2-205 CORRECTED. This union used to end `| 'CLOSED' | 'ACCEPTED'` — and both halves of
 * that were wrong against the server it now talks to.
 *
 *   • `ACCEPTED` is not a state `VulnerabilityService` has. The server's word is `RISK_ACCEPTED`,
 *     and its `TransitionRequest.@Pattern` refuses anything else — so every button offering
 *     `ACCEPTED` would have 409'd the moment this store was wired, from every state.
 *   • `FALSE_POSITIVE` was absent ENTIRELY. It is a lawful NCA ECC disposition with its own
 *     bilingual reason columns, reachable from `NEW` and from `TRIAGED`.
 *
 * Between them, **7 of the 36 live rows carried a status this file could not name** (4
 * RISK_ACCEPTED + 3 FALSE_POSITIVE, identical in dev and prod). F2-203's `audit_lc` shape
 * INVERTED: there the register held statuses the server's machine did not declare; here the
 * server was right and the CLIENT was short of it — which is worse in one specific way, because
 * a client vocabulary that omits a state does not merely mislabel a row, it removes the
 * operator's ability to reach it at all. Marking a vulnerability a false positive was not a
 * thing this platform could do.
 */
export type VulnStatus =
  | 'NEW' | 'TRIAGED' | 'OWNER_ASSIGNED' | 'IN_REMEDIATION' | 'AWAITING_VERIFICATION'
  | 'RISK_ACCEPTED' | 'FALSE_POSITIVE' | 'CLOSED';

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

/**
 * A row of `cyber_lc.vulnerabilities`, as `VulnerabilityDto` puts it on the wire.
 *
 * ⚠ F2-205 renamed four fields and dropped one, so the type says what the server says:
 *   `discoveredAt` → `detectedAt`, `dueDate` → `slaDeadline`, `currentOwner` → `owner`,
 *   `notesEn/Ar` → `descriptionEn/Ar`.
 *
 * ⚠⚠ `slaDays` is DELETED rather than renamed, and that is the interesting one. There is no such
 * column: the server computes the deadline once, at TRIAGED, from the NCA ECC severity matrix
 * (`SLA_DAYS` — CRITICAL 7 / HIGH 30 / MEDIUM 60 / LOW 90) and stores only `sla_deadline`. The
 * table used to render `SLA {v.slaDays}d` beside the due date, so re-deriving it here would mean
 * a SECOND copy of that matrix in the browser — which is the exact defect class this slice
 * exists to close. The deadline is the row's fact; the matrix is the server's policy.
 */
export interface Vulnerability {
  /** The server-minted external reference (`VULN-…`). Never client-chosen — see F2-205. */
  id: string;
  cveId?: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  /** Linked asset id (`cyber_lc.assets.external_id`-shaped, free text on the column). */
  assetId: string;
  cvssScore?: number;
  severity: VulnSeverity;
  status: VulnStatus;
  detectionSource?: string;
  detectedAt?: string;
  /** The ECC remediation deadline, stamped at TRIAGED. Absent until then. */
  slaDeadline?: string;
  triagedAt?: string;
  /** Stamped by the server from the authenticated principal — never sent by a client. */
  triagedBy?: string;
  ownerAssignedAt?: string;
  owner?: string;
  remediationStartedAt?: string;
  remediationMethodEn?: string;
  remediationMethodAr?: string;
  verificationAt?: string;
  verifiedBy?: string;
  closedAt?: string;
  /** How many times this row has already breached its deadline. 8 of 36 carry 1. */
  breachCount?: number;
  slaExtended?: boolean;
  /** Who accepted the exposure. NULL on all four RISK_ACCEPTED rows today — see F2-205. */
  riskAcceptedBy?: string;
  riskAcceptanceExpiry?: string;
  riskAcceptanceReasonEn?: string;
  riskAcceptanceReasonAr?: string;
  falsePositiveReasonEn?: string;
  falsePositiveReasonAr?: string;
  threatIntelId?: string;
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
// F2-205: `VULN_KEY = 'grc.cyber.vuln.v1'` is DELETED. The register is served; nothing about it
// is kept in the browser. `lifecycleStoreContract.test.ts` derives its browser-local population
// from the `grc.*.v1` literals in this file, so removing the key is what moves the store's state
// in that registry — the declaration cannot drift from the code.
const ASSET_KEY = 'grc.cyber.asset.v1';
const THREAT_KEY = 'grc.cyber.threat.v1';
const SOC_KEY = 'grc.cyber.soc.v1';
const PENTEST_KEY = 'grc.cyber.pentest.v1';
const EVENT = 'grc.cyber.lifecycle.changed';

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString();
// F2-205: `_daysAhead` is deleted — its only callers were `SEED_VULNS`' invented due dates.
// `_daysAgo` survives because four browser-local corpora still remain in this file (filing,
// assets, threats, soc, pentests); it is a clock-minted timestamp of exactly the F2-194 kind and
// goes when the last of them is wired.

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

// ⚠⚠ F2-205: `SEED_VULNS` — six invented vulnerabilities — is DELETED.
//
// It carried CVE numbers, CVSS scores, named owners ('Head of Digital', 'CIO') and remediation
// narratives ('Patch tested in staging. Production deployment scheduled for tonight 2am-3am
// window.'), all minted relative to `Date.now()` at module load so they could never look stale
// — F2-194's clock-anchored fabrication, which has no tell. Meanwhile `cyber_lc.vulnerabilities`
// held 36 real rows in dev and prod behind a complete, hardened controller with no client.
//
// 🔑 Two measurements say what the corpus was doing. Of the 36 real rows, `remediation_method`
// is populated on ZERO — so the confident remediation notes were not a stale copy of anything,
// they were invented outright. And 31 of the 36 are past their SLA deadline and not closed,
// while the six invented rows showed one overdue: the fabrication was not merely wrong, it was
// wrong in the flattering direction, which is the third time in this programme after F2-193's
// connector cadences and F2-195's KPI tiles.

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

// ─── Vulnerability register — SERVER-BACKED (F2-205) ───────────────────────

/**
 * The NCA ECC remediation machine, mirroring `VulnerabilityService.ALLOWED` edge for edge.
 *
 * `VulnerabilityMachineParityTest` compares this table against the server's by reflection and
 * fails the build on any divergence, in BOTH directions. It exists because this workspace used to
 * hold THREE incompatible ideas of the same machine and none of them was the server's:
 *
 *   1. `VulnStatus` — 7 states, one of which (`ACCEPTED`) the server does not have, and missing
 *      `FALSE_POSITIVE` entirely.
 *   2. `CyberLifecyclePage`'s pipeline `onAdvance` — a LINEAR `Record<string, VulnStatus>` from
 *      NEW to CLOSED with no branches at all, so neither risk acceptance nor false-positive
 *      disposition was reachable from the pipeline.
 *   3. `LifecycleOverviewPage`'s `stages` array — six codes, the same two omissions.
 *
 * 🔑 F2-196's question — "how many things read this status, and do they agree?" — answered four
 * (counting the server) with four different vocabularies. What let it survive is that all three
 * CLIENT vocabularies agreed with each other about the happy path, so any two of them compared
 * side by side looked consistent. The one they all disagreed with was the only one that decides.
 */
export const VULN_ALLOWED: Record<string, readonly VulnStatus[]> = {
  NEW:                   ['TRIAGED', 'FALSE_POSITIVE'],
  TRIAGED:               ['OWNER_ASSIGNED', 'RISK_ACCEPTED', 'FALSE_POSITIVE'],
  OWNER_ASSIGNED:        ['IN_REMEDIATION', 'RISK_ACCEPTED'],
  IN_REMEDIATION:        ['AWAITING_VERIFICATION'],
  AWAITING_VERIFICATION: ['CLOSED', 'IN_REMEDIATION'],
  RISK_ACCEPTED:         ['CLOSED'],
  FALSE_POSITIVE:        ['CLOSED'],
};

/**
 * DERIVED from {@link VULN_ALLOWED} — the states that are reachable and have no outgoing edges.
 * Never typed a second time: F2-196's rule, learned where an inclusion list over the PDPL DSR
 * vocabulary meant the extended statutory deadline could never be enforced. The server derives
 * its own the same way (`VulnerabilityService.TERMINAL`, which F2-205 also converted from a
 * hand-written `List.of("CLOSED")`).
 */
export const VULN_TERMINAL: readonly string[] =
  [...new Set(Object.values(VULN_ALLOWED).flat())]
    .filter(st => (VULN_ALLOWED[st] ?? []).length === 0);

/**
 * Every state the register can hold, DERIVED — keys plus targets, in declaration order.
 *
 * ⚠ F2-205: the vulnerability vocabulary was restated FIVE times across this codebase and all
 * five client copies shared the same two omissions. Anything that needs the full list — a filter's
 * options, a pipeline's stage columns, a legend — must derive it rather than type a sixth.
 */
export const ALL_VULN_STATUSES: readonly VulnStatus[] = [
  ...Object.keys(VULN_ALLOWED) as VulnStatus[],
  ...(Object.values(VULN_ALLOWED).flat() as VulnStatus[])
    .filter(s => !Object.prototype.hasOwnProperty.call(VULN_ALLOWED, s)),
].filter((s, i, a) => a.indexOf(s) === i);

/** The legal next states — empty for a terminal state AND for one the machine does not declare. */
export function nextVulnStatuses(status: string): readonly VulnStatus[] {
  return VULN_ALLOWED[status] ?? [];
}

/**
 * The single step a one-button "advance" control means — the REMEDIATION path, declared.
 *
 * ⚠⚠ The pipeline needs one target per stage, and the machine branches: `NEW` can go to TRIAGED
 * or FALSE_POSITIVE, `TRIAGED` to OWNER_ASSIGNED or RISK_ACCEPTED or FALSE_POSITIVE. Three ways
 * to resolve that are wrong:
 *
 *   • Take `nextVulnStatuses(s)[0]`. That makes the ARRAY ORDER of `VULN_ALLOWED` silently
 *     load-bearing — F2-190's defect exactly, where a lifecycle hop picked by whichever state a
 *     `Set` yielded first became a control nobody had chosen.
 *   • Advance only when there is exactly one successor. Then `NEW` and `TRIAGED` never advance
 *     from the pipeline at all, which is most of the register.
 *   • Keep the page's own linear map. That is what was there: a `Record<string, VulnStatus>`
 *     inside `onAdvance`, a third vocabulary, with no branch to risk acceptance or false positive
 *     anywhere in it.
 *
 * So it is declared here, once, and `vulnerabilityLifecycle.test.ts` asserts every entry is a
 * legal edge of {@link VULN_ALLOWED} — a shortcut through the machine, never around it. The
 * dispositions that are judgements rather than progress (RISK_ACCEPTED, FALSE_POSITIVE) are
 * deliberately NOT reachable from a one-click control: they need a reason from the deciding
 * officer, which the detail table collects.
 */
export const VULN_PRIMARY_PATH: Record<string, VulnStatus> = {
  NEW:                   'TRIAGED',
  TRIAGED:               'OWNER_ASSIGNED',
  OWNER_ASSIGNED:        'IN_REMEDIATION',
  IN_REMEDIATION:        'AWAITING_VERIFICATION',
  AWAITING_VERIFICATION: 'CLOSED',
  RISK_ACCEPTED:         'CLOSED',
  FALSE_POSITIVE:        'CLOSED',
};

/** The remediation-path successor, or null where a one-click advance has no honest meaning. */
export function primaryNextVulnStatus(status: string): VulnStatus | null {
  return VULN_PRIMARY_PATH[status] ?? null;
}

/** Does the machine know this status at all? A terminal state is managed but has no successors. */
export function isVulnStatusManaged(status: string): boolean {
  return Object.prototype.hasOwnProperty.call(VULN_ALLOWED, status)
    || VULN_TERMINAL.includes(status);
}

/**
 * "Open" as the COMPLEMENT of the derived terminal set, never a second inclusion list.
 *
 * Every overdue/critical count in this file used to read `v.status !== 'CLOSED'`, which is the
 * same answer today and stops being so the moment a second terminal state is added — and the
 * product argument for making `RISK_ACCEPTED` terminal is a real one. With a complement, an
 * unrecognised status is chased rather than excused, which for an ECC remediation clock is the
 * safe direction.
 */
export function isVulnOpen(status: string): boolean { return !VULN_TERMINAL.includes(status); }

/** Past its ECC deadline and not finished. No deadline = not yet triaged = not yet overdue. */
export function isVulnOverdue(v: Vulnerability, now = new Date()): boolean {
  return !!v.slaDeadline && isVulnOpen(v.status) && new Date(v.slaDeadline) < now;
}

/** Server rows, cached. Empty until {@link hydrateVulns} resolves. */
let _vulns: Vulnerability[] = [];
let _vulnsHydrated = false;
let _vulnsLoadFailed = false;
let _vulnsPromise: Promise<void> | null = null;

function fromVulnDto(d: VulnLifecycleDto): Vulnerability {
  return {
    id: d.id,
    cveId: d.cveId ?? undefined,
    titleEn: d.titleEn,
    titleAr: d.titleAr,
    descriptionEn: d.descriptionEn ?? undefined,
    descriptionAr: d.descriptionAr ?? undefined,
    assetId: d.assetId,
    cvssScore: d.cvssScore ?? undefined,
    severity: d.severity as VulnSeverity,
    status: d.status as VulnStatus,
    detectionSource: d.detectionSource ?? undefined,
    detectedAt: d.detectedAt ?? undefined,
    slaDeadline: d.slaDeadline ?? undefined,
    triagedAt: d.triagedAt ?? undefined,
    triagedBy: d.triagedBy ?? undefined,
    ownerAssignedAt: d.ownerAssignedAt ?? undefined,
    owner: d.owner ?? undefined,
    remediationStartedAt: d.remediationStartedAt ?? undefined,
    remediationMethodEn: d.remediationMethodEn ?? undefined,
    remediationMethodAr: d.remediationMethodAr ?? undefined,
    verificationAt: d.verificationAt ?? undefined,
    verifiedBy: d.verifiedBy ?? undefined,
    closedAt: d.closedAt ?? undefined,
    breachCount: d.breachCount ?? undefined,
    slaExtended: d.slaExtended ?? undefined,
    riskAcceptedBy: d.riskAcceptedBy ?? undefined,
    riskAcceptanceExpiry: d.riskAcceptanceExpiry ?? undefined,
    riskAcceptanceReasonEn: d.riskAcceptanceReasonEn ?? undefined,
    riskAcceptanceReasonAr: d.riskAcceptanceReasonAr ?? undefined,
    falsePositiveReasonEn: d.falsePositiveReasonEn ?? undefined,
    falsePositiveReasonAr: d.falsePositiveReasonAr ?? undefined,
    threatIntelId: d.threatIntelId ?? undefined,
  };
}

/**
 * Fetch the tenant's remediation register. Idempotent — concurrent callers share one request,
 * which matters because three separate pages hydrate this store.
 *
 * A 403 is not a load failure: a persona without `CYBER:READ` is correctly refused, and the page
 * should show an empty register rather than an error banner blaming the server.
 */
export function hydrateVulns(force = false): Promise<void> {
  if (_vulnsPromise && !force) return _vulnsPromise;
  _vulnsLoadFailed = false;
  _vulnsPromise = vulnLifecycleApi.list()
    .then(rows => { _vulns = rows.map(fromVulnDto); _vulnsHydrated = true; emit(); })
    .catch(e => {
      if (!(e instanceof ApiError) || e.status !== 403) _vulnsLoadFailed = true;
      console.warn('[cyberLifecycle] vulnerability fetch failed:', e);
      _vulnsHydrated = true;
      emit();
    });
  return _vulnsPromise;
}

export function areVulnsHydrated(): boolean { return _vulnsHydrated; }
export function vulnsLoadFailed(): boolean { return _vulnsLoadFailed; }

export function loadVulns(): Vulnerability[] { return _vulns; }

/**
 * Advance a vulnerability through the ECC machine — SERVER-SIDE — and replace the cached row with
 * whatever the server returns.
 *
 * ⚠⚠ This used to be `{ ...v, status }` written into `localStorage`, with NO transition table of
 * any kind. Two consequences, both live until F2-205:
 *
 *   • Nothing was persisted. An analyst closed a CRITICAL vulnerability, saw a success toast, and
 *     a reload put it back — while the real row sat untouched in a register no screen read.
 *   • The browser permitted transitions the machine forbids, including `NEW -> CLOSED`: a critical
 *     exposure marked closed having never been triaged, assigned, remediated or verified, with no
 *     `verified_by` and no audit row. That is the ECC remediation control itself, absent.
 *
 * 🔑 Replacing the row from the server's response is what makes both halves impossible to
 * reintroduce: there is no local status to write, and no second machine to consult.
 */
export async function transitionVuln(
  id: string,
  status: VulnStatus,
  detail?: {
    owner?: string;
    remediationMethodEn?: string;
    remediationMethodAr?: string;
    riskAcceptanceExpiry?: string;
    riskAcceptanceReasonEn?: string;
    riskAcceptanceReasonAr?: string;
    falsePositiveReasonEn?: string;
    falsePositiveReasonAr?: string;
  },
): Promise<Vulnerability> {
  const saved = fromVulnDto(await vulnLifecycleApi.transition(id, { status, ...detail }));
  _vulns = _vulns.map(v => (v.id === id ? saved : v));
  emit();
  return saved;
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
    // F2-205: VULN_KEY is gone from this list along with the key itself. Cross-tab sync for the
    // vulnerability register now comes from the server's own broadcast — see the note on
    // `useRealtimeOrgEvent('changed.vulnerability')` in VulnerabilitiesV2Page.
    if ([FILING_KEY, ASSET_KEY, THREAT_KEY, SOC_KEY, PENTEST_KEY].includes(e.key ?? '')) cb();
  };
  window.addEventListener('storage', storage);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storage);
  };
}

function emit() { try { window.dispatchEvent(new CustomEvent(EVENT)); } catch { /* ignore */ } }

// ─── The NCA Haseen submission generator — RETIRED (F2-205) ────────────────
//
// ⚠⚠ `buildHaseenPacket()` and `buildHaseenSheets()` were here, and both are DELETED.
//
// Together they produced the "NCA HASEEN — ECC v3 ANNUAL SELF-ASSESSMENT FILING": a cover naming
// the reporting entity, per-domain maturity scores, an "evidenced controls" count, a
// VULNERABILITY MANAGEMENT POSTURE section, the pen-test programme, and the footer "Generated by
// TSS GRC Platform · upload to NCA Haseen portal". `CyberLifecyclePage` emitted it FOUR ways: a
// `.txt` download, a print-to-PDF stamped "CONFIDENTIAL — REGULATOR" with a reference id, and
// branded .xlsx / .docx through the platform's own export subsystem.
//
// Every figure in it came from this file's browser constants.
//
// 🔑 This is the SAME fabricated ECC return F2-199 retired at `/nca-ecc-export`, reached through
// a second door — and the reason F2-199 did not find it is the reusable part. Its
// `exportProvenance` gate asks whether a page that emits a document has server-derived data in
// scope. This page does: `reportingApi.packetXlsx(payload)` and `reportingApi.packetDocx(payload)`
// are the ONLY two `SERVER_CALL` tokens on it, and both take the fabricated payload as their
// ARGUMENT. **The server call the provenance gate found is the one that carries the invented
// figures out.** A gate that asks "does this page talk to a server?" cannot tell an inbound read
// from an outbound write, and an export endpoint is the one place where the difference is the
// whole question. `exportProvenance.test.ts` now separates them.
//
// Retiring rather than rewiring is ADR-017 §D-5, ratified for this shape in F2-42 and applied
// again in F2-198 and F2-199: `nca_ecc.filings` holds 4 real filings with per-domain scores, peer
// review, CISO approval and an NCA submission reference, and `NcaEccFilingsPage` already renders
// all of it at `/nca-ecc/filings`. Rebuilding this packet from that register is NOT a rewire:
// F2-199 measured `responses_json` NULL on all 4 filings in both databases, so the per-control
// detail underneath the domain scores does not exist to be rendered, and inventing it is
// F2-196's defect. A server-side ECC export generator is filed as the real fix.
//
// ⚠ The vulnerability section was the one part this slice COULD have made real, and that is
// exactly why it had to go with the rest: a document whose posture table is server-derived and
// whose ECC maturity scores are invented is F2-203's self-contradicting export — worse than a
// uniformly fabricated one, because the accurate half vouches for the other.

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

export const VULN_SEVERITY_TONE: Record<VulnSeverity, 'risk-crit' | 'risk-high' | 'risk-med' | 'risk-low'> = {
  CRITICAL: 'risk-crit', HIGH: 'risk-high', MEDIUM: 'risk-med', LOW: 'risk-low',
};

/**
 * Bilingual labels for all EIGHT server states. `ACCEPTED` was renamed to the server's
 * `RISK_ACCEPTED` and `FALSE_POSITIVE` added — until F2-205 seven of the 36 live rows rendered
 * with no label at all, and `VULN_STATUS_LABEL[s][lang]` on an unmapped status is a TypeError of
 * exactly the kind F2-203 found three of in `audit_lc`. Read it through {@link vulnStatusLabel},
 * never by direct index.
 */
export const VULN_STATUS_LABEL: Record<VulnStatus, { en: string; ar: string }> = {
  NEW:                   { en: 'New',                    ar: 'جديدة' },
  TRIAGED:               { en: 'Triaged',                ar: 'مفروزة' },
  OWNER_ASSIGNED:        { en: 'Owner assigned',         ar: 'تم تعيين المالك' },
  IN_REMEDIATION:        { en: 'In remediation',         ar: 'قيد المعالجة' },
  AWAITING_VERIFICATION: { en: 'Awaiting verification',  ar: 'بانتظار التحقق' },
  RISK_ACCEPTED:         { en: 'Risk accepted',          ar: 'مقبولة كمخاطرة' },
  FALSE_POSITIVE:        { en: 'False positive',         ar: 'إنذار كاذب' },
  CLOSED:                { en: 'Closed',                 ar: 'مُغلَقة' },
};

/**
 * Total-function label lookup. A status the map does not carry returns the raw token rather than
 * throwing — the F2-203 rule, where a `MAP[row.status][lang]` crash was held shut by a filter
 * that removed every row, and fixing the filter is what detonated it in the browser.
 */
export function vulnStatusLabel(status: string, lang: 'en' | 'ar'): string {
  const e = VULN_STATUS_LABEL[status as VulnStatus];
  return e ? e[lang] : status;
}

export const VULN_STATUS_TONE: Record<VulnStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  NEW: 'danger', TRIAGED: 'warning', OWNER_ASSIGNED: 'info', IN_REMEDIATION: 'warning',
  AWAITING_VERIFICATION: 'info', RISK_ACCEPTED: 'neutral', FALSE_POSITIVE: 'neutral',
  CLOSED: 'success',
};

/** Total-function tone lookup, for the same reason as {@link vulnStatusLabel}. */
export function vulnStatusTone(status: string): 'neutral' | 'info' | 'warning' | 'success' | 'danger' {
  return VULN_STATUS_TONE[status as VulnStatus] ?? 'neutral';
}

export const SOC_SEVERITY_TONE: Record<SocSeverity, 'risk-crit' | 'risk-high' | 'risk-med' | 'risk-low'> = {
  P1: 'risk-crit', P2: 'risk-high', P3: 'risk-med', P4: 'risk-low',
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
    // F2-205: `isVulnOpen` is the complement of the DERIVED terminal set, and `isVulnOverdue`
    // reads the server's `slaDeadline` (there is no `dueDate` — see the Vulnerability interface).
    vulnsCritical: vulns.filter(v => v.severity === 'CRITICAL' && isVulnOpen(v.status)).length,
    vulnsOverdue: vulns.filter(v => isVulnOverdue(v)).length,
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
