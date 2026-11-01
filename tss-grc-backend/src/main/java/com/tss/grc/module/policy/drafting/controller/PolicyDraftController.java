package com.tss.grc.module.policy.drafting.controller;

import com.tss.grc.module.integrations.llm.dto.LlmStatusDto;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.AcceptPolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.CreatePolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.PolicyDraftResponse;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.RejectPolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.UpdatePolicyDraftRequest;
import com.tss.grc.module.policy.drafting.service.PolicyDraftService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for LLM-backed bilingual policy drafting and human-in-the-loop
 * acceptance into {@code policy.documents}.
 */
@RestController
@RequestMapping("/api/policy/drafts")
@RequiredArgsConstructor
@RequireModule("COMPLIANCE")
@Tag(name = "AI Policy Drafting")
public class PolicyDraftController {

    private final PolicyDraftService service;

    @GetMapping("/status")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public LlmStatusDto status() {
        return service.gatewayStatus();
    }

    @GetMapping
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<PolicyDraftResponse> list(
        @RequestParam(value = "status", required = false) String status
    ) {
        return service.listDrafts(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public PolicyDraftResponse get(@PathVariable("id") Long id) {
        return service.getDraft(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public ResponseEntity<PolicyDraftResponse> requestDraft(
        @Valid @RequestBody CreatePolicyDraftRequest body
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.requestDraft(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public PolicyDraftResponse updateDraft(
        @PathVariable("id") Long id,
        @Valid @RequestBody UpdatePolicyDraftRequest body
    ) {
        return service.updateDraft(id, body);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public PolicyDraftResponse acceptDraft(
        @PathVariable("id") Long id,
        @Valid @RequestBody AcceptPolicyDraftRequest body
    ) {
        return service.acceptDraft(id, body);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public PolicyDraftResponse rejectDraft(
        @PathVariable("id") Long id,
        @Valid @RequestBody RejectPolicyDraftRequest body
    ) {
        return service.rejectDraft(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE') or hasPermission('COMPLIANCE','WRITE')")
    public ResponseEntity<Void> deleteDraft(@PathVariable("id") Long id) {
        service.deleteDraft(id);
        return ResponseEntity.noContent().build();
    }
}
