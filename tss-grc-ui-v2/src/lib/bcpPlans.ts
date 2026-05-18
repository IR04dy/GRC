// BCP/DRP plan builder client — audit T1.4b.

import { api } from './api';

export type BcpPlanType = 'BCP' | 'DRP';
export type BcpPlanStatus =
  | 'DRAFT' | 'UNDER_REVIEW' | 'APPROVED' | 'PUBLISHED'
  | 'ARCHIVED' | 'SUPERSEDED' | 'CANCELLED';

export interface BcpPlan {
  id: number;
  organisationId?: number;
  planCode: string;
  titleEn: string;
  titleAr?: string;
  planType: BcpPlanType;
  sourceBiaId?: number;
  serviceId?: number;
  processId?: number;
  rtoHours?: number;
  rpoHours?: number;
  mtpdHours?: number;
  planOwnerId?: number;
  responseTeamRole?: string;
  status: BcpPlanStatus;
  currentVersion: number;
  approvedBy?: string;
  approvedAt?: string;
  publishedAt?: string;
  lastExercisedAt?: string;
  nextReviewDate?: string;
  /** Tier-2 #10 attestation linkage. */
  lastAttestedAt?:     string;
  lastAttestedByName?: string;
  reviewState?:        'CURRENT' | 'DUE_SOON' | 'OVERDUE';
}

export interface BcpSection {
  id: number;
  planId: number;
  sectionKey: string;
  sectionTitleEn: string;
  sectionTitleAr?: string;
  sectionOrder: number;
  bodyEn?: string;
  bodyAr?: string;
  lastEditedBy?: string;
  lastEditedAt?: string;
}

export interface BcpVersion {
  id: number;
  planId: number;
  versionNumber: number;
  bodyJson: string;
  approvedBy?: string;
  approvedAt?: string;
  changeSummary?: string;
  createdAt?: string;
}

export interface SectionDef { key: string; titleEn: string; titleAr: string; }
export interface VersionDiff { from: number; to: number; fromJson: string; toJson: string; }

const BASE = '/api/v1/bcm/plans';

export function planStatusTone(s: BcpPlanStatus): 'neutral' | 'info' | 'warning' | 'success' | 'danger' {
  switch (s) {
    case 'DRAFT':         return 'neutral';
    case 'UNDER_REVIEW':  return 'warning';
    case 'APPROVED':      return 'info';
    case 'PUBLISHED':     return 'success';
    case 'ARCHIVED':      return 'neutral';
    case 'SUPERSEDED':    return 'neutral';
    case 'CANCELLED':     return 'danger';
  }
}

export async function listPlans(planType?: BcpPlanType, page = 0, size = 100): Promise<{ content: BcpPlan[]; totalElements: number }> {
  const q = new URLSearchParams();
  if (planType) q.set('planType', planType);
  q.set('page', String(page)); q.set('size', String(size));
  return api.get<{ content: BcpPlan[]; totalElements: number }>(`${BASE}?${q.toString()}`);
}
export async function planStats(): Promise<Record<string, number>> {
  return api.get<Record<string, number>>(`${BASE}/stats`);
}
export async function sectionCatalogue(type: BcpPlanType): Promise<SectionDef[]> {
  return api.get<SectionDef[]>(`${BASE}/section-catalogue/${type}`);
}
export async function getPlan(id: number): Promise<BcpPlan> {
  return api.get<BcpPlan>(`${BASE}/${id}`);
}
export async function createPlan(body: Partial<BcpPlan>): Promise<BcpPlan> {
  return api.post<BcpPlan>(BASE, body);
}
export async function patchPlan(id: number, body: Partial<BcpPlan>): Promise<BcpPlan> {
  return api.patch<BcpPlan>(`${BASE}/${id}`, body);
}
export async function transitionPlan(id: number, to: BcpPlanStatus): Promise<BcpPlan> {
  return api.post<BcpPlan>(`${BASE}/${id}/transition`, { to });
}
export async function reopenPlan(id: number): Promise<BcpPlan> {
  return api.post<BcpPlan>(`${BASE}/${id}/reopen`, {});
}
export async function generatePlanFromBia(biaId: number, planType: BcpPlanType,
                                          titleEn?: string, titleAr?: string): Promise<BcpPlan> {
  return api.post<BcpPlan>(`${BASE}/generate-from-bia`, { biaId, planType, titleEn, titleAr });
}
export async function listSections(planId: number): Promise<BcpSection[]> {
  return api.get<BcpSection[]>(`${BASE}/${planId}/sections`);
}
export async function addSection(planId: number, body: Partial<BcpSection>): Promise<BcpSection> {
  return api.post<BcpSection>(`${BASE}/${planId}/sections`, body);
}
export async function editSection(sectionId: number, bodyEn?: string, bodyAr?: string): Promise<BcpSection> {
  return api.patch<BcpSection>(`${BASE}/sections/${sectionId}`, { bodyEn, bodyAr });
}
export async function listVersions(planId: number): Promise<BcpVersion[]> {
  return api.get<BcpVersion[]>(`${BASE}/${planId}/versions`);
}
export async function diffVersions(planId: number, from: number, to: number): Promise<VersionDiff> {
  return api.get<VersionDiff>(`${BASE}/${planId}/versions/diff?from=${from}&to=${to}`);
}
