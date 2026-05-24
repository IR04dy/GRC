package com.tss.grc.module.sust.climate.controller;

import com.tss.grc.module.sust.climate.dto.ClimateDtos.AnnualClimateReport;
import com.tss.grc.module.sust.climate.service.TcfdReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Annual IFRS S2 / TCFD disclosure pack — Sprint 11.
 *
 * <p>One read endpoint: assembles scenarios, sector impacts, weighted
 * expected loss, financed-emissions roll-up, per-asset-class records,
 * and target progress for the given reporting year.
 */
@RestController
@RequestMapping("/api/v1/climate/report")
@RequiredArgsConstructor
@Tag(name = "Annual Climate Report (IFRS S2 / TCFD)")
@SecurityRequirement(name = "bearerAuth")
public class ClimateReportController {

    private static final String READ_GUARD =
            "hasAnyRole('SUSTAINABILITY_OFFICER','CFO','CISO','RISK_MANAGER','BOARD_MEMBER','AUDITOR','PLATFORM_ADMIN')";

    private final TcfdReportService service;

    @GetMapping("/annual")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Annual IFRS S2 / TCFD disclosure pack for the reporting year.")
    public AnnualClimateReport annual(@RequestParam Integer year) {
        return service.generateAnnualReportData(year);
    }
}
