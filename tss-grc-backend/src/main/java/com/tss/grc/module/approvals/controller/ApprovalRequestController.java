package com.tss.grc.module.approvals.controller;

import com.tss.grc.module.approvals.dto.ApprovalRequestDto;
import com.tss.grc.module.approvals.service.ApprovalRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/approvals/requests")
@RequiredArgsConstructor
public class ApprovalRequestController {

    private final ApprovalRequestService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ApprovalRequestDto> list(@RequestParam(defaultValue = "false") boolean myInbox,
                                         Authentication auth) {
        return myInbox ? service.myInbox(auth.getName()) : service.listForOrg();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApprovalRequestDto get(@PathVariable String id) { return service.get(id); }

    // S2 (F-1): defence in depth. ApprovalRequestService already refuses a
    // decision from anyone but the current assignee (SEC-IAM-002, AccessDeniedException),
    // so these were service-compensated rather than wide open — but the controller
    // carried no authority check, so any authenticated principal could raise a
    // request or attempt a transition. Now gated on the WORKFLOW module.
    @PostMapping
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public ResponseEntity<ApprovalRequestDto> create(@RequestBody @Valid ApprovalRequestDto req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('WORKFLOW','APPROVE')")
    public ApprovalRequestDto transition(@PathVariable String id,
                                         @RequestBody @Valid ApprovalRequestDto.TransitionRequest req,
                                         Authentication auth) {
        // SEC-IAM-002: the deciding identity is the authenticated principal, never a
        // body field. Object-level ownership (caller == assignedTo) and the admin
        // override are enforced in the service.
        return service.transition(id, req, auth.getName(), isPlatformAdmin(auth));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    private boolean isPlatformAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if ("ROLE_PLATFORM_ADMIN".equals(ga.getAuthority())) return true;
        }
        return false;
    }
}
