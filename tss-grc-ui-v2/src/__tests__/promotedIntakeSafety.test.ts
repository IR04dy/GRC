import { describe, it, expect, vi, afterEach } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { mintPromotedId, isUnsyncedId, type PromotedRecord } from '../lib/promotedRecords';
import * as adapters from '../lib/promotedAdapters';
import { api, downloadFile, uploadFile, ApiError } from '../lib/api';

/**
 * F2-171 — a promoted intake row is a placeholder, and nothing may treat it as a record.
 *
 * <b>What was wrong.</b> Pressing Promote on an intake submission writes a row to
 * `localStorage` and to nothing else — no `privacy.dpia`, `risk.risks` or `incident.incidents`
 * row is created, and `POST /api/v1/intake` + `PUT /api/v1/intake/{id}/triage` exist on the
 * server and are called by no product code. Eleven register pages then merge those rows in
 * ahead of the real ones and render the same live governance actions on them: Approve a DPIA,
 * advance an incident phase, close a risk, delete a vendor.
 *
 * The id those actions used came from `Date.now() % 1_000_000` — a **positive** integer in the
 * exact range SQL Server IDENTITY values occupy. Two files documented the opposite:
 * `promotedAdapters.ts` promised "*negative* synthesized ids … so they never collide with
 * backend-assigned ids" and `PromotedRecord.id` promised "highest existing + 1". Three accounts
 * of one value, and the live one was the unsafe one. Measured in dev, 0.056% of promotions would
 * name some existing record across the eleven registers (Σ max(id) ÷ 1,000,000), and that share
 * grows with every row a tenant adds.
 *
 * <b>Why F2-137 could not see it.</b> That guard asks whether a route backed by `lib/intake`
 * is *public* — its scope is the `/portal/` and `/public/` prefixes. This path is entirely
 * authenticated: a signed-in DPO presses New → DPIA, is routed to the Services Center, and a
 * triager promotes it. F2-137 stays green, correctly, and its premise test ("lib/intake really
 * does make zero network calls") is what proves the substrate is still client-only.
 *
 * <b>What is enforced here.</b> Two independent layers, because the UX layer is per-call-site
 * and therefore forgettable:
 *   1. the id can never name a real record — negative by construction, coerced on read;
 *   2. the api client refuses such an id at all three network entry points, so a page that
 *      forgets to disable a button produces an honest error instead of touching a real row.
 * Layer 2 is the control. Layer 1 is what makes it decidable.
 */

const REC = (id: number | string): PromotedRecord => ({
  id,
  sourceIntakeId: 'intake-abc',
  sourceReferenceCode: 'DI-K7L9-Z4M2',
  promotedAt: '2026-08-09T10:00:00.000Z',
  payload: { projectName: 'Falcon', summary: 'x', observation: 'y', scope: 'z', vendorName: 'v' },
});

describe('F2-171 — the placeholder id can never name a real record', () => {
  it('mints only negative, safe, distinct ids', () => {
    const ids = Array.from({ length: 50 }, () => mintPromotedId());
    for (const id of ids) {
      expect(id, 'a positive id lands in the range real IDENTITY values occupy').toBeLessThan(0);
      expect(Number.isSafeInteger(id), 'a rounded id would start colliding with itself').toBe(true);
    }
    // The old scheme repeated every 1000 s and within a millisecond; both are covered by
    // minting 50 in a tight loop, which is faster than the clock ticks.
    expect(new Set(ids).size, 'two promotions shared an id').toBe(ids.length);
  });

  it('recognises a placeholder id and nothing else', () => {
    expect(isUnsyncedId(-1)).toBe(true);
    expect(isUnsyncedId(-1_754_000_000_000_001)).toBe(true);
    expect(isUnsyncedId(1)).toBe(false);
    expect(isUnsyncedId(0)).toBe(false);
    expect(isUnsyncedId(482_913), 'the id the old scheme would have minted').toBe(false);
    // Deliberately excluded: real records elsewhere carry string external ids (bow-ties, ML
    // models), so a string rule would call those unsynced too.
    expect(isUnsyncedId('capa-intake-abc')).toBe(false);
    expect(isUnsyncedId(null)).toBe(false);
    expect(isUnsyncedId(undefined)).toBe(false);
    expect(isUnsyncedId(Number.NaN)).toBe(false);
  });

  /**
   * The overlay is browser storage — there is no migration step for it. Records promoted
   * before this fix are sitting in users' `localStorage` right now with positive ids, so the
   * adapters have to repair on read rather than trust the producer.
   */
  it('coerces a legacy positive overlay id in every numeric adapter', () => {
    const numeric = [
      'toIncidentDto', 'toDsrDto', 'toBreachDto', 'toRiskResponse', 'toVendorDto',
      'toEthicsCaseDto', 'toDpiaDto', 'toBiaDto', 'toEngagementDto', 'toFindingDto',
      'toExceptionDto', 'toChangeRequestDto',
    ] as const;

    // Anti-vacuity: if the export names drift, this must fail rather than test nothing.
    expect(numeric.length, 'adapter list is stale').toBe(12);

    for (const name of numeric) {
      const fn = (adapters as unknown as Record<string, (p: PromotedRecord) => { id: number }>)[name];
      expect(fn, `${name} is no longer exported`).toBeTypeOf('function');
      expect(fn(REC(482_913)).id, `${name} passed a positive legacy id through`).toBe(-482_913);
      expect(fn(REC(-77)).id, `${name} altered an already-safe id`).toBe(-77);
    }
  });

  it('the one adapter that mints a string still does, and is not forced negative', () => {
    // `toCapaFromObservation` was the only one of the thirteen honouring the documented
    // "clearly distinct scheme". Regressing it to a number would silently re-enter the
    // numeric id space.
    expect(adapters.toCapaFromObservation(REC(5)).id).toBe('capa-intake-abc');
  });

  /**
   * ⚠ The defect was at the *producer*, and every assertion above tests the helper instead.
   * Mutation caught that: reverting `intake.ts` to `Date.now() % 1_000_000` left all of them
   * green, because none of them go through the code that was wrong. This one does — it drives
   * the real hook and reads what landed in the overlay.
   *
   * <p>Rewritten by F2-172. It used to promote one of five fabricated `SEED_INTAKE` records
   * through a synchronous hook; the seed is deleted and `triage()` now awaits
   * `PUT /api/v1/intake/{id}/triage`, so the server is stubbed and the hook is driven for
   * real. The property under test is unchanged and still the one that matters: whatever the
   * server answers, the placeholder the overlay receives must be negative.
   *
   * <p>Note what the stub deliberately returns — `promotedToRecordId: null`. That is the
   * server's real behaviour after F2-172: it refuses a client-supplied target id and creates
   * no downstream row, so the register overlay is the only thing holding a local id and it
   * must never be mistakable for a record.
   */
  it('promoting a real intake through the hook lands a negative id in the overlay', async () => {
    localStorage.clear();
    const { renderHook, act, waitFor } = await import('@testing-library/react');
    const apiMod = await import('../lib/api');
    const { useIntake } = await import('../lib/intake');
    const { usePromoted } = await import('../lib/promotedRecords');

    const wire = (status: string, promotedTo: string | null) => ({
      id: 4321, referenceCode: 'DI-K7L9-Z4M2', kind: 'DPIA_INITIATION', status,
      channel: 'WEB_FORM', submitterIsAnonymous: false, submitterUserId: 5,
      submitterRole: 'EMPLOYEE', submitterName: null, submitterEmail: null, submitterPhone: null,
      payload: '{"projectName":"Falcon"}', assignedTo: 'DPO', slaDueAt: null, severity: 'MEDIUM',
      acknowledgedAt: '2026-08-09T10:00:00', promotedToEntityType: promotedTo,
      promotedToRecordId: null, promotedAt: promotedTo ? '2026-08-09T11:00:00' : null,
      rejectionReason: null, locale: 'en', history: [],
    });

    vi.spyOn(apiMod.intakeApi, 'list').mockResolvedValue(
      { content: [wire('TRIAGING', null)], totalElements: 1 } as never);
    const triageSpy = vi.spyOn(apiMod.intakeApi, 'triage')
      .mockResolvedValue(wire('PROMOTED', 'DPIA') as never);

    const intake = renderHook(() => useIntake());
    await waitFor(() => expect(intake.result.current.loading).toBe(false));
    const target = intake.result.current.items[0];
    expect(target, 'the stubbed inbox produced no record — the hook no longer reads it').toBeTruthy();
    expect(typeof target.id, 'the record id is the server id now, not a client string').toBe('number');

    let outcome: { entityType: string; recordId: string | number } | null = null;
    await act(async () => { outcome = await intake.result.current.triage(target.id, 'PROMOTE'); });
    expect(triageSpy, 'triage did not reach the server — it is a local mutation again')
      .toHaveBeenCalledWith(4321, { decision: 'PROMOTE' });
    expect(outcome, 'triage returned no promotion result').toBeTruthy();

    const recordId = outcome!.recordId;
    expect(typeof recordId === 'number' && recordId < 0,
      `promotion minted ${recordId} — a positive id lands in the range real records occupy`).toBe(true);

    const overlay = renderHook(() => usePromoted(outcome!.entityType as never));
    await waitFor(() => expect(overlay.result.current.length).toBeGreaterThan(0));
    expect(overlay.result.current.some(r => r.id === recordId)).toBe(true);
    localStorage.clear();
  });

  /**
   * F2-168's lesson, which cost that finding its whole slice: keys and record ids have to move
   * together. The provenance badge is a `Map<number, code>` looked up by the *adapted* row id,
   * so a coercion applied in one place and not the other turns the badge off silently — the
   * kind of regression that shows up as "the ↩ chip stopped appearing" long after the commit.
   */
  it('keys the provenance map with the same coercion the adapters apply', async () => {
    localStorage.clear();
    const { renderHook } = await import('@testing-library/react');
    localStorage.setItem('grc.promotedRecords', JSON.stringify({
      byEntity: { RISK: [{ ...REC(482_913) }] },   // a legacy POSITIVE id, as already in the wild
    }));

    const { result } = renderHook(() => adapters.usePromotedProvenance('RISK'));
    const rowId = adapters.toRiskResponse(REC(482_913)).id;
    expect(result.current.get(rowId), 'the badge would not resolve for this row').toBe('DI-K7L9-Z4M2');
    expect(result.current.has(482_913), 'still keyed by the raw positive id').toBe(false);
    localStorage.clear();
  });
});

describe('F2-171 — the api client refuses a placeholder id at every entry point', () => {
  afterEach(() => vi.unstubAllGlobals());

  function stubFetch() {
    const spy = vi.fn(async () => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', spy);
    return spy;
  }

  const PLACEHOLDER = '/api/v1/privacy/dpias/-1754000000000/approve';

  it('rejects every verb without making a request', async () => {
    const spy = stubFetch();
    const calls: Array<Promise<unknown>> = [
      api.get(PLACEHOLDER), api.post(PLACEHOLDER, {}), api.put(PLACEHOLDER, {}),
      api.patch(PLACEHOLDER, {}), api.delete(PLACEHOLDER),
      downloadFile('/api/v1/risk/risks/-9/export', 'x.pdf'),
      uploadFile('/api/v1/evidence/-9/file', new File(['x'], 'x.txt')),
    ].map(p => expect(p).rejects.toBeInstanceOf(ApiError));
    await Promise.all(calls);
    expect(spy, 'a request left the browser for a record that does not exist').not.toHaveBeenCalled();
  });

  it('names the cause, because a silent no-op reads as a broken page', async () => {
    stubFetch();
    await expect(api.post(PLACEHOLDER, {})).rejects.toThrow(/not been saved to the server/i);
    // Status 0 marks it client-side — no HTTP exchange happened, so no status code is honest.
    await api.post(PLACEHOLDER, {}).catch((e: ApiError) => expect(e.status).toBe(0));
  });

  /**
   * ⚠ The anti-vacuity half, and the reason it is not optional: F2-169 shipped a negative
   * assertion whose regex could never match, so it stayed green with the defect re-inserted.
   * A path guard that rejects everything would pass every test above.
   */
  it('lets a real path through, including ones containing digits and a query string', async () => {
    const spy = stubFetch();
    await api.get('/api/v1/privacy/dpias/6');
    await api.get('/api/v1/privacy/consents?page=0&size=100');
    await api.post('/api/v1/risk/risks/152/accept', {});
    expect(spy).toHaveBeenCalledTimes(3);
  });

  /**
   * ⚠ Two vacuous versions of this preceded the working one, and only mutation found either.
   * `?delta=-5&sort=-id` passes with or without the query being stripped — no value is preceded
   * by a slash. `?filter=/-5&sort=-id` also passes: the segment matcher requires `/` or
   * end-of-string after the digits, and `&` is neither. The value has to put `/-5` where the
   * unsplit string would genuinely match, i.e. last. A test named after a property it cannot
   * distinguish is worse than no test — it reports the property as held.
   */
  it('does not mistake a negative value in the QUERY STRING for a placeholder id', async () => {
    const spy = stubFetch();
    await api.get('/api/v1/reporting/kpis?sort=-id&filter=/-5');
    expect(spy, 'the guard must inspect the path only — a query value is not a path segment')
      .toHaveBeenCalledTimes(1);
  });
});

/**
 * The UX half. This one is a source scan, which in this programme has invented more defects
 * than it has found — so it is scoped to a measured file list, states its own limit, and
 * carries a floor so a broken matcher cannot read as clean.
 */
describe('F2-171 — every register that merges promoted rows makes them inert', () => {
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

  /** Pages that merge the overlay into a list — measured, not listed. */
  const mergers = walk(join(SRC, 'pages'))
    .map(f => ({ f, src: readFileSync(f, 'utf8') }))
    .filter(({ src }) => /usePromoted\(/.test(src));

  it('finds the merging pages at all', () => {
    // F2-69's lesson: a scan that finds nothing must never read as a clean one.
    expect(mergers.length, 'no page merges usePromoted — the matcher broke').toBeGreaterThanOrEqual(8);
  });

  /**
   * ⚠ Anchored inside the `disabled={…}` expression, NOT on the file containing the identifier
   * anywhere. The first version tested `/isUnsyncedId/.test(src)`, and mutation showed why that
   * is worthless: deleting the guard from the expression leaves the *import* behind, so the
   * regression a reviewer would actually make — editing the condition — passed clean. Same class
   * as F2-137's statement-anchored import regex, and as F2-165's regex literal: the matcher was
   * looking at the wrong span of text, which does not under-report, it invents a pass.
   */
  function guardsItsActions(src: string): boolean {
    if (/disabled=\{[^}]*isUnsyncedId/.test(src)) return true;
    // The bespoke shape: hoist once per row, then reference it (PrivacyPage's DPIA tab).
    return /\bconst unsynced = isUnsyncedId\(/.test(src) && /disabled=\{unsynced\}/.test(src);
  }

  it('no merging page renders a row action that ignores the placeholder id', () => {
    const offenders: string[] = [];
    for (const { f, src } of mergers) {
      const rel = f.slice(SRC.length + 1).replace(/\\/g, '/');
      // A page with no row actions at all has nothing to guard (CapaPage: string ids, no actions).
      if (!/<RowActions/.test(src) && !/\bdisabled=\{unsynced\}/.test(src)) continue;
      if (!guardsItsActions(src)) {
        offenders.push(`${rel} merges promoted rows and renders actions, but no disabled= expression consults isUnsyncedId`);
      }
    }
    expect(offenders, 'a promoted row is a placeholder for a record the server has never seen. '
      + 'Rendering Approve / Delete / Advance on it invites an action that cannot happen — and '
      + 'under the pre-F2-171 id scheme, occasionally happened to a different real record. Pass '
      + '`isUnsyncedId(row.id)` into the action\'s disabled expression.\n  '
      + offenders.join('\n  ')).toEqual([]);
  });

  /**
   * Scope, stated rather than implied, and *measured* rather than guessed: this resolves per
   * FILE, so a page with several action sites passes on the strength of any one of them.
   * Mutation confirms the blind spot — replacing `PrivacyPage`'s `const unsynced =
   * isUnsyncedId(d.id)` with `false` leaves this green, because the same file's DSR and breach
   * tables still carry inline guards. That is F2-165's granularity lesson yet again: an
   * instrument's resolution is its blind spot, and it is invisible from inside.
   *
   * Accepted rather than fixed, for a stated reason: the enforced safety property is the
   * api-layer refusal above, which is total and per-call. This scan only keeps the UX from
   * silently rotting, and the matcher's own two shapes are pinned by the test below it.
   */
  it('is honest about being a per-file check, so the api guard stays the control', () => {
    const both = mergers.filter(({ src }) => guardsItsActions(src) && /<RowActions/.test(src));
    expect(both.length, 'the guarded population collapsed').toBeGreaterThanOrEqual(7);
  });

  it('the matcher rejects the shape that used to pass — an import with no guarded expression', () => {
    // Mutation-proofing the helper itself, the same way F2-137 pins its import regex.
    expect(guardsItsActions("import { isUnsyncedId } from '../../lib/promotedRecords';\n"
      + '<RowActions disabled={isDemo} />')).toBe(false);
    expect(guardsItsActions('<RowActions disabled={isDemo || isUnsyncedId(r.id)} />')).toBe(true);
    expect(guardsItsActions('const unsynced = isUnsyncedId(d.id);\n<Button disabled={unsynced} />')).toBe(true);
  });
});
