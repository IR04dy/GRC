package com.tss.grc.module.intake.controller;

import com.tss.grc.module.intake.dto.IntakePublicStatusDto;
import com.tss.grc.module.intake.dto.IntakeRecordDto;
import com.tss.grc.module.intake.dto.IntakeSubmissionRequest;
import com.tss.grc.module.intake.dto.TriageDecisionRequest;
import com.tss.grc.module.intake.service.IntakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * IntakeController — DD-82.
 *
 * REST surface for the intake substrate. Mirrors the IntakeRecord lifecycle
 * the React `useIntake()` hook drives:
 *
 *   POST   /api/v1/intake               Submit a new intake (auth required)
 *   GET    /api/v1/intake               Triager inbox — list for current org
 *   GET    /api/v1/intake/{id}          One record (with full event history)
 *   PUT    /api/v1/intake/{id}/triage   Triage decision (PROMOTE / REJECT / CLOSE)
 *   GET    /api/v1/intake/track/{code}  Public case-tracker lookup (no auth)
 *
 * The `track` endpoint is intentionally left open — anonymous submitters
 * paste their reference code from /portal/case-tracker and expect to see
 * their submission status without logging in. PII fields are still
 * filtered downstream when the submission was anonymous.
 */
@RestController
@RequestMapping("/api/v1/intake")
@RequiredArgsConstructor
@Tag(name = "Intake")
@SecurityRequirement(name = "bearerAuth")
public class IntakeController {

    private final IntakeService service;

    @PostMapping
    @PreAuthorize("hasPermission('INTAKE', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a new intake record")
    public IntakeRecordDto submit(@Valid @RequestBody IntakeSubmissionRequest req) {
        return service.submit(req);
    }

    @GetMapping
    @PreAuthorize("hasPermission('INTAKE', 'READ')")
    @Operation(summary = "Triager inbox")
    public Page<IntakeRecordDto> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String kind,
        @PageableDefault(size = 50, sort = "createdAt") Pageable pageable
    ) {
        return service.list(status, kind, pageable);
    }

    @PutMapping("/{id}/triage")
    @PreAuthorize("hasPermission('INTAKE', 'TRIAGE')")
    @Operation(summary = "Apply a triage decision (PROMOTE / REJECT / CLOSE)")
    public IntakeRecordDto triage(@PathVariable Long id,
                                   @Valid @RequestBody TriageDecisionRequest req,
                                   Authentication auth) {
        return service.triage(id, req, auth != null ? auth.getName() : "system");
    }

    /**
     * Public case-tracker — anonymous submitters paste their reference code
     * from /portal/case-tracker and get back their submission status. No
     * auth required because the reference code itself is the bearer token
     * for this read.
     *
     * SECURITY (SEC-FIN-001): returns ONLY a minimal, PII-free projection
     * ({@link IntakePublicStatusDto}) — never submitter identity, payload,
     * assignee, or event history.
     */
    @GetMapping("/track/{referenceCode}")
    @Operation(summary = "Public case-tracker lookup by reference code (PII-free status only)")
    public ResponseEntity<IntakePublicStatusDto> track(@PathVariable String referenceCode) {
        return service.findPublicStatusByReferenceCode(referenceCode)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
