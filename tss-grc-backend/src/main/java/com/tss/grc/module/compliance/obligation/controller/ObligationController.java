package com.tss.grc.module.compliance.obligation.controller;

import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationCreateRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationKpis;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationResponse;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationTransitionRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationUpdateRequest;
import com.tss.grc.module.compliance.obligation.service.ObligationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sprint 11 — REST surface for the regulatory obligation register.
 *
 * <pre>
 *   GET    /api/v1/compliance/obligations
 *   GET    /api/v1/compliance/obligations/kpis
 *   GET    /api/v1/compliance/obligations/due?days=30
 *   GET    /api/v1/compliance/obligations/by-regulator/{regulator}
 *   GET    /api/v1/compliance/obligations/by-owner/{ownerUserId}
 *   GET    /api/v1/compliance/obligations/{id}
 *   POST   /api/v1/compliance/obligations
 *   PATCH  /api/v1/compliance/obligations/{id}
 *   POST   /api/v1/compliance/obligations/{id}/transition
 *   DELETE /api/v1/compliance/obligations/{id}
 * </pre>
 *
 * <p>Read access is broad (every audit-readiness reader) so the
 * register can be wall-mounted on every dashboard. Writes are gated
 * to the Compliance Officer + Platform Admin.
 */
@RestController
@RequestMapping("/api/v1/compliance/obligations")
@RequiredArgsConstructor
@Tag(name = "Compliance — Obligation Register (Sprint 11)")
@SecurityRequirement(name = "bearerAuth")
public class ObligationController {

    private final ObligationService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<ObligationResponse> list() {
        return service.list();
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public ObligationKpis kpis() {
        return service.kpis();
    }

    @GetMapping("/due")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<ObligationResponse> due(@RequestParam(name = "days", defaultValue = "30") int days) {
        return service.listDueWithinDays(days);
    }

    @GetMapping("/by-regulator/{regulator}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<ObligationResponse> byRegulator(@PathVariable String regulator) {
        return service.listByRegulator(regulator);
    }

    @GetMapping("/by-owner/{ownerUserId}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<ObligationResponse> byOwner(@PathVariable Long ownerUserId) {
        return service.listByOwner(ownerUserId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public ObligationResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public ObligationResponse create(@RequestBody @Valid ObligationCreateRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public ObligationResponse update(@PathVariable Long id,
                                      @RequestBody @Valid ObligationUpdateRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public ObligationResponse transition(@PathVariable Long id,
                                          @RequestBody @Valid ObligationTransitionRequest req) {
        return service.transition(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public void delete(@PathVariable Long id) {
        service.softDelete(id);
    }
}
