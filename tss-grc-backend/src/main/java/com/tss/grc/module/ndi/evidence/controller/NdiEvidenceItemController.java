package com.tss.grc.module.ndi.evidence.controller;

import com.tss.grc.module.ndi.evidence.entity.NdiEvidenceItem;
import com.tss.grc.module.ndi.evidence.service.NdiEvidenceItemService;
import com.tss.grc.module.ndi.evidence.service.NdiEvidenceItemService.EvidenceAggregate;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ndi/evidence")
@RequiredArgsConstructor
@Tag(name = "NDI — Evidence Repository")
public class NdiEvidenceItemController {

    // SEC-NDI-001: granular module authorization (was isAuthenticated() only).
    private static final String READ    = "hasPermission('NDI','READ')";
    private static final String WRITE   = "hasPermission('NDI','WRITE')";
    private static final String APPROVE = "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')";

    private final NdiEvidenceItemService service;

    @GetMapping
    @PreAuthorize(READ)
    public Page<NdiEvidenceItem> list(@RequestParam(required = false) Long cycleId,
                                      @RequestParam(required = false) String type,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "100") int size) {
        return service.list(cycleId, type, status, PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/aggregate")
    @PreAuthorize(READ)
    public EvidenceAggregate aggregate() { return service.aggregate(); }

    @GetMapping("/expiring-soon")
    @PreAuthorize(READ)
    public List<NdiEvidenceItem> expiringSoon(@RequestParam(defaultValue = "60") int days) {
        return service.expiringSoon(days);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public NdiEvidenceItem get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<NdiEvidenceItem> create(@RequestBody NdiEvidenceItem body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public NdiEvidenceItem update(@PathVariable Long id, @RequestBody NdiEvidenceItem body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE)
    public NdiEvidenceItem approve(@PathVariable Long id) {
        // SEC-NDI-003: reviewer resolved server-side from the authenticated
        // principal; SoD (reviewer != owner) enforced in the service.
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(APPROVE)
    public NdiEvidenceItem reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        // SEC-NDI-003: reviewer resolved server-side from the principal; only the reason comes from the body.
        return service.reject(id, body.get("reason"));
    }

    @PostMapping("/{oldId}/supersede-by/{newId}")
    @PreAuthorize(WRITE)
    public NdiEvidenceItem supersede(@PathVariable Long oldId, @PathVariable Long newId) {
        return service.supersede(oldId, newId);
    }

    @PostMapping("/check-expiry")
    @PreAuthorize(WRITE)
    public Map<String, Object> checkExpiry() {
        List<Long> flipped = service.runExpiryCheck();
        return Map.of("flippedCount", flipped.size(), "flippedIds", flipped);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
