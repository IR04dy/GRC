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

    /**
     * NIST 800-61r2 phase. State machine:
     *   IDENTIFICATION → ANALYSIS → CONTAINMENT → ERADICATION → RECOVERY → POST_INCIDENT
     * The existing `status` column tracks high-level state (OPEN | INVESTIGATING |
     * RESOLVED | CLOSED) and is auto-mapped from `nistPhase` by the service layer.
     */
    @Column(name = "nist_phase", nullable = false, length = 30)
    private String nistPhase = "IDENTIFICATION";

    /** NCA HEY 24-hour cyber-incident notification (NCA mandate). */
    @Column(name = "nca_hey_notified_at")
    private java.time.LocalDateTime ncaHeyNotifiedAt;
    /** Saudi Data Office 72-hour PDPL Article 20 notification. */
    @Column(name = "data_office_notified_at")
    private java.time.LocalDateTime dataOfficeNotifiedAt;
    /** CITC 24-hour notification (telco / cloud providers). */
    @Column(name = "citc_notified_at")
    private java.time.LocalDateTime citcNotifiedAt;
    /** SAMA Cyber Threat Intelligence Sharing (banks). */
    @Column(name = "sama_notified_at")
    private java.time.LocalDateTime samaNotifiedAt;
    /** HRSD breach affecting employee data. */
    @Column(name = "hrsd_notified_at")
    private java.time.LocalDateTime hrsdNotifiedAt;

    /** MITRE ATT&CK tactics — comma-separated tactic IDs, e.g. "TA0001,TA0002". */
    @Column(name = "mitre_tactics", length = 500)
    private String mitreTactics;
    /** MITRE ATT&CK techniques — comma-separated technique IDs, e.g. "T1078,T1133". */
    @Column(name = "mitre_techniques", length = 500)
    private String mitreTechniques;

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
