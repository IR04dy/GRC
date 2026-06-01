package com.tss.grc.module.dashboard.service;

import com.tss.grc.module.compliance.repository.ComplianceAssessmentRepository;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.dashboard.dto.ChartData;
import com.tss.grc.module.dashboard.dto.DashboardStats;
import com.tss.grc.module.dashboard.dto.HeatmapData;
import com.tss.grc.module.dashboard.dto.TrendPoint;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.incident.repository.IncidentRepository;
import com.tss.grc.module.risk.dto.RiskResponse;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RiskRepository riskRepository;
    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository;
    private final ControlRepository controlRepository;
    private final ComplianceAssessmentRepository assessmentRepository;

    @PersistenceContext
    private EntityManager em;

    @Cacheable(value = "dashboardStats", key = "#organisationId")
    @Transactional(readOnly = true)
    public DashboardStats getStats(Long organisationId) {
        long critical = riskRepository.countByOrganisationIdAndRiskLevel(organisationId, "CRITICAL");
        long high     = riskRepository.countByOrganisationIdAndRiskLevel(organisationId, "HIGH");
        long medium   = riskRepository.countByOrganisationIdAndRiskLevel(organisationId, "MEDIUM");
        long low      = riskRepository.countByOrganisationIdAndRiskLevel(organisationId, "LOW");
        long total    = critical + high + medium + low;
        long open     = riskRepository.countByOrganisationIdAndStatus(organisationId, "OPEN");
        long closed   = riskRepository.countByOrganisationIdAndStatus(organisationId, "CLOSED");
        long activeUsers = userRepository.countByOrganisationIdAndIsActiveTrue(organisationId);
        long totalUsers  = userRepository.findByOrganisationId(organisationId, PageRequest.of(0, 1)).getTotalElements();

        // Incident stats (Sprint 2)
        long openIncidents = incidentRepository.countByOrganisationIdAndStatus(organisationId, "OPEN")
                           + incidentRepository.countByOrganisationIdAndStatus(organisationId, "INVESTIGATING");

        // Compliance stats (Sprint 2)
        Double avgScore = assessmentRepository.avgScoreByOrg(organisationId);
        long complianceScore = avgScore != null ? Math.round(avgScore) : 0L;

        // Overdue audit findings — past due_date and not in a closed state.
        long overdueItems = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM compliance.findings "
                        + "WHERE organisation_id = :org AND is_deleted = 0 "
                        + "AND due_date IS NOT NULL AND due_date < :today "
                        + "AND status NOT IN ('CLOSED','RESOLVED','REMEDIATED','DONE','VERIFIED')")
                .setParameter("org", organisationId)
                .setParameter("today", LocalDate.now())
                .getSingleResult()).longValue();

        return new DashboardStats(total, critical, high, medium, low, open, closed, totalUsers, activeUsers,
                critical, high, openIncidents, complianceScore, 0L, overdueItems);
    }

    @Cacheable(value = "heatmapData", key = "#organisationId")
    @Transactional(readOnly = true)
    public HeatmapData getHeatmap(Long organisationId) {
        List<Object[]> rows = riskRepository.countByHeatmapCell(organisationId);
        List<HeatmapData.HeatmapCell> cells = new ArrayList<>();
        for (Object[] row : rows) {
            int likelihood = row[0] != null ? ((Number) row[0]).intValue() : 0;
            int impact     = row[1] != null ? ((Number) row[1]).intValue() : 0;
            long count     = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            cells.add(new HeatmapData.HeatmapCell(likelihood, impact, count));
        }
        return new HeatmapData(cells);
    }

    @Cacheable(value = "riskByCategory", key = "#organisationId")
    @Transactional(readOnly = true)
    public ChartData getRiskByCategory(Long organisationId) {
        List<Object[]> rows = riskRepository.countByCategory(organisationId);
        List<String> labels = new ArrayList<>();
        List<Long> values   = new ArrayList<>();
        for (Object[] row : rows) {
            labels.add(row[0] != null ? row[0].toString() : "Unknown");
            values.add(row[1] != null ? ((Number) row[1]).longValue() : 0L);
        }
        return new ChartData(labels, values);
    }

    @Transactional(readOnly = true)
    public List<RiskResponse> getRecentRisks(Long organisationId) {
        List<Risk> risks = riskRepository.findTop5ByOrganisationIdOrderByCreatedAtDesc(organisationId, PageRequest.of(0, 5));
        return risks.stream().map(r -> new RiskResponse(
            r.getId(), r.getExternalId(),
            "RISK-" + String.format("%04d", r.getId()),
            r.getTitleEn(), r.getTitleAr(), r.getDescriptionEn(), r.getDescriptionAr(),
            r.getCategoryId(), null, null, null,
            r.getOwnerId(), null, null, null,
            r.getStatus(),
            r.getInherentLikelihood(), r.getInherentImpact(), r.getInherentScore(),
            r.getResidualLikelihood(), r.getResidualImpact(), r.getResidualScore(),
            r.getRiskLevel(), r.getIdentifiedDate(), r.getReviewDate(),
            r.getCreatedAt(), r.getUpdatedAt()
        )).toList();
    }

    // ── KPI trend (real weekly series from entity timestamps) ───────────────
    //
    // Replaces the synthetic Math.random/Math.sin sparklines on the dashboards
    // with a genuine series: for each of the last `weeks` ISO weeks, the
    // cumulative count of still-open items created on or before that week.
    // No snapshot table needed — derived from created_at + current status.

    private record MetricSource(String table, String openFilter, String extra) {}

    private static final Map<String, MetricSource> METRICS = Map.ofEntries(
        Map.entry("risks",          new MetricSource("risk.risks",     " AND status <> 'CLOSED'", "")),
        Map.entry("open_risks",     new MetricSource("risk.risks",     " AND status <> 'CLOSED'", "")),
        Map.entry("critical_risks", new MetricSource("risk.risks",     " AND status <> 'CLOSED'", " AND risk_level = 'CRITICAL'")),
        Map.entry("high_risks",     new MetricSource("risk.risks",     " AND status <> 'CLOSED'", " AND risk_level = 'HIGH'")),
        Map.entry("incidents",      new MetricSource("cyber.incidents", " AND status NOT IN ('CLOSED','RESOLVED')", "")),
        Map.entry("open_incidents", new MetricSource("cyber.incidents", " AND status NOT IN ('CLOSED','RESOLVED')", "")),
        // Compliance / audit findings still open (not closed or remediated).
        Map.entry("findings",       new MetricSource("compliance.findings",
                " AND status NOT IN ('CLOSED','RESOLVED','REMEDIATED','VERIFIED','DONE')", "")),
        // PDPL data-subject requests still in flight.
        Map.entry("dsr",            new MetricSource("privacy.data_subject_requests",
                " AND status NOT IN ('COMPLETED','RESOLVED','REJECTED','CLOSED')", "")),
        // Privacy breaches not yet closed out.
        Map.entry("breaches",       new MetricSource("privacy.data_breaches",
                " AND status NOT IN ('CLOSED','RESOLVED','REMEDIATED')", "")),
        // Third-party / supplier vendors on the books (excludes terminated).
        Map.entry("vendors",        new MetricSource("scm.vendors", " AND status <> 'TERMINATED'", ""))
    );

    @Transactional(readOnly = true)
    public List<TrendPoint> getKpiTrend(Long orgId, String metric, int weeks) {
        int n = Math.max(2, Math.min(52, weeks));
        MetricSource src = METRICS.getOrDefault(metric == null ? "risks" : metric.toLowerCase(),
                METRICS.get("risks"));
        LocalDate windowStart = LocalDate.now()
                .minusWeeks(n - 1L)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        String base = " FROM " + src.table()
                + " WHERE organisation_id = :org AND is_deleted = 0" + src.openFilter() + src.extra();

        long baseline = ((Number) em.createNativeQuery(
                "SELECT COUNT(*)" + base + " AND created_at < :start")
                .setParameter("org", orgId)
                .setParameter("start", windowStart.atStartOfDay())
                .getSingleResult()).longValue();

        // Fetch creation timestamps inside the window and bucket by week in Java.
        // (SQL Server won't GROUP BY a parameterised DATEDIFF, so we bucket here.)
        @SuppressWarnings("unchecked")
        List<Object> stamps = em.createNativeQuery(
                "SELECT created_at" + base + " AND created_at >= :start")
                .setParameter("org", orgId)
                .setParameter("start", windowStart.atStartOfDay())
                .getResultList();
        Map<Integer, Long> byWeek = new HashMap<>();
        for (Object o : stamps) {
            LocalDate created = o instanceof java.sql.Timestamp ts ? ts.toLocalDateTime().toLocalDate()
                    : o instanceof java.time.LocalDateTime ldt ? ldt.toLocalDate()
                    : LocalDate.parse(o.toString().substring(0, 10));
            int w = (int) java.time.temporal.ChronoUnit.WEEKS.between(windowStart, created);
            if (w >= 0 && w < n) byWeek.merge(w, 1L, Long::sum);
        }

        List<TrendPoint> out = new ArrayList<>(n);
        long cumulative = baseline;
        for (int w = 0; w < n; w++) {
            cumulative += byWeek.getOrDefault(w, 0L);
            out.add(new TrendPoint("W" + (w + 1), cumulative));
        }
        return out;
    }
}
