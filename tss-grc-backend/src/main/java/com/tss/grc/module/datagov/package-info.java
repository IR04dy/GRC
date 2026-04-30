/**
 * Data Governance module.
 *
 * <p>Establishes data ownership, quality standards, and lifecycle policies
 * across the organisation. Aligned with Saudi NCA data classification requirements
 * and ISO 27001 Annex A.8 (Asset Management).</p>
 *
 * <h3>Key entities</h3>
 * <ul>
 *   <li>{@code DataAsset} — logical data asset register (databases, files, APIs, reports)</li>
 *   <li>{@code DataClassification} — classification scheme (Public / Internal / Confidential / Restricted)</li>
 *   <li>{@code DataStewardship} — steward and custodian assignments per data asset</li>
 *   <li>{@code DataQualityRule} — data quality rules with automated check scheduling</li>
 *   <li>{@code DataLineage} — upstream / downstream data lineage records</li>
 * </ul>
 *
 * <h3>Cross-module integration</h3>
 * <ul>
 *   <li>Privacy module ← data assets link to RoPA processing activities</li>
 *   <li>Risk module ← critical data assets generate default risk entries</li>
 *   <li>Compliance module ← data classification evidence for NCA ECC / ISO 27001</li>
 * </ul>
 */
package com.tss.grc.module.datagov;
