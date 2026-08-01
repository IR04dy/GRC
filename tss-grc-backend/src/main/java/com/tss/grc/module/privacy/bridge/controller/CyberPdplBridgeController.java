package com.tss.grc.module.privacy.bridge.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.privacy.bridge.dto.CyberBreachBridgeLogDtos.*;
import com.tss.grc.module.privacy.bridge.service.CyberPdplBridgeService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/privacy/cyber-bridge")
@RequiredArgsConstructor
@RequireModule("PDPL")
@Tag(name = "Cyber → PDPL Bridge")
@SecurityRequirement(name = "bearerAuth")
public class CyberPdplBridgeController {

    private final CyberPdplBridgeService service;

    @GetMapping("/log")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public Page<Response> list(@RequestParam(required = false) String outcome,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "200") int size) {
        return service.list(outcome, PageRequest.of(page, Math.min(size, 200))).map(Response::from);
    }

    @GetMapping("/log/by-incident/{cyberIncidentId}")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public List<Response> byIncident(@PathVariable Long cyberIncidentId) {
        return service.forIncident(cyberIncidentId).stream().map(Response::from).toList();
    }

    @PostMapping("/trigger")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') and hasPermission('CYBER','WRITE')")
    public ResponseEntity<Response> trigger(@Valid @RequestBody ManualTriggerRequest req,
                                            Authentication auth) {
        var l = service.triggerForIncident(
                req.cyberIncidentId(),
                req.dataSubjectsAffectedEst(),
                req.dataCategoriesAffected(),
                req.piiAssessment(),
                userIdOf(auth),
                usernameOf(auth));
        return ResponseEntity.status(201).body(Response.from(l));
    }

    private static Long userIdOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }
    private static String usernameOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUsername();
        return auth != null ? auth.getName() : null;
    }
}
