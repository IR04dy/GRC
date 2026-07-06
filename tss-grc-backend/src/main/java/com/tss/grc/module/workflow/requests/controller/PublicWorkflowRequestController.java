package com.tss.grc.module.workflow.requests.controller;

import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.ActionRequest;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.PublicRequestView;
import com.tss.grc.module.workflow.requests.service.WorkflowRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * WF-7 — the PUBLIC (token-credentialed) surface for EXTERNAL workflow-request recipients.
 *
 * <p>An EXTERNAL recipient (e.g. an outside auditor or vendor contact) has no platform login;
 * on send it is minted an opaque access token which is the ONLY credential here. Mirrors the
 * intake / whistleblower / TPRM BCP-attestation by-token pattern:
 * <ul>
 *   <li>{@code GET  /api/public/workflow-requests/{token}} — the MINIMAL projection (§7 #18):
 *       the request's own facts + this recipient's own status; no internal ids, no other
 *       recipients, no timeline.</li>
 *   <li>{@code POST /api/public/workflow-requests/{token}/respond} — record the external
 *       acknowledgement/response; feeds the normal routing (incl. WF-6 branch rules).</li>
 * </ul>
 *
 * <p>{@code permitAll} in {@link com.tss.grc.config.SecurityConfig}; the path prefix is
 * throttled by {@link com.tss.grc.security.PublicEndpointRateLimitFilter}. Wrong token → 404,
 * expired → 410, already-answered / closed / not-this-step → 409 (all via
 * {@code ResponseStatusException} in the service).
 */
@RestController
@RequestMapping("/api/public/workflow-requests")
@RequiredArgsConstructor
@Tag(name = "Workflow Engine — Public (external recipient tracker)")
public class PublicWorkflowRequestController {

    private final WorkflowRequestService svc;

    @GetMapping("/{token}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "External recipient's minimal view of the request they were sent, by token.")
    public PublicRequestView view(@PathVariable String token) {
        return svc.publicView(token);
    }

    @PostMapping("/{token}/respond")
    @PreAuthorize("permitAll()")
    @Operation(summary = "External recipient acknowledges/responds via their token (feeds normal routing).")
    public PublicRequestView respond(@PathVariable String token,
                                     @RequestBody(required = false) ActionRequest body) {
        return svc.publicRespondByToken(token, body == null ? null : body.note(),
                                                body == null ? null : body.outcome());
    }
}
