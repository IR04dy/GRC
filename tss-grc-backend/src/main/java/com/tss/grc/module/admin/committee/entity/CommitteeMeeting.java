package com.tss.grc.module.admin.committee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

@Entity
@Table(schema = "admin", name = "committee_meetings")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CommitteeMeeting {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "committee_id", nullable = false)
    private Long committeeId;

    @Column(name = "committee_code", length = 64)
    private String committeeCode;

    @Column(name = "committee_name_en", length = 255)
    private String committeeNameEn;

    @Column(name = "meeting_code", nullable = false, length = 64)
    private String meetingCode;

    @Column(name = "title_en", length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "ended_at")   private LocalDateTime endedAt;

    @Column(name = "location_or_link", length = 500)
    private String locationOrLink;

    @Column(name = "agenda_json",       columnDefinition = "NVARCHAR(MAX)") private String agendaJson;

    /**
     * ⚠ <b>Legacy, read-only (F2-28).</b> The free-text attendance blob predates structured
     * attendance and is a <em>second</em> caller-authored attendance surface — 32 meetings
     * carry one. {@code completeMeeting} no longer accepts it; the column is retained so
     * historical minutes stay readable, not because anything should write it.
     */
    @Column(name = "attendees_json",    columnDefinition = "NVARCHAR(MAX)") private String attendeesJson;

    @Column(name = "quorum_met")                                            private Short  quorumMet;

    /**
     * F2-28 / DF-5 — <b>where this meeting's quorum flag came from.</b>
     *
     * <p>{@code COMPUTED} — derived by the platform from seat-bound attendance against the
     * committee's active voting roster. {@code ASSERTED_LEGACY} — supplied by whoever called
     * {@code complete}, which was the only source that ever existed before V319 (17 meetings,
     * 15 of them e-signed). {@code NULL} — no quorum has been established.
     *
     * <p>The legacy rows are <b>labelled, not flipped</b>: a retroactive "no attendance ⇒ not
     * quorate" rule would invalidate approved, e-signed governance history to make a new
     * control look clean. V315 and V316 take the same stance on pre-fix votes and approvals.
     */
    @Column(name = "quorum_source", length = 20) private String quorumSource;

    /** The denominator actually used, persisted at compute time. The live roster changes;
     *  a past meeting's arithmetic must not. This is the roster snapshot. */
    @Column(name = "quorum_denominator") private Integer quorumDenominator;
    @Column(name = "quorum_present")     private Integer quorumPresent;
    @Column(name = "quorum_required")    private Integer quorumRequired;
    @Column(name = "quorum_computed_at") private LocalDateTime quorumComputedAt;

    /** Who last replaced the attendance sheet. Makes the replacement attributable, and is the
     *  input to the four-eyes gate on minutes approval (F2-39): the person who authored the
     *  attendance cannot approve the minutes that rest on it. */
    @Column(name = "attendance_recorded_by") private Long attendanceRecordedBy;
    @Column(name = "attendance_recorded_at") private LocalDateTime attendanceRecordedAt;
    @Column(name = "minutes_en",        columnDefinition = "NVARCHAR(MAX)") private String minutesEn;
    @Column(name = "minutes_ar",        columnDefinition = "NVARCHAR(MAX)") private String minutesAr;
    @Column(name = "decisions_json",    columnDefinition = "NVARCHAR(MAX)") private String decisionsJson;
    @Column(name = "action_items_json", columnDefinition = "NVARCHAR(MAX)") private String actionItemsJson;

    @Column(name = "chair_name",     length = 255) private String chairName;
    @Column(name = "secretary_name", length = 255) private String secretaryName;

    @Column(nullable = false, length = 20)
    private String status = "SCHEDULED";

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "minutes_approved_at") private LocalDateTime minutesApprovedAt;
    @Column(name = "minutes_approved_by") private Long minutesApprovedBy;
    @Column(name = "approver_name", length = 255) private String approverName;

    @Column(name = "external_id", length = 64) private String externalId;
    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
    @Column(name = "is_active",  nullable = false) private Short isActive  = 1;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "deleted_by") private Long deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
