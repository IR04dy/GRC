// F2-42 / ADR-017 D-5 — the board pack as data.
//
// Mirrors `reporting.dto.BoardPackAggregate`, served by
// `GET /api/v1/reporting/board-pack.json`. The same builder produces the PDF and
// the PPTX, so a figure on screen is the figure in the download by construction
// rather than by review.
//
// ─── What this replaces ───────────────────────────────────────────────────
// `lib/boardPack.ts` and its `buildBoardPack()`, deleted with this file's
// arrival. It assembled a board pack client-side from eight fabricated sources —
// three of them named `mockCompliance`, `mockPrivacy`, `mockWorkflows`, and one
// literally called `synthesiseAllBreaches()`. So the platform had two board packs
// that disagreed, and the PDF was the correct one.
//
// ⚠ **Do not fetch this through `useApiList`.** That hook takes mock data and
// substitutes it whenever the request fails or returns nothing, surfacing
// `isDemo: true`. That contract is right for a register and wrong here: a board
// pack silently falling back to demo figures is *precisely* F2-42, rebuilt behind
// a different door. This module therefore exposes a plain fetch, and the page
// renders explicit loading / forbidden / error states instead.
//
// ⚠ **Board-confidential.** The endpoint carries `hasPermission('BOARD','EXPORT')`
// — the same guard as the PDF and the deck, because the same figures leave the
// building either way (ADR-017 D-10a narrowed all three from 24 roles to 8). A
// 403 here is the control working; the page must say so rather than look broken.

import { api } from './api';

/** `Header` — who the pack is for and as of when. */
export interface BoardPackHeader {
  organisationId: number | null;
  organisationNameEn: string | null;
  organisationNameAr: string | null;
  generatedAt: string | null;   // ISO LocalDateTime
  asOf: string | null;          // ISO LocalDate
}

/** `Executive` — the ten headline counts. All server-computed. */
export interface BoardPackExecutive {
  totalRisks: number;
  criticalRisks: number;
  openRisks: number;
  openFindings: number;
  totalFindings: number;
  totalKris: number;
  totalControls: number;
  totalEthicsCases: number;
  strsFiled: number;
  amlAlerts: number;
}

export interface BoardPackRiskRow {
  id: number | null;
  /** The `R-00042` display id — formatted server-side so the PDF, the deck and
   *  this screen cannot show three different references for one risk. */
  reference: string | null;
  titleEn: string | null;
  titleAr: string | null;
  inherentScore: number | null;
  residualScore: number | null;
  status: string | null;
}

/** `Aging` — open findings by escalation tier. */
export interface BoardPackAging {
  totalOpen: number;
  onTrack: number;
  manager: number;
  director: number;
  executive: number;
  closed: number;
}

export interface BoardPackFindingRow {
  id: number | null;
  title: string | null;
  severity: string | null;
  owner: string | null;
  daysOpen: number | null;
  status: string | null;
}

export interface BoardPackKriRow {
  id: number | null;
  code: string | null;
  titleEn: string | null;
  titleAr: string | null;
  band: string | null;
  latestValue: number | null;
  greenThreshold: number | null;
  amberThreshold: number | null;
}

export interface BoardPackKris {
  green: number;
  amber: number;
  red: number;
  noData: number;
  rows: BoardPackKriRow[] | null;
}

export interface BoardPackFrameworkRow {
  code: string | null;
  nameEn: string | null;
  nameAr: string | null;
  controls: number;
  policies: number;
  fullPct: number | null;
  openFindings: number;
}

export interface BoardPackChanges {
  total: number;
  inFlight: number;
  success: number;
  rolledBack: number;
  emergency: number;
}

export interface BoardPackEthics {
  totalCases: number;
  open: number;
  substantiated: number;
  briberyOrKickback: number;
  amlSuspicion: number;
  retaliation: number;
  strsFiled: number;
}

export interface BoardPackSustainability {
  /** False when the sustainability dashboard could not be composed — render the
   *  section as unavailable rather than as zeroes, which read as "we emit nothing". */
  available: boolean;
  esgScore: number | null;
  esgBand: string | null;
  ghgTotal: number | null;
  scope3Total: number | null;
  cmaReadinessPct: number | null;
  tcfdAlignmentPct: number | null;
  sgiOnTrack: number;
  sgiTotal: number;
  openCapas: number | null;
  topGapCount: number;
}

/**
 * `frameworkCoverage` and `sustainability` are null under the server's DECK
 * scope — the two expensive sections the PPTX omits. This endpoint requests FULL,
 * so both are populated here; they stay nullable because the wire contract allows
 * null and a screen that assumed otherwise would crash on a scope change rather
 * than degrade.
 */
export interface BoardPackAggregate {
  header: BoardPackHeader | null;
  executive: BoardPackExecutive | null;
  topRisks: BoardPackRiskRow[] | null;
  aging: BoardPackAging | null;
  oldestOpenFindings: BoardPackFindingRow[] | null;
  kris: BoardPackKris | null;
  frameworkCoverage: BoardPackFrameworkRow[] | null;
  changes: BoardPackChanges | null;
  ethics: BoardPackEthics | null;
  sustainability: BoardPackSustainability | null;
}

/**
 * The live board pack for the caller's tenant.
 *
 * Takes no `lang`: every name in the aggregate is carried in both languages and
 * the consumer chooses, which is what lets one build serve an Arabic PDF and an
 * English deck from identical figures.
 */
export async function fetchBoardPack(): Promise<BoardPackAggregate> {
  return api.get<BoardPackAggregate>('/api/v1/reporting/board-pack.json');
}
