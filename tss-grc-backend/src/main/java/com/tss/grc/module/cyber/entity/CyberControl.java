package com.tss.grc.module.cyber.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDateTime;

@Entity
@Table(schema = "cyber", name = "cyber_controls")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CyberControl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "framework_id")
    private Long frameworkId;

    /**
     * The derived English label of {@link #frameworkId}'s framework.
     *
     * <p>⚠ F2-277 — this carried NO {@code length} at all, so the bean assumed JPA's 255 default
     * against a NVARCHAR(100) column. That was harmless until V390 widened
     * {@code cyber_frameworks.name} to 255 to mirror {@code name_ar}: a framework name of 101..255
     * characters then became legal in the parent and refused by this copy, which answered 500 on
     * the create (probed live). V391 widens the column and this declares the width, so the two
     * agree for the first time. <b>A width invariant scoped to a bilingual pair cannot see a
     * derived copy in another table, and widening the source is what makes the copy overflow.</b>
     */
    @Column(name = "framework_name", length = 255)
    private String frameworkName;

    /**
     * F2-277 — the Arabic half of the same derived label, from
     * {@code cyber_frameworks.name_ar} (4 of 6 rows hold it). Nullable, because a framework with
     * no Arabic name must not be unrecordable: an empty Arabic label is an honest "not recorded"
     * and a manufactured one is a fabricated governance record (F2-271).
     */
    @Column(name = "framework_name_ar", length = 255)
    private String frameworkNameAr;

    private String domain;

    @Column(name = "control_number")
    private String controlNumber;

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "title_ar")
    private String titleAr;

    @Column(name = "procedure_en", columnDefinition = "NVARCHAR(MAX)")
    private String procedureEn;

    @Column(name = "control_type")
    private String controlType;

    @Column(name = "control_owner")
    private String controlOwner;

    @Column(name = "review_frequency")
    private String reviewFrequency;

    @Column(name = "maturity_level")
    private String maturityLevel;

    @Column(name = "implementation_status")
    private String implementationStatus;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (externalId == null) externalId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
