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
// the target table with a `parent_intake` foreign key. We mirror that
// with `sourceIntakeId` and `sourceReferenceCode` on each promoted
// record.

import { useEffect, useState } from 'react';
import type { RegisterTarget } from './intake';

export interface PromotedRecord {
  /** Synthesized id (highest existing + 1 at promotion time). */
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
