package com.tss.grc.module.ndi.domain.controller;

import com.tss.grc.module.ndi.domain.entity.NdiGovernanceDomain;
import com.tss.grc.module.ndi.domain.service.NdiGovernanceDomainService;
import com.tss.grc.module.ndi.domain.service.NdiGovernanceDomainService.CycleRollup;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * NDI Data Governance Domain REST endpoints — {@code /api/ndi/domains/*}.
 *
 * <pre>
 *   GET    /?activeOnly=true|false   list the catalog
 *   GET    /:id                      fetch one
 *   POST   /                         create
 *   PUT    /:id                      update
 *   DELETE /:id                      soft-delete
 *   POST   /:id/mark-official        flip placeholder → official
 *   POST   /:id/mark-placeholder     flip back to placeholder
 *   GET    /rollup?cycleId=          cross-slice rollup + overall NDI score
 * </pre>
 */
@RestController
@RequestMapping("/api/ndi/domains")
@RequiredArgsConstructor
@RequireModule("DATA_AI_GOV")
@Tag(name = "NDI — Governance Domains")
public class NdiGovernanceDomainController {

    // SEC-NDI-001: granular module authorization (was isAuthenticated() only).
    private static final String READ    = "hasPermission('NDI','READ')";
    private static final String WRITE   = "hasPermission('NDI','WRITE')";
    private static final String APPROVE = "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')";

    private final NdiGovernanceDomainService service;

    @GetMapping
    @PreAuthorize(READ)
    public List<NdiGovernanceDomain> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public NdiGovernanceDomain get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<NdiGovernanceDomain> create(@RequestBody NdiGovernanceDomain body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public NdiGovernanceDomain update(@PathVariable Long id, @RequestBody NdiGovernanceDomain body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/mark-official")
    @PreAuthorize(WRITE)
    public NdiGovernanceDomain markOfficial(@PathVariable Long id) {
        return service.markOfficial(id);
    }

    @PostMapping("/{id}/mark-placeholder")
    @PreAuthorize(WRITE)
    public NdiGovernanceDomain markPlaceholder(@PathVariable Long id) {
        return service.markPlaceholder(id);
    }

    @GetMapping("/rollup")
    @PreAuthorize(READ)
    public CycleRollup rollup(@RequestParam Long cycleId) {
        return service.rollupForCycle(cycleId);
    }
}
