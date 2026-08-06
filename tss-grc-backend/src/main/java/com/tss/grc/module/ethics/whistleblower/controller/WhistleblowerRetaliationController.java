package com.tss.grc.module.ethics.whistleblower.controller;

import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.RetaliationComplaintRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.RetaliationComplaintResponse;
import com.tss.grc.module.ethics.whistleblower.service.WhistleblowerRetaliationService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for the Whistleblower retaliation-protection sub-flow.
 *
 * <p>Owned by an independent protector — typically HR Director or an
 * external investigator. Read is widened to the Ethics + Compliance +
 * Internal Audit reading list; write is HR Director / Compliance
 * Officer / Platform Admin.
 */
@RestController
@RequestMapping("/api/v1/ethics/whistleblower/retaliation")
@RequiredArgsConstructor
@RequireModule("ETHICS")
@Tag(name = "Ethics — Whistleblower Retaliation Sub-flow (Sprint 13)")
@SecurityRequirement(name = "bearerAuth")
public class WhistleblowerRetaliationController {

    private static final String READ_GUARD =
            "hasAnyRole('ETHICS_OFFICER','COMPLIANCE_OFFICER','INTERNAL_AUDITOR'," +
            "'AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN')";
    private static final String WRITE_GUARD =
            "hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')";

    private final WhistleblowerRetaliationService service;

    @GetMapping
    @PreAuthorize(READ_GUARD)
    public List<RetaliationComplaintResponse> list(@RequestParam(value = "caseId", required = false) Long caseId) {
        return caseId == null ? service.list() : service.listForCase(caseId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_GUARD)
    public RetaliationComplaintResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Submit a retaliation claim linked to an existing whistleblower case.")
    public ResponseEntity<RetaliationComplaintResponse> claim(@Valid @RequestBody RetaliationComplaintRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.claim(req));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize(WRITE_GUARD)
    public RetaliationComplaintResponse validate(@PathVariable Long id,
                                                   @RequestParam(required = false) Long protectorUserId,
                                                   Authentication auth) {
        return service.validate(id, protectorUserId != null ? protectorUserId : userId(auth));
    }

    @PostMapping("/{id}/uphold")
    @PreAuthorize(WRITE_GUARD)
    public RetaliationComplaintResponse uphold(@PathVariable Long id,
                                                 @RequestParam(required = false) Long protectorUserId,
                                                 Authentication auth) {
        return service.uphold(id, protectorUserId != null ? protectorUserId : userId(auth));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize(WRITE_GUARD)
    public RetaliationComplaintResponse dismiss(@PathVariable Long id,
                                                  @RequestParam(required = false) Long protectorUserId,
                                                  Authentication auth) {
        return service.dismiss(id, protectorUserId != null ? protectorUserId : userId(auth));
    }

    private static Long userId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails ud) return ud.getUserId();
        return null;
    }
}
