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

    private final NdiEvidenceItemService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<NdiEvidenceItem> list(@RequestParam(required = false) Long cycleId,
                                      @RequestParam(required = false) String type,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "100") int size) {
        return service.list(cycleId, type, status, PageRequest.of(page, size));
    }

    @GetMapping("/aggregate")
    @PreAuthorize("isAuthenticated()")
    public EvidenceAggregate aggregate() { return service.aggregate(); }

    @GetMapping("/expiring-soon")
    @PreAuthorize("isAuthenticated()")
    public List<NdiEvidenceItem> expiringSoon(@RequestParam(defaultValue = "60") int days) {
        return service.expiringSoon(days);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiEvidenceItem get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NdiEvidenceItem> create(@RequestBody NdiEvidenceItem body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiEvidenceItem update(@PathVariable Long id, @RequestBody NdiEvidenceItem body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public NdiEvidenceItem approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return service.approve(id, body == null ? null : body.get("reviewerName"));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public NdiEvidenceItem reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.reject(id, body.get("reason"), body.get("reviewerName"));
    }

    @PostMapping("/{oldId}/supersede-by/{newId}")
    @PreAuthorize("isAuthenticated()")
    public NdiEvidenceItem supersede(@PathVariable Long oldId, @PathVariable Long newId) {
        return service.supersede(oldId, newId);
    }

    @PostMapping("/check-expiry")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> checkExpiry() {
        List<Long> flipped = service.runExpiryCheck();
        return Map.of("flippedCount", flipped.size(), "flippedIds", flipped);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
