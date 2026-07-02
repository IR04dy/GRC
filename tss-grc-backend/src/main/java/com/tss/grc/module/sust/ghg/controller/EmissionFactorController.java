package com.tss.grc.module.sust.ghg.controller;

import com.tss.grc.module.sust.ghg.entity.EmissionFactor;
import com.tss.grc.module.sust.ghg.service.EmissionFactorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Versioned emission-factor library — {@code /api/sustainability/ghg/factors} (S-J).
 *
 * <pre>
 *   GET  /factors            list (?activeOnly=true for the current library)
 *   GET  /factors/{id}       one factor version
 *   POST /factors            create / supersede a factor code (version bump)
 *   POST /factors/{id}/deactivate
 * </pre>
 */
@RestController
@RequestMapping("/api/sustainability/ghg/factors")
@RequiredArgsConstructor
@Tag(name = "Sustainability — GHG Emission-Factor Library")
public class EmissionFactorController {

    private final EmissionFactorService service;

    @GetMapping
    @PreAuthorize("hasPermission('SUST','READ')")
    public List<EmissionFactor> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public EmissionFactor get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<EmissionFactor> create(@RequestBody EmissionFactor req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public EmissionFactor deactivate(@PathVariable Long id) { return service.deactivate(id); }
}
