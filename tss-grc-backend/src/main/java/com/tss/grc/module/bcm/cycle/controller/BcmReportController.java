package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.service.BcmReportService;
import com.tss.grc.module.bcm.cycle.service.BcmReportService.ReportDef;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST surface for the BCM-7 report generator.
 * <pre>
 *   GET /api/v1/bcm/reports/catalog       list all 15 report definitions
 *   GET /api/v1/bcm/reports/{code}        generate one
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm/reports")
@RequiredArgsConstructor
@Tag(name = "BCM Reports")
public class BcmReportController {

    private final BcmReportService service;

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public List<ReportDef> catalog() { return service.catalog(); }

    @GetMapping("/{code}")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> generate(@PathVariable String code) {
        return service.generate(code);
    }
}
