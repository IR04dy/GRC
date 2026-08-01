package com.tss.grc.module.outsourcing.controller;

import com.tss.grc.module.outsourcing.dto.OutsourcingDtos.*;
import com.tss.grc.module.outsourcing.service.OutsourcingService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REST surface for Sprint 30 Outsourcing &amp; Cloud Governance. */
@RestController("outsourcingCloudController")
@RequestMapping("/api/v1/outsourcing")
@RequiredArgsConstructor
@RequireModule("TPRM")
public class OutsourcingController {

    private static final String READ_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER','AUDITOR','CISO','CIO','CRO')";
    private static final String WRITE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER','CIO')";
    private static final String APPROVE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','CRO','CIO')";

    private final OutsourcingService svc;

    // -------------------------------------------------------- Arrangement

    @GetMapping("/arrangements")
    @PreAuthorize(READ_GUARD)
    public List<ArrangementResponse> list() { return svc.listArrangements(); }

    @GetMapping("/arrangements/{id}")
    @PreAuthorize(READ_GUARD)
    public ArrangementResponse get(@PathVariable Long id) { return svc.getArrangement(id); }

    @PostMapping("/arrangements")
    @PreAuthorize(WRITE_GUARD)
    public ResponseEntity<ArrangementResponse> create(@Valid @RequestBody CreateArrangementRequest req) {
        return ResponseEntity.ok(svc.createArrangement(req));
    }

    @PostMapping("/arrangements/{id}/transition")
    @PreAuthorize(APPROVE_GUARD)
    public ArrangementResponse transition(@PathVariable Long id, @Valid @RequestBody TransitionRequest req) {
        return svc.transitionArrangement(id, req);
    }

    @GetMapping("/arrangements/{id}/timeline")
    @PreAuthorize(READ_GUARD)
    public List<EventResponse> timeline(@PathVariable Long id) { return svc.arrangementTimeline(id); }

    // ---------------------------------------------------------- Cloud Svc

    @GetMapping("/cloud-services")
    @PreAuthorize(READ_GUARD)
    public List<CloudServiceResponse> listClouds(
            @RequestParam(required = false) Long arrangementId) {
        return svc.listCloudServices(arrangementId);
    }

    @PostMapping("/cloud-services")
    @PreAuthorize(WRITE_GUARD)
    public ResponseEntity<CloudServiceResponse> createCloud(
            @Valid @RequestBody CreateCloudServiceRequest req) {
        return ResponseEntity.ok(svc.createCloudService(req));
    }

    @PostMapping("/cloud-services/{id}/classify")
    @PreAuthorize(WRITE_GUARD)
    public CloudServiceResponse classify(
            @PathVariable Long id,
            @Valid @RequestBody ClassifyCloudServiceRequest req) {
        return svc.classifyCloudService(id, req);
    }

    @PostMapping("/cloud-services/{id}/transition")
    @PreAuthorize(APPROVE_GUARD)
    public CloudServiceResponse transitionCloud(
            @PathVariable Long id,
            @Valid @RequestBody CloudServiceTransitionRequest req) {
        return svc.transitionCloudService(id, req);
    }

    @GetMapping("/cloud-services/{id}/timeline")
    @PreAuthorize(READ_GUARD)
    public List<EventResponse> cloudTimeline(@PathVariable Long id) {
        return svc.cloudServiceTimeline(id);
    }

    // ------------------------------------------------------------ Reviews

    @GetMapping("/arrangements/{id}/reviews")
    @PreAuthorize(READ_GUARD)
    public List<ReviewResponse> reviewsFor(@PathVariable Long id) {
        return svc.listReviewsByArrangement(id);
    }

    @PostMapping("/reviews")
    @PreAuthorize(WRITE_GUARD)
    public ResponseEntity<ReviewResponse> createReview(@Valid @RequestBody CreateReviewRequest req) {
        return ResponseEntity.ok(svc.createReview(req));
    }

    @PostMapping("/reviews/{id}/transition")
    @PreAuthorize(WRITE_GUARD)
    public ReviewResponse transitionReview(
            @PathVariable Long id,
            @Valid @RequestBody TransitionReviewRequest req) {
        return svc.transitionReview(id, req);
    }

    // ---------------------------------------------------------- Exit plan

    @GetMapping("/arrangements/{id}/exit-plans")
    @PreAuthorize(READ_GUARD)
    public List<ExitPlanResponse> exitPlansFor(@PathVariable Long id) {
        return svc.listExitPlansByArrangement(id);
    }

    @PostMapping("/exit-plans")
    @PreAuthorize(WRITE_GUARD)
    public ResponseEntity<ExitPlanResponse> createExitPlan(@Valid @RequestBody CreateExitPlanRequest req) {
        return ResponseEntity.ok(svc.createExitPlan(req));
    }

    @PostMapping("/exit-plans/{id}/transition")
    @PreAuthorize(APPROVE_GUARD)
    public ExitPlanResponse transitionExitPlan(
            @PathVariable Long id,
            @Valid @RequestBody ExitPlanTransitionRequest req) {
        return svc.transitionExitPlan(id, req);
    }

    // ---------------------------------------------------------- Dashboard

    @GetMapping("/dashboard")
    @PreAuthorize(READ_GUARD)
    public DashboardResponse dashboard() { return svc.dashboard(); }
}
