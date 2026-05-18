package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.service.BcmDependencyGraphService;
import com.tss.grc.module.bcm.cycle.service.BcmDependencyGraphService.GraphPayload;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the BCM dependency graph.
 * <pre>
 *   GET /api/v1/bcm/dependency-graph
 * </pre>
 * Returns {@link GraphPayload} ready to feed react-flow on the frontend.
 */
@RestController
@RequestMapping("/api/v1/bcm/dependency-graph")
@RequiredArgsConstructor
@Tag(name = "BCM Dependency Graph")
public class BcmDependencyGraphController {

    private final BcmDependencyGraphService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public GraphPayload graph() {
        return service.build();
    }
}
