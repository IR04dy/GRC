export interface Vendor {
  id: number;
  name: string;
  service: string;
  tier: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  score: number;
  nextReview: string;
  status: 'ACTIVE' | 'UNDER_REVIEW' | 'PENDING';
}

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

export const VENDORS: Vendor[] = [
  { id: 1, name: 'Microsoft Saudi Arabia', service: 'Cloud Infrastructure',  tier: 'CRITICAL', score: 18, nextReview: '2026-09-15', status: 'ACTIVE' },
  { id: 2, name: 'STC Solutions',           service: 'Telecom & Connectivity', tier: 'CRITICAL', score: 22, nextReview: '2026-08-10', status: 'ACTIVE' },
  { id: 3, name: 'Saudi Information Tech',  service: 'IT Services',           tier: 'HIGH',     score: 41, nextReview: '2026-06-22', status: 'UNDER_REVIEW' },
  { id: 4, name: 'Elm Co.',                 service: 'Identity Verification', tier: 'HIGH',     score: 33, nextReview: '2026-07-08', status: 'ACTIVE' },
  { id: 5, name: 'Mobily Business',         service: 'WAN & Internet',        tier: 'MEDIUM',   score: 52, nextReview: '2026-05-30', status: 'ACTIVE' },
  { id: 6, name: 'Cloud Solutions Co.',     service: 'Backup & DR',           tier: 'MEDIUM',   score: 67, nextReview: '2026-05-15', status: 'UNDER_REVIEW' },
  { id: 7, name: 'Datapro Saudi',           service: 'Help Desk Outsourcing', tier: 'LOW',      score: 12, nextReview: '2026-11-02', status: 'ACTIVE' },
  { id: 8, name: 'Riyadh Print Services',   service: 'Document Management',   tier: 'LOW',      score: 8,  nextReview: '2026-12-15', status: 'PENDING' },
];
