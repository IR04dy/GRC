package com.tss.grc.module.integrations.llm.sanitize;

/**
 * Pluggable pre-flight prompt sanitization seam (e.g. PII / secret masking
 * before prompts leave the process when a cloud provider is active).
 *
 * <p>Kept open by design so a dedicated masking implementation can replace
 * {@link NoOpPromptSanitizer} without touching callers or provider adapters.
 */
public interface PromptSanitizer {

    /**
     * Sanitize outbound prompt text prior to hashing and dispatch.
     *
     * @param rawPrompt raw system or user prompt text
     * @return sanitized prompt text (never null when input is non-null)
     */
    String sanitize(String rawPrompt);
}
