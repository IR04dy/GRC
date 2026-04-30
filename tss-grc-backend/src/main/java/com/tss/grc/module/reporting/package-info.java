/**
 * Reporting &amp; Analytics module.
 *
 * <p>Provides executive dashboards, cross-module analytics, and export capabilities
 * (Excel via Apache POI 5.x, PDF via OpenPDF with Arabic RTL support).</p>
 *
 * <h3>Key entities</h3>
 * <ul>
 *   <li>{@code ReportDefinition} — saved report templates with parameters</li>
 *   <li>{@code ReportExecution} — async execution tracking (QUEUED → RUNNING → COMPLETED/FAILED)</li>
 *   <li>{@code ReportSchedule} — scheduled report delivery via SMTP</li>
 * </ul>
 *
 * <h3>Caching</h3>
 * <p>Caffeine cache with 5-minute TTL for dashboard aggregations, keyed by
 * {@code organisationId + cacheKey}.</p>
 *
 * <h3>Cross-module integration</h3>
 * <ul>
 *   <li>Reads from {@code reporting.vw_unified_risk_summary} SQL view</li>
 *   <li>Reads from {@code reporting.vw_compliance_status} SQL view</li>
 *   <li>Reads from {@code reporting.vw_incident_summary} SQL view</li>
 * </ul>
 */
package com.tss.grc.module.reporting;
