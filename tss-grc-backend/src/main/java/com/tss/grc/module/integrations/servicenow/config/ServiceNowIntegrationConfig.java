package com.tss.grc.module.integrations.servicenow.config;

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
 * Wires the {@link ServiceNowProperties} bean and provides a tuned
 * {@link RestClient} for the LIVE client to share.
 *
 * <p>The {@code @PostConstruct} startup log mirrors the dev convention
 * of "what mode am I in?" surfacing for operators. If the operator
 * flipped {@code enabled=true} but forgot to set credentials, we warn
 * loudly so the misconfiguration is visible in the boot log.
 */
@Configuration
@EnableConfigurationProperties(ServiceNowProperties.class)
public class ServiceNowIntegrationConfig {

    private static final Logger log = LoggerFactory.getLogger(ServiceNowIntegrationConfig.class);

    private final ServiceNowProperties props;

    public ServiceNowIntegrationConfig(ServiceNowProperties props) {
        this.props = props;
    }

    @PostConstruct
    void announce() {
        log.info("[ServiceNow] integration enabled={} mode={} baseUrl={}",
            props.isEnabled(), props.getMode(), props.getBaseUrl());

        if (props.isEnabled() && props.getMode() == ServiceNowProperties.Mode.LIVE) {
            boolean okAuth = switch (props.getAuth().getType()) {
                case OAUTH2_CLIENT_CREDENTIALS -> props.getAuth().hasOAuthCredentials();
                case BASIC -> props.getAuth().hasBasicCredentials();
            };
            if (!okAuth) {
                log.warn("[ServiceNow] enabled=true mode=LIVE but auth credentials are missing "
                    + "(authType={}). Outbound calls will fail until env vars are set.",
                    props.getAuth().getType());
            }
        }
    }

    /**
     * Shared RestClient for the LIVE ServiceNow client. Read timeout aligned to
     * {@code timeoutSeconds}; connect timeout fixed to 10s.
     */
    @Bean("serviceNowRestClient")
    public RestClient serviceNowRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        return RestClient.builder()
            .requestFactory(factory)
            .build();
    }
}
