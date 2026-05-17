package com.tss.grc.module.platform.licensing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Module licensing entitlement — Framework Intersection F9.
 *
 * <p>One row per (organisation, module). Saudi GRC prompt §18
 * wants each module separately licensable; this is the table the
 * LicenseGuard checks before letting a request through, and the
 * authoritative source for the JWT {@code enabled_modules} claim
 * the frontend reads to hide unlicensed sidebar entries.
 */
@Entity
@Table(schema = "platform", name = "organisation_licenses")
@Getter @Setter @NoArgsConstructor
public class OrganisationLicense {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "module_code", nullable = false, length = 40)
    private String moduleCode;

    /** FREE | PRO | ENTERPRISE */
    @Column(nullable = false, length = 20)
    private String tier = "PRO";

    @Column(name = "seat_count")
    private Integer seatCount;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom = LocalDate.now();

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
