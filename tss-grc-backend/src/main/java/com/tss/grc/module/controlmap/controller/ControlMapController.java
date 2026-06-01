package com.tss.grc.module.controlmap.controller;

import com.tss.grc.module.controlmap.dto.ControlLinkResponse;
import com.tss.grc.module.controlmap.dto.ControlLinkSetRequest;
import com.tss.grc.module.controlmap.service.ControlMapService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Generic, optional, manual control mapping — any entity maps to many
 * sub-controls across frameworks. GET returns the current mappings; PUT replaces
 * the whole set (user-maintained; never auto-derived).
 */
@RestController
@RequestMapping("/api/v1/control-links")
@RequiredArgsConstructor
@Tag(name = "Control Mapping")
@SecurityRequirement(name = "bearerAuth")
public class ControlMapController {

    private final ControlMapService service;

    @GetMapping("/{entityType}/{entityId}")
    @Operation(summary = "List the sub-controls an entity maps to")
    public List<ControlLinkResponse> get(@PathVariable String entityType, @PathVariable Long entityId) {
        return service.getMappings(entityType, entityId);
    }

    @PutMapping("/{entityType}/{entityId}")
    @Operation(summary = "Replace the entity's control mappings with the supplied set")
    public List<ControlLinkResponse> set(@PathVariable String entityType,
                                         @PathVariable Long entityId,
                                         @RequestBody ControlLinkSetRequest request,
                                         @AuthenticationPrincipal GrcUserDetails me) {
        return service.setMappings(entityType, entityId, request.subControlIds(),
            me != null ? me.getUserId() : null);
    }
}
