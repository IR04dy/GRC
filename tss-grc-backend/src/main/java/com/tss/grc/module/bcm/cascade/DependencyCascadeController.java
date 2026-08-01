package com.tss.grc.module.bcm.cascade;

import com.tss.grc.module.bcm.cascade.DependencyCascadeService.CascadeResult;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dependency-cascade simulator — Sprint-9 Slice 9-A.
 *
 * <pre>
 *   GET /api/v1/bcm/dependency-cascade/{failedProcessId}
 * </pre>
 *
 * <p>Returns impacted downstream processes bucketed at 1h / 4h / 8h / 24h
 * time-bands, with MTPD-breach flag + confidence score (drops with cascade
 * depth). Pure compute over existing tables — no schema changes.
 */
@RestController
@RequestMapping("/api/v1/bcm/dependency-cascade")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM Dependency Cascade")
public class DependencyCascadeController {

    private final DependencyCascadeService service;

    @GetMapping("/{failedProcessId}")
    @PreAuthorize("isAuthenticated()")
    public CascadeResult simulate(@PathVariable Long failedProcessId) {
        return service.simulate(failedProcessId);
    }
}
