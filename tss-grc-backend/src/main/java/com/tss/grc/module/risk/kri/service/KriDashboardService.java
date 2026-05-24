package com.tss.grc.module.risk.kri.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriBreachResponse;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriDashboardCell;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriDashboardSnapshot;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriHistoryPoint;
import com.tss.grc.module.risk.kri.entity.KriBreach;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.kri.entity.KriMeasurement;
import com.tss.grc.module.risk.kri.repository.KriBreachRepository;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * KRI dashboard projections — Sprint 13.
 *
 * <p>Two query shapes:
 * <ul>
 *   <li>{@link #snapshot()} — heatmap + KPI tiles for {@code /risk/kri/dashboard}.</li>
 *   <li>{@link #history(UUID, int)} — N-day trend points for the
 *       sparkline on the KRI detail page.</li>
 * </ul>
 *
 * <p>Kept separate from {@link KriDefinitionService} because the
 * dashboard does heavy bulk-loading and is a read-only surface — no
 * F7/F8 emission, no transactional boundaries that mutate.
 */
@Service
@RequiredArgsConstructor
public class KriDashboardService {

    private final KriDefinitionRepository defRepo;
    private final KriMeasurementRepository measRepo;
    private final KriBreachRepository breachRepo;

    @Transactional(readOnly = true)
    public KriDashboardSnapshot snapshot() {
        Long orgId = TenantContext.getCurrentTenant();
        List<KriDefinition> defs = defRepo
                .findByOrganisationIdAndIsDeletedFalseOrderByKriCodeAsc(orgId);

        long active = defs.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive())).count();

        long green = 0, amber = 0, red = 0, na = 0;
        List<KriDashboardCell> cells = new java.util.ArrayList<>(defs.size());

        // Open-breach lookup: load once per tenant rather than N+1.
        List<KriBreach> openBreaches = breachRepo
                .findByOrganisationIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(orgId);
        java.util.Set<Long> krisWithOpenBreach = openBreaches.stream()
                .map(KriBreach::getKriId).collect(Collectors.toSet());

        for (KriDefinition d : defs) {
            KriMeasurement latest = measRepo
                    .findFirstByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(
                            orgId, d.getId())
                    .orElse(null);
            String status = latest == null ? "NA" : latest.getStatus();
            switch (status) {
                case "GREEN" -> green++;
                case "AMBER" -> amber++;
                case "RED"   -> red++;
                default       -> na++;
            }
            cells.add(new KriDashboardCell(
                    d.getExternalId(), d.getKriCode(),
                    d.getTitleEn(), d.getTitleAr(),
                    d.getCategory(),
                    status,
                    latest == null ? null : latest.getValue(),
                    d.getMeasurementUnit(),
                    latest == null ? null : latest.getMeasuredAt(),
                    krisWithOpenBreach.contains(d.getId())));
        }

        long amberOpen = breachRepo.countByOrganisationIdAndBreachLevelAndClosedAtIsNullAndIsDeletedFalse(
                orgId, "AMBER");
        long redOpen = breachRepo.countByOrganisationIdAndBreachLevelAndClosedAtIsNullAndIsDeletedFalse(
                orgId, "RED");

        // Bulk-load matching definitions for the open-breaches list
        // so the response carries titles without N+1.
        Map<Long, KriDefinition> defById = defs.stream()
                .collect(Collectors.toMap(KriDefinition::getId, x -> x, (a, b) -> a));
        List<KriBreachResponse> breachResponses = openBreaches.stream()
                .map(b -> KriBreachResponse.from(b, defById.get(b.getKriId())))
                .toList();

        return new KriDashboardSnapshot(
                defs.size(), active,
                green, amber, red, na,
                amberOpen, redOpen,
                cells, breachResponses);
    }

    @Transactional(readOnly = true)
    public List<KriHistoryPoint> history(UUID kriExternalId, int days) {
        Long orgId = TenantContext.getCurrentTenant();
        KriDefinition d = defRepo
                .findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, kriExternalId)
                .orElseThrow(() -> new EntityNotFoundException("KriDefinition not found: " + kriExternalId));
        int clampedDays = Math.max(1, Math.min(days, 365 * 2));
        LocalDateTime since = LocalDateTime.now().minusDays(clampedDays);
        return measRepo.findRangeForKri(orgId, d.getId(), since).stream()
                .map(KriHistoryPoint::from)
                .toList();
    }
}
