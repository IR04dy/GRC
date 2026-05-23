// Sprint 11 — Three Lines of Defense — frontend lib smoke tests.
//
// Run modes:
//   node --import tsx --test src/__tests__/threeLines.test.ts
//   npx tsx --test src/__tests__/threeLines.test.ts
//   (or any project-standard runner once one is added)
//
// The tss-grc-ui-v2 project has no test framework installed yet — these
// tests use the Node 18+ built-in `node:test` + `node:assert/strict`
// modules to stay zero-dep. They are still type-checked by `npm run
// typecheck` (tsc -b --noEmit), so a broken refactor in
// {@code lib/threeLines.ts} surfaces in CI even without a test runner.

import { describe, it } from 'node:test';
import { strict as assert } from 'node:assert';
// Import directly from the pure-helper module — avoids dragging in
// {@code lib/api.ts} which reads {@code import.meta.env} at evaluation
// time and is therefore Vite-only.
import { decodeLodBands, effectiveBand, type LodBand } from '../lib/threeLinesHelpers';

// ─── effectiveBand ─────────────────────────────────────────────────────

describe('effectiveBand — IIA assurance-independence precedence', () => {
  it('returns null for empty/null input', () => {
    assert.equal(effectiveBand(null), null);
    assert.equal(effectiveBand(undefined), null);
    assert.equal(effectiveBand([]), null);
  });

  it('returns the only band when a user has a single role', () => {
    assert.equal(effectiveBand(['LINE_1']), 'LINE_1');
    assert.equal(effectiveBand(['LINE_2']), 'LINE_2');
    assert.equal(effectiveBand(['LINE_3']), 'LINE_3');
    assert.equal(effectiveBand(['BOARD']),  'BOARD');
  });

  it('LINE_3 beats LINE_2 beats LINE_1 (assurance independence)', () => {
    assert.equal(effectiveBand(['LINE_1', 'LINE_2']), 'LINE_2');
    assert.equal(effectiveBand(['LINE_2', 'LINE_3']), 'LINE_3');
    assert.equal(effectiveBand(['LINE_1', 'LINE_3']), 'LINE_3');
    assert.equal(effectiveBand(['LINE_1', 'LINE_2', 'LINE_3']), 'LINE_3');
  });

  it('BOARD outranks LINE_3 (Audit Committee chair > IA)', () => {
    assert.equal(effectiveBand(['LINE_3', 'BOARD']), 'BOARD');
    assert.equal(effectiveBand(['LINE_1', 'LINE_2', 'LINE_3', 'BOARD']), 'BOARD');
  });
});

// ─── decodeLodBands ───────────────────────────────────────────────────

describe('decodeLodBands — JWT claim decoder', () => {
  it('returns [] for null/undefined/empty token', () => {
    assert.deepEqual(decodeLodBands(null), []);
    assert.deepEqual(decodeLodBands(undefined), []);
    assert.deepEqual(decodeLodBands(''), []);
  });

  it('returns [] for malformed tokens', () => {
    assert.deepEqual(decodeLodBands('not.a.jwt.malformed'), []);
    assert.deepEqual(decodeLodBands('only-one-part'), []);
  });

  it('decodes a valid lod_band claim with multiple bands', () => {
    const bands: LodBand[] = ['LINE_1', 'BOARD'];
    const token = makeJwt({ lod_band: bands, sub: '42' });
    assert.deepEqual(decodeLodBands(token), bands);
  });

  it('drops unknown band strings while keeping the valid ones', () => {
    const token = makeJwt({ lod_band: ['LINE_2', 'JUNK', 'LINE_3'] });
    assert.deepEqual(decodeLodBands(token), ['LINE_2', 'LINE_3']);
  });

  it('returns [] when the claim is missing or not an array', () => {
    assert.deepEqual(decodeLodBands(makeJwt({ sub: '1' })), []);
    assert.deepEqual(decodeLodBands(makeJwt({ lod_band: 'LINE_1' })), []);
    assert.deepEqual(decodeLodBands(makeJwt({ lod_band: null })), []);
  });
});

// ─── Helpers ──────────────────────────────────────────────────────────

function makeJwt(payload: Record<string, unknown>): string {
  const header  = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body    = base64Url(JSON.stringify(payload));
  // Signature is irrelevant — decodeLodBands only inspects the body. The
  // backend's HS256 verification is what protects the actual token.
  return `${header}.${body}.signature`;
}

function base64Url(s: string): string {
  // Mirror the encoding the backend uses (RFC 7515). Use Buffer when in
  // Node; fall back to btoa in the unlikely case the test runs in jsdom.
  const b64 = typeof Buffer !== 'undefined'
    ? Buffer.from(s, 'utf8').toString('base64')
    : btoa(s);
  return b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
