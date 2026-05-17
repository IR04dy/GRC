// Audit Universe — the inventory of every auditable entity / process /
// framework that *could* be audited. industry-standard GRC tooling equivalent:
// `audit_universe` table.
//
// Standards anchor:
//   IIA IPPF 2010.A1 — "the chief audit executive must establish a risk-
//                       based plan to determine the priorities of the
//                       internal audit activity"
//   COBIT MEA02      — independent assurance over the system of internal
//                       control
//   NCA ECC 1.7      — internal-audit function with documented audit plan
//   SAMA CSF / CMA   — annual audit-plan submission expected
//
// The universe is the input to the Audit Plan: each plan row picks an
// entity from the universe and schedules an engagement against it.

export type UniverseEntityType =
  | 'BUSINESS_UNIT'    // top-level division (Treasury, Retail Banking, …)
  | 'PROCESS'          // operational process (KYC onboarding, AML monitoring)
  | 'IT_PLATFORM'      // tech estate (core banking, cloud, identity)
  | 'FRAMEWORK'        // compliance domain (NCA ECC, PDPL, SAMA CSF)
  | 'OUTSOURCED';      // critical vendor / outsourced function

export type RiskRating = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface AuditUniverseEntity {
  /** Stable code joined to AuditPlan rows. */
  code: string;
  type: UniverseEntityType;
  /** Bilingual name. */
  nameEn: string;
  nameAr: string;
  /** Higher-level entity (e.g. PROCESS rolls up to BUSINESS_UNIT). */
  parentCode?: string;
  /** Owner stakeholder code (joins to STAKEHOLDERS). */
  ownerCode?: string;
  /** Risk rating drives audit-plan priority. */
  riskRating: RiskRating;
  /** Last engagement that audited this entity (yyyy-mm-dd). */
  lastAuditedAt?: string;
  /** Recommended audit cycle in months. */
  cycleMonths: number;
  /** Optional regulator framework codes that mandate coverage of this entity. */
  regulatorMandate?: string[];
  /** Optional brief description. */
  noteEn?: string;
}

// ── Universe data — illustrative for a Saudi financial / mid-market entity
export const AUDIT_UNIVERSE: AuditUniverseEntity[] = [
  // ── Business units ───────────────────────────────────────────────────────
  { code: 'BU-RETAIL',  type: 'BUSINESS_UNIT', nameEn: 'Retail Banking',         nameAr: 'الأعمال المصرفية للأفراد',
    ownerCode: 'COO', riskRating: 'HIGH',     cycleMonths: 12, lastAuditedAt: '2025-03-15' },
  { code: 'BU-CORP',    type: 'BUSINESS_UNIT', nameEn: 'Corporate + Institutional', nameAr: 'العملاء المؤسّسيون',
    ownerCode: 'COO', riskRating: 'HIGH',     cycleMonths: 12, lastAuditedAt: '2025-06-20' },
  { code: 'BU-TREASURY', type: 'BUSINESS_UNIT', nameEn: 'Treasury + Markets',       nameAr: 'الخزينة والأسواق',
    ownerCode: 'CFO', riskRating: 'CRITICAL', cycleMonths: 12, lastAuditedAt: '2025-09-10', regulatorMandate: ['SAMA_CSF', 'CMA'] },
  { code: 'BU-OPS',     type: 'BUSINESS_UNIT', nameEn: 'Operations + Back Office', nameAr: 'العمليات والمساندة',
    ownerCode: 'COO', riskRating: 'MEDIUM',   cycleMonths: 18, lastAuditedAt: '2025-01-30' },

  // ── Critical processes ───────────────────────────────────────────────────
  { code: 'PROC-KYC',   type: 'PROCESS', parentCode: 'BU-RETAIL',  nameEn: 'Customer onboarding (KYC)',   nameAr: 'تأهيل العملاء (اعرف عميلك)',
    ownerCode: 'AML_OFFICER', riskRating: 'CRITICAL', cycleMonths: 12, lastAuditedAt: '2026-01-15',
    regulatorMandate: ['SAMA_AML', 'FATF'],
    noteEn: 'Saudi sovereign-ID + Absher integration; high regulator focus post AML 2023.' },
  { code: 'PROC-AML',   type: 'PROCESS', parentCode: 'BU-OPS',     nameEn: 'AML transaction monitoring',  nameAr: 'مراقبة المعاملات لمكافحة غسل الأموال',
    ownerCode: 'AML_OFFICER', riskRating: 'CRITICAL', cycleMonths: 6, lastAuditedAt: '2025-12-08',
    regulatorMandate: ['SAMA_AML', 'FATF'] },
  { code: 'PROC-PAYMENTS', type: 'PROCESS', parentCode: 'BU-RETAIL', nameEn: 'Payments + settlements',   nameAr: 'المدفوعات والتسويات',
    ownerCode: 'COO', riskRating: 'CRITICAL', cycleMonths: 12, lastAuditedAt: '2025-04-22',
    regulatorMandate: ['SAMA_CSF', 'SAMA_OPEN_BANKING'] },
  { code: 'PROC-LENDING', type: 'PROCESS', parentCode: 'BU-RETAIL', nameEn: 'Credit underwriting',        nameAr: 'الإقراض والتقييم الائتماني',
    ownerCode: 'CRO', riskRating: 'HIGH', cycleMonths: 12, lastAuditedAt: '2025-08-12' },
  { code: 'PROC-PRIVACY', type: 'PROCESS', parentCode: 'BU-OPS',    nameEn: 'Customer-data processing',  nameAr: 'معالجة بيانات العملاء',
    ownerCode: 'DPO', riskRating: 'HIGH', cycleMonths: 12, lastAuditedAt: '2026-04-15',
    regulatorMandate: ['PDPL'] },
  { code: 'PROC-HR',    type: 'PROCESS', parentCode: 'BU-OPS',     nameEn: 'HR + payroll',                nameAr: 'الموارد البشرية والرواتب',
    ownerCode: 'HR_DIRECTOR', riskRating: 'MEDIUM', cycleMonths: 24, lastAuditedAt: '2024-11-05',
    regulatorMandate: ['HRSD'] },
  { code: 'PROC-PROCUREMENT', type: 'PROCESS', parentCode: 'BU-OPS', nameEn: 'Procurement + vendor mgmt', nameAr: 'المشتريات وإدارة الموردين',
    ownerCode: 'TPRM_LEAD', riskRating: 'MEDIUM', cycleMonths: 18, lastAuditedAt: '2025-05-30' },
  { code: 'PROC-FINREP', type: 'PROCESS', parentCode: 'BU-TREASURY', nameEn: 'Financial reporting',      nameAr: 'التقارير المالية',
    ownerCode: 'CFO', riskRating: 'CRITICAL', cycleMonths: 12, lastAuditedAt: '2026-02-28',
    regulatorMandate: ['CMA', 'ZATCA'] },

  // ── IT platforms ─────────────────────────────────────────────────────────
  { code: 'IT-CORE-BANK', type: 'IT_PLATFORM', nameEn: 'Core banking system',     nameAr: 'النظام المصرفي الأساسي',
    ownerCode: 'CIO', riskRating: 'CRITICAL', cycleMonths: 12, lastAuditedAt: '2025-10-15',
    regulatorMandate: ['SAMA_CSF', 'NCA_ECC'] },
  { code: 'IT-CLOUD',   type: 'IT_PLATFORM', nameEn: 'Public cloud estate',        nameAr: 'البنية السحابية العامة',
    ownerCode: 'CIO', riskRating: 'HIGH',     cycleMonths: 12, lastAuditedAt: '2025-11-20',
    regulatorMandate: ['CITC', 'NCA_ECC'] },
  { code: 'IT-IAM',     type: 'IT_PLATFORM', nameEn: 'Identity + access mgmt',     nameAr: 'إدارة الهويات والصلاحيات',
    ownerCode: 'CISO', riskRating: 'HIGH',    cycleMonths: 12, lastAuditedAt: '2026-03-10',
    regulatorMandate: ['NCA_ECC'] },
  { code: 'IT-SOC',     type: 'IT_PLATFORM', nameEn: 'SOC + SIEM',                  nameAr: 'مركز عمليات الأمن وSIEM',
    ownerCode: 'CISO', riskRating: 'HIGH',    cycleMonths: 6,  lastAuditedAt: '2026-04-04',
    regulatorMandate: ['NCA_ECC'] },

  // ── Compliance frameworks ───────────────────────────────────────────────
  { code: 'FW-ISO-27001', type: 'FRAMEWORK', nameEn: 'ISO/IEC 27001 ISMS',          nameAr: 'نظام إدارة أمن المعلومات (الآيزو 27001)',
    ownerCode: 'CISO', riskRating: 'HIGH', cycleMonths: 12, lastAuditedAt: '2026-04-04',
    regulatorMandate: ['ISO_27001'] },
  { code: 'FW-NCA-ECC', type: 'FRAMEWORK', nameEn: 'NCA Essential Cybersecurity Controls', nameAr: 'الضوابط الأساسية للأمن السيبراني',
    ownerCode: 'CISO', riskRating: 'CRITICAL', cycleMonths: 12, lastAuditedAt: '2026-04-04',
    regulatorMandate: ['NCA_ECC'] },
  { code: 'FW-PDPL',    type: 'FRAMEWORK', nameEn: 'Saudi PDPL',                     nameAr: 'نظام حماية البيانات الشخصية',
    ownerCode: 'DPO', riskRating: 'CRITICAL', cycleMonths: 12, lastAuditedAt: '2026-04-15',
    regulatorMandate: ['PDPL'] },
  { code: 'FW-ISO-22301', type: 'FRAMEWORK', nameEn: 'ISO 22301 BCMS',               nameAr: 'نظام إدارة استمرارية الأعمال',
    ownerCode: 'BCM_LEAD', riskRating: 'HIGH', cycleMonths: 12, lastAuditedAt: '2026-02-12',
    regulatorMandate: ['ISO_22301', 'NCEMA'] },
  { code: 'FW-SAMA-CSF', type: 'FRAMEWORK', nameEn: 'SAMA Cyber Security Framework', nameAr: 'إطار البنك المركزي السعودي للأمن السيبراني',
    ownerCode: 'CISO', riskRating: 'CRITICAL', cycleMonths: 12, lastAuditedAt: '2026-04-22',
    regulatorMandate: ['SAMA_CSF'] },
  { code: 'FW-SOC-2',   type: 'FRAMEWORK', nameEn: 'SOC 2 (TSC 2017)',               nameAr: 'تقرير الضوابط (سوك 2)',
    ownerCode: 'COMPLIANCE_LEAD', riskRating: 'HIGH', cycleMonths: 12, lastAuditedAt: '2026-03-10',
    regulatorMandate: ['SOC_2'] },
  { code: 'FW-PCI-DSS', type: 'FRAMEWORK', nameEn: 'PCI DSS v4.0',                    nameAr: 'معيار أمن بيانات بطاقات الدفع',
    ownerCode: 'CISO', riskRating: 'HIGH', cycleMonths: 12, lastAuditedAt: '2026-01-30',
    regulatorMandate: ['PCI_DSS'] },

  // ── Outsourced critical functions ───────────────────────────────────────
  { code: 'OUT-CLOUD-MS', type: 'OUTSOURCED', nameEn: 'Microsoft Saudi (cloud)',     nameAr: 'مايكروسوفت العربية السعودية (سحابة)',
    ownerCode: 'TPRM_LEAD', riskRating: 'CRITICAL', cycleMonths: 12, lastAuditedAt: '2026-03-15' },
  { code: 'OUT-STC',    type: 'OUTSOURCED', nameEn: 'STC sovereign cloud',           nameAr: 'الحوسبة السحابية السيادية لـ STC',
    ownerCode: 'TPRM_LEAD', riskRating: 'CRITICAL', cycleMonths: 12, lastAuditedAt: '2025-08-12' },
  { code: 'OUT-BACKUP', type: 'OUTSOURCED', nameEn: 'Backup + DR vendor',            nameAr: 'مورد النسخ الاحتياطي والتعافي',
    ownerCode: 'BCM_LEAD', riskRating: 'HIGH', cycleMonths: 12 },
];

// ── Helpers ────────────────────────────────────────────────────────────────

/** "Months since last audit" — undefined when never audited. */
export function monthsSinceLastAudit(entity: AuditUniverseEntity, now = new Date()): number | undefined {
  if (!entity.lastAuditedAt) return undefined;
  const diff = now.getTime() - new Date(entity.lastAuditedAt).getTime();
  return Math.round(diff / (30.44 * 86_400_000));
}

/** True when next audit is within 90 days OR overdue. */
export function isAuditDueSoon(entity: AuditUniverseEntity, now = new Date()): boolean {
  if (!entity.lastAuditedAt) return true;
  const months = monthsSinceLastAudit(entity, now) ?? 0;
  return months >= entity.cycleMonths - 3;
}

/** Months overdue (negative if not yet due). */
export function monthsOverdue(entity: AuditUniverseEntity, now = new Date()): number {
  if (!entity.lastAuditedAt) return entity.cycleMonths;
  return (monthsSinceLastAudit(entity, now) ?? 0) - entity.cycleMonths;
}

export const RISK_TONE_UNIVERSE: Record<RiskRating, 'danger' | 'warning' | 'info' | 'success'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'success',
};
