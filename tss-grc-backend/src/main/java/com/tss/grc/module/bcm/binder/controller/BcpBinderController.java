package com.tss.grc.module.bcm.binder.controller;

import com.tss.grc.module.bcm.binder.dto.BcpBinderDtos.BinderResponse;
import com.tss.grc.module.bcm.binder.service.BcpBinderService;
import com.tss.grc.module.bcm.binder.service.BcpBinderService.DownloadResult;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Slice 10-F — REST surface for the offline BCP/DRP binder PDF generator.
 *
 * <pre>
 *   POST   /api/v1/bcm/plans/{planId}/binder?language=EN|AR|BILINGUAL  — generate
 *   GET    /api/v1/bcm/plans/{planId}/binders                          — list versions
 *   GET    /api/v1/bcm/binders/{exportId}                              — detail
 *   GET    /api/v1/bcm/binders/{exportId}/download                     — bytes
 *   DELETE /api/v1/bcm/binders/{exportId}                              — archive
 * </pre>
 *
 * <p>Role guard uses {@code hasAnyRole(...)} (not {@code hasAnyAuthority})
 * because {@code CustomUserDetailsService} prefixes authorities with
 * {@code ROLE_}. Read-side is broad (AUDITOR sees binders too — they're
 * regulator-grade artefacts). Generate / archive are tighter.
 */
@RestController
@RequestMapping("/api/v1/bcm")
@RequiredArgsConstructor
@Tag(name = "BCM — BCP/DRP Binder Export (Slice 10-F)")
@SecurityRequirement(name = "bearerAuth")
public class BcpBinderController {

    private final BcpBinderService service;

    /**
     * Trigger a binder generation. Returns HTTP 202 with the freshly-created
     * binder row (status=READY when synchronous render succeeds, status=FAILED
     * otherwise) and a Location header pointing at the detail endpoint so the
     * client can re-fetch / poll.
     */
    @PostMapping("/plans/{planId}/binder")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN','RISK_MANAGER')")
    public ResponseEntity<BinderResponse> generate(
        @PathVariable Long planId,
        @RequestParam(value = "language", defaultValue = "BILINGUAL") String language
    ) {
        BinderResponse out = service.generate(planId, language);
        URI location = URI.create("/api/v1/bcm/binders/" + out.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED).location(location).body(out);
    }

    /** Newest-first list of binder versions for the given plan. */
    @GetMapping("/plans/{planId}/binders")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN','RISK_MANAGER','AUDITOR','BOARD_MEMBER','CFO')")
    public List<BinderResponse> list(@PathVariable Long planId) {
        return service.listForPlan(planId);
    }

    /** Detail (status, page count, byte size, download count) of one binder. */
    @GetMapping("/binders/{exportId}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN','RISK_MANAGER','AUDITOR','BOARD_MEMBER','CFO')")
    public BinderResponse get(@PathVariable Long exportId) {
        return service.get(exportId);
    }

    /**
     * Streams the binder bytes as an octet-stream PDF download. Increments
     * {@code download_count} and audits the access (auditors need this for
     * the offline-distribution log).
     */
    // F2-27: BCM:EXPORT AND-ed in front of the role list — the binder is a full BCP
    // extract, and a raw role-name check bypasses the permission catalogue entirely, so
    // BCM:EXPORT could never restrict it. AND narrows and never widens. 'BOARD_MEMBER' is
    // dropped: it is not a defined role in iam.roles, so it never matched. V320 grants
    // BCM:EXPORT to CISO and CFO, which this list allows but the catalogue did not — without
    // that grant the AND would have denied two roles the endpoint has always permitted.
    @GetMapping("/binders/{exportId}/download")
    @PreAuthorize("hasPermission('BCM','EXPORT') and "
        + "hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN','RISK_MANAGER','AUDITOR','CFO')")
    public ResponseEntity<byte[]> download(@PathVariable Long exportId) {
        DownloadResult r = service.download(exportId);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + r.filename() + "\"")
            .body(r.bytes());
    }

    /** Soft-delete (archive) — status flips to ARCHIVED and is_deleted=1. */
    @DeleteMapping("/binders/{exportId}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','PLATFORM_ADMIN')")
    public ResponseEntity<BinderResponse> archive(@PathVariable Long exportId) {
        return ResponseEntity.ok(service.archive(exportId));
    }
}
