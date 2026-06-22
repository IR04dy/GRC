package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberActionPlan;
import com.tss.grc.module.cyber.repository.CyberActionPlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link CyberActionPlanService} — extracted from the former CyberService
 * god-class (QUAL-CP-001). Locks CAPA completion (status / progress / stamp).
 */
@ExtendWith(MockitoExtension.class)
class CyberActionPlanServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long ID     = 100L;

    @Mock private CyberActionPlanRepository actionPlanRepo;
    @InjectMocks private CyberActionPlanService service;

    @BeforeEach void setTenant() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    @Test
    void completeActionPlan_sets_COMPLETED_progress_100_and_completedAt() {
        CyberActionPlan ap = new CyberActionPlan();
        ap.setId(ID);
        ap.setWorkflowStatus("IN_PROGRESS");
        ap.setProgressPct(45);
        when(actionPlanRepo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(ap));
        when(actionPlanRepo.save(any(CyberActionPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        CyberActionPlan after = service.completeActionPlan(ID);
        assertThat(after.getWorkflowStatus()).isEqualTo("COMPLETED");
        assertThat(after.getProgressPct()).isEqualTo(100);
        assertThat(after.getCompletedAt()).isEqualTo(LocalDate.now());
    }
}
