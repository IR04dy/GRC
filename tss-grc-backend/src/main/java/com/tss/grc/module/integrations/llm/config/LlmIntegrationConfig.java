package com.tss.grc.module.integrations.llm.config;

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
 * Wires {@link LlmProperties} and a dedicated {@link RestClient} with read
 * timeouts tuned for local or hosted LLM generation.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmIntegrationConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmIntegrationConfig.class);

    private final LlmProperties props;

    public LlmIntegrationConfig(LlmProperties props) {
        this.props = props;
    }

    @PostConstruct
    void announce() {
        log.info("[LLM] gateway enabled={} mode={} provider={} model={}",
            props.isEnabled(), props.getMode(), props.getProvider(), props.getModel());
        if (props.isEnabled()
                && props.getMode() == LlmProperties.Mode.LIVE
                && !props.hasRequiredCredentials()) {
            log.warn("[LLM] enabled=true mode=LIVE provider={} but API key is unset. "
                + "Inference calls will fail until GEMINI_API_KEY / LLM_API_KEY is configured.",
                props.getProvider());
        }
    }

    @Bean("llmRestClient")
    public RestClient llmRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, props.getTimeoutSeconds())));
        return RestClient.builder()
            .requestFactory(factory)
            .build();
    }
}
