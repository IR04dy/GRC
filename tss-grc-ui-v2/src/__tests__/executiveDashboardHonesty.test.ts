import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * JRN-03 — the first screen after sign-in must not assert what it has not measured.
 *
 * Three claims, all on `ExecutiveDashboard`, all of them things a reader takes at
 * face value precisely because the surface around them is real:
 *
 *   1. THE GREETING NAMED A PERSON WHO IS NOT THE USER. The hero read
 *      `{isAr ? 'أيمن' : 'Aymen'}` — one hard-coded name, in both languages, for
 *      every user of every tenant. Measured, it was the only occurrence of either
 *      string in this source tree and the backend had none. F2-207's
 *      invented-person family at its sharpest: not an officer inside a demo
 *      corpus, but the signed-in user's own name, fabricated.
 *
 *      🔑 What made it read as personalised is that the greeting BESIDE it is
 *      genuinely computed from the clock. A real mechanism next to a fabricated
 *      value is what stops anybody checking the value.
 *
 *   2. THE 30-DAY POSTURE DELTA WAS DERIVED FROM THE PRESENT.
 *      `posture >= 75 ? +2 : posture >= 60 ? -1 : -4`, under a comment saying the
 *      magnitude was "derived from the current posture so the chip moves
 *      coherently" — i.e. constructed to LOOK measured. It is not derivable today
 *      rather than merely unwired: nothing stores the posture composite, and one
 *      of its own inputs has no historical form.
 *
 *   3. THE DELTA ARROW FOLLOWED THE TONE, NOT THE SIGN — found while reading the
 *      renderer, and NOT part of the audit. `delta.tone === 'success' ? ↓ : ↑`
 *      chooses the icon from the tone while the sign is printed from the value,
 *      and the two are independent. Live on the Compliance-score tile, whose chip
 *      was `{ value: +3, tone: 'success' }`: it rendered "↓ +3". Removing the
 *      invented chips makes that unreachable rather than absent, so the renderer
 *      is fixed as well (F2-213) — a future real delta would otherwise inherit it.
 *
 * ─── What this gate does NOT assert, and why ─────────────────────────────────
 *
 * ⚠ The identical Arabic in the afternoon and evening branches is CORRECT and is
 * pinned here so nobody "fixes" it. §6f called it a house-rule-7 defect — *"the
 * three-way English branch is a two-way one in the platform's primary language"*
 * — and that is a fact about ARABIC, not about the code: Arabic greets in two
 * parts of the day (صباح الخير until noon, مساء الخير after) where English greets
 * in three. Inventing a third Arabic greeting to make the branches symmetrical
 * would make the primary language read unnaturally, which is a fix failing on
 * correct code (F2-214).
 *
 * ⚠ Nor does it assert a timezone. Neither the greeting nor the date label passes
 * one, so both follow the viewer's device clock — which is right for a greeting,
 * since it greets whoever is reading. Whether the DATE should instead show the
 * tenant's regulatory day is a product question, filed rather than decided here.
 */

const SRC = join(__dirname, '..');
const FILE = 'pages/dashboard/ExecutiveDashboard.tsx';
const raw = readFileSync(join(SRC, FILE), 'utf-8');

/** Blank comments, preserving length and newlines so line numbers survive
 *  (F2-242). ONE left-to-right alternation, never two passes: at a `//` an inner
 *  `/*` can never open a block, and the `(?<!:)` keeps a URL from truncating one
 *  (F2-198 / F2-211). Load-bearing here because this fix's own comments QUOTE
 *  every expression they retired — the register's convention — so a raw scan
 *  reads the epitaph as the corpse (F2-193). */
function stripComments(src: string): string {
  return src.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '));
}

const code = stripComments(raw);

describe('JRN-03 — the executive dashboard asserts only what it measured', () => {
  it('the hero greets from the session, and substitutes nothing when there is none', () => {
    expect(
      /['"]Aymen['"]|['"]أيمن['"]/.test(code),
      'the hard-coded greeting name is back in rendered code. It named one person for every '
        + 'user of every tenant, in both languages (F2-207).',
    ).toBe(false);

    expect(
      /getUser\(\)\?\.username/.test(code),
      'the hero no longer reads the signed-in identity. The session carries a username and no '
        + 'display name, so that is the most the client can honestly greet by.',
    ).toBe(true);

    // No placeholder fallback. F2-248 records this exact shape as a defect, and
    // production holds a row whose author is the literal string 'You'.
    expect(
      /getUser\(\)\?\.username\s*\?\?\s*['"`]/.test(code),
      'a string fallback was added after `getUser()?.username`. With no session there is no '
        + 'name and none may be substituted — F2-248, where `?? \'You\'` reached production.',
    ).toBe(false);

    // ...and the separator goes with the name. Dropping the name while leaving the
    // comma renders "Good morning, " with nothing after it — the visible residue of
    // a name that is not there, which is how a removal announces itself as a bug.
    expect(
      /\{who \? \(isAr \? '، ' : ', '\) : ''\}/.test(code),
      'the greeting separator is no longer conditional on there being a name.',
    ).toBe(true);
  });

  it('the 30-day posture chip renders as absent, with a reason, and never as health', () => {
    expect(
      /const\s+deltaPct\s*=/.test(code),
      'the invented 30-day posture delta is back. Nothing stores the posture composite, and '
        + 'one of its inputs has no historical form, so no honest value exists to show.',
    ).toBe(false);

    // UX-008's convention: the reason reaches `title` AND `aria-label`, because a
    // tooltip alone is a hint a screen-reader user never receives (F2-209).
    expect(/title=\{postureDeltaHint\}/.test(code), 'the absent chip carries no tooltip reason').toBe(true);
    expect(/ariaLabel=\{postureDeltaHint\}/.test(code), 'the reason is a tooltip only — a screen-reader '
      + 'user never receives it (F2-209 / UX-008)').toBe(true);

    // Bilingual, and Arabic first — house rule 7.
    expect(/postureDeltaHint\s*=\s*isAr/.test(code), 'the reason is not bilingual').toBe(true);

    // Neutral, never a tone that reads as an all-clear. An invented figure invites
    // a question; an invented all-clear closes one (F2-197).
    const chip = /<Badge[^>]*postureDeltaHint[^>]*>/.exec(code)?.[0]
      ?? /<Badge[\s\S]{0,200}?postureDeltaHint[\s\S]{0,200}?>/.exec(code)?.[0] ?? '';
    expect(chip, 'the absent posture chip was not found').not.toBe('');
    expect(/tone="neutral"/.test(chip), `the absent chip carries a tone that reads as health: ${chip}`).toBe(true);
  });

  it('the delta arrow follows the sign, not the tone', () => {
    expect(
      /delta\.tone\s*===\s*'success'\s*\?\s*<TrendingDown/.test(code),
      'the arrow is chosen from the tone again. Tone and sign are independent, so this renders '
        + 'a DOWN arrow beside a plus sign for any metric where a rise is good — it did, on the '
        + 'Compliance-score tile: "↓ +3".',
    ).toBe(false);

    expect(
      /delta\.value\s*>=\s*0\s*\?\s*<TrendingUp/.test(code),
      'the arrow no longer follows the delta\'s own sign.',
    ).toBe(true);
  });

  it('the two-way Arabic greeting is deliberate and stays', () => {
    // Both branches return the same Arabic ON PURPOSE: Arabic greets in two parts
    // of the day where English greets in three. This pins the intent so the next
    // reader does not "restore symmetry" and make the primary language unnatural.
    const afternoon = /if \(h < 18\) return isAr \? '([^']+)'/.exec(code)?.[1];
    const evening = /return isAr \? '([^']+)' : 'Good evening'/.exec(code)?.[1];
    expect(afternoon, 'the afternoon greeting branch was not found').toBeTruthy();
    expect(evening, 'the evening greeting branch was not found').toBeTruthy();
    expect(afternoon, 'the two Arabic greetings diverged. Arabic has no distinct "good afternoon"; '
      + 'مساء الخير covers both. Read the comment above the branch before changing this.').toBe(evening);
    // ...and the English side must still make the distinction it legitimately has.
    expect(/'Good afternoon'/.test(code) && /'Good evening'/.test(code),
      'the English greeting lost a branch — the Arabic two-way form is not a reason to collapse it',
    ).toBe(true);
  });

  it('the comment stripper is load-bearing, and the file proves it', () => {
    // The fix's own comments quote every expression they retired, so the raw file
    // still contains them. If this ever stops being true the first leg is passing
    // for a weaker reason than it claims, and should be re-read.
    expect(/['"]Aymen['"]/.test(raw), 'the retired name is no longer quoted in the comment that '
      + 'records it — the stripping is then untested defence rather than load-bearing').toBe(true);
    expect(/const deltaPct = posture/.test(raw), 'the retired deltaPct expression is no longer quoted').toBe(true);
    // Assert the stripper's PROPERTIES, not a hand-counted run of spaces. Three
    // separate counts were miscounted by eye while writing this slice; a magic
    // number in an assertion is a guess unless it is derived (F2-459).
    const sample = "const x = 1; // delta: { value: -2 }\nconst y = 2;";
    const stripped = stripComments(sample);
    expect(stripped.length, 'offsets must survive stripping').toBe(sample.length);
    expect(stripped.split('\n').length, 'line numbers must survive stripping').toBe(2);
    expect(stripped, 'the comment body survived').not.toContain('value: -2');
    expect(stripped.startsWith('const x = 1;'), 'real code was eaten').toBe(true);
    expect(stripped.split('\n')[1], 'the next line was eaten').toBe('const y = 2;');
    // `(?<!:)` — a URL must not open a line comment.
    expect(stripComments("const u = 'https://x.test/a'; // note")).toContain("'https://x.test/a'");
  });
});
