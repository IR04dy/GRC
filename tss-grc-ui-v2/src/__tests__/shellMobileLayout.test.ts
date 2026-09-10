import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * JRN-02 — the shell must collapse to ONE track once the sidebar leaves the flow.
 *
 * `shell.css` lays the app out as `.grc-shell { grid-template-columns:
 * var(--sidebar-w) 1fr }`. Below 768px `breakpoints.css` makes `.sb`
 * `position: fixed` so it can slide in as a drawer — which makes it an
 * absolutely-positioned box, so it stops being a grid item and takes no part in
 * auto-placement. Every other child of `.grc-shell` is also out of flow or
 * absent (the skip-link, the backdrop, the help FAB and the palette scrim are
 * all `position: fixed`; the palette returns null when closed), so `.grc-main`
 * is the ONLY in-flow child left and auto-places into the FIRST track — the
 * 280px `--sidebar-w` one — leaving `1fr` empty.
 *
 * At a 390px viewport that computes to `280px 110px`, which is the whole of the
 * audit's report in one cause: a 280px main column, 110px of dead space, and —
 * because `direction: rtl` flips the flow and puts track 1 on the right — the
 * Arabic `x=110` offset. It is the only finding in that audit that makes the
 * product unusable rather than merely confusing.
 *
 * ⚠ The audit blamed `shell.css:7` for a hardcoded `280px 110px` desktop grid.
 * It is `var(--sidebar-w) 1fr`; the observed pair is that template COMPUTED at
 * 390px. The wrong mechanism matters because it points a reader at editing the
 * desktop template, which would break every viewport above 767px instead.
 *
 * ─── What this gate asserts, and why each leg is here ────────────────────────
 *
 * A one-line CSS layout fix is invisible to every other instrument in this tree:
 * jsdom has no layout engine, so no render test can see a track width, and no
 * backend gate reads a stylesheet. Worse, the fix's CORRECTNESS rests on two
 * facts that live in OTHER files and could each be changed by somebody who never
 * opens this one — at which point the declaration is still present, still reads
 * as correct, and silently does nothing. So the premises are legs, not comments
 * ([[F2-463]]: assert the premise rather than restating it):
 *
 *   A. the collapse is declared INSIDE the mobile block (not on desktop);
 *   B. `.sb` is still `position: fixed` there — the CAUSE. One track is right
 *      ONLY because the sidebar is out of flow. Put the drawer back in flow and
 *      a single track means `.sb` takes it and `.grc-main` is pushed to an
 *      implicit second ROW, which is a different broken layout;
 *   C. `breakpoints.css` is still imported AFTER `shell.css`. Both selectors are
 *      `.grc-shell` (0,1,0) and a media query adds no specificity, so LOAD ORDER
 *      is the only thing that makes the mobile rule win. Flip the two imports in
 *      `main.tsx` and this fix stops applying with nothing red anywhere;
 *   D. the desktop template is still the two-track `var(--sidebar-w) 1fr`, and
 *      `.grc-shell` is styled in exactly those two places. Without this, leg A
 *      passes just as happily over a tree where the grid was deleted (nothing to
 *      collapse) or where a third rule re-establishes two tracks later.
 *
 * ⚠⚠ THE DETECTOR MUST STRIP CSS COMMENTS, AND THIS FILE'S OWN FIX IS WHY.
 * The comment above the declaration in `breakpoints.css` QUOTES the desktop
 * template — `grid-template-columns: var(--sidebar-w) 1fr` — to explain the
 * mechanism. A comment-blind scan therefore finds a two-track `.grc-shell` in
 * `breakpoints.css` and reads the epitaph as the corpse ([[F2-193]]), reporting
 * the mobile block as two-track on a CORRECT tree. Comments are blanked to
 * equal-length whitespace so line numbers survive ([[F2-242]]).
 *
 * ⚠ Leg A is a POSITIVE assertion, so a dead detector fails it rather than
 * passing vacuously ([[F2-455]]). What a positive assertion CAN do is pass for
 * the wrong reason: a brace matcher that over-runs would accept a declaration
 * sitting after the block's closing brace — i.e. on desktop, which is the
 * defect. The self-test therefore drives the extractor over synthetic input in
 * BOTH directions, and the must-NOT-match case is the one that matters.
 */

const SRC = join(__dirname, '..');

/** Blank every CSS comment, preserving length and newlines so offsets and line
 *  numbers still refer to the real file. CSS has no line-comment form, so one
 *  non-greedy pass is sound here (the two-pass hazard [[F2-198]] recorded is a
 *  JavaScript one, where `//` and the text inside it can open a block). */
function stripCssComments(css: string): string {
  return css.replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '));
}

/** The body of the first `@media (max-width: 767px)` block whose text mentions
 *  `needle`, brace-matched from its opening `{`. Returns null when absent. */
function mediaBlockContaining(css: string, needle: string): string | null {
  const open = /@media\s*\(\s*max-width:\s*767px\s*\)\s*\{/g;
  let m: RegExpExecArray | null;
  while ((m = open.exec(css)) !== null) {
    let depth = 1;
    let i = m.index + m[0].length;
    const start = i;
    while (i < css.length && depth > 0) {
      if (css[i] === '{') depth++;
      else if (css[i] === '}') depth--;
      i++;
    }
    if (depth !== 0) return null; // unbalanced — refuse rather than guess
    const body = css.slice(start, i - 1);
    if (body.includes(needle)) return body;
  }
  return null;
}

/** Every `grid-template-columns` value declared for `.grc-shell` in `css`. */
function shellTemplates(css: string): string[] {
  const out: string[] = [];
  const rule = /\.grc-shell\s*\{([^}]*)\}/g;
  let m: RegExpExecArray | null;
  while ((m = rule.exec(css)) !== null) {
    const decl = /grid-template-columns\s*:\s*([^;}]+)/.exec(m[1]);
    if (decl) out.push(decl[1].trim());
  }
  return out;
}

/** Track count of a `grid-template-columns` value: top-level whitespace-separated
 *  tokens, so `minmax(0, 1fr)` counts as one and `var(--sidebar-w) 1fr` as two. */
function trackCount(value: string): number {
  let depth = 0;
  let tracks = 0;
  let inToken = false;
  for (const ch of value) {
    if (ch === '(') depth++;
    else if (ch === ')') depth--;
    if (depth === 0 && /\s/.test(ch)) {
      inToken = false;
    } else if (!inToken) {
      inToken = true;
      tracks++;
    }
  }
  return tracks;
}

const breakpoints = stripCssComments(readFileSync(join(SRC, 'styles/breakpoints.css'), 'utf8'));
const shell = stripCssComments(readFileSync(join(SRC, 'styles/shell.css'), 'utf8'));
const mainTsx = readFileSync(join(SRC, 'main.tsx'), 'utf8');

describe('JRN-02 — mobile shell collapses to one track', () => {
  it('A. the mobile drawer block collapses .grc-shell to a single track', () => {
    const block = mediaBlockContaining(breakpoints, '.sb');
    expect(block, 'no @media (max-width: 767px) block mentioning .sb was found in breakpoints.css').not.toBeNull();

    const templates = shellTemplates(block!);
    expect(
      templates,
      'the mobile drawer block declares no grid-template-columns for .grc-shell. ' +
        'Below 768px .sb is position:fixed and therefore not a grid item, so .grc-main ' +
        'auto-places into the 280px --sidebar-w track and the 1fr track is left empty — ' +
        'the whole page laid out in the vacated sidebar column.',
    ).toHaveLength(1);

    expect(
      trackCount(templates[0]),
      `the mobile .grc-shell template is "${templates[0]}", which has ${trackCount(templates[0])} tracks. ` +
        'It must have exactly one: the sidebar is out of flow below 768px, so a second track ' +
        'can only ever be empty space the main column is pushed out of.',
    ).toBe(1);
  });

  it('B. the collapse is only correct because .sb is out of flow there', () => {
    const block = mediaBlockContaining(breakpoints, '.grc-shell');
    expect(block, 'the .grc-shell collapse is not inside a max-width:767px block').not.toBeNull();
    expect(
      /\.sb\s*\{[^}]*position:\s*fixed/.test(block!),
      'the mobile block collapses .grc-shell to one track but no longer makes .sb ' +
        'position:fixed. One track is correct ONLY while the sidebar is out of flow — ' +
        'put the drawer back in flow and .sb takes the single track, pushing .grc-main ' +
        'into an implicit second ROW. The two rules are one decision.',
    ).toBe(true);
  });

  it('C. breakpoints.css is imported after shell.css, so the mobile rule wins', () => {
    const shellAt = mainTsx.indexOf("styles/shell.css");
    const bpAt = mainTsx.indexOf("styles/breakpoints.css");
    expect(shellAt, 'main.tsx no longer imports styles/shell.css').toBeGreaterThan(-1);
    expect(bpAt, 'main.tsx no longer imports styles/breakpoints.css').toBeGreaterThan(-1);
    expect(
      bpAt,
      'main.tsx imports breakpoints.css BEFORE shell.css. Both rules select .grc-shell ' +
        '(specificity 0,1,0) and a media query adds none, so load order is the only thing ' +
        'that makes the mobile collapse win. In this order the desktop two-track template ' +
        'wins at every viewport and the JRN-02 fix silently does nothing.',
    ).toBeGreaterThan(shellAt);
  });

  it('D. the desktop template is still the two-track grid this collapses', () => {
    const desktop = shellTemplates(shell);
    expect(
      desktop,
      'shell.css declares no grid-template-columns for .grc-shell. If the shell grid is ' +
        'gone, leg A is asserting a collapse of nothing — re-read the layout rather than ' +
        'deleting this leg.',
    ).toHaveLength(1);
    expect(trackCount(desktop[0]), `desktop template "${desktop[0]}" is not two tracks`).toBe(2);
    expect(desktop[0], 'the desktop sidebar track no longer references --sidebar-w').toContain('--sidebar-w');

    // Exactly two rules style .grc-shell across the whole stylesheet set: the
    // desktop template and the mobile collapse. A third would re-open the
    // question of which one wins.
    const all = [
      'styles/tokens.css',
      'styles/globals.css',
      'styles/shell.css',
      'styles/components.css',
      'styles/motion.css',
      'styles/breakpoints.css',
      'styles/mobile.css',
    ].map((p) => stripCssComments(readFileSync(join(SRC, p), 'utf8')));
    const total = all.reduce((n, css) => n + shellTemplates(css).length, 0);
    expect(
      total,
      'more than two rules set grid-template-columns on .grc-shell. Which one applies is ' +
        'then a load-order question again; fold the new rule into one of the two.',
    ).toBe(2);
  });

  it('self-test — the extractor accepts a declaration inside the block and refuses one outside it', () => {
    const inside = `
      @media (max-width: 767px) {
        .sb { position: fixed; }
        .grc-shell { grid-template-columns: 1fr; }
      }
    `;
    const outside = `
      @media (max-width: 767px) {
        .sb { position: fixed; }
      }
      .grc-shell { grid-template-columns: 1fr; }
    `;
    // Must match: the declaration is in the block.
    expect(shellTemplates(mediaBlockContaining(inside, '.sb')!)).toHaveLength(1);
    // Must NOT match: a brace matcher that over-runs would swallow the trailing
    // rule and report a desktop-wide collapse as a mobile fix. This is the
    // direction that makes the extractor honest.
    expect(shellTemplates(mediaBlockContaining(outside, '.sb')!)).toHaveLength(0);

    // The comment stripper is load-bearing because breakpoints.css's own fix
    // comment quotes the desktop template ([[F2-193]]).
    const quoted = `
      @media (max-width: 767px) {
        .sb { position: fixed; }
        /* was .grc-shell { grid-template-columns: var(--sidebar-w) 1fr } */
        .grc-shell { grid-template-columns: 1fr; }
      }
    `;
    const t = shellTemplates(mediaBlockContaining(stripCssComments(quoted), '.sb')!);
    expect(t).toHaveLength(1);
    expect(trackCount(t[0])).toBe(1);

    // Track counting: a function call is one track, not two.
    expect(trackCount('1fr')).toBe(1);
    expect(trackCount('minmax(0, 1fr)')).toBe(1);
    expect(trackCount('var(--sidebar-w) 1fr')).toBe(2);
    expect(trackCount('repeat(2, 1fr)')).toBe(1);
  });
});
