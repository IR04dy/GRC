package com.tss.grc.module.intake.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * IntakeRecord — DD-82.
 *
 * Centralised front-door persistence for every kind of new submission
 * across the platform. Created from the React intake substrate
 * (intake.ts) when an authenticated employee or public submitter posts
 * a form. Lives until triaged and promoted to a register-specific row,
 * then stays as the audit-trail anchor.
 *
 * Schema mirrors the IntakeRecord interface on the frontend: same
 * statuses, same kinds, same payload shape.
 */
@Entity
@Table(schema = "intake", name = "intake_records")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class IntakeRecord extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "reference_code", nullable = false, length = 40, unique = true)
    private String referenceCode;

    /** IntakeKind from the frontend — drives form schema, SLA, target register. */
    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    /** SUBMITTED | TRIAGING | PROMOTED | REJECTED | CLOSED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "SUBMITTED";

    /** WEB_FORM | EMAIL | PHONE | IN_PERSON | API_INGESTION | MOBILE. */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "submitter_is_anonymous", nullable = false)
    private Boolean submitterIsAnonymous = false;

    /**
     * The platform user who filed this submission — F2-172, V374.
     *
     * <p>Set from the security context by {@code IntakeService#submit}, never from the request
     * body. {@code NULL} is a meaningful value and not a gap: an anonymous submission of an
     * anonymity-allowing kind (SPEAKUP / BREACH_REPORT / VRP_DISCLOSURE) stores no identity at all,
     * because ISO 37001 §8.3 and Nazaha expect a whistleblowing channel to be genuinely anonymous.
     *
     * <p>No {@code @ManyToOne} to a user entity, deliberately: a submission is an immutable record
     * of an event and must survive the account being deleted, exactly as an audit-log row does.
     */
    @Column(name = "submitter_user_id")
    private Long submitterUserId;

    @Column(name = "submitter_role", length = 20)
    private String submitterRole;

    @Column(name = "submitter_name", length = 200)
    private String submitterName;

    @Column(name = "submitter_email", length = 254)
    private String submitterEmail;

    @Column(name = "submitter_phone", length = 50)
    private String submitterPhone;

    /** Form payload as JSON. Validated by the service layer per kind. */
    @Lob
    @Column(name = "payload", columnDefinition = "NVARCHAR(MAX)")
    private String payload;

    @Column(name = "assigned_to", length = 50)
    private String assignedTo;

    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "acknowledged_at", nullable = false)
    private LocalDateTime acknowledgedAt;

    /** Promotion outcome — set when the triager promotes to a register. */
    @Column(name = "promoted_to_entity_type", length = 30)
    private String promotedToEntityType;

    @Column(name = "promoted_to_record_id", length = 40)
    private String promotedToRecordId;

    @Column(name = "promoted_at")
    private LocalDateTime promotedAt;

    @Lob
    @Column(name = "rejection_reason", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReason;

    /** Reply locale at submission time. Drives email reply language. */
    @Column(name = "locale", length = 2)
    private String locale;

    @OneToMany(mappedBy = "intakeRecord", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("at ASC")
    private List<IntakeEvent> history = new ArrayList<>();
}
