package com.tss.grc.module.capa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(schema = "capa", name = "tasks")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CapaTask {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64) private String externalId;
    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "origin_type", nullable = false, length = 60) private String originType;
    @Column(name = "origin_id",   nullable = false, length = 120) private String originId;

    @Column(name = "title_en", nullable = false, length = 500) private String titleEn;
    @Column(name = "title_ar", nullable = false, length = 500) private String titleAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    /** CORRECTIVE | PREVENTIVE */
    @Column(name = "capa_type", nullable = false, length = 20) private String capaType = "CORRECTIVE";
    @Column(length = 10) private String severity;

    @Column(nullable = false, length = 255) private String owner;
    @Column(name = "target_date", nullable = false) private LocalDate targetDate;

    /** OPEN/IN_PROGRESS/AWAITING_VERIFICATION/CLOSED/CANCELLED */
    @Column(nullable = false, length = 30) private String status = "OPEN";

    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "submitted_for_verification_at") private LocalDateTime submittedForVerificationAt;
    @Column(name = "verified_at") private LocalDateTime verifiedAt;
    @Column(name = "verified_by", length = 255) private String verifiedBy;
    @Column(name = "closed_at") private LocalDateTime closedAt;
    @Column(name = "cancelled_at") private LocalDateTime cancelledAt;
    @Column(name = "cancellation_reason_en", columnDefinition = "NVARCHAR(MAX)") private String cancellationReasonEn;

    /** F2-275 (V389) - house rule 7. Nullable and NOT @NotBlank on purpose: bean validation fires on UPDATE too, so requiring Arabic would make every record predating V389 uneditable, and nothing can invent Arabic governance text (F2-30's reason). */
    @Column(name = "cancellation_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String cancellationReasonAr;

    @Column(name = "progress_notes_en", columnDefinition = "NVARCHAR(MAX)") private String progressNotesEn;
    @Column(name = "progress_notes_ar", columnDefinition = "NVARCHAR(MAX)") private String progressNotesAr;
    @Column(name = "evidence_refs", columnDefinition = "NVARCHAR(MAX)") private String evidenceRefs;

    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
