package com.tss.grc.module.ethics.controller;

import com.tss.grc.module.ethics.entity.Disclosure;
import com.tss.grc.module.ethics.service.DisclosureService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ethics/disclosures")
@RequiredArgsConstructor
@Tag(name = "Ethics & Compliance — Disclosures (CoI / Gifts / Hospitality)")
@SecurityRequirement(name = "bearerAuth")
public class DisclosureController {

    private final DisclosureService service;

    @GetMapping
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Page<Disclosure> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.list(type, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Disclosure get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public Disclosure create(@RequestBody Disclosure body) { return service.create(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public Disclosure update(@PathVariable Long id, @RequestBody Disclosure body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public Disclosure transition(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String target = body.get("target") != null ? body.get("target").toString() : null;
        if (target == null) throw new IllegalArgumentException("target is required");
        return service.transition(id, target, body);
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Map<String, Object> analytics() { return service.analytics(); }
}
