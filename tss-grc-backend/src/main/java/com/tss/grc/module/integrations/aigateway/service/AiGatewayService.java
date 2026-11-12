package com.tss.grc.module.integrations.aigateway.service;

import com.tss.grc.module.integrations.aigateway.client.AiGatewayClient;
import com.tss.grc.module.integrations.aigateway.config.AiGatewayProperties;
import com.tss.grc.module.integrations.llm.dto.LlmGatewayException;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import com.tss.grc.module.integrations.llm.dto.LlmStatusDto;
import com.tss.grc.module.integrations.llm.entity.LlmCallLog;
import com.tss.grc.module.integrations.llm.repository.LlmCallLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise AI Gateway Service facade for TSS GRC Platform.
 *
 * <p>Delegates AI generation and guardrails to the external AI Gateway while preserving:
 * <ul>
 *   <li><b>PERF-IO-003:</b> No DB connection held across inference. Quota reads and audit
 *       ledger inserts execute in short {@code REQUIRES_NEW} transactions.</li>
 *   <li><b>Tenant Quota Protection:</b> Enforces daily token ceilings before outbound dispatch.
 *       Each call reserves its estimated tokens under a per-tenant lock until its ledger row
 *       is written, so concurrent calls see each other. The reservation lives in this JVM:
 *       in a cluster each instance enforces separately, so a tenant can overshoot by about
 *       one call per instance.</li>
 *   <li><b>Append-only Telemetry:</b> Logs all completions in {@code platform.llm_call_log}.</li>
 * </ul>
 */
@Service
public class AiGatewayService {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayService.class);

    private static final int ERROR_MSG_MAX = 2000;

    /** Output tokens reserved for a request that sets no {@code maxOutputTokens}. */
    static final long DEFAULT_OUTPUT_RESERVATION = 2048L;

    /** Tokens reserved by calls that passed the quota check and are not yet in the ledger. */
    private final ConcurrentHashMap<Long, Long> inFlightTokens = new ConcurrentHashMap<>();

    /** One monitor per tenant for check-and-reserve; one entry per organisation, never evicted. */
    private final ConcurrentHashMap<Long, Object> quotaLocks = new ConcurrentHashMap<>();

    private final AiGatewayProperties props;
    private final AiGatewayClient client;
    private final LlmCallLogRepository callLogRepo;
    private final TransactionTemplate requiresNewTx;
    private final TransactionTemplate readOnlyTx;

    public AiGatewayService(
        AiGatewayProperties props,
        AiGatewayClient client,
        LlmCallLogRepository callLogRepo,
        PlatformTransactionManager txManager
    ) {
        this.props = props;
        this.client = client;
        this.callLogRepo = callLogRepo;

        TransactionTemplate writeTpl = new TransactionTemplate(txManager);
        writeTpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = writeTpl;

        TransactionTemplate readTpl = new TransactionTemplate(txManager);
        readTpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);
        readTpl.setReadOnly(true);
        this.readOnlyTx = readTpl;
    }

    /**
     * Inspect non-sensitive gateway status, current 24h tenant token consumption,
     * live guardrail counters, and the tenant's recent call log.
     */
    public LlmStatusDto status(Long organisationId) {
        long used = 0L;
        List<LlmStatusDto.RecentCallItem> recentCalls = List.of();
        if (organisationId != null) {
            Long sum = readOnlyTx.execute(
                s -> callLogRepo.sumTokensSince(organisationId, LocalDateTime.now().minusHours(24)));
            used = sum == null ? 0L : sum;
            List<LlmCallLog> rows = readOnlyTx.execute(
                s -> callLogRepo.findTop15ByOrganisationIdOrderByIdDesc(organisationId));
            if (rows != null) {
                recentCalls = rows.stream()
                    .map(r -> new LlmStatusDto.RecentCallItem(
                        r.getId(),
                        r.getFeatureCode(),
                        r.getProvider(),
                        r.getModel(),
                        r.getInputTokens() == null ? 0 : r.getInputTokens(),
                        r.getOutputTokens() == null ? 0 : r.getOutputTokens(),
                        r.getLatencyMs() == null ? 0L : r.getLatencyMs(),
                        r.getStatus(),
                        r.getFinishReason(),
                        r.getCreatedAt() == null ? null : r.getCreatedAt().toString()
                    ))
                    .toList();
            }
        }
        boolean credsOk = props.getMode() == AiGatewayProperties.Mode.STUB || props.hasRequiredCredentials();
        String effectiveProvider = props.getMode() == AiGatewayProperties.Mode.STUB ? "STUB" : "AI_GATEWAY";
        LlmStatusDto.GatewayMetricsSummary gwMetrics = client.fetchMetrics();

        return new LlmStatusDto(
            props.isEnabled(),
            props.getMode().name(),
            effectiveProvider,
            props.getModel(),
            credsOk,
            props.getTenantDailyTokenQuota(),
            used,
            gwMetrics,
            recentCalls
        );
    }

    /**
     * Quota-check, execute through AI Gateway, and record telemetry.
     */
    public LlmResponse complete(Long organisationId, LlmRequest request) {
        if (organisationId == null) {
            throw new IllegalStateException("organisationId is required for metered AI Gateway execution");
        }

        String sys = request.systemPrompt() == null ? "" : request.systemPrompt();
        String usr = request.userPrompt() == null ? "" : request.userPrompt();
        String digest = sha256Hex(sys + "\n---\n" + usr);
        String provider = props.getMode() == AiGatewayProperties.Mode.STUB ? "STUB" : "AI_GATEWAY";

        if (!props.isEnabled()) {
            persistCallLog(organisationId, request, provider, props.getModel(), digest,
                0, 0, 0L, "DISABLED", null, "AI Gateway is disabled by administrator configuration");
            throw new LlmGatewayException("AI_GATEWAY_DISABLED",
                "AI generation features are currently disabled by administrator configuration.");
        }

        long quota = props.getTenantDailyTokenQuota();
        long reserved = 0L;
        if (quota > 0) {
            reserved = estimateTokens(request);
            String refusal = tryReserve(organisationId, quota, reserved);
            if (refusal != null) {
                persistCallLog(organisationId, request, provider, props.getModel(), digest,
                    0, 0, 0L, "QUOTA_EXCEEDED", null, refusal);
                throw new LlmGatewayException("QUOTA_EXCEEDED", refusal);
            }
        }

        long startMs = System.currentTimeMillis();
        try {
            try {
                LlmResponse response = client.complete(organisationId, request);
                long latency = Math.max(1L, System.currentTimeMillis() - startMs);

                persistCallLog(organisationId, request, provider, props.getModel(), digest,
                    response.inputTokens(), response.outputTokens(), latency,
                    "SUCCEEDED", response.finishReason(), null);

                return response;

            } catch (LlmGatewayException ex) {
                long latency = Math.max(1L, System.currentTimeMillis() - startMs);
                persistCallLog(organisationId, request, provider, props.getModel(), digest,
                    0, 0, latency, "FAILED", null, ex.getMessage());
                throw ex;
            } catch (Exception ex) {
                long latency = Math.max(1L, System.currentTimeMillis() - startMs);
                persistCallLog(organisationId, request, provider, props.getModel(), digest,
                    0, 0, latency, "FAILED", null, ex.getMessage());
                throw new LlmGatewayException("EXECUTION_FAILED", "AI Gateway call failed: " + ex.getMessage(), ex);
            }
        } finally {
            // After the call-log row is committed, never before: see tryReserve.
            release(organisationId, reserved);
        }
    }

    /**
     * Checks the quota and reserves {@code tokens} for one call, atomically per tenant.
     * Returns null when reserved, or the refusal message.
     *
     * <p>A call may start only while the ledger's 24h total plus the tokens reserved by calls
     * still in progress is below the quota, so concurrent requests cannot all pass on the
     * same stale total. In-flight is read before the ledger: a call that finishes in between
     * commits its row before releasing its reservation, so it is counted once or twice,
     * never zero times.
     */
    private String tryReserve(Long organisationId, long quota, long tokens) {
        synchronized (quotaLocks.computeIfAbsent(organisationId, k -> new Object())) {
            long inFlight = inFlightTokens.getOrDefault(organisationId, 0L);
            Long usedObj = readOnlyTx.execute(
                s -> callLogRepo.sumTokensSince(organisationId, LocalDateTime.now().minusHours(24)));
            long used = usedObj == null ? 0L : usedObj;
            if (used + inFlight >= quota) {
                return String.format(Locale.ROOT,
                    "Daily tenant token quota exceeded (%d / %d tokens in the last 24h, %d reserved by calls in progress).",
                    used, quota, inFlight);
            }
            inFlightTokens.merge(organisationId, tokens, Long::sum);
            return null;
        }
    }

    private void release(Long organisationId, long tokens) {
        if (tokens > 0) {
            inFlightTokens.computeIfPresent(organisationId, (k, v) -> v > tokens ? v - tokens : null);
        }
    }

    /**
     * Upper-bound guess at a call's tokens: about three prompt characters per token (cautious
     * for Arabic, generous for English) plus the output ceiling, or a default when unset.
     */
    static long estimateTokens(LlmRequest req) {
        long chars = (req.systemPrompt() == null ? 0 : req.systemPrompt().length())
            + (req.userPrompt() == null ? 0 : req.userPrompt().length());
        long output = req.maxOutputTokens() != null ? req.maxOutputTokens() : DEFAULT_OUTPUT_RESERVATION;
        return (chars + 2) / 3 + output;
    }

    private void persistCallLog(
        Long organisationId,
        LlmRequest req,
        String provider,
        String model,
        String promptSha256,
        int inTok,
        int outTok,
        long latencyMs,
        String status,
        String finishReason,
        String errorMessage
    ) {
        try {
            requiresNewTx.executeWithoutResult(statusTx -> {
                LlmCallLog row = new LlmCallLog();
                row.setOrganisationId(organisationId);
                row.setFeatureCode(req.featureCode());
                row.setPromptTemplateId(req.promptTemplateId());
                row.setPromptTemplateVersion(req.promptTemplateVersion());
                row.setProvider(provider);
                row.setModel(model);
                row.setPromptSha256(promptSha256);
                row.setInputTokens(inTok);
                row.setOutputTokens(outTok);
                row.setLatencyMs(latencyMs);
                row.setStatus(status);
                row.setFinishReason(finishReason);
                if (errorMessage != null && errorMessage.length() > ERROR_MSG_MAX) {
                    row.setErrorMessage(errorMessage.substring(0, ERROR_MSG_MAX));
                } else {
                    row.setErrorMessage(errorMessage);
                }
                callLogRepo.save(row);
            });
        } catch (Exception ex) {
            // The business flow carries on, but a lost row is a lost audit record and, for a
            // SUCCEEDED call, tokens the quota never sees - so it must be loud.
            log.error("[AI Gateway] Failed to write llm_call_log row: org={} feature={} status={} tokens={}",
                organisationId, req.featureCode(), status, inTok + outTok, ex);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
