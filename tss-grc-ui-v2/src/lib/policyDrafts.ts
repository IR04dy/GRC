// AI Policy Drafting API client (V401).

import { api } from './api';
import type { BadgeTone } from '../components/ui/Badge';
import type { ReviewCadence } from './policy';

export type PolicyDraftStatus =
  | 'REQUESTED'
  | 'GENERATING'
  | 'GENERATED'
  | 'FAILED'
  | 'ACCEPTED'
  | 'REJECTED';

export interface LlmStatusDto {
  enabled: boolean;
  mode: 'STUB' | 'LIVE';
  provider: string;
  model: string;
  credentialsConfigured: boolean;
  dailyTokenQuota: number;
  tokensUsedLast24h: number;
}

export interface CreatePolicyDraftRequest {
  policyType: string;
  category: string;
  tone?: string;
  frameworkIds?: number[];
  controlIds?: number[];
  applicabilityScope?: string;
  customInstructions?: string;
}

export interface UpdatePolicyDraftRequest {
  titleEn: string;
  titleAr: string;
  summaryEn?: string;
  summaryAr?: string;
  contentMdEn: string;
  contentMdAr: string;
  citedControlIds?: number[];
  suggestedReviewCadence?: ReviewCadence;
  assumptions?: string[];
  reviewerNote?: string;
}

export interface AcceptPolicyDraftRequest {
  policyCode: string;
  version?: string;
  ownerName?: string;
  ownerEmail?: string;
  reviewCadence?: ReviewCadence;
  applicabilityScope?: string;
  reviewerNote?: string;
}

export interface RejectPolicyDraftRequest {
  reviewerNote: string;
}

export interface GeneratedPolicyContent {
  titleEn: string;
  titleAr: string;
  summaryEn?: string;
  summaryAr?: string;
  contentMdEn: string;
  contentMdAr: string;
  citedControlIds: number[];
  suggestedReviewCadence?: ReviewCadence;
  assumptions: string[];
}

export interface PolicyDraftResponse {
  id: number;
  externalId: string;
  status: PolicyDraftStatus;
  policyType: string;
  category: string;
  tone: string;
  request?: CreatePolicyDraftRequest;
  generated?: GeneratedPolicyContent;
  edited?: GeneratedPolicyContent;
  hallucinatedCitations: number[];
  promptTemplateId: string;
  promptTemplateVersion: string;
  provider?: string;
  model?: string;
  inputTokens: number;
  outputTokens: number;
  latencyMs: number;
  failureReason?: string;
  acceptedPolicyId?: number;
  requestedBy: string;
  reviewedBy?: string;
  reviewedAt?: string;
  reviewerNote?: string;
  createdAt?: string;
  updatedAt?: string;
}

const BASE = '/api/policy/drafts';

export function getPolicyDraftGatewayStatus() {
  return api.get<LlmStatusDto>(`${BASE}/status`);
}

export function listPolicyDrafts(status?: PolicyDraftStatus) {
  const qs = status ? `?status=${encodeURIComponent(status)}` : '';
  return api.get<PolicyDraftResponse[]>(`${BASE}${qs}`);
}

export function getPolicyDraft(id: number) {
  return api.get<PolicyDraftResponse>(`${BASE}/${id}`);
}

export function requestPolicyDraft(body: CreatePolicyDraftRequest) {
  return api.post<PolicyDraftResponse>(BASE, body);
}

export function updatePolicyDraft(id: number, body: UpdatePolicyDraftRequest) {
  return api.put<PolicyDraftResponse>(`${BASE}/${id}`, body);
}

export function acceptPolicyDraft(id: number, body: AcceptPolicyDraftRequest) {
  return api.post<PolicyDraftResponse>(`${BASE}/${id}/accept`, body);
}

export function rejectPolicyDraft(id: number, body: RejectPolicyDraftRequest) {
  return api.post<PolicyDraftResponse>(`${BASE}/${id}/reject`, body);
}

export function draftStatusTone(s: PolicyDraftStatus): BadgeTone {
  switch (s) {
    case 'REQUESTED':  return 'neutral';
    case 'GENERATING': return 'info';
    case 'GENERATED':  return 'primary';
    case 'ACCEPTED':   return 'success';
    case 'FAILED':     return 'danger';
    case 'REJECTED':   return 'warning';
  }
}

export function draftStatusLabel(s: PolicyDraftStatus, lang: 'en' | 'ar'): string {
  const en: Record<PolicyDraftStatus, string> = {
    REQUESTED: 'Queued',
    GENERATING: 'Generating…',
    GENERATED: 'Ready for review',
    ACCEPTED: 'Accepted',
    FAILED: 'Failed',
    REJECTED: 'Rejected',
  };
  const ar: Record<PolicyDraftStatus, string> = {
    REQUESTED: 'في قائمة الانتظار',
    GENERATING: 'جارٍ التوليد…',
    GENERATED: 'جاهزة للمراجعة',
    ACCEPTED: 'مقبولة',
    FAILED: 'فشلت',
    REJECTED: 'مرفوضة',
  };
  return (lang === 'ar' ? ar : en)[s];
}
