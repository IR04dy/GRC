/**
 * Risk-Control-Finding Pivot view — Sprint 11.
 *
 * <p>Cross-module aggregation slice that answers the auditor's
 * canonical first question: <em>"for any selected risk, what controls
 * are in place, are they effective, are there findings, and is
 * remediation on track?"</em> Today that requires opening 4-5 modules
 * — Risk → Controls → CCM → Findings → CAPA — and reconciling by
 * hand. This module surfaces it on a single screen.
 *
 * <p>Regulatory driver:
 * <ul>
 *   <li><b>NCA ECC-2 (Essential Cybersecurity Controls v2)</b> — audit
 *       workflows demand traceable evidence that each identified risk
 *       has linked controls and that those controls are tested.</li>
 *   <li><b>SAMA CSF 1.2.3 (Risk Treatment)</b> — traceable link from
 *       each risk → control → test → finding → remediation.</li>
 *   <li><b>IIA Standard 2210.A1</b> — engagement planning must
 *       consider the risk and the controls designed to mitigate it.
 *       The pivot is what an internal auditor would otherwise build
 *       in Excel during scoping.</li>
 * </ul>
 *
 * <p>This is a <strong>pure aggregation</strong> module — it owns no
 * tables, ships no migration, and never mutates state. Three lenses
 * over the same response shape (the focal entity discriminator
 * picks which lens):
 * <ul>
 *   <li><b>Risk lens</b> — risk at the centre, surrounded by the set
 *       of linked controls, the audit findings raised against those
 *       controls (or against the risk directly), the open CAPA items,
 *       and the most recent CCM test rollup for the linked controls.</li>
 *   <li><b>Control lens</b> — control at the centre, surrounded by
 *       the risks it mitigates, findings that target it, and the CAPA
 *       items remediating those findings.</li>
 *   <li><b>Finding lens</b> — finding at the centre, surrounded by
 *       the linked control (and through it the risks it mitigates) +
 *       the CAPA items hanging off the finding.</li>
 * </ul>
 *
 * <p>F7+F8 contract on every read (analytics signal — tells the
 * platform which risks / controls / findings are reviewed most):
 * <ul>
 *   <li>F8 event emitted: {@code PIVOT_VIEW_GENERATED}</li>
 *   <li>F7 audit_log entry with the same action type</li>
 * </ul>
 *
 * <p>Multi-tenancy: every aggregation query passes
 * {@code organisationId} from {@link
 * com.tss.grc.common.tenant.TenantContext} to the source-module
 * repositories. A cross-org pivot request returns 404 because the
 * focal entity is invisible under the current tenant context.
 *
 * <p>Graceful missing-link handling: a risk that has zero linked
 * controls is a legitimate state ("uncontrolled risk"). The response
 * sections collapse to empty arrays in that case rather than throw.
 * Same for missing CCM test definitions, missing findings, missing
 * CAPA. The data-completeness percentage in {@code PivotSummary}
 * surfaces the gap to the operator.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Reuses existing module repositories — does not re-query their
 *       tables directly.</li>
 *   <li>Single DTO shape for all three lenses, with a
 *       {@code focalEntity} discriminator. Frontend handles layout.</li>
 *   <li>The Risk ↔ Control bridge is read from
 *       {@code platform.risk_control_links} (the unified table) — the
 *       legacy {@code risk.risk_controls} bridge is not consulted
 *       because the Sprint 11 unified library is now the source of
 *       truth.</li>
 *   <li>Findings are linked to risks via the {@code control_crosswalk}
 *       JSON column on {@code audit_lc.findings}, which carries the
 *       set of control codes a finding implicates; we match those
 *       control codes to the risk's linked controls.</li>
 *   <li>CAPA items hang off findings (via
 *       {@code audit_lc.capa_tasks.finding_id}); the pivot resolves
 *       the chain risk → findings → CAPA.</li>
 * </ul>
 */
package com.tss.grc.module.governance.pivot;
