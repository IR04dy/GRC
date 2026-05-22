package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * Ordered step within an {@link IrPlaybook}. The frontend renders these as
 * a phase-grouped checklist (NIST SP 800-61 phases:
 * DETECT → TRIAGE → CONTAIN → ERADICATE → RECOVER → LESSONS_LEARNED).
 */
@Entity
@Table(schema = "socops", name = "ir_playbook_step")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class IrPlaybookStep extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "playbook_id", nullable = false) private Long playbookId;
    @Column(name = "step_order", nullable = false) private Integer stepOrder;
    @Column(nullable = false, length = 40) private String phase;
    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    @Column(name = "instructions_en", columnDefinition = "NVARCHAR(MAX)") private String instructionsEn;
    @Column(name = "instructions_ar", columnDefinition = "NVARCHAR(MAX)") private String instructionsAr;
    @Column(name = "responsible_role", length = 120) private String responsibleRole;
    @Column(name = "estimated_minutes") private Integer estimatedMinutes;
    @Column(name = "is_critical", nullable = false) private Boolean isCritical = false;
}
