package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.CryptoAsset;
import com.tss.grc.module.socops.service.CryptoAssetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/socops/crypto-assets")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Cryptographic Inventory")
public class CryptoAssetController {

    private final CryptoAssetService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('CISO','SECURITY_ANALYST','PLATFORM_ADMIN','AUDITOR')")
    public Page<CryptoAsset> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('CISO','SECURITY_ANALYST','PLATFORM_ADMIN','AUDITOR')")
    public List<CryptoAsset> expiring(@RequestParam(defaultValue = "30") int days) {
        return svc.expiringWithinDays(days);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CISO','SECURITY_ANALYST','PLATFORM_ADMIN','AUDITOR')")
    public CryptoAsset get(@PathVariable Long id) { return svc.get(id); }

    @PostMapping
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN')")
    public CryptoAsset save(@RequestBody CryptoAsset body) { return svc.save(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN')")
    public CryptoAsset update(@PathVariable Long id, @RequestBody CryptoAsset body) {
        body.setId(id);
        return svc.save(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id); return ResponseEntity.noContent().build();
    }
}
