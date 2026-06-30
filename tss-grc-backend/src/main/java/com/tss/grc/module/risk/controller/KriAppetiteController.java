package com.tss.grc.module.risk.controller;

import com.tss.grc.module.risk.dto.KriDefinitionRequest;
import com.tss.grc.module.risk.dto.RiskAppetiteRequest;
import com.tss.grc.module.risk.entity.AppetiteBreachEvent;
import com.tss.grc.module.risk.entity.KriBreachEvent;
import com.tss.grc.module.risk.entity.KriDefinition;
import com.tss.grc.module.risk.entity.KriReading;
import com.tss.grc.module.risk.entity.RiskAppetiteStatement;
import com.tss.grc.module.risk.service.KriEngineService;
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
 * KRI threshold engine + Risk appetite endpoints — assessment §5.1 ERM #5 + #6.
 */
@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@Tag(name = "ERM — KRI engine & Risk appetite")
@SecurityRequirement(name = "bearerAuth")
public class KriAppetiteController {

    private final KriEngineService kriService;
    private final RiskAppetiteService appetiteService;

    // ── KRI definitions ─────────────────────────────────────────────────────

    @GetMapping("/kris")
    @PreAuthorize("hasPermission('RISK','READ')")
    public Page<KriDefinition> listKris(@PageableDefault(size = 50) Pageable pageable) {
        return kriService.listDefinitions(pageable);
    }

    @GetMapping("/kris/{id}")
    @PreAuthorize("hasPermission('RISK','READ')")
    public KriDefinition getKri(@PathVariable Long id) {
        return kriService.getDefinition(id);
    }

    @PostMapping("/kris")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public KriDefinition createKri(@Valid @RequestBody KriDefinitionRequest body) {
        return kriService.createDefinition(body);
    }

    @PutMapping("/kris/{id}")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public KriDefinition updateKri(@PathVariable Long id, @Valid @RequestBody KriDefinitionRequest body) {
        return kriService.updateDefinition(id, body);
    }

    @DeleteMapping("/kris/{id}")
    @PreAuthorize("hasPermission('RISK','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteKri(@PathVariable Long id) {
        kriService.deleteDefinition(id);
    }

    // ── Readings + breaches ─────────────────────────────────────────────────

    @PostMapping("/kris/{id}/readings")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    @Operation(summary = "Record a new KRI reading. Auto-generates a breach event when the band worsens.")
    public Map<String, Object> recordReading(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal value = body.get("value") instanceof Number n
            ? new BigDecimal(n.toString())
            : new BigDecimal(body.get("value").toString());
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;
        return kriService.recordReading(id, value, notes);
    }

    @GetMapping("/kris/{id}/readings")
    @PreAuthorize("hasPermission('RISK','READ')")
    public List<KriReading> listReadings(@PathVariable Long id) {
        return kriService.listReadings(id);
    }

    @GetMapping("/kris/{id}/breaches")
    @PreAuthorize("hasPermission('RISK','READ')")
    public List<KriBreachEvent> listBreaches(@PathVariable Long id) {
        return kriService.listBreaches(id);
    }

    @PostMapping("/kris/breaches/{breachId}/acknowledge")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public KriBreachEvent acknowledgeBreach(@PathVariable Long breachId, @RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") instanceof Number n ? n.longValue() : null;
        return kriService.acknowledgeBreach(breachId, userId);
    }

    @PostMapping("/kris/breaches/{breachId}/resolve")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public KriBreachEvent resolveBreach(@PathVariable Long breachId, @RequestBody Map<String, Object> body) {
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;
        return kriService.resolveBreach(breachId, notes);
    }

    @GetMapping("/kris/dashboard")
    @PreAuthorize("hasPermission('RISK','READ')")
    public Map<String, Object> kriDashboard() {
        return kriService.dashboard();
    }

    // ── Risk appetite ───────────────────────────────────────────────────────

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
    public RiskAppetiteStatement approveAppetite(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String approver = body.get("approvedBy") != null ? body.get("approvedBy").toString() : null;
        return appetiteService.approve(id, approver);
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
