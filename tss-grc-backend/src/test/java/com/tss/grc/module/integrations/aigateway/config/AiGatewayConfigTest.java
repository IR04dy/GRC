package com.tss.grc.module.integrations.aigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks in that the gateway is off with no key unless configured, that a LIVE, enabled
 * gateway without a key refuses to start, and that {@code application.yml} actually binds
 * to the properties class (it once sat under a different prefix and was silently ignored).
 */
class AiGatewayConfigTest {

    private static AiGatewayProperties props(boolean enabled, AiGatewayProperties.Mode mode, String apiKey) {
        AiGatewayProperties p = new AiGatewayProperties();
        p.setEnabled(enabled);
        p.setMode(mode);
        p.setApiKey(apiKey);
        return p;
    }

    @Test
    void defaultsAreOffWithNoKey() {
        AiGatewayProperties p = new AiGatewayProperties();

        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getApiKey()).isNull();
        assertThat(p.hasRequiredCredentials()).isFalse();
    }

    @Test
    void liveAndEnabledWithoutKeyRefusesToStart() {
        AiGatewayConfig config = new AiGatewayConfig(props(true, AiGatewayProperties.Mode.LIVE, " "));

        assertThatThrownBy(config::checkStartup)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AI_GATEWAY_API_KEY");
    }

    @Test
    void startsWhenDisabledOrStubOrKeyed() {
        assertThatCode(() -> new AiGatewayConfig(
            props(false, AiGatewayProperties.Mode.LIVE, null)).checkStartup()).doesNotThrowAnyException();
        assertThatCode(() -> new AiGatewayConfig(
            props(true, AiGatewayProperties.Mode.STUB, null)).checkStartup()).doesNotThrowAnyException();
        assertThatCode(() -> new AiGatewayConfig(
            props(true, AiGatewayProperties.Mode.LIVE, "test-key")).checkStartup()).doesNotThrowAnyException();
    }

    @Test
    void applicationYmlBindsToThePropertiesPrefix() throws Exception {
        String prefix = AiGatewayProperties.class.getAnnotation(ConfigurationProperties.class).prefix();

        // Only application.yml, no OS environment, so the ${VAR:default} defaults are what bind.
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))) {
            env.getPropertySources().addLast(source);
        }

        BindResult<AiGatewayProperties> bound = Binder.get(env).bind(prefix, AiGatewayProperties.class);

        assertThat(bound.isBound()).as("application.yml has a block under %s", prefix).isTrue();
        assertThat(bound.get().isEnabled()).isFalse();
        assertThat(bound.get().getMode()).isEqualTo(AiGatewayProperties.Mode.LIVE);
        assertThat(bound.get().hasRequiredCredentials()).isFalse();
        assertThat(bound.get().getTenantDailyTokenQuota()).isEqualTo(500_000L);
    }
}
