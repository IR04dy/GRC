package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.CyberInsurance;
import com.tss.grc.module.socops.service.CyberInsuranceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/socops/cyber-insurance")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Cyber Insurance")
public class CyberInsuranceController {

    private final CyberInsuranceService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('CISO','RISK_MANAGER','PLATFORM_ADMIN','AUDITOR','FINANCE_LEAD')")
    public List<CyberInsurance> list() { return svc.list(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CISO','RISK_MANAGER','PLATFORM_ADMIN','AUDITOR','FINANCE_LEAD')")
    public CyberInsurance get(@PathVariable Long id) { return svc.get(id); }

    @GetMapping("/renewals-due")
    @PreAuthorize("hasAnyRole('CISO','RISK_MANAGER','PLATFORM_ADMIN','AUDITOR','FINANCE_LEAD')")
    public List<CyberInsurance> renewalsDue(@RequestParam(defaultValue = "90") int days) {
        return svc.renewalsDueWithinDays(days);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CISO','RISK_MANAGER','PLATFORM_ADMIN')")
    public CyberInsurance create(@RequestBody CyberInsurance body) { return svc.save(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CISO','RISK_MANAGER','PLATFORM_ADMIN')")
    public CyberInsurance update(@PathVariable Long id, @RequestBody CyberInsurance body) {
        body.setId(id);
        return svc.save(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id); return ResponseEntity.noContent().build();
    }
}
