/**
 * Audit Committee Dashboard — Sprint 11.
 *
 * <p>Curation slice that turns the platform's existing GRC data into
 * the four canonical Audit Committee questions:
 * <ol>
 *   <li>Are we managing risk?</li>
 *   <li>Are our controls working?</li>
 *   <li>Are we compliant?</li>
 *   <li>Are findings being closed?</li>
 * </ol>
 *
 * <p>Regulatory driver:
 * <ul>
 *   <li><b>SAMA Corporate Governance Regulations §6</b> — Audit
 *       Committee responsibilities require regular reporting on risk,
 *       internal control, compliance and findings. Committee Chair
 *       customarily an independent non-executive director.</li>
 *   <li><b>CMA Listed-Companies Corporate Governance Code
 *       Article 54</b> — the Audit Committee must "review reports
 *       and disclosures relating to internal control, risk management,
 *       compliance" and document the meeting outcome.</li>
 *   <li><b>Saudi Companies Law Art.103</b> — Audit Committee
 *       mandate.</li>
 * </ul>
 *
 * <p>Two entities:
 * <ul>
 *   <li>{@link com.tss.grc.module.audit_committee.dashboard.entity.DashboardSnapshot}
 *       — a point-in-time freeze of the live dashboard JSON tied to a
 *       meeting date. Becomes immutable once
 *       {@code is_finalized = true}.</li>
 *   <li>{@link com.tss.grc.module.audit_committee.dashboard.entity.DashboardAttestation}
 *       — per-member signed attestation against a snapshot. Carries a
 *       SHA-256 {@code signature_hash} of
 *       {@code attestation_text || user_id || ISO-8601(timestamp)}
 *       as tamper evidence.</li>
 * </ul>
 *
 * <p>Ten sections in the live dashboard, each backed by a different
 * existing module:
 * <table>
 *   <caption>Dashboard sections and their source modules</caption>
 *   <tr><th>Section</th><th>Source module</th></tr>
 *   <tr><td>Risk posture</td><td>{@code module.risk}</td></tr>
 *   <tr><td>Control posture</td><td>{@code module.compliance} + {@code module.ccm}</td></tr>
 *   <tr><td>Audit findings</td><td>{@code module.compliance}</td></tr>
 *   <tr><td>CAPA progress</td><td>{@code module.capa}</td></tr>
 *   <tr><td>AI models</td><td>{@code module.ai.governance}</td></tr>
 *   <tr><td>Climate</td><td>{@code module.sust.climate} + {@code module.sust.ghg}</td></tr>
 *   <tr><td>Obligations</td><td>{@code module.compliance.obligation}</td></tr>
 *   <tr><td>Incidents</td><td>{@code module.incident}</td></tr>
 *   <tr><td>Vendor BCP</td><td>{@code module.tprm.bcp_attestation}</td></tr>
 *   <tr><td>Three Lines</td><td>{@code module.governance.threelines}</td></tr>
 * </table>
 *
 * <p>F7+F8 contract on every mutation:
 * <ul>
 *   <li>F8 events: {@code AC_DASHBOARD_SNAPSHOT_GENERATED},
 *       {@code AC_DASHBOARD_ATTESTED},
 *       {@code AC_RESOLUTION_RECORDED},
 *       {@code AC_DASHBOARD_FINALIZED}.</li>
 *   <li>F7 audit_log entries with the same action types.</li>
 * </ul>
 *
 * <p>Multi-tenancy: every repository finder takes
 * {@code organisationId} explicitly via {@link
 * com.tss.grc.common.tenant.TenantContext}. Soft-deleted rows are
 * hidden by the entity {@code @Where(is_deleted = 0)} clauses.
 *
 * <p>Live vs snapshot trade-off: live calls reconcile to current
 * source data — useful while the Committee is in session. Snapshot
 * calls reconcile to the meeting-time freeze — useful for minutes
 * and after-the-fact audit.
 *
 * <p>Graceful missing-data handling: every section is computed in
 * its own try/catch so a missing register (e.g. AI governance in a
 * tenant that hasn't onboarded any models) returns a zero-filled
 * section rather than crashing the entire dashboard.
 */
package com.tss.grc.module.audit_committee.dashboard;
