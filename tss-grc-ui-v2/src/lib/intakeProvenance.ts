// Intake provenance — "this register row came from submission SI-K7L9-Z4M2".
//
// F2-172 replaced what this used to be. `promotedAdapters.usePromotedProvenance` read the
// `localStorage` overlay that intake promotion wrote to, and keyed a map by the synthesised
// id of a row that existed only in that browser. Promotion now creates a real record and
// the server records the link on the intake row itself (`promoted_to_entity_type` +
// `promoted_to_record_id`), so provenance is a fact we can read rather than one we cached.
//
// ⚠ Degrades to no badges, never to a wrong one. Reading it needs `INTAKE:READ`, which a
// register viewer may not hold (`so_officer` holds none of the INTAKE permissions), and the
// call can simply fail. A missing chip understates the audit trail; a fabricated one would
// assert an origin that is not there, and this whole finding is about the difference.

import { useEffect, useState } from 'react';
import { intakeApi } from './api';
import { isAuthenticated } from './auth';
import type { RegisterTarget } from './intake';

/**
 * Map from a promoted record's id to the reference code of the intake it came from.
 *
 * Keys are strings because the ids are: most registers key on a bigint but audit
 * engagements and CAPA tasks carry a UUID, and `promoted_to_record_id` is `NVARCHAR(40)`
 * for exactly that reason. Call sites pass `String(row.id)`.
 */
export function usePromotedProvenance(target: RegisterTarget): Map<string, string> {
  const [map, setMap] = useState<Map<string, string>>(() => new Map());

  useEffect(() => {
    if (!isAuthenticated()) return;
    let live = true;
    intakeApi.list()
      .then(page => {
        if (!live) return;
        const next = new Map<string, string>();
        for (const r of page.content) {
          if (r.promotedToEntityType === target && r.promotedToRecordId) {
            next.set(String(r.promotedToRecordId), r.referenceCode);
          }
        }
        setMap(next);
      })
      .catch(() => { /* no badges — see the file header for why this is not a fallback */ });
    return () => { live = false; };
  }, [target]);

  return map;
}
