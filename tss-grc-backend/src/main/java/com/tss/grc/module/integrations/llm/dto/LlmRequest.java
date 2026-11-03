package com.tss.grc.module.integrations.llm.dto;

import java.util.List;

/**
 * Provider-neutral structured completion request passed into the LLM gateway.
 *
 * @param featureCode           calling feature identifier (e.g. {@code POLICY_DRAFT})
 * @param promptTemplateId      registered prompt template identifier
 * @param promptTemplateVersion semantic version of the prompt template
 * @param systemPrompt          system instructions framing role, output contract, and guardrails
 * @param userPrompt            tenant-grounded context and task instructions
 * @param jsonMode              when true, instructs the provider to emit a JSON object
 * @param maxOutputTokens       optional per-call token ceiling (null uses gateway default)
 * @param temperature           optional per-call temperature (null uses gateway default)
 * @param groundingControlIds   optional control IDs supplied in grounding context (used by STUB mode)
 */
public record LlmRequest(
    String featureCode,
    String promptTemplateId,
    String promptTemplateVersion,
    String systemPrompt,
    String userPrompt,
    boolean jsonMode,
    Integer maxOutputTokens,
    Double temperature,
    List<Long> groundingControlIds
) {
    public LlmRequest {
        groundingControlIds = groundingControlIds == null ? List.of() : List.copyOf(groundingControlIds);
    }
}
