/**
 * Per-authority obligation register — what each regulator requires the
 * organisation to do. industry-standard GRC tooling "Obligation" / Continuous-Compliance
 * pattern.
 *
 * Each obligation cites the source clause and tracks the org's internal
 * fulfilment status (compliant / in-progress / gap / N/A) with an inline
 * pointer to the control or evidence that satisfies it.
 *
 * Bilingual; keyed by AuthorityDto.code from `lib/authorities.ts`.
 * The set is illustrative — meant for demos, not exhaustive.
 */

export type ObligationStatus = 'COMPLIANT' | 'IN_PROGRESS' | 'GAP' | 'NOT_APPLICABLE';

export interface AuthorityObligation {
  /** Stable obligation code (authority + framework clause). */
  code: string;
  /** Bilingual title. */
  titleEn: string;
  titleAr: string;
  /** Framework code (joined to FrameworkDto.code). */
  frameworkCode: string;
  /** Domain (used for grouping in the workspace). */
  domain: 'GOVERNANCE' | 'CYBER' | 'PRIVACY' | 'BCM' | 'TPRM' | 'AML' | 'TAX' | 'TELECOM' | 'CLOUD';
  /** Date the obligation became (or becomes) mandatory. */
  mandatoryFrom: string;
  /** Org's current state of fulfilment. */
  internalStatus: ObligationStatus;
  /** Pointer to the control / policy / evidence ref that satisfies it. */
  internalRef?: string;
  /** Optional note explaining current state. */
  noteEn?: string;
}

const NCA: AuthorityObligation[] = [
  { code: 'NCA-ECC-1.1.1', titleEn: 'Approved cybersecurity strategy with executive sponsor',
    titleAr: 'استراتيجية معتمدة للأمن السيبراني مع راعٍ تنفيذي',
    frameworkCode: 'NCA_ECC', domain: 'GOVERNANCE', mandatoryFrom: '2018-04-01',
    internalStatus: 'COMPLIANT', internalRef: 'POL-ISMS-001 v3.2',
    noteEn: 'Strategy approved by board 2026-Q1; CISO is executive sponsor.' },
  { code: 'NCA-ECC-2.1.1', titleEn: 'Cybersecurity steering committee meeting at minimum quarterly',
    titleAr: 'اجتماع لجنة الأمن السيبراني الفصلي على الأقل',
    frameworkCode: 'NCA_ECC', domain: 'GOVERNANCE', mandatoryFrom: '2018-04-01',
    internalStatus: 'COMPLIANT', internalRef: 'EV-1083',
    noteEn: 'Q1 minutes attached as evidence.' },
  { code: 'NCA-ECC-3.4.1', titleEn: 'Encrypted backups with quarterly restore tests',
    titleAr: 'نسخ احتياطية مشفّرة مع اختبارات استعادة فصلية',
    frameworkCode: 'NCA_ECC', domain: 'CYBER', mandatoryFrom: '2018-04-01',
    internalStatus: 'COMPLIANT', internalRef: 'EV-1079' },
  { code: 'NCA-ECC-5.2.1', titleEn: 'External vulnerability scanning at minimum quarterly',
    titleAr: 'فحص الثغرات الخارجية فصلياً على الأقل',
    frameworkCode: 'NCA_ECC', domain: 'CYBER', mandatoryFrom: '2018-04-01',
    internalStatus: 'COMPLIANT', internalRef: 'EV-1082' },
  { code: 'NCA-ECC-7.2.4', titleEn: 'Incident-response plan tested annually with tabletop exercise',
    titleAr: 'خطة استجابة للحوادث مع تمرين سنوي',
    frameworkCode: 'NCA_ECC', domain: 'CYBER', mandatoryFrom: '2018-04-01',
    internalStatus: 'IN_PROGRESS', internalRef: 'AUD-2026-006',
    noteEn: '2026 tabletop scheduled for June; tracked in internal-audit engagement.' },
  { code: 'NCA-ECC-V2-MIG', titleEn: 'Migration to ECC v2.0 by mandatory date',
    titleAr: 'الترحيل إلى الإصدار 2 من الضوابط الأساسية',
    frameworkCode: 'NCA_ECC', domain: 'CYBER', mandatoryFrom: '2027-09-22',
    internalStatus: 'IN_PROGRESS', internalRef: 'Reg change #42',
    noteEn: 'Impact assessment complete; control gap analysis in flight.' },
];

const SAMA: AuthorityObligation[] = [
  { code: 'SAMA-CSF-1.1.1', titleEn: 'CSF self-assessment annually',
    titleAr: 'التقييم الذاتي السنوي لإطار البنك المركزي',
    frameworkCode: 'SAMA_CSF', domain: 'CYBER', mandatoryFrom: '2017-05-01',
    internalStatus: 'COMPLIANT', internalRef: 'AUD-2026-003' },
  { code: 'SAMA-CSF-3.3.5', titleEn: 'Privileged-access recertification at minimum quarterly',
    titleAr: 'إعادة اعتماد الصلاحيات المميّزة فصلياً',
    frameworkCode: 'SAMA_CSF', domain: 'CYBER', mandatoryFrom: '2017-05-01',
    internalStatus: 'COMPLIANT', internalRef: 'EV-1077' },
  { code: 'SAMA-AML-2.4', titleEn: 'AML transaction monitoring with 100% coverage of high-risk customers',
    titleAr: 'مراقبة معاملات مكافحة غسل الأموال بتغطية كاملة للمخاطر العالية',
    frameworkCode: 'SAMA_AML', domain: 'AML', mandatoryFrom: '2018-01-01',
    internalStatus: 'GAP', internalRef: 'R-0036',
    noteEn: 'Risk register flags screening gap; remediation tracked in risk treatment plan.' },
  { code: 'SAMA-OB-3.2', titleEn: 'Open Banking SCA on every high-value transaction',
    titleAr: 'مصادقة قوية للعمليات المرتفعة في المصرفية المفتوحة',
    frameworkCode: 'SAMA_OPEN_BANKING', domain: 'CYBER', mandatoryFrom: '2026-10-08',
    internalStatus: 'IN_PROGRESS', internalRef: 'CHG-2026-013',
    noteEn: 'Auth flow updates rolling out via change request — 60% complete.' },
  { code: 'SAMA-BCP-4.1', titleEn: 'Business continuity testing at minimum semi-annually',
    titleAr: 'اختبار استمرارية الأعمال نصف سنوي على الأقل',
    frameworkCode: 'SAMA_BCP', domain: 'BCM', mandatoryFrom: '2017-05-01',
    internalStatus: 'COMPLIANT', internalRef: 'KRI-OPS-02' },
];

const SDAIA: AuthorityObligation[] = [
  { code: 'PDPL-Art-30', titleEn: 'Maintain Record of Processing Activities (RoPA)',
    titleAr: 'الاحتفاظ بسجل معالجة البيانات الشخصية',
    frameworkCode: 'PDPL', domain: 'PRIVACY', mandatoryFrom: '2023-09-14',
    internalStatus: 'COMPLIANT', internalRef: 'EV-1080' },
  { code: 'PDPL-Art-29', titleEn: 'Cross-border transfer requires SDAIA-approved mechanism',
    titleAr: 'نقل البيانات عبر الحدود يتطلب آلية معتمدة',
    frameworkCode: 'PDPL', domain: 'PRIVACY', mandatoryFrom: '2023-09-14',
    internalStatus: 'GAP', internalRef: 'AUD-F-031',
    noteEn: 'Standard Contractual Clauses with payroll vendor still under negotiation; finding open.' },
  { code: 'PDPL-Art-12', titleEn: 'Honour data-subject requests within 30 days',
    titleAr: 'الاستجابة لطلبات أصحاب البيانات خلال 30 يوماً',
    frameworkCode: 'PDPL', domain: 'PRIVACY', mandatoryFrom: '2023-09-14',
    internalStatus: 'IN_PROGRESS', internalRef: 'KRI-PRV-01',
    noteEn: 'KRI tracks DSR breaches; one breach detected this quarter.' },
  { code: 'PDPL-Art-26', titleEn: 'Conduct DPIA before launching high-risk processing',
    titleAr: 'تقييم أثر حماية البيانات قبل المعالجة عالية المخاطر',
    frameworkCode: 'PDPL', domain: 'PRIVACY', mandatoryFrom: '2023-09-14',
    internalStatus: 'COMPLIANT', internalRef: 'DPIA-41 / DPIA-43' },
  { code: 'PDPL-Art-21', titleEn: 'Notify SDAIA + affected subjects of personal-data breach',
    titleAr: 'إبلاغ هيئة البيانات وأصحابها عند الاختراق',
    frameworkCode: 'PDPL', domain: 'PRIVACY', mandatoryFrom: '2023-09-14',
    internalStatus: 'COMPLIANT', internalRef: 'POL-PDPL-001 v2.1' },
  { code: 'PDPL-AI-DPIA', titleEn: 'Mandatory DPIA for AI-based decision systems (2026 amendment)',
    titleAr: 'تقييم أثر إلزامي لأنظمة القرار بالذكاء الاصطناعي',
    frameworkCode: 'PDPL', domain: 'PRIVACY', mandatoryFrom: '2026-08-15',
    internalStatus: 'IN_PROGRESS', internalRef: 'Reg change #41',
    noteEn: 'Impact assessed; updates to DPIA template and AI policy pending.' },
];

const NRC: AuthorityObligation[] = [
  { code: 'NRC-TIER-MAP', titleEn: 'Tier-mapped supplier register (Tier 1, 2, 3)',
    titleAr: 'سجل الموردين مصنّف حسب المستوى',
    frameworkCode: 'NRC_TIER_MAPPING', domain: 'TPRM', mandatoryFrom: '2024-01-01',
    internalStatus: 'COMPLIANT', internalRef: 'SCM module' },
  { code: 'NRC-NAT-RISK', titleEn: 'National-risk-register submission semi-annually',
    titleAr: 'تقديم بيانات السجل الوطني للمخاطر نصف سنوياً',
    frameworkCode: 'NRC_NATIONAL_RISK', domain: 'GOVERNANCE', mandatoryFrom: '2024-01-01',
    internalStatus: 'IN_PROGRESS', internalRef: '—',
    noteEn: 'Q2 submission window opens June 1.' },
];

const CITC: AuthorityObligation[] = [
  { code: 'CITC-CCRF-3', titleEn: 'Cloud Computing Regulatory Framework v3 — sovereign residency for level-4',
    titleAr: 'إطار التنظيم الحوسبة السحابية الإصدار 3',
    frameworkCode: 'CITC_CCRF', domain: 'CLOUD', mandatoryFrom: '2026-09-15',
    internalStatus: 'IN_PROGRESS', internalRef: 'Reg change #46',
    noteEn: 'Workloads classified; sovereign migration plan for level-4 in flight.' },
];

const ZATCA: AuthorityObligation[] = [
  { code: 'ZATCA-FATOORA-W18', titleEn: 'E-invoicing Phase 2 wave 18 onboarding',
    titleAr: 'الفوترة الإلكترونية المرحلة 2 الموجة 18',
    frameworkCode: 'ZATCA_FATOORA', domain: 'TAX', mandatoryFrom: '2026-12-01',
    internalStatus: 'COMPLIANT', internalRef: 'Existing FATOORA integration',
    noteEn: 'Already covered by current integration.' },
];

export const AUTHORITY_OBLIGATIONS_BY_CODE: Record<string, AuthorityObligation[]> = {
  NCA, SAMA, SDAIA, NRC, CITC, ZATCA,
};

export const STATUS_LABEL_OBLIGATION: Record<ObligationStatus, string> = {
  COMPLIANT: 'Compliant',
  IN_PROGRESS: 'In progress',
  GAP: 'Gap',
  NOT_APPLICABLE: 'N/A',
};

export const STATUS_TONE_OBLIGATION: Record<ObligationStatus, 'success' | 'info' | 'danger' | 'neutral'> = {
  COMPLIANT: 'success',
  IN_PROGRESS: 'info',
  GAP: 'danger',
  NOT_APPLICABLE: 'neutral',
};
