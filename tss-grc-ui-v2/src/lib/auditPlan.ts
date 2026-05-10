// Annual Audit Plan — risk-based selection from the AUDIT_UNIVERSE,
// scheduled across the year, with explicit Audit-Committee approval gate.
// ServiceNow IRM equivalent: `audit_plan` table.
//
// Standards anchor:
//   IIA IPPF 2010    — risk-based plan establishing audit priorities
//   IIA IPPF 2020    — communication + approval (Audit Cte sign-off)
//   IIA IPPF 2030    — resource management + plan-vs-actual reporting
//   COBIT MEA02.05   — independent assurance plan
//   NCA ECC 1.7.2    — annual audit plan + tracking
//   CMA Listing Rules §53 — audit-committee oversight of plan
//
// State machine: DRAFT → COMMITTEE_REVIEW → APPROVED → IN_FLIGHT → CLOSED.
// A plan in IN_FLIGHT exposes plan-vs-actual coverage; on CLOSED it is
// archived and the next-year plan can supersede it.

export type PlanStatus = 'DRAFT' | 'COMMITTEE_REVIEW' | 'APPROVED' | 'IN_FLIGHT' | 'CLOSED';
export type PlanQuarter = 'Q1' | 'Q2' | 'Q3' | 'Q4';
export type PlanRowStatus = 'PLANNED' | 'IN_FIELDWORK' | 'REPORT' | 'COMPLETED' | 'DEFERRED' | 'CANCELLED';

export interface AuditPlanRow {
  id: number;
  /** Joins to AUDIT_UNIVERSE.code — the auditable entity. */
  universeCode: string;
  /** Framework / scope this engagement targets (joins to FrameworkDto.code). */
  framework: string;
  /** Type of audit (matches AuditEngagementDto.auditType). */
  auditType: 'INTERNAL_AUDIT' | 'EXTERNAL_AUDIT' | 'CERTIFICATION_AUDIT' | 'REGULATORY_AUDIT';
  plannedQuarter: PlanQuarter;
  /** Auditor firm or team that will perform the engagement. */
  plannedAuditor: string;
  /** Estimated effort in person-days. */
  plannedDays: number;
  status: PlanRowStatus;
  /** Optional: id of the executed AuditEngagement. */
  linkedEngagementId?: number;
  /** Optional rationale — why this entity was prioritised. */
  rationaleEn?: string;
  rationaleAr?: string;
}

export interface AuditPlan {
  /** Plan year (e.g. 2026). */
  year: number;
  status: PlanStatus;
  /** All plan rows in scope this year. */
  rows: AuditPlanRow[];
  /** Audit-Committee approval timestamp + minute reference. */
  approvedAt?: string;
  approvedMinuteRef?: string;
  /** Free-form executive summary attached to the plan. */
  summaryEn?: string;
  summaryAr?: string;
}

// ── 2026 Audit Plan — the live demo plan ──────────────────────────────────
export const AUDIT_PLAN_2026: AuditPlan = {
  year: 2026,
  status: 'IN_FLIGHT',
  approvedAt: '2025-12-12T15:00:00',
  approvedMinuteRef: 'AUDIT-CTE-MIN-2025-12-12',
  summaryEn: 'Risk-based plan covering 12 critical/high-rated entities + the 5 framework certifications due in 2026. Plan adopted by the Audit Committee on 12 December 2025.',
  summaryAr: 'خطة قائمة على المخاطر تغطّي 12 جهة حرجة/عالية الخطورة و5 شهادات إطار مستحقّة في 2026. اعتمدتها لجنة المراجعة بتاريخ 12 ديسمبر 2025.',
  rows: [
    { id: 1, universeCode: 'PROC-AML',         framework: 'SAMA_AML',  auditType: 'REGULATORY_AUDIT',
      plannedQuarter: 'Q1', plannedAuditor: 'EY MENA',          plannedDays: 35, status: 'COMPLETED', linkedEngagementId: 63,
      rationaleEn: 'CRITICAL — SAMA AML mandate, R-0036 open finding.' },
    { id: 2, universeCode: 'FW-NCA-ECC',       framework: 'NCA_ECC',   auditType: 'REGULATORY_AUDIT',
      plannedQuarter: 'Q1', plannedAuditor: 'Internal Audit',   plannedDays: 25, status: 'COMPLETED',
      rationaleEn: 'CRITICAL — NCA ECC v2 transition impact.' },
    { id: 3, universeCode: 'FW-ISO-27001',     framework: 'ISO_27001', auditType: 'CERTIFICATION_AUDIT',
      plannedQuarter: 'Q1', plannedAuditor: 'KPMG ME',          plannedDays: 20, status: 'COMPLETED', linkedEngagementId: 61,
      rationaleEn: 'Annual surveillance — certificate maintenance.' },
    { id: 4, universeCode: 'FW-PCI-DSS',       framework: 'PCI_DSS',   auditType: 'EXTERNAL_AUDIT',
      plannedQuarter: 'Q1', plannedAuditor: 'AT&T QSA',         plannedDays: 18, status: 'COMPLETED', linkedEngagementId: 65,
      rationaleEn: 'PCI DSS annual re-assessment.' },
    { id: 5, universeCode: 'FW-SOC-2',         framework: 'SOC_2',     auditType: 'CERTIFICATION_AUDIT',
      plannedQuarter: 'Q1', plannedAuditor: 'PwC',              plannedDays: 22, status: 'COMPLETED', linkedEngagementId: 64,
      rationaleEn: 'SOC 2 Type II refresh.' },
    { id: 6, universeCode: 'FW-PDPL',          framework: 'PDPL',      auditType: 'EXTERNAL_AUDIT',
      plannedQuarter: 'Q2', plannedAuditor: 'Deloitte ME',      plannedDays: 30, status: 'REPORT', linkedEngagementId: 62,
      rationaleEn: 'CRITICAL — cross-border transfer compliance focus.' },
    { id: 7, universeCode: 'FW-SAMA-CSF',      framework: 'SAMA_CSF',  auditType: 'REGULATORY_AUDIT',
      plannedQuarter: 'Q2', plannedAuditor: 'EY MENA',          plannedDays: 40, status: 'IN_FIELDWORK',
      rationaleEn: 'CRITICAL — annual SAMA CSF self-assessment + external review.' },
    { id: 8, universeCode: 'IT-IAM',           framework: 'NCA_ECC',   auditType: 'INTERNAL_AUDIT',
      plannedQuarter: 'Q2', plannedAuditor: 'Internal Audit',   plannedDays: 15, status: 'IN_FIELDWORK', linkedEngagementId: 66,
      rationaleEn: 'PAM recertification follow-up — finding 32 mitigation.' },
    { id: 9, universeCode: 'PROC-PRIVACY',     framework: 'PDPL',      auditType: 'INTERNAL_AUDIT',
      plannedQuarter: 'Q2', plannedAuditor: 'Internal Audit',   plannedDays: 12, status: 'PLANNED',
      rationaleEn: 'HIGH — data-subject rights handling + RoPA freshness.' },
    { id: 10, universeCode: 'FW-ISO-22301',    framework: 'ISO_22301', auditType: 'CERTIFICATION_AUDIT',
      plannedQuarter: 'Q3', plannedAuditor: 'BSI',              plannedDays: 18, status: 'PLANNED',
      rationaleEn: 'Stage-2 ISO 22301 certification.' },
    { id: 11, universeCode: 'PROC-PAYMENTS',   framework: 'SAMA_OPEN_BANKING', auditType: 'REGULATORY_AUDIT',
      plannedQuarter: 'Q3', plannedAuditor: 'EY MENA',          plannedDays: 30, status: 'PLANNED',
      rationaleEn: 'CRITICAL — Open Banking phase 3 pre-go-live audit.' },
    { id: 12, universeCode: 'BU-TREASURY',     framework: 'CMA',       auditType: 'EXTERNAL_AUDIT',
      plannedQuarter: 'Q3', plannedAuditor: 'PwC',              plannedDays: 40, status: 'PLANNED',
      rationaleEn: 'CMA annual financial audit.' },
    { id: 13, universeCode: 'IT-SOC',          framework: 'NCA_ECC',   auditType: 'INTERNAL_AUDIT',
      plannedQuarter: 'Q3', plannedAuditor: 'Internal Audit',   plannedDays: 10, status: 'PLANNED',
      rationaleEn: 'Half-year SOC + SIEM coverage review.' },
    { id: 14, universeCode: 'OUT-CLOUD-MS',    framework: 'NCA_ECC',   auditType: 'INTERNAL_AUDIT',
      plannedQuarter: 'Q3', plannedAuditor: 'Internal Audit',   plannedDays: 12, status: 'PLANNED',
      rationaleEn: 'Critical-vendor right-to-audit invocation.' },
    { id: 15, universeCode: 'OUT-STC',         framework: 'CITC',      auditType: 'INTERNAL_AUDIT',
      plannedQuarter: 'Q4', plannedAuditor: 'Internal Audit',   plannedDays: 10, status: 'PLANNED',
      rationaleEn: 'Sovereign-cloud level-4 attestation.' },
    { id: 16, universeCode: 'PROC-FINREP',     framework: 'CMA',       auditType: 'EXTERNAL_AUDIT',
      plannedQuarter: 'Q4', plannedAuditor: 'PwC',              plannedDays: 35, status: 'PLANNED',
      rationaleEn: 'Year-end financial reporting + ISSB sustainability.' },
    { id: 17, universeCode: 'FW-NCA-ECC',      framework: 'NCA_ECC',   auditType: 'INTERNAL_AUDIT',
      plannedQuarter: 'Q4', plannedAuditor: 'Internal Audit',   plannedDays: 15, status: 'PLANNED',
      rationaleEn: 'NCA ECC v2 readiness verification before 2027 mandate.' },
    { id: 18, universeCode: 'PROC-PROCUREMENT', framework: 'NRC_TIER_MAPPING', auditType: 'INTERNAL_AUDIT',
      plannedQuarter: 'Q4', plannedAuditor: 'Internal Audit',   plannedDays: 8,  status: 'PLANNED',
      rationaleEn: 'Tier-mapping accuracy + concentration-risk follow-up.' },
  ],
};

// ── 2027 Plan — DRAFT (illustrates the lifecycle gate) ────────────────────
export const AUDIT_PLAN_2027: AuditPlan = {
  year: 2027,
  status: 'DRAFT',
  summaryEn: 'Drafting in flight; will go to Audit Committee for review in November 2026.',
  summaryAr: 'قيد الإعداد؛ سيُعرض على لجنة المراجعة للمراجعة في نوفمبر 2026.',
  rows: [
    { id: 101, universeCode: 'FW-NCA-ECC',  framework: 'NCA_ECC',  auditType: 'REGULATORY_AUDIT',
      plannedQuarter: 'Q1', plannedAuditor: 'TBD',             plannedDays: 35, status: 'PLANNED',
      rationaleEn: 'CRITICAL — NCA ECC v2 mandatory cut-over (effective Sep 2027).' },
    { id: 102, universeCode: 'FW-PDPL',     framework: 'PDPL',     auditType: 'EXTERNAL_AUDIT',
      plannedQuarter: 'Q2', plannedAuditor: 'TBD',             plannedDays: 30, status: 'PLANNED',
      rationaleEn: 'PDPL 2026 amendments first-year compliance.' },
  ],
};

export const AUDIT_PLANS: AuditPlan[] = [AUDIT_PLAN_2026, AUDIT_PLAN_2027];

// ── Helpers ────────────────────────────────────────────────────────────────

export const PLAN_STATUS_TONE: Record<PlanStatus, 'neutral' | 'info' | 'primary' | 'warning' | 'success'> = {
  DRAFT: 'neutral', COMMITTEE_REVIEW: 'info', APPROVED: 'primary',
  IN_FLIGHT: 'warning', CLOSED: 'success',
};

export const PLAN_STATUS_LABEL: Record<PlanStatus, { en: string; ar: string }> = {
  DRAFT:            { en: 'Draft',            ar: 'مسودة' },
  COMMITTEE_REVIEW: { en: 'Committee review', ar: 'مراجعة اللجنة' },
  APPROVED:         { en: 'Approved',         ar: 'مُعتمدة' },
  IN_FLIGHT:        { en: 'In flight',        ar: 'قيد التنفيذ' },
  CLOSED:           { en: 'Closed',           ar: 'مُغلقة' },
};

export const PLAN_ROW_STATUS_TONE: Record<PlanRowStatus, 'neutral' | 'info' | 'warning' | 'accent' | 'success' | 'danger'> = {
  PLANNED: 'neutral', IN_FIELDWORK: 'warning', REPORT: 'accent',
  COMPLETED: 'success', DEFERRED: 'info', CANCELLED: 'danger',
};

export const PLAN_ROW_STATUS_LABEL: Record<PlanRowStatus, { en: string; ar: string }> = {
  PLANNED:      { en: 'Planned',       ar: 'مُخطَّطة' },
  IN_FIELDWORK: { en: 'In fieldwork',  ar: 'عمل ميداني' },
  REPORT:       { en: 'Report',        ar: 'إصدار التقرير' },
  COMPLETED:    { en: 'Completed',     ar: 'مُنتهية' },
  DEFERRED:     { en: 'Deferred',      ar: 'مُؤجَّلة' },
  CANCELLED:    { en: 'Cancelled',     ar: 'مُلغاة' },
};

/** Plan-vs-actual coverage stats — what % of planned rows have been
    completed, currently in fieldwork, or still planned for the year. */
export function planCoverage(plan: AuditPlan): {
  total: number; completed: number; inFlight: number; planned: number; deferred: number; cancelled: number;
  completedPct: number; inFlightPct: number;
} {
  const total = plan.rows.length;
  const completed = plan.rows.filter(r => r.status === 'COMPLETED').length;
  const inFlight  = plan.rows.filter(r => r.status === 'IN_FIELDWORK' || r.status === 'REPORT').length;
  const planned   = plan.rows.filter(r => r.status === 'PLANNED').length;
  const deferred  = plan.rows.filter(r => r.status === 'DEFERRED').length;
  const cancelled = plan.rows.filter(r => r.status === 'CANCELLED').length;
  return {
    total, completed, inFlight, planned, deferred, cancelled,
    completedPct: total > 0 ? Math.round((completed / total) * 100) : 0,
    inFlightPct:  total > 0 ? Math.round((inFlight  / total) * 100) : 0,
  };
}
