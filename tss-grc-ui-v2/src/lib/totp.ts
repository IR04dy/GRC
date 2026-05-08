// TOTP (RFC 6238) — pure-Web-Crypto implementation.
//
// Used by the MFA enrolment + challenge flow. No external libraries — the
// browser's SubtleCrypto handles HMAC-SHA1, the rest is straight bit-twiddling.
//
// Defaults: SHA1 digest, 30-second time step, 6-digit code (matches Google
// Authenticator / Microsoft Authenticator / Authy / Aegis).
//
// Why SHA1 (not SHA256/512): RFC 6238 still defaults to SHA1 because every
// authenticator app supports it; SHA256 is opt-in and many apps don't read
// the `algorithm` parameter from the otpauth URI.

const DIGITS = 6;
const PERIOD_SECONDS = 30;

// ─── Base32 (RFC 4648, no padding) ──────────────────────────────────────────
//
// TOTP secrets are exchanged in Base32 because it's case-insensitive and
// safe for QR codes. We need encode (random secret → user-readable string)
// and decode (string → bytes for HMAC).

const BASE32_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

export function base32Encode(bytes: Uint8Array): string {
  let bits = 0;
  let value = 0;
  let out = '';
  for (let i = 0; i < bytes.length; i++) {
    value = (value << 8) | bytes[i];
    bits += 8;
    while (bits >= 5) {
      out += BASE32_ALPHABET[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += BASE32_ALPHABET[(value << (5 - bits)) & 31];
  return out;
}

export function base32Decode(input: string): Uint8Array {
  const cleaned = input.replace(/=+$/, '').toUpperCase().replace(/\s+/g, '');
  const out: number[] = [];
  let bits = 0;
  let value = 0;
  for (let i = 0; i < cleaned.length; i++) {
    const idx = BASE32_ALPHABET.indexOf(cleaned[i]);
    if (idx < 0) throw new Error(`Invalid base32 character: ${cleaned[i]}`);
    value = (value << 5) | idx;
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return new Uint8Array(out);
}

// ─── Random secret ──────────────────────────────────────────────────────────

/** Generate a cryptographically random 160-bit (20-byte) secret in Base32. */
export function generateSecret(): string {
  const bytes = new Uint8Array(20);
  crypto.getRandomValues(bytes);
  return base32Encode(bytes);
}

// ─── otpauth:// URI ────────────────────────────────────────────────────────
//
// QR-friendly URI per
// https://github.com/google/google-authenticator/wiki/Key-Uri-Format

export interface OtpAuthParams {
  secret: string;
  /** Account name shown in the authenticator app — usually email or username. */
  account: string;
  /** Issuer name shown in the authenticator app. */
  issuer: string;
  digits?: number;
  period?: number;
  algorithm?: 'SHA1' | 'SHA256' | 'SHA512';
}

export function buildOtpAuthUri(p: OtpAuthParams): string {
  const issuer = encodeURIComponent(p.issuer);
  const account = encodeURIComponent(p.account);
  const params = new URLSearchParams();
  params.set('secret', p.secret);
  params.set('issuer', p.issuer);
  if (p.digits) params.set('digits', String(p.digits));
  if (p.period) params.set('period', String(p.period));
  if (p.algorithm) params.set('algorithm', p.algorithm);
  return `otpauth://totp/${issuer}:${account}?${params.toString()}`;
}

// ─── HMAC-SHA1 → TOTP code ─────────────────────────────────────────────────

async function hmacSha1(key: Uint8Array, message: Uint8Array): Promise<Uint8Array> {
  const cryptoKey = await crypto.subtle.importKey(
    'raw',
    // SubtleCrypto requires a fresh ArrayBuffer-backed view — copy bytes
    // into an explicit ArrayBuffer to avoid SharedArrayBuffer rejections.
    key.buffer.slice(key.byteOffset, key.byteOffset + key.byteLength) as ArrayBuffer,
    { name: 'HMAC', hash: { name: 'SHA-1' } },
    false,
    ['sign'],
  );
  const sig = await crypto.subtle.sign(
    'HMAC',
    cryptoKey,
    message.buffer.slice(message.byteOffset, message.byteOffset + message.byteLength) as ArrayBuffer,
  );
  return new Uint8Array(sig);
}

/** RFC 6238 dynamic truncation. */
function dynamicTruncate(hmac: Uint8Array): number {
  const offset = hmac[hmac.length - 1] & 0x0f;
  return ((hmac[offset] & 0x7f) << 24)
       | ((hmac[offset + 1] & 0xff) << 16)
       | ((hmac[offset + 2] & 0xff) << 8)
       |  (hmac[offset + 3] & 0xff);
}

/** Compute the TOTP code for a given counter (counter = floor(unix / period)). */
async function totpForCounter(secret: string, counter: number, digits = DIGITS): Promise<string> {
  const key = base32Decode(secret);
  const buf = new ArrayBuffer(8);
  const view = new DataView(buf);
  // 8-byte big-endian counter — JS numbers are 53-bit, so split high/low.
  const high = Math.floor(counter / 0x1_0000_0000);
  const low = counter & 0xffff_ffff;
  view.setUint32(0, high, false);
  view.setUint32(4, low, false);
  const hmac = await hmacSha1(key, new Uint8Array(buf));
  const code = dynamicTruncate(hmac) % (10 ** digits);
  return code.toString().padStart(digits, '0');
}

/** Current TOTP code at a given epoch (default = now). */
export async function generateTotp(secret: string, epochMs: number = Date.now(), period = PERIOD_SECONDS): Promise<string> {
  const counter = Math.floor(epochMs / 1000 / period);
  return totpForCounter(secret, counter);
}

/** Verify a code against the current window ± skew (default ±1 = 90s tolerance).
 *  Returns true if the code matches any window in range. */
export async function verifyTotp(secret: string, code: string, opts: { skew?: number; epochMs?: number } = {}): Promise<boolean> {
  const skew = opts.skew ?? 1;
  const now = opts.epochMs ?? Date.now();
  const counter = Math.floor(now / 1000 / PERIOD_SECONDS);
  for (let w = -skew; w <= skew; w++) {
    const candidate = await totpForCounter(secret, counter + w);
    if (candidate === code.replace(/\s+/g, '')) return true;
  }
  return false;
}

/** Seconds remaining in the current 30s window — drives the countdown UI. */
export function secondsLeftInWindow(epochMs: number = Date.now(), period = PERIOD_SECONDS): number {
  return period - Math.floor(epochMs / 1000) % period;
}

// ─── Recovery codes ────────────────────────────────────────────────────────
//
// 10 single-use codes the user can fall back to if their authenticator is
// lost. Format: 5 hex chars + dash + 5 hex chars (e.g. "a3c12-9f4d8").

export function generateRecoveryCodes(count = 10): string[] {
  const out: string[] = [];
  for (let i = 0; i < count; i++) {
    const bytes = new Uint8Array(5);
    crypto.getRandomValues(bytes);
    const hex = Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('');
    out.push(`${hex.slice(0, 5)}-${hex.slice(5, 10)}`);
  }
  return out;
}
