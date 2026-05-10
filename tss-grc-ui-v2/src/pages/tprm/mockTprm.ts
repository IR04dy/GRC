// Demo-mode fallback for the TPRM module. Aligned with the
// TprmVendorDto shape in lib/api.ts so the vendor register, KPIs,
// expiring-contracts panel and per-vendor detail page render with
// realistic data offline. No fields left blank.
import type { TprmVendorDto } from '../../lib/api';
import type { VendorTier, OnboardingStage } from '../../lib/vendorOnboarding';

// ── Per-vendor detail-tab mocks ────────────────────────────────────────
export interface VendorAssessmentRow {
  id: number; date: string; type: 'INITIAL' | 'PERIODIC' | 'TRIGGERED'; assessor: string; score: number; risk: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'; status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED'; findings: string;
}
export const VENDOR_ASSESSMENTS_BY_ID: Record<number, VendorAssessmentRow[]> = {
  1: [
    { id: 11, date: '2026-03-15', type: 'PERIODIC',  assessor: 'KPMG ME',         score: 92, risk: 'LOW',    status: 'COMPLETED', findings: 'Annual SOC2 review — clean opinion.' },
    { id: 12, date: '2025-10-08', type: 'TRIGGERED', assessor: 'Internal TPRM',   score: 88, risk: 'LOW',    status: 'COMPLETED', findings: 'Triggered by Saudi sovereign-cloud transition; no issues.' },
  ],
  2: [
    { id: 21, date: '2026-04-02', type: 'TRIGGERED', assessor: 'Internal TPRM',   score: 70, risk: 'MEDIUM', status: 'COMPLETED', findings: 'Two outages in Q1 above 4h MTPD; SD-WAN diversification recommended.' },
    { id: 22, date: '2025-08-12', type: 'PERIODIC',  assessor: 'Internal TPRM',   score: 85, risk: 'LOW',    status: 'COMPLETED', findings: 'Routine annual review.' },
  ],
  3: [
    { id: 31, date: '2026-04-21', type: 'INITIAL',   assessor: 'Mona Al-Bishi',   score: 58, risk: 'HIGH',   status: 'IN_PROGRESS', findings: 'Manufacturing-floor segmentation gaps; awaiting samples.' },
  ],
  4: [
    { id: 41, date: '2026-02-28', type: 'PERIODIC',  assessor: 'Internal TPRM',   score: 80, risk: 'MEDIUM', status: 'COMPLETED', findings: 'PDPL Article 29 partial compliance — migration plan accepted.' },
  ],
  5: [
    { id: 51, date: '2026-04-02', type: 'TRIGGERED', assessor: 'Internal TPRM',   score: 70, risk: 'MEDIUM', status: 'COMPLETED', findings: 'Two outages in Q1 above 4h MTPD; escalated to Risk.' },
  ],
};

export interface VendorContractRow {
  id: number; contractNumber: string; titleEn: string; type: string; startDate: string; endDate: string; value: number; currency: string; status: string;
}
export const VENDOR_CONTRACTS_BY_ID: Record<number, VendorContractRow[]> = {
  1: [
    { id: 101, contractNumber: 'AWS-EA-2025-001', titleEn: 'Enterprise Agreement (Cloud)',  type: 'MSA',     startDate: '2025-01-01', endDate: '2027-12-31', value: 18_500_000, currency: 'SAR', status: 'ACTIVE' },
    { id: 102, contractNumber: 'AWS-PSO-2024-088', titleEn: 'Professional Services Order',  type: 'SOW',     startDate: '2024-09-01', endDate: '2025-09-01', value: 1_800_000,  currency: 'SAR', status: 'CLOSED' },
  ],
  2: [
    { id: 201, contractNumber: 'STC-MSP-2024-077', titleEn: 'Sovereign Cloud Hosting',       type: 'MSA',     startDate: '2024-06-01', endDate: '2026-06-30', value: 9_200_000,  currency: 'SAR', status: 'EXPIRING_SOON' },
  ],
  3: [
    { id: 301, contractNumber: 'SIT-IT-2024-014', titleEn: 'Managed IT Services',            type: 'MSA',     startDate: '2024-03-15', endDate: '2027-03-14', value: 5_400_000,  currency: 'SAR', status: 'ACTIVE' },
  ],
  4: [
    { id: 401, contractNumber: 'ELM-2025-022',    titleEn: 'Identity Verification API',      type: 'SUBSCRIPTION', startDate: '2025-04-01', endDate: '2027-03-31', value: 2_100_000, currency: 'SAR', status: 'ACTIVE' },
  ],
  5: [
    { id: 501, contractNumber: 'MBL-WAN-2025-031', titleEn: 'WAN + SIP Master Services',     type: 'MSA',     startDate: '2025-03-01', endDate: '2026-08-31', value: 6_750_000,  currency: 'SAR', status: 'EXPIRING_SOON' },
  ],
};

export const VENDORS: TprmVendorDto[] = [
  { id: 1,
    vendorName:    'Microsoft Saudi Arabia',
    vendorNameAr:  'مايكروسوفت العربية السعودية',
    vendorType:    'Cloud Infrastructure',
    country:       'Saudi Arabia',
    riskRating:    'CRITICAL', status: 'ACTIVE',
    contractEndDate: '2027-12-31',
    description:   'Sovereign-region M365 + Azure tenant hosting Tier-0 customer-facing workloads. SAMA-classified critical service provider; covered by signed SOC 2 Type II.' },
  { id: 2,
    vendorName:    'STC Solutions',
    vendorNameAr:  'حلول الاتصالات السعودية',
    vendorType:    'Sovereign Cloud Hosting',
    country:       'Saudi Arabia',
    riskRating:    'CRITICAL', status: 'ACTIVE',
    contractEndDate: '2026-06-30',
    description:   'Sovereign cloud hosting for systems handling national-classified data. CITC level-4 certified; contract expiring soon, renewal under negotiation.' },
  { id: 3,
    vendorName:    'Saudi Information Tech',
    vendorNameAr:  'تقنية المعلومات السعودية',
    vendorType:    'Managed IT Services',
    country:       'Saudi Arabia',
    riskRating:    'HIGH',     status: 'UNDER_REVIEW',
    contractEndDate: '2027-03-14',
    description:   'On-site managed IT services covering desktop, network and end-user support. Triggered re-assessment after manufacturing-floor segmentation gap surfaced.' },
  { id: 4,
    vendorName:    'Elm Co.',
    vendorNameAr:  'شركة علم',
    vendorType:    'Identity Verification API',
    country:       'Saudi Arabia',
    riskRating:    'HIGH',     status: 'ACTIVE',
    contractEndDate: '2027-03-31',
    description:   'Government-issued ID verification API used in customer onboarding (Absher integration). PDPL Article 29 partial-compliance mitigation in flight.' },
  { id: 5,
    vendorName:    'Mobily Business',
    vendorNameAr:  'موبايلي للأعمال',
    vendorType:    'WAN + SIP',
    country:       'Saudi Arabia',
    riskRating:    'MEDIUM',   status: 'ACTIVE',
    contractEndDate: '2026-08-31',
    description:   'WAN connectivity + SIP termination for headquarters and branches. Two outages above 4-hour MTPD in Q1; SD-WAN diversification recommended.' },
  { id: 6,
    vendorName:    'Cloud Solutions Co.',
    vendorNameAr:  'شركة الحلول السحابية',
    vendorType:    'Backup & Disaster Recovery',
    country:       'Saudi Arabia',
    riskRating:    'MEDIUM',   status: 'UNDER_REVIEW',
    contractEndDate: '2026-12-15',
    description:   'Cloud backup + DR-as-a-service for the financial-systems estate. SOC 2 attestation overdue — vendor escalation tracked under finding AUD-F-036.' },
  { id: 7,
    vendorName:    'Datapro Saudi',
    vendorNameAr:  'دتابرو السعودية',
    vendorType:    'Help Desk Outsourcing',
    country:       'Saudi Arabia',
    riskRating:    'LOW',      status: 'ACTIVE',
    contractEndDate: '2027-11-02',
    description:   'Outsourced Tier-1/Tier-2 help desk for internal users. Limited customer-data exposure; quarterly access recertification on file.' },
  { id: 8,
    vendorName:    'Riyadh Print Services',
    vendorNameAr:  'خدمات الطباعة بالرياض',
    vendorType:    'Document Management',
    country:       'Saudi Arabia',
    riskRating:    'LOW',      status: 'PENDING',
    contractEndDate: '2026-12-15',
    description:   'Managed print + secure-document destruction. Contract pending finance approval; renewal includes ISO 27001 SoA addendum.' },
  { id: 9,
    vendorName:    'Tabby Pay',
    vendorNameAr:  'تابي للمدفوعات',
    vendorType:    'Payments — BNPL',
    country:       'Saudi Arabia',
    riskRating:    'HIGH',     status: 'ACTIVE',
    contractEndDate: '2026-10-31',
    description:   'Buy-Now-Pay-Later integration for retail checkout. SAMA-licensed; SCA flow validated against Open Banking phase 3 requirements.' },
  { id: 10,
    vendorName:    'AWS Middle East (Bahrain)',
    vendorNameAr:  'أمازون ويب سيرفسز الشرق الأوسط',
    vendorType:    'Cloud Infrastructure',
    country:       'Bahrain',
    riskRating:    'CRITICAL', status: 'ACTIVE',
    contractEndDate: '2027-12-31',
    description:   'Cross-border cloud workloads (analytics + AI). PDPL Article 29 transfer mechanism via SDAIA-approved standard contractual clauses.' },
  { id: 11,
    vendorName:    'Tenable Network Security',
    vendorNameAr:  'تينابل لأمن الشبكات',
    vendorType:    'Vulnerability Management',
    country:       'United States',
    riskRating:    'MEDIUM',   status: 'ACTIVE',
    contractEndDate: '2027-04-30',
    description:   'External + internal vulnerability scanning. Quarterly Nessus reports anchor NCA ECC §5.2 evidence (EV-1082).' },
  { id: 12,
    vendorName:    'CyberArk',
    vendorNameAr:  'سايبر آرك',
    vendorType:    'Privileged Access Management',
    country:       'Israel',
    riskRating:    'HIGH',     status: 'ACTIVE',
    contractEndDate: '2027-06-30',
    description:   'Privileged session recording + vault for production infrastructure. Quarterly recertification log anchors NCA ECC AC-2 evidence (EV-1077).' },

  // ── DD-74: in-pipeline vendors (still onboarding). One per stage so
  //          the new Onboarding Pipeline panel has content in each lane.
  { id: 13,
    vendorName:    'Snowflake KSA',
    vendorNameAr:  'سنوفليك السعودية',
    vendorType:    'Cloud Data Warehouse',
    country:       'Saudi Arabia',
    riskRating:    'HIGH',     status: 'ONBOARDING',
    contractEndDate: '',
    description:   'Sovereign-region data-warehouse for analytics workloads. Just submitted by Data Office — pending tier classification.' },
  { id: 14,
    vendorName:    'Datadog',
    vendorNameAr:  'داتا دوغ',
    vendorType:    'Observability / APM',
    country:       'United States',
    riskRating:    'MEDIUM',   status: 'ONBOARDING',
    contractEndDate: '',
    description:   'SaaS observability for the production estate. TPRM analyst classifying into tier — cross-border log shipping under review.' },
  { id: 15,
    vendorName:    'Lookout Mobile Security',
    vendorNameAr:  'لوك أوت لأمن الأجهزة الجوّالة',
    vendorType:    'Mobile Threat Defense',
    country:       'United States',
    riskRating:    'MEDIUM',   status: 'ONBOARDING',
    contractEndDate: '',
    description:   'Mobile-device threat detection + EDR. Sanctions screen and ZATCA tax compliance in progress.' },
  { id: 16,
    vendorName:    'Cloudflare ME',
    vendorNameAr:  'كلاودفلير الشرق الأوسط',
    vendorType:    'WAF + CDN',
    country:       'Bahrain',
    riskRating:    'HIGH',     status: 'ONBOARDING',
    contractEndDate: '',
    description:   'Edge WAF + DDoS protection for customer-facing properties. Awaiting CISO Office sign-off on SOC 2 + DPA review.' },
];

// ── DD-74: TPRM metadata layer ─────────────────────────────────────────
// Frontend-only augmentation of the vendor record with the data the
// onboarding pipeline + reassessment cadence engine needs:
//   - tier            → drives reassessment cadence (T1 annual …)
//   - onboardingStage → drives the kanban pipeline lane
//   - lastAssessedAt  → drives the next-review-due computation
//
// Backend wiring will land in a follow-up; for demo mode this map is the
// source of truth. Today's date for the seed math = 2026-05-05.

export interface VendorTprmMeta {
  tier: VendorTier;
  onboardingStage: OnboardingStage;
  /** ISO date string. */
  lastAssessedAt: string;
}

export const VENDOR_TPRM_META: Record<number, VendorTprmMeta> = {
  // ── Active (already through onboarding) ─────────────
  1:  { tier: 'TIER_1', onboardingStage: 'ACTIVE', lastAssessedAt: '2025-03-15' }, // 14 mo → OVERDUE for T1
  2:  { tier: 'TIER_1', onboardingStage: 'ACTIVE', lastAssessedAt: '2025-04-12' }, // 13 mo → OVERDUE
  3:  { tier: 'TIER_2', onboardingStage: 'ACTIVE', lastAssessedAt: '2026-04-21' }, // just reassessed → ON_TRACK
  4:  { tier: 'TIER_1', onboardingStage: 'ACTIVE', lastAssessedAt: '2025-09-05' }, // 8 mo → ON_TRACK
  5:  { tier: 'TIER_2', onboardingStage: 'ACTIVE', lastAssessedAt: '2024-04-05' }, // 25 mo → OVERDUE for T2
  6:  { tier: 'TIER_2', onboardingStage: 'ACTIVE', lastAssessedAt: '2024-11-05' }, // 18 mo → ON_TRACK
  7:  { tier: 'TIER_3', onboardingStage: 'ACTIVE', lastAssessedAt: '2023-11-05' }, // 30 mo → DUE_SOON for T3
  8:  { tier: 'TIER_3', onboardingStage: 'CONTRACT',       lastAssessedAt: '2026-04-20' }, // mid-contract
  9:  { tier: 'TIER_1', onboardingStage: 'ACTIVE', lastAssessedAt: '2026-02-05' }, // 3 mo → ON_TRACK
  10: { tier: 'TIER_1', onboardingStage: 'ACTIVE', lastAssessedAt: '2025-06-05' }, // 11 mo → DUE_SOON
  11: { tier: 'TIER_3', onboardingStage: 'ACTIVE', lastAssessedAt: '2023-05-05' }, // 36 mo → OVERDUE
  12: { tier: 'TIER_2', onboardingStage: 'ACTIVE', lastAssessedAt: '2024-06-05' }, // 23 mo → DUE_SOON

  // ── In-pipeline (DD-74 onboarding stages) ────────────
  13: { tier: 'TIER_2', onboardingStage: 'INTAKE',          lastAssessedAt: '2026-05-01' },
  14: { tier: 'TIER_3', onboardingStage: 'TIER_ASSESSMENT', lastAssessedAt: '2026-04-25' },
  15: { tier: 'TIER_3', onboardingStage: 'DUE_DILIGENCE',   lastAssessedAt: '2026-04-15' },
  16: { tier: 'TIER_1', onboardingStage: 'SECURITY_REVIEW', lastAssessedAt: '2026-04-10' },
};
