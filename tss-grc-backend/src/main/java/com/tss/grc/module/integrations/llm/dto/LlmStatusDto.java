package com.tss.grc.module.integrations.llm.dto;

import java.util.List;

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
    long tokensUsedLast24h,
    GatewayMetricsSummary gatewayMetrics,
    List<RecentCallItem> recentCalls
) {
    public LlmStatusDto(
        boolean enabled,
        String mode,
        String provider,
        String model,
        boolean credentialsConfigured,
        long dailyTokenQuota,
        long tokensUsedLast24h
    ) {
        this(
            enabled,
            mode,
            provider,
            model,
            credentialsConfigured,
            dailyTokenQuota,
            tokensUsedLast24h,
            null,
            List.of()
        );
    }

    public record GatewayMetricsSummary(
        boolean reachable,
        long totalRequests,
        long cacheHits,
        long cacheMisses,
        double cacheHitRatePct,
        int activeCacheEntries,
        int cacheMaxEntries,
        int cacheTtlSeconds,
        long piiMaskedCount,
        long injectionsBlocked,
        long safetyViolationsBlocked,
        long hallucinationsDetected,
        long errors,
        double avgLatencyMs
    ) {}

    public record RecentCallItem(
        Long id,
        String featureCode,
        String provider,
        String model,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        String status,
        String finishReason,
        String createdAt
    ) {}
}
