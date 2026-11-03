package com.tss.grc.module.integrations.aigateway.service;

import com.tss.grc.module.integrations.aigateway.client.AiGatewayClient;
import com.tss.grc.module.integrations.aigateway.config.AiGatewayProperties;
import com.tss.grc.module.integrations.llm.dto.LlmGatewayException;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import com.tss.grc.module.integrations.llm.dto.LlmStatusDto;
import com.tss.grc.module.integrations.llm.entity.LlmCallLog;
import com.tss.grc.module.integrations.llm.repository.LlmCallLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Enterprise AI Gateway Service facade for TSS GRC Platform.
 *
 * <p>Delegates AI generation and guardrails to the external AI Gateway while preserving:
 * <ul>
 *   <li><b>PERF-IO-003:</b> No DB connection held across inference. Quota reads and audit
 *       ledger inserts execute in short {@code REQUIRES_NEW} transactions.</li>
 *   <li><b>Tenant Quota Protection:</b> Enforces daily token ceilings before outbound dispatch.</li>
 *   <li><b>Append-only Telemetry:</b> Logs all completions in {@code platform.llm_call_log}.</li>
 * </ul>
 */
@Service
public class AiGatewayService {

    private static final int ERROR_MSG_MAX = 2000;

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
     * Inspect non-sensitive gateway status and current 24h tenant token consumption.
     */
    public LlmStatusDto status(Long organisationId) {
        long used = 0L;
        if (organisationId != null) {
            Long sum = readOnlyTx.execute(
                s -> callLogRepo.sumTokensSince(organisationId, LocalDateTime.now().minusHours(24)));
            used = sum == null ? 0L : sum;
        }
        boolean credsOk = props.getMode() == AiGatewayProperties.Mode.STUB || props.hasRequiredCredentials();
        String effectiveProvider = props.getMode() == AiGatewayProperties.Mode.STUB ? "STUB" : "AI_GATEWAY";

        return new LlmStatusDto(
            props.isEnabled(),
            props.getMode().name(),
            effectiveProvider,
            props.getModel(),
            credsOk,
            props.getTenantDailyTokenQuota(),
            used
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
        if (quota > 0) {
            Long usedObj = readOnlyTx.execute(
                s -> callLogRepo.sumTokensSince(organisationId, LocalDateTime.now().minusHours(24)));
            long used = usedObj == null ? 0L : usedObj;
            if (used >= quota) {
                String msg = String.format(Locale.ROOT,
                    "Daily tenant token quota exceeded (%d / %d tokens in the last 24h).", used, quota);
                persistCallLog(organisationId, request, provider, props.getModel(), digest,
                    0, 0, 0L, "QUOTA_EXCEEDED", null, msg);
                throw new LlmGatewayException("QUOTA_EXCEEDED", msg);
            }
        }

        long startMs = System.currentTimeMillis();
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
            // Never break business flow if telemetry insert fails
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
