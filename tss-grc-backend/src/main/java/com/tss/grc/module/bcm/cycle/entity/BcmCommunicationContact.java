package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Communication contact — Sprint BCM-2.
 *
 * <p>Backs the call-tree workflow (BCM Improvement Plan §3.K).
 * Records emergency contacts, escalation levels, and the most-recent
 * call-tree test outcome per contact.
 */
@Entity
@Table(schema = "bcm", name = "communication_contacts")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmCommunicationContact {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "role_title", length = 255)
    private String roleTitle;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "primary_phone", length = 40)
    private String primaryPhone;

    @Column(name = "secondary_phone", length = 40)
    private String secondaryPhone;

    @Column(length = 255)
    private String email;

    /** 1..5 — first responder → executive → crisis manager. */
    @Column(name = "escalation_level", nullable = false)
    // F2-240 - no initialiser ON PURPOSE. This entity is bound by its own PUT, so an
    // omitted key deserialises to the initialiser and the update's `!= null` guard can
    // never be false - the line reads as a partial update and behaves as a full
    // replace. The default is owned by BcmCommunicationContactService#create, asserted
    // by NeverFiringNullGuardContractTest in both directions.
    private Integer escalationLevel;

    @Column(name = "related_plan_id")
    private Long relatedPlanId;

    /** AVAILABLE | ON_LEAVE | UNAVAILABLE | BACKUP */
    @Column(name = "availability_status", nullable = false, length = 20)
    // F2-240 - no initialiser ON PURPOSE. This entity is bound by its own PUT, so an
    // omitted key deserialises to the initialiser and the update's `!= null` guard can
    // never be false - the line reads as a partial update and behaves as a full
    // replace. The default is owned by BcmCommunicationContactService#create, asserted
    // by NeverFiringNullGuardContractTest in both directions.
    private String availabilityStatus;

    @Column(name = "last_tested_date")
    private LocalDate lastTestedDate;

    /** PASSED | FAILED | NOT_REACHED */
    @Column(name = "last_test_result", length = 20)
    private String lastTestResult;

    /** ACTIVE | INACTIVE | ARCHIVED */
    @Column(nullable = false, length = 20)
    // F2-240 - no initialiser ON PURPOSE. This entity is bound by its own PUT, so an
    // omitted key deserialises to the initialiser and the update's `!= null` guard can
    // never be false - the line reads as a partial update and behaves as a full
    // replace. The default is owned by BcmCommunicationContactService#create, asserted
    // by NeverFiringNullGuardContractTest in both directions.
    private String status;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
