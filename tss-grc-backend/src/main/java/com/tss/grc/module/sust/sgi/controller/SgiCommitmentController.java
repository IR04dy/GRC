package com.tss.grc.module.sust.sgi.controller;

import com.tss.grc.module.sust.sgi.dto.SgiCreateRequest;
import com.tss.grc.module.sust.sgi.entity.SgiCommitment;
import com.tss.grc.module.sust.sgi.service.SgiCommitmentService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Saudi Green Initiative REST endpoints —
 * {@code /api/sustainability/sgi-commitments/*}.
 *
 * <pre>
 *   GET    /                       list (filter by status / sector)
 *   GET    /aggregate              KPI rollup (tiers, sectors, achieved/retired)
 *   GET    /:id                    fetch one
 *   GET    /:id/progress           computed progress + tier for one commitment
 *   GET    /by-code/:code          fetch by commitment_code (lookup for CapaTab)
 *   POST   /                       create (defaults: ACTIVE)
 *   PUT    /:id                    update (refused if status ∈ ACHIEVED, RETIRED)
 *   POST   /:id/achieve            ACTIVE → ACHIEVED   (APPROVE; Rules A/C/D)
 *   POST   /:id/retire             ACTIVE → RETIRED    (APPROVE; Rule B: reason)
 *   DELETE /:id                    soft-delete
 * </pre>
 */
@RestController
@RequestMapping("/api/sustainability/sgi-commitments")
@RequiredArgsConstructor
@Tag(name = "Sustainability — SGI Commitments")
public class SgiCommitmentController {

    private final SgiCommitmentService service;
    private final com.tss.grc.module.sust.sgi.service.SgiSurveillanceService surveillance;
    private final com.tss.grc.module.platform.export.TabularExportService tabularExport;

    @GetMapping
    @PreAuthorize("hasPermission('SUST','READ')")
    public Page<SgiCommitment> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sector,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        if (status != null) return service.listByStatus(status, p);
        if (sector != null) return service.listBySector(sector, p);
        return service.list(p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> aggregate() { return service.aggregate(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public SgiCommitment get(@PathVariable Long id) { return service.get(id); }

    @GetMapping("/{id}/progress")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> progress(@PathVariable Long id) {
        return service.progress(service.get(id));
    }

    @GetMapping("/by-code/{code}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public SgiCommitment getByCode(@PathVariable String code) { return service.getByCode(code); }

    @PostMapping
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<SgiCommitment> create(@Valid @RequestBody SgiCreateRequest req) {
        return ResponseEntity.status(201).body(service.create(req.toEntity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public SgiCommitment update(@PathVariable Long id, @RequestBody SgiCommitment req) {
        return service.update(id, req);
    }

    /**
     * ACTIVE → ACHIEVED. Disclosure-grade governance action: APPROVE-guarded,
     * no body, achiever stamped from the principal. Rules A (target met),
     * C (SoD — achiever ≠ last editor, 409) and D (evidence attached, 400)
     * enforced server-side.
     */
    @PostMapping("/{id}/achieve")
    @PreAuthorize("hasPermission('SUST','APPROVE')")
    public SgiCommitment achieve(@PathVariable Long id) {
        return service.achieve(id);
    }

    /** ACTIVE → RETIRED. APPROVE-guarded. Body: {@code { "reason": "Replaced by …" }} (Rule B). */
    @PostMapping("/{id}/retire")
    @PreAuthorize("hasPermission('SUST','APPROVE')")
    public SgiCommitment retire(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.retire(id, body == null ? null : body.get("reason"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /** SGI-E — the whole SGI register as a branded XLSX (via the shared tabular engine). */
    @GetMapping("/export.xlsx")
    @PreAuthorize("hasPermission('SUST','EXPORT')")
    public org.springframework.http.ResponseEntity<byte[]> exportXlsx(
            @RequestParam(defaultValue = "en") String lang) {
        byte[] bytes = tabularExport.xlsx(service.exportPayload(lang));
        String filename = com.tss.grc.module.platform.export.ExportNaming.build(
            "SGI", "CommitmentRegister", lang, "xlsx");
        return org.springframework.http.ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"")
            .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
            .body(bytes);
    }

    /**
     * SGI-C — pull {@code current_value} from the commitment's linked source
     * (ESG metric reading or GHG inventory total). MANUAL commitments 400.
     */
    @PostMapping("/{id}/refresh-value")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public SgiCommitment refreshValue(@PathVariable Long id) {
        return service.refreshValue(id);
    }

    /**
     * SGI-B — manual surveillance sweep for the current tenant: raises the
     * behind-plan (DELAYED) and overdue-milestone auto-CAPAs. Idempotent; also
     * runs daily at 03:00 UTC. Mirrors the horizon trajectory/permit sweeps.
     */
    @PostMapping("/sweep")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public Map<String, Integer> sweep() {
        var r = surveillance.sweep();
        return Map.of("delayedCapas", r.delayedCapas(), "milestoneCapas", r.milestoneCapas());
    }
}
