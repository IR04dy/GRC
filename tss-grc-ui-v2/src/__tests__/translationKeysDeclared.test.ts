import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve, sep, posix } from 'node:path';

/**
 * F2-340 — a `t('key')` whose key is never declared renders the RAW KEY to the user.
 *
 * `i18n.tsx`'s translator is `t = (key, fallback?) => dict[key] ?? fallback ?? key` — a missing
 * key with no fallback returns the key string itself, and the interface doc says so. So a
 * single-literal `t('loading')` for a key absent from BOTH dictionaries put the literal word
 * "loading" on screen (in both languages). Measured 2026-08-12: THREE such keys at 24 sites —
 * `loading` (20×: evidence-chain, risk-scenarios, fourth-party, DR plans, processor assessments,
 * supplier resilience, third-party cyber, contract clauses, data sharing, emerging risk, framework
 * controls, committees, TPRM overview), `loadingEllipsis` (3×: RCA, signatures), and
 * `toastValidationFailed` (1×: IncidentsPage — a red validation toast reading the raw key, its
 * intended English text stranded in a dead `?? 'Required fields missing'` JS fallback that `t()`
 * never reaches). Fixed by declaring the three keys in both dictionaries.
 *
 * This gate holds two invariants nothing else in the tree guarded:
 *   (A) every single-literal `t('key')` used in a shipped component is DECLARED — hard zero,
 *       empty exemption map (there is always a correct alternative: declare the key, or pass a
 *       2-arg default `t('key', 'Default')` which renders the default rather than the key).
 *   (B) `en.ts` and `ar.ts` declare IDENTICAL key sets (house rule 7) — a key present in one and
 *       missing from the other is an English fallback on the Arabic-primary UI, or a dead key.
 *       `BilingualInvariantRatchetTest` (F2-271) checks entity `*_en`/`*_ar` fields on the backend;
 *       the FRONTEND translation dictionaries were unguarded until now.
 *
 * The key extractor is STRING-AWARE (a char-scan that skips string/comment spans and captures
 * `<ident>:` at object depth 1). A naive `^\s+(\w+):` regex under-reads: en.ts packs several keys
 * per line (`critCritical: 'Critical', critHigh: 'High', ...`), so a line-first regex reported
 * `critHigh`/`critMedium`/`critLow` as undeclared when they are declared — F2-192's shape, found
 * mid-slice against my own detector. The self-test below drives both detectors over a synthetic
 * corpus in both directions, because a "collect violations, assert empty" leg is vacuous on a clean
 * tree and would read the same whether the detector works or has rotted (F2-208/F2-295).
 *
 * Scope: a single-literal `t('id')` only. Two-arg `t('id', 'default')` calls have a fallback and
 * cannot render a raw key (they are a separate, milder house-rule-7 question — the Arabic user sees
 * the English default — and are deliberately out of scope here). Dynamic/concatenated keys cannot
 * be resolved statically and are not flagged (F2-201). `\bt\(` never matches inside `set(` (word
 * boundary) and, measured over the whole tree, matched no non-i18n `t` — 0 false positives.
 */

const SRC = resolve(__dirname, '..');
const TR = join(SRC, 'translations');

/** F2-198 single left-to-right alternation; preserves newlines so line numbers survive. */
function stripComments(s: string): string {
  return s.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '));
}

/**
 * String-aware extraction of object keys `<ident>:` at object depth 1 (the flat dictionary).
 * Robust to multiple pairs per line and commas inside string values.
 */
function declaredKeys(source: string): Map<string, number[]> {
  const keys = new Map<string, number[]>();
  const n = source.length;
  let i = 0, line = 1, depth = 0;
  let instr: string | null = null, esc = false;
  const idColon = /^([A-Za-z_][A-Za-z0-9_]*)\s*:/;
  while (i < n) {
    const c = source[i];
    if (c === '\n') { line++; i++; continue; }
    if (instr) {
      if (esc) esc = false;
      else if (c === '\\') esc = true;
      else if (c === instr) instr = null;
      i++; continue;
    }
    if (c === "'" || c === '"' || c === '`') { instr = c; i++; continue; }
    if (c === '/' && source[i + 1] === '/') { const j = source.indexOf('\n', i); i = j < 0 ? n : j; continue; }
    if (c === '/' && source[i + 1] === '*') {
      const j = source.indexOf('*/', i + 2);
      const block = source.slice(i, j < 0 ? n : j + 2);
      line += (block.match(/\n/g) || []).length;
      i = j < 0 ? n : j + 2; continue;
    }
    if (c === '{') { depth++; i++; continue; }
    if (c === '}') { depth--; i++; continue; }
    if (depth === 1) {
      const m = idColon.exec(source.slice(i, i + 128));
      if (m) {
        const arr = keys.get(m[1]);
        if (arr) arr.push(line); else keys.set(m[1], [line]);
        i += m[0].length; continue;
      }
    }
    i++;
  }
  return keys;
}

const SINGLE = /\bt\(\s*(['"])([A-Za-z_][A-Za-z0-9_]*)\1\s*\)/g;

function scanUsedSingleLiteral(root: string, declared: Set<string>): Map<string, string[]> {
  const bugs = new Map<string, string[]>();
  const walk = (dir: string) => {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name);
      const st = statSync(full);
      if (st.isDirectory()) {
        if (name === 'translations' || name === 'node_modules') continue;
        walk(full);
      } else if (/\.(ts|tsx)$/.test(name) && !/\.(test|d)\.(ts|tsx)$/.test(name)) {
        const s = stripComments(readFileSync(full, 'utf8'));
        let m: RegExpExecArray | null;
        SINGLE.lastIndex = 0;
        while ((m = SINGLE.exec(s)) !== null) {
          const key = m[2];
          if (!declared.has(key)) {
            const rel = full.slice(SRC.length + 1).split(sep).join(posix.sep);
            const arr = bugs.get(key);
            if (arr) arr.push(rel); else bugs.set(key, [rel]);
          }
        }
      }
    }
  };
  walk(root);
  return bugs;
}

const EN = declaredKeys(readFileSync(join(TR, 'en.ts'), 'utf8'));
const AR = declaredKeys(readFileSync(join(TR, 'ar.ts'), 'utf8'));

describe('F2-340 — translation keys are declared and bilingual', () => {
  it('(A) every single-literal t(key) used in a component is declared — renders raw key otherwise', () => {
    const bugs = scanUsedSingleLiteral(SRC, new Set(EN.keys()));
    const msg = [...bugs.entries()]
      .map(([k, sites]) => `  t('${k}') @ ${sites.length} site(s), e.g. ${sites[0]}`)
      .join('\n');
    expect(bugs.size, `single-literal t('key') with an UNDECLARED key (renders the raw key to the user):\n${msg}`).toBe(0);
  });

  it('(B) en.ts and ar.ts declare identical key sets (house rule 7)', () => {
    const enKeys = new Set(EN.keys());
    const arKeys = new Set(AR.keys());
    const missingAr = [...enKeys].filter((k) => !arKeys.has(k)).sort();
    const missingEn = [...arKeys].filter((k) => !enKeys.has(k)).sort();
    expect(missingAr, `keys in en.ts MISSING from ar.ts (English fallback on Arabic-primary UI): ${missingAr.join(', ')}`).toEqual([]);
    expect(missingEn, `keys in ar.ts MISSING from en.ts (dead Arabic key): ${missingEn.join(', ')}`).toEqual([]);
  });

  it('(C) neither dictionary declares a duplicate key (a later dup silently shadows)', () => {
    const enDup = [...EN.entries()].filter(([, v]) => v.length > 1).map(([k, v]) => `en:${k}@${v}`);
    const arDup = [...AR.entries()].filter(([, v]) => v.length > 1).map(([k, v]) => `ar:${k}@${v}`);
    expect([...enDup, ...arDup], 'duplicate keys shadow silently').toEqual([]);
  });

  it('self-test — the detectors fire on synthetic violations (not vacuous)', () => {
    // multi-key line + string-with-colon value: the tokenizer must find BOTH keys and NOT the
    // pseudo-key inside the value.
    const synth = declaredKeys("export const x = {\n  a: 'x', b: 'a: not a key',\n  c: 'y',\n};");
    expect([...synth.keys()].sort()).toEqual(['a', 'b', 'c']);
    // used-scan flags an undeclared single-literal key and ignores a 2-arg call and a declared one.
    const declared = new Set(['known']);
    const probe = "function f(t){ return t('missingKey') + t('known') + t('withDefault','D'); }";
    const stripped = stripComments(probe);
    const found = new Set<string>();
    let m: RegExpExecArray | null; SINGLE.lastIndex = 0;
    while ((m = SINGLE.exec(stripped)) !== null) if (!declared.has(m[2])) found.add(m[2]);
    expect([...found].sort()).toEqual(['missingKey']); // NOT 'withDefault' (2-arg), NOT 'known'
    // parity detector direction check
    const l = new Set(['a', 'b']); const r = new Set(['a', 'c']);
    expect([...l].filter((k) => !r.has(k))).toEqual(['b']);
    expect([...r].filter((k) => !l.has(k))).toEqual(['c']);
  });
});
