package com.tss.grc.module.platform.completion;

import com.tss.grc.module.evidence.entity.EvidenceConnector;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.platform.completion.BuyerGapService.AppetiteVariance;
import com.tss.grc.module.platform.completion.BuyerGapService.AtRiskTransfer;
import com.tss.grc.module.platform.completion.BuyerGapService.AttestBody;
import com.tss.grc.module.platform.completion.BuyerGapService.AttestationSweepResult;
import com.tss.grc.module.platform.completion.BuyerGapService.ConnectorPatch;
import com.tss.grc.module.platform.completion.BuyerGapService.DsrSlaSweepResult;
import com.tss.grc.module.platform.completion.BuyerGapService.FreshnessSweepResult;
import com.tss.grc.module.platform.completion.BuyerGapService.Pdpl72hSweepResult;
import com.tss.grc.module.privacy.entity.ProcessingActivity;
import com.tss.grc.module.risk.entity.RiskAttestation;
import com.tss.grc.module.risk.entity.RiskControl;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST surface for the buyer-assessment completion bundle:
 *
 * <pre>
 *   §5.1 ERM
 *     POST  /api/v1/risk/kris/{id}/readings            {value, notes}
 *     GET   /api/v1/risk/risks/{id}/controls
 *     POST  /api/v1/risk/risks/{id}/controls           {controlId, effectiveness, contributionPct, notes}
 *     GET   /api/v1/risk/appetite/variance
 *
 *   §5.9 Evidence
 *     POST  /api/v1/evidence/items/freshness/sweep
 *
 *   §5.4 Privacy
 *     GET   /api/v1/privacy/processing-activities/at-risk-transfers
 *     GET   /api/v1/privacy/processing-activities/special-category
 * </pre>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Buyer-assessment completion (§5.1 + §5.9 + §5.4)")
public class BuyerGapController {

    private final BuyerGapService service;

    // ── ERM record ─────────────────────────────────────────────
    //
    // POST /api/v1/risk/kris/{id}/readings is already served by the existing
    // KriAppetiteController + KriEngineService (which also creates breach
    // events). The buyer doc's §5.1 #2 KRI engine ask is therefore already
    // closed by the legacy path — we don't redeclare it here. The new
    // bundle keeps risk-control linkage + appetite variance + evidence
    // freshness + privacy at-risk transfers.

    public record LinkControlBody(Long controlId, String effectiveness,
                                  BigDecimal contributionPct, String notes) {}

    @GetMapping("/api/v1/risk/risks/{id}/controls")
    @PreAuthorize("isAuthenticated()")
    public List<RiskControl> listRiskControls(@PathVariable Long id) {
        return service.listRiskControls(id);
    }

    @PostMapping("/api/v1/risk/risks/{id}/controls")
    @PreAuthorize("isAuthenticated()")
    public RiskControl linkRiskControl(@PathVariable Long id, @RequestBody LinkControlBody body) {
        return service.linkRiskControl(id, body.controlId(), body.effectiveness(),
            body.contributionPct(), body.notes());
    }

    @GetMapping("/api/v1/risk/appetite/variance")
    @PreAuthorize("isAuthenticated()")
    public AppetiteVariance appetiteVariance() { return service.appetiteVariance(); }

    // ── Evidence freshness ─────────────────────────────────────

    @PostMapping("/api/v1/evidence/items/freshness/sweep")
    @PreAuthorize("isAuthenticated()")
    public FreshnessSweepResult freshnessSweep() { return service.evidenceFreshnessSweep(); }

    // ── Privacy: at-risk transfers + special-category ──────────

    @GetMapping("/api/v1/privacy/processing-activities/at-risk-transfers")
    @PreAuthorize("isAuthenticated()")
    public List<AtRiskTransfer> atRiskTransfers() { return service.atRiskTransfers(); }

    @GetMapping("/api/v1/privacy/processing-activities/special-category")
    @PreAuthorize("isAuthenticated()")
    public List<ProcessingActivity> specialCategory() { return service.specialCategoryActivities(); }

    // ── §5.1 #6 — Risk owner attestation cycle (V115) ─────────────────

    @PostMapping("/api/v1/risk/risks/{id}/attestations")
    @PreAuthorize("isAuthenticated()")
    public RiskAttestation attestRisk(@PathVariable Long id, @RequestBody AttestBody body) {
        return service.attestRisk(id, body);
    }

    @GetMapping("/api/v1/risk/risks/{id}/attestations")
    @PreAuthorize("isAuthenticated()")
    public List<RiskAttestation> listRiskAttestations(@PathVariable Long id) {
        return service.listRiskAttestations(id);
    }

    @PostMapping("/api/v1/risk/attestations/sweep")
    @PreAuthorize("isAuthenticated()")
    public AttestationSweepResult riskAttestationSweep() {
        return service.riskAttestationSweep();
    }

    // ── §5.4 #1 — DSR 30-day SLA sweep (V115) ─────────────────────────

    @PostMapping("/api/v1/privacy/dsr/sla/sweep")
    @PreAuthorize("isAuthenticated()")
    public DsrSlaSweepResult dsrSlaSweep() {
        return service.dsrSlaSweep();
    }

    // ── §5.4 #2 — PDPL 72-hour breach deadline sweep (V115) ───────────

    @PostMapping("/api/v1/privacy/breach/pdpl-72h/sweep")
    @PreAuthorize("isAuthenticated()")
    public Pdpl72hSweepResult pdpl72hSweep() {
        return service.pdpl72hSweep();
    }

    // ── §5.9 #2 — Evidence connector registry (V115) ──────────────────

    @GetMapping("/api/v1/evidence/connectors")
    @PreAuthorize("isAuthenticated()")
    public List<EvidenceConnector> listConnectors() {
        return service.listConnectors();
    }

    @PatchMapping("/api/v1/evidence/connectors/{code}")
    @PreAuthorize("isAuthenticated()")
    public EvidenceConnector updateConnector(@PathVariable String code,
                                             @RequestBody ConnectorPatch body) {
        return service.updateConnectorStatus(code, body);
    }
}
