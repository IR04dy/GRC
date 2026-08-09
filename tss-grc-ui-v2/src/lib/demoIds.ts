/**
 * F2-168 — fabricated records must not occupy the real id space.
 *
 * Every mock file numbers its records from 1, and so does every identity column. That collision
 * is not a bundle-hygiene concern ([[F2-15]]) and not only a hazard on the demo-fallback path
 * ([[F2-167]]) — it is the mechanism by which fabricated children attach themselves to **genuine
 * records on the success path**. Six detail pages fetch a real record from the server and then
 * decorate it out of a fabricated map keyed by that record's id, with no demo flag in the file at
 * all: `CONTROL_TESTS_BY_ID[control.id]` renders fabricated control-test results (tester, sample
 * count, PASS) on a real control; `FINDING_HISTORY_BY_ID[finding.id]` renders a fabricated audit
 * trail on a real finding; `VENDOR_ASSESSMENTS_BY_ID`, `DPIA_REVIEWS_BY_ID`, `RISK_HISTORY_BY_ID`
 * and the regulator-citation maps in `entityAnchors.ts` do the same. Measured in dev on
 * 2026-08-09: 5 real controls (ids 1, 3, 4, 9, 10 of 35), 5 real findings (31–34, 38 of 41),
 * 5 real vendors (1–5 of 28) and 6 real risks were being decorated this way.
 *
 * The whole class closes with one constant. Shifting the fabricated corpus above any plausible
 * identity value means:
 *   • `MAP[realId]` misses, so the page falls through to the honest empty state it already has;
 *   • `MAP[demoId]` still resolves, because keys and record ids shift by the same amount — the
 *     demo experience is preserved exactly;
 *   • a write raised from a fabricated row can only 404, which is what F2-167 left open.
 *
 * ⚠ **Why this is a wrapper and not 968 edited literals.** The corpus carries ~968 id-ish numeric
 * literals across 22 modules, including object keys (`APPETITE_BREACHES` is keyed by appetite id),
 * and a missed one is *silent* — no compile error, just a lookup that stops resolving. Rewriting
 * them textually puts the correctness of the slice in the hands of a regex. Wrapping each export
 * in a recursive shift puts it in the hands of one function with a unit test, and leaves the source
 * readable at its original small numbers.
 *
 * ⚠ **Only NAMED id fields move.** A bare number in the corpus is a measurement, a maturity level,
 * a sample count, or a week in a trend series — `KRI_TRENDS_BY_ID` is `Record<number, number[]>`,
 * where the keys are KRI ids and the values are readings. Shifting a value would corrupt data while
 * looking like it worked. Field names ending `Id` that hold *strings* (`nationalId: '1090455678'`,
 * `cveId`, `ruleId`) are likewise left alone, because the shift only applies to numbers.
 */

/** Above every identity value this platform can plausibly reach, and below `Integer.MAX_VALUE`. */
export const DEMO_ID_BASE = 900_000_000;

/** True for an id that came out of the fabricated corpus. */
export function isDemoId(v: unknown): v is number {
  return typeof v === 'number' && Number.isInteger(v) && v >= DEMO_ID_BASE;
}

function isIdKey(k: string): boolean {
  return k === 'id' || (k.length > 2 && k.endsWith('Id'));
}

function shiftable(v: number): boolean {
  // `< DEMO_ID_BASE` is what makes the shift idempotent, which matters more than it looks: two
  // exports can share the same record object by reference, and each would be wrapped separately.
  return Number.isInteger(v) && v > 0 && v < DEMO_ID_BASE;
}

function shiftInPlace(v: unknown, seen: Set<object>): void {
  if (v === null || typeof v !== 'object') return;
  if (seen.has(v)) return;
  seen.add(v);
  if (Array.isArray(v)) {
    for (const e of v) shiftInPlace(e, seen);
    return;
  }
  const o = v as Record<string, unknown>;
  for (const k of Object.keys(o)) {
    const val = o[k];
    if (typeof val === 'number') {
      if (isIdKey(k) && shiftable(val)) o[k] = val + DEMO_ID_BASE;
    } else {
      shiftInPlace(val, seen);
    }
  }
}

/** An array of fabricated records. Mutates and returns the same array. */
export function demoRows<T>(rows: T[]): T[] {
  shiftInPlace(rows, new Set());
  return rows;
}

/** A single fabricated record. */
export function demoRecord<T>(rec: T): T {
  shiftInPlace(rec, new Set());
  return rec;
}

/**
 * A fabricated map keyed by a record id (`*_BY_ID`, `KRI_TRENDS_BY_ID`, `APPETITE_BREACHES`…).
 * Shifts the keys as well as any ids inside the values, so `MAP[record.id]` keeps resolving for a
 * fabricated record and stops resolving for a real one.
 */
export function demoKeyed<T>(map: Record<number, T>): Record<number, T> {
  shiftInPlace(map, new Set());
  const out: Record<number, T> = {};
  for (const k of Object.keys(map)) {
    const n = Number(k);
    out[shiftable(n) ? n + DEMO_ID_BASE : n] = (map as Record<string, T>)[k];
  }
  return out;
}
