import { describe, expect, it } from 'vitest';
import {
  CONNECTOR_REGISTRY, CADENCE_LABEL, CONNECTOR_TRIGGER_LABEL, isAutomated,
  type ConnectorDefinition,
} from '../lib/connectors';

/**
 * F2-193 — the connector catalogue may not claim a trigger the backend does not provide.
 *
 * <p>What it used to say: twelve entries, eight of them `status: 'ACTIVE'`, each with a `cadence`,
 * a `lastRunAt` timestamp and an `intake30d` count — "records auto-ingested in the last 30 days".
 * Measured against the backend, **five of the eight had no implementation of any kind**: the three
 * regulator RSS feeds, the vendor SOC 2 expiry watcher, and the CAPA effectiveness re-test, whose
 * description named a `VERIFICATION_FAILED` status that appears in no Java source. A sixth was
 * push-only, not the 5-minute poll it advertised. The two with a real `@Scheduled` loop both had the
 * wrong cadence, in the flattering direction. The `intake30d` values summed to **292** in a KPI that
 * linked to `/intake-inbox` — a queue holding **zero rows** in dev and in prod.
 *
 * <p>🔑 The fabrication did not live in the data, it lived in the TYPE. A field called `lastRunAt`
 * on a catalogue that no server feeds can only ever be invented, and a field called `status` that a
 * human sets is a claim with no source. So the assertions below are mostly structural: they check
 * that the retired fields have not come back, that a claim of automation names the backend symbol
 * providing it, and that an unimplemented connector describes itself in the planned voice. The half
 * that a frontend test cannot do — *is that named symbol real?* — is `ConnectorBackingContractTest`
 * in the backend suite, which reads `connectors.ts` and resolves every `backing` against the Java
 * sources. Neither half is sufficient alone: this file would pass over a `backing` naming a class
 * that does not exist, and the backend file would pass over a re-added `intake30d`.
 *
 * <p>⚠ This is a **wording and shape ratchet**, which is the kind that regresses: nothing about an
 * invented timestamp breaks a build, and "Active" is an easy word to restore.
 */

/** `Class#method` — a scheduled loop is named by the method the container calls. */
const POLL_BACKING = /^[A-Z][A-Za-z0-9]*#[a-z][A-Za-z0-9]*$/;
/** `VERB /path` — a webhook is named by the route the vendor posts to. */
const ROUTE_BACKING = /^(GET|POST|PUT|PATCH|DELETE) \/api\/v1\/[A-Za-z0-9/_{}-]+$/;

/**
 * Fields the old shape carried. Asserted absent by NAME rather than by type, because the type is
 * what a future edit would widen: adding `lastRunAt?: string` back to the interface and setting it
 * on one entry is a two-line change that restores exactly the defect.
 */
const RETIRED_FIELDS = ['status', 'lastRunAt', 'intake30d', 'owner'] as const;

/** Openers that mark a capability as not yet built, in both languages. */
const PLANNED_VOICE = ['Planned:', 'مخطَّط:'];

function id(c: ConnectorDefinition) { return c.id; }

describe('F2-193 — connector catalogue trigger honesty', () => {
  it('has entries, so none of the assertions below pass vacuously', () => {
    expect(CONNECTOR_REGISTRY.length).toBeGreaterThan(8);
    expect(CONNECTOR_REGISTRY.filter(isAutomated).length).toBeGreaterThan(0);
    expect(CONNECTOR_REGISTRY.filter(c => c.trigger === 'NOT_IMPLEMENTED').length).toBeGreaterThan(0);
  });

  it('carries no field that only a fabrication could fill', () => {
    const offenders: string[] = [];
    for (const c of CONNECTOR_REGISTRY) {
      for (const f of RETIRED_FIELDS) {
        if (f in (c as unknown as Record<string, unknown>)) offenders.push(`${id(c)}.${f}`);
      }
    }
    expect(offenders,
      'F2-193: last-run stamps, 30-day counts, a hand-set status and a per-connector owner email '
      + 'are all state no server supplies for this catalogue. Live state belongs to the four real '
      + 'adapters on /api/v1/integrations, read by integrationsApi.ts.').toEqual([]);
  });

  it('names the backend symbol behind every claimed trigger, in the right notation', () => {
    for (const c of CONNECTOR_REGISTRY) {
      if (c.trigger === 'NOT_IMPLEMENTED') continue;
      expect(c.backing, `${id(c)} claims trigger ${c.trigger} and names no backend`).toBeTruthy();
      const b = c.backing as string;
      if (c.trigger === 'SCHEDULED_POLL') {
        expect(b, `${id(c)}: a scheduled poll is named Class#method — got "${b}"`)
          .toMatch(POLL_BACKING);
      } else {
        expect(b, `${id(c)}: a webhook / operator route is named "VERB /api/v1/..." — got "${b}"`)
          .toMatch(ROUTE_BACKING);
      }
    }
  });

  it('gates every automated connector on the property an operator must set', () => {
    // The two real pollers ship with polling-enabled:false and the KRI cycle with a property set in
    // no profile in the repo, so "there is a poller" and "the poller runs" are different facts. The
    // old catalogue asserted the second by omitting the distinction.
    for (const c of CONNECTOR_REGISTRY.filter(isAutomated)) {
      expect(c.gate, `${id(c)} is automated and names no configuration gate`).toBeTruthy();
      expect((c.gate as string).startsWith('grc.'),
        `${id(c)}: the gate must be the real property path — got "${c.gate}"`).toBe(true);
    }
  });

  it('carries a cadence exactly when there is a poll loop to have one', () => {
    for (const c of CONNECTOR_REGISTRY) {
      if (c.trigger === 'SCHEDULED_POLL') {
        expect(c.cadence, `${id(c)} polls and declares no interval`).toBeTruthy();
        expect(Object.keys(CADENCE_LABEL)).toContain(c.cadence as string);
      } else {
        expect(c.cadence,
          `${id(c)} is ${c.trigger} and declares a cadence — a webhook has no interval, and an `
          + 'unimplemented connector has nothing to run on one').toBeUndefined();
      }
    }
  });

  it('leaves an unimplemented connector with nothing that implies it runs', () => {
    for (const c of CONNECTOR_REGISTRY.filter(x => x.trigger === 'NOT_IMPLEMENTED')) {
      expect(c.backing, `${id(c)} is not implemented and names a backend`).toBeUndefined();
      expect(c.gate, `${id(c)} is not implemented and names a gate`).toBeUndefined();
    }
  });

  it('describes an unimplemented connector in the planned voice, in both languages', () => {
    // The badge is not enough. A reader reads the sentence, and "Daily import of CVE-tagged
    // findings; auto-creates a record per host/CVE pair" is a present-tense claim about a connector
    // that, for five of these, did not exist.
    for (const c of CONNECTOR_REGISTRY.filter(x => x.trigger === 'NOT_IMPLEMENTED')) {
      expect(PLANNED_VOICE.some(p => c.descriptionEn.startsWith(p)),
        `${id(c)}: descriptionEn must open in the planned voice — got "${c.descriptionEn.slice(0, 60)}…"`)
        .toBe(true);
      expect(PLANNED_VOICE.some(p => c.descriptionAr.startsWith(p)),
        `${id(c)}: descriptionAr must open in the planned voice — got "${c.descriptionAr.slice(0, 60)}…"`)
        .toBe(true);
    }
  });

  it('never opens an implemented connector in the planned voice', () => {
    // The mirror of the rule above, so "Planned:" cannot be used as a hedge on something that runs.
    for (const c of CONNECTOR_REGISTRY.filter(x => x.trigger !== 'NOT_IMPLEMENTED')) {
      for (const p of PLANNED_VOICE) {
        expect(c.descriptionEn.startsWith(p), `${id(c)} is ${c.trigger} and reads as planned`).toBe(false);
        expect(c.descriptionAr.startsWith(p), `${id(c)} is ${c.trigger} and reads as planned`).toBe(false);
      }
    }
  });

  it('is bilingual and uniquely keyed', () => {
    const ids = new Set<string>();
    for (const c of CONNECTOR_REGISTRY) {
      expect(ids.has(c.id), `duplicate connector id ${c.id}`).toBe(false);
      ids.add(c.id);
      for (const [field, value] of [['nameAr', c.nameAr], ['descriptionAr', c.descriptionAr],
        ['nameEn', c.nameEn], ['descriptionEn', c.descriptionEn], ['reference', c.reference]]) {
        expect(value, `${id(c)}.${field} is empty`).toBeTruthy();
      }
    }
  });

  it('labels every trigger and cadence member it declares', () => {
    for (const c of CONNECTOR_REGISTRY) {
      expect(CONNECTOR_TRIGGER_LABEL[c.trigger]?.en, `no English label for ${c.trigger}`).toBeTruthy();
      expect(CONNECTOR_TRIGGER_LABEL[c.trigger]?.ar, `no Arabic label for ${c.trigger}`).toBeTruthy();
    }
  });
});
