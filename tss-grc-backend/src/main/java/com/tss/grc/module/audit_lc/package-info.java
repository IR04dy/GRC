/**
 * Audit Lifecycle module — backend wiring.
 *
 * Backs {@code lib/auditLifecycle.ts}. Schema: {@code audit_lc.*}.
 *
 * Primary state-machine entity: {@link com.tss.grc.module.audit_lc.entity.Finding}
 * (OPEN → MGMT_RESPONSE → IN_REMEDIATION → AWAITING_VERIFICATION → CLOSED).
 *
 * Supporting tables (in V16 migration; Spring Boot classes can be added
 * mechanically as needed): annual_plans, working_papers, capa_tasks.
 */
package com.tss.grc.module.audit_lc;
