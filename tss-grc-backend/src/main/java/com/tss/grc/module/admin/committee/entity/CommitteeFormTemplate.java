package com.tss.grc.module.admin.committee.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Downloadable bilingual form template (charter, agenda, motion,
 * minutes, action register, COI declaration, skills matrix, annual
 * evaluation, etc.). Real markdown content — not Lorem-Ipsum
 * placeholders. Field placeholders use the [BRACKETED] convention so the
 * platform can auto-fill them from meeting / committee data.
 *
 * <p>{@code applicableTo} is a comma-separated list of
 * {@code committee_type} values (or "ALL"). The catalog page filters
 * forms by the committee the user is viewing.
 */
@Entity
@Table(schema = "admin", name = "committee_form_templates")
@Getter @Setter @NoArgsConstructor
public class CommitteeFormTemplate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_code", nullable = false, length = 80, unique = true)
    private String formCode;

    @Column(name = "form_type", nullable = false, length = 40)
    private String formType;

    @Column(name = "applicable_to", nullable = false, length = 500)
    private String applicableTo;

    @Column(name = "regulator_anchors", length = 500)
    private String regulatorAnchors;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 255)
    private String titleAr;

    @Column(name = "purpose_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String purposeEn;

    @Column(name = "purpose_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String purposeAr;

    @Column(name = "content_md_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String contentMdEn;

    @Column(name = "content_md_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String contentMdAr;

    @Column(name = "completion_guide_en", columnDefinition = "NVARCHAR(MAX)")
    private String completionGuideEn;

    @Column(name = "completion_guide_ar", columnDefinition = "NVARCHAR(MAX)")
    private String completionGuideAr;

    @Column(nullable = false, length = 20)
    private String version = "1.0";

    @Column(name = "is_active", nullable = false)
    private Short isActive = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
