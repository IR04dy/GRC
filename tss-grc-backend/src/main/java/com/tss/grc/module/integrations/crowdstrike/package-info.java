/**
 * CrowdStrike Falcon EDR bridge adapter — Sprint 12, slice 3.
 *
 * <p>Bidirectional adapter:
 * <ul>
 *   <li><b>Pull</b> — {@code /detects} polling endpoint discovers new Falcon
 *       detections; each one becomes a {@code socops.security_event} row plus
 *       a {@code platform.integration_external_link} mapping
 *       (integration='CROWDSTRIKE').</li>
 *   <li><b>Push</b> — {@code /devices/entities/devices-actions/v2} runs host
 *       containment (isolate / lift) requested by SOC Tier 2 or higher.</li>
 * </ul>
 *
 * <p>Module layout (mirrors the Splunk and ServiceNow slices):
 * <ul>
 *   <li>{@code config}     — {@link com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties}
 *                            and boot-time wiring.</li>
 *   <li>{@code oauth}      — {@link com.tss.grc.module.integrations.crowdstrike.oauth.CrowdStrikeOAuthTokenManager}
 *                            caches Falcon OAuth2 access tokens (30-min TTL).</li>
 *   <li>{@code dto}        — {@code FalconDetectionDto}, {@code ContainmentRequest/Response},
 *                            {@code StatusDto}, {@code StubInvocation}.</li>
 *   <li>{@code client}     — {@link com.tss.grc.module.integrations.crowdstrike.client.CrowdStrikeClient}
 *                            interface plus STUB and LIVE implementations selected by
 *                            {@code grc.integrations.crowdstrike.mode}.</li>
 *   <li>{@code service}    — {@code CrowdStrikeInboundService} (poll + create
 *                            security_event), {@code CrowdStrikeOutboundService}
 *                            (contain / lift).</li>
 *   <li>{@code controller} — admin + SOC endpoints under
 *                            {@code /api/v1/integrations/crowdstrike}.</li>
 *   <li>{@code entity},
 *       {@code repository} — {@code CrowdStrikeDetectionLog} +
 *                            {@code CrowdStrikeActionLog} (V170). The generic
 *                            {@code IntegrationExternalLink} (V163) is reused
 *                            from the ServiceNow slice.</li>
 * </ul>
 *
 * <p>Read {@code tss-grc-backend/docs/integrations/CROWDSTRIKE.md} for operator
 * notes covering Falcon API key creation, the EU-1 vs US-1 cloud-region
 * decision, and smoke-test curls.
 */
package com.tss.grc.module.integrations.crowdstrike;
