package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.ThreatIntel;
import com.tss.grc.module.socops.service.ThreatIntelService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/socops/threat-intel")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Threat Intelligence")
public class ThreatIntelController {

    private final ThreatIntelService svc;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public Page<ThreatIntel> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public ThreatIntel get(@PathVariable Long id) { return svc.get(id); }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('TI_INGEST','SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public ThreatIntel save(@RequestBody ThreatIntel body) { return svc.save(body); }

    @PostMapping("/{id}/action")
    @PreAuthorize("hasAnyAuthority('SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public ThreatIntel action(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") instanceof Number n ? n.longValue() : null;
        String notes = body.get("notes") == null ? null : body.get("notes").toString();
        return svc.markActioned(id, userId, notes);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PLATFORM_ADMIN','CISO')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id); return ResponseEntity.noContent().build();
    }
}
