package com.tss.grc.module.evidence.controller;

import com.tss.grc.module.evidence.dto.EvidenceReviewRequest;
import com.tss.grc.module.evidence.dto.EvidenceReviewResponse;
import com.tss.grc.module.evidence.service.EvidenceReviewService;
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

@RestController
@RequestMapping("/api/v1/evidence/reviews")
@RequiredArgsConstructor
@Tag(name = "Evidence — Reviews")
@SecurityRequirement(name = "bearerAuth")
public class EvidenceReviewController {

    private final EvidenceReviewService reviewService;

    @GetMapping
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "List evidence reviews with optional status filter")
    public Page<EvidenceReviewResponse> list(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return reviewService.list(status, pageable);
    }

    @PostMapping
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new evidence review")
    public EvidenceReviewResponse create(@Valid @RequestBody EvidenceReviewRequest request) {
        return reviewService.create(request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @Operation(summary = "Complete a review with APPROVED, REJECTED, or NEEDS_REVISION result")
    public EvidenceReviewResponse complete(
            @PathVariable Long id,
            @RequestParam String result,
            @RequestParam(required = false, defaultValue = "") String notes
    ) {
        return reviewService.complete(id, result, notes);
    }
}
