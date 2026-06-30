package com.tss.grc.module.evidence.controller;

import com.tss.grc.module.evidence.dto.EvidenceRequestCreateDto;
import com.tss.grc.module.evidence.dto.EvidenceRequestResponse;
import com.tss.grc.module.evidence.dto.EvidenceRequestCommentDtos.CreateCommentDto;
import com.tss.grc.module.evidence.dto.EvidenceRequestCommentDtos.CommentResponse;
import com.tss.grc.module.evidence.service.EvidenceRequestService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evidence/requests")
@RequiredArgsConstructor
@Tag(name = "Evidence — Requests")
@SecurityRequirement(name = "bearerAuth")
public class EvidenceRequestController {

    private final EvidenceRequestService requestService;

    @GetMapping
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "List evidence requests with optional status filter")
    public Page<EvidenceRequestResponse> list(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return requestService.list(status, pageable);
    }

    @GetMapping("/by-entity")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "List evidence requests anchored to a generic entity (entityType + entityId)")
    public java.util.List<EvidenceRequestResponse> listByEntity(
            @RequestParam String entityType,
            @RequestParam Long entityId
    ) {
        return requestService.listByEntity(entityType, entityId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    public EvidenceRequestResponse get(@PathVariable Long id) {
        return requestService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new evidence request")
    public EvidenceRequestResponse create(@Valid @RequestBody EvidenceRequestCreateDto dto,
                                          @AuthenticationPrincipal GrcUserDetails me) {
        return requestService.create(dto, me != null ? me.getUserId() : null);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    public EvidenceRequestResponse update(@PathVariable Long id,
                                           @Valid @RequestBody EvidenceRequestCreateDto dto) {
        return requestService.update(id, dto);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @Operation(summary = "Transition request status (OPEN, IN_PROGRESS, SUBMITTED, APPROVED, REJECTED, CLOSED)")
    public EvidenceRequestResponse updateStatus(@PathVariable Long id,
                                                 @RequestParam String status) {
        return requestService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('EVIDENCE', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        requestService.delete(id);
    }

    // ── Comment thread (plan task §12.9) ──────────────────────────────────────

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "List the comment thread on an evidence request")
    public java.util.List<CommentResponse> listComments(@PathVariable Long id) {
        return requestService.listComments(id);
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a comment to an evidence request")
    public CommentResponse addComment(@PathVariable Long id,
                                      @Valid @RequestBody CreateCommentDto dto,
                                      @AuthenticationPrincipal GrcUserDetails me) {
        return requestService.addComment(id, dto.body(), me != null ? me.getUserId() : null);
    }
}
