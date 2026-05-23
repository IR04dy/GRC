package com.tss.grc.module.integrations.crowdstrike.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Wires {@link CrowdStrikeProperties} and exposes a tuned {@link RestClient}
 * for the OAuth token manager and the LIVE Falcon client.
 *
 * <p>Mirrors {@code SplunkIntegrationConfig} so operators see a consistent
 * boot-log shape across every integration adapter.
 *
 * <p>Scheduling for {@code CrowdStrikeInboundService.scheduledPoll()} is
 * provided by the application-wide {@code @EnableScheduling} on
 * {@code GrcApplication} — no per-integration opt-in needed.
 */
@Configuration
@EnableConfigurationProperties(CrowdStrikeProperties.class)
public class CrowdStrikeIntegrationConfig {

    private static final Logger log = LoggerFactory.getLogger(CrowdStrikeIntegrationConfig.class);

    private final CrowdStrikeProperties props;

    public CrowdStrikeIntegrationConfig(CrowdStrikeProperties props) {
        this.props = props;
    }

    @PostConstruct
    void announce() {
        log.info("[CrowdStrike] integration enabled={} mode={} baseUrl={} polling={} interval={}s",
            props.isEnabled(), props.getMode(), props.getBaseUrl(),
            props.isPollingEnabled(), props.getPollingIntervalSeconds());

        if (props.isEnabled() && props.getMode() == CrowdStrikeProperties.Mode.LIVE
                && !props.hasOAuthCredentials()) {
            log.warn("[CrowdStrike] enabled=true mode=LIVE but OAuth client credentials are missing. "
                + "Outbound calls will fail with 401 until CROWDSTRIKE_CLIENT_ID + "
                + "CROWDSTRIKE_CLIENT_SECRET env vars are set.");
        }
    }

    /**
     * Shared RestClient for the OAuth token manager and the LIVE client.
     * Connect+read timeout aligned to {@code timeoutMs}. Default JDK trust
     * store handles TLS to {@code api.*.crowdstrike.com}.
     */
    @Bean("crowdStrikeRestClient")
    public RestClient crowdStrikeRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(Math.max(1_000, props.getTimeoutMs()));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
            .requestFactory(factory)
            .build();
    }
}
