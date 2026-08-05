package com.tss.grc.module.change_mgmt.spi;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * S8h / CE-10 — the change module's outbound port to the platform workflow engine (ADR-012), making
 * Change Enablement the <b>15th</b> adopting module.
 *
 * <h3>G-10 said "inconsistent task/SLA/escalation behaviour". Measured 2026-08-05, that is exactly
 * right, and the inconsistency is not where the plan's design put it</h3>
 *
 * <pre>
 *   change_mgmt.change_tasks .... 10 columns. No assignee id, no sla_hours, no escalation_role,
 *                                 no reminder fields, no routing, no event timeline.
 *   workflow.requests ........... 47 columns, 16 of them routing/SLA/escalation:
 *                                 routing_mode, completion_rule, completion_threshold, current_step,
 *                                 due_date, sla_hours, priority, reminder_days_before,
 *                                 last_reminder_at, reminder_count, escalation_role,
 *                                 escalation_user_id, escalation_tiers, escalation_level,
 *                                 escalated_at, recipient_category
 *   41 change tasks ............. 23 of them overdue right now (22 PENDING + 1 IN_PROGRESS)
 *   14 distinct owner_name ...... 0 resolve to a user. 7 resolve to a department — and 0 of 7
 *                                 users carry a department_id, while 5 of 34 departments have a head.
 * </pre>
 *
 * <p>So every overdue change task is overdue on somebody who cannot be named, and CH-5's
 * {@code CHANGE_TASK_OVERDUE} sweep escalates into the void {@code ChangeRatificationPolicy}'s own
 * javadoc already warns about. That is the gap. Duplicating those 16 columns onto
 * {@code change_tasks} would have been the second implementation G-10 complains about.
 *
 * <h3>⚠ What this port must NOT do, and why it is the whole design</h3>
 * CE-10's wording is "<i>change authorisations become workflow requests</i>". Measured, that is not
 * what adoption means anywhere on this platform:
 *
 * <ul>
 *   <li>{@code WorkflowRequestService} imports evidence, committee, iam and platform — and
 *       <b>none of the 14 adopting modules</b>. There is no callback into a source record.</li>
 *   <li>It publishes {@code DomainEvent} for {@code WORKFLOW_REQUEST_SENT} and
 *       {@code WORKFLOW_REQUEST_ESCALATED} <b>only</b> — never for respond/approve/reject/close.
 *       So not even an event-driven route could authorise anything.</li>
 *   <li>In all 14, adoption is {@code RaiseRequestButton} + {@code ModuleEngineRequestsPanel}: a
 *       tracked, routed, SLA'd human task anchored to a record. The record's own state machine is
 *       untouched.</li>
 * </ul>
 *
 * <p>A change is authorised by exactly <b>two</b> routes, and S8f's register unions both: recorded
 * {@code change_approvals} votes, or a referred change's carried committee resolution. Routing
 * authorisation through the engine would add a third — and because the engine writes no vote rows
 * and no referral, every engine-authorised change would be reported as
 * {@code NO_AUTHORISATION_RECORD} by the register that shipped one slice ago.
 *
 * <p>Hence {@link #RAISABLE_TYPES} and the deliberate absence of {@code APPROVAL} from it. Not a
 * convention — {@link #raise} refuses it. An {@code APPROVAL}-typed engine request against a change
 * would be an approval-shaped artefact in the audit trail that confers no authority, and an auditor
 * reconciling it against the register would find a contradiction with no explanation.
 *
 * <h3>Reachability is the contract, and it mirrors the notifier on purpose</h3>
 * {@code WorkflowRequestService.notifyStep} resolves a DEPARTMENT recipient through
 * {@code findByOrganisationIdAndDepartmentIdAndIsActiveTrue} and then marks the row
 * {@code NOTIFIED} — whether or not that returned anybody. With 0 of 7 users carrying a department,
 * a change task routed to "Cyber Risk &amp; Compliance" would read as NOTIFIED having notified
 * nobody. So {@link #reach} is computed by <b>the same rules the notifier uses</b>, not by a
 * plausible-looking second opinion: a reachability check that does not mirror the notifier is
 * answering a different question, and would pass exactly when it matters least.
 *
 * <p>Implemented in {@code module.change_adapters}, outside both modules. Optional, like
 * {@link EvidencePort}: reads return empty when the adapter is absent. But note the polarity split —
 * an <b>absent adapter</b> yields {@code Optional.empty()} from {@link #raise} (nothing was raised
 * and the caller can say so), whereas an <b>unreachable addressee</b> throws. Silently raising a
 * request nobody receives is the failure this port exists to prevent, so it cannot be a quiet
 * return value.
 */
public interface WorkflowPort {

    /** What a change-module engine request may be anchored to. */
    enum Subject {
        /** The change request as a whole — a review, an information request, a follow-up. */
        CHANGE,
        /** One implementation task on a change — the case G-10 is actually about. */
        TASK
    }

    /**
     * The engine request types the change module may raise.
     *
     * <p>⚠ {@code APPROVAL} is absent by design and {@link #raise} refuses it — see the class
     * javadoc. {@code REVIEW} is present because a post-implementation review is a real change
     * activity; authorisation in this module means CAB/ECAB approval specifically, and that is the
     * one word withheld.
     */
    List<String> RAISABLE_TYPES = List.of(
        "TASK", "REVIEW", "EVIDENCE_REQUEST", "INFORMATION",
        "FOLLOW_UP", "ACKNOWLEDGEMENT", "REPORT_DISTRIBUTION");

    /** The request type a change may never raise, because it would look like an authorisation. */
    String REFUSED_TYPE = "APPROVAL";

    /** Recipient categories the engine can address. Mirrors {@code workflow.request_recipients}. */
    List<String> ADDRESSEE_TYPES = List.of("USER", "ROLE", "DEPARTMENT", "COMMITTEE");

    /**
     * Who a request is for. Deliberately narrow: one addressee per raise.
     *
     * <p>The change module has no use for the engine's full multi-step, multi-recipient composer
     * here — a task has one owner. Offering routing modes the change record cannot express would be
     * a surface with no subject.
     */
    record Addressee(String type, Long id, String key, String label) { }

    /**
     * Whether an addressee reaches a real person, and if not, why not — in the platform's own
     * vocabulary (F2-94 named these for role fan-out; the same distinctions apply here).
     *
     * @param users     how many active users the notifier would actually reach
     * @param reachable {@code users > 0}, or {@code true} for a token-credentialled external
     * @param reason    {@code null} when reachable; otherwise one of {@link #REASONS}
     */
    record Reach(int users, boolean reachable, String reason) {
        public static Reach ok(int users) { return new Reach(users, true, null); }
        public static Reach no(String reason) { return new Reach(0, false, reason); }
    }

    /** Closed vocabulary for an unreachable addressee. A name that is not a role cannot be fixed
     *  by granting anything, so "unknown" and "held by nobody" stay separate. */
    List<String> REASONS = List.of(
        "ADDRESSEE_TYPE_UNKNOWN",
        "USER_NOT_FOUND",
        "ROLE_NOT_FOUND",
        "ROLE_HELD_BY_NOBODY",
        "DEPARTMENT_NOT_FOUND",
        "DEPARTMENT_HAS_NO_MEMBERS",
        "COMMITTEE_NOT_FOUND",
        "COMMITTEE_HAS_NO_LINKED_USERS");

    /** Just enough of an engine request to render it beside the change record. */
    record RequestRef(Long id, String code, String requestType, String status,
                      String titleEn, String titleAr, LocalDate dueDate, Integer slaHours,
                      String priority, String countdown, String recipientCategory,
                      int recipientCount, String subjectType, Long subjectId) { }

    /** An addressee that currently reaches somebody — what a picker may offer. */
    record Candidate(String type, Long id, String key, String label, int users) { }

    /**
     * What a free-text owner label on a change task resolves to, if anything.
     *
     * <p>This exists because {@code change_tasks.owner_name} is a string. Measured: 14 distinct
     * values, 0 matching a user, 7 matching a department — and every matching department is empty.
     * The report that surfaces this has to distinguish "names nothing at all" from "names something
     * real that contains nobody", because only the second is fixed by appointing someone.
     *
     * @param type  the addressee kind matched, or {@code null} when nothing matched
     * @param reach the reach of the match; {@code UNRESOLVED} when {@code type} is {@code null}
     */
    record LabelMatch(String label, String type, Long id, String key, String matchedLabel, Reach reach) { }

    /** Reason used when a label matches no role, department, committee or user at all. */
    String UNRESOLVED = "LABEL_MATCHES_NOTHING";

    /**
     * Resolve free-text owner labels to addressees, in one pass over the tenant's directories.
     *
     * <p>Batched rather than per-label because the resolution reads every role, department and
     * committee in the tenant: resolving 14 labels one at a time would read them 14 times.
     */
    List<LabelMatch> resolveLabels(List<String> labels);

    /**
     * Would this addressee reach anyone? Computed with the notifier's own rules.
     *
     * <p>Returns {@code ADDRESSEE_TYPE_UNKNOWN} rather than throwing for an unrecognised type, so a
     * caller can report a bad addressee alongside good ones instead of losing the whole batch.
     */
    Reach reach(Addressee addressee);

    /** Every addressee in this tenant that reaches at least one active user, cheapest first. */
    List<Candidate> candidates();

    /**
     * Raise a real engine request against a change or one of its tasks, and send it.
     *
     * @return the created request, or {@code Optional.empty()} when the engine adapter is absent
     * @throws IllegalArgumentException when {@code requestType} is {@link #REFUSED_TYPE}, is not in
     *         {@link #RAISABLE_TYPES}, or when {@code addressee} reaches nobody — the message names
     *         the reason so the operator knows what to fix rather than that it "failed"
     */
    Optional<RequestRef> raise(Subject subject, Long subjectId, String requestType,
                               String titleEn, String titleAr, String description,
                               Addressee addressee, LocalDate dueDate, Integer slaHours,
                               String priority, String escalationRole);

    /** Engine requests anchored to this subject, newest first. Empty when the adapter is absent. */
    List<RequestRef> raised(Subject subject, Long subjectId);

    /** Engine requests anchored to any subject in this change — the change and all its tasks. */
    List<RequestRef> raisedForChange(Long changeId, List<Long> taskIds);
}
