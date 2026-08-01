package com.tss.grc.module.privacy.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.privacy.service.PdplDataFlowService;
import com.tss.grc.module.privacy.service.PdplDataFlowService.DataFlowGraph;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDPL data-flow graph — Sprint-8 Slice 8-C.
 *
 * <pre>
 *   GET /api/v1/privacy/data-flow-graph
 * </pre>
 *
 * <p>Returns a node-link graph aggregating every {@code processing_activities}
 * row into the SDAIA PDPL "data flow" picture the DPO needs for inspections:
 * data category → activity → country + transfer mechanism, with Saudi-native
 * bilingual labels carried through.
 */
@RestController
@RequestMapping("/api/v1/privacy/data-flow-graph")
@RequiredArgsConstructor
@RequireModule("PDPL")
@Tag(name = "Privacy — PDPL Data Flow")
public class PdplDataFlowController {

    private final PdplDataFlowService service;

    @GetMapping
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public DataFlowGraph graph() {
        return service.buildGraph();
    }
}
