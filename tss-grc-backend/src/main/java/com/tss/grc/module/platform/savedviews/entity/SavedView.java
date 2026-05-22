package com.tss.grc.module.platform.savedviews.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Per-user, per-page saved view of (filter + sort + visible columns) — the
 * server-side equivalent of the localStorage shape established in
 * {@code tss-grc-ui-v2/src/lib/savedViews.ts}.
 *
 * <p>Sprint-4 Track-2.5 Phase 2. See V160 migration for rationale on the
 * JSON columns and the per-user-per-page default index.
 */
@Entity
@Table(schema = "platform", name = "saved_views")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SavedView {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Stable per-page key, e.g. {@code "cyber/vulns"}, {@code "risk/register"}. */
    @Column(name = "page_key", nullable = false, length = 120)
    private String pageKey;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    /** Phase-3 follow-up (org-wide). Persisted so the DTO round-trips. */
    @Column(name = "is_shared", nullable = false)
    private Boolean isShared = Boolean.FALSE;

    /** Opaque per-page filter blob — the DB never reads inside this. */
    @Column(name = "filter_json", columnDefinition = "NVARCHAR(MAX)")
    private String filterJson;

    /** Opaque per-page sort blob. */
    @Column(name = "sort_json", columnDefinition = "NVARCHAR(MAX)")
    private String sortJson;

    /** Opaque per-page visible-columns array. */
    @Column(name = "visible_columns_json", columnDefinition = "NVARCHAR(MAX)")
    private String visibleColumnsJson;

    /** URL-search-params snapshot for pages whose filter state lives in
     *  the URL (e.g. WorkflowSubmissionsPage, RiskRegisterPage). */
    @Column(name = "url_query", length = 2000)
    private String urlQuery;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = Boolean.FALSE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
