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
