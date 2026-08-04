import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { ENTITY_TYPE_OPTIONS, LINK_TYPE_OPTIONS } from '../pages/change/changeVocabulary';
import { en } from '../translations/en';
import { ar } from '../translations/ar';

/**
 * S7d / CE-7 — the change→CAPA link the **server** creates must be one the **register** can name.
 *
 * <p>`syncPirFollowUpCapa` writes a `ChangeLink` with `entityType = "CAPA"` and
 * `linkType = "FOLLOW_UP"`, and `change_links` carries no CHECK constraint on either column
 * (measured in both databases) — so nothing in the schema would ever object to the two sides
 * disagreeing. The integrations panel renders `l.entityType.replace(/_/g,' ')`, so a link the
 * dropdown does not list still *displays*; what breaks is subtler and worse: a user sees a `CAPA` /
 * `FOLLOW_UP` link on the record and cannot create one, which reads as a bug in the form rather than
 * as the deliberate asymmetry it would be.
 *
 * <p>Same reasoning as `changeLifecycle.test.ts`, one layer down: the frontend holds a second copy of
 * a backend vocabulary, and a comment saying "keep these in step" cannot fail.
 */

const JAVA = join(
  __dirname, '..', '..', '..', 'tss-grc-backend', 'src', 'main', 'java', 'com', 'tss', 'grc',
  'module', 'change_mgmt', 'service', 'ChangeManagementService.java');
const ADAPTER = join(
  __dirname, '..', '..', '..', 'tss-grc-backend', 'src', 'main', 'java', 'com', 'tss', 'grc',
  'module', 'change_adapters', 'CapaAdapter.java');

const java = readFileSync(JAVA, 'utf8');

/** The literals the PIR hook stamps on its ChangeLink. */
function hookLinkPair(): { entityType: string; linkType: string } {
  const body = java.slice(java.indexOf('private void syncPirFollowUpCapa'));
  expect(body, 'syncPirFollowUpCapa is gone from the service — this test is stale, not passing').not.toBe('');
  const entity = /link\.setEntityType\("(\w+)"\)/.exec(body);
  const link = /link\.setLinkType\("(\w+)"\)/.exec(body);
  expect(entity, 'could not read setEntityType out of the hook').not.toBeNull();
  expect(link, 'could not read setLinkType out of the hook').not.toBeNull();
  return { entityType: entity![1], linkType: link![1] };
}

describe('S7d/CE-7 — the PIR follow-up link vocabulary', () => {
  it('offers the entity type the server actually writes', () => {
    const { entityType } = hookLinkPair();
    expect(ENTITY_TYPE_OPTIONS.map(o => o.value)).toContain(entityType);
  });

  it('offers the link type the server actually writes', () => {
    const { linkType } = hookLinkPair();
    expect(LINK_TYPE_OPTIONS.map(o => o.value)).toContain(linkType);
  });

  it('keeps the dedup marker a pair — the hook must test both halves', () => {
    // A one-field check would swallow CH-13's INCIDENT/TRIGGERED_BY-shaped near misses and silently
    // skip raising the CAPA. The backend has a unit test for the behaviour; this asserts the shape
    // is still a conjunction, because loosening it here is invisible from the frontend.
    const body = java.slice(java.indexOf('private void syncPirFollowUpCapa'));
    const dedup = body.slice(0, body.indexOf('if (already) return;'));
    expect(dedup).toMatch(/"CAPA"\.equals\(l\.getEntityType\(\)\)\s*&&\s*"FOLLOW_UP"\.equals\(l\.getLinkType\(\)\)/);
  });

  it('raises the CAPA under the origin type the tracker already uses', () => {
    // capa.tasks holds eight origin types, every one lower snake case (control_test, audit_finding,
    // processor_assessment …). A screaming-case CHANGE_PIR would have been the only odd one out, and
    // origin_type has no CHECK to object — the vocabulary is enforced by nothing but agreement.
    const adapter = readFileSync(ADAPTER, 'utf8');
    const origin = /ORIGIN_TYPE\s*=\s*"([a-z_]+)"/.exec(adapter);
    expect(origin, 'CapaAdapter no longer declares ORIGIN_TYPE').not.toBeNull();
    expect(origin![1]).toBe('change_pir');
  });

  it('discloses in both languages that saving a follow-up creates a record elsewhere', () => {
    // F2-98 in reverse: there a badge announced something untrue, here a field would quietly open a
    // governance record the user never asked for. Both languages, because Arabic is primary.
    for (const [lang, dict] of [['en', en], ['ar', ar]] as const) {
      const hint = (dict as Record<string, string>).changePirFollowUpHint;
      expect(hint, `changePirFollowUpHint missing from ${lang}`).toBeTruthy();
      expect(hint.length, `${lang} hint is too short to say anything`).toBeGreaterThan(20);
    }
    expect((ar as Record<string, string>).changePirFollowUpHint)
      .toMatch(/[؀-ۿ]/);
    expect((ar as Record<string, string>).changePirFollowUpHint)
      .not.toBe((en as Record<string, string>).changePirFollowUpHint);
  });
});
