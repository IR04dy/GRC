package com.tss.grc.module.privacy.extensions.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.NoticeArchiveRequest;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.NoticeCreate;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.NoticeResponse;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.NoticeUpdate;
import com.tss.grc.module.privacy.extensions.service.PrivacyNoticeService;
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

import java.util.List;

@RestController
@RequestMapping("/api/privacy/notices")
@RequiredArgsConstructor
@RequireModule("PDPL")
@Tag(name = "PDPL — Privacy notices")
@SecurityRequirement(name = "bearerAuth")
public class PrivacyNoticeController {

    private final PrivacyNoticeService service;

    @GetMapping
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public Page<NoticeResponse> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return service.list(PageRequest.of(page, Math.min(size, 200))).map(NoticeResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public NoticeResponse get(@PathVariable Long id) {
        return NoticeResponse.from(service.get(id));
    }

    @GetMapping("/by-code/{code}")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public List<NoticeResponse> history(@PathVariable String code) {
        return service.historyForCode(code).stream().map(NoticeResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public ResponseEntity<NoticeResponse> create(@RequestBody @Valid NoticeCreate body, Authentication auth) {
        return ResponseEntity.ok(NoticeResponse.from(service.create(body, userId(auth))));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public NoticeResponse update(@PathVariable Long id, @RequestBody @Valid NoticeUpdate body) {
        return NoticeResponse.from(service.update(id, body));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public NoticeResponse publish(@PathVariable Long id, Authentication auth) {
        return NoticeResponse.from(service.publish(id, userId(auth)));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public NoticeResponse archive(@PathVariable Long id, @RequestBody(required = false) NoticeArchiveRequest body, Authentication auth) {
        return NoticeResponse.from(service.archive(id, body, userId(auth)));
    }

    @PostMapping("/{id}/supersede")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public NoticeResponse supersede(@PathVariable Long id, Authentication auth) {
        return NoticeResponse.from(service.supersede(id, userId(auth)));
    }

    private Long userId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails ud) return ud.getUserId();
        return null;
    }
}
