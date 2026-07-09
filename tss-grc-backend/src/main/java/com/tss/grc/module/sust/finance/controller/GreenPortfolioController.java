package com.tss.grc.module.sust.finance.controller;

import com.tss.grc.module.sust.finance.entity.CarbonCredit;
import com.tss.grc.module.sust.finance.entity.PortfolioAsset;
import com.tss.grc.module.sust.finance.entity.TaxonomyEntry;
import com.tss.grc.module.sust.finance.service.GreenPortfolioService;
import com.tss.grc.module.sust.finance.service.GreenPortfolioService.PortfolioSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Green-Finance uplift G3 (plan §5) — Green Portfolio (taxonomy, tagging w/ SoD, GAR)
 * + voluntary carbon-credit ledger. Reads = SUST READ; asset/credit creation and tag
 * proposals = SUST WRITE; tag decisions and retirements = SUST APPROVE.
 */
@RestController
@RequestMapping("/api/v1/sustainability/portfolio")
@RequiredArgsConstructor
@Tag(name = "Sustainability — Green portfolio & carbon credits")
public class GreenPortfolioController {

    private static final String READ_GUARD    = "hasPermission('SUST','READ')";
    private static final String WRITE_GUARD   = "hasPermission('SUST','WRITE')";
    private static final String APPROVE_GUARD = "hasPermission('SUST','APPROVE')";

    private final GreenPortfolioService service;

    // ── taxonomy + summary ──────────────────────────────────────────────

    @GetMapping("/taxonomy")
    @PreAuthorize(READ_GUARD)
    public List<TaxonomyEntry> taxonomy() { return service.listTaxonomy(); }

    @GetMapping("/summary")
    @PreAuthorize(READ_GUARD)
    public PortfolioSummary summary() { return service.summary(); }

    // ── assets + tagging ────────────────────────────────────────────────

    public record CreateAssetRequest(
        @NotBlank @Size(max = 40) String assetCode,
        @NotBlank @Size(max = 300) String nameEn,
        @Size(max = 300) String nameAr,
        @Size(max = 80) String sector,
        @NotNull @DecimalMin("0.0001") BigDecimal exposureSarMn) {}

    public record ProposeTagRequest(
        @NotBlank @Size(max = 40) String taxonomyCode,
        @NotBlank @Pattern(regexp = "ELIGIBLE|TRANSITION|INELIGIBLE") String requestedStatus) {}

    public record DecideTagRequest(@NotNull Boolean approve) {}

    @GetMapping("/assets")
    @PreAuthorize(READ_GUARD)
    public List<PortfolioAsset> assets() { return service.listAssets(); }

    @PostMapping("/assets")
    @PreAuthorize(WRITE_GUARD)
    public PortfolioAsset createAsset(@Valid @RequestBody CreateAssetRequest r) {
        return service.createAsset(r.assetCode(), r.nameEn(), r.nameAr(), r.sector(), r.exposureSarMn());
    }

    @PostMapping("/assets/{id}/propose-tag")
    @PreAuthorize(WRITE_GUARD)
    public PortfolioAsset proposeTag(@PathVariable Long id, @Valid @RequestBody ProposeTagRequest r) {
        return service.proposeTag(id, r.taxonomyCode(), r.requestedStatus());
    }

    @PostMapping("/assets/{id}/decide-tag")
    @PreAuthorize(APPROVE_GUARD)
    public PortfolioAsset decideTag(@PathVariable Long id, @Valid @RequestBody DecideTagRequest r) {
        return service.decideTag(id, r.approve());
    }

    // ── carbon credits ──────────────────────────────────────────────────

    public record PurchaseCreditRequest(
        @NotBlank @Size(max = 80) String registry,
        @NotBlank @Size(max = 200) String serialRange,
        @NotNull Integer vintageYear,
        @Size(max = 80) String projectType,
        @Size(max = 60) String projectCountry,
        @NotNull @DecimalMin("0.0001") BigDecimal quantityTco2e,
        BigDecimal priceSarPerT) {}

    public record RetireCreditRequest(
        @NotBlank @Size(max = 200) String certificateRef,
        @NotBlank @Size(max = 300) String purpose) {}

    @GetMapping("/credits")
    @PreAuthorize(READ_GUARD)
    public List<CarbonCredit> credits() { return service.listCredits(); }

    @PostMapping("/credits")
    @PreAuthorize(WRITE_GUARD)
    public CarbonCredit purchase(@Valid @RequestBody PurchaseCreditRequest r) {
        CarbonCredit c = new CarbonCredit();
        c.setRegistry(r.registry());
        c.setSerialRange(r.serialRange());
        c.setVintageYear(r.vintageYear());
        c.setProjectType(r.projectType());
        c.setProjectCountry(r.projectCountry());
        c.setQuantityTco2e(r.quantityTco2e());
        c.setPriceSarPerT(r.priceSarPerT());
        return service.purchaseCredit(c);
    }

    @PostMapping("/credits/{id}/retire")
    @PreAuthorize(APPROVE_GUARD)
    public CarbonCredit retire(@PathVariable Long id, @Valid @RequestBody RetireCreditRequest r) {
        return service.retireCredit(id, r.certificateRef(), r.purpose());
    }
}
