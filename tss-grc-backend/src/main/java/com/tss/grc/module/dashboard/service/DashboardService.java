package com.tss.grc.module.dashboard.service;

import com.tss.grc.module.compliance.repository.ComplianceAssessmentRepository;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.dashboard.dto.ChartData;
import com.tss.grc.module.dashboard.dto.DashboardStats;
import com.tss.grc.module.dashboard.dto.HeatmapData;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.incident.repository.IncidentRepository;
import com.tss.grc.module.risk.dto.RiskResponse;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RiskRepository riskRepository;
    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository;
    private final ControlRepository controlRepository;
    private final ComplianceAssessmentRepository assessmentRepository;

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

        return new DashboardStats(total, critical, high, medium, low, open, closed, totalUsers, activeUsers,
                critical, high, openIncidents, complianceScore, 0L);
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
}
