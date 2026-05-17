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
    @Column(name = "attendees_json",    columnDefinition = "NVARCHAR(MAX)") private String attendeesJson;
    @Column(name = "quorum_met")                                            private Short  quorumMet;
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
