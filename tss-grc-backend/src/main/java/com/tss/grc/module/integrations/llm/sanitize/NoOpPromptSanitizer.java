package com.tss.grc.module.integrations.llm.sanitize;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default pass-through {@link PromptSanitizer} active until a PII-masking
 * bean is registered.
 */
@Component
@ConditionalOnMissingBean(value = PromptSanitizer.class, ignored = NoOpPromptSanitizer.class)
public class NoOpPromptSanitizer implements PromptSanitizer {

    @Override
    public String sanitize(String rawPrompt) {
        return rawPrompt == null ? "" : rawPrompt;
    }
}
