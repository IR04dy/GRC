import { describe, it, expect } from 'vitest';
import { STATUS_KEY, STATUS_TONE } from '../lib/labels';

/**
 * F2-357 — every status that has a localised LABEL must also have a badge TONE.
 *
 * <p>`statusLabel` maps a status to an i18n key via {@link STATUS_KEY}; badges render
 * `STATUS_TONE[status] ?? 'neutral'`. When a status is added to STATUS_KEY (so it localises)
 * but not to STATUS_TONE, its badge silently falls back to a grey/neutral tone — the label is
 * right, the colour is wrong. F2-211 did exactly that for the five PDPL breach statuses
 * (NOTIFIED / INVESTIGATING / INVESTIGATED / ESCALATED / REMEDIATED): they localised correctly
 * on the privacy.data_breaches register (PrivacyPage) and PdplBreachConsole but rendered grey —
 * an ESCALATED breach visually indistinguishable from a CLOSED one, on a PDPL Article 20 register.
 *
 * <p>This is the "fixed the label, left the lookup" half-fix the programme keeps closing
 * (F2-205 / F2-211). The completeness leg is the durable invariant; the second leg pins the five
 * breach tones so a regression to grey fails here.
 */
describe('F2-357 — STATUS_TONE is complete for every labellable status', () => {

  it('every STATUS_KEY status also has a STATUS_TONE entry (no silent neutral fallback)', () => {
    const missing = Object.keys(STATUS_KEY).filter(s => STATUS_TONE[s] === undefined);
    expect(
      missing,
      `These statuses have a localised label but no badge tone, so they render grey via the `
      + `'?? \\'neutral\\'' fallback: ${missing.join(', ')}. Add each to STATUS_TONE in `
      + `src/lib/labels.ts (danger = urgent/bad, warning = active, info = in-progress, success = done).`,
    ).toEqual([]);
  });

  it('the five PDPL breach statuses (F2-211) carry their intended non-neutral tones', () => {
    // Regression pin: a revert to a grey/neutral badge for an escalated or remediated breach
    // must fail here. Tones follow the platform convention used across STATUS_TONE.
    expect(STATUS_TONE.NOTIFIED).toBe('info');
    expect(STATUS_TONE.INVESTIGATING).toBe('warning');
    expect(STATUS_TONE.INVESTIGATED).toBe('info');
    expect(STATUS_TONE.ESCALATED).toBe('danger');
    expect(STATUS_TONE.REMEDIATED).toBe('success');
  });

  it('F2-359 — RESOLVED (the recovery-phase incident terminal) carries its label key and success tone', () => {
    // RESOLVED is the status IncidentService.legalStatus actually writes
    // (OPEN | INVESTIGATING | RESOLVED | CLOSED; RECOVERY/POST_INCIDENT map to it). It was absent
    // from BOTH maps while the phantom RECOVERED (0 rows, not a legal status) carried both, so a
    // RESOLVED incident rendered a grey, raw-token badge. A revert to that state must fail here.
    // (The completeness leg above cannot catch it: absent from STATUS_KEY, RESOLVED was outside
    // that leg's population — STATUS_KEY ⊆ STATUS_TONE says nothing about a status in neither map.)
    expect(STATUS_KEY.RESOLVED).toBe('statusResolved');
    expect(STATUS_TONE.RESOLVED).toBe('success');
  });

  it('the maps are non-trivial (anti-vacuity — a broken import cannot pass this file)', () => {
    // If the import rots to {} both legs above pass vacuously; this floor is the guard.
    expect(Object.keys(STATUS_KEY).length).toBeGreaterThanOrEqual(15);
    expect(Object.keys(STATUS_TONE).length).toBeGreaterThanOrEqual(15);
  });
});
