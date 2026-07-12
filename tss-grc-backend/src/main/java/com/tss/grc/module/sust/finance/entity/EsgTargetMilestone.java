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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Plan §5.5 — an interim milestone on an ESG target; the trajectory interpolates
 *  piecewise-linearly through milestones instead of a straight baseline→target line. */
@Entity
@Table(schema = "sust", name = "esg_target_milestones")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EsgTargetMilestone {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "target_id", nullable = false) private Long targetId;
    @Column(name = "year", nullable = false) private Integer year;
    @Column(nullable = false, precision = 18, scale = 4) private BigDecimal value;
    @Column(name = "label_en", length = 200) private String labelEn;
    @Column(name = "label_ar", length = 200) private String labelAr;
    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
