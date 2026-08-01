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

    /**
     * FREE | PRO | ENTERPRISE.
     *
     * <p>⚠ <b>No field initialiser, deliberately — and this class is also the request body.</b>
     * {@code tier}, {@code validFrom} and {@code enabled} used to be initialised here to
     * {@code "PRO"}, {@code LocalDate.now()} and {@code true}. {@code LicensingService.upsert}
     * applies a partial update with {@code if (body.getX() != null) existing.setX(...)}, so a
     * defaulted field is <em>never</em> null and every partial update silently overwrote all three.
     * Live-verified 2026-08-01: re-enabling one tenant's ESG licence with
     * {@code {"enabled": true\}} rewrote its tier from ENTERPRISE to PRO. The other two are worse —
     * a patch to {@code notes} reset the licence start date to today and <b>silently re-enabled a
     * module the business had deliberately disabled</b>, undoing the one commercial control this
     * table exists to carry.
     *
     * <p>The defaults live in {@code upsertInternal}'s create branch, which already applied them
     * behind three {@code == null} checks that could never fire — dead code that proved the author
     * expected these fields to arrive null. Removing the initialisers makes that code live and the
     * update branch honest. <b>Do not reintroduce them:</b> for an entity that doubles as a partial
     * request body, a field default is indistinguishable from a value the caller sent.
     */
    @Column(nullable = false, length = 20)
    private String tier;

    @Column(name = "seat_count")
    private Integer seatCount;

    /** No initialiser — see {@link #tier}. Defaulted on create by {@code LicensingService}. */
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    /** No initialiser — see {@link #tier}. A default here re-enables what {@code disable} turned off. */
    @Column(nullable = false)
    private Boolean enabled;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
