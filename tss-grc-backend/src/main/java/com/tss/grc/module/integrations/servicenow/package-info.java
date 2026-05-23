/**
 * ServiceNow ITSM integration adapter — Sprint 12, first slice.
 *
 * <p>Module layout:
 * <ul>
 *   <li>{@code config}     — {@link com.tss.grc.module.integrations.servicenow.config.ServiceNowProperties}
 *                            and the boot-time wiring.</li>
 *   <li>{@code dto}        — {@code ServiceNowIncidentRequest}, {@code ServiceNowIncidentResponse},
 *                            {@code StubInvocation}.</li>
 *   <li>{@code client}     — {@link com.tss.grc.module.integrations.servicenow.client.ServiceNowClient}
 *                            interface plus STUB and LIVE implementations selected by
 *                            {@code grc.integrations.servicenow.mode}.</li>
 *   <li>{@code oauth}      — token cache + refresh for OAuth client-credentials.</li>
 *   <li>{@code service}    — {@link com.tss.grc.module.integrations.servicenow.service.ServiceNowOutboundService}
 *                            façade for pushing cyber incidents, audit findings, CAPA tasks,
 *                            and compliance gaps.</li>
 *   <li>{@code controller} — admin endpoints under {@code /api/v1/integrations/servicenow}.</li>
 *   <li>{@code webhook}    — inbound endpoint for SNow → GRC state change notifications.</li>
 *   <li>{@code entity},
 *       {@code repository} — {@code IntegrationExternalLink} (table V163).</li>
 * </ul>
 *
 * <p>Read {@code tss-grc-backend/docs/integrations/SERVICENOW.md} for operator notes.
 */
package com.tss.grc.module.integrations.servicenow;
