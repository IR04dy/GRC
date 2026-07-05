package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.DetectionRule;
import com.tss.grc.module.socops.service.DetectionRuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * SOC-E — the SOC's detection-rule / use-case library (MITRE ATT&amp;CK-mapped). Read access
 * mirrors the threat-intel surface (SOC analysts, CISO, admin, auditor); authoring is limited to
 * the analyst/CISO/admin (and the TI-ingest service role, which curates content).
 */
@RestController
@RequestMapping("/api/v1/socops/detections")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Detection Library")
public class DetectionRuleController {

    private final DetectionRuleService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public Page<DetectionRule> list(@RequestParam(required = false) String technique,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return svc.list(technique, PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public DetectionRule get(@PathVariable Long id) { return svc.get(id); }

    @PostMapping
    @PreAuthorize("hasAnyRole('TI_INGEST','SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public DetectionRule create(@RequestBody DetectionRule body) {
        body.setId(null);
        return svc.save(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public DetectionRule update(@PathVariable Long id, @RequestBody DetectionRule body) {
        body.setId(id);
        return svc.save(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id); return ResponseEntity.noContent().build();
    }
}
