package com.tss.grc.module.sust.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Plan §5.6 — one NCEC permit violation; each raises its own auto-CAPA
 *  (sourceType ENV_PERMIT, ref ENV_PERMIT_VIOLATION:{id}). */
@Entity
@Table(schema = "sust", name = "env_permit_violations")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EnvPermitViolation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "permit_id", nullable = false) private Long permitId;
    @Column(name = "violation_date", nullable = false) private LocalDate violationDate;
    @Column(name = "description_en", nullable = false, length = 500) private String descriptionEn;
    @Column(name = "description_ar", length = 500) private String descriptionAr;
    /** CRITICAL | HIGH | MEDIUM. */
    @Column(nullable = false, length = 10) private String severity = "HIGH";
    /** OPEN | RESOLVED. */
    @Column(nullable = false, length = 15) private String status = "OPEN";
    @Column(name = "linked_capa_id") private Long linkedCapaId;
    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
