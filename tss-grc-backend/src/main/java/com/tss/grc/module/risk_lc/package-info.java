/**
 * Risk Lifecycle module — Phase F backend wiring template.
 *
 * Backs the {@code lib/riskLifecycle.ts} workspace. The other 4 lifecycle
 * modules (audit, privacy, BCM, cyber) follow the exact same shape:
 *
 *   entity/      — JPA @Entity classes mapped to risk_lc.* tables
 *   repository/  — Spring Data JPA repositories with org-scoped finders
 *   service/     — @Transactional business logic + state machine guards
 *   dto/         — Request/Response DTOs (separate from JPA entities)
 *   controller/  — @RestController exposing /api/risk-lc/** endpoints
 *
 * Multi-tenancy: every query filters by organisation_id from {@code TenantContext}
 * (set by {@code JwtAuthenticationFilter}). Soft delete via {@code is_deleted=0}
 * with {@code @Where} on entities.
 */
package com.tss.grc.module.risk_lc;
