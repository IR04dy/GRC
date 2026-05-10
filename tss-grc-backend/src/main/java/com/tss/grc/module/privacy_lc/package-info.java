/**
 * Privacy Lifecycle module — backend wiring.
 *
 * Backs {@code lib/privacyLifecycle.ts}. Schema: {@code privacy_lc.*}.
 *
 * Primary state-machine entity: {@link com.tss.grc.module.privacy_lc.entity.DsrCase}
 * (INTAKE → VERIFYING_IDENTITY → COLLECTING → REDACTING → DELIVERED + EXTENDED/REJECTED).
 *
 * PDPL Articles modeled in V17 migration: 22 (ROPA), 25 (DPIA), 29 (Cross-border), 4 (DSR).
 */
package com.tss.grc.module.privacy_lc;
