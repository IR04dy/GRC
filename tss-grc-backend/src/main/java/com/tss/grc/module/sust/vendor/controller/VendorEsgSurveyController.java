package com.tss.grc.module.sust.vendor.controller;

import com.tss.grc.module.sust.evidence.service.SustEvidenceLinkService;
import com.tss.grc.module.sust.vendor.dto.VendorEsgResponseRequest;
import com.tss.grc.module.sust.vendor.dto.VendorEsgSurveyCreateRequest;
import com.tss.grc.module.sust.vendor.entity.VendorEsgSurvey;
import com.tss.grc.module.sust.vendor.service.VendorEsgSurveyService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Vendor ESG surveillance REST endpoints —
 * {@code /api/sustainability/vendor-surveys/*} (S-O).
 *
 * <pre>
 *   GET    /?year=2025&status=SENT      list (filter by year / status)
 *   GET    /aggregate                   KPI rollup (spend coverage, avg score, escalated…)
 *   GET    /:id                         fetch one
 *   POST   /                            create (validated vendor link)
 *   POST   /:id/send                    DRAFT → SENT (issue the data request)
 *   POST   /:id/record-response         SENT|ESCALATED → RESPONDED (+ computed score)
 *   POST   /:id/accept                  RESPONDED → ACCEPTED
 *   POST   /:id/reject   {reason}       RESPONDED → REJECTED (+ auto-CAPA)
 *   POST   /:id/link-evidence {evidenceItemId, notes}   attach real evidence (S-C bridge)
 *   POST   /sweep-overdue               escalate past-due non-responses (+ auto-CAPA)
 *   DELETE /:id                         soft-delete
 * </pre>
 *
 * Every route is guarded by {@code hasPermission('SUST',…)} (S-A) — the same
 * scheme as the rest of the module, so a non-admin {@code SUSTAINABILITY_OFFICER}
 * operates it end-to-end.
 */
@RestController
@RequestMapping("/api/sustainability/vendor-surveys")
@RequiredArgsConstructor
@Tag(name = "Sustainability — Vendor ESG Surveillance")
public class VendorEsgSurveyController {

    private final VendorEsgSurveyService service;
    private final SustEvidenceLinkService evidenceLink;

    @GetMapping
    @PreAuthorize("hasPermission('SUST','READ')")
    public Page<VendorEsgSurvey> list(
            @RequestParam(required = false) Short year,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        if (status != null) return service.listByStatus(status, p);
        if (year != null)   return service.listByYear(year, p);
        return service.list(p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> aggregate() { return service.aggregate(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public VendorEsgSurvey get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<VendorEsgSurvey> create(@Valid @RequestBody VendorEsgSurveyCreateRequest req) {
        return ResponseEntity.status(201).body(service.create(req.toEntity()));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public VendorEsgSurvey send(@PathVariable Long id) { return service.send(id); }

    @PostMapping("/{id}/record-response")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public VendorEsgSurvey recordResponse(@PathVariable Long id, @Valid @RequestBody VendorEsgResponseRequest req) {
        return service.recordResponse(id, req);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public VendorEsgSurvey accept(@PathVariable Long id, @AuthenticationPrincipal GrcUserDetails me) {
        return service.accept(id, me);
    }

    /** Body: {@code { "reason": "…" }} — optional. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public VendorEsgSurvey reject(@PathVariable Long id, @AuthenticationPrincipal GrcUserDetails me,
                                  @RequestBody(required = false) Map<String, String> body) {
        return service.reject(id, me, body == null ? null : body.get("reason"));
    }

    /** Body: {@code { "evidenceItemId": 12, "notes": "…" }} — attaches a real, hashed evidence item (S-C). */
    @PostMapping("/{id}/link-evidence")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public Map<String, Object> linkEvidence(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object rawId = body.get("evidenceItemId");
        if (rawId == null) throw new IllegalArgumentException("evidenceItemId required");
        Long evidenceItemId = Long.valueOf(rawId.toString());
        Object notes = body.get("notes");
        return evidenceLink.link("VendorEsgSurvey", id, evidenceItemId, notes == null ? null : notes.toString());
    }

    /** Manual non-response escalation trigger (also runs daily on a schedule). */
    @PostMapping("/sweep-overdue")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public Map<String, Object> sweepOverdue() {
        List<VendorEsgSurvey> escalated = service.sweepOverdue();
        return Map.of("escalated", escalated.size(),
                      "ids", escalated.stream().map(VendorEsgSurvey::getId).toList());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
