package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.MaDueDiligence;
import com.tss.grc.module.socops.service.MaDueDiligenceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/socops/ma-due-diligence")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — M&A Cyber Due Diligence")
public class MaDueDiligenceController {

    private final MaDueDiligenceService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('CISO','RISK_MANAGER','MA_LEAD','PLATFORM_ADMIN','AUDITOR','LEGAL_COUNSEL','CFO')")
    public Page<MaDueDiligence> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CISO','RISK_MANAGER','MA_LEAD','PLATFORM_ADMIN','AUDITOR','LEGAL_COUNSEL','CFO')")
    public MaDueDiligence get(@PathVariable Long id) { return svc.get(id); }

    @PostMapping
    @PreAuthorize("hasAnyRole('CISO','MA_LEAD','PLATFORM_ADMIN')")
    public MaDueDiligence create(@RequestBody MaDueDiligence body) { return svc.save(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CISO','MA_LEAD','PLATFORM_ADMIN')")
    public MaDueDiligence update(@PathVariable Long id, @RequestBody MaDueDiligence body) {
        body.setId(id);
        return svc.save(body);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('CISO','MA_LEAD','PLATFORM_ADMIN')")
    public MaDueDiligence transition(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return svc.transitionPhase(id, body.get("phase"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id); return ResponseEntity.noContent().build();
    }
}
