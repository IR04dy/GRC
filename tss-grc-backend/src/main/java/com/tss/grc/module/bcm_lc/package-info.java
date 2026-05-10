/**
 * BCM Lifecycle module — backend wiring.
 *
 * Backs {@code lib/bcmLifecycle.ts}. Schema: {@code bcm_lc.*}.
 *
 * Primary state-machine entity: {@link com.tss.grc.module.bcm_lc.entity.Invocation}
 * (STANDBY → DECLARED → INVOKED → STABILISED → STAND_DOWN → POST_INCIDENT_REVIEW → CLOSED).
 *
 * Aligned with NCEMA (National Center for Emergency Management),
 * ISO 22301 BCMS, and SAMA Cyber Resilience for financial-sector members.
 *
 * Tables modeled in V18 migration:
 *   bia_records          — Business Impact Analysis (RTO/RPO/MTPD)
 *   recovery_strategies  — strategies linked to BIAs
 *   drills               — tabletop / functional / full simulation exercises
 *   invocations          — real disruption response (state machine)
 */
package com.tss.grc.module.bcm_lc;
