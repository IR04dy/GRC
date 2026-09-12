import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { isTestFile, relPosix, stringLiterals, walkSources } from './support/sourceScan';

/**
 * JRN-10 — an internal delivery sprint may not appear in product copy, in either language.
 *
 * <h3>What was there</h3>
 * <p>A page eyebrow sits ABOVE the page heading, so it is the most prominent line on the screen
 * after the title itself, and 47 of them read <em>Sharia Governance · Sprint 20</em>. A Saudi bank's
 * compliance officer has no sprint board; the number tells them nothing and reads as unfinished
 * software. Swept: <b>106 values across 44 files</b> — 52 English eyebrows, 48 Arabic eyebrows, two
 * prose sentences naming a <em>Sprint 4 SLA engine</em> in each language, and the coming-soon badge
 * in both.
 *
 * <h3>⚠⚠ Why the count in the plan was 53 and the truth was 106 — a THIRD token error in one chain</h3>
 * <p>The plan records this finding's own measurement history, and it is three failures of one kind:
 * <ul>
 *   <li>The audit grepped the Latin token {@code Sprint} and read <b>zero</b> over {@code ar.ts},
 *       which carries the identical defect — so the PRIMARY language was policed more weakly than
 *       the secondary one (house rule 7).</li>
 *   <li>A refuter grepped bare {@code المرحلة} — the ordinary Arabic word for <em>stage</em> — and
 *       reported ~52, of which 30 are legitimate copy like <em>المرحلة الحالية</em> (current stage).</li>
 *   <li>The correction then required {@code المرحلة} + a <b>Western</b> digit and found <b>one</b> —
 *       because every eyebrow twin but one is written with <b>Arabic-Indic</b> digits
 *       ({@code المرحلة ١٣}, U+0663 U+0661). One token too narrow, and a narrow token returns a
 *       SMALLER population, which reads exactly like a clean tree.</li>
 * </ul>
 * 🔑 Worse than merely narrow: that token is INVERTED. Of the three values it matches, <b>two are
 * the legitimate ZATCA e-invoicing Phase 2 copy</b> and only one is a defect — so it caught the
 * domain copy and missed 47 real ones. The tell was inside {@code ar.ts} itself, where three
 * sibling eyebrow keys use two numeral systems.
 *
 * <h3>Why the English leg is a hard zero and the Arabic one cannot be</h3>
 * <p>English: no product string should read <em>Sprint &lt;number&gt;</em>, so leg 1 is a hard zero
 * with no exemption map — safe by F2-214's test, because a correct eyebrow is always available.
 *
 * <p>Arabic: <b>no token separates the defect from legitimate copy.</b> {@code المرحلة 2} is ZATCA's
 * real Phase 2 and {@code المرحلة 13} was a sprint; {@code المرحلة الثانية} is ISO 22301 stage 2 and
 * {@code المرحلة الخامسة} was <em>Sprint 5</em>. The distinction is semantic — an external
 * regulator's programme phase versus our own delivery sprint — so legs 2 and 3 are hard zeros over
 * the two shapes that are unambiguously OURS, and leg 4 holds everything they cannot see as an
 * exactly-asserted census with a reason per member. A hard zero over a population the instrument
 * cannot see is the most reassuring possible artefact (F2-205), so the residue is countable instead.
 *
 * <h3>⚠ Comments are deliberately out of scope, and that is load-bearing rather than lenient</h3>
 * <p>Measured on this tree: <b>327</b> occurrences of {@code Sprint &lt;n&gt;} survive in source, and
 * <b>every one is in a comment</b> — a developer-facing note recording which sprint added a block,
 * which is true and worth keeping. Reading raw source would fire 327 times on correct code. The
 * self-test pins that behaviour synthetically so it does not depend on the corpus keeping them.
 */

const SRC = join(process.cwd(), 'src');

/** المرحلة — "the stage/phase". Used by BOTH the defect and by real regulatory copy. */
const MARHALA = 'المرحلة';
/** السبرنت — "the sprint", transliterated. Never a regulatory term; always ours. */
const SIBRINT = 'السبرنت';

/**
 * A space before the digit is the whole narrowing, and it is stated rather than accidental:
 * {@code main.tsx} imports {@code ./components/ui/sprint4-extras.css}, an import SPECIFIER rather
 * than copy, and it has no space. Every real instance was {@code Sprint} + space + number.
 */
const EN_SPRINT = /\bSprint +\d/i;
/** The eyebrow form, either numeral system. The bullet is what excludes ZATCA's `المرحلة 2 الموجة`. */
const AR_EYEBROW = new RegExp(`·\\s*${MARHALA}\\s*[0-9٠-٩]`);
/**
 * `المرحلة` + a number or a spelled ordinal — the shape that can name a phase at all.
 *
 * ⚠ The trailing look-ahead is not decoration. Written without it, the alternative `ثان` matched
 * the PREFIX of `ثانية` and the captured phrase came back truncated — which makes the census key a
 * phantom that matches nothing, i.e. an exemption that exempts nothing (F2-199). Both directions of
 * leg 4 failed on it, which is the argument for enforcing a named map both ways rather than one.
 */
const AR_PHASE = new RegExp(
  `${MARHALA}\\s+([0-9٠-٩]+|ال(?:أول|ثاني|ثالث|رابع|خامس|سادس|سابع|ثامن|تاسع|عاشر)(?:ة|ى)?)`
  + `(?![؀-ۿ])`);

/**
 * The Arabic values that name a phase and are NOT ours. Every one was read with its English twin
 * before being written here, and the twin is quoted, because a reason that cannot be checked is the
 * least-verified part of the most authoritative-looking artefact (F2-188).
 */
const EXTERNAL_PROGRAMME_PHASES: Record<string, string> = {
  [`lib/assets.ts :: ${MARHALA} الثانية`]:
    'ZATCA e-invoicing Phase 2. Its own English twin reads "ZATCA Phase-2 e-invoicing integration".',
  [`lib/auditCommittee.ts :: ${MARHALA} الثانية`]:
    'ISO 22301 certification stage 2 — an ISO audit really does have a stage 1 and a stage 2. Twin: "ISO 22301 stage-2 certification readiness".',
  [`lib/onboarding.ts :: ${MARHALA} الثانية`]:
    'ZATCA e-invoicing Phase 2. Twin: "Phase 2 integration — XML/UBL invoice format, QR codes and clearance API".',
  [`lib/regulatoryChange.ts :: ${MARHALA} الثالثة`]:
    'SAMA Open Banking Phase 3. Twin: "SAMA Open Banking — Phase 3 mandatory APIs".',
  [`lib/regulatoryChange.ts :: ${MARHALA} الثانية`]:
    'ZATCA e-invoicing Phase 2, wave 22. Twin: "Wave 22 brings entities with annual revenue >= SAR 1M into Phase-2 e-invoicing".',
  [`pages/compliance/mockCompliance.ts :: ${MARHALA} الثالثة`]:
    'SAMA Open Banking Phase 3. Twin: "Open Banking Phase 3 — instructions", citing SAMA Circular 4801/2026.',
  [`pages/compliance/mockCompliance.ts :: ${MARHALA} 2`]:
    'ZATCA e-invoicing Phase 2 wave 18. Twin: "E-invoicing Phase 2 wave 18", citing ZATCA Decision 2026/22.',
  [`lib/authorityObligations.ts :: ${MARHALA} 2`]:
    'ZATCA e-invoicing Phase 2 wave 18. Twin: "E-invoicing Phase 2 wave 18 onboarding", obligation ZATCA-FATOORA-W18.',
};

type Value = { rel: string; line: number; text: string };

const VALUES: Value[] = walkSources(SRC)
  .filter(f => !isTestFile(f))
  .flatMap(f => {
    const rel = relPosix(SRC, f);
    return stringLiterals(readFileSync(f, 'utf8')).map(l => ({ rel, line: l.line, text: l.text }));
  });

const at = (v: Value) => `${v.rel}:${v.line}  ${v.text.slice(0, 90)}`;

describe('JRN-10 — product copy does not name an internal sprint', () => {
  it('no English copy names a Sprint number', () => {
    const offenders = VALUES.filter(v => EN_SPRINT.test(v.text)).map(at);
    expect(offenders, 'An eyebrow, a page sub or a badge naming an internal sprint. Drop the '
      + '"· Sprint N" suffix; the domain label alone is the eyebrow.').toEqual([]);
  });

  it('no Arabic copy transliterates the word sprint', () => {
    const offenders = VALUES.filter(v => v.text.includes(SIBRINT)).map(at);
    expect(offenders, `${SIBRINT} is a transliteration of "sprint" and is never a regulatory term, `
      + 'so it can only ever be ours.').toEqual([]);
  });

  it('no Arabic eyebrow names a stage number, in EITHER numeral system', () => {
    const offenders = VALUES.filter(v => AR_EYEBROW.test(v.text)).map(at);
    expect(offenders, 'The bullet separator is the eyebrow form. Both numeral systems are covered '
      + 'BECAUSE checking only one is what returned 1 of 48.').toEqual([]);
  });

  it('every remaining Arabic phase reference is an external programme phase, and each is named', () => {
    const found = new Map<string, string>();
    for (const v of VALUES) {
      if (AR_EYEBROW.test(v.text)) continue;          // leg 3 owns that shape
      const m = AR_PHASE.exec(v.text);
      if (m) found.set(`${v.rel} :: ${MARHALA} ${m[1]}`, at(v));
    }
    expect([...found.keys()].sort(), 'A new Arabic value naming a phase. If it is a regulator\'s '
      + 'programme phase (ZATCA, SAMA, an ISO stage) add it with its English twin quoted; if it is '
      + 'one of ours, delete the number.').toEqual(Object.keys(EXTERNAL_PROGRAMME_PHASES).sort());

    // Reverse: an entry that no longer matches anything is a reason outliving its subject (F2-199).
    expect(Object.keys(EXTERNAL_PROGRAMME_PHASES).filter(k => !found.has(k)),
      'Declared exemption that the scan no longer finds — delete it in the commit that removed it.')
      .toEqual([]);

    for (const [k, reason] of Object.entries(EXTERNAL_PROGRAMME_PHASES)) {
      expect(reason.length, `${k}: a bare label is not a reason (F2-188)`).toBeGreaterThan(60);
      expect(reason, `${k}: the reason must name the programme or quote the English twin`)
        .toMatch(/ZATCA|SAMA|ISO |Twin:/);
    }
  });

  it('the detectors fire on the shapes they forbid and not on the ones they must tolerate', () => {
    // MUST flag — one per leg, in the exact form each was found in.
    expect(EN_SPRINT.test('Sharia Governance · Sprint 20')).toBe(true);
    expect('محرّك SLA السبرنت الرابع'.includes(SIBRINT)).toBe(true);
    expect(AR_EYEBROW.test('الحوكمة الشرعية · المرحلة ٢٠')).toBe(true);   // Arabic-Indic
    expect(AR_EYEBROW.test('سلاسل الاعتماد · المرحلة 13')).toBe(true);    // Western

    // MUST NOT flag — each of these is real code or real copy in this tree today.
    expect(EN_SPRINT.test('./components/ui/sprint4-extras.css')).toBe(false);
    expect(EN_SPRINT.test('Detection-engineering tuning sprint')).toBe(false);
    expect(AR_EYEBROW.test('الفوترة الإلكترونية المرحلة 2 الموجة 18')).toBe(false);
    expect(AR_PHASE.test('المرحلة الحالية')).toBe(false);                 // "current stage"
    expect(AR_PHASE.test('L0 المرحلة الفيزيائية')).toBe(false);           // "physical stage"

    // The ordinal must be captured WHOLE. Matching `ثان` as a prefix of `ثانية` truncates the
    // census key, and a truncated key is an exemption that exempts nothing.
    expect(AR_PHASE.exec('تكامل المرحلة الثانية للفوترة')?.[1]).toBe('الثانية');
    expect(AR_PHASE.exec('المرحلة 2 الموجة 18')?.[1]).toBe('2');

    // Comments are out of scope, and this is what makes that load-bearing rather than lenient:
    // read raw, this fixture flags; read as the gate reads it, it does not.
    const fixture = "// Sprint 13 — added the approvals queue\nconst x = 'Approvals';\n";
    expect(EN_SPRINT.test(fixture)).toBe(true);
    expect(stringLiterals(fixture).some(l => EN_SPRINT.test(l.text))).toBe(false);
    // ...and a literal on a commented line is still copy when it is genuinely a literal.
    expect(stringLiterals("const x = 'Approvals · Sprint 13';").some(l => EN_SPRINT.test(l.text)))
      .toBe(true);
  });

  it('the scan resolves a real corpus', () => {
    // Floors sit above the largest WRONG answer, which for a broken walk or a rotted extractor is
    // zero — an empty corpus makes every assertion above pass vacuously (F2-235).
    expect(VALUES.length, 'string literals extracted').toBeGreaterThan(100_000);
    expect(new Set(VALUES.map(v => v.rel)).size, 'non-test source files').toBeGreaterThan(700);
  });
});
