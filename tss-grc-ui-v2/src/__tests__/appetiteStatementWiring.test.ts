import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { stripComments } from './support/sourceScan';

/**
 * F2-166 — a risk appetite statement can be created, and the one it creates can gate something.
 *
 * <p>Found by F2-165's per-method reachability scan. The Appetite tab listed statements, showed a
 * five-KPI dashboard, fed a breach feed, and offered <b>Update exposure</b>, <b>Approve</b> and
 * <b>Retire</b> — every verb except the two that bring a statement into existence.
 * `ermAdvancedApi.createAppetite` and `updateAppetite` had no caller anywhere in `src/`.
 *
 * <p>That is not cosmetic. `RiskService.isOverAppetite` matches an <b>APPROVED</b> statement on the
 * risk's `categoryId` and compares residual to `toleranceUpper` — it is the over-appetite arm of
 * F2-164's mandatory-treatment close gate. With no way to create a statement, that arm can only
 * fire for a tenant whose rows arrived by seed script. Measured in dev: organisation 1 has 9
 * (8 APPROVED, 3 risks currently over a ceiling); organisations 2 and 3 have <b>zero</b>, and no
 * screen could give them any.
 *
 * <h3>⚠ Why nobody noticed — the fallback was load-bearing, again</h3>
 * `loadAll` ended with `if (list.length === 0) { applyMock(); return; }`. A signed-in tenant with no
 * statements was shown the demo library (`APPETITES` + `SAUDI_APPETITES`, ids 5101–5107 / 6101–6108)
 * with the write buttons live on rows that exist in no database. So the tab always looked populated,
 * the honest "No appetite statements yet" branch was <b>unreachable code</b>, and the missing create
 * path had nothing to draw attention to it. Fourth occurrence of this shape after F2-42, F2-38 and
 * F2-158 — 🔑 <b>when a screen has a client-side fallback, hit its endpoints before believing it.</b>
 */

const PAGE = join(__dirname, '..', 'pages', 'risk', 'RiskRegisterPage.tsx');
const SRC = stripComments(readFileSync(PAGE, 'utf8'));

/** The appetite panel only — the page also holds the register, heatmap, KRI and workflow tabs. */
function appetitePanel(): string {
  const from = SRC.indexOf('function AppetiteDashboard(');
  expect(from, 'AppetiteDashboard is gone — this whole file is measuring nothing').toBeGreaterThan(0);
  const to = SRC.indexOf('\nfunction ', from + 1);
  return SRC.slice(from, to === -1 ? SRC.length : to);
}

describe('F2-166 — the appetite register can create and edit a statement', () => {
  it('wires createAppetite and updateAppetite', () => {
    const panel = appetitePanel();
    expect(panel, 'nothing calls createAppetite — no tenant can define an appetite, and the '
      + 'over-appetite close gate has nothing to compare against')
      .toContain('ermAdvancedApi.createAppetite');
    expect(panel, 'a statement could be created and never corrected')
      .toContain('ermAdvancedApi.updateAppetite');
  });

  it('offers a create affordance and a per-row edit', () => {
    const panel = appetitePanel();
    expect(panel).toMatch(/setEditing\(\{\s*of:\s*null\s*\}\)/);   // New statement
    expect(panel).toMatch(/setEditing\(\{\s*of:\s*s\s*\}\)/);      // Edit this row
  });

  it('requires the two columns the close gate reads', () => {
    // ⚠ This is the assertion with teeth. `RiskAppetiteRequest` accepts a null `categoryId` and a
    // null `toleranceUpper`, and `isOverAppetite` needs BOTH — so an optional form produces
    // statements that gate nothing and look identical to ones that do. Four of the nine rows seeded
    // in dev have a null category and are inert for exactly this reason.
    const panel = appetitePanel();
    for (const key of ['categoryId', 'toleranceUpper']) {
      const field = new RegExp(`key: '${key}',[^\\n]*`).exec(panel)?.[0] ?? '';
      expect(field, `the ${key} field is missing`).not.toBe('');
      expect(field, `${key} must be required — a statement without it is invisible to `
        + 'RiskService.isOverAppetite, so it reads as governance that is not there')
        .toContain('required: true');
    }
  });

  it('the appetite-level options are exactly the tones the badge can render', () => {
    // F2-164's vocabulary lesson: a lookup table with `?? 'neutral'` swallows an unknown key, so a
    // level the badge cannot render is indistinguishable from one it can. Deriving the options from
    // APPETITE_TONE rather than retyping them is what keeps the two in step.
    expect(appetitePanel()).toContain('Object.keys(APPETITE_TONE)');
  });
});

describe('F2-166 — an empty tenant sees an empty register, not a fabricated one', () => {
  it('no longer swaps in the demo library when the server returns zero statements', () => {
    expect(appetitePanel(), 'the zero-rows fallback is back: a tenant with no statements is shown '
      + 'the demo library with live write buttons on ids that exist nowhere, and the missing create '
      + 'path becomes invisible again')
      .not.toMatch(/list\.length\s*===\s*0[^\n]*applyMock/);
  });

  it('still shows the demo library to a visitor who is not signed in', () => {
    // The other direction, deliberately. Demo mode is a real feature for the unauthenticated
    // landing experience; deleting it would be a different defect, so this pins that it survives.
    const panel = appetitePanel();
    expect(panel).toMatch(/if\s*\(!isAuthenticated\(\)\)\s*\{\s*applyMock\(\);\s*return;\s*\}/);
  });

  it('hides the write affordances while demo rows are on screen', () => {
    // A write against id 5101 can only 404. Showing the button teaches the user the tab is broken.
    const panel = appetitePanel();
    expect(panel).toMatch(/!showingDemo && \(/);                       // the New button
    expect(panel).toMatch(/!showingDemo && s\.status !== 'RETIRED'/);  // the row Edit
  });

  it('names what the empty state means for the gate', () => {
    // An empty register is a finding about the tenant's governance, not a blank panel — so the copy
    // says which control is inert until a statement exists and is approved.
    expect(appetitePanel()).toMatch(/over-appetite arm of the mandatory-treatment gate/);
  });
});
