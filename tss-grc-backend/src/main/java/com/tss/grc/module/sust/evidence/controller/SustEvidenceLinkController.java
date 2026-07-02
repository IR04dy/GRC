package com.tss.grc.module.sust.evidence.controller;

import com.tss.grc.module.sust.evidence.dto.SustEvidenceLinkRequest;
import com.tss.grc.module.sust.evidence.service.SustEvidenceLinkService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * S-C — sustainability evidence bridge to the generic evidence engine.
 *
 * <pre>
 *   POST /api/sustainability/evidence-links            link a real evidence item to an ESG entity
 *   GET  /api/sustainability/evidence-links?entityType=&entityId=   list linked evidence for an entity
 *   GET  /api/sustainability/evidence-links/package[?entityType=]   evidence package (files + SHA-256)
 * </pre>
 *
 * The heavy lifecycle (request/SLA/upload+hash/review/approve/version/expire)
 * lives in {@code /api/v1/evidence/*}; this surface only links + bundles.
 */
@RestController
@RequestMapping("/api/sustainability/evidence-links")
@RequiredArgsConstructor
@Tag(name = "Sustainability — Evidence Links (module/evidence bridge)")
public class SustEvidenceLinkController {

    private final SustEvidenceLinkService service;

    @PostMapping
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<Map<String, Object>> link(@Valid @RequestBody SustEvidenceLinkRequest req) {
        return ResponseEntity.status(201).body(
            service.link(req.entityType(), req.entityId(), req.evidenceItemId(), req.notes()));
    }

    @GetMapping
    @PreAuthorize("hasPermission('SUST','READ')")
    public List<Map<String, Object>> forEntity(@RequestParam String entityType, @RequestParam Long entityId) {
        return service.listForEntity(entityType, entityId);
    }

    @GetMapping("/package")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> evidencePackage(@RequestParam(required = false) String entityType) {
        return service.buildPackage(entityType);
    }
}
