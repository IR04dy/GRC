package com.tss.grc.module.admin.committee.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeMeetingPaper;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingPaperRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pre-read papers service — tracks circulation timestamps, read receipts,
 * and late-paper discipline.
 *
 * <p>The {@code circulate} method auto-detects late papers: if the
 * circulation time is within 24h of the meeting's {@code scheduledAt},
 * the paper is flagged {@code latePaper=1} and a {@code latePaperReason}
 * must be supplied (good governance practice, CMA CG Article 64).
 *
 * <p>{@code markRead} appends a read-receipt to the JSON array. The
 * read-rate KPI computed on the meeting detail uses this data.
 */
@Service
@RequiredArgsConstructor
public class CommitteeMeetingPapersService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CommitteeMeetingPaperRepository papers;
    private final CommitteeMeetingRepository      meetings;
    private final AuditLogService                 audit;
    private final ApplicationEventPublisher       publisher;

    @Transactional(readOnly = true)
    public List<CommitteeMeetingPaper> listForMeeting(Long meetingId) {
        return papers.findForMeeting(TenantContext.getCurrentTenant(), meetingId);
    }

    @Transactional(readOnly = true)
    public CommitteeMeetingPaper get(Long id) {
        return papers.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("CommitteeMeetingPaper " + id));
    }

    /** Circulate a paper — sets circulatedAt and auto-flags late papers. */
    @Transactional
    public CommitteeMeetingPaper circulate(CommitteeMeetingPaper in) {
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        in.setCreatedAt(LocalDateTime.now());
        if (in.getCirculatedAt() == null) in.setCirculatedAt(LocalDateTime.now());
        if (in.getConfidentiality() == null) in.setConfidentiality("INTERNAL");
        if (in.getLatePaper() == null) in.setLatePaper((short) 0);

        // Auto-detect late paper: circulation within 24h of meeting.
        meetings.findByIdAndOrganisationId(in.getMeetingId(), org).ifPresent(mtg -> {
            if (mtg.getScheduledAt() != null && in.getCirculatedAt() != null) {
                Duration leadTime = Duration.between(in.getCirculatedAt(), mtg.getScheduledAt());
                if (leadTime.toHours() < 24 && leadTime.toHours() >= 0) {
                    in.setLatePaper((short) 1);
                }
            }
        });

        CommitteeMeetingPaper saved = papers.save(in);

        audit.log("PAPER_CIRCULATE", "CommitteeMeetingPaper", saved.getId(),
            saved.getPaperCode() + " · meeting=" + saved.getMeetingId()
                + (saved.getLatePaper() == 1 ? " · LATE" : ""));

        publisher.publishEvent(DomainEvent.of(
            org, saved.getLatePaper() == 1 ? "COMMITTEE_PAPER_LATE" : "COMMITTEE_PAPER_CIRCULATED",
            "CommitteeMeetingPaper", saved.getId(),
            Map.of("paperCode",       saved.getPaperCode(),
                   "meetingId",       String.valueOf(saved.getMeetingId()),
                   "agendaItemSeq",   saved.getAgendaItemSeq() == null ? "" : String.valueOf(saved.getAgendaItemSeq()),
                   "confidentiality", saved.getConfidentiality(),
                   "latePaper",       String.valueOf(saved.getLatePaper()),
                   "authorName",      saved.getAuthorName() == null ? "" : saved.getAuthorName())));

        return saved;
    }

    /** Append a read-receipt: {userId, readAt}. */
    @Transactional
    public CommitteeMeetingPaper markRead(Long id, Long userId) {
        CommitteeMeetingPaper p = get(id);
        try {
            ArrayNode arr;
            if (p.getReadReceiptsJson() == null || p.getReadReceiptsJson().isBlank()) {
                arr = JSON.createArrayNode();
            } else {
                var node = JSON.readTree(p.getReadReceiptsJson());
                arr = node.isArray() ? (ArrayNode) node : JSON.createArrayNode();
            }
            // Skip if user has already read it.
            boolean already = false;
            for (var entry : arr) {
                if (entry.has("userId") && entry.get("userId").asLong() == userId) {
                    already = true; break;
                }
            }
            if (!already) {
                ObjectNode receipt = JSON.createObjectNode();
                receipt.put("userId", userId);
                receipt.put("readAt", LocalDateTime.now().toString());
                arr.add(receipt);
                p.setReadReceiptsJson(arr.toString());
                papers.save(p);
                audit.log("PAPER_READ", "CommitteeMeetingPaper", id,
                    "User " + userId + " read " + p.getPaperCode());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to update read receipts", ex);
        }
        return p;
    }

    @Transactional
    public CommitteeMeetingPaper markLate(Long id, String reason) {
        CommitteeMeetingPaper p = get(id);
        p.setLatePaper((short) 1);
        p.setLatePaperReason(reason);
        CommitteeMeetingPaper saved = papers.save(p);
        audit.log("PAPER_LATE_FLAG", "CommitteeMeetingPaper", id,
            p.getPaperCode() + " · reason=" + (reason == null ? "—" : reason));
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> readStats(Long meetingId) {
        List<CommitteeMeetingPaper> all = listForMeeting(meetingId);
        long total = all.size();
        long withReceipts = all.stream()
            .filter(p -> p.getReadReceiptsJson() != null && !p.getReadReceiptsJson().isBlank()
                      && !"[]".equals(p.getReadReceiptsJson().trim()))
            .count();
        long late = all.stream().filter(p -> p.getLatePaper() != null && p.getLatePaper() == 1).count();
        return Map.of(
            "totalPapers",       total,
            "papersWithReceipts", withReceipts,
            "latePapers",        late);
    }
}
