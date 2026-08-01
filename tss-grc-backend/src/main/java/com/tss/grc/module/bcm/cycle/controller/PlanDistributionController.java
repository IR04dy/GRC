package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.BcpPlanAcknowledgement;
import com.tss.grc.module.bcm.cycle.service.PlanDistributionService;
import com.tss.grc.module.bcm.cycle.service.PlanDistributionService.DistributionStats;
import com.tss.grc.module.bcm.cycle.service.PlanDistributionService.Recipient;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for plan distribution + acknowledgement — audit Tier-2 #11.
 *
 * <pre>
 *   GET    /api/v1/bcm/plans/{planId}/distribution           list all acks (any version)
 *   GET    /api/v1/bcm/plans/{planId}/distribution/stats     counts + ack% at current version
 *   POST   /api/v1/bcm/plans/{planId}/distribution           distribute to N recipients
 *   POST   /api/v1/bcm/plans/distribution/{ackId}/acknowledge
 *   POST   /api/v1/bcm/plans/distribution/{ackId}/decline
 *   POST   /api/v1/bcm/plans/{planId}/distribution/mark-stale  (admin / lifecycle hook)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm/plans")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Plan distribution & acknowledgement (Tier-2 #11)")
public class PlanDistributionController {

    private final PlanDistributionService service;

    private static String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    /** The caller's user id, used to prove they are the designated recipient. */
    private static Long actorUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof GrcUserDetails u) ? u.getUserId() : null;
    }

    /**
     * BCM WRITE holders may acknowledge/decline on a recipient's behalf (recorded as
     * such in the audit trail). Everyone else may only act on their own distribution
     * row — enforced in the service, since it needs the row to know the recipient.
     */
    private static boolean canActOnBehalf() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream().map(Object::toString)
            .anyMatch(x -> "BCM_WRITE".equals(x) || "ROLE_PLATFORM_ADMIN".equals(x));
    }

    public record DistributeBody(
        List<Recipient> recipients,
        String channel,
        String notes
    ) {}

    public record AckBody(String notes) {}

    @GetMapping("/{planId}/distribution")
    @PreAuthorize("isAuthenticated()")
    public List<BcpPlanAcknowledgement> list(@PathVariable Long planId) {
        return service.listForPlan(planId);
    }

    @GetMapping("/{planId}/distribution/stats")
    @PreAuthorize("isAuthenticated()")
    public DistributionStats stats(@PathVariable Long planId) {
        return service.statsFor(planId);
    }

    @PostMapping("/{planId}/distribution")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public List<BcpPlanAcknowledgement> distribute(@PathVariable Long planId,
                                                   @RequestBody DistributeBody body) {
        return service.distribute(planId, body.recipients(), body.channel(), actor(), body.notes());
    }

    @PostMapping("/distribution/{ackId}/acknowledge")
    @PreAuthorize("isAuthenticated()")
    public BcpPlanAcknowledgement acknowledge(@PathVariable Long ackId,
                                              @RequestBody(required = false) AckBody body) {
        return service.acknowledge(ackId, body == null ? null : body.notes(), actor(),
                                   actorUserId(), canActOnBehalf());
    }

    @PostMapping("/distribution/{ackId}/decline")
    @PreAuthorize("isAuthenticated()")
    public BcpPlanAcknowledgement decline(@PathVariable Long ackId,
                                          @RequestBody(required = false) AckBody body) {
        return service.decline(ackId, body == null ? null : body.notes(), actor(),
                               actorUserId(), canActOnBehalf());
    }

    @PostMapping("/{planId}/distribution/mark-stale")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public int markStale(@PathVariable Long planId) {
        return service.markStale(planId);
    }
}
