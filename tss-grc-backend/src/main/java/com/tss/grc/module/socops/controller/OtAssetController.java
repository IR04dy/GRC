package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.OtAsset;
import com.tss.grc.module.socops.service.OtAssetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/socops/ot-assets")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — OT/ICS Assets")
public class OtAssetController {

    private final OtAssetService svc;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CISO','OT_LEAD','SECURITY_ANALYST','PLATFORM_ADMIN','AUDITOR')")
    public Page<OtAsset> list(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CISO','OT_LEAD','SECURITY_ANALYST','PLATFORM_ADMIN','AUDITOR')")
    public OtAsset get(@PathVariable Long id) { return svc.get(id); }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('CISO','OT_LEAD','PLATFORM_ADMIN')")
    public OtAsset create(@RequestBody OtAsset body) { return svc.save(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CISO','OT_LEAD','PLATFORM_ADMIN')")
    public OtAsset update(@PathVariable Long id, @RequestBody OtAsset body) {
        body.setId(id);
        return svc.save(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PLATFORM_ADMIN','CISO')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id); return ResponseEntity.noContent().build();
    }
}
