package com.tss.grc.module.integrations.llm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        log.info("[LLM] gateway enabled={} mode={} primaryProvider={} fallbackProvider={} tertiaryProvider={} primaryModel={}",
            props.isEnabled(), props.getMode(), props.getProvider(), props.getFallbackProvider(), props.getTertiaryProvider(),
            props.resolveModel(props.getProvider()));
        if (props.isEnabled()
                && props.getMode() == LlmProperties.Mode.LIVE
                && !props.hasRequiredCredentials()) {
            log.warn("[LLM] enabled=true mode=LIVE but no valid API key is configured in fallback chain. "
                + "Inference calls will fail until OPENAI_API_KEY / GEMINI_API_KEY / GLM_API_KEY is configured.");
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
