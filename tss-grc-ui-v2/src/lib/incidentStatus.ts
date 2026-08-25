// F2-360 — the cyber-incident lifecycle's TERMINAL states, and "open" as their complement.
//
// The backend authority is IncidentService.legalStatus, which coerces cyber.incidents.status
// to exactly OPEN | INVESTIGATING | RESOLVED | CLOSED (the NIST RECOVERY / POST_INCIDENT phases
// map to RESOLVED). So the done/terminal set is { RESOLVED, CLOSED } — RESOLVED is the
// recovery-phase terminal awaiting formal closure, CLOSED is archived — and RECOVERED is NOT a
// legal status at all (see F2-359).
//
// F2-196: "open" is the COMPLEMENT of a declared terminal set, never a hand-typed inclusion list.
// IncidentsPage previously had THREE readers of "open/active" that disagreed — the regulator
// picker excluded the correct { CLOSED, RESOLVED }, open_count excluded the phantom
// { CLOSED, RECOVERED } (RECOVERED is 0 rows, never written, so RESOLVED leaked in and over-counted
// open by the 10/44 RESOLVED rows), and the critical KPI excluded only { CLOSED }. Routing every
// open/active reader through isIncidentOpen makes the three agree and cannot drift again.
//
// This is a MIRROR of the backend vocabulary (the frontend cannot see the Java state machine),
// so it is declared once here with the authority cited, and NOT a copy of openbanking.ts's
// unrelated SAMA open-banking IncidentStatus (a different register).
export const INCIDENT_TERMINAL_STATUSES: ReadonlySet<string> = new Set(['RESOLVED', 'CLOSED']);

/** True when an incident is NOT in a terminal state (i.e. still open/active). Null-safe: an
 *  absent status reads as open, matching the pre-fix `status !== 'CLOSED'` readers. */
export function isIncidentOpen(status: string | null | undefined): boolean {
  return !INCIDENT_TERMINAL_STATUSES.has(status ?? '');
}
