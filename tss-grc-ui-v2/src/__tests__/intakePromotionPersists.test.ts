import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join } from 'node:path';

import { api, downloadFile, uploadFile, ApiError } from '../lib/api';

/**
 * F2-172 — intake promotion writes a record on the server, and the client never invents one.
 *
 * <h3>What this replaces</h3>
 * `promotedIntakeSafety.test.ts` held F2-171's containment: a promoted row's id was negative
 * by construction, and eleven register pages rendered it inert. That was the right fix for a
 * promotion that could not persist. It could not be the end state, because the row still
 * existed in exactly one browser — the intake's permanent history said <i>"Promoted to DPIA
 * #482913"</i> and no DPIA existed, on the client or the server.
 *
 * Promotion now goes to `PUT /api/v1/intake/{id}/triage`, which creates the register record
 * and stamps the id from the row it wrote. So the properties worth holding have moved:
 *
 *   1. `lib/intake` reaches the network when there is a session, and does not fabricate when
 *      that fails — F2-169's rule, on the queue where acting on a fabricated row is a
 *      governance decision about a case that does not exist;
 *   2. no page merges a client-only overlay into a register any more — the mechanism, the
 *      adapters and the 15 inert-row sites are gone rather than disabled;
 *   3. the client cannot name the record it claims was created;
 *   4. the api client still refuses a negative id at every entry point, which is now an
 *      unreachable invariant rather than a live guard, and is kept as one.
 */

const SRC = join(__dirname, '..');

function walk(dir: string): string[] {
  const out: string[] = [];
  for (const e of readdirSync(dir)) {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) out.push(...walk(p));
    else if ((p.endsWith('.tsx') || p.endsWith('.ts')) && !p.includes('.test.')) out.push(p);
  }
  return out;
}

/**
 * Strip comments — this tree's javadoc names the retired mechanism on purpose — and
 * normalise line endings first.
 *
 * ⚠ The CRLF normalisation is not tidiness. `core.autocrlf` gives this checkout `\r\n`, and
 * `readFileSync(f, 'utf8')` hands them through verbatim while Python's `read_text()`
 * translates them. Two multi-line assertions below were written against a Python probe that
 * matched, and failed here against the same source — an anchor that reports "this moved"
 * when nothing moved is the noisiest possible false negative, and the quiet version of the
 * same bug is an assertion that silently never matches (F2-169's vacuous `.not.toMatch`).
 */
function code(src: string): string {
  return src.replace(/\r\n/g, '\n').replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/(?<!:)\/\/[^\n]*/g, ' ');
}

describe('F2-172 — the client-only promotion overlay is gone, not disabled', () => {
  const ALL = walk(SRC).map(f => ({ path: f.slice(SRC.length + 1).replace(/\\/g, '/'), body: readFileSync(f, 'utf8') }));

  it('the file scan works at all', () => {
    // F2-69's floor. A collapsed walk would make every absence below vacuously true, and
    // "nothing references the overlay" is exactly the claim a broken scan makes for free.
    expect(ALL.length, 'the source walk found almost nothing').toBeGreaterThan(150);
    expect(ALL.some(f => f.path === 'lib/intake.ts')).toBe(true);
  });

  it('the overlay modules no longer exist', () => {
    // Deleted rather than emptied: a module that still exports `addPromoted` invites the
    // next feature to call it, and the whole defect was one call site away from correct.
    for (const gone of ['lib/promotedRecords.ts', 'lib/promotedAdapters.ts']) {
      expect(existsSync(join(SRC, gone)), `${gone} is back — promotion writes a real record now`)
        .toBe(false);
    }
  });

  it('nothing imports or references the retired overlay', () => {
    const offenders: string[] = [];
    for (const { path, body } of ALL) {
      const src = code(body);
      for (const name of ['usePromoted(', 'addPromoted(', 'mintPromotedId(', 'isUnsyncedId(']) {
        if (src.includes(name)) offenders.push(`${path} → ${name}`);
      }
    }
    expect(offenders,
      'These reference the client-only promoted-records overlay. Promotion writes a real row '
      + 'via PUT /api/v1/intake/{id}/triage, so the register\'s own list call returns it — '
      + 'merging a localStorage copy on top re-creates F2-171:\n  ' + offenders.join('\n  '))
      .toEqual([]);
  });

  /**
   * The provenance chip survived the retirement, and this is the assertion that says it is
   * backed by the server rather than by the cache it used to read. A badge sourced from
   * `localStorage` claims an origin nobody else can see; sourced from the intake row's
   * `promoted_to_record_id`, it is a fact.
   */
  it('the provenance badge reads the server, not browser storage', () => {
    const prov = readFileSync(join(SRC, 'lib', 'intakeProvenance.ts'), 'utf8');
    expect(prov).toMatch(/intakeApi\.list\(\)/);
    expect(code(prov), 'provenance must not come from localStorage again').not.toMatch(/localStorage/);
  });
});

describe('F2-172 — lib/intake reaches the server, and does not fabricate when it cannot', () => {
  const INTAKE = readFileSync(join(SRC, 'lib', 'intake.ts'), 'utf8');

  it('submit and triage call the intake API', () => {
    const src = code(INTAKE);
    expect(src, 'submit no longer posts to the server').toMatch(/intakeApi\.submit\(/);
    expect(src, 'triage no longer calls the server').toMatch(/intakeApi\.triage\(/);
    expect(src, 'the queue is no longer loaded from the server').toMatch(/intakeApi\.list\(/);
  });

  /**
   * ⚠ The anchor is the `catch` arm of the load, not the file. F2-171's own source scan
   * tested `/isUnsyncedId/.test(src)` and the *import line* satisfied it, so the regression a
   * reviewer would actually make passed clean. The regression here is "put something in the
   * list when the load fails", and it lives inside that arm.
   */
  it('a failed load shows nothing, and says that it failed', () => {
    const src = code(INTAKE);
    const catchArm = /\}\s*catch\s*\(e\)\s*\{([\s\S]*?)\}\s*finally/.exec(src);
    expect(catchArm, 'the reload() catch arm moved — repoint this assertion').toBeTruthy();
    expect(catchArm![1], 'the failed-load path must not substitute fabricated submissions')
      .not.toMatch(/SEED_INTAKE|setItems\(\s*\[\s*\{/);
    expect(catchArm![1], 'an empty list must be distinguishable from a failed load — the '
      + 'ambiguity four earlier findings on this programme turned out to be')
      .toMatch(/setError\(/);
    expect(catchArm![1]).toMatch(/setItems\(\[\]\)/);
  });

  /**
   * Stronger than the assertion this replaces. An earlier draft of this slice kept the five
   * fabricated `SEED_INTAKE` submissions for the no-session portal path and asserted only that
   * a signed-in triager could not see them. Deleting them outright is better and it is what
   * shipped: the portal's honest answer is that anonymous submission is blocked (F2-138), not
   * five invented cases with invented submitter names.
   */
  it('there is no fabricated inbox left to fall back to', () => {
    const src = code(INTAKE);
    expect(src, 'the seed fixture is back — an empty inbox is a fact, five invented '
      + 'submissions are not (F2-166, F2-169)').not.toMatch(/SEED_INTAKE/);
    expect(existsSync(join(SRC, 'lib', 'intakeSeed.ts')),
      'lib/intakeSeed.ts is back — it existed only to hold the fixture').toBe(false);
  });

  /**
   * The whole substrate ran on `localStorage` (F2-171), so the single most useful thing to
   * assert about this file is the absence of that word. It also closes the loophole an earlier
   * draft left open: a "fallback only when signed out" branch is still a branch that writes a
   * reference code the server never issued, and for a DSR that code represents a statutory
   * clock the platform has not started.
   */
  it('the intake substrate no longer touches browser storage at all', () => {
    expect(code(INTAKE), 'lib/intake.ts must not read or write localStorage on any path')
      .not.toMatch(/localStorage/);
  });
});

describe('F2-172 — the client cannot name the record the server created', () => {
  const API = code(readFileSync(join(SRC, 'lib', 'api.ts'), 'utf8'));

  it('the triage call sends a decision and a note, and nothing else', () => {
    const triage = /triage: \(id: number, body: \{([\s\S]*?)\}\)/.exec(API);
    expect(triage, 'intakeApi.triage moved — repoint this assertion').toBeTruthy();
    expect(triage![1], 'a caller-supplied target register or record id is the defect F2-172 '
      + 'removed. Jackson drops unknown properties silently, so re-adding one here would be '
      + 'obeyed by no server and rejected by none either — it would simply be a lie in a type.')
      .not.toMatch(/promoted/i);
    expect(triage![1]).toMatch(/decision:/);
    expect(triage![1]).toMatch(/note\?:/);
  });

  it('the submission body carries no promotion fields either', () => {
    const body = /export interface IntakeSubmissionBody \{([\s\S]*?)\n\}/.exec(API);
    expect(body).toBeTruthy();
    expect(body![1]).not.toMatch(/promoted/i);
  });
});

describe('F2-172 — a negative id still cannot leave the browser', () => {
  afterEach(() => vi.unstubAllGlobals());
  beforeEach(() => vi.unstubAllGlobals());

  function stubFetch() {
    const spy = vi.fn(async () => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', spy);
    return spy;
  }

  const NEGATIVE = '/api/v1/privacy/dpias/-1754000000000/approve';

  it('rejects every verb without making a request', async () => {
    const spy = stubFetch();
    await Promise.all([
      api.get(NEGATIVE), api.post(NEGATIVE, {}), api.put(NEGATIVE, {}),
      api.patch(NEGATIVE, {}), api.delete(NEGATIVE),
      downloadFile('/api/v1/risk/risks/-9/export', 'x.pdf'),
      uploadFile('/api/v1/evidence/-9/file', new File(['x'], 'x.txt')),
    ].map(p => expect(p).rejects.toBeInstanceOf(ApiError)));
    expect(spy, 'a request left the browser for an id no server issues').not.toHaveBeenCalled();
  });

  /** Carried over from `promotedIntakeSafety.test.ts` when that file was retired: a guard that
   *  refuses silently is indistinguishable from a dead button, which is the class of defect
   *  this programme keeps finding. Status 0 marks it client-side — no HTTP exchange happened,
   *  so any real status code would be a fabrication of its own. */
  it('names the cause, because a silent no-op reads as a broken page', async () => {
    stubFetch();
    await expect(api.post(NEGATIVE, {})).rejects.toThrow(/no server ever issues/i);
    await api.post(NEGATIVE, {}).catch((e: ApiError) => expect(e.status).toBe(0));
  });

  /**
   * ⚠ Anti-vacuity, and not optional: a path guard that rejected everything would satisfy
   * the assertion above while breaking the product. F2-169 shipped a negative assertion whose
   * regex could never match and stayed green with the defect re-inserted.
   */
  it('lets real paths through, including digits and a query string', async () => {
    const spy = stubFetch();
    await api.get('/api/v1/privacy/dpias/6');
    await api.get('/api/v1/privacy/consents?page=0&size=100');
    await api.post('/api/v1/risk/risks/152/accept', {});
    expect(spy).toHaveBeenCalledTimes(3);
  });

  /**
   * ⚠ Two vacuous versions of this preceded the working one (F2-171). `?delta=-5&sort=-id`
   * passes with or without the query being stripped — no value is preceded by a slash — and
   * `?filter=/-5&sort=-id` also passes, because the segment matcher needs `/` or end-of-string
   * after the digits and `&` is neither. The value has to put `/-5` where the unsplit string
   * would genuinely match, i.e. last.
   */
  it('does not mistake a negative value in the QUERY STRING for an id', async () => {
    const spy = stubFetch();
    await api.get('/api/v1/reporting/kpis?sort=-id&filter=/-5');
    expect(spy, 'the guard must inspect the path only').toHaveBeenCalledTimes(1);
  });
});
