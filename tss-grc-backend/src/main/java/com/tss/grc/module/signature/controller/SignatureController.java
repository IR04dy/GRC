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
    @Operation(summary = "List the sign-off trail for an entity")
    public List<SignatureResponse> list(@PathVariable String entityType, @PathVariable Long entityId) {
        return service.list(entityType, entityId);
    }

    @PostMapping("/{entityType}/{entityId}")
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
