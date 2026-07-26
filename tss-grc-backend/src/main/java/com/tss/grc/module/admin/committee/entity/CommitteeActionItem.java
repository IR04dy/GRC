package com.tss.grc.module.admin.committee.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * First-class committee action item — replaces the V40
 * {@code action_items_json} blob with structured rows so actions can be
 * queried, escalated, and linked to cross-module records.
 *
 * <p>The cross-module linkage ({@code linkedModule}, {@code linkedEntityType},
 * {@code linkedEntityId}, {@code linkedEntityCode}) is the key data point —
 * actions are typically generated <i>because of</i> something in another
 * module (a risk, an audit finding, a policy review, an incident), and the
 * platform makes those links navigable from both ends.
 *
 * <p>The {@code carriedFromMeetingId} column tracks actions rolled forward
 * from a previous meeting into this one (the secretary's "matters arising"
 * automation).
 */
@Entity
@Table(schema = "admin", name = "committee_action_items")
@Getter @Setter @NoArgsConstructor
public class CommitteeActionItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "committee_id", nullable = false)
    private Long committeeId;

    @Column(name = "action_code", nullable = false, length = 80)
    private String actionCode;

    @Column(nullable = false)
    private Integer seq;

    @Column(name = "agenda_item_seq")
    private Integer agendaItemSeq;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    // F2-30 — house rule 7 ("Arabic names are backend-mandatory") was asserted in
    // CLAUDE.md and enforced nowhere. Measured 0 blank rows on this column before the
    // constraint landed, so it cannot strand an existing record.
    @NotBlank
    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "owner_name", nullable = false, length = 255)
    private String ownerName;

    @Column(name = "owner_role", length = 120)
    private String ownerRole;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** LOW | NORMAL | HIGH | CRITICAL */
    @Column(nullable = false, length = 20)
    private String priority = "NORMAL";

    /** OPEN | IN_PROGRESS | COMPLETED | OVERDUE | CANCELLED | DEFERRED */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "completion_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String completionNotesEn;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by")
    private Long completedBy;

    /** Cross-module linkage: which other record did this action come from
     *  or relate to. e.g. linkedModule=RISK, linkedEntityType=Risk,
     *  linkedEntityId=42, linkedEntityCode=RSK-2026-004 */
    @Column(name = "linked_module", length = 40)
    private String linkedModule;

    @Column(name = "linked_entity_type", length = 120)
    private String linkedEntityType;

    @Column(name = "linked_entity_id")
    private Long linkedEntityId;

    @Column(name = "linked_entity_code", length = 80)
    private String linkedEntityCode;

    /** If this action was rolled forward from a previous meeting, the
     *  source meeting id. Drives the "matters arising" section of the
     *  agenda auto-populator. */
    @Column(name = "carried_from_meeting_id")
    private Long carriedFromMeetingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
