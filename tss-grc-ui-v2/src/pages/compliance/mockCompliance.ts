import { demoKeyed, demoRows } from '../../lib/demoIds';

// Demo-mode fallback data for the Compliance module. Aligned with backend
// DTOs in lib/api.ts so framework scorecards, findings aging and audit
// engagement timelines render with realistic data offline.
import type {
  ComplianceControlDto, ComplianceAssessmentDto, ComplianceFindingDto,
  RegulatoryChangeDto, PolicyDto, AuditEngagementDto,
  FrameworkDto, FrameworkCoverageDto, FrameworkPolicyRowDto,
  FrameworkFindingRowDto, FrameworkControlRowDto, FrameworkDpiaRowDto,
  FrameworkKriRowDto, FrameworkRiskRowDto,
} from '../../lib/api';

// ── Controls ──────────────────────────────────────────────────────────────
export const COMPLIANCE_CONTROLS: ComplianceControlDto[] = demoRows([
  { id: 1, code: 'A.5.1',   nameEn: 'Information security policies',          framework: 'ISO_27001', domain: 'Organizational', maturityLevel: 5, status: 'IMPLEMENTED',     owner: 'CISO' },
  { id: 2, code: 'A.5.7',   nameEn: 'Threat intelligence',                    framework: 'ISO_27001', domain: 'Operations',     maturityLevel: 3, status: 'PARTIAL',         owner: 'soc.lead@tss.sa' },
  { id: 3, code: 'AC-2',    nameEn: 'Account management lifecycle',           framework: 'NCA_ECC',   domain: 'Identity',       maturityLevel: 4, status: 'IMPLEMENTED',     owner: 'iam.team@tss.sa' },
  { id: 4, code: 'IR-3',    nameEn: 'Incident response plan',                 framework: 'NCA_ECC',   domain: 'Incident',       maturityLevel: 4, status: 'IMPLEMENTED',     owner: 'csirt.lead@tss.sa' },
  { id: 5, code: 'PDPL-7',  nameEn: 'Cross-border transfer safeguards',       framework: 'PDPL',      domain: 'Cross-Border',   maturityLevel: 2, status: 'PARTIAL',         owner: 'dpo@tss.sa' },
  { id: 6, code: 'PDPL-12', nameEn: 'Data subject rights handling',           framework: 'PDPL',      domain: 'Subject Rights', maturityLevel: 4, status: 'IMPLEMENTED',     owner: 'dpo@tss.sa' },
  { id: 7, code: 'CYB-1.4', nameEn: 'Cryptographic key management',           framework: 'SAMA_CSF',  domain: 'Cryptography',   maturityLevel: 3, status: 'PARTIAL',         owner: 'pki.team@tss.sa' },
  { id: 8, code: '8.4',     nameEn: 'Business continuity strategies',         framework: 'ISO_22301', domain: 'Strategy',       maturityLevel: 2, status: 'PLANNED',         owner: 'bcm.team@tss.sa' },
  { id: 9, code: 'A.8.16',  nameEn: 'Monitoring activities',                  framework: 'ISO_27001', domain: 'Operations',     maturityLevel: 3, status: 'PARTIAL',         owner: 'soc.lead@tss.sa' },
  { id: 10, code: 'CC-3.1', nameEn: 'Information communicated to external',   framework: 'SOC_2',     domain: 'Communication',  maturityLevel: 4, status: 'IMPLEMENTED',     owner: 'audit.lead@tss.sa' },
]);

// ── Assessments ───────────────────────────────────────────────────────────
export const COMPLIANCE_ASSESSMENTS: ComplianceAssessmentDto[] = demoRows([
  { id: 21, framework: 'ISO_27001',  period: '2026-Q1', auditor: 'KPMG ME',         score: 92, status: 'COMPLETED',
    assessmentDate: '2026-03-28', notes: 'Annual surveillance audit; two minor non-conformities raised.' },
  { id: 22, framework: 'NCA_ECC',    period: '2026-Q1', auditor: 'Internal Audit',  score: 85, status: 'COMPLETED',
    assessmentDate: '2026-04-04', notes: 'Domain-by-domain self-assessment with management response.' },
  { id: 23, framework: 'PDPL',       period: '2026-Q1', auditor: 'Deloitte ME',     score: 71, status: 'COMPLETED',
    assessmentDate: '2026-04-15', notes: 'External audit identified gaps in cross-border transfer mechanism.' },
  { id: 24, framework: 'SAMA_CSF',   period: '2026-Q2', auditor: 'EY MENA',         score: 88, status: 'IN_PROGRESS',
    assessmentDate: '2026-04-22', notes: 'External regulatory audit currently in fieldwork phase.' },
  { id: 25, framework: 'ISO_22301',  period: '2026-Q1', auditor: 'BSI',             score: 79, status: 'COMPLETED',
    assessmentDate: '2026-02-12', notes: 'Stage-1 readiness review for ISO 22301 certification.' },
  { id: 26, framework: 'SOC_2',      period: '2026-H1', auditor: 'PwC',             score: 94, status: 'COMPLETED',
    assessmentDate: '2026-03-10', notes: 'SOC 2 Type II refresh — qualified opinion.' },
  { id: 27, framework: 'PCI_DSS',    period: '2026-Q1', auditor: 'AT&T QSA',        score: 90, status: 'COMPLETED',
    assessmentDate: '2026-01-30', notes: 'Annual PCI re-assessment; AOC issued.' },
]);

// ── Findings ──────────────────────────────────────────────────────────────
export const COMPLIANCE_FINDINGS: ComplianceFindingDto[] = demoRows([
  { id: 31, title: 'Cross-border data transfer mechanism not formalised',
    severity: 'HIGH', owner: 'dpo@tss.sa',
    capa: 'Negotiate SDAIA Standard Contractual Clauses with vendor; ratify by June.',
    dueDate: '2026-06-30', status: 'OPEN', capaStage: 'IMPLEMENTING',
    frameworkId: 2, clauseRef: 'PDPL-29' },
  { id: 32, title: 'Privileged session recording not extended to DBA bastion',
    severity: 'MEDIUM', owner: 'pam.team@tss.sa',
    capa: 'Extend CyberArk session recording to DBA bastion hosts.',
    dueDate: '2026-05-31', status: 'OPEN', capaStage: 'IMPLEMENTING',
    ownerTestAt: '2026-04-25', ownerTestResult: 'PARTIAL',
    frameworkId: 1, clauseRef: 'AC-2.1.2' },
  { id: 33, title: 'SIEM tuning false-positive rate above threshold',
    severity: 'MEDIUM', owner: 'soc.lead@tss.sa',
    capa: 'Quarterly tuning sprint; reduce FP rate to <5%.',
    dueDate: '2026-06-30', status: 'OPEN', capaStage: 'OWNER_TESTED',
    ownerTestAt: '2026-04-12', ownerTestResult: 'PARTIAL',
    frameworkId: 3, clauseRef: 'A.8.16' },
  { id: 34, title: 'Cryptographic key rotation policy needs annual cadence',
    severity: 'LOW', owner: 'pki.team@tss.sa',
    capa: 'Update KMS policy to enforce annual key rotation for production.',
    dueDate: '2026-05-15', status: 'OPEN', capaStage: 'INDEPENDENT_TESTED',
    ownerTestAt: '2026-04-02', ownerTestResult: 'PASS',
    independentTestAt: '2026-04-18', independentTestResult: 'PASS',
    frameworkId: 4, clauseRef: 'CYB-1.4.3' },
  { id: 35, title: 'BCP exercise scoring methodology inconsistent',
    severity: 'LOW', owner: 'bcm.team@tss.sa',
    capa: 'Adopt unified scoring rubric per ISO 22301 §8.5.',
    dueDate: '2026-05-01', status: 'CLOSED', capaStage: 'CLOSED',
    ownerTestAt: '2026-03-10', ownerTestResult: 'PASS',
    independentTestAt: '2026-03-22', independentTestResult: 'PASS',
    auditSignoffAt: '2026-04-04',
    frameworkId: 5, clauseRef: '8.5.4' },
  { id: 36, title: 'External vendor SOC 2 attestation overdue',
    severity: 'HIGH', owner: 'tprm.lead@tss.sa',
    capa: 'Request fresh SOC 2 from cloud-storage vendor; escalate if not received in 30 days.',
    dueDate: '2026-05-30', status: 'OPEN', capaStage: 'IDENTIFIED',
    frameworkId: 6, clauseRef: 'CC-9.2' },
  { id: 37, title: 'Risk-acceptance for legacy SSL not time-bound',
    severity: 'MEDIUM', owner: 'security.officer@tss.sa',
    capa: 'Add 6-month sunset to all legacy SSL exceptions; re-attestation required.',
    dueDate: '2026-06-15', status: 'OPEN', capaStage: 'IMPLEMENTING',
    frameworkId: 1, clauseRef: 'A.8.24' },
  { id: 38, title: 'CCTV retention exceeds policy at three branches',
    severity: 'MEDIUM', owner: 'dpo@tss.sa',
    capa: 'Audit retention configuration; align with 90-day policy.',
    dueDate: '2026-05-20', status: 'OPEN', capaStage: 'AUDIT_SIGNED_OFF',
    ownerTestAt: '2026-03-30', ownerTestResult: 'PASS',
    independentTestAt: '2026-04-15', independentTestResult: 'PASS',
    auditSignoffAt: '2026-04-26',
    frameworkId: 2, clauseRef: 'PDPL-15' },
]);

// ── Regulatory changes ────────────────────────────────────────────────────
export const REGULATORY_CHANGES: RegulatoryChangeDto[] = demoRows([
  { id: 41, sourceAuthority: 'SDAIA',
    titleEn: 'PDPL Implementing Regulations — 2026 amendments',
    titleAr: 'اللائحة التنفيذية لنظام حماية البيانات — تعديلات 2026',
    citation: 'SDAIA Resolution 22 of 1447H',
    summaryEn: 'Tightens cross-border transfer requirements; introduces mandatory DPIA for AI-based decisions.',
    publishedDate: '2026-02-15', effectiveDate: '2026-08-15',
    applicability: 'IN_SCOPE',  impactLevel: 'HIGH',     status: 'IMPACT_ASSESSED',
    impactSummary: '12 controls require update; cross-border transfer programme needs full refresh.',
    controlsUpdatedCount: 4 },
  { id: 42, sourceAuthority: 'NCA',
    titleEn: 'NCA ECC v2.0 transition',
    titleAr: 'نظام الضوابط الأساسية لهيئة الأمن السيبراني الإصدار 2.0',
    citation: 'NCA Circular 2026/03',
    summaryEn: 'New v2.0 of Essential Cybersecurity Controls; 18-month migration window.',
    publishedDate: '2026-03-22', effectiveDate: '2027-09-22',
    applicability: 'IN_SCOPE',  impactLevel: 'HIGH',     status: 'APPLICABILITY_ASSESSED',
    impactSummary: 'Awaiting full impact analysis; 5 new domains added.',
    controlsUpdatedCount: 0 },
  { id: 43, sourceAuthority: 'SAMA',
    titleEn: 'Open Banking Phase 3 — instructions',
    titleAr: 'إرشادات الخدمات المصرفية المفتوحة — المرحلة الثالثة',
    citation: 'SAMA Circular 4801/2026',
    summaryEn: 'Adds variable-recurring-payments + mandates SCA for high-value transactions.',
    publishedDate: '2026-04-08', effectiveDate: '2026-10-08',
    applicability: 'IN_SCOPE',  impactLevel: 'MEDIUM',   status: 'CONTROLS_UPDATED',
    impactSummary: 'Authentication flows updated; SCA tuning live in production.',
    controlsUpdatedCount: 8 },
  { id: 44, sourceAuthority: 'CMA',
    titleEn: 'Sustainability disclosure rules',
    titleAr: 'قواعد إفصاح الاستدامة',
    citation: 'CMA Resolution 8/2026',
    summaryEn: 'Mandatory ESG disclosures for listed entities aligning with ISSB.',
    publishedDate: '2026-01-30', effectiveDate: '2027-01-01',
    applicability: 'OUT_OF_SCOPE', impactLevel: 'LOW',  status: 'CLOSED',
    impactSummary: 'Not applicable — entity is unlisted.', controlsUpdatedCount: 0,
    closedAt: '2026-02-12' },
  { id: 45, sourceAuthority: 'ZATCA',
    titleEn: 'E-invoicing Phase 2 wave 18',
    titleAr: 'الفوترة الإلكترونية المرحلة 2 الموجة 18',
    citation: 'ZATCA Decision 2026/22',
    summaryEn: 'New revenue-band threshold pulls additional taxpayers into FATOORA.',
    publishedDate: '2026-04-02', effectiveDate: '2026-12-01',
    applicability: 'IN_SCOPE',  impactLevel: 'MEDIUM',   status: 'NEW',
    impactSummary: 'Initial review pending; existing FATOORA integration expected to cover.',
    controlsUpdatedCount: 0 },
  { id: 46, sourceAuthority: 'CITC',
    titleEn: 'Cloud Computing Regulatory Framework v3',
    titleAr: 'إطار التنظيم الحوسبة السحابية الإصدار 3',
    citation: 'CITC Circular CC-2026-04',
    summaryEn: 'New tier-classification + sovereign-residency requirements for level-4 services.',
    publishedDate: '2026-03-15', effectiveDate: '2026-09-15',
    applicability: 'IN_SCOPE',  impactLevel: 'HIGH',     status: 'IMPACT_ASSESSED',
    impactSummary: 'Workloads classified; sovereign migration plan in flight.',
    controlsUpdatedCount: 2 },
]);

// ── Policies ──────────────────────────────────────────────────────────────
export const POLICIES: PolicyDto[] = demoRows([
  { id: 51, code: 'POL-ISMS-001', titleEn: 'Information Security Policy', titleAr: 'سياسة أمن المعلومات',
    version: '3.2', category: 'SECURITY', status: 'PUBLISHED',
    effectiveFrom: '2026-04-01', nextReviewDate: '2027-04-01',
    publishedAt: '2026-03-22T09:00:00', approvedAt: '2026-03-20T15:30:00',
    contentEn: 'Authoritative ISMS policy …' },
  { id: 52, code: 'POL-PDPL-001', titleEn: 'Privacy & Personal Data Policy', titleAr: 'سياسة حماية البيانات الشخصية',
    version: '2.1', category: 'PRIVACY', status: 'PUBLISHED',
    effectiveFrom: '2026-02-15', nextReviewDate: '2027-02-15',
    publishedAt: '2026-02-12T11:00:00', approvedAt: '2026-02-10T15:00:00' },
  { id: 53, code: 'POL-AML-001', titleEn: 'Anti-Money Laundering Policy', titleAr: 'سياسة مكافحة غسل الأموال',
    version: '4.0', category: 'AML', status: 'PUBLISHED',
    effectiveFrom: '2026-01-01', nextReviewDate: '2027-01-01',
    publishedAt: '2025-12-22T10:00:00' },
  { id: 54, code: 'POL-COC-001', titleEn: 'Code of Conduct', titleAr: 'مدونة قواعد السلوك',
    version: '5.3', category: 'ETHICS', status: 'PUBLISHED',
    effectiveFrom: '2026-03-01', nextReviewDate: '2027-03-01',
    publishedAt: '2026-02-25T09:00:00' },
  { id: 55, code: 'POL-AI-001', titleEn: 'Acceptable AI Use Policy', titleAr: 'سياسة الاستخدام المقبول للذكاء الاصطناعي',
    version: '0.4', category: 'TECHNOLOGY', status: 'LEGAL_REVIEW',
    effectiveFrom: '2026-06-01' },
  { id: 56, code: 'POL-BCP-001', titleEn: 'Business Continuity Policy', titleAr: 'سياسة استمرارية الأعمال',
    version: '2.0', category: 'BCM', status: 'EXEC_APPROVAL',
    effectiveFrom: '2026-05-01' },
  { id: 57, code: 'POL-3PR-001', titleEn: 'Third-Party Risk Management Policy', titleAr: 'سياسة إدارة مخاطر الأطراف الثالثة',
    version: '2.4', category: 'TPRM', status: 'PUBLISHED',
    effectiveFrom: '2026-02-01', nextReviewDate: '2026-08-01',
    publishedAt: '2026-01-28T10:00:00' },
  { id: 58, code: 'POL-LEG-002', titleEn: 'Legacy Crypto Sunset Policy', titleAr: 'سياسة إنهاء التشفير القديم',
    version: '1.0', category: 'SECURITY', status: 'REVIEW_DUE',
    effectiveFrom: '2025-09-30', nextReviewDate: '2026-04-30' },
]);

// ── Audit engagements ─────────────────────────────────────────────────────
export const AUDIT_ENGAGEMENTS: AuditEngagementDto[] = demoRows([
  { id: 61, engagementCode: 'AUD-2026-001', titleEn: 'ISO 27001 Annual Surveillance',
    auditType: 'CERTIFICATION_AUDIT', framework: 'ISO_27001',
    auditorFirm: 'KPMG ME', leadAuditor: 'David Frost',
    engagementLetterDate: '2026-01-12', openingMeetingDate: '2026-03-15',
    fieldworkStartDate: '2026-03-15', fieldworkEndDate: '2026-03-25',
    reportIssuedDate: '2026-04-04', closureDate: '2026-04-22', nextFollowupDate: '2026-09-22',
    scopeSummary: 'Full ISMS scope; surveillance of all Annex A applicable controls.',
    status: 'CLOSED', findingsCount: 2 },
  { id: 62, engagementCode: 'AUD-2026-002', titleEn: 'PDPL External Compliance Review',
    auditType: 'EXTERNAL_AUDIT', framework: 'PDPL',
    auditorFirm: 'Deloitte ME', leadAuditor: 'Layan Kheir',
    engagementLetterDate: '2026-02-08', openingMeetingDate: '2026-04-04',
    fieldworkStartDate: '2026-04-04', fieldworkEndDate: '2026-04-15',
    reportIssuedDate: '2026-04-22',
    scopeSummary: 'Article-by-article review with cross-border transfer focus.',
    status: 'REPORT_ISSUED', findingsCount: 3 },
  { id: 63, engagementCode: 'AUD-2026-003', titleEn: 'SAMA CSF External Audit',
    auditType: 'REGULATORY_AUDIT', framework: 'SAMA_CSF',
    auditorFirm: 'EY MENA', leadAuditor: 'Khaled Ahmad',
    engagementLetterDate: '2026-03-22', openingMeetingDate: '2026-04-22',
    fieldworkStartDate: '2026-04-22',
    fieldworkEndDate: '2026-05-13',
    scopeSummary: 'Cybersecurity Framework v1.0 — all 4 main domains.',
    status: 'FIELDWORK', findingsCount: 0 },
  { id: 64, engagementCode: 'AUD-2026-004', titleEn: 'SOC 2 Type II Re-assessment',
    auditType: 'CERTIFICATION_AUDIT', framework: 'SOC_2',
    auditorFirm: 'PwC', leadAuditor: 'Sarah Mitchell',
    engagementLetterDate: '2025-12-12', openingMeetingDate: '2026-01-15',
    fieldworkStartDate: '2026-01-15', fieldworkEndDate: '2026-02-15',
    reportIssuedDate: '2026-03-10', closureDate: '2026-03-22',
    scopeSummary: 'Trust-services criteria for security, availability and confidentiality.',
    status: 'CLOSED', findingsCount: 1 },
  { id: 65, engagementCode: 'AUD-2026-005', titleEn: 'PCI DSS Annual Re-Assessment',
    auditType: 'EXTERNAL_AUDIT', framework: 'PCI_DSS',
    auditorFirm: 'AT&T QSA', leadAuditor: 'Roberto Esposito',
    engagementLetterDate: '2025-11-04', openingMeetingDate: '2026-01-12',
    fieldworkStartDate: '2026-01-12', fieldworkEndDate: '2026-01-23',
    reportIssuedDate: '2026-01-30', closureDate: '2026-02-08',
    scopeSummary: 'Cardholder Data Environment; 12 PCI-DSS requirements.',
    status: 'CLOSED', findingsCount: 0 },
  { id: 66, engagementCode: 'AUD-2026-006', titleEn: 'Internal Audit — Cyber Operations',
    auditType: 'INTERNAL_AUDIT', framework: 'ISO_27001',
    auditorFirm: 'Internal Audit', leadAuditor: 'Mona Al-Bishi',
    engagementLetterDate: '2026-04-15',
    scopeSummary: 'Half-year internal review of cyber operations effectiveness.',
    status: 'PLANNED', findingsCount: 0 },
]);

// ── Frameworks catalogue ──────────────────────────────────────────────────
export const FRAMEWORKS: FrameworkDto[] = demoRows([
  { id: 1, code: 'ISO_27001',  nameEn: 'ISO/IEC 27001',  nameAr: 'الآيزو 27001',  version: '2022', issuer: 'ISO',     family: 'INFOSEC',     isActive: true },
  { id: 2, code: 'NCA_ECC',    nameEn: 'NCA ECC',        nameAr: 'الضوابط الأساسية للأمن السيبراني', version: '1.0',  issuer: 'NCA',     family: 'CYBER_REG',   isActive: true },
  { id: 3, code: 'PDPL',       nameEn: 'Saudi PDPL',     nameAr: 'نظام حماية البيانات', version: '1.1', issuer: 'SDAIA',  family: 'PRIVACY',     isActive: true },
  { id: 4, code: 'SAMA_CSF',   nameEn: 'SAMA CSF',       nameAr: 'إطار البنك المركزي', version: '1.0',  issuer: 'SAMA',     family: 'CYBER_REG',   isActive: true },
  { id: 5, code: 'ISO_22301',  nameEn: 'ISO 22301',      nameAr: 'الآيزو 22301',  version: '2019', issuer: 'ISO',     family: 'BCM',         isActive: true },
  { id: 6, code: 'SOC_2',      nameEn: 'SOC 2',          nameAr: 'سوك 2',          version: 'TSC 2017', issuer: 'AICPA', family: 'AUDIT',       isActive: true },
  { id: 7, code: 'PCI_DSS',    nameEn: 'PCI DSS',        nameAr: 'بي سي آي دي إس إس', version: 'v4.0', issuer: 'PCI SSC', family: 'INFOSEC',     isActive: true },
  { id: 8, code: 'NIST_CSF',   nameEn: 'NIST CSF',       nameAr: 'إطار نيست',      version: '2.0',  issuer: 'NIST',     family: 'CYBER_REG',   isActive: true },
  { id: 9, code: 'ISO_31000',  nameEn: 'ISO 31000',      nameAr: 'الآيزو 31000',  version: '2018', issuer: 'ISO',     family: 'RISK',        isActive: true },
  { id: 10, code: 'COSO_ERM',  nameEn: 'COSO ERM',       nameAr: 'كوزو إي آر إم', version: '2017', issuer: 'COSO',    family: 'RISK',        isActive: true },
  { id: 11, code: 'SAMA_AML',  nameEn: 'SAMA AML',       nameAr: 'مكافحة غسل الأموال', version: '2024', issuer: 'SAMA', family: 'AML_AB',    isActive: true },
  { id: 12, code: 'FATF',      nameEn: 'FATF Recommendations', nameAr: 'توصيات فاتف', version: '2023', issuer: 'FATF',  family: 'AML_AB',    isActive: true },
  { id: 13, code: 'ISO_37001', nameEn: 'ISO 37001',      nameAr: 'الآيزو 37001',  version: '2016', issuer: 'ISO',     family: 'AML_AB',    isActive: true },

  // ── HRSD frameworks (DD-83) ───────────────────────────────────────────
  // Saudi Ministry of Human Resources & Social Development owns three
  // distinct compliance regimes that affect every private-sector employer:
  //
  //   • HRSD_LABOR    — Saudi Labor Law (نظام العمل) + WPS, work hours,
  //                     end-of-service benefits, contracts, work permits.
  //   • HRSD_GOSI     — General Organization for Social Insurance —
  //                     mandatory social-insurance contributions.
  //   • HRSD_NITAQAT  — Saudization quota program. Drives visa quotas,
  //                     work-permit issuance, and government-tender
  //                     eligibility. Bands: Platinum / Green High / Mid /
  //                     Low / Yellow / Red, by sector × establishment size.
  { id: 14, code: 'HRSD_LABOR',   nameEn: 'Saudi Labor Law',  nameAr: 'نظام العمل السعودي', version: '2023 (consolidated)', issuer: 'HRSD', family: 'HR',         isActive: true },
  { id: 15, code: 'HRSD_GOSI',    nameEn: 'GOSI',             nameAr: 'التأمينات الاجتماعية',     version: '2024',  issuer: 'HRSD', family: 'HR',         isActive: true },
  { id: 16, code: 'HRSD_NITAQAT', nameEn: 'Nitaqat (Saudization)', nameAr: 'نطاقات (السعودة)',  version: 'v3 — 2024', issuer: 'HRSD', family: 'HR',     isActive: true },

  // ── DD-85: Ministry of Commerce frameworks ────────────────────────────
  // Three regimes that touch every commercial entity in Saudi Arabia:
  //   • MOC_TASATTUR              — Anti-Concealment Law (نظام مكافحة التستر)
  //                                  governs the case where a Saudi national
  //                                  fronts a business actually controlled by
  //                                  a foreigner (تستر تجاري). Penalties:
  //                                  fines up to SAR 5M, prison up to 5
  //                                  years, deportation, asset confiscation.
  //   • MOC_CONSUMER_PROTECTION   — Consumer Protection Law + e-Commerce Law
  //                                  governs returns, refunds, deceptive
  //                                  marketing, price labelling, warranty
  //                                  obligations.
  //   • MOC_COMMERCIAL_REGISTRY   — Commercial Registry obligations: keeping
  //                                  the CR current, beneficial-ownership
  //                                  disclosure, annual confirmation,
  //                                  trade-name + activity-code accuracy.
  { id: 17, code: 'MOC_TASATTUR',           nameEn: 'Anti-Concealment Law (Tasattur)', nameAr: 'نظام مكافحة التستر',  version: '2020',          issuer: 'MOC', family: 'COMPLIANCE', isActive: true },
  { id: 18, code: 'MOC_CONSUMER_PROTECTION', nameEn: 'Consumer Protection Law',          nameAr: 'نظام حماية المستهلك', version: '2014 (e-Comm 2019)', issuer: 'MOC', family: 'COMPLIANCE', isActive: true },
  { id: 19, code: 'MOC_COMMERCIAL_REGISTRY', nameEn: 'Commercial Registry obligations',  nameAr: 'التزامات السجل التجاري', version: '2024',         issuer: 'MOC', family: 'COMPLIANCE', isActive: true },
]);

// ── Coverage stats per framework ──────────────────────────────────────────
export const FRAMEWORK_COVERAGE: FrameworkCoverageDto[] = demoRows([
  { frameworkId: 1, code: 'ISO_27001',  nameEn: 'ISO/IEC 27001', family: 'INFOSEC',
    policyCount: 6, full: 4, partial: 2, outOfScope: 0, notApplicable: 0,
    openFindings: 3, closedFindings: 11,
    controlCount: 14, implementedControls: 11, avgMaturity: 3.6,
    dpiaCount: 0, kriCount: 1, riskCount: 4, riskAsRiskFw: 1, riskAsCompFw: 4 },
  { frameworkId: 2, code: 'NCA_ECC',    nameEn: 'NCA ECC',       family: 'CYBER_REG',
    policyCount: 5, full: 3, partial: 2, outOfScope: 0, notApplicable: 0,
    openFindings: 2, closedFindings: 8,
    controlCount: 12, implementedControls: 9, avgMaturity: 3.4,
    dpiaCount: 1, kriCount: 3, riskCount: 5, riskAsRiskFw: 1, riskAsCompFw: 5 },
  { frameworkId: 3, code: 'PDPL',       nameEn: 'Saudi PDPL',     family: 'PRIVACY',
    policyCount: 3, full: 1, partial: 2, outOfScope: 0, notApplicable: 0,
    openFindings: 1, closedFindings: 4,
    controlCount: 8, implementedControls: 5, avgMaturity: 2.9,
    dpiaCount: 5, kriCount: 1, riskCount: 3, riskAsRiskFw: 0, riskAsCompFw: 3 },
  { frameworkId: 4, code: 'SAMA_CSF',   nameEn: 'SAMA CSF',       family: 'CYBER_REG',
    policyCount: 4, full: 3, partial: 1, outOfScope: 0, notApplicable: 0,
    openFindings: 0, closedFindings: 6,
    controlCount: 10, implementedControls: 8, avgMaturity: 3.7,
    dpiaCount: 0, kriCount: 1, riskCount: 2, riskAsRiskFw: 0, riskAsCompFw: 2 },
  { frameworkId: 5, code: 'ISO_22301',  nameEn: 'ISO 22301',      family: 'BCM',
    policyCount: 3, full: 2, partial: 1, outOfScope: 0, notApplicable: 0,
    openFindings: 1, closedFindings: 3,
    controlCount: 7, implementedControls: 5, avgMaturity: 3.2,
    dpiaCount: 0, kriCount: 2, riskCount: 2, riskAsRiskFw: 1, riskAsCompFw: 1 },
  { frameworkId: 6, code: 'SOC_2',      nameEn: 'SOC 2',          family: 'AUDIT',
    policyCount: 3, full: 3, partial: 0, outOfScope: 0, notApplicable: 1,
    openFindings: 0, closedFindings: 2,
    controlCount: 6, implementedControls: 6, avgMaturity: 4.2,
    dpiaCount: 0, kriCount: 0, riskCount: 1, riskAsRiskFw: 0, riskAsCompFw: 1 },
  { frameworkId: 7, code: 'PCI_DSS',    nameEn: 'PCI DSS',        family: 'INFOSEC',
    policyCount: 4, full: 3, partial: 1, outOfScope: 0, notApplicable: 1,
    openFindings: 0, closedFindings: 4,
    controlCount: 12, implementedControls: 11, avgMaturity: 4.1,
    dpiaCount: 0, kriCount: 0, riskCount: 2, riskAsRiskFw: 0, riskAsCompFw: 2 },
  { frameworkId: 8, code: 'NIST_CSF',   nameEn: 'NIST CSF',       family: 'CYBER_REG',
    policyCount: 4, full: 2, partial: 2, outOfScope: 0, notApplicable: 0,
    openFindings: 1, closedFindings: 5,
    controlCount: 10, implementedControls: 7, avgMaturity: 3.0,
    dpiaCount: 0, kriCount: 2, riskCount: 3, riskAsRiskFw: 1, riskAsCompFw: 3 },
  { frameworkId: 9, code: 'ISO_31000',  nameEn: 'ISO 31000',      family: 'RISK',
    policyCount: 2, full: 2, partial: 0, outOfScope: 0, notApplicable: 0,
    openFindings: 0, closedFindings: 2,
    controlCount: 4, implementedControls: 4, avgMaturity: 4.0,
    dpiaCount: 0, kriCount: 4, riskCount: 12, riskAsRiskFw: 12, riskAsCompFw: 0 },
  { frameworkId: 10, code: 'COSO_ERM', nameEn: 'COSO ERM',       family: 'RISK',
    policyCount: 2, full: 2, partial: 0, outOfScope: 0, notApplicable: 0,
    openFindings: 0, closedFindings: 1,
    controlCount: 3, implementedControls: 3, avgMaturity: 4.0,
    dpiaCount: 0, kriCount: 0, riskCount: 7, riskAsRiskFw: 7, riskAsCompFw: 0 },
  { frameworkId: 11, code: 'SAMA_AML', nameEn: 'SAMA AML',       family: 'AML_AB',
    policyCount: 2, full: 2, partial: 0, outOfScope: 0, notApplicable: 0,
    openFindings: 1, closedFindings: 5,
    controlCount: 8, implementedControls: 7, avgMaturity: 3.6,
    dpiaCount: 0, kriCount: 1, riskCount: 2, riskAsRiskFw: 0, riskAsCompFw: 2 },
  { frameworkId: 12, code: 'FATF',     nameEn: 'FATF',           family: 'AML_AB',
    policyCount: 1, full: 1, partial: 0, outOfScope: 0, notApplicable: 0,
    openFindings: 0, closedFindings: 1,
    controlCount: 4, implementedControls: 3, avgMaturity: 3.2,
    dpiaCount: 0, kriCount: 0, riskCount: 1, riskAsRiskFw: 0, riskAsCompFw: 1 },
  { frameworkId: 13, code: 'ISO_37001', nameEn: 'ISO 37001',     family: 'AML_AB',
    policyCount: 2, full: 1, partial: 1, outOfScope: 0, notApplicable: 0,
    openFindings: 0, closedFindings: 2,
    controlCount: 5, implementedControls: 4, avgMaturity: 3.4,
    dpiaCount: 0, kriCount: 0, riskCount: 1, riskAsRiskFw: 0, riskAsCompFw: 1 },

  // ── HRSD coverage (DD-83) ────────────────────────────────────────────
  { frameworkId: 14, code: 'HRSD_LABOR',   nameEn: 'Saudi Labor Law', family: 'HR',
    policyCount: 4, full: 3, partial: 1, outOfScope: 0, notApplicable: 0,
    openFindings: 1, closedFindings: 3,
    controlCount: 9, implementedControls: 7, avgMaturity: 3.5,
    dpiaCount: 0, kriCount: 1, riskCount: 2, riskAsRiskFw: 0, riskAsCompFw: 2 },
  { frameworkId: 15, code: 'HRSD_GOSI',    nameEn: 'GOSI', family: 'HR',
    policyCount: 2, full: 2, partial: 0, outOfScope: 0, notApplicable: 0,
    openFindings: 0, closedFindings: 1,
    controlCount: 5, implementedControls: 5, avgMaturity: 4.0,
    dpiaCount: 0, kriCount: 0, riskCount: 1, riskAsRiskFw: 0, riskAsCompFw: 1 },
  { frameworkId: 16, code: 'HRSD_NITAQAT', nameEn: 'Nitaqat', family: 'HR',
    policyCount: 1, full: 1, partial: 0, outOfScope: 0, notApplicable: 0,
    openFindings: 1, closedFindings: 2,
    controlCount: 4, implementedControls: 3, avgMaturity: 3.2,
    dpiaCount: 0, kriCount: 1, riskCount: 1, riskAsRiskFw: 0, riskAsCompFw: 1 },

  // ── DD-85: MOC coverage ──────────────────────────────────────────────
  { frameworkId: 17, code: 'MOC_TASATTUR', nameEn: 'Tasattur', family: 'COMPLIANCE',
    policyCount: 2, full: 1, partial: 1, outOfScope: 0, notApplicable: 0,
    openFindings: 0, closedFindings: 1,
    controlCount: 6, implementedControls: 5, avgMaturity: 3.6,
    dpiaCount: 0, kriCount: 1, riskCount: 1, riskAsRiskFw: 0, riskAsCompFw: 1 },
  { frameworkId: 18, code: 'MOC_CONSUMER_PROTECTION', nameEn: 'Consumer Protection', family: 'COMPLIANCE',
    policyCount: 3, full: 2, partial: 1, outOfScope: 0, notApplicable: 0,
    openFindings: 1, closedFindings: 4,
    controlCount: 8, implementedControls: 6, avgMaturity: 3.4,
    dpiaCount: 0, kriCount: 1, riskCount: 2, riskAsRiskFw: 0, riskAsCompFw: 2 },
  { frameworkId: 19, code: 'MOC_COMMERCIAL_REGISTRY', nameEn: 'Commercial Registry', family: 'COMPLIANCE',
    policyCount: 2, full: 2, partial: 0, outOfScope: 0, notApplicable: 0,
    openFindings: 0, closedFindings: 1,
    controlCount: 5, implementedControls: 5, avgMaturity: 4.0,
    dpiaCount: 0, kriCount: 0, riskCount: 1, riskAsRiskFw: 0, riskAsCompFw: 1 },
]);

// ── Per-framework drill-down rows (sample for ISO_27001 = 1) ───────────────
export const FRAMEWORK_POLICIES_BY_ID: Record<number, FrameworkPolicyRowDto[]> = demoKeyed({
  1: [
    { mappingId: 9001, policyId: 51, code: 'POL-ISMS-001', titleEn: 'Information Security Policy',
      version: '3.2', category: 'SECURITY', status: 'PUBLISHED', nextReviewDate: '2027-04-01',
      clauseRef: 'A.5.1', satisfaction: 'FULL' },
    { mappingId: 9002, policyId: 58, code: 'POL-LEG-002', titleEn: 'Legacy Crypto Sunset Policy',
      version: '1.0', category: 'SECURITY', status: 'REVIEW_DUE', nextReviewDate: '2026-04-30',
      clauseRef: 'A.8.24', satisfaction: 'PARTIAL' },
  ],
  2: [
    { mappingId: 9011, policyId: 51, code: 'POL-ISMS-001', titleEn: 'Information Security Policy',
      version: '3.2', category: 'SECURITY', status: 'PUBLISHED', nextReviewDate: '2027-04-01',
      clauseRef: 'CY-1.2', satisfaction: 'FULL' },
  ],
  3: [
    { mappingId: 9021, policyId: 52, code: 'POL-PDPL-001', titleEn: 'Privacy & Personal Data Policy',
      version: '2.1', category: 'PRIVACY', status: 'PUBLISHED', nextReviewDate: '2027-02-15',
      clauseRef: 'PDPL-7', satisfaction: 'FULL' },
  ],
});

export const FRAMEWORK_FINDINGS_BY_ID: Record<number, FrameworkFindingRowDto[]> = demoKeyed({
  1: [
    { id: 33, title: 'SIEM tuning false-positive rate above threshold',
      severity: 'MEDIUM', owner: 'soc.lead@tss.sa', dueDate: '2026-06-30',
      status: 'OPEN', capaStage: 'OWNER_TESTED', clauseRef: 'A.8.16', createdAt: '2026-04-04' },
    { id: 32, title: 'Privileged session recording not extended to DBA bastion',
      severity: 'MEDIUM', owner: 'pam.team@tss.sa', dueDate: '2026-05-31',
      status: 'OPEN', capaStage: 'IMPLEMENTING', clauseRef: 'A.5.15', createdAt: '2026-04-08' },
    { id: 37, title: 'Risk-acceptance for legacy SSL not time-bound',
      severity: 'MEDIUM', owner: 'security.officer@tss.sa', dueDate: '2026-06-15',
      status: 'OPEN', capaStage: 'IMPLEMENTING', clauseRef: 'A.8.24', createdAt: '2026-04-12' },
  ],
  3: [
    { id: 31, title: 'Cross-border data transfer mechanism not formalised',
      severity: 'HIGH', owner: 'dpo@tss.sa', dueDate: '2026-06-30',
      status: 'OPEN', capaStage: 'IMPLEMENTING', clauseRef: 'PDPL-29', createdAt: '2026-04-15' },
  ],
});

export const FRAMEWORK_CONTROLS_BY_ID: Record<number, FrameworkControlRowDto[]> = demoKeyed({
  1: [
    { id: 1,  code: 'A.5.1',  nameEn: 'Information security policies', domain: 'Organizational', maturityLevel: 5, status: 'IMPLEMENTED',  owner: 'CISO' },
    { id: 9,  code: 'A.8.16', nameEn: 'Monitoring activities',          domain: 'Operations',     maturityLevel: 3, status: 'PARTIAL',      owner: 'soc.lead@tss.sa' },
  ],
  2: [
    { id: 3,  code: 'AC-2',   nameEn: 'Account management lifecycle', domain: 'Identity',  maturityLevel: 4, status: 'IMPLEMENTED', owner: 'iam.team@tss.sa' },
    { id: 4,  code: 'IR-3',   nameEn: 'Incident response plan',       domain: 'Incident',  maturityLevel: 4, status: 'IMPLEMENTED', owner: 'csirt.lead@tss.sa' },
  ],
});

export const FRAMEWORK_DPIAS_BY_ID: Record<number, FrameworkDpiaRowDto[]> = demoKeyed({
  3: [
    { id: 41, titleEn: 'AI-based credit scoring',          triggerReason: 'AUTOMATED_DECISION_MAKING', status: 'APPROVED',
      overallResidualRisk: 'MEDIUM', assessmentDate: '2026-02-10', nextReviewDate: '2026-08-10', clauseRef: 'PDPL-22' },
    { id: 42, titleEn: 'Cross-border payroll vendor',     triggerReason: 'CROSS_BORDER_TRANSFER', status: 'IN_REVIEW',
      overallResidualRisk: 'HIGH', assessmentDate: '2026-04-12', clauseRef: 'PDPL-29' },
    { id: 45, titleEn: 'Employee wellness programme',     triggerReason: 'SPECIAL_CATEGORY', status: 'DRAFT',
      overallResidualRisk: 'MEDIUM', assessmentDate: '2026-04-25', clauseRef: 'PDPL-6' },
  ],
});

export const FRAMEWORK_KRIS_BY_ID: Record<number, FrameworkKriRowDto[]> = demoKeyed({
  1: [
    { id: 1002, code: 'KRI-CYB-02', nameEn: 'Critical vulnerabilities open beyond SLA',
      unit: 'CVEs', direction: 'HIGHER_IS_WORSE', greenMax: 0, amberMax: 3,
      frequency: 'DAILY', owner: 'patch.team@tss.sa', clauseRef: 'A.8.8' },
  ],
  2: [
    { id: 1001, code: 'KRI-CYB-01', nameEn: 'Failed login attempts (24h)',
      unit: 'events', direction: 'HIGHER_IS_WORSE', greenMax: 500, amberMax: 1500,
      frequency: 'DAILY', owner: 'soc.lead@tss.sa', clauseRef: 'CY-3.1' },
    { id: 1006, code: 'KRI-COM-01', nameEn: 'Open audit findings beyond due date',
      unit: 'findings', direction: 'HIGHER_IS_WORSE', greenMax: 2, amberMax: 5,
      frequency: 'WEEKLY', owner: 'audit.lead@tss.sa', clauseRef: 'GO-2.1' },
  ],
  3: [
    { id: 1007, code: 'KRI-PRV-01', nameEn: 'DSR responses past PDPL 30-day SLA',
      unit: 'DSRs', direction: 'HIGHER_IS_WORSE', greenMax: 0, amberMax: 2,
      frequency: 'WEEKLY', owner: 'dpo@tss.sa', clauseRef: 'PDPL-23' },
  ],
});

export const FRAMEWORK_RISKS_BY_ID: Record<number, FrameworkRiskRowDto[]> = demoKeyed({
  1: [
    { id: 1, titleEn: 'Sensitive Data Breach',  status: 'OPEN', inherentScore: 20, residualScore: 9,
      riskLevel: 'CRITICAL', matchedAsComplianceFramework: true, complianceFrameworkClause: 'A.5.34' },
    { id: 4, titleEn: 'Phishing Campaign',      status: 'CLOSED', inherentScore: 9, residualScore: 4,
      riskLevel: 'LOW', matchedAsComplianceFramework: true, complianceFrameworkClause: 'A.6.3' },
  ],
  3: [
    { id: 10, titleEn: 'Cross-Border Data Transfer Compliance', status: 'UNDER_ASSESSMENT', inherentScore: 20, residualScore: 12,
      riskLevel: 'HIGH', matchedAsComplianceFramework: true, complianceFrameworkClause: 'PDPL-29' },
  ],
  9: [
    { id: 1, titleEn: 'Sensitive Data Breach',  status: 'OPEN', inherentScore: 20, residualScore: 9,
      riskLevel: 'CRITICAL', matchedAsRiskFramework: true, riskFrameworkClause: '6.4.3' },
    { id: 8, titleEn: 'Insider Misuse of Privileged Access', status: 'TREATMENT_IN_PROGRESS', inherentScore: 15, residualScore: 8,
      riskLevel: 'HIGH', matchedAsRiskFramework: true, riskFrameworkClause: '6.4.3' },
  ],
});

// ── Per-control detail-tab mocks ──────────────────────────────────────────
export interface ControlSubControlRow {
  id: number; code: string; nameEn: string; status: string; maturityLevel: number; owner: string;
}
export const CONTROL_SUBCONTROLS_BY_ID: Record<number, ControlSubControlRow[]> = demoKeyed({
  1: [
    { id: 101, code: 'A.5.1.1',  nameEn: 'Senior leadership endorsement of ISMS policy', status: 'IMPLEMENTED', maturityLevel: 5, owner: 'CISO' },
    { id: 102, code: 'A.5.1.2',  nameEn: 'Review of policies for information security', status: 'IMPLEMENTED', maturityLevel: 4, owner: 'security.officer@tss.sa' },
    { id: 103, code: 'A.5.1.3',  nameEn: 'Communication of ISMS policy to all staff',   status: 'IMPLEMENTED', maturityLevel: 4, owner: 'awareness.lead@tss.sa' },
  ],
  3: [
    { id: 301, code: 'AC-2.1.1', nameEn: 'Joiner workflow (automated)',                status: 'IMPLEMENTED', maturityLevel: 4, owner: 'iam.team@tss.sa' },
    { id: 302, code: 'AC-2.1.2', nameEn: 'Mover/leaver workflow (automated)',          status: 'IMPLEMENTED', maturityLevel: 4, owner: 'iam.team@tss.sa' },
    { id: 303, code: 'AC-2.1.3', nameEn: 'Quarterly access recertification',          status: 'PARTIAL',     maturityLevel: 3, owner: 'iam.team@tss.sa' },
    { id: 304, code: 'AC-2.1.4', nameEn: 'JIT elevation for Tier-0 systems',          status: 'IN_PROGRESS', maturityLevel: 3, owner: 'pam.team@tss.sa' },
  ],
  4: [
    { id: 401, code: 'IR-3.1.1', nameEn: 'Documented incident response plan',          status: 'IMPLEMENTED', maturityLevel: 4, owner: 'csirt.lead@tss.sa' },
    { id: 402, code: 'IR-3.1.2', nameEn: 'Annual response-plan exercise',              status: 'IMPLEMENTED', maturityLevel: 4, owner: 'csirt.lead@tss.sa' },
    { id: 403, code: 'IR-3.1.3', nameEn: 'Saudi regulator notification templates',    status: 'IMPLEMENTED', maturityLevel: 4, owner: 'legal.counsel@tss.sa' },
  ],
  9: [
    { id: 901, code: 'A.8.16.1', nameEn: 'SIEM ingest coverage of Tier-0 systems',     status: 'IMPLEMENTED', maturityLevel: 4, owner: 'soc.lead@tss.sa' },
    { id: 902, code: 'A.8.16.2', nameEn: 'Alert tuning + false-positive rate <5%',    status: 'PARTIAL',     maturityLevel: 3, owner: 'soc.lead@tss.sa' },
    { id: 903, code: 'A.8.16.3', nameEn: '24/7 monitoring + SOC handover',            status: 'IMPLEMENTED', maturityLevel: 4, owner: 'soc.duty@tss.sa' },
  ],
});

export interface ControlEvidenceRow {
  id: number; code: string; titleEn: string; type: string; collectedDate: string; expiryDate: string; status: string;
}
export const CONTROL_EVIDENCE_BY_ID: Record<number, ControlEvidenceRow[]> = demoKeyed({
  1: [
    { id: 1, code: 'EV-1083', titleEn: 'Information Security Policy v3.2',             type: 'POLICY',        collectedDate: '2026-04-15', expiryDate: '2027-04-15', status: 'APPROVED' },
    { id: 8, code: 'EV-1076', titleEn: 'Annual ISMS Internal Audit Plan',              type: 'DOCUMENT',      collectedDate: '2026-01-15', expiryDate: '2026-12-31', status: 'APPROVED' },
  ],
  3: [
    { id: 7, code: 'EV-1077', titleEn: 'Q1 PAM Recertification Log',                    type: 'LOG',           collectedDate: '2026-03-31', expiryDate: '2026-06-30', status: 'APPROVED' },
  ],
  4: [
    { id: 6, code: 'EV-1075', titleEn: 'IR Tabletop Minutes — April',                   type: 'MINUTES',       collectedDate: '2026-04-22', expiryDate: '2026-10-22', status: 'APPROVED' },
  ],
  9: [
    { id: 3, code: 'EV-1081', titleEn: 'Firewall Rules Configuration Snapshot',         type: 'CONFIGURATION', collectedDate: '2026-04-12', expiryDate: '2026-05-12', status: 'PENDING_REVIEW' },
  ],
});

export interface ControlTestRow {
  id: number; date: string; tester: string; result: 'PASS' | 'FAIL' | 'PARTIAL'; samples: number; findings: number; notes: string;
}
export const CONTROL_TESTS_BY_ID: Record<number, ControlTestRow[]> = demoKeyed({
  1: [
    { id: 1, date: '2026-03-28', tester: 'KPMG ME',          result: 'PASS',    samples: 25, findings: 0, notes: 'No exceptions found.' },
    { id: 2, date: '2025-11-04', tester: 'Internal Audit',   result: 'PASS',    samples: 20, findings: 1, notes: 'Minor gap closed pre-audit.' },
  ],
  3: [
    { id: 3, date: '2026-03-12', tester: 'Mona Al-Bishi',     result: 'PARTIAL', samples: 30, findings: 2, notes: 'Recertification rate 96% — 2 outliers.' },
    { id: 4, date: '2025-09-20', tester: 'Internal Audit',    result: 'PASS',    samples: 30, findings: 0, notes: 'Clean test.' },
  ],
  4: [
    { id: 5, date: '2026-04-04', tester: 'CSIRT exercise',    result: 'PASS',    samples: 1,  findings: 0, notes: 'Tabletop scenario passed; minor runbook updates noted.' },
  ],
  9: [
    { id: 6, date: '2026-04-04', tester: 'Yousef Al-Harbi',   result: 'PARTIAL', samples: 50, findings: 3, notes: 'Three Tier-1 systems below SIEM coverage threshold.' },
  ],
});

// ── Per-finding detail-tab mocks ─────────────────────────────────────────
export interface FindingHistoryEvent {
  id: number; at: string; actor: string; type: 'CREATED' | 'CAPA_PROGRESS' | 'COMMENT' | 'TEST_RESULT' | 'STATUS_CHANGE' | 'SIGNOFF'; message: string;
}
export const FINDING_HISTORY_BY_ID: Record<number, FindingHistoryEvent[]> = demoKeyed({
  31: [
    { id: 11, at: '2026-04-22T14:00:00', actor: 'DPO + Legal',      type: 'CAPA_PROGRESS', message: 'Standard Contractual Clauses drafted; vendor review in progress.' },
    { id: 12, at: '2026-04-15T10:00:00', actor: 'Mona Al-Bishi',    type: 'STATUS_CHANGE', message: 'Status: IDENTIFIED → IMPLEMENTING.' },
    { id: 13, at: '2026-04-15T09:30:00', actor: 'External Auditor', type: 'CREATED',       message: 'Finding raised: cross-border transfer mechanism not formalised.' },
  ],
  32: [
    { id: 21, at: '2026-04-25T11:00:00', actor: 'PAM Team',         type: 'TEST_RESULT',  message: 'Owner test PARTIAL — 80% of bastion hosts covered.' },
    { id: 22, at: '2026-04-08T15:30:00', actor: 'PAM Team',         type: 'CAPA_PROGRESS', message: 'Session-recording rolled out to dev bastion.' },
    { id: 23, at: '2026-04-04T09:00:00', actor: 'Internal Audit',   type: 'CREATED',       message: 'Finding raised after PAM coverage review.' },
  ],
  33: [
    { id: 31, at: '2026-04-12T16:00:00', actor: 'SOC Lead',         type: 'TEST_RESULT',  message: 'Owner test PARTIAL — 92% tuning rate vs 95% target.' },
    { id: 32, at: '2026-04-04T10:30:00', actor: 'KPMG ME',          type: 'CREATED',       message: 'Finding raised after surveillance audit.' },
  ],
  34: [
    { id: 41, at: '2026-04-18T11:00:00', actor: 'Independent Tester',type: 'TEST_RESULT', message: 'Independent test PASS — annual rotation policy documented.' },
    { id: 42, at: '2026-04-02T14:00:00', actor: 'PKI Team',         type: 'TEST_RESULT',  message: 'Owner test PASS.' },
    { id: 43, at: '2026-02-28T09:00:00', actor: 'KPMG ME',          type: 'CREATED',       message: 'Finding raised post-surveillance audit.' },
  ],
});

export const FINDING_EVIDENCE_BY_ID: Record<number, ControlEvidenceRow[]> = demoKeyed({
  31: [
    { id: 4, code: 'EV-1080', titleEn: 'PDPL Data Inventory Report Q4 2025',   type: 'REPORT',      collectedDate: '2026-04-08', expiryDate: '2026-05-08', status: 'PENDING_REVIEW' },
  ],
  32: [
    { id: 7, code: 'EV-1077', titleEn: 'Q1 PAM Recertification Log',           type: 'LOG',         collectedDate: '2026-03-31', expiryDate: '2026-06-30', status: 'APPROVED' },
  ],
  33: [
    { id: 9, code: 'EV-1075', titleEn: 'Phishing Awareness Campaign Results',   type: 'REPORT',      collectedDate: '2026-04-05', expiryDate: '2026-07-05', status: 'APPROVED' },
  ],
});

// ── Per-policy detail-tab mocks ──────────────────────────────────────────
export interface PolicyLifecycleEvent {
  id: number; at: string; actor: string; stage: 'DRAFT' | 'LEGAL_REVIEW' | 'EXEC_APPROVAL' | 'PUBLISHED' | 'REVIEW_DUE' | 'RETIRED'; message: string;
}
export const POLICY_LIFECYCLE_EVENTS_BY_ID: Record<number, PolicyLifecycleEvent[]> = demoKeyed({
  51: [
    { id: 1, at: '2026-04-01T09:00:00', actor: 'CEO',           stage: 'PUBLISHED',     message: 'Policy v3.2 published; effective 2026-04-01.' },
    { id: 2, at: '2026-03-22T15:30:00', actor: 'Exec Committee',stage: 'EXEC_APPROVAL', message: 'Approved by executive committee.' },
    { id: 3, at: '2026-03-08T11:00:00', actor: 'Legal Counsel', stage: 'LEGAL_REVIEW',  message: 'Legal sign-off recorded.' },
    { id: 4, at: '2026-02-12T14:00:00', actor: 'CISO',          stage: 'DRAFT',         message: 'Initial draft submitted for review.' },
  ],
  52: [
    { id: 5, at: '2026-02-15T10:00:00', actor: 'CEO',          stage: 'PUBLISHED',     message: 'Policy v2.1 published; effective 2026-02-15.' },
    { id: 6, at: '2026-02-10T15:00:00', actor: 'Exec Committee',stage: 'EXEC_APPROVAL', message: 'Approved.' },
    { id: 7, at: '2026-01-20T09:00:00', actor: 'Legal Counsel', stage: 'LEGAL_REVIEW',  message: 'Legal review complete.' },
  ],
  55: [ // AI Use Policy — currently in legal review
    { id: 8, at: '2026-04-10T14:00:00', actor: 'Legal Counsel', stage: 'LEGAL_REVIEW',  message: 'Legal review in progress.' },
    { id: 9, at: '2026-03-18T09:00:00', actor: 'CISO',          stage: 'DRAFT',         message: 'Draft v0.4 submitted.' },
  ],
  58: [ // Legacy Crypto Sunset — review due
    { id: 10, at: '2026-04-15T10:00:00', actor: 'System',         stage: 'REVIEW_DUE',    message: 'Annual review due — flagged.' },
    { id: 11, at: '2025-09-30T11:00:00', actor: 'CEO',            stage: 'PUBLISHED',     message: 'Policy v1.0 published.' },
  ],
});
