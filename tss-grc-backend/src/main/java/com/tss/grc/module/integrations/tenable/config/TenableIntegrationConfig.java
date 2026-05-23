package com.tss.grc.module.integrations.tenable.config;

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
 * Wires {@link TenableProperties} and exposes a tuned {@link RestClient}
 * for the LIVE Tenable client.
 *
 * <p>Mirrors {@code CrowdStrikeIntegrationConfig} so operators see a
 * consistent boot-log shape across every integration adapter.
 *
 * <p>Scheduling for {@code TenableInboundService.scheduledPoll()} is
 * provided by the application-wide {@code @EnableScheduling} on
 * {@code GrcApplication} — no per-integration opt-in needed.
 */
@Configuration
@EnableConfigurationProperties(TenableProperties.class)
public class TenableIntegrationConfig {

    private static final Logger log = LoggerFactory.getLogger(TenableIntegrationConfig.class);

    private final TenableProperties props;

    public TenableIntegrationConfig(TenableProperties props) {
        this.props = props;
    }

    @PostConstruct
    void announce() {
        log.info("[Tenable] integration enabled={} mode={} platform={} baseUrl={} polling={} interval={}s",
            props.isEnabled(), props.getMode(), props.getPlatform(), props.getBaseUrl(),
            props.isPollingEnabled(), props.getPollingIntervalSeconds());

        if (props.isEnabled() && props.getMode() == TenableProperties.Mode.LIVE
                && !props.hasApiKeyCredentials()) {
            log.warn("[Tenable] enabled=true mode=LIVE but API key pair is missing. "
                + "Outbound calls will fail with 401 until TENABLE_ACCESS_KEY + "
                + "TENABLE_SECRET_KEY env vars are set.");
        }
    }

    /**
     * Shared RestClient for the LIVE client. Connect+read timeout aligned
     * to {@code timeoutMs}. Default JDK trust store handles TLS to
     * {@code cloud.tenable.com}; on-prem Tenable.sc appliances using a
     * private CA need that CA installed in the deployment's JVM truststore.
     */
    @Bean("tenableRestClient")
    public RestClient tenableRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(Math.max(1_000, props.getTimeoutMs()));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
            .requestFactory(factory)
            .build();
    }
}
