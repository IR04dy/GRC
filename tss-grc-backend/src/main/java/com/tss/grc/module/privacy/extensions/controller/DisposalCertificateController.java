package com.tss.grc.module.privacy.extensions.controller;

import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.DisposalCompleteRequest;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.DisposalCreate;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.DisposalResponse;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.DisposalVerifyRequest;
import com.tss.grc.module.privacy.extensions.service.DisposalCertificateService;
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
@RequestMapping("/api/privacy/disposal-certificates")
@RequiredArgsConstructor
@Tag(name = "PDPL — Disposal certificate")
@SecurityRequirement(name = "bearerAuth")
public class DisposalCertificateController {

    private final DisposalCertificateService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<DisposalResponse> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return service.list(PageRequest.of(page, size)).map(DisposalResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public DisposalResponse get(@PathVariable Long id) {
        return DisposalResponse.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DisposalResponse> create(@RequestBody @Valid DisposalCreate body, Authentication auth) {
        return ResponseEntity.ok(DisposalResponse.from(service.create(body, userId(auth))));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("isAuthenticated()")
    public DisposalResponse start(@PathVariable Long id, Authentication auth) {
        return DisposalResponse.from(service.start(id, userId(auth)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("isAuthenticated()")
    public DisposalResponse complete(@PathVariable Long id, @RequestBody @Valid DisposalCompleteRequest body, Authentication auth) {
        return DisposalResponse.from(service.complete(id, body, userId(auth)));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("isAuthenticated()")
    public DisposalResponse verify(@PathVariable Long id, @RequestBody @Valid DisposalVerifyRequest body, Authentication auth) {
        return DisposalResponse.from(service.verify(id, body, userId(auth)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public DisposalResponse cancel(@PathVariable Long id) {
        return DisposalResponse.from(service.cancel(id));
    }

    private Long userId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails ud) return ud.getUserId();
        return null;
    }
}
