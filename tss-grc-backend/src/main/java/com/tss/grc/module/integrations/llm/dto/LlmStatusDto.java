package com.tss.grc.module.integrations.llm.dto;

/**
 * Non-sensitive runtime status of the LLM gateway for UI display and operator diagnostics.
 * Never exposes API keys or internal URLs containing credentials.
 */
public record LlmStatusDto(
    boolean enabled,
    String mode,
    String provider,
    String model,
    boolean credentialsConfigured,
    long dailyTokenQuota,
    long tokensUsedLast24h
) {}
