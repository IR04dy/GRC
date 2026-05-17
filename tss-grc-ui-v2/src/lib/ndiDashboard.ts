// NDI Dashboard API client (slice 12).

import { api } from './api';

export interface DomainCard {
  code: string;
  nameEn: string;
  nameAr?: string;
  weight: number;
  blendedScorePct: number;
  isPlaceholder: boolean;
}

export interface UpcomingItem {
  type: 'evidence' | 'capa';
  code?: string;
  titleEn?: string;
  titleAr?: string;
  dueDate: string;
  daysUntil: number;
  severity?: string;
}

export interface DashboardSnapshot {
  cycleId: number;
  cycleNameEn?: string;
  cycleNameAr?: string;
  reportingYear?: number;
  cycleStatus?: string;
  overallNdiPct?: number;
  maturityScorePct?: number;
  compliancePct?: number;
  opexScorePct?: number;
  totalDomains: number;
  placeholderDomains: number;
  totalMaturity: number;     maturityApproved: number;
  totalCompliance: number;   complianceApproved: number;
  totalOpex: number;         opexAchieved: number;       opexBehindPlan: number;
  totalEvidence: number;     evidenceActive: number;     evidenceExpiringSoon: number; evidenceExpired: number;
  totalGaps: number;         criticalOpen: number;       gapsResolved: number;
  totalCapas: number;        capasCompleted: number;     capasOverdue: number;
  totalPlans: number;        plansInFlight: number;      plansCompleted: number;
  submissionsTotal: number;  submissionsAcknowledged: number;
  topUnderperforming: DomainCard[];
  upcoming: UpcomingItem[];
  latestSubmission: Record<string, unknown>;
}

export function getDashboard(cycleId: number) {
  return api.get<DashboardSnapshot>(`/api/ndi/dashboard?cycleId=${cycleId}`);
}
