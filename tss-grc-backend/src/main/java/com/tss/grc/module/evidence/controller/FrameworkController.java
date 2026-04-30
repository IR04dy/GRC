package com.tss.grc.module.evidence.controller;

import com.tss.grc.module.evidence.dto.FrameworkResponse;
import com.tss.grc.module.evidence.dto.SubControlResponse;
import com.tss.grc.module.evidence.service.FrameworkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/evidence/frameworks")
@RequiredArgsConstructor
@Tag(name = "Evidence — Frameworks")
@SecurityRequirement(name = "bearerAuth")
public class FrameworkController {

    private final FrameworkService frameworkService;

    @GetMapping
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "List all active NCA frameworks with controls and sub-controls")
    public List<FrameworkResponse> list() {
        return frameworkService.listFrameworks();
    }

    @GetMapping("/{controlId}/sub-controls")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "List sub-controls for a given control")
    public List<SubControlResponse> subControls(@PathVariable Long controlId) {
        return frameworkService.listSubControls(controlId);
    }
}
