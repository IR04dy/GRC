package com.tss.grc.module.signature.controller;

import com.tss.grc.module.signature.dto.DigitalSignatureDtos;
import com.tss.grc.module.signature.dto.DigitalSignatureDtos.CreateRequest;
import com.tss.grc.module.signature.dto.DigitalSignatureDtos.RejectRequest;
import com.tss.grc.module.signature.dto.DigitalSignatureDtos.Response;
import com.tss.grc.module.signature.dto.DigitalSignatureDtos.SignRequest;
import com.tss.grc.module.signature.service.DigitalSignatureService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for the digital signature module.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET    /api/signature/transactions                  — paginated list (org-scoped)</li>
 *   <li>GET    /api/signature/transactions/inbox            — REQUESTED rows assigned to me</li>
 *   <li>GET    /api/signature/transactions/by-entity        — every signature attached to a given entity</li>
 *   <li>GET    /api/signature/transactions/{id}             — single row</li>
 *   <li>POST   /api/signature/transactions                  — open a new request</li>
 *   <li>POST   /api/signature/transactions/{id}/sign        — signer attests</li>
 *   <li>POST   /api/signature/transactions/{id}/reject      — signer declines</li>
 *   <li>POST   /api/signature/transactions/{id}/expire      — explicit expiry (admin / sweep)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/signature/transactions")
@RequiredArgsConstructor
@Tag(name = "Digital Signature")
@SecurityRequirement(name = "bearerAuth")
public class DigitalSignatureController {

    private final DigitalSignatureService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List signature transactions (paginated, org-scoped).")
    public Page<Response> list(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size) {
        return service.list(PageRequest.of(page, size)).map(Response::from);
    }

    @GetMapping("/inbox")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Signature requests awaiting the calling user.")
    public List<Response> inbox(Authentication auth) {
        Long signerUserId = userIdOf(auth);
        return service.inboxForUser(signerUserId).stream().map(Response::from).toList();
    }

    @GetMapping("/by-entity")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "All signatures (any status) for a given related entity.")
    public List<Response> byEntity(@RequestParam String entityType,
                                   @RequestParam Long entityId) {
        return service.historyForEntity(entityType, entityId).stream().map(Response::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Response get(@PathVariable Long id) { return Response.from(service.get(id)); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Open a new signature request. Status starts at REQUESTED.")
    public ResponseEntity<Response> create(@RequestBody @Valid CreateRequest body, Authentication auth) {
        Long initiatorId = userIdOf(auth);
        String initiatorName = auth.getName();
        return ResponseEntity.ok(Response.from(service.request(body, initiatorId, initiatorName)));
    }

    @PostMapping("/{id}/sign")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Sign a REQUESTED transaction. Stores hash + cert ref + signature timestamp.")
    public Response sign(@PathVariable Long id, @RequestBody @Valid SignRequest body, Authentication auth) {
        // SEC-IAM-003: bind the signing act to the authenticated principal so only
        // the designated signer can complete the signature.
        return Response.from(service.sign(id, body, userIdOf(auth)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Reject a REQUESTED transaction. Rejection reason is mandatory.")
    public Response reject(@PathVariable Long id, @RequestBody @Valid RejectRequest body, Authentication auth) {
        // SEC-IAM-003: only the designated signer may decline the request.
        return Response.from(service.reject(id, body, userIdOf(auth)));
    }

    @PostMapping("/{id}/expire")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Mark a REQUESTED transaction as EXPIRED (admin override or scheduled sweep).")
    public Response expire(@PathVariable Long id) {
        return Response.from(service.expire(id));
    }

    private Long userIdOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails ud) {
            return ud.getUserId();
        }
        return null;
    }
}
