package com.tss.grc.module.change_mgmt.controller;

import com.tss.grc.module.admin.committee.service.CommitteeGateMapService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ADR-017 <b>D-2</b> — the screen behind the refusal: this tenant's CAB / ECAB authority.
 *
 * <pre>
 *   GET    /api/v1/changes/authority             → both gates + configured/fault
 *   GET    /api/v1/changes/authority/eligible    → committees that may serve
 *   PUT    /api/v1/changes/authority/{gate}      { "committeeId": 13 }
 *   DELETE /api/v1/changes/authority/{gate}
 * </pre>
 *
 * <p><b>Why this lives in {@code change_mgmt} and not beside the committee controllers it
 * administers.</b> The first draft sat in {@code admin/committee} carrying
 * {@code @RequireModule("CHANGE")} on the argument that a tenant refused every CAB approval must be
 * able to reach the remedy — and {@code EntitlementCoverageRatchetTest} rejected it, correctly: a
 * controller must name the SKU its <em>package</em> is sold as, or the package↔SKU map in
 * {@code SkuRegistry} stops being the source of truth. The ratchet's own message names the two legal
 * options (move the package, or move the controller), and moving the controller is the honest one
 * here: this endpoint configures <b>change governance</b>, its absence breaks {@code CHANGE} and
 * nothing else, and the value it stores is read by {@code ChangeManagementService}. The committee
 * subsystem is merely where the row and the validation live.
 *
 * <p>⚠ <b>One coupling remains and is not fixable by an annotation.</b> Naming the authority needs a
 * committee to name, and <b>constituting</b> a committee is licensed as {@code BOARD}. So a tenant
 * that bought change governance without the board pack can reach this screen and still have nothing
 * to select. All three dev tenants hold both licences, so it is latent rather than live; it is
 * recorded in ADR-017 D-2 as a SKU-dependency note for the D-4 shelf decision.
 *
 * <p>Writes are {@code ADMIN:WRITE} — the same permission that charters a committee. Naming which
 * governance body holds an authority is a charter act, so the person who may constitute the body is
 * the person who may appoint it; a {@code CHANGE:APPROVE} holder decides changes, not who decides them.
 */
@RestController
@RequestMapping("/api/v1/changes/authority")
@RequiredArgsConstructor
@RequireModule("CHANGE")
@Tag(name = "Change Management — Change Authority (ADR-017 D-2)")
@SecurityRequirement(name = "bearerAuth")
public class ChangeAuthorityController {

    private final CommitteeGateMapService service;

    @GetMapping
    @Operation(summary = "This tenant's CAB/ECAB authority, with the configured verdict per gate")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<Map<String, Object>> current() {
        return service.current();
    }

    @GetMapping("/eligible")
    @Operation(summary = "Committees that may act as an authority (never DISSOLVED)")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<Map<String, Object>> eligible() {
        return service.eligibleCommittees();
    }

    @PutMapping("/{gate}")
    @Operation(summary = "Name the committee that acts as this tenant's CAB or ECAB")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public Map<String, Object> set(@PathVariable String gate, @RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("committeeId");
        Long committeeId = raw instanceof Number n ? n.longValue()
            : raw == null ? null : Long.valueOf(raw.toString().trim());
        return service.upsert(gate, committeeId);
    }

    @DeleteMapping("/{gate}")
    @Operation(summary = "Clear this gate — changes routed to it can no longer be approved")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public ResponseEntity<Void> clear(@PathVariable String gate) {
        service.clear(gate);
        return ResponseEntity.noContent().build();
    }
}
