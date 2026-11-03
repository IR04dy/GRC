package com.tss.grc.module.integrations.llm.dto;

/**
 * Provider-neutral completion response returned by the LLM gateway.
 */
public record LlmResponse(
    String content,
    String provider,
    String model,
    int inputTokens,
    int outputTokens,
    long latencyMs,
    String finishReason
) {}
