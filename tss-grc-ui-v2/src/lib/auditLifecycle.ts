// Audit lifecycle — production hardening (Option 2).
//
// Wraps the existing audit engagement model with the full IIA Standards-
// aligned audit lifecycle that an Audit Committee chair in Saudi Arabia
// expects:
//
//   1. Annual audit plan — risk-based selection of engagements per quarter,
//      Audit Committee-approved per CMA Corporate Governance Regulations
//      Article 95 (Audit Committee responsibilities).
//   2. Working papers — per-engagement bundles of test work, interview
//      notes, observations and document reviews, with reviewer @mentions
//      per IIA Standard 2330 (Documenting Information).
//   3. Finding write-up — bilingual finding with severity, control
//      crosswalk to NCA ECC / SAMA RBSF, management response and target
//      remediation date.
//   4. CAPA tracking — corrective + preventive actions linked back to the
//      finding, with closure verification.
//   5. Audit Committee packet — quarterly executive report exporting
//      engagement progress, finding aging, CAPA closure rate and the next
//      quarter's plan.
//
// Persistence: in-memory module cache hydrated from /api/audit-lc/*
// (annual-plans / working-papers / findings / capas) plus the real audit-engagement
// catalogue at /api/v1/compliance/audit-engagements. EVERY mutation goes to the
// server and the cache is replaced from the server's response — see F2-203 below.
// The page subscribes via {@link subscribeAuditLifecycle} and re-renders through
// the emit() channel whenever the cache changes.
//
// ── F2-203 ───────────────────────────────────────────────────────────────────
// This file used to be the store F2-202 declared SERVER_READ_LOCAL_WRITE: it read
// four real registers and dropped every finding write on the floor. `saveFinding`
// and `transitionFinding` assigned a module-level array and returned, so an auditor
// closing an internal-audit finding under IIA Standard 2500 got a success toast and
// nothing was written — a reload put it back. The server side was complete and
// hardened the whole time (`POST /api/audit-lc/findings`, `POST /{id}/transition`
// with a guarded machine that answers 409, AUDIT:WRITE, FINDING_CREATED /
// FINDING_TRANSITIONED domain events, and a realtime broadcast this page ALREADY
// subscribed to — an event its own actions could never cause).
//
// It also treated `AUDIT_ENGAGEMENTS` — a `demoRows()` corpus mocking a DIFFERENT
// register — as the engagement authority. That corpus keys on AUD-2026-00x while
// the findings register keys on ENG-IA-00x / IA-2026-001, so `findingsForEngagement`
// and `workingPapersForEngagement` returned 0 rows for every one of the eight
// selectable engagements while 20 findings and 12 working papers sat in the table.
// 🔑 The empty tab was hiding a crash: `FINDING_STATUS_LABEL[f.status][lang]` is a
// TypeError for the 7 of 20 rows whose status the label map does not declare, and
// the only reason it never fired is that the filter removed every row first.
//
// `engagement_code`, `capa_tasks.finding_id` and `annual_plans.q*_codes` are free
// text with no foreign key and no CHECK — `audit_lc` has ZERO check constraints —
// so the code spaces cannot be reconciled from here. They are read tolerantly and
// the divergence is filed; rewriting 32 rows in two databases to agree is an owner
// decision, not a side effect of a display fix (F2-183's rule).

import { api, ApiError, complianceApi } from './api';
import type { AuditEngagementDto } from './api';

// ─── Types ──────────────────────────────────────────────────────────────────

/**
 * ⚠⚠ ALL FOUR of this module's registers hold status values their own service's vocabulary
 * does not declare, and `audit_lc` has ZERO CHECK constraints — not a subset, none — so
 * nothing ever refused them. Measured 2026-08-13, identical in dev and prod:
 *
 *   findings        7 of 20   IN_PROGRESS ×3, REMEDIATED ×2, VERIFIED ×2
 *   annual_plans    1 of 2    APPROVED            (the service declares AC_APPROVED)
 *   working_papers  8 of 12   REVIEWED            (the service declares PEER_REVIEW/APPROVED)
 *   capa_tasks      3 of 12   VERIFIED            (the service declares AWAITING_VERIFICATION)
 *   ────────────────────────
 *                  19 of 46 rows cannot be advanced by ANY route: each service does
 *                  `TRANSITIONS.getOrDefault(status, Set.of())`, so an undeclared status has
 *                  no outgoing edge and every transition answers 409 with an empty set.
 *
 * The annual-plan one is the sharpest: the single APPROVED row is the 2026 plan the
 * organisation is executing, and it can never be moved to EXECUTING or CLOSED.
 *
 * 🔑 Two of the three label maps crashed on these values — `MAP[status][lang]` is a TypeError
 * on an undeclared token — and the crash was hidden by accidents: for findings, a broken
 * engagement picker filtered every row away; for the annual plan, `plans[0]` happened to
 * select the DRAFT row. **F2-203's own fix to plan selection is what exposed it**, which is
 * the argument for the `*_UNMANAGED` declarations below: they are what makes the register
 * renderable at all.
 *
 * They are NOT folded into the managed vocabularies. Each reads as a synonym of a declared
 * state (APPROVED≈AC_APPROVED, REVIEWED≈APPROVED, VERIFIED≈CLOSED/AWAITING_VERIFICATION), and
 * collapsing synonyms rewrites 19 live rows in two databases — an owner decision, not a side
 * effect of a display fix (F2-183, F2-196).
 */
export type ManagedAnnualPlanStatus =
  'DRAFT' | 'AC_REVIEW' | 'AC_APPROVED' | 'EXECUTING' | 'CLOSED';
export type AnnualPlanStatus = ManagedAnnualPlanStatus | 'APPROVED';

/**
   * ⚠⚠ It is not only `status`. `working_papers.type` holds DESIGN_TEST, FOLLOW_UP,
   * OPERATING_TEST and SAMPLE_TEST — **11 of the 12 rows in prod carry a type this union did
   * not declare**, and `WP_TYPE_LABEL[wp.type][lang]` was indexed directly on it, so the
   * Working papers tab was a TypeError away from the same crash as the Plan and CAPA tabs.
   * It never fired because the broken engagement picker filtered every row away first.
   *
   * 🔑 This is what a gate keyed on the maps you already know about cannot see. The first
   * version of F2-203's gate listed four label maps by name and passed — `WP_TYPE_LABEL` was
   * simply not one of the four. The rule is structural now: no label map may be indexed
   * directly ANYWHERE on this page, whatever it is called.
   */
export type DeclaredWorkingPaperType =
  'TEST' | 'INTERVIEW' | 'OBSERVATION' | 'DOCUMENT_REVIEW' | 'WALKTHROUGH';
export type StoredWorkingPaperType =
  'DESIGN_TEST' | 'OPERATING_TEST' | 'SAMPLE_TEST' | 'FOLLOW_UP';
export type WorkingPaperType = DeclaredWorkingPaperType | StoredWorkingPaperType;
export type ManagedWorkingPaperStatus = 'DRAFT' | 'PEER_REVIEW' | 'APPROVED' | 'REJECTED';
export type WorkingPaperStatus = ManagedWorkingPaperStatus | 'REVIEWED';

export type FindingSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'OBSERVATION';

/** The states `FindingService.ALLOWED` declares edges for. Pinned against the server. */
export type ManagedFindingStatus =
  'OPEN' | 'MGMT_RESPONSE' | 'IN_REMEDIATION' | 'AWAITING_VERIFICATION' | 'CLOSED';

/**
 * States real rows carry that the server's machine declares NO edge out of.
 *
 * F2-203: `audit_lc.findings.status` has no CHECK constraint — `audit_lc` has none at
 * all — so the vocabulary lives only in the heads of its readers, and they disagree.
 * 7 of 20 rows in dev AND prod hold one of these three, meaning those findings cannot
 * be advanced or closed through any route: `ALLOWED.getOrDefault(status, Set.of())` is
 * empty, so every transition answers 409 with an empty allowed-set. They are declared
 * here so the platform can DISPLAY them and say so, rather than crash on a missing
 * label or offer a button that errors.
 *
 * They are NOT mapped onto the managed states. IN_PROGRESS/REMEDIATED/VERIFIED read as
 * synonyms of IN_REMEDIATION/AWAITING_VERIFICATION/CLOSED, and collapsing synonyms
 * rewrites live rows in two databases — an owner decision, not a side effect of a
 * display fix (F2-196's rule, applied to a second register).
 */
export type UnmanagedFindingStatus = 'IN_PROGRESS' | 'REMEDIATED' | 'VERIFIED';

export type FindingStatus = ManagedFindingStatus | UnmanagedFindingStatus;

export type ManagedCapaStatus =
  'OPEN' | 'IN_PROGRESS' | 'AWAITING_VERIFICATION' | 'CLOSED' | 'OVERDUE';
export type CapaStatus = ManagedCapaStatus | 'VERIFIED';

// ── One predicate, four machines ────────────────────────────────────────────
//
// F2-178's shape: the thing that decides what is reachable is written ONCE and every surface
// reads it. Each register keeps its own declared table — the vocabularies genuinely differ —
// but there is a single implementation of "what can this row become?", so a fifth caller
// cannot invent a sixth answer. Before F2-203 the findings machine alone had four copies.

/** The legal next states for a row, or none for a terminal OR undeclared status. */
function nextFrom<S extends string>(
  table: Record<string, readonly S[]>, status: string,
): readonly S[] {
  return table[status] ?? [];
}

function declares(table: Record<string, unknown>, status: string): boolean {
  return Object.prototype.hasOwnProperty.call(table, status);
}

/**
 * A label for any status, including one this build has never heard of.
 *
 * Falling back to the raw token shows the operator what the column holds rather than blanking
 * the row or taking the page down — and with no CHECK on any of the four columns, a ninth
 * value can arrive from a data fix or an integration at any time.
 */
function labelFrom(
  map: Record<string, { en: string; ar: string }>, status: string, lang: 'en' | 'ar',
): string {
  return map[status] ? map[status][lang] : status;
}

/** Terminal = declared with no outgoing edge. Derived, never typed twice (F2-196). */
function terminalOf<S extends string>(table: Record<string, readonly S[]>): readonly string[] {
  return Object.keys(table).filter(s => table[s].length === 0);
}

/**
 * The IIA finding machine — the ONE table this module consults, mirroring
 * `FindingService.ALLOWED` edge for edge. `AuditFindingMachineParityTest` fails the
 * build if the two drift.
 *
 * Before F2-203 the frontend held THREE copies of it: an `onAdvance` lookup map, four
 * hard-coded `if (f.status === …)` buttons, and `transitionFinding` accepting any
 * target at all. That is the F2-189/F2-190/F2-191 shape — a lifecycle with more
 * writers than readers of its own table — and here the extra copies let the UI offer
 * `OPEN → CLOSED`, which the server refuses.
 */
export const FINDING_ALLOWED: Record<ManagedFindingStatus, readonly ManagedFindingStatus[]> = {
  OPEN:                  ['MGMT_RESPONSE'],
  MGMT_RESPONSE:         ['IN_REMEDIATION'],
  IN_REMEDIATION:        ['AWAITING_VERIFICATION'],
  AWAITING_VERIFICATION: ['CLOSED'],
  CLOSED:                [],
};

export const FINDING_MANAGED_STATUSES =
  Object.keys(FINDING_ALLOWED) as ManagedFindingStatus[];

/** Derived from the machine, never typed twice (F2-196). */
export const FINDING_TERMINAL: readonly ManagedFindingStatus[] =
  terminalOf(FINDING_ALLOWED) as ManagedFindingStatus[];

export const FINDING_UNMANAGED_STATUSES: readonly UnmanagedFindingStatus[] =
  ['IN_PROGRESS', 'REMEDIATED', 'VERIFIED'];

/** The legal next states for a finding — empty for terminal AND for unmanaged rows. */
export function nextFindingStatuses(status: string): readonly ManagedFindingStatus[] {
  return nextFrom(FINDING_ALLOWED, status);
}

/** Does the server's machine know this status at all? */
export function isFindingStatusManaged(status: string): boolean {
  return declares(FINDING_ALLOWED, status);
}

/**
 * "Open" is the COMPLEMENT of the derived terminal set, never a second list (F2-196).
 *
 * That deliberately counts the three unmanaged statuses as open. VERIFIED and
 * REMEDIATED read as finished, but nothing in this platform declares them terminal, so
 * saying a finding is closed on that basis would be the platform blessing a synonym it
 * has not adopted. For an audit finding the safe direction is to keep chasing it.
 */
export function isFindingOpen(status: string): boolean {
  return !(FINDING_TERMINAL as readonly string[]).includes(status);
}

export interface AnnualPlan {
  /** e.g. "AP-2026". */
  id: string;
  year: number;
  status: AnnualPlanStatus;
  /** ISO timestamp of last status change. */
  lastUpdated: string;
  acApprovedBy?: string;
  acApprovedAt?: string;
  /** Engagement codes scheduled per quarter (Q1-Q4). */
  q1: string[];
  q2: string[];
  q3: string[];
  q4: string[];
  /** Total budget hours for the year. */
  budgetHours: number;
  /** Notes attached during AC review. */
  acNotesEn?: string;
  acNotesAr?: string;
}

export interface WorkingPaper {
  id: string;
  engagementCode: string;
  type: WorkingPaperType;
  /** Sequential reference within the engagement (e.g. "WP-001"). */
  refNumber: string;
  titleEn: string;
  titleAr: string;
  /** Author (auditor name). */
  author: string;
  /** ISO date created. */
  createdAt: string;
  status: WorkingPaperStatus;
  /** Reviewer (manager / partner). */
  reviewer?: string;
  reviewedAt?: string;
  /** Free-text procedure performed. */
  procedureEn: string;
  procedureAr: string;
  /** Bilingual conclusion. */
  conclusionEn: string;
  conclusionAr: string;
  /** Linked control codes. */
  controlsCovered: string[];
  /** Hours spent on this paper. */
  hours: number;
}

export interface Finding {
  id: string;
  engagementCode: string;
  /** Sequential within engagement. */
  refNumber: string;
  titleEn: string;
  titleAr: string;
  severity: FindingSeverity;
  status: FindingStatus;
  /** Control / framework crosswalk. */
  controlCrosswalk: { framework: string; controlCode: string }[];
  /** Bilingual condition (what was observed). */
  conditionEn: string;
  conditionAr: string;
  /** Bilingual criteria (what should be — policy/regulation). */
  criteriaEn: string;
  criteriaAr: string;
  /** Bilingual root cause. */
  causeEn: string;
  causeAr: string;
  /** Bilingual recommendation. */
  recommendationEn: string;
  recommendationAr: string;
  /** Management response — bilingual + author + accepted/disagreed. */
  mgmtResponseEn?: string;
  mgmtResponseAr?: string;
  mgmtResponseAuthor?: string;
  mgmtAccepts?: boolean;
  /** Target remediation date. */
  targetDate?: string;
  /** Who owns remediation. */
  ownerRole?: string;
  /** ISO timestamp when finding was raised. */
  raisedAt: string;
  /** Linked CAPA task ids. */
  capaIds: string[];
}

export interface CapaTask {
  id: string;
  findingId: string;
  /** Corrective vs Preventive. */
  kind: 'CORRECTIVE' | 'PREVENTIVE';
  titleEn: string;
  titleAr: string;
  ownerRole: string;
  /** Target completion. */
  targetDate: string;
  status: CapaStatus;
  /** ISO when closed. */
  closedAt?: string;
  /** Verification notes by audit. */
  verificationNotesEn?: string;
  verificationNotesAr?: string;
  /** Evidence reference. */
  evidenceRef?: string;
}

// ─── Backend wire types ─────────────────────────────────────────────────────
//
// These mirror the JPA entities serialised through Jackson — they use CSV
// strings for the array-shaped frontend fields (q1_codes, controlsCovered,
// capaIds) and split-out timestamps. We never expose these to the page; the
// from*/to* adapters below are the only translation surface.

interface BackendAnnualPlan {
  id?: number;
  externalId: string;
  organisationId?: number;
  year: number;
  status: AnnualPlanStatus;
  lastUpdated?: string;
  acApprovedBy?: string;
  acApprovedAt?: string;
  q1Codes?: string;
  q2Codes?: string;
  q3Codes?: string;
  q4Codes?: string;
  budgetHours?: number;
  acNotesEn?: string;
  acNotesAr?: string;
}

interface BackendWorkingPaper {
  id?: number;
  externalId: string;
  organisationId?: number;
  engagementCode: string;
  type: WorkingPaperType;
  refNumber: string;
  titleEn: string;
  titleAr: string;
  author: string;
  createdAt?: string;
  status: WorkingPaperStatus;
  reviewer?: string;
  reviewedAt?: string;
  procedureEn?: string;
  procedureAr?: string;
  conclusionEn?: string;
  conclusionAr?: string;
  controlsCovered?: string;       // CSV
  hours?: number | string;        // BigDecimal serialises as either
}

interface BackendCapaTask {
  id?: number;
  externalId: string;
  organisationId?: number;
  findingId: string;
  kind: 'CORRECTIVE' | 'PREVENTIVE';
  titleEn: string;
  titleAr: string;
  ownerRole: string;
  targetDate: string;
  status: CapaStatus;
  closedAt?: string;
  verificationNotesEn?: string;
  verificationNotesAr?: string;
  evidenceRef?: string;
}

interface BackendFinding {
  id: string;                 // FindingDto exposes external_id as `id`
  engagementCode: string;
  refNumber?: string;
  titleEn: string;
  titleAr: string;
  severity: FindingSeverity;
  status: FindingStatus;
  controlCrosswalk?: string;  // JSON
  conditionEn?: string;
  conditionAr?: string;
  criteriaEn?: string;
  criteriaAr?: string;
  causeEn?: string;
  causeAr?: string;
  recommendationEn?: string;
  recommendationAr?: string;
  mgmtResponseEn?: string;
  mgmtResponseAr?: string;
  mgmtResponseAuthor?: string;
  mgmtAccepts?: boolean;
  targetDate?: string;
  ownerRole?: string;
  raisedAt?: string;
  capaIds?: string;           // CSV
}

// ─── Adapters ───────────────────────────────────────────────────────────────

function splitCsv(s: string | undefined): string[] {
  if (!s) return [];
  return s.split(',').map(t => t.trim()).filter(Boolean);
}

function joinCsv(xs: string[] | undefined): string {
  return (xs ?? []).join(',');
}

function parseCrosswalk(json: string | undefined): { framework: string; controlCode: string }[] {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json) as unknown;
    if (Array.isArray(parsed)) return parsed as { framework: string; controlCode: string }[];
    return [];
  } catch { return []; }
}

/**
 * F2-217. Strip the fields a create endpoint mints or stamps for itself.
 *
 * The `toBackend*` mappers below serve both PUT and POST. On PUT that is right — the server's
 * update methods are whitelists that ignore `status` and the review/approval columns. On POST
 * it was not: all three creates persisted the request entity, so an `AC_APPROVED` plan with an
 * `acApprovedBy` of our choosing, an `APPROVED` working paper with a named `reviewer`, or a
 * `CLOSED` CAPA with a backdated `closedAt` all stored exactly as sent — with no transition, no
 * state-machine check and no record of who decided (probed live: create 200, the equivalent
 * guarded transition 409).
 *
 * The server now refuses a non-initial status and clears the stamps. This keeps the client from
 * sending keys it has no business setting, so a rejection is impossible rather than merely
 * handled — and, unlike a silently-discarded key, a field that is never sent cannot be mistaken
 * for one the server honoured (F2-214).
 */
function forCreate<T extends object>(body: T, ...serverOwned: (keyof T)[]): Partial<T> {
  const out: Partial<T> = { ...body };
  for (const k of serverOwned) delete out[k];
  return out;
}

function fromBackendPlan(b: BackendAnnualPlan): AnnualPlan {
  return {
    id: b.externalId,
    year: b.year,
    status: b.status,
    lastUpdated: b.lastUpdated ?? new Date().toISOString(),
    acApprovedBy: b.acApprovedBy,
    acApprovedAt: b.acApprovedAt,
    q1: splitCsv(b.q1Codes),
    q2: splitCsv(b.q2Codes),
    q3: splitCsv(b.q3Codes),
    q4: splitCsv(b.q4Codes),
    budgetHours: b.budgetHours ?? 0,
    acNotesEn: b.acNotesEn,
    acNotesAr: b.acNotesAr,
  };
}

function toBackendPlan(p: AnnualPlan): Partial<BackendAnnualPlan> {
  return {
    externalId: p.id,
    year: p.year,
    status: p.status,
    acApprovedBy: p.acApprovedBy,
    acApprovedAt: p.acApprovedAt,
    q1Codes: joinCsv(p.q1),
    q2Codes: joinCsv(p.q2),
    q3Codes: joinCsv(p.q3),
    q4Codes: joinCsv(p.q4),
    budgetHours: p.budgetHours,
    acNotesEn: p.acNotesEn,
    acNotesAr: p.acNotesAr,
  };
}

function fromBackendWp(b: BackendWorkingPaper): WorkingPaper {
  return {
    id: b.externalId,
    engagementCode: b.engagementCode,
    type: b.type,
    refNumber: b.refNumber,
    titleEn: b.titleEn,
    titleAr: b.titleAr,
    author: b.author,
    createdAt: b.createdAt ?? new Date().toISOString(),
    status: b.status,
    reviewer: b.reviewer,
    reviewedAt: b.reviewedAt,
    procedureEn: b.procedureEn ?? '',
    procedureAr: b.procedureAr ?? '',
    conclusionEn: b.conclusionEn ?? '',
    conclusionAr: b.conclusionAr ?? '',
    controlsCovered: splitCsv(b.controlsCovered),
    hours: typeof b.hours === 'string' ? parseFloat(b.hours) : (b.hours ?? 0),
  };
}

function toBackendWp(w: WorkingPaper): Partial<BackendWorkingPaper> {
  return {
    externalId: w.id,
    engagementCode: w.engagementCode,
    type: w.type,
    refNumber: w.refNumber,
    titleEn: w.titleEn,
    titleAr: w.titleAr,
    author: w.author,
    createdAt: w.createdAt,
    status: w.status,
    reviewer: w.reviewer,
    reviewedAt: w.reviewedAt,
    procedureEn: w.procedureEn,
    procedureAr: w.procedureAr,
    conclusionEn: w.conclusionEn,
    conclusionAr: w.conclusionAr,
    controlsCovered: joinCsv(w.controlsCovered),
    hours: w.hours,
  };
}

function fromBackendCapa(b: BackendCapaTask): CapaTask {
  return {
    id: b.externalId,
    findingId: b.findingId,
    kind: b.kind,
    titleEn: b.titleEn,
    titleAr: b.titleAr,
    ownerRole: b.ownerRole,
    targetDate: b.targetDate,
    status: b.status,
    closedAt: b.closedAt,
    verificationNotesEn: b.verificationNotesEn,
    verificationNotesAr: b.verificationNotesAr,
    evidenceRef: b.evidenceRef,
  };
}

function toBackendCapa(c: CapaTask): Partial<BackendCapaTask> {
  return {
    externalId: c.id,
    findingId: c.findingId,
    kind: c.kind,
    titleEn: c.titleEn,
    titleAr: c.titleAr,
    ownerRole: c.ownerRole,
    targetDate: c.targetDate,
    status: c.status,
    closedAt: c.closedAt,
    verificationNotesEn: c.verificationNotesEn,
    verificationNotesAr: c.verificationNotesAr,
    evidenceRef: c.evidenceRef,
  };
}

function fromBackendFinding(b: BackendFinding): Finding {
  return {
    id: b.id,
    engagementCode: b.engagementCode,
    refNumber: b.refNumber ?? '',
    titleEn: b.titleEn,
    titleAr: b.titleAr,
    severity: b.severity,
    status: b.status,
    controlCrosswalk: parseCrosswalk(b.controlCrosswalk),
    conditionEn: b.conditionEn ?? '',
    conditionAr: b.conditionAr ?? '',
    criteriaEn: b.criteriaEn ?? '',
    criteriaAr: b.criteriaAr ?? '',
    causeEn: b.causeEn ?? '',
    causeAr: b.causeAr ?? '',
    recommendationEn: b.recommendationEn ?? '',
    recommendationAr: b.recommendationAr ?? '',
    mgmtResponseEn: b.mgmtResponseEn,
    mgmtResponseAr: b.mgmtResponseAr,
    mgmtResponseAuthor: b.mgmtResponseAuthor,
    mgmtAccepts: b.mgmtAccepts,
    targetDate: b.targetDate,
    ownerRole: b.ownerRole,
    raisedAt: b.raisedAt ?? new Date().toISOString(),
    capaIds: splitCsv(b.capaIds),
  };
}

// ─── F2-203: the seed corpus is deleted ─────────────────────────────────────
//
// Four `SEED_*` constants used to stand in whenever a register came back empty —
// `if (_findings.length === 0) _findings = SEED_FINDINGS` — plus a second fallback
// inside every `loadX()`. That is the F2-158 shape that made a 500 invisible for the
// whole Role Management API: a client-side fallback renders plausible rows, so nobody
// looks at the endpoint. Here the harm was sharper than a hidden error, because the
// content was governance attribution: `SEED_PLAN` named an invented individual —
// "Khalid Al-Falih (Audit Committee Chair)" — as having APPROVED the annual audit plan
// under CMA Corporate Governance Regulations Article 95, with an approval date minted
// from `Date.now()` at module load so it could never look stale (F2-194).
//
// A tenant whose audit register is genuinely empty now sees that it is empty. An
// unreachable server is a DIFFERENT fact and is reported as one — see `_loadFailed`.

// ─── In-memory cache + hydrate ──────────────────────────────────────────────

let _plan: AnnualPlan | null = null;
let _wps: WorkingPaper[] = [];
let _findings: Finding[] = [];
let _capas: CapaTask[] = [];
/** The REAL audit-engagement catalogue — `compliance.audit_engagements`, 12 rows. */
let _engagements: AuditEngagementDto[] = [];
let _hydratePromise: Promise<void> | null = null;
let _hydrated = false;
let _loadFailed = false;

const EVENT = 'grc.audit.lifecycle.changed';

/**
 * Fetch the four audit-lifecycle registers plus the real engagement catalogue.
 * Safe to call repeatedly — only the first call hits the API unless `force`.
 *
 * <p>F2-203: there is no seed fallback. An empty register renders as empty and an
 * unreachable server renders as a failure — two different facts that the previous
 * `catch → SEED_*` collapsed into one plausible-looking screen.
 */
/**
 * Which annual plan an Audit Committee packet is about.
 *
 * ⚠ This function exists because of a mistake I made and only caught by rendering the
 * document. `plans[0]` — insertion order — was replaced with "most recent year first", whose
 * comment claimed it stopped a 2027 DRAFT shadowing the 2026 APPROVED plan. It does the exact
 * shadowing it claimed to prevent, in the other direction: a Q3-2026 packet was built from
 * next year's DRAFT, budget and quarters included. 🔑 A selection fix can reintroduce the bug
 * it names, and neither version is visible in a passing test — the artefact has to be read.
 *
 * The plan a packet is about is the one for the CURRENT year. Falling back to the most recent
 * is deliberate over falling back to nothing: an organisation whose 2026 plan was never
 * entered still has a real 2025 plan, and the packet says which year it is showing.
 */
function pickCurrentPlan(plans: BackendAnnualPlan[]): BackendAnnualPlan {
  const thisYear = new Date().getFullYear();
  return plans.find(p => p.year === thisYear)
    ?? [...plans].sort((a, b) => (b.year ?? 0) - (a.year ?? 0))[0];
}

export function hydrateAuditLifecycle(force = false): Promise<void> {
  if (_hydratePromise && !force) return _hydratePromise;
  _loadFailed = false;
  _hydratePromise = (async () => {
    const plans    = await api.get<BackendAnnualPlan[]>('/api/audit-lc/annual-plans').catch(logAndEmpty<BackendAnnualPlan>);
    const wps      = await api.get<BackendWorkingPaper[]>('/api/audit-lc/working-papers').catch(logAndEmpty<BackendWorkingPaper>);
    const findings = await api.get<BackendFinding[]>('/api/audit-lc/findings').catch(logAndEmpty<BackendFinding>);
    const capas    = await api.get<BackendCapaTask[]>('/api/audit-lc/capas').catch(logAndEmpty<BackendCapaTask>);
    // The engagement catalogue the AC packet and the KPI strip need. `CompliancePage`
    // has read this endpoint all along; only this module was using a mock of it.
    const engs = await complianceApi.auditEngagements(0, 200)
      .then(p => p.content)
      .catch(logAndEmpty<AuditEngagementDto>);
    _plan = plans.length > 0 ? fromBackendPlan(pickCurrentPlan(plans)) : null;
    _wps         = wps.map(fromBackendWp);
    _findings    = findings.map(fromBackendFinding);
    _capas       = capas.map(fromBackendCapa);
    _engagements = engs;
    _hydrated = true;
    emit();
  })();
  return _hydratePromise;
}

function logAndEmpty<T>(e: unknown): T[] {
  if (!(e instanceof ApiError) || e.status >= 500) {
    console.warn('[auditLifecycle] backend fetch failed:', e);
  }
  // F2-203: an empty register and an unreachable one must not look the same. A 403 is
  // an entitlement answer, not a failure, so it is not flagged.
  if (!(e instanceof ApiError) || e.status !== 403) _loadFailed = true;
  return [] as T[];
}

/** Has hydrate() finished at least once? Exposed for tests / sanity checks. */
export function isAuditLifecycleHydrated(): boolean { return _hydrated; }

/**
 * Did any register fail to load? The page must say so instead of rendering an empty
 * list, which is the defect `RiskControlFindingPivotPage` still carries.
 */
export function auditLifecycleLoadFailed(): boolean { return _loadFailed; }

// ─── Engagements (the real catalogue, and the codes the registers actually use) ──

/** The real audit-engagement catalogue. Empty until hydrate resolves. */
export function loadEngagements(): AuditEngagementDto[] { return _engagements; }

/** A terminal engagement status — the complement is "in flight" (F2-196, not a list). */
const ENGAGEMENT_TERMINAL: readonly string[] = ['CLOSED'];

export function isEngagementInFlight(status: string | undefined): boolean {
  return !!status && !ENGAGEMENT_TERMINAL.includes(status);
}

/**
 * The engagement codes the findings and working-paper registers actually reference.
 *
 * F2-203: this — not a catalogue — is what the picker must offer. `engagement_code` is
 * free text on both tables with no foreign key, and the five code spaces in play
 * (findings `ENG-IA-00x`/`IA-2026-001`, working papers `IA-2026-001`, the real
 * catalogue `AUD-2026-00x`, `audit_lc.ia_engagement` `ENG-2026-00x`, and the annual
 * plan's `ENG-CYBER-2026Q1`…) do not intersect. A picker built from the groups its own
 * lists contain cannot show an empty tab; a picker built from a catalogue can, and did,
 * for all eight of its options.
 */
export function registerEngagementCodes(): string[] {
  const codes = new Set<string>();
  _findings.forEach(f => { if (f.engagementCode) codes.add(f.engagementCode); });
  _wps.forEach(w => { if (w.engagementCode) codes.add(w.engagementCode); });
  return [...codes].sort();
}

// ─── Annual plan ────────────────────────────────────────────────────────────

/**
 * The most recent annual audit plan, or null when the register holds none.
 *
 * F2-203: this used to return `SEED_PLAN` — an invented Audit Committee chair and an
 * approval date — rather than admit the register was empty.
 */
export function loadAnnualPlan(): AnnualPlan | null {
  return _plan;
}

export function saveAnnualPlan(plan: AnnualPlan): AnnualPlan {
  const next = { ...plan, lastUpdated: new Date().toISOString() };
  _plan = next;
  emit();
  // Fire-and-forget — try update first; if the row doesn't exist on the
  // backend (404), fall back to create.
  void api.put<BackendAnnualPlan>(`/api/audit-lc/annual-plans/${encodeURIComponent(next.id)}`, toBackendPlan(next))
    .catch(async (err) => {
      if (err instanceof ApiError && err.status === 404) {
        // F2-217: created as DRAFT, not as whatever this browser last saw. If the server has no
        // such plan, this client holds no evidence of an Audit Committee approval — only a
        // memory of one — and re-asserting it here is how an approval acquires no approver.
        try { await api.post<BackendAnnualPlan>('/api/audit-lc/annual-plans',
                forCreate(toBackendPlan(next), 'status', 'acApprovedBy', 'acApprovedAt')); }
        catch (e) { console.warn('[auditLifecycle] save plan create-fallback failed:', e); }
      } else {
        console.warn('[auditLifecycle] save plan failed:', err);
      }
    });
  return next;
}

/**
 * Move the annual plan through its Audit-Committee lifecycle, taking the row back
 * from the server.
 *
 * F2-203: the `by` parameter is gone. The caller used to pass
 * `'Khalid Al-Falih (AC Chair)'` — an invented individual — and this function wrote it
 * into the local cache as `acApprovedBy` while the server stamped the AUTHENTICATED
 * principal (`AuditLifecycleController` takes the actor from `Authentication` and
 * ignores the body, per SEC-IAM-002). Nothing read the response, so the screen credited
 * an Audit Committee approval to a person who does not exist while the audit trail
 * recorded someone else. Replacing the row from the response fixes both halves and
 * removes any need for the client to name an approver at all.
 */
export async function transitionPlan(status: AnnualPlanStatus): Promise<AnnualPlan> {
  const plan = loadAnnualPlan();
  if (!plan) throw new Error('no annual plan loaded');
  const saved = fromBackendPlan(await api.post<BackendAnnualPlan>(
    `/api/audit-lc/annual-plans/${encodeURIComponent(plan.id)}/transition`,
    { to: status }));
  _plan = saved;
  emit();
  return saved;
}

// ─── Working papers ─────────────────────────────────────────────────────────

export function loadWorkingPapers(): WorkingPaper[] {
  return _wps;
}

export function workingPapersForEngagement(code: string): WorkingPaper[] {
  return loadWorkingPapers().filter(w => w.engagementCode === code);
}

export function saveWorkingPaper(wp: WorkingPaper): WorkingPaper {
  const exists = _wps.some(w => w.id === wp.id);
  _wps = [..._wps.filter(w => w.id !== wp.id), wp];
  emit();
  if (exists) {
    void api.put<BackendWorkingPaper>(`/api/audit-lc/working-papers/${encodeURIComponent(wp.id)}`, toBackendWp(wp))
      .catch(e => console.warn('[auditLifecycle] save wp failed:', e));
  } else {
    void api.post<BackendWorkingPaper>('/api/audit-lc/working-papers',
        forCreate(toBackendWp(wp), 'status', 'reviewer', 'reviewedAt'))
      .catch(e => console.warn('[auditLifecycle] create wp failed:', e));
  }
  return wp;
}

/**
 * F2-203: `reviewer` is gone for the same reason `by` left {@link transitionPlan} — the
 * caller passed the invented `'Sara Al-Zahrani (Audit Manager)'` while
 * `AuditLifecycleService.transitionWorkingPaper` stamps `actor(auth)`. Take the row back.
 */
export async function transitionWorkingPaper(id: string, status: WorkingPaperStatus): Promise<WorkingPaper> {
  const saved = fromBackendWp(await api.post<BackendWorkingPaper>(
    `/api/audit-lc/working-papers/${encodeURIComponent(id)}/transition`,
    { to: status }));
  _wps = _wps.map(w => w.id === id ? saved : w);
  emit();
  return saved;
}

/**
 * F2-203: no `author` parameter. The page passed `'Faisal Al-Otaibi (Auditor)'` and,
 * unlike the two transitions above, `createWorkingPaper` on the server saves the body
 * verbatim — so that invented auditor was PERSISTED as the author of an IIA Standard
 * 2330 working paper. The server now stamps the authenticated principal.
 *
 * `externalId` is still minted here because `AuditLifecycleService.createWorkingPaper`
 * throws without one — that contract is the same on annual plans and CAPAs, and
 * changing all three is filed rather than done in a findings slice.
 */
export async function createWorkingPaper(
  engagementCode: string, type: WorkingPaperType,
  titleEn: string, titleAr: string, hours: number,
): Promise<WorkingPaper> {
  const existing = _wps.filter(w => w.engagementCode === engagementCode);
  const refNumber = `WP-${String(existing.length + 1).padStart(3, '0')}`;
  const id = `WP-${new Date().getFullYear()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`;
  const saved = fromBackendWp(await api.post<BackendWorkingPaper>('/api/audit-lc/working-papers', {
    externalId: id, engagementCode, type, refNumber,
    titleEn, titleAr, status: 'DRAFT',
    procedureEn: '', procedureAr: '', conclusionEn: '', conclusionAr: '',
    controlsCovered: '', hours,
  }));
  _wps = [..._wps.filter(w => w.id !== saved.id), saved];
  emit();
  return saved;
}

// ─── Findings ───────────────────────────────────────────────────────────────

export function loadFindings(): Finding[] {
  return _findings;
}

export function findingsForEngagement(code: string): Finding[] {
  return _findings.filter(f => f.engagementCode === code);
}

/**
 * Raise a finding. The SERVER mints the reference and the sequence number.
 *
 * F2-203: this used to mint `FIN-<year>-<4 base-36 chars>` as the external id and
 * `FIN-%03d` as the ref number, then hand both to a function that wrote neither
 * anywhere. `FindingService.create` mints both when the request omits them — and it
 * used to HONOUR them when supplied, which is F2-172(d)'s defect ("only the server may
 * issue a tracking code"); that half is closed in the same commit. The client-side
 * sequence was wrong regardless: it counted the local cache, which included a seeded
 * finding, so the first real finding on an engagement would have been FIN-002.
 *
 * `ownerRole` is a required argument with no default on purpose — the page used to pass
 * the literal string `'Owner role'`.
 */
export async function createFinding(
  engagementCode: string, titleEn: string, titleAr: string,
  severity: FindingSeverity, conditionEn: string, conditionAr: string, ownerRole: string,
): Promise<Finding> {
  const saved = fromBackendFinding(await api.post<BackendFinding>('/api/audit-lc/findings', {
    engagementCode, titleEn, titleAr, severity,
    conditionEn, conditionAr, ownerRole,
  }));
  _findings = [saved, ..._findings.filter(x => x.id !== saved.id)];
  emit();
  return saved;
}

/**
 * Advance a finding through the IIA lifecycle. The row comes back FROM THE SERVER.
 *
 * F2-203: the whole body used to be three lines of local array assignment. Nothing was
 * written, so an auditor who closed a finding saw it close and a reload undid it. The
 * caller must pick `status` from {@link nextFindingStatuses} — this function does not
 * validate, because the server's machine is the authority and answers 409; what it must
 * not do is compute the new status itself, which is why the cache is replaced from the
 * response rather than patched with the requested value.
 *
 * `mgmtResponse*` ride on the transition because that is where the server accepts them
 * — there is no PUT on findings at all (filed).
 *
 * ── F2-441 ───────────────────────────────────────────────────────────────────
 * `accepts` is the IIA Standard 2500 VERDICT and it was missing from both ends: the
 * server's `TransitionRequest` declared the response text and author and not the
 * verdict, and this signature took two of the four. So a management response could be
 * recorded and whether management ACCEPTED the finding could not — while the page
 * rendered `mgmtAccepts` as a two-state badge, so null showed as "Disagreed".
 *
 * ⚠ It is `boolean | undefined`, and the distinction is the whole point: `undefined`
 * means "no answer given, leave whatever is stored" (the server null-guards it), and
 * `false` is a RECORDED disagreement. Passing `!!x` here would erase the third state
 * again one layer up.
 *
 * ⚠ Both call sites still pass NO mgmt argument, because there is no form to enter a
 * management response anywhere in the UI — so advancing a finding through
 * MGMT_RESPONSE records nothing. That is a product gap, filed rather than invented
 * here: who may respond, whether the author is the authenticated principal (F2-236 —
 * the server currently takes it from the BODY, which is the wrong direction), and
 * whether a verdict is mandatory at that transition are all decisions.
 */
export async function transitionFinding(
  id: string, status: ManagedFindingStatus,
  mgmt?: { responseEn?: string; responseAr?: string; accepts?: boolean },
): Promise<Finding> {
  const saved = fromBackendFinding(await api.post<BackendFinding>(
    `/api/audit-lc/findings/${encodeURIComponent(id)}/transition`,
    { status, mgmtResponseEn: mgmt?.responseEn, mgmtResponseAr: mgmt?.responseAr,
      mgmtAccepts: mgmt?.accepts }));
  _findings = _findings.map(x => x.id === id ? saved : x);
  emit();
  return saved;
}

// ─── CAPAs ──────────────────────────────────────────────────────────────────

export function loadCapas(): CapaTask[] {
  return _capas;
}

/**
 * The CAPAs raised against a finding.
 *
 * F2-203: takes the FINDING, not an id, because `audit_lc.capa_tasks.finding_id` has two
 * writers that disagree about what it holds. The seeded rows carry the finding's
 * `ref_number` (`F-2026-001`); this module's own `createCapa` sends the `external_id`
 * (a GUID). It is free text with no foreign key, so `capasForFinding(f.id)` matched
 * nothing and all 12 CAPAs in dev and prod were invisible on the CAPA tab.
 *
 * Matching either key is a tolerant read, deliberately: picking one and rewriting the
 * other's rows in two databases is an owner decision (F2-183). Filed.
 */
export function capasForFinding(finding: Pick<Finding, 'id' | 'refNumber'>): CapaTask[] {
  return _capas.filter(c => c.findingId === finding.id || c.findingId === finding.refNumber);
}

export function saveCapa(c: CapaTask): CapaTask {
  const exists = _capas.some(x => x.id === c.id);
  _capas = [..._capas.filter(x => x.id !== c.id), c];
  emit();
  if (exists) {
    void api.put<BackendCapaTask>(`/api/audit-lc/capas/${encodeURIComponent(c.id)}`, toBackendCapa(c))
      .catch(e => console.warn('[auditLifecycle] save capa failed:', e));
  } else {
    void api.post<BackendCapaTask>('/api/audit-lc/capas',
        forCreate(toBackendCapa(c), 'status', 'closedAt'))
      .catch(e => console.warn('[auditLifecycle] create capa failed:', e));
  }
  return c;
}

export function createCapa(findingId: string, kind: 'CORRECTIVE' | 'PREVENTIVE', titleEn: string, titleAr: string, ownerRole: string): CapaTask {
  const id = `CAPA-${new Date().getFullYear()}-${String(_capas.length + 1).padStart(3, '0')}`;
  const c: CapaTask = {
    id, findingId, kind,
    titleEn, titleAr, ownerRole,
    targetDate: new Date(Date.now() + 30 * 86_400_000).toISOString().slice(0, 10),
    status: 'OPEN',
  };
  saveCapa(c);
  // F2-203: the back-link used to be written here, as `saveFinding({...f, capaIds: [...]})`
  // — into a function that wrote nothing. There is no endpoint that updates a finding, so
  // the finding's `capa_ids` column is the server's own denormalised copy and a client
  // cannot maintain it. The authoritative link is `capa_tasks.finding_id`, which this row
  // carries; {@link capasForFinding} reads that. Filed: nothing on the server populates
  // `findings.capa_ids` either — it is NULL on all 20 rows in both databases.
  return c;
}

export function transitionCapa(id: string, status: CapaStatus, evidenceRef?: string, notesEn?: string, notesAr?: string): CapaTask | null {
  const c = loadCapas().find(x => x.id === id);
  if (!c) return null;
  const updated: CapaTask = {
    ...c, status,
    closedAt: status === 'CLOSED' ? new Date().toISOString() : c.closedAt,
    evidenceRef: evidenceRef ?? c.evidenceRef,
    verificationNotesEn: notesEn ?? c.verificationNotesEn,
    verificationNotesAr: notesAr ?? c.verificationNotesAr,
  };
  _capas = _capas.map(x => x.id === id ? updated : x);
  emit();
  void api.post<BackendCapaTask>(
    `/api/audit-lc/capas/${encodeURIComponent(id)}/transition`,
    { to: status })
    .catch(e => console.warn('[auditLifecycle] transition capa failed:', e));
  // Persist verification notes / evidence separately (transition endpoint
  // doesn't accept them on the backend) — fall back to a PUT.
  if (notesEn || notesAr || evidenceRef) {
    void api.put<BackendCapaTask>(`/api/audit-lc/capas/${encodeURIComponent(id)}`, toBackendCapa(updated))
      .catch(() => { /* already logged */ });
  }
  return updated;
}

// ─── Subscriptions ──────────────────────────────────────────────────────────

export function subscribeAuditLifecycle(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  // Kick off the hydrate if no subscriber has yet — first subscriber
  // is typically AuditLifecyclePage on mount, so this gives us a real-data
  // first render without extra wiring at the call site.
  if (!_hydratePromise) void hydrateAuditLifecycle();
  return () => {
    window.removeEventListener(EVENT, handler);
  };
}

function emit() {
  try { window.dispatchEvent(new CustomEvent(EVENT)); } catch { /* ignore */ }
}

// ─── AC packet generator ───────────────────────────────────────────────────
//
// F2-203: this is an EXPORT — .txt download, print-to-PDF classified "CONFIDENTIAL —
// AUDIT COMMITTEE", and a branded XLSX/DOCX through the server's reporting subsystem.
// Section 2 and the whole "Engagements" sheet used to be built from `MOCK_ENGAGEMENTS`,
// so the document an Audit Committee receives under CMA Article 95 listed eight
// invented engagements — each joined against the real registers and therefore each
// reporting "WPs: 0 · Findings: 0" while section 3 counted 20 real findings. The packet
// contradicted itself on the same page.
//
// 🔑 F2-199's `exportProvenance` gate passed it, because the gate asks whether server
// data is in scope for the emitting file and this file DOES call `api.get`. A one-hop
// scope rule cannot tell WHICH data reaches the document, so a file that mixes real and
// fabricated sources reads as fully backed. That is the ninth instrument in this
// lineage and the ninth distinct reason one of them was blind.

/** A row of section 2 — measured from the registers, not from a catalogue. */
function engagementProgressRows(): { code: string; title: string; wps: number; findings: number }[] {
  return registerEngagementCodes().map(code => {
    const known = _engagements.find(e => e.engagementCode === code);
    return {
      code,
      // No title rather than an invented one when the code resolves in no catalogue —
      // which today is all of them, because the five code spaces do not intersect.
      title: known?.titleEn ?? '',
      wps: _wps.filter(w => w.engagementCode === code).length,
      findings: _findings.filter(f => f.engagementCode === code).length,
    };
  });
}

/**
 * Severity tally that cannot produce NaN. `audit_lc.findings.severity` has no CHECK
 * either — only `FindingDto`'s `@Pattern` narrows the API path, and the seed rows were
 * inserted around it — so `sevCounts[f.severity]++` on an undeclared token would have
 * put NaN into a document going to an Audit Committee.
 */
function tallySeverities(findings: Finding[]): Record<string, number> {
  const out: Record<string, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, OBSERVATION: 0 };
  findings.forEach(f => { out[f.severity] = (out[f.severity] ?? 0) + 1; });
  return out;
}

/** Overdue = past its target date and not in the DERIVED terminal set (F2-196). */
function countOverdueFindings(findings: Finding[]): number {
  const today = new Date();
  return findings.filter(f => f.targetDate && new Date(f.targetDate) < today && isFindingOpen(f.status)).length;
}

export function buildAcPacket(orgEn: string, orgAr: string, quarter: string): string {
  const plan = loadAnnualPlan();
  const findings = loadFindings();
  const capas = loadCapas();

  const lines: string[] = [];
  const div = '─'.repeat(64);
  const dt = (iso: string) => new Date(iso).toLocaleString('en-GB', { timeZone: 'Asia/Riyadh' });

  lines.push(div);
  lines.push('AUDIT COMMITTEE EXECUTIVE PACKET');
  lines.push('حزمة لجنة المراجعة التنفيذية');
  lines.push(div);
  lines.push(`Reporting entity: ${orgEn}`);
  lines.push(`الجهة          : ${orgAr}`);
  lines.push(`Quarter        : ${quarter}`);
  lines.push(`Generated      : ${new Date().toISOString()}`);
  lines.push('');

  lines.push('1. ANNUAL AUDIT PLAN STATUS · حالة خطة التدقيق السنوية');
  if (!plan) {
    // F2-197: an absence must read as unmeasured, never as an all-clear. The previous
    // code could not reach this branch — it substituted an invented approved plan.
    lines.push('   No annual audit plan is recorded for this organisation.');
    lines.push('   لا توجد خطة تدقيق سنوية مُسجَّلة لهذه الجهة.');
  } else {
    lines.push(`   Plan ID  : ${plan.id} (${plan.year})`);
    lines.push(`   Status   : ${plan.status}`);
    lines.push(`   Approved : ${plan.acApprovedBy ?? '—'} on ${plan.acApprovedAt ? dt(plan.acApprovedAt) : '—'}`);
    lines.push(`   Budget   : ${plan.budgetHours.toLocaleString()} hours`);
    lines.push(`   Q1 (${plan.q1.length}): ${plan.q1.join(', ')}`);
    lines.push(`   Q2 (${plan.q2.length}): ${plan.q2.join(', ')}`);
    lines.push(`   Q3 (${plan.q3.length}): ${plan.q3.join(', ')}`);
    lines.push(`   Q4 (${plan.q4.length}): ${plan.q4.join(', ')}`);
  }
  lines.push('');

  lines.push('2. ENGAGEMENT PROGRESS · تقدّم الارتباطات');
  const progress = engagementProgressRows();
  if (progress.length === 0) {
    lines.push('   No engagement is referenced by any working paper or finding.');
    lines.push('   لا يوجد ارتباط مُشار إليه في أي ورقة عمل أو ملاحظة.');
  } else {
    progress.forEach(r => {
      lines.push(`   [${r.code}]${r.title ? ` ${r.title}` : ''}`);
      lines.push(`     WPs: ${r.wps} · Findings: ${r.findings}`);
    });
  }
  lines.push(`   Engagements in the audit-engagement catalogue: ${_engagements.length}`);
  lines.push('');

  lines.push('3. FINDINGS BY SEVERITY · الملاحظات بحسب الخطورة');
  const sevCounts = tallySeverities(findings);
  Object.entries(sevCounts).forEach(([sev, n]) => lines.push(`   ${sev.padEnd(12)}: ${n}`));
  const overdue = countOverdueFindings(findings);
  lines.push(`   Overdue     : ${overdue}`);
  lines.push('');

  lines.push('4. CAPA CLOSURE RATE · معدّل إغلاق إجراءات CAPA');
  const totalCapas = capas.length;
  const closedCapas = capas.filter(c => c.status === 'CLOSED').length;
  const overdueCapas = capas.filter(c => new Date(c.targetDate) < new Date() && c.status !== 'CLOSED').length;
  const closurePct = totalCapas > 0 ? Math.round((closedCapas / totalCapas) * 100) : 0;
  lines.push(`   Total tasks : ${totalCapas}`);
  lines.push(`   Closed      : ${closedCapas} (${closurePct}%)`);
  lines.push(`   In progress : ${capas.filter(c => c.status === 'IN_PROGRESS').length}`);
  lines.push(`   Overdue     : ${overdueCapas}`);
  lines.push('');

  lines.push('5. KEY FINDINGS DETAIL · تفاصيل أبرز الملاحظات');
  findings
    .filter(f => f.severity === 'CRITICAL' || f.severity === 'HIGH')
    .forEach(f => {
      lines.push(`   [${f.refNumber}] ${f.severity} · ${f.status}`
        + (isFindingStatusManaged(f.status) ? '' : ' (no transition declared for this status)'));
      lines.push(`     EN: ${f.titleEn}`);
      lines.push(`     AR: ${f.titleAr}`);
      lines.push(`     Owner: ${f.ownerRole ?? '—'}`);
      lines.push(`     Target: ${f.targetDate ?? '—'}`);
      lines.push(`     Frameworks: ${f.controlCrosswalk.map(c => `${c.framework}/${c.controlCode}`).join(', ')}`);
    });
  lines.push('');

  lines.push(div);
  lines.push('Generated by TSS GRC Platform');
  lines.push(div);
  return lines.join('\n');
}

/** Export P5 — Audit-Committee packet as tabular sheets for a branded .xlsx/.docx.
 *  "Severity"/"Status" headers drive the workbook's conditional formatting. */
export function buildAcSheets(): { name: string; headers: string[]; rows: string[][] }[] {
  const plan = loadAnnualPlan();
  const findings = loadFindings();
  const capas = loadCapas();
  const sev = tallySeverities(findings);
  const overdue = countOverdueFindings(findings);
  const totalCapas = capas.length;
  const closedCapas = capas.filter(c => c.status === 'CLOSED').length;
  const overdueCapas = capas.filter(c => new Date(c.targetDate) < new Date() && c.status !== 'CLOSED').length;
  return [
    {
      name: 'Annual Plan',
      headers: ['Field', 'Value'],
      rows: plan ? [
        ['Plan ID', `${plan.id} (${plan.year})`],
        ['Status', plan.status],
        ['Approved by', plan.acApprovedBy ?? '—'],
        ['Budget (hours)', String(plan.budgetHours)],
        ['Q1 / Q2 / Q3 / Q4', `${plan.q1.length} / ${plan.q2.length} / ${plan.q3.length} / ${plan.q4.length}`],
      ] : [['Plan', 'No annual audit plan is recorded for this organisation']],
    },
    {
      // F2-203: the engagements this register actually covers. The row used to be one
      // per invented engagement, each reporting 0 working papers and 0 findings.
      name: 'Engagements',
      headers: ['Code', 'Title', 'Working Papers', 'Findings'],
      rows: engagementProgressRows().map(r => [r.code, r.title, String(r.wps), String(r.findings)]),
    },
    {
      name: 'Findings by Severity',
      headers: ['Severity', 'Count'],
      rows: [['CRITICAL', String(sev.CRITICAL)], ['HIGH', String(sev.HIGH)], ['MEDIUM', String(sev.MEDIUM)],
        ['LOW', String(sev.LOW)], ['OBSERVATION', String(sev.OBSERVATION)], ['Overdue', String(overdue)]],
    },
    {
      name: 'CAPA Closure',
      headers: ['Metric', 'Value'],
      rows: [
        ['Total tasks', String(totalCapas)],
        ['Closed', `${closedCapas} (${totalCapas ? Math.round((closedCapas / totalCapas) * 100) : 0}%)`],
        ['In progress', String(capas.filter(c => c.status === 'IN_PROGRESS').length)],
        ['Overdue', String(overdueCapas)],
      ],
    },
    {
      name: 'Key Findings',
      headers: ['Ref', 'Severity', 'Status', 'Title', 'Owner', 'Target', 'Frameworks'],
      rows: findings.filter(f => f.severity === 'CRITICAL' || f.severity === 'HIGH').map(f =>
        [f.refNumber, f.severity,
          f.status + (isFindingStatusManaged(f.status) ? '' : ' (unmanaged)'),
          f.titleEn, f.ownerRole ?? '—', f.targetDate ?? '—',
          f.controlCrosswalk.map(c => `${c.framework}/${c.controlCode}`).join(', ')]),
    },
  ];
}

// ─── Display helpers ───────────────────────────────────────────────────────

export const PLAN_STATUS_LABEL: Record<AnnualPlanStatus, { en: string; ar: string }> = {
  DRAFT:        { en: 'Draft',          ar: 'مسوّدة' },
  AC_REVIEW:    { en: 'AC review',      ar: 'مراجعة لجنة المراجعة' },
  AC_APPROVED:  { en: 'AC approved',    ar: 'مُعتمَدة من لجنة المراجعة' },
  EXECUTING:    { en: 'Executing',      ar: 'قيد التنفيذ' },
  CLOSED:       { en: 'Closed',         ar: 'مُغلَقة' },
  // F2-203: the value the 2026 plan actually holds. Indexing this map directly on it is a
  // TypeError that took the whole Plan tab down — and only `plans[0]` happening to select the
  // OTHER row kept it from firing. This slice's own plan-selection fix is what exposed it.
  APPROVED:     { en: 'Approved',       ar: 'مُعتمَدة' },
};

export const PLAN_STATUS_TONE: Record<AnnualPlanStatus, 'neutral' | 'info' | 'success' | 'primary'> = {
  DRAFT: 'neutral', AC_REVIEW: 'info', AC_APPROVED: 'success', EXECUTING: 'primary', CLOSED: 'neutral',
  APPROVED: 'success',
};

/** Mirrors `AuditLifecycleService.AP_TRANSITIONS`. Pinned by AuditFindingMachineParityTest. */
export const PLAN_ALLOWED: Record<ManagedAnnualPlanStatus, readonly ManagedAnnualPlanStatus[]> = {
  DRAFT:       ['AC_REVIEW'],
  AC_REVIEW:   ['AC_APPROVED', 'DRAFT'],
  AC_APPROVED: ['EXECUTING'],
  EXECUTING:   ['CLOSED'],
  CLOSED:      [],
};

export const PLAN_UNMANAGED_STATUSES: readonly string[] = ['APPROVED'];

export function nextPlanStatuses(status: string): readonly ManagedAnnualPlanStatus[] {
  return nextFrom(PLAN_ALLOWED, status);
}
export function isPlanStatusManaged(status: string): boolean {
  return declares(PLAN_ALLOWED, status);
}
export function planStatusLabel(status: string, lang: 'en' | 'ar'): string {
  return labelFrom(PLAN_STATUS_LABEL, status, lang);
}

export const WP_TYPE_LABEL: Record<WorkingPaperType, { en: string; ar: string }> = {
  TEST:            { en: 'Test',             ar: 'اختبار' },
  INTERVIEW:       { en: 'Interview',        ar: 'مقابلة' },
  OBSERVATION:     { en: 'Observation',      ar: 'ملاحظة' },
  DOCUMENT_REVIEW: { en: 'Document review',  ar: 'مراجعة مستند' },
  WALKTHROUGH:     { en: 'Walkthrough',      ar: 'تجوال' },
  // F2-203: the four types the register actually holds, 11 of 12 rows in prod. Unlike the
  // status vocabularies these carry no lifecycle, so labelling them is the whole fix —
  // there is no transition to disclose as unavailable.
  DESIGN_TEST:     { en: 'Design test',      ar: 'اختبار تصميم' },
  OPERATING_TEST:  { en: 'Operating test',   ar: 'اختبار تشغيل' },
  SAMPLE_TEST:     { en: 'Sample test',      ar: 'اختبار عيّنة' },
  FOLLOW_UP:       { en: 'Follow-up',        ar: 'متابعة' },
};

export function workingPaperTypeLabel(type: string, lang: 'en' | 'ar'): string {
  return labelFrom(WP_TYPE_LABEL, type, lang);
}

export const WP_STATUS_TONE: Record<WorkingPaperStatus, 'neutral' | 'warning' | 'success' | 'danger'> = {
  DRAFT: 'neutral', PEER_REVIEW: 'warning', APPROVED: 'success', REJECTED: 'danger',
  // F2-203: 8 of the 12 working papers hold this. An undeclared key here gave
  // `tone={undefined}` rather than a crash, so this instance was quieter than the plan's —
  // and just as stuck: WP_TRANSITIONS has no edge out of REVIEWED.
  REVIEWED: 'success',
};

export const WP_STATUS_LABEL: Record<WorkingPaperStatus, { en: string; ar: string }> = {
  DRAFT:       { en: 'Draft',       ar: 'مسوّدة' },
  PEER_REVIEW: { en: 'Peer review', ar: 'مراجعة الأقران' },
  APPROVED:    { en: 'Approved',    ar: 'مُعتمَدة' },
  REJECTED:    { en: 'Rejected',    ar: 'مرفوضة' },
  REVIEWED:    { en: 'Reviewed',    ar: 'تمّت المراجعة' },
};

/** Mirrors `AuditLifecycleService.WP_TRANSITIONS`. */
export const WP_ALLOWED: Record<ManagedWorkingPaperStatus, readonly ManagedWorkingPaperStatus[]> = {
  DRAFT:       ['PEER_REVIEW'],
  PEER_REVIEW: ['APPROVED', 'REJECTED'],
  REJECTED:    ['DRAFT'],          // the re-work loop
  APPROVED:    [],
};

export const WP_UNMANAGED_STATUSES: readonly string[] = ['REVIEWED'];

export function nextWorkingPaperStatuses(status: string): readonly ManagedWorkingPaperStatus[] {
  return nextFrom(WP_ALLOWED, status);
}
export function isWorkingPaperStatusManaged(status: string): boolean {
  return declares(WP_ALLOWED, status);
}
export function workingPaperStatusLabel(status: string, lang: 'en' | 'ar'): string {
  return labelFrom(WP_STATUS_LABEL, status, lang);
}

export const SEVERITY_TONE: Record<FindingSeverity, 'risk-crit' | 'risk-high' | 'risk-med' | 'risk-low' | 'neutral'> = {
  CRITICAL: 'risk-crit', HIGH: 'risk-high', MEDIUM: 'risk-med', LOW: 'risk-low', OBSERVATION: 'neutral',
};

export const FINDING_STATUS_LABEL: Record<FindingStatus, { en: string; ar: string }> = {
  OPEN:                  { en: 'Open',                  ar: 'مفتوحة' },
  MGMT_RESPONSE:         { en: 'Mgmt response',         ar: 'ردّ الإدارة' },
  IN_REMEDIATION:        { en: 'In remediation',        ar: 'قيد المعالجة' },
  AWAITING_VERIFICATION: { en: 'Awaiting verification', ar: 'بانتظار التحقق' },
  CLOSED:                { en: 'Closed',                ar: 'مُغلَقة' },
  // F2-203: the three statuses real rows carry that the server's machine has no edge
  // out of. Labelled so the register can be displayed; see UnmanagedFindingStatus for
  // why they are not folded into the five above.
  IN_PROGRESS:           { en: 'In progress',           ar: 'قيد التنفيذ' },
  REMEDIATED:            { en: 'Remediated',            ar: 'تمّت المعالجة' },
  VERIFIED:              { en: 'Verified',              ar: 'تمّ التحقق' },
};

/**
 * A label for any status string, including one this build has never heard of.
 *
 * F2-203: `FINDING_STATUS_LABEL[f.status][lang]` was indexed directly, which is a
 * TypeError on an undeclared token — and `audit_lc.findings.status` has NO CHECK
 * constraint, so a ninth value can arrive from a data fix or an integration at any
 * time. Falling back to the raw token shows the operator what the column holds rather
 * than blanking the row or taking the page down.
 */
export function findingStatusLabel(status: string, lang: 'en' | 'ar'): string {
  return labelFrom(FINDING_STATUS_LABEL, status, lang);
}

export const CAPA_STATUS_TONE: Record<CapaStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  OPEN: 'neutral', IN_PROGRESS: 'info', AWAITING_VERIFICATION: 'warning', CLOSED: 'success', OVERDUE: 'danger',
  VERIFIED: 'success',
};

export const CAPA_STATUS_LABEL: Record<CapaStatus, { en: string; ar: string }> = {
  OPEN:                  { en: 'Open',                  ar: 'مفتوحة' },
  IN_PROGRESS:           { en: 'In progress',           ar: 'قيد التنفيذ' },
  AWAITING_VERIFICATION: { en: 'Awaiting verification', ar: 'بانتظار التحقق' },
  CLOSED:                { en: 'Closed',                ar: 'مُغلَقة' },
  OVERDUE:               { en: 'Overdue',               ar: 'متأخّرة' },
  // F2-203: 3 of the 12 CAPAs hold this, and the CAPA tab indexed this map directly — the
  // same TypeError as the Plan tab, dodged only when the row was ALSO past its target date
  // and the "overdue" branch won the ternary.
  VERIFIED:              { en: 'Verified',              ar: 'تمّ التحقق' },
};

/** Mirrors `AuditLifecycleService.CAPA_TRANSITIONS`. */
export const CAPA_ALLOWED: Record<ManagedCapaStatus, readonly ManagedCapaStatus[]> = {
  OPEN:                  ['IN_PROGRESS', 'AWAITING_VERIFICATION'],
  IN_PROGRESS:           ['AWAITING_VERIFICATION', 'OPEN'],
  AWAITING_VERIFICATION: ['CLOSED', 'IN_PROGRESS'],
  OVERDUE:               ['IN_PROGRESS', 'AWAITING_VERIFICATION'],
  CLOSED:                [],
};

export const CAPA_UNMANAGED_STATUSES: readonly string[] = ['VERIFIED'];

export function nextCapaStatuses(status: string): readonly ManagedCapaStatus[] {
  return nextFrom(CAPA_ALLOWED, status);
}
export function isCapaStatusManaged(status: string): boolean {
  return declares(CAPA_ALLOWED, status);
}
export function capaStatusLabel(status: string, lang: 'en' | 'ar'): string {
  return labelFrom(CAPA_STATUS_LABEL, status, lang);
}

// ─── Aggregation ────────────────────────────────────────────────────────────

export interface AuditLifecycleStats {
  totalEngagements: number;
  engagementsInFlight: number;
  workingPapersTotal: number;
  workingPapersInReview: number;
  findingsOpen: number;
  findingsCritical: number;
  capaTotal: number;
  capaClosed: number;
  capaOverdue: number;
  closureRate: number;
}

/**
 * F2-203: every figure here is now measured from a register the server answered.
 *
 * `totalEngagements` was `MOCK_ENGAGEMENTS.length` — the length of a demo corpus, so
 * every tenant's Audit Committee workspace reported the same 6. `engagementsInFlight`
 * was an INCLUSION list (`PLANNED || FIELDWORK`) over a 7-value status vocabulary, so it
 * silently dropped ENGAGED, MGMT_RESPONSE, OPENING_MEETING and REPORTING — 5 of 12 real
 * engagements where 10 are not closed. It is the complement of a declared terminal set
 * now, which is F2-196's rule: an unrecognised status is chased, not excused.
 */
export function aggregateAuditLifecycle(): AuditLifecycleStats {
  const findings = loadFindings();
  const capas = loadCapas();
  const wps = loadWorkingPapers();
  const capaClosed = capas.filter(c => c.status === 'CLOSED').length;
  const capaOverdue = capas.filter(c => new Date(c.targetDate) < new Date() && c.status !== 'CLOSED').length;
  return {
    totalEngagements: _engagements.length,
    engagementsInFlight: _engagements.filter(e => isEngagementInFlight(e.status)).length,
    workingPapersTotal: wps.length,
    workingPapersInReview: wps.filter(w => w.status === 'PEER_REVIEW').length,
    findingsOpen: findings.filter(f => isFindingOpen(f.status)).length,
    findingsCritical: findings.filter(f => f.severity === 'CRITICAL' || f.severity === 'HIGH').length,
    capaTotal: capas.length,
    capaClosed,
    capaOverdue,
    closureRate: capas.length > 0 ? Math.round((capaClosed / capas.length) * 100) : 0,
  };
}

/**
 * Resolve an engagement code against the REAL catalogue.
 *
 * Returns undefined when the code is not in it — which today is every code the annual
 * plan schedules (`ENG-CYBER-2026Q1`…) and every code the registers reference. The
 * caller must render the code itself in that case, not drop the row: this function
 * used to search a mock corpus and its callers treated undefined as "nothing to show",
 * so the Plan tab's quarter cards rendered empty while three engagements were scheduled
 * in each.
 */
export function pickEngagementByCode(code: string): AuditEngagementDto | undefined {
  return _engagements.find(e => e.engagementCode === code);
}
