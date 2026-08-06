package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.service.CrisisEventService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Slice 9-F — spec-aligned mobile surface for the Crisis Commander.
 *
 * <pre>
 *   GET /api/v1/bcm/crisis/active-summary    — same payload as
 *                                              /api/v1/bcm/crisis-events/active-summary
 *                                              but published under the cleaner
 *                                              "crisis" namespace the mobile
 *                                              client uses.
 * </pre>
 *
 * <p>This thin façade keeps the desktop client on the existing
 * {@code crisis-events} URL family while letting the mobile client live
 * under {@code /crisis/*}. The handler delegates straight to
 * {@link CrisisEventService#getActiveCrisisSummary()}.
 */
@RestController
@RequestMapping("/api/v1/bcm/crisis")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Mobile Crisis Room (Slice 9-F)")
@SecurityRequirement(name = "bearerAuth")
public class MobileCrisisController {

    private final CrisisEventService crisisEventService;

    /**
     * F2-52 — the guard is <b>referenced</b>, not restated. It used to be a copy of
     * {@code CrisisEventController}'s, ghost names and all: {@code CRISIS_COMMANDER} and
     * {@code INCIDENT_COMMANDER} exist in no database, and the real {@code CRISIS_MANAGER} role was
     * excluded from the crisis room. A façade that duplicates its twin's guard string is a guard that
     * will eventually disagree with itself, and the mobile URL is the copy nobody re-reads.
     */
    @GetMapping("/active-summary")
    @PreAuthorize(CrisisEventController.CRISIS_ROOM_GUARD)
    public List<CrisisEventService.ActiveCrisisSummaryRow> activeSummary() {
        return crisisEventService.getActiveCrisisSummary();
    }
}
