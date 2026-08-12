import { describe, it, expect } from 'vitest';
import { DSR_REQUEST_TERMINAL, isDsrTerminal, isDsrOpen, normaliseDsrStatus } from './dsrLifecycle';

/**
 * F2-196 — the DSR terminal/open predicate for `privacy.data_subject_requests`.
 *
 * The backend half of this contract (parity with `DsrRequestLifecycle.TERMINAL`, and the
 * rule that `PrivacyPage` may not compare statuses inline) is enforced by
 * `DsrLifecycleContractTest`, which reads these files from disk. What lives here is the
 * behaviour a reader of the page depends on.
 */
describe('DSR request lifecycle', () => {
  it('treats every status this register actually stores as finished as terminal', () => {
    // Measured 2026-08-12 in dev and the deployed database, 20 rows each: RESOLVED x6 and
    // COMPLETED x1 are finished requests. The page used to test only for FULFILLED, which
    // appears in NONE of them — so those 7 rows each rendered a red "N days overdue"
    // against the PDPL Article 23 deadline and still offered Fulfil / Extend / Reject.
    for (const finished of ['FULFILLED', 'REJECTED', 'RESOLVED', 'COMPLETED']) {
      expect(isDsrTerminal(finished)).toBe(true);
      expect(isDsrOpen(finished)).toBe(false);
    }
  });

  it('counts every live status as open, including the one intake promotion writes', () => {
    // `OPEN` is what DsrPromotionAdapter writes, and the old inclusion list
    // {PENDING, IN_PROGRESS, NEW} dropped it — so a DSR filed through the platform's own
    // front door did not appear in the PDPL tile. `ESCALATED` was dropped too.
    for (const live of ['NEW', 'PENDING', 'OPEN', 'IN_PROGRESS', 'ESCALATED']) {
      expect(isDsrOpen(live)).toBe(true);
      expect(isDsrTerminal(live)).toBe(false);
    }
  });

  it('treats an undeclared status as OPEN, never as finished', () => {
    // The load-bearing asymmetry: open is the COMPLEMENT of finished, not a second list.
    // An inclusion list answers "not open" here, which silently excuses a request from a
    // statutory clock. Erring towards open means it gets chased instead.
    for (const unknown of ['AWAITING_LEGAL', 'PAUSED', 'ZZZ', '', '   ', null, undefined]) {
      expect(isDsrOpen(unknown)).toBe(true);
      expect(isDsrTerminal(unknown)).toBe(false);
    }
  });

  it('compares case-insensitively because the column is CI-collated with no CHECK', () => {
    // 'resolved' is storable — there is no CHECK constraint on the column and the collation
    // is Latin1_General_100_CI_AS_SC_UTF8 — so a bare === would miss it. F2-179 family.
    expect(isDsrTerminal('resolved')).toBe(true);
    expect(isDsrTerminal('  Completed ')).toBe(true);
    expect(isDsrTerminal('fUlFiLlEd')).toBe(true);
    expect(isDsrOpen('in_progress')).toBe(true);
    expect(normaliseDsrStatus('  resolved ')).toBe('RESOLVED');
    expect(normaliseDsrStatus(null)).toBe('');
  });

  it('keeps terminal and open exactly complementary over the whole declared set', () => {
    for (const s of DSR_REQUEST_TERMINAL) {
      expect(isDsrOpen(s)).toBe(!isDsrTerminal(s));
    }
    expect(DSR_REQUEST_TERMINAL).toHaveLength(4);
  });
});
