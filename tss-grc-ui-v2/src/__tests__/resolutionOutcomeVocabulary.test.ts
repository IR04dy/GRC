import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { OUTCOME_TONE, DECIDED_OUTCOMES, isDecidedOutcome } from '../lib/committees';

/**
 * S7a — the frontend holds a **second copy** of the committee resolution outcome vocabulary, and
 * nothing was checking it.
 *
 * Same instrument as `changeLifecycle.test.ts`, which on its first run found a transition the
 * server allowed and the UI had no control for. Here the risk is sharper than a missing control:
 * before V349 the outcome list held four verdicts and no undecided value, and
 * `CommitteesPage.resolutionInitial` **defaulted the field to `APPROVED`** — so the only screen
 * that creates a resolution pre-filled a decision nobody had voted on. Creating it published
 * `COMMITTEE_RESOLUTION_APPROVED`, which `AR-146` turns into a 24-hour regulator disclosure clock
 * and `AR-147` into "file this in the board-resolutions register within 48 hours". Both rules are
 * enabled in the dev database.
 *
 * So these tests read the Java and assert the two sides agree — and, separately, that the form
 * cannot go back to pre-deciding a motion.
 */

const BACKEND = resolve(
  __dirname,
  '../../../tss-grc-backend/src/main/java/com/tss/grc/module/admin/committee/service/CommitteeDecisionAuthority.java',
);
const PAGE = resolve(__dirname, '../pages/committees/CommitteesPage.tsx');
const LIB = resolve(__dirname, '../lib/committees.ts');

function read(p: string): string {
  return readFileSync(p, 'utf8');
}

/** The five values `CommitteeDecisionAuthority.ALL` declares, in declaration order. */
function serverVocabulary(): string[] {
  const src = read(BACKEND);
  const m = /ALL\s*=[\s\S]*?List\.of\(([^)]*)\)/.exec(src);
  expect(m, 'could not find CommitteeDecisionAuthority.ALL — this test is broken, not the code')
    .toBeTruthy();
  return m![1]
    .split(',')
    .map(t => t.trim())
    .map(t => (t === 'PROPOSED' ? '"PROPOSED"' : t)) // ALL references the PROPOSED constant
    .map(t => t.replace(/^"/, '').replace(/"$/, ''))
    .filter(Boolean);
}

/** The values the server treats as decisions. */
function serverDecided(): string[] {
  const src = read(BACKEND);
  const m = /DECIDED\s*=\s*Set\.of\(([^)]*)\)/.exec(src);
  expect(m, 'could not find CommitteeDecisionAuthority.DECIDED').toBeTruthy();
  return m![1].split(',').map(t => t.trim().replace(/^"/, '').replace(/"$/, '')).filter(Boolean);
}

/** The union members of the FE `ResolutionOutcome` type. */
function feVocabulary(): string[] {
  const src = read(LIB);
  const m = /export type ResolutionOutcome\s*=\s*([^;]+);/.exec(src);
  expect(m, 'could not find the ResolutionOutcome type').toBeTruthy();
  return m![1].split('|').map(t => t.trim().replace(/^'/, '').replace(/'$/, '')).filter(Boolean);
}

describe('S7a — resolution outcome vocabulary, front end vs back end', () => {
  it('the FE union lists exactly what the server permits', () => {
    const server = serverVocabulary();
    const fe = feVocabulary();
    expect(server.length, 'anti-vacuity: the server vocabulary should have five values').toBe(5);
    expect(fe.slice().sort()).toEqual(server.slice().sort());
  });

  it('PROPOSED is in the vocabulary and is NOT a decision, on both sides', () => {
    // The whole of F2-89 in one assertion: an undecided state exists, and neither side counts it
    // as a verdict.
    expect(feVocabulary()).toContain('PROPOSED');
    expect(serverDecided()).not.toContain('PROPOSED');
    expect(DECIDED_OUTCOMES).not.toContain('PROPOSED' as never);
    expect(isDecidedOutcome('PROPOSED')).toBe(false);
    expect(isDecidedOutcome('APPROVED')).toBe(true);
    expect(isDecidedOutcome(null)).toBe(false);
  });

  it('DECIDED_OUTCOMES matches the server, so the two never disagree about a verdict', () => {
    expect([...DECIDED_OUTCOMES].sort()).toEqual(serverDecided().slice().sort());
  });

  it('every outcome has a badge tone — a new value cannot render undefined', () => {
    for (const o of feVocabulary()) {
      expect(Object.keys(OUTCOME_TONE), `OUTCOME_TONE is missing ${o}`).toContain(o);
    }
  });

  it('⚠ the resolution form must not pre-fill a verdict', () => {
    // The F2-89 defect, pinned where it lived. `resolutionInitial` seeds the create form; if its
    // outcome is ever a decided value again, raising a motion announces a decision nobody took.
    const src = read(PAGE);
    const initial = /const resolutionInitial\s*=\s*\{([\s\S]*?)\n\s*\};/.exec(src);
    expect(initial, 'could not find resolutionInitial in CommitteesPage').toBeTruthy();
    const outcome = /outcome:\s*'([A-Z_]+)'/.exec(initial![1]);
    expect(outcome, 'resolutionInitial no longer sets an outcome default').toBeTruthy();
    expect(
      isDecidedOutcome(outcome![1]),
      `the create form defaults outcome to ${outcome![1]}, a decided value. A motion must be `
        + 'raised undecided — F2-89: this defaulted to APPROVED, and creating it started a '
        + 'regulator disclosure clock (AR-146) and a board-register filing instruction (AR-147).',
    ).toBe(false);
  });

  it('PROPOSED is offered first in the outcome picker', () => {
    // Order is not cosmetic here: the first option is what a user reaches for, and the list used
    // to start at APPROVED.
    const src = read(PAGE);
    const m = /const OUTCOMES:\s*ResolutionOutcome\[\]\s*=\s*\[([^\]]*)\]/.exec(src);
    expect(m, 'could not find the OUTCOMES list').toBeTruthy();
    const first = m![1].split(',')[0].trim().replace(/^'/, '').replace(/'$/, '');
    expect(first).toBe('PROPOSED');
  });

  it('every outcome offered by the form has an Arabic label', () => {
    // House rule 7: user-visible values are bilingual. A missing key renders `undefined` in Arabic.
    const src = read(PAGE);
    const m = /const OUTCOME_AR:\s*Record<ResolutionOutcome, string>\s*=\s*\{([\s\S]*?)\n\s*\};/.exec(src);
    expect(m, 'could not find OUTCOME_AR').toBeTruthy();
    for (const o of feVocabulary()) {
      expect(m![1], `OUTCOME_AR is missing an Arabic label for ${o}`).toContain(`${o}:`);
    }
  });
});
