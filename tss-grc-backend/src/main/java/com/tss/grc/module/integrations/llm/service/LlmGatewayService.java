package com.tss.grc.module.integrations.llm.service;

import com.tss.grc.module.integrations.llm.client.LlmClient;
import com.tss.grc.module.integrations.llm.config.LlmProperties;
import com.tss.grc.module.integrations.llm.dto.LlmGatewayException;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import com.tss.grc.module.integrations.llm.dto.LlmStatusDto;
import com.tss.grc.module.integrations.llm.entity.LlmCallLog;
import com.tss.grc.module.integrations.llm.repository.LlmCallLogRepository;
import com.tss.grc.module.integrations.llm.sanitize.PromptSanitizer;
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
 * Tenant-metered LLM gateway facade.
 *
 * <p>Key operational invariants:
 * <ul>
 *   <li><b>No DB connection held across inference:</b> quota checks and call-log
 *       inserts run inside short {@code PROPAGATION_REQUIRES_NEW} transactions via
 *       {@link TransactionTemplate}, keeping HikariCP connections free during 5–60s
 *       LLM calls (PERF-IO-003).</li>
 *   <li><b>Pluggable PII masking seam:</b> prompts pass through {@link PromptSanitizer}
 *       before SHA-256 hashing and transport dispatch.</li>
 *   <li><b>Append-only telemetry:</b> every attempt (succeeded, failed, disabled, or
 *       quota-blocked) records a row in {@code platform.llm_call_log}.</li>
 * </ul>
 */
@Service
public class LlmGatewayService {

    private static final int ERROR_MSG_MAX = 2000;

    private final LlmProperties props;
    private final LlmClient client;
    private final PromptSanitizer sanitizer;
    private final LlmCallLogRepository callLogRepo;
    private final TransactionTemplate requiresNewTx;
    private final TransactionTemplate readOnlyTx;

    public LlmGatewayService(
        LlmProperties props,
        LlmClient client,
        PromptSanitizer sanitizer,
        LlmCallLogRepository callLogRepo,
        PlatformTransactionManager txManager
    ) {
        this.props = props;
        this.client = client;
        this.sanitizer = sanitizer;
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
        boolean credsOk = props.getMode() == LlmProperties.Mode.STUB || props.hasRequiredCredentials();
        String effectiveProvider = props.getMode() == LlmProperties.Mode.STUB
            ? "STUB"
            : props.getProvider().name();
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
     * Sanitize, quota-check, execute, and meter a single LLM completion call.
     * Must be invoked outside a long-lived transaction; manages its own short
     * {@code REQUIRES_NEW} transactions for quota read and call-log write.
     */
    public LlmResponse complete(Long organisationId, LlmRequest request) {
        if (organisationId == null) {
            throw new IllegalStateException("organisationId is required for metered LLM execution");
        }

        String sanitizedSystem = sanitizer.sanitize(request.systemPrompt());
        String sanitizedUser = sanitizer.sanitize(request.userPrompt());
        String digest = sha256Hex(sanitizedSystem + "\n---\n" + sanitizedUser);
        String activeProvider = props.getMode() == LlmProperties.Mode.STUB
            ? "STUB"
            : props.getProvider().name();

        if (!props.isEnabled()) {
            persistCallLog(organisationId, request, activeProvider, props.getModel(), digest,
                0, 0, 0L, "DISABLED", null, "LLM gateway is disabled by configuration");
            throw new LlmGatewayException("LLM_DISABLED",
                "AI policy drafting is currently disabled by administrator configuration.");
        }

        long quota = props.getTenantDailyTokenQuota();
        if (quota > 0) {
            Long usedObj = readOnlyTx.execute(
                s -> callLogRepo.sumTokensSince(organisationId, LocalDateTime.now().minusHours(24)));
            long used = usedObj == null ? 0L : usedObj;
            if (used >= quota) {
                String msg = String.format(Locale.ROOT,
                    "Daily tenant LLM token quota exceeded (%d / %d tokens in the last 24h).", used, quota);
                persistCallLog(organisationId, request, activeProvider, props.getModel(), digest,
                    0, 0, 0L, "QUOTA_EXCEEDED", null, msg);
                throw new LlmGatewayException("QUOTA_EXCEEDED", msg);
            }
        }

        LlmRequest sanitizedReq = new LlmRequest(
            request.featureCode(),
            request.promptTemplateId(),
            request.promptTemplateVersion(),
            sanitizedSystem,
            sanitizedUser,
            request.jsonMode(),
            request.maxOutputTokens(),
            request.temperature(),
            request.groundingControlIds()
        );

        long startMs = System.currentTimeMillis();
        try {
            LlmResponse response = client.complete(sanitizedReq);
            persistCallLog(
                organisationId,
                sanitizedReq,
                response.provider(),
                response.model(),
                digest,
                response.inputTokens(),
                response.outputTokens(),
                response.latencyMs(),
                "SUCCEEDED",
                truncate(response.finishReason(), 60),
                null
            );
            return response;
        } catch (RuntimeException ex) {
            long elapsed = Math.max(1L, System.currentTimeMillis() - startMs);
            persistCallLog(
                organisationId,
                sanitizedReq,
                activeProvider,
                props.getModel(),
                digest,
                0,
                0,
                elapsed,
                "FAILED",
                null,
                truncate(ex.getMessage(), ERROR_MSG_MAX)
            );
            if (ex instanceof LlmGatewayException gex) {
                throw gex;
            }
            throw new LlmGatewayException("PROVIDER_ERROR",
                "LLM inference failed: " + ex.getMessage(), ex);
        }
    }

    private void persistCallLog(
        Long organisationId,
        LlmRequest callSpec,
        String provider,
        String model,
        String promptSha256,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        String status,
        String finishReason,
        String errorMessage
    ) {
        String validStatus = LlmCallLog.STATUS.require(status);
        requiresNewTx.executeWithoutResult(tx -> {
            LlmCallLog row = new LlmCallLog();
            row.setOrganisationId(organisationId);
            row.setFeatureCode(truncate(callSpec.featureCode(), 60));
            row.setPromptTemplateId(truncate(callSpec.promptTemplateId(), 80));
            row.setPromptTemplateVersion(truncate(callSpec.promptTemplateVersion(), 20));
            row.setProvider(truncate(provider, 40));
            row.setModel(truncate(model, 120));
            row.setPromptSha256(promptSha256);
            row.setInputTokens(Math.max(0, inputTokens));
            row.setOutputTokens(Math.max(0, outputTokens));
            row.setLatencyMs(Math.max(0L, latencyMs));
            row.setStatus(validStatus);
            row.setFinishReason(truncate(finishReason, 60));
            row.setErrorMessage(truncate(errorMessage, ERROR_MSG_MAX));
            callLogRepo.save(row);
        });
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
