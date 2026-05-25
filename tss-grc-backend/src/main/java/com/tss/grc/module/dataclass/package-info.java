/**
 * Data Classification + NDMO module — Sprint 32.
 *
 * <p>Operationalises the NDMO Data Classification Policy alongside
 * PDPL Article 4 (special-category) + Article 6 (lawful basis) for
 * the Saudi bank's data estate.
 *
 * <h2>Regulatory anchors</h2>
 * <ul>
 *   <li><b>NDMO Data Classification Policy</b> (Saudi NDMO 2020 v1.1) —
 *       4-level scheme: <code>PUBLIC</code> /
 *       <code>INTERNAL</code> / <code>CONFIDENTIAL</code> /
 *       <code>TOP_SECRET</code></li>
 *   <li><b>PDPL Article 4</b> sensitive-data categorisation (health,
 *       biometric, genetic, racial/ethnic, political, religious, etc.)</li>
 *   <li><b>PDPL Article 6</b> lawful-basis tagging at the dataset
 *       level</li>
 *   <li><b>PDPL Article 7</b> cross-border transfer gates linked to
 *       data assets</li>
 *   <li><b>SAMA Cloud Computing Regulatory Framework §4</b> tier
 *       alignment (NDMO PUBLIC ≈ T1, INTERNAL ≈ T2, CONFIDENTIAL ≈ T3,
 *       TOP_SECRET ≈ T4)</li>
 *   <li><b>NCA ECC-2 (2023)</b> §4-1 data classification +
 *       §4-2 secure handling</li>
 * </ul>
 *
 * <h2>State machines</h2>
 *
 * DataAsset (one row per logical dataset):
 * <pre>
 *   DRAFT → CLASSIFIED → APPROVED → IN_USE → REVIEW_DUE
 *                ↓                              ↓
 *             RECLASSIFY → CLASSIFIED       RECLASSIFY
 *                                              ↓
 *                                         DECOMMISSIONED
 * </pre>
 *
 * Flow (a cross-system data transfer):
 * <pre>
 *   PROPOSED → APPROVED → ACTIVE → SUSPENDED → TERMINATED
 *                ↓
 *             REJECTED
 * </pre>
 *
 * Exception (a controlled deviation from classification policy):
 * <pre>
 *   REQUESTED → APPROVED → ACTIVE → EXPIRED
 *                  ↓             ↓
 *               REJECTED      REVOKED
 * </pre>
 *
 * <h2>Cross-border gate (PDPL Article 7)</h2>
 *
 * <p>A {@code Flow} with destinationCountry not in
 * {SAUDI, in adequate-jurisdiction list} and sourceClassification ∈
 * {CONFIDENTIAL, TOP_SECRET} requires an APPROVED PDPL Art.7 transfer
 * authorisation reference on file; service-layer guard rejects {@code
 * APPROVED} promotion without it.
 *
 * <h2>F7 audit + F8 events</h2>
 *
 * <p>10 triggers: {@code DATA_ASSET_REGISTERED},
 * {@code DATA_ASSET_CLASSIFIED}, {@code DATA_ASSET_APPROVED},
 * {@code DATA_ASSET_RECLASSIFIED_UP}, {@code DATA_FLOW_PROPOSED},
 * {@code DATA_FLOW_APPROVED}, {@code DATA_FLOW_CROSS_BORDER_DETECTED},
 * {@code DATA_EXCEPTION_REQUESTED}, {@code DATA_EXCEPTION_APPROVED},
 * {@code DATA_REVIEW_DUE}.
 */
package com.tss.grc.module.dataclass;
