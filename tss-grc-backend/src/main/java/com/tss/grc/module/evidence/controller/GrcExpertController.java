package com.tss.grc.module.evidence.controller;

import com.tss.grc.module.evidence.dto.ExpertFrameworkResponse;
import com.tss.grc.module.evidence.service.GrcExpertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GRC Expert / SME surface — serves the full NCA / NDI / PDPL / BCM framework
 * catalog (control → sub-control → recommended evidence) enriched with the
 * current org's live evidence-collection coverage. The frontend uses this to
 * let officers browse SME guidance and raise evidence requests (collect → review
 * → approve → expire) straight from a recommended-evidence line.
 */
@RestController
@RequestMapping("/api/v1/evidence/expert")
@RequiredArgsConstructor
@Tag(name = "Evidence — GRC Expert")
@SecurityRequirement(name = "bearerAuth")
public class GrcExpertController {

    private final GrcExpertService expertService;

    @GetMapping
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "GRC Expert / SME catalog — frameworks by family with sub-controls, "
            + "recommended evidence, and live evidence-collection status")
    public List<ExpertFrameworkResponse> catalog() {
        return expertService.listExpertCatalog();
    }
}
