package com.tss.grc.module.aml.sanctions.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.DashboardResponse;
import com.tss.grc.module.aml.sanctions.repository.ScreeningCheckRepository;
import com.tss.grc.module.aml.sanctions.repository.ScreeningHitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 13 — Sanctions screening KPI dashboard service.
 *
 * <p>Returns a single snapshot consumed by the dashboard page. False-
 * positive rate = {@code false_positive_count / (true_positive +
 * false_positive)}, null when no positives have been adjudicated yet.
 */
@Service
@RequiredArgsConstructor
public class SanctionsDashboardService {

    private final ScreeningCheckRepository checkRepo;
    private final ScreeningHitRepository hitRepo;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        Long orgId = TenantContext.requireOrganisationId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(24);

        long checks24h = checkRepo.countByOrganisationIdAndCheckAtAfterAndIsDeletedFalse(orgId, since);
        long totalChecks = checkRepo.countByOrganisationIdAndIsDeletedFalse(orgId);

        long pending = hitRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(orgId, "PENDING_REVIEW");
        long truePos = hitRepo.countByOrganisationIdAndStatusInAndIsDeletedFalse(orgId,
            List.of("TRUE_POSITIVE", "BLOCK_RECOMMENDED"));
        long falsePos = hitRepo.countByOrganisationIdAndStatusInAndIsDeletedFalse(orgId,
            List.of("FALSE_POSITIVE", "RELEASE_RECOMMENDED"));
        long escalated = hitRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(orgId, "ESCALATED");
        long slaBreached = hitRepo.countByOrganisationIdAndSlaBreachTrueAndIsDeletedFalse(orgId);

        Double fpRate = null;
        long adjudicated = truePos + falsePos;
        if (adjudicated > 0) {
            fpRate = ((double) falsePos / adjudicated);
        }
        return new DashboardResponse(
            checks24h, totalChecks,
            pending, truePos, falsePos, escalated, slaBreached,
            fpRate, now
        );
    }
}
