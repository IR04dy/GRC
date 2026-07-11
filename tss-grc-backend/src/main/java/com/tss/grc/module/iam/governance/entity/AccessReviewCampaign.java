package com.tss.grc.module.iam.governance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RBAC Access-Governance P2 (plan §6.5) — one access recertification campaign.
 * Items are snapshotted from {@code iam.user_roles} at creation; closure is blocked
 * until every item is dispositioned, or exception-approved by GRC_ADMINISTRATOR.
 */
@Entity
@Table(schema = "iam", name = "access_review_campaigns")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AccessReviewCampaign extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", nullable = false, updatable = false) private UUID externalId = UUID.randomUUID();
    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    /** PRIVILEGED | MODULE | ALL. */
    @Column(nullable = false, length = 20) private String scope;
    @Column(name = "scope_module", length = 40) private String scopeModule;
    @Column(name = "window_start", nullable = false) private LocalDate windowStart;
    @Column(name = "window_end", nullable = false) private LocalDate windowEnd;
    @Column(name = "window_hijri", length = 120) private String windowHijri;
    /** OPEN | CLOSED. */
    @Column(nullable = false, length = 15) private String status = "OPEN";
    @Column(name = "closed_at") private LocalDateTime closedAt;
    @Column(name = "closed_by") private Long closedBy;
    @Column(name = "exception_note", length = 1000) private String exceptionNote;
}
