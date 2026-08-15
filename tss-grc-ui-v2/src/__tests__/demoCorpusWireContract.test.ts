import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

/**
 * F2-211 — a demo fallback corpus is a SECOND wire contract, and nothing checked it.
 *
 * `demoRows<T>(rows: T[]): T[]` is generic, so in
 *
 *     export const BREACHES: PrivacyBreachDto[] = demoRows([ … ])
 *
 * TypeScript infers `T` from the literal and then checks `T[]` for ASSIGNABILITY to
 * `PrivacyBreachDto[]` — where extra properties are legal. The excess-property check
 * that would fire on a direct annotation never runs. So the type that appears to bind
 * the corpus enforces nothing, and a corpus can populate keys its DTO does not even
 * declare. That is not theoretical: F2-210 renamed `retentionPeriod` to
 * `retentionPeriodMonths` on `PrivacyRopaDto`, and the ROPA corpus kept setting the
 * retired key through a full clean typecheck.
 *
 * ⚠ STATE THE LIMIT OF THIS GATE OUT LOUD. It compares the corpus against the DTO, so
 * it catches a corpus that has DRIFTED from its type. It cannot catch a corpus and a
 * DTO that are wrong TOGETHER — which is the shape F2-211 actually found: the breach
 * corpus set `severity` / `occurredAt` / `reportedAt` / `affectedRecords` and the DTO
 * declared all four, while `privacy.data_breaches` has a column for none of them. Only
 * the entity adjudicates that, which is what `PrivacyWireContractTest` does by
 * reflection on the backend side.
 *
 * The two compose into the contract that matters — corpus ⊆ DTO ⊆ entity — and NEITHER
 * IS SUFFICIENT ALONE. This one is why a fixed corpus cannot silently drift back; the
 * backend one is why the DTO cannot claim a column that does not exist.
 *
 * Why it matters that the fallback was wrong rather than merely fake: the corpus is
 * what `useApiList` renders when the server is unreachable, so those four columns and
 * the PDPL Article 20 countdown rendered HERE and only here. F2-158's lesson was "when
 * a page has a client-side fallback, hit its endpoint directly before believing the
 * page"; this is the mechanism underneath it. And F2-194's: the countdown was anchored
 * on `Date.now()` at module load, so the fabrication could never look stale.
 */

const SRC = join(__dirname, '..');

/**
 * ⚠ ONE alternation — never two passes (F2-68(c), F2-198). Stripping blocks first in a
 * SEPARATE pass lets a `//` comment whose prose contains an unclosed `/*` open a block
 * that runs to the next closing delimiter and swallows real declarations. A corrupted
 * source yields a SMALLER population, which reads exactly like "those corpora are fine".
 * Load-bearing here: the corpora this slice fixed carry headers that NAME the keys they
 * used to set.
 *
 * ⚠⚠ F2-211 measured a correction worth keeping: within a single alternation the BRANCH
 * ORDER is a no-op. `//` and `/*` can never match at the same position, so leftmost-match
 * consumes the line comment first whichever branch is written first — verified in node,
 * both orderings give the same answer, and only the two-pass form loses a declaration.
 * The rule is "one alternation", not "line comments first"; a mutation that merely swaps
 * the branches is an EQUIVALENT MUTANT and its survival is not a hole.
 */
const STRIP = /(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g;
const stripComments = (s: string) => s.replace(STRIP, '');

function walk(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, out);
    else if (p.endsWith('.ts') || p.endsWith('.tsx')) out.push(p);
  }
  return out;
}

/** Exported interfaces of lib/api.ts and the field names each declares. */
function apiInterfaces(): Map<string, Set<string>> {
  const src = stripComments(readFileSync(join(SRC, 'lib', 'api.ts'), 'utf8'));
  const out = new Map<string, Set<string>>();
  for (const m of src.matchAll(/export interface (\w+)\s*\{([^}]*)\}/g)) {
    const fields = new Set<string>();
    for (const f of m[2].matchAll(/(?:^|[;\n])\s*(\w+)\s*\??\s*:/g)) fields.add(f[1]);
    out.set(m[1], fields);
  }
  return out;
}

interface Corpus {
  file: string; name: string; dto: string; keys: Set<string>;
}

/**
 * The population is DERIVED, not recalled: every `export const X: Dto[] = demoRows([…])`
 * in the tree. F2-204's rule — a registry is only as honest as the rule that decided its
 * membership, and that rule is usually implicit. Here membership is a measurement.
 */
function corpora(): Corpus[] {
  const found: Corpus[] = [];
  for (const file of walk(SRC)) {
    const raw = readFileSync(file, 'utf8');
    if (!raw.includes('demoRows') && !raw.includes('demoKeyed')) continue;
    const src = stripComments(raw);
    const re = /export const (\w+)\s*:\s*(\w+)\[\]\s*=\s*demo(?:Rows|Keyed)\(\[([\s\S]*?)\n\]\)/g;
    for (const m of src.matchAll(re)) {
      const keys = new Set<string>();
      for (const k of m[3].matchAll(/[{,]\s*(\w+)\s*:/g)) keys.add(k[1]);
      found.push({
        file: relative(SRC, file).split(sep).join('/'),
        name: m[1], dto: m[2], keys,
      });
    }
  }
  return found;
}

describe('F2-211 — a demo fallback corpus may not set a key its DTO does not declare', () => {
  const IFACES = apiInterfaces();
  const CORPORA = corpora();

  it('finds the corpora at all (anti-vacuity — silence must not read as success)', () => {
    // A gate keyed to a literal shape reports a SMALLER population the moment a
    // declaration changes form, and reports zero offences either way (F2-209).
    expect(CORPORA.length).toBeGreaterThanOrEqual(40);
    expect(IFACES.size).toBeGreaterThanOrEqual(100);
    expect(CORPORA.some(c => c.name === 'BREACHES' && c.dto === 'PrivacyBreachDto')).toBe(true);
    expect(CORPORA.some(c => c.name === 'ROPA' && c.dto === 'PrivacyRopaDto')).toBe(true);
  });

  it('no corpus sets a key its declared DTO does not have', () => {
    const offences: string[] = [];
    for (const c of CORPORA) {
      const dto = IFACES.get(c.dto);
      if (!dto) continue;   // annotated type is not an interface of lib/api.ts
      for (const k of c.keys) if (!dto.has(k)) offences.push(`${c.file} ${c.name}: ${c.dto}.${k}`);
    }
    // Hard zero, no exemption map: measured 0 across 49 resolved corpora.
    expect(offences, [
      'A demo fallback corpus sets a key its DTO does not declare. The `: Dto[]`',
      'annotation does NOT catch this — demoRows<T>(rows: T[]) infers T from the',
      'literal, so only assignability is checked and excess properties are legal.',
      'A stale key here renders in fallback mode and as an em-dash against the real',
      'server, which is the failure this gate exists to prevent recurring.',
    ].join(' ')).toEqual([]);
  });

  it('the breach corpus carries only fields the schema can supply', () => {
    const breaches = CORPORA.find(c => c.name === 'BREACHES');
    expect(breaches).toBeDefined();
    // Retired — privacy.data_breaches has no column for any of these four, so the
    // corpus was the only thing that ever made the register's Severity, Records,
    // Occurred and 72-hour clock columns render.
    for (const dead of ['severity', 'occurredAt', 'reportedAt', 'affectedRecords']) {
      expect(breaches!.keys.has(dead), `BREACHES must not set \`${dead}\`: no column exists, `
        + 'so a real row can never carry it and the corpus would once again be the only '
        + 'place the column renders').toBe(false);
    }
    // …and it must supply what the schema DOES keep, or the fallback stops
    // exercising the clock at all and the gate has traded one blind spot for another.
    for (const live of ['discoveredAt', 'dataSubjectsAffectedCount',
                        'pdpcNotificationRequired', 'sdaiaNotificationStatus']) {
      expect(breaches!.keys.has(live), `BREACHES must set \`${live}\``).toBe(true);
    }
  });

  /**
   * ⚠ A corpus assertion passes both when the tree is clean and when the parser has
   * rotted. Feed it the pre-fix shapes and require them to be caught. F2-195 shipped a
   * detector whose corpus legs were green for the wrong reason; the fix is a synthetic
   * input that MUST fail.
   */
  it('SELF-TEST: the parser catches the exact pre-fix corpora', () => {
    const dto = new Set(['id', 'titleEn', 'breachType', 'status',
                         'dataSubjectsAffectedCount', 'discoveredAt']);
    const preFix = `
      export const BREACHES: PrivacyBreachDto[] = demoRows([
        { id: 10, titleEn: 'x', breachType: 'CONFIDENTIALITY', severity: 'HIGH',
          status: 'INVESTIGATING', occurredAt: _a, reportedAt: _b, affectedRecords: 47_220 },
      ])
      `;
    const m = /export const (\w+)\s*:\s*(\w+)\[\]\s*=\s*demo(?:Rows|Keyed)\(\[([\s\S]*?)\n\s*\]\)/
      .exec(stripComments(preFix));
    expect(m, 'the corpus pattern must match at all, or every assertion above is vacuous').not.toBeNull();
    const keys = new Set<string>();
    for (const k of m![3].matchAll(/[{,]\s*(\w+)\s*:/g)) keys.add(k[1]);
    expect(keys.has('severity')).toBe(true);           // the parser really sees them
    expect([...keys].filter(k => !dto.has(k)).sort())
      .toEqual(['affectedRecords', 'occurredAt', 'reportedAt', 'severity']);
  });

  /**
   * The stripper is load-bearing (the headers name the retired keys), so prove it
   * both ways rather than trusting the ordering. F2-209 first checked this with a
   * surviving-CHARACTER ratio and it failed on a correct tree, because a file
   * explaining a retirement is legitimately mostly comment. Count declarations
   * anchored to line starts — a comment can never satisfy that.
   */
  it('SELF-TEST: comment stripping keeps every anchored declaration', () => {
    const anchored = (s: string) => (s.match(/^\s*export\s/gm) ?? []).length;
    for (const f of ['pages/privacy/mockPrivacy.ts', 'lib/api.ts', 'lib/pdplBreach.ts']) {
      const raw = readFileSync(join(SRC, f), 'utf8');
      expect(anchored(stripComments(raw)), `${f}: stripping lost an anchored declaration`)
        .toBe(anchored(raw));
    }
    /**
     * ⚠⚠ THE TRAP MUST ACTUALLY SPRING. The first version of this control read
     *   "// see the /* old *​/ shape"
     * — where the inner block CLOSES on the same line. Reversed to block-first the
     * stripper still produced the right answer there, so the mutation that reversed
     * the alternation SURVIVED and the control proved nothing. F2-197 recorded
     * exactly this: a negative control that does not break the thing reads as a hole
     * in the instrument when it is really a hole in the control.
     *
     * F2-198's real failure needs an UNCLOSED `/*` inside a `//` line, so block-first
     * runs from there to the next `*​/` anywhere below and swallows the declarations
     * in between — which is how a census lost 45% of `App.tsx` and reported 24
     * server-less pages instead of 74.
     */
    const trap = [
      '// the old shape opened a /* here and never closed it on this line',
      'export const KEEP = 1;',
      '/* an ordinary block comment */',
      'export const ALSO = 2;',
    ].join('\n');
    expect(anchored(stripComments(trap)),
      'the stripper lost a declaration between an unclosed `/*` inside a line comment and '
      + 'the next closing delimiter — a corrupted source yields a SMALLER population, which '
      + 'reads exactly like "these are all fine"').toBe(2);
    expect(stripComments(trap)).not.toContain('never closed it');
    expect(stripComments(trap)).toContain('KEEP');

    // …and the thing that would actually break it, asserted as the negative control:
    // TWO PASSES rather than one alternation. This is the shape F2-198 measured losing
    // 45% of App.tsx. Swapping the branches inside one alternation would NOT break it
    // (verified), so writing that as the control would prove nothing.
    const twoPass = (s: string) =>
      s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');
    expect(anchored(twoPass(trap)),
      'the two-pass form must still be demonstrably broken — if this ever equals 2, the trap '
      + 'has stopped springing and the assertion above proves nothing').toBe(1);
  });
});
