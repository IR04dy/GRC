import { describe, it, expect } from 'vitest';
import { en } from '../translations/en';
import { ar } from '../translations/ar';
import { COLLISION_TONE, showsOutage, FSC_DEFAULT_DAYS } from '../pages/change/ForwardSchedulePanel';
import type { ScheduleEntryDto } from '../lib/api';

/**
 * S8g / CE-8 — the frontend must not render an outage the database would refuse, and must not paint
 * two overlapping windows the same colour as two simultaneous outages.
 */
function entry(over: Partial<ScheduleEntryDto> = {}): ScheduleEntryDto {
  return {
    changeId: 9, changeCode: 'CHG-2025-0009',
    titleEn: 'Patch the portal', titleAr: 'ترقيع البوابة',
    changeType: 'NORMAL', riskLevel: 'HIGH', status: 'SCHEDULED',
    windowStart: '2026-09-22T22:00:00', windowEnd: '2026-09-23T06:00:00',
    outageExpected: true, outageMinutes: 90, servicesAffected: 'Customer portal',
    outageDeclared: true, freezeCode: null,
    ...over,
  };
}

const REASONS = ['WINDOW_OVERLAP', 'OUTAGE_OVERLAP', 'INSIDE_FREEZE_WINDOW'] as const;

describe('CE-8 — what counts as a declared outage on screen', () => {
  it('renders an outage only when the declaration is COMPLETE', () => {
    expect(showsOutage(entry())).toBe(true);
    // ⚠ The raw flag is not sufficient. ck_change_pso refuses outage_expected = 1 without a positive
    // duration and named services, so rendering on the flag alone would show the user an outage the
    // database would not accept — the screen contradicting the API, and the screen is what is believed.
    expect(showsOutage(entry({ outageExpected: true, outageDeclared: false }))).toBe(false);
  });

  it('an incomplete declaration is never shown as an outage', () => {
    expect(showsOutage(entry({ outageMinutes: null, outageDeclared: false }))).toBe(false);
    expect(showsOutage(entry({ servicesAffected: null, outageDeclared: false }))).toBe(false);
  });
});

describe('CE-8 — collision tones', () => {
  it('two simultaneous outages outrank two overlapping windows', () => {
    // Overlapping windows are often fine. Two overlapping DECLARED outages is a business-visible
    // incident being scheduled on purpose, and one colour for both loses the only useful distinction.
    expect(COLLISION_TONE.OUTAGE_OVERLAP).toBe('danger');
    expect(COLLISION_TONE.WINDOW_OVERLAP).toBe('warning');
    expect(COLLISION_TONE.OUTAGE_OVERLAP).not.toBe(COLLISION_TONE.WINDOW_OVERLAP);
  });

  it('a freeze violation is a danger, not a warning', () => {
    expect(COLLISION_TONE.INSIDE_FREEZE_WINDOW).toBe('danger');
  });

  it('every reason has a tone — an unstyled row renders neutral and reads as fine', () => {
    for (const r of REASONS) expect(COLLISION_TONE[r]).toBeDefined();
  });
});

describe('CE-8 — house rule 7: every code renders in both languages', () => {
  const keys = [
    ...REASONS.map(r => `fscCollision_${r}`),
    'fscTitle', 'fscSub', 'fscOutages', 'fscCollisions', 'fscInFreeze',
    'fscUnscheduled', 'fscUnscheduledHint', 'fscNeverPublished', 'fscNeverPublishedHint',
    'fscPublishedOn', 'fscStale', 'fscVerifyDigest', 'fscDigestOk', 'fscDigestMismatch',
    'fscPublish', 'fscPublishHint', 'fscNothingToPublish', 'fscNoOutage', 'fscColOutage',
    'fscEmptyTitle', 'fscEmptyHint', 'fscCollisionsTitle',
  ];

  it('English and Arabic both carry every key', () => {
    const e = en as unknown as Record<string, string>;
    const a = ar as unknown as Record<string, string>;
    for (const k of keys) {
      expect(e[k], `en.${k}`).toBeTruthy();
      expect(a[k], `ar.${k}`).toBeTruthy();
    }
  });

  it('the Arabic is a translation, not the English copied across', () => {
    const e = en as unknown as Record<string, string>;
    const a = ar as unknown as Record<string, string>;
    for (const k of keys) expect(a[k], `ar.${k} is identical to en.${k}`).not.toBe(e[k]);
  });

  it('"never published" and "stale" are different strings', () => {
    // A tenant that never issued a schedule and one whose January schedule expired have different
    // problems, and one message for both hides which.
    const e = en as unknown as Record<string, string>;
    expect(e.fscNeverPublished).not.toBe(e.fscStale);
    expect(e.fscNeverPublishedHint).toMatch(/no record of what the business was told/i);
  });

  it('the unscheduled hint says an empty collision list does not cover them', () => {
    // The sentence that stops a clean list over an incomplete population reading as a clean schedule.
    expect((en as unknown as Record<string, string>).fscUnscheduledHint)
      .toMatch(/does not cover them/i);
  });

  it('the digest-mismatch message tells the reader not to trust the copy', () => {
    expect((en as unknown as Record<string, string>).fscDigestMismatch)
      .toMatch(/untrustworthy|does NOT match/);
  });

  it('the empty state does not claim there is nothing to worry about', () => {
    // "Nothing scheduled" is a fact about the window, not a clean bill of health — the hint says why
    // a change might be missing from it.
    expect((en as unknown as Record<string, string>).fscEmptyHint).toMatch(/once they carry a window/i);
  });
});

describe('CE-8 — the default window', () => {
  it('matches the server default so the published window is the one on screen', () => {
    // If these drift, an operator publishes a different period from the one they were reading.
    expect(FSC_DEFAULT_DAYS).toBe(90);
  });
});
