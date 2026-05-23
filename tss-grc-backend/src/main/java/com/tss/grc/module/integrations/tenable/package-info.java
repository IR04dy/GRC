/**
 * Tenable.io / Tenable.sc Vulnerability Management bridge adapter —
 * Sprint 12, slice 4.
 *
 * <p>Bidirectional adapter:
 * <ul>
 *   <li><b>Pull</b> — Tenable.io {@code /vulns/export} or Tenable.sc
 *       {@code /rest/analysis} discovers new vulnerability findings; each
 *       becomes a {@code cyber.vulnerabilities} row plus a
 *       {@code platform.integration_external_link} mapping
 *       (integration='TENABLE').</li>
 *   <li><b>Push</b> — Tenable workbench / accept-risk endpoints record
 *       GRC-side decisions: recast severity, accept risk, or remove an
 *       existing recast. Each push produces a bidirectional audit trail
 *       so analysts can reconstruct who decided what and when.</li>
 * </ul>
 *
 * <p>Module layout (mirrors the CrowdStrike and Splunk slices):
 * <ul>
 *   <li>{@code config}     — {@link com.tss.grc.module.integrations.tenable.config.TenableProperties}
 *                            and boot-time wiring.</li>
 *   <li>{@code dto}        — {@code TenableFindingDto}, {@code RecastRequest},
 *                            {@code AcceptRiskRequest}, {@code RecastResponse},
 *                            {@code StatusDto}, {@code StubInvocation}.</li>
 *   <li>{@code client}     — {@link com.tss.grc.module.integrations.tenable.client.TenableClient}
 *                            interface plus STUB and LIVE implementations selected by
 *                            {@code grc.integrations.tenable.mode}.</li>
 *   <li>{@code service}    — {@code TenableInboundService} (poll + create
 *                            vulnerability rows), {@code TenableOutboundService}
 *                            (recast / accept-risk / remove-recast).</li>
 *   <li>{@code controller} — admin + VM/risk endpoints under
 *                            {@code /api/v1/integrations/tenable}.</li>
 *   <li>{@code entity},
 *       {@code repository} — {@code TenableFindingLog} +
 *                            {@code TenableRecastLog} (V176). The generic
 *                            {@code IntegrationExternalLink} (V163) is reused
 *                            from the ServiceNow slice with integration='TENABLE'.</li>
 * </ul>
 *
 * <p>Auth — Tenable uses an API key pair carried in the
 * {@code X-ApiKeys: accessKey=...;secretKey=...} header on every request.
 * Unlike CrowdStrike's OAuth flow there is no token cache.
 *
 * <p>KSA-resident hosting — Tenable.io is hosted in US / EU regions; there
 * is no KSA datacenter as of this slice. Banks that must keep VM data
 * inside KSA borders run Tenable.sc on-prem; the {@code platform} property
 * selects between the IO and SC live-client code paths.
 *
 * <p>Read {@code tss-grc-backend/docs/integrations/TENABLE.md} for operator
 * notes covering Tenable API key creation, the IO-vs-SC choice, PDPL data-
 * residency considerations, and smoke-test curls.
 */
package com.tss.grc.module.integrations.tenable;
