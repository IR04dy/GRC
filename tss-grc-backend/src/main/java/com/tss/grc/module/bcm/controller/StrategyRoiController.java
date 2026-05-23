package com.tss.grc.module.bcm.controller;

import com.tss.grc.module.bcm.dto.StrategyRoiDtos.RecommendationRequest;
import com.tss.grc.module.bcm.dto.StrategyRoiDtos.StrategyRoiComparison;
import com.tss.grc.module.bcm.dto.StrategyRoiDtos.StrategyRoiRequest;
import com.tss.grc.module.bcm.dto.StrategyRoiDtos.StrategyRoiResponse;
import com.tss.grc.module.bcm.service.StrategyRoiService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Slice 9-D — REST surface for the continuity-strategy ROI analyses.
 *
 * <pre>
 *   GET    /api/v1/bcm/roi/analyses                         — list (filterable by biaId)
 *   GET    /api/v1/bcm/roi/analyses/{externalId}            — detail
 *   POST   /api/v1/bcm/roi/analyses                         — create
 *   PUT    /api/v1/bcm/roi/analyses/{externalId}            — update (recomputes derived)
 *   POST   /api/v1/bcm/roi/analyses/{externalId}/recommend  — recommendation transition
 *   GET    /api/v1/bcm/roi/analyses/comparison?ids=2,3,4    — side-by-side
 *   DELETE /api/v1/bcm/roi/analyses/{externalId}            — archive
 * </pre>
 *
 * <p>Role guard uses {@code hasAnyRole} (not {@code hasAnyAuthority})
 * because {@code CustomUserDetailsService} prefixes authorities with
 * {@code ROLE_}. Same shape as PR #174's socops fix and the Slice 9-C
 * BIA controller.
 *
 * <p>Read-side is open to a wider audience (AUDITOR, CFO, BOARD_MEMBER)
 * because the ROI table is a board-pack artifact. Write-side stays tight
 * (BCM_MANAGER, CISO, RISK_MANAGER, PLATFORM_ADMIN). The recommendation
 * transition is tighter still — only CFO / CISO / PLATFORM_ADMIN can
 * approve / reject / defer.
 */
@RestController
@RequestMapping("/api/v1/bcm/roi/analyses")
@RequiredArgsConstructor
@Tag(name = "BCM — Continuity Strategy ROI (Slice 9-D)")
@SecurityRequirement(name = "bearerAuth")
public class StrategyRoiController {

    private final StrategyRoiService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN','CFO','BOARD_MEMBER','RISK_MANAGER')")
    public List<StrategyRoiResponse> list(@RequestParam(value = "biaId", required = false) Long biaId) {
        return biaId == null ? service.list() : service.listForBia(biaId);
    }

    @GetMapping("/comparison")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN','CFO','BOARD_MEMBER','RISK_MANAGER')")
    public StrategyRoiComparison comparison(@RequestParam("ids") List<Long> ids) {
        return service.comparison(ids);
    }

    @GetMapping("/{externalId}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN','CFO','BOARD_MEMBER','RISK_MANAGER')")
    public StrategyRoiResponse get(@PathVariable UUID externalId) {
        return service.get(externalId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','RISK_MANAGER','PLATFORM_ADMIN')")
    public StrategyRoiResponse create(@RequestBody @Valid StrategyRoiRequest req) {
        return service.create(req);
    }

    @PutMapping("/{externalId}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','RISK_MANAGER','PLATFORM_ADMIN')")
    public StrategyRoiResponse update(@PathVariable UUID externalId,
                                       @RequestBody @Valid StrategyRoiRequest req) {
        return service.update(externalId, req);
    }

    @PostMapping("/{externalId}/recommend")
    @PreAuthorize("hasAnyRole('CFO','CISO','PLATFORM_ADMIN')")
    public StrategyRoiResponse recommend(@PathVariable UUID externalId,
                                          @RequestBody @Valid RecommendationRequest req) {
        return service.recommend(externalId, req);
    }

    @DeleteMapping("/{externalId}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN')")
    public ResponseEntity<StrategyRoiResponse> archive(@PathVariable UUID externalId) {
        return ResponseEntity.ok(service.archive(externalId));
    }
}
