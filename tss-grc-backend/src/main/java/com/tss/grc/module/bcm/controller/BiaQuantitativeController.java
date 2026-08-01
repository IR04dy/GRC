package com.tss.grc.module.bcm.controller;

import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.BiaQuantitativeRequest;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.BiaQuantitativeResponse;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.BoardApprovalRequest;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.CfoApprovalRequest;
import com.tss.grc.module.bcm.service.QuantitativeBiaService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Slice 9-C — REST surface for the quantitative BIA.
 *
 * <pre>
 *   GET    /api/v1/bcm/bia/quantitative
 *   GET    /api/v1/bcm/bia/quantitative/{externalId}
 *   POST   /api/v1/bcm/bia/quantitative
 *   PUT    /api/v1/bcm/bia/quantitative/{externalId}
 *   POST   /api/v1/bcm/bia/quantitative/{externalId}/submit
 *   POST   /api/v1/bcm/bia/quantitative/{externalId}/bcm-review
 *   POST   /api/v1/bcm/bia/quantitative/{externalId}/cfo-approve
 *   POST   /api/v1/bcm/bia/quantitative/{externalId}/board-approve
 *   DELETE /api/v1/bcm/bia/quantitative/{externalId}
 * </pre>
 *
 * <p>Role guard uses {@code hasAnyRole} (not {@code hasAnyAuthority})
 * because {@code CustomUserDetailsService} prefixes authorities with
 * {@code ROLE_}. This is the same shape PR #174 used to fix the socops
 * controllers — keep them aligned.
 */
@RestController
@RequestMapping("/api/v1/bcm/bia/quantitative")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Quantitative BIA (Slice 9-C)")
@SecurityRequirement(name = "bearerAuth")
// Class-level grant is the READ floor — every endpoint requires at least one of
// these roles. Mutating / approval verbs are narrowed per-method below so a
// read-only assurance role (AUDITOR) and RISK_MANAGER cannot create, edit,
// approve or archive a quantitative BIA (segregation of duties — ISO 22301 §5.3).
@PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN','RISK_MANAGER','AUDITOR','CFO')")
public class BiaQuantitativeController {

    private final QuantitativeBiaService service;

    private static String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? "system" : a.getName();
    }

    @GetMapping
    public List<BiaQuantitativeResponse> list() {
        return service.list();
    }

    @GetMapping("/{externalId}")
    public BiaQuantitativeResponse get(@PathVariable UUID externalId) {
        return service.get(externalId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN')")
    public BiaQuantitativeResponse create(@RequestBody @Valid BiaQuantitativeRequest req) {
        return service.create(req);
    }

    @PutMapping("/{externalId}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN')")
    public BiaQuantitativeResponse update(@PathVariable UUID externalId,
                                          @RequestBody @Valid BiaQuantitativeRequest req) {
        return service.update(externalId, req);
    }

    @PostMapping("/{externalId}/submit")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN')")
    public BiaQuantitativeResponse submitForReview(@PathVariable UUID externalId) {
        return service.submitForReview(externalId);
    }

    @PostMapping("/{externalId}/bcm-review")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN')")
    public BiaQuantitativeResponse bcmLeadReview(@PathVariable UUID externalId) {
        return service.bcmLeadReview(externalId, actor());
    }

    @PostMapping("/{externalId}/cfo-approve")
    @PreAuthorize("hasAnyRole('CFO','PLATFORM_ADMIN')")
    public BiaQuantitativeResponse cfoApprove(@PathVariable UUID externalId,
                                              @RequestBody @Valid CfoApprovalRequest req) {
        return service.cfoApprove(externalId, req);
    }

    @PostMapping("/{externalId}/board-approve")
    @PreAuthorize("hasAnyRole('BOARD_MEMBER','PLATFORM_ADMIN')")
    public BiaQuantitativeResponse boardApprove(@PathVariable UUID externalId,
                                                @RequestBody @Valid BoardApprovalRequest req) {
        return service.boardApprove(externalId, req.minutesRef());
    }

    @DeleteMapping("/{externalId}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','PLATFORM_ADMIN')")
    public ResponseEntity<BiaQuantitativeResponse> archive(@PathVariable UUID externalId) {
        return ResponseEntity.ok(service.archive(externalId));
    }
}
