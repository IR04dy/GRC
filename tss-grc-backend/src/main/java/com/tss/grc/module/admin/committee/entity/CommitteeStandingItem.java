package com.tss.grc.module.admin.committee.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Recurring agenda item bound to a committee template. The annual-
 * calendar generator copies these into every meeting's agenda before
 * the secretary opens the agenda builder — so the secretary only has
 * to add ad-hoc items.
 *
 * <p>Items marked {@code isMandatory=1} cannot be removed by the
 * secretary; only the Chair can authorise their exclusion (e.g. for
 * special-purpose meetings).
 *
 * <p>The {@code sourceModule} column drives cross-module auto-population:
 * items with sourceModule='RISK' pre-load the latest risk-register
 * highlights, 'AUDIT' pre-loads open finding stats, 'CYBER' pre-loads the
 * SOC dashboard, and so on. NULL sourceModule = manual content.
 */
@Entity
@Table(schema = "admin", name = "committee_standing_items")
@Getter @Setter @NoArgsConstructor
public class CommitteeStandingItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false, length = 80)
    private String templateCode;

    @Column(nullable = false)
    private Integer seq;

    @Column(name = "topic_en", nullable = false, length = 500)
    private String topicEn;

    @Column(name = "topic_ar", nullable = false, length = 500)
    private String topicAr;

    @Column(name = "presenter_role", length = 120)
    private String presenterRole;

    @Column(name = "minutes_allocated", nullable = false)
    private Integer minutesAllocated = 15;

    @Column(name = "is_mandatory", nullable = false)
    private Short isMandatory = 1;

    @Column(name = "source_module", length = 40)
    private String sourceModule;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
