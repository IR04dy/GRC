package com.tss.grc.module.evidence.service;

import com.tss.grc.module.evidence.entity.EvidenceRequest;
import com.tss.grc.module.evidence.repository.EvidenceRequestRepository;
import com.tss.grc.module.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Evidence-request SLA reminders. Each night, any open request past its due date
 * gets a reminder notification to its assignee; once it is {@value #ESCALATION_DAYS}+
 * days overdue, the original requester is also notified (escalation).
 *
 * <p>Scheduling is enabled globally on {@code GrcApplication}. Runs system-wide
 * (no tenant context) — each notification is delivered against the request's own
 * organisation.
 */
@Component
@RequiredArgsConstructor
public class EvidenceRequestSlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(EvidenceRequestSlaScheduler.class);

    /** Days overdue after which the requester is escalated, not just the assignee. */
    private static final long ESCALATION_DAYS = 7;

    private final EvidenceRequestRepository requestRepository;
    private final NotificationService notifications;

    /** Runs at 01:40 every day (server local time), just after the expiry sweep. */
    @Scheduled(cron = "0 40 1 * * *")
    public void sweepOverdueRequests() {
        try {
            int sent = remindOverdue();
            if (sent > 0) {
                log.info("[evidence-request-sla] delivered {} overdue reminder/escalation notification(s)", sent);
            }
        } catch (Exception e) {
            log.error("[evidence-request-sla] sweep failed: {}", e.getMessage(), e);
        }
    }

    /** Finds overdue open requests and notifies assignees (+ requesters when escalated). */
    public int remindOverdue() {
        LocalDate today = LocalDate.now();
        List<EvidenceRequest> overdue = requestRepository.findOverdue(today);
        int sent = 0;
        for (EvidenceRequest r : overdue) {
            long days = ChronoUnit.DAYS.between(r.getDueDate(), today);
            String msgEn = "\"" + r.getTitleEn() + "\" is " + days + " day(s) overdue (due " + r.getDueDate() + ").";

            if (notifications.notifyUser(r.getOrganisationId(), r.getAssignedToId(),
                    "Evidence request overdue", "طلب دليل متأخر", msgEn,
                    "EVIDENCE_REQUEST_OVERDUE", "EvidenceRequest", r.getId())) {
                sent++;
            }

            if (days >= ESCALATION_DAYS && r.getRequestedById() != null
                    && !r.getRequestedById().equals(r.getAssignedToId())) {
                if (notifications.notifyUser(r.getOrganisationId(), r.getRequestedById(),
                        "Evidence request escalation", "تصعيد طلب دليل",
                        "An evidence request you raised, \"" + r.getTitleEn() + "\", is " + days
                            + " days overdue and still unfulfilled.",
                        "EVIDENCE_REQUEST_ESCALATION", "EvidenceRequest", r.getId())) {
                    sent++;
                }
            }
        }
        return sent;
    }
}
