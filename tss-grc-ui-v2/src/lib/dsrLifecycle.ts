/**
 * DSR lifecycle vocabulary for the `privacy.data_subject_requests` register — the one
 * `PrivacyPage` renders. PDPL Article 23 (30-day response window).
 *
 * ── Why this file exists (F2-196) ──────────────────────────────────────────────
 * `PrivacyPage` asked "is this request finished?" with `status === 'FULFILLED' ||
 * status === 'REJECTED'`, and asked "is it open?" with a separate INCLUSION list
 * `{PENDING, IN_PROGRESS, NEW}`. Measured 2026-08-12 against dev and the deployed
 * database (20 rows each, identical, and no CHECK constraint on the column):
 *
 *   stored: RESOLVED×6  IN_PROGRESS×5  NEW×2  ESCALATED×2  PENDING×2
 *           REJECTED×1  COMPLETED×1  OPEN×1
 *
 * `FULFILLED` is in NONE of them — it is what the server's `fulfilDsr` writes, and it
 * exists in 3 rows of a *different* table. Two consequences, in opposite directions:
 *
 *   • the SLA column and the row actions treated the 7 finished rows (RESOLVED,
 *     COMPLETED) as live, so each showed a red "N days overdue" against the statutory
 *     deadline and still offered Fulfil / Extend / Reject;
 *   • the open count omitted `OPEN` — the status the intake promotion adapter writes,
 *     so a request filed through the platform's own front door did not count — and
 *     `ESCALATED`, giving 9 where 12 are open.
 *
 * A vocabulary mismatch has no direction: it errs whichever way the forgotten token
 * happens to sit relative to the predicate. Hence one whitelist of finished states, and
 * `isDsrOpen` as its COMPLEMENT — never a second list. A status nobody anticipated then
 * counts as open, which is the safe direction for a statutory clock.
 *
 * ── Mirrored, and gated ────────────────────────────────────────────────────────
 * `DSR_REQUEST_TERMINAL` mirrors `DsrRequestLifecycle.TERMINAL` in the backend, and
 * `DsrLifecycleContractTest` reads THIS file and fails the build if the two sets differ
 * in either direction. Change one, change both, in the same commit.
 *
 * ⚠ Three server-side DSR registers exist on three tables with disjoint vocabularies
 * (`privacy.data_subject_requests`, `privacy_lc.dsr_cases`, `data_officer.dsr_request`),
 * plus a fourth browser-local one in `privacyLifecycle.ts`. This file governs the first
 * only. Do not widen it to "all DSR statuses" — the sets are genuinely different, and
 * merging them is what produced the defect above.
 */

/**
 * Statuses meaning the request is finished and its PDPL clock has stopped.
 *
 * FULFILLED and REJECTED are what the server writes; RESOLVED and COMPLETED are what
 * this register actually holds. All four are listed because the column has no CHECK
 * constraint, so all four are reachable — and a token cannot be wished away by leaving
 * it out of this set, only mis-classified.
 */
export const DSR_REQUEST_TERMINAL: readonly string[] = [
  'COMPLETED', 'FULFILLED', 'REJECTED', 'RESOLVED',
];

/**
 * Normalise for comparison. The column is `Latin1_General_100_CI_AS_SC_UTF8` with no
 * CHECK, so `'resolved'` is storable and a bare `=== 'RESOLVED'` would be false against
 * it. Compare through here.
 */
export function normaliseDsrStatus(status: string | null | undefined): string {
  return (status ?? '').trim().toUpperCase();
}

/** True when the request is finished — a whitelist. */
export function isDsrTerminal(status: string | null | undefined): boolean {
  return DSR_REQUEST_TERMINAL.includes(normaliseDsrStatus(status));
}

/** True when the request is still live — the complement, so an unknown status is open. */
export function isDsrOpen(status: string | null | undefined): boolean {
  return !isDsrTerminal(status);
}
