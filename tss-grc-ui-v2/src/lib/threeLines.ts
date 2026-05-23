// Three Lines of Defense (3LoD) API client — Sprint 11.
//
// Wires the frontend to the V166 governance module shipped by this PR.
// The shapes here mirror the backend DTOs:
//
//   ThreeLinesOverviewDto      → ThreeLinesOverview
//   ThreeLinesOverviewDto.BandCounts → BandCounts
//   ThreeLinesArtefactDto      → ThreeLinesArtefact
//   UserBandDto                → UserBand
//   RoleMappingUpdateRequest   → RoleMappingUpdateRequest
//
// Pure helpers (effectiveBand, decodeLodBands) live in {@code
// threeLinesHelpers.ts} so unit tests can import them without dragging
// in the {@code import.meta.env}-using api client.

import { api } from './api';
import type { LodBand } from './threeLinesHelpers';
export { effectiveBand, decodeLodBands, type LodBand } from './threeLinesHelpers';

export interface BandCounts {
  lodBand: LodBand;
  titleEn: string;
  titleAr: string;
  /** Saudi-native column subtitle — short, role-aligned not literal. */
  subtitleEn: string;
  subtitleAr: string;
  risksOpen: number;
  findingsOpen: number;
  capaOpen: number;
  policiesPendingApproval: number;
  dsrCasesOpen: number;
  overdueItems: number;
  approvalsPending: number;
  roleNames: string[];
}

export interface ThreeLinesOverview {
  bands: BandCounts[];
  totalRisksOpen: number;
  totalFindingsOpen: number;
  totalCapaOpen: number;
  totalPoliciesPendingApproval: number;
  totalOverdue: number;
}

export type SlaStatus = 'ON_TRACK' | 'DUE_SOON' | 'OVERDUE';
export type ArtefactType = 'RISK' | 'FINDING' | 'CAPA' | 'POLICY' | 'DSR';

export interface ThreeLinesArtefact {
  artefactType: ArtefactType;
  id: number;
  externalId: string | null;
  titleEn: string | null;
  titleAr: string | null;
  severity: string | null;
  status: string | null;
  ownerLabel: string | null;
  dueDate: string | null;     // ISO yyyy-MM-dd
  slaStatus: SlaStatus;
  escalatedToBoard: boolean;
}

export interface UserBand {
  userId: number;
  username: string;
  roleNames: string[];
  allBands: LodBand[];
  effectiveBand: LodBand | null;
}

export interface RoleMappingUpdateRequest {
  roleName: string;
  lodBand: LodBand;
  titleEn?: string;
  titleAr?: string;
  rationaleEn?: string;
  rationaleAr?: string;
}

export interface ThreeLinesRoleMapping {
  id: number;
  organisationId: number | null;
  roleName: string;
  lodBand: LodBand;
  titleEn: string;
  titleAr: string;
  rationaleEn: string | null;
  rationaleAr: string | null;
}

const BASE = '/api/v1/governance/three-lines';

export const threeLinesApi = {
  overview: () => api.get<ThreeLinesOverview>(`${BASE}/overview`),
  drillDown: (band: LodBand) => api.get<ThreeLinesArtefact[]>(`${BASE}/line/${band}`),
  userBand:  (userId: number) => api.get<UserBand>(`${BASE}/user/${userId}/role-band`),
  upsertMapping: (req: RoleMappingUpdateRequest) =>
    api.post<ThreeLinesRoleMapping>(`${BASE}/role-mapping`, req),
};

