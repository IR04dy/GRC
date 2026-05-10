import { useCallback, useEffect, useState } from 'react';
import type { CommLogEntry, CommState } from './notificationMatrix';

/**
 * Local mutation store for comm-log entries — overlays on the static
 * COMMS_LOG_BY_RECORD + scheduler output. ServiceNow IRM has a server-
 * persisted mutation log; in demo mode we mirror the same shape via
 * localStorage so an auditor's "mark sent" survives a page reload.
 */

export interface CommsMutation {
  recordKey: string;
  /** entry.id from COMMS_LOG_BY_RECORD or scheduler. */
  entryId: string;
  state: CommState;
  receiptRef?: string;
  noteEn?: string;
  /** ISO timestamp the user clicked "mark sent". */
  sentAt: string;
}

const STORAGE_KEY = 'grc.commsMutations';

function load(): CommsMutation[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as CommsMutation[];
    return Array.isArray(parsed) ? parsed : [];
  } catch { return []; }
}

function save(items: CommsMutation[]) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(items)); }
  catch { /* storage unavailable / quota — ignore */ }
}

/** React hook: mutation store + helpers. */
export function useCommsMutations() {
  const [mutations, setMutations] = useState<CommsMutation[]>(() => load());

  useEffect(() => save(mutations), [mutations]);

  const upsert = useCallback((m: CommsMutation) => {
    setMutations(prev => {
      const next = prev.filter(x => !(x.recordKey === m.recordKey && x.entryId === m.entryId));
      next.push(m);
      return next;
    });
  }, []);

  const lookup = useCallback((recordKey: string, entryId: string): CommsMutation | undefined => {
    return mutations.find(m => m.recordKey === recordKey && m.entryId === entryId);
  }, [mutations]);

  /** Apply mutations on top of an entry, returning a merged view. */
  const apply = useCallback((recordKey: string, entry: CommLogEntry): CommLogEntry => {
    const m = mutations.find(x => x.recordKey === recordKey && x.entryId === entry.id);
    if (!m) return entry;
    return { ...entry,
      state: m.state,
      receiptRef: m.receiptRef ?? entry.receiptRef,
      noteEn: m.noteEn ?? entry.noteEn,
      sentAt: m.sentAt };
  }, [mutations]);

  const clear = useCallback(() => setMutations([]), []);

  return { mutations, upsert, lookup, apply, clear };
}
