package com.tss.grc.module.iam.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.governance.entity.AccessReviewCampaign;
import com.tss.grc.module.iam.governance.repository.AccessReviewCampaignRepository;
import com.tss.grc.module.iam.governance.repository.AccessReviewItemRepository;
import com.tss.grc.module.iam.governance.repository.BreakGlassEventRepository;
import com.tss.grc.module.iam.governance.repository.SodViolationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * RBAC Access-Governance P3 (plan §6.7) — the access-analytics dashboard payload:
 * privileged-holder count, dormant grants (no login ≥90d), grants expiring ≤30d,
 * open SoD violations, campaign completion %, top grant holders (over-privilege
 * candidates), and the break-glass posture (active / awaiting post-hoc review).
 */
@Service
@RequiredArgsConstructor
public class AccessAnalyticsService {

    private final BreakGlassEventRepository breakGlass;
    private final SodViolationRepository sodViolations;
    private final AccessReviewCampaignRepository campaigns;
    private final AccessReviewItemRepository items;

    public record GrantHolder(String username, long grants) {}
    public record CampaignCompletion(Long id, String titleEn, String status, long total,
                                     long decided, double completionPct) {}
    public record AccessAnalytics(
        long privilegedHolders,
        long dormantGrants,
        long grantsExpiring30d,
        long openSodViolations,
        long breakGlassActive,
        long breakGlassAwaitingReview,
        long platformAdminActions30d,
        List<CampaignCompletion> campaignCompletion,
        List<GrantHolder> topGrantHolders) {}

    @Transactional(readOnly = true)
    public AccessAnalytics analytics() {
        Long org = TenantContext.requireOrganisationId();

        List<CampaignCompletion> completion = new ArrayList<>();
        for (AccessReviewCampaign c : campaigns.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(org)) {
            long total = items.countByCampaignIdAndIsDeletedFalse(c.getId());
            long pending = items.countByCampaignIdAndDecisionAndIsDeletedFalse(c.getId(), "PENDING");
            long decided = total - pending;
            double pct = total == 0 ? 100.0
                : BigDecimal.valueOf(decided * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
            completion.add(new CampaignCompletion(c.getId(), c.getTitleEn(), c.getStatus(), total, decided, pct));
        }

        List<GrantHolder> top = breakGlass.topGrantHolders(org).stream()
            .map(r -> new GrantHolder(String.valueOf(r[0]), ((Number) r[1]).longValue()))
            .toList();

        return new AccessAnalytics(
            breakGlass.countPrivilegedHolders(org),
            breakGlass.countDormantGrants(org),
            breakGlass.countExpiringSoon(org),
            sodViolations.countByOrganisationIdAndStatusAndIsDeletedFalse(org, "OPEN"),
            breakGlass.countByOrganisationIdAndStatusAndIsDeletedFalse(org, "ACTIVE"),
            breakGlass.countByOrganisationIdAndReviewStatusAndIsDeletedFalse(org, "PENDING_REVIEW"),
            breakGlass.countPlatformAdminActions30d(org),
            completion,
            top);
    }
}
