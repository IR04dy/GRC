package com.tss.grc.module.risk.controller;

import com.tss.grc.module.risk.dto.MethodologyDtos.ConfigDto;
import com.tss.grc.module.risk.dto.MethodologyDtos.ConfigUpdateDto;
import com.tss.grc.module.risk.dto.MethodologyDtos.ImpactRatingsResponse;
import com.tss.grc.module.risk.dto.MethodologyDtos.SetImpactDto;
import com.tss.grc.module.risk.service.RiskMethodologyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Configurable methodology + multi-dimensional impact endpoints — ERM epic E-F.
 *
 * <pre>
 *   GET  /api/v1/risk/methodology                      org methodology config (seeded)
 *   PUT  /api/v1/risk/methodology                      update dimensions / scales
 *   GET  /api/v1/risk/risks/{id}/impact-dimensions     per-dimension impact + worst
 *   PUT  /api/v1/risk/risks/{id}/impact-dimensions     set dimensions → worst drives impact
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@Tag(name = "Risk methodology & multi-dimensional impact (E-F)")
public class RiskMethodologyController {

    private final RiskMethodologyService service;

    @GetMapping("/methodology")
    @PreAuthorize("hasPermission('RISK','READ')")
    public ConfigDto getMethodology() {
        return ConfigDto.from(service.getOrSeed());
    }

    @PutMapping("/methodology")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public ConfigDto updateMethodology(@RequestBody ConfigUpdateDto dto) {
        return ConfigDto.from(service.updateConfig(dto));
    }

    @GetMapping("/risks/{id}/impact-dimensions")
    @PreAuthorize("hasPermission('RISK','READ')")
    public ImpactRatingsResponse getImpactDimensions(@PathVariable Long id) {
        return service.getImpactRatings(id);
    }

    @PutMapping("/risks/{id}/impact-dimensions")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public ImpactRatingsResponse setImpactDimensions(@PathVariable Long id, @Valid @RequestBody SetImpactDto dto) {
        return service.setImpactRatings(id, dto.dimensions());
    }
}
