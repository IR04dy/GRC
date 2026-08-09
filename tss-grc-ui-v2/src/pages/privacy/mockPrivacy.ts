// Demo-mode fallback for the Privacy / PDPL module. Aligned with backend DTOs
// in lib/api.ts so DSR aging, breach 72-hour clock and RoPA charts render
// without authentication.
import type { PrivacyDsrDto, PrivacyBreachDto, PrivacyRopaDto, DpiaDto } from '../../lib/api';
import { demoKeyed, demoRows } from '../../lib/demoIds';

// ── DSRs ────────────────────────────────────────────────────────────────────
export const DSRS: PrivacyDsrDto[] = demoRows([
  { id: 1, requestType: 'ACCESS',        requesterName: 'Ahmed Al-Mutairi',
    requesterEmail: 'a.almutairi@example.sa',  receivedAt: '2026-04-25', dueDate: '2026-05-25', status: 'IN_PROGRESS' },
  { id: 2, requestType: 'ERASURE',       requesterName: 'Layla Al-Saud',
    requesterEmail: 'l.alsaud@example.sa',     receivedAt: '2026-04-22', dueDate: '2026-04-29', status: 'PENDING' },
  { id: 3, requestType: 'PORTABILITY',   requesterName: 'Mohammed Al-Otaibi',
    requesterEmail: 'm.alotaibi@example.sa',   receivedAt: '2026-04-18', dueDate: '2026-05-18', status: 'IN_PROGRESS' },
  { id: 4, requestType: 'RECTIFICATION', requesterName: 'Sara Al-Dosari',
    requesterEmail: 's.aldosari@example.sa',   receivedAt: '2026-04-12', dueDate: '2026-04-19', status: 'CLOSED' },
  { id: 5, requestType: 'ACCESS',        requesterName: 'Khalid Al-Ghamdi',
    requesterEmail: 'k.alghamdi@example.sa',   receivedAt: '2026-04-08', dueDate: '2026-04-15', status: 'CLOSED' },
  { id: 6, requestType: 'OBJECTION',     requesterName: 'Noura Al-Harbi',
    requesterEmail: 'n.alharbi@example.sa',    receivedAt: '2026-04-03', dueDate: '2026-04-10', status: 'APPROVED' },
  { id: 7, requestType: 'WITHDRAW_CONSENT', requesterName: 'Faisal Al-Maliki',
    requesterEmail: 'f.almaliki@example.sa',   receivedAt: '2026-04-29', dueDate: '2026-05-29', status: 'OPEN' },
]);

// ── Data breaches ──────────────────────────────────────────────────────────
// Sprint 4 — synthesise a "still in 72h window" breach at module load so
// the PDPL response console always has an actively-counting case to show
// in demo mode. The reportedAt anchors at "30 hours ago" relative to now,
// landing the case in the AMBER tier with ~42h to SDAIA notification.
const _now = new Date();
const _thirtyHoursAgo = new Date(_now.getTime() - 30 * 3_600_000).toISOString();
const _thirtyOneHoursAgo = new Date(_now.getTime() - 31 * 3_600_000).toISOString();

export const BREACHES: PrivacyBreachDto[] = demoRows([
  { id: 10, titleEn: 'Customer database export downloaded by terminated employee',
    descriptionEn: 'Departing analyst downloaded a 47k-row customer export 18 hours before access revocation. Dataset contains name, mobile, IBAN-suffix.',
    breachType: 'CONFIDENTIALITY', severity: 'HIGH', status: 'INVESTIGATING',
    occurredAt: _thirtyOneHoursAgo, reportedAt: _thirtyHoursAgo,
    affectedRecords: 47_220 },
  { id: 11, titleEn: 'Lost laptop with customer contact data',
    descriptionEn: 'Marketing exec laptop stolen at airport — encrypted at rest, 72h notification not required.',
    breachType: 'CONFIDENTIALITY', severity: 'LOW', status: 'CLOSED',
    occurredAt: '2026-04-08T14:30:00', reportedAt: '2026-04-08T18:15:00',
    affectedRecords: 0 },
  { id: 12, titleEn: 'Misdirected payroll spreadsheet',
    descriptionEn: 'HR spreadsheet emailed to wrong distribution list (32 recipients in payroll team).',
    breachType: 'CONFIDENTIALITY', severity: 'MEDIUM', status: 'INVESTIGATING',
    occurredAt: '2026-04-19T09:42:00', reportedAt: '2026-04-19T10:20:00',
    affectedRecords: 482 },
  { id: 13, titleEn: 'Vendor system mis-configuration exposed customer records',
    descriptionEn: 'Third-party loyalty platform left S3 bucket public; customer phone+email exposed for 6 hours.',
    breachType: 'CONFIDENTIALITY', severity: 'HIGH', status: 'NOTIFIED',
    occurredAt: '2026-04-02T22:00:00', reportedAt: '2026-04-03T08:30:00',
    affectedRecords: 18_440 },
  { id: 14, titleEn: 'Ransomware impact on legacy file share',
    descriptionEn: 'CRYPTO-locker on legacy file share; no PII confirmed exfiltrated.',
    breachType: 'AVAILABILITY', severity: 'HIGH', status: 'OPEN',
    occurredAt: '2026-04-19T03:42:00', reportedAt: '2026-04-19T04:15:00',
    affectedRecords: 0 },
  { id: 15, titleEn: 'Phishing-driven credential exposure',
    descriptionEn: 'Three customer-service agents fell to spear-phishing; tokens revoked within 90 minutes.',
    breachType: 'CONFIDENTIALITY', severity: 'MEDIUM', status: 'CLOSED',
    occurredAt: '2026-03-28T15:00:00', reportedAt: '2026-03-28T17:30:00',
    affectedRecords: 0 },
  { id: 16, titleEn: 'Test data containing real customer IDs',
    descriptionEn: 'QA database refreshed from production without redaction.',
    breachType: 'CONFIDENTIALITY', severity: 'LOW', status: 'CLOSED',
    occurredAt: '2026-03-15T11:00:00', reportedAt: '2026-03-15T13:00:00',
    affectedRecords: 4_120 },
]);

// ── RoPA (Records of Processing Activity) ──────────────────────────────────
export const ROPA: PrivacyRopaDto[] = demoRows([
  { id: 21, activityNameEn: 'Customer onboarding (KYC)',           activityNameAr: 'استقطاب العملاء',
    purposeEn: 'Identity verification + AML screening per SAMA AML.',
    legalBasis: 'LEGAL_OBLIGATION', retentionPeriod: '10 years post-relationship',
    status: 'ACTIVE' },
  { id: 22, activityNameEn: 'Marketing communications',            activityNameAr: 'التواصل التسويقي',
    purposeEn: 'Personalised offers and product updates by SMS / e-mail.',
    legalBasis: 'CONSENT',          retentionPeriod: '24 months from last interaction',
    status: 'ACTIVE' },
  { id: 23, activityNameEn: 'Customer service transcripts',        activityNameAr: 'تسجيلات خدمة العملاء',
    purposeEn: 'Quality assurance and dispute resolution.',
    legalBasis: 'LEGITIMATE_INTEREST', retentionPeriod: '12 months',
    status: 'ACTIVE' },
  { id: 24, activityNameEn: 'Salary disbursement',                 activityNameAr: 'صرف الرواتب',
    purposeEn: 'Payroll processing + tax + GOSI + government reporting.',
    legalBasis: 'CONTRACT',         retentionPeriod: '15 years (HRSD)',
    status: 'ACTIVE' },
  { id: 25, activityNameEn: 'CCTV at branches',                    activityNameAr: 'كاميرات المراقبة في الفروع',
    purposeEn: 'Premises security + theft / fraud detection.',
    legalBasis: 'LEGITIMATE_INTEREST', retentionPeriod: '90 days',
    status: 'ACTIVE' },
  { id: 26, activityNameEn: 'Web analytics + cookies',             activityNameAr: 'تحليلات الموقع والكوكيز',
    purposeEn: 'Site performance + segment analysis.',
    legalBasis: 'CONSENT',          retentionPeriod: '13 months',
    status: 'ACTIVE' },
  { id: 27, activityNameEn: 'Loan-application credit scoring',     activityNameAr: 'تقييم طلبات التمويل',
    purposeEn: 'Risk-based pricing + adverse-action notice generation.',
    legalBasis: 'CONTRACT',         retentionPeriod: '7 years',
    status: 'ACTIVE' },
  { id: 28, activityNameEn: 'Fraud monitoring (transactions)',     activityNameAr: 'مراقبة المعاملات للاحتيال',
    purposeEn: 'Real-time scoring of transactions to detect fraud.',
    legalBasis: 'LEGAL_OBLIGATION', retentionPeriod: '5 years',
    status: 'ACTIVE' },
  { id: 29, activityNameEn: 'Sanctions / PEP screening',           activityNameAr: 'فحص العقوبات والأشخاص ذوي النفوذ',
    purposeEn: 'AML / CFT compliance + watchlist matching per FATF.',
    legalBasis: 'LEGAL_OBLIGATION', retentionPeriod: '10 years',
    status: 'ACTIVE' },
  { id: 30, activityNameEn: 'Employee health programme',           activityNameAr: 'برنامج صحة الموظفين',
    purposeEn: 'Voluntary wellness programme (special-category data).',
    legalBasis: 'CONSENT',          retentionPeriod: 'Duration of programme',
    status: 'UNDER_REVIEW' },
]);

// ── DPIAs ───────────────────────────────────────────────────────────────────
export const DPIAS: DpiaDto[] = demoRows([
  { id: 41, titleEn: 'AI-based credit scoring',           titleAr: 'تقييم الائتمان بالذكاء الاصطناعي',
    triggerReason: 'AUTOMATED_DECISION_MAKING', status: 'APPROVED',
    overallResidualRisk: 'MEDIUM', processingActivityId: 27,
    assessmentDate: '2026-02-10', nextReviewDate: '2026-08-10',
    approvedAt: '2026-02-14T09:00:00',
    conclusion: 'Risk acceptable with explainability requirement; continuous monitoring of bias metrics.' },
  { id: 42, titleEn: 'Cross-border payroll vendor',       titleAr: 'مورد الرواتب عبر الحدود',
    triggerReason: 'CROSS_BORDER_TRANSFER', status: 'IN_REVIEW',
    overallResidualRisk: 'HIGH', processingActivityId: 24,
    assessmentDate: '2026-04-12', nextReviewDate: '2026-10-12',
    conclusion: 'Awaiting SDAIA cross-border transfer permit; encryption at rest and in transit verified.' },
  { id: 43, titleEn: 'Marketing personalisation engine',  titleAr: 'محرك التسويق المخصص',
    triggerReason: 'LARGE_SCALE_PROFILING', status: 'APPROVED',
    overallResidualRisk: 'LOW',  processingActivityId: 22,
    assessmentDate: '2026-01-22', nextReviewDate: '2026-07-22',
    approvedAt: '2026-01-25T11:30:00',
    conclusion: 'Consent-based; granular preferences; opt-out path verified end-to-end.' },
  { id: 44, titleEn: 'Branch CCTV with facial-detection (count-only)',
    titleAr: 'كاميرات الفروع مع كشف الوجوه (عدّ فقط)',
    triggerReason: 'BIOMETRIC', status: 'REJECTED',
    overallResidualRisk: 'HIGH', processingActivityId: 25,
    assessmentDate: '2026-03-04',
    rejectedAt: '2026-03-12T15:45:00', rejectionReason: 'Use-case did not justify biometric processing; alternative people-counter approved instead.' },
  { id: 45, titleEn: 'Employee wellness programme (special-category)',
    titleAr: 'برنامج صحة الموظفين (بيانات حساسة)',
    triggerReason: 'SPECIAL_CATEGORY', status: 'DRAFT',
    overallResidualRisk: 'MEDIUM', processingActivityId: 30,
    assessmentDate: '2026-04-25',
    conclusion: 'Consent UI in design; vendor contract under negotiation.' },
  { id: 46, titleEn: 'Customer-service transcript analytics',
    titleAr: 'تحليلات تسجيلات خدمة العملاء',
    triggerReason: 'LARGE_SCALE_MONITORING', status: 'EXPIRED',
    overallResidualRisk: 'LOW', processingActivityId: 23,
    assessmentDate: '2024-09-10', nextReviewDate: '2025-09-10',
    conclusion: 'Re-assessment due — original assessment expired.' },
]);

// ── Per-DPIA detail-tab mocks ───────────────────────────────────────────
export interface DpiaQuestionRow {
  id: number; section: string; question: string; answer: string; risk: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
}
export const DPIA_QUESTIONS_BY_ID: Record<number, DpiaQuestionRow[]> = demoKeyed({
  41: [
    { id: 1, section: 'Lawful basis',     question: 'What lawful basis covers this AI-driven decision?',                        answer: 'Contract performance + legitimate interest balanced against subject impact.', risk: 'MEDIUM' },
    { id: 2, section: 'Automated decisions', question: 'Does the model produce decisions with legal or significant effect?',     answer: 'Yes — credit-scoring drives loan approvals.',                                  risk: 'HIGH' },
    { id: 3, section: 'Subject rights',   question: 'Is human review available on adverse decisions?',                          answer: 'Yes — adverse-action notice with 14-day human-review window.',                 risk: 'LOW' },
    { id: 4, section: 'Bias',             question: 'How are bias metrics tracked?',                                            answer: 'Quarterly bias audit on protected attributes; results to DPO + AI ethics board.', risk: 'MEDIUM' },
    { id: 5, section: 'Data minimisation', question: 'Are only necessary data fields used?',                                     answer: 'Feature set reviewed annually; PII not used as input.',                          risk: 'LOW' },
  ],
  42: [
    { id: 11, section: 'Cross-border',     question: 'Where is data hosted + processed?',                                       answer: 'Hosted in Egypt by primary processor; back-up in Saudi Arabia.',                risk: 'HIGH' },
    { id: 12, section: 'Adequacy',         question: 'Is the destination country an adequate recipient under PDPL Art 29?',     answer: 'No adequacy decision yet; SCCs in negotiation.',                               risk: 'HIGH' },
    { id: 13, section: 'Encryption',       question: 'Is data encrypted in transit + at rest?',                                  answer: 'Yes — TLS 1.3 in transit; AES-256 at rest with HSM-backed keys.',               risk: 'LOW' },
  ],
  45: [
    { id: 21, section: 'Special category', question: 'Is health data processed?',                                                answer: 'Yes — opt-in BMI, blood pressure, exercise tracking.',                          risk: 'HIGH' },
    { id: 22, section: 'Consent',          question: 'How is consent captured + revocable?',                                     answer: 'In-app onboarding flow; revocation removes data within 30 days.',                risk: 'MEDIUM' },
  ],
});

export interface DpiaMitigationRow {
  id: number; risk: string; mitigation: string; owner: string; due: string; status: 'PLANNED' | 'IN_PROGRESS' | 'IMPLEMENTED';
}
export const DPIA_MITIGATIONS_BY_ID: Record<number, DpiaMitigationRow[]> = demoKeyed({
  41: [
    { id: 1, risk: 'Algorithmic bias on protected attributes', mitigation: 'Quarterly bias audit + threshold-based alerting',           owner: 'AI Ethics Board', due: '2026-08-31', status: 'IN_PROGRESS' },
    { id: 2, risk: 'Lack of explainability for adverse decisions', mitigation: 'SHAP-based explainer surfaced in adverse-action notice', owner: 'Data Science',     due: '2026-06-15', status: 'IMPLEMENTED' },
  ],
  42: [
    { id: 11, risk: 'Cross-border without adequacy', mitigation: 'Adopt SDAIA Standard Contractual Clauses with vendor', owner: 'DPO + Legal',     due: '2026-06-30', status: 'IN_PROGRESS' },
    { id: 12, risk: 'Single-region hosting',         mitigation: 'Migrate primary to in-Kingdom STC sovereign region',  owner: 'CIO Office',      due: '2026-12-31', status: 'PLANNED' },
  ],
  45: [
    { id: 21, risk: 'Special-category data without explicit consent', mitigation: 'In-app opt-in flow with granular categories',         owner: 'Product + DPO', due: '2026-07-01', status: 'PLANNED' },
  ],
});

export interface DpiaReviewRow {
  id: number; date: string; reviewer: string; role: string; outcome: 'APPROVED' | 'CHANGES_REQUESTED' | 'REJECTED'; comments: string;
}
export const DPIA_REVIEWS_BY_ID: Record<number, DpiaReviewRow[]> = demoKeyed({
  41: [
    { id: 1, date: '2026-02-14', reviewer: 'DPO',                   role: 'Data Protection Officer', outcome: 'APPROVED',          comments: 'Approved subject to quarterly bias audit.' },
    { id: 2, date: '2026-02-12', reviewer: 'AI Ethics Board',       role: 'Ethics Board',            outcome: 'CHANGES_REQUESTED', comments: 'Request explainability mitigation.' },
    { id: 3, date: '2026-02-08', reviewer: 'CISO',                  role: 'Security Officer',        outcome: 'APPROVED',          comments: 'Cyber controls adequate.' },
  ],
  42: [
    { id: 11, date: '2026-04-22', reviewer: 'DPO',                  role: 'Data Protection Officer', outcome: 'CHANGES_REQUESTED', comments: 'SCCs must be in place before approval.' },
  ],
  44: [
    { id: 21, date: '2026-03-12', reviewer: 'DPO',                  role: 'Data Protection Officer', outcome: 'REJECTED',          comments: 'Use-case did not justify biometric processing.' },
    { id: 22, date: '2026-03-08', reviewer: 'External Counsel',     role: 'External Legal',          outcome: 'CHANGES_REQUESTED', comments: 'Recommend rejection or alternative people-counter.' },
  ],
});
