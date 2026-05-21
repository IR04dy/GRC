import { api } from './api';

/** Tier-4 audit #23 — NCEMA After-Action Report bindings. */

export type AarStatus = 'DRAFT' | 'UNDER_REVIEW' | 'APPROVED' | 'PUBLISHED' | 'RESCINDED';

export interface CrisisAar {
  id: number;
  organisationId: number;
  crisisEventId: number;
  reportCode: string;
  titleEn: string;
  titleAr?: string | null;

  executiveSummaryEn?: string | null;
  executiveSummaryAr?: string | null;
  timelineMdEn?: string | null;
  timelineMdAr?: string | null;
  decisionsMdEn?: string | null;
  decisionsMdAr?: string | null;
  whatWorkedMdEn?: string | null;
  whatWorkedMdAr?: string | null;
  whatFailedMdEn?: string | null;
  whatFailedMdAr?: string | null;
  rootCauseMdEn?: string | null;
  rootCauseMdAr?: string | null;
  recommendationsMdEn?: string | null;
  recommendationsMdAr?: string | null;
  regulatoryNotificationsMd?: string | null;
  lessonsLearnedIdsJson?: string | null;
  correctiveActionIdsJson?: string | null;
  gapIdsJson?: string | null;

  status: AarStatus;
  approvedAt?: string | null;
  publishedAt?: string | null;
  digitalSignatureTxnId?: number | null;

  createdAt: string;
  updatedAt?: string | null;
}

export const aar = {
  list: () => api.get<CrisisAar[]>('/api/v1/bcm/aars'),
  listForEvent: (crisisEventId: number) =>
    api.get<CrisisAar[]>(`/api/v1/bcm/aars/event/${crisisEventId}`),
  get: (id: number) => api.get<CrisisAar>(`/api/v1/bcm/aars/${id}`),
  generate: (crisisEventId: number) =>
    api.post<CrisisAar>(`/api/v1/bcm/aars/generate/${crisisEventId}`),
  update: (id: number, body: Partial<CrisisAar>) =>
    api.put<CrisisAar>(`/api/v1/bcm/aars/${id}`, body),
  transition: (id: number, target: AarStatus, digitalSignatureTxnId?: number) =>
    api.post<CrisisAar>(`/api/v1/bcm/aars/${id}/transitions/${target}`,
      digitalSignatureTxnId ? { digitalSignatureTxnId } : {}),
};
