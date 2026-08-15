import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * F2-217 — a create payload may not carry a governance state or its authority stamp.
 *
 * The three `audit_lc` creates persisted the request entity, so whatever `toBackend*` put on the
 * wire was stored: an `AC_APPROVED` annual plan with an `acApprovedBy` of the caller's choosing,
 * an `APPROVED` IIA-2330 working paper with a named `reviewer`, a `CLOSED` CAPA with a backdated
 * `closedAt`. Probed live 2026-08-15: create 200, the equivalent guarded transition 409.
 *
 * The server refuses these now, so this file is not what makes the platform safe — it is what
 * stops the client sending keys it has no business setting, which is the difference between a
 * request that cannot be wrong and one that merely gets rejected.
 *
 * ⚠ The assertions are scoped to the SHAPE (`api.post<…>('<path>', <expr>)` and what `<expr>`
 * strips), not to an identifier, because F2-197 recorded a gate going red on a rename and
 * F2-205 recorded one satisfied by a token sitting in an import list.
 */

const SRC = readFileSync(resolve(__dirname, '..', 'lib', 'auditLifecycle.ts'), 'utf8');

/**
 * Strip comments in ONE left-to-right alternation. Two sequential passes delete 45% of a file
 * whose `//` prose happens to contain a literal `/*` (F2-198), and this file's own header
 * comments quote code — including the very `toBackendPlan(` shape asserted below.
 */
function stripComments(s: string): string {
  return s.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, '');
}

const CODE = stripComments(SRC);

/** Every `api.post<…>('path', arg)` in the file, as [path, argument-expression]. */
function posts(): Array<[string, string]> {
  const out: Array<[string, string]> = [];
  const re = /api\.post\s*<[^>]*>\s*\(\s*(['`])([^'`]+)\1\s*,/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(CODE)) !== null) {
    // brace/paren-balanced read of the second argument
    let i = re.lastIndex;
    let depth = 0;
    let arg = '';
    for (; i < CODE.length; i++) {
      const c = CODE[i];
      if (c === '(' || c === '{' || c === '[') depth++;
      if (c === ')' || c === '}' || c === ']') {
        if (depth === 0) break;
        depth--;
      }
      arg += c;
    }
    out.push([m[2], arg.trim()]);
  }
  return out;
}

const CREATE_CONTRACT: Record<string, string[]> = {
  // path fragment              -> keys the server owns and the client must not send
  'audit-lc/annual-plans':  ['status', 'acApprovedBy', 'acApprovedAt'],
  'audit-lc/working-papers': ['status', 'reviewer', 'reviewedAt'],
  'audit-lc/capas':          ['status', 'closedAt'],
};

describe('F2-217 — audit_lc create payloads carry no state and no authority stamp', () => {
  it('the parser finds the creates it is meant to check', () => {
    const paths = posts().map(([p]) => p);
    // Anti-vacuity: if the regex rots, every assertion below passes over an empty list.
    for (const frag of Object.keys(CREATE_CONTRACT)) {
      expect(
        paths.filter(p => p.includes(frag) && !p.includes('transition')).length,
        `no POST to ${frag} found — the scan has rotted, not the code`,
      ).toBeGreaterThan(0);
    }
  });

  it.each(Object.entries(CREATE_CONTRACT))(
    'every create POST to %s strips the keys the server owns',
    (frag, ownedKeys) => {
      const creates = posts().filter(([p]) => p.includes(frag) && !p.includes('transition'));
      for (const [path, arg] of creates) {
        if (/^\{/.test(arg)) {
          // A literal payload: it must simply not mention the owned keys, except a `status`
          // set to the register's initial state, which the server still accepts.
          for (const k of ownedKeys) {
            if (k === 'status') continue;
            expect(arg, `${path} sends ${k} in a literal create payload`).not.toMatch(
              new RegExp(`\\b${k}\\s*:`),
            );
          }
          continue;
        }
        expect(arg, `${path} POSTs a mapper result without stripping the server-owned keys`)
          .toMatch(/forCreate\s*\(/);
        for (const k of ownedKeys) {
          expect(arg, `${path} does not strip '${k}'`).toMatch(new RegExp(`'${k}'`));
        }
      }
    },
  );

  /**
   * ⚠ The first draft of this leg also asserted `to:` on every transition payload and FAILED on
   * correct code: `transitionFinding` posts `{ status, mgmtResponseEn, … }` while the plan/paper/
   * CAPA routes post `{ to }`. A gate that fails on correct code is worse than no gate — it trains
   * the next reader to edit the assertion rather than the code (F2-214) — so this accepts either
   * spelling of "the target state" and asserts only the claim it is actually making.
   */
  it('a transition POST is NOT stripped — it is the route allowed to move state', () => {
    const transitions = posts().filter(([p]) => p.includes('transition'));
    expect(transitions.length).toBeGreaterThan(0);
    for (const [path, arg] of transitions) {
      expect(arg, `${path} must name the target state`).toMatch(/(?:\bto\s*:|\bstatus\b)/);
      expect(arg, `${path} must not be run through the create stripper`).not.toMatch(/forCreate/);
    }
  });

  /**
   * The call-shape assertions above are satisfied by a `forCreate` that strips nothing, so the
   * helper's own body is pinned too. It stays module-private deliberately — exporting it purely
   * to unit-test it would put an otherwise-unused symbol on the module's surface (F2-207).
   * The behavioural guarantee is the server's, and the live probe measures it there.
   */
  it('forCreate actually removes the keys it is given', () => {
    const body = CODE.match(/function forCreate[\s\S]*?\n}/)?.[0] ?? '';
    expect(body, 'forCreate not found — the scan has rotted').not.toBe('');
    expect(body, 'forCreate must delete each server-owned key, not merely accept a list')
      .toMatch(/delete\s+out\[\s*k\s*\]/);
  });

  it('positive control: the pre-F2-217 shape fails this gate', () => {
    const preFix = "void api.post<BackendCapaTask>('/api/audit-lc/capas', toBackendCapa(c))";
    expect(preFix).not.toMatch(/forCreate\s*\(/);
  });
});
