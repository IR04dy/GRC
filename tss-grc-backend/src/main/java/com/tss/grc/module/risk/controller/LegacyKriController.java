package com.tss.grc.module.risk.controller;

import com.tss.grc.module.risk.dto.KriDefinitionRequest;
import com.tss.grc.module.risk.entity.KriBreachEvent;
import com.tss.grc.module.risk.entity.KriDefinition;
import com.tss.grc.module.risk.entity.KriReading;
import com.tss.grc.module.risk.service.KriEngineService;
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
 * Legacy KRI threshold engine REST surface — assessment §5.1 ERM #5.
 *
 * <p><b>Deprecated (ERM epic E-E).</b> This is the numeric-id, single-table
 * ({@code risk.kri_definitions}/{@code kri_readings}/{@code kri_breach_events})
 * KRI engine. The <em>canonical</em> KRI engine is the richer Sprint-13
 * {@code risk/kri/*} stack — UUID external ids, a full AMBER/RED breach
 * lifecycle (open / escalate / de-escalate / close), F8 domain events,
 * sustained-RED auto-CAPA, and a scheduler — exposed at
 * {@code /api/v1/risk/kri/*} ({@link com.tss.grc.module.risk.kri.controller.KriController}).
 *
 * <p>This controller (split out of the former {@code KriAppetiteController})
 * is retained <em>only</em> to keep the existing frontend KRI surface
 * ({@code riskApi.kris*}) working until it is repointed to the canonical
 * endpoints. The remaining E-E work — migrating the legacy rows into the
 * canonical tables, repointing the frontend + the reporting/board-pack/
 * framework-anchor consumers, then deleting this controller and
 * {@link KriEngineService} — is tracked in the remediation plan (§20 E-E).
 *
 * @deprecated superseded by {@link com.tss.grc.module.risk.kri.controller.KriController};
 *             scheduled for removal once KRI is consolidated onto {@code risk/kri/*}.
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@Tag(name = "ERM — KRI engine (legacy, superseded by /api/v1/risk/kri/*)")
@SecurityRequirement(name = "bearerAuth")
public class LegacyKriController {

    private final KriEngineService kriService;

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
}
