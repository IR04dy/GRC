package com.tss.grc.module.privacy.extensions.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.AssessRequest;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.CrossBorderCreate;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.CrossBorderResponse;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.RejectRequest;
import com.tss.grc.module.privacy.extensions.service.CrossBorderTransferService;
import com.tss.grc.security.GrcUserDetails;
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

@RestController
@RequestMapping("/api/privacy/cross-border-transfers")
@RequiredArgsConstructor
@RequireModule("PDPL")
@Tag(name = "PDPL — Cross-border transfer")
@SecurityRequirement(name = "bearerAuth")
public class CrossBorderTransferController {

    private final CrossBorderTransferService service;

    @GetMapping
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public Page<CrossBorderResponse> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        return service.list(PageRequest.of(page, Math.min(size, 200))).map(CrossBorderResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public CrossBorderResponse get(@PathVariable Long id) {
        return CrossBorderResponse.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public ResponseEntity<CrossBorderResponse> create(@RequestBody @Valid CrossBorderCreate body, Authentication auth) {
        return ResponseEntity.ok(CrossBorderResponse.from(service.create(body, userId(auth))));
    }

    @PostMapping("/{id}/assess")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public CrossBorderResponse assess(@PathVariable Long id, @RequestBody @Valid AssessRequest body) {
        return CrossBorderResponse.from(service.assess(id, body));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public CrossBorderResponse approve(@PathVariable Long id, Authentication auth) {
        return CrossBorderResponse.from(service.approve(id, userId(auth), auth.getName()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public CrossBorderResponse reject(@PathVariable Long id, @RequestBody @Valid RejectRequest body) {
        return CrossBorderResponse.from(service.reject(id, body));
    }

    @PostMapping("/{id}/expire")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public CrossBorderResponse expire(@PathVariable Long id) {
        return CrossBorderResponse.from(service.expire(id));
    }

    private Long userId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails ud) return ud.getUserId();
        return null;
    }
}
