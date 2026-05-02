package com.tss.grc.module.evidence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Version snapshot of an evidence item — assessment §5.9 recommendation #1.
 * Replacing an evidence file produces a new EvidenceVersion row; the previous
 * version is retained read-only. Forms a per-evidence WORM hash chain via
 * {@code previousHash}.
 */
@Entity
@Table(schema = "evidence", name = "evidence_versions")
@Getter @Setter @NoArgsConstructor
public class EvidenceVersion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "title_en", length = 300)
    private String titleEn;

    @Column(name = "title_ar", length = 300)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "evidence_hash", length = 128)
    private String evidenceHash;

    @Column(name = "previous_hash", length = 128)
    private String previousHash;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "change_summary", length = 1000)
    private String changeSummary;

    @Column(name = "snapshotted_at", nullable = false, updatable = false)
    private LocalDateTime snapshottedAt = LocalDateTime.now();

    @Column(name = "snapshotted_by")
    private Long snapshottedBy;
}
