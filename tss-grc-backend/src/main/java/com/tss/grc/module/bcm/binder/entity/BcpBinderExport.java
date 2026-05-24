package com.tss.grc.module.bcm.binder.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slice 10-F — One row per generated PDF binder export.
 *
 * <p>The binder PDF itself lives on disk under
 * {@code ${grc.storage.base-path}/binders/<org_id>/<external_id>.pdf}.
 * This row is the metadata index + audit record over that file. Status
 * lifecycle:
 *
 * <pre>
 *   GENERATING ──► READY ──► ARCHIVED
 *       └──────► FAILED  (terminal — error_message captured)
 * </pre>
 *
 * <p>Multi-tenancy is enforced via {@code organisationId} on every
 * finder (per CLAUDE.md). Soft-delete via {@code @Where(is_deleted = 0)}.
 * ROWVERSION on the column for optimistic concurrency on archive flows.
 */
@Entity
@Table(schema = "bcm", name = "bcp_binder_export")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcpBinderExport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "bcp_plan_id", nullable = false)
    private Long bcpPlanId;

    @Column(name = "export_code", nullable = false, length = 64)
    private String exportCode;

    /** PDF | DOCX | ZIP — only PDF is implemented in 10-F. */
    @Column(nullable = false, length = 20)
    private String format = "PDF";

    /** EN | AR | BILINGUAL — sequential half-stitched layout when BILINGUAL. */
    @Column(nullable = false, length = 10)
    private String language = "BILINGUAL";

    /** Captured from {@code BcpPlan.currentVersion} at generation time (e.g. "v3"). */
    @Column(name = "version_label", length = 64)
    private String versionLabel;

    @Column(name = "watermark_text", nullable = false, length = 255)
    private String watermarkText =
        "Controlled — Not for Distribution / محكوم — غير قابل للتوزيع";

    /** GENERATING | READY | FAILED | ARCHIVED */
    @Column(nullable = false, length = 20)
    private String status = "GENERATING";

    @Column(name = "error_message", columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Column(name = "generated_by_user_id")
    private Long generatedByUserId;

    @Column(name = "generated_by_name", length = 255)
    private String generatedByName;

    @Column(name = "generation_duration_ms")
    private Integer generationDurationMs;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "byte_size")
    private Integer byteSize;

    /** Relative to {@code grc.storage.base-path}. Format: {@code binders/<org>/<uuid>.pdf}. */
    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount = 0;

    @Column(name = "last_downloaded_at")
    private LocalDateTime lastDownloadedAt;

    @Column(name = "last_downloaded_by_user_id")
    private Long lastDownloadedByUserId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
