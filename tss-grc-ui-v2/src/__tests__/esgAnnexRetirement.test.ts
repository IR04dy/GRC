import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

/**
 * F2-435 — `/esg` emitted a fabricated CMA Art. 91-94 / TCFD ANNUAL-REPORT ANNEX beside the real
 * one, on the same page.
 *
 * `lib/esg.ts`'s `buildEsgAnnex` / `buildEsgAnnexSheets` built an "ESG / SUSTAINABILITY ANNEX —
 * ANNUAL REPORT" — GHG Scope 1/2/3 with third-party verification flags, the CMA Articles 91-94
 * disclosure statuses, the TCFD four-pillar alignment — from `EMISSIONS_TIMESERIES`,
 * `CMA_ARTICLES` and `TCFD_ITEMS`, module-level constants in a file that made **zero** server
 * calls. The page emitted it FOUR ways from its header, so on every tab: a `.txt` download, a
 * print-to-PDF stamped `CONFIDENTIAL — SUSTAINABILITY`, and branded `.xlsx`/`.docx` through
 * `reportingApi.packetXlsx` / `packetDocx`. The **primary** button on the page was one of them.
 *
 * ⚠⚠ WHY F2-199's `exportProvenance` WAS GREEN OVER IT, AND THE GAP THAT REMAINS.
 *
 * That gate asks whether the emitting page has server data in scope, and F2-205 corrected its
 * DIRECTION-blindness (an outbound `reportingApi.packet*` POST is a printer, not provenance).
 * `EsgPage` calls `esgDashboardApi.get` and five `vendorEsgApi.*` methods for its other tabs, so
 * after stripping the emitter it still resolves `backedVia: 'itself'` and drops out of the
 * unbacked population.
 *
 * 🔑 That is F2-203's lesson, which that gate states for a DIFFERENT gate and not for itself: a
 * one-hop "is there server data in scope" rule certifies the FILE, not the FIGURE. Closing it in
 * general needs the emitted payload resolved THROUGH LOCALS — `printPacket({ sections:
 * packetTextToSections(text) })` where `text` came from the builder two statements earlier — which
 * is real dataflow, F2-431's two-hop shape, and its own slice. Measured here: of five emitter call
 * sites whose payload names an imported symbol, the identifier resolvable without dataflow is the
 * FORMATTER at all five. So this file guards the member; the gap is recorded in the register.
 *
 * ⚙ RETIRED rather than rewired, per ADR-017 §D-5 and F2-199/F2-207 — and safe because the real
 * annex is a SIBLING TAB. `EsgAnnexTab` drives `lib/sustainabilityAnnex.ts` end to end
 * (`generateAnnex` → `submitReport` → `approveReport` → `publishReport` → `exportReport`) over
 * registers measured populated in both databases: `sust.cma_disclosure` 91 rows,
 * `sust.tcfd_item` 85, `sust.ghg_emission_record` 41 dev / 40 prod.
 *
 * 🔑🔑 The tell was already in the tree, as prose. `lib/sustainabilityCma.ts` says it "replaces
 * the static CMA_ARTICLES constant"; `EsgCmaTab` and `EsgTcfdTab` each say "promoted from the
 * static … constant to a live API-driven" register — V60 and V61. **Both tabs were de-mocked and
 * the constants they were promoted OFF were left in place, still feeding the export.** The
 * de-mocking stopped at the visible surface, which is exactly why the page reads as finished
 * (F2-193: when you de-mock a store, grep for everything that summarised it — here, exported it).
 */

const SRC = resolve(__dirname, '..');

/**
 * ⚠ ONE alternation, left-to-right, newline-preserving. Written the obvious way (block comments
 * first) this deletes 45% of some files here, because a `//` prose comment can contain a literal
 * `/*` — F2-68(c) found it, F2-198 reproduced it. And it is load-bearing in BOTH directions in
 * this file: the notes below deliberately QUOTE the retired identifiers to record what they
 * replaced, so a raw scan would read the epitaph as the corpse (F2-193).
 */
function stripComments(src: string): string {
  return src.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, m => m.replace(/[^\n]/g, ' '));
}

const ESG = stripComments(readFileSync(join(SRC, 'lib/esg.ts'), 'utf8'));
const PAGE = stripComments(readFileSync(join(SRC, 'pages/esg/EsgPage.tsx'), 'utf8'));
const ANNEX_TAB = readFileSync(join(SRC, 'pages/esg/EsgAnnexTab.tsx'), 'utf8');
const ANNEX_CLIENT = readFileSync(join(SRC, 'lib/sustainabilityAnnex.ts'), 'utf8');
const EN = readFileSync(join(SRC, 'translations/en.ts'), 'utf8');
const AR = readFileSync(join(SRC, 'translations/ar.ts'), 'utf8');

describe('F2-435 — the fabricated ESG annex stays retired', () => {
  it('lib/esg.ts exports no annex builder', () => {
    for (const fn of ['buildEsgAnnex', 'buildEsgAnnexSheets']) {
      expect(ESG,
        `${fn} built a CMA Art. 91-94 / TCFD annual-report annex from module constants in a file `
        + 'that makes no server call. The real annex is generated server-side from the live '
        + 'registers — see EsgAnnexTab. Do not re-add a builder here.')
        .not.toMatch(new RegExp(`\\b${fn}\\s*\\(`));
    }
  });

  it('and no corpus behind one', () => {
    // The three the builders were the only readers of. Their TYPES stay — `TcfdItem` is shared by
    // four files and `CmaArticle['status']` keys the platform-wide STATUS_LABEL — so this asserts
    // the CONSTANTS, never the interfaces.
    for (const konst of ['EMISSIONS_TIMESERIES', 'CMA_ARTICLES', 'TCFD_ITEMS',
                         'CURRENT_YEAR_RECORD']) {
      expect(ESG, `${konst} was a fabricated corpus feeding the retired annex`)
        .not.toMatch(new RegExp(`\\b${konst}\\b`));
    }
  });

  it('the page emits no document at all', () => {
    // Four emitters, three mechanisms. `reportingApi.packet*` is the one F2-205 had to teach the
    // export gate to read as a PRINTER rather than as provenance, so it is the one most likely to
    // come back looking legitimate.
    for (const shape of [/\bnew\s+Blob\s*\(/, /\bprintPacket\s*\(/,
                         /reportingApi\.packet(?:Xlsx|Docx)\s*\(/]) {
      expect(PAGE, 'EsgPage must not emit a document: everything it could build one from is a '
        + 'browser constant, and the real annex has a server-side generator with a lifecycle')
        .not.toMatch(shape);
    }
  });

  it('the header control lands on the real Annex tab rather than vanishing', () => {
    // F2-137/F2-207: retiring a surface must not silently remove the affordance — a control has to
    // land somewhere true. Asserted as the CALL SHAPE, not the token, so a rename cannot satisfy
    // it by accident (F2-194) and an extraction cannot break it (F2-197).
    expect(PAGE).toMatch(/onClick=\{\(\)\s*=>\s*setTab\('annex'\)\}/);
    expect(PAGE).toMatch(/t\('esgOpenAnnexTab'\)/);
  });

  it('the retire left a REAL surface — the sibling tab still drives the server annex', () => {
    // The check F2-207 requires and F2-205 could NOT satisfy for BcmLifecyclePage: retiring a
    // fabricated export is only safe once the signal is verified to survive somewhere real. If
    // this leg ever fails, the retire has become a deletion.
    expect(PAGE).toMatch(/tab === 'annex'\s*&&\s*<AnnexTab\s*\/>/);
    for (const fn of ['generateAnnex', 'submitReport', 'approveReport', 'publishReport',
                      'exportReport']) {
      expect(ANNEX_TAB, `EsgAnnexTab must still drive ${fn}`).toContain(fn);
    }
    expect(ANNEX_CLIENT).toContain('/generate-annex');
    expect(ANNEX_CLIENT).toMatch(/api\.post</);
  });

  it('the retired keys are gone from both dictionaries and the new one is in both', () => {
    // F2-201's rule: for a DELETE, over-approximate usage. Both keys were read only by the removed
    // code, checked across the tree before deleting them. House rule 7 — the replacement exists in
    // Arabic too, and Arabic is the primary language.
    for (const [name, dict] of [['en', EN], ['ar', AR]] as const) {
      expect(dict, `${name}: esgDownloadAnnex was the fabricated download's label`)
        .not.toMatch(/\besgDownloadAnnex\s*:/);
      expect(dict, `${name}: esgAnnexDownloadedToast said "attach to the annual report"`)
        .not.toMatch(/\besgAnnexDownloadedToast\s*:/);
      expect(dict, `${name}: the replacement control needs a label`)
        .toMatch(/\besgOpenAnnexTab\s*:/);
    }
  });

  it('the comment stripper is sound in both directions', () => {
    // Without it this file fails on a CORRECT tree, because lib/esg.ts's own retirement note
    // quotes `buildEsgAnnex` and `CMA_ARTICLES` to record what it replaced. With it, a real
    // declaration must still be visible.
    const withNote = stripComments('/** retired buildEsgAnnex over CMA_ARTICLES */\n'
                                 + 'export const KEEP = 1;\n');
    expect(withNote).not.toContain('buildEsgAnnex');
    expect(withNote).toContain('export const KEEP');
    // and the live file really does quote them, so the stripping is load-bearing rather than
    // theoretical
    const raw = readFileSync(join(SRC, 'lib/esg.ts'), 'utf8');
    expect(raw).toContain('buildEsgAnnex');
    expect(raw).toContain('CMA_ARTICLES');
  });
});
