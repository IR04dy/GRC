package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.service.BcmBoardPackService;
import com.tss.grc.module.bcm.cycle.service.BcmBoardPackService.BoardPackSection;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Board Pack BCM section — Slice 7-D (Sprint-7).
 *
 * <pre>
 *   GET /api/v1/bcm/board-pack-section
 * </pre>
 *
 * <p>Returns a tenant-scoped aggregate suitable for direct embedding into the
 * quarterly board pack: top critical processes by MTPD, recent exercises, CAPA
 * snapshot, last AAR, active crises, regulator notification log, readiness
 * score headline. No schema changes — pure aggregator over existing data.
 */
@RestController
@RequestMapping("/api/v1/bcm/board-pack-section")
@RequiredArgsConstructor
@Tag(name = "BCM Board Pack")
public class BcmBoardPackController {

    private final BcmBoardPackService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public BoardPackSection generate() {
        return service.generate();
    }
}
