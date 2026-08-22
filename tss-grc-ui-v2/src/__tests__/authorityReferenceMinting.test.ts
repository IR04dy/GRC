import { describe, expect, it } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

/**
 * F2-269 — the browser may not mint a reference that belongs to an outside authority.
 *
 * <p>[[F2-172]](d) established that only the server may issue a tracking code, and [[F2-209]]
 * sharpened it for the case that matters most here: **a reference only an outside authority can
 * issue is received and recorded, or absent — the server may not mint it either.** [[F2-207]] filed
 * a frontend population for this (*"14 client-side business-reference minters in 12 files, entropy
 * from `Math.random`"*, naming `NCA-ACK-`, `SAMA-CTI-`) and nobody counted it.
 *
 * <p><b>What was live.</b> `cyberLifecycle.transitionFiling` minted
 * `NCA-ACK-<year>-<Math.random()>` on the `ACCEPTED` transition, and `CyberLifecyclePage` rendered
 * it as a green `success` badge. Reached by a four-click flow — DRAFT → PEER_REVIEW →
 * CISO_APPROVAL → SUBMITTED → `cycMarkAccepted` — so the platform displayed **a fabricated
 * National Cybersecurity Authority acknowledgement of an ECC filing**. There is no `nca_ack_ref`
 * column anywhere and no NCA integration that could receive one, so the value could only ever have
 * been invented.
 *
 * <p>🔑 That is the top rung of [[F2-199]]'s harm ladder. A fabricated dashboard misleads whoever
 * is looking; a fabricated export leaves the platform; **a fabricated acknowledgement asserts that
 * a regulator has accepted a filing.** And [[F2-205]] edited this exact file, retired its
 * fabricated exports, and left a comment disclosing that the filing store is browser-local — 🔑
 * **a disclosure in the code is not a disclosure on the screen**, and the operator saw only a green
 * monospaced badge that looks exactly like a real regulator reference.
 *
 * <p><b>What was latent.</b> `createThreat` invented the issuing body's own advisory number —
 * `NCA-AD-<year>-<random>` for NCA HASEEN, `SAMA-CTI-<year>-<random>` for SAMA ISAC. Latent only
 * because its single caller passes `'INTERNAL'`, which is a fact about today rather than a property
 * of the control ([[F2-182]]), so it is closed too: the reference is a caller-supplied parameter
 * now and stays absent when the advisory carried none.
 *
 * <p><b>Why a hard zero is safe here</b> — unlike the reference and deadline censuses, which are
 * counts because a caller-supplied value is often legitimate. There is no correct reason for a
 * browser to generate a Saudi regulator's own reference number, so this ban cannot fail on correct
 * code ([[F2-214]]). Two exclusions are asserted rather than assumed:
 * <ul>
 *   <li><b>A recorded reference is not a minted one.</b> The seed corpus carries real received
 *       advisory numbers (`sourceRef: 'NCA-AD-2026-0148'`) — no entropy on the line, so the
 *       entropy requirement excludes them, and that exclusion is pinned.</li>
 *   <li><b>A label formatted from a server id is not a mint</b> (`INC-${'{'}String(i.id)...`), and
 *       `crypto.subtle.digest('SHA-256', …)` is not a reference at all. My first scan counted both:
 *       9 `SHA-256` hits and 5 server-id labels out of 38 raw sites.</li>
 * </ul>
 *
 * <p>⚠ <b>Comments are stripped PRESERVING NEWLINES.</b> The first version of this scan removed
 * them, which shifted every subsequent line number — so its report pointed at unrelated code
 * ([[F2-242]] records the same remedy). This file's own prose names the retired prefixes, so a
 * comment-blind scan would also read the epitaph as the corpse ([[F2-193]]).
 */

// The house idiom (kpiProvenance.test.ts): resolve from cwd, not from import.meta.url —
// the URL form yielded 'D:\src' on Windows and every walk failed with ENOENT.
const SRC = join(process.cwd(), 'src');

/** Saudi and sector authorities whose references this platform receives, never issues. */
const AUTHORITIES = [
  'NCA', 'SAMA', 'NCEMA', 'SDAIA', 'CMA', 'ZATCA', 'SFDA', 'GOSI', 'MOCI', 'HRSD', 'FIU',
] as const;

/** Entropy or a local counter — the fingerprint of minting rather than recording. */
const MINTS = /Math\s*\.\s*random|Date\s*\.\s*now\s*\(|toString\(36\)|crypto\s*\.\s*randomUUID/;

/** Not a reference at all. */
const NOT_A_REF = /crypto\s*\.\s*subtle|\bdigest\s*\(|['"`]SHA-/;

/** A label formatted from a server-assigned id. */
const SERVER_ID_LABEL = /String\(\s*\w+\??\.(?:id|\w*Id)\s*\)|\.\s*(?:id|\w*Id)\s*\)\s*\}/;

const AUTHORITY_PREFIX = new RegExp(
  '[`\'"]\\s*(' + AUTHORITIES.join('|') + ')-[A-Z0-9]*-?\\$\\{|' +
  '[`\'"]\\s*(' + AUTHORITIES.join('|') + ')-[A-Z0-9]*-?\'\\s*\\+',
);

/** Strip comments but keep every newline, so line numbers survive (F2-242). */
function stripKeepLines(src: string): string {
  return src.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, m => m.replace(/[^\n]/g, ' '));
}

function walk(dir: string, out: string[] = []): string[] {
  for (const e of readdirSync(dir)) {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) {
      if (e !== '__tests__' && e !== 'node_modules') { walk(p, out); }
    } else if (/\.(ts|tsx)$/.test(e)) {
      out.push(p);
    }
  }
  return out;
}

interface Hit { file: string; line: number; text: string }

function scan(): { authority: Hit[]; otherMints: Hit[]; excluded: Hit[] } {
  const authority: Hit[] = [];
  const otherMints: Hit[] = [];
  const excluded: Hit[] = [];
  for (const file of walk(SRC)) {
    const lines = stripKeepLines(readFileSync(file, 'utf8')).split('\n');
    lines.forEach((raw, i) => {
      const prefixes = [...raw.matchAll(/[`'"]([A-Z][A-Z0-9]{1,9})-/g)].map(m => m[1]);
      if (prefixes.length === 0) { return; }
      const rel = file.slice(process.cwd().length + 1).split('\\').join('/');
      const hit: Hit = { file: rel, line: i + 1, text: raw.trim().slice(0, 140) };
      if (NOT_A_REF.test(raw)) { excluded.push(hit); return; }
      if (!MINTS.test(raw) && !/\bseq\b|\bnext[A-Z]\w*\(/.test(raw)) { return; }
      if (SERVER_ID_LABEL.test(raw) && !MINTS.test(raw)) { excluded.push(hit); return; }
      if (prefixes.some(p => (AUTHORITIES as readonly string[]).includes(p))
          && AUTHORITY_PREFIX.test(raw)) {
        authority.push(hit);
      } else {
        otherMints.push(hit);
      }
    });
  }
  return { authority, otherMints, excluded };
}

/**
 * Non-authority browser mints, decrease-only and with NO VERDICTS.
 *
 * <p>Measured by this test. These are our own references, so a browser minting one is
 * [[F2-172]](d)'s ordinary shape rather than [[F2-209]]'s — worth counting and reading, not
 * banning wholesale, because some of these stores have no server side at all ([[F2-204]]'s
 * `NO_SERVER_SURFACE` population) and a mint there is the only id the row will ever have.
 */
const BASELINE_BROWSER_MINTS = 26;

describe('F2-269 — authority-owned references are received, never minted in the browser', () => {
  it('no frontend file mints a reference belonging to an outside authority', () => {
    const { authority } = scan();
    expect(
      authority.map(h => `${h.file}:${h.line}  ${h.text}`),
      'A Saudi or sector authority issues its own reference numbers; this platform receives and '
      + 'records them, or shows their absence (F2-209). There is no correct reason for a browser to '
      + 'generate one, which is why this is a hard zero rather than a census. If an integration '
      + 'starts receiving them, store what it received.',
    ).toEqual([]);
  });

  it('the detector fires on the shapes this slice retired, and not on a recorded reference', () => {
    const minted = 'const r = `NCA-ACK-${new Date().getFullYear()}-${Math.random().toString(36)}`;';
    const mintedSama = ': source === \'SAMA-ISAC\' ? `SAMA-CTI-${new Date().getFullYear()}-${Math'
      + '.floor(Math.random() * 999)}`';
    for (const shape of [minted, mintedSama]) {
      expect(AUTHORITY_PREFIX.test(shape) && MINTS.test(shape),
        `the detector must fire on the literal retired shape: ${shape.slice(0, 60)}`).toBe(true);
    }
    // A RECORDED reference has no entropy on the line — the seed corpus is full of them, and
    // banning those would delete real received advisory numbers.
    const recorded = "  { id: 'THR-2026-001', source: 'NCA-HASEEN', sourceRef: 'NCA-AD-2026-0148',";
    expect(MINTS.test(recorded), 'a recorded reference must not read as a mint').toBe(false);
    // Not a reference at all.
    expect(NOT_A_REF.test("crypto.subtle.digest('SHA-256', buf)")).toBe(true);
    // A label formatted from a server id.
    expect(SERVER_ID_LABEL.test('`INC-${String(i.id).padStart(4, \'0\')}`')).toBe(true);
  });

  it('non-authority browser mints are counted, decrease-only', () => {
    const { otherMints, excluded } = scan();
    expect(walk(SRC).length, 'anti-vacuity: the walk collapsed and a small tree reports a clean one')
      .toBeGreaterThan(400);
    expect(
      otherMints.length,
      `${otherMints.length} frontend sites mint one of OUR OWN references; ceiling `
      + `${BASELINE_BROWSER_MINTS}. No verdicts: some of these stores have no server side at all, `
      + `so the mint is the only id the row will ever have (F2-204). Sites:\n`
      + otherMints.map(h => `  ${h.file}:${h.line}  ${h.text}`).join('\n'),
    ).toBeLessThanOrEqual(BASELINE_BROWSER_MINTS);
    expect(excluded.length,
      'anti-vacuity on the exclusions: SHA-256 digests and server-id labels really are being '
      + 'subtracted, so a change that stops excluding them fails here rather than inflating the '
      + 'census').toBeGreaterThan(5);
  });
});
