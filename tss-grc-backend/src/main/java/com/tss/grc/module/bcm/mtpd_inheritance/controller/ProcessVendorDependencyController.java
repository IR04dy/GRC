package com.tss.grc.module.bcm.mtpd_inheritance.controller;

import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.DependencyCreateRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.DependencyResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.DependencyUpdateRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.service.MtpdInheritanceService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Slice 10-E — CRUD surface for process ↔ vendor dependency edges.
 *
 * <p>The BCM Manager + TPRM Lead curate these as part of the BIA
 * cycle. Once on file, the MTPD-inheritance compute engine joins
 * them against the Slice 10-D vendor attestation data.
 *
 * <pre>
 *   GET    /api/v1/bcm/process-vendor-dependencies          — list
 *   GET    /api/v1/bcm/process-vendor-dependencies/{id}     — read
 *   POST   /api/v1/bcm/process-vendor-dependencies          — create
 *   PUT    /api/v1/bcm/process-vendor-dependencies/{id}     — update
 *   DELETE /api/v1/bcm/process-vendor-dependencies/{id}     — soft-delete
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm/process-vendor-dependencies")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Process ↔ Vendor Dependencies (Slice 10-E)")
@SecurityRequirement(name = "bearerAuth")
public class ProcessVendorDependencyController {

    private final MtpdInheritanceService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('BCM_MANAGER','RISK_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN')")
    public List<DependencyResponse> list() {
        return service.listDependencies();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','RISK_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN')")
    public DependencyResponse get(@PathVariable Long id) {
        return service.getDependency(id);
    }

    // F2-254: was hasAnyRole('BCM_MANAGER','PLATFORM_ADMIN') and BCM_MANAGER is held by NO user
    // in dev or prod - a role guard never reaches ModulePermissionEvaluator, so it never reaches
    // the PLATFORM_ADMIN bypass either; this passed only because it happened to NAME the
    // administrator. Audience diff measured first: both names hold BCM:WRITE, so nobody is dropped
    // and GRC_ADMINISTRATOR + RISK_MANAGER are admitted. AUDITOR and VIEWER hold READ+EXPORT only.
    // The READ guards here keep their role lists: CISO is named on them and holds only BCM:EXPORT.
    @PostMapping
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public DependencyResponse create(@RequestBody @Valid DependencyCreateRequest req) {
        return service.createDependency(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public DependencyResponse update(@PathVariable Long id,
                                      @RequestBody @Valid DependencyUpdateRequest req) {
        return service.updateDependency(id, req);
    }

    // F2-254: DELETE rather than WRITE, per this module's own convention (BcmController and
    // CrisisCommsTemplateController both use hasPermission('BCM','DELETE') on delete verbs).
    // It is also the narrower audience, so it cannot over-widen: RISK_MANAGER holds WRITE and
    // not DELETE, and stays refused here - asserted, because collapsing the two would be the
    // silent half of this conversion.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('BCM','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteDependency(id);
        return ResponseEntity.noContent().build();
    }
}
