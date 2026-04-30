package com.tss.grc.module.tprm.controller;

import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.entity.VendorAssessment;
import com.tss.grc.module.tprm.service.TprmService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tprm")
@RequiredArgsConstructor
@Tag(name = "TPRM")
@SecurityRequirement(name = "bearerAuth")
public class TprmController {

    private final TprmService tprmService;

    // ── Vendors ───────────────────────────────────────────────────────────────

    @GetMapping("/vendors")
    @PreAuthorize("hasPermission('TPRM', 'READ')")
    public Page<Vendor> listVendors(@PageableDefault(size = 20) Pageable pageable) {
        return tprmService.listVendors(pageable);
    }

    @GetMapping("/vendors/{id}")
    @PreAuthorize("hasPermission('TPRM', 'READ')")
    public Vendor getVendor(@PathVariable Long id) {
        return tprmService.getVendor(id);
    }

    @PostMapping("/vendors")
    @PreAuthorize("hasPermission('TPRM', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public Vendor createVendor(@RequestBody Vendor req) {
        return tprmService.createVendor(req);
    }

    @PutMapping("/vendors/{id}")
    @PreAuthorize("hasPermission('TPRM', 'WRITE')")
    public Vendor updateVendor(@PathVariable Long id, @RequestBody Vendor req) {
        return tprmService.updateVendor(id, req);
    }

    @DeleteMapping("/vendors/{id}")
    @PreAuthorize("hasPermission('TPRM', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVendor(@PathVariable Long id) {
        tprmService.deleteVendor(id);
    }

    // ── Assessments ───────────────────────────────────────────────────────────

    @GetMapping("/assessments")
    @PreAuthorize("hasPermission('TPRM', 'READ')")
    public List<VendorAssessment> listAssessments(@RequestParam(required = false) Long vendorId) {
        return tprmService.listAssessments(vendorId);
    }

    @PostMapping("/assessments")
    @PreAuthorize("hasPermission('TPRM', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public VendorAssessment createAssessment(@RequestBody VendorAssessment req) {
        return tprmService.createAssessment(req);
    }

    @PutMapping("/assessments/{id}")
    @PreAuthorize("hasPermission('TPRM', 'WRITE')")
    public VendorAssessment updateAssessment(@PathVariable Long id, @RequestBody VendorAssessment req) {
        return tprmService.updateAssessment(id, req);
    }

    @DeleteMapping("/assessments/{id}")
    @PreAuthorize("hasPermission('TPRM', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssessment(@PathVariable Long id) {
        tprmService.deleteAssessment(id);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('TPRM', 'READ')")
    public Map<String, Long> getStats() {
        return tprmService.getStats();
    }
}
