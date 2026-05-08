// React hook — Sprint 4 §2.7 frontend consumer.
//
// Subscribes a component to live updates for a single record. Detail pages
// and lifecycle kanbans use this so they update without reload when someone
// else transitions the same entity.

import { useEffect } from 'react';
import { subscribe, currentOrgId, type RealtimeHandler } from './realtime';

/**
 * Subscribe to /topic/org.{orgId}.entity.{type}.{id} for the lifetime of the
 * component. Cleanup on unmount.
 *
 * @param entityType e.g. "vulnerability", "treatment_plan", "dsr_case"
 * @param externalId the record's external id (e.g. "VULN-2026-001")
 * @param handler callback invoked with the broadcast envelope
 *
 * Example:
 *   useRealtimeEntity('vulnerability', vulnId, (env) => {
 *     // env.data is the new VulnerabilityDto
 *     setVuln(env.data);
 *   });
 */
export function useRealtimeEntity(
  entityType: string,
  externalId: string | undefined,
  handler: RealtimeHandler,
): void {
  useEffect(() => {
    if (!externalId) return;
    const orgId = currentOrgId();
    if (!orgId) return;

    const topic = `/topic/org.${orgId}.entity.${entityType}.${externalId}`;
    const unsubscribe = subscribe(topic, handler);
    return unsubscribe;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entityType, externalId]);
}

/**
 * Subscribe to /topic/org.{orgId}.{kind} for org-wide broadcasts.
 * Use for SLA pulses, dashboard refreshes, notification bell increments.
 */
export function useRealtimeOrgEvent(kind: string, handler: RealtimeHandler): void {
  useEffect(() => {
    const orgId = currentOrgId();
    if (!orgId) return;
    const topic = `/topic/org.${orgId}.${kind}`;
    return subscribe(topic, handler);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kind]);
}
