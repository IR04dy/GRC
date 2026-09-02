import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * F2-441 — the IIA Standard 2500 management-response VERDICT must be recordable, and a finding
 * with no verdict must not be displayed as one management rejected.
 *
 * ## What was wrong
 *
 * `audit_lc.findings` carries four management-response columns. The server's `TransitionRequest`
 * declared three of them — the response text in both languages, and the author — and **not**
 * `mgmt_accepts`, while `FindingDto` returned it on the READ wire. Measured by ASM per owner type,
 * `setMgmtAccepts` had **zero** cross-class callers, and the column is NULL on all 20 findings in
 * both databases. So management's answer could be recorded and its **verdict** could not.
 *
 * 🔑 F2-215's two-of-N at 3 of 4, in one adjacent block of a request record: a reader who finds
 * three management-response fields there has no reason to ask about a fourth.
 *
 * ## Why both halves had to land together
 *
 * The badge was `tone={f.mgmtAccepts ? 'success' : 'warning'}` — two states over a three-state
 * fact. While nothing could write the column that was invisible; **making the verdict recordable is
 * exactly what would have started asserting a dispute** for every finding that still had none.
 * F2-203's rule: when you fix a filter, a selector or a fallback, assume something downstream has
 * never run.
 *
 * ## The assertion that matters most
 *
 * `== null`, never `!f.mgmtAccepts`. A **recorded disagreement is `false`**, and a truthiness test
 * would render it as "no verdict recorded" — hiding a real dispute inside the fix meant to expose
 * the un-answered ones. F2-195 records that exact mistake (`isNoData` must be `value == null`, not
 * `!value`, or a real 0 renders as absent).
 *
 * ⚠ Measured and deliberately NOT built: there is no form to enter a management response anywhere,
 * and both `transitionFinding` call sites pass no `mgmt` argument at all — so advancing a finding
 * through MGMT_RESPONSE records nothing. Who may respond, whether the author is the authenticated
 * principal (the server takes it from the BODY today, which F2-236 says is the wrong direction) and
 * whether a verdict is mandatory at that transition are product decisions, filed (F2-188).
 */
const SRC = join(process.cwd(), 'src');
const read = (p: string) => readFileSync(join(SRC, p), 'utf8');

/** One left-to-right alternation, never two passes — the recorded stripper landmine. */
const stripComments = (s: string) =>
  s.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, m => m.replace(/[^\n]/g, ' '));

describe('F2-441 — the management-response verdict', () => {
  it('the transition client sends the verdict, so the capability is reachable', () => {
    const code = stripComments(read('lib/auditLifecycle.ts'));
    const fn = code.slice(code.indexOf('export async function transitionFinding'));
    const body = fn.slice(0, fn.indexOf('\n}'));
    expect(body, 'the mgmt parameter must carry the verdict').toMatch(/accepts\s*\?\s*:\s*boolean/);
    expect(body, 'and it must reach the payload as mgmtAccepts').toMatch(/mgmtAccepts\s*:\s*mgmt\?\.accepts/);
    // F2-212: assert the ASSIGNMENT at the payload builder, not the field's presence on a type — a
    // builder may omit a field the type happily permits, and on this endpoint that omission is the
    // entire defect.
    expect(body, 'passing !!x would collapse "no answer" into "disagreed" one layer up')
      .not.toMatch(/mgmtAccepts\s*:\s*!!/);
  });

  it('the badge has three states and distinguishes them by identity, not truthiness', () => {
    const code = stripComments(read('pages/audit-lifecycle/AuditLifecycleTabs.tsx'));
    const block = code.slice(code.indexOf('alc-mgmt-response'));
    const head = block.slice(0, block.indexOf('alc-mgmt-head-author'));

    expect(head, 'a null verdict must read neutral, not warning')
      .toMatch(/f\.mgmtAccepts\s*==\s*null\s*\?\s*'neutral'/);
    expect(head, 'and it must say so rather than showing a dispute')
      .toMatch(/f\.mgmtAccepts\s*==\s*null\s*\?\s*t\('alcVerdictUnrecorded'\)/);
    expect(head, 'the two recorded verdicts must both still be distinguishable')
      .toMatch(/'success'/);
    expect(head, 'a recorded disagreement must still read as one').toMatch(/'warning'/);

    // The load-bearing one. `!f.mgmtAccepts` passes every assertion above and reintroduces the
    // defect for a RECORDED false.
    expect(head, 'a truthiness test cannot tell a recorded disagreement from no answer')
      .not.toMatch(/!f\.mgmtAccepts/);
    expect(head, 'and neither can String(!!…) on the data attribute')
      .not.toMatch(/String\(!!/);
    expect(head, 'the data attribute needs the third state too')
      .toMatch(/data-accepted=\{f\.mgmtAccepts\s*==\s*null\s*\?\s*'unset'/);
  });

  it('the hint reaches a screen reader as well as a tooltip', () => {
    // UX-008 built the em-dash + hint convention for Kpi with the text on BOTH title and
    // aria-label, and F2-209 records it as the rule. Badge offered only `title`, so a badge saying
    // "no verdict recorded" was a tooltip a screen-reader user never received.
    const badge = stripComments(read('components/ui/Badge.tsx'));
    expect(badge, 'Badge must be able to carry an accessible name').toMatch(/ariaLabel\?\s*:\s*string/);
    expect(badge, 'and pass it through').toMatch(/aria-label=\{ariaLabel\}/);

    const head = stripComments(read('pages/audit-lifecycle/AuditLifecycleTabs.tsx'));
    const block = head.slice(head.indexOf('alc-mgmt-response'));
    expect(block.slice(0, block.indexOf('alc-mgmt-head-author')),
      'the unrecorded-verdict badge must set both').toMatch(/ariaLabel=\{f\.mgmtAccepts\s*==\s*null/);
  });

  it('both languages declare the two new keys', () => {
    // House rule 7 — Arabic is the primary language, and F2-208 found this family of drift in the
    // Arabic half alone.
    for (const dict of ['translations/en.ts', 'translations/ar.ts']) {
      const s = read(dict);
      expect(s, `${dict} must declare alcVerdictUnrecorded`).toContain('alcVerdictUnrecorded:');
      expect(s, `${dict} must declare the hint`).toContain('alcVerdictUnrecordedHint:');
    }
  });

  it('the comment stripper is sound in both directions', () => {
    // This file's own assertions are about source text, and the retirement notes above quote the
    // shape they replaced — so a raw scan would read the epitaph as the corpse (F2-193).
    expect(stripComments("const a = 1; // f.mgmtAccepts ? 'success' : 'warning'"))
      .not.toContain("'success'");
    expect(stripComments("const url = 'https://x/y'; const b = 2;"))
      .toContain("'https://x/y'");
    // and the two-pass form is what corrupts a file whose // comment contains a /* (F2-198)
    const tricky = "// a /* b\nconst keep = 1;\n";
    expect(stripComments(tricky)).toContain('const keep = 1;');
  });
});
