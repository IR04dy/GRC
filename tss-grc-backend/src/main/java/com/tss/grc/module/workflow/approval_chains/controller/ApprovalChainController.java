package com.tss.grc.module.workflow.approval_chains.controller;

import java.util.Locale;
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
 *
 * <p><b>Identity rule for all three write routes (SEC-IAM-001).</b> {@code start},
 * {@code actions} and {@code withdraw} each take their acting identity from the
 * authenticated principal. No request body on this controller carries a user id.
 *
 * <p>⚠ F2-225: that used to be true of two of the three. {@code actions} and
 * {@code withdraw} each carried a {@code @NotNull @Positive Long actorUserId} the
 * service correctly ignored, and each said so in a comment; {@code start} carried
 * {@code @NotNull @Positive Long originatorUserId} and the service wrote it onto the
 * row. 🔑 <b>All three fields had the same wire shape, so nothing a caller or a
 * reader could see distinguished the honoured one from the ignored ones — and the
 * honoured one is the sole input to {@code withdraw}'s authorisation check.</b> The
 * two comments made it worse: a reader who finds the rule stated twice does not then
 * ask about the third route. All three fields are deleted.
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
        // SECURITY (SEC-IAM-001, F2-225): the originator is the authenticated
        // principal. It used to arrive in the body — and this is the route that
        // ESTABLISHES the identity the two routes below check against, so it was
        // the one that most needed the rule the two below had already documented.
        return instanceService.startChain(req, acting().userId());
    }

    @PostMapping("/{id}/actions")
    @PreAuthorize("hasPermission('WORKFLOW','APPROVE')")
    public InstanceResponse recordAction(@PathVariable Long id,
                                          @RequestBody @Valid InstanceActionRequest req) {
        // SECURITY (SEC-IAM-001): the acting identity is taken from the
        // authenticated principal, NOT from req.actorUserId() (which is ignored).
        Acting actor = acting();
        return instanceService.recordAction(id, req, actor.userId(), actor.roles());
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasPermission('WORKFLOW','APPROVE')")
    public InstanceResponse withdraw(@PathVariable Long id,
                                      @RequestBody @Valid InstanceWithdrawRequest req) {
        // SECURITY (SEC-IAM-001): only the authenticated originator may withdraw.
        return instanceService.withdraw(id, req, acting().userId());
    }

    @GetMapping("/pending-for-me")
    @PreAuthorize("isAuthenticated()")
    public List<PendingDecisionResponse> pendingForMe() {
        Acting actor = acting();
        return instanceService.pendingForUser(actor.userId(), actor.roles());
    }

    // ── Authenticated-principal helper ───────────────────────────────

    /** The acting user id + un-prefixed role names, derived from the JWT principal. */
    private record Acting(Long userId, Set<String> roles) {}

    private Acting acting() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = null;
        Set<String> roles = new HashSet<>();
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails details) {
            userId = details.getUserId();
        }
        if (auth != null) {
            // F2-450 — ROLE_-prefixed authorities ONLY, and upper-cased once here.
            //
            // ⚠⚠ This used to read
            //     roles.add(name.startsWith("ROLE_") ? name.substring(5) : name)
            // which KEPT every non-prefixed authority — and the retired expression is
            // quoted here in FULL, closing paren included, deliberately: the gate's
            // detector matches that shape, so this comment is exactly what makes its
            // comment-stripping load-bearing rather than decorative. Quoted without the
            // paren, the control that disables the stripping SURVIVED (F2-193/F2-206).
            // CustomUserDetailsService grants
            // ROLE_<roleName> for each role AND each permission name verbatim, so this set also
            // carried all 144 permission strings (ADMIN_APPROVE, AI_WRITE, BCM_EXPORT, …) — and
            // it is the input to ApprovalChainInstanceService#matchesActor, i.e. to WHO MAY SIGN
            // AN APPROVAL STEP. A step's required_role is free text a tenant admin types, and
            // permission names look exactly like the role names this platform already ships
            // (DLP_INGEST, SANCTIONS_CHECK), so a step configured to require one would have been
            // satisfied by every holder of that permission.
            //
            // KEY: three other sites strip ROLE_ and all three FILTER on the prefix first —
            // NdiAuditService#firstRole, SignatureService, and WorkflowRequestService in a SIBLING
            // SERVICE OF THIS MODULE — so the correct idiom was present three times over and the
            // one outlier was the one feeding an authorisation decision.
            //
            // ⚠ The upper-casing is the second half: matchesActor's parameter is named
            // rolesUpper and only ONE of its two callers used to supply one — pendingForUser
            // built it, recordAction passed this set raw. Normalising here means the inbox and
            // the action path can no longer disagree about a lower-case role name, where the
            // asymmetry was a step listed as pending that the same principal is then refused.
            for (GrantedAuthority a : auth.getAuthorities()) {
                String name = a.getAuthority();
                if (name != null && name.startsWith("ROLE_")) {
                    roles.add(name.substring("ROLE_".length()).toUpperCase(Locale.ROOT));
                }
            }
        }
        return new Acting(userId, roles);
    }
}
