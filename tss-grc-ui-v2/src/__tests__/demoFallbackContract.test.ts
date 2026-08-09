import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, posix, sep } from 'node:path';

/**
 * W2-1b — two static guards on the demo-data fallback.
 *
 * The platform substitutes demo rows in two situations, both inside {@code useApiResource}:
 * when the API call **fails** (backend down, 500, network error) and when it succeeds but
 * returns **zero rows** — the latter documented in the hook as "prefer the rich demo set so
 * the UI never looks broken on a fresh tenant".
 *
 * **This is disclosed, not silent, and that distinction is the whole point of these tests.**
 * The hook sets `isDemo: true` and every consumer page renders a warning `Demo mode` badge
 * plus disables live refresh (`enabled: !isDemo`). Measured before writing this file: 10 of
 * 10 real consumers reference `isDemo`. So the behaviour is deliberate product design, and
 * the risk is not that it exists but that a future page **forgets to disclose it** — which
 * is precisely what a test can hold.
 *
 * ⚠ W2-1b's plan text asks for "every register → error state, **zero rows**". That is in
 * direct conflict with the shipped contract (demo rows + a badge), and choosing between them
 * is a product decision, not an engineering one — so these tests pin the CURRENT contract and
 * the conflict is recorded in the plan for the owner to settle. Deleting demo mode
 * unilaterally would be the same mistake as F2-40: treating a designed assertion as a defect.
 */

const SRC = join(__dirname, '..');

function walk(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) {
      if (name === 'node_modules' || name === 'dist') continue;
      walk(full, out);
    } else if (/\.tsx?$/.test(name)) {
      out.push(full);
    }
  }
  return out;
}

/** Repo-relative, forward-slashed, so the baseline list reads the same on any platform. */
function rel(file: string): string {
  return file.slice(SRC.length + 1).split(sep).join(posix.sep);
}

const IS_TEST = (p: string) => /\.test\.tsx?$/.test(p) || p.includes('__tests__/') || p.includes('__fixtures__/');

const ALL = walk(SRC).map(f => ({ path: rel(f), body: readFileSync(f, 'utf8') }));

describe('W2-1b — mock imports outside tests are ratcheted, not free', () => {
  /**
   * The plan asks for a CI rule banning `/mock/i` imports outside tests. A hard ban cannot land
   * today: **33 such imports exist across 27 files** (measured, and close to the 26 F2-15
   * estimated). So this is a ratchet on the measured baseline — the same shape as the backend's
   * authorisation and mass-assignment ratchets. F2-15 is the row that drives the number down; this
   * test only guarantees it cannot drift up while that work is pending.
   */
  const BASELINE_MOCK_IMPORTS = 33;
  const BASELINE_MOCK_FILES = 27;

  function mockImports() {
    const hits: { path: string; module: string }[] = [];
    for (const { path, body } of ALL) {
      if (IS_TEST(path)) continue;
      const re = /^\s*import[^;\n]*?from\s+['"]([^'"]*mock[^'"]*)['"]/gim;
      let m: RegExpExecArray | null;
      while ((m = re.exec(body)) !== null) hits.push({ path, module: m[1] });
    }
    return hits;
  }

  it('does not grow the number of non-test files importing mock data', () => {
    const hits = mockImports();
    const files = new Set(hits.map(h => h.path));
    // Reported, not just asserted: a failure should say WHICH file was added.
    const detail = [...files].sort().join('\n  ');
    expect(hits.length, `mock imports outside tests grew.\n  ${detail}`)
      .toBeLessThanOrEqual(BASELINE_MOCK_IMPORTS);
    expect(files.size, `files importing mocks grew.\n  ${detail}`)
      .toBeLessThanOrEqual(BASELINE_MOCK_FILES);
  });

  it('keeps the scan honest — it must actually be finding the known sites', () => {
    // A scan that silently matches nothing would make the ratchet above vacuously green, which
    // is the failure mode that let four backend regex scans under-count this month.
    const hits = mockImports();
    expect(hits.length).toBeGreaterThan(20);
    expect(hits.map(h => h.path)).toContain('pages/incidents/IncidentsPage.tsx');
  });
});

describe('W2-1b — a demo substitution is never silent', () => {
  /**
   * The real guard. `useApiList`/`useApiResource` can put fabricated rows on screen, so any page
   * that calls one **must** reference `isDemo` — otherwise a customer sees demo incidents,
   * vendors or evidence with nothing telling them so. This is statically checkable and cheap,
   * and it is what stops the next page from regressing what W2-1a-i and W2-1a-ii cleaned up.
   */
  it('every page consuming useApiList/useApiResource surfaces isDemo', () => {
    const offenders: string[] = [];
    for (const { path, body } of ALL) {
      if (IS_TEST(path)) continue;
      if (!/useApi(List|Resource)\s*[<(]/.test(body)) continue;
      if (path === 'lib/useApiResource.ts') continue;              // the hook itself
      // A file that only names the hook in a comment is not a consumer.
      const codeOnly = body.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
      if (!/useApi(List|Resource)\s*[<(]/.test(codeOnly)) continue;
      if (!/\bisDemo\b/.test(codeOnly)) offenders.push(path);
    }
    expect(offenders,
      'These files put mock rows on screen without surfacing isDemo, so a user cannot tell '
      + 'fabricated data from their own:\n  ' + offenders.join('\n  '))
      .toEqual([]);
  });

  /**
   * F2-98 — the guard above triggers on a call to the hook. Four pages **hand-roll** the same
   * substitution (`catch { applyDemo() }` / `if (list.length === 0) applyDemo()`) and so were
   * outside it entirely. All four disclosed with `{demoMode && <Badge>}` where
   * `const demoMode = !isAuthenticated()` — a value computed once, which is false for exactly the
   * users who can hit the empty-register and API-failure paths. So the badge announced the case
   * that was already obvious (you are not signed in) and stayed silent on the two that are not.
   *
   * <p>The lesson is the one the export ceiling taught: a guard scoped to a *mechanism* tolerates
   * every reimplementation of the behaviour. This is scoped to the behaviour.
   */
  /** Strip comments — a javadoc describing the old shape is documentation, not code. */
  function code(body: string): string {
    return body.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/^\s*\/\/.*$/gm, ' ');
  }

  /**
   * Every `applyDemo…()` / `applyMock()` body in a file, by brace matching.
   *
   * ⚠ The detector is deliberately **behavioural, not name-based**. Its first draft looked for
   * `set(Demo|IsDemo)…(true)` anywhere in the file, and that was wrong twice over: it reported the
   * correctly-fixed `RiskRegisterPage` as broken (its state is called `showingDemo`), and it passed
   * `EthicsPage` on the strength of its *parent's* `setDemoMode(true)` while both child tabs were
   * still silent. Naming is not the contract; setting the flag where the mock is applied is.
   */
  function applierBodies(src: string): { name: string; body: string }[] {
    const out: { name: string; body: string }[] = [];
    const re = /function\s+(apply[A-Za-z0-9_]*)\s*\([^)]*\)\s*\{/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(src)) !== null) {
      let depth = 1, i = re.lastIndex;
      while (i < src.length && depth > 0) {
        if (src[i] === '{') depth++;
        else if (src[i] === '}') depth--;
        i++;
      }
      out.push({ name: m[1], body: src.slice(re.lastIndex, i) });
    }
    return out;
  }

  it('a page that hand-rolls the substitution raises the flag where it applies the mock', () => {
    const offenders: string[] = [];
    let scanned = 0;
    for (const { path, body } of ALL) {
      if (IS_TEST(path)) continue;
      if (!/import[\s\S]{0,400}?from\s+['"][^'"]*mock[^'"]*['"]/i.test(body)) continue;
      const src = code(body);
      // Only files that substitute from a catch or an empty-result branch are in scope: applying a
      // mock in an explicit "load demo" button is not an undisclosed substitution.
      const substitutes =
        /catch\s*(\([^)]*\))?\s*\{\s*(apply|set)[A-Za-z]*\(/.test(src)
        || /\.length === 0\)\s*\{?\s*(apply|set)[A-Za-z]*\(/.test(src);
      if (!substitutes) continue;
      for (const { name, fnBody } of applierBodies(src).map(a => ({ name: a.name, fnBody: a.body }))) {
        // Is this applier actually one of the substitution paths, or an unrelated apply*?
        const isSubstitutionPath = new RegExp(
          `(catch\\s*(\\([^)]*\\))?\\s*\\{\\s*${name}\\(|\\.length === 0\\)\\s*\\{?\\s*${name}\\()`)
          .test(src);
        if (!isSubstitutionPath) continue;
        scanned++;
        if (!/\bset[A-Za-z0-9_]*\(\s*true\s*\)/.test(fnBody)) offenders.push(`${path} → ${name}()`);
      }
    }
    // Anti-vacuity: a brace-matcher that found nothing would make this green for the wrong reason.
    expect(scanned, 'the applier scan found no substitution paths at all — it has stopped working')
      .toBeGreaterThanOrEqual(6);
    expect(offenders,
      'These functions put fabricated rows on screen without raising the demo flag, so a signed-in '
      + 'user cannot tell them from their own records (F2-98). Set the flag inside the applier:\n  '
      + offenders.join('\n  '))
      .toEqual([]);
  });

  it('no page still derives its demo disclosure from the session alone', () => {
    // `const demoMode = !isAuthenticated()` is the exact failing shape: evaluated once, false for
    // every signed-in user, and therefore silent on both substitution paths.
    const offenders: string[] = [];
    for (const { path, body } of ALL) {
      if (IS_TEST(path)) continue;
      if (!/import[\s\S]{0,400}?from\s+['"][^'"]*mock[^'"]*['"]/i.test(body)) continue;
      const src = code(body);
      if (!/catch\s*(\([^)]*\))?\s*\{\s*apply[A-Za-z]*\(|\.length === 0\)\s*\{?\s*apply[A-Za-z]*\(/.test(src))
        continue;
      if (/const\s+\w*[Dd]emo\w*\s*=\s*!isAuthenticated\(\)/.test(src)) offenders.push(path);
    }
    expect(offenders,
      'A demo flag declared `const … = !isAuthenticated()` cannot describe a substitution that '
      + 'happens to a signed-in user. Make it state (F2-98):\n  ' + offenders.join('\n  '))
      .toEqual([]);
  });

  /**
   * F2-167 — **disclosure is not enough when the fabricated row carries a button.**
   *
   * Every assertion above this one is about *telling the user* the rows are fake. F2-166 satisfied
   * all of them and was still a P2: the badge said "demo" while Approve / Retire / Update-exposure
   * rendered live on the fabricated rows. And the ids are not safely distinct — **every mock file
   * numbers its records from 1, and so does every identity column.** Measured in dev: `iam.users`
   * holds 7 rows at ids 1–8 against `mockAdmin`'s 1–15, `tprm.vendors` 28 rows at 1–28 against
   * `mockTprm`'s 1–501, `compliance.controls` 35 at 1–35, `evidence.evidence_items` 57 inside
   * `mockEvidence`'s band. Substitution also fires when the list call **threw**, and on that path the
   * tenant's real records exist — so `onDelete` on "demo user #3" sent a real, tenant-scoped delete
   * for **real user 3**, while the confirmation dialog displayed the fabricated name.
   *
   * ⚙ So the shape to distrust is not "undisclosed mock data" but **"a real mutation reachable from
   * a row the product itself knows is not real"**. `RowActions` is the choke point — it renders
   * edit, delete *and* the workflow `extras` (approve / escalate / complete) for the whole platform —
   * so one `disabled` prop covers all 35 sites, and `RowActions.test.tsx` proves the callbacks do not
   * fire. This is a hard zero with no allow-list, because every site was fixed in the same commit.
   */
  it('every RowActions on a substituting page is disabled while the rows are fabricated', () => {
    const offenders: string[] = [];
    let scanned = 0;
    for (const { path, body } of ALL) {
      if (IS_TEST(path)) continue;
      const src = code(body);
      // In scope: a file that can put fabricated rows on screen, by either mechanism.
      const substitutes =
        (/import[\s\S]{0,400}?from\s+['"][^'"]*mock[^'"]*['"]/i.test(body)
          && (/catch\s*(\([^)]*\))?\s*\{\s*(apply|set)[A-Za-z]*\(/.test(src)
              || /\.length === 0\)\s*\{?\s*(apply|set)[A-Za-z]*\(/.test(src)))
        || /useApi(List|Resource)\s*[<(]/.test(src);
      if (!substitutes) continue;
      for (const m of src.matchAll(/<RowActions\b([^>]*)/g)) {
        scanned++;
        if (!/\bdisabled=\{/.test(m[1])) {
          offenders.push(`${path} (offset ${m.index})`);
        }
      }
    }
    // Anti-vacuity: 35 sites were measured across 13 pages. A scan that finds none would make this
    // green for the wrong reason — the failure mode that let four backend regex scans under-count.
    expect(scanned, 'the RowActions scan found nothing — it has stopped working')
      .toBeGreaterThanOrEqual(30);
    expect(offenders,
      'These row-action clusters sit on a page that can substitute fabricated rows, and nothing '
      + 'makes them inert. Pass the page\'s demo flag: `<RowActions disabled={isDemo} …>`. The ids '
      + 'in the mock files overlap real ids, so a delete from a demo row can hit a real record '
      + '(F2-167):\n  ' + offenders.join('\n  '))
      .toEqual([]);
  });

  it('the hook still documents both substitution paths', () => {
    const hook = ALL.find(f => f.path === 'lib/useApiResource.ts');
    expect(hook, 'useApiResource.ts moved — this contract test needs repointing').toBeDefined();
    const body = hook!.body;
    // Path 1: the API threw.
    expect(body).toMatch(/catch\s*\([\s\S]{0,200}setDemo\(true\)/);
    // Path 2: the API succeeded and returned nothing. This is the one worth naming out loud,
    // because a genuinely empty register — a fresh tenant, or one whose rows were deleted —
    // shows demo data instead of its own empty state.
    expect(body).toMatch(/list\.length === 0[\s\S]{0,160}setDemo\(true\)/);
    expect(body).toMatch(/isDemo/);
  });
});

/**
 * F2-169 — a substitution in a *fallback position* must be gated on a fabricated parent.
 *
 * The two guards above certify one mechanism: `useApiResource`/`applyMock` raises `isDemo`, and every
 * consumer renders the badge. ⚙ **A contract that inspects one mechanism certifies only that
 * mechanism.** Measured 2026-08-09: **19 substitutions** written a different way — a bare
 * `.catch(() => FRAMEWORKS)`, a `d.length > 0 ? d : WORKFLOW_DEFINITIONS`, a
 * `?? COMPLIANCE_FINDINGS.find(…)` — were never in this file's population, so they raised no flag and
 * rendered no badge. Worst of them: `frameworkApi.list().catch(() => FRAMEWORKS)` showed a signed-in
 * user a **fabricated regulatory catalogue** on any failure, and three sites turned a genuinely empty
 * tenant into fabricated rows, which is [[F2-166]]'s defect in three more places.
 *
 * The rule this pins is the one [[F2-168]] made expressible: **fabricated data may substitute only
 * for a fabricated parent.** `isDemoId(parentId)` is true exactly when the record the children hang
 * off came out of the corpus, so demo walk-throughs keep working and a real record's failed fetch
 * surfaces as an error instead of as somebody's invented policies.
 *
 * A top-of-loader `if (!isAuthenticated()) { setItems(MOCK); return; }` is *not* a fallback position
 * and is deliberately outside this population — that is the disclosed demo path.
 */
describe('F2-169 — fabricated data only substitutes for a fabricated parent', () => {
  /** `.catch(() => X)`, `?? X`, `a.length ? a : X` — the three shapes measured in the tree. */
  const FALLBACK = /\.catch\(\s*(?:\([^)]*\)|\w+)\s*=>[^\n]*|\?\?[^\n]*|\.length\s*(?:>\s*0|===\s*0|\?)[^\n]*/;

  /**
   * ⚠ Reviewed exception, with the reason recorded — not an allow-list to grow.
   * `AdminPage`'s Roles tab is a read-only display, and F2-52's comment in that file explains why the
   * fallback stays: the role *picker* reads `rolesAreReal ? roles : []`, so the ghost roles the
   * fallback contains can never be granted. If that decoupling ever goes, this entry must go too.
   */
  const REVIEWED = new Set(['pages/admin/AdminPage.tsx#ADMIN_ROLES']);

  it('no ungated fabricated fallback survives outside the reviewed exception', () => {
    const violations: string[] = [];
    let population = 0;
    for (const { path, body } of ALL) {
      if (IS_TEST(path)) continue;
      const src = stripBlockAndLineComments(body);
      const names = corpusNamesImportedBy(src);
      if (names.length === 0) continue;
      // a local `const x = isDemoId(...)` counts as the gate, because that is how the six framework
      // tabs read — the predicate is hoisted out of the six identical .catch arms
      const gates = ['isDemoId', ...[...src.matchAll(/const\s+(\w+)\s*=\s*isDemoId\(/g)].map(m => m[1])];
      for (const line of src.split('\n')) {
        const m = FALLBACK.exec(line);
        if (!m) continue;
        const hit = names.filter(n => m[0].includes(n));
        if (hit.length === 0) continue;
        population += 1;
        if (gates.some(g => m[0].includes(g))) continue;
        const key = `${path}#${hit[0]}`;
        if (REVIEWED.has(key)) continue;
        violations.push(`${key}  ${line.trim().slice(0, 100)}`);
      }
    }
    expect(violations).toEqual([]);
    // anti-vacuity: the scan must still be finding the gated survivors it is meant to police
    expect(population).toBeGreaterThanOrEqual(10);
  });

  it('the three empty-tenant substitutions are gone — an empty tenant is a real answer', () => {
    // F2-166's shape, found three more times: `list.length === 0 ? MOCK : list` makes the honest
    // empty state unreachable code for a signed-in tenant that simply has nothing yet.
    for (const rel of ['pages/ethics/EthicsPage.tsx', 'pages/workflow/WorkflowsPage.tsx',
                       'pages/compliance/CompliancePage.tsx']) {
      const src = stripBlockAndLineComments(readFileSync(join(SRC, rel), 'utf8'));
      const names = corpusNamesImportedBy(src);
      // WARNING, and the reason this reads the way it does: the first draft built the pattern with
      // `new RegExp` from a template literal and was **vacuous** — it passed with the defect
      // re-inserted, and only the general scan above caught the mutation. A `.not.toMatch` whose
      // regex can never match is the quietest way to own a guarantee you do not have.
      const offenders = src.split(/\r?\n/)
        .filter(l => /\.length\s*(?:>\s*0|===\s*0)/.test(l))
        .filter(l => names.some(n => l.includes(n)))
        .map(l => rel + ': ' + l.trim().slice(0, 90));
      expect(offenders).toEqual([]);
    }
  });

  it('a failed load says so', () => {
    // Removing a fabrication is only half the fix: an error rendered as an empty screen is the same
    // lie in a quieter voice. Every loader this slice touched surfaces the failure.
    for (const rel of ['pages/ethics/EthicsPage.tsx', 'pages/privacy/PrivacyPage.tsx',
                       'pages/workflow/WorkflowsPage.tsx', 'pages/compliance/CompliancePage.tsx',
                       'pages/detail/ControlDetailPage.tsx', 'pages/detail/DpiaDetailPage.tsx',
                       'pages/detail/FindingDetailPage.tsx', 'pages/detail/VendorDetailPage.tsx']) {
      expect(readFileSync(join(SRC, rel), 'utf8'), `${rel} swallows its load error`)
        .toMatch(/toast\.danger\([^\n]*loadFailed/);
    }
  });

  it('loadFailed is bilingual, like everything a user reads', () => {
    for (const lang of ['en', 'ar']) {
      expect(readFileSync(join(SRC, `translations/${lang}.ts`), 'utf8')).toMatch(/^\s+loadFailed: '/m);
    }
  });
});

/** Corpus identifiers a file imports — the only names that can be a fabricated substitution. */
function corpusNamesImportedBy(src: string): string[] {
  const out = new Set<string>();
  for (const m of src.matchAll(/import\s+(?:type\s+)?\{([^}]*)\}\s+from\s+'[^']*(?:mock\w+|entityAnchors|policyLifecycle)'/g)) {
    for (const part of m[1].split(',')) {
      const n = part.trim().split(' as ').pop()!.trim();
      if (n && /^[A-Z]/.test(n)) out.add(n);
    }
  }
  return [...out];
}

/**
 * ⚠ Comments must come out first. The measurement that produced this test read them and reported
 * three "violations" that were the prose describing the fix — the same mistake F2-165 made with a
 * hand-rolled stripper. This one is deliberately conservative: it does not try to parse regex
 * literals, it only removes `//`-to-end-of-line and `/* … *\/` runs, which is enough here because a
 * corpus identifier is upper-case and never appears inside one.
 */
function stripBlockAndLineComments(src: string): string {
  return src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');
}
