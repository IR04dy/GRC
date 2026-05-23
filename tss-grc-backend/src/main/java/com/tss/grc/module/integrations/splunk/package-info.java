/**
 * Splunk SIEM bridge adapter — Sprint 12, slice 2.
 *
 * <p>Mirror of the ServiceNow ITSM adapter (slice 1). Pushes GRC events
 * (security_event, findings, risks, CAPA) into Splunk via HEC and receives
 * Splunk saved-search alert webhooks back into {@code socops.security_event}.
 *
 * <p>Module layout:
 * <ul>
 *   <li>{@code config}     — {@link com.tss.grc.module.integrations.splunk.config.SplunkProperties}
 *                            and the boot-time wiring.</li>
 *   <li>{@code dto}        — {@code SplunkHecEventRequest}, {@code SplunkHecEventResponse},
 *                            {@code StubInvocation}.</li>
 *   <li>{@code client}     — {@link com.tss.grc.module.integrations.splunk.client.SplunkClient}
 *                            interface plus STUB and LIVE implementations selected by
 *                            {@code grc.integrations.splunk.mode}.</li>
 *   <li>{@code service}    — {@link com.tss.grc.module.integrations.splunk.service.SplunkOutboundService}
 *                            façade for pushing SecurityEvent rows + generic events.</li>
 *   <li>{@code controller} — admin + SOC endpoints under {@code /api/v1/integrations/splunk}.</li>
 *   <li>{@code webhook}    — inbound endpoint for Splunk → GRC alert action.</li>
 *   <li>{@code entity},
 *       {@code repository} — {@code SplunkHecOutboundLog} (V165). The generic
 *                            {@code IntegrationExternalLink} (V163) is reused
 *                            from the ServiceNow slice.</li>
 * </ul>
 *
 * <p>Read {@code tss-grc-backend/docs/integrations/SPLUNK.md} for operator notes.
 */
package com.tss.grc.module.integrations.splunk;
