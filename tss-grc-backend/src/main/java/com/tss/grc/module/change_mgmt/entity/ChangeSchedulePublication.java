package com.tss.grc.module.change_mgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * S8g / CE-8 — one publication of the forward schedule of change (V356).
 *
 * <p><b>Why the FSC needed a record at all.</b> {@code /calendar} has served the live schedule since
 * CH-17, and it changes the moment anybody reschedules. Nothing recorded what the business was
 * actually told, so "we published the forward schedule" was a claim with no artefact behind it — and
 * an unpublished forward schedule is a query, not a communication.
 *
 * <p><b>Why the snapshot is stored when S8f insisted findings must be derived.</b> These look like
 * opposite decisions and they are the same one. A reconciliation finding answers <i>what is true
 * now</i>, so storing it lets it drift from the rows. A publication answers <i>what did we tell the
 * business on the 4th</i> — a fact about the past that today's rows cannot reproduce, because the
 * schedule has moved since. Deriving it would answer a different question. {@code board_pack.packs}
 * (V26) made the same call for the same reason, and says so in its own migration.
 *
 * <p>{@code contentSha256} follows the evidence subsystem: a published schedule that cannot be shown
 * unaltered is an assertion about the past with nothing behind it.
 *
 * <h3>⚠ This row is immutable</h3>
 * No {@code updatedAt}, no {@code status}, and no revise operation. A published schedule that can be
 * edited afterwards is not a publication — superseding one means publishing again, so the meaningful
 * read is "the latest by {@code publishedAt}". V356 self-check 2 THROWs if either column ever appears,
 * because that is the shape somebody reaches for when they want to "fix" a published document.
 */
@Entity
@Table(schema = "change_mgmt", name = "change_schedule_publication")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ChangeSchedulePublication {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** The period the published schedule covers — not the period it was published in. */
    @Column(name = "window_from", nullable = false)
    private LocalDateTime windowFrom;

    @Column(name = "window_to", nullable = false)
    private LocalDateTime windowTo;

    /** Counts, so a list of publications reads without parsing every snapshot. */
    @Column(name = "entry_count", nullable = false)
    private Integer entryCount = 0;

    /** How many published entries declared a service outage (the PSO half of CE-8). */
    @Column(name = "outage_count", nullable = false)
    private Integer outageCount = 0;

    /** How many published entries fall inside an active freeze window. */
    @Column(name = "freeze_count", nullable = false)
    private Integer freezeCount = 0;

    @Column(name = "entries_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String entriesJson;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Column(name = "published_by_id")
    private Long publishedById;

    @Column(name = "published_by_name", nullable = false, length = 255)
    private String publishedByName;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
