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
   * reviewer would actually make passed clean. The regression here is "restore the seed on a
   * failed load", and it lives inside that arm.
   */
  it('a failed load for a signed-in user shows nothing, not the seed records', () => {
    const src = code(INTAKE);
    const catchArm = /\}\s*catch\s*\{([\s\S]*?)\}\s*finally/.exec(src);
    expect(catchArm, 'the refresh() catch arm moved — repoint this assertion').toBeTruthy();
    expect(catchArm![1], 'the failed-load path must not substitute fabricated submissions')
      .not.toMatch(/SEED_INTAKE|setServer\(\s*\[/);
    expect(catchArm![1], 'a failed load must say so').toMatch(/setLoadFailed\(true\)/);
  });

  it('the seed records are reachable only without a session', () => {
    const src = code(INTAKE);
    // SEED_INTAKE may be defined and exported; what matters is that the branch consuming it
    // is the `signedIn ? … : …` false arm.
    const items = /const items: IntakeRecord\[\] = ([\s\S]*?);\n\n/.exec(src);
    expect(items, 'the items expression moved — repoint this assertion').toBeTruthy();
    expect(items![1]).toMatch(/signedIn\s*\n?\s*\?/);
    const [signedInArm, signedOutArm] = items![1].split(/:\s*\[/);
    expect(signedInArm, 'a signed-in user must not be shown seed submissions')
      .not.toMatch(/SEED_INTAKE/);
    expect(signedOutArm, 'the no-session portal path still needs its seed (F2-138)')
      .toMatch(/SEED_INTAKE/);
  });

  it('promotion cannot happen without a session', () => {
    // The triager inbox is behind a login, so the no-session branch has no legitimate
    // promotion to perform — and a fallback there is the defect verbatim.
    expect(code(INTAKE)).toMatch(/if \(!signedIn\)[\s\S]{0,200}throw new Error/);
  });
});

describe('F2-172 — the client cannot name the record the server created', () => {
  const API = code(readFileSync(join(SRC, 'lib', 'api.ts'), 'utf8'));

  it('the triage call sends a decision and a note, and nothing else', () => {
    const triage = /triage: \(id: number, decision:[\s\S]*?\),\n/.exec(code(API));
    expect(triage, 'intakeApi.triage moved — repoint this assertion').toBeTruthy();
    expect(triage![0], 'a caller-supplied record id is the defect F2-172 removed')
      .not.toMatch(/promotedTo/);
    expect(triage![0]).toMatch(/\{ decision, note \}/);
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
