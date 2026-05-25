package com.tss.grc.module.openbanking.entity;

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
 * Timeline row across TPPs / consents / incidents.
 *
 * <p>Event types:
 * <pre>
 *   TPP_ONBOARDED | TPP_SUSPENDED | TPP_REVOKED | TPP_REINSTATED
 *   CONSENT_GRANTED | CONSENT_REVOKED | CONSENT_EXPIRED
 *   CERT_EXPIRING | CERT_ROTATED
 *   INCIDENT_RAISED | INCIDENT_STATUS_CHANGED | INCIDENT_RESOLVED
 *   API_ABUSE_DETECTED | BULK_REVOCATION_DETECTED
 *   INTERNAL_NOTE
 * </pre>
 */
@Entity
@Table(schema = "openbanking", name = "ob_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ObEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** Optional FK to TPP. */
    @Column(name = "tpp_id")
    private Long tppId;

    /** Optional FK to consent. */
    @Column(name = "consent_id")
    private Long consentId;

    /** Optional FK to incident. */
    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name", length = 256)
    private String actorName;

    @Column(name = "status_after", length = 32)
    private String statusAfter;

    @Column(name = "note_en", columnDefinition = "NVARCHAR(MAX)")
    private String noteEn;

    @Column(name = "note_ar", columnDefinition = "NVARCHAR(MAX)")
    private String noteAr;
}
