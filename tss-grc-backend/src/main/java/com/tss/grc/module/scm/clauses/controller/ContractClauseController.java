package com.tss.grc.module.scm.clauses.controller;

import com.tss.grc.module.scm.clauses.dto.ContractClauseDtos.*;
import com.tss.grc.module.scm.clauses.service.ContractClauseService;
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

@RestController
@RequestMapping("/api/scm/contract-clauses")
@RequiredArgsConstructor
@Tag(name = "Contract Clauses")
@SecurityRequirement(name = "bearerAuth")
public class ContractClauseController {

    private final ContractClauseService service;

    @GetMapping
    @PreAuthorize("hasPermission('SCM','READ')")
    public Page<Response> list(@RequestParam(required = false) String status,
                               @RequestParam(required = false) String category,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "200") int size) {
        return service.list(status, category, PageRequest.of(page, size)).map(Response::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SCM','READ')")
    public Response get(@PathVariable Long id) {
        return Response.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest req) {
        return ResponseEntity.status(201).body(Response.from(service.create(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public Response update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return Response.from(service.update(id, req));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public Response approve(@PathVariable Long id, Authentication auth) {
        return Response.from(service.approve(id, usernameOf(auth), userIdOf(auth)));
    }

    @PostMapping("/{id}/deprecate")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public Response deprecate(@PathVariable Long id, @Valid @RequestBody DeprecateRequest req) {
        return Response.from(service.deprecate(id, req));
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
