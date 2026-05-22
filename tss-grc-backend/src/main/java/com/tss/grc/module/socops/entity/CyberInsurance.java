package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cyber insurance policy + rolled-up claim history. Cyber-insurance
 * underwriters in KSA require evidence of GRC maturity to issue or renew
 * a policy — this table is the artefact a broker like Marsh Saudi Arabia
 * pulls from each renewal cycle.
 */
@Entity
@Table(schema = "socops", name = "cyber_insurance")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CyberInsurance extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "policy_number", nullable = false, length = 80) private String policyNumber;
    @Column(name = "carrier_name", nullable = false, length = 255) private String carrierName;
    @Column(name = "broker_name", length = 255) private String brokerName;
    @Column(name = "policy_type", nullable = false, length = 40) private String policyType;
    @Column(name = "coverage_areas", columnDefinition = "NVARCHAR(MAX)") private String coverageAreas;

    @Column(name = "aggregate_limit_sar", precision = 18, scale = 2) private BigDecimal aggregateLimitSar;
    @Column(name = "per_event_limit_sar", precision = 18, scale = 2) private BigDecimal perEventLimitSar;
    @Column(name = "retention_sar", precision = 18, scale = 2) private BigDecimal retentionSar;

    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "effective_to", nullable = false) private LocalDate effectiveTo;
    @Column(name = "renewal_due_at") private LocalDate renewalDueAt;
    @Column(nullable = false, length = 30) private String status = "ACTIVE";

    @Column(name = "underwriting_questionnaire_id") private Long underwritingQuestionnaireId;
    @Column(name = "last_renewal_responses_at") private LocalDateTime lastRenewalResponsesAt;

    @Column(name = "claim_count", nullable = false) private Integer claimCount = 0;
    @Column(name = "last_claim_at") private LocalDateTime lastClaimAt;
    @Column(name = "last_claim_paid_sar", precision = 18, scale = 2) private BigDecimal lastClaimPaidSar;
    @Column(name = "aggregate_claims_paid_sar", precision = 18, scale = 2) private BigDecimal aggregateClaimsPaidSar;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;
}
