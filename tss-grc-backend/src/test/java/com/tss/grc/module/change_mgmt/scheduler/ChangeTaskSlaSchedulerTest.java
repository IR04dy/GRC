package com.tss.grc.module.change_mgmt.scheduler;

import com.tss.grc.module.change_mgmt.entity.ChangeTask;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeTaskRepository;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CH-5 — the change-task SLA sweep emits CHANGE_TASK_OVERDUE for overdue open tasks
 * exactly once per due date (audit-marker dedup), so restarts never double-notify.
 */
@ExtendWith(MockitoExtension.class)
class ChangeTaskSlaSchedulerTest {

    private static final Long ORG = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 22, 10, 0);

    @Mock ChangeTaskRepository taskRepo;
    @Mock ChangeRequestRepository changeRepo;
    @Mock AuditLogRepository auditRepo;
    @Mock AuditLogService audit;
    @Mock ApplicationEventPublisher publisher;
    @InjectMocks ChangeTaskSlaScheduler scheduler;

    private static ChangeTask task(long id, LocalDateTime due, String status) {
        ChangeTask t = new ChangeTask();
        t.setId(id);
        t.setChangeRequestId(99L);
        t.setTitleEn("Deploy DB migration");
        t.setOwnerName("ops");
        t.setDueAt(due);
        t.setStatus(status);
        return t;
    }

    @Test
    void sweep_emitsOverdueEvent_andMarksFired_whenNotAlreadyNotified() {
        when(taskRepo.findOverdueOpen(ORG, NOW))
            .thenReturn(List.of(task(5L, NOW.minusDays(3), "IN_PROGRESS")));
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(ORG, "ChangeTask", 5L))
            .thenReturn(List.of());   // never fired before

        int fired = scheduler.sweep(ORG, NOW);

        assertThat(fired).isEqualTo(1);
        verify(publisher).publishEvent(any(DomainEvent.class));
        // dedup marker written, keyed on the task's due date
        verify(audit).log(startsWith("CHG_FIRE:TASK_OVERDUE:"), eq("ChangeTask"), eq(5L), anyString());
    }

    @Test
    void sweep_skips_whenAlreadyNotifiedForThatDueDate() {
        LocalDateTime due = NOW.minusDays(3);
        AuditLogEntry marker = new AuditLogEntry();
        marker.setActionType("CHG_FIRE:TASK_OVERDUE:" + due.toLocalDate());   // matches the dedup key
        when(taskRepo.findOverdueOpen(ORG, NOW))
            .thenReturn(List.of(task(5L, due, "PENDING")));
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(ORG, "ChangeTask", 5L))
            .thenReturn(List.of(marker));

        int fired = scheduler.sweep(ORG, NOW);

        assertThat(fired).isZero();
        verify(publisher, never()).publishEvent(any());
        verify(audit, never()).log(any(), any(), any(), any());
    }
}
