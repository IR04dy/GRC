package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.CrisisAar;
import com.tss.grc.module.bcm.cycle.service.CrisisAarService;
import com.tss.grc.module.bcm.cycle.service.CrisisAarService.UpdateBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tier-4 #23 — NCEMA After-Action Report API.
 *
 * <pre>
 *   GET    /api/v1/bcm/aars                                — list all AARs
 *   GET    /api/v1/bcm/aars/event/{crisisEventId}          — AARs for one crisis
 *   GET    /api/v1/bcm/aars/{id}                           — fetch one
 *   POST   /api/v1/bcm/aars/generate/{crisisEventId}       — draft from crisis data
 *   PUT    /api/v1/bcm/aars/{id}                           — edit markdown bodies
 *   POST   /api/v1/bcm/aars/{id}/transitions/{target}      — DRAFT → UNDER_REVIEW
 *                                                            → APPROVED → PUBLISHED
 *                                                            → RESCINDED
 * </pre>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bcm/aars")
@Tag(name = "NCEMA After-Action Reports")
public class CrisisAarController {

    private final CrisisAarService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CrisisAar> list() { return service.list(); }

    @GetMapping("/event/{crisisEventId}")
    @PreAuthorize("isAuthenticated()")
    public List<CrisisAar> listForEvent(@PathVariable Long crisisEventId) {
        return service.listForEvent(crisisEventId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CrisisAar get(@PathVariable Long id) { return service.get(id); }

    @PostMapping("/generate/{crisisEventId}")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public CrisisAar generate(@PathVariable Long crisisEventId) {
        return service.generate(crisisEventId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public CrisisAar update(@PathVariable Long id, @RequestBody UpdateBody body) {
        return service.update(id, body);
    }

    public record TransitionBody(Long digitalSignatureTxnId) {}

    @PostMapping("/{id}/transitions/{target}")
    @PreAuthorize("hasPermission('BCM','APPROVE')")
    public CrisisAar transition(@PathVariable Long id,
                                @PathVariable String target,
                                @RequestBody(required = false) TransitionBody body) {
        Long sigId = body == null ? null : body.digitalSignatureTxnId();
        return service.transition(id, target, sigId);
    }
}
