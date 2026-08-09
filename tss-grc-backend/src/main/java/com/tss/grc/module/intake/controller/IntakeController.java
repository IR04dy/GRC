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

    /**
     * The front door — F2-172, and an ADR-010 §Sanctioned exceptions member.
     *
     * <p>This was {@code hasPermission('INTAKE','WRITE')}, which measured 2026-08-09 is held by
     * <b>3 of 69 roles</b>: {@code COMPLIANCE_OFFICER}, {@code GRC_ADMINISTRATOR} and
     * {@code ORG_ADMIN} — every one an administrator, and not one line-employee role among them.
     * Nothing had noticed because no product code had ever called this endpoint (F2-171): the
     * client filed submissions into {@code localStorage} instead. Wiring the client to the server
     * without this change would have turned a universally-available front door into an
     * administrators-only one, and 403'd every employee trying to:
     *
     * <ul>
     *   <li>report a security event — <b>NCA ECC 6.1</b>, ISO 27001 §6.8;</li>
     *   <li>report a privacy breach — <b>PDPL Art. 27</b>;</li>
     *   <li>notify a workplace injury — <b>GOSI, 24 hours</b>, where failure to report shifts
     *       medical-claim cost onto the employer;</li>
     *   <li>raise a concern anonymously — <b>Nazaha</b>, ISO 37001 §8.3;</li>
     *   <li>disclose a conflict of interest — the same duty {@code CoiController#submitDeclaration}
     *       is already a sanctioned member for. This endpoint subsumes it as
     *       {@code ETHICS_DISCLOSURE}, so gating the two differently would have been incoherent on
     *       its face.</li>
     * </ul>
     *
     * <p>ADR-010's three admission conditions, each discharged rather than asserted: the duty is
     * universal (read the 26 catalogue entries, not the guard); the server binds the subject
     * ({@code IntakeService#submit} takes the submitter from {@code CurrentActor} and the identity
     * fields in the body are ignored — V374 added the column, and it had to land <b>first</b>, for
     * F2-144's reason); and it is named and pinned, in {@code ControllerAuthorizationRatchetTest}'s
     * {@code SELF_SCOPED} bucket with {@code IntakeSubmitGuardTest} holding the expression.
     *
     * <p>⚠ Reading and triaging stay permissioned, and the asymmetry is the design: filing is a
     * duty, whereas reading other people's submissions — whistleblowing reports, DSRs, injury
     * notifications — is a privilege. An over-wide READ here would expose exactly the material this
     * change exists to let people report safely.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
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

    /**
     * One record with its full event history. Advertised in this class's javadoc since DD-82 and
     * never implemented until F2-172 — the client had no way to open a submission it had just
     * filed, which is part of why the client-side store survived.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('INTAKE', 'READ')")
    @Operation(summary = "One intake record with its full event history")
    public IntakeRecordDto findOne(@PathVariable Long id) {
        return service.findOne(id);
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
