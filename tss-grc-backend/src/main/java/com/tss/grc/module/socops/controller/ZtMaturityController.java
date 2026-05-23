package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.ZtMaturity;
import com.tss.grc.module.socops.service.ZtMaturityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/socops/zt-maturity")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Zero Trust Maturity")
public class ZtMaturityController {

    private final ZtMaturityService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN','AUDITOR')")
    public List<ZtMaturity> list() { return svc.list(); }

    @GetMapping("/{assessmentCode}")
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN','AUDITOR')")
    public List<ZtMaturity> byAssessment(@PathVariable String assessmentCode) {
        return svc.byAssessment(assessmentCode);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN')")
    public ZtMaturity save(@RequestBody ZtMaturity body) { return svc.save(body); }
}
