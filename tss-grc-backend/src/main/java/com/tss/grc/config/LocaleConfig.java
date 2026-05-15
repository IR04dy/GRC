package com.tss.grc.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.Localeindustry-standard tooling;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleindustry-standard tooling;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Locale configuration for Arabic/English bilingual support.
 *
 * <p>Supports language switching via 'lang' request parameter.
 * Default locale is English (en). Arabic (ar) is the secondary locale.
 * Locale preference is stored in the HTTP session.
 */
@Configuration
public class LocaleConfig {

    /**
     * Session-based locale resolver.
     * Stores user's locale preference in the HTTP session.
     * Default: English (en).
     */
    @Bean
    public Localeindustry-standard tooling localeindustry-standard tooling() {
        SessionLocaleindustry-standard tooling resolver = new SessionLocaleindustry-standard tooling();
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setLocaleAttributeName("CURRENT_LOCALE");
        return resolver;
    }

    /**
     * Locale change interceptor.
     * Triggered by: ?lang=ar or ?lang=en query parameter.
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    /**
     * Message source for i18n messages.
     * Loads from messages.properties and messages_ar.properties.
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages", "validation-messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }
}
