// Attestation campaign engine — DD-63.
//
// Periodic "everyone please attest" pushes that the industry pattern / industry-standard tooling /
// industry-standard tooling all support: the platform fans out an
// attestation request to a defined population (control owners /
// employees / suppliers), tracks per-respondent completion, and
// surfaces a single dashboard for the campaign owner.
//
// Use cases (Saudi-regulated):
//   - Quarterly control-effectiveness attestation (NCA ECC §1.1.4,
//     ISO 27001 §9.2)
//   - Annual privacy-policy + Code-of-Conduct re-acknowledgement
//     (PDPL Art. 5, Nazaha Code of Ethical Conduct)
//   - Annual KYC + COI disclosure (SAMA, ISO 37001)
//   - SAMA outsourcing attestation that a critical-vendor is still
//     compliant (SAMA Outsourcing §5.3)
//   - Sub-processor list re-confirmation (PDPL Art. 29)
//
// Architecture: every campaign is a header record + N respondent
// records. As respondents file their attestation (via the existing
// CONTROL_ATTESTATION intake form from DD-60), the respondent record
// flips to COMPLETED and the campaign progress bar advances.

export type CampaignKind =
  | 'CONTROL_EFFECTIVENESS'   // ISO 27001 §9.2
  | 'POLICY_ACKNOWLEDGEMENT'  // PDPL Art. 5
  | 'COI_ANNUAL'              // ISO 37001 §8.3
  | 'CODE_OF_CONDUCT'         // Nazaha
  | 'VENDOR_ATTESTATION'      // SAMA Outsourcing §5.3
  | 'SUB_PROCESSOR_REVIEW'    // PDPL Art. 29
  | 'SECURITY_AWARENESS_QUIZ';// NCA ECC 1.4

export type CampaignStatus =
  | 'DRAFT'
  | 'IN_FLIGHT'
  | 'CLOSING'
  | 'COMPLETED'
  | 'CANCELLED';

export type RespondentStatus =
  | 'PENDING'      // not yet started
  | 'IN_PROGRESS'  // opened but not submitted
  | 'COMPLETED'    // submitted
  | 'EXEMPT'       // exempted by campaign owner
  | 'OVERDUE';     // window expired without submission

export interface CampaignRespondent {
  id: string;
  /** stakeholder code or email */
  responder: string;
  responderName?: string;
  scope?: string;       // e.g. specific control code they own
  status: RespondentStatus;
  submittedAt?: string;
  /** When they last opened the campaign — drives "started but not submitted" telemetry. */
  lastSeenAt?: string;
  /** Reference to the intake record that fulfilled this respondent slot. */
  intakeRefCode?: string;
}

export interface AttestationCampaign {
  id: string;
  code: string;          // CAMP-2026-Q2-CTRL
  kind: CampaignKind;
  titleEn: string;
  titleAr: string;
  descriptionEn: string;
  descriptionAr?: string;
  status: CampaignStatus;
  /** Standards / regulator citation. */
  reference: string;
  /** Owner stakeholder code. */
  owner: string;
  /** ISO date — campaign opens. */
  startDate: string;
  /** ISO date — last day to respond. */
  dueDate: string;
  /** ISO date — campaign closed (set when status flips to COMPLETED/CANCELLED). */
  closedDate?: string;
  respondents: CampaignRespondent[];
}

// ── Helpers ─────────────────────────────────────────────────────────────

export function campaignProgress(c: AttestationCampaign): {
  total: number; completed: number; inProgress: number; pending: number; overdue: number; exempt: number;
  completedPct: number;
} {
  const total = c.respondents.length;
  const completed = c.respondents.filter(r => r.status === 'COMPLETED').length;
  const inProgress = c.respondents.filter(r => r.status === 'IN_PROGRESS').length;
  const pending = c.respondents.filter(r => r.status === 'PENDING').length;
  const overdue = c.respondents.filter(r => r.status === 'OVERDUE').length;
  const exempt = c.respondents.filter(r => r.status === 'EXEMPT').length;
  const considered = total - exempt;
  const completedPct = considered > 0 ? Math.round((completed / considered) * 100) : 0;
  return { total, completed, inProgress, pending, overdue, exempt, completedPct };
}

export const CAMPAIGN_STATUS_TONE: Record<CampaignStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  DRAFT:     'neutral',
  IN_FLIGHT: 'info',
  CLOSING:   'warning',
  COMPLETED: 'success',
  CANCELLED: 'danger',
};

export const CAMPAIGN_STATUS_LABEL: Record<CampaignStatus, { en: string; ar: string }> = {
  DRAFT:     { en: 'Draft',         ar: 'مسودة' },
  IN_FLIGHT: { en: 'In flight',     ar: 'قيد التنفيذ' },
  CLOSING:   { en: 'Closing window', ar: 'نافذة الإغلاق' },
  COMPLETED: { en: 'Completed',     ar: 'مُنجَزة' },
  CANCELLED: { en: 'Cancelled',     ar: 'مُلغاة' },
};

export const RESPONDENT_STATUS_TONE: Record<RespondentStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  PENDING:     'neutral',
  IN_PROGRESS: 'info',
  COMPLETED:   'success',
  EXEMPT:      'neutral',
  OVERDUE:     'danger',
};

export const RESPONDENT_STATUS_LABEL: Record<RespondentStatus, { en: string; ar: string }> = {
  PENDING:     { en: 'Pending',     ar: 'بانتظار' },
  IN_PROGRESS: { en: 'In progress', ar: 'قيد التنفيذ' },
  COMPLETED:   { en: 'Completed',   ar: 'مُنجزة' },
  EXEMPT:      { en: 'Exempt',      ar: 'مُستثنى' },
  OVERDUE:     { en: 'Overdue',     ar: 'متأخر' },
};

export const CAMPAIGN_KIND_LABEL: Record<CampaignKind, { en: string; ar: string }> = {
  CONTROL_EFFECTIVENESS:    { en: 'Control effectiveness',    ar: 'فاعلية الضوابط' },
  POLICY_ACKNOWLEDGEMENT:   { en: 'Policy acknowledgement',   ar: 'الإقرار بالسياسات' },
  COI_ANNUAL:               { en: 'Annual COI disclosure',    ar: 'الإفصاح السنوي عن تضارب المصالح' },
  CODE_OF_CONDUCT:          { en: 'Code-of-Conduct re-attest', ar: 'إعادة الإقرار بمدوّنة السلوك' },
  VENDOR_ATTESTATION:       { en: 'Vendor attestation',       ar: 'إقرار المورد' },
  SUB_PROCESSOR_REVIEW:     { en: 'Sub-processor review',     ar: 'مراجعة المعالجين الفرعيين' },
  SECURITY_AWARENESS_QUIZ:  { en: 'Security awareness quiz',  ar: 'اختبار الوعي الأمني' },
};

// ── Seed campaigns ─────────────────────────────────────────────────────

export const ATTESTATION_CAMPAIGNS: AttestationCampaign[] = [
  {
    id: 'camp-001',
    code: 'CAMP-2026-Q2-CTRL',
    kind: 'CONTROL_EFFECTIVENESS',
    titleEn: 'Q2 2026 Quarterly Control Effectiveness Attestation',
    titleAr: 'إقرار الفاعلية ربع السنوي للضوابط — الربع الثاني 2026',
    descriptionEn: 'Every control owner attests to operating effectiveness for the controls in their portfolio. Required for ISO 27001 §9.2 internal audit evidence.',
    descriptionAr: 'يُقرّ كل مالك ضابط بفاعلية ضوابطه التشغيلية. مطلوب كدليل للتدقيق الداخلي وفقاً لـ ISO 27001 §9.2.',
    status: 'IN_FLIGHT',
    reference: 'ISO 27001:2022 §9.2, NCA ECC 1.1.4',
    owner: 'audit.lead@tss.sa',
    startDate: '2026-04-01',
    dueDate: '2026-05-15',
    respondents: [
      { id: 'r-001', responder: 'iam.team@tss.sa', responderName: 'IAM Team Lead', scope: 'AC-2.* family', status: 'COMPLETED', submittedAt: '2026-04-10', intakeRefCode: 'CA-K9R7-3M2P' },
      { id: 'r-002', responder: 'soc.lead@tss.sa', responderName: 'SOC Lead', scope: 'A.8.16 monitoring', status: 'COMPLETED', submittedAt: '2026-04-12', intakeRefCode: 'CA-J3W8-7N4X' },
      { id: 'r-003', responder: 'pki.team@tss.sa', responderName: 'PKI Team', scope: 'CYB-1.4 cryptography', status: 'IN_PROGRESS', lastSeenAt: '2026-04-22' },
      { id: 'r-004', responder: 'pam.team@tss.sa', responderName: 'PAM Team', scope: 'AC-2.1 privileged access', status: 'PENDING' },
      { id: 'r-005', responder: 'bcm.team@tss.sa', responderName: 'BCM Team', scope: '8.4 BCM strategies', status: 'PENDING' },
      { id: 'r-006', responder: 'dpo@tss.sa', responderName: 'DPO Office', scope: 'PDPL family', status: 'COMPLETED', submittedAt: '2026-04-08', intakeRefCode: 'CA-Q1W2-X8Z9' },
      { id: 'r-007', responder: 'tprm.lead@tss.sa', responderName: 'TPRM Lead', scope: 'SAMA outsourcing', status: 'IN_PROGRESS', lastSeenAt: '2026-04-25' },
      { id: 'r-008', responder: 'patch.team@tss.sa', responderName: 'Patch Team', scope: 'A.8.8 vuln mgmt', status: 'OVERDUE' },
    ],
  },
  {
    id: 'camp-002',
    code: 'CAMP-2026-COI-ANNUAL',
    kind: 'COI_ANNUAL',
    titleEn: 'Annual Conflict-of-Interest Disclosure 2026',
    titleAr: 'الإفصاح السنوي عن تضارب المصالح 2026',
    descriptionEn: 'Every employee with procurement / approval authority confirms COI status for the calendar year.',
    descriptionAr: 'كل موظف لديه صلاحية مشتريات أو اعتماد يؤكّد حالة تضارب المصالح للسنة التقويمية.',
    status: 'IN_FLIGHT',
    reference: 'ISO 37001 §8.3, IFAC §150, Nazaha',
    owner: 'ethics.officer@tss.sa',
    startDate: '2026-01-01',
    dueDate: '2026-02-28',
    respondents: [
      { id: 'r-101', responder: 'ciso@tss.sa', responderName: 'CISO', status: 'COMPLETED', submittedAt: '2026-01-15' },
      { id: 'r-102', responder: 'cfo@tss.sa', responderName: 'CFO', status: 'COMPLETED', submittedAt: '2026-01-22' },
      { id: 'r-103', responder: 'cro@tss.sa', responderName: 'CRO', status: 'COMPLETED', submittedAt: '2026-01-08' },
      { id: 'r-104', responder: 'audit.lead@tss.sa', responderName: 'Internal Audit Lead', status: 'COMPLETED', submittedAt: '2026-01-12' },
      { id: 'r-105', responder: 'tprm.lead@tss.sa', responderName: 'TPRM Lead', status: 'OVERDUE' },
      { id: 'r-106', responder: 'procurement.lead@tss.sa', responderName: 'Procurement Lead', status: 'COMPLETED', submittedAt: '2026-01-18' },
    ],
  },
  {
    id: 'camp-003',
    code: 'CAMP-2026-PDPL-POLICY',
    kind: 'POLICY_ACKNOWLEDGEMENT',
    titleEn: 'PDPL Privacy Policy Re-acknowledgement (Annual)',
    titleAr: 'إعادة الإقرار بسياسة الخصوصية (سنوي)',
    descriptionEn: 'All employees re-acknowledge the Privacy & Personal Data Policy following the 2026 PDPL Implementing Regulations refresh.',
    descriptionAr: 'يُعيد جميع الموظفين الإقرار بسياسة الخصوصية بعد تحديث 2026 للائحة التنفيذية لنظام حماية البيانات.',
    status: 'COMPLETED',
    reference: 'PDPL Art. 5, ISO 27001 §A.5.36',
    owner: 'dpo@tss.sa',
    startDate: '2026-03-01',
    dueDate: '2026-03-31',
    closedDate: '2026-04-02',
    respondents: [
      { id: 'r-201', responder: 'all-staff', responderName: 'All Staff', status: 'COMPLETED', submittedAt: '2026-03-31' },
    ],
  },
  {
    id: 'camp-004',
    code: 'CAMP-2026-VENDOR-ATTEST',
    kind: 'VENDOR_ATTESTATION',
    titleEn: 'Critical-Vendor Annual Attestation',
    titleAr: 'الإقرار السنوي للموردين الحرجين',
    descriptionEn: 'Each Tier-1 vendor confirms continued compliance with the SLAs, security controls and PDPL safeguards in their contract.',
    descriptionAr: 'يؤكّد كل مورد من الدرجة الأولى استمرار الامتثال لاتفاقيات مستوى الخدمة والضوابط الأمنية وضمانات حماية البيانات في عقده.',
    status: 'DRAFT',
    reference: 'SAMA Outsourcing §5.3, ISO 27036',
    owner: 'tprm.lead@tss.sa',
    startDate: '2026-06-01',
    dueDate: '2026-07-31',
    respondents: [
      { id: 'r-301', responder: 'aws-ksa', responderName: 'AWS (Saudi Arabia)', status: 'PENDING' },
      { id: 'r-302', responder: 'stc-cloud', responderName: 'STC Cloud', status: 'PENDING' },
      { id: 'r-303', responder: 'cloudflare', responderName: 'Cloudflare', status: 'PENDING' },
      { id: 'r-304', responder: 'auth0', responderName: 'Auth0 (Okta)', status: 'PENDING' },
      { id: 'r-305', responder: 'stripe', responderName: 'Stripe', status: 'PENDING' },
    ],
  },
];
