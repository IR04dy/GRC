// Activity log — Phase B of the infrastructure round.
//
// Captures every state transition + assignment + create/close event across
// all 5 lifecycle workspaces. Inspired by ServiceNow Activity Log + Linear's
// activity feed. Shown via <ActivityFeedDrawer /> per entity.

import { currentUser, getUser } from './userDirectory';

export type ActivityAction =
  | 'CREATED' | 'TRANSITIONED' | 'ASSIGNED' | 'COMMENTED'
  | 'CLOSED' | 'REOPENED' | 'NOTIFIED' | 'EVIDENCE_ADDED';

export interface ActivityEvent {
  id: string;
  /** Entity type (e.g. "treatment-plan", "finding", "dsr"). */
  entityType: string;
  /** Entity id (e.g. "TP-2026-001"). */
  entityId: string;
  action: ActivityAction;
  /** From-status / to-status for transitions. */
  fromStatus?: string;
  toStatus?: string;
  /** Assignee id when ASSIGNED. */
  assigneeId?: string;
  /** Free-text bilingual note. */
  noteEn?: string;
  noteAr?: string;
  /** User id who did this (current user at time of action). */
  userId: string;
  /** ISO timestamp. */
  ts: string;
}

const KEY = 'grc.activity.v1';
const EVENT = 'grc.activity.changed';

export function loadActivity(): ActivityEvent[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(KEY);
    return raw ? JSON.parse(raw) as ActivityEvent[] : [];
  } catch { return []; }
}

export function activityFor(entityType: string, entityId: string): ActivityEvent[] {
  return loadActivity()
    .filter(a => a.entityType === entityType && a.entityId === entityId)
    .sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime());
}

export function logActivity(event: Omit<ActivityEvent, 'id' | 'ts' | 'userId'>): ActivityEvent {
  const user = currentUser();
  const full: ActivityEvent = {
    ...event,
    id: `ACT-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
    ts: new Date().toISOString(),
    userId: user.id,
  };
  const all = [...loadActivity(), full];
  // Cap to last 1000 events.
  const trimmed = all.slice(-1000);
  try { window.localStorage.setItem(KEY, JSON.stringify(trimmed)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
  return full;
}

export function subscribeActivity(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  return () => window.removeEventListener(EVENT, handler);
}

/** Convenience helpers. */
export function logTransition(entityType: string, entityId: string, fromStatus: string, toStatus: string): void {
  logActivity({ entityType, entityId, action: 'TRANSITIONED', fromStatus, toStatus });
}

export function logCreated(entityType: string, entityId: string): void {
  logActivity({ entityType, entityId, action: 'CREATED' });
}

export function logAssigned(entityType: string, entityId: string, assigneeId: string): void {
  logActivity({ entityType, entityId, action: 'ASSIGNED', assigneeId });
}

/** Resolve an event's user. */
export function activityUser(event: ActivityEvent) {
  return getUser(event.userId);
}
