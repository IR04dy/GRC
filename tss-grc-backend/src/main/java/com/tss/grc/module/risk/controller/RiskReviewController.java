package com.tss.grc.module.risk.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.risk.dto.RiskReviewDtos.OverdueRow;
import com.tss.grc.module.risk.dto.RiskReviewDtos.ReassessDto;
import com.tss.grc.module.risk.dto.RiskReviewDtos.ReviewResponse;
import com.tss.grc.module.risk.dto.RiskReviewDtos.ReviewSweep;
import com.tss.grc.module.risk.service.RiskReviewService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * ERM epic E-J — risk review / reassessment REST surface.
 *
 * <pre>
 *   POST /api/v1/risk/risks/{id}/reassess        re-score / confirm + reset the review clock
 *   GET  /api/v1/risk/risks/{id}/reviews         per-risk review history
 *   GET  /api/v1/risk/reviews/overdue            risks past their scheduled review date
 *   POST /api/v1/risk/reviews/run-overdue-sweep  flag overdue risks + emit F8 (on-demand)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@RequireModule("RISK")
@Tag(name = "Risk Management — Review & Reassessment")
@SecurityRequirement(name = "bearerAuth")
public class RiskReviewController {

    private final RiskReviewService service;

    @PostMapping("/risks/{id}/reassess")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    @Operation(summary = "Reassess a risk — optionally re-score residual L/I, reset the review clock, log the review")
    public ReviewResponse reassess(@PathVariable Long id,
                                   @RequestBody(required = false) ReassessDto dto,
                                   @AuthenticationPrincipal GrcUserDetails me) {
        return ReviewResponse.from(service.reassess(id, dto,
                me != null ? me.getUserId() : null,
                me != null ? me.getUsername() : null));
    }

    @GetMapping("/risks/{id}/reviews")
    @PreAuthorize("hasPermission('RISK', 'READ')")
    public List<ReviewResponse> history(@PathVariable Long id) {
        return service.listReviews(id).stream().map(ReviewResponse::from).toList();
    }

    @GetMapping("/reviews/overdue")
    @PreAuthorize("hasPermission('RISK', 'READ')")
    @Operation(summary = "Active risks past their scheduled review date (worst-overdue first)")
    public List<OverdueRow> overdue() {
        LocalDate today = LocalDate.now();
        return service.listOverdue().stream().map(r -> OverdueRow.from(r, today)).toList();
    }

    @PostMapping("/reviews/run-overdue-sweep")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    @Operation(summary = "Flag this tenant's overdue risks as OVERDUE and emit RISK_REVIEW_OVERDUE (F8)")
    public ReviewSweep runSweep() {
        return new ReviewSweep(service.runOverdueSweepForTenant());
    }
}
