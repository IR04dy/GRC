package com.tss.grc.module.signature.controller;

import com.tss.grc.module.signature.dto.SignRequest;
import com.tss.grc.module.signature.dto.SignatureResponse;
import com.tss.grc.module.signature.service.SignatureService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * In-house electronic sign-off. Any entity can collect signatures; GET lists
 * the sign-off trail, POST records a new one for the current user.
 */
@RestController
@RequestMapping("/api/v1/signatures")
@RequiredArgsConstructor
@Tag(name = "E-signature")
@SecurityRequirement(name = "bearerAuth")
public class SignatureController {

    private final SignatureService service;

    @GetMapping("/{entityType}/{entityId}")
    @PreAuthorize("hasPermission('SIGNATURE','READ')")
    @Operation(summary = "List the sign-off trail for an entity")
    public List<SignatureResponse> list(@PathVariable String entityType, @PathVariable Long entityId) {
        return service.list(entityType, entityId);
    }

    /**
     * S1/W0-5 (finding F-1): this endpoint carried NO authority check at all, so any
     * authenticated principal — including the read-only VIEWER — could record a
     * signature against any entity. The platform sells e-signed minutes as
     * tamper-evidence (ADR-019), so signing authority is now an explicit permission
     * (V313 grants SIGNATURE_APPROVE to the roles already trusted to approve
     * something). Attribution was already correct — the signer is the authenticated
     * principal, never a body field — so this closes "unauthorised but attributable".
     *
     * <p>Internal callers (e.g. CommitteeService.approveMinutes e-signing the
     * minutes) invoke SignatureService directly and are unaffected by this guard.
     */
    @PostMapping("/{entityType}/{entityId}")
    @PreAuthorize("hasPermission('SIGNATURE','APPROVE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Sign an entity (records signer, intent, statement, integrity hash)")
    public SignatureResponse sign(@PathVariable String entityType,
                                  @PathVariable Long entityId,
                                  @Valid @RequestBody SignRequest req,
                                  @AuthenticationPrincipal GrcUserDetails me) {
        return service.sign(entityType, entityId, req.intent(), req.statementEn(),
            me != null ? me.getUserId() : null);
    }
}
