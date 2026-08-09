// Promoted-records overlay — DD-69.
//
// Bridges the intake substrate (DD-54..58) and the per-module registers.
// Until now, triaging "Promote" on an intake record just flipped the
// intake status to PROMOTED — the downstream Incident / DSR / Risk /
// Vendor register never got a new row. The triager would say "promoted"
// but the user couldn't actually see the new record anywhere.
//
// This file holds a single localStorage-backed overlay of "records that
// were created via intake promotion". Each register page reads its
// matching slice and merges with its native seed/API data. The overlay
// also stores the source intake id so the audit trail is preserved.
//
// industry-standard GRC tooling equivalent: every promoted intake creates a row in
// the target table with a `parent_intake` foreign key.
//
// ⚠ F2-171 — WE DO NOT MIRROR THAT, and the sentence that used to end here
// ("We mirror that with `sourceIntakeId`…") claimed we did. What is mirrored is
// the *shape*; the row is written to this browser's `localStorage` and to nothing
// else. No `privacy.dpia` / `risk.risks` / `incident.incidents` row is created, no
// other user or machine can see the promotion, and clearing site data destroys it.
// `POST /api/v1/intake` and `PUT /api/v1/intake/{id}/triage` exist and are never
// called by any product code — F2-137 measured that for the *public* portals and
// gated them; the authenticated triager path this file serves was outside its
// route-prefix scope. Building the server write is filed as F2-172.
//
// Read that as the standing caveat on everything below: these are placeholders
// for records that do not exist yet, so they must be *labelled* as such and must
// never be handed to a real endpoint.

import { useEffect, useState } from 'react';
import type { RegisterTarget } from './intake';

/**
 * Session sequence, so two promotions inside the same millisecond cannot share an id.
 * Resets on reload — harmless, because the millisecond has moved on by then.
 */
let promotedSeq = 0;

/**
 * Mint an id for a promoted record — **negative by construction**.
 *
 * ⚠ F2-171: this is the whole reason the function exists rather than a literal at the
 * call site. `intake.ts` used to compute `Date.now() % 1_000_000`, a **positive**
 * integer in 0…999,999, while `promotedAdapters.ts` documented the opposite
 * ("Promoted records use *negative* synthesized ids … so they never collide with
 * backend-assigned ids") and this interface documented a third thing ("highest
 * existing + 1"). Three files, three accounts, and the live value was the unsafe one.
 *
 * SQL Server IDENTITY columns start at 1 and count up, so a negative id cannot name a
 * real record in any tenant, in any table, ever. That is the property being bought —
 * not "a collision is unlikely". The old scheme's collision window was measured in dev
 * at 0.056% per promotion across the 11 registers (sum of max(id) ÷ 1,000,000) and it
 * grows linearly as the tenant's tables do, which is the wrong direction for a number
 * that decides whether an Approve button hits the record on screen or a different one.
 *
 * Magnitude stays inside `Number.MAX_SAFE_INTEGER` until well past 2255.
 */
export function mintPromotedId(): number {
  const id = -(Date.now() * 1000 + (promotedSeq++ % 1000));
  // Cheap and total: if the arithmetic ever leaves the safe range, a silently rounded
  // id would start colliding with itself rather than with the server.
  return Number.isSafeInteger(id) ? id : -Date.now();
}

/**
 * Is this the id of a record that exists only in this browser?
 *
 * Total for the numeric scheme: no server id is negative. **Not** total for the one
 * adapter that mints a string (`toCapaFromObservation` → `capa-<intakeId>`); string
 * ids are excluded deliberately, because real records elsewhere carry string external
 * ids (UUIDs on bow-ties, ML models) and a string rule would call those unsynced too.
 * The CAPA case needs no coverage here — its page renders no server actions.
 */
export function isUnsyncedId(id: number | string | null | undefined): boolean {
  return typeof id === 'number' && Number.isFinite(id) && id < 0;
}

export interface PromotedRecord {
  /** Synthesized id — always negative, minted by {@link mintPromotedId}. */
  id: number | string;
  /** Source intake record id. */
  sourceIntakeId: string;
  /** Source intake reference code (e.g. "SI-K7L9-Z4M2"). */
  sourceReferenceCode: string;
  /** When the promotion happened. */
  promotedAt: string;
  /** Snapshot of the intake payload — built per-kind by the promotion handler. */
  payload: Record<string, unknown>;
}

const STORAGE_KEY = 'grc.promotedRecords';

interface PromotedOverlay {
  /** Map: entityType → list of promoted records. */
  byEntity: Partial<Record<RegisterTarget, PromotedRecord[]>>;
}

function load(): PromotedOverlay {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { byEntity: {} };
    const parsed = JSON.parse(raw);
    return { byEntity: parsed.byEntity ?? {} };
  } catch { return { byEntity: {} }; }
}
function save(o: PromotedOverlay) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(o)); } catch { /* ignore */ }
}

/** Add a record to the overlay. Called by intake.triage() when the
    triager picks Promote and the kind has a promotion handler. */
export function addPromoted(entityType: RegisterTarget, record: PromotedRecord): void {
  const overlay = load();
  const list = overlay.byEntity[entityType] ?? [];
  overlay.byEntity[entityType] = [...list, record];
  save(overlay);
  // Notify any mounted listeners — useState in usePromoted picks this up
  // via the storage event when the overlay is updated from another tab,
  // and via direct setOverlay when updated from the same component tree.
  try {
    window.dispatchEvent(new CustomEvent('grc.promoted.changed', { detail: { entityType } }));
  } catch { /* ignore */ }
}

/** React hook: subscribe to the overlay slice for one entity type. */
export function usePromoted(entityType: RegisterTarget): PromotedRecord[] {
  const [items, setItems] = useState<PromotedRecord[]>(() => load().byEntity[entityType] ?? []);
  useEffect(() => {
    const refresh = () => setItems(load().byEntity[entityType] ?? []);
    window.addEventListener('grc.promoted.changed', refresh);
    window.addEventListener('storage', refresh);
    return () => {
      window.removeEventListener('grc.promoted.changed', refresh);
      window.removeEventListener('storage', refresh);
    };
  }, [entityType]);
  return items;
}

/** Resets the overlay (used by demo "reset" buttons + tests). */
export function resetPromoted(): void {
  save({ byEntity: {} });
  try { window.dispatchEvent(new CustomEvent('grc.promoted.changed')); } catch { /* ignore */ }
}
