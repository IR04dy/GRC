package com.tss.grc.module.ethics.controller;

import com.tss.grc.module.ethics.entity.KycSubject;
import com.tss.grc.module.ethics.entity.ScreeningHit;
import com.tss.grc.module.ethics.service.KycService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ethics/kyc")
@RequiredArgsConstructor
@RequireModule("ETHICS")
@Tag(name = "Ethics & Compliance — Sanctions / PEP / CDD / EDD")
@SecurityRequirement(name = "bearerAuth")
public class KycController {

    private final KycService service;

    @GetMapping
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Page<KycSubject> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String risk,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.list(type, status, risk, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public KycSubject get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public KycSubject create(@RequestBody KycSubject body) { return service.create(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public KycSubject update(@PathVariable Long id, @RequestBody KycSubject body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }

    @PostMapping("/{id}/cdd-complete")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public KycSubject completeCdd(@PathVariable Long id) { return service.completeCdd(id); }

    @PostMapping("/{id}/edd-complete")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public KycSubject completeEdd(@PathVariable Long id) { return service.completeEdd(id); }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public KycSubject approve(@PathVariable Long id) { return service.approve(id); }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public KycSubject reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        return service.reject(id, reason);
    }

    @PostMapping("/{id}/screen")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public KycSubject runScreening(@PathVariable Long id) { return service.runScreening(id); }

    // ── Screening hits ──────────────────────────────────────────────────────

    @GetMapping("/{id}/hits")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<ScreeningHit> hits(@PathVariable Long id) { return service.hits(id); }

    @PostMapping("/{id}/hits")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreeningHit recordHit(@PathVariable Long id, @RequestBody ScreeningHit body) {
        return service.recordHit(id, body);
    }

    @PostMapping("/hits/{hitId}/disposition")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public ScreeningHit disposition(@PathVariable Long hitId, @RequestBody Map<String, Object> body) {
        String disp     = body.get("disposition") != null ? body.get("disposition").toString() : null;
        String notes    = body.get("notes") != null ? body.get("notes").toString() : null;
        String decidedBy= body.get("decidedBy") != null ? body.get("decidedBy").toString() : null;
        Boolean sar     = body.get("sarFiled") instanceof Boolean b ? b : null;
        String sarRef   = body.get("sarReference") != null ? body.get("sarReference").toString() : null;
        if (disp == null) throw new IllegalArgumentException("disposition is required");
        return service.disposition(hitId, disp, notes, decidedBy, sar, sarRef);
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Map<String, Object> analytics() { return service.analytics(); }
}
