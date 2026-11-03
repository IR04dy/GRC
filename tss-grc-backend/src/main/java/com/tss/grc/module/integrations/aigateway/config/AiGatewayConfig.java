package com.tss.grc.module.integrations.aigateway.config;

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
 * Spring configuration for the AI Gateway subsystem.
 */
@Configuration
@EnableConfigurationProperties(AiGatewayProperties.class)
public class AiGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayConfig.class);

    private final AiGatewayProperties props;

    public AiGatewayConfig(AiGatewayProperties props) {
        this.props = props;
    }

    @PostConstruct
    void logStartup() {
        log.info("[AI Gateway] enabled={} mode={} baseUrl={} model={}",
            props.isEnabled(), props.getMode(), props.getBaseUrl(), props.getModel());
    }

    @Bean("aiGatewayRestClient")
    public RestClient aiGatewayRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, props.getTimeoutSeconds())));
        return RestClient.builder()
            .requestFactory(factory)
            .build();
    }
}
