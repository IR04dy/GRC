package com.tss.grc.module.risk.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.risk.dto.RiskAppetiteRequest;
import com.tss.grc.module.risk.entity.AppetiteBreachEvent;
import com.tss.grc.module.risk.entity.RiskAppetiteStatement;
import com.tss.grc.module.risk.service.RiskAppetiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Risk appetite statements — assessment §5.1 ERM #6.
 *
 * <p>Canonical home for the {@code /api/v1/risk/appetite*} surface. Extracted
 * from the former {@code KriAppetiteController} (ERM epic E-E): appetite and
 * the KRI engine are unrelated domains, so the appetite endpoints now live in
 * a dedicated controller backed by {@link RiskAppetiteService} (the KRI
 * endpoints moved to {@link LegacyKriController}, pending consolidation onto
 * the canonical {@code risk/kri/*} engine).
 */
@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@RequireModule("RISK")
@Tag(name = "ERM — Risk appetite")
@SecurityRequirement(name = "bearerAuth")
public class RiskAppetiteController {

    private final RiskAppetiteService appetiteService;

    @GetMapping("/appetite")
    @PreAuthorize("hasPermission('RISK','READ')")
    public Page<RiskAppetiteStatement> listAppetites(@PageableDefault(size = 50) Pageable pageable) {
        return appetiteService.list(pageable);
    }

    @GetMapping("/appetite/{id}")
    @PreAuthorize("hasPermission('RISK','READ')")
    public RiskAppetiteStatement getAppetite(@PathVariable Long id) {
        return appetiteService.get(id);
    }

    @PostMapping("/appetite")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public RiskAppetiteStatement createAppetite(@Valid @RequestBody RiskAppetiteRequest body) {
        return appetiteService.create(body);
    }

    @PutMapping("/appetite/{id}")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public RiskAppetiteStatement updateAppetite(@PathVariable Long id, @Valid @RequestBody RiskAppetiteRequest body) {
        return appetiteService.update(id, body);
    }

    @DeleteMapping("/appetite/{id}")
    @PreAuthorize("hasPermission('RISK','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAppetite(@PathVariable Long id) {
        appetiteService.delete(id);
    }

    @PostMapping("/appetite/{id}/approve")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public RiskAppetiteStatement approveAppetite(@PathVariable Long id) {
        // F2-431 — no body. The approver is the authenticated principal, so an `approvedBy`
        // field would be one the server ignores, which tells a caller their value matters
        // (F2-184). Removing it is safe under Spring Boot's disabled
        // FAIL_ON_UNKNOWN_PROPERTIES: an existing client that still posts one is unaffected.
        return appetiteService.approve(id);
    }

    @PostMapping("/appetite/{id}/retire")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public RiskAppetiteStatement retireAppetite(@PathVariable Long id) {
        return appetiteService.retire(id);
    }

    @PostMapping("/appetite/{id}/exposure")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    @Operation(summary = "Update current exposure. Auto-generates appetite breach event if exposure exceeds tolerance band.")
    public RiskAppetiteStatement updateExposure(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal value = body.get("value") instanceof Number n
            ? new BigDecimal(n.toString())
            : new BigDecimal(body.get("value").toString());
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;
        return appetiteService.updateExposure(id, value, notes);
    }

    @GetMapping("/appetite/{id}/breaches")
    @PreAuthorize("hasPermission('RISK','READ')")
    public List<AppetiteBreachEvent> listAppetiteBreaches(@PathVariable Long id) {
        return appetiteService.listBreaches(id);
    }

    @GetMapping("/appetite/dashboard")
    @PreAuthorize("hasPermission('RISK','READ')")
    public Map<String, Object> appetiteDashboard() {
        return appetiteService.dashboard();
    }

    // ── E-H — register → appetite exposure aggregation + breach escalation ──
    @PostMapping("/appetite/recompute-exposure")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    @Operation(summary = "Aggregate live residual exposure from the register into each approved "
        + "appetite statement (worst residual per category) and escalate any breach via F8.")
    public RiskAppetiteService.ExposureRecompute recomputeAppetiteExposure() {
        return appetiteService.recomputeExposureFromRegister();
    }
}
