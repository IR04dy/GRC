package com.tss.grc.module.policy.drafting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.util.ExternalIds;
import com.tss.grc.module.compliance.entity.Control;
import com.tss.grc.module.compliance.entity.Framework;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.compliance.repository.FrameworkRepository;
import com.tss.grc.module.integrations.llm.dto.LlmStatusDto;
import com.tss.grc.module.integrations.llm.service.LlmGatewayService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.AcceptPolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.CreatePolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.GeneratedPolicyContent;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.PolicyDraftResponse;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.RejectPolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.UpdatePolicyDraftRequest;
import com.tss.grc.module.policy.drafting.entity.PolicyAiDraft;
import com.tss.grc.module.policy.drafting.repository.PolicyAiDraftRepository;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.entity.PolicyDocumentFrameworkMapping;
import com.tss.grc.module.policy.repository.PolicyDocumentFrameworkMappingRepository;
import com.tss.grc.module.policy.service.PolicyManagementService;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates AI-assisted policy drafting, human review/editing, and promotion
 * into the canonical {@code policy.documents} register.
 */
@Service
public class PolicyDraftService {

    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {};

    private final PolicyAiDraftRepository draftRepo;
    private final PolicyManagementService policyManagementService;
    private final PolicyDocumentFrameworkMappingRepository frameworkMappingRepo;
    private final FrameworkRepository frameworkRepo;
    private final ControlRepository controlRepo;
    private final LlmGatewayService llmGateway;
    private final PolicyDraftAsyncWorker asyncWorker;
    private final AuditLogService audit;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTx;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public PolicyDraftService(
        PolicyAiDraftRepository draftRepo,
        PolicyManagementService policyManagementService,
        PolicyDocumentFrameworkMappingRepository frameworkMappingRepo,
        FrameworkRepository frameworkRepo,
        ControlRepository controlRepo,
        LlmGatewayService llmGateway,
        PolicyDraftAsyncWorker asyncWorker,
        AuditLogService audit,
        ObjectMapper objectMapper,
        PlatformTransactionManager txManager
    ) {
        this.draftRepo = draftRepo;
        this.policyManagementService = policyManagementService;
        this.frameworkMappingRepo = frameworkMappingRepo;
        this.frameworkRepo = frameworkRepo;
        this.controlRepo = controlRepo;
        this.llmGateway = llmGateway;
        this.asyncWorker = asyncWorker;
        this.audit = audit;
        this.objectMapper = objectMapper;

        TransactionTemplate tpl = new TransactionTemplate(txManager);
        tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tpl;
    }

    public LlmStatusDto gatewayStatus() {
        return llmGateway.status(CurrentActor.organisationId());
    }

    @Transactional(readOnly = true)
    public List<PolicyDraftResponse> listDrafts(String status) {
        Long orgId = CurrentActor.organisationId();
        List<PolicyAiDraft> rows;
        if (status != null && !status.isBlank()) {
            String validStatus = PolicyAiDraft.STATUS.require(status);
            rows = draftRepo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(orgId, validStatus);
        } else {
            rows = draftRepo.findByOrganisationIdOrderByCreatedAtDesc(orgId);
        }
        List<PolicyDraftResponse> out = new ArrayList<>(rows.size());
        for (PolicyAiDraft r : rows) {
            out.add(toResponseStatic(r, objectMapper));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public PolicyDraftResponse getDraft(Long id) {
        Long orgId = CurrentActor.organisationId();
        PolicyAiDraft draft = draftRepo.findByIdAndOrganisationId(id, orgId)
            .orElseThrow(() -> new EntityNotFoundException("AI policy draft not found: " + id));
        return toResponseStatic(draft, objectMapper);
    }

    /**
     * Commits a {@code REQUESTED} draft row in an independent short transaction,
     * then dispatches asynchronous generation on {@link PolicyDraftAsyncWorker}.
     */
    public PolicyDraftResponse requestDraft(CreatePolicyDraftRequest req) {
        Long orgId = CurrentActor.organisationId();
        String actor = CurrentActor.requireUsername("requesting an AI policy draft");
        Long userId = CurrentActor.userId();

        String tone = (req.tone() == null || req.tone().isBlank())
            ? "FORMAL_REGULATORY"
            : req.tone().trim().toUpperCase(Locale.ROOT);
        if (tone.length() > 40) {
            tone = tone.substring(0, 40);
        }
        final String effectiveTone = tone;

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(req);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid draft request payload: " + ex.getMessage(), ex);
        }

        PolicyAiDraft saved = requiresNewTx.execute(tx -> {
            PolicyAiDraft d = new PolicyAiDraft();
            d.setOrganisationId(orgId);
            d.setExternalId(ExternalIds.generate("AID"));
            d.setStatus(PolicyAiDraft.STATUS.require("REQUESTED"));
            d.setPolicyType(req.policyType().trim());
            d.setCategory(req.category().trim());
            d.setTone(effectiveTone);
            d.setRequestJson(requestJson);
            d.setPromptTemplateId(PolicyDraftPromptBuilder.TEMPLATE_ID);
            d.setPromptTemplateVersion(PolicyDraftPromptBuilder.TEMPLATE_VERSION);
            d.setRequestedBy(actor);
            d.setRequestedByUserId(userId);
            PolicyAiDraft persisted = draftRepo.save(d);
            audit.log("POLICY_AI_DRAFT_REQUESTED", "PolicyAiDraft", persisted.getId(),
                "AI policy draft requested (" + persisted.getExternalId() + ", type="
                    + persisted.getPolicyType() + ") by " + actor);
            return persisted;
        });

        if (saved == null) {
            throw new IllegalStateException("Failed to persist AI policy draft request");
        }

        PolicyDraftResponse resp = toResponseStatic(saved, objectMapper);
        asyncWorker.generateAsync(saved.getId(), orgId, actor, userId);
        return resp;
    }

    @Transactional
    public PolicyDraftResponse updateDraft(Long id, UpdatePolicyDraftRequest req) {
        Long orgId = CurrentActor.organisationId();
        String actor = CurrentActor.requireUsername("editing an AI policy draft");
        PolicyAiDraft draft = draftRepo.findByIdAndOrganisationId(id, orgId)
            .orElseThrow(() -> new EntityNotFoundException("AI policy draft not found: " + id));
        if (!"GENERATED".equals(draft.getStatus())) {
            throw new IllegalStateException(
                "Only GENERATED drafts can be edited (current status: " + draft.getStatus() + ")");
        }

        List<Long> validControlIds = resolveTenantControlIds(orgId, req.citedControlIds());
        String cadence = (req.suggestedReviewCadence() == null || req.suggestedReviewCadence().isBlank())
            ? "ANNUAL"
            : req.suggestedReviewCadence().trim().toUpperCase(Locale.ROOT);

        GeneratedPolicyContent edited = new GeneratedPolicyContent(
            req.titleEn().trim(),
            req.titleAr().trim(),
            req.summaryEn() == null ? "" : req.summaryEn().trim(),
            req.summaryAr() == null ? "" : req.summaryAr().trim(),
            req.contentMdEn().trim(),
            req.contentMdAr().trim(),
            validControlIds,
            cadence,
            req.assumptions() == null ? List.of() : List.copyOf(req.assumptions())
        );

        try {
            draft.setEditedOutputJson(objectMapper.writeValueAsString(edited));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to serialize edited policy draft", ex);
        }
        if (req.reviewerNote() != null) {
            draft.setReviewerNote(blankToNull(req.reviewerNote()));
        }
        draft.setReviewedBy(actor);
        draft.setReviewedAt(LocalDateTime.now());

        PolicyAiDraft saved = draftRepo.save(draft);
        audit.log("POLICY_AI_DRAFT_EDITED", "PolicyAiDraft", saved.getId(),
            "AI policy draft edited (" + saved.getExternalId() + ") by " + actor);
        PolicyDraftResponse resp = toResponseStatic(saved, objectMapper);
        broadcastDraftChange(orgId, resp);
        return resp;
    }

    @Transactional
    public PolicyDraftResponse acceptDraft(Long id, AcceptPolicyDraftRequest req) {
        Long orgId = CurrentActor.organisationId();
        String actor = CurrentActor.requireUsername("accepting an AI policy draft");
        PolicyAiDraft draft = draftRepo.findByIdAndOrganisationId(id, orgId)
            .orElseThrow(() -> new EntityNotFoundException("AI policy draft not found: " + id));
        if (!"GENERATED".equals(draft.getStatus())) {
            throw new IllegalStateException(
                "Only GENERATED drafts can be accepted into the policy register (current status: "
                    + draft.getStatus() + ")");
        }

        String effectiveJson = (draft.getEditedOutputJson() != null && !draft.getEditedOutputJson().isBlank())
            ? draft.getEditedOutputJson()
            : draft.getOutputJson();
        if (effectiveJson == null || effectiveJson.isBlank()) {
            throw new IllegalStateException("Draft has no generated content to accept");
        }

        GeneratedPolicyContent content;
        try {
            content = objectMapper.readValue(effectiveJson, GeneratedPolicyContent.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse generated policy content", ex);
        }

        if (isBlank(content.titleEn()) || isBlank(content.titleAr())
                || isBlank(content.contentMdEn()) || isBlank(content.contentMdAr())) {
            throw new IllegalArgumentException(
                "Both English and Arabic titles and policy bodies are required before accepting a draft");
        }

        CreatePolicyDraftRequest origReq = parseRequestSilently(draft.getRequestJson(), objectMapper);

        String combinedBilingualMarkdown = composeBilingualMarkdown(
            content.titleEn().trim(),
            content.contentMdEn().trim(),
            content.titleAr().trim(),
            content.contentMdAr().trim()
        );

        List<Long> validControlIds = resolveTenantControlIds(orgId, content.citedControlIds());
        List<Framework> validFrameworks = resolveActiveFrameworks(
            origReq != null ? origReq.frameworkIds() : null);

        PolicyDocument doc = new PolicyDocument();
        doc.setPolicyCode(req.policyCode().trim());
        doc.setVersion(!isBlank(req.version()) ? req.version().trim() : "1.0");
        doc.setTitleEn(content.titleEn().trim());
        doc.setTitleAr(content.titleAr().trim());
        doc.setSummaryEn(blankToNull(content.summaryEn()));
        doc.setSummaryAr(blankToNull(content.summaryAr()));
        doc.setContentMd(combinedBilingualMarkdown);
        doc.setCategory(draft.getCategory());
        doc.setApplicabilityScope(firstNonBlank(
            req.applicabilityScope(),
            origReq != null ? origReq.applicabilityScope() : null
        ));
        doc.setOwnerName(blankToNull(req.ownerName()));
        doc.setOwnerEmail(blankToNull(req.ownerEmail()));
        doc.setReviewCadence(firstNonBlank(req.reviewCadence(), content.suggestedReviewCadence()));
        if (!validFrameworks.isEmpty()) {
            String fwCodes = validFrameworks.stream()
                .map(Framework::getCode)
                .collect(Collectors.joining(", "));
            doc.setRelatedFramework(fwCodes.length() > 120 ? fwCodes.substring(0, 120) : fwCodes);
        }
        doc.setNotes("Promoted from AI Policy Draft " + draft.getExternalId());

        PolicyDocument created = policyManagementService.create(doc, actor);

        if (!validControlIds.isEmpty()) {
            policyManagementService.setControlLinks(created.getId(), validControlIds);
        }

        if (!validFrameworks.isEmpty()) {
            List<PolicyDocumentFrameworkMapping> mappings = new ArrayList<>(validFrameworks.size());
            for (Framework fw : validFrameworks) {
                PolicyDocumentFrameworkMapping m = new PolicyDocumentFrameworkMapping();
                m.setOrganisationId(orgId);
                m.setDocumentId(created.getId());
                m.setFrameworkId(fw.getId());
                m.setSatisfaction("PARTIAL");
                m.setNotes("Linked on AI policy draft acceptance (" + draft.getExternalId() + ")");
                m.setCreatedBy(actor);
                mappings.add(m);
            }
            frameworkMappingRepo.saveAll(mappings);
        }

        draft.setStatus(PolicyAiDraft.STATUS.require("ACCEPTED"));
        draft.setAcceptedPolicyId(created.getId());
        draft.setReviewedBy(actor);
        draft.setReviewedAt(LocalDateTime.now());
        if (req.reviewerNote() != null) {
            draft.setReviewerNote(blankToNull(req.reviewerNote()));
        }
        PolicyAiDraft saved = draftRepo.save(draft);

        audit.log("POLICY_AI_DRAFT_ACCEPTED", "PolicyAiDraft", saved.getId(),
            "Accepted AI policy draft " + saved.getExternalId() + " -> PolicyDocument #"
                + created.getId() + " (" + created.getPolicyCode() + " v" + created.getVersion() + ")");

        PolicyDraftResponse resp = toResponseStatic(saved, objectMapper);
        broadcastDraftChange(orgId, resp);
        return resp;
    }

    @Transactional
    public PolicyDraftResponse rejectDraft(Long id, RejectPolicyDraftRequest req) {
        Long orgId = CurrentActor.organisationId();
        String actor = CurrentActor.requireUsername("rejecting an AI policy draft");
        PolicyAiDraft draft = draftRepo.findByIdAndOrganisationId(id, orgId)
            .orElseThrow(() -> new EntityNotFoundException("AI policy draft not found: " + id));
        if (!"GENERATED".equals(draft.getStatus()) && !"FAILED".equals(draft.getStatus())) {
            throw new IllegalStateException(
                "Only GENERATED or FAILED drafts can be rejected (current status: " + draft.getStatus() + ")");
        }

        draft.setStatus(PolicyAiDraft.STATUS.require("REJECTED"));
        draft.setReviewedBy(actor);
        draft.setReviewedAt(LocalDateTime.now());
        draft.setReviewerNote(req.reviewerNote().trim());
        PolicyAiDraft saved = draftRepo.save(draft);

        audit.log("POLICY_AI_DRAFT_REJECTED", "PolicyAiDraft", saved.getId(),
            "Rejected AI policy draft " + saved.getExternalId() + " by " + actor
                + ": " + saved.getReviewerNote());

        PolicyDraftResponse resp = toResponseStatic(saved, objectMapper);
        broadcastDraftChange(orgId, resp);
        return resp;
    }

    static String composeBilingualMarkdown(
        String titleEn,
        String contentMdEn,
        String titleAr,
        String contentMdAr
    ) {
        return "# " + titleEn + "\n\n"
            + contentMdEn + "\n\n"
            + "---\n\n"
            + "# " + titleAr + "\n\n"
            + contentMdAr;
    }

    static PolicyDraftResponse toResponseStatic(PolicyAiDraft d, ObjectMapper mapper) {
        CreatePolicyDraftRequest req = parseRequestSilently(d.getRequestJson(), mapper);
        GeneratedPolicyContent gen = parseContentSilently(d.getOutputJson(), mapper);
        GeneratedPolicyContent ed = parseContentSilently(d.getEditedOutputJson(), mapper);
        List<Long> hallucinated = parseLongListSilently(d.getHallucinatedCitations(), mapper);
        return new PolicyDraftResponse(
            d.getId(),
            d.getExternalId(),
            d.getStatus(),
            d.getPolicyType(),
            d.getCategory(),
            d.getTone(),
            req,
            gen,
            ed,
            hallucinated,
            d.getPromptTemplateId(),
            d.getPromptTemplateVersion(),
            d.getProvider(),
            d.getModel(),
            d.getInputTokens() == null ? 0 : d.getInputTokens(),
            d.getOutputTokens() == null ? 0 : d.getOutputTokens(),
            d.getLatencyMs() == null ? 0L : d.getLatencyMs(),
            d.getFailureReason(),
            d.getAcceptedPolicyId(),
            d.getRequestedBy(),
            d.getReviewedBy(),
            d.getReviewedAt(),
            d.getReviewerNote(),
            d.getCreatedAt(),
            d.getUpdatedAt()
        );
    }

    private List<Long> resolveTenantControlIds(Long orgId, List<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        Set<Long> matched = new LinkedHashSet<>();
        for (Control c : controlRepo.findByOrganisationIdAndIdIn(orgId, rawIds)) {
            matched.add(c.getId());
        }
        List<Long> ordered = new ArrayList<>();
        for (Long id : rawIds) {
            if (id != null && matched.contains(id) && !ordered.contains(id)) {
                ordered.add(id);
            }
        }
        return ordered;
    }

    private List<Framework> resolveActiveFrameworks(List<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        return frameworkRepo.findAllById(rawIds).stream()
            .filter(f -> Boolean.TRUE.equals(f.getIsActive()))
            .toList();
    }

    private void broadcastDraftChange(Long orgId, PolicyDraftResponse resp) {
        if (broadcaster != null) {
            broadcaster.broadcastEntityUpdate(orgId, "policy_ai_draft", resp.externalId(), resp);
            broadcaster.broadcastOrgEvent(orgId, "policy.ai_draft", Map.of(
                "draftId", resp.id(),
                "externalId", resp.externalId(),
                "status", resp.status()
            ));
        }
    }

    private static CreatePolicyDraftRequest parseRequestSilently(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, CreatePolicyDraftRequest.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static GeneratedPolicyContent parseContentSilently(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, GeneratedPolicyContent.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<Long> parseLongListSilently(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, LONG_LIST_TYPE);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
