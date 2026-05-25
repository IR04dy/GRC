package com.tss.grc.module.dataclass.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A controlled deviation from data classification or handling policy. */
@Entity
@Table(schema = "dataclass", name = "data_exception")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DataException extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "data_asset_id")  private Long dataAssetId;
    @Column(name = "data_flow_id")   private Long dataFlowId;
    @Column(name = "reference", nullable = false, length = 64) private String reference;

    /** ENCRYPTION_AT_REST_WAIVER/MASKING_WAIVER/RETENTION_OVERRIDE/CROSS_BORDER_TEMP/OTHER. */
    @Column(name = "exception_type", length = 32) private String exceptionType;

    /** REQUESTED/APPROVED/ACTIVE/EXPIRED/REJECTED/REVOKED. */
    @Column(name = "status", nullable = false, length = 16) private String status = "REQUESTED";

    @Column(name = "justification_en", columnDefinition = "NVARCHAR(MAX)") private String justificationEn;
    @Column(name = "justification_ar", columnDefinition = "NVARCHAR(MAX)") private String justificationAr;

    @Column(name = "compensating_controls_en", columnDefinition = "NVARCHAR(MAX)") private String compensatingControlsEn;
    @Column(name = "compensating_controls_ar", columnDefinition = "NVARCHAR(MAX)") private String compensatingControlsAr;

    @Column(name = "requested_by_user_id")  private Long requestedByUserId;
    @Column(name = "approver_user_id")      private Long approverUserId;
    @Column(name = "approved_at")           private LocalDateTime approvedAt;
    @Column(name = "valid_from")            private LocalDate validFrom;
    @Column(name = "valid_until")           private LocalDate validUntil;
    @Column(name = "revoked_at")            private LocalDate revokedAt;

    @Column(name = "rejection_reason_en", columnDefinition = "NVARCHAR(MAX)") private String rejectionReasonEn;
    @Column(name = "rejection_reason_ar", columnDefinition = "NVARCHAR(MAX)") private String rejectionReasonAr;
}
