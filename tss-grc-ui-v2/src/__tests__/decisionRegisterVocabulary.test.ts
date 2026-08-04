import { describe, it, expect } from 'vitest';
import { en } from '../translations/en';
import { ar } from '../translations/ar';

/**
 * S8d / CE-14b — the decision register's on-screen copy.
 *
 * The register's honesty lives in two places: the two axes inside the file, and the sentence on the
 * governance tab that tells a reader those axes exist. If the sentence is missing in Arabic, an
 * Arabic-speaking auditor downloads a register whose "not recorded" rows they have no reason to
 * expect — which is the F2-107 shape again, in a second language.
 */
const KEYS = [
  'govDecisionRegisterTitle',
  'govDecisionRegisterSub',
  'govDecisionRegisterBasis',
  'govDecisionRegisterPdf',
  'govDecisionRegisterXlsx',
  'govDecisionRegisterFailed',
  'exporting',
] as const;

describe('CE-14b — decision register copy', () => {
  it('every key exists in BOTH dictionaries', () => {
    for (const k of KEYS) {
      expect(en[k as keyof typeof en], `en.${k}`).toBeTruthy();
      expect(ar[k as keyof typeof ar], `ar.${k}`).toBeTruthy();
    }
  });

  it('the Arabic copy is actually Arabic, not an English placeholder', () => {
    // A copied English string passes a "key exists" check and fails a reader. Format markers like
    // "PDF" and "Excel" are legitimately Latin, so test for the presence of Arabic script instead
    // of the absence of Latin.
    const arabic = /[؀-ۿ]/;
    for (const k of KEYS) {
      expect(arabic.test(String(ar[k as keyof typeof ar])), `ar.${k} has no Arabic script`).toBe(true);
    }
  });

  it('the basis sentence names both axes rather than only the happy one', () => {
    expect(en.govDecisionRegisterBasis).toMatch(/recorded/i);
    expect(en.govDecisionRegisterBasis).toMatch(/inferred/i);
    // "not recorded" must appear verbatim: it is what the file prints, and the two must agree.
    expect(en.govDecisionRegisterBasis).toMatch(/not recorded/i);
  });

  it('the two download labels are distinguishable', () => {
    expect(en.govDecisionRegisterPdf).not.toEqual(en.govDecisionRegisterXlsx);
    expect(ar.govDecisionRegisterPdf).not.toEqual(ar.govDecisionRegisterXlsx);
  });
});
