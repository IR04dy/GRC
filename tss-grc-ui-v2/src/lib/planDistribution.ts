// Plan distribution + acknowledgement client — audit Tier-2 #11.
//
// Surfaces /api/v1/bcm/plans/{planId}/distribution/* to the UI as
// typed wrappers. The shared `api` helper resolves to the parsed
// JSON body directly (NOT axios-style {data:T}) — do not add `.data`.

import { api } from './api';

export type AckStatus = 'PENDING' | 'ACKNOWLEDGED' | 'DECLINED' | 'STALE';
export type DistributionChannel = 'EMAIL' | 'PORTAL' | 'IN_PERSON' | 'PRINT' | 'OTHER';

export interface PlanAcknowledgement {
  id:                   number;
  organisationId:       number;
  planId:               number;
  planVersion:          number;
  recipientUserId?:     number;
  recipientName:        string;
  recipientEmail?:      string;
  recipientRole?:       string;
  distributedAt:        string;
  distributedById?:     number;
  distributedByName?:   string;
  distributionChannel:  DistributionChannel;
  distributionNotes?:   string;
  status:               AckStatus;
  acknowledgedAt?:      string;
  acknowledgementNotes?:string;
  createdAt?:           string;
  updatedAt?:           string;
}

export interface Recipient {
  userId?: number;
  name:    string;
  email?:  string;
  role?:   string;
}

export interface DistributionStats {
  planVersion:         number;
  total:               number;
  pending:             number;
  acknowledged:        number;
  declined:            number;
  stale:               number;
  acknowledgedPercent: number;
}

/** Map a status to a tone for badge / progress rendering. */
export function ackStatusTone(s: AckStatus): 'warning' | 'success' | 'danger' | 'neutral' {
  switch (s) {
    case 'PENDING':       return 'warning';
    case 'ACKNOWLEDGED':  return 'success';
    case 'DECLINED':      return 'danger';
    case 'STALE':         return 'neutral';
  }
}

const BASE = '/api/v1/bcm/plans';

export function listDistribution(planId: number): Promise<PlanAcknowledgement[]> {
  return api.get<PlanAcknowledgement[]>(`${BASE}/${planId}/distribution`);
}

export function distributionStats(planId: number): Promise<DistributionStats> {
  return api.get<DistributionStats>(`${BASE}/${planId}/distribution/stats`);
}

export function distribute(planId: number, recipients: Recipient[],
                           channel: DistributionChannel = 'EMAIL',
                           notes?: string): Promise<PlanAcknowledgement[]> {
  return api.post<PlanAcknowledgement[]>(`${BASE}/${planId}/distribution`,
    { recipients, channel, notes });
}

export function acknowledge(ackId: number, notes?: string): Promise<PlanAcknowledgement> {
  return api.post<PlanAcknowledgement>(`${BASE}/distribution/${ackId}/acknowledge`, { notes });
}

export function decline(ackId: number, notes?: string): Promise<PlanAcknowledgement> {
  return api.post<PlanAcknowledgement>(`${BASE}/distribution/${ackId}/decline`, { notes });
}
