package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.AppsecFinding;
import com.tss.grc.module.socops.service.AppsecFindingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/socops/appsec")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Application Security")
public class AppsecFindingController {

    private final AppsecFindingService svc;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CISO','APPSEC_LEAD','SECURITY_ANALYST','PLATFORM_ADMIN','AUDITOR')")
    public Page<AppsecFinding> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CISO','APPSEC_LEAD','SECURITY_ANALYST','PLATFORM_ADMIN','AUDITOR')")
    public AppsecFinding get(@PathVariable Long id) { return svc.get(id); }

    /** Scanner ingest endpoint — Veracode / Snyk / Checkmarx / SonarQube /
     *  GitHub AS adapters POST findings here. Idempotent on (scanner, externalId). */
    @PostMapping("/ingest")
    @PreAuthorize("hasAnyAuthority('APPSEC_INGEST','PLATFORM_ADMIN','CISO')")
    public AppsecFinding ingest(@RequestBody AppsecFinding body) {
        return svc.ingest(body);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('APPSEC_LEAD','PLATFORM_ADMIN','CISO')")
    public AppsecFinding create(@RequestBody AppsecFinding body) { return svc.save(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('APPSEC_LEAD','PLATFORM_ADMIN','CISO')")
    public AppsecFinding update(@PathVariable Long id, @RequestBody AppsecFinding body) {
        body.setId(id);
        return svc.save(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id); return ResponseEntity.noContent().build();
    }
}
