package com.tss.grc.module.evidence.controller;

import com.tss.grc.module.evidence.dto.*;
import com.tss.grc.module.evidence.service.EvidenceItemService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/evidence/items")
@RequiredArgsConstructor
@Tag(name = "Evidence — Items")
@SecurityRequirement(name = "bearerAuth")
public class EvidenceItemController {

    private final EvidenceItemService evidenceItemService;

    @GetMapping
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "List evidence items with optional status/type filters")
    public Page<EvidenceItemResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return evidenceItemService.list(status, type, pageable);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "Get evidence dashboard statistics")
    public EvidenceDashboardStats stats() {
        return evidenceItemService.getStats();
    }

    @GetMapping("/expired")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "List expired evidence items")
    public List<EvidenceItemResponse> expired() {
        return evidenceItemService.listExpired();
    }

    @GetMapping("/by-sub-control/{subControlId}")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "List evidence items linked to a specific sub-control")
    public List<EvidenceItemResponse> bySubControl(@PathVariable Long subControlId) {
        return evidenceItemService.listBySubControl(subControlId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    public EvidenceItemResponse get(@PathVariable Long id) {
        return evidenceItemService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload / register a new evidence item")
    public EvidenceItemResponse create(@Valid @RequestBody EvidenceItemCreateRequest request) {
        return evidenceItemService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    public EvidenceItemResponse update(@PathVariable Long id,
                                        @Valid @RequestBody EvidenceItemCreateRequest request) {
        return evidenceItemService.update(id, request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @Operation(summary = "Submit evidence for review")
    public EvidenceItemResponse submit(@PathVariable Long id) {
        return evidenceItemService.submitForReview(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @Operation(summary = "Approve evidence item")
    public EvidenceItemResponse approve(@PathVariable Long id) {
        return evidenceItemService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @Operation(summary = "Reject evidence item (returns to REJECTED status)")
    public EvidenceItemResponse reject(@PathVariable Long id) {
        return evidenceItemService.reject(id);
    }

    @PostMapping("/link")
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Link evidence to a risk, audit result, or other module entity")
    public void addLink(@Valid @RequestBody EvidenceLinkRequest request) {
        evidenceItemService.addLink(request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('EVIDENCE', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        evidenceItemService.delete(id);
    }
}
