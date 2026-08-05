// Committees + meetings API client — uses shared api.ts.

import { api, downloadFile } from './api';

export type CommitteeStatus = 'ACTIVE' | 'INACTIVE' | 'DISSOLVED';
export type MeetingStatus   = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'MINUTES_APPROVED' | 'CANCELLED';
// A committee instance may carry any of the catalogue's committee types: the
// admin.committees CK_com_type constraint was widened to the full
// ExtendedCommitteeType set, and the create form now offers all of them. Kept
// as an alias so the many existing `CommitteeType` references stay valid.
export type CommitteeType   = ExtendedCommitteeType;
export type MeetingCadence  = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'AD_HOC';

export interface Committee {
  id: number; code: string; externalId?: string;
  nameEn: string; nameAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  committeeType: CommitteeType;
  chairpersonName?: string; chairpersonRole?: string;
  secretaryName?: string;
  membersJson?: string;
  charterEn?: string; charterAr?: string;
  meetingCadence: MeetingCadence;
  quorumPct?: number;
  status: CommitteeStatus;
  dissolvedAt?: string; dissolvedReason?: string;
  createdAt: string; updatedAt: string;
}

export interface CommitteeMember { name: string; role: string; votingMember?: boolean; }

export interface CommitteeMeeting {
  id: number; meetingCode: string; externalId?: string;
  committeeId: number; committeeCode?: string; committeeNameEn?: string;
  titleEn?: string; titleAr?: string;
  scheduledAt: string; startedAt?: string; endedAt?: string;
  locationOrLink?: string;
  agendaJson?: string; attendeesJson?: string; quorumMet?: number;
  minutesEn?: string; minutesAr?: string;
  decisionsJson?: string; actionItemsJson?: string;
  chairName?: string; secretaryName?: string;
  status: MeetingStatus; cancelReason?: string;
  minutesApprovedAt?: string; approverName?: string;
  createdAt: string; updatedAt: string;
}

/**
 * One agenda line.
 *
 * ⚠ CE-13 — the platform writes the title under **two** key shapes and this interface declared one.
 * Measured across all 34 dev meetings: 24 agendas — every real committee, both quarters, including the
 * CAB's own next sitting — key their items `titleEn`/`titleAr`, while the 8 older DEMO meetings and
 * *everything* `populateAgenda` builds key them `topicEn`/`topicAr`. So the agenda list rendered an
 * ordered list of **empty bullets** for 24 of 34 meetings, and the CE-14a agenda-pack PDF (which read
 * only `titleEn`) printed a dash for the rest. Both readers were wrong, in opposite directions.
 *
 * `topicEn` stays canonical for writers; readers accept either through {@link agendaTopic}.
 */
export interface AgendaItem {
  seq: number;
  topicEn?: string;
  topicAr?: string;
  /** The shape the twelve real committees are seeded with. Read, never written. */
  titleEn?: string;
  titleAr?: string;
  presenter?: string;
  minutesAllocated?: number;
  /** Set by the S7b decision block — 'CHANGE' for a change referred to this committee. */
  sourceModule?: string;
}

/**
 * The item's text in the reader's language, falling through to the other language rather than to a
 * blank line: a title someone can read in English beats an empty bullet for an Arabic reader.
 */
export function agendaTopic(item: AgendaItem, isAr: boolean): string {
  const pick = isAr
    ? [item.topicAr, item.titleAr, item.topicEn, item.titleEn]
    : [item.topicEn, item.titleEn];
  return pick.find(v => v != null && String(v).trim() !== '') ?? '';
}
export interface AttendeeEntry { name: string; role?: string; present?: boolean | null; votingMember?: boolean; }
export interface Decision { seq: number; decisionEn: string; decisionAr?: string; voteFor?: number; voteAgainst?: number; voteAbstain?: number; outcome?: string; }
export interface ActionItem { seq: number; actionEn: string; owner?: string; dueDate?: string; status?: string; }

const BASE = '/api/admin/committees';

export function listCommittees(status?: CommitteeStatus, page = 0, size = 200):
    Promise<{ content: Committee[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${BASE}?page=${page}&size=${size}${qs}`);
}

export function createCommittee(body: {
  code: string; nameEn: string; nameAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  committeeType: CommitteeType;
  chairpersonName?: string; secretaryName?: string;
  charterEn?: string; charterAr?: string;
  meetingCadence?: MeetingCadence; quorumPct?: number;
}): Promise<Committee> {
  return api.post(BASE, body);
}

export function dissolveCommittee(id: number, reason: string): Promise<Committee> {
  return api.post(`${BASE}/${id}/dissolve`, { reason });
}

// ── ADR-017 D-2: per-tenant change authority (CAB / ECAB) ──────────────────────
//
// The screen the 409 `change-authority-not-configured` refusal names. Before this
// existed, the only way a tenant could name its own change authority was a DBA
// INSERT into admin.committee_gate_map — so the refusal had no remedy inside the
// product, which is the dead end D-2's own conditions rule out.

export type AuthorityGate = 'CAB' | 'ECAB';

export interface GateAuthority {
  gate: AuthorityGate;
  committeeId?: number | null;
  committeeNameEn?: string;
  committeeNameAr?: string;
  committeeType?: string;
  committeeStatus?: CommitteeStatus;
  /** The server's verdict — NOT "a row exists". A row pointing at a dissolved
   *  committee comes back configured=false with fault=COMMITTEE_UNAVAILABLE. */
  configured: boolean;
  fault?: 'NOT_MAPPED' | 'COMMITTEE_UNAVAILABLE' | null;
}

export interface EligibleCommittee {
  id: number; nameEn: string; nameAr?: string;
  committeeType: string; status: CommitteeStatus;
}

export function listGateAuthority(): Promise<GateAuthority[]> {
  return api.get('/api/v1/changes/authority');
}

export function listEligibleAuthorities(): Promise<EligibleCommittee[]> {
  return api.get('/api/v1/changes/authority/eligible');
}

export function setGateAuthority(gate: AuthorityGate, committeeId: number):
    Promise<{ gate: string; committeeId: number; configured: boolean }> {
  return api.put(`/api/v1/changes/authority/${gate}`, { committeeId });
}

export function clearGateAuthority(gate: AuthorityGate): Promise<void> {
  return api.delete(`/api/v1/changes/authority/${gate}`);
}

export function listMeetings(status?: MeetingStatus, page = 0, size = 200):
    Promise<{ content: CommitteeMeeting[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${BASE}/meetings?page=${page}&size=${size}${qs}`);
}

export function createMeeting(body: {
  committeeId: number; titleEn?: string; titleAr?: string;
  scheduledAt: string; locationOrLink?: string; agendaJson?: string;
}): Promise<CommitteeMeeting> {
  return api.post(`${BASE}/meetings`, body);
}

export function startMeeting(id: number): Promise<CommitteeMeeting> {
  return api.post(`${BASE}/meetings/${id}/start`);
}

export function completeMeeting(id: number, body?: { minutesEn?: string; minutesAr?: string }): Promise<CommitteeMeeting> {
  return api.post(`${BASE}/meetings/${id}/complete`, body ?? {});
}

export function approveMinutes(id: number): Promise<CommitteeMeeting> {
  return api.post(`${BASE}/meetings/${id}/approve-minutes`);
}

export function cancelMeeting(id: number, reason: string): Promise<CommitteeMeeting> {
  return api.post(`${BASE}/meetings/${id}/cancel`, { reason });
}

// ── CH-14 attendance · F2-28 seat-bound quorum ──────────────────────────────

/**
 * F2-28 — what the caller sends: who was in the room. Nothing else.
 *
 * A mark names a roster seat, or a guest. Identity and voting eligibility come from the seat,
 * so the request can no longer author them — which is the finding: the old shape carried a
 * free-text name plus a client-set `votingMember`, and the quorum denominator was a count of
 * the rows supplied, so inventing six voting attendees and marking four present made a
 * committee quorate.
 *
 * Absence needs no mark: a voting seat with no mark is absent and still counts in the
 * denominator. Forgetting one can only make a meeting look *less* quorate.
 */
export interface AttendanceMark {
  /** The roster seat (`CommitteeMemberTerm.id`). Omit for a guest. */
  memberTermId?: number | null;
  /** Guest's name — required when there is no `memberTermId`. */
  guestName?: string | null;
  guestRole?: string | null;
  present?: boolean;
  coiDeclared?: boolean;
  coiNote?: string | null;
}

/** Output only — `votingMember` is resolved from the seat, never echoed from a request. */
export interface AttendeeRow {
  memberTermId?: number | null;
  memberName: string; memberRole?: string;
  votingMember?: boolean; present?: boolean;
  coiDeclared?: boolean; coiNote?: string;
  /** A non-member who attended; never counts toward quorum. */
  guest?: boolean;
}

export interface QuorumView {
  meetingId: number; quorumPct?: number;
  /** The denominator: the committee's ACTIVE VOTING ROSTER — not a count of attendance rows.
   *  An absent member counts here, which is what quorum means. */
  totalVoting: number;
  presentVoting: number; requiredVoting: number;
  quorumMet: boolean;
  /** `COMPUTED` = derived by the platform · `ASSERTED_LEGACY` = a flag a caller supplied before
   *  V319 (17 meetings, 15 of them e-signed) · absent = no quorum established. */
  quorumSource?: 'COMPUTED' | 'ASSERTED_LEGACY';
  computedAt?: string;
  /** Voting seats nobody marked — absent by default. Surfaced so the rule is visible. */
  unrecordedVoting: number;
  /** Voting seats with no platform user (F2-26) — why a member may not be able to self-vote. */
  rosterUnlinked: number;
  attendees: AttendeeRow[];
}

export function recordAttendance(meetingId: number, marks: AttendanceMark[]): Promise<QuorumView> {
  return api.post(`${BASE}/meetings/${meetingId}/attendance`, { marks });
}
export function getQuorum(meetingId: number): Promise<QuorumView> {
  return api.get(`${BASE}/meetings/${meetingId}/quorum`);
}

// ── CH-14: per-member resolution voting ────────────────────────────────────
export interface ResolutionVote {
  id: number; resolutionId: number; meetingId?: number;
  memberName: string; memberRole?: string;
  vote: string; rationale?: string; createdAt?: string;
}
export function recordResolutionVote(
  resolutionId: number,
  body: { memberName: string; memberRole?: string; vote: string; rationale?: string },
): Promise<Resolution> {
  return api.post(`/api/v1/committees/resolutions/${resolutionId}/votes`, body);
}
export function listResolutionVotes(resolutionId: number): Promise<ResolutionVote[]> {
  return api.get(`/api/v1/committees/resolutions/${resolutionId}/votes`);
}

// ─── Catalog (V154-V156) ───────────────────────────────────────────────
// The 19-template Saudi-regulated catalog + standing-item library + forms
// library. Global (not tenant-scoped) — every tenant sees the same data.
// Endpoint base: /api/v1/committees/catalog

export type RegulatoryDomain = 'BANKING' | 'INSURANCE' | 'CMA' | 'PUBLIC' | 'HEALTHCARE' | 'GENERAL';
export type ExtendedCommitteeType =
  | 'BOARD' | 'EXEC_COMMITTEE' | 'RISK_COMMITTEE' | 'AUDIT_COMMITTEE'
  | 'BCM_COMMITTEE' | 'COMPLIANCE_COMMITTEE' | 'TECH_COMMITTEE'
  | 'NOMINATION_REMUNERATION' | 'CYBER_SECURITY' | 'DATA_GOVERNANCE'
  | 'PRIVACY_DPO' | 'CRISIS_MANAGEMENT' | 'AML' | 'IT_STEERING'
  | 'ALCO' | 'INVESTMENT' | 'AI_ETHICS' | 'ANTI_FRAUD'
  | 'INTERNAL_CONTROL' | 'ESG_SUSTAINABILITY' | 'ARCH_REVIEW' | 'OTHER';

export type FormType =
  | 'CHARTER' | 'AGENDA' | 'PRE_READ_COVERSHEET' | 'ATTENDANCE'
  | 'MOTION' | 'MINUTES' | 'ACTION_REGISTER' | 'COI_DECLARATION'
  | 'SKILLS_MATRIX' | 'ANNUAL_EVALUATION';

export interface CommitteeTemplate {
  id: number;
  templateCode: string;
  committeeType: ExtendedCommitteeType;
  regulatoryDomain: RegulatoryDomain;
  regulatorAnchors: string | null;       // comma-separated
  nameEn: string;
  nameAr: string;
  mandateEn: string;
  mandateAr: string;
  authorityEn: string;
  authorityAr: string;
  compositionEn: string;
  compositionAr: string;
  quorumPct: number;
  cadence: 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'SEMI_ANNUAL' | 'ANNUAL' | 'AD_HOC';
  termYears: number | null;
  reportsToTemplateCode: string | null;
  escalationEn: string | null;
  escalationAr: string | null;
  regulatorCitationEn: string | null;
  regulatorCitationAr: string | null;
  independenceRequired: number;
  financialExpertRequired: number;
  regulatoryReportingEn: string | null;
  isActive: number;
}

export interface StandingItem {
  id: number;
  templateCode: string;
  seq: number;
  topicEn: string;
  topicAr: string;
  presenterRole: string | null;
  minutesAllocated: number;
  isMandatory: number;
  sourceModule: string | null;
  descriptionEn: string | null;
  descriptionAr: string | null;
}

export interface FormTemplate {
  id: number;
  formCode: string;
  formType: FormType;
  applicableTo: string;                  // comma-separated committee_type or 'ALL'
  regulatorAnchors: string | null;
  titleEn: string;
  titleAr: string;
  purposeEn: string;
  purposeAr: string;
  contentMdEn: string;
  contentMdAr: string;
  completionGuideEn: string | null;
  completionGuideAr: string | null;
  version: string;
  isActive: number;
}

const CATALOG_BASE = '/api/v1/committees/catalog';

export function listTemplates(domain?: RegulatoryDomain): Promise<CommitteeTemplate[]> {
  const qs = domain ? `?domain=${domain}` : '';
  return api.get(`${CATALOG_BASE}/templates${qs}`);
}

export function getTemplate(templateCode: string): Promise<CommitteeTemplate> {
  return api.get(`${CATALOG_BASE}/templates/${encodeURIComponent(templateCode)}`);
}

export function getStandingItems(templateCode: string): Promise<StandingItem[]> {
  return api.get(`${CATALOG_BASE}/templates/${encodeURIComponent(templateCode)}/standing-items`);
}

export function listForms(committeeType?: ExtendedCommitteeType): Promise<FormTemplate[]> {
  const qs = committeeType ? `?committeeType=${committeeType}` : '';
  return api.get(`${CATALOG_BASE}/forms${qs}`);
}

export function getForm(formCode: string): Promise<FormTemplate> {
  return api.get(`${CATALOG_BASE}/forms/${encodeURIComponent(formCode)}`);
}

/** Download the form as a branded PDF — server-side render via OpenPDF
 *  with bilingual cover band, purpose, body, and completion guide.
 *  Filename: {formCode}-v{version}.pdf. */
export function exportFormPdf(formCode: string, version?: string): Promise<void> {
  const fallback = `${formCode}${version ? `-v${version}` : ''}.pdf`;
  return downloadFile(
    `${CATALOG_BASE}/forms/${encodeURIComponent(formCode)}/export?format=pdf`,
    fallback);
}

// ─── Member registry (V154 — terms + skills) ──────────────────────────

export interface CommitteeMemberTerm {
  id: number;
  committeeId: number;
  memberName: string;
  memberUserId: number | null;
  role: string;
  isVotingMember: number;
  isIndependent: number;
  independenceBasisEn: string | null;
  appointedAt: string;
  appointedByReso: string | null;
  termStartsAt: string;
  termEndsAt: string | null;
  termNumber: number;
  renewalStatus: 'ACTIVE' | 'EXPIRED' | 'RESIGNED' | 'REMOVED' | 'RENEWED';
  endReason: string | null;
  endedAt: string | null;
  createdAt: string;
  /** F2-26 — declared to have no platform login (external auditor/advisor). Distinguishes
   *  a seat that will never be linked from one that simply has not been linked yet. */
  noPlatformUser?: boolean;
  noPlatformUserBasisEn?: string | null;
}

// ─── F2-26: roster seat → platform user ───────────────────────────────

/** LINKED = holds a platform user · EXTERNAL = declared to have none · UNLINKED = gap. */
export type SeatLinkState = 'LINKED' | 'EXTERNAL' | 'UNLINKED';

export interface LinkableUser {
  id: number;
  username: string;
  fullNameEn: string | null;
  fullNameAr: string | null;
  jobTitleEn: string | null;
  /** ACTIVE seats this user already holds on the committee being populated. > 0 means
   *  picking them would 409 — one person cannot count twice toward quorum. */
  heldSeats: number;
}

export interface SeatLinkageSeat {
  termId: number;
  memberName: string;
  role: string;
  votingMember: boolean;
  state: SeatLinkState;
  memberUserId: number | null;
  username: string | null;
  fullNameEn: string | null;
  basisEn: string | null;
}

export interface SeatLinkageReport {
  committeeId: number;
  activeSeats: number;
  linked: number;
  declaredExternal: number;
  unlinked: number;
  votingSeats: number;
  votingLinked: number;
  votingUnlinked: number;
  /** linked / (activeSeats − declaredExternal): declared-external seats leave the
   *  denominator, or a committee that seats an external auditor could never reach 100%. */
  coveragePct: number;
  seats: SeatLinkageSeat[];
}

export interface LinkUserBody {
  memberUserId?: number | null;
  noPlatformUser?: boolean;
  basisEn?: string | null;
  reason?: string | null;
}

export interface CommitteeMemberSkill {
  id: number;
  committeeId: number;
  memberTermId: number | null;
  memberName: string;
  skillDimension: string;       // FINANCIAL_EXPERT | CYBER_EXPERT | …
  skillLevel: 'BASIC' | 'INTERMEDIATE' | 'EXPERT';
  evidenceEn: string | null;
  certification: string | null;
  yearsExperience: number | null;
  lastReviewedAt: string | null;
  createdAt: string;
}

export interface IndependenceStats {
  activeMembers: number;
  independentMembers: number;
  independenceRatioPct: number;
}

const REG_BASE = '/api/v1/committees/registry';

export function listMemberTerms(committeeId: number): Promise<CommitteeMemberTerm[]> {
  return api.get(`${REG_BASE}/committees/${committeeId}/terms`);
}
export function listActiveMembers(committeeId: number): Promise<CommitteeMemberTerm[]> {
  return api.get(`${REG_BASE}/committees/${committeeId}/active-members`);
}
export function getIndependenceStats(committeeId: number): Promise<IndependenceStats> {
  return api.get(`${REG_BASE}/committees/${committeeId}/independence`);
}
export function appointMember(body: Partial<CommitteeMemberTerm>): Promise<CommitteeMemberTerm> {
  return api.post(`${REG_BASE}/terms`, body);
}
export function endMemberTerm(termId: number, renewalStatus: string, reason: string)
    : Promise<CommitteeMemberTerm> {
  return api.post(`${REG_BASE}/terms/${termId}/end`, { renewalStatus, reason });
}
export function renewMemberTerm(termId: number, newStart: string, newEnd: string, resolutionCode: string)
    : Promise<CommitteeMemberTerm> {
  return api.post(`${REG_BASE}/terms/${termId}/renew`, { newStart, newEnd, resolutionCode });
}

/**
 * F2-26 — set which platform user holds a seat: link, re-link, declare external, or clear.
 *
 * `{ memberUserId }` links · `{ noPlatformUser: true, basisEn }` declares external ·
 * `{}` clears back to unlinked. 409 when the user already holds an active seat on the
 * committee (one person must not count twice toward quorum) or when the term is closed.
 */
export function linkSeatUser(termId: number, body: LinkUserBody): Promise<CommitteeMemberTerm> {
  return api.post(`${REG_BASE}/terms/${termId}/link-user`, body);
}

/**
 * F2-26 — users the secretariat may attach to a seat.
 *
 * Served by the committee module under `ADMIN:WRITE`, **not** from the IAM user list:
 * `BOARD_SECRETARY` holds `ADMIN:WRITE` but not `IAM:READ`, so a picker sourced from
 * `/api/v1/admin/users` would 403 for the persona that maintains rosters. This is also why
 * the picker must not use `lib/userDirectory` — that is a hardcoded demo directory whose
 * ids (`usr-001`) do not exist in `iam.users`, and linking a governance seat to a
 * fabricated identity is the defect F2-26 is about.
 */
export function listLinkableUsers(committeeId?: number, q?: string): Promise<LinkableUser[]> {
  const p = new URLSearchParams();
  if (committeeId != null) p.set('committeeId', String(committeeId));
  if (q) p.set('q', q);
  const qs = p.toString();
  return api.get(`${REG_BASE}/linkable-users${qs ? `?${qs}` : ''}`);
}

/** F2-26 — roster-linkage coverage: how many seats can cast their own vote. */
export function getSeatLinkage(committeeId: number): Promise<SeatLinkageReport> {
  return api.get(`${REG_BASE}/committees/${committeeId}/linkage`);
}

export function listSkills(committeeId: number): Promise<CommitteeMemberSkill[]> {
  return api.get(`${REG_BASE}/committees/${committeeId}/skills`);
}
export function skillCoverage(committeeId: number): Promise<Record<string, number>> {
  return api.get(`${REG_BASE}/committees/${committeeId}/skills/coverage`);
}
export function addSkill(body: Partial<CommitteeMemberSkill>): Promise<CommitteeMemberSkill> {
  return api.post(`${REG_BASE}/skills`, body);
}
export function removeSkill(skillId: number): Promise<void> {
  return api.delete(`${REG_BASE}/skills/${skillId}`);
}

// ─── Meeting papers (V154 — pre-read tracking) ────────────────────────

export interface MeetingPaper {
  id: number;
  meetingId: number;
  paperCode: string;
  titleEn: string;
  titleAr: string | null;
  agendaItemSeq: number | null;
  authorName: string | null;
  authorRole: string | null;
  circulatedAt: string | null;
  circulatedBy: number | null;
  confidentiality: ConfidentialityLevel;
  fileStorageRef: string | null;
  readReceiptsJson: string | null;
  latePaper: number;
  latePaperReason: string | null;
  createdAt: string;
}

export interface MeetingPaperStats {
  totalPapers: number;
  papersWithReceipts: number;
  latePapers: number;
}

const PAPERS_BASE = '/api/v1/committees/papers';

export function listMeetingPapers(meetingId: number): Promise<MeetingPaper[]> {
  return api.get(`${PAPERS_BASE}/by-meeting/${meetingId}`);
}
export function meetingPaperStats(meetingId: number): Promise<MeetingPaperStats> {
  return api.get(`${PAPERS_BASE}/by-meeting/${meetingId}/stats`);
}
export function circulatePaper(body: Partial<MeetingPaper>): Promise<MeetingPaper> {
  return api.post(`${PAPERS_BASE}/circulate`, body);
}
export function markPaperRead(paperId: number, userId: number): Promise<MeetingPaper> {
  return api.post(`${PAPERS_BASE}/${paperId}/read/${userId}`);
}
export function markPaperLate(paperId: number, reason: string): Promise<MeetingPaper> {
  return api.post(`${PAPERS_BASE}/${paperId}/mark-late`, { reason });
}

/** Tone palette for the badge column in the catalog page — anchors render
 *  primary, type renders info, domain renders warning. Keeps the look
 *  consistent across template list, charter detail, and forms library. */
export const DOMAIN_TONE: Record<RegulatoryDomain, 'info' | 'warning' | 'success' | 'neutral'> = {
  BANKING: 'warning', INSURANCE: 'warning', CMA: 'info',
  PUBLIC: 'success', HEALTHCARE: 'success', GENERAL: 'neutral',
};

export const TYPE_LABEL_EN: Record<ExtendedCommitteeType, string> = {
  BOARD: 'Board',
  EXEC_COMMITTEE: 'Executive',
  RISK_COMMITTEE: 'Risk',
  AUDIT_COMMITTEE: 'Audit',
  BCM_COMMITTEE: 'BCM',
  COMPLIANCE_COMMITTEE: 'Compliance',
  TECH_COMMITTEE: 'Technology',
  NOMINATION_REMUNERATION: 'Nomination & Remuneration',
  CYBER_SECURITY: 'Cyber Security',
  DATA_GOVERNANCE: 'Data Governance',
  PRIVACY_DPO: 'Privacy / DPO',
  CRISIS_MANAGEMENT: 'Crisis Management',
  AML: 'AML',
  IT_STEERING: 'IT Steering',
  ALCO: 'ALCO',
  INVESTMENT: 'Investment',
  AI_ETHICS: 'AI Ethics',
  ANTI_FRAUD: 'Anti-Fraud',
  INTERNAL_CONTROL: 'Internal Control',
  ESG_SUSTAINABILITY: 'ESG / Sustainability',
  ARCH_REVIEW: 'Architecture Review',
  OTHER: 'Other',
};

// ─── Resolutions + Action Items (V154 + operational layer) ───────────
// First-class records replacing the V40 decisions_json + action_items_json
// blobs. Resolutions are immutable; actions have a full lifecycle.

/**
 * S7a / V349 — `PROPOSED` is a motion **raised and not yet decided**, and the other four are
 * decisions. Keep this in step with `CommitteeDecisionAuthority.ALL` on the server, which is
 * itself ratcheted against `CK_resolution_outcome`.
 *
 * ⚠ Before V349 all four permitted values were verdicts, so a motion could not be raised without
 * recording one — and the form below defaulted the field to `APPROVED`, which is wired to a
 * regulator disclosure clock (AR-146) and a board-register filing instruction (AR-147).
 */
export type ResolutionOutcome = 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'DEFERRED' | 'WITHDRAWN';

/** The outcomes that mean the committee has decided — mirrors `CommitteeDecisionAuthority.DECIDED`. */
export const DECIDED_OUTCOMES: readonly ResolutionOutcome[] =
  ['APPROVED', 'REJECTED', 'DEFERRED', 'WITHDRAWN'] as const;

export const isDecidedOutcome = (o: string | null | undefined): boolean =>
  !!o && (DECIDED_OUTCOMES as readonly string[]).includes(o);
export type ConfidentialityLevel = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED';
export type ActionStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'OVERDUE' | 'CANCELLED' | 'DEFERRED';
export type ActionPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';

export interface Resolution {
  id: number;
  meetingId: number;
  committeeId: number;
  resolutionCode: string;
  seq: number;
  agendaItemSeq: number | null;
  titleEn: string;
  titleAr: string | null;
  bodyEn: string;
  bodyAr: string | null;
  proposedBy: string | null;
  secondedBy: string | null;
  voteFor: number;
  voteAgainst: number;
  voteAbstain: number;
  outcome: ResolutionOutcome;
  confidentiality: ConfidentialityLevel;
  effectiveFrom: string | null;
  reviewRequiredBy: string | null;
  regulatorNotify: number;
  dissentNotesEn: string | null;
  dissentNotesAr: string | null;
  createdAt: string;
  /**
   * F2-40 — where the tally came from. `COMPUTED` from per-member vote rows, `ASSERTED`
   * recorded from the minutes by `tallyRecordedByName`, `ASSERTED_LEGACY` present before V322
   * with no recoverable provenance. Server-assigned: the four fields are READ_ONLY on the
   * entity, so posting them does nothing.
   */
  tallySource: 'COMPUTED' | 'ASSERTED' | 'ASSERTED_LEGACY' | null;
  tallyRecordedByUserId: number | null;
  tallyRecordedByName: string | null;
  tallyStampedAt: string | null;
}

export interface ActionItemRecord {
  id: number;
  meetingId: number;
  committeeId: number;
  actionCode: string;
  seq: number;
  agendaItemSeq: number | null;
  titleEn: string;
  titleAr: string | null;
  descriptionEn: string | null;
  descriptionAr: string | null;
  ownerUserId: number | null;
  ownerName: string;
  ownerRole: string | null;
  dueDate: string;
  priority: ActionPriority;
  status: ActionStatus;
  completionNotesEn: string | null;
  completedAt: string | null;
  completedBy: number | null;
  linkedModule: string | null;
  linkedEntityType: string | null;
  linkedEntityId: number | null;
  linkedEntityCode: string | null;
  carriedFromMeetingId: number | null;
  createdAt: string;
}

const RES_BASE = '/api/v1/committees/resolutions';
const ACT_BASE = '/api/v1/committees/action-items';
const CAL_BASE = '/api/v1/committees/calendar';

export function listResolutions(page = 0, size = 50):
    Promise<{ content: Resolution[]; totalElements: number }> {
  return api.get(`${RES_BASE}?page=${page}&size=${size}`);
}

export function getResolution(id: number): Promise<Resolution> {
  return api.get(`${RES_BASE}/${id}`);
}

export function resolutionsByMeeting(meetingId: number): Promise<Resolution[]> {
  return api.get(`${RES_BASE}/by-meeting/${meetingId}`);
}

export function resolutionsByCommittee(committeeId: number): Promise<Resolution[]> {
  return api.get(`${RES_BASE}/by-committee/${committeeId}`);
}

export function createResolution(body: Partial<Resolution>): Promise<Resolution> {
  return api.post(RES_BASE, body);
}

export function listActions(page = 0, size = 50):
    Promise<{ content: ActionItemRecord[]; totalElements: number }> {
  return api.get(`${ACT_BASE}?page=${page}&size=${size}`);
}

export function getAction(id: number): Promise<ActionItemRecord> {
  return api.get(`${ACT_BASE}/${id}`);
}

export function actionsByMeeting(meetingId: number): Promise<ActionItemRecord[]> {
  return api.get(`${ACT_BASE}/by-meeting/${meetingId}`);
}

export function actionsByCommittee(committeeId: number): Promise<ActionItemRecord[]> {
  return api.get(`${ACT_BASE}/by-committee/${committeeId}`);
}

export function openActionsForCommittee(committeeId: number): Promise<ActionItemRecord[]> {
  return api.get(`${ACT_BASE}/open-for-committee/${committeeId}`);
}

export function openActionsForOwner(ownerUserId: number): Promise<ActionItemRecord[]> {
  return api.get(`${ACT_BASE}/open-for-owner/${ownerUserId}`);
}

export function createAction(body: Partial<ActionItemRecord>): Promise<ActionItemRecord> {
  return api.post(ACT_BASE, body);
}

export function startAction(id: number): Promise<ActionItemRecord> {
  return api.post(`${ACT_BASE}/${id}/start`);
}

export function completeAction(id: number, completionNotesEn?: string, completedBy?: number):
    Promise<ActionItemRecord> {
  return api.post(`${ACT_BASE}/${id}/complete`, { completionNotesEn: completionNotesEn ?? '', completedBy: completedBy ?? null });
}

export function cancelAction(id: number, reason: string): Promise<ActionItemRecord> {
  return api.post(`${ACT_BASE}/${id}/cancel`, { reason });
}

export function deferAction(id: number, newDueDate: string, reason: string): Promise<ActionItemRecord> {
  return api.post(`${ACT_BASE}/${id}/defer`, { newDueDate, reason });
}

export function generateAnnualCalendar(committeeId: number, year: number):
    Promise<{ committeeId: number; year: number; created: number }> {
  return api.post(`${CAL_BASE}/generate-calendar/${committeeId}?year=${year}`);
}

export function populateAgenda(meetingId: number): Promise<CommitteeMeeting> {
  return api.post(`${CAL_BASE}/populate-agenda/${meetingId}`);
}

export const OUTCOME_TONE: Record<ResolutionOutcome, 'success' | 'danger' | 'warning' | 'neutral' | 'info'> = {
  // `info` rather than a warning tone: a raised motion is a normal state, not a problem.
  PROPOSED: 'info',
  APPROVED: 'success', REJECTED: 'danger', DEFERRED: 'warning', WITHDRAWN: 'neutral',
};

export const ACTION_STATUS_TONE: Record<ActionStatus, 'warning' | 'info' | 'success' | 'danger' | 'neutral'> = {
  OPEN: 'warning', IN_PROGRESS: 'info', COMPLETED: 'success',
  OVERDUE: 'danger', CANCELLED: 'neutral', DEFERRED: 'warning',
};

export const ACTION_PRIORITY_TONE: Record<ActionPriority, 'neutral' | 'info' | 'warning' | 'danger'> = {
  LOW: 'neutral', NORMAL: 'info', HIGH: 'warning', CRITICAL: 'danger',
};

export const TYPE_LABEL_AR: Record<ExtendedCommitteeType, string> = {
  BOARD: 'مجلس الإدارة',
  EXEC_COMMITTEE: 'تنفيذية',
  RISK_COMMITTEE: 'المخاطر',
  AUDIT_COMMITTEE: 'المراجعة',
  BCM_COMMITTEE: 'استمرارية الأعمال',
  COMPLIANCE_COMMITTEE: 'الالتزام',
  TECH_COMMITTEE: 'التقنية',
  NOMINATION_REMUNERATION: 'الترشيحات والمكافآت',
  CYBER_SECURITY: 'الأمن السيبراني',
  DATA_GOVERNANCE: 'حوكمة البيانات',
  PRIVACY_DPO: 'حماية البيانات',
  CRISIS_MANAGEMENT: 'إدارة الأزمات',
  AML: 'مكافحة غسل الأموال',
  IT_STEERING: 'توجيه تقنية المعلومات',
  ALCO: 'الأصول والخصوم',
  INVESTMENT: 'الاستثمار',
  AI_ETHICS: 'أخلاقيات الذكاء الاصطناعي',
  ANTI_FRAUD: 'مكافحة الاحتيال',
  INTERNAL_CONTROL: 'الرقابة الداخلية',
  ESG_SUSTAINABILITY: 'الاستدامة',
  ARCH_REVIEW: 'مراجعة المعمارية',
  OTHER: 'أخرى',
};

export function parseAgenda(json?: string): AgendaItem[] {
  if (!json) return []; try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseAttendees(json?: string): AttendeeEntry[] {
  if (!json) return []; try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseDecisions(json?: string): Decision[] {
  if (!json) return []; try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseActions(json?: string): ActionItem[] {
  if (!json) return []; try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseMembers(json?: string): CommitteeMember[] {
  if (!json) return []; try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}

export const COM_STATUS_TONE: Record<CommitteeStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  ACTIVE: 'success', INACTIVE: 'warning', DISSOLVED: 'info',
};
export const MTG_STATUS_TONE: Record<MeetingStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  SCHEDULED: 'info', IN_PROGRESS: 'primary', COMPLETED: 'warning',
  MINUTES_APPROVED: 'success', CANCELLED: 'danger',
};
