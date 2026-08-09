import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { DEMO_ID_BASE } from '../lib/demoIds';
import { stripComments, walkSources, relPosix } from './support/sourceScan';

/**
 * F2-168 — a fabricated record must not be able to occupy a real record's id.
 *
 * <p>The defect this closes is not the demo-fallback path. It is a lookup that runs on the
 * **success** path: six detail pages fetch a genuine record from the server and then decorate it
 * out of a fabricated map keyed by that record's id, with no demo flag anywhere in the file.
 * `CONTROL_TESTS_BY_ID[control.id]` rendered fabricated control-test results — tester, sample
 * count, PASS — on a real control. `FINDING_HISTORY_BY_ID[finding.id]` rendered a fabricated audit
 * trail on a real finding. `entityAnchors.ts` attached fabricated regulator citations, and its own
 * header comment says it is <em>"keyed by COMPLIANCE_FINDINGS.id (mock ids 31-38)"</em> — the file
 * documented the collision and nothing connected the two halves. Measured in dev on 2026-08-09:
 * 5 of 35 real controls, 5 of 41 real findings, 5 of 28 real vendors and 6 real risks were being
 * decorated this way.
 *
 * <p>⚠ <b>Why the corpus is wrapped and not rewritten.</b> ~968 id-ish literals across 17 modules,
 * including object keys, and a missed one is *silent* — the lookup just stops resolving. A textual
 * rewrite puts the slice's correctness in a regex; `demoRows`/`demoKeyed` put it in one recursive
 * function with 16 unit tests of its own (`lib/demoIds.test.ts`), including the two ways it could
 * corrupt data while appearing to work: shifting a number that is not an id, and shifting twice.
 *
 * <p>The assertions below are deliberately layered, because the cheap one is not the property that
 * matters: <em>every id is large</em> would also be satisfied by a shift that broke every internal
 * reference. So this file also proves the demo graph still resolves (keys still point at records),
 * that non-id numbers did not move, and — in the language of the defect — that no plausible real id
 * reaches a fabricated map.
 */

// Every fabricated module, discovered rather than listed, so a new mock file is covered the day
// it lands. `import.meta.glob` is eager: the modules are real, evaluated imports, not source text.
const CORPUS: Record<string, Record<string, unknown>> = {
  ...import.meta.glob('../pages/**/mock*.ts', { eager: true }),
  ...import.meta.glob('../lib/mockWorkflows.ts', { eager: true }),
  ...import.meta.glob('../lib/entityAnchors.ts', { eager: true }),
  ...import.meta.glob('../lib/policyLifecycle.ts', { eager: true }),
};

const SRC = join(__dirname, '..');

/** Files the textual ratchet applies to — the same set, as paths. */
const CORPUS_FILES = walkSources(SRC)
  .filter(f => f.endsWith('.ts') && !f.includes('.test.'))
  .filter(f => {
    const r = relPosix(SRC, f);
    return /\/mock\w*\.ts$/.test(r) || r === 'lib/entityAnchors.ts' || r === 'lib/policyLifecycle.ts';
  });

function isIdKey(k: string): boolean {
  return k === 'id' || (k.length > 2 && k.endsWith('Id'));
}

/** Index just past the `[`/`{` opened at `start`, skipping brackets inside string literals. */
function balancedEnd(src: string, start: number): number {
  let depth = 0;
  let instr: string | null = null;
  for (let i = start; i < src.length; i++) {
    const c = src[i];
    if (instr) {
      if (c === '\\') i++;
      else if (c === instr) instr = null;
    } else if (c === '\'' || c === '"' || c === '`') instr = c;
    else if (c === '[' || c === '{') depth++;
    else if (c === ']' || c === '}') {
      depth--;
      if (depth === 0) return i + 1;
    }
  }
  return src.length;
}

interface Walked {
  /** every id-ish numeric field found, with the path that reaches it */
  ids: { where: string; value: number }[];
  /** every numeric key of an all-numeric-keyed object (a `*_BY_ID` map) */
  keys: { where: string; value: number }[];
  /** every plain number that is NOT an id — these must be untouched */
  plain: number[];
  exports: number;
  maps: { where: string; map: Record<string, unknown> }[];
}

function walkValue(v: unknown, where: string, w: Walked, seen: Set<object>): void {
  if (v === null || typeof v !== 'object') return;
  if (seen.has(v)) return;
  seen.add(v);
  if (Array.isArray(v)) {
    v.forEach((e, i) => {
      if (typeof e === 'number') w.plain.push(e);
      else walkValue(e, `${where}[${i}]`, w, seen);
    });
    return;
  }
  const o = v as Record<string, unknown>;
  const own = Object.keys(o);
  const allNumeric = own.length > 0 && own.every(k => /^\d+$/.test(k));
  if (allNumeric) {
    w.maps.push({ where, map: o });
    for (const k of own) w.keys.push({ where: `${where}[${k}]`, value: Number(k) });
  }
  for (const k of own) {
    const val = o[k];
    if (typeof val === 'number') {
      if (isIdKey(k)) w.ids.push({ where: `${where}.${k}`, value: val });
      else w.plain.push(val);
    } else {
      walkValue(val, `${where}.${k}`, w, seen);
    }
  }
}

function walkCorpus(): Walked {
  const w: Walked = { ids: [], keys: [], plain: [], exports: 0, maps: [] };
  const seen = new Set<object>();
  for (const [path, mod] of Object.entries(CORPUS)) {
    for (const [name, value] of Object.entries(mod)) {
      if (typeof value === 'function' || value === undefined) continue;
      w.exports += 1;
      walkValue(value, `${path.replace('../', '')}#${name}`, w, seen);
    }
  }
  return w;
}

const W = walkCorpus();

describe('F2-168 — the fabricated corpus sits outside the real id space', () => {
  it('every id-ish field in every fabricated record is above DEMO_ID_BASE', () => {
    const low = W.ids.filter(h => h.value !== 0 && h.value < DEMO_ID_BASE);
    expect(low.map(h => `${h.where} = ${h.value}`)).toEqual([]);
  });

  it('every key of every fabricated *_BY_ID map is above DEMO_ID_BASE', () => {
    const low = W.keys.filter(h => h.value < DEMO_ID_BASE);
    expect(low.map(h => `${h.where}`)).toEqual([]);
  });

  it('no plausible real id reaches a fabricated map — the defect, in its own language', () => {
    // A real control was 1..35, a real finding 1..41, a real vendor 1..28, a real risk 1..152.
    // Sweep the whole band a tenant can plausibly occupy rather than the ids that collided in dev.
    const reachable: string[] = [];
    for (const { where, map } of W.maps) {
      for (let realId = 1; realId <= 2000; realId++) {
        if (map[realId] !== undefined) {
          reachable.push(`${where}[${realId}]`);
          break;
        }
      }
    }
    expect(reachable).toEqual([]);
  });
});

describe('F2-168 — the shift preserved the demo graph', () => {
  it('every map key still resolves to a fabricated record id', () => {
    // Measured true BEFORE the shift (0 unresolved keys across 11 maps), so it is a real
    // invariant and not a property the change invented. This is what proves the shift was uniform:
    // "all ids are large" would also hold if every internal reference had been broken.
    const known = new Set(W.ids.map(h => h.value));
    const orphaned = W.keys.filter(h => !known.has(h.value));
    expect(orphaned.map(h => h.where)).toEqual([]);
  });

  it('a fabricated parent can still reach its fabricated children', () => {
    // The concrete F2-168 example, both directions in one assertion.
    const mock = CORPUS['../pages/compliance/mockCompliance.ts'] as {
      COMPLIANCE_CONTROLS: { id: number }[];
      CONTROL_TESTS_BY_ID: Record<number, unknown[]>;
    };
    const withTests = mock.COMPLIANCE_CONTROLS.filter(c => mock.CONTROL_TESTS_BY_ID[c.id]);
    expect(withTests.length).toBeGreaterThan(0);
    // …and the real control whose id used to collide cannot.
    expect(mock.CONTROL_TESTS_BY_ID[1]).toBeUndefined();
    expect(mock.CONTROL_TESTS_BY_ID[9]).toBeUndefined();
  });

  it('non-id numbers did not move — a reading is not an id', () => {
    // The mistake this pins: "add DEMO_ID_BASE to every number in the corpus". KRI_TRENDS_BY_ID is
    // Record<number, number[]> where the keys are KRI ids and the values are weekly readings; a
    // shifted reading renders a chart that is wrong without looking broken.
    const risks = CORPUS['../pages/risk/mockRisks.ts'] as { KRI_TRENDS_BY_ID: Record<number, number[]> };
    const series = Object.values(risks.KRI_TRENDS_BY_ID);
    expect(series.length).toBeGreaterThan(0);
    expect(series.flat().every(v => v < 100_000)).toBe(true);
    // and across the whole corpus: plain numbers are scores, counts, years, money — never id-sized
    expect(W.plain.filter(n => n >= DEMO_ID_BASE)).toEqual([]);
  });
});

describe('F2-168 — the ratchet', () => {
  it('every fabricated export carrying an id is wrapped in the shift', () => {
    // A hard zero, not a ceiling: an unwrapped export is a fabricated record back in the real id
    // space, and there is no reviewed reason to have one.
    //
    // ⚠ The body has to be brace-matched, not sampled. The first draft read a fixed 6,000 chars
    // after each export and accused six exports that hold no ids at all — `POLICY_STAGES` is a
    // string array whose "id literal" belonged to the *next* export in the file. Fifth over-wide
    // scan in five slices, and the only reason it was caught is that the failure list was short
    // enough to read: a wrong ratchet that fires is fixable, a wrong ratchet that passes is not.
    const unwrapped: string[] = [];
    for (const file of CORPUS_FILES) {
      const src = stripComments(readFileSync(file, 'utf8'));
      const rel = relPosix(SRC, file);
      for (const m of src.matchAll(/^export const (\w+)[^\n]*?=\s*(demoRows\(|demoKeyed\(|demoRecord\(|[[{])/gm)) {
        if (m[2].startsWith('demo')) continue;
        const open = (m.index ?? 0) + m[0].length - 1;
        const body = src.slice(open, balancedEnd(src, open));
        if (/\b\w*[Ii]d:\s*\d+/.test(body) || /^\s{2}\d+:/m.test(body)) unwrapped.push(`${rel}#${m[1]}`);
      }
    }
    expect(unwrapped).toEqual([]);
  });

  it('the six pages that decorate a real record keep their honest empty branch', () => {
    // These lookups now MISS for a real record, which is the fix — so the fallback and the empty
    // state are load-bearing. Deleting a `?? []` here turns a decorated record into a crash.
    const PAGES: [string, string[]][] = [
      ['pages/detail/ControlDetailPage.tsx', ['CONTROL_SUBCONTROLS_BY_ID', 'CONTROL_EVIDENCE_BY_ID', 'CONTROL_TESTS_BY_ID', 'CONTROL_ANCHORS_BY_ID']],
      ['pages/detail/FindingDetailPage.tsx', ['FINDING_EVIDENCE_BY_ID', 'FINDING_HISTORY_BY_ID', 'FINDING_ANCHORS_BY_ID']],
      ['pages/detail/DpiaDetailPage.tsx',    ['DPIA_QUESTIONS_BY_ID', 'DPIA_MITIGATIONS_BY_ID', 'DPIA_REVIEWS_BY_ID']],
      ['pages/detail/VendorDetailPage.tsx',  ['VENDOR_ASSESSMENTS_BY_ID', 'VENDOR_CONTRACTS_BY_ID']],
      ['pages/detail/PolicyDetailPage.tsx',  ['POLICY_ANCHORS_BY_ID']],
      ['pages/risk/RiskDetailPage.tsx',      ['RISK_KRIS_BY_ID', 'RISK_HISTORY_BY_ID', 'RISK_ANCHORS_BY_ID']],
      ['pages/risk/RiskRegisterPage.tsx',    ['KRI_TRENDS_BY_ID']],
    ];
    const naked: string[] = [];
    for (const [rel, maps] of PAGES) {
      const src = readFileSync(join(SRC, rel), 'utf8');
      for (const map of maps) {
        const uses = [...src.matchAll(new RegExp(`${map}\\[[^\\]]{1,40}\\]`, 'g'))];
        expect(uses.length, `${rel} no longer reads ${map}`).toBeGreaterThan(0);
        for (const u of uses) {
          const after = src.slice((u.index ?? 0) + u[0].length, (u.index ?? 0) + u[0].length + 4);
          if (!after.trimStart().startsWith('??')) naked.push(`${rel}: ${u[0]}`);
        }
      }
    }
    expect(naked).toEqual([]);
  });

  it('is not vacuous', () => {
    expect(Object.keys(CORPUS).length).toBeGreaterThanOrEqual(17);
    expect(CORPUS_FILES.length).toBeGreaterThanOrEqual(17);
    expect(W.exports).toBeGreaterThanOrEqual(80);
    expect(W.ids.length).toBeGreaterThanOrEqual(500);
    expect(W.maps.length).toBeGreaterThanOrEqual(30);
    expect(W.plain.length).toBeGreaterThanOrEqual(500);
  });
});
