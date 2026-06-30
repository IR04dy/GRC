package com.tss.grc.module.risk.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Legacy <strong>simple</strong> treatment model (type + description + status +
 * dates). ERM epic E-D designates {@code risk_lc.TreatmentPlan} (strategy +
 * actions + approval state-machine + cost-benefit) as the <strong>single
 * canonical</strong> treatment model; new creation flows ({@code
 * RiskTreatmentWizard}) now write canonical plans, and the mandatory-treatment
 * gate ({@code RiskService.close}) reads them.
 *
 * <p>This entity + {@code RiskTreatmentService} + the {@code
 * /api/v1/risk/risks/{id}/treatments} endpoints are retained for back-compat
 * (the RiskDetailPage "treatments" tab still reads them). Full retirement —
 * migrating those rows into {@code treatment_plans} and routing the tab to the
 * canonical API — is a documented E-D follow-on; do not add new write paths here.
 */
@Entity
@Table(schema = "risk", name = "risk_treatments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RiskTreatment extends BaseEntity {
    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "risk_id", nullable = false)
    private Long riskId;

    @Column(name = "treatment_type", nullable = false, length = 20)
    private String treatmentType;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)", nullable = false)
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "status", length = 20)
    private String status = "PLANNED";

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
