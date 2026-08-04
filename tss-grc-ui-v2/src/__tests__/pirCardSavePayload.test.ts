import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * F2-105 — **the PIR card could not save at all**, and that is why the retrospective looked unused.
 *
 * <p>`PUT /api/v1/changes/{id}` binds the `ChangeRequest` **entity** with `@Valid`, and F2-30 put
 * `@NotBlank` on `titleAr` ("Arabic names are backend-mandatory", asserted in CLAUDE.md and enforced
 * nowhere until then). The server's `apply()` is a partial merge — it skips null fields — but `@Valid`
 * runs *before* it, so a body carrying only the four PIR fields is rejected 400 with
 * `errors: {titleAr: "must not be blank"}` before `apply()` is ever reached.
 *
 * <pre>
 *   PUT {pirFollowUp: "…"}                          -> 400   titleAr must not be blank
 *   PUT {...change, pirFollowUp: "…"}               -> 200
 * </pre>
 *
 * <p>So "Follow-up actions" — and the whole PIR card — had been unsaveable since F2-30 landed. The
 * measurement that opened S7d read <b>zero</b> populated PIR fields across dev and prod and concluded
 * the field went nowhere; the sharper truth is that it was <b>unreachable</b>, and the live probe for
 * S7d/CE-7 is what surfaced it — three legs 400'd on their first run.
 *
 * <p>The register's edit form has always sent `{...c}` (`reqFromDto`), which is why *it* worked. This
 * test pins the card to the same shape, generically: it reads the real entity's bean-validation
 * constraints, so a future `@NotBlank` on another column cannot quietly break the card again.
 *
 * <p><b>Why not relax the server instead.</b> Dropping `@Valid` from the PUT, or moving `@NotBlank`
 * off `titleAr`, would reverse F2-30 — the constraint exists because bilingual records are a product
 * requirement, and it was measured safe (0 blank rows) before it landed. A partial-merge API with
 * required fields legitimately demands the caller resend them.
 */

const ENTITY = join(
  __dirname, '..', '..', '..', 'tss-grc-backend', 'src', 'main', 'java', 'com', 'tss', 'grc',
  'module', 'change_mgmt', 'entity', 'ChangeRequest.java');
const CARD = join(__dirname, '..', 'pages', 'change', 'ChangePirCard.tsx');

/** Every field on the entity that bean validation will refuse to leave absent. */
function mandatoryFields(): string[] {
  const java = readFileSync(ENTITY, 'utf8');
  const out: string[] = [];
  const re = /@(?:NotBlank|NotNull)\b[\s\S]{0,400}?private\s+\S+\s+(\w+)\s*;/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(java)) !== null) out.push(m[1]);
  return out;
}

describe('F2-105 — the PIR card must send a body @Valid will accept', () => {
  it('the entity really does carry a mandatory field (otherwise this test proves nothing)', () => {
    // Anti-vacuity. If F2-30's constraint is ever removed the partial body would start working and
    // this file would pass for the wrong reason — so state the premise and let it fail loudly.
    expect(mandatoryFields()).toContain('titleAr');
  });

  it('spreads the whole record into the update payload', () => {
    const card = readFileSync(CARD, 'utf8');
    const call = card.slice(card.indexOf('changeApi.update('));
    const body = call.slice(0, call.indexOf('});') + 1);
    expect(body, 'could not locate the changeApi.update body — this test is stale').toContain('pirFollowUp');
    expect(body)
      .toMatch(/\.\.\.change\s*,/);
  });

  it('...or, failing that, names every mandatory field explicitly', () => {
    // The spread is the preferred shape, but an explicit list is equally correct. Accept either —
    // what must never pass is a body that omits a field @Valid requires, because that is a 400 the
    // user reads as "Validation Failed" with nothing to act on.
    const card = readFileSync(CARD, 'utf8');
    const call = card.slice(card.indexOf('changeApi.update('));
    const body = call.slice(0, call.indexOf('});') + 1);
    const spreadsRecord = /\.\.\.change\s*,/.test(body);
    if (spreadsRecord) return;
    for (const f of mandatoryFields()) {
      expect(body, `${f} is @NotBlank/@NotNull on the entity and absent from the PUT body`).toContain(f);
    }
  });

  it('does not resend status — the state machine owns it (CM-C3)', () => {
    // `{...change}` carries status, and that is safe only because apply() deliberately never copies
    // it. If the card ever set status explicitly it would be attempting the PUT/create bypass CM-C3
    // closed, and a spread is not that.
    const card = readFileSync(CARD, 'utf8');
    const call = card.slice(card.indexOf('changeApi.update('));
    const body = call.slice(0, call.indexOf('});') + 1);
    expect(body).not.toMatch(/^\s*status\s*:/m);
  });
});
