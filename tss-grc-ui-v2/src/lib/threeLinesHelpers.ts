// Three Lines of Defense — pure helpers (no api / no React).
//
// Split out from {@code lib/threeLines.ts} so unit tests can import the
// helpers without dragging in the {@code import.meta.env}-using api
// client. Both the tests and the api client import from here.

export type LodBand = 'LINE_1' | 'LINE_2' | 'LINE_3' | 'BOARD';

/** IIA assurance-independence ordering — highest band wins.
 *  BOARD > LINE_3 > LINE_2 > LINE_1. */
export function effectiveBand(bands: LodBand[] | null | undefined): LodBand | null {
  if (!bands || bands.length === 0) return null;
  if (bands.includes('BOARD'))  return 'BOARD';
  if (bands.includes('LINE_3')) return 'LINE_3';
  if (bands.includes('LINE_2')) return 'LINE_2';
  if (bands.includes('LINE_1')) return 'LINE_1';
  return bands[0];
}

/** Decode the {@code lod_band} claim from a JWT access token. Returns []
 *  when the token is missing, expired, malformed, or doesn't carry the
 *  claim — the dashboard endpoint is still authoritative; this is only
 *  for the "my band" highlighter UX hint. */
export function decodeLodBands(jwt: string | null | undefined): LodBand[] {
  if (!jwt) return [];
  try {
    const parts = jwt.split('.');
    if (parts.length < 2) return [];
    // base64url decode — tolerate missing padding
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
    const payload = JSON.parse(decode(padded));
    const claim = payload?.lod_band;
    if (!Array.isArray(claim)) return [];
    return claim.filter((s: unknown): s is LodBand =>
      s === 'LINE_1' || s === 'LINE_2' || s === 'LINE_3' || s === 'BOARD');
  } catch {
    return [];
  }
}

/** Cross-runtime base64 decoder — uses `atob` in the browser, `Buffer`
 *  under Node. Keeps the helper test-runnable without a browser shim. */
function decode(s: string): string {
  if (typeof atob === 'function') return atob(s);
  if (typeof Buffer !== 'undefined') return Buffer.from(s, 'base64').toString('utf8');
  throw new Error('No base64 decoder available');
}
