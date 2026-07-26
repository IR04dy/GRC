package com.tss.grc.module.change_mgmt.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(schema = "change_mgmt", name = "change_tasks")
@Getter @Setter @NoArgsConstructor
public class ChangeTask {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_request_id", nullable = false)
    private Long changeRequestId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    // F2-30 — house rule 7 ("Arabic names are backend-mandatory") was asserted in
    // CLAUDE.md and enforced nowhere. Measured 0 blank rows on this column before the
    // constraint landed, so it cannot strand an existing record.
    @NotBlank
    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "owner_name", length = 255)
    private String ownerName;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING"; // PENDING | IN_PROGRESS | DONE | BLOCKED | SKIPPED

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;
}
