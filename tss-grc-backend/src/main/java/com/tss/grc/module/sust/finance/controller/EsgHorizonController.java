package com.tss.grc.module.sust.finance.controller;

import com.tss.grc.module.sust.finance.entity.CbamLine;
import com.tss.grc.module.sust.finance.entity.EnvPermit;
import com.tss.grc.module.sust.finance.entity.EsgTarget;
import com.tss.grc.module.sust.finance.entity.EsgTargetActual;
import com.tss.grc.module.sust.finance.entity.IfrsRequirement;
import com.tss.grc.module.sust.finance.service.EsgHorizonService;
import com.tss.grc.module.sust.finance.service.EsgHorizonService.CbamSummary;
import com.tss.grc.module.sust.finance.service.EsgHorizonService.IfrsSummary;
import com.tss.grc.module.sust.finance.service.EsgHorizonService.PermitView;
import com.tss.grc.module.sust.finance.service.EsgHorizonService.TargetView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Green-Finance uplift G4 (plan §5.4–§5.7) — regulatory horizon: ESG targets &
 * trajectory (auto-CAPA on off-track), IFRS S1/S2 readiness, NCEC permit expiry
 * surveillance (auto-CAPA), and the EU CBAM exposure screen.
 * Reads = SUST READ; writes + sweeps = SUST WRITE.
 */
@RestController
@RequestMapping("/api/v1/sustainability/horizon")
@RequiredArgsConstructor
@Tag(name = "Sustainability — Regulatory horizon (targets / IFRS / NCEC / CBAM)")
public class EsgHorizonController {

    private static final String READ_GUARD  = "hasPermission('SUST','READ')";
    private static final String WRITE_GUARD = "hasPermission('SUST','WRITE')";

    private final EsgHorizonService service;

    // ── targets & trajectory ────────────────────────────────────────────

    public record CreateTargetRequest(
        @NotBlank @Size(max = 40) String targetCode,
        @NotBlank @Size(max = 300) String titleEn,
        @Size(max = 300) String titleAr,
        @NotBlank @Pattern(regexp = "GHG_S1|GHG_S2|GHG_S3|ENERGY|WATER|WASTE|RENEWABLES_SHARE") String scope,
        @NotBlank @Size(max = 40) String unit,
        @NotNull Integer baselineYear,
        @NotNull BigDecimal baselineValue,
        @NotNull Integer targetYear,
        @NotNull BigDecimal targetValue,
        Boolean sgiAligned) {}

    public record AddActualRequest(@NotNull Integer year, @NotNull BigDecimal value, @Size(max = 500) String notes) {}

    @GetMapping("/targets")
    @PreAuthorize(READ_GUARD)
    public List<TargetView> targets() { return service.listTargets(); }

    @PostMapping("/targets")
    @PreAuthorize(WRITE_GUARD)
    public EsgTarget createTarget(@Valid @RequestBody CreateTargetRequest r) {
        EsgTarget t = new EsgTarget();
        t.setTargetCode(r.targetCode());
        t.setTitleEn(r.titleEn());
        t.setTitleAr(r.titleAr());
        t.setScope(r.scope());
        t.setUnit(r.unit());
        t.setBaselineYear(r.baselineYear());
        t.setBaselineValue(r.baselineValue());
        t.setTargetYear(r.targetYear());
        t.setTargetValue(r.targetValue());
        t.setSgiAligned(Boolean.TRUE.equals(r.sgiAligned()));
        return service.createTarget(t);
    }

    @PostMapping("/targets/{id}/actuals")
    @PreAuthorize(WRITE_GUARD)
    public EsgTargetActual addActual(@PathVariable Long id, @Valid @RequestBody AddActualRequest r) {
        return service.addActual(id, r.year(), r.value(), r.notes());
    }

    @PostMapping("/targets/trajectory-sweep")
    @PreAuthorize(WRITE_GUARD)
    public Map<String, Integer> trajectorySweep() {
        return Map.of("capasRaised", service.trajectorySweep());
    }

    // ── IFRS S1/S2 ──────────────────────────────────────────────────────

    public record SetIfrsStatusRequest(
        @NotBlank @Pattern(regexp = "COVERED|PARTIAL|GAP") String status,
        @Size(max = 300) String coverageRef,
        @Size(max = 200) String ownerName) {}

    @GetMapping("/ifrs")
    @PreAuthorize(READ_GUARD)
    public List<IfrsRequirement> ifrs() { return service.listIfrs(); }

    @GetMapping("/ifrs/summary")
    @PreAuthorize(READ_GUARD)
    public IfrsSummary ifrsSummary() { return service.ifrsSummary(); }

    @PostMapping("/ifrs/{id}/status")
    @PreAuthorize(WRITE_GUARD)
    public IfrsRequirement setIfrsStatus(@PathVariable Long id, @Valid @RequestBody SetIfrsStatusRequest r) {
        return service.setIfrsStatus(id, r.status(), r.coverageRef(), r.ownerName());
    }

    // ── NCEC permits ────────────────────────────────────────────────────

    public record CreatePermitRequest(
        @NotBlank @Size(max = 60) String permitNumber,
        @NotBlank @Size(max = 300) String facilityEn,
        @Size(max = 300) String facilityAr,
        @Size(max = 80) String permitType,
        LocalDate issueDate,
        @NotNull LocalDate expiryDate,
        @Size(max = 40) String monitoringFrequency) {}

    @GetMapping("/env-permits")
    @PreAuthorize(READ_GUARD)
    public List<PermitView> permits() { return service.listPermits(); }

    @PostMapping("/env-permits")
    @PreAuthorize(WRITE_GUARD)
    public EnvPermit createPermit(@Valid @RequestBody CreatePermitRequest r) {
        EnvPermit p = new EnvPermit();
        p.setPermitNumber(r.permitNumber());
        p.setFacilityEn(r.facilityEn());
        p.setFacilityAr(r.facilityAr());
        p.setPermitType(r.permitType());
        p.setIssueDate(r.issueDate());
        p.setExpiryDate(r.expiryDate());
        p.setMonitoringFrequency(r.monitoringFrequency());
        return service.createPermit(p);
    }

    @PostMapping("/env-permits/expiry-sweep")
    @PreAuthorize(WRITE_GUARD)
    public Map<String, Integer> permitSweep(@RequestParam(defaultValue = "90") @Min(1) @Max(365) int leadDays) {
        return Map.of("capasRaised", service.permitExpirySweep(leadDays));
    }

    // ── CBAM ────────────────────────────────────────────────────────────

    public record CreateCbamLineRequest(
        @NotBlank @Pattern(regexp = "ALUMINIUM|STEEL|CEMENT|FERTILISERS|HYDROGEN|ELECTRICITY") String cnGroup,
        @NotBlank @Size(max = 300) String productDescEn,
        @Size(max = 300) String productDescAr,
        @NotNull Integer periodYear,
        @NotNull @Min(1) @Max(4) Integer periodQuarter,
        @NotNull @DecimalMin("0.0001") BigDecimal exportVolumeTonnes,
        @NotNull @DecimalMin("0") BigDecimal embeddedTco2ePerTonne,
        @Size(max = 200) String euImporter,
        @Pattern(regexp = "MEASURED|DEFAULT_VALUES") String dataBasis) {}

    @GetMapping("/cbam")
    @PreAuthorize(READ_GUARD)
    public List<CbamLine> cbam() { return service.listCbam(); }

    @GetMapping("/cbam/summary")
    @PreAuthorize(READ_GUARD)
    public CbamSummary cbamSummary() { return service.cbamSummary(); }

    @PostMapping("/cbam")
    @PreAuthorize(WRITE_GUARD)
    public CbamLine createCbam(@Valid @RequestBody CreateCbamLineRequest r) {
        CbamLine c = new CbamLine();
        c.setCnGroup(r.cnGroup());
        c.setProductDescEn(r.productDescEn());
        c.setProductDescAr(r.productDescAr());
        c.setPeriodYear(r.periodYear());
        c.setPeriodQuarter(r.periodQuarter());
        c.setExportVolumeTonnes(r.exportVolumeTonnes());
        c.setEmbeddedTco2ePerTonne(r.embeddedTco2ePerTonne());
        if (r.euImporter() != null) c.setEuImporter(r.euImporter());
        if (r.dataBasis() != null) c.setDataBasis(r.dataBasis());
        return service.createCbamLine(c);
    }
}
