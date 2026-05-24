/**
 * Key Risk Indicator (KRI) engine — Sprint 13.
 *
 * <p>Saudi banks track 30–50 KRIs (failed-login-attempts-per-day,
 * % overdue CAPAs, cross-border-transfer volume, AML alert aging,
 * LCR…). Today these sit in Excel pivot tables refreshed manually
 * quarterly; the audit committee opens every meeting with a KRI
 * heatmap built by hand from those spreadsheets.
 *
 * <p>This package introduces a structured KRI register:
 * <ul>
 *   <li>{@link com.tss.grc.module.risk.kri.entity.KriDefinition} — one
 *       row per KRI with thresholds, frequency, owner, regulatory
 *       anchor.</li>
 *   <li>{@link com.tss.grc.module.risk.kri.entity.KriMeasurement} —
 *       append-only series of recorded values, each with a snapshot
 *       GREEN/AMBER/RED status.</li>
 *   <li>{@link com.tss.grc.module.risk.kri.entity.KriBreach} — one row
 *       per AMBER/RED breach window opened and closed by the engine.</li>
 * </ul>
 *
 * <p>Service layer:
 * <ul>
 *   <li>{@link com.tss.grc.module.risk.kri.service.KriDefinitionService}
 *       — CRUD on definitions with F7+F8 emission.</li>
 *   <li>{@link com.tss.grc.module.risk.kri.service.KriMeasurementService}
 *       — record one measurement, evaluate status, open/close breaches.</li>
 *   <li>{@link com.tss.grc.module.risk.kri.service.KriBreachService}
 *       — acknowledge, close, sustained-red → auto-CAPA Issue.</li>
 *   <li>{@link com.tss.grc.module.risk.kri.service.KriSchedulerService}
 *       — daily 06:00 tick that evaluates SQL/CALLBACK KRIs; gated by
 *       {@code grc.kri.scheduler-enabled} (off by default).</li>
 * </ul>
 *
 * <p>Coexistence: the platform already carries an older
 * {@code risk.kri_definitions} (plural) stub table from Sprint-2.
 * This module uses the new {@code risk.kri_definition} (singular)
 * tables introduced by V193 — the singular tables are richer
 * (categories, value_direction, source_query_type, audit columns) and
 * are intended to replace the plural stub once the older
 * {@code com.tss.grc.module.risk.entity.KriDefinition} consumer
 * surfaces are migrated.
 *
 * <p>Regulatory anchors: SAMA Compliance Framework §4.2 (documented
 * KRIs, monthly review minimum), NCA ECC-2 control 2-12 (continuous
 * monitoring of cybersecurity metrics), SAMA BCF §3.5 (recovery-time
 * metrics), PDPL Articles 4 + 29 (privacy posture metrics).
 */
package com.tss.grc.module.risk.kri;
