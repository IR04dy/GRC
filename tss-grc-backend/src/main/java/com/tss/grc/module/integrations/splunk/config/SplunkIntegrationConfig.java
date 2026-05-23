package com.tss.grc.module.integrations.splunk.config;

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
 * Wires the {@link SplunkProperties} bean and exposes a tuned
 * {@link RestClient} for the LIVE Splunk client.
 *
 * <p>Mirrors {@code ServiceNowIntegrationConfig} so operators see a
 * consistent boot-log shape for every integration adapter.
 *
 * <p>If the operator flipped {@code enabled=true} but forgot to set the
 * HEC token, we warn loudly so the misconfiguration is visible in the
 * startup log — outbound calls would otherwise fail with a 401 once
 * traffic begins flowing.
 */
@Configuration
@EnableConfigurationProperties(SplunkProperties.class)
public class SplunkIntegrationConfig {

    private static final Logger log = LoggerFactory.getLogger(SplunkIntegrationConfig.class);

    private final SplunkProperties props;

    public SplunkIntegrationConfig(SplunkProperties props) {
        this.props = props;
    }

    @PostConstruct
    void announce() {
        log.info("[Splunk] integration enabled={} mode={} hecUrl={} index={}",
            props.isEnabled(), props.getMode(), props.getHecUrl(), props.getDefaultIndex());

        if (props.isEnabled() && props.getMode() == SplunkProperties.Mode.LIVE && !props.hasHecToken()) {
            log.warn("[Splunk] enabled=true mode=LIVE but HEC token is missing. "
                + "Outbound calls will fail with 401 until SPLUNK_HEC_TOKEN env var is set.");
        }
    }

    /**
     * Shared RestClient for the LIVE Splunk client. Connect+read timeout
     * aligned to {@code timeoutMs}. SSL verification is controlled via
     * {@code verifySsl}; the default JDK trust store is used when {@code true}.
     */
    @Bean("splunkRestClient")
    public RestClient splunkRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(Math.max(1_000, props.getTimeoutMs()));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
            .requestFactory(factory)
            .build();
    }
}
