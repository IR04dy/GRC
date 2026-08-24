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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    // ── F2-329: a create owns none of the scheduler/complete-owned trail ───────────────────────

    @Test
    void saveActionPlan_cannotBeBornCompleted() {
        CyberActionPlan ap = new CyberActionPlan();
        ap.setTitleEn("Patch the exposed admin console");
        ap.setWorkflowStatus("COMPLETED");          // the bypass: born completed, skipping completeActionPlan
        assertThatThrownBy(() -> service.saveActionPlan(ap, ORG_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("complete");
    }

    @Test
    void saveActionPlan_scrubsSchedulerAndCompletionTrail() {
        CyberActionPlan ap = new CyberActionPlan();
        ap.setTitleEn("Patch the exposed admin console");
        // A forged escalation/reminder/completion trail (all scheduler/complete-owned — CY-E).
        // escalatedAt is the sharp one: the sweep reads a set value as "already escalated once".
        ap.setEscalatedAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        ap.setLastReminderAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        ap.setReminderCount(14);
        ap.setCompletedAt(LocalDate.of(2020, 1, 1));
        when(actionPlanRepo.save(any(CyberActionPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        CyberActionPlan saved = service.saveActionPlan(ap, ORG_ID);
        assertThat(saved.getEscalatedAt()).as("the sweep must not see a forged escalation (CY-E)").isNull();
        assertThat(saved.getLastReminderAt()).isNull();
        assertThat(saved.getReminderCount()).isZero();
        assertThat(saved.getCompletedAt()).as("completion is completeActionPlan's alone").isNull();
        assertThat(saved.getWorkflowStatus()).isEqualTo("OPEN");   // intermediate states stay caller-settable
    }
}
