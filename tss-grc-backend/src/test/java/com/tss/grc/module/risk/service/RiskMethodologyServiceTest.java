package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.dto.MethodologyDtos.ImpactDimensionDto;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskMethodologyConfig;
import com.tss.grc.module.risk.repository.RiskImpactRatingRepository;
import com.tss.grc.module.risk.repository.RiskMethodologyConfigRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskMethodologyServiceTest {

    private static final Long ORG = 1L, OTHER = 99L, RID = 7L;

    @Mock RiskMethodologyConfigRepository configRepo;
    @Mock RiskImpactRatingRepository impactRepo;
    @Mock RiskRepository risks;
    @Mock AuditLogService audit;
    @InjectMocks RiskMethodologyService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private RiskMethodologyConfig cfg() {
        RiskMethodologyConfig c = new RiskMethodologyConfig();
        c.setId(1L); c.setOrganisationId(ORG);   // defaults: 8 dims, scales 5
        return c;
    }
    private Risk risk(Long org) { Risk r = new Risk(); r.setId(RID); r.setOrganisationId(org); return r; }

    @Test void getOrSeed_seedsDefaultsWhenMissing() {
        when(configRepo.findByOrganisationId(ORG)).thenReturn(Optional.empty());
        when(configRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        RiskMethodologyConfig c = service.getOrSeed();

        assertThat(c.getImpactDimensionsCsv()).isEqualTo(RiskMethodologyConfig.DEFAULT_DIMENSIONS);
        assertThat(c.getImpactScaleMax()).isEqualTo(5);
        assertThat(c.getLikelihoodScaleMax()).isEqualTo(5);
    }

    @Test void setImpact_worstDimensionDrivesInherentImpact() {
        Risk r = risk(ORG);
        when(risks.findById(RID)).thenReturn(Optional.of(r));
        when(configRepo.findByOrganisationId(ORG)).thenReturn(Optional.of(cfg()));
        when(impactRepo.findByOrganisationIdAndRiskIdOrderByDimensionAsc(ORG, RID)).thenReturn(List.of());
        when(impactRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(risks.save(any())).thenAnswer(i -> i.getArgument(0));

        service.setImpactRatings(RID, List.of(
            new ImpactDimensionDto("FINANCIAL", 3),
            new ImpactDimensionDto("cyber", 5),       // lower-case normalises
            new ImpactDimensionDto("PRIVACY", 2)));

        assertThat(r.getInherentImpact()).isEqualTo(5);   // worst dimension
        verify(impactRepo, times(3)).save(any());
    }

    @Test void setImpact_unknownDimension_throws() {
        when(risks.findById(RID)).thenReturn(Optional.of(risk(ORG)));
        when(configRepo.findByOrganisationId(ORG)).thenReturn(Optional.of(cfg()));

        assertThatThrownBy(() -> service.setImpactRatings(RID, List.of(new ImpactDimensionDto("WIDGETS", 3))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dimension");
        verify(impactRepo, never()).save(any());
        verify(risks, never()).save(any());
    }

    @Test void setImpact_scoreAboveScale_throws() {
        when(risks.findById(RID)).thenReturn(Optional.of(risk(ORG)));
        when(configRepo.findByOrganisationId(ORG)).thenReturn(Optional.of(cfg()));

        assertThatThrownBy(() -> service.setImpactRatings(RID, List.of(new ImpactDimensionDto("FINANCIAL", 9))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1..5");
        verify(impactRepo, never()).save(any());
    }

    @Test void setImpact_crossTenantRisk_throws() {
        when(risks.findById(RID)).thenReturn(Optional.of(risk(OTHER)));
        assertThatThrownBy(() -> service.setImpactRatings(RID, List.of(new ImpactDimensionDto("FINANCIAL", 3))))
            .isInstanceOf(EntityNotFoundException.class);
        verify(impactRepo, never()).save(any());
    }
}
