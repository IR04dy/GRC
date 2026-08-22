import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { PdplApplicabilityBadge } from '../components/privacy/PdplApplicabilityBadge';
import type { RegulatoryTemplate } from '../lib/api';

/**
 * F2-281 — two rules the backend gate cannot see, because both are about what a
 * colour and a placement claim.
 *
 * The finding: `GET /api/v1/incidents/{id}/regulatory-templates` renders the PDPL
 * Article 20 breach notification an operator files with the Saudi Data Office,
 * and that body omitted the number of affected data subjects and the categories
 * of personal data affected while `cyber.incidents` stored both on the same row.
 * The classification is now on the wire as `piiClassification`, and this badge is
 * where an operator sees it.
 *
 * What is asserted here is the part a wire contract cannot reach:
 *
 *  1. `NOT_CLASSIFIED` must be **neutral**, never `success`. It means nobody has
 *     assessed the incident yet — a green all-clear on an unassessed statutory
 *     clock closes a question that an absent figure would merely raise (F2-197).
 *  2. It renders on the Article 20 card **only**. `applicabilityHint` is prose the
 *     reader judges, and that is correct for SAMA and CITC, which the platform
 *     cannot answer; presenting a stored classification beside those would imply
 *     it settles their applicability too.
 *
 * Both languages, separately, because F2-208 found this class of drift in the
 * Arabic half alone and Arabic is the primary language (house rule 7).
 */
function tmpl(over: Partial<RegulatoryTemplate> = {}): RegulatoryTemplate {
  return {
    code: 'DATA_OFFICE',
    titleEn: 'Saudi Data Office — 72-hour PDPL Article 20 Breach Notification',
    titleAr: 'إخطار خرق البيانات — مكتب البيانات السعودي (72 ساعة)',
    deadlineHours: 72, hoursSinceDiscovery: 3354, hoursRemaining: -3282,
    overdue: true,
    submissionChannel: 'Submit via Saudi Data Office portal once available',
    applicabilityHint: 'Personal data breach — PDPL Article 20 mandates 72-hour notification',
    prefilledBody: 'BREACH NOTIFICATION — PDPL Article 20',
    piiClassification: 'NOT_CLASSIFIED',
    ...over,
  };
}

describe('F2-281 — the PDPL applicability badge discloses, and never gives an all-clear', () => {
  it('renders CONFIRMED as danger in both languages', () => {
    for (const isAr of [false, true]) {
      const { container } = render(
        <PdplApplicabilityBadge tmpl={tmpl({ piiClassification: 'CONFIRMED' })} isAr={isAr} />);
      const html = container.innerHTML;
      expect(html, `CONFIRMED must not be neutral (isAr=${isAr})`).toContain('danger');
      expect(html).not.toContain('success');
      expect(container.textContent).toBe(
        isAr ? 'بيانات شخصية: مؤكَّد' : 'Personal data: confirmed');
    }
  });

  it('renders NOT_CLASSIFIED as NEUTRAL and never as success, in both languages', () => {
    for (const isAr of [false, true]) {
      const { container } = render(<PdplApplicabilityBadge tmpl={tmpl()} isAr={isAr} />);
      const html = container.innerHTML;
      // The whole point: "nobody has assessed this" is not "there is nothing to
      // assess". A green badge here would be an invented all-clear on a statutory
      // clock, which is strictly worse than an absent one (F2-197).
      expect(html, `an unassessed classification may not read as success (isAr=${isAr})`)
        .not.toContain('success');
      expect(html).toContain('neutral');
      expect(container.textContent).toBe(
        isAr ? 'بيانات شخصية: لم يُقيَّم بعد' : 'Personal data: not yet assessed');
    }
  });

  it('says "not yet assessed" rather than asserting an absence of personal data', () => {
    const { container } = render(<PdplApplicabilityBadge tmpl={tmpl()} isAr={false} />);
    // "No personal data" would be a claim about the incident; the platform only
    // knows that nobody has classified it.
    expect(container.textContent?.toLowerCase()).not.toContain('no personal data');
    expect(container.textContent?.toLowerCase()).not.toContain('none');
  });

  it('renders nothing for the four regulators whose applicability the platform cannot answer', () => {
    for (const code of ['NCA_HEY', 'CITC', 'SAMA', 'HRSD'] as const) {
      const { container } = render(
        <PdplApplicabilityBadge tmpl={tmpl({ code, piiClassification: 'CONFIRMED' })}
                                isAr={false} />);
      expect(container.innerHTML, `${code} must not carry a PDPL classification badge`).toBe('');
    }
  });
});
