package com.tss.grc.module.bcm.mtpd_inheritance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slice 10-E — Edge in the (business_process → vendor) dependency graph.
 *
 * <p>One row per (organisation, business_process, vendor) tuple. The
 * BCM Manager curates these edges as part of BIA finalisation; the
 * MTPD-inheritance compute engine walks the edges and joins each one
 * to the latest ACCEPTED {@code tprm.vendor_bcp_attestation} (Slice
 * 10-D) to determine whether the vendor's attested MTPD violates the
 * process's own MTPD.
 *
 * <p>Three classifications on {@code dependencyType}:
 * <ul>
 *   <li>{@code REQUIRED} — vendor failure stops the process. These are
 *       the rows that drive breach findings.</li>
 *   <li>{@code RECOMMENDED} — vendor failure degrades the process but
 *       a manual fallback exists. Findings surface as informational.</li>
 *   <li>{@code FALLBACK} — vendor used only when primary fails;
 *       excluded from inheritance compute.</li>
 * </ul>
 *
 * <p>And three on {@code dependencyStrength}:
 * <ul>
 *   <li>{@code TIGHT} — process stops immediately on vendor outage</li>
 *   <li>{@code LOOSE} — degraded mode acceptable for the MTPD window</li>
 *   <li>{@code TRANSIENT} — delay only; eventual consistency tolerable</li>
 * </ul>
 *
 * Strength feeds severity calculation in {@code MtpdInheritanceService}.
 */
@Entity
@Table(schema = "bcm", name = "process_vendor_dependency")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ProcessVendorDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "business_process_id", nullable = false)
    private Long businessProcessId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    /** REQUIRED | RECOMMENDED | FALLBACK. */
    @Column(name = "dependency_type", nullable = false, length = 40)
    private String dependencyType = "REQUIRED";

    /** TIGHT | LOOSE | TRANSIENT. */
    @Column(name = "dependency_strength", nullable = false, length = 20)
    private String dependencyStrength = "TIGHT";

    @Column(name = "notes_en", length = 1000)
    private String notesEn;

    @Column(name = "notes_ar", length = 1000)
    private String notesAr;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
