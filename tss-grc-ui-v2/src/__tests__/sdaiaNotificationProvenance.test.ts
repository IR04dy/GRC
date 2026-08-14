import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

/**
 * F2-209 — a document addressed to an authority may not carry a field that
 * resolves to nothing, and the control that discharges a statutory notification
 * may not be gated on a column the server does not own for it.
 *
 * ─── why an eleventh instrument ──────────────────────────────────────────────
 *
 * Ten guards in this tree already refuse fabricated data, and each is keyed on a
 * different mechanism (see `exportProvenance.test.ts` for the list). The two
 * closest were both green over this defect, for reasons worth stating exactly:
 *
 *   • F2-199's `exportProvenance` asks *does the page that emits a document have
 *     server data in scope?* — YES here: `PdplBreachConsole` is handed real
 *     `PrivacyBreachDto` rows and fetches `/breaches/{id}/sdaia-submission`.
 *   • F2-203 sharpened that to *which of this file's data reaches the thing it
 *     emits?* — and here the SAME data reaches it. `downloadPacket` wrote
 *     `JSON.stringify({ packet, breach })`: the genuine server row and the
 *     invented submission, in one file.
 *
 * 🔑 Both are FILE-level. The Article 25 packet was a HYBRID: `breach.id`,
 * `descriptionEn`, `dataSubjectsAffectedCount` and `discoveredAt` were real, and
 * the reporting controller's identity, the categories of personal data affected,
 * the likely consequences for data subjects, the containment measures taken and
 * the remediation measures proposed were literals in the bundle. The real fields
 * are what made the invented ones credible. So this gate asks the question one
 * level down — per FIELD.
 *
 * Measured against `privacy.data_breaches` (25 columns from sys.columns, 17 live
 * rows, identical in dev and prod): there is no column for any of the five.
 *
 * ⚠ The two harms differ in kind. `containmentMeasures` was not a metric — it
 * was a statement of fact about what the organisation did ("Forensic image of
 * impacted hosts captured for SDAIA inspection"), identical for every breach in
 * every tenant, in a document the platform's own runbook says a DPO transcribes
 * into the SDAIA portal by hand.
 */

const SRC = resolve(__dirname, '..');

/**
 * ⚠ ONE alternation, left-to-right, line comments FIRST. Written the other way
 * a `//` comment containing a literal `/*` swallows the rest of the file and the
 * population SHRINKS, which reads exactly like good news (F2-68(c), F2-198).
 *
 * ⚠⚠ Stripping is load-bearing here for a second reason: `lib/pdplBreach.ts` and
 * `PdplBreachConsole.tsx` both carry header comments that NAME the retired
 * symbols and QUOTE the invented sentences, because that is how this codebase
 * records a deletion. A raw-text ban would read the epitaph as the corpse
 * (F2-193, F2-202, F2-205).
 */
function stripComments(src: string): string {
  return src.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, '');
}

const LIB_PATH     = join(SRC, 'lib', 'pdplBreach.ts');
const CONSOLE_PATH = join(SRC, 'components', 'privacy', 'PdplBreachConsole.tsx');
const PAGE_PATH    = join(SRC, 'pages', 'privacy', 'PrivacyPage.tsx');

const libRaw     = readFileSync(LIB_PATH, 'utf8');
const consoleRaw = readFileSync(CONSOLE_PATH, 'utf8');
const pageRaw    = readFileSync(PAGE_PATH, 'utf8');

const lib     = stripComments(libRaw);
const consoleCode = stripComments(consoleRaw);
const page    = stripComments(pageRaw);

/**
 * The five Article 25 fields with no column in either database. Named, because
 * the whole finding is that a document asserted them — and asserted as a list so
 * that wiring one (which needs a migration) forces a deliberate edit here rather
 * than silently shrinking a count.
 */
const NOT_HELD = ['Categories', 'Consequences', 'Containment', 'Proposed', 'Controller', 'Dpo'] as const;

/** Fields that DO resolve to a `PrivacyBreachDto` property. */
const HELD = ['Nature', 'Narrative', 'Subjects', 'AwareAt', 'PortalRef', 'SubmittedAt'] as const;

/** Parse `ARTICLE_25_FIELDS`'s entries out of the (comment-stripped) source. */
function declaredFields(src: string): Array<{ key: string; clause: string; source: string }> {
  const block = src.match(/ARTICLE_25_FIELDS[^=]*=\s*\[([\s\S]*?)\n\];/);
  if (!block) return [];
  const out: Array<{ key: string; clause: string; source: string }> = [];
  /**
   * ⚠ The tail must be `(?:,[^}]*)?` and the first draft was not. Adding
   * `format: 'datetime'` to two entries made the parse return 10 of 12, and the
   * two it dropped were the two that changed — so a gate keyed to the exact
   * literal shape reports a SMALLER population the moment a field gains a
   * property. It failed loudly here only because the leg asserts a minimum size;
   * without that anti-vacuity floor it would have silently stopped checking the
   * two entries most recently edited.
   */
  const entry = /\{\s*key:\s*'([^']+)'\s*,\s*clause:\s*'([^']+)'\s*,\s*source:\s*(null|'[^']+')\s*(?:,[^}]*)?\}/g;
  let m: RegExpExecArray | null;
  while ((m = entry.exec(block[1])) !== null) {
    out.push({ key: m[1], clause: m[2], source: m[3] });
  }
  return out;
}

describe('F2-209 — SDAIA Article 25 notification provenance', () => {

  // ── 1. the fabricated packet is gone, and stays gone ──────────────────────

  it('no code generates an SDAIA notification packet', () => {
    for (const symbol of [
      'generateSdaiaNotification',
      'SdaiaNotificationPacket',
      'DEFAULT_CONTROLLER',
      'downloadPacket',
      'PacketPreview',
    ]) {
      expect(lib.includes(symbol),
        `lib/pdplBreach.ts declares ${symbol} — the Article 25 generator was retired because no column backs its fields`).toBe(false);
      expect(consoleCode.includes(symbol),
        `PdplBreachConsole references ${symbol} — the Article 25 generator was retired`).toBe(false);
    }
  });

  it('no field of an authority-addressed document is a literal narrative', () => {
    // The five invented Article 25 answers, by the property names they were
    // assembled under. A narrative sentence asserted to a regulator has to come
    // from somewhere; none of these has a column.
    for (const invented of [
      'containmentMeasures',
      'proposedMeasures',
      'likelyConsequences',
      'categoriesAffected',
      'natureOfBreach',
      'approximateSubjects',
    ]) {
      expect(lib.includes(invented),
        `lib/pdplBreach.ts still assembles ${invented} — privacy.data_breaches has no column for it`).toBe(false);
      expect(consoleCode.includes(invented),
        `PdplBreachConsole still renders ${invented}`).toBe(false);
    }
  });

  /**
   * ⚠ SCOPED to the two files this slice owns, deliberately. `Mona Al-Bishi`
   * appears in eight other product files and `TSS Saudi Holding` in one — they
   * are members of F2-207's measured 65-name invented cast, which is filed and
   * far larger than this slice. A gate that fails on code the slice did not
   * touch is a gate nobody can keep green, so the assertion is narrow and the
   * measurement is in the register.
   */
  it('the breach console names no filing entity, CR number or DPO of its own', () => {
    for (const literal of ['1010299292', 'Mona Al-Bishi', 'TSS Saudi Holding', 'dpo@tss.sa', '+966 11 200 0000']) {
      expect(lib.includes(literal),
        `lib/pdplBreach.ts hard-codes ${literal} — a tenant-specific fact in a shipped bundle cannot be right for more than one tenant`).toBe(false);
      expect(consoleCode.includes(literal),
        `PdplBreachConsole hard-codes ${literal}`).toBe(false);
    }
  });

  // ── 2. per-field provenance is DECLARED ───────────────────────────────────

  it('every Article 25 field declares a server source or explicit null', () => {
    const fields = declaredFields(lib);
    expect(fields.length,
      'ARTICLE_25_FIELDS did not parse — the inventory is what replaced the generator, so an empty parse means this gate is vacuous').toBeGreaterThanOrEqual(12);
    for (const f of fields) {
      expect(f.source === 'null' || /^'[A-Za-z][A-Za-z0-9]*'$/.test(f.source),
        `Article 25 field ${f.key} declares source=${f.source}, which is neither null nor a DTO property name`).toBe(true);
    }
  });

  it('the fields with no column are exactly the ones measured to have none', () => {
    const fields = declaredFields(lib);
    const nulls = fields.filter(f => f.source === 'null').map(f => f.key).sort();
    expect(nulls,
      'the set of Article 25 fields the platform cannot fill changed — that is a schema fact (privacy.data_breaches, 25 columns, dev = prod), so wiring one needs a migration and this list updated in the same commit').toEqual([...NOT_HELD].sort());
    const sourced = fields.filter(f => f.source !== 'null').map(f => f.key).sort();
    expect(sourced, 'the set of Article 25 fields read from the record changed').toEqual([...HELD].sort());
  });

  /**
   * ⚠⚠ The load-bearing distinction, and the one a naive fix loses: a field with
   * NO COLUMN and a field with a column nobody filled in must not render the
   * same. An absence that looks like a blank form field reads as "nothing
   * further is required" — F2-197's invented all-clear, on a statutory
   * submission. Asserted POSITIVELY (the branch exists and carries the hint),
   * because a ban can be satisfied by deleting the whole section.
   */
  it('a field with no column is disclosed, not left blank', () => {
    expect(/aria-label=\{notHeldHint\}/.test(consoleCode),
      'the not-held branch must carry the bilingual hint on aria-label as well as title (UX-008)').toBe(true);
    expect(/title=\{notHeldHint\}/.test(consoleCode),
      'the not-held branch must carry the hint on title').toBe(true);
    expect(consoleCode.includes("t('pdplArt25DpoSupplies')"),
      'a field the platform cannot hold must say who does supply it').toBe(true);
    expect(consoleCode.includes("t('pdplArt25Empty')"),
      'a field with a column and no value must read as not-yet-recorded, which is a different fact').toBe(true);
    // and the value itself must come from the declared accessor, never a literal
    expect(/article25Value\s*\(/.test(consoleCode),
      'inventory values must be read through article25Value so the declared source is the only path to a rendered value').toBe(true);
  });

  // ── 3. the statutory gate reads the server's own column ───────────────────

  /**
   * The live half of F2-209. `sdaia_notification_status` is the column the
   * server owns for "has SDAIA been notified?", with a four-state machine in
   * `PrivacyService`. The console asked `status === 'NOTIFIED'` — the breach
   * LIFECYCLE status — and 4 of 17 live rows in BOTH databases are
   * `status = NOTIFIED` with `sdaia_notification_status = NOT_REPORTED`, all
   * four requiring notification, 113–193 days past a 72-hour window. They were
   * filtered out of the console's "needs SDAIA" pool.
   */
  it('the SDAIA question is never answered from the breach lifecycle status', () => {
    for (const wrong of [
      "status === 'NOTIFIED'",
      "status !== 'NOTIFIED'",
      'status === "NOTIFIED"',
      'status !== "NOTIFIED"',
    ]) {
      expect(consoleCode.includes(wrong),
        `PdplBreachConsole compares a breach lifecycle status to NOTIFIED (${wrong}) — the platform owns sdaia_notification_status for that question`).toBe(false);
    }
  });

  /**
   * ⚠⚠ MY OWN GATE HAD F2-203'S DEFECT AND ONLY THE MUTATION HARNESS SAW IT.
   *
   * The first version counted `isSdaiaReported(` and required `>= 2`. There are
   * THREE call sites, so a mutation that reverted the submission panel to its own
   * `!== 'NOTIFIED_REPORTED'`-style comparison left two behind and SURVIVED.
   * F2-203 recorded this in these words — "a count with a floor cannot see one
   * member vanish" — and I wrote a floor in a gate built after quoting it.
   *
   * Replaced with a ban on the SHAPE the defect takes: no raw comparison against
   * a SDAIA state literal anywhere in the console. The predicates live in the lib
   * and are the only place those tokens may be compared, so reverting any site to
   * an inline comparison fails regardless of how many other sites remain.
   */
  it('no site compares a SDAIA state literal by hand', () => {
    for (const state of ['NOT_REPORTED', 'IN_PREPARATION', 'SUBMITTED', 'ACKNOWLEDGED']) {
      for (const op of ['===', '!==']) {
        const shape = new RegExp(`${op}\\s*['"]${state}['"]`);
        expect(shape.test(consoleCode),
          `PdplBreachConsole compares a SDAIA state to '${state}' inline (${op}). The four states are a server vocabulary with a state machine behind them; ask isSdaiaReported / isSdaiaSubmitted so one component cannot hold two answers.`).toBe(false);
      }
    }
  });

  it('each SDAIA question is asked through the predicate that answers it', () => {
    // pool + KPI discharge -> isSdaiaSubmitted (IN_PREPARATION discharges nothing)
    expect((consoleCode.match(/isSdaiaSubmitted\s*\(/g) ?? []).length,
      'the urgency pool and the countdown KPI must both ask isSdaiaSubmitted — a notification merely IN_PREPARATION has not been sent, so excluding it from the pool or greening the 72-hour tile is the same defect one notch milder').toBe(2);
    // the panel's Record-vs-Update label -> isSdaiaReported (has anything been recorded at all?)
    expect((consoleCode.match(/isSdaiaReported\s*\(/g) ?? []).length,
      'the submission panel asks isSdaiaReported for its Record/Update label').toBe(1);
    // and the predicates must WHITELIST, so an unanticipated status is chased
    // rather than excused — for a statutory clock that is the safe direction.
    expect(/return\s+state === 'IN_PREPARATION' \|\| state === 'SUBMITTED' \|\| state === 'ACKNOWLEDGED';/.test(lib),
      'isSdaiaReported must whitelist the reported states rather than excluding NOT_REPORTED').toBe(true);
    expect(/return\s+state === 'SUBMITTED' \|\| state === 'ACKNOWLEDGED';/.test(lib),
      'isSdaiaSubmitted must whitelist the two states that discharge the clock').toBe(true);
  });

  it('the client vocabulary matches the four states the server declares', () => {
    const m = lib.match(/SDAIA_NOTIFICATION_STATES\s*=\s*\[([\s\S]*?)\]/);
    expect(m, 'SDAIA_NOTIFICATION_STATES must be declared').toBeTruthy();
    const states = [...(m![1].matchAll(/'([A-Z_]+)'/g))].map(x => x[1]).sort();
    expect(states, 'the client must mirror PrivacyService.SDAIA_STATES exactly — F2-205 found five client vocabularies for one server machine, all agreeing with each other and none with the server')
      .toEqual(['ACKNOWLEDGED', 'IN_PREPARATION', 'NOT_REPORTED', 'SUBMITTED']);
  });

  // ── 4. no control claims to do what the platform cannot ───────────────────

  /**
   * SDAIA has no machine-readable API — the entity javadoc says so and the
   * submission is a human filling in a portal. A "Dispatch to SDAIA" button
   * cannot be fixed by pointing it at a better endpoint, so it is gone rather
   * than repointed. It had been wired to `notifyDataOffice`, which writes
   * `pdpc_notification_required` + `pdpc_notified_at` and never the SDAIA
   * columns, with no audit row.
   */
  it('nothing offers to dispatch a notification to SDAIA', () => {
    for (const key of ['pdplDispatchToSdaia', 'pdplDispatched', 'pdplDispatchToast', 'pdplDownloadPacket', 'pdplSdaiaPacketTitle']) {
      expect(consoleCode.includes(key), `PdplBreachConsole still uses the retired label ${key}`).toBe(false);
    }
    expect(consoleCode.includes('onDispatch'),
      'the onDispatch prop is retired — its contract said the action must transition state and the one caller passed an endpoint that does not').toBe(false);
    expect(/PdplBreachConsole[^>]*onDispatch/.test(page),
      'PrivacyPage must not pass a dispatch action to the breach console').toBe(false);
  });

  it('the console still drives the real submission recorder', () => {
    // Assert the CALL SHAPE, never the token: F2-194 and F2-205 both had a
    // `toContain('name')` leg satisfied by the import statement while every
    // call site was deleted.
    expect(/privacyApi\.recordSdaiaSubmission\s*\(/.test(consoleCode),
      'the console must record the SDAIA submission through the real endpoint').toBe(true);
    expect(/privacyApi\.sdaiaStatus\s*\(/.test(consoleCode),
      'the console must read the authoritative SDAIA status from the server').toBe(true);
  });

  // ── 5. what the operator READS must not promise what no column holds ──────

  /**
   * ⚠⚠ Three consecutive slices (F2-205, F2-207, F2-208) had the BROWSER find a
   * text-level claim after every code-scoped assertion was green. A claim about
   * what a surface does can live anywhere a user reads text, and the place
   * written specifically to be read is the place no code scan looks.
   *
   * Scoped to this module's own keys — other modules legitimately describe their
   * own generated documents — and it already fired once: `pdplConsoleEmpty` read
   * "All historical incidents are closed and notifications dispatched", while
   * every one of the 17 live breaches in both databases is NOT_REPORTED.
   */
  it('no pdpl/sdaia string claims the platform drafts or sends the notification', () => {
    for (const file of ['en.ts', 'ar.ts']) {
      const dict = stripComments(readFileSync(join(SRC, 'translations', file), 'utf8'));
      const family = [...dict.matchAll(/^\s{2}((?:pdpl|sdaia)[A-Za-z0-9_]*)\s*:\s*'([^']*)'/gm)];
      expect(family.length, `no pdpl*/sdaia* keys parsed out of ${file} — the leg would be vacuous`).toBeGreaterThan(80);
      for (const [, key, value] of family) {
        for (const claim of [
          'auto-generated', 'autogenerated', 'ready for dispatch', 'queued for dispatch',
          'notifications dispatched', 'notification dispatched',
          'تلقائية', 'مُرسَلة', 'جاهزة للإرسال',
        ]) {
          expect(value.toLowerCase().includes(claim.toLowerCase()),
            `${file}:${key} claims "${claim}" — the platform neither drafts the Article 25 submission nor sends it; SDAIA has no machine-readable API`).toBe(false);
        }
      }
    }
  });

  // ── 6. the detectors themselves ───────────────────────────────────────────

  /**
   * ⚠ A corpus assertion passes both when the app is clean and when the regex
   * has rotted, and a leg that has never fired is indistinguishable from a
   * vacuous one. Feed each detector the exact shape that existed before the fix.
   */
  it('SELF-TEST: the detectors fire on the shape that was there', () => {
    const fabricated = `
      const DEFAULT_CONTROLLER = { nameEn: 'TSS Saudi Holding', crNumber: '1010299292', dpoNameEn: 'Mona Al-Bishi' };
      export function generateSdaiaNotification(breach, severity) {
        return {
          internalRef: \`PDPL-N-\${breach.id}\`,
          containmentMeasures: { en: 'Affected systems isolated within the first hour.' },
          proposedMeasures: { en: 'Reset of all credentials in scope.' },
        };
      }
      const reportedReady = breach.status === 'NOTIFIED';
    `;
    // the retired-symbol legs
    expect(fabricated.includes('generateSdaiaNotification')).toBe(true);
    expect(fabricated.includes('DEFAULT_CONTROLLER')).toBe(true);
    // the invented-narrative leg
    expect(fabricated.includes('containmentMeasures')).toBe(true);
    expect(fabricated.includes('proposedMeasures')).toBe(true);
    // the identity leg
    expect(fabricated.includes('1010299292')).toBe(true);
    expect(fabricated.includes('Mona Al-Bishi')).toBe(true);
    // the wrong-column leg
    expect(fabricated.includes("status === 'NOTIFIED'")).toBe(true);

    // and a clean shape must NOT trip them — a detector that fires on anything
    // is a detector nobody can keep green (F2-207 deleted a leg for this).
    const clean = `
      const reported = isSdaiaReported(breach.sdaiaNotificationStatus);
      const value = article25Value(field, breach);
    `;
    expect(clean.includes('generateSdaiaNotification')).toBe(false);
    expect(clean.includes("status === 'NOTIFIED'")).toBe(false);
    expect(/isSdaiaReported\s*\(/.test(clean)).toBe(true);
  });

  /**
   * ⚠⚠ The comment stripper is load-bearing in BOTH directions here and this
   * pins both. `lib/pdplBreach.ts`'s header quotes 'Mona Al-Bishi', the CR
   * number and the invented containment sentence, because that is how a deletion
   * is recorded in this repo — so a raw-text ban would fail on a correct tree
   * (F2-202/F2-205's epitaph-as-corpse), and a stripper that removed nothing
   * would make every ban above vacuous.
   */
  it('SELF-TEST: the stripper removes the epitaph and keeps the code', () => {
    expect(libRaw.includes('Mona Al-Bishi'),
      "the header must still explain what was deleted, or the next reader re-adds it").toBe(true);
    expect(lib.includes('Mona Al-Bishi'),
      'the stripper must remove the header naming the retired literal').toBe(false);
    // …and it must not have eaten the file: the exports the console imports.
    for (const kept of ['ARTICLE_25_FIELDS', 'isSdaiaReported', 'computeBreachClock', 'classifySeverity']) {
      expect(lib.includes(kept), `the stripper removed real code (${kept} is missing)`).toBe(true);
    }
    /**
     * ⚠ A surviving-CHARACTER ratio is the wrong instrument and my first draft
     * used one (`> 40% of raw`). It failed on a correct tree, because this file
     * is 62% comment by design — the header explaining a retirement is longer
     * than the code that replaced it. F2-199 already recorded the right check
     * and I reached for a ratio anyway: count declarations ANCHORED TO LINE
     * STARTS, which a comment can never satisfy (every comment line here begins
     * `//` or ` *`), and require none to be lost. On the wrong stripper ordering
     * this drops to zero, because the `lazy()`/`export` block gets eaten.
     */
    const anchoredExports = (s: string) => (s.match(/^export\s/gm) ?? []).length;
    expect(anchoredExports(lib),
      'the stripper lost line-anchored export declarations — it corrupted the source, and a corrupted source yields a SMALLER population, which reads exactly like "this file is clean"')
      .toBe(anchoredExports(libRaw));
    expect(anchoredExports(lib), 'no exports parsed at all — the check would be vacuous').toBeGreaterThan(8);
  });
});
