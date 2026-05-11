// Committee + meeting API client. Backend: /api/admin/committees

export type CommitteeStatus = 'ACTIVE' | 'INACTIVE' | 'DISSOLVED';
export type MeetingStatus   = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'MINUTES_APPROVED' | 'CANCELLED';
export type CommitteeType   = 'BOARD' | 'EXEC_COMMITTEE' | 'RISK_COMMITTEE' | 'AUDIT_COMMITTEE'
                            | 'BCM_COMMITTEE' | 'COMPLIANCE_COMMITTEE' | 'TECH_COMMITTEE' | 'OTHER';
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

export interface AgendaItem { seq: number; topicEn: string; topicAr?: string; presenter?: string; minutesAllocated?: number; }
export interface AttendeeEntry { name: string; role?: string; present?: boolean | null; votingMember?: boolean; }
export interface Decision { seq: number; decisionEn: string; decisionAr?: string; voteFor?: number; voteAgainst?: number; voteAbstain?: number; outcome?: string; }
export interface ActionItem { seq: number; actionEn: string; owner?: string; dueDate?: string; status?: string; }

const BASE = '/api/admin/committees';

function authHeaders(extra?: Record<string,string>): HeadersInit {
  const tok = window.sessionStorage.getItem('grc.accessToken') ?? '';
  return { ...(extra ?? {}), ...(tok ? { Authorization: `Bearer ${tok}` } : {}) };
}

// ─── Committees ─────────────────────────────────────────────────────
export async function listCommittees(status?: CommitteeStatus, page = 0, size = 200):
    Promise<{ content: Committee[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  const res = await fetch(`${BASE}?page=${page}&size=${size}${qs}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listCommittees HTTP ${res.status}`);
  return res.json();
}

export async function createCommittee(body: {
  code: string; nameEn: string; nameAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  committeeType: CommitteeType;
  chairpersonName?: string; secretaryName?: string;
  charterEn?: string; charterAr?: string;
  meetingCadence?: MeetingCadence; quorumPct?: number;
}): Promise<Committee> {
  const res = await fetch(BASE, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`createCommittee HTTP ${res.status}`);
  return res.json();
}

export async function dissolveCommittee(id: number, reason: string): Promise<Committee> {
  const res = await fetch(`${BASE}/${id}/dissolve`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error(`dissolveCommittee HTTP ${res.status}`);
  return res.json();
}

// ─── Meetings ───────────────────────────────────────────────────────
export async function listMeetings(status?: MeetingStatus, page = 0, size = 200):
    Promise<{ content: CommitteeMeeting[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  const res = await fetch(`${BASE}/meetings?page=${page}&size=${size}${qs}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listMeetings HTTP ${res.status}`);
  return res.json();
}

export async function createMeeting(body: {
  committeeId: number; titleEn?: string; titleAr?: string;
  scheduledAt: string; locationOrLink?: string; agendaJson?: string;
}): Promise<CommitteeMeeting> {
  const res = await fetch(`${BASE}/meetings`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`createMeeting HTTP ${res.status}`);
  return res.json();
}

export async function startMeeting(id: number): Promise<CommitteeMeeting> {
  const res = await fetch(`${BASE}/meetings/${id}/start`, { method: 'POST', headers: authHeaders() });
  if (!res.ok) throw new Error(`startMeeting HTTP ${res.status}`);
  return res.json();
}

export async function completeMeeting(id: number, body?: { minutesEn?: string; minutesAr?: string }): Promise<CommitteeMeeting> {
  const res = await fetch(`${BASE}/meetings/${id}/complete`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body ?? {}),
  });
  if (!res.ok) throw new Error(`completeMeeting HTTP ${res.status}`);
  return res.json();
}

export async function approveMinutes(id: number): Promise<CommitteeMeeting> {
  const res = await fetch(`${BASE}/meetings/${id}/approve-minutes`, { method: 'POST', headers: authHeaders() });
  if (!res.ok) throw new Error(`approveMinutes HTTP ${res.status}`);
  return res.json();
}

export async function cancelMeeting(id: number, reason: string): Promise<CommitteeMeeting> {
  const res = await fetch(`${BASE}/meetings/${id}/cancel`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error(`cancelMeeting HTTP ${res.status}`);
  return res.json();
}

// ─── Parsers ────────────────────────────────────────────────────────
export function parseAgenda(json?: string): AgendaItem[] {
  if (!json) return [];
  try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseAttendees(json?: string): AttendeeEntry[] {
  if (!json) return [];
  try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseDecisions(json?: string): Decision[] {
  if (!json) return [];
  try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseActions(json?: string): ActionItem[] {
  if (!json) return [];
  try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseMembers(json?: string): CommitteeMember[] {
  if (!json) return [];
  try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}

export const COM_STATUS_TONE: Record<CommitteeStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  ACTIVE: 'success', INACTIVE: 'warning', DISSOLVED: 'info',
};
export const MTG_STATUS_TONE: Record<MeetingStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  SCHEDULED:        'info',
  IN_PROGRESS:      'primary',
  COMPLETED:        'warning',
  MINUTES_APPROVED: 'success',
  CANCELLED:        'danger',
};
