package com.tss.grc.module.risk.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "status", length = 20)
    private String status = "PLANNED";

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
