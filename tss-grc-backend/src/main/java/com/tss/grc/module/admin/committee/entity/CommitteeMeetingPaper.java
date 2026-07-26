package com.tss.grc.module.admin.committee.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Pre-read paper tracking — board-secretary discipline that CMA, SAMA, and
 * good-governance frameworks all require: papers must be circulated ahead
 * of the meeting (typically 5 working days for board, 3 for committees),
 * and read-receipts are part of the audit trail proving members had time
 * to prepare.
 *
 * <p>The {@code circulatedAt} timestamp drives the "late paper" flag:
 * anything circulated within 24h of the meeting is automatically marked
 * {@code latePaper=1} and must carry a {@code latePaperReason}.
 *
 * <p>{@code readReceiptsJson} is a JSON array of {@code [{userId, readAt}]}
 * — populated when a member opens the paper in the DMS or the platform.
 * Drives the read-rate KPI that appears on the meeting detail.
 *
 * <p>{@code fileStorageRef} is a DMS pointer (S3 key, Notion URL,
 * SharePoint path) — the platform never stores the file itself, just the
 * reference. Aligns with PDPL data-minimisation: the GRC platform is for
 * governance metadata, not content storage.
 */
@Entity
@Table(schema = "admin", name = "committee_meeting_papers")
@Getter @Setter @NoArgsConstructor
public class CommitteeMeetingPaper {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "paper_code", nullable = false, length = 80)
    private String paperCode;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    // F2-30 — house rule 7 ("Arabic names are backend-mandatory") was asserted in
    // CLAUDE.md and enforced nowhere. Measured 0 blank rows on this column before the
    // constraint landed, so it cannot strand an existing record.
    @NotBlank
    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "agenda_item_seq")
    private Integer agendaItemSeq;

    @Column(name = "author_name", length = 255)
    private String authorName;

    @Column(name = "author_role", length = 120)
    private String authorRole;

    @Column(name = "circulated_at")
    private LocalDateTime circulatedAt;

    @Column(name = "circulated_by")
    private Long circulatedBy;

    /** PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED */
    @Column(nullable = false, length = 20)
    private String confidentiality = "INTERNAL";

    /** DMS reference (S3 key, Notion URL, SharePoint path) — not the file. */
    @Column(name = "file_storage_ref", length = 500)
    private String fileStorageRef;

    /** JSON array: [{userId, readAt}] */
    @Column(name = "read_receipts_json", columnDefinition = "NVARCHAR(MAX)")
    private String readReceiptsJson;

    @Column(name = "late_paper", nullable = false)
    private Short latePaper = 0;

    @Column(name = "late_paper_reason", length = 500)
    private String latePaperReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
