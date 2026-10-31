package com.tss.grc.module.policy.drafting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.Control;
import com.tss.grc.module.compliance.entity.Framework;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.compliance.repository.FrameworkRepository;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import com.tss.grc.module.integrations.llm.service.LlmGatewayService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.CreatePolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.PolicyDraftResponse;
import com.tss.grc.module.policy.drafting.entity.PolicyAiDraft;
import com.tss.grc.module.policy.drafting.repository.PolicyAiDraftRepository;
import com.tss.grc.module.policy.drafting.service.PolicyDraftOutputValidator.ValidationResult;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Asynchronous background worker that executes tenant-grounded LLM policy
 * generation off the request thread without holding open database connections
 * across inference calls.
 */
@Service
public class PolicyDraftAsyncWorker {

    private static final Logger log = LoggerFactory.getLogger(PolicyDraftAsyncWorker.class);
    private static final int MAX_GROUNDING_CONTROLS = 40;
    private static final int FAILURE_REASON_MAX = 2000;

    private final PolicyAiDraftRepository draftRepo;
    private final OrganisationRepository orgRepo;
    private final FrameworkRepository frameworkRepo;
    private final ControlRepository controlRepo;
    private final PolicyDraftPromptBuilder promptBuilder;
    private final PolicyDraftOutputValidator validator;
    private final LlmGatewayService llmGateway;
    private final AuditLogService audit;
    private final NotificationService notifications;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTx;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public PolicyDraftAsyncWorker(
        PolicyAiDraftRepository draftRepo,
        OrganisationRepository orgRepo,
        FrameworkRepository frameworkRepo,
        ControlRepository controlRepo,
        PolicyDraftPromptBuilder promptBuilder,
        PolicyDraftOutputValidator validator,
        LlmGatewayService llmGateway,
        AuditLogService audit,
        NotificationService notifications,
        ObjectMapper objectMapper,
        PlatformTransactionManager txManager
    ) {
        this.draftRepo = draftRepo;
        this.orgRepo = orgRepo;
        this.frameworkRepo = frameworkRepo;
        this.controlRepo = controlRepo;
        this.promptBuilder = promptBuilder;
        this.validator = validator;
        this.llmGateway = llmGateway;
        this.audit = audit;
        this.notifications = notifications;
        this.objectMapper = objectMapper;

        TransactionTemplate tpl = new TransactionTemplate(txManager);
        tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tpl;
    }

    private record GroundingContext(
        LlmRequest llmRequest,
        Set<Long> allowedControlIds
    ) {}

    @Async
    public void generateAsync(Long draftId, Long orgId, String requesterUsername, Long requesterUserId) {
        TenantContext.setCurrentTenant(orgId);
        try {
            GroundingContext ctx = requiresNewTx.execute(tx -> prepareAndMarkGenerating(draftId, orgId));
            if (ctx == null) {
                return;
            }

            int totalInTokens = 0;
            int totalOutTokens = 0;
            long totalLatencyMs = 0L;
            LlmResponse llmResp = null;
            ValidationResult validated = null;
            Exception terminalFailure = null;

            try {
                llmResp = llmGateway.complete(orgId, ctx.llmRequest());
                totalInTokens += llmResp.inputTokens();
                totalOutTokens += llmResp.outputTokens();
                totalLatencyMs += llmResp.latencyMs();
                try {
                    validated = validator.validateAndSanitize(llmResp.content(), ctx.allowedControlIds());
                } catch (IllegalArgumentException firstValidationErr) {
                    log.info("[PolicyDraft] draftId={} initial output failed validation ({}); running 1 repair pass",
                        draftId, firstValidationErr.getMessage());
                    LlmRequest repairReq = promptBuilder.buildRepairPrompt(
                        ctx.llmRequest(), llmResp.content(), firstValidationErr.getMessage());
                    LlmResponse repairResp = llmGateway.complete(orgId, repairReq);
                    llmResp = repairResp;
                    totalInTokens += repairResp.inputTokens();
                    totalOutTokens += repairResp.outputTokens();
                    totalLatencyMs += repairResp.latencyMs();
                    validated = validator.validateAndSanitize(repairResp.content(), ctx.allowedControlIds());
                }
            } catch (Exception ex) {
                terminalFailure = ex;
                log.warn("[PolicyDraft] draftId={} generation failed: {}", draftId, ex.getMessage());
            }

            final LlmResponse finalLlm = llmResp;
            final ValidationResult finalValidated = validated;
            final Exception finalFailure = terminalFailure;
            final int inTok = totalInTokens;
            final int outTok = totalOutTokens;
            final long latMs = totalLatencyMs;

            PolicyDraftResponse updatedDto = requiresNewTx.execute(tx -> persistOutcome(
                draftId, orgId, requesterUsername, requesterUserId,
                finalLlm, finalValidated, finalFailure, inTok, outTok, latMs
            ));

            if (updatedDto != null && broadcaster != null) {
                broadcaster.broadcastEntityUpdate(orgId, "policy_ai_draft", updatedDto.externalId(), updatedDto);
                broadcaster.broadcastOrgEvent(orgId, "policy.ai_draft", Map.of(
                    "draftId", updatedDto.id(),
                    "externalId", updatedDto.externalId(),
                    "status", updatedDto.status(),
                    "requestedBy", requesterUsername == null ? "" : requesterUsername
                ));
                broadcaster.broadcastOrgEvent(orgId, "notifications", Map.of(
                    "kind", "POLICY_AI_DRAFT",
                    "draftId", updatedDto.id(),
                    "status", updatedDto.status()
                ));
            }
        } finally {
            TenantContext.clear();
        }
    }

    private GroundingContext prepareAndMarkGenerating(Long draftId, Long orgId) {
        PolicyAiDraft draft = draftRepo.findByIdAndOrganisationId(draftId, orgId).orElse(null);
        if (draft == null || !"REQUESTED".equals(draft.getStatus())) {
            return null;
        }
        draft.setStatus(PolicyAiDraft.STATUS.require("GENERATING"));
        draftRepo.save(draft);

        CreatePolicyDraftRequest req;
        try {
            req = objectMapper.readValue(draft.getRequestJson(), CreatePolicyDraftRequest.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Corrupt requestJson on PolicyAiDraft " + draftId, ex);
        }

        Organisation org = orgRepo.findById(orgId).orElse(null);

        List<Framework> frameworks = List.of();
        if (req.frameworkIds() != null && !req.frameworkIds().isEmpty()) {
            frameworks = frameworkRepo.findAllById(req.frameworkIds()).stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsActive()))
                .toList();
        }

        Map<Long, Control> selectedControls = new LinkedHashMap<>();
        if (req.controlIds() != null && !req.controlIds().isEmpty()) {
            for (Control c : controlRepo.findByOrganisationIdAndIdIn(orgId, req.controlIds())) {
                selectedControls.put(c.getId(), c);
            }
        }
        if (selectedControls.isEmpty() && !frameworks.isEmpty()) {
            for (Framework f : frameworks) {
                for (Control c : controlRepo.findByOrganisationIdAndFrameworkId(orgId, f.getId())) {
                    selectedControls.putIfAbsent(c.getId(), c);
                    if (selectedControls.size() >= MAX_GROUNDING_CONTROLS) {
                        break;
                    }
                }
                if (selectedControls.size() >= MAX_GROUNDING_CONTROLS) {
                    break;
                }
            }
        }
        if (selectedControls.isEmpty()) {
            for (Control c : controlRepo.findByOrganisationId(orgId, PageRequest.of(0, 20)).getContent()) {
                selectedControls.putIfAbsent(c.getId(), c);
            }
        }

        List<Control> controlList = new ArrayList<>(selectedControls.values());
        if (controlList.size() > MAX_GROUNDING_CONTROLS) {
            controlList = controlList.subList(0, MAX_GROUNDING_CONTROLS);
        }
        Set<Long> allowedIds = new LinkedHashSet<>();
        for (Control c : controlList) {
            allowedIds.add(c.getId());
        }

        LlmRequest llmReq = promptBuilder.buildPrompt(req, org, frameworks, controlList, draft.getTone());
        return new GroundingContext(llmReq, allowedIds);
    }

    private PolicyDraftResponse persistOutcome(
        Long draftId,
        Long orgId,
        String requesterUsername,
        Long requesterUserId,
        LlmResponse llmResp,
        ValidationResult validated,
        Exception failure,
        int inTokens,
        int outTokens,
        long latencyMs
    ) {
        PolicyAiDraft draft = draftRepo.findByIdAndOrganisationId(draftId, orgId).orElse(null);
        if (draft == null) {
            return null;
        }
        draft.setInputTokens(inTokens);
        draft.setOutputTokens(outTokens);
        draft.setLatencyMs(latencyMs);
        if (llmResp != null) {
            draft.setProvider(truncate(llmResp.provider(), 40));
            draft.setModel(truncate(llmResp.model(), 120));
        }

        if (validated != null && failure == null) {
            try {
                draft.setOutputJson(objectMapper.writeValueAsString(validated.content()));
                if (!validated.hallucinatedCitations().isEmpty()) {
                    draft.setHallucinatedCitations(
                        objectMapper.writeValueAsString(validated.hallucinatedCitations()));
                }
                draft.setFailureReason(null);
                draft.setStatus(PolicyAiDraft.STATUS.require("GENERATED"));
            } catch (Exception serEx) {
                draft.setStatus(PolicyAiDraft.STATUS.require("FAILED"));
                draft.setFailureReason(truncate("Output serialization failed: " + serEx.getMessage(), FAILURE_REASON_MAX));
            }
        } else {
            draft.setStatus(PolicyAiDraft.STATUS.require("FAILED"));
            String reason = failure != null && failure.getMessage() != null
                ? failure.getMessage()
                : "Policy draft generation failed";
            draft.setFailureReason(truncate(reason, FAILURE_REASON_MAX));
        }

        PolicyAiDraft saved = draftRepo.save(draft);

        if ("GENERATED".equals(saved.getStatus())) {
            String titleEn = validated != null ? validated.content().titleEn() : saved.getPolicyType();
            String titleAr = validated != null ? validated.content().titleAr() : saved.getPolicyType();
            audit.log("POLICY_AI_DRAFT_GENERATED", "PolicyAiDraft", saved.getId(),
                "AI policy draft generated (" + saved.getExternalId() + ") via "
                    + saved.getProvider() + "/" + saved.getModel()
                    + " for " + requesterUsername);
            notifications.notifyUser(
                orgId,
                requesterUserId,
                "AI policy draft ready: " + truncate(titleEn, 180),
                "مسودة السياسة الذكية جاهزة: " + truncate(titleAr, 180),
                "Your AI-drafted policy (" + saved.getExternalId() + ") is ready for bilingual review and acceptance.",
                "POLICY_AI_DRAFT_READY",
                "PolicyAiDraft",
                saved.getId()
            );
        } else {
            audit.log("POLICY_AI_DRAFT_FAILED", "PolicyAiDraft", saved.getId(),
                "AI policy draft failed (" + saved.getExternalId() + "): " + saved.getFailureReason());
            notifications.notifyUser(
                orgId,
                requesterUserId,
                "AI policy draft failed (" + saved.getExternalId() + ")",
                "تعذر إنشاء مسودة السياسة الذكية (" + saved.getExternalId() + ")",
                "Draft generation failed: " + truncate(saved.getFailureReason(), 300),
                "POLICY_AI_DRAFT_FAILED",
                "PolicyAiDraft",
                saved.getId()
            );
        }

        return PolicyDraftService.toResponseStatic(saved, objectMapper);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
