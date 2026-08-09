import { demoKeyed, demoRows } from '../../lib/demoIds';

// Sample risk data — used when no JWT is present (demo mode). Aligned with
// backend DTOs in lib/api.ts so heat map, KRI dashboard and appetite charts
// all populate without authentication.
import type {
  RiskResponse, KriDefinitionDto, KriBreachEventDto,
  RiskAppetiteStatementDto, AppetiteBreachEventDto,
} from '../../lib/api';

export const MOCK_RISKS: RiskResponse[] = demoRows([
  { id: 1,  riskCode: 'R-0042', titleEn: 'Sensitive Data Breach',     titleAr: 'تسريب بيانات حساسة',
    descriptionEn: 'Unauthorised disclosure of customer-identifying or special-category personal data through web, API, or mailbox compromise. Top NCA ECC + PDPL exposure for the entity.',
    categoryNameEn: 'Cyber',       categoryNameAr: 'سيبراني',
    ownerFullNameEn: 'Ahmed Al-Qahtani', ownerUsername: 'ciso@tss.sa', status: 'OPEN',
    riskLevel: 'CRITICAL',
    inherentLikelihood: 5, inherentImpact: 4, inherentScore: 20,
    residualLikelihood: 3, residualImpact: 3, residualScore: 9,
    identifiedDate: '2025-08-12', reviewDate: '2026-08-12' },
  { id: 2,  riskCode: 'R-0041', titleEn: 'Critical Service Outage',   titleAr: 'انقطاع خدمة حرجة',
    descriptionEn: 'Sustained outage of a Tier-0 customer-facing service breaching the 4-hour MTPD; cascading impact on payments, on-boarding and SLA penalties.',
    categoryNameEn: 'Operational', categoryNameAr: 'تشغيلي',
    ownerFullNameEn: 'Layla Al-Saud', ownerUsername: 'sre.lead@tss.sa', status: 'TREATMENT_IN_PROGRESS',
    riskLevel: 'HIGH',
    inherentLikelihood: 3, inherentImpact: 4, inherentScore: 12,
    residualLikelihood: 2, residualImpact: 3, residualScore: 6,
    identifiedDate: '2025-09-30', reviewDate: '2026-06-30' },
  { id: 3,  riskCode: 'R-0040', titleEn: 'Supplier Concentration',    titleAr: 'تمركز الموردين',
    descriptionEn: 'Over-reliance on a small number of CRITICAL vendors with no documented backup or exit plan; flagged by NRC tier-mapping review.',
    categoryNameEn: 'Third-Party', categoryNameAr: 'أطراف ثالثة',
    ownerFullNameEn: 'Mohammed Al-Otaibi', ownerUsername: 'tprm.lead@tss.sa', status: 'UNDER_ASSESSMENT',
    riskLevel: 'MEDIUM',
    inherentLikelihood: 3, inherentImpact: 3, inherentScore: 9,
    identifiedDate: '2026-01-15', reviewDate: '2026-07-15' },
  { id: 4,  riskCode: 'R-0039', titleEn: 'Phishing Campaign',         titleAr: 'هجمات تصيّد',
    descriptionEn: 'Targeted credential-harvest campaigns against finance and executive accounts. Closed after MFA enforcement and quarterly awareness training reduced click-rate below 3%.',
    categoryNameEn: 'Cyber',       categoryNameAr: 'سيبراني',
    ownerFullNameEn: 'Sara Al-Dosari', ownerUsername: 'soc.lead@tss.sa', status: 'CLOSED',
    riskLevel: 'MEDIUM',
    inherentLikelihood: 3, inherentImpact: 3, inherentScore: 9,
    residualLikelihood: 2, residualImpact: 2, residualScore: 4,
    identifiedDate: '2025-04-22', reviewDate: '2026-04-22' },
  { id: 5,  riskCode: 'R-0038', titleEn: 'Regulatory Non-Compliance', titleAr: 'عدم امتثال تنظيمي',
    descriptionEn: 'Failure to meet a published regulator mandate (NCA ECC v2 transition, SAMA Open Banking phase 3, PDPL 2026 amendments). Direct fine exposure plus reputational risk.',
    categoryNameEn: 'Compliance',  categoryNameAr: 'امتثال',
    ownerFullNameEn: 'Khalid Al-Ghamdi', ownerUsername: 'compliance.lead@tss.sa', status: 'OPEN',
    riskLevel: 'HIGH',
    inherentLikelihood: 3, inherentImpact: 5, inherentScore: 15,
    identifiedDate: '2026-03-10', reviewDate: '2026-09-10' },
  { id: 6,  riskCode: 'R-0037', titleEn: 'Cloud Mis-configuration',   titleAr: 'سوء تهيئة سحابية',
    descriptionEn: 'Public-exposed storage buckets, over-permissive IAM roles, or unencrypted snapshots in the production cloud estate. Active CSPM scanning in place.',
    categoryNameEn: 'Tech',        categoryNameAr: 'تقني',
    ownerFullNameEn: 'Noura Al-Harbi', ownerUsername: 'cloud.platform@tss.sa', status: 'TREATMENT_IN_PROGRESS',
    riskLevel: 'MEDIUM',
    inherentLikelihood: 4, inherentImpact: 2, inherentScore: 8,
    residualLikelihood: 2, residualImpact: 2, residualScore: 4,
    identifiedDate: '2025-11-04', reviewDate: '2026-05-04' },
  { id: 7,  riskCode: 'R-0036', titleEn: 'AML Transaction Screening Gap',
    titleAr: 'فجوة في فحص المعاملات لمكافحة غسل الأموال',
    descriptionEn: 'High-risk customer segment with incomplete sanctions / PEP screening coverage relative to SAMA AML mandate; remediation tracked via R-0036 treatment plan.',
    categoryNameEn: 'Compliance', categoryNameAr: 'امتثال',
    ownerFullNameEn: 'Faisal Al-Maliki', ownerUsername: 'aml.team@tss.sa', status: 'UNDER_ASSESSMENT',
    riskLevel: 'CRITICAL',
    inherentLikelihood: 4, inherentImpact: 5, inherentScore: 20,
    identifiedDate: '2026-02-08', reviewDate: '2026-08-08' },
  { id: 8,  riskCode: 'R-0035', titleEn: 'Insider Misuse of Privileged Access',
    titleAr: 'إساءة استخدام داخلية للصلاحيات',
    descriptionEn: 'Privileged users (DBAs, sysadmins, finance approvers) abusing access for data exfiltration or fraud. Mitigated via PAM session recording and quarterly recertification.',
    categoryNameEn: 'Cyber', categoryNameAr: 'سيبراني',
    ownerFullNameEn: 'Hassan Al-Saud', ownerUsername: 'iam.team@tss.sa', status: 'TREATMENT_IN_PROGRESS',
    riskLevel: 'HIGH',
    inherentLikelihood: 3, inherentImpact: 5, inherentScore: 15,
    residualLikelihood: 2, residualImpact: 4, residualScore: 8,
    identifiedDate: '2025-12-20', reviewDate: '2026-06-20' },
  { id: 9,  riskCode: 'R-0034', titleEn: 'Vendor BCP Inadequacy',
    titleAr: 'قصور في خطط استمرارية أعمال الموردين',
    descriptionEn: 'Tier-1 / Tier-2 vendors without tested business-continuity plans matching our 4-hour MTPD requirement. Audit finding open against ISO 22301 §8.5.',
    categoryNameEn: 'Third-Party', categoryNameAr: 'أطراف ثالثة',
    ownerFullNameEn: 'Sami Al-Qahtani', ownerUsername: 'bcm.team@tss.sa', status: 'OPEN',
    riskLevel: 'HIGH',
    inherentLikelihood: 4, inherentImpact: 4, inherentScore: 16,
    identifiedDate: '2026-01-22', reviewDate: '2026-07-22' },
  { id: 10, riskCode: 'R-0033', titleEn: 'Cross-Border Data Transfer Compliance',
    titleAr: 'الامتثال لنقل البيانات عبر الحدود',
    descriptionEn: 'Customer or HR data transferred to processors outside KSA without an SDAIA-approved transfer mechanism (SCC, BCR, adequacy decision). PDPL Article 29 exposure.',
    categoryNameEn: 'Compliance', categoryNameAr: 'امتثال',
    ownerFullNameEn: 'Mona Al-Bishi', ownerUsername: 'dpo@tss.sa', status: 'UNDER_ASSESSMENT',
    riskLevel: 'CRITICAL',
    inherentLikelihood: 4, inherentImpact: 5, inherentScore: 20,
    residualLikelihood: 3, residualImpact: 4, residualScore: 12,
    identifiedDate: '2026-02-15', reviewDate: '2026-08-15' },
  { id: 11, riskCode: 'R-0032', titleEn: 'IT-OT Convergence Risk',
    titleAr: 'مخاطر تكامل تقنية المعلومات والتشغيل',
    descriptionEn: 'Production IT/OT systems sharing infrastructure or identity stores without proper segmentation; lateral-movement and ransomware exposure.',
    categoryNameEn: 'Operational', categoryNameAr: 'تشغيلي',
    ownerFullNameEn: 'Ahmed Al-Qahtani', ownerUsername: 'ot.security@tss.sa', status: 'OPEN',
    riskLevel: 'HIGH',
    inherentLikelihood: 3, inherentImpact: 4, inherentScore: 12,
    identifiedDate: '2026-03-04', reviewDate: '2026-09-04' },
  { id: 12, riskCode: 'R-0031', titleEn: 'Climate-Driven Operational Risk',
    titleAr: 'مخاطر تشغيلية مرتبطة بالمناخ',
    descriptionEn: 'Heat-wave + flash-flood exposure to physical sites and data centres. Accepted with mitigations: HVAC upgrades, dual-region failover, semi-annual BCP exercise.',
    categoryNameEn: 'Strategic', categoryNameAr: 'استراتيجي',
    ownerFullNameEn: 'Layla Al-Saud', ownerUsername: 'risk.committee@tss.sa', status: 'ACCEPTED',
    riskLevel: 'MEDIUM',
    inherentLikelihood: 3, inherentImpact: 3, inherentScore: 9,
    residualLikelihood: 3, residualImpact: 3, residualScore: 9,
    identifiedDate: '2025-07-10', reviewDate: '2026-07-10' },
]);

// ── KRIs (Key Risk Indicators) ──────────────────────────────────────────────
export const KRIS: KriDefinitionDto[] = demoRows([
  { id: 1001, code: 'KRI-CYB-01', nameEn: 'Failed login attempts (24h)',
    descriptionEn: 'Aggregated failed-login events across all auth systems in the last 24 hours.',
    riskId: 1, unit: 'events', direction: 'HIGHER_IS_WORSE',
    greenMax: 500, amberMax: 1500, targetValue: 200,
    frequency: 'DAILY', owner: 'soc.lead@tss.sa', dataSource: 'SIEM',
    framework: 'NCA_ECC', isActive: true },
  { id: 1002, code: 'KRI-CYB-02', nameEn: 'Critical vulnerabilities open beyond SLA',
    descriptionEn: 'Count of CRITICAL CVEs older than 7 days without remediation.',
    riskId: 1, unit: 'CVEs', direction: 'HIGHER_IS_WORSE',
    greenMax: 0, amberMax: 3, targetValue: 0,
    frequency: 'DAILY', owner: 'patch.team@tss.sa', dataSource: 'Tenable',
    framework: 'NCA_ECC', isActive: true },
  { id: 1003, code: 'KRI-OPS-01', nameEn: 'Critical service downtime (monthly)',
    descriptionEn: 'Aggregate downtime minutes for Tier-0 services this calendar month.',
    riskId: 2, unit: 'minutes', direction: 'HIGHER_IS_WORSE',
    greenMax: 30, amberMax: 120, targetValue: 15,
    frequency: 'MONTHLY', owner: 'sre.team@tss.sa', dataSource: 'StatusPage',
    framework: 'ISO_22301', isActive: true },
  { id: 1004, code: 'KRI-OPS-02', nameEn: 'BCP exercise pass rate (quarterly)',
    descriptionEn: 'Share of BCP exercises scoring ≥ 80% over the quarter.',
    riskId: 2, unit: '%', direction: 'LOWER_IS_WORSE',
    greenMax: 90, amberMax: 75, targetValue: 95,
    frequency: 'QUARTERLY', owner: 'bcm.team@tss.sa', dataSource: 'BCM module',
    framework: 'ISO_22301', isActive: true },
  { id: 1005, code: 'KRI-TPRM-01', nameEn: 'Critical vendors without backup (count)',
    descriptionEn: 'Number of CRITICAL vendors with no documented alternative.',
    riskId: 3, unit: 'vendors', direction: 'HIGHER_IS_WORSE',
    greenMax: 1, amberMax: 3, targetValue: 0,
    frequency: 'MONTHLY', owner: 'tprm.lead@tss.sa', dataSource: 'TPRM module',
    framework: 'ISO_27001', isActive: true },
  { id: 1006, code: 'KRI-COM-01', nameEn: 'Open audit findings beyond due date',
    descriptionEn: 'Findings whose CAPA due date has passed without closure.',
    riskId: 5, unit: 'findings', direction: 'HIGHER_IS_WORSE',
    greenMax: 2, amberMax: 5, targetValue: 0,
    frequency: 'WEEKLY', owner: 'audit.lead@tss.sa', dataSource: 'Audit module',
    framework: 'NCA_ECC', isActive: true },
  { id: 1007, code: 'KRI-PRV-01', nameEn: 'DSR responses past PDPL 30-day SLA',
    descriptionEn: 'Count of Data-Subject Requests not fulfilled within 30 days.',
    riskId: 10, unit: 'DSRs', direction: 'HIGHER_IS_WORSE',
    greenMax: 0, amberMax: 2, targetValue: 0,
    frequency: 'WEEKLY', owner: 'dpo@tss.sa', dataSource: 'Privacy module',
    framework: 'PDPL', isActive: true },
  { id: 1008, code: 'KRI-AML-01', nameEn: 'AML alerts cleared within 5 days (%)',
    descriptionEn: 'Share of AML alerts cleared (false-positive or escalated) within 5 working days.',
    riskId: 7, unit: '%', direction: 'LOWER_IS_WORSE',
    greenMax: 90, amberMax: 75, targetValue: 95,
    frequency: 'WEEKLY', owner: 'aml.team@tss.sa', dataSource: 'AML engine',
    framework: 'SAMA_AML', isActive: true },
]);

// 12-week trend per KRI — feeds the TimeSeries sparkline on each KRI card.
export const KRI_TRENDS_BY_ID: Record<number, number[]> = demoKeyed({
  1001: [ 320, 380, 410, 540, 720, 980, 880, 760, 920, 1100, 1180, 1240 ], // Failed logins (24h) — climbing into amber
  1002: [   0,   0,   1,   0,   2,   0,   1,   0,   0,   0,    0,    0 ], // Critical CVEs open beyond SLA
  1003: [  18,  22,  19,  25,  28,  30,  21,  19,  23,  24,   22,   22 ], // Critical service downtime (mins)
  1004: [  82,  85,  88,  84,  86,  82,  79,  80,  78,  79,   81,   81 ], // BCP exercise pass-rate (%)
  1005: [   3,   3,   3,   3,   3,   3,   3,   4,   4,   4,    4,    4 ], // Critical vendors w/o backup — drifting RED
  1006: [   3,   2,   2,   2,   1,   1,   2,   2,   1,   1,    1,    1 ], // Open audit findings beyond due date
  1007: [   1,   1,   0,   0,   0,   0,   0,   0,   0,   0,    0,    0 ], // DSR responses past PDPL SLA
  1008: [  88,  85,  82,  79,  76,  74,  78,  77,  76,  78,   79,   78 ], // AML alerts cleared within 5d (%)
});

// Latest band per KRI (used by KriDashboard.latest map)
export const KRI_LATEST_BANDS: Record<number, string> = demoKeyed({
  1001: 'AMBER',
  1002: 'GREEN',
  1003: 'GREEN',
  1004: 'AMBER',
  1005: 'RED',
  1006: 'GREEN',
  1007: 'GREEN',
  1008: 'AMBER',
});

// Open breaches per KRI (only RED bands typically have any)
export const KRI_BREACHES: Record<number, KriBreachEventDto[]> = demoKeyed({
  1005: [
    { id: 9001, kriId: 1005, readingId: 88001, breachBand: 'RED', readingValue: 4, thresholdCrossed: 3,
      breachedAt: '2026-04-22T08:30:00', status: 'OPEN' },
  ],
  1001: [
    { id: 9002, kriId: 1001, readingId: 88002, breachBand: 'AMBER', readingValue: 1240, thresholdCrossed: 500,
      breachedAt: '2026-04-19T07:00:00', status: 'ACKNOWLEDGED', acknowledgedAt: '2026-04-19T08:30:00' },
  ],
});

export const KRI_DASHBOARD_STATS = {
  totalKris: 8, green: 4, amber: 3, red: 1, noData: 0, openBreaches: 1,
};

// ── Risk Appetite ───────────────────────────────────────────────────────────
export const APPETITES: RiskAppetiteStatementDto[] = demoRows([
  { id: 5101, titleEn: 'Cybersecurity – Critical Vulnerabilities', titleAr: 'الأمن السيبراني — ثغرات حرجة',
    statementEn: 'We have a MINIMAL appetite for unpatched critical vulnerabilities in production.',
    riskDimension: 'CYBER', appetiteLevel: 'MINIMAL',
    toleranceLower: 0, toleranceUpper: 5, unit: 'open CVEs', currentExposure: 3,
    approvedBy: 'CISO', approvedAt: '2026-01-15T10:00:00', nextReviewDate: '2026-07-15',
    status: 'APPROVED' },
  { id: 5102, titleEn: 'Operational – Critical Service Outages',  titleAr: 'تشغيلي — انقطاع الخدمات الحرجة',
    statementEn: 'Tier-0 services must remain within 99.95% availability monthly.',
    riskDimension: 'OPERATIONAL', appetiteLevel: 'CAUTIOUS',
    toleranceLower: 0, toleranceUpper: 30, unit: 'downtime min', currentExposure: 22,
    approvedBy: 'COO', approvedAt: '2026-02-04T09:30:00', nextReviewDate: '2026-08-04',
    status: 'APPROVED' },
  { id: 5103, titleEn: 'Compliance – Open Regulatory Findings',   titleAr: 'الامتثال — مخالفات تنظيمية مفتوحة',
    statementEn: 'We have an AVERSE appetite for unresolved regulator findings beyond agreed deadlines.',
    riskDimension: 'COMPLIANCE', appetiteLevel: 'AVERSE',
    toleranceLower: 0, toleranceUpper: 0, unit: 'overdue findings', currentExposure: 1,
    approvedBy: 'Chief Compliance Officer', approvedAt: '2026-01-22T11:00:00', nextReviewDate: '2026-07-22',
    status: 'APPROVED' },
  { id: 5104, titleEn: 'Third-Party – Critical Vendor Concentration',
    titleAr: 'الطرف الثالث — تمركز الموردين الحرجين',
    statementEn: 'No single vendor should represent more than 25% of any critical-service spend.',
    riskDimension: 'THIRD_PARTY', appetiteLevel: 'CAUTIOUS',
    toleranceLower: 0, toleranceUpper: 25, unit: '% spend', currentExposure: 32,
    approvedBy: 'CFO', approvedAt: '2025-11-04T14:00:00', nextReviewDate: '2026-05-04',
    status: 'APPROVED' },
  { id: 5105, titleEn: 'Privacy – DSR Response Time',             titleAr: 'الخصوصية — زمن الاستجابة لطلبات أصحاب البيانات',
    statementEn: 'Data subject requests must be fulfilled within the PDPL 30-day window.',
    riskDimension: 'PRIVACY', appetiteLevel: 'AVERSE',
    toleranceLower: 0, toleranceUpper: 30, unit: 'days', currentExposure: 18,
    approvedBy: 'DPO', approvedAt: '2026-02-12T09:00:00', nextReviewDate: '2026-08-12',
    status: 'APPROVED' },
  { id: 5106, titleEn: 'Strategic – New-Product ROI',             titleAr: 'استراتيجي — العائد على الاستثمار للمنتجات الجديدة',
    statementEn: 'New product launches must clear a 12% IRR over a 3-year horizon.',
    riskDimension: 'STRATEGIC', appetiteLevel: 'OPEN',
    toleranceLower: 12, toleranceUpper: 100, unit: '% IRR', currentExposure: 18,
    approvedBy: 'CEO', approvedAt: '2026-03-04T15:00:00', nextReviewDate: '2026-09-04',
    status: 'APPROVED' },
  { id: 5107, titleEn: 'Operational – Insider Threat',            titleAr: 'تشغيلي — تهديد داخلي',
    statementEn: 'Number of insider misuse events tolerated per year.',
    riskDimension: 'OPERATIONAL', appetiteLevel: 'AVERSE',
    toleranceLower: 0, toleranceUpper: 1, unit: 'events/yr', currentExposure: 1,
    approvedBy: 'CISO', approvedAt: '2026-02-22T10:30:00', nextReviewDate: '2026-08-22',
    status: 'APPROVED' },
]);

// Open breaches per appetite statement (only those exceeding tolerance)
export const APPETITE_BREACHES: Record<number, AppetiteBreachEventDto[]> = demoKeyed({
  5104: [
    { id: 8001, appetiteId: 5104, observedValue: 32, breachSeverity: 'EXCEEDED',
      breachedAt: '2026-04-15T12:00:00',
      notes: 'AWS spend share rose to 32% after the regional expansion.', status: 'OPEN' },
  ],
});

export const APPETITE_DASHBOARD_STATS = {
  total: 7, withinTolerance: 5, exceeded: 1, critical: 1, noExposure: 0,
};

// ── Per-risk detail-tab mocks ──────────────────────────────────────────────
// Used by RiskDetailPage tabs (Assessments / Treatments / KRIs / History).
// Keyed by MOCK_RISKS.id.
export interface RiskAssessmentRow {
  id: number;
  date: string;
  assessor: string;
  inherentScore: number;
  residualScore: number | null;
  notes: string;
}
export const RISK_ASSESSMENTS_BY_ID: Record<number, RiskAssessmentRow[]> = demoKeyed({
  1: [
    { id: 1011, date: '2026-04-12', assessor: 'Ahmed Al-Qahtani', inherentScore: 20, residualScore: 9,  notes: 'Quarterly re-assessment after DLP rollout. Compensating controls recognised.' },
    { id: 1012, date: '2026-01-14', assessor: 'Hassan Al-Saud',   inherentScore: 20, residualScore: 12, notes: 'Initial assessment post-incident. Treatment plan approved.' },
    { id: 1013, date: '2025-10-03', assessor: 'Mona Al-Bishi',    inherentScore: 16, residualScore: 12, notes: 'Pre-treatment baseline.' },
  ],
  2: [
    { id: 1021, date: '2026-03-22', assessor: 'Layla Al-Saud',     inherentScore: 12, residualScore: 6, notes: 'Post-failover-drill re-assessment. Residual reduced.' },
    { id: 1022, date: '2025-12-04', assessor: 'Mohammed Al-Otaibi',inherentScore: 12, residualScore: 9, notes: 'Initial.' },
  ],
  3: [
    { id: 1031, date: '2026-02-18', assessor: 'Faisal Al-Maliki', inherentScore: 9,  residualScore: null, notes: 'Awaiting control implementation.' },
  ],
  5: [
    { id: 1051, date: '2026-04-02', assessor: 'Khalid Al-Ghamdi', inherentScore: 15, residualScore: null, notes: 'Open assessment — pending control map alignment.' },
  ],
  7: [
    { id: 1071, date: '2026-04-22', assessor: 'Faisal Al-Maliki', inherentScore: 20, residualScore: null, notes: 'AML rule-pack v2.4 in pilot; residual recalibration deferred.' },
  ],
  8: [
    { id: 1081, date: '2026-04-15', assessor: 'Hassan Al-Saud',   inherentScore: 15, residualScore: 8,  notes: 'CyberArk session-recording extended; residual improving.' },
    { id: 1082, date: '2025-11-08', assessor: 'Hassan Al-Saud',   inherentScore: 15, residualScore: 12, notes: 'Initial.' },
  ],
  10: [
    { id: 1101, date: '2026-04-19', assessor: 'Mona Al-Bishi',    inherentScore: 20, residualScore: 12, notes: 'Awaiting SDAIA cross-border permit; safeguards documented.' },
  ],
});

export interface RiskTreatmentRow {
  id: number;
  type: 'MITIGATE' | 'TRANSFER' | 'ACCEPT' | 'AVOID';
  action: string;
  owner: string;
  status: 'PLANNED' | 'IN_PROGRESS' | 'IMPLEMENTED' | 'CANCELLED';
  dueDate: string;
  progressPct: number;
}
export const RISK_TREATMENTS_BY_ID: Record<number, RiskTreatmentRow[]> = demoKeyed({
  1: [
    { id: 2011, type: 'MITIGATE', action: 'Roll out DLP-tagging on all customer-data sources', owner: 'data.security@tss.sa', status: 'IN_PROGRESS', dueDate: '2026-06-30', progressPct: 60 },
    { id: 2012, type: 'MITIGATE', action: 'Quarterly access recertification on all sensitive stores',  owner: 'iam.team@tss.sa',     status: 'IMPLEMENTED', dueDate: '2026-03-31', progressPct: 100 },
    { id: 2013, type: 'TRANSFER', action: 'Cyber-insurance coverage uplift to USD 25M aggregate',     owner: 'finance.lead@tss.sa', status: 'IMPLEMENTED', dueDate: '2026-01-15', progressPct: 100 },
  ],
  2: [
    { id: 2021, type: 'MITIGATE', action: 'Active-active multi-region deployment for Tier-0 services', owner: 'sre.lead@tss.sa', status: 'IN_PROGRESS', dueDate: '2026-08-31', progressPct: 45 },
    { id: 2022, type: 'MITIGATE', action: 'Quarterly DR failover test with 1h RTO target',             owner: 'bcm.team@tss.sa', status: 'IMPLEMENTED', dueDate: '2026-02-08', progressPct: 100 },
  ],
  3: [
    { id: 2031, type: 'MITIGATE', action: 'Onboard secondary supplier for top 3 critical components', owner: 'tprm.lead@tss.sa', status: 'PLANNED',     dueDate: '2026-09-30', progressPct: 10 },
    { id: 2032, type: 'TRANSFER', action: 'Performance-bond + SLA penalty clauses in master contract', owner: 'procurement@tss.sa', status: 'IMPLEMENTED', dueDate: '2026-02-01', progressPct: 100 },
  ],
  8: [
    { id: 2081, type: 'MITIGATE', action: 'Just-in-time elevation rolled out to all Tier-0 systems',  owner: 'pam.team@tss.sa', status: 'IN_PROGRESS', dueDate: '2026-07-31', progressPct: 60 },
    { id: 2082, type: 'MITIGATE', action: 'Session recording on DBA bastion hosts',                   owner: 'pam.team@tss.sa', status: 'IN_PROGRESS', dueDate: '2026-05-31', progressPct: 80 },
  ],
  10: [
    { id: 2101, type: 'MITIGATE', action: 'Adopt SDAIA Standard Contractual Clauses with all foreign processors', owner: 'dpo@tss.sa', status: 'IN_PROGRESS', dueDate: '2026-06-30', progressPct: 35 },
    { id: 2102, type: 'AVOID',    action: 'Migrate PDPL Tier-2 data to in-Kingdom STC sovereign cloud',          owner: 'cio.office@tss.sa', status: 'IN_PROGRESS', dueDate: '2026-12-31', progressPct: 25 },
  ],
});

export interface RiskKriRow {
  id: number;
  code: string;
  nameEn: string;
  band: 'GREEN' | 'AMBER' | 'RED' | 'NO_DATA';
  current: number;
  unit: string;
  greenMax: number;
  amberMax: number;
}
export const RISK_KRIS_BY_ID: Record<number, RiskKriRow[]> = demoKeyed({
  1: [
    { id: 1001, code: 'KRI-CYB-01', nameEn: 'Failed login attempts (24h)',          band: 'AMBER', current: 1240, unit: 'events',  greenMax: 500, amberMax: 1500 },
    { id: 1002, code: 'KRI-CYB-02', nameEn: 'Critical vulnerabilities open beyond SLA', band: 'GREEN', current: 0,    unit: 'CVEs',    greenMax: 0,   amberMax: 3 },
  ],
  2: [
    { id: 1003, code: 'KRI-OPS-01', nameEn: 'Critical service downtime (monthly)',     band: 'GREEN', current: 22,   unit: 'minutes', greenMax: 30,  amberMax: 120 },
    { id: 1004, code: 'KRI-OPS-02', nameEn: 'BCP exercise pass rate (quarterly)',     band: 'AMBER', current: 81,   unit: '%',       greenMax: 90,  amberMax: 75 },
  ],
  3: [
    { id: 1005, code: 'KRI-TPRM-01', nameEn: 'Critical vendors without backup (count)', band: 'RED',   current: 4,    unit: 'vendors', greenMax: 1,   amberMax: 3 },
  ],
  5: [
    { id: 1006, code: 'KRI-COM-01', nameEn: 'Open audit findings beyond due date',     band: 'GREEN', current: 1,    unit: 'findings', greenMax: 2, amberMax: 5 },
  ],
  10: [
    { id: 1007, code: 'KRI-PRV-01', nameEn: 'DSR responses past PDPL 30-day SLA',      band: 'GREEN', current: 0,    unit: 'DSRs',    greenMax: 0,   amberMax: 2 },
  ],
  7: [
    { id: 1008, code: 'KRI-AML-01', nameEn: 'AML alerts cleared within 5 days (%)',    band: 'AMBER', current: 78,   unit: '%',       greenMax: 90,  amberMax: 75 },
  ],
});

export interface RiskHistoryEvent {
  id: number;
  at: string;
  actor: string;
  type: 'CREATED' | 'STATUS_CHANGE' | 'SCORE_CHANGE' | 'TREATMENT_ADDED' | 'COMMENT' | 'ATTACHMENT' | 'REVIEW';
  message: string;
}
export const RISK_HISTORY_BY_ID: Record<number, RiskHistoryEvent[]> = demoKeyed({
  1: [
    { id: 5011, at: '2026-04-12T10:30:00', actor: 'Ahmed Al-Qahtani', type: 'REVIEW',          message: 'Quarterly re-assessment completed. Residual recalibrated 12 → 9.' },
    { id: 5012, at: '2026-04-08T14:00:00', actor: 'Mona Al-Bishi',     type: 'COMMENT',         message: 'DLP coverage now 92% — recommend closing residual review.' },
    { id: 5013, at: '2026-03-22T09:15:00', actor: 'data.security@tss.sa', type: 'TREATMENT_ADDED', message: 'Added "DLP-tagging rollout" treatment.' },
    { id: 5014, at: '2026-01-14T11:45:00', actor: 'Hassan Al-Saud',    type: 'STATUS_CHANGE',   message: 'Status changed UNDER_ASSESSMENT → APPROVED_FOR_TREATMENT.' },
    { id: 5015, at: '2025-10-03T08:00:00', actor: 'Risk Manager',      type: 'CREATED',         message: 'Risk registered after DLP gap audit.' },
  ],
  2: [
    { id: 5021, at: '2026-03-22T16:00:00', actor: 'Layla Al-Saud',     type: 'REVIEW',          message: 'Post-failover-drill assessment.' },
    { id: 5022, at: '2026-02-08T18:00:00', actor: 'Ahmed Al-Qahtani',  type: 'COMMENT',         message: 'Datacenter failover drill: RTO 54 min vs 1h target.' },
    { id: 5023, at: '2025-12-04T10:00:00', actor: 'Mohammed Al-Otaibi',type: 'CREATED',         message: 'Risk registered.' },
  ],
  8: [
    { id: 5081, at: '2026-04-15T15:30:00', actor: 'Hassan Al-Saud',    type: 'SCORE_CHANGE',    message: 'Residual reduced 12 → 8 after PAM expansion.' },
    { id: 5082, at: '2026-04-15T15:00:00', actor: 'pam.team@tss.sa',   type: 'TREATMENT_ADDED', message: 'Session recording on DBA bastion hosts.' },
    { id: 5083, at: '2025-11-08T10:00:00', actor: 'Hassan Al-Saud',    type: 'CREATED',         message: 'Risk registered after CSIRT review.' },
  ],
});

export interface RiskLinkRow {
  id: number;
  type: 'CONTROL' | 'FINDING' | 'POLICY' | 'INCIDENT' | 'KRI' | 'CHANGE';
  ref: string;
  title: string;
  href: string;
  status?: string;
}
export const RISK_LINKS_BY_ID: Record<number, RiskLinkRow[]> = demoKeyed({
  1: [
    { id: 7011, type: 'CONTROL', ref: 'A.5.34',     title: 'Privacy + protection of PII',          href: '/controls/1', status: 'IMPLEMENTED' },
    { id: 7012, type: 'CONTROL', ref: 'AC-2.1.2',   title: 'Privileged access review',             href: '/controls/3', status: 'PARTIAL' },
    { id: 7013, type: 'POLICY',  ref: 'POL-PDPL-001', title: 'Privacy & Personal Data Policy',      href: '/policies/52' },
    { id: 7014, type: 'KRI',     ref: 'KRI-CYB-01', title: 'Failed login attempts (24h)',          href: '/risk?tab=kri', status: 'AMBER' },
  ],
  2: [
    { id: 7021, type: 'CONTROL', ref: '8.4',        title: 'Continuity strategies',                href: '/controls/8',  status: 'PARTIAL' },
    { id: 7022, type: 'INCIDENT',ref: 'INC-2026-001', title: 'Datacenter failover drill (Q1)',     href: '/incidents' },
  ],
  8: [
    { id: 7081, type: 'CONTROL', ref: 'AC-2.1.2',   title: 'Privileged access review',             href: '/controls/3', status: 'PARTIAL' },
    { id: 7082, type: 'FINDING', ref: 'AUD-F-032',  title: 'PAM session recording not extended',    href: '/findings/32' },
    { id: 7083, type: 'CHANGE',  ref: 'CHG-2026-012', title: 'Roll out MFA for all VPN users',     href: '/changes' },
  ],
});
