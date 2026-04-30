package com.tss.grc.module.incident.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;

@Entity
@Table(schema = "cyber", name = "incidents")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Incident extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "status", length = 50)
    private String status = "OPEN";

    @Column(name = "assignee", length = 255)
    private String assignee;

    @Column(name = "discovery_date")
    private LocalDate discoveryDate;

    @Column(name = "resolution_date")
    private LocalDate resolutionDate;

    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    /** JSON array of timeline events: [{action, actionAr, date}] */
    @Column(name = "timeline_json", columnDefinition = "NVARCHAR(MAX)")
    private String timelineJson;

    /** Lessons learned text (EN) */
    @Column(name = "lessons_learned", columnDefinition = "NVARCHAR(MAX)")
    private String lessonsLearned;

    /** Lessons learned text (AR) */
    @Column(name = "lessons_learned_ar", columnDefinition = "NVARCHAR(MAX)")
    private String lessonsLearnedAr;
}
