package com.tss.grc.module.dataclass.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cross-system data transfer linking a source DataAsset to a destination system. */
@Entity
@Table(schema = "dataclass", name = "data_flow")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DataFlow extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "data_asset_id", nullable = false)   private Long dataAssetId;
    @Column(name = "reference", nullable = false, length = 64) private String reference;

    @Column(name = "source_system_label", length = 256)      private String sourceSystemLabel;
    @Column(name = "destination_system_label", length = 256) private String destinationSystemLabel;

    @Column(name = "destination_country", length = 8) private String destinationCountry;
    /** IN_KINGDOM/GCC/ADEQUATE/NON_ADEQUATE — PDPL Art.7 adequacy grade. */
    @Column(name = "destination_adequacy", length = 16) private String destinationAdequacy;

    /** BATCH/STREAMING/API/FILE_TRANSFER/REPLICATION. */
    @Column(name = "transfer_mode", length = 16) private String transferMode;

    /** PROPOSED/APPROVED/ACTIVE/SUSPENDED/TERMINATED/REJECTED. */
    @Column(name = "status", nullable = false, length = 16) private String status = "PROPOSED";

    @Column(name = "purpose_en", columnDefinition = "NVARCHAR(MAX)") private String purposeEn;
    @Column(name = "purpose_ar", columnDefinition = "NVARCHAR(MAX)") private String purposeAr;

    @Column(name = "pdpl_art7_transfer_ref", length = 64) private String pdplArt7TransferRef;

    @Column(name = "approved_at")  private LocalDateTime approvedAt;
    @Column(name = "activated_at") private LocalDate activatedAt;
    @Column(name = "suspended_at") private LocalDate suspendedAt;
    @Column(name = "terminated_at") private LocalDate terminatedAt;

    @Column(name = "rejection_reason_en", columnDefinition = "NVARCHAR(MAX)") private String rejectionReasonEn;
    @Column(name = "rejection_reason_ar", columnDefinition = "NVARCHAR(MAX)") private String rejectionReasonAr;
}
