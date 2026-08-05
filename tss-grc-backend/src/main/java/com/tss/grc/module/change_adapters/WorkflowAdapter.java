package com.tss.grc.module.change_adapters;

import com.tss.grc.module.change_mgmt.spi.WorkflowPort;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.CreateRequest;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RecipientDto;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RequestResponse;
import com.tss.grc.module.workflow.requests.service.WorkflowRequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * S8h / CE-10 — {@link WorkflowPort} over {@code module.workflow.requests}, the platform workflow
 * engine. Change Enablement becomes the 15th adopting module.
 *
 * <h3>Why this file is thin, and why that was the finding</h3>
 * The first draft computed recipient reachability <em>here</em>: it injected {@code RoleRepository},
 * {@code UserRepository}, {@code DepartmentRepository} and the committee repositories, re-derived each
 * of {@code notifyStep}'s four resolution rules, and had a unit test assert that the two copies agreed.
 *
 * <p>{@code ChangeModuleBoundaryTest} rejected it — "reaching a fifth peer arrived without a port
 * decision" — and the rejection was right for a reason beyond package hygiene: <b>a mirror maintained
 * by assertion is a mirror that eventually lies.</b> Reachability is a fact about the engine's own
 * fan-out, not about change, so it belongs in the class that performs the fan-out. It now lives beside
 * {@code notifyStep} as {@code WorkflowRequestService.reachOf}, where it cannot drift — and where the
 * other 14 adopting modules can use it too, since every one of them can compose a request that reaches
 * nobody.
 *
 * <p>What is left here is the only thing an adapter should hold: the peer's vocabulary. Change's
 * {@link WorkflowPort.Subject} maps to the engine's {@code source_record_type}, and the engine's reach
 * records map to the port's. That is the division S7d settled for {@code origin_type} and S8a for
 * {@code linked_entity_type} — the port names the subject in its own words, the adapter translates.
 *
 * <h3>⚠ The one thing this adapter refuses</h3>
 * {@link WorkflowPort#REFUSED_TYPE} — an {@code APPROVAL}-typed request. A change is authorised by
 * recorded CAB votes or a carried committee resolution and by nothing else; an approval-shaped engine
 * request would sit in the audit trail conferring no authority, contradicting the unauthorised-change
 * register without explaining itself. The refusal precedes every other step in {@link #raise}.
 */
@Slf4j
@Component
public class WorkflowAdapter implements WorkflowPort {

    /** The engine's own name for a change-module request. */
    static final String SOURCE_MODULE = "CHANGE";

    /** {@code source_record_type} per subject — CamelCase, matching the 14 existing adopters. */
    static final Map<Subject, String> RECORD_TYPES = Map.of(
        Subject.CHANGE, "ChangeRequest",
        Subject.TASK,   "ChangeTask");

    /** How many candidate addressees to offer. A picker with 300 entries is not a picker. */
    static final int CANDIDATE_LIMIT = 60;

    private final WorkflowRequestService requests;

    public WorkflowAdapter(@Autowired(required = false) WorkflowRequestService requests) {
        this.requests = requests;
    }

    // ── reach (delegated, deliberately) ──────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Reach reach(Addressee a) {
        if (requests == null || a == null || a.type() == null || a.type().isBlank())
            return Reach.no("ADDRESSEE_TYPE_UNKNOWN");
        String type = a.type().trim().toUpperCase(Locale.ROOT);
        // EXTERNAL is not in ADDRESSEE_TYPES: it reaches a token holder rather than a platform user,
        // and a change task assigned to an email address is a different feature with its own consent
        // and expiry questions. Rejected here rather than passed through and answered wrongly.
        if (!ADDRESSEE_TYPES.contains(type)) return Reach.no("ADDRESSEE_TYPE_UNKNOWN");
        return toReach(requests.reachOf(type, a.id(), a.key()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candidate> candidates() {
        if (requests == null) return List.of();
        return requests.reachableAddressees(CANDIDATE_LIMIT).stream()
            .map(x -> new Candidate(x.recipientType(), x.recipientId(), x.recipientKey(),
                                    x.label(), x.users()))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabelMatch> resolveLabels(List<String> labels) {
        if (requests == null || labels == null || labels.isEmpty()) return List.of();
        return requests.resolveAddresseeLabels(labels).stream()
            .map(r -> new LabelMatch(r.label(), r.recipientType(), r.recipientId(),
                                     r.recipientKey(), r.matchedLabel(), toReach(r.reach())))
            .toList();
    }

    /**
     * The engine's reach vocabulary in the port's words.
     *
     * <p>One name differs on purpose. The engine says {@code UNKNOWN_TYPE} for any recipient kind it
     * cannot count — including EXTERNAL, which it <em>can</em> notify; the port says
     * {@code ADDRESSEE_TYPE_UNKNOWN} because from the change module's side that distinction does not
     * exist. {@code LABEL_MATCHES_NOTHING} is the same string on both sides, because it describes the
     * label rather than the engine.
     */
    private static Reach toReach(WorkflowRequestService.RecipientReach r) {
        if (r == null) return Reach.no("ADDRESSEE_TYPE_UNKNOWN");
        if (r.reachable()) return Reach.ok(r.users());
        return Reach.no("UNKNOWN_TYPE".equals(r.reason()) ? "ADDRESSEE_TYPE_UNKNOWN" : r.reason());
    }

    // ── raise ────────────────────────────────────────────────────────────────

    /**
     * ⚠ Deliberately <b>not</b> {@code @Transactional}, and this cost a 500 to learn.
     *
     * <p>{@code create} and {@code send} each persist the same {@code workflow.requests} row, and each
     * is {@code @Transactional} in its own right. Wrapping both in one outer transaction makes them
     * join it, so the row is flushed twice against a {@code ROWVERSION} column and the second flush
     * reads a stale version: <em>"Row was updated or deleted by another transaction"</em> — landmine #2
     * on this platform's own list, met here in a new place.
     *
     * <p>Letting each call own its transaction is also exactly what the platform's own
     * {@code RaiseRequestButton} does: it POSTs create, then POSTs send, as two requests. The
     * consequence is honest and worth stating — a failure in {@code send} leaves a DRAFT request
     * behind rather than rolling the creation back. A DRAFT request is visible, editable and sendable;
     * a half-committed one would not be, and neither would a request that silently vanished.
     */
    @Override
    public Optional<RequestRef> raise(Subject subject, Long subjectId, String requestType,
                                      String titleEn, String titleAr, String description,
                                      Addressee addressee, LocalDate dueDate, Integer slaHours,
                                      String priority, String escalationRole) {
        String type = requestType == null ? "" : requestType.trim().toUpperCase(Locale.ROOT);
        if (REFUSED_TYPE.equals(type))
            throw new IllegalArgumentException(
                "A change may not raise an " + REFUSED_TYPE + " request through the workflow engine. "
              + "Change authorisation has exactly two routes — recorded CAB votes, or a carried "
              + "committee resolution on a referral — and an engine request grants neither. "
              + "Raise a REVIEW or TASK instead, or use the CAB path.");
        if (!RAISABLE_TYPES.contains(type))
            throw new IllegalArgumentException("Unsupported request type '" + requestType
                + "'. Supported: " + String.join(", ", RAISABLE_TYPES));

        Reach r = reach(addressee);
        if (!r.reachable())
            throw new IllegalArgumentException(
                "This request would reach nobody (" + r.reason() + "), so the engine's SLA, reminder "
              + "and escalation would run against an empty audience. Pick an addressee that resolves "
              + "to at least one active user.");

        if (requests == null) {
            log.warn("CE-10: workflow engine unavailable — no request raised for {} #{}", subject, subjectId);
            return Optional.empty();
        }

        // ⚠ Found by live probe, not by a unit test. The engine refuses a request that requires a
        // response but carries no due date — "dueDate is required when a response is required" — and
        // that rule is right: an SLA clock, a reminder ladder and an escalation path all measure from
        // a deadline, so a response requirement without one cannot be chased. It matters here because
        // 18 of the 41 seeded change tasks have no due_at at all, which is half of what G-10 is
        // complaining about; routing them is exactly when a date has to be supplied.
        LocalDate due = dueDate;
        if (due == null && slaHours != null && slaHours > 0)
            due = LocalDate.now().plusDays(Math.max(1L, (long) Math.ceil(slaHours / 24.0)));
        if (due == null)
            throw new IllegalArgumentException(
                "A deadline is required to route work through the engine: the SLA clock, the reminders "
              + "and the escalation ladder all measure from one. Give this request a due date, or an "
              + "SLA in hours to derive it from."
              + (subject == Subject.TASK ? " The task itself has no due date to inherit." : ""));

        RecipientDto rec = new RecipientDto(
            addressee.type().trim().toUpperCase(Locale.ROOT), addressee.id(), addressee.key(),
            addressee.label(), "ASSIGNEE", 1);

        CreateRequest dto = new CreateRequest(
            SOURCE_MODULE, RECORD_TYPES.get(subject), subjectId, type,
            titleEn, titleAr, description,
            "PARALLEL", "ALL", null,
            due, slaHours,
            priority == null || priority.isBlank() ? "MEDIUM" : priority.trim().toUpperCase(Locale.ROOT),
            null, null,
            escalationRole == null || escalationRole.isBlank() ? null : escalationRole.trim(),
            null, Boolean.TRUE, Boolean.FALSE, null, null, null,
            List.of(rec), null, null, null, null, null);

        RequestResponse created = requests.create(dto);
        // Sent immediately: a DRAFT request has no SLA clock and notifies nobody, so leaving it in
        // DRAFT would reproduce the dead button the engine exists to replace.
        RequestResponse sent = requests.send(created.id());
        return Optional.of(ref(sent, RECORD_TYPES.get(subject), subjectId));
    }

    // ── reads ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<RequestRef> raised(Subject subject, Long subjectId) {
        if (requests == null || subjectId == null) return List.of();
        String type = RECORD_TYPES.get(subject);
        return requests.listForRecord(type, subjectId).stream()
            .map(w -> ref(w, type, subjectId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequestRef> raisedForChange(Long changeId, List<Long> taskIds) {
        if (requests == null || changeId == null) return List.of();
        List<RequestRef> out = new java.util.ArrayList<>(raised(Subject.CHANGE, changeId));
        if (taskIds != null && !taskIds.isEmpty())
            requests.listForRecords(RECORD_TYPES.get(Subject.TASK), taskIds)
                .forEach(w -> out.add(ref(w, RECORD_TYPES.get(Subject.TASK), w.sourceRecordId())));
        return out;
    }

    // ── mapping ──────────────────────────────────────────────────────────────

    private static RequestRef ref(RequestResponse w, String subjectType, Long subjectId) {
        return new RequestRef(w.id(), w.requestCode(), w.requestType(), w.status(),
            w.titleEn(), w.titleAr(), w.dueDate(), w.slaHours(), w.priority(),
            w.countdownStatus(), w.recipientCategory(),
            w.recipients() == null ? 0 : w.recipients().size(), subjectType, subjectId);
    }
}
