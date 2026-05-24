package com.tss.grc.module.workflow.approval_chains.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.StepCreateRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.StepResponse;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.TemplateCreateRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.TemplateResponse;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.TemplateUpdateRequest;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainStep;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainTemplate;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainStepRepository;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sprint 13 — Template CRUD + set-default exclusivity service.
 *
 * <p>Templates are slowly-changing (the bank re-writes a CAPA ladder
 * once a year, after a Compliance Committee paper). Audit + tenant
 * isolation are enforced at the service layer.
 *
 * <p>{@link #setDefault(Long)} is the only operation with cross-row
 * implications: the new default has to demote any sibling defaults in
 * the same {@code appliesToModule} scope so the "default for this
 * module" lookup is unambiguous.
 */
@Service
@RequiredArgsConstructor
public class ApprovalChainTemplateService {

    private final ApprovalChainTemplateRepository templateRepo;
    private final ApprovalChainStepRepository stepRepo;
    private final AuditLogService audit;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TemplateResponse> list() {
        Long orgId = org();
        return templateRepo.findByOrganisationIdAndIsDeletedFalseOrderByTemplateCodeAsc(orgId)
            .stream().map(t -> toResponseWithSteps(t, stepRepo
                .findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(orgId, t.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listByModule(String module) {
        Long orgId = org();
        return templateRepo.findByOrganisationIdAndAppliesToModuleAndIsDeletedFalseOrderByTemplateCodeAsc(orgId, module)
            .stream().map(t -> toResponseWithSteps(t, stepRepo
                .findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(orgId, t.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(Long id) {
        ApprovalChainTemplate t = loadOrThrow(id);
        List<ApprovalChainStep> steps = stepRepo
            .findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(org(), id);
        return toResponseWithSteps(t, steps);
    }

    // ─── Writes ─────────────────────────────────────────────────────

    @Transactional
    public TemplateResponse create(TemplateCreateRequest req) {
        Long orgId = org();
        String code = (req.templateCode() != null && !req.templateCode().isBlank())
            ? req.templateCode() : nextTemplateCode();
        if (templateRepo.existsByOrganisationIdAndTemplateCodeAndIsDeletedFalse(orgId, code)) {
            throw new IllegalArgumentException("Template code already exists: " + code);
        }
        if (req.steps() == null || req.steps().isEmpty()) {
            throw new IllegalArgumentException("Template requires at least one step");
        }
        validateStepDefinitions(req.steps());

        ApprovalChainTemplate t = new ApprovalChainTemplate();
        t.setOrganisationId(orgId);
        t.setTemplateCode(code);
        t.setTitleEn(req.titleEn());
        t.setTitleAr(req.titleAr());
        t.setDescriptionEn(req.descriptionEn());
        t.setDescriptionAr(req.descriptionAr());
        t.setAppliesToWorkflowCodes(req.appliesToWorkflowCodes());
        t.setAppliesToModule(req.appliesToModule());
        t.setIsDefault(Boolean.TRUE.equals(req.isDefault()));
        ApprovalChainTemplate saved = templateRepo.save(t);

        for (StepCreateRequest s : req.steps()) {
            ApprovalChainStep st = new ApprovalChainStep();
            st.setOrganisationId(orgId);
            st.setTemplateId(saved.getId());
            st.setStepOrder(s.stepOrder());
            st.setStepType(s.stepType() != null ? s.stepType() : "SERIAL");
            st.setTitleEn(s.titleEn());
            st.setTitleAr(s.titleAr());
            st.setRequiredRole(s.requiredRole());
            st.setRequiredUserId(s.requiredUserId());
            st.setRequiredRoleInChain(s.requiredRoleInChain());
            st.setIsMandatory(s.isMandatory() == null || s.isMandatory());
            st.setCanDelegate(s.canDelegate() == null || s.canDelegate());
            st.setSlaHours(s.slaHours());
            st.setEscalationRole(s.escalationRole());
            stepRepo.save(st);
        }

        // If is_default=true, clear other defaults in the same module scope.
        if (Boolean.TRUE.equals(saved.getIsDefault()) && saved.getAppliesToModule() != null) {
            clearOtherDefaults(saved);
        }

        audit.log("CREATE", "ApprovalChainTemplate", saved.getId(),
            "Approval-chain template created: " + saved.getTemplateCode() + " (" + saved.getTitleEn() + ")");

        return toResponseWithSteps(saved, stepRepo
            .findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(orgId, saved.getId()));
    }

    @Transactional
    public TemplateResponse update(Long id, TemplateUpdateRequest req) {
        ApprovalChainTemplate t = loadOrThrow(id);
        if (req.titleEn() != null)               t.setTitleEn(req.titleEn());
        if (req.titleAr() != null)               t.setTitleAr(req.titleAr());
        if (req.descriptionEn() != null)         t.setDescriptionEn(req.descriptionEn());
        if (req.descriptionAr() != null)         t.setDescriptionAr(req.descriptionAr());
        if (req.appliesToWorkflowCodes() != null) t.setAppliesToWorkflowCodes(req.appliesToWorkflowCodes());
        if (req.appliesToModule() != null)        t.setAppliesToModule(req.appliesToModule());
        if (req.isDefault() != null)              t.setIsDefault(req.isDefault());
        t.setUpdatedAt(LocalDateTime.now());
        ApprovalChainTemplate saved = templateRepo.save(t);

        if (Boolean.TRUE.equals(saved.getIsDefault()) && saved.getAppliesToModule() != null) {
            clearOtherDefaults(saved);
        }

        audit.log("UPDATE", "ApprovalChainTemplate", saved.getId(),
            "Approval-chain template updated: " + saved.getTemplateCode());
        return toResponseWithSteps(saved, stepRepo
            .findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(org(), saved.getId()));
    }

    /**
     * Flip a template to default for its module scope. Demotes every
     * other default in the same scope so the per-scope "default
     * template" lookup is unambiguous.
     */
    @Transactional
    public TemplateResponse setDefault(Long id) {
        ApprovalChainTemplate t = loadOrThrow(id);
        if (t.getAppliesToModule() == null) {
            throw new IllegalArgumentException(
                "Cannot mark default without an appliesToModule scope (template " + t.getTemplateCode() + ")");
        }
        t.setIsDefault(true);
        t.setUpdatedAt(LocalDateTime.now());
        ApprovalChainTemplate saved = templateRepo.save(t);
        clearOtherDefaults(saved);
        audit.log("UPDATE", "ApprovalChainTemplate", saved.getId(),
            "Approval-chain template " + saved.getTemplateCode() + " marked default for module " + saved.getAppliesToModule());
        return toResponseWithSteps(saved, stepRepo
            .findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(org(), saved.getId()));
    }

    @Transactional
    public void softDelete(Long id) {
        ApprovalChainTemplate t = loadOrThrow(id);
        t.setIsDeleted(true);
        t.setDeletedAt(LocalDateTime.now());
        templateRepo.save(t);
        audit.log("DELETE", "ApprovalChainTemplate", id,
            "Approval-chain template soft-deleted: " + t.getTemplateCode());
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private ApprovalChainTemplate loadOrThrow(Long id) {
        return templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Approval-chain template not found: " + id));
    }

    private void clearOtherDefaults(ApprovalChainTemplate saved) {
        List<ApprovalChainTemplate> siblings = templateRepo
            .findByOrganisationIdAndAppliesToModuleAndIsDefaultTrueAndIsDeletedFalseAndIdNot(
                saved.getOrganisationId(), saved.getAppliesToModule(), saved.getId());
        for (ApprovalChainTemplate sibling : siblings) {
            sibling.setIsDefault(false);
            sibling.setUpdatedAt(LocalDateTime.now());
            templateRepo.save(sibling);
        }
    }

    /** Validates that every step has exactly one matcher set. */
    static void validateStepDefinitions(List<StepCreateRequest> steps) {
        for (StepCreateRequest s : steps) {
            int matchers = (s.requiredRole() != null && !s.requiredRole().isBlank() ? 1 : 0)
                         + (s.requiredUserId() != null ? 1 : 0)
                         + (s.requiredRoleInChain() != null && !s.requiredRoleInChain().isBlank() ? 1 : 0);
            if (matchers == 0) {
                throw new IllegalArgumentException(
                    "Step " + s.stepOrder() + " (" + s.titleEn() + ") needs exactly one of "
                    + "requiredRole, requiredUserId or requiredRoleInChain");
            }
            if (matchers > 1) {
                throw new IllegalArgumentException(
                    "Step " + s.stepOrder() + " (" + s.titleEn() + ") has more than one matcher set");
            }
            String type = Objects.requireNonNullElse(s.stepType(), "SERIAL");
            if (!"SERIAL".equals(type) && !"PARALLEL".equals(type)) {
                throw new IllegalArgumentException(
                    "Step " + s.stepOrder() + " has invalid step_type: " + type);
            }
        }
    }

    private static String nextTemplateCode() {
        return "CHAIN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    static TemplateResponse toResponseWithSteps(ApprovalChainTemplate t, List<ApprovalChainStep> steps) {
        List<StepResponse> stepRows = steps.stream().map(ApprovalChainTemplateService::toStepResponse).toList();
        return new TemplateResponse(
            t.getId(), t.getExternalId(), t.getOrganisationId(),
            t.getTemplateCode(), t.getTitleEn(), t.getTitleAr(),
            t.getDescriptionEn(), t.getDescriptionAr(),
            t.getAppliesToWorkflowCodes(), t.getAppliesToModule(),
            t.getIsDefault(), stepRows,
            t.getCreatedAt(), t.getUpdatedAt()
        );
    }

    static StepResponse toStepResponse(ApprovalChainStep s) {
        return new StepResponse(
            s.getId(), s.getTemplateId(), s.getStepOrder(), s.getStepType(),
            s.getTitleEn(), s.getTitleAr(),
            s.getRequiredRole(), s.getRequiredUserId(), s.getRequiredRoleInChain(),
            s.getIsMandatory(), s.getCanDelegate(),
            s.getSlaHours(), s.getEscalationRole()
        );
    }
}
