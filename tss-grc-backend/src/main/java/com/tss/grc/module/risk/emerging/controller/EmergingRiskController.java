package com.tss.grc.module.risk.emerging.controller;

import com.tss.grc.module.risk.emerging.dto.EmergingRiskDtos.*;
import com.tss.grc.module.risk.emerging.service.EmergingRiskService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/api/risk/emerging")
@RequiredArgsConstructor
@Tag(name = "Emerging Risks")
@SecurityRequirement(name = "bearerAuth")
public class EmergingRiskController {

    private final EmergingRiskService service;

    @GetMapping
    @PreAuthorize("hasPermission('RISK','READ')")
    public Page<Response> list(@RequestParam(required = false) String status,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "100") int size) {
        // SEC-RISK-003: clamp client-supplied page size so an unbounded
        // ?size=… cannot drive a single huge fetch (CWE-770/400).
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        return service.list(status, PageRequest.of(safePage, safeSize)).map(Response::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('RISK','READ')")
    public Response get(@PathVariable Long id) {
        return Response.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest req) {
        return ResponseEntity.status(201).body(Response.from(service.create(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public Response update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return Response.from(service.update(id, req));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public Response review(@PathVariable Long id, @Valid @RequestBody ReviewRequest req, Authentication auth) {
        return Response.from(service.recordReview(id, req, userIdOf(auth)));
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public Response escalate(@PathVariable Long id, @Valid @RequestBody EscalateRequest req, Authentication auth) {
        return Response.from(service.escalate(id, req, userIdOf(auth)));
    }

    @PostMapping("/{id}/mature")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public Response mature(@PathVariable Long id) {
        return Response.from(service.mature(id));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public Response dismiss(@PathVariable Long id, @Valid @RequestBody DismissRequest req) {
        return Response.from(service.dismiss(id, req));
    }

    private static Long userIdOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }
}
