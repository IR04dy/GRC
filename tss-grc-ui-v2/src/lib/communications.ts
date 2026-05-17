// Communication Record — first-class entity with its own lifecycle.
//
// Until DD-32, "comms" lived only as overlay entries piggy-backed on
// the static notification matrix (COMMS_LOG_BY_RECORD + mutations).
// That's fine for *catalogued* obligations but doesn't model:
//   - ad-hoc proactive comms (board update, voluntary regulator
//     engagement, customer advisory)
//   - the review / approval gates that real regulator submissions
//     pass through before they go out
//   - threaded follow-ups, amendments, retractions
//
// industry-standard GRC tooling equivalent: the `sn_communication` table — every
// comm has its own row with a documented lifecycle and audit trail.
// Standards anchor:
//   ISO 22301 §7.4.2 — retain documented evidence of what was
//                       communicated, to whom, when
//   ISO 27001 A.5.5 — contact with authorities through documented
//                      channels
//   NIST CSF RS.CO-3 — info shared consistent with response plans
//   COSO ERM Component 5 — quality of communication implies a
//                           reviewed channel, not send-and-forget
//   NCA ECC 1.1.4 — documented + governed cybersecurity comms channel
//   SAMA CSF 4.4.4 — documented change/incident comms process

import { useCallback, useEffect, useState } from 'react';
import type { CommChannel } from './stakeholders';

export type CommStatus =
  | 'DRAFT'           // author still composing
  | 'IN_REVIEW'       // submitted for peer / function-lead review
  | 'APPROVED'        // accountable owner has signed off
  | 'SENT'            // dispatched to the recipient
  | 'ACKNOWLEDGED'    // counterparty has confirmed receipt
  | 'CLOSED'          // fully closed-out (either after ack or by closure decision)
  | 'RETRACTED';      // pulled back (with reason)

export interface Communication {
  id: string;
  subject: string;
  bodyEn: string;
  bodyAr?: string;
  channel: CommChannel;
  /** Joins to STAKEHOLDERS.code. */
  recipientCode: string;
  /** Optional source record this comm relates to — "CHANGE_REQUEST:14",
      "CYBER_INCIDENT:501", "PERIODIC:NRC-2026-H1", etc. */
  sourceRecordKey?: string;
  /** Optional notification spec this comm fulfils — joins to
      NotificationSpec.id. Lets a comm "satisfy" a pending obligation. */
  sourceSpecId?: string;

  status: CommStatus;
  /** Free-form sender (defaults to current user / stakeholder code). */
  createdBy: string;
  createdAt: string;
  reviewedBy?: string;
  reviewedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  sentAt?: string;
  acknowledgedAt?: string;
  closedAt?: string;
  /** Regulator portal acknowledgement ID, signed-PDF reference, etc. */
  receiptRef?: string;

  /** For threading — replies, amendments, retraction notices. */
  parentCommId?: string;
  retractionReason?: string;

  /** Free-form audience tags — used by the broadcast comms (FSC,
      change freeze announcement, etc.). */
  audience?: Array<'ITSM_USERS' | 'BUSINESS_STAKEHOLDERS' | 'CUSTOMERS' | 'EXEC' | 'BOARD' | 'PUBLIC'>;
}

export const STATUS_TONE_COMM: Record<CommStatus, 'neutral' | 'info' | 'warning' | 'primary' | 'success' | 'danger'> = {
  DRAFT:        'neutral',
  IN_REVIEW:    'info',
  APPROVED:     'primary',
  SENT:         'warning',     // sent but not yet acknowledged → still "open"
  ACKNOWLEDGED: 'success',
  CLOSED:       'success',
  RETRACTED:    'danger',
};

export const STATUS_LABEL_COMM: Record<CommStatus, { en: string; ar: string }> = {
  DRAFT:        { en: 'Draft',        ar: 'مسودة' },
  IN_REVIEW:    { en: 'In review',    ar: 'قيد المراجعة' },
  APPROVED:     { en: 'Approved',     ar: 'معتمد' },
  SENT:         { en: 'Sent',         ar: 'أُرسل' },
  ACKNOWLEDGED: { en: 'Acknowledged', ar: 'تم التأكيد' },
  CLOSED:       { en: 'Closed',       ar: 'مغلق' },
  RETRACTED:    { en: 'Retracted',    ar: 'مسحوب' },
};

/** Allowed forward transitions per state. Drives the lifecycle UI. */
export const FORWARD_TRANSITIONS: Record<CommStatus, CommStatus[]> = {
  DRAFT:        ['IN_REVIEW', 'RETRACTED'],
  IN_REVIEW:    ['APPROVED', 'DRAFT', 'RETRACTED'],
  APPROVED:     ['SENT', 'DRAFT', 'RETRACTED'],
  SENT:         ['ACKNOWLEDGED', 'CLOSED', 'RETRACTED'],
  ACKNOWLEDGED: ['CLOSED'],
  CLOSED:       [],
  RETRACTED:    [],
};

const STORAGE_KEY = 'grc.communications';

function load(): Communication[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as Communication[];
    return Array.isArray(parsed) ? parsed : [];
  } catch { return []; }
}

function save(items: Communication[]) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(items)); }
  catch { /* storage unavailable — ignore */ }
}

/** React hook: localStorage-backed Communication store. */
export function useCommunications() {
  const [items, setItems] = useState<Communication[]>(() => load());
  useEffect(() => save(items), [items]);

  const create = useCallback((init: Omit<Communication, 'id' | 'createdAt' | 'status'> & { status?: CommStatus }) => {
    const id = `comm-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const next: Communication = {
      ...init,
      id,
      status: init.status ?? 'DRAFT',
      createdAt: new Date().toISOString(),
    };
    setItems(prev => [...prev, next]);
    return next;
  }, []);

  const update = useCallback((id: string, patch: Partial<Communication>) => {
    setItems(prev => prev.map(c => c.id === id ? { ...c, ...patch } : c));
  }, []);

  /** Advance a comm to the next status, stamping the relevant timestamp. */
  const advance = useCallback((id: string, to: CommStatus, by?: string, extra?: Partial<Communication>) => {
    setItems(prev => prev.map(c => {
      if (c.id !== id) return c;
      const stampField =
        to === 'IN_REVIEW'    ? { reviewedBy: by, reviewedAt: new Date().toISOString() } :
        to === 'APPROVED'     ? { approvedBy: by, approvedAt: new Date().toISOString() } :
        to === 'SENT'         ? { sentAt: new Date().toISOString() } :
        to === 'ACKNOWLEDGED' ? { acknowledgedAt: new Date().toISOString() } :
        to === 'CLOSED'       ? { closedAt: new Date().toISOString() } :
        {};
      return { ...c, ...stampField, ...extra, status: to };
    }));
  }, []);

  const remove = useCallback((id: string) => {
    setItems(prev => prev.filter(c => c.id !== id));
  }, []);

  const bySource = useCallback((sourceRecordKey: string): Communication[] => {
    return items.filter(c => c.sourceRecordKey === sourceRecordKey)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  }, [items]);

  const byStatus = useCallback((status: CommStatus): Communication[] => {
    return items.filter(c => c.status === status)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [items]);

  return { items, create, update, advance, remove, bySource, byStatus };
}
