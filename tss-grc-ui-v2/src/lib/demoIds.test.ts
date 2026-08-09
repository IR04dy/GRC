import { describe, it, expect } from 'vitest';
import { DEMO_ID_BASE, isDemoId, demoRows, demoRecord, demoKeyed } from './demoIds';

/**
 * F2-168 — the whole slice rests on this function, so it is tested harder than the callers.
 *
 * The reason the corpus is wrapped rather than rewritten is that a missed literal is silent: a
 * lookup simply stops resolving, with no compile error and no failing render. That trade only pays
 * if the shift itself is exhaustively pinned — in particular the two ways it could corrupt data
 * while appearing to work: shifting a number that is not an id, and shifting the same record twice.
 */

const B = DEMO_ID_BASE;

describe('demoIds — what moves', () => {
  it('shifts `id` and every camelCase *Id field', () => {
    const [r] = demoRows([{ id: 3, vendorId: 7, linkedControlId: 9, frameworkId: 1 }]);
    expect(r).toEqual({ id: B + 3, vendorId: B + 7, linkedControlId: B + 9, frameworkId: B + 1 });
  });

  it('shifts ids nested in arrays and objects at any depth', () => {
    const rows = demoRows([{ id: 1, meta: { ownerId: 2, tags: [{ id: 3 }, { id: 4, riskId: 5 }] } }]);
    expect(rows[0].meta.ownerId).toBe(B + 2);
    expect(rows[0].meta.tags.map(t => t.id)).toEqual([B + 3, B + 4]);
    expect(rows[0].meta.tags[1].riskId).toBe(B + 5);
  });

  it('shifts map keys AND the ids inside the values', () => {
    const m = demoKeyed<{ id: number }[]>({ 1: [{ id: 11 }], 9: [{ id: 12 }] });
    expect(Object.keys(m).map(Number)).toEqual([B + 1, B + 9]);
    expect(m[B + 1][0].id).toBe(B + 11);
  });

  it('keeps a keyed lookup resolvable from a shifted record — the point of the exercise', () => {
    const rows = demoRows([{ id: 4 }]);
    const children = demoKeyed<string[]>({ 4: ['sub-control A'] });
    expect(children[rows[0].id]).toEqual(['sub-control A']);
    // …and unreachable from the REAL record that used to collide with it.
    expect(children[4]).toBeUndefined();
  });

  it('handles a single record', () => {
    expect(demoRecord({ id: 2, name: 'x' })).toEqual({ id: B + 2, name: 'x' });
  });
});

describe('demoIds — what must NOT move', () => {
  it('leaves bare numbers alone: a measurement is not an id', () => {
    // The failure this pins: KRI_TRENDS_BY_ID is Record<number, number[]> — keys are KRI ids,
    // values are twelve weekly readings. Shifting a reading corrupts the chart silently.
    const trends = demoKeyed<number[]>({ 1001: [12, 14, 9, 31] });
    expect(trends[B + 1001]).toEqual([12, 14, 9, 31]);
  });

  it('leaves non-id numeric fields alone', () => {
    const [r] = demoRows([{ id: 1, samples: 25, findings: 0, maturityLevel: 3, year: 2025, amberMax: 8 }]);
    expect(r).toEqual({ id: B + 1, samples: 25, findings: 0, maturityLevel: 3, year: 2025, amberMax: 8 });
  });

  it('leaves string-valued *Id fields alone — nationalId is a person, not a row', () => {
    const [r] = demoRows([{ id: 1, nationalId: '1090455678', cveId: 'CVE-2024-1234', ruleId: 'R-7' }]);
    expect(r.nationalId).toBe('1090455678');
    expect(r.cveId).toBe('CVE-2024-1234');
    expect(r.ruleId).toBe('R-7');
  });

  it('leaves a field merely CONTAINING "id" alone', () => {
    const [r] = demoRows([{ id: 1, idx: 4, width: 3, valid: 1, identified: 2 }]);
    expect(r).toEqual({ id: B + 1, idx: 4, width: 3, valid: 1, identified: 2 });
  });

  it('leaves zero and negative sentinels alone', () => {
    const [r] = demoRows([{ id: 0, ownerId: -1 }]);
    expect(r).toEqual({ id: 0, ownerId: -1 });
  });

  it('leaves null and undefined alone', () => {
    const [r] = demoRows([{ id: 1, ownerId: null, riskId: undefined }]);
    expect(r.ownerId).toBeNull();
    expect(r.riskId).toBeUndefined();
  });
});

describe('demoIds — idempotence', () => {
  it('shifting twice is the same as shifting once', () => {
    const once = demoRows([{ id: 3, vendorId: 7 }]);
    const twice = demoRows(demoRows([{ id: 3, vendorId: 7 }]));
    expect(twice).toEqual(once);
  });

  it('survives two exports sharing one record by reference', () => {
    // This is not hypothetical: a corpus file can export an array and a keyed map that both
    // reference the same objects, and each export is wrapped separately.
    const shared = { id: 5, riskId: 6 };
    demoRows([shared]);
    demoKeyed({ 1: [shared] });
    expect(shared).toEqual({ id: B + 5, riskId: B + 6 });
  });

  it('does not loop on a cyclic reference', () => {
    const a: { id: number; self?: unknown } = { id: 1 };
    a.self = a;
    expect(() => demoRecord(a)).not.toThrow();
    expect(a.id).toBe(B + 1);
  });
});

describe('isDemoId', () => {
  it('separates fabricated ids from real ones', () => {
    expect(isDemoId(B + 1)).toBe(true);
    expect(isDemoId(B)).toBe(true);
    expect(isDemoId(35)).toBe(false);
    expect(isDemoId(0)).toBe(false);
    expect(isDemoId('900000001')).toBe(false);
    expect(isDemoId(undefined)).toBe(false);
  });

  it('sits above any identity value this platform can reach, and below Integer.MAX_VALUE', () => {
    // A path variable is a Java int on some endpoints: an id above 2^31-1 would 400 rather than
    // 404, which is a worse signal. The band has to be implausible, not impossible.
    expect(DEMO_ID_BASE).toBeGreaterThan(10_000_000);
    expect(DEMO_ID_BASE + 100_000).toBeLessThan(2_147_483_647);
  });
});
