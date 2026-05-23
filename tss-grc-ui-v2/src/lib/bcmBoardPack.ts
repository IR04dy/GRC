// BCM Board Pack section client — Slice 7-D (Sprint-7).
//
// Wraps GET /api/v1/bcm/board-pack-section — a single aggregator that
// returns the quarterly Board-pack BCM section: top critical processes
// by MTPD, last exercises, CAPA snapshot, last AAR, active crises,
// regulator notifications, and the headline Continuity Readiness Score.

import { api } from './api';

export interface CriticalProcessRow {
  processId: number;
  processNameEn: string;
  processNameAr?: string;
  criticality?: string;
  mtpdHours?: number;
  rtoHours?: number;
  rpoHours?: number;
  overallImpact?: string;
}

export interface ExerciseRow {
  id: number;
  exerciseNameEn: string;
  exerciseNameAr?: string;
  exerciseType?: string;
  executedDate?: string;
  status?: string;
  score?: number;
  capaCount?: number;
  facilitator?: string;
}

export interface CapaSnapshot {
  activeOpenCount: number;
  overdueCount: number;
  closedCount: number;
  byStatus: Record<string, number>;
}

export interface AarSummary {
  id: number;
  reportCode: string;
  titleEn: string;
  titleAr?: string;
  status: string;
  approvedAt?: string;
  publishedAt?: string;
  executiveSummaryExcerptEn?: string;
  executiveSummaryExcerptAr?: string;
  crisisEventId?: number;
}

export interface CrisisHeadline {
  id: number;
  eventCode: string;
  titleEn: string;
  titleAr?: string;
  severity?: number;
  status?: string;
  detectionTime?: string;
  personalDataAffected: boolean;
  cyberRelated: boolean;
  supplierInvolved: boolean;
}

export interface ActiveCrisisSnapshot {
  activeCount: number;
  bySeverity: Record<string, number>;
  top: CrisisHeadline[];
}

export interface RegulatorNotificationRow {
  id: number;
  crisisEventId: number;
  regulator: string;
  notifiedAt: string;
  method?: string;
  referenceNumber?: string;
  acknowledged: boolean;
  acknowledgementAt?: string;
  acknowledgementReference?: string;
}

export interface ReadinessHeadline {
  averageScore: number;
  biaCount: number;
  biasByCriticality: Record<string, number>;
}

export interface BoardPackSection {
  organisationId: number;
  generatedAt: string;
  topCriticalProcesses: CriticalProcessRow[];
  recentExercises: ExerciseRow[];
  capa: CapaSnapshot;
  lastAar?: AarSummary;
  activeCrises: ActiveCrisisSnapshot;
  regulatorNotifications: RegulatorNotificationRow[];
  readiness: ReadinessHeadline;
}

export async function boardPackSection(): Promise<BoardPackSection> {
  return api.get<BoardPackSection>('/api/v1/bcm/board-pack-section');
}
