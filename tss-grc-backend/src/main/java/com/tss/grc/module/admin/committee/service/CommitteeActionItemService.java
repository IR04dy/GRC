package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeActionItem;
import com.tss.grc.module.admin.committee.repository.CommitteeActionItemRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Action-item lifecycle service.
 *
 * <p>Lifecycle: OPEN → IN_PROGRESS → COMPLETED. Side branches:
 * CANCELLED (no longer relevant, Chair approves closure), DEFERRED
 * (date moved with Chair approval), OVERDUE (sweep flips this when
 * dueDate &lt; today and status is OPEN/IN_PROGRESS).
 *
 * <p>F8 events on every transition so downstream automation (next-
 * meeting agenda auto-populator, owner-manager escalation on overdue,
 * cross-module reverse-link updates) can react.
 */
@Service
@RequiredArgsConstructor
public class CommitteeActionItemService {

    private final CommitteeActionItemRepository repo;
    private final AuditLogService               audit;
    private final ApplicationEventPublisher     publisher;

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public Page<CommitteeActionItem> list(Pageable pageable) {
        try {
            return repo.findByOrganisationIdOrderByCreatedAtDesc(
                TenantContext.getCurrentTenant(), pageable);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) {
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public CommitteeActionItem get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("CommitteeActionItem " + id));
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public List<CommitteeActionItem> listByMeeting(Long meetingId) {
        try {
            return repo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(
                TenantContext.getCurrentTenant(), meetingId);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public List<CommitteeActionItem> listByCommittee(Long committeeId) {
        try {
            return repo.findByOrganisationIdAndCommitteeIdOrderByDueDateAsc(
                TenantContext.getCurrentTenant(), committeeId);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    /** Open actions for a committee — drives the "matters arising"
     *  section of the agenda auto-populator. */
    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public List<CommitteeActionItem> openActionsForCommittee(Long committeeId) {
        try {
            return repo.findOpenByCommittee(
                TenantContext.getCurrentTenant(), committeeId);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public List<CommitteeActionItem> openActionsForOwner(Long ownerUserId) {
        try {
            return repo.findOpenByOwner(
                TenantContext.getCurrentTenant(), ownerUserId);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    @Transactional
    public CommitteeActionItem create(CommitteeActionItem in) {
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        in.setCreatedAt(LocalDateTime.now());
        if (in.getStatus() == null) in.setStatus("OPEN");
        CommitteeActionItem saved = repo.save(in);

        audit.log("ACTION_CREATE", "CommitteeActionItem", saved.getId(),
            saved.getActionCode() + " · owner=" + saved.getOwnerName()
                + " · due=" + saved.getDueDate());

        publisher.publishEvent(DomainEvent.of(
            org, "COMMITTEE_ACTION_CREATED", "CommitteeActionItem", saved.getId(),
            Map.of("actionCode",   saved.getActionCode(),
                   "committeeId",  saved.getCommitteeId(),
                   "meetingId",    saved.getMeetingId(),
                   "ownerName",    saved.getOwnerName() == null ? "" : saved.getOwnerName(),
                   "dueDate",      saved.getDueDate() == null ? "" : saved.getDueDate().toString(),
                   "priority",     saved.getPriority(),
                   "linkedModule", saved.getLinkedModule() == null ? "" : saved.getLinkedModule(),
                   "linkedEntityCode", saved.getLinkedEntityCode() == null ? "" : saved.getLinkedEntityCode())));

        return saved;
    }

    /** Move OPEN → IN_PROGRESS. */
    @Transactional
    public CommitteeActionItem startProgress(Long id) {
        CommitteeActionItem a = get(id);
        String prev = a.getStatus();
        if (!"OPEN".equals(prev) && !"OVERDUE".equals(prev)) {
            throw new IllegalStateException("Cannot start progress from status " + prev);
        }
        a.setStatus("IN_PROGRESS");
        CommitteeActionItem saved = repo.save(a);
        audit.log("ACTION_START", "CommitteeActionItem", id,
            "Status " + prev + " → IN_PROGRESS");
        publishTransition(saved, prev, "IN_PROGRESS");
        return saved;
    }

    /** Mark COMPLETED. */
    @Transactional
    public CommitteeActionItem complete(Long id, String completionNotesEn, Long completedBy) {
        CommitteeActionItem a = get(id);
        String prev = a.getStatus();
        a.setStatus("COMPLETED");
        a.setCompletedAt(LocalDateTime.now());
        if (completedBy != null) a.setCompletedBy(completedBy);
        if (completionNotesEn != null) a.setCompletionNotesEn(completionNotesEn);
        CommitteeActionItem saved = repo.save(a);
        audit.log("ACTION_COMPLETE", "CommitteeActionItem", id,
            "Status " + prev + " → COMPLETED · notes=" + (completionNotesEn == null ? "—" : completionNotesEn));
        publishTransition(saved, prev, "COMPLETED");
        return saved;
    }

    /** Cancel — closure without completion. Requires Chair authority
     *  in practice; the service trusts caller permission. */
    @Transactional
    public CommitteeActionItem cancel(Long id, String reason) {
        CommitteeActionItem a = get(id);
        String prev = a.getStatus();
        a.setStatus("CANCELLED");
        a.setCompletionNotesEn(reason);
        CommitteeActionItem saved = repo.save(a);
        audit.log("ACTION_CANCEL", "CommitteeActionItem", id,
            "Status " + prev + " → CANCELLED · reason=" + (reason == null ? "—" : reason));
        publishTransition(saved, prev, "CANCELLED");
        return saved;
    }

    /** Defer due-date — requires Chair authority in practice. */
    @Transactional
    public CommitteeActionItem defer(Long id, LocalDate newDueDate, String reason) {
        CommitteeActionItem a = get(id);
        String prev = a.getStatus();
        LocalDate oldDue = a.getDueDate();
        a.setStatus("DEFERRED");
        a.setDueDate(newDueDate);
        CommitteeActionItem saved = repo.save(a);
        audit.log("ACTION_DEFER", "CommitteeActionItem", id,
            "Status " + prev + " → DEFERRED · " + oldDue + " → " + newDueDate
                + " · reason=" + (reason == null ? "—" : reason));
        publishTransition(saved, prev, "DEFERRED");
        return saved;
    }

    /** Called by the scheduled sweep when due_date has passed and status
     *  is still OPEN/IN_PROGRESS. */
    @Transactional
    public CommitteeActionItem markOverdue(Long id) {
        CommitteeActionItem a = get(id);
        String prev = a.getStatus();
        if ("COMPLETED".equals(prev) || "CANCELLED".equals(prev) || "OVERDUE".equals(prev)) {
            return a;
        }
        a.setStatus("OVERDUE");
        CommitteeActionItem saved = repo.save(a);
        audit.log("ACTION_OVERDUE", "CommitteeActionItem", id,
            "Status " + prev + " → OVERDUE · was due " + a.getDueDate());
        publishTransition(saved, prev, "OVERDUE");
        return saved;
    }

    private void publishTransition(CommitteeActionItem a, String fromStatus, String toStatus) {
        publisher.publishEvent(DomainEvent.of(
            a.getOrganisationId(),
            "COMMITTEE_ACTION_" + toStatus,
            "CommitteeActionItem", a.getId(),
            Map.of("actionCode",  a.getActionCode(),
                   "committeeId", a.getCommitteeId(),
                   "meetingId",   a.getMeetingId(),
                   "owner",       a.getOwnerName() == null ? "" : a.getOwnerName(),
                   "fromStatus",  fromStatus,
                   "toStatus",    toStatus,
                   "priority",    a.getPriority())));
    }
}
