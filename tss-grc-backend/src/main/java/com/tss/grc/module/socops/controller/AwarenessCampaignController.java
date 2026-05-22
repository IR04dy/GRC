package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.AwarenessCampaign;
import com.tss.grc.module.socops.service.AwarenessCampaignService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/socops/awareness")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Security Awareness")
public class AwarenessCampaignController {

    private final AwarenessCampaignService svc;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CISO','SECURITY_ANALYST','PLATFORM_ADMIN','AUDITOR')")
    public Page<AwarenessCampaign> list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CISO','SECURITY_ANALYST','PLATFORM_ADMIN','AUDITOR')")
    public AwarenessCampaign get(@PathVariable Long id) { return svc.get(id); }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('CISO','PLATFORM_ADMIN')")
    public AwarenessCampaign save(@RequestBody AwarenessCampaign body) { return svc.save(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CISO','PLATFORM_ADMIN')")
    public AwarenessCampaign update(@PathVariable Long id, @RequestBody AwarenessCampaign body) {
        body.setId(id);
        return svc.save(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id); return ResponseEntity.noContent().build();
    }
}
