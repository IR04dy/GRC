package com.tss.grc.module.workflow.approval_chains.controller;

import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.InstanceActionRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.InstanceResponse;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.InstanceStartRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.InstanceWithdrawRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.PendingDecisionResponse;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.TemplateCreateRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.TemplateResponse;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.TemplateUpdateRequest;
import com.tss.grc.module.workflow.approval_chains.service.ApprovalChainInstanceService;
import com.tss.grc.module.workflow.approval_chains.service.ApprovalChainTemplateService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sprint 13 — REST surface for approval-chain templates + instances.
 *
 * <pre>
 *   Templates:
 *     GET    /api/v1/workflow/chains/templates
 *     GET    /api/v1/workflow/chains/templates/by-module/{module}
 *     GET    /api/v1/workflow/chains/templates/{id}
 *     POST   /api/v1/workflow/chains/templates
 *     PATCH  /api/v1/workflow/chains/templates/{id}
 *     POST   /api/v1/workflow/chains/templates/{id}/set-default
 *     DELETE /api/v1/workflow/chains/templates/{id}
 *
 *   Instances:
 *     GET    /api/v1/workflow/chains
 *     GET    /api/v1/workflow/chains/{id}
 *     POST   /api/v1/workflow/chains/start
 *     POST   /api/v1/workflow/chains/{id}/actions
 *     POST   /api/v1/workflow/chains/{id}/withdraw
 *     GET    /api/v1/workflow/chains/pending-for-me
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/workflow/chains")
@RequiredArgsConstructor
@Tag(name = "Workflow — Approval Chains (Sprint 13)")
@SecurityRequirement(name = "bearerAuth")
public class ApprovalChainController {

    private final ApprovalChainTemplateService templateService;
    private final ApprovalChainInstanceService instanceService;

    // ── Templates ────────────────────────────────────────────────────

    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','DPO')")
    public List<TemplateResponse> listTemplates() {
        return templateService.list();
    }

    @GetMapping("/templates/by-module/{module}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','DPO')")
    public List<TemplateResponse> listTemplatesByModule(@PathVariable String module) {
        return templateService.listByModule(module);
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','DPO')")
    public TemplateResponse getTemplate(@PathVariable Long id) {
        return templateService.get(id);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER')")
    public TemplateResponse createTemplate(@RequestBody @Valid TemplateCreateRequest req) {
        return templateService.create(req);
    }

    @PatchMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER')")
    public TemplateResponse updateTemplate(@PathVariable Long id,
                                            @RequestBody @Valid TemplateUpdateRequest req) {
        return templateService.update(id, req);
    }

    @PostMapping("/templates/{id}/set-default")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER')")
    public TemplateResponse setDefault(@PathVariable Long id) {
        return templateService.setDefault(id);
    }

    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER')")
    public void deleteTemplate(@PathVariable Long id) {
        templateService.softDelete(id);
    }

    // ── Instances ────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','DPO')")
    public List<InstanceResponse> listInstances() {
        return instanceService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','DPO')")
    public InstanceResponse getInstance(@PathVariable Long id) {
        return instanceService.get(id);
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','RISK_MANAGER','CISO','DPO','RISK_OWNER')")
    public InstanceResponse start(@RequestBody @Valid InstanceStartRequest req) {
        return instanceService.startChain(req);
    }

    @PostMapping("/{id}/actions")
    @PreAuthorize("isAuthenticated()")
    public InstanceResponse recordAction(@PathVariable Long id,
                                          @RequestBody @Valid InstanceActionRequest req) {
        return instanceService.recordAction(id, req);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("isAuthenticated()")
    public InstanceResponse withdraw(@PathVariable Long id,
                                      @RequestBody @Valid InstanceWithdrawRequest req) {
        return instanceService.withdraw(id, req);
    }

    @GetMapping("/pending-for-me")
    @PreAuthorize("isAuthenticated()")
    public List<PendingDecisionResponse> pendingForMe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = null;
        Set<String> roles = new HashSet<>();
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails details) {
            userId = details.getUserId();
        }
        if (auth != null) {
            for (GrantedAuthority a : auth.getAuthorities()) {
                String name = a.getAuthority();
                if (name != null) {
                    roles.add(name.startsWith("ROLE_") ? name.substring(5) : name);
                }
            }
        }
        return instanceService.pendingForUser(userId, roles);
    }
}
