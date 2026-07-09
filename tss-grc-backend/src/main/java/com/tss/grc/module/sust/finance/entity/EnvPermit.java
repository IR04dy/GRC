package com.tss.grc.module.sust.finance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.util.UUID;

/** G4 (plan §5.6 / REG-5) — an NCEC environmental permit with expiry surveillance;
 *  permits inside the lead window (or expired) raise an automatic CAPA. */
@Entity
@Table(schema = "sust", name = "env_permits")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EnvPermit extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", nullable = false, updatable = false) private UUID externalId = UUID.randomUUID();
    @Column(name = "permit_number", nullable = false, length = 60) private String permitNumber;
    @Column(name = "facility_en", nullable = false, length = 300) private String facilityEn;
    @Column(name = "facility_ar", length = 300) private String facilityAr;
    @Column(nullable = false, length = 40) private String authority = "NCEC";
    @Column(name = "permit_type", length = 80) private String permitType;
    @Column(name = "issue_date") private LocalDate issueDate;
    @Column(name = "expiry_date", nullable = false) private LocalDate expiryDate;
    @Column(name = "conditions_en", columnDefinition = "NVARCHAR(MAX)") private String conditionsEn;
    @Column(name = "conditions_ar", columnDefinition = "NVARCHAR(MAX)") private String conditionsAr;
    @Column(name = "monitoring_frequency", length = 40) private String monitoringFrequency;
    /** VALID | SUSPENDED | EXPIRED. */
    @Column(nullable = false, length = 20) private String status = "VALID";
}
