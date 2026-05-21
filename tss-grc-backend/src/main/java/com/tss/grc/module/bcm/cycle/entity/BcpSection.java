package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * One ISO 22301 §8.4.2 section of a {@link BcpPlan} — audit T1.4b.
 *
 * <p>{@code sectionKey} is the stable identifier the UI keys off (e.g.
 * {@code "activation_criteria"}); {@code sectionTitleEn}/{@code Ar} carry
 * the human-readable label. {@code bodyEn}/{@code Ar} hold the section text
 * (plain or simple markdown — the dev frontend uses textarea + markdown
 * rendering; future polish could swap in Tiptap).
 *
 * <p>Sections are created at plan creation time from a canonical catalogue
 * (see {@code BcpPlanService.DEFAULT_SECTIONS_*}); users edit the bodies.
 * Adding ad-hoc sections beyond the catalogue is supported by simply
 * POSTing a new row — uniqueness is on {@code (plan_id, section_key)}.
 */
@Entity
@Table(schema = "bcm", name = "bcp_sections")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcpSection {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "section_key", nullable = false, length = 50)
    private String sectionKey;

    @Column(name = "section_title_en", nullable = false, length = 255)
    private String sectionTitleEn;

    @Column(name = "section_title_ar", length = 255)
    private String sectionTitleAr;

    @Column(name = "section_order", nullable = false)
    private Integer sectionOrder;

    @Column(name = "body_en", columnDefinition = "NVARCHAR(MAX)")
    private String bodyEn;

    @Column(name = "body_ar", columnDefinition = "NVARCHAR(MAX)")
    private String bodyAr;

    @Column(name = "last_edited_by", length = 255) private String        lastEditedBy;
    @Column(name = "last_edited_at")               private LocalDateTime lastEditedAt;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")                    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
