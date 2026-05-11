package com.tss.grc.module.bcm.drp.controller;

import com.tss.grc.module.bcm.drp.dto.DrPlanDtos.*;
import com.tss.grc.module.bcm.drp.service.DrPlanService;
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
@RequestMapping("/api/bcm/dr-plans")
@RequiredArgsConstructor
@Tag(name = "DR Plans")
@SecurityRequirement(name = "bearerAuth")
public class DrPlanController {

    private final DrPlanService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<Response> list(@RequestParam(required = false) String status,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "100") int size) {
        return service.list(status, PageRequest.of(page, size)).map(Response::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Response get(@PathVariable Long id) {
        return Response.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest req) {
        return ResponseEntity.status(201).body(Response.from(service.create(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Response update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return Response.from(service.update(id, req));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public Response approve(@PathVariable Long id, Authentication auth) {
        return Response.from(service.approve(id, usernameOf(auth), userIdOf(auth)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public Response reject(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return Response.from(service.reject(id, req));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("isAuthenticated()")
    public Response activate(@PathVariable Long id, @Valid @RequestBody ActivateRequest req, Authentication auth) {
        return Response.from(service.activate(id, req, userIdOf(auth)));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("isAuthenticated()")
    public Response archive(@PathVariable Long id, @Valid @RequestBody ArchiveRequest req) {
        return Response.from(service.archive(id, req));
    }

    @PostMapping("/{id}/record-test")
    @PreAuthorize("isAuthenticated()")
    public Response recordTest(@PathVariable Long id, @Valid @RequestBody TestRequest req, Authentication auth) {
        return Response.from(service.recordTest(id, req, userIdOf(auth)));
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
