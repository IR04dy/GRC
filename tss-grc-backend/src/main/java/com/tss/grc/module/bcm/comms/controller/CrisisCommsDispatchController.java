package com.tss.grc.module.bcm.comms.controller;

import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.CascadeResponse;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.DispatchRequest;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.DispatchResult;
import com.tss.grc.module.bcm.comms.service.CrisisCommsDispatchService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Slice 9-E — REST surface for cascade dispatch + history.
 *
 * <pre>
 *   POST /api/v1/bcm/comms/v2/dispatch                   — fire a cascade
 *   GET  /api/v1/bcm/comms/v2/cascades                   — all cascades
 *   GET  /api/v1/bcm/comms/v2/cascades?crisisEventId=N   — per-event timeline
 *   GET  /api/v1/bcm/comms/v2/cascades/{externalId}      — one row
 * </pre>
 *
 * <p>Dispatch is restricted to operators who can actually pull the trigger
 * during an incident — CISO, CRISIS_COMMANDER, COMMS_LEAD, PLATFORM_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/bcm/comms/v2")
@RequiredArgsConstructor
@Tag(name = "BCM — Crisis Communications Dispatch (Slice 9-E)")
@SecurityRequirement(name = "bearerAuth")
public class CrisisCommsDispatchController {

    private final CrisisCommsDispatchService service;

    @PostMapping("/dispatch")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CISO','CRISIS_COMMANDER','COMMS_LEAD','PLATFORM_ADMIN')")
    public DispatchResult dispatch(@RequestBody @Valid DispatchRequest req) {
        return service.dispatch(req);
    }

    @GetMapping("/cascades")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','COMMS_LEAD','PLATFORM_ADMIN','AUDITOR','CRISIS_COMMANDER')")
    public List<CascadeResponse> list(
        @RequestParam(value = "crisisEventId", required = false) Long crisisEventId
    ) {
        return crisisEventId == null
            ? service.listAll()
            : service.listForCrisisEvent(crisisEventId);
    }

    @GetMapping("/cascades/{externalId}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','COMMS_LEAD','PLATFORM_ADMIN','AUDITOR','CRISIS_COMMANDER')")
    public CascadeResponse get(@PathVariable UUID externalId) {
        return service.get(externalId);
    }
}
