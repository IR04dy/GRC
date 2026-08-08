import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * F2-164 — **the risk screens must key on the statuses the server can actually emit.**
 *
 * `CK_risks_status` on `risk.risks` permits exactly seven values. `RiskDetailPage`
 * gated three lifecycle buttons and its whole status-badge table on
 * `UNDER_ASSESSMENT` and `TREATMENT_IN_PROGRESS`, which are not among them — so
 * **Approve never rendered at all, and Close never rendered for a `MITIGATING` risk**,
 * which is precisely the population the mandatory-treatment close gate governs.
 * `RiskRegisterPage` has always used the real vocabulary, so the same risk offered
 * different actions depending on which screen you opened it from.
 *
 * <h3>⚙ Why this is in the F2-164 slice and not filed separately</h3>
 * The slice tightened what the close gate accepts. Tightening a gate is worthless if
 * the button that trips it cannot render — that is F2-163's lesson arriving one layer
 * down: **an unreachable control reads as an absent feature, and a control keyed on an
 * impossible status is unreachable in a way no test, type or lint can see.** `status`
 * is `string` on the wire, so `=== 'UNDER_ASSESSMENT'` type-checks perfectly and is
 * simply never true.
 *
 * <h3>⚠ What this test is NOT</h3>
 * It is not a claim that the vocabulary below is correct — it is a claim that the FE
 * and the CHECK constraint agree. If a migration adds a status, this fails and the
 * list here is what has to be updated, deliberately, after checking each screen.
 */

/**
 * From `CK_risks_status` (read from `sys.check_constraints` in dev, 2026-08-08):
 *   ([status]='TRANSFERRED' OR 'ARCHIVED' OR 'CLOSED' OR 'ACCEPTED'
 *    OR 'MITIGATING' OR 'UNDER_REVIEW' OR 'OPEN')
 */
const DB_STATUSES = [
  'OPEN', 'UNDER_REVIEW', 'MITIGATING', 'ACCEPTED', 'TRANSFERRED', 'CLOSED', 'ARCHIVED',
] as const;

/** Statuses the FE used to believe in and the database has never permitted. */
const GHOST_STATUSES = ['UNDER_ASSESSMENT', 'TREATMENT_IN_PROGRESS'] as const;

const FILES = [
  'src/pages/risk/RiskDetailPage.tsx',
  'src/pages/risk/RiskRegisterPage.tsx',
] as const;

function read(rel: string): string {
  return readFileSync(resolve(process.cwd(), rel), 'utf8');
}

/**
 * The identifiers that hold a **Risk** on these two pages: `risk` (detail) and `r`
 * (register rows). Nothing else.
 *
 * <p>⚠⚠ **This list is the whole test, and getting it wrong invents defects.** The
 * first version of this scan matched `\w*\.status` — any object — and reported eight
 * offenders. Six were other vocabularies living on the same page: `tr.status`
 * (RiskTreatment: PLANNED/IN_PROGRESS/IMPLEMENTED/CANCELLED), `b.status` (a KRI
 * breach: RESOLVED), `s.status` (an appetite statement: APPROVED/RETIRED). Only
 * `r.status === 'TREATED'` was real. **A screen carries several status vocabularies
 * and they are indistinguishable by shape** — which is the same failure as F2-163's
 * `rowHref`, inverted: there the pattern was too narrow and hid five real routes;
 * here it was too wide and manufactured six. Both times the fix was to open every
 * hit before believing the count.
 */
const RISK_IDENTIFIERS = ['risk', 'r'] as const;

/** `risk.status === 'X'` / `r.status !== 'X'`, and only for those identifiers. */
function comparedStatuses(src: string): string[] {
  const out = new Set<string>();
  const re = new RegExp(
    String.raw`\b(?:${RISK_IDENTIFIERS.join('|')})\.status\s*[!=]==\s*'([A-Z_]+)'`, 'g');
  let m: RegExpExecArray | null;
  while ((m = re.exec(src)) !== null) out.add(m[1]);
  return [...out].sort();
}

describe('F2-164 — risk lifecycle status vocabulary', () => {
  it('no risk screen compares status against a value the CHECK constraint forbids', () => {
    const offenders: string[] = [];
    for (const f of FILES) {
      for (const s of comparedStatuses(read(f))) {
        if (!(DB_STATUSES as readonly string[]).includes(s)) offenders.push(`${f} → ${s}`);
      }
    }
    expect(offenders,
      'These comparisons can never be true: CK_risks_status does not permit the value, so '
      + 'the guarded button never renders. A dead condition is invisible — `status` is a '
      + 'string, so tsc is happy, and the screen simply shows one fewer action than it '
      + 'should. Offenders: ' + JSON.stringify(offenders)).toEqual([]);
  });

  it('the ghost statuses are gone from the risk screens entirely, badge tables included', () => {
    // The render conditions were the bug, but the badge table carried the same two
    // keys — so an UNDER_REVIEW risk fell through to `neutral`. Fixing conditions and
    // leaving the lookup would have left the screen quietly wrong in a different way.
    const offenders: string[] = [];
    for (const f of FILES) {
      const src = read(f);
      for (const g of GHOST_STATUSES) {
        // The word may legitimately appear inside a comment explaining the fix.
        const codeOnly = src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
        if (codeOnly.includes(g)) offenders.push(`${f} → ${g}`);
      }
    }
    expect(offenders, 'Offenders: ' + JSON.stringify(offenders)).toEqual([]);
  });

  it('both status-tone tables are keyed exactly on the permitted statuses', () => {
    // ⚠ The render conditions were only half of it. `STATUS_TONE` (detail) and
    // `STATUS_TONES` (register) are `Record<string, BadgeTone>` with a `?? 'neutral'`
    // fallback, so a wrong key is not a crash and not a type error — it is a grey
    // badge. Between them they held five invented statuses (UNDER_ASSESSMENT,
    // TREATMENT_IN_PROGRESS, IN_PROGRESS, MITIGATE, TREATED) and missed real ones, so
    // UNDER_REVIEW — the largest bucket in dev, 30 of 71 rows — rendered neutral on
    // both screens. 🔑 A lookup table with a fallback is where a vocabulary error goes
    // to hide: every other shape of this bug fails loudly.
    const tables: Array<[string, string]> = [
      ['src/pages/risk/RiskDetailPage.tsx', 'STATUS_TONE'],
      ['src/pages/risk/RiskRegisterPage.tsx', 'STATUS_TONES'],
    ];
    for (const [file, name] of tables) {
      const src = read(file);
      const at = src.indexOf(`const ${name}: Record<string, BadgeTone> = {`);
      expect(at, `${name} not found in ${file} — renamed or removed, so this test is `
        + 'measuring nothing').toBeGreaterThan(0);
      // ⚠ Slice from after the `{`, and do NOT anchor keys to line start: the detail
      // page packs several keys per line, and an `^\s*` pattern found two of seven —
      // which reported five real keys as missing. Third over/under-matching regex in
      // this one file; measuring source text is easy to get subtly wrong.
      const open = src.indexOf('{', at);
      const body = src.slice(open + 1, src.indexOf('};', open));
      const keys = [...body.matchAll(/\b([A-Z_]{3,}):/g)].map(m => m[1]).sort();
      expect(keys, `${name} in ${file} must be keyed exactly on the statuses the database `
        + `permits. Found: ${JSON.stringify(keys)}`)
        .toEqual([...DB_STATUSES].sort());
    }
  });

  it('no risk screen filters or links by a status the CHECK constraint forbids', () => {
    // The ghost vocabulary also leaked into a query string: the risk overview's
    // "Treatments" tile linked to `/risk?status=TREATMENT_IN_PROGRESS`, which the
    // register hands straight to `riskApi.list(status)`. The server matched nothing,
    // so the tile always opened an empty register — a dead link that renders as a
    // working one, and no comparison for the test above to catch.
    const offenders: string[] = [];
    for (const f of FILES) {
      const re = /[?&]status=([A-Z_]+)/g;
      let m: RegExpExecArray | null;
      while ((m = re.exec(read(f))) !== null) {
        if (!(DB_STATUSES as readonly string[]).includes(m[1])) offenders.push(`${f} → ${m[1]}`);
      }
    }
    expect(offenders, 'Offenders: ' + JSON.stringify(offenders)).toEqual([]);
  });

  it('the scan actually sees the risk-status comparisons it claims to check', () => {
    // Non-vacuity, and specifically a guard on RISK_IDENTIFIERS: narrowing that list
    // to fix six false positives is one edit away from narrowing it to nothing, at
    // which point every test above passes by measuring an empty set.
    const detail = comparedStatuses(read('src/pages/risk/RiskDetailPage.tsx'));
    const register = comparedStatuses(read('src/pages/risk/RiskRegisterPage.tsx'));
    expect(detail, 'RiskDetailPage risk-status comparisons vanished — RISK_IDENTIFIERS '
      + 'no longer matches the page').toContain('OPEN');
    expect(register, 'RiskRegisterPage risk-status comparisons vanished').toContain('OPEN');
    expect([...detail, ...register].length).toBeGreaterThanOrEqual(6);
  });

  it('the detail page offers Approve and Close on the statuses that reach them', () => {
    const src = read('src/pages/risk/RiskDetailPage.tsx');
    // Non-vacuity: if these three vanish, the two tests above pass over a page that
    // has no lifecycle buttons left at all — which is what "0 offenders" would then
    // be measuring. Named literally so a rename fails here rather than silently.
    expect(src, 'Approve must render for UNDER_REVIEW — the status `approve()` requires')
      .toContain("risk.status === 'UNDER_REVIEW'");
    expect(src, 'Close must render for MITIGATING — the post-approval status, and the one '
      + 'the mandatory-treatment gate actually governs')
      .toContain("risk.status === 'MITIGATING'");
    expect(src, 'Submit-for-review must render for OPEN').toContain("risk.status === 'OPEN'");
  });
});

describe('F2-164 — the bare accept bypass is gone from the client', () => {
  it('no api client exposes POST /risks/{id}/accept', () => {
    const api = read('src/lib/api.ts');
    // ⚠ Matches the endpoint path, not the word "accept": `/acceptances` is the
    // GOVERNED surface and must survive. The retired route ends at `/accept`.
    const bare = /\/api\/v1\/risk\/risks\/\$\{[^}]+\}\/accept`/.test(api);
    expect(bare,
      'POST /risks/{id}/accept was a bare RISK:WRITE flip to ACCEPTED that exempted the '
      + 'risk from the mandatory-treatment close gate, with no acceptance record for the '
      + 'expiry sweep to ever reopen. The endpoint is gone server-side; a client calling '
      + 'it would 404. Use riskAcceptanceApi.create then .approve.').toBe(false);

    // …and the governed surface is still wired, or the fix removed the feature.
    expect(api).toContain('/api/v1/risk/risks/${riskId}/acceptances');
    expect(api).toContain('/api/v1/risk/acceptances/${id}/approve');
  });

  it('every riskAcceptanceApi method has at least one caller', () => {
    // 🔑 The gap this slice found. F2-68(c)'s unused-export scan resolves at the
    // granularity of the exported OBJECT: `riskAcceptanceApi` had one caller
    // (`create`, from the wizard) so it counted as reached — and `approve`, `reject`,
    // `revoke` and `listForRisk` were dead inside a live export. Read aloud, that was
    // "no screen can approve a risk acceptance", which is why the ungoverned one-click
    // flip was the only path that worked. An instrument's granularity is its blind spot.
    const api = read('src/lib/api.ts');
    const decl = api.slice(api.indexOf('export const riskAcceptanceApi = {'));
    const body = decl.slice(0, decl.indexOf('\n};'));
    const methods = [...body.matchAll(/^\s{2}(\w+):/gm)].map(m => m[1]);
    expect(methods.length, 'riskAcceptanceApi parsed as empty — the regex lost the '
      + 'declaration shape and this test is measuring nothing').toBeGreaterThanOrEqual(5);

    const tree = execSyncFileList();
    const uncalled = methods.filter(m =>
      !tree.some(f => f !== 'src/lib/api.ts'
        && read(f).includes(`riskAcceptanceApi.${m}(`)));
    expect(uncalled, 'Dead acceptance-API methods: ' + JSON.stringify(uncalled)
      + '. A method with no caller is a governance step no user can take.').toEqual([]);
  });
});

/** Every source file under src/, so "has a caller" is measured over the whole app. */
function execSyncFileList(): string[] {
  const { readdirSync, statSync } = require('node:fs') as typeof import('node:fs');
  const out: string[] = [];
  const walk = (dir: string) => {
    for (const e of readdirSync(dir)) {
      const p = `${dir}/${e}`;
      if (statSync(p).isDirectory()) walk(p);
      else if (/\.tsx?$/.test(e)) out.push(p);
    }
  };
  walk('src');
  return out;
}
