package com.tss.grc.module.compliance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;

@Entity
@Table(schema = "compliance", name = "findings")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AuditFinding extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "assessment_id")
    private Long assessmentId;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "owner", length = 255)
    private String owner;

    @Column(name = "capa", columnDefinition = "NVARCHAR(MAX)")
    private String capa;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "status", length = 50)
    private String status = "OPEN";
}
