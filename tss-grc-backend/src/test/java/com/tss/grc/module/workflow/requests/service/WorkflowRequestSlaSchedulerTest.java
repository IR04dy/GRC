package com.tss.grc.module.workflow.requests.service;

import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequest;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequestRecipient;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestEventRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRecipientRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WF-2 — {@link WorkflowRequestSlaScheduler}: overdue requests flip to OVERDUE and escalate
 * exactly once ({@code escalatedAt} guard); requests inside their own reminder window get a
 * throttled reminder to pending assignee recipients; requests outside the window or already
 * escalated are left alone.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowRequestSlaSchedulerTest {

    private static final Long ORG = 1L;

    @Mock private WorkflowRequestRepository          requestRepo;
    @Mock private WorkflowRequestRecipientRepository recipientRepo;
    @Mock private WorkflowRequestEventRepository     eventRepo;
    @Mock private UserRepository                     userRepo;
    @Mock private NotificationService                notifications;
    @Mock private AuditLogService                    audit;
    @Mock private ApplicationEventPublisher          publisher;
    @InjectMocks private WorkflowRequestSlaScheduler scheduler;

    @BeforeEach
    void stubSaves() {
        lenient().when(requestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(eventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(requestRepo.findByStatusInAndDueDateBefore(any(), any())).thenReturn(List.of());
        lenient().when(requestRepo.findByStatusInAndDueDateBetween(any(), any(), any())).thenReturn(List.of());
    }

    private WorkflowRequest request(Long id, String status, LocalDate due, String escRole) {
        WorkflowRequest r = new WorkflowRequest();
        r.setId(id);
        r.setOrganisationId(ORG);
        r.setSourceModule("RISK");
        r.setRequestType("EVIDENCE_REQUEST");
        r.setTitleEn("t");
        r.setStatus(status);
        r.setDueDate(due);
        r.setPriority("HIGH");
        r.setEscalationRole(escRole);
        r.setReminderDaysBefore(3);
        r.setReminderCount(0);
        r.setRoutingMode("PARALLEL");
        r.setCurrentStep(1);
        return r;
    }

    private WorkflowRequestRecipient assignee(Long requestId, Long userId, String status) {
        WorkflowRequestRecipient x = new WorkflowRequestRecipient();
        x.setOrganisationId(ORG);
        x.setRequestId(requestId);
        x.setRecipientType("USER");
        x.setRecipientId(userId);
        x.setActorRole("ASSIGNEE");
        x.setStepNo(1);
        x.setStatus(status);
        return x;
    }

    @Test
    void overdue_flipsToOverdue_escalatesOnce_notifiesPath() {
        WorkflowRequest r = request(10L, "SENT", LocalDate.now().minusDays(2), "CISO");
        when(requestRepo.findByStatusInAndDueDateBefore(any(), any())).thenReturn(List.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 10L))
            .thenReturn(List.of(assignee(10L, 2L, "NOTIFIED")));

        WorkflowRequestSlaScheduler.SweepResult res = scheduler.sweep();

        assertThat(res.escalations()).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo("OVERDUE");
        assertThat(r.getEscalatedAt()).isNotNull();
        assertThat(r.getReminderCount()).isEqualTo(1);
        verify(notifications).broadcastToRole(eq(ORG), eq("CISO"), anyString(), anyString(),
            anyString(), eq("WORKFLOW_REQUEST_ESCALATED"), eq("WorkflowRequest"), eq(10L));
        verify(audit).log(eq("WORKFLOW_REQUEST_OVERDUE"), eq("WorkflowRequest"), eq(10L), anyString());
        verify(publisher).publishEvent(any(DomainEvent.class));
        verify(eventRepo, times(2)).save(any());   // OVERDUE + ESCALATED timeline events
    }

    @Test
    void overdue_alreadyEscalated_flipsStatusButDoesNotReNotify() {
        WorkflowRequest r = request(11L, "IN_PROGRESS", LocalDate.now().minusDays(5), "CISO");
        r.setEscalatedAt(LocalDateTime.now().minusDays(1));   // escalated on a previous sweep
        when(requestRepo.findByStatusInAndDueDateBefore(any(), any())).thenReturn(List.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 11L))
            .thenReturn(List.of());

        scheduler.sweep();

        assertThat(r.getStatus()).isEqualTo("OVERDUE");
        verify(notifications, never()).broadcastToRole(any(), any(), any(), any(), any(), any(), any(), any());
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
        verify(eventRepo, times(1)).save(any());   // OVERDUE event only — no second ESCALATED
    }

    @Test
    void reminder_insideWindow_remindsPendingAssignee_andStampsThrottle() {
        WorkflowRequest r = request(12L, "SENT", LocalDate.now().plusDays(2), null);  // window 3d
        when(requestRepo.findByStatusInAndDueDateBetween(any(), any(), any())).thenReturn(List.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 12L))
            .thenReturn(List.of(assignee(12L, 2L, "NOTIFIED")));

        WorkflowRequestSlaScheduler.SweepResult res = scheduler.sweep();

        assertThat(res.reminders()).isEqualTo(1);
        assertThat(r.getLastReminderAt()).isNotNull();
        assertThat(r.getReminderCount()).isEqualTo(1);
        verify(notifications).notifyUser(eq(ORG), eq(2L), anyString(), anyString(), anyString(),
            eq("WORKFLOW_REQUEST_REMINDER"), eq("WorkflowRequest"), eq(12L));
    }

    @Test
    void reminder_throttled_whenRecentlyReminded() {
        WorkflowRequest r = request(13L, "SENT", LocalDate.now().plusDays(2), null);
        r.setLastReminderAt(LocalDateTime.now().minusHours(1));   // reminded an hour ago
        when(requestRepo.findByStatusInAndDueDateBetween(any(), any(), any())).thenReturn(List.of(r));

        WorkflowRequestSlaScheduler.SweepResult res = scheduler.sweep();

        assertThat(res.reminders()).isZero();
        verify(notifications, never()).notifyUser(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reminder_outsideOwnWindow_skipped() {
        WorkflowRequest r = request(14L, "SENT", LocalDate.now().plusDays(10), null); // window 3d
        when(requestRepo.findByStatusInAndDueDateBetween(any(), any(), any())).thenReturn(List.of(r));

        WorkflowRequestSlaScheduler.SweepResult res = scheduler.sweep();

        assertThat(res.reminders()).isZero();
        verify(requestRepo, never()).save(any());
    }

    @Test
    void reminder_skipsEscalatedRequests() {
        WorkflowRequest r = request(15L, "IN_PROGRESS", LocalDate.now().plusDays(1), "CISO");
        r.setEscalatedAt(LocalDateTime.now().minusDays(1));
        when(requestRepo.findByStatusInAndDueDateBetween(any(), any(), any())).thenReturn(List.of(r));

        WorkflowRequestSlaScheduler.SweepResult res = scheduler.sweep();

        assertThat(res.reminders()).isZero();
    }
}
