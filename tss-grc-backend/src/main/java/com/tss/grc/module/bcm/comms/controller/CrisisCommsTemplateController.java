package com.tss.grc.module.bcm.comms.controller;

import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.ApprovalTransitionRequest;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.TemplateRequest;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.TemplateResponse;
import com.tss.grc.module.bcm.comms.service.CrisisCommsTemplateService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Slice 9-E — REST surface for the v2 crisis-comms template register.
 *
 * <pre>
 *   GET    /api/v1/bcm/comms/v2/templates                     — list (filterable)
 *   GET    /api/v1/bcm/comms/v2/templates/{externalId}        — detail
 *   POST   /api/v1/bcm/comms/v2/templates                     — create
 *   PUT    /api/v1/bcm/comms/v2/templates/{externalId}        — update
 *   POST   /api/v1/bcm/comms/v2/templates/{externalId}/transition — approval state machine
 *   DELETE /api/v1/bcm/comms/v2/templates/{externalId}        — archive
 * </pre>
 *
 * <p>The {@code /v2} prefix differentiates this from the legacy v1
 * register at {@code /api/v1/bcm/comms/templates} (V18 schema).
 *
 * <p>Read side is wide (BCM_MANAGER, CISO, COMMS_LEAD, PLATFORM_ADMIN,
 * AUDITOR, CRISIS_COMMANDER). Write side is tighter (CISO, COMMS_LEAD,
 * PLATFORM_ADMIN, LEGAL_COUNSEL).
 */
@RestController
@RequestMapping("/api/v1/bcm/comms/v2/templates")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Crisis Communications Templates v2 (Slice 9-E)")
@SecurityRequirement(name = "bearerAuth")
public class CrisisCommsTemplateController {

    private final CrisisCommsTemplateService service;

    @GetMapping
    @PreAuthorize("hasPermission('BCM','READ')")
    public List<TemplateResponse> list(
        @RequestParam(value = "audience",         required = false) String audience,
        @RequestParam(value = "scenario",         required = false) String scenario,
        @RequestParam(value = "approvalStatus",   required = false) String approvalStatus
    ) {
        if (audience != null && scenario != null) {
            return service.findByAudienceAndScenario(audience, scenario);
        }
        if (audience != null) {
            return service.listByAudience(audience);
        }
        if (approvalStatus != null) {
            return service.listByApprovalStatus(approvalStatus);
        }
        return service.list();
    }

    @GetMapping("/{externalId}")
    @PreAuthorize("hasPermission('BCM','READ')")
    public TemplateResponse get(@PathVariable UUID externalId) {
        return service.get(externalId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public TemplateResponse create(@RequestBody @Valid TemplateRequest req) {
        return service.create(req);
    }

    @PutMapping("/{externalId}")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public TemplateResponse update(@PathVariable UUID externalId,
                                    @RequestBody @Valid TemplateRequest req) {
        return service.update(externalId, req);
    }

    @PostMapping("/{externalId}/transition")
    @PreAuthorize("hasPermission('BCM','APPROVE')")
    public TemplateResponse transition(@PathVariable UUID externalId,
                                        @RequestBody @Valid ApprovalTransitionRequest req) {
        return service.transition(externalId, req);
    }

    @DeleteMapping("/{externalId}")
    @PreAuthorize("hasPermission('BCM','DELETE')")
    public ResponseEntity<TemplateResponse> archive(@PathVariable UUID externalId) {
        return ResponseEntity.ok(service.archive(externalId));
    }
}
