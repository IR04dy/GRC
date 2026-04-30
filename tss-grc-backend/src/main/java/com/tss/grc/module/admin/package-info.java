/**
 * Administration &amp; Configuration module.
 *
 * <p>Platform-level and organisation-level administration: user and role management,
 * module activation per organisation, branding configuration, lookup table management,
 * licensing, system health monitoring, and audit log viewer.</p>
 *
 * <h3>Key capabilities</h3>
 * <ul>
 *   <li>User CRUD with role assignment and forced password reset</li>
 *   <li>Role and permission management (RBAC)</li>
 *   <li>Organisation module activation (feature flags per tenant)</li>
 *   <li>Branding: logo, primary colour, organisation name in Arabic / English</li>
 *   <li>Lookup table management (risk categories, asset types, etc.)</li>
 *   <li>License management and subscription tier controls</li>
 *   <li>System audit log viewer with export</li>
 * </ul>
 *
 * <h3>Access control</h3>
 * <p>All controllers in this package require {@code ROLE_PLATFORM_ADMIN} or
 * {@code ROLE_ORG_ADMIN} — enforced via Spring Security {@code @PreAuthorize}.</p>
 */
package com.tss.grc.module.admin;
