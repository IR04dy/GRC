package com.tss.grc.module.incident.rca.controller;

import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.incident.rca.dto.RcaDtos.CreateRequest;
import com.tss.grc.module.incident.rca.dto.RcaDtos.RejectRequest;
import com.tss.grc.module.incident.rca.dto.RcaDtos.Response;
import com.tss.grc.module.incident.rca.dto.RcaDtos.UpdateRequest;
import com.tss.grc.module.incident.rca.service.RootCauseAnalysisService;
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

import java.util.List;

@RestController
@RequestMapping("/api/incident/rca")
@RequiredArgsConstructor
@Tag(name = "Root Cause Analysis")
@SecurityRequirement(name = "bearerAuth")
public class RootCauseAnalysisController {

    private final RootCauseAnalysisService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List RCAs (paginated, org-scoped).")
    public Page<Response> list(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size) {
        return service.list(PageRequest.of(page, Math.min(size, 200))).map(Response::from);
    }

    @GetMapping("/by-entity")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "All RCAs attached to a given parent record.")
    public List<Response> byEntity(@RequestParam String entityType, @RequestParam Long entityId) {
        return service.forEntity(entityType, entityId).stream().map(Response::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Response get(@PathVariable Long id) { return Response.from(service.get(id)); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new RCA in DRAFT state.")
    public ResponseEntity<Response> create(@RequestBody @Valid CreateRequest body, Authentication auth) {
        return ResponseEntity.ok(Response.from(service.create(body, userId(auth))));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update fields on a DRAFT RCA. Refuses on non-DRAFT.")
    public Response update(@PathVariable Long id, @RequestBody @Valid UpdateRequest body) {
        return Response.from(service.update(id, body));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit DRAFT for review.")
    public Response submit(@PathVariable Long id, Authentication auth) {
        return Response.from(service.submit(id, userId(auth)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Approve an IN_REVIEW RCA.")
    public Response approve(@PathVariable Long id, Authentication auth) {
        return Response.from(service.approve(id, userId(auth), auth.getName()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Reject an IN_REVIEW RCA. Reason is mandatory.")
    public Response reject(@PathVariable Long id, @RequestBody @Valid RejectRequest body) {
        return Response.from(service.reject(id, body));
    }

    @PostMapping("/{id}/generate-capas")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Spawn one CAPA per numbered recommendation in the RCA. APPROVED only. "
                       + "Optional ?owner=<name> overrides the default (the RCA facilitator).")
    public List<CapaTaskDto> generateCapas(@PathVariable Long id,
                                           @RequestParam(name = "owner", required = false) String owner) {
        return service.generateCapasFromRecommendations(id, owner);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    private Long userId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails ud) return ud.getUserId();
        return null;
    }
}
