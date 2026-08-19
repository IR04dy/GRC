import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * F2-246 — a picker's placeholder may only promise what that instance offers.
 *
 * `SearchableSelect` hard-coded its search placeholder as *"Search or create new…"* /
 * *"ابحث أو أنشئ جديداً…"* regardless of whether `onCreate` was wired, while the create row
 * itself is correctly gated on `!!onCreate`. Measured across the app: **17 of 19**
 * `select-searchable` fields wire no `onCreate`, so almost every picker in the platform
 * invited an operator to create a record it cannot create.
 *
 * Found in the browser after every backend gate was green — the eighth consecutive slice in
 * which a text-level claim surfaced there rather than in a scan, because a string that is
 * only wrong *in relation to a sibling prop* is invisible to a code-scoped detector.
 *
 * ⚠ This is asserted structurally rather than by rendering, so it cannot pass because a
 * fixture happened to wire `onCreate`: the file itself must show the promise depending on
 * the prop, in BOTH languages (house rule 7 — F2-208 found exactly this drift in the Arabic
 * half only).
 */
describe('SearchableSelect — the placeholder promises only what the instance offers', () => {
  const SRC = readFileSync(
    join(__dirname, '..', 'components', 'ui', 'SearchableSelect.tsx'), 'utf8');

  // The prose above deliberately quotes the retired string to explain the fix, so the
  // assertions read the JSX only. Prose about code, stored as data, satisfies a naive
  // scan (F2-193, F2-202, F2-205).
  const code = SRC.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, '');

  it('the create promise is conditional on onCreate, not unconditional', () => {
    const unconditional = /placeholder=\{\s*isAr\s*\?\s*'ابحث أو أنشئ جديداً…'/;
    expect(unconditional.test(code),
      'the search placeholder must not promise "create new" when no onCreate is wired')
      .toBe(false);

    // Assert the SHAPE of the dependency, not merely that the word `onCreate` appears
    // somewhere — it appears many times in this file.
    const conditional = /placeholder=\{\s*onCreate\s*\?/;
    expect(conditional.test(code),
      'the placeholder must be chosen by whether onCreate is wired').toBe(true);
  });

  it('both languages get a search-only placeholder, and both keep the create one', () => {
    // Arabic is the platform's primary language, so the two halves are asserted
    // separately: F2-208 found a label/predicate drift that existed only in the Arabic.
    expect(code).toContain("'ابحث…'");
    expect(code).toContain("'Search…'");
    expect(code).toContain("'ابحث أو أنشئ جديداً…'");
    expect(code).toContain("'Search or create new…'");
  });

  it('the create ROW is still gated on onCreate — the affordance was never the defect', () => {
    // The fix must not be mistaken for removing the capability. Only the promise moved.
    expect(/const showCreate\s*=\s*!!onCreate/.test(code)).toBe(true);
  });

  it('self-test: the detector fires on the shape it was written for', () => {
    const preFix = "placeholder={isAr ? 'ابحث أو أنشئ جديداً…' : 'Search or create new…'}";
    expect(/placeholder=\{\s*isAr\s*\?\s*'ابحث أو أنشئ جديداً…'/.test(preFix)).toBe(true);
    expect(/placeholder=\{\s*onCreate\s*\?/.test(preFix)).toBe(false);
  });
});
