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
     * CY-B (C-CRIT-3): when non-null, this register row was created to mirror a
     * /cyber SOC-workspace incident ({@code cyber.cyber_incidents.id}). Lets the
     * mirror stay idempotent (one register row per workspace incident) and marks
     * workspace-origin incidents in the register.
     */
    @Column(name = "source_workspace_incident_id")
    private Long sourceWorkspaceIncidentId;

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

    /*
     * ── PDPL classification (F2-281) ────────────────────────────────────────
     * These eight columns are the cyber → PDPL bridge's half of this row. They
     * are written by ONE writer, {@code privacy.bridge.CyberPdplBridgeService},
     * in native SQL, and until F2-281 they were mapped by nothing — so whether
     * an incident affects personal data was not on the incident's own wire, and
     * the PDPL Article 20 notification this service RENDERS omitted the two
     * facts Article 20 turns on while the same row stored both.
     *
     * Mapped {@code insertable = false, updatable = false} deliberately:
     *   - the classification is a privacy-module decision, so no incident-module
     *     write path may forge one (the hazard is absent, not merely latent);
     *   - {@code affects_personal_data} is NOT NULL DEFAULT 0, so leaving it out
     *     of Hibernate's INSERT is what lets the database default apply — which
     *     is correct: a new incident affects no personal data until classified.
     * Reading them here is what puts them on the wire; writing them stays with
     * the bridge.
     *
     * <p>Each also carries {@code @Setter(AccessLevel.NONE)} -- the first use of
     * that idiom in this codebase, and the reason is worth the line. A Lombok
     * setter over a non-updatable column compiles, runs and silently does
     * nothing, which is the decoy shape F2-184 named: it answers the question a
     * reader was about to ask. Suppressing it makes the write a COMPILE error,
     * which is stronger than a gate and cannot rot.
     */

    /** 1 once the bridge has classified this incident as affecting personal data. */
    @Column(name = "affects_personal_data", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Short affectsPersonalData;

    /** Estimated count of affected data subjects — PDPL Art. 20 notification input. */
    @Column(name = "data_subjects_affected_est", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Integer dataSubjectsAffectedEst;

    /** Comma-separated categories of personal data affected, e.g. "EMAIL,NATIONAL_ID". */
    @Column(name = "data_categories_affected", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String dataCategoriesAffected;

    /** The DPO's narrative assessment recorded when the incident was classified. */
    @Column(name = "personal_data_pii_assessment", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String personalDataPiiAssessment;

    /** FK to the {@code privacy.data_breaches} row the bridge auto-created. */
    @Column(name = "linked_data_breach_id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Long linkedDataBreachId;

    /** The linked breach's {@code external_id}, the reference an SDAIA filing cites. */
    @Column(name = "linked_data_breach_code", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String linkedDataBreachCode;

    @Column(name = "pii_classified_at", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private java.time.LocalDateTime piiClassifiedAt;

    @Column(name = "pii_classified_by", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Long piiClassifiedBy;

    /**
     * The one predicate for "has this incident been classified as affecting
     * personal data?". It exists because four surfaces ask it — the PDPL
     * Article 20 body, the HRSD body, the regulatory-template applicability and
     * the response DTO — and F2-280 is what a second copy of a governance
     * predicate costs: two readers of one fact that disagreed on 12 roles.
     *
     * <p>The column is {@code smallint NOT NULL DEFAULT 0}, so the test is
     * {@code != 0} rather than a truthiness check, and a null (an unsaved
     * instance) reads as NOT classified — never as classified.
     */
    public boolean isPiiConfirmed() {
        return affectsPersonalData != null && affectsPersonalData != 0;
    }

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
