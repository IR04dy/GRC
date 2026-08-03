/**
 * S5c — the change lifecycle as the frontend understands it.
 *
 * <p>This file is deliberately **pure**: no React, no API client, no i18n. That is what
 * makes it testable, and `changeLifecycle.test.ts` pins `nextStatus` against the backend's
 * own `ChangeManagementService.NORMAL_TRANSITIONS` — the frontend holds a *second copy* of
 * the state machine, and until S5c nothing checked the two agreed. A forward move the
 * server rejects renders as a button that always fails.
 */
import { canApprove } from '../../lib/auth';
import type { ChangeRequestDto } from '../../lib/api';

/**
 * F2-29 — the transition targets the server treats as CAB *decisions*, requiring
 * `CHANGE:APPROVE` on top of `CHANGE:WRITE`. Mirrors
 * `ChangeManagementService.DECISION_TARGETS`; if that set changes, this one must follow —
 * the backend's `ChangeDecisionAuthorityTest` ratchet forces the question to be asked there,
 * and this comment is the pointer back.
 *
 * `CAB_REJECTED` is in the set for completeness even though this page never offers it: the
 * UI has no reject control at all today, only a `CAB_REJECTED` status label and column. The
 * API accepts it, so a future reject button inherits the gate rather than rediscovering it.
 */
export const DECISION_TARGETS = new Set([
  'CAB_APPROVED', 'CAB_REJECTED', 'EMERGENCY_APPROVED',
  // S7c / X-2 — CAB_DEFERRED and MORE_INFO_REQUIRED are CAB decisions too: recording what a
  // governance body concluded needs CHANGE:APPROVE, not merely CHANGE:WRITE. Mirrored from the
  // backend set, where `ChangeDecisionAuthorityTest` forced the classification to be stated and
  // `ChangeAuthorityFailClosedTest` recorded them as *deliberately open* under A17-D2 — neither
  // authorises anything, so an unconfigured gate must not refuse to record them.
  //
  // Like CAB_REJECTED, this page offers no control for either yet; the decision panel is CE-13.
  // They are in the set so that panel inherits the gate instead of rediscovering it.
  'CAB_DEFERRED', 'MORE_INFO_REQUIRED',
]);

/**
 * Can the signed-in user actually perform this transition? Used to *disable* rather than
 * hide the advance control: `nextStatus` returns the single forward move for a change, so
 * hiding it would leave a card that looks stuck with nothing saying why. Disabled + a title
 * naming the missing permission is the honest version.
 */
export function mayTransitionTo(target: string | null): boolean {
  if (!target) return false;
  return !DECISION_TARGETS.has(target) || canApprove('CHANGE');
}

export function nextStatus(c: ChangeRequestDto): string | null {
  switch (c.status) {
    case 'DRAFT':            return c.changeType === 'EMERGENCY' ? 'EMERGENCY_APPROVED' : 'SUBMITTED';
    case 'SUBMITTED':        return 'IMPACT_ASSESSED';
    case 'IMPACT_ASSESSED':  return 'CAB_PENDING';
    case 'CAB_PENDING':      return 'CAB_APPROVED';
    case 'CAB_APPROVED':     return 'SCHEDULED';
    case 'SCHEDULED':        return 'IMPLEMENTING';
    case 'EMERGENCY_APPROVED': return 'IMPLEMENTING';
    case 'IMPLEMENTING':     return 'IMPLEMENTED';
    case 'IMPLEMENTED':      return 'POST_REVIEW';
    case 'POST_REVIEW':      return 'CLOSED';
    case 'ROLLED_BACK':      return 'POST_REVIEW';
    default:                  return null;
  }
}
