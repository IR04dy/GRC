package com.tss.grc.module.cyber.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Cloud service inventory entry — backs NCA CSCC §1.1 (cloud service catalogue).
 *
 * <p>One row per (service × provider) used by the tenant. Used by:
 * <ul>
 *   <li>NCA Cybersecurity dashboard — counts of public-cloud services
 *       hosting Confidential+ data outside the Kingdom.</li>
 *   <li>TPRM — vendor dependency analysis when a provider is decommissioned.</li>
 *   <li>BCM — dependency mapping for critical services.</li>
 * </ul>
 *
 * <p>Lifecycle: ACTIVE → UNDER_REVIEW (compliance check pending) →
 * ACTIVE | DEPROVISIONED → ARCHIVED.
 */
@Entity
@Table(schema = "cyber", name = "cloud_services")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CloudService extends BaseEntity {

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @Column(name = "provider", nullable = false, length = 255)
    private String provider;

    /** IAAS | PAAS | SAAS | FAAS | DBAAS */
    @Column(name = "service_model", nullable = false, length = 20)
    private String serviceModel = "SAAS";

    /** PUBLIC | PRIVATE | HYBRID | COMMUNITY */
    @Column(name = "deployment_model", nullable = false, length = 20)
    private String deploymentModel = "PUBLIC";

    /** PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED | TOP_SECRET (NCA / NDMO scale) */
    @Column(name = "data_classification", length = 30)
    private String dataClassification;

    @Column(name = "residency_region", length = 100)
    private String residencyRegion;

    @Column(name = "kingdom_data_resident", nullable = false)
    private Boolean kingdomDataResident = false;

    /** NCA CSCC tier — "1" | "2" | "3". String to allow future fractional tiers. */
    @Column(name = "cscc_tier", length = 10)
    private String csccTier;

    /** Soft FK to scm.contracts. */
    @Column(name = "contract_id")
    private Long contractId;

    /** Soft FK to iam.users. */
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    @Column(name = "criticality", nullable = false, length = 20)
    private String criticality = "MEDIUM";

    @Column(name = "sso_enabled",           nullable = false) private Boolean ssoEnabled           = false;
    @Column(name = "mfa_enforced",          nullable = false) private Boolean mfaEnforced          = false;
    @Column(name = "encryption_at_rest",    nullable = false) private Boolean encryptionAtRest    = false;
    @Column(name = "encryption_in_transit", nullable = false) private Boolean encryptionInTransit = true;

    /** ACTIVE | UNDER_REVIEW | DEPROVISIONED | ARCHIVED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "last_review_date") private LocalDate lastReviewDate;
    @Column(name = "next_review_date") private LocalDate nextReviewDate;

    @Column(name = "notes", length = 1000)
    private String notes;
}
