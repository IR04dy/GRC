import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { stripComments } from './support/sourceScan';
import { isIncidentOpen, INCIDENT_TERMINAL_STATUSES } from '../lib/incidentStatus';

/**
 * F2-360 — the cyber-incident "open/active" count is the COMPLEMENT of a declared terminal set,
 * not a hand-typed exclusion list (F2-196).
 *
 * IncidentsPage had THREE readers of "open" that disagreed: the regulator picker excluded the
 * correct { CLOSED, RESOLVED }; open_count excluded the phantom { CLOSED, RECOVERED } (RECOVERED is
 * 0 rows / not a legal status per IncidentService.legalStatus — F2-359 — so the 10/44 RESOLVED rows
 * leaked in and the "Open" KPI over-reported); the critical KPI excluded only { CLOSED }. The fix
 * routes all three through isIncidentOpen so they cannot drift.
 *
 * The verification is a UNIT leg (the predicate is correct) plus a SOURCE-SCAN wiring leg (F2-205:
 * a correct predicate says nothing until the page CALLS it — here at every open-reader). A
 * source-scan is a stronger wiring proof than a single render test would have been: it covers all
 * three sites, including the regulator picker, without extracting the KPI strip to a leaf (the
 * F2-359 filing assumed a render test and therefore an extraction; this path needs neither).
 */
describe('F2-360 incident "open" = complement of the declared terminal set', () => {

  it('the terminal set mirrors IncidentService.legalStatus (RESOLVED, CLOSED)', () => {
    expect([...INCIDENT_TERMINAL_STATUSES].sort()).toEqual(['CLOSED', 'RESOLVED']);
  });

  it('isIncidentOpen: OPEN/INVESTIGATING open; RESOLVED/CLOSED terminal; absent reads open', () => {
    expect(isIncidentOpen('OPEN')).toBe(true);
    expect(isIncidentOpen('INVESTIGATING')).toBe(true);
    expect(isIncidentOpen('RESOLVED')).toBe(false);   // the 10/44 rows the old open_count over-counted
    expect(isIncidentOpen('CLOSED')).toBe(false);
    expect(isIncidentOpen(null)).toBe(true);
    expect(isIncidentOpen(undefined)).toBe(true);
    // RECOVERED is not a legal status; if it ever appeared it is not terminal (matches the backend,
    // which would coerce it to OPEN) — so it must never be treated as a done/closed state.
    expect(isIncidentOpen('RECOVERED')).toBe(true);
  });

  const page = stripComments(
    readFileSync(join(__dirname, '..', 'pages/incidents/IncidentsPage.tsx'), 'utf8'));

  it('every open/active reader routes through isIncidentOpen (open_count, critical, regulator picker)', () => {
    // Wiring proof: the three readers each call the predicate. A revert of ANY one drops the count
    // below 3 (pre-fix = 0). This is the F2-205 "does the code CALL it" leg.
    const calls = (page.match(/isIncidentOpen\s*\(/g) ?? []).length;
    expect(calls,
      'open_count, critical and the regulator-template picker must each call isIncidentOpen — ' +
      'a re-inlined status comparison is the F2-196 drift this gate exists to catch')
      .toBeGreaterThanOrEqual(3);
  });

  it('the phantom-RECOVERED exclusion is gone (the exact F2-359/F2-360 defect signature)', () => {
    // open_count was `i.status !== 'CLOSED' && i.status !== 'RECOVERED'`. The only surviving
    // RECOVERED literals are the edit-form option and a timeline icon check (`=== 'RECOVERED'`),
    // neither a `!==` open-comparison. Comments are stripped so an explanatory note cannot satisfy
    // this (F2-193).
    expect(page.includes("!== 'RECOVERED'"),
      "a bare `status !== 'RECOVERED'` open-comparison is the F2-359 phantom-exclusion defect")
      .toBe(false);
  });
});
