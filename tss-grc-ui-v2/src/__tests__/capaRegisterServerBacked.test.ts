import { describe, it, expect } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { CAPA_ALLOWED, capaForwardPath, capaAbandonState } from '../lib/capa';

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * F2-208 — the CAPA register stays server-backed, and stays simpler than what it replaced.
 *
 * `useCapas()` used to MERGE three sources into one list: the real rows from
 * `GET /api/capa/tasks`, a twenty-row corpus in `lib/capa.ts`, and a `localStorage` overlay of
 * added rows and per-row status patches. The register showed 34 rows where 14 exist, and the
 * patches were applied on top of the SERVER rows when the list was assembled — so advancing a
 * real CAPA to "audit signed-off" changed what the screen said about a record the server had
 * never been told about, attributed to the literal `'demo.user@tss.sa'`.
 *
 * `POST /api/capa/tasks/{id}/transition` had existed the whole time, and it stamps the verifier
 * from the authenticated principal (F2-22b(vi)) precisely because a caller-supplied verifier is
 * a governance defect. So this is the F2-201 shape: a finished, hardened server control with a
 * client that shadowed it. Second member of that population to be REWIRED rather than retired.
 *
 * ⚠ THE LOAD-BEARING LEG IS BEHAVIOURAL, NOT STRUCTURAL. A rewire that kept a local status
 * write would look identical on screen and be the same defect, so
 * `noDecisionIsWrittenIntoLocalState` is the assertion that matters; the rest support it.
 *
 * ⚠ Every scan strips comments first. This codebase explains a deletion by naming what was
 * deleted — the two pages' headers quote `advance(c.id, to, 'demo.user@tss.sa')` and name the
 * eight retired states — so a raw-text scan reads the epitaph as the corpse. F2-193, F2-196,
 * F2-202, F2-205 and F2-207 all recorded that. Direction: comment-blind OVER-reports here, so
 * it fails loudly rather than shrinking a population.
 * ─────────────────────────────────────────────────────────────────────────────
 */

const SRC = join(__dirname, '..');
const read = (p: string) => readFileSync(join(SRC, p), 'utf8');
const STRIP = /(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g;
const strip = (s: string) => s.replace(STRIP, '');

const LIB = 'lib/capa.ts';
const NOTIFS = 'lib/capaNotifications.ts';
const PAGE = 'pages/capa/CapaPage.tsx';
const DETAIL = 'pages/detail/CapaDetailPage.tsx';
/** Everything that renders or derives a CAPA. */
const SURFACE = [LIB, NOTIFS, PAGE, DETAIL];

/** The five states the server has. Deliberately duplicated from nothing — the
 *  FE↔BE parity is `CapaMachineParityTest`'s job; this is only the retired set. */
const RETIRED_STATES = [
  'IDENTIFIED', 'IMPLEMENTING', 'OWNER_TESTED', 'INDEPENDENT_TESTED',
  'AUDIT_SIGNED_OFF', 'VERIFICATION_FAILED',
];

/**
 * The dictionaries — NOT part of SURFACE, and that is F2-465's instrument finding.
 *
 * The leg below bans every RETIRED_STATES token across SURFACE, so `VERIFICATION_FAILED`
 * was forbidden in four code files while sitting in `en.ts`, which is in none of them.
 * The prose leg that DOES read these files was scoped to a phrase list drawn from the two
 * tabs F2-208 retired (`root cause`, `effectiveness`, `fishbone`), and the retired sentence
 * used none of those words in English. Its ARABIC half did say `فحص فاعلية` — an
 * effectiveness check — and escaped for a third reason: the filter's token was the definite
 * `الفاعلية` and the string carried the bare `فاعلية`, so the two halves of one bilingual
 * filter had different reach. A file list, a phrase list, and a definite article.
 */
const DICTS = ['translations/en.ts', 'translations/ar.ts'];

/**
 * Every `capa*` key a dictionary declares. Comments are stripped first: this codebase
 * explains a deletion by naming what was deleted, so a raw scan reads the epitaph as the
 * corpse (F2-193) — and both retirement notes below name the retired key.
 */
function capaKeys(dict: string): { key: string; value: string }[] {
  return [...strip(read(dict)).matchAll(/^ {2}(capa[A-Za-z0-9_]*):\s*'([^']*)'/gm)]
    .map(m => ({ key: m[1], value: m[2] }));
}

/** A state ladder written in prose: the arrow the retired sentence joined its stages with. */
const PROSE_LADDER = /→/;

/** An UPPER_SNAKE token — the shape a state name takes when prose quotes one verbatim. */
const SNAKE_TOKEN = /\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b/g;

/**
 * Every `setSomething(...)` call's argument text, brace-balanced.
 *
 * Used by the load-bearing leg below. `set[A-Z]` matches React's `useState` setters by the
 * house naming convention (`setItems`, `setOverlay`, `setStatus`) and nothing else on this
 * surface — the balanced walk matters because an updater callback is several lines long and a
 * line-based scan would miss the property that sits in the middle of it.
 */
function stateSetterArguments(src: string): string[] {
  const out: string[] = [];
  const re = /\bset[A-Z]\w*\s*\(/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(src)) !== null) {
    let depth = 1;
    let i = m.index + m[0].length;
    const start = i;
    while (i < src.length && depth > 0) {
      const c = src[i];
      if (c === '(') depth++;
      else if (c === ')') depth--;
      i++;
    }
    out.push(src.slice(start, i - 1));
  }
  return out;
}

/** Fields that exist on no column of `capa.tasks` (26 columns, read from sys.columns). */
const COLUMNLESS_FIELDS = [
  'rcaMethod', 'rcaSteps', 'rootCauseCategory', 'effectivenessDueDate',
  'effectivenessVerified', 'parentCapaId', 'customerDisclosureRequired',
  'frameworkRefs', 'ownerTestResult', 'independentTestResult', 'auditSignedOffBy',
];

describe('F2-208 — the CAPA register is server-backed', () => {
  it('the fabricated preventive-action trigger engine stays deleted', () => {
    expect(existsSync(join(SRC, 'lib/paTriggers.ts')),
      'lib/paTriggers.ts derived "a KRI has breached, file a preventive action" from '
      + 'pages/risk/mockRisks. With a real create wired, its one-click "Open PA" would have '
      + 'turned an invented breach into a real row in capa.tasks — a real write driven by a '
      + 'fabricated signal, which is worse than the fake write it replaced.').toBe(false);

    for (const f of SURFACE) {
      expect(strip(read(f)), `${f} still imports the retired trigger engine`)
        .not.toMatch(/from\s+['"][^'"]*paTriggers['"]/);
    }
  });

  it('nothing on the CAPA surface reads or writes localStorage', () => {
    for (const f of SURFACE) {
      expect(strip(read(f)), `${f} touches localStorage. The overlay was durable, which is why `
        + 'the reload that should have exposed the fabrication confirmed it instead — unlike '
        + "F2-201's approvals page, which reverted on refresh and was eventually noticed.")
        .not.toMatch(/localStorage/);
    }
  });

  it('there is no seed corpus behind the register', () => {
    const lib = strip(read(LIB));
    expect(lib, 'a `CAPAS` corpus is back in lib/capa.ts').not.toMatch(/\bCAPAS\b/);
    // The shape, not the name: an exported array of object literals in this module is a corpus
    // whatever it is called (F2-194's lesson — the de-mocking boundary was a FILENAME).
    expect(lib, 'lib/capa.ts exports an array literal of records — that is a corpus under a new name')
      .not.toMatch(/export const \w+\s*:\s*Capa\[\]\s*=\s*\[/);
  });

  it('no decision is written into local state — the load-bearing leg', () => {
    const lib = strip(read(LIB));

    // The row must be replaced from the server's response, not patched locally.
    expect(lib, 'transition must call the API client, not compute the next row')
      .toMatch(/capaTaskApi\.transition\s*\(/);
    expect(lib, 'the row must be built from the server response via fromCapaTaskDto')
      .toMatch(/fromCapaTaskDto\(await capaTaskApi\.transition/);

    // ⚠⚠ THE RULE, and it took two attempts to get right. My first draft banned the literal
    // shape `{ ...c, status: to }` — and the pre-fix control showed it SILENT against master,
    // because the real code built `{ ...existingPatch, ...stampField, ...(extra ?? {}), status: to,
    // history: [...] }`: three spreads between the brace and the property, so a pattern demanding
    // them adjacent could not see the defect it was written for. 🔑 A detector written for the
    // shape you imagine, rather than the shape that existed, reads as a passing gate.
    //
    // What actually made the fabrication work is narrower and checkable: a status value reaching a
    // React state setter. `setOverlay(prev => ({ …, status: to }))` is the defect; the fix's
    // `setItems(prev => prev.map(c => c.id === id ? saved : c))` mentions no status at all,
    // because `saved` came from the server.
    for (const f of SURFACE) {
      for (const call of stateSetterArguments(strip(read(f)))) {
        expect(call, `${f} passes a status into a React state setter: ${call.slice(0, 90)}`)
          .not.toMatch(/\bstatus\b/);
      }
    }

    // ⚠ And the banning-a-word version is still not enough on its own: the mutation harness
    // showed `setItems(prev => prev.map(c => c.id === id ? { ...c, ...body } : c))` SURVIVING it,
    // because a spread carries the status without ever naming it. 🔑 Ban the shape by requiring
    // the positive instead — every write to the register's state must be the SERVER's row or an
    // explicit clear. There is no third legitimate source.
    for (const call of stateSetterArguments(lib)) {
      const allowed = /fromCapaTaskDto|\bsaved\b|^\s*\[\s*\]\s*$|^\s*(?:true|false|null)\s*$|^\s*e instanceof/.test(call);
      expect(allowed,
        `lib/capa.ts writes register state from something that is not the server's response: `
        + `${call.slice(0, 90)}`).toBe(true);
    }

    // And no status literal is assigned to a row anywhere on the surface.
    for (const f of SURFACE) {
      expect(strip(read(f)), `${f} assigns a status literal to a record`)
        .not.toMatch(/\.status\s*=\s*['"]/);
    }
  });

  it('no state the server cannot store is declared or named', () => {
    for (const f of SURFACE) {
      const src = strip(read(f));
      for (const s of RETIRED_STATES) {
        expect(src, `${f} still names ${s}, which capa.tasks cannot hold. A state only the browser `
          + 'can reach is a state only the browser knows about.').not.toContain(s);
      }
    }
  });

  it('no field with no column is declared on the Capa type', () => {
    const lib = strip(read(LIB));
    for (const field of COLUMNLESS_FIELDS) {
      expect(lib, `${field} is back on the Capa type. It has no column on capa.tasks, so a value `
        + 'for it can only have come from the browser — re-adding it needs a migration first.')
        .not.toContain(field);
    }
  });

  it('no client-side identity is used as an actor or a verifier', () => {
    for (const f of SURFACE) {
      const src = strip(read(f));
      expect(src, `${f} carries a hard-coded mailbox. The verifier on a CAPA closure is `
        + 'capa.tasks.verified_by, stamped by the server from the signed-in principal — the whole '
        + 'point of the column.').not.toMatch(/@tss\.sa/);
      expect(src, `${f} names demo.user`).not.toContain('demo.user');
    }
  });

  it('no business reference is minted in the browser', () => {
    for (const f of SURFACE) {
      expect(strip(read(f)), `${f} mints an identifier. The server owns external_id — F2-208 `
        + 'closed the last six create paths that let a caller choose one.')
        .not.toMatch(/Math\.random/);
    }
  });

  it('the page header does not advertise what the register no longer has', () => {
    // F2-207's lesson, second application: a claim about what a surface DOES can live anywhere a
    // user reads text, and a gate scoped to code cannot see the sentence written to be read.
    // `capaPageSub` promised "a documented root cause, verification gates, and effectiveness
    // check" — one of the three exists. Scoped to this module's own keys, because other modules
    // legitimately talk about root causes.
    for (const dict of DICTS) {
      const keys = capaKeys(dict);
      expect(keys.length, `${dict}: no capa* keys parsed — the parse has rotted`)
        .toBeGreaterThan(40);
      // ⚠ F2-465 widened two of these tokens. `الفاعلية` is the DEFINITE form, so it could not
      // match the bare `فاعلية` in `capaWorkflowFootnote`'s "فحص فاعلية بعد 90 يوماً" — the two
      // halves of one bilingual filter had different reach, and the Arabic half of the claim
      // escaped for that reason alone. `effectiveness` → `effectiv` for the same asymmetry in
      // the other direction. Measured: widened, they hit that one key and nothing else of 85.
      const claims = keys.filter(k =>
        /root cause|السبب الجذري|effectiv|فاعلية|fishbone|عظمة السمكة/i.test(k.value));
      expect(claims.map(c => `${c.key} = "${c.value}"`),
        `${dict}: a capa* label still describes root-cause analysis or an effectiveness re-test. `
        + 'Neither has a column; retiring a tab means retiring the sentence that advertised it.')
        .toEqual([]);
    }
  });

  // ── F2-465 — the prose about the machine is held to the machine ─────────────────────

  it('no capa label writes the machine out in prose', () => {
    // A state ladder in a translation string is a SECOND COPY of the vocabulary, and F2-196's
    // rule is that a second copy drifts from the one with a vote. This one drifted all the way:
    // six stages, of which the register has one. Composing the sentence from CAPA_STATUS_LABEL
    // is always available, so there is no correct code for this zero to fail on (F2-214).
    for (const dict of DICTS) {
      const keys = capaKeys(dict);
      expect(keys.length, `${dict}: no capa* keys parsed — the parse has rotted`)
        .toBeGreaterThan(40);
      const ladders = keys.filter(k => PROSE_LADDER.test(k.value));
      expect(ladders.map(k => `${k.key} = "${k.value}"`),
        `${dict}: a capa* label writes the state machine out in prose. Compose it from `
        + 'CAPA_STATUS_LABEL and capaForwardPath() instead — a sentence written beside the '
        + 'machine is free to disagree with the lanes rendered directly above it, which is '
        + 'exactly what capaWorkflowFootnote did.')
        .toEqual([]);
    }
  });

  it('no capa label names a token the register cannot store', () => {
    // The vocabulary is DERIVED from the machine, never listed here: a second list is the
    // defect this whole gate exists for. `LOW`/`HIGH`/`CORRECTIVE` are single words and so
    // do not match SNAKE_TOKEN, which is why the severity and type labels are unaffected.
    const vocabulary = new Set(Object.keys(CAPA_ALLOWED));
    expect(vocabulary.size, 'the machine parsed to nothing — the import, not the label, is broken')
      .toBeGreaterThan(3);
    for (const dict of DICTS) {
      const offenders: string[] = [];
      for (const { key, value } of capaKeys(dict)) {
        const bad = [...value.matchAll(SNAKE_TOKEN)]
          .map(m => m[0]).filter(t => !vocabulary.has(t));
        if (bad.length > 0) offenders.push(`${key} names ${[...new Set(bad)].join(', ')}`);
      }
      expect(offenders, `${dict}: a capa* label quotes a state token this register has no state `
        + 'for. VERIFICATION_FAILED was banned across the four SURFACE files while sitting in '
        + 'this dictionary, which is in none of them.')
        .toEqual([]);
    }
  });

  it('the workflow sentence is composed from the machine, not written beside it', () => {
    // Assert the CALL SHAPE, never the bare token: an import line satisfies `toContain` and
    // says nothing about whether the page calls anything (F2-194, F2-205).
    const page = strip(read(PAGE));
    expect(page, `${PAGE} no longer composes the workflow sentence from the machine. The stage `
      + 'names must come from capaForwardPath() so the sentence and the lanes cannot disagree.')
      .toMatch(/capaForwardPath\s*\(/);
    for (const dict of DICTS) {
      expect(capaKeys(dict).map(k => k.key),
        `${dict}: capaWorkflowFootnote is back. It is the machine written out in prose.`)
        .not.toContain('capaWorkflowFootnote');
    }
  });

  it('the forward path is derived from the machine and names no state itself', () => {
    expect(capaForwardPath()).toEqual(['OPEN', 'IN_PROGRESS', 'AWAITING_VERIFICATION', 'CLOSED']);
    expect(capaAbandonState()).toBe('CANCELLED');

    // Whatever the machine becomes, the sentence can only ever name states it declares.
    const vocabulary = new Set(Object.keys(CAPA_ALLOWED));
    for (const s of capaForwardPath()) expect(vocabulary.has(s)).toBe(true);

    // …and "derived" is structural, not a claim: the three functions contain no state literal,
    // so there is nothing in them to drift. Comment-stripped, because the javadoc explaining
    // the tie-break legitimately names all five (F2-193).
    const lib = strip(read(LIB));
    const from = lib.indexOf('function capaRootState(');
    const to = lib.indexOf('export const CAPA_STATUS_LABEL');
    expect(from, 'capaRootState not found — the slice, not the code, is broken')
      .toBeGreaterThan(-1);
    expect(to).toBeGreaterThan(from);
    const derivation = lib.slice(from, to);
    for (const s of Object.keys(CAPA_ALLOWED)) {
      expect(derivation, `the derivation names '${s}'. A state literal inside the code that `
        + 'derives the order is the second copy the derivation exists to avoid.')
        .not.toContain(`'${s}'`);
    }
  });

  it('the detectors fire — synthetic positive controls, not corpus assertions', () => {
    // ⚠ Every assertion above passes on a clean tree AND on a tree where the regexes have
    // stopped matching. F2-195's rule: carry a self-test. Each pattern here is the one used
    // above, fed the shape it is meant to catch.
    // The state-setter walk, fed the real pre-fix shape — three spreads before the property,
    // which is what defeated the first draft of this leg.
    const realDefect =
      'setOverlay(prev => ({ ...prev, patches: { ...existing, ...stampField, ...(extra ?? {}), '
      + 'status: to, history: updatedHistory } }));';
    expect(stateSetterArguments(realDefect).some(a => /\bstatus\b/.test(a)),
      'the state-setter walk must catch a status buried behind several spreads').toBe(true);
    // And the fix's own shape must NOT trip it, or the gate fails on correct code.
    expect(stateSetterArguments('setItems(prev => prev.map(c => (c.id === id ? saved : c)));')
      .some(a => /\bstatus\b/.test(a))).toBe(false);

    expect("export const CAPAS: Capa[] = [")
      .toMatch(/export const \w+\s*:\s*Capa\[\]\s*=\s*\[/);

    expect("advance(c.id, to, 'demo.user@tss.sa');").toMatch(/@tss\.sa/);

    expect("`capa-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`")
      .toMatch(/Math\.random/);

    // And the comment stripper: a line comment whose prose contains a block-open must not
    // swallow the code after it (F2-198). Both directions.
    const tricky = "// see /* the note */\nconst live = 1;\n/* gone */\nconst also = 2;";
    expect(strip(tricky)).toContain('const live = 1;');
    expect(strip(tricky)).toContain('const also = 2;');
    expect(strip(tricky)).not.toContain('gone');

    // ── F2-465's detectors, fed the shapes they exist to catch ──────────────────────────
    const retiredEn = 'Forward path: Identified → Implementing → Owner-tested (1LoD) → '
      + 'Independent-tested (2LoD) → Audit signed-off (3LoD) → Closed. A 90-day post-closure '
      + 'check fires VERIFICATION_FAILED on regression.';
    const retiredAr = 'المسار الأمامي: مُحدّدة → قيد التنفيذ → اختبار المالك → اختبار مستقل → '
      + 'اعتماد المراجعة → مغلقة. فحص فاعلية بعد 90 يوماً من الإغلاق يحوّلها إلى فشل التحقّق '
      + 'عند التراجع.';

    // The ladder ban is the only one of the three that catches BOTH languages, which is why
    // it is the load-bearing leg: the token ban is blind to the Arabic, where the state name
    // is translated rather than quoted.
    expect(PROSE_LADDER.test(retiredEn)).toBe(true);
    expect(PROSE_LADDER.test(retiredAr)).toBe(true);
    expect(PROSE_LADDER.test('Any unfinished state can instead end as Cancelled.')).toBe(false);

    const vocabulary = new Set(Object.keys(CAPA_ALLOWED));
    const snakeOf = (s: string) =>
      [...s.matchAll(SNAKE_TOKEN)].map(m => m[0]).filter(t => !vocabulary.has(t));
    expect(snakeOf(retiredEn)).toEqual(['VERIFICATION_FAILED']);
    expect(snakeOf(retiredAr)).toEqual([]);
    // A label naming a REAL state must not trip it, or the gate fails on correct code (F2-214).
    expect(snakeOf('Moves to AWAITING_VERIFICATION on submit.')).toEqual([]);
    // …and a bare severity word is not an UPPER_SNAKE token at all — the reason the severity
    // and type labels are outside this population by construction rather than by exemption.
    expect(snakeOf('CRITICAL items first.')).toEqual([]);

    // F2-208's filter, as F2-465 widened it: the definite `الفاعلية` could not match this.
    const widened = /root cause|السبب الجذري|effectiv|فاعلية|fishbone|عظمة السمكة/i;
    expect(widened.test(retiredAr), 'the widened Arabic token must catch the bare form').toBe(true);
    expect(/الفاعلية/.test(retiredAr), 'the definite form is why the Arabic half escaped')
      .toBe(false);
    expect(widened.test('المسار الأمامي:')).toBe(false);
  });
});
