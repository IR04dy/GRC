package com.tss.grc.module.fraud.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Investigation timeline entry for a {@link FraudCase} — Sprint 14.
 *
 * <p>One row per investigator-recorded event:
 * <ul>
 *   <li>{@code TRIAGE_DECISION} — initial triage outcome + severity rating</li>
 *   <li>{@code INVESTIGATOR_ASSIGNED} — assignment change</li>
 *   <li>{@code STATUS_CHANGED} — state-machine transition</li>
 *   <li>{@code EVIDENCE_RECORDED} — investigator added evidence pointer</li>
 *   <li>{@code FINDING_RECORDED} — interim or final finding</li>
 *   <li>{@code STR_FILED} — SAFIU STR submitted</li>
 *   <li>{@code LAW_ENFORCEMENT_NOTIFIED} — referral to CID etc.</li>
 *   <li>{@code RECOVERY_RECORDED} — funds recovered</li>
 *   <li>{@code CLOSURE} — case closed with notes</li>
 * </ul>
 *
 * <p>The detail/timeline UI renders these in reverse chronological
 * order with the bilingual {@link #noteEn} / {@link #noteAr} fields.
 */
@Entity
@Table(schema = "fraud", name = "fraud_case_update")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class FraudCaseUpdate extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    /** See class-level javadoc for the type taxonomy. */
    @Column(name = "update_type", nullable = false, length = 32)
    private String updateType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    /** User who recorded the update — {@code iam.user.id}. */
    @Column(name = "actor_user_id")
    private Long actorUserId;

    /** Display name of actor (denormalised for audit-stability). */
    @Column(name = "actor_name", length = 256)
    private String actorName;

    @Column(name = "note_en", columnDefinition = "NVARCHAR(MAX)")
    private String noteEn;

    @Column(name = "note_ar", columnDefinition = "NVARCHAR(MAX)")
    private String noteAr;

    /** Snapshot of case status AFTER this update (for status-change rows). */
    @Column(name = "status_after", length = 32)
    private String statusAfter;

    /** Optional reference to evidence (URL, document id, exhibit number). */
    @Column(name = "evidence_reference", length = 512)
    private String evidenceReference;
}
