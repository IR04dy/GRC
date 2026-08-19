import { describe, it, expect } from 'vitest';
import { parentRiskTooltip } from '../lib/riskScenarios';

/**
 * F2-245 — the register's risk column says what it can, and discloses what it cannot.
 *
 * ⚠ This predicate exists because the first draft of the cell rendered
 * `title={r.riskCode}` under a comment claiming the tooltip stated the Arabic caveat.
 * It did not — found in the browser after every gate was green. F2-184's shape: a
 * comment describing a behaviour that does not exist is worse than no comment, because
 * it answers the question a reader was about to ask.
 */
describe('parentRiskTooltip', () => {
  const row = { riskCode: 'b7b5a877-3f7b-4580-8628-1e7fb32c04d4', riskTitleEn: 'Non-Compliance with NCA ECC Controls' };

  it('carries the reference in English, and no language caveat', () => {
    const t = parentRiskTooltip(row, 'en');
    expect(t).toContain(row.riskCode);
    // The English UI is showing an English name — there is nothing to disclose.
    expect(t).not.toMatch(/[؀-ۿ]/);
  });

  it('discloses in Arabic that this register stores the risk name in English only', () => {
    const t = parentRiskTooltip(row, 'ar');
    expect(t).toContain(row.riskCode);
    // house rule 7: `risk.scenarios` has no `risk_title_ar` column, and the operator
    // reading an English name amid an Arabic UI is owed the reason.
    expect(t).toMatch(/[؀-ۿ]/);
    expect(t).toContain('بالإنجليزية فقط');
  });

  it('says nothing at all when there is no parent risk', () => {
    // Not an empty string: an empty `title` attribute is a tooltip that opens blank,
    // which reads as a missing value rather than as an absent parent.
    expect(parentRiskTooltip({ riskCode: undefined, riskTitleEn: undefined }, 'ar')).toBeUndefined();
    expect(parentRiskTooltip({ riskCode: undefined, riskTitleEn: undefined }, 'en')).toBeUndefined();
  });

  it('still carries the caveat when the reference is missing but a name is present', () => {
    const t = parentRiskTooltip({ riskCode: undefined, riskTitleEn: 'Some risk' }, 'ar');
    expect(t).toContain('بالإنجليزية فقط');
  });
});
