// Slice 9-F — Mobile Crisis Room — frontend lib smoke tests.
//
// Run modes:
//   node --import tsx --test src/__tests__/mobileCrisis.test.ts
//   npx tsx --test src/__tests__/mobileCrisis.test.ts
//
// Same zero-dep pattern as src/__tests__/threeLines.test.ts — uses the
// Node 18+ built-in `node:test` + `node:assert/strict`. These tests are
// still type-checked by `npm run typecheck`, so a broken refactor of
// {@code lib/mobileCrisis.ts} surfaces in CI.

import { describe, it } from 'vitest';
import { strict as assert } from 'node:assert';
// Import from the pure-helper module — avoids dragging in lib/api.ts
// which reads import.meta.env at evaluation time (Vite-only). Mirrors the
// pattern used by src/__tests__/threeLines.test.ts.
import {
  toEasternArabicDigits,
  formatCountdown,
  formatRelativeAgo,
  urlBase64ToUint8Array,
} from '../lib/mobileCrisisHelpers';

// ─── toEasternArabicDigits ─────────────────────────────────────────

describe('toEasternArabicDigits — Eastern Arabic numerals for Saudi-native chips', () => {
  it('maps all ten digits', () => {
    assert.equal(toEasternArabicDigits('0123456789'), '٠١٢٣٤٥٦٧٨٩');
  });
  it('accepts numbers as input', () => {
    assert.equal(toEasternArabicDigits(47), '٤٧');
  });
  it('preserves non-digit characters', () => {
    assert.equal(toEasternArabicDigits('47 hrs'), '٤٧ hrs');
  });
});

// ─── formatCountdown ───────────────────────────────────────────────

describe('formatCountdown — Saudi-native phrasing of "N hours/minutes remaining"', () => {
  it('returns em-dash for null/undefined', () => {
    assert.equal(formatCountdown(null, 'ar'),      '—');
    assert.equal(formatCountdown(undefined, 'ar'), '—');
  });
  it('returns breach text for ≤ 0', () => {
    assert.equal(formatCountdown(0, 'ar'),   'تم تجاوز المهلة');
    assert.equal(formatCountdown(-5, 'ar'),  'تم تجاوز المهلة');
    assert.equal(formatCountdown(0, 'en'),   'Deadline breached');
  });
  it('formats minutes when < 1 hour remaining (AR with Eastern digits)', () => {
    assert.equal(formatCountdown(12, 'ar'), '١٢ دقيقة متبقية');
  });
  it('formats hours when ≥ 1 hour remaining', () => {
    assert.equal(formatCountdown(47 * 60, 'ar'), '٤٧ ساعة متبقية');
    assert.equal(formatCountdown(47 * 60, 'en'), '47 hours remaining');
  });
});

// ─── formatRelativeAgo ─────────────────────────────────────────────

describe('formatRelativeAgo — Saudi-native "منذ X" phrasing', () => {
  it('returns em-dash for missing input', () => {
    assert.equal(formatRelativeAgo(null,      'ar'), '—');
    assert.equal(formatRelativeAgo(undefined, 'ar'), '—');
    assert.equal(formatRelativeAgo('garbage', 'ar'), '—');
  });
  it('returns "just now" for < 1 minute', () => {
    const recent = new Date(Date.now() - 10_000).toISOString();
    assert.equal(formatRelativeAgo(recent, 'ar'), 'الآن');
    assert.equal(formatRelativeAgo(recent, 'en'), 'just now');
  });
  it('formats minutes ago with Eastern digits in Arabic', () => {
    const t = new Date(Date.now() - 12 * 60_000).toISOString();
    assert.equal(formatRelativeAgo(t, 'ar'), 'منذ ١٢ دقيقة');
    assert.equal(formatRelativeAgo(t, 'en'), '12 min ago');
  });
  it('formats hours ago for ≥ 60 minutes', () => {
    const t = new Date(Date.now() - 3 * 3600_000).toISOString();
    assert.equal(formatRelativeAgo(t, 'ar'), 'منذ ٣ ساعة');
  });
});

// ─── urlBase64ToUint8Array ─────────────────────────────────────────

describe('urlBase64ToUint8Array — VAPID public key decoder', () => {
  it('decodes a known base64-url string', () => {
    // base64-url("hello") = "aGVsbG8" (no padding, '-' / '_' substituted)
    const out = urlBase64ToUint8Array('aGVsbG8');
    assert.deepEqual(Array.from(out), [104, 101, 108, 108, 111]);
  });
  it('handles the - / _ → + / / substitution', () => {
    // base64("subjects?") → "c3ViamVjdHM/", base64-url replaces '/' with '_'
    const out = urlBase64ToUint8Array('c3ViamVjdHM_');
    assert.equal(out.length, 9);
    assert.equal(out[0], 's'.charCodeAt(0));
    assert.equal(out[8], '?'.charCodeAt(0));
  });
});
